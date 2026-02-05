# LSP Protocol

> **Path**: `docs/tooling/lsp.md`
> **Parent**: [tooling/](./README.md)

Language Server Protocol implementation over WebSocket for IDE integration.

## Overview

The Constellation LSP server provides:

- **Autocomplete** - Module names and keywords
- **Diagnostics** - Real-time compilation errors
- **Hover** - Module documentation and types
- **Execute** - Run pipelines from editor
- **Semantic tokens** - Rich syntax highlighting

## Architecture

```
Editor (VSCode, etc.)
    ↓ WebSocket
Constellation HTTP Server
    ↓
ConstellationLanguageServer
    ↓
LangCompiler + Constellation
```

## Connection

**Endpoint:** `ws://localhost:8080/lsp`

The LSP runs inside `ConstellationServer`, automatically available when the server starts.

```scala
ConstellationServer
  .builder(constellation, compiler)
  .withPort(8080)  // LSP at ws://localhost:8080/lsp
  .run
```

## Supported Methods

### Requests (client to server)

| Method | Description |
|--------|-------------|
| `initialize` | Initialize LSP session |
| `textDocument/completion` | Get autocomplete suggestions |
| `textDocument/hover` | Get hover documentation |
| `constellation/executePipeline` | Execute pipeline (custom) |

### Notifications (client to server)

| Method | Description |
|--------|-------------|
| `initialized` | Client finished init |
| `textDocument/didOpen` | File opened |
| `textDocument/didChange` | Content changed |
| `textDocument/didClose` | File closed |

### Notifications (server to client)

| Method | Description |
|--------|-------------|
| `textDocument/publishDiagnostics` | Send compilation errors |

## Message Format

JSON-RPC 2.0 over WebSocket:

### Request

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "textDocument/completion",
  "params": {
    "textDocument": {"uri": "file:///path/to/file.cst"},
    "position": {"line": 2, "character": 10}
  }
}
```

### Response

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "isIncomplete": false,
    "items": [
      {
        "label": "Uppercase",
        "kind": 3,
        "detail": "v1.0",
        "documentation": "Converts text to uppercase"
      }
    ]
  }
}
```

### Notification

```json
{
  "jsonrpc": "2.0",
  "method": "textDocument/publishDiagnostics",
  "params": {
    "uri": "file:///path/to/file.cst",
    "diagnostics": [
      {
        "range": {"start": {"line": 1, "character": 0}, "end": {"line": 1, "character": 10}},
        "severity": 1,
        "message": "Undefined function: NonExistentModule"
      }
    ]
  }
}
```

## Testing Manually

### Using websocat

```bash
# Install
brew install websocat  # macOS
# or: cargo install websocat

# Connect
websocat ws://localhost:8080/lsp
```

**Initialize:**

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"processId":null,"rootUri":null,"capabilities":{"textDocument":{}}}}
```

**Request completion:**

```json
{"jsonrpc":"2.0","id":2,"method":"textDocument/completion","params":{"textDocument":{"uri":"file:///test.cst"},"position":{"line":0,"character":0}}}
```

## Server Capabilities

Advertised in `initialize` response:

| Capability | Value |
|------------|-------|
| `completionProvider` | Yes |
| `hoverProvider` | Yes |
| `textDocumentSync` | Full |
| `executeCommandProvider` | Yes |
| `semanticTokensProvider` | Yes |

## Document Sync

Uses **full document sync** - entire content sent on every change.

For large files (>10KB), consider:
- Debouncing validation
- Caching parse results

## Module Structure

### lang-lsp Module

**Location:** `modules/lang-lsp/`

| File | Purpose |
|------|---------|
| `ConstellationLanguageServer.scala` | Main LSP implementation |
| `DocumentManager.scala` | Open document tracking |
| `protocol/JsonRpc.scala` | JSON-RPC 2.0 types |
| `protocol/LspTypes.scala` | Position, Range, Diagnostic |
| `protocol/LspMessages.scala` | Request/response types |

### http-api Integration

**File:** `modules/http-api/.../LspWebSocketHandler.scala`

Handles WebSocket lifecycle:
1. Accept connection at `/lsp`
2. Create LSP server instance
3. Route JSON-RPC messages
4. Send responses back

## Configuration

### Client Settings

```json
{
  "constellation.server.url": "ws://localhost:8080/lsp"
}
```

### Different Port

```scala
ConstellationServer
  .builder(constellation, compiler)
  .withPort(9000)  // LSP at ws://localhost:9000/lsp
  .run
```

## Performance

| Aspect | Behavior |
|--------|----------|
| Connection | Single persistent WebSocket |
| Module lookup | In-memory, fast |
| Compilation | On-demand for validation |
| Reconnection | Auto-reconnect on close |

## Diagnostics

Error types reported:

| Type | Example |
|------|---------|
| Undefined module | `Undefined function: Foo` |
| Type mismatch | `Expected String, got Int` |
| Undefined variable | `Undefined variable: x` |
| Syntax error | `Unexpected token` |

## Extending

### Add New Method

1. Define params in `LspMessages.scala`:

```scala
case class DefinitionParams(
  textDocument: TextDocumentIdentifier,
  position: Position
)
```

2. Handle in `ConstellationLanguageServer.scala`:

```scala
case "textDocument/definition" =>
  handleDefinition(request)
```

3. Update `ServerCapabilities`:

```scala
ServerCapabilities(
  definitionProvider = Some(true)
)
```

### Add Custom Command

1. Register in initialize:

```scala
executeCommandProvider = Some(ExecuteCommandOptions(
  commands = List("constellation.executePipeline", "constellation.formatPipeline")
))
```

2. Handle `workspace/executeCommand`

## Troubleshooting

### Connection Failed

1. Verify server: `curl http://localhost:8080/health`
2. Test WebSocket: `websocat ws://localhost:8080/lsp`
3. Check firewall settings

### No Completions

1. Check file has `.cst` extension
2. Verify language mode
3. Check Output panel for errors

### Diagnostics Stale

1. Save the file
2. Check server logs
3. Test via HTTP: `curl -X POST http://localhost:8080/compile`

## Resources

- [LSP Specification](https://microsoft.github.io/language-server-protocol/)
- [JSON-RPC 2.0](https://www.jsonrpc.org/specification)
- [VSCode Language Extensions](https://code.visualstudio.com/api/language-extensions/overview)

## Related

- [vscode.md](./vscode.md) - VSCode extension
- [dashboard.md](./dashboard.md) - Web dashboard
