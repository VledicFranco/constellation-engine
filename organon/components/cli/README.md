# CLI

> **Path**: `organon/components/cli/`
> **Module**: `modules/lang-cli/`

Command-line interface for Constellation Engine.

## Overview

The CLI is an HTTP client for Constellation Engine operations. It communicates with a running Constellation server to compile pipelines, execute workflows, and visualize DAGs. The CLI itself does not contain compilation or execution logic.

## Key Files

| File | Purpose |
|------|---------|
| `Main.scala` | Entry point (IOApp) |
| `CliApp.scala` | Command dispatch, global options, exit codes |
| `Config.scala` | Configuration loading with precedence |
| `HttpClient.scala` | HTTP client wrapper for API calls |
| `Output.scala` | Human/JSON output formatting |
| `StringUtils.scala` | Safe string truncation and error sanitization |
| `models/ApiModels.scala` | Shared API response types |
| `commands/CompileCommand.scala` | Type-check pipeline files |
| `commands/RunCommand.scala` | Execute pipelines with inputs |
| `commands/VizCommand.scala` | Generate DAG visualizations |
| `commands/ServerCommand.scala` | Server operations (health, pipelines, executions) |
| `commands/DeployCommand.scala` | Deployment operations (push, canary, rollback) |
| `commands/ConfigCommand.scala` | Manage CLI configuration |

## Architecture

```
CLI (HTTP Client)
    │
    ▼
┌─────────────────────────────┐
│   Constellation Server      │
│  ┌────────────────────────┐ │
│  │ /compile               │ │  ← compile
│  │ /run                   │ │  ← run
│  │ /pipelines             │ │  ← viz, server ops
│  │ /health                │ │  ← health check
│  └────────────────────────┘ │
└─────────────────────────────┘
```

## Commands

| Command | Purpose | API Endpoint |
|---------|---------|--------------|
| `compile <file>` | Type-check pipeline | `POST /compile` |
| `run <file>` | Execute pipeline | `POST /run` |
| `viz <file>` | Generate DAG visualization | `POST /compile` + `GET /pipelines/<hash>` |
| `server health` | Check server health | `GET /health` |
| `server pipelines` | List loaded pipelines | `GET /pipelines` |
| `server pipelines show <name>` | Show pipeline details | `GET /pipelines/<name>` |
| `server executions list` | List suspended executions | `GET /executions` |
| `server executions show <id>` | Show execution details | `GET /executions/<id>` |
| `server executions delete <id>` | Delete execution | `DELETE /executions/<id>` |
| `server metrics` | Show server metrics | `GET /metrics` |
| `deploy push <file>` | Deploy pipeline | `POST /pipelines/<name>/reload` |
| `deploy canary <file>` | Deploy as canary | `POST /pipelines/<name>/reload` (with canary config) |
| `deploy promote <pipeline>` | Promote canary | `POST /pipelines/<name>/canary/promote` |
| `deploy rollback <pipeline>` | Rollback version | `POST /pipelines/<name>/rollback` |
| `deploy status <pipeline>` | Show canary status | `GET /pipelines/<name>/canary` |
| `config show` | Display configuration | (local) |
| `config get <key>` | Get config value | (local) |
| `config set <key> <value>` | Set config value | (local) |

## Global Options

| Flag | Short | Description |
|------|-------|-------------|
| `--server <url>` | `-s` | Server URL |
| `--token <token>` | `-t` | API token |
| `--json` | `-j` | JSON output |
| `--quiet` | `-q` | Suppress output |
| `--verbose` | `-v` | Debug output |

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | Compilation error |
| 2 | Runtime error |
| 3 | Connection error |
| 4 | Authentication error |
| 5 | Resource not found |
| 6 | Resource conflict |
| 10 | Usage error |

## Configuration

Configuration file: `~/.constellation/config.json`

```json
{
  "server": {
    "url": "http://localhost:8080",
    "token": null
  },
  "defaults": {
    "output": "human",
    "viz_format": "dot"
  }
}
```

**Precedence (highest to lowest):**
1. CLI flags (`--server`, `--token`, `--json`)
2. Environment variables (`CONSTELLATION_SERVER_URL`, `CONSTELLATION_TOKEN`)
3. Config file
4. Built-in defaults

## Dependencies

- **Depends on**: None (HTTP client only)
- **Depended by**: None (end-user tool)
- **Communicates with**: http-api (via HTTP)

## Related Documentation

- [CLI Ethos](./ETHOS.md) - Normative constraints
- [RFC-021](../../../rfcs/rfc-021-cli.md) - Full specification
- [Website Docs](../../../website/docs/tooling/cli.md) - User guide
