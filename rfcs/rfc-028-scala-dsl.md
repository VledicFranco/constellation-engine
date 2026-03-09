# RFC-028: Scala DSL for Pipeline Composition

**Status:** Accepted
**Priority:** P1 (Developer Experience / Ecosystem)
**Author:** Human + Claude
**Created:** 2026-03-09

---

## Summary

Introduce a **typed Scala DSL** for composing Constellation pipelines entirely in Scala 3, without writing `.cst` files. The DSL maps 1:1 to the `.cst` language surface (`in`, module calls, `out`) while exploiting Scala's type system to catch wiring errors at compile time.

The DSL lives in the `runtime` module, produces `DagSpec` directly (bypassing the parser, AST, type checker, and IR compiler), and integrates with the existing `Constellation.run` execution path via `LoadedPipeline`.

**Key design bet:** `ModuleBuilder[I, O]` already carries full type information at the point of definition. The DSL uses those types — without any new wrapper types — to validate that pipeline connections are correct before the program compiles.

---

## Motivation

### The Gap

Scala users embedding Constellation in their applications currently must either:

1. Write `.cst` source strings inline (stringly-typed, no IDE support, no compile-time errors)
2. Construct `DagSpec` manually (low-level, verbose, UUID bookkeeping by hand)

Neither option integrates naturally with Scala projects. Teams that don't need hot-reloadable text pipelines — the common case for library embedders — gain nothing from the `.cst` text representation and pay its costs: string-based field bindings, runtime-only type errors, and a separate compilation step.

### What This Enables

```scala
// Before: .cst text embedded in Scala source
val source = """
  in text: String
  cleaned = Trim(text)
  result  = Uppercase(cleaned)
  out result
"""
constellation.compile(source).flatMap(constellation.run(_, inputs))

// After: typed DSL, wiring errors caught at compile time
val pipeline = Pipeline.define("myPipeline") { p =>
  val text    = p.input[TrimInput]("text")
  val cleaned = p.step(text)(trim)       // TypedRef[TrimOutput]
  val result  = p.step(cleaned)(upper)   // compile error if TrimOutput ≠ UpperInput
  p.output("result", result)
}
for {
  constellation <- ConstellationImpl.init
  _             <- pipeline.registerModules(constellation)
  result        <- constellation.run(pipeline.load, inputs)
} yield result
```

### Why Not `.cst`?

The `.cst` language is the right tool when:
- Pipelines are loaded from external files or user-provided strings
- Hot-reloading at runtime is required
- Non-Scala authors need to write pipelines

The Scala DSL is the right tool when:
- Pipelines are defined in Scala source alongside their modules
- The full Scala type system is available
- Runtime errors from field name mismatches are unacceptable

Both are first-class. The DSL does not replace `.cst`.

---

## Design Principles

| Principle | Rationale |
|-----------|-----------|
| **No new module types** | The DSL accepts `ModuleBuilder[I, O]` — the value users already write before calling `.build()`. Zero new wrapper types. |
| **Scala's type system is the type checker** | The DSL replaces `DagCompiler`'s type checker with Scala's own. Wiring errors are compile-time errors. |
| **Direct `DagSpec` construction** | The DSL bypasses the parser, AST, and IR. It constructs `DagSpec` directly — the same structure the compiler produces. The runtime executes it identically. |
| **`runtime` module only** | No new dependency layers. `DagSpec`, `ModuleBuilder[I, O]`, `CTypeTag`, and `PipelineImage` are all already in `runtime` or `core`. |
| **`.cst` parity on constructs, not syntax** | Every `.cst` construct has a DSL equivalent. Conversely, the DSL adds nothing `.cst` cannot express. |
| **Execution-agnostic `TypedPipeline`** | `TypedPipeline` is a pure, immutable value. It does not hold a reference to a `Constellation` instance. Registration and execution are separate, explicit steps. |

---

## API Design

### Core Types

