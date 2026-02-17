# RFC-017: v1.0 Readiness Gap Analysis

- **Status:** Rejected
- **Author:** Project Team
- **Created:** 2026-02-04
- **Rejected:** 2026-02-17
- **Rejection Reason:** Premature — v1.0 planning requires real user data to prioritize correctly. Will revisit after gathering adoption feedback.

## Summary

This RFC catalogs every gap between the current state of Constellation Engine (v0.4.0) and a v1.0 release suitable for public open-source adoption by JVM/Scala teams. Constellation is positioned as a **typed pipeline framework for JVM teams** — comparable in audience to http4s or ZIO — not a general-purpose orchestrator competing with Airflow, Prefect, or Dagster. Scheduling, cron, and event triggers are explicitly out of scope. The focus is on library quality, API stability, developer experience, and community readiness.

This document does not implement any changes. It serves as the roadmap that subsequent issues and PRs will reference.

## Current State Assessment

Constellation Engine v0.4.0 is feature-complete for its core value proposition:

- **23 RFCs implemented** (RFC-001 through RFC-016, including all RFC-015 sub-RFCs), covering resilience options, pipeline lifecycle management, suspendable execution, dashboard E2E testing, and a project-wide QA audit.
- **140+ test files** across all modules (core, runtime, lang-parser, lang-compiler, lang-lsp, http-api, cache-memcached, example-app), including unit tests, integration tests, property-based tests, benchmarks, and stress tests.
- **6 CI/CD workflows** (ci.yml, docs.yml, publish.yml, benchmark.yml, dashboard-tests.yml, vscode-extension-tests.yml).
- **Maven Central publishing** via sbt-ci-release.
- **Documentation website** on GitHub Pages via Docusaurus with Mermaid support.
- **Dashboard E2E tests** via Playwright with page-object model.
- **OpenAPI specification** at `docs/api/openapi.yaml` (currently at version 0.2.0 — behind actual release).
- **CHANGELOG** following Keep a Changelog + Semantic Versioning conventions.

What follows is a structured gap analysis organized by priority.

---

## Gap Categories

### 1. Open-Source Community Infrastructure (P0 — Blockers)

These are table-stakes for any credible OSS project targeting adoption by engineering teams.

**Already in place:** `LICENSE` (MIT), `CONTRIBUTING.md`, `CHANGELOG.md`.

| Item | Current Status | Action Required |
|------|---------------|-----------------|
| `CODE_OF_CONDUCT.md` | Missing | Create at repo root (Contributor Covenant v2.1) |
| `SECURITY.md` | Missing at root (security docs exist at `docs/security.md`) | Create root-level `SECURITY.md` with vulnerability disclosure policy and link to detailed docs |
| GitHub Issue Templates | Missing (no `.github/ISSUE_TEMPLATE/` directory) | Create bug report, feature request, and question templates |
| GitHub PR Template | Missing (no `.github/PULL_REQUEST_TEMPLATE.md`) | Create PR template with checklist (tests pass, docs updated, etc.) |
| GitHub Discussions | Not enabled | Enable for Q&A, Ideas, and Show-and-Tell categories |
| `onBrokenLinks` config | Set to `'warn'` (`website/docusaurus.config.ts:16`) | Change to `'throw'` — broken links must fail the docs build before v1 |
| `onBrokenMarkdownLinks` | Set to `'warn'` (`website/docusaurus.config.ts:27`) | Change to `'throw'` for consistency |
| Scalafix in CI | Plugin installed (`project/plugins.sbt`) but not enforced in `ci.yml` | Add `sbt scalafix --check` step to CI workflow |

### 2. Developer Experience (P0 — Blockers)

What a Scala developer needs to evaluate and adopt the library within their first hour.

| Item | Current Status | Action Required |
|------|---------------|-----------------|
| Giter8 template (`sbt new`) | Missing | Create `constellation.g8` template for a 5-minute hello-world experience |
| Standalone example project | Missing — `example-app` is an in-tree sbt submodule | Extract to a separate repository or create a self-contained example in the docs that depends on published Maven Central artifacts |
| API stability guarantees | Implicit (no `@deprecated` annotations, but no stated policy) | Add "API Stability" section to docs defining what is public API vs. internal, and the guarantees each carries |
| Binary compatibility | Not checked — no MiMa (sbt-mima-plugin) in `project/plugins.sbt` | Add MiMa plugin, configure `mimaPreviousArtifacts` against v1.0.0 once released, enforce `mimaFailOnProblem` in CI |
| Scaladoc publishing | Not automated | Add Scaladoc generation step to `docs.yml` workflow; publish to GitHub Pages alongside Docusaurus site |
| `constellation-lang` CLI | No CLI exists | Create minimal CLI tool with `compile`, `run`, and `viz` subcommands (can be a new sbt module or a GraalVM native-image wrapper) |

