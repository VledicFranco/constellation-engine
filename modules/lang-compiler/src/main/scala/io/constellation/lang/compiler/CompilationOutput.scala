package io.constellation.lang.compiler

import io.constellation.LoadedProgram
import io.constellation.lang.ast.CompileWarning

/** Result of compiling a constellation-lang program.
  *
  * Wraps a [[LoadedProgram]] (which contains the [[io.constellation.ProgramImage]] and synthetic
  * modules) together with any compiler warnings.
  *
  * @param program
  *   The loaded program ready for execution
  * @param warnings
  *   Compiler warnings (e.g., unused variables, deprecations)
  */
final case class CompilationOutput(
    program: LoadedProgram,
    warnings: List[CompileWarning]
)
