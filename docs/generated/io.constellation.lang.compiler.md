<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/core/src/main/scala/io/constellation -->
<!-- Hash: b7fd67b9bb19 -->
<!-- Generated: 2026-02-06T18:48:02.349440700Z -->

# io.constellation.lang.compiler

## Objects

### CompilationOutput$

/** Result of compiling a constellation-lang pipeline.
  *
  * Wraps a [[LoadedPipeline]] (which contains the [[io.constellation.PipelineImage]] and synthetic
  * modules) together with any compiler warnings.
  *
  * @param pipeline
  *   The loaded pipeline ready for execution
  * @param warnings
  *   Compiler warnings (e.g., unused variables, deprecations)
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: CompilationOutput): CompilationOutput` |  |
| `apply` | `(pipeline: LoadedPipeline, warnings: List[CompileWarning]): CompilationOutput` |  |
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
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CompilerError$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `toException` | `(error: CompilerError): IllegalStateException` | /** Convert a CompilerError to an exception for backwards compatibility at API boundaries */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### DagCompileOutput$

/** Internal compilation output from DagCompiler. */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: DagCompileOutput): DagCompileOutput` |  |
| `apply` | `(dagSpec: DagSpec, syntheticModules: Map[UUID, Uninitialized], moduleOptions: Map[UUID, IRModuleCallOptions], warnings: List[CompileWarning]): DagCompileOutput` |  |
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
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### DagCompiler$

/** Compiles IR to Constellation DagSpec.
  *
  * ==Type Safety Note==
  *
  * The `asInstanceOf` casts in this file are **safe by construction**:
  *
  *   - '''Lambda casts''': HOF lambda arguments are always TypedExpression.Lambda because the IR
  *     generator only produces lambda arguments for higher-order functions
  *
  *   - '''Boolean casts''' (in evaluateLambdaBody): Logical operations (And, Or, Not) and
  *     conditionals receive Boolean operands because the type checker verifies all operand types
  *     during semantic analysis before IR generation
  *
  *   - '''Numeric casts''' (in evaluateLambdaBody): Arithmetic operations receive numeric operands
  *     because the type checker verifies operand types
  *
  *   - '''Condition result cast''' (in ConditionalModule): WhenNode conditions are type-checked to
  *     be Boolean during semantic analysis
  *
  * Runtime type validation can be enabled by setting `CONSTELLATION_DEBUG=true` for development and
  * debugging purposes. See [[io.constellation.DebugMode]].
  *
  * @see
  *   [[io.constellation.DebugMode]] for optional runtime type validation
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `compile` | `(program: IRPipeline, dagName: String, registeredModules: Map[String, Uninitialized]): Either[CompilerError, DagCompileOutput]` | /** Compile an IR pipeline to a DagSpec and synthetic modules */ |
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

### ErrorCategory$

/** Error category for grouping related errors */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `values` | `(): Array` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `fromOrdinal` | `(ordinal: Int): ErrorCategory` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `valueOf` | `($name: String): ErrorCategory` |  |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ErrorCodes$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `fromCompileError` | `(error: CompileError): ErrorCode` | /** Map a CompileError to its corresponding ErrorCode */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ErrorFormatter$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(source: String): ErrorFormatter` | /** Create a formatter for the given source code */ |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toMarkdown` | `(source: String, error: CompileError): String` | /** Quick format to markdown */ |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `formatError` | `(source: String, error: CompileError, context: SuggestionContext): FormattedError` | /** Format a single error with context */ |
| `formatError` | `(source: String, error: CompileError): FormattedError` | /** Format a single error with default context */ |
| `toPlainText` | `(source: String, error: CompileError): String` | /** Quick format to plain text */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `formatErrors` | `(source: String, errors: List[CompileError], context: SuggestionContext): List[FormattedError]` | /** Format multiple errors */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `formatErrors$default$3` | `(): SuggestionContext` | /** Format multiple errors */ |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### FormattedError$

/** Formatted error message with all contextual information.
  *
  * This is the structured output of error formatting, containing all the pieces needed to display a
  * helpful error message.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: FormattedError): FormattedError` |  |
| `apply` | `(code: String, title: String, category: ErrorCategory, location: String, snippet: String, explanation: String, suggestions: List[String], docUrl: Option[String], rawMessage: String): FormattedError` |  |
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
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### HigherOrderOp$

/** Higher-order operation types */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `fromOrdinal` | `(ordinal: Int): HigherOrderOp` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `values` | `(): Array` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `valueOf` | `($name: String): HigherOrderOp` |  |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### IRGenerator$

