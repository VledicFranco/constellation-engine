package io.constellation

import cats.effect.IO

import java.time.Instant

/** Opaque handle to a stored suspended execution. */
final case class SuspensionHandle(id: String)

/** Summary of a stored suspension, without the full execution snapshot.
  *
  * @param handle
  *   Opaque handle for load/delete operations
  * @param executionId
  *   The original execution ID
  * @param structuralHash
  *   Program structural hash (links to ProgramStore)
  * @param resumptionCount
  *   How many times this execution has been resumed
  * @param missingInputs
  *   Inputs that are still needed (name -> type)
  * @param createdAt
  *   When the suspension was first stored
  * @param lastResumedAt
  *   When the suspension was last resumed (if ever)
  */
final case class SuspensionSummary(
    handle: SuspensionHandle,
    executionId: java.util.UUID,
    structuralHash: String,
    resumptionCount: Int,
    missingInputs: Map[String, CType],
    createdAt: Instant,
    lastResumedAt: Option[Instant]
)

/** Filter criteria for listing stored suspensions.
  *
  * All fields are optional; when set, they are combined with AND logic.
  *
  * @param structuralHash
  *   Only include suspensions for this program hash
  * @param executionId
  *   Only include this specific execution
  * @param minResumptionCount
  *   Only include suspensions resumed at least this many times
  * @param maxResumptionCount
  *   Only include suspensions resumed at most this many times
  */
final case class SuspensionFilter(
    structuralHash: Option[String] = None,
    executionId: Option[java.util.UUID] = None,
    minResumptionCount: Option[Int] = None,
    maxResumptionCount: Option[Int] = None
)

object SuspensionFilter {

  /** Filter that matches all stored suspensions. */
  val All: SuspensionFilter = SuspensionFilter()
}

/** Persistence layer for [[SuspendedExecution]] snapshots.
  *
  * Provides save/load/delete/list operations keyed by [[SuspensionHandle]]. Implementations may be
  * in-memory (for dev/test) or backed by a database.
  */
trait SuspensionStore {

  /** Save a suspended execution and return a handle for later retrieval.
    *
    * @param suspended
    *   The execution snapshot to store
    * @return
    *   A handle that can be used to load or delete the snapshot
    */
  def save(suspended: SuspendedExecution): IO[SuspensionHandle]

  /** Load a suspended execution by handle.
    *
    * @param handle
    *   The handle returned by a previous `save`
    * @return
    *   The stored snapshot, or None if not found
    */
  def load(handle: SuspensionHandle): IO[Option[SuspendedExecution]]

  /** Delete a stored suspension.
    *
    * @param handle
    *   The handle to delete
    * @return
    *   true if the entry was found and deleted, false if not found
    */
  def delete(handle: SuspensionHandle): IO[Boolean]

  /** List stored suspensions matching the given filter.
    *
    * @param filter
    *   Criteria to filter by (default: all)
    * @return
    *   Summaries of matching suspensions
    */
  def list(filter: SuspensionFilter = SuspensionFilter.All): IO[List[SuspensionSummary]]
}
