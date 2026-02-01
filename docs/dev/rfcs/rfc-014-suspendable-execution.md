# RFC-014: Suspendable Execution

**Status:** Draft
**Priority:** P1 (Core Runtime)
**Author:** Human + Claude
**Created:** 2026-01-30

---

## Summary

Add suspend/resume semantics to DAG execution and introduce a content-addressed program lifecycle model. When a program is executed with a subset of its declared inputs, the runtime executes as far as the dependency graph allows, then suspends — returning a `DataSignature` containing all computed values, execution metadata, and a structured `SuspendedExecution` IR that can be inspected, serialized, and used to resume execution later when additional inputs are provided. This enables long-lived, human-paced workflows (e.g. multi-step onboarding, approval gates) where execution spans hours or days and pauses for external input at each step.

To support this, the RFC also introduces a unified program lifecycle: compiled programs are split into serializable artifacts (`ProgramImage` — content-addressed, distributable) and executable runtime forms (`LoadedProgram` — with live synthetic modules). Programs are stored by structural hash with mutable name aliases, enabling run-by-reference, hot-loading, deduplication, and versioned rollback.

---

## Motivation

Constellation programs currently execute in a single pass: all inputs must be provided upfront, and execution either succeeds or fails. This works well for request/response pipelines but cannot model workflows where:

- A process requires **incremental human input** over time (e.g. a bank account opening that collects identity, address, employment, and funding information across separate sessions)
- A step requires **human approval** before the pipeline can proceed (e.g. a manager signs off on a computed risk score before the account is provisioned)
- The workflow is defined as a **single logical program** but its execution spans minutes, hours, or days

Today, library consumers who need this pattern must decompose their constellation program into multiple independent programs and manually stitch them together with application-level orchestration. This defeats the purpose of expressing the workflow as a single DAG — the dependency relationships, type safety, and execution optimizations are lost.

### What This Enables

A Scala developer embedding constellation can:

1. Define a complete multi-step workflow as a single `.cst` program with all inputs declared upfront
2. Execute the program with only the first step's inputs
3. Receive a `DataSignature` showing what was computed and what inputs are still missing
4. Inspect, serialize, and store the suspension state (in their database, Redis, S3 — wherever they choose, using whatever serialization format they prefer)
5. When the user returns with the next step's data, resume from the saved state
6. Repeat until all inputs are satisfied and the program completes

The DAG structure determines what can execute at each step — no explicit step annotations needed.

---

## Design Principles

| Principle | Rationale |
|-----------|-----------|
| **Implicit suspension** | Suspension points are derived from DAG dependencies, not declared in syntax. No language changes required. |
| **Incremental resumption** | Each resume provides some inputs, execution progresses as far as possible, suspends again if needed. |
| **Structured state** | The suspension state is a structured IR (`SuspendedExecution` case class) that the consumer can inspect, transform, and serialize using their preferred codec. The runtime does not manage persistence. |
| **Unified return type** | Every execution — whether it completes or suspends — returns a `DataSignature`. The consumer always handles the same type. |
| **Content-addressed identity** | Compiled programs are identified by the structural hash of their DagSpec (SHA-256). Names are mutable aliases. This enables deduplication, immutable references, and versioned rollback. |
| **Serializable vs executable** | `ProgramImage` (serializable artifact) and `LoadedProgram` (executable form) are distinct types. The serialization boundary is enforced at the type level. |
| **Consumer responsibilities** | Step validation/ordering, idempotency of side effects, state lifecycle (TTL, cleanup), and persistence are all consumer concerns. |

---

## Data Signature

The `DataSignature` is the unified return type for all DAG executions. It captures everything that happened during an execution, whether it completed or suspended.

### Core Fields (Always Present)

```scala
final case class DataSignature(
  // --- Identity ---
  executionId: UUID,                    // Unique ID for this execution run
  structuralHash: String,               // Structural hash of the compiled DagSpec (true semantic identity)
  resumptionCount: Int,                 // 0 for first run, increments on each resume

  // --- Status ---
  status: ExecutionStatus,              // Completed | Suspended | Failed

  // --- Data ---
  inputs: Map[String, CValue],         // All inputs provided (cumulative across resumptions)
  computedNodes: Map[String, CValue],  // All computed data node values (by variable name)
  outputs: Map[String, CValue],        // Resolved output values (subset of computedNodes)

  // --- Suspension Info ---
  missingInputs: Map[String, CType],   // Inputs not yet provided, with expected types
  pendingOutputs: List[String],        // Output names not yet resolved

  // --- State ---
  suspendedState: Option[SuspendedExecution], // Structured suspension IR (None if Completed)

  // --- Metadata ---
  metadata: SignatureMetadata           // Timing and execution metadata
)

sealed trait ExecutionStatus
object ExecutionStatus {
  case object Completed extends ExecutionStatus
  case object Suspended extends ExecutionStatus
  case class Failed(errors: List[ExecutionError]) extends ExecutionStatus
}
```

### Metadata (Baseline)

```scala
final case class SignatureMetadata(
  startedAt: Instant,
  completedAt: Instant,
  totalDuration: FiniteDuration,

  // --- Optional (controlled by flags) ---
  nodeTimings: Option[Map[String, FiniteDuration]],    // Per-node execution duration
  provenance: Option[Map[String, List[String]]],       // Per-node: which inputs it depended on
  blockedGraph: Option[Map[String, List[String]]],     // Per-node: which missing inputs block it
  resolutionSources: Option[Map[String, ResolutionSource]]  // Per-node: how value was obtained
)

/** Tracks how each node's value was obtained — important for audit trails
  * in regulated workflows (proving which values were computed vs manually provided).
  */
sealed trait ResolutionSource
object ResolutionSource {
  case object FromModuleExecution extends ResolutionSource   // Module ran and produced this value
  case object FromInput extends ResolutionSource             // Consumer provided as an input
  case object FromManualResolution extends ResolutionSource  // Consumer injected via resolvedNodes
}
```

### Metadata Flags

```scala
final case class ExecutionOptions(
  includeTimings: Boolean = false,            // Per-node execution durations
  includeProvenance: Boolean = false,         // Per-node dependency traces
  includeBlockedGraph: Boolean = false,       // Which missing inputs block which nodes
  includeResolutionSources: Boolean = false   // Per-node: module execution vs input vs manual resolution
)
```

The baseline metadata (identity, status, timing totals) is always present with negligible overhead. The optional fields require extra bookkeeping during execution and are controlled by flags. The intended pattern:

- **Development:** Enable all flags for full observability and debugging
- **Production:** Disable flags for minimal overhead, enable selectively when investigating issues

---

## API Surface

### Executing with Suspension Support

Execution always goes through `Constellation`, which owns the module registry and merges registered modules (from `ModuleBuilder`) with synthetic modules (from compilation) internally. `LoadedProgram` is a pure data holder — it does not have a standalone `run` method.

```scala
trait Constellation {
  // --- Suspendable execution ---

  /** Execute a LoadedProgram. Merges registered + synthetic modules internally. */
  def run(
    loaded: LoadedProgram,
    inputs: Map[String, CValue],
    options: ExecutionOptions = ExecutionOptions()
  ): IO[DataSignature]

  /** Execute by reference (name alias or structural hash).
    * Resolves from ProgramStore, rehydrates, merges modules, executes.
    *
    * Convention: refs starting with "sha256:" are resolved as structural hashes
    * (prefix stripped, looked up by hash). All other refs are resolved as name aliases.
    * Name aliases cannot start with "sha256:".
    */
  def run(
    ref: String,
    inputs: Map[String, CValue],
    options: ExecutionOptions = ExecutionOptions()
  ): IO[DataSignature]

  /** Convenience: load from SuspensionStore, rehydrate program, merge modules, resume.
    * Requires a SuspensionStore to be configured via builder.
    */
  def resumeFromStore(
    handle: SuspensionHandle,
    additionalInputs: Map[String, CValue],
    resolvedNodes: Map[String, CValue] = Map.empty,
    options: ExecutionOptions = ExecutionOptions()
  ): IO[DataSignature]
}
```

