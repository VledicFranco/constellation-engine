<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/core/src/main/scala/io/constellation -->
<!-- Hash: 509e1b331609 -->
<!-- Generated: 2026-03-10T05:05:52.067730200Z -->

# io.constellation

## Objects

### AdaptiveJsonConverter$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(lazyThreshold: Int, streamingThreshold: Int): AdaptiveJsonConverter` | /** Create with custom thresholds */ |
| `apply` | `(): AdaptiveJsonConverter` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `convert` | `(json: Json, expectedType: CType, sizeHint: Int): Either[String, CValue]` | /** Convert with explicit size hint */ |
| `convert` | `(json: Json, expectedType: CType): Either[String, CValue]` | /** Convert using default instance */ |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CType$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
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

### CTypeTag$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `mapTag` | `[A, B](keyInjector: CTypeTag[A], valueInjector: CTypeTag[B]): CTypeTag[Map[Any, Any]]` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `vectorTag` | `[A](injector: CTypeTag[A]): CTypeTag[Vector[Any]]` |  |
| `inline$of` | `[A](cType0: CType): CTypeTag[Any]` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `optionTag` | `[A](innerTag: CTypeTag[A]): CTypeTag[Option[Any]]` |  |
| `listTag` | `[A](injector: CTypeTag[A]): CTypeTag[List[Any]]` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productTag` | `[T](m: ProductOf[T]): CTypeTag[Product]` | /** Derive CTypeTag for a case class using Scala 3 Mirrors. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CValue$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CValueExtractor$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `vectorExtractor` | `[A](extractor: CValueExtractor[A]): CValueExtractor[Vector[Any]]` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `mapExtractor` | `[A, B](keyExtractor: CValueExtractor[A], valueExtractor: CValueExtractor[B]): CValueExtractor[Map[Any, Any]]` |  |
| `listExtractor` | `[A](extractor: CValueExtractor[A]): CValueExtractor[List[Any]]` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `optionExtractor` | `[A](extractor: CValueExtractor[A]): CValueExtractor[Option[Any]]` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CValueInjector$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `mapInjector` | `[A, B](keyInjector: CValueInjector[A], valueInjector: CValueInjector[B], keyTypeTag: CTypeTag[A], valueTypeTag: CTypeTag[B]): CValueInjector[Map[Any, Any]]` |  |
| `optionInjector` | `[A](injector: CValueInjector[A], typeTag: CTypeTag[A]): CValueInjector[Option[Any]]` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `vectorInjector` | `[A](injector: CValueInjector[A], typeTag: CTypeTag[A]): CValueInjector[Vector[Any]]` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `listInjector` | `[A](injector: CValueInjector[A], typeTag: CTypeTag[A]): CValueInjector[List[Any]]` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CirceJsonSuspensionCodec$

/** Circe JSON implementation of [[SuspensionCodec]].
  *
  * Serializes [[SuspendedExecution]] to/from JSON bytes using the codecs defined in
  * [[CustomJsonCodecs]].
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `decode` | `(bytes: Array[Byte]): Either[CodecError, SuspendedExecution]` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `evalEncoder` | `[A](encoder: Encoder[A]): Encoder[Eval[Any]]` |  |
| `evalDecoder` | `[A](decoder: Decoder[A]): Decoder[Eval[Any]]` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `uuidMapDecoder` | `[A](decoder: Decoder[A]): Decoder[Map[UUID, Any]]` |  |
| `uuidMapEncoder` | `[A](encoder: Encoder[A]): Encoder[Map[UUID, Any]]` |  |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `encode` | `(suspended: SuspendedExecution): Either[CodecError, Array[Byte]]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CodecError$

/** Error during suspension encoding/decoding. */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: CodecError): CodecError` |  |
| `apply` | `(message: String, cause: Option[Throwable]): CodecError` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ComponentMetadata$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: ComponentMetadata): ComponentMetadata` |  |
| `apply` | `(name: String, description: String, tags: List[String], majorVersion: Int, minorVersion: Int): ComponentMetadata` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `empty` | `(name: String): ComponentMetadata` |  |
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
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ConstellationError$

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
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ContentHash$

/** Utilities for computing deterministic content hashes of DAG specifications.
  *
  * Used by [[PipelineImage]] to derive structural and syntactic hashes that enable deduplication,
  * caching, and change detection.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `computeSHA256` | `(bytes: Array[Byte]): String` | /** Compute a SHA-256 hex digest of the given bytes. */ |
| `computeStructuralHash` | `(dag: DagSpec): String` | /** Compute a structural hash of a [[DagSpec]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `canonicalizeDagSpec` | `(dag: DagSpec): String` | /** Produce a deterministic canonical string representation of a [[DagSpec]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ConversionStrategy$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CycleDetectedError$

/** Error when a cycle is detected in the DAG.
  *
  * @param nodeIds
  *   The nodes involved in the cycle (if available)
  * @param context
  *   Additional debugging context
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: CycleDetectedError): CycleDetectedError` |  |
| `apply` | `(nodeIds: List[UUID], context: Map[String, String]): CycleDetectedError` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### DagChange$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### DagSpec$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: DagSpec): DagSpec` |  |
| `apply` | `(metadata: ComponentMetadata, modules: Map[UUID, ModuleNodeSpec], data: Map[UUID, DataNodeSpec], inEdges: Set[Tuple2[UUID, UUID]], outEdges: Set[Tuple2[UUID, UUID]], declaredOutputs: List[String], outputBindings: Map[String, UUID]): DagSpec` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `empty` | `(name: String): DagSpec` |  |
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
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### DataNodeSpec$

/** Specification for a data node in a DAG.
  *
  * @param name
  *   The name of the data node
  * @param nicknames
  *   Map from module UUID to the parameter name used by that module
  * @param cType
  *   The type of data this node holds
  * @param inlineTransform
  *   Optional inline transform to compute this node's value from inputs. When present, this data
  *   node computes its value by applying the transform to the values of its input data nodes
  *   (specified in transformInputs). This eliminates the need for a synthetic module node.
  * @param transformInputs
  *   Map from input name (as expected by the transform) to source data node UUID. Only used when
  *   inlineTransform is defined. For example, a MergeTransform expects inputs named "left" and
  *   "right".
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: DataNodeSpec): DataNodeSpec` |  |
| `apply` | `(name: String, nicknames: Map[UUID, String], cType: CType, inlineTransform: Option[InlineTransform], transformInputs: Map[String, UUID]): DataNodeSpec` |  |
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

### DataNotFoundError$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: DataNotFoundError): DataNotFoundError` |  |
| `apply` | `(dataId: UUID, dataType: String, context: Map[String, String]): DataNotFoundError` |  |
| `data` | `(dataId: UUID, ctx: Map[String, String]): DataNotFoundError` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `deferred$default$2` | `(): Map` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `deferred` | `(dataId: UUID, ctx: Map[String, String]): DataNotFoundError` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `data$default$2` | `(): Map` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### DataSignature$

/** Describes the outcome of executing a pipeline.
  *
  * Contains the execution status, all computed values, declared outputs, information about missing
  * inputs (for suspended executions), and optional metadata. When the status is
  * [[PipelineStatus.Suspended]] or [[PipelineStatus.Failed]], a [[SuspendedExecution]] snapshot is
  * included so the execution can be resumed.
  *
  * @param executionId
  *   Unique ID for this execution run
  * @param structuralHash
  *   Structural hash of the pipeline that was executed
  * @param resumptionCount
  *   Number of times this execution has been resumed (0 for first run)
  * @param status
  *   Terminal status of the execution
  * @param inputs
  *   Input values that were provided
  * @param computedNodes
  *   All computed data node values (keyed by variable name)
  * @param outputs
  *   Subset of computedNodes matching declared outputs
  * @param missingInputs
  *   Input variable names that were expected but not provided
  * @param pendingOutputs
  *   Declared output names that were not computed
  * @param suspendedState
  *   Snapshot for resumption (present when Suspended or Failed)
  * @param metadata
  *   Optional timing/provenance metadata
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: DataSignature): DataSignature` |  |
| `apply` | `(executionId: UUID, structuralHash: String, resumptionCount: Int, status: PipelineStatus, inputs: Map[String, CValue], computedNodes: Map[String, CValue], outputs: Map[String, CValue], missingInputs: List[String], pendingOutputs: List[String], suspendedState: Option[SuspendedExecution], metadata: SignatureMetadata): DataSignature` |  |
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

### DebugLevel$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `values` | `(): Array` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `fromOrdinal` | `(ordinal: Int): DebugLevel` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `fromString` | `(s: String): Option[DebugLevel]` | /** Parse debug level from environment variable string. */ |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `valueOf` | `($name: String): DebugLevel` |  |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### DebugMode$

/** Debug Mode Utilities for Constellation Engine
  *
  * Provides runtime type validation with configurable levels. By default, runs in **ErrorsOnly**
  * mode to catch type safety violations in production while minimizing overhead.
  *
  * ==Configuration Levels==
  *
  * Set via `CONSTELLATION_DEBUG` environment variable:
  *
  * | Value                  | Level      | Behavior                              |
  * |:-----------------------|:-----------|:--------------------------------------|
  * | `off`, `false`, `0`    | Off        | No validation (zero overhead)         |
  * | `errors`, `errorsonly` | ErrorsOnly | Log violations, don't throw (default) |
  * | `full`, `true`, `1`    | Full       | Throw on violations (development)     |
  *
  * ==Default Behavior (ErrorsOnly)==
  *
  * When `CONSTELLATION_DEBUG` is not set:
  *   - Type violations are logged to stderr
  *   - No exceptions are thrown
  *   - Minimal performance impact (<1%)
  *   - Silent data corruption is prevented
  *
  * {{{
  * # No env var set → ErrorsOnly (default)
  * sbt run
  *
  * # Explicit ErrorsOnly mode
  * export CONSTELLATION_DEBUG=errors
  * sbt run
  *
  * # Full validation (development/testing)
  * export CONSTELLATION_DEBUG=full
  * sbt run
  *
  * # Disable all validation (production optimization)
  * export CONSTELLATION_DEBUG=off
  * sbt run
  * }}}
  *
  * ==Usage==
  *
  * Use `safeCast` instead of `asInstanceOf` for casts that may fail:
  *
  * {{{
  * import io.constellation.DebugMode.safeCast
  *
  * // Instead of: value.asInstanceOf[MyType]
  * val result = safeCast[MyType](value, "context description")
  * }}}
  *
  * In **ErrorsOnly** mode (default):
  *   - Invalid casts are logged to stderr
  *   - The cast proceeds anyway (fail-soft)
  *   - Allows detection without breaking production
  *
  * In **Full** mode:
  *   - Invalid casts throw `TypeMismatchError`
  *   - Fail-fast for development/testing
  *
  * In **Off** mode:
  *   - Direct `asInstanceOf` call (zero overhead)
  *   - No validation
  *
  * ==Safe Casts vs Boundary Validation==
  *
  * Most `asInstanceOf` casts in Constellation are **safe by construction**:
  *
  *   - **DagCompiler casts**: Types are guaranteed by the IR generator and type checker. The
  *     compiler only generates casts when it has proven the types match.
  *
  *   - **InlineTransform casts**: Types come from the compiled DAG specification. The transform
  *     functions receive exactly the types specified in the DAG.
  *
  *   - **Runtime casts**: Types are guaranteed by module specifications and the type checking phase
  *     that runs before execution.
  *
  *   - **RawValue internal casts**: Implementation details where types are known from the
  *     immediately preceding pattern match or construction.
  *
  * **Boundary validation** (in `ExecutionHelper` and `JsonCValueConverter`) is where external data
  * enters the system. These locations perform explicit validation and error handling rather than
  * using casts.
  *
  * ==Performance==
  *
  * | Level      | Overhead | When to Use                         |
  * |:-----------|:---------|:------------------------------------|
  * | Off        | 0%       | Production (after thorough testing) |
  * | ErrorsOnly | <1%      | Production (default, recommended)   |
  * | Full       | ~2-5%    | Development, testing, debugging     |
  *
  * @see
  *   [[io.constellation.TypeMismatchError]] for the error thrown in Full mode
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `safeCastNamed` | `[T](value: Any, expectedTypeName: String, context: String, tag: ClassTag[T]): Any` | /** Performs a type cast with optional runtime validation, using a custom expected type name. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `safeCast` | `[T](value: Any, context: String, tag: ClassTag[T]): Any` | /** Performs a type cast with configurable runtime validation. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `debugAssert` | `(condition: Boolean, message: String, context: String): Unit` | /** Validates a condition in debug mode only. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `debugLog` | `(message: String): Unit` | /** Logs a message in debug mode only. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ExecutionError$

/** Describes a single error that occurred during module execution. */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: ExecutionError): ExecutionError` |  |
| `apply` | `(nodeName: String, moduleName: String, message: String, cause: Option[Throwable], retriesAttempted: Int): ExecutionError` |  |
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

### ExecutionOptions$

/** Controls which optional metadata is collected during execution.
  *
  * All flags default to false to keep the common path lightweight.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: ExecutionOptions): ExecutionOptions` |  |
| `apply` | `(includeTimings: Boolean, includeProvenance: Boolean, includeBlockedGraph: Boolean, includeResolutionSources: Boolean): ExecutionOptions` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ExecutionTrace$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: ExecutionTrace): ExecutionTrace` |  |
| `apply` | `(executionId: String, dagName: String, startTime: Long, endTime: Option[Long], nodeResults: Map[String, NodeExecutionResult]): ExecutionTrace` |  |
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
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ExecutionTracker$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `create` | `(config: Config): IO[ExecutionTracker[IO]]` | /** Create a new ExecutionTracker with custom configuration. */ |
| `create` | `(): IO` | /** Create a new ExecutionTracker with default configuration. */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `fromRuntimeState` | `(executionId: String, dagName: String, runtimeState: State, startTime: Long): ExecutionTrace` | /** Helper to convert Runtime.State to execution trace results. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### InlineTransform$

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
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### InputAlreadyProvidedError$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: InputAlreadyProvidedError): InputAlreadyProvidedError` |  |
| `apply` | `(name: String): InputAlreadyProvidedError` |  |
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

### InputTypeMismatchError$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: InputTypeMismatchError): InputTypeMismatchError` |  |
| `apply` | `(name: String, expected: CType, actual: CType): InputTypeMismatchError` |  |
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

### InputValidationError$

