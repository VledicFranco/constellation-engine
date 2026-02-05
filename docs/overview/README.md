# Overview

> **Path**: `docs/overview/`
> **Parent**: [docs/](../README.md)

High-level concepts and architecture of Constellation Engine.

## Contents

| File | Description |
|------|-------------|
| [when-to-use.md](./when-to-use.md) | Use cases, anti-patterns, comparison with alternatives |
| [architecture.md](./architecture.md) | System design, module dependencies, data flow |
| [glossary.md](./glossary.md) | Term definitions |

## Summary

Constellation is a **pipeline orchestration framework** with two layers:

1. **constellation-lang** (`.cst` files) - Declarative DSL for pipeline definition
2. **Scala runtime** - Module implementation, execution engine, HTTP API

The separation enables:
- Non-programmers can compose pipelines from existing modules
- Developers focus on module implementation, not orchestration
- Hot-reload pipelines without restarting the server
- Compile-time validation of pipeline structure

## Core Value

| Value | How |
|-------|-----|
| Type safety | Compile-time validation of field access and type operations |
| Automatic parallelization | Independent modules execute concurrently |
| Built-in resilience | Retry, timeout, fallback, caching as DSL options |
| Observability | Execution tracing, metrics hooks, DAG visualization |
