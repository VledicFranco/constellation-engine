package io.constellation.examples.app

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import io.constellation.*
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.compiler.CompileResult
import io.constellation.stdlib.StdLib
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.nio.file.{Files, Paths}
import scala.io.Source

/** Tests that verify all .cst example programs in the examples directory compile and execute
  * successfully.
  *
  * This test suite automatically discovers all .cst files in the examples directory and verifies:
  *   1. Each file compiles without errors 2. The compiled DAG has the expected structure
  */
class ExamplesTest extends AnyFlatSpec with Matchers {

  private val compiler = ExampleLib.compiler

  /** Get all .cst files from the examples directory */
  private def getExampleFiles: List[File] = {
    val examplesDir = new File("modules/example-app/examples")
    if examplesDir.exists() && examplesDir.isDirectory then {
      examplesDir.listFiles().filter(_.getName.endsWith(".cst")).toList
    } else {
      // Try alternate path (when running from different working directory)
      val altDir = new File("examples")
      if altDir.exists() && altDir.isDirectory then {
        altDir.listFiles().filter(_.getName.endsWith(".cst")).toList
      } else {
        List.empty
      }
    }
  }

  /** Read file contents */
  private def readFile(file: File): String = {
    val source = Source.fromFile(file)
    try source.mkString
    finally source.close()
  }

  "Example programs" should "have .cst files in the examples directory" in {
    val files = getExampleFiles
    files should not be empty
    info(s"Found ${files.length} example files: ${files.map(_.getName).mkString(", ")}")
  }

  // ========== simple-test.cst ==========

  "simple-test.cst" should "compile successfully" in {
    val files      = getExampleFiles
    val simpleTest = files.find(_.getName == "simple-test.cst")
    simpleTest shouldBe defined

    val source = readFile(simpleTest.get)
    val result = compiler.compile(source, "simple-test")

    result.isRight shouldBe true
    val compiled = result.toOption.get
    compiled.dagSpec.name shouldBe "simple-test"
  }

  it should "have correct input and output structure" in {
    val files      = getExampleFiles
    val simpleTest = files.find(_.getName == "simple-test.cst").get
    val source     = readFile(simpleTest)
    val result     = compiler.compile(source, "simple-test")

    val compiled = result.toOption.get

    // Should have 'message' as input
    val inputNames = compiled.dagSpec.data.values.map(_.name).toSet
    inputNames should contain("message")

    // Should have 'result' as declared output
    compiled.dagSpec.declaredOutputs should contain("result")

    // Should have the Uppercase module
    compiled.dagSpec.modules.values.map(_.name).exists(_.contains("Uppercase")) shouldBe true
  }

  // ========== text-analysis.cst ==========

  "text-analysis.cst" should "compile successfully" in {
    val files        = getExampleFiles
    val textAnalysis = files.find(_.getName == "text-analysis.cst")
    textAnalysis shouldBe defined

    val source = readFile(textAnalysis.get)
    val result = compiler.compile(source, "text-analysis")

    result.isRight shouldBe true
    val compiled = result.toOption.get
    compiled.dagSpec.name shouldBe "text-analysis"
  }

  it should "have correct input and output structure" in {
    val files        = getExampleFiles
    val textAnalysis = files.find(_.getName == "text-analysis.cst").get
    val source       = readFile(textAnalysis)
    val result       = compiler.compile(source, "text-analysis")

    val compiled = result.toOption.get

    // Should have 'document' as input
    val inputNames = compiled.dagSpec.data.values.map(_.name).toSet
    inputNames should contain("document")

    // Should have multiple outputs
    compiled.dagSpec.declaredOutputs should contain allOf ("cleaned", "normalized", "words", "chars", "lines")

    // Should have the text processing modules
    val moduleNames = compiled.dagSpec.modules.values.map(_.name).toList
    moduleNames.exists(_.contains("Trim")) shouldBe true
    moduleNames.exists(_.contains("Lowercase")) shouldBe true
    moduleNames.exists(_.contains("WordCount")) shouldBe true
    moduleNames.exists(_.contains("TextLength")) shouldBe true
    moduleNames.exists(_.contains("SplitLines")) shouldBe true
  }

  // ========== data-pipeline.cst ==========

