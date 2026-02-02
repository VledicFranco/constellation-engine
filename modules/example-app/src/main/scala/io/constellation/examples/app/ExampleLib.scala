package io.constellation.examples.app

import io.constellation.*
import io.constellation.lang.{LangCompiler, LangCompilerBuilder}
import io.constellation.lang.semantic.*
import io.constellation.stdlib.StdLib
import io.constellation.examples.app.modules.{DataModules, ResilienceModules, TextModules}

import java.util.UUID

/** ExampleLib - Complete function library for example applications.
  *
  * This library combines the standard library (StdLib) with custom example modules (DataModules,
  * TextModules) to provide a full-featured compiler for demonstration and testing purposes.
  *
  * ==Available Functions==
  *
  * '''Data Processing (from DataModules):'''
  *   - `SumList(numbers: List<Int>) -> Int` - Sum of list elements
  *   - `Average(numbers: List<Int>) -> Float` - Arithmetic mean
  *   - `Max(numbers: List<Int>) -> Int` - Maximum value
  *   - `Min(numbers: List<Int>) -> Int` - Minimum value
  *   - `FilterGreaterThan(numbers: List<Int>, threshold: Int) -> List<Int>`
  *   - `MultiplyEach(numbers: List<Int>, multiplier: Int) -> List<Int>`
  *   - `Range(start: Int, end: Int) -> List<Int>` - Generate number sequence
  *   - `FormatNumber(number: Int) -> String` - Format with commas
  *
  * '''Text Processing (from TextModules):'''
  *   - `Uppercase(text: String) -> String` - Convert to uppercase
  *   - `Lowercase(text: String) -> String` - Convert to lowercase
  *   - `Trim(text: String) -> String` - Remove whitespace
  *   - `Replace(text: String, find: String, replace: String) -> String`
  *   - `WordCount(text: String) -> Int` - Count words
  *   - `TextLength(text: String) -> Int` - Character count
  *   - `Contains(text: String, substring: String) -> Boolean`
  *   - `SplitLines(text: String) -> List<String>` - Split by newline
  *   - `Split(text: String, delimiter: String) -> List<String>`
  *
  * ==Usage==
  *
  * {{{
  * // Get a compiler with all example functions
  * val compiler = ExampleLib.compiler
  *
  * // Compile a constellation-lang program
  * compiler.compile("""
  *   in numbers: List<Int>
  *   total = SumList(numbers)
  *   out total
  * """, "my-pipeline")
  * }}}
  *
  * @see
  *   [[io.constellation.stdlib.StdLib]] for standard library functions
  * @see
  *   [[io.constellation.examples.app.modules.DataModules]] for data modules
  * @see
  *   [[io.constellation.examples.app.modules.TextModules]] for text modules
  */

/** Example library combining StdLib with custom DataModules and TextModules.
  *
  * Registers all function signatures with the compiler for type-checking and provides module
  * implementations for runtime execution.
  */
object ExampleLib {

  // ========== Data Module Signatures ==========

  private val sumListSig = FunctionSignature(
    name = "SumList",
    params = List("numbers" -> SemanticType.SList(SemanticType.SInt)),
    returns = SemanticType.SInt,
    moduleName = "SumList"
  )

  private val averageSig = FunctionSignature(
    name = "Average",
    params = List("numbers" -> SemanticType.SList(SemanticType.SInt)),
    returns = SemanticType.SFloat,
    moduleName = "Average"
  )

  private val maxSig = FunctionSignature(
    name = "Max",
    params = List("numbers" -> SemanticType.SList(SemanticType.SInt)),
    returns = SemanticType.SInt,
    moduleName = "Max"
  )

  private val minSig = FunctionSignature(
    name = "Min",
    params = List("numbers" -> SemanticType.SList(SemanticType.SInt)),
    returns = SemanticType.SInt,
    moduleName = "Min"
  )

  private val filterGreaterThanSig = FunctionSignature(
    name = "FilterGreaterThan",
    params = List(
      "numbers"   -> SemanticType.SList(SemanticType.SInt),
      "threshold" -> SemanticType.SInt
    ),
    returns = SemanticType.SList(SemanticType.SInt),
    moduleName = "FilterGreaterThan"
  )

  private val multiplyEachSig = FunctionSignature(
    name = "MultiplyEach",
    params = List(
      "numbers"    -> SemanticType.SList(SemanticType.SInt),
      "multiplier" -> SemanticType.SInt
    ),
    returns = SemanticType.SList(SemanticType.SInt),
    moduleName = "MultiplyEach"
  )

  private val rangeSig = FunctionSignature(
    name = "Range",
    params = List(
      "start" -> SemanticType.SInt,
      "end"   -> SemanticType.SInt
    ),
    returns = SemanticType.SList(SemanticType.SInt),
    moduleName = "Range"
  )

  private val formatNumberSig = FunctionSignature(
    name = "FormatNumber",
    params = List("number" -> SemanticType.SInt),
    returns = SemanticType.SString,
    moduleName = "FormatNumber"
  )

  // ========== Text Module Signatures ==========

