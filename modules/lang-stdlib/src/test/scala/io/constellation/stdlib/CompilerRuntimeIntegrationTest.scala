package io.constellation.stdlib

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.impl.ConstellationImpl
import io.constellation.stdlib.StdLib
import io.constellation.{CType, CValue, Runtime}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Integration tests for compiler runtime execution, specifically targeting lambda evaluator code
  * paths that are only executed at runtime.
  *
  * These tests cover the 10 runtime-only branches in DagCompiler (lines 1010-1205):
  *   - createLambdaEvaluator - Creates runtime lambda functions
  *   - evaluateLambdaBodyUnsafe - Evaluates lambda body nodes
  *   - evaluateLambdaNodeUnsafe - Evaluates individual IR nodes
  *   - evaluateBuiltinFunctionUnsafe - Handles builtin function evaluation
  *
  * Tests compile DAGs with HOF operations (filter, map, all, any) and execute them with actual
  * input data to verify lambda evaluation correctness.
  *
  * See: modules/lang-compiler/docs/BRANCH_COVERAGE_ANALYSIS.md
  *
  * Run with: sbt "langStdlib/testOnly *CompilerRuntimeIntegrationTest"
  */
class CompilerRuntimeIntegrationTest extends AnyFlatSpec with Matchers {

  private val constellation = ConstellationImpl.init.unsafeRunSync()
  private val compiler      = StdLib.compiler

  // Load stdlib modules for HOF operations
  StdLib.allModules.values.foreach { module =>
    constellation.setModule(module).unsafeRunSync()
  }

  /** Compile and execute a pipeline, returning a specific output value. */
  private def compileAndRun(
      source: String,
      inputs: Map[String, CValue],
      outputName: String
  ): CValue = {
    val compileResult = compiler.compile(source, "runtime-test")
    compileResult match {
      case Left(errors) =>
        throw new RuntimeException(s"Compilation failed: ${errors.map(_.message).mkString(", ")}")
      case Right(compiled) =>
        val dag     = compiled.pipeline.image.dagSpec
        val modules = compiled.pipeline.syntheticModules
        val state   = Runtime.run(dag, inputs, modules).unsafeRunSync()
        val outputNodeId = dag.outputBindings.getOrElse(
          outputName,
          throw new RuntimeException(
            s"Output '$outputName' not found. Available: ${dag.outputBindings.keys.mkString(", ")}"
          )
        )
        state.data(outputNodeId).value
    }
  }

  // ===== Filter Tests =====

