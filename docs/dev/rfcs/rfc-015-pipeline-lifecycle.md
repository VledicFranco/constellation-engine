# RFC-015: Pipeline Lifecycle Management

**Status:** Draft
**Priority:** P1 (Core HTTP Module)
**Author:** Human + Claude
**Created:** 2026-02-02

---

## Summary

Standardize "pipeline" as the canonical term for a compiled constellation `.cst` program, formalize the distinction between **hot pipelines** (ad-hoc, compiled on-the-fly) and **cold pipelines** (pre-loaded, stored for execution-by-reference), and implement the full pipeline lifecycle within the HTTP API module — covering suspension/resumption over HTTP, pipeline persistence, startup loading, and hot-reload.

This RFC introduces:
1. **Terminology standardization** — rename "program" to "pipeline" across the codebase, docs, API, and VSCode extension; define hot/cold as first-class concepts
2. **Suspension-aware HTTP endpoints** — expose `DataSignature` status, missing inputs, and resume-by-ID via REST
3. **Pipeline persistence** — pluggable `PipelineStore` backend (filesystem, database) so cold pipelines survive restarts
4. **Startup pipeline loader** — load `.cst` files from a configured directory at server boot
5. **Hot-reload** — recompile and re-alias a cold pipeline without restarting the server

---

## Motivation

Constellation Engine's HTTP API module (`constellation-http-api`) serves two audiences:

1. **Developers prototyping** via the dashboard — send source, see outputs, iterate
2. **Applications embedding** the HTTP server — compile once, execute many times, manage pipeline versions

Both audiences face gaps:

### Gap 0: Inconsistent Terminology

The codebase uses "program" to refer to a compiled constellation `.cst` file in some places (`ProgramStore`, `ProgramImage`, `LoadedProgram`), "DAG" in others (`DagSpec`, `dagName`), and "script" in the dashboard. Users encounter all three terms without clear definitions. More critically, there is no vocabulary for distinguishing the two fundamentally different ways pipelines are used:

- **Hot pipelines** — source is compiled and executed in one request, not intended for reuse
- **Cold pipelines** — compiled once, stored, and executed repeatedly by reference

This distinction affects caching strategy, API design, operational model, and documentation. Without naming it, users cannot reason about it.

### Gap 1: Undocumented Execution Modes

The HTTP API supports three distinct execution patterns, but none are documented:

