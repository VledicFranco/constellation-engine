# Constellation Language Server Protocol (LSP) Integration

This document explains how to use the Language Server Protocol integration for constellation-lang, enabling IDE features like autocomplete, diagnostics, and hover information.

## Overview

The Constellation Engine provides a full Language Server Protocol (LSP) implementation over WebSockets, allowing developers to write constellation-lang programs with IDE support in VSCode and other editors.

**Features:**
- **Autocomplete** - Module names and keywords
- **Diagnostics** - Real-time compilation error checking
- **Hover Information** - Module documentation and type information
- **Execute Pipeline** - Run pipelines directly from the editor
- **Syntax Highlighting** - Rich syntax highlighting for `.cst` files

## Architecture

```
VSCode Extension (TypeScript)
    ↓ WebSocket
Constellation HTTP Server (Scala)
    ↓
ConstellationLanguageServer
    ↓
LangCompiler + Constellation
```

The LSP server runs inside your HTTP API server, accessible via WebSocket at `/lsp`.

## Quick Start

### 1. Start the Server

Start your Constellation application with HTTP API and LSP support:

```bash
sbt "exampleApp/runMain io.constellation.examples.app.TextProcessingApp"
```

This starts the server on `http://localhost:8080` with LSP at `ws://localhost:8080/lsp`.

### 2. Install VSCode Extension

```bash
cd vscode-extension
npm install
npm run compile
```

### 3. Open VSCode

Press `F5` to launch the Extension Development Host, or package and install:

```bash
npm run package
code --install-extension constellation-lang-0.1.0.vsix
```

### 4. Write Constellation Programs

Create a file with `.cst` extension:

```constellation
# my-pipeline.cst
in text: String

# Autocomplete will suggest: Uppercase, Lowercase, Trim, etc.
result = Uppercase(text)

out result
```

## LSP Features

### Autocomplete

**Trigger:** Type the beginning of a module name and press `Ctrl+Space`

**Example:**
```constellation
in text: String
result = Up[CTRL+SPACE]
```

**Shows:**
- `Uppercase (v1.0)` - Converts text to uppercase
- All other modules starting with "Up"

**Keyword Autocomplete:**
- `in` - Input declaration
- `out` - Output declaration

### Diagnostics

**Real-time Error Checking:**

As you type, the LSP validates your program and shows errors:

```constellation
in text: String
result = NonExistentModule(text)  # Red squiggly line
out result
```

**Error Message:**
```
Undefined function: NonExistentModule
```

**Error Types:**
- Undefined modules
- Type mismatches
- Undefined variables
- Invalid syntax

### Hover Information

**Trigger:** Hover your mouse over a module name

**Example:**
```constellation
result = WordCount(text)  # Hover over "WordCount"
```

**Shows:**
```markdown
**WordCount** (v1.0)

Counts the number of words in text

**Tags:** text, analysis
```

### Execute Pipeline

**Trigger:** Command Palette → "Constellation: Execute Current Pipeline"

**Flow:**
1. Opens input prompt for JSON values
2. Sends request to LSP server
3. Compiles and executes pipeline
4. Shows success/error message

**Example:**
```constellation
in text: String
cleaned = Trim(text)
uppercased = Uppercase(cleaned)
out uppercased
```

**Input:** `{"text": "  hello world  "}`
**Result:** Success message with output

## Integration with Your Application

### Adding LSP to Your HTTP Server

The example application already includes LSP support. Here's how it works:

**File:** `modules/example-app/src/main/scala/io/constellation/examples/app/TextProcessingApp.scala`

```scala
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.http.ConstellationServer

for {
  constellation <- ConstellationImpl.init
  _ <- registerCustomModules(constellation)
  compiler = LangCompiler.empty

  // Server automatically includes LSP WebSocket at /lsp
  _ <- ConstellationServer
    .builder(constellation, compiler)
    .withPort(8080)
    .run
} yield ()
```

The `ConstellationServer` automatically provides:
- REST API at `/compile`, `/execute`, `/modules`, etc.
- WebSocket LSP at `/lsp`

### Custom LSP Configuration

**Change LSP Endpoint:**

In your VSCode settings:

```json
{
  "constellation.server.url": "ws://your-host:your-port/lsp"
}
```

**Different Port:**

```scala
ConstellationServer
  .builder(constellation, compiler)
  .withPort(9000)  // LSP will be at ws://localhost:9000/lsp
  .run
```

## LSP Protocol Details

### Supported Methods

**Requests (client → server → client):**
- `initialize` - Initialize LSP session
- `textDocument/completion` - Get autocomplete suggestions
- `textDocument/hover` - Get hover information
- `constellation/executePipeline` - Execute pipeline (custom)

**Notifications (client → server, no response):**
- `initialized` - Client finished initialization
- `textDocument/didOpen` - File opened
- `textDocument/didChange` - File content changed
- `textDocument/didClose` - File closed

**Notifications (server → client):**
- `textDocument/publishDiagnostics` - Send compilation errors

### Message Format

**JSON-RPC 2.0 over WebSocket:**

```json
// Request
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "textDocument/completion",
  "params": {
    "textDocument": {"uri": "file:///path/to/file.cst"},
    "position": {"line": 2, "character": 10}
  }
}

// Response
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

## Module Structure

### lang-lsp Module

**Location:** `modules/lang-lsp/`

**Key Files:**
- `protocol/JsonRpc.scala` - JSON-RPC 2.0 protocol types
- `protocol/LspTypes.scala` - LSP core types (Position, Range, Diagnostic, etc.)
- `protocol/LspMessages.scala` - LSP message types (requests, responses)
- `DocumentManager.scala` - Manages open documents
- `ConstellationLanguageServer.scala` - Main LSP server implementation

**Dependencies:**
- `runtime` - For Constellation API
- `lang-compiler` - For compiling and validating programs

### http-api Module

**LSP Integration:**

**File:** `modules/http-api/src/main/scala/io/constellation/http/LspWebSocketHandler.scala`

Provides WebSocket endpoint that:
1. Accepts WebSocket connections at `/lsp`
2. Creates LSP server instance
3. Routes JSON-RPC messages to LSP server
4. Sends responses back to client

## Testing LSP

### Manual Testing with WebSocket Client

Use `websocat` or similar tool:

```bash
# Install websocat
brew install websocat  # macOS
# or: cargo install websocat

