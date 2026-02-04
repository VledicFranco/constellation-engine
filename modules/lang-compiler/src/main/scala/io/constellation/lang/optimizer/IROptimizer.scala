package io.constellation.lang.optimizer

import io.constellation.lang.compiler.{IRNode, IRPipeline}

/** IR Optimizer that orchestrates optimization passes.
  *
  * Applies a sequence of optimization passes iteratively until a fixpoint is reached or the maximum
  * number of iterations is exceeded.
  */
object IROptimizer {

  /** Optimize an IR pipeline using the given configuration.
    *
    * @param ir
    *   The IR pipeline to optimize
    * @param config
    *   The optimization configuration
    * @return
    *   The optimized IR pipeline and statistics
    */
  def optimize(
      ir: IRPipeline,
      config: OptimizationConfig = OptimizationConfig.default
  ): OptimizationResult = {
    if !config.hasOptimizationsEnabled then {
      return OptimizationResult(ir, OptimizationStats.empty, iterations = 0)
    }

    val passes      = buildPassList(config)
    val beforeStats = analyze(ir)

    var current   = ir
    var iteration = 0
    var changed   = true

    // Iterative optimization until fixpoint or max iterations
    while changed && iteration < config.maxIterations do {
      val before = current
      current = passes.foldLeft(current) { (prog, pass) =>
        pass.run(prog)
      }
      changed = current.nodes.size != before.nodes.size ||
        current.nodes.keys.toSet != before.nodes.keys.toSet
      iteration += 1
    }

    val afterStats = analyze(current)
    val stats = OptimizationStats(
      nodesBefore = beforeStats.totalNodes,
      nodesAfter = afterStats.totalNodes,
      nodesEliminated = beforeStats.totalNodes - afterStats.totalNodes,
      passesApplied = passes.map(_.name),
      iterations = iteration
    )

    OptimizationResult(current, stats, iteration)
  }

  /** Optimize an IR pipeline and return just the optimized IR.
    *
    * Convenience method for when statistics are not needed.
    */
  def optimizeIR(
      ir: IRPipeline,
      config: OptimizationConfig = OptimizationConfig.default
  ): IRPipeline = optimize(ir, config).optimizedIR

  /** Build the list of enabled optimization passes */
  private def buildPassList(config: OptimizationConfig): List[OptimizationPass] =
    List(
      // Order matters: DCE should run after other optimizations to clean up
      if config.enableConstantFolding then Some(ConstantFolding) else None,
      if config.enableCSE then Some(CommonSubexpressionElimination) else None,
      if config.enableDCE then Some(DeadCodeElimination) else None
    ).flatten

  /** Analyze an IR pipeline and return statistics */
  def analyze(ir: IRPipeline): IRAnalysis = {
    val nodesByType = ir.nodes.values.groupBy(_.getClass.getSimpleName)

    IRAnalysis(
      totalNodes = ir.nodes.size,
      inputNodes = nodesByType.getOrElse("Input", Nil).size,
      moduleCallNodes = nodesByType.getOrElse("ModuleCall", Nil).size,
      literalNodes = nodesByType.getOrElse("LiteralNode", Nil).size,
      mergeNodes = nodesByType.getOrElse("MergeNode", Nil).size,
      conditionalNodes = nodesByType.getOrElse("ConditionalNode", Nil).size,
      otherNodes = ir.nodes.size -
        nodesByType.getOrElse("Input", Nil).size -
        nodesByType.getOrElse("ModuleCall", Nil).size -
        nodesByType.getOrElse("LiteralNode", Nil).size -
        nodesByType.getOrElse("MergeNode", Nil).size -
        nodesByType.getOrElse("ConditionalNode", Nil).size
    )
  }
}

/** Result of optimization including the optimized IR and statistics */
final case class OptimizationResult(
    optimizedIR: IRPipeline,
    stats: OptimizationStats,
    iterations: Int
)

/** Statistics about optimization results */
final case class OptimizationStats(
    nodesBefore: Int,
    nodesAfter: Int,
    nodesEliminated: Int,
    passesApplied: List[String],
    iterations: Int
) {

  /** Percentage of nodes eliminated */
  def eliminationPercentage: Double =
    if nodesBefore == 0 then 0.0
    else (nodesEliminated.toDouble / nodesBefore) * 100

  /** Summary string for logging */
  def summary: String =
    s"Optimization: $nodesEliminated nodes eliminated ($nodesBefore -> $nodesAfter, " +
      f"${eliminationPercentage}%.1f%%) in $iterations iteration(s)"
}

object OptimizationStats {
  val empty: OptimizationStats = OptimizationStats(
    nodesBefore = 0,
    nodesAfter = 0,
    nodesEliminated = 0,
    passesApplied = Nil,
    iterations = 0
  )
}

/** Analysis of IR structure */
final case class IRAnalysis(
    totalNodes: Int,
    inputNodes: Int,
    moduleCallNodes: Int,
    literalNodes: Int,
    mergeNodes: Int,
    conditionalNodes: Int,
    otherNodes: Int
)
