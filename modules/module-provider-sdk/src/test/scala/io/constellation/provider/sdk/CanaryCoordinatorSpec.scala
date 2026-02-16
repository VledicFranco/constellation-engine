package io.constellation.provider.sdk

import scala.concurrent.duration.*

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}

import io.constellation.provider.v1.provider as pb
import io.constellation.provider.{CValueSerializer, JsonCValueSerializer}
import io.constellation.{CType, CValue}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CanaryCoordinatorSpec extends AnyFlatSpec with Matchers {

  private val serializer: CValueSerializer = JsonCValueSerializer

  private val canaryConfig = CanaryConfig(
    observationWindow = 10.milliseconds, // Short for testing
    healthThreshold = 0.95,
    maxLatencyMs = 5000L,
    rollbackOnFailure = true
  )

  private val sdkConfig = SdkConfig(
    executorPort = 9091,
    heartbeatInterval = 100.milliseconds,
    canary = canaryConfig
  )

  private val oldModule = ModuleDefinition(
    "echo",
    CType.CString,
    CType.CString,
    "1.0.0",
    "Echo v1",
    v => IO.pure(v)
  )

  private val newModule = ModuleDefinition(
    "echo",
    CType.CString,
    CType.CString,
    "2.0.0",
    "Echo v2",
    v => IO.pure(v)
  )

  private def mkConnection(
      transport: FakeProviderTransport,
      modules: List[ModuleDefinition] = List(oldModule),
      healthy: Boolean = true
  ): InstanceConnection = {
    val modulesRef = Ref.of[IO, List[ModuleDefinition]](modules).unsafeRunSync()
    val conn = new InstanceConnection(
      instanceAddress = "localhost:9090",
      namespace = "test",
      transport = transport,
      config = sdkConfig,
      modulesRef = modulesRef,
      serializer = serializer
    )
    if healthy then conn.connect.unsafeRunSync()
    conn
  }

  // ===== All healthy → Promoted =====

  "CanaryCoordinator" should "promote when all instances are healthy" in {
    val t1    = FakeProviderTransport.create.unsafeRunSync()
    val t2    = FakeProviderTransport.create.unsafeRunSync()
    val conn1 = mkConnection(t1)
    val conn2 = mkConnection(t2)

    val coordinator = new CanaryCoordinator(
      connections = Map("inst1" -> conn1, "inst2" -> conn2),
      config = canaryConfig
    )

    val result = coordinator.rollout(List(oldModule), List(newModule)).unsafeRunSync()
    result shouldBe CanaryResult.Promoted
  }

  // ===== Single instance → Promoted =====

  it should "promote with single instance" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    val coordinator = new CanaryCoordinator(
      connections = Map("inst1" -> conn),
      config = canaryConfig
    )

    val result = coordinator.rollout(List(oldModule), List(newModule)).unsafeRunSync()
    result shouldBe CanaryResult.Promoted
  }

  // ===== First instance unhealthy → RolledBack =====

  it should "rollback when first instance becomes unhealthy" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    // Simulate unhealthy after module replacement
    conn.simulateDrain.unsafeRunSync()

    val coordinator = new CanaryCoordinator(
      connections = Map("inst1" -> conn),
      config = canaryConfig
    )

    val result = coordinator.rollout(List(oldModule), List(newModule)).unsafeRunSync()
    result shouldBe a[CanaryResult.RolledBack]
  }

  // ===== Empty connections → Promoted (vacuous truth) =====

  it should "promote with empty connections" in {
    val coordinator = new CanaryCoordinator(
      connections = Map.empty,
      config = canaryConfig
    )

    val result = coordinator.rollout(List(oldModule), List(newModule)).unsafeRunSync()
    result shouldBe CanaryResult.Promoted
  }

  // ===== Module replacement is called =====

  it should "replace modules on each instance during rollout" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    val coordinator = new CanaryCoordinator(
      connections = Map("inst1" -> conn),
      config = canaryConfig
    )

    coordinator.rollout(List(oldModule), List(newModule)).unsafeRunSync()

    // Verify the modules were replaced
    val currentModules = conn.modulesRef.get.unsafeRunSync()
    currentModules.map(_.version) shouldBe List("2.0.0")
  }

  // ===== Rollback restores old modules =====

  it should "restore old modules on rollback" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)

    // Make the connection go unhealthy after replaceModules
    val unhealthyConn = new InstanceConnection(
      instanceAddress = "localhost:9090",
      namespace = "test",
      transport = transport,
      config = sdkConfig,
      modulesRef = Ref.of[IO, List[ModuleDefinition]](List(oldModule)).unsafeRunSync(),
      serializer = serializer
    ) {
      // Override to simulate unhealthy after module change
      private val replaceCount = Ref.unsafe[IO, Int](0)
      override def replaceModules(newModules: List[ModuleDefinition]): IO[Unit] =
        replaceCount.updateAndGet(_ + 1).flatMap { count =>
          super.replaceModules(newModules) >>
            (if count == 1 then simulateDrain else IO.unit) // First replace triggers drain
        }
    }

    // Start connected
    unhealthyConn.connect.unsafeRunSync()

    val coordinator = new CanaryCoordinator(
      connections = Map("inst1" -> unhealthyConn),
      config = canaryConfig
    )

    val result = coordinator.rollout(List(oldModule), List(newModule)).unsafeRunSync()
    result shouldBe a[CanaryResult.RolledBack]

    // Old modules should be restored
    val restored = unhealthyConn.modulesRef.get.unsafeRunSync()
    restored.map(_.version) shouldBe List("1.0.0")
  }

  // ===== Multiple instances, middle one fails =====

  it should "rollback all when middle instance fails" in {
    val t1    = FakeProviderTransport.create.unsafeRunSync()
    val t2    = FakeProviderTransport.create.unsafeRunSync()
    val t3    = FakeProviderTransport.create.unsafeRunSync()
    val conn1 = mkConnection(t1)
    val conn2 = mkConnection(t2)
    val conn3 = mkConnection(t3)

    // conn2 will become unhealthy after module replacement
    conn2.simulateDrain.unsafeRunSync()

    val coordinator = new CanaryCoordinator(
      connections = Map("inst1" -> conn1, "inst2" -> conn2, "inst3" -> conn3),
      config = canaryConfig
    )

    val result = coordinator.rollout(List(oldModule), List(newModule)).unsafeRunSync()
    result shouldBe a[CanaryResult.RolledBack]
  }

  // ===== Observation window is respected =====

  it should "wait for observation window before checking health" in {
    val longConfig = canaryConfig.copy(observationWindow = 100.milliseconds)
    val transport  = FakeProviderTransport.create.unsafeRunSync()
    val conn       = mkConnection(transport)

    val coordinator = new CanaryCoordinator(
      connections = Map("inst1" -> conn),
      config = longConfig
    )

    val start = System.currentTimeMillis()
    coordinator.rollout(List(oldModule), List(newModule)).unsafeRunSync()
    val elapsed = System.currentTimeMillis() - start

    // Use 80ms threshold (80% of 100ms window) to avoid timer-resolution flakiness
    elapsed should be >= 80L
  }

  // ===== RolledBack includes reason =====

  it should "include reason in RolledBack result" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()
    val conn      = mkConnection(transport)
    conn.simulateDrain.unsafeRunSync()

    val coordinator = new CanaryCoordinator(
      connections = Map("inst1" -> conn),
      config = canaryConfig
    )

    val result = coordinator.rollout(List(oldModule), List(newModule)).unsafeRunSync()
    result match {
      case CanaryResult.RolledBack(reason) =>
        reason should include("inst1")
      case other =>
        fail(s"Expected RolledBack, got $other")
    }
  }
}
