# Components

> **Path**: `organon/components/`
> **Parent**: [organon/](](../README.md)

Technical documentation organized by codebase module. Use this for implementation details.

## Contents

| Component | Description | Module Path |
|-----------|-------------|-------------|
| [core/](./core/) | Type system (CType, CValue, TypeSystem) | `modules/core/` |
| [runtime/](./runtime/) | Execution engine, ModuleBuilder, scheduling | `modules/runtime/` |
| [compiler/](./compiler/) | Parser, type checker, IR generator, DAG compiler | `modules/lang-parser/`, `modules/lang-compiler/` |
| [stdlib/](./stdlib/) | Standard library functions | `modules/lang-stdlib/` |
| [lsp/](./lsp/) | Language server protocol implementation | `modules/lang-lsp/` |
| [http-api/](./http-api/) | REST endpoints, WebSocket, dashboard | `modules/http-api/` |
| [cli/](./cli/) | Command-line interface | `modules/lang-cli/` |

## When to Use Components vs Features

| Question | Start Here |
|----------|------------|
| "How does caching work?" | [features/resilience/](../features/resilience/) |
| "Where is caching implemented?" | [components/runtime/](./runtime/) |
| "What can I do with types?" | [features/type-safety/](../features/type-safety/) |
| "How does the type checker work?" | [components/compiler/](./compiler/) |

**Features** = what the system does (capability-oriented)
**Components** = how the system is built (implementation-oriented)

## Dependency Graph

```
                    core
                      │
        ┌─────────────┼─────────────┐
        │             │             │
        ▼             ▼             │
    runtime      lang-parser        │
        │             │             │
        │             ▼             │
        │       lang-compiler ◄─────┘
        │             │
        │      ┌──────┴──────┐
        │      │             │
        │      ▼             ▼
        │  lang-stdlib   lang-lsp
        │      │
        └──────┼─────────────┐
               │             │
               ▼             │
           http-api ◄────────┘
               │
               ▼
         example-app
```

**Rule:** Components can only depend on components above them. No cycles.

## Structure

Each component directory contains:

| File | Purpose |
|------|---------|
| `README.md` | Overview, key files, navigation |
| `architecture.md` | Internal design and data flow |
| `spi.md` | Extension points (if applicable) |
| `*.md` | Detailed documentation per subsystem |
