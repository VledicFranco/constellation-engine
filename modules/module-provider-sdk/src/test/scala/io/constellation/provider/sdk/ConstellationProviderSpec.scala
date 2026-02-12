package io.constellation.provider.sdk

import scala.concurrent.duration.*

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global

import io.constellation.{CType, CValue}
import io.constellation.provider.{CValueSerializer, JsonCValueSerializer}
import io.constellation.provider.v1.provider as pb

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConstellationProviderSpec extends AnyFlatSpec with Matchers {

  private val serializer: CValueSerializer = JsonCValueSerializer

  private val sdkConfig = SdkConfig(
    executorPort = 9091,
    heartbeatInterval = 100.milliseconds,
    canary = CanaryConfig(observationWindow = 10.milliseconds)
  )

  private val echoModule = ModuleDefinition(
    "echo",
    CType.CString,
    CType.CString,
    "1.0.0",
    "Echo",
    v => IO.pure(v)
  )

  // ===== Register module before start =====

  "ConstellationProvider" should "register a module" in {
    val provider = ConstellationProvider
      .create(
        namespace = "test",
        instances = List("localhost:9090"),
        config = sdkConfig,
        transportFactory = _ => FakeProviderTransport.create.unsafeRunSync(),
        executorServerFactory = new FakeExecutorServerFactory,
        serializer = serializer
      )
      .unsafeRunSync()

    provider.register(echoModule).unsafeRunSync()

    val modules = provider.registeredModules.unsafeRunSync()
    modules should have size 1
    modules.head.name shouldBe "echo"
  }

  // ===== Start discovers instances and connects =====

  it should "discover instances and connect on start" in {
    val transports = Ref.of[IO, List[FakeProviderTransport]](List.empty).unsafeRunSync()

    val provider = ConstellationProvider
      .create(
        namespace = "test",
        instances = List("host1:9090", "host2:9090"),
        config = sdkConfig,
        transportFactory = addr => {
          val t = FakeProviderTransport.create.unsafeRunSync()
          transports.update(_ :+ t).unsafeRunSync()
          t
        },
        executorServerFactory = new FakeExecutorServerFactory,
        serializer = serializer
      )
      .unsafeRunSync()

    provider.register(echoModule).unsafeRunSync()
    provider.start
      .use { _ =>
        for {
          status <- provider.status
        } yield {
          status should have size 2
          status.foreach { case (_, state) =>
            state shouldBe InstanceConnectionState.Active
          }
        }
      }
      .unsafeRunSync()
  }

  // ===== Graceful shutdown deregisters =====

  it should "disconnect all on graceful shutdown" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()

    val provider = ConstellationProvider
      .create(
        namespace = "test",
        instances = List("host1:9090"),
        config = sdkConfig,
        transportFactory = _ => transport,
        executorServerFactory = new FakeExecutorServerFactory,
        serializer = serializer
      )
      .unsafeRunSync()

    provider.register(echoModule).unsafeRunSync()
    provider.start.use(_ => IO.unit).unsafeRunSync()

    // After resource release, all connections should be disconnected
    val status = provider.status.unsafeRunSync()
    status.foreach { case (_, state) =>
      state shouldBe InstanceConnectionState.Disconnected
    }
  }

  // ===== Status reports =====

  it should "report status for all instances" in {
    val provider = ConstellationProvider
      .create(
        namespace = "test",
        instances = List("host1:9090", "host2:9090"),
        config = sdkConfig,
        transportFactory = _ => FakeProviderTransport.create.unsafeRunSync(),
        executorServerFactory = new FakeExecutorServerFactory,
        serializer = serializer
      )
      .unsafeRunSync()

    provider.register(echoModule).unsafeRunSync()
    provider.start
      .use { _ =>
        provider.status.map { status =>
          status should have size 2
        }
      }
      .unsafeRunSync()
  }

  // ===== Canary delegates to CanaryCoordinator =====

  it should "perform canary rollout" in {
    val provider = ConstellationProvider
      .create(
        namespace = "test",
        instances = List("host1:9090"),
        config = sdkConfig,
        transportFactory = _ => FakeProviderTransport.create.unsafeRunSync(),
        executorServerFactory = new FakeExecutorServerFactory,
        serializer = serializer
      )
      .unsafeRunSync()

    provider.register(echoModule).unsafeRunSync()

    val newModule = ModuleDefinition(
      "echo",
      CType.CString,
      CType.CString,
      "2.0.0",
      "Echo v2",
      v => IO.pure(v)
    )

    provider.start
      .use { _ =>
        provider.canaryRollout(List(newModule)).map { result =>
          result shouldBe CanaryResult.Promoted
        }
      }
      .unsafeRunSync()
  }

  // ===== Executor server starts =====

  it should "start executor server on start" in {
    val executorFactory = new FakeExecutorServerFactory

    val provider = ConstellationProvider
      .create(
        namespace = "test",
        instances = List("host1:9090"),
        config = sdkConfig,
        transportFactory = _ => FakeProviderTransport.create.unsafeRunSync(),
        executorServerFactory = executorFactory,
        serializer = serializer
      )
      .unsafeRunSync()

    provider.register(echoModule).unsafeRunSync()

    executorFactory.started shouldBe false

    provider.start
      .use { _ =>
        IO {
          executorFactory.started shouldBe true
        }
      }
      .unsafeRunSync()

    executorFactory.stopped shouldBe true
  }

  // ===== No instances case =====

  it should "handle empty instance list" in {
    val provider = ConstellationProvider
      .create(
        namespace = "test",
        instances = List.empty,
        config = sdkConfig,
        transportFactory = _ => FakeProviderTransport.create.unsafeRunSync(),
        executorServerFactory = new FakeExecutorServerFactory,
        serializer = serializer
      )
      .unsafeRunSync()

    provider.register(echoModule).unsafeRunSync()
    provider.start
      .use { _ =>
        provider.status.map { status =>
          status shouldBe empty
        }
      }
      .unsafeRunSync()
  }

  // ===== withDiscovery factory =====

  it should "create provider with discovery strategy" in {
    val provider = ConstellationProvider
      .withDiscovery(
        namespace = "test",
        discovery = StaticDiscovery(List("host1:9090")),
        config = sdkConfig,
        transportFactory = _ => FakeProviderTransport.create.unsafeRunSync(),
        executorServerFactory = new FakeExecutorServerFactory,
        serializer = serializer
      )
      .unsafeRunSync()

    provider.register(echoModule).unsafeRunSync()
    provider.start
      .use { _ =>
        provider.status.map { status =>
          status should have size 1
        }
      }
      .unsafeRunSync()
  }

  // ===== Multiple modules =====

  it should "register multiple modules" in {
    val provider = ConstellationProvider
      .create(
        namespace = "test",
        instances = List("host1:9090"),
        config = sdkConfig,
        transportFactory = _ => FakeProviderTransport.create.unsafeRunSync(),
        executorServerFactory = new FakeExecutorServerFactory,
        serializer = serializer
      )
      .unsafeRunSync()

    val m1 =
      ModuleDefinition("upper", CType.CString, CType.CString, "1.0.0", "Upper", v => IO.pure(v))
    val m2 =
      ModuleDefinition("lower", CType.CString, CType.CString, "1.0.0", "Lower", v => IO.pure(v))

    provider.register(m1).unsafeRunSync()
    provider.register(m2).unsafeRunSync()

    val modules = provider.registeredModules.unsafeRunSync()
    modules should have size 2
    modules.map(_.name).toSet shouldBe Set("upper", "lower")
  }
}
