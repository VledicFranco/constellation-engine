package io.constellation.impl

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.FiniteDuration

import cats.effect.IO
import cats.implicits.*

import io.constellation.*
import io.constellation.cache.CacheBackend
import io.constellation.execution.{ConstellationLifecycle, GlobalScheduler}
import io.constellation.spi.{
  ConstellationBackends,
  ExecutionListener,
  MetricsProvider,
  TracerProvider
}

/** Default implementation of the [[io.constellation.Constellation]] API.
  *
  * Manages module registry, delegates execution to the [[io.constellation.Runtime]], and integrates
  * with the SPI backend layer for metrics, tracing, caching, and lifecycle management.
  *
  * ==Construction==
  *
  * Use the companion object factory methods:
  * {{{
  * // Minimal setup
  * val constellation = ConstellationImpl.init
  *
  * // With custom scheduler
  * val constellation = ConstellationImpl.initWithScheduler(scheduler)
  *
  * // Full configuration via builder
  * val constellation = ConstellationImpl.builder()
  *   .withScheduler(scheduler)
  *   .withBackends(backends)
  *   .withDefaultTimeout(30.seconds)
  *   .build()
  * }}}
  *
  * @param moduleRegistry
  *   Registry for module definitions
  * @param PipelineStoreInstance
  *   Pipeline image store
  * @param scheduler
  *   Global scheduler for task ordering and concurrency control
  * @param backends
  *   Pluggable SPI backends (metrics, tracing, listener, cache, circuit breakers)
  * @param defaultTimeout
  *   Optional default timeout applied to all DAG executions
  * @param lifecycle
  *   Optional lifecycle manager for graceful shutdown support
  *
  * @see
  *   [[io.constellation.impl.ConstellationImpl.ConstellationBuilder]] for the builder API
  */
