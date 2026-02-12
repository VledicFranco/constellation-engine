---
name: developer
description: >
  Constellation Engine developer context. Loads architecture, type system, module patterns,
  project structure, and development commands. Use when working on any development task:
  bug fixes, new features, refactoring, testing, code review, or exploring the codebase.
  Activates when the user asks to implement, fix, debug, test, or modify code.
allowed-tools: Read, Grep, Glob, Bash, Edit, Write
---

# Constellation Engine Developer Context

You are working on **Constellation Engine**, a type-safe pipeline orchestration framework for Scala 3. Before starting any task, internalize this context.

---

## 1. What Is Constellation Engine?

A framework that lets users:
1. **Define** processing modules in Scala using `ModuleBuilder` API
2. **Compose** pipelines in `constellation-lang` DSL (`.cst` files)
3. **Execute** pipelines with automatic dependency resolution and parallelization
4. **Expose** pipelines via HTTP API or embed in Scala applications

**Tech stack:** Scala 3.3.4 | Cats Effect 3 | http4s | Circe | cats-parse

### Processing Pipeline

```
Source (.cst) --> Parser --> AST --> Type Checker --> Typed IR --> DAG Compiler --> Execution DAG --> Runtime --> Results
```

Each stage catches different errors: syntax errors at parse, type errors at type-check, dependency errors at compile, runtime failures at execute.

---

## 2. Module Dependency Graph

**CRITICAL: modules can only depend on modules above them. NEVER create circular dependencies.**

```
core (foundation - no dependencies)
  |-> runtime
  |     |-> cache-memcached
  |     |-> doc-generator
  |
  +-> lang-ast
        +-> lang-parser
              +-> lang-compiler
                    |-> lang-stdlib
                    |-> lang-lsp
                    |-> lang-cli
                    |-> http-api
                    +-> example-app
```

**Layering:**
1. **Core** (`core`): Pure type definitions, no side effects
2. **Execution** (`runtime`): Module execution, caching, pools
3. **Language** (`lang-*`): Parsing, compilation, type checking
4. **Service** (`http-api`, `lang-lsp`, `lang-cli`): External interfaces
5. **Application** (`example-app`): User-facing modules

---

## 3. Key File Locations

### Core Entry Points

