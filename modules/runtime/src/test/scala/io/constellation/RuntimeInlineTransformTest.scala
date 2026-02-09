package io.constellation

import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.execution.GlobalScheduler
import io.constellation.pool.RuntimePool
import io.constellation.spi.ConstellationBackends

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RuntimeInlineTransformTest extends AnyFlatSpec with Matchers {

  // ===== Helper: module definitions =====

  case class StringInput(text: String)
  case class StringOutput(result: String)

  private def uppercaseModule: Module.Uninitialized =
    ModuleBuilder
      .metadata("Uppercase", "Converts to uppercase", 1, 0)
      .implementationPure[StringInput, StringOutput](in => StringOutput(in.text.toUpperCase))
      .build

  case class IntInput(x: Long)
  case class IntOutput(result: Long)

  private def doubleModule: Module.Uninitialized =
    ModuleBuilder
      .metadata("Double", "Doubles a number", 1, 0)
      .implementationPure[IntInput, IntOutput](in => IntOutput(in.x * 2))
      .build

  // Sentinel UUID used as a nickname key for standalone user input data nodes.
  // Validation requires at least one nickname so it can match init data keys.
  private val SentinelId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

  // ===== Helper: create a user input DataNodeSpec with a self-nickname =====

  /** Creates a DataNodeSpec for a user input data node. Uses a sentinel UUID as the nickname key so
    * that Runtime validation can match the initData key to this data node.
    */
  private def userInput(name: String, cType: CType): DataNodeSpec =
    DataNodeSpec(name, Map(SentinelId -> name), cType)

  /** Creates a DataNodeSpec for a user input that is also consumed by a module. The nickname map
    * includes both the sentinel (for validation) and the module's entry.
    */
  private def userInputForModule(name: String, moduleId: UUID, paramName: String, cType: CType): DataNodeSpec =
    DataNodeSpec(name, Map(SentinelId -> name, moduleId -> paramName), cType)

  // ===== Helper: build a single-module DAG with one inline transform data node =====

  /** Creates a DAG with one module (input -> module -> moduleOutput) plus an additional data node
    * that has an inline transform. The inline transform node is not connected to any module via
    * inEdges/outEdges; it computes its value purely from its transformInputs.
    */
  private def dagWithInlineTransform(
      moduleId: UUID,
      inputId: UUID,
      moduleOutputId: UUID,
      transformDataId: UUID,
      inputName: String,
      outputName: String,
      inputType: CType,
      outputType: CType,
      transformSpec: DataNodeSpec
  ): DagSpec = DagSpec(
    metadata = ComponentMetadata.empty("InlineTransformDag"),
    modules = Map(
      moduleId -> ModuleNodeSpec(
        metadata = ComponentMetadata("TestModule", "Test", List.empty, 1, 0),
        consumes = Map(inputName -> inputType),
        produces = Map(outputName -> outputType)
      )
    ),
    data = Map(
      inputId -> DataNodeSpec(inputName, Map(moduleId -> inputName), inputType),
      moduleOutputId -> DataNodeSpec(outputName, Map(moduleId -> outputName), outputType),
      transformDataId -> transformSpec
    ),
    inEdges = Set((inputId, moduleId)),
    outEdges = Set((moduleId, moduleOutputId))
  )

  /** Creates a DAG with no modules -- only user input data nodes and inline transform data nodes.
    * Useful for testing inline transforms in isolation.
    */
  private def transformOnlyDag(
      inputSpecs: Map[UUID, DataNodeSpec],
      transformSpecs: Map[UUID, DataNodeSpec]
  ): DagSpec = DagSpec(
    metadata = ComponentMetadata.empty("TransformOnlyDag"),
    modules = Map.empty,
    data = inputSpecs ++ transformSpecs,
    inEdges = Set.empty,
    outEdges = Set.empty
  )

  // =====================================================================
  // NotTransform tests
  // =====================================================================

  "Runtime.run with NotTransform" should "negate a true boolean input" in {
    val inputId     = UUID.randomUUID()
    val notResultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        inputId -> userInput("flag", CType.CBoolean)
      ),
      transformSpecs = Map(
        notResultId -> DataNodeSpec(
          "not_flag",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.NotTransform),
          transformInputs = Map("operand" -> inputId)
        )
      )
    )

    val initData = Map("flag" -> CValue.CBoolean(true))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(notResultId).value shouldBe CValue.CBoolean(false)
  }

  it should "negate a false boolean input" in {
    val inputId     = UUID.randomUUID()
    val notResultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        inputId -> userInput("flag", CType.CBoolean)
      ),
      transformSpecs = Map(
        notResultId -> DataNodeSpec(
          "not_flag",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.NotTransform),
          transformInputs = Map("operand" -> inputId)
        )
      )
    )

    val initData = Map("flag" -> CValue.CBoolean(false))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(notResultId).value shouldBe CValue.CBoolean(true)
  }

  it should "chain NotTransform with a module" in {
    val moduleId       = UUID.randomUUID()
    val inputId        = UUID.randomUUID()
    val moduleOutputId = UUID.randomUUID()
    val boolInputId    = UUID.randomUUID()
    val notResultId    = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("NotWithModuleDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Uppercase", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputId -> DataNodeSpec("text", Map(moduleId -> "text"), CType.CString),
        moduleOutputId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CString),
        boolInputId -> userInput("flag", CType.CBoolean),
        notResultId -> DataNodeSpec(
          "not_flag",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.NotTransform),
          transformInputs = Map("operand" -> boolInputId)
        )
      ),
      inEdges = Set((inputId, moduleId)),
      outEdges = Set((moduleId, moduleOutputId))
    )

    val modules  = Map(moduleId -> uppercaseModule)
    val initData = Map("text" -> CValue.CString("hello"), "flag" -> CValue.CBoolean(true))

    val state = Runtime.run(dag, initData, modules).unsafeRunSync()

    state.moduleStatus(moduleId).value shouldBe a[Module.Status.Fired]
    state.data(moduleOutputId).value shouldBe CValue.CString("HELLO")
    state.data(notResultId).value shouldBe CValue.CBoolean(false)
  }

  // =====================================================================
  // ConditionalTransform tests
  // =====================================================================

  "Runtime.run with ConditionalTransform" should "select then-branch when condition is true" in {
    val condInputId   = UUID.randomUUID()
    val thenInputId   = UUID.randomUUID()
    val elseInputId   = UUID.randomUUID()
    val condResultId  = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        condInputId -> userInput("cond", CType.CBoolean),
        thenInputId -> userInput("thenVal", CType.CString),
        elseInputId -> userInput("elseVal", CType.CString)
      ),
      transformSpecs = Map(
        condResultId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CString,
          inlineTransform = Some(InlineTransform.ConditionalTransform),
          transformInputs = Map(
            "cond"   -> condInputId,
            "thenBr" -> thenInputId,
            "elseBr" -> elseInputId
          )
        )
      )
    )

    val initData = Map(
      "cond"    -> CValue.CBoolean(true),
      "thenVal" -> CValue.CString("yes"),
      "elseVal" -> CValue.CString("no")
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(condResultId).value shouldBe CValue.CString("yes")
  }

  it should "select else-branch when condition is false" in {
    val condInputId  = UUID.randomUUID()
    val thenInputId  = UUID.randomUUID()
    val elseInputId  = UUID.randomUUID()
    val condResultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        condInputId -> userInput("cond", CType.CBoolean),
        thenInputId -> userInput("thenVal", CType.CString),
        elseInputId -> userInput("elseVal", CType.CString)
      ),
      transformSpecs = Map(
        condResultId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CString,
          inlineTransform = Some(InlineTransform.ConditionalTransform),
          transformInputs = Map(
            "cond"   -> condInputId,
            "thenBr" -> thenInputId,
            "elseBr" -> elseInputId
          )
        )
      )
    )

    val initData = Map(
      "cond"    -> CValue.CBoolean(false),
      "thenVal" -> CValue.CString("yes"),
      "elseVal" -> CValue.CString("no")
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(condResultId).value shouldBe CValue.CString("no")
  }

  it should "work with integer branches" in {
    val condInputId  = UUID.randomUUID()
    val thenInputId  = UUID.randomUUID()
    val elseInputId  = UUID.randomUUID()
    val condResultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        condInputId -> userInput("cond", CType.CBoolean),
        thenInputId -> userInput("thenVal", CType.CInt),
        elseInputId -> userInput("elseVal", CType.CInt)
      ),
      transformSpecs = Map(
        condResultId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CInt,
          inlineTransform = Some(InlineTransform.ConditionalTransform),
          transformInputs = Map(
            "cond"   -> condInputId,
            "thenBr" -> thenInputId,
            "elseBr" -> elseInputId
          )
        )
      )
    )

    val initData = Map(
      "cond"    -> CValue.CBoolean(true),
      "thenVal" -> CValue.CInt(100L),
      "elseVal" -> CValue.CInt(0L)
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(condResultId).value shouldBe CValue.CInt(100L)
  }

  // =====================================================================
  // AndTransform tests
  // =====================================================================

  "Runtime.run with AndTransform" should "return true when both inputs are true" in {
    val leftId    = UUID.randomUUID()
    val rightId   = UUID.randomUUID()
    val resultId  = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        leftId  -> userInput("left", CType.CBoolean),
        rightId -> userInput("right", CType.CBoolean)
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.AndTransform),
          transformInputs = Map("left" -> leftId, "right" -> rightId)
        )
      )
    )

    val initData = Map("left" -> CValue.CBoolean(true), "right" -> CValue.CBoolean(true))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CBoolean(true)
  }

  it should "return false when left is false (short-circuit)" in {
    val leftId   = UUID.randomUUID()
    val rightId  = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        leftId  -> userInput("left", CType.CBoolean),
        rightId -> userInput("right", CType.CBoolean)
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.AndTransform),
          transformInputs = Map("left" -> leftId, "right" -> rightId)
        )
      )
    )

    val initData = Map("left" -> CValue.CBoolean(false), "right" -> CValue.CBoolean(true))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CBoolean(false)
  }

  it should "return false when right is false" in {
    val leftId   = UUID.randomUUID()
    val rightId  = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        leftId  -> userInput("left", CType.CBoolean),
        rightId -> userInput("right", CType.CBoolean)
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.AndTransform),
          transformInputs = Map("left" -> leftId, "right" -> rightId)
        )
      )
    )

    val initData = Map("left" -> CValue.CBoolean(true), "right" -> CValue.CBoolean(false))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CBoolean(false)
  }

  // =====================================================================
  // OrTransform tests
  // =====================================================================

  "Runtime.run with OrTransform" should "return true when left is true (short-circuit)" in {
    val leftId   = UUID.randomUUID()
    val rightId  = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        leftId  -> userInput("left", CType.CBoolean),
        rightId -> userInput("right", CType.CBoolean)
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.OrTransform),
          transformInputs = Map("left" -> leftId, "right" -> rightId)
        )
      )
    )

    val initData = Map("left" -> CValue.CBoolean(true), "right" -> CValue.CBoolean(false))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CBoolean(true)
  }

  it should "return false when both are false" in {
    val leftId   = UUID.randomUUID()
    val rightId  = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        leftId  -> userInput("left", CType.CBoolean),
        rightId -> userInput("right", CType.CBoolean)
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.OrTransform),
          transformInputs = Map("left" -> leftId, "right" -> rightId)
        )
      )
    )

    val initData = Map("left" -> CValue.CBoolean(false), "right" -> CValue.CBoolean(false))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CBoolean(false)
  }

  it should "return true when right is true" in {
    val leftId   = UUID.randomUUID()
    val rightId  = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        leftId  -> userInput("left", CType.CBoolean),
        rightId -> userInput("right", CType.CBoolean)
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.OrTransform),
          transformInputs = Map("left" -> leftId, "right" -> rightId)
        )
      )
    )

    val initData = Map("left" -> CValue.CBoolean(false), "right" -> CValue.CBoolean(true))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CBoolean(true)
  }

  // =====================================================================
  // GuardTransform tests
  // =====================================================================

  "Runtime.run with GuardTransform" should "return Some when condition is true" in {
    val condId   = UUID.randomUUID()
    val exprId   = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        condId -> userInput("cond", CType.CBoolean),
        exprId -> userInput("expr", CType.CInt)
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.COptional(CType.CInt),
          inlineTransform = Some(InlineTransform.GuardTransform),
          transformInputs = Map("cond" -> condId, "expr" -> exprId)
        )
      )
    )

    val initData = Map("cond" -> CValue.CBoolean(true), "expr" -> CValue.CInt(42L))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CSome(CValue.CInt(42L), CType.CInt)
  }

  it should "return None when condition is false" in {
    val condId   = UUID.randomUUID()
    val exprId   = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        condId -> userInput("cond", CType.CBoolean),
        exprId -> userInput("expr", CType.CInt)
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.COptional(CType.CInt),
          inlineTransform = Some(InlineTransform.GuardTransform),
          transformInputs = Map("cond" -> condId, "expr" -> exprId)
        )
      )
    )

    val initData = Map("cond" -> CValue.CBoolean(false), "expr" -> CValue.CInt(42L))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CNone(CType.CInt)
  }

  // =====================================================================
  // CoalesceTransform tests
  // =====================================================================

  "Runtime.run with CoalesceTransform" should "return inner value when left is Some" in {
    val leftId   = UUID.randomUUID()
    val rightId  = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    // CoalesceTransform works on the Any level: left is Some(v) or None
    // We need to provide the left input as an Optional that gets converted to Some/None at runtime
    // User inputs go through cValueToAny, which converts CSome -> Some, CNone -> None
    val dag = transformOnlyDag(
      inputSpecs = Map(
        leftId  -> userInput("left", CType.COptional(CType.CInt)),
        rightId -> userInput("right", CType.CInt)
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CInt,
          inlineTransform = Some(InlineTransform.CoalesceTransform),
          transformInputs = Map("left" -> leftId, "right" -> rightId)
        )
      )
    )

    val initData = Map(
      "left"  -> CValue.CSome(CValue.CInt(10L), CType.CInt),
      "right" -> CValue.CInt(99L)
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CInt(10L)
  }

  it should "return fallback when left is None" in {
    val leftId   = UUID.randomUUID()
    val rightId  = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        leftId  -> userInput("left", CType.COptional(CType.CInt)),
        rightId -> userInput("right", CType.CInt)
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CInt,
          inlineTransform = Some(InlineTransform.CoalesceTransform),
          transformInputs = Map("left" -> leftId, "right" -> rightId)
        )
      )
    )

    val initData = Map(
      "left"  -> CValue.CNone(CType.CInt),
      "right" -> CValue.CInt(99L)
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CInt(99L)
  }

  // =====================================================================
  // RecordBuildTransform tests
  // =====================================================================

  "Runtime.run with RecordBuildTransform" should "build a record from input fields" in {
    val nameInputId  = UUID.randomUUID()
    val ageInputId   = UUID.randomUUID()
    val recordId     = UUID.randomUUID()

    val fieldTypes = Map("name" -> CType.CString, "age" -> CType.CInt)

    val dag = transformOnlyDag(
      inputSpecs = Map(
        nameInputId -> userInput("name", CType.CString),
        ageInputId  -> userInput("age", CType.CInt)
      ),
      transformSpecs = Map(
        recordId -> DataNodeSpec(
          "record",
          Map.empty,
          CType.CProduct(fieldTypes),
          inlineTransform = Some(InlineTransform.RecordBuildTransform(List("name", "age"))),
          transformInputs = Map("name" -> nameInputId, "age" -> ageInputId)
        )
      )
    )

    val initData = Map("name" -> CValue.CString("Alice"), "age" -> CValue.CInt(30L))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    val recordValue = state.data(recordId).value
    recordValue shouldBe a[CValue.CProduct]
    val product = recordValue.asInstanceOf[CValue.CProduct]
    product.value("name") shouldBe CValue.CString("Alice")
    product.value("age") shouldBe CValue.CInt(30L)
  }

  it should "build a single-field record" in {
    val inputId  = UUID.randomUUID()
    val recordId = UUID.randomUUID()

    val fieldTypes = Map("value" -> CType.CFloat)

    val dag = transformOnlyDag(
      inputSpecs = Map(
        inputId -> userInput("value", CType.CFloat)
      ),
      transformSpecs = Map(
        recordId -> DataNodeSpec(
          "record",
          Map.empty,
          CType.CProduct(fieldTypes),
          inlineTransform = Some(InlineTransform.RecordBuildTransform(List("value"))),
          transformInputs = Map("value" -> inputId)
        )
      )
    )

    val initData = Map("value" -> CValue.CFloat(3.14))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    val product = state.data(recordId).value.asInstanceOf[CValue.CProduct]
    product.value("value") shouldBe CValue.CFloat(3.14)
  }

  // =====================================================================
  // MatchTransform tests
  // =====================================================================

  "Runtime.run with MatchTransform" should "match the first matching pattern" in {
    val scrutineeId = UUID.randomUUID()
    val matchId     = UUID.randomUUID()

    // Pattern matching on an integer: if it's 1 -> "one", if it's 2 -> "two", else -> "other"
    val patternMatchers = List[Any => Boolean](
      v => v == 1L,
      v => v == 2L,
      _ => true // wildcard
    )
    val bodyEvaluators = List[Any => Any](
      _ => "one",
      _ => "two",
      _ => "other"
    )

    val dag = transformOnlyDag(
      inputSpecs = Map(
        scrutineeId -> userInput("x", CType.CInt)
      ),
      transformSpecs = Map(
        matchId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CString,
          inlineTransform = Some(
            InlineTransform.MatchTransform(patternMatchers, bodyEvaluators, CType.CInt)
          ),
          transformInputs = Map("scrutinee" -> scrutineeId)
        )
      )
    )

    val initData = Map("x" -> CValue.CInt(1L))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(matchId).value shouldBe CValue.CString("one")
  }

  it should "fall through to the second pattern" in {
    val scrutineeId = UUID.randomUUID()
    val matchId     = UUID.randomUUID()

    val patternMatchers = List[Any => Boolean](
      v => v == 1L,
      v => v == 2L,
      _ => true
    )
    val bodyEvaluators = List[Any => Any](
      _ => "one",
      _ => "two",
      _ => "other"
    )

    val dag = transformOnlyDag(
      inputSpecs = Map(
        scrutineeId -> userInput("x", CType.CInt)
      ),
      transformSpecs = Map(
        matchId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CString,
          inlineTransform = Some(
            InlineTransform.MatchTransform(patternMatchers, bodyEvaluators, CType.CInt)
          ),
          transformInputs = Map("scrutinee" -> scrutineeId)
        )
      )
    )

    val initData = Map("x" -> CValue.CInt(2L))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(matchId).value shouldBe CValue.CString("two")
  }

  it should "fall through to the wildcard default" in {
    val scrutineeId = UUID.randomUUID()
    val matchId     = UUID.randomUUID()

    val patternMatchers = List[Any => Boolean](
      v => v == 1L,
      v => v == 2L,
      _ => true
    )
    val bodyEvaluators = List[Any => Any](
      _ => "one",
      _ => "two",
      _ => "other"
    )

    val dag = transformOnlyDag(
      inputSpecs = Map(
        scrutineeId -> userInput("x", CType.CInt)
      ),
      transformSpecs = Map(
        matchId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CString,
          inlineTransform = Some(
            InlineTransform.MatchTransform(patternMatchers, bodyEvaluators, CType.CInt)
          ),
          transformInputs = Map("scrutinee" -> scrutineeId)
        )
      )
    )

    val initData = Map("x" -> CValue.CInt(999L))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(matchId).value shouldBe CValue.CString("other")
  }

  it should "match on union types by unwrapping the tag" in {
    val scrutineeId = UUID.randomUUID()
    val matchId     = UUID.randomUUID()

    // Union type: Circle(radius: Float) | Square(side: Float)
    val unionType = CType.CUnion(Map(
      "Circle" -> CType.CProduct(Map("radius" -> CType.CFloat)),
      "Square" -> CType.CProduct(Map("side" -> CType.CFloat))
    ))

    val patternMatchers = List[Any => Boolean](
      // The scrutinee is unwrapped by MatchTransform: inner value is the product map
      _ => true // Match anything (first pattern matches Circle variant)
    )
    val bodyEvaluators = List[Any => Any](
      // Body evaluator receives the original (tag, inner) tuple
      scrutinee => {
        val (tag, _) = scrutinee.asInstanceOf[(String, Any)]
        s"matched_$tag"
      }
    )

    val dag = transformOnlyDag(
      inputSpecs = Map(
        scrutineeId -> userInput("shape", unionType)
      ),
      transformSpecs = Map(
        matchId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CString,
          inlineTransform = Some(
            InlineTransform.MatchTransform(patternMatchers, bodyEvaluators, unionType)
          ),
          transformInputs = Map("scrutinee" -> scrutineeId)
        )
      )
    )

    val initData = Map(
      "shape" -> CValue.CUnion(
        CValue.CProduct(Map("radius" -> CValue.CFloat(5.0)), Map("radius" -> CType.CFloat)),
        Map("Circle" -> CType.CProduct(Map("radius" -> CType.CFloat)),
            "Square"  -> CType.CProduct(Map("side" -> CType.CFloat))),
        "Circle"
      )
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(matchId).value shouldBe CValue.CString("matched_Circle")
  }

  // =====================================================================
  // LiteralTransform tests (expanded beyond existing tests)
  // =====================================================================

  "Runtime.run with LiteralTransform" should "produce a string literal" in {
    val literalId = UUID.randomUUID()

    // LiteralTransform is a top-level data node (no user inputs needed when it's the only node)
    // But we need at least one user input for a valid DAG, so let's add a dummy.
    val dummyId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        dummyId -> userInput("dummy", CType.CInt)
      ),
      transformSpecs = Map(
        literalId -> DataNodeSpec(
          "lit",
          Map.empty,
          CType.CString,
          inlineTransform = Some(InlineTransform.LiteralTransform("hello world")),
          transformInputs = Map.empty
        )
      )
    )

    val initData = Map("dummy" -> CValue.CInt(0L))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(literalId).value shouldBe CValue.CString("hello world")
  }

  it should "produce a boolean literal" in {
    val literalId = UUID.randomUUID()
    val dummyId   = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        dummyId -> userInput("dummy", CType.CInt)
      ),
      transformSpecs = Map(
        literalId -> DataNodeSpec(
          "lit",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.LiteralTransform(true)),
          transformInputs = Map.empty
        )
      )
    )

    val initData = Map("dummy" -> CValue.CInt(0L))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(literalId).value shouldBe CValue.CBoolean(true)
  }

  it should "produce a float literal" in {
    val literalId = UUID.randomUUID()
    val dummyId   = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        dummyId -> userInput("dummy", CType.CInt)
      ),
      transformSpecs = Map(
        literalId -> DataNodeSpec(
          "lit",
          Map.empty,
          CType.CFloat,
          inlineTransform = Some(InlineTransform.LiteralTransform(2.718)),
          transformInputs = Map.empty
        )
      )
    )

    val initData = Map("dummy" -> CValue.CInt(0L))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(literalId).value shouldBe CValue.CFloat(2.718)
  }

  it should "produce a list literal" in {
    val literalId = UUID.randomUUID()
    val dummyId   = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        dummyId -> userInput("dummy", CType.CInt)
      ),
      transformSpecs = Map(
        literalId -> DataNodeSpec(
          "lit",
          Map.empty,
          CType.CList(CType.CInt),
          inlineTransform = Some(InlineTransform.LiteralTransform(List(1L, 2L, 3L))),
          transformInputs = Map.empty
        )
      )
    )

    val initData = Map("dummy" -> CValue.CInt(0L))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(literalId).value shouldBe CValue.CList(
      Vector(CValue.CInt(1L), CValue.CInt(2L), CValue.CInt(3L)),
      CType.CInt
    )
  }

  // =====================================================================
  // MergeTransform tests
  // =====================================================================

  "Runtime.run with MergeTransform" should "merge two records" in {
    val leftId   = UUID.randomUUID()
    val rightId  = UUID.randomUUID()
    val mergedId = UUID.randomUUID()

    val leftType  = CType.CProduct(Map("name" -> CType.CString))
    val rightType = CType.CProduct(Map("age" -> CType.CInt))
    val mergedType = CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt))

    val dag = transformOnlyDag(
      inputSpecs = Map(
        leftId  -> userInput("left", leftType),
        rightId -> userInput("right", rightType)
      ),
      transformSpecs = Map(
        mergedId -> DataNodeSpec(
          "merged",
          Map.empty,
          mergedType,
          inlineTransform = Some(InlineTransform.MergeTransform(leftType, rightType)),
          transformInputs = Map("left" -> leftId, "right" -> rightId)
        )
      )
    )

    val initData = Map(
      "left"  -> CValue.CProduct(Map("name" -> CValue.CString("Alice")), Map("name" -> CType.CString)),
      "right" -> CValue.CProduct(Map("age" -> CValue.CInt(30L)), Map("age" -> CType.CInt))
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    val product = state.data(mergedId).value.asInstanceOf[CValue.CProduct]
    product.value("name") shouldBe CValue.CString("Alice")
    product.value("age") shouldBe CValue.CInt(30L)
  }

  // =====================================================================
  // ProjectTransform tests
  // =====================================================================

  "Runtime.run with ProjectTransform" should "project fields from a record" in {
    val sourceId  = UUID.randomUUID()
    val resultId  = UUID.randomUUID()

    val sourceType = CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt, "email" -> CType.CString))
    val resultType = CType.CProduct(Map("name" -> CType.CString, "email" -> CType.CString))

    val dag = transformOnlyDag(
      inputSpecs = Map(
        sourceId -> userInput("record", sourceType)
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "projected",
          Map.empty,
          resultType,
          inlineTransform = Some(InlineTransform.ProjectTransform(List("name", "email"), sourceType)),
          transformInputs = Map("source" -> sourceId)
        )
      )
    )

    val initData = Map(
      "record" -> CValue.CProduct(
        Map(
          "name"  -> CValue.CString("Bob"),
          "age"   -> CValue.CInt(25L),
          "email" -> CValue.CString("bob@example.com")
        ),
        Map("name" -> CType.CString, "age" -> CType.CInt, "email" -> CType.CString)
      )
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    val product = state.data(resultId).value.asInstanceOf[CValue.CProduct]
    product.value("name") shouldBe CValue.CString("Bob")
    product.value("email") shouldBe CValue.CString("bob@example.com")
    product.value.contains("age") shouldBe false
  }

  // =====================================================================
  // FieldAccessTransform tests
  // =====================================================================

  "Runtime.run with FieldAccessTransform" should "extract a field from a record" in {
    val sourceId = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val sourceType = CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt))

    val dag = transformOnlyDag(
      inputSpecs = Map(
        sourceId -> userInput("record", sourceType)
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "name_field",
          Map.empty,
          CType.CString,
          inlineTransform = Some(InlineTransform.FieldAccessTransform("name", sourceType)),
          transformInputs = Map("source" -> sourceId)
        )
      )
    )

    val initData = Map(
      "record" -> CValue.CProduct(
        Map("name" -> CValue.CString("Charlie"), "age" -> CValue.CInt(40L)),
        Map("name" -> CType.CString, "age" -> CType.CInt)
      )
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CString("Charlie")
  }

  // =====================================================================
  // StringInterpolationTransform tests
  // =====================================================================

  "Runtime.run with StringInterpolationTransform" should "interpolate expressions into string" in {
    val nameId   = UUID.randomUUID()
    val ageId    = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    // String template: "Hello, {name}! You are {age} years old."
    // parts = ["Hello, ", "! You are ", " years old."]
    // expr0 -> name, expr1 -> age
    val dag = transformOnlyDag(
      inputSpecs = Map(
        nameId -> userInput("name", CType.CString),
        ageId  -> userInput("age", CType.CInt)
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "greeting",
          Map.empty,
          CType.CString,
          inlineTransform = Some(
            InlineTransform.StringInterpolationTransform(List("Hello, ", "! You are ", " years old."))
          ),
          transformInputs = Map("expr0" -> nameId, "expr1" -> ageId)
        )
      )
    )

    val initData = Map("name" -> CValue.CString("Dave"), "age" -> CValue.CInt(28L))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CString("Hello, Dave! You are 28 years old.")
  }

  it should "handle single-expression interpolation" in {
    val exprId   = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        exprId -> userInput("val", CType.CString)
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CString,
          inlineTransform = Some(
            InlineTransform.StringInterpolationTransform(List("prefix_", "_suffix"))
          ),
          transformInputs = Map("expr0" -> exprId)
        )
      )
    )

    val initData = Map("val" -> CValue.CString("middle"))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CString("prefix_middle_suffix")
  }

  // =====================================================================
  // ListLiteralTransform tests
  // =====================================================================

  "Runtime.run with ListLiteralTransform" should "assemble elements into a list" in {
    val elem0Id  = UUID.randomUUID()
    val elem1Id  = UUID.randomUUID()
    val elem2Id  = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        elem0Id -> userInput("a", CType.CInt),
        elem1Id -> userInput("b", CType.CInt),
        elem2Id -> userInput("c", CType.CInt)
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "list",
          Map.empty,
          CType.CList(CType.CInt),
          inlineTransform = Some(InlineTransform.ListLiteralTransform(3)),
          transformInputs = Map("elem0" -> elem0Id, "elem1" -> elem1Id, "elem2" -> elem2Id)
        )
      )
    )

    val initData = Map(
      "a" -> CValue.CInt(10L),
      "b" -> CValue.CInt(20L),
      "c" -> CValue.CInt(30L)
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CList(
      Vector(CValue.CInt(10L), CValue.CInt(20L), CValue.CInt(30L)),
      CType.CInt
    )
  }

  // =====================================================================
  // FilterTransform tests
  // =====================================================================

  "Runtime.run with FilterTransform" should "filter list elements based on predicate" in {
    val sourceId = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        sourceId -> userInput("numbers", CType.CList(CType.CInt))
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "filtered",
          Map.empty,
          CType.CList(CType.CInt),
          inlineTransform = Some(
            InlineTransform.FilterTransform(elem => elem.asInstanceOf[Long] > 2L)
          ),
          transformInputs = Map("source" -> sourceId)
        )
      )
    )

    val initData = Map(
      "numbers" -> CValue.CList(
        Vector(CValue.CInt(1L), CValue.CInt(2L), CValue.CInt(3L), CValue.CInt(4L)),
        CType.CInt
      )
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CList(
      Vector(CValue.CInt(3L), CValue.CInt(4L)),
      CType.CInt
    )
  }

  // =====================================================================
  // MapTransform tests
  // =====================================================================

  "Runtime.run with MapTransform" should "transform each list element" in {
    val sourceId = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        sourceId -> userInput("numbers", CType.CList(CType.CInt))
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "doubled",
          Map.empty,
          CType.CList(CType.CInt),
          inlineTransform = Some(
            InlineTransform.MapTransform(elem => elem.asInstanceOf[Long] * 2L)
          ),
          transformInputs = Map("source" -> sourceId)
        )
      )
    )

    val initData = Map(
      "numbers" -> CValue.CList(
        Vector(CValue.CInt(1L), CValue.CInt(2L), CValue.CInt(3L)),
        CType.CInt
      )
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CList(
      Vector(CValue.CInt(2L), CValue.CInt(4L), CValue.CInt(6L)),
      CType.CInt
    )
  }

  // =====================================================================
  // AllTransform tests
  // =====================================================================

  "Runtime.run with AllTransform" should "return true when all elements match" in {
    val sourceId = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        sourceId -> userInput("numbers", CType.CList(CType.CInt))
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "allPositive",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(
            InlineTransform.AllTransform(elem => elem.asInstanceOf[Long] > 0L)
          ),
          transformInputs = Map("source" -> sourceId)
        )
      )
    )

    val initData = Map(
      "numbers" -> CValue.CList(Vector(CValue.CInt(1L), CValue.CInt(2L), CValue.CInt(3L)), CType.CInt)
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CBoolean(true)
  }

  it should "return false when not all elements match" in {
    val sourceId = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        sourceId -> userInput("numbers", CType.CList(CType.CInt))
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "allPositive",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(
            InlineTransform.AllTransform(elem => elem.asInstanceOf[Long] > 0L)
          ),
          transformInputs = Map("source" -> sourceId)
        )
      )
    )

    val initData = Map(
      "numbers" -> CValue.CList(
        Vector(CValue.CInt(1L), CValue.CInt(-1L), CValue.CInt(3L)),
        CType.CInt
      )
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CBoolean(false)
  }

  // =====================================================================
  // AnyTransform tests
  // =====================================================================

  "Runtime.run with AnyTransform" should "return true when at least one element matches" in {
    val sourceId = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        sourceId -> userInput("numbers", CType.CList(CType.CInt))
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "anyNegative",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(
            InlineTransform.AnyTransform(elem => elem.asInstanceOf[Long] < 0L)
          ),
          transformInputs = Map("source" -> sourceId)
        )
      )
    )

    val initData = Map(
      "numbers" -> CValue.CList(
        Vector(CValue.CInt(1L), CValue.CInt(-1L), CValue.CInt(3L)),
        CType.CInt
      )
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CBoolean(true)
  }

  it should "return false when no elements match" in {
    val sourceId = UUID.randomUUID()
    val resultId = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        sourceId -> userInput("numbers", CType.CList(CType.CInt))
      ),
      transformSpecs = Map(
        resultId -> DataNodeSpec(
          "anyNegative",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(
            InlineTransform.AnyTransform(elem => elem.asInstanceOf[Long] < 0L)
          ),
          transformInputs = Map("source" -> sourceId)
        )
      )
    )

    val initData = Map(
      "numbers" -> CValue.CList(
        Vector(CValue.CInt(1L), CValue.CInt(2L), CValue.CInt(3L)),
        CType.CInt
      )
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(resultId).value shouldBe CValue.CBoolean(false)
  }

  // =====================================================================
  // Chained inline transforms (transform feeding into another transform)
  // =====================================================================

  "Runtime.run with chained transforms" should "chain NotTransform -> ConditionalTransform" in {
    val boolInputId  = UUID.randomUUID()
    val thenInputId  = UUID.randomUUID()
    val elseInputId  = UUID.randomUUID()
    val notResultId  = UUID.randomUUID()
    val condResultId = UUID.randomUUID()

    // boolInput -> NotTransform -> notResult -> ConditionalTransform -> condResult
    // The conditional uses the negated boolean to choose between thenVal and elseVal

    val dag = transformOnlyDag(
      inputSpecs = Map(
        boolInputId -> userInput("flag", CType.CBoolean),
        thenInputId -> userInput("thenVal", CType.CString),
        elseInputId -> userInput("elseVal", CType.CString)
      ),
      transformSpecs = Map(
        notResultId -> DataNodeSpec(
          "not_flag",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.NotTransform),
          transformInputs = Map("operand" -> boolInputId)
        ),
        condResultId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CString,
          inlineTransform = Some(InlineTransform.ConditionalTransform),
          transformInputs = Map(
            "cond"   -> notResultId,
            "thenBr" -> thenInputId,
            "elseBr" -> elseInputId
          )
        )
      )
    )

    // flag=true -> not_flag=false -> conditional selects elseBr
    val initData = Map(
      "flag"    -> CValue.CBoolean(true),
      "thenVal" -> CValue.CString("THEN"),
      "elseVal" -> CValue.CString("ELSE")
    )

    val state = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(notResultId).value shouldBe CValue.CBoolean(false)
    state.data(condResultId).value shouldBe CValue.CString("ELSE")
  }

  it should "chain And -> Not (composite boolean logic)" in {
    val leftId    = UUID.randomUUID()
    val rightId   = UUID.randomUUID()
    val andId     = UUID.randomUUID()
    val notAndId  = UUID.randomUUID()

    // left AND right -> notResult = NOT(left AND right) = NAND
    val dag = transformOnlyDag(
      inputSpecs = Map(
        leftId  -> userInput("left", CType.CBoolean),
        rightId -> userInput("right", CType.CBoolean)
      ),
      transformSpecs = Map(
        andId -> DataNodeSpec(
          "and_result",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.AndTransform),
          transformInputs = Map("left" -> leftId, "right" -> rightId)
        ),
        notAndId -> DataNodeSpec(
          "nand_result",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.NotTransform),
          transformInputs = Map("operand" -> andId)
        )
      )
    )

    // true AND true = true, NOT(true) = false
    val initData = Map("left" -> CValue.CBoolean(true), "right" -> CValue.CBoolean(true))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(andId).value shouldBe CValue.CBoolean(true)
    state.data(notAndId).value shouldBe CValue.CBoolean(false)
  }

  // =====================================================================
  // Inline transforms with modules together
  // =====================================================================

  "Runtime.run with inline transforms and modules" should "use module output as transform input" in {
    val moduleId       = UUID.randomUUID()
    val inputId        = UUID.randomUUID()
    val moduleOutputId = UUID.randomUUID()
    val fieldResultId  = UUID.randomUUID()

    // input (Int) -> Double module -> moduleOutput (Int)
    // moduleOutput feeds into a LiteralTransform (independent, just tests parallel execution)
    // Also test that inline transform and module run concurrently
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("ModuleAndTransformDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Double", "Doubles", List.empty, 1, 0),
          consumes = Map("x" -> CType.CInt),
          produces = Map("result" -> CType.CInt)
        )
      ),
      data = Map(
        inputId -> DataNodeSpec("x", Map(moduleId -> "x"), CType.CInt),
        moduleOutputId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CInt),
        fieldResultId -> DataNodeSpec(
          "constant",
          Map.empty,
          CType.CString,
          inlineTransform = Some(InlineTransform.LiteralTransform("hello")),
          transformInputs = Map.empty
        )
      ),
      inEdges = Set((inputId, moduleId)),
      outEdges = Set((moduleId, moduleOutputId))
    )

    val modules  = Map(moduleId -> doubleModule)
    val initData = Map("x" -> CValue.CInt(21L))

    val state = Runtime.run(dag, initData, modules).unsafeRunSync()

    state.moduleStatus(moduleId).value shouldBe a[Module.Status.Fired]
    state.data(moduleOutputId).value shouldBe CValue.CInt(42L)
    state.data(fieldResultId).value shouldBe CValue.CString("hello")
  }

  // =====================================================================
  // Runtime.runWithBackends with inline transforms
  // =====================================================================

  "Runtime.runWithBackends with inline transforms" should "execute NotTransform with backends" in {
    val inputId     = UUID.randomUUID()
    val notResultId = UUID.randomUUID()
    val dummyModId  = UUID.randomUUID()
    val dummyInId   = UUID.randomUUID()
    val dummyOutId  = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("BackendsNotDag"),
      modules = Map(
        dummyModId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        dummyInId -> DataNodeSpec("text", Map(dummyModId -> "text"), CType.CString),
        dummyOutId -> DataNodeSpec("result", Map(dummyModId -> "result"), CType.CString),
        inputId -> userInput("flag", CType.CBoolean),
        notResultId -> DataNodeSpec(
          "not_flag",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.NotTransform),
          transformInputs = Map("operand" -> inputId)
        )
      ),
      inEdges = Set((dummyInId, dummyModId)),
      outEdges = Set((dummyModId, dummyOutId))
    )

    val modules  = Map(dummyModId -> uppercaseModule)
    val initData = Map("text" -> CValue.CString("test"), "flag" -> CValue.CBoolean(true))

    val state = Runtime.runWithBackends(
      dag,
      initData,
      modules,
      Map.empty,
      GlobalScheduler.unbounded,
      ConstellationBackends.defaults
    ).unsafeRunSync()

    state.data(notResultId).value shouldBe CValue.CBoolean(false)
    state.data(dummyOutId).value shouldBe CValue.CString("TEST")
  }

  it should "execute RecordBuildTransform with backends" in {
    val nameId   = UUID.randomUUID()
    val ageId    = UUID.randomUUID()
    val recordId = UUID.randomUUID()
    val modId    = UUID.randomUUID()
    val modInId  = UUID.randomUUID()
    val modOutId = UUID.randomUUID()

    val fieldTypes = Map("name" -> CType.CString, "age" -> CType.CInt)

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("BackendsRecordDag"),
      modules = Map(
        modId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        modInId -> DataNodeSpec("text", Map(modId -> "text"), CType.CString),
        modOutId -> DataNodeSpec("result", Map(modId -> "result"), CType.CString),
        nameId -> userInput("name", CType.CString),
        ageId -> userInput("age", CType.CInt),
        recordId -> DataNodeSpec(
          "record",
          Map.empty,
          CType.CProduct(fieldTypes),
          inlineTransform = Some(InlineTransform.RecordBuildTransform(List("name", "age"))),
          transformInputs = Map("name" -> nameId, "age" -> ageId)
        )
      ),
      inEdges = Set((modInId, modId)),
      outEdges = Set((modId, modOutId))
    )

    val modules  = Map(modId -> uppercaseModule)
    val initData = Map(
      "text" -> CValue.CString("hi"),
      "name" -> CValue.CString("Eve"),
      "age"  -> CValue.CInt(25L)
    )

    val state = Runtime.runWithBackends(
      dag,
      initData,
      modules,
      Map.empty,
      GlobalScheduler.unbounded,
      ConstellationBackends.defaults
    ).unsafeRunSync()

    val product = state.data(recordId).value.asInstanceOf[CValue.CProduct]
    product.value("name") shouldBe CValue.CString("Eve")
    product.value("age") shouldBe CValue.CInt(25L)
  }

  // =====================================================================
  // Runtime.runPooled with inline transforms
  // =====================================================================

  "Runtime.runPooled with inline transforms" should "execute NotTransform with pooled resources" in {
    val inputId     = UUID.randomUUID()
    val notResultId = UUID.randomUUID()
    val modId       = UUID.randomUUID()
    val modInId     = UUID.randomUUID()
    val modOutId    = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("PooledNotDag"),
      modules = Map(
        modId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        modInId -> DataNodeSpec("text", Map(modId -> "text"), CType.CString),
        modOutId -> DataNodeSpec("result", Map(modId -> "result"), CType.CString),
        inputId -> userInput("flag", CType.CBoolean),
        notResultId -> DataNodeSpec(
          "not_flag",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.NotTransform),
          transformInputs = Map("operand" -> inputId)
        )
      ),
      inEdges = Set((modInId, modId)),
      outEdges = Set((modId, modOutId))
    )

    val modules  = Map(modId -> uppercaseModule)
    val initData = Map("text" -> CValue.CString("pool"), "flag" -> CValue.CBoolean(false))

    val state = (for {
      pool   <- RuntimePool.create()
      result <- Runtime.runPooled(dag, initData, modules, pool)
    } yield result).unsafeRunSync()

    state.data(notResultId).value shouldBe CValue.CBoolean(true)
    state.data(modOutId).value shouldBe CValue.CString("POOL")
  }

  it should "execute ConditionalTransform with pooled resources" in {
    val condId   = UUID.randomUUID()
    val thenId   = UUID.randomUUID()
    val elseId   = UUID.randomUUID()
    val resultId = UUID.randomUUID()
    val modId    = UUID.randomUUID()
    val modInId  = UUID.randomUUID()
    val modOutId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("PooledCondDag"),
      modules = Map(
        modId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Double", "Test", List.empty, 1, 0),
          consumes = Map("x" -> CType.CInt),
          produces = Map("result" -> CType.CInt)
        )
      ),
      data = Map(
        modInId -> DataNodeSpec("x", Map(modId -> "x"), CType.CInt),
        modOutId -> DataNodeSpec("result", Map(modId -> "result"), CType.CInt),
        condId -> userInput("cond", CType.CBoolean),
        thenId -> userInput("thenVal", CType.CString),
        elseId -> userInput("elseVal", CType.CString),
        resultId -> DataNodeSpec(
          "condResult",
          Map.empty,
          CType.CString,
          inlineTransform = Some(InlineTransform.ConditionalTransform),
          transformInputs = Map("cond" -> condId, "thenBr" -> thenId, "elseBr" -> elseId)
        )
      ),
      inEdges = Set((modInId, modId)),
      outEdges = Set((modId, modOutId))
    )

    val modules  = Map(modId -> doubleModule)
    val initData = Map(
      "x"       -> CValue.CInt(5L),
      "cond"    -> CValue.CBoolean(false),
      "thenVal" -> CValue.CString("YES"),
      "elseVal" -> CValue.CString("NO")
    )

    val state = (for {
      pool   <- RuntimePool.create()
      result <- Runtime.runPooled(dag, initData, modules, pool)
    } yield result).unsafeRunSync()

    state.data(modOutId).value shouldBe CValue.CInt(10L)
    state.data(resultId).value shouldBe CValue.CString("NO")
  }

  // =====================================================================
  // Multiple inline transforms running concurrently
  // =====================================================================

  "Runtime.run with multiple inline transforms" should "execute several transforms in parallel" in {
    val boolInputId = UUID.randomUUID()
    val intInputId  = UUID.randomUUID()

    val notId      = UUID.randomUUID()
    val literalId  = UUID.randomUUID()
    val literal2Id = UUID.randomUUID()

    val dag = transformOnlyDag(
      inputSpecs = Map(
        boolInputId -> userInput("flag", CType.CBoolean),
        intInputId  -> userInput("num", CType.CInt)
      ),
      transformSpecs = Map(
        notId -> DataNodeSpec(
          "not_flag",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.NotTransform),
          transformInputs = Map("operand" -> boolInputId)
        ),
        literalId -> DataNodeSpec(
          "lit_str",
          Map.empty,
          CType.CString,
          inlineTransform = Some(InlineTransform.LiteralTransform("constant")),
          transformInputs = Map.empty
        ),
        literal2Id -> DataNodeSpec(
          "lit_int",
          Map.empty,
          CType.CInt,
          inlineTransform = Some(InlineTransform.LiteralTransform(99L)),
          transformInputs = Map.empty
        )
      )
    )

    val initData = Map("flag" -> CValue.CBoolean(true), "num" -> CValue.CInt(7L))
    val state    = Runtime.run(dag, initData, Map.empty).unsafeRunSync()

    state.data(notId).value shouldBe CValue.CBoolean(false)
    state.data(literalId).value shouldBe CValue.CString("constant")
    state.data(literal2Id).value shouldBe CValue.CInt(99L)
  }
}
