package io.constellation.provider

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration.*

import cats.effect.{IO, Ref, Resource}
import cats.implicits.*

import io.constellation.*
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.FunctionRegistry
import io.constellation.provider.v1.provider as pb

import io.grpc.{Server, ServerBuilder, Status}
import io.grpc.stub.StreamObserver

/** Central manager that wraps a Constellation instance and hosts the gRPC ModuleProvider service.
  *
  * Implements the Constellation trait via delegation, so existing infrastructure (http-api, LSP)
  * works unchanged when wrapping with this manager.
  */
class ModuleProviderManager(
    delegate: Constellation,
    compiler: LangCompiler,
    config: ProviderManagerConfig,
    val controlPlane: ControlPlaneManager,
    serializer: CValueSerializer,
    private[provider] val channelCache: GrpcChannelCache
) extends Constellation {

  // ===== Constellation delegation =====

  def getModules: IO[List[ModuleNodeSpec]] = delegate.getModules
  def getModuleByName(name: String): IO[Option[Module.Uninitialized]] =
    delegate.getModuleByName(name)
  def setModule(module: Module.Uninitialized): IO[Unit] = delegate.setModule(module)
  override def removeModule(name: String): IO[Unit]     = delegate.removeModule(name)
  def PipelineStore: PipelineStore                      = delegate.PipelineStore
  def run(
      loaded: LoadedPipeline,
      inputs: Map[String, CValue],
      options: ExecutionOptions
  ): IO[DataSignature] =
    delegate.run(loaded, inputs, options)
  def run(ref: String, inputs: Map[String, CValue], options: ExecutionOptions): IO[DataSignature] =
    delegate.run(ref, inputs, options)
  override def suspensionStore: Option[SuspensionStore] = delegate.suspensionStore
  def resumeFromStore(
      handle: SuspensionHandle,
      additionalInputs: Map[String, CValue],
      resolvedNodes: Map[String, CValue],
      options: ExecutionOptions
  ): IO[DataSignature] =
    delegate.resumeFromStore(handle, additionalInputs, resolvedNodes, options)

  // ===== Provider Management =====

  private val functionRegistry: FunctionRegistry = compiler.functionRegistry
  private val currentProtocolVersion: Int        = 1

  /** Executor pools per namespace. Solo providers use a pool of size 1; groups use multi-member
    * pools.
    */
  private[provider] val executorPools: Ref[IO, Map[String, ExecutorPool]] =
    Ref.unsafe[IO, Map[String, ExecutorPool]](Map.empty)

  /** Handle a Register RPC. */
  def handleRegister(request: pb.RegisterRequest, connectionId: String): IO[pb.RegisterResponse] =
    for {
      conns <- controlPlane.getAllConnections
      namespaceOwners = conns.map(c => c.namespace -> c.connectionId).toMap
      namespaceGroupIds = conns.collect {
        case c if c.groupId.nonEmpty => c.namespace -> c.groupId
      }.toMap
      validationResults = SchemaValidator.validate(
        request,
        functionRegistry,
        namespaceOwners,
        namespaceGroupIds,
        connectionId,
        config.reservedNamespaces
      )

      // Process accepted modules
      registeredNames <- validationResults
        .traverse {
          case ModuleValidationResult.Accepted(qualifiedName) =>
            request.modules.toList.find(d =>
              s"${request.namespace}.${d.name}" == qualifiedName
            ) match {
              case Some(decl) =>
                registerExternalModule(decl, request.namespace, request.executorUrl, connectionId)
                  .as(Some(qualifiedName))
              case None =>
                IO.raiseError(
                  new RuntimeException(
                    s"Internal error: accepted module $qualifiedName not found in request"
                  )
                )
            }
          case ModuleValidationResult.Rejected(_, _) =>
            IO.pure(None)
        }
        .map(_.flatten)

      // Track connection in control plane
      negotiatedVersion = math.min(request.protocolVersion, currentProtocolVersion)
      _ <- controlPlane.registerConnection(
        connectionId = connectionId,
        namespace = request.namespace,
        executorUrl = request.executorUrl,
        groupId = request.groupId,
        modules = registeredNames.toSet,
        protocolVersion = negotiatedVersion
      )

      // Build response
      allAccepted = validationResults.forall(_.isInstanceOf[ModuleValidationResult.Accepted])
      results = request.modules.toList.zip(validationResults).map {
        case (decl, ModuleValidationResult.Accepted(_)) =>
          pb.ModuleRegistrationResult(moduleName = decl.name, accepted = true)
        case (decl, ModuleValidationResult.Rejected(_, reason)) =>
          pb.ModuleRegistrationResult(
            moduleName = decl.name,
            accepted = false,
            rejectionReason = reason
          )
      }
    } yield pb.RegisterResponse(
      success = allAccepted,
      results = results,
      protocolVersion = currentProtocolVersion,
      connectionId = connectionId
    )

  /** Handle a Deregister RPC. */
  def handleDeregister(
      request: pb.DeregisterRequest,
      connectionId: String
  ): IO[pb.DeregisterResponse] =
    for {
      connOpt <- controlPlane.getConnectionByNamespace(request.namespace)
      results <- request.moduleNames.toList.traverse { moduleName =>
        val qualifiedName = s"${request.namespace}.$moduleName"
        connOpt match {
          case Some(conn) if conn.connectionId == connectionId =>
            if conn.registeredModules.contains(qualifiedName) then
              deregisterExternalModule(qualifiedName).as(
                pb.ModuleDeregistrationResult(moduleName = moduleName, removed = true)
              )
            else
              IO.pure(
                pb.ModuleDeregistrationResult(
                  moduleName = moduleName,
                  removed = false,
                  error = "not found"
                )
              )
          case Some(_) =>
            IO.pure(
              pb.ModuleDeregistrationResult(
                moduleName = moduleName,
                removed = false,
                error = "wrong namespace owner"
              )
            )
          case None =>
            IO.pure(
              pb.ModuleDeregistrationResult(
                moduleName = moduleName,
                removed = false,
                error = "not found"
              )
            )
        }
      }

      // Update control plane state
      _ <- connOpt match {
        case Some(conn) if conn.connectionId == connectionId =>
          val removedNames =
            results.filter(_.removed).map(r => s"${request.namespace}.${r.moduleName}").toSet
          val updatedModules = conn.registeredModules -- removedNames
          if updatedModules.isEmpty then controlPlane.removeConnection(connectionId)
          else controlPlane.updateModules(connectionId, updatedModules)
        case _ => IO.unit
      }
    } yield pb.DeregisterResponse(
      success = results.forall(_.removed),
      results = results
    )

  /** Deregister all modules owned by a connection (e.g., on control plane stream break or liveness
    * timeout).
    *
    * For group members: modules are only deregistered when the last group member disconnects. The
    * executor is always removed from the pool, but modules stay registered for other members.
    */
  def deregisterAllForConnection(connectionId: String): IO[Unit] =
    for {
      connOpt <- controlPlane.getConnection(connectionId)
      isLast  <- controlPlane.isLastGroupMember(connectionId)
      _ <- connOpt.traverse_ { conn =>
        // Remove executor from pool
        removeFromExecutorPool(conn.namespace, connectionId) >>
          // Only deregister modules if this is the last group member (or solo provider)
          (if isLast then
             conn.registeredModules.toList.traverse_(deregisterExternalModule) >>
               IO(channelCache.shutdownChannel(conn.executorUrl))
           else IO.unit)
      }
      _ <- controlPlane.removeConnection(connectionId)
    } yield ()

  private def registerExternalModule(
      decl: pb.ModuleDeclaration,
      namespace: String,
      executorUrl: String,
      connectionId: String
  ): IO[Unit] =
    for {
      inputSchema <- IO.fromOption(decl.inputSchema)(
        new RuntimeException(s"Missing input schema for ${decl.name}")
      )
      outputSchema <- IO.fromOption(decl.outputSchema)(
        new RuntimeException(s"Missing output schema for ${decl.name}")
      )
      inputType <- IO.fromEither(
        TypeSchemaConverter
          .toCType(inputSchema)
          .left
          .map(e => new RuntimeException(s"Invalid input schema for ${decl.name}: $e"))
      )
      outputType <- IO.fromEither(
        TypeSchemaConverter
          .toCType(outputSchema)
          .left
          .map(e => new RuntimeException(s"Invalid output schema for ${decl.name}: $e"))
      )

      // Get or create executor pool for this namespace
      pool <- getOrCreateExecutorPool(namespace)
      _    <- pool.add(ExecutorEndpoint(connectionId, executorUrl))

      // Only create the module and signature if this is the first registration for the namespace
      // (subsequent group members just add to the pool)
      poolSize <- pool.size
      _ <-
        if poolSize == 1 then {
          val module = ExternalModule.create(
            name = decl.name,
            namespace = namespace,
            executorPool = pool,
            inputType = inputType,
            outputType = outputType,
            description = decl.description,
            serializer = serializer,
            channelCache = channelCache
          )

          val signature = ExternalFunctionSignature.create(
            name = decl.name,
            namespace = namespace,
            inputType = inputType,
            outputType = outputType
          )

          delegate.setModule(module) >> IO(functionRegistry.register(signature))
        } else IO.unit
    } yield ()

  /** Get or create an executor pool for a namespace. */
  private def getOrCreateExecutorPool(namespace: String): IO[ExecutorPool] =
    executorPools.get.flatMap { pools =>
      pools.get(namespace) match {
        case Some(pool) => IO.pure(pool)
        case None =>
          RoundRobinExecutorPool.create.flatMap { newPool =>
            executorPools.modify { current =>
              current.get(namespace) match {
                case Some(existing) => (current, existing) // Another thread created it
                case None           => (current + (namespace -> newPool), newPool)
              }
            }
          }
      }
    }

  /** Remove a connection's executor from the namespace pool. */
  private def removeFromExecutorPool(namespace: String, connectionId: String): IO[Unit] =
    executorPools.get.flatMap { pools =>
      pools.get(namespace) match {
        case Some(pool) =>
          pool.remove(connectionId).flatMap { isEmpty =>
            if isEmpty then executorPools.update(_ - namespace)
            else IO.unit
          }
        case None => IO.unit
      }
    }

  // ===== Operational Tooling =====

  /** List all provider connections with summary info. */
  def listProviders: IO[List[ProviderInfo]] =
    controlPlane.getAllConnections.map(_.map { conn =>
      ProviderInfo(
        connectionId = conn.connectionId,
        namespace = conn.namespace,
        executorUrl = conn.executorUrl,
        groupId = conn.groupId,
        modules = conn.registeredModules,
        state = conn.state,
        registeredAt = conn.registeredAt,
        lastHeartbeatAt = conn.lastHeartbeatAt
      )
    })

  /** Drain a provider connection by ID. Returns false if connection not found or not active. */
  def drainProvider(connectionId: String, reason: String, deadlineMs: Long = 30000L): IO[Boolean] =
    controlPlane.drainConnection(connectionId, reason, deadlineMs)

  private def deregisterExternalModule(qualifiedName: String): IO[Unit] =
    for {
      _ <- delegate.removeModule(qualifiedName)
      _ <- IO(functionRegistry.deregister(qualifiedName))
    } yield ()
}

