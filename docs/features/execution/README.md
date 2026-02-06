# Execution Modes

> **Path**: `docs/features/execution/`
> **Parent**: [features/](../README.md)

Hot, cold, and suspended execution modes for Constellation Engine pipelines.

## Ethos Summary

- **Hot** execution is the default for development (compile + run in one request)
- **Cold** execution is for production (pre-compiled, execute by reference)
- **Suspended** execution handles missing inputs gracefully
- Mode choice is **explicit**, not inferred

## Contents

| File | Description |
|------|-------------|
| [PHILOSOPHY.md](./PHILOSOPHY.md) | Why multiple execution modes exist |
| [ETHOS.md](./ETHOS.md) | Constraints for LLMs working on execution |
| [hot-execution.md](./hot-execution.md) | Compile and run in one request |
| [cold-execution.md](./cold-execution.md) | Pre-compiled, execute by reference |
| [suspension.md](./suspension.md) | Pause on missing inputs, resume later |

## Quick Reference

| Mode | Endpoint | Use Case |
|------|----------|----------|
| Hot | `POST /run` | Development, ad-hoc queries |
| Cold | `POST /compile` then `POST /execute` | Production, high throughput |
| Suspended | `POST /run` then `POST /executions/{id}/resume` | Multi-step workflows |

## Mode Selection Flowchart

```
┌─────────────────────────────────────────────┐
│          What is your use case?             │
└─────────────────────────────────────────────┘
                      │
         ┌────────────┼────────────┐
         ▼            ▼            ▼
   ┌──────────┐ ┌──────────┐ ┌──────────────┐
   │ Dev/Test │ │Production│ │Multi-step    │
   │ iteration│ │ API      │ │workflow      │
   └──────────┘ └──────────┘ └──────────────┘
         │            │            │
         ▼            ▼            ▼
   ┌──────────┐ ┌──────────┐ ┌──────────────┐
   │   HOT    │ │   COLD   │ │  SUSPENDED   │
   │ POST /run│ │/compile +│ │ /run + resume│
   └──────────┘ │ /execute │ └──────────────┘
               └──────────┘
```

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `http-api` | Endpoints for each mode | `ConstellationRoutes.scala`, `ExecutionHelper.scala` |
| `runtime` | Execution and suspension | `Runtime.scala`, `SuspendableExecution.scala` |
| `runtime` | State persistence | `SuspensionStore.scala`, `PipelineStore.scala` |
| `lang-compiler` | Compilation pipeline | `LangCompiler.scala` |

## See Also

- [docs/runtime/execution-modes.md](../../runtime/execution-modes.md) - Runtime-level details
- [docs/http-api/execution.md](../../http-api/execution.md) - HTTP API reference
- [docs/http-api/suspension.md](../../http-api/suspension.md) - Suspension API reference
