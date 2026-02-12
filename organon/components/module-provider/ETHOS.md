# Module Provider Ethos

> Normative constraints for the gRPC-based dynamic module registration system.

---

## Identity

- **IS:** Cross-process module registration protocol that lets external services contribute pipeline modules to Constellation at runtime via gRPC
- **IS NOT:** An in-process SPI (see [extensibility](../../features/extensibility/)), a module runtime, or a compiler

---

## Semantic Mapping

### SDK (`module-provider-sdk`)

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `CValueSerializer` | Converts CValue to/from bytes for gRPC transport |
| `JsonCValueSerializer` | JSON-over-bytes serialization using Circe codecs |
| `TypeSchemaConverter` | Bidirectional converter between protobuf TypeSchema and CType |
| `ConstellationProvider` | Entry point for provider developers: register modules, connect to instances |
| `ModuleDefinition` | A module's name, types, version, and handler function |
| `SdkConfig` | Provider configuration: ports, timeouts, canary, group ID |
| `CanaryConfig` | Canary rollout thresholds: observation window, health ratio, max latency |
| `InstanceConnection` | Manages lifecycle of a single connection to one Constellation instance |
| `InstanceConnectionState` | State machine: Disconnected, Registering, Active, Draining, Reconnecting |
| `CanaryCoordinator` | Sequential rollout of new modules with health-gated promotion |
| `CanaryResult` | Outcome: Promoted, RolledBack, PartialFailure |
| `ProviderTransport` | Interface for Register/Deregister/ControlPlane RPCs |
| `ControlPlaneHandler` | Callbacks for heartbeat acks, drain requests, reports |
| `ControlPlaneStream` | Sends heartbeats and drain acknowledgments to server |
| `ExecutorServerFactory` | Creates a gRPC server hosting ModuleExecutor service |
| `ModuleExecutorServer` | Dispatches ExecuteRequests to registered module handlers |
| `DiscoveryStrategy` | Resolves Constellation instance addresses (static or DNS) |
| `GrpcProviderTransport` | Production gRPC transport using ScalaPB stubs |
| `GrpcExecutorServerFactory` | Production gRPC server via Netty |

### Server (`module-provider`)

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `ModuleProviderManager` | Server-side orchestrator: validates, registers, and manages external modules |
| `ExternalModule` | Creates `Module.Uninitialized` backed by gRPC calls to a provider's executor |
| `ExternalFunctionSignature` | Creates `FunctionSignature` from validated module declarations for compiler |
| `SchemaValidator` | Validates namespaces, executor URLs, module names, and type schemas |
| `ControlPlaneManager` | Tracks provider connections, heartbeats, liveness, and drain state |
| `ProviderConnection` | Full state for one connected provider: connection ID, namespace, modules, timestamps |
| `ConnectionState` | Server-side state machine: Registered, Active, Draining, Disconnected |
| `ExecutorPool` | Load-balances ExecuteRequests across provider group members |
| `RoundRobinExecutorPool` | Thread-safe round-robin endpoint selection via `Ref[IO]` |
| `ExecutorEndpoint` | A provider's executor address paired with its connection ID |
| `GrpcChannelCache` | Reuses gRPC channels across module invocations |
| `ProviderManagerConfig` | Server config: gRPC port, heartbeat intervals, reserved namespaces |
| `ProviderInfo` | Summary of a provider connection for operational listing |

For complete type signatures, see:
- [io.constellation.provider](/organon/generated/io.constellation.provider.md) (if generated)

---

## Invariants

### 1. Namespace isolation

A namespace is exclusively owned by one provider (or one provider group). Different providers cannot register modules in the same namespace.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/module-provider/src/main/scala/io/constellation/provider/SchemaValidator.scala#validate` |
| Test | `modules/module-provider/src/test/scala/io/constellation/provider/SchemaValidatorSpec.scala#reject namespace owned by another provider` |

### 2. Reserved namespace protection

Providers cannot register modules in reserved namespaces (e.g., `stdlib`). This prevents external code from shadowing built-in functions.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/module-provider/src/main/scala/io/constellation/provider/SchemaValidator.scala#validate` |
| Test | `modules/module-provider/src/test/scala/io/constellation/provider/SchemaValidatorSpec.scala#reject reserved namespace` |

### 3. Connection lifecycle is server-authoritative

The server assigns connection IDs, tracks heartbeats, and enforces liveness. If a provider stops heartbeating, the server auto-deregisters its modules.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/module-provider/src/main/scala/io/constellation/provider/ControlPlaneManager.scala#startLivenessMonitor` |
| Test | `modules/module-provider/src/test/scala/io/constellation/provider/ConnectionLifecycleSpec.scala#auto-deregister when heartbeat stops` |

### 4. Type schema round-trip fidelity

`TypeSchemaConverter.toCType(toTypeSchema(t))` equals `t` for all supported CTypes. No information is lost in the protobuf representation.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/module-provider-sdk/src/main/scala/io/constellation/provider/TypeSchemaConverter.scala#toCType` |
| Test | `modules/module-provider-sdk/src/test/scala/io/constellation/provider/TypeSchemaConverterSpec.scala#round-trip CProduct` |

### 5. CValue serialization round-trip fidelity

`deserialize(serialize(v))` equals `Right(v)` for all CValue variants. Serialization errors surface as `Left`.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/module-provider-sdk/src/main/scala/io/constellation/provider/CValueSerializer.scala` |
| Test | `modules/module-provider-sdk/src/test/scala/io/constellation/provider/CValueSerializerSpec.scala#round-trip CProduct` |

### 6. Executor pool is thread-safe

`RoundRobinExecutorPool` uses `Ref[IO]` for all state mutations. Concurrent add/remove/next operations are safe.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/module-provider/src/main/scala/io/constellation/provider/ExecutorPool.scala#RoundRobinExecutorPool` |
| Test | `modules/module-provider/src/test/scala/io/constellation/provider/ExecutorPoolSpec.scala#cycle through endpoints` |

---

## Principles (Prioritized)

1. **Safety over convenience** — Validate schemas, namespaces, and URLs at registration time rather than at execution time
2. **Server authority** — The server owns connection lifecycle; providers are guests
3. **Graceful degradation** — Provider disconnection cleans up modules without crashing the runtime
4. **Transport independence** — SDK defines transport traits; gRPC is one implementation

---

## Decision Heuristics

- When adding a new RPC, define it in the `.proto` file and regenerate — the SDK owns all protobuf definitions
- When choosing between SDK and server for new logic, ask: "Does a provider developer need this?" If yes, SDK; if no, server
- When implementing load balancing, use the `ExecutorPool` trait — never select endpoints directly
- When handling connection loss, always deregister modules first, then clean up state

---

## Out of Scope

- In-process module registration (see [runtime](../runtime/))
- SPI interfaces for cache/metrics/listeners (see [extensibility](../../features/extensibility/))
- Pipeline compilation and type checking (see [compiler](../compiler/))
- HTTP API and dashboard (see [http-api](../http-api/))

---

## Implements Features

| Feature | Artifacts |
|---------|-----------|
| [Extensibility](../../features/extensibility/) | ConstellationProvider, ModuleProviderManager, ExternalModule, ProviderTransport |
