# RFC-016: Project-Wide QA Audit

**Status:** Implemented
**Priority:** P0 (Security & Stability)
**Author:** Human + Claude
**Created:** 2026-02-04

---

## Summary

Comprehensive project-wide QA audit of the Constellation Engine codebase. This document catalogs defects, security vulnerabilities, race conditions, resource leaks, and correctness issues discovered during static analysis across all modules.

20 findings: 4 High, 12 Medium, 4 Low.
19 Fixed, 1 Won't Fix (F-16.15 — analyzed as not impactful).

---

## Methodology

All source files across every module were audited:
- **core** (11 files) - Type system, pipeline status, content hashing
- **runtime** (30+ files) - Module execution, scheduling, caching, suspension
- **lang-parser** (2 files) - Constellation-lang parser
- **lang-compiler** (10+ files) - Type checking, DAG compilation, IR generation
- **lang-stdlib** (5 files) - Standard library functions
- **lang-lsp** (10+ files) - Language server protocol implementation
- **http-api** (20+ files) - HTTP API, dashboard, middleware, WebSocket

Focus areas: correctness, concurrency safety, security, resource management, error handling.

---

## Severity Definitions

| Severity | Definition |
|----------|-----------|
| **High** | Security vulnerabilities, data loss, or race conditions causing incorrect behavior under normal usage. Must fix before production. |
| **Medium** | Incorrect behavior under specific conditions, resource leaks, or missing safety checks. Should fix before production. |
| **Low** | Suboptimal behavior, code smells, or minor robustness gaps. Fix opportunistically. |

---

## Findings

### Security (http-api)

#### F-16.1: Path traversal in dashboard file serving

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Component** | `DashboardRoutes.scala` |
| **Description** | The `getFileContent` method resolves user-supplied relative paths using `config.getCstDirectory.resolve(relativePath)` without validating that the resolved path remains within the base directory. An attacker can use `../../` sequences to read arbitrary `.cst` files outside the configured directory. The `.endsWith(".cst")` check only validates the file extension, not the directory containment. |
| **Impact** | Arbitrary file read for any file with `.cst` extension reachable from the filesystem. |
| **Fix** | After resolving the path, verify containment: `resolvedPath.normalize().startsWith(baseDir.normalize())`. Reject if false. |

---

#### F-16.2: WebSocket idle timeout disabled

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `ConstellationServer.scala` |
| **Description** | The Ember server was previously configured with `withIdleTimeout(Duration.Inf)`, disabling idle connection cleanup. Clients (including WebSocket connections for LSP) could remain open indefinitely without activity. |
| **Impact** | Resource exhaustion — an attacker can open many WebSocket connections and leave them idle, consuming file descriptors and memory until the server runs out of resources. |
| **Fix** | Set a reasonable idle timeout (e.g., 10 minutes). LSP clients reconnect automatically. |

---

#### F-16.3: X-Forwarded-For trusted without proxy validation

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `RateLimitMiddleware.scala` |
| **Description** | The `extractClientIp` method trusts the `X-Forwarded-For` header unconditionally. Without a trusted proxy configuration, any client can spoof their IP address to bypass rate limiting by rotating the header value on each request. |
| **Impact** | Rate limiting can be bypassed entirely by setting arbitrary `X-Forwarded-For` values. |
| **Fix** | Add a `trustedProxies: Set[String]` config. Only trust `X-Forwarded-For` when `remoteAddr` is in the trusted set. Fall back to `remoteAddr` otherwise. |

---

#### F-16.4: No file size limit in dashboard file serving

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `DashboardRoutes.scala` |
| **Description** | `Files.readString(fullPath)` reads the entire file into memory without checking size. A large `.cst` file (e.g., 1GB) will exhaust heap memory. |
| **Impact** | Denial of service via memory exhaustion. |
| **Fix** | Check `Files.size(fullPath)` against a configurable maximum (e.g., 10MB) before reading. |

---

### Concurrency (runtime, lang-lsp)

