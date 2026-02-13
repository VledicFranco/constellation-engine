<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/core/src/main/scala/io/constellation -->
<!-- Hash: 479214b49771 -->
<!-- Generated: 2026-02-13T07:57:34.015713900Z -->

# io.constellation.lang.ast

## Objects

### Annotation$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
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

### ArithOp$

/** Arithmetic operators */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `values` | `(): Array` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `fromOrdinal` | `(ordinal: Int): ArithOp` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `valueOf` | `($name: String): ArithOp` |  |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### BackoffStrategy$

/** Backoff strategy for retry delays */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `values` | `(): Array` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `fromOrdinal` | `(ordinal: Int): BackoffStrategy` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `valueOf` | `($name: String): BackoffStrategy` |  |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### BoolOp$

/** Boolean operators */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `values` | `(): Array` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `fromOrdinal` | `(ordinal: Int): BoolOp` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `valueOf` | `($name: String): BoolOp` |  |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CompareOp$

/** Comparison operators */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `values` | `(): Array` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `fromOrdinal` | `(ordinal: Int): CompareOp` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `valueOf` | `($name: String): CompareOp` |  |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CompileError$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
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

### CompileWarning$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### CustomPriority$

/** Custom numeric priority (higher = more important) */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: CustomPriority): CustomPriority` |  |
| `apply` | `(value: Int): CustomPriority` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
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

### Declaration$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
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

### Duration$

/** Duration value (e.g., 30s, 5min, 1h) */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: Duration): Duration` |  |
| `apply` | `(value: Long, unit: DurationUnit): Duration` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
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

### DurationUnit$

/** Duration unit for timeout, delay, cache options */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `values` | `(): Array` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `valueOf` | `($name: String): DurationUnit` |  |
| `fromOrdinal` | `(ordinal: Int): DurationUnit` |  |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### ErrorStrategy$

/** Error handling strategy */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `values` | `(): Array` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `fromOrdinal` | `(ordinal: Int): ErrorStrategy` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `valueOf` | `($name: String): ErrorStrategy` |  |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### Expression$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
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

### LineCol$

/** Line and column position (1-based, for display) */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: LineCol): LineCol` |  |
| `apply` | `(line: Int, col: Int): LineCol` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
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

### LineMap$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: LineMap): LineMap` |  |
| `apply` | `(lineStarts: Array[Int]): LineMap` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `fromSource` | `(content: String): LineMap` |  |
| `fromProduct` | `(x$0: Product): MirroredMonoType` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### Located$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `[A](x$1: Located[A]): Located[Any]` |  |
| `apply` | `[A](value: A, span: Span): Located[Any]` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
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

### MatchCase$

/** A single case in a match expression */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: MatchCase): MatchCase` |  |
| `apply` | `(pattern: Located[Pattern], body: Located[Expression]): MatchCase` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
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

### ModuleCallOptions$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `apply` | `(retry: Option[Int], timeout: Option[Duration], delay: Option[Duration], backoff: Option[BackoffStrategy], fallback: Option[Located[Expression]], cache: Option[Duration], cacheBackend: Option[String], throttle: Option[Rate], concurrency: Option[Int], onError: Option[ErrorStrategy], lazyEval: Option[Boolean], priority: Option[Either[PriorityLevel, CustomPriority]]): ModuleCallOptions` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `unapply` | `(x$1: ModuleCallOptions): ModuleCallOptions` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
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

### Pattern$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### Pipeline$

/** A complete constellation-lang pipeline */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: Pipeline): Pipeline` |  |
| `apply` | `(declarations: List[Declaration], outputs: List[Located[String]]): Pipeline` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
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

### PriorityLevel$

/** Priority level for scheduling hints */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `values` | `(): Array` |  |
| `fromOrdinal` | `(ordinal: Int): PriorityLevel` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `valueOf` | `($name: String): PriorityLevel` |  |
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

### QualifiedName$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: QualifiedName): QualifiedName` |  |
| `simple` | `(name: String): QualifiedName` | /** Create a simple (non-qualified) name */ |
| `apply` | `(parts: List[String]): QualifiedName` |  |
| `fromString` | `(s: String): QualifiedName` | /** Parse a qualified name from a dotted string */ |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
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

