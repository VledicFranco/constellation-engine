# RFC-024: Module Provider Protocol (v4)

**Status:** Draft (Revision 4 - Dynamic Registration)
**Priority:** P3 (Extensibility)
**Author:** Claude + User
**Created:** 2026-02-10
**Revised:** 2026-02-11
**Supersedes:** RFC-024 v1, v2, v3

---

## Summary

Define a **dynamic, type-safe Module Provider Protocol** using gRPC that allows external services to register and deregister modules with Constellation instances at runtime. Module providers are autonomous services that actively manage their own lifecycle — registering schemas, maintaining liveness, and coordinating upgrades across a Constellation cluster.

**Key Design Principles:**
- **Server-to-server:** Providers are peers, not clients. They initiate registration.
- **Provider-driven lifecycle:** The SDK manages cluster awareness, registration, liveness, and upgrades.
- **Registry as integration point:** The compiler, LSP, and runtime already consume the module registry. Dynamic registration simply changes how modules enter and leave it.
- **Type safety at registration:** Schema validation happens when a provider registers, not at execution time.
- **Facade pattern:** The Module Provider Manager wraps a Constellation instance, exposing the same API so existing infrastructure (http-api, LSP) works unchanged.

---

## Motivation

### Why Dynamic Over Static

Static configuration (v3) requires schema duplication across config files, SDK decorators, and service implementations. Any change requires redeploying configuration. Dynamic registration eliminates this:

- **Single source of truth:** The provider declares its own schema. Constellation validates it.
- **Zero-downtime evolution:** Providers register/deregister at runtime. No Constellation restart needed.
- **Cluster coordination:** SDKs manage multi-instance registration, enabling canary releases and rolling upgrades.

### Performance

gRPC with MessagePack binary serialization provides ~2.5x lower overhead than HTTP+JSON (~2ms vs ~5ms per call). For pipelines with many external modules, this compounds.

### Type Safety

Constellation's type system must extend across process boundaries. When a provider registers a module, Constellation validates the declared schema against its type system. Only validated modules enter the registry. Once registered, runtime execution trusts the validated types — no per-call type checking needed.

---

## Architecture

### System Overview

```
┌─────────────────┐     gRPC      ┌──────────────────────────────┐
│ Module Provider  │◄════════════►│  Constellation Instance A     │
│ (Python/Scala/   │              │  ┌────────────────────────┐   │
│  Node.js service)│              │  │ Module Provider Manager │   │
│                  │              │  │  ┌──────────────────┐  │   │
│  SDK handles:    │              │  │  │ Module Registry   │  │   │
│  - registration  │     gRPC     │  │  └──────────────────┘  │   │
│  - liveness      │◄═══════════►│  └────────────────────────┘   │
│  - cluster sync  │              │  │ Compiler │ LSP │ Runtime │  │
│  - canary deploy │              └──────────────────────────────┘
│                  │
│                  │     gRPC      ┌──────────────────────────────┐
│                  │◄════════════►│  Constellation Instance B     │
│                  │              │  (same structure)              │
└─────────────────┘              └──────────────────────────────┘
```

### Layer Decomposition

| Layer | Responsibility | Location |
|-------|---------------|----------|
| **Module Registry** | Add/remove/query modules | `core` or `runtime` (existing, needs dynamic ops) |
| **Module Provider Manager** | Bridges gRPC protocol to registry, validates schemas | New Scala package: `module-provider` |
| **Provider Protocol** | gRPC service definitions for register/deregister/execute | Protobuf definitions |
| **SDK** | Cluster awareness, lifecycle management, advanced features | Separate libraries (Scala first) |

---

## 1. Registry Semantics

The module registry is the central integration point. The compiler resolves modules from it, the LSP provides completions from it, the runtime executes from it. This section defines the dynamic operations the registry must support.

### Operations

| Operation | Semantics |
|-----------|-----------|
| **Add** | Register a new module. The fully qualified name (`namespace.name`) must be globally unique. Fails if: (a) same qualified name exists in a different provider's namespace (ownership violation), or (b) same qualified name exists in a reserved namespace (e.g., `stdlib.*`). |
| **Remove** | Deregister a module. Removes it from both registries. In-flight executions complete (modules bound at `initModules` time); new compilations no longer see it. |
| **Replace** | Atomic remove + add of a module within the same namespace by the same provider. Used for upgrades. |
| **Query** | List registered modules, their schemas, and their provider namespace. |

### Namespace Ownership (RESOLVED)

A provider's namespace is a **constellation-lang namespace** — the same dot-separated path used by `use` declarations in `.cst` scripts. This is not a new concept; it reuses the existing language-level namespace system.

**How it works today:** `FunctionSignature` already carries `namespace: Option[String]`:

```scala
FunctionSignature(
  name = "add",
  namespace = Some("stdlib.math"),
  moduleName = "stdlib.add",
  ...
)
```

Users access namespaced functions via `use` declarations:

```constellation
use ml.sentiment
result = analyze(text)             # wildcard import

use ml.sentiment as sent
result = sent.analyze(text)        # aliased import

result = ml.sentiment.analyze(text)  # fully qualified
```

