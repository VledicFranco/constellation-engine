# Glossary

> **Path**: `docs/overview/glossary.md`
> **Parent**: [overview/](./README.md)

## Core Concepts

| Term | Definition |
|------|------------|
| **Pipeline** | A directed acyclic graph (DAG) of modules that transforms inputs to outputs |
| **Module** | A named Scala function exposed to the DSL; the unit of execution |
| **DAG** | Directed Acyclic Graph; the compiled form of a pipeline |
| **Node** | A vertex in the DAG (input, module call, computed value, or output) |
| **Layer** | A set of nodes that can execute in parallel (no dependencies between them) |

## Language

| Term | Definition |
|------|------------|
| **constellation-lang** | The declarative DSL for defining pipelines (`.cst` files) |
| **Declaration** | `in`, `out`, or `type` statement in a pipeline |
| **Expression** | A value-producing construct (field access, merge, module call, etc.) |
| **Module option** | Orchestration modifier (`retry`, `timeout`, `cache`, etc.) |

## Types

| Term | Definition |
|------|------------|
| **CType** | Runtime type representation in the type system |
| **CValue** | Runtime value carrying its CType |
| **Record** | Structured type with named fields (`{ name: String, age: Int }`) |
| **Candidates** | Legacy alias for `List<T>`; used for batch processing |
| **Optional** | Type representing a value that may not be present |
| **Union** | Tagged variant type (`Success \| Error`) |

## Execution

| Term | Definition |
|------|------------|
| **Hot pipeline** | Compile + execute in one request (`POST /run`) |
| **Cold pipeline** | Pre-compiled and stored; execute by reference (`POST /execute`) |
| **Suspended execution** | Paused due to missing inputs; can be resumed later |
| **Structural hash** | Content-addressed identifier for a compiled pipeline (deterministic) |
| **Syntactic hash** | Hash of source text (includes whitespace, comments) |

## Resilience

| Term | Definition |
|------|------------|
| **Retry** | Re-execute a failed module N times |
| **Backoff** | Strategy for increasing delay between retries |
| **Fallback** | Default value returned when a module fails |
| **Throttle** | Rate limit (requests per time window) |
| **Concurrency limit** | Maximum parallel executions of a module |

## Infrastructure

| Term | Definition |
|------|------------|
| **SPI** | Service Provider Interface; extension point for custom backends |
| **LSP** | Language Server Protocol; enables IDE features |
| **Canary** | Gradual rollout of a new pipeline version with traffic splitting |
