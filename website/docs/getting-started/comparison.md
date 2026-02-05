---
title: "Comparison Guide"
sidebar_position: 2
description: "Side-by-side comparisons of Constellation vs manual Scala code"
---

# Comparison Guide

This page shows side-by-side comparisons of common patterns implemented manually in Scala versus their Constellation equivalents.

## 1. API Composition (Fan-out / Fan-in)

Fetch data from three services in parallel, merge the results, and return a subset of fields.

### Manual Scala

```scala
import cats.effect.IO
import cats.syntax.all._

def getComposite(userId: String): IO[Json] =
  for {
    (profile, activity, prefs) <- (
      profileClient.get(userId),
      activityClient.get(userId),
      prefsClient.get(userId)
    ).parTupled

    merged = profile.deepMerge(activity).deepMerge(prefs)

    // No compile-time check that these fields exist
    result = Json.obj(
      "name"  -> merged.hcursor.get[String]("userName").getOrElse(""),
      "score" -> merged.hcursor.get[Int]("activityScore").getOrElse(0),
      "theme" -> merged.hcursor.get[String]("theme").getOrElse("default")
    )
  } yield result
```

**Issues:** Field names are unchecked strings. A typo in `"userName"` compiles fine but fails at runtime. Parallelism requires explicit `parTupled`.

### Constellation

```constellation
in userId: String

profile  = ProfileService(userId)
activity = ActivityService(userId)
prefs    = PrefsService(userId)

merged = profile + activity + prefs
result = merged[userName, activityScore, theme]

out result
```

**Advantages:** The compiler verifies that `userName`, `activityScore`, and `theme` exist in the merged record. The three service calls run in parallel automatically (they have no data dependencies). A typo in a field name is a compile error.

---

## 2. Resilient API Calls

Call an unreliable external API with retry, backoff, timeout, and fallback.

### Manual Scala

```scala
import cats.effect.IO
import scala.concurrent.duration._
import retry._

def callWithResilience(endpoint: String, default: String): IO[String] = {
  val policy = RetryPolicies
    .limitRetries[IO](3)
    .join(RetryPolicies.exponentialBackoff[IO](100.milliseconds))

  val isWorthRetrying: Throwable => IO[Boolean] =
    _ => IO.pure(true)

  retryingOnAllErrors[String](
    policy = policy,
    onError = (err, details) =>
      IO(logger.warn(s"Attempt ${details.retriesSoFar} failed: ${err.getMessage}"))
  ) {
    IO.race(
      externalApi.call(endpoint),
      IO.sleep(2.seconds) *> IO.raiseError(new TimeoutException("Timed out"))
    ).flatMap {
      case Left(result) => IO.pure(result)
      case Right(_)     => IO.raiseError(new TimeoutException("Timed out"))
    }
  }.handleErrorWith { _ =>
    IO.pure(default)
  }
}
```

**Issues:** ~30 lines of retry/timeout/fallback boilerplate that must be repeated (or abstracted) for every external call. The retry library, timeout mechanism, and fallback logic are all separate concerns wired together manually.

### Constellation

```constellation
in endpoint: String
in default: String

result = ExternalApi(endpoint) with
    retry: 3,
    delay: 100ms,
    backoff: exponential,
    timeout: 2s,
    fallback: default

out result
```

**Advantages:** All resilience concerns are declarative options on the module call. The runtime handles retry loops, backoff scheduling, timeout races, and fallback substitution. Changing the retry count is a one-character edit.

---

## 3. Batch Enrichment

Take a batch of candidate records, merge context data into each, and project specific fields.

### Manual Scala

```scala
import cats.effect.IO
import cats.syntax.all._

case class Item(id: String, value: Int)
case class Context(userId: Int, source: String)
case class Enriched(id: String, value: Int, userId: Int, source: String)
case class Summary(id: String, userId: Int)

def enrich(items: List[Item], ctx: Context): IO[List[Summary]] =
  items.traverse { item =>
    IO.pure(Summary(
      id     = item.id,
      userId = ctx.userId
    ))
  }
```

**Issues:** You must manually define `Enriched` and `Summary` case classes. The field selection in `Summary` is not verified against the merged shape — adding a field to `Context` doesn't automatically make it available for projection.

### Constellation

```constellation
type Item = { id: String, value: Int }
type Context = { userId: Int, source: String }

in items: Candidates<Item>
in context: Context

enriched = items + context
summary = enriched[id, userId]

out summary
```

**Advantages:** The `+` operator merges `Context` fields into each item in the batch. The `[]` projection verifies that `id` and `userId` exist in the merged type. Adding a new field to `Context` automatically makes it available for projection without changing intermediate types.

---

## Summary

| Concern | Manual Scala | Constellation |
|---|---|---|
| **Type safety** | Scala compiler checks function signatures, but field names in JSON/Maps are unchecked | Field-level compile-time validation through the entire pipeline |
| **Parallelism** | Explicit `parTupled` / `parMapN` | Automatic from DAG structure |
| **Resilience** | Manual retry/timeout/fallback wrappers per call | Declarative `with` options |
| **Hot reload** | Recompile + restart | Change `.cst` file, re-run |
| **Observability** | Add logging/metrics manually | Built-in execution traces and DAG visualization |

## Next Steps

- [Tutorial](tutorial.md) — build a pipeline from scratch
- [Cookbook](/docs/cookbook/) — see these patterns in runnable examples
- [Resilient Pipelines](/docs/language/resilient-pipelines) — full reference for resilience options