### Resuming from Suspended State

```scala
object SuspendableExecution {
  /** Resume a suspended execution with additional inputs and/or manually resolved nodes.
    *
    * @param suspended The suspension IR from a previous execution
    * @param additionalInputs New inputs to provide for this resumption
    * @param resolvedNodes Manually provided values for unresolved data nodes (by variable name).
    *                      Typically used to heal failed modules or skip slow ones.
    * @param modules Synthetic modules from LoadedProgram (not stored in IR — see design note)
    * @param options Metadata flags for this execution
    */
  def resume(
    suspended: SuspendedExecution,
    additionalInputs: Map[String, CValue] = Map.empty,
    resolvedNodes: Map[String, CValue] = Map.empty,
    modules: Map[UUID, Module.Uninitialized],
    options: ExecutionOptions = ExecutionOptions()
  ): IO[DataSignature]
}
```

The `modules` parameter provides the synthetic modules (inline transforms, conditionals) generated by the compiler. These are Scala functions that cannot be serialized, so they are not part of the `SuspendedExecution` IR. Consumers obtain them from a `LoadedProgram` — either from the original compilation or by rehydrating a stored `ProgramImage` via `ProgramImage.rehydrate(image).syntheticModules`.

The `resolvedNodes` parameter allows the consumer to manually provide values for any unresolved data node by variable name. This serves two purposes:

- **Healing failed modules:** When a module fails (e.g. external API down), the consumer can bypass it by providing the output value directly, allowing downstream execution to continue.
- **Skipping modules:** The consumer can proactively provide a value for any unresolved node, even if its producing module hasn't failed — useful for skipping slow or expensive operations when the result is known through other means.

### Consumer Flow

```scala
// Compile the program once → get a CompilationOutput (program + warnings)
val output = compiler.compile(onboardingSource, "onboarding").toOption.get
output.warnings.foreach(w => logger.warn(w.message))  // Log warnings
val loaded: LoadedProgram = output.program

// Optionally store the image for future reference / other instances
val hash = programStore.store(loaded.image).unsafeRunSync()
programStore.alias("onboarding", hash).unsafeRunSync()

// Step 1: Initial execution with partial inputs
val sig1 = constellation.run(
  loaded,
  inputs = Map(
    "name"  -> CValue.CString("Alice"),
    "email" -> CValue.CString("alice@example.com")
  )
).unsafeRunSync()

sig1.status          // Suspended
sig1.missingInputs   // Map("address" -> CString, "ssn" -> CString, ...)
sig1.outputs         // Map.empty (outputs depend on later steps)
sig1.computedNodes   // Map("validated_email" -> CValue.CString("alice@example.com"), ...)

// Consumer can inspect the structured IR before persisting
val suspended = sig1.suspendedState.get
suspended.computedValues   // Examine what was computed
suspended.structuralHash   // Log for auditing

// Consumer serializes and stores (using built-in JSON codec or their own)
val bytes = SuspendedExecution.serialize(suspended).toOption.get
saveToDatabase(userId, bytes)

// --- Hours later, user returns (possibly on a different JVM instance) ---

// Step 2: Restore the LoadedProgram from stored image
val image = programStore.get(structuralHash).unsafeRunSync().get
val loaded = ProgramImage.rehydrate(image)   // Cheap — no compilation, no source needed

// Deserialize suspension state and resume
val bytes = loadFromDatabase(userId)
val suspended = SuspendedExecution.deserialize(bytes).toOption.get

val sig2 = SuspendableExecution.resume(
  suspended,
  additionalInputs = Map("address" -> CValue.CString("123 Main St")),
  modules = loaded.syntheticModules  // From rehydrated LoadedProgram
).unsafeRunSync()

sig2.status          // Suspended (still missing ssn, funding_source, ...)
sig2.resumptionCount // 1
sig2.computedNodes   // Now includes address-dependent nodes too
sig2.inputs          // Cumulative: name, email, address

// --- More steps until complete ---

val sigN = SuspendableExecution.resume(
  lastSuspended,
  additionalInputs = Map("funding_source" -> CValue.CString("checking-account-id")),
  modules = loaded.syntheticModules
).unsafeRunSync()

sigN.status          // Completed
sigN.suspendedState  // None (execution is complete)
sigN.outputs         // Map("account_id" -> CValue.CString("ACC-12345"), ...)

// --- Healing a failed module ---

// If a module fails (e.g. CreditCheck external API is down):
val sigFailed = SuspendableExecution.resume(suspended, ...).unsafeRunSync()
sigFailed.status       // Failed(errors = List(ExecutionError("CreditCheck", ...)))
sigFailed.failedNodes  // Map("credit_score" -> ExecutionError(...))

// Option A: Retry after fixing the external condition
val sigRetry = SuspendableExecution.resume(
  sigFailed.suspendedState.get,   // State is preserved on failure
  modules = loaded.syntheticModules
).unsafeRunSync()

// Option B: Heal by providing the value manually
val sigHealed = SuspendableExecution.resume(
  sigFailed.suspendedState.get,
  resolvedNodes = Map("credit_score" -> CValue.CInt(750)),   // Bypass CreditCheck
  modules = loaded.syntheticModules
).unsafeRunSync()
// Downstream nodes (e.g. RiskAssess) now execute using the manually provided credit_score
```

### Projecting Outputs

The `DataSignature` provides utilities for accessing output values:

```scala
// On DataSignature:

/** Check if the execution produced all declared outputs. */
def isComplete: Boolean = status == ExecutionStatus.Completed

/** Get a specific output value, if resolved. */
def output(name: String): Option[CValue] = outputs.get(name)

/** Get a specific computed node value by variable name. */
def node(name: String): Option[CValue] = computedNodes.get(name)

/** Get all inputs that have been provided across all resumptions. */
def allInputs: Map[String, CValue] = inputs

/** Percentage of outputs resolved (0.0 to 1.0). */
def progress: Double

/** Nodes whose producing module failed, with error details. */
def failedNodes: Map[String, ExecutionError]
```

---

## Suspended Execution IR

The suspension state is a structured case class, not an opaque blob. This gives consumers full inspectability — they can examine, log, transform, and debug the state before choosing how and where to persist it.

### Structure

```scala
final case class SuspendedExecution(
  // --- Identity ---
  executionId: UUID,                                // Continuity across resumptions
  structuralHash: String,                           // Structural hash — detect program changes since suspension
  resumptionCount: Int,                             // How many times execution has been resumed

  // --- DAG ---
  dagSpec: DagSpec,                                 // The compiled DAG structure

  // --- Accumulated State ---
  providedInputs: Map[String, CValue],              // All inputs across all resumptions
  computedValues: Map[UUID, CValue],                // Results for all executed data nodes
  moduleStatuses: Map[UUID, Module.Status]          // Which modules have fired, with results
)
```

| Field | Purpose |
|-------|---------|
| `executionId` | Stable ID linking all resumptions of the same execution |
| `structuralHash` | Detect if the program semantically changed since suspension |
| `resumptionCount` | Track how many times execution has been resumed |
| `dagSpec` | The compiled DAG — needed to determine what to execute next. Intentionally embedded (not referenced by hash) so the IR is self-contained and resumable without a ProgramStore lookup. |
| `providedInputs` | Cumulative inputs across all resumptions |
| `computedValues` | `CValue` results for every data node that has been computed |
| `moduleStatuses` | Which modules have fired and their execution outcomes |

**Note:** Synthetic modules (compiler-generated functions for inline transforms and conditionals) are **not** stored in the IR. They are Scala functions that cannot be meaningfully serialized. The consumer provides them when calling `resume()` — typically from the same `LoadedProgram` used for the initial execution, or by rehydrating a stored `ProgramImage`. This keeps the IR fully serializable as pure data.

### Serialization

Serialization is abstracted behind a `SuspensionCodec` trait. The runtime ships with a Circe JSON implementation as the default. Consumers can swap in alternative codecs (binary, protobuf, encrypted) without changing any other code.