/** Error when input validation fails.
  *
  * @param inputName
  *   The name of the input that failed validation
  * @param reason
  *   Why validation failed
  * @param context
  *   Additional debugging context
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: InputValidationError): InputValidationError` |  |
| `apply` | `(inputName: String, reason: String, context: Map[String, String]): InputValidationError` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### JsonCValueConverter$

/** Bidirectional converter between JSON and CValue/RawValue types.
  *
  * JSON → CValue/RawValue requires type information (CType) to guide the conversion. CValue → JSON
  * is straightforward and doesn't require type information.
  *
  * ==Conversion Strategies==
  *
  *   - **Eager** (`jsonToCValue`): Full recursive conversion. Best for small payloads (<10KB).
  *   - **Adaptive** (`convertAdaptive`): Automatically chooses strategy based on payload size. Uses
  *     streaming for large payloads, lazy for medium, eager for small.
  *   - **Streaming** (`StreamingJsonConverter`): Jackson-based streaming for very large payloads.
  *
  * ==Memory-Efficient Path==
  *
  * For large data (especially numeric arrays), use the RawValue methods:
  *   - `jsonToRawValue`: Direct JSON to RawValue (most efficient)
  *   - `rawValueToJson`: Direct RawValue to JSON using type info
  *
  * These avoid allocating intermediate CValue wrappers, providing ~6x memory reduction for large
  * numeric arrays.
  *
  * ==Performance Guidelines==
  *
  * | Payload Size   | Recommended Method                            |
  * |:---------------|:----------------------------------------------|
  * | < 10KB         | `jsonToCValue` (eager)                        |
  * | 10-100KB       | `convertAdaptive`                             |
  * | > 100KB        | `convertAdaptive` or `StreamingJsonConverter` |
  * | Numeric arrays | `jsonToRawValue`                              |
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `jsonToCValue` | `(json: Json, expectedType: CType, path: String): Either[String, CValue]` | /** Convert JSON to CValue using the expected type as a guide. |
| `jsonToRawValue$default$3` | `(): String` | /** Convert JSON directly to RawValue using the expected type as a guide. |
| `cValueToJson` | `(cValue: CValue): Json` | /** Convert CValue to JSON. |
| `convertAdaptive` | `(json: Json, expectedType: CType, sizeHint: Int): Either[String, CValue]` | /** Adaptive conversion with explicit size hint. |
| `convertAdaptive` | `(json: Json, expectedType: CType): Either[String, CValue]` | /** Adaptive conversion that automatically chooses the best strategy based on payload size. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `jsonToRawValue` | `(json: Json, expectedType: CType, path: String): Either[String, RawValue]` | /** Convert JSON directly to RawValue using the expected type as a guide. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `jsonToCValue$default$3` | `(): String` | /** Convert JSON to CValue using the expected type as a guide. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `rawValueToJson` | `(raw: RawValue, cType: CType): Json` | /** Convert RawValue to JSON using type information. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### LazyJsonValue$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(json: Json, expectedType: CType): LazyJsonValue` |  |
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

### LazyListValue$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(jsonArray: Vector[Json], elementType: CType): LazyListValue` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `fromJson` | `(json: Json, elementType: CType): Either[String, LazyListValue]` | /** Create from Json, extracting the array */ |
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

### LazyProductValue$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(jsonObj: Map[String, Json], fieldTypes: Map[String, CType]): LazyProductValue` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `fromJson` | `(json: Json, fieldTypes: Map[String, CType]): Either[String, LazyProductValue]` | /** Create from Json, extracting the object */ |
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

### LoadedPipeline$

/** A pipeline that is ready to execute.
  *
  * Combines an immutable [[PipelineImage]] with the runtime module instances (synthetic modules
  * like branch modules) needed for execution.
  *
  * @param image
  *   The immutable pipeline snapshot
  * @param syntheticModules
  *   Module implementations keyed by node UUID
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: LoadedPipeline): LoadedPipeline` |  |
| `apply` | `(image: PipelineImage, syntheticModules: Map[UUID, Uninitialized]): LoadedPipeline` |  |
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

### MetadataBuilder$

/** Builds [[SignatureMetadata]] from execution state, controlled by [[ExecutionOptions]] flags.
  *
  * Shared by both [[io.constellation.impl.ConstellationImpl]] and [[SuspendableExecution]] to avoid
  * duplicating non-trivial metadata computation logic.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `build` | `(state: State, dagSpec: DagSpec, options: ExecutionOptions, startedAt: Instant, completedAt: Instant, inputNodeNames: Set[String], resolvedNodeNames: Set[String]): SignatureMetadata` | /** Build metadata from a completed or suspended execution. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `build$default$7` | `(): Set` | /** Build metadata from a completed or suspended execution. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### Module$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `uninitialized` | `[I, O](partialSpec: ModuleNodeSpec, run0: Function1[I, IO[Produces[O]]], mi: ProductOf[I], mo: ProductOf[O]): Uninitialized` |  |
| `awaitOnInputs` | `[T](namespace: Namespace, runtime: Runtime, m: ProductOf[T]): IO[Product]` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `getFieldTypes` | `[T](m: ProductOf[T]): List[CType]` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `registerData` | `[T](namespace: Namespace, m: ProductOf[T]): IO[MutableDataTable]` |  |
| `buildDataNodeSpec` | `[T](m: ProductOf[T]): Map[String, CType]` |  |
| `provideOnOutputs` | `[T](namespace: Namespace, runtime: Runtime, outputs: T, m: ProductOf[T]): IO[Unit]` |  |
| `getInjectors` | `[T](m: ProductOf[T]): List[CValueInjector[Any]]` |  |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `inline$logger` | `(): Logger` |  |
| `getFieldNames` | `[T](m: ProductOf[T]): List[String]` |  |

### ModuleBuilder$

/** ModuleBuilder - Fluent API for defining Constellation modules.
  *
  * ModuleBuilder provides a type-safe, declarative way to create processing modules that can be
  * composed into DAG pipelines. Each module has:
  *   - Metadata (name, description, version, tags)
  *   - Input/output type signatures (derived from case classes)
  *   - Implementation (pure function or IO-based)
  *
  * ==Basic Usage==
  *
  * {{{
  * // 1. Define input/output case classes
  * case class TextInput(text: String)
  * case class TextOutput(result: String)
  *
  * // 2. Build the module
  * val uppercase: Module.Uninitialized = ModuleBuilder
  *   .metadata(
  *     name = "Uppercase",
  *     description = "Converts text to uppercase",
  *     majorVersion = 1,
  *     minorVersion = 0
  *   )
  *   .tags("text", "transform")
  *   .implementationPure[TextInput, TextOutput] { input =>
  *     TextOutput(input.text.toUpperCase)
  *   }
  *   .build
  * }}}
  *
  * ==Implementation Types==
  *
  * '''Pure implementations''' - For side-effect-free transformations:
  * {{{
  * .implementationPure[Input, Output] { input => Output(...) }
  * }}}
  *
  * '''IO implementations''' - For effectful operations (HTTP, DB, etc.):
  * {{{
  * .implementation[Input, Output] { input =>
  *   IO {
  *     // perform side effects
  *     Output(...)
  *   }
  * }
  * }}}
  *
  * ==Field Naming Rules==
  *
  * Case class field names map directly to variable names in constellation-lang:
  * {{{
  * case class MyInput(text: String, count: Int)
  *
  * // In constellation-lang:
  * result = MyModule(text, count)  // field names must match exactly
  * }}}
  *
  * ==Module States==
  *
  *   - `Module.Uninitialized` - Module template, not yet registered
  *   - `Module.Initialized` - Module with runtime context, ready for execution
  *
  * @see
  *   [[io.constellation.Module]] for module type definitions
  * @see
  *   [[io.constellation.Constellation]] for module registration API
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `[I, O](x$1: ModuleBuilder[I, O]): ModuleBuilder[Product, Product]` |  |
| `apply` | `[I, O](_metadata: ComponentMetadata, _config: ModuleConfig, _context: Option[Map[String, Json]], _httpConfig: Option[ModuleHttpConfig], _run: Function1[I, IO[Produces[O]]]): ModuleBuilder[Product, Product]` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `metadata$default$5` | `(): List` | /** Entry point for building a module. Returns a [[ModuleBuilderInit]] for further configuration. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `metadata` | `(name: String, description: String, majorVersion: Int, minorVersion: Int, tags: List[String]): ModuleBuilderInit` | /** Entry point for building a module. Returns a [[ModuleBuilderInit]] for further configuration. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ModuleBuilderInit$

/** Initial builder state before an implementation function is defined.
  *
  * Call [[implementation]], [[implementationPure]], or [[implementationWithContext]] to transition
  * to a typed [[ModuleBuilder]] with input/output types fixed.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: ModuleBuilderInit): ModuleBuilderInit` |  |
| `apply` | `(_metadata: ComponentMetadata, _config: ModuleConfig, _context: Option[Map[String, Json]], _httpConfig: Option[ModuleHttpConfig]): ModuleBuilderInit` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ModuleCallOptions$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: ModuleCallOptions): ModuleCallOptions` |  |
| `apply` | `(retry: Option[Int], timeoutMs: Option[Long], delayMs: Option[Long], backoff: Option[String], cacheMs: Option[Long], cacheBackend: Option[String], throttleCount: Option[Int], throttlePerMs: Option[Long], concurrency: Option[Int], onError: Option[String], lazyEval: Option[Boolean], priority: Option[Int], batchSize: Option[Int], batchTimeoutMs: Option[Long], window: Option[String], checkpointMs: Option[Long], joinStrategy: Option[String]): ModuleCallOptions` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
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

### ModuleConfig$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: ModuleConfig): ModuleConfig` |  |
| `apply` | `(inputsTimeout: FiniteDuration, moduleTimeout: FiniteDuration): ModuleConfig` |  |
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
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `default` | `(): ModuleConfig` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ModuleExecutionError$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: ModuleExecutionError): ModuleExecutionError` |  |
| `apply` | `(moduleName: String, cause: Throwable, ctx: Map[String, String]): ModuleExecutionError` |  |
| `apply` | `(moduleName: String, cause: Throwable): ModuleExecutionError` |  |
| `apply` | `(moduleName: String, cause: Option[Throwable], context: Map[String, String]): ModuleExecutionError` |  |
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
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ModuleHttpConfig$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: ModuleHttpConfig): ModuleHttpConfig` |  |
| `apply` | `(published: Boolean): ModuleHttpConfig` |  |
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
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ModuleNodeSpec$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: ModuleNodeSpec): ModuleNodeSpec` |  |
| `apply` | `(metadata: ComponentMetadata, consumes: Map[String, CType], produces: Map[String, CType], config: ModuleConfig, definitionContext: Option[Map[String, Json]], httpConfig: Option[ModuleHttpConfig]): ModuleNodeSpec` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `empty` | `(): ModuleNodeSpec` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ModuleNotFoundError$

/** Error when a module is not found in the namespace.
  *
  * @param moduleName
  *   The name of the missing module
  * @param context
  *   Additional debugging context
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: ModuleNotFoundError): ModuleNotFoundError` |  |
| `apply` | `(moduleName: String, context: Map[String, String]): ModuleNotFoundError` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### NodeAlreadyResolvedError$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: NodeAlreadyResolvedError): NodeAlreadyResolvedError` |  |
| `apply` | `(name: String): NodeAlreadyResolvedError` |  |
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

### NodeExecutionResult$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: NodeExecutionResult): NodeExecutionResult` |  |
| `apply` | `(nodeId: String, status: NodeStatus, value: Option[Json], durationMs: Option[Long], error: Option[String]): NodeExecutionResult` |  |
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
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### NodeNotFoundError$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(nodeId: UUID, nodeType: String, context: Map[String, String]): NodeNotFoundError` |  |
| `condition$default$2` | `(): Map` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `source$default$2` | `(): Map` |  |
| `expression$default$2` | `(): Map` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `input` | `(nodeId: UUID, ctx: Map[String, String]): NodeNotFoundError` |  |
| `unapply` | `(x$1: NodeNotFoundError): NodeNotFoundError` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `input$default$2` | `(): Map` |  |
| `expression` | `(nodeId: UUID, ctx: Map[String, String]): NodeNotFoundError` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `condition` | `(nodeId: UUID, ctx: Map[String, String]): NodeNotFoundError` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `source` | `(nodeId: UUID, ctx: Map[String, String]): NodeNotFoundError` |  |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### NodeStatus$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `values` | `(): Array` |  |
| `fromOrdinal` | `(ordinal: Int): NodeStatus` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `valueOf` | `($name: String): NodeStatus` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### NodeTypeMismatchError$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: NodeTypeMismatchError): NodeTypeMismatchError` |  |
| `apply` | `(name: String, expected: CType, actual: CType): NodeTypeMismatchError` |  |
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

### PipelineChangedError$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: PipelineChangedError): PipelineChangedError` |  |
| `apply` | `(expected: String, actual: String): PipelineChangedError` |  |
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

### PipelineImage$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: PipelineImage): PipelineImage` |  |
| `apply` | `(structuralHash: String, syntacticHash: String, dagSpec: DagSpec, moduleOptions: Map[UUID, ModuleCallOptions], compiledAt: Instant, sourceHash: Option[String]): PipelineImage` |  |
| `computeStructuralHash` | `(dagSpec: DagSpec): String` | /** Compute the structural hash of a DagSpec. */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `rehydrate` | `(image: PipelineImage): LoadedPipeline` | /** Rehydrate a PipelineImage into a LoadedPipeline by reconstructing synthetic modules. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### PipelineNotFoundError$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: PipelineNotFoundError): PipelineNotFoundError` |  |
| `apply` | `(ref: String): PipelineNotFoundError` |  |
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

### PipelineStatus$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### RawValue$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### RawValueConverter$

/** Converts between CValue and RawValue representations.
  *
  * This converter is used at system boundaries:
  *   - JSON input → RawValue (using fromCValue after JSON parsing)
  *   - Internal execution uses RawValue
  *   - RawValue → JSON output (using TypedValueAccessor.toCValue)
  *
  * ==Memory Optimization==
  *
  * The converter automatically uses specialized array types for primitive lists:
  *   - List[Long] → RIntList
  *   - List[Double] → RFloatList
  *   - List[String] → RStringList
  *   - List[Boolean] → RBoolList
  *
  * This provides ~6x memory reduction for large numeric arrays.
  */

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
| `fromCValue` | `(cValue: CValue): RawValue` | /** Convert a CValue to its memory-efficient RawValue representation. Type information is stripped |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toCValue` | `(raw: RawValue, cType: CType): CValue` | /** Convert a RawValue back to CValue using type information. Delegates to TypedValueAccessor. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ResolutionSource$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ResumeInProgressError$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: ResumeInProgressError): ResumeInProgressError` |  |
| `apply` | `(executionId: UUID): ResumeInProgressError` |  |
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

### Runtime$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: Runtime): Runtime` |  |
| `runWithRawInputsAndScheduler` | `(dag: DagSpec, initData: Map[String, RawValue], inputTypes: Map[String, CType], modules: Map[UUID, Uninitialized], modulePriorities: Map[UUID, Int], scheduler: GlobalScheduler): IO[State]` | /** Run DAG with RawValue inputs and priority-based scheduling. |
| `apply` | `(table: MutableDataTable, state: MutableState): Runtime` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `runCancellable` | `(dag: DagSpec, initData: Map[String, CValue], modules: Map[UUID, Uninitialized], modulePriorities: Map[UUID, Int], scheduler: GlobalScheduler, backends: ConstellationBackends): IO[CancellableExecution]` | /** Run DAG with cancellation support. |
| `runWithBackends` | `(dag: DagSpec, initData: Map[String, CValue], modules: Map[UUID, Uninitialized], modulePriorities: Map[UUID, Int], scheduler: GlobalScheduler, backends: ConstellationBackends): IO[State]` | /** Run DAG with pluggable backend instrumentation. |
| `runWithTimeout` | `(timeout: FiniteDuration, dag: DagSpec, initData: Map[String, CValue], modules: Map[UUID, Uninitialized], modulePriorities: Map[UUID, Int], scheduler: GlobalScheduler, backends: ConstellationBackends): IO[State]` | /** Run DAG with a global timeout. |
| `runPooledWithScheduler` | `(dag: DagSpec, initData: Map[String, CValue], modules: Map[UUID, Uninitialized], pool: RuntimePool, modulePriorities: Map[UUID, Int], scheduler: GlobalScheduler): IO[State]` | /** Run DAG with object pooling and priority-based scheduling. |
| `anyToCValue` | `(value: Any, cType: CType): CValue` | /** Convert an Any value to CValue based on the expected CType. Used by inline transforms and |
| `runPooled` | `(dag: DagSpec, initData: Map[String, CValue], modules: Map[UUID, Uninitialized], pool: RuntimePool): IO[State]` | /** Run DAG with object pooling for reduced allocation overhead. |
| `runWithScheduler` | `(dag: DagSpec, initData: Map[String, CValue], modules: Map[UUID, Uninitialized], modulePriorities: Map[UUID, Int], scheduler: GlobalScheduler): IO[State]` | /** Run DAG with priority-based scheduling. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `run` | `(dag: DagSpec, initData: Map[String, CValue], modules: Map[UUID, Uninitialized]): IO[State]` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `runWithRawInputs` | `(dag: DagSpec, initData: Map[String, RawValue], inputTypes: Map[String, CType], modules: Map[UUID, Uninitialized]): IO[State]` | /** Run DAG with RawValue inputs (memory-efficient path). |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
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

### RuntimeNotInitializedError$

/** Error when the runtime is not properly initialized.
  *
  * @param reason
  *   What is not initialized
  * @param context
  *   Additional debugging context
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: RuntimeNotInitializedError): RuntimeNotInitializedError` |  |
| `apply` | `(reason: String, context: Map[String, String]): RuntimeNotInitializedError` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### SignatureMetadata$

/** Optional metadata collected during execution.
  *
  * All fields are optional and governed by [[ExecutionOptions]].
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: SignatureMetadata): SignatureMetadata` |  |
| `apply` | `(startedAt: Option[Instant], completedAt: Option[Instant], totalDuration: Option[Duration], nodeTimings: Option[Map[String, Duration]], provenance: Option[Map[String, String]], blockedGraph: Option[Map[String, List[String]]], resolutionSources: Option[Map[String, ResolutionSource]]): SignatureMetadata` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `toString` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
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

### SteppedExecution$

/** Stepped execution engine for debugging DAG pipelines. Allows batch-by-batch execution with state
  * inspection between steps.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `createSession` | `(sessionId: String, dagSpec: DagSpec, syntheticModules: Map[UUID, Uninitialized], registeredModules: Map[UUID, Uninitialized], inputs: Map[String, CValue]): IO[SessionState]` | /** Create a new stepped execution session. |
| `executeNextBatch` | `(session: SessionState): IO[Tuple2[SessionState, Boolean]]` | /** Execute the next batch of modules. Returns the updated session state and whether execution is |
| `executeToCompletion` | `(session: SessionState): IO[SessionState]` | /** Execute remaining batches to completion. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `computeBatches` | `(dagSpec: DagSpec): List[ExecutionBatch]` | /** Compute execution batches using Kahn's algorithm for topological ordering. Groups modules by |
| `initializeRuntime` | `(session: SessionState): IO[SessionState]` | /** Initialize the runtime for stepping (called before first step). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `valuePreview` | `(value: CValue, maxLength: Int): String` | /** Convert CValue to a preview string for display. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `getOutputs` | `(session: SessionState): Map[String, CValue]` | /** Get output values from a completed session. |
| `valuePreview$default$2` | `(): Int` | /** Convert CValue to a preview string for display. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### StreamingJsonConverter$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(limits: StreamingLimits): StreamingJsonConverter` |  |
| `apply` | `(): StreamingJsonConverter` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `streamToCValue` | `(bytes: Array[Byte], expectedType: CType): Either[String, CValue]` | /** Convenience method for streaming from bytes */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `streamFromString` | `(jsonString: String, expectedType: CType): Either[String, CValue]` | /** Convenience method for streaming from string */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### StreamingLimits$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: StreamingLimits): StreamingLimits` |  |
| `apply` | `(maxPayloadSize: Long, maxArrayElements: Int, maxNestingDepth: Int): StreamingLimits` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### SuspendableExecution$