final class ConstellationImpl(
    moduleRegistry: ModuleRegistry,
    PipelineStoreInstance: PipelineStore,
    scheduler: GlobalScheduler = GlobalScheduler.unbounded,
    backends: ConstellationBackends = ConstellationBackends.defaults,
    defaultTimeout: Option[FiniteDuration] = None,
    lifecycle: Option[ConstellationLifecycle] = None,
    suspensionStoreOpt: Option[SuspensionStore] = None
) extends Constellation {

  def getModules: IO[List[ModuleNodeSpec]] =
    moduleRegistry.listModules

  def getModuleByName(name: String): IO[Option[Module.Uninitialized]] =
    moduleRegistry.get(name)

  def setModule(factory: Module.Uninitialized): IO[Unit] =
    moduleRegistry.register(factory.spec.name, factory)

  // ---------------------------------------------------------------------------
  // New API
  // ---------------------------------------------------------------------------

  def PipelineStore: PipelineStore = PipelineStoreInstance

  def run(
      loaded: LoadedPipeline,
      inputs: Map[String, CValue],
      options: ExecutionOptions = ExecutionOptions()
  ): IO[DataSignature] = {
    val dagSpec   = loaded.image.dagSpec
    val startedAt = Instant.now()

    for {
      // Merge registry modules with synthetic modules from the loaded pipeline
      registryModules <- moduleRegistry.initModules(dagSpec)
      allModules = registryModules ++ loaded.syntheticModules

      // Execute
      state <- executeWithTimeout(
        Runtime.runWithBackends(dagSpec, inputs, allModules, Map.empty, scheduler, backends),
        dagSpec,
        inputs,
        allModules,
        Map.empty
      )
    } yield buildDataSignature(state, loaded, inputs, options, startedAt, resumptionCount = 0)
  }

  def run(
      ref: String,
      inputs: Map[String, CValue],
      options: ExecutionOptions
  ): IO[DataSignature] = {
    val hashLookup: IO[Option[PipelineImage]] =
      if ref.startsWith("sha256:") then PipelineStoreInstance.get(ref.stripPrefix("sha256:"))
      else PipelineStoreInstance.getByName(ref)

    hashLookup.flatMap {
      case Some(image) =>
        val loaded = PipelineImage.rehydrate(image)
        run(loaded, inputs, options)
      case None =>
        IO.raiseError(new Exception(s"Pipeline not found: $ref"))
    }
  }

  override def suspensionStore: Option[SuspensionStore] = suspensionStoreOpt

  def resumeFromStore(
      handle: SuspensionHandle,
      additionalInputs: Map[String, CValue],
      resolvedNodes: Map[String, CValue],
      options: ExecutionOptions
  ): IO[DataSignature] =
    suspensionStoreOpt match {
      case None =>
        IO.raiseError(new IllegalStateException("No SuspensionStore configured"))
      case Some(store) =>
        store.load(handle).flatMap {
          case None =>
            IO.raiseError(new NoSuchElementException(s"Suspension not found: ${handle.id}"))
          case Some(suspended) =>
            for {
              // Resolve registry modules
              registryModules <- moduleRegistry.initModules(suspended.dagSpec)
              // Reconstruct synthetic modules from DagSpec
              syntheticModules = SyntheticModuleFactory.fromDagSpec(suspended.dagSpec)
              allModules       = syntheticModules ++ registryModules
              // Delegate to SuspendableExecution.resume
              result <- SuspendableExecution.resume(
                suspended = suspended,
                additionalInputs = additionalInputs,
                resolvedNodes = resolvedNodes,
                modules = allModules,
                options = options,
                scheduler = scheduler,
                backends = backends
              )
            } yield result
        }
    }

  /** Build a DataSignature from a Runtime.State. */
  private def buildDataSignature(
      state: Runtime.State,
      loaded: LoadedPipeline,
      inputs: Map[String, CValue],
      options: ExecutionOptions,
      startedAt: Instant,
      resumptionCount: Int
  ): DataSignature = {
    val dagSpec = loaded.image.dagSpec

    // Build UUID->name lookup from data nodes
    val uuidToName: Map[UUID, String] = dagSpec.data.map { case (uuid, spec) => uuid -> spec.name }

    // Convert state.data to name-keyed map
    val computedNodes: Map[String, CValue] = state.data.flatMap { case (uuid, evalCValue) =>
      uuidToName.get(uuid).map(name => name -> evalCValue.value)
    }

    // Determine outputs (declared outputs that have been computed)
    // Use outputBindings (name -> UUID) for reliable lookup since data node
    // names may differ from variable names (e.g. "Uppercase_output" vs "result")
    val outputs: Map[String, CValue] = dagSpec.declaredOutputs.flatMap { name =>
      dagSpec.outputBindings
        .get(name)
        .flatMap { dataNodeUuid =>
          state.data.get(dataNodeUuid).map(evalCValue => name -> evalCValue.value)
        }
        .orElse(computedNodes.get(name).map(name -> _))
    }.toMap

    // Missing inputs
    val expectedInputNames = dagSpec.userInputDataNodes.values.flatMap(_.nicknames.values).toSet
    val providedInputNames = inputs.keySet
    val missingInputs      = (expectedInputNames -- providedInputNames).toList.sorted

    // Pending outputs
    val pendingOutputs = dagSpec.declaredOutputs.filterNot(outputs.contains)

    // Determine status
    val failedModules = state.moduleStatus.toList.flatMap { case (uuid, evalStatus) =>
      evalStatus.value match {
        case Module.Status.Failed(error) =>
          val moduleName = dagSpec.modules.get(uuid).map(_.name).getOrElse(uuid.toString)
          Some(
            ExecutionError(
              nodeName = moduleName,
              moduleName = moduleName,
              message = error.getMessage,
              cause = Some(error)
            )
          )
        case _ => None
      }
    }

    val status: PipelineStatus =
      if failedModules.nonEmpty then PipelineStatus.Failed(failedModules)
      else if pendingOutputs.isEmpty && missingInputs.isEmpty then PipelineStatus.Completed
      else PipelineStatus.Suspended

    val completedAt = Instant.now()
    val metadata = MetadataBuilder.build(
      state,
      dagSpec,
      options,
      startedAt,
      completedAt,
      inputNodeNames = inputs.keySet
    )

    // Build suspended state if not completed
    val suspendedState: Option[SuspendedExecution] =
      if status == PipelineStatus.Completed then None
      else
        Some(
          SuspendedExecution(
            executionId = state.processUuid,
            structuralHash = loaded.structuralHash,
            resumptionCount = resumptionCount,
            dagSpec = dagSpec,
            moduleOptions = loaded.image.moduleOptions,
            providedInputs = inputs,
            computedValues = state.data.map { case (uuid, evalCValue) => uuid -> evalCValue.value },
            moduleStatuses = state.moduleStatus.map { case (uuid, evalStatus) =>
              uuid -> evalStatus.value.toString
            }
          )
        )

    DataSignature(
      executionId = state.processUuid,
      structuralHash = loaded.structuralHash,
      resumptionCount = resumptionCount,
      status = status,
      inputs = inputs,
      computedNodes = computedNodes,
      outputs = outputs,
      missingInputs = missingInputs,
      pendingOutputs = pendingOutputs,
      suspendedState = suspendedState,
      metadata = metadata
    )
  }

  /** Execute with optional timeout applied. */
  private def executeWithTimeout(
      run: IO[Runtime.State],
      dagSpec: DagSpec,
      inputs: Map[String, CValue],
      modules: Map[java.util.UUID, Module.Uninitialized],
      priorities: Map[java.util.UUID, Int]
  ): IO[Runtime.State] =
    defaultTimeout match {
      case Some(timeout) =>
        Runtime.runWithTimeout(timeout, dagSpec, inputs, modules, priorities, scheduler, backends)
      case None =>
        run
    }
}

object ConstellationImpl {

