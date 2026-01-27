# RFC-004: Cache

**Status:** Draft
**Priority:** 1 (Core Resilience)
**Author:** Agent 1
**Created:** 2026-01-25

---

## Summary

Add a `cache` option to module calls that caches results with a configurable TTL and pluggable storage backends.

---

## Motivation

Many module calls are:
- Expensive to compute (ML inference, complex aggregations)
- Idempotent for the same inputs
- Called repeatedly with the same arguments

Caching these results improves performance and reduces load on external services. This RFC proposes language-level caching with:
- Per-call TTL configuration
- Pluggable storage backends
- Automatic cache key generation

---

## Syntax

### Basic Usage

```constellation
result = MyModule(input) with cache: 5min
```

### With Backend Selection

```constellation
# Use default (in-memory) cache
result = MyModule(input) with cache: 5min

# Use specific backend (configured at DAG level)
result = MyModule(input) with cache: 5min, cache_backend: "redis"
```

### Duration Format

| Unit | Syntax | Example |
|------|--------|---------|
| Seconds | `s` | `cache: 30s` |
| Minutes | `min` | `cache: 5min` |
| Hours | `h` | `cache: 1h` |
| Days | `d` | `cache: 7d` |

---

## Semantics

### Behavior

1. Compute cache key from module name + input values
2. Check cache for existing entry
3. If cache hit and not expired, return cached value
4. If cache miss or expired, execute module and store result
5. Only successful results are cached

### Cache Key Generation

Keys are computed deterministically from:
- Module name
- All input values (serialized to canonical form)
- Optionally: version or hash of module implementation

```
key = hash(moduleName + canonicalSerialize(inputs))
```

### Interaction with Other Options

| Option | Interaction |
|--------|-------------|
| `retry` | Only successful (post-retry) results are cached |
| `timeout` | Timeout errors are NOT cached |
| `fallback` | Fallback results are NOT cached |

### Cache Invalidation

- TTL-based expiration (automatic)
- Manual invalidation via runtime API
- Version-based invalidation (when module changes)

---

## Pluggable Backends

### Backend Interface

```scala
trait CacheBackend {
  def get(key: String): IO[Option[CValue]]
  def set(key: String, value: CValue, ttl: FiniteDuration): IO[Unit]
  def delete(key: String): IO[Unit]
  def clear(): IO[Unit]
}
```

### Built-in Backends

| Backend | Description | Use Case |
|---------|-------------|----------|
| `memory` | In-process ConcurrentHashMap | Development, single instance |
| `caffeine` | Caffeine cache with eviction | Production, single instance |

### External Backends (Plugins)

| Backend | Description | Use Case |
|---------|-------------|----------|
| `redis` | Redis cluster | Distributed, high availability |
| `memcached` | Memcached | Distributed, simple KV |

### Backend Configuration

At DAG level or runtime configuration:

```scala
val cacheConfig = CacheConfig(
  defaultBackend = "caffeine",
  backends = Map(
    "memory" -> MemoryCacheBackend(),
    "caffeine" -> CaffeineCacheBackend(maxSize = 10000),
    "redis" -> RedisCacheBackend(host = "localhost", port = 6379)
  )
)
```

---

## Implementation Notes

### Parser Changes

```
Option         ::= Identifier ':' OptionValue
OptionValue    ::= Integer | Duration | Expression | Identifier
```

### AST Changes

```scala
case class ModuleCallOptions(
  // ...
  cache: Option[Duration] = None,      // NEW
  cacheBackend: Option[String] = None  // NEW (optional)
)
```

### Runtime Changes

```scala
def executeWithCache[A](
  module: Module,
  inputs: Map[String, CValue],
  ttl: FiniteDuration,
  backend: CacheBackend
): IO[A] = {
  val key = computeCacheKey(module.name, inputs)

  backend.get(key).flatMap {
    case Some(cached) => IO.pure(cached.asInstanceOf[A])
    case None =>
      module.run(inputs).flatTap { result =>
        backend.set(key, result, ttl)
      }
  }
}
```

### Metrics

Track cache performance:
- Hit rate
- Miss rate
- Eviction count
- Average lookup latency

---

## Examples

### Basic Caching

```constellation
in text: String

# Cache embeddings for 1 hour
embedding = GetEmbedding(text) with cache: 1h

out embedding
```

### Cache with Retry

```constellation
in query: String

# Retry on failure, cache successful results
result = SearchAPI(query) with retry: 3, cache: 5min

out result
```

### Different TTLs

```constellation
in userId: String
in productId: String

# User data changes rarely - cache longer
user = GetUser(userId) with cache: 1h

# Product data changes frequently - cache shorter
product = GetProduct(productId) with cache: 5min

out { user, product }
```

### Backend Selection

```constellation
in sessionId: String

# Use Redis for session data (shared across instances)
session = GetSession(sessionId) with cache: 30min, cache_backend: "redis"

out session
```

---

## Alternatives Considered

### 1. Annotation-Based Caching

```constellation
@cached(ttl = "5min")
def GetEmbedding(text: String): Embedding
```

Rejected: Caching is a call-site concern, not a module definition concern.

### 2. Global Cache Configuration

Configure all caching at DAG level.

Rejected: Different calls have different caching needs.

### 3. Cache Key Override

```constellation
result = MyModule(input) with cache: 5min, cache_key: customKey
```

Deferred: Add if automatic key generation proves insufficient.

---

## Open Questions

1. Should we support cache warming (pre-populate cache)?
2. How to handle cache stampede (many concurrent misses)?
3. Should cache entries be compressed for large values?
4. How to handle serialization for complex types?

---

## References

- [RFC-001: Retry](./rfc-001-retry.md)
- [RFC-002: Timeout](./rfc-002-timeout.md)
- [RFC-003: Fallback](./rfc-003-fallback.md)
