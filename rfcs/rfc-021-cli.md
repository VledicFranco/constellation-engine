# RFC-021: Constellation CLI

**Status:** In Progress
**Priority:** P1 (Developer Experience)
**Author:** Claude
**Created:** 2026-02-08
**Issue:** [#210](https://github.com/VledicFranco/constellation-engine/issues/210)

---

## Summary

Create a unified command-line interface for Constellation Engine that enables developers to compile, run, visualize pipelines, manage deployments, and operate servers from the terminal.

---

## Motivation

Users currently have two ways to interact with Constellation Engine:

1. **Embed the library** — Requires Scala project setup, sbt configuration
2. **Use the HTTP API directly** — Requires crafting HTTP requests, managing auth headers

Neither approach supports quick experimentation, CI/CD integration, or operational scripting. A CLI tool addresses these gaps:

| Use Case | Current Solution | With CLI |
|----------|------------------|----------|
| Quick compile check | Start server, use dashboard | `constellation compile pipeline.cst` |
| CI/CD validation | Custom HTTP scripts | `constellation compile --json` |
| Deploy to production | Manual API calls | `constellation deploy push pipeline.cst` |
| Check server health | curl + jq | `constellation server health` |
| Canary rollout | Complex API orchestration | `constellation deploy canary --percent 10` |

---

## Architecture

The CLI is a **client to the Constellation HTTP API**, not a standalone compiler. All operations go through a running Constellation server which has the module registry, compiler, and runtime.

```
constellation CLI (HTTP/WebSocket client)
        │
        ▼
┌─────────────────────────────┐
│   Constellation Server      │
│  ┌────────────────────────┐ │
│  │ /api/v1/preview        │ │  ← compile, viz
│  │ /api/v1/run            │ │  ← run
│  │ /lsp (WebSocket)       │ │  ← IDE features
│  │ /api/v1/pipelines      │ │  ← deploy, list
│  │ /api/v1/executions     │ │  ← history
│  │ /health                │ │  ← server status
│  └────────────────────────┘ │
└─────────────────────────────┘
```

**Why client-only architecture:**

1. **Module availability** — Compilation requires knowing available modules (signatures, types). Only the server has the module registry.
2. **Consistency** — Same compilation/execution behavior as dashboard and API consumers.
3. **Lightweight CLI** — No need to bundle compiler, runtime, or dependencies. Just an HTTP client.
4. **Unified auth** — Same API keys work for CLI, dashboard, and programmatic access.

---

## Command Structure

```
constellation
│
├── compile <file.cst>               # Compile and type-check
│   ├── --watch                      # Watch mode: recompile on changes
│   └── --json                       # Output errors as JSON
│
├── run <file.cst>                   # Execute pipeline
│   ├── --input <key>=<value>        # Provide input value
│   ├── --input-file <path.json>     # Provide inputs from JSON file
│   └── --json                       # Output result as JSON
│
├── viz <file.cst>                   # Visualize pipeline DAG
│   ├── --format dot                 # Graphviz DOT (default)
│   ├── --format json                # Raw DAG JSON
│   └── --format mermaid             # Mermaid diagram syntax
│
├── server                           # Server operations
│   ├── health                       # Check server health
│   ├── pipelines                    # List loaded pipelines
│   │   └── show <name>              # Show pipeline details
│   ├── executions                   # Execution history
│   │   ├── list                     # List recent executions
│   │   ├── show <id>                # Show execution details
│   │   └── delete <id>              # Delete execution record
│   └── metrics                      # Show Prometheus metrics
│
├── deploy                           # Pipeline deployment
│   ├── push <file.cst>              # Deploy pipeline to server
│   │   └── --name <name>            # Override pipeline name
│   ├── canary <file.cst>            # Deploy as canary
│   │   └── --percent <N>            # Traffic percentage (default: 10)
│   ├── promote <pipeline>           # Promote canary to stable
│   └── rollback <pipeline>          # Rollback to previous version
│
└── config                           # CLI configuration
    ├── set <key> <value>            # Set config value
    ├── get <key>                    # Get config value
    └── show                         # Show all configuration
```

---

## Global Flags

| Flag | Short | Description | Default |
|------|-------|-------------|---------|
| `--server <url>` | `-s` | Constellation server URL | `http://localhost:8080` |
| `--token <token>` | `-t` | API authentication token | From config file |
| `--json` | `-j` | Output as JSON | `false` |
| `--quiet` | `-q` | Suppress non-essential output | `false` |
| `--verbose` | `-v` | Verbose output for debugging | `false` |
| `--help` | `-h` | Show help | |
| `--version` | `-V` | Show version | |

---

## Exit Codes

| Code | Name | Description |
|------|------|-------------|
| 0 | `SUCCESS` | Command completed successfully |
| 1 | `COMPILE_ERROR` | Pipeline compilation failed (syntax or type errors) |
| 2 | `RUNTIME_ERROR` | Pipeline execution failed |
| 3 | `CONNECTION_ERROR` | Cannot connect to server |
| 4 | `AUTH_ERROR` | Authentication failed (invalid or missing token) |
| 5 | `NOT_FOUND` | Resource not found (pipeline, execution, etc.) |
| 10 | `USAGE_ERROR` | Invalid command-line arguments |

---

## Configuration

### Config File Location

```
~/.constellation/config.json
```

### Config Schema

```json
{
  "server": {
    "url": "http://localhost:8080",
    "token": "sk-..."
  },
  "defaults": {
    "output": "human",
    "viz_format": "dot"
  }
}
```

### Config Precedence

1. Command-line flags (highest priority)
2. Environment variables (`CONSTELLATION_SERVER_URL`, `CONSTELLATION_TOKEN`)
3. Config file
4. Built-in defaults (lowest priority)

---

## Examples

### Development Workflow

```bash
# Configure server (once)
constellation config set server.url http://localhost:8080

# Iterate on pipeline
constellation compile my-pipeline.cst
# ✓ Compilation successful

constellation run my-pipeline.cst --input text="Hello, World!"
# {
#   "greeting": "HELLO, WORLD!"
# }

# Visualize
constellation viz my-pipeline.cst | dot -Tpng > dag.png
```

### CI/CD Pipeline

```bash
#!/bin/bash
set -e

# Validate all pipelines compile
for f in pipelines/*.cst; do
  constellation compile "$f" --json || exit 1
done

# Deploy to staging
constellation --server $STAGING_URL --token $STAGING_TOKEN \
  deploy push pipelines/main.cst

# Run smoke test
constellation --server $STAGING_URL --token $STAGING_TOKEN \
  run pipelines/main.cst --input-file test-inputs.json --json \
  | jq -e '.status == "success"'
```

### Canary Deployment

```bash
# Deploy canary with 10% traffic
constellation deploy canary pipelines/v2.cst --percent 10

# Monitor (external)
sleep 300

# Check metrics
constellation server metrics | grep pipeline_errors

# If healthy, promote
constellation deploy promote my-pipeline

# If problems, rollback
constellation deploy rollback my-pipeline
```

### Operations

```bash
# Health check
constellation server health
# ✓ Server healthy
#   Version: 0.5.0
#   Uptime: 3d 14h 22m
#   Pipelines: 12 loaded

# List executions
constellation server executions list --limit 10
# ID                                    Pipeline      Status     Duration
# 550e8400-e29b-41d4-a716-446655440000  my-pipeline   completed  45ms
# ...

# Inspect failed execution
constellation server executions show 550e8400-... --json
```

---

## Implementation

### Module Structure

```
modules/lang-cli/
├── src/main/scala/io/constellation/cli/
│   ├── Main.scala              # Entry point
│   ├── Config.scala            # Configuration loading
│   ├── Client.scala            # HTTP client wrapper
│   ├── Output.scala            # Human/JSON output formatting
│   ├── commands/
│   │   ├── CompileCommand.scala
│   │   ├── RunCommand.scala
│   │   ├── VizCommand.scala
│   │   ├── ServerCommands.scala
│   │   ├── DeployCommands.scala
│   │   └── ConfigCommands.scala
│   └── model/
│       └── Responses.scala     # API response models
└── src/test/scala/io/constellation/cli/
    ├── CommandParsingTest.scala
    ├── ConfigTest.scala
    └── OutputFormattingTest.scala
```

### Dependencies

```scala
// build.sbt
lazy val langCli = project
  .in(file("modules/lang-cli"))
  .settings(
    libraryDependencies ++= Seq(
      "com.monovore"  %% "decline-effect" % "2.4.1",  // CLI parsing
      "org.http4s"    %% "http4s-ember-client" % http4sVersion,
      "org.http4s"    %% "http4s-circe" % http4sVersion,
      "io.circe"      %% "circe-generic" % circeVersion,
      "com.lihaoyi"   %% "fansi" % "0.4.0",           // Colored output
    ),
    assembly / mainClass := Some("io.constellation.cli.Main"),
    assembly / assemblyJarName := "constellation-cli.jar"
  )
```

### Technology Choices

| Component | Library | Rationale |
|-----------|---------|-----------|
| CLI parsing | `decline-effect` | FP-friendly, Cats Effect integration, composable subcommands |
| HTTP client | `http4s-ember-client` | Consistent with server stack, pure FP |
| JSON | `circe` | Already used throughout codebase |
| Colored output | `fansi` | Lightweight, cross-platform |

---

## Distribution

### Phase 1: Coursier (v1.0)

Leverage Maven Central publishing for Coursier installation:

```bash
# Create channel file (hosted on GitHub Pages)
# https://constellation-engine.io/coursier/channel.json

# Install
cs install --channel https://constellation-engine.io/coursier/channel.json constellation

# Or directly from Maven Central
cs install io.constellation:constellation-cli_3:0.5.0

# Usage
constellation --version
```

**Coursier Channel File:**

```json
{
  "constellation": {
    "mainClass": "io.constellation.cli.Main",
    "repositories": ["central"],
    "dependencies": ["io.constellation::constellation-cli:latest.release"]
  }
}
```

### Phase 2: Install Script

For users without Coursier:

```bash
curl -sSL https://constellation-engine.io/install.sh | bash
```

The script:
1. Detects OS and architecture
2. Downloads fat JAR from GitHub Releases
3. Creates launcher script in `~/.local/bin/constellation`
4. Adds to PATH if needed

### Future: Native Binary

GraalVM native-image for zero-JVM distribution. Deferred post-v1.0 due to:
- Build complexity (reflection configuration)
- Per-platform binaries
- CI/CD overhead

---

## Phased Rollout

### Phase 1: Core Commands (v1.0) ✓

- [x] `compile` command
- [x] `run` command
- [x] `viz` command
- [x] `config` commands
- [ ] Coursier distribution
- [x] Documentation

### Phase 2: Server Operations (v1.1) ✓

- [x] `server health`
- [x] `server pipelines`
- [x] `server executions`
- [x] `server metrics`

### Phase 3: Deployment (v1.2)

- [ ] `deploy push`
- [ ] `deploy canary`
- [ ] `deploy promote`
- [ ] `deploy rollback`
- [ ] Install script distribution

---

## Acceptance Criteria

### Phase 1 ✓

- [x] `constellation compile example.cst` reports errors or success
- [x] `constellation run example.cst --input text="hello"` prints output
- [x] `constellation viz example.cst` outputs valid DOT format
- [x] `constellation config set/get` persists configuration
- [x] `--json` flag works on all commands
- [ ] `cs install constellation` works from Maven Central
- [x] Exit codes are correct for all error conditions
- [x] `--help` documents all commands and options

### Phase 2 ✓

- [x] `constellation server health` shows server status
- [x] `constellation server pipelines` lists loaded pipelines
- [x] `constellation server executions list` shows history

### Phase 3

- [ ] `constellation deploy push` uploads pipeline
- [ ] `constellation deploy canary --percent N` creates canary
- [ ] `constellation deploy promote/rollback` work correctly
- [ ] Install script works on Linux and macOS

---

## Related

- [RFC-017: v1.0 Readiness](./rfc-017-v1-readiness.md) — Gap analysis identifying CLI need
- [RFC-015b: Pipeline Loader & Reload](./rfc-015b-pipeline-loader-reload.md) — Hot reload API used by deploy commands
- [RFC-015c: Canary Releases](./rfc-015c-canary-releases.md) — Canary API used by deploy commands
- [Issue #210](https://github.com/VledicFranco/constellation-engine/issues/210) — Original issue

---

## Open Questions

1. **Short alias**: Should we provide `cst` as a shorter alias for `constellation`?
2. **Shell completions**: Should Phase 1 include bash/zsh/fish completions?
3. **Watch mode**: Should `compile --watch` use filesystem watching or poll the server?
