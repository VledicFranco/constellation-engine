# LSP WebSocket Protocol

The Constellation Engine provides an LSP (Language Server Protocol) endpoint via WebSocket for IDE integration.

## Connection

**Endpoint:** `ws://localhost:8080/lsp`

Connect using any WebSocket client. Messages follow the LSP specification with JSON-RPC 2.0 framing.

## Message Format

All messages use the LSP Content-Length header format:

```
Content-Length: <length>\r\n\r\n<json-payload>
```

Where `<length>` is the byte length of the JSON payload in UTF-8.

## Supported Methods

### Lifecycle

| Method | Type | Description |
|--------|------|-------------|
| `initialize` | Request | Initialize the language server |
| `initialized` | Notification | Client finished initialization |
| `shutdown` | Request | Prepare server for exit |
| `exit` | Notification | Exit the server |

### Text Document Synchronization

| Method | Type | Description |
|--------|------|-------------|
| `textDocument/didOpen` | Notification | Document opened |
| `textDocument/didChange` | Notification | Document content changed |
| `textDocument/didClose` | Notification | Document closed |

### Language Features

| Method | Type | Description |
|--------|------|-------------|
| `textDocument/completion` | Request | Get completion items |
| `textDocument/hover` | Request | Get hover information |

### Custom Methods (Constellation-specific)

| Method | Type | Description |
|--------|------|-------------|
| `constellation/executePipeline` | Request | Execute the current script |
| `constellation/getInputSchema` | Request | Get input schema for script |
| `constellation/getDagStructure` | Request | Get DAG visualization data |
| `constellation/stepStart` | Request | Start step-through execution |
| `constellation/stepNext` | Request | Execute next batch |
| `constellation/stepContinue` | Request | Run to completion |
| `constellation/stepStop` | Request | Stop debug session |

## Example Messages

### Initialize Request

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "processId": 1234,
    "capabilities": {}
  }
}
```

### Initialize Response

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "capabilities": {
      "textDocumentSync": 1,
      "completionProvider": {
        "triggerCharacters": ["(", ",", " ", "."]
      },
      "hoverProvider": true
    }
  }
}
```

### Document Open Notification

```json
{
  "jsonrpc": "2.0",
  "method": "textDocument/didOpen",
  "params": {
    "textDocument": {
      "uri": "file:///path/to/script.cst",
      "languageId": "constellation",
      "version": 1,
      "text": "in text: String\nresult = Uppercase(text)\nout result"
    }
  }
}
```

### Completion Request

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "textDocument/completion",
  "params": {
    "textDocument": {
      "uri": "file:///path/to/script.cst"
    },
    "position": {
      "line": 1,
      "character": 10
    }
  }
}
```

### Completion Response

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "isIncomplete": false,
    "items": [
      {
        "label": "Uppercase",
        "kind": 3,
        "detail": "Uppercase(text: String) -> String",
        "documentation": "Converts text to uppercase",
        "insertText": "Uppercase()"
      }
    ]
  }
}
```

### Execute Pipeline Request

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "constellation/executePipeline",
  "params": {
    "uri": "file:///path/to/script.cst",
    "inputs": {
      "text": "hello world"
    }
  }
}
```

### Execute Pipeline Response

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "success": true,
    "outputs": {
      "result": "HELLO WORLD"
    },
    "executionTimeMs": 15
  }
}
```

### Get DAG Structure Request

```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "constellation/getDagStructure",
  "params": {
    "uri": "file:///path/to/script.cst"
  }
}
```

### Get DAG Structure Response

```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "result": {
    "success": true,
    "dag": {
      "modules": {
        "uuid-1": {
          "name": "Uppercase",
          "consumes": {"text": "String"},
          "produces": {"result": "String"}
        }
      },
      "data": {
        "uuid-2": {"name": "text", "cType": "String"},
        "uuid-3": {"name": "result", "cType": "String"}
      },
      "inEdges": [["uuid-2", "uuid-1"]],
      "outEdges": [["uuid-1", "uuid-3"]],
      "declaredOutputs": ["result"]
    }
  }
}
```

### Step-Through Execution

Start a debug session:

```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "method": "constellation/stepStart",
  "params": {
    "uri": "file:///path/to/script.cst",
    "inputs": {"text": "hello"}
  }
}
```

Response includes session ID:

```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "result": {
    "success": true,
    "sessionId": "session-abc123",
    "totalBatches": 3,
    "initialState": {
      "currentBatch": 0,
      "totalBatches": 3,
      "batchNodes": ["uuid-1"],
      "completedNodes": [],
      "pendingNodes": ["uuid-2", "uuid-3"]
    }
  }
}
```

Step to next batch:

```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "method": "constellation/stepNext",
  "params": {
    "sessionId": "session-abc123"
  }
}
```

## Server-Sent Notifications

The server sends diagnostics when documents are validated:

```json
{
  "jsonrpc": "2.0",
  "method": "textDocument/publishDiagnostics",
  "params": {
    "uri": "file:///path/to/script.cst",
    "diagnostics": [
      {
        "range": {
          "start": {"line": 1, "character": 9},
          "end": {"line": 1, "character": 18}
        },
        "severity": 1,
        "source": "constellation-lang",
        "message": "Undefined function: Upppercase"
      }
    ]
  }
}
```

## Error Responses

Errors follow JSON-RPC 2.0 format:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32601,
    "message": "Method not found: unknownMethod"
  }
}
```

Standard error codes:
- `-32700` - Parse error
- `-32600` - Invalid request
- `-32601` - Method not found
- `-32602` - Invalid params
- `-32603` - Internal error

## Client Implementation Notes

1. **Keep-alive**: Empty messages are silently ignored (can be used for keep-alive pings)
2. **Text sync**: Use `textDocumentSync: 1` (full document sync)
3. **Diagnostics**: Published automatically after `didOpen` and `didChange`
4. **Completion triggers**: `(`, `,`, ` `, `.` trigger completion suggestions