  /** Initialize with default unbounded scheduler. */
  def init: IO[ConstellationImpl] =
    for {
      moduleRegistry <- ModuleRegistryImpl.init
      ps             <- PipelineStoreImpl.init
    } yield new ConstellationImpl(
      moduleRegistry = moduleRegistry,
      PipelineStoreInstance = ps
    )

  /** Initialize with a custom scheduler for priority-based execution.
    *
    * @param scheduler
    *   The global scheduler to use for task ordering
    * @return
    *   ConstellationImpl with scheduler support
    */
  def initWithScheduler(scheduler: GlobalScheduler): IO[ConstellationImpl] =
    for {
      moduleRegistry <- ModuleRegistryImpl.init
      ps             <- PipelineStoreImpl.init
    } yield new ConstellationImpl(
      moduleRegistry = moduleRegistry,
      PipelineStoreInstance = ps,
      scheduler = scheduler
    )

  /** Create a builder for configuring ConstellationImpl with custom backends. */
  def builder(): ConstellationBuilder = ConstellationBuilder()

  /** Builder for constructing a ConstellationImpl with custom configuration.
    *
    * @param scheduler
    *   The global scheduler for task ordering
    * @param backends
    *   Pluggable backend services (metrics, tracing, listener, cache)
    * @param defaultTimeout
    *   Optional default timeout for DAG executions
    * @param lifecycle
    *   Optional lifecycle manager for graceful shutdown
    * @param PipelineStoreOpt
    *   Optional pre-configured PipelineStore
    * @param suspensionStoreOpt
    *   Optional SuspensionStore for persist/resume of suspended executions
    */
  final case class ConstellationBuilder(
      scheduler: GlobalScheduler = GlobalScheduler.unbounded,
      backends: ConstellationBackends = ConstellationBackends.defaults,
      defaultTimeout: Option[FiniteDuration] = None,
      lifecycle: Option[ConstellationLifecycle] = None,
      PipelineStoreOpt: Option[PipelineStore] = None,
      suspensionStoreOpt: Option[SuspensionStore] = None
  ) {

    /** Set the global scheduler for task ordering and concurrency control. */
    def withScheduler(s: GlobalScheduler): ConstellationBuilder = copy(scheduler = s)

    /** Replace all SPI backends at once. */
    def withBackends(b: ConstellationBackends): ConstellationBuilder = copy(backends = b)

    /** Set the metrics provider (e.g., Prometheus, Datadog). */
    def withMetrics(m: MetricsProvider): ConstellationBuilder =
      copy(backends = backends.copy(metrics = m))

    /** Set the distributed tracing provider (e.g., OpenTelemetry, Jaeger). */
    def withTracer(t: TracerProvider): ConstellationBuilder =
      copy(backends = backends.copy(tracer = t))

    /** Set the execution event listener (e.g., Kafka, database audit log). */
    def withListener(l: ExecutionListener): ConstellationBuilder =
      copy(backends = backends.copy(listener = l))

    /** Set the cache backend for compiled DAGs and results (e.g., Redis, Caffeine). */
    def withCache(c: CacheBackend): ConstellationBuilder =
      copy(backends = backends.copy(cache = Some(c)))

    /** Set a default timeout applied to all DAG executions. */
    def withDefaultTimeout(t: FiniteDuration): ConstellationBuilder = copy(defaultTimeout = Some(t))

    /** Set the lifecycle manager for graceful shutdown support. */
    def withLifecycle(lc: ConstellationLifecycle): ConstellationBuilder = copy(lifecycle = Some(lc))

    /** Set a pre-configured PipelineStore instance. */
    def withPipelineStore(ps: PipelineStore): ConstellationBuilder =
      copy(PipelineStoreOpt = Some(ps))

    /** Set a SuspensionStore for persisting and resuming suspended executions. */
    def withSuspensionStore(ss: SuspensionStore): ConstellationBuilder =
      copy(suspensionStoreOpt = Some(ss))

    /** Enable circuit breakers for module execution with the given configuration.
      *
      * This is an IO operation because it allocates the circuit breaker registry.
      */
    import io.constellation.execution.CircuitBreakerConfig
    def withCircuitBreaker(config: CircuitBreakerConfig): IO[ConstellationBuilder] =
      io.constellation.execution.CircuitBreakerRegistry.create(config).map { registry =>
        copy(backends = backends.copy(circuitBreakers = Some(registry)))
      }

    /** Build the configured [[ConstellationImpl]] instance.
      *
      * Allocates module registry and returns a fully configured instance.
      */
    def build(): IO[ConstellationImpl] =
      for {
        moduleRegistry <- ModuleRegistryImpl.init
        ps             <- PipelineStoreOpt.map(IO.pure).getOrElse(PipelineStoreImpl.init)
      } yield new ConstellationImpl(
        moduleRegistry = moduleRegistry,
        PipelineStoreInstance = ps,
        scheduler = scheduler,
        backends = backends,
        defaultTimeout = defaultTimeout,
        lifecycle = lifecycle,
        suspensionStoreOpt = suspensionStoreOpt
      )
  }
}
