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

1. **Don't forget `cats.implicits._`** - Required for `.traverse`
2. **Don't mismatch field names** - Case class fields must match constellation-lang variable names
3. **Don't skip testing** - Run `make test` before committing

## Documentation

- Full architecture: See `llm.md`
- Contributing guide: See `CONTRIBUTING.md`
- **Sprint backlog & issue tracking: See `TODO.md`** (agents update this directly)
- Language documentation: See `docs/constellation-lang/`

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

---

## Agent Startup Checklist (CRITICAL)

**Every agent MUST complete these steps before writing any code.**

### Step 1: Verify Environment

```bash
# 1. Check you're in a worktree (NOT main repo)
git rev-parse --git-dir
# Must contain "worktrees/" - if it shows just ".git", STOP

# 2. Check current branch
git branch --show-current

# 3. Fetch latest from remote
git fetch origin
```

### Step 2: Check Issue Status on GitHub

**BEFORE starting work, verify the issue is still open and unassigned:**

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

### Step 3: Check for Existing PRs

```
Use: mcp__github__list_pull_requests
- owner: VledicFranco
- repo: constellation-engine
- state: "open"
```

**Look for:**
- PRs already addressing your issue
- PRs that modify the same files you'll touch (coordinate if so)

### Step 4: Claim the Issue

1. **Update TODO.md** - Mark issue as `[~]` in progress with your agent number
2. **Comment on GitHub issue** (optional but recommended):
   ```
   Use: mcp__github__add_issue_comment
   Body: "Starting work on this issue. Branch: agent-<N>/issue-<NUMBER>-<desc>"
   ```

### Step 5: Create Feature Branch

```bash
# Ensure on latest master
git checkout master
git pull origin master

# Create feature branch
git checkout -b agent-<N>/issue-<NUMBER>-<short-description>
```

### Step 6: Verify Build Works

```bash
# Compile to ensure clean starting state
make compile

# Run tests to establish baseline
make test
```

**Only proceed if both commands succeed.**

---

## Self-Review Protocol (MANDATORY)

**Before creating a PR, review your own changes as if you were a senior engineer.**

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
- [ ] Is the PR description clear and complete?

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

### Create Detailed PR Description

After self-review, create a PR with a thorough description:

```markdown
## Summary
<What does this PR do? 1-3 bullet points>

## Changes
- <Specific change 1>
- <Specific change 2>
- <Specific change 3>

## Self-Review Notes
<Any decisions you made, trade-offs, or things you considered>

## Testing
- [ ] `make compile` passes
- [ ] `make test` passes
- [ ] Manual testing: <describe what you tested>

## Checklist
- [ ] Code follows project conventions
- [ ] No unnecessary changes outside issue scope
- [ ] Edge cases handled
- [ ] Tests added/updated as needed

Closes #<issue-number>

---
Generated by Claude Agent <N>
```

### Request Changes on Yourself

If during self-review you find issues:

1. **Fix them before creating the PR** - Don't create a PR you wouldn't approve
2. **Document trade-offs** - If you made a deliberate choice, explain why
3. **Note concerns** - If something feels off but you can't pinpoint it, mention it

### Create Follow-Up Issues

During development and self-review, you will often discover related work that is **out of scope** for the current issue. **Do not expand scope** - instead, create new GitHub issues for follow-up work.

**When to create follow-up issues:**
- You notice a bug unrelated to your current work
- You see an opportunity for refactoring that would bloat the PR
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
1. Reference them in your PR description under a "Follow-up Issues" section
2. Do NOT add them to TODO.md (they're not part of current sprint until triaged)

**Example PR description section:**
```markdown
## Follow-up Issues Created
- #42 - [P2] Refactor duplicate validation logic
- #43 - [P3] Add missing edge case tests for parser
```

---

## Complete Agent Workflow Summary

```
┌─────────────────────────────────────────────────────────────┐
│                    AGENT STARTUP                             │
├─────────────────────────────────────────────────────────────┤
│ 1. Verify worktree (git rev-parse --git-dir)                │
│ 2. Check issue is OPEN on GitHub (mcp__github__issue_read)  │
│ 3. Check no conflicting PRs (mcp__github__list_pull_requests)│
│ 4. Claim issue in TODO.md ([~] status)                      │
│ 5. Create feature branch                                     │
│ 6. Verify build works (make compile && make test)           │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    DEVELOPMENT                               │
├─────────────────────────────────────────────────────────────┤
│ • Make small, focused commits                                │
│ • Run tests frequently                                       │
│ • Stay within issue scope                                    │
│ • Update TODO.md if blocked                                  │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    PRE-PR CHECKLIST                          │
├─────────────────────────────────────────────────────────────┤
│ 1. make compile succeeds                                     │
│ 2. make test passes                                          │
│ 3. git fetch origin && git rebase origin/master             │
│ 4. Re-run tests after rebase                                 │
│ 5. Self-review all changes (see checklist above)            │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    CREATE PR                                 │
├─────────────────────────────────────────────────────────────┤
│ 1. Push branch: git push -u origin <branch>                 │
│ 2. Create PR with detailed description                       │
│ 3. Ensure PR references issue: "Closes #<N>"                │
│ 4. Update TODO.md to [x] after PR merges                    │
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
- TODO/roadmap: `TODO.md`
