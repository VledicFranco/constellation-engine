---
title: "VSCode Extension"
sidebar_position: 2
description: "Visual Studio Code extension for Constellation language support"
---

# VSCode Extension Guide

The Constellation VSCode extension provides a rich editing experience for `.cst` files with LSP-powered features including autocomplete, diagnostics, hover information, and pipeline execution.

## Installation

### From Marketplace (Recommended)

Search for "Constellation Language Support" in the VSCode Extensions view (`Ctrl+Shift+X` / `Cmd+Shift+X`).

### Manual Installation (VSIX)

1. Navigate to the extension directory:
   ```bash
   cd vscode-extension
   ```

2. Install dependencies and compile:
   ```bash
   npm install
   npm run compile
   ```

3. Package the extension:
   ```bash
   npm run package
   ```

   This creates `constellation-lang-0.4.0.vsix` in the current directory.

4. Install the VSIX file in VSCode:

   **Via Command Line:**
   ```bash
   code --install-extension constellation-lang-0.4.0.vsix
   ```

   **Via VSCode UI:**
   - Open VSCode
   - Press `Ctrl+Shift+P` / `Cmd+Shift+P`
   - Type "Extensions: Install from VSIX"
   - Select the `.vsix` file

5. Reload VSCode when prompted

### Development Mode

For extension development or testing:

1. Open the `vscode-extension` directory in VSCode:
   ```bash
   cd constellation-engine/vscode-extension
   code .
   ```

2. Install dependencies:
   ```bash
   npm install
   npm run compile
   ```

3. Press `F5` to launch the Extension Development Host
   - A new VSCode window opens with the extension loaded
   - Set breakpoints in `src/extension.ts` for debugging

## Configuration Options

Configure the extension via VSCode Settings (`Ctrl+,` / `Cmd+,`) or `.vscode/settings.json`:

### Server URL

```json
{
  "constellation.server.url": "ws://localhost:8080/lsp"
}
```

The WebSocket URL of the Constellation Language Server. Change this when:
- Running the server on a different port
- Connecting to a remote server
- Using a multi-agent setup

### DAG Layout Direction

```json
{
  "constellation.dagLayoutDirection": "TB"
}
```

Default layout direction for DAG visualization:
- `TB` - Top to bottom (default)
- `LR` - Left to right

### All Settings Reference

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `constellation.server.url` | string | `ws://localhost:8080/lsp` | WebSocket URL of the LSP server |
| `constellation.dagLayoutDirection` | enum | `TB` | Default DAG layout (`TB` or `LR`) |

## Keyboard Shortcuts

| Shortcut | Windows/Linux | Mac | Action |
|----------|---------------|-----|--------|
| Run Script | `Ctrl+Shift+R` | `Cmd+Shift+R` | Execute the current `.cst` file |
| Show DAG | `Ctrl+Shift+D` | `Cmd+Shift+D` | Open DAG visualization in browser |
| Autocomplete | `Ctrl+Space` | `Ctrl+Space` | Trigger autocomplete suggestions |
| Command Palette | `Ctrl+Shift+P` | `Cmd+Shift+P` | Open command palette |
| Quick Fix | `Ctrl+.` | `Cmd+.` | Show quick fixes for diagnostics |
| Go to Definition | `F12` | `F12` | Navigate to definition |
| Peek Definition | `Alt+F12` | `Option+F12` | Peek definition inline |

### Customizing Shortcuts

Open Keyboard Shortcuts (`Ctrl+K Ctrl+S`) and search for "Constellation" to rebind:

- `constellation.runScript`
- `constellation.showDagVisualization`
- `constellation.executePipeline`

## Debugging Workflow

### Prerequisites

1. **Start the Constellation Server:**
   ```bash
   make server
   # Or: sbt "exampleApp/runMain io.constellation.examples.app.TextProcessingApp"
   ```

2. **Verify Server Health:**
   ```bash
   curl http://localhost:8080/health
   # Expected: {"status":"ok"}
   ```

3. **Open a `.cst` File:**
   The extension activates automatically when you open a `.cst` file.

### Debugging a Pipeline

1. **Write your pipeline:**
   ```constellation
   in text: String

   cleaned = Trim(text)
   upper = Uppercase(cleaned)
   wordCount = WordCount(upper)

   out upper
   out wordCount
   ```

