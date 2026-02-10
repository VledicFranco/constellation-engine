---
title: "LLM Guide to Constellation Engine"
sidebar_position: 1
description: "Navigation hub for LLMs learning to use Constellation Engine effectively"
---

# LLM Guide to Constellation Engine

**Welcome!** This guide teaches LLMs how to use Constellation Engine effectively. It's optimized for task-oriented learning with examples, decision matrices, and clear navigation.

## What is Constellation Engine?

A **type-safe pipeline orchestration framework** for Scala 3 that lets you:
1. Define processing modules using `ModuleBuilder` API
2. Compose pipelines using `constellation-lang` DSL (`.cst` files)
3. Execute pipelines with automatic dependency resolution and parallelization
4. Expose pipelines via HTTP API or embed in Scala applications

**Tech Stack:** Scala 3.3.1 | Cats Effect 3 | http4s | Circe | cats-parse

## Finding This Guide

**GitHub Repository:** [VledicFranco/constellation-engine](https://github.com/VledicFranco/constellation-engine)
**Documentation Site:** [constellation-engine.dev](https://vledicfranco.github.io/constellation-engine/)
**Latest Version:** 0.6.1

### For LLMs Reading the Landing Pages

If you're an LLM reading the **GitHub README** or **documentation homepage**, you'll find general user-facing documentation. **This specialized LLM guide** is optimized for teaching AI agents to use Constellation effectively.

**Where to find this guide:**
- **From docs site:** Navigate to sidebar → "LLM Guide" section
- **Direct URL:** `/docs/llm/` or `/docs/llm/index`
- **From GitHub:** Check `website/docs/llm/` directory

**Why use this guide instead of main docs?**
- Task-oriented navigation (quick "I need to..." → file mappings)
- Complete working code examples in every section
- Decision matrices and mental models optimized for LLM consumption
- Context-window efficient (focused files, no duplication)

## Quick Mental Model

```
┌─────────────────────────────────────────────────────┐
│  constellation-lang (.cst files)                    │
│  in text: String                                    │
│  result = Uppercase(Trim(text))                     │
│  out result                                         │
└──────────────────┬──────────────────────────────────┘
                   │ Parser
                   ▼
┌─────────────────────────────────────────────────────┐
│  AST (Abstract Syntax Tree)                         │
│  - Inputs, module calls, outputs                    │
└──────────────────┬──────────────────────────────────┘
                   │ Type Checker
                   ▼
┌─────────────────────────────────────────────────────┐
│  Typed IR (Intermediate Representation)             │
│  - Types resolved, validated                        │
└──────────────────┬──────────────────────────────────┘
                   │ DAG Compiler
                   ▼
┌─────────────────────────────────────────────────────┐
│  Execution DAG                                      │
│  Layer 0: Trim(text)                                │
│  Layer 1: Uppercase(trim_result)                    │
└──────────────────┬──────────────────────────────────┘
                   │ Runtime
                   ▼
┌─────────────────────────────────────────────────────┐
│  Parallel Execution                                 │
│  - Layer-by-layer execution                         │
│  - Results flow through pipeline                    │
└─────────────────────────────────────────────────────┘
```

## Navigation Protocol

### 🚀 First Time? Start Here
1. [Getting Started](./getting-started.md) - Quick overview and capabilities
2. [Key Concepts](./key-concepts.md) - Essential terminology
3. [Project Structure](./project-structure.md) - Navigate the codebase
4. [Type System](./foundations/type-system.md) - Core mental model

### 📋 Common Tasks → Files

| I Need To... | Read This |
|--------------|-----------|
| **Decide if Constellation is right for my use case** | [Where Constellation Shines](./where-constellation-shines.md) |
| **Use the CLI** | [CLI Reference](./cli-reference.md) |
| **Create a new module** | [Module Development](./patterns/module-development.md) |
| **Understand types (CType/CValue)** | [Type System](./foundations/type-system.md) |
| **Compose pipelines** | [DAG Composition](./patterns/dag-composition.md) |
| **Add retry/timeout/caching** | [Resilience Patterns](./patterns/resilience.md) |
| **Fix type errors** | [Error Handling](./patterns/error-handling.md) |
| **Use the HTTP API** | [HTTP API Reference](./reference/http-api.md) |
| **Embed in Scala app** | [Embedded API](./integration/embedded-api.md) |
| **Understand execution** | [DAG Execution](./foundations/dag-execution.md) |
| **Debug compiler errors** | [Error Codes Reference](./reference/error-codes.md) |

### 📚 Learning Path by Role

#### **Building Modules (Scala Developer)**
1. [Module Development Patterns](./patterns/module-development.md)
2. [Type System Foundations](./foundations/type-system.md)
3. [Module Options Reference](./reference/module-options.md)
4. [Module Registration](./integration/module-registration.md)

#### **Writing Pipelines (.cst files)**
1. [Pipeline Lifecycle](./foundations/pipeline-lifecycle.md)
2. [Type Syntax Reference](./reference/type-syntax.md)
3. [DAG Composition Patterns](./patterns/dag-composition.md)
4. [Error Handling](./patterns/error-handling.md)

#### **Deploying/Operating**
1. [Execution Modes](./foundations/execution-modes.md)
2. [HTTP API Reference](./reference/http-api.md)
3. [CLI Reference](./cli-reference.md)
4. [Resilience Patterns](./patterns/resilience.md)

#### **Extending Constellation**
1. [Embedded API](./integration/embedded-api.md)
2. [Module Registration](./integration/module-registration.md)
3. [Type Algebra](./patterns/type-algebra.md)

## Document Categories

### 🎯 Foundations
**Core mental models for reasoning about Constellation**

- [Type System](./foundations/type-system.md) - CType/CValue hierarchy and compatibility
- [Pipeline Lifecycle](./foundations/pipeline-lifecycle.md) - Parse → Execute journey
- [DAG Execution](./foundations/dag-execution.md) - How parallel execution works
- [Execution Modes](./foundations/execution-modes.md) - Hot vs cold, HTTP vs embedded

### 🎨 Patterns
**Practical implementation guides with examples**

- [Module Development](./patterns/module-development.md) - Step-by-step module creation
- [Resilience](./patterns/resilience.md) - Retry/timeout/cache patterns
- [DAG Composition](./patterns/dag-composition.md) - Parallel/serial patterns
- [Type Algebra](./patterns/type-algebra.md) - Record operations and type-driven design
- [Error Handling](./patterns/error-handling.md) - Diagnostics and recovery

### 📖 Reference
**Complete API documentation and lookup tables**

- [Type Syntax](./reference/type-syntax.md) - All CType variants
- [Module Options](./reference/module-options.md) - Complete with_clause reference
- [HTTP API](./reference/http-api.md) - REST endpoints
- [Error Codes](./reference/error-codes.md) - Error catalog with solutions

### 🔌 Integration
**Advanced usage for embedding and extending**

- [Embedded API](./integration/embedded-api.md) - Programmatic usage
- [Module Registration](./integration/module-registration.md) - Runtime module loading

## Key Files in This Guide

| Priority | File | Purpose |
|----------|------|---------|
| **P0** | [Getting Started](./getting-started.md) | Onboarding overview |
| **P0** | [Key Concepts](./key-concepts.md) | Terminology glossary |
| **P0** | [Where Constellation Shines](./where-constellation-shines.md) | Decision guide: is Constellation right for you? |
| **P0** | [Type System](./foundations/type-system.md) | Core mental model |
| **P1** | [Module Development](./patterns/module-development.md) | Most common task |
| **P1** | [CLI Reference](./cli-reference.md) | Command-line usage |
| **P1** | [Pipeline Lifecycle](./foundations/pipeline-lifecycle.md) | How compilation works |
| **P2** | [DAG Composition](./patterns/dag-composition.md) | Pipeline patterns |
| **P2** | [Resilience Patterns](./patterns/resilience.md) | Production patterns |
| **P3** | [HTTP API](./reference/http-api.md) | REST interface |
| **P3** | [Embedded API](./integration/embedded-api.md) | Advanced integration |

## Related Documentation

### Existing Resources (Link Instead of Duplicate)
- **[Cookbook](../cookbook/index.md)** - 23 runnable recipes for common tasks
- **[API Reference](../api-reference/http-api-overview.md)** - Detailed API documentation
- **[Language Reference](../language/index.md)** - constellation-lang syntax
- **[Getting Started](../getting-started/introduction.md)** - Human-focused introduction
- **[CLAUDE.md](https://github.com/VledicFranco/constellation-engine/blob/master/CLAUDE.md)** - Operational guidance for Claude

### Philosophy & Design
- **[organon/](https://github.com/VledicFranco/constellation-engine/tree/master/organon)** - LLM constraints and design philosophy
- **[Technical Architecture](../architecture/technical-architecture.md)** - Deep dive into internals

## How to Use This Guide

### Context Window Strategy
Each document is **500-2000 lines** for efficient context window usage. Don't try to read everything at once.

### Reading Protocol
1. **Start with task** - Use the "Common Tasks → Files" table above
2. **Read prerequisites** - Each file lists required reading
3. **Run examples** - All code is tested and runnable
4. **Check references** - Follow links for deeper details

### Code Example Convention
All examples follow this pattern:
```scala
// Scala module definition
case class MyInput(text: String)
case class MyOutput(result: String)

val myModule = ModuleBuilder
  .metadata("MyModule", "Description", 1, 0)
  .implementationPure[MyInput, MyOutput] { input =>
    MyOutput(input.text.toUpperCase)
  }
  .build
```

```constellation
# constellation-lang usage
in text: String
result = MyModule(text)
out result
```

### Decision Matrices
Many documents include decision matrices like this:

| Scenario | Solution | Trade-off |
|----------|----------|-----------|
| Pure computation | `.implementationPure` | No side effects allowed |
| Side effects needed | `.implementation` | Requires IO monad |
| Async operations | `.implementation` + `IO.async` | Complexity |

## Quick Reference Cards

### Type System at a Glance
```
CType (type level)          CValue (value level)
├─ CPrimitive              ├─ CPrimitive
│  ├─ CString               │  ├─ CString("text")
│  ├─ CInt                  │  ├─ CInt(42)
│  ├─ CDouble               │  ├─ CDouble(3.14)
│  └─ CBoolean              │  └─ CBoolean(true)
├─ CRecord                 ├─ CRecord
├─ CUnion                  ├─ CUnion
├─ CList                   ├─ CList
└─ COptional               └─ COptional
```

### Module Lifecycle
```
Define → Register → Parse → Type Check → Compile → Execute
```

### Execution Modes
```
Hot:  Precompiled → Fast execution → HTTP API
Cold: Compile on-demand → Flexible → Embedded API
```

## Getting Help

### When Documentation is Insufficient
1. **Check [Cookbook](../cookbook/index.md)** - Working examples
2. **Search [GitHub Issues](https://github.com/VledicFranco/constellation-engine/issues)** - Known problems
3. **Read [CLAUDE.md](https://github.com/VledicFranco/constellation-engine/blob/master/CLAUDE.md)** - Development conventions
4. **Explore [Tests](https://github.com/VledicFranco/constellation-engine/tree/master/modules)** - Real usage patterns

### Contributing
Found gaps in this documentation? See [CONTRIBUTING.md](https://github.com/VledicFranco/constellation-engine/blob/master/CONTRIBUTING.md)

---

**Ready to start?** → [Getting Started Guide](./getting-started.md)
