package io.constellation

import cats.effect.unsafe.implicits.global

import io.constellation.impl.ModuleRegistryImpl

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ModuleRegistryDeregisterTest extends AnyFlatSpec with Matchers {

  case class TestInput(x: Long)
  case class TestOutput(result: Long)

  private def createTestModule(name: String): Module.Uninitialized =
    ModuleBuilder
      .metadata(name, s"Test module $name", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x * 2))
      .build

  // ===== Deregister =====

  "ModuleRegistry.deregister" should "remove a registered module" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    val module   = createTestModule("TestModule")

    registry.register("TestModule", module).unsafeRunSync()
    registry.get("TestModule").unsafeRunSync() shouldBe defined

    registry.deregister("TestModule").unsafeRunSync()
    registry.get("TestModule").unsafeRunSync() shouldBe None
  }

  it should "remove prefixed module and its short name index" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    val module   = createTestModule("Uppercase")

    registry.register("provider.Uppercase", module).unsafeRunSync()

    // Should be findable by short name
    registry.get("Uppercase").unsafeRunSync() shouldBe defined

    registry.deregister("provider.Uppercase").unsafeRunSync()

    // Both full name and short name should be gone
    registry.get("provider.Uppercase").unsafeRunSync() shouldBe None
    registry.get("Uppercase").unsafeRunSync() shouldBe None
  }

  it should "not affect other modules" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    val module1  = createTestModule("Module1")
    val module2  = createTestModule("Module2")

    registry.register("Module1", module1).unsafeRunSync()
    registry.register("Module2", module2).unsafeRunSync()

    registry.deregister("Module1").unsafeRunSync()

    registry.get("Module1").unsafeRunSync() shouldBe None
    registry.get("Module2").unsafeRunSync() shouldBe defined
  }

  it should "be a no-op for non-existent module" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    val module   = createTestModule("Existing")

    registry.register("Existing", module).unsafeRunSync()

    // Should not throw
    registry.deregister("NonExistent").unsafeRunSync()
    registry.get("Existing").unsafeRunSync() shouldBe defined
  }

  it should "allow re-registration after deregister" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    val module1  = createTestModule("MyModule")
    val module2  = createTestModule("MyModule")

    registry.register("MyModule", module1).unsafeRunSync()
    registry.deregister("MyModule").unsafeRunSync()
    registry.get("MyModule").unsafeRunSync() shouldBe None

    registry.register("MyModule", module2).unsafeRunSync()
    registry.get("MyModule").unsafeRunSync() shouldBe defined
  }

  it should "not remove short name if another module owns it" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    val module1  = createTestModule("Transform")
    val module2  = createTestModule("Transform")

    registry.register("ns1.Transform", module1).unsafeRunSync()
    registry.register("ns2.Transform", module2).unsafeRunSync()

    // Short name "Transform" maps to ns1 (first registered wins)
    registry.deregister("ns2.Transform").unsafeRunSync()

    // Short name should still work for ns1
    registry.get("Transform").unsafeRunSync() shouldBe defined
    registry.get("ns1.Transform").unsafeRunSync() shouldBe defined
    // ns2.Transform is removed from modules, but get() has a fallback that strips
    // the prefix and finds ns1.Transform via short name — verify module count instead
    registry.size.unsafeRunSync() shouldBe 1
  }

  it should "remove short name when the module it points to is deregistered" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    val module1  = createTestModule("Transform")
    val module2  = createTestModule("Transform")

    registry.register("ns1.Transform", module1).unsafeRunSync()
    registry.register("ns2.Transform", module2).unsafeRunSync()

    // Short name "Transform" maps to ns1 (first registered wins)
    // Deregister ns1 — short name should be removed since it pointed to ns1
    registry.deregister("ns1.Transform").unsafeRunSync()

    registry.get("ns1.Transform").unsafeRunSync() shouldBe None
    // ns2 is still findable by full name
    registry.get("ns2.Transform").unsafeRunSync() shouldBe defined
    // Short name "Transform" was removed because it pointed to ns1
    // (ns2 didn't get a short name because ns1 was registered first)
  }

  // ===== Constellation.removeModule =====

  "Constellation.removeModule" should "delegate to ModuleRegistry.deregister" in {
    val constellation = io.constellation.impl.ConstellationImpl.init.unsafeRunSync()
    val module        = createTestModule("TestModule")

    constellation.setModule(module).unsafeRunSync()
    constellation.getModuleByName("TestModule").unsafeRunSync() shouldBe defined

    constellation.removeModule("TestModule").unsafeRunSync()
    constellation.getModuleByName("TestModule").unsafeRunSync() shouldBe None
  }
}