```scala
// A compile-time handle to a DAG node that produces values of type A.
// Carries the UUID of the underlying DataNodeSpec.
// Call options accumulate via method chaining.
sealed trait TypedRef[A] {
  def retry(n: Int): TypedRef[A]
  def timeout(d: FiniteDuration): TypedRef[A]
  def delay(d: FiniteDuration): TypedRef[A]
  def cache(d: FiniteDuration): TypedRef[A]
  def cacheBackend(name: String): TypedRef[A]
  def throttle(count: Int, per: FiniteDuration): TypedRef[A]
  def concurrency(n: Int): TypedRef[A]
  def onError(strategy: ErrorStrategy): TypedRef[A]
  def lazyEval: TypedRef[A]
  def priority(level: PriorityLevel): TypedRef[A]
  def withFallback[B >: A](fallback: TypedRef[B]): TypedRef[B]
}

// The builder scope passed to Pipeline.define's block.
// All DSL operations go through this.
trait PipelineBuilder {
  def input[I: CTypeTag](name: String): TypedRef[I]
  def step[I <: Product, O <: Product](ref: TypedRef[I])(module: ModuleBuilder[I, O]): TypedRef[O]
  def assemble[I <: Product, O <: Product](module: ModuleBuilder[I, O])(f: AssemblyCtx => I): TypedRef[O]
  def adapt[A, B: CTypeTag](ref: TypedRef[A])(f: A => B): TypedRef[B]
  def output(name: String, ref: TypedRef[?]): Unit
}

// A fully described, immutable pipeline — not yet bound to a Constellation instance.
// load  — produces a LoadedPipeline for immediate execution
// image — exposes the underlying PipelineImage for persistence / PipelineStore
// registerModules — convenience: registers all referenced ModuleBuilders with a Constellation instance
final class TypedPipeline private[constellation] (
    private val spec: DagSpec,
    private val callOptions: Map[UUID, ModuleCallOptions],
    private val syntheticModules: Map[UUID, Module.Uninitialized],
    private val moduleBuilders: List[Module.Uninitialized]
) {
  val image: PipelineImage = PipelineImage(
    structuralHash = PipelineImage.computeStructuralHash(spec),
    syntacticHash  = "",
    dagSpec        = spec,
    moduleOptions  = callOptions,
    compiledAt     = java.time.Instant.now()
  )

  def load: LoadedPipeline =
    LoadedPipeline(image, syntheticModules)

  def registerModules(constellation: Constellation): IO[Unit] =
    moduleBuilders.traverse_(constellation.setModule)
}

// Entry point
object Pipeline {
  def define(name: String)(f: PipelineBuilder => Unit): TypedPipeline
}
```

**`moduleBuilders`** is populated by `Pipeline.define` — every `ModuleBuilder[I, O]` passed to `p.step` or `p.assemble` is collected and turned into `Module.Uninitialized` (via `.build()`) internally. `registerModules` simply registers them all. Users do not manage this list.

---

### Operation Reference

#### `p.input[I](name)` — Declare a pipeline input

Maps to `.cst`: `in text: String`

```scala
val text: TypedRef[TrimInput] = p.input[TrimInput]("text")
```

- Registers a `DataNodeSpec` with `cType = CTypeTag[I].cType` and `name = name`
- `CTypeTag[I]` is derived automatically for primitives and case classes
- Returns a `TypedRef[I]` wrapping the new node's UUID

---

#### `p.step(ref)(module)` — Single-input module call

Maps to `.cst`: `result = Uppercase(cleaned)`

```scala
val result: TypedRef[UpperOutput] = p.step(cleaned)(upper)
// Requires: cleaned: TypedRef[UpperInput]
// module: ModuleBuilder[UpperInput, UpperOutput]
```

- `ref: TypedRef[I]` must match the module's input type `I` exactly — enforced at compile time
- Registers a `ModuleNodeSpec` from `module._metadata`, with `consumes`/`produces` derived via `CTypeTag`
- Registers an output `DataNodeSpec` named after the module
- Adds `inEdge(dataNode → moduleNode)` and `outEdge(moduleNode → outputDataNode)`
- Populates `DataNodeSpec.nicknames` with `moduleUUID → fieldName` for the single input field
- Collects `module.build` into `TypedPipeline.moduleBuilders` for `registerModules`

**Note on single-field inputs:** `step` is the ergonomic path for the common pattern where a module takes exactly one upstream node. If `I` has multiple fields sourced from different upstream nodes, use `assemble`.

---