#### Codec Trait

```scala
trait SuspensionCodec {
  def encode(state: SuspendedExecution): Either[CodecError, Array[Byte]]
  def decode(bytes: Array[Byte]): Either[CodecError, SuspendedExecution]
}

case class CodecError(message: String, cause: Option[Throwable] = None)
```

#### Built-in: Circe JSON Codec

```scala
object CirceJsonSuspensionCodec extends SuspensionCodec {
  // Uses Circe encoders/decoders for all IR types.
  // Output is UTF-8 JSON bytes.
  def encode(state: SuspendedExecution): Either[CodecError, Array[Byte]]
  def decode(bytes: Array[Byte]): Either[CodecError, SuspendedExecution]
}
```

JSON is the default for v1 because:
- The project already depends on Circe
- JSON is human-readable, making stored states inspectable in databases
- Most IR types (`CValue`, `CType`, `DagSpec`) already have or are straightforward to derive Circe codecs for
- Debuggability during early adoption outweighs binary compactness

#### Convenience Methods

```scala
object SuspendedExecution {
  /** Serialize using the default JSON codec. */
  def serialize(state: SuspendedExecution): Either[CodecError, Array[Byte]] =
    CirceJsonSuspensionCodec.encode(state)

  /** Deserialize using the default JSON codec. */
  def deserialize(bytes: Array[Byte]): Either[CodecError, SuspendedExecution] =
    CirceJsonSuspensionCodec.decode(bytes)
}
```

#### Custom Codecs

Consumers who need a different format implement `SuspensionCodec`:

```scala
// Example: high-performance binary codec
object FurySuspensionCodec extends SuspensionCodec {
  def encode(state: SuspendedExecution): Either[CodecError, Array[Byte]] = ???
  def decode(bytes: Array[Byte]): Either[CodecError, SuspendedExecution] = ???
}

// Use it directly
val bytes = FurySuspensionCodec.encode(suspended)

// Or plug into the persistence middleware
val store = InMemorySuspensionStore(codec = FurySuspensionCodec)
```

The `SuspensionCodec` trait is intentionally minimal — encode and decode. No versioning, streaming, or schema evolution concerns are baked into the trait. Implementations handle those concerns internally if needed.

### Program Change Detection

When resuming, the runtime compares the stored `structuralHash` with the current program's structural hash. If they differ, the resume fails with a clear error:

```scala
case class ProgramChangedError(
  expectedStructuralHash: String,
  actualStructuralHash: String
) extends RuntimeException(
  s"Cannot resume: program has changed since suspension (expected=$expectedStructuralHash, actual=$actualStructuralHash)"
)
```

This prevents silently corrupting execution state when the program is modified between suspension and resumption.

---

## Execution Semantics

### Suspension Rules

1. **Missing input = suspension point.** A data node backed by an `in` declaration that has no provided value causes dependent nodes to be skipped.
2. **Maximum progress.** The runtime executes every node whose dependencies are fully satisfied. Nodes with no dependency on missing inputs execute normally.
3. **No partial node execution.** A module either fires completely or not at all. If any of its input edges are unsatisfied, the module is not fired.
4. **Side effects are final.** Once a module fires successfully, its result is captured in the suspended state and the module is never re-executed on resume.
5. **Failure preserves state.** When a module fails (after all retries are exhausted and `on_error` is not `skip`), the execution stops with `status = Failed` but `suspendedState` is still populated. The consumer can inspect what failed, fix the external condition and retry, or manually provide the failed node's value via `resolvedNodes`.

### Resumption Rules

1. **Additive inputs only.** Resumption adds new inputs; it cannot change or remove previously provided inputs.
2. **Replay, not re-execute.** Previously computed node values are replayed from the suspended state. Modules that already fired are not called again.
3. **Fresh nodes execute normally.** Nodes that were blocked in the previous run but are now unblocked (because their missing inputs have been provided) execute as normal IO operations.
4. **Incremental progress.** After resumption, the runtime may suspend again if there are still missing inputs blocking further progress.

### Input Validation on Resume

When `additionalInputs` are provided to `resume`, the runtime validates before executing:

| Case | Behavior | Rationale |
|------|----------|-----------|
| **Wrong type** — `CInt` where `CString` expected | Fail with `InputTypeMismatchError` | Cheap upfront check against `missingInputs` types. Prevents confusing module-level errors. |
| **Unexpected key** — not in `missingInputs` | Ignore silently | Consumer may provide inputs speculatively or for a later step. Strictness would make the API brittle. |
| **Duplicate key, same value** — already provided with identical value | Accept silently (no-op) | Idempotency — retries should not fail. |
| **Duplicate key, different value** — already provided with a different value | Fail with `InputAlreadyProvidedError` | Enforces Rule 1 (additive only). Changing a previously provided input would invalidate computed nodes that depended on it. |

```scala
case class InputTypeMismatchError(
  inputName: String,
  expectedType: CType,
  actualType: CType
) extends RuntimeException(
  s"Input '$inputName' has wrong type: expected $expectedType, got $actualType"
)

case class InputAlreadyProvidedError(
  inputName: String
) extends RuntimeException(
  s"Input '$inputName' was already provided with a different value. Inputs cannot be changed after provision."
)
```

### Node Resolution Validation

When `resolvedNodes` are provided to `resume`, the runtime validates before executing:

| Case | Behavior | Rationale |
|------|----------|-----------|
| **Unknown node** — name not in DagSpec | Fail with `UnknownNodeError` | Typo protection. Unlike inputs, node names are internal — the consumer should be precise. |
| **Wrong type** — CValue doesn't match node's CType | Fail with `NodeTypeMismatchError` | Prevents type-unsafe state from propagating downstream. |
| **Already resolved** — node already has a computed value | Fail with `NodeAlreadyResolvedError` | Preserves the "side effects are final" rule. Once a value exists, it can't be overwritten. |
| **Unresolved, module didn't fail** — module is pending/blocked | Accept | General-purpose: consumer can proactively provide values for any unresolved node, not just failed ones. |

```scala
case class UnknownNodeError(
  nodeName: String
) extends RuntimeException(
  s"Node '$nodeName' does not exist in the program."
)

case class NodeTypeMismatchError(
  nodeName: String,
  expectedType: CType,
  actualType: CType
) extends RuntimeException(
  s"Node '$nodeName' has wrong type: expected $expectedType, got $actualType"
)

case class NodeAlreadyResolvedError(
  nodeName: String
) extends RuntimeException(
  s"Node '$nodeName' already has a computed value and cannot be overwritten."
)
```

### Program Resolution Errors

When `Constellation.run(ref, ...)` cannot resolve a program reference:

```scala
case class ProgramNotFoundError(
  ref: String
) extends RuntimeException(
  s"No program found for ref '$ref'. Checked as name alias and structural hash."
)
```

### Interaction with Existing Features

| Feature | Interaction |
|---------|-------------|
| `retry` / `backoff` | Applied normally when a module fires. Retry state is not preserved across suspensions — a module that fires on resume gets fresh retry attempts. |
| `timeout` | Applied per-module-execution, not across suspensions. A 5s timeout means 5s per individual module call, not 5s for the entire multi-day workflow. |
| `cache` | Module-level caching applies within a single execution run. Cross-suspension caching is handled by the replay mechanism. |
| `priority` | Applied when scheduling module execution within a single run. Priority has no effect on suspension ordering. |
| `on_error` | Applied normally. A module that fails with `skip` error strategy produces a zero value that is captured in the suspended state like any other result. |
| `lazy` | Lazy values are forced during execution as needed. Forced values are captured in suspended state. |
| Cancellation | A suspended execution can be "cancelled" by simply discarding the state blob. No runtime action needed. |

---

## Program Lifecycle & Content-Addressed Storage

### Why This Refactor Is Needed

The suspension feature reveals a deeper insight: **an un-run DAG is structurally identical to a suspended execution at resumption 0** — no inputs provided, no nodes computed, all modules pending. Compiled programs and suspended executions are points on the same lifecycle continuum, not separate concepts.

