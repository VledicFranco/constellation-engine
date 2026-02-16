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
}