/** Provides the ability to resume a suspended pipeline execution.
  *
  * Given a [[SuspendedExecution]] snapshot, additional inputs, and optionally manually-resolved
  * node values, this re-executes the pipeline from where it left off.
  *
  * Thread-safety: Concurrent resume calls for the same executionId are prevented by an in-flight
  * operation tracker. Only one resume can proceed at a time per suspended execution.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `resume$default$2` | `(): Map` | /** Resume a suspended execution with additional inputs and/or resolved nodes. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `resume$default$3` | `(): Map` | /** Resume a suspended execution with additional inputs and/or resolved nodes. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `resume` | `(suspended: SuspendedExecution, additionalInputs: Map[String, CValue], resolvedNodes: Map[String, CValue], modules: Map[UUID, Uninitialized], options: ExecutionOptions, scheduler: GlobalScheduler, backends: ConstellationBackends): IO[DataSignature]` | /** Resume a suspended execution with additional inputs and/or resolved nodes. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `resume$default$6` | `(): GlobalScheduler` | /** Resume a suspended execution with additional inputs and/or resolved nodes. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `resume$default$7` | `(): ConstellationBackends` | /** Resume a suspended execution with additional inputs and/or resolved nodes. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `resume$default$5` | `(): ExecutionOptions` | /** Resume a suspended execution with additional inputs and/or resolved nodes. |
| `resume$default$4` | `(): Map` | /** Resume a suspended execution with additional inputs and/or resolved nodes. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### SuspendedExecution$

/** Serializable snapshot of a suspended pipeline execution.
  *
  * Contains all the information needed to resume execution: the DAG structure, already-computed
  * values, provided inputs, and module statuses.
  *
  * @param executionId
  *   Unique ID for this execution
  * @param structuralHash
  *   Structural hash of the pipeline
  * @param resumptionCount
  *   How many times this execution has been resumed
  * @param dagSpec
  *   The DAG specification
  * @param moduleOptions
  *   Per-module runtime options
  * @param providedInputs
  *   Inputs that were provided by the user
  * @param computedValues
  *   Data node values already computed (UUID -> CValue)
  * @param moduleStatuses
  *   Status of each module (UUID -> status string)
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: SuspendedExecution): SuspendedExecution` |  |
| `apply` | `(executionId: UUID, structuralHash: String, resumptionCount: Int, dagSpec: DagSpec, moduleOptions: Map[UUID, ModuleCallOptions], providedInputs: Map[String, CValue], computedValues: Map[UUID, CValue], moduleStatuses: Map[UUID, String]): SuspendedExecution` |  |
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

### SuspensionFilter$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: SuspensionFilter): SuspensionFilter` |  |
| `apply` | `(structuralHash: Option[String], executionId: Option[UUID], minResumptionCount: Option[Int], maxResumptionCount: Option[Int]): SuspensionFilter` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### SuspensionHandle$

/** Opaque handle to a stored suspended execution. */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: SuspensionHandle): SuspensionHandle` |  |
| `apply` | `(id: String): SuspensionHandle` |  |
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

### SuspensionSummary$

/** Summary of a stored suspension, without the full execution snapshot.
  *
  * @param handle
  *   Opaque handle for load/delete operations
  * @param executionId
  *   The original execution ID
  * @param structuralHash
  *   Pipeline structural hash (links to PipelineStore)
  * @param resumptionCount
  *   How many times this execution has been resumed
  * @param missingInputs
  *   Inputs that are still needed (name -> type)
  * @param createdAt
  *   When the suspension was first stored
  * @param lastResumedAt
  *   When the suspension was last resumed (if ever)
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: SuspensionSummary): SuspensionSummary` |  |
| `apply` | `(handle: SuspensionHandle, executionId: UUID, structuralHash: String, resumptionCount: Int, missingInputs: Map[String, CType], createdAt: Instant, lastResumedAt: Option[Instant]): SuspensionSummary` |  |
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

### SyntheticModuleFactory$

/** Reconstructs synthetic module implementations from a [[DagSpec]].
  *
  * Currently only branch modules can be reconstructed because their behaviour is fully determined
  * by the DagSpec structure (number of condition/expression pairs and the output type). HOF
  * transforms (filter, map, etc.) contain closures that cannot be serialized or reconstructed.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `fromDagSpec` | `(dagSpec: DagSpec): Map[UUID, Uninitialized]` | /** Scan a DagSpec for branch modules and reconstruct their [[Module.Uninitialized]]. |
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

### TypeConversionError$

/** Error when type conversion fails between formats.
  *
  * @param from
  *   Source type or format
  * @param to
  *   Target type or format
  * @param reason
  *   Why the conversion failed
  * @param context
  *   Additional debugging context
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: TypeConversionError): TypeConversionError` |  |
| `apply` | `(from: String, to: String, reason: String, context: Map[String, String]): TypeConversionError` |  |
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

### TypeMismatchError$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: TypeMismatchError): TypeMismatchError` |  |
| `apply` | `(expected: String, actual: String, context: Map[String, String]): TypeMismatchError` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `fromValue$default$3` | `(): Map` | /** Create a TypeMismatchError from CValue */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `fromTypes$default$3` | `(): Map` | /** Create a TypeMismatchError from CType values */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `fromValue` | `(expected: String, actual: CValue, ctx: Map[String, String]): TypeMismatchError` | /** Create a TypeMismatchError from CValue */ |
| `fromTypes` | `(expected: CType, actual: CType, ctx: Map[String, String]): TypeMismatchError` | /** Create a TypeMismatchError from CType values */ |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### TypeSystem$package$

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
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `deriveType` | `[T](tag: CTypeTag[T]): CType` | /** Convenience function to derive CType from a Scala type. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### TypedValueAccessor$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(cType: CType): TypedValueAccessor` |  |
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

### UndefinedVariableError$

/** Error when a variable is referenced but not defined.
  *
  * @param variableName
  *   The name of the undefined variable
  * @param context
  *   Additional debugging context
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: UndefinedVariableError): UndefinedVariableError` |  |
| `apply` | `(variableName: String, context: Map[String, String]): UndefinedVariableError` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### UnknownNodeError$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: UnknownNodeError): UnknownNodeError` |  |
| `apply` | `(name: String): UnknownNodeError` |  |
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

### UnsupportedOperationError$

/** Error when an unsupported operation is encountered.
  *
  * @param operation
  *   Description of the unsupported operation
  * @param reason
  *   Why the operation is not supported
  * @param context
  *   Additional debugging context
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: UnsupportedOperationError): UnsupportedOperationError` |  |
| `apply` | `(operation: String, reason: String, context: Map[String, String]): UnsupportedOperationError` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ValidationError$

/** Error when validation fails (e.g., DAG validation).
  *
  * @param errors
  *   List of validation error messages
  * @param context
  *   Additional debugging context
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: ValidationError): ValidationError` |  |
| `apply` | `(errors: List[String], context: Map[String, String]): ValidationError` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### json$

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
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `evalEncoder` | `[A](encoder: Encoder[A]): Encoder[Eval[Any]]` |  |
| `evalDecoder` | `[A](decoder: Decoder[A]): Decoder[Eval[Any]]` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `uuidMapDecoder` | `[A](decoder: Decoder[A]): Decoder[Map[UUID, Any]]` |  |
| `uuidMapEncoder` | `[A](encoder: Encoder[A]): Encoder[Map[UUID, Any]]` |  |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

## Classes

### class AdaptiveJsonConverter

/** Adaptive JSON converter that chooses the best strategy based on payload characteristics.
  *
  * ==Strategies==
  *
  *   - **Eager** (current): Best for small payloads (<10KB). Uses recursive descent with full
  *     materialization. Lowest overhead for small data.
  *
  *   - **Lazy**: Best for medium payloads (10KB-100KB) or when partial access is expected. Wraps
  *     JSON and defers conversion until values are accessed.
  *
  *   - **Streaming**: Best for large payloads (>100KB). Uses Jackson streaming parser for
  *     memory-efficient parsing without loading entire structure.
  *
  * ==Usage==
  *
  * {{{
  * val converter = AdaptiveJsonConverter()
  * val result = converter.convert(json, expectedType)
  *
  * // With explicit size hint (avoids re-estimation)
  * val result = converter.convert(json, expectedType, sizeHint = Some(50000))
  *
  * // With custom thresholds
  * val customConverter = AdaptiveJsonConverter(
  *   lazyThreshold = 5000,
  *   streamingThreshold = 50000
  * )
  * }}}
  *
  * ==Performance Characteristics==
  *
  * | Payload Size | Strategy  | Overhead | Memory    |
  * |:-------------|:----------|:---------|:----------|
  * | < 10KB       | Eager     | Minimal  | Full      |
  * | 10-100KB     | Lazy      | Low      | On-demand |
  * | > 100KB      | Streaming | Medium   | Constant  |
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getLastStrategy` | `(): ConversionStrategy` | /** Get the strategy used for the last conversion (for testing/diagnostics) */ |
| `convert$default$3` | `(): Option` | /** Convert JSON to CValue using the optimal strategy. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `convert` | `(json: Json, expectedType: CType, sizeHint: Option[Int]): Either[String, CValue]` | /** Convert JSON to CValue using the optimal strategy. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class BoundedInputStream

/** InputStream wrapper that enforces a maximum read size.
  *
  * Throws IllegalStateException when limit is exceeded.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `available` | `(): Int` |  |
| `skip` | `(x$0: Long): Long` |  |
| `close` | `(): Unit` |  |
| `getBytesRead` | `(): Long` |  |
| `reset` | `(): Unit` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `mark` | `(x$0: Int): Unit` |  |
| `markSupported` | `(): Boolean` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `skipNBytes` | `(x$0: Long): Unit` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `transferTo` | `(x$0: OutputStream): Long` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `readAllBytes` | `(): Array[Byte]` |  |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `read` | `(b: Array[Byte], off: Int, len: Int): Int` |  |
| `read` | `(): Int` |  |
| `read` | `(x$0: Array[Byte]): Int` |  |
| `readNBytes` | `(x$0: Array[Byte], x$1: Int, x$2: Int): Int` |  |
| `readNBytes` | `(x$0: Int): Array[Byte]` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class CodecError

/** Error during suspension encoding/decoding. */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `message` | `String` |  |
| `cause` | `Option[Throwable]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): String` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `productPrefix` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_1` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `copy$default$2` | `(): Option` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Option` |  |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(message: String, cause: Option[Throwable]): CodecError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class ComponentMetadata