#### F-16.5: GlobalScheduler shutdown race condition

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Component** | `GlobalScheduler.scala` (lines 319-320) |
| **Description** | The `shutdown` method sets `shuttingDown = true` and cancels the aging fiber, but in-flight tasks can still call `dispatch` which attempts to complete Deferred gates. When the aging fiber is cancelled mid-operation, pending gates may be left incomplete. No drain mechanism waits for in-flight tasks to finish. |
| **Impact** | Tasks in flight during shutdown may deadlock on incomplete Deferred gates or produce incomplete results. |
| **Fix** | Add a drain phase: after setting `shuttingDown = true`, wait for in-flight tasks to complete (with a timeout), then cancel the aging fiber. Fail any remaining pending gates with a ShutdownException. |

---

#### F-16.6: LSP moduleCompletionTrie race condition

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Component** | `ConstellationLanguageServer.scala` (lines 62-63) |
| **Description** | `moduleCompletionTrie` and `lastModuleNames` are mutable `var` fields accessed by concurrent LSP request handlers. `CompletionTrie` itself is documented as NOT thread-safe (CompletionTrie.scala line 12). Multiple concurrent `textDocument/completion` requests can race on reads/writes to the trie. |
| **Impact** | Data corruption in the completion trie, inconsistent autocompletion results, potential ConcurrentModificationException. |
| **Fix** | Wrap in `Ref[IO, CompletionTrie]` or protect access with synchronization. |

---

#### F-16.7: LSP unsafeRunSync blocks LSP dispatch thread

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Component** | `ConstellationLanguageServer.scala` (lines 643, 1528) |
| **Description** | Multiple methods call `.unsafeRunSync()` inside IO handlers. This blocks the calling thread, violating Cats Effect's non-blocking contract. If the IO operation depends on work scheduled on the same thread pool, this causes deadlock. |
| **Impact** | LSP becomes unresponsive under load when blocked threads starve the pool. |
| **Fix** | Replace `unsafeRunSync()` with proper IO composition using `flatMap`. |

---

#### F-16.8: InMemoryCacheBackend eviction race

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `InMemoryCacheBackend.scala` (lines 76-82) |
| **Description** | Cache eviction uses `this.synchronized` to protect the eviction loop, but the subsequent `storage.put()` at line 85 is outside the synchronized block. Between the eviction check and the put, another fiber can insert entries, causing the cache to exceed `maxSize`. |
| **Impact** | Cache size can exceed configured maximum; eviction statistics become inaccurate. |
| **Fix** | Extend the synchronized block to include the `put` operation, or use a Cats Effect `Semaphore`. |

---

#### F-16.9: SuspendableExecution resume cleanup on cancellation

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `SuspendableExecution.scala` (lines 85-102) |
| **Description** | Resume tracking uses `ConcurrentHashMap.putIfAbsent` with `guarantee` for cleanup. If `doResume` is cancelled via `Fiber.cancel`, the `guarantee` block may not execute (Cats Effect cancellation can skip guarantee in certain fiber states), leaving the execution ID permanently in `inFlightResumes`. |
| **Impact** | Cancelled resume attempts can permanently block future resume attempts for that execution. |
| **Fix** | Use `onCancel` in addition to `guarantee`, or use `guaranteeCase` to handle cancellation. |

---

#### F-16.10: LazyJsonValue double-materialization race

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `LazyValue.scala` (line 46) |
| **Description** | `LazyJsonValue.materialize` uses `@volatile var cached` for memoization. Two concurrent threads can both see `cached = None`, both run the JSON conversion, and both write to `cached`. This is a benign race (both compute the same result) but wastes CPU on duplicate work. |
| **Impact** | Wasted computation under concurrent access. No correctness issue. |
| **Fix** | Use `AtomicReference` with `compareAndSet` or a lazy val pattern. |

---

### Resource Management (runtime, http-api, lang-lsp)

