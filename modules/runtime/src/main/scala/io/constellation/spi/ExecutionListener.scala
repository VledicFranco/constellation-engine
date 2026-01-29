package io.constellation.spi

import cats.effect.IO

import java.util.UUID

/** Service Provider Interface for execution lifecycle events.
  *
  * Embedders implement this trait to receive callbacks when DAG executions
  * and module invocations start and complete. All callbacks are invoked
  * fire-and-forget (errors are swallowed, execution is not blocked).
  *
  * Use `ExecutionListener.composite` to combine multiple listeners.
  */
trait ExecutionListener {

  /** Called when a DAG execution begins.
    *
    * @param executionId Unique identifier for this execution
    * @param dagName Name of the DAG being executed
    */
  def onExecutionStart(executionId: UUID, dagName: String): IO[Unit]

  /** Called when a module begins execution within a DAG.
    *
    * @param executionId The parent execution identifier
    * @param moduleId UUID of the module node
    * @param moduleName Human-readable module name
    */
  def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit]

  /** Called when a module completes successfully.
    *
    * @param executionId The parent execution identifier
    * @param moduleId UUID of the module node
    * @param moduleName Human-readable module name
    * @param durationMs Execution duration in milliseconds
    */
  def onModuleComplete(
      executionId: UUID,
      moduleId: UUID,
      moduleName: String,
      durationMs: Long
  ): IO[Unit]

  /** Called when a module fails.
    *
    * @param executionId The parent execution identifier
    * @param moduleId UUID of the module node
    * @param moduleName Human-readable module name
    * @param error The failure cause
    */
  def onModuleFailed(
      executionId: UUID,
      moduleId: UUID,
      moduleName: String,
      error: Throwable
  ): IO[Unit]

  /** Called when an entire DAG execution completes.
    *
    * @param executionId The execution identifier
    * @param dagName Name of the DAG
    * @param succeeded Whether all modules completed without failure
    * @param durationMs Total execution duration in milliseconds
    */
  def onExecutionComplete(
      executionId: UUID,
      dagName: String,
      succeeded: Boolean,
      durationMs: Long
  ): IO[Unit]

  /** Called when an execution is cancelled (user-initiated or timeout).
    *
    * Default implementation is a no-op for backwards compatibility.
    *
    * @param executionId The execution identifier
    * @param dagName Name of the DAG
    */
  def onExecutionCancelled(executionId: UUID, dagName: String): IO[Unit] = IO.unit
}

object ExecutionListener {

  /** No-op implementation that ignores all events. */
  val noop: ExecutionListener = new ExecutionListener {
    def onExecutionStart(executionId: UUID, dagName: String): IO[Unit] = IO.unit
    def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit] = IO.unit
    def onModuleComplete(executionId: UUID, moduleId: UUID, moduleName: String, durationMs: Long): IO[Unit] = IO.unit
    def onModuleFailed(executionId: UUID, moduleId: UUID, moduleName: String, error: Throwable): IO[Unit] = IO.unit
    def onExecutionComplete(executionId: UUID, dagName: String, succeeded: Boolean, durationMs: Long): IO[Unit] = IO.unit
    override def onExecutionCancelled(executionId: UUID, dagName: String): IO[Unit] = IO.unit
  }

  /** Combine multiple listeners into a single listener.
    *
    * Each event is dispatched to all listeners sequentially.
    * Errors from individual listeners are swallowed (logged to stderr).
    */
  def composite(listeners: ExecutionListener*): ExecutionListener = {
    if (listeners.isEmpty) noop
    else if (listeners.size == 1) listeners.head
    else new CompositeExecutionListener(listeners.toList)
  }

  private class CompositeExecutionListener(listeners: List[ExecutionListener]) extends ExecutionListener {

    private def fanOut(f: ExecutionListener => IO[Unit]): IO[Unit] =
      listeners.foldLeft(IO.unit) { (acc, listener) =>
        acc *> f(listener).handleErrorWith(_ => IO.unit)
      }

    def onExecutionStart(executionId: UUID, dagName: String): IO[Unit] =
      fanOut(_.onExecutionStart(executionId, dagName))

    def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit] =
      fanOut(_.onModuleStart(executionId, moduleId, moduleName))

    def onModuleComplete(executionId: UUID, moduleId: UUID, moduleName: String, durationMs: Long): IO[Unit] =
      fanOut(_.onModuleComplete(executionId, moduleId, moduleName, durationMs))

    def onModuleFailed(executionId: UUID, moduleId: UUID, moduleName: String, error: Throwable): IO[Unit] =
      fanOut(_.onModuleFailed(executionId, moduleId, moduleName, error))

    def onExecutionComplete(executionId: UUID, dagName: String, succeeded: Boolean, durationMs: Long): IO[Unit] =
      fanOut(_.onExecutionComplete(executionId, dagName, succeeded, durationMs))

    override def onExecutionCancelled(executionId: UUID, dagName: String): IO[Unit] =
      fanOut(_.onExecutionCancelled(executionId, dagName))
  }
}
