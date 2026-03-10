<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/runtime/src/main/scala/io/constellation/dsl -->
<!-- Hash: rfc028dsl0001 -->
<!-- Generated: 2026-03-09T00:00:00.000000000Z -->

# io.constellation.dsl

Type-safe Scala DSL for composing pipelines without `.cst` text files.

Entry point: `Pipeline.define(name)(builder => ...)` — opens a `PipelineBuilder` scope,
runs the user block, and seals the result into a `TypedPipeline`.

## Traits

### trait TypedRef[A]

/** A handle to a typed data node in a pipeline under construction.
  *
  * Carries the UUID of the underlying [[io.constellation.DataNodeSpec]] and any accumulated
  * [[io.constellation.ModuleCallOptions]] for the module that produces it. All option methods
  * return a new `TypedRef[A]` — accumulation is immutable.
  *
  * Obtained from `p.input`, `p.step`, `p.assemble`, or `p.adapt`. Never instantiated directly.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `retry` | `(n: Int): TypedRef[A]` | /** Retry the producing module up to `n` times on failure. */ |
| `timeout` | `(d: FiniteDuration): TypedRef[A]` | /** Abort the producing module if it does not complete within `d`. */ |
| `delay` | `(d: FiniteDuration): TypedRef[A]` | /** Delay execution of the producing module by `d`. */ |
| `backoff` | `(strategy: String): TypedRef[A]` | /** Backoff strategy for retries: `"fixed"`, `"linear"`, or `"exponential"`. */ |
| `cache` | `(d: FiniteDuration): TypedRef[A]` | /** Cache the output of the producing module for `d`. */ |
| `cacheBackend` | `(name: String): TypedRef[A]` | /** Named cache backend to use (overrides the default). */ |
| `throttle` | `(count: Int, per: FiniteDuration): TypedRef[A]` | /** Throttle calls to the producing module: at most `count` per `per` window. */ |
| `concurrency` | `(n: Int): TypedRef[A]` | /** Maximum concurrent executions of the producing module. */ |
| `onError` | `(strategy: String): TypedRef[A]` | /** Error handling strategy: `"propagate"`, `"skip"`, `"log"`, or `"wrap"`. */ |
| `lazyEval` | `(): TypedRef[A]` | /** Evaluate the producing module lazily (only when its output is consumed). */ |
| `priority` | `(p: Int): TypedRef[A]` | /** Scheduling priority for the producing module (higher value = higher priority). */ |

### trait PipelineBuilder

/** User-facing DSL scope for building type-safe pipelines.
  *
  * Obtained exclusively via [[Pipeline.define]]; never instantiated directly by user code.
  *
  * ==Compile-time type safety==
  * `step` and `adapt` require both input and output types to be single-field case classes —
  * enforced at compile time via `requireSingleField`. Multi-field fan-in requires `p.assemble`.
  *
  * ==Module registration==
  * Named modules passed to `step` and `assemble` are collected and exposed via
  * [[TypedPipeline.moduleBuilders]], allowing [[TypedPipeline.registerModules]] to register
  * them with a [[io.constellation.Constellation]] instance. Synthetic modules created by
  * `adapt` are never registered — they execute via `LoadedPipeline.syntheticModules` directly.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `input` | `[I: CTypeTag](name: String): TypedRef[I]` | /** Declare a typed pipeline input and return a handle to it. For single-field Product types the data node's CType is the field's type, matching what `Constellation.run` expects when the user provides the input value. */ |