#### F-16.11: ExecutionTracker LRU eviction stack overflow

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `ExecutionTracker.scala` (lines 210-212) |
| **Description** | `evictIfNeeded` is recursively defined: `if traces.size > maxTraces then evictOldest.evictIfNeeded(maxTraces)`. If the trace count exceeds `maxTraces` by a large amount (e.g., 1000+ over), this recurses once per eviction, risking StackOverflowError. |
| **Impact** | Server crash on StackOverflow if trace backlog is large. |
| **Fix** | Replace recursion with a `while` loop or `@tailrec` iterative approach. |

---

#### F-16.12: LSP session memory leak

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `DebugSessionManager.scala` (lines 19, 131-138) |
| **Description** | Stale sessions are only cleaned up when a new session is created (`cleanupStaleSessions` called in `createSession`). If no new sessions are created, old sessions accumulate indefinitely with their associated state (DAGs, execution results, step history). |
| **Impact** | Memory growth on long-running LSP servers with debug sessions. |
| **Fix** | Run periodic cleanup (e.g., via a background fiber every 5 minutes), or clean up on session access. |

---

#### F-16.13: Dashboard file stream not closed

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `DashboardRoutes.scala` (lines 253-258) |
| **Description** | `Files.list(currentDir)` returns a `java.util.stream.Stream` that must be closed after use. The code converts it to an iterator and list without closing the stream, leaking the underlying file descriptor. |
| **Impact** | File descriptor leak — on repeated directory listings, file descriptors accumulate until the OS limit is hit. |
| **Fix** | Use try-finally or `scala.util.Using` to ensure `stream.close()` is called. |

---

#### F-16.14: Unbounded directory recursion in buildFileTreeRec

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `DashboardRoutes.scala` (lines 250-291) |
| **Description** | `buildFileTreeRec` recursively descends directory trees without depth limits. A deeply nested directory structure (e.g., from symlink loops or adversarial paths) causes StackOverflowError. |
| **Impact** | Server crash from stack overflow. |
| **Fix** | Add a `maxDepth` parameter (e.g., 20) and stop recursion when exceeded. |

---

### Correctness (runtime, lang-compiler, lang-stdlib)

#### F-16.15: TokenBucketRateLimiter floating-point drift

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `RateLimiter.scala` (lines 133-150) |
| **Description** | Token refill uses `elapsed * tokensPerMs` where `tokensPerMs` is a Double. For low rates (e.g., 1 token/second = 0.001 tokens/ms), floating-point rounding accumulates over time. After millions of refill operations, the accumulated error can prevent tokens from reaching integer boundaries. |
| **Impact** | Rate limiter may occasionally allow or deny one extra request per window. Not exploitable but imprecise. |
| **Fix** | Won't Fix — the implementation already uses absolute time differences per-call (not incremental accumulation). Token counts are bounded by `maxTokens`, preventing meaningful drift. |

---

#### F-16.16: IR topologicalLayers silently accepts cycles

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `IR.scala` (lines 354-356) |
| **Description** | When `topologicalLayers` detects a cycle (current layer empty but nodes remain), it adds remaining nodes as a single layer instead of reporting an error. This silently produces an invalid execution schedule for cyclic graphs. |
| **Impact** | If a cyclic DAG bypasses earlier validation, it executes in an undefined order instead of failing. |
| **Fix** | Throw a `DagCompilerError.CycleDetected` error instead of silently adding remaining nodes. |

---

#### F-16.17: Division by zero silently returns 0

| Field | Value |
|-------|-------|
| **Severity** | Low |
| **Component** | `MathFunctions.scala` (lines 37, 61) |
| **Description** | The `divide` and `modulo` stdlib modules return `0` when the divisor is zero, without logging or signaling an error. Users can't distinguish between a valid result of 0 and a division-by-zero. |
| **Impact** | Silent data corruption in pipelines that divide by computed values that happen to be zero. |
| **Fix** | Return an error via `IO.raiseError` or use an Optional return type. |

---

#### F-16.18: Empty list head/last return 0L