| Mode | Endpoints | Description | Documented? |
|------|-----------|-------------|-------------|
| Hot | `POST /run` | Send source + inputs, get outputs | Partially |
| Warm | `POST /compile` then `POST /execute` | Compile once, execute by reference | No |
| Cold | Pre-load pipelines, `POST /execute` by name | Pipelines available at startup | No (doesn't exist) |

Users discover these patterns by reading source code or guessing. The `/programs/*` management endpoints (list, get, delete, alias) are not documented in the website or README.

### Gap 2: Suspension Not Exposed via HTTP

RFC-014 implemented full suspend/resume semantics at the library level. The `DataSignature` returned by `constellation.run()` includes `status`, `missingInputs`, `pendingOutputs`, and `suspendedState`. But the HTTP API discards all of this:

- `POST /execute` returns only `{ success, outputs, error }` — no status, no missing inputs
- `POST /run` returns only `{ success, outputs, structuralHash, error }` — same limitation
- There is no `POST /executions/:id/resume` endpoint
- There is no way to list or manage suspended executions via HTTP

This means HTTP consumers cannot use multi-step workflows at all. The suspension feature — one of the most architecturally significant capabilities — is invisible to HTTP clients.

### Gap 3: Pipelines Don't Survive Restarts

`ProgramStore` is in-memory. Every compiled pipeline, alias, and syntactic index is lost when the server restarts. For cold pipelines (the primary production use case), this means:

- After every deploy, all pipelines must be recompiled
- There is no startup loader to restore previously-compiled pipelines
- The dashboard file browser reads `.cst` files from disk but has no connection to `ProgramStore` — browsing a file doesn't load it as a cold pipeline

### Gap 4: No Hot-Reload

Once a cold pipeline is compiled and aliased, the only way to update it is to:
1. Stop the server
2. Change the `.cst` file
3. Restart the server (losing all pipelines)
4. Recompile everything

There is no endpoint to recompile a named pipeline from new source. The existing `PUT /programs/:name/alias` can repoint a name to a different structural hash, but requires the new version to be compiled first via a separate `/compile` call.

### What This Enables

After this RFC:

- The entire codebase, docs, API, and tooling use **"pipeline"** consistently
- Users understand the **hot vs cold** distinction and choose the right mode for their use case
- A deployment can **pre-load production pipelines** from a directory at startup, making them immediately available by name
- An HTTP client can **start a multi-step workflow**, receive the list of missing inputs, and **resume** with additional data across separate HTTP requests
- A CI/CD system can **hot-reload a pipeline** by posting updated source to a single endpoint, without restarting the server
- The dashboard file browser can **load pipelines into PipelineStore**, bridging the gap between file browsing and pipeline management

---

## Design Principles

| Principle | Rationale |
|-----------|-----------|
| **Pipeline as the unit of work** | A `.cst` file compiles to a pipeline. "Program" was ambiguous (could mean user code, a running process, etc.). "Pipeline" is precise: a directed acyclic graph of modules with typed inputs and outputs. |
| **Hot/cold as first-class concepts** | These aren't just "ways to call the API" — they have different caching strategies, lifecycle semantics, and operational models. Naming them makes them designable. |
| **Library-first** | All new capabilities are exposed through the Scala library API first, then surfaced in HTTP. The HTTP module is a thin adapter. |
| **Pluggable persistence** | `PipelineStore` is a trait with in-memory (default) and persistent (filesystem, custom) backends. The HTTP module doesn't know which backend is active. |
| **Clean breaks allowed** | No external consumers exist yet. We can rename types, change endpoints, and restructure responses without deprecation. This freedom won't last — use it now for the terminology rename. |
| **Opt-in complexity** | Suspension-aware endpoints, persistence, startup loading, and hot-reload are all opt-in. The default server works exactly as today. |
| **Content-addressed foundation** | All pipeline identity flows through structural hashes (from RFC-014). Persistence and hot-reload build on this — same source = same hash = no-op. |

---

## Phase 0: Terminology Standardization

### Core Definitions

| Term | Definition |
|------|-----------|
| **Pipeline** | A compiled constellation `.cst` source. A DAG of typed module calls with declared inputs and outputs. Replaces "program" everywhere in the codebase. |
| **Hot pipeline** | A pipeline compiled and executed in a single request (`POST /run`). The image is stored in `PipelineStore` by structural hash, enabling deduplication and later promotion to cold. The caller doesn't manage the stored image directly but benefits from it: identical source produces the same hash, so millions of requests with the same source compile once and store one image. The structural hash returned in the response lets callers switch to `POST /execute` (cold path) for subsequent requests if desired. |
| **Cold pipeline** | A pipeline compiled once, stored in `PipelineStore`, and executed repeatedly by reference (`POST /execute`). Loaded at startup from disk or compiled via `POST /compile`. |
| **Warm pipeline** | A pipeline explicitly compiled via `POST /compile` and stored with a name for later execution via `POST /execute`. The caller separates compilation from execution. Once stored, it behaves identically to a cold pipeline. |
| **Pipeline image** | The serializable, content-addressed artifact produced by compilation. Contains `DagSpec`, structural hash, metadata. Can be persisted and transferred. Replaces `ProgramImage`. |
| **Loaded pipeline** | A pipeline image combined with live synthetic modules, ready for execution. Cannot be serialized (contains closures). Replaces `LoadedProgram`. |

### Hot vs Cold: Caching Implications

The hot/cold distinction has direct implications for the two caching layers:

```
                    ┌─────────────────────┐     ┌─────────────────────┐
  Hot pipeline ───> │ CompilationCache    │ ──> │ PipelineStore       │ ──> execute
  (POST /run)       │ (LRU, in-memory)    │     │ (content-addressed) │
                    │ Key: source hash    │     │ store by struct hash│
                    └─────────────────────┘     └─────────────────────┘
                    Avoids recompilation         Deduplicates + enables
                    on identical source          promotion to cold path

                    ┌─────────────────────┐
  Cold pipeline ──> │ PipelineStore       │ ───> rehydrate ───> execute
  (POST /execute)   │ (content-addressed) │      (from image)
                    │ Key: structural hash│
                    └─────────────────────┘
```

| Aspect | Hot Pipeline | Cold Pipeline |
|--------|-------------|---------------|
| **Compilation** | On-demand per request (cached by `CompilationCache`) | Once at load time |
| **Storage** | `CompilationCache` (LRU) + `PipelineStore` (content-addressed) | `PipelineStore` only |
| **Cache key** | Source hash + registry hash (syntactic) | Structural hash (semantic) |
| **Cache eviction** | LRU when cache is full | Explicit `DELETE /pipelines/:ref` |
| **Startup cost** | None (compiled on demand) | All pipelines compiled at boot |
| **Latency (first request)** | Compilation + execution | Execution only (pre-compiled) |
| **Latency (repeat request)** | Cache hit check + execution | Rehydrate + execution |
| **Memory footprint** | `CompilationCache` bounded (LRU); `PipelineStore` grows with unique sources (deduplicated) | Grows with number of stored pipelines |

**Key insight:** Both hot and cold pipelines flow through `PipelineStore`. The difference is in how they get there and what sits in front:

- **Hot path:** `CompilationCache` sits in front of compilation. It caches `CompilationOutput` by source hash, so identical source never recompiles. The compiled `PipelineImage` is then stored in `PipelineStore` by structural hash. This is why hashes were introduced as pipeline identifiers — content-addressed storage means millions of hot requests with the same source produce exactly one stored image, and the structural hash returned in the `/run` response gives callers a stable reference they can use to switch to the cold path (`POST /execute`) at any time.

- **Cold path:** Pipelines are already in `PipelineStore` (loaded at startup or compiled earlier). Execution reads directly from the store. `CompilationCache` is never consulted.

The two caching layers serve different purposes: `CompilationCache` avoids redundant compilation (source → compiled output), `PipelineStore` enables execution-by-reference (hash → pipeline image). They compose naturally: every compilation populates both.

When a hot pipeline is executed via `POST /run`:
1. `CachingLangCompiler` checks `CompilationCache` (source hash + registry hash)
2. On miss: full compilation pipeline runs, result stored in `CompilationCache`
3. The resulting `PipelineImage` is stored in `PipelineStore` (deduplicated by structural hash)
4. Execution runs, response includes `structuralHash` — the caller can use this with `POST /execute` to skip compilation on future requests
5. On subsequent identical `/run` requests: `CompilationCache` hit, no recompilation, same image already in `PipelineStore`

When a cold pipeline is executed via `POST /execute`:
1. `PipelineStore.get(ref)` retrieves the `PipelineImage` by name or hash
2. `PipelineImage.rehydrate()` reconstructs synthetic modules
3. Execution runs — `CompilationCache` is never consulted

### Rename Inventory

The following identifiers, endpoints, and documentation must be renamed:

#### Scala Types (12 classes/traits)

| Current | New | Module |
|---------|-----|--------|
| `ProgramStore` | `PipelineStore` | runtime |
| `ProgramStoreImpl` | `PipelineStoreImpl` | runtime |
| `ProgramImage` | `PipelineImage` | runtime |
| `LoadedProgram` | `LoadedPipeline` | runtime |
| `ProgramChangedError` | `PipelineChangedError` | runtime |
| `ProgramNotFoundError` | `PipelineNotFoundError` | runtime |
| `Program` (AST) | `Pipeline` | lang-ast |
| `IRProgram` | `IRPipeline` | lang-compiler |
| `TypedProgram` | `TypedPipeline` | lang-compiler |
| `ProgramSummary` | `PipelineSummary` | http-api |
| `ProgramListResponse` | `PipelineListResponse` | http-api |
| `ProgramDetailResponse` | `PipelineDetailResponse` | http-api |

#### Scala Methods/Properties

| Current | New | Location |
|---------|-----|----------|
| `constellation.programStore` | `constellation.pipelineStore` | `Constellation.scala` |
| `.withProgramStore(ps)` | `.withPipelineStore(ps)` | `ConstellationBuilder` |
| `CompilationOutput.program` | `CompilationOutput.pipeline` | `CompilationOutput.scala` |
| `IRGenerator.generate(program)` | `IRGenerator.generate(pipeline)` | `IRGenerator.scala` |
| `DagCompiler.compile(program)` | `DagCompiler.compile(pipeline)` | `DagCompiler.scala` |
| `TypeChecker.check(program)` | `TypeChecker.check(pipeline)` | `TypeChecker.scala` |
| `ConstellationParser.program` | `ConstellationParser.pipeline` | `ConstellationParser.scala` |

#### HTTP Endpoints

| Current | New |
|---------|-----|
| `GET /programs` | `GET /pipelines` |
| `GET /programs/:ref` | `GET /pipelines/:ref` |
| `DELETE /programs/:ref` | `DELETE /pipelines/:ref` |
| `PUT /programs/:name/alias` | `PUT /pipelines/:name/alias` |

#### Environment Variables (New in Phase 3)

These env vars are introduced by this RFC (Phase 3). They don't exist in the codebase today — this table documents the names to use.

| Variable | Purpose |
|----------|---------|
| `CONSTELLATION_PIPELINE_DIR` | Directory to load `.cst` files from at startup |
| `CONSTELLATION_PIPELINE_RECURSIVE` | Scan subdirectories |
| `CONSTELLATION_PIPELINE_FAIL_ON_ERROR` | Fail startup if any pipeline fails to compile |

#### File Renames

| Current | New |
|---------|-----|
| `ProgramStore.scala` | `PipelineStore.scala` |
| `ProgramStoreImpl.scala` | `PipelineStoreImpl.scala` |
| `ProgramImage.scala` | `PipelineImage.scala` |
| `LoadedProgram.scala` | `LoadedPipeline.scala` |
| `ProgramStoreTest.scala` | `PipelineStoreTest.scala` |
| `ProgramImageTest.scala` | `PipelineImageTest.scala` |
| `docs/constellation-lang/program-structure.md` | `docs/constellation-lang/pipeline-structure.md` |
| `website/docs/language/program-structure.md` | `website/docs/language/pipeline-structure.md` |

#### Documentation Updates

| Location | Scope |
|----------|-------|
| `docs/constellation-lang/*.md` | Replace "program" with "pipeline" (~30 occurrences) |
| `docs/embedding-guide.md` | Update `programStore` references, examples |
| `docs/api/openapi.yaml` | Rename `/programs` paths, update schemas/descriptions |
| `website/docs/**/*.md` | Mirror all doc changes (~50 occurrences) |
| `website/sidebars.ts` | Update `program-structure` → `pipeline-structure` |
| `README.md` | Update endpoint table, terminology |
| `CHANGELOG.md` | Update references to `/programs` endpoints |
| `CLAUDE.md` | Update terminology in quick-start section |
| `docs/dev/rfcs/rfc-014-*.md` | Update type names and endpoint paths |
| `ExampleServer.scala` | Update comments referencing `/programs` |

#### VSCode Extension

| Location | Change |
|----------|--------|
| `vscode-extension/src/panels/*.ts` | Replace "program" with "pipeline" in UI labels |
| `vscode-extension/src/test/fixtures/*.cst` | Update comments ("benchmark program" → "benchmark pipeline") |
| `vscode-extension/package.json` | Update command labels/descriptions if any reference "program" |

### Parser Compatibility

The AST type `Program` is renamed to `Pipeline`, but the **constellation-lang syntax is unchanged**. Users still write:

```constellation
in text: String
result = Uppercase(text)
out result
```

The rename is internal to the compiler — it affects the Scala types that represent a parsed `.cst` file, not the `.cst` syntax itself. No migration of `.cst` files is needed.

### Migration Strategy

The library has no external consumers yet — all usage is internal (example-app, tests, docs). This means we can do a **hard rename** with no deprecation period:

1. Rename all types, methods, files, and endpoints in a single commit
2. No type aliases, no endpoint redirects, no backward-compatibility shims
3. Update all documentation, OpenAPI spec, and VSCode extension in the same commit
4. Search-and-destroy any remaining "program" references (excluding general programming terms)

### What Changes

| Component | Change |
|-----------|--------|
| `runtime` module | Rename 6 types + their files, update all references |
| `lang-ast` module | Rename `Program` → `Pipeline` |
| `lang-compiler` module | Rename `IRProgram`, `TypedProgram`, update compiler pipeline |
| `lang-parser` module | Rename parser combinator (`val program` → `val pipeline`), update ~50 test variables |
| `http-api` module | Rename API model types, endpoint paths |
| `lang-lsp` module | Update references to `Program` AST type |
| `example-app` module | Update comments |
| All documentation | Replace "program" with "pipeline" throughout |
| VSCode extension | Update UI labels |
| OpenAPI spec | Rename paths and schemas |

### What Doesn't Change

| Component | Reason |
|-----------|--------|
| `.cst` file syntax | Internal rename, no language changes |
| `DagSpec` | Already uses "DAG" terminology, not "program" |
| `DataSignature` | Already pipeline-agnostic |
| `Module`, `ModuleBuilder` | Modules are components of a pipeline, not pipelines themselves |
| `CompilationCache` | Correct name — it caches compilations, not pipelines |
| `CacheBackend`, `CacheSerde` | Module-level caching SPI, unrelated to pipeline identity |

### Tests

- All existing tests pass after rename (this is a refactor, not a behavior change)
- `grep -rn "ProgramStore\|ProgramImage\|LoadedProgram\|IRProgram\|TypedProgram" modules/ --include="*.scala"` returns zero hits
- `grep -rn '"/programs' modules/ --include="*.scala"` returns zero hits
- Manual review of remaining "program" in comments to distinguish general usage from pipeline-specific references

---

## Execution Modes

### Hot Execution (Ad-Hoc)

**Endpoint:** `POST /run`

The caller provides source code and inputs in a single request. The server compiles, stores, executes, and returns outputs. This is the "serverless" mode — no prior setup needed.

```
Client                          Server
  │                               │
  │  POST /run                    │
  │  { source: "...",             │
  │    inputs: { x: 42 } }       │
  │──────────────────────────────>│
  │                               │ compile → store → execute
  │  { success: true,             │
  │    outputs: { result: 84 },   │
  │    structuralHash: "abc..." } │
  │<──────────────────────────────│
```

**Use cases:** Prototyping, dashboard "Run" button, one-off scripts, development iteration.

**Caching behavior:** `CompilationCache` caches `CompilationOutput` by source hash — identical source never recompiles. The `PipelineImage` is stored in `PipelineStore` by structural hash, deduplicated automatically. The `structuralHash` in the response lets callers promote to cold execution (`POST /execute`) at any time without recompilation.

### Warm Execution (Compile Once, Execute Many)

**Endpoints:** `POST /compile` then `POST /execute`

The caller compiles a pipeline first, receiving a structural hash and optional name. Subsequent executions reference the stored pipeline by name or hash, skipping compilation entirely.

```
Client                          Server
  │                               │
  │  POST /compile                │
  │  { source: "...",             │
  │    name: "myPipeline" }       │
  │──────────────────────────────>│
  │                               │ compile → store → alias
  │  { structuralHash: "abc...",  │
  │    name: "myPipeline" }       │
  │<──────────────────────────────│
  │                               │
  │  POST /execute                │
  │  { ref: "myPipeline",         │
  │    inputs: { x: 42 } }       │
  │──────────────────────────────>│
  │                               │ resolve → rehydrate → execute
  │  { success: true,             │
  │    outputs: { result: 84 } }  │
  │<──────────────────────────────│
  │                               │
  │  POST /execute                │
  │  { ref: "myPipeline",         │  (same pipeline, different inputs)
  │    inputs: { x: 100 } }      │
  │──────────────────────────────>│
  │                               │ resolve → rehydrate → execute
  │  { outputs: { result: 200 } } │
  │<──────────────────────────────│
```

**Use cases:** Production APIs, microservices that compile once at startup and serve many requests.

**Caching behavior:** The `/compile` step populates both `CompilationCache` and `PipelineStore`. Subsequent `/execute` calls read from `PipelineStore` only — `CompilationCache` is not consulted.

### Cold Execution (Pre-Loaded)

**Mechanism:** Server loads `.cst` files from a configured directory at startup, compiles them, and stores them by filename as aliases.

```
Server Startup                          Filesystem
  │                                       │
  │  Scan $CONSTELLATION_PIPELINE_DIR     │
  │──────────────────────────────────────>│
  │                                       │
  │  pipelines/                           │
  │    scoring.cst                        │
  │    enrichment.cst                     │
  │<──────────────────────────────────────│
  │                                       │
  │  compile("scoring.cst") → alias("scoring")
  │  compile("enrichment.cst") → alias("enrichment")
  │
  │  Ready. 2 pipelines loaded.
```

Then clients execute by name:

```
Client                          Server
  │  POST /execute                │
  │  { ref: "scoring",            │
  │    inputs: { ... } }          │
  │──────────────────────────────>│
  │                               │ resolve → rehydrate → execute
  │  { outputs: { score: 0.87 } } │
  │<──────────────────────────────│
```

**Use cases:** Production deployments, Docker containers with baked-in pipelines, Kubernetes pods with ConfigMap-mounted `.cst` files.

**Caching behavior:** Pipelines are compiled once at startup and stored in `PipelineStore`. `CompilationCache` may be populated as a side effect but is not used for cold execution. Hot-reload (Phase 4) recompiles and updates the stored image without clearing `CompilationCache`.

---

## Phase 1: Suspension-Aware HTTP Responses

### Extended Response Models

Add optional fields to existing responses (backward compatible):

```scala
// Extended ExecuteResponse
case class ExecuteResponse(
  success: Boolean,
  outputs: Option[Map[String, Json]] = None,
  error: Option[String] = None,
  // --- New fields (Phase 1) ---
  status: Option[String] = None,           // "completed" | "suspended" | "failed"
  executionId: Option[String] = None,      // UUID for resume reference
  missingInputs: Option[Map[String, String]] = None,  // name → type
  pendingOutputs: Option[List[String]] = None,
  resumptionCount: Option[Int] = None
)

// Extended RunResponse (same additions)
case class RunResponse(
  success: Boolean,
  outputs: Option[Map[String, Json]] = None,
  structuralHash: Option[String] = None,
  compilationErrors: Option[List[String]] = None,
  error: Option[String] = None,
  // --- New fields (Phase 1) ---
  status: Option[String] = None,
  executionId: Option[String] = None,
  missingInputs: Option[Map[String, String]] = None,
  pendingOutputs: Option[List[String]] = None,
  resumptionCount: Option[Int] = None
)
```

### Behavior Change

When `constellation.run()` returns a `DataSignature` with `status = Suspended`:

**Before (current):**
```json
{
  "success": true,
  "outputs": {}
}
```

**After:**
```json
{
  "success": true,
  "status": "suspended",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "outputs": { "partialResult": 42 },
  "missingInputs": { "approval": "Bool", "managerNotes": "String" },
  "pendingOutputs": ["finalScore", "recommendation"],
  "resumptionCount": 0
}
```

Existing clients that only read `success` and `outputs` continue to work unchanged. Clients aware of suspension can check `status` and proceed accordingly.

### What Changes

| Component | Change |
|-----------|--------|
| `ApiModels.scala` | Add optional fields to `ExecuteResponse`, `RunResponse` |
| `ConstellationRoutes.scala` | Map `DataSignature` fields to response, populate new fields |

### What Doesn't Change

| Component | Reason |
|-----------|--------|
| `DataSignature` | Already has all the information |
| `ConstellationImpl` | Already returns `DataSignature` |
| `SuspendableExecution` | Already works |
| `PipelineStore` | Not affected |

### Tests

- Existing `/run` and `/execute` tests pass unchanged (new fields are optional/`None`)
- New test: `/run` with partial inputs returns `status: "suspended"` and `missingInputs`
- New test: `/execute` with partial inputs returns `status: "suspended"`
- New test: Completed execution returns `status: "completed"` with `missingInputs: {}`

---

## Phase 2: Resume Endpoint

### New Endpoint

```
POST /executions/:id/resume
Content-Type: application/json

{
  "additionalInputs": {
    "approval": true,
    "managerNotes": "Looks good"
  }
}
```

**Response:** Same shape as `ExecuteResponse` (may suspend again if more inputs needed).

### Suspension Storage

The server needs to store `SuspendedExecution` state between requests. This uses the existing `SuspensionStore` trait from the runtime module:

```scala
// Already defined in runtime module
trait SuspensionStore[F[_]] {
  def save(state: SuspendedExecution): F[Unit]
  def load(executionId: UUID): F[Option[SuspendedExecution]]
  def delete(executionId: UUID): F[Unit]
  def list: F[List[SuspendedExecution]]
}
```

**Default:** In-memory `SuspensionStore` (sufficient for single-instance deployments).

### Server Builder Extension

```scala
ConstellationServer.builder(constellation, compiler)
  .withSuspensionStore(InMemorySuspensionStore.create)  // opt-in
  .run
```

When no suspension store is configured, resume returns `404 Not Found` (suspension state is discarded).

### Additional Endpoints

```
GET /executions                    → List suspended executions
GET /executions/:id                → Get suspension details
DELETE /executions/:id             → Abandon/delete a suspended execution
```

### Request/Response Models

```scala
case class ResumeRequest(
  additionalInputs: Option[Map[String, Json]] = None,
  resolvedNodes: Option[Map[String, Json]] = None  // Manual healing
)

case class ExecutionSummary(
  executionId: String,
  structuralHash: String,
  pipelineName: Option[String],
  resumptionCount: Int,
  missingInputs: Map[String, String],
  createdAt: String  // ISO-8601
)

case class ExecutionListResponse(
  executions: List[ExecutionSummary]
)
```

### Execution Flow

```
Client                          Server
  │                               │
  │  POST /run (partial inputs)   │
  │──────────────────────────────>│
  │                               │ execute → suspend → store in SuspensionStore
  │  { status: "suspended",       │
  │    executionId: "abc-123",    │
  │    missingInputs: {...} }     │
  │<──────────────────────────────│
  │                               │
  │  ... (hours/days pass) ...    │
  │                               │
  │  POST /executions/abc-123/resume
  │  { additionalInputs: {...} }  │
  │──────────────────────────────>│
  │                               │ load from SuspensionStore → resume → complete
  │  { status: "completed",       │
  │    outputs: { ... } }         │
  │<──────────────────────────────│
```

### What Changes

| Component | Change |
|-----------|--------|
| `ConstellationRoutes.scala` | Add `/executions/*` routes |
| `ConstellationServer.scala` | Add `withSuspensionStore()` builder method |
| `ApiModels.scala` | Add `ResumeRequest`, `ExecutionSummary`, `ExecutionListResponse` |

### What Doesn't Change

| Component | Reason |
|-----------|--------|
| `SuspendableExecution` | Resume logic already works |
| `SuspensionStore` trait | Already defined |
| `DataSignature` | Already captures suspension state |

### Tests

- Resume a suspended execution, verify outputs
- Resume with wrong execution ID → 404
- Resume without suspension store configured → 404 with clear error
- Resume with type-mismatched inputs → 400
- Concurrent resume attempts → 409 Conflict
- List suspended executions
- Delete a suspended execution, verify resume returns 404

---

## Phase 3: Startup Pipeline Loader

### Configuration

```scala
case class PipelineLoaderConfig(
  directory: Path,                        // Directory containing .cst files
  recursive: Boolean = false,             // Scan subdirectories
  failOnError: Boolean = false,           // Fail startup on compilation error (vs log warning)
  aliasStrategy: AliasStrategy = AliasStrategy.FileName  // How to name pipelines
)

enum AliasStrategy {
  case FileName          // scoring.cst → "scoring"
  case RelativePath      // pipelines/scoring.cst → "pipelines/scoring"
  case None              // No alias, only structural hash
}
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_PIPELINE_DIR` | (none) | Directory to load `.cst` files from at startup |
| `CONSTELLATION_PIPELINE_RECURSIVE` | `false` | Scan subdirectories |
| `CONSTELLATION_PIPELINE_FAIL_ON_ERROR` | `false` | Fail startup if any pipeline fails to compile |

### Server Builder Extension

```scala
ConstellationServer.builder(constellation, compiler)
  .withPipelineLoader(PipelineLoaderConfig(
    directory = Paths.get("/opt/constellation/pipelines"),
    recursive = true,
    failOnError = true
  ))
  .run
```

### Startup Behavior

1. Scan `directory` for `*.cst` files
2. For each file:
   a. Read source
   b. Compute syntactic hash
   c. Check if already in `PipelineStore` (via `lookupSyntactic`) — skip if present
   d. Compile via `compiler.compileIO(source, name)`
   e. Store `PipelineImage` in `PipelineStore`
   f. Alias by filename (or configured strategy)
   g. Log: `Loaded pipeline 'scoring' (sha256:abc123...) from scoring.cst`
3. If `failOnError = true` and any compilation fails → abort startup with error details
4. If `failOnError = false` → log warnings, continue with successfully compiled pipelines

### Logging

```
INFO  - Loading pipelines from /opt/constellation/pipelines/
INFO  - Loaded 'scoring' (sha256:abc..., 5 modules, 2 inputs, 1 output)
INFO  - Loaded 'enrichment' (sha256:def..., 3 modules, 1 input, 2 outputs)
WARN  - Failed to compile 'broken.cst': Parse error at line 5: unexpected token
INFO  - Pipeline loading complete: 2 loaded, 1 failed
```

### What Changes

| Component | Change |
|-----------|--------|
| `ConstellationServer.scala` | Add `withPipelineLoader()`, call loader during startup |
| New: `PipelineLoader.scala` | Startup loading logic |
| New: `PipelineLoaderConfig.scala` | Configuration case class |

### Tests

- Load directory with 2 valid `.cst` files → both accessible via `/execute`
- Load directory with 1 valid, 1 invalid file, `failOnError = false` → valid one loaded, warning logged
- Load directory with 1 valid, 1 invalid file, `failOnError = true` → startup fails
- Load empty directory → no-op, server starts normally
- Load with `recursive = true` → finds files in subdirectories
- Reload same directory (no changes) → all deduplicated via syntactic hash
- Alias strategies: FileName, RelativePath, None

---

## Phase 4: Hot-Reload Endpoint

### New Endpoint

```
POST /pipelines/:name/reload
Content-Type: application/json

{
  "source": "in x: Int\nresult = Double(x)\nout result"
}
```

**Response:**

```json
{
  "success": true,
  "previousHash": "abc123...",
  "newHash": "def456...",
  "name": "scoring",
  "changed": true
}
```

If the source hasn't changed (same structural hash), returns `changed: false` — no recompilation occurs.

### Behavior

1. Compile the new source
2. Compute structural hash
3. If same hash as current alias target → `changed: false`, no-op
4. Store new `PipelineImage`
5. Update alias to point to new structural hash
6. Old image remains in store (other aliases or hashes may reference it)
7. In-flight executions of the old version complete normally (they hold a reference to the old `LoadedPipeline`)

### File-Based Reload

When a pipeline loader is configured, also support reloading from the file:

```
POST /pipelines/:name/reload
```

(No body — re-reads the original `.cst` file from disk.)

This enables CI/CD workflows:
1. Update `.cst` file on disk (via ConfigMap update, file sync, etc.)
2. `POST /pipelines/scoring/reload` → server re-reads and recompiles
3. No restart needed

### Caching Interaction

When a cold pipeline is reloaded:
1. The new `PipelineImage` is stored in `PipelineStore` (replacing the alias target)
2. `CompilationCache` is **not** invalidated — it uses source hash as key, and the new source has a different hash. The old cache entry will eventually be evicted by LRU
3. If the same new source was already hot-executed earlier, `CompilationCache` may have a hit for it — this is correct and avoids redundant compilation

### Endpoint Variants

| Request | Behavior |
|---------|----------|
| `POST /pipelines/:name/reload` with `source` body | Compile from provided source |
| `POST /pipelines/:name/reload` without body | Re-read from original file path (requires pipeline loader) |

### What Changes

| Component | Change |
|-----------|--------|
| `ConstellationRoutes.scala` | Add `/pipelines/:name/reload` route |
| `ApiModels.scala` | Add `ReloadRequest`, `ReloadResponse` |
| `PipelineLoader.scala` | Track source file paths for file-based reload |

### Tests

- Reload with new source → alias updated, new hash returned
- Reload with same source → `changed: false`
- Reload non-existent pipeline → 404
- Reload from file (pipeline loader configured) → works
- Reload from file (no pipeline loader) → 400 with error
- In-flight execution uses old version while reload happens
- Concurrent reloads of same pipeline → serialized, both succeed

---

## Phase 5: Persistent PipelineStore

### Motivation

For production cold pipelines, pipeline images should survive restarts without recompilation. The startup loader (Phase 3) handles the case where source files are available, but persistent storage avoids recompilation entirely.

### Pluggable Backend

```scala
trait PipelineStore {
  // ... existing trait (unchanged interface, renamed from ProgramStore)
}

// Existing
class InMemoryPipelineStore extends PipelineStore { ... }

// New: filesystem-backed
class FileSystemPipelineStore(
  directory: Path,
  delegate: InMemoryPipelineStore  // in-memory cache layer
) extends PipelineStore { ... }
```

### Filesystem Layout

```
.constellation-store/
  images/
    abc123.json     # PipelineImage serialized as JSON
    def456.json
  aliases.json      # { "scoring": "abc123", "enrichment": "def456" }
  syntactic-index.json
```

### Design Constraint

`PipelineImage` contains `DagSpec`, which is fully serializable (case classes, enums, UUIDs). The `LoadedPipeline` contains live `Module.Uninitialized` instances (closures) which are not serializable. Therefore:

- **Persist:** `PipelineImage` (serializable artifact)
- **Don't persist:** `LoadedPipeline` (reconstructed at load time via `PipelineImage.rehydrate()`)

This is consistent with RFC-014's design separation between serializable images and executable pipelines.

### Server Builder Extension

```scala
ConstellationServer.builder(constellation, compiler)
  .withPersistentPipelineStore(Paths.get(".constellation-store"))
  .run
```

### What Changes

| Component | Change |
|-----------|--------|
| `PipelineStore.scala` | Add `FileSystemPipelineStore` |
| `PipelineImage.scala` | Add Circe codecs for JSON serialization |
| `ConstellationServer.scala` | Add `withPersistentPipelineStore()` |

### What Doesn't Change

| Component | Reason |
|-----------|--------|
| `PipelineStore` trait | Interface unchanged |
| `ConstellationRoutes` | Uses `PipelineStore` trait, backend-agnostic |
| `InMemoryPipelineStore` | Still the default |

### Tests

- Store → restart → load → pipelines still available
- Concurrent writes don't corrupt filesystem
- Corrupted file → log warning, skip (don't crash)
- Filesystem store with in-memory cache → reads served from memory

---

## Phase 6: Dashboard Integration

### Bridge File Browser → PipelineStore

The dashboard file browser currently reads `.cst` files from disk via `/api/v1/files` and sends source to `/api/v1/execute` (hot path). After Phase 3, files loaded at startup are already in `PipelineStore`.

Add a "Load" action in the file browser that:
1. Compiles the selected file via `/compile`
2. Aliases it by filename
3. Shows it in a "Loaded Pipelines" panel alongside file browser

### Pipelines Panel

New dashboard panel showing loaded pipelines:

| Pipeline | Hash | Inputs | Outputs | Actions |
|----------|------|--------|---------|---------|
| scoring | abc1... | x: Int, y: Float | score: Float | Execute, Reload, Delete |
| enrichment | def4... | text: String | entities: List | Execute, Reload, Delete |

### Execute from Pipelines Panel

Clicking "Execute" on a loaded pipeline:
1. Shows input form (populated from `inputSchema` in pipeline metadata)
2. Submits via `POST /execute { ref: "scoring", inputs: {...} }`
3. Shows outputs (or suspension state with missing inputs)

### Suspend/Resume in Dashboard

When an execution returns `status: "suspended"`:
1. Show partially computed outputs
2. Highlight missing inputs in the input form
3. "Resume" button submits `POST /executions/:id/resume` with filled-in missing inputs

This is a UX enhancement and can be implemented after the HTTP endpoints are stable.

---

## Implementation Order

| Phase | Depends On | Scope |
|-------|-----------|-------|
| Phase 0: Terminology rename | Nothing | Large: mechanical rename across codebase, well-defined |
| Phase 1: Suspension-aware responses | Phase 0 | Small: extend 2 response models, map fields |
| Phase 2: Resume endpoint | Phase 1 | Medium: new routes, SuspensionStore wiring |
| Phase 3: Startup loader | Phase 0 | Medium: new component, env vars, logging |
| Phase 4: Hot-reload | Phase 3 (for file-based reload) | Small: new endpoint, alias update |
| Phase 5: Persistent PipelineStore | Phase 0 | Medium: filesystem backend, Circe codecs |
| Phase 6: Dashboard integration | Phases 1-4 | Large: frontend work, new panel, UX |

Phase 0 must come first (all other phases use the new names). After Phase 0, Phases 1, 3, and 5 can proceed in parallel. Phase 2 depends on Phase 1. Phase 4 depends on Phase 3. Phase 6 depends on all others.

---

## Migration Guide

### Breaking Changes (No External Consumers)

> **Note:** Constellation Engine has no external consumers yet. All usage is internal (example-app, tests, docs). We can therefore make breaking changes freely across minor versions without deprecation periods. This section documents the changes for internal reference only.

**Scala API:** All "program" types are removed and replaced. `ProgramStore` → `PipelineStore`, `ProgramImage` → `PipelineImage`, `LoadedProgram` → `LoadedPipeline`, etc. No type aliases.

**HTTP API:** `/programs/*` endpoints are removed and replaced with `/pipelines/*`. No redirects.

**Environment variables:** New pipeline-related env vars use the `CONSTELLATION_PIPELINE_*` prefix (no existing env vars to rename — these are introduced by Phase 3).

### For Server Operators

**Default behavior unchanged.** Without configuring a pipeline loader, suspension store, or persistent store, the server behaves exactly as today.

To opt in:
```scala
ConstellationServer.builder(constellation, compiler)
  // Phase 2: Enable suspension
  .withSuspensionStore(InMemorySuspensionStore.create)
  // Phase 3: Load pipelines at startup
  .withPipelineLoader(PipelineLoaderConfig(
    directory = Paths.get("/opt/pipelines"),
    failOnError = true
  ))
  // Phase 5: Persist pipelines across restarts
  .withPersistentPipelineStore(Paths.get(".constellation-store"))
  .run
```

Or via environment variables:
```bash
CONSTELLATION_PIPELINE_DIR=/opt/pipelines
CONSTELLATION_PIPELINE_FAIL_ON_ERROR=true
```

---

## Future Work (Out of Scope)

| Feature | Rationale |
|---------|-----------|
| **File watching / inotify** | Automatic hot-reload when `.cst` files change on disk. Adds OS-specific complexity (Java WatchService). Better as a follow-up after manual reload is proven. |
| **Distributed PipelineStore** | Database-backed (PostgreSQL, Redis) PipelineStore for multi-instance deployments. Filesystem is sufficient for single-instance. |
| **Pipeline versioning UI** | Dashboard showing version history of a pipeline, with diff view. Depends on persistent store capturing version history. |
| **Suspension TTL / expiry** | Auto-cleanup of abandoned suspended executions after a configurable time. Simple to add to SuspensionStore later. |
| **Webhook on suspension** | Notify external systems when an execution suspends (e.g., send email, post to Slack). Application-level concern. |
| **Pipeline dependencies** | One pipeline importing/calling another. Fundamental language change, separate RFC. |

---

## Design Decisions

**D1: "Pipeline" over "program", "workflow", or "graph".** "Program" is overloaded (any code is a "program"). "Workflow" implies a sequence of steps (pipelines are DAGs, not sequences). "Graph" is too technical and doesn't convey the input-output nature. "Pipeline" is precise: data flows through a series of typed transformations. It's the standard term in data engineering, ML systems, and CI/CD — audiences that overlap with Constellation's target users.

**D2: Hot/cold as named concepts, not just usage patterns.** Without names, the distinction is implicit in which endpoints you call. With names, it becomes a designable axis: caching strategy differs (CompilationCache vs PipelineStore), lifecycle differs (ephemeral vs permanent), operational model differs (stateless vs stateful). Naming it enables documenting it, testing it, and optimizing for it.

**D3: Additive responses over new endpoints.** We extend existing `/execute` and `/run` responses with optional suspension fields rather than creating separate `/execute-with-suspend` endpoints. This keeps the API surface small and avoids forcing clients to choose between endpoint variants.

**D4: SuspensionStore is opt-in.** Without a configured store, suspended state is returned in the response but not persisted server-side. Clients can store it themselves (same as the library API pattern). The store is only needed for the resume endpoint.

**D5: Filesystem persistence over database.** For single-instance deployments (the primary use case for the HTTP module), filesystem persistence is simpler, has no external dependencies, and is easy to inspect/debug. Database backends can be added later.

**D6: Startup loader reads source files, not serialized images.** Even though persistent PipelineStore can store serialized images, the startup loader always reads `.cst` source and compiles it. This ensures pipelines are always compiled with the current compiler version, avoiding stale/incompatible serialized state.

**D7: Hot-reload is explicit, not automatic.** The `/pipelines/:name/reload` endpoint must be called explicitly rather than watching for file changes. This is simpler, more predictable, and avoids platform-specific file watching issues. File watching can be added as a separate follow-up.

**D8: Old versions are not garbage collected.** When a pipeline is reloaded, the old `PipelineImage` remains in the store (referenced by its structural hash). Explicit `DELETE /pipelines/:ref` is the cleanup mechanism. Automatic GC adds complexity around determining when an image is truly unreferenced.

**D9: Hard rename, no deprecation.** The library has no external consumers yet, so we do a clean break: rename everything in one commit with no type aliases or endpoint redirects. This keeps the codebase unambiguous — there is exactly one name for each concept, not two. If external consumers exist in the future, breaking changes will require deprecation cycles, but that constraint doesn't apply today.
