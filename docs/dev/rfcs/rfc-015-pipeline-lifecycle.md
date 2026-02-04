# RFC-015: Pipeline Lifecycle Management

**Status:** Implemented
**Priority:** P1 (Core HTTP Module)
**Author:** Human + Claude
**Created:** 2026-02-02

---

## Summary

Standardize "pipeline" as the canonical term for a compiled constellation `.cst` program, formalize the distinction between **hot pipelines** (ad-hoc, compiled on-the-fly) and **cold pipelines** (pre-loaded, stored for execution-by-reference), and implement the full pipeline lifecycle within the HTTP API module — covering suspension/resumption over HTTP, pipeline persistence, startup loading, hot-reload with versioning, and canary releases.

This is a **master RFC** that defines shared terminology and architecture. The implementation is split into child RFCs:

| Child RFC | Scope | Depends On |
|-----------|-------|------------|
| [RFC-015a](./rfc-015a-suspension-http.md) | Suspension-aware HTTP responses + resume endpoint | RFC-015 |
| [RFC-015b](./rfc-015b-pipeline-loader-reload.md) | Startup pipeline loader, hot-reload, versioning | RFC-015 |
| [RFC-015c](./rfc-015c-canary-releases.md) | Canary releases with observability | RFC-015b |
| [RFC-015d](./rfc-015d-persistent-pipeline-store.md) | Persistent PipelineStore (filesystem-backed) | RFC-015 |
| [RFC-015e](./rfc-015e-dashboard-integration.md) | Dashboard integration (pipelines panel, suspend/resume UI, canary UI) | RFC-015a, RFC-015b |
| [RFC-015f](./rfc-015f-qa-iteration-1.md) | QA Iteration 1 — post-implementation audit (16 findings) | RFC-015a through RFC-015e |

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

This means HTTP consumers cannot use multi-step workflows at all. The suspension feature — one of the most architecturally significant capabilities — is invisible to HTTP clients. See [RFC-015a](./rfc-015a-suspension-http.md).

### Gap 3: Pipelines Don't Survive Restarts

`ProgramStore` is in-memory. Every compiled pipeline, alias, and syntactic index is lost when the server restarts. For cold pipelines (the primary production use case), this means:

- After every deploy, all pipelines must be recompiled
- There is no startup loader to restore previously-compiled pipelines
- The dashboard file browser reads `.cst` files from disk but has no connection to `ProgramStore` — browsing a file doesn't load it as a cold pipeline

See [RFC-015b](./rfc-015b-pipeline-loader-reload.md) and [RFC-015d](./rfc-015d-persistent-pipeline-store.md).

### Gap 4: No Hot-Reload or Safe Rollouts

Once a cold pipeline is compiled and aliased, the only way to update it is to:
1. Stop the server
2. Change the `.cst` file
3. Restart the server (losing all pipelines)
4. Recompile everything

There is no endpoint to recompile a named pipeline from new source. No version history is maintained, so rollback is impossible. And there is no way to safely test a new pipeline version with a fraction of traffic before promoting it.

See [RFC-015b](./rfc-015b-pipeline-loader-reload.md) and [RFC-015c](./rfc-015c-canary-releases.md).

### What This Enables

After this RFC and its children:

- The entire codebase, docs, API, and tooling use **"pipeline"** consistently
- Users understand the **hot vs cold** distinction and choose the right mode for their use case
- A deployment can **pre-load production pipelines** from a directory at startup, making them immediately available by name
- An HTTP client can **start a multi-step workflow**, receive the list of missing inputs, and **resume** with additional data across separate HTTP requests
- Pipelines have **version history** with instant rollback to any previous version
- A CI/CD system can **canary-release a new pipeline version** with traffic splitting, per-version observability, and automatic rollback on error threshold breach
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
| **Opt-in complexity** | Suspension-aware endpoints, persistence, startup loading, hot-reload, canary releases, and dashboard integration are all opt-in. The default server works exactly as today. |
| **Content-addressed foundation** | All pipeline identity flows through structural hashes (from RFC-014). Persistence, hot-reload, and canary releases build on this — same source = same hash = no-op. |
| **Progressive rollouts** | Canary releases allow operators to validate new pipeline versions with a fraction of traffic before full promotion. Observability (per-version metrics) and automatic rollback provide safety nets for production deployments. |

