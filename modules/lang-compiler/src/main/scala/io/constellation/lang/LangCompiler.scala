package io.constellation.lang

import cats.effect.IO
import io.constellation.*
import io.constellation.lang.ast.CompileError
import io.constellation.lang.compiler.*
import io.constellation.lang.parser.ConstellationParser
import io.constellation.lang.semantic.*

import java.util.UUID

/** Main interface for compiling constellation-lang programs */
trait LangCompiler {

  /** Compile a constellation-lang source to a DagSpec and synthetic modules */
  def compile(source: String, dagName: String): Either[List[CompileError], CompileResult]

  /** Get the function registry for namespace/function introspection */
  def functionRegistry: FunctionRegistry
}

object LangCompiler {

  /** Create a new LangCompiler with the given function registry and module map */
  def apply(
      registry: FunctionRegistry,
      modules: Map[String, Module.Uninitialized]
  ): LangCompiler = new LangCompilerImpl(registry, modules)

  /** Create an empty LangCompiler (no registered functions or modules) */
  def empty: LangCompiler = new LangCompilerImpl(FunctionRegistry.empty, Map.empty)

  /** Builder for constructing a LangCompiler */
  def builder: LangCompilerBuilder = LangCompilerBuilder()
}

/** Builder for LangCompiler with fluent API */
final case class LangCompilerBuilder(
    private val registry: FunctionRegistry = FunctionRegistry.empty,
    private val modules: Map[String, Module.Uninitialized] = Map.empty,
    private val cacheConfig: Option[CompilationCache.Config] = None
) {

  /** Register a function signature for type checking */
  def withFunction(sig: FunctionSignature): LangCompilerBuilder = {
    registry.register(sig)
    this
  }

  /** Register a module with its signature */
  def withModule(
      languageName: String,
      module: Module.Uninitialized,
      params: List[(String, SemanticType)],
      returns: SemanticType
  ): LangCompilerBuilder = {
    val sig = FunctionSignature(
      name = languageName,
      params = params,
      returns = returns,
      moduleName = module.spec.name
    )
    registry.register(sig)
    copy(modules = modules + (module.spec.name -> module))
  }

  /** Register multiple modules for DagCompiler to access at compile time. This is separate from
    * signatures - use withFunction for type checking, and withModules to make the actual
    * Module.Uninitialized available.
    */
  def withModules(newModules: Map[String, Module.Uninitialized]): LangCompilerBuilder =
    copy(modules = modules ++ newModules)

  /** Enable compilation caching with the given configuration */
  def withCaching(config: CompilationCache.Config = CompilationCache.Config()): LangCompilerBuilder =
    copy(cacheConfig = Some(config))

  /** Disable compilation caching */
  def withoutCaching: LangCompilerBuilder =
    copy(cacheConfig = None)

  /** Build the LangCompiler, optionally wrapped with caching */
  def build: LangCompiler = {
    val base = new LangCompilerImpl(registry, modules)
    cacheConfig match {
      case Some(config) => CachingLangCompiler.withConfig(base, config)
      case None         => base
    }
  }
}

/** Implementation of LangCompiler */
private class LangCompilerImpl(
    registry: FunctionRegistry,
    modules: Map[String, Module.Uninitialized]
) extends LangCompiler {

  def functionRegistry: FunctionRegistry = registry

  def compile(source: String, dagName: String): Either[List[CompileError], CompileResult] =
    for {
      // Phase 1: Parse
      program <- ConstellationParser.parse(source).left.map(List(_))

      // Phase 2: Type check
      typedProgram <- TypeChecker.check(program, registry)

      // Phase 3: Generate IR
      irProgram = IRGenerator.generate(typedProgram)

      // Phase 4: Compile to DagSpec
      result <- DagCompiler.compile(irProgram, dagName, modules).left.map { err =>
        List(CompileError.InternalError(err.message))
      }
    } yield result
}

/** Utilities for registering modules with the compiler */
object ModuleBridge {

  /** Create a function signature from a module spec */
  def signatureFromModule(
      languageName: String,
      module: Module.Uninitialized,
      params: List[(String, SemanticType)],
      returns: SemanticType
  ): FunctionSignature =
    FunctionSignature(
      name = languageName,
      params = params,
      returns = returns,
      moduleName = module.spec.name
    )

  /** Convert CType to SemanticType for module integration */
  def ctypeToSemanticType(ctype: CType): SemanticType =
    SemanticType.fromCType(ctype)

  /** Extract input parameter types from a module spec */
  def extractParams(module: Module.Uninitialized): List[(String, SemanticType)] =
    module.spec.consumes.map { case (name, ctype) =>
      name -> SemanticType.fromCType(ctype)
    }.toList

  /** Extract output type from a module spec (assumes single output named "out") */
  def extractReturns(module: Module.Uninitialized): SemanticType =
    module.spec.produces.get("out") match {
      case Some(ctype) => SemanticType.fromCType(ctype)
      case None        =>
        // If no "out", create a record from all produces
        SemanticType.SRecord(
          module.spec.produces.map { case (name, ctype) =>
            name -> SemanticType.fromCType(ctype)
          }
        )
    }
}
