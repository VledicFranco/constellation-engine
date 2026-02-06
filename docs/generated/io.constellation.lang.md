<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/core/src/main/scala/io/constellation -->
<!-- Hash: b7fd67b9bb19 -->
<!-- Generated: 2026-02-06T09:24:15.740467600Z -->

# io.constellation.lang

## Objects

### CachingLangCompiler$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(underlying: LangCompiler, cache: CompilationCache): CachingLangCompiler` | /** Create a CachingLangCompiler wrapping the given compiler */ |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `withDefaults` | `(underlying: LangCompiler): CachingLangCompiler` | /** Create a CachingLangCompiler with default cache configuration */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `withConfig` | `(underlying: LangCompiler, config: Config): CachingLangCompiler` | /** Create a CachingLangCompiler with custom cache configuration */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
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
| `createUnsafeWithBackend` | `(backend: CacheBackend, config: Config): CompilationCache` | /** Create a CompilationCache synchronously with a specific cache backend. */ |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `create` | `(config: Config): IO[CompilationCache]` | /** Create a new CompilationCache with the default in-memory backend. */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `createUnsafe$default$1` | `(): Config` | /** Create a CompilationCache synchronously (for use in non-IO contexts). Should only be used |
| `createWithBackend` | `(backend: CacheBackend, config: Config): IO[CompilationCache]` | /** Create a CompilationCache with a specific cache backend. |
| `createUnsafe` | `(config: Config): CompilationCache` | /** Create a CompilationCache synchronously (for use in non-IO contexts). Should only be used |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `createWithBackend$default$2` | `(): Config` | /** Create a CompilationCache with a specific cache backend. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `createUnsafeWithBackend$default$2` | `(): Config` | /** Create a CompilationCache synchronously with a specific cache backend. */ |
| `create$default$1` | `(): Config` | /** Create a new CompilationCache with the default in-memory backend. */ |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### LangCompiler$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `builder` | `(): LangCompilerBuilder` | /** Builder for constructing a LangCompiler */ |
| `apply` | `(registry: FunctionRegistry, modules: Map[String, Uninitialized], optimizationConfig: OptimizationConfig): LangCompiler` | /** Create a new LangCompiler with the given function registry and module map. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `apply$default$3` | `(): OptimizationConfig` | /** Create a new LangCompiler with the given function registry and module map. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `empty` | `(): LangCompiler` | /** Create an empty LangCompiler (no registered functions or modules) */ |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
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
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
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
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `extractParams` | `(module: Uninitialized): List[Tuple2[String, SemanticType]]` | /** Extract input parameter types from a module spec */ |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `ctypeToSemanticType` | `(ctype: CType): SemanticType` | /** Convert CType to SemanticType for module integration */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `extractReturns` | `(module: Uninitialized): SemanticType` | /** Extract output type from a module spec (assumes single output named "out") */ |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `signatureFromModule` | `(languageName: String, module: Uninitialized, params: List[Tuple2[String, SemanticType]], returns: SemanticType): FunctionSignature` | /** Create a function signature from a module spec */ |
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
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `compile` | `(source: String, dagName: String): Either[List[CompileError], CompilationOutput]` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `functionRegistry` | `(): FunctionRegistry` |  |
| `invalidate` | `(dagName: String): Unit` | /** Invalidate a specific cached compilation (blocking). */ |
| `compileIO` | `(source: String, dagName: String): IO[Either[List[CompileError], CompilationOutput]]` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `invalidateAll` | `(): Unit` | /** Invalidate all cached compilations (blocking). */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `compileToIR` | `(source: String, dagName: String): Either[List[CompileError], IRPipeline]` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `invalidateIO` | `(dagName: String): IO[Unit]` | /** Invalidate a specific cached compilation (IO-based). */ |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `invalidateAllIO` | `(): IO` | /** Invalidate all cached compilations (IO-based). */ |
| `cacheStats` | `(): CacheStats` | /** Get cache statistics (blocking, for backward compatibility). */ |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
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
| `stats` | `(): IO` | /** Get current cache statistics. |
| `size` | `(): IO` | /** Get current number of entries in the cache. */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `get` | `(dagName: String, sourceHash: String, registryHash: String): IO[Option[CompilationOutput]]` | /** Look up a cached compilation result. |
| `invalidate` | `(dagName: String): IO[Unit]` | /** Invalidate a specific cache entry by dagName. */ |
| `put` | `(dagName: String, sourceHash: String, registryHash: String, result: CompilationOutput): IO[Unit]` | /** Store a compilation result in the cache. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getBackend` | `(): CacheBackend` | /** Get the underlying cache backend. */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `invalidateAll` | `(): IO[Unit]` | /** Invalidate all cache entries. */ |

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
| `copy$default$5` | `(): OptimizationConfig` |  |
| `withCacheBackend` | `(backend: CacheBackend): LangCompilerBuilder` | /** Set a custom cache backend for the compilation cache. |
| `withModules` | `(newModules: Map[String, Uninitialized]): LangCompilerBuilder` | /** Register multiple modules for DagCompiler to access at compile time. This is separate from |
| `productArity` | `(): Int` |  |
| `_1` | `(): FunctionRegistry` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `copy$default$1` | `(): FunctionRegistry` |  |
| `withoutCaching` | `(): LangCompilerBuilder` | /** Disable compilation caching */ |
| `copy` | `(registry: FunctionRegistry, modules: Map[String, Uninitialized], cacheConfig: Option[Config], cacheBackend: Option[CacheBackend], optimizationConfig: OptimizationConfig): LangCompilerBuilder` |  |
| `withOptimization` | `(config: OptimizationConfig): LangCompilerBuilder` | /** Enable IR optimization with the given configuration */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): Option` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$2` | `(): Map` |  |
| `copy$default$4` | `(): Option` |  |
| `_5` | `(): OptimizationConfig` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `withFunction` | `(sig: FunctionSignature): LangCompilerBuilder` | /** Register a function signature for type checking */ |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `withCaching` | `(config: Config): LangCompilerBuilder` | /** Enable compilation caching with the given configuration */ |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `withoutOptimization` | `(): LangCompilerBuilder` | /** Disable IR optimization */ |
| `withModule` | `(languageName: String, module: Uninitialized, params: List[Tuple2[String, SemanticType]], returns: SemanticType): LangCompilerBuilder` | /** Register a module with its signature */ |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Map` |  |
| `productElementNames` | `(): Iterator` |  |
| `withCaching$default$1` | `(): Config` | /** Enable compilation caching with the given configuration */ |
| `_4` | `(): Option` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `copy$default$3` | `(): Option` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `withOptimization$default$1` | `(): OptimizationConfig` | /** Enable IR optimization with the given configuration */ |
| `build` | `(): LangCompiler` | /** Build the LangCompiler, optionally wrapped with caching */ |

### class LangCompilerImpl

/** Implementation of LangCompiler */

**Extends:** LangCompiler

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `compile` | `(source: String, dagName: String): Either[List[CompileError], CompilationOutput]` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `functionRegistry` | `(): FunctionRegistry` |  |
| `compileIO` | `(source: String, dagName: String): IO[Either[List[CompileError], CompilationOutput]]` | /** Async variant of compile that avoids blocking threads. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `compileToIR` | `(source: String, dagName: String): Either[List[CompileError], IRPipeline]` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
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
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `compile` | `(source: String, dagName: String): Either[List[CompileError], CompilationOutput]` | /** Compile a constellation-lang source to a CompilationOutput (LoadedPipeline + warnings). */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `functionRegistry` | `(): FunctionRegistry` | /** Get the function registry for namespace/function introspection */ |
| `compileIO` | `(source: String, dagName: String): IO[Either[List[CompileError], CompilationOutput]]` | /** Async variant of compile that avoids blocking threads. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `compileToIR` | `(source: String, dagName: String): Either[List[CompileError], IRPipeline]` | /** Compile to IR only (for visualization) */ |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

<!-- END GENERATED -->
