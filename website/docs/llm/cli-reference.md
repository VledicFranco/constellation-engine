# CLI Reference

The Constellation CLI provides a command-line interface for compiling, running, deploying, and managing pipelines. All operations communicate with a running Constellation server via HTTP API.

## Installation

### Using Coursier (Recommended)

```bash
# Add the Constellation channel (once)
cs channel --add https://vledicfranco.github.io/constellation-engine/channel

# Install the CLI
cs install constellation

# Verify installation
constellation --version

# Update to latest
cs update constellation
```

### CI / Non-Interactive (Bootstrap)

```bash
# Create a standalone launcher directly from Maven Central
cs bootstrap io.github.vledicfranco:constellation-lang-cli_3:latest.release -o /usr/local/bin/constellation --force
```

### Building from Source

```bash
# Clone the repository
git clone https://github.com/VledicFranco/constellation-engine.git
cd constellation-engine

# Build the CLI fat JAR
make cli-assembly

# The JAR will be at modules/lang-cli/target/scala-3.3.4/constellation-cli.jar
java -jar modules/lang-cli/target/scala-3.3.4/constellation-cli.jar --version
```

## Quick Start

```bash
# Configure server URL (one-time setup)
constellation config set server.url http://localhost:8080

# Compile a pipeline
constellation compile my-pipeline.cst

# Run a pipeline with inputs
constellation run my-pipeline.cst --input name="Alice" --input age=30

# Visualize the pipeline DAG
constellation viz my-pipeline.cst --format dot | dot -Tpng > dag.png

# Deploy to production
constellation deploy push my-pipeline.cst --name my-pipeline
```

## Global Flags

These flags apply to all commands:

| Flag | Short | Description | Default |
|------|-------|-------------|---------|
| `--server <url>` | `-s` | Constellation server URL | `http://localhost:8080` |
| `--token <token>` | `-t` | API authentication token | From config file |
| `--json` | `-j` | Output as JSON | `false` |
| `--quiet` | `-q` | Suppress non-essential output | `false` |
| `--verbose` | `-v` | Verbose output for debugging | `false` |
| `--help` | `-h` | Show help | |
| `--version` | `-V` | Show CLI version | |

## Commands

### compile

Compile and type-check a pipeline file.

**Usage:**
```bash
constellation compile <file.cst> [FLAGS]
```

**Examples:**
```bash
# Basic compilation
constellation compile my-pipeline.cst
# ✓ Compilation successful (hash: sha256:a1b2c3...)

# With JSON output for CI/CD
constellation compile my-pipeline.cst --json
# {"success":true,"structuralHash":"sha256:a1b2c3...","syntacticHash":"sha256:d4e5f6..."}

# Check compilation on remote server
constellation compile my-pipeline.cst --server https://prod.example.com --token sk-...
```

**Exit Codes:**
- `0` - Compilation successful
- `1` - Compilation failed (syntax or type errors)
- `3` - Cannot connect to server
- `4` - Authentication failed
- `10` - Invalid arguments (file not found, etc.)

**JSON Output Format:**
```json
{
  "success": true,
  "structuralHash": "sha256:...",
  "syntacticHash": "sha256:...",
  "name": "my-pipeline"
}
```

For errors:
```json
{
  "success": false,
  "errors": [
    "Line 5: Type mismatch: expected String, got Int",
    "Line 8: Unknown module 'NonExistent'"
  ]
}
```

---

### run

Execute a pipeline with provided inputs.

**Usage:**
```bash
constellation run <file.cst> [--input key=value] [--input-file inputs.json] [FLAGS]
```

**Options:**
- `--input <key>=<value>`, `-i` - Provide an input value (can be specified multiple times)
- `--input-file <path>`, `-f` - Load inputs from a JSON file

**Examples:**
```bash
# Simple execution with inline inputs
constellation run greeting.cst --input name="Alice"
# ✓ Execution completed:
#   message: "Hello, Alice!"

# Multiple inputs
constellation run calculator.cst --input a=5 --input b=3 --input op="add"
# ✓ Execution completed:
#   result: 8

# Inputs from JSON file
constellation run complex-pipeline.cst --input-file inputs.json
# Where inputs.json contains:
# {
#   "name": "Bob",
#   "age": 30,
#   "active": true
# }

# JSON output for scripting
constellation run pipeline.cst --input text="hello" --json
# {"success":true,"outputs":{"result":"HELLO"}}

# CLI inputs override file inputs
constellation run pipeline.cst --input-file base.json --input override="value"
```

