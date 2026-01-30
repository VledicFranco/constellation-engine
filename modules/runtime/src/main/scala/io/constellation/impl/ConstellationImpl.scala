package io.constellation.impl

import cats.effect.IO
import io.constellation.*
import io.constellation.cache.CacheBackend
import io.constellation.execution.{CancellableExecution, ConstellationLifecycle, GlobalScheduler}
import io.constellation.spi.{ConstellationBackends, ExecutionListener, MetricsProvider, TracerProvider}

import scala.concurrent.duration.FiniteDuration

/** Default implementation of the [[io.constellation.Constellation]] API.
  *
  * Manages module and DAG registries, delegates execution to the [[io.constellation.Runtime]],
  * and integrates with the SPI backend layer for metrics, tracing, caching, and lifecycle management.
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
  * @param moduleRegistry Registry for module definitions
  * @param dagRegistry Registry for DAG specifications
  * @param scheduler Global scheduler for task ordering and concurrency control
  * @param backends Pluggable SPI backends (metrics, tracing, listener, cache, circuit breakers)
  * @param defaultTimeout Optional default timeout applied to all DAG executions
  * @param lifecycle Optional lifecycle manager for graceful shutdown support
  *
  * @see [[io.constellation.impl.ConstellationImpl.ConstellationBuilder]] for the builder API
  */
final class ConstellationImpl(
    moduleRegistry: ModuleRegistry,
    dagRegistry: DagRegistry,
    scheduler: GlobalScheduler = GlobalScheduler.unbounded,
    backends: ConstellationBackends = ConstellationBackends.defaults,
    defaultTimeout: Option[FiniteDuration] = None,
    lifecycle: Option[ConstellationLifecycle] = None
) extends Constellation {

  def getModules: IO[List[ModuleNodeSpec]] =
    moduleRegistry.listModules

  def getModuleByName(name: String): IO[Option[Module.Uninitialized]] =
    moduleRegistry.get(name)

  def setModule(factory: Module.Uninitialized): IO[Unit] =
    moduleRegistry.register(factory.spec.name, factory)

  def dagExists(name: String): IO[Boolean] =
    dagRegistry.exists(name)

  def createDag(name: String): IO[Option[DagSpec]] =
    for {
      exists <- dagRegistry.exists(name)
      result <-
        if exists then {
          IO.pure(None)
        } else {
          val dagSpec = DagSpec.empty(name)
          dagRegistry.register(name, dagSpec).as(Some(dagSpec))
        }
    } yield result

  def setDag(name: String, spec: DagSpec): IO[Unit] =
    dagRegistry.register(name, spec)

  def listDags: IO[Map[String, ComponentMetadata]] =
    dagRegistry.list

  def getDag(name: String): IO[Option[DagSpec]] =
    dagRegistry.retrieve(name, None)

  /** Execute with optional timeout applied. */
  private def executeWithTimeout(run: IO[Runtime.State], dagSpec: DagSpec, inputs: Map[String, CValue], modules: Map[java.util.UUID, Module.Uninitialized], priorities: Map[java.util.UUID, Int]): IO[Runtime.State] =
    defaultTimeout match {
      case Some(timeout) =>
        Runtime.runWithTimeout(timeout, dagSpec, inputs, modules, priorities, scheduler, backends)
      case None =>
        run
    }

  /** Wrap execution with lifecycle registration if lifecycle is present. */
  private def withLifecycle(exec: IO[CancellableExecution]): IO[CancellableExecution] =
    lifecycle match {
      case Some(lc) =>
        exec.flatMap { handle =>
          lc.registerExecution(handle.executionId, handle).flatMap {
            case true =>
              // Deregister when result completes
              val wrapped = new CancellableExecution {
                def executionId = handle.executionId
                def cancel = handle.cancel
                def result = handle.result.guarantee(lc.deregisterExecution(handle.executionId))
                def status = handle.status
              }
              IO.pure(wrapped)
            case false =>
              handle.cancel *> IO.raiseError(
                new ConstellationLifecycle.ShutdownRejectedException("System is shutting down")
              )
          }
        }
      case None => exec
    }

  def runDag(name: String, inputs: Map[String, CValue]): IO[Runtime.State] =
    for {
      dag <- dagRegistry.retrieve(name, None)
      result <- dag match {
        case Some(dagSpec) =>
          for {
            modules <- moduleRegistry.initModules(dagSpec)
            context <- executeWithTimeout(
              Runtime.runWithBackends(dagSpec, inputs, modules, Map.empty, scheduler, backends),
              dagSpec, inputs, modules, Map.empty
            )
          } yield context
        case None => IO.raiseError(new Exception(s"DAG $name not found"))
      }
    } yield result

  def runDagSpec(dagSpec: DagSpec, inputs: Map[String, CValue]): IO[Runtime.State] =
    for {
      modules <- moduleRegistry.initModules(dagSpec)
      context <- executeWithTimeout(
        Runtime.runWithBackends(dagSpec, inputs, modules, Map.empty, scheduler, backends),
        dagSpec, inputs, modules, Map.empty
      )
    } yield context

  def runDagWithModules(
      dagSpec: DagSpec,
      inputs: Map[String, CValue],
      modules: Map[java.util.UUID, Module.Uninitialized]
  ): IO[Runtime.State] =
    executeWithTimeout(
      Runtime.runWithBackends(dagSpec, inputs, modules, Map.empty, scheduler, backends),
      dagSpec, inputs, modules, Map.empty
    )

  def runDagWithModulesAndPriorities(
      dagSpec: DagSpec,
      inputs: Map[String, CValue],
      modules: Map[java.util.UUID, Module.Uninitialized],
      modulePriorities: Map[java.util.UUID, Int]
  ): IO[Runtime.State] =
    executeWithTimeout(
      Runtime.runWithBackends(dagSpec, inputs, modules, modulePriorities, scheduler, backends),
      dagSpec, inputs, modules, modulePriorities
    )

  override def runDagCancellable(name: String, inputs: Map[String, CValue]): IO[CancellableExecution] =
    for {
      dag <- dagRegistry.retrieve(name, None)
      result <- dag match {
        case Some(dagSpec) =>
          for {
            modules <- moduleRegistry.initModules(dagSpec)
            exec <- withLifecycle(
              Runtime.runCancellable(dagSpec, inputs, modules, Map.empty, scheduler, backends)
            )
          } yield exec
        case None => IO.raiseError(new Exception(s"DAG $name not found"))
      }
    } yield result
}

