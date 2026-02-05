package io.constellation.lang.parser

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalacheck.Gen

/** Property-based tests for parser determinism and round-trip consistency (RFC-017 Phase 3).
  *
  * Tests that:
  *   - Generated valid programs always parse successfully
  *   - Parsing the same source always produces structurally identical ASTs
  *   - Boolean programs parse deterministically
  *
  * Run with: sbt "langParser/testOnly *ParserRoundTripPropertyTest"
  */
class ParserRoundTripPropertyTest
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckPropertyChecks {

  private val parser = ConstellationParser

  // -------------------------------------------------------------------------
  // Inline generators (parser test scope doesn't have access to core test sources)
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

  private val genValidProgram: Gen[String] = for {
    numInputs      <- Gen.choose(1, 3)
    numAssignments <- Gen.choose(2, 5)
    inputNames     <- Gen.listOfN(numInputs, genVarName).map(_.distinct)
    if inputNames.nonEmpty
    assignNames <- Gen.listOfN(numAssignments, genVarName)
      .map(_.distinct.filterNot(inputNames.contains))
    if assignNames.nonEmpty
  } yield {
    val sb = new StringBuilder

    sb.append(s"in ${inputNames.head}: Boolean\n")
    if inputNames.size > 1 then sb.append(s"in ${inputNames(1)}: Int\n")
    if inputNames.size > 2 then sb.append(s"in ${inputNames(2)}: String\n")
    sb.append("\n")

    val boolInput  = inputNames.head
    val allDefined = scala.collection.mutable.ListBuffer[String](inputNames*)

    assignNames.zipWithIndex.foreach { case (name, idx) =>
      val expr = idx % 5 match {
        case 0 => s"$boolInput and $boolInput"
        case 1 => s"$boolInput or not $boolInput"
        case 2 => s"not $boolInput"
        case 3 =>
          val prev = allDefined.filter(_ != boolInput).headOption.getOrElse(boolInput)
          s"$prev"
        case 4 => s"($boolInput and $boolInput) or $boolInput"
      }
      sb.append(s"$name = $expr\n")
      allDefined += name
    }

    sb.append("\n")
    sb.append(s"out ${assignNames.last}\n")
    sb.append(s"out $boolInput\n")

    sb.toString
  }

  // -------------------------------------------------------------------------
  // Parse determinism: parse(source) always produces same AST
  // -------------------------------------------------------------------------

  "Parser" should "deterministically parse generated valid programs" in {
    forAll(genValidProgram) { source =>
      whenever(source.trim.nonEmpty) {
        val result1 = parser.parse(source)
        val result2 = parser.parse(source)

        result1.isRight shouldBe true
        result2.isRight shouldBe true

        // ASTs should be structurally equal (spans are deterministic for same input)
        result1 shouldBe result2
      }
    }
  }

  it should "deterministically parse generated boolean programs" in {
    forAll(genSimpleBooleanProgram) { source =>
      val result1 = parser.parse(source)
      val result2 = parser.parse(source)

      result1.isRight shouldBe true
      result2.isRight shouldBe true

      result1 shouldBe result2
    }
  }

  // -------------------------------------------------------------------------
  // Generated programs always parse successfully
  // -------------------------------------------------------------------------

  it should "successfully parse all generated valid programs" in {
    forAll(genValidProgram) { source =>
      whenever(source.trim.nonEmpty) {
        val result = parser.parse(source)
        withClue(s"Failed to parse generated program:\n$source\nError: ${result.left.toOption}") {
          result.isRight shouldBe true
        }
      }
    }
  }

  it should "successfully parse all generated boolean programs" in {
    forAll(genSimpleBooleanProgram) { source =>
      val result = parser.parse(source)
      withClue(s"Failed to parse generated boolean program:\n$source\nError: ${result.left.toOption}") {
        result.isRight shouldBe true
      }
    }
  }

  // -------------------------------------------------------------------------
  // Parse output structure consistency
  // -------------------------------------------------------------------------

  it should "produce consistent declaration counts across repeated parses" in {
    forAll(genValidProgram) { source =>
      whenever(source.trim.nonEmpty) {
        val results = (1 to 5).map(_ => parser.parse(source))

        results.foreach(_.isRight shouldBe true)

        val declCounts = results.map(_.toOption.get.declarations.size)
        declCounts.distinct.size shouldBe 1

        val outputCounts = results.map(_.toOption.get.outputs.size)
        outputCounts.distinct.size shouldBe 1
      }
    }
  }

  // -------------------------------------------------------------------------
  // Fixed programs parse determinism
  // -------------------------------------------------------------------------

  it should "deterministically parse a passthrough program" in {
    val source = "in x: String\nout x"
    val results = (1 to 10).map(_ => parser.parse(source))
    results.foreach(_.isRight shouldBe true)
    results.sliding(2).foreach { pair =>
      pair.head shouldBe pair.last
    }
  }

  it should "deterministically parse a conditional program" in {
    val source =
      """in flag: Boolean
        |in a: Int
        |in b: Int
        |result = if (flag) a else b
        |out result
        |""".stripMargin
    val results = (1 to 10).map(_ => parser.parse(source))
    results.foreach(_.isRight shouldBe true)
    results.sliding(2).foreach { pair =>
      pair.head shouldBe pair.last
    }
  }

  it should "deterministically parse a guard and coalesce program" in {
    val source =
      """in flag: Boolean
        |in value: Int
        |in fallback: Int
        |guarded = value when flag
        |result = guarded ?? fallback
        |out result
        |""".stripMargin
    val results = (1 to 10).map(_ => parser.parse(source))
    results.foreach(_.isRight shouldBe true)
    results.sliding(2).foreach { pair =>
      pair.head shouldBe pair.last
    }
  }
}