| Component | Path |
|-----------|------|
| Type system (CType/CValue) | `modules/core/src/main/scala/io/constellation/TypeSystem.scala` |
| Module builder API | `modules/runtime/src/main/scala/io/constellation/ModuleBuilder.scala` |
| Runtime execution | `modules/runtime/src/main/scala/io/constellation/Runtime.scala` |
| Constellation core | `modules/runtime/src/main/scala/io/constellation/Constellation.scala` |
| Parser | `modules/lang-parser/src/main/scala/io/constellation/lang/parser/ConstellationParser.scala` |
| Lang compiler | `modules/lang-compiler/src/main/scala/io/constellation/lang/LangCompiler.scala` |
| DAG compiler | `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/DagCompiler.scala` |
| Type checker | `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/TypeChecker.scala` |
| IR generator | `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/IRGenerator.scala` |
| Semantic types | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/SemanticType.scala` |
| StdLib registry | `modules/lang-stdlib/src/main/scala/io/constellation/stdlib/StdLib.scala` |
| HTTP server | `modules/http-api/src/main/scala/io/constellation/http/ConstellationServer.scala` |
| HTTP routes | `modules/http-api/src/main/scala/io/constellation/http/ConstellationRoutes.scala` |
| LSP server | `modules/lang-lsp/src/main/scala/io/constellation/lsp/ConstellationLanguageServer.scala` |
| CLI entry | `modules/lang-cli/src/main/scala/io/constellation/cli/Main.scala` |
| Example modules | `modules/example-app/src/main/scala/io/constellation/examples/app/ExampleLib.scala` |

### Issue Triage: Where to Look

| Issue Type | Module | Key Files |
|------------|--------|-----------|
| Type errors, CValue/CType bugs | `core` | `TypeSystem.scala` |
| Module execution, ModuleBuilder | `runtime` | `ModuleBuilder.scala`, `Runtime.scala` |
| Parse errors, syntax issues | `lang-parser` | `ConstellationParser.scala` |
| Type checking, semantic errors | `lang-compiler` | `TypeChecker.scala`, `SemanticType.scala` |
| DAG compilation issues | `lang-compiler` | `DagCompiler.scala`, `IRGenerator.scala` |
| Standard library functions | `lang-stdlib` | `StdLib.scala`, `categories/*` |
| LSP, autocomplete, diagnostics | `lang-lsp` | `ConstellationLanguageServer.scala` |
| HTTP endpoints, WebSocket | `http-api` | `ConstellationServer.scala`, `ConstellationRoutes.scala` |
| Example modules | `example-app` | `modules/TextModules.scala`, `modules/DataModules.scala` |
| Dashboard UI | `dashboard/` | `src/` |

---

## 4. Development Commands

**ALWAYS use make commands. NEVER use raw sbt commands directly.**

| Task | Command |
|------|---------|
| Start dev environment | `make dev` |
| Start server (hot-reload) | `make server-rerun` |
| Run all tests | `make test` |
| Compile everything | `make compile` |
| Run core tests | `make test-core` |
| Run compiler tests | `make test-compiler` |
| Run LSP tests | `make test-lsp` |
| Dashboard E2E tests | `make test-dashboard` |
| Build fat JAR | `make assembly` |
| Build CLI JAR | `make cli-assembly` |
| Clean build | `make clean` |

**Windows (if make unavailable):** `.\scripts\dev.ps1` or `.\scripts\dev.ps1 -ServerOnly`

**sbt on Windows caveat:** Cannot run multiple sbt instances in parallel (lock conflict). Run tests sequentially.

---

## 5. Type System Overview

Constellation has **two parallel type systems**:

### Runtime Types (CType/CValue) - in `TypeSystem.scala`

```
CType (sealed trait)              CValue (sealed trait)
|-- CString                       |-- CString(value: String)
|-- CInt                          |-- CInt(value: Long)
|-- CFloat                        |-- CFloat(value: Double)
|-- CBoolean                      |-- CBoolean(value: Boolean)
|-- CList(valuesType)             |-- CList(value: Vector[CValue])
|-- CMap(keysType, valuesType)    |-- CMap(value: Vector[(CValue, CValue)])
|-- CProduct(structure: Map)      |-- CProduct(value: Map[String, CValue])
|-- CUnion(structure: Map)        |-- CUnion(tag: String, value: CValue, unionType: CType)
|-- COptional(innerType)          |-- COptional(value: Option[CValue])
```

### Compile-Time Types (SemanticType) - in `SemanticType.scala`

Used during type checking. Maps to CType for runtime. Supports unions, optionals, row polymorphism, type inference.

### Known API Quirks

- `CValue.CList` has field `value` (not `values`), type `Vector[CValue]`
- `CValue.CMap` takes `Vector[(CValue, CValue)]` (not `Map`)
- `RawValue.RMap` has field `entries` (not a `length` method)
- `ResilienceModules` uses `majorVersion`/`minorVersion` (not `version.major`)

---

## 6. Code Conventions

### Module Creation Pattern

```scala
import cats.effect.IO
import cats.implicits._  // Required for .traverse
import io.constellation._

// ALWAYS use case classes, NEVER tuples
case class MyInput(text: String)
case class MyOutput(result: String)

val myModule: Module.Uninitialized = ModuleBuilder
  .metadata("MyModule", "Description", 1, 0)
  .implementationPure[MyInput, MyOutput] { input =>
    MyOutput(input.text.toUpperCase)
  }
  .build
```

**Rules:**
- Module name in `metadata()` MUST match usage in `.cst` files (case-sensitive)
- Case class field names MUST match constellation-lang variable names
- ALWAYS use case classes for I/O, NEVER tuples (Scala 3 doesn't support single-element tuples)
- Import `cats.implicits._` when using `.traverse` for module registration

### Implementation Types

```scala
// Pure (no side effects)
.implementationPure[Input, Output] { input => Output(...) }

// IO-based (side effects allowed)
.implementation[Input, Output] { input => IO { Output(...) } }
```

### Naming Conventions

- **Modules:** PascalCase (`Uppercase`, `WordCount`)
- **Variables:** camelCase (`val uppercase`, `val wordCount`)
- **Case classes:** PascalCase (`TextInput`, `WordCountOutput`)
- **Fields:** camelCase, must match constellation-lang variable names
- **Packages:** match source location (`io.constellation`, `io.constellation.lang.compiler`)

### constellation-lang Syntax

```constellation
# Input declarations
in text: String
in count: Int
in user: {name: String, age: Int}

# Module calls (PascalCase, must match ModuleBuilder name)
trimmed = Trim(text)
result = Uppercase(trimmed)

# With resilience options
cached = ExpensiveAPI(input) with {
  retry: 3,
  timeout: 10s,
  cache: 1h,
  fallback: DefaultValue(input)
}

# Output declarations
out result
```

---

## 7. Testing Conventions

- Framework: ScalaTest (`AnyFlatSpec` + `Matchers`)
- Package matches source: `io.constellation`, `io.constellation.lang.compiler`, etc.
- IO tests: `import cats.effect.unsafe.implicits.global` then `.unsafeRunSync()`
- Error tests: `intercept[ErrorType] { ... }` or `.attempt.unsafeRunSync()` for IO
- Section comments: `// ===== Section Name =====`
- Run tests before committing: `make test`

---

## 8. Adding New Modules to example-app

1. Define module in `modules/example-app/src/main/scala/io/constellation/examples/app/modules/`
2. Add `FunctionSignature` to `ExampleLib.scala`
3. Register in `allSignatures` and `allModules`
4. Restart server

---

## 9. Adding Standard Library Functions

1. Create module in appropriate category: `modules/lang-stdlib/src/main/scala/io/constellation/stdlib/categories/`
2. Register in `modules/lang-stdlib/src/main/scala/io/constellation/stdlib/StdLib.scala`
3. Add signature to `allSignatures`
4. Add module to `allModules`

---

## 10. HTTP API & Server

- Default port: 8080 (configurable via `CONSTELLATION_PORT`)
- Health: `GET /health`, `/health/live`, `/health/ready`
- Execute pipeline: `POST /execute`
- List modules: `GET /modules`
- Validate: `POST /validate`
- LSP WebSocket: `ws://localhost:{port}/lsp`

### Server Builder Pattern

```scala
ConstellationServer.builder(constellation, compiler)
  .withAuth(AuthConfig(apiKeys = Map("key1" -> ApiRole.Admin)))
  .withCors(CorsConfig(allowedOrigins = Set("https://app.example.com")))
  .withRateLimit(RateLimitConfig(requestsPerMinute = 100, burst = 20))
  .withHealthChecks(HealthCheckConfig(enableDetailEndpoint = true))
  .run
```

---

## 11. Deep-Dive Documentation (Read On-Demand)

When you need deeper context on a specific topic, read these files:

### Foundations
| Topic | File |
|-------|------|
| Type system deep dive | `website/docs/llm/foundations/type-system.md` |
| Pipeline lifecycle | `website/docs/llm/foundations/pipeline-lifecycle.md` |
| DAG execution | `website/docs/llm/foundations/dag-execution.md` |
| Execution modes | `website/docs/llm/foundations/execution-modes.md` |

### Patterns
| Topic | File |
|-------|------|
| Module development | `website/docs/llm/patterns/module-development.md` |
| Resilience (retry/timeout/cache) | `website/docs/llm/patterns/resilience.md` |
| DAG composition | `website/docs/llm/patterns/dag-composition.md` |
| Type algebra | `website/docs/llm/patterns/type-algebra.md` |
| Error handling | `website/docs/llm/patterns/error-handling.md` |

### Reference
| Topic | File |
|-------|------|
| Type syntax | `website/docs/llm/reference/type-syntax.md` |
| Module options (with clause) | `website/docs/llm/reference/module-options.md` |
| HTTP API endpoints | `website/docs/llm/reference/http-api.md` |
| Error codes catalog | `website/docs/llm/reference/error-codes.md` |
| CLI commands | `website/docs/llm/cli-reference.md` |

### Integration
| Topic | File |
|-------|------|
| Embedded Scala API | `website/docs/llm/integration/embedded-api.md` |
| Module registration | `website/docs/llm/integration/module-registration.md` |

### Architecture & Philosophy
| Topic | File |
|-------|------|
| Technical architecture | `website/docs/architecture/technical-architecture.md` |
| Security model | `website/docs/architecture/security-model.md` |
| Organon philosophy | `organon/PHILOSOPHY.md` |
| Organon ethos | `organon/ETHOS.md` |

### Component Details (organon)
| Component | File |
|-----------|------|
| Core (types) | `organon/components/core/README.md` |
| Runtime (execution) | `organon/components/runtime/README.md` |
| Compiler (parser/typechecker) | `organon/components/compiler/README.md` |
| StdLib (built-in functions) | `organon/components/stdlib/README.md` |
| LSP (editor support) | `organon/components/lsp/README.md` |
| HTTP API (server) | `organon/components/http-api/README.md` |
| CLI (command line) | `organon/components/cli/README.md` |

### Feature Details (organon)
| Feature | File |
|---------|------|
| Type safety | `organon/features/type-safety/README.md` |
| Resilience | `organon/features/resilience/README.md` |
| Parallelization | `organon/features/parallelization/README.md` |
| Execution | `organon/features/execution/README.md` |
| Extensibility (SPI) | `organon/features/extensibility/README.md` |
| Tooling (LSP/dashboard) | `organon/features/tooling/README.md` |

### Auto-Generated Type Catalogs
For exact Scala signatures, check `organon/generated/io.constellation.*.md` files. These are auto-generated from TASTy and are read-only.

---

## 12. Task Workflow

When starting any development task:

1. **Understand the task** - Read the issue or request carefully
2. **Locate relevant code** - Use the issue triage table above to find the right module
3. **Read the code** - Always read existing code before modifying. Read relevant deep-dive docs if needed.
4. **Make changes** - Follow code conventions above
5. **Test** - Run `make test` (or module-specific `make test-core`, `make test-compiler`, etc.)
6. **Self-review** - Check correctness, consistency, edge cases before committing

### Performance Targets (for performance-critical changes)

| Operation | Target |
|-----------|--------|
| Parse (small program) | <5ms |
| Full pipeline (medium) | <100ms |
| Cache hit | <5ms |
| Autocomplete | <50ms |
| Cache speedup | >5x |

---

## 13. Common Pitfalls

1. **Don't forget `cats.implicits._`** - Required for `.traverse`
2. **Don't mismatch field names** - Case class fields must match constellation-lang variable names
3. **Don't use raw sbt** - Always use `make` commands
4. **Don't create circular dependencies** - Respect the module dependency graph
5. **Don't use tuples for module I/O** - Always use case classes
6. **Don't skip testing** - Run `make test` before committing
7. **Module names are case-sensitive** - `"Uppercase"` in Scala must match `Uppercase` in `.cst`
