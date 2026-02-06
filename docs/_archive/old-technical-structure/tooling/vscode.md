# VSCode Extension

> **Path**: `docs/tooling/vscode.md`
> **Parent**: [tooling/](./README.md)

IDE integration for `.cst` files with LSP-powered features.

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

## Features

| Feature | Description |
|---------|-------------|
| Syntax highlighting | Rich highlighting via TextMate grammar |
| Autocomplete | Module names and keywords |
| Diagnostics | Real-time error checking |
| Hover documentation | Module info and type details |
| Pipeline execution | Run scripts from editor |
| DAG visualization | Open dashboard view |

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+Shift+R` | Run script |
| `Ctrl+Shift+D` | Show DAG visualization |
| `Ctrl+Space` | Trigger autocomplete |

**Mac:** Use `Cmd` instead of `Ctrl`.

## Commands

| Command | Title |
|---------|-------|
| `constellation.runScript` | Run Script |
| `constellation.showDagVisualization` | Open Dashboard |
| `constellation.executePipeline` | Execute Current Pipeline |
| `constellation.showPerformanceStats` | Show Performance Statistics |

Access via Command Palette (`Ctrl+Shift+P`).

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
// Agent 1
{ "constellation.server.url": "ws://localhost:8081/lsp" }

// Agent 2
{ "constellation.server.url": "ws://localhost:8082/lsp" }
```

## Autocomplete

**Trigger:** Type module name prefix + `Ctrl+Space`

```constellation
in text: String
result = Up  # Press Ctrl+Space here
```

**Shows:**
- `Uppercase (v1.0)` - Converts text to uppercase
- Other modules starting with "Up"

**Keywords:** `in`, `out`

## Diagnostics

Real-time error checking as you type:

```constellation
in text: String
result = NonExistentModule(text)  # Red squiggly
out result
```

**Error types:**
- Undefined modules
- Type mismatches
- Undefined variables
- Syntax errors

## Hover Information

Hover over module names for documentation:

```markdown
**WordCount** (v1.0)

Counts the number of words in text

**Tags:** text, analysis
```

## Pipeline Execution

1. Open `.cst` file
2. `Ctrl+Shift+R` or Command Palette > "Run Script"
3. Enter JSON inputs when prompted
4. View results in notification/output

**Example input:** `{"text": "hello world"}`

## Semantic Tokens

Enhanced syntax highlighting for:

| Token Type | Description |
|------------|-------------|
| `function` | Module/function calls |
| `type` | Type names |
| `parameter` | Input parameters |
| `variable` | Variables |
| `namespace` | Imports |

## File Association

Files with `.cst` extension automatically use Constellation language mode.

**Manual override:** Click language mode in status bar, select "Constellation".

## Troubleshooting

### Extension Not Loading

1. Check extension is installed (Extensions panel)
2. Verify activation events in package.json
3. Open Developer Tools (Help > Toggle Developer Tools)
4. Reinstall extension

### No Autocomplete

1. Verify file has `.cst` extension
2. Check language mode shows "Constellation"
3. Reload window: `Ctrl+Shift+P` > "Reload Window"
4. Check Output panel: View > Output > "Constellation Language Server"

### Connection Failed

1. Verify server is running: `curl http://localhost:8080/health`
2. Check WebSocket endpoint: `websocat ws://localhost:8080/lsp`
3. Verify `constellation.server.url` setting
4. Check firewall settings

### Diagnostics Not Updating

1. Save the file
2. Check LSP server logs
3. Test compilation: `curl -X POST http://localhost:8080/compile -d '...'`

## Development

### Build Commands

```bash
npm run compile    # Build once
npm run watch      # Watch mode
npm run test       # Run tests
npm run test:unit  # Unit tests only
```

### Extension Structure

```
vscode-extension/
├── src/
│   ├── extension.ts       # Entry point
│   ├── panels/            # UI panels
│   └── test/              # Tests
├── syntaxes/
│   └── constellation.tmLanguage.json
├── language-configuration.json
└── package.json
```

## Related

- [lsp.md](./lsp.md) - LSP protocol details
- [dashboard.md](./dashboard.md) - Web dashboard
