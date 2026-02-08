# External API Integration

**Priority:** 10 (Lower)
**Target Level:** Scala module
**Status:** Not Implemented

---

## Overview

ML pipelines often need to call external APIs—third-party ML services, enrichment APIs, or internal microservices. Robust API integration requires retries, circuit breakers, and timeout handling.

---

## Key Patterns

### Retry with Exponential Backoff

```scala
import retry._
import cats.effect.IO

def callWithRetry[A](call: IO[A]): IO[A] = {
  val policy = RetryPolicies.limitRetries[IO](3) |+|
    RetryPolicies.exponentialBackoff[IO](100.millis)

  retryingOnAllErrors(
    policy = policy,
    onError = (err, details) => IO(logger.warn(s"Retry ${details.retriesSoFar}: $err"))
  )(call)
}
```

### Circuit Breaker

```scala
import io.github.resilience4j.circuitbreaker.CircuitBreaker

class ResilientApiClient(underlying: ApiClient) {
  private val circuitBreaker = CircuitBreaker.ofDefaults("external-api")

  def call[A](request: Request): IO[A] = {
    IO.fromCallable(() =>
      circuitBreaker.executeCallable(() =>
        underlying.call(request).unsafeRunSync()
      )
    )
  }
}
```

### Timeout Handling

```scala
def callWithTimeout[A](call: IO[A], timeout: FiniteDuration, fallback: A): IO[A] = {
  call.timeout(timeout).handleError(_ => fallback)
}
```

---

## Generic HTTP Client

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/http/ResilientHttpClient.scala

package io.constellation.ml.http

import cats.effect.IO
import org.http4s.client.Client

class ResilientHttpClient(
  underlying: Client[IO],
  maxRetries: Int = 3,
  baseDelay: FiniteDuration = 100.millis,
  timeout: FiniteDuration = 5.seconds
) {

  def get[A](url: String)(implicit decoder: Decoder[A]): IO[A] = {
    callWithRetry(
      underlying.expect[A](url).timeout(timeout)
    )
  }

  def post[Req, Res](url: String, body: Req)(
    implicit encoder: Encoder[Req], decoder: Decoder[Res]
  ): IO[Res] = {
    callWithRetry(
      underlying.expect[Res](
        Request[IO](Method.POST, Uri.unsafeFromString(url))
          .withEntity(body.asJson)
      ).timeout(timeout)
    )
  }

  private def callWithRetry[A](call: IO[A]): IO[A] = {
    def loop(remaining: Int, delay: FiniteDuration): IO[A] = {
      call.handleErrorWith { error =>
        if (remaining > 0) {
          IO.sleep(delay) *> loop(remaining - 1, delay * 2)
        } else {
          IO.raiseError(error)
        }
      }
    }
    loop(maxRetries, baseDelay)
  }
}
```

---

## Configuration

```hocon
constellation.external-apis {
  default {
    timeout = 5s
    max-retries = 3
    base-delay = 100ms
    circuit-breaker {
      failure-rate-threshold = 50
      wait-duration = 30s
    }
  }

  services {
    enrichment-api {
      url = "https://api.enrichment.com"
      timeout = 10s
    }
  }
}
```

---

## Implementation Checklist

- [ ] Implement `ResilientHttpClient`
- [ ] Add circuit breaker integration
- [ ] Add connection pooling
- [ ] Create configuration loader
- [ ] Write integration tests

---

## Related Documents

- [Model Inference](./02-model-inference.md) - External model servers
- [Feature Store](./04-feature-store.md) - External feature services