### 3. Documentation Gaps (P1 — Important)

| Item | Current Status | Action Required |
|------|---------------|-----------------|
| "Why Constellation" page | Missing | Website page explaining the value proposition vs. alternatives (not Airflow — vs. hand-wired Scala code, raw Cats Effect composition, etc.) |
| Comparison guide | Missing | "Constellation vs. hand-rolled pipelines" showing concrete before/after examples |
| Blog / announcements | Disabled (`blog: false` in `docusaurus.config.ts:42`) | Enable blog preset, write v1.0 announcement post |
| Roadmap page | Missing | Public roadmap on website showing post-v1 direction |
| Migration guide v0.4 → v1.0 | Does not exist yet | Create once breaking changes (if any) are finalized |
| Cookbook / recipes | 21 `.cst` examples exist but are not surfaced as docs | Surface existing examples as a cookbook section on the website; expand with recipes for common patterns: fan-out/fan-in, conditional branching, caching strategies |
| OpenAPI spec currency | Version stuck at 0.2.0 (actual: 0.4.0); missing RFC-015 endpoints | Update `docs/api/openapi.yaml` to match actual API surface (see also §5 for full scope) |

### 4. Testing & Quality (P1 — Important)

| Item | Current Status | Action Required |
|------|---------------|-----------------|
| HTTP API route test coverage | 21 test files, but coverage threshold at 60% | Raise http-api statement coverage target to 75%+ |
| lang-lsp coverage | ~60% statement / ~50% branch | Raise to 70% statement / 60% branch |
| Property-based tests | 2 files (`TypeSystemPropertyTest`, `CompilationPropertyTest`) — covers core types and compilation | Expand coverage: add parser round-tripping (parse → print → parse ≡ identity), runtime execution determinism, and serialization round-tripping |
| Mutation testing | None | Consider adding (nice-to-have, not blocking v1) |
| Integration test suite | 8 files (per-feature integration tests) | Add dedicated end-to-end integration tests: `.cst` source → compile → execute → verify outputs, covering the full pipeline lifecycle |

### 5. Runtime & API Polish (P1 — Important)

| Item | Current Status | Action Required |
|------|---------------|-----------------|
| `/metrics` format | Custom JSON only | Add Prometheus text format option via `Accept: text/plain` content negotiation |
| Structured JSON logging guide | Missing | Document how to configure Logback for JSON output in production deployments |
| Error message quality | Good but not audited holistically | Audit all user-facing error messages for clarity, actionability, and consistent formatting |
| OpenAPI spec completeness | Version stuck at 0.2.0; missing RFC-015 lifecycle endpoints | Bump to 1.0.0, add pipeline lifecycle, suspension, canary, and hot-reload endpoints |
| Graceful shutdown documentation | Minimal | Document shutdown behavior: drain timeout, in-flight request handling, resource cleanup order |

### 6. Packaging & Distribution (P2 — Nice-to-have for v1.0)

| Item | Current Status | Action Required |
|------|---------------|-----------------|
| Docker Hub image | Not published (Dockerfile and docker-compose.yml exist) | Add `docker push` step to CI on release tags |
| VSCode extension marketplace | Unclear publication status | Verify and document marketplace listing; add publish step to `vscode-extension-tests.yml` on release |
| Homebrew formula | N/A (no CLI yet) | Create after CLI tool exists (see §2) |
| Scaladex listing | Should happen automatically via Maven Central | Verify indexing after next publish; add badge to README if indexed |

### 7. Website & Marketing (P2 — Nice-to-have)

| Item | Current Status | Action Required |
|------|---------------|-----------------|
| SEO metadata | Basic | Ensure all doc pages have proper `description` frontmatter |
| Social preview image | Exists (`social-preview.svg`) | Verify it renders correctly on GitHub, Twitter/X, and LinkedIn embeds |
| Community links | Only GitHub Issues | Add Discord or GitHub Discussions link to website footer and README |
| Landing page polish | Exists with Hero / Features / Code / Stats sections | Review messaging for v1 positioning; update stats to reflect v1 capabilities |

---

## What's Explicitly Out of Scope for v1.0

These features are **not** part of Constellation's v1.0 value proposition. They may appear in future major versions but are not required for a credible v1.0 release as a typed pipeline framework:

