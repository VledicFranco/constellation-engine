<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/core/src/main/scala/io/constellation -->
<!-- Hash: 479214b49771 -->
<!-- Generated: 2026-02-13T07:57:34.003967300Z -->

# io.constellation.errors

## Objects

### ApiError$

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `fromThrowable` | `(prefix: String, t: Throwable): ApiError` | /** Wrap an exception as an API error */ |
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

### ErrorHandling$

/** Error handling utilities for consistent patterns across modules */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `handleNotification` | `(operation: String, logger: Function1[String, IO[Unit]], fa: IO[Unit]): IO[Unit]` | /** Handle errors in notification handlers by logging instead of silently swallowing. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `executeWithResult` | `[A, R](fa: IO[A], onSuccess: Function1[A, R], onError: Function1[Throwable, R]): IO[Any]` | /** Execute an IO and map failure to a result type. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `fromEither` | `[E, A](either: Either[E, A]): EitherT[IO, Any, Any]` | /** Convert an Either value into EitherT */ |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `handleRequestWithLogging` | `[A](operation: String, logger: Function1[String, IO[Unit]], fa: IO[A]): IO[Option[Any]]` | /** Execute an IO operation and convert to Option, logging errors. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `liftIO` | `[E, A](io: IO[A], mapError: Function1[Throwable, E]): EitherT[IO, Any, Any]` | /** Convert an IO operation to EitherT with error mapping */ |
| `pure` | `[E, A](a: A): EitherT[IO, Any, Any]` | /** Lift a pure value into EitherT */ |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `fromOption` | `[E, A](opt: Option[A], ifNone: E): EitherT[IO, Any, Any]` | /** Convert an Option into EitherT with a custom error for None */ |

## Traits

### trait ApiError

/** Standardized error types for HTTP API responses */

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

<!-- END GENERATED -->
