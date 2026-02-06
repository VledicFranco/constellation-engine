<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/core/src/main/scala/io/constellation -->
<!-- Hash: b7fd67b9bb19 -->
<!-- Generated: 2026-02-06T09:24:15.807948200Z -->

# io.constellation.cache

## Objects

### CacheEntry$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `[A](x$1: CacheEntry[A]): CacheEntry[Any]` |  |
| `apply` | `[A](value: A, createdAt: Long, expiresAt: Long): CacheEntry[Any]` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `create` | `[A](value: A, ttl: FiniteDuration): CacheEntry[Any]` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CacheKeyGenerator$

/** Generates deterministic cache keys from module names and inputs.
  *
  * Cache keys are computed as:
  * {{{
  * key = hash(moduleName + canonicalSerialize(inputs))
  * }}}
  *
  * The canonical serialization ensures that equivalent inputs produce the same key, regardless of
  * map ordering, etc.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `generateKey` | `(moduleName: String, inputs: Map[String, CValue], version: Option[String]): String` | /** Generate a cache key for a module call. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `generateKey$default$3` | `(): Option` | /** Generate a cache key for a module call. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashBytes` | `(bytes: Array[Byte]): String` | /** Compute a hash for raw bytes (e.g., for binary inputs). */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `generateShortKey` | `(moduleName: String, inputs: Map[String, CValue], length: Int): String` | /** Generate a short key (truncated hash) for display purposes. Not suitable for production cache |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `generateShortKey$default$3` | `(): Int` | /** Generate a short key (truncated hash) for display purposes. Not suitable for production cache |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CacheRegistry$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `withBackends` | `(backends: Seq): IO[CacheRegistry]` | /** Create a registry with pre-configured backends. */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `create` | `(): IO` | /** Create an empty cache registry. */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `withMemory` | `(maxSize: Int): IO[CacheRegistry]` | /** Create a registry with a sized in-memory backend. */ |
| `withMemory` | `(): IO` | /** Create a registry with a default in-memory backend. */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CacheSerde$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `javaSerde` | `[A](): CacheSerde[Serializable]` | /** Java serialization fallback for any Serializable value. |
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
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CacheSerdeException$

/** Exception thrown when cache serialization or deserialization fails. */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(message: String, cause: Throwable): CacheSerdeException` |  |
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
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CacheStats$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: CacheStats): CacheStats` |  |
| `apply` | `(hits: Long, misses: Long, evictions: Long, size: Int, maxSize: Option[Int]): CacheStats` |  |
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
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### InMemoryCacheBackend$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(): InMemoryCacheBackend` | /** Create a new in-memory cache with no size limit. */ |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `withMaxSize` | `(maxSize: Int): InMemoryCacheBackend` | /** Create a new in-memory cache with max size (LRU eviction). */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

## Classes

### case class CacheEntry[A]

/** A cached entry with metadata. */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `value` | `A` |  |
| `createdAt` | `Long` |  |
| `expiresAt` | `Long` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productArity` | `(): Int` |  |
| `isExpired` | `(): Boolean` | /** Check if this entry has expired. */ |
| `_1` | `(): A` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `copy$default$1` | `[A](): A` |  |
| `copy` | `[A](value: A, createdAt: Long, expiresAt: Long): CacheEntry[Any]` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): Long` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$2` | `[A](): Long` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Long` |  |
| `productElementNames` | `(): Iterator` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `copy$default$3` | `[A](): Long` |  |
| `remainingTtlMs` | `(): Long` | /** Remaining TTL in milliseconds, or 0 if expired. */ |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class CacheRegistryImpl

/** Default implementation using Cats Effect Ref for thread-safe state. */

**Extends:** CacheRegistry

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `unregister` | `(name: String): IO[Boolean]` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `register` | `(name: String, backend: CacheBackend): IO[Unit]` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `clearAll` | `(): IO` |  |
| `setDefault` | `(name: String): IO[Boolean]` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `list` | `(): IO` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `get` | `(name: String): IO[Option[CacheBackend]]` |  |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `allStats` | `(): IO` |  |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `default` | `(): IO` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class CacheSerdeException

/** Exception thrown when cache serialization or deserialization fails. */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getMessage` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getLocalizedMessage` | `(): String` |  |
| `toString` | `(): String` |  |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `setCause` | `(x$0: Throwable): Unit` |  |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |

