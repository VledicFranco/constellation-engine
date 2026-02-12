---
title: "Module Provider Integration"
sidebar_position: 3
description: "Build cross-process module providers that contribute pipeline modules to Constellation via gRPC"
---

# Module Provider Integration

Build external services that register pipeline modules with a running Constellation instance via gRPC. Modules can be written in any language, run in separate processes, and scale independently.

## When to Use

| Scenario | Module Provider | In-Process Module |
|----------|----------------|-------------------|
| Module runs in a separate process | Yes | No |
| Module needs independent scaling | Yes | No |
| Module written in Python/Go/Rust | Yes | No |
| Module runs in the same JVM | No | Yes |
| Sub-millisecond latency required | No | Yes |

**Decision rule:** If your module needs its own process, language runtime, or scaling strategy, use Module Provider. Otherwise, use [ModuleBuilder](../patterns/module-development.md) for in-process modules.

## Architecture

```
Provider Process                    Constellation Server
┌──────────────────────┐           ┌──────────────────────────┐
│ ConstellationProvider │──Register─>│ ModuleProviderManager    │
│                      │──Heartbeat>│ ControlPlaneManager      │
│ ModuleExecutorServer │<─Execute──│ ExternalModule            │
│  (handles requests)  │           │ ExecutorPool (LB)         │
└──────────────────────┘           └──────────────────────────┘
```

**Flow:**
1. Provider calls `Register` RPC with namespace, executor URL, and module declarations
2. Server validates schemas, creates `ExternalModule` per module, registers with `ModuleRegistry`
3. Provider opens `ControlPlane` stream for heartbeats
4. When a pipeline calls the module, server sends `ExecuteRequest` to provider's executor
5. Provider runs handler, returns result

## SDK Quick Start

### 1. Define a Module

```scala
import io.constellation.{CType, CValue}
import io.constellation.provider.sdk._
import cats.effect.IO

val analyzeModule = ModuleDefinition(
  name = "Analyze",
  inputType = CType.CProduct(Map("text" -> CType.CString)),
  outputType = CType.CProduct(Map(
    "sentiment" -> CType.CFloat,
    "confidence" -> CType.CFloat
  )),
  version = "1.0.0",
  description = "Sentiment analysis",
  handler = { input =>
    val text = input.asInstanceOf[CValue.CProduct].values("text")
      .asInstanceOf[CValue.CString].value
    IO.pure(CValue.CProduct(Map(
      "sentiment" -> CValue.CFloat(0.85),
      "confidence" -> CValue.CFloat(0.92)
    )))
  }
)
```

### 2. Create and Start Provider

```scala
import io.constellation.provider.sdk._
import io.constellation.provider.{JsonCValueSerializer, GrpcProviderTransport}
import io.grpc.ManagedChannelBuilder
import cats.effect.{IO, IOApp}

object MyProvider extends IOApp.Simple {
  def run: IO[Unit] = {
    for {
      provider <- ConstellationProvider.create(
        namespace = "ml",
        instances = List("localhost:9090"),
        config = SdkConfig(),
        transportFactory = { addr =>
          val Array(host, port) = addr.split(":")
          val channel = ManagedChannelBuilder
            .forAddress(host, port.toInt).usePlaintext().build()
          new GrpcProviderTransport(channel)
        },
        executorServerFactory = new GrpcExecutorServerFactory(),
        serializer = JsonCValueSerializer
      )
      _ <- provider.register(analyzeModule)
      _ <- provider.start.useForever
    } yield ()
  }
}
```

### 3. Use in constellation-lang

```constellation
in text: String
result = ml.Analyze(text)
out result
```

## Configuration

### SDK (Provider-Side)

```scala
val config = SdkConfig(
  executorPort = 9091,            // Port for receiving ExecuteRequests
  heartbeatInterval = 5.seconds,
  reconnectBackoff = 1.second,
  maxReconnectBackoff = 60.seconds,
  maxReconnectAttempts = 10,
  groupId = None                  // Set for horizontal scaling
)
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `executorPort` | `9091` | Port the provider listens on |
| `heartbeatInterval` | `5s` | Heartbeat frequency |
| `reconnectBackoff` | `1s` | Initial reconnection delay |
| `maxReconnectBackoff` | `60s` | Maximum reconnection delay |
| `maxReconnectAttempts` | `10` | Max consecutive reconnect failures |
| `groupId` | `None` | Shared group ID for horizontal scaling |

### Server-Side

| Parameter | Default | Env Variable |
|-----------|---------|--------------|
| `grpcPort` | `9090` | `CONSTELLATION_PROVIDER_PORT` |
| `heartbeatTimeout` | `15s` | `CONSTELLATION_PROVIDER_HEARTBEAT_TIMEOUT` |
| `controlPlaneRequiredTimeout` | `30s` | `CONSTELLATION_PROVIDER_CONTROL_PLANE_TIMEOUT` |
| `reservedNamespaces` | `stdlib` | `CONSTELLATION_PROVIDER_RESERVED_NS` |

## Server-Side Setup

```scala
import io.constellation.provider.{ModuleProviderManager, ProviderManagerConfig}
import io.constellation.provider.JsonCValueSerializer

