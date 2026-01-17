# Constellation Language Support for VSCode

Language support for Constellation orchestration DSL with integrated Language Server Protocol (LSP) features.

## Features

- **Syntax Highlighting** - Rich syntax highlighting for `.cst` files
- **Autocomplete** - Module name completion and keyword suggestions
- **Diagnostics** - Real-time compilation error checking
- **Hover Information** - View module documentation on hover
- **Execute Pipeline** - Run pipelines directly from the editor

## Installation

### Prerequisites

1. Start a Constellation HTTP server with LSP support:

```bash
sbt "exampleApp/runMain io.constellation.examples.app.TextProcessingApp"
```

This will start the HTTP API server on port 8080 with WebSocket LSP support at `ws://localhost:8080/lsp`.

### Install Extension

1. Navigate to the VSCode extension directory:

```bash
cd vscode-extension
```

2. Install dependencies:

```bash
npm install
```

3. Compile the extension:

```bash
npm run compile
```

4. Package the extension (optional):

```bash
npm run package
```

5. Install in VSCode:
   - Press `F5` to open a new VSCode window with the extension loaded (for development)
   - Or use `code --install-extension constellation-lang-0.1.0.vsix` (if packaged)

## Usage

### Creating a Constellation Program

1. Create a new file with `.cst` extension
2. Start writing your pipeline:

```constellation
# Example: Text processing pipeline
in rawText: String

# Transform text
cleaned = Trim(rawText)
uppercased = Uppercase(cleaned)

# Analyze text
wordCount = WordCount(uppercased)

# Output results
out uppercased
out wordCount
```

### Features in Action

**Autocomplete:**
- Type the beginning of a module name and press `Ctrl+Space` to see suggestions
- Module names show version and description

**Diagnostics:**
- Compilation errors appear as you type
- Red squiggly lines indicate errors
- Hover over errors to see details

**Hover Information:**
- Hover over module names to see documentation
- Shows module version, description, and tags

**Execute Pipeline:**
- Open a `.cst` file
- Press `Cmd+Shift+P` (Mac) or `Ctrl+Shift+P` (Windows/Linux)
- Type "Constellation: Execute Current Pipeline"
- Enter input values as JSON when prompted

## Configuration

Configure the language server URL in VSCode settings:

```json
{
  "constellation.server.url": "ws://localhost:8080/lsp"
}
```

## Development

### Building from Source

```bash
npm install
npm run compile
```

### Watching for Changes

```bash
npm run watch
```

### Debugging

1. Open the extension directory in VSCode
2. Press `F5` to launch the Extension Development Host
3. Open a `.cst` file in the new window
4. Set breakpoints in `src/extension.ts`

## Troubleshooting

**Extension not activating:**
- Check that `.cst` files are recognized
- Ensure the language ID is "constellation"

**No autocomplete or diagnostics:**
- Verify the Constellation server is running on `localhost:8080`
- Check the WebSocket connection in the Output panel
- Try reconnecting by reloading the window

**WebSocket connection failed:**
- Make sure the HTTP server is running:
  ```bash
  sbt "exampleApp/runMain io.constellation.examples.app.TextProcessingApp"
  ```
- Check the server URL in settings
- Verify port 8080 is not blocked by firewall

## License

Same license as Constellation Engine

## Support

For issues and feature requests, please file an issue on the Constellation Engine repository.
