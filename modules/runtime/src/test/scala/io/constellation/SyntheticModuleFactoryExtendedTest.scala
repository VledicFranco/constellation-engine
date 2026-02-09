package io.constellation

import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SyntheticModuleFactoryExtendedTest extends AnyFlatSpec with Matchers {

  // ===== fromDagSpec with ConditionalTransform inline data nodes =====

  "SyntheticModuleFactory.fromDagSpec" should "return empty map when DagSpec has only data nodes with ConditionalTransform" in {
    // ConditionalTransform is an inline transform on data nodes, not a branch module.
    // The factory only looks at module nodes whose name contains "branch-".
    val condDataId = UUID.randomUUID()
    val thenDataId = UUID.randomUUID()
    val elseDataId = UUID.randomUUID()
    val resultId   = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("ConditionalDag"),
      modules = Map.empty,
      data = Map(
        condDataId -> DataNodeSpec("cond", Map.empty, CType.CBoolean),
        thenDataId -> DataNodeSpec("thenBr", Map.empty, CType.CString),
        elseDataId -> DataNodeSpec("elseBr", Map.empty, CType.CString),
        resultId -> DataNodeSpec(
          "result",
          Map.empty,
          CType.CString,
          inlineTransform = Some(InlineTransform.ConditionalTransform),
          transformInputs = Map("cond" -> condDataId, "thenBr" -> thenDataId, "elseBr" -> elseDataId)
        )
      ),
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result shouldBe empty
  }

  it should "return empty map when DagSpec has only non-branch modules and inline transforms" in {
    val moduleId   = UUID.randomUUID()
    val inputId    = UUID.randomUUID()
    val outputId   = UUID.randomUUID()
    val mergeId    = UUID.randomUUID()
    val leftId     = UUID.randomUUID()
    val rightId    = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("MixedDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Text transform", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputId -> DataNodeSpec("text", Map(moduleId -> "text"), CType.CString),
        outputId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CString),
        leftId -> DataNodeSpec("left", Map.empty, CType.CProduct(Map("a" -> CType.CString))),
        rightId -> DataNodeSpec("right", Map.empty, CType.CProduct(Map("b" -> CType.CInt))),
        mergeId -> DataNodeSpec(
          "merged",
          Map.empty,
          CType.CProduct(Map("a" -> CType.CString, "b" -> CType.CInt)),
          inlineTransform = Some(InlineTransform.MergeTransform(
            CType.CProduct(Map("a" -> CType.CString)),
            CType.CProduct(Map("b" -> CType.CInt))
          )),
          transformInputs = Map("left" -> leftId, "right" -> rightId)
        )
      ),
      inEdges = Set((inputId, moduleId)),
      outEdges = Set((moduleId, outputId))
    )

    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result shouldBe empty
  }

  it should "return empty map for DagSpec.empty" in {
    val dag = DagSpec.empty("EmptyDag")
    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result shouldBe empty
  }

  // ===== fromDagSpec with multiple inline transforms =====

  it should "return empty map for DagSpec with multiple different inline transforms but no branch modules" in {
    val srcId       = UUID.randomUUID()
    val fieldId     = UUID.randomUUID()
    val condId      = UUID.randomUUID()
    val thenId      = UUID.randomUUID()
    val elseId      = UUID.randomUUID()
    val condResultId = UUID.randomUUID()
    val literalId   = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("InlineOnlyDag"),
      modules = Map.empty,
      data = Map(
        srcId -> DataNodeSpec("source", Map.empty, CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt))),
        fieldId -> DataNodeSpec(
          "nameField",
          Map.empty,
          CType.CString,
          inlineTransform = Some(InlineTransform.FieldAccessTransform("name", CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt)))),
          transformInputs = Map("source" -> srcId)
        ),
        condId -> DataNodeSpec("cond", Map.empty, CType.CBoolean),
        thenId -> DataNodeSpec("thenBr", Map.empty, CType.CString),
        elseId -> DataNodeSpec("elseBr", Map.empty, CType.CString),
        condResultId -> DataNodeSpec(
          "condResult",
          Map.empty,
          CType.CString,
          inlineTransform = Some(InlineTransform.ConditionalTransform),
          transformInputs = Map("cond" -> condId, "thenBr" -> thenId, "elseBr" -> elseId)
        ),
        literalId -> DataNodeSpec(
          "literal42",
          Map.empty,
          CType.CInt,
          inlineTransform = Some(InlineTransform.LiteralTransform(42L)),
          transformInputs = Map.empty
        )
      ),
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result shouldBe empty
  }

  // ===== fromDagSpec detection with branch modules mixed with data-level inline transforms =====

  it should "detect branch modules even when DagSpec also contains inline transformed data nodes" in {
    val branchId    = UUID.randomUUID()
    val cond0Id     = UUID.randomUUID()
    val expr0Id     = UUID.randomUUID()
    val otherwiseId = UUID.randomUUID()
    val outId       = UUID.randomUUID()
    val litId       = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("MixedDag"),
      modules = Map(
        branchId -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-0", "Branch module", List.empty, 1, 0),
          consumes = Map("cond0" -> CType.CBoolean, "expr0" -> CType.CString, "otherwise" -> CType.CString),
          produces = Map("out" -> CType.CString)
        )
      ),
      data = Map(
        cond0Id -> DataNodeSpec("cond0", Map(branchId -> "cond0"), CType.CBoolean),
        expr0Id -> DataNodeSpec("expr0", Map(branchId -> "expr0"), CType.CString),
        otherwiseId -> DataNodeSpec("otherwise", Map(branchId -> "otherwise"), CType.CString),
        outId -> DataNodeSpec("out", Map(branchId -> "out"), CType.CString),
        litId -> DataNodeSpec(
          "literal",
          Map.empty,
          CType.CInt,
          inlineTransform = Some(InlineTransform.LiteralTransform(99L)),
          transformInputs = Map.empty
        )
      ),
      inEdges = Set((cond0Id, branchId), (expr0Id, branchId), (otherwiseId, branchId)),
      outEdges = Set((branchId, outId))
    )

    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result should have size 1
    result should contain key branchId
  }

  // ===== Branch module naming patterns =====

  it should "detect branch module regardless of suffix after 'branch-'" in {
    val branchId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        branchId -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-custom-name-42", "Custom branch", List.empty, 1, 0),
          consumes = Map("cond0" -> CType.CBoolean, "expr0" -> CType.CString, "otherwise" -> CType.CString),
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

  it should "not detect a module named 'mybranch' (no 'branch-' substring)" in {
    val moduleId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("mybranch", "Not a branch module", List.empty, 1, 0),
          consumes = Map("cond0" -> CType.CBoolean, "expr0" -> CType.CString, "otherwise" -> CType.CString),
          produces = Map("out" -> CType.CString)
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result shouldBe empty
  }

  it should "detect a module with 'branch-' in the middle of the name" in {
    val moduleId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("my-branch-0", "Branch in middle", List.empty, 1, 0),
          consumes = Map("cond0" -> CType.CBoolean, "expr0" -> CType.CString, "otherwise" -> CType.CString),
          produces = Map("out" -> CType.CString)
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result should have size 1
  }

  // ===== Branch module with zero condition cases =====

  it should "create a branch module with zero conditions (only otherwise)" in {
    val branchId    = UUID.randomUUID()
    val otherwiseId = UUID.randomUUID()
    val outId       = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("ZeroBranchDag"),
      modules = Map(
        branchId -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-0", "Zero-case branch", List.empty, 1, 0),
          consumes = Map("otherwise" -> CType.CString),
          produces = Map("out" -> CType.CString)
        )
      ),
      data = Map(
        otherwiseId -> DataNodeSpec("otherwise", Map(branchId -> "otherwise"), CType.CString),
        outId -> DataNodeSpec("out", Map(branchId -> "out"), CType.CString)
      ),
      inEdges = Set((otherwiseId, branchId)),
      outEdges = Set((branchId, outId))
    )

    val syntheticModules = SyntheticModuleFactory.fromDagSpec(dag)
    syntheticModules should have size 1

    // Execute: with zero conditions, it should always select otherwise
    val result = (for {
      runnable <- syntheticModules(branchId).init(branchId, dag)
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = runnable.data, state = stateRef)
      _   <- runtime.setTableData(otherwiseId, "fallback-value": Any)
      _   <- runnable.run(runtime)
      res <- runtime.getTableData(outId)
    } yield res).unsafeRunSync()

    result shouldBe "fallback-value"
  }

  // ===== Branch module output type variants =====

  it should "create a branch module with CBoolean output type" in {
    val branchId    = UUID.randomUUID()
    val cond0Id     = UUID.randomUUID()
    val expr0Id     = UUID.randomUUID()
    val otherwiseId = UUID.randomUUID()
    val outId       = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("BoolBranchDag"),
      modules = Map(
        branchId -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-bool", "Boolean branch", List.empty, 1, 0),
          consumes = Map("cond0" -> CType.CBoolean, "expr0" -> CType.CBoolean, "otherwise" -> CType.CBoolean),
          produces = Map("out" -> CType.CBoolean)
        )
      ),
      data = Map(
        cond0Id -> DataNodeSpec("cond0", Map(branchId -> "cond0"), CType.CBoolean),
        expr0Id -> DataNodeSpec("expr0", Map(branchId -> "expr0"), CType.CBoolean),
        otherwiseId -> DataNodeSpec("otherwise", Map(branchId -> "otherwise"), CType.CBoolean),
        outId -> DataNodeSpec("out", Map(branchId -> "out"), CType.CBoolean)
      ),
      inEdges = Set((cond0Id, branchId), (expr0Id, branchId), (otherwiseId, branchId)),
      outEdges = Set((branchId, outId))
    )

    val syntheticModules = SyntheticModuleFactory.fromDagSpec(dag)
    syntheticModules should have size 1

    val finalState = (for {
      runnable <- syntheticModules(branchId).init(branchId, dag)
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = runnable.data, state = stateRef)
      _     <- runtime.setTableData(cond0Id, true: Any)
      _     <- runtime.setTableData(expr0Id, true: Any)
      _     <- runtime.setTableData(otherwiseId, false: Any)
      _     <- runnable.run(runtime)
      res   <- runtime.getTableData(outId)
      state <- stateRef.get
    } yield (res, state)).unsafeRunSync()

    finalState._1 shouldBe true
    finalState._2.data should contain key outId
    finalState._2.data(outId).value shouldBe CValue.CBoolean(true)
  }

  it should "create a branch module with CFloat output type" in {
    val branchId    = UUID.randomUUID()
    val cond0Id     = UUID.randomUUID()
    val expr0Id     = UUID.randomUUID()
    val otherwiseId = UUID.randomUUID()
    val outId       = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("FloatBranchDag"),
      modules = Map(
        branchId -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-float", "Float branch", List.empty, 1, 0),
          consumes = Map("cond0" -> CType.CBoolean, "expr0" -> CType.CFloat, "otherwise" -> CType.CFloat),
          produces = Map("out" -> CType.CFloat)
        )
      ),
      data = Map(
        cond0Id -> DataNodeSpec("cond0", Map(branchId -> "cond0"), CType.CBoolean),
        expr0Id -> DataNodeSpec("expr0", Map(branchId -> "expr0"), CType.CFloat),
        otherwiseId -> DataNodeSpec("otherwise", Map(branchId -> "otherwise"), CType.CFloat),
        outId -> DataNodeSpec("out", Map(branchId -> "out"), CType.CFloat)
      ),
      inEdges = Set((cond0Id, branchId), (expr0Id, branchId), (otherwiseId, branchId)),
      outEdges = Set((branchId, outId))
    )

    val syntheticModules = SyntheticModuleFactory.fromDagSpec(dag)

    val finalState = (for {
      runnable <- syntheticModules(branchId).init(branchId, dag)
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = runnable.data, state = stateRef)
      _     <- runtime.setTableData(cond0Id, false: Any)
      _     <- runtime.setTableData(expr0Id, 1.5: Any)
      _     <- runtime.setTableData(otherwiseId, 3.14: Any)
      _     <- runnable.run(runtime)
      res   <- runtime.getTableData(outId)
      state <- stateRef.get
    } yield (res, state)).unsafeRunSync()

    finalState._1 shouldBe 3.14
    finalState._2.data(outId).value shouldBe CValue.CFloat(3.14)
  }

  // ===== Multi-case branch: first condition true =====

  "Multi-case branch execution" should "select first expression when first condition is true" in {
    val branchId    = UUID.randomUUID()
    val cond0Id     = UUID.randomUUID()
    val expr0Id     = UUID.randomUUID()
    val cond1Id     = UUID.randomUUID()
    val expr1Id     = UUID.randomUUID()
    val cond2Id     = UUID.randomUUID()
    val expr2Id     = UUID.randomUUID()
    val otherwiseId = UUID.randomUUID()
    val outId       = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("ThreeCaseBranch"),
      modules = Map(
        branchId -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-0", "Three cases", List.empty, 1, 0),
          consumes = Map(
            "cond0" -> CType.CBoolean, "expr0" -> CType.CString,
            "cond1" -> CType.CBoolean, "expr1" -> CType.CString,
            "cond2" -> CType.CBoolean, "expr2" -> CType.CString,
            "otherwise" -> CType.CString
          ),
          produces = Map("out" -> CType.CString)
        )
      ),
      data = Map(
        cond0Id -> DataNodeSpec("cond0", Map(branchId -> "cond0"), CType.CBoolean),
        expr0Id -> DataNodeSpec("expr0", Map(branchId -> "expr0"), CType.CString),
        cond1Id -> DataNodeSpec("cond1", Map(branchId -> "cond1"), CType.CBoolean),
        expr1Id -> DataNodeSpec("expr1", Map(branchId -> "expr1"), CType.CString),
        cond2Id -> DataNodeSpec("cond2", Map(branchId -> "cond2"), CType.CBoolean),
        expr2Id -> DataNodeSpec("expr2", Map(branchId -> "expr2"), CType.CString),
        otherwiseId -> DataNodeSpec("otherwise", Map(branchId -> "otherwise"), CType.CString),
        outId -> DataNodeSpec("out", Map(branchId -> "out"), CType.CString)
      ),
      inEdges = Set(
        (cond0Id, branchId), (expr0Id, branchId),
        (cond1Id, branchId), (expr1Id, branchId),
        (cond2Id, branchId), (expr2Id, branchId),
        (otherwiseId, branchId)
      ),
      outEdges = Set((branchId, outId))
    )

    val syntheticModules = SyntheticModuleFactory.fromDagSpec(dag)
    val branchModule = syntheticModules(branchId)

    val result = (for {
      runnable <- branchModule.init(branchId, dag)
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = runnable.data, state = stateRef)
      _ <- runtime.setTableData(cond0Id, true: Any)
      _ <- runtime.setTableData(expr0Id, "first": Any)
      _ <- runtime.setTableData(cond1Id, true: Any)
      _ <- runtime.setTableData(expr1Id, "second": Any)
      _ <- runtime.setTableData(cond2Id, true: Any)
      _ <- runtime.setTableData(expr2Id, "third": Any)
      _ <- runtime.setTableData(otherwiseId, "fallback": Any)
      _ <- runnable.run(runtime)
      res <- runtime.getTableData(outId)
    } yield res).unsafeRunSync()

    // First condition is true, so first expression is selected
    result shouldBe "first"
  }

  it should "select third expression when only third condition is true" in {
    val branchId    = UUID.randomUUID()
    val cond0Id     = UUID.randomUUID()
    val expr0Id     = UUID.randomUUID()
    val cond1Id     = UUID.randomUUID()
    val expr1Id     = UUID.randomUUID()
    val cond2Id     = UUID.randomUUID()
    val expr2Id     = UUID.randomUUID()
    val otherwiseId = UUID.randomUUID()
    val outId       = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("ThreeCaseBranch"),
      modules = Map(
        branchId -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-0", "Three cases", List.empty, 1, 0),
          consumes = Map(
            "cond0" -> CType.CBoolean, "expr0" -> CType.CString,
            "cond1" -> CType.CBoolean, "expr1" -> CType.CString,
            "cond2" -> CType.CBoolean, "expr2" -> CType.CString,
            "otherwise" -> CType.CString
          ),
          produces = Map("out" -> CType.CString)
        )
      ),
      data = Map(
        cond0Id -> DataNodeSpec("cond0", Map(branchId -> "cond0"), CType.CBoolean),
        expr0Id -> DataNodeSpec("expr0", Map(branchId -> "expr0"), CType.CString),
        cond1Id -> DataNodeSpec("cond1", Map(branchId -> "cond1"), CType.CBoolean),
        expr1Id -> DataNodeSpec("expr1", Map(branchId -> "expr1"), CType.CString),
        cond2Id -> DataNodeSpec("cond2", Map(branchId -> "cond2"), CType.CBoolean),
        expr2Id -> DataNodeSpec("expr2", Map(branchId -> "expr2"), CType.CString),
        otherwiseId -> DataNodeSpec("otherwise", Map(branchId -> "otherwise"), CType.CString),
        outId -> DataNodeSpec("out", Map(branchId -> "out"), CType.CString)
      ),
      inEdges = Set(
        (cond0Id, branchId), (expr0Id, branchId),
        (cond1Id, branchId), (expr1Id, branchId),
        (cond2Id, branchId), (expr2Id, branchId),
        (otherwiseId, branchId)
      ),
      outEdges = Set((branchId, outId))
    )

    val syntheticModules = SyntheticModuleFactory.fromDagSpec(dag)
    val branchModule = syntheticModules(branchId)

    val result = (for {
      runnable <- branchModule.init(branchId, dag)
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = runnable.data, state = stateRef)
      _ <- runtime.setTableData(cond0Id, false: Any)
      _ <- runtime.setTableData(expr0Id, "first": Any)
      _ <- runtime.setTableData(cond1Id, false: Any)
      _ <- runtime.setTableData(expr1Id, "second": Any)
      _ <- runtime.setTableData(cond2Id, true: Any)
      _ <- runtime.setTableData(expr2Id, "third": Any)
      _ <- runtime.setTableData(otherwiseId, "fallback": Any)
      _ <- runnable.run(runtime)
      res <- runtime.getTableData(outId)
    } yield res).unsafeRunSync()

    result shouldBe "third"
  }

  // ===== Spec preservation =====

  "Branch module spec" should "preserve the original ModuleNodeSpec" in {
    val branchId = UUID.randomUUID()
    val spec = ModuleNodeSpec(
      metadata = ComponentMetadata("branch-0", "My branch", List("conditional"), 2, 1),
      consumes = Map("cond0" -> CType.CBoolean, "expr0" -> CType.CString, "otherwise" -> CType.CString),
      produces = Map("out" -> CType.CString)
    )

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(branchId -> spec),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = SyntheticModuleFactory.fromDagSpec(dag)
    val branchModule = result(branchId)

    branchModule.spec shouldBe spec
    branchModule.spec.name shouldBe "branch-0"
    branchModule.spec.description shouldBe "My branch"
    branchModule.spec.tags shouldBe List("conditional")
    branchModule.spec.majorVersion shouldBe 2
    branchModule.spec.minorVersion shouldBe 1
  }

  // ===== Multiple branch modules with different output types =====

  "fromDagSpec with mixed branches" should "correctly reconstruct branches with different output types" in {
    val branchStr = UUID.randomUUID()
    val branchInt = UUID.randomUUID()
    val branchBool = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("MultiBranchDag"),
      modules = Map(
        branchStr -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-str", "String branch", List.empty, 1, 0),
          consumes = Map("cond0" -> CType.CBoolean, "expr0" -> CType.CString, "otherwise" -> CType.CString),
          produces = Map("out" -> CType.CString)
        ),
        branchInt -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-int", "Int branch", List.empty, 1, 0),
          consumes = Map("cond0" -> CType.CBoolean, "expr0" -> CType.CInt, "otherwise" -> CType.CInt),
          produces = Map("out" -> CType.CInt)
        ),
        branchBool -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-bool", "Bool branch", List.empty, 1, 0),
          consumes = Map("cond0" -> CType.CBoolean, "expr0" -> CType.CBoolean, "otherwise" -> CType.CBoolean),
          produces = Map("out" -> CType.CBoolean)
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result should have size 3
    result should contain key branchStr
    result should contain key branchInt
    result should contain key branchBool
  }
}
