package io.constellation.stdlib

import cats.effect.IO
import io.constellation.*
import io.constellation.lang.compiler.CompileResult
import io.constellation.lang.{LangCompiler, LangCompilerBuilder}
import io.constellation.lang.semantic.*
import io.constellation.stdlib.categories.*

import java.util.UUID

/** Standard library of modules for constellation-lang.
  *
  * These modules provide common operations for ML pipeline orchestration. The implementation is
  * split across category files in the `categories/` directory for maintainability.
  *
  * Categories:
  *   - MathFunctions: add, subtract, multiply, divide, max, min
  *   - StringFunctions: concat, upper, lower, string-length
  *   - ListFunctions: list-length, list-first, list-last, list-is-empty
  *   - BooleanFunctions: and, or, not
  *   - ComparisonFunctions: eq-int, eq-string, gt, lt, gte, lte
  *   - UtilityFunctions: identity, const-*, log
  *   - HigherOrderFunctions: filter, map, all, any (lambda-based)
  */
object StdLib
    extends MathFunctions
    with StringFunctions
    with ListFunctions
    with BooleanFunctions
    with ComparisonFunctions
    with UtilityFunctions
    with HigherOrderFunctions
    with RecordFunctions {

  /** Register all standard library functions with a LangCompiler builder */
  def registerAll(builder: LangCompilerBuilder): LangCompilerBuilder = {
    val allSigs = mathSignatures ++
      stringSignatures ++
      listSignatures ++
      booleanSignatures ++
      comparisonSignatures ++
      utilitySignatures ++
      hofSignatures ++
      recordSignatures

    allSigs.foldLeft(builder)((b, sig) => b.withFunction(sig))
  }

  /** Get all standard library modules */
  def allModules: Map[String, Module.Uninitialized] =
    mathModules ++
      stringModules ++
      listModules ++
      booleanModules ++
      comparisonModules ++
      utilityModules ++
      recordModules

  /** Get all standard library function signatures */
  def allSignatures: List[FunctionSignature] =
    mathSignatures ++
      stringSignatures ++
      listSignatures ++
      booleanSignatures ++
      comparisonSignatures ++
      utilitySignatures ++
      hofSignatures ++
      recordSignatures

  /** Create a LangCompiler with all standard library functions registered */
  def compiler: LangCompiler = {
    val builder = registerAll(LangCompilerBuilder()).withModules(allModules)
    new StdLibCompiler(builder.build, allModules)
  }

  /** Compiler wrapper that includes stdlib modules in results */
  private class StdLibCompiler(
      underlying: LangCompiler,
      stdModules: Map[String, Module.Uninitialized]
  ) extends LangCompiler {
    def functionRegistry: FunctionRegistry = underlying.functionRegistry

    def compile(source: String, dagName: String) =
      underlying.compile(source, dagName).map { result =>
        // Include stdlib modules that are referenced by the DAG
        // Match module specs in the DAG to stdlib modules by name
        val neededStdModules: Map[UUID, Module.Uninitialized] =
          result.dagSpec.modules.flatMap { case (moduleId, spec) =>
            stdModules
              .find { case (name, _) => spec.name.contains(name) }
              .map { case (_, module) => moduleId -> module }
          }
        result.copy(syntheticModules = result.syntheticModules ++ neededStdModules)
      }
  }
}
