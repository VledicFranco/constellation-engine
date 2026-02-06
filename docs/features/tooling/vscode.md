# VSCode Extension

> **Path**: `docs/features/tooling/vscode.md`
> **Parent**: [tooling/](./README.md)

IDE integration for `.cst` files with LSP-powered features.

---

## Overview

The Constellation VSCode extension provides:

- **Syntax highlighting** - Rich highlighting via TextMate grammar
- **Autocomplete** - Module names, keywords, and type-aware suggestions
- **Diagnostics** - Real-time error checking via LSP
- **Hover documentation** - Module info and type details
- **Pipeline execution** - Run scripts directly from the editor
- **DAG visualization** - Open dashboard view for current file

The extension is a **thin LSP client**. All language intelligence comes from the `ConstellationLanguageServer` via WebSocket. This ensures consistent behavior across all editors that support LSP.

---

## Installation

### From Source

```bash
cd vscode-extension
npm install
npm run compile
```

**Debug mode:** Press `F5` to launch Extension Development Host.

**Package and install:**

```bash
npm run package
code --install-extension constellation-lang-0.4.0.vsix
```

### Prerequisites

1. Constellation server running with LSP support
2. VSCode 1.75.0 or later

---

## Features

| Feature | Description | LSP Method |
|---------|-------------|------------|
| Syntax highlighting | Rich highlighting via TextMate + semantic tokens | `textDocument/semanticTokens/full` |
| Autocomplete | Module names, keywords, type suggestions | `textDocument/completion` |
| Diagnostics | Real-time error checking | `textDocument/publishDiagnostics` |
| Hover documentation | Module info and type details | `textDocument/hover` |
| Go-to-definition | Navigate to module definitions | `textDocument/definition` |
| Pipeline execution | Run scripts from editor | `constellation/executePipeline` |
| DAG visualization | Open dashboard | Custom command |

---

## Keyboard Shortcuts

| Shortcut | Action | Command |
|----------|--------|---------|
| `Ctrl+Shift+R` | Run script | `constellation.runScript` |
| `Ctrl+Shift+D` | Show DAG visualization | `constellation.showDagVisualization` |
| `Ctrl+Space` | Trigger autocomplete | `editor.action.triggerSuggest` |

**Mac:** Use `Cmd` instead of `Ctrl`.

---

## Commands

Access via Command Palette (`Ctrl+Shift+P`):

| Command | Title |
|---------|-------|
| `constellation.runScript` | Run Script |
| `constellation.showDagVisualization` | Open Dashboard |
| `constellation.executePipeline` | Execute Current Pipeline |
| `constellation.showPerformanceStats` | Show Performance Statistics |
| `constellation.restartLanguageServer` | Restart Language Server |

---

## Configuration

### Settings

```json
{
  "constellation.server.url": "ws://localhost:8080/lsp",
  "constellation.dagLayoutDirection": "TB"
}
```

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `constellation.server.url` | string | `ws://localhost:8080/lsp` | LSP WebSocket URL |
| `constellation.dagLayoutDirection` | enum | `TB` | DAG layout direction (TB/LR) |

### Multi-Agent Setup

Each agent uses a different port:

```json
// Agent 1 workspace settings
{ "constellation.server.url": "ws://localhost:8081/lsp" }

// Agent 2 workspace settings
{ "constellation.server.url": "ws://localhost:8082/lsp" }
```

---

## Autocomplete

**Trigger:** Type module name prefix + `Ctrl+Space`

```constellation
in text: String
result = Up  # Press Ctrl+Space here
```

**Shows:**
- `Uppercase (v1.0)` - Converts text to uppercase
- Other modules starting with "Up"

**Keywords:** `in`, `out`, `with`

**Contextual suggestions:**
- After `with`, suggests resilience options: `retry`, `timeout`, `fallback`, `cache`
- After `:`, suggests type names: `String`, `Int`, `Boolean`, `List`, `Map`

---

## Diagnostics

Real-time error checking as you type:

```constellation
in text: String
result = NonExistentModule(text)  # Red squiggly underline
out result
```

**Error types displayed:**
- Undefined modules
- Type mismatches
- Undefined variables
- Syntax errors

**Error display:**
- Red squiggly underline at error location
- Error message in Problems panel
- Error message on hover

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

Hover over types for type information:

```markdown
**String**

UTF-8 text value
```

---

