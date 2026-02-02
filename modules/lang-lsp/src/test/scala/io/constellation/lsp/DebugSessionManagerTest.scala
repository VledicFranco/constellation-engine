package io.constellation.lsp

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class DebugSessionManagerTest extends AnyFlatSpec with Matchers {

  case class TestInput(x: Long)
  case class TestOutput(result: Long)

  private def createTestModule(name: String, multiplier: Long): Module.Uninitialized =
    ModuleBuilder
      .metadata(name, s"Test module $name", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x * multiplier))
      .build

  private def createSimpleDag(): (DagSpec, Map[UUID, Module.Uninitialized]) = {
    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Double", "Test", List.empty, 1, 0),
          consumes = Map("x" -> CType.CInt),
          produces = Map("result" -> CType.CInt)
        )
      ),
      data = Map(
        inputDataId  -> DataNodeSpec("x", Map(inputDataId -> "x", moduleId -> "x"), CType.CInt),
        outputDataId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CInt)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId)),
      declaredOutputs = List("result"),
      outputBindings = Map("result" -> outputDataId)
    )

    val modules = Map(moduleId -> createTestModule("Double", 2))

    (dag, modules)
  }

  // ========== Creation Tests ==========

  "DebugSessionManager" should "create empty manager" in {
    val result = for {
      manager <- DebugSessionManager.create
      count   <- manager.sessionCount
    } yield count

    result.unsafeRunSync() shouldBe 0
  }

  it should "create a debug session" in {
    val (dag, modules) = createSimpleDag()
    val inputs         = Map("x" -> CValue.CInt(21))

    val result = for {
      manager <- DebugSessionManager.create
      session <- manager.createSession(dag, Map.empty, modules, inputs)
      count   <- manager.sessionCount
    } yield (session, count)

    val (session, count) = result.unsafeRunSync()
    session.sessionId should not be empty
    session.batches.length should be > 0
    count shouldBe 1
  }

  // ========== Session Retrieval Tests ==========

  it should "retrieve existing session" in {
    val (dag, modules) = createSimpleDag()
    val inputs         = Map("x" -> CValue.CInt(21))

    val result = for {
      manager   <- DebugSessionManager.create
      created   <- manager.createSession(dag, Map.empty, modules, inputs)
      retrieved <- manager.getSession(created.sessionId)
    } yield (created.sessionId, retrieved)

    val (sessionId, retrieved) = result.unsafeRunSync()
    retrieved shouldBe defined
    retrieved.get.sessionId shouldBe sessionId
  }

  it should "return None for non-existent session" in {
    val result = for {
      manager   <- DebugSessionManager.create
      retrieved <- manager.getSession("non-existent-session-id")
    } yield retrieved

    result.unsafeRunSync() shouldBe None
  }

  // ========== Session Update Tests ==========

  it should "update session state" in {
    val (dag, modules) = createSimpleDag()
    val inputs         = Map("x" -> CValue.CInt(21))

    val result = for {
      manager <- DebugSessionManager.create
      created <- manager.createSession(dag, Map.empty, modules, inputs)
      // Modify the session
      modified = created.copy(currentBatchIndex = created.currentBatchIndex + 1)
      _         <- manager.updateSession(modified)
      retrieved <- manager.getSession(created.sessionId)
    } yield (created.currentBatchIndex, retrieved.map(_.currentBatchIndex))

    val (originalIndex, newIndex) = result.unsafeRunSync()
    newIndex shouldBe Some(originalIndex + 1)
  }

  it should "ignore update for non-existent session" in {
    val (dag, modules) = createSimpleDag()
    val inputs         = Map("x" -> CValue.CInt(21))

    val result = for {
      manager <- DebugSessionManager.create
      // Create a fake session that's not in the manager
      fakeSession = SteppedExecution.SessionState(
        sessionId = "fake-session",
        dagSpec = dag,
        compiledModules = modules,
        syntheticModules = Map.empty,
        inputs = inputs,
        batches = List.empty,
        currentBatchIndex = 0,
        nodeStates = Map.empty,
        runtimeOpt = None,
        runnableModules = Map.empty,
        startTime = System.currentTimeMillis()
      )
      _     <- manager.updateSession(fakeSession)
      count <- manager.sessionCount
    } yield count

    result.unsafeRunSync() shouldBe 0
  }

  // ========== Session Removal Tests ==========

  it should "remove existing session" in {
    val (dag, modules) = createSimpleDag()
    val inputs         = Map("x" -> CValue.CInt(21))

    val result = for {
      manager    <- DebugSessionManager.create
      created    <- manager.createSession(dag, Map.empty, modules, inputs)
      removed    <- manager.removeSession(created.sessionId)
      countAfter <- manager.sessionCount
      retrieved  <- manager.getSession(created.sessionId)
    } yield (removed, countAfter, retrieved)

    val (removed, countAfter, retrieved) = result.unsafeRunSync()
    removed shouldBe true
    countAfter shouldBe 0
    retrieved shouldBe None
  }

  it should "return false when removing non-existent session" in {
    val result = for {
      manager <- DebugSessionManager.create
      removed <- manager.removeSession("non-existent")
    } yield removed

    result.unsafeRunSync() shouldBe false
  }

  // ========== Stop Session Tests ==========

  it should "stop a session" in {
    val (dag, modules) = createSimpleDag()
    val inputs         = Map("x" -> CValue.CInt(21))

    val result = for {
      manager   <- DebugSessionManager.create
      created   <- manager.createSession(dag, Map.empty, modules, inputs)
      stopped   <- manager.stopSession(created.sessionId)
      retrieved <- manager.getSession(created.sessionId)
    } yield (stopped, retrieved)

    val (stopped, retrieved) = result.unsafeRunSync()
    stopped shouldBe true
    retrieved shouldBe None
  }

  // ========== Step Execution Tests ==========

  it should "step through execution" in {
    val (dag, modules) = createSimpleDag()
    val inputs         = Map("x" -> CValue.CInt(21))

    val result = for {
      manager    <- DebugSessionManager.create
      session    <- manager.createSession(dag, Map.empty, modules, inputs)
      stepResult <- manager.stepNext(session.sessionId)
    } yield stepResult

    val stepResult = result.unsafeRunSync()
    stepResult shouldBe defined
    stepResult.get._1.currentBatchIndex should be > 1 // Progressed past batch 0 and 1
  }

  it should "return None when stepping non-existent session" in {
    val result = for {
      manager    <- DebugSessionManager.create
      stepResult <- manager.stepNext("non-existent")
    } yield stepResult

    result.unsafeRunSync() shouldBe None
  }

  it should "execute to completion" in {
    val (dag, modules) = createSimpleDag()
    val inputs         = Map("x" -> CValue.CInt(21))

    val result = for {
      manager          <- DebugSessionManager.create
      session          <- manager.createSession(dag, Map.empty, modules, inputs)
      completedSession <- manager.stepContinue(session.sessionId)
    } yield completedSession

    val completedSession = result.unsafeRunSync()
    completedSession shouldBe defined
    completedSession.get.currentBatchIndex shouldBe completedSession.get.batches.length
  }

  it should "return None when continuing non-existent session" in {
    val result = for {
      manager        <- DebugSessionManager.create
      continueResult <- manager.stepContinue("non-existent")
    } yield continueResult

    result.unsafeRunSync() shouldBe None
  }

  // ========== Multiple Sessions Tests ==========

  it should "manage multiple sessions" in {
    val (dag, modules) = createSimpleDag()
    val inputs1        = Map("x" -> CValue.CInt(10))
    val inputs2        = Map("x" -> CValue.CInt(20))
    val inputs3        = Map("x" -> CValue.CInt(30))

    val result = for {
      manager  <- DebugSessionManager.create
      session1 <- manager.createSession(dag, Map.empty, modules, inputs1)
      session2 <- manager.createSession(dag, Map.empty, modules, inputs2)
      session3 <- manager.createSession(dag, Map.empty, modules, inputs3)
      count    <- manager.sessionCount
      // Each session should be retrievable
      retrieved1 <- manager.getSession(session1.sessionId)
      retrieved2 <- manager.getSession(session2.sessionId)
      retrieved3 <- manager.getSession(session3.sessionId)
    } yield (count, retrieved1.isDefined, retrieved2.isDefined, retrieved3.isDefined)

    val (count, has1, has2, has3) = result.unsafeRunSync()
    count shouldBe 3
    has1 shouldBe true
    has2 shouldBe true
    has3 shouldBe true
  }

  // ========== Session Access Time Update Tests ==========

  it should "update last accessed time on getSession" in {
    val (dag, modules) = createSimpleDag()
    val inputs         = Map("x" -> CValue.CInt(21))

    // This test verifies that getSession updates the last accessed time
    // We can't directly inspect the ManagedSession, but we can verify
    // the session remains accessible after multiple gets
    val result = for {
      manager   <- DebugSessionManager.create
      created   <- manager.createSession(dag, Map.empty, modules, inputs)
      _         <- manager.getSession(created.sessionId)
      _         <- manager.getSession(created.sessionId)
      _         <- manager.getSession(created.sessionId)
      retrieved <- manager.getSession(created.sessionId)
    } yield retrieved

    result.unsafeRunSync() shouldBe defined
  }

  // ========== Session Count Tests ==========

  it should "track session count correctly" in {
    val (dag, modules) = createSimpleDag()
    val inputs         = Map("x" -> CValue.CInt(21))

    val result = for {
      manager  <- DebugSessionManager.create
      count0   <- manager.sessionCount
      session1 <- manager.createSession(dag, Map.empty, modules, inputs)
      count1   <- manager.sessionCount
      session2 <- manager.createSession(dag, Map.empty, modules, inputs)
      count2   <- manager.sessionCount
      _        <- manager.removeSession(session1.sessionId)
      count3   <- manager.sessionCount
      _        <- manager.removeSession(session2.sessionId)
      count4   <- manager.sessionCount
    } yield (count0, count1, count2, count3, count4)

    val (count0, count1, count2, count3, count4) = result.unsafeRunSync()
    count0 shouldBe 0
    count1 shouldBe 1
    count2 shouldBe 2
    count3 shouldBe 1
    count4 shouldBe 0
  }

  // ========== Edge Cases ==========

  "DebugSessionManager with complex DAG" should "handle multi-level dependencies" in {
    // Create a DAG with multiple modules: A -> B -> C
    val moduleAId    = UUID.randomUUID()
    val moduleBId    = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val midDataId    = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("ChainDag"),
      modules = Map(
        moduleAId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Double", "Test", List.empty, 1, 0),
          consumes = Map("x" -> CType.CInt),
          produces = Map("result" -> CType.CInt)
        ),
        moduleBId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Triple", "Test", List.empty, 1, 0),
          consumes = Map("x" -> CType.CInt),
          produces = Map("result" -> CType.CInt)
        )
      ),
      data = Map(
        inputDataId -> DataNodeSpec(
          "input",
          Map(inputDataId -> "input", moduleAId -> "x"),
          CType.CInt
        ),
        midDataId -> DataNodeSpec("mid", Map(moduleAId -> "result", moduleBId -> "x"), CType.CInt),
        outputDataId -> DataNodeSpec("output", Map(moduleBId -> "result"), CType.CInt)
      ),
      inEdges = Set((inputDataId, moduleAId), (midDataId, moduleBId)),
      outEdges = Set((moduleAId, midDataId), (moduleBId, outputDataId)),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> outputDataId)
    )

    val modules = Map(
      moduleAId -> createTestModule("Double", 2),
      moduleBId -> createTestModule("Triple", 3)
    )

    val inputs = Map("input" -> CValue.CInt(10))

    val result = for {
      manager   <- DebugSessionManager.create
      session   <- manager.createSession(dag, Map.empty, modules, inputs)
      completed <- manager.stepContinue(session.sessionId)
    } yield completed

    val completed = result.unsafeRunSync()
    completed shouldBe defined
    // 10 * 2 = 20, 20 * 3 = 60
    completed.get.batches.length should be > 1
  }

  "Session isolation" should "ensure sessions don't interfere with each other" in {
    val (dag, modules) = createSimpleDag()
    val inputs1        = Map("x" -> CValue.CInt(10))
    val inputs2        = Map("x" -> CValue.CInt(100))

    val result = for {
      manager  <- DebugSessionManager.create
      session1 <- manager.createSession(dag, Map.empty, modules, inputs1)
      session2 <- manager.createSession(dag, Map.empty, modules, inputs2)
      // Step session1
      _ <- manager.stepNext(session1.sessionId)
      // Session2 should still be at initial state
      retrieved2 <- manager.getSession(session2.sessionId)
    } yield (session2.currentBatchIndex, retrieved2.map(_.currentBatchIndex))

    val (original, current) = result.unsafeRunSync()
    current shouldBe Some(original)
  }
}