/** Generates IR from a typed AST */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `generate` | `(program: TypedPipeline): IRPipeline` | /** Generate IR from a typed pipeline */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
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
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### IRModuleCallOptions$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: IRModuleCallOptions): IRModuleCallOptions` |  |
| `apply` | `(retry: Option[Int], timeoutMs: Option[Long], delayMs: Option[Long], backoff: Option[BackoffStrategy], fallback: Option[UUID], cacheMs: Option[Long], cacheBackend: Option[String], throttleCount: Option[Int], throttlePerMs: Option[Long], concurrency: Option[Int], onError: Option[ErrorStrategy], lazyEval: Option[Boolean], priority: Option[Int]): IRModuleCallOptions` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### IRNode$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### IRPipeline$

/** The complete IR representation of a constellation-lang pipeline */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: IRPipeline): IRPipeline` |  |
| `apply` | `(nodes: Map[UUID, IRNode], inputs: List[UUID], declaredOutputs: List[String], variableBindings: Map[String, UUID]): IRPipeline` |  |
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
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ModuleOptionsExecutor$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `createWithCacheBackend$default$2` | `(): GlobalScheduler` | /** Create a module options executor with a pre-configured cache backend. |
| `withRegistries` | `(cacheRegistry: CacheRegistry, limiterRegistry: LimiterRegistry): ModuleOptionsExecutor` | /** Create a module options executor with custom registries (unbounded scheduler). */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `create` | `(): IO` | /** Create a new module options executor with default registries and unbounded scheduler. */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `createWithScheduler` | `(scheduler: GlobalScheduler): IO[ModuleOptionsExecutor]` | /** Create a module options executor with a custom scheduler. |
| `createWithCacheBackend` | `(cacheBackend: Option[CacheBackend], scheduler: GlobalScheduler): IO[ModuleOptionsExecutor]` | /** Create a module options executor with a pre-configured cache backend. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `withAll` | `(cacheRegistry: CacheRegistry, limiterRegistry: LimiterRegistry, scheduler: GlobalScheduler): ModuleOptionsExecutor` | /** Create a module options executor with custom registries and scheduler. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### SuggestionContext$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: SuggestionContext): SuggestionContext` |  |
| `apply` | `(definedVariables: List[String], definedTypes: List[String], availableFunctions: List[String], availableNamespaces: List[String], functionsByNamespace: Map[String, List[String]]): SuggestionContext` |  |
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
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### Suggestions$

/** Generates contextual suggestions for compiler errors.
  *
  * Provides "Did you mean?" suggestions using Levenshtein distance and context-aware suggestions
  * based on error type.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `findSimilar$default$4` | `(): Int` | /** Find strings similar to the target using Levenshtein distance. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `findSimilar$default$3` | `(): Int` | /** Find strings similar to the target using Levenshtein distance. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `forError` | `(error: CompileError, context: SuggestionContext): List[String]` | /** Generate contextual suggestions for a compile error. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `findSimilar` | `(target: String, candidates: List[String], maxDistance: Int, maxSuggestions: Int): List[String]` | /** Find strings similar to the target using Levenshtein distance. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `levenshteinDistance` | `(s1: String, s2: String): Int` | /** Calculate Levenshtein edit distance between two strings. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### TypedLambda$

/** Typed lambda representation for IR (independent of TypedExpression) Contains IR nodes
  * representing the lambda body that can be evaluated per element during collection operations.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: TypedLambda): TypedLambda` |  |
| `apply` | `(paramNames: List[String], paramTypes: List[SemanticType], bodyNodes: Map[UUID, IRNode], bodyOutputId: UUID, returnType: SemanticType): TypedLambda` |  |
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
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

## Classes

### case class CompilationOutput

/** Result of compiling a constellation-lang pipeline.
  *
  * Wraps a [[LoadedPipeline]] (which contains the [[io.constellation.PipelineImage]] and synthetic
  * modules) together with any compiler warnings.
  *
  * @param pipeline
  *   The loaded pipeline ready for execution
  * @param warnings
  *   Compiler warnings (e.g., unused variables, deprecations)
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `pipeline` | `LoadedPipeline` |  |
| `warnings` | `List[CompileWarning]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productPrefix` | `(): String` |  |
| `copy$default$1` | `(): LoadedPipeline` |  |
| `productArity` | `(): Int` |  |
| `_1` | `(): LoadedPipeline` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `copy` | `(pipeline: LoadedPipeline, warnings: List[CompileWarning]): CompilationOutput` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `canEqual` | `(that: Any): Boolean` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): List` |  |
| `productElementNames` | `(): Iterator` |  |
| `productElement` | `(n: Int): Any` |  |
| `copy$default$2` | `(): List` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class DagCompileOutput

/** Internal compilation output from DagCompiler. */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `dagSpec` | `DagSpec` |  |
| `syntheticModules` | `Map[UUID, Uninitialized]` |  |
| `moduleOptions` | `Map[UUID, IRModuleCallOptions]` |  |
| `warnings` | `List[CompileWarning]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `copy$default$1` | `(): DagSpec` |  |
| `_1` | `(): DagSpec` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `copy` | `(dagSpec: DagSpec, syntheticModules: Map[UUID, Uninitialized], moduleOptions: Map[UUID, IRModuleCallOptions], warnings: List[CompileWarning]): DagCompileOutput` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): Map` |  |
| `productPrefix` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$4` | `(): List` |  |
| `productArity` | `(): Int` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `copy$default$3` | `(): Map` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Map` |  |
| `productElementNames` | `(): Iterator` |  |
| `_4` | `(): List` |  |
| `productElement` | `(n: Int): Any` |  |
| `copy$default$2` | `(): Map` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |

### class ErrorFormatter

/** Formats compile errors into rich, helpful error messages.
  *
  * Features:
  *   - Error codes with explanations
  *   - Source code snippets with caret markers
  *   - "Did you mean?" suggestions
  *   - Documentation links
  *
  * @param source
  *   The source code being compiled
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `formatAll$default$2` | `(): SuggestionContext` | /** Format multiple errors. |
| `format` | `(error: CompileError, context: SuggestionContext): FormattedError` | /** Format a compile error with full context. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `format$default$2` | `(): SuggestionContext` | /** Format a compile error with full context. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `formatAll` | `(errors: List[CompileError], context: SuggestionContext): List[FormattedError]` | /** Format multiple errors. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class FormattedError

/** Formatted error message with all contextual information.
  *
  * This is the structured output of error formatting, containing all the pieces needed to display a
  * helpful error message.
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `code` | `String` |  |
| `title` | `String` |  |
| `category` | `ErrorCategory` |  |
| `location` | `String` |  |
| `snippet` | `String` |  |
| `explanation` | `String` |  |
| `suggestions` | `List[String]` |  |
| `docUrl` | `Option[String]` |  |
| `rawMessage` | `String` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `_9` | `(): String` |  |
| `copy$default$1` | `(): String` |  |
| `_6` | `(): String` |  |
| `_1` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `copy` | `(code: String, title: String, category: ErrorCategory, location: String, snippet: String, explanation: String, suggestions: List[String], docUrl: Option[String], rawMessage: String): FormattedError` |  |
| `_8` | `(): Option` |  |
| `_3` | `(): ErrorCategory` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$5` | `(): String` |  |
| `_5` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$4` | `(): String` |  |
| `_7` | `(): List` |  |
| `productArity` | `(): Int` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `copy$default$3` | `(): ErrorCategory` |  |
| `copy$default$9` | `(): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$8` | `(): Option` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `toOneLine` | `(): String` | /** Format as a concise single-line message */ |
| `toMarkdown` | `(): String` | /** Format as markdown for IDE hover/tooltips */ |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): String` |  |
| `productElementNames` | `(): Iterator` |  |
| `_4` | `(): String` |  |
| `copy$default$6` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `copy$default$2` | `(): String` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `toPlainText` | `(): String` | /** Format as plain text for terminal output */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `copy$default$7` | `(): List` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class IRModuleCallOptions

/** Module call options at IR level.
  *
  * These are the resolved options from the AST, with fallback expressions compiled into IR nodes.
  * Options like retry, timeout, etc. are passed through to the runtime for execution.
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `retry` | `Option[Int]` |  |
| `timeoutMs` | `Option[Long]` |  |
| `delayMs` | `Option[Long]` |  |
| `backoff` | `Option[BackoffStrategy]` |  |
| `fallback` | `Option[UUID]` |  |
| `cacheMs` | `Option[Long]` |  |
| `cacheBackend` | `Option[String]` |  |
| `throttleCount` | `Option[Int]` |  |
| `throttlePerMs` | `Option[Long]` |  |
| `concurrency` | `Option[Int]` |  |
| `onError` | `Option[ErrorStrategy]` |  |
| `lazyEval` | `Option[Boolean]` |  |
| `priority` | `Option[Int]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `_9` | `(): Option` |  |
| `copy$default$5` | `(): Option` |  |
| `copy$default$1` | `(): Option` |  |
| `copy$default$10` | `(): Option` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): Option` |  |
| `productPrefix` | `(): String` |  |
| `_11` | `(): Option` |  |
| `_5` | `(): Option` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$4` | `(): Option` |  |
| `_7` | `(): Option` |  |
| `productArity` | `(): Int` |  |
| `_13` | `(): Option` |  |
| `_6` | `(): Option` |  |
| `_1` | `(): Option` |  |
| `copy$default$11` | `(): Option` |  |
| `isEmpty` | `(): Boolean` |  |
| `toString` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `productElementName` | `(n: Int): String` |  |
| `copy$default$3` | `(): Option` |  |
| `copy$default$9` | `(): Option` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `_12` | `(): Option` |  |
| `copy$default$8` | `(): Option` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `copy$default$12` | `(): Option` |  |
| `toModuleCallOptions` | `(): ModuleCallOptions` | /** Convert to runtime-level [[ModuleCallOptions]] (drops IR-specific `fallback`). */ |
| `copy$default$13` | `(): Option` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Option` |  |
| `productElementNames` | `(): Iterator` |  |
| `_4` | `(): Option` |  |
| `copy$default$6` | `(): Option` |  |
| `productElement` | `(n: Int): Any` |  |
| `copy$default$2` | `(): Option` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `copy` | `(retry: Option[Int], timeoutMs: Option[Long], delayMs: Option[Long], backoff: Option[BackoffStrategy], fallback: Option[UUID], cacheMs: Option[Long], cacheBackend: Option[String], throttleCount: Option[Int], throttlePerMs: Option[Long], concurrency: Option[Int], onError: Option[ErrorStrategy], lazyEval: Option[Boolean], priority: Option[Int]): IRModuleCallOptions` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `_10` | `(): Option` |  |
| `copy$default$7` | `(): Option` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `_8` | `(): Option` |  |

### case class IRPipeline

/** The complete IR representation of a constellation-lang pipeline */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `nodes` | `Map[UUID, IRNode]` |  |
| `inputs` | `List[UUID]` |  |
| `declaredOutputs` | `List[String]` |  |
| `variableBindings` | `Map[String, UUID]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `_1` | `(): Map` |  |
| `maxParallelism` | `(): Int` | /** Get the maximum parallelism potential (size of largest layer) */ |
| `copy` | `(nodes: Map[UUID, IRNode], inputs: List[UUID], declaredOutputs: List[String], variableBindings: Map[String, UUID]): IRPipeline` |  |
| `criticalPathLength` | `(): Int` | /** Get the critical path length (number of layers) */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): List` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$1` | `(): Map` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$4` | `(): Map` |  |
| `productArity` | `(): Int` |  |
| `toString` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `topologicalOrder` | `(): List` | /** Topologically sort nodes for execution order */ |
| `productElementName` | `(n: Int): String` |  |
| `copy$default$3` | `(): List` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `topologicalLayers` | `(): List` | /** Compute topological layers for parallel processing. |
| `dependencies` | `(nodeId: UUID): Set[UUID]` | /** Get all dependencies for a given node */ |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): List` |  |
| `productElementNames` | `(): Iterator` |  |
| `_4` | `(): Map` |  |
| `productElement` | `(n: Int): Any` |  |
| `copy$default$2` | `(): List` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class ModuleOptionsExecutor

