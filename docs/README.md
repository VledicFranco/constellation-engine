# Constellation Engine

Type-safe pipeline orchestration for Scala 3. Define pipelines in a declarative DSL, implement modules in Scala, execute with automatic parallelization and resilience.

## Navigation

| Path | Description |
|------|-------------|
| [overview/](./overview/) | Core concepts, architecture, when to use |
| [language/](./language/) | DSL syntax: types, expressions, module options |
| [runtime/](./runtime/) | Execution engine, scheduling, ModuleBuilder API |
| [http-api/](./http-api/) | REST endpoints, security, WebSocket LSP |
| [extensibility/](./extensibility/) | SPI interfaces for custom backends |
| [tooling/](./tooling/) | Dashboard, VSCode extension, LSP |
| [stdlib/](./stdlib/) | Standard library functions |
| [reference/](./reference/) | Errors, troubleshooting, migration guides |
| [dev/](./dev/) | RFCs, benchmarks, internal development docs |

## Core Features

| Feature | Description | Components | Level | Docs |
|---------|-------------|------------|-------|------|
| Type-safe pipelines | Compile-time field access and type validation | core, compiler | Basic | [language/types/](./language/types/) |
| Automatic parallelization | Independent modules execute concurrently | runtime | Basic | [runtime/execution-modes.md](./runtime/execution-modes.md) |
| Module options | Retry, timeout, cache, throttle, fallback | runtime, compiler | Intermediate | [language/options/](./language/options/) |
| Hot execution | Compile + run in one request | runtime, http-api | Basic | [runtime/execution-modes.md](./runtime/execution-modes.md) |
| Cold execution | Pre-compiled, execute by reference | runtime, http-api | Intermediate | [runtime/execution-modes.md](./runtime/execution-modes.md) |
| Suspend/Resume | Pause on missing inputs, resume later | runtime, http-api | Advanced | [http-api/suspension.md](./http-api/suspension.md) |
| Priority scheduler | Bounded concurrency with priority ordering | runtime | Advanced | [runtime/scheduling.md](./runtime/scheduling.md) |
| Type algebra | Record merge, projection, element-wise ops | core, compiler | Intermediate | [language/expressions/](./language/expressions/) |
| Standard library | 44+ built-in functions (math, string, list) | stdlib | Basic | [stdlib/](./stdlib/) |
| SPI extensibility | Custom cache, metrics, listeners, storage | runtime | Advanced | [extensibility/](./extensibility/) |
| HTTP API | REST endpoints for execution and management | http-api | Intermediate | [http-api/](./http-api/) |
| LSP integration | IDE autocomplete, hover docs, diagnostics | lang-lsp, http-api | Intermediate | [tooling/lsp.md](./tooling/lsp.md) |
| Web dashboard | Browser-based pipeline editor and runner | http-api | Basic | [tooling/dashboard.md](./tooling/dashboard.md) |
| Canary deployments | Traffic splitting with auto-rollback | http-api | Advanced | [http-api/pipelines.md](./http-api/pipelines.md) |
| Pipeline versioning | Content-addressed storage, rollback | http-api | Intermediate | [http-api/pipelines.md](./http-api/pipelines.md) |

## Quick Reference

### Pipeline Structure
```constellation
in inputName: Type           # declare inputs
type Alias = TypeExpression  # type aliases (optional)
result = Module(args)        # module calls
out result                   # declare outputs
```

### Module Call with Options
```constellation
result = GetUser(id) with retry: 3, timeout: 5s, cache: 15min
```

### Execution Modes
| Mode | Endpoint | Use Case |
|------|----------|----------|
| Hot | `POST /run` | Development, ad-hoc queries |
| Cold | `POST /compile` + `POST /execute` | Production, high throughput |
| Suspended | `POST /run` → `POST /resume` | Multi-step workflows |

## Meta

| File | Purpose |
|------|---------|
| [STRUCTURE.md](./STRUCTURE.md) | Documentation organization ethos |
| [dev/FEATURE-OVERVIEW.md](./dev/FEATURE-OVERVIEW.md) | Comprehensive feature inventory |
| [dev/LLM-CONTEXT.md](./dev/LLM-CONTEXT.md) | Ultra-condensed LLM context |