### Rate$

/** Rate value for throttle option (e.g., 100/1min = 100 per minute) */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: Rate): Rate` |  |
| `apply` | `(count: Int, per: Duration): Rate` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
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

### SourceFile$

/** Source file with efficient span→line/col conversion */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: SourceFile): SourceFile` |  |
| `apply` | `(name: String, content: String): SourceFile` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
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

### Span$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `unapply` | `(x$1: Span): Span` |  |
| `apply` | `(start: Int, end: Int): Span` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `point` | `(offset: Int): Span` | /** Helper to create span from single offset */ |
| `toString` | `(): String` | /** Returns a string representation of the object. |
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

### TypeExpr$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `ordinal` | `(x$0: MirroredMonoType): Int` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
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

## Classes

### case class CustomPriority

/** Custom numeric priority (higher = more important) */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `value` | `Int` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productPrefix` | `(): String` |  |
| `productArity` | `(): Int` |  |
| `_1` | `(): Int` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `productElementNames` | `(): Iterator` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `copy$default$1` | `(): Int` |  |
| `copy` | `(value: Int): CustomPriority` |  |
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
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class Duration

/** Duration value (e.g., 30s, 5min, 1h) */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `value` | `Long` |  |
| `unit` | `DurationUnit` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productPrefix` | `(): String` |  |
| `productArity` | `(): Int` |  |
| `_1` | `(): Long` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `productElementNames` | `(): Iterator` |  |
| `copy$default$1` | `(): Long` |  |
| `copy` | `(value: Long, unit: DurationUnit): Duration` |  |
| `copy$default$2` | `(): DurationUnit` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toMillis` | `(): Long` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): DurationUnit` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class LineCol

/** Line and column position (1-based, for display) */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `line` | `Int` |  |
| `col` | `Int` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productPrefix` | `(): String` |  |
| `productArity` | `(): Int` |  |
| `_1` | `(): Int` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `productElementNames` | `(): Iterator` |  |
| `copy$default$1` | `(): Int` |  |
| `copy` | `(line: Int, col: Int): LineCol` |  |
| `copy$default$2` | `(): Int` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Int` |  |
| `productElement` | `(n: Int): Any` |  |
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

### case class LineMap

/** Efficient mapping from byte offsets to line/column positions */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `lineStarts` | `Array[Int]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productPrefix` | `(): String` |  |
| `productArity` | `(): Int` |  |
| `lineCount` | `(): Int` |  |
| `_1` | `(): Array` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `copy$default$1` | `(): Array` |  |
| `copy` | `(lineStarts: Array[Int]): LineMap` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `canEqual` | `(that: Any): Boolean` |  |
| `toString` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `offsetToLineCol` | `(offset: Int): LineCol` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `productElementNames` | `(): Iterator` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class Located[A]

/** A value with its source span */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `value` | `A` |  |
| `span` | `Span` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productPrefix` | `(): String` |  |
| `productArity` | `(): Int` |  |
| `_1` | `(): A` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `productElementNames` | `(): Iterator` |  |
| `copy$default$1` | `[A](): A` |  |
| `copy` | `[A](value: A, span: Span): Located[Any]` |  |
| `copy$default$2` | `[A](): Span` |  |
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
| `_2` | `(): Span` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `map` | `[B](f: Function1[A, B]): Located[Any]` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class MatchCase

/** A single case in a match expression */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `pattern` | `Located[Pattern]` |  |
| `body` | `Located[Expression]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productPrefix` | `(): String` |  |
| `productArity` | `(): Int` |  |
| `_1` | `(): Located` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `productElementNames` | `(): Iterator` |  |
| `copy$default$1` | `(): Located` |  |
| `copy` | `(pattern: Located[Pattern], body: Located[Expression]): MatchCase` |  |
| `copy$default$2` | `(): Located` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `canEqual` | `(that: Any): Boolean` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Located` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class ModuleCallOptions

