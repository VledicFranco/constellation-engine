# Constellation Engine

Type-safe pipeline orchestration for Scala 3. Define pipelines in a declarative DSL, implement modules in Scala, execute with automatic parallelization and resilience.

## Navigation

| Path | Description |
|------|-------------|
| [features/](./features/) | **What Constellation does** — capability-oriented documentation |
| [components/](./components/) | **How Constellation is built** — implementation-oriented documentation |
| [protocols/](./protocols/) | **How to accomplish tasks** — step-by-step procedures for common documentation tasks |
| [generated/](./generated/) | **What exists in code** — auto-generated type catalogs |
| [rfcs/](./rfcs/) | Design proposals and decisions |
| [dev/](./dev/) | Benchmarks, research, internal development docs |

## Organon (Guiding Documents)

This project uses the **organon** pattern — a complete guidance system consisting of philosophy, ethos, and protocol at each scope.

| Scope | Philosophy | Ethos | Purpose |
|-------|------------|-------|---------|
| **Product** | [../PHILOSOPHY.md](../PHILOSOPHY.md) | [../ETHOS.md](../ETHOS.md) | Why Constellation exists, codebase constraints |
| **Documentation** | [PHILOSOPHY.md](./PHILOSOPHY.md) | [ETHOS.md](./ETHOS.md) | Why docs are structured this way |
| **Features** | `features/X/PHILOSOPHY.md` | `features/X/ETHOS.md` | Domain-specific guidance |

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

This documentation follows the **organon** pattern — a complete guidance system with three artifacts:

| Artifact | Question | Audience |
|----------|----------|----------|
| **Philosophy** | Why do we do it this way? | Humans understanding the system |
| **Ethos** | What should we do and not do? | LLMs (and humans) behaving in the system |
| **Protocol** | How do we accomplish this task? | Agents executing specific tasks |

Each feature has its own organon (philosophy + ethos). See the [Organon methodology](../ethos/) for details.

## Three-Layer Model

Documentation exists at three layers:

```
Organon (manual)     ← "What it means" — ETHOS.md with semantic mapping
Generated (auto)     ← "What exists" — docs/generated/*.md
Code (source)        ← "The truth" — modules/*/src/**/*.scala
```

| Command | Purpose |
|---------|---------|
| `make generate-docs` | Regenerate catalogs from code |
| `make check-docs` | Verify catalogs are fresh |
| `make verify-ethos` | Check invariant references are valid |

Component ETHOS files bridge generated catalogs to domain meaning via **semantic mapping tables**. See [components/runtime/ETHOS.md](./components/runtime/ETHOS.md) for an example.

## For LLMs

Start with [ETHOS.md](./ETHOS.md) for behavioral constraints, then navigate to the relevant feature. Each feature's `ETHOS.md` contains domain-specific constraints.

**Navigation pattern:**
```
"How does X work?"     → features/X/
"Where is X implemented?" → features/X/*.md → Components table → components/Y/
"What are the rules for X?" → features/X/ETHOS.md
```
