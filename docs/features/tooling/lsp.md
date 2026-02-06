# LSP Protocol

> **Path**: `docs/features/tooling/lsp.md`
> **Parent**: [tooling/](./README.md)

Language Server Protocol implementation over WebSocket for IDE integration.

---

## Overview

The Constellation LSP server provides IDE features for `.cst` files:

- **Autocomplete** - Module names, keywords, and type-aware suggestions
- **Diagnostics** - Real-time compilation errors and warnings
- **Hover** - Module documentation and type information
- **Go-to-definition** - Navigate to module definitions
- **Semantic tokens** - Rich syntax highlighting
- **Execute** - Run pipelines from the editor

LSP is the **single protocol** for all IDE integration. By implementing LSP once, Constellation supports every editor that speaks LSP: VSCode, Neovim, Emacs, Sublime Text, JetBrains IDEs.

---

## Architecture

```
┌────────────────────────────────────────┐
│     Editor (VSCode, Neovim, etc.)      │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │      LSP Client (extension)     │   │
│  └─────────────────────────────────┘   │
└────────────────────────────────────────┘
                    │
                    │ WebSocket (JSON-RPC 2.0)
                    ▼
┌────────────────────────────────────────┐
│     ConstellationServer (http-api)     │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │     LspWebSocketHandler         │   │
│  └─────────────────────────────────┘   │
└────────────────────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────┐
│   ConstellationLanguageServer          │
│   (lang-lsp)                           │
│                                         │
│  ┌──────────┐ ┌──────────┐            │
│  │ Document │ │Completion│            │
│  │ Manager  │ │  Trie    │            │
│  └──────────┘ └──────────┘            │
│                                         │
│  ┌──────────┐ ┌──────────┐            │
│  │ Semantic │ │ Debouncer│            │
│  │ Tokens   │ │          │            │
│  └──────────┘ └──────────┘            │
└────────────────────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────┐
│     LangCompiler + Constellation       │
│     (compilation, type checking)       │
└────────────────────────────────────────┘
```

---

## Connection

**Endpoint:** `ws://localhost:8080/lsp`

The LSP runs inside `ConstellationServer`, automatically available when the server starts.

```scala
ConstellationServer
  .builder(constellation, compiler)
  .withPort(8080)  // LSP at ws://localhost:8080/lsp
  .run
```

---

## Supported Methods

### Requests (client to server)

| Method | Description |
|--------|-------------|
| `initialize` | Initialize LSP session |
| `shutdown` | Prepare for exit |
| `textDocument/completion` | Get autocomplete suggestions |
| `textDocument/hover` | Get hover documentation |
| `textDocument/definition` | Go to definition |
| `textDocument/semanticTokens/full` | Get semantic tokens |
| `constellation/executePipeline` | Execute pipeline (custom) |

### Notifications (client to server)

| Method | Description |
|--------|-------------|
| `initialized` | Client finished initialization |
| `exit` | Exit server |
| `textDocument/didOpen` | File opened |
| `textDocument/didChange` | Content changed |
| `textDocument/didClose` | File closed |
| `textDocument/didSave` | File saved |

### Notifications (server to client)

| Method | Description |
|--------|-------------|
| `textDocument/publishDiagnostics` | Send compilation errors |
| `window/logMessage` | Send log messages |
| `window/showMessage` | Show message to user |

---

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
        "range": {
          "start": {"line": 1, "character": 0},
          "end": {"line": 1, "character": 10}
        },
        "severity": 1,
        "code": "E101",
        "message": "Undefined function: NonExistentModule"
      }
    ]
  }
}
```

---

## Server Capabilities

Advertised in `initialize` response:

| Capability | Value | Description |
|------------|-------|-------------|
| `completionProvider` | Yes | Autocomplete support |
| `hoverProvider` | Yes | Hover documentation |
| `definitionProvider` | Yes | Go-to-definition |
| `textDocumentSync` | Full | Full document sync on change |
| `executeCommandProvider` | Yes | Custom commands |
| `semanticTokensProvider` | Yes | Rich syntax highlighting |

---

## Autocomplete

Triggered by typing or `Ctrl+Space`:

**Module completions:**
```constellation
in text: String
result = Up  # Ctrl+Space → Uppercase, Update, ...
```

**Keyword completions:**
- `in` - Input declaration
- `out` - Output declaration
- `with` - Resilience options

**Type-aware suggestions:**
Completions are filtered based on expected types when possible.

---

## Diagnostics

Real-time error checking as you type:

| Error Type | Example |
|------------|---------|
| Undefined module | `Undefined function: Foo` |
| Type mismatch | `Expected String, got Int` |
| Undefined variable | `Undefined variable: x` |
| Syntax error | `Unexpected token` |

Diagnostics are debounced (100ms) to avoid overwhelming the editor during rapid typing.

---

## Hover Information

Hover over module names for documentation:

```markdown
**WordCount** (v1.0)

