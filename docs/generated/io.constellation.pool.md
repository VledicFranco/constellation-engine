<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/core/src/main/scala/io/constellation -->
<!-- Hash: b7fd67b9bb19 -->
<!-- Generated: 2026-02-06T18:48:02.570505500Z -->

# io.constellation.pool

## Objects

### DeferredPool$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `create` | `(initialSize: Int, maxSize: Int): IO[DeferredPool]` | /** Create and initialize a DeferredPool. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `create$default$2` | `(): Int` | /** Create and initialize a DeferredPool. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `create$default$1` | `(): Int` | /** Create and initialize a DeferredPool. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `empty` | `(): IO` | /** Create an empty pool (for testing or when pooling is disabled). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### RuntimePool$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `create` | `(config: Config): IO[RuntimePool]` | /** Create a RuntimePool with custom configuration. |
| `create` | `(): IO[RuntimePool]` | /** Create a RuntimePool with default configuration. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `empty` | `(): IO` | /** Create an empty pool (no pre-allocation, for testing). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### RuntimeStatePool$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `create` | `(initialSize: Int, maxSize: Int): IO[RuntimeStatePool]` | /** Create and initialize a RuntimeStatePool. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `create$default$2` | `(): Int` | /** Create and initialize a RuntimeStatePool. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `create$default$1` | `(): Int` | /** Create and initialize a RuntimeStatePool. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `empty` | `(): IO` | /** Create an empty pool (for testing or when pooling is disabled). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

## Classes

### class DeferredPool

/** A pool of pre-allocated Deferred instances for reducing allocation overhead.
  *
  * Deferreds in Cats Effect are one-shot: once completed, they cannot be reset. This pool manages a
  * supply of fresh, uncompleted Deferreds that can be acquired for DAG execution. After execution
  * completes, the pool replenishes itself with fresh Deferreds for subsequent requests.
  *
  * ==Thread Safety==
  *
  * All operations are thread-safe and use Cats Effect's Ref for state management.
  *
  * ==Memory Model==
  *
  * The pool maintains between `initialSize` and `maxSize` Deferreds. When depleted, it creates
  * fresh Deferreds on demand. After execution, calling `replenish` restores the pool to its target
  * size.
  *
  * @param initialSize
  *   Number of Deferreds to pre-allocate on initialization
  * @param maxSize
  *   Maximum pool size (to prevent unbounded growth)
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `acquire` | `(): IO` | /** Acquire a single Deferred from the pool. If the pool is empty, creates a fresh Deferred. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `replenish` | `(count: Int): IO[Unit]` | /** Replenish the pool with fresh Deferreds after execution. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `size` | `(): IO` | /** Get current pool size (for metrics/debugging). |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `getMetrics` | `(): MetricsSnapshot` | /** Get pool metrics snapshot. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `acquireN` | `(n: Int): IO[List[Deferred[IO, Any]]]` | /** Acquire multiple Deferreds efficiently. Takes as many as available from the pool, creates the |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class RuntimePool

/** Unified pool manager for runtime resources.
  *
  * Combines DeferredPool and RuntimeStatePool into a single interface for convenient access and
  * lifecycle management.
  *
  * ==Usage==
  *
  * {{{
  * // Create pool manager
  * val pool = RuntimePool.create().unsafeRunSync()
  *
  * // Use in Runtime.runPooled
  * Runtime.runPooled(dagSpec, inputs, modules, pool)
  * }}}
  *
  * ==Configuration==
  *
  * Pool sizes can be configured based on workload:
  *   - High throughput: Larger pools (more pre-allocation)
  *   - Low memory: Smaller pools (on-demand allocation)
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `sizes` | `(): IO` | /** Get current sizes of both pools. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getMetrics` | `(): CombinedMetrics` | /** Get combined metrics from both pools. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class RuntimeStatePool

/** A pool of reusable runtime state containers.
  *
  * Each execution of a DAG requires mutable state to track module status and data values. This pool
  * maintains pre-allocated containers that can be acquired, used for execution, and then released
  * back to the pool.
  *
  * ==Thread Safety==
  *
  * Pool access is thread-safe via Cats Effect Ref. Each acquired PooledState is exclusively owned
  * by its execution and should not be shared.
  *
  * ==Lifecycle==
  *
  *   1. `acquire` - Get a state container from the pool (or create new if empty) 2. Use the
  *      container during DAG execution 3. `release` - Clear and return the container to the pool
  *
  * @param maxSize
  *   Maximum number of state containers to keep in the pool
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `acquire` | `(): IO` | /** Acquire a state container from the pool. Creates a new one if the pool is empty. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `size` | `(): IO` | /** Get current pool size (for metrics/debugging). |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `getMetrics` | `(): MetricsSnapshot` | /** Get pool metrics snapshot. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `use` | `[A](f: Function1[PooledState, IO[A]]): IO[Any]` | /** Bracket-style acquire/release for safe resource management. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `release` | `(state: PooledState): IO[Unit]` | /** Release a state container back to the pool. The container is cleared before being added to the |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

<!-- END GENERATED -->
