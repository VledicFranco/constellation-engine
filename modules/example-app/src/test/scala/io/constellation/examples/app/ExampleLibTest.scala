package io.constellation.examples.app

import io.constellation.examples.app.modules.{DataModules, ResilienceModules, TextModules}
import io.constellation.lang.LangCompilerBuilder
import io.constellation.lang.semantic.{FunctionRegistry, SemanticType}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Integration tests for ExampleLib.
  *
  * Tests module registration, function signature validation, and compiler integration.
  */
class ExampleLibTest extends AnyFlatSpec with Matchers {

  // ========== Module Registration Tests ==========

  "ExampleLib.allModules" should "contain all data and text modules" in {
    val modules = ExampleLib.allModules

    // Data modules
    modules.keys should contain("SumList")
    modules.keys should contain("Average")
    modules.keys should contain("Max")
    modules.keys should contain("Min")
    modules.keys should contain("FilterGreaterThan")
    modules.keys should contain("MultiplyEach")
    modules.keys should contain("Range")
    modules.keys should contain("FormatNumber")

    // Text modules
    modules.keys should contain("Uppercase")
    modules.keys should contain("Lowercase")
    modules.keys should contain("Trim")
    modules.keys should contain("Replace")
    modules.keys should contain("WordCount")
    modules.keys should contain("TextLength")
    modules.keys should contain("Contains")
    modules.keys should contain("SplitLines")
    modules.keys should contain("Split")

    // Resilience modules
    modules.keys should contain("SlowQuery")
    modules.keys should contain("SlowApiCall")
    modules.keys should contain("ExpensiveCompute")
    modules.keys should contain("FlakyService")
    modules.keys should contain("TimeoutProneService")
    modules.keys should contain("RateLimitedApi")
    modules.keys should contain("ResourceIntensiveTask")
    modules.keys should contain("QuickCheck")
    modules.keys should contain("DeepAnalysis")
    modules.keys should contain("AlwaysFailsService")
  }

  it should "have correct total module count" in {
    val modules = ExampleLib.allModules
    // 8 data modules + 9 text modules + 10 resilience modules = 27 total
    modules.size shouldBe 27
  }

  it should "have matching module names and spec names" in {
    ExampleLib.allModules.foreach { case (name, module) =>
      module.spec.name shouldBe name
    }
  }

  // ========== Function Signature Tests ==========

  "ExampleLib.dataSignatures" should "have correct count" in {
    ExampleLib.dataSignatures should have size 8
  }

  it should "have valid parameter types" in {
    ExampleLib.dataSignatures.foreach { sig =>
      sig.params should not be empty
      sig.params.foreach { case (_, paramType) =>
        paramType should not be null
      }
    }
  }

  "ExampleLib.textSignatures" should "have correct count" in {
    ExampleLib.textSignatures should have size 9
  }

  it should "have valid parameter types" in {
    ExampleLib.textSignatures.foreach { sig =>
      sig.params should not be empty
      sig.params.foreach { case (_, paramType) =>
        paramType should not be null
      }
    }
  }

  "ExampleLib.resilienceSignatures" should "have correct count" in {
    ExampleLib.resilienceSignatures should have size 10
  }

  it should "have valid parameter types" in {
    ExampleLib.resilienceSignatures.foreach { sig =>
      sig.params should not be empty
      sig.params.foreach { case (_, paramType) =>
        paramType should not be null
      }
    }
  }

  "ExampleLib.allSignatures" should "have correct total count" in {
    // 8 data + 9 text + 10 resilience = 27 total
    ExampleLib.allSignatures should have size 27
  }

  it should "have unique function names" in {
    val names = ExampleLib.allSignatures.map(_.name)
    names.distinct should have size names.size
  }

  it should "have matching function and module names" in {
    ExampleLib.allSignatures.foreach { sig =>
      sig.moduleName shouldBe sig.name
    }
  }

  // ========== Specific Signature Type Tests ==========

  "SumList signature" should "have correct types" in {
    val sig = ExampleLib.dataSignatures.find(_.name == "SumList").get

    sig.params should have size 1
    sig.params.head shouldBe ("numbers" -> SemanticType.SList(SemanticType.SInt))
    sig.returns shouldBe SemanticType.SInt
  }

  "Average signature" should "have correct types" in {
    val sig = ExampleLib.dataSignatures.find(_.name == "Average").get

    sig.params should have size 1
    sig.params.head shouldBe ("numbers" -> SemanticType.SList(SemanticType.SInt))
    sig.returns shouldBe SemanticType.SFloat
  }

