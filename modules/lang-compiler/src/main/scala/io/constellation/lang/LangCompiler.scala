package io.constellation.lang

import cats.effect.IO
import io.constellation.*
import io.constellation.lang.ast.CompileError
import io.constellation.lang.compiler.*
import io.constellation.lang.optimizer.{IROptimizer, OptimizationConfig}
import io.constellation.lang.parser.ConstellationParser
import io.constellation.lang.semantic.*

import java.time.Instant
import java.util.UUID

/** Main interface for compiling constellation-lang programs */
trait LangCompiler {

  /** Compile a constellation-lang source to a CompilationOutput (LoadedProgram + warnings). */
  def compile(source: String, dagName: String): Either[List[CompileError], CompilationOutput]

  /** Async variant of compile that avoids blocking threads.
    *
    * Override in subclasses that use IO-based caching. The default
    * implementation wraps the synchronous `compile` in IO.
    */
  def compileIO(source: String, dagName: String): IO[Either[List[CompileError], CompilationOutput]] =
    IO(compile(source, dagName))

  /** Compile to IR only (for visualization) */
  def compileToIR(source: String, dagName: String): Either[List[CompileError], IRProgram]

  /** Get the function registry for namespace/function introspection */
  def functionRegistry: FunctionRegistry
}

object LangCompiler {

  /** Create a new LangCompiler with the given function registry and module map.
    *
    * Note: Optimization is disabled by default for backward compatibility.
    * Use the builder with `.withOptimization()` to enable IR optimization.
    */
  def apply(
      registry: FunctionRegistry,
      modules: Map[String, Module.Uninitialized],
      optimizationConfig: OptimizationConfig = OptimizationConfig.none
  ): LangCompiler = new LangCompilerImpl(registry, modules, optimizationConfig)

  /** Create an empty LangCompiler (no registered functions or modules) */
  def empty: LangCompiler =
    new LangCompilerImpl(FunctionRegistry.empty, Map.empty, OptimizationConfig.none)

  /** Builder for constructing a LangCompiler */
  def builder: LangCompilerBuilder = LangCompilerBuilder()
}

/** Builder for LangCompiler with fluent API.
  *
  * Optimization is disabled by default. Use `.withOptimization()` to enable.
  */
final case class LangCompilerBuilder(
    private val registry: FunctionRegistry = FunctionRegistry.empty,
    private val modules: Map[String, Module.Uninitialized] = Map.empty,
    private val cacheConfig: Option[CompilationCache.Config] = None,
    private val optimizationConfig: OptimizationConfig = OptimizationConfig.none
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

  /** Enable IR optimization with the given configuration */
  def withOptimization(config: OptimizationConfig = OptimizationConfig.default): LangCompilerBuilder =
    copy(optimizationConfig = config)

  /** Disable IR optimization */
  def withoutOptimization: LangCompilerBuilder =
    copy(optimizationConfig = OptimizationConfig.none)

  /** Build the LangCompiler, optionally wrapped with caching */
  def build: LangCompiler = {
    val base = new LangCompilerImpl(registry, modules, optimizationConfig)
    cacheConfig match {
      case Some(config) => CachingLangCompiler.withConfig(base, config)
      case None         => base
    }
  }
}

/** Implementation of LangCompiler */
private class LangCompilerImpl(
    registry: FunctionRegistry,
    modules: Map[String, Module.Uninitialized],
    optimizationConfig: OptimizationConfig = OptimizationConfig.none
) extends LangCompiler {

  def functionRegistry: FunctionRegistry = registry

  def compile(source: String, dagName: String): Either[List[CompileError], CompilationOutput] =
    for {
      // Phase 1: Parse
      program <- ConstellationParser.parse(source).left.map(List(_))

      // Phase 2: Type check
      typedProgram <- TypeChecker.check(program, registry)

      // Phase 3: Generate IR
      irProgram = IRGenerator.generate(typedProgram)

      // Phase 4: Optimize IR
      optimizedIR = IROptimizer.optimizeIR(irProgram, optimizationConfig)

      // Phase 5: Compile to DagSpec
      result <- DagCompiler.compile(optimizedIR, dagName, modules).left.map { err =>
        List(CompileError.InternalError(err.message))
      }
    } yield {
      // Build CompilationOutput from DagCompileOutput
      val sourceHash = ContentHash.computeSHA256(source.getBytes("UTF-8"))
      val structuralHash = ProgramImage.computeStructuralHash(result.dagSpec)
      val moduleOptions = result.moduleOptions.map { case (uuid, irOpts) =>
        uuid -> irOpts.toModuleCallOptions
      }

      val image = ProgramImage(
        structuralHash = structuralHash,
        syntacticHash = sourceHash,
        dagSpec = result.dagSpec,
        moduleOptions = moduleOptions,
        compiledAt = Instant.now(),
        sourceHash = Some(sourceHash)
      )

      val loaded = LoadedProgram(image, result.syntheticModules)
      CompilationOutput(loaded, typedProgram.warnings)
    }

  def compileToIR(source: String, dagName: String): Either[List[CompileError], IRProgram] =
    for {
      // Phase 1: Parse
      program <- ConstellationParser.parse(source).left.map(List(_))

      // Phase 2: Type check
      typedProgram <- TypeChecker.check(program, registry)

      // Phase 3: Generate IR
      irProgram = IRGenerator.generate(typedProgram)

      // Phase 4: Optimize IR
      optimizedIR = IROptimizer.optimizeIR(irProgram, optimizationConfig)
    } yield optimizedIR
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
