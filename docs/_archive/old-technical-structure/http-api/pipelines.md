# Pipeline Management

> **Path**: `docs/http-api/pipelines.md`
> **Parent**: [http-api/](./README.md)

CRUD operations, versioning, aliases, and canary deployments for pipelines.

## Endpoint Summary

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/pipelines` | GET | List all stored pipelines |
| `/pipelines/{ref}` | GET | Get pipeline metadata |
| `/pipelines/{ref}` | DELETE | Delete a pipeline |
| `/pipelines/{name}/alias` | PUT | Repoint alias to different hash |
| `/pipelines/{name}/reload` | POST | Hot-reload from new source |
| `/pipelines/{name}/versions` | GET | List version history |
| `/pipelines/{name}/rollback` | POST | Rollback to previous version |
| `/pipelines/{name}/rollback/{v}` | POST | Rollback to specific version |
| `/pipelines/{name}/canary` | GET | Get canary deployment status |
| `/pipelines/{name}/canary/promote` | POST | Promote canary to next step |
| `/pipelines/{name}/canary/rollback` | POST | Rollback canary deployment |
| `/pipelines/{name}/canary` | DELETE | Abort canary deployment |

## Pipeline CRUD

### GET /pipelines

List all stored pipeline images with their aliases.

```bash
curl http://localhost:8080/pipelines
```

**Response:**

```json
{
  "pipelines": [
    {
      "structuralHash": "a1b2c3d4e5f6...",
      "syntacticHash": "f6e5d4c3b2a1...",
      "aliases": ["text-processor", "prod-v1"],
      "compiledAt": "2024-01-15T10:30:00Z",
      "moduleCount": 3,
      "declaredOutputs": ["result"]
    }
  ]
}
```

### GET /pipelines/{ref}

Get detailed metadata for a pipeline by name or hash.

```bash
# By name
curl http://localhost:8080/pipelines/text-processor

# By hash (prefix with sha256:)
curl http://localhost:8080/pipelines/sha256:a1b2c3d4...
```

**Response:**

```json
{
  "structuralHash": "a1b2c3d4e5f6...",
  "syntacticHash": "f6e5d4c3b2a1...",
  "aliases": ["text-processor"],
  "compiledAt": "2024-01-15T10:30:00Z",
  "modules": [
    {
      "name": "Uppercase",
      "description": "Converts text to uppercase",
      "version": "1.0",
      "inputs": { "text": "CString" },
      "outputs": { "result": "CString" }
    }
  ],
  "declaredOutputs": ["result"],
  "inputSchema": { "text": "CString" },
  "outputSchema": { "result": "CString" }
}
```

### DELETE /pipelines/{ref}

Delete a pipeline image. Returns 409 if aliases still reference it.

```bash
curl -X DELETE http://localhost:8080/pipelines/sha256:a1b2c3d4...
```

**Success:** `{"deleted": true}`

**Conflict (aliases exist):**

```json
{
  "error": "AliasConflict",
  "message": "Cannot delete pipeline: aliases [text-processor, prod-v1] point to it"
}
```

### PUT /pipelines/{name}/alias

Point an alias to a different structural hash.

```bash
curl -X PUT http://localhost:8080/pipelines/text-processor/alias \
  -H "Content-Type: application/json" \
  -d '{"structuralHash": "b2c3d4e5f6a1..."}'
```

**Response:**

```json
{
  "name": "text-processor",
  "structuralHash": "b2c3d4e5f6a1..."
}
```

## Pipeline Versioning

Versioning tracks the history of a named pipeline. Requires server-side versioning enabled.

### POST /pipelines/{name}/reload

Hot-reload a pipeline from new source code.

```bash
curl -X POST http://localhost:8080/pipelines/text-processor/reload \
  -H "Content-Type: application/json" \
  -d '{"source": "in text: String\nresult = Uppercase(text)\nout result"}'
