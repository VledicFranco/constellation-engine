<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/core/src/main/scala/io/constellation -->
<!-- Hash: 148c223be8d9 -->
<!-- Generated: 2026-02-16T16:11:26.650194300Z -->

# io.constellation.lang

## Objects

### CachingLangCompiler$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(underlying: LangCompiler, cache: CompilationCache): CachingLangCompiler` | /** Create a CachingLangCompiler wrapping the given compiler */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `withDefaults` | `(underlying: LangCompiler): CachingLangCompiler` | /** Create a CachingLangCompiler with default cache configuration */ |
| `withConfig` | `(underlying: LangCompiler, config: Config): CachingLangCompiler` | /** Create a CachingLangCompiler with custom cache configuration */ |
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

### CompilationCache$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `createWithBackend$default$2` | `(): Config` | /** Create a CompilationCache with a specific cache backend. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `create` | `(config: Config): IO[CompilationCache]` | /** Create a new CompilationCache with the default in-memory backend. */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `create$default$1` | `(): Config` | /** Create a new CompilationCache with the default in-memory backend. */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `createWithBackend` | `(backend: CacheBackend, config: Config): IO[CompilationCache]` | /** Create a CompilationCache with a specific cache backend. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `createUnsafe$default$1` | `(): Config` | /** Create a CompilationCache synchronously (for use in non-IO contexts). Should only be used |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `createUnsafeWithBackend$default$2` | `(): Config` | /** Create a CompilationCache synchronously with a specific cache backend. */ |
| `createUnsafe` | `(config: Config): CompilationCache` | /** Create a CompilationCache synchronously (for use in non-IO contexts). Should only be used |
| `createUnsafeWithBackend` | `(backend: CacheBackend, config: Config): CompilationCache` | /** Create a CompilationCache synchronously with a specific cache backend. */ |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### LangCompiler$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(registry: FunctionRegistry, modules: Map[String, Uninitialized], optimizationConfig: OptimizationConfig): LangCompiler` | /** Create a new LangCompiler with the given function registry and module map. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `builder` | `(): LangCompilerBuilder` | /** Builder for constructing a LangCompiler */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `apply$default$3` | `(): OptimizationConfig` | /** Create a new LangCompiler with the given function registry and module map. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `empty` | `(): LangCompiler` | /** Create an empty LangCompiler (no registered functions or modules) */ |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### LangCompilerBuilder$

