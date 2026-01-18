package io.constellation.examples.app.modules

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._
import io.constellation._
import io.constellation.impl.ConstellationImpl
import io.constellation.examples.app.ExampleLib
import io.constellation.stdlib.StdLib
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Unit tests for DataModules.
  *
  * Tests all data processing modules with various inputs including edge cases.
  */
class DataModulesTest extends AnyFlatSpec with Matchers {

  private val compiler = ExampleLib.compiler

  /** Create a Constellation instance with all modules registered */
  private def createConstellation: IO[Constellation] = {
    for {
      constellation <- ConstellationImpl.init
      allModules = (StdLib.allModules ++ ExampleLib.allModules).values.toList
      _ <- allModules.traverse(constellation.setModule)
    } yield constellation
  }

  /** Helper to compile, run, and extract output value */
  private def runModule[T](
      source: String,
      dagName: String,
      inputs: Map[String, CValue],
      outputName: String
  )(extract: CValue => T): T = {
    val test = for {
      constellation <- createConstellation
      compiled = compiler.compile(source, dagName).toOption.get
      _ <- constellation.setDag(dagName, compiled.dagSpec)
      state <- constellation.runDag(dagName, inputs)
      resultBinding = compiled.dagSpec.outputBindings(outputName)
      resultValue = state.data.get(resultBinding).map(_.value)
    } yield resultValue

    val result = test.unsafeRunSync()
    result shouldBe defined
    extract(result.get)
  }

  // ========== SumList Tests ==========