**For module providers:** When a provider registers with namespace `"ml.sentiment"`, the Module Provider Manager creates `FunctionSignature` entries with `namespace = Some("ml.sentiment")`. The compiler, LSP, and runtime consume these identically to built-in namespaced functions.

**Ownership rules:**
- A provider's namespace is the dot-separated path it declares at registration (e.g., `"ml.sentiment"`).
- The namespace must be a valid constellation-lang namespace (dot-separated identifiers, no reserved words).
- A provider can only replace/remove modules **in its own namespace**.
- Cross-namespace name collisions are rejected.
- The namespace is validated syntactically at registration time (must parse as a valid `QualifiedName`).

**Implications:**
- `.cst` scripts are agnostic to whether a namespace is backed by local Scala modules or remote providers.
- The compiler's existing `NamespaceScope` resolution (wildcard imports, alias imports, fully qualified names) works without modification.
- The LSP autocomplete already enumerates functions by namespace — external modules appear automatically.

### Name Conflict Rules

- A module name must be unique within its namespace. The fully qualified name (`namespace.name`) must be globally unique.
- Registering a fully qualified name that already exists **in a different provider's namespace**: not a conflict (namespaces are isolated).
- Registering a name that already exists **in the same namespace by the same provider connection**: treated as a **Replace** operation (upgrade).
- Registering a name that already exists **in the same namespace by a different provider connection**: REJECTED (namespace ownership violation).
- Built-in modules (e.g., `stdlib.*`) occupy reserved namespaces and can never be replaced by providers.

### Namespace Ownership Identity

Constellation identifies provider ownership **by the active control plane connection**. The first provider to establish a control plane stream under a given namespace becomes its owner for the lifetime of that connection. If the connection drops (provider crash, network failure), the namespace is released and another provider may claim it.

**v1 trust model:** Namespace ownership is trust-based. Any process with network access can claim any namespace. This is acceptable for internal/trusted networks (same model as most service meshes).

**Future hardening:** mTLS with client certificates can bind namespaces to authenticated identities. The gRPC connection's client certificate CN could be validated against the declared namespace, preventing unauthorized claims. This is a natural Phase 2+ addition and does not require protocol changes (TLS is a transport concern).

### Registry Architecture (RESOLVED)

The codebase has **two separate registries** that must both be updated for dynamic registration:

| Registry | Purpose | Thread-safe | Consumed by |
|----------|---------|-------------|-------------|
| `ModuleRegistry` | Runtime module implementations | Yes (`Ref[IO]`) | Runtime (`initModules` at execution time) |
| `FunctionRegistry` | Compile-time type signatures | **No** (mutable `var`) | Compiler (`TypeChecker`) + LSP (completions) |

**Current behavior:** `constellation.setModule()` updates `ModuleRegistry` only. It does NOT update `FunctionRegistry`. A dynamically registered module would be executable but not compilable.

**Required changes for dynamic providers:**

1. **`ModuleRegistry`** — Add the `ExternalModule` implementation. Already supports runtime mutation and is thread-safe (`Ref[IO]`). **Needs a `deregister(name: String)` method** (currently only has `register`).
2. **`FunctionRegistry`** — Add a `FunctionSignature` with the module's types and namespace. **Requires refactoring to be thread-safe** (replace mutable `var` maps with `AtomicReference` or `Ref[IO]`). Also needs a `deregister(qualifiedName: String)` method.

**No notification mechanism needed:**
- **Compiler:** Reads `FunctionRegistry` by reference on each `compile()` call. If the registry is mutated safely, the next compilation picks up changes automatically.
- **Runtime:** `initModules()` reads `ModuleRegistry` at execution time. Already live.
- **LSP:** Function completions read `compiler.functionRegistry` directly (sees new entries immediately). Module completion trie rebuilds when `constellation.getModules()` returns different data.

**Prerequisite refactor:** Two changes needed before provider protocol work:
1. Make `InMemoryFunctionRegistry` thread-safe — replace `private var` maps with `AtomicReference` or `Ref[IO]`.
2. Add `deregister` methods to both `FunctionRegistry` and `ModuleRegistry` traits.

This is a bounded change and should be its own PR.

---

## 2. Provider Protocol

### gRPC Service Definitions

```protobuf
syntax = "proto3";
package constellation.provider;

// ===== Registration Protocol =====
// Hosted by: CONSTELLATION
// Provider connects to Constellation to register/deregister modules and maintain liveness.

service ModuleProvider {
  // Register one or more modules with this Constellation instance.
  // Constellation validates schemas and rejects on conflict or malformed types.
  rpc Register(RegisterRequest) returns (RegisterResponse);

  // Deregister modules by name. Only modules in the provider's namespace can be removed.
  rpc Deregister(DeregisterRequest) returns (DeregisterResponse);

  // Bidirectional control plane stream. Serves as:
  // 1. Liveness signal (stream break → auto-deregister)
  // 2. Application-level control channel (extensible via message types)
  rpc ControlPlane(stream ControlMessage) returns (stream ControlMessage);
}

// ===== Module Execution =====
// Hosted by: PROVIDER
// Constellation calls back to the provider's execution endpoint to run modules.
// The provider advertises its executor_url during registration.

service ModuleExecutor {
  rpc Execute(ExecuteRequest) returns (ExecuteResponse);
}
```

