package io.constellation.lang.property

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.{CType, CValue}

import org.scalacheck.Gen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

/** Property-based tests for runtime execution determinism (RFC-017 Phase 3).
  *
  * Verifies that compiling once and executing multiple times with the same inputs always produces
  * identical outputs. Tests cover passthrough, boolean logic, conditionals, and guards.
  *
  * Run with: sbt "langCompiler/testOnly *RuntimeDeterminismPropertyTest"
  */
class RuntimeDeterminismPropertyTest
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckPropertyChecks {

  private val constellation = ConstellationImpl.init.unsafeRunSync()
  private val compiler      = LangCompiler.empty

  private val executionCount = 10

  // -------------------------------------------------------------------------
  // Inline generator (lang-compiler test scope doesn't have access to core test sources)
  // -------------------------------------------------------------------------

  private val genVarName: Gen[String] = for {
    head <- Gen.alphaLowerChar
    tail <- Gen.listOfN(Gen.choose(2, 8).sample.getOrElse(4), Gen.alphaLowerChar).map(_.mkString)
  } yield s"$head$tail"

  private val genSimpleBooleanProgram: Gen[String] = for {
    numVars  <- Gen.choose(2, 10)
    varNames <- Gen.listOfN(numVars, genVarName).map(_.distinct.take(numVars))
    if varNames.size >= 2
  } yield {
    val sb = new StringBuilder
    sb.append("in flag: Boolean\n")
    sb.append("in fallback: Int\n")
    sb.append("in value: Int\n\n")

    sb.append(s"${varNames.head} = flag\n")
    varNames.tail.zipWithIndex.foreach { case (name, idx) =>
      val prev = varNames(idx)
      val op = idx % 4 match {
        case 0 => s"$prev and flag"
        case 1 => s"$prev or flag"
        case 2 => s"not $prev"
        case 3 => s"($prev and flag) or not flag"
      }
      sb.append(s"$name = $op\n")
    }

    val last = varNames.last
    sb.append(s"\nguarded = value when $last\n")
    sb.append(s"result = guarded ?? fallback\n")
    sb.append(s"\nout result\n")
    sb.append(s"out $last\n")
    sb.toString
  }

  /** Compile once, execute N times, return all output maps. */
  private def compileOnceRunMany(
      source: String,
      inputs: Map[String, CValue],
      n: Int = executionCount
  ): List[Map[String, CValue]] = {
    val compiled = compiler.compile(source, "determinism-test")
    compiled.isRight shouldBe true
    val loaded = compiled.toOption.get.pipeline

    (1 to n).map { _ =>
      constellation.run(loaded, inputs).unsafeRunSync().outputs
    }.toList
  }

  // =========================================================================
  // Passthrough determinism
  // =========================================================================

  "Runtime execution" should "be deterministic for String passthrough" in {
    val source  = "in x: String\nout x"
    val results = compileOnceRunMany(source, Map("x" -> CValue.CString("hello")))

    results.sliding(2).foreach { pair =>
      pair.head shouldBe pair.last
    }
  }

  it should "be deterministic for Int passthrough" in {
    val source  = "in x: Int\nout x"
    val results = compileOnceRunMany(source, Map("x" -> CValue.CInt(42)))

    results.sliding(2).foreach { pair =>
      pair.head shouldBe pair.last
    }
  }

  it should "be deterministic for Float passthrough" in {
    val source  = "in x: Float\nout x"
    val results = compileOnceRunMany(source, Map("x" -> CValue.CFloat(3.14)))

    results.sliding(2).foreach { pair =>
      pair.head shouldBe pair.last
    }
  }

  it should "be deterministic for Boolean passthrough" in {
    val source  = "in x: Boolean\nout x"
    val results = compileOnceRunMany(source, Map("x" -> CValue.CBoolean(true)))

    results.sliding(2).foreach { pair =>
      pair.head shouldBe pair.last
    }
  }

  // =========================================================================
  // Boolean logic determinism
  // =========================================================================

  it should "be deterministic for boolean logic chains" in {
    val source =
      """in a: Boolean
        |in b: Boolean
        |c = a and b
        |d = a or b
        |e = not a
        |f = (a and b) or not a
        |out c
        |out d
        |out e
        |out f
        |""".stripMargin

    val inputs  = Map("a" -> CValue.CBoolean(true), "b" -> CValue.CBoolean(false))
    val results = compileOnceRunMany(source, inputs)

    results.sliding(2).foreach { pair =>
      pair.head shouldBe pair.last
    }
  }

  // =========================================================================
  // Conditional determinism
  // =========================================================================

  it should "be deterministic for conditional expressions" in {
    val source =
      """in flag: Boolean
        |in a: Int
        |in b: Int
        |result = if (flag) a else b
        |out result
        |""".stripMargin

    val inputs = Map(
      "flag" -> CValue.CBoolean(true),
      "a"    -> CValue.CInt(10),
      "b"    -> CValue.CInt(20)
    )
    val results = compileOnceRunMany(source, inputs)

    results.sliding(2).foreach { pair =>
      pair.head shouldBe pair.last
    }
    // Also verify the actual value
    results.foreach { outputs =>
      outputs("result") shouldBe CValue.CInt(10)
    }
  }

  // =========================================================================
  // Guard + coalesce determinism
  // =========================================================================

  it should "be deterministic for guard and coalesce expressions" in {
    val source =
      """in flag: Boolean
        |in value: Int
        |in fallback: Int
        |guarded = value when flag
        |result = guarded ?? fallback
        |out result
        |""".stripMargin

    val inputs = Map(
      "flag"     -> CValue.CBoolean(false),
      "value"    -> CValue.CInt(42),
      "fallback" -> CValue.CInt(0)
    )
    val results = compileOnceRunMany(source, inputs)

    results.sliding(2).foreach { pair =>
      pair.head shouldBe pair.last
    }
    results.foreach { outputs =>
      outputs("result") shouldBe CValue.CInt(0) // flag is false → fallback
    }
  }

  // =========================================================================
  // Multiple outputs determinism
  // =========================================================================

  it should "be deterministic with multiple outputs" in {
    val source =
      """in x: Int
        |in y: String
        |in flag: Boolean
        |out x
        |out y
        |out flag
        |""".stripMargin

    val inputs = Map(
      "x"    -> CValue.CInt(1),
      "y"    -> CValue.CString("hi"),
      "flag" -> CValue.CBoolean(true)
    )
    val results = compileOnceRunMany(source, inputs)

    results.sliding(2).foreach { pair =>
      pair.head shouldBe pair.last
    }
  }

  // =========================================================================
  // Generated boolean program determinism
  // =========================================================================

  it should "be deterministic for generated boolean programs" in {
    forAll(genSimpleBooleanProgram) { source =>
      val compiled = compiler.compile(source, "gen-determinism")
      whenever(compiled.isRight) {
        val loaded = compiled.toOption.get.pipeline
        val inputs = Map(
          "flag"     -> CValue.CBoolean(true),
          "fallback" -> CValue.CInt(99),
          "value"    -> CValue.CInt(42)
        )

        val results = (1 to executionCount).map { _ =>
          constellation.run(loaded, inputs).unsafeRunSync().outputs
        }.toList

        results.sliding(2).foreach { pair =>
          pair.head shouldBe pair.last
        }
      }
    }
  }

  // =========================================================================
  // Record determinism
  // =========================================================================

  it should "be deterministic for record passthrough" in {
    val source = "in user: { name: String, age: Int }\nout user"
    val record = CValue.CProduct(
      Map("name" -> CValue.CString("Alice"), "age" -> CValue.CInt(30)),
      Map("name" -> CType.CString, "age"           -> CType.CInt)
    )
    val results = compileOnceRunMany(source, Map("user" -> record))

    results.sliding(2).foreach { pair =>
      pair.head shouldBe pair.last
    }
  }
}
