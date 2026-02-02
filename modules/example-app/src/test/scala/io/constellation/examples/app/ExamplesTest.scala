package io.constellation.examples.app

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import io.constellation.*
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.compiler.CompilationOutput
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
    compiled.program.image.dagSpec.name shouldBe "simple-test"
  }

  it should "have correct input and output structure" in {
    val files      = getExampleFiles
    val simpleTest = files.find(_.getName == "simple-test.cst").get
    val source     = readFile(simpleTest)
    val result     = compiler.compile(source, "simple-test")

    val compiled = result.toOption.get

    // Should have 'message' as input
    val inputNames = compiled.program.image.dagSpec.data.values.map(_.name).toSet
    inputNames should contain("message")

    // Should have 'result' as declared output
    compiled.program.image.dagSpec.declaredOutputs should contain("result")

    // Should have the Uppercase module
    compiled.program.image.dagSpec.modules.values
      .map(_.name)
      .exists(_.contains("Uppercase")) shouldBe true
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
    compiled.program.image.dagSpec.name shouldBe "text-analysis"
  }

  it should "have correct input and output structure" in {
    val files        = getExampleFiles
    val textAnalysis = files.find(_.getName == "text-analysis.cst").get
    val source       = readFile(textAnalysis)
    val result       = compiler.compile(source, "text-analysis")

    val compiled = result.toOption.get

    // Should have 'document' as input
    val inputNames = compiled.program.image.dagSpec.data.values.map(_.name).toSet
    inputNames should contain("document")

    // Should have multiple outputs
    compiled.program.image.dagSpec.declaredOutputs should contain allOf ("cleaned", "normalized", "words", "chars", "lines")

    // Should have the text processing modules
    val moduleNames = compiled.program.image.dagSpec.modules.values.map(_.name).toList
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
    compiled.program.image.dagSpec.name shouldBe "data-pipeline"
  }

  it should "have correct input structure" in {
    val files        = getExampleFiles
    val dataPipeline = files.find(_.getName == "data-pipeline.cst").get
    val source       = readFile(dataPipeline)
    val result       = compiler.compile(source, "data-pipeline")

    val compiled = result.toOption.get

    // Should have 'numbers', 'threshold', and 'multiplier' as inputs
    val inputNames = compiled.program.image.dagSpec.data.values.map(_.name).toSet
    inputNames should contain allOf ("numbers", "threshold", "multiplier")
  }

  it should "have correct output structure" in {
    val files        = getExampleFiles
    val dataPipeline = files.find(_.getName == "data-pipeline.cst").get
    val source       = readFile(dataPipeline)
    val result       = compiler.compile(source, "data-pipeline")

    val compiled = result.toOption.get

    // Should have all declared outputs
    compiled.program.image.dagSpec.declaredOutputs should contain allOf (
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
    val moduleNames = compiled.program.image.dagSpec.modules.values.map(_.name).toList
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
        compiled.program.image.dagSpec.name shouldBe dagName
        compiled.program.image.dagSpec.data should not be empty
        compiled.program.image.dagSpec.declaredOutputs should not be empty
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

      // Execute with input
      inputs = Map("message" -> CValue.CString("hello world"))
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")
    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CString("HELLO WORLD")
  }

  "text-analysis.cst" should "execute with correct outputs" in {
    val test = for {
      constellation <- createConstellation
      files        = getExampleFiles
      textAnalysis = files.find(_.getName == "text-analysis.cst").get
      source       = readFile(textAnalysis)

      // Compile
      compiled = compiler.compile(source, "text-analysis").toOption.get

      // Execute with input
      inputs = Map("document" -> CValue.CString("  Hello World\nLine 2  "))
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()

    // Verify outputs exist
    sig.outputs.keys should contain allOf ("cleaned", "normalized", "words", "chars", "lines")

    // Check cleaned output
    val cleanedValue = sig.outputs.get("cleaned")
    cleanedValue shouldBe defined
    cleanedValue.get shouldBe CValue.CString("Hello World\nLine 2")

    // Check normalized output
    val normalizedValue = sig.outputs.get("normalized")
    normalizedValue shouldBe defined
    normalizedValue.get shouldBe CValue.CString("hello world\nline 2")

    // Check word count
    val wordsValue = sig.outputs.get("words")
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

      // Execute with inputs: numbers=[1,2,3,4,5], threshold=2, multiplier=3
      // Expected: filtered=[3,4,5], scaled=[9,12,15], total=36, avg=12.0, highest=15, lowest=9
      inputs = Map(
        "numbers"    -> CValue.CList(Vector(1L, 2L, 3L, 4L, 5L).map(CValue.CInt.apply), CType.CInt),
        "threshold"  -> CValue.CInt(2),
        "multiplier" -> CValue.CInt(3)
      )
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()

    sig.outputs.keys should contain allOf ("filtered", "scaled", "total", "avg", "highest", "lowest", "formattedTotal")

    // Verify filtered output: [3, 4, 5]
    val filteredValue = sig.outputs.get("filtered")
    filteredValue shouldBe defined
    val filteredList = filteredValue.get.asInstanceOf[CValue.CList]
    filteredList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(3L, 4L, 5L)

    // Verify scaled output: [9, 12, 15]
    val scaledValue = sig.outputs.get("scaled")
    scaledValue shouldBe defined
    val scaledList = scaledValue.get.asInstanceOf[CValue.CList]
    scaledList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(9L, 12L, 15L)

    // Verify total: 36
    val totalValue = sig.outputs.get("total")
    totalValue shouldBe defined
    totalValue.get shouldBe CValue.CInt(36)

    // Verify avg: 12.0
    val avgValue = sig.outputs.get("avg")
    avgValue shouldBe defined
    avgValue.get shouldBe CValue.CFloat(12.0)

    // Verify highest: 15
    val highestValue = sig.outputs.get("highest")
    highestValue shouldBe defined
    highestValue.get shouldBe CValue.CInt(15)

    // Verify lowest: 9
    val lowestValue = sig.outputs.get("lowest")
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
    compiled.program.image.dagSpec.data should have size 1

    val inputNode = compiled.program.image.dagSpec.data.values.head
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

    val compiled  = result.toOption.get
    val inputNode = compiled.program.image.dagSpec.data.values.head
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

    val compiled  = result.toOption.get
    val inputNode = compiled.program.image.dagSpec.data.values.head
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

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("y")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.program.image.dagSpec.data.get(outputBinding.get)
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

    val compiled  = result.toOption.get
    val inputNode = compiled.program.image.dagSpec.data.values.head
    inputNode.cType match {
      case CType.CUnion(structure) =>
        structure should have size 2
        structure.keys should contain("String")
        structure.values.exists {
          case CType.COptional(CType.CInt) => true
          case _                           => false
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

    val compiled  = result.toOption.get
    val inputNode = compiled.program.image.dagSpec.data.values.head
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

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.program.image.dagSpec.data.get(outputBinding.get)
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

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.program.image.dagSpec.data.get(outputBinding.get)
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

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.program.image.dagSpec.data.get(outputBinding.get)
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

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.program.image.dagSpec.data.get(outputBinding.get)
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

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.program.image.dagSpec.data.get(outputBinding.get)
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

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.program.image.dagSpec.data.get(outputBinding.get)
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

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.program.image.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.CInt
  }

  // ========== Lambda and Higher-Order Function Integration Tests ==========

  "Lambda and HOF programs" should "compile filter with lambda" in {
    val stdlibCompiler = StdLib.compiler
    val source         = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = filter(numbers, (x) => gt(x, 0))
      out result
    """
    val result         = stdlibCompiler.compile(source, "filter-lambda-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.program.image.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.CList(CType.CInt)
  }

  it should "compile map with lambda" in {
    val stdlibCompiler = StdLib.compiler
    val source         = """
      use stdlib.collection
      use stdlib.math
      in numbers: List<Int>
      result = map(numbers, (x) => multiply(x, 2))
      out result
    """
    val result         = stdlibCompiler.compile(source, "map-lambda-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.program.image.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.CList(CType.CInt)
  }

  it should "compile all with lambda" in {
    val stdlibCompiler = StdLib.compiler
    val source         = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = all(numbers, (x) => gt(x, 0))
      out result
    """
    val result         = stdlibCompiler.compile(source, "all-lambda-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.program.image.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.CBoolean
  }

  it should "compile any with lambda" in {
    val stdlibCompiler = StdLib.compiler
    val source         = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = any(numbers, (x) => lt(x, 0))
      out result
    """
    val result         = stdlibCompiler.compile(source, "any-lambda-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.program.image.dagSpec.data.get(outputBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.CBoolean
  }

  it should "compile chained filter and map with lambdas" in {
    val stdlibCompiler = StdLib.compiler
    val source         = """
      use stdlib.collection
      use stdlib.compare
      use stdlib.math
      in numbers: List<Int>
      positives = filter(numbers, (x) => gt(x, 0))
      result = map(positives, (x) => multiply(x, 2))
      out result
    """
    val result         = stdlibCompiler.compile(source, "filter-map-chain-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Verify both filter and map nodes exist
    val hasFilter = compiled.program.image.dagSpec.data.values.exists(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.FilterTransform])
    )
    val hasMap = compiled.program.image.dagSpec.data.values.exists(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.MapTransform])
    )
    hasFilter shouldBe true
    hasMap shouldBe true
  }

  it should "compile lambda with boolean operators in body" in {
    val stdlibCompiler = StdLib.compiler
    val source         = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = filter(numbers, (x) => gt(x, 0) and lt(x, 100))
      out result
    """
    val result         = stdlibCompiler.compile(source, "lambda-bool-ops-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    outputBinding.isDefined shouldBe true

    val outputNode = compiled.program.image.dagSpec.data.get(outputBinding.get)
    outputNode.get.cType shouldBe CType.CList(CType.CInt)
  }

  it should "compile lambda with explicit type annotation" in {
    val stdlibCompiler = StdLib.compiler
    val source         = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = filter(numbers, (x: Int) => gt(x, 0))
      out result
    """
    val result         = stdlibCompiler.compile(source, "typed-lambda-dag")
    result.isRight shouldBe true
  }

  it should "compile filter result passed to all" in {
    val stdlibCompiler = StdLib.compiler
    val source         = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      positives = filter(numbers, (x) => gt(x, 0))
      result = all(positives, (x) => gt(x, 0))
      out result
    """
    val result         = stdlibCompiler.compile(source, "filter-to-all-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    val outputNode    = compiled.program.image.dagSpec.data.get(outputBinding.get)
    outputNode.get.cType shouldBe CType.CBoolean
  }

  it should "compile filter result passed to any" in {
    val stdlibCompiler = StdLib.compiler
    val source         = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      positives = filter(numbers, (x) => gt(x, 0))
      result = any(positives, (x) => gt(x, 100))
      out result
    """
    val result         = stdlibCompiler.compile(source, "filter-to-any-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    val outputNode    = compiled.program.image.dagSpec.data.get(outputBinding.get)
    outputNode.get.cType shouldBe CType.CBoolean
  }

  it should "compile map result passed to filter" in {
    val stdlibCompiler = StdLib.compiler
    val source         = """
      use stdlib.collection
      use stdlib.compare
      use stdlib.math
      in numbers: List<Int>
      doubled = map(numbers, (x) => multiply(x, 2))
      result = filter(doubled, (x) => gt(x, 10))
      out result
    """
    val result         = stdlibCompiler.compile(source, "map-to-filter-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    val outputNode    = compiled.program.image.dagSpec.data.get(outputBinding.get)
    outputNode.get.cType shouldBe CType.CList(CType.CInt)
  }

  it should "compile lambda with literal comparison" in {
    val stdlibCompiler = StdLib.compiler
    val source         = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = filter(numbers, (x) => gt(x, 42))
      out result
    """
    val result         = stdlibCompiler.compile(source, "literal-compare-lambda-dag")
    result.isRight shouldBe true
  }

  it should "compile lambda with not operator" in {
    val stdlibCompiler = StdLib.compiler
    val source         = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = filter(numbers, (x) => not gt(x, 0))
      out result
    """
    val result         = stdlibCompiler.compile(source, "not-lambda-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val hasFilter = compiled.program.image.dagSpec.data.values.exists(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.FilterTransform])
    )
    hasFilter shouldBe true
  }

  it should "compile lambda with or operator" in {
    val stdlibCompiler = StdLib.compiler
    val source         = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = filter(numbers, (x) => lt(x, 0) or gt(x, 100))
      out result
    """
    val result         = stdlibCompiler.compile(source, "or-lambda-dag")
    result.isRight shouldBe true
  }

  it should "compile all with literal true predicate" in {
    val stdlibCompiler = StdLib.compiler
    val source         = """
      use stdlib.collection
      in numbers: List<Int>
      result = all(numbers, (x) => true)
      out result
    """
    val result         = stdlibCompiler.compile(source, "all-true-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    val outputNode    = compiled.program.image.dagSpec.data.get(outputBinding.get)
    outputNode.get.cType shouldBe CType.CBoolean
  }

  it should "compile any with literal false predicate" in {
    val stdlibCompiler = StdLib.compiler
    val source         = """
      use stdlib.collection
      in numbers: List<Int>
      result = any(numbers, (x) => false)
      out result
    """
    val result         = stdlibCompiler.compile(source, "any-false-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    val outputNode    = compiled.program.image.dagSpec.data.get(outputBinding.get)
    outputNode.get.cType shouldBe CType.CBoolean
  }

  // ========== Lambda Execution Tests ==========

  "lambdas-and-hof.cst" should "compile successfully" in {
    val files      = getExampleFiles
    val hofExample = files.find(_.getName == "lambdas-and-hof.cst")
    hofExample shouldBe defined

    val source         = readFile(hofExample.get)
    val stdlibCompiler = StdLib.compiler
    val result         = stdlibCompiler.compile(source, "lambdas-and-hof")

    result match {
      case Left(errors) =>
        fail(s"Compilation failed: ${errors.map(_.message).mkString(", ")}")
      case Right(compiled) =>
        // Verify expected outputs are declared
        compiled.program.image.dagSpec.declaredOutputs should contain allOf (
          "positives", "above10", "doubled", "tripled",
          "allPositive", "allNonNegative", "hasNegative", "hasZero"
        )
    }
  }

  it should "have expected HOF data nodes with inline transforms" in {
    val files          = getExampleFiles
    val hofExample     = files.find(_.getName == "lambdas-and-hof.cst").get
    val source         = readFile(hofExample)
    val stdlibCompiler = StdLib.compiler
    val result         = stdlibCompiler.compile(source, "lambdas-and-hof")
    val compiled       = result.toOption.get

    // Should have FilterTransform nodes
    val filterNodes = compiled.program.image.dagSpec.data.values.filter(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.FilterTransform])
    )
    filterNodes.size should be > 0

    // Should have MapTransform nodes
    val mapNodes = compiled.program.image.dagSpec.data.values.filter(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.MapTransform])
    )
    mapNodes.size should be > 0

    // Should have AllTransform nodes
    val allNodes = compiled.program.image.dagSpec.data.values.filter(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.AllTransform])
    )
    allNodes.size should be > 0

    // Should have AnyTransform nodes
    val anyNodes = compiled.program.image.dagSpec.data.values.filter(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.AnyTransform])
    )
    anyNodes.size should be > 0
  }

  // ========== String Interpolation Tests ==========

  "String interpolation programs" should "compile simple interpolation" in {
    val stdlibCompiler = StdLib.compiler

    val source = """
      in name: String
      result = "Hello, ${name}!"
      out result
    """

    val result = stdlibCompiler.compile(source, "interp-simple-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val outputBinding = compiled.program.image.dagSpec.outputBindings.get("result")
    val outputNode    = compiled.program.image.dagSpec.data.get(outputBinding.get)
    outputNode.get.cType shouldBe CType.CString
  }

  it should "compile interpolation with multiple expressions" in {
    val stdlibCompiler = StdLib.compiler

    val source = """
      in firstName: String
      in lastName: String
      in age: Int
      result = "${firstName} ${lastName}, age ${age}"
      out result
    """

    val result = stdlibCompiler.compile(source, "interp-multi-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.program.image.dagSpec.data.values.exists(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.StringInterpolationTransform])
    ) shouldBe true
  }

  it should "compile interpolation with arithmetic expression" in {
    val stdlibCompiler = StdLib.compiler

    val source = """
      use stdlib.math
      in a: Int
      in b: Int
      result = "Sum: ${add(a, b)}"
      out result
    """

    val result = stdlibCompiler.compile(source, "interp-arith-dag")
    result.isRight shouldBe true
  }

  it should "compile interpolation with field access" in {
    val stdlibCompiler = StdLib.compiler

    val source = """
      in user: { name: String, age: Int }
      result = "User ${user.name} is ${user.age} years old"
      out result
    """

    val result = stdlibCompiler.compile(source, "interp-field-dag")
    result.isRight shouldBe true
  }

  it should "compile interpolation with conditional expression" in {
    val stdlibCompiler = StdLib.compiler

    val source = """
      in flag: Boolean
      result = "Status: ${if (flag) 1 else 0}"
      out result
    """

    val result = stdlibCompiler.compile(source, "interp-cond-dag")
    result.isRight shouldBe true
  }

  it should "compile interpolation with boolean value" in {
    val stdlibCompiler = StdLib.compiler

    val source = """
      in active: Boolean
      result = "Active: ${active}"
      out result
    """

    val result = stdlibCompiler.compile(source, "interp-bool-dag")
    result.isRight shouldBe true
  }

  it should "compile interpolation with Optional value" in {
    val stdlibCompiler = StdLib.compiler

    val source = """
      in maybeValue: Optional<String>
      result = "Value: ${maybeValue}"
      out result
    """

    val result = stdlibCompiler.compile(source, "interp-optional-dag")
    result.isRight shouldBe true
  }

  it should "compile interpolation with List value" in {
    val stdlibCompiler = StdLib.compiler

    val source = """
      in items: List<Int>
      result = "Items: ${items}"
      out result
    """

    val result = stdlibCompiler.compile(source, "interp-list-dag")
    result.isRight shouldBe true
  }

  it should "compile interpolation with function call" in {
    val stdlibCompiler = StdLib.compiler

    val source = """
      use stdlib.string
      in name: String
      result = "HELLO ${trim(name)}"
      out result
    """

    val result = stdlibCompiler.compile(source, "interp-func-dag")
    result.isRight shouldBe true
  }

  it should "compile interpolation used as function argument" in {
    val stdlibCompiler = StdLib.compiler

    val source = """
      use stdlib.string
      in name: String
      greeting = "Hello, ${name}!"
      result = trim(greeting)
      out result
    """

    val result = stdlibCompiler.compile(source, "interp-arg-dag")
    result.isRight shouldBe true
  }

  it should "compile chained string interpolations" in {
    val stdlibCompiler = StdLib.compiler

    val source = """
      in name: String
      greeting = "Hello, ${name}!"
      message = "Message: ${greeting}"
      out message
    """

    val result = stdlibCompiler.compile(source, "interp-chain-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have 2 StringInterpolationTransform nodes
    val interpCount = compiled.program.image.dagSpec.data.values.count(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.StringInterpolationTransform])
    )
    interpCount shouldBe 2
  }

  it should "compile interpolation with branch expression" in {
    val stdlibCompiler = StdLib.compiler

    val source = """
      use stdlib.compare
      in score: Int
      grade = branch {
        gt(score, 90) -> "A",
        gt(score, 80) -> "B",
        otherwise -> "C"
      }
      result = "Grade: ${grade}"
      out result
    """

    val result = stdlibCompiler.compile(source, "interp-branch-dag")
    result.isRight shouldBe true
  }

  it should "compile plain string without interpolation" in {
    val stdlibCompiler = StdLib.compiler

    val source = """
      result = "Hello, World!"
      out result
    """

    val result = stdlibCompiler.compile(source, "plain-string-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should NOT have StringInterpolationTransform for plain strings
    val hasStringInterp = compiled.program.image.dagSpec.data.values.exists(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.StringInterpolationTransform])
    )
    hasStringInterp shouldBe false
  }

  it should "compile string with escaped dollar sign" in {
    val stdlibCompiler = StdLib.compiler

    val source = """
      result = "Price: \$100"
      out result
    """

    val result = stdlibCompiler.compile(source, "escaped-dollar-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Escaped $ should be literal, not interpolation
    val hasStringInterp = compiled.program.image.dagSpec.data.values.exists(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.StringInterpolationTransform])
    )
    hasStringInterp shouldBe false
  }

  "string-interpolation.cst" should "compile successfully" in {
    val files         = getExampleFiles
    val interpExample = files.find(_.getName == "string-interpolation.cst")
    interpExample shouldBe defined

    val source = readFile(interpExample.get)
    // Use ExampleLib compiler which includes Uppercase and other example functions
    val result = compiler.compile(source, "string-interpolation")

    result match {
      case Left(errors) =>
        fail(s"Compilation failed: ${errors.map(_.message).mkString(", ")}")
      case Right(compiled) =>
        // Verify expected outputs are declared
        compiled.program.image.dagSpec.declaredOutputs should contain allOf (
          "greeting", "ageNextYear", "summary", "formatted", "statusMessage", "formalGreeting"
        )
    }
  }

  it should "have expected StringInterpolationTransform nodes" in {
    val files         = getExampleFiles
    val interpExample = files.find(_.getName == "string-interpolation.cst").get
    val source        = readFile(interpExample)
    // Use ExampleLib compiler which includes Uppercase and other example functions
    val result   = compiler.compile(source, "string-interpolation")
    val compiled = result.toOption.get

    // Should have multiple StringInterpolationTransform nodes
    val interpNodes = compiled.program.image.dagSpec.data.values.filter(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.StringInterpolationTransform])
    )
    interpNodes.size should be > 0
  }

  "String interpolation execution" should "execute simple string interpolation" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        in name: String
        result = "Hello, ${name}!"
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "interp-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      inputs = Map("name" -> CValue.CString("World"))
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CString("Hello, World!")
  }

  it should "execute string interpolation with multiple expressions" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        in firstName: String
        in lastName: String
        result = "${firstName} ${lastName}"
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "interp-multi-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      inputs = Map(
        "firstName" -> CValue.CString("John"),
        "lastName"  -> CValue.CString("Doe")
      )
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CString("John Doe")
  }

  it should "execute string interpolation with Int value" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        in count: Int
        result = "Count: ${count}"
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "interp-int-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      inputs = Map("count" -> CValue.CInt(42))
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CString("Count: 42")
  }

  it should "execute string interpolation with Boolean value" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        in active: Boolean
        result = "Active: ${active}"
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "interp-bool-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      inputs = Map("active" -> CValue.CBoolean(true))
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CString("Active: true")
  }

  it should "execute string interpolation at start of string" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        in name: String
        result = "${name} says hi"
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "interp-start-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      inputs = Map("name" -> CValue.CString("Alice"))
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CString("Alice says hi")
  }

  it should "execute string interpolation at end of string" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        in name: String
        result = "Hello ${name}"
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "interp-end-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      inputs = Map("name" -> CValue.CString("Bob"))
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CString("Hello Bob")
  }

  it should "execute string with only interpolation" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        in name: String
        result = "${name}"
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "interp-only-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      inputs = Map("name" -> CValue.CString("test"))
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CString("test")
  }

  it should "execute string interpolation with field access" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        in user: { name: String, age: Int }
        result = "User ${user.name} is ${user.age}"
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "interp-field-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      inputs = Map(
        "user" -> CValue.CProduct(
          Map("name" -> CValue.CString("Alice"), "age" -> CValue.CInt(30)),
          Map("name" -> CType.CString, "age"           -> CType.CInt)
        )
      )
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CString("User Alice is 30")
  }

  it should "execute chained string interpolations" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        in name: String
        greeting = "Hello, ${name}!"
        message = "Message: ${greeting}"
        out message
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "interp-chain-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      inputs = Map("name" -> CValue.CString("World"))
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("message")

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CString("Message: Hello, World!")
  }

  "Lambda filter execution" should "execute filter with positive number predicate" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = filter(numbers, (x) => gt(x, 0))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "filter-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      // Execute with input: [1, -2, 3, -4, 5]
      inputs = Map(
        "numbers" -> CValue.CList(
          Vector(1L, -2L, 3L, -4L, 5L).map(CValue.CInt.apply),
          CType.CInt
        )
      )
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    val resultList = resultValue.get.asInstanceOf[CValue.CList]
    resultList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(1L, 3L, 5L)
  }

  it should "execute filter with empty result" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = filter(numbers, (x) => gt(x, 100))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "filter-empty-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      // Execute with input: [1, 2, 3] - none > 100
      inputs = Map(
        "numbers" -> CValue.CList(
          Vector(1L, 2L, 3L).map(CValue.CInt.apply),
          CType.CInt
        )
      )
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    val resultList = resultValue.get.asInstanceOf[CValue.CList]
    resultList.value shouldBe empty
  }

  "Lambda map execution" should "execute map with multiply transform" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        use stdlib.collection
        use stdlib.math
        in numbers: List<Int>
        result = map(numbers, (x) => multiply(x, 2))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "map-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      // Execute with input: [1, 2, 3]
      inputs = Map(
        "numbers" -> CValue.CList(
          Vector(1L, 2L, 3L).map(CValue.CInt.apply),
          CType.CInt
        )
      )
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    val resultList = resultValue.get.asInstanceOf[CValue.CList]
    resultList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(2L, 4L, 6L)
  }

  it should "execute map with addition transform" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        use stdlib.collection
        use stdlib.math
        in numbers: List<Int>
        result = map(numbers, (x) => add(x, 10))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "map-add-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      // Execute with input: [1, 2, 3]
      inputs = Map(
        "numbers" -> CValue.CList(
          Vector(1L, 2L, 3L).map(CValue.CInt.apply),
          CType.CInt
        )
      )
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    val resultList = resultValue.get.asInstanceOf[CValue.CList]
    resultList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(11L, 12L, 13L)
  }

  "Lambda all execution" should "execute all returning true" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = all(numbers, (x) => gt(x, 0))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "all-true-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      // Execute with input: [1, 2, 3] - all positive
      inputs = Map(
        "numbers" -> CValue.CList(
          Vector(1L, 2L, 3L).map(CValue.CInt.apply),
          CType.CInt
        )
      )
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CBoolean(true)
  }

  it should "execute all returning false" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = all(numbers, (x) => gt(x, 0))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "all-false-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      // Execute with input: [1, -2, 3] - not all positive
      inputs = Map(
        "numbers" -> CValue.CList(
          Vector(1L, -2L, 3L).map(CValue.CInt.apply),
          CType.CInt
        )
      )
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CBoolean(false)
  }

  it should "execute all on empty list returning true (vacuous truth)" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = all(numbers, (x) => gt(x, 0))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "all-empty-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      // Execute with empty list
      inputs = Map("numbers" -> CValue.CList(Vector.empty, CType.CInt))
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CBoolean(true)
  }

  "Lambda any execution" should "execute any returning true" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = any(numbers, (x) => lt(x, 0))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "any-true-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      // Execute with input: [1, -2, 3] - some negative
      inputs = Map(
        "numbers" -> CValue.CList(
          Vector(1L, -2L, 3L).map(CValue.CInt.apply),
          CType.CInt
        )
      )
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CBoolean(true)
  }

  it should "execute any returning false" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = any(numbers, (x) => lt(x, 0))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "any-false-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      // Execute with input: [1, 2, 3] - none negative
      inputs = Map(
        "numbers" -> CValue.CList(
          Vector(1L, 2L, 3L).map(CValue.CInt.apply),
          CType.CInt
        )
      )
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CBoolean(false)
  }

  it should "execute any on empty list returning false" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = any(numbers, (x) => lt(x, 0))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "any-empty-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      // Execute with empty list
      inputs = Map("numbers" -> CValue.CList(Vector.empty, CType.CInt))
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CBoolean(false)
  }

  "Chained HOF execution" should "execute filter then map chain" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        use stdlib.collection
        use stdlib.compare
        use stdlib.math
        in numbers: List<Int>
        positives = filter(numbers, (x) => gt(x, 0))
        result = map(positives, (x) => multiply(x, 2))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "filter-map-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      // Execute with input: [1, -2, 3, -4, 5]
      // Filter: [1, 3, 5], then Map *2: [2, 6, 10]
      inputs = Map(
        "numbers" -> CValue.CList(
          Vector(1L, -2L, 3L, -4L, 5L).map(CValue.CInt.apply),
          CType.CInt
        )
      )
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    val resultList = resultValue.get.asInstanceOf[CValue.CList]
    resultList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(2L, 6L, 10L)
  }

  it should "execute filter result passed to all" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        positives = filter(numbers, (x) => gt(x, 0))
        result = all(positives, (x) => gt(x, 0))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "filter-all-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      // Execute with input: [1, -2, 3, -4, 5]
      // Filter positives: [1, 3, 5], all are > 0 -> true
      inputs = Map(
        "numbers" -> CValue.CList(
          Vector(1L, -2L, 3L, -4L, 5L).map(CValue.CInt.apply),
          CType.CInt
        )
      )
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    resultValue.get shouldBe CValue.CBoolean(true)
  }

  "Lambda with boolean operators execution" should "execute filter with AND predicate" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = filter(numbers, (x) => gt(x, 0) and lt(x, 10))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "filter-and-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      // Execute with input: [-5, 1, 5, 15, 20]
      // Filter: 0 < x < 10 -> [1, 5]
      inputs = Map(
        "numbers" -> CValue.CList(
          Vector(-5L, 1L, 5L, 15L, 20L).map(CValue.CInt.apply),
          CType.CInt
        )
      )
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    val resultList = resultValue.get.asInstanceOf[CValue.CList]
    resultList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(1L, 5L)
  }

  it should "execute filter with OR predicate" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = filter(numbers, (x) => lt(x, 0) or gt(x, 10))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "filter-or-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      // Execute with input: [-5, 1, 5, 15, 20]
      // Filter: x < 0 OR x > 10 -> [-5, 15, 20]
      inputs = Map(
        "numbers" -> CValue.CList(
          Vector(-5L, 1L, 5L, 15L, 20L).map(CValue.CInt.apply),
          CType.CInt
        )
      )
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    val resultList = resultValue.get.asInstanceOf[CValue.CList]
    resultList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(-5L, 15L, 20L)
  }

  it should "execute filter with NOT predicate" in {
    val test = for {
      constellation <- createConstellation
      stdlibCompiler = StdLib.compiler
      source         = """
        use stdlib.collection
        use stdlib.compare
        in numbers: List<Int>
        result = filter(numbers, (x) => not gt(x, 5))
        out result
      """
      compiled <- IO.fromEither(
        stdlibCompiler
          .compile(source, "filter-not-exec")
          .left
          .map(e => new Exception(e.map(_.message).mkString(", ")))
      )

      // Execute with input: [1, 3, 5, 7, 9]
      // Filter: NOT(x > 5) = x <= 5 -> [1, 3, 5]
      inputs = Map(
        "numbers" -> CValue.CList(
          Vector(1L, 3L, 5L, 7L, 9L).map(CValue.CInt.apply),
          CType.CInt
        )
      )
      sig <- constellation.run(compiled.program, inputs)
    } yield (sig, compiled)

    val (sig, compiled) = test.unsafeRunSync()
    val resultValue     = sig.outputs.get("result")

    resultValue shouldBe defined
    val resultList = resultValue.get.asInstanceOf[CValue.CList]
    resultList.value.map(_.asInstanceOf[CValue.CInt].value) shouldBe Vector(1L, 3L, 5L)
  }
}