### Registration Messages

```protobuf
message RegisterRequest {
  // constellation-lang namespace (dot-separated, e.g., "ml.sentiment").
  // Must be a valid QualifiedName in the cst language.
  string namespace = 1;

  repeated ModuleDeclaration modules = 2;

  // Protocol version supported by this SDK.
  // Constellation will respond with its own version.
  // Both sides operate at min(provider_version, constellation_version).
  int32 protocol_version = 3;

  // The gRPC endpoint where Constellation can call ModuleExecutor.Execute().
  // e.g., "ml-service:9090". The provider hosts the ModuleExecutor service at this address.
  string executor_url = 4;
}

message ModuleDeclaration {
  string name = 1;              // Short name within the namespace (e.g., "analyze"). Fully qualified name = namespace + "." + name.
  TypeSchema input_schema = 2;  // CType-compatible input definition
  TypeSchema output_schema = 3; // CType-compatible output definition
  string version = 4;           // Semantic version (informational, used by SDK for canary tracking)
  string description = 5;       // Human-readable description
}

message RegisterResponse {
  bool success = 1;

  // Per-module results (in same order as request)
  repeated ModuleRegistrationResult results = 2;

  // Protocol version supported by this Constellation instance.
  // The negotiated version is min(provider_version, constellation_version).
  int32 protocol_version = 3;
}

message ModuleRegistrationResult {
  string module_name = 1;
  bool accepted = 2;
  string rejection_reason = 3;  // Empty if accepted
}
```

### Deregistration Messages

```protobuf
message DeregisterRequest {
  string namespace = 1;
  repeated string module_names = 2;  // Short names within the namespace (e.g., "analyze", not "ml.sentiment.analyze")
}

message DeregisterResponse {
  bool success = 1;
  repeated ModuleDeregistrationResult results = 2;
}

message ModuleDeregistrationResult {
  string module_name = 1;
  bool removed = 2;
  string error = 3;  // e.g., "not found", "wrong namespace"
}
```

### Control Plane Messages

```protobuf
// Unified control plane message. Both sides send and receive this type.
// Extensible via oneof — new message types can be added in future protocol versions
// without changing the RPC definition.
message ControlMessage {
  // Informational: echoes the negotiated version from registration.
  // Allows message handlers to check version without tracking connection state.
  int32 protocol_version = 1;

  oneof payload {
    // v1: Liveness
    Heartbeat heartbeat = 10;
    HeartbeatAck heartbeat_ack = 11;

    // v1: Reconciliation
    ActiveModulesReport active_modules_report = 12;

    // Reserved for future protocol versions:
    // v2: Drain, backpressure, schema hot-update, metrics push, config push
    // Fields 20-29 reserved for v2 control messages
  }
}

message Heartbeat {
  string namespace = 1;
  int64 timestamp = 2;
}

message HeartbeatAck {
  int64 timestamp = 1;
}

message ActiveModulesReport {
  // Constellation sends this periodically so the provider can reconcile
  repeated string active_modules = 1;
}
```

### Execution Messages

```protobuf
message ExecuteRequest {
  string module_name = 1;       // Short name within the provider's namespace (e.g., "analyze", not "ml.sentiment.analyze")
  bytes input_data = 2;         // MessagePack-encoded CValue
  string execution_id = 3;      // For tracing
  map<string, string> metadata = 4;
}

message ExecuteResponse {
  oneof result {
    bytes output_data = 1;      // MessagePack-encoded CValue
    ExecutionError error = 2;
  }
  ExecutionMetrics metrics = 3;
}

message ExecutionError {
  string code = 1;     // TYPE_ERROR, RUNTIME_ERROR, TIMEOUT, MODULE_NOT_FOUND
  string message = 2;
  string stack_trace = 3;
}

message ExecutionMetrics {
  int64 duration_ms = 1;
  int64 memory_bytes = 2;
}
```

### Type System Messages

```protobuf
message TypeSchema {
  oneof type {
    PrimitiveType primitive = 1;
    RecordType record = 2;
    ListType list = 3;
    MapType map = 4;
    UnionType union = 5;
    OptionType option = 6;
  }
}

message PrimitiveType {
  enum Kind {
    STRING = 0;
    INT = 1;
    FLOAT = 2;
    BOOL = 3;
  }
  Kind kind = 1;
}

message RecordType {
  map<string, TypeSchema> fields = 1;
}

message ListType {
  TypeSchema element_type = 1;
}

message MapType {
  TypeSchema key_type = 1;
  TypeSchema value_type = 2;
}

message UnionType {
  repeated TypeSchema variants = 1;
}

message OptionType {
  TypeSchema inner_type = 1;
}
```

### Liveness & Control Plane (RESOLVED)

The liveness mechanism is a **bidirectional gRPC stream** between provider and Constellation. This stream serves a dual purpose:

1. **Liveness detection.** If the stream breaks (provider crash, network partition), Constellation immediately deregisters the provider's modules. The provider SDK detects the break symmetrically and enters reconnection/re-registration logic.

2. **Application-level control plane.** The stream is the extensibility surface for the protocol. Beyond heartbeat pings, both sides can send typed messages to coordinate behavior. This avoids adding new RPC endpoints for every new feature.

