package io.constellation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class ContentHashTest extends AnyFlatSpec with Matchers {

  "computeSHA256" should "produce a deterministic hex string" in {
    val hash1 = ContentHash.computeSHA256("hello".getBytes("UTF-8"))
    val hash2 = ContentHash.computeSHA256("hello".getBytes("UTF-8"))
    hash1 shouldBe hash2
    hash1.length shouldBe 64 // SHA-256 produces 32 bytes = 64 hex chars
  }

  it should "produce different hashes for different inputs" in {
    val hash1 = ContentHash.computeSHA256("hello".getBytes("UTF-8"))
    val hash2 = ContentHash.computeSHA256("world".getBytes("UTF-8"))
    hash1 should not be hash2
  }

  "canonicalizeDagSpec" should "produce the same output for structurally equivalent DAGs" in {
    // Create two DAGs with different UUIDs but same structure
    val mod1 = UUID.randomUUID()
    val data1 = UUID.randomUUID()
    val data2 = UUID.randomUUID()

    val dag1 = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(mod1 -> ModuleNodeSpec(
        metadata = ComponentMetadata.empty("TestModule"),
        consumes = Map("input" -> CType.CString),
        produces = Map("out" -> CType.CString)
      )),
      data = Map(
        data1 -> DataNodeSpec("input", Map(mod1 -> "input"), CType.CString),
        data2 -> DataNodeSpec("output", Map(mod1 -> "out"), CType.CString)
      ),
      inEdges = Set((data1, mod1)),
      outEdges = Set((mod1, data2)),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> data2)
    )

    val mod2 = UUID.randomUUID()
    val data3 = UUID.randomUUID()
    val data4 = UUID.randomUUID()

    val dag2 = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(mod2 -> ModuleNodeSpec(
        metadata = ComponentMetadata.empty("TestModule"),
        consumes = Map("input" -> CType.CString),
        produces = Map("out" -> CType.CString)
      )),
      data = Map(
        data3 -> DataNodeSpec("input", Map(mod2 -> "input"), CType.CString),
        data4 -> DataNodeSpec("output", Map(mod2 -> "out"), CType.CString)
      ),
      inEdges = Set((data3, mod2)),
      outEdges = Set((mod2, data4)),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> data4)
    )

    val canon1 = ContentHash.canonicalizeDagSpec(dag1)
    val canon2 = ContentHash.canonicalizeDagSpec(dag2)
    canon1 shouldBe canon2
  }

  it should "produce different output for structurally different DAGs" in {
    val mod1 = UUID.randomUUID()
    val data1 = UUID.randomUUID()

    val dag1 = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(mod1 -> ModuleNodeSpec(
        metadata = ComponentMetadata.empty("ModuleA"),
        consumes = Map("input" -> CType.CString),
        produces = Map("out" -> CType.CString)
      )),
      data = Map(data1 -> DataNodeSpec("input", Map(mod1 -> "input"), CType.CString)),
      inEdges = Set((data1, mod1)),
      outEdges = Set.empty
    )

    val mod2 = UUID.randomUUID()
    val data2 = UUID.randomUUID()

    val dag2 = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(mod2 -> ModuleNodeSpec(
        metadata = ComponentMetadata.empty("ModuleB"),
        consumes = Map("input" -> CType.CInt),
        produces = Map("out" -> CType.CInt)
      )),
      data = Map(data2 -> DataNodeSpec("input", Map(mod2 -> "input"), CType.CInt)),
      inEdges = Set((data2, mod2)),
      outEdges = Set.empty
    )

    val canon1 = ContentHash.canonicalizeDagSpec(dag1)
    val canon2 = ContentHash.canonicalizeDagSpec(dag2)
    canon1 should not be canon2
  }

  "computeStructuralHash" should "be deterministic" in {
    val mod1 = UUID.randomUUID()
    val data1 = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(mod1 -> ModuleNodeSpec(
        metadata = ComponentMetadata.empty("TestModule"),
        consumes = Map("input" -> CType.CString),
        produces = Map("out" -> CType.CString)
      )),
      data = Map(data1 -> DataNodeSpec("input", Map(mod1 -> "input"), CType.CString)),
      inEdges = Set((data1, mod1)),
      outEdges = Set.empty
    )

    val hash1 = ContentHash.computeStructuralHash(dag)
    val hash2 = ContentHash.computeStructuralHash(dag)
    hash1 shouldBe hash2
    hash1.length shouldBe 64
  }

  it should "produce the same hash for structurally equivalent DAGs with different UUIDs" in {
    val mod1 = UUID.randomUUID()
    val data1 = UUID.randomUUID()

    val dag1 = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(mod1 -> ModuleNodeSpec(
        metadata = ComponentMetadata.empty("M"),
        consumes = Map("a" -> CType.CInt),
        produces = Map("out" -> CType.CInt)
      )),
      data = Map(data1 -> DataNodeSpec("a", Map(mod1 -> "a"), CType.CInt)),
      inEdges = Set((data1, mod1)),
      outEdges = Set.empty
    )

    val mod2 = UUID.randomUUID()
    val data2 = UUID.randomUUID()

    val dag2 = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(mod2 -> ModuleNodeSpec(
        metadata = ComponentMetadata.empty("M"),
        consumes = Map("a" -> CType.CInt),
        produces = Map("out" -> CType.CInt)
      )),
      data = Map(data2 -> DataNodeSpec("a", Map(mod2 -> "a"), CType.CInt)),
      inEdges = Set((data2, mod2)),
      outEdges = Set.empty
    )

    ContentHash.computeStructuralHash(dag1) shouldBe ContentHash.computeStructuralHash(dag2)
  }

  "canonicalizeDagSpec" should "sort maps by key for determinism" in {
    val mod = UUID.randomUUID()
    val data = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(mod -> ModuleNodeSpec(
        metadata = ComponentMetadata.empty("M"),
        consumes = Map("z" -> CType.CString, "a" -> CType.CInt),
        produces = Map("out" -> CType.CString)
      )),
      data = Map(data -> DataNodeSpec("x", Map.empty, CType.CString)),
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val canonical = ContentHash.canonicalizeDagSpec(dag)
    // "a" should appear before "z" in consumes
    val aIdx = canonical.indexOf("consumes:a=")
    val zIdx = canonical.indexOf("consumes:z=")
    aIdx should be < zIdx
  }
}