/** Shared metadata for components (modules and DAGs).
  *
  * @param name
  *   Unique component name (case-sensitive)
  * @param description
  *   Human-readable description
  * @param tags
  *   Classification tags for filtering and discovery
  * @param majorVersion
  *   Major semantic version
  * @param minorVersion
  *   Minor semantic version
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` |  |
| `description` | `String` |  |
| `tags` | `List[String]` |  |
| `majorVersion` | `Int` |  |
| `minorVersion` | `Int` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productArity` | `(): Int` |  |
| `_1` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): List` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$4` | `(): Int` |  |
| `copy$default$5` | `(): Int` |  |
| `_5` | `(): Int` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$1` | `(): String` |  |
| `copy$default$3` | `(): List` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `(): String` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): String` |  |
| `_4` | `(): Int` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(name: String, description: String, tags: List[String], majorVersion: Int, minorVersion: Int): ComponentMetadata` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class CycleDetectedError

/** Error when a cycle is detected in the DAG.
  *
  * @param nodeIds
  *   The nodes involved in the cycle (if available)
  * @param context
  *   Additional debugging context
  */

**Extends:** CompilerError, Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `nodeIds` | `List[UUID]` |  |
| `context` | `Map[String, String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): List` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `errorCode` | `(): String` |  |
| `productPrefix` | `(): String` |  |
| `category` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_1` | `(): List` |  |
| `getCause` | `(): Throwable` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `message` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toJson` | `(): Json` | /** Convert this error to a JSON object for API responses */ |
| `copy$default$2` | `(): Map` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Map` |  |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(nodeIds: List[UUID], context: Map[String, String]): CycleDetectedError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class DagSpec

/** Specification for a directed acyclic graph (DAG) pipeline.
  *
  * A DAG consists of module nodes (processing steps) and data nodes (values flowing between
  * modules), connected by directed edges. The compiler produces a `DagSpec` from constellation-lang
  * source code, and the runtime executes it by traversing the graph in topological order.
  *
  * @param metadata
  *   Component metadata (name, description, version, tags)
  * @param modules
  *   Module nodes keyed by UUID
  * @param data
  *   Data nodes keyed by UUID
  * @param inEdges
  *   Edges from data nodes to module nodes (module inputs)
  * @param outEdges
  *   Edges from module nodes to data nodes (module outputs)
  * @param declaredOutputs
  *   Explicitly declared output variable names (from `out` statements)
  * @param outputBindings
  *   Mapping from output name to the data node UUID that produces it
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `metadata` | `ComponentMetadata` |  |
| `modules` | `Map[UUID, ModuleNodeSpec]` |  |
| `data` | `Map[UUID, DataNodeSpec]` |  |
| `inEdges` | `Set[Tuple2[UUID, UUID]]` |  |
| `outEdges` | `Set[Tuple2[UUID, UUID]]` |  |
| `declaredOutputs` | `List[String]` |  |
| `outputBindings` | `Map[String, UUID]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `copy$default$7` | `(): Map` |  |
| `_6` | `(): List` |  |
| `bottomLevelDataNodes` | `(): Map` | /** Data nodes that are outputs of the DAG (not consumed by any module) */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): Map` |  |
| `productPrefix` | `(): String` |  |
| `minorVersion` | `(): Int` |  |
| `majorVersion` | `(): Int` |  |
| `copy$default$4` | `(): Set` |  |
| `copy$default$5` | `(): Set` |  |
| `_5` | `(): Set` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_7` | `(): Map` |  |
| `productArity` | `(): Int` |  |
| `userInputDataNodes` | `(): Map` | /** Data nodes that are user inputs (top-level nodes without inline transforms) */ |
| `copy$default$1` | `(): ComponentMetadata` |  |
| `name` | `(): String` |  |
| `_1` | `(): ComponentMetadata` |  |
| `copy$default$3` | `(): Map` |  |
| `toString` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `copy$default$2` | `(): Map` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `description` | `(): String` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Map` |  |
| `_4` | `(): Set` |  |
| `copy$default$6` | `(): List` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(metadata: ComponentMetadata, modules: Map[UUID, ModuleNodeSpec], data: Map[UUID, DataNodeSpec], inEdges: Set[Tuple2[UUID, UUID]], outEdges: Set[Tuple2[UUID, UUID]], declaredOutputs: List[String], outputBindings: Map[String, UUID]): DagSpec` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `tags` | `(): List` |  |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `topLevelDataNodes` | `(): Map` | /** Data nodes that are inputs to the DAG (not produced by any module) */ |

### case class DataNodeSpec

/** Specification for a data node in a DAG.
  *
  * @param name
  *   The name of the data node
  * @param nicknames
  *   Map from module UUID to the parameter name used by that module
  * @param cType
  *   The type of data this node holds
  * @param inlineTransform
  *   Optional inline transform to compute this node's value from inputs. When present, this data
  *   node computes its value by applying the transform to the values of its input data nodes
  *   (specified in transformInputs). This eliminates the need for a synthetic module node.
  * @param transformInputs
  *   Map from input name (as expected by the transform) to source data node UUID. Only used when
  *   inlineTransform is defined. For example, a MergeTransform expects inputs named "left" and
  *   "right".
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` |  |
| `nicknames` | `Map[UUID, String]` |  |
| `cType` | `CType` |  |
| `inlineTransform` | `Option[InlineTransform]` |  |
| `transformInputs` | `Map[String, UUID]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productArity` | `(): Int` |  |
| `_1` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): CType` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$4` | `(): Option` |  |
| `copy$default$5` | `(): Map` |  |
| `_5` | `(): Map` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$1` | `(): String` |  |
| `copy$default$3` | `(): CType` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `(): Map` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Map` |  |
| `_4` | `(): Option` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(name: String, nicknames: Map[UUID, String], cType: CType, inlineTransform: Option[InlineTransform], transformInputs: Map[String, UUID]): DataNodeSpec` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class DataNotFoundError

/** Error when data is not found in a runtime table.
  *
  * @param dataId
  *   The ID of the missing data
  * @param dataType
  *   Description of the data type (e.g., "data", "deferred")
  * @param context
  *   Additional debugging context
  */

**Extends:** RuntimeError, Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `dataId` | `UUID` |  |
| `dataType` | `String` |  |
| `context` | `Map[String, String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): UUID` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `errorCode` | `(): String` |  |
| `_3` | `(): Map` |  |
| `productPrefix` | `(): String` |  |
| `category` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_1` | `(): UUID` |  |
| `getCause` | `(): Throwable` |  |
| `copy$default$3` | `(): Map` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `message` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toJson` | `(): Json` | /** Convert this error to a JSON object for API responses */ |
| `copy$default$2` | `(): String` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): String` |  |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(dataId: UUID, dataType: String, context: Map[String, String]): DataNotFoundError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class DataSignature

/** Describes the outcome of executing a pipeline.
  *
  * Contains the execution status, all computed values, declared outputs, information about missing
  * inputs (for suspended executions), and optional metadata. When the status is
  * [[PipelineStatus.Suspended]] or [[PipelineStatus.Failed]], a [[SuspendedExecution]] snapshot is
  * included so the execution can be resumed.
  *
  * @param executionId
  *   Unique ID for this execution run
  * @param structuralHash
  *   Structural hash of the pipeline that was executed
  * @param resumptionCount
  *   Number of times this execution has been resumed (0 for first run)
  * @param status
  *   Terminal status of the execution
  * @param inputs
  *   Input values that were provided
  * @param computedNodes
  *   All computed data node values (keyed by variable name)
  * @param outputs
  *   Subset of computedNodes matching declared outputs
  * @param missingInputs
  *   Input variable names that were expected but not provided
  * @param pendingOutputs
  *   Declared output names that were not computed
  * @param suspendedState
  *   Snapshot for resumption (present when Suspended or Failed)
  * @param metadata
  *   Optional timing/provenance metadata
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `executionId` | `UUID` |  |
| `structuralHash` | `String` |  |
| `resumptionCount` | `Int` |  |
| `status` | `PipelineStatus` |  |
| `inputs` | `Map[String, CValue]` |  |
| `computedNodes` | `Map[String, CValue]` |  |
| `outputs` | `Map[String, CValue]` |  |
| `missingInputs` | `List[String]` |  |
| `pendingOutputs` | `List[String]` |  |
| `suspendedState` | `Option[SuspendedExecution]` |  |
| `metadata` | `SignatureMetadata` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `_9` | `(): List` |  |
| `copy$default$7` | `(): Map` |  |
| `_6` | `(): Map` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `output` | `(name: String): Option[CValue]` | /** Look up a specific output by name. */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `productPrefix` | `(): String` |  |
| `copy$default$11` | `(): SignatureMetadata` |  |
| `_3` | `(): Int` |  |
| `_11` | `(): SignatureMetadata` |  |
| `copy$default$9` | `(): List` |  |
| `copy$default$4` | `(): PipelineStatus` |  |
| `copy$default$5` | `(): Map` |  |
| `_5` | `(): Map` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_7` | `(): Map` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): UUID` |  |
| `isComplete` | `(): Boolean` | /** Whether all declared outputs have been computed. */ |
| `failedNodes` | `(): List` | /** Names of nodes whose modules failed during execution. */ |
| `_1` | `(): UUID` |  |
| `allInputs` | `(): Map` | /** All provided input values. */ |
| `copy$default$3` | `(): Int` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `copy$default$8` | `(): List` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `(): String` |  |
| `node` | `(name: String): Option[CValue]` | /** Look up any computed node by variable name. */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `copy$default$10` | `(): Option` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): String` |  |
| `_4` | `(): PipelineStatus` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `copy$default$6` | `(): Map` |  |
| `progress` | `(): Double` | /** Fraction of declared outputs that have been computed (0.0 to 1.0). */ |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(executionId: UUID, structuralHash: String, resumptionCount: Int, status: PipelineStatus, inputs: Map[String, CValue], computedNodes: Map[String, CValue], outputs: Map[String, CValue], missingInputs: List[String], pendingOutputs: List[String], suspendedState: Option[SuspendedExecution], metadata: SignatureMetadata): DataSignature` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `hashCode` | `(): Int` |  |
| `_10` | `(): Option` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `_8` | `(): List` |  |

### case class ExecutionError

/** Describes a single error that occurred during module execution. */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `nodeName` | `String` |  |
| `moduleName` | `String` |  |
| `message` | `String` |  |
| `cause` | `Option[Throwable]` |  |
| `retriesAttempted` | `Int` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): String` |  |
| `_1` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): String` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$4` | `(): Option` |  |
| `copy$default$5` | `(): Int` |  |
| `_5` | `(): Int` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$3` | `(): String` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `copy$default$2` | `(): String` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): String` |  |
| `_4` | `(): Option` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(nodeName: String, moduleName: String, message: String, cause: Option[Throwable], retriesAttempted: Int): ExecutionError` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class ExecutionOptions

/** Controls which optional metadata is collected during execution.
  *
  * All flags default to false to keep the common path lightweight.
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `includeTimings` | `Boolean` |  |
| `includeProvenance` | `Boolean` |  |
| `includeBlockedGraph` | `Boolean` |  |
| `includeResolutionSources` | `Boolean` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): Boolean` |  |
| `_1` | `(): Boolean` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): Boolean` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$4` | `(): Boolean` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$3` | `(): Boolean` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `(): Boolean` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Boolean` |  |
| `_4` | `(): Boolean` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(includeTimings: Boolean, includeProvenance: Boolean, includeBlockedGraph: Boolean, includeResolutionSources: Boolean): ExecutionOptions` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class ExecutionTrace

/** Complete trace of a DAG execution.
  *
  * @param executionId
  *   Unique identifier for this execution
  * @param dagName
  *   Name of the DAG that was executed
  * @param startTime
  *   Unix timestamp when execution started
  * @param endTime
  *   Unix timestamp when execution ended (if completed)
  * @param nodeResults
  *   Map of node ID to execution result
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `executionId` | `String` |  |
| `dagName` | `String` |  |
| `startTime` | `Long` |  |
| `endTime` | `Option[Long]` |  |
| `nodeResults` | `Map[String, NodeExecutionResult]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productArity` | `(): Int` |  |
| `_1` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `_3` | `(): Long` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$4` | `(): Option` |  |
| `copy$default$5` | `(): Map` |  |
| `_5` | `(): Map` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$1` | `(): String` |  |
| `isComplete` | `(): Boolean` | /** Check if execution is complete */ |
| `copy$default$3` | `(): Long` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `(): String` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): String` |  |
| `_4` | `(): Option` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(executionId: String, dagName: String, startTime: Long, endTime: Option[Long], nodeResults: Map[String, NodeExecutionResult]): ExecutionTrace` |  |
| `totalDurationMs` | `(): Option` | /** Total execution time in milliseconds, if completed */ |
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

### case class InputAlreadyProvidedError

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `productPrefix` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$1` | `(): String` |  |
| `_1` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(name: String): InputAlreadyProvidedError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class InputTypeMismatchError

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` |  |
| `expected` | `CType` |  |
| `actual` | `CType` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `_3` | `(): CType` |  |
| `productPrefix` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$1` | `(): String` |  |
| `_1` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `copy$default$3` | `(): CType` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `(): CType` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): CType` |  |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(name: String, expected: CType, actual: CType): InputTypeMismatchError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class InputValidationError

/** Error when input validation fails.
  *
  * @param inputName
  *   The name of the input that failed validation
  * @param reason
  *   Why validation failed
  * @param context
  *   Additional debugging context
  */

**Extends:** RuntimeError, Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `inputName` | `String` |  |
| `reason` | `String` |  |
| `context` | `Map[String, String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): String` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `errorCode` | `(): String` |  |
| `_3` | `(): Map` |  |
| `productPrefix` | `(): String` |  |
| `category` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_1` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `copy$default$3` | `(): Map` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `message` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toJson` | `(): Json` | /** Convert this error to a JSON object for API responses */ |
| `copy$default$2` | `(): String` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): String` |  |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(inputName: String, reason: String, context: Map[String, String]): InputValidationError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### class LazyJsonValue

