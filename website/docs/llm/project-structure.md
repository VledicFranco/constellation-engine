# Project Structure Guide

A comprehensive guide to navigating the Constellation Engine codebase. This document helps you quickly locate specific functionality and understand the module organization.

## Table of Contents

- [Quick Reference](#quick-reference)
- [Module Organization](#module-organization)
- [Dependency Graph](#dependency-graph)
- [Package Structure](#package-structure)
- [File Locations Reference](#file-locations-reference)
- [Navigation Commands](#navigation-commands)
- [Where to Find Functionality](#where-to-find-functionality)

## Quick Reference

**All modules live under:** `modules/<module-name>/src/main/scala/io/constellation/`

**Key entry points:**
- Type system: `modules/core/src/main/scala/io/constellation/TypeSystem.scala`
- Module builder: `modules/runtime/src/main/scala/io/constellation/ModuleBuilder.scala`
- Parser: `modules/lang-parser/src/main/scala/io/constellation/lang/parser/ConstellationParser.scala`
- Compiler: `modules/lang-compiler/src/main/scala/io/constellation/lang/LangCompiler.scala`
- HTTP server: `modules/http-api/src/main/scala/io/constellation/http/ConstellationServer.scala`
- LSP server: `modules/lang-lsp/src/main/scala/io/constellation/lsp/ConstellationLanguageServer.scala`

## Module Organization

Constellation Engine is organized into 12 primary modules:

| Module | Purpose | Dependencies |
|--------|---------|--------------|
| `core` | Foundation types (CType, CValue, TypeSystem) | None |
| `runtime` | Execution engine, ModuleBuilder, Runtime | `core` |
| `lang-ast` | AST definitions for constellation-lang | `core` |
| `lang-parser` | Parser for `.cst` files | `lang-ast`, `core` |
| `lang-compiler` | Type checker, DAG compiler, optimizer | `lang-parser`, `lang-ast`, `runtime`, `core` |
| `lang-stdlib` | Standard library modules (String, List, Math) | `lang-compiler`, `runtime`, `core` |
| `lang-lsp` | Language Server Protocol implementation | `lang-compiler`, `lang-ast`, `core` |
| `lang-cli` | Command-line interface | `lang-compiler`, `http-api`, `runtime`, `core` |
| `http-api` | HTTP server, WebSocket, middleware | `lang-compiler`, `runtime`, `core` |
| `example-app` | Example modules (Text, Data processing) | `lang-stdlib`, `lang-compiler`, `runtime`, `core` |
| `cache-memcached` | Memcached cache backend | `runtime`, `core` |
| `doc-generator` | Documentation generation utilities | `runtime`, `core` |

## Dependency Graph

**CRITICAL:** Modules can only depend on modules above them in this graph. Never create circular dependencies.

```
core (foundation - no dependencies)
  ├─> runtime
  │     ├─> cache-memcached
  │     ├─> doc-generator
  │     └─> (used by everything below)
  │
  └─> lang-ast
        └─> lang-parser
              └─> lang-compiler
                    ├─> lang-stdlib
                    ├─> lang-lsp
                    ├─> lang-cli
                    ├─> http-api
                    └─> example-app
```

**Layering Rules:**
1. **Core layer** (`core`): Pure type definitions, no side effects
2. **Execution layer** (`runtime`): Module execution, caching, pools
3. **Language layer** (`lang-*`): Parsing, compilation, type checking
4. **Service layer** (`http-api`, `lang-lsp`, `lang-cli`): External interfaces
5. **Application layer** (`example-app`): User-facing modules

## Package Structure

Each module follows standard Scala project structure:

```
modules/<module-name>/
├── src/
│   ├── main/
│   │   ├── scala/
│   │   │   └── io/constellation/
│   │   │       └── <module-specific packages>
│   │   └── resources/
│   └── test/
│       ├── scala/
│       │   └── io/constellation/
│       │       └── <test files>
│       └── resources/
└── README.md (optional)
```

### Package Naming Conventions

**Core packages:**
- `io.constellation.*` - Core types (TypeSystem, Spec, etc.)

**Runtime packages:**
- `io.constellation.*` - Runtime, ModuleBuilder, Constellation
- `io.constellation.cache.*` - Caching infrastructure
- `io.constellation.execution.*` - Circuit breakers, scheduling
- `io.constellation.pool.*` - Resource pooling
- `io.constellation.spi.*` - Service Provider Interface (backends, metrics, tracing)
- `io.constellation.errors.*` - Error handling utilities

**Language packages:**
- `io.constellation.lang.ast.*` - AST node definitions
- `io.constellation.lang.parser.*` - Parser implementation
- `io.constellation.lang.compiler.*` - Compiler, type checker, IR generator
- `io.constellation.lang.semantic.*` - Semantic analysis, type inference
- `io.constellation.lang.optimizer.*` - IR optimization passes
- `io.constellation.lang.viz.*` - DAG visualization (DOT, Mermaid, SVG)

**LSP packages:**
- `io.constellation.lsp.*` - Language server core
- `io.constellation.lsp.protocol.*` - LSP protocol messages
- `io.constellation.lsp.diagnostics.*` - Error diagnostics

**HTTP packages:**
- `io.constellation.http.*` - HTTP routes, server, middleware

**Standard library packages:**
- `io.constellation.stdlib.*` - StdLib registry
- `io.constellation.stdlib.categories.*` - Function categories (String, List, Math, etc.)

**Example app packages:**
- `io.constellation.examples.app.*` - Example library registry
- `io.constellation.examples.app.modules.*` - Module implementations (Text, Data)

## File Locations Reference

### Core Module (`modules/core`)

| Component | Path |
|-----------|------|
| Type system | `src/main/scala/io/constellation/TypeSystem.scala` |
| CValue/CType definitions | `src/main/scala/io/constellation/TypeSystem.scala` |
| Constellation errors | `src/main/scala/io/constellation/ConstellationError.scala` |
| Module specs | `src/main/scala/io/constellation/Spec.scala` |
| Pipeline status | `src/main/scala/io/constellation/PipelineStatus.scala` |
| Content hashing | `src/main/scala/io/constellation/ContentHash.scala` |
| Raw value types | `src/main/scala/io/constellation/RawValue.scala` |
| Debug mode config | `src/main/scala/io/constellation/DebugMode.scala` |

### Runtime Module (`modules/runtime`)

| Component | Path |
|-----------|------|
| ModuleBuilder API | `src/main/scala/io/constellation/ModuleBuilder.scala` |
| Runtime execution | `src/main/scala/io/constellation/Runtime.scala` |
| Constellation core | `src/main/scala/io/constellation/Constellation.scala` |
| Module registry | `src/main/scala/io/constellation/ModuleRegistry.scala` |
| Pipeline storage | `src/main/scala/io/constellation/PipelineStore.scala` |
| Pipeline images | `src/main/scala/io/constellation/PipelineImage.scala` |
| Loaded pipelines | `src/main/scala/io/constellation/LoadedPipeline.scala` |
| Execution tracking | `src/main/scala/io/constellation/ExecutionTracker.scala` |
| Suspended execution | `src/main/scala/io/constellation/SuspendedExecution.scala` |
| Suspension codec | `src/main/scala/io/constellation/SuspensionCodec.scala` |
| Suspension store | `src/main/scala/io/constellation/SuspensionStore.scala` |
| Cache registry | `src/main/scala/io/constellation/cache/CacheRegistry.scala` |
| Circuit breakers | `src/main/scala/io/constellation/execution/CircuitBreakerRegistry.scala` |
| Runtime pool | `src/main/scala/io/constellation/pool/RuntimePool.scala` |
| SPI backends | `src/main/scala/io/constellation/spi/ConstellationBackends.scala` |
| Metrics provider | `src/main/scala/io/constellation/spi/MetricsProvider.scala` |
| Tracer provider | `src/main/scala/io/constellation/spi/TracerProvider.scala` |

### Language AST Module (`modules/lang-ast`)

| Component | Path |
|-----------|------|
| AST nodes | `src/main/scala/io/constellation/lang/ast/AST.scala` |
| Visitor pattern | `src/main/scala/io/constellation/lang/ast/ASTVisitor.scala` |

### Language Parser Module (`modules/lang-parser`)

| Component | Path |
|-----------|------|
| Main parser | `src/main/scala/io/constellation/lang/parser/ConstellationParser.scala` |
| Memoization | `src/main/scala/io/constellation/lang/parser/MemoizationSupport.scala` |

### Language Compiler Module (`modules/lang-compiler`)

| Component | Path |
|-----------|------|
| Lang compiler | `src/main/scala/io/constellation/lang/LangCompiler.scala` |
| Caching compiler | `src/main/scala/io/constellation/lang/CachingLangCompiler.scala` |
| DAG compiler | `src/main/scala/io/constellation/lang/compiler/DagCompiler.scala` |
| Type checker | `src/main/scala/io/constellation/lang/compiler/TypeChecker.scala` |
| IR generator | `src/main/scala/io/constellation/lang/compiler/IRGenerator.scala` |
| Compilation output | `src/main/scala/io/constellation/lang/compiler/CompilationOutput.scala` |
| Compiler errors | `src/main/scala/io/constellation/lang/compiler/CompilerError.scala` |
| Error formatter | `src/main/scala/io/constellation/lang/compiler/ErrorFormatter.scala` |
| Suggestions | `src/main/scala/io/constellation/lang/compiler/Suggestions.scala` |
| Module options | `src/main/scala/io/constellation/lang/compiler/ModuleOptionsExecutor.scala` |
| Semantic types | `src/main/scala/io/constellation/lang/semantic/SemanticType.scala` |
| Type inference | `src/main/scala/io/constellation/lang/semantic/Mode.scala` |
| Row unification | `src/main/scala/io/constellation/lang/semantic/RowUnification.scala` |
| Subtyping | `src/main/scala/io/constellation/lang/semantic/Subtyping.scala` |
| IR optimizer | `src/main/scala/io/constellation/lang/optimizer/IROptimizer.scala` |
| Constant folding | `src/main/scala/io/constellation/lang/optimizer/ConstantFolding.scala` |
| Dead code elim | `src/main/scala/io/constellation/lang/optimizer/DeadCodeElimination.scala` |
| Optimization config | `src/main/scala/io/constellation/lang/optimizer/OptimizationConfig.scala` |
| DAG renderer | `src/main/scala/io/constellation/lang/viz/DagRenderer.scala` |
| DOT renderer | `src/main/scala/io/constellation/lang/viz/DOTRenderer.scala` |
| SVG renderer | `src/main/scala/io/constellation/lang/viz/SVGRenderer.scala` |
| Mermaid renderer | `src/main/scala/io/constellation/lang/viz/MermaidRenderer.scala` |
| ASCII renderer | `src/main/scala/io/constellation/lang/viz/ASCIIRenderer.scala` |
| Sugiyama layout | `src/main/scala/io/constellation/lang/viz/SugiyamaLayout.scala` |
| Compilation cache | `src/main/scala/io/constellation/lang/CompilationCache.scala` |

### Language Standard Library Module (`modules/lang-stdlib`)

| Component | Path |
|-----------|------|
| StdLib registry | `src/main/scala/io/constellation/stdlib/StdLib.scala` |
| String functions | `src/main/scala/io/constellation/stdlib/categories/StringFunctions.scala` |
| List functions | `src/main/scala/io/constellation/stdlib/categories/ListFunctions.scala` |
| Math functions | `src/main/scala/io/constellation/stdlib/categories/MathFunctions.scala` |
| Boolean functions | `src/main/scala/io/constellation/stdlib/categories/BooleanFunctions.scala` |
| Comparison functions | `src/main/scala/io/constellation/stdlib/categories/ComparisonFunctions.scala` |
| Type conversion | `src/main/scala/io/constellation/stdlib/categories/TypeConversionFunctions.scala` |
| Higher-order functions | `src/main/scala/io/constellation/stdlib/categories/HigherOrderFunctions.scala` |
| Utility functions | `src/main/scala/io/constellation/stdlib/categories/UtilityFunctions.scala` |

### Language LSP Module (`modules/lang-lsp`)

| Component | Path |
|-----------|------|
| Language server | `src/main/scala/io/constellation/lsp/ConstellationLanguageServer.scala` |
| Document manager | `src/main/scala/io/constellation/lsp/DocumentManager.scala` |
| Completion trie | `src/main/scala/io/constellation/lsp/CompletionTrie.scala` |
| Type formatter | `src/main/scala/io/constellation/lsp/TypeFormatter.scala` |
| Semantic tokens | `src/main/scala/io/constellation/lsp/SemanticTokenTypes.scala` |
| With-clause completions | `src/main/scala/io/constellation/lsp/WithClauseCompletions.scala` |
| Debouncer | `src/main/scala/io/constellation/lsp/Debouncer.scala` |
| Debug session manager | `src/main/scala/io/constellation/lsp/DebugSessionManager.scala` |
| LSP messages | `src/main/scala/io/constellation/lsp/protocol/LspMessages.scala` |
| LSP types | `src/main/scala/io/constellation/lsp/protocol/LspTypes.scala` |
| JSON-RPC | `src/main/scala/io/constellation/lsp/protocol/JsonRpc.scala` |
| Options diagnostics | `src/main/scala/io/constellation/lsp/diagnostics/OptionsDiagnostics.scala` |

### Language CLI Module (`modules/lang-cli`)

| Component | Path |
|-----------|------|
| Main entry point | `src/main/scala/io/constellation/cli/Main.scala` |
| CLI app | `src/main/scala/io/constellation/cli/CliApp.scala` |
| Commands | `src/main/scala/io/constellation/cli/commands/` |

### HTTP API Module (`modules/http-api`)

| Component | Path |
|-----------|------|
| HTTP server | `src/main/scala/io/constellation/http/ConstellationServer.scala` |
| HTTP routes | `src/main/scala/io/constellation/http/ConstellationRoutes.scala` |
| Auth middleware | `src/main/scala/io/constellation/http/AuthMiddleware.scala` |
| Auth config | `src/main/scala/io/constellation/http/AuthConfig.scala` |
| CORS middleware | `src/main/scala/io/constellation/http/CorsMiddleware.scala` |
| Rate limit middleware | `src/main/scala/io/constellation/http/RateLimitMiddleware.scala` |
| Health check routes | `src/main/scala/io/constellation/http/HealthCheckRoutes.scala` |
| Dashboard routes | `src/main/scala/io/constellation/http/DashboardRoutes.scala` |
| Dashboard config | `src/main/scala/io/constellation/http/DashboardConfig.scala` |
| Dashboard models | `src/main/scala/io/constellation/http/DashboardModels.scala` |
| API models | `src/main/scala/io/constellation/http/ApiModels.scala` |
| Execution helper | `src/main/scala/io/constellation/http/ExecutionHelper.scala` |
| Execution storage | `src/main/scala/io/constellation/http/ExecutionStorage.scala` |
| Pipeline loader config | `src/main/scala/io/constellation/http/PipelineLoaderConfig.scala` |
| Prometheus formatter | `src/main/scala/io/constellation/http/PrometheusFormatter.scala` |
| Canary router | `src/main/scala/io/constellation/http/CanaryRouter.scala` |

### Example App Module (`modules/example-app`)

| Component | Path |
|-----------|------|
| ExampleLib registry | `src/main/scala/io/constellation/examples/app/ExampleLib.scala` |
| Text modules | `src/main/scala/io/constellation/examples/app/modules/TextModules.scala` |
| Data modules | `src/main/scala/io/constellation/examples/app/modules/DataModules.scala` |
| Resilience modules | `src/main/scala/io/constellation/examples/app/modules/ResilienceModules.scala` |

### Cache Memcached Module (`modules/cache-memcached`)

| Component | Path |
|-----------|------|
| Memcached backend | `src/main/scala/io/constellation/cache/memcached/MemcachedCacheBackend.scala` |
| Memcached config | `src/main/scala/io/constellation/cache/memcached/MemcachedConfig.scala` |

### Frontend Components

| Component | Path |
|-----------|------|
| VSCode extension | `vscode-extension/src/extension.ts` |
| Dashboard TypeScript | `dashboard/src/` |
| Dashboard types | `dashboard/src/types.d.ts` |
| Dashboard E2E tests | `dashboard-tests/` |
| Page objects | `dashboard-tests/pages/` |

### Infrastructure

| Component | Path |
|-----------|------|
| Build definition | `build.sbt` |
| Makefile | `Makefile` |
| Dockerfile | `Dockerfile` |
| Docker Compose | `docker-compose.yml` |
| Docker ignore | `.dockerignore` |
| Kubernetes manifests | `deploy/k8s/` |
| Development scripts | `scripts/` |

## Navigation Commands

### Find Files by Pattern

```bash
# Find all Scala files in a module
ls modules/<module-name>/src/main/scala/io/constellation/

# Find test files
ls modules/<module-name>/src/test/scala/io/constellation/

# Find all files matching a pattern
find modules -name "*TypeSystem*"

# Find constellation-lang scripts
find . -name "*.cst"
```

### Search for Code

```bash
# Search for a class definition
grep -r "class ClassName\|object ObjectName" modules/

# Search for a function definition
grep -r "def functionName" modules/

# Find usages of a type
grep -r "CType\|CValue\|Module.Uninitialized" modules/

# Find import statements
grep -r "import io.constellation" modules/

# Search in specific module only
grep -r "pattern" modules/core/src/
```

### Navigate by Issue Type

Use these commands to quickly find relevant code based on issue type:

```bash
# Type system issues
ls modules/core/src/main/scala/io/constellation/TypeSystem.scala

# Module execution issues
ls modules/runtime/src/main/scala/io/constellation/ModuleBuilder.scala
ls modules/runtime/src/main/scala/io/constellation/Runtime.scala

# Parser issues
ls modules/lang-parser/src/main/scala/io/constellation/lang/parser/ConstellationParser.scala

# Type checking issues
ls modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/TypeChecker.scala
ls modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/

# Compilation issues
ls modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/DagCompiler.scala
ls modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/IRGenerator.scala

# LSP issues
ls modules/lang-lsp/src/main/scala/io/constellation/lsp/ConstellationLanguageServer.scala

# HTTP API issues
ls modules/http-api/src/main/scala/io/constellation/http/
```

### Run Tests for Specific Areas

```bash
# Test by module
make test-core           # Core type system
make test-compiler       # Parser + compiler
make test-lsp            # Language server
make test-dashboard      # Dashboard E2E tests

# Test specific file (sbt)
sbt "core/testOnly *TypeSystemSpec"
sbt "langCompiler/testOnly *TypeCheckerSpec"
sbt "langLsp/testOnly *CompletionSpec"
```

## Where to Find Functionality

### By Feature Category

**Type System & Values:**
- Type definitions (`CType`): `modules/core/.../TypeSystem.scala`
- Value definitions (`CValue`): `modules/core/.../TypeSystem.scala`
- Type conversion: `modules/core/.../RawValueConverter.scala`
- Type accessors: `modules/core/.../TypedValueAccessor.scala`

**Module System:**
- Building modules: `modules/runtime/.../ModuleBuilder.scala`
- Module registry: `modules/runtime/.../ModuleRegistry.scala`
- Module metadata: `modules/runtime/.../MetadataBuilder.scala`
- Module specs: `modules/core/.../Spec.scala`

**Execution Engine:**
- Runtime execution: `modules/runtime/.../Runtime.scala`
- Pipeline execution: `modules/runtime/.../Constellation.scala`
- Suspended execution: `modules/runtime/.../SuspendedExecution.scala`
- Stepped execution: `modules/runtime/.../SteppedExecution.scala`
- Circuit breakers: `modules/runtime/.../execution/CircuitBreakerRegistry.scala`

**Language Processing:**
- Parsing: `modules/lang-parser/.../ConstellationParser.scala`
- Type checking: `modules/lang-compiler/.../TypeChecker.scala`
- DAG compilation: `modules/lang-compiler/.../DagCompiler.scala`
- IR generation: `modules/lang-compiler/.../IRGenerator.scala`
- Optimization: `modules/lang-compiler/.../optimizer/`

**Standard Library:**
- String functions: `modules/lang-stdlib/.../categories/StringFunctions.scala`
- List functions: `modules/lang-stdlib/.../categories/ListFunctions.scala`
- Math functions: `modules/lang-stdlib/.../categories/MathFunctions.scala`
- All categories: `modules/lang-stdlib/.../categories/`

**IDE Support:**
- Language server: `modules/lang-lsp/.../ConstellationLanguageServer.scala`
- Autocomplete: `modules/lang-lsp/.../CompletionTrie.scala`
- Diagnostics: `modules/lang-lsp/.../diagnostics/`
- Document management: `modules/lang-lsp/.../DocumentManager.scala`

**HTTP API:**
- Server setup: `modules/http-api/.../ConstellationServer.scala`
- Route definitions: `modules/http-api/.../ConstellationRoutes.scala`
- Authentication: `modules/http-api/.../AuthMiddleware.scala`
- CORS: `modules/http-api/.../CorsMiddleware.scala`
- Rate limiting: `modules/http-api/.../RateLimitMiddleware.scala`

**Caching:**
- Cache registry: `modules/runtime/.../cache/CacheRegistry.scala`
- Compilation cache: `modules/lang-compiler/.../CompilationCache.scala`
- Memcached backend: `modules/cache-memcached/.../MemcachedCacheBackend.scala`

**Visualization:**
- DAG rendering: `modules/lang-compiler/.../viz/DagRenderer.scala`
- DOT format: `modules/lang-compiler/.../viz/DOTRenderer.scala`
- SVG format: `modules/lang-compiler/.../viz/SVGRenderer.scala`
- Mermaid format: `modules/lang-compiler/.../viz/MermaidRenderer.scala`

**Error Handling:**
- Core errors: `modules/core/.../ConstellationError.scala`
- Compiler errors: `modules/lang-compiler/.../CompilerError.scala`
- Error formatting: `modules/lang-compiler/.../ErrorFormatter.scala`
- Error utilities: `modules/runtime/.../errors/ErrorHandling.scala`

### By Common Task

**Adding a new module to example-app:**
1. Create module in `modules/example-app/.../modules/`
2. Add to `ExampleLib.scala` (signature + registration)
3. Restart server with `make server`

**Fixing a parse error:**
1. Check `modules/lang-parser/.../ConstellationParser.scala`
2. Add test in `modules/lang-parser/src/test/`
3. Run `make test-compiler`

**Fixing a type error:**
1. Check `modules/lang-compiler/.../TypeChecker.scala`
2. Look at semantic types in `modules/lang-compiler/.../semantic/SemanticType.scala`
3. Add test and run `make test-compiler`

**Adding LSP feature:**
1. Update `modules/lang-lsp/.../ConstellationLanguageServer.scala`
2. Add protocol types in `modules/lang-lsp/.../protocol/`
3. Test in VSCode extension

**Adding HTTP endpoint:**
1. Add route in `modules/http-api/.../ConstellationRoutes.scala`
2. Add models in `modules/http-api/.../ApiModels.scala`
3. Test with `curl` or dashboard

**Adding standard library function:**
1. Create module in appropriate category file in `modules/lang-stdlib/.../categories/`
2. Register in `modules/lang-stdlib/.../StdLib.scala`
3. Add signature to `allSignatures`
4. Add module to `allModules`

## Issue Triage Quick Reference

| Issue Type | Primary Module(s) | Key Files |
|------------|-------------------|-----------|
| Type errors, CValue/CType bugs | `core` | `TypeSystem.scala` |
| Module execution, ModuleBuilder | `runtime` | `ModuleBuilder.scala`, `Runtime.scala` |
| Parse errors, syntax issues | `lang-parser` | `ConstellationParser.scala` |
| Type checking, semantic errors | `lang-compiler` | `TypeChecker.scala`, `SemanticType.scala` |
| DAG compilation issues | `lang-compiler` | `DagCompiler.scala`, `IRGenerator.scala` |
| Standard library functions | `lang-stdlib` | `StdLib.scala`, `categories/*` |
| LSP, autocomplete, diagnostics | `lang-lsp` | `ConstellationLanguageServer.scala` |
| HTTP endpoints, WebSocket | `http-api` | `ConstellationServer.scala`, `ConstellationRoutes.scala` |
| Example modules (text, data) | `example-app` | `modules/TextModules.scala`, `modules/DataModules.scala` |
| VSCode extension | `vscode-extension/` | `src/extension.ts`, `src/panels/*.ts` |
| Dashboard UI | `dashboard/` | `src/` |
| Dashboard E2E tests | `dashboard-tests/` | `tests/`, `pages/` |

## See Also

- [Architecture Overview](../architecture/technical-architecture.md)
- [CLAUDE.md](../../CLAUDE.md) - Full development rules
- [Contributing Guide](../../CONTRIBUTING.md)
