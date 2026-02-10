package io.constellation

import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SyntheticModuleFactoryTest extends AnyFlatSpec with Matchers {

  // ===== fromDagSpec detection =====

  "SyntheticModuleFactory.fromDagSpec" should "detect branch modules by name" in {
    val branchId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        branchId -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-0", "Branch module", List.empty, 1, 0),
          consumes =
            Map("cond0" -> CType.CBoolean, "expr0" -> CType.CString, "otherwise" -> CType.CString),
          produces = Map("out" -> CType.CString)
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result should have size 1
    result should contain key branchId
  }

  it should "ignore non-branch modules" in {
    val moduleId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Not a branch", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result shouldBe empty
  }

  it should "return empty for empty dag" in {
    val dag    = DagSpec.empty("EmptyDag")
    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result shouldBe empty
  }

  it should "detect multiple branch modules" in {
    val branch1  = UUID.randomUUID()
    val branch2  = UUID.randomUUID()
    val normalId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        branch1 -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-0", "First branch", List.empty, 1, 0),
          consumes =
            Map("cond0" -> CType.CBoolean, "expr0" -> CType.CInt, "otherwise" -> CType.CInt),
          produces = Map("out" -> CType.CInt)
        ),
        branch2 -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-1", "Second branch", List.empty, 1, 0),
          consumes =
            Map("cond0" -> CType.CBoolean, "expr0" -> CType.CString, "otherwise" -> CType.CString),
          produces = Map("out" -> CType.CString)
        ),
        normalId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Transform", "Normal module", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result should have size 2
    result should contain key branch1
    result should contain key branch2
    result should not contain key(normalId)
  }

  it should "use default CType.CString when 'out' not in produces" in {
    val branchId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        branchId -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-fallback", "Branch no out", List.empty, 1, 0),
          consumes =
            Map("cond0" -> CType.CBoolean, "expr0" -> CType.CString, "otherwise" -> CType.CString),
          produces = Map.empty // No "out" key
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result should have size 1
    // The module was created - the output type defaults to CType.CString
  }

  it should "count multiple condition cases correctly" in {
    val branchId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        branchId -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-multi", "Multi-branch", List.empty, 1, 0),
          consumes = Map(
            "cond0"     -> CType.CBoolean,
            "expr0"     -> CType.CInt,
            "cond1"     -> CType.CBoolean,
            "expr1"     -> CType.CInt,
            "cond2"     -> CType.CBoolean,
            "expr2"     -> CType.CInt,
            "otherwise" -> CType.CInt
          ),
          produces = Map("out" -> CType.CInt)
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result should have size 1
  }

  // ===== Full integration: init and run a branch module =====

  "Branch module" should "execute single-case branch selecting true path" in {
    val branchId    = UUID.randomUUID()
    val cond0Id     = UUID.randomUUID()
    val expr0Id     = UUID.randomUUID()
    val otherwiseId = UUID.randomUUID()
    val outId       = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("BranchDag"),
      modules = Map(
        branchId -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-0", "Branch", List.empty, 1, 0),
          consumes =
            Map("cond0" -> CType.CBoolean, "expr0" -> CType.CString, "otherwise" -> CType.CString),
          produces = Map("out" -> CType.CString)
        )
      ),
      data = Map(
        cond0Id     -> DataNodeSpec("cond0", Map(branchId -> "cond0"), CType.CBoolean),
        expr0Id     -> DataNodeSpec("expr0", Map(branchId -> "expr0"), CType.CString),
        otherwiseId -> DataNodeSpec("otherwise", Map(branchId -> "otherwise"), CType.CString),
        outId       -> DataNodeSpec("out", Map(branchId -> "out"), CType.CString)
      ),
      inEdges = Set((cond0Id, branchId), (expr0Id, branchId), (otherwiseId, branchId)),
      outEdges = Set((branchId, outId))
    )

    val syntheticModules = SyntheticModuleFactory.fromDagSpec(dag)
    syntheticModules should have size 1

    val branchModule = syntheticModules(branchId)

    val state = (for {
      // Init the module
      runnable <- branchModule.init(branchId, dag)
      // Create state
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = runnable.data, state = stateRef)
      // Provide input values
      _ <- runtime.setTableData(cond0Id, true: Any)
      _ <- runtime.setTableData(expr0Id, "yes-path": Any)
      _ <- runtime.setTableData(otherwiseId, "no-path": Any)
      // Run the module
      _ <- runnable.run(runtime)
      // Get result
      result     <- runtime.getTableData(outId)
      finalState <- stateRef.get
    } yield (result, finalState)).unsafeRunSync()

    state._1 shouldBe "yes-path"
  }

  it should "execute single-case branch selecting otherwise path" in {
    val branchId    = UUID.randomUUID()
    val cond0Id     = UUID.randomUUID()
    val expr0Id     = UUID.randomUUID()
    val otherwiseId = UUID.randomUUID()
    val outId       = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("BranchDag"),
      modules = Map(
        branchId -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-0", "Branch", List.empty, 1, 0),
          consumes =
            Map("cond0" -> CType.CBoolean, "expr0" -> CType.CString, "otherwise" -> CType.CString),
          produces = Map("out" -> CType.CString)
        )
      ),
      data = Map(
        cond0Id     -> DataNodeSpec("cond0", Map(branchId -> "cond0"), CType.CBoolean),
        expr0Id     -> DataNodeSpec("expr0", Map(branchId -> "expr0"), CType.CString),
        otherwiseId -> DataNodeSpec("otherwise", Map(branchId -> "otherwise"), CType.CString),
        outId       -> DataNodeSpec("out", Map(branchId -> "out"), CType.CString)
      ),
      inEdges = Set((cond0Id, branchId), (expr0Id, branchId), (otherwiseId, branchId)),
      outEdges = Set((branchId, outId))
    )

    val syntheticModules = SyntheticModuleFactory.fromDagSpec(dag)
    val branchModule     = syntheticModules(branchId)

    val result = (for {
      runnable <- branchModule.init(branchId, dag)
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = runnable.data, state = stateRef)
      _   <- runtime.setTableData(cond0Id, false: Any)
      _   <- runtime.setTableData(expr0Id, "yes-path": Any)
      _   <- runtime.setTableData(otherwiseId, "no-path": Any)
      _   <- runnable.run(runtime)
      res <- runtime.getTableData(outId)
    } yield res).unsafeRunSync()

    result shouldBe "no-path"
  }

  it should "execute multi-case branch selecting second condition" in {
    val branchId    = UUID.randomUUID()
    val cond0Id     = UUID.randomUUID()
    val expr0Id     = UUID.randomUUID()
    val cond1Id     = UUID.randomUUID()
    val expr1Id     = UUID.randomUUID()
    val otherwiseId = UUID.randomUUID()
    val outId       = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("MultiBranchDag"),
      modules = Map(
        branchId -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-0", "Multi-branch", List.empty, 1, 0),
          consumes = Map(
            "cond0"     -> CType.CBoolean,
            "expr0"     -> CType.CInt,
            "cond1"     -> CType.CBoolean,
            "expr1"     -> CType.CInt,
            "otherwise" -> CType.CInt
          ),
          produces = Map("out" -> CType.CInt)
        )
      ),
      data = Map(
        cond0Id     -> DataNodeSpec("cond0", Map(branchId -> "cond0"), CType.CBoolean),
        expr0Id     -> DataNodeSpec("expr0", Map(branchId -> "expr0"), CType.CInt),
        cond1Id     -> DataNodeSpec("cond1", Map(branchId -> "cond1"), CType.CBoolean),
        expr1Id     -> DataNodeSpec("expr1", Map(branchId -> "expr1"), CType.CInt),
        otherwiseId -> DataNodeSpec("otherwise", Map(branchId -> "otherwise"), CType.CInt),
        outId       -> DataNodeSpec("out", Map(branchId -> "out"), CType.CInt)
      ),
      inEdges = Set(
        (cond0Id, branchId),
        (expr0Id, branchId),
        (cond1Id, branchId),
        (expr1Id, branchId),
        (otherwiseId, branchId)
      ),
      outEdges = Set((branchId, outId))
    )

    val syntheticModules = SyntheticModuleFactory.fromDagSpec(dag)
    val branchModule     = syntheticModules(branchId)

    val result = (for {
      runnable <- branchModule.init(branchId, dag)
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = runnable.data, state = stateRef)
      // cond0 false, cond1 true -> should select expr1
      _   <- runtime.setTableData(cond0Id, false: Any)
      _   <- runtime.setTableData(expr0Id, 10L: Any)
      _   <- runtime.setTableData(cond1Id, true: Any)
      _   <- runtime.setTableData(expr1Id, 20L: Any)
      _   <- runtime.setTableData(otherwiseId, 30L: Any)
      _   <- runnable.run(runtime)
      res <- runtime.getTableData(outId)
    } yield res).unsafeRunSync()

    result shouldBe 20L
  }

  it should "execute multi-case branch selecting otherwise when all conditions false" in {
    val branchId    = UUID.randomUUID()
    val cond0Id     = UUID.randomUUID()
    val expr0Id     = UUID.randomUUID()
    val cond1Id     = UUID.randomUUID()
    val expr1Id     = UUID.randomUUID()
    val otherwiseId = UUID.randomUUID()
    val outId       = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("MultiBranchDag"),
      modules = Map(
        branchId -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-0", "Multi-branch", List.empty, 1, 0),
          consumes = Map(
            "cond0"     -> CType.CBoolean,
            "expr0"     -> CType.CInt,
            "cond1"     -> CType.CBoolean,
            "expr1"     -> CType.CInt,
            "otherwise" -> CType.CInt
          ),
          produces = Map("out" -> CType.CInt)
        )
      ),
      data = Map(
        cond0Id     -> DataNodeSpec("cond0", Map(branchId -> "cond0"), CType.CBoolean),
        expr0Id     -> DataNodeSpec("expr0", Map(branchId -> "expr0"), CType.CInt),
        cond1Id     -> DataNodeSpec("cond1", Map(branchId -> "cond1"), CType.CBoolean),
        expr1Id     -> DataNodeSpec("expr1", Map(branchId -> "expr1"), CType.CInt),
        otherwiseId -> DataNodeSpec("otherwise", Map(branchId -> "otherwise"), CType.CInt),
        outId       -> DataNodeSpec("out", Map(branchId -> "out"), CType.CInt)
      ),
      inEdges = Set(
        (cond0Id, branchId),
        (expr0Id, branchId),
        (cond1Id, branchId),
        (expr1Id, branchId),
        (otherwiseId, branchId)
      ),
      outEdges = Set((branchId, outId))
    )

    val syntheticModules = SyntheticModuleFactory.fromDagSpec(dag)
    val branchModule     = syntheticModules(branchId)

    val result = (for {
      runnable <- branchModule.init(branchId, dag)
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = runnable.data, state = stateRef)
      // All conditions false -> should select otherwise
      _   <- runtime.setTableData(cond0Id, false: Any)
      _   <- runtime.setTableData(expr0Id, 10L: Any)
      _   <- runtime.setTableData(cond1Id, false: Any)
      _   <- runtime.setTableData(expr1Id, 20L: Any)
      _   <- runtime.setTableData(otherwiseId, 30L: Any)
      _   <- runnable.run(runtime)
      res <- runtime.getTableData(outId)
    } yield res).unsafeRunSync()

    result shouldBe 30L
  }

  it should "store CValue in state data after execution" in {
    val branchId    = UUID.randomUUID()
    val cond0Id     = UUID.randomUUID()
    val expr0Id     = UUID.randomUUID()
    val otherwiseId = UUID.randomUUID()
    val outId       = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("BranchDag"),
      modules = Map(
        branchId -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-0", "Branch", List.empty, 1, 0),
          consumes =
            Map("cond0" -> CType.CBoolean, "expr0" -> CType.CInt, "otherwise" -> CType.CInt),
          produces = Map("out" -> CType.CInt)
        )
      ),
      data = Map(
        cond0Id     -> DataNodeSpec("cond0", Map(branchId -> "cond0"), CType.CBoolean),
        expr0Id     -> DataNodeSpec("expr0", Map(branchId -> "expr0"), CType.CInt),
        otherwiseId -> DataNodeSpec("otherwise", Map(branchId -> "otherwise"), CType.CInt),
        outId       -> DataNodeSpec("out", Map(branchId -> "out"), CType.CInt)
      ),
      inEdges = Set((cond0Id, branchId), (expr0Id, branchId), (otherwiseId, branchId)),
      outEdges = Set((branchId, outId))
    )

    val syntheticModules = SyntheticModuleFactory.fromDagSpec(dag)
    val branchModule     = syntheticModules(branchId)

    val finalState = (for {
      runnable <- branchModule.init(branchId, dag)
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = runnable.data, state = stateRef)
      _     <- runtime.setTableData(cond0Id, true: Any)
      _     <- runtime.setTableData(expr0Id, 42L: Any)
      _     <- runtime.setTableData(otherwiseId, 0L: Any)
      _     <- runnable.run(runtime)
      state <- stateRef.get
    } yield state).unsafeRunSync()

    // The branch module should set state data for the output
    finalState.data should contain key outId
    finalState.data(outId).value shouldBe CValue.CInt(42L)
  }
}