/** Summary information about a connected provider. */
final case class ProviderInfo(
    connectionId: String,
    namespace: String,
    executorUrl: String,
    groupId: String,
    modules: Set[String],
    state: ConnectionState,
    registeredAt: Long,
    lastHeartbeatAt: Option[Long]
)

object ModuleProviderManager {

  /** Create a ModuleProviderManager that wraps a Constellation instance and starts a gRPC server.
    *
    * The Resource manages gRPC server lifecycle, liveness monitor, report sender, and channel
    * cache. Release order: cancel fibers → deregister connections → stop gRPC server → shut down
    * channels.
    */
  def apply(
      delegate: Constellation,
      compiler: LangCompiler,
      config: ProviderManagerConfig,
      serializer: CValueSerializer = JsonCValueSerializer
  ): Resource[IO, ModuleProviderManager] =
    for {
      state       <- Resource.eval(Ref.of[IO, Map[String, ProviderConnection]](Map.empty))
      callbackRef <- Resource.eval(Ref.of[IO, String => IO[Unit]](_ => IO.unit))
      cache       <- Resource.make(IO(new GrpcChannelCache))(c => IO(c.shutdownAll()))
      cp      = new ControlPlaneManager(state, config, connId => callbackRef.get.flatMap(_(connId)))
      manager = new ModuleProviderManager(delegate, compiler, config, cp, serializer, cache)
      _ <- Resource.eval(callbackRef.set(connId => manager.deregisterAllForConnection(connId)))
      _ <- startGrpcServer(manager, config)
      // Graceful shutdown: deregister all active connections before server stops
      _ <- Resource.make(IO.unit)(_ =>
        cp.getAllConnections.flatMap(
          _.traverse_(conn => manager.deregisterAllForConnection(conn.connectionId))
        )
      )
      // Background fibers — released first (before deregister and server stop)
      _ <- cp.startLivenessMonitor
      _ <- cp.startActiveModulesReporter
    } yield manager