2. **Check for Errors:**
   - Diagnostics appear as red squiggly lines
   - Hover over errors for detailed messages
   - Check the Problems panel (`Ctrl+Shift+M`)

3. **Execute the Pipeline:**
   - Press `Ctrl+Shift+R` / `Cmd+Shift+R`
   - Enter input values when prompted (JSON format)
   - View results in the Output panel

4. **Visualize the DAG:**
   - Press `Ctrl+Shift+D` / `Cmd+Shift+D`
   - Opens the web dashboard with DAG visualization

### Debugging Extension Issues

1. **View Extension Logs:**
   - Open Output panel: `View > Output`
   - Select "Constellation Language Server" from dropdown
   - Look for connection errors or LSP messages

2. **Check Server Connection:**
   ```bash
   # Verify WebSocket endpoint
   curl http://localhost:8080/health
   ```

3. **Reload Extension:**
   - Press `Ctrl+Shift+P` / `Cmd+Shift+P`
   - Type "Developer: Reload Window"

## Remote Server Configuration

### Connecting to a Remote Server

For remote development or shared server scenarios:

```json
{
  "constellation.server.url": "ws://remote-server.example.com:8080/lsp"
}
```

### SSH Tunnel Setup

For secure connections through SSH:

1. **Create SSH Tunnel:**
   ```bash
   ssh -L 8080:localhost:8080 user@remote-server.example.com
   ```

2. **Use localhost in settings:**
   ```json
   {
     "constellation.server.url": "ws://localhost:8080/lsp"
   }
   ```

### TLS/WSS Connections

For production servers with TLS:

```json
{
  "constellation.server.url": "wss://api.example.com/lsp"
}
```

Ensure the server is configured with valid TLS certificates.

### Authentication

If the server requires API key authentication:

1. The WebSocket handshake uses the same auth as HTTP endpoints
2. Configure API keys in the server:
   ```scala
   ConstellationServer
     .builder(constellation, compiler)
     .withAuth(AuthConfig.fromEnv)
     .run
   ```

3. The extension currently does not support passing auth headers (planned feature)

## Multi-Root Workspace Support

The extension works in multi-root workspaces with multiple Constellation projects.

### Configuration Per Folder

Each workspace folder can have its own `.vscode/settings.json`:

```
workspace/
├── project-a/
│   ├── .vscode/
│   │   └── settings.json  # constellation.server.url = "ws://localhost:8081/lsp"
│   └── scripts/
│       └── pipeline.cst
├── project-b/
│   ├── .vscode/
│   │   └── settings.json  # constellation.server.url = "ws://localhost:8082/lsp"
│   └── scripts/
│       └── pipeline.cst
```

### Multi-Agent Setup

For parallel development with multiple agents:

| Agent | Port | Settings |
|-------|------|----------|
| Main | 8080 | `ws://localhost:8080/lsp` |
| Agent 1 | 8081 | `ws://localhost:8081/lsp` |
| Agent 2 | 8082 | `ws://localhost:8082/lsp` |

Each VSCode window connects to its dedicated server instance.

### Workspace Settings Example

`.code-workspace` file:

```json
{
  "folders": [
    { "path": "project-a" },
    { "path": "project-b" }
  ],
  "settings": {
    "constellation.server.url": "ws://localhost:8080/lsp"
  }
}
```

## Troubleshooting Common Issues

### Extension Not Activating

**Symptoms:**
- No syntax highlighting
- No autocomplete or diagnostics

**Solutions:**
1. Verify the file has a `.cst` extension
2. Check that the extension is installed and enabled
3. Reload the window (`Developer: Reload Window`)
4. Check the Extensions view for errors

### Cannot Connect to Server

**Symptoms:**
- "WebSocket connection failed" in Output panel
- No diagnostics appearing

**Solutions:**

1. **Verify server is running:**
   ```bash
   curl http://localhost:8080/health
   ```

2. **Check server URL in settings:**
   ```json
   {
     "constellation.server.url": "ws://localhost:8080/lsp"
   }
   ```

3. **Check for port conflicts:**
   ```bash
   # Windows
   netstat -ano | findstr :8080

   # Unix/macOS
   lsof -i :8080
   ```

4. **Restart the server:**
   ```bash
   make server
   ```

### No Autocomplete Suggestions

**Symptoms:**
- `Ctrl+Space` shows no completions
- Module names not suggested