  "data-pipeline.cst" should "compile successfully" in {
    val files        = getExampleFiles
    val dataPipeline = files.find(_.getName == "data-pipeline.cst")
    dataPipeline shouldBe defined

    val source = readFile(dataPipeline.get)
    val result = compiler.compile(source, "data-pipeline")

    result.isRight shouldBe true
    val compiled = result.toOption.get
    compiled.dagSpec.name shouldBe "data-pipeline"
  }

  it should "have correct input structure" in {
    val files        = getExampleFiles
    val dataPipeline = files.find(_.getName == "data-pipeline.cst").get
    val source       = readFile(dataPipeline)
    val result       = compiler.compile(source, "data-pipeline")

    val compiled = result.toOption.get

    // Should have 'numbers', 'threshold', and 'multiplier' as inputs
    val inputNames = compiled.dagSpec.data.values.map(_.name).toSet
    inputNames should contain allOf ("numbers", "threshold", "multiplier")
  }

  it should "have correct output structure" in {
    val files        = getExampleFiles
    val dataPipeline = files.find(_.getName == "data-pipeline.cst").get
    val source       = readFile(dataPipeline)
    val result       = compiler.compile(source, "data-pipeline")

    val compiled = result.toOption.get

    // Should have all declared outputs
    compiled.dagSpec.declaredOutputs should contain allOf (
      "filtered", "scaled", "total", "avg", "highest", "lowest", "formattedTotal"
    )
  }

  it should "have all required data processing modules" in {
    val files        = getExampleFiles
    val dataPipeline = files.find(_.getName == "data-pipeline.cst").get
    val source       = readFile(dataPipeline)
    val result       = compiler.compile(source, "data-pipeline")

    val compiled = result.toOption.get

    // Should have the data processing modules
    val moduleNames = compiled.dagSpec.modules.values.map(_.name).toList
    moduleNames.exists(_.contains("FilterGreaterThan")) shouldBe true
    moduleNames.exists(_.contains("MultiplyEach")) shouldBe true
    moduleNames.exists(_.contains("SumList")) shouldBe true
    moduleNames.exists(_.contains("Average")) shouldBe true
    moduleNames.exists(_.contains("Max")) shouldBe true
    moduleNames.exists(_.contains("Min")) shouldBe true
    moduleNames.exists(_.contains("FormatNumber")) shouldBe true
  }

  // ========== Dynamic test for all examples ==========

  "All example programs" should "compile without errors" in {
    val files = getExampleFiles
    files should not be empty

    val results = files.map { file =>
      val source  = readFile(file)
      val dagName = file.getName.stripSuffix(".cst")
      val result  = compiler.compile(source, dagName)
      (file.getName, result)
    }

    val failures = results.filter(_._2.isLeft)
    if failures.nonEmpty then {
      val errorMessages = failures.map { case (name, result) =>
        s"$name: ${result.left.toOption.get.map(_.message).mkString("; ")}"
      }
      fail(s"The following examples failed to compile:\n${errorMessages.mkString("\n")}")
    }

    info(s"All ${results.length} example programs compiled successfully")
  }

  it should "produce valid DAG specifications" in {
    val files = getExampleFiles

    files.foreach { file =>
      val source  = readFile(file)
      val dagName = file.getName.stripSuffix(".cst")
      val result  = compiler.compile(source, dagName)

      withClue(s"${file.getName}: ") {
        result.isRight shouldBe true
        val compiled = result.toOption.get

        // Every compiled program should have a valid DAG spec
        compiled.dagSpec.name shouldBe dagName
        compiled.dagSpec.data should not be empty
        compiled.dagSpec.declaredOutputs should not be empty
      }
    }
  }

  // ========== Execution Tests ==========

  /** Create a Constellation instance with all modules registered */
  private def createConstellation: IO[Constellation] =
    for {
      constellation <- ConstellationImpl.init
      allModules = (StdLib.allModules ++ ExampleLib.allModules).values.toList
      _ <- allModules.traverse(constellation.setModule)
    } yield constellation

  "simple-test.cst" should "execute with correct output" in {
    val test = for {
      constellation <- createConstellation
      files      = getExampleFiles
      simpleTest = files.find(_.getName == "simple-test.cst").get
      source     = readFile(simpleTest)

      // Compile
      compiled = compiler.compile(source, "simple-test").toOption.get

      // Set up the DAG
      _ <- constellation.setDag("simple-test", compiled.dagSpec)

      // Execute with input
      inputs = Map("message" -> CValue.CString("hello world"))
      state <- constellation.runDag("simple-test", inputs)

      // Get the output
      resultBinding = compiled.dagSpec.outputBindings("result")
      resultValue   = state.data.get(resultBinding).map(_.value)
    } yield resultValue

    val result = test.unsafeRunSync()
    result shouldBe defined
    result.get shouldBe CValue.CString("HELLO WORLD")
  }