The current implementation doesn't reflect this:

| Problem | Current State | Consequence |
|---------|--------------|-------------|
| **Mixed serializability** | `CompileResult` bundles serializable data (`DagSpec`) with non-serializable Scala functions (`syntheticModules`) in one type | Consumers can't store or transfer compiled programs without losing the ability to execute them |
| **No content addressing** | `DagRegistry` is `Map[String, DagSpec]` — name-only lookup | Two identical programs compiled under different names are stored twice; no deduplication, no immutable references |
| **Weak hashing** | Compilation cache uses `source.hashCode()` (Scala `hashCode`, not cryptographic) | Not portable across JVM versions, collisions possible, no meaningful identity |
| **Fragile cache** | `CachingLangCompiler` maintains a separate TTL-based cache, eviction loses synthetic modules | Cache miss after eviction requires full recompilation even when the DagSpec is still stored |
| **No run-by-reference** | HTTP API requires either sending source every time or pre-compiling by name | No immutable program references; name-based lookup can silently change when recompiled |

Building suspension on top of this model would propagate these problems into the suspension layer — `SuspendedExecution` would inherit the same mixed serializability, the same lack of content addressing, and the same fragile relationship with synthetic modules.

Instead of patching around these issues, this RFC introduces a unified program lifecycle model that cleanly separates serializable artifacts from executable runtime forms, and replaces name-only storage with content-addressed identity.

### Previous vs New Ontological Structure

#### Before (Current Implementation)

```
Source String
    ↓ LangCompiler.compile(source, dagName)
CompileResult                                    ← MIXED: serializable + non-serializable
├── dagSpec: DagSpec                             (serializable)
├── syntheticModules: Map[UUID, Module.Uninitialized]   (NOT serializable — Scala functions)
├── moduleOptions: Map[UUID, IRModuleCallOptions]       (serializable)
└── warnings: List[CompileWarning]               (serializable)
    ↓ constellation.setDag(name, compileResult.dagSpec)
DagRegistry: Map[String, DagSpec]                ← NAME-ONLY, no dedup, no versioning
    ↓ constellation.runDag(name, inputs)
Runtime.State                                    ← Single-shot result, no suspension
```

**Pain points for the consumer:**
- Must hold onto `CompileResult` in memory to access `syntheticModules` for `runDagWithModules`
- Cannot persist a compiled program and re-execute it later without recompilation
- Cannot reference a program by content — only by a mutable name
- No way to detect if a name was silently recompiled to a different program

#### After (New Model)

```
Source String
    ↓ compile()
LoadedProgram                                    ← CLEAR SPLIT: serializable image + runtime modules
├── image: ProgramImage                          (serializable — the distributable artifact)
│   ├── structuralHash: String                   (SHA-256 of canonical DagSpec — true identity)
│   ├── syntacticHash: String                    (normalized AST hash — cheap cache key)
│   ├── dagSpec: DagSpec
│   ├── moduleOptions: Map[UUID, IRModuleCallOptions]
│   ├── compiledAt: Instant
│   └── sourceHash: Option[String]               (provenance — hash of original source)
└── syntheticModules: Map[UUID, Module.Uninitialized]   (runtime-only — NOT in image)
    ↓ store.store(loaded.image)
ProgramStore                                     ← CONTENT-ADDRESSED with name aliases
├── images: Map[StructuralHash, ProgramImage]    (immutable, deduplicated)
├── aliases: Map[String, StructuralHash]         (mutable name → hash, like git branches)
└── syntacticIndex: Map[(SyntacticHash, RegistryHash), StructuralHash]  (compilation cache)
    ↓ constellation.run(loaded, inputs, options)
DataSignature                                    ← Unified result: completed, suspended, or failed
    ↓ (if suspended)
SuspendedExecution                               ← Serializable snapshot, resumable
    ↓ ProgramImage.rehydrate(image)
LoadedProgram                                    ← Executable again, no source needed
```

### Domain Types

```scala
/** Serializable compiled artifact — immutable, content-addressed, distributable.
  * Like a Docker image: store it, transfer it, instantiate it anywhere.
  */
final case class ProgramImage(
  // --- Identity ---
  structuralHash: String,                              // SHA-256 of canonical DagSpec (true identity)
  syntacticHash: String,                               // Normalized AST hash (cheap cache key)

  // --- Content ---
  dagSpec: DagSpec,                                    // The compiled DAG structure
  moduleOptions: Map[UUID, IRModuleCallOptions],       // Per-module runtime options

  // --- Provenance ---
  compiledAt: Instant,                                 // When this image was first created
  sourceHash: Option[String]                           // SHA-256 of original source (for auditing)
)

/** Executable form — a ProgramImage loaded into a running JVM with live synthetic modules.
  * Created by compiling source or rehydrating a ProgramImage. NOT serializable.
  * Pure data holder — execution goes through Constellation, which merges registered modules.
  */
final case class LoadedProgram(
  image: ProgramImage,
  syntheticModules: Map[UUID, Module.Uninitialized]    // Compiler-generated Scala functions
) {
  def structuralHash: String = image.structuralHash
}

object ProgramImage {
  /** Rehydrate a serializable image into an executable form.
    * Delegates to SyntheticModuleFactory to deterministically reconstruct
    * synthetic modules from DagSpec metadata (InlineTransform, ModuleNodeSpec).
    * Cheap — no compilation needed, no source code needed.
    */
  def rehydrate(image: ProgramImage): LoadedProgram =
    LoadedProgram(image, SyntheticModuleFactory.fromDagSpec(image.dagSpec))
}

/** Single source of truth for creating synthetic modules from DagSpec metadata.
  * Lives in constellation-runtime. Called by the compiler during compilation
  * and by ProgramImage.rehydrate() during deserialization recovery.
  *
  * Synthetic modules are deterministic pure functions derived from:
  * - DataNodeSpec.inlineTransform (merge, project, field access, conditional, etc.)
  * - ModuleNodeSpec input/output types
  * - DataNodeSpec.transformInputs wiring
  *
  * Dependency direction: compiler → runtime (calls factory), not the reverse.
  */
object SyntheticModuleFactory {
  def fromDagSpec(dagSpec: DagSpec): Map[UUID, Module.Uninitialized]
}
```

```scala
/** Compiler output — separates the reusable program from ephemeral compiler feedback.
  * LangCompiler.compile returns Either[List[CompileError], CompilationOutput].
  */
final case class CompilationOutput(
  program: LoadedProgram,
  warnings: List[CompileWarning]
)
```

**The serialization boundary is now a type-level guarantee:** if you have a `ProgramImage`, you can serialize it. If you have a `LoadedProgram`, you can execute it. The compiler produces `LoadedProgram` (both). Deserialization produces `ProgramImage` (artifact only). `rehydrate` bridges the gap.

### Two-Level Hashing

| Level | Name | Computed When | Algorithm | What It Catches | Cost |
|-------|------|--------------|-----------|----------------|------|
| **Syntactic** | `syntacticHash` | After parsing (~1ms) | SHA-256 of normalized AST | Variable renaming, whitespace, comments, reordering of independent statements | ~1ms |
| **Structural** | `structuralHash` | After compilation (free — byproduct) | SHA-256 of canonical DagSpec | Everything — true semantic identity. Two programs that compile to the same DAG get the same hash. | 0 (already compiled) |

**Syntactic normalization** (applied to parsed AST before hashing):

1. Alpha-normalize variable names to positional canonical forms (`_v0`, `_v1`, ...) based on declaration order
2. Strip comments and normalize whitespace
3. Sort independent declarations (statements with no data dependency between them)

**Structural normalization** (applied to DagSpec before hashing):

1. Canonicalize all UUIDs to deterministic positional identifiers
2. Sort all map entries by key
3. Normalize field ordering within specs

The syntactic hash is the **compilation cache key** — combined with a `registryHash`, it identifies a unique compilation context. If two source strings have the same syntactic hash and the module registry hasn't changed, they will compile to the same DagSpec, so compilation can be skipped entirely. The structural hash is the **storage identity** — the immutable fingerprint of what the program actually does.

