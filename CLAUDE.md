# CLAUDE.md - Development Rules for Constellation Engine

This file contains actionable rules that Claude must follow when working on this codebase.

## Development Commands

**ALWAYS use these commands. NEVER use raw sbt commands directly.**

| Task | Command |
|------|---------|
| Start dev environment | `make dev` |
| Start server only | `make server` |
| Start server (hot-reload) | `make server-rerun` |
| Run all tests | `make test` |
| Run dashboard E2E tests | `make test-dashboard` |
| Run dashboard smoke tests | `make test-dashboard-smoke` |
| Compile everything | `make compile` |
| Build dashboard TypeScript | `make dashboard` |
| Watch dashboard TypeScript | `make dashboard-watch` |
| Clean build | `make clean` |
| Build fat JAR | `make assembly` |
| Build Docker image | `make docker-build` |
| Run Docker container | `make docker-run` |

**Windows (if make unavailable):**
```powershell
.\scripts\dev.ps1              # Full dev environment
.\scripts\dev.ps1 -ServerOnly  # Server only
```

**Why:** Raw `sbt` commands bypass project-specific configurations and are harder to maintain.

## Server Endpoints

- HTTP API: `http://localhost:{port}` (default: 8080)
- LSP WebSocket: `ws://localhost:{port}/lsp`
- Health check: `GET /health`
- Liveness probe: `GET /health/live`
- Readiness probe: `GET /health/ready`
- Detail diagnostics: `GET /health/detail` (opt-in, auth-gated)

**Port Configuration:**
The server port is configurable via the `CONSTELLATION_PORT` environment variable (default: 8080).

**Scheduler Configuration:**
The global priority scheduler can be configured via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_SCHEDULER_ENABLED` | `false` | Enable bounded scheduler with priority ordering |
| `CONSTELLATION_SCHEDULER_MAX_CONCURRENCY` | `16` | Maximum concurrent tasks when scheduler enabled |
| `CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT` | `30s` | Time before low-priority tasks get priority boost |

Example:
```bash
CONSTELLATION_SCHEDULER_ENABLED=true CONSTELLATION_SCHEDULER_MAX_CONCURRENCY=8 sbt "exampleApp/run"
```

For more details, see `docs/dev/global-scheduler.md`.

**HTTP Hardening Configuration (all opt-in, disabled by default):**

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_API_KEYS` | (none) | Comma-separated `key:Role` pairs, e.g. `key1:Admin,key2:Execute` |
| `CONSTELLATION_CORS_ORIGINS` | (none) | Comma-separated origin URLs, e.g. `https://app.example.com`. Use `*` for wildcard. |
| `CONSTELLATION_RATE_LIMIT_RPM` | `100` | Requests per minute per client IP (only active when rate limiting enabled via builder) |
| `CONSTELLATION_RATE_LIMIT_BURST` | `20` | Burst size for rate limiter token bucket |

Example (fully hardened server):
```scala
ConstellationServer.builder(constellation, compiler)
  .withAuth(AuthConfig(apiKeys = Map("key1" -> ApiRole.Admin)))
  .withCors(CorsConfig(allowedOrigins = Set("https://app.example.com")))
  .withRateLimit(RateLimitConfig(requestsPerMinute = 100, burst = 20))
  .withHealthChecks(HealthCheckConfig(enableDetailEndpoint = true))
  .run
```

| Role | Permissions |
|------|------------|
| `Admin` | All HTTP methods |
| `Execute` | GET + POST only |
| `ReadOnly` | GET only |

Public paths exempt from auth and rate limiting: `/health`, `/health/live`, `/health/ready`, `/metrics`.

## Code Conventions

### Module Creation

**ALWAYS use case classes for inputs/outputs, never tuples:**
```scala
// CORRECT
case class MyInput(text: String)
case class MyOutput(result: String)

val myModule = ModuleBuilder
  .metadata("MyModule", "Description", 1, 0)
  .implementationPure[MyInput, MyOutput] { input =>
    MyOutput(input.text.toUpperCase)
  }
  .build

// WRONG - Scala 3 doesn't support single-element tuples
.implementationPure[(String,), (String,)] { ... }
```

### Required Imports

```scala
import cats.effect.IO
import cats.implicits._  // Required for .traverse
import io.constellation._
import io.constellation.TypeSystem._
```

### Naming Conventions

- **Modules:** PascalCase (`Uppercase`, `WordCount`)
- **Variables:** camelCase (`val uppercase`, `val wordCount`)
- **Case classes:** PascalCase (`TextInput`, `WordCountOutput`)
- **Fields:** camelCase, must match constellation-lang variable names