**Current control plane messages (v1):**

| Direction | Message | Purpose |
|-----------|---------|---------|
| Provider → Constellation | `Heartbeat` | Liveness signal, carries provider timestamp |
| Constellation → Provider | `HeartbeatAck` | Liveness acknowledgement |
| Constellation → Provider | `ActiveModulesReport` | Periodic reconciliation of registered modules |

**Future control plane capabilities (v2+):**

The bidirectional stream can be extended with new message types in future protocol versions without changing the registration or execution RPCs:

| Capability | Direction | Description |
|------------|-----------|-------------|
| **Drain request** | Constellation → Provider | Ask provider to gracefully stop accepting new executions (for maintenance) |
| **Backpressure signal** | Constellation → Provider | Inform provider that execution queue is saturated |
| **Schema update** | Provider → Constellation | Hot-update a module's type schema without full deregister/register cycle |
| **Metrics push** | Provider → Constellation | Provider-side execution metrics (latency histograms, error rates) |
| **Configuration push** | Constellation → Provider | Runtime config changes (timeout overrides, feature flags) |

This makes the stream the natural evolution path for the protocol. New capabilities are added as message types, not new RPCs, keeping the core protocol surface stable across versions.

**Detection speed:** Application-level heartbeats give explicit control over detection latency (configurable interval, e.g., 5s ping with 15s timeout), independent of OS-level TCP keepalive settings.

**Control plane is required.** A provider must establish a control plane stream within a configurable timeout after registration (e.g., 30s). If the stream is not opened in time, the registration is revoked. This prevents orphaned registrations from providers that register and then disappear without liveness tracking.

**Constellation restart recovery.** If a Constellation instance restarts, all dynamically registered modules are lost (the registry is in-memory). The SDK detects the control plane stream break, enters reconnection logic, and re-registers all modules when the instance is available again. From the SDK's perspective, a Constellation restart is indistinguishable from a network partition — the recovery path is the same.

---

## 3. Module Provider Manager

A new Scala package (`module-provider`) that bridges the provider protocol to the Constellation registry.

**Position in the dependency graph:**

```
core → runtime → lang-compiler
                      ↓
              module-provider  (new — depends on runtime + lang-compiler)
                      ↓
                  http-api     (optional: can use module-provider-enhanced Constellation)
```

`module-provider` depends on `runtime` (for the `Constellation` trait, `ModuleRegistry`, `Module.Uninitialized`) and `lang-compiler` (for `FunctionRegistry`, `LangCompiler`). It also introduces gRPC and protobuf library dependencies.

### Design (RESOLVED: Decorator Pattern)

`Constellation` is a **trait**. All consumers (`ConstellationServer`, `ConstellationLanguageServer`) receive it as a trait-typed parameter. The codebase already uses the decorator pattern — `ConstellationServer.wrapWithStore` creates an anonymous `Constellation` that delegates to an underlying instance while intercepting specific methods.

`ModuleProviderManager` follows this same pattern:

```scala
class ModuleProviderManager(
  delegate: Constellation,
  compiler: LangCompiler,           // Needed to access FunctionRegistry for signature registration
  config: ProviderManagerConfig
) extends Constellation {

  // ===== Delegation (all standard Constellation operations) =====
  def PipelineStore: PipelineStore = delegate.PipelineStore
  def run(loaded: LoadedPipeline, inputs: Map[String, CValue], options: ExecutionOptions): IO[DataSignature] =
    delegate.run(loaded, inputs, options)
  def run(ref: String, inputs: Map[String, CValue], options: ExecutionOptions): IO[DataSignature] =
    delegate.run(ref, inputs, options)
  override def suspensionStore: Option[SuspensionStore] = delegate.suspensionStore
  def resumeFromStore(...): IO[DataSignature] = delegate.resumeFromStore(...)

  // ===== Enhanced module operations =====
  def getModules: IO[List[ModuleNodeSpec]] = delegate.getModules  // Includes external modules
  def getModuleByName(name: String): IO[Option[Module.Uninitialized]] = delegate.getModuleByName(name)
  def setModule(module: Module.Uninitialized): IO[Unit] = delegate.setModule(module)

  // ===== Provider-specific (not part of Constellation trait) =====
  // - Starts gRPC server accepting provider connections
  // - On Register: validates schema, checks conflicts, calls delegate.setModule() + compiler.functionRegistry.register()
  // - On Deregister: removes from both registries
  // - On ControlPlane stream: tracks liveness, auto-deregisters on disconnect
  def startProviderServer: IO[Unit] = ???
}
```

**Why decorator works well here:**
- **Transparent.** Downstream consumers (`ConstellationServer`, LSP) receive `Constellation` and are unaware of the wrapper.
- **Composable.** Can layer with other decorators if needed.
- **Production-proven.** Already used in the codebase (`wrapWithStore`).
- **Non-invasive.** No changes to `ConstellationImpl` or the `Constellation` trait.

### Responsibilities

