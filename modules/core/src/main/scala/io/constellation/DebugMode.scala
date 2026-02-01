package io.constellation

import scala.reflect.ClassTag

/** Debug level for runtime type validation.
  *
  * Controls the behavior of runtime type checks in production:
  * - **Off**: No validation (zero overhead, but risks silent type corruption)
  * - **ErrorsOnly**: Log validation failures but don't throw (default, minimal overhead)
  * - **Full**: Throw exceptions on validation failures (development/testing)
  */
enum DebugLevel:
  case Off, ErrorsOnly, Full

  /** Whether any validation is enabled (ErrorsOnly or Full). */
  def isEnabled: Boolean = this != Off

  /** Whether validation should throw exceptions (Full mode). */
  def shouldThrow: Boolean = this == Full

object DebugLevel {
  /** Parse debug level from environment variable string. */
  def fromString(s: String): Option[DebugLevel] = s.toLowerCase match {
    case "off" | "false" | "0" => Some(Off)
    case "errors" | "errorsonly" | "errors-only" => Some(ErrorsOnly)
    case "full" | "true" | "1" => Some(Full)
    case _ => None
  }
}

/** Debug Mode Utilities for Constellation Engine
  *
  * Provides runtime type validation with configurable levels. By default, runs in
  * **ErrorsOnly** mode to catch type safety violations in production while minimizing
  * overhead.
  *
  * ==Configuration Levels==
  *
  * Set via `CONSTELLATION_DEBUG` environment variable:
  *
  * | Value | Level | Behavior |
  * |-------|-------|----------|
  * | `off`, `false`, `0` | Off | No validation (zero overhead) |
  * | `errors`, `errorsonly` | ErrorsOnly | Log violations, don't throw (default) |
  * | `full`, `true`, `1` | Full | Throw on violations (development) |
  *
  * ==Default Behavior (ErrorsOnly)==
  *
  * When `CONSTELLATION_DEBUG` is not set:
  * - Type violations are logged to stderr
  * - No exceptions are thrown
  * - Minimal performance impact (<1%)
  * - Silent data corruption is prevented
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
  * - Invalid casts are logged to stderr
  * - The cast proceeds anyway (fail-soft)
  * - Allows detection without breaking production
  *
  * In **Full** mode:
  * - Invalid casts throw `TypeMismatchError`
  * - Fail-fast for development/testing
  *
  * In **Off** mode:
  * - Direct `asInstanceOf` call (zero overhead)
  * - No validation
  *
  * ==Safe Casts vs Boundary Validation==
  *
  * Most `asInstanceOf` casts in Constellation are **safe by construction**:
  *
  *   - **DagCompiler casts**: Types are guaranteed by the IR generator and type checker.
  *     The compiler only generates casts when it has proven the types match.
  *
  *   - **InlineTransform casts**: Types come from the compiled DAG specification.
  *     The transform functions receive exactly the types specified in the DAG.
  *
  *   - **Runtime casts**: Types are guaranteed by module specifications and the
  *     type checking phase that runs before execution.
  *
  *   - **RawValue internal casts**: Implementation details where types are known
  *     from the immediately preceding pattern match or construction.
  *
  * **Boundary validation** (in `ExecutionHelper` and `JsonCValueConverter`) is where
  * external data enters the system. These locations perform explicit validation
  * and error handling rather than using casts.
  *
  * ==Performance==
  *
  * | Level | Overhead | When to Use |
  * |-------|----------|-------------|
  * | Off | 0% | Production (after thorough testing) |
  * | ErrorsOnly | <1% | Production (default, recommended) |
  * | Full | ~2-5% | Development, testing, debugging |
  *
  * @see [[io.constellation.TypeMismatchError]] for the error thrown in Full mode
  */
object DebugMode {

  /** Current debug level. Reads from CONSTELLATION_DEBUG environment variable.
    *
    * This is evaluated once at class load time and cached for performance.
    *
    * Default: ErrorsOnly (logs violations but doesn't throw)
    */
  val level: DebugLevel =
    sys.env.get("CONSTELLATION_DEBUG")
      .flatMap(DebugLevel.fromString)
      .getOrElse(DebugLevel.ErrorsOnly)

  /** Whether debug mode is enabled (ErrorsOnly or Full).
    *
    * Provided for backward compatibility.
    */
  val isEnabled: Boolean = level.isEnabled