### Module Name Matching

Module names in `ModuleBuilder.metadata()` must EXACTLY match usage in constellation-lang:
```scala
// Scala
ModuleBuilder.metadata("Uppercase", ...)  // Name: "Uppercase"

// constellation-lang
result = Uppercase(text)  // Must match exactly (case-sensitive)
```

## Dependency Rules

**CRITICAL: Modules can only depend on modules above them in this graph:**

```
core
  ↓
runtime ← lang-ast
            ↓
         lang-parser
            ↓
       lang-compiler
            ↓
    ┌───────┼───────┐
lang-stdlib  │    lang-lsp
             ↓
          http-api
             ↓
        example-app
```

**NEVER create circular dependencies.**

## Testing

Run tests before committing:
```bash
make test           # All tests
make test-core      # Core module
make test-compiler  # Compiler module
make test-lsp       # LSP module
make test-dashboard       # Dashboard E2E tests
make test-dashboard-smoke # Dashboard smoke tests (quick)
```

## File Locations

| Component | Path |
|-----------|------|
| Type system | `modules/core/.../TypeSystem.scala` |
| ModuleBuilder | `modules/runtime/.../ModuleBuilder.scala` |
| Parser | `modules/lang-parser/.../ConstellationParser.scala` |
| Compiler | `modules/lang-compiler/.../DagCompiler.scala` |
| StdLib | `modules/lang-stdlib/.../StdLib.scala` |
| ExampleLib | `modules/example-app/.../ExampleLib.scala` |
| HTTP Server | `modules/http-api/.../ConstellationServer.scala` |
| Auth Middleware | `modules/http-api/.../AuthMiddleware.scala` |
| CORS Middleware | `modules/http-api/.../CorsMiddleware.scala` |
| Rate Limit Middleware | `modules/http-api/.../RateLimitMiddleware.scala` |
| Health Check Routes | `modules/http-api/.../HealthCheckRoutes.scala` |
| LSP Server | `modules/lang-lsp/.../ConstellationLanguageServer.scala` |
| Dashboard TS sources | `dashboard/src/` |
| Dashboard types | `dashboard/src/types.d.ts` |
| Dashboard E2E Tests | `dashboard-tests/` |
| Dashboard Page Objects | `dashboard-tests/pages/` |
| Dockerfile | `Dockerfile` |
| Docker Compose | `docker-compose.yml` |
| Docker ignore | `.dockerignore` |
| K8s Manifests | `deploy/k8s/` |

## Adding New Functions

When adding a module to example-app:

1. Define the module in `modules/example-app/.../modules/`
2. Add FunctionSignature to `ExampleLib.scala`
3. Register in `allSignatures` and `allModules`
4. Restart server

## VSCode Extension

| Shortcut | Action |
|----------|--------|
| `Ctrl+Shift+R` | Run script |
| `Ctrl+Shift+D` | Show DAG visualization |
| `Ctrl+Space` | Autocomplete |
| `F5` | Launch extension debug |

## Common Pitfalls to Avoid

1. **Don't forget `cats.implicits._`** - Required for `.traverse`
2. **Don't mismatch field names** - Case class fields must match constellation-lang variable names
3. **Don't skip testing** - Run `make test` before committing

## Documentation

- Full architecture: See `docs/architecture.md`
- Contributing guide: See `CONTRIBUTING.md`
- **Issue tracking: Use GitHub Issues**
- Language documentation: See `docs/constellation-lang/`

---

## Performance Testing & Benchmarks

**Run benchmarks before performance-critical changes:**

| Command | Purpose |
|---------|---------|
| `sbt "langCompiler/testOnly *Benchmark"` | All compiler benchmarks |
| `sbt "langCompiler/testOnly *RegressionTests"` | Regression suite (CI enforced) |
| `sbt "langLsp/testOnly *Benchmark"` | LSP operation benchmarks |
| `sbt "runtime/testOnly *ExecutionBenchmark"` | Runtime execution |

### Key Performance Targets

| Operation | Target | Critical |
|-----------|--------|----------|
| Parse (small) | <5ms | Yes |
| Full pipeline (medium) | <100ms | Yes |
| Cache hit | <5ms | Yes |
| Autocomplete | <50ms | Yes |
| Cache speedup | >5x | Yes |

### Check Cache Effectiveness

```bash
curl http://localhost:8080/metrics | jq .cache
# hitRate should be >0.8 for unchanged sources
```

### Performance Documentation