/** Wraps a JSON value and only converts to CValue when materialize() is called. Caches the result
  * after first conversion.
  *
  * @param json
  *   The JSON value to wrap
  * @param expectedType
  *   The expected CType for conversion
  */

**Extends:** LazyCValue

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `cType` | `(): CType` |  |
| `materialize` | `(): Either` |  |
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
| `isMaterialized` | `(): Boolean` | /** Check if value has been materialized */ |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class LazyListValue

/** Lazy list that only converts accessed elements. Ideal for large arrays where only a subset is
  * needed.
  *
  * @param jsonArray
  *   The JSON array as a Vector of Json elements
  * @param elementType
  *   The CType of each element
  */

**Extends:** LazyCValue

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `materialize` | `(): Either` | /** Materialize all elements - use sparingly for large lists */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `get` | `(index: Int): Either[String, CValue]` | /** Get element at index, converting on first access */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `cType` | `(): CType` |  |
| `size` | `(): Int` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `isFullyMaterialized` | `(): Boolean` | /** Check if all elements are materialized */ |
| `materializedCount` | `(): Int` | /** Get number of materialized elements */ |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class LazyProductValue

/** Lazy product (record) that only converts accessed fields.
  *
  * @param jsonObj
  *   The JSON object as a map of field name to Json
  * @param fieldTypes
  *   Map of field name to expected CType
  */

**Extends:** LazyCValue

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `cType` | `(): CType` |  |
| `materialize` | `(): Either` | /** Materialize all fields */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `getField` | `(name: String): Either[String, Option[CValue]]` | /** Get field by name, converting on first access */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `fieldNames` | `(): Set` | /** Get field names */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getFieldRequired` | `(name: String): Either[String, CValue]` | /** Get field, requiring it to exist */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `materializedCount` | `(): Int` | /** Get number of materialized fields */ |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class LoadedPipeline

/** A pipeline that is ready to execute.
  *
  * Combines an immutable [[PipelineImage]] with the runtime module instances (synthetic modules
  * like branch modules) needed for execution.
  *
  * @param image
  *   The immutable pipeline snapshot
  * @param syntheticModules
  *   Module implementations keyed by node UUID
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `image` | `PipelineImage` |  |
| `syntheticModules` | `Map[UUID, Uninitialized]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productPrefix` | `(): String` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): PipelineImage` |  |
| `_1` | `(): PipelineImage` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `structuralHash` | `(): String` | /** Structural hash of the underlying DAG (convenience delegate). */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `canEqual` | `(that: Any): Boolean` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `(): Map` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Map` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(image: PipelineImage, syntheticModules: Map[UUID, Uninitialized]): LoadedPipeline` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class ModuleBuilder[I, O]

/** Typed builder state with input/output types and implementation function defined.
  *
  * Supports functional transformations (`map`, `contraMap`, `biMap`) and finalization via `build`
  * (multi-field case classes) or `buildSimple` (single-field wrappers).
  *
  * @tparam I
  *   Input case class type
  * @tparam O
  *   Output case class type
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `_metadata` | `ComponentMetadata` |  |
| `_config` | `ModuleConfig` |  |
| `_context` | `Option[Map[String, Json]]` |  |
| `_httpConfig` | `Option[ModuleHttpConfig]` |  |
| `_run` | `Function1[I, IO[Produces[O]]]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `tags` | `(newTags: Seq): ModuleBuilder[I, O]` |  |
| `build` | `(mi: ProductOf[I], mo: ProductOf[O]): Uninitialized` | /** Finalize the builder and produce an uninitialized module. |
| `_3` | `(): Option` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$4` | `[I, O](): Option` |  |
| `copy$default$5` | `[I, O](): Function1[I, IO[Produces[O]]]` |  |
| `_5` | `(): Function1` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `[I, O](): ComponentMetadata` |  |
| `name` | `(newName: String): ModuleBuilder[I, O]` |  |
| `_1` | `(): ComponentMetadata` |  |
| `httpEndpoint` | `(config: ModuleHttpConfig): ModuleBuilder[I, O]` | /** Mark this module for HTTP endpoint publishing with custom configuration. */ |
| `httpEndpoint` | `(): ModuleBuilder[I, O]` | /** Mark this module for HTTP endpoint publishing with default configuration. */ |
| `copy$default$3` | `[I, O](): Option` |  |
| `biMap` | `[I2, O2](f: Function1[I2, I], g: Function1[O, O2]): ModuleBuilder[Product, Product]` | /** Transform both input and output types simultaneously. */ |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `contraMap` | `[I2](f: Function1[I2, I]): ModuleBuilder[Product, O]` | /** Transform the input type, keeping the output type unchanged. */ |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `[I, O](): ModuleConfig` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `description` | `(newDescription: String): ModuleBuilder[I, O]` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): ModuleConfig` |  |
| `_4` | `(): Option` |  |
| `version` | `(major: Int, minor: Int): ModuleBuilder[I, O]` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `inputsTimeout` | `(newTimeout: FiniteDuration): ModuleBuilder[I, O]` |  |
| `copy` | `[I, O](_metadata: ComponentMetadata, _config: ModuleConfig, _context: Option[Map[String, Json]], _httpConfig: Option[ModuleHttpConfig], _run: Function1[I, IO[Produces[O]]]): ModuleBuilder[Product, Product]` |  |
| `moduleTimeout` | `(newTimeout: FiniteDuration): ModuleBuilder[I, O]` |  |
| `hashCode` | `(): Int` |  |
| `map` | `[O2](f: Function1[O, O2]): ModuleBuilder[I, Product]` | /** Transform the output type, keeping the input type unchanged. */ |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `definitionContext` | `(newContext: Map[String, Json]): ModuleBuilder[I, O]` |  |
| `buildSimple` | `(inTag: CTypeTag[I], outTag: CTypeTag[O], outInjector: CValueInjector[O], miIn: ProductOf[SimpleIn[I]], moOut: ProductOf[SimpleOut[O]]): Uninitialized` | /** Build a module using `SimpleIn`/`SimpleOut` wrappers for single-value I/O. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class ModuleBuilderInit

/** Initial builder state before an implementation function is defined.
  *
  * Call [[implementation]], [[implementationPure]], or [[implementationWithContext]] to transition
  * to a typed [[ModuleBuilder]] with input/output types fixed.
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `_metadata` | `ComponentMetadata` |  |
| `_config` | `ModuleConfig` |  |
| `_context` | `Option[Map[String, Json]]` |  |
| `_httpConfig` | `Option[ModuleHttpConfig]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `copy$default$4` | `(): Option` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `implementationPure` | `[I, O](newRun: Function1[I, O]): ModuleBuilder[Product, Product]` | /** Set a pure (side-effect-free) implementation function. |
| `tags` | `(newTags: Seq): ModuleBuilderInit` |  |
| `implementation` | `[I, O](newRun: Function1[I, IO[O]]): ModuleBuilder[Product, Product]` | /** Set an effectful (IO-based) implementation function. |
| `_3` | `(): Option` |  |
| `productPrefix` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): ComponentMetadata` |  |
| `implementationWithContext` | `[I, O](newRun: Function1[I, IO[Produces[O]]]): ModuleBuilder[Product, Product]` | /** Set an implementation that returns [[Module.Produces]] with execution context metadata. |
| `name` | `(newName: String): ModuleBuilderInit` |  |
| `_1` | `(): ComponentMetadata` |  |
| `httpEndpoint` | `(config: ModuleHttpConfig): ModuleBuilderInit` | /** Mark this module for HTTP endpoint publishing with custom configuration. */ |
| `httpEndpoint` | `(): ModuleBuilderInit` | /** Mark this module for HTTP endpoint publishing with default configuration. */ |
| `copy$default$3` | `(): Option` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `(): ModuleConfig` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `description` | `(newDescription: String): ModuleBuilderInit` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): ModuleConfig` |  |
| `_4` | `(): Option` |  |
| `version` | `(major: Int, minor: Int): ModuleBuilderInit` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `inputsTimeout` | `(newTimeout: FiniteDuration): ModuleBuilderInit` |  |
| `copy` | `(_metadata: ComponentMetadata, _config: ModuleConfig, _context: Option[Map[String, Json]], _httpConfig: Option[ModuleHttpConfig]): ModuleBuilderInit` |  |
| `moduleTimeout` | `(newTimeout: FiniteDuration): ModuleBuilderInit` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `definitionContext` | `(newContext: Map[String, Json]): ModuleBuilderInit` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class ModuleCallOptions

/** Runtime-level module call options.
  *
  * Unlike [[io.constellation.lang.compiler.IRModuleCallOptions]] which lives in `lang-compiler` and
  * references AST-level enum types, this case class uses plain strings for enum fields so that it
  * can live in `core` without any `lang-ast` dependency.
  *
  * @param retry
  *   Maximum retry count
  * @param timeoutMs
  *   Timeout in milliseconds
  * @param delayMs
  *   Delay before execution in milliseconds
  * @param backoff
  *   Backoff strategy name: "fixed", "linear", "exponential"
  * @param cacheMs
  *   Cache TTL in milliseconds
  * @param cacheBackend
  *   Cache backend name
  * @param throttleCount
  *   Throttle: max operations
  * @param throttlePerMs
  *   Throttle: per time window in milliseconds
  * @param concurrency
  *   Concurrency limit
  * @param onError
  *   Error strategy name: "fail", "skip", etc.
  * @param lazyEval
  *   Whether to evaluate lazily
  * @param priority
  *   Numeric priority (0-100)
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `retry` | `Option[Int]` |  |
| `timeoutMs` | `Option[Long]` |  |
| `delayMs` | `Option[Long]` |  |
| `backoff` | `Option[String]` |  |
| `cacheMs` | `Option[Long]` |  |
| `cacheBackend` | `Option[String]` |  |
| `throttleCount` | `Option[Int]` |  |
| `throttlePerMs` | `Option[Long]` |  |
| `concurrency` | `Option[Int]` |  |
| `onError` | `Option[String]` |  |
| `lazyEval` | `Option[Boolean]` |  |
| `priority` | `Option[Int]` |  |
| `batchSize` | `Option[Int]` |  |
| `batchTimeoutMs` | `Option[Long]` |  |
| `window` | `Option[String]` |  |
| `checkpointMs` | `Option[Long]` |  |
| `joinStrategy` | `Option[String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `_1` | `(): Option` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `_3` | `(): Option` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$11` | `(): Option` |  |
| `_9` | `(): Option` |  |
| `_11` | `(): Option` |  |
| `copy$default$9` | `(): Option` |  |
| `_16` | `(): Option` |  |
| `copy$default$13` | `(): Option` |  |
| `copy$default$7` | `(): Option` |  |
| `copy$default$4` | `(): Option` |  |
| `copy$default$5` | `(): Option` |  |
| `_14` | `(): Option` |  |
| `_5` | `(): Option` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_7` | `(): Option` |  |
| `productArity` | `(): Int` |  |
| `copy$default$15` | `(): Option` |  |
| `copy$default$1` | `(): Option` |  |
| `_13` | `(): Option` |  |
| `_6` | `(): Option` |  |
| `toString` | `(): String` |  |
| `isEmpty` | `(): Boolean` |  |
| `copy$default$3` | `(): Option` |  |
| `productElementName` | `(n: Int): String` |  |
| `copy$default$8` | `(): Option` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `_12` | `(): Option` |  |
| `_17` | `(): Option` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `copy$default$2` | `(): Option` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `copy$default$10` | `(): Option` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Option` |  |
| `copy$default$14` | `(): Option` |  |
| `_4` | `(): Option` |  |
| `copy$default$6` | `(): Option` |  |
| `productElement` | `(n: Int): Any` |  |
| `copy$default$12` | `(): Option` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(retry: Option[Int], timeoutMs: Option[Long], delayMs: Option[Long], backoff: Option[String], cacheMs: Option[Long], cacheBackend: Option[String], throttleCount: Option[Int], throttlePerMs: Option[Long], concurrency: Option[Int], onError: Option[String], lazyEval: Option[Boolean], priority: Option[Int], batchSize: Option[Int], batchTimeoutMs: Option[Long], window: Option[String], checkpointMs: Option[Long], joinStrategy: Option[String]): ModuleCallOptions` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `copy$default$17` | `(): Option` |  |
| `_15` | `(): Option` |  |
| `copy$default$16` | `(): Option` |  |
| `_10` | `(): Option` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `_8` | `(): Option` |  |

### case class ModuleConfig

/** Timeout configuration for module execution.
  *
  * @param inputsTimeout
  *   Maximum time to wait for all input data nodes to resolve
  * @param moduleTimeout
  *   Maximum time to wait for the module's implementation to complete
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `inputsTimeout` | `FiniteDuration` |  |
| `moduleTimeout` | `FiniteDuration` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productPrefix` | `(): String` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): FiniteDuration` |  |
| `_1` | `(): FiniteDuration` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `canEqual` | `(that: Any): Boolean` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `(): FiniteDuration` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): FiniteDuration` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(inputsTimeout: FiniteDuration, moduleTimeout: FiniteDuration): ModuleConfig` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class ModuleExecutionError

/** Error when a module fails during execution.
  *
  * @param moduleName
  *   The name of the module that failed
  * @param cause
  *   The underlying exception (if any)
  * @param context
  *   Additional debugging context
  */

**Extends:** RuntimeError, Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `moduleName` | `String` |  |
| `cause` | `Option[Throwable]` |  |
| `context` | `Map[String, String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): String` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `errorCode` | `(): String` |  |
| `_3` | `(): Map` |  |
| `productPrefix` | `(): String` |  |
| `category` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_1` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `copy$default$3` | `(): Map` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `message` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toJson` | `(): Json` | /** Convert this error to a JSON object for API responses */ |
| `copy$default$2` | `(): Option` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Option` |  |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(moduleName: String, cause: Option[Throwable], context: Map[String, String]): ModuleExecutionError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class ModuleHttpConfig

/** Configuration for publishing a module as an HTTP endpoint.
  *
  * When attached to a [[ModuleNodeSpec]], the module becomes callable via `POST
  * /modules/{name}/invoke` without writing a pipeline.
  *
  * @param published
  *   Whether the endpoint is active (default true)
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `published` | `Boolean` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productPrefix` | `(): String` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): Boolean` |  |
| `_1` | `(): Boolean` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `canEqual` | `(that: Any): Boolean` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(published: Boolean): ModuleHttpConfig` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class ModuleNodeSpec

