<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/core/src/main/scala/io/constellation -->
<!-- Hash: 479214b49771 -->
<!-- Generated: 2026-02-13T07:57:33.879973200Z -->

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
| `withConfig` | `(underlying: LangCompiler, config: Config): CachingLangCompiler` | /** Create a CachingLangCompiler with custom cache configuration */ |
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
| `withDefaults` | `(underlying: LangCompiler): CachingLangCompiler` | /** Create a CachingLangCompiler with default cache configuration */ |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CompilationCache$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `createWithBackend$default$2` | `(): Config` | /** Create a CompilationCache with a specific cache backend. |
| `createUnsafe$default$1` | `(): Config` | /** Create a CompilationCache synchronously (for use in non-IO contexts). Should only be used |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `create` | `(config: Config): IO[CompilationCache]` | /** Create a new CompilationCache with the default in-memory backend. */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `create$default$1` | `(): Config` | /** Create a new CompilationCache with the default in-memory backend. */ |
| `createUnsafeWithBackend$default$2` | `(): Config` | /** Create a CompilationCache synchronously with a specific cache backend. */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `createUnsafe` | `(config: Config): CompilationCache` | /** Create a CompilationCache synchronously (for use in non-IO contexts). Should only be used |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `createWithBackend` | `(backend: CacheBackend, config: Config): IO[CompilationCache]` | /** Create a CompilationCache with a specific cache backend. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `createUnsafeWithBackend` | `(backend: CacheBackend, config: Config): CompilationCache` | /** Create a CompilationCache synchronously with a specific cache backend. */ |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### LangCompiler$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `builder` | `(): LangCompilerBuilder` | /** Builder for constructing a LangCompiler */ |
| `empty` | `(): LangCompiler` | /** Create an empty LangCompiler (no registered functions or modules) */ |
| `apply` | `(registry: FunctionRegistry, modules: Map[String, Uninitialized], optimizationConfig: OptimizationConfig): LangCompiler` | /** Create a new LangCompiler with the given function registry and module map. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `apply$default$3` | `(): OptimizationConfig` | /** Create a new LangCompiler with the given function registry and module map. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
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
| `apply` | `(registry: FunctionRegistry, modules: Map[String, Uninitialized], cacheConfig: Option[Config], cacheBackend: Option[CacheBackend], optimizationConfig: OptimizationConfig): LangCompilerBuilder` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `unapply` | `(x$1: LangCompilerBuilder): LangCompilerBuilder` |  |
| `toString` | `(): String` |  |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
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
| `signatureFromModule` | `(languageName: String, module: Uninitialized, params: List[Tuple2[String, SemanticType]], returns: SemanticType): FunctionSignature` | /** Create a function signature from a module spec */ |
| `extractReturns` | `(module: Uninitialized): SemanticType` | /** Extract output type from a module spec (assumes single output named "out") */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `extractParams` | `(module: Uninitialized): List[Tuple2[String, SemanticType]]` | /** Extract input parameter types from a module spec */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `ctypeToSemanticType` | `(ctype: CType): SemanticType` | /** Convert CType to SemanticType for module integration */ |
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
| `compileIO` | `(source: String, dagName: String): IO[Either[List[CompileError], CompilationOutput]]` |  |
| `compileToIR` | `(source: String, dagName: String): Either[List[CompileError], IRPipeline]` |  |
| `functionRegistry` | `(): FunctionRegistry` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `cacheStats` | `(): CacheStats` | /** Get cache statistics (blocking, for backward compatibility). */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `compile` | `(source: String, dagName: String): Either[List[CompileError], CompilationOutput]` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `invalidateIO` | `(dagName: String): IO[Unit]` | /** Invalidate a specific cached compilation (IO-based). */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `cacheStatsIO` | `(): IO` | /** Get cache statistics (IO-based, preferred). */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `invalidateAll` | `(): Unit` | /** Invalidate all cached compilations (blocking). */ |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `invalidate` | `(dagName: String): Unit` | /** Invalidate a specific cached compilation (blocking). */ |
| `invalidateAllIO` | `(): IO` | /** Invalidate all cached compilations (IO-based). */ |
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
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `get` | `(dagName: String, sourceHash: String, registryHash: String): IO[Option[CompilationOutput]]` | /** Look up a cached compilation result. |
| `getBackend` | `(): CacheBackend` | /** Get the underlying cache backend. */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `put` | `(dagName: String, sourceHash: String, registryHash: String, result: CompilationOutput): IO[Unit]` | /** Store a compilation result in the cache. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `stats` | `(): IO` | /** Get current cache statistics. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `invalidateAll` | `(): IO[Unit]` | /** Invalidate all cache entries. */ |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `invalidate` | `(dagName: String): IO[Unit]` | /** Invalidate a specific cache entry by dagName. */ |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `size` | `(): IO` | /** Get current number of entries in the cache. */ |
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
| `productArity` | `(): Int` |  |
| `_1` | `(): FunctionRegistry` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `withModules` | `(newModules: Map[String, Uninitialized]): LangCompilerBuilder` | /** Register multiple modules for DagCompiler to access at compile time. This is separate from |
| `copy` | `(registry: FunctionRegistry, modules: Map[String, Uninitialized], cacheConfig: Option[Config], cacheBackend: Option[CacheBackend], optimizationConfig: OptimizationConfig): LangCompilerBuilder` |  |
| `build` | `(): LangCompiler` | /** Build the LangCompiler, optionally wrapped with caching */ |
| `_3` | `(): Option` |  |
| `productPrefix` | `(): String` |  |
| `_5` | `(): OptimizationConfig` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `withCaching$default$1` | `(): Config` | /** Enable compilation caching with the given configuration */ |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `withoutOptimization` | `(): LangCompilerBuilder` | /** Disable IR optimization */ |
| `withCaching` | `(config: Config): LangCompilerBuilder` | /** Enable compilation caching with the given configuration */ |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `withCacheBackend` | `(backend: CacheBackend): LangCompilerBuilder` | /** Set a custom cache backend for the compilation cache. |
| `productElementNames` | `(): Iterator` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Map` |  |
| `withFunction` | `(sig: FunctionSignature): LangCompilerBuilder` | /** Register a function signature for type checking */ |
| `copy$default$1` | `(): FunctionRegistry` |  |
| `withoutCaching` | `(): LangCompilerBuilder` | /** Disable compilation caching */ |
| `_4` | `(): Option` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `copy$default$4` | `(): Option` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `copy$default$2` | `(): Map` |  |
| `copy$default$3` | `(): Option` |  |
| `withOptimization` | `(config: OptimizationConfig): LangCompilerBuilder` | /** Enable IR optimization with the given configuration */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `withModule` | `(languageName: String, module: Uninitialized, params: List[Tuple2[String, SemanticType]], returns: SemanticType): LangCompilerBuilder` | /** Register a module with its signature */ |
| `copy$default$5` | `(): OptimizationConfig` |  |
| `withOptimization$default$1` | `(): OptimizationConfig` | /** Enable IR optimization with the given configuration */ |
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
| `compileIO` | `(source: String, dagName: String): IO[Either[List[CompileError], CompilationOutput]]` | /** Async variant of compile that avoids blocking threads. |
| `compileToIR` | `(source: String, dagName: String): Either[List[CompileError], IRPipeline]` |  |
| `functionRegistry` | `(): FunctionRegistry` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `compile` | `(source: String, dagName: String): Either[List[CompileError], CompilationOutput]` |  |
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

## Traits

### trait LangCompiler

/** Main interface for compiling constellation-lang programs */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `compileIO` | `(source: String, dagName: String): IO[Either[List[CompileError], CompilationOutput]]` | /** Async variant of compile that avoids blocking threads. |
| `compileToIR` | `(source: String, dagName: String): Either[List[CompileError], IRPipeline]` | /** Compile to IR only (for visualization) */ |
| `functionRegistry` | `(): FunctionRegistry` | /** Get the function registry for namespace/function introspection */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `compile` | `(source: String, dagName: String): Either[List[CompileError], CompilationOutput]` | /** Compile a constellation-lang source to a CompilationOutput (LoadedPipeline + warnings). */ |
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

<!-- END GENERATED -->
