# CLI

> **Path**: `organon/features/tooling/cli.md`
> **Component**: [lang-cli](../../components/cli/)

Command-line interface for Constellation Engine operations.

## Overview

The CLI is an HTTP client that communicates with a running Constellation server. It enables:

- **Quick iteration**: Compile and run pipelines from the terminal
- **CI/CD integration**: JSON output and deterministic exit codes
- **Operations**: Health checks, metrics, and deployment management
- **Scripting**: Pipe-friendly design for shell workflows

## Command Categories

### Pipeline Operations

```bash
constellation compile <file.cst>     # Type-check
constellation run <file.cst>         # Execute with inputs
constellation viz <file.cst>         # Generate DAG visualization
```

### Server Operations

```bash
constellation server health          # Check server status
constellation server pipelines       # List loaded pipelines
constellation server executions list # List suspended executions
constellation server metrics         # Show Prometheus metrics
```

### Deployment Operations

```bash
constellation deploy push <file>     # Deploy pipeline
constellation deploy canary <file>   # Canary deployment
constellation deploy promote <name>  # Promote canary
constellation deploy rollback <name> # Rollback to previous
constellation deploy status <name>   # Show canary status
```

### Configuration

```bash
constellation config show            # Display all config
constellation config get <key>       # Get specific value
constellation config set <key> <val> # Set value
```

## Design Principles

### 1. Client-Only Architecture

The CLI contains no compilation or execution logic. All operations go through the Constellation server which has the module registry, compiler, and runtime.

**Why:**
- Compilation requires knowing available modules (only server has the registry)
- Same behavior as dashboard and API consumers
- Lightweight binary (just an HTTP client)
- Unified authentication

### 2. Machine-Friendly Output

The `--json` flag produces valid JSON for all commands, enabling:

```bash
# Parse with jq
constellation run pipeline.cst --json | jq '.outputs.result'

# Check success in scripts
if constellation compile pipeline.cst --json | jq -e '.success'; then
  echo "Compilation passed"
fi
```

### 3. Deterministic Exit Codes

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

### 4. Layered Configuration

Precedence (highest to lowest):
1. CLI flags (`--server`, `--token`, `--json`)
2. Environment variables (`CONSTELLATION_SERVER_URL`, `CONSTELLATION_TOKEN`)
3. Config file (`~/.constellation/config.json`)
4. Built-in defaults

## Security Features

### Error Sanitization

Exception messages are sanitized to redact:
- Bearer tokens
- API keys (sk-...)
- Authorization headers
- Password patterns

### Atomic Config Writes

Configuration saves use temp file + atomic rename to prevent corruption.

### Path Traversal Mitigation

File operations resolve symlinks and validate paths before reading.

### Input Size Limits

Input files are limited to 10MB to prevent memory exhaustion.

## Component Location

| Aspect | Path |
|--------|------|
| Source | `modules/lang-cli/src/main/scala/io/constellation/cli/` |
| Tests | `modules/lang-cli/src/test/scala/io/constellation/cli/` |
| Fat JAR | `modules/lang-cli/target/scala-3.3.4/constellation-cli.jar` |

## Related Documentation

- [Component Reference](../../components/cli/) - Implementation details
- [ETHOS](../../components/cli/ETHOS.md) - Normative constraints
- [RFC-021](../../../rfcs/rfc-021-cli.md) - Full specification
- [User Guide](../../../website/docs/tooling/cli.md) - End-user documentation
