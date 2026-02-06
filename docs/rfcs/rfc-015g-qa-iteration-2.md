# RFC-015g: QA Iteration 2 — Post-Fix Audit

**Status:** Implemented
**Priority:** P1 (Stability & Correctness)
**Author:** Human + Claude
**Created:** 2026-02-04
**Parent:** [RFC-015: Pipeline Lifecycle Management](./rfc-015-pipeline-lifecycle.md)
**Prerequisite:** [RFC-015f: QA Iteration 1](./rfc-015f-qa-iteration-1.md) (all 16 findings fixed)
**Audits:** [RFC-015c](./rfc-015c-canary-releases.md), [RFC-015d](./rfc-015d-persistent-pipeline-store.md)

---

## Summary

Second QA iteration following the completion of all 16 fixes from RFC-015f. This audit focuses on canary release correctness (RFC-015c) and persistent store concurrency (RFC-015d), where the first iteration's fixes introduced or revealed new issues.

4 findings: 2 High (correctness bugs), 1 Medium (race condition), 1 Low (code smell).

---

## Methodology

Same methodology as RFC-015f: cross-referencing RFC specifications against implementation code, focusing on correctness, concurrency safety, and test coverage. This iteration specifically targeted:

- **Canary execution path** — end-to-end flow from `selectVersion` → `recordResult` → promotion/rollback
- **Alias consistency** — pipeline name-to-hash alias integrity across canary lifecycle states
- **FileSystemPipelineStore** — persistence atomicity for the syntactic index

---

## Severity Definitions

| Severity | Definition |
|----------|-----------|
| **High** | Incorrect behavior under specific conditions. May cause incorrect results or silent failures. Should fix before production deployment. |
| **Medium** | Suboptimal behavior or missing safety. Unlikely to cause failures but degrades operational quality. |
| **Low** | Code style issues or minor deviations from best practices. Fix opportunistically. |

---

## Findings

### Canary Releases (RFC-015c)

#### G-1: Auto-promotion does not update pipeline alias

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Component** | `ConstellationRoutes.scala` (lines 1028–1052) |
| **Description** | When `canaryRouter.recordResult()` auto-promotes a canary through all steps and returns a `CanaryState` with `status == Complete`, the returned state is discarded with `.as(result)` (line 1040). The pipeline alias is never updated to point to the new version's structural hash. After auto-promotion completes, all subsequent non-canary executions by name still resolve to the **old** version. |
| **Root Cause** | The reload handler deliberately leaves the alias on the old version during canary setup (line 1300–1302): `if reloadReq.canary.isEmpty then alias(name, newHash) else IO.unit`. The manual promote endpoint (line 598–604) correctly updates the alias on `Complete`, but the auto-promotion path in `executeByRef` never checks the returned canary state for terminal transitions. |
| **Impact** | Any canary deployment that reaches completion via auto-promotion silently leaves the pipeline alias stale. Operators believe the new version is live, but all non-canary traffic continues executing the old version indefinitely. Only manual promote works correctly. |
| **Fix** | After `canaryRouter.traverse(_.recordResult(...))`, check if the returned state has `status == Complete` and update the alias. Also handle `RolledBack` (alias already points to old, so no action needed — but log for observability). |

---

#### G-2: Canary success detection uses broken equality check

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Component** | `ConstellationRoutes.scala` (lines 1035–1037) |
| **Description** | The success flag for canary metrics is computed as: `sig.status != PipelineStatus.Failed(Nil)`. `PipelineStatus.Failed` is a case class with `errors: List[ExecutionError]`. This comparison uses structural equality — `Failed(List(someError)) != Failed(Nil)` evaluates to `true`, so a genuinely failed execution is counted as a **success** in canary metrics. Only a `Failed` with an empty error list would be detected as a failure, which never occurs in practice (the runtime always populates error details). |
| **Root Cause** | Incorrect equality pattern — should use pattern matching (`case _: PipelineStatus.Failed => false`) instead of structural equality against a specific instance. |
| **Impact** | Canary error rate tracking is broken. The `errorRate` for the new version will always be 0.0 regardless of actual failures. Auto-rollback based on `errorThreshold` never triggers. Auto-promotion proceeds even when the new version is consistently failing. |
| **Fix** | Replace the equality check with pattern matching: `!sig.status.isInstanceOf[PipelineStatus.Failed]` or a `match` expression. |

---

### Persistent Pipeline Store (RFC-015d)

#### G-3: `persistSyntacticEntry` has read-modify-write race on file

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Component** | `FileSystemPipelineStore.scala` (lines 132–145) |
| **Description** | `persistSyntacticEntry` reads the syntactic index from the **file** via `readSyntacticIndexFile()`, adds an entry, and writes back. Two concurrent `store()` calls both read the same file state, each adds their entry, and the second write overwrites the first — losing an entry. Compare with `persistAliases()` (lines 119–125) which correctly reads from the in-memory **delegate** (atomic `Ref`), avoiding this race. |
| **Root Cause** | When `persistAliases()` was fixed in RFC-015f (finding F-4.2), `persistSyntacticEntry` was not updated to follow the same pattern. The delegate already maintains the syntactic index atomically via `Ref`; the file persistence should mirror the delegate's state, not read-modify-write independently. |
| **Impact** | Under concurrent `store()` calls, syntactic index entries can be lost from the file. On server restart, the lost entries cause cache misses — pipelines are recompiled instead of recognized as already stored. No data corruption (the delegate `Ref` remains correct during the running server). |
| **Fix** | Read from the delegate's syntactic index (`delegate.lookupSyntactic` or equivalent) instead of the file. Alternatively, persist the full index from the delegate after each update, mirroring the `persistAliases()` pattern. Since the delegate `PipelineStore` trait doesn't expose a `listSyntacticIndex` method, the simplest fix is to maintain a local `Ref` that mirrors the syntactic index and persist from that. |

---

### Code Quality

#### G-4: `Right(null)` used instead of typed `Option`

| Field | Value |
|-------|-------|
| **Severity** | Low |
| **Component** | `ConstellationRoutes.scala` (line 1317) |
| **Description** | When no canary is requested during reload, the code uses `IO.pure(Right(null))` and later defends against the null with `canaryStateOpt.toOption.flatMap(Option(_))` on line 1328–1329. While functionally safe due to the `Option(_)` defense, using `null` in Scala is a code smell that can cause NPEs if the defense is refactored away. |
| **Fix** | Change the type to `Either[String, Option[CanaryState]]` and use `Right(None)` / `Right(Some(state))` throughout the match. |

---

## Test Coverage Gaps

The following test scenarios are missing from the existing test suites:

1. **Auto-promotion alias update** (`CanaryIntegrationTest`): No test verifies that after a canary auto-promotes to Complete, executing by name resolves to the new version.
2. **Failure detection with real errors** (`CanaryIntegrationTest`): No test verifies that a pipeline execution that returns `Failed(List(error))` is counted as a failure in canary metrics.

---

## Summary Table

| ID | Severity | Component | Status |
|----|----------|-----------|--------|
| G-1 | High | `ConstellationRoutes.scala` | Fixed |
| G-2 | High | `ConstellationRoutes.scala` | Fixed |
| G-3 | Medium | `FileSystemPipelineStore.scala` | Fixed |
| G-4 | Low | `ConstellationRoutes.scala` | Fixed |