**Input Value Parsing:**

The CLI automatically infers types:
- `"hello"` → String
- `42` → Int
- `3.14` → Double
- `true` / `false` → Boolean
- `null` → Null
- `{"key":"value"}` → JSON object (pass valid JSON string)
- `[1,2,3]` → JSON array (pass valid JSON string)

**Exit Codes:**
- `0` - Execution successful
- `1` - Compilation failed
- `2` - Runtime error
- `3` - Cannot connect to server
- `4` - Authentication failed
- `10` - Invalid arguments or file errors

**JSON Output Format:**
```json
{
  "success": true,
  "outputs": {
    "result": "Hello, World!",
    "count": 42
  }
}
```

For suspended executions:
```json
{
  "success": true,
  "status": "suspended",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "missingInputs": {
    "email": "String",
    "age": "Int"
  }
}
```

For runtime errors:
```json
{
  "success": false,
  "error": "Division by zero at module Calculate"
}
```

---

### viz

Generate a visualization of the pipeline DAG.

**Usage:**
```bash
constellation viz <file.cst> [--format FORMAT] [FLAGS]
```

**Options:**
- `--format <format>`, `-F` - Output format: `dot`, `json`, `mermaid` (default: `dot`)

**Examples:**
```bash
# Generate Graphviz DOT format (default)
constellation viz my-pipeline.cst
# digraph pipeline {
#   rankdir=LR;
#   ...
# }

# Render to PNG using Graphviz
constellation viz my-pipeline.cst | dot -Tpng > pipeline.png

# Mermaid diagram format
constellation viz my-pipeline.cst --format mermaid
# graph LR
#   input_name[name: String]
#   module_0[Uppercase]
#   ...

# JSON for programmatic use
constellation viz my-pipeline.cst --format json
# {
#   "nodes": [
#     {"id": "input_name", "label": "name: String", "dependencies": []},
#     {"id": "module_0", "label": "Uppercase", "dependencies": ["input_name"]}
#   ],
#   "edges": [
#     {"from": "input_name", "to": "module_0"}
#   ]
# }
```

**Output Formats:**

| Format | Use Case | Example Output |
|--------|----------|----------------|
| `dot` | Graphviz rendering | `digraph pipeline { ... }` |
| `mermaid` | Markdown documentation | `graph LR ...` |
| `json` | Programmatic processing | `{"nodes":[...],"edges":[...]}` |

**Exit Codes:**
- `0` - Visualization generated successfully
- `1` - Compilation failed
- `3` - Cannot connect to server
- `4` - Authentication failed
- `5` - Pipeline not found after compilation

---

### server

Server operations for monitoring and management.

#### server health

Check server health status.

**Usage:**
```bash
constellation server health [FLAGS]
```

**Examples:**
```bash
# Check health
constellation server health
# ✓ Server healthy
#   Version: 0.7.0
#   Uptime: 3d 14h 22m
#   Pipelines: 12 loaded

# JSON output for monitoring
constellation server health --json
# {"status":"ok","version":"0.7.0","uptime":"3d 14h 22m","pipelineCount":12}
```

**Exit Codes:**
- `0` - Server is healthy
- `2` - Server unhealthy
- `3` - Cannot connect to server

---

#### server pipelines

List loaded pipelines or show details for a specific pipeline.

**Usage:**
```bash
constellation server pipelines [FLAGS]
constellation server pipelines show <name-or-hash> [FLAGS]
```

