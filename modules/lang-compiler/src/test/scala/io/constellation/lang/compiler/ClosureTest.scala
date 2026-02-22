package io.constellation.lang.compiler

import io.constellation.*
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Integration tests for lambda closure support (RFC-030). Verifies that lambdas can capture
  * variables from their enclosing scope and that the compiled DAG correctly wires captured data
  * dependencies.
  */
class ClosureTest extends AnyFlatSpec with Matchers {

  // ===== Test Helper =====

  /** Creates a LangCompiler with HOF functions (filter, map, all, any), comparison (gt, lt), and
    * arithmetic (add, multiply) registered.
    */
  private def closureCompiler: LangCompiler = {
    val registry = FunctionRegistry.empty
    // filter: (List<Int>, (Int) => Boolean) => List<Int>
    registry.register(
      FunctionSignature(
        name = "filter",
        params = List(
          "items"     -> SemanticType.SList(SemanticType.SInt),
          "predicate" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
        ),
        returns = SemanticType.SList(SemanticType.SInt),
        moduleName = "stdlib.hof.filter-int",
        namespace = Some("stdlib.collection")
      )
    )
    // map: (List<Int>, (Int) => Int) => List<Int>
    registry.register(
      FunctionSignature(
        name = "map",
        params = List(
          "items"     -> SemanticType.SList(SemanticType.SInt),
          "transform" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SInt)
        ),
        returns = SemanticType.SList(SemanticType.SInt),
        moduleName = "stdlib.hof.map-int-int",
        namespace = Some("stdlib.collection")
      )
    )
    // all: (List<Int>, (Int) => Boolean) => Boolean
    registry.register(
      FunctionSignature(
        name = "all",
        params = List(
          "items"     -> SemanticType.SList(SemanticType.SInt),
          "predicate" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
        ),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.hof.all-int",
        namespace = Some("stdlib.collection")
      )
    )
    // any: (List<Int>, (Int) => Boolean) => Boolean
    registry.register(
      FunctionSignature(
        name = "any",
        params = List(
          "items"     -> SemanticType.SList(SemanticType.SInt),
          "predicate" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
        ),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.hof.any-int",
        namespace = Some("stdlib.collection")
      )
    )
    // Comparison functions
    registry.register(
      FunctionSignature(
        name = "gt",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.gt",
        namespace = Some("stdlib.compare")
      )
    )
    registry.register(
      FunctionSignature(
        name = "lt",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.lt",
        namespace = Some("stdlib.compare")
      )
    )
    // Arithmetic functions
    registry.register(
      FunctionSignature(
        name = "add",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "stdlib.add",
        namespace = Some("stdlib.math")
      )
    )
    registry.register(
      FunctionSignature(
        name = "multiply",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "stdlib.multiply",
        namespace = Some("stdlib.math")
      )
    )
    LangCompiler(registry, Map.empty)
  }

  /** Extended compiler with additional stdlib functions needed for infix operator desugaring. The
    * TypeChecker desugars infix operators to stdlib function calls (e.g., `x > 5` → `gt(x, 5)`), so
    * the function registry must include all target functions.
    */
  private def infixCompiler: LangCompiler = {
    val base     = closureCompiler
    val registry = base.functionRegistry
    // Additional comparison functions for infix desugaring
    registry.register(
      FunctionSignature(
        name = "gte",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.gte",
        namespace = Some("stdlib.compare")
      )
    )
    registry.register(
      FunctionSignature(
        name = "lte",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.lte",
        namespace = Some("stdlib.compare")
      )
    )
    registry.register(
      FunctionSignature(
        name = "eq-int",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.eq-int",
        namespace = Some("stdlib.compare")
      )
    )
    // not — needed for != desugaring (TypeChecker wraps eq in not)
    registry.register(
      FunctionSignature(
        name = "not",
        params = List("a" -> SemanticType.SBoolean),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.not",
        namespace = Some("stdlib.logic")
      )
    )
    // Additional arithmetic for infix desugaring
    registry.register(
      FunctionSignature(
        name = "subtract",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "stdlib.subtract",
        namespace = Some("stdlib.math")
      )
    )
    registry.register(
      FunctionSignature(
        name = "divide",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "stdlib.divide",
        namespace = Some("stdlib.math")
      )
    )
    LangCompiler(registry, Map.empty)
  }

  // ===== Basic Closure Tests =====

  "ClosureTest" should "compile filter with captured input variable" in {
    val compiler = closureCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |in threshold: Int
        |
        |above = filter(numbers, (x) => gt(x, threshold))
        |out above""".stripMargin

    val result = compiler.compile(source, "closure-filter-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have a closure filter transform (not a plain FilterTransform)
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.ClosureFilterTransform]
      )
    ) shouldBe true

    // The HOF data node should have "threshold" in its transformInputs (besides "source")
    val hofNode = compiled.pipeline.image.dagSpec.data.values
      .find(d => d.name.contains("hof"))
      .get
    hofNode.transformInputs.keys should contain("threshold")
    hofNode.transformInputs.keys should contain("source")
  }

  it should "compile filter with captured computed value" in {
    val compiler = closureCompiler
    // Register an Average module to simulate a computed value
    val registry = compiler.functionRegistry
    registry.register(
      FunctionSignature(
        name = "Average",
        params = List("items" -> SemanticType.SList(SemanticType.SInt)),
        returns = SemanticType.SInt,
        moduleName = "example.average"
      )
    )
    val compilerWithAvg = LangCompiler(registry, Map.empty)

    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |
        |avg = Average(numbers)
        |aboveAvg = filter(numbers, (x) => gt(x, avg))
        |out aboveAvg""".stripMargin

    val result = compilerWithAvg.compile(source, "closure-computed-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.ClosureFilterTransform]
      )
    ) shouldBe true
  }

  it should "compile filter with shadowed variable (lambda param wins)" in {
    val compiler = closureCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in x: Int
        |in numbers: List<Int>
        |
        |result = filter(numbers, (x) => gt(x, 0))
        |out result""".stripMargin

    val result = compiler.compile(source, "closure-shadow-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // With shadowing, lambda param `x` hides outer `x` — no closure capture needed
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.FilterTransform]
      )
    ) shouldBe true
  }

  it should "compile filter with multiple captured variables" in {
    val compiler = closureCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |in lower: Int
        |in upper: Int
        |
        |inRange = filter(numbers, (x) => gt(x, lower) and lt(x, upper))
        |out inRange""".stripMargin

    val result = compiler.compile(source, "closure-multi-capture-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.ClosureFilterTransform]
      )
    ) shouldBe true

    // Both captured variables should be in transformInputs
    val hofNode = compiled.pipeline.image.dagSpec.data.values
      .find(d => d.name.contains("hof"))
      .get
    hofNode.transformInputs.keys should contain("lower")
    hofNode.transformInputs.keys should contain("upper")
  }

  it should "compile map with closure (captured offset)" in {
    val compiler = closureCompiler
    val source =
      """use stdlib.collection
        |use stdlib.math
        |
        |in numbers: List<Int>
        |in offset: Int
        |
        |shifted = map(numbers, (x) => add(x, offset))
        |out shifted""".stripMargin

    val result = compiler.compile(source, "closure-map-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.ClosureMapTransform]
      )
    ) shouldBe true
  }

  it should "compile all with closure (captured threshold)" in {
    val compiler = closureCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |in threshold: Int
        |
        |allAbove = all(numbers, (x) => gt(x, threshold))
        |out allAbove""".stripMargin

    val result = compiler.compile(source, "closure-all-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.ClosureAllTransform]
      )
    ) shouldBe true
  }

  it should "compile any with closure (captured threshold)" in {
    val compiler = closureCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |in threshold: Int
        |
        |anyAbove = any(numbers, (x) => gt(x, threshold))
        |out anyAbove""".stripMargin

    val result = compiler.compile(source, "closure-any-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.ClosureAnyTransform]
      )
    ) shouldBe true
  }

  // ===== IR-Level Verification =====

  it should "generate TypedLambda with capturedBindings for closures" in {
    val compiler = closureCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |in threshold: Int
        |
        |above = filter(numbers, (x) => gt(x, threshold))
        |out above""".stripMargin

    val irResult = compiler.compileToIR(source, "closure-ir-test")
    irResult.isRight shouldBe true

    val ir = irResult.toOption.get
    // Find the HigherOrderNode
    val hofNode = ir.nodes.values.collectFirst { case h: IRNode.HigherOrderNode => h }
    hofNode shouldBe defined

    val hof = hofNode.get
    // Lambda should have capturedBindings for "threshold"
    hof.lambda.capturedBindings should contain key "threshold"

    // HigherOrderNode should have capturedInputs mapping to outer context
    hof.capturedInputs should contain key "threshold"
  }

  it should "not generate capturedBindings when lambda has no free variables" in {
    val compiler = closureCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |
        |positive = filter(numbers, (x) => gt(x, 0))
        |out positive""".stripMargin

    val irResult = compiler.compileToIR(source, "no-closure-ir-test")
    irResult.isRight shouldBe true

    val ir      = irResult.toOption.get
    val hofNode = ir.nodes.values.collectFirst { case h: IRNode.HigherOrderNode => h }
    hofNode shouldBe defined

    // No closure capture — lambda only uses its own param and a literal
    hofNode.get.lambda.capturedBindings shouldBe empty
    hofNode.get.capturedInputs shouldBe empty
  }

  // ===== Backwards Compatibility =====

  it should "still compile non-closure lambdas with FilterTransform" in {
    val compiler = closureCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in items: List<Int>
        |result = filter(items, (x) => gt(x, 0))
        |out result""".stripMargin

    val result = compiler.compile(source, "compat-filter-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // No captured variables -> plain FilterTransform (not ClosureFilterTransform)
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.FilterTransform]
      )
    ) shouldBe true
  }

  it should "still compile non-closure lambdas with MapTransform" in {
    val compiler = closureCompiler
    val source =
      """use stdlib.collection
        |use stdlib.math
        |
        |in items: List<Int>
        |result = map(items, (x) => multiply(x, 2))
        |out result""".stripMargin

    val result = compiler.compile(source, "compat-map-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // No captured variables -> plain MapTransform (not ClosureMapTransform)
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.MapTransform]
      )
    ) shouldBe true
  }

  // ===== Infix Operator Tests (RFC-032) =====
  //
  // The TypeChecker desugars infix operators to the same stdlib FunctionCall nodes
  // used by explicit function-call syntax. These tests verify that the full pipeline
  // (parser → TypeChecker → IRGenerator → DagCompiler) handles infix in lambda bodies.

  it should "compile filter with infix > operator" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |
        |positive = filter(numbers, (x) => x > 0)
        |out positive""".stripMargin

    val result = compiler.compile(source, "infix-gt-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.FilterTransform]
      )
    ) shouldBe true
  }

  it should "compile filter with infix < operator" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |
        |small = filter(numbers, (x) => x < 100)
        |out small""".stripMargin

    val result = compiler.compile(source, "infix-lt-dag")
    result.isRight shouldBe true
  }

  it should "compile filter with infix >= operator" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |
        |result = filter(numbers, (x) => x >= 0)
        |out result""".stripMargin

    val result = compiler.compile(source, "infix-gte-dag")
    result.isRight shouldBe true
  }

  it should "compile filter with infix <= operator" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |
        |result = filter(numbers, (x) => x <= 100)
        |out result""".stripMargin

    val result = compiler.compile(source, "infix-lte-dag")
    result.isRight shouldBe true
  }

  it should "compile filter with infix == operator" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |
        |zeros = filter(numbers, (x) => x == 0)
        |out zeros""".stripMargin

    val result = compiler.compile(source, "infix-eq-dag")
    result.isRight shouldBe true
  }

  it should "compile filter with infix != operator" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |use stdlib.logic
        |
        |in numbers: List<Int>
        |
        |nonzero = filter(numbers, (x) => x != 0)
        |out nonzero""".stripMargin

    val result = compiler.compile(source, "infix-neq-dag")
    result.isRight shouldBe true
  }

  it should "compile map with infix * operator" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.math
        |
        |in numbers: List<Int>
        |
        |doubled = map(numbers, (x) => x * 2)
        |out doubled""".stripMargin

    val result = compiler.compile(source, "infix-mul-dag")
    result.isRight shouldBe true
  }

  it should "compile map with infix + operator" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.math
        |
        |in numbers: List<Int>
        |
        |incremented = map(numbers, (x) => x + 1)
        |out incremented""".stripMargin

    val result = compiler.compile(source, "infix-add-dag")
    result.isRight shouldBe true
  }

  it should "compile map with infix - operator" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.math
        |
        |in numbers: List<Int>
        |
        |decremented = map(numbers, (x) => x - 1)
        |out decremented""".stripMargin

    val result = compiler.compile(source, "infix-sub-dag")
    result.isRight shouldBe true
  }

  it should "compile map with infix / operator" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.math
        |
        |in numbers: List<Int>
        |
        |halved = map(numbers, (x) => x / 2)
        |out halved""".stripMargin

    val result = compiler.compile(source, "infix-div-dag")
    result.isRight shouldBe true
  }

  it should "compile filter with combined infix and boolean operators" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |
        |inRange = filter(numbers, (x) => x > 0 and x < 100)
        |out inRange""".stripMargin

    val result = compiler.compile(source, "infix-combined-dag")
    result.isRight shouldBe true
  }

  it should "compile map with compound infix arithmetic" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.math
        |
        |in numbers: List<Int>
        |
        |transformed = map(numbers, (x) => x * 2 + 1)
        |out transformed""".stripMargin

    val result = compiler.compile(source, "infix-compound-dag")
    result.isRight shouldBe true
  }

  it should "compile filter with infix > and captured variable (closure)" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |in threshold: Int
        |
        |above = filter(numbers, (x) => x > threshold)
        |out above""".stripMargin

    val result = compiler.compile(source, "infix-closure-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Captured variable → ClosureFilterTransform
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.ClosureFilterTransform]
      )
    ) shouldBe true

    val hofNode = compiled.pipeline.image.dagSpec.data.values
      .find(d => d.name.contains("hof"))
      .get
    hofNode.transformInputs.keys should contain("threshold")
  }

  it should "compile map with infix + and captured variable (closure)" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.math
        |
        |in numbers: List<Int>
        |in offset: Int
        |
        |shifted = map(numbers, (x) => x + offset)
        |out shifted""".stripMargin

    val result = compiler.compile(source, "infix-closure-map-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.ClosureMapTransform]
      )
    ) shouldBe true
  }

  it should "compile filter with infix range and two captured variables (closure)" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |in lower: Int
        |in upper: Int
        |
        |inRange = filter(numbers, (x) => x > lower and x < upper)
        |out inRange""".stripMargin

    val result = compiler.compile(source, "infix-closure-range-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.ClosureFilterTransform]
      )
    ) shouldBe true

    val hofNode = compiled.pipeline.image.dagSpec.data.values
      .find(d => d.name.contains("hof"))
      .get
    hofNode.transformInputs.keys should contain("lower")
    hofNode.transformInputs.keys should contain("upper")
  }

  // ===== Implicit 'it' Parameter Tests (RFC-033) =====
  //
  // When a HOF expects a single-param lambda and the argument expression contains
  // a free VarRef("it"), the TypeChecker auto-wraps it in (it) => expr.
  // No new AST nodes — pure compile-time sugar.

  it should "compile filter with implicit it parameter" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |
        |positive = filter(numbers, it > 0)
        |out positive""".stripMargin

    val result = compiler.compile(source, "implicit-it-filter-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.FilterTransform]
      )
    ) shouldBe true
  }

  it should "compile filter with implicit it and closure capture" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |in threshold: Int
        |
        |above = filter(numbers, it > threshold)
        |out above""".stripMargin

    val result = compiler.compile(source, "implicit-it-closure-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.ClosureFilterTransform]
      )
    ) shouldBe true

    val hofNode = compiled.pipeline.image.dagSpec.data.values
      .find(d => d.name.contains("hof"))
      .get
    hofNode.transformInputs.keys should contain("threshold")
  }

  it should "compile map with implicit it parameter" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.math
        |
        |in numbers: List<Int>
        |
        |doubled = map(numbers, it * 2)
        |out doubled""".stripMargin

    val result = compiler.compile(source, "implicit-it-map-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.MapTransform]
      )
    ) shouldBe true
  }

  it should "compile map with implicit it and closure capture" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.math
        |
        |in numbers: List<Int>
        |in factor: Int
        |
        |scaled = map(numbers, it * factor)
        |out scaled""".stripMargin

    val result = compiler.compile(source, "implicit-it-map-closure-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.ClosureMapTransform]
      )
    ) shouldBe true

    val hofNode = compiled.pipeline.image.dagSpec.data.values
      .find(d => d.name.contains("hof"))
      .get
    hofNode.transformInputs.keys should contain("factor")
  }

  it should "compile all with implicit it parameter" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |
        |allPositive = all(numbers, it >= 0)
        |out allPositive""".stripMargin

    val result = compiler.compile(source, "implicit-it-all-dag")
    result.isRight shouldBe true
  }

  it should "compile any with implicit it parameter" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |
        |hasNegative = any(numbers, it < 0)
        |out hasNegative""".stripMargin

    val result = compiler.compile(source, "implicit-it-any-dag")
    result.isRight shouldBe true
  }

  it should "compile chained HOFs with implicit it" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |use stdlib.math
        |
        |in numbers: List<Int>
        |
        |result = map(filter(numbers, it > 0), it * 2)
        |out result""".stripMargin

    val result = compiler.compile(source, "implicit-it-chained-dag")
    result.isRight shouldBe true
  }

  it should "compile implicit it with compound boolean expression" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |in lower: Int
        |in upper: Int
        |
        |inRange = filter(numbers, it > lower and it < upper)
        |out inRange""".stripMargin

    val result = compiler.compile(source, "implicit-it-boolean-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.ClosureFilterTransform]
      )
    ) shouldBe true

    val hofNode = compiled.pipeline.image.dagSpec.data.values
      .find(d => d.name.contains("hof"))
      .get
    hofNode.transformInputs.keys should contain("lower")
    hofNode.transformInputs.keys should contain("upper")
  }

  // ===== Infix HOF Syntax Tests (RFC-033, Phase 2) =====
  //
  // Infix syntax: numbers filter it > 0 map it * 2
  // Parser produces FunctionCall("filter", [numbers, it > 0]), then the
  // TypeChecker lifts `it` into a lambda via the Phase 1 implicit-it case.

  it should "compile infix filter with implicit it" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |
        |positive = numbers filter it > 0
        |out positive""".stripMargin

    val result = compiler.compile(source, "infix-filter-it-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.FilterTransform]
      )
    ) shouldBe true
  }

  it should "compile infix map with implicit it" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.math
        |
        |in numbers: List<Int>
        |
        |doubled = numbers map it * 2
        |out doubled""".stripMargin

    val result = compiler.compile(source, "infix-map-it-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.MapTransform]
      )
    ) shouldBe true
  }

  it should "compile chained infix filter then map" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |use stdlib.math
        |
        |in numbers: List<Int>
        |
        |result = numbers filter it > 0 map it * 2
        |out result""".stripMargin

    val result = compiler.compile(source, "infix-chain-dag")
    result.isRight shouldBe true
  }

  it should "compile infix filter with closure capture" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |in threshold: Int
        |
        |above = numbers filter it > threshold
        |out above""".stripMargin

    val result = compiler.compile(source, "infix-closure-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.image.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.ClosureFilterTransform]
      )
    ) shouldBe true

    val hofNode = compiled.pipeline.image.dagSpec.data.values
      .find(d => d.name.contains("hof"))
      .get
    hofNode.transformInputs.keys should contain("threshold")
  }

  it should "compile infix all and any" in {
    val compiler = infixCompiler
    val source =
      """use stdlib.collection
        |use stdlib.compare
        |
        |in numbers: List<Int>
        |
        |allPos = numbers all it > 0
        |hasNeg = numbers any it < 0
        |out allPos
        |out hasNeg""".stripMargin

    val result = compiler.compile(source, "infix-all-any-dag")
    result.isRight shouldBe true
  }
}
