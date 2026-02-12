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

Multiple provider instances can register under the same namespace with a shared `group_id`. The server maintains an `ExecutorPool` that load-balances across group members.

```
Provider A (group_id: "ml-pool")  ──┐
Provider B (group_id: "ml-pool")  ──┼── namespace: "ml" → ExecutorPool [A, B, C]
Provider C (group_id: "ml-pool")  ──┘
```

**Rules:**
- Solo providers (no group_id) have exclusive namespace ownership
- Group providers share a namespace and must have matching schemas
- A solo provider cannot join an existing group namespace (and vice versa)
- When the last group member disconnects, the namespace is released

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
| `controlPlaneRequiredTimeout` | `30s` | `CONSTELLATION_PROVIDER_CP_TIMEOUT` | Control plane establishment deadline |
| `reservedNamespaces` | `stdlib` | `CONSTELLATION_PROVIDER_RESERVED_NS` | Protected namespace prefixes |

## Related

- [ETHOS.md](./ETHOS.md) — SPI constraints (in-process extensibility)
- [PHILOSOPHY.md](./PHILOSOPHY.md) — Why SPI over inheritance
- [Component: module-provider](../../components/module-provider/) — Implementation details
- [RFC-024](../../../rfcs/rfc-024-module-provider-protocol-v4.md) — Full protocol specification
