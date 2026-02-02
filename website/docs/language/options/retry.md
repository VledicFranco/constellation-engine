---
title: "retry"
sidebar_position: 1
---

# retry

Automatically retry module execution on failure.

## Syntax

```constellation
result = Module(args) with retry: <count>
```

**Type:** Integer (non-negative)

## Description

The `retry` option specifies the maximum number of retry attempts if a module call fails. The first execution is not counted as a retry, so `retry: 3` means up to 4 total attempts (1 initial + 3 retries).

## Examples

### Basic Retry

```constellation
response = HttpGet(url) with retry: 3
```

If the request fails, retry up to 3 more times before giving up.

### Retry with Delay

```constellation
result = FlakyService(input) with retry: 5, delay: 1s
```

Wait 1 second between retry attempts.

### Retry with Exponential Backoff

```constellation
response = ApiCall(request) with
    retry: 5,
    delay: 500ms,
    backoff: exponential
```

Wait 500ms, 1s, 2s, 4s, 8s between successive retries.

### Retry with Timeout

```constellation
result = SlowOperation(data) with retry: 3, timeout: 10s
```

Each attempt has a 10-second timeout. Total possible time: 40s.

### Retry with Fallback

```constellation
value = GetConfig(key) with retry: 2, fallback: "default"
```

After all retries fail, return "default" instead of raising an error.

## Behavior

1. Execute the module
2. If successful, return the result
3. If failed and retries remaining:
   - Apply delay (if specified)
   - Retry the execution
4. If failed and no retries remaining:
   - Use fallback (if specified)
   - Otherwise, raise `RetryExhaustedException`

## Error Details

When all retries are exhausted, a `RetryExhaustedException` is raised containing:
- Module name
- Total number of attempts
- History of all errors from each attempt

Example error message:
```
FlakyService failed after 4 attempts:
  Attempt 1: Connection timeout
  Attempt 2: Connection timeout
  Attempt 3: Service unavailable
  Attempt 4: Connection timeout
```

## Related Options

- **[delay](./delay.md)** - Time between retries
- **[backoff](./backoff.md)** - How delay increases
- **[timeout](./timeout.md)** - Time limit per attempt
- **[fallback](./fallback.md)** - Default value on failure

## Diagnostics

| Warning/Error | Cause |
|---------------|-------|
| High retry count | More than 10 retries specified |
| Negative value | Retry count must be >= 0 |

## Best Practices

- Use moderate retry counts (2-5) for most operations
- Always pair with `timeout` to prevent hanging
- Consider `delay` with `backoff: exponential` for rate-limited APIs
- Provide a `fallback` for graceful degradation
