# Runtime Component

> **Path**: `docs/components/runtime/`
> **Parent**: [components/](../README.md)
> **Module**: `modules/runtime/`

The execution engine that runs compiled DAGs.

## Key Files

| File | Purpose |
|------|---------|
| `Runtime.scala` | DAG execution engine |
| `ModuleBuilder.scala` | Fluent API for module creation |
| `Module.scala` | Module trait and execution |
| `GlobalScheduler.scala` | Priority-based task scheduling |
| `DeferredPool.scala` | Object pooling for performance |

## Subsystems

| Subsystem | Files | Description |
|-----------|-------|-------------|
| Execution | `Runtime.scala`, `LayerExecutor.scala` | Layer-based DAG execution |
| Modules | `ModuleBuilder.scala`, `Module.scala` | Module creation and invocation |
| Scheduling | `GlobalScheduler.scala` | Priority ordering, concurrency control |
| Resilience | `RetryExecutor.scala`, `CacheExecutor.scala`, `TimeoutExecutor.scala` | Option execution |
| SPI | `spi/*.scala` | Extension interfaces |

## Execution Flow

```
DagSpec + Inputs + Modules
         │
         ▼
    ┌─────────────────────┐
    │   Layer 0: Inputs   │
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │ Layer 1: [A, B, C]  │ ← parallel execution
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │   Layer 2: [D]      │
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │  Layer 3: Outputs   │
    └─────────────────────┘
         │
         ▼
    ExecutionResult
```

## SPI Extension Points

| Interface | Purpose | See |
|-----------|---------|-----|
| `CacheBackend` | Custom cache storage | [spi.md](./spi.md) |
| `MetricsProvider` | Custom metrics export | [spi.md](./spi.md) |
| `ExecutionListener` | Execution event hooks | [spi.md](./spi.md) |
| `ExecutionStorage` | Execution state persistence | [spi.md](./spi.md) |

## Features Implemented

| Feature | Runtime Role |
|---------|--------------|
| [Resilience](../../features/resilience/) | Executes retry, timeout, fallback, cache |
| [Parallelization](../../features/parallelization/) | Concurrent layer execution |
| [Execution modes](../../features/execution/) | Hot/cold/suspended execution |

## Dependencies

- **Depends on:** `core` (CType, CValue)
- **Depended on by:** `lang-compiler`, `http-api`