- **Scheduling / cron triggers** — not our value prop; users bring their own scheduler
- **Event-driven execution** (webhooks, message queues) — integrate externally
- **Distributed / multi-node execution** — single-JVM execution model for v1
- **Streaming / incremental execution** — batch-oriented DAG execution
- **GraphQL API** — REST + LSP WebSocket is sufficient
- **REPL / web playground** — the CLI tool (§2) covers the interactive use case
- **Multi-tenant isolation** — single-tenant deployment model for v1

---

## Proposed v1.0 Release Criteria

All of the following must be green before cutting v1.0:

1. All P0 items from §1 (Community Infrastructure) and §2 (Developer Experience) are complete
2. All P1 items are complete or explicitly deferred with written justification in this RFC
3. `make test` passes with all modules at their target coverage thresholds
4. No `TODO` or `FIXME` comments in published source (modules that ship to Maven Central)
5. `CHANGELOG.md` updated with v1.0 section
6. Migration guide written (if any breaking changes from v0.4)
7. Website blog post for v1.0 announcement drafted and reviewed
8. README reviewed and updated for v1.0 messaging
9. OpenAPI spec (`docs/api/openapi.yaml`) matches actual API surface
10. MiMa baseline set against v1.0.0 for future binary compatibility checks

---

## Execution Phases

Suggested ordering of work. Each phase can be tracked via GitHub Issues referencing this RFC. P2 items (§6 Packaging, §7 Website) are not scheduled into phases — they can be addressed opportunistically or post-v1.

### Phase 1: Community Infrastructure (P0)

- Create `CODE_OF_CONDUCT.md` (Contributor Covenant v2.1)
- Create root-level `SECURITY.md` with disclosure policy
- Create `.github/ISSUE_TEMPLATE/` with bug report, feature request, and question templates
- Create `.github/PULL_REQUEST_TEMPLATE.md`
- Enable GitHub Discussions
- Change `onBrokenLinks` and `onBrokenMarkdownLinks` to `'throw'` in `website/docusaurus.config.ts`
- Add `sbt scalafix --check` step to `.github/workflows/ci.yml`

### Phase 2: Developer Experience (P0)

- Add sbt-mima-plugin for binary compatibility checking
- Write "API Stability" documentation section
- Add Scaladoc generation to `docs.yml` CI workflow
- Create Giter8 template (separate repo: `constellation.g8`)
- Create standalone example project or extract `example-app`
- Build minimal `constellation-lang` CLI module

### Phase 3: Testing, Quality & API Polish (P1)

- Raise http-api coverage threshold to 75%+
- Raise lang-lsp coverage to 70% statement / 60% branch
- Expand property-based tests: parser round-tripping, runtime determinism, serialization round-tripping
- Add end-to-end integration test suite (source → compile → execute → verify)
- Update `docs/api/openapi.yaml` to version 1.0.0 with all current endpoints
- Add Prometheus text format to `/metrics` endpoint (code change in `http-api`)
- Audit all user-facing error messages for clarity and actionability

### Phase 4: Documentation & Website (P1)

- Create "Why Constellation" page for website
- Create "Constellation vs. hand-rolled pipelines" comparison guide
- Enable blog in Docusaurus config, write v1.0 announcement post
- Create public roadmap page
- Write structured JSON logging guide
- Surface existing 21 `.cst` examples as a cookbook section; expand with additional patterns (fan-out/fan-in, conditional branching, caching strategies)
- Document graceful shutdown behavior

### Phase 5: Release

- Version bump 0.4.0 → 1.0.0 in `build.sbt` and `vscode-extension/package.json`
- Update `CHANGELOG.md` with v1.0 section
- Write migration guide (if breaking changes)
- Final self-review of README for v1.0 messaging
- Cut release via `scripts/release.ps1 -Type major`
- Publish announcement blog post

---

## References

- [RFC-001 through RFC-011](./): Resilience options (Retry, Timeout, Fallback, Cache, Delay, Backoff, Throttle, Concurrency, OnError, Lazy, Priority)
- [RFC-012](./rfc-012-dashboard-e2e-tests.md): Dashboard E2E Tests
- [RFC-013](./rfc-013-production-readiness.md): Production Readiness Roadmap
- [RFC-014](./rfc-014-suspendable-execution.md): Suspendable Execution
- [RFC-015](./rfc-015-pipeline-lifecycle.md): Pipeline Lifecycle Management (and sub-RFCs 015a–015g)
- [RFC-016](./rfc-016-project-qa-audit.md): Project-Wide QA Audit
- [CHANGELOG](../../../CHANGELOG.md)
- [OpenAPI Spec](../../api/openapi.yaml)
- [Docusaurus Config](../../../website/docusaurus.config.ts)