/** Module call options from `with` clause
  *
  * Example: `result = MyModule(input) with retry: 3, timeout: 30s, cache: 5min`
  */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `retry` | `Option[Int]` |  |
| `timeout` | `Option[Duration]` |  |
| `delay` | `Option[Duration]` |  |
| `backoff` | `Option[BackoffStrategy]` |  |
| `fallback` | `Option[Located[Expression]]` |  |
| `cache` | `Option[Duration]` |  |
| `cacheBackend` | `Option[String]` |  |
| `throttle` | `Option[Rate]` |  |
| `concurrency` | `Option[Int]` |  |
| `onError` | `Option[ErrorStrategy]` |  |
| `lazyEval` | `Option[Boolean]` |  |
| `priority` | `Option[Either[PriorityLevel, CustomPriority]]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `copy$default$10` | `(): Option` |  |
| `copy$default$9` | `(): Option` |  |
| `_1` | `(): Option` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `productElementNames` | `(): Iterator` |  |
| `copy$default$1` | `(): Option` |  |
| `_8` | `(): Option` |  |
| `_3` | `(): Option` |  |
| `productPrefix` | `(): String` |  |
| `_9` | `(): Option` |  |
| `_11` | `(): Option` |  |
| `_5` | `(): Option` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `_7` | `(): Option` |  |
| `productArity` | `(): Int` |  |
| `copy$default$7` | `(): Option` |  |
| `copy$default$12` | `(): Option` |  |
| `_6` | `(): Option` |  |
| `isEmpty` | `(): Boolean` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `_12` | `(): Option` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `copy$default$8` | `(): Option` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `copy$default$11` | `(): Option` |  |
| `_2` | `(): Option` |  |
| `_4` | `(): Option` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `copy` | `(retry: Option[Int], timeout: Option[Duration], delay: Option[Duration], backoff: Option[BackoffStrategy], fallback: Option[Located[Expression]], cache: Option[Duration], cacheBackend: Option[String], throttle: Option[Rate], concurrency: Option[Int], onError: Option[ErrorStrategy], lazyEval: Option[Boolean], priority: Option[Either[PriorityLevel, CustomPriority]]): ModuleCallOptions` |  |
| `hashCode` | `(): Int` |  |
| `copy$default$4` | `(): Option` |  |
| `copy$default$6` | `(): Option` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `copy$default$2` | `(): Option` |  |
| `copy$default$3` | `(): Option` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `copy$default$5` | `(): Option` |  |
| `_10` | `(): Option` |  |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class Pipeline

/** A complete constellation-lang pipeline */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `declarations` | `List[Declaration]` |  |
| `outputs` | `List[Located[String]]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productPrefix` | `(): String` |  |
| `productArity` | `(): Int` |  |
| `_1` | `(): List` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `productElementNames` | `(): Iterator` |  |
| `copy$default$1` | `(): List` |  |
| `copy` | `(declarations: List[Declaration], outputs: List[Located[String]]): Pipeline` |  |
| `copy$default$2` | `(): List` |  |
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
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class QualifiedName

/** Qualified name for namespace support (e.g., "stdlib.math.add") */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `parts` | `List[String]` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `localName` | `(): String` | /** The local name (last part) */ |
| `productPrefix` | `(): String` |  |
| `namespace` | `(): Option` | /** The namespace portion (all parts except the last) */ |
| `productArity` | `(): Int` |  |
| `_1` | `(): List` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `productElementNames` | `(): Iterator` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `copy$default$1` | `(): List` |  |
| `copy` | `(parts: List[String]): QualifiedName` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `canEqual` | `(that: Any): Boolean` |  |
| `isSimple` | `(): Boolean` | /** True if this is a simple name without namespace */ |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `fullName` | `(): String` | /** The fully qualified name */ |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class Rate

/** Rate value for throttle option (e.g., 100/1min = 100 per minute) */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `count` | `Int` |  |
| `per` | `Duration` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productPrefix` | `(): String` |  |
| `productArity` | `(): Int` |  |
| `_1` | `(): Int` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `productElementNames` | `(): Iterator` |  |
| `copy$default$1` | `(): Int` |  |
| `copy` | `(count: Int, per: Duration): Rate` |  |
| `copy$default$2` | `(): Duration` |  |
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
| `_2` | `(): Duration` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class SourceFile

