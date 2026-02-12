package io.constellation.provider.sdk

import scala.concurrent.duration.*

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}

import io.constellation.provider.v1.provider as pb
import io.constellation.provider.{CValueSerializer, JsonCValueSerializer}
import io.constellation.{CType, CValue}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class InstanceConnectionSpec extends AnyFlatSpec with Matchers {

  private val serializer: CValueSerializer = JsonCValueSerializer

  private val config = SdkConfig(
    executorPort = 9091,
    heartbeatInterval = 100.milliseconds,
    reconnectBackoff = 50.milliseconds,
    maxReconnectBackoff = 200.milliseconds,
    maxReconnectAttempts = 3
  )

  private val echoModule = ModuleDefinition(
    "echo",
    CType.CString,
    CType.CString,
    "1.0.0",
    "Echo",
    v => IO.pure(v)
  )

  private def mkConnection(
      transport: FakeProviderTransport,
      modules: List[ModuleDefinition] = List(echoModule),
      cfg: SdkConfig = config
  ): InstanceConnection = {
    val modulesRef = Ref.of[IO, List[ModuleDefinition]](modules).unsafeRunSync()
    new InstanceConnection(
      instanceAddress = "localhost:9090",
      namespace = "test",
      transport = transport,
      config = cfg,
      modulesRef = modulesRef,
      serializer = serializer
    )
  }

  // ===== Initial state =====

  "InstanceConnection" should "start in Disconnected state" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    conn.currentState.unsafeRunSync() shouldBe InstanceConnectionState.Disconnected
  }

  // ===== Successful connect =====

  it should "transition to Active on successful connect" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    conn.connect.unsafeRunSync()

    conn.currentState.unsafeRunSync() shouldBe InstanceConnectionState.Active

    import scala.jdk.CollectionConverters.*
    val regs = transport.registerCalls.asScala.toList
    regs should have size 1
    regs.head.namespace shouldBe "test"
    regs.head.executorUrl should not be empty
  }

  it should "store connectionId from register response" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    transport
      .setRegisterResponse(
        pb.RegisterResponse(success = true, connectionId = "my-conn-123", protocolVersion = 1)
      )
      .unsafeRunSync()

    val conn = mkConnection(transport)
    conn.connect.unsafeRunSync()

    conn.connectionId.unsafeRunSync() shouldBe Some("my-conn-123")
  }

  // ===== Registration failure =====

  it should "transition to Disconnected on registration failure" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    transport
      .setRegisterResponse(
        pb.RegisterResponse(success = false, connectionId = "")
      )
      .unsafeRunSync()

    val conn = mkConnection(transport)

    val error = intercept[RuntimeException] {
      conn.connect.unsafeRunSync()
    }
    error.getMessage should include("Registration failed")

    conn.currentState.unsafeRunSync() shouldBe InstanceConnectionState.Disconnected
  }

  // ===== Disconnect =====

  it should "transition to Disconnected on graceful disconnect" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    conn.connect.unsafeRunSync()
    conn.currentState.unsafeRunSync() shouldBe InstanceConnectionState.Active

    conn.disconnect.unsafeRunSync()
    conn.currentState.unsafeRunSync() shouldBe InstanceConnectionState.Disconnected

    import scala.jdk.CollectionConverters.*
    transport.deregisterCalls.asScala.toList should have size 1
  }

  // ===== Disconnect when not connected =====

  it should "be a no-op to disconnect when already disconnected" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    noException should be thrownBy conn.disconnect.unsafeRunSync()
    conn.currentState.unsafeRunSync() shouldBe InstanceConnectionState.Disconnected
  }

  // ===== Double connect is idempotent =====

  it should "be idempotent for double connect" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    conn.connect.unsafeRunSync()
    conn.connect.unsafeRunSync() // Should not throw or re-register

    import scala.jdk.CollectionConverters.*
    // Only one registration call should have been made
    transport.registerCalls.asScala.toList should have size 1
  }

  // ===== isHealthy =====

  it should "report healthy when Active" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    conn.connect.unsafeRunSync()
    conn.isHealthy.unsafeRunSync() shouldBe true
  }

  it should "report unhealthy when Disconnected" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    conn.isHealthy.unsafeRunSync() shouldBe false
  }

  // ===== Module declarations in registration =====

  it should "include module declarations in register request" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val modules = List(
      ModuleDefinition(
        "upper",
        CType.CString,
        CType.CString,
        "1.0.0",
        "Uppercase",
        v => IO.pure(v)
      ),
      ModuleDefinition("lower", CType.CString, CType.CString, "2.0.0", "Lowercase", v => IO.pure(v))
    )
    val conn = mkConnection(transport, modules)

    conn.connect.unsafeRunSync()

    import scala.jdk.CollectionConverters.*
    val req = transport.registerCalls.asScala.toList.head
    req.modules should have size 2
    req.modules.map(_.name).toSet shouldBe Set("upper", "lower")
  }

  // ===== replaceModules =====

  it should "update modules via replaceModules" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    conn.connect.unsafeRunSync()

    val newModules = List(
      ModuleDefinition("v2module", CType.CInt, CType.CInt, "2.0.0", "V2", v => IO.pure(v))
    )
    conn.replaceModules(newModules).unsafeRunSync()

    // Should still be Active
    conn.currentState.unsafeRunSync() shouldBe InstanceConnectionState.Active
  }

  // ===== Drain handling =====

  it should "report unhealthy when Draining" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    conn.connect.unsafeRunSync()

    // Simulate drain by setting state
    conn.simulateDrain.unsafeRunSync()
    conn.currentState.unsafeRunSync() shouldBe InstanceConnectionState.Draining
    conn.isHealthy.unsafeRunSync() shouldBe false
  }

  // ===== State transitions =====

  it should "track state transitions correctly" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    conn.currentState.unsafeRunSync() shouldBe InstanceConnectionState.Disconnected
    conn.connect.unsafeRunSync()
    conn.currentState.unsafeRunSync() shouldBe InstanceConnectionState.Active
    conn.disconnect.unsafeRunSync()
    conn.currentState.unsafeRunSync() shouldBe InstanceConnectionState.Disconnected
  }

  // ===== Executor URL in registration =====

  it should "include executor URL in register request" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    conn.connect.unsafeRunSync()

    import scala.jdk.CollectionConverters.*
    val req = transport.registerCalls.asScala.toList.head
    req.executorUrl should not be empty
  }

  // ===== Connection ID cleared on disconnect =====

  it should "clear connectionId on disconnect" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    conn.connect.unsafeRunSync()
    conn.connectionId.unsafeRunSync() shouldBe defined

    conn.disconnect.unsafeRunSync()
    conn.connectionId.unsafeRunSync() shouldBe None
  }

  // ===== Namespace in deregister matches registration =====

  it should "use correct namespace in deregister request" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    conn.connect.unsafeRunSync()
    conn.disconnect.unsafeRunSync()

    import scala.jdk.CollectionConverters.*
    val dereg = transport.deregisterCalls.asScala.toList.head
    dereg.namespace shouldBe "test"
  }

  // ===== Protocol version in registration =====

  it should "set protocol version to 1 in register request" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    conn.connect.unsafeRunSync()

    import scala.jdk.CollectionConverters.*
    val req = transport.registerCalls.asScala.toList.head
    req.protocolVersion shouldBe 1
  }
}