## Semantic Tokens

Enhanced syntax highlighting beyond TextMate grammar:

| Token Type | Example | Color (default theme) |
|------------|---------|----------------------|
| `function` | `Uppercase` | Yellow |
| `type` | `String` | Green |
| `parameter` | `text` in `in text` | Orange |
| `variable` | `result` | Blue |
| `keyword` | `in`, `out`, `with` | Purple |

Semantic tokens provide more accurate highlighting than TextMate alone, especially for:
- Distinguishing module calls from variables
- Highlighting type annotations correctly
- Identifying resilience option keywords

---

## Pipeline Execution

1. Open `.cst` file
2. `Ctrl+Shift+R` or Command Palette > "Run Script"
3. Enter JSON inputs when prompted
4. View results in notification/output panel

**Example input dialog:** `{"text": "hello world"}`

**Result display:**
- Success: Notification with output values
- Failure: Error message with details

---

## File Association

Files with `.cst` extension automatically use Constellation language mode.

**Manual override:**
1. Click language mode in status bar (bottom-right)
2. Select "Constellation"

---

## Extension Structure

```
vscode-extension/
├── src/
│   ├── extension.ts              # Entry point, activation
│   ├── panels/
│   │   └── ScriptRunnerPanel.ts  # Script execution UI
│   ├── utils/
│   │   ├── dagLayoutUtils.ts     # DAG layout helpers
│   │   ├── performanceTracker.ts # Performance monitoring
│   │   └── webviewUtils.ts       # Webview helpers
│   └── test/                     # Test suites
├── syntaxes/
│   └── constellation.tmLanguage.json  # TextMate grammar
├── language-configuration.json    # Bracket matching, comments
└── package.json                   # Extension manifest
```

---

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `vscode-extension` | Extension entry point | `src/extension.ts` |
| `vscode-extension` | Script execution UI | `src/panels/ScriptRunnerPanel.ts` |
| `vscode-extension` | DAG layout utilities | `src/utils/dagLayoutUtils.ts` |
| `vscode-extension` | Performance tracking | `src/utils/performanceTracker.ts` |
| `vscode-extension` | TextMate grammar | `syntaxes/constellation.tmLanguage.json` |
| `vscode-extension` | Language config | `language-configuration.json` |
| `lang-lsp` | LSP server (all intelligence) | `ConstellationLanguageServer.scala` |
| `http-api` | WebSocket transport | `LspWebSocketHandler.scala` |

---

## Development

### Build Commands

```bash
npm run compile    # Build once
npm run watch      # Watch mode for development
npm run test       # Run all tests
npm run test:unit  # Unit tests only
npm run lint       # Run ESLint
npm run package    # Create VSIX package
```

### Debug Extension

1. Open `vscode-extension/` folder in VSCode
2. Press `F5` to launch Extension Development Host
3. Open a `.cst` file in the new window
4. Set breakpoints in `src/extension.ts`

### Run Tests

```bash
# Unit tests
npm run test:unit

# All tests (requires server running)
npm run test
```

---

## Troubleshooting

### Extension Not Loading

1. Check extension is installed (Extensions panel)
2. Verify activation events in `package.json`
3. Open Developer Tools (`Help > Toggle Developer Tools`)
4. Check Output panel for "Constellation Language Server" errors
5. Reinstall extension

### No Autocomplete

1. Verify file has `.cst` extension
2. Check language mode shows "Constellation" in status bar
3. Check LSP connection (see below)
4. Reload window: `Ctrl+Shift+P` > "Reload Window"

### LSP Connection Failed

1. Verify server is running: `curl http://localhost:8080/health`
2. Check WebSocket endpoint: `websocat ws://localhost:8080/lsp`
3. Verify `constellation.server.url` setting matches server port
4. Check firewall settings
5. Check Output panel: `View > Output > "Constellation Language Server"`

### Diagnostics Not Updating

1. Save the file to trigger recompilation
2. Check LSP server is connected (status bar)
3. Check server logs for errors
4. Restart language server: `Ctrl+Shift+P` > "Restart Language Server"

### Slow Performance

1. Check file size (very large files may be slow)
2. Check server CPU/memory usage
3. Disable other extensions temporarily to isolate
4. Check Output panel for timing information

---

## Related

- [lsp.md](./lsp.md) - LSP protocol details
- [dashboard.md](./dashboard.md) - Web dashboard
