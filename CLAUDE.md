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

## Server Endpoints

- HTTP API: `http://localhost:8080`
- LSP WebSocket: `ws://localhost:8080/lsp`
- Health check: `GET /health`

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

1. **Don't use `ConstellationImpl.create`** - Use `ConstellationImpl.init`
2. **Don't forget `cats.implicits._`** - Required for `.traverse`
3. **Don't mismatch field names** - Case class fields must match constellation-lang variable names
4. **Don't skip testing** - Run `make test` before committing

## Documentation

- Full architecture: See `llm.md`
- Contributing guide: See `CONTRIBUTING.md`
- TODO/roadmap: See `TODO.md`

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

### GitHub Flow (Required Steps)

**Before starting work:**
```bash
# 1. Verify worktree (see above)
# 2. Fetch latest changes
git fetch origin

# 3. Ensure your worktree is based on latest master
git rebase origin/master

# 4. Create feature branch (if not already on one)
git checkout -b agent-<N>/issue-<NUMBER>-<description>
```

**While working:**
- Make small, focused commits
- Run `make test` before each commit
- Commit frequently (at least after each logical change)

**Before creating PR:**
```bash
# 1. Run full test suite
make test

# 2. Ensure branch is up to date
git fetch origin
git rebase origin/master

# 3. If rebase had conflicts, re-run tests
make test

# 4. Push to remote
git push -u origin agent-<N>/issue-<NUMBER>-<description>
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

### Pull Request Format

**Title:** `[Issue #<NUMBER>] <Short description>`

**Body template:**
```markdown
## Summary
<1-3 bullet points describing what changed>

## Changes
- <List of specific changes>

## Testing
- [ ] `make test` passes
- [ ] Manual testing performed (describe if applicable)
- [ ] No merge conflicts with master

## Issue
Closes #<NUMBER>

---
Generated by Claude Agent <N>
```

### Pre-PR Checklist (MANDATORY)

**DO NOT create a PR until ALL of these pass:**

1. [ ] Verified working in a worktree (not main repo)
2. [ ] Branch follows naming convention: `agent-<N>/issue-<NUMBER>-<desc>`
3. [ ] `make compile` succeeds
4. [ ] `make test` passes (ALL tests, not just new ones)
5. [ ] Rebased on latest `origin/master`
6. [ ] No merge conflicts
7. [ ] Commits follow conventional format
8. [ ] PR references the issue number with `Closes #<N>`

### Coordination Rules

**Issue Assignment:**
- Only work on issues explicitly assigned to you
- Use GitHub MCP to check issue assignments: `mcp__github__issue_read`
- If no issue is assigned, ask the user which issue to work on
- One agent = one issue at a time

**File Conflict Prevention:**
- Before editing a file, check if other PRs touch the same file:
  ```bash
  # List open PRs
  gh pr list --state open

  # Check files in a specific PR
  gh pr diff <PR-NUMBER> --name-only
  ```
- If another PR modifies the same file, coordinate with user before proceeding
- Prefer smaller, focused changes over large multi-file refactors

**Communication:**
- Add comments on GitHub issues to indicate you're starting work
- Update issue with progress if work takes multiple sessions
- If blocked, comment on the issue explaining the blocker

### Forbidden Actions

**NEVER do these:**

| Action | Why |
|--------|-----|
| Push directly to `master` | Bypasses review, breaks other agents |
| Work without an issue number | Cannot track work, causes coordination chaos |
| Force push (`git push -f`) | Destroys history, breaks other agents' rebases |
| Work in main repo directory | Conflicts with other agents |
| Create PR with failing tests | Wastes reviewer time, blocks merges |
| Modify files outside issue scope | Causes unexpected conflicts |
| Rebase/merge other agents' branches | Only the owning agent manages their branch |
| Close issues without PR merge | Issue should close automatically via PR |

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

### Agent Identification

When starting a session, identify yourself by including in your first message:
```
Working as Agent <N> in worktree: <directory-name>
Branch: <current-branch>
Assigned Issue: #<number> - <title>
```

This helps the user track which agent is doing what.
