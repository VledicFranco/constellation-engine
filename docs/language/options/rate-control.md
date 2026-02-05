# Rate Control Options

> **Path**: `docs/language/options/rate-control.md`
> **Parent**: [options/](./README.md)

Options for limiting execution rate and concurrency.

## throttle

Limit calls per time window (rate limiting).

```constellation
result = ExternalAPI(req) with throttle: 100/1min
```

### Syntax

```
throttle: count/duration
```

| Example | Meaning |
|---------|---------|
| `100/1min` | 100 calls per minute |
| `10/1s` | 10 calls per second |
| `1000/1h` | 1000 calls per hour |

### Behavior

- Uses token bucket algorithm
- Excess calls are queued (not rejected)
- Queue processes as tokens become available

### Use Cases

```constellation
# Respect external API limits
data = ThirdPartyAPI(req) with throttle: 60/1min

# Prevent database overload
results = DbQuery(params) with throttle: 1000/1s
```

## concurrency

Limit parallel executions of a module.

```constellation
result = HeavyCompute(data) with concurrency: 5
```

### Behavior

- Semaphore-based limiting
- `concurrency: 1` = mutex (serial execution)
- Excess calls wait for a slot

### Use Cases

```constellation
# Limit connections to a service
data = RemoteService(req) with concurrency: 10

# Serialize access to shared resource
result = UpdateCounter(delta) with concurrency: 1

# Limit memory-heavy operations
report = GenerateReport(data) with concurrency: 2
```

## Combining throttle and concurrency

Different concerns:
- `throttle`: rate over time
- `concurrency`: simultaneous executions

```constellation
# Max 5 concurrent, max 100/minute
data = API(req) with concurrency: 5, throttle: 100/1min
```

## With Other Options

### Rate-Limited with Retry
```constellation
data = FlakeyAPI(req)
  with throttle: 50/1min,
       retry: 2,
       timeout: 10s
```

### Concurrency with Timeout
```constellation
result = SlowQuery(params)
  with concurrency: 3,
       timeout: 30s
```

## Per-Module Tracking

Limits are tracked per module name:

```constellation
# These have separate limits
userA = GetUser(idA) with concurrency: 5
userB = GetUser(idB) with concurrency: 5
# Both share the "GetUser" concurrency pool

# This has its own limit
order = GetOrder(orderId) with concurrency: 10
# Separate "GetOrder" concurrency pool
```
