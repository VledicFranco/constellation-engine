# Caching

> **Path**: `docs/features/resilience/caching.md`
> **Parent**: [resilience/](./README.md)
> **RFC**: [RFC-004](../../rfcs/rfc-004-cache.md)

Cache module results with configurable TTL and pluggable backends.

## Quick Example

```constellation
# Cache for 15 minutes
user = GetUser(id) with cache: 15min

# Cache with custom backend
user = GetUser(id) with cache: 15min, cache_backend: "redis"
```

## Behavior

1. **Cache key** is computed from module name + input values (content-addressed)
2. **Cache hit** returns stored value, skips module execution
3. **Cache miss** executes module, stores result with TTL
4. **Cache store** happens after successful execution (errors are not cached)

## Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `cache` | Duration | None | TTL for cached results |
| `cache_backend` | String | `"default"` | Backend identifier (see SPI) |

## Execution Order

```
Request
   │
   ▼
┌─────────────┐
│ Cache Check │ ─── hit ──► Return cached value
└──────┬──────┘
       │ miss
       ▼
┌─────────────┐
│   Execute   │
└──────┬──────┘
       │ success
       ▼
┌─────────────┐
│ Cache Store │
└──────┬──────┘
       │
       ▼
   Return result
```

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `lang-compiler` | Validates `cache` option, attaches to DAG | `modules/lang-compiler/.../OptionValidator.scala:142` |
| `runtime` | Cache check/store execution | `modules/runtime/.../CacheExecutor.scala` |
| `runtime` (SPI) | Backend interface | `modules/runtime/.../spi/CacheBackend.scala` |
| `runtime` | In-memory default backend | `modules/runtime/.../InMemoryCacheBackend.scala` |

## Cache Key Computation

Keys are content-addressed from:
- Module name (e.g., `"GetUser"`)
- Input JSON (normalized, sorted keys)

```scala
// Pseudocode
val key = sha256(s"${module.name}:${input.toNormalizedJson}")
```

This means:
- Same input → same key → cache hit
- Different input → different key → cache miss
- Module name is part of key → no collisions across modules

## Backend SPI

Custom backends implement `CacheBackend`:

```scala
trait CacheBackend {
  def get(key: String): IO[Option[CValue]]
  def set(key: String, value: CValue, ttl: FiniteDuration): IO[Unit]
  def invalidate(key: String): IO[Unit]
}
```

See [components/runtime/spi.md](../../components/runtime/spi.md) for implementation guide.

## Metrics

When metrics are enabled, caching emits:

| Metric | Type | Description |
|--------|------|-------------|
| `constellation.cache.hits` | Counter | Cache hits |
| `constellation.cache.misses` | Counter | Cache misses |
| `constellation.cache.stores` | Counter | Successful cache stores |
| `constellation.cache.latency` | Histogram | Cache operation latency |

## Variations

### Short TTL (request deduplication)

```constellation
# Deduplicate within a single execution
result = ExpensiveCall(data) with cache: 1s
```

### Long TTL (static data)

```constellation
# Cache config that rarely changes
config = GetConfig() with cache: 1h
```

### With fallback

```constellation
# Return stale value if refresh fails
user = GetUser(id) with cache: 15min, fallback: cachedUser
```

## Best Practices

1. **Start without caching.** Add `cache` when you've measured the need.
2. **Use short TTLs for changing data.** Stale data causes subtle bugs.
3. **Monitor cache hit rate.** Low hit rate means caching isn't helping.
4. **Consider cache invalidation.** TTL-based expiry only; no manual invalidation from DSL.

## Related

- [retry.md](./retry.md) — Retry interacts with cache (cached errors are not retried)
- [fallback.md](./fallback.md) — Fallback applies after cache miss + execution failure
- [RFC-004](../../rfcs/rfc-004-cache.md) — Original design document