---

## Phase 0: Terminology Standardization ✅ Implemented

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

#### Environment Variables (New in Child RFCs)

These env vars are introduced by child RFCs. They don't exist in the codebase today — this table documents the names to use.

| Variable | Purpose | Introduced By |
|----------|---------|---------------|
| `CONSTELLATION_PIPELINE_DIR` | Directory to load `.cst` files from at startup | RFC-015b |
| `CONSTELLATION_PIPELINE_RECURSIVE` | Scan subdirectories | RFC-015b |
| `CONSTELLATION_PIPELINE_FAIL_ON_ERROR` | Fail startup if any pipeline fails to compile | RFC-015b |
| `CONSTELLATION_PIPELINE_ALIAS_STRATEGY` | Alias strategy: `filename`, `relative-path`, or `hash-only` | RFC-015b |

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
| `lang-compiler` module | Rename `IRPipeline`, `TypedPipeline`, update compiler pipeline |
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

**Mechanism:** Server loads `.cst` files from a configured directory at startup, compiles them, and stores them by filename as aliases. See [RFC-015b](./rfc-015b-pipeline-loader-reload.md).

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

**Caching behavior:** Pipelines are compiled once at startup and stored in `PipelineStore`. `CompilationCache` may be populated as a side effect but is not used for cold execution. Hot-reload ([RFC-015b](./rfc-015b-pipeline-loader-reload.md)) recompiles and updates the stored image without clearing `CompilationCache`.

---

## Implementation Order

| Phase | RFC | Depends On | Scope |
|-------|-----|-----------|-------|
| Phase 0: Terminology rename ✅ | RFC-015 (this RFC) | Nothing | Large: mechanical rename across codebase, well-defined |
| Phase 1: Suspension-aware responses | [RFC-015a](./rfc-015a-suspension-http.md) | Phase 0 | Small: extend 2 response models, map fields |
| Phase 2: Resume endpoint | [RFC-015a](./rfc-015a-suspension-http.md) | Phase 1 | Medium: new routes, SuspensionStore wiring |
| Phase 3: Startup loader | [RFC-015b](./rfc-015b-pipeline-loader-reload.md) | Phase 0 | Medium: new component, env vars, logging |
| Phase 4a: Versioning + hot-reload | [RFC-015b](./rfc-015b-pipeline-loader-reload.md) | Phase 3 | Medium: version store, reload endpoint, version management |
| Phase 4b: Canary releases | [RFC-015c](./rfc-015c-canary-releases.md) | Phase 4a | Medium: canary router, weighted execution, metrics, auto-promote/rollback |
| Phase 5: Persistent PipelineStore | [RFC-015d](./rfc-015d-persistent-pipeline-store.md) | Phase 0 | Medium: filesystem backend, Circe codecs |
| Phase 6: Dashboard integration | [RFC-015e](./rfc-015e-dashboard-integration.md) | Phases 1-4 | Large: frontend work, new panel, canary UI, UX |

Phase 0 must come first (all other phases use the new names). After Phase 0, Phases 1, 3, and 5 can proceed in parallel. Phase 2 depends on Phase 1. Phase 4a depends on Phase 3. Phase 4b depends on Phase 4a (canary requires versioning to track old/new versions). Phase 6 depends on all others.

---

## Migration Guide

### Breaking Changes (No External Consumers)

> **Note:** Constellation Engine has no external consumers yet. All usage is internal (example-app, tests, docs). We can therefore make breaking changes freely across minor versions without deprecation periods. This section documents the changes for internal reference only.

