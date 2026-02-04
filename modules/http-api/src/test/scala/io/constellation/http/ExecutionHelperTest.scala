package io.constellation.http

import cats.Eval
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.constellation.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class ExecutionHelperTest extends AnyFlatSpec with Matchers {

  // ========== Test Fixtures ==========

  // For convertInputs tests - only creates input nodes (no outputs)
  // Without edges, all data nodes are considered top-level (inputs)
  private def createInputOnlyDagSpec(
      inputName: String,
      inputType: CType
  ): DagSpec = {
    val inputDataId = UUID.randomUUID()

    DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map.empty,
      data = Map(
        inputDataId -> DataNodeSpec(
          name = inputName,
          nicknames = Map(inputDataId -> inputName),
          cType = inputType
        )
      ),
      inEdges = Set.empty,
      outEdges = Set.empty
    )
  }

  private def createDagSpecWithMultipleInputs(): (DagSpec, UUID, UUID) = {
    val inputXId = UUID.randomUUID()
    val inputYId = UUID.randomUUID()

    // Only create input nodes - without edges, all are considered top-level (inputs)
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("MultiInputDag"),
      modules = Map.empty,
      data = Map(
        inputXId -> DataNodeSpec(
          name = "x",
          nicknames = Map(inputXId -> "x"),
          cType = CType.CInt
        ),
        inputYId -> DataNodeSpec(
          name = "y",
          nicknames = Map(inputYId -> "y"),
          cType = CType.CString
        )
      ),
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    (dag, inputXId, inputYId)
  }

  // ========== convertInputs Tests ==========

  "ExecutionHelper.convertInputs" should "convert JSON Int to CValue.CInt" in {
    val dag    = createInputOnlyDagSpec("x", CType.CInt)
    val inputs = Map("x" -> Json.fromInt(42))

    val result = ExecutionHelper.convertInputs(inputs, dag).unsafeRunSync()

    result.size shouldBe 1
    result("x") shouldBe CValue.CInt(42)
  }

  it should "convert JSON String to CValue.CString" in {
    val dag    = createInputOnlyDagSpec("text", CType.CString)
    val inputs = Map("text" -> Json.fromString("hello world"))

    val result = ExecutionHelper.convertInputs(inputs, dag).unsafeRunSync()

    result("text") shouldBe CValue.CString("hello world")
  }

  it should "convert JSON Boolean to CValue.CBoolean" in {
    val dag    = createInputOnlyDagSpec("flag", CType.CBoolean)
    val inputs = Map("flag" -> Json.fromBoolean(true))

    val result = ExecutionHelper.convertInputs(inputs, dag).unsafeRunSync()

    result("flag") shouldBe CValue.CBoolean(true)
  }

  it should "convert JSON Float to CValue.CFloat" in {
    val dag    = createInputOnlyDagSpec("value", CType.CFloat)
    val inputs = Map("value" -> Json.fromDoubleOrNull(3.14))

    val result = ExecutionHelper.convertInputs(inputs, dag).unsafeRunSync()

    result("value") shouldBe CValue.CFloat(3.14)
  }

  it should "convert JSON array to CValue.CList" in {
    val dag = createInputOnlyDagSpec("items", CType.CList(CType.CInt))
    val inputs = Map(
      "items" -> Json.fromValues(
        List(
          Json.fromInt(1),
          Json.fromInt(2),
          Json.fromInt(3)
        )
      )
    )

    val result = ExecutionHelper.convertInputs(inputs, dag).unsafeRunSync()

    result("items") shouldBe CValue.CList(
      Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)),
      CType.CInt
    )
  }

  it should "convert JSON object to CValue.CProduct" in {
    val productType = CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt))
    val dag         = createInputOnlyDagSpec("user", productType)
    val inputs = Map(
      "user" -> Json.obj(
        "name" -> Json.fromString("Alice"),
        "age"  -> Json.fromInt(30)
      )
    )

    val result = ExecutionHelper.convertInputs(inputs, dag).unsafeRunSync()

    result("user") shouldBe a[CValue.CProduct]
    val product = result("user").asInstanceOf[CValue.CProduct]
    product.value("name") shouldBe CValue.CString("Alice")
    product.value("age") shouldBe CValue.CInt(30)
  }

  it should "handle multiple inputs" in {
    val (dag, _, _) = createDagSpecWithMultipleInputs()
    val inputs = Map(
      "x" -> Json.fromInt(42),
      "y" -> Json.fromString("test")
    )

    val result = ExecutionHelper.convertInputs(inputs, dag).unsafeRunSync()

    result.size shouldBe 2
    result("x") shouldBe CValue.CInt(42)
    result("y") shouldBe CValue.CString("test")
  }

  it should "fail for missing required input" in {
    val (dag, _, _) = createDagSpecWithMultipleInputs()
    val inputs      = Map("x" -> Json.fromInt(42)) // missing "y"

    val error = intercept[RuntimeException] {
      ExecutionHelper.convertInputs(inputs, dag).unsafeRunSync()
    }

    error.getMessage should include("Missing required input")
    error.getMessage should include("y")
  }

  it should "fail for type mismatch" in {
    val dag    = createInputOnlyDagSpec("x", CType.CInt)
    val inputs = Map("x" -> Json.fromString("not a number"))

    val error = intercept[RuntimeException] {
      ExecutionHelper.convertInputs(inputs, dag).unsafeRunSync()
    }

    error.getMessage should include("x")
  }

  it should "handle empty inputs when no inputs required" in {
    // DAG with no top-level data nodes (all data is internal)
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("NoInputDag"),
      modules = Map.empty,
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )
    val inputs = Map.empty[String, Json]

    val result = ExecutionHelper.convertInputs(inputs, dag).unsafeRunSync()

    result shouldBe empty
  }

  it should "convert optional type with Some value" in {
    val dag    = createInputOnlyDagSpec("opt", CType.COptional(CType.CInt))
    val inputs = Map("opt" -> Json.fromInt(42))

    val result = ExecutionHelper.convertInputs(inputs, dag).unsafeRunSync()

    result("opt") shouldBe CValue.CSome(CValue.CInt(42), CType.CInt)
  }

  it should "convert optional type with null value" in {
    val dag    = createInputOnlyDagSpec("opt", CType.COptional(CType.CString))
    val inputs = Map("opt" -> Json.Null)

    val result = ExecutionHelper.convertInputs(inputs, dag).unsafeRunSync()

    result("opt") shouldBe CValue.CNone(CType.CString)
  }

  // ========== convertInputsLenient Tests ==========

  "ExecutionHelper.convertInputsLenient" should "skip missing inputs" in {
    val (dag, _, _) = createDagSpecWithMultipleInputs()
    val inputs      = Map("x" -> Json.fromInt(42)) // missing "y"

    val result = ExecutionHelper.convertInputsLenient(inputs, dag).unsafeRunSync()

    result.size shouldBe 1
    result("x") shouldBe CValue.CInt(42)
    result.get("y") shouldBe None
  }

  it should "still fail for type mismatch" in {
    val dag    = createInputOnlyDagSpec("x", CType.CInt)
    val inputs = Map("x" -> Json.fromString("not a number"))

    val error = intercept[RuntimeException] {
      ExecutionHelper.convertInputsLenient(inputs, dag).unsafeRunSync()
    }

    error.getMessage should include("x")
  }

  it should "convert all inputs when all are present" in {
    val (dag, _, _) = createDagSpecWithMultipleInputs()
    val inputs = Map(
      "x" -> Json.fromInt(42),
      "y" -> Json.fromString("test")
    )

    val result = ExecutionHelper.convertInputsLenient(inputs, dag).unsafeRunSync()

    result.size shouldBe 2
    result("x") shouldBe CValue.CInt(42)
    result("y") shouldBe CValue.CString("test")
  }

  it should "return empty map when all inputs are missing" in {
    val (dag, _, _) = createDagSpecWithMultipleInputs()
    val inputs      = Map.empty[String, Json]

    val result = ExecutionHelper.convertInputsLenient(inputs, dag).unsafeRunSync()

    result shouldBe empty
  }

  // ========== buildMissingInputsMap Tests ==========

  "ExecutionHelper.buildMissingInputsMap" should "return correct name-to-type map for missing inputs" in {
    val (dag, _, _) = createDagSpecWithMultipleInputs()
    val provided    = Set("x") // only x provided, y is missing

    val result = ExecutionHelper.buildMissingInputsMap(provided, dag)

    result.size shouldBe 1
    result("y") shouldBe "CString"
  }

  it should "return empty map when all inputs are provided" in {
    val (dag, _, _) = createDagSpecWithMultipleInputs()
    val provided    = Set("x", "y")

    val result = ExecutionHelper.buildMissingInputsMap(provided, dag)

    result shouldBe empty
  }

  it should "return all inputs when none are provided" in {
    val (dag, _, _) = createDagSpecWithMultipleInputs()
    val provided    = Set.empty[String]

    val result = ExecutionHelper.buildMissingInputsMap(provided, dag)

    result.size shouldBe 2
    result("x") shouldBe "CInt"
    result("y") shouldBe "CString"
  }

  // ========== extractOutputs Tests ==========

  "ExecutionHelper.extractOutputs" should "extract declared outputs from state" in {
    val outputId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map.empty,
      data = Map(
        outputId -> DataNodeSpec("result", Map(outputId -> "result"), CType.CInt)
      ),
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List("result"),
      outputBindings = Map("result" -> outputId)
    )

    val state = Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = dag,
      moduleStatus = Map.empty,
      data = Map(outputId -> Eval.now(CValue.CInt(42)))
    )

    val result = ExecutionHelper.extractOutputs(state).unsafeRunSync()

    result.size shouldBe 1
    result("result") shouldBe Json.fromInt(42)
  }

  it should "extract multiple declared outputs" in {
    val outputAId = UUID.randomUUID()
    val outputBId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("MultiOutputDag"),
      modules = Map.empty,
      data = Map(
        outputAId -> DataNodeSpec("a", Map(outputAId -> "a"), CType.CString),
        outputBId -> DataNodeSpec("b", Map(outputBId -> "b"), CType.CBoolean)
      ),
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List("a", "b"),
      outputBindings = Map("a" -> outputAId, "b" -> outputBId)
    )

    val state = Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = dag,
      moduleStatus = Map.empty,
      data = Map(
        outputAId -> Eval.now(CValue.CString("hello")),
        outputBId -> Eval.now(CValue.CBoolean(true))
      )
    )

    val result = ExecutionHelper.extractOutputs(state).unsafeRunSync()

    result.size shouldBe 2
    result("a") shouldBe Json.fromString("hello")
    result("b") shouldBe Json.fromBoolean(true)
  }

  it should "fail if declared output is not in output bindings" in {
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map.empty,
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List("missing"),
      outputBindings = Map.empty // No binding for "missing"
    )

    val state = Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = dag,
      moduleStatus = Map.empty,
      data = Map.empty
    )

    val error = intercept[RuntimeException] {
      ExecutionHelper.extractOutputs(state).unsafeRunSync()
    }

    error.getMessage should include("Output binding for 'missing' not found")
  }

  it should "fail if data node UUID not found in state" in {
    val outputId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map.empty,
      data = Map(
        outputId -> DataNodeSpec("result", Map(outputId -> "result"), CType.CInt)
      ),
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List("result"),
      outputBindings = Map("result" -> outputId)
    )

    val state = Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = dag,
      moduleStatus = Map.empty,
      data = Map.empty // Missing the output data
    )

    val error = intercept[RuntimeException] {
      ExecutionHelper.extractOutputs(state).unsafeRunSync()
    }

    error.getMessage should include("Data node for output 'result'")
    error.getMessage should include("not found")
  }

  it should "convert complex output types to JSON" in {
    val outputId    = UUID.randomUUID()
    val productType = CType.CProduct(Map("x" -> CType.CInt, "y" -> CType.CString))
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map.empty,
      data = Map(
        outputId -> DataNodeSpec("result", Map(outputId -> "result"), productType)
      ),
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List("result"),
      outputBindings = Map("result" -> outputId)
    )

    val state = Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = dag,
      moduleStatus = Map.empty,
      data = Map(
        outputId -> Eval.now(
          CValue.CProduct(
            Map("x" -> CValue.CInt(10), "y" -> CValue.CString("test")),
            productType.structure
          )
        )
      )
    )

    val result = ExecutionHelper.extractOutputs(state).unsafeRunSync()

    result("result") shouldBe Json.obj(
      "x" -> Json.fromInt(10),
      "y" -> Json.fromString("test")
    )
  }

  it should "convert list output to JSON array" in {
    val outputId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map.empty,
      data = Map(
        outputId -> DataNodeSpec("items", Map(outputId -> "items"), CType.CList(CType.CInt))
      ),
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List("items"),
      outputBindings = Map("items" -> outputId)
    )

    val state = Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = dag,
      moduleStatus = Map.empty,
      data = Map(
        outputId -> Eval.now(
          CValue.CList(
            Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)),
            CType.CInt
          )
        )
      )
    )

    val result = ExecutionHelper.extractOutputs(state).unsafeRunSync()

    result("items") shouldBe Json.fromValues(
      List(
        Json.fromInt(1),
        Json.fromInt(2),
        Json.fromInt(3)
      )
    )
  }

  // ========== Legacy Output Extraction Tests ==========

  "ExecutionHelper.extractOutputs (legacy mode)" should "extract all bottom-level nodes when no declaredOutputs" in {
    val outputId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("LegacyDag"),
      modules = Map.empty,
      data = Map(
        outputId -> DataNodeSpec("result", Map(outputId -> "result"), CType.CInt)
      ),
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List.empty, // No declared outputs - legacy mode
      outputBindings = Map.empty
    )

    val state = Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = dag,
      moduleStatus = Map.empty,
      data = Map(outputId -> Eval.now(CValue.CInt(100)))
    )

    val result = ExecutionHelper.extractOutputs(state).unsafeRunSync()

    result("result") shouldBe Json.fromInt(100)
  }
}
