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
| Compile everything | `make compile` |
| Clean build | `make clean` |

**Windows (if make unavailable):**
```powershell
.\scripts\dev.ps1              # Full dev environment
.\scripts\dev.ps1 -ServerOnly  # Server only
```

**Why:** Raw `sbt` commands bypass project-specific configurations and are harder to maintain.

## MCP Server Tools (PREFERRED)

**When the Constellation MCP server is available, ALWAYS prefer MCP tools over direct command execution.**

The MCP server provides structured output, result caching, and better integration for multi-agent workflows. Use these tools instead of running commands directly:

| Task | MCP Tool | Fallback Command |
|------|----------|------------------|
| Run all tests | `constellation_run_tests` | `make test` |
| Run module tests | `constellation_run_tests({ module: "parser" })` | `make test-parser` |
| Check compilation | `constellation_get_build_status` | `make compile` |
| Get cached test results | `constellation_get_test_status` | (no equivalent) |
| Run affected tests only | `constellation_run_affected_tests` | (no equivalent) |
| Verify worktree | `constellation_verify_worktree` | `git rev-parse --git-dir` |
| Get agent context | `constellation_get_agent_context` | (manual git commands) |
| Resume session info | `constellation_resume_session` | (manual git commands) |
| Read work queue | `constellation_read_queue({ agentNumber: N })` | `cat agents/agent-N/QUEUE.md` |
| Record session handoff | `constellation_handoff_session` | (no equivalent) |

**Benefits of MCP tools:**
- **Structured output**: Parsed test results with pass/fail counts and failure details
- **Caching**: `constellation_get_test_status` returns cached results without re-running
- **Smart testing**: `constellation_run_affected_tests` only tests modules affected by your changes
- **Session management**: Tools for resuming work, reading queues, and handoff notes
- **Consistency**: Same interface regardless of platform (Windows/macOS/Linux)

**When to fall back to direct commands:**
- MCP server is not configured or unavailable
- You need interactive output (watching logs in real-time)
- Running the development server (`make dev`, `make server`)

**Documentation:** See `docs/dev/constellation-repo-dev-mcp.md` for complete tool reference.

## Server Endpoints

- HTTP API: `http://localhost:{port}` (default: 8080)
- LSP WebSocket: `ws://localhost:{port}/lsp`
- Health check: `GET /health`

**Port Configuration:**
The server port is configurable via the `CONSTELLATION_PORT` environment variable.
For multi-agent setups, each agent uses a unique port based on their agent number:

| Agent | Port | Environment Variable |
|-------|------|---------------------|
| Main repo | 8080 | (default) |
| Agent 1 | 8081 | `CONSTELLATION_PORT=8081` |
| Agent 2 | 8082 | `CONSTELLATION_PORT=8082` |
| Agent N | 8080+N | `CONSTELLATION_PORT=808N` |

The `scripts/dev.ps1` script auto-detects the agent number from the directory name.

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
| LSP Server | `modules/lang-lsp/.../ConstellationLanguageServer.scala` |

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

- Full architecture: See `llm.md`
- Contributing guide: See `CONTRIBUTING.md`
- **Issue tracking: Use GitHub Issues** (agents track work via issues)
- Language documentation: See `docs/constellation-lang/`

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
2. **Working directory check**: Ensures no uncommitted changes (excludes `agents/`)
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

## Multi-Agent Workflow (CRITICAL)

This repository uses multiple Claude agents working in parallel. **Follow these rules strictly to avoid conflicts.**

### Worktree Requirement

**NEVER work directly in the main repository clone.** Always work in a git worktree.

**Before starting any work, verify you are in a worktree:**
```bash
git rev-parse --git-dir
```
- If output is `.git` → You are in the main repo. **STOP. Do not proceed.**
- If output contains `worktrees/` → You are in a worktree. Safe to proceed.

**If no worktree exists, create one:**
```bash
# From main repo directory
git worktree add ../constellation-agent-<N> -b agent-<N>/work master
```

### Branch Naming Convention

**ALWAYS use this format:**
```
agent-<N>/issue-<NUMBER>-<short-description>
```

Examples:
- `agent-1/issue-42-add-validation-module`
- `agent-2/issue-15-fix-parser-error`
- `agent-3/issue-78-update-docs`