**Solutions:**

1. **Wait for LSP initialization:**
   The first completion request may take 1-2 seconds

2. **Check that modules are registered:**
   ```bash
   curl http://localhost:8080/api/v1/modules
   ```

3. **Verify server has your modules:**
   Ensure your custom modules are registered in the server application

### Diagnostics Not Updating

**Symptoms:**
- Old errors persist after fixing
- New errors not appearing

**Solutions:**

1. **Trigger a save:** `Ctrl+S` / `Cmd+S`
2. **Edit the file:** Make a small change to trigger recompilation
3. **Restart the server:** The server may have crashed

### Slow Performance

**Symptoms:**
- Autocomplete takes several seconds
- Typing feels laggy

**Solutions:**

1. **Check server performance:**
   ```bash
   curl http://localhost:8080/health/detail
   ```

2. **Reduce file complexity:**
   Large files with many modules may be slow

3. **Check server logs:**
   ```bash
   # Look for errors in the server console
   ```

### npm Install Fails

**Symptoms:**
- Dependency errors during `npm install`
- Module not found errors

**Solutions:**

1. **Check Node.js version:**
   ```bash
   node --version  # Should be 18.x or higher
   ```

2. **Clear npm cache:**
   ```bash
   npm cache clean --force
   rm -rf node_modules
   npm install
   ```

3. **Install vsce globally:**
   ```bash
   npm install -g @vscode/vsce
   ```

### VSIX Packaging Fails

**Symptoms:**
- `npm run package` fails
- Missing files in package

**Solutions:**

1. **Compile first:**
   ```bash
   npm run compile
   ```

2. **Check for TypeScript errors:**
   ```bash
   npx tsc --noEmit
   ```

3. **Verify `.vscodeignore` exists**

## Features Reference

### Syntax Highlighting

TextMate grammar provides syntax highlighting for:

- Keywords (`in`, `out`, `if`, `then`, `else`, `match`)
- Types (`String`, `Int`, `Boolean`, `List<T>`, etc.)
- Module calls (PascalCase identifiers)
- Comments (`# single line`)
- Strings and numbers

### Semantic Tokens

Enhanced highlighting powered by the LSP:

| Token Type | Description | Example |
|------------|-------------|---------|
| `function` | Module calls | `Uppercase(text)` |
| `type` | Type annotations | `String`, `List<Int>` |
| `parameter` | Input parameters | `in text: String` |
| `variable` | Variable bindings | `result = ...` |
| `namespace` | Imports | `import std` |

### Autocomplete

Triggered by `Ctrl+Space` or typing:

- **Module names:** All registered modules with signatures
- **Keywords:** `in`, `out`, `if`, `match`, etc.
- **Type names:** `String`, `Int`, `Boolean`, etc.
- **Variables:** Previously declared variables in scope

### Hover Information

Hover over identifiers to see:

- **Modules:** Name, version, description, input/output types
- **Variables:** Inferred type
- **Types:** Full type definition

### Diagnostics

Real-time error checking:

- **Syntax errors:** Invalid syntax highlighted
- **Type errors:** Type mismatches
- **Undefined references:** Unknown modules or variables
- **Unused variables:** Warnings for unused bindings

### Commands

Available via Command Palette (`Ctrl+Shift+P`):

| Command | Description |
|---------|-------------|
| `Constellation: Execute Current Pipeline` | Run with input prompt |
| `Constellation: Run Script` | Run the current file |
| `Constellation: Open Dashboard (DAG Visualization)` | Open dashboard in browser |
| `Constellation: Show Performance Statistics` | Display LSP performance stats |

## Editor Integration

### Editor Title Bar

When editing a `.cst` file, the title bar shows:

- **Play button** - Run Script (`Ctrl+Shift+R`)
- **Browser button** - Open DAG Visualization (`Ctrl+Shift+D`)

### Context Menu

Right-click in a `.cst` file for:

- Run Script
- Open Dashboard (DAG Visualization)

### Problem Matcher

Compilation errors are automatically parsed and shown in the Problems panel with:

- File path
- Line and column number
- Error message
- Quick navigation to error location

## Related Documentation

- [Dashboard Guide](./dashboard.md) - Web dashboard features
- [LSP Integration](./lsp-integration.md) - WebSocket protocol details
- [Troubleshooting](./troubleshooting.md) - More debugging tips