  "FilterGreaterThan signature" should "have correct types" in {
    val sig = ExampleLib.dataSignatures.find(_.name == "FilterGreaterThan").get

    sig.params should have size 2
    sig.params(0) shouldBe ("numbers"   -> SemanticType.SList(SemanticType.SInt))
    sig.params(1) shouldBe ("threshold" -> SemanticType.SInt)
    sig.returns shouldBe SemanticType.SList(SemanticType.SInt)
  }

  "Uppercase signature" should "have correct types" in {
    val sig = ExampleLib.textSignatures.find(_.name == "Uppercase").get

    sig.params should have size 1
    sig.params.head shouldBe ("text" -> SemanticType.SString)
    sig.returns shouldBe SemanticType.SString
  }

  "WordCount signature" should "have correct types" in {
    val sig = ExampleLib.textSignatures.find(_.name == "WordCount").get

    sig.params should have size 1
    sig.params.head shouldBe ("text" -> SemanticType.SString)
    sig.returns shouldBe SemanticType.SInt
  }

  "Contains signature" should "have correct types" in {
    val sig = ExampleLib.textSignatures.find(_.name == "Contains").get

    sig.params should have size 2
    sig.params(0) shouldBe ("text"      -> SemanticType.SString)
    sig.params(1) shouldBe ("substring" -> SemanticType.SString)
    sig.returns shouldBe SemanticType.SBoolean
  }

  "Split signature" should "have correct types" in {
    val sig = ExampleLib.textSignatures.find(_.name == "Split").get

    sig.params should have size 2
    sig.params(0) shouldBe ("text"      -> SemanticType.SString)
    sig.params(1) shouldBe ("delimiter" -> SemanticType.SString)
    sig.returns shouldBe SemanticType.SList(SemanticType.SString)
  }

  // ========== Compiler Registration Tests ==========

  "ExampleLib.registerAll" should "register all functions with a compiler builder" in {
    val builder  = ExampleLib.registerAll(LangCompilerBuilder())
    val compiler = builder.build
    val registry = compiler.functionRegistry

    // All functions should be registered
    ExampleLib.allSignatures.foreach { sig =>
      registry.lookup(sig.name).isDefined shouldBe true
    }
  }

  "ExampleLib.compiler" should "create a working compiler" in {
    val compiler = ExampleLib.compiler
    compiler should not be null
    compiler.functionRegistry should not be null
  }

  it should "have all ExampleLib functions registered" in {
    val registry = ExampleLib.compiler.functionRegistry

    // Data functions
    registry.lookup("SumList").isDefined shouldBe true
    registry.lookup("Average").isDefined shouldBe true
    registry.lookup("Max").isDefined shouldBe true
    registry.lookup("Min").isDefined shouldBe true
    registry.lookup("FilterGreaterThan").isDefined shouldBe true
    registry.lookup("MultiplyEach").isDefined shouldBe true
    registry.lookup("Range").isDefined shouldBe true
    registry.lookup("FormatNumber").isDefined shouldBe true

    // Text functions
    registry.lookup("Uppercase").isDefined shouldBe true
    registry.lookup("Lowercase").isDefined shouldBe true
    registry.lookup("Trim").isDefined shouldBe true
    registry.lookup("Replace").isDefined shouldBe true
    registry.lookup("WordCount").isDefined shouldBe true
    registry.lookup("TextLength").isDefined shouldBe true
    registry.lookup("Contains").isDefined shouldBe true
    registry.lookup("SplitLines").isDefined shouldBe true
    registry.lookup("Split").isDefined shouldBe true

    // Resilience functions
    registry.lookup("SlowQuery").isDefined shouldBe true
    registry.lookup("SlowApiCall").isDefined shouldBe true
    registry.lookup("ExpensiveCompute").isDefined shouldBe true
    registry.lookup("FlakyService").isDefined shouldBe true
    registry.lookup("TimeoutProneService").isDefined shouldBe true
    registry.lookup("RateLimitedApi").isDefined shouldBe true
    registry.lookup("ResourceIntensiveTask").isDefined shouldBe true
    registry.lookup("QuickCheck").isDefined shouldBe true
    registry.lookup("DeepAnalysis").isDefined shouldBe true
    registry.lookup("AlwaysFailsService").isDefined shouldBe true
  }