**Rules:**
- `<N>` = Your agent number (ask user if unknown)
- `<NUMBER>` = GitHub issue number you're working on
- `<short-description>` = Lowercase, hyphen-separated, max 5 words
- **NEVER work without an assigned issue number**

### Git Workflow (Required Steps)

**Before starting work:**
```bash
# 1. Verify worktree (see above)

# 2. Sync with latest master (CRITICAL for multi-agent coordination)
git fetch origin
git checkout master
git pull origin master

# 3. Create feature branch from updated master
git checkout -b agent-<N>/issue-<NUMBER>-<description>
```

**While working:**
- Make small, focused commits
- Run `make test` before each commit
- Commit frequently (at least after each logical change)

**Before merging to master:**
```bash
# 1. Run full test suite
make test

# 2. Sync with latest master (CRITICAL - other agents may have merged)
git fetch origin
git rebase origin/master

# 3. If rebase had conflicts, re-run tests
make test

# 4. Pull master again and merge (ensures no race condition with other agents)
git checkout master
git pull origin master
git merge agent-<N>/issue-<NUMBER>-<description>
git push origin master

# 5. Clean up feature branch
git branch -d agent-<N>/issue-<NUMBER>-<description>
```

### Commit Message Format

**Use Conventional Commits:**
```
<type>(scope): <description>

[optional body]

Closes #<issue-number>
```

**Types:**
- `feat` - New feature
- `fix` - Bug fix
- `refactor` - Code change that neither fixes a bug nor adds a feature
- `test` - Adding or updating tests
- `docs` - Documentation only
- `chore` - Build process, dependencies, etc.

**Example:**
```
feat(parser): add support for optional parameters

Implements optional parameter syntax using `?` suffix.
Updates grammar and adds validation in semantic analysis.

Closes #42
```

### Pre-Merge Checklist (MANDATORY)

**DO NOT merge to master until ALL of these pass:**

1. [ ] Verified working in a worktree (use `constellation_verify_worktree` or `git rev-parse --git-dir`)
2. [ ] Branch follows naming convention: `agent-<N>/issue-<NUMBER>-<desc>`
3. [ ] Compilation succeeds (use `constellation_get_build_status` or `make compile`)
4. [ ] ALL tests pass (use `constellation_run_tests` or `make test`)
5. [ ] Rebased on latest `origin/master`
6. [ ] No merge conflicts (resolve any conflicts, then re-run tests)
7. [ ] Commits follow conventional format
8. [ ] Final commit message references the issue number with `Closes #<N>`

**Prefer MCP tools** for steps 1, 3, and 4 when available - they provide structured output and caching.

### Coordination Rules

**Issue Assignment:**
- **Check your queue first:** Read `agents/agent-<N>/QUEUE.md` for your assigned issues
- Work on issues in priority order from your queue
- Use GitHub MCP to verify issue is still open: `mcp__github__issue_read`
- If your queue is empty, ask the user for new assignments
- One agent = one issue at a time (complete current before starting next)

**Conflict Prevention:**
- Prefer smaller, focused changes over large multi-file refactors
- If you encounter merge conflicts when rebasing:
  1. Resolve the conflicts carefully
  2. Re-run ALL tests after resolution
  3. Only proceed to merge if tests pass
- Coordinate with user if conflicts are complex or involve another agent's recent work

**Communication:**
- Add comments on GitHub issues to indicate you're starting work
- Update issue with progress if work takes multiple sessions
- If blocked, comment on the issue explaining the blocker

### Forbidden Actions

**NEVER do these:**

| Action | Why |
|--------|-----|
| Merge to `master` with failing tests | Breaks the build for all agents |
| Work without an issue number | Cannot track work, causes coordination chaos |
| Force push to `master` (`git push -f`) | Destroys history, breaks other agents' rebases |
| Work in main repo directory | Conflicts with other agents |
| Modify files outside issue scope | Causes unexpected conflicts |
| Rebase/merge other agents' branches | Only the owning agent manages their branch |
| Merge without running tests after rebase | Conflicts may have broken the code |

### Recovery Procedures

**If you accidentally worked in main repo:**
```bash
# 1. Stash your changes
git stash

# 2. Create a worktree
git worktree add ../constellation-agent-<N> -b agent-<N>/recovery master

# 3. Switch to worktree and apply changes
cd ../constellation-agent-<N>
git stash pop
```

