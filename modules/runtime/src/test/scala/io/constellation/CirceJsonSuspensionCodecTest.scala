package io.constellation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class CirceJsonSuspensionCodecTest extends AnyFlatSpec with Matchers {

  private val codec = CirceJsonSuspensionCodec

  private def mkSuspended(
      inputs: Map[String, CValue] = Map("text" -> CValue.CString("hello")),
      computed: Map[UUID, CValue] = Map.empty,
      statuses: Map[UUID, String] = Map.empty
  ): SuspendedExecution = SuspendedExecution(
    executionId = UUID.randomUUID(),
    structuralHash = "test-hash-123",
    resumptionCount = 0,
    dagSpec = DagSpec.empty("test"),
    moduleOptions = Map.empty,
    providedInputs = inputs,
    computedValues = computed,
    moduleStatuses = statuses
  )

  "CirceJsonSuspensionCodec" should "round-trip a simple SuspendedExecution" in {
    val original = mkSuspended()
    val encoded = codec.encode(original)
    encoded.isRight shouldBe true

    val decoded = codec.decode(encoded.toOption.get)
    decoded.isRight shouldBe true

    val result = decoded.toOption.get
    result.executionId shouldBe original.executionId
    result.structuralHash shouldBe original.structuralHash
    result.resumptionCount shouldBe original.resumptionCount
    result.providedInputs shouldBe original.providedInputs
  }

  it should "round-trip with various CValue types" in {
    val inputs = Map(
      "text" -> CValue.CString("hello"),
      "count" -> CValue.CInt(42L),
      "ratio" -> CValue.CFloat(3.14),
      "flag" -> CValue.CBoolean(true)
    )

    val original = mkSuspended(inputs = inputs)
    val encoded = codec.encode(original)
    encoded.isRight shouldBe true

    val decoded = codec.decode(encoded.toOption.get)
    decoded.isRight shouldBe true

    decoded.toOption.get.providedInputs shouldBe inputs
  }

  it should "round-trip with computed values" in {
    val nodeId = UUID.randomUUID()
    val computed = Map(nodeId -> CValue.CString("computed-value"))

    val original = mkSuspended(computed = computed)
    val encoded = codec.encode(original)
    encoded.isRight shouldBe true

    val decoded = codec.decode(encoded.toOption.get)
    decoded.isRight shouldBe true

    decoded.toOption.get.computedValues shouldBe computed
  }

  it should "round-trip with module statuses" in {
    val modId = UUID.randomUUID()
    val statuses = Map(modId -> "Fired")

    val original = mkSuspended(statuses = statuses)
    val encoded = codec.encode(original)
    encoded.isRight shouldBe true

    val decoded = codec.decode(encoded.toOption.get)
    decoded.isRight shouldBe true

    decoded.toOption.get.moduleStatuses shouldBe statuses
  }

  it should "round-trip with resumptionCount > 0" in {
    val original = mkSuspended().copy(resumptionCount = 3)
    val encoded = codec.encode(original)
    encoded.isRight shouldBe true

    val decoded = codec.decode(encoded.toOption.get)
    decoded.isRight shouldBe true

    decoded.toOption.get.resumptionCount shouldBe 3
  }

  it should "fail gracefully on invalid JSON" in {
    val result = codec.decode("not json".getBytes("UTF-8"))
    result.isLeft shouldBe true
  }

  it should "fail gracefully on valid JSON with wrong structure" in {
    val result = codec.decode("""{"foo":"bar"}""".getBytes("UTF-8"))
    result.isLeft shouldBe true
  }

  it should "round-trip CValue.CList" in {
    val inputs = Map(
      "items" -> CValue.CList(
        Vector(CValue.CString("a"), CValue.CString("b")),
        CType.CString
      )
    )
    val original = mkSuspended(inputs = inputs)
    val decoded = codec.decode(codec.encode(original).toOption.get).toOption.get
    decoded.providedInputs shouldBe inputs
  }

  it should "round-trip CValue.CProduct" in {
    val inputs = Map(
      "person" -> CValue.CProduct(
        Map("name" -> CValue.CString("Alice"), "age" -> CValue.CInt(30L)),
        Map("name" -> CType.CString, "age" -> CType.CInt)
      )
    )
    val original = mkSuspended(inputs = inputs)
    val decoded = codec.decode(codec.encode(original).toOption.get).toOption.get
    decoded.providedInputs shouldBe inputs
  }

  it should "round-trip CValue.CSome and CValue.CNone" in {
    val inputs = Map(
      "present" -> CValue.CSome(CValue.CString("hi"), CType.CString),
      "absent" -> CValue.CNone(CType.CInt)
    )
    val original = mkSuspended(inputs = inputs)
    val decoded = codec.decode(codec.encode(original).toOption.get).toOption.get
    decoded.providedInputs shouldBe inputs
  }

  it should "round-trip with module options" in {
    val modId = UUID.randomUUID()
    val options = Map(modId -> ModuleCallOptions(retry = Some(3), timeoutMs = Some(5000L)))
    val original = mkSuspended().copy(moduleOptions = options)
    val decoded = codec.decode(codec.encode(original).toOption.get).toOption.get
    decoded.moduleOptions shouldBe options
  }
}