**Examples:**
```bash
# List all pipelines
constellation server pipelines
# 3 pipeline(s) loaded:
#   my-pipeline (sha256:a1b2c3...) - 5 modules, outputs: [result, count]
#   data-processor (sha256:d4e5f6...) - 3 modules, outputs: [data]
#   validator (sha256:789abc...) - 2 modules, outputs: [valid]

# Show details for a specific pipeline
constellation server pipelines show my-pipeline
# Pipeline Details
#
#   Hash: sha256:a1b2c3d4e5f6...
#   Aliases: my-pipeline
#   Compiled: 2026-02-09T10:30:45Z
#
# Inputs:
#   name: String
#   age: Int
#
# Outputs:
#   greeting: String
#   isAdult: Boolean
#
# Modules: (3)
#   Uppercase v1.0
#     Converts text to uppercase
#   ...

# JSON output
constellation server pipelines --json
# {"pipelines":[{"name":"my-pipeline","structuralHash":"sha256:...","aliases":["my-pipeline"],...}]}
```

**Exit Codes:**
- `0` - Success
- `3` - Cannot connect to server
- `4` - Authentication failed
- `5` - Pipeline not found (for `show` subcommand)

---

#### server executions

Manage execution history.

**Usage:**
```bash
constellation server executions list [--limit N] [FLAGS]
constellation server executions show <id> [FLAGS]
constellation server executions delete <id> [FLAGS]
```

**Examples:**
```bash
# List recent executions
constellation server executions list
# ID                                  Pipeline      Missing  Created
# 550e8400-e29b-41d4-a716-446655440000  sha256:a1b...  0        2026-02-09 10:30
# 6ba7b810-9dad-11d1-80b4-00c04fd430c8  sha256:d4e...  2        2026-02-09 09:15

# List top 5 executions
constellation server executions list --limit 5

# Show execution details
constellation server executions show 550e8400-e29b-41d4-a716-446655440000
# Execution Details
#
#   ID: 550e8400-e29b-41d4-a716-446655440000
#   Pipeline: sha256:a1b2c3d4...
#   Resumptions: 2
#   Created: 2026-02-09T10:30:45Z
#
# Missing Inputs:
#   email: String
#   phone: String

# Delete a suspended execution
constellation server executions delete 550e8400-e29b-41d4-a716-446655440000
# ✓ Deleted execution 550e8400-e29b-41d4-a716-446655440000
```

**Exit Codes:**
- `0` - Success
- `3` - Cannot connect to server
- `4` - Authentication failed
- `5` - Execution not found

---

#### server metrics

Show server metrics (Prometheus format).

**Usage:**
```bash
constellation server metrics [FLAGS]
```

**Examples:**
```bash
# Human-readable metrics
constellation server metrics
# Server Metrics
#
# Server:
#   Uptime: 3d 14h 22m
#   Requests: 45023
#
# Cache:
#   Hits: 12500
#   Misses: 800
#   Hit Rate: 94.0%
#   Entries: 42
#
# Scheduler: enabled
#   Active: 3
#   Queued: 1
#   Completed: 8923

# JSON output
constellation server metrics --json
# {
#   "server": {
#     "uptime_seconds": 295320,
#     "requests_total": 45023
#   },
#   "cache": {
#     "hits": 12500,
#     "misses": 800,
#     "hitRate": 0.94,
#     "entries": 42
#   },
#   ...
# }
```

**Exit Codes:**
- `0` - Success
- `3` - Cannot connect to server
- `4` - Authentication failed

---

### deploy

Pipeline deployment operations with versioning and canary releases.

#### deploy push

Deploy a pipeline to the server.

**Usage:**
```bash
constellation deploy push <file.cst> [--name NAME] [FLAGS]
```

**Options:**
- `--name <name>`, `-n` - Pipeline name (default: filename without `.cst` extension)

**Examples:**
```bash
# Deploy pipeline
constellation deploy push my-pipeline.cst
# ✓ Deployed my-pipeline v1
#   Hash: sha256:a1b2c3...

# Deploy with custom name
constellation deploy push pipeline.cst --name production-pipeline
# ✓ Deployed production-pipeline v1
#   Hash: sha256:d4e5f6...

# Re-deploy (increments version if changed)
constellation deploy push my-pipeline.cst
# ✓ Deployed my-pipeline v2
#   Hash: sha256:789abc...
#   Previous: sha256:a1b2c3...

# No changes
constellation deploy push my-pipeline.cst
# ○ No changes to my-pipeline (already at v2)
```