- **Full guide:** `docs/dev/performance-benchmarks.md` (all benchmarks, targets, troubleshooting)
- **Framework guide:** `docs/dev/benchmark-framework.md` (how to write new benchmarks)
- **E2E fixtures:** `vscode-extension/src/test/fixtures/PERF-README.md`
- **CI workflow:** `.github/workflows/benchmark.yml`

### Adding Performance Tests

When writing performance-sensitive code:

1. Add baseline to `RegressionTests.scala` if critical
2. Use `BenchmarkHarness.measureWithWarmup()` for measurements
3. Use `TestFixtures` for consistent input (small/medium/large programs)
4. Document expected results in `docs/dev/performance-benchmarks.md`

---

## Dashboard E2E Tests

**Run E2E tests before modifying dashboard HTML, JS, CSS, or API routes:**

| Command | Purpose |
|---------|---------|
| `make test-dashboard` | Run all dashboard E2E tests |
| `make test-dashboard-smoke` | Quick smoke check (~30s) |
| `make test-dashboard-full` | Full suite with HTML report |
| `make install-dashboard-tests` | Install Playwright + browsers |

### Key Test Targets

| Test Suite | Coverage | Critical |
|------------|----------|----------|
| Smoke tests | Dashboard loads, health OK, no JS errors | Yes |
| File browsing | Tree load, folder toggle, file selection | Yes |
| Script execution | Input fill, Run, output verification | Yes |
| DAG interaction | Node click, zoom, layout toggle | No |
| Execution history | History list, filter, detail view | No |
| Navigation | View switching, hash routing, deep links | No |
| Keyboard shortcuts | Ctrl+Enter execution | No |
| Error handling | Missing inputs, graceful errors | No |

### Running Tests Locally

```bash
# First-time setup
make install-dashboard-tests

# Start server (in a separate terminal)
make server

# Run smoke tests (quick verification)
make test-dashboard-smoke

# Run full suite
make test-dashboard
```

### Debugging Failures

```bash
# Run tests with browser visible
cd dashboard-tests && npx playwright test --headed

# Run specific test file
cd dashboard-tests && npx playwright test file-browsing.spec.ts

# Open HTML report
cd dashboard-tests && npx playwright show-report reports

# View failure screenshots
ls dashboard-tests/test-results/

# View trace for failed test
cd dashboard-tests && npx playwright show-trace test-results/<trace-file>.zip
```

### Dashboard E2E Documentation

- **RFC:** `docs/dev/rfcs/rfc-012-dashboard-e2e-tests.md`
- **Test directory:** `dashboard-tests/`
- **Page objects:** `dashboard-tests/pages/` (living documentation of UI contract)
- **CI workflow:** `.github/workflows/dashboard-tests.yml`

### Adding Dashboard Tests

When modifying dashboard code:

1. Run `make test-dashboard-smoke` to verify nothing is broken
2. If adding a new UI component, create a Page Object in `dashboard-tests/pages/`
3. Add test specs in `dashboard-tests/tests/`
4. Run `make test-dashboard` to verify the full suite
5. Page Objects serve as machine-readable documentation of the UI contract

---

## Playwright Dev Loop

**Use screenshot-driven iteration when modifying dashboard visuals (CSS, HTML, JS, or Cytoscape config).** The protocol captures the dashboard in 16+ states via Playwright, lets the agent visually analyze results against a stated objective, and repeats -- up to 5 autonomous iterations before presenting screenshots for human review.

**Full protocol:** `docs/dev/playwright-dev-loop.md`

### Automation Scripts (Windows)

| Command | Purpose |
|---------|---------|
| `.\scripts\dev-loop.ps1` | Full iteration: kill server, restart, run screenshot audit |
| `.\scripts\dev-loop.ps1 -Compile` | Same but compile first (for Scala/backend changes) |
| `.\scripts\dev-loop.ps1 -TestFilter "1-simple"` | Run only matching screenshot tests (faster) |
| `.\scripts\restart-server.ps1` | Restart server only (no screenshots) |
| `.\scripts\restart-server.ps1 -Compile` | Compile + restart server only |

All scripts accept `-Port` (default `8080`).

### Manual Command

```bash
cd dashboard-tests && npx playwright test screenshot-audit --reporter=list
```

**Screenshots output:** `dashboard-tests/screenshots/`

### Protocol Summary

| Step | Action |
|------|--------|
| 1 | Receive visual objective from user |
| 2 | Make code changes (CSS/HTML/JS/Scala) |
| 3 | `.\scripts\dev-loop.ps1` (or `.\scripts\dev-loop.ps1 -Compile` for backend changes) |
| 4 | Read screenshots from `dashboard-tests/screenshots/`, analyze against objective |
| 5 | If objective met: done. If not and iteration < 5: go to step 2. If iteration = 5: present to user. |