  it should "also include StdLib functions" in {
    val registry = ExampleLib.compiler.functionRegistry

    // StdLib math functions
    registry.lookup("add").isDefined shouldBe true
    registry.lookup("subtract").isDefined shouldBe true

    // StdLib string functions
    registry.lookup("concat").isDefined shouldBe true
    registry.lookup("trim").isDefined shouldBe true

    // StdLib boolean functions
    registry.lookup("and").isDefined shouldBe true
    registry.lookup("or").isDefined shouldBe true
  }

  // ========== Compilation Tests ==========

  "ExampleLib.compiler" should "compile programs using data functions" in {
    val source = """
      in numbers: List<Int>
      total = SumList(numbers)
      avg = Average(numbers)
      out total
      out avg
    """

    val result = ExampleLib.compiler.compile(source, "data-test")
    result.isRight shouldBe true
  }

  it should "compile programs using text functions" in {
    val source = """
      in text: String
      upper = Uppercase(text)
      len = TextLength(text)
      words = WordCount(text)
      out upper
      out len
      out words
    """

    val result = ExampleLib.compiler.compile(source, "text-test")
    result.isRight shouldBe true
  }

  it should "compile programs mixing data and text functions" in {
    val source = """
      in numbers: List<Int>
      in label: String
      total = SumList(numbers)
      formatted = FormatNumber(total)
      upperLabel = Uppercase(label)
      out formatted
      out upperLabel
    """

    val result = ExampleLib.compiler.compile(source, "mixed-test")
    result.isRight shouldBe true
  }

  it should "compile programs using stdlib and example functions together" in {
    val source = """
      in a: Int
      in b: Int
      in text: String
      sum = add(a, b)
      formatted = FormatNumber(sum)
      upperText = Uppercase(text)
      out formatted
      out upperText
    """

    val result = ExampleLib.compiler.compile(source, "combined-test")
    result.isRight shouldBe true
  }

  it should "produce synthetic modules for execution" in {
    val source = """
      in numbers: List<Int>
      total = SumList(numbers)
      out total
    """

    val result = ExampleLib.compiler.compile(source, "synth-test")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.pipeline.syntheticModules should not be empty
  }

  it should "compile programs using resilience functions" in {
    val source = """
      in query: String
      result = SlowQuery(query)
      out result
    """

    val result = ExampleLib.compiler.compile(source, "resilience-test")
    result.isRight shouldBe true
  }

  it should "compile programs using resilience functions with module call options" in {
    val source = """
      in query: String
      in fallbackValue: String
      result = SlowQuery(query) with cache: 5min, retry: 3, timeout: 2s
      safeResult = FlakyService(query) with retry: 3, delay: 100ms, backoff: exponential, fallback: fallbackValue
      out result
      out safeResult
    """

    val result = ExampleLib.compiler.compile(source, "resilience-options-test")
    result.isRight shouldBe true
  }

  // ========== Error Handling Tests ==========

  "ExampleLib.compiler" should "reject unknown functions" in {
    val source = """
      in x: Int
      result = UnknownFunction(x)
      out result
    """

    val result = ExampleLib.compiler.compile(source, "error-test")
    result.isLeft shouldBe true
  }

  it should "reject type mismatches" in {
    val source = """
      in text: String
      result = SumList(text)
      out result
    """

    val result = ExampleLib.compiler.compile(source, "type-error-test")
    result.isLeft shouldBe true
  }

  it should "reject wrong number of arguments" in {
    val source = """
      in numbers: List<Int>
      result = SumList(numbers, 10)
      out result
    """

    val result = ExampleLib.compiler.compile(source, "args-error-test")
    result.isLeft shouldBe true
  }

  // ========== Module Consistency Tests ==========

  "DataModules and ExampleLib" should "have consistent module counts" in {
    val dataModuleCount    = DataModules.all.size
    val dataSignatureCount = ExampleLib.dataSignatures.size

    dataModuleCount shouldBe dataSignatureCount
  }

  "TextModules and ExampleLib" should "have consistent module counts" in {
    val textModuleCount    = TextModules.all.size
    val textSignatureCount = ExampleLib.textSignatures.size

    textModuleCount shouldBe textSignatureCount
  }

  "ResilienceModules and ExampleLib" should "have consistent module counts" in {
    val resilienceModuleCount    = ResilienceModules.all.size
    val resilienceSignatureCount = ExampleLib.resilienceSignatures.size

    resilienceModuleCount shouldBe resilienceSignatureCount
  }

  "All modules" should "have corresponding signatures" in {
    val moduleNames    = ExampleLib.allModules.keys.toSet
    val signatureNames = ExampleLib.allSignatures.map(_.name).toSet

    moduleNames shouldBe signatureNames
  }
}
