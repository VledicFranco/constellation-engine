# cache

Cache module results for a specified duration.

## Syntax

```constellation
result = Module(args) with cache: <duration>
```

**Type:** Duration (`ms`, `s`, `min`, `h`, `d`)

## Description

The `cache` option enables result caching for a module call. When enabled, the first successful call stores its result, and subsequent calls with the same inputs return the cached value until the TTL (time-to-live) expires.

The cache key is computed from the module name and all input values, ensuring that different inputs produce different cache entries.

## Examples

### Basic Caching

```constellation
profile = LoadUserProfile(userId) with cache: 15min
```

Cache user profiles for 15 minutes.

### Short Cache for Fast Lookups

```constellation
config = GetConfig(key) with cache: 30s
```

Brief caching for frequently accessed configuration.

### Long Cache for Static Data

```constellation
schema = LoadSchema(tableName) with cache: 1d
```

Cache schema definitions for a full day.

### Cache with Retry

```constellation
data = FetchData(id) with retry: 3, cache: 10min
```

Retries happen before caching. Only successful results are cached.

### Cache with Custom Backend

```constellation
session = LoadSession(token) with cache: 1h, cache_backend: "redis"
```

Use a named Redis backend instead of the default in-memory cache.

## Behavior

1. Generate cache key from module name and input values
2. Check cache for existing entry
3. If cached value exists and not expired:
   - Return cached value (cache hit)
4. If no cached value or expired:
   - Execute the module (with retry if configured)
   - If successful, store result in cache with TTL
   - Return the result

### Cache Key Generation

Cache keys are deterministic hashes of:
- Module name
- Input parameter names and values (sorted alphabetically)

This ensures:
- Same inputs always produce the same cache key
- Different inputs produce different cache keys
- Order of parameters doesn't matter

## Duration Units

| Unit | Suffix | Example |
|------|--------|---------|
| Milliseconds | `ms` | `500ms` |
| Seconds | `s` | `30s` |
| Minutes | `min` | `5min`, `15min` |
| Hours | `h` | `1h`, `24h` |
| Days | `d` | `1d`, `7d` |

## Cache Statistics

The runtime tracks cache statistics:
- **hits** - Number of cache hits
- **misses** - Number of cache misses
- **evictions** - Number of expired/evicted entries
- **entries** - Current number of cached entries
- **hitRate** - Ratio of hits to total accesses

Access via the `/metrics` endpoint:
```bash
curl http://localhost:8080/metrics | jq .cache
```

## Related Options

- **[cache_backend](./cache-backend.md)** - Specify named cache storage
- **[retry](./retry.md)** - Retry before caching result
- **[timeout](./timeout.md)** - Timeout before caching result

## Best Practices

- Cache expensive or slow operations
- Use shorter TTLs for frequently changing data
- Use longer TTLs for stable reference data
- Consider cache invalidation strategies for critical data
- Monitor cache hit rates to tune TTL values
- Only cache pure functions (same inputs = same output)
