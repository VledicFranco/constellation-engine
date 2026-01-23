package io.constellation

import cats.effect.{IO, Ref}
import cats.implicits._
import io.circe.{Encoder, Json}
import io.circe.generic.semiauto._

import java.util.UUID
import scala.collection.immutable.Queue

/** Execution status for tracking node execution.
  *
  * This is defined in the runtime module and converted to DagVizIR.ExecutionStatus by the LSP.
  */
enum NodeStatus:
  case Pending   // Not yet executed
  case Running   // Currently executing
  case Completed // Successfully completed
  case Failed    // Execution failed

object NodeStatus:
  given Encoder[NodeStatus] = Encoder.encodeString.contramap(_.toString)

/** Result of executing a single node in the DAG.
  *
  * @param nodeId
  *   The unique identifier for this node
  * @param status
  *   The execution status (Pending, Running, Completed, Failed)
  * @param value
  *   The computed value as JSON (truncated if large)
  * @param durationMs
  *   How long this node took to execute in milliseconds
  * @param error
  *   Error message if execution failed
  */
case class NodeExecutionResult(
    nodeId: String,
    status: NodeStatus,
    value: Option[Json] = None,
    durationMs: Option[Long] = None,
    error: Option[String] = None
)

object NodeExecutionResult:
  given Encoder[NodeExecutionResult] = deriveEncoder

/** Complete trace of a DAG execution.
  *
  * @param executionId
  *   Unique identifier for this execution
  * @param dagName
  *   Name of the DAG that was executed
  * @param startTime
  *   Unix timestamp when execution started
  * @param endTime
  *   Unix timestamp when execution ended (if completed)
  * @param nodeResults
  *   Map of node ID to execution result
  */
case class ExecutionTrace(
    executionId: String,
    dagName: String,
    startTime: Long,
    endTime: Option[Long] = None,
    nodeResults: Map[String, NodeExecutionResult] = Map.empty
) {

  /** Total execution time in milliseconds, if completed */
  def totalDurationMs: Option[Long] = endTime.map(_ - startTime)

  /** Check if execution is complete */
  def isComplete: Boolean = endTime.isDefined
}

object ExecutionTrace:
  given Encoder[ExecutionTrace] = deriveEncoder

/** Tracker for capturing per-node execution data during DAG execution.
  *
  * Thread-safe implementation using Ref for concurrent access. Provides LRU eviction to prevent
  * unbounded memory growth.
  */
trait ExecutionTracker[F[_]] {

  /** Start tracking a new execution.
    *
    * @param dagName
    *   Name of the DAG being executed
    * @return
    *   Unique execution ID for this run
    */
  def startExecution(dagName: String): F[String]

  /** Record that a node has started executing.
    *
    * @param executionId
    *   The execution ID from startExecution
    * @param nodeId
    *   The node that is starting
    */
  def recordNodeStart(executionId: String, nodeId: String): F[Unit]

  /** Record that a node completed successfully.
    *
    * @param executionId
    *   The execution ID from startExecution
    * @param nodeId
    *   The node that completed
    * @param value
    *   The computed value as JSON
    * @param durationMs
    *   How long the node took to execute
    */
  def recordNodeComplete(
      executionId: String,
      nodeId: String,
      value: Json,
      durationMs: Long
  ): F[Unit]

  /** Record that a node failed.
    *
    * @param executionId
    *   The execution ID from startExecution
    * @param nodeId
    *   The node that failed
    * @param error
    *   Error message describing the failure
    * @param durationMs
    *   How long until the node failed
    */
  def recordNodeFailed(
      executionId: String,
      nodeId: String,
      error: String,
      durationMs: Long
  ): F[Unit]

  /** Mark an execution as finished.
    *
    * @param executionId
    *   The execution ID from startExecution
    */
  def finishExecution(executionId: String): F[Unit]

  /** Get the trace for a specific execution.
    *
    * @param executionId
    *   The execution ID to look up
    * @return
    *   The trace if it exists and hasn't been evicted
    */
  def getTrace(executionId: String): F[Option[ExecutionTrace]]

  /** Get all active traces (for debugging/monitoring). */
  def getAllTraces: F[List[ExecutionTrace]]

  /** Clear all traces (useful for testing). */
  def clear: F[Unit]
}

object ExecutionTracker {

  /** Configuration for the execution tracker.
    *
    * @param maxTraces
    *   Maximum number of traces to keep (LRU eviction)
    * @param maxValueSizeBytes
    *   Maximum size of serialized JSON values (larger values are truncated)
    */
  case class Config(
      maxTraces: Int = 100,
      maxValueSizeBytes: Int = 10 * 1024 // 10KB
  )

  /** Create a new ExecutionTracker with default configuration. */
  def create: IO[ExecutionTracker[IO]] = create(Config())

  /** Create a new ExecutionTracker with custom configuration. */
  def create(config: Config): IO[ExecutionTracker[IO]] = for {
    state <- Ref.of[IO, TrackerState](TrackerState.empty)
  } yield new ExecutionTrackerImpl(state, config)