**Exit Codes:**
- `0` - Deployment successful
- `1` - Compilation failed
- `3` - Cannot connect to server
- `4` - Authentication failed
- `10` - File not found or invalid

---

#### deploy canary

Deploy a new version as a canary with gradual traffic shifting.

**Usage:**
```bash
constellation deploy canary <file.cst> [--name NAME] [--percent N] [FLAGS]
```

**Options:**
- `--name <name>`, `-n` - Pipeline name
- `--percent <N>`, `-p` - Initial traffic percentage to new version (default: `10`)

**Examples:**
```bash
# Start canary with 10% traffic
constellation deploy canary my-pipeline.cst --percent 10
# ✓ Canary started for my-pipeline
#   New version: v2 (sha256:789abc...)
#   Old version: v1 (sha256:a1b2c3...)
#   Traffic: 10% to new version
#   Status: active

# Custom traffic percentage
constellation deploy canary my-pipeline.cst --percent 25
# ✓ Canary started for my-pipeline
#   ...
#   Traffic: 25% to new version
```

**Exit Codes:**
- `0` - Canary started successfully
- `1` - Compilation failed
- `3` - Cannot connect to server
- `4` - Authentication failed
- `6` - Conflict (canary already active for this pipeline)
- `10` - File not found or invalid

---

#### deploy promote

Promote a canary deployment by increasing traffic to the new version.

**Usage:**
```bash
constellation deploy promote <pipeline> [FLAGS]
```

**Examples:**
```bash
# Promote canary (increases traffic incrementally)
constellation deploy promote my-pipeline
# ✓ Canary my-pipeline promoted: 10% → 25%

# Continue promoting until 100%
constellation deploy promote my-pipeline
# ✓ Canary my-pipeline promoted: 25% → 50%

constellation deploy promote my-pipeline
# ✓ Canary my-pipeline promoted: 50% → 100% (complete)
```

**Exit Codes:**
- `0` - Promotion successful
- `3` - Cannot connect to server
- `4` - Authentication failed
- `5` - No active canary deployment for pipeline

---

#### deploy rollback

Rollback a pipeline to a previous version.

**Usage:**
```bash
constellation deploy rollback <pipeline> [--version N] [FLAGS]
```

**Options:**
- `--version <N>`, `-v` - Specific version to rollback to (default: previous version)

**Examples:**
```bash
# Rollback to previous version
constellation deploy rollback my-pipeline
# ✓ Rolled back my-pipeline
#   From: v3
#   To: v2
#   Hash: sha256:789abc...

# Rollback to specific version
constellation deploy rollback my-pipeline --version 1
# ✓ Rolled back my-pipeline
#   From: v3
#   To: v1
#   Hash: sha256:a1b2c3...
```

**Exit Codes:**
- `0` - Rollback successful
- `3` - Cannot connect to server
- `4` - Authentication failed
- `5` - Pipeline or version not found

---

#### deploy status

Show status of a canary deployment.

**Usage:**
```bash
constellation deploy status <pipeline> [FLAGS]
```

**Examples:**
```bash
# Check canary status
constellation deploy status my-pipeline
# Canary Deployment: my-pipeline
#
#   Status: active
#   Traffic: 25% to new version (step 2)
#   Started: 2026-02-09T10:30:45Z
#
#   Old version: v1 (sha256:a1b2c3...)
#   New version: v2 (sha256:789abc...)
#
# Metrics:
#   Old version: 7500 reqs, 0.5% errors, 45ms p99
#   New version: 2500 reqs, 0.3% errors, 42ms p99
```

**Exit Codes:**
- `0` - Status retrieved successfully
- `3` - Cannot connect to server
- `4` - Authentication failed
- `5` - No active canary deployment for pipeline

---

### config

Manage CLI configuration.

#### config show

Show all configuration.

**Usage:**
```bash
constellation config show [FLAGS]
```

**Examples:**
```bash
# Show current configuration
constellation config show
# Server:
#   url: http://localhost:8080
#   token: (set)
# Defaults:
#   output: human
#   viz_format: dot

# JSON output
constellation config show --json
# {
#   "server": {
#     "url": "http://localhost:8080",
#     "token": "sk-..."
#   },
#   "defaults": {
#     "output": "human",
#     "viz_format": "dot"
#   }
# }
```

