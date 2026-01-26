package io.constellation.http

import cats.effect.{IO, Ref}
import cats.implicits._
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto._
import io.constellation.lang.viz.DagVizIR

import java.util.UUID
import scala.collection.immutable.Queue

/** Execution status for stored executions */
enum ExecutionStatus:
  case Running   // Execution in progress
  case Completed // Execution completed successfully
  case Failed    // Execution failed with error

object ExecutionStatus:
  given Encoder[ExecutionStatus] = Encoder.encodeString.contramap(_.toString)
  given Decoder[ExecutionStatus] = Decoder.decodeString.emap { s =>
    ExecutionStatus.values.find(_.toString == s).toRight(s"Unknown ExecutionStatus: $s")
  }

/** Source of the execution request */
enum ExecutionSource:
  case Dashboard        // Executed from web dashboard
  case VSCodeExtension  // Executed from VSCode extension
  case API              // Executed directly via API

object ExecutionSource:
  given Encoder[ExecutionSource] = Encoder.encodeString.contramap(_.toString)
  given Decoder[ExecutionSource] = Decoder.decodeString.emap { s =>
    ExecutionSource.values.find(_.toString == s).toRight(s"Unknown ExecutionSource: $s")
  }

/** Result of executing a single node */
case class StoredNodeResult(
    nodeId: String,
    status: ExecutionStatus,
    value: Option[Json] = None,
    durationMs: Option[Long] = None,
    error: Option[String] = None
)

object StoredNodeResult:
  given Encoder[StoredNodeResult] = deriveEncoder
  given Decoder[StoredNodeResult] = deriveDecoder

/** Complete stored execution record */
case class StoredExecution(
    executionId: String,
    dagName: String,
    scriptPath: Option[String],
    startTime: Long,
    endTime: Option[Long],
    inputs: Map[String, Json],
    outputs: Option[Map[String, Json]],
    status: ExecutionStatus,
    nodeResults: Map[String, StoredNodeResult],
    dagVizIR: Option[DagVizIR],
    sampleRate: Double,
    source: ExecutionSource
) {
  /** Total execution time in milliseconds, if completed */
  def durationMs: Option[Long] = endTime.map(_ - startTime)
}

object StoredExecution:
  given Encoder[StoredExecution] = deriveEncoder
  given Decoder[StoredExecution] = deriveDecoder

/** Summary of an execution for listing */
case class ExecutionSummary(
    executionId: String,
    dagName: String,
    scriptPath: Option[String],
    startTime: Long,
    endTime: Option[Long],
    status: ExecutionStatus,
    source: ExecutionSource,
    nodeCount: Int,
    outputPreview: Option[String]
)

object ExecutionSummary:
  given Encoder[ExecutionSummary] = deriveEncoder
  given Decoder[ExecutionSummary] = deriveDecoder

  def fromStored(exec: StoredExecution): ExecutionSummary = {
    val preview = exec.outputs.flatMap { outputs =>
      outputs.headOption.map { case (key, value) =>
        val jsonStr = value.noSpaces
        val truncated = if jsonStr.length > 50 then jsonStr.take(50) + "..." else jsonStr
        s"$key: $truncated"
      }
    }
    ExecutionSummary(
      executionId = exec.executionId,
      dagName = exec.dagName,
      scriptPath = exec.scriptPath,
      startTime = exec.startTime,
      endTime = exec.endTime,
      status = exec.status,
      source = exec.source,
      nodeCount = exec.nodeResults.size,
      outputPreview = preview
    )
  }

/** Storage statistics */
case class StorageStats(
    totalExecutions: Int,
    runningExecutions: Int,
    completedExecutions: Int,
    failedExecutions: Int,
    oldestExecutionTime: Option[Long],
    newestExecutionTime: Option[Long],
    maxCapacity: Int
)

object StorageStats:
  given Encoder[StorageStats] = deriveEncoder
  given Decoder[StorageStats] = deriveDecoder

