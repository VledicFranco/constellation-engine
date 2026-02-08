# Parallelization

> **Path**: `organon/features/parallelization/`
> **Parent**: [features/](../README.md)

Automatic concurrent execution of independent modules.

## Ethos Summary

- Parallelism is **automatic**, not manual
- Independent nodes execute **concurrently by default**
- Dependencies are **inferred from data flow**
- The scheduler respects **priority hints** when contention exists

## Contents

| File | Description |
|------|-------------|
| PHILOSOPHY.md | Why automatic parallelization matters |
| ETHOS.md | Constraints for LLMs working on scheduling |
| layer-execution.md | How layers are identified and executed |
| scheduling.md | Priority scheduler and concurrency control |
| performance.md | Tuning parallelization behavior |

## Quick Reference

```constellation
# These execute in parallel (no data dependency)
user = GetUser(id)
orders = GetOrders(id)
preferences = GetPreferences(id)

# This waits for all three (has data dependency)
profile = BuildProfile(user, orders, preferences)
```

## Execution Model

```
Layer 0: [inputs]
    │
    ▼ parallel
Layer 1: [GetUser, GetOrders, GetPreferences]
    │
    ▼ sequential (depends on layer 1)
Layer 2: [BuildProfile]
    │
    ▼
Layer 3: [outputs]
```

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `lang-compiler` | Dependency analysis, layer assignment | `DagCompiler.scala` |
| `runtime` | Parallel layer execution | `Runtime.scala`, `LayerExecutor.scala` |
| `runtime` | Priority scheduling | `GlobalScheduler.scala` |
