<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/core/src/main/scala/io/constellation -->
<!-- Hash: 509e1b331609 -->
<!-- Generated: 2026-03-10T05:05:52.055751900Z -->

# io.constellation.dsl

## Objects

### AssemblyCtx$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `fieldImpl` | `[A, B](ref: Expr[TypedRef[A]], selector: Expr[Function1[A, B]], ctx: Expr[AssemblyCtx], evidence$1: Type[A], evidence$2: Type[B], x$4: Quotes): Expr[Any]` | /** Scala 3 macro: extracts the field name from `selector` at compile time, generates code to |
| `apply` | `(): AssemblyCtx` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### Pipeline$

/** Entry point for the Scala pipeline DSL. */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `define` | `(name: String, f: Function1[PipelineBuilder, Unit]): TypedPipeline` | /** Define a named pipeline. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### PipelineBuilder$package$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `fieldMapOf` | `[T](tag: CTypeTag[T]): Map[String, CType]` | /** Extract the field name → [[io.constellation.CType]] map from a single-field or multi-field |
| `requireSingleField` | `[T](m: ProductOf[T]): Unit` | /** Require at compile time that `T` has exactly one field. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### TypedRef$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `[A](uuid: UUID): TypedRef[Any]` | /** Create a fresh [[TypedRef]] for a data node with no accumulated options. */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

## Classes

### class AssemblyCtx

/** Mutable dependency collector used inside a [[PipelineBuilder.assemble]] block.
  *
  * Each [[field]] call records one input-field binding (upstream data node UUID → field name of the
  * assembled input type) and returns a placeholder value of the field's type.
  *
  * The collected bindings are consumed by [[PipelineBuilderImpl.assemble]] after the user-supplied
  * lambda returns, to wire `inEdges` and `DataNodeSpec.nicknames` for the fan-in module.
  *
  * ==Usage==
  * {{{
  * p.assemble(mergeModule) { ctx =>
  *   MergeInput(
  *     left  = ctx.field(nodeA, _.left),   // records nodeA.uuid → "left"
  *     right = ctx.field(nodeB, _.right)   // records nodeB.uuid → "right"
  *   )
  * }
  * }}}
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `recordDep` | `(ref: TypedRef[Any], fieldName: String): Unit` | /** Record a dependency: upstream data node `ref` feeds field `fieldName` of the assembled type. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `field` | `[A, B](ref: TypedRef[A], selector: Function1[A, B]): Any` | /** Extract the field name from `selector` at compile time, record the dependency at runtime, and |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getDeps` | `(): List` | /** The collected (dataNodeUUID, fieldName) pairs in recording order. */ |
| `getDepsWithRefs` | `(): List` | /** The collected (TypedRefImpl, fieldName) pairs — used by [[PipelineBuilderImpl]] to flush |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class PipelineBuilderImpl

/** Mutable accumulator that backs [[PipelineBuilder]] during a [[Pipeline.define]] block.
  *
  * Sealed to `private[dsl]` — the public contract is [[PipelineBuilder]].
  */

