package io.constellation.execution

import cats.effect.IO
import cats.implicits._

import scala.concurrent.duration._

/** Backoff strategies for retry delays.
  *
  * - Fixed: Constant delay between retries
  * - Linear: Delay increases linearly (N × base delay)
  * - Exponential: Delay doubles each retry (2^N × base delay, capped at 30s)
  */
sealed trait BackoffStrategy

object BackoffStrategy {
  case object Fixed extends BackoffStrategy
  case object Linear extends BackoffStrategy
  case object Exponential extends BackoffStrategy

  /** Compute delay for a given attempt using the specified strategy.
    *
    * @param baseDelay The base delay duration
    * @param attempt The current attempt number (1-indexed)
    * @param strategy The backoff strategy to use
    * @param maxDelay Maximum delay cap (default 30 seconds)
    * @return The computed delay duration
    */
  def computeDelay(
      baseDelay: FiniteDuration,
      attempt: Int,
      strategy: BackoffStrategy,
      maxDelay: FiniteDuration = 30.seconds
  ): FiniteDuration = {
    val computed = strategy match {
      case Fixed       => baseDelay
      case Linear      => baseDelay * attempt.toLong
      case Exponential => baseDelay * math.pow(2, attempt - 1).toLong
    }
    if (computed > maxDelay) maxDelay else computed
  }
}

/** Execution wrappers for module calls with retry, timeout, and fallback support.
  *
  * These wrappers can be composed to build resilient execution pipelines:
  * {{{
  * val result = ModuleExecutor.executeWithRetry(
  *   ModuleExecutor.executeWithTimeout(
  *     module.run(inputs),
  *     timeout = 30.seconds
  *   ),
  *   maxRetries = 3,
  *   delay = Some(1.second)
  * )
  * }}}
  *
  * Or use the combined executor:
  * {{{
  * val result = ModuleExecutor.execute(
  *   module.run(inputs),
  *   options = ExecutionOptions(
  *     retry = Some(3),
  *     timeout = Some(30.seconds),
  *     fallback = Some(IO.pure(defaultValue))
  *   )
  * )
  * }}}
  */
object ModuleExecutor {

  /** Execute with retry logic.
    *
    * Retries the operation up to `maxRetries` times on failure.
    * Each attempt is independent - errors from previous attempts don't affect subsequent ones.
    *
    * @param operation The IO operation to execute
    * @param maxRetries Maximum number of retry attempts (not including initial attempt)
    * @param delay Optional delay between retry attempts
    * @param backoff Backoff strategy for delay calculation (default: Fixed)
    * @param maxDelay Maximum delay cap for backoff strategies
    * @param onRetry Optional callback invoked before each retry with attempt number and error
    * @return The result of the first successful attempt, or the last error if all fail
    */
  def executeWithRetry[A](
      operation: IO[A],
      maxRetries: Int,
      delay: Option[FiniteDuration] = None,
      backoff: BackoffStrategy = BackoffStrategy.Fixed,
      maxDelay: FiniteDuration = 30.seconds,
      onRetry: Option[(Int, Throwable) => IO[Unit]] = None
  ): IO[A] = {
    def attempt(remaining: Int, attemptNum: Int, errors: List[Throwable]): IO[A] = {
      operation.handleErrorWith { error =>
        val updatedErrors = errors :+ error

        if (remaining > 0) {
          val retryCallback = onRetry.fold(IO.unit)(cb => cb(attemptNum, error))
          val delayEffect = delay match {
            case Some(baseDelay) =>
              val computedDelay = BackoffStrategy.computeDelay(baseDelay, attemptNum, backoff, maxDelay)
              IO.sleep(computedDelay)
            case None => IO.unit
          }

          retryCallback *> delayEffect *> attempt(remaining - 1, attemptNum + 1, updatedErrors)
        } else {
          val totalAttempts = maxRetries + 1  // initial + retries
          IO.raiseError(RetryExhaustedException(
            s"Operation failed after $totalAttempts attempts",
            totalAttempts,
            updatedErrors
          ))
        }
      }
    }

    if (maxRetries <= 0) operation
    else attempt(maxRetries, 1, List.empty)
  }

  /** Execute with timeout.
    *
    * Cancels the operation if it exceeds the specified duration.
    *
    * @param operation The IO operation to execute
    * @param timeout Maximum duration to wait
    * @return The result if completed within timeout, or ModuleTimeoutException
    */
  def executeWithTimeout[A](
      operation: IO[A],
      timeout: FiniteDuration
  ): IO[A] = {
    operation.timeout(timeout).adaptError {
      case _: java.util.concurrent.TimeoutException =>
        ModuleTimeoutException(s"Operation timed out after ${timeout.toMillis}ms", timeout)
    }
  }

