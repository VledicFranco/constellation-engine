# Cold Execution

> **Path**: `docs/features/execution/cold-execution.md`
> **Parent**: [execution/](./README.md)

Pre-compile pipelines, execute by reference for production throughput.

## Overview

Cold execution separates compilation from execution. Pipelines are compiled once (typically at deployment time) and executed many times by reference. This eliminates compilation latency from the request path, providing consistent sub-millisecond execution overhead.

## When to Use Cold Execution

| Use Case | Why Cold Execution |
|----------|-------------------|
| Production APIs | Consistent low latency |
| High-throughput services | No compilation overhead per request |
| Version-controlled deployments | Clear pipeline versioning |
| Pre-warmed systems | Compile at startup, not at first request |
| Canary deployments | Execute different versions by reference |

## Workflow

```
┌─────────────────────────────────────────────────────────────┐
│                   DEPLOYMENT TIME                            │
├─────────────────────────────────────────────────────────────┤
│  POST /compile                                               │
│    source: "in x: String\nout x"                            │
│    name: "my-pipeline"                                       │
│                     ↓                                        │
│  Response: { structuralHash: "abc123...", name: "my-pipe" } │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    REQUEST TIME                              │
├─────────────────────────────────────────────────────────────┤
│  POST /execute                                               │
│    ref: "my-pipeline"  (or "abc123...")                     │
│    inputs: { x: "value" }                                    │
│                     ↓                                        │
│  Response: { outputs: { x: "value" }, status: "completed" } │
└─────────────────────────────────────────────────────────────┘
```

## API

### Step 1: Compile and Store

#### Endpoint

```
POST /compile
```

#### Request