**Registry hash computation:** SHA-256 of the sorted list of `(moduleName, majorVersion, minorVersion, inputTypeSignature, outputTypeSignature)` tuples for all registered `FunctionSignature`s. Sorting is lexicographic by `moduleName`. Type signatures use their canonical `CType.toString` form. This ensures that adding, removing, or changing any registered module invalidates the syntactic cache.

### Content-Addressed Program Store

The `ProgramStore` replaces the current `DagRegistry`. Programs are stored by structural hash (immutable, deduplicated) and referenced by name (mutable alias) or hash.

**Note:** `ProgramStore` always operates on raw hashes (no `sha256:` prefix). The `sha256:` convention is a consumer-facing concern — `Constellation.run(ref)` strips the prefix before delegating to `ProgramStore.get`.

```scala
trait ProgramStore {
  // --- Store ---
  /** Store a program image. Returns the structural hash.
    * If an image with the same structural hash already exists, this is a no-op (dedup).
    */
  def store(image: ProgramImage): IO[String]

  // --- Aliases (mutable name → structural hash) ---
  /** Point a name at a structural hash. Creates or updates the alias. */
  def alias(name: String, structuralHash: String): IO[Unit]

  /** Resolve a name to its current structural hash. */
  def resolve(name: String): IO[Option[String]]

  /** List all aliases with their current targets. */
  def listAliases: IO[Map[String, String]]

  // --- Retrieve ---
  /** Get a program image by structural hash. */
  def get(structuralHash: String): IO[Option[ProgramImage]]

  /** Get by name (resolve alias → retrieve image). */
  def getByName(name: String): IO[Option[ProgramImage]]

  // --- Syntactic index (compilation cache) ---
  /** Register (syntacticHash, registryHash) → structural hash mapping.
    * registryHash captures the module registry state (function signatures).
    * When the registry changes, old entries become naturally unreachable.
    */
  def indexSyntactic(syntacticHash: String, registryHash: String, structuralHash: String): IO[Unit]

  /** Lookup by syntactic + registry hash (cache hit = skip compilation). */
  def lookupSyntactic(syntacticHash: String, registryHash: String): IO[Option[String]]

  // --- Lifecycle ---
  def listImages: IO[List[ProgramImage]]
  def remove(structuralHash: String): IO[Boolean]
}
```

Names are **mutable aliases** — like git branch pointers. Compiling v2 of `"onboarding"` stores a new image and repoints the alias. The v1 image remains retrievable by its structural hash. This enables:

- **Rollback:** Repoint the alias to a previous hash
- **Blue-green:** Two aliases pointing to different versions, swap atomically
- **Immutable references:** Use structural hash in production configs for guaranteed stability
- **Deduplication:** Two programs that compile to the same DAG share one image

### Refactor Mapping: Current Types → New Types

| Current Type | Becomes | Nature of Change |
|-------------|---------|-----------------|
| `CompileResult` | `CompilationOutput` → `LoadedProgram` | Split. `dagSpec` + `moduleOptions` + hashes → `ProgramImage` (serializable). `syntheticModules` stays on `LoadedProgram` (runtime-only). `warnings` stays on the new `CompilationOutput` wrapper — ephemeral compiler feedback, not stored. |
| `DagRegistry` | `ProgramStore` | Replaced. `Map[String, DagSpec]` → content-addressed store with `images`, `aliases`, and `syntacticIndex`. |
| `DagRegistryImpl` | `ProgramStoreImpl` | Replaced. Three internal `Ref[IO, Map[...]]` instances. |
| `CompilationCache` | Absorbed | The syntactic index in `ProgramStore` replaces the standalone compilation cache. No separate cache layer — the store IS the cache. |
| `CachingLangCompiler` | Refactored | Instead of its own cache, delegates to `ProgramStore.lookupSyntactic(syntacticHash, registryHash)` on compile. On cache miss, compiles and stores. Registry changes naturally invalidate stale entries. |
| `Constellation.setDag` | Removed | Consumers use `ProgramStore.store` + `alias` directly. |
| `Constellation.getDag` | Removed | Consumers use `ProgramStore.getByName` or `get` directly. |
| `Constellation.runDag(name, inputs)` | `Constellation.run(ref, inputs)` | `ref` accepts name OR structural hash. Resolves to `ProgramImage`, rehydrates to `LoadedProgram`, executes. |
| `Constellation.runDagWithModules(...)` | `Constellation.run(loaded, inputs)` | Consumer passes `LoadedProgram` directly. |

### Impact on HTTP API

The HTTP API gains run-by-reference and content-addressed compilation:

| Endpoint | Before | After |
|----------|--------|-------|
| `POST /compile` | `{source, dagName}` → stores DagSpec by name | `{source, name?}` → returns `{structuralHash, syntacticHash, name?}`. Image always stored by hash. If `name` given, alias created. |
| `POST /execute` | `{dagName, inputs}` → name lookup only | `{ref, inputs}` where `ref` is a name OR structural hash |
| `POST /run` | `{source, inputs}` → ephemeral, not stored | `{source, inputs}` → compile (with syntactic cache), store image, execute. Returns `structuralHash` for future reference. |
| `GET /programs` | N/A | List all stored images with aliases |
| `GET /programs/:ref` | N/A | Program metadata by name or hash |
| `DELETE /programs/:ref` | N/A | Remove image (fails if aliases point to it) |
| `PUT /programs/:name/alias` | N/A | Repoint alias to a different structural hash |

### Use Cases

**Development mode — source on request:**

```scala
// Send source every time. Behind the scenes, syntactic hash avoids recompilation.
POST /run { "source": "in x: String\nresult = Uppercase(x)\nout result", "inputs": {"x": "hello"} }
// → { "status": "Completed", "outputs": {"result": "HELLO"}, "structuralHash": "sha256:abc123..." }

// Same source, different whitespace → syntactic hash matches → skip compilation
POST /run { "source": "in x : String\nresult=Uppercase( x )\nout result", "inputs": {"x": "world"} }
// → compilation skipped, same structuralHash, executed from cached image
```

**Production mode — run by reference:**

```scala
// Pre-compile during deployment
POST /compile { "source": "...", "name": "onboarding" }
// → { "structuralHash": "sha256:abc123...", "name": "onboarding" }

// Execute by name (mutable — follows alias)
POST /execute { "ref": "onboarding", "inputs": {...} }

// Execute by hash (immutable — guaranteed same program forever)
POST /execute { "ref": "sha256:abc123...", "inputs": {...} }
```

**Hot-loading — update without downtime:**

```scala
// Deploy new version
POST /compile { "source": "...(v2)...", "name": "onboarding" }
// → { "structuralHash": "sha256:def456..." }
// "onboarding" alias now points to v2. v1 still accessible by sha256:abc123...

// Rollback
PUT /programs/onboarding/alias { "structuralHash": "sha256:abc123..." }
// "onboarding" now points back to v1
```

### Connection to Suspension

An un-run `LoadedProgram` is a `SuspendedExecution` at resumption 0:

| | LoadedProgram (before run) | SuspendedExecution (mid-run) |
|---|---|---|
| Inputs provided | None | Some (cumulative) |
| Nodes computed | None | Some |
| Modules fired | None | Some |
| DagSpec | Present | Present |
| Can execute | Yes (with inputs) | Yes (with more inputs) |

This means `constellation.run(loaded, partialInputs)` and `SuspendableExecution.resume(suspended, moreInputs, modules)` are the same operation at different lifecycle points. The unified `DataSignature` return type works for both. The `structuralHash` in `ProgramImage` is the same `structuralHash` referenced in `SuspendedExecution` and `DataSignature` — a single identity threading through the entire lifecycle.

---

## Optional Persistence Middleware

The core runtime produces and consumes `SuspendedExecution` IR. An optional middleware module provides convenient persistence management for consumers who don't want to handle serialization and storage themselves.

### API

