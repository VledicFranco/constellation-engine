---
title: "CLI Tool"
sidebar_position: 0
description: "Command-line interface for Constellation Engine"
---

# CLI Tool


The Constellation CLI provides command-line access to compile, execute, and visualize pipelines. It communicates with a running Constellation server via HTTP.

## Installation

### Coursier (Recommended)

```bash
# Install from Maven Central
cs install io.constellation:constellation-cli_3:0.6.1

# Verify installation
constellation --version
```

### Manual Installation

```bash
# Download the fat JAR
curl -sSL https://github.com/VledicFranco/constellation-engine/releases/download/v0.6.1/constellation-cli.jar -o constellation-cli.jar

# Create launcher script
echo '#!/bin/bash
java -jar /path/to/constellation-cli.jar "$@"' > constellation
chmod +x constellation
mv constellation ~/.local/bin/
```

### Build from Source

```bash
cd constellation-engine
make cli-assembly
# JAR created at: modules/lang-cli/target/scala-3.3.1/constellation-cli.jar
```

## Quick Start

```bash
# Configure server connection (once)
constellation config set server.url http://localhost:8080

# Compile a pipeline
constellation compile my-pipeline.cst

# Run with inputs
constellation run my-pipeline.cst --input text="Hello, World!"

# Visualize the DAG
constellation viz my-pipeline.cst | dot -Tpng > dag.png
```

## Commands

### compile

Type-check a pipeline file without executing it.

```bash
constellation compile <file.cst>
```

**Examples:**

```bash
# Basic compilation
constellation compile pipelines/transform.cst

# JSON output for scripting
constellation compile pipelines/transform.cst --json
```

**Output (success):**
```
✓ Compilation successful (hash: 7a3b8c9d...)
```

**Output (error):**
```
✗ Compilation failed with 2 error(s):
  • Syntax error at line 3: unexpected token '}'
  • Type error at line 7: expected Int, got String
```

### run

Execute a pipeline with provided inputs.

```bash
constellation run <file.cst> [options]
```

**Options:**
- `--input <key>=<value>`, `-i`: Provide an input value (repeatable)
- `--input-file <path.json>`, `-f`: Load inputs from a JSON file

**Examples:**

```bash
# Single input
constellation run greet.cst --input name="World"

# Multiple inputs
constellation run process.cst --input text="hello" --input count=5

# Inputs from file
constellation run complex.cst --input-file inputs.json

# Combined (CLI overrides file)
constellation run pipeline.cst --input-file defaults.json --input override=true
```

**Input File Format:**

```json
{
  "text": "Hello, World!",
  "count": 42,
  "enabled": true,
  "items": ["a", "b", "c"]
}
```

**Output (success):**
```
✓ Execution completed:
  greeting: "HELLO, WORLD!"
  wordCount: 2
```

**Output (suspended):**
```
⏸ Execution suspended (ID: 7a3b8c9d...)
  Missing inputs:
    text: String
    count: Int
```

### viz

Generate a DAG visualization of a pipeline.

```bash
constellation viz <file.cst> [options]
```

**Options:**
- `--format <format>`, `-F`: Output format (default: `dot`)
  - `dot`: Graphviz DOT format
  - `json`: Raw DAG as JSON
  - `mermaid`: Mermaid diagram syntax

**Examples:**

```bash
# Generate PNG using Graphviz
constellation viz pipeline.cst | dot -Tpng > dag.png

# Generate SVG
constellation viz pipeline.cst | dot -Tsvg > dag.svg

# JSON for custom processing
constellation viz pipeline.cst --format json | jq '.nodes'

# Mermaid for documentation
constellation viz pipeline.cst --format mermaid
```

**DOT Output:**
```dot
digraph pipeline {
  rankdir=LR;
  node [shape=box, style=rounded];

  "input_text" [label="text: String"];
  "module_0" [label="Uppercase"];
  "output_result" [label="out: result"];

  "input_text" -> "module_0";
  "module_0" -> "output_result";
}
```

### server

Server operations and monitoring.

```bash
constellation server <subcommand>
```

