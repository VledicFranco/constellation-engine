package io.constellation

import java.util.UUID

/** Serializable snapshot of a suspended pipeline execution.
  *
  * Contains all the information needed to resume execution: the DAG structure, already-computed
  * values, provided inputs, and module statuses.
  *
  * @param executionId
  *   Unique ID for this execution
  * @param structuralHash
  *   Structural hash of the pipeline
  * @param resumptionCount
  *   How many times this execution has been resumed
  * @param dagSpec
  *   The DAG specification
  * @param moduleOptions
  *   Per-module runtime options
  * @param providedInputs
  *   Inputs that were provided by the user
  * @param computedValues
  *   Data node values already computed (UUID -> CValue)
  * @param moduleStatuses
  *   Status of each module (UUID -> status string)
  */
final case class SuspendedExecution(
    executionId: UUID,
    structuralHash: String,
    resumptionCount: Int,
    dagSpec: DagSpec,
    moduleOptions: Map[UUID, ModuleCallOptions],
    providedInputs: Map[String, CValue],
    computedValues: Map[UUID, CValue],
    moduleStatuses: Map[UUID, String]
)