/** Trait for execution storage backends.
  *
  * Provides a pluggable interface for storing and retrieving execution history.
  * The default implementation is in-memory with LRU eviction.
  */
trait ExecutionStorage[F[_]] {

  /** Store an execution record.
    *
    * @param execution The execution to store
    * @return The execution ID
    */
  def store(execution: StoredExecution): F[String]

  /** Get an execution by ID.
    *
    * @param executionId The execution ID to look up
    * @return The execution if found
    */
  def get(executionId: String): F[Option[StoredExecution]]

  /** List executions with pagination.
    *
    * @param limit Maximum number of results
    * @param offset Number of results to skip
    * @return List of execution summaries
    */
  def list(limit: Int, offset: Int): F[List[ExecutionSummary]]

  /** List executions for a specific script.
    *
    * @param scriptPath Path to the script
    * @param limit Maximum number of results
    * @return List of execution summaries
    */
  def listByScript(scriptPath: String, limit: Int): F[List[ExecutionSummary]]

  /** Get storage statistics. */
  def stats: F[StorageStats]

  /** Update an execution (for recording completion).
    *
    * @param executionId The execution ID to update
    * @param f Function to transform the execution
    * @return Updated execution if found
    */
  def update(executionId: String)(f: StoredExecution => StoredExecution): F[Option[StoredExecution]]

  /** Delete an execution.
    *
    * @param executionId The execution ID to delete
    * @return True if deleted, false if not found
    */
  def delete(executionId: String): F[Boolean]

  /** Clear all executions (useful for testing). */
  def clear: F[Unit]
}

object ExecutionStorage {

  /** Configuration for execution storage.
    *
    * @param maxExecutions Maximum number of executions to store (LRU eviction)
    * @param maxValueSizeBytes Maximum size of JSON values (larger values are truncated)
    */
  case class Config(
      maxExecutions: Int = 1000,
      maxValueSizeBytes: Int = 10 * 1024 // 10KB
  )

  /** Create a new in-memory execution storage with default configuration. */
  def inMemory: IO[ExecutionStorage[IO]] = inMemory(Config())

  /** Create a new in-memory execution storage with custom configuration. */
  def inMemory(config: Config): IO[ExecutionStorage[IO]] = for {
    state <- Ref.of[IO, StorageState](StorageState.empty(config.maxExecutions))
  } yield new InMemoryExecutionStorage(state, config)

  /** Internal state for the in-memory storage. */
  private case class StorageState(
      executions: Map[String, StoredExecution],
      lruOrder: Queue[String], // Oldest first for eviction
      maxCapacity: Int
  ) {

    def add(execution: StoredExecution): StorageState = {
      val newState = copy(
        executions = executions + (execution.executionId -> execution),
        lruOrder = lruOrder.enqueue(execution.executionId)
      )
      newState.evictIfNeeded
    }

    def update(executionId: String)(f: StoredExecution => StoredExecution): StorageState =
      executions.get(executionId) match {
        case Some(exec) => copy(executions = executions + (executionId -> f(exec)))
        case None       => this
      }

    def remove(executionId: String): StorageState =
      copy(
        executions = executions - executionId,
        lruOrder = lruOrder.filterNot(_ == executionId)
      )

    private def evictOldest: StorageState =
      lruOrder.dequeueOption match {
        case Some((oldestId, remaining)) =>
          copy(executions = executions - oldestId, lruOrder = remaining)
        case None => this
      }

    private def evictIfNeeded: StorageState =
      if executions.size > maxCapacity then evictOldest.evictIfNeeded
      else this
  }

  private object StorageState {
    def empty(maxCapacity: Int): StorageState =
      StorageState(Map.empty, Queue.empty, maxCapacity)
  }

