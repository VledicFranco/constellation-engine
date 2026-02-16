# RFC-027: Module HTTP Endpoints

**Status:** Implemented
**Priority:** P2 (Developer Experience / Integration)
**Author:** Human + Claude
**Created:** 2026-02-10

---

## Summary

This RFC proposes an opt-in mechanism on `ModuleBuilder` that automatically publishes individual modules as `POST /modules/{name}/invoke` HTTP endpoints. Today, invoking a Constellation module over HTTP requires writing a `.cst` pipeline, compiling it via `POST /compile`, and executing via `POST /execute` — a three-step ceremony even for trivial single-module calls.

With this change, module authors add a single `.httpEndpoint()` call to their builder chain, and the framework generates a typed JSON endpoint backed by the module's existing `CType` signatures. A discovery endpoint (`GET /modules/published`) lists all published modules and their schemas, and an optional OpenAPI 3.0 spec is generated at `GET /modules/published/openapi`.

The feature is entirely additive — no existing endpoints or behaviors change, and modules without `.httpEndpoint()` remain invisible to the new routing layer.

## Motivation

### Problem

Constellation's current HTTP surface is pipeline-centric. Every interaction requires:

1. **Write** a `.cst` source declaring inputs, a single module call, and outputs
2. **Compile** it via `POST /compile` to get a pipeline reference
3. **Execute** it via `POST /execute` with the pipeline reference and inputs

For a team that wants to test a single module, integrate from a non-Scala service, or expose a module as a microservice endpoint, this overhead is significant.

### What This Enables

| Use Case | Before | After |
|----------|--------|-------|
| Test a module in isolation | Write `.cst` wrapper → compile → execute | `POST /modules/Uppercase/invoke` |
| Third-party integration | Build and document custom adapter | Point at auto-generated endpoint + schema |
| API gateway routing | Custom route mapping per pipeline | Route directly to `/modules/{name}/invoke` |
| OpenAPI documentation | Manual specification authoring | Auto-generated from `CType` signatures |
| Microservice decomposition | One pipeline per module | One endpoint per module, zero boilerplate |

### Design Goal

A module author should be able to go from "working module" to "working HTTP endpoint" by adding one builder method call and zero additional files.

## Design Principles

| Principle | Rationale |
|-----------|-----------|
| **Explicit opt-in** | Not every module should be externally callable. Publishing is a conscious API surface decision. |
| **Zero pipeline overhead** | Direct invocation bypasses DAG compilation, scheduling, and inter-node wiring. |
| **Schema from types** | `CType` already describes inputs/outputs; JSON Schema is derived, never hand-written. |
| **Middleware reuse** | Auth, CORS, rate limiting compose identically to pipeline routes. |
| **Additive to existing API** | No changes to `/compile`, `/execute`, `/run`, or `/modules` (listing). |
| **Progressive enhancement** | Start with basic invoke, add OpenAPI and per-module overrides later. |

## Architecture

### Route Generation Flow

```
ModuleBuilder                  Constellation                ConstellationRoutes
     │                              │                              │
     │  .httpEndpoint()             │                              │
     │  .build                      │                              │
     │──────────────────────────▶   │                              │
     │  Module.Uninitialized        │                              │
     │  (httpConfig = Some(...))    │                              │
     │                              │                              │
     │         setModule(module)    │                              │
     │  ◀───────────────────────   │                              │
     │                              │  publishedModules()          │
     │                              │  ◀───────────────────────── │
     │                              │                              │
     │                              │  List[ModuleNodeSpec]        │
     │                              │  (filtered: httpConfig.isDefined)
     │                              │  ─────────────────────────▶ │
     │                              │                              │
     │                              │          For each published: │
     │                              │    POST /modules/{name}/invoke
     │                              │    ─────────────────────────▶│
```

### Invocation Flow (Direct vs Pipeline)