/** Builder for LangCompiler with fluent API.
  *
  * Optimization is disabled by default. Use `.withOptimization()` to enable.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: LangCompilerBuilder): LangCompilerBuilder` |  |
| `apply` | `(registry: FunctionRegistry, modules: Map[String, Uninitialized], cacheConfig: Option[Config], cacheBackend: Option[CacheBackend], optimizationConfig: OptimizationConfig): LangCompilerBuilder` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### LangCompilerImpl$

/** Implementation of LangCompiler */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(registry: FunctionRegistry, modules: Map[String, Uninitialized], optimizationConfig: OptimizationConfig): LangCompilerImpl` |  |
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

### ModuleBridge$

/** Utilities for registering modules with the compiler */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `extractParams` | `(module: Uninitialized): List[Tuple2[String, SemanticType]]` | /** Extract input parameter types from a module spec */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `signatureFromModule` | `(languageName: String, module: Uninitialized, params: List[Tuple2[String, SemanticType]], returns: SemanticType): FunctionSignature` | /** Create a function signature from a module spec */ |
| `extractReturns` | `(module: Uninitialized): SemanticType` | /** Extract output type from a module spec (assumes single output named "out") */ |
| `ctypeToSemanticType` | `(ctype: CType): SemanticType` | /** Convert CType to SemanticType for module integration */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

## Classes

### class CachingLangCompiler

/** A LangCompiler wrapper that caches compilation results.
  *
  * Provides transparent caching of compilation results to avoid redundant parsing, type checking,
  * and IR generation when the same source is compiled multiple times.
  *
  * Cache invalidation occurs when:
  *   - Source code changes (different sourceHash)
  *   - Function registry changes (different registryHash)
  *   - TTL expires (configurable)
  *
  * @param underlying
  *   the underlying compiler to delegate to on cache miss
  * @param cache
  *   the compilation cache
  */

**Extends:** LangCompiler

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `cacheStatsIO` | `(): IO` | /** Get cache statistics (IO-based, preferred). */ |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `functionRegistry` | `(): FunctionRegistry` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `compileToIR` | `(source: String, dagName: String): Either[List[CompileError], IRPipeline]` |  |
| `invalidateAll` | `(): Unit` | /** Invalidate all cached compilations (blocking). */ |
| `compile` | `(source: String, dagName: String): Either[List[CompileError], CompilationOutput]` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `invalidateIO` | `(dagName: String): IO[Unit]` | /** Invalidate a specific cached compilation (IO-based). */ |
| `compileIO` | `(source: String, dagName: String): IO[Either[List[CompileError], CompilationOutput]]` |  |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `invalidate` | `(dagName: String): Unit` | /** Invalidate a specific cached compilation (blocking). */ |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `cacheStats` | `(): CacheStats` | /** Get cache statistics (blocking, for backward compatibility). */ |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `invalidateAllIO` | `(): IO` | /** Invalidate all cached compilations (IO-based). */ |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class CompilationCache

/** Thread-safe cache for compilation results.
  *
  * Delegates storage to a `CacheBackend` (defaulting to `InMemoryCacheBackend`) for consistency
  * with the runtime cache SPI. The compilation cache adds hash-based validation on top: a cached
  * result is only returned if both the source hash and registry hash match the stored entry.
  *
  * Statistics are tracked at this layer (not delegated to the backend) to ensure accurate hit/miss
  * counts regardless of backend caching behavior.
  *
  * '''Note:''' `CompilationOutput` contains closures (`Module.Uninitialized`) that cannot be
  * serialized, so this cache must use an in-memory backend. The `CacheBackend` abstraction is used
  * for API consistency, not for enabling distributed caching of compilation results.
  *
  * @param backend
  *   the underlying cache backend for storage
  * @param state
  *   thread-safe reference holding the key index and access timestamps
  * @param statsRef
  *   the thread-safe reference to cache statistics
  * @param config
  *   cache configuration
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `put` | `(dagName: String, sourceHash: String, registryHash: String, result: CompilationOutput): IO[Unit]` | /** Store a compilation result in the cache. |
| `invalidateAll` | `(): IO[Unit]` | /** Invalidate all cache entries. */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `stats` | `(): IO` | /** Get current cache statistics. |
| `get` | `(dagName: String, sourceHash: String, registryHash: String): IO[Option[CompilationOutput]]` | /** Look up a cached compilation result. |
| `size` | `(): IO` | /** Get current number of entries in the cache. */ |
| `getBackend` | `(): CacheBackend` | /** Get the underlying cache backend. */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `invalidate` | `(dagName: String): IO[Unit]` | /** Invalidate a specific cache entry by dagName. */ |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class LangCompilerBuilder

/** Builder for LangCompiler with fluent API.
  *
  * Optimization is disabled by default. Use `.withOptimization()` to enable.
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `registry` | `FunctionRegistry` |  |
| `modules` | `Map[String, Uninitialized]` |  |
| `cacheConfig` | `Option[Config]` |  |
| `cacheBackend` | `Option[CacheBackend]` |  |
| `optimizationConfig` | `OptimizationConfig` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `withCacheBackend` | `(backend: CacheBackend): LangCompilerBuilder` | /** Set a custom cache backend for the compilation cache. |
| `copy$default$4` | `(): Option` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `copy$default$1` | `(): FunctionRegistry` |  |
| `copy` | `(registry: FunctionRegistry, modules: Map[String, Uninitialized], cacheConfig: Option[Config], cacheBackend: Option[CacheBackend], optimizationConfig: OptimizationConfig): LangCompilerBuilder` |  |
| `withModules` | `(newModules: Map[String, Uninitialized]): LangCompilerBuilder` | /** Register multiple modules for DagCompiler to access at compile time. This is separate from |
| `withModule` | `(languageName: String, module: Uninitialized, params: List[Tuple2[String, SemanticType]], returns: SemanticType): LangCompilerBuilder` | /** Register a module with its signature */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): Option` |  |
| `productPrefix` | `(): String` |  |
| `withCaching$default$1` | `(): Config` | /** Enable compilation caching with the given configuration */ |
| `productElementNames` | `(): Iterator` |  |
| `_5` | `(): OptimizationConfig` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `productArity` | `(): Int` |  |
| `withCaching` | `(config: Config): LangCompilerBuilder` | /** Enable compilation caching with the given configuration */ |
| `_1` | `(): FunctionRegistry` |  |
| `copy$default$3` | `(): Option` |  |
| `copy$default$5` | `(): OptimizationConfig` |  |
| `withFunction` | `(sig: FunctionSignature): LangCompilerBuilder` | /** Register a function signature for type checking */ |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `withoutOptimization` | `(): LangCompilerBuilder` | /** Disable IR optimization */ |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Map` |  |
| `_4` | `(): Option` |  |
| `withOptimization` | `(config: OptimizationConfig): LangCompilerBuilder` | /** Enable IR optimization with the given configuration */ |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `withOptimization$default$1` | `(): OptimizationConfig` | /** Enable IR optimization with the given configuration */ |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `copy$default$2` | `(): Map` |  |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `withoutCaching` | `(): LangCompilerBuilder` | /** Disable compilation caching */ |
| `build` | `(): LangCompiler` | /** Build the LangCompiler, optionally wrapped with caching */ |

### class LangCompilerImpl

/** Implementation of LangCompiler */

**Extends:** LangCompiler

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `compileToIR` | `(source: String, dagName: String): Either[List[CompileError], IRPipeline]` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `functionRegistry` | `(): FunctionRegistry` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `compile` | `(source: String, dagName: String): Either[List[CompileError], CompilationOutput]` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `compileIO` | `(source: String, dagName: String): IO[Either[List[CompileError], CompilationOutput]]` | /** Async variant of compile that avoids blocking threads. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

## Traits

### trait LangCompiler

/** Main interface for compiling constellation-lang programs */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `compileToIR` | `(source: String, dagName: String): Either[List[CompileError], IRPipeline]` | /** Compile to IR only (for visualization) */ |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `functionRegistry` | `(): FunctionRegistry` | /** Get the function registry for namespace/function introspection */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `compile` | `(source: String, dagName: String): Either[List[CompileError], CompilationOutput]` | /** Compile a constellation-lang source to a CompilationOutput (LoadedPipeline + warnings). */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `compileIO` | `(source: String, dagName: String): IO[Either[List[CompileError], CompilationOutput]]` | /** Async variant of compile that avoids blocking threads. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

<!-- END GENERATED -->