---

#### config get

Get a specific configuration value.

**Usage:**
```bash
constellation config get <key> [FLAGS]
```

**Examples:**
```bash
# Get server URL
constellation config get server.url
# server.url = http://localhost:8080

# Get default output format
constellation config get defaults.output
# defaults.output = human

# Non-existent key
constellation config get invalid.key
# Config key 'invalid.key' not found
```

**Exit Codes:**
- `0` - Key found
- `5` - Key not found

---

#### config set

Set a configuration value.

**Usage:**
```bash
constellation config set <key> <value> [FLAGS]
```

**Examples:**
```bash
# Set server URL
constellation config set server.url https://prod.example.com
# ✓ Set server.url = https://prod.example.com

# Set API token
constellation config set server.token sk-prod-abc123
# ✓ Set server.token = sk-prod-abc123

# Set default output format
constellation config set defaults.output json
# ✓ Set defaults.output = json

# Set visualization format
constellation config set defaults.viz_format mermaid
# ✓ Set defaults.viz_format = mermaid
```

**Valid Keys:**
- `server.url` - Server URL (string)
- `server.token` - API authentication token (string)
- `defaults.output` - Default output format: `human` or `json`
- `defaults.viz_format` - Default visualization format: `dot`, `json`, or `mermaid`

**Exit Codes:**
- `0` - Value set successfully
- `10` - Invalid key or value

---

## Configuration

### Config File Location

Configuration is stored in:
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

### Configuration Precedence

Configuration values are resolved in the following order (highest priority first):

1. **Command-line flags** - `--server`, `--token`, `--json`
2. **Environment variables** - `CONSTELLATION_SERVER_URL`, `CONSTELLATION_TOKEN`
3. **Config file** - `~/.constellation/config.json`
4. **Built-in defaults** - `http://localhost:8080`, no token, human output

**Example:**
```bash
# Config file has server.url = http://localhost:8080
# Environment has CONSTELLATION_SERVER_URL=https://staging.example.com
# Command has --server https://prod.example.com

constellation compile pipeline.cst
# Uses: https://prod.example.com (command-line wins)

CONSTELLATION_SERVER_URL=https://staging.example.com constellation compile pipeline.cst
# Uses: https://staging.example.com (environment wins over config file)

constellation compile pipeline.cst
# Uses: http://localhost:8080 (from config file)
```

### Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `CONSTELLATION_SERVER_URL` | Server URL | `http://localhost:8080` |
| `CONSTELLATION_TOKEN` | API token | `sk-prod-abc123` |

---

## Exit Codes

The CLI uses semantic exit codes for scripting and CI/CD integration:

| Code | Name | Description | When to Expect |
|------|------|-------------|----------------|
| `0` | `SUCCESS` | Command completed successfully | All successful operations |
| `1` | `COMPILE_ERROR` | Pipeline compilation failed | Syntax errors, type errors, missing modules |
| `2` | `RUNTIME_ERROR` | Pipeline execution failed | Division by zero, null dereference, etc. |
| `3` | `CONNECTION_ERROR` | Cannot connect to server | Server down, network issues, wrong URL |
| `4` | `AUTH_ERROR` | Authentication failed | Invalid token, missing token when required |
| `5` | `NOT_FOUND` | Resource not found | Pipeline, execution, or config key not found |
| `6` | `CONFLICT` | Resource conflict | Canary already active, version conflict |
| `10` | `USAGE_ERROR` | Invalid command-line arguments | File not found, invalid flags, malformed input |

**Examples in CI/CD:**
```bash
#!/bin/bash
set -e

# Compile all pipelines
for f in pipelines/*.cst; do
  constellation compile "$f" || exit $?
done

# Deploy only if compilation succeeds
constellation deploy push main-pipeline.cst

# Exit code 0 means success
echo "Deployment complete"
```

---

## JSON Output Format

All commands support `--json` flag for machine-readable output.

### Success Response

```json
{
  "success": true,
  "message": "Operation completed",
  "data": { ... }
}
```

### Error Response

```json
{
  "success": false,
  "error": "Error type",
  "message": "Human-readable error message"
}
```

### Compilation Errors

