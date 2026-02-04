package io.constellation.lang.compiler

import io.constellation.LoadedPipeline
import io.constellation.lang.ast.CompileWarning

/** Result of compiling a constellation-lang pipeline.
  *
  * Wraps a [[LoadedPipeline]] (which contains the [[io.constellation.PipelineImage]] and synthetic
  * modules) together with any compiler warnings.
  *
  * @param pipeline
  *   The loaded pipeline ready for execution
  * @param warnings
  *   Compiler warnings (e.g., unused variables, deprecations)
  */
final case class CompilationOutput(
    pipeline: LoadedPipeline,
    warnings: List[CompileWarning]
)
