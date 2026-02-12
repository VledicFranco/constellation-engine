package io.constellation.provider.sdk

import cats.effect.{IO, Ref, Resource}
import cats.implicits.*

import io.constellation.provider.CValueSerializer
import io.constellation.provider.v1.provider as pb

/** Top-level entry point for the provider SDK.
  *
  * Manages module registration, instance connections, executor server, and canary rollouts.
  */
class ConstellationProvider(
    val namespace: String,
    config: SdkConfig,
    discovery: DiscoveryStrategy,
    private[sdk] val modulesRef: Ref[IO, List[ModuleDefinition]],
    private[sdk] val connectionsRef: Ref[IO, Map[String, InstanceConnection]],
    transportFactory: String => ProviderTransport,
    executorServerFactory: ExecutorServerFactory,
    serializer: CValueSerializer
) {

  /** Register a module to be provided. Must be called before start. */
  def register(module: ModuleDefinition): IO[Unit] =
    modulesRef.update(_ :+ module)

  /** Get all registered modules. */
  def registeredModules: IO[List[ModuleDefinition]] = modulesRef.get

  /** Start the provider: launch executor server, discover instances, connect to all.
    *
    * The Resource manages the full lifecycle — releasing disconnects all and stops the executor.
    */
  def start: Resource[IO, Unit] =
    for {
      // Start executor server
      modules <- Resource.eval(modulesRef.get)
      modRef  <- Resource.eval(Ref.of[IO, List[ModuleDefinition]](modules))
      executorServer = new ModuleExecutorServer(modRef, serializer)
      _ <- executorServerFactory.create(executorServer.toHandler, config.executorPort)

      // Discover instances and create connections
      instances <- Resource.eval(discovery.instances)
      connections <- Resource.eval(instances.traverse { addr =>
        for {
          transport  <- IO(transportFactory(addr))
          connModRef <- Ref.of[IO, List[ModuleDefinition]](modules)
          conn = new InstanceConnection(
            instanceAddress = addr,
            namespace = namespace,
            transport = transport,
            config = config,
            modulesRef = connModRef,
            serializer = serializer
          )
        } yield addr -> conn
      })

      // Store connections
      _ <- Resource.eval(connectionsRef.set(connections.toMap))

      // Connect all instances
      _ <- Resource.eval(connections.traverse_ { case (_, conn) => conn.connect })

      // On release: disconnect all
      _ <- Resource.make(IO.unit)(_ =>
        connectionsRef.get.flatMap(_.values.toList.traverse_(_.disconnect))
      )
    } yield ()

  /** Perform a canary rollout of new modules across all instances. */
  def canaryRollout(newModules: List[ModuleDefinition]): IO[CanaryResult] =
    for {
      oldModules  <- modulesRef.get
      connections <- connectionsRef.get
      coordinator = new CanaryCoordinator(connections, config.canary)
      result <- coordinator.rollout(oldModules, newModules)
      _ <- result match {
        case CanaryResult.Promoted => modulesRef.set(newModules)
        case _                     => IO.unit
      }
    } yield result

  /** Get status of all instance connections. */
  def status: IO[List[(String, InstanceConnectionState)]] =
    connectionsRef.get.flatMap { conns =>
      conns.toList.traverse { case (addr, conn) =>
        conn.currentState.map(addr -> _)
      }
    }
}

object ConstellationProvider {

  /** Create a provider with a static list of instance addresses. */
  def create(
      namespace: String,
      instances: List[String],
      config: SdkConfig = SdkConfig(),
      transportFactory: String => ProviderTransport,
      executorServerFactory: ExecutorServerFactory,
      serializer: CValueSerializer
  ): IO[ConstellationProvider] =
    for {
      modulesRef     <- Ref.of[IO, List[ModuleDefinition]](List.empty)
      connectionsRef <- Ref.of[IO, Map[String, InstanceConnection]](Map.empty)
    } yield new ConstellationProvider(
      namespace = namespace,
      config = config,
      discovery = StaticDiscovery(instances),
      modulesRef = modulesRef,
      connectionsRef = connectionsRef,
      transportFactory = transportFactory,
      executorServerFactory = executorServerFactory,
      serializer = serializer
    )

  /** Create a provider with a custom discovery strategy. */
  def withDiscovery(
      namespace: String,
      discovery: DiscoveryStrategy,
      config: SdkConfig = SdkConfig(),
      transportFactory: String => ProviderTransport,
      executorServerFactory: ExecutorServerFactory,
      serializer: CValueSerializer
  ): IO[ConstellationProvider] =
    for {
      modulesRef     <- Ref.of[IO, List[ModuleDefinition]](List.empty)
      connectionsRef <- Ref.of[IO, Map[String, InstanceConnection]](Map.empty)
    } yield new ConstellationProvider(
      namespace = namespace,
      config = config,
      discovery = discovery,
      modulesRef = modulesRef,
      connectionsRef = connectionsRef,
      transportFactory = transportFactory,
      executorServerFactory = executorServerFactory,
      serializer = serializer
    )
}
