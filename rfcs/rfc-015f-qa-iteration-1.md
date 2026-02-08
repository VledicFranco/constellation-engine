# RFC-015f: QA Iteration 1 — Post-Implementation Audit

**Status:** Implemented
**Priority:** P1 (Stability & Correctness)
**Author:** Human + Claude
**Created:** 2026-02-04
**Parent:** [RFC-015: Pipeline Lifecycle Management](./rfc-015-pipeline-lifecycle.md)
**Audits:** [RFC-015a](./rfc-015a-suspension-http.md), [RFC-015b](./rfc-015b-pipeline-loader-reload.md), [RFC-015c](./rfc-015c-canary-releases.md), [RFC-015d](./rfc-015d-persistent-pipeline-store.md), [RFC-015e](./rfc-015e-dashboard-integration.md)

---

## Summary

Post-implementation QA audit of the RFC-015 family (Phases 0–6). This document catalogs defects, race conditions, resource leaks, and missing test coverage discovered during static analysis of the implemented code. Each finding includes severity, affected component, root cause, and recommended fix.

This is the first QA iteration. Subsequent iterations may follow as fixes are verified and new issues are discovered.

---

## Methodology

Each RFC phase was audited by cross-referencing the RFC specification against the implementation code. The audit focused on:

- **Correctness** — does the implementation match the spec?
- **Concurrency safety** — are `Ref`-based state transitions atomic?
- **Resource management** — are there unbounded growth or leak vectors?
- **Error handling** — are failures surfaced or silently swallowed?
- **Security** — are there input validation gaps?
- **Test coverage** — are spec-required tests present?

---

## Severity Definitions

| Severity | Definition |
|----------|-----------|
| **Critical** | Data loss, corruption, or unbounded resource growth that will cause production failures under normal usage patterns. Must fix before production deployment. |
| **High** | Incorrect behavior under specific conditions (race windows, edge cases). May cause intermittent failures or incorrect results. Should fix before production deployment. |
| **Medium** | Suboptimal behavior, missing validation, or performance issues. Unlikely to cause failures but degrades operational quality. Fix in next iteration. |
| **Low** | Documentation inconsistencies, style issues, or minor deviations from spec. Fix opportunistically. |

---

## Findings

### Phase 0: Terminology Rename

#### F-0.1: Stale "program" references in documentation comments

| Field | Value |
|-------|-------|
| **Severity** | Low |
| **Component** | `PipelineStore.scala`, `Constellation.scala`, `SuspensionStore.scala`, `ConstellationRoutes.scala`, `DemoServer.scala` |
| **Description** | 17 Scaladoc and inline comments still reference "program" instead of "pipeline". All runtime identifiers, type names, endpoint paths, and API surface correctly use "pipeline" — only human-facing comments were missed. |
| **Impact** | No functional impact. May confuse contributors reading source comments. |
| **Recommendation** | Batch find-and-replace in comments. Exclude general programming terms (e.g., "programming language" is correct). |

---

### Phases 1–2: Suspension-Aware HTTP (RFC-015a)

#### F-1.1: No TTL or cleanup for stale suspended executions