/** Source file with efficient span→line/col conversion */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` |  |
| `content` | `String` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productArity` | `(): Int` |  |
| `_1` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `productElementNames` | `(): Iterator` |  |
| `copy$default$1` | `(): String` |  |
| `copy` | `(name: String, content: String): SourceFile` |  |
| `extractSnippet` | `(span: Span): String` |  |
| `copy$default$2` | `(): String` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `spanToLineCol` | `(span: Span): Tuple2[LineCol, LineCol]` |  |
| `productPrefix` | `(): String` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `extractLine` | `(line: Int): String` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): String` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### case class Span

/** Source span representing a range in the source code (byte offsets) */

**Extends:** Product, Serializable

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `start` | `Int` |  |
| `end` | `Int` |  |

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `productPrefix` | `(): String` |  |
| `contains` | `(offset: Int): Boolean` |  |
| `productArity` | `(): Int` |  |
| `_1` | `(): Int` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `productElementNames` | `(): Iterator` |  |
| `copy$default$1` | `(): Int` |  |
| `copy` | `(start: Int, end: Int): Span` |  |
| `point` | `(): Int` |  |
| `copy$default$2` | `(): Int` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `length` | `(): Int` |  |
| `canEqual` | `(that: Any): Boolean` |  |
| `isEmpty` | `(): Boolean` |  |
| `toString` | `(): String` |  |
| `productElementName` | `(n: Int): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `_2` | `(): Int` |  |
| `productElement` | `(n: Int): Any` |  |
| `equals` | `(x$0: Any): Boolean` |  |
| `hashCode` | `(): Int` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `productIterator` | `(): Iterator` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

## Traits

### trait Annotation

/** Annotations provide metadata for declarations */

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

### trait CompileError

/** Compile errors with span information */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `formatWithSource` | `(source: SourceFile): String` |  |
| `span` | `(): Option` |  |
| `format` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `message` | `(): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait CompileWarning

/** Compile warnings - non-fatal diagnostics */

**Extends:** Object

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `span` | `(): Option` |  |
| `format` | `(): String` |  |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `message` | `(): String` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

### trait Declaration

/** Top-level declarations */

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

### trait Expression

/** Expressions */

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

### trait Pattern

/** Patterns for match expressions */

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

### trait TypeExpr

/** Type expressions */

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

## Enums

### enum ArithOp

/** Arithmetic operators */

**Cases:**

| Case | Parameters |
|------|------------|
| `Add` |  |
| `Sub` |  |
| `Mul` |  |
| `Div` |  |

### enum BackoffStrategy

/** Backoff strategy for retry delays */

**Cases:**

| Case | Parameters |
|------|------------|
| `Fixed` |  |
| `Linear` |  |
| `Exponential` |  |

### enum BoolOp

/** Boolean operators */

**Cases:**

| Case | Parameters |
|------|------------|
| `And` |  |
| `Or` |  |

### enum CompareOp

/** Comparison operators */

**Cases:**

| Case | Parameters |
|------|------------|
| `Eq` |  |
| `NotEq` |  |
| `Lt` |  |
| `Gt` |  |
| `LtEq` |  |
| `GtEq` |  |

### enum DurationUnit

/** Duration unit for timeout, delay, cache options */

**Cases:**

| Case | Parameters |
|------|------------|
| `Milliseconds` |  |
| `Seconds` |  |
| `Minutes` |  |
| `Hours` |  |
| `Days` |  |

### enum ErrorStrategy

/** Error handling strategy */

**Cases:**

| Case | Parameters |
|------|------------|
| `Propagate` |  |
| `Skip` |  |
| `Log` |  |
| `Wrap` |  |

### enum PriorityLevel

/** Priority level for scheduling hints */

**Cases:**

| Case | Parameters |
|------|------------|
| `Critical` |  |
| `High` |  |
| `Normal` |  |
| `Low` |  |
| `Background` |  |

<!-- END GENERATED -->