---

## Release Management

**Creating releases with semantic versioning:**

| Release Type | Version Change | Command |
|--------------|----------------|---------|
| Patch | 0.1.0 → 0.1.1 | `.\scripts\release.ps1 -Type patch` |
| Minor | 0.1.0 → 0.2.0 | `.\scripts\release.ps1 -Type minor` |
| Major | 0.1.0 → 1.0.0 | `.\scripts\release.ps1 -Type major` |

**Unix/macOS:**
```bash
./scripts/release.sh patch
./scripts/release.sh minor
./scripts/release.sh major
```

### What the Release Script Does

1. **Prerequisites check**: Verifies `gh` CLI is installed and authenticated
2. **Working directory check**: Ensures no uncommitted changes
3. **Branch check**: Must be on `master` branch
4. **Remote sync**: Verifies local is up-to-date with `origin/master`
5. **Version bump**: Updates version in:
   - `build.sbt` (Scala version)
   - `vscode-extension/package.json` (Extension version)
6. **Changelog update**: Replaces `[Unreleased]` with version and date
7. **Tests**: Runs full test suite (`sbt test`)
8. **Git operations**: Commits, tags, and pushes to origin
9. **GitHub release**: Creates release via `gh release create`

### Dry Run Mode

**Always test with dry-run first:**
```powershell
.\scripts\release.ps1 -Type patch -DryRun    # Windows
./scripts/release.sh patch --dry-run          # Unix/macOS
```

Dry-run shows what would happen without making any changes.

### Prerequisites

- **GitHub CLI (`gh`)**: Install from https://cli.github.com/
- **Authenticated**: Run `gh auth login` first
- **Clean working directory**: Commit or stash changes before releasing
- **On master branch**: Releases are only created from master

### CHANGELOG.md Format

Keep an `[Unreleased]` section at the top of `CHANGELOG.md`:
```markdown
## [Unreleased]

### Added
- New feature description

### Changed
- Change description

### Fixed
- Bug fix description
```

The release script converts this to:
```markdown
## [0.1.1] - 2026-01-22

### Added
...
```

---

## Self-Review Protocol (MANDATORY)

**Before merging to master, review your own changes as if you were a senior engineer.**

### Code Review Checklist

Run through this checklist for EVERY file you modified:

#### 1. Correctness
- [ ] Does the code do what the issue asks for?
- [ ] Are edge cases handled?
- [ ] Are error conditions handled gracefully?

#### 2. Code Quality
- [ ] Is the code readable and well-organized?
- [ ] Are variable/function names descriptive?
- [ ] Is there unnecessary complexity that could be simplified?
- [ ] Are there any code smells (duplication, long functions, etc.)?

#### 3. Consistency
- [ ] Does the code follow existing patterns in the codebase?
- [ ] Are naming conventions consistent with surrounding code?
- [ ] Is the style consistent (imports, formatting, etc.)?

#### 4. Testing
- [ ] Are there tests for new functionality?
- [ ] Do existing tests still pass?
- [ ] Are edge cases tested?

#### 5. Documentation
- [ ] Are complex logic sections commented?
- [ ] Are public APIs documented (if applicable)?
- [ ] Are commit messages clear and descriptive?

#### 6. Security & Performance
- [ ] Are there any obvious security issues?
- [ ] Are there any performance concerns (N+1 queries, unnecessary loops)?
- [ ] Are resources properly cleaned up?

### How to Self-Review

```bash
# 1. View all your changes
git diff origin/master...HEAD

# 2. Review file by file
git diff origin/master...HEAD -- <file>

# 3. Check what files changed
git diff origin/master...HEAD --stat
```

**Ask yourself for each change:**
- "If I were reviewing this PR from a colleague, would I approve it?"
- "Is there anything I'd ask them to change?"
- "Will this be easy to maintain 6 months from now?"

### Write Clear Commit Messages

After self-review, ensure your final commit message is thorough:

```
<type>(scope): <description>

<Body explaining what changed and why>

- <Specific change 1>
- <Specific change 2>
- <Specific change 3>

Closes #<issue-number>

Co-Authored-By: Claude <noreply@anthropic.com>
```

### Fix Issues Before Merging

If during self-review you find issues:

1. **Fix them before merging** - Don't merge code you wouldn't approve
2. **Document trade-offs** - Add comments in code or commit message explaining decisions
3. **Note concerns** - If something feels off, ask the user before proceeding

