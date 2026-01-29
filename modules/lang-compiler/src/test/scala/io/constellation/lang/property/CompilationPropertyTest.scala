package io.constellation.lang.property

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import io.constellation.lang.LangCompiler
import io.constellation.lang.parser.ConstellationParser
import io.constellation.lang.ast.CompileError
import io.constellation.lang.benchmark.TestFixtures

/** Property-based tests for compilation determinism (RFC-013 Phase 5.2)
  *
  * Verifies that parsing and compilation are deterministic and produce
  * consistent results across repeated invocations.
  *
  * Run with: sbt "langCompiler/testOnly *CompilationPropertyTest"
  */
class CompilationPropertyTest extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  private val parser = ConstellationParser
  private val compiler = LangCompiler.empty

  // -------------------------------------------------------------------------
  // Parsing determinism
  // -------------------------------------------------------------------------

  "Parser" should "be deterministic: same source always produces same AST" in {
    val sources = List(
      TestFixtures.smallProgram,
      TestFixtures.mediumProgram,
      TestFixtures.largeProgram,
      TestFixtures.stressProgram100,
      TestFixtures.stressProgram200
    )

    sources.foreach { source =>
      val results = (1 to 10).map(_ => parser.parse(source))

      // All should succeed
      results.foreach(_.isRight shouldBe true)

      // All ASTs should be equal
      val asts = results.map(_.toOption.get)
      asts.sliding(2).foreach { pair =>
        pair.head shouldBe pair.last
      }
    }
  }

  it should "be deterministic for generated stress programs" in {
    // Test with various chain lengths
    val chainLengths = List(10, 50, 100, 200)

    chainLengths.foreach { length =>
      val source = TestFixtures.generateStressProgram(length)
      val result1 = parser.parse(source)
      val result2 = parser.parse(source)

      result1.isRight shouldBe true
      result2.isRight shouldBe true
      result1 shouldBe result2
    }
  }

  // -------------------------------------------------------------------------
  // Compilation determinism
  // -------------------------------------------------------------------------

  "Compilation" should "be deterministic: same source always produces same DagSpec" in {
    val sources = List(
      TestFixtures.smallProgram,
      TestFixtures.mediumProgram,
      TestFixtures.largeProgram
    )

    sources.foreach { source =>
      val results = (1 to 5).map(_ => compiler.compile(source, "test"))

      // All should produce the same success/failure
      val firstIsRight = results.head.isRight
      results.foreach(_.isRight shouldBe firstIsRight)

      if (firstIsRight) {
        val dagSpecs = results.map(_.toOption.get.dagSpec)
        // Module counts, data counts, edge counts should match
        dagSpecs.sliding(2).foreach { pair =>
          pair.head.modules.size shouldBe pair.last.modules.size
          pair.head.data.size shouldBe pair.last.data.size
          pair.head.inEdges.size shouldBe pair.last.inEdges.size
          pair.head.outEdges.size shouldBe pair.last.outEdges.size
          pair.head.declaredOutputs shouldBe pair.last.declaredOutputs
        }
      }
    }
  }

  it should "produce consistent module and data counts for stress programs" in {
    val lengths = List(10, 50, 100)

    lengths.foreach { length =>
      val source = TestFixtures.generateStressProgram(length)
      val r1 = compiler.compile(source, "stress")
      val r2 = compiler.compile(source, "stress")

      r1.isRight shouldBe r2.isRight

      if (r1.isRight) {
        val d1 = r1.toOption.get.dagSpec
        val d2 = r2.toOption.get.dagSpec
        d1.modules.size shouldBe d2.modules.size
        d1.data.size shouldBe d2.data.size
        d1.declaredOutputs shouldBe d2.declaredOutputs
      }
    }
  }

  // -------------------------------------------------------------------------
  // Parse error consistency
  // -------------------------------------------------------------------------

  "Parser errors" should "be consistent for the same invalid input" in {
    val invalidSources = List(
      "this is not valid code !!!",
      "in x: String\n\n",  // No outputs
      "out missing",        // Undeclared variable reference (parser may accept, compiler rejects)
      "in x: String\n = broken syntax\nout x",
      "in x: \nout x"
    )

    invalidSources.foreach { source =>
      val results = (1 to 5).map(_ => parser.parse(source))

      // All should produce the same success/failure status
      val firstIsRight = results.head.isRight
      results.foreach(_.isRight shouldBe firstIsRight)

      if (!firstIsRight) {
        // Error messages should be consistent
        val errors = results.map(_.left.toOption.get.message)
        errors.distinct.size shouldBe 1
      }
    }
  }

  // -------------------------------------------------------------------------
  // Compilation error consistency
  // -------------------------------------------------------------------------

  "Compilation errors" should "be consistent for the same invalid source" in {
    val invalidSources = List(
      // Type mismatch: using Int where String expected
      "in x: Int\nresult = if (x) x else x\nout result",
      // Undefined variable
      "in x: String\nresult = undefined_var\nout result"
    )

    invalidSources.foreach { source =>
      val results = (1 to 5).map(_ => compiler.compile(source, "test"))

      val firstIsRight = results.head.isRight
      results.foreach(_.isRight shouldBe firstIsRight)

      if (!firstIsRight) {
        // Error counts should be consistent
        val errorCounts = results.map(_.left.toOption.get.size)
        errorCounts.distinct.size shouldBe 1
      }
    }
  }

  // -------------------------------------------------------------------------
  // Output binding consistency
  // -------------------------------------------------------------------------

  "Compilation" should "produce consistent output bindings" in {
    val source =
      """in x: Boolean
        |in y: Int
        |in z: Int
        |result = if (x) y else z
        |flag = x and x
        |out result
        |out flag
        |""".stripMargin

    val results = (1 to 10).map(_ => compiler.compile(source, "binding-test"))

    results.foreach(_.isRight shouldBe true)

    val bindings = results.map(_.toOption.get.dagSpec.outputBindings.keySet)
    bindings.sliding(2).foreach { pair =>
      pair.head shouldBe pair.last
    }
  }
}