# Connect to LSP
websocat ws://localhost:8080/lsp
```

**Send Initialize Request:**

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"processId":null,"rootUri":null,"capabilities":{"textDocument":{}}}}
```

**Send Completion Request:**

```json
{"jsonrpc":"2.0","id":2,"method":"textDocument/completion","params":{"textDocument":{"uri":"file:///test.cst"},"position":{"line":0,"character":0}}}
```

### Unit Testing

**Location:** `modules/lang-lsp/src/test/scala/`

Test the LSP server directly:

```scala
import io.constellation.lsp.ConstellationLanguageServer
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler

"ConstellationLanguageServer" should "provide completions" in {
  val result = (for {
    constellation <- ConstellationImpl.init
    _ <- constellation.setModule(uppercaseModule)
    compiler = LangCompiler.empty
    server <- ConstellationLanguageServer.create(
      constellation,
      compiler,
      _ => IO.unit
    )
    request = Request(
      id = RequestId.NumberId(1),
      method = "textDocument/completion",
      params = Some(completionParams.asJson)
    )
    response <- server.handleRequest(request)
  } yield response).unsafeRunSync()

  // Assert response contains "Uppercase"
}
```

## Extending the LSP

### Adding New LSP Features

**Example: Add "Go to Definition"**

1. **Add to LspMessages.scala:**

```scala
case class DefinitionParams(
  textDocument: TextDocumentIdentifier,
  position: Position
)
```

2. **Handle in ConstellationLanguageServer.scala:**

```scala
def handleRequest(request: Request): IO[Response] = {
  request.method match {
    // ... existing cases ...
    case "textDocument/definition" =>
      handleDefinition(request)
  }
}

private def handleDefinition(request: Request): IO[Response] = {
  // Find module definition, return Location
}
```

3. **Update ServerCapabilities:**

```scala
ServerCapabilities(
  // ... existing capabilities ...
  definitionProvider = Some(true)
)
```

### Adding Custom Commands

**Example: Add "Format Pipeline"**

1. **Register in initialize response:**

```scala
executeCommandProvider = Some(ExecuteCommandOptions(
  commands = List(
    "constellation.executePipeline",
    "constellation.formatPipeline"  // New command
  )
))
```

2. **Handle command:**

```scala
case "workspace/executeCommand" =>
  handleExecuteCommand(request)

private def handleExecuteCommand(request: Request): IO[Response] = {
  // Parse command name and params
  // Execute formatting logic
  // Return result
}
```

## Troubleshooting

### WebSocket Connection Issues

**Symptom:** Extension can't connect to server

**Solutions:**
- Verify server is running: `curl http://localhost:8080/health`
- Check WebSocket endpoint: `websocat ws://localhost:8080/lsp`
- Check firewall settings
- Try different port in configuration

### No Autocomplete

**Symptom:** Typing doesn't show suggestions

**Solutions:**
- Ensure file has `.cst` extension
- Check language mode (bottom right of VSCode) shows "Constellation"
- Reload window: `Cmd+Shift+P` → "Reload Window"
- Check Output panel for errors: View → Output → "Constellation Language Server"

### Diagnostics Not Updating

**Symptom:** Errors don't appear or don't clear

**Solutions:**
- Save the file (diagnostics update on change)
- Check LSP server logs
- Verify compilation works: `curl -X POST http://localhost:8080/compile -d '...'`

### VSCode Extension Not Loading

**Symptom:** Extension doesn't activate

**Solutions:**
- Check extension is installed: Extensions panel
- Verify activationEvents in package.json
- Look for errors: Help → Toggle Developer Tools → Console
- Reinstall extension

## Performance Considerations

### Document Sync

The LSP uses **full document sync** - the entire file content is sent on every change. For large files (>10KB), consider:

- Using incremental sync (requires protocol changes)
- Debouncing validation
- Caching parse results

### WebSocket Connection

- Single WebSocket per client
- Persistent connection (auto-reconnect on close)
- Messages are JSON-RPC over WebSocket (not HTTP polling)

### Module Loading

- Modules loaded once at server startup
- Autocomplete queries are fast (in-memory lookup)
- Compilation happens on-demand for validation

## Future Enhancements

Potential features to add:

1. **Code Actions** - Quick fixes for common errors
2. **Rename** - Rename variables across file
3. **Find References** - Find all usages of a variable
4. **Format Document** - Auto-format constellation-lang code
5. **Semantic Tokens** - Advanced syntax highlighting
6. **Signature Help** - Parameter hints for module calls
7. **Document Symbols** - Outline view of pipeline
8. **Workspace Symbols** - Search across multiple files

## Resources

- **LSP Specification:** https://microsoft.github.io/language-server-protocol/
- **JSON-RPC 2.0:** https://www.jsonrpc.org/specification
- **VSCode Language Extensions:** https://code.visualstudio.com/api/language-extensions/overview
- **http4s WebSockets:** https://http4s.org/v0.23/docs/websocket.html

## Support

For questions or issues with LSP integration:
- Check the troubleshooting section above
- Review the example application code
- File an issue on the Constellation Engine repository
