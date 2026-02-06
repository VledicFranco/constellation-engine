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
