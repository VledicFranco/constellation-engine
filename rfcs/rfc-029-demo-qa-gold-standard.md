# RFC-029: Multi-Agent Demo App QA — Gold Standard Test Suite

- **Status:** Draft
- **Created:** 2026-02-14
- **Author:** Claude (AI-assisted)

## Summary

Turn the `constellation-demo` app into a **gold standard test suite** that proves constellation-engine works end-to-end. The demo currently showcases 13 pipelines, 2 external providers, monitoring, and 8 codelabs — but has **zero automated tests**. All validation is manual curl scripts.

This RFC proposes a multi-agent parallel effort to:
1. Add pipelines exercising every missing language feature and stdlib function
2. Write automated tests for every pipeline (compilation, execution, error handling)
3. Discover and fix engine bugs through systematic testing
4. Build CI/CD infrastructure for continuous regression detection

## Motivation

The demo app is the most comprehensive integration point for constellation-engine — it exercises the HTTP API, pipeline compilation, execution runtime, external providers, and monitoring in a realistic deployment. Yet it has no automated tests, meaning:

- **No regression detection** — Breaking changes to the engine are invisible until manual testing
- **No proof of correctness** — Users have no reference for expected behavior
- **Incomplete feature coverage** — Many engine features are never exercised
- **No bug discovery feedback loop** — Integration bugs are discovered late

Turning the demo into an automatically-tested reference implementation creates:
1. **Proof that the engine works** end-to-end across releases
2. **Regression detection** on every commit
3. **Living documentation** — tests show users exactly how features work
4. **Bug discovery** feedback loop to the main engine repo

## Feature Gap Analysis

### Language Features NOT in Any Demo Pipeline

| Feature | Engine Support | Demo Coverage |
|---------|---------------|---------------|
| Pattern matching (`match`) | Full | None |
| Lambda expressions | Full | None |
| Type definitions (`type Name = {...}`) | Full | None |
| String interpolation (`"${var}"`) | Full | None |
| Projection (`record[f1, f2]`) | Full | None |
| Type algebra (record merge `+`) | Full | None |
| Higher-order stdlib (filter, map, all, any) | Full | None |

### 29 Stdlib Functions Not in Any Demo Pipeline

**Math:** abs, modulo, round, negate

**String:** join, string-length

**List:** list-length, list-first, list-last, list-is-empty, list-sum, list-concat, list-contains, list-reverse

**Comparison:** eq-int, eq-string, gt, lt, gte, lte

**Conversion:** to-string, to-int, to-float

**Utility:** identity, log

**Higher-order:** filter, map, all, any

### Runtime Features NOT Demonstrated

| Feature | Engine Support | Demo Coverage |
|---------|---------------|---------------|
| Execution suspension and resumption | Full | None |
| Stepped debugging | Full | None |
| Pipeline versioning and rollback | Full | None |
| Canary deployment | Full (via SDK) | None |
| Auth / rate limiting / CORS | Full | Configured but not demo'd |

## Agent Roles

Four agents work in parallel, each with a distinct responsibility:

| Agent | Role | Works On |
|-------|------|----------|
| **Feature Agent** | Adds new pipelines + provider modules exercising missing features | `constellation-demo` repo |
| **Test Agent** | Writes automated tests (unit, integration, E2E) | `constellation-demo` repo |
| **Bug Hunter Agent** | Reproduces failures as tests in main repo, fixes engine bugs | `constellation-engine` repo |
| **Infra Agent** | CI/CD, Docker, test harness, reporting | Both repos |

### Agent Interaction Protocol

```
Feature Agent ──(new pipeline)──→ Test Agent ──(writes tests)──→ CI
                                       │
                                  test fails?
                                       │
                                  engine bug? ──→ Bug Hunter Agent ──(fix)──→ engine release
                                       │                                          │
                                  demo bug? ──→ Feature Agent ──(fix)──→ re-test  │
                                                                                   │
                                  Infra Agent ──(bump version)──→ re-test ◄────────┘
```

## Work Packages

### Dependency Graph

```
WP-0: Version Upgrade (prerequisite for all)
  ├── WP-1: Lambda & Higher-Order Pipelines ──┐
  ├── WP-2: Pattern Matching Pipeline         ├── WP-7: Pipeline Compilation Tests
  ├── WP-3: Type Definition Pipeline          │   WP-8: Pipeline Execution Tests
  ├── WP-4: String Interpolation Pipeline     │   WP-9: Error Scenario Tests
  ├── WP-5: Missing Stdlib Pipelines          ┘   WP-10: Provider Lifecycle Tests
  ├── WP-6: Advanced Runtime Demos ────────────── WP-11: Runtime Feature Tests
  ├── WP-12: E2E Docker Compose Test Suite
  ├── WP-13: CI/CD Pipeline for Demo Repo
  ├── WP-14: Performance Benchmarks
  └── WP-15: Documentation & README Updates
```