  /** Performs a type cast with configurable runtime validation.
    *
    * Behavior depends on debug level:
    * - **Off**: Direct `asInstanceOf[T]` (zero overhead)
    * - **ErrorsOnly**: Log violation, then cast anyway (fail-soft, default)
    * - **Full**: Throw TypeMismatchError on violation (fail-fast)
    *
    * @tparam T The target type to cast to
    * @param value The value to cast
    * @param context A description of where this cast occurs (for error messages)
    * @param tag Implicit ClassTag for runtime type checking (only used when enabled)
    * @return The value cast to type T
    * @throws TypeMismatchError if in Full mode and the cast is invalid
    *
    * @example {{{
    * // Safe cast with context for debugging
    * val lambda = safeCast[TypedExpression.Lambda](
    *   args(lambdaArgIndex),
    *   "HOF lambda argument extraction"
    * )
    * }}}
    */
  inline def safeCast[T](value: Any, context: String)(using tag: ClassTag[T]): T =
    level match {
      case DebugLevel.Off =>
        value.asInstanceOf[T]

      case DebugLevel.ErrorsOnly =>
        if (value == null) {
          value.asInstanceOf[T] // null can be cast to any reference type
        } else if (!tag.runtimeClass.isInstance(value)) {
          // Log the violation but proceed with the cast
          System.err.println(
            s"[WARN] Type cast violation in $context: expected ${tag.runtimeClass.getSimpleName}, got ${value.getClass.getSimpleName}"
          )
          value.asInstanceOf[T]
        } else {
          value.asInstanceOf[T]
        }

      case DebugLevel.Full =>
        if (value == null) {
          value.asInstanceOf[T]
        } else if (!tag.runtimeClass.isInstance(value)) {
          throw TypeMismatchError(
            expected = tag.runtimeClass.getSimpleName,
            actual = value.getClass.getSimpleName,
            context = Map("location" -> context, "value" -> value.toString.take(100))
          )
        } else {
          value.asInstanceOf[T]
        }
    }

  /** Performs a type cast with optional runtime validation, using a custom expected type name.
    *
    * Use this variant when the ClassTag doesn't provide a meaningful type name
    * (e.g., for type aliases or complex generic types).
    *
    * @tparam T The target type to cast to
    * @param value The value to cast
    * @param expectedTypeName Human-readable name of the expected type for error messages
    * @param context A description of where this cast occurs
    * @param tag Implicit ClassTag for runtime type checking
    * @return The value cast to type T
    * @throws TypeMismatchError if in Full mode and the cast is invalid
    */
  inline def safeCastNamed[T](value: Any, expectedTypeName: String, context: String)(using
      tag: ClassTag[T]
  ): T =
    level match {
      case DebugLevel.Off =>
        value.asInstanceOf[T]

      case DebugLevel.ErrorsOnly =>
        if (value == null) {
          value.asInstanceOf[T]
        } else if (!tag.runtimeClass.isInstance(value)) {
          System.err.println(
            s"[WARN] Type cast violation in $context: expected $expectedTypeName, got ${value.getClass.getSimpleName}"
          )
          value.asInstanceOf[T]
        } else {
          value.asInstanceOf[T]
        }

      case DebugLevel.Full =>
        if (value == null) {
          value.asInstanceOf[T]
        } else if (!tag.runtimeClass.isInstance(value)) {
          throw TypeMismatchError(
            expected = expectedTypeName,
            actual = value.getClass.getSimpleName,
            context = Map("location" -> context)
          )
        } else {
          value.asInstanceOf[T]
        }
    }

  /** Validates a condition in debug mode only.
    *
    * When debug mode is Off, this is a no-op with zero runtime cost.
    * In ErrorsOnly mode, logs the failure but doesn't throw.
    * In Full mode, throws an exception.
    *
    * @param condition The condition to check (only evaluated in debug mode)
    * @param message Error message if the condition is false
    * @param context Context for the error
    * @throws RuntimeException if in Full mode and condition is false
    */
  inline def debugAssert(condition: => Boolean, message: => String, context: => String): Unit =
    level match {
      case DebugLevel.Off => // no-op

      case DebugLevel.ErrorsOnly =>
        if (!condition) {
          System.err.println(s"[WARN] Debug assertion failed in $context: $message")
        }

      case DebugLevel.Full =>
        if (!condition) {
          throw new RuntimeException(s"Debug assertion failed in $context: $message")
        }
    }

  /** Logs a message in debug mode only.
    *
    * When debug mode is Off, this is a no-op with zero runtime cost.
    * In ErrorsOnly or Full mode, logs to stderr.
    *
    * @param message The message to log (only evaluated in debug mode)
    */
  inline def debugLog(message: => String): Unit =
    if (level.isEnabled) {
      System.err.println(s"[CONSTELLATION_DEBUG] $message")
    }
}
