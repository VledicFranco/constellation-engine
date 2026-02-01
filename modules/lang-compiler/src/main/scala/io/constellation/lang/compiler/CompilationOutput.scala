package io.constellation.lang.compiler

import io.constellation.{LoadedProgram, ModuleCallOptions, Module}
import io.constellation.lang.ast.CompileWarning

import java.util.UUID

/** Result of compiling a constellation-lang program.
  *
  * Wraps a [[LoadedProgram]] (which contains the [[io.constellation.ProgramImage]]
  * and synthetic modules) together with any compiler warnings.
  *
  * Deprecated accessors are provided for backward compatibility with code that
  * previously used [[CompileResult]].
  *
  * @param program  The loaded program ready for execution
  * @param warnings Compiler warnings (e.g., unused variables, deprecations)
  */
final case class CompilationOutput(
    program: LoadedProgram,
    warnings: List[CompileWarning]
) {

  @deprecated("Use program.image.dagSpec", "0.3.0")
  def dagSpec: io.constellation.DagSpec = program.image.dagSpec

  @deprecated("Use program.syntheticModules", "0.3.0")
  def syntheticModules: Map[UUID, Module.Uninitialized] = program.syntheticModules

  @deprecated("Use program.image.moduleOptions", "0.3.0")
  def moduleOptions: Map[UUID, ModuleCallOptions] = program.image.moduleOptions
}