**Subcommands:**
- `health`: Check server health status
- `pipelines`: List loaded pipelines
- `pipelines show <name>`: Show pipeline details
- `executions list`: List suspended executions
- `executions show <id>`: Show execution details
- `executions delete <id>`: Delete a suspended execution
- `metrics`: Show server metrics

**Examples:**

```bash
# Check server health
constellation server health
# ✓ Server healthy

# List all pipelines
constellation server pipelines
# 3 pipeline(s) loaded:
#   my-pipeline (7a3b8c9d...) - 2 modules, outputs: [result]
#   transform (abc12345...) - 3 modules, outputs: [output]

# Show pipeline details
constellation server pipelines show my-pipeline

# List suspended executions
constellation server executions list
# ID                                    Pipeline       Missing  Created
# 550e8400-e29b-41d4-a716-446655440000  7a3b8c9d...        2    2026-02-08T10:30:00

# Show execution details
constellation server executions show 550e8400-e29b-41d4-a716-446655440000

# Delete a suspended execution
constellation server executions delete 550e8400-e29b-41d4-a716-446655440000

# Show server metrics
constellation server metrics
# Server Metrics
#
# Server:
#   Uptime: 3d 14h 22m
#   Requests: 12345
#
# Cache:
#   Hits: 8901
#   Misses: 1234
#   Hit Rate: 87.8%
```

### config

Manage CLI configuration.

```bash
constellation config <subcommand>
```

**Subcommands:**
- `show`: Display all configuration
- `get <key>`: Get a specific value
- `set <key> <value>`: Set a value

**Examples:**

```bash
# Show all config
constellation config show

# Get server URL
constellation config get server.url

# Set server URL
constellation config set server.url http://prod.example.com:9090

# Set authentication token
constellation config set server.token sk-your-api-key
```

**Config File Location:**
- Unix/macOS: `~/.constellation/config.json`
- Windows: `%USERPROFILE%\.constellation\config.json`

### deploy

Pipeline deployment and canary release operations.

```bash
constellation deploy <subcommand>
```

**Subcommands:**
- `push <file.cst>`: Deploy a pipeline to the server
- `canary <file.cst>`: Deploy a pipeline as a canary release
- `promote <pipeline>`: Promote a canary deployment to stable
- `rollback <pipeline>`: Rollback a pipeline to a previous version
- `status <pipeline>`: Show canary deployment status

**Examples:**

```bash
# Deploy a pipeline
constellation deploy push pipeline.cst
# ✓ Deployed my-pipeline v1
#   Hash: abc123def456...

# Deploy with custom name
constellation deploy push pipeline.cst --name production-scorer

# Start a canary deployment (10% traffic to new version)
constellation deploy canary pipeline.cst --percent 10
# ✓ Canary started for my-pipeline
#   New version: v2 (def456...)
#   Old version: v1 (abc123...)
#   Traffic: 10% to new version

# Check canary status
constellation deploy status my-pipeline
# Canary Deployment: my-pipeline
#   Status: observing
#   Traffic: 25% to new version (step 2)
#   Metrics:
#     Old version: 950 reqs, 0.2% errors, 45ms p99
#     New version: 50 reqs, 2.0% errors, 42ms p99

# Manually promote to next step
constellation deploy promote my-pipeline
# ✓ Canary my-pipeline promoted: 25% → 50%

# Rollback to previous version
constellation deploy rollback my-pipeline
# ✓ Rolled back my-pipeline
#   From: v2
#   To: v1

# Rollback to specific version
constellation deploy rollback my-pipeline --version 1
```

## Global Options

| Flag | Short | Description |
|------|-------|-------------|
| `--server <url>` | `-s` | Constellation server URL |
| `--token <token>` | `-t` | API authentication token |
| `--json` | `-j` | Output as JSON (for scripting) |
| `--quiet` | `-q` | Suppress non-essential output |
| `--verbose` | `-v` | Verbose output for debugging |
| `--help` | `-h` | Show help |
| `--version` | `-V` | Show version |

**Examples:**