/** Executes module operations with options from the DAG compiler.
  *
  * This is the integration layer between:
  *   - `IRModuleCallOptions` from the compiler
  *   - Runtime execution infrastructure (ModuleExecutor, CacheRegistry, LimiterRegistry,
  *     GlobalScheduler, etc.)
  *
  * ==Usage==
  *
  * {{{
  * val executor = ModuleOptionsExecutor.create.unsafeRunSync()
  *
  * val result = executor.executeWithOptions(
  *   operation = module.run(runtime),
  *   moduleId = moduleId,
  *   moduleName = "MyModule",
  *   options = irOptions,
  *   outputType = CType.CString,
  *   getFallbackValue = Some(() => IO.pure("default"))
  * )
  * }}}
  *
  * ==Priority Scheduling==
  *
  * When a GlobalScheduler is configured (via `withScheduler` or environment config), tasks with
  * `priority` options are submitted to the scheduler for global ordering. High-priority tasks from
  * any execution will run before low-priority tasks.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `executeWithOptions$default$7` | `[A](): Option` | /** Execute an operation with the specified module call options. |
| `getLimiterRegistry` | `(): LimiterRegistry` | /** Get the limiter registry for external access. */ |
| `getCacheRegistry` | `(): CacheRegistry` | /** Get the cache registry for external access. */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `executeWithOptions` | `[A](operation: IO[A], moduleId: UUID, moduleName: String, options: IRModuleCallOptions, outputType: CType, inputs: Map[String, CValue], getFallbackValue: Option[Function0[IO[Any]]]): IO[Any]` | /** Execute an operation with the specified module call options. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `executeWithOptions$default$6` | `[A](): Map` | /** Execute an operation with the specified module call options. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `getScheduler` | `(): GlobalScheduler` | /** Get the scheduler for external access. */ |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class SuggestionContext

