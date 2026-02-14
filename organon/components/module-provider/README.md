# Module Provider

> **Path**: `organon/components/module-provider/`
> **Parent**: [components/](../README.md)

gRPC-based dynamic module registration system. Split into two sbt modules:

- **`module-provider-sdk`** — Lightweight client library for provider developers. Depends on `runtime`.
- **`module-provider`** — Server-side infrastructure. Depends on SDK + `lang-compiler`.

## Contents

| File | Description |
|------|-------------|
| [ETHOS.md](./ETHOS.md) | Constraints, semantic mapping, invariants |

## Key Files

### SDK (`modules/module-provider-sdk/`)

| File | Purpose |
|------|---------|
| `ConstellationProvider.scala` | Entry point: register modules, connect to instances |
| `ModuleDefinition.scala` | Module name, types, handler function |
| `SdkConfig.scala` | Configuration (ports, timeouts, canary, group ID) |
| `InstanceConnection.scala` | Single connection lifecycle management |
| `CanaryCoordinator.scala` | Sequential rollout with health-gated promotion |
| `CValueSerializer.scala` | CValue to/from bytes for gRPC transport |
| `TypeSchemaConverter.scala` | CType to/from protobuf TypeSchema |
| `transport.scala` | Transport, ControlPlane, and ExecutorServer traits |

### Server (`modules/module-provider/`)

| File | Purpose |
|------|---------|
| `ModuleProviderManager.scala` | Server-side orchestrator for registration |
| `ExternalModule.scala` | Creates `Module.Uninitialized` backed by gRPC |
| `SchemaValidator.scala` | Validates namespaces, URLs, names, schemas |
| `ControlPlaneManager.scala` | Tracks connections, heartbeats, liveness |
| `ExecutorPool.scala` | Load-balances across provider group members |
| `ProviderManagerConfig.scala` | Server configuration with env var support |

## Related

- [Feature: extensibility/module-provider](../../features/extensibility/module-provider.md)
- [Feature: extensibility/control-plane](../../features/extensibility/control-plane.md)
- [Feature: extensibility/cvalue-wire-format](../../features/extensibility/cvalue-wire-format.md)
- [RFC-024](../../../rfcs/rfc-024-module-provider-protocol-v4.md)
