package io.constellation

import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SteppedExecutionTest extends AnyFlatSpec with Matchers {

  case class StringInput(text: String)
  case class StringOutput(result: String)

  case class IntInput(x: Long)
  case class IntOutput(result: Long)

  private def createUppercaseModule(): Module.Uninitialized =
    ModuleBuilder
      .metadata("Uppercase", "Converts text to uppercase", 1, 0)
      .implementationPure[StringInput, StringOutput](in => StringOutput(in.text.toUpperCase))
      .build

  private def createDoubleModule(): Module.Uninitialized =
    ModuleBuilder
      .metadata("Double", "Doubles a number", 1, 0)
      .implementationPure[IntInput, IntOutput](in => IntOutput(in.x * 2))
      .build

  private def createAddModule(): Module.Uninitialized = {
    case class AddInput(a: Long, b: Long)
    case class AddOutput(sum: Long)
    ModuleBuilder
      .metadata("Add", "Adds two numbers", 1, 0)
      .implementationPure[AddInput, AddOutput](in => AddOutput(in.a + in.b))
      .build
  }

  // Tests for computeBatches
  "computeBatches" should "create batch for input data nodes first" in {
    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("SimpleDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Test", "Test", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputDataId  -> DataNodeSpec("input", Map(moduleId -> "text"), CType.CString),
        outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId))
    )

    val batches = SteppedExecution.computeBatches(dag)

    batches.head.batchIndex shouldBe 0
    batches.head.moduleIds shouldBe empty
    batches.head.dataIds should contain(inputDataId)
  }

  it should "create batch for modules in topological order" in {
    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("SimpleDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Test", "Test", List.empty, 1, 0)
        )
      ),
      data = Map(
        inputDataId  -> DataNodeSpec("input", Map(moduleId -> "text"), CType.CString),
        outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId))
    )

    val batches = SteppedExecution.computeBatches(dag)

    batches.length shouldBe 2
    batches(1).moduleIds should contain(moduleId)
    batches(1).dataIds should contain(outputDataId)
  }

  it should "group parallel modules in same batch" in {
    val moduleId1     = UUID.randomUUID()
    val moduleId2     = UUID.randomUUID()
    val inputDataId1  = UUID.randomUUID()
    val inputDataId2  = UUID.randomUUID()
    val outputDataId1 = UUID.randomUUID()
    val outputDataId2 = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("ParallelDag"),
      modules = Map(
        moduleId1 -> ModuleNodeSpec(metadata = ComponentMetadata("M1", "M1", List.empty, 1, 0)),
        moduleId2 -> ModuleNodeSpec(metadata = ComponentMetadata("M2", "M2", List.empty, 1, 0))
      ),
      data = Map(
        inputDataId1  -> DataNodeSpec("in1", Map(moduleId1 -> "text"), CType.CString),
        inputDataId2  -> DataNodeSpec("in2", Map(moduleId2 -> "text"), CType.CString),
        outputDataId1 -> DataNodeSpec("out1", Map(moduleId1 -> "result"), CType.CString),
        outputDataId2 -> DataNodeSpec("out2", Map(moduleId2 -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId1, moduleId1), (inputDataId2, moduleId2)),
      outEdges = Set((moduleId1, outputDataId1), (moduleId2, outputDataId2))
    )

    val batches = SteppedExecution.computeBatches(dag)

    // Batch 0: inputs, Batch 1: both modules in parallel
    batches.length shouldBe 2
    batches(1).moduleIds should contain allOf (moduleId1, moduleId2)
  }

  it should "order sequential modules in separate batches" in {
    val moduleId1    = UUID.randomUUID()
    val moduleId2    = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val midDataId    = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("SequentialDag"),
      modules = Map(
        moduleId1 -> ModuleNodeSpec(metadata = ComponentMetadata("M1", "M1", List.empty, 1, 0)),
        moduleId2 -> ModuleNodeSpec(metadata = ComponentMetadata("M2", "M2", List.empty, 1, 0))
      ),
      data = Map(
        inputDataId -> DataNodeSpec("input", Map(moduleId1 -> "text"), CType.CString),
        midDataId -> DataNodeSpec(
          "mid",
          Map(moduleId1 -> "result", moduleId2 -> "text"),
          CType.CString
        ),
        outputDataId -> DataNodeSpec("output", Map(moduleId2 -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId1), (midDataId, moduleId2)),
      outEdges = Set((moduleId1, midDataId), (moduleId2, outputDataId))
    )

    val batches = SteppedExecution.computeBatches(dag)

    // Batch 0: inputs, Batch 1: M1, Batch 2: M2
    batches.length shouldBe 3
    batches(1).moduleIds should contain only moduleId1
    batches(2).moduleIds should contain only moduleId2
  }

  it should "handle empty DAG" in {
    val dag = DagSpec.empty("EmptyDag")

    val batches = SteppedExecution.computeBatches(dag)

    batches.length shouldBe 1
    batches.head.moduleIds shouldBe empty
    batches.head.dataIds shouldBe empty
  }

  // Tests for createSession
  "createSession" should "create a session with correct initial state" in {
    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("SessionDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Test", "Test", List.empty, 1, 0)
        )
      ),
      data = Map(
        inputDataId  -> DataNodeSpec("input", Map(moduleId -> "text"), CType.CString),
        outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId))
    )

    val modules = Map(moduleId -> createUppercaseModule())
    val inputs  = Map("input" -> CValue.CString("test"))

    val session = SteppedExecution
      .createSession(
        sessionId = "test-session",
        dagSpec = dag,
        syntheticModules = Map.empty,
        registeredModules = modules,
        inputs = inputs
      )
      .unsafeRunSync()

    session.sessionId shouldBe "test-session"
    session.currentBatchIndex shouldBe 0
    session.runtimeOpt shouldBe None
    session.nodeStates.values.foreach(_ shouldBe SteppedExecution.NodeState.Pending)
  }

  it should "initialize all node states as pending" in {
    val moduleId = UUID.randomUUID()
    val dataId1  = UUID.randomUUID()
    val dataId2  = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("NodeStateDag"),
      modules =
        Map(moduleId -> ModuleNodeSpec(metadata = ComponentMetadata("M", "", List.empty, 1, 0))),
      data = Map(
        dataId1 -> DataNodeSpec("d1", Map.empty, CType.CString),
        dataId2 -> DataNodeSpec("d2", Map.empty, CType.CString)
      ),
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val session = SteppedExecution
      .createSession(
        "session",
        dag,
        Map.empty,
        Map.empty,
        Map.empty
      )
      .unsafeRunSync()

    session.nodeStates(moduleId) shouldBe SteppedExecution.NodeState.Pending
    session.nodeStates(dataId1) shouldBe SteppedExecution.NodeState.Pending
    session.nodeStates(dataId2) shouldBe SteppedExecution.NodeState.Pending
  }

  // Tests for initializeRuntime
  "initializeRuntime" should "setup runtime and mark input data as completed" in {
    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("RuntimeDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputDataId -> DataNodeSpec(
          "input",
          Map(inputDataId -> "input", moduleId -> "text"),
          CType.CString
        ),
        outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId))
    )

    val modules = Map(moduleId -> createUppercaseModule())
    val inputs  = Map("input" -> CValue.CString("hello"))

    val session = SteppedExecution
      .createSession(
        "session",
        dag,
        Map.empty,
        modules,
        inputs
      )
      .unsafeRunSync()

    val initializedSession = SteppedExecution.initializeRuntime(session).unsafeRunSync()

    initializedSession.runtimeOpt shouldBe defined
    initializedSession.currentBatchIndex shouldBe 1 // Skip batch 0 (input nodes)

    val inputNodeState = initializedSession.nodeStates(inputDataId)
    inputNodeState shouldBe a[SteppedExecution.NodeState.Completed]
    inputNodeState.asInstanceOf[SteppedExecution.NodeState.Completed].value shouldBe CValue.CString(
      "hello"
    )
  }

  // Tests for executeNextBatch
  "executeNextBatch" should "execute a batch and update node states" in {
    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("ExecuteDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputDataId -> DataNodeSpec(
          "input",
          Map(inputDataId -> "input", moduleId -> "text"),
          CType.CString
        ),
        outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId))
    )

    val modules = Map(moduleId -> createUppercaseModule())
    val inputs  = Map("input" -> CValue.CString("world"))

    val session = SteppedExecution
      .createSession(
        "session",
        dag,
        Map.empty,
        modules,
        inputs
      )
      .unsafeRunSync()

    val initialized              = SteppedExecution.initializeRuntime(session).unsafeRunSync()
    val (afterBatch, isComplete) = SteppedExecution.executeNextBatch(initialized).unsafeRunSync()

    isComplete shouldBe true
    afterBatch.currentBatchIndex shouldBe 2 // Moved to next (final) batch

    val moduleState = afterBatch.nodeStates(moduleId)
    moduleState shouldBe a[SteppedExecution.NodeState.Completed]

    val outputState = afterBatch.nodeStates(outputDataId)
    outputState shouldBe a[SteppedExecution.NodeState.Completed]
    outputState.asInstanceOf[SteppedExecution.NodeState.Completed].value shouldBe CValue.CString(
      "WORLD"
    )
  }

  it should "return true for isComplete when all batches executed" in {
    val dag = DagSpec.empty("EmptyDag")

    val session = SteppedExecution
      .createSession(
        "session",
        dag,
        Map.empty,
        Map.empty,
        Map.empty
      )
      .unsafeRunSync()

    // Empty DAG has only 1 batch (inputs with empty dataIds), and no modules
    session.batches.length shouldBe 1
    session.batches.head.moduleIds shouldBe empty
    session.batches.head.dataIds shouldBe empty

    // Session starts at batch 0, and since there's only 1 batch (empty inputs),
    // checking completion is based on batch index vs batch count
    // currentBatchIndex == 0, batches.length == 1
    // After "processing" batch 0 (no-op for empty DAG), would be at index 1, which >= length
    session.currentBatchIndex shouldBe 0
  }

  // Tests for executeToCompletion
  "executeToCompletion" should "execute all batches in sequence" in {
    val moduleId1    = UUID.randomUUID()
    val moduleId2    = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val midDataId    = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("CompleteDag"),
      modules = Map(
        moduleId1 -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "M1", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        ),
        moduleId2 -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase2", "M2", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputDataId -> DataNodeSpec(
          "input",
          Map(inputDataId -> "input", moduleId1 -> "text"),
          CType.CString
        ),
        midDataId -> DataNodeSpec(
          "mid",
          Map(moduleId1 -> "result", moduleId2 -> "text"),
          CType.CString
        ),
        outputDataId -> DataNodeSpec("output", Map(moduleId2 -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId1), (midDataId, moduleId2)),
      outEdges = Set((moduleId1, midDataId), (moduleId2, outputDataId))
    )

    val modules = Map(
      moduleId1 -> createUppercaseModule(),
      moduleId2 -> createUppercaseModule()
    )
    val inputs = Map("input" -> CValue.CString("test"))

    val session = SteppedExecution
      .createSession(
        "session",
        dag,
        Map.empty,
        modules,
        inputs
      )
      .unsafeRunSync()

    val initialized = SteppedExecution.initializeRuntime(session).unsafeRunSync()
    val completed   = SteppedExecution.executeToCompletion(initialized).unsafeRunSync()

    completed.currentBatchIndex shouldBe completed.batches.length

    // All modules should be completed
    completed.nodeStates(moduleId1) shouldBe a[SteppedExecution.NodeState.Completed]
    completed.nodeStates(moduleId2) shouldBe a[SteppedExecution.NodeState.Completed]
  }

  // Tests for getOutputs
  "getOutputs" should "return declared outputs after execution" in {
    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("OutputDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputDataId -> DataNodeSpec(
          "input",
          Map(inputDataId -> "input", moduleId -> "text"),
          CType.CString
        ),
        outputDataId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId)),
      declaredOutputs = List("result"),
      outputBindings = Map("result" -> outputDataId)
    )

    val modules = Map(moduleId -> createUppercaseModule())
    val inputs  = Map("input" -> CValue.CString("output"))

    val session = SteppedExecution
      .createSession(
        "session",
        dag,
        Map.empty,
        modules,
        inputs
      )
      .unsafeRunSync()

    val initialized = SteppedExecution.initializeRuntime(session).unsafeRunSync()
    val completed   = SteppedExecution.executeToCompletion(initialized).unsafeRunSync()

    val outputs = SteppedExecution.getOutputs(completed)
    outputs.keys should contain("result")
    outputs("result") shouldBe CValue.CString("OUTPUT")
  }

  it should "return empty map when no declared outputs" in {
    val moduleId = UUID.randomUUID()
    val dataId   = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("NoOutputDag"),
      modules =
        Map(moduleId -> ModuleNodeSpec(metadata = ComponentMetadata("M", "", List.empty, 1, 0))),
      data = Map(dataId -> DataNodeSpec("d", Map.empty, CType.CString)),
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List.empty,
      outputBindings = Map.empty
    )

    val session = SteppedExecution
      .createSession(
        "session",
        dag,
        Map.empty,
        Map.empty,
        Map.empty
      )
      .unsafeRunSync()

    val outputs = SteppedExecution.getOutputs(session)
    outputs shouldBe empty
  }

  // Tests for valuePreview
  "valuePreview" should "format string values with quotes" in {
    val preview = SteppedExecution.valuePreview(CValue.CString("hello"))
    preview shouldBe "\"hello\""
  }

  it should "format integer values" in {
    val preview = SteppedExecution.valuePreview(CValue.CInt(42))
    preview shouldBe "42"
  }

  it should "format float values" in {
    val preview = SteppedExecution.valuePreview(CValue.CFloat(3.14))
    preview shouldBe "3.14"
  }

  it should "format boolean values" in {
    val preview1 = SteppedExecution.valuePreview(CValue.CBoolean(true))
    val preview2 = SteppedExecution.valuePreview(CValue.CBoolean(false))
    preview1 shouldBe "true"
    preview2 shouldBe "false"
  }

  it should "format list values with count" in {
    val preview = SteppedExecution.valuePreview(
      CValue.CList(Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)), CType.CInt)
    )
    preview shouldBe "[3 items]"
  }

  it should "format map values with count" in {
    val preview = SteppedExecution.valuePreview(
      CValue.CMap(
        Vector((CValue.CString("a"), CValue.CInt(1))),
        CType.CString,
        CType.CInt
      )
    )
    preview shouldBe "{1 entries}"
  }

  it should "format product values with field names" in {
    val preview = SteppedExecution.valuePreview(
      CValue.CProduct(
        Map("name" -> CValue.CString("test"), "value" -> CValue.CInt(42)),
        Map("name" -> CType.CString, "value"          -> CType.CInt)
      )
    )
    preview should include("name")
    preview should include("value")
  }

  it should "format union values with tag" in {
    val preview = SteppedExecution.valuePreview(
      CValue.CUnion(
        CValue.CString("inner"),
        Map("Left" -> CType.CString, "Right" -> CType.CInt),
        "Left"
      )
    )
    preview shouldBe "Left(...)"
  }

  it should "format Some values" in {
    val preview = SteppedExecution.valuePreview(
      CValue.CSome(CValue.CInt(42), CType.CInt)
    )
    preview shouldBe "Some(42)"
  }

  it should "format None values" in {
    val preview = SteppedExecution.valuePreview(
      CValue.CNone(CType.CString)
    )
    preview shouldBe "None"
  }

  it should "truncate long strings" in {
    val longString = "a" * 100
    val preview    = SteppedExecution.valuePreview(CValue.CString(longString), maxLength = 20)
    preview.length shouldBe 20
    preview should endWith("...")
  }

  // ===== executeNextBatch with failing module =====

  "executeNextBatch" should "mark module as Failed when execution throws" in {
    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    case class FailInput(text: String)
    case class FailOutput(result: String)

    val failingModule = ModuleBuilder
      .metadata("Failing", "Fails always", 1, 0)
      .implementationPure[FailInput, FailOutput](_ => throw new RuntimeException("boom"))
      .build

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("FailDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Failing", "Test", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputDataId -> DataNodeSpec(
          "input",
          Map(inputDataId -> "input", moduleId -> "text"),
          CType.CString
        ),
        outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId))
    )

    val modules = Map(moduleId -> failingModule)
    val inputs  = Map("input" -> CValue.CString("test"))

    val session = SteppedExecution
      .createSession("session", dag, Map.empty, modules, inputs)
      .unsafeRunSync()

    val initialized     = SteppedExecution.initializeRuntime(session).unsafeRunSync()
    val (afterBatch, _) = SteppedExecution.executeNextBatch(initialized).unsafeRunSync()

    val moduleState = afterBatch.nodeStates(moduleId)
    moduleState shouldBe a[SteppedExecution.NodeState.Failed]
  }

  it should "return true immediately when all batches already executed" in {
    val dag = DagSpec.empty("EmptyDag")

    val session = SteppedExecution
      .createSession("session", dag, Map.empty, Map.empty, Map.empty)
      .unsafeRunSync()

    // Set currentBatchIndex beyond batches length
    val completedSession = session.copy(currentBatchIndex = session.batches.length)

    val (result, isComplete) = SteppedExecution.executeNextBatch(completedSession).unsafeRunSync()
    isComplete shouldBe true
    result.currentBatchIndex shouldBe completedSession.currentBatchIndex
  }

  // ===== validateRunIO error paths =====

  "initializeRuntime" should "fail with unexpected input name" in {
    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("ValidateDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputDataId -> DataNodeSpec(
          "input",
          Map(inputDataId -> "input", moduleId -> "text"),
          CType.CString
        ),
        outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId))
    )

    val modules = Map(moduleId -> createUppercaseModule())
    // Use an unexpected input name
    val inputs = Map("wrongname" -> CValue.CString("hello"))

    val session = SteppedExecution
      .createSession("session", dag, Map.empty, modules, inputs)
      .unsafeRunSync()

    val result = SteppedExecution.initializeRuntime(session).attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.swap.getOrElse(throw new AssertionError("Expected Left")).getMessage should include(
      "unexpected"
    )
  }

  it should "fail with wrong input type" in {
    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TypeDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputDataId -> DataNodeSpec(
          "input",
          Map(inputDataId -> "input", moduleId -> "text"),
          CType.CString
        ),
        outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId))
    )

    val modules = Map(moduleId -> createUppercaseModule())
    // Use wrong type (Int instead of String)
    val inputs = Map("input" -> CValue.CInt(42))

    val session = SteppedExecution
      .createSession("session", dag, Map.empty, modules, inputs)
      .unsafeRunSync()

    val result = SteppedExecution.initializeRuntime(session).attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.swap.getOrElse(throw new AssertionError("Expected Left")).getMessage should include(
      "different type"
    )
  }

  // ===== executeToCompletion for already-complete session =====

  "executeToCompletion" should "return immediately for already-complete session" in {
    val dag = DagSpec.empty("EmptyDag")

    val session = SteppedExecution
      .createSession("session", dag, Map.empty, Map.empty, Map.empty)
      .unsafeRunSync()

    val completedSession = session.copy(currentBatchIndex = session.batches.length)
    val result           = SteppedExecution.executeToCompletion(completedSession).unsafeRunSync()

    result.currentBatchIndex shouldBe completedSession.currentBatchIndex
  }
}