  /** Internal state for the tracker. */
  private case class TrackerState(
      traces: Map[String, ExecutionTrace],
      lruOrder: Queue[String] // Oldest first for eviction
  ) {

    def addTrace(trace: ExecutionTrace): TrackerState =
      copy(
        traces = traces + (trace.executionId -> trace),
        lruOrder = lruOrder.enqueue(trace.executionId)
      )

    def updateTrace(executionId: String)(f: ExecutionTrace => ExecutionTrace): TrackerState =
      traces.get(executionId) match {
        case Some(trace) => copy(traces = traces + (executionId -> f(trace)))
        case None        => this
      }

    def evictOldest: TrackerState =
      lruOrder.dequeueOption match {
        case Some((oldestId, remaining)) =>
          copy(traces = traces - oldestId, lruOrder = remaining)
        case None => this
      }

    def evictIfNeeded(maxTraces: Int): TrackerState =
      if traces.size > maxTraces then evictOldest.evictIfNeeded(maxTraces)
      else this
  }

  private object TrackerState {
    val empty: TrackerState = TrackerState(Map.empty, Queue.empty)
  }

  /** Implementation of ExecutionTracker using Ref for thread-safe state. */
  private class ExecutionTrackerImpl(
      state: Ref[IO, TrackerState],
      config: Config
  ) extends ExecutionTracker[IO] {

    def startExecution(dagName: String): IO[String] = for {
      executionId <- IO(UUID.randomUUID().toString)
      now         <- IO(System.currentTimeMillis())
      trace = ExecutionTrace(
        executionId = executionId,
        dagName = dagName,
        startTime = now
      )
      _ <- state.update(s => s.addTrace(trace).evictIfNeeded(config.maxTraces))
    } yield executionId

    def recordNodeStart(executionId: String, nodeId: String): IO[Unit] =
      state.update(_.updateTrace(executionId) { trace =>
        val result = NodeExecutionResult(
          nodeId = nodeId,
          status = NodeStatus.Running
        )
        trace.copy(nodeResults = trace.nodeResults + (nodeId -> result))
      })

    def recordNodeComplete(
        executionId: String,
        nodeId: String,
        value: Json,
        durationMs: Long
    ): IO[Unit] = {
      val truncatedValue = truncateValue(value, config.maxValueSizeBytes)
      state.update(_.updateTrace(executionId) { trace =>
        val result = NodeExecutionResult(
          nodeId = nodeId,
          status = NodeStatus.Completed,
          value = Some(truncatedValue),
          durationMs = Some(durationMs)
        )
        trace.copy(nodeResults = trace.nodeResults + (nodeId -> result))
      })
    }

    def recordNodeFailed(
        executionId: String,
        nodeId: String,
        error: String,
        durationMs: Long
    ): IO[Unit] =
      state.update(_.updateTrace(executionId) { trace =>
        val result = NodeExecutionResult(
          nodeId = nodeId,
          status = NodeStatus.Failed,
          error = Some(error),
          durationMs = Some(durationMs)
        )
        trace.copy(nodeResults = trace.nodeResults + (nodeId -> result))
      })

    def finishExecution(executionId: String): IO[Unit] = for {
      now <- IO(System.currentTimeMillis())
      _ <- state.update(_.updateTrace(executionId) { trace =>
        trace.copy(endTime = Some(now))
      })
    } yield ()

    def getTrace(executionId: String): IO[Option[ExecutionTrace]] =
      state.get.map(_.traces.get(executionId))

    def getAllTraces: IO[List[ExecutionTrace]] =
      state.get.map(_.traces.values.toList)

    def clear: IO[Unit] =
      state.set(TrackerState.empty)

    /** Truncate a JSON value if it exceeds the maximum size. */
    private def truncateValue(json: Json, maxBytes: Int): Json = {
      val serialized = json.noSpaces
      if serialized.length <= maxBytes then json
      else Json.fromString(s"<truncated: ${serialized.length} bytes>")
    }
  }

  /** Helper to convert Runtime.State to execution trace results.
    *
    * This bridges the existing Runtime execution with the new tracking system.
    */
  def fromRuntimeState(
      executionId: String,
      dagName: String,
      runtimeState: Runtime.State,
      startTime: Long
  ): ExecutionTrace = {
    val endTime = System.currentTimeMillis()

    // Convert module status to node results
    val moduleResults = runtimeState.moduleStatus.map { case (moduleId, evalStatus) =>
      val nodeId = moduleId.toString
      val status = evalStatus.value
      val result = status match {
        case Module.Status.Unfired =>
          NodeExecutionResult(nodeId, NodeStatus.Pending)

        case Module.Status.Fired(latency, _) =>
          NodeExecutionResult(
            nodeId = nodeId,
            status = NodeStatus.Completed,
            durationMs = Some(latency.toMillis)
          )

        case Module.Status.Timed(latency) =>
          NodeExecutionResult(
            nodeId = nodeId,
            status = NodeStatus.Failed,
            error = Some(s"Timed out after ${latency.toMillis}ms"),
            durationMs = Some(latency.toMillis)
          )

        case Module.Status.Failed(error) =>
          NodeExecutionResult(
            nodeId = nodeId,
            status = NodeStatus.Failed,
            error = Some(error.getMessage)
          )
      }
      nodeId -> result
    }

    // Convert data values to node results
    val dataResults = runtimeState.data.map { case (dataId, evalValue) =>
      val nodeId = dataId.toString
      val value  = evalValue.value
      val json   = JsonCValueConverter.cValueToJson(value)
      val result = NodeExecutionResult(
        nodeId = nodeId,
        status = NodeStatus.Completed,
        value = Some(json)
      )
      nodeId -> result
    }

    ExecutionTrace(
      executionId = executionId,
      dagName = dagName,
      startTime = startTime,
      endTime = Some(endTime),
      nodeResults = moduleResults ++ dataResults
    )
  }
}
