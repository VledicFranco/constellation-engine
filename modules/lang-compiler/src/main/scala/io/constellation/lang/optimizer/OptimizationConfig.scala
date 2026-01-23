package io.constellation.lang.optimizer

/** Configuration for IR optimization passes.
  *
  * @param enableDCE Enable dead code elimination
  * @param enableConstantFolding Enable constant folding
  * @param enableCSE Enable common subexpression elimination
  * @param maxIterations Maximum number of optimization iterations (for iterative optimization)
  */
final case class OptimizationConfig(
    enableDCE: Boolean = true,
    enableConstantFolding: Boolean = true,
    enableCSE: Boolean = true,
    maxIterations: Int = 3
) {

  /** Returns the list of enabled pass names */
  def enabledPassNames: List[String] = List(
    if (enableDCE) Some("dead-code-elimination") else None,
    if (enableConstantFolding) Some("constant-folding") else None,
    if (enableCSE) Some("common-subexpression-elimination") else None
  ).flatten

  /** Check if any optimization is enabled */
  def hasOptimizationsEnabled: Boolean =
    enableDCE || enableConstantFolding || enableCSE
}

object OptimizationConfig {

  /** Default configuration with all optimizations enabled */
  val default: OptimizationConfig = OptimizationConfig()

  /** No optimizations enabled */
  val none: OptimizationConfig = OptimizationConfig(
    enableDCE = false,
    enableConstantFolding = false,
    enableCSE = false
  )

  /** Aggressive optimization with more iterations */
  val aggressive: OptimizationConfig = OptimizationConfig(
    maxIterations = 10
  )

  /** Only dead code elimination */
  val dceOnly: OptimizationConfig = OptimizationConfig(
    enableDCE = true,
    enableConstantFolding = false,
    enableCSE = false
  )

  /** Only constant folding */
  val constantFoldingOnly: OptimizationConfig = OptimizationConfig(
    enableDCE = false,
    enableConstantFolding = true,
    enableCSE = false
  )

  /** Only common subexpression elimination */
  val cseOnly: OptimizationConfig = OptimizationConfig(
    enableDCE = false,
    enableConstantFolding = false,
    enableCSE = true
  )
}