  /** Execute with fallback.
    *
    * Returns the fallback value if the operation fails.
    * The fallback is only evaluated if needed (lazy evaluation).
    *
    * @param operation The IO operation to execute
    * @param fallback The fallback value to use on failure (lazy)
    * @param onFallback Optional callback invoked when fallback is used with the original error
    * @return The result of the operation, or the fallback value on failure
    */
  def executeWithFallback[A](
      operation: IO[A],
      fallback: => IO[A],
      onFallback: Option[Throwable => IO[Unit]] = None
  ): IO[A] = {
    operation.handleErrorWith { error =>
      val callback = onFallback.fold(IO.unit)(cb => cb(error))
      callback *> fallback
    }
  }

  /** Combined execution with all options.
    *
    * Applies options in this order:
    * 1. Timeout (per attempt)
    * 2. Retry (wraps timeout)
    * 3. Fallback (wraps retry)
    *
    * @param operation The IO operation to execute
    * @param options Execution options
    * @return The result according to the configured options
    */
  def execute[A](
      operation: IO[A],
      options: ExecutionOptions[A]
  ): IO[A] = {
    // Step 1: Apply timeout (per attempt)
    val withTimeout = options.timeout match {
      case Some(t) => executeWithTimeout(operation, t)
      case None    => operation
    }

    // Step 2: Apply retry (wraps timeout so each attempt has its own timeout)
    val withRetry = options.retry match {
      case Some(r) => executeWithRetry(
        withTimeout,
        r,
        options.delay,
        options.backoff.getOrElse(BackoffStrategy.Fixed),
        options.maxDelay.getOrElse(30.seconds),
        options.onRetry
      )
      case None => withTimeout
    }

    // Step 3: Apply fallback (wraps retry)
    options.fallback match {
      case Some(fb) => executeWithFallback(withRetry, fb, options.onFallback)
      case None     => withRetry
    }
  }

  /** Convenience method for simple retry with delay.
    */
  def retryWithDelay[A](
      operation: IO[A],
      maxRetries: Int,
      delay: FiniteDuration
  ): IO[A] = executeWithRetry(operation, maxRetries, Some(delay))

  /** Convenience method for timeout with fallback.
    */
  def timeoutOrElse[A](
      operation: IO[A],
      timeout: FiniteDuration,
      fallback: => A
  ): IO[A] = executeWithFallback(
    executeWithTimeout(operation, timeout),
    IO.pure(fallback)
  )
}

/** Options for combined execution.
  *
  * @param retry Maximum retry attempts (None = no retry)
  * @param timeout Timeout per attempt (None = no timeout)
  * @param delay Delay between retry attempts
  * @param backoff Backoff strategy for delay calculation
  * @param maxDelay Maximum delay cap for backoff strategies
  * @param fallback Fallback value on complete failure
  * @param onRetry Callback before each retry
  * @param onFallback Callback when fallback is used
  */
final case class ExecutionOptions[A](
    retry: Option[Int] = None,
    timeout: Option[FiniteDuration] = None,
    delay: Option[FiniteDuration] = None,
    backoff: Option[BackoffStrategy] = None,
    maxDelay: Option[FiniteDuration] = None,
    fallback: Option[IO[A]] = None,
    onRetry: Option[(Int, Throwable) => IO[Unit]] = None,
    onFallback: Option[Throwable => IO[Unit]] = None
)

object ExecutionOptions {
  def default[A]: ExecutionOptions[A] = ExecutionOptions()

  def withRetry[A](maxRetries: Int): ExecutionOptions[A] =
    ExecutionOptions(retry = Some(maxRetries))

  def withTimeout[A](timeout: FiniteDuration): ExecutionOptions[A] =
    ExecutionOptions(timeout = Some(timeout))

  def withFallback[A](fallback: => IO[A]): ExecutionOptions[A] =
    ExecutionOptions(fallback = Some(fallback))
}

/** Exception thrown when all retry attempts are exhausted.
  *
  * @param message Error message
  * @param totalAttempts Total number of attempts made
  * @param errors List of errors from each attempt
  */
final case class RetryExhaustedException(
    message: String,
    totalAttempts: Int,
    errors: List[Throwable]
) extends RuntimeException(message) {

  /** Get a detailed error report with all attempt errors. */
  def detailedMessage: String = {
    val errorDetails = errors.zipWithIndex.map { case (e, i) =>
      s"  Attempt ${i + 1}: ${e.getClass.getSimpleName}: ${e.getMessage}"
    }.mkString("\n")

    s"$message\n$errorDetails"
  }

  override def toString: String = detailedMessage
}

/** Exception thrown when a module execution times out.
  *
  * @param message Error message
  * @param timeout The timeout duration that was exceeded
  */
final case class ModuleTimeoutException(
    message: String,
    timeout: FiniteDuration
) extends RuntimeException(message) {

  override def toString: String =
    s"ModuleTimeoutException: $message (timeout: ${timeout.toMillis}ms)"
}