| Responsibility | Detail |
|---------------|--------|
| **gRPC server** | Accepts incoming provider connections (Register, Deregister, ControlPlane) |
| **Schema validation** | Converts `TypeSchema` protobuf to `CType`, validates well-formedness and namespace validity |
| **Conflict detection** | Checks fully qualified names against existing registry (including built-ins) |
| **Dual registry mutation** | On register: adds `ExternalModule` to `ModuleRegistry` (via `delegate.setModule`) AND `FunctionSignature` to `FunctionRegistry` (via `compiler.functionRegistry`) |
| **Liveness tracking** | Monitors control plane streams, auto-deregisters on disconnect |
| **Facade** | Exposes the same `Constellation` trait so downstream consumers are unaware |

### Integration Point

```scala
// Without providers
val constellation = ConstellationImpl.builder().build()
val compiler = LangCompilerBuilder().withFunction(...).build()
val server = ConstellationServer.builder(constellation, compiler).run

// With providers — one line changes
val constellation = ConstellationImpl.builder().build()
val compiler = LangCompilerBuilder().withFunction(...).build()
val withProviders = ModuleProviderManager(constellation, compiler, providerConfig)
val server = ConstellationServer.builder(withProviders, compiler).run
// Everything else works identically. LSP, http-api, runtime all see Constellation trait.
```

---

## 4. SDK Contract

The SDK is a library that module provider services use. It handles all protocol complexity so that service developers only write module implementations.

### SDK Responsibilities

| Responsibility | Detail |
|---------------|--------|
| **Cluster awareness** | Knows all Constellation instances (via config or discovery). Maintains connections to each. |
| **Registration** | Registers modules with every instance. Retries on failure. |
| **Liveness** | Maintains heartbeat streams. Re-registers if connection drops and recovers. |
| **Execution server** | Hosts the `ModuleExecutor` gRPC service so Constellation can call back for execution. |
| **Canary releases** | Coordinates synchronized deregister/register across the cluster for upgrades. |
| **Graceful shutdown** | Deregisters all modules before process exit. |

### Cluster Awareness (RESOLVED)

**v1: Static instance list.** The SDK is configured with an explicit list of Constellation instance addresses. This is the simplest model and sufficient for most deployment scenarios.

```scala
val provider = ConstellationProvider(
  namespace = "ml.sentiment",
  instances = List("constellation-a:9090", "constellation-b:9090")
)
```

**Extensible via pluggable discovery.** The instance list is provided through a `DiscoveryStrategy` trait, making it straightforward to add DNS-based or service discovery backends later without breaking changes:

```scala
trait DiscoveryStrategy {
  def instances: IO[List[String]]
}

// v1: Static list
case class StaticDiscovery(addresses: List[String]) extends DiscoveryStrategy {
  def instances: IO[List[String]] = IO.pure(addresses)
}

// Future: DNS-based (Kubernetes headless services)
// case class DnsDiscovery(serviceName: String) extends DiscoveryStrategy
// Future: Service registry (Consul, etcd)
// case class ConsulDiscovery(consulUrl: String, serviceName: String) extends DiscoveryStrategy
```

**Constellation instances are stateless.** Instances do not know about each other and do not communicate. Each instance is independently authoritative for its own registry. The provider SDK is the coordination layer — it registers the same modules with every instance, so the cluster converges naturally.

**Why no gossip:** Gossip protocols (seed node + instance discovery) would require Constellation instances to maintain cluster membership state, handle split-brain scenarios, and synchronize registries. This adds significant complexity for a narrow benefit (detecting cross-instance namespace conflicts). The stateless model is simpler, easier to operate, and aligns with standard stateless service deployment patterns. Cross-instance consistency is an operational concern managed by the provider SDK, not a protocol concern.

### Replace Semantics and Consistency (RESOLVED)

**Per-instance:** Replace is atomic — a single registry operation that swaps the module implementation and function signature. DAGs already in flight are unaffected (modules are bound at `initModules` time); new DAGs pick up the replacement immediately.

**Cross-cluster:** Eventual consistency. During a rolling replace, some instances may have v1 while others have v2. This is the standard behavior of any rolling deployment and is explicitly accepted. The SDK controls the pace.

### Canary Release Protocol (RESOLVED)

Canary releases use **per-instance rolling replacement** orchestrated by the SDK. Constellation remains stateless — one module per qualified name per instance, no traffic splitting, no weighted routing.

**Flow:**

```
Provider v1 (running)          SDK                    Constellation Cluster
       │                        │                    [A: v1] [B: v1] [C: v1]
       │                        │
Provider v2 (starts)───────────▶│
       │                        │
       │              1. Replace v1→v2 on Instance A
       │                        ├──Register(v2)──────▶[A: v2] [B: v1] [C: v1]
       │                        │
       │              2. Monitor Instance A via control plane stream
       │                        │   (error rates, latency from ExecutionMetrics)
       │                        │
       │              3a. Healthy after threshold ──▶ proceed
       │                        ├──Register(v2)──────▶[A: v2] [B: v2] [C: v1]
       │                        │   ...
       │                        ├──Register(v2)──────▶[A: v2] [B: v2] [C: v2]
       │                        │
       │              3b. Unhealthy ──▶ rollback
       │                        ├──Register(v1)──────▶[A: v1] [B: v1] [C: v1]
       │                        │
Provider v1 (shuts down) ◀──────│  (only after full promotion)
```

