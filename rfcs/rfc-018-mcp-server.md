# RFC-018: MCP Server for LLM Integration

**Status:** Draft
**Priority:** P1 (Developer Experience)
**Author:** Human + Claude
**Created:** 2026-02-05

---

## Summary

This RFC proposes a Model Context Protocol (MCP) server that enables LLMs to discover, compose, and execute Constellation pipelines. The server exposes documentation as resources, module/pipeline metadata as dynamic resources, and compilation/execution as tools.

The design prioritizes **multi-agent team scenarios**: a shared Constellation service used by many developers, each running one or more AI agents concurrently.

---

## Motivation

### The Problem

LLMs can help developers write Constellation pipelines, but today they lack:

1. **Context** — LLMs don't know what modules are available on a given deployment
2. **Validation** — No way to check if a pipeline is syntactically/semantically correct
3. **Execution** — Can't test pipelines without manual copy/paste to dashboard or curl
4. **Freshness** — Documentation in training data may be outdated

### The Opportunity

With MCP, an LLM can:

```
discover available modules → compose a pipeline → validate syntax →
compile to DAG → execute with test inputs → return results
```

This transforms LLMs from "documentation readers" into "pipeline co-pilots" that understand the live state of a Constellation deployment.

### Multi-Agent Team Scenario

Consider a team of 20 engineers using a shared Constellation service. Each engineer may use:
- Claude Code for pipeline development
- Cursor for IDE-integrated assistance
- Custom agents for automated pipeline generation

At peak, 50+ agents might interact with the service simultaneously. The MCP design must handle:

| Challenge | Solution |
|-----------|----------|
| Configuration per developer | Local MCP server instance with personal config |
| Request attribution | Agent ID passed to Constellation for tracing |
| Cold pipeline collisions | Namespace prefixes (user/agent scoped) |
| Rate limiting fairness | Per-agent quotas at Constellation level |
| Stale module lists | Live fetching, short TTL caching |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Developer Workstation                          │
│  ┌─────────────┐     stdio/SSE      ┌─────────────────────────────────┐ │
│  │   Claude    │◄──────────────────►│  constellation-mcp (local)      │ │
│  │   Cursor    │                    │  - Bundles documentation        │ │
│  │   Agent X   │                    │  - Proxies to Constellation API │ │
│  └─────────────┘                    │  - Adds agent identification    │ │
│                                     └──────────────┬──────────────────┘ │
└──────────────────────────────────────────────────────│────────────────────┘
                                                       │ HTTPS
                                                       ▼
                                        ┌─────────────────────────────────┐
                                        │   Constellation Service         │
                                        │   (shared, deployed)            │
                                        │                                 │
                                        │   GET  /modules                 │
                                        │   GET  /modules/:name           │
                                        │   POST /validate                │
                                        │   POST /compile                 │
                                        │   POST /run                     │
                                        │   POST /execute                 │
                                        │   GET  /pipelines               │
                                        └─────────────────────────────────┘
```

### Why Local MCP Server?

Each developer runs their own `constellation-mcp` instance because:

1. **Personal configuration** — Points to their target environment (dev/staging/prod)
2. **Agent identification** — Injects their identity into requests
3. **No shared state** — MCP layer is stateless, all state lives in Constellation
4. **Easy multi-environment** — Switch between Constellation instances via config
5. **Offline docs** — Documentation resources available without network

### Agent Identification

Every request from the MCP server to Constellation includes:

```http
X-Constellation-Agent: claude-code:alice@acme.com
X-Constellation-Request-ID: mcp-req-abc123
```

This enables:
- **Tracing**: Logs show which agent made which request
- **Metrics**: Per-agent request counts, error rates
- **Rate limiting**: Fairness across agents (optional, at Constellation level)
- **Audit**: Who ran what pipeline when

---

## MCP Resources

Resources provide read-only context to LLMs.

### Static Resources (bundled with MCP server)

| URI | Description |
|-----|-------------|
| `constellation://docs/language-reference` | Full .cst syntax guide |
| `constellation://docs/module-options` | retry, cache, timeout, etc. |
| `constellation://docs/type-system` | Types, type algebra, records |
| `constellation://docs/examples/{name}` | Cookbook examples |
| `constellation://docs/errors/{code}` | Error code explanations |

