package io.constellation

import java.util.UUID

/** Describes the outcome of executing a pipeline.
  *
  * Contains the execution status, all computed values, declared outputs, information about missing
  * inputs (for suspended executions), and optional metadata. When the status is
  * [[PipelineStatus.Suspended]] or [[PipelineStatus.Failed]], a [[SuspendedExecution]] snapshot is
  * included so the execution can be resumed.
  *
  * @param executionId
  *   Unique ID for this execution run
  * @param structuralHash
  *   Structural hash of the pipeline that was executed
  * @param resumptionCount
  *   Number of times this execution has been resumed (0 for first run)
  * @param status
  *   Terminal status of the execution
  * @param inputs
  *   Input values that were provided
  * @param computedNodes
  *   All computed data node values (keyed by variable name)
  * @param outputs
  *   Subset of computedNodes matching declared outputs
  * @param missingInputs
  *   Input variable names that were expected but not provided
  * @param pendingOutputs
  *   Declared output names that were not computed
  * @param suspendedState
  *   Snapshot for resumption (present when Suspended or Failed)
  * @param metadata
  *   Optional timing/provenance metadata
  */
final case class DataSignature(
    executionId: UUID,
    structuralHash: String,
    resumptionCount: Int,
    status: PipelineStatus,
    inputs: Map[String, CValue],
    computedNodes: Map[String, CValue],
    outputs: Map[String, CValue],
    missingInputs: List[String],
    pendingOutputs: List[String],
    suspendedState: Option[SuspendedExecution] = None,
    metadata: SignatureMetadata = SignatureMetadata()
) {

  /** Whether all declared outputs have been computed. */
  def isComplete: Boolean = status == PipelineStatus.Completed

  /** Look up a specific output by name. */
  def output(name: String): Option[CValue] = outputs.get(name)

  /** Look up any computed node by variable name. */
  def node(name: String): Option[CValue] = computedNodes.get(name)

  /** All provided input values. */
  def allInputs: Map[String, CValue] = inputs

  /** Fraction of declared outputs that have been computed (0.0 to 1.0). */
  def progress: Double = {
    val total = outputs.size + pendingOutputs.size
    if total == 0 then 1.0 else outputs.size.toDouble / total
  }

  /** Names of nodes whose modules failed during execution. */
  def failedNodes: List[String] = status match {
    case PipelineStatus.Failed(errors) => errors.map(_.nodeName)
    case _                             => Nil
  }
}