  private val uppercaseSig = FunctionSignature(
    name = "Uppercase",
    params = List("text" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "Uppercase"
  )

  private val lowercaseSig = FunctionSignature(
    name = "Lowercase",
    params = List("text" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "Lowercase"
  )

  private val trimSig = FunctionSignature(
    name = "Trim",
    params = List("text" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "Trim"
  )

  private val replaceSig = FunctionSignature(
    name = "Replace",
    params = List(
      "text"    -> SemanticType.SString,
      "find"    -> SemanticType.SString,
      "replace" -> SemanticType.SString
    ),
    returns = SemanticType.SString,
    moduleName = "Replace"
  )

  private val wordCountSig = FunctionSignature(
    name = "WordCount",
    params = List("text" -> SemanticType.SString),
    returns = SemanticType.SInt,
    moduleName = "WordCount"
  )

  private val textLengthSig = FunctionSignature(
    name = "TextLength",
    params = List("text" -> SemanticType.SString),
    returns = SemanticType.SInt,
    moduleName = "TextLength"
  )

  private val containsSig = FunctionSignature(
    name = "Contains",
    params = List(
      "text"      -> SemanticType.SString,
      "substring" -> SemanticType.SString
    ),
    returns = SemanticType.SBoolean,
    moduleName = "Contains"
  )

  private val splitLinesSig = FunctionSignature(
    name = "SplitLines",
    params = List("text" -> SemanticType.SString),
    returns = SemanticType.SList(SemanticType.SString),
    moduleName = "SplitLines"
  )

  private val splitSig = FunctionSignature(
    name = "Split",
    params = List(
      "text"      -> SemanticType.SString,
      "delimiter" -> SemanticType.SString
    ),
    returns = SemanticType.SList(SemanticType.SString),
    moduleName = "Split"
  )

  // ========== Resilience Module Signatures ==========

  private val slowQuerySig = FunctionSignature(
    name = "SlowQuery",
    params = List("query" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "SlowQuery"
  )

  private val slowApiCallSig = FunctionSignature(
    name = "SlowApiCall",
    params = List("endpoint" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "SlowApiCall"
  )

  private val expensiveComputeSig = FunctionSignature(
    name = "ExpensiveCompute",
    params = List("data" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "ExpensiveCompute"
  )

  private val flakyServiceSig = FunctionSignature(
    name = "FlakyService",
    params = List("request" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "FlakyService"
  )

  private val timeoutProneServiceSig = FunctionSignature(
    name = "TimeoutProneService",
    params = List("request" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "TimeoutProneService"
  )

  private val rateLimitedApiSig = FunctionSignature(
    name = "RateLimitedApi",
    params = List("request" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "RateLimitedApi"
  )

  private val resourceIntensiveTaskSig = FunctionSignature(
    name = "ResourceIntensiveTask",
    params = List("task" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "ResourceIntensiveTask"
  )

  private val quickCheckSig = FunctionSignature(
    name = "QuickCheck",
    params = List("data" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "QuickCheck"
  )

  private val deepAnalysisSig = FunctionSignature(
    name = "DeepAnalysis",
    params = List("data" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "DeepAnalysis"
  )

  private val alwaysFailsServiceSig = FunctionSignature(
    name = "AlwaysFailsService",
    params = List("request" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "AlwaysFailsService"
  )

  // ========== All Signatures ==========

  val dataSignatures: List[FunctionSignature] = List(
    sumListSig,
    averageSig,
    maxSig,
    minSig,
    filterGreaterThanSig,
    multiplyEachSig,
    rangeSig,
    formatNumberSig
  )

  val textSignatures: List[FunctionSignature] = List(
    uppercaseSig,
    lowercaseSig,
    trimSig,
    replaceSig,
    wordCountSig,
    textLengthSig,
    containsSig,
    splitLinesSig,
    splitSig
  )

  val resilienceSignatures: List[FunctionSignature] = List(
    slowQuerySig,
    slowApiCallSig,
    expensiveComputeSig,
    flakyServiceSig,
    timeoutProneServiceSig,
    rateLimitedApiSig,
    resourceIntensiveTaskSig,
    quickCheckSig,
    deepAnalysisSig,
    alwaysFailsServiceSig
  )

  val allSignatures: List[FunctionSignature] =
    dataSignatures ++ textSignatures ++ resilienceSignatures

  // ========== Module Mappings ==========

  /** All example modules mapped by name */
  def allModules: Map[String, Module.Uninitialized] = {
    val dataModuleMap       = DataModules.all.map(m => m.spec.name -> m).toMap
    val textModuleMap       = TextModules.all.map(m => m.spec.name -> m).toMap
    val resilienceModuleMap = ResilienceModules.all.map(m => m.spec.name -> m).toMap
    dataModuleMap ++ textModuleMap ++ resilienceModuleMap
  }

  /** Register all example functions with a LangCompiler builder */
  def registerAll(builder: LangCompilerBuilder): LangCompilerBuilder =
    allSignatures.foldLeft(builder) { (b, sig) =>
      b.withFunction(sig)
    }

  /** Create a compiler with StdLib + ExampleLib functions */
  def compiler: LangCompiler = {
    // Start with StdLib, then add ExampleLib signatures, then add modules
    val combinedModules = StdLib.allModules ++ allModules
    val builder = registerAll(StdLib.registerAll(LangCompilerBuilder()))
      .withModules(combinedModules) // Make modules available to DagCompiler
    builder.build
  }

  /** Compiler wrapper that includes both stdlib and example modules */
  private class ExampleLibCompiler(
      underlying: LangCompiler,
      modules: Map[String, Module.Uninitialized]
  ) extends LangCompiler {
    def functionRegistry: FunctionRegistry = underlying.functionRegistry

    def compile(source: String, dagName: String) =
      underlying.compile(source, dagName).map { result =>
        // Include modules that are referenced by the DAG
        val neededModules: Map[UUID, Module.Uninitialized] =
          result.program.image.dagSpec.modules.flatMap { case (moduleId, spec) =>
            modules
              .find { case (name, _) => spec.name.contains(name) }
              .map { case (_, module) => moduleId -> module }
          }
        val updatedProgram = result.program.copy(
          syntheticModules = result.program.syntheticModules ++ neededModules
        )
        result.copy(program = updatedProgram)
      }

    def compileToIR(source: String, dagName: String) =
      underlying.compileToIR(source, dagName)
  }
}