```

**Response:**

```json
{
  "success": true,
  "previousHash": "a1b2c3d4...",
  "newHash": "b2c3d4e5...",
  "name": "text-processor",
  "changed": true,
  "version": 2
}
```

If the source produces the same structural hash, `changed` is false.

### GET /pipelines/{name}/versions

List all versions of a named pipeline.

```bash
curl http://localhost:8080/pipelines/text-processor/versions
```

**Response:**

```json
{
  "name": "text-processor",
  "versions": [
    {"version": 1, "structuralHash": "a1b2c3d4...", "createdAt": "2024-01-15T10:30:00Z", "active": false},
    {"version": 2, "structuralHash": "b2c3d4e5...", "createdAt": "2024-01-15T11:00:00Z", "active": true}
  ],
  "activeVersion": 2
}
```

### POST /pipelines/{name}/rollback

Rollback to the previous version.

```bash
curl -X POST http://localhost:8080/pipelines/text-processor/rollback
```

### POST /pipelines/{name}/rollback/{version}

Rollback to a specific version number.

```bash
curl -X POST http://localhost:8080/pipelines/text-processor/rollback/1
```

**Response:**

```json
{
  "success": true,
  "name": "text-processor",
  "previousVersion": 2,
  "activeVersion": 1,
  "structuralHash": "a1b2c3d4..."
}
```

## Canary Deployments

Canary releases gradually shift traffic from an old version to a new one, with automatic rollback on errors.

### Starting a Canary

Include `canary` config in the reload request:

```bash
curl -X POST http://localhost:8080/pipelines/text-processor/reload \
  -H "Content-Type: application/json" \
  -d '{
    "source": "...",
    "canary": {
      "initialWeight": 0.05,
      "promotionSteps": [0.10, 0.25, 0.50, 1.0],
      "observationWindow": "5m",
      "errorThreshold": 0.05,
      "autoPromote": true
    }
  }'
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `initialWeight` | number | 0.05 | Initial traffic fraction to new version |
| `promotionSteps` | array | [0.10, 0.25, 0.50, 1.0] | Traffic weights for each promotion step |
| `observationWindow` | string | "5m" | Duration to observe before promoting |
| `errorThreshold` | number | 0.05 | Error rate threshold for rollback |
| `latencyThresholdMs` | number | (none) | P99 latency threshold for rollback |
| `minRequests` | number | 10 | Minimum requests before evaluating |
| `autoPromote` | boolean | true | Auto-promote when metrics are healthy |

### GET /pipelines/{name}/canary

Get current canary deployment status.

```bash
curl http://localhost:8080/pipelines/text-processor/canary
```

**Response:**

```json
{
  "pipelineName": "text-processor",
  "oldVersion": {"version": 1, "structuralHash": "a1b2c3d4..."},
  "newVersion": {"version": 2, "structuralHash": "b2c3d4e5..."},
  "currentWeight": 0.25,
  "currentStep": 2,
  "status": "observing",
  "startedAt": "2024-01-15T11:00:00Z",
  "metrics": {
    "oldVersion": {
      "requests": 950, "successes": 948, "failures": 2,
      "avgLatencyMs": 45.2, "p99LatencyMs": 120.0
    },
    "newVersion": {
      "requests": 50, "successes": 49, "failures": 1,
      "avgLatencyMs": 42.1, "p99LatencyMs": 95.0
    }
  }
}
```

**Status values:** `observing`, `promoting`, `rolled_back`, `complete`

### POST /pipelines/{name}/canary/promote

Manually advance to the next promotion step.

```bash
curl -X POST http://localhost:8080/pipelines/text-processor/canary/promote
```

### POST /pipelines/{name}/canary/rollback

Manually rollback the canary (route all traffic to old version).

```bash
curl -X POST http://localhost:8080/pipelines/text-processor/canary/rollback
```

### DELETE /pipelines/{name}/canary

Abort the canary deployment entirely.

```bash
curl -X DELETE http://localhost:8080/pipelines/text-processor/canary
```

## Error Responses

| Code | Error | Description |
|------|-------|-------------|
| 400 | VersioningNotEnabled | Server does not have versioning enabled |
| 400 | CanaryNotEnabled | Server does not have canary routing enabled |
| 400 | InvalidVersion | Version parameter is not a valid integer |
| 404 | NotFound | Pipeline or version not found |
| 409 | AliasConflict | Cannot delete pipeline with active aliases |
| 409 | CanaryConflict | Cannot start canary (e.g., no previous version) |

## See Also

- [execution.md](./execution.md) - Execute pipelines
- [suspension.md](./suspension.md) - Manage suspended executions
- [security.md](./security.md) - Protect management endpoints