**If your branch has conflicts with master:**
```bash
# 1. Fetch latest
git fetch origin

# 2. Rebase (resolve conflicts as they appear)
git rebase origin/master

# 3. If conflicts are complex, ask user for guidance
# 4. After resolving, run tests
make test

# 5. Force push ONLY your own branch (never master)
git push --force-with-lease
```

**If tests fail after rebase:**
- Do NOT create a PR
- Fix the failures first
- If failures are in code you didn't write, check if another PR was merged
- Ask user for help if the failure is unrelated to your changes

### Session Resume Protocol

When resuming work on an existing branch (e.g., after a session crash or continuation):

**Preferred: Use MCP tool (gets all info in one call)**
```
constellation_resume_session({ agentNumber: N })
```

This returns: current branch, last commits, uncommitted changes, and rebase status.

**Fallback: Manual git commands**
```bash
# 1. Check if your branch exists locally and remotely
git branch -a | grep agent-<N>

# 2. Checkout your existing branch
git checkout agent-<N>/issue-<NUMBER>-<description>

# 3. Check last commits to understand where you left off
git log -5 --oneline

# 4. Check for uncommitted changes
git status

# 5. Review what was done since branching from master
git diff origin/master...HEAD --stat

# 6. Fetch latest and check if rebase is needed
git fetch origin
git log HEAD..origin/master --oneline  # If output, rebase needed

# 7. If rebase needed:
git rebase origin/master
make test
```

**Session handoff notes:**
- Check the GitHub issue for any comments you left about progress
- Review your QUEUE.md for status notes (or use `constellation_read_queue`)
- Verify build with `constellation_get_build_status` or `make compile`

### Handling Merge Conflicts

When you encounter merge conflicts during rebase:

```bash
# 1. Git will pause during rebase showing conflicted files
git status  # See which files have conflicts

# 2. Open each conflicted file and resolve manually
# Look for <<<<<<< HEAD, =======, and >>>>>>> markers

# 3. After resolving each file, stage it
git add <resolved-file>

# 4. Continue the rebase
git rebase --continue

# 5. CRITICAL: Re-run ALL tests after resolving conflicts
make test

# 6. Only merge to master if tests pass
```

If conflicts are complex or involve code you don't understand, ask the user for guidance.

### Agent Identification

When starting a session, identify yourself by including in your first message:
```
Working as Agent <N> in worktree: <directory-name>
Branch: <current-branch>
Assigned Issue: #<number> - <title>
```

This helps the user track which agent is doing what.

### Running Test Servers (Multi-Agent)

Each agent can run their own server instance for testing without port conflicts.

**Automatic port detection:**
```powershell
# In your worktree (e.g., constellation-agent-2), simply run:
.\scripts\dev.ps1 -ServerOnly

# The script auto-detects agent number from directory name:
# constellation-agent-1 → port 8081
# constellation-agent-2 → port 8082
# etc.
```

**Manual port override:**
```powershell
# Explicit port
.\scripts\dev.ps1 -ServerOnly -Port 8085

# Or via environment variable
$env:CONSTELLATION_PORT = 8085
.\scripts\dev.ps1 -ServerOnly
```

**Accessing your agent's server:**
```bash
# Replace {port} with your agent's port
curl http://localhost:{port}/health
curl http://localhost:{port}/modules
```

**VSCode Extension:**
When testing the VSCode extension, update the LSP port in `vscode-extension/.vscode/settings.json` or configure it per-agent.

---

## Agent Startup Checklist (CRITICAL)

**Every agent MUST complete these steps before writing any code.**

### Step 0: Start From Main Repository (IMPORTANT)

**Agents MUST be launched from the main `constellation-engine` directory**, not from a worktree.

**Why:** The `agents/` directory containing queue files is only updated in the main repository. Worktrees have their own working state and do not receive updates to queue files. Starting from the main repo ensures you read the latest queue assignments.

```bash
# You should be in the main repo directory:
# C:/Users/.../constellation-engine (or equivalent)
# NOT in constellation-agent-<N>
```

### Step 1: Read Your Work Queue (From Main Repo)

**Read your assigned issues from your queue file while still in the main repo:**

```
# Preferred: Use MCP tool
constellation_read_queue({ agentNumber: N })

# Fallback: Direct file read
cat agents/agent-<N>/QUEUE.md
```

