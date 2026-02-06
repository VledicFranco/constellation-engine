# Module Call Options

Module call options allow you to control how module calls execute with built-in resilience, caching, and scheduling features. Options are specified using the `with` clause after a module call.

## Syntax

```constellation
result = ModuleName(arg1, arg2) with option1: value1, option2: value2
```

Options are comma-separated key-value pairs. Multiple options can be combined to create sophisticated execution policies.

## Quick Reference

| Option | Type | Description |
|--------|------|-------------|
| `retry` | Integer | Maximum retry attempts on failure |
| `timeout` | Duration | Maximum execution time per attempt |
| `delay` | Duration | Base delay between retries |
| `backoff` | Strategy | How delay increases between retries |
| `fallback` | Expression | Value to use if all retries fail |
| `cache` | Duration | TTL for caching results |
| `cache_backend` | String | Named cache backend to use |
| `throttle` | Rate | Maximum call rate limit |
| `concurrency` | Integer | Maximum parallel executions |
| `on_error` | Strategy | Error handling behavior |
| `lazy` | Boolean | Defer execution until needed |
| `priority` | Level | Scheduling priority hint |

## Option Categories

### Resilience Options

Options for handling failures and ensuring reliable execution:

- **[retry](./options/retry.md)** - Automatic retry on failure
- **[timeout](./options/timeout.md)** - Time limit per execution attempt
- **[delay](./options/delay.md)** - Wait time between retries
- **[backoff](./options/backoff.md)** - Delay increase strategy
- **[fallback](./options/fallback.md)** - Default value on failure

### Caching Options

Options for result caching and storage:

- **[cache](./options/cache.md)** - Result caching with TTL
- **[cache_backend](./options/cache-backend.md)** - Pluggable cache storage

### Rate Control Options

Options for managing execution rate and resources:

- **[throttle](./options/throttle.md)** - Rate limiting
- **[concurrency](./options/concurrency.md)** - Parallel execution limit

### Advanced Options

Options for fine-tuned execution control:

- **[on_error](./options/on-error.md)** - Error handling strategies
- **[lazy](./options/lazy.md)** - Deferred execution
- **[priority](./options/priority.md)** - Scheduling priority

## Value Types

### Duration

Time values with unit suffix:

| Unit | Suffix | Example |
|------|--------|---------|
| Milliseconds | `ms` | `500ms` |
| Seconds | `s` | `30s` |
| Minutes | `min` | `5min` |
| Hours | `h` | `1h` |
| Days | `d` | `1d` |

### Rate

Request rate in format `count/duration`:

```constellation
throttle: 100/1min    # 100 calls per minute
throttle: 10/1s       # 10 calls per second
throttle: 1000/1h     # 1000 calls per hour
```

### Strategies

**Backoff strategies:**
- `fixed` - Constant delay between retries
- `linear` - Delay increases linearly (N × base delay)
- `exponential` - Delay doubles each retry (capped at 30s)

**Error strategies:**
- `propagate` - Re-throw the error (default)
- `skip` - Return zero value for the type
- `log` - Log error and return zero value
- `wrap` - Wrap error in result type

**Priority levels:**
- `critical` - Highest priority
- `high` - Above normal
- `normal` - Default priority
- `low` - Below normal
- `background` - Lowest priority

## Examples

### Basic Retry with Timeout

```constellation
in url: String

response = HttpGet(url) with retry: 3, timeout: 10s
out response
```

If `HttpGet` fails, it will retry up to 3 times. Each attempt has a 10-second timeout.

### Retry with Exponential Backoff

```constellation
in apiEndpoint: String

result = FetchData(apiEndpoint) with
    retry: 5,
    delay: 1s,
    backoff: exponential,
    timeout: 30s

out result
```

Retries with delays of 1s, 2s, 4s, 8s, 16s (exponential backoff, capped at 30s).

### Caching Expensive Operations

```constellation
in userId: String

profile = LoadUserProfile(userId) with cache: 15min

out profile
```

Results are cached for 15 minutes. Subsequent calls with the same `userId` return the cached value.

### Rate-Limited API Calls

```constellation
in requests: List[Request]

results = requests.map(r => ProcessRequest(r) with throttle: 100/1min)

out results
```

Limits processing to 100 requests per minute to respect API rate limits.

### Fallback Values

```constellation
in stockSymbol: String

price = GetStockPrice(stockSymbol) with
    retry: 2,
    timeout: 5s,
    fallback: 0.0

out price
```

If `GetStockPrice` fails after retries, returns `0.0` instead of raising an error.

### Complete Resilient Pipeline

```constellation
in inputData: Record

# Step 1: Validate with quick timeout
validated = Validate(inputData) with timeout: 1s

# Step 2: Transform with retry and caching
transformed = Transform(validated) with
    retry: 3,
    delay: 500ms,
    backoff: exponential,
    cache: 10min

# Step 3: Store with fallback
stored = Store(transformed) with
    retry: 5,
    timeout: 30s,
    fallback: { success: false, reason: "storage unavailable" }

out stored
```

## Option Interactions

### Retry + Timeout

Timeout applies **per attempt**, not total. With `retry: 3, timeout: 10s`, total maximum time is 40s (4 attempts × 10s each).

### Retry + Delay + Backoff

The `delay` option sets the base delay. The `backoff` strategy determines how the delay changes:

| Attempt | Fixed | Linear (1s base) | Exponential (1s base) |
|---------|-------|------------------|----------------------|
| 1 → 2 | 1s | 1s | 1s |
| 2 → 3 | 1s | 2s | 2s |
| 3 → 4 | 1s | 3s | 4s |
| 4 → 5 | 1s | 4s | 8s |
| 5 → 6 | 1s | 5s | 16s |

### Cache + Retry

Caching occurs **after** successful execution. Failed results are not cached. Retries happen before the result is cached.

### Fallback + On_Error

If both are specified, `fallback` takes precedence for providing a default value. Use `on_error` for handling errors differently (logging, wrapping, etc.).

## Warnings and Diagnostics

The compiler provides warnings for potentially incorrect option combinations:

| Warning | Description |
|---------|-------------|
| `delay without retry` | Delay has no effect without retry |
| `backoff without delay` | Backoff requires a base delay |
| `backoff without retry` | Backoff has no effect without retry |
| `cache_backend without cache` | Backend requires cache option |
| `High retry count` | More than 10 retries may indicate a problem |

Errors are raised for invalid values:

| Error | Description |
|-------|-------------|
| Negative retry | Retry count must be non-negative |
| Zero concurrency | Concurrency must be positive |
| Unknown option | Unrecognized option name |
| Duplicate option | Same option specified twice |

## IDE Support

### Autocomplete

After `with`, the IDE suggests available option names. After the colon:
- Duration options show unit completions (`ms`, `s`, `min`, `h`, `d`)
- Strategy options show valid values (`exponential`, `skip`, etc.)
- Priority shows level names (`critical`, `high`, etc.)

### Hover Information

Hover over any option name to see:
- Description of the option
- Type signature
- Usage examples
- Related options

### Diagnostics

Real-time warnings appear for:
- Ineffective option combinations (delay without retry)
- High retry counts (> 10)
- Invalid values

## See Also

- [Individual Option Reference](./options/) - Detailed documentation for each option
- [Resilient Pipelines Guide](./examples/resilient-pipelines.md) - Real-world patterns
- [Orchestration Algebra](./orchestration-algebra.md) - Control flow operators