---

### WP-0: Version Upgrade (Infra Agent)

**Blocks:** All other WPs

**Scope:**
- Update `server/build.sbt` to v0.8.0 for all constellation dependencies
- Update `provider-scala/build.sbt` to v0.8.0 for SDK
- Verify Docker Compose builds and all services start
- Verify all 13 existing pipelines still execute correctly via manual curl

**Acceptance criteria:**
- [ ] All services start with `docker compose up`
- [ ] Health check returns OK for all services
- [ ] All 13 existing pipelines compile and execute successfully

---

### WP-1: Lambda & Higher-Order Pipelines (Feature Agent)

**Depends on:** WP-0

**Scope:**
- Pipeline using lambda expressions passed to higher-order stdlib functions
- Pipeline using `filter`, `map`, `all`, `any` with inline lambdas
- New provider module that returns list data suitable for higher-order processing

**Deliverables:**
- `pipelines/lambda-filter.cst` — Filter a list using a lambda predicate
- `pipelines/lambda-map-reduce.cst` — Map + reduce pattern
- `pipelines/higher-order-validation.cst` — Use `all`/`any` for data validation

**Acceptance criteria:**
- [ ] Each pipeline compiles without errors
- [ ] Each pipeline executes with sample inputs and produces correct outputs
- [ ] Lambdas are used in at least 3 different stdlib function contexts

---

### WP-2: Pattern Matching Pipeline (Feature Agent)

**Depends on:** WP-0

**Scope:**
- Pipeline demonstrating `match` expressions on union types
- Provider module that returns union-typed data

**Deliverables:**
- `pipelines/pattern-match-basic.cst` — Match on a simple union (string | int)
- `pipelines/pattern-match-nested.cst` — Match with nested record access

**Acceptance criteria:**
- [ ] Match expression covers all variants
- [ ] Exhaustiveness is validated by the compiler
- [ ] Pipeline executes correctly for each variant

---

### WP-3: Type Definition Pipeline (Feature Agent)

**Depends on:** WP-0

**Scope:**
- Pipeline using `type` keyword to define custom record and union types
- Demonstrates type reuse across multiple module calls

**Deliverables:**
- `pipelines/type-definitions.cst` — Define and use custom types
- `pipelines/type-composition.cst` — Record merge (`+`) and projection

**Acceptance criteria:**
- [ ] Custom types compile and are usable in module calls
- [ ] Record merge produces correct combined structure
- [ ] Projection selects correct subset of fields

---

### WP-4: String Interpolation Pipeline (Feature Agent)

**Depends on:** WP-0

**Scope:**
- Pipeline using `"${var}"` string interpolation syntax
- Demonstrates interpolation with various value types

**Deliverables:**
- `pipelines/string-interpolation.cst` — Basic interpolation with variables
- `pipelines/template-rendering.cst` — Complex template with multiple interpolations

**Acceptance criteria:**
- [ ] String interpolation works with String, Int, Float, Boolean values
- [ ] Nested interpolation (if supported) works correctly
- [ ] Output matches expected formatted strings

---

### WP-5: Missing Stdlib Pipelines (Feature Agent)

**Depends on:** WP-0

**Scope:**
- Pipelines that exercise every stdlib function not currently in any demo
- Organized by category: math, string, list, comparison, conversion, utility

**Deliverables:**
- `pipelines/stdlib-math.cst` — abs, modulo, round, negate
- `pipelines/stdlib-string.cst` — join, string-length
- `pipelines/stdlib-list.cst` — list-length, list-first, list-last, list-is-empty, list-sum, list-concat, list-contains, list-reverse
- `pipelines/stdlib-comparison.cst` — eq-int, eq-string, gt, lt, gte, lte
- `pipelines/stdlib-conversion.cst` — to-string, to-int, to-float
- `pipelines/stdlib-utility.cst` — identity, log

**Acceptance criteria:**
- [ ] Every stdlib function appears in at least one pipeline
- [ ] Each pipeline executes with sample inputs
- [ ] Golden output files exist for snapshot testing

---

### WP-6: Advanced Runtime Demos (Feature Agent)

**Depends on:** WP-0