```json
{
  "success": false,
  "errors": [
    "Line 5: Type mismatch: expected String, got Int",
    "Line 8: Unknown module 'NonExistent'"
  ]
}
```

### Connection Errors

```json
{
  "success": false,
  "error": "connection_error",
  "message": "Connection refused",
  "url": "http://localhost:8080"
}
```

---

## Common Workflows

### Development Iteration

```bash
# Configure once
constellation config set server.url http://localhost:8080

# Edit pipeline...
# Compile and check for errors
constellation compile my-pipeline.cst

# Run with test inputs
constellation run my-pipeline.cst --input name="Alice" --input age=30

# Visualize to understand the DAG
constellation viz my-pipeline.cst | dot -Tpng > dag.png
```

### CI/CD Validation

```bash
#!/bin/bash
set -e

# Validate all pipelines compile
echo "Validating pipelines..."
for f in pipelines/*.cst; do
  echo "Checking $f..."
  constellation compile "$f" --json || exit 1
done

echo "All pipelines valid!"
```

### Deployment Pipeline

```bash
#!/bin/bash
set -e

SERVER_URL="https://prod.example.com"
TOKEN="$CONSTELLATION_PROD_TOKEN"

# Deploy to production
echo "Deploying to production..."
constellation --server "$SERVER_URL" --token "$TOKEN" \
  deploy push pipelines/main.cst --name main-pipeline

# Run smoke test
echo "Running smoke test..."
constellation --server "$SERVER_URL" --token "$TOKEN" \
  run pipelines/main.cst --input-file test-inputs.json --json \
  | jq -e '.success == true'

echo "Deployment successful!"
```

### Canary Rollout

```bash
#!/bin/bash
set -e

PIPELINE="my-pipeline"
SERVER="https://prod.example.com"
TOKEN="$CONSTELLATION_PROD_TOKEN"

# Deploy canary with 10% traffic
echo "Starting canary deployment..."
constellation --server "$SERVER" --token "$TOKEN" \
  deploy canary pipelines/v2.cst --name "$PIPELINE" --percent 10

# Wait and monitor
echo "Monitoring canary for 5 minutes..."
sleep 300

# Check metrics
constellation --server "$SERVER" --token "$TOKEN" \
  deploy status "$PIPELINE"

# If healthy, promote
read -p "Promote canary? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
  constellation --server "$SERVER" --token "$TOKEN" \
    deploy promote "$PIPELINE"
  echo "Canary promoted!"
else
  # Rollback
  constellation --server "$SERVER" --token "$TOKEN" \
    deploy rollback "$PIPELINE"
  echo "Canary rolled back!"
fi
```

### Monitoring and Operations

```bash
# Check server health
constellation server health

# List all loaded pipelines
constellation server pipelines

# Inspect a specific pipeline
constellation server pipelines show my-pipeline

# View execution history
constellation server executions list --limit 20

# Inspect a failed execution
constellation server executions show 550e8400-e29b-41d4-a716-446655440000

# Check server metrics
constellation server metrics
```

---

## Integration with Build Tools

### Makefile

```makefile
.PHONY: compile test deploy

# Compile all pipelines
compile:
	@for f in pipelines/*.cst; do \
		echo "Compiling $$f..."; \
		constellation compile $$f || exit 1; \
	done

# Run test suite
test:
	@constellation run pipelines/test.cst --input-file test-inputs.json

# Deploy to staging
deploy-staging:
	@constellation --server $(STAGING_URL) --token $(STAGING_TOKEN) \
		deploy push pipelines/main.cst

# Deploy to production with canary
deploy-prod:
	@constellation --server $(PROD_URL) --token $(PROD_TOKEN) \
		deploy canary pipelines/main.cst --percent 10
```

### GitHub Actions

```yaml
name: Validate Pipelines

on: [push, pull_request]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Install Constellation CLI
        run: cs bootstrap io.github.vledicfranco:constellation-lang-cli_3:latest.release -o /usr/local/bin/constellation --force

      - name: Start Constellation Server
        run: |
          # Start server in background
          docker run -d -p 8080:8080 constellation/server:latest

          # Wait for server to be ready
          timeout 30 sh -c 'until curl -s http://localhost:8080/health; do sleep 1; done'

      - name: Compile Pipelines
        run: |
          for f in pipelines/*.cst; do
            echo "Validating $f..."
            constellation compile "$f" --json || exit 1
          done

      - name: Run Tests
        run: |
          constellation run pipelines/test.cst \
            --input-file test-inputs.json \
            --json | jq -e '.success == true'
```