| Field | Value |
|-------|-------|
| **Severity** | Critical |
| **Component** | `InMemorySuspensionStore` |
| **Description** | `InMemorySuspensionStore` uses a `Ref[IO, Map[UUID, SuspendedExecution]]` with no eviction policy. Suspended executions are stored indefinitely until explicitly deleted via `DELETE /executions/:id` or resumed. In a server that processes thousands of requests with partial inputs (e.g., multi-step approval workflows), the map grows without bound. |
| **Root Cause** | The RFC-015a spec acknowledges "Suspension TTL / expiry" as future work (listed in the master RFC's Future Work table). The implementation follows the spec — but the spec underestimates the risk for long-running servers. |
| **Impact** | Memory exhaustion over time in servers that handle suspension-producing workloads. Silent degradation — no warning is emitted as the map grows. |
| **Recommendation** | Add a configurable TTL to `InMemorySuspensionStore`. On each `save()` or `load()`, lazily evict entries older than the TTL. Default: 24 hours. Log a warning when entries are evicted. This is a small change to `InMemorySuspensionStore` and does not require a trait change. |

#### F-1.2: Missing HTTP integration tests for suspension edge cases

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `ConstellationRoutesTest.scala` |
| **Description** | The RFC-015a spec lists these required tests, but corresponding HTTP-level integration tests are missing: (1) Concurrent resume of the same execution returns 409 Conflict. (2) Resume with type-mismatched inputs returns 400. (3) Resume that produces another suspension (multi-step chain). Unit tests for `SuspendableExecution` cover the runtime behavior, but no tests verify the HTTP layer maps these correctly. |
| **Impact** | Regressions in HTTP response codes or error messages would go undetected. |
| **Recommendation** | Add integration tests in `ConstellationRoutesTest.scala` that exercise the full HTTP path for these three scenarios. |

---

### Phases 3–4a: Pipeline Loader, Reload, Versioning (RFC-015b)

#### F-2.1: PipelineLoader stores images before confirming overall success

| Field | Value |
|-------|-------|
| **Severity** | Critical |
| **Component** | `PipelineLoader.scala` |
| **Description** | When `failOnError = true`, the loader compiles and stores each pipeline sequentially. If the third of five files fails to compile, the first two are already stored in `PipelineStore` with aliases. The loader then aborts startup — but the partially-loaded pipelines remain in the store. If the server proceeds despite the abort (e.g., a future code change catches the error), those partial pipelines would be accessible. |
| **Root Cause** | The loader stores each pipeline immediately after compilation rather than batching all compilations first and storing only on full success. |
| **Impact** | With `failOnError = true`, the server aborts, so partial state is discarded (the process exits). The risk is latent — if error handling changes, partial state becomes observable. With `failOnError = false`, partial loading is intentional and correct. |
| **Recommendation** | When `failOnError = true`, compile all files first (collecting results), then store only if all succeeded. This makes the all-or-nothing semantics explicit rather than relying on process exit for cleanup. |

#### F-2.2: Unbounded version history in PipelineVersionStore

| Field | Value |
|-------|-------|
| **Severity** | Critical |
| **Component** | `PipelineVersionStore.scala` |
| **Description** | `PipelineVersionStore` keeps all versions for every pipeline in a `Ref[IO, Map[String, List[PipelineVersion]]]`. The version list grows with every reload. The RFC-015b spec states "Old versions are not garbage collected" (Design Decision D3), and cleanup is via explicit `DELETE /pipelines/:ref`. However, no maximum version count is enforced — a pipeline reloaded 10,000 times retains all 10,000 version entries in memory. |
| **Root Cause** | Design Decision D3 defers GC to the operator. No upper bound protects against operators who don't clean up. |
| **Impact** | Memory growth proportional to total reloads across all pipelines. In CI/CD environments with frequent automated reloads, this can become significant. Each `PipelineVersion` is small (~100 bytes), so 10,000 versions ≈ 1MB per pipeline — manageable, but unbounded growth is a code smell. |
| **Recommendation** | Add an optional `maxVersionsPerPipeline` configuration (default: unlimited for backward compatibility). When exceeded, prune the oldest non-active versions. Log when pruning occurs. |

#### F-2.3: Missing HTTP integration tests for reload and version endpoints

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `ConstellationRoutesTest.scala` |
| **Description** | The RFC-015b spec lists tests for: reload creating a new version, reload with same source returning `changed: false`, version history endpoint, rollback to previous/specific version, rollback of non-existent version (404). Integration tests exercising these through the HTTP layer are incomplete. |
| **Impact** | HTTP-level regressions (wrong status codes, missing fields in JSON responses) would go undetected. |
| **Recommendation** | Add integration tests matching the RFC-015b test list. |

---

### Phase 4b: Canary Releases (RFC-015c)

#### F-3.1: Race condition in canary step evaluation timing

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Component** | `CanaryRouter.scala` — `recordResult()` |
| **Description** | In `recordResult()`, `Instant.now()` is captured outside the `stateRef.modify` call. If the `modify` retries (due to contention from concurrent requests), the `now` value is stale — it was computed before the retry, not at the point of evaluation. This means the `observationWindow` check (`now >= stepStartedAt + window`) may use a timestamp that's seconds or even minutes old under high contention. |
| **Root Cause** | `Ref.modify` is optimistic — it retries when the underlying value changed between read and write. Side-effectful values (like `Instant.now()`) captured outside the modify become stale on retry. |
| **Impact** | Under high concurrency, a canary step may be promoted slightly earlier than the configured `observationWindow`. The window is typically minutes (default 5m), so a few seconds of skew is unlikely to cause incorrect promotions. However, the pattern is incorrect and could mask issues in latency-sensitive configurations. |
| **Recommendation** | Capture `Instant.now()` inside the modify function, or use `stateRef.flatModify` with `IO(Instant.now())` to ensure freshness on each attempt. |

#### F-3.2: Unbounded latency vector growth

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `CanaryRouter.scala` — `VersionMetrics` |
| **Description** | `VersionMetrics` stores all request latencies in a `Vector[Double]` for p99 calculation. Metrics are reset between promotion steps, so the vector size is bounded by traffic during one observation window. However, for high-traffic pipelines (thousands of requests per minute) with long observation windows, the vector can grow to millions of entries. |
| **Root Cause** | The plan noted "since metrics reset between steps and minRequests defaults to 10, the buffer stays small." This is true for low-traffic pipelines but not for high-traffic ones where the observation window may accumulate substantial traffic. |
| **Impact** | Memory growth during canary observation windows. For a pipeline serving 1,000 req/s with a 5-minute window, each step accumulates ~300,000 latency values (~2.4 MB per version, ~4.8 MB total). Manageable but unnecessary. |
| **Recommendation** | Replace the full latency vector with a bounded data structure. Options: (a) t-digest for streaming percentile estimation, (b) fixed-size circular buffer sampling every Nth request, (c) sorted reservoir sampling with a cap (e.g., 10,000 entries). Option (b) is simplest and sufficient for p99 accuracy with a 10,000-entry buffer. |

#### F-3.3: Completed/RolledBack canary state not cleared from map

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `CanaryRouter.scala` |
| **Description** | When a canary reaches `Complete` or `RolledBack` status, the `CanaryState` entry remains in the `Ref[IO, Map[String, CanaryState]]`. The `startCanary()` method checks for existing entries and returns 409 Conflict. This means after a canary completes, starting a new canary for the same pipeline fails with 409 until the state is explicitly cleared. |
| **Root Cause** | `recordResult()` transitions the status to `Complete` or `RolledBack` but does not remove the entry from the map. Neither `promote()` (on final step) nor `rollback()` removes the entry. |
| **Impact** | Operators must call `DELETE /pipelines/:name/canary` after every completed canary before starting a new one. The RFC spec describes `DELETE` as "abort canary (rollback to old)" — using it to clear a completed canary is semantically confusing. |
| **Recommendation** | Automatically remove the canary entry from the map when status transitions to `Complete` or `RolledBack`. Alternatively, modify `startCanary()` to replace entries in terminal states (`Complete`, `RolledBack`) rather than rejecting them. |

#### F-3.4: Latency measurement includes pre-execution overhead

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `ConstellationRoutes.scala` — `executeByRef()` |
| **Description** | The latency timer in `executeByRef()` starts before image resolution and input conversion, and stops after execution completes. The recorded latency includes: (1) `PipelineStore.get()` lookup, (2) `rehydrate()` to reconstruct modules, (3) input JSON → `CValue` conversion, (4) actual pipeline execution. Only (4) is the pipeline's latency — items (1)–(3) are infrastructure overhead. |
| **Impact** | Canary metrics compare latencies that include infrastructure overhead. If the old and new versions have identical performance but the new version's image is larger (more modules to rehydrate), the canary may show a false latency regression. The effect is small (rehydration is sub-millisecond) but violates the principle that canary metrics measure pipeline behavior, not infrastructure. |
| **Recommendation** | Narrow the timer to measure only `constellation.executePipelineImage()`. This isolates pipeline execution latency from infrastructure overhead. |

---

### Phase 5: Persistent PipelineStore (RFC-015d)

#### F-4.1: Non-atomic concurrent writes to aliases.json

| Field | Value |
|-------|-------|
| **Severity** | Critical |
| **Component** | `FileSystemPipelineStore.scala` |
| **Description** | When two pipelines are aliased concurrently, both threads: (1) read `aliases.json`, (2) deserialize to `Map[String, String]`, (3) add their entry, (4) serialize and write back. If thread A reads before thread B writes, thread A's write overwrites thread B's alias. The in-memory delegate (`InMemoryPipelineStore`) is protected by `Ref` and sees both aliases — but `aliases.json` on disk loses one. On restart, the lost alias is missing. |
| **Root Cause** | File-level read-modify-write without locking or atomic swap. The in-memory `Ref` serializes state correctly, but the filesystem writes are not coordinated with it. |
| **Impact** | Alias loss on server restart after concurrent alias operations. The in-memory state is correct until restart. After restart, the persistent store loads from disk, missing the lost alias. The pipeline image itself is not lost (it's in `images/<hash>.json`), only the name → hash mapping. |
| **Recommendation** | Use atomic file writes (write to temp file, then rename). Serialize all file writes through a single `Semaphore[IO]` or piggyback on the in-memory `Ref.modify` to emit a single consistent snapshot of aliases after each mutation. |

#### F-4.2: Syntactic index loses registryHash on restart

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Component** | `FileSystemPipelineStore.scala` — `syntactic-index.json` |
| **Description** | The syntactic index maps `(syntacticHash, registryHash)` → `structuralHash`. In the in-memory store, `lookupSyntactic` requires both the syntactic hash (source content) and the registry hash (available functions) to identify a pipeline. The persistent `syntactic-index.json` stores only `syntacticHash → structuralHash`, discarding the `registryHash` dimension. On restart, the index is loaded without registry hash context. If the same source is compiled against a different function registry (e.g., after adding new modules), the stale index returns the wrong structural hash. |
| **Root Cause** | The JSON serialization flattens the two-dimensional key `(syntacticHash, registryHash)` to a single key. |
| **Impact** | After restart, if the function registry has changed (new modules added or removed), the syntactic index may return stale structural hashes. The pipeline would execute with the old compiled version despite the registry change. This is uncommon in production (registries rarely change between restarts) but is a correctness violation. |
| **Recommendation** | Use a composite key in `syntactic-index.json`: `"syntacticHash:registryHash" → structuralHash`. On load, reconstruct the two-dimensional map. This preserves full deduplication semantics across restarts. |

#### F-4.3: Path traversal vulnerability in image file naming

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `FileSystemPipelineStore.scala` |
| **Description** | The structural hash is used directly as a filename: `images/${structuralHash}.json`. If a malicious or buggy compilation produces a structural hash containing path separators (e.g., `../../etc/passwd`), the file write could escape the intended directory. Currently, structural hashes are SHA-256 hex strings (64 characters, `[0-9a-f]`), so this cannot happen in practice. However, the code does not validate this assumption. |
| **Root Cause** | No input validation on the structural hash before using it as a path component. The safety relies on the hash implementation producing safe characters, not on explicit validation. |
| **Impact** | No exploitable vulnerability exists today. The risk is latent — if the hash format changes (e.g., base64 encoding with `/` characters), the vulnerability becomes real. |
| **Recommendation** | Add a validation check: `require(structuralHash.matches("[0-9a-f]+"), "Invalid structural hash")` before constructing file paths. Defense in depth. |

#### F-4.4: Non-atomic image file writes

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `FileSystemPipelineStore.scala` |
| **Description** | Image files are written directly to their final path (`images/<hash>.json`). If the process crashes or is killed mid-write, the file may contain partial JSON. On restart, the persistent store attempts to load this file, encounters a parse error, logs a warning, and skips it — which is the correct recovery behavior (per RFC-015d spec: "Corrupted file → log warning, skip"). However, the pipeline is lost until recompiled. |
| **Root Cause** | Direct file write without write-to-temp-then-rename pattern. |
| **Impact** | Pipeline image loss on crash during write. The window is small (milliseconds) and requires a crash at the exact moment of a write. Low probability but non-zero for servers handling many compilations. |
| **Recommendation** | Write to `images/<hash>.json.tmp`, then rename to `images/<hash>.json`. Rename is atomic on most filesystems (POSIX `rename()`, NTFS `MoveFileEx`). On startup, delete any `.tmp` files (incomplete writes from previous crash). |

#### F-4.5: Disk-full errors silently swallowed

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `FileSystemPipelineStore.scala` |
| **Description** | File write operations catch exceptions and log warnings but do not propagate the error to the caller. If the disk is full, the in-memory delegate successfully stores the pipeline, but the filesystem write fails silently. The caller (e.g., `POST /compile`) receives a success response. On restart, the pipeline is missing. |
| **Root Cause** | The write-through design prioritizes availability: "writes go to both the in-memory delegate and the filesystem." The filesystem write is treated as best-effort. |
| **Impact** | Silent data loss on disk-full conditions. The operator sees successful API responses but pipelines don't survive restart. No alert or error response indicates the problem. |
| **Recommendation** | Log at ERROR level (not WARN) when filesystem writes fail. Optionally, add a health check that verifies filesystem write capability (write a test file, delete it) and reports degraded status via `GET /health/ready` when persistence is broken. |

---

### Phase 6: Dashboard Integration (RFC-015e)

#### F-5.1: Sequential canary status fetches in pipeline list

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Component** | `dashboard/src/components/pipelines-panel.ts` — `updatePipelinesList()` |
| **Description** | When refreshing the pipelines panel, the code fetches canary status for each pipeline sequentially using `for...of` with `await`. For N pipelines, this means N sequential HTTP requests. With a 100ms round-trip per request and 20 pipelines, the panel takes 2 seconds to refresh. |
| **Root Cause** | The `for (const pipeline of pipelines)` loop awaits each `fetch()` individually rather than collecting promises and awaiting them together. |
| **Impact** | Dashboard responsiveness degrades linearly with the number of loaded pipelines. Users experience sluggish UI updates during pipeline list refresh. |
| **Recommendation** | Replace the sequential loop with `Promise.all(pipelines.map(p => fetchCanaryStatus(p)))` to parallelize the requests. The browser's connection pool limits concurrency naturally (typically 6 concurrent connections per origin). |

---

## Findings Summary

| ID | Severity | Phase | Component | Short Description | Status |
|----|----------|-------|-----------|-------------------|--------|
| F-0.1 | Low | 0 | Comments | 17 stale "program" references in Scaladoc | Fixed |
| F-1.1 | Critical | 1–2 | SuspensionStore | No TTL for stale suspensions — memory leak | Fixed in `a8cf31f` |
| F-1.2 | Medium | 1–2 | Tests | Missing HTTP tests for suspension edge cases | Fixed |
| F-2.1 | Critical | 3–4a | PipelineLoader | Stores images before confirming overall success | Fixed in `a8cf31f` |
| F-2.2 | Critical | 3–4a | VersionStore | Unbounded version history growth | Fixed in `a8cf31f` |
| F-2.3 | Medium | 3–4a | Tests | Missing HTTP tests for reload/version endpoints | Fixed |
| F-3.1 | High | 4b | CanaryRouter | Race condition in step evaluation timing | Fixed in `a8cf31f` |
| F-3.2 | Medium | 4b | CanaryRouter | Unbounded latency vector growth | Fixed in `a8cf31f` |
| F-3.3 | Medium | 4b | CanaryRouter | Completed canary state blocks new canaries | Fixed in `a8cf31f` |
| F-3.4 | Medium | 4b | CanaryRouter | Latency includes pre-execution overhead | Fixed |
| F-4.1 | Critical | 5 | FileSystemStore | Non-atomic concurrent alias writes | Fixed in `a8cf31f` |
| F-4.2 | High | 5 | FileSystemStore | Syntactic index loses registryHash | Fixed in `a8cf31f` |
| F-4.3 | Medium | 5 | FileSystemStore | Path traversal (latent, defense-in-depth) | Fixed in `a8cf31f` |
| F-4.4 | Medium | 5 | FileSystemStore | Non-atomic image writes | Fixed in `a8cf31f` |
| F-4.5 | Medium | 5 | FileSystemStore | Disk-full errors silently swallowed | Fixed in `a8cf31f` |
| F-5.1 | High | 6 | Dashboard | Sequential canary fetches in pipeline list | Fixed in `a8cf31f` |

### By Severity

| Severity | Count | Fixed | Open | Findings |
|----------|-------|-------|------|----------|
| Critical | 4 | 4 | 0 | F-1.1, F-2.1, F-2.2, F-4.1 |
| High | 3 | 3 | 0 | F-3.1, F-4.2, F-5.1 |
| Medium | 8 | 8 | 0 | F-1.2, F-2.3, F-3.2, F-3.3, F-3.4, F-4.3, F-4.4, F-4.5 |
| Low | 1 | 1 | 0 | F-0.1 |
| **Total** | **16** | **16** | **0** | |

---

## Recommended Fix Order

Fixes are ordered by severity and dependency. Independent fixes can be parallelized.

### Batch 1: Critical Fixes (must fix before production)

| Priority | Finding | Effort | Dependencies |
|----------|---------|--------|--------------|
| 1 | F-4.1: Atomic alias writes | Small | None |
| 2 | F-1.1: Suspension TTL | Small | None |
| 3 | F-2.2: Version history cap | Small | None |
| 4 | F-2.1: Batch-then-store in loader | Medium | None |

### Batch 2: High Severity (should fix before production)

| Priority | Finding | Effort | Dependencies |
|----------|---------|--------|--------------|
| 5 | F-3.1: Canary timing race | Small | None |
| 6 | F-4.2: Composite syntactic index key | Medium | None |
| 7 | F-5.1: Parallel canary fetches | Small | None |

### Batch 3: Medium Severity (next iteration)

| Priority | Finding | Effort | Dependencies |
|----------|---------|--------|--------------|
| 8 | F-3.3: Clear terminal canary state | Small | None |
| 9 | F-4.4: Atomic image writes | Small | None |
| 10 | F-4.5: Error-level logging on disk failure | Small | None |
| 11 | F-4.3: Hash validation | Small | None |
| 12 | F-3.4: Narrow latency measurement | Small | F-3.1 |
| 13 | F-3.2: Bounded latency buffer | Medium | None |
| 14 | F-1.2: Suspension HTTP tests | Medium | F-1.1 |
| 15 | F-2.3: Reload/version HTTP tests | Medium | None |

### Batch 4: Low Severity (opportunistic)

| Priority | Finding | Effort | Dependencies |
|----------|---------|--------|--------------|
| 16 | F-0.1: Fix stale comments | Small | None |

---

## Verification Criteria

Each fix must satisfy:

1. **Unit test** covering the specific defect (regression test)
2. **Existing tests still pass** (`make test`)
3. **Code review** confirming the fix doesn't introduce new issues
4. **Finding status updated** in this document (append "Fixed in `<commit-hash>`" to the finding)

After all Critical and High findings are fixed, this RFC's status transitions to **Implemented**.

---

## Design Decisions

**D1: Audit-as-RFC rather than issue tracker.** Documenting findings in an RFC (rather than individual GitHub issues) keeps the full audit context in one place — severity rationale, cross-cutting patterns, and fix ordering are visible together. Individual findings can be referenced as `RFC-015f/F-3.1` in commit messages and PR descriptions.

**D2: Severity calibrated to "production deployment readiness."** Critical means "will cause failures under normal production usage." High means "will cause failures under specific conditions." This calibration reflects that the RFC-015 family is feature-complete but not yet deployed to production.

**D3: Fix ordering considers both severity and effort.** Small-effort Critical fixes come first because they provide the highest risk reduction per unit of work. Large-effort Medium fixes are deferred to Batch 3 even though they may be important — the goal is to reach a production-safe baseline quickly.
