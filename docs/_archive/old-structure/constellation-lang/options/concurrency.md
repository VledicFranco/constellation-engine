# concurrency

Limit the number of parallel executions of a module.

## Syntax

```constellation
result = Module(args) with concurrency: <limit>
```

**Type:** Integer (positive)

## Description

The `concurrency` option limits how many instances of a module can execute simultaneously. When the limit is reached, additional calls wait until a slot becomes available. This is useful for controlling resource usage and preventing overload.

## Examples

### Limit Parallel Database Connections

```constellation
data = DatabaseQuery(sql) with concurrency: 5
```

Maximum 5 concurrent database queries.

### Single-Threaded Execution

```constellation
result = NotThreadSafe(input) with concurrency: 1
```

Ensure only one call executes at a time (mutex behavior).

### Combined with Throttle

```constellation
response = ExternalApi(request) with
    concurrency: 10,
    throttle: 100/1min
```

Maximum 10 concurrent calls AND maximum 100 calls per minute.

### Limit Resource-Intensive Operations

```constellation
processed = HeavyComputation(data) with concurrency: 2
```

Limit CPU-intensive operations to 2 parallel instances.

## Behavior

The concurrency limiter uses a semaphore:

1. When a call is made, try to acquire a permit
2. If a permit is available:
   - Acquire it
   - Execute the module
   - Release the permit when done
3. If no permit available:
   - Wait until a permit is released
   - Then proceed with execution

## Concurrency vs Throttle

| Aspect | concurrency | throttle |
|--------|-------------|----------|
| Limits | Parallel executions | Calls per time window |
| Queueing | When all slots busy | When rate exceeded |
| Use case | Resource protection | Rate limiting |

They can be combined:

```constellation
# Max 5 parallel, max 100 per minute
result = Service(x) with concurrency: 5, throttle: 100/1min
```

## Related Options

- **[throttle](./throttle.md)** - Rate limiting
- **[timeout](./timeout.md)** - Time limit per execution

## Per-Module Limiting

Concurrency limits are tracked per module name:

```constellation
# These have separate concurrency limits
a = ServiceA(x) with concurrency: 5
b = ServiceB(y) with concurrency: 5
```

## Diagnostics

| Error | Cause |
|-------|-------|
| Zero concurrency | Concurrency must be positive (>= 1) |

## Best Practices

- Set concurrency based on downstream service capacity
- Use `concurrency: 1` for non-thread-safe operations
- Combine with timeout to prevent deadlocks
- Monitor queue depth to detect capacity issues
- Consider the total concurrency across all modules