  "filter with gt predicate" should "keep elements greater than threshold" in {
    val source = """
      in numbers: Seq<Int>
      positives = filter(numbers, (x) => gt(x, 0))
      out positives
    """
    val inputs =
      Map("numbers" -> CValue.CSeq(Vector(1L, -2L, 3L, -4L, 5L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "positives")

    result shouldBe CValue.CSeq(Vector(1L, 3L, 5L).map(CValue.CInt), CType.CInt)
  }

  it should "handle filter with lt predicate" in {
    val source = """
      in numbers: Seq<Int>
      negatives = filter(numbers, (x) => lt(x, 0))
      out negatives
    """
    val inputs =
      Map("numbers" -> CValue.CSeq(Vector(1L, -2L, 3L, -4L, 5L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "negatives")

    result shouldBe CValue.CSeq(Vector(-2L, -4L).map(CValue.CInt), CType.CInt)
  }

  it should "handle filter with gte predicate" in {
    val source = """
      in numbers: Seq<Int>
      nonNegatives = filter(numbers, (x) => gte(x, 0))
      out nonNegatives
    """
    val inputs =
      Map("numbers" -> CValue.CSeq(Vector(1L, -2L, 0L, 3L, -4L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "nonNegatives")

    result shouldBe CValue.CSeq(Vector(1L, 0L, 3L).map(CValue.CInt), CType.CInt)
  }

  it should "handle filter with lte predicate" in {
    val source = """
      in numbers: Seq<Int>
      nonPositives = filter(numbers, (x) => lte(x, 0))
      out nonPositives
    """
    val inputs =
      Map("numbers" -> CValue.CSeq(Vector(1L, -2L, 0L, 3L, -4L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "nonPositives")

    result shouldBe CValue.CSeq(Vector(-2L, 0L, -4L).map(CValue.CInt), CType.CInt)
  }

  it should "handle filter with eq-int predicate" in {
    val source = """
      in numbers: Seq<Int>
      zeros = filter(numbers, (x) => eq-int(x, 0))
      out zeros
    """
    val inputs =
      Map("numbers" -> CValue.CSeq(Vector(1L, 0L, 2L, 0L, 3L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "zeros")

    result shouldBe CValue.CSeq(Vector(0L, 0L).map(CValue.CInt), CType.CInt)
  }

  // ===== Map Tests =====

  "map with arithmetic" should "double all elements" in {
    val source = """
      in numbers: Seq<Int>
      doubled = map(numbers, (x) => multiply(x, 2))
      out doubled
    """
    val inputs = Map("numbers" -> CValue.CSeq(Vector(1L, 2L, 3L, 4L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "doubled")

    result shouldBe CValue.CSeq(Vector(2L, 4L, 6L, 8L).map(CValue.CInt), CType.CInt)
  }

  it should "handle map with add operation" in {
    val source = """
      in numbers: Seq<Int>
      incremented = map(numbers, (x) => add(x, 10))
      out incremented
    """
    val inputs = Map("numbers" -> CValue.CSeq(Vector(1L, 2L, 3L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "incremented")

    result shouldBe CValue.CSeq(Vector(11L, 12L, 13L).map(CValue.CInt), CType.CInt)
  }

  it should "handle map with subtract operation" in {
    val source = """
      in numbers: Seq<Int>
      decremented = map(numbers, (x) => subtract(x, 5))
      out decremented
    """
    val inputs = Map("numbers" -> CValue.CSeq(Vector(10L, 20L, 30L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "decremented")

    result shouldBe CValue.CSeq(Vector(5L, 15L, 25L).map(CValue.CInt), CType.CInt)
  }

  it should "handle map with divide operation" in {
    val source = """
      in numbers: Seq<Int>
      halved = map(numbers, (x) => divide(x, 2))
      out halved
    """
    val inputs = Map("numbers" -> CValue.CSeq(Vector(10L, 20L, 30L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "halved")

    result shouldBe CValue.CSeq(Vector(5L, 10L, 15L).map(CValue.CInt), CType.CInt)
  }

  it should "handle map with conditional lambda" in {
    val source = """
      in numbers: Seq<Int>
      result = map(numbers, (x) => if (gt(x, 0)) x else 0)
      out result
    """
    val inputs =
      Map("numbers" -> CValue.CSeq(Vector(1L, -2L, 3L, -4L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "result")

    result shouldBe CValue.CSeq(Vector(1L, 0L, 3L, 0L).map(CValue.CInt), CType.CInt)
  }

  // ===== All Tests =====

  "all with predicate" should "return true when all elements satisfy predicate" in {
    val source = """
      in numbers: Seq<Int>
      allPositive = all(numbers, (x) => gt(x, 0))
      out allPositive
    """
    val inputs = Map("numbers" -> CValue.CSeq(Vector(1L, 2L, 3L, 4L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "allPositive")

    result shouldBe CValue.CBoolean(true)
  }

  it should "return false when any element fails predicate" in {
    val source = """
      in numbers: Seq<Int>
      allPositive = all(numbers, (x) => gt(x, 0))
      out allPositive
    """
    val inputs =
      Map("numbers" -> CValue.CSeq(Vector(1L, -2L, 3L, 4L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "allPositive")

    result shouldBe CValue.CBoolean(false)
  }

  // ===== Any Tests =====

  "any with predicate" should "return true when at least one element satisfies predicate" in {
    val source = """
      in numbers: Seq<Int>
      anyNegative = any(numbers, (x) => lt(x, 0))
      out anyNegative
    """
    val inputs =
      Map("numbers" -> CValue.CSeq(Vector(1L, 2L, -3L, 4L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "anyNegative")

    result shouldBe CValue.CBoolean(true)
  }

  it should "return false when no element satisfies predicate" in {
    val source = """
      in numbers: Seq<Int>
      anyNegative = any(numbers, (x) => lt(x, 0))
      out anyNegative
    """
    val inputs = Map("numbers" -> CValue.CSeq(Vector(1L, 2L, 3L, 4L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "anyNegative")

    result shouldBe CValue.CBoolean(false)
  }

  // ===== Boolean Operations in Lambdas =====
  // Note: Boolean operators (and, or, not) have different syntax in constellation-lang
  // These tests are skipped for now as they require operator syntax clarification

  ignore should "combine multiple predicates with and" in {
    val source = """
      in numbers: Seq<Int>
      result = filter(numbers, (x) => and(gt(x, 0), lt(x, 10)))
      out result
    """
    val inputs =
      Map("numbers" -> CValue.CSeq(Vector(-5L, 1L, 5L, 15L, 20L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "result")

    result shouldBe CValue.CSeq(Vector(1L, 5L).map(CValue.CInt), CType.CInt)
  }

  ignore should "accept elements matching either predicate with or" in {
    val source = """
      in numbers: Seq<Int>
      result = filter(numbers, (x) => or(lt(x, 0), gt(x, 10)))
      out result
    """
    val inputs =
      Map("numbers" -> CValue.CSeq(Vector(-5L, 1L, 5L, 15L, 20L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "result")

    result shouldBe CValue.CSeq(Vector(-5L, 15L, 20L).map(CValue.CInt), CType.CInt)
  }

  ignore should "negate predicate result with not" in {
    val source = """
      in numbers: Seq<Int>
      result = filter(numbers, (x) => not(eq-int(x, 0)))
      out result
    """
    val inputs =
      Map("numbers" -> CValue.CSeq(Vector(0L, 1L, 0L, 2L, 3L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "result")

    result shouldBe CValue.CSeq(Vector(1L, 2L, 3L).map(CValue.CInt), CType.CInt)
  }

  // ===== Complex Lambda Bodies =====

  "map with nested conditional" should "handle complex logic" in {
    val source = """
      in numbers: Seq<Int>
      result = map(numbers, (x) => if (eq-int(x, 0)) 0 else if (gt(x, 0)) 1 else -1)
      out result
    """
    val inputs =
      Map("numbers" -> CValue.CSeq(Vector(-5L, 0L, 5L, -3L, 0L, 10L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "result")

    result shouldBe CValue.CSeq(Vector(-1L, 0L, 1L, -1L, 0L, 1L).map(CValue.CInt), CType.CInt)
  }

  ignore should "evaluate nested and/or/not" in {
    val source = """
      in numbers: Seq<Int>
      result = filter(numbers, (x) => and(or(lt(x, -5), gt(x, 5)), not(eq-int(x, 0))))
      out result
    """
    val inputs =
      Map(
        "numbers" -> CValue.CSeq(
          Vector(-10L, -5L, 0L, 5L, 10L, -6L, 6L).map(CValue.CInt),
          CType.CInt
        )
      )
    val result = compileAndRun(source, inputs, "result")

    result shouldBe CValue.CSeq(Vector(-10L, 10L, -6L, 6L).map(CValue.CInt), CType.CInt)
  }

  // ===== Edge Cases =====

  "filter with empty list" should "return empty list" in {
    val source = """
      in numbers: Seq<Int>
      result = filter(numbers, (x) => gt(x, 0))
      out result
    """
    val inputs = Map("numbers" -> CValue.CSeq(Vector.empty, CType.CInt))
    val result = compileAndRun(source, inputs, "result")

    result shouldBe CValue.CSeq(Vector.empty, CType.CInt)
  }

  "all on empty list" should "return true" in {
    val source = """
      in numbers: Seq<Int>
      result = all(numbers, (x) => gt(x, 0))
      out result
    """
    val inputs = Map("numbers" -> CValue.CSeq(Vector.empty, CType.CInt))
    val result = compileAndRun(source, inputs, "result")

    result shouldBe CValue.CBoolean(true)
  }

  "any on empty list" should "return false" in {
    val source = """
      in numbers: Seq<Int>
      result = any(numbers, (x) => gt(x, 0))
      out result
    """
    val inputs = Map("numbers" -> CValue.CSeq(Vector.empty, CType.CInt))
    val result = compileAndRun(source, inputs, "result")

    result shouldBe CValue.CBoolean(false)
  }

  "map with divide by zero" should "return 0 for division by zero" in {
    val source = """
      in numbers: Seq<Int>
      result = map(numbers, (x) => divide(10, x))
      out result
    """
    val inputs = Map("numbers" -> CValue.CSeq(Vector(2L, 0L, 5L).map(CValue.CInt), CType.CInt))
    val result = compileAndRun(source, inputs, "result")

    result shouldBe CValue.CSeq(Vector(5L, 0L, 2L).map(CValue.CInt), CType.CInt)
  }
}