These are bundled into the MCP server package, versioned with releases.

### Dynamic Resources (fetched from Constellation API)

| URI | Description | Source |
|-----|-------------|--------|
| `constellation://modules` | List of all registered modules | `GET /modules` |
| `constellation://modules/{name}` | Module signature, description, version | `GET /modules/:name` |
| `constellation://pipelines` | List of stored cold pipelines | `GET /pipelines` |
| `constellation://pipelines/{ref}` | Pipeline schema (inputs/outputs) | `GET /pipelines/:ref` |

Dynamic resources have a short TTL (30s default) to balance freshness vs load.

### Module Resource Example

```json
{
  "uri": "constellation://modules/EnrichCustomer",
  "name": "EnrichCustomer",
  "mimeType": "application/json",
  "contents": {
    "name": "EnrichCustomer",
    "description": "Fetches customer data from CRM and enriches with segment info",
    "version": "2.1",
    "input": {
      "type": "record",
      "fields": {
        "customerId": "String",
        "includeHistory": "Boolean?"
      }
    },
    "output": {
      "type": "record",
      "fields": {
        "customerId": "String",
        "name": "String",
        "segment": "String",
        "lifetimeValue": "Double",
        "history": "List[Order]?"
      }
    },
    "tags": ["crm", "enrichment"],
    "options": {
      "supportedOptions": ["retry", "timeout", "cache"],
      "defaults": {
        "timeout": "5s"
      }
    }
  }
}
```

---

## MCP Tools

Tools allow LLMs to take actions.

### Tool: `validate`

Check .cst syntax without compilation.

```typescript
{
  name: "validate",
  description: "Validate Constellation pipeline syntax",
  inputSchema: {
    type: "object",
    properties: {
      source: { type: "string", description: "Pipeline source code (.cst)" }
    },
    required: ["source"]
  }
}
```

**Response:**
```json
{
  "valid": true,
  "warnings": []
}
// or
{
  "valid": false,
  "errors": [
    { "line": 3, "column": 12, "code": "E001", "message": "Unknown module: Foo" }
  ]
}
```

### Tool: `compile`

Compile pipeline and return DAG structure.

```typescript
{
  name: "compile",
  description: "Compile pipeline to executable DAG",
  inputSchema: {
    type: "object",
    properties: {
      source: { type: "string", description: "Pipeline source code" },
      name: { type: "string", description: "Optional name for cold storage" }
    },
    required: ["source"]
  }
}
```

**Response:**
```json
{
  "success": true,
  "structuralHash": "sha256:abc123...",
  "inputs": { "orderId": "String" },
  "outputs": { "enrichedOrder": "EnrichedOrder" },
  "dag": {
    "nodes": ["input:orderId", "module:GetOrder", "module:EnrichOrder", "output:enrichedOrder"],
    "edges": [["input:orderId", "module:GetOrder"], ...]
  },
  "storedAs": "user:alice/order-enrichment"  // if name provided
}
```

### Tool: `execute`

Run a pipeline (hot or cold).

```typescript
{
  name: "execute",
  description: "Execute a Constellation pipeline",
  inputSchema: {
    type: "object",
    properties: {
      source: { type: "string", description: "Pipeline source (for hot execution)" },
      ref: { type: "string", description: "Pipeline reference (for cold execution)" },
      inputs: { type: "object", description: "Input values" }
    },
    required: ["inputs"]
  }
}
```

**Response:**
```json
{
  "success": true,
  "outputs": { "enrichedOrder": { "id": "ORD-123", "total": 99.99, "customer": {...} } },
  "executionId": "exec-789",
  "metrics": {
    "compileTimeMs": 45,
    "executeTimeMs": 120,
    "modulesExecuted": 3
  }
}
```

