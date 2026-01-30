# RFC-008: Concurrency

**Status:** Implemented
**Priority:** 3 (Rate Control)
**Author:** Agent 1
**Created:** 2026-01-25

---

## Summary

Add a `concurrency` option to module calls that limits the number of simultaneous executions.

---

## Motivation

Some modules have resource constraints:
- Limited connection pool to a database
- GPU memory limits for ML inference
- License restrictions on concurrent users
- Memory-intensive operations that can't all run at once

The `concurrency` option limits how many instances of a module can run simultaneously.

---

## Syntax

```constellation
result = MyModule(input) with concurrency: 5
```

Where `5` is the maximum number of concurrent executions allowed.

---

## Semantics

### Behavior

1. Before executing, acquire a permit from the semaphore
2. If a permit is available, execute immediately
3. If no permits available, wait until one is released
4. Execute the module call
5. Release the permit when done (success or failure)

### Scope

Concurrency is tracked per module name:

```constellation
# Both calls share the same concurrency limit
result1 = HeavyModule(input1) with concurrency: 3
result2 = HeavyModule(input2) with concurrency: 3
# Max 3 HeavyModule calls can run at any time
```

### Comparison with Throttle

| Aspect | Throttle | Concurrency |
|--------|----------|-------------|
| Limits | Calls per time period | Active executions |
| Unit | `100/1min` | `5` |
| Question answered | "How fast?" | "How many at once?" |
| Use case | API rate limits | Resource limits |

### Interaction with Other Options

| Option | Interaction |
|--------|-------------|
| `throttle` | Both apply: rate-limited AND concurrency-limited |
| `timeout` | Timeout includes wait time for concurrency slot |
| `retry` | Each retry attempt needs a concurrency slot |

---

## Implementation Notes

### Parser Changes

Concurrency is just an integer option:

```
Option ::= 'concurrency' ':' Integer
```

### AST Changes

```scala
case class ModuleCallOptions(
  // ...
  concurrency: Option[Int] = None,  // NEW
)
```

### Runtime Changes

```scala
import cats.effect.std.Semaphore

class ConcurrencyLimiter(maxConcurrent: Int) {
  private val semaphore: IO[Semaphore[IO]] = Semaphore[IO](maxConcurrent).memoize

  def withPermit[A](action: IO[A]): IO[A] = {
    semaphore.flatMap(_.permit.use(_ => action))
  }
}

def executeWithConcurrency[A](
  module: Module,
  inputs: Map[String, CValue],
  limiter: ConcurrencyLimiter
): IO[A] = {
  limiter.withPermit(module.run(inputs))
}
```

### Shared Concurrency Limiters

Limiters are shared per module name:

```scala
class ConcurrencyRegistry {
  private val limiters = ConcurrentHashMap[String, ConcurrencyLimiter]()

  def getOrCreate(moduleName: String, maxConcurrent: Int): ConcurrencyLimiter = {
    limiters.computeIfAbsent(moduleName, _ => new ConcurrencyLimiter(maxConcurrent))
  }
}
```

---

## Examples

### Database Connection Limit

```constellation
in queries: List<Query>

# Database has 10 connection pool limit
results = queries | map(q => QueryDatabase(q) with concurrency: 10)

out results
```

### GPU Inference

```constellation
in images: List<Image>

# Only 2 concurrent GPU inference operations
predictions = images | map(img => MLInference(img) with concurrency: 2)

out predictions
```

### Combined with Throttle

```constellation
in requests: List<Request>

# API allows 100 req/min AND max 5 concurrent connections
responses = requests | map(r => CallAPI(r) with
    throttle: 100/1min,
    concurrency: 5
)

out responses
```

### Memory-Intensive Operations

```constellation
in documents: List<Document>

# Limit concurrent processing to avoid OOM
processed = documents | map(doc =>
    HeavyProcessing(doc) with concurrency: 3
)

out processed
```

---

## Alternatives Considered

### 1. Global Concurrency Configuration

```scala
val dagConfig = DagConfig(
  concurrency = Map("MLInference" -> 2)
)
```

Rejected: Concurrency limits are best declared at call site for visibility.

### 2. Concurrency at DAG Level

```constellation
# Limit total concurrent modules in DAG
dag MyDag with max_concurrency: 10
```

Deferred: DAG-level concurrency is different from per-module concurrency. Can add later if needed.

### 3. Fair vs FIFO Queuing

Allow configuration of how waiting tasks are ordered.

Deferred: FIFO is sensible default. Can add `concurrency_order: fair` later if needed.

---

## Open Questions

1. How to handle concurrency limits across distributed instances?
2. Should waiting for concurrency count against `timeout`?
3. Should we support priority-based concurrency queuing?
4. How to expose concurrency metrics (waiting count, utilization)?

---

## References

- [RFC-007: Throttle](./rfc-007-throttle.md)
- [Semaphore - Cats Effect](https://typelevel.org/cats-effect/docs/std/semaphore)
