package io.constellation.lang.semantic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FunctionRegistryTest extends AnyFlatSpec with Matchers {

  private def mkSig(
      name: String,
      moduleName: String,
      namespace: Option[String] = None
  ): FunctionSignature =
    FunctionSignature(
      name = name,
      params = List("input" -> SemanticType.SString),
      returns = SemanticType.SString,
      moduleName = moduleName,
      namespace = namespace
    )

  // ===== Thread-safety (AtomicReference) =====

  "InMemoryFunctionRegistry" should "support concurrent register and lookup without errors" in {
    val registry = new InMemoryFunctionRegistry

    // Register from multiple threads concurrently
    val threads = (1 to 100).map { i =>
      val t = new Thread(() => {
        val sig = mkSig(s"Func$i", s"Module$i", Some(s"ns$i"))
        registry.register(sig)
      })
      t.start()
      t
    }
    threads.foreach(_.join())

    // All 100 should be registered
    registry.all.size shouldBe 100
  }

  it should "support concurrent register and deregister without errors" in {
    val registry = new InMemoryFunctionRegistry

    // Pre-register 50 functions
    (1 to 50).foreach { i =>
      registry.register(mkSig(s"Func$i", s"Module$i", Some(s"ns$i")))
    }

    // Concurrently register 50 more and deregister the first 50
    val registerThreads = (51 to 100).map { i =>
      val t = new Thread(() => registry.register(mkSig(s"Func$i", s"Module$i", Some(s"ns$i"))))
      t.start()
      t
    }
    val deregisterThreads = (1 to 50).map { i =>
      val t = new Thread(() => registry.deregister(s"ns$i.Func$i"))
      t.start()
      t
    }

    (registerThreads ++ deregisterThreads).foreach(_.join())

    // Should have exactly the 50 newly registered
    registry.all.size shouldBe 50
    registry.lookupQualified("ns51.Func51") shouldBe defined
    registry.lookupQualified("ns1.Func1") shouldBe None
  }

  // ===== Deregister =====

  it should "deregister a function by qualified name" in {
    val registry = new InMemoryFunctionRegistry
    val sig      = mkSig("Analyze", "provider.Analyze", Some("provider"))
    registry.register(sig)

    registry.lookupQualified("provider.Analyze") shouldBe Some(sig)
    registry.deregister("provider.Analyze")
    registry.lookupQualified("provider.Analyze") shouldBe None
  }

  it should "remove from simple name index on deregister" in {
    val registry = new InMemoryFunctionRegistry
    val sig      = mkSig("Analyze", "provider.Analyze", Some("provider"))
    registry.register(sig)

    registry.lookupSimple("Analyze") shouldBe List(sig)
    registry.deregister("provider.Analyze")
    registry.lookupSimple("Analyze") shouldBe empty
  }

  it should "remove namespace when last function in it is deregistered" in {
    val registry = new InMemoryFunctionRegistry
    val sig1     = mkSig("Func1", "ns.Func1", Some("ns"))
    val sig2     = mkSig("Func2", "ns.Func2", Some("ns"))
    registry.register(sig1)
    registry.register(sig2)

    registry.namespaces should contain("ns")

    registry.deregister("ns.Func1")
    registry.namespaces should contain("ns") // still has Func2

    registry.deregister("ns.Func2")
    registry.namespaces should not contain "ns"
  }

  it should "keep other functions with same simple name after deregister" in {
    val registry = new InMemoryFunctionRegistry
    val sig1     = mkSig("Transform", "ns1.Transform", Some("ns1"))
    val sig2     = mkSig("Transform", "ns2.Transform", Some("ns2"))
    registry.register(sig1)
    registry.register(sig2)

    registry.lookupSimple("Transform").size shouldBe 2

    registry.deregister("ns1.Transform")
    registry.lookupSimple("Transform") shouldBe List(sig2)
    registry.lookupQualified("ns2.Transform") shouldBe Some(sig2)
  }

  it should "be a no-op when deregistering a non-existent function" in {
    val registry = new InMemoryFunctionRegistry
    registry.register(mkSig("Func", "ns.Func", Some("ns")))

    // Should not throw
    registry.deregister("nonexistent.Func")
    registry.all.size shouldBe 1
  }

  it should "deregister a function without namespace" in {
    val registry = new InMemoryFunctionRegistry
    val sig      = mkSig("Uppercase", "Uppercase")
    registry.register(sig)

    registry.lookup("Uppercase") shouldBe Some(sig)
    registry.deregister("Uppercase")
    registry.lookup("Uppercase") shouldBe None
    registry.all shouldBe empty
  }
}
