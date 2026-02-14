# Module Provider Protocol

> **Path**: `organon/features/extensibility/module-provider.md`
> **Parent**: [extensibility/](./README.md)

The Module Provider Protocol extends Constellation's extensibility model across process boundaries. While [SPIs](./ETHOS.md) enable in-process pluggability (cache backends, metrics, listeners), the Module Provider Protocol enables external services to contribute pipeline modules to a running Constellation instance via gRPC.

## When to Use

| Scenario | Use Module Provider | Use In-Process Module |
|----------|--------------------|-----------------------|
| Module runs in a separate process (Python, Go, etc.) | Yes | No |
| Module needs independent scaling | Yes | No |
| Module needs independent deployment lifecycle | Yes | No |
| Module runs in the same JVM | No | Yes |
| Module needs sub-millisecond latency | No | Yes |

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Provider Process (external)                                     │
│  ┌──────────────────────┐  ┌─────────────────────────────────┐  │
│  │ ConstellationProvider│  │ ModuleExecutorServer (gRPC)     │  │
│  │  - register modules  │  │  - receives ExecuteRequest      │  │
│  │  - discover instances│  │  - dispatches to handler        │  │
│  │  - manage connections│  │  - returns ExecuteResponse      │  │
│  └──────────┬───────────┘  └───────────────▲─────────────────┘  │
│             │                               │                    │
└─────────────┼───────────────────────────────┼────────────────────┘
              │ Register/Deregister           │ Execute
              │ ControlPlane (heartbeat)      │
              ▼                               │
┌─────────────────────────────────────────────┼────────────────────┐
│  Constellation Server                        │                    │
│  ┌──────────────────────┐  ┌────────────────┴────────────────┐  │
│  │ ModuleProviderManager│  │ ExternalModule                  │  │
│  │  - validate schemas  │  │  - wraps gRPC call as Module    │  │
│  │  - track connections │  │  - serialize/deserialize CValue │  │
│  │  - manage lifecycle  │  │  - load-balance via ExecutorPool│  │
│  └──────────────────────┘  └─────────────────────────────────┘  │
│                                                                   │
│  ┌──────────────────┐  ┌────────────────┐  ┌─────────────────┐  │
│  │ SchemaValidator  │  │ ControlPlane   │  │ FunctionRegistry│  │
│  │                  │  │ Manager        │  │ (compiler)      │  │
│  └──────────────────┘  └────────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Protocol Flow

### Registration

1. Provider discovers Constellation instances (static addresses or DNS)
2. Provider calls `Register` RPC with namespace, executor URL, and module declarations
3. Server validates: namespace ownership, executor URL format, module names, type schemas
4. Server creates `ExternalModule` + `ExternalFunctionSignature` per module
5. Server registers modules in `ModuleRegistry` and signatures in `FunctionRegistry`
6. Server returns `RegisterResponse` with connection ID

### Control Plane

1. Provider opens bidirectional `ControlPlane` stream using connection ID
2. Provider sends periodic `Heartbeat` messages
3. Server sends `HeartbeatAck`, `ActiveModulesReport`, and `DrainRequest` messages
4. If heartbeat lapses, server auto-deregisters the provider's modules

### Execution

1. Pipeline references an external module (e.g., `ml.Predict`)
2. Runtime resolves module to `ExternalModule` backed by `ExecutorPool`
3. `ExecutorPool` selects an endpoint (round-robin for groups, single for solo)
4. Module serializes CValue input, sends `ExecuteRequest` to provider's executor
5. Provider's `ModuleExecutorServer` dispatches to the registered handler
6. Response is deserialized back to CValue and returned to the pipeline

### Deregistration

1. Provider calls `Deregister` RPC with connection ID and module names
2. Server removes modules from `ModuleRegistry` and `FunctionRegistry`
3. If all modules deregistered, server removes the connection

## Provider Groups (Horizontal Scaling)

Multiple provider instances can register under the same namespace with a shared `group_id`. The server maintains an `ExecutorPool` per namespace that load-balances across group members.

```
Provider A (group_id: "ml-pool")  ──┐
Provider B (group_id: "ml-pool")  ──┼── namespace: "ml" → ExecutorPool [A, B, C]
Provider C (group_id: "ml-pool")  ──┘
```

### Registration Rules

| Scenario | Result |
|----------|--------|
| Solo provider registers on free namespace | Namespace claimed exclusively |
| Group provider registers on free namespace | Namespace claimed for group |
| Group member joins existing group namespace | Added to `ExecutorPool` |
| Solo provider tries to join group namespace | **Rejected** — namespace conflict |
| Group provider tries to join solo namespace | **Rejected** — namespace conflict |
| Last group member disconnects | Namespace released |

### ExecutorPool Round-Robin

The `ExecutorPool` trait abstracts endpoint selection. The default `RoundRobinExecutorPool` implementation:

1. Maintains a `Vector[ExecutorEndpoint]` behind an atomic `Ref[IO, ...]`
2. Tracks a round-robin index that advances on each `next` call and wraps around
3. Is thread-safe — concurrent pipeline executions can safely call `next` simultaneously

```
next() call sequence with 3 endpoints [A, B, C]:
  Call 1 → A (idx 0)
  Call 2 → B (idx 1)
  Call 3 → C (idx 2)
  Call 4 → A (idx 0, wrapped)
```

