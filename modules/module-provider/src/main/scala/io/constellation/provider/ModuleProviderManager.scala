package io.constellation.provider

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration.*

import cats.effect.{IO, Ref, Resource}
import cats.implicits.*

import io.constellation.*
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.FunctionRegistry
import io.constellation.provider.v1.{provider => pb}

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
    serializer: CValueSerializer
) extends Constellation {

  // ===== Constellation delegation =====

  def getModules: IO[List[ModuleNodeSpec]]                     = delegate.getModules
  def getModuleByName(name: String): IO[Option[Module.Uninitialized]] = delegate.getModuleByName(name)
  def setModule(module: Module.Uninitialized): IO[Unit]        = delegate.setModule(module)
  override def removeModule(name: String): IO[Unit]            = delegate.removeModule(name)
  def PipelineStore: PipelineStore                              = delegate.PipelineStore
  def run(loaded: LoadedPipeline, inputs: Map[String, CValue], options: ExecutionOptions): IO[DataSignature] =
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

  /** Handle a Register RPC. */
  def handleRegister(request: pb.RegisterRequest, connectionId: String): IO[pb.RegisterResponse] =
    for {
      conns <- controlPlane.getAllConnections
      namespaceOwners = conns.map(c => c.namespace -> c.connectionId).toMap
      validationResults = SchemaValidator.validate(
        request, functionRegistry, namespaceOwners, connectionId, config.reservedNamespaces
      )

      // Process accepted modules
      registeredNames <- validationResults.traverse {
        case ModuleValidationResult.Accepted(qualifiedName) =>
          val decl = request.modules.find(d => s"${request.namespace}.${d.name}" == qualifiedName).get
          registerExternalModule(decl, request.namespace, request.executorUrl).as(Some(qualifiedName))
        case ModuleValidationResult.Rejected(_, _) =>
          IO.pure(None)
      }.map(_.flatten)

      // Track connection in control plane
      negotiatedVersion = math.min(request.protocolVersion, currentProtocolVersion)
      _ <- controlPlane.registerConnection(
        connectionId = connectionId,
        namespace = request.namespace,
        executorUrl = request.executorUrl,
        modules = registeredNames.toSet,
        protocolVersion = negotiatedVersion
      )

      // Build response
      allAccepted = validationResults.forall(_.isInstanceOf[ModuleValidationResult.Accepted])
      results = request.modules.toList.zip(validationResults).map {
        case (decl, ModuleValidationResult.Accepted(_)) =>
          pb.ModuleRegistrationResult(moduleName = decl.name, accepted = true)
        case (decl, ModuleValidationResult.Rejected(_, reason)) =>
          pb.ModuleRegistrationResult(moduleName = decl.name, accepted = false, rejectionReason = reason)
      }
    } yield pb.RegisterResponse(
      success = allAccepted,
      results = results,
      protocolVersion = currentProtocolVersion,
      connectionId = connectionId
    )

  /** Handle a Deregister RPC. */
  def handleDeregister(request: pb.DeregisterRequest, connectionId: String): IO[pb.DeregisterResponse] =
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
              IO.pure(pb.ModuleDeregistrationResult(moduleName = moduleName, removed = false, error = "not found"))
          case Some(_) =>
            IO.pure(pb.ModuleDeregistrationResult(
              moduleName = moduleName, removed = false, error = "wrong namespace owner"
            ))
          case None =>
            IO.pure(pb.ModuleDeregistrationResult(moduleName = moduleName, removed = false, error = "not found"))
        }
      }

      // Update control plane state
      _ <- connOpt match {
        case Some(conn) if conn.connectionId == connectionId =>
          val removedNames = results.filter(_.removed).map(r => s"${request.namespace}.${r.moduleName}").toSet
          val updatedModules = conn.registeredModules -- removedNames
          if updatedModules.isEmpty then controlPlane.removeConnection(connectionId)
          else controlPlane.updateModules(connectionId, updatedModules)
        case _ => IO.unit
      }
    } yield pb.DeregisterResponse(
      success = results.forall(_.removed),
      results = results
    )

  /** Deregister all modules owned by a connection (e.g., on control plane stream break or liveness timeout). */
  def deregisterAllForConnection(connectionId: String): IO[Unit] =
    for {
      connOpt <- controlPlane.getConnection(connectionId)
      _ <- connOpt.traverse_ { conn =>
        conn.registeredModules.toList.traverse_(deregisterExternalModule)
      }
      _ <- controlPlane.removeConnection(connectionId)
    } yield ()

  private def registerExternalModule(
      decl: pb.ModuleDeclaration,
      namespace: String,
      executorUrl: String
  ): IO[Unit] = {
    val qualifiedName = s"$namespace.${decl.name}"

    // Convert schemas (already validated by SchemaValidator)
    val inputType  = TypeSchemaConverter.toCType(decl.inputSchema.get).toOption.get
    val outputType = TypeSchemaConverter.toCType(decl.outputSchema.get).toOption.get

    // Create external module
    val module = ExternalModule.create(
      name = decl.name,
      namespace = namespace,
      executorUrl = executorUrl,
      inputType = inputType,
      outputType = outputType,
      description = decl.description,
      serializer = serializer
    )

    // Create function signature
    val signature = ExternalFunctionSignature.create(
      name = decl.name,
      namespace = namespace,
      inputType = inputType,
      outputType = outputType
    )

    // Register in both registries
    for {
      _ <- delegate.setModule(module)
      _ <- IO(functionRegistry.register(signature))
    } yield ()
  }

  private def deregisterExternalModule(qualifiedName: String): IO[Unit] =
    for {
      _ <- delegate.removeModule(qualifiedName)
      _ <- IO(functionRegistry.deregister(qualifiedName))
    } yield ()
}

