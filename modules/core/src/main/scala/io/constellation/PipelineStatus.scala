package io.constellation

import java.time.{Duration, Instant}
import java.util.UUID

/** Status of a pipeline execution.
  *
  * Distinct from [[io.constellation.execution.ExecutionStatus]] which tracks
  * Running/Completed/Cancelled/TimedOut/Failed for CancellableExecution handles. PipelineStatus
  * describes the data-flow outcome: all outputs computed, some outputs pending, or errors
  * encountered.
  */
sealed trait PipelineStatus

object PipelineStatus {

  /** All declared outputs have been computed. */
  case object Completed extends PipelineStatus

  /** Some inputs or intermediate values are missing; the execution can be resumed. */
  case object Suspended extends PipelineStatus

  /** One or more modules failed during execution. */
  final case class Failed(errors: List[ExecutionError]) extends PipelineStatus
}

/** Describes a single error that occurred during module execution. */
final case class ExecutionError(
    nodeName: String,
    moduleName: String,
    message: String,
    cause: Option[Throwable] = None,
    retriesAttempted: Int = 0
)

/** Describes how a particular data node's value was obtained. */
sealed trait ResolutionSource

object ResolutionSource {

  /** Value was computed by executing a module. */
  case object FromModuleExecution extends ResolutionSource

  /** Value was provided as a user input. */
  case object FromInput extends ResolutionSource

  /** Value was manually supplied during a resume call. */
  case object FromManualResolution extends ResolutionSource
}

/** Optional metadata collected during execution.
  *
  * All fields are optional and governed by [[ExecutionOptions]].
  */
final case class SignatureMetadata(
    startedAt: Option[Instant] = None,
    completedAt: Option[Instant] = None,
    totalDuration: Option[Duration] = None,
    nodeTimings: Option[Map[String, Duration]] = None,
    provenance: Option[Map[String, String]] = None,
    blockedGraph: Option[Map[String, List[String]]] = None,
    resolutionSources: Option[Map[String, ResolutionSource]] = None
)

/** Controls which optional metadata is collected during execution.
  *
  * All flags default to false to keep the common path lightweight.
  */
final case class ExecutionOptions(
    includeTimings: Boolean = false,
    includeProvenance: Boolean = false,
    includeBlockedGraph: Boolean = false,
    includeResolutionSources: Boolean = false
)
