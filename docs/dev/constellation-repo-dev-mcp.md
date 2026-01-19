# MCP Server for Multi-Agent Development

The Constellation Engine MCP (Model Context Protocol) server provides tools for multi-agent development workflows, enabling Claude agents to build, test, and coordinate work on the codebase.

## Overview

The MCP server exposes two categories of tools:

1. **Build & Test Tools** - Run tests, check compilation status, and run affected tests
2. **Session Management Tools** - Manage agent context, worktrees, work queues, and session handoffs

## Installation

### Prerequisites

- Node.js 18+
- npm

### Build

```bash
# From repository root
make mcp-install   # Install dependencies
make mcp-build     # Build TypeScript
make mcp-test      # Run tests
```

Or directly:

```bash
cd constellation-repo-dev-mcp
npm install
npm run build
npm test
```

## Configuration

### Claude Desktop

Add to your Claude Desktop configuration file:

**Windows** (`%APPDATA%\Claude\claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "constellation-engine": {
      "command": "node",
      "args": ["C:/Users/YOUR_USERNAME/Repositories/constellation-engine/constellation-repo-dev-mcp/dist/index.js"],
      "cwd": "C:/Users/YOUR_USERNAME/Repositories/constellation-engine"
    }
  }
}
```

**macOS/Linux** (`~/.config/claude/claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "constellation-engine": {
      "command": "node",
      "args": ["/path/to/constellation-engine/constellation-repo-dev-mcp/dist/index.js"],
      "cwd": "/path/to/constellation-engine"
    }
  }
}
```

### Claude Code

The MCP server can also be used with Claude Code by adding it to your project's MCP configuration.

## Build & Test Tools

### `constellation_run_tests`

Run tests for all modules or a specific one.

**Parameters:**
| Name | Type | Default | Description |
|------|------|---------|-------------|
| `module` | string | `"all"` | Module to test: `all`, `core`, `runtime`, `parser`, `compiler`, `lsp`, `http`, `stdlib` |
| `fastMode` | boolean | `false` | Skip recompilation (uses `sbt testQuick`) |

**Returns:**
```typescript
{
  success: boolean,
  exitCode: number,
  stdout: string,
  stderr: string,
  duration: number,        // milliseconds
  summary: {
    passed: number,
    failed: number,
    skipped: number
  },
  failedTests?: Array<{
    name: string,
    suite: string,
    message: string
  }>
}
```

**Example Usage:**
```
Run tests for the parser module
→ constellation_run_tests({ module: "parser" })

Quick test run (skip recompilation)
→ constellation_run_tests({ module: "all", fastMode: true })
```

### `constellation_get_test_status`

Get cached results from the most recent test run without re-executing tests.

**Parameters:**
| Name | Type | Default | Description |
|------|------|---------|-------------|
| `module` | string | `"all"` | Module to check status for |

**Returns:**
```typescript
{
  available: boolean,
  lastRunTimestamp?: string,  // ISO 8601
  summary?: { passed: number, failed: number, skipped: number },
  failedTests?: Array<{ name: string, suite: string, message: string }>
}
```

### `constellation_get_build_status`

Check if the project compiles. Returns compilation errors and warnings.

**Parameters:**
| Name | Type | Default | Description |
|------|------|---------|-------------|
| `module` | string | `"all"` | Module to compile |

**Returns:**
```typescript
{
  success: boolean,
  exitCode: number,
  errors: Array<{
    file: string,
    line: number,
    column: number,
    message: string,
    severity: "error" | "warning"
  }>,
  warnings: number,
  duration: number  // milliseconds
}
```

### `constellation_run_affected_tests`

Run tests only for modules affected by changed files compared to a base branch. Uses the module dependency graph to include downstream dependents.

**Parameters:**
| Name | Type | Default | Description |
|------|------|---------|-------------|
| `baseBranch` | string | `"master"` | Branch to compare against |

**Returns:**
```typescript
{
  affectedModules: string[],
  success: boolean,
  results: Array<{
    module: string,
    result: TestResult
  }>
}
```

## Session Management Tools

### `constellation_get_agent_context`

Get the current agent's working context including worktree info, branch details, and repository status.

**Parameters:** None

**Returns:**
```typescript
{
  agentNumber: number | null,
  worktree: {
    path: string,
    isValid: boolean,
    gitDir: string
  },
  branch: {
    name: string,
    isFeatureBranch: boolean,
    issueNumber: number | null,
    aheadBehind: { ahead: number, behind: number }
  },
  repoStatus: {
    hasUncommittedChanges: boolean,
    stagedFiles: string[],
    modifiedFiles: string[],
    untrackedFiles: string[]
  }
}
```

### `constellation_verify_worktree`

Verify the current directory is a valid worktree (not the main repository). Returns a recommendation if in the wrong location.

**Parameters:** None

**Returns:**
```typescript
{
  isValidWorktree: boolean,
  isMainRepo: boolean,
  currentPath: string,
  recommendation: string | null,
  availableWorktrees: Array<{
    path: string,
    branch: string,
    commit: string
  }>
}
```

### `constellation_resume_session`

Get all information needed to resume work on an agent's session.

**Parameters:**
| Name | Type | Default | Description |
|------|------|---------|-------------|
| `agentNumber` | number | auto-detect | Agent number (1-10) |

**Returns:**
```typescript
{
  agentNumber: number,
  currentBranch: string,
  issueNumber: number | null,
  lastCommits: Array<{
    sha: string,
    message: string,
    date: string
  }>,
  uncommittedChanges: {
    staged: string[],
    unstaged: string[],
    untracked: string[]
  },
  rebaseStatus: {
    needsRebase: boolean,
    commitsAhead: number,
    commitsBehind: number
  },
  changesSinceBranch: {
    filesChanged: number,
    insertions: number,
    deletions: number
  },
  openPR: {
    number: number,
    title: string,
    url: string
  } | null
}
```

### `constellation_read_queue`

Read an agent's work queue from their `QUEUE.md` file.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `agentNumber` | number | Yes | Agent number (1-10) |

**Returns:**
```typescript
{
  track: string,          // e.g., "Compiler & Language Features"
  focus: string,          // e.g., "Parser, Type System, Core Language"
  assignedIssues: Array<{
    priority: number,     // 1-3
    issueNumber: number,
    title: string,
    status: string,       // "Not Started", "In Progress", etc.
    branch?: string
  }>,
  completedIssues: Array<{
    issueNumber: number,
    title: string,
    pr?: number
  }>,
  notes: string,
  dependencies: string
}
```

### `constellation_handoff_session`

Record handoff notes for session continuity. Creates a markdown file with summary, next steps, and blockers.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `agentNumber` | number | Yes | Agent number (1-10) |
| `issueNumber` | number | Yes | Issue being worked on |
| `status` | string | Yes | `"in_progress"`, `"blocked"`, or `"ready_for_review"` |
| `summary` | string | Yes | Current state summary |
| `nextSteps` | string[] | Yes | Array of next steps |
| `blockers` | string[] | No | Array of blockers |

**Returns:**
```typescript
{
  saved: boolean,
  handoffPath: string,
  timestamp: string  // ISO 8601
}
```

## Module Dependency Graph

The `constellation_run_affected_tests` tool uses this dependency graph to determine which modules need testing when files change:

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

**Dependency propagation example:**
- Changes to `core` trigger tests for all dependent modules
- Changes to `lang-parser` trigger tests for `lang-compiler`, `lang-stdlib`, `lang-lsp`, `http-api`, and `example-app`
- Changes to `lang-lsp` trigger tests only for `http-api` and `example-app`

## Development

### Watch Mode

```bash
cd constellation-repo-dev-mcp
npm run dev  # Watches for changes and recompiles
```

### Running Tests

```bash
npm test
```

### Cleaning Build

```bash
npm run clean
# or
make mcp-clean
```

## Workflow Examples

### Agent Startup

When an agent starts a session, it should:

1. Call `constellation_verify_worktree` to ensure it's in a valid worktree
2. Call `constellation_read_queue` to get assigned issues
3. Call `constellation_resume_session` to understand current state

### Before Creating a PR

1. Call `constellation_get_build_status` to verify compilation
2. Call `constellation_run_tests` to ensure all tests pass
3. Call `constellation_run_affected_tests` to run targeted tests for changed modules

### Session Handoff

When ending a session without completing work:

1. Call `constellation_handoff_session` with current progress
2. The handoff file is saved to `agents/agent-N/handoffs/`

## Troubleshooting

### "Unknown tool" Error

Ensure the MCP server is properly built:
```bash
make mcp-build
```

### Tests Timing Out

The default timeout for tests is 10 minutes. For large test suites, this should be sufficient. If tests are taking longer, check for infinite loops or blocking operations.

### Cache Issues

Test results are cached in `.mcp-cache/test-results.json`. To clear the cache:
```bash
rm -rf .mcp-cache
```
