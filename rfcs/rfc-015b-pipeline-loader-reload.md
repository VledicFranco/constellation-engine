# RFC-015b: Pipeline Loader, Hot-Reload, and Versioning

**Status:** Implemented
**Priority:** P1 (Core HTTP Module)
**Author:** Human + Claude
**Created:** 2026-02-02
**Parent:** [RFC-015: Pipeline Lifecycle Management](./rfc-015-pipeline-lifecycle.md)
**Depends On:** RFC-015 (Phase 0: terminology rename)

---

## Summary

Add a startup pipeline loader that reads `.cst` files from a configured directory at server boot, a hot-reload endpoint for updating cold pipelines without restarting, and a version history system that enables instant rollback to any previous version.

**Phases covered:**
- Phase 3: Startup pipeline loader
- Phase 4a: Versioning + hot-reload

---

## Motivation

Cold pipelines (the primary production use case) require pre-loading — compiling `.cst` files and storing them in `PipelineStore` before accepting requests. Currently this must be done programmatically. There is no declarative way to say "load all pipelines from this directory at startup."

Once loaded, updating a pipeline requires restarting the server. There is no hot-reload endpoint, no version history, and no rollback capability.

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
  case HashOnly          // No alias, only structural hash
}
```

### Alias Strategies

The `AliasStrategy` determines how loaded pipelines are named in `PipelineStore`:

#### `FileName` (default)

The simplest strategy. The filename without extension becomes the alias:

```
pipelines/
  scoring.cst          → alias: "scoring"
  enrichment.cst       → alias: "enrichment"
  my-pipeline.cst      → alias: "my-pipeline"
```

**Use when:** All pipeline files are in a single flat directory with unique filenames. This is the recommended default for most deployments.

**Collision handling:** If two files in different subdirectories have the same filename (with `recursive = true`), the loader logs an error and skips the duplicate. The first file loaded wins.

#### `RelativePath`

The path relative to the configured directory (without extension) becomes the alias. Useful when pipelines are organized in subdirectories:

```
pipelines/
  scoring/
    v1.cst             → alias: "scoring/v1"
    v2.cst             → alias: "scoring/v2"
  enrichment/
    main.cst           → alias: "enrichment/main"
    experimental.cst   → alias: "enrichment/experimental"
```

**Use when:** You need namespacing, multiple versions of the same logical pipeline, or team-based directory organization. Requires `recursive = true` to scan subdirectories.

**Path separators:** Always uses `/` regardless of OS. Windows `\` is normalized to `/`.

#### `HashOnly`

No alias is created. Pipelines are stored by structural hash only:

```
pipelines/
  scoring.cst          → hash: "abc123..." (no alias)
  enrichment.cst       → hash: "def456..." (no alias)
```

**Use when:** Pipelines are referenced by structural hash in a system that manages its own naming. Uncommon, but useful for content-addressed workflows where callers already know the hash.

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_PIPELINE_DIR` | (none) | Directory to load `.cst` files from at startup |
| `CONSTELLATION_PIPELINE_RECURSIVE` | `false` | Scan subdirectories |
| `CONSTELLATION_PIPELINE_FAIL_ON_ERROR` | `false` | Fail startup if any pipeline fails to compile |
| `CONSTELLATION_PIPELINE_ALIAS_STRATEGY` | `filename` | Alias strategy: `filename`, `relative-path`, or `hash-only` |

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
- Alias strategies: FileName, RelativePath, HashOnly — each verified
- FileName collision with `recursive = true` → error logged, first file wins

---

## Phase 4a: Pipeline Versioning and Hot-Reload

### Pipeline Versioning

Every named pipeline maintains an ordered version history. Each reload creates a new version rather than silently replacing the old one.

```scala
case class PipelineVersion(
  version: Int,              // monotonic, auto-incremented (v1, v2, v3...)
  structuralHash: String,
  createdAt: Instant,
  source: Option[String]     // retained for reload-from-file traceability
)
```

The alias for a pipeline name (e.g., `"scoring"`) always points to the **active** version. By default that's the latest, but pinning and rollback can change it.

**Version lifecycle:**

- The first `POST /compile` or startup load creates version 1
- Each `POST /pipelines/:name/reload` creates the next version (v2, v3, ...)
- Rollback re-points the alias to an earlier version (no recompilation)
- Old versions remain in `PipelineStore` by structural hash until explicitly deleted
- Version numbers are monotonic integers — simple, unambiguous, no semver complexity

### Version Management Endpoints