#### `p.assemble(module)(ctx => I(...))` — Multi-input (fan-in) module call

Maps to `.cst` multi-argument calls: `result = Merge(left = nodeA, right = nodeB)`

```scala
val result: TypedRef[MergeOutput] =
  p.assemble(merge) { ctx =>
    MergeInput(
      left  = ctx.field(nodeA, _.text),   // TypedRef[TrimOutput] → String field
      right = ctx.field(nodeB, _.value)   // TypedRef[OtherOutput] → String field
    )
  }
```

- `ctx.field(ref, selector)` is an `inline def` backed by a Scala 3 macro:
  - Extracts the field name from the selector at compile time (e.g. `_.text` → `"text"`)
  - Records the dependency: `ref`'s UUID feeds the named field of `I`
  - Returns a placeholder value of the field's type so the outer lambda `ctx => I(...)` type-checks normally
  - Emits `@scala.annotation.unused` on the placeholder to suppress IDE warnings
- The macro constructs the `nicknames` map and `inEdges` from the captured dependencies
- The outer lambda is called once during DSL construction solely to capture dependencies; its return value is discarded
- Return type of the lambda must be exactly `I` — enforced by the type parameter
- Collects `module.build` into `TypedPipeline.moduleBuilders` for `registerModules`

**Limitation:** `ctx.field` only works with direct field selectors (`_.fieldName`). Arbitrary expressions inside the selector are not supported and produce a compile error.

---

#### `p.adapt(ref)(f: A => B)` — Inline type adaptation

No direct `.cst` equivalent. Bridges type mismatches between upstream output and downstream input — typically a field rename or structural reshape — without defining a named module.

```scala
// TrimOutput(result: String) → UpperInput(text: String): adapt field name
val adapted: TypedRef[UpperInput] =
  p.adapt(trimmed)(out => UpperInput(text = out.result))
```

