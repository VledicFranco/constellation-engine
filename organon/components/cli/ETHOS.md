# CLI Ethos

> Normative constraints for the command-line interface.

---

## Identity

- **IS:** HTTP client for Constellation server operations
- **IS NOT:** Standalone compiler, server, module runtime, or execution engine

---

## Semantic Mapping

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `Main` | Entry point, IOApp implementation |
| `CliApp` | Main application orchestrating command dispatch |
| `CliCommand` | Base trait for user-invokable CLI commands |
| `CliConfig` | User preferences and server connection settings |
| `HttpClient` | HTTP communication with Constellation server |
| `Output` | Formatted results for terminal display |
| `OutputFormat` | Human-readable or JSON output mode |
| `ExitCodes` | Machine-readable status indicators |
| `StringUtils` | Safe string operations and error sanitization |
| `ApiModels` | Shared response types for API calls |

For complete type signatures, see the generated catalog.

---

## Invariants

### 1. Exit codes are deterministic

Same input always produces the same exit code. Exit codes follow RFC-021 specification exactly.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-cli/src/main/scala/io/constellation/cli/CliApp.scala#ExitCodes` |
| Test | `modules/lang-cli/src/test/scala/io/constellation/cli/CompileCommandTest.scala#exit codes are defined correctly` |

### 2. JSON output is valid and parseable

The `--json` flag produces valid JSON on stdout for all commands. Output can be piped to `jq` or parsed programmatically.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-cli/src/main/scala/io/constellation/cli/Output.scala` |
| Test | `modules/lang-cli/src/test/scala/io/constellation/cli/OutputTest.scala#JSON format is valid JSON` |

### 3. Human output uses appropriate colors

Errors display in red, success in green, warnings in yellow. Colors are implemented via fansi for cross-platform support.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-cli/src/main/scala/io/constellation/cli/Output.scala#Red, Green, Yellow` |
| Test | `modules/lang-cli/src/test/scala/io/constellation/cli/OutputTest.scala#human format includes checkmark` |

### 4. Config precedence is consistent

CLI flags override environment variables, which override config file values, which override defaults. This order is deterministic and documented.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-cli/src/main/scala/io/constellation/cli/Config.scala#load` |
| Test | `modules/lang-cli/src/test/scala/io/constellation/cli/IntegrationTest.scala#config: load applies precedence correctly` |

### 5. Graceful handling of server unavailability

Connection errors return exit code 3 with a helpful message including the attempted URL and a hint to check if the server is running.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-cli/src/main/scala/io/constellation/cli/Output.scala#connectionError` |
| Test | `modules/lang-cli/src/test/scala/io/constellation/cli/OutputTest.scala#connectionError: human format shows URL and hint` |

### 6. File operations handle errors gracefully

Missing files, permission errors, and invalid content produce clear error messages with appropriate exit codes (1 for compile errors, 10 for usage errors).

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-cli/src/main/scala/io/constellation/cli/commands/CompileCommand.scala#readSourceFile` |
| Test | `modules/lang-cli/src/test/scala/io/constellation/cli/CompileCommandTest.scala#readSourceFile` |

### 7. Error messages are sanitized

Connection errors and exception messages are sanitized to redact sensitive information (Bearer tokens, API keys, passwords) before display.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-cli/src/main/scala/io/constellation/cli/StringUtils.scala#sanitizeError` |
| Test | `modules/lang-cli/src/test/scala/io/constellation/cli/EdgeCaseTest.scala#sanitizeError` |

### 8. Config writes are atomic

Configuration file writes use temp file + atomic rename to prevent corruption on concurrent access or process interruption.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-cli/src/main/scala/io/constellation/cli/Config.scala#save` |
| Test | `modules/lang-cli/src/test/scala/io/constellation/cli/EdgeCaseTest.scala#shallow path is accepted` |

### 9. Path traversal is mitigated

File operations resolve symlinks via `toRealPath()` and validate paths before reading to prevent directory traversal attacks.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-cli/src/main/scala/io/constellation/cli/commands/CompileCommand.scala#readSourceFile` |
| Test | `modules/lang-cli/src/test/scala/io/constellation/cli/EdgeCaseTest.scala` |

### 10. Input file size is bounded

Input files (`--input-file`) are limited to 10MB to prevent memory exhaustion from maliciously large files.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-cli/src/main/scala/io/constellation/cli/commands/RunCommand.scala#parseInputs` |
| Test | `modules/lang-cli/src/test/scala/io/constellation/cli/EdgeCaseTest.scala` |

---

## Principles (Prioritized)

1. **User-friendly over technical** — Error messages should guide users toward solutions, not expose implementation details
2. **Composable over monolithic** — Design for piping, scripting, and CI/CD integration
3. **Fail-fast over silent failures** — Exit with non-zero code on any error
4. **Explicit over implicit** — Show what's happening; no hidden side effects

---

## Decision Heuristics

- When adding commands, use subcommands for grouping (e.g., `server health`, `server metrics`)
- When formatting output, default to human-readable; JSON is opt-in via `--json`
- When handling errors, include actionable suggestions in human mode
- When parsing inputs, accept both `key=value` CLI args and JSON files
- When uncertain about exit codes, prefer higher specificity (e.g., use 4 for auth errors, not generic 1)

---

## Out of Scope

- Server implementation → see [http-api](../http-api/)
- Compilation logic → see [compiler](../compiler/)
- Execution engine → see [runtime](../runtime/)
- Type system → see [core](../core/)
- LSP protocol → see [lsp](../lsp/)

---

## Implements Features

| Feature | Artifacts |
|---------|-----------|
| [Tooling](../../features/tooling/) | CompileCommand, RunCommand, VizCommand (CLI access to pipeline operations) |
| [Extensibility](../../features/extensibility/) | ConfigCommand (user configuration) |