**Scope:**
- Pipeline versioning: deploy v1, upgrade to v2, rollback to v1
- Canary deployment: deploy canary, observe, promote/rollback
- Suspension/resumption: suspend at data node, resume with input
- Auth demo: API key configuration and role-based access

**Deliverables:**
- `demos/versioning/` — Scripts demonstrating pipeline version lifecycle
- `demos/canary/` — Scripts demonstrating canary rollout
- `demos/suspension/` — Pipeline that suspends and resumes
- `demos/auth/` — Auth configuration and role-based access examples

**Acceptance criteria:**
- [ ] Versioning demo shows deploy, upgrade, and rollback
- [ ] Canary demo shows health-gated promotion
- [ ] Suspension demo successfully suspends and resumes execution
- [ ] Auth demo demonstrates all three roles (Admin, Execute, ReadOnly)

---

### WP-7: Pipeline Compilation Tests (Test Agent)

**Depends on:** WP-1 through WP-5

**Scope:**
- Test that every `.cst` file compiles successfully via `POST /compile`
- Test compilation error messages for intentionally invalid pipelines
- Parametric test: glob all `pipelines/*.cst`, compile each

**Test structure:**
```bash
# Parametric compilation test
for pipeline in pipelines/*.cst; do
  response=$(curl -s -X POST http://localhost:8080/compile \
    -H "Content-Type: text/plain" \
    -d @"$pipeline")
  # Assert no compilation errors
  echo "$response" | jq -e '.errors | length == 0'
done
```

**Deliverables:**
- `tests/compilation/test-all-compile.sh` — Parametric compilation test
- `tests/compilation/test-invalid-pipelines.sh` — Error message validation
- `tests/compilation/invalid/` — Intentionally broken pipeline files

**Acceptance criteria:**
- [ ] Every valid `.cst` file compiles without errors
- [ ] Invalid pipelines produce descriptive error messages
- [ ] Test output is machine-parseable (exit codes + structured output)

---

### WP-8: Pipeline Execution Tests (Test Agent)

**Depends on:** WP-1 through WP-5

**Scope:**
- Test that every pipeline executes with sample inputs and produces expected outputs
- Golden output files: `tests/golden/{pipeline-name}.json` with expected results
- Snapshot testing: execute → compare against golden file

**Test structure:**
```bash
# Golden output test
pipeline="pipelines/stdlib-math.cst"
inputs='{"x": 10, "y": 3}'
expected="tests/golden/stdlib-math.json"

actual=$(curl -s -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -d "{\"source\": \"$(cat $pipeline)\", \"inputs\": $inputs}")

diff <(echo "$actual" | jq -S .outputs) <(jq -S . "$expected")
```

**Deliverables:**
- `tests/execution/test-all-execute.sh` — Parametric execution test
- `tests/golden/` — Golden output files for each pipeline
- `tests/inputs/` — Sample input files for each pipeline

**Acceptance criteria:**
- [ ] Every pipeline has a golden output file
- [ ] All golden output tests pass
- [ ] Snapshot update script exists for regenerating golden files

---

### WP-9: Error Scenario Tests (Test Agent)

**Depends on:** WP-0

**Scope:**
- Missing required inputs
- Type mismatches in inputs
- Provider timeout (simulate slow provider)
- Provider crash during execution
- Invalid pipeline references
- Rate limit exceeded (if auth enabled)

**Deliverables:**
- `tests/errors/test-missing-inputs.sh`
- `tests/errors/test-type-mismatches.sh`
- `tests/errors/test-provider-timeout.sh`
- `tests/errors/test-provider-crash.sh`
- `tests/errors/test-invalid-pipeline.sh`
- `tests/errors/test-rate-limit.sh`

**Acceptance criteria:**
- [ ] Each error scenario returns appropriate HTTP status code
- [ ] Error messages are descriptive and actionable
- [ ] Server remains healthy after error scenarios (no crash)

---

### WP-10: Provider Lifecycle Tests (Test Agent)

**Depends on:** WP-0

**Scope:**
- Provider registration and module discovery
- Provider heartbeat and health monitoring
- Provider drain and graceful shutdown
- Provider reconnection after failure
- Multiple provider instances with load balancing

**Deliverables:**
- `tests/provider/test-registration.sh` — Register, verify modules available
- `tests/provider/test-heartbeat.sh` — Verify heartbeat liveness detection
- `tests/provider/test-drain.sh` — Drain provider, verify graceful shutdown
- `tests/provider/test-reconnection.sh` — Kill provider, verify auto-reconnection
- `tests/provider/test-load-balancing.sh` — Multiple instances, verify round-robin