### Tool: `describe_module`

Get detailed information about a specific module.

```typescript
{
  name: "describe_module",
  description: "Get detailed schema and documentation for a module",
  inputSchema: {
    type: "object",
    properties: {
      name: { type: "string", description: "Module name" }
    },
    required: ["name"]
  }
}
```

### Tool: `list_modules`

List all available modules with optional filtering.

```typescript
{
  name: "list_modules",
  description: "List available modules",
  inputSchema: {
    type: "object",
    properties: {
      tag: { type: "string", description: "Filter by tag" },
      search: { type: "string", description: "Search in name/description" }
    }
  }
}
```

---

## MCP Prompts

Prompts provide contextual instructions for common tasks.

### Prompt: `write_pipeline`

```typescript
{
  name: "write_pipeline",
  description: "Help write a Constellation pipeline",
  arguments: [
    { name: "goal", description: "What should the pipeline accomplish?", required: true },
    { name: "available_inputs", description: "What inputs are available?", required: false }
  ]
}
```

**Generated prompt includes:**
- Language syntax reference
- List of available modules (fetched live)
- Example patterns from cookbook
- Instruction to validate before presenting

### Prompt: `debug_pipeline`

```typescript
{
  name: "debug_pipeline",
  description: "Help debug a failing pipeline",
  arguments: [
    { name: "source", description: "Pipeline source code", required: true },
    { name: "error", description: "Error message received", required: true }
  ]
}
```

**Generated prompt includes:**
- Error code reference
- Common mistakes for that error type
- Suggestion to re-validate with fixes

---

## Cold Pipeline Namespacing

When agents store cold pipelines, names are automatically namespaced:

| Provided Name | Stored As | Why |
|---------------|-----------|-----|
| `my-pipeline` | `user:alice/my-pipeline` | User namespace from agent ID |
| `temp-test` | `agent:claude-abc/temp-test` | Agent session namespace |
| `shared/common-enrichment` | `shared/common-enrichment` | Explicit shared prefix (requires permission) |

This prevents collisions when multiple agents create pipelines with similar names.

**Listing pipelines** respects namespaces:
- By default, agents see their own + shared pipelines
- Admin agents can list all namespaces

---

## Configuration

### MCP Server Config (`constellation-mcp.json`)

```json
{
  "constellation": {
    "baseUrl": "https://constellation.internal.acme.com",
    "apiKey": "${CONSTELLATION_API_KEY}"
  },
  "agent": {
    "id": "claude-code",
    "user": "alice@acme.com"
  },
  "cache": {
    "moduleTtlSeconds": 30,
    "documentationTtlSeconds": 3600
  },
  "features": {
    "allowColdStorage": true,
    "allowExecution": true,
    "maxSourceLength": 50000
  }
}
```

### Environment Variables

| Variable | Description |
|----------|-------------|
| `CONSTELLATION_URL` | Base URL of Constellation service |
| `CONSTELLATION_API_KEY` | API key for authentication |
| `CONSTELLATION_AGENT_USER` | User identity for namespacing |

### Claude Desktop Integration

```json
{
  "mcpServers": {
    "constellation": {
      "command": "npx",
      "args": ["-y", "constellation-mcp"],
      "env": {
        "CONSTELLATION_URL": "https://constellation.internal.acme.com",
        "CONSTELLATION_API_KEY": "key_xxx",
        "CONSTELLATION_AGENT_USER": "alice@acme.com"
      }
    }
  }
}
```

---

## HTTP API Additions

The MCP server is a thin client. Some Constellation API additions are needed:

### `GET /modules/:name` (new)

Returns detailed module schema. Currently `/modules` returns a list; this adds individual lookup.

### `POST /validate` (new)

Syntax-only validation without full compilation. Faster feedback for iterative authoring.

