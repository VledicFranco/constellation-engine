# Contributing to Constellation Engine

Thank you for your interest in contributing to Constellation Engine! This guide will help you get started with development.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Development Workflow](#development-workflow)
4. [Project Structure](#project-structure)
5. [Running Tests](#running-tests)
6. [VSCode Setup](#vscode-setup)
7. [Common Tasks](#common-tasks)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites

Before you begin, ensure you have:

- **Java 17+** (JDK 17 or later)
- **SBT 1.9+** (Scala Build Tool)
- **Node.js 18+** and npm
- **VSCode** (recommended for extension development)

### Verify Installation

```bash
java -version    # Should show Java 17+
sbt --version    # Should show SBT 1.9+
node --version   # Should show Node 18+
npm --version
```

---

## Quick Start

### 1. Clone and Install Dependencies

```bash
git clone https://github.com/your-org/constellation-engine.git
cd constellation-engine

# Install all dependencies
make install
# Or manually:
sbt update
cd vscode-extension && npm install && cd ..
```

### 2. Start Development Environment

**Option A: Using Make (recommended)**
```bash
make dev    # Starts server + TypeScript watch
```

**Option B: Using Scripts**
```bash
# Windows PowerShell
.\scripts\dev.ps1

# Unix/Mac
./scripts/dev.sh
```

**Option C: Manual**
```bash
# Terminal 1: Start server
sbt "exampleApp/runMain io.constellation.examples.app.server.ExampleServer"

# Terminal 2: Watch TypeScript
cd vscode-extension && npm run watch
```

### 3. Launch VSCode Extension

1. Open VSCode in the project root
2. Press `F5` to launch the extension in a new window
3. Open a `.cst` file and start coding!

---

## Development Workflow

### Make Commands

| Command | Description |
|---------|-------------|
| `make dev` | Start full dev environment (server + watch) |
| `make server` | Start HTTP/LSP server only |
| `make watch` | Watch Scala and recompile on changes |
| `make ext-watch` | Watch TypeScript and recompile |
| `make test` | Run all tests |
| `make compile` | Compile all modules |
| `make clean` | Clean build artifacts |

### Hot Reload (Server Auto-Restart)

For automatic server restart on code changes:

```bash
make server-rerun
# Or: sbt "~exampleApp/reStart"
```

This requires the sbt-revolver plugin (already configured).

### VSCode Tasks

Press `Ctrl+Shift+P` → "Tasks: Run Task" to see available tasks:

- **Compile All** - Compile Scala modules
- **Run Tests** - Run all tests
- **Start Server (ExampleLib)** - Start the HTTP/LSP server
- **Watch Compile** - Watch mode for Scala
- **Full Dev Setup** - Start server + extension watch

---

## Project Structure

```
constellation-engine/
├── modules/
│   ├── core/           # Type system (CType, CValue)
│   ├── runtime/        # Execution engine, ModuleBuilder
│   ├── lang-ast/       # AST definitions
│   ├── lang-parser/    # Parser (cats-parse)
│   ├── lang-compiler/  # Type checker, DAG compiler
│   ├── lang-stdlib/    # Standard library functions
│   ├── lang-lsp/       # Language Server Protocol
│   ├── http-api/       # HTTP server (http4s)
│   └── example-app/    # Example application
├── vscode-extension/   # VSCode extension (TypeScript)
├── docs/               # Documentation
├── scripts/            # Development scripts
├── Makefile            # Build automation
└── build.sbt           # SBT configuration
```

### Module Dependencies

```
core → runtime → lang-compiler → http-api
       ↓                ↓
    lang-ast      lang-stdlib, lang-lsp, example-app
       ↓
   lang-parser
```

**Rule:** Modules can only depend on modules above them. No circular dependencies.

---

## Running Tests

### All Tests
```bash
make test
# Or: sbt test
```

### Module-Specific Tests
```bash
make test-core       # Test core module
make test-compiler   # Test compiler
make test-lsp        # Test LSP server
make test-parser     # Test parser
```

### Watch Mode (Continuous Testing)
```bash
sbt "~test"          # Re-run tests on changes
sbt "~testQuick"     # Only re-run failed tests
```

---

## VSCode Setup

### Recommended Extensions

- **Scala (Metals)** - Scala language support
- **Scala Syntax** - Syntax highlighting

### Settings

The project includes `.vscode/tasks.json` with pre-configured tasks.

### Debugging the Extension

1. Open the `vscode-extension/` folder in VSCode
2. Press `F5` to launch
3. A new VSCode window opens with the extension loaded
4. Set breakpoints in TypeScript files

### Debugging the Scala Backend

1. Start the server with debug port:
   ```bash
   sbt -jvm-debug 5005 "exampleApp/run"
   ```
2. Attach a debugger to port 5005

---

## Common Tasks

### Adding a New Module Function

1. **Define the module** in `modules/example-app/src/main/scala/.../modules/`:
   ```scala
   case class MyInput(value: String)
   case class MyOutput(result: String)

   val myModule: Module.Uninitialized = ModuleBuilder
     .metadata("MyModule", "Description", 1, 0)
     .implementationPure[MyInput, MyOutput] { input =>
       MyOutput(input.value.toUpperCase)
     }
     .build
   ```

2. **Add signature to ExampleLib** in `ExampleLib.scala`:
   ```scala
   private val myModuleSig = FunctionSignature(
     name = "MyModule",
     params = List("value" -> SemanticType.SString),
     returns = SemanticType.SString,
     moduleName = "MyModule"
   )
   ```

3. **Register in `allSignatures`** and **add module to `allModules`**

4. **Restart server** to pick up changes

### Testing a Constellation-Lang Program

Create a `.cst` file:
```
in text: String
result = MyModule(text)
out result
```

Run with `Ctrl+Shift+R` in VSCode or via HTTP:
```bash
curl -X POST http://localhost:8080/compile \
  -H "Content-Type: application/json" \
  -d '{"source": "in x: String\nresult = Uppercase(x)\nout result", "dagName": "test"}'
```

---

## Troubleshooting

### Server won't start

1. Check if port 8080 is in use:
   ```bash
   netstat -an | grep 8080
   ```
2. Kill existing Java processes:
   ```bash
   # Windows
   taskkill /F /IM java.exe

   # Unix
   pkill -f "java.*constellation"
   ```

### SBT compilation errors

1. Clean and recompile:
   ```bash
   make clean
   make compile
   ```
2. Update dependencies:
   ```bash
   sbt update
   ```

### Extension not connecting to LSP

1. Ensure server is running on port 8080
2. Check VSCode output panel for errors
3. Verify WebSocket URL in settings: `ws://localhost:8080/lsp`

### TypeScript compilation errors

```bash
cd vscode-extension
rm -rf node_modules out
npm install
npm run compile
```

---

## Code Style

- **Scala:** Follow standard Scala 3 conventions
- **TypeScript:** Follow existing patterns in the codebase
- **Commits:** Use conventional commit messages
- **Tests:** Add tests for new functionality

---

## Getting Help

- Check existing issues on GitHub
- Review documentation in `/docs`
- See `llm.md` for comprehensive codebase guide

---

Happy coding! 🚀