object ConstellationImpl {

  /** Initialize with default unbounded scheduler. */
  def init: IO[ConstellationImpl] =
    for {
      moduleRegistry <- ModuleRegistryImpl.init
      dagRegistry    <- DagRegistryImpl.init
    } yield new ConstellationImpl(moduleRegistry = moduleRegistry, dagRegistry = dagRegistry)

  /** Initialize with a custom scheduler for priority-based execution.
    *
    * @param scheduler The global scheduler to use for task ordering
    * @return ConstellationImpl with scheduler support
    */
  def initWithScheduler(scheduler: GlobalScheduler): IO[ConstellationImpl] =
    for {
      moduleRegistry <- ModuleRegistryImpl.init
      dagRegistry    <- DagRegistryImpl.init
    } yield new ConstellationImpl(
      moduleRegistry = moduleRegistry,
      dagRegistry = dagRegistry,
      scheduler = scheduler
    )

  /** Create a builder for configuring ConstellationImpl with custom backends. */
  def builder(): ConstellationBuilder = ConstellationBuilder()

  /** Builder for constructing a ConstellationImpl with custom configuration.
    *
    * @param scheduler The global scheduler for task ordering
    * @param backends Pluggable backend services (metrics, tracing, listener, cache)
    * @param defaultTimeout Optional default timeout for DAG executions
    * @param lifecycle Optional lifecycle manager for graceful shutdown
    */
  final case class ConstellationBuilder(
      scheduler: GlobalScheduler = GlobalScheduler.unbounded,
      backends: ConstellationBackends = ConstellationBackends.defaults,
      defaultTimeout: Option[FiniteDuration] = None,
      lifecycle: Option[ConstellationLifecycle] = None
  ) {
    /** Set the global scheduler for task ordering and concurrency control. */
    def withScheduler(s: GlobalScheduler): ConstellationBuilder = copy(scheduler = s)

    /** Replace all SPI backends at once. */
    def withBackends(b: ConstellationBackends): ConstellationBuilder = copy(backends = b)

    /** Set the metrics provider (e.g., Prometheus, Datadog). */
    def withMetrics(m: MetricsProvider): ConstellationBuilder = copy(backends = backends.copy(metrics = m))

    /** Set the distributed tracing provider (e.g., OpenTelemetry, Jaeger). */
    def withTracer(t: TracerProvider): ConstellationBuilder = copy(backends = backends.copy(tracer = t))

    /** Set the execution event listener (e.g., Kafka, database audit log). */
    def withListener(l: ExecutionListener): ConstellationBuilder = copy(backends = backends.copy(listener = l))

    /** Set the cache backend for compiled DAGs and results (e.g., Redis, Caffeine). */
    def withCache(c: CacheBackend): ConstellationBuilder = copy(backends = backends.copy(cache = Some(c)))

    /** Set a default timeout applied to all DAG executions. */
    def withDefaultTimeout(t: FiniteDuration): ConstellationBuilder = copy(defaultTimeout = Some(t))

    /** Set the lifecycle manager for graceful shutdown support. */
    def withLifecycle(lc: ConstellationLifecycle): ConstellationBuilder = copy(lifecycle = Some(lc))

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
      * Allocates module and DAG registries and returns a fully configured instance.
      */
    def build(): IO[ConstellationImpl] =
      for {
        moduleRegistry <- ModuleRegistryImpl.init
        dagRegistry    <- DagRegistryImpl.init
      } yield new ConstellationImpl(
        moduleRegistry = moduleRegistry,
        dagRegistry = dagRegistry,
        scheduler = scheduler,
        backends = backends,
        defaultTimeout = defaultTimeout,
        lifecycle = lifecycle
      )
  }
}
