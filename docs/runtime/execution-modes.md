# Execution Modes

> **Path**: `docs/runtime/execution-modes.md`
> **Parent**: [runtime/](./README.md)

Three patterns for pipeline execution.

## Hot Execution

Compile and execute in one request.

```http
POST /run
{
  "source": "in x: String\nout x",
  "inputs": { "x": "hello" }
}
```

**Response:**
```json
{
  "outputs": { "x": "hello" },
  "structuralHash": "sha256:abc123...",
  "compileTimeMs": 45,
  "executeTimeMs": 12
}
```

### Characteristics

| Aspect | Value |
|--------|-------|
| Latency | ~50-100ms (compile + execute) |
| Caching | Source is compiled each request |
| Use case | Development, ad-hoc queries |

### When to Use

- Interactive development
- One-time transformations
- Dynamic pipeline generation
- Testing and debugging

## Cold Execution

Compile once, execute by reference.

### Step 1: Compile and Store

```http
POST /compile
{
  "source": "in orderId: String\norder = GetOrder(orderId)\nout order",
  "name": "order-lookup"
}
```

**Response:**
```json
{
  "structuralHash": "sha256:def456...",
  "alias": "order-lookup"
}
```

### Step 2: Execute by Reference

```http
POST /execute
{
  "ref": "order-lookup",
  "inputs": { "orderId": "ORD-123" }
}
```

Or by hash:
```http
POST /execute
{
  "ref": "sha256:def456...",
  "inputs": { "orderId": "ORD-123" }
}
```

### Characteristics

| Aspect | Value |
|--------|-------|
| Latency | ~1ms (no compile) |
| Storage | PipelineImage persisted |
| Use case | Production APIs |

### When to Use

- Production workloads
- High-throughput services
- Version-controlled deployments
- Pre-warmed at startup

## Suspended Execution

Pause when inputs are missing, resume later.

### Initial Request (Partial Inputs)

```http
POST /run
{
  "source": "in userId: String\nin approval: Boolean\nuser = GetUser(userId)\nout user when approval",
  "inputs": { "userId": "user-123" }
}
```

**Response:**
```json
{
  "status": "Suspended",
  "executionId": "exec-789",
  "missingInputs": ["approval"],
  "computedNodes": { "user": { "id": "user-123", "name": "Alice" } }
}
```

### Resume with Missing Inputs

```http
POST /executions/exec-789/resume
{
  "inputs": { "approval": true }
}
```

**Response:**
```json
{
  "status": "Completed",
  "outputs": { "user": { "id": "user-123", "name": "Alice" } }
}
```

### Characteristics

| Aspect | Value |
|--------|-------|
| State | Persisted between calls |
| Use case | Multi-step workflows |

### When to Use

- Human-in-the-loop approvals
- Multi-day workflows
- Event-driven pipelines
- Incremental data collection

## Comparison

| Mode | Compile | Latency | State | Use Case |
|------|---------|---------|-------|----------|
| Hot | Every request | ~50-100ms | None | Development |
| Cold | Once | ~1ms | Pipeline stored | Production |
| Suspended | Once | Varies | Execution stored | Workflows |
