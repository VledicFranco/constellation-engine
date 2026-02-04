# RFC-015d: Persistent PipelineStore

**Status:** Implemented
**Priority:** P1 (Core HTTP Module)
**Author:** Human + Claude
**Created:** 2026-02-02
**Parent:** [RFC-015: Pipeline Lifecycle Management](./rfc-015-pipeline-lifecycle.md)
**Depends On:** RFC-015 (Phase 0: terminology rename)

---

## Summary

Add a filesystem-backed `PipelineStore` implementation so cold pipelines, aliases, version history, and syntactic indices survive server restarts without recompilation.

---

## Motivation

`PipelineStore` is in-memory. Every compiled pipeline, alias, and syntactic index is lost when the server restarts. For cold pipelines (the primary production use case), this means:

- After every deploy, all pipelines must be recompiled
- The startup loader ([RFC-015b](./rfc-015b-pipeline-loader-reload.md)) handles the case where `.cst` source files are available, but recompilation on every restart is wasteful
- Pipelines compiled via `POST /compile` or `POST /run` (hot → cold promotion) are lost entirely if the original source isn't on disk

Persistent storage eliminates this: pipeline images are written to disk on compilation and loaded on startup, skipping recompilation.

---

## Pluggable Backend

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

The `FileSystemPipelineStore` wraps `InMemoryPipelineStore` as a cache layer:
- **Reads** are served from the in-memory delegate (fast)
- **Writes** go to both the in-memory delegate and the filesystem (durable)
- **On startup**, the filesystem store loads all persisted images into the in-memory delegate

---

## Filesystem Layout

```
.constellation-store/
  images/
    abc123.json         # PipelineImage serialized as JSON
    def456.json
  versions/             # Only populated when RFC-015b (versioning) is implemented
    scoring.json        # Version history for "scoring" pipeline
    enrichment.json     # Version history for "enrichment" pipeline
  aliases.json          # { "scoring": "abc123", "enrichment": "def456" }
  syntactic-index.json  # Syntactic hash → structural hash mapping
```

> **Note on versioning:** The `versions/` directory stores version history introduced by [RFC-015b](./rfc-015b-pipeline-loader-reload.md). If RFC-015d is implemented before RFC-015b, the persistent store operates without version history — it persists images, aliases, and the syntactic index only. The `versions/` directory is created on demand when version tracking is active.

### File Formats

**`images/<hash>.json`** — one file per pipeline image, keyed by structural hash:
```json
{
  "structuralHash": "abc123...",
  "dagSpec": { ... },
  "metadata": {
    "compiledAt": "2026-02-02T14:30:00Z",
    "sourceHash": "xyz789...",
    "moduleCount": 5,
    "inputCount": 2,
    "outputCount": 1
  }
}
```

**`versions/<name>.json`** — version history per named pipeline:
```json
{
  "pipelineName": "scoring",
  "versions": [
    { "version": 1, "structuralHash": "abc123...", "createdAt": "2026-02-02T14:30:00Z" },
    { "version": 2, "structuralHash": "def456...", "createdAt": "2026-02-02T15:00:00Z" }
  ],
  "activeVersion": 2
}
```

**`aliases.json`** — name → structural hash mapping:
```json
{
  "scoring": "def456...",
  "enrichment": "ghi789..."
}
```

> **Note:** `aliases.json` is intentionally redundant with the `activeVersion` in `versions/<name>.json`. The alias file enables O(1) startup loading of the name → hash mapping without parsing every version history file. The version files are the source of truth; `aliases.json` is a denormalized index rebuilt on startup if missing or corrupted.

**`syntactic-index.json`** — syntactic hash → structural hash mapping (for deduplication):
```json
{
  "syn-abc...": "struct-abc...",
  "syn-def...": "struct-def..."
}
```

---

## Design Constraint

`PipelineImage` contains `DagSpec`, which is fully serializable (case classes, enums, UUIDs). The `LoadedPipeline` contains live `Module.Uninitialized` instances (closures) which are not serializable. Therefore:

- **Persist:** `PipelineImage` (serializable artifact)
- **Don't persist:** `LoadedPipeline` (reconstructed at load time via `PipelineImage.rehydrate()`)

This is consistent with RFC-014's design separation between serializable images and executable pipelines.

---

## Server Builder Extension

```scala
ConstellationServer.builder(constellation, compiler)
  .withPersistentPipelineStore(Paths.get(".constellation-store"))
  .run
```

---

## Interaction with Startup Loader

When both a persistent store and a startup loader ([RFC-015b](./rfc-015b-pipeline-loader-reload.md)) are configured:

1. The persistent store loads all previously-stored images on startup
2. The startup loader scans `.cst` files and compiles them
3. For files whose syntactic hash is already in the persistent store → skip (no recompilation)
4. For new or changed files → compile and persist

This means: first deploy compiles everything. Subsequent restarts only recompile changed files.

---

## What Changes

| Component | Change |
|-----------|--------|
| New: `FileSystemPipelineStore.scala` | Filesystem-backed PipelineStore |
| `PipelineImage.scala` | Add Circe codecs for JSON serialization |
| `ConstellationServer.scala` | Add `withPersistentPipelineStore()` |

## What Doesn't Change

| Component | Reason |
|-----------|--------|
| `PipelineStore` trait | Interface unchanged |
| `ConstellationRoutes` | Uses `PipelineStore` trait, backend-agnostic |
| `InMemoryPipelineStore` | Still the default |

---

## Tests

- Store → restart → load → pipelines still available
- Concurrent writes don't corrupt filesystem
- Corrupted file → log warning, skip (don't crash)
- Filesystem store with in-memory cache → reads served from memory
- Version history persisted and restored across restarts
- Aliases persisted and restored across restarts
- Syntactic index persisted — deduplicated loads on restart
- Large store (hundreds of pipelines) → startup time acceptable

---

## Design Decisions

**D1: Filesystem persistence over database.** For single-instance deployments (the primary use case for the HTTP module), filesystem persistence is simpler, has no external dependencies, and is easy to inspect/debug. The JSON files are human-readable, can be version-controlled, and can be backed up with standard tools. Database backends (PostgreSQL, Redis) can be added later for multi-instance deployments.