**Scala API:** All "program" types are removed and replaced. `ProgramStore` → `PipelineStore`, `ProgramImage` → `PipelineImage`, `LoadedProgram` → `LoadedPipeline`, etc. No type aliases.

**HTTP API:** `/programs/*` endpoints are removed and replaced with `/pipelines/*`. No redirects.

**Environment variables:** New pipeline-related env vars use the `CONSTELLATION_PIPELINE_*` prefix (no existing env vars to rename — these are introduced by [RFC-015b](./rfc-015b-pipeline-loader-reload.md)).

### For Server Operators

**Default behavior unchanged.** Without configuring a pipeline loader, suspension store, or persistent store, the server behaves exactly as today.

To opt in:
```scala
ConstellationServer.builder(constellation, compiler)
  // RFC-015a: Enable suspension
  .withSuspensionStore(InMemorySuspensionStore.create)
  // RFC-015b: Load pipelines at startup
  .withPipelineLoader(PipelineLoaderConfig(
    directory = Paths.get("/opt/pipelines"),
    failOnError = true
  ))
  // RFC-015d: Persist pipelines across restarts
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

These features are related to the pipeline lifecycle but are explicitly out of scope for this RFC family. They may be addressed in future RFCs.

| Feature | Related RFC | Rationale |
|---------|------------|-----------|
| **File watching / inotify** | RFC-015b | Automatic hot-reload when `.cst` files change on disk. Adds OS-specific complexity (Java WatchService). Better as a follow-up after manual reload is proven. |
| **Distributed PipelineStore** | RFC-015d | Database-backed (PostgreSQL, Redis) PipelineStore for multi-instance deployments. Filesystem is sufficient for single-instance. |
| **Persistent canary state** | RFC-015c | Canary state surviving server restarts. Adds complexity (serializing in-flight metrics, resuming observation windows). In-memory is sufficient for initial release. |
| **Canary webhooks / alerting** | RFC-015c | Notify external systems (Slack, PagerDuty) on canary state transitions. Application-level concern, easy to add via hook on state change. |
| **A/B testing (multi-version split)** | RFC-015c | Running more than two versions simultaneously with configurable weights. Canary is a special case of A/B with two versions. Generalizing adds routing complexity. |
| **Version diff / changelog** | RFC-015b, 015e | Dashboard showing source diff between pipeline versions. Requires storing source text in version history (already captured in `PipelineVersion.source`), but the UI is non-trivial. |
| **Suspension TTL / expiry** | RFC-015a | Auto-cleanup of abandoned suspended executions after a configurable time. Simple to add to SuspensionStore later. |
| **Webhook on suspension** | RFC-015a | Notify external systems when an execution suspends. Application-level concern. |
| **Pipeline dependencies** | — | One pipeline importing/calling another. Fundamental language change, separate RFC. |

---

## Design Decisions

These design decisions apply to the master RFC (Phase 0 and overall architecture). Each child RFC contains its own design decisions for its specific scope.

**D1: "Pipeline" over "program", "workflow", or "graph".** "Program" is overloaded (any code is a "program"). "Workflow" implies a sequence of steps (pipelines are DAGs, not sequences). "Graph" is too technical and doesn't convey the input-output nature. "Pipeline" is precise: data flows through a series of typed transformations. It's the standard term in data engineering, ML systems, and CI/CD — audiences that overlap with Constellation's target users.

**D2: Hot/cold as named concepts, not just usage patterns.** Without names, the distinction is implicit in which endpoints you call. With names, it becomes a designable axis: caching strategy differs (CompilationCache vs PipelineStore), lifecycle differs (ephemeral vs permanent), operational model differs (stateless vs stateful). Naming it enables documenting it, testing it, and optimizing for it.

**D3: Hard rename, no deprecation.** The library has no external consumers yet, so we do a clean break: rename everything in one commit with no type aliases or endpoint redirects. This keeps the codebase unambiguous — there is exactly one name for each concept, not two. If external consumers exist in the future, breaking changes will require deprecation cycles, but that constraint doesn't apply today.
