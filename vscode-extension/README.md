# Constellation Language Support for VSCode

Language support for Constellation orchestration DSL with integrated Language Server Protocol (LSP) features.

## Features

- **Syntax Highlighting** - Rich syntax highlighting for `.cst` files
- **Autocomplete** - Module name completion and keyword suggestions
- **Diagnostics** - Real-time compilation error checking
- **Hover Information** - View module documentation on hover
- **Execute Pipeline** - Run pipelines directly from the editor

## Quick Start

Get up and running in 3 minutes:

```bash
# 1. Clone the repo
git clone git@github.com:VledicFranco/constellation-engine.git
cd constellation-engine

# 2. Install and run the VSCode extension
cd vscode-extension
npm install && npm run compile
# Press F5 in VSCode to launch Extension Development Host

# 3. In a separate terminal, start the server
cd ..
sbt "exampleApp/runMain io.constellation.examples.app.TextProcessingApp"

# 4. Create a test.cst file in the Extension Development Host window
# Type: in text: String
# Type: result = Up   (then press Ctrl+Space to see autocomplete!)
```

See [Installation](#installation) for detailed instructions.

## Installation

### Prerequisites

Before installing the extension, make sure you have:

- **VSCode** 1.75.0 or higher
- **Node.js** 18.x or higher and npm
- **Constellation Engine** repository cloned locally
- **SBT** installed (for running the server)

### Step 1: Clone the Repository

If you haven't already, clone the Constellation Engine repository:

```bash
git clone git@github.com:VledicFranco/constellation-engine.git
cd constellation-engine
```

### Step 2: Install the VSCode Extension

There are two ways to install the extension: **Development Mode** (recommended for testing) or **Install as VSIX** (for regular use).

#### Option A: Development Mode (Recommended)

1. Open the constellation-engine repository in VSCode:
   ```bash
   code .
   ```

2. Open the VSCode extension directory in a terminal:
   ```bash
   cd vscode-extension
   ```

3. Install Node.js dependencies:
   ```bash
   npm install
   ```

4. Compile the TypeScript code:
   ```bash
   npm run compile
   ```

5. Press `F5` in VSCode (or Run → Start Debugging)
   - This opens a new "Extension Development Host" window
   - The extension is automatically loaded in this window
   - You can set breakpoints and debug the extension

6. In the Extension Development Host window, open any `.cst` file or create a new one

**Note:** This method is perfect for development and testing. The extension reloads automatically when you make changes.

#### Option B: Install as VSIX Package

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

   This creates `constellation-lang-0.1.0.vsix` in the current directory.

4. Install the VSIX file in VSCode:

   **Via Command Line:**
   ```bash
   code --install-extension constellation-lang-0.1.0.vsix
   ```

   **Via VSCode UI:**
   - Open VSCode
   - Press `Cmd+Shift+P` (Mac) or `Ctrl+Shift+P` (Windows/Linux)
   - Type "Extensions: Install from VSIX"
   - Select the `constellation-lang-0.1.0.vsix` file

5. Reload VSCode when prompted

### Step 3: Start the Constellation Server

The extension requires a running Constellation server with LSP support.

1. Open a terminal in the constellation-engine directory:
   ```bash
   cd constellation-engine
   ```

2. Start the example application server:
   ```bash
   sbt "exampleApp/runMain io.constellation.examples.app.TextProcessingApp"
   ```

   You should see:
   ```
   🚀 Initializing Constellation Engine...
   📦 Registering custom modules...

   ✅ Available custom modules:
      • Uppercase (v1.0)
      • Lowercase (v1.0)
      • Trim (v1.0)
      ... (and more)

   🌐 Starting HTTP API server on port 8080...
   ```

3. The LSP server is now available at `ws://localhost:8080/lsp`

**Keep this terminal running** while using the extension.

### Step 4: Verify Installation

1. In VSCode, create a new file called `test.cst`

2. Type the following:
   ```constellation
   in text: String
   result = Up
   ```

3. Press `Ctrl+Space` after typing "Up"
   - You should see autocomplete suggestions for "Uppercase"
   - This confirms the extension is working!

### Troubleshooting Installation

**Extension not activating:**
- Check that the file has a `.cst` extension
- Open the Output panel: View → Output → Select "Constellation Language Server"
- Look for connection errors

**Cannot connect to server:**
- Verify the server is running: `curl http://localhost:8080/health`
- Check the server URL in settings matches `ws://localhost:8080/lsp`
- Make sure port 8080 is not blocked

**npm install fails:**
- Check Node.js version: `node --version` (should be 18.x or higher)
- Clear npm cache: `npm cache clean --force`
- Delete `node_modules` and try again

**VSIX packaging fails:**
- Make sure you ran `npm run compile` first
- Install vsce globally: `npm install -g @vscode/vsce`

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

### Complete Workflow Example

Here's a complete example showing all features in action:

**1. Create a new file `my-pipeline.cst`:**

```constellation
# Data analysis pipeline
in numbers: List<Long>
in threshold: Long

# Filter numbers above threshold
filtered = FilterGreaterThan(numbers, threshold)

# Calculate statistics
total = SumList(filtered)
avg = Average(filtered)
maximum = Max(filtered)

# Output results
out total
out avg
out maximum
```

**2. Watch autocomplete in action:**
- Type `fil` and press `Ctrl+Space`
- Select `FilterGreaterThan` from the suggestions
- The extension shows: "FilterGreaterThan (v1.0) - Filters numbers greater than threshold"

**3. See diagnostics:**
- Type `result = NonExistentModule(numbers)`
- A red squiggly line appears
- Hover to see: "Undefined function: NonExistentModule"

**4. Get hover information:**
- Hover over `Average`
- See documentation: "**Average** (v1.0) - Calculates the average of a list of numbers"

**5. Execute the pipeline:**
- Press `Cmd+Shift+P` → "Constellation: Execute Current Pipeline"
- Enter inputs: `{"numbers": [1, 5, 10, 15, 20], "threshold": 8}`
- See success message with results

### Available Modules

The example server includes these modules (hover over them in VSCode to see full documentation):

**Text Processing:**
- `Uppercase`, `Lowercase`, `Trim`, `Replace`
- `WordCount`, `TextLength`, `Contains`
- `SplitLines`, `Split`

**Data Processing:**
- `SumList`, `Average`, `Max`, `Min`
- `FilterGreaterThan`, `MultiplyEach`
- `Range`, `FormatNumber`

### Keyboard Shortcuts

| Action | Windows/Linux | Mac |
|--------|---------------|-----|
| Autocomplete | `Ctrl+Space` | `Ctrl+Space` |
| Command Palette | `Ctrl+Shift+P` | `Cmd+Shift+P` |
| Quick Fix | `Ctrl+.` | `Cmd+.` |
| Go to Definition | `F12` | `F12` |
| Peek Definition | `Alt+F12` | `Option+F12` |

### Tips and Tricks

**Faster Development:**
1. Keep the server terminal visible to see compilation messages
2. Use autocomplete liberally - press `Ctrl+Space` whenever unsure
3. Hover over modules to see their input/output types
4. Check the Output panel if something isn't working

**Testing Pipelines:**
1. Start with simple inputs to verify correctness
2. Use the Execute command to test without leaving VSCode
3. Check error messages carefully - they show line and column numbers
4. Build complex pipelines incrementally

**Common Patterns:**
```constellation
# Pattern 1: Sequential transformation
in text: String
step1 = Trim(text)
step2 = Uppercase(step1)
step3 = WordCount(step2)
out step3

# Pattern 2: Multiple outputs
in data: List<Long>
total = SumList(data)
avg = Average(data)
out total
out avg

# Pattern 3: Filtering and transformation
in numbers: List<Long>
in min: Long
filtered = FilterGreaterThan(numbers, min)
doubled = MultiplyEach(filtered, 2)
out doubled
```

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

## Using with Your Own Constellation Server

The extension works with any Constellation server that includes the LSP module. Here's how to use it with your own custom modules:

### 1. Add LSP Dependencies

In your `build.sbt`, include the LSP and HTTP API modules:

```scala
lazy val myApp = (project in file("my-app"))
  .dependsOn(runtime, langCompiler, langLsp, httpApi)
  .settings(
    name := "my-constellation-app",
    // ... your settings
  )
```

### 2. Create Your Application

```scala
package com.mycompany.myapp

import cats.effect.{IO, IOApp}
import cats.implicits._
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.http.ConstellationServer

object MyApp extends IOApp.Simple {
  def run: IO[Unit] = {
    for {
      // Initialize engine
      constellation <- ConstellationImpl.init

      // Register YOUR custom modules
      _ <- myCustomModules.traverse(constellation.setModule)

      // Create compiler (use LangCompiler.empty or StdLib.compiler)
      compiler = LangCompiler.empty

      // Start HTTP server with LSP
      _ <- ConstellationServer
        .builder(constellation, compiler)
        .withPort(8080)  // LSP will be at ws://localhost:8080/lsp
        .run
    } yield ()
  }
}
```

### 3. Run Your Server

```bash
sbt "myApp/run"
```

### 4. Use the Extension

The VSCode extension will now provide autocomplete, diagnostics, and hover information for YOUR custom modules!

**No changes to the extension needed** - it automatically discovers available modules from the server.

### 5. (Optional) Configure Custom Server URL

If using a different port or host:

1. Open VSCode Settings (Cmd+, or Ctrl+,)
2. Search for "Constellation"
3. Set "Constellation: Server URL" to your server (e.g., `ws://localhost:9000/lsp`)

Or add to `.vscode/settings.json`:
```json
{
  "constellation.server.url": "ws://localhost:9000/lsp"
}
```

## Support

For issues and feature requests, please file an issue on the [Constellation Engine repository](https://github.com/VledicFranco/constellation-engine).