  /** In-memory implementation of ExecutionStorage using Ref for thread-safe state. */
  private class InMemoryExecutionStorage(
      state: Ref[IO, StorageState],
      config: Config
  ) extends ExecutionStorage[IO] {

    def store(execution: StoredExecution): IO[String] = {
      val truncatedExec = truncateExecution(execution)
      state.update(_.add(truncatedExec)).as(execution.executionId)
    }

    def get(executionId: String): IO[Option[StoredExecution]] =
      state.get.map(_.executions.get(executionId))

    def list(limit: Int, offset: Int): IO[List[ExecutionSummary]] =
      state.get.map { s =>
        s.executions.values.toList
          .sortBy(-_.startTime) // Most recent first
          .slice(offset, offset + limit)
          .map(ExecutionSummary.fromStored)
      }

    def listByScript(scriptPath: String, limit: Int): IO[List[ExecutionSummary]] =
      state.get.map { s =>
        s.executions.values.toList
          .filter(_.scriptPath.contains(scriptPath))
          .sortBy(-_.startTime)
          .take(limit)
          .map(ExecutionSummary.fromStored)
      }

    def stats: IO[StorageStats] =
      state.get.map { s =>
        val execs = s.executions.values.toList
        StorageStats(
          totalExecutions = execs.size,
          runningExecutions = execs.count(_.status == ExecutionStatus.Running),
          completedExecutions = execs.count(_.status == ExecutionStatus.Completed),
          failedExecutions = execs.count(_.status == ExecutionStatus.Failed),
          oldestExecutionTime = execs.map(_.startTime).minOption,
          newestExecutionTime = execs.map(_.startTime).maxOption,
          maxCapacity = s.maxCapacity
        )
      }

    def update(executionId: String)(f: StoredExecution => StoredExecution): IO[Option[StoredExecution]] =
      state.modify { s =>
        val updated = s.update(executionId)(f)
        (updated, updated.executions.get(executionId))
      }

    def delete(executionId: String): IO[Boolean] =
      state.modify { s =>
        val exists = s.executions.contains(executionId)
        (s.remove(executionId), exists)
      }

    def clear: IO[Unit] =
      state.set(StorageState.empty(config.maxExecutions))

    /** Truncate JSON values in an execution if they exceed the maximum size. */
    private def truncateExecution(exec: StoredExecution): StoredExecution = {
      val truncatedInputs = exec.inputs.view.mapValues(truncateValue).toMap
      val truncatedOutputs = exec.outputs.map(_.view.mapValues(truncateValue).toMap)
      val truncatedNodes = exec.nodeResults.view.mapValues { node =>
        node.copy(value = node.value.map(truncateValue))
      }.toMap
      exec.copy(
        inputs = truncatedInputs,
        outputs = truncatedOutputs,
        nodeResults = truncatedNodes
      )
    }

    private def truncateValue(json: Json): Json = {
      val serialized = json.noSpaces
      if serialized.length <= config.maxValueSizeBytes then json
      else Json.fromString(s"<truncated: ${serialized.length} bytes>")
    }
  }

  /** Helper to create a new execution record. */
  def createExecution(
      dagName: String,
      scriptPath: Option[String],
      inputs: Map[String, Json],
      source: ExecutionSource,
      sampleRate: Double = 1.0
  ): StoredExecution = StoredExecution(
    executionId = UUID.randomUUID().toString,
    dagName = dagName,
    scriptPath = scriptPath,
    startTime = System.currentTimeMillis(),
    endTime = None,
    inputs = inputs,
    outputs = None,
    status = ExecutionStatus.Running,
    nodeResults = Map.empty,
    dagVizIR = None,
    sampleRate = sampleRate,
    source = source
  )

  /** Check if an execution should be sampled based on rate. */
  def shouldSample(source: ExecutionSource, scriptRate: Option[Double], configRate: Double): Boolean =
    source match {
      case ExecutionSource.VSCodeExtension => true // Always store VSCode executions
      case _ =>
        val rate = scriptRate.getOrElse(configRate)
        scala.util.Random.nextDouble() < rate
    }
}
