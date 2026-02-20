<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/core/src/main/scala/io/constellation -->
<!-- Hash: 9265ebb4609c -->
<!-- Generated: 2026-02-20T22:04:13.632288400Z -->

# io.constellation.impl

## Objects

### ConstellationImpl$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(moduleRegistry: ModuleRegistry, PipelineStoreInstance: PipelineStore, scheduler: GlobalScheduler, backends: ConstellationBackends, defaultTimeout: Option[FiniteDuration], lifecycle: Option[ConstellationLifecycle], suspensionStoreOpt: Option[SuspensionStore]): ConstellationImpl` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `initWithScheduler` | `(scheduler: GlobalScheduler): IO[ConstellationImpl]` | /** Initialize with a custom scheduler for priority-based execution. |
| `builder` | `(): ConstellationBuilder` | /** Create a builder for configuring ConstellationImpl with custom backends. */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `init` | `(): IO` | /** Initialize with default unbounded scheduler. */ |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### InMemorySuspensionStore$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `initWithCodecValidation` | `(codec: SuspensionCodec): IO[SuspensionStore]` | /** Create a new empty in-memory suspension store with codec validation. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `initWithTTL` | `(ttl: FiniteDuration): IO[SuspensionStore]` | /** Create a new empty in-memory suspension store with a TTL. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `init` | `(): IO` | /** Create a new empty in-memory suspension store. */ |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ModuleRegistryImpl$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(modulesRef: Ref[IO, Map[String, Uninitialized]], nameIndexRef: Ref[IO, Map[String, String]]): ModuleRegistryImpl` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `withModules` | `(modules: List[Tuple2[String, Uninitialized]]): IO[ModuleRegistryImpl]` | /** Create with pre-populated modules (for testing). */ |
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
| `init` | `(): IO` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### PipelineStoreImpl$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
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
| `init` | `(): IO` | /** Create a new empty in-memory PipelineStore. */ |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

## Classes

### class ConstellationImpl

/** Default implementation of the [[io.constellation.Constellation]] API.
  *
  * Manages module registry, delegates execution to the [[io.constellation.Runtime]], and integrates
  * with the SPI backend layer for metrics, tracing, caching, and lifecycle management.
  *
  * ==Construction==
  *
  * Use the companion object factory methods:
  * {{{
  * // Minimal setup
  * val constellation = ConstellationImpl.init
  *
  * // With custom scheduler
  * val constellation = ConstellationImpl.initWithScheduler(scheduler)
  *
  * // Full configuration via builder
  * val constellation = ConstellationImpl.builder()
  *   .withScheduler(scheduler)
  *   .withBackends(backends)
  *   .withDefaultTimeout(30.seconds)
  *   .build()
  * }}}
  *
  * @param moduleRegistry
  *   Registry for module definitions
  * @param PipelineStoreInstance
  *   Pipeline image store
  * @param scheduler
  *   Global scheduler for task ordering and concurrency control
  * @param backends
  *   Pluggable SPI backends (metrics, tracing, listener, cache, circuit breakers)
  * @param defaultTimeout
  *   Optional default timeout applied to all DAG executions
  * @param lifecycle
  *   Optional lifecycle manager for graceful shutdown support
  *
  * @see
  *   [[io.constellation.impl.ConstellationImpl.ConstellationBuilder]] for the builder API
  */

**Extends:** Constellation

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `resumeFromStore$default$3` | `(): Map` | /** Resume a suspended execution from the SuspensionStore. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `run` | `(ref: String, inputs: Map[String, CValue], options: ExecutionOptions): IO[DataSignature]` |  |
| `run` | `(loaded: LoadedPipeline, inputs: Map[String, CValue], options: ExecutionOptions): IO[DataSignature]` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getModuleByName` | `(name: String): IO[Option[Uninitialized]]` |  |
| `removeModule` | `(name: String): IO[Unit]` |  |
| `suspensionStore` | `(): Option` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getModules` | `(): IO` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `publishedModules` | `(): IO` | /** List modules that have been published as HTTP endpoints. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `setModule` | `(factory: Uninitialized): IO[Unit]` |  |
| `resumeFromStore$default$4` | `(): ExecutionOptions` | /** Resume a suspended execution from the SuspensionStore. |
| `run$default$3` | `(): ExecutionOptions` |  |
| `resumeFromStore` | `(handle: SuspensionHandle, additionalInputs: Map[String, CValue], resolvedNodes: Map[String, CValue], options: ExecutionOptions): IO[DataSignature]` |  |
| `resumeFromStore$default$2` | `(): Map` | /** Resume a suspended execution from the SuspensionStore. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `PipelineStore` | `(): PipelineStore` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class InMemorySuspensionStore

/** In-memory implementation of [[SuspensionStore]] backed by a concurrent `Ref`.
  *
  * Suitable for development and testing. Stored suspensions are lost on JVM restart.
  *
  * @param store
  *   Concurrent map of handle ID -> stored entry
  * @param codecOpt
  *   Optional codec for round-trip validation on save
  * @param ttl
  *   Optional TTL for stored entries. Entries older than the TTL are lazily evicted on save/load
  *   operations. Default: None (entries live forever).
  */

