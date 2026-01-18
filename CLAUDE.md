# CLAUDE.md - Development Rules for Constellation Engine

This file contains actionable rules that Claude must follow when working on this codebase.

## Development Commands

**ALWAYS use these commands. NEVER use raw sbt commands directly.**

| Task | Command |
|------|---------|
| Start dev environment | `make dev` |
| Start server only | `make server` |
| Start server (hot-reload) | `make server-rerun` |
| Run all tests | `make test` |
| Compile everything | `make compile` |
| Clean build | `make clean` |

**Windows (if make unavailable):**
```powershell
.\scripts\dev.ps1              # Full dev environment
.\scripts\dev.ps1 -ServerOnly  # Server only
```

**Why:** Raw `sbt` commands bypass project-specific configurations and are harder to maintain.

## Server Endpoints

- HTTP API: `http://localhost:8080`
- LSP WebSocket: `ws://localhost:8080/lsp`
- Health check: `GET /health`

## Code Conventions

### Module Creation

**ALWAYS use case classes for inputs/outputs, never tuples:**
```scala
// CORRECT
case class MyInput(text: String)
case class MyOutput(result: String)

val myModule = ModuleBuilder
  .metadata("MyModule", "Description", 1, 0)
  .implementationPure[MyInput, MyOutput] { input =>
    MyOutput(input.text.toUpperCase)
  }
  .build

// WRONG - Scala 3 doesn't support single-element tuples
.implementationPure[(String,), (String,)] { ... }
```

### Required Imports

```scala
import cats.effect.IO
import cats.implicits._  // Required for .traverse
import io.constellation._
import io.constellation.TypeSystem._
```

### Naming Conventions

- **Modules:** PascalCase (`Uppercase`, `WordCount`)
- **Variables:** camelCase (`val uppercase`, `val wordCount`)
- **Case classes:** PascalCase (`TextInput`, `WordCountOutput`)
- **Fields:** camelCase, must match constellation-lang variable names

### Module Name Matching

Module names in `ModuleBuilder.metadata()` must EXACTLY match usage in constellation-lang:
```scala
// Scala
ModuleBuilder.metadata("Uppercase", ...)  // Name: "Uppercase"

// constellation-lang
result = Uppercase(text)  // Must match exactly (case-sensitive)
```

## Dependency Rules

**CRITICAL: Modules can only depend on modules above them in this graph:**

```
core
  ↓
runtime ← lang-ast
            ↓
         lang-parser
            ↓
       lang-compiler
            ↓
    ┌───────┼───────┐
lang-stdlib  │    lang-lsp
             ↓
          http-api
             ↓
        example-app
```

**NEVER create circular dependencies.**

## Testing

Run tests before committing:
```bash
make test           # All tests
make test-core      # Core module
make test-compiler  # Compiler module
make test-lsp       # LSP module
```

## File Locations

| Component | Path |
|-----------|------|
| Type system | `modules/core/.../TypeSystem.scala` |
| ModuleBuilder | `modules/runtime/.../ModuleBuilder.scala` |
| Parser | `modules/lang-parser/.../ConstellationParser.scala` |
| Compiler | `modules/lang-compiler/.../DagCompiler.scala` |
| StdLib | `modules/lang-stdlib/.../StdLib.scala` |
| ExampleLib | `modules/example-app/.../ExampleLib.scala` |
| HTTP Server | `modules/http-api/.../ConstellationServer.scala` |
| LSP Server | `modules/lang-lsp/.../ConstellationLanguageServer.scala` |

## Adding New Functions

When adding a module to example-app:

1. Define the module in `modules/example-app/.../modules/`
2. Add FunctionSignature to `ExampleLib.scala`
3. Register in `allSignatures` and `allModules`
4. Restart server

## VSCode Extension

| Shortcut | Action |
|----------|--------|
| `Ctrl+Shift+R` | Run script |
| `Ctrl+Shift+D` | Show DAG visualization |
| `Ctrl+Space` | Autocomplete |
| `F5` | Launch extension debug |

## Common Pitfalls to Avoid

1. **Don't use `ConstellationImpl.create`** - Use `ConstellationImpl.init`
2. **Don't forget `cats.implicits._`** - Required for `.traverse`
3. **Don't mismatch field names** - Case class fields must match constellation-lang variable names
4. **Don't skip testing** - Run `make test` before committing

## Documentation

- Full architecture: See `llm.md`
- Contributing guide: See `CONTRIBUTING.md`
- TODO/roadmap: See `TODO.md`