```
Pipeline Execution (existing):

  JSON input
    │
    ▼
  POST /compile  ──▶  DagCompiler  ──▶  DagSpec (stored)
    │
    ▼
  POST /execute  ──▶  Scheduler  ──▶  DAG traversal  ──▶  Module.run()  ──▶  JSON output
                       (N nodes)


Direct Module Invocation (proposed):

  JSON input
    │
    ▼
  POST /modules/{name}/invoke
    │
    ▼
  JsonCValueConverter.convertAdaptive(json, consumes)
    │
    ▼
  Module.run(CProduct)
    │
    ▼
  JsonCValueConverter.cValueToJson(result)
    │
    ▼
  JSON output
```

Direct invocation eliminates DAG compilation, scheduling overhead, and inter-node wiring. The module is called with a `CProduct` constructed from the JSON body using `JsonCValueConverter`, which already handles bidirectional JSON/CValue conversion.

### Component Responsibilities

| Component | Module | Responsibility |
|-----------|--------|----------------|
| `ModuleHttpConfig` | `core` | Data class for HTTP publishing options |
| `ModuleNodeSpec.httpConfig` | `core` | Optional field on module spec |
| `ModuleBuilder.httpEndpoint()` | `runtime` | Builder method to opt in |
| `ModuleHttpRoutes` | `http-api` | Route generation + invocation logic |
| `ModuleSchemaGenerator` | `http-api` | CType → JSON Schema / OpenAPI conversion |

This respects the dependency graph: `core` ← `runtime` ← `http-api`.

## API Design

### Endpoints

#### `POST /modules/{name}/invoke`

Invoke a published module directly with JSON input.

**Request:**
```http
POST /modules/Uppercase/invoke HTTP/1.1
Content-Type: application/json

{
  "text": "hello world"
}
```

**Response (200 OK):**
```json
{
  "result": "HELLO WORLD"
}
```

**Response (400 Bad Request):**
```json
{
  "error": "input_validation",
  "message": "Expected field 'text' of type String, got Int",
  "module": "Uppercase"
}
```

**Response (404 Not Found):**
```json
{
  "error": "module_not_found",
  "message": "Module 'Foo' is not published as an HTTP endpoint"
}
```

**Response (500 Internal Server Error):**
```json
{
  "error": "module_error",
  "message": "Module execution failed: <details>",
  "module": "Uppercase"
}
```

The request body is a flat JSON object whose keys correspond to the module's `consumes` map. The response body is a flat JSON object whose keys correspond to the module's `produces` map.

#### `GET /modules/published`

List all modules published as HTTP endpoints, with their input/output schemas.

**Response:**
```json
{
  "modules": [
    {
      "name": "Uppercase",
      "description": "Converts text to uppercase",
      "version": "1.0",
      "tags": ["text"],
      "endpoint": "/modules/Uppercase/invoke",
      "input": {
        "text": "String"
      },
      "output": {
        "result": "String"
      }
    },
    {
      "name": "WordCount",
      "description": "Counts words in text",
      "version": "1.0",
      "tags": ["text", "analytics"],
      "endpoint": "/modules/WordCount/invoke",
      "input": {
        "text": "String"
      },
      "output": {
        "count": "Int"
      }
    }
  ]
}
```

#### `GET /modules/published/openapi`

Return an OpenAPI 3.0 specification describing all published module endpoints.

**Response (200 OK):**
```json
{
  "openapi": "3.0.3",
  "info": {
    "title": "Constellation Module Endpoints",
    "version": "1.0.0"
  },
  "paths": {
    "/modules/Uppercase/invoke": {
      "post": {
        "summary": "Converts text to uppercase",
        "tags": ["text"],
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": {
                "type": "object",
                "properties": {
                  "text": { "type": "string" }
                },
                "required": ["text"]
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "Module output",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "result": { "type": "string" }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
```

### CType to JSON Schema Mapping