  "SumList" should "sum a list of positive integers" in {
    val source = """
      in numbers: List<Int>
      result = SumList(numbers)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector(1L, 2L, 3L, 4L, 5L).map(CValue.CInt.apply), CType.CInt)
    )

    val result = runModule[Long](source, "sumlist-test", inputs, "result") {
      case CValue.CInt(v) => v
    }
    result shouldBe 15L
  }

  it should "return 0 for an empty list" in {
    val source = """
      in numbers: List<Int>
      result = SumList(numbers)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector.empty, CType.CInt)
    )

    val result = runModule[Long](source, "sumlist-empty", inputs, "result") {
      case CValue.CInt(v) => v
    }
    result shouldBe 0L
  }

  it should "handle a single element list" in {
    val source = """
      in numbers: List<Int>
      result = SumList(numbers)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector(42L).map(CValue.CInt.apply), CType.CInt)
    )

    val result = runModule[Long](source, "sumlist-single", inputs, "result") {
      case CValue.CInt(v) => v
    }
    result shouldBe 42L
  }

  it should "handle negative numbers" in {
    val source = """
      in numbers: List<Int>
      result = SumList(numbers)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector(-5L, 10L, -3L).map(CValue.CInt.apply), CType.CInt)
    )

    val result = runModule[Long](source, "sumlist-negative", inputs, "result") {
      case CValue.CInt(v) => v
    }
    result shouldBe 2L
  }

  // ========== Average Tests ==========

  "Average" should "calculate average of integers" in {
    val source = """
      in numbers: List<Int>
      result = Average(numbers)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector(2L, 4L, 6L).map(CValue.CInt.apply), CType.CInt)
    )

    val result = runModule[Double](source, "avg-test", inputs, "result") {
      case CValue.CFloat(v) => v
    }
    result shouldBe 4.0
  }

  it should "return 0.0 for an empty list" in {
    val source = """
      in numbers: List<Int>
      result = Average(numbers)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector.empty, CType.CInt)
    )

    val result = runModule[Double](source, "avg-empty", inputs, "result") {
      case CValue.CFloat(v) => v
    }
    result shouldBe 0.0
  }

  it should "handle single element" in {
    val source = """
      in numbers: List<Int>
      result = Average(numbers)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector(100L).map(CValue.CInt.apply), CType.CInt)
    )

    val result = runModule[Double](source, "avg-single", inputs, "result") {
      case CValue.CFloat(v) => v
    }
    result shouldBe 100.0
  }

  it should "handle non-integer averages" in {
    val source = """
      in numbers: List<Int>
      result = Average(numbers)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector(1L, 2L).map(CValue.CInt.apply), CType.CInt)
    )

    val result = runModule[Double](source, "avg-decimal", inputs, "result") {
      case CValue.CFloat(v) => v
    }
    result shouldBe 1.5
  }

  // ========== Max Tests ==========

  "Max" should "find maximum in a list" in {
    val source = """
      in numbers: List<Int>
      result = Max(numbers)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector(3L, 7L, 2L, 9L, 1L).map(CValue.CInt.apply), CType.CInt)
    )

    val result = runModule[Long](source, "max-test", inputs, "result") {
      case CValue.CInt(v) => v
    }
    result shouldBe 9L
  }

  it should "return 0 for empty list" in {
    val source = """
      in numbers: List<Int>
      result = Max(numbers)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector.empty, CType.CInt)
    )

    val result = runModule[Long](source, "max-empty", inputs, "result") {
      case CValue.CInt(v) => v
    }
    result shouldBe 0L
  }

  it should "handle negative numbers" in {
    val source = """
      in numbers: List<Int>
      result = Max(numbers)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector(-5L, -2L, -8L).map(CValue.CInt.apply), CType.CInt)
    )

    val result = runModule[Long](source, "max-negative", inputs, "result") {
      case CValue.CInt(v) => v
    }
    result shouldBe -2L
  }

  // ========== Min Tests ==========

  "Min" should "find minimum in a list" in {
    val source = """
      in numbers: List<Int>
      result = Min(numbers)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector(3L, 7L, 2L, 9L, 1L).map(CValue.CInt.apply), CType.CInt)
    )

    val result = runModule[Long](source, "min-test", inputs, "result") {
      case CValue.CInt(v) => v
    }
    result shouldBe 1L
  }

  it should "return 0 for empty list" in {
    val source = """
      in numbers: List<Int>
      result = Min(numbers)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector.empty, CType.CInt)
    )

    val result = runModule[Long](source, "min-empty", inputs, "result") {
      case CValue.CInt(v) => v
    }
    result shouldBe 0L
  }

  it should "handle negative numbers" in {
    val source = """
      in numbers: List<Int>
      result = Min(numbers)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector(-5L, -2L, -8L).map(CValue.CInt.apply), CType.CInt)
    )

    val result = runModule[Long](source, "min-negative", inputs, "result") {
      case CValue.CInt(v) => v
    }
    result shouldBe -8L
  }

  // ========== FilterGreaterThan Tests ==========

  "FilterGreaterThan" should "filter values above threshold" in {
    val source = """
      in numbers: List<Int>
      in threshold: Int
      result = FilterGreaterThan(numbers, threshold)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector(1L, 5L, 3L, 8L, 2L).map(CValue.CInt.apply), CType.CInt),
      "threshold" -> CValue.CInt(3)
    )

    val result = runModule[Vector[Long]](source, "filter-test", inputs, "result") {
      case CValue.CList(values, _) => values.map { case CValue.CInt(v) => v }
    }
    result shouldBe Vector(5L, 8L)
  }

  it should "return empty list when no values exceed threshold" in {
    val source = """
      in numbers: List<Int>
      in threshold: Int
      result = FilterGreaterThan(numbers, threshold)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector(1L, 2L, 3L).map(CValue.CInt.apply), CType.CInt),
      "threshold" -> CValue.CInt(10)
    )

    val result = runModule[Vector[Long]](source, "filter-none", inputs, "result") {
      case CValue.CList(values, _) => values.map { case CValue.CInt(v) => v }
    }
    result shouldBe Vector.empty
  }

  it should "handle empty input list" in {
    val source = """
      in numbers: List<Int>
      in threshold: Int
      result = FilterGreaterThan(numbers, threshold)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector.empty, CType.CInt),
      "threshold" -> CValue.CInt(5)
    )

    val result = runModule[Vector[Long]](source, "filter-empty", inputs, "result") {
      case CValue.CList(values, _) => values.map { case CValue.CInt(v) => v }
    }
    result shouldBe Vector.empty
  }

  // ========== MultiplyEach Tests ==========

  "MultiplyEach" should "multiply each element by multiplier" in {
    val source = """
      in numbers: List<Int>
      in multiplier: Int
      result = MultiplyEach(numbers, multiplier)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector(1L, 2L, 3L).map(CValue.CInt.apply), CType.CInt),
      "multiplier" -> CValue.CInt(3)
    )

    val result = runModule[Vector[Long]](source, "multiply-test", inputs, "result") {
      case CValue.CList(values, _) => values.map { case CValue.CInt(v) => v }
    }
    result shouldBe Vector(3L, 6L, 9L)
  }

  it should "handle zero multiplier" in {
    val source = """
      in numbers: List<Int>
      in multiplier: Int
      result = MultiplyEach(numbers, multiplier)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector(5L, 10L).map(CValue.CInt.apply), CType.CInt),
      "multiplier" -> CValue.CInt(0)
    )

    val result = runModule[Vector[Long]](source, "multiply-zero", inputs, "result") {
      case CValue.CList(values, _) => values.map { case CValue.CInt(v) => v }
    }
    result shouldBe Vector(0L, 0L)
  }

  it should "handle negative multiplier" in {
    val source = """
      in numbers: List<Int>
      in multiplier: Int
      result = MultiplyEach(numbers, multiplier)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector(2L, 4L).map(CValue.CInt.apply), CType.CInt),
      "multiplier" -> CValue.CInt(-2)
    )

    val result = runModule[Vector[Long]](source, "multiply-neg", inputs, "result") {
      case CValue.CList(values, _) => values.map { case CValue.CInt(v) => v }
    }
    result shouldBe Vector(-4L, -8L)
  }

  it should "handle empty list" in {
    val source = """
      in numbers: List<Int>
      in multiplier: Int
      result = MultiplyEach(numbers, multiplier)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CList(Vector.empty, CType.CInt),
      "multiplier" -> CValue.CInt(5)
    )

    val result = runModule[Vector[Long]](source, "multiply-empty", inputs, "result") {
      case CValue.CList(values, _) => values.map { case CValue.CInt(v) => v }
    }
    result shouldBe Vector.empty
  }

  // ========== Range Tests ==========

  "Range" should "generate a sequence of numbers" in {
    val source = """
      in start: Int
      in end: Int
      result = Range(start, end)
      out result
    """
    val inputs = Map(
      "start" -> CValue.CInt(1),
      "end" -> CValue.CInt(5)
    )

    val result = runModule[Vector[Long]](source, "range-test", inputs, "result") {
      case CValue.CList(values, _) => values.map { case CValue.CInt(v) => v }
    }
    result shouldBe Vector(1L, 2L, 3L, 4L, 5L)
  }

  it should "handle single element range" in {
    val source = """
      in start: Int
      in end: Int
      result = Range(start, end)
      out result
    """
    val inputs = Map(
      "start" -> CValue.CInt(5),
      "end" -> CValue.CInt(5)
    )

    val result = runModule[Vector[Long]](source, "range-single", inputs, "result") {
      case CValue.CList(values, _) => values.map { case CValue.CInt(v) => v }
    }
    result shouldBe Vector(5L)
  }

  it should "handle negative ranges" in {
    val source = """
      in start: Int
      in end: Int
      result = Range(start, end)
      out result
    """
    val inputs = Map(
      "start" -> CValue.CInt(-2),
      "end" -> CValue.CInt(2)
    )

    val result = runModule[Vector[Long]](source, "range-neg", inputs, "result") {
      case CValue.CList(values, _) => values.map { case CValue.CInt(v) => v }
    }
    result shouldBe Vector(-2L, -1L, 0L, 1L, 2L)
  }

  // ========== FormatNumber Tests ==========

  "FormatNumber" should "format number with thousand separators" in {
    val source = """
      in number: Int
      result = FormatNumber(number)
      out result
    """
    val inputs = Map(
      "number" -> CValue.CInt(1000000)
    )

    val result = runModule[String](source, "format-test", inputs, "result") {
      case CValue.CString(v) => v
    }
    // Formatted with locale-specific separator (could be comma or period)
    result should (include("1") and include("000") and include("000"))
  }

  it should "handle small numbers" in {
    val source = """
      in number: Int
      result = FormatNumber(number)
      out result
    """
    val inputs = Map(
      "number" -> CValue.CInt(42)
    )

    val result = runModule[String](source, "format-small", inputs, "result") {
      case CValue.CString(v) => v
    }
    result shouldBe "42"
  }

  it should "handle zero" in {
    val source = """
      in number: Int
      result = FormatNumber(number)
      out result
    """
    val inputs = Map(
      "number" -> CValue.CInt(0)
    )

    val result = runModule[String](source, "format-zero", inputs, "result") {
      case CValue.CString(v) => v
    }
    result shouldBe "0"
  }

  it should "handle negative numbers" in {
    val source = """
      in number: Int
      result = FormatNumber(number)
      out result
    """
    val inputs = Map(
      "number" -> CValue.CInt(-1234567)
    )

    val result = runModule[String](source, "format-neg", inputs, "result") {
      case CValue.CString(v) => v
    }
    result should startWith("-")
  }

  // ========== Module Metadata Tests ==========

  "DataModules.all" should "contain all data modules" in {
    val moduleNames = DataModules.all.map(_.spec.name)

    moduleNames should contain("SumList")
    moduleNames should contain("Average")
    moduleNames should contain("Max")
    moduleNames should contain("Min")
    moduleNames should contain("FilterGreaterThan")
    moduleNames should contain("MultiplyEach")
    moduleNames should contain("Range")
    moduleNames should contain("FormatNumber")
  }

  it should "have correct module count" in {
    DataModules.all should have size 8
  }

  "Each data module" should "have valid metadata" in {
    DataModules.all.foreach { module =>
      module.spec.name should not be empty
      module.spec.metadata.description should not be empty
      module.spec.metadata.majorVersion should be >= 0
      module.spec.metadata.minorVersion should be >= 0
    }
  }

  it should "have data-related tags" in {
    DataModules.all.foreach { module =>
      module.spec.metadata.tags should not be empty
      module.spec.metadata.tags.exists(t =>
        t == "data" || t == "aggregation" || t == "statistics" ||
        t == "filter" || t == "transform" || t == "generator" || t == "format"
      ) shouldBe true
    }
  }
}
