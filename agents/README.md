# Multi-Agent Work Coordination

This directory coordinates work across multiple Claude agents working on Constellation Engine.

## Structure

```
agents/
├── agent-1/QUEUE.md    # Compiler/Language track
├── agent-2/QUEUE.md    # Runtime/Operators track
├── agent-3/QUEUE.md    # Integration (LSP/VSCode) track
├── agent-4/QUEUE.md    # Infrastructure/Testing track
└── archive/            # Completed phase documentation
```

## Agent Tracks

| Agent | Track | Focus Areas |
|-------|-------|-------------|
| Agent 1 | Compiler/Language | Parser, Type System, Core Language |
| Agent 2 | Runtime/Operators | Runtime execution, operators, type system impl |
| Agent 3 | Integration | LSP, HTTP API, VSCode Extension |
| Agent 4 | Infrastructure | Testing, Documentation, Tooling |

## How to Assign Work

1. Create GitHub issues for the work
2. Update the appropriate `agent-N/QUEUE.md` with the issue
3. Agents check their queue file on startup

## Archive

The `archive/` directory contains completed planning documents:
- Phase 1-3 context files (architecture improvements)
- @example annotation orchestration plan
- Project status tracking

---

*See CLAUDE.md for detailed agent workflow protocols.*