**Acceptance criteria:**
- [ ] Provider registers and modules become callable
- [ ] Missed heartbeats trigger deregistration within timeout
- [ ] Drain completes in-flight work before disconnecting
- [ ] Reconnection re-registers modules automatically
- [ ] Load balancing distributes calls across pool members

---

### WP-11: Runtime Feature Tests (Test Agent)

**Depends on:** WP-6

**Scope:**
- Cache effectiveness (execute twice, verify cache hit via metrics)
- Priority scheduling (submit low + high priority, verify ordering)
- Retry behavior (flaky module, verify retry count)
- Timeout enforcement (slow module, verify timeout error)

**Deliverables:**
- `tests/runtime/test-cache.sh` — Execute twice, check metrics for cache hits
- `tests/runtime/test-priority.sh` — Submit with different priorities
- `tests/runtime/test-retry.sh` — Execute with flaky module
- `tests/runtime/test-timeout.sh` — Execute with slow module

**Acceptance criteria:**
- [ ] Cache hit rate > 0.8 for repeated identical executions
- [ ] Priority ordering is respected under load
- [ ] Retry count matches configured retry policy
- [ ] Timeout fires within configured window

---

### WP-12: E2E Docker Compose Test Suite (Infra Agent)

**Depends on:** WP-7 through WP-11

**Scope:**
- Shell script or Node.js test runner that orchestrates the full suite
- `docker compose up -d` → wait for health → run all tests → `docker compose down`
- Test output: JUnit XML for CI integration
- Screenshot capture for dashboard state (optional, Playwright)

**Deliverables:**
- `tests/run-all.sh` — Master test runner
- `tests/lib/assertions.sh` — Shared assertion functions
- `tests/lib/wait-for-health.sh` — Health check polling
- `tests/reports/` — JUnit XML output directory

**Acceptance criteria:**
- [ ] Single command runs entire test suite
- [ ] Tests produce JUnit XML reports
- [ ] Non-zero exit code on any failure
- [ ] Cleanup runs even on test failure (trap handler)

---

### WP-13: CI/CD Pipeline (Infra Agent)

**Depends on:** WP-12

**Scope:**
- GitHub Actions workflow in `constellation-demo`
- Trigger: push to master, PR
- Steps: build images → start compose → run tests → report
- Badge in README

**Deliverables:**
- `.github/workflows/demo-tests.yml` — CI workflow
- Badge added to `README.md`

**Acceptance criteria:**
- [ ] CI runs on every push and PR
- [ ] Test results appear as PR check
- [ ] JUnit XML uploaded as artifact
- [ ] Build badge shows pass/fail in README

---

### WP-14: Performance Benchmarks (Test Agent)

**Depends on:** WP-8

**Scope:**
- Baseline latency for each pipeline
- Cache hit ratio after repeated execution
- Provider registration time
- Concurrent execution throughput

**Deliverables:**
- `tests/performance/benchmark-pipelines.sh` — Per-pipeline latency
- `tests/performance/benchmark-cache.sh` — Cache effectiveness
- `tests/performance/benchmark-concurrent.sh` — Throughput under load
- `tests/performance/results/baseline.json` — Baseline results

**Acceptance criteria:**
- [ ] Baseline established for all 13+ pipelines
- [ ] Cache hit rate measured and documented
- [ ] Concurrent throughput measured (requests/second)
- [ ] Results stored in JSON for trend tracking

---

### WP-15: Documentation & README Updates (Feature Agent)

**Depends on:** WP-7 through WP-14

**Scope:**
- Update demo README with test instructions
- Add test coverage to each codelab
- Document the "run tests" workflow

**Deliverables:**
- Updated `README.md` with testing section
- Updated codelab docs with test references
- `TESTING.md` — Comprehensive testing guide

**Acceptance criteria:**
- [ ] README includes "Running Tests" section
- [ ] Each codelab references its automated tests
- [ ] TESTING.md covers local dev, CI, and debugging failed tests

## Bug Discovery Protocol

When a test fails due to an engine bug (not a demo bug):

### Step 1: Test Agent Creates Issue

The Test Agent creates a GitHub issue on `constellation-engine` with:
- Minimal reproduction steps (pipeline source, inputs, curl command)
- Expected vs actual behavior
- Link to failing demo test
- Label: `bug`, `demo-discovered`

### Step 2: Bug Hunter Agent Reproduces