  private def startGrpcServer(
      manager: ModuleProviderManager,
      config: ProviderManagerConfig
  ): Resource[IO, Server] = {
    val serviceImpl = new ModuleProviderServiceImpl(manager)

    Resource.make(
      IO {
        // Break builder chain into separate vals to avoid scoverage instrumentation issue with Scala 3
        val serviceDef =
          pb.ModuleProviderGrpc.bindService(serviceImpl, scala.concurrent.ExecutionContext.global)
        val builder = ServerBuilder.forPort(config.grpcPort).addService(serviceDef)
        val server  = builder.build()
        server.start()
        server
      }
    )(server => IO(server.shutdown()).void)
  }
}

/** gRPC service implementation that delegates to ModuleProviderManager. */
private class ModuleProviderServiceImpl(manager: ModuleProviderManager)
    extends pb.ModuleProviderGrpc.ModuleProvider {

  import cats.effect.unsafe.implicits.global

  override def register(
      request: pb.RegisterRequest
  ): scala.concurrent.Future[pb.RegisterResponse] = {
    val connectionId = UUID.randomUUID().toString
    val io           = manager.handleRegister(request, connectionId)
    toFuture(io)
  }

  override def deregister(
      request: pb.DeregisterRequest
  ): scala.concurrent.Future[pb.DeregisterResponse] = {
    val io =
      if request.connectionId.nonEmpty then manager.handleDeregister(request, request.connectionId)
      else {
        // Backwards compat: resolve actual connectionId from namespace
        manager.controlPlane.getConnectionByNamespace(request.namespace).flatMap {
          case Some(conn) => manager.handleDeregister(request, conn.connectionId)
          case None =>
            IO.pure(
              pb.DeregisterResponse(
                success = false,
                results = request.moduleNames.toList.map(n =>
                  pb.ModuleDeregistrationResult(
                    moduleName = n,
                    removed = false,
                    error = "not found"
                  )
                )
              )
            )
        }
      }
    toFuture(io)
  }

  override def controlPlane(
      responseObserver: StreamObserver[pb.ControlMessage]
  ): StreamObserver[pb.ControlMessage] = {
    // connectionId comes from the FIRST message on the stream
    val connectionIdRef = new AtomicReference[String](null)

    new StreamObserver[pb.ControlMessage] {
      override def onNext(msg: pb.ControlMessage): Unit = {
        val connId = msg.connectionId

        // First message → activate the control plane (fire-and-forget to avoid blocking gRPC thread)
        if connectionIdRef.compareAndSet(null, connId) then {
          manager.controlPlane
            .activateControlPlane(connId, responseObserver)
            .unsafeRunAndForget()
        }

        msg.payload match {
          case pb.ControlMessage.Payload.Heartbeat(hb) =>
            manager.controlPlane.recordHeartbeat(connId).unsafeRunAndForget()
            responseObserver.onNext(
              pb.ControlMessage(
                protocolVersion = msg.protocolVersion,
                connectionId = connId,
                payload = pb.ControlMessage.Payload.HeartbeatAck(
                  pb.HeartbeatAck(timestamp = hb.timestamp)
                )
              )
            )
          case pb.ControlMessage.Payload.DrainAck(ack) =>
            manager.controlPlane.recordDrainAck(connId, ack).unsafeRunAndForget()
          case _ => ()
        }
      }

      override def onError(t: Throwable): Unit =
        Option(connectionIdRef.get()).foreach { connId =>
          manager.deregisterAllForConnection(connId).unsafeRunAndForget()
        }

      override def onCompleted(): Unit = {
        Option(connectionIdRef.get()).foreach { connId =>
          manager.deregisterAllForConnection(connId).unsafeRunAndForget()
        }
        responseObserver.onCompleted()
      }
    }
  }

  private def toFuture[A](io: IO[A]): scala.concurrent.Future[A] = {
    val promise = scala.concurrent.Promise[A]()
    io.unsafeRunAsync {
      case Right(value) => promise.success(value)
      case Left(error)  => promise.failure(error)
    }
    promise.future
  }
}