| CType | JSON Schema | Example JSON |
|-------|-------------|-------------|
| `CString` | `{ "type": "string" }` | `"hello"` |
| `CInt` | `{ "type": "integer", "format": "int64" }` | `42` |
| `CFloat` | `{ "type": "number", "format": "double" }` | `3.14` |
| `CBoolean` | `{ "type": "boolean" }` | `true` |
| `CList(CString)` | `{ "type": "array", "items": { "type": "string" } }` | `["a", "b"]` |
| `CMap(CString, CInt)` | `{ "type": "object", "additionalProperties": { "type": "integer" } }` | `{ "a": 1 }` |
| `CProduct(fields)` | `{ "type": "object", "properties": { ... }, "required": [...] }` | `{ "x": 1, "y": 2 }` |
| `CUnion(variants)` | `{ "oneOf": [...], "discriminator": { "propertyName": "type" } }` | `{ "type": "A", ... }` |
| `COptional(CString)` | `{ "type": "string", "nullable": true }` | `"hello"` or `null` |

This mapping is already partially implemented by `JsonCValueConverter` for the value layer; the schema layer mirrors it structurally.

## Module Builder API Changes

### New Builder Method

```scala
// In ModuleBuilder[I, O] and ModuleBuilderInit:
def httpEndpoint(): ModuleBuilder[I, O]
def httpEndpoint(config: ModuleHttpConfig): ModuleBuilder[I, O]
```

The zero-argument form uses default settings. The overload accepts a config for per-module overrides.

### ModuleHttpConfig (new, in `core`)

```scala
package io.constellation

import scala.concurrent.duration.FiniteDuration

case class ModuleHttpConfig(
    published: Boolean = true,
    rateLimitOverride: Option[Int] = None,         // requests-per-minute, overrides global
    authRoleOverride: Option[String] = None,        // minimum role, overrides global
    timeoutOverride: Option[FiniteDuration] = None  // invocation timeout, overrides module default
)

object ModuleHttpConfig {
  val default: ModuleHttpConfig = ModuleHttpConfig()
}
```

### ModuleNodeSpec Change

```scala
// Existing:
case class ModuleNodeSpec(
    metadata: ComponentMetadata,
    consumes: Map[String, CType] = Map.empty,
    produces: Map[String, CType] = Map.empty,
    config: ModuleConfig = ModuleConfig.default,
    definitionContext: Option[Map[String, Json]] = None
)

// Proposed addition:
case class ModuleNodeSpec(
    metadata: ComponentMetadata,
    consumes: Map[String, CType] = Map.empty,
    produces: Map[String, CType] = Map.empty,
    config: ModuleConfig = ModuleConfig.default,
    definitionContext: Option[Map[String, Json]] = None,
    httpConfig: Option[ModuleHttpConfig] = None      // NEW — None = not published (default)
)
```

Adding a new field with a default value is binary-compatible — existing code that constructs `ModuleNodeSpec` without `httpConfig` continues to work.

### Constellation API Addition

```scala
// In Constellation (or a new helper):
def publishedModules: IO[List[(ModuleNodeSpec, Module.Initialized)]]
// Returns only modules where httpConfig.isDefined
```

## End-to-End Examples

### Example 1: Simple Module

```scala
// Module definition (in example-app or any module library)
case class TextInput(text: String)
case class TextOutput(result: String)

val uppercase = ModuleBuilder
  .metadata("Uppercase", "Converts text to uppercase", 1, 0)
  .tags("text")
  .implementationPure[TextInput, TextOutput] { input =>
    TextOutput(input.text.toUpperCase)
  }
  .httpEndpoint()   // <— one new line
  .build
```

```bash
# Invoke directly
curl -X POST http://localhost:8080/modules/Uppercase/invoke \
  -H "Content-Type: application/json" \
  -d '{"text": "hello world"}'

# Response: {"result": "HELLO WORLD"}
```

### Example 2: Multi-Field Module

```scala
case class SearchInput(query: String, maxResults: Int, caseSensitive: Boolean)
case class SearchOutput(matches: Vector[String], total: Int)

val search = ModuleBuilder
  .metadata("Search", "Full-text search over documents", 1, 0)
  .tags("search", "text")
  .implementation[SearchInput, SearchOutput] { input =>
    IO {
      val results = searchIndex.query(input.query, input.maxResults, input.caseSensitive)
      SearchOutput(results.toVector, results.size)
    }
  }
  .httpEndpoint()
  .build
```