/** Context for generating suggestions.
  *
  * Contains information about available symbols in the current compilation.
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `definedVariables` | `List[String]` |  |
| `definedTypes` | `List[String]` |  |
| `availableFunctions` | `List[String]` |  |
| `availableNamespaces` | `List[String]` |  |
| `functionsByNamespace` | `Map[String, List[String]]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `copy$default$5` | `(): Map` |  |
| `copy$default$1` | `(): List` |  |
| `_1` | `(): List` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `copy` | `(definedVariables: List[String], definedTypes: List[String], availableFunctions: List[String], availableNamespaces: List[String], functionsByNamespace: Map[String, List[String]]): SuggestionContext` |  |
| `_3` | `(): List` |  |
| `productPrefix` | `(): String` |  |
| `_5` | `(): Map` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$4` | `(): List` |  |
| `productArity` | `(): Int` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `copy$default$3` | `(): List` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): List` |  |
| `productElementNames` | `(): Iterator` |  |
| `_4` | `(): List` |  |
| `productElement` | `(n: Int): Any` |  |
| `copy$default$2` | `(): List` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class TypedLambda

/** Typed lambda representation for IR (independent of TypedExpression) Contains IR nodes
  * representing the lambda body that can be evaluated per element during collection operations.
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `paramNames` | `List[String]` |  |
| `paramTypes` | `List[SemanticType]` |  |
| `bodyNodes` | `Map[UUID, IRNode]` |  |
| `bodyOutputId` | `UUID` |  |
| `returnType` | `SemanticType` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `copy$default$5` | `(): SemanticType` |  |
| `copy$default$1` | `(): List` |  |
| `_1` | `(): List` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `copy` | `(paramNames: List[String], paramTypes: List[SemanticType], bodyNodes: Map[UUID, IRNode], bodyOutputId: UUID, returnType: SemanticType): TypedLambda` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): Map` |  |
| `productPrefix` | `(): String` |  |
| `_5` | `(): SemanticType` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$4` | `(): UUID` |  |
| `productArity` | `(): Int` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `copy$default$3` | `(): Map` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): List` |  |
| `productElementNames` | `(): Iterator` |  |
| `productElement` | `(n: Int): Any` |  |
| `copy$default$2` | `(): List` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `_4` | `(): UUID` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

## Traits

### trait CompilerError

/** Errors that can occur during DAG compilation.
  *
  * These are compile-time errors (not runtime), representing invalid IR structures or unsupported
  * operations discovered during compilation.
  */

**Extends:** Object

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
| `message` | `(): String` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait ErrorCode

/** Error code with structured information for improved error messages.
  *
  * Each error code provides:
  *   - A unique identifier (e.g., E001)
  *   - A short title describing the error
  *   - A detailed explanation of why this error occurs
  *   - An optional documentation path for more information
  */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `category` | `(): ErrorCategory` |  |
| `docUrl` | `(): Option` | /** Full documentation URL */ |
| `code` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `title` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `explanation` | `(): String` |  |
| `docPath` | `(): Option` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait IRNode

/** Intermediate representation for constellation-lang pipelines.
  *
  * The IR represents a data-flow graph where:
  *   - Each node produces a value with a known type
  *   - Dependencies between nodes are explicit via UUID references
  *   - This IR is designed to be easily compiled to Constellation DagSpec
  */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `debugSpan` | `(): Option` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `outputType` | `(): SemanticType` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `id` | `(): UUID` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

## Enums

### enum ErrorCategory

/** Error category for grouping related errors */

**Cases:**

| Case | Parameters |
|------|------------|
| `Syntax` |  |
| `Type` |  |
| `Reference` |  |
| `Semantic` |  |
| `Internal` |  |

### enum HigherOrderOp

/** Higher-order operation types */

**Cases:**

| Case | Parameters |
|------|------------|
| `Filter` |  |
| `Map` |  |
| `All` |  |
| `Any` |  |
| `SortBy` |  |

<!-- END GENERATED -->
