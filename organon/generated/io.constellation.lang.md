<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/core/src/main/scala/io/constellation -->
<!-- Hash: 53238a8a4dd9 -->
<!-- Generated: 2026-02-10T06:41:44.718176700Z -->

# io.constellation.lang

## Objects

### CachingLangCompiler$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `withDefaults` | `(underlying: LangCompiler): CachingLangCompiler` | /** Create a CachingLangCompiler with default cache configuration */ |
| `apply` | `(underlying: LangCompiler, cache: CompilationCache): CachingLangCompiler` | /** Create a CachingLangCompiler wrapping the given compiler */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `withConfig` | `(underlying: LangCompiler, config: Config): CachingLangCompiler` | /** Create a CachingLangCompiler with custom cache configuration */ |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CompilationCache$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `createUnsafeWithBackend$default$2` | `(): Config` | /** Create a CompilationCache synchronously with a specific cache backend. */ |
| `create$default$1` | `(): Config` | /** Create a new CompilationCache with the default in-memory backend. */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `create` | `(config: Config): IO[CompilationCache]` | /** Create a new CompilationCache with the default in-memory backend. */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `createUnsafeWithBackend` | `(backend: CacheBackend, config: Config): CompilationCache` | /** Create a CompilationCache synchronously with a specific cache backend. */ |
| `createUnsafe` | `(config: Config): CompilationCache` | /** Create a CompilationCache synchronously (for use in non-IO contexts). Should only be used |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `createWithBackend` | `(backend: CacheBackend, config: Config): IO[CompilationCache]` | /** Create a CompilationCache with a specific cache backend. |
| `createUnsafe$default$1` | `(): Config` | /** Create a CompilationCache synchronously (for use in non-IO contexts). Should only be used |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `createWithBackend$default$2` | `(): Config` | /** Create a CompilationCache with a specific cache backend. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### LangCompiler$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `empty` | `(): LangCompiler` | /** Create an empty LangCompiler (no registered functions or modules) */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `apply$default$3` | `(): OptimizationConfig` | /** Create a new LangCompiler with the given function registry and module map. |
| `apply` | `(registry: FunctionRegistry, modules: Map[String, Uninitialized], optimizationConfig: OptimizationConfig): LangCompiler` | /** Create a new LangCompiler with the given function registry and module map. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `builder` | `(): LangCompilerBuilder` | /** Builder for constructing a LangCompiler */ |

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
| `toString` | `(): String` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
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
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
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
| `extractReturns` | `(module: Uninitialized): SemanticType` | /** Extract output type from a module spec (assumes single output named "out") */ |
| `signatureFromModule` | `(languageName: String, module: Uninitialized, params: List[Tuple2[String, SemanticType]], returns: SemanticType): FunctionSignature` | /** Create a function signature from a module spec */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `ctypeToSemanticType` | `(ctype: CType): SemanticType` | /** Convert CType to SemanticType for module integration */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `extractParams` | `(module: Uninitialized): List[Tuple2[String, SemanticType]]` | /** Extract input parameter types from a module spec */ |
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
| `invalidateAll` | `(): Unit` | /** Invalidate all cached compilations (blocking). */ |
| `invalidateIO` | `(dagName: String): IO[Unit]` | /** Invalidate a specific cached compilation (IO-based). */ |
| `functionRegistry` | `(): FunctionRegistry` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `compile` | `(source: String, dagName: String): Either[List[CompileError], CompilationOutput]` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `cacheStats` | `(): CacheStats` | /** Get cache statistics (blocking, for backward compatibility). */ |
| `cacheStatsIO` | `(): IO` | /** Get cache statistics (IO-based, preferred). */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `invalidateAllIO` | `(): IO` | /** Invalidate all cached compilations (IO-based). */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `compileToIR` | `(source: String, dagName: String): Either[List[CompileError], IRPipeline]` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `invalidate` | `(dagName: String): Unit` | /** Invalidate a specific cached compilation (blocking). */ |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `compileIO` | `(source: String, dagName: String): IO[Either[List[CompileError], CompilationOutput]]` |  |
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
| `invalidateAll` | `(): IO[Unit]` | /** Invalidate all cache entries. */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `size` | `(): IO` | /** Get current number of entries in the cache. */ |
| `stats` | `(): IO` | /** Get current cache statistics. |
| `put` | `(dagName: String, sourceHash: String, registryHash: String, result: CompilationOutput): IO[Unit]` | /** Store a compilation result in the cache. |
| `getBackend` | `(): CacheBackend` | /** Get the underlying cache backend. */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `get` | `(dagName: String, sourceHash: String, registryHash: String): IO[Option[CompilationOutput]]` | /** Look up a cached compilation result. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `invalidate` | `(dagName: String): IO[Unit]` | /** Invalidate a specific cache entry by dagName. */ |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
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
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `copy` | `(registry: FunctionRegistry, modules: Map[String, Uninitialized], cacheConfig: Option[Config], cacheBackend: Option[CacheBackend], optimizationConfig: OptimizationConfig): LangCompilerBuilder` |  |
| `build` | `(): LangCompiler` | /** Build the LangCompiler, optionally wrapped with caching */ |
| `_3` | `(): Option` |  |
| `productPrefix` | `(): String` |  |
| `_5` | `(): OptimizationConfig` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `withoutCaching` | `(): LangCompilerBuilder` | /** Disable compilation caching */ |
| `productArity` | `(): Int` |  |
| `_1` | `(): FunctionRegistry` |  |
| `copy$default$2` | `(): Map` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `withModule` | `(languageName: String, module: Uninitialized, params: List[Tuple2[String, SemanticType]], returns: SemanticType): LangCompilerBuilder` | /** Register a module with its signature */ |
| `withOptimization$default$1` | `(): OptimizationConfig` | /** Enable IR optimization with the given configuration */ |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `productElementNames` | `(): Iterator` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `withCaching$default$1` | `(): Config` | /** Enable compilation caching with the given configuration */ |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `withOptimization` | `(config: OptimizationConfig): LangCompilerBuilder` | /** Enable IR optimization with the given configuration */ |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Map` |  |
| `_4` | `(): Option` |  |
| `productElement` | `(n: Int): Any` |  |
| `copy$default$4` | `(): Option` |  |
| `withModules` | `(newModules: Map[String, Uninitialized]): LangCompilerBuilder` | /** Register multiple modules for DagCompiler to access at compile time. This is separate from |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `copy$default$5` | `(): OptimizationConfig` |  |
| `withoutOptimization` | `(): LangCompilerBuilder` | /** Disable IR optimization */ |
| `copy$default$1` | `(): FunctionRegistry` |  |
| `withCacheBackend` | `(backend: CacheBackend): LangCompilerBuilder` | /** Set a custom cache backend for the compilation cache. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `withCaching` | `(config: Config): LangCompilerBuilder` | /** Enable compilation caching with the given configuration */ |
| `copy$default$3` | `(): Option` |  |
| `withFunction` | `(sig: FunctionSignature): LangCompilerBuilder` | /** Register a function signature for type checking */ |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class LangCompilerImpl

/** Implementation of LangCompiler */

**Extends:** LangCompiler

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `functionRegistry` | `(): FunctionRegistry` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `compile` | `(source: String, dagName: String): Either[List[CompileError], CompilationOutput]` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `compileToIR` | `(source: String, dagName: String): Either[List[CompileError], IRPipeline]` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `compileIO` | `(source: String, dagName: String): IO[Either[List[CompileError], CompilationOutput]]` | /** Async variant of compile that avoids blocking threads. |
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
| `functionRegistry` | `(): FunctionRegistry` | /** Get the function registry for namespace/function introspection */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `compile` | `(source: String, dagName: String): Either[List[CompileError], CompilationOutput]` | /** Compile a constellation-lang source to a CompilationOutput (LoadedPipeline + warnings). */ |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `compileToIR` | `(source: String, dagName: String): Either[List[CompileError], IRPipeline]` | /** Compile to IR only (for visualization) */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `compileIO` | `(source: String, dagName: String): IO[Either[List[CompileError], CompilationOutput]]` | /** Async variant of compile that avoids blocking threads. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

<!-- END GENERATED -->