**Pool operations:**

| Method | Behavior |
|--------|----------|
| `next` | Select next endpoint (round-robin). Raises error if pool is empty. |
| `add(endpoint)` | Idempotent — same `connectionId` replaces existing entry |
| `remove(connectionId)` | Returns `true` if pool is now empty (triggers namespace release) |
| `size` | Current number of healthy endpoints |
| `endpoints` | List all current endpoints |

**Implementation:** `modules/module-provider/src/main/scala/io/constellation/provider/ExecutorPool.scala`

### Scaling Patterns

**Scale out:** Start a new provider instance with the same `namespace` and `group_id`. It registers, opens a ControlPlane stream, and is added to the pool. The next `Execute` call may be routed to it.

**Scale in:** Stop a provider instance. Its ControlPlane heartbeat lapses, the liveness monitor marks it `Disconnected`, and `remove(connectionId)` is called on the pool. If other group members remain, the namespace stays active.

**Rolling upgrade:** Use `DrainRequest` to gracefully drain one instance at a time, upgrade it, re-register. The pool always has at least N-1 endpoints during the upgrade.

### Group Member Independence

Each group member has its own independent:
- `connectionId` (unique per registration)
- ControlPlane stream and heartbeat tracking
- Executor URL (may differ — different ports, hosts, containers)

Failure of one member does not affect others. The `isLastGroupMember` check ensures namespace cleanup only happens when the final member disconnects.

## Canary Rollout

The SDK's `CanaryCoordinator` orchestrates safe module upgrades:

1. For each connected instance, replace modules with new versions
2. Wait for observation window
3. Check health (ratio above threshold, latency below maximum)
4. If healthy: promote and continue to next instance
5. If unhealthy: roll back all instances to old modules

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `module-provider-sdk` | Client library for provider developers | `ConstellationProvider.scala`, `ModuleDefinition.scala`, `SdkConfig.scala` |
| `module-provider-sdk` | Serialization and type conversion | `CValueSerializer.scala`, `TypeSchemaConverter.scala` |
| `module-provider-sdk` | gRPC transport and executor hosting | `GrpcProviderTransport.scala`, `GrpcExecutorServer.scala` |
| `module-provider` | Server-side registration and validation | `ModuleProviderManager.scala`, `SchemaValidator.scala` |
| `module-provider` | Connection lifecycle management | `ControlPlaneManager.scala`, `ExternalModule.scala` |
| `module-provider` | Load balancing and pool management | `ExecutorPool.scala`, `GrpcChannelCache.scala` |
| `runtime` | Module and function registries | `ModuleRegistry`, `FunctionRegistry` |
| `lang-compiler` | FunctionSignature resolution | `FunctionSignature`, `SemanticType` |

## Configuration

### SDK (Provider-Side)

| Config | Default | Description |
|--------|---------|-------------|
| `executorPort` | `9091` | Port for ModuleExecutor gRPC service |
| `heartbeatInterval` | `5s` | Control plane heartbeat frequency |
| `reconnectBackoff` | `1s` | Initial reconnection backoff |
| `maxReconnectBackoff` | `60s` | Maximum reconnection backoff |
| `maxReconnectAttempts` | `10` | Max consecutive reconnection attempts |
| `groupId` | `None` | Optional group ID for horizontal scaling |

### Server-Side

| Config | Default | Env Variable | Description |
|--------|---------|--------------|-------------|
| `grpcPort` | `9090` | `CONSTELLATION_PROVIDER_PORT` | gRPC service port |
| `heartbeatTimeout` | `15s` | `CONSTELLATION_PROVIDER_HEARTBEAT_TIMEOUT` | Liveness timeout |
| `controlPlaneRequiredTimeout` | `30s` | `CONSTELLATION_PROVIDER_CONTROL_PLANE_TIMEOUT` | Control plane establishment deadline |
| `reservedNamespaces` | `stdlib` | `CONSTELLATION_PROVIDER_RESERVED_NS` | Protected namespace prefixes |

## TypeScript SDK

The TypeScript SDK (`@constellation-engine/provider-sdk`) provides a Node.js-native implementation of the Module Provider Protocol. It mirrors the Scala SDK's API but uses Promises instead of IO and explicit lifecycle instead of Resource.

**Package**: `sdks/typescript/`
**npm**: `@constellation-engine/provider-sdk`
**Organon**: See [components/typescript-sdk/](../../components/typescript-sdk/)

## Related

- [control-plane.md](./control-plane.md) — Control Plane operations (heartbeat, drain, liveness)
- [cvalue-wire-format.md](./cvalue-wire-format.md) — CValue JSON serialization contract
- [ETHOS.md](./ETHOS.md) — SPI constraints (in-process extensibility)
- [PHILOSOPHY.md](./PHILOSOPHY.md) — Why SPI over inheritance
- [Component: module-provider](../../components/module-provider/) — Implementation details
- [Component: typescript-sdk](../../components/typescript-sdk/) — TypeScript SDK details
- [RFC-024](../../../rfcs/rfc-024-module-provider-protocol-v4.md) — Full protocol specification
- [RFC-028](../../../rfcs/rfc-028-typescript-module-provider-sdk.md) — TypeScript SDK RFC