```scala
/** Pure persistence layer — CRUD only, no execution logic.
  * Stores and retrieves SuspendedExecution (pure data), not DataSignature (runtime metadata).
  * Resume logic lives on Constellation, which owns the module registry.
  */
trait SuspensionStore {
  /** Save a suspended execution. Returns a handle for retrieval. */
  def save(state: SuspendedExecution): IO[SuspensionHandle]

  /** Load a suspended execution by handle. */
  def load(handle: SuspensionHandle): IO[Option[SuspendedExecution]]

  /** Delete a suspended execution (cleanup). */
  def delete(handle: SuspensionHandle): IO[Boolean]

  /** List all suspended executions, optionally filtered. */
  def list(filter: SuspensionFilter = SuspensionFilter.All): IO[List[SuspensionSummary]]
}

final case class SuspensionHandle(id: String)

final case class SuspensionSummary(
  handle: SuspensionHandle,
  executionId: UUID,
  structuralHash: String,
  resumptionCount: Int,
  missingInputs: Map[String, CType],
  createdAt: Instant,
  lastResumedAt: Option[Instant]
)
// Note: `createdAt` and `lastResumedAt` are store-level metadata maintained by
// the SuspensionStore implementation, not derived from the SuspendedExecution IR.
// Implementations set `createdAt` on save() and update `lastResumedAt` on each
// subsequent save() of the same executionId.
```

### Backend SPI

The `SuspensionStore` is a trait that consumers implement for their storage backend:

```scala
// Provided by the consumer:
val store: SuspensionStore = InMemorySuspensionStore()  // Dev/test, uses CirceJsonSuspensionCodec
// or with custom codec:
val store: SuspensionStore = InMemorySuspensionStore(codec = FurySuspensionCodec)
// or fully custom backend:
val store: SuspensionStore = JdbcSuspensionStore(dataSource, codec = myCodec)  // Consumer implements
```

The constellation library ships with an `InMemorySuspensionStore` for development and testing. Production implementations are the consumer's responsibility — they choose their database, serialization, and lifecycle management.

### Plugging Into Constellation

```scala
// Consumer setup
val constellation = Constellation.builder()
  .withModules(myModules)
  .withSuspensionStore(store)  // Optional
  .build

// If store is configured, convenience methods are available:
val sig1   = constellation.run("onboarding", partialInputs).unsafeRunSync()
val handle = store.save(sig1.suspendedState.get).unsafeRunSync()
// ...later...
val sig2   = constellation.resumeFromStore(handle, moreInputs).unsafeRunSync()
```

---

## What Changes

| Component | Change |
|-----------|--------|
| `constellation-core` | `ExecutionStatus`, `ExecutionOptions`, `DataSignature`, `ProgramImage` case classes |
| `constellation-runtime` | `LoadedProgram`, `ProgramStore` (replaces `DagRegistry`), `SuspendableExecution` executor, `SuspendedExecution` IR, `SuspensionCodec` trait, `CirceJsonSuspensionCodec`, rehydration logic |
| `constellation-runtime` (optional) | `SuspensionStore` trait, `InMemorySuspensionStore` |
| `constellation-compiler` | `LangCompiler` returns `CompilationOutput` (`LoadedProgram` + warnings) instead of `CompileResult`. `CachingLangCompiler` delegates to `ProgramStore.lookupSyntactic`. Syntactic normalization + hashing added to parse phase. |
| `constellation-http-api` | `POST /execute` accepts name or structural hash. `POST /run` returns structural hash. New endpoints: `GET /programs`, `GET /programs/:ref`, `DELETE /programs/:ref`, `PUT /programs/:name/alias`. |

## What Doesn't Change

| Component | Reason |
|-----------|--------|
| constellation-lang syntax | Suspension is implicit from DAG dependencies; hashing is transparent |
| Parser grammar | No new AST nodes (normalization is a post-parse transform) |
| Module system / ModuleBuilder | Modules are unaware of suspension and content addressing |
| Existing `runDag` / `setDag` / `getDag` methods | Removed — replaced by `Constellation.run` and `ProgramStore`. No existing users to break. |

---

## Implementation Phases

### Phase 1: Program Image & Content-Addressed Store

- `ProgramImage` case class with `structuralHash`, `syntacticHash`, `dagSpec`, `moduleOptions`, provenance fields
- `LoadedProgram` case class wrapping `ProgramImage` + `syntheticModules`
- `CompilationOutput` case class wrapping `LoadedProgram` + `List[CompileWarning]`
- Structural hash computation: canonical DagSpec → SHA-256
- Syntactic hash computation: parse → normalize AST → SHA-256
- `ProgramStore` trait and `ProgramStoreImpl` (content-addressed images, mutable aliases, syntactic index)
- Refactor `LangCompiler` to return `CompilationOutput` (wraps `LoadedProgram` + `warnings`) instead of `CompileResult`
- Refactor `CachingLangCompiler` to use `ProgramStore.lookupSyntactic(syntacticHash, registryHash)` instead of standalone cache
- Extract `SyntheticModuleFactory` into `constellation-runtime` — single source of truth for creating synthetic modules from DagSpec metadata. Refactor `DagCompiler` to delegate to this factory instead of inline creation.
- Rehydration: `ProgramImage.rehydrate()` → delegates to `SyntheticModuleFactory.fromDagSpec`
- Replace `DagRegistry` with `ProgramStore` in `Constellation`. Remove `setDag`/`getDag`/`runDag` — replaced by `ProgramStore` and `Constellation.run`.
- Tests: structural hash determinism, syntactic normalization equivalence, store/alias/resolve round-trip, deduplication, rehydration correctness (rehydrated modules produce same results as compiler-created ones)

### Phase 2: Core Suspend/Resume

- `DataSignature` type with core fields
- `SuspendedExecution` IR case class (references `structuralHash`)
- `ExecutionStatus` enum
- `SuspensionCodec` trait with `CirceJsonSuspensionCodec` default implementation
- Convenience methods: `SuspendedExecution.serialize` / `deserialize` (delegate to JSON codec)
- `Constellation.run(loaded, inputs)` returns `DataSignature`
- `SuspendableExecution.resume` accepting `SuspendedExecution` + `additionalInputs` + `resolvedNodes` + consumer-provided modules
- Input validation on resume (type mismatch, duplicate with different value, unexpected key ignored)
- Node resolution validation (unknown node, type mismatch, already resolved)
- Failed executions preserve suspension state (`status = Failed`, `suspendedState = Some(...)`)
- `DataSignature.failedNodes` accessor
- Program change detection on resume (structural hash comparison)
- Tests: round-trip suspend/resume, incremental multi-step, all inputs provided (no suspension), codec round-trip (JSON), custom codec pluggability, cross-JVM resume via rehydration, input validation edge cases, node healing (failed module bypass), error cases

### Phase 3: Metadata Flags

- `ExecutionOptions` with `includeTimings`, `includeProvenance`, `includeBlockedGraph`, `includeResolutionSources`
- `SignatureMetadata` optional fields
- Per-node timing instrumentation
- Dependency trace collection
- Blocked graph computation
- `ResolutionSource` tracking (`FromModuleExecution`, `FromInput`, `FromManualResolution`)
- Tests: each flag independently, all flags combined, resolution source tracking across healed nodes, performance comparison flags off vs on

### Phase 4: Persistence Middleware

- `SuspensionStore` trait (pure CRUD — save/load/delete/list of `SuspendedExecution`)
- `SuspensionHandle`, `SuspensionSummary`, `SuspensionFilter`
- `InMemorySuspensionStore` implementation (accepts `SuspensionCodec`)
- `Constellation.builder().withSuspensionStore(store)` integration
- `Constellation.resumeFromStore` convenience (load + rehydrate + merge modules + resume)
- Tests: store/load round-trip, list/filter, delete, resumeFromStore end-to-end, concurrent access

### Phase 5: HTTP API — Content-Addressed Endpoints