/** Specification for a module node within a DAG.
  *
  * Describes the module's identity, its input/output type signatures, timeout configuration, and
  * optional definition-time context metadata.
  *
  * @param metadata
  *   Component metadata (name, description, version, tags)
  * @param consumes
  *   Input parameter types keyed by parameter name
  * @param produces
  *   Output field types keyed by field name
  * @param config
  *   Timeout configuration for inputs and module execution
  * @param definitionContext
  *   Optional JSON metadata from module definition
  * @param httpConfig
  *   Optional HTTP endpoint configuration; when present the module is published at
  *   `/modules/{name}/invoke`
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `metadata` | `ComponentMetadata` |  |
| `consumes` | `Map[String, CType]` |  |
| `produces` | `Map[String, CType]` |  |
| `config` | `ModuleConfig` |  |
| `definitionContext` | `Option[Map[String, Json]]` |  |
| `httpConfig` | `Option[ModuleHttpConfig]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productArity` | `(): Int` |  |
| `_6` | `(): Option` |  |
| `_1` | `(): ComponentMetadata` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `tags` | `(): List` |  |
| `_3` | `(): Map` |  |
| `productPrefix` | `(): String` |  |
| `minorVersion` | `(): Int` |  |
| `majorVersion` | `(): Int` |  |
| `copy$default$4` | `(): ModuleConfig` |  |
| `copy$default$5` | `(): Option` |  |
| `_5` | `(): Option` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$1` | `(): ComponentMetadata` |  |
| `name` | `(): String` |  |
| `copy$default$3` | `(): Map` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `(): Map` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `description` | `(): String` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Map` |  |
| `_4` | `(): ModuleConfig` |  |
| `copy$default$6` | `(): Option` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `inputsTimeout` | `(): FiniteDuration` |  |
| `copy` | `(metadata: ComponentMetadata, consumes: Map[String, CType], produces: Map[String, CType], config: ModuleConfig, definitionContext: Option[Map[String, Json]], httpConfig: Option[ModuleHttpConfig]): ModuleNodeSpec` |  |
| `moduleTimeout` | `(): FiniteDuration` |  |
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

### case class ModuleNotFoundError

/** Error when a module is not found in the namespace.
  *
  * @param moduleName
  *   The name of the missing module
  * @param context
  *   Additional debugging context
  */

**Extends:** RuntimeError, Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `moduleName` | `String` |  |
| `context` | `Map[String, String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): String` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `errorCode` | `(): String` |  |
| `productPrefix` | `(): String` |  |
| `category` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_1` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `message` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toJson` | `(): Json` | /** Convert this error to a JSON object for API responses */ |
| `copy$default$2` | `(): Map` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Map` |  |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(moduleName: String, context: Map[String, String]): ModuleNotFoundError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class NodeAlreadyResolvedError

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `productPrefix` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$1` | `(): String` |  |
| `_1` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(name: String): NodeAlreadyResolvedError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class NodeExecutionResult

/** Result of executing a single node in the DAG.
  *
  * @param nodeId
  *   The unique identifier for this node
  * @param status
  *   The execution status (Pending, Running, Completed, Failed)
  * @param value
  *   The computed value as JSON (truncated if large)
  * @param durationMs
  *   How long this node took to execute in milliseconds
  * @param error
  *   Error message if execution failed
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `nodeId` | `String` |  |
| `status` | `NodeStatus` |  |
| `value` | `Option[Json]` |  |
| `durationMs` | `Option[Long]` |  |
| `error` | `Option[String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): String` |  |
| `_1` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): Option` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$4` | `(): Option` |  |
| `copy$default$5` | `(): Option` |  |
| `_5` | `(): Option` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$3` | `(): Option` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `(): NodeStatus` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): NodeStatus` |  |
| `_4` | `(): Option` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(nodeId: String, status: NodeStatus, value: Option[Json], durationMs: Option[Long], error: Option[String]): NodeExecutionResult` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class NodeNotFoundError

/** Error when a referenced node is not found in the DAG.
  *
  * @param nodeId
  *   The ID of the missing node
  * @param nodeType
  *   Description of the node type (e.g., "input", "source", "condition")
  * @param context
  *   Additional debugging context
  */

**Extends:** CompilerError, Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `nodeId` | `UUID` |  |
| `nodeType` | `String` |  |
| `context` | `Map[String, String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): UUID` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `errorCode` | `(): String` |  |
| `_3` | `(): Map` |  |
| `productPrefix` | `(): String` |  |
| `category` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_1` | `(): UUID` |  |
| `getCause` | `(): Throwable` |  |
| `copy$default$3` | `(): Map` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `message` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toJson` | `(): Json` | /** Convert this error to a JSON object for API responses */ |
| `copy$default$2` | `(): String` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): String` |  |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(nodeId: UUID, nodeType: String, context: Map[String, String]): NodeNotFoundError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class NodeTypeMismatchError

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` |  |
| `expected` | `CType` |  |
| `actual` | `CType` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `_3` | `(): CType` |  |
| `productPrefix` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$1` | `(): String` |  |
| `_1` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `copy$default$3` | `(): CType` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `(): CType` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): CType` |  |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(name: String, expected: CType, actual: CType): NodeTypeMismatchError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class PipelineChangedError

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `expected` | `String` |  |
| `actual` | `String` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): String` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `productPrefix` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_1` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `(): String` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): String` |  |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(expected: String, actual: String): PipelineChangedError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class PipelineImage

/** Immutable, serializable snapshot of a compiled pipeline.
  *
  * A PipelineImage captures everything needed to reconstruct a runnable pipeline: the DAG topology,
  * module call options, and content hashes for deduplication.
  *
  * @param structuralHash
  *   SHA-256 of the canonicalized DagSpec (UUID-independent)
  * @param syntacticHash
  *   SHA-256 of the source text (empty if source unavailable)
  * @param dagSpec
  *   The compiled DAG specification
  * @param moduleOptions
  *   Per-module runtime options (retry, timeout, etc.)
  * @param compiledAt
  *   Timestamp when the image was created
  * @param sourceHash
  *   Optional hash of the original source code
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `structuralHash` | `String` |  |
| `syntacticHash` | `String` |  |
| `dagSpec` | `DagSpec` |  |
| `moduleOptions` | `Map[UUID, ModuleCallOptions]` |  |
| `compiledAt` | `Instant` |  |
| `sourceHash` | `Option[String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): String` |  |
| `_6` | `(): Option` |  |
| `_1` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): DagSpec` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$4` | `(): Map` |  |
| `copy$default$5` | `(): Instant` |  |
| `_5` | `(): Instant` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$3` | `(): DagSpec` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `(): String` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): String` |  |
| `_4` | `(): Map` |  |
| `copy$default$6` | `(): Option` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(structuralHash: String, syntacticHash: String, dagSpec: DagSpec, moduleOptions: Map[UUID, ModuleCallOptions], compiledAt: Instant, sourceHash: Option[String]): PipelineImage` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class PipelineNotFoundError

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `ref` | `String` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): String` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `productPrefix` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_1` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(ref: String): PipelineNotFoundError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class ResumeInProgressError

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `executionId` | `UUID` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): UUID` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `productPrefix` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_1` | `(): UUID` |  |
| `getCause` | `(): Throwable` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(executionId: UUID): ResumeInProgressError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class Runtime

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `table` | `MutableDataTable` |  |
| `state` | `MutableState` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `close` | `(latency: FiniteDuration): IO[State]` |  |
| `_1` | `(): MutableDataTable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `setTableData` | `(dataId: UUID, data: Any): IO[Unit]` |  |
| `setTableDataCValue` | `(dataId: UUID, data: CValue): IO[Unit]` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getTableData` | `(dataId: UUID): IO[Any]` |  |
| `productPrefix` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `setTableDataRawValue` | `(dataId: UUID, data: RawValue): IO[Unit]` | /** Set table data using a RawValue directly (memory-efficient path). Converts RawValue to the |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): MutableDataTable` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `copy$default$2` | `(): MutableState` |  |
| `setStateData` | `(dataId: UUID, data: CValue): IO[Unit]` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): MutableState` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(table: MutableDataTable, state: MutableState): Runtime` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `setModuleStatus` | `(moduleId: UUID, status: Status): IO[Unit]` |  |

### case class RuntimeNotInitializedError

/** Error when the runtime is not properly initialized.
  *
  * @param reason
  *   What is not initialized
  * @param context
  *   Additional debugging context
  */

**Extends:** RuntimeError, Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `reason` | `String` |  |
| `context` | `Map[String, String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): String` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `errorCode` | `(): String` |  |
| `productPrefix` | `(): String` |  |
| `category` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_1` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `message` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toJson` | `(): Json` | /** Convert this error to a JSON object for API responses */ |
| `copy$default$2` | `(): Map` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Map` |  |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(reason: String, context: Map[String, String]): RuntimeNotInitializedError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class SignatureMetadata

/** Optional metadata collected during execution.
  *
  * All fields are optional and governed by [[ExecutionOptions]].
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `startedAt` | `Option[Instant]` |  |
| `completedAt` | `Option[Instant]` |  |
| `totalDuration` | `Option[Duration]` |  |
| `nodeTimings` | `Option[Map[String, Duration]]` |  |
| `provenance` | `Option[Map[String, String]]` |  |
| `blockedGraph` | `Option[Map[String, List[String]]]` |  |
| `resolutionSources` | `Option[Map[String, ResolutionSource]]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `copy$default$7` | `(): Option` |  |
| `copy$default$1` | `(): Option` |  |
| `_1` | `(): Option` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): Option` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$4` | `(): Option` |  |
| `copy$default$5` | `(): Option` |  |
| `_5` | `(): Option` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_7` | `(): Option` |  |
| `productArity` | `(): Int` |  |
| `_6` | `(): Option` |  |
| `copy$default$3` | `(): Option` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `copy$default$2` | `(): Option` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Option` |  |
| `_4` | `(): Option` |  |
| `copy$default$6` | `(): Option` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(startedAt: Option[Instant], completedAt: Option[Instant], totalDuration: Option[Duration], nodeTimings: Option[Map[String, Duration]], provenance: Option[Map[String, String]], blockedGraph: Option[Map[String, List[String]]], resolutionSources: Option[Map[String, ResolutionSource]]): SignatureMetadata` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### class StreamingJsonConverter

/** Streaming JSON converter using Jackson for memory-efficient parsing of large payloads. Parses
  * JSON incrementally without loading entire structure into memory.
  *
  * ==Security Features==
  *
  *   - **Payload size limit**: Default 100MB (prevents memory exhaustion)
  *   - **Array element limit**: Default 1M elements (prevents memory exhaustion)
  *   - **Nesting depth limit**: Default 50 levels (prevents stack overflow)
  *
  * ==Usage==
  *
  * For byte arrays:
  * {{{
  * val converter = StreamingJsonConverter()
  * val result = converter.streamToCValue(bytes, CType.CList(CType.CFloat))
  * }}}
  *
  * For InputStreams (truly streaming):
  * {{{
  * val result = converter.streamFromInputStream(inputStream, expectedType).unsafeRunSync()
  * }}}
  *
  * Custom limits:
  * {{{
  * val limits = StreamingLimits(maxPayloadSize = 10_000_000, maxArrayElements = 100_000)
  * val converter = StreamingJsonConverter(limits)
  * }}}
  *
  * ==Performance==
  *
  * The streaming converter is most beneficial for:
  *   - Large payloads (>100KB)
  *   - Numeric arrays (embeddings, feature vectors)
  *   - When memory is constrained
  *
  * For small payloads, the overhead of Jackson parsing may exceed the benefits.
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `streamToCValue` | `(bytes: Array[Byte], expectedType: CType): Either[String, CValue]` | /** Parse from byte array using streaming (still streaming internally, but from memory). |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `streamFromInputStream` | `(input: InputStream, expectedType: CType): IO[Either[String, CValue]]` | /** Stream JSON from InputStream directly to CValue. Memory-efficient for large payloads. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `streamFromString` | `(jsonString: String, expectedType: CType): Either[String, CValue]` | /** Parse from string using streaming. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class StreamingLimits

/** Configuration for streaming JSON parser limits.
  *
  * Prevents DoS attacks via:
  *   - Payload size limits (memory exhaustion)
  *   - Array element limits (memory exhaustion)
  *   - Nesting depth limits (stack overflow)
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `maxPayloadSize` | `Long` |  |
| `maxArrayElements` | `Int` |  |
| `maxNestingDepth` | `Int` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): Long` |  |
| `_1` | `(): Long` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): Int` |  |
| `productPrefix` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$3` | `(): Int` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `validate` | `(): Either` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `copy$default$2` | `(): Int` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Int` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(maxPayloadSize: Long, maxArrayElements: Int, maxNestingDepth: Int): StreamingLimits` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class SuspendedExecution

