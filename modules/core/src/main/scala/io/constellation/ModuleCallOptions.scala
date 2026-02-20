package io.constellation

/** Runtime-level module call options.
  *
  * Unlike [[io.constellation.lang.compiler.IRModuleCallOptions]] which lives in `lang-compiler` and
  * references AST-level enum types, this case class uses plain strings for enum fields so that it
  * can live in `core` without any `lang-ast` dependency.
  *
  * @param retry
  *   Maximum retry count
  * @param timeoutMs
  *   Timeout in milliseconds
  * @param delayMs
  *   Delay before execution in milliseconds
  * @param backoff
  *   Backoff strategy name: "fixed", "linear", "exponential"
  * @param cacheMs
  *   Cache TTL in milliseconds
  * @param cacheBackend
  *   Cache backend name
  * @param throttleCount
  *   Throttle: max operations
  * @param throttlePerMs
  *   Throttle: per time window in milliseconds
  * @param concurrency
  *   Concurrency limit
  * @param onError
  *   Error strategy name: "fail", "skip", etc.
  * @param lazyEval
  *   Whether to evaluate lazily
  * @param priority
  *   Numeric priority (0-100)
  */
final case class ModuleCallOptions(
    retry: Option[Int] = None,
    timeoutMs: Option[Long] = None,
    delayMs: Option[Long] = None,
    backoff: Option[String] = None,
    cacheMs: Option[Long] = None,
    cacheBackend: Option[String] = None,
    throttleCount: Option[Int] = None,
    throttlePerMs: Option[Long] = None,
    concurrency: Option[Int] = None,
    onError: Option[String] = None,
    lazyEval: Option[Boolean] = None,
    priority: Option[Int] = None,
    // Streaming options (RFC-025 Phase 3)
    batchSize: Option[Int] = None,
    batchTimeoutMs: Option[Long] = None,
    window: Option[String] = None,
    checkpointMs: Option[Long] = None,
    joinStrategy: Option[String] = None
) {

  def isEmpty: Boolean =
    retry.isEmpty && timeoutMs.isEmpty && delayMs.isEmpty && backoff.isEmpty &&
      cacheMs.isEmpty && cacheBackend.isEmpty && throttleCount.isEmpty &&
      throttlePerMs.isEmpty && concurrency.isEmpty && onError.isEmpty &&
      lazyEval.isEmpty && priority.isEmpty &&
      batchSize.isEmpty && batchTimeoutMs.isEmpty && window.isEmpty &&
      checkpointMs.isEmpty && joinStrategy.isEmpty
}

object ModuleCallOptions {
  val empty: ModuleCallOptions = ModuleCallOptions()
}
