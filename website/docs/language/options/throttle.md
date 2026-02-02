---
title: "throttle"
sidebar_position: 8
---

# throttle

Limit the rate of module calls.

## Syntax

```constellation
result = Module(args) with throttle: <count>/<duration>
```

**Type:** Rate (count per duration)

## Description

The `throttle` option limits how frequently a module can be called. Calls that exceed the rate limit are queued and executed when capacity becomes available. This is useful for respecting external API rate limits or controlling resource usage.

The rate is specified as `count/duration`, where `count` is the maximum number of calls allowed in the given `duration` window.

## Examples

### API Rate Limiting

```constellation
response = ExternalApi(request) with throttle: 100/1min
```

Maximum 100 calls per minute to the external API.

### Per-Second Limiting

```constellation
result = FastService(data) with throttle: 10/1s
```

Maximum 10 calls per second.

### Hourly Limiting

```constellation
report = GenerateReport(params) with throttle: 1000/1h
```

Maximum 1000 reports per hour.

### Combined with Retry

```constellation
response = RateLimitedApi(request) with
    throttle: 50/1min,
    retry: 3,
    delay: 1s,
    backoff: exponential
```

Rate limit calls and retry on failure with backoff.

## Behavior

The throttle uses a token bucket algorithm:

1. Each `duration` period, `count` tokens are available
2. Each call consumes one token
3. If tokens are available:
   - Consume a token
   - Execute immediately
4. If no tokens available:
   - Wait until a token becomes available
   - Then execute

This allows bursting up to `count` calls instantly, with sustained rate averaging to `count/duration`.

## Rate Format

```
<count>/<duration>

Examples:
  100/1min   - 100 per minute
  10/1s      - 10 per second
  1000/1h    - 1000 per hour
  50/30s     - 50 per 30 seconds
```

## Related Options

- **[concurrency](./concurrency.md)** - Limit parallel executions
- **[retry](./retry.md)** - Retry after rate limit wait

## Per-Module Limiting

Rate limits are tracked per module name. Different modules have independent limits:

```constellation
# These have separate rate limits
a = ServiceA(x) with throttle: 10/1s
b = ServiceB(y) with throttle: 10/1s
```

## Best Practices

- Match throttle rate to external API limits (with margin)
- Use throttle for third-party APIs with documented rate limits
- Combine with retry for handling rate limit errors
- Consider using per-user or per-tenant throttling for fairness
- Monitor throttle wait times to detect capacity issues
