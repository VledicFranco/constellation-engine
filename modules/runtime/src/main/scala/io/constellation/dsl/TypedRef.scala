package io.constellation.dsl

import java.util.UUID

import scala.concurrent.duration.FiniteDuration

import io.constellation.ModuleCallOptions

/** A handle to a typed data node in a pipeline under construction.
  *
  * Carries the UUID of the underlying [[io.constellation.DataNodeSpec]] and any accumulated
  * [[ModuleCallOptions]] for the module that produces it. All option methods return a new
  * `TypedRef[A]` — accumulation is immutable.
  */
sealed trait TypedRef[A] {

  /** Retry the producing module up to `n` times on failure. */
  def retry(n: Int): TypedRef[A]

  /** Abort the producing module if it does not complete within `d`. */
  def timeout(d: FiniteDuration): TypedRef[A]

  /** Delay execution of the producing module by `d`. */
  def delay(d: FiniteDuration): TypedRef[A]

  /** Backoff strategy for retries: `"fixed"`, `"linear"`, or `"exponential"`. */
  def backoff(strategy: String): TypedRef[A]

  /** Cache the output of the producing module for `d`. */
  def cache(d: FiniteDuration): TypedRef[A]

  /** Named cache backend to use (overrides the default). */
  def cacheBackend(name: String): TypedRef[A]

  /** Throttle calls to the producing module: at most `count` per `per` window. */
  def throttle(count: Int, per: FiniteDuration): TypedRef[A]

  /** Maximum concurrent executions of the producing module. */
  def concurrency(n: Int): TypedRef[A]

  /** Error handling strategy: `"propagate"`, `"skip"`, `"log"`, or `"wrap"`. */
  def onError(strategy: String): TypedRef[A]

  /** Evaluate the producing module lazily (only when its output is consumed). */
  def lazyEval: TypedRef[A]

  /** Scheduling priority for the producing module (higher value = higher priority). */
  def priority(p: Int): TypedRef[A]
}

private[dsl] final class TypedRefImpl[A](
    val uuid: UUID,
    val options: ModuleCallOptions
) extends TypedRef[A] {

  private def withOptions(f: ModuleCallOptions => ModuleCallOptions): TypedRef[A] =
    new TypedRefImpl[A](uuid, f(options))

  def retry(n: Int): TypedRef[A] =
    withOptions(_.copy(retry = Some(n)))

  def timeout(d: FiniteDuration): TypedRef[A] =
    withOptions(_.copy(timeoutMs = Some(d.toMillis)))

  def delay(d: FiniteDuration): TypedRef[A] =
    withOptions(_.copy(delayMs = Some(d.toMillis)))

  def backoff(strategy: String): TypedRef[A] =
    withOptions(_.copy(backoff = Some(strategy)))

  def cache(d: FiniteDuration): TypedRef[A] =
    withOptions(_.copy(cacheMs = Some(d.toMillis)))

  def cacheBackend(name: String): TypedRef[A] =
    withOptions(_.copy(cacheBackend = Some(name)))

  def throttle(count: Int, per: FiniteDuration): TypedRef[A] =
    withOptions(_.copy(throttleCount = Some(count), throttlePerMs = Some(per.toMillis)))

  def concurrency(n: Int): TypedRef[A] =
    withOptions(_.copy(concurrency = Some(n)))

  def onError(strategy: String): TypedRef[A] =
    withOptions(_.copy(onError = Some(strategy)))

  def lazyEval: TypedRef[A] =
    withOptions(_.copy(lazyEval = Some(true)))

  def priority(p: Int): TypedRef[A] =
    withOptions(_.copy(priority = Some(p)))
}

private[dsl] object TypedRef {

  /** Create a fresh [[TypedRef]] for a data node with no accumulated options. */
  def apply[A](uuid: UUID): TypedRef[A] =
    new TypedRefImpl[A](uuid, ModuleCallOptions.empty)
}