```
GET  /pipelines/:name/versions            → list version history
POST /pipelines/:name/rollback            → rollback to previous active version
POST /pipelines/:name/rollback/:version   → rollback to specific version
```

### Hot-Reload Endpoint

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
  "changed": true,
  "version": 3
}
```

If the source hasn't changed (same structural hash), returns `changed: false` — no recompilation occurs, no new version is created.

#### Behavior

1. Compile the new source
2. Compute structural hash
3. If same hash as current alias target → `changed: false`, no-op
4. Store new `PipelineImage`
5. Create new `PipelineVersion` entry
6. Update alias to point to new structural hash
7. Old image remains in store (other aliases or hashes may reference it)
8. In-flight executions of the old version complete normally (they hold a reference to the old `LoadedPipeline`)

#### File-Based Reload

When a pipeline loader is configured, also support reloading from the file:

```
POST /pipelines/:name/reload
```

(No body — re-reads the original `.cst` file from disk.)

This enables CI/CD workflows:
1. Update `.cst` file on disk (via ConfigMap update, file sync, etc.)
2. `POST /pipelines/scoring/reload` → server re-reads and recompiles
3. No restart needed

#### Caching Interaction

When a cold pipeline is reloaded:
1. The new `PipelineImage` is stored in `PipelineStore` (replacing the alias target)
2. `CompilationCache` is **not** invalidated — it uses source hash as key, and the new source has a different hash. The old cache entry will eventually be evicted by LRU
3. If the same new source was already hot-executed earlier, `CompilationCache` may have a hit for it — this is correct and avoids redundant compilation

#### Endpoint Variants

| Request | Behavior |
|---------|----------|
| `POST /pipelines/:name/reload` with `source` body | Compile from provided source |
| `POST /pipelines/:name/reload` without body | Re-read from original file path (requires pipeline loader) |
| `POST /pipelines/:name/reload` with `source` + `canary` | Compile and start canary deployment (see [RFC-015c](./rfc-015c-canary-releases.md)) |
| `POST /pipelines/:name/reload` without `source`, with `canary` | Re-read from file and start canary deployment |

### What Changes

| Component | Change |
|-----------|--------|
| `ConstellationRoutes.scala` | Add `/pipelines/:name/reload`, `/pipelines/:name/versions`, `/pipelines/:name/rollback` routes |
| `ApiModels.scala` | Add `ReloadRequest`, `ReloadResponse`, `PipelineVersion` |
| `PipelineLoader.scala` | Track source file paths for file-based reload |
| New: `PipelineVersionStore.scala` | Version history tracking per named pipeline |

### Tests

#### Versioning tests
- Reload creates a new version, version number increments
- Reload with same source → `changed: false`, no new version
- `GET /pipelines/:name/versions` returns ordered version history
- Rollback to previous version → alias re-pointed, no recompilation
- Rollback to specific version → works
- Rollback non-existent version → 404
- Version history preserved across multiple reloads

#### Hot-reload tests
- Reload with new source → alias updated, new hash returned
- Reload non-existent pipeline → 404
- Reload from file (pipeline loader configured) → works
- Reload from file (no pipeline loader) → 400 with error
- In-flight execution uses old version while reload happens
- Concurrent reloads of same pipeline → serialized, both succeed

---

## Design Decisions

**D1: Startup loader reads source files, not serialized images.** Even though persistent PipelineStore ([RFC-015d](./rfc-015d-persistent-pipeline-store.md)) can store serialized images, the startup loader always reads `.cst` source and compiles it. This ensures pipelines are always compiled with the current compiler version, avoiding stale/incompatible serialized state.

**D2: Hot-reload is explicit, not automatic.** The `/pipelines/:name/reload` endpoint must be called explicitly rather than watching for file changes. This is simpler, more predictable, and avoids platform-specific file watching issues. File watching can be added as a separate follow-up.

**D3: Old versions are not garbage collected.** When a pipeline is reloaded, the old `PipelineImage` remains in the store (referenced by its structural hash and version history). Explicit `DELETE /pipelines/:ref` is the cleanup mechanism. Automatic GC adds complexity around determining when an image is truly unreferenced — especially during canary deployments where both old and new versions are actively serving traffic.

**D4: Monotonic integer versions, not semver.** Pipeline versions use simple incrementing integers (v1, v2, v3) rather than semantic versioning. Pipelines don't have a meaningful notion of "breaking" vs "non-breaking" changes at the version level — any input/output schema change is caught at compile time. Semver would add cognitive overhead without providing actionable information. The version number's purpose is ordering and rollback reference, not compatibility signaling.
