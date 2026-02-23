<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/core/src/main/scala/io/constellation -->
<!-- Hash: 9265ebb4609c -->
<!-- Generated: 2026-02-23T03:19:06.467157300Z -->

# io.constellation.impl

## Objects

### ConstellationImpl$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `builder` | `(): ConstellationBuilder` | /** Create a builder for configuring ConstellationImpl with custom backends. */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `initWithScheduler` | `(scheduler: GlobalScheduler): IO[ConstellationImpl]` | /** Initialize with a custom scheduler for priority-based execution. |
| `apply` | `(moduleRegistry: ModuleRegistry, PipelineStoreInstance: PipelineStore, scheduler: GlobalScheduler, backends: ConstellationBackends, defaultTimeout: Option[FiniteDuration], lifecycle: Option[ConstellationLifecycle], suspensionStoreOpt: Option[SuspensionStore]): ConstellationImpl` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `init` | `(): IO` | /** Initialize with default unbounded scheduler. */ |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### InMemorySuspensionStore$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `initWithCodecValidation` | `(codec: SuspensionCodec): IO[SuspensionStore]` | /** Create a new empty in-memory suspension store with codec validation. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `initWithTTL` | `(ttl: FiniteDuration): IO[SuspensionStore]` | /** Create a new empty in-memory suspension store with a TTL. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `init` | `(): IO` | /** Create a new empty in-memory suspension store. */ |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ModuleRegistryImpl$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(modulesRef: Ref[IO, Map[String, Uninitialized]], nameIndexRef: Ref[IO, Map[String, String]]): ModuleRegistryImpl` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `withModules` | `(modules: List[Tuple2[String, Uninitialized]]): IO[ModuleRegistryImpl]` | /** Create with pre-populated modules (for testing). */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `init` | `(): IO` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### PipelineStoreImpl$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `init` | `(): IO` | /** Create a new empty in-memory PipelineStore. */ |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
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
| `suspensionStore` | `(): Option` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `getModuleByName` | `(name: String): IO[Option[Uninitialized]]` |  |
| `removeModule` | `(name: String): IO[Unit]` |  |
| `run$default$3` | `(): ExecutionOptions` |  |
| `resumeFromStore$default$2` | `(): Map` | /** Resume a suspended execution from the SuspensionStore. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getModules` | `(): IO` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `run` | `(ref: String, inputs: Map[String, CValue], options: ExecutionOptions): IO[DataSignature]` |  |
| `run` | `(loaded: LoadedPipeline, inputs: Map[String, CValue], options: ExecutionOptions): IO[DataSignature]` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `getBatchFunctions` | `(moduleNames: List[String]): IO[Map[String, Function1[List[CValue], IO[List[Either[Throwable, CValue]]]]]]` | /** Get batch functions for external modules by qualified name (RFC-034 Phase 1). |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `getStreamingBatchFunctions` | `(moduleNames: List[String]): IO[Map[String, Function1[List[CValue], IO[List[Either[Throwable, CValue]]]]]]` | /** Get streaming batch functions for persistent bidirectional gRPC streaming (RFC-034 Phase 2B). |
| `setModule` | `(factory: Uninitialized): IO[Unit]` |  |
| `resumeFromStore$default$4` | `(): ExecutionOptions` | /** Resume a suspended execution from the SuspensionStore. |
| `resumeFromStore` | `(handle: SuspensionHandle, additionalInputs: Map[String, CValue], resolvedNodes: Map[String, CValue], options: ExecutionOptions): IO[DataSignature]` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `publishedModules` | `(): IO` | /** List modules that have been published as HTTP endpoints. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `resumeFromStore$default$3` | `(): Map` | /** Resume a suspended execution from the SuspensionStore. |
| `PipelineStore` | `(): PipelineStore` |  |
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
| `list` | `(filter: SuspensionFilter): IO[List[SuspensionSummary]]` |  |
| `delete` | `(handle: SuspensionHandle): IO[Boolean]` |  |
| `save` | `(suspended: SuspendedExecution): IO[SuspensionHandle]` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `load` | `(handle: SuspensionHandle): IO[Option[SuspendedExecution]]` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `list$default$1` | `(): SuspensionFilter` | /** List stored suspensions matching the given filter. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
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
| `register` | `(name: String, node: Uninitialized): IO[Unit]` |  |
| `contains` | `(name: String): IO[Boolean]` | /** Check if a module is registered (by any name variant). */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `registerAll` | `(modules: List[Tuple2[String, Uninitialized]]): IO[Unit]` | /** Register multiple modules efficiently. Useful for batch registration at startup. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `get` | `(name: String): IO[Option[Uninitialized]]` | /** Fast module lookup using pre-computed index. Tries exact match first, then stripped name |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `clear` | `(): IO` | /** Clear all modules (for testing). */ |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `indexSize` | `(): IO` | /** Get number of indexed names (may be > size due to short names). */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `size` | `(): IO` | /** Get number of registered modules. */ |
| `initModules` | `(dagSpec: DagSpec): IO[Map[UUID, Uninitialized]]` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
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
| `listAliases` | `(): IO` |  |
| `resolve` | `(name: String): IO[Option[String]]` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `indexSyntactic` | `(syntacticHash: String, registryHash: String, structuralHash: String): IO[Unit]` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `get` | `(structuralHash: String): IO[Option[PipelineImage]]` |  |
| `remove` | `(structuralHash: String): IO[Boolean]` |  |
| `store` | `(image: PipelineImage): IO[String]` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `alias` | `(name: String, structuralHash: String): IO[Unit]` |  |
| `lookupSyntactic` | `(syntacticHash: String, registryHash: String): IO[Option[String]]` |  |
| `listImages` | `(): IO` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `getByName` | `(name: String): IO[Option[PipelineImage]]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

<!-- END GENERATED -->