for {
  constellation <- ConstellationImpl.builder().build()
  compiler      <- LangCompiler.builder.build
  manager       <- ModuleProviderManager(
    delegate   = constellation,
    compiler   = compiler,
    config     = ProviderManagerConfig(),
    serializer = JsonCValueSerializer
  )
} yield manager
// manager is a Constellation that also accepts gRPC registrations on port 9090
```

## Type System Mapping

| CType | Protobuf |
|-------|----------|
| `CString` | `PrimitiveType.STRING` |
| `CInt` | `PrimitiveType.INT` |
| `CFloat` | `PrimitiveType.FLOAT` |
| `CBoolean` | `PrimitiveType.BOOL` |
| `CProduct` | `RecordType` |
| `CList` | `ListType` |
| `CMap` | `MapType` |
| `COptional` | `OptionType` |
| `CUnion` | `UnionType` |

Conversion is handled by `TypeSchemaConverter`. Round-trip fidelity: `toCType(toTypeSchema(t)) == t` for all supported types.

## Horizontal Scaling

Multiple providers can serve the same namespace by sharing a `groupId`:

```scala
val config = SdkConfig(groupId = Some("ml-pool"))
```

```
Provider A (group_id: "ml-pool")  ──┐
Provider B (group_id: "ml-pool")  ──┼── namespace: "ml" → round-robin
Provider C (group_id: "ml-pool")  ──┘
```

**Rules:**
- All group members must register the same modules with compatible schemas
- Solo providers (no `groupId`) have exclusive namespace ownership
- Solo and group cannot coexist in the same namespace
- Last group member disconnecting releases the namespace

## Namespace Rules

Dot-separated identifiers: `ml`, `data.transform`, `company.service`

| Rule | Example |
|------|---------|
| Segment starts with letter | `ml` (ok), `2ml` (rejected) |
| Alphanumeric + underscores | `my_service` (ok), `my-service` (rejected) |
| Case-sensitive | `ML` != `ml` |
| No reserved prefixes | `stdlib.foo` (rejected) |
| Exclusive ownership | One provider (or group) per namespace |

## Validation Checks

The server validates every `Register` request:

| Check | Rejection Reason |
|-------|-----------------|
| Namespace syntax | Invalid dot-separated identifiers |
| Namespace ownership | Namespace owned by another provider |
| Reserved namespace | `stdlib` prefix is protected |
| Executor URL format | Must be `host:port` (no scheme prefix) |
| Module name format | Must start with letter, alphanumeric + underscores |
| Type schema validity | Input/output schemas must be well-formed |

## Connection Lifecycle

```
Provider                          Server
   │                                │
   ├── Register ──────────────────> │  Validates, creates ExternalModule
   │                                │
   ├── ControlPlane (stream) ────-> │  Transitions to Active
   │                                │
   ├── Heartbeat ─────────────────> │  Updates lastHeartbeatAt
   │  <── HeartbeatAck ──────────── │
   │                                │
   │  <── DrainRequest ──────────── │  (graceful shutdown)
   ├── DrainAck ──────────────────> │
   │                                │
   ├── Deregister ────────────────> │  Removes modules
```

If heartbeat lapses, server auto-deregisters the provider's modules after `heartbeatTimeout`.

## Canary Rollout

Safely upgrade modules across connected instances:

```scala
val result = provider.canaryRollout(newModules).unsafeRunSync()
// CanaryResult: Promoted | RolledBack(reason) | PartialFailure(promoted, failed)
```

Rolls out to one instance at a time, waits for observation window, checks health, promotes or rolls back.

## Common Pitfalls

| Pitfall | Solution |
|---------|----------|
| Using `grpc://host:port` as executor URL | Use `host:port` only (no scheme prefix) |
| Connecting to HTTP port (8080) instead of gRPC port (9090) | Module Provider uses its own gRPC port |
| Forgetting `new` for `GrpcExecutorServerFactory` | It's a class, not a case class: `new GrpcExecutorServerFactory()` |
| `GrpcProviderTransport.apply(host, port)` returns `Resource` | Use the constructor with a `ManagedChannel` for non-Resource usage |
| Namespace not persisted across server restarts | Providers must re-register after server restart |

## Key Source Files

| Component | Module | Path |
|-----------|--------|------|
| SDK entry point | `module-provider-sdk` | `io.constellation.provider.sdk.ConstellationProvider` |
| Module definition | `module-provider-sdk` | `io.constellation.provider.sdk.ModuleDefinition` |
| CValue serialization | `module-provider-sdk` | `io.constellation.provider.CValueSerializer` |
| Type conversion | `module-provider-sdk` | `io.constellation.provider.TypeSchemaConverter` |
| Transport traits | `module-provider-sdk` | `io.constellation.provider.sdk.transport` |
| Server orchestrator | `module-provider` | `io.constellation.provider.ModuleProviderManager` |
| Schema validation | `module-provider` | `io.constellation.provider.SchemaValidator` |
| Connection lifecycle | `module-provider` | `io.constellation.provider.ControlPlaneManager` |
| Load balancing | `module-provider` | `io.constellation.provider.ExecutorPool` |

## See Also

- **[Module Registration](./module-registration.md)** - In-process module registration patterns
- **[Module Development](../patterns/module-development.md)** - Building in-process modules
- **[Embedded API](./embedded-api.md)** - Programmatic Constellation usage
- **[Integration Guide](/docs/integrations/module-provider)** - Full human-readable guide
- **[RFC-024](https://github.com/VledicFranco/constellation-engine/blob/master/rfcs/rfc-024-module-provider-protocol-v4.md)** - Protocol specification

---

**Back to:** [Module Registration](./module-registration.md) | **Up to:** [LLM Guide Index](../index.md)