| `step` | `inline [I <: Product: CTypeTag, O <: Product: CTypeTag](ref: TypedRef[I])(module: ModuleBuilder[I, O])(using Mirror.ProductOf[I], Mirror.ProductOf[O]): TypedRef[O]` | /** Wire a module into the pipeline, consuming `ref` as its single input. Both `I` and `O` must each have exactly one field — enforced at compile time. Option methods on the returned TypedRef are flushed into moduleOptions when the ref is next consumed. The module is collected for `TypedPipeline.registerModules`. */ |
| `assemble` | `inline [I <: Product: CTypeTag, O <: Product: CTypeTag](module: ModuleBuilder[I, O])(f: AssemblyCtx => I)(using Mirror.ProductOf[I], Mirror.ProductOf[O]): TypedRef[O]` | /** Wire a multi-input module into the pipeline using an AssemblyCtx binding block. Allows fan-in from multiple upstream nodes into a module with a multi-field input type. `O` must have exactly one field. The lambda `f` is invoked once during pipeline construction; its return value is discarded. */ |
| `adapt` | `inline [A <: Product: CTypeTag, B <: Product: CTypeTag](ref: TypedRef[A])(f: A => B)(using Mirror.ProductOf[A], Mirror.ProductOf[B]): TypedRef[B]` | /** Insert an inline type-adaptation step between two type-incompatible nodes. Creates an anonymous synthetic module that wraps `f` and stores it in TypedPipeline.syntheticModules. Never registered via registerModules. Both A and B must be single-field Product types. */ |
| `output` | `(name: String, ref: TypedRef[?]): Unit` | /** Declare a named output for the pipeline, bound to the data node referenced by `ref`. Any accumulated options on `ref` are flushed into moduleOptions. */ |

## Classes

### final class AssemblyCtx

/** Mutable dependency collector used inside a [[PipelineBuilder.assemble]] block.
  *
  * Each [[field]] call records one input-field binding (upstream data node UUID → field name
  * of the assembled input type) and returns a placeholder value of the field's type.
  *
  * ==Usage==
  * {{{
  * p.assemble(mergeModule) { ctx =>
  *   MergeInput(
  *     left  = ctx.field(nodeA, _.left),   // records nodeA → "left"
  *     right = ctx.field(nodeB, _.right)   // records nodeB → "right"
  *   )
  * }
  * }}}
  *
  * `selector` must be a simple field selector of the form `_.fieldName`. Arbitrary
  * expressions produce a compile error.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `field` | `inline [A, B](ref: TypedRef[A], selector: A => B): B` | /** Extract the field name from `selector` at compile time, record the dependency at runtime, and return a placeholder value of type B. The selector must be `_.fieldName`; arbitrary expressions produce a compile error. The placeholder return value is always discarded. */ |

### final class TypedPipeline

/** An immutable, executable pipeline built from a [[Pipeline.define]] block.
  *
  * Holds the sealed [[io.constellation.DagSpec]] and any accumulated
  * [[io.constellation.ModuleCallOptions]] from [[TypedRef]] chains.
  * Call [[load]] to obtain a [[io.constellation.LoadedPipeline]] ready to pass to
  * [[io.constellation.Constellation.run]].
  */

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `spec` | `DagSpec` | The compiled DAG specification. |
| `callOptions` | `Map[UUID, ModuleCallOptions]` | Accumulated call options keyed by module node UUID. |
| `syntheticModules` | `Map[UUID, Module.Uninitialized]` | Anonymous adapt modules keyed by their DAG module UUID. |
| `moduleBuilders` | `List[Module.Uninitialized]` | Named modules (from `p.step` and `p.assemble`) in declaration order. |
| `image` | `PipelineImage` | Immutable snapshot: structural hash + DagSpec + moduleOptions. Computed once at construction. |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `load` | `(): LoadedPipeline` | /** Wrap the image into a LoadedPipeline ready for execution. Pure — no IO. Does NOT call PipelineImage.rehydrate. */ |
| `registerModules` | `(constellation: Constellation): IO[Unit]` | /** Register each module builder with `constellation` so the runtime can find it by name. Synthetic adapt modules are excluded — they run via syntheticModules without registration. Returns IO.unit when moduleBuilders is empty. */ |

## Objects

### Pipeline$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `define` | `(name: String)(f: PipelineBuilder => Unit): TypedPipeline` | /** Define a named pipeline. Opens a PipelineBuilder scope, runs the user-provided block, then seals the accumulated state into a TypedPipeline. @param name Pipeline name used as ComponentMetadata identifier. @param f Builder block — receives a PipelineBuilder and wires inputs, steps, and outputs. */ |

<!-- END GENERATED -->