### GitLab CI

```yaml
stages:
  - validate
  - deploy

validate-pipelines:
  stage: validate
  image: constellation/cli:latest
  services:
    - constellation/server:latest
  script:
    - |
      for f in pipelines/*.cst; do
        echo "Validating $f..."
        constellation compile "$f" --json || exit 1
      done

deploy-production:
  stage: deploy
  image: constellation/cli:latest
  only:
    - main
  script:
    - constellation --server $PROD_SERVER_URL --token $PROD_TOKEN
        deploy push pipelines/main.cst
```

---

## Troubleshooting

### Cannot connect to server

**Error:**
```
✗ Cannot connect to server at http://localhost:8080
  Connection refused
  Hint: Make sure the Constellation server is running
```

**Solutions:**
1. Check if server is running: `curl http://localhost:8080/health`
2. Verify server URL: `constellation config get server.url`
3. Update server URL: `constellation config set server.url http://correct-url:8080`
4. Check firewall and network connectivity

---

### Authentication failed

**Error:**
```
✗ Authentication required
```

**Solutions:**
1. Set API token: `constellation config set server.token sk-your-token`
2. Or use flag: `constellation compile --token sk-your-token pipeline.cst`
3. Or use environment: `export CONSTELLATION_TOKEN=sk-your-token`
4. Verify token is valid and has correct permissions

---

### File not found

**Error:**
```
✗ File not found: my-pipeline.cst
```

**Solutions:**
1. Check file path: `ls -la my-pipeline.cst`
2. Use absolute path: `constellation compile /absolute/path/to/pipeline.cst`
3. Check current directory: `pwd`

---

### Input file too large

**Error:**
```
✗ Input file too large (max 10MB): large-inputs.json
```

**Solutions:**
1. Split inputs into smaller files
2. Use inline `--input` flags for smaller datasets
3. Reduce input file size (CLI has 10MB limit for safety)

---

## Advanced Usage

### Custom Output Parsing

```bash
# Extract only the structural hash
HASH=$(constellation compile pipeline.cst --json | jq -r '.structuralHash')
echo "Pipeline hash: $HASH"

# Check if compilation succeeded
if constellation compile pipeline.cst --json | jq -e '.success == true' > /dev/null; then
  echo "Compilation successful"
else
  echo "Compilation failed"
  exit 1
fi

# Extract error messages
constellation compile bad-pipeline.cst --json | jq -r '.errors[]'
```

### Batch Operations

```bash
# Compile all pipelines in parallel
find pipelines/ -name "*.cst" -print0 | \
  xargs -0 -P 4 -I {} constellation compile {}

# Deploy multiple pipelines
for pipeline in pipelines/*.cst; do
  name=$(basename "$pipeline" .cst)
  constellation deploy push "$pipeline" --name "$name"
done
```

### Health Monitoring

```bash
# Continuous health check
watch -n 5 'constellation server health'

# Alert if server unhealthy
if ! constellation server health --json | jq -e '.status == "ok"' > /dev/null; then
  echo "Server unhealthy!" | mail -s "Alert" admin@example.com
fi
```

### Pipeline Comparison

```bash
# Compare two pipeline versions
HASH1=$(constellation compile v1.cst --json | jq -r '.structuralHash')
HASH2=$(constellation compile v2.cst --json | jq -r '.structuralHash')

if [ "$HASH1" = "$HASH2" ]; then
  echo "Pipelines are structurally identical"
else
  echo "Pipelines differ"
  echo "v1: $HASH1"
  echo "v2: $HASH2"
fi
```

---

## See Also

- [HTTP API Reference](../api-reference/http-api-overview.md) - Full HTTP API documentation
- [Pipeline Structure](../language/pipeline-structure.md) - Pipeline language syntax
- [Deployment Guide](../operations/deployment.md) - Production deployment best practices