### case class CacheStats

/** Cache statistics for monitoring and debugging.
  *
  * This is the canonical cache statistics type used across the entire codebase, including both
  * runtime module caching and compilation caching.
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `hits` | `Long` |  |
| `misses` | `Long` |  |
| `evictions` | `Long` |  |
| `size` | `Int` |  |
| `maxSize` | `Option[Int]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `copy$default$5` | `(): Option` |  |
| `productArity` | `(): Int` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `copy$default$1` | `(): Long` |  |
| `copy` | `(hits: Long, misses: Long, evictions: Long, size: Int, maxSize: Option[Int]): CacheStats` |  |
| `entries` | `(): Int` | /** Alias for [[size]], used by compilation cache consumers. */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): Long` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$2` | `(): Long` |  |
| `copy$default$4` | `(): Int` |  |
| `_5` | `(): Option` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_1` | `(): Long` |  |
| `hitRatio` | `(): Double` | /** Cache hit ratio (0.0 to 1.0). */ |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Long` |  |
| `productElementNames` | `(): Iterator` |  |
| `_4` | `(): Int` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `copy$default$3` | `(): Long` |  |
| `hitRate` | `(): Double` | /** Alias for [[hitRatio]], used by compilation cache consumers. */ |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class DistributedCacheBackend

/** Abstract base class for distributed (network-backed) cache backends.
  *
  * Provides the bridge between the type-erased `CacheBackend` interface and byte-level network
  * operations. Subclasses implement `getBytes`/`setBytes`/`deleteKey`/`clearAll`/`getStats` to
  * communicate with the remote cache server.
  *
  * Serialization is handled by a `CacheSerde[Any]` instance, which by default tries JSON for
  * `CValue` instances and falls back to Java serialization for other types.
  *
  * ==Implementing a new backend==
  *
  * {{{
  * class RedisCacheBackend(client: RedisClient, serde: CacheSerde[Any])
  *   extends DistributedCacheBackend(serde) {
  *
  *   override protected def getBytes(key: String) = ...
  *   override protected def setBytes(key: String, bytes: Array[Byte], ttl: FiniteDuration) = ...
  *   override protected def deleteKey(key: String) = ...
  *   override protected def clearAll = ...
  *   override protected def getStats = ...
  * }
  * }}}
  *
  * @param serde
  *   Serialization strategy for cache values
  */

