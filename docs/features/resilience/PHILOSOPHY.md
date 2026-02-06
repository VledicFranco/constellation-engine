# Resilience Philosophy

> Why resilience is a language feature, not a library concern.

---

## The Problem

Backend systems call external services. External services fail. The standard response is defensive code:

```scala
// Without Constellation: 40+ lines of boilerplate per call
def getUser(id: String): IO[User] = {
  def attempt(retriesLeft: Int, delay: FiniteDuration): IO[User] = {
    httpClient.get(s"/users/$id").flatMap { response =>
      if (response.status.isSuccess)
        response.as[User]
      else
        IO.raiseError(ApiError(response.status))
    }.handleErrorWith { error =>
      if (retriesLeft > 0)
        IO.sleep(delay) >> attempt(retriesLeft - 1, delay * 2)
      else
        IO.raiseError(error)
    }
  }.timeout(5.seconds)
   .handleError(_ => defaultUser)

  cache.getOrElse(s"user:$id") {
    attempt(3, 1.second).flatTap(u => cache.set(s"user:$id", u, 15.minutes))
  }
}
```

This pattern repeats across every service call with slight variations. The problems:

1. **Obscured intent.** The business logic (get a user) is buried in resilience machinery.
2. **Inconsistent application.** Each developer implements retry differently.
3. **Hard to change.** Updating retry policy means touching every call site.
4. **Not visible.** Reading the pipeline doesn't reveal resilience strategy.

---

## The Bet

Resilience is **cross-cutting infrastructure**, not business logic. It belongs in the orchestration layer, not in module implementations.

By making resilience declarative and part of the language:

```constellation
user = GetUser(id) with retry: 3, timeout: 5s, fallback: defaultUser, cache: 15min
```

We achieve:

1. **Visible policy.** The pipeline shows exactly what resilience is applied.
2. **Consistent behavior.** All retries work the same way.
3. **Central control.** Change retry strategy without touching modules.
4. **Compile-time validation.** Invalid options are caught before execution.

---

## Design Decisions

### 1. Options, Not Wrappers

Resilience is expressed as options on module calls, not as wrapper modules:

```constellation
# YES: Options on the call
result = GetUser(id) with retry: 3

# NO: Wrapper modules (rejected approach)
result = Retry(GetUser(id), 3)
```

**Why:** Options compose naturally and are validated by the compiler. Wrappers would require type gymnastics and obscure the actual module being called.

### 2. Orthogonal Composition

Each option works independently and composes with others:

```constellation
result = GetUser(id) with
    retry: 3,        # retries up to 3 times
    timeout: 5s,     # each attempt times out at 5s
    fallback: {},    # if all attempts fail, use fallback
    cache: 15min     # cache successful results
```

The execution order is: cache check → (execution → timeout → retry) → cache store → fallback.

**Why:** Orthogonal options are easier to understand and test than interleaved behaviors.

### 3. Safe Defaults

All defaults are "do nothing":

| Option | Default | Meaning |
|--------|---------|---------|
| `retry` | 0 | No retry |
| `timeout` | None | No timeout |
| `fallback` | None | Propagate error |
| `cache` | None | No caching |
| `throttle` | None | No rate limit |

**Why:** Unsafe defaults cause production incidents. A module without explicit resilience fails fast, which is easier to debug than unexpected retries or stale cache hits.

### 4. Compile-Time Validation

Invalid options are compile-time errors:

```constellation
result = GetUser(id) with retry: -1       # Error: retry must be >= 0
result = GetUser(id) with timeout: "fast" # Error: timeout must be duration
result = GetUser(id) with fallback: 42    # Error: fallback type must match output
```

**Why:** Runtime validation means production errors. Compile-time validation means development errors.

---

## Trade-Offs

| Decision | Benefit | Cost |
|----------|---------|------|
| Language-level options | Visible, consistent, validated | Can't express every retry strategy |
| Safe defaults | No accidental retries/caching | More verbose for common cases |
| Orthogonal composition | Predictable behavior | Execution order is fixed |
| Compile-time validation | Errors caught early | Stricter than runtime config |

### What We Gave Up

- **Custom retry predicates.** Can't retry only on specific errors (future RFC candidate).
- **Dynamic configuration.** Options are static; can't change retry count at runtime.
- **Per-attempt hooks.** Can't inject logging between retry attempts (use execution listeners instead).

These limitations are intentional. The 90% case is covered by declarative options; the 10% case uses custom modules or execution listeners.

---

## Influences

- **Resilience4j:** Circuit breaker, retry, rate limiter patterns
- **Polly (.NET):** Fluent resilience policy composition
- **Istio:** Declarative retry/timeout at the mesh layer
- **Hystrix:** Bulkhead and fallback patterns

The key insight from these tools: resilience is infrastructure, not business logic. Constellation takes this further by making resilience part of the language syntax.