| Field | Value |
|-------|-------|
| **Severity** | Low |
| **Component** | `ListFunctions.scala` (lines 27, 33) |
| **Description** | `listFirst` and `listLast` return `0L` for empty lists. Users can't distinguish between "first element is 0" and "list is empty". |
| **Impact** | Silent incorrect results when operating on empty lists. |
| **Fix** | Return Optional or raise an error for empty lists. |

---

### Error Handling (http-api, lang-lsp)

#### F-16.19: Dashboard execution failures not recorded

| Field | Value |
|-------|-------|
| **Severity** | Low |
| **Component** | `DashboardRoutes.scala` (lines 495-505) |
| **Description** | When pipeline execution fails, the HTTP error is returned to the client, but the execution record in `ExecutionStorage` stays in `Running` status. The dashboard shows it as still running. |
| **Impact** | Confusing dashboard UI — failed executions appear as perpetually running. |
| **Fix** | Update execution record with `Failed` status and error message in the error handler. |

---

#### F-16.20: LSP broad exception catching masks errors

| Field | Value |
|-------|-------|
| **Severity** | Low |
| **Component** | `ConstellationLanguageServer.scala` (line 474) |
| **Description** | `evaluateExampleToJson` catches `case _: Exception => None`, which includes `OutOfMemoryError` (actually Error, not Exception — this is correct) and `StackOverflowError` (also Error). The catch block only catches Exception subclasses, so Error types propagate correctly. However, it still masks useful exceptions like `NumberFormatException` without logging. |
| **Impact** | Difficult to debug example evaluation failures. |
| **Fix** | Log the caught exception at debug level before returning None. |

---

## Test Coverage Gaps

1. **No integration test for path traversal prevention** in DashboardRoutes
2. **No shutdown-under-load test** for GlobalScheduler
3. **No concurrent completion test** for LSP
4. **No eviction-under-concurrency test** for InMemoryCacheBackend
5. **No empty-list behavior test** for stdlib list functions

---

## Summary Table

| ID | Severity | Module | Component | Status |
|----|----------|--------|-----------|--------|
| F-16.1 | High | http-api | DashboardRoutes (path traversal) | Fixed |
| F-16.2 | Medium | http-api | ConstellationServer (idle timeout) | Fixed |
| F-16.3 | Medium | http-api | RateLimitMiddleware (XFF spoofing) | Fixed |
| F-16.4 | Medium | http-api | DashboardRoutes (file size) | Fixed |
| F-16.5 | High | runtime | GlobalScheduler (shutdown race) | Fixed |
| F-16.6 | High | lang-lsp | LSP CompletionTrie (race) | Fixed |
| F-16.7 | High | lang-lsp | LSP unsafeRunSync (blocking) | Fixed |
| F-16.8 | Medium | runtime | InMemoryCacheBackend (eviction) | Fixed |
| F-16.9 | Medium | runtime | SuspendableExecution (cancel) | Fixed |
| F-16.10 | Medium | runtime | LazyJsonValue (double-init) | Fixed |
| F-16.11 | Medium | runtime | ExecutionTracker (stack overflow) | Fixed |
| F-16.12 | Medium | lang-lsp | DebugSessionManager (leak) | Fixed |
| F-16.13 | Medium | http-api | DashboardRoutes (fd leak) | Fixed |
| F-16.14 | Medium | http-api | DashboardRoutes (recursion) | Fixed |
| F-16.15 | Medium | runtime | RateLimiter (float drift) | Won't Fix |
| F-16.16 | Medium | lang-compiler | IR.scala (silent cycles) | Fixed |
| F-16.17 | Low | lang-stdlib | MathFunctions (div by zero) | Fixed |
| F-16.18 | Low | lang-stdlib | ListFunctions (empty list) | Fixed |
| F-16.19 | Low | http-api | DashboardRoutes (failed exec) | Fixed |
| F-16.20 | Low | lang-lsp | LSP exception masking | Fixed |