```bash
# Use a specific server
constellation --server http://staging:8080 compile pipeline.cst

# Authenticate with token
constellation --token sk-abc123 run pipeline.cst

# JSON output for CI/CD
constellation --json compile pipeline.cst | jq '.success'
```

## Configuration

### Config File

```json
{
  "server": {
    "url": "http://localhost:8080",
    "token": "sk-your-api-key"
  },
  "defaults": {
    "output": "human",
    "viz_format": "dot"
  }
}
```

### Environment Variables

| Variable | Description |
|----------|-------------|
| `CONSTELLATION_SERVER_URL` | Server URL |
| `CONSTELLATION_TOKEN` | API token |

### Precedence

Configuration values are applied in this order (highest priority first):

1. Command-line flags (`--server`, `--token`)
2. Environment variables
3. Config file (`~/.constellation/config.json`)
4. Built-in defaults

## Exit Codes

| Code | Name | Description |
|------|------|-------------|
| 0 | `SUCCESS` | Command completed successfully |
| 1 | `COMPILE_ERROR` | Pipeline compilation failed |
| 2 | `RUNTIME_ERROR` | Pipeline execution failed |
| 3 | `CONNECTION_ERROR` | Cannot connect to server |
| 4 | `AUTH_ERROR` | Authentication failed |
| 5 | `NOT_FOUND` | Resource not found |
| 6 | `CONFLICT` | Resource conflict (e.g., canary already active) |
| 10 | `USAGE_ERROR` | Invalid command-line arguments |

**Example CI Usage:**

```bash
#!/bin/bash
set -e

# Compile all pipelines
for f in pipelines/*.cst; do
  constellation compile "$f" --json || exit 1
done

# Run tests
constellation run tests/integration.cst \
  --input-file test-data.json \
  --json | jq -e '.success'
```

## Common Patterns

### CI/CD Integration

```yaml
# GitHub Actions example
jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Install CLI
        run: cs install io.constellation:constellation-cli_3:0.6.1
      - name: Validate pipelines
        run: |
          for f in pipelines/*.cst; do
            constellation compile "$f" --json
          done
```

### Scripting with JSON Output

```bash
# Extract outputs
result=$(constellation run pipeline.cst --input x=5 --json)
echo "$result" | jq -r '.outputs.result'

# Check success
if constellation run test.cst --json | jq -e '.success'; then
  echo "Test passed"
fi
```

### Canary Deployment

```bash
# Deploy canary with 5% traffic
constellation deploy canary pipelines/v2.cst --percent 5

# Wait and monitor
sleep 300

# Check metrics
constellation deploy status my-pipeline

# If healthy, promote to next step
constellation deploy promote my-pipeline

# If problems, rollback
constellation deploy rollback my-pipeline
```

### Production Deployment Pipeline

```bash
#!/bin/bash
set -e

# Validate before deploying
constellation compile pipeline.cst --json || exit 1

# Deploy to staging
constellation --server $STAGING_URL deploy push pipeline.cst

# Run smoke tests
constellation --server $STAGING_URL run pipeline.cst \
  --input-file test-inputs.json --json | jq -e '.success'

# Deploy to production as canary
constellation --server $PROD_URL deploy canary pipeline.cst --percent 10

echo "Canary deployed. Monitor metrics before promoting."
```

## Troubleshooting

### Cannot connect to server

```
✗ Cannot connect to server at http://localhost:8080
  Connection refused
  Hint: Make sure the Constellation server is running
```

**Solution:** Start the Constellation server with `make server` or check the URL.

### Authentication required

```
✗ Authentication required
```

**Solution:** Set your API token:
```bash
constellation config set server.token sk-your-api-key
# or
constellation --token sk-your-api-key run pipeline.cst
```

### File not found

```
✗ File not found: pipeline.cst
```

**Solution:** Check the file path. Use absolute paths or ensure you're in the correct directory.

### Invalid input format

```
✗ Invalid input format 'key', expected key=value
```

**Solution:** Use the correct format for `--input`:
```bash
constellation run p.cst --input key=value   # Correct
constellation run p.cst --input key         # Wrong
```