**Your queue contains:**
- Prioritized list of assigned issues
- Current status of each issue
- Dependencies and notes
- Pick the highest priority issue that is not "In Progress" by another session

### Step 2: Change to Your Worktree

**After reading your queue, change to your worktree directory:**

```bash
# Change to your worktree
cd ../constellation-agent-<N>

# Verify you're now in the worktree
git rev-parse --git-dir
# Should contain "worktrees/" - confirms you're in the worktree

# Check current branch
git branch --show-current

# Fetch latest from remote
git fetch origin
```

### Step 3: Verify Issue Status on GitHub

**BEFORE starting work, verify the issue is still open:**

```
Use: mcp__github__issue_read with method="get"
- owner: VledicFranco
- repo: constellation-engine
- issue_number: <your-issue>
```

**Check for:**
- `state: "OPEN"` - If closed, pick a different issue
- `assignees` - If assigned to someone else, pick a different issue
- Read the issue body for full requirements

### Step 4: Check Recent Commits on Master

```bash
# See what was recently merged to master
git fetch origin
git log origin/master --oneline -10
```

**Look for:**
- Recent commits that might affect your issue
- Commits that touch the same files you'll be modifying

### Step 5: Claim the Issue

1. **Comment on GitHub issue** to claim it:
   ```
   Use: mcp__github__add_issue_comment
   Body: "Starting work on this issue. Branch: agent-<N>/issue-<NUMBER>-<desc>"
   ```

### Step 6: Create Feature Branch

```bash
# Ensure on latest master
git checkout master
git pull origin master

# Create feature branch
git checkout -b agent-<N>/issue-<NUMBER>-<short-description>
```

### Step 7: Verify Build Works

```
# Preferred: Use MCP tools
constellation_get_build_status()  # Check compilation
constellation_run_tests()          # Run all tests

# Fallback: Direct commands
make compile
make test
```

**Only proceed if both succeed.**

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

Co-Authored-By: Claude <agent-N>
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

    ---
    Created by Agent <N>
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

## Complete Agent Workflow Summary

```
┌─────────────────────────────────────────────────────────────┐
│                    AGENT STARTUP                             │
├─────────────────────────────────────────────────────────────┤
│ 0. Start from main repo (constellation-engine directory)    │
│ 1. Read your queue (agents/agent-<N>/QUEUE.md)              │
│ 2. Change to worktree (cd ../constellation-agent-<N>)       │
│ 3. Verify issue is OPEN on GitHub (mcp__github__issue_read) │
│ 4. Check recent commits on master                            │
│ 5. Claim issue (comment on GitHub issue)                    │
│ 6. git pull origin master ← SYNC before branch!             │
│ 7. Create feature branch from master                         │
│ 8. Verify build works (make compile && make test)           │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    DEVELOPMENT                               │
├─────────────────────────────────────────────────────────────┤
│ • Make small, focused commits                                │
│ • Run tests frequently                                       │
│ • Stay within issue scope                                    │
│ • Comment on issue if blocked                                │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    PRE-MERGE CHECKLIST                       │
├─────────────────────────────────────────────────────────────┤
│ 1. make compile succeeds                                     │
│ 2. make test passes                                          │
│ 3. git fetch origin && git rebase origin/master             │
│ 4. Resolve any merge conflicts                               │
│ 5. Re-run tests after rebase                                 │
│ 6. Self-review all changes (see checklist above)            │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    MERGE TO MASTER                           │
├─────────────────────────────────────────────────────────────┤
│ 1. git checkout master                                       │
│ 2. git pull origin master  ← SYNC before merge!             │
│ 3. git merge agent-<N>/issue-<NUMBER>-<desc>                │
│ 4. git push origin master                                    │
│ 5. Ensure final commit has "Closes #<N>"                    │
│ 6. Delete feature branch locally                             │
└─────────────────────────────────────────────────────────────┘
```

### Quick Start Context

**What is Constellation Engine?**

A type-safe, composable ML orchestration framework for Scala 3. Users:
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

**Verify Setup Before Coding:** See [Agent Startup Checklist](#agent-startup-checklist-critical) above.

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
- Full architecture details: `llm.md`
- Language documentation: `docs/constellation-lang/`
- Issue tracking: GitHub Issues