**Extends:** PipelineBuilder

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `io$constellation$dsl$PipelineBuilder$$inline$stepImpl` | `[I, O](ref: TypedRef[I], built: Uninitialized, evidence$1: CTypeTag[I], evidence$2: CTypeTag[O]): TypedRef[Product]` |  |
| `io$constellation$dsl$PipelineBuilder$$inline$adaptImpl` | `[A, B](ref: TypedRef[A], f: Function1[A, B], ma: ProductOf[A], mb: ProductOf[B], evidence$1: CTypeTag[A], evidence$2: CTypeTag[B]): TypedRef[Product]` |  |
| `adapt` | `[A, B](ref: TypedRef[A], f: Function1[A, B], evidence$1: CTypeTag[A], evidence$2: CTypeTag[B], ma: ProductOf[A], mb: ProductOf[B]): TypedRef[Product]` | /** Insert an inline type-adaptation step between two type-incompatible nodes. |
| `getSyntheticModules` | `(): Map` | /** Synthetic modules created by `p.adapt` calls, keyed by their module node UUID. */ |
| `io$constellation$dsl$PipelineBuilder$$inline$assembleImpl` | `[I, O](built: Uninitialized, f: Function1[AssemblyCtx, I], evidence$1: CTypeTag[I], evidence$2: CTypeTag[O]): TypedRef[Product]` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `input` | `[I](name: String, evidence$1: CTypeTag[I]): TypedRef[Any]` |  |
| `getCallOptions` | `(): Map` | /** Call options collected from [[TypedRef]] chains, keyed by module node UUID. */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `assemble` | `[I, O](module: ModuleBuilder[I, O], f: Function1[AssemblyCtx, I], evidence$1: CTypeTag[I], evidence$2: CTypeTag[O], mi: ProductOf[I], mo: ProductOf[O]): TypedRef[Product]` | /** Wire a multi-input module into the pipeline using an [[AssemblyCtx]] binding block. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `buildDagSpec` | `(name: String): DagSpec` | /** Seal the accumulated state into a [[DagSpec]]. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `output` | `(name: String, ref: TypedRef[Any]): Unit` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `step` | `[I, O](ref: TypedRef[I], module: ModuleBuilder[I, O], evidence$1: CTypeTag[I], evidence$2: CTypeTag[O], mi: ProductOf[I], mo: ProductOf[O]): TypedRef[Product]` | /** Wire a module into the pipeline, consuming `ref` as its single input. |
| `getNamedModules` | `(): List` | /** Named modules collected by `p.step` and `p.assemble`, in declaration order. |

### class TypedPipeline

/** An immutable, executable pipeline built from a [[Pipeline.define]] block.
  *
  * Holds the sealed [[DagSpec]] and any accumulated [[ModuleCallOptions]] from [[TypedRef]] chains.
  * Call [[load]] to obtain a [[LoadedPipeline]] ready to pass to
  * [[io.constellation.Constellation.run]].
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `load` | `(): LoadedPipeline` | /** Wrap the image into a [[LoadedPipeline]] ready for execution. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `registerModules` | `(constellation: Constellation): IO[Unit]` | /** Register each module builder with `constellation` so the runtime can find it by name. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class TypedRefImpl[A]

**Extends:** TypedRef

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `cacheBackend` | `(name: String): TypedRef[A]` |  |
| `onError` | `(strategy: String): TypedRef[A]` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `concurrency` | `(n: Int): TypedRef[A]` |  |
| `priority` | `(p: Int): TypedRef[A]` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `retry` | `(n: Int): TypedRef[A]` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `cache` | `(d: FiniteDuration): TypedRef[A]` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `timeout` | `(d: FiniteDuration): TypedRef[A]` |  |
| `lazyEval` | `(): TypedRef` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `throttle` | `(count: Int, per: FiniteDuration): TypedRef[A]` |  |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `delay` | `(d: FiniteDuration): TypedRef[A]` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `backoff` | `(strategy: String): TypedRef[A]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

## Traits

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
  * [[TypedPipeline.moduleBuilders]], allowing [[TypedPipeline.registerModules]] to register them
  * with a [[io.constellation.Constellation]] instance. Synthetic modules created by `adapt` are
  * never registered — they execute via [[LoadedPipeline.syntheticModules]] directly.
  */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `input` | `[I](name: String, evidence$1: CTypeTag[I]): TypedRef[Any]` | /** Declare a typed pipeline input and return a handle to it. |
| `io$constellation$dsl$PipelineBuilder$$inline$stepImpl` | `[I, O](ref: TypedRef[I], built: Uninitialized, evidence$1: CTypeTag[I], evidence$2: CTypeTag[O]): TypedRef[Product]` |  |
| `io$constellation$dsl$PipelineBuilder$$inline$adaptImpl` | `[A, B](ref: TypedRef[A], f: Function1[A, B], ma: ProductOf[A], mb: ProductOf[B], evidence$1: CTypeTag[A], evidence$2: CTypeTag[B]): TypedRef[Product]` |  |
| `adapt` | `[A, B](ref: TypedRef[A], f: Function1[A, B], evidence$1: CTypeTag[A], evidence$2: CTypeTag[B], ma: ProductOf[A], mb: ProductOf[B]): TypedRef[Product]` | /** Insert an inline type-adaptation step between two type-incompatible nodes. |
| `io$constellation$dsl$PipelineBuilder$$inline$assembleImpl` | `[I, O](built: Uninitialized, f: Function1[AssemblyCtx, I], evidence$1: CTypeTag[I], evidence$2: CTypeTag[O]): TypedRef[Product]` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `output` | `(name: String, ref: TypedRef[Any]): Unit` | /** Declare a named output for the pipeline, bound to the data node referenced by `ref`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `step` | `[I, O](ref: TypedRef[I], module: ModuleBuilder[I, O], evidence$1: CTypeTag[I], evidence$2: CTypeTag[O], mi: ProductOf[I], mo: ProductOf[O]): TypedRef[Product]` | /** Wire a module into the pipeline, consuming `ref` as its single input. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `assemble` | `[I, O](module: ModuleBuilder[I, O], f: Function1[AssemblyCtx, I], evidence$1: CTypeTag[I], evidence$2: CTypeTag[O], mi: ProductOf[I], mo: ProductOf[O]): TypedRef[Product]` | /** Wire a multi-input module into the pipeline using an [[AssemblyCtx]] binding block. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait TypedRef[A]

/** A handle to a typed data node in a pipeline under construction.
  *
  * Carries the UUID of the underlying [[io.constellation.DataNodeSpec]] and any accumulated
  * [[ModuleCallOptions]] for the module that produces it. All option methods return a new
  * `TypedRef[A]` — accumulation is immutable.
  */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `cacheBackend` | `(name: String): TypedRef[A]` | /** Named cache backend to use (overrides the default). */ |
| `onError` | `(strategy: String): TypedRef[A]` | /** Error handling strategy: `"propagate"`, `"skip"`, `"log"`, or `"wrap"`. */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `concurrency` | `(n: Int): TypedRef[A]` | /** Maximum concurrent executions of the producing module. */ |
| `priority` | `(p: Int): TypedRef[A]` | /** Scheduling priority for the producing module (higher value = higher priority). */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `retry` | `(n: Int): TypedRef[A]` | /** Retry the producing module up to `n` times on failure. */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `cache` | `(d: FiniteDuration): TypedRef[A]` | /** Cache the output of the producing module for `d`. */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `timeout` | `(d: FiniteDuration): TypedRef[A]` | /** Abort the producing module if it does not complete within `d`. */ |
| `lazyEval` | `(): TypedRef` | /** Evaluate the producing module lazily (only when its output is consumed). */ |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `throttle` | `(count: Int, per: FiniteDuration): TypedRef[A]` | /** Throttle calls to the producing module: at most `count` per `per` window. */ |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `delay` | `(d: FiniteDuration): TypedRef[A]` | /** Delay execution of the producing module by `d`. */ |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `backoff` | `(strategy: String): TypedRef[A]` | /** Backoff strategy for retries: `"fixed"`, `"linear"`, or `"exponential"`. */ |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

<!-- END GENERATED -->
