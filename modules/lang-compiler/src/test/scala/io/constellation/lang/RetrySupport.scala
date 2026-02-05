package io.constellation.lang

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.{Outcome, Retries}

/** Mixin for AnyFlatSpec test classes that contain timing-sensitive tests.
  *
  * Retries any test tagged with `Retryable` up to 3 times before reporting failure. This prevents
  * flaky CI failures caused by system load affecting timing assertions.
  *
  * Usage:
  * {{{
  * class MyTest extends AnyFlatSpec with RetrySupport {
  *   it should "do something timing-sensitive" taggedAs Retryable in { ... }
  * }
  * }}}
  */
trait RetrySupport extends AnyFlatSpec with Retries {

  override def withFixture(test: NoArgTest): Outcome =
    if isRetryable(test) then withRetry(super.withFixture(test))
    else super.withFixture(test)
}
