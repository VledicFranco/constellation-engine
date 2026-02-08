# RFC-011: Priority

**Status:** Implemented
**Priority:** 4 (Advanced Control)
**Author:** Agent 1
**Created:** 2026-01-25
**Implemented:** 2026-01-26

---

## Summary

Add a `priority` option to module calls that provides hints to the scheduler about execution importance.

---

## Motivation

In complex pipelines, some computations are more time-sensitive than others:
- User-facing requests need lower latency
- Background batch processing can be deprioritized
- Critical path modules should be scheduled first

The `priority` option allows declaring relative importance of module calls, helping the scheduler optimize for latency-sensitive paths.

---

## Syntax

```constellation
result = MyModule(input) with priority: high
```

### Priority Levels

| Level | Description |
|-------|-------------|
| `critical` | Highest priority, minimal queuing |
| `high` | Above normal, preferred scheduling |
| `normal` | Default priority |
| `low` | Below normal, yield to others |
| `background` | Lowest priority, run when idle |

Or numeric:

```constellation
result = MyModule(input) with priority: 10  # Higher = more important
```

---

## Semantics

### Behavior

Priority affects scheduling decisions:
1. When resources are constrained (concurrency limits, rate limits)
2. Higher priority calls are processed first
3. Priority does not guarantee execution order in general

### Priority Is a Hint

Priority is advisory, not mandatory:
- Scheduler may ignore hints when impractical
- Does not affect dependency order
- Does not preempt running executions

### Interaction with Other Options

| Option | Interaction |
|--------|-------------|
| `concurrency` | Higher priority claims permits first |
| `throttle` | Higher priority gets rate limit slots first |
| `timeout` | Priority doesn't affect timeout |

### Priority Inheritance

When a high-priority call depends on other calls, should dependencies inherit priority?

Default: No inheritance. Each call has its own priority.

---

## Implementation Notes

### Parser Changes

```
Priority ::= 'critical' | 'high' | 'normal' | 'low' | 'background' | Integer
```

### AST Changes

```scala
enum PriorityLevel:
  case Critical
  case High
  case Normal
  case Low
  case Background
  case Custom(value: Int)

case class ModuleCallOptions(
  // ...
  priority: Option[PriorityLevel] = None,  // NEW
)
```

### Runtime Changes

Using a priority queue for scheduling:

```scala
import scala.collection.mutable.PriorityQueue

case class ScheduledTask[A](
  task: IO[A],
  priority: Int,
  submittedAt: Long
)

class PriorityScheduler {
  private val queue = PriorityQueue[ScheduledTask[_]]()(
    Ordering.by(t => (t.priority, -t.submittedAt))  // Higher priority first, then FIFO
  )

  def submit[A](task: IO[A], priority: PriorityLevel): IO[A] = {
    val scheduled = ScheduledTask(task, priority.toInt, System.currentTimeMillis())
    // Add to queue and process...
  }
}
```

### Priority-Aware Semaphore

For concurrency limits:

```scala
class PrioritySemaphore(permits: Int) {
  private val waiters = PriorityQueue[Deferred[IO, Unit]]()

  def acquireWithPriority(priority: Int): IO[Unit] = {
    // Higher priority waiters get permits first
  }
}
```

---

## Examples

### User-Facing vs Batch

```constellation
in request: Request

# User-facing path is high priority
userResult = ProcessUserRequest(request) with priority: high

# Background analytics is low priority
analytics = RecordAnalytics(request) with priority: background

out userResult
```

### Critical Path Optimization

```constellation
in query: String

# Critical path for response
mainResult = CoreSearch(query) with priority: critical

# Optional enrichments
enrichment1 = Enrich1(mainResult) with priority: low
enrichment2 = Enrich2(mainResult) with priority: low

out { mainResult, enrichment1, enrichment2 }
```

### Tiered Processing

```constellation
in requests: List<Request>

# Premium users get higher priority
results = requests | map(req =>
    Process(req) with priority: (if req.isPremium then high else normal)
)

out results
```

---

## Alternatives Considered

### 1. Deadline-Based Priority

```constellation
result = MyModule(input) with deadline: 100ms
```

Scheduler ensures call completes within deadline.

Deferred: More complex to implement correctly. Can add as enhancement.

### 2. Weight-Based Priority

```constellation
result = MyModule(input) with weight: 0.8
```

Relative weight for fair scheduling.

Rejected: Less intuitive than named levels. Numeric priority already allows fine-tuning.

### 3. Priority Inheritance

```constellation
dag MyDag with priority: high
# All calls inherit high priority
```

Deferred: Can add DAG-level defaults later.

---

## Implementation

The Global Priority Scheduler was implemented in commit `0df81bd`. Key files:

| File | Purpose |
|------|---------|
| `modules/runtime/.../execution/GlobalScheduler.scala` | Core scheduler with bounded/unbounded modes |
| `modules/runtime/.../Runtime.scala` | DAG execution with scheduler integration |
| `modules/lang-compiler/.../ModuleOptionsExecutor.scala` | Priority option handling |
| `modules/http-api/.../ConstellationServer.scala` | Scheduler configuration from env vars |

### Priority Values

| Level | Value |
|-------|-------|
| `critical` | 100 |
| `high` | 80 |
| `normal` | 50 |
| `low` | 20 |
| `background` | 0 |

### Resolved Open Questions

1. **Resource allocation**: Priority only affects scheduling order, not resource allocation.
2. **Priority inversion**: Addressed via starvation prevention (aging mechanism). Tasks waiting >30s get priority boost.
3. **Priority-based timeouts**: Not implemented. Use explicit `timeout` option instead.
4. **Monitoring**: `/metrics` endpoint includes scheduler stats (activeCount, queuedCount, starvationPromotions, etc.)

For full details, see [Global Scheduler Documentation](../global-scheduler.md).

---

## References

- [RFC-007: Throttle](./rfc-007-throttle.md)
- [RFC-008: Concurrency](./rfc-008-concurrency.md)
- [Priority Scheduling - Wikipedia](https://en.wikipedia.org/wiki/Fixed-priority_pre-emptive_scheduling)