```json
{
  "source": "in orderId: String\norder = GetOrder(orderId)\nout order",
  "name": "order-lookup"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `source` | string | Yes | Constellation-lang source code |
| `name` | string | No | Pipeline alias (for reference by name) |

#### Response (Success)

```json
{
  "success": true,
  "structuralHash": "def456abc789...",
  "syntacticHash": "789abc123def...",
  "name": "order-lookup"
}
```

| Field | Description |
|-------|-------------|
| `structuralHash` | Content-based hash of pipeline semantics |
| `syntacticHash` | Hash of source text (for change detection) |
| `name` | Alias for reference (if provided) |

#### Response (Error)

```json
{
  "success": false,
  "errors": ["Line 2: Unknown function 'GetOrdr'"]
}
```

### Step 2: Execute by Reference

#### Endpoint

```
POST /execute
```

#### Request (by Name)

```json
{
  "ref": "order-lookup",
  "inputs": {
    "orderId": "ORD-123"
  }
}
```

#### Request (by Hash)

```json
{
  "ref": "def456abc789012345678901234567890123456789012345678901234567890123",
  "inputs": {
    "orderId": "ORD-123"
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `ref` | string | Yes | Pipeline name or 64-char structural hash |
| `inputs` | object | Yes | Input values keyed by variable name |

#### Reference Resolution

The system determines reference type automatically:

| Reference Format | Interpretation |
|------------------|----------------|
| Exactly 64 hex characters | Structural hash lookup |
| Anything else | Alias name lookup |

#### Response

Same format as hot execution response, without `structuralHash` field:

```json
{
  "success": true,
  "outputs": {
    "order": {
      "id": "ORD-123",
      "status": "shipped",
      "items": [...]
    }
  },
  "status": "completed",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "resumptionCount": 0
}
```

## Examples

### Compile Once, Execute Many

```bash
# Compile at deployment
curl -X POST http://localhost:8080/compile \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in text: String\nresult = Uppercase(text)\nout result",
    "name": "text-processor"
  }'

# Execute repeatedly
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -d '{"ref": "text-processor", "inputs": {"text": "hello"}}'

curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -d '{"ref": "text-processor", "inputs": {"text": "world"}}'
```

### Version Pinning

```bash
# Store the hash from compile response
HASH=$(curl -s -X POST http://localhost:8080/compile \
  -H "Content-Type: application/json" \
  -d '{"source": "..."}' | jq -r '.structuralHash')

# Execute by exact hash (immune to name updates)
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -d "{\"ref\": \"$HASH\", \"inputs\": {...}}"
```

### Pipeline Update

```bash
# Compile new version with same name
curl -X POST http://localhost:8080/compile \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in text: String\nresult = Uppercase(Trim(text))\nout result",
    "name": "text-processor"
  }'

# New executions use updated pipeline
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -d '{"ref": "text-processor", "inputs": {"text": "  hello  "}}'
```

## Versioning and Caching

### Structural Hash

The structural hash is computed from the pipeline's semantic structure:

- Variable names and types
- Module calls and their arguments
- Data flow graph edges
- Input and output declarations

The hash is **not** affected by:
- Comments
- Whitespace
- Variable ordering (where semantically equivalent)

This means:
- Reformatting source does not change the hash
- Adding comments does not change the hash
- Any semantic change produces a new hash

### Name Aliases

Pipeline names are aliases that point to structural hashes:

```
"text-processor" → "def456abc789..."
```

When you compile with a name that already exists:
- The new pipeline is stored with its structural hash
- The name alias is updated to point to the new hash
- The old pipeline remains accessible by its hash

### Pipeline Store

Compiled pipelines are stored in memory by default. For persistence:

```scala
// Use file-system backed store
ConstellationServer.builder(constellation, compiler)
  .withPipelineStore(FileSystemPipelineStore(Path.of("/pipelines")))
  .run
```

### Cache Metrics

```bash
curl http://localhost:8080/metrics | jq .cache
```

```json
{
  "hitRate": 0.95,
  "missRate": 0.05,
  "evictionCount": 0,
  "loadCount": 15
}
```

## Performance Characteristics

| Aspect | Hot Execution | Cold Execution |
|--------|---------------|----------------|
| First request | 50-100ms | N/A (pre-compiled) |
| Subsequent requests | 5-50ms (cache hit) | ~1ms overhead |
| Variance | High (compile time varies) | Low (consistent) |
| Memory | Cached on first use | Pre-loaded |

Cold execution provides:
- **Predictable latency**: No compilation in request path
- **Lower variance**: Consistent execution overhead
- **Better P99**: No compilation tail latency

## Error Handling

### Pipeline Not Found

```json
{
  "error": "NotFound",
  "message": "Pipeline 'my-pipeline' not found",
  "requestId": "abc-123"
}
```

HTTP status: `404 Not Found`

### Invalid Reference Format

```json
{
  "error": "BadRequest",
  "message": "Invalid pipeline reference format"
}
```

HTTP status: `400 Bad Request`

### Runtime Errors

Same as hot execution - the request succeeds, execution fails:

```json
{
  "success": false,
  "error": "Module 'GetOrder' execution failed: order not found",
  "status": "failed",
  "executionId": "..."
}
```

HTTP status: `200 OK`

## Suspension Support

Cold execution supports suspension. If inputs are missing:

```json
{
  "success": true,
  "status": "suspended",
  "executionId": "550e8400-...",
  "missingInputs": {
    "approvalCode": "CString"
  }
}
```

Resume using the execution ID. See [suspension.md](./suspension.md).

## Components Involved

| Component | Module | File | Role |
|-----------|--------|------|------|
| HTTP Endpoints | `http-api` | `ConstellationRoutes.scala` | Route handling |
| Execution Helper | `http-api` | `ExecutionHelper.scala` | Reference resolution |
| Pipeline Store | `runtime` | `PipelineStore.scala` | Hash and name storage |
| Pipeline Store Impl | `runtime` | `PipelineStoreImpl.scala` | In-memory implementation |
| FS Pipeline Store | `http-api` | `FileSystemPipelineStore.scala` | Persistent storage |
| Pipeline Image | `runtime` | `PipelineImage.scala` | Compiled pipeline format |
| Pipeline Loader | `http-api` | `PipelineLoader.scala` | Startup pipeline loading |
| Version Store | `http-api` | `PipelineVersionStore.scala` | Version tracking |
| Compiler | `lang-compiler` | `LangCompiler.scala` | Source compilation |
| Runtime | `runtime` | `Runtime.scala` | Module execution |

## Deployment Patterns

### Pre-warm at Startup

Load pipelines from files at server start:

```scala
ConstellationServer.builder(constellation, compiler)
  .withPipelineLoader(PipelineLoaderConfig(
    directory = Path.of("pipelines"),
    pattern = "*.cst",
    watchForChanges = false
  ))
  .run
```

### Hot Reload in Development

Watch for file changes:

```scala
.withPipelineLoader(PipelineLoaderConfig(
  directory = Path.of("pipelines"),
  watchForChanges = true
))
```

### Canary Deployment

Route percentage of traffic to new version:

```scala
.withCanaryRouter(CanaryConfig(
  stable = "order-lookup@v1",
  canary = "order-lookup@v2",
  canaryPercent = 10
))
```

## Related Documentation

- [PHILOSOPHY.md](./PHILOSOPHY.md) - Why cold execution exists
- [hot-execution.md](./hot-execution.md) - Development mode execution
- [suspension.md](./suspension.md) - Resuming suspended executions
- [docs/http-api/execution.md](../../http-api/execution.md) - Full API reference
- [docs/http-api/pipelines.md](../../http-api/pipelines.md) - Pipeline management