**Key properties:**
- **Constellation is unaware of canary logic.** Each instance sees a normal Replace operation. The SDK controls the pace and decides whether to proceed or rollback.
- **The control plane stream provides feedback.** The SDK monitors `ExecutionMetrics` and errors from each instance to decide health.
- **v1 and v2 providers run simultaneously** during the canary window. The SDK manages connections to both and knows which version is registered on each instance.
- **`ModuleDeclaration.version`** gives the SDK the state to track what's deployed where.
- **`.cst` scripts are unaffected.** The qualified name (`ml.sentiment.analyze`) is unchanged — only the backing implementation changes.
- **Rollback is a Replace in the other direction.** Re-register v1 on any instance where v2 proved unhealthy.

### Scala SDK Example

```scala
// First SDK will be Scala
val provider = ConstellationProvider(
  namespace = "ml.sentiment",  // constellation-lang namespace
  instances = List("constellation-a:9090", "constellation-b:9090")
)

// Registers as ml.sentiment.analyze — users call via:
//   use ml.sentiment
//   result = analyze(input)
provider.register("analyze",
  inputType = CType.CRecord(Map("text" -> CType.CString)),
  outputType = CType.CRecord(Map(
    "sentiment" -> CType.CString,
    "confidence" -> CType.CFloat
  ))
) { input =>
  IO {
    val text = input.asRecord("text").asString
    val result = analyzeSentiment(text)
    CValue.CRecord(Map(
      "sentiment" -> CValue.CString(result.label),
      "confidence" -> CValue.CFloat(result.score)
    ))
  }
}

// Registers as ml.sentiment.embed
provider.register("embed",
  inputType = CType.CRecord(Map("text" -> CType.CString)),
  outputType = CType.CRecord(Map("vector" -> CType.CList(CType.CFloat)))
) { input =>
  IO { /* ... */ }
}

provider.start() // Registers with all instances, starts execution server, maintains liveness
```

**Usage in `.cst`:**
```constellation
in text: String

use ml.sentiment
result = analyze(text)
vector = embed(text)

out result
out vector
```

---

## 5. Type Validation

### When Validation Happens

Validation occurs **at registration time**, not at execution time. When a provider sends a `RegisterRequest`:

1. **Parse:** Convert `TypeSchema` protobuf messages to `CType` values.
2. **Well-formedness:** Verify the types are valid (no empty records, no unsupported primitives, etc.).
3. **Name check:** Verify module names are valid identifiers that can appear in constellation-lang.
4. **Conflict check:** Verify no name collision with existing modules (or same-namespace replace).
5. **Accept/Reject:** Return per-module results.

### What Is NOT Validated at Registration (RESOLVED: Schema-Only)

Constellation validates **schema well-formedness and conflict-freedom** at registration. It does **not** validate behavioral correctness or runtime type conformance.

This is the same trust model as native Scala modules. A module declared with `implementationPure[MyInput, MyOutput]` is trusted to return a valid `MyOutput` — the type system guarantees the contract at the boundary, but the implementation is responsible for honoring it. External modules are no different: the provider declares its schema, Constellation validates the schema, and the provider is trusted to conform at runtime.

**What is validated:**
- `TypeSchema` maps to a valid `CType` (well-formed types, no empty records, valid primitives)
- Module name is a valid constellation-lang identifier
- Namespace is a valid constellation-lang namespace path
- No name conflicts (or same-namespace replace)

**What is NOT validated:**
- Whether the provider actually produces outputs matching its declared schema (trusted, same as Scala modules)
- Functional correctness of the module logic

**Runtime safety net:** If a provider returns data that doesn't match the declared output schema, MessagePack deserialization will fail at execution time with a clear error. This is equivalent to a `ClassCastException` in a misbehaving Scala module — a bug in the implementation, not in the protocol.

---

## 6. Protocol Versioning (RESOLVED)

### Version Negotiation

Protocol versioning uses **version negotiation at registration time**:

1. The SDK sends `protocol_version` in `RegisterRequest` (the highest version it supports).
2. Constellation responds with its own `protocol_version` in `RegisterResponse`.
3. Both sides operate at `min(provider_version, constellation_version)` for the lifetime of the connection.
4. The SDK stores the negotiated version per Constellation instance (different instances in the cluster may be at different versions during rolling upgrades).

### Compatibility Guarantees

| Change type | Compatibility | Mechanism |
|-------------|--------------|-----------|
| New optional field on existing message | Wire-compatible | Protobuf ignores unknown fields |
| New `oneof` variant in `ControlMessage` | Wire-compatible | Old consumers see field as unset, ignore safely |
| New RPC method on existing service | Wire-compatible | Old clients never call it |
| New `TypeSchema` variant (e.g., `CDateTime`) | **Requires version bump** | Old Constellation rejects unknown type at registration with a clear error |
| Semantic change to existing field | **Requires major version bump** | New protobuf package (`constellation.provider.v2`) |

### Version Semantics

- **Minor version increments** (1 → 2 → 3): Additive, backwards-compatible changes. New control plane message types, new optional fields, new TypeSchema variants. Old consumers ignore what they don't understand; new consumers check the negotiated version before using new features.
- **Major version increments** (rare): Breaking changes to existing message semantics. Requires a new protobuf package (`constellation.provider.v2`). Constellation serves both packages simultaneously during migration. This should be avoided if possible.

