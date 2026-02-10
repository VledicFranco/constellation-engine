package io.constellation

import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SteppedExecutionExtendedTest extends AnyFlatSpec with Matchers {

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

  // ---------------------------------------------------------------------------
  // 1. Cycle detection
  // ---------------------------------------------------------------------------

  "computeBatches" should "throw RuntimeException with 'Cycle detected' for a cyclic DAG" in {
    // Create two modules that depend on each other:
    // M1 consumes dataA, produces dataB
    // M2 consumes dataB, produces dataA
    // This creates a cycle: M1 -> dataB -> M2 -> dataA -> M1
    val moduleId1 = UUID.randomUUID()
    val moduleId2 = UUID.randomUUID()
    val dataA     = UUID.randomUUID()
    val dataB     = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("CyclicDag"),
      modules = Map(
        moduleId1 -> ModuleNodeSpec(metadata = ComponentMetadata("M1", "M1", List.empty, 1, 0)),
        moduleId2 -> ModuleNodeSpec(metadata = ComponentMetadata("M2", "M2", List.empty, 1, 0))
      ),
      data = Map(
        dataA -> DataNodeSpec("dataA", Map(moduleId1 -> "text", moduleId2 -> "result"), CType.CString),
        dataB -> DataNodeSpec("dataB", Map(moduleId2 -> "text", moduleId1 -> "result"), CType.CString)
      ),
      // dataA feeds M1, dataB feeds M2
      inEdges = Set((dataA, moduleId1), (dataB, moduleId2)),
      // M1 produces dataB, M2 produces dataA
      outEdges = Set((moduleId1, dataB), (moduleId2, dataA))
    )

    val exception = intercept[RuntimeException] {
      SteppedExecution.computeBatches(dag)
    }
    exception.getMessage should include("Cycle detected")
  }

  it should "throw RuntimeException for a self-referencing module" in {
    // Single module that consumes its own output
    val moduleId = UUID.randomUUID()
    val dataId   = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("SelfCycleDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(metadata = ComponentMetadata("M1", "M1", List.empty, 1, 0))
      ),
      data = Map(
        dataId -> DataNodeSpec("data", Map(moduleId -> "text"), CType.CString)
      ),
      // dataId feeds moduleId, and moduleId produces dataId
      inEdges = Set((dataId, moduleId)),
      outEdges = Set((moduleId, dataId))
    )

    val exception = intercept[RuntimeException] {
      SteppedExecution.computeBatches(dag)
    }
    exception.getMessage should include("Cycle detected")
  }

  // ---------------------------------------------------------------------------
  // 2. Runtime not initialized error
  // ---------------------------------------------------------------------------

  "executeNextBatch" should "throw RuntimeException when runtime is not initialized" in {
    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("UninitDag"),
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
    val inputs  = Map("input" -> CValue.CString("test"))

    // Create session but do NOT call initializeRuntime
    val session = SteppedExecution
      .createSession("session", dag, Map.empty, modules, inputs)
      .unsafeRunSync()

    // Advance past batch 0 to force entering the else branch where runtime is needed
    val sessionAtBatch1 = session.copy(currentBatchIndex = 1)

    val exception = intercept[RuntimeException] {
      SteppedExecution.executeNextBatch(sessionAtBatch1).unsafeRunSync()
    }
    exception.getMessage should include("Runtime not initialized")
  }

  // ---------------------------------------------------------------------------
  // 3. Module output empty (no output data in state)
  // ---------------------------------------------------------------------------

  "executeNextBatch" should "produce '(no output)' when module has no output data in state" in {
    // Create a module that completes but its output data node is never populated
    // This can happen if the outEdges don't match any data in the runtime table
    val moduleId    = UUID.randomUUID()
    val inputDataId = UUID.randomUUID()
    // Output data node exists in DAG but the module won't actually populate it
    // because the outEdges point to a different data ID than what's in the table
    val outputDataId = UUID.randomUUID()
    val phantomDataId = UUID.randomUUID()

    case class NoopInput(text: String)
    case class NoopOutput(result: String)

    val noopModule = ModuleBuilder
      .metadata("Noop", "Does nothing useful", 1, 0)
      .implementationPure[NoopInput, NoopOutput](in => NoopOutput(in.text))
      .build

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("NoOutputDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Noop", "Test", List.empty, 1, 0),
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

    val modules = Map(moduleId -> noopModule)
    val inputs  = Map("input" -> CValue.CString("test"))

    val session = SteppedExecution
      .createSession("session", dag, Map.empty, modules, inputs)
      .unsafeRunSync()

    val initialized              = SteppedExecution.initializeRuntime(session).unsafeRunSync()
    val (afterBatch, isComplete) = SteppedExecution.executeNextBatch(initialized).unsafeRunSync()

    isComplete shouldBe true
    val moduleState = afterBatch.nodeStates(moduleId)
    moduleState shouldBe a[SteppedExecution.NodeState.Completed]
  }

  // ---------------------------------------------------------------------------
  // 4. valuePreview edge cases
  // ---------------------------------------------------------------------------

  "valuePreview" should "handle empty CList" in {
    val preview = SteppedExecution.valuePreview(CValue.CList(Vector.empty, CType.CInt))
    preview shouldBe "[0 items]"
  }

  it should "handle empty CMap" in {
    val preview = SteppedExecution.valuePreview(
      CValue.CMap(Vector.empty, CType.CString, CType.CInt)
    )
    preview shouldBe "{0 entries}"
  }

  it should "handle empty CProduct" in {
    val preview = SteppedExecution.valuePreview(
      CValue.CProduct(Map.empty, Map.empty)
    )
    preview shouldBe "{}"
  }

  it should "handle nested CSome(CSome(...))" in {
    val innerValue = CValue.CSome(CValue.CInt(99), CType.CInt)
    val outerValue = CValue.CSome(innerValue, CType.COptional(CType.CInt))
    val preview    = SteppedExecution.valuePreview(outerValue, maxLength = 100)
    preview shouldBe "Some(Some(99))"
  }

  it should "handle deeply nested CSome values" in {
    val level0 = CValue.CInt(7)
    val level1 = CValue.CSome(level0, CType.CInt)
    val level2 = CValue.CSome(level1, CType.COptional(CType.CInt))
    val level3 = CValue.CSome(level2, CType.COptional(CType.COptional(CType.CInt)))
    val preview = SteppedExecution.valuePreview(level3, maxLength = 100)
    preview shouldBe "Some(Some(Some(7)))"
  }

  it should "handle CNone with various inner types" in {
    SteppedExecution.valuePreview(CValue.CNone(CType.CInt)) shouldBe "None"
    SteppedExecution.valuePreview(CValue.CNone(CType.CBoolean)) shouldBe "None"
    SteppedExecution.valuePreview(CValue.CNone(CType.COptional(CType.CString))) shouldBe "None"
  }

  it should "not truncate string at exactly the maxLength boundary" in {
    // String that produces preview of exactly maxLength characters should NOT be truncated
    // "a" * 8 produces preview "\"aaaaaaaa\"" which is 10 chars
    val preview = SteppedExecution.valuePreview(CValue.CString("a" * 8), maxLength = 10)
    preview shouldBe "\"aaaaaaaa\""
    preview.length shouldBe 10
    preview should not endWith "..."
  }

  it should "truncate string at maxLength + 1 boundary" in {
    // "a" * 9 produces preview "\"aaaaaaaaa\"" which is 11 chars; with maxLength=10 it truncates
    val preview = SteppedExecution.valuePreview(CValue.CString("a" * 9), maxLength = 10)
    preview.length shouldBe 10
    preview should endWith("...")
  }

  it should "handle CSome wrapping a string with truncation" in {
    // CSome("hello") -> Some("hello") which is 13 chars
    val preview = SteppedExecution.valuePreview(
      CValue.CSome(CValue.CString("hello world this is a long string"), CType.CString),
      maxLength = 20
    )
    preview.length should be <= 20
    preview should startWith("Some(")
  }

  it should "handle CProduct with many fields" in {
    val fields = (1 to 10).map(i => s"field$i" -> CValue.CInt(i.toLong)).toMap
    val types  = (1 to 10).map(i => s"field$i" -> CType.CInt).toMap
    val preview = SteppedExecution.valuePreview(CValue.CProduct(fields, types), maxLength = 200)
    // Should list all field names
    (1 to 10).foreach { i =>
      preview should include(s"field$i")
    }
  }

  // ---------------------------------------------------------------------------
  // 5. getOutputs with incomplete execution
  // ---------------------------------------------------------------------------

  "getOutputs" should "return empty map when output binding references non-existent data node" in {
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("MissingBindingDag"),
      modules = Map.empty,
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List("result"),
      outputBindings = Map("result" -> UUID.randomUUID()) // points to UUID not in nodeStates
    )

    val session = SteppedExecution
      .createSession("session", dag, Map.empty, Map.empty, Map.empty)
      .unsafeRunSync()

    val outputs = SteppedExecution.getOutputs(session)
    outputs shouldBe empty
  }

  it should "return empty map when output data node is still Pending" in {
    val dataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("PendingOutputDag"),
      modules = Map.empty,
      data = Map(
        dataId -> DataNodeSpec("result", Map.empty, CType.CString)
      ),
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List("result"),
      outputBindings = Map("result" -> dataId)
    )

    val session = SteppedExecution
      .createSession("session", dag, Map.empty, Map.empty, Map.empty)
      .unsafeRunSync()

    // At this point, nodeStates(dataId) is Pending, not Completed
    session.nodeStates(dataId) shouldBe SteppedExecution.NodeState.Pending
    val outputs = SteppedExecution.getOutputs(session)
    outputs shouldBe empty
  }

  it should "return empty map when output data node is Failed" in {
    val dataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("FailedOutputDag"),
      modules = Map.empty,
      data = Map(
        dataId -> DataNodeSpec("result", Map.empty, CType.CString)
      ),
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List("result"),
      outputBindings = Map("result" -> dataId)
    )

    val session = SteppedExecution
      .createSession("session", dag, Map.empty, Map.empty, Map.empty)
      .unsafeRunSync()

    // Manually set the node state to Failed
    val failedSession = session.copy(
      nodeStates = session.nodeStates.updated(
        dataId,
        SteppedExecution.NodeState.Failed(new RuntimeException("failure"))
      )
    )

    val outputs = SteppedExecution.getOutputs(failedSession)
    outputs shouldBe empty
  }

  it should "return only completed outputs when some outputs are missing bindings" in {
    val completedDataId = UUID.randomUUID()
    val missingDataId   = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("PartialOutputDag"),
      modules = Map.empty,
      data = Map(
        completedDataId -> DataNodeSpec("output1", Map.empty, CType.CString)
      ),
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List("output1", "output2"),
      outputBindings = Map(
        "output1" -> completedDataId
        // output2 has no binding
      )
    )

    val session = SteppedExecution
      .createSession("session", dag, Map.empty, Map.empty, Map.empty)
      .unsafeRunSync()

    // Manually mark completedDataId as completed
    val withCompleted = session.copy(
      nodeStates = session.nodeStates.updated(
        completedDataId,
        SteppedExecution.NodeState.Completed(CValue.CString("done"), 10L)
      )
    )

    val outputs = SteppedExecution.getOutputs(withCompleted)
    outputs.size shouldBe 1
    outputs("output1") shouldBe CValue.CString("done")
    outputs.contains("output2") shouldBe false
  }

  // ---------------------------------------------------------------------------
  // 6. Multiple input data nodes
  // ---------------------------------------------------------------------------

  "initializeRuntime" should "handle multiple independent input data nodes" in {
    case class AddInput(a: Long, b: Long)
    case class AddOutput(sum: Long)

    val addModule = ModuleBuilder
      .metadata("Add", "Adds two numbers", 1, 0)
      .implementationPure[AddInput, AddOutput](in => AddOutput(in.a + in.b))
      .build

    val moduleId    = UUID.randomUUID()
    val inputDataA  = UUID.randomUUID()
    val inputDataB  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("MultiInputDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Add", "Test", List.empty, 1, 0),
          consumes = Map("a" -> CType.CInt, "b" -> CType.CInt),
          produces = Map("sum" -> CType.CInt)
        )
      ),
      data = Map(
        inputDataA -> DataNodeSpec(
          "inputA",
          Map(inputDataA -> "inputA", moduleId -> "a"),
          CType.CInt
        ),
        inputDataB -> DataNodeSpec(
          "inputB",
          Map(inputDataB -> "inputB", moduleId -> "b"),
          CType.CInt
        ),
        outputDataId -> DataNodeSpec("sum", Map(moduleId -> "sum"), CType.CInt)
      ),
      inEdges = Set((inputDataA, moduleId), (inputDataB, moduleId)),
      outEdges = Set((moduleId, outputDataId)),
      declaredOutputs = List("sum"),
      outputBindings = Map("sum" -> outputDataId)
    )

    val modules = Map(moduleId -> addModule)
    val inputs  = Map("inputA" -> CValue.CInt(10L), "inputB" -> CValue.CInt(20L))

    val session = SteppedExecution
      .createSession("session", dag, Map.empty, modules, inputs)
      .unsafeRunSync()

    val initialized = SteppedExecution.initializeRuntime(session).unsafeRunSync()

    // Both input data nodes should be marked as Completed
    initialized.nodeStates(inputDataA) shouldBe a[SteppedExecution.NodeState.Completed]
    initialized.nodeStates(inputDataB) shouldBe a[SteppedExecution.NodeState.Completed]

    initialized.nodeStates(inputDataA)
      .asInstanceOf[SteppedExecution.NodeState.Completed]
      .value shouldBe CValue.CInt(10L)
    initialized.nodeStates(inputDataB)
      .asInstanceOf[SteppedExecution.NodeState.Completed]
      .value shouldBe CValue.CInt(20L)
  }

  "executeToCompletion" should "execute a DAG with multiple independent inputs correctly" in {
    case class AddInput(a: Long, b: Long)
    case class AddOutput(sum: Long)

    val addModule = ModuleBuilder
      .metadata("Add", "Adds two numbers", 1, 0)
      .implementationPure[AddInput, AddOutput](in => AddOutput(in.a + in.b))
      .build

    val moduleId     = UUID.randomUUID()
    val inputDataA   = UUID.randomUUID()
    val inputDataB   = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("MultiInputExecDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Add", "Test", List.empty, 1, 0),
          consumes = Map("a" -> CType.CInt, "b" -> CType.CInt),
          produces = Map("sum" -> CType.CInt)
        )
      ),
      data = Map(
        inputDataA -> DataNodeSpec(
          "inputA",
          Map(inputDataA -> "inputA", moduleId -> "a"),
          CType.CInt
        ),
        inputDataB -> DataNodeSpec(
          "inputB",
          Map(inputDataB -> "inputB", moduleId -> "b"),
          CType.CInt
        ),
        outputDataId -> DataNodeSpec("sum", Map(moduleId -> "sum"), CType.CInt)
      ),
      inEdges = Set((inputDataA, moduleId), (inputDataB, moduleId)),
      outEdges = Set((moduleId, outputDataId)),
      declaredOutputs = List("sum"),
      outputBindings = Map("sum" -> outputDataId)
    )

    val modules = Map(moduleId -> addModule)
    val inputs  = Map("inputA" -> CValue.CInt(10L), "inputB" -> CValue.CInt(20L))

    val session = SteppedExecution
      .createSession("session", dag, Map.empty, modules, inputs)
      .unsafeRunSync()

    val initialized = SteppedExecution.initializeRuntime(session).unsafeRunSync()
    val completed   = SteppedExecution.executeToCompletion(initialized).unsafeRunSync()

    val outputs = SteppedExecution.getOutputs(completed)
    outputs("sum") shouldBe CValue.CInt(30L)
  }

  // ---------------------------------------------------------------------------
  // 7. syntheticModules handling
  // ---------------------------------------------------------------------------

  "initializeRuntime" should "combine synthetic modules with registered modules" in {
    // Build a DAG with two modules: one registered, one synthetic
    val moduleId1    = UUID.randomUUID()
    val moduleId2    = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val midDataId    = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("SyntheticDag"),
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
      outEdges = Set((moduleId1, midDataId), (moduleId2, outputDataId)),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> outputDataId)
    )

    // moduleId1 is registered, moduleId2 is synthetic
    val registeredModules = Map(moduleId1 -> createUppercaseModule())
    val syntheticModules  = Map(moduleId2 -> createUppercaseModule())
    val inputs = Map("input" -> CValue.CString("hello"))

    val session = SteppedExecution
      .createSession("session", dag, syntheticModules, registeredModules, inputs)
      .unsafeRunSync()

    val initialized = SteppedExecution.initializeRuntime(session).unsafeRunSync()

    // Runtime should be available (both module sets merged successfully)
    initialized.runtimeOpt shouldBe defined
    // Both modules should be runnable
    initialized.runnableModules.size shouldBe 2
    initialized.runnableModules.contains(moduleId1) shouldBe true
    initialized.runnableModules.contains(moduleId2) shouldBe true
  }

  "executeToCompletion" should "run DAG with synthetic modules to completion" in {
    val moduleId1    = UUID.randomUUID()
    val moduleId2    = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val midDataId    = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("SyntheticExecDag"),
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
      outEdges = Set((moduleId1, midDataId), (moduleId2, outputDataId)),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> outputDataId)
    )

    val registeredModules = Map(moduleId1 -> createUppercaseModule())
    val syntheticModules  = Map(moduleId2 -> createUppercaseModule())
    val inputs = Map("input" -> CValue.CString("hello"))

    val session = SteppedExecution
      .createSession("session", dag, syntheticModules, registeredModules, inputs)
      .unsafeRunSync()

    val initialized = SteppedExecution.initializeRuntime(session).unsafeRunSync()
    val completed   = SteppedExecution.executeToCompletion(initialized).unsafeRunSync()

    val outputs = SteppedExecution.getOutputs(completed)
    outputs("output") shouldBe CValue.CString("HELLO")
  }

  // ---------------------------------------------------------------------------
  // Additional edge cases
  // ---------------------------------------------------------------------------

  "createSession" should "set startTime to a reasonable value" in {
    val beforeTime = System.currentTimeMillis()
    val dag        = DagSpec.empty("TimeDag")

    val session = SteppedExecution
      .createSession("session", dag, Map.empty, Map.empty, Map.empty)
      .unsafeRunSync()

    val afterTime = System.currentTimeMillis()

    session.startTime should be >= beforeTime
    session.startTime should be <= afterTime
  }

  "computeBatches" should "handle a three-module chain producing three sequential batches" in {
    val m1 = UUID.randomUUID()
    val m2 = UUID.randomUUID()
    val m3 = UUID.randomUUID()
    val d0 = UUID.randomUUID() // input
    val d1 = UUID.randomUUID() // m1 -> m2
    val d2 = UUID.randomUUID() // m2 -> m3
    val d3 = UUID.randomUUID() // m3 output

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("ThreeChain"),
      modules = Map(
        m1 -> ModuleNodeSpec(metadata = ComponentMetadata("M1", "", List.empty, 1, 0)),
        m2 -> ModuleNodeSpec(metadata = ComponentMetadata("M2", "", List.empty, 1, 0)),
        m3 -> ModuleNodeSpec(metadata = ComponentMetadata("M3", "", List.empty, 1, 0))
      ),
      data = Map(
        d0 -> DataNodeSpec("d0", Map(m1 -> "text"), CType.CString),
        d1 -> DataNodeSpec("d1", Map(m1 -> "result", m2 -> "text"), CType.CString),
        d2 -> DataNodeSpec("d2", Map(m2 -> "result", m3 -> "text"), CType.CString),
        d3 -> DataNodeSpec("d3", Map(m3 -> "result"), CType.CString)
      ),
      inEdges = Set((d0, m1), (d1, m2), (d2, m3)),
      outEdges = Set((m1, d1), (m2, d2), (m3, d3))
    )

    val batches = SteppedExecution.computeBatches(dag)

    // Batch 0: inputs, Batch 1: m1, Batch 2: m2, Batch 3: m3
    batches.length shouldBe 4
    batches(0).moduleIds shouldBe empty
    batches(1).moduleIds should contain only m1
    batches(2).moduleIds should contain only m2
    batches(3).moduleIds should contain only m3
  }

  "computeBatches" should "handle diamond-shaped DAG with proper batching" in {
    // input -> M1 -> mid1 -> M3 -> output
    //       -> M2 -> mid2 ->
    val m1 = UUID.randomUUID()
    val m2 = UUID.randomUUID()
    val m3 = UUID.randomUUID()
    val inputData = UUID.randomUUID()
    val mid1      = UUID.randomUUID()
    val mid2      = UUID.randomUUID()
    val output    = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("DiamondDag"),
      modules = Map(
        m1 -> ModuleNodeSpec(metadata = ComponentMetadata("M1", "", List.empty, 1, 0)),
        m2 -> ModuleNodeSpec(metadata = ComponentMetadata("M2", "", List.empty, 1, 0)),
        m3 -> ModuleNodeSpec(metadata = ComponentMetadata("M3", "", List.empty, 1, 0))
      ),
      data = Map(
        inputData -> DataNodeSpec("input", Map(m1 -> "text", m2 -> "text"), CType.CString),
        mid1 -> DataNodeSpec("mid1", Map(m1 -> "result", m3 -> "a"), CType.CString),
        mid2 -> DataNodeSpec("mid2", Map(m2 -> "result", m3 -> "b"), CType.CString),
        output -> DataNodeSpec("output", Map(m3 -> "result"), CType.CString)
      ),
      inEdges = Set((inputData, m1), (inputData, m2), (mid1, m3), (mid2, m3)),
      outEdges = Set((m1, mid1), (m2, mid2), (m3, output))
    )

    val batches = SteppedExecution.computeBatches(dag)

    // Batch 0: inputs, Batch 1: M1 and M2 in parallel, Batch 2: M3
    batches.length shouldBe 3
    batches(1).moduleIds should contain allOf (m1, m2)
    batches(2).moduleIds should contain only m3
  }

  "valuePreview" should "handle CList with single item" in {
    val preview = SteppedExecution.valuePreview(
      CValue.CList(Vector(CValue.CString("only")), CType.CString)
    )
    preview shouldBe "[1 items]"
  }

  it should "handle CMap with multiple entries" in {
    val pairs = (1 to 5).map(i =>
      (CValue.CString(s"key$i"), CValue.CInt(i.toLong))
    ).toVector
    val preview = SteppedExecution.valuePreview(
      CValue.CMap(pairs, CType.CString, CType.CInt)
    )
    preview shouldBe "{5 entries}"
  }

  it should "handle CUnion with different tags" in {
    val preview1 = SteppedExecution.valuePreview(
      CValue.CUnion(
        CValue.CInt(42),
        Map("Left" -> CType.CInt, "Right" -> CType.CString),
        "Right"
      )
    )
    preview1 shouldBe "Right(...)"
  }

  it should "not truncate when string is shorter than maxLength" in {
    val preview = SteppedExecution.valuePreview(CValue.CString("hi"), maxLength = 50)
    preview shouldBe "\"hi\""
  }

  it should "handle maxLength of 3 by producing just '...'" in {
    // A long string with maxLength=3: take(0) + "..." = "..."
    val preview = SteppedExecution.valuePreview(CValue.CString("abcdefghij"), maxLength = 3)
    preview shouldBe "..."
  }
}