/** Serializable snapshot of a suspended pipeline execution.
  *
  * Contains all the information needed to resume execution: the DAG structure, already-computed
  * values, provided inputs, and module statuses.
  *
  * @param executionId
  *   Unique ID for this execution
  * @param structuralHash
  *   Structural hash of the pipeline
  * @param resumptionCount
  *   How many times this execution has been resumed
  * @param dagSpec
  *   The DAG specification
  * @param moduleOptions
  *   Per-module runtime options
  * @param providedInputs
  *   Inputs that were provided by the user
  * @param computedValues
  *   Data node values already computed (UUID -> CValue)
  * @param moduleStatuses
  *   Status of each module (UUID -> status string)
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `executionId` | `UUID` |  |
| `structuralHash` | `String` |  |
| `resumptionCount` | `Int` |  |
| `dagSpec` | `DagSpec` |  |
| `moduleOptions` | `Map[UUID, ModuleCallOptions]` |  |
| `providedInputs` | `Map[String, CValue]` |  |
| `computedValues` | `Map[UUID, CValue]` |  |
| `moduleStatuses` | `Map[UUID, String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `copy$default$7` | `(): Map` |  |
| `copy$default$1` | `(): UUID` |  |
| `_6` | `(): Map` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_8` | `(): Map` |  |
| `_3` | `(): Int` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$4` | `(): DagSpec` |  |
| `copy$default$5` | `(): Map` |  |
| `_5` | `(): Map` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_7` | `(): Map` |  |
| `productArity` | `(): Int` |  |
| `_1` | `(): UUID` |  |
| `copy$default$3` | `(): Int` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `copy$default$8` | `(): Map` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `(): String` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): String` |  |
| `_4` | `(): DagSpec` |  |
| `copy$default$6` | `(): Map` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(executionId: UUID, structuralHash: String, resumptionCount: Int, dagSpec: DagSpec, moduleOptions: Map[UUID, ModuleCallOptions], providedInputs: Map[String, CValue], computedValues: Map[UUID, CValue], moduleStatuses: Map[UUID, String]): SuspendedExecution` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class SuspensionFilter

/** Filter criteria for listing stored suspensions.
  *
  * All fields are optional; when set, they are combined with AND logic.
  *
  * @param structuralHash
  *   Only include suspensions for this pipeline hash
  * @param executionId
  *   Only include this specific execution
  * @param minResumptionCount
  *   Only include suspensions resumed at least this many times
  * @param maxResumptionCount
  *   Only include suspensions resumed at most this many times
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `structuralHash` | `Option[String]` |  |
| `executionId` | `Option[UUID]` |  |
| `minResumptionCount` | `Option[Int]` |  |
| `maxResumptionCount` | `Option[Int]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `copy$default$4` | `(): Option` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): Option` |  |
| `_1` | `(): Option` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): Option` |  |
| `productPrefix` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$3` | `(): Option` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `copy$default$2` | `(): Option` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Option` |  |
| `_4` | `(): Option` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(structuralHash: Option[String], executionId: Option[UUID], minResumptionCount: Option[Int], maxResumptionCount: Option[Int]): SuspensionFilter` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class SuspensionHandle

/** Opaque handle to a stored suspended execution. */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productPrefix` | `(): String` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): String` |  |
| `_1` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `canEqual` | `(that: Any): Boolean` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(id: String): SuspensionHandle` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class SuspensionSummary

/** Summary of a stored suspension, without the full execution snapshot.
  *
  * @param handle
  *   Opaque handle for load/delete operations
  * @param executionId
  *   The original execution ID
  * @param structuralHash
  *   Pipeline structural hash (links to PipelineStore)
  * @param resumptionCount
  *   How many times this execution has been resumed
  * @param missingInputs
  *   Inputs that are still needed (name -> type)
  * @param createdAt
  *   When the suspension was first stored
  * @param lastResumedAt
  *   When the suspension was last resumed (if ever)
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `handle` | `SuspensionHandle` |  |
| `executionId` | `UUID` |  |
| `structuralHash` | `String` |  |
| `resumptionCount` | `Int` |  |
| `missingInputs` | `Map[String, CType]` |  |
| `createdAt` | `Instant` |  |
| `lastResumedAt` | `Option[Instant]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `copy$default$7` | `(): Option` |  |
| `copy$default$1` | `(): SuspensionHandle` |  |
| `_6` | `(): Instant` |  |
| `_1` | `(): SuspensionHandle` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `_3` | `(): String` |  |
| `productPrefix` | `(): String` |  |
| `copy$default$4` | `(): Int` |  |
| `copy$default$5` | `(): Map` |  |
| `_5` | `(): Map` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_7` | `(): Option` |  |
| `productArity` | `(): Int` |  |
| `copy$default$3` | `(): String` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `copy$default$2` | `(): UUID` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): UUID` |  |
| `_4` | `(): Int` |  |
| `copy$default$6` | `(): Instant` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(handle: SuspensionHandle, executionId: UUID, structuralHash: String, resumptionCount: Int, missingInputs: Map[String, CType], createdAt: Instant, lastResumedAt: Option[Instant]): SuspensionSummary` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `hashCode` | `(): Int` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class TypeConversionError

/** Error when type conversion fails between formats.
  *
  * @param from
  *   Source type or format
  * @param to
  *   Target type or format
  * @param reason
  *   Why the conversion failed
  * @param context
  *   Additional debugging context
  */

**Extends:** TypeError, Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `from` | `String` |  |
| `to` | `String` |  |
| `reason` | `String` |  |
| `context` | `Map[String, String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCause` | `(x$0: Throwable): Unit` |  |
| `copy$default$4` | `(): Map` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): String` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `errorCode` | `(): String` |  |
| `_3` | `(): String` |  |
| `productPrefix` | `(): String` |  |
| `category` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_1` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `copy$default$3` | `(): String` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `message` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toJson` | `(): Json` | /** Convert this error to a JSON object for API responses */ |
| `copy$default$2` | `(): String` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): String` |  |
| `getLocalizedMessage` | `(): String` |  |
| `_4` | `(): Map` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(from: String, to: String, reason: String, context: Map[String, String]): TypeConversionError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class TypeMismatchError

/** Error when a value's type doesn't match the expected type.
  *
  * @param expected
  *   The expected CType
  * @param actual
  *   The actual CType or value description
  * @param context
  *   Additional debugging context
  */

**Extends:** TypeError, Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `expected` | `String` |  |
| `actual` | `String` |  |
| `context` | `Map[String, String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): String` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `errorCode` | `(): String` |  |
| `_3` | `(): Map` |  |
| `productPrefix` | `(): String` |  |
| `category` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_1` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `copy$default$3` | `(): Map` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `message` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toJson` | `(): Json` | /** Convert this error to a JSON object for API responses */ |
| `copy$default$2` | `(): String` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): String` |  |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(expected: String, actual: String, context: Map[String, String]): TypeMismatchError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### class TypedValueAccessor

/** Type-aware accessor for RawValue operations.
  *
  * TypedValueAccessor bridges RawValue (untyped, memory-efficient) and CValue (typed,
  * self-describing) representations. It uses the associated CType to interpret RawValue data.
  *
  * ==Usage==
  * {{{
  * val accessor = TypedValueAccessor(CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt)))
  * val raw = RawValue.RProduct(Array(RawValue.RString("Alice"), RawValue.RInt(30)))
  * val nameValue = accessor.getField(raw, "name") // RString("Alice")
  * val cValue = accessor.toCValue(raw) // CProduct(Map("name" -> CString("Alice"), "age" -> CInt(30)))
  * }}}
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getFieldType` | `(fieldName: String): CType` | /** Get the CType for a field in a product type. |
| `getFloat` | `(raw: RawValue): Double` | /** Extract a Double from a RawValue. |
| `getBool` | `(raw: RawValue): Boolean` | /** Extract a Boolean from a RawValue. |
| `getInt` | `(raw: RawValue): Long` | /** Extract a Long from a RawValue. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `getField` | `(raw: RawValue, fieldName: String): RawValue` | /** Get a field from a product value by name. Requires cType to be CProduct. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `elementAccessor` | `(): TypedValueAccessor` | /** Get an accessor for list elements. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `innerAccessor` | `(): TypedValueAccessor` | /** Get an accessor for optional inner type. |
| `getString` | `(raw: RawValue): String` | /** Extract a String from a RawValue. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `fieldAccessor` | `(fieldName: String): TypedValueAccessor` | /** Get an accessor for a field in a product type. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toCValue` | `(raw: RawValue): CValue` | /** Convert a RawValue back to CValue when needed (e.g., for JSON output). |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class UndefinedVariableError

/** Error when a variable is referenced but not defined.
  *
  * @param variableName
  *   The name of the undefined variable
  * @param context
  *   Additional debugging context
  */

**Extends:** CompilerError, Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `variableName` | `String` |  |
| `context` | `Map[String, String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCause` | `(x$0: Throwable): Unit` |  |
| `copy$default$1` | `(): String` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `errorCode` | `(): String` |  |
| `productPrefix` | `(): String` |  |
| `category` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `productArity` | `(): Int` |  |
| `_1` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `message` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toJson` | `(): Json` | /** Convert this error to a JSON object for API responses */ |
| `copy$default$2` | `(): Map` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Map` |  |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(variableName: String, context: Map[String, String]): UndefinedVariableError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class UnknownNodeError

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `productPrefix` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `copy$default$1` | `(): String` |  |
| `_1` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(name: String): UnknownNodeError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class UnsupportedOperationError

/** Error when an unsupported operation is encountered.
  *
  * @param operation
  *   Description of the unsupported operation
  * @param reason
  *   Why the operation is not supported
  * @param context
  *   Additional debugging context
  */