### SDK Behavior

The SDK uses the negotiated version for **feature detection**:

```scala
// SDK knows what each Constellation instance supports
val negotiatedVersion = connectionState.negotiatedVersion(instanceUrl)

// Only use v2 features if the instance supports them
if (negotiatedVersion >= 2) {
  // Send drain-aware control plane messages
} else {
  // Fall back to v1 behavior
}
```

This allows the SDK to manage a heterogeneous cluster (some instances at v1, others at v2) during rolling Constellation upgrades.

### TypeSchema Evolution

When the CType system evolves (e.g., new `CDateTime` type added in protocol v2):
- A v2 provider registers a module with `CDateTime` in its schema.
- A v1 Constellation receives an unknown `TypeSchema` oneof variant → the field appears unset → registration is **rejected** with error: `"Unsupported type in schema. Provider protocol version 2 not supported by this instance (version 1)."`.
- The SDK can detect this via the negotiated version and either downgrade the schema or skip that instance.

---

## 7. Resilience Integration

All existing resilience clauses work with external modules because they operate at the execution layer, which is agnostic to whether a module is local or remote:

| Clause | Behavior with External Modules |
|--------|-------------------------------|
| `with retry: N` | Retries the gRPC `Execute` call on transient errors (`UNAVAILABLE`, `DEADLINE_EXCEEDED`) |
| `with timeout: Ns` | Sets gRPC deadline |
| `with cache: Nmin` | Caches results by input hash; cache hit skips gRPC call entirely |
| `with fallback: expr` | Returns fallback value on any execution error |

No changes needed to the resilience system. External modules are just modules.

---

## 8. Execution Flow

Once a module is registered, execution follows this path:

```
.cst script: "use ml.sentiment; result = analyze(text)"
  │
  ▼
Compiler
  ├── NamespaceScope resolves "analyze" → ml.sentiment.analyze
  ├── FunctionRegistry lookup → FunctionSignature (input/output types)
  ├── Type checking passes
  ├── DagCompiler links to ExternalModule by name
  │
  ▼
Runtime (executes DAG, reaches ExternalModule node)
  │
  ▼
ExternalModule.execute(input: CValue)
  │
  ├── Serialize input to MessagePack
  ├── gRPC Execute call to provider (at executor_url from registration)
  │   └── ExecuteRequest { module_name: "analyze", input_data: <bytes> }
  ├── Deserialize output from MessagePack
  │
  ▼
CValue output (continues DAG execution)
```

`ExternalModule` implements `Module.Uninitialized` — the same interface as local Scala modules. The runtime doesn't know or care that execution crosses a process boundary:

```scala
class ExternalModule(
  name: String,                // Short name (e.g., "analyze")
  namespace: String,           // constellation-lang namespace (e.g., "ml.sentiment")
  executorUrl: String,         // gRPC endpoint from RegisterRequest.executor_url
  inputType: CType,
  outputType: CType,
  grpcClient: ModuleExecutorGrpc.Client
) extends Module.Uninitialized {

  override val spec: ModuleNodeSpec = ModuleNodeSpec(
    name = s"$namespace.$name",
    // ... input/output type metadata
  )

  override def execute(input: CValue): IO[CValue] =
    for {
      inputBytes  <- IO(MessagePack.encode(input))
      response    <- grpcClient.execute(ExecuteRequest(
                       module_name = name,
                       input_data = inputBytes
                     ))
      output      <- response.result match {
                       case OutputData(bytes) => IO(MessagePack.decode(bytes, outputType))
                       case Error(err)        => IO.raiseError(ModuleError.ExecutionFailed(name, err.message))
                     }
    } yield output
}
```

**`ExecuteRequest.module_name` uses the short name** (e.g., `"analyze"`, not `"ml.sentiment.analyze"`). Constellation already routes to the correct provider by namespace — the provider only needs to know which of its own modules to execute.

---

## Open Questions Summary

| # | Question | Options | Status |
|---|----------|---------|--------|
| 1 | What is a namespace? | **Resolved:** constellation-lang namespace (dot-separated path, reuses existing `use`/`QualifiedName` system) | RESOLVED |
| 2 | Do consumers need registry change notifications? | **Resolved:** No notifications needed. Update both `ModuleRegistry` + `FunctionRegistry`. Prerequisite: make `FunctionRegistry` thread-safe. | RESOLVED |
| 3 | What is the liveness mechanism? | **Resolved:** Bidirectional gRPC stream. Doubles as control plane — extensibility surface for future protocol versions. | RESOLVED |
| 4 | Should Manager extend or wrap Constellation? | **Resolved:** Decorator (wrap). `Constellation` is a trait, pattern already used in codebase (`wrapWithStore`). | RESOLVED |
| 5 | How does the SDK discover instances? | **Resolved:** Static config (v1), pluggable `DiscoveryStrategy` for future DNS/service discovery. No gossip — Constellation stays stateless. | RESOLVED |
| 6 | How atomic does replace need to be? | **Resolved:** Per-instance atomic, cross-cluster eventual consistency. Canary via per-instance rolling replacement orchestrated by SDK (Model A). | RESOLVED |
| 7 | Should registration include test-case validation? | **Resolved:** Schema-only (v1). Same trust model as native Scala modules — schema validated at boundary, implementation trusted. | RESOLVED |
| 8 | Protocol versioning and backwards compatibility | **Resolved:** Version negotiation at registration. Both sides operate at `min(provider, constellation)`. Protobuf wire compat for additive changes; version bump for new TypeSchema variants. | RESOLVED |

