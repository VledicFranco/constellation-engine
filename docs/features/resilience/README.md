# Resilience

> **Path**: `docs/features/resilience/`
> **Parent**: [features/](../README.md)

Built-in resilience primitives: retry, timeout, fallback, cache, throttle, and error handling.

## Ethos Summary

> Full ethos: [ETHOS.md](./ETHOS.md)

- Resilience is **declarative**, not imperative
- Options are **validated at compile time**
- Defaults are **safe** (no retry, no cache, fail-fast)
- Options **compose orthogonally** (retry + timeout + fallback all work together)

## Contents

| File | Description |
|------|-------------|
| [PHILOSOPHY.md](./PHILOSOPHY.md) | Why resilience is a language feature |
| [ETHOS.md](./ETHOS.md) | Constraints for LLMs working on resilience |
| [retry.md](./retry.md) | Retry failed module calls |
| [timeout.md](./timeout.md) | Maximum execution time |
| [fallback.md](./fallback.md) | Default value on failure |
| [cache.md](./cache.md) | Cache results with TTL |
| [throttle.md](./throttle.md) | Rate limiting |
| [backoff.md](./backoff.md) | Retry delay strategies |
| [error-handling.md](./error-handling.md) | on_error strategies |

## Quick Reference

```constellation
result = GetUser(id) with
    retry: 3,
    delay: 1s,
    backoff: exponential,
    timeout: 5s,
    fallback: { name: "Unknown", id: id },
    cache: 15min
```

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `lang-compiler` | Parses and validates options | `OptionParser.scala`, `OptionValidator.scala` |
| `runtime` | Executes resilience logic | `RetryExecutor.scala`, `CacheExecutor.scala`, `TimeoutExecutor.scala` |
| `runtime` (SPI) | Cache backend interface | `CacheBackend.scala` |

## Feature Map

| Option | RFC | Status | Default |
|--------|-----|--------|---------|
| `retry` | [RFC-001](../../rfcs/rfc-001-retry.md) | Implemented | 0 (no retry) |
| `timeout` | [RFC-002](../../rfcs/rfc-002-timeout.md) | Implemented | None (no timeout) |
| `fallback` | [RFC-003](../../rfcs/rfc-003-fallback.md) | Implemented | None (propagate error) |
| `cache` | [RFC-004](../../rfcs/rfc-004-cache.md) | Implemented | None (no cache) |
| `delay` | [RFC-005](../../rfcs/rfc-005-delay.md) | Implemented | 0 (immediate retry) |
| `backoff` | [RFC-006](../../rfcs/rfc-006-backoff.md) | Implemented | fixed |
| `throttle` | [RFC-007](../../rfcs/rfc-007-throttle.md) | Implemented | None (no limit) |
| `concurrency` | [RFC-008](../../rfcs/rfc-008-concurrency.md) | Implemented | Unlimited |
| `on_error` | [RFC-009](../../rfcs/rfc-009-on-error.md) | Implemented | propagate |
| `lazy` | [RFC-010](../../rfcs/rfc-010-lazy.md) | Implemented | false |
| `priority` | [RFC-011](../../rfcs/rfc-011-priority.md) | Implemented | normal |
