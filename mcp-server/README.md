# Constellation Engine MCP Server

Model Context Protocol (MCP) server providing Build/Test and Session Management tools for multi-agent development on Constellation Engine.

## Quick Start

```bash
# Install dependencies
npm install

# Build
npm run build

# Start server
npm start
```

## Tools

### Build & Test Tools

| Tool | Description |
|------|-------------|
| `constellation_run_tests` | Run tests for all modules or a specific one |
| `constellation_get_test_status` | Get cached results from most recent test run |
| `constellation_get_build_status` | Check if project compiles |
| `constellation_run_affected_tests` | Run tests only for modules affected by changed files |

### Session Management Tools

| Tool | Description |
|------|-------------|
| `constellation_get_agent_context` | Get current agent's working context |
| `constellation_verify_worktree` | Verify current directory is a valid worktree |
| `constellation_resume_session` | Get all info needed to resume work on a session |
| `constellation_read_queue` | Read agent's work queue from QUEUE.md |
| `constellation_handoff_session` | Record handoff notes for session continuity |

## Claude Desktop Configuration

Add to your Claude Desktop configuration (`claude_desktop_config.json`):

### Windows

```json
{
  "mcpServers": {
    "constellation-engine": {
      "command": "node",
      "args": ["C:/Users/YOUR_USERNAME/Repositories/constellation-engine/mcp-server/dist/index.js"],
      "cwd": "C:/Users/YOUR_USERNAME/Repositories/constellation-engine"
    }
  }
}
```

### macOS/Linux

```json
{
  "mcpServers": {
    "constellation-engine": {
      "command": "node",
      "args": ["/path/to/constellation-engine/mcp-server/dist/index.js"],
      "cwd": "/path/to/constellation-engine"
    }
  }
}
```

## Development

```bash
# Watch mode for development
npm run dev

# Run tests
npm test

# Clean build
npm run clean
```

## Tool Details

### `constellation_run_tests`

Run tests for all modules or a specific one.

**Parameters:**
- `module` (optional): `all`, `core`, `runtime`, `parser`, `compiler`, `lsp`, `http`, `stdlib`. Default: `all`
- `fastMode` (optional): Skip recompilation. Default: `false`

**Returns:**
```typescript
{
  success: boolean,
  exitCode: number,
  stdout: string,
  stderr: string,
  duration: number,
  summary: { passed: number, failed: number, skipped: number },
  failedTests?: [{ name: string, suite: string, message: string }]
}
```

### `constellation_get_agent_context`

Get current agent's working context.

**Returns:**
```typescript
{
  agentNumber: number | null,
  worktree: { path: string, isValid: boolean, gitDir: string },
  branch: { name: string, isFeatureBranch: boolean, issueNumber: number | null, aheadBehind: { ahead: number, behind: number } },
  repoStatus: { hasUncommittedChanges: boolean, stagedFiles: string[], modifiedFiles: string[], untrackedFiles: string[] }
}
```

### `constellation_verify_worktree`

Verify current directory is a valid worktree (not main repo).

**Returns:**
```typescript
{
  isValidWorktree: boolean,
  isMainRepo: boolean,
  currentPath: string,
  recommendation: string | null,
  availableWorktrees: [{ path: string, branch: string, commit: string }]
}
```

### `constellation_resume_session`

Get all info needed to resume work on an agent's session.

**Parameters:**
- `agentNumber` (optional): Agent 1-10. Auto-detected if not provided.

**Returns:**
```typescript
{
  agentNumber: number,
  currentBranch: string,
  issueNumber: number | null,
  lastCommits: [{ sha: string, message: string, date: string }],
  uncommittedChanges: { staged: string[], unstaged: string[], untracked: string[] },
  rebaseStatus: { needsRebase: boolean, commitsAhead: number, commitsBehind: number },
  changesSinceBranch: { filesChanged: number, insertions: number, deletions: number },
  openPR: { number: number, title: string, url: string } | null
}
```

### `constellation_read_queue`

Read agent's work queue from QUEUE.md.

**Parameters:**
- `agentNumber` (required): Agent 1-10

**Returns:**
```typescript
{
  track: string,
  focus: string,
  assignedIssues: [{ priority: number, issueNumber: number, title: string, status: string, branch?: string }],
  completedIssues: [{ issueNumber: number, title: string, pr?: number }],
  notes: string,
  dependencies: string
}
```

### `constellation_handoff_session`

Record handoff notes for session continuity.

**Parameters:**
- `agentNumber` (required): Agent 1-10
- `issueNumber` (required): Issue being worked on
- `status` (required): `in_progress`, `blocked`, `ready_for_review`
- `summary` (required): Current state summary
- `nextSteps` (required): Array of next steps to complete
- `blockers` (optional): Array of blockers

**Returns:**
```typescript
{
  saved: boolean,
  handoffPath: string,
  timestamp: string
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
```

Changes to `core` will trigger tests for all dependent modules.