- Creates a DSL-generated anonymous `Module.Uninitialized` wrapping `f`
- Stored in `TypedPipeline.syntheticModules` (keyed by the output node's UUID), surfaced in `LoadedPipeline.syntheticModules`
- Never appears in the module registry — does not require `registerModules`
- `CTypeTag[B]` required (given implicitly) to register the output `DataNodeSpec.cType`
- Follows the same pattern as `SyntheticModuleFactory` for branch and HOF nodes

**Use `p.adapt` when:** upstream and downstream types differ in field names but are structurally compatible. Use a named `ModuleBuilder` when the transformation has domain meaning worth naming and testing.

---

#### `p.output(name, ref)` — Declare a pipeline output

Maps to `.cst`: `out result`

```scala
p.output("result", result)
```

- Adds `name` to `DagSpec.declaredOutputs`
- Adds `name → ref.uuid` to `DagSpec.outputBindings`

---

#### Call options — Chained on `TypedRef`

Maps to `.cst` `with` clauses: `result = Uppercase(cleaned) with retry(3) timeout(500ms)`

```scala
val result: TypedRef[UpperOutput] =
  p.step(cleaned)(upper)
    .retry(3)
    .timeout(500.millis)
    .cache(30.seconds)
```

- Options accumulate on the `TypedRef` and are packaged into `PipelineImage.moduleOptions` at `TypedPipeline` construction time
- Keyed by the output `DataNodeSpec` UUID (matching the key used by `Runtime` when resolving options)
- `withFallback(other)` requires `other: TypedRef[B]` where `B >: A` — type-checked at compile time; the fallback ref's UUID is recorded as `IRModuleCallOptions.fallback`

---

### Complete Example

```scala
// ── Module definitions ────────────────────────────────────────────────────
// Keep ModuleBuilder[I, O] — do NOT call .build() here.
// Pipeline.define collects and builds them internally.
case class RawInput(text: String)
case class TrimInput(text: String)
case class TrimOutput(result: String)
case class UpperInput(text: String)
case class UpperOutput(result: String)
case class MergeInput(original: String, processed: String)
case class MergeOutput(summary: String)

val trim: ModuleBuilder[TrimInput, TrimOutput] =
  ModuleBuilder.metadata("Trim", "Trim whitespace", 1, 0)
    .implementationPure(i => TrimOutput(i.text.trim))

val upper: ModuleBuilder[UpperInput, UpperOutput] =
  ModuleBuilder.metadata("Uppercase", "Uppercase text", 1, 0)
    .implementationPure(i => UpperOutput(i.text.toUpperCase))

val merge: ModuleBuilder[MergeInput, MergeOutput] =
  ModuleBuilder.metadata("Merge", "Merge two strings", 1, 0)
    .implementationPure(i => MergeOutput(s"${i.original} → ${i.processed}"))

// ── Pipeline DSL ──────────────────────────────────────────────────────────
val pipeline: TypedPipeline = Pipeline.define("textPipeline") { p =>

  // Declare pipeline input
  val raw = p.input[RawInput]("input")

  // Adapt RawInput → TrimInput (field rename, no named module needed)
  val adapted = p.adapt(raw)(r => TrimInput(text = r.text))

  // Single-input chain with call options
  val trimmed = p.step(adapted)(trim).retry(2).timeout(1.second)

  // Adapt TrimOutput → UpperInput before passing to upper
  val upper1 = p.step(
    p.adapt(trimmed)(t => UpperInput(text = t.result))
  )(upper)

  // Fan-in: assemble MergeInput from two upstream nodes
  val summary = p.assemble(merge) { ctx =>
    MergeInput(
      original  = ctx.field(adapted, _.text),
      processed = ctx.field(upper1, _.result)
    )
  }

  p.output("summary", summary)
}

// ── Registration and execution ────────────────────────────────────────────
for {
  constellation <- ConstellationImpl.init
  _             <- pipeline.registerModules(constellation)   // registers trim, upper, merge
  result        <- constellation.run(pipeline.load, Map("input" -> CValue.CRecord(...)))
} yield result.output("summary")

// ── Persistence (optional) ────────────────────────────────────────────────
// pipeline.image is a PipelineImage — serializable, storable in PipelineStore
constellation.PipelineStore.store(pipeline.image)
```

---

## Implementation Approach

### Module Placement

All DSL types (`TypedRef`, `PipelineBuilder`, `AssemblyCtx`, `TypedPipeline`, `Pipeline`) live in the `runtime` module under `io.constellation.dsl`. No new sbt module is needed.

Dependencies already available in `runtime` / `core`:

| Type | Module |
|------|--------|
| `DagSpec`, `DataNodeSpec`, `ModuleNodeSpec` | `core` |
| `ModuleBuilder[I, O]`, `CTypeTag` | `runtime` |
| `PipelineImage`, `LoadedPipeline`, `Module.Uninitialized` | `runtime` |
| `ModuleCallOptions`, `ErrorStrategy`, `PriorityLevel` | `core` / `runtime` |
| `SyntheticModuleFactory` | `runtime` |

### `DagSpec` Construction

`PipelineBuilder` is a mutable accumulator (internal, not exposed). It tracks:

```
modules:        Map[UUID, ModuleNodeSpec]        ← one per p.step / p.assemble call
data:           Map[UUID, DataNodeSpec]           ← one per p.input + one per module output
inEdges:        Set[(UUID, UUID)]                 ← data → module for each input binding
outEdges:       Set[(UUID, UUID)]                 ← module → data for each output
declaredOutputs: List[String]                     ← from p.output calls
outputBindings: Map[String, UUID]                 ← from p.output calls
callOptions:    Map[UUID, ModuleCallOptions]       ← accumulated from TypedRef chains
syntheticMods:  Map[UUID, Module.Uninitialized]   ← from p.adapt calls
moduleBuilders: List[Module.Uninitialized]         ← from p.step / p.assemble calls
```

`Pipeline.define(name)(f)` runs `f` against a fresh `PipelineBuilder`, then seals the accumulated state into a `TypedPipeline`. The `TypedPipeline` constructor computes the structural hash and builds the `PipelineImage` eagerly — it is immutable from that point.

### `CTypeTag` for `CType` Derivation

`ModuleNodeSpec.consumes` and `.produces` require `Map[String, CType]`. The DSL derives these from `ModuleBuilder[I, O]`'s type parameters at compile time using `CTypeTag` and `Mirror.ProductOf`:

```scala
// Produces field name → CType for each field of a case class
inline def consumesOf[I <: Product](using Mirror.ProductOf[I], CTypeTag[I]): Map[String, CType]
inline def producesOf[O <: Product](using Mirror.ProductOf[O], CTypeTag[O]): Map[String, CType]
```

This is the same mechanism `ModuleBuilder.build` uses today internally. The DSL calls it explicitly to populate `ModuleNodeSpec` without going through `.build()`.

### `assemble` and the `ctx.field` Macro

`AssemblyCtx` is a stateful collector. `ctx.field[A, B](ref: TypedRef[A], selector: A => B)` is an `inline def` using `scala.quoted.Quotes`:

1. Reifies `selector` as a `quotes.reflect.Term`
2. Asserts it is a simple `Select` node; emits a compile error otherwise
3. Extracts the field name string from the `Select`
4. Records `(ref.uuid → fieldName)` in the `AssemblyCtx`'s mutable dependency buffer
5. Returns a zero placeholder of type `B` (never used at runtime — the DAG wires by UUID)
6. Annotates the placeholder with `@scala.annotation.unused` to suppress IDE warnings

The outer lambda `ctx => I(...)` is invoked exactly once during `Pipeline.define` to drive the macro and populate the dependency buffer. Its `I` return value is discarded. Only the buffer contents are used to construct `inEdges` and `DataNodeSpec.nicknames`.

### `p.adapt` and Synthetic Modules

`p.adapt(ref)(f: A => B)` does not use `InlineTransform` (which is reserved for IR-compiled transforms generated by `DagCompiler`). Instead:

1. Wraps `f` in an anonymous `Module.Uninitialized` via `ModuleBuilder` with a generated name (e.g. `__adapt_<uuid>`)
2. Assigns it a fresh UUID
3. Adds it to `syntheticMods` — surfaced as `LoadedPipeline.syntheticModules` at `.load` time
4. Wires input/output edges identically to a regular `p.step` call
5. Does **not** add it to `moduleBuilders` — synthetic adapters are never registered in the `Constellation` module registry

This follows the same pattern as `SyntheticModuleFactory` for branch and HOF pipeline nodes.

### `TypedPipeline.load` and Execution Integration

```
Pipeline.define(name)(f)
  → PipelineBuilder accumulates state
  → TypedPipeline(spec, callOptions, syntheticMods, moduleBuilders)
      image = PipelineImage(dagSpec, moduleOptions, structuralHash, ...)  ← computed eagerly

TypedPipeline.registerModules(constellation)   ← IO[Unit], registers named modules
TypedPipeline.load                             ← pure, LoadedPipeline(image, syntheticMods)

constellation.run(pipeline.load, inputs)       ← identical to .cst execution path
```

No changes to `Runtime`, `DagSpec` execution, or any existing code paths. The `DagSpec` produced by the DSL is structurally identical to one produced by `DagCompiler` for an equivalent `.cst` program.

---

## `.cst` Parity Table

| `.cst` construct | DSL equivalent |
|---|---|
| `in x: T` | `p.input[I]("x")` where `CTypeTag[I]` maps to `T` |
| `result = Mod(x)` | `p.step(xRef)(mod)` |
| `result = Mod(a, b)` | `p.assemble(mod) { ctx => I(f1 = ctx.field(a, _.f1), f2 = ctx.field(b, _.f2)) }` |
| `out result` | `p.output("result", result)` |
| Field-name / structural adaptation | `p.adapt(ref)(out => NewType(field = out.otherField))` |
| `with retry(3)` | `.retry(3)` on `TypedRef` |
| `with timeout(500ms)` | `.timeout(500.millis)` on `TypedRef` |
| `with cache(30s)` | `.cache(30.seconds)` on `TypedRef` |
| `with cache_backend("redis")` | `.cacheBackend("redis")` on `TypedRef` |
| `with fallback(x)` | `.withFallback(xRef)` on `TypedRef` |
| `with lazy` | `.lazyEval` on `TypedRef` |
| `with priority(high)` | `.priority(PriorityLevel.High)` on `TypedRef` |
| `with on_error(skip)` | `.onError(ErrorStrategy.Skip)` on `TypedRef` |
| `with throttle(10/1s)` | `.throttle(10, 1.second)` on `TypedRef` |
| `with concurrency(4)` | `.concurrency(4)` on `TypedRef` |
| `with delay(200ms)` | `.delay(200.millis)` on `TypedRef` |

**Not in scope for v1:** conditionals (`if/else`), guards (`when`), branch clauses, list HOF operations (`filter`, `map`, `all`, `any`), string interpolation, match expressions, record merge (`&`), record projection. These correspond to `IRNode` variants generated by `DagCompiler` that have no direct equivalent as module calls. V2 follow-up.

---

## Out of Scope

- **Conversion between DSL and `.cst` text** — the DSL does not emit `.cst` source; `.cst` files are not parsed into DSL objects
- **`Module.Uninitialized` at DSL call sites** — if you only have `Module.Uninitialized` (e.g. loaded from a remote registry), use `.cst` or construct `DagSpec` manually; the DSL requires `ModuleBuilder[I, O]`
- **Streaming pipelines** — streaming execution (RFC-025) is orthogonal; `TypedPipeline.streamLoad` is a future extension point
- **LSP / IDE tooling** — the DSL is plain Scala; Metals provides all navigation, autocomplete, and diagnostics natively
- **Runtime hot-reload** — `TypedPipeline.image` can be stored in `PipelineStore` and re-executed, but the DSL value is a compile-time artifact; dynamic reloading requires `.cst`

---

## Resolved Design Decisions

| # | Question | Decision |
|---|----------|----------|
| 1 | Name for `p.adapt` | `p.adapt` — clearer than `p.map` (avoids functor connotation) or `p.transform` (too generic) |
| 4 | Module registration | `TypedPipeline.registerModules(c: Constellation): IO[Unit]` — explicit convenience method. `load` stays pure. Synthetic adapter modules are never registered. |

---

## Open Questions

2. **`step` strictness on multi-field inputs** — should `p.step(ref)(module)` compile when `I` has multiple fields (requiring the caller to have assembled them into a single `TypedRef[I]` beforehand), or should it emit a compile-time hint directing the user to `assemble`? Current design: compiles as long as `ref: TypedRef[I]` — the user is responsible for constructing the `I`-typed ref upstream.

3. **`assemble` placeholder for non-nullable types** — `ctx.field` returns a zero placeholder of type `B`. For sealed traits and abstract types with no sensible zero value, the zero will be `null.asInstanceOf[B]`, which is safe (never used at runtime) but produces a runtime `NullPointerException` if a bug causes it to escape the macro. A compile-time warning for such types may be warranted.

5. **`TypedPipeline` and the `PipelineStore`** — `pipeline.image` is exposed for users who want to persist the compiled pipeline. Should `TypedPipeline` also expose a `storeAs(alias: String)(constellation: Constellation): IO[Unit]` convenience that calls `PipelineStore.store` + `PipelineStore.alias` in one step?

---

## Acceptance Criteria

- [ ] `p.input`, `p.step`, `p.assemble`, `p.adapt`, `p.output` all compile and produce a correct `DagSpec`
- [ ] Type mismatch on `p.step` (wrong `TypedRef[I]`) is a compile error with a readable message
- [ ] All 11 call option methods on `TypedRef` produce correct `ModuleCallOptions` in `PipelineImage.moduleOptions`
- [ ] `ctx.field` macro extracts field names correctly for all direct case class field selectors
- [ ] `ctx.field` with a non-selector expression (e.g. `_.field.nested`) emits a compile error
- [ ] `pipeline.load` produces a `LoadedPipeline` that `constellation.run` executes correctly end-to-end
- [ ] `pipeline.registerModules(constellation)` registers all named modules; synthetic adapt modules are not registered
- [ ] `p.adapt` nodes execute correctly as anonymous synthetic modules
- [ ] `pipeline.image` is a valid `PipelineImage` that can be round-tripped through `PipelineStore`
- [ ] Structural hash of a DSL-defined pipeline equals that of the equivalent `.cst`-compiled pipeline (identical topology, same module names, same field bindings)
- [ ] Full test suite passes (`make test`)
- [ ] Example added to `example-app` demonstrating a multi-step pipeline with `p.adapt`, `p.assemble`, and at least two call options