**Extends:** CompilerError, Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `operation` | `String` |  |
| `reason` | `String` |  |
| `context` | `Map[String, String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `errorCode` | `(): String` |  |
| `_3` | `(): Map` |  |
| `productPrefix` | `(): String` |  |
| `category` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `_1` | `(): String` |  |
| `getCause` | `(): Throwable` |  |
| `copy$default$3` | `(): Map` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `message` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toJson` | `(): Json` | /** Convert this error to a JSON object for API responses */ |
| `copy$default$2` | `(): String` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): String` |  |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(operation: String, reason: String, context: Map[String, String]): UnsupportedOperationError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### case class ValidationError

/** Error when validation fails (e.g., DAG validation).
  *
  * @param errors
  *   List of validation error messages
  * @param context
  *   Additional debugging context
  */

**Extends:** RuntimeError, Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `errors` | `List[String]` |  |
| `context` | `Map[String, String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCause` | `(x$0: Throwable): Unit` |  |
| `productArity` | `(): Int` |  |
| `copy$default$1` | `(): List` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `errorCode` | `(): String` |  |
| `productPrefix` | `(): String` |  |
| `category` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_1` | `(): List` |  |
| `getCause` | `(): Throwable` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `message` | `(): String` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toJson` | `(): Json` | /** Convert this error to a JSON object for API responses */ |
| `copy$default$2` | `(): Map` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Map` |  |
| `getLocalizedMessage` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `productElementNames` | `(): Iterator` |  |
| `copy` | `(errors: List[String], context: Map[String, String]): ValidationError` |  |
| `hashCode` | `(): Int` |  |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

## Traits

### trait AwaitOnInputs[I]

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `awaitOnInputs` | `(namespace: Namespace, runtime: Runtime): IO[I]` |  |
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

### trait CType

/** Runtime type representation for Constellation values.
  *
  * CType is a sealed trait representing all possible types that can flow through a Constellation
  * DAG. Every CValue has a corresponding CType.
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
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait CTypeTag[A]

/** Type class mapping Scala types to their [[CType]] representation at compile time.
  *
  * Given instances are provided for primitives (`String`, `Long`, `Double`, `Boolean`), collections
  * (`List`, `Vector`, `Map`, `Option`), and case classes (via Scala 3 `Mirror`).
  *
  * @tparam A
  *   The Scala type to map
  */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `cType` | `(): CType` |  |
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

### trait CValue

/** Runtime value representation for Constellation data.
  *
  * Every value flowing through a DAG is represented as a `CValue`. Each variant carries its data
  * and can report its corresponding [[CType]] via `ctype`.
  */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `ctype` | `(): CType` |  |
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

### trait CValueExtractor[A]

/** Type class for extracting Scala values from [[CValue]] representations.
  *
  * Given instances are provided for primitives, collections, and `Option`. Extraction is effectful
  * (`IO`) because type mismatches raise errors.
  *
  * @tparam A
  *   The target Scala type to extract into
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
| `extract` | `(data: CValue): IO[A]` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `map` | `[B](f: Function1[A, B]): CValueExtractor[Any]` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait CValueInjector[A]

/** Type class for injecting Scala values into [[CValue]] representations.
  *
  * Given instances are provided for primitives, collections, and `Option`. Injection is pure (no
  * side effects).
  *
  * @tparam A
  *   The source Scala type to inject from
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
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `inject` | `(value: A): CValue` |  |
| `contramap` | `[B](f: Function1[B, A]): CValueInjector[Any]` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait CompilerError

/** Errors that occur during compilation, IR generation, or semantic analysis. */

**Extends:** Exception, ConstellationError

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCause` | `(x$0: Throwable): Unit` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getCause` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `errorCode` | `(): String` | /** Error code for programmatic handling (e.g., "TYPE_MISMATCH", "NODE_NOT_FOUND") */ |
| `category` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `toString` | `(): String` |  |
| `message` | `(): String` | /** Human-readable error message */ |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toJson` | `(): Json` | /** Convert this error to a JSON object for API responses */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `getLocalizedMessage` | `(): String` |  |
| `context` | `(): Map` | /** Additional context for debugging (e.g., field names, node IDs, module names) */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### trait Constellation

/** Core API for the Constellation pipeline orchestration engine.
  *
  * Provides methods for registering modules and executing pipelines. This is the primary interface
  * that embedders interact with to compile and run constellation-lang pipelines.
  *
  * {{{
  * for {
  *   constellation <- ConstellationImpl.init
  *   _             <- constellation.setModule(myModule)
  *   // Use LoadedPipeline + run
  *   result        <- constellation.run(LoadedPipeline, inputs)
  *   // Or use PipelineStore ref + run
  *   _             <- constellation.PipelineStore.store(image)
  *   _             <- constellation.PipelineStore.alias("pipeline", image.structuralHash)
  *   result        <- constellation.run("pipeline", inputs)
  * } yield result
  * }}}
  *
  * @see
  *   [[io.constellation.impl.ConstellationImpl]] for the default implementation
  * @see
  *   [[io.constellation.Module]] for module definitions
  * @see
  *   [[io.constellation.DagSpec]] for DAG specifications
  */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `PipelineStore` | `(): PipelineStore` | /** Access the pipeline store for managing compiled pipeline images. */ |
| `run$default$3` | `(): ExecutionOptions` | /** Execute a loaded pipeline with the given inputs. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `getStreamingBatchFunctions` | `(moduleNames: List[String]): IO[Map[String, Function1[List[CValue], IO[List[Either[Throwable, CValue]]]]]]` | /** Get streaming batch functions for persistent bidirectional gRPC streaming (RFC-034 Phase 2B). |
| `setModule` | `(module: Uninitialized): IO[Unit]` | /** Register a module for use in DAG execution. |
| `resumeFromStore` | `(handle: SuspensionHandle, additionalInputs: Map[String, CValue], resolvedNodes: Map[String, CValue], options: ExecutionOptions): IO[DataSignature]` | /** Resume a suspended execution from the SuspensionStore. |
| `resumeFromStore$default$4` | `(): ExecutionOptions` | /** Resume a suspended execution from the SuspensionStore. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `suspensionStore` | `(): Option` | /** Access the optional suspension store. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `resumeFromStore$default$3` | `(): Map` | /** Resume a suspended execution from the SuspensionStore. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `getModuleByName` | `(name: String): IO[Option[Uninitialized]]` | /** Look up a registered module by name. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `removeModule` | `(name: String): IO[Unit]` | /** Remove a module by name. |
| `run` | `(ref: String, inputs: Map[String, CValue], options: ExecutionOptions): IO[DataSignature]` | /** Execute a pipeline by reference (alias name or "sha256:<hash>"). |
| `run` | `(loaded: LoadedPipeline, inputs: Map[String, CValue], options: ExecutionOptions): IO[DataSignature]` | /** Execute a loaded pipeline with the given inputs. |
| `resumeFromStore$default$2` | `(): Map` | /** Resume a suspended execution from the SuspensionStore. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `publishedModules` | `(): IO` | /** List modules that have been published as HTTP endpoints. |
| `getModules` | `(): IO` | /** List all registered module specifications. */ |
| `getBatchFunctions` | `(moduleNames: List[String]): IO[Map[String, Function1[List[CValue], IO[List[Either[Throwable, CValue]]]]]]` | /** Get batch functions for external modules by qualified name (RFC-034 Phase 1). |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait ConstellationError

/** Base trait for all Constellation domain errors.
  *
  * All custom exceptions in Constellation extend this trait, providing:
  *   - Structured error categorization via sealed hierarchy
  *   - Context maps for debugging information
  *   - Error codes for programmatic handling
  *   - JSON serialization for API responses
  */

**Extends:** Exception

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCause` | `(x$0: Throwable): Unit` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getCause` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `errorCode` | `(): String` | /** Error code for programmatic handling (e.g., "TYPE_MISMATCH", "NODE_NOT_FOUND") */ |
| `category` | `(): String` | /** The error category (type, compiler, or runtime) */ |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `toString` | `(): String` |  |
| `message` | `(): String` | /** Human-readable error message */ |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toJson` | `(): Json` | /** Convert this error to a JSON object for API responses */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `getLocalizedMessage` | `(): String` |  |
| `context` | `(): Map` | /** Additional context for debugging (e.g., field names, node IDs, module names) */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### trait ConversionStrategy

/** Conversion strategy used by AdaptiveJsonConverter */

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
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait CustomJsonCodecs

**Extends:** Object

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
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `evalEncoder` | `[A](encoder: Encoder[A]): Encoder[Eval[Any]]` |  |
| `evalDecoder` | `[A](decoder: Decoder[A]): Decoder[Eval[Any]]` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `uuidMapDecoder` | `[A](decoder: Decoder[A]): Decoder[Map[UUID, Any]]` |  |
| `uuidMapEncoder` | `[A](encoder: Encoder[A]): Encoder[Map[UUID, Any]]` |  |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait DagChange

/** Incremental change operations that can be applied to a [[DagSpec]]. */

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
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait DataNodeSpecBuilder[A]

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
| `build` | `(): Map` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait ExecutionTracker[F]

/** Tracker for capturing per-node execution data during DAG execution.
  *
  * Thread-safe implementation using Ref for concurrent access. Provides LRU eviction to prevent
  * unbounded memory growth.
  */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getAllTraces` | `(): F` | /** Get all active traces (for debugging/monitoring). */ |
| `recordNodeStart` | `(executionId: String, nodeId: String): F[Unit]` | /** Record that a node has started executing. |
| `recordNodeComplete` | `(executionId: String, nodeId: String, value: Json, durationMs: Long): F[Unit]` | /** Record that a node completed successfully. |
| `getTrace` | `(executionId: String): F[Option[ExecutionTrace]]` | /** Get the trace for a specific execution. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `recordNodeFailed` | `(executionId: String, nodeId: String, error: String, durationMs: Long): F[Unit]` | /** Record that a node failed. |
| `clear` | `(): F` | /** Clear all traces (useful for testing). */ |
| `startExecution` | `(dagName: String): F[String]` | /** Start tracking a new execution. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `finishExecution` | `(executionId: String): F[Unit]` | /** Mark an execution as finished. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait InlineTransform

/** Inline transforms are lightweight operations that execute directly on data nodes without the
  * overhead of a full module (no UUID generation, no deferred allocation, no module scheduling).
  *
  * These are used for simple synthetic operations like record merging, field projection, field
  * access, and conditionals.
  *
  * ==Type Safety Note==
  *
  * The `asInstanceOf` casts in this file are **safe by construction**:
  *
  *   - All inline transforms receive inputs from the compiled DAG
  *   - The DagCompiler type-checks all expressions before generating transforms
  *   - Boolean operations (And, Or, Not, Conditional, Guard) only receive Boolean inputs because
  *     the type checker verifies operand types during compilation
  *   - List operations (Filter, Map, All, Any) only receive List inputs because the type checker
  *     verifies the source expression has a List type
  *
  * Runtime type validation can be enabled by setting `CONSTELLATION_DEBUG=true` for development and
  * debugging purposes. See [[io.constellation.DebugMode]].
  *
  * @see
  *   docs/dev/optimizations/04-inline-synthetic-modules.md
  * @see
  *   [[io.constellation.DebugMode]] for optional runtime type validation
  */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(inputs: Map[String, Any]): Any` | /** Apply the transform to the input values. |
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

### trait LazyCValue

/** Lazy CValue that defers JSON conversion until value is accessed. Provides significant
  * performance gains when only a subset of data is used.
  *
  * ==Usage==
  *
  * Lazy values wrap JSON and only convert to CValue when `materialize()` is called:
  * {{{
  * val lazy = LazyJsonValue(json, CType.CString)
  * // No conversion yet
  * val value = lazy.materialize // Conversion happens here
  * }}}
  *
  * For lists, `LazyListValue` converts elements on-demand:
  * {{{
  * val lazyList = LazyListValue(jsonArray, CType.CInt)
  * val first = lazyList.get(0) // Only first element converted
  * }}}
  */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `cType` | `(): CType` | /** Get the expected CType */ |
| `materialize` | `(): Either` | /** Convert to CValue, performing deferred conversion if needed */ |
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

### trait ModuleRegistry

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `deregister` | `(name: String): IO[Unit]` | /** Remove a module by its canonical name. |
| `initModules` | `(spec: DagSpec): IO[Map[UUID, Uninitialized]]` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `get` | `(name: String): IO[Option[Uninitialized]]` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `listModules` | `(): IO` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `register` | `(name: String, node: Uninitialized): IO[Unit]` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait PipelineStatus

/** Status of a pipeline execution.
  *
  * Distinct from [[io.constellation.execution.ExecutionStatus]] which tracks
  * Running/Completed/Cancelled/TimedOut/Failed for CancellableExecution handles. PipelineStatus
  * describes the data-flow outcome: all outputs computed, some outputs pending, or errors
  * encountered.
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
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait PipelineStore

/** Persistent store for compiled pipeline images.
  *
  * Supports storage, retrieval, aliasing (human-readable names), and a syntactic index that maps
  * (syntacticHash, registryHash) pairs to structural hashes for cache-hit detection.
  */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `remove` | `(structuralHash: String): IO[Boolean]` | /** Remove a pipeline image by structural hash. Returns true if found. */ |
| `listAliases` | `(): IO` | /** List all known aliases. */ |
| `resolve` | `(name: String): IO[Option[String]]` | /** Resolve a human-readable alias to a structural hash. */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `indexSyntactic` | `(syntacticHash: String, registryHash: String, structuralHash: String): IO[Unit]` | /** Index a syntactic hash to a structural hash for cache lookups. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `alias` | `(name: String, structuralHash: String): IO[Unit]` | /** Create or update a human-readable alias for a structural hash. */ |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `listImages` | `(): IO` | /** List all stored pipeline images. */ |
| `getByName` | `(name: String): IO[Option[PipelineImage]]` | /** Retrieve a pipeline image by alias name. */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `store` | `(image: PipelineImage): IO[String]` | /** Store a pipeline image. Returns the structural hash as the key. */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `lookupSyntactic` | `(syntacticHash: String, registryHash: String): IO[Option[String]]` | /** Look up a structural hash by syntactic hash and registry hash. */ |
| `get` | `(structuralHash: String): IO[Option[PipelineImage]]` | /** Retrieve a pipeline image by structural hash. */ |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait ProvideOnOutputs[O]

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
| `provideOutputs` | `(namespace: Namespace, runtime: Runtime, outputs: O): IO[Unit]` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait RawValue

/** Unboxed value representation without embedded type metadata.
  *
  * RawValue separates type information from values, storing types once per data node rather than
  * embedded in every value. This significantly reduces memory overhead for large collections,
  * especially in ML workloads.
  *
  * ==Memory Savings==
  *
  * For a list of 10,000 floats:
  *   - CListValue[CFloatValue]: ~240KB (6x overhead due to object wrappers)
  *   - RFloatList(Array[Double]): ~80KB (unboxed primitives)
  *
  * ==Usage==
  *
  * Type information is stored separately in DataNodeSpec.cType. Use TypedValueAccessor for
  * type-aware operations when needed.
  *
  * @see
  *   [[TypedValueAccessor]] for type-aware operations
  * @see
  *   [[RawValueConverter]] for CValue ↔ RawValue conversion
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
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `toDebugString` | `(): String` | /** Debug string representation that includes value info. For type information, use the CType from |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait RegisterData[T]

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `registerData` | `(namespace: Namespace): IO[MutableDataTable]` |  |
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

### trait ResolutionSource

/** Describes how a particular data node's value was obtained. */

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
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait RuntimeError

/** Errors that occur during pipeline execution at runtime. */

**Extends:** Exception, ConstellationError

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCause` | `(x$0: Throwable): Unit` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getCause` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `errorCode` | `(): String` | /** Error code for programmatic handling (e.g., "TYPE_MISMATCH", "NODE_NOT_FOUND") */ |
| `category` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `toString` | `(): String` |  |
| `message` | `(): String` | /** Human-readable error message */ |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toJson` | `(): Json` | /** Convert this error to a JSON object for API responses */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `getLocalizedMessage` | `(): String` |  |
| `context` | `(): Map` | /** Additional context for debugging (e.g., field names, node IDs, module names) */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

### trait SuspensionCodec

/** Codec for serializing/deserializing [[SuspendedExecution]] snapshots.
  *
  * Implementations should handle the full [[SuspendedExecution]] graph including CValue data,
  * DagSpec topology, and module options.
  */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `decode` | `(bytes: Array[Byte]): Either[CodecError, SuspendedExecution]` | /** Decode a SuspendedExecution from bytes. */ |
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
| `encode` | `(suspended: SuspendedExecution): Either[CodecError, Array[Byte]]` | /** Encode a SuspendedExecution to bytes. */ |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait SuspensionStore

/** Persistence layer for [[SuspendedExecution]] snapshots.
  *
  * Provides save/load/delete/list operations keyed by [[SuspensionHandle]]. Implementations may be
  * in-memory (for dev/test) or backed by a database.
  */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `delete` | `(handle: SuspensionHandle): IO[Boolean]` | /** Delete a stored suspension. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `list` | `(filter: SuspensionFilter): IO[List[SuspensionSummary]]` | /** List stored suspensions matching the given filter. |
| `save` | `(suspended: SuspendedExecution): IO[SuspensionHandle]` | /** Save a suspended execution and return a handle for later retrieval. |
| `list$default$1` | `(): SuspensionFilter` | /** List stored suspensions matching the given filter. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `load` | `(handle: SuspensionHandle): IO[Option[SuspendedExecution]]` | /** Load a suspended execution by handle. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait TypeError

/** Type-related errors that occur during type checking, conversion, or extraction. */

**Extends:** Exception, ConstellationError

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `setCause` | `(x$0: Throwable): Unit` |  |
| `fillInStackTrace` | `(): Throwable` |  |
| `getCause` | `(): Throwable` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `printStackTrace` | `(x$0: PrintWriter): Unit` |  |
| `printStackTrace` | `(x$0: PrintStream): Unit` |  |
| `printStackTrace` | `(): Unit` |  |
| `initCause` | `(x$0: Throwable): Throwable` |  |
| `errorCode` | `(): String` | /** Error code for programmatic handling (e.g., "TYPE_MISMATCH", "NODE_NOT_FOUND") */ |
| `category` | `(): String` |  |
| `setStackTrace` | `(x$0: Array[StackTraceElement]): Unit` |  |
| `getMessage` | `(): String` |  |
| `toString` | `(): String` |  |
| `message` | `(): String` | /** Human-readable error message */ |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `toJson` | `(): Json` | /** Convert this error to a JSON object for API responses */ |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `getLocalizedMessage` | `(): String` |  |
| `context` | `(): Map` | /** Additional context for debugging (e.g., field names, node IDs, module names) */ |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `addSuppressed` | `(x$0: Throwable): Unit` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getSuppressed` | `(): Array[Throwable]` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `getStackTrace` | `(): Array[StackTraceElement]` |  |

## Enums

### enum DebugLevel

/** Debug level for runtime type validation.
  *
  * Controls the behavior of runtime type checks in production:
  *   - **Off**: No validation (zero overhead, but risks silent type corruption)
  *   - **ErrorsOnly**: Log validation failures but don't throw (default, minimal overhead)
  *   - **Full**: Throw exceptions on validation failures (development/testing)
  */

**Cases:**

| Case | Parameters |
|------|------------|
| `Off` |  |
| `ErrorsOnly` |  |
| `Full` |  |

### enum NodeStatus

/** Execution status for tracking node execution.
  *
  * This is defined in the runtime module and converted to DagVizIR.ExecutionStatus by the LSP.
  */

**Cases:**

| Case | Parameters |
|------|------------|
| `Pending` |  |
| `Running` |  |
| `Completed` |  |
| `Failed` |  |

<!-- END GENERATED -->
