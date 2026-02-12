---
title: "Module Provider"
sidebar_position: 6
description: "Build external module providers that contribute pipeline modules to Constellation via gRPC — enabling cross-process, cross-language, and independently scalable modules."
---

# Module Provider Integration Guide

## Overview

The Module Provider Protocol lets external services register pipeline modules with a running Constellation instance via gRPC. This enables modules written in any language, running in separate processes, with independent scaling and deployment lifecycles.

**Use cases:**
- ML inference services exposing models as pipeline modules
- Microservices contributing domain-specific transformations
- Polyglot pipelines (Python, Go, Rust modules alongside Scala)
- Horizontally scaled module pools with load balancing

:::tip When to Use
Use Module Providers when your module needs its own process, language runtime, or scaling strategy. For modules that run in the same JVM, use the standard [ModuleBuilder API](/docs/api-reference/programmatic-api#creating-modules) instead — it's simpler and faster.
:::

## How It Works

```
┌── Provider Process ──────────────────────────────────────┐
│  ConstellationProvider  ←→  ModuleExecutorServer (gRPC)  │
└────────────┬────────────────────────▲────────────────────┘
             │ Register / Heartbeat   │ Execute
             ▼                        │
┌── Constellation Server ─────────────┴────────────────────┐
│  ModuleProviderManager  →  ExternalModule (in pipeline)  │
└──────────────────────────────────────────────────────────┘
```

1. **Register** — Provider sends module declarations (name, input/output types) to the server
2. **Execute** — When a pipeline calls the module, the server sends an `ExecuteRequest` to the provider
3. **Heartbeat** — Provider maintains a control plane stream with periodic heartbeats
4. **Deregister** — Provider gracefully removes its modules on shutdown

## SDK Quick Start

Add the SDK dependency to your `build.sbt`:

```scala
libraryDependencies += "io.github.vledicfranco" %% "constellation-module-provider-sdk" % "<version>"
```

### Defining Modules

```scala
import io.constellation.{CType, CValue}
import io.constellation.provider.sdk._
import io.constellation.provider.JsonCValueSerializer
import io.grpc.ManagedChannelBuilder
import cats.effect.{IO, IOApp}

object MyProvider extends IOApp.Simple {

  val analyzeModule = ModuleDefinition(
    name = "Analyze",
    inputType = CType.CProduct(Map("text" -> CType.CString)),
    outputType = CType.CProduct(Map("sentiment" -> CType.CFloat, "confidence" -> CType.CFloat)),
    version = "1.0.0",
    description = "Sentiment analysis",
    handler = { input =>
      val text = input.asInstanceOf[CValue.CProduct].values("text")
        .asInstanceOf[CValue.CString].value
      // Your analysis logic here
      IO.pure(CValue.CProduct(Map(
        "sentiment" -> CValue.CFloat(0.85),
        "confidence" -> CValue.CFloat(0.92)
      )))
    }
  )

  def run: IO[Unit] = {
    for {
      provider <- ConstellationProvider.create(
        namespace = "ml",
        instances = List("localhost:9090"),
        config = SdkConfig(),
        transportFactory = { addr =>
          val Array(host, port) = addr.split(":")
          val channel = ManagedChannelBuilder.forAddress(host, port.toInt).usePlaintext().build()
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

Once running, the module is available in constellation-lang:

```constellation
in text: String
result = ml.Analyze(text)
out result
```

### Configuration

```scala
import io.constellation.provider.sdk.{SdkConfig, CanaryConfig}
import scala.concurrent.duration._

val config = SdkConfig(
  executorPort = 9091,          // Port for receiving ExecuteRequests
  heartbeatInterval = 5.seconds,
  reconnectBackoff = 1.second,
  maxReconnectBackoff = 60.seconds,
  maxReconnectAttempts = 10,
  groupId = None,               // Set for horizontal scaling
  canary = CanaryConfig(
    observationWindow = 30.seconds,
    healthThreshold = 0.95,
    rollbackOnFailure = true
  )
)
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `executorPort` | `9091` | Port the provider listens on for ExecuteRequests |
| `heartbeatInterval` | `5s` | How often heartbeats are sent |
| `reconnectBackoff` | `1s` | Initial delay before reconnection attempt |
| `maxReconnectBackoff` | `60s` | Maximum delay between reconnection attempts |
| `maxReconnectAttempts` | `10` | Give up after this many consecutive failures |
| `groupId` | `None` | Shared group ID for horizontal scaling |

## Server-Side Setup

The server needs `ModuleProviderManager` wrapping a `Constellation` instance:

```scala
import io.constellation.provider.{ModuleProviderManager, ProviderManagerConfig}
import io.constellation.provider.JsonCValueSerializer
import io.constellation.impl.ConstellationImpl

for {
  constellation <- ConstellationImpl.builder().build()
  compiler      <- LangCompiler.builder.build
  manager       <- ModuleProviderManager(
    delegate   = constellation,
    compiler   = compiler,
    config     = ProviderManagerConfig(),
    serializer = JsonCValueSerializer
  )
  // manager is now a Constellation that also accepts gRPC registrations on port 9090
} yield manager
```

### Server Configuration

| Parameter | Default | Env Variable |
|-----------|---------|--------------|
| `grpcPort` | `9090` | `CONSTELLATION_PROVIDER_PORT` |
| `heartbeatTimeout` | `15s` | `CONSTELLATION_PROVIDER_HEARTBEAT_TIMEOUT` |
| `controlPlaneRequiredTimeout` | `30s` | `CONSTELLATION_PROVIDER_CONTROL_PLANE_TIMEOUT` |
| `reservedNamespaces` | `stdlib` | `CONSTELLATION_PROVIDER_RESERVED_NS` |

## Type System

Module providers declare input and output types using protobuf `TypeSchema` messages. The SDK converts between Constellation's `CType` and protobuf automatically.

### Supported Types

| CType | Protobuf Representation |
|-------|------------------------|
| `CString` | `PrimitiveType.STRING` |
| `CInt` | `PrimitiveType.INT` |
| `CFloat` | `PrimitiveType.FLOAT` |
| `CBoolean` | `PrimitiveType.BOOL` |
| `CProduct` | `RecordType` with named fields |
| `CList` | `ListType` with element type |
| `CMap` | `MapType` with key and value types |
| `COptional` | `OptionType` with inner type |
| `CUnion` | `UnionType` with variant types |

### Serialization

CValues are serialized to JSON bytes for transport using `JsonCValueSerializer`. This supports all CValue variants and provides human-readable wire format for debugging.

```scala
import io.constellation.provider.{CValueSerializer, JsonCValueSerializer}

val serializer: CValueSerializer = JsonCValueSerializer

// Serialize
val bytes: Either[String, Array[Byte]] = serializer.serialize(myValue)

// Deserialize
val value: Either[String, CValue] = serializer.deserialize(bytes)
```

## Horizontal Scaling (Provider Groups)

Multiple provider instances can serve the same namespace by sharing a `groupId`:

```scala
val config = SdkConfig(groupId = Some("ml-pool"))
```

The server maintains an `ExecutorPool` that load-balances ExecuteRequests across group members using round-robin selection.

```
Provider A (group_id: "ml-pool")  ──┐
Provider B (group_id: "ml-pool")  ──┼── namespace: "ml" → round-robin
Provider C (group_id: "ml-pool")  ──┘
```

**Rules:**
- All group members must register the same modules with compatible schemas
- Solo providers (no `groupId`) have exclusive namespace ownership
- A solo provider cannot join a group namespace, and vice versa
- When the last group member disconnects, the namespace is released

## Canary Rollout

Safely upgrade modules across all connected instances:

```scala
val newModules = List(analyzeModuleV2)
val result = provider.canaryRollout(newModules).unsafeRunSync()

result match {
  case CanaryResult.Promoted =>
    println("All instances upgraded successfully")
  case CanaryResult.RolledBack(reason) =>
    println(s"Rollback: $reason")
  case CanaryResult.PartialFailure(promoted, failed) =>
    println(s"Partial: ${promoted.size} ok, ${failed.size} failed")
}
```

The coordinator rolls out to one instance at a time, waits for the observation window, checks health, and either promotes or rolls back all instances.

## Namespace Rules

Providers register modules under a namespace (e.g., `ml`, `data.transform`). Namespaces:

- Use dot-separated segments: `ml`, `data.transform`, `company.service`
- Each segment must start with a letter and contain only letters, digits, and underscores
- Are case-sensitive
- Cannot use reserved prefixes (`stdlib` by default)
- Are exclusively owned by one provider or one provider group

In constellation-lang, namespaced modules are called with dot notation:

```constellation
result = ml.Analyze(text)
enriched = data.transform.Enrich(record)
```

## Validation

The server validates every registration request:

| Check | Error |
|-------|-------|
| Namespace syntax | Must be valid dot-separated identifiers |
| Namespace ownership | Cannot register in another provider's namespace |
| Reserved namespace | Cannot use `stdlib` prefix |
| Executor URL format | Must be valid `host:port` (no scheme prefix) |
| Module name format | Must start with letter, alphanumeric + underscores only |
| Type schema validity | Input and output schemas must be well-formed |

Failed validations return a `RegisterResponse` with `success = false` and a rejection reason per module.

## Connection Lifecycle

```
Provider                          Server
   │                                │
   ├── Register ──────────────────→ │  Validates, creates ExternalModule
   │                                │
   ├── ControlPlane (open stream) → │  Transitions to Active
   │                                │
   ├── Heartbeat ────────────────→  │  Updates lastHeartbeatAt
   │  ←── HeartbeatAck ────────── ─┤
   │  ←── ActiveModulesReport ─────┤
   │                                │
   │  ←── DrainRequest ────────────┤  (graceful shutdown)
   ├── DrainAck ──────────────────→ │
   │                                │
   ├── Deregister ────────────────→ │  Removes modules
   │                                │
```

If the provider stops sending heartbeats, the server auto-deregisters its modules after `heartbeatTimeout`.

## Gotchas

- **gRPC port vs HTTP port:** The Module Provider gRPC service (default 9090) is separate from the HTTP API (default 8080). Providers connect to the gRPC port.
- **Executor URL format:** Use `host:port` without a scheme prefix. `grpc://` or `http://` will be rejected.
- **Namespace persistence:** Namespaces are not persisted. If the server restarts, providers must re-register.
- **Latency overhead:** Cross-process modules add network round-trip latency. Use in-process modules for latency-critical operations.
- **Serialization cost:** CValues are serialized to JSON bytes for transport. Complex nested types incur serialization overhead.

## Related

- [Programmatic API](/docs/api-reference/programmatic-api) — In-process module registration
- [Cache Backend SPI](./cache-backend) — Example of in-process extensibility
- [Technical Architecture](/docs/architecture/technical-architecture) — How modules fit in the execution pipeline
- [Clustering](/docs/operations/clustering) — Running multiple Constellation instances