Counts the number of words in text

**Input:** {text: String}
**Output:** {count: Int}

**Tags:** text, analysis
```

---

## Semantic Tokens

Enhanced syntax highlighting beyond TextMate grammar:

| Token Type | Description |
|------------|-------------|
| `function` | Module/function calls |
| `type` | Type names |
| `parameter` | Input parameters |
| `variable` | Variables |
| `namespace` | Imports |
| `keyword` | Language keywords |
| `string` | String literals |
| `number` | Numeric literals |

---

## Document Sync

Uses **full document sync** - entire content sent on every change.

For large files (>10KB), the server:
- Debounces validation (100ms)
- Caches parse results
- Only recompiles changed sections when possible

---

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

---

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `lang-lsp` | Main LSP implementation | `ConstellationLanguageServer.scala` |
| `lang-lsp` | Open document tracking | `DocumentManager.scala` |
| `lang-lsp` | Completion prefix matching | `CompletionTrie.scala` |
| `lang-lsp` | Request debouncing | `Debouncer.scala` |
| `lang-lsp` | Semantic token generation | `SemanticTokenProvider.scala`, `SemanticTokenTypes.scala` |
| `lang-lsp` | Type display formatting | `TypeFormatter.scala` |
| `lang-lsp` | With-clause completions | `WithClauseCompletions.scala` |
| `lang-lsp` | Option validation diagnostics | `diagnostics/OptionsDiagnostics.scala` |
| `lang-lsp` | JSON-RPC types | `protocol/JsonRpc.scala` |
| `lang-lsp` | LSP type definitions | `protocol/LspTypes.scala` |
| `lang-lsp` | Request/response types | `protocol/LspMessages.scala` |
| `http-api` | WebSocket handler | `LspWebSocketHandler.scala` |

---

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

### Multi-Agent Setup

Each agent uses a different port:

```json
// Agent 1
{ "constellation.server.url": "ws://localhost:8081/lsp" }

// Agent 2
{ "constellation.server.url": "ws://localhost:8082/lsp" }
```

---

## Performance

| Aspect | Behavior | Target |
|--------|----------|--------|
| Connection | Single persistent WebSocket | N/A |
| Module lookup | In-memory trie, fast | < 5ms |
| Completion response | Prefix matching | < 50ms |
| Diagnostics publish | Debounced | < 100ms |
| Reconnection | Auto-reconnect on close | N/A |

---

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
  commands = List(
    "constellation.executePipeline",
    "constellation.formatPipeline"
  )
))
```

2. Handle `workspace/executeCommand`

---

## Troubleshooting

### Connection Failed

1. Verify server: `curl http://localhost:8080/health`
2. Test WebSocket: `websocat ws://localhost:8080/lsp`
3. Check firewall settings
4. Verify port matches `constellation.server.url`

### No Completions

1. Check file has `.cst` extension
2. Verify language mode is "Constellation"
3. Check Output panel for errors
4. Reload window: `Ctrl+Shift+P` > "Reload Window"

### Diagnostics Not Updating

1. Save the file to trigger recompilation
2. Check server logs for errors
3. Test via HTTP: `curl -X POST http://localhost:8080/compile`

### Slow Response

1. Check file size (large files are slower)
2. Check server CPU/memory usage
3. Verify debouncing is working (not every keystroke triggers recompile)

---

## Resources

- [LSP Specification](https://microsoft.github.io/language-server-protocol/)
- [JSON-RPC 2.0](https://www.jsonrpc.org/specification)
- [VSCode Language Extensions](https://code.visualstudio.com/api/language-extensions/overview)

---

## Related

- [vscode.md](./vscode.md) - VSCode extension
- [dashboard.md](./dashboard.md) - Web dashboard