- Update `POST /compile` to return `structuralHash` and create alias
- Update `POST /execute` to accept name or structural hash as `ref`
- Update `POST /run` to store image and return `structuralHash`
- New `GET /programs` — list images with aliases
- New `GET /programs/:ref` — program metadata by name or hash
- New `DELETE /programs/:ref` — remove image
- New `PUT /programs/:name/alias` — repoint alias
- Tests: compile + execute by hash, compile + alias + execute by name, hot-load repoint, dedup via /run, rollback via alias update

### Phase 6: Documentation

- Embedder guide: "Suspendable Execution" — concepts, API walkthrough, examples
- Embedder guide: "Program Lifecycle" — ProgramImage, LoadedProgram, ProgramStore, hashing
- Embedder guide: "Healing Failed Executions" — resolvedNodes, failedNodes, audit trails
- API reference: all new types with Scaladoc
- Migration guide: `CompileResult` → `CompilationOutput`/`LoadedProgram`, `DagRegistry` → `ProgramStore`, `runDag` → `Constellation.run`
- Update `llm.md` and `CLAUDE.md` with new types and commands
- Update example-app to use new API

---

## Test Strategy

### Canonical Fixture Programs

Tests across all phases use a shared set of `.cst` fixture programs with known dependency structures:

**Fixture 1: `three-step-onboarding.cst`** — Three-tier dependency chain for incremental suspension.

```constellation
# Tier 1: requires name + email
in name: String
in email: String
validated_email = ValidateEmail(email)
greeting = FormatGreeting(name)

# Tier 2: requires address (depends on tier 1 outputs)
in address: String
full_profile = BuildProfile(greeting, validated_email, address)

# Tier 3: requires funding_source (depends on tier 2)
in funding_source: String
account = CreateAccount(full_profile, funding_source)

out account
out greeting
```

**Usage:** Provide `name` + `email` → tier 1 executes, suspends. Provide `address` → tier 2 executes, suspends. Provide `funding_source` → completes.

**Fixture 2: `parallel-branches.cst`** — Independent branches to test maximum progress.

```constellation
in x: String
in y: Int

# Branch A (depends only on x)
a1 = Uppercase(x)
a2 = Trim(a1)

# Branch B (depends only on y)
b1 = Double(y)

# Merge (depends on both branches)
in z: String
result = Merge(a2, b1, z)

out a2
out b1
out result
```

**Usage:** Provide `x` only → branch A completes, branch B blocked, merge blocked. Provide `y` → branch B completes, merge still blocked (missing `z`). Tests maximum progress with parallel independent branches.

**Fixture 3: `failable-pipeline.cst`** — For testing failure preservation and healing.

```constellation
in ssn: String
credit_score = CreditCheck(ssn)
risk_level = RiskAssess(credit_score)
decision = Approve(risk_level)

out decision
out credit_score
```

**Usage:** `CreditCheck` is configured to fail in tests. Consumer heals by providing `credit_score` via `resolvedNodes`. `RiskAssess` and `Approve` then execute normally.

**Fixture 4: `trivial-complete.cst`** — All inputs provided, no suspension.

```constellation
in text: String
result = Uppercase(text)
out result
```

**Usage:** Provide `text` → executes to completion immediately. `status = Completed`, `suspendedState = None`. Verifies that suspendable execution works transparently when no inputs are missing.

### Property-Based Tests (ScalaCheck)

| Property | Invariant |
|----------|-----------|
| **Syntactic normalization idempotency** | `normalize(normalize(ast)) == normalize(ast)` for any AST |
| **Syntactic hash determinism** | Same source string → same syntactic hash, always |
| **Structural hash determinism** | Same DagSpec → same structural hash, always |
| **Structural hash uniqueness** | Different DagSpecs → different structural hashes (probabilistic — no SHA-256 collisions) |
| **Rehydration equivalence** | For any `LoadedProgram`, `rehydrate(loaded.image)` produces synthetic modules that yield identical outputs for the same inputs |
| **Codec round-trip** | `decode(encode(state)) == Right(state)` for any `SuspendedExecution` |
| **Input accumulation** | After N resumptions, `suspendedState.providedInputs == union of all inputs across all steps` |
| **Additive-only inputs** | `resume(state, Map("x" -> v1))` followed by `resume(_, Map("x" -> v2))` where `v1 != v2` always fails with `InputAlreadyProvidedError` |
| **Node resolution finality** | `resume(state, resolvedNodes = Map("n" -> v))` followed by `resume(_, resolvedNodes = Map("n" -> v2))` always fails with `NodeAlreadyResolvedError` |
| **Alias repoint** | `alias("name", h1)` then `alias("name", h2)` → `resolve("name") == Some(h2)` and `get(h1)` still succeeds |

### Integration Test: Full Lifecycle

A single end-to-end test exercises the complete lifecycle using `three-step-onboarding.cst`:

```
1. Compile source → CompilationOutput (LoadedProgram + warnings)
2. Store image → ProgramStore.store(image) → structuralHash
3. Create alias → ProgramStore.alias("onboarding", hash)
4. Run by ref → constellation.run("onboarding", tier1Inputs) → DataSignature (Suspended)
5. Verify sig1: status=Suspended, computedNodes has tier 1 values, missingInputs has tier 2+3
6. Serialize → SuspendedExecution.serialize(sig1.suspendedState.get) → bytes
7. Deserialize → SuspendedExecution.deserialize(bytes) → SuspendedExecution
8. Rehydrate → ProgramImage.rehydrate(image) → LoadedProgram (simulates different JVM)
9. Resume → SuspendableExecution.resume(suspended, tier2Inputs, loaded.syntheticModules) → sig2 (Suspended)
10. Verify sig2: resumptionCount=1, computedNodes has tier 1+2, missingInputs has tier 3 only
11. Resume → resume(sig2.suspendedState.get, tier3Inputs, ...) → sig3 (Completed)
12. Verify sig3: status=Completed, all outputs resolved, suspendedState=None
13. Verify dedup: compile same source again → same structuralHash, store is no-op
```

### Integration Test: Healing

Using `failable-pipeline.cst`:

```
1. Compile and run with ssn → CreditCheck fails → DataSignature (Failed, suspendedState=Some)
2. Verify failedNodes contains "credit_score"
3. Resume with resolvedNodes = Map("credit_score" -> CInt(750)) → RiskAssess + Approve execute
4. Verify status=Completed, resolution sources show credit_score=FromManualResolution
```

### Performance Benchmarks

| Operation | Target | Rationale |
|-----------|--------|-----------|
| Syntactic hash computation | <2ms (small), <10ms (large) | Must be faster than compilation to justify cache |
| Structural hash computation | <5ms | Computed once post-compile, amortized over all executions |
| Rehydration | <5ms | Must be near-instant — called on every run-by-reference |
| Codec round-trip (JSON) | <10ms (small state), <50ms (large state) | Serialization on suspend, deserialization on resume |
| ProgramStore lookup (by hash) | <1ms | Hot path for run-by-reference |
| ProgramStore lookup (by name) | <1ms | Alias resolve + hash lookup |

---

## Migration Guide

Although there are no external users, the internal codebase (example-app, HTTP API, tests, benchmarks) uses the current API. This section maps old patterns to new ones for implementers.

### Compilation

```scala
// BEFORE
val result: CompileResult = compiler.compile(source, dagName).toOption.get
val dagSpec = result.dagSpec
val modules = result.syntheticModules

// AFTER
val output: CompilationOutput = compiler.compile(source, dagName).toOption.get
output.warnings.foreach(w => logger.warn(w.message))
val loaded: LoadedProgram = output.program
val image: ProgramImage = loaded.image       // Serializable artifact
val modules = loaded.syntheticModules         // Runtime-only
```

### Storing Programs

```scala
// BEFORE
constellation.setDag("myprogram", compileResult.dagSpec)

// AFTER
programStore.store(loaded.image).unsafeRunSync()               // Content-addressed
programStore.alias("myprogram", loaded.structuralHash).unsafeRunSync()  // Named reference
```

### Executing Programs

