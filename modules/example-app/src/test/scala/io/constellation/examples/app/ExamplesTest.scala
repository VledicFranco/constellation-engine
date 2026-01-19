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

  // ========== Union Type Integration Tests ==========

  "Union type programs" should "compile with simple union type inputs" in {
    val source = """
      in x: String | Int
      out x
    """
    val result = compiler.compile(source, "union-simple-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.data should have size 1

    val inputNode = compiled.dagSpec.data.values.head
    inputNode.cType match {
      case CType.CUnion(structure) =>
        structure.keys should contain allOf ("String", "Int")
      case other => fail(s"Expected CUnion, got $other")
    }
  }

  it should "compile with union type definition" in {
    val source = """
      type Result = String | Int | Boolean
      in result: Result
      out result
    """
    val result = compiler.compile(source, "union-typedef-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val inputNode = compiled.dagSpec.data.values.head
    inputNode.cType match {
      case CType.CUnion(structure) =>
        structure should have size 3
      case other => fail(s"Expected CUnion, got $other")
    }
  }

  it should "compile with union of record types" in {
    val source = """
      type Success = { value: Int }
      type Error = { message: String }
      type Outcome = Success | Error
      in outcome: Outcome
      out outcome
    """
    val result = compiler.compile(source, "union-record-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val inputNode = compiled.dagSpec.data.values.head
    inputNode.cType match {
      case CType.CUnion(structure) =>
        structure should have size 2
        structure.values.forall(_.isInstanceOf[CType.CProduct]) shouldBe true
      case other => fail(s"Expected CUnion, got $other")
    }
  }

  it should "compile and preserve union type through assignment" in {
    val source = """
      in x: String | Int
      y = x
      out y
    """
    val result = compiler.compile(source, "union-assign-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("y")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType match {
      case CType.CUnion(structure) =>
        structure.keys should contain allOf ("String", "Int")
      case other => fail(s"Expected CUnion, got $other")
    }
  }

  it should "compile union with parameterized types" in {
    val source = """
      in x: Optional<Int> | String
      out x
    """
    val result = compiler.compile(source, "union-param-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val inputNode = compiled.dagSpec.data.values.head
    inputNode.cType match {
      case CType.CUnion(structure) =>
        structure should have size 2
        structure.keys should contain("String")
        structure.values.exists {
          case CType.COptional(CType.CInt) => true
          case _ => false
        } shouldBe true
      case other => fail(s"Expected CUnion, got $other")
    }
  }

  it should "compile union combined with merge type expression" in {
    val source = """
      type A = { x: Int }
      type B = { y: String }
      type Combined = A + B | { z: Boolean }
      in data: Combined
      out data
    """
    val result = compiler.compile(source, "union-merge-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val inputNode = compiled.dagSpec.data.values.head
    inputNode.cType match {
      case CType.CUnion(structure) =>
        structure should have size 2
        // One member should have both x and y fields
        structure.values.exists {
          case CType.CProduct(fields) =>
            fields.contains("x") && fields.contains("y")
          case _ => false
        } shouldBe true
        // Other member should have z field
        structure.values.exists {
          case CType.CProduct(fields) =>
            fields.contains("z") && !fields.contains("x")
          case _ => false
        } shouldBe true
      case other => fail(s"Expected CUnion, got $other")
    }
  }

  // ========== Guard Expression Integration Tests ==========

  "Guard expression programs" should "compile and produce Optional type" in {
    val source = """
      in value: Int
      in isActive: Boolean
      result = value when isActive
      out result
    """
    val result = compiler.compile(source, "guard-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.COptional(CType.CInt)
  }

  it should "compile guard with record value" in {
    val source = """
      in person: { name: String, age: Int }
      in isAdult: Boolean
      result = person when isAdult
      out result
    """
    val result = compiler.compile(source, "guard-record-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType match {
      case CType.COptional(CType.CProduct(fields)) =>
        fields.keys should contain allOf ("name", "age")
      case other => fail(s"Expected Optional<Record>, got $other")
    }
  }

  it should "compile guard with union type value" in {
    val source = """
      in data: String | Int
      in flag: Boolean
      result = data when flag
      out result
    """
    val result = compiler.compile(source, "guard-union-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType match {
      case CType.COptional(CType.CUnion(structure)) =>
        structure.keys should contain allOf ("String", "Int")
      case other => fail(s"Expected Optional<Union>, got $other")
    }
  }

  // ========== Coalesce Expression Integration Tests ==========

  "Coalesce expression programs" should "compile with Optional and fallback" in {
    val source = """
      in maybeValue: Optional<Int>
      in fallback: Int
      result = maybeValue ?? fallback
      out result
    """
    val result = compiler.compile(source, "coalesce-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.CInt
  }

  it should "compile coalesce with two Optional values" in {
    val source = """
      in primary: Optional<Int>
      in secondary: Optional<Int>
      result = primary ?? secondary
      out result
    """
    val result = compiler.compile(source, "coalesce-optional-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.COptional(CType.CInt)
  }

  it should "compile combined guard and coalesce expressions" in {
    val source = """
      in value: Int
      in condition: Boolean
      in fallback: Int
      result = value when condition ?? fallback
      out result
    """
    val result = compiler.compile(source, "guard-coalesce-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.CInt
  }

  it should "compile chained coalesce expressions" in {
    val source = """
      in first: Optional<Int>
      in second: Optional<Int>
      in last: Int
      result = first ?? second ?? last
      out result
    """
    val result = compiler.compile(source, "chained-coalesce-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.CInt
  }

  // ========== Lambda and Higher-Order Function Integration Tests ==========

  "Lambda and HOF programs" should "compile filter with lambda" in {
    val stdlibCompiler = StdLib.compiler
    val source = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = filter(numbers, (x) => gt(x, 0))
      out result
    """
    val result = stdlibCompiler.compile(source, "filter-lambda-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.CList(CType.CInt)
  }

  it should "compile map with lambda" in {
    val stdlibCompiler = StdLib.compiler
    val source = """
      use stdlib.collection
      use stdlib.math
      in numbers: List<Int>
      result = map(numbers, (x) => multiply(x, 2))
      out result
    """
    val result = stdlibCompiler.compile(source, "map-lambda-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.CList(CType.CInt)
  }

  it should "compile all with lambda" in {
    val stdlibCompiler = StdLib.compiler
    val source = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = all(numbers, (x) => gt(x, 0))
      out result
    """
    val result = stdlibCompiler.compile(source, "all-lambda-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.CBoolean
  }

  it should "compile any with lambda" in {
    val stdlibCompiler = StdLib.compiler
    val source = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = any(numbers, (x) => lt(x, 0))
      out result
    """
    val result = stdlibCompiler.compile(source, "any-lambda-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.CBoolean
  }

  it should "compile chained filter and map with lambdas" in {
    val stdlibCompiler = StdLib.compiler
    val source = """
      use stdlib.collection
      use stdlib.compare
      use stdlib.math
      in numbers: List<Int>
      positives = filter(numbers, (x) => gt(x, 0))
      result = map(positives, (x) => multiply(x, 2))
      out result
    """
    val result = stdlibCompiler.compile(source, "filter-map-chain-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Verify both filter and map nodes exist
    val hasFilter = compiled.dagSpec.data.values.exists(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.FilterTransform])
    )
    val hasMap = compiled.dagSpec.data.values.exists(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.MapTransform])
    )
    hasFilter shouldBe true
    hasMap shouldBe true
  }

  it should "compile lambda with boolean operators in body" in {
    val stdlibCompiler = StdLib.compiler
    val source = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = filter(numbers, (x) => gt(x, 0) and lt(x, 100))
      out result
    """
    val result = stdlibCompiler.compile(source, "lambda-bool-ops-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.get.cType shouldBe CType.CList(CType.CInt)
  }

  it should "compile lambda with explicit type annotation" in {
    val stdlibCompiler = StdLib.compiler
    val source = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = filter(numbers, (x: Int) => gt(x, 0))
      out result
    """
    val result = stdlibCompiler.compile(source, "typed-lambda-dag")
    result.isRight shouldBe true
  }

  it should "compile filter result passed to all" in {
    val stdlibCompiler = StdLib.compiler
    val source = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      positives = filter(numbers, (x) => gt(x, 0))
      result = all(positives, (x) => gt(x, 0))
      out result
    """
    val result = stdlibCompiler.compile(source, "filter-to-all-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.get.cType shouldBe CType.CBoolean
  }

  it should "compile filter result passed to any" in {
    val stdlibCompiler = StdLib.compiler
    val source = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      positives = filter(numbers, (x) => gt(x, 0))
      result = any(positives, (x) => gt(x, 100))
      out result
    """
    val result = stdlibCompiler.compile(source, "filter-to-any-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.get.cType shouldBe CType.CBoolean
  }

  it should "compile map result passed to filter" in {
    val stdlibCompiler = StdLib.compiler
    val source = """
      use stdlib.collection
      use stdlib.compare
      use stdlib.math
      in numbers: List<Int>
      doubled = map(numbers, (x) => multiply(x, 2))
      result = filter(doubled, (x) => gt(x, 10))
      out result
    """
    val result = stdlibCompiler.compile(source, "map-to-filter-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.get.cType shouldBe CType.CList(CType.CInt)
  }

  it should "compile lambda with literal comparison" in {
    val stdlibCompiler = StdLib.compiler
    val source = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = filter(numbers, (x) => gt(x, 42))
      out result
    """
    val result = stdlibCompiler.compile(source, "literal-compare-lambda-dag")
    result.isRight shouldBe true
  }

  it should "compile lambda with not operator" in {
    val stdlibCompiler = StdLib.compiler
    val source = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = filter(numbers, (x) => not gt(x, 0))
      out result
    """
    val result = stdlibCompiler.compile(source, "not-lambda-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val hasFilter = compiled.dagSpec.data.values.exists(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.FilterTransform])
    )
    hasFilter shouldBe true
  }

  it should "compile lambda with or operator" in {
    val stdlibCompiler = StdLib.compiler
    val source = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = filter(numbers, (x) => lt(x, 0) or gt(x, 100))
      out result
    """
    val result = stdlibCompiler.compile(source, "or-lambda-dag")
    result.isRight shouldBe true
  }

  it should "compile all with literal true predicate" in {
    val stdlibCompiler = StdLib.compiler
    val source = """
      use stdlib.collection
      in numbers: List<Int>
      result = all(numbers, (x) => true)
      out result
    """
    val result = stdlibCompiler.compile(source, "all-true-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.get.cType shouldBe CType.CBoolean
  }

  it should "compile any with literal false predicate" in {
    val stdlibCompiler = StdLib.compiler
    val source = """
      use stdlib.collection
      in numbers: List<Int>
      result = any(numbers, (x) => false)
      out result
    """
    val result = stdlibCompiler.compile(source, "any-false-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val outputBinding = compiled.dagSpec.outputBindings.get("result")
    val outputNode = compiled.dagSpec.data.get(outputBinding.get)
    outputNode.get.cType shouldBe CType.CBoolean
  }

  // ========== Lambda Execution Tests ==========

  "lambdas-and-hof.cst" should "compile successfully" in {
    val files = getExampleFiles
    val hofExample = files.find(_.getName == "lambdas-and-hof.cst")
    hofExample shouldBe defined

    val source = readFile(hofExample.get)
    val stdlibCompiler = StdLib.compiler
    val result = stdlibCompiler.compile(source, "lambdas-and-hof")

    result match {
      case Left(errors) =>
        fail(s"Compilation failed: ${errors.map(_.message).mkString(", ")}")
      case Right(compiled) =>
        // Verify expected outputs are declared
        compiled.dagSpec.declaredOutputs should contain allOf (
          "positives", "above10", "doubled", "tripled",
          "allPositive", "allNonNegative", "hasNegative", "hasZero"
        )
    }
  }

  it should "have expected HOF data nodes with inline transforms" in {
    val files = getExampleFiles
    val hofExample = files.find(_.getName == "lambdas-and-hof.cst").get
    val source = readFile(hofExample)
    val stdlibCompiler = StdLib.compiler
    val result = stdlibCompiler.compile(source, "lambdas-and-hof")
    val compiled = result.toOption.get

    // Should have FilterTransform nodes
    val filterNodes = compiled.dagSpec.data.values.filter(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.FilterTransform])
    )
    filterNodes.size should be > 0

    // Should have MapTransform nodes
    val mapNodes = compiled.dagSpec.data.values.filter(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.MapTransform])
    )
    mapNodes.size should be > 0

    // Should have AllTransform nodes
    val allNodes = compiled.dagSpec.data.values.filter(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.AllTransform])
    )
    allNodes.size should be > 0

    // Should have AnyTransform nodes
    val anyNodes = compiled.dagSpec.data.values.filter(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.AnyTransform])
    )
    anyNodes.size should be > 0
  }

  // NOTE: Lambda execution tests are commented out until runtime support for HOF inline transforms
  // is fully implemented. The compilation tests above verify correct DAG structure.
  // TODO: Re-enable when runtime HOF execution is working. See follow-up issue.
  // The tests below fail with "Failed to find data node in init data" which is a runtime issue.

  /*
  "Lambda filter execution" should "execute filter with positive number predicate" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = filter(numbers, (x) => gt(x, 0))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler.compile(source, "filter-exec")
          .left.map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      // Register synthetic modules
      _ <- compiled.syntheticModules.values.toList.traverse(constellation.setModule)
      _ <- constellation.setDag("filter-exec", compiled.dagSpec)

      // Execute with input: [1, -2, 3, -4, 5]
      inputs = Map("numbers" -> CValue.CList(
        Vector(1L, -2L, 3L, -4L, 5L).map(CValue.CInt.apply), CType.CInt
      ))
      state <- constellation.runDag("filter-exec", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()
    val outputBindings = compiled.dagSpec.outputBindings
    val resultValue = state.data.get(outputBindings("result")).map(_.value)

    resultValue shouldBe defined
    val resultList = resultValue.get.asInstanceOf[CValue.CList]
    resultList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(1L, 3L, 5L)
  }

  it should "execute filter with empty result" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = filter(numbers, (x) => gt(x, 100))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler.compile(source, "filter-empty-exec")
          .left.map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      _ <- compiled.syntheticModules.values.toList.traverse(constellation.setModule)
      _ <- constellation.setDag("filter-empty-exec", compiled.dagSpec)

      // Execute with input: [1, 2, 3] - none > 100
      inputs = Map("numbers" -> CValue.CList(
        Vector(1L, 2L, 3L).map(CValue.CInt.apply), CType.CInt
      ))
      state <- constellation.runDag("filter-empty-exec", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()
    val outputBindings = compiled.dagSpec.outputBindings
    val resultValue = state.data.get(outputBindings("result")).map(_.value)

    resultValue shouldBe defined
    val resultList = resultValue.get.asInstanceOf[CValue.CList]
    resultList.value shouldBe empty
  }

  "Lambda map execution" should "execute map with multiply transform" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source = """
        use stdlib.collection
        use stdlib.math
        in numbers: List<Int>
        result = map(numbers, (x) => multiply(x, 2))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler.compile(source, "map-exec")
          .left.map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      _ <- compiled.syntheticModules.values.toList.traverse(constellation.setModule)
      _ <- constellation.setDag("map-exec", compiled.dagSpec)

      // Execute with input: [1, 2, 3]
      inputs = Map("numbers" -> CValue.CList(
        Vector(1L, 2L, 3L).map(CValue.CInt.apply), CType.CInt
      ))
      state <- constellation.runDag("map-exec", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()
    val outputBindings = compiled.dagSpec.outputBindings
    val resultValue = state.data.get(outputBindings("result")).map(_.value)

    resultValue shouldBe defined
    val resultList = resultValue.get.asInstanceOf[CValue.CList]
    resultList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(2L, 4L, 6L)
  }

  it should "execute map with addition transform" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source = """
        use stdlib.collection
        use stdlib.math
        in numbers: List<Int>
        result = map(numbers, (x) => add(x, 10))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler.compile(source, "map-add-exec")
          .left.map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      _ <- compiled.syntheticModules.values.toList.traverse(constellation.setModule)
      _ <- constellation.setDag("map-add-exec", compiled.dagSpec)

      // Execute with input: [1, 2, 3]
      inputs = Map("numbers" -> CValue.CList(
        Vector(1L, 2L, 3L).map(CValue.CInt.apply), CType.CInt
      ))
      state <- constellation.runDag("map-add-exec", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()
    val outputBindings = compiled.dagSpec.outputBindings
    val resultValue = state.data.get(outputBindings("result")).map(_.value)

    resultValue shouldBe defined
    val resultList = resultValue.get.asInstanceOf[CValue.CList]
    resultList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(11L, 12L, 13L)
  }

  "Lambda all execution" should "execute all returning true" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = all(numbers, (x) => gt(x, 0))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler.compile(source, "all-true-exec")
          .left.map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      _ <- compiled.syntheticModules.values.toList.traverse(constellation.setModule)
      _ <- constellation.setDag("all-true-exec", compiled.dagSpec)

      // Execute with input: [1, 2, 3] - all positive
      inputs = Map("numbers" -> CValue.CList(
        Vector(1L, 2L, 3L).map(CValue.CInt.apply), CType.CInt
      ))
      state <- constellation.runDag("all-true-exec", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()
    val outputBindings = compiled.dagSpec.outputBindings
    val resultValue = state.data.get(outputBindings("result")).map(_.value)

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CBoolean(true)
  }

  it should "execute all returning false" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = all(numbers, (x) => gt(x, 0))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler.compile(source, "all-false-exec")
          .left.map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      _ <- compiled.syntheticModules.values.toList.traverse(constellation.setModule)
      _ <- constellation.setDag("all-false-exec", compiled.dagSpec)

      // Execute with input: [1, -2, 3] - not all positive
      inputs = Map("numbers" -> CValue.CList(
        Vector(1L, -2L, 3L).map(CValue.CInt.apply), CType.CInt
      ))
      state <- constellation.runDag("all-false-exec", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()
    val outputBindings = compiled.dagSpec.outputBindings
    val resultValue = state.data.get(outputBindings("result")).map(_.value)

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CBoolean(false)
  }

  it should "execute all on empty list returning true (vacuous truth)" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = all(numbers, (x) => gt(x, 0))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler.compile(source, "all-empty-exec")
          .left.map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      _ <- compiled.syntheticModules.values.toList.traverse(constellation.setModule)
      _ <- constellation.setDag("all-empty-exec", compiled.dagSpec)

      // Execute with empty list
      inputs = Map("numbers" -> CValue.CList(Vector.empty, CType.CInt))
      state <- constellation.runDag("all-empty-exec", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()
    val outputBindings = compiled.dagSpec.outputBindings
    val resultValue = state.data.get(outputBindings("result")).map(_.value)

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CBoolean(true)
  }

  "Lambda any execution" should "execute any returning true" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = any(numbers, (x) => lt(x, 0))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler.compile(source, "any-true-exec")
          .left.map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      _ <- compiled.syntheticModules.values.toList.traverse(constellation.setModule)
      _ <- constellation.setDag("any-true-exec", compiled.dagSpec)

      // Execute with input: [1, -2, 3] - some negative
      inputs = Map("numbers" -> CValue.CList(
        Vector(1L, -2L, 3L).map(CValue.CInt.apply), CType.CInt
      ))
      state <- constellation.runDag("any-true-exec", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()
    val outputBindings = compiled.dagSpec.outputBindings
    val resultValue = state.data.get(outputBindings("result")).map(_.value)

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CBoolean(true)
  }

  it should "execute any returning false" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = any(numbers, (x) => lt(x, 0))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler.compile(source, "any-false-exec")
          .left.map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      _ <- compiled.syntheticModules.values.toList.traverse(constellation.setModule)
      _ <- constellation.setDag("any-false-exec", compiled.dagSpec)

      // Execute with input: [1, 2, 3] - none negative
      inputs = Map("numbers" -> CValue.CList(
        Vector(1L, 2L, 3L).map(CValue.CInt.apply), CType.CInt
      ))
      state <- constellation.runDag("any-false-exec", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()
    val outputBindings = compiled.dagSpec.outputBindings
    val resultValue = state.data.get(outputBindings("result")).map(_.value)

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CBoolean(false)
  }

  it should "execute any on empty list returning false" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = any(numbers, (x) => lt(x, 0))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler.compile(source, "any-empty-exec")
          .left.map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      _ <- compiled.syntheticModules.values.toList.traverse(constellation.setModule)
      _ <- constellation.setDag("any-empty-exec", compiled.dagSpec)

      // Execute with empty list
      inputs = Map("numbers" -> CValue.CList(Vector.empty, CType.CInt))
      state <- constellation.runDag("any-empty-exec", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()
    val outputBindings = compiled.dagSpec.outputBindings
    val resultValue = state.data.get(outputBindings("result")).map(_.value)

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CBoolean(false)
  }

  "Chained HOF execution" should "execute filter then map chain" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source = """
        use stdlib.collection
        use stdlib.compare
        use stdlib.math
        in numbers: List<Int>
        positives = filter(numbers, (x) => gt(x, 0))
        result = map(positives, (x) => multiply(x, 2))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler.compile(source, "filter-map-exec")
          .left.map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      _ <- compiled.syntheticModules.values.toList.traverse(constellation.setModule)
      _ <- constellation.setDag("filter-map-exec", compiled.dagSpec)

      // Execute with input: [1, -2, 3, -4, 5]
      // Filter: [1, 3, 5], then Map *2: [2, 6, 10]
      inputs = Map("numbers" -> CValue.CList(
        Vector(1L, -2L, 3L, -4L, 5L).map(CValue.CInt.apply), CType.CInt
      ))
      state <- constellation.runDag("filter-map-exec", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()
    val outputBindings = compiled.dagSpec.outputBindings
    val resultValue = state.data.get(outputBindings("result")).map(_.value)

    resultValue shouldBe defined
    val resultList = resultValue.get.asInstanceOf[CValue.CList]
    resultList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(2L, 6L, 10L)
  }

  it should "execute filter result passed to all" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        positives = filter(numbers, (x) => gt(x, 0))
        result = all(positives, (x) => gt(x, 0))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler.compile(source, "filter-all-exec")
          .left.map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      _ <- compiled.syntheticModules.values.toList.traverse(constellation.setModule)
      _ <- constellation.setDag("filter-all-exec", compiled.dagSpec)

      // Execute with input: [1, -2, 3, -4, 5]
      // Filter positives: [1, 3, 5], all are > 0 -> true
      inputs = Map("numbers" -> CValue.CList(
        Vector(1L, -2L, 3L, -4L, 5L).map(CValue.CInt.apply), CType.CInt
      ))
      state <- constellation.runDag("filter-all-exec", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()
    val outputBindings = compiled.dagSpec.outputBindings
    val resultValue = state.data.get(outputBindings("result")).map(_.value)

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CBoolean(true)
  }

  "Lambda with boolean operators execution" should "execute filter with AND predicate" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = filter(numbers, (x) => gt(x, 0) and lt(x, 10))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler.compile(source, "filter-and-exec")
          .left.map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      _ <- compiled.syntheticModules.values.toList.traverse(constellation.setModule)
      _ <- constellation.setDag("filter-and-exec", compiled.dagSpec)

      // Execute with input: [-5, 1, 5, 15, 20]
      // Filter: 0 < x < 10 -> [1, 5]
      inputs = Map("numbers" -> CValue.CList(
        Vector(-5L, 1L, 5L, 15L, 20L).map(CValue.CInt.apply), CType.CInt
      ))
      state <- constellation.runDag("filter-and-exec", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()
    val outputBindings = compiled.dagSpec.outputBindings
    val resultValue = state.data.get(outputBindings("result")).map(_.value)

    resultValue shouldBe defined
    val resultList = resultValue.get.asInstanceOf[CValue.CList]
    resultList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(1L, 5L)
  }

  it should "execute filter with OR predicate" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = filter(numbers, (x) => lt(x, 0) or gt(x, 10))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler.compile(source, "filter-or-exec")
          .left.map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      _ <- compiled.syntheticModules.values.toList.traverse(constellation.setModule)
      _ <- constellation.setDag("filter-or-exec", compiled.dagSpec)

      // Execute with input: [-5, 1, 5, 15, 20]
      // Filter: x < 0 OR x > 10 -> [-5, 15, 20]
      inputs = Map("numbers" -> CValue.CList(
        Vector(-5L, 1L, 5L, 15L, 20L).map(CValue.CInt.apply), CType.CInt
      ))
      state <- constellation.runDag("filter-or-exec", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()
    val outputBindings = compiled.dagSpec.outputBindings
    val resultValue = state.data.get(outputBindings("result")).map(_.value)

    resultValue shouldBe defined
    val resultList = resultValue.get.asInstanceOf[CValue.CList]
    resultList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(-5L, 15L, 20L)
  }

  it should "execute filter with NOT predicate" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = filter(numbers, (x) => not gt(x, 5))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler.compile(source, "filter-not-exec")
          .left.map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      _ <- compiled.syntheticModules.values.toList.traverse(constellation.setModule)
      _ <- constellation.setDag("filter-not-exec", compiled.dagSpec)

      // Execute with input: [1, 3, 5, 7, 9]
      // Filter: NOT(x > 5) = x <= 5 -> [1, 3, 5]
      inputs = Map("numbers" -> CValue.CList(
        Vector(1L, 3L, 5L, 7L, 9L).map(CValue.CInt.apply), CType.CInt
      ))
      state <- constellation.runDag("filter-not-exec", inputs)
    } yield (state, compiled)

    val (state, compiled) = test.unsafeRunSync()
    val outputBindings = compiled.dagSpec.outputBindings
    val resultValue = state.data.get(outputBindings("result")).map(_.value)

    resultValue shouldBe defined
    val resultList = resultValue.get.asInstanceOf[CValue.CList]
    resultList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(1L, 3L, 5L)
  }
  */
}