**Extends:** SuspensionStore

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `delete` | `(handle: SuspensionHandle): IO[Boolean]` |  |
| `list` | `(filter: SuspensionFilter): IO[List[SuspensionSummary]]` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `list$default$1` | `(): SuspensionFilter` | /** List stored suspensions matching the given filter. |
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
| `load` | `(handle: SuspensionHandle): IO[Option[SuspendedExecution]]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `save` | `(suspended: SuspendedExecution): IO[SuspensionHandle]` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class ModuleRegistryImpl

/** Optimized ModuleRegistry implementation with pre-computed name index.
  *
  * ==Performance Optimization==
  *
  * Instead of performing string operations on every lookup, name variants are pre-computed at
  * registration time. This eliminates:
  *   - String splitting on lookups
  *   - Array allocations on lookups
  *   - Secondary map lookups for stripped names
  *
  * ==Name Resolution==
  *
  * Modules can be looked up by:
  *   - Full name (e.g., "mydag.Uppercase")
  *   - Short name (e.g., "Uppercase") if unambiguous
  *
  * When multiple modules share the same short name (e.g., "dag1.Transform" and "dag2.Transform"),
  * the first registered module wins for short name lookup. Full names always work correctly.
  *
  * ==Thread Safety==
  *
  * Uses Cats Effect Ref for thread-safe atomic updates.
  */

**Extends:** ModuleRegistry

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `indexSize` | `(): IO` | /** Get number of indexed names (may be > size due to short names). */ |
| `register` | `(name: String, node: Uninitialized): IO[Unit]` |  |
| `contains` | `(name: String): IO[Boolean]` | /** Check if a module is registered (by any name variant). */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `registerAll` | `(modules: List[Tuple2[String, Uninitialized]]): IO[Unit]` | /** Register multiple modules efficiently. Useful for batch registration at startup. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `get` | `(name: String): IO[Option[Uninitialized]]` | /** Fast module lookup using pre-computed index. Tries exact match first, then stripped name |
| `size` | `(): IO` | /** Get number of registered modules. */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `clear` | `(): IO` | /** Clear all modules (for testing). */ |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `initModules` | `(dagSpec: DagSpec): IO[Map[UUID, Uninitialized]]` |  |
| `deregister` | `(name: String): IO[Unit]` |  |
| `listModules` | `(): IO` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class PipelineStoreImpl

/** In-memory implementation of [[PipelineStore]].
  *
  * Uses three concurrent Ref maps: one for images, one for aliases, and one for the syntactic
  * index.
  */

**Extends:** PipelineStore

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `store` | `(image: PipelineImage): IO[String]` |  |
| `indexSyntactic` | `(syntacticHash: String, registryHash: String, structuralHash: String): IO[Unit]` |  |
| `lookupSyntactic` | `(syntacticHash: String, registryHash: String): IO[Option[String]]` |  |
| `resolve` | `(name: String): IO[Option[String]]` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `alias` | `(name: String, structuralHash: String): IO[Unit]` |  |
| `get` | `(structuralHash: String): IO[Option[PipelineImage]]` |  |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `listImages` | `(): IO` |  |
| `getByName` | `(name: String): IO[Option[PipelineImage]]` |  |
| `remove` | `(structuralHash: String): IO[Boolean]` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `listAliases` | `(): IO` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

<!-- END GENERATED -->
