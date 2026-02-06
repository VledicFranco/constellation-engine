---
title: "timeout"
sidebar_position: 2
description: "Set a maximum execution time for a module call, cancelling the operation if it exceeds the limit."
---

# timeout

Set a maximum execution time for a module call.

## Syntax

```constellation
result = Module(args) with timeout: <duration>
```

**Type:** Duration (`ms`, `s`, `min`, `h`, `d`)

## Description

The `timeout` option sets the maximum time a module call can execute before being cancelled. When combined with `retry`, the timeout applies **per attempt**, not to the total execution time.

## Examples

### Basic Timeout

```constellation
response = HttpRequest(url) with timeout: 30s
```

If the request takes longer than 30 seconds, it's cancelled with a timeout error.

### Timeout with Retry

```constellation
result = RemoteCall(params) with timeout: 10s, retry: 3
```

Each of the 4 possible attempts (1 initial + 3 retries) has a 10-second timeout. Maximum total time: 40 seconds.

### Short Timeout for Fast Services

```constellation
cached = GetFromCache(key) with timeout: 100ms
```

Fail fast if the cache doesn't respond within 100ms.

### Timeout with Fallback

```constellation
data = SlowOperation(input) with timeout: 5s, fallback: defaultData
```

If the operation times out, use `defaultData` instead of failing.

## Behavior

1. Start the module execution
2. Start a timer for the specified duration
3. If the module completes before the timer:
   - Cancel the timer
   - Return the result
4. If the timer fires before the module completes:
   - Cancel the module execution
   - Raise `ModuleTimeoutException`

## Error Details

When a timeout occurs, a `ModuleTimeoutException` is raised containing:
- Module name
- Timeout duration
- Descriptive message

Example error message:
```
Module HttpRequest timed out after 30s
```

## Duration Units

| Unit | Suffix | Example |
|------|--------|---------|
| Milliseconds | `ms` | `100ms`, `500ms` |
| Seconds | `s` | `1s`, `30s` |
| Minutes | `min` | `1min`, `5min` |
| Hours | `h` | `1h`, `2h` |
| Days | `d` | `1d` |

## Related Options

- **[retry](./retry.md)** - Retry after timeout
- **[fallback](./fallback.md)** - Default value on timeout
- **[delay](./delay.md)** - Wait between retried timeouts

## Best Practices

- Always set a timeout for network operations
- Use shorter timeouts with retry for transient failures
- Consider the downstream impact of long timeouts
- Set timeouts based on expected response times plus margin