---

## Implementation Phases

### Phase 0: Prerequisites
- Make `InMemoryFunctionRegistry` thread-safe (replace `private var` with `AtomicReference` or `Ref[IO]`)
- Add `deregister` to `FunctionRegistry` trait (`deregister(qualifiedName: String)`)
- Add `deregister` to `ModuleRegistry` trait (`deregister(name: String)`)
- This is a bounded refactor and should be its own PR

### Phase 1: Single-Instance Protocol
- Protobuf definitions and codegen
- `ExternalModule` implementation (wraps gRPC `ModuleExecutor` call with MessagePack ser/de)
- Module Provider Manager (decorator, gRPC server, schema validation, dual registry mutation)
- Scala SDK (single-instance registration, execution server hosting)
- Register, Deregister, Execute working end-to-end on a single Constellation instance

### Phase 2: Cluster & Lifecycle
- Static `DiscoveryStrategy` (SDK registers with multiple instances)
- Control plane stream (liveness detection, auto-deregister on disconnect)
- SDK reconnection and re-registration logic
- Graceful shutdown (deregister all modules before process exit)
- Version negotiation

### Phase 3: Advanced Operations
- Canary release coordination (per-instance rolling replacement with health monitoring)
- `DiscoveryStrategy` extensibility (DNS-based discovery)
- Python SDK
- Node.js SDK
- Operational tooling (list providers, drain, metrics)

---

## Security Considerations

### v1: Trust-Based (Internal Networks)

The v1 protocol operates on a trust-based model suitable for internal/trusted networks:

- **No authentication.** Any process with network access to the Constellation gRPC port can register modules.
- **No authorization.** Any namespace can be claimed by any provider (first-come ownership).
- **No encryption.** gRPC connections are plaintext unless TLS is configured at the transport level.

This is the same trust model as most internal service meshes and is acceptable for development and internal production environments.

### Future Hardening Path

| Concern | Mitigation | Phase |
|---------|-----------|-------|
| **Authentication** | mTLS with client certificates. Constellation validates provider identity before accepting registration. | Phase 2+ |
| **Authorization** | Namespace ACLs. Map client certificate CNs to allowed namespaces. Prevent unauthorized namespace claims. | Phase 2+ |
| **Encryption** | TLS on all gRPC connections. Standard gRPC TLS configuration. | Phase 2+ |
| **Code execution** | External modules execute in the provider's process, not Constellation's. Constellation never runs untrusted code directly. The blast radius of a malicious provider is limited to the data it receives via `Execute` calls. | By design (v1) |

**Key safety property:** Constellation never executes provider-supplied code. It only sends data to providers and receives results. A malicious provider can return incorrect results but cannot compromise the Constellation process itself.

---

## Organon Compliance

| Principle | Compliance | Notes |
|-----------|------------|-------|
| **Type Safety Over Convenience** | PASS | Schema validated at registration. Types enforced at language boundary. Same trust model as native modules. |
| **Explicit Over Implicit** | PASS | Provider explicitly declares namespace and schema. Static instance discovery. No magic auto-discovery in v1. |
| **Composition Over Extension** | PASS | Extends via module registry (composition), not language syntax changes. Decorator pattern for integration. |
| **Declarative Over Imperative** | PASS | `.cst` scripts are unchanged — `use ml.sentiment; result = analyze(text)` is declarative. |
| **Simple Over Powerful** | PASS | Phased delivery: single-instance first, cluster second, canary third. Each phase is independently useful. |

---

## Trade-offs

| Aspect | Benefit | Cost |
|--------|---------|------|
| Dynamic registration | Zero-downtime evolution, single source of truth | More complex than static config |
| Provider-driven lifecycle | Provider controls its own fate, SDK handles complexity | SDK maintenance burden |
| Cluster awareness in SDK | Enables canary releases, rolling upgrades | SDK must handle partial failures, eventual consistency |
| Stateless Constellation instances | Simple operations, standard deployment patterns | No cross-instance conflict detection (operational concern) |
| Schema-only validation | Same trust model as Scala modules, fast registration | Provider bugs surface at runtime, not registration |
| gRPC transport | ~2.5x faster than HTTP+JSON | Protobuf codegen, harder debugging |
| Bidirectional control plane | Extensible, doubles as liveness signal | More protocol surface than simple heartbeat |

---

## Success Criteria

- Provider can register/deregister modules at runtime without Constellation restart
- Type validation rejects malformed schemas and name conflicts at registration time
- Registered external modules are usable in constellation-lang scripts immediately
- Compiler, LSP, and runtime work with external modules without logic changes (only prerequisite: thread-safe `FunctionRegistry` + `deregister` methods)
- SDK handles cluster registration with retry and liveness
- All resilience clauses work with external modules
- gRPC execution overhead <2ms per call
