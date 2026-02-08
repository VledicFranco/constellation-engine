# Constellation Engine Ethos

> Version 1.0 — Product-Level Organon
>
> Part of the [Organon System](./organon/README.md). See also: [PHILOSOPHY.md](./PHILOSOPHY.md)
>
> Behavioral constraints for LLMs working on this codebase.

---

## Identity

### What Constellation IS

- **A pipeline orchestration framework.** It composes modules into executable DAGs.
- **A type-safe DSL.** Field accesses and type operations are validated at compile time.
- **A Scala 3 library.** Modules are Scala functions; the runtime is Cats Effect.
- **An HTTP service.** Pipelines are executed via REST API or WebSocket LSP.

### What Constellation is NOT

- **Not a general-purpose language.** Don't add loops, conditionals beyond `when`/`branch`, or arbitrary computation.
- **Not a workflow engine.** No human-in-the-loop, no long-running processes, no saga patterns.
- **Not a streaming system.** Not for Kafka, Flink, or continuous data streams.
- **Not an ETL tool.** Not optimized for batch data processing. Use Spark/dbt for that.

---

## Core Invariants

These rules cannot be violated. They are the foundation of the system's integrity.

1. **Code is the source of truth.** When documentation conflicts with code, one must be fixed. Never leave conflicts unresolved.

2. **Types are structural.** Records are defined by their fields, not by name. Never introduce nominal typing.

3. **Modules are pure interfaces.** The DSL sees input type → output type. Implementation details stay in Scala.

4. **DAGs are acyclic.** Cycles in pipelines are compile-time errors. Never allow cyclic dependencies.

5. **Resilience is declarative.** Retry, timeout, fallback, cache are language constructs. Never embed resilience logic in modules.

6. **Parallelism is automatic.** Independent nodes execute concurrently. Never require manual parallelization.

---

## Design Principles

In priority order. When principles conflict, higher-ranked principles win.

### 1. Type Safety Over Convenience

Reject features that weaken compile-time guarantees, even if they would be convenient. A runtime error in production is worse than a stricter DSL.

### 2. Explicit Over Implicit

Prefer explicit syntax over magic. If behavior isn't visible in the code, it's harder to debug and understand.

### 3. Composition Over Extension

Add capabilities by composing existing constructs, not by extending the language. New syntax is expensive; new modules are cheap.

### 4. Declarative Over Imperative

Express *what* should happen, not *how*. The runtime decides execution strategy.

### 5. Simple Over Powerful

Prefer a simple solution that covers 90% of cases over a powerful solution that covers 100% but is harder to understand.

---

## Decision Heuristics

When facing ambiguity, use these guidelines:

### Adding Language Features

- **Does it require new syntax?** Prefer stdlib functions or module options over new syntax.
- **Does it weaken type safety?** Reject unless the trade-off is exceptional.
- **Can it be expressed with existing constructs?** Composition is preferred.
- **Is there a clear RFC?** Major features require an RFC before implementation.

### Modifying Type System

- **Does it preserve structural typing?** Never introduce nominal types.
- **Does it break existing programs?** Backward compatibility matters.
- **Is the inference decidable?** Type inference must terminate.

### Adding Module Options

- **Is it orthogonal to existing options?** Options should compose independently.
- **Does the compiler validate it?** Invalid options should be compile-time errors.
- **Is the default safe?** Unsafe defaults require explicit opt-in.

### Documentation Changes

- **Does it match the code?** Verify claims against implementation.
- **Is it in the right layer?** Philosophy explains why; ethos constrains behavior; reference documents facts.
- **Does it follow the structure?** README as router, feature-driven organization.

---

## Failure Philosophy

When things go wrong:

1. **Fail at compile time.** Catch errors before execution whenever possible.
2. **Fail with clear messages.** Error messages should identify the problem and suggest solutions.
3. **Fail visibly.** Never swallow errors silently. Surface failures to users.
4. **Fail safely.** When uncertain, reject rather than guess. A false negative is better than silent corruption.

---

## Component Boundaries

| Component | Responsibility | Does NOT do |
|-----------|----------------|-------------|
| `core` | Type system (CType, CValue) | Parsing, execution, HTTP |
| `lang-parser` | Syntax → AST | Type checking, execution |
| `lang-compiler` | AST → DAG, type checking | Execution, HTTP |
| `runtime` | DAG execution, ModuleBuilder | Parsing, HTTP |
| `lang-stdlib` | Standard library functions | Core language features |
| `lang-lsp` | IDE integration | Execution, HTTP serving |
| `http-api` | REST/WebSocket endpoints | Parsing, compilation (delegates) |

**Dependency rule:** Lower components cannot depend on higher ones. See `CLAUDE.md` for the full dependency graph.

---

## What To Do When Uncertain

1. **Check existing patterns.** How do similar features work?
2. **Consult the RFCs.** Is there a design document for this area?
3. **Read the tests.** Test cases often clarify intended behavior.
4. **Ask the user.** When genuinely ambiguous, ask rather than guess.

---

## What Is Explicitly Out of Scope

Do not add features for:

- General-purpose computation (loops, arbitrary functions)
- Long-running workflows (sagas, human-in-the-loop)
- Streaming data (Kafka, Flink integration)
- Batch ETL (Spark-style processing)
- Multi-tenancy (tenant isolation, per-tenant config)
- Distributed execution (cross-node DAG execution)

These may be future directions, but they are not current goals. Proposals require RFCs.
