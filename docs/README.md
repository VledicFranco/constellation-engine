# Constellation Engine

Type-safe pipeline orchestration for Scala 3. Define pipelines in a declarative DSL, implement modules in Scala, execute with automatic parallelization and resilience.

## Navigation

| Path | Description |
|------|-------------|
| [features/](./features/) | **What Constellation does** — capability-oriented documentation |
| [components/](./components/) | **How Constellation is built** — implementation-oriented documentation |
| [rfcs/](./rfcs/) | Design proposals and decisions |
| [dev/](./dev/) | Benchmarks, research, internal development docs |

## Guiding Documents

| Document | Purpose |
|----------|---------|
| [PHILOSOPHY.md](./PHILOSOPHY.md) | Why this documentation is structured the way it is |
| [ETHOS.md](./ETHOS.md) | Behavioral constraints for LLMs working on this documentation |
| [../PHILOSOPHY.md](../PHILOSOPHY.md) | Why Constellation exists (product philosophy) |
| [../ETHOS.md](../ETHOS.md) | Behavioral constraints for the Constellation codebase |

## Features

| Feature | Description | Components |
|---------|-------------|------------|
| [Type Safety](./features/type-safety/) | Compile-time validation of field accesses and type operations | core, compiler |
| [Resilience](./features/resilience/) | Retry, timeout, fallback, cache, throttle, error handling | compiler, runtime |
| [Parallelization](./features/parallelization/) | Automatic concurrent execution of independent modules | runtime |
| [Execution Modes](./features/execution/) | Hot, cold, and suspended execution | runtime, http-api |
| [Extensibility](./features/extensibility/) | SPI for custom backends, metrics, listeners | runtime |
| [Tooling](./features/tooling/) | Dashboard, LSP, VSCode extension | http-api, lang-lsp |

## Components

| Component | Description | Module |
|-----------|-------------|--------|
| [core](./components/core/) | Type system (CType, CValue) | `modules/core/` |
| [runtime](./components/runtime/) | Execution engine, ModuleBuilder | `modules/runtime/` |
| [compiler](./components/compiler/) | Parser, type checker, DAG compiler | `modules/lang-*` |
| [http-api](./components/http-api/) | REST endpoints, WebSocket, dashboard | `modules/http-api/` |

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

## Documentation Structure

This documentation follows the **philosophy/ethos/protocol** pattern:

- **Philosophy** — explains *why* decisions were made (for understanding)
- **Ethos** — prescribes *what* should and shouldn't be done (for behavioral consistency)
- **Protocols** — specifies *how* to accomplish specific tasks (for execution)

Each feature has its own philosophy and ethos.

## For LLMs

Start with [ETHOS.md](./ETHOS.md) for behavioral constraints, then navigate to the relevant feature. Each feature's `ETHOS.md` contains domain-specific constraints.

**Navigation pattern:**
```
"How does X work?"     → features/X/
"Where is X implemented?" → features/X/*.md → Components table → components/Y/
"What are the rules for X?" → features/X/ETHOS.md
```
