# Multi-Agent Work Coordination

This directory contains work queues for parallel Claude agents working on the constellation-engine codebase.

## Agent Assignments

| Agent | Track | Focus Areas |
|-------|-------|-------------|
| Agent 1 | Compiler/Language | Parser, Type System, Core Language |
| Agent 2 | Runtime/Operators | Runtime execution, operators |
| Agent 3 | Integration | LSP, HTTP API, VSCode Extension |
| Agent 4 | Infrastructure | Testing, Documentation, Tooling |

## Current Status

**No active work phases.** All agents are available for new assignments.

## How It Works

1. Create GitHub issues for new work
2. Update the appropriate `agent-N/QUEUE.md` with issue assignments
3. Agents check their queue file on startup
4. Agents work in separate git worktrees to avoid conflicts
5. PRs are merged to master after tests pass

---
*See CLAUDE.md for detailed agent workflow protocols.*