**Extends:** CacheBackend

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getOrCompute` | `[A](key: String, ttl: FiniteDuration, compute: IO): IO[Any]` | /** Get or compute a value. |
| `stats` | `(): IO` |  |
| `delete` | `(key: String): IO[Boolean]` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `get` | `[A](key: String): IO[Option[CacheEntry[Any]]]` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `clear` | `(): IO` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `contains` | `(key: String): IO[Boolean]` | /** Check if a key exists and is not expired. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `set` | `[A](key: String, value: A, ttl: FiniteDuration): IO[Unit]` |  |

### class InMemoryCacheBackend

/** In-memory cache backend using ConcurrentHashMap.
  *
  * Suitable for single-instance deployments and development. For distributed systems, use Redis or
  * Memcached backends.
  *
  * ==Features==
  *
  *   - Thread-safe concurrent access
  *   - TTL-based expiration (lazy cleanup)
  *   - Optional max size with LRU eviction
  *   - Statistics tracking
  *
  * ==Usage==
  *
  * {{{
  * // Basic usage
  * val cache = InMemoryCacheBackend()
  *
  * // With max size (LRU eviction)
  * val cache = InMemoryCacheBackend(maxSize = Some(1000))
  *
  * // Store and retrieve
  * cache.set("key", myValue, ttl = 5.minutes).unsafeRunSync()
  * val result = cache.get[MyType]("key").unsafeRunSync()
  * }}}
  */

**Extends:** CacheBackend

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getOrCompute` | `[A](key: String, ttl: FiniteDuration, compute: IO): IO[Any]` | /** Get or compute a value. |
| `delete` | `(key: String): IO[Boolean]` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `get` | `[A](key: String): IO[Option[CacheEntry[Any]]]` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `set` | `[A](key: String, value: A, ttl: FiniteDuration): IO[Unit]` |  |
| `stats` | `(): IO` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `clear` | `(): IO` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `resetStats` | `(): IO` | /** Reset all statistics counters. Useful for testing or metrics reset. |
| `forceCleanup` | `(): IO` | /** Force cleanup of all expired entries. Useful for testing or manual maintenance. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `contains` | `(key: String): IO[Boolean]` | /** Check if a key exists and is not expired. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

## Traits

### trait CacheBackend

/** Pluggable cache backend interface.
  *
  * Implementations must be thread-safe and support concurrent access. All operations return IO to
  * allow for async backends (e.g., Redis).
  *
  * ==Usage==
  *
  * {{{
  * val cache: CacheBackend = InMemoryCacheBackend()
  *
  * // Store with TTL
  * cache.set("key", "value", ttl = 5.minutes)
  *
  * // Retrieve
  * val result = cache.get("key")  // IO[Option[CacheEntry[String]]]
  *
  * // Get statistics
  * val stats = cache.stats  // IO[CacheStats]
  * }}}
  */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getOrCompute` | `[A](key: String, ttl: FiniteDuration, compute: IO): IO[Any]` | /** Get or compute a value. |
| `stats` | `(): IO` | /** Get cache statistics. */ |
| `delete` | `(key: String): IO[Boolean]` | /** Delete a specific key from the cache. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `get` | `[A](key: String): IO[Option[CacheEntry[Any]]]` | /** Get a value from the cache. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `set` | `[A](key: String, value: A, ttl: FiniteDuration): IO[Unit]` | /** Store a value in the cache with TTL. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `clear` | `(): IO` | /** Clear all entries from the cache. */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `contains` | `(key: String): IO[Boolean]` | /** Check if a key exists and is not expired. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait CacheRegistry

/** Registry for managing multiple cache backends.
  *
  * Allows configuring different backends for different use cases:
  *   - `memory` - In-process cache for development
  *   - `caffeine` - Caffeine-based cache for production single instance
  *   - `redis` - Distributed cache for multi-instance deployments
  *
  * ==Usage==
  *
  * {{{
  * val registry = CacheRegistry.create.unsafeRunSync()
  *
  * // Register backends
  * registry.register("memory", InMemoryCacheBackend()).unsafeRunSync()
  * registry.register("redis", redisCacheBackend).unsafeRunSync()
  *
  * // Get backend by name
  * val cache = registry.get("redis").unsafeRunSync()
  *
  * // Use default backend
  * val default = registry.default.unsafeRunSync()
  * }}}
  */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `unregister` | `(name: String): IO[Boolean]` | /** Unregister a backend. */ |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `register` | `(name: String, backend: CacheBackend): IO[Unit]` | /** Register a cache backend with a name. */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `get` | `(name: String): IO[Option[CacheBackend]]` | /** Get a cache backend by name. */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `clearAll` | `(): IO` | /** Clear all backends. */ |
| `setDefault` | `(name: String): IO[Boolean]` | /** Set the default backend by name. */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `list` | `(): IO` | /** List all registered backend names. */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `allStats` | `(): IO` | /** Get statistics for all backends. */ |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `default` | `(): IO` | /** Get the default cache backend. Returns the first registered backend, or InMemoryCacheBackend |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait CacheSerde[A]

/** Type class for serializing and deserializing cache values.
  *
  * Distributed cache backends (Memcached, Redis) need to convert values to/from bytes for network
  * transport. This type class provides pluggable serialization strategies.
  *
  * ==Built-in instances==
  *
  *   - `CacheSerde.cvalueSerde` - JSON-based serde for `CValue` using existing Circe codecs
  *   - `CacheSerde.mapCValueSerde` - JSON-based serde for `Map[String, CValue]`
  *   - `CacheSerde.javaSerde` - Java serialization fallback for any `Serializable` value
  *
  * ==Custom implementations==
  *
  * {{{
  * given mySerde: CacheSerde[MyType] = new CacheSerde[MyType] {
  *   def serialize(value: MyType): Array[Byte] = ...
  *   def deserialize(bytes: Array[Byte]): MyType = ...
  * }
  * }}}
  */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `serialize` | `(value: A): Array[Byte]` | /** Serialize a value to bytes. */ |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `deserialize` | `(bytes: Array[Byte]): A` | /** Deserialize bytes back to a value. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

<!-- END GENERATED -->