The Bug Hunter Agent picks up the issue:
1. Writes a failing test in the engine repo that reproduces the bug
2. Commits the failing test: `test(module): reproduce #NNN — description`
3. Fixes the bug in engine code
4. Verifies the engine test passes
5. Commits the fix: `fix(module): description. Closes #NNN`
6. Opens a PR with both the test and the fix

### Step 3: Infra Agent Bumps Version

After the fix is released:
1. Infra Agent bumps engine version in demo `build.sbt`
2. Runs `docker compose build` to pick up new version
3. Runs the previously-failing demo test

### Step 4: Test Agent Confirms

The Test Agent re-runs the full suite to confirm:
1. The originally failing test now passes
2. No regressions in other tests

## Test Technology Stack

| Layer | Tool | Why |
|-------|------|-----|
| Pipeline compilation/execution tests | Shell scripts + curl + jq | Matches demo's existing HTTP API pattern, zero deps |
| Provider lifecycle tests | Shell scripts + curl | Direct API testing |
| E2E orchestration | Shell script | Docker Compose lifecycle management |
| Assertions | jq comparisons + diff | Lightweight, CI-friendly |
| Dashboard E2E (optional) | Playwright | Visual validation (reuse existing `dashboard-tests/` patterns) |
| CI | GitHub Actions | Standard, free for public repos |
| Performance | curl + time + jq | Latency measurement, results in JSON |

### Why Shell Scripts Over a Test Framework?

1. **Zero dependencies** — No Node.js, Python, or JVM needed beyond Docker
2. **Matches the demo** — The demo already uses curl scripts for manual testing
3. **Transparent** — Anyone can read a shell script and understand the test
4. **CI-friendly** — Exit codes are the universal test result format
5. **Portable** — Works on any Unix-like system (Linux, macOS, WSL)

## Acceptance Criteria (RFC-Level)

The effort described in this RFC is complete when:

- [ ] Every stdlib function appears in at least one tested pipeline
- [ ] Every language feature (match, lambda, type def, interpolation, projection, merge) has a tested pipeline
- [ ] Every runtime feature (cache, retry, timeout, priority, fallback) has an automated test
- [ ] Provider lifecycle (register, heartbeat, drain, deregister) is tested
- [ ] All tests run in CI on every push
- [ ] Bug discovery protocol has been exercised at least once
- [ ] Demo README documents how to run all tests
- [ ] Performance baselines are established and tracked

## Parallel Execution Timeline

```
Day 1:  WP-0 (Infra) ─────────────────────────────────────────
Day 2:  WP-1,2,3 (Feature) │ WP-13 (Infra) │ WP-7 (Test)
Day 3:  WP-4,5 (Feature)   │ WP-12 (Infra) │ WP-8 (Test)
Day 4:  WP-6 (Feature)     │ WP-9,10 (Test) │ Bug fixes
Day 5:  WP-11,14 (Test)    │ Bug fixes      │ WP-15 (Docs)
Day 6:  Integration testing │ Final bug fixes │ Polish
```

**Critical path:** WP-0 → WP-1..5 → WP-7,8 → WP-12 → WP-13

**Parallelizable from Day 2:**
- Feature Agent works on WP-1..5 independently
- Test Agent writes WP-7,8 test harness while pipelines are being created (can test existing 13 pipelines immediately)
- Infra Agent sets up WP-13 CI scaffolding while tests are being written
- Bug Hunter Agent is on standby, activated when test failures reveal engine bugs

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Engine bugs block demo tests | Test suite incomplete | Bug Hunter Agent prioritizes blocking bugs; tests skip known failures |
| v0.8.0 breaks existing demo pipelines | WP-0 takes longer | Identify breaking changes early; fix or update pipelines |
| Shell test scripts become hard to maintain | Long-term maintenance burden | Keep scripts small and focused; shared assertion library |
| Docker Compose flaky in CI | False failures | Retry logic; health check polling with timeout |
| Performance benchmarks vary across environments | Unreliable baselines | Establish per-environment baselines; focus on relative regressions |

## Related

- [RFC-012](./rfc-012-dashboard-e2e-tests.md) — Dashboard E2E test patterns (Playwright)
- [RFC-016](./rfc-016-project-qa-audit.md) — Project QA audit
- [RFC-026](./rfc-026-testing-framework.md) — Testing framework RFC
- [Organon: extensibility](../organon/features/extensibility/) — Module provider protocol documentation
- [Organon: control-plane](../organon/features/extensibility/control-plane.md) — Control plane lifecycle
- [Organon: cvalue-wire-format](../organon/features/extensibility/cvalue-wire-format.md) — CValue JSON contract