object ModuleProviderManager {

  /** Create a ModuleProviderManager that wraps a Constellation instance and starts a gRPC server.
    *
    * The Resource manages the gRPC server lifecycle, liveness monitor, and report sender.
    * On release: deregisters all connections, stops gRPC server, cancels background fibers.
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
      cp = new ControlPlaneManager(state, config, connId => callbackRef.get.flatMap(_(connId)))
      manager = new ModuleProviderManager(delegate, compiler, config, cp, serializer)
      _ <- Resource.eval(callbackRef.set(connId => manager.deregisterAllForConnection(connId)))
      _ <- cp.startLivenessMonitor
      _ <- cp.startActiveModulesReporter
      _ <- startGrpcServer(manager, config)
      // Graceful shutdown: deregister all active connections before server stops
      _ <- Resource.make(IO.unit)(_ =>
        cp.getAllConnections.flatMap(_.traverse_(conn =>
          manager.deregisterAllForConnection(conn.connectionId)
        ))
      )
    } yield manager

  private def startGrpcServer(
      manager: ModuleProviderManager,
      config: ProviderManagerConfig
  ): Resource[IO, Server] = {
    import cats.effect.unsafe.implicits.global

    val serviceImpl = new ModuleProviderServiceImpl(manager)

    Resource.make(
      IO {
        val server = ServerBuilder
          .forPort(config.grpcPort)
          .addService(
            pb.ModuleProviderGrpc.bindService(serviceImpl, scala.concurrent.ExecutionContext.global)
          )
          .build()
          .start()
        server
      }
    )(server => IO(server.shutdown()).void)
  }
}

/** gRPC service implementation that delegates to ModuleProviderManager. */
private class ModuleProviderServiceImpl(manager: ModuleProviderManager)
    extends pb.ModuleProviderGrpc.ModuleProvider {

  import cats.effect.unsafe.implicits.global

  override def register(request: pb.RegisterRequest): scala.concurrent.Future[pb.RegisterResponse] = {
    val connectionId = UUID.randomUUID().toString
    val io = manager.handleRegister(request, connectionId)
    toFuture(io)
  }

  override def deregister(request: pb.DeregisterRequest): scala.concurrent.Future[pb.DeregisterResponse] = {
    // Use connection_id from proto if present, fall back to namespace for backwards compat
    val connectionId =
      if request.connectionId.nonEmpty then request.connectionId
      else request.namespace
    val io = manager.handleDeregister(request, connectionId)
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

        // First message → activate the control plane
        if connectionIdRef.compareAndSet(null, connId) then {
          manager.controlPlane.activateControlPlane(connId, responseObserver)
            .unsafeRunSync()
        }

        msg.payload match {
          case pb.ControlMessage.Payload.Heartbeat(hb) =>
            manager.controlPlane.recordHeartbeat(connId).unsafeRunSync()
            responseObserver.onNext(
              pb.ControlMessage(
                protocolVersion = msg.protocolVersion,
                connectionId = connId,
                payload = pb.ControlMessage.Payload.HeartbeatAck(
                  pb.HeartbeatAck(timestamp = hb.timestamp)
                )
              )
            )
          case _ => ()
        }
      }

      override def onError(t: Throwable): Unit = {
        Option(connectionIdRef.get()).foreach { connId =>
          manager.deregisterAllForConnection(connId).unsafeRunSync()
        }
      }

      override def onCompleted(): Unit = {
        Option(connectionIdRef.get()).foreach { connId =>
          manager.deregisterAllForConnection(connId).unsafeRunSync()
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
