# Control Plane Operations

> **Path**: `organon/features/extensibility/control-plane.md`
> **Parent**: [extensibility/](./README.md)

The Control Plane is a bidirectional gRPC stream between a provider and the Constellation server that manages connection liveness, module health reporting, and graceful shutdown. Every registered provider **must** establish a Control Plane stream within a configurable timeout or be auto-deregistered.

## Connection Lifecycle

```
  Register RPC
       │
       ▼
  ┌───────────┐   ControlPlane stream opened   ┌──────────┐
  │ Registered │ ─────────────────────────────→ │  Active  │
  └───────────┘                                 └────┬─────┘
       │                                             │
       │ timeout (no stream)                         │ heartbeat timeout
       │ or error                                    │ or stream error
       ▼                                             ▼
  ┌──────────────┐   DrainRequest              ┌──────────────┐
  │ Disconnected │ ◄──────────────────────── │   Draining   │
  └──────────────┘   (after work completes)    └──────────────┘
                                                     │
                     Active ──DrainRequest──→ Draining ──work done──→ Disconnected
```

### State Machine

| State | Entry Condition | Behavior | Exit |
|-------|----------------|----------|------|
| `Registered` | `Register` RPC succeeds | Waiting for provider to open ControlPlane stream | → `Active` (stream opens) or → `Disconnected` (timeout) |
| `Active` | ControlPlane stream established | Heartbeats expected; `HeartbeatAck` + `ActiveModulesReport` sent | → `Draining` (drain requested) or → `Disconnected` (heartbeat lapse) |
| `Draining` | `DrainRequest` sent and `DrainAck` received | In-flight work completing; no new executions dispatched | → `Disconnected` (work done or deadline expires) |
| `Disconnected` | Timeout, stream error, or drain completion | Modules removed from registries; connection cleaned up | Terminal |

**Implementation:** `modules/module-provider/src/main/scala/io/constellation/provider/ControlPlaneManager.scala`

## Message Types

### Provider → Server

| Message | Purpose | Fields |
|---------|---------|--------|
| `Heartbeat` | Prove liveness | `connectionId`, `protocolVersion`, timestamp |
| `DrainAck` | Acknowledge drain request | `connectionId`, `inFlightCount` |

### Server → Provider

| Message | Purpose | Fields |
|---------|---------|--------|
| `HeartbeatAck` | Confirm heartbeat received | `connectionId`, `protocolVersion` |
| `ActiveModulesReport` | Inform provider which modules the server considers active | `activeModules[]` (short names, namespace prefix stripped) |
| `DrainRequest` | Request graceful shutdown | `reason`, `deadlineMs` |

## HeartbeatAck Semantics

- The server records the heartbeat timestamp on every valid `Heartbeat` from an `Active` connection
- `HeartbeatAck` is a confirmation — if the provider stops receiving acks, it should assume the server is unhealthy and initiate reconnection
- Heartbeats from connections in any state other than `Active` are ignored (no state transition)

## DrainRequest Semantics

1. Server calls `drainConnection(connectionId, reason, deadlineMs)` on `ControlPlaneManager`
2. If the connection is `Active` with a valid response observer, a `DrainRequest` is sent via the ControlPlane stream
3. Provider receives the request and sends a `DrainAck` with its current in-flight count
4. Server transitions the connection from `Active` → `Draining` upon receiving the ack
5. Provider completes in-flight work, then closes the ControlPlane stream
6. Server transitions to `Disconnected` and runs cleanup

**Drain use cases:**
- Rolling upgrades (deploy new version, drain old)
- Graceful scale-down
- Manual operator intervention

## Failure Modes

### Missed Heartbeats

| Scenario | Server Behavior |
|----------|-----------------|
| Provider process crashes | Heartbeat timeout fires → `Active` → `Disconnected` |
| Network partition | Same as crash — indistinguishable from server's perspective |
| Provider paused (GC, swap) | Timeout fires if pause exceeds `heartbeatTimeout` |

The liveness monitor runs every 1 second and checks:
- `Registered` connections: if `now - registeredAt > controlPlaneRequiredTimeout`, mark `Disconnected`
- `Active` connections: if `now - lastHeartbeatAt > heartbeatTimeout`, mark `Disconnected`
- `Draining` connections: not killed (graceful wind-down)

### Stream Errors

If the gRPC ControlPlane stream encounters a transport error:
1. The `StreamObserver.onError` callback fires on the server
2. Server calls `deactivateConnection(connectionId)` → `Disconnected`
3. Provider's SDK detects the stream break and initiates reconnection with exponential backoff

### Reconnection

The provider SDK (`InstanceConnection`) handles reconnection:
1. Detects stream close or error
2. Waits `reconnectBackoff` (initial: 1s, max: 60s, exponential)
3. Re-calls `Register` RPC to get a new `connectionId`
4. Opens a new ControlPlane stream
5. Resets backoff on successful reconnection

After `maxReconnectAttempts` consecutive failures, the SDK gives up and reports the instance as permanently unreachable.

## ControlPlaneManager Internals

### Thread Safety

All state is managed through `Ref[IO, Map[String, ProviderConnection]]` — an atomic, lock-free concurrent reference. State transitions use `modify` or `update` to ensure consistency without blocking.

### Background Fibers

Two `Resource`-managed fibers run for the lifetime of the server:

| Fiber | Interval | Purpose |
|-------|----------|---------|
| Liveness Monitor | 1 second | Checks all connections for heartbeat/registration timeouts |
| Active Modules Reporter | Configurable (`activeModulesReportInterval`) | Sends `ActiveModulesReport` to all active connections |

Both fibers are canceled automatically when the server shuts down (Resource release).

### Cleanup Callback

When a connection is declared dead, the `onConnectionDead` callback fires:
1. Closes the gRPC response observer stream (if present)
2. Removes modules from `ModuleRegistry` and `FunctionRegistry`
3. Removes executor endpoints from the `ExecutorPool`
4. If the connection was the last member of a provider group, releases the namespace

## Configuration

| Config | Default | Env Variable | Description |
|--------|---------|--------------|-------------|
| `heartbeatTimeout` | `15s` | `CONSTELLATION_PROVIDER_HEARTBEAT_TIMEOUT` | Max time between heartbeats before declaring dead |
| `controlPlaneRequiredTimeout` | `30s` | `CONSTELLATION_PROVIDER_CONTROL_PLANE_TIMEOUT` | Max time after Register to establish ControlPlane stream |
| `activeModulesReportInterval` | `30s` | — | How often to send ActiveModulesReport to providers |

## Group Interactions

For providers in a group (shared `group_id`):
- Each group member has its own independent ControlPlane stream
- Heartbeat failures affect only the individual member, not the group
- The `isLastGroupMember` check determines whether namespace cleanup happens on disconnect
- Solo providers (empty `group_id`) always trigger namespace cleanup on disconnect

## Related

- [module-provider.md](./module-provider.md) — Full protocol overview
- [Component: module-provider](../../components/module-provider/) — Implementation details
- [RFC-024](../../../rfcs/rfc-024-module-provider-protocol-v4.md) — Protocol specification