### Create Follow-Up Issues

During development and self-review, you will often discover related work that is **out of scope** for the current issue. **Do not expand scope** - instead, create new GitHub issues for follow-up work.

**When to create follow-up issues:**
- You notice a bug unrelated to your current work
- You see an opportunity for refactoring that would bloat your changes
- You discover missing tests in code you didn't write
- You find documentation that needs updating
- You identify a feature enhancement while implementing something else

**How to create follow-up issues:**

```
Use: mcp__github__issue_write with method="create"
- owner: VledicFranco
- repo: constellation-engine
- title: "[P<N>] <Short description>"
- body: |
    ## Summary
    <What needs to be done>

    ## Context
    Discovered while working on #<current-issue-number>.

    ## Acceptance Criteria
    - [ ] <Specific criteria>
- labels: ["<appropriate-label>"]
```

**Priority guidelines for new issues:**
- `P0` - Critical bugs, blocking issues
- `P1` - Important features, significant bugs
- `P2` - Medium priority enhancements
- `P3` - Nice-to-have, low priority

**After creating follow-up issues:**
1. Reference them in your commit message or issue comment
2. Do NOT add them to TODO.md (they're not part of current sprint until triaged)

**Example commit message section:**
```
feat(parser): add support for optional parameters

Implements optional parameter syntax using `?` suffix.

Follow-up issues created:
- #42 - [P2] Refactor duplicate validation logic
- #43 - [P3] Add missing edge case tests for parser

Closes #15
```

---

## Quick Start Context

**What is Constellation Engine?**

A type-safe pipeline orchestration framework for Scala 3. Users:
1. Define processing modules using the `ModuleBuilder` API
2. Compose pipelines using `constellation-lang` DSL (`.cst` files)
3. Execute pipelines with automatic dependency resolution
4. Expose pipelines via HTTP API

**Tech Stack:** Scala 3.3.1 | Cats Effect 3 | http4s | Circe | cats-parse

**Issue Triage - Where to Look:**

| Issue Type | Primary Module(s) | Key Files |
|------------|-------------------|-----------|
| Type errors, CValue/CType bugs | `core` | `TypeSystem.scala` |
| Module execution, ModuleBuilder | `runtime` | `ModuleBuilder.scala`, `Runtime.scala` |
| Parse errors, syntax issues | `lang-parser` | `ConstellationParser.scala` |
| Type checking, semantic errors | `lang-compiler` | `TypeChecker.scala`, `SemanticType.scala` |
| DAG compilation issues | `lang-compiler` | `DagCompiler.scala`, `IRGenerator.scala` |
| Standard library functions | `lang-stdlib` | `StdLib.scala` |
| LSP, autocomplete, diagnostics | `lang-lsp` | `ConstellationLanguageServer.scala` |
| HTTP endpoints, WebSocket | `http-api` | `ConstellationServer.scala`, `ConstellationRoutes.scala` |
| Example modules (text, data) | `example-app` | `modules/TextModules.scala`, `modules/DataModules.scala` |
| VSCode extension | `vscode-extension/` | `src/extension.ts`, `src/panels/*.ts` |

**Quick Exploration Commands:**

```bash
# Find all Scala files in a module
ls modules/<module-name>/src/main/scala/io/constellation/

# Search for a class/function definition
grep -r "def functionName\|class ClassName\|object ObjectName" modules/

# Find usages of a type
grep -r "CType\|CValue\|Module.Uninitialized" modules/

# Run module-specific tests
make test-core       # Core type system
make test-compiler   # Parser + compiler
make test-lsp        # Language server
```

**Constellation-Lang Syntax (for context):**

```constellation
# Input declarations
in text: String
in count: Int

# Module calls (PascalCase, must match ModuleBuilder name)
cleaned = Trim(text)
result = Uppercase(cleaned)

# Output declarations
out result
```

**Common Patterns You'll See:**

```scala
// Module definition pattern
case class MyInput(text: String)
case class MyOutput(result: String)

val myModule = ModuleBuilder
  .metadata("MyModule", "Description", 1, 0)
  .implementationPure[MyInput, MyOutput] { input =>
    MyOutput(input.text.toUpperCase)
  }
  .build

// IO-based module (for side effects)
.implementation[Input, Output] { input => IO { ... } }

// Registering modules
modules.traverse(constellation.setModule)  // requires cats.implicits._
```

**References:**
- Full architecture details: `docs/architecture.md`
- Language documentation: `docs/constellation-lang/`
- Issue tracking: GitHub Issues