```json
POST /validate
{ "source": "in x: String\nout x" }

Response:
{ "valid": true, "warnings": [] }
```

### `GET /pipelines` (new, if not exists)

List stored cold pipelines with their schemas.

### Agent Headers Support

Constellation should log/trace these headers when present:
- `X-Constellation-Agent`
- `X-Constellation-Request-ID`

---

## Security Considerations

### API Key Scoping

MCP servers should use scoped API keys:

| Scope | Permissions |
|-------|-------------|
| `read` | List modules, list pipelines, validate |
| `execute` | Above + run/execute pipelines |
| `write` | Above + store cold pipelines |
| `admin` | Above + access all namespaces |

### Input Validation

The MCP server should enforce:
- Maximum source length (prevent DoS via huge pipelines)
- Rate limiting at MCP level (complement to Constellation rate limiting)
- Input sanitization before forwarding

### No Credential Exposure

MCP resources/tools should never expose:
- API keys
- Internal service URLs
- Other users' pipeline contents (namespace isolation)

---

## Implementation Phases

### Phase 1: Core MCP Server (TypeScript)

**Deliverables:**
- MCP server package (`constellation-mcp`)
- Static documentation resources
- Dynamic module listing resource
- `validate`, `compile`, `execute` tools
- Basic configuration

**Constellation changes:**
- `GET /modules/:name` endpoint
- `POST /validate` endpoint
- Agent header logging

### Phase 2: Enhanced Discovery

**Deliverables:**
- Module search/filter tool
- Pipeline listing resources
- `write_pipeline` prompt with live module context
- `debug_pipeline` prompt with error reference

### Phase 3: Multi-Agent Polish

**Deliverables:**
- Cold pipeline namespacing
- Per-agent metrics in Constellation
- Scoped API key support
- Admin tools for namespace management

### Phase 4: Advanced Features

**Deliverables:**
- DAG visualization resource (SVG/Mermaid)
- Execution history resource (per-agent filtered)
- Pipeline diff tool (compare versions)
- Batch execution tool

---

## Alternatives Considered

### Embed MCP in Constellation HTTP Server (Scala)

**Pros:**
- Single deployment
- Native access to internals

**Cons:**
- MCP SDK less mature in Scala
- Tighter coupling
- Harder to version independently

**Decision:** Standalone TypeScript server. Can revisit if MCP-over-HTTP becomes standard.

### Shared MCP Server (multi-tenant)

**Pros:**
- Single deployment for team

**Cons:**
- Complex auth/session management
- Single point of failure
- Harder to configure per-developer

**Decision:** Each developer runs their own MCP server. Stateless design makes this simple.

### GraphQL Instead of REST

**Pros:**
- Flexible queries for module discovery

**Cons:**
- Overkill for this use case
- Additional complexity

**Decision:** REST is sufficient. Module list is small, detailed queries are infrequent.

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Time to first working pipeline (new user + LLM) | < 5 minutes |
| Validation round-trip latency | < 200ms |
| Module discovery freshness | < 30s staleness |
| Agent attribution coverage | 100% of MCP-originated requests |

---

## Open Questions

1. **Should MCP server support SSE for streaming execution output?**
   - Long-running pipelines might benefit from progress updates
   - Adds complexity; defer to Phase 4?

2. **How to handle module version mismatches?**
   - LLM writes pipeline for module v2, but server has v1
   - Include version in validation errors?

3. **Should there be a "playground" namespace for ephemeral experiments?**
   - Auto-cleanup after 24h
   - No namespace prefix required

4. **Integration with VSCode extension?**
   - Extension already has LSP; should it also connect via MCP?
   - Could provide unified experience across IDEs

---

## References

- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [MCP TypeScript SDK](https://github.com/modelcontextprotocol/typescript-sdk)
- [RFC-013: Production Readiness](./rfc-013-production-readiness.md)
- [RFC-015: Pipeline Lifecycle](./rfc-015-pipeline-lifecycle.md)
