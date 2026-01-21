package io.constellation

import scala.reflect.ClassTag

/** Debug Mode Utilities for Constellation Engine
  *
  * Provides optional runtime type validation that can be enabled during development
  * and debugging. When disabled (default), all operations have zero runtime cost.
  *
  * ==Enabling Debug Mode==
  *
  * Set the environment variable `CONSTELLATION_DEBUG=true` to enable debug mode:
  *
  * {{{
  * # Linux/macOS
  * export CONSTELLATION_DEBUG=true
  * sbt run
  *
  * # Windows PowerShell
  * $env:CONSTELLATION_DEBUG = "true"
  * sbt run
  *
  * # Windows CMD
  * set CONSTELLATION_DEBUG=true
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
  * When debug mode is enabled, this will throw a `TypeMismatchError` with context
  * if the cast would fail. When disabled, it compiles to a direct `asInstanceOf`.
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
  * When `CONSTELLATION_DEBUG` is not set or set to any value other than "true":
  *   - `safeCast` compiles to a direct `asInstanceOf` call
  *   - `isEnabled` is a compile-time constant `false`
  *   - No ClassTag instances are created or retained
  *   - Zero runtime overhead
  *
  * @see [[io.constellation.TypeMismatchError]] for the error thrown in debug mode
  */
object DebugMode {

  /** Whether debug mode is enabled. Reads from CONSTELLATION_DEBUG environment variable.
    *
    * This is evaluated once at class load time and cached for performance.
    */
  val isEnabled: Boolean =
    sys.env.get("CONSTELLATION_DEBUG").exists(_.equalsIgnoreCase("true"))

  /** Performs a type cast with optional runtime validation in debug mode.
    *
    * When debug mode is disabled (default), this compiles to a direct `asInstanceOf[T]`
    * with zero runtime cost.
    *
    * When debug mode is enabled, this validates the cast at runtime and throws a
    * `TypeMismatchError` with contextual information if the cast would fail.
    *
    * @tparam T The target type to cast to
    * @param value The value to cast
    * @param context A description of where this cast occurs (for error messages)
    * @param tag Implicit ClassTag for runtime type checking (only used in debug mode)
    * @return The value cast to type T
    * @throws TypeMismatchError if debug mode is enabled and the cast is invalid
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
    if isEnabled then
      if value == null then
        value.asInstanceOf[T] // null can be cast to any reference type
      else if !tag.runtimeClass.isInstance(value) then
        throw TypeMismatchError(
          expected = tag.runtimeClass.getSimpleName,
          actual = value.getClass.getSimpleName,
          context = Map("location" -> context, "value" -> value.toString.take(100))
        )
      else
        value.asInstanceOf[T]
    else
      value.asInstanceOf[T]

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
    * @throws TypeMismatchError if debug mode is enabled and the cast is invalid
    */
  inline def safeCastNamed[T](value: Any, expectedTypeName: String, context: String)(using
      tag: ClassTag[T]
  ): T =
    if isEnabled then
      if value == null then
        value.asInstanceOf[T]
      else if !tag.runtimeClass.isInstance(value) then
        throw TypeMismatchError(
          expected = expectedTypeName,
          actual = value.getClass.getSimpleName,
          context = Map("location" -> context)
        )
      else
        value.asInstanceOf[T]
    else
      value.asInstanceOf[T]

  /** Validates a condition in debug mode only.
    *
    * When debug mode is disabled, this is a no-op with zero runtime cost.
    *
    * @param condition The condition to check (only evaluated in debug mode)
    * @param message Error message if the condition is false
    * @param context Context for the error
    * @throws RuntimeException if debug mode is enabled and condition is false
    */
  inline def debugAssert(condition: => Boolean, message: => String, context: => String): Unit =
    if isEnabled && !condition then
      throw new RuntimeException(s"Debug assertion failed in $context: $message")

  /** Logs a message in debug mode only.
    *
    * When debug mode is disabled, this is a no-op with zero runtime cost.
    *
    * @param message The message to log (only evaluated in debug mode)
    */
  inline def debugLog(message: => String): Unit =
    if isEnabled then
      System.err.println(s"[CONSTELLATION_DEBUG] $message")
}
