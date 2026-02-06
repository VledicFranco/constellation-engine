# Caching Options

> **Path**: `docs/language/options/caching.md`
> **Parent**: [options/](./README.md)

Result caching with TTL and custom backends.

## cache

Cache successful results for a duration.

```constellation
user = GetUser(id) with cache: 15min
config = LoadConfig(env) with cache: 1h
```

| Unit | Example |
|------|---------|
| `ms` | `cache: 500ms` |
| `s` | `cache: 30s` |
| `min` | `cache: 15min` |
| `h` | `cache: 1h` |
| `d` | `cache: 1d` |

### Cache Key

Deterministic hash of:
- Module name
- Sorted input parameters

Same inputs → cache hit.

### Behavior

- Only successful results are cached
- Failures are not cached (allows retry)
- With `retry`: cache happens after success

```constellation
# If first attempt fails, retry; cache on success
data = Fetch(id) with retry: 2, cache: 5min
```

## cache_backend

Use a named cache storage backend.

```constellation
user = GetUser(id) with cache: 15min, cache_backend: "redis"
session = GetSession(token) with cache: 1h, cache_backend: "distributed"
```

**Requires**: `cache` must be set.

### Built-in Backends

| Backend | Description |
|---------|-------------|
| (default) | In-memory LRU cache |

### Custom Backends

Implement `CacheBackend` SPI. See [extensibility/cache-backend.md](../../extensibility/cache-backend.md).

## Cache Statistics

Available at `GET /metrics`:

```json
{
  "cache": {
    "hits": 15234,
    "misses": 892,
    "evictions": 45,
    "hitRate": 0.944
  }
}
```

## Patterns

### Short TTL for Volatile Data
```constellation
price = GetPrice(symbol) with cache: 30s
```

### Long TTL for Static Data
```constellation
schema = GetSchema(tableName) with cache: 1h
```

### Different Backends by Use Case
```constellation
# Fast local cache for hot data
user = GetUser(id) with cache: 5min

# Distributed cache for shared state
session = GetSession(token) with cache: 1h, cache_backend: "redis"
```

### Cache with Fallback
```constellation
# If cache miss and fetch fails, use fallback
config = GetConfig(key) with cache: 1h, fallback: defaultConfig
```