```bash
curl -X POST http://localhost:8080/modules/Search/invoke \
  -H "Content-Type: application/json" \
  -d '{"query": "constellation", "maxResults": 10, "caseSensitive": false}'

# Response: {"matches": ["Constellation Engine", "constellation-lang"], "total": 2}
```

### Example 3: Rate-Limited Module with Auth Override

```scala
import scala.concurrent.duration._

val expensiveModule = ModuleBuilder
  .metadata("GPTSummarize", "Summarize text using external LLM", 1, 0)
  .tags("ai", "text")
  .implementation[SummarizeInput, SummarizeOutput] { input =>
    callExternalLLM(input.text)
  }
  .httpEndpoint(ModuleHttpConfig(
    rateLimitOverride = Some(10),          // 10 RPM (stricter than global)
    authRoleOverride = Some("Admin"),      // Admin-only
    timeoutOverride = Some(30.seconds)     // longer timeout for LLM call
  ))
  .build
```

### Example 4: Discovery and OpenAPI

```bash
# List all published modules
curl http://localhost:8080/modules/published | jq '.modules[].name'
# "Uppercase"
# "Search"
# "GPTSummarize"

# Get OpenAPI spec (import into Swagger UI, Postman, etc.)
curl http://localhost:8080/modules/published/openapi > openapi.json
```

## Implementation Phases

### Phase 1: Basic Publishing and Invocation

Core functionality: opt-in publishing, direct invocation, discovery endpoint.

- [ ] Add `ModuleHttpConfig` case class to `core` module
- [ ] Add `httpConfig: Option[ModuleHttpConfig]` field to `ModuleNodeSpec`
- [ ] Add `.httpEndpoint()` method to `ModuleBuilder` and `ModuleBuilderInit`
- [ ] Thread `httpConfig` through `Module.Uninitialized` → `Module.Initialized` → `ModuleNodeSpec`
- [ ] Add `publishedModules` method to `Constellation`
- [ ] Create `ModuleHttpRoutes` in `http-api` module
- [ ] Implement `POST /modules/{name}/invoke` route
- [ ] Implement `GET /modules/published` route
- [ ] Wire `ModuleHttpRoutes` into `ConstellationServer` builder
- [ ] Add unit tests for `ModuleHttpConfig` propagation
- [ ] Add integration tests for invoke endpoint (success, validation error, 404)
- [ ] Add integration tests for discovery endpoint
- [ ] Update `ExampleLib` with at least one `.httpEndpoint()` module for manual testing

**Deliverables:** Users can publish modules via `.httpEndpoint()`, invoke them with `POST /modules/{name}/invoke`, and discover them via `GET /modules/published`.

### Phase 2: OpenAPI Generation

Auto-generate OpenAPI 3.0 specifications from `CType` signatures.

- [ ] Create `ModuleSchemaGenerator` in `http-api` module
- [ ] Implement `CType` → JSON Schema conversion (all 9 `CType` variants)
- [ ] Implement OpenAPI 3.0 document assembly from published module specs
- [ ] Implement `GET /modules/published/openapi` route
- [ ] Add tests for CType → JSON Schema mapping (all variants, nested types)
- [ ] Add test for full OpenAPI document generation
- [ ] Validate generated spec against OpenAPI 3.0 schema

**Deliverables:** Users can export a complete OpenAPI spec and import it into Swagger UI, Postman, or any OpenAPI-compatible tool.

### Phase 3: Per-Module Middleware Overrides and Metrics

Fine-grained control and observability for published modules.

- [ ] Implement per-module rate limiting (override global RPM)
- [ ] Implement per-module auth role enforcement (override global minimum)
- [ ] Implement per-module timeout enforcement (override `moduleTimeout`)
- [ ] Add per-module invocation metrics to `/metrics` endpoint
- [ ] Add metrics: invocation count, latency histogram, error rate per module
- [ ] Add integration tests for middleware overrides
- [ ] Document per-module configuration in operations guide

**Deliverables:** Module authors can set per-module rate limits, auth roles, and timeouts. Operators can monitor per-module invocation metrics.

## Open Questions