  "text-analysis.cst" should "execute with correct outputs" in {
    val test = for {
      constellation <- createConstellation
      files        = getExampleFiles
      textAnalysis = files.find(_.getName == "text-analysis.cst").get
      source       = readFile(textAnalysis)

      // Compile
      compiled = compiler.compile(source, "text-analysis").toOption.get

      // Set up the DAG
      _ <- constellation.setDag("text-analysis", compiled.dagSpec)

      // Execute with input
      inputs = Map("document" -> CValue.CString("  Hello World\nLine 2  "))
      state <- constellation.runDag("text-analysis", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()

    // Verify outputs exist
    val outputBindings = compiled.dagSpec.outputBindings
    outputBindings.keys should contain allOf ("cleaned", "normalized", "words", "chars", "lines")

    // Check cleaned output
    val cleanedValue = state.data.get(outputBindings("cleaned")).map(_.value)
    cleanedValue shouldBe defined
    cleanedValue.get shouldBe CValue.CString("Hello World\nLine 2")

    // Check normalized output
    val normalizedValue = state.data.get(outputBindings("normalized")).map(_.value)
    normalizedValue shouldBe defined
    normalizedValue.get shouldBe CValue.CString("hello world\nline 2")

    // Check word count
    val wordsValue = state.data.get(outputBindings("words")).map(_.value)
    wordsValue shouldBe defined
    wordsValue.get shouldBe CValue.CInt(4)
  }

  "data-pipeline.cst" should "execute with correct outputs" in {
    val test = for {
      constellation <- createConstellation
      files        = getExampleFiles
      dataPipeline = files.find(_.getName == "data-pipeline.cst").get
      source       = readFile(dataPipeline)

      // Compile
      compiled = compiler.compile(source, "data-pipeline").toOption.get

      // Set up the DAG
      _ <- constellation.setDag("data-pipeline", compiled.dagSpec)

      // Execute with inputs: numbers=[1,2,3,4,5], threshold=2, multiplier=3
      // Expected: filtered=[3,4,5], scaled=[9,12,15], total=36, avg=12.0, highest=15, lowest=9
      inputs = Map(
        "numbers"    -> CValue.CList(Vector(1L, 2L, 3L, 4L, 5L).map(CValue.CInt.apply), CType.CInt),
        "threshold"  -> CValue.CInt(2),
        "multiplier" -> CValue.CInt(3)
      )
      state <- constellation.runDag("data-pipeline", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()

    val outputBindings = compiled.dagSpec.outputBindings
    outputBindings.keys should contain allOf ("filtered", "scaled", "total", "avg", "highest", "lowest", "formattedTotal")

    // Verify filtered output: [3, 4, 5]
    val filteredValue = state.data.get(outputBindings("filtered")).map(_.value)
    filteredValue shouldBe defined
    val filteredList = filteredValue.get.asInstanceOf[CValue.CList]
    filteredList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(3L, 4L, 5L)

    // Verify scaled output: [9, 12, 15]
    val scaledValue = state.data.get(outputBindings("scaled")).map(_.value)
    scaledValue shouldBe defined
    val scaledList = scaledValue.get.asInstanceOf[CValue.CList]
    scaledList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(9L, 12L, 15L)

    // Verify total: 36
    val totalValue = state.data.get(outputBindings("total")).map(_.value)
    totalValue shouldBe defined
    totalValue.get shouldBe CValue.CInt(36)

    // Verify avg: 12.0
    val avgValue = state.data.get(outputBindings("avg")).map(_.value)
    avgValue shouldBe defined
    avgValue.get shouldBe CValue.CFloat(12.0)

    // Verify highest: 15
    val highestValue = state.data.get(outputBindings("highest")).map(_.value)
    highestValue shouldBe defined
    highestValue.get shouldBe CValue.CInt(15)

    // Verify lowest: 9
    val lowestValue = state.data.get(outputBindings("lowest")).map(_.value)
    lowestValue shouldBe defined
    lowestValue.get shouldBe CValue.CInt(9)
  }
}