```scala
// BEFORE — by name (returns Runtime.State)
val state = constellation.runDag("myprogram", inputs).unsafeRunSync()
val outputs = state.data.map { case (k, v) => k -> v.value }

// BEFORE — with modules (returns Runtime.State)
val state = constellation.runDagWithModules(dagSpec, inputs, syntheticModules).unsafeRunSync()

// AFTER — by LoadedProgram (returns DataSignature)
val sig = constellation.run(loaded, inputs).unsafeRunSync()
sig.outputs          // Already resolved by variable name
sig.computedNodes    // All intermediate values

// AFTER — by reference (returns DataSignature)
val sig = constellation.run("myprogram", inputs).unsafeRunSync()
val sig = constellation.run("sha256:abc123...", inputs).unsafeRunSync()
```

### Retrieving Programs

```scala
// BEFORE
val dagSpec: Option[DagSpec] = constellation.getDag("myprogram").unsafeRunSync()

// AFTER
val image: Option[ProgramImage] = programStore.getByName("myprogram").unsafeRunSync()
val image: Option[ProgramImage] = programStore.get("sha256:abc123...").unsafeRunSync()
val loaded: LoadedProgram = ProgramImage.rehydrate(image.get)   // When you need to execute
```

### Internal Files Requiring Migration

| File | Changes |
|------|---------|
| `modules/runtime/.../Constellation.scala` | Remove `setDag`/`getDag`/`runDag`/`runDagWithModules`. Add `run(loaded, ...)` and `run(ref, ...)`. Replace `DagRegistry` with `ProgramStore`. |
| `modules/runtime/.../DagRegistry.scala` | Delete — replaced by `ProgramStore`. |
| `modules/runtime/.../impl/DagRegistryImpl.scala` | Delete — replaced by `ProgramStoreImpl`. |
| `modules/runtime/.../impl/ConstellationImpl.scala` | Rewrite to use `ProgramStore`. Module merging logic moves here (registered + synthetic). |
| `modules/lang-compiler/.../LangCompiler.scala` | Return `CompilationOutput` instead of `CompileResult`. |
| `modules/lang-compiler/.../compiler/DagCompiler.scala` | Delegate synthetic module creation to `SyntheticModuleFactory`. Compute structural hash. |
| `modules/lang-compiler/.../CachingLangCompiler.scala` | Replace internal cache with `ProgramStore.lookupSyntactic`. |
| `modules/lang-compiler/.../CompilationCache.scala` | Delete — absorbed into `ProgramStore`. |
| `modules/http-api/.../ConstellationRoutes.scala` | Update all route handlers for new API. Add `/programs` endpoints. |
| `modules/http-api/.../ApiModels.scala` | Update request/response models (`ref` instead of `dagName`, add `structuralHash` to responses). |
| `modules/example-app/.../ExampleApp.scala` | Update to use `CompilationOutput`, `ProgramStore`, `constellation.run`. |
| All existing tests using `CompileResult` | Update to unwrap `CompilationOutput.program`. |

---

## Future Work (Out of Scope for This RFC)

| Feature | Description |
|---------|-------------|
| **Node invalidation** | Allow consumers to invalidate specific completed nodes on resume, causing them to re-execute. Enables "go back and change an earlier answer" workflows. |
| **Per-node checkpointing** | Persist state after each node execution for crash recovery. Requires storage in the execution hot path. |
| **Step validation middleware** | Optional middleware that enforces application-defined rules about which inputs can be provided at which resumption step. |
| **Suspension-aware HTTP endpoints** | REST API for suspend/resume/list operations on the optional HTTP server module. |
| **Distributed suspension** | Coordination for suspended executions across multiple server instances. |
| **TTL and expiry** | Automatic cleanup of suspended executions that haven't been resumed within a time limit (consumer concern for now). |
| **Persistent ProgramStore** | In-memory `ProgramStoreImpl` is sufficient for v1. A persistent implementation (backed by database, filesystem, or object storage) would survive JVM restarts. Follows the same SPI pattern as `SuspensionStore`. |
| **Image garbage collection** | When aliases are repointed, orphaned images accumulate. A GC strategy (reference counting, mark-and-sweep over aliases + active suspensions) would reclaim storage. |
| **ProgramImage codec** | `SuspensionCodec` handles `SuspendedExecution`. A parallel `ProgramImageCodec` (or a generalized codec) would enable persistent ProgramStore implementations with pluggable serialization. For v1, Circe JSON is sufficient. |

---

## Design Decisions

Decisions resolved during RFC design, documented here for context.

### D1: Serialization Format

**Decision:** Circe JSON as the default codec, behind an abstract `SuspensionCodec` trait.

**Rationale:** The project already depends on Circe, and JSON is human-readable — making stored states inspectable in databases during early adoption. The `SuspensionCodec` trait provides a clean seam for consumers to introduce high-performance binary codecs (e.g. Scala Fury, protobuf) or encrypted codecs without any changes to the runtime. Convenience methods (`SuspendedExecution.serialize`/`deserialize`) delegate to `CirceJsonSuspensionCodec` by default.

### D2: State Size

**Decision:** No `estimateSize` utility. State size is the consumer's concern.

**Rationale:** The `SuspendedExecution` IR is a plain case class — consumers can serialize it and check byte length, or use standard JVM object-size tooling. Adding an estimate utility to the runtime would be imprecise (JVM object layout varies) and add API surface for a niche concern. If a consumer needs to bound state size, they can check `computedValues.size` or post-serialization byte length.

### D3: Concurrent Resumptions

**Decision:** Consumer responsibility. The runtime does not detect or prevent concurrent resumptions.

**Rationale:** Since `SuspendedExecution` is an immutable case class, two concurrent `resume` calls with the same state would each produce valid but divergent results. Detecting this in the runtime would require shared mutable state (execution ID registry), adding complexity and limiting deployment flexibility (e.g. multi-instance services). Consumers who need single-writer semantics can enforce it at their persistence layer (optimistic locking, distributed locks, etc.). This will be documented as a consumer responsibility with guidance on common patterns.

### D4: Module Storage in IR

**Decision:** Store module references (UUIDs) in the IR, not module instances. Consumer provides modules on resume.

**Rationale:** Synthetic modules are Scala functions generated by the compiler — they cannot be meaningfully serialized. Storing them in the IR would make it non-serializable and couple it to the JVM instance. Instead, `SuspendedExecution` contains only the `DagSpec` (which references modules by UUID), and the consumer provides the `Map[UUID, Module.Uninitialized]` from their retained `LoadedProgram` when calling `resume()`. This keeps the IR purely data, avoids unnecessary recompilation, and aligns with the existing `runDagWithModules` pattern.

### D5: Two-Level Hashing

**Decision:** Two complementary hashes — syntactic (cheap, post-parse) and structural (true identity, post-compile) — rather than a single hash.

**Rationale:** True semantic hashing without compilation is impossible — the DAG structure depends on type checking, module resolution, and optimization. A syntactic hash after parsing (~1ms) catches the common equivalences (variable renaming, whitespace, reordering) and serves as a fast cache key to skip compilation. The structural hash is computed as a free byproduct of compilation and provides true semantic identity. Attempting a single hash that handles both use cases would either be too expensive (full compilation) or too imprecise (missing semantic equivalences). The two-level approach gives fast cache performance AND correct identity.

### D6: Domain Naming — ProgramImage / LoadedProgram

**Decision:** Rename `CompileResult` to `LoadedProgram` and extract its serializable fields into `ProgramImage`. Names are mutable aliases pointing to structural hashes.

**Rationale:** The previous `CompileResult` mixed serializable data (`DagSpec`, `moduleOptions`) with non-serializable Scala functions (`syntheticModules`) in one type. This made it impossible to persist or transfer compiled programs without losing executability. The new naming makes the serialization boundary a type-level guarantee: `ProgramImage` is always serializable (store it, transfer it, content-address it), `LoadedProgram` is always executable (run it, resume from it). `ProgramImage.rehydrate()` bridges the gap deterministically without recompilation. The alias model (names as mutable pointers to structural hashes) follows the git branch metaphor — consumers can version, rollback, and hot-swap programs by repointing aliases.