### 1. URL Prefix: `/modules/{name}/invoke` vs `/api/modules/{name}`?

**Current leaning:** `/modules/{name}/invoke` — consistent with existing `/modules` listing endpoint, explicit verb avoids ambiguity with RESTful resource semantics.

**Trade-off:** `/api/modules/{name}` is shorter and more REST-like, but `POST /modules/Uppercase` could be confused with creating a module named "Uppercase".

### 2. Should invocation create a synthetic single-node DagSpec?

**Current leaning:** No — invoke the module directly. Creating a DagSpec adds unnecessary overhead and couples direct invocation to the pipeline compiler.

**Trade-off:** A synthetic DagSpec would get scheduling, metrics, and suspension support for free, but at the cost of latency and complexity. If these features are needed later, they can be added as opt-in wrappers.

### 3. Dynamic module discovery (hot-publish)?

**Current leaning:** Static at server startup. Published modules are determined when `setModule` is called. No dynamic publishing/unpublishing at runtime.

**Trade-off:** Dynamic discovery would allow modules to be published/unpublished via API, but adds complexity around route table mutation and concurrent access. Can be revisited if module-provider-protocol (RFC-024) is implemented.

### 4. Interaction with streaming pipelines (RFC-025)?

**Current leaning:** Out of scope for this RFC. Streaming connectors are pipeline-level constructs, not module-level. If a module internally uses streaming, the HTTP endpoint still returns a single JSON response.

**Trade-off:** A future extension could support `Transfer-Encoding: chunked` or SSE for modules that produce streams, but this requires a different invocation model.

### 5. Batch invocation endpoint?

**Current leaning:** Defer. A `POST /modules/{name}/invoke/batch` that accepts an array of inputs could reduce HTTP overhead for bulk calls.

**Trade-off:** Adds API surface area. Users can achieve similar results with pipeline execution or client-side parallelism. Worth revisiting based on user demand.

## Rejected Alternatives

### Auto-publish all modules

**Rejected because:** Violates the principle of explicit API surface management. A module designed as an internal pipeline step may not be suitable for direct external invocation (e.g., it may expect pre-processed inputs). Opt-in ensures module authors consciously decide what to expose.

### Separate configuration file

**Rejected because:** Module HTTP configuration would be disconnected from the module definition. The builder pattern already centralizes all module concerns (metadata, implementation, config, tags) — HTTP publishing belongs in the same chain.

### Use `FunctionSignature` instead of `ModuleNodeSpec`

**Rejected because:** `FunctionSignature` lives in `lang-compiler` and is tied to the language's type system. Using it would create a dependency from `core` → `lang-compiler`, violating the dependency graph. `ModuleNodeSpec` already contains `consumes`/`produces` maps with `CType`, which is sufficient for schema generation.

### Separate HTTP framework for module endpoints

**Rejected because:** `ConstellationServer` already uses http4s with composable middleware (auth, CORS, rate limiting). Adding a separate framework would double the dependency surface and prevent middleware reuse.

### GraphQL instead of REST

**Rejected because:** GraphQL is better suited for complex query patterns across multiple entities. Module invocation is a simple request/response pattern — a single POST endpoint per module is the minimal viable surface. GraphQL could be considered as a future layer on top of the REST endpoints.

## References

- **RFC-013:** HTTP API Hardening (auth, CORS, rate limiting middleware)
- **RFC-024:** Module Provider Protocol (dynamic module loading, relevant to future discovery)
- **RFC-025:** Streaming Pipelines (interaction with streaming connectors)
- **Key source files:**
  - `ModuleBuilder`: `modules/runtime/.../ModuleBuilder.scala`
  - `ModuleNodeSpec`: `modules/core/.../Spec.scala`
  - `JsonCValueConverter`: `modules/runtime/.../JsonCValueConverter.scala`
  - `ConstellationRoutes`: `modules/http-api/.../ConstellationRoutes.scala`
  - `CType` / `CValue`: `modules/core/.../TypeSystem.scala`
  - `ConstellationServer`: `modules/http-api/.../ConstellationServer.scala`
