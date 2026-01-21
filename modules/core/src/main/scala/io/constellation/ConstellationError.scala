package io.constellation

import java.util.UUID
import io.circe.{Encoder, Json}
import io.circe.syntax._

/** Constellation Error Hierarchy
  *
  * This module defines a structured exception hierarchy for domain-specific errors in Constellation
  * Engine. All errors extend a common base trait and include context maps for debugging.
  *
  * ==Error Categories==
  *
  * {{{
  * ConstellationError (base trait)
  * ├── TypeError          - Type system and type conversion errors
  * │   ├── TypeMismatchError
  * │   └── TypeConversionError
  * ├── CompilerError      - Compilation and IR generation errors
  * │   ├── NodeNotFoundError
  * │   ├── UndefinedVariableError
  * │   ├── CycleDetectedError
  * │   └── UnsupportedOperationError
  * └── RuntimeError       - Execution and runtime errors
  *     ├── ModuleNotFoundError
  *     ├── ModuleExecutionError
  *     ├── InputValidationError
  *     └── DataNotFoundError
  * }}}
  *
  * ==Usage==
  * {{{
  * // Throwing a typed error
  * throw TypeMismatchError(
  *   expected = CType.CString,
  *   actual = CType.CInt,
  *   context = Map("field" -> "name", "module" -> "Uppercase")
  * )
  *
  * // Pattern matching on errors
  * error match {
  *   case e: TypeError    => handleTypeError(e)
  *   case e: CompilerError => handleCompilerError(e)
  *   case e: RuntimeError  => handleRuntimeError(e)
  * }
  *
  * // JSON serialization for API responses
  * val json = error.toJson
  * }}}
  *
  * @see
  *   [[io.constellation.TypeSystem]] for type definitions
  */

/** Base trait for all Constellation domain errors.
  *
  * All custom exceptions in Constellation extend this trait, providing:
  *   - Structured error categorization via sealed hierarchy
  *   - Context maps for debugging information
  *   - Error codes for programmatic handling
  *   - JSON serialization for API responses
  */
sealed trait ConstellationError extends Exception {

  /** Human-readable error message */
  def message: String

  /** Error code for programmatic handling (e.g., "TYPE_MISMATCH", "NODE_NOT_FOUND") */
  def errorCode: String

  /** Additional context for debugging (e.g., field names, node IDs, module names) */
  def context: Map[String, String]

  /** The error category (type, compiler, or runtime) */
  def category: String

  override def getMessage: String = {
    val contextStr =
      if (context.isEmpty) ""
      else context.map { case (k, v) => s"$k=$v" }.mkString(" [", ", ", "]")
    s"[$errorCode] $message$contextStr"
  }

  /** Convert this error to a JSON object for API responses */
  def toJson: Json = Json.obj(
    "error"    -> Json.fromString(errorCode),
    "category" -> Json.fromString(category),
    "message"  -> Json.fromString(message),
    "context"  -> context.asJson
  )
}

object ConstellationError {

  /** Encoder for serializing ConstellationError to JSON */
  given Encoder[ConstellationError] = Encoder.instance(_.toJson)
}

// =============================================================================
// Type Errors - Type system and type conversion errors
// =============================================================================

/** Type-related errors that occur during type checking, conversion, or extraction. */
sealed trait TypeError extends ConstellationError {
  override def category: String = "type"
}

/** Error when a value's type doesn't match the expected type.
  *
  * @param expected
  *   The expected CType
  * @param actual
  *   The actual CType or value description
  * @param context
  *   Additional debugging context
  */
final case class TypeMismatchError(
    expected: String,
    actual: String,
    context: Map[String, String] = Map.empty
) extends TypeError {
  override def errorCode: String = "TYPE_MISMATCH"
  override def message: String   = s"Expected $expected, but got $actual"
}

object TypeMismatchError {

  /** Create a TypeMismatchError from CType values */
  def fromTypes(expected: CType, actual: CType, ctx: Map[String, String] = Map.empty): TypeMismatchError =
    TypeMismatchError(expected.toString, actual.toString, ctx)

  /** Create a TypeMismatchError from CValue */
  def fromValue(expected: String, actual: CValue, ctx: Map[String, String] = Map.empty): TypeMismatchError =
    TypeMismatchError(expected, s"${actual.getClass.getSimpleName}(${actual.ctype})", ctx)
}

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
final case class TypeConversionError(
    from: String,
    to: String,
    reason: String,
    context: Map[String, String] = Map.empty
) extends TypeError {
  override def errorCode: String = "TYPE_CONVERSION"
  override def message: String   = s"Cannot convert from $from to $to: $reason"
}

// =============================================================================
// Compiler Errors - Compilation and IR generation errors
// =============================================================================

/** Errors that occur during compilation, IR generation, or semantic analysis. */
sealed trait CompilerError extends ConstellationError {
  override def category: String = "compiler"
}

/** Error when a referenced node is not found in the DAG.
  *
  * @param nodeId
  *   The ID of the missing node
  * @param nodeType
  *   Description of the node type (e.g., "input", "source", "condition")
  * @param context
  *   Additional debugging context
  */
final case class NodeNotFoundError(
    nodeId: UUID,
    nodeType: String,
    context: Map[String, String] = Map.empty
) extends CompilerError {
  override def errorCode: String = "NODE_NOT_FOUND"
  override def message: String   = s"$nodeType node $nodeId not found"
}

object NodeNotFoundError {

  def input(nodeId: UUID, ctx: Map[String, String] = Map.empty): NodeNotFoundError =
    NodeNotFoundError(nodeId, "Input", ctx)

  def source(nodeId: UUID, ctx: Map[String, String] = Map.empty): NodeNotFoundError =
    NodeNotFoundError(nodeId, "Source", ctx)

  def condition(nodeId: UUID, ctx: Map[String, String] = Map.empty): NodeNotFoundError =
    NodeNotFoundError(nodeId, "Condition", ctx)

  def expression(nodeId: UUID, ctx: Map[String, String] = Map.empty): NodeNotFoundError =
    NodeNotFoundError(nodeId, "Expression", ctx)
}

/** Error when a variable is referenced but not defined.
  *
  * @param variableName
  *   The name of the undefined variable
  * @param context
  *   Additional debugging context
  */
final case class UndefinedVariableError(
    variableName: String,
    context: Map[String, String] = Map.empty
) extends CompilerError {
  override def errorCode: String = "UNDEFINED_VARIABLE"
  override def message: String   = s"Undefined variable: $variableName"
}

/** Error when a cycle is detected in the DAG.
  *
  * @param nodeIds
  *   The nodes involved in the cycle (if available)
  * @param context
  *   Additional debugging context
  */
final case class CycleDetectedError(
    nodeIds: List[UUID] = Nil,
    context: Map[String, String] = Map.empty
) extends CompilerError {
  override def errorCode: String = "CYCLE_DETECTED"
  override def message: String = {
    if (nodeIds.isEmpty) "Cycle detected in DAG"
    else s"Cycle detected in DAG involving nodes: ${nodeIds.mkString(", ")}"
  }
}

/** Error when an unsupported operation is encountered.
  *
  * @param operation
  *   Description of the unsupported operation
  * @param reason
  *   Why the operation is not supported
  * @param context
  *   Additional debugging context
  */
final case class UnsupportedOperationError(
    operation: String,
    reason: String,
    context: Map[String, String] = Map.empty
) extends CompilerError {
  override def errorCode: String = "UNSUPPORTED_OPERATION"
  override def message: String   = s"Unsupported operation '$operation': $reason"
}

// =============================================================================
// Runtime Errors - Execution and runtime errors
// =============================================================================

/** Errors that occur during pipeline execution at runtime. */
sealed trait RuntimeError extends ConstellationError {
  override def category: String = "runtime"
}

/** Error when a module is not found in the namespace.
  *
  * @param moduleName
  *   The name of the missing module
  * @param context
  *   Additional debugging context
  */
final case class ModuleNotFoundError(
    moduleName: String,
    context: Map[String, String] = Map.empty
) extends RuntimeError {
  override def errorCode: String = "MODULE_NOT_FOUND"
  override def message: String   = s"Module '$moduleName' not found in namespace"
}

/** Error when a module fails during execution.
  *
  * @param moduleName
  *   The name of the module that failed
  * @param cause
  *   The underlying exception (if any)
  * @param context
  *   Additional debugging context
  */
final case class ModuleExecutionError(
    moduleName: String,
    cause: Option[Throwable],
    context: Map[String, String] = Map.empty
) extends RuntimeError {
  override def errorCode: String = "MODULE_EXECUTION"
  override def message: String = {
    val causeMsg = cause.map(e => s": ${e.getMessage}").getOrElse("")
    s"Module '$moduleName' execution failed$causeMsg"
  }

  override def getCause: Throwable = cause.orNull
}

object ModuleExecutionError {

  def apply(moduleName: String, cause: Throwable): ModuleExecutionError =
    ModuleExecutionError(moduleName, Some(cause), Map.empty)

  def apply(moduleName: String, cause: Throwable, ctx: Map[String, String]): ModuleExecutionError =
    ModuleExecutionError(moduleName, Some(cause), ctx)
}

/** Error when input validation fails.
  *
  * @param inputName
  *   The name of the input that failed validation
  * @param reason
  *   Why validation failed
  * @param context
  *   Additional debugging context
  */
final case class InputValidationError(
    inputName: String,
    reason: String,
    context: Map[String, String] = Map.empty
) extends RuntimeError {
  override def errorCode: String = "INPUT_VALIDATION"
  override def message: String   = s"Input '$inputName' validation failed: $reason"
}

/** Error when data is not found in a runtime table.
  *
  * @param dataId
  *   The ID of the missing data
  * @param dataType
  *   Description of the data type (e.g., "data", "deferred")
  * @param context
  *   Additional debugging context
  */
final case class DataNotFoundError(
    dataId: UUID,
    dataType: String,
    context: Map[String, String] = Map.empty
) extends RuntimeError {
  override def errorCode: String = "DATA_NOT_FOUND"
  override def message: String   = s"$dataType with ID $dataId not found"
}

object DataNotFoundError {

  def data(dataId: UUID, ctx: Map[String, String] = Map.empty): DataNotFoundError =
    DataNotFoundError(dataId, "Data", ctx)

  def deferred(dataId: UUID, ctx: Map[String, String] = Map.empty): DataNotFoundError =
    DataNotFoundError(dataId, "Deferred", ctx)
}

/** Error when the runtime is not properly initialized.
  *
  * @param reason
  *   What is not initialized
  * @param context
  *   Additional debugging context
  */
final case class RuntimeNotInitializedError(
    reason: String,
    context: Map[String, String] = Map.empty
) extends RuntimeError {
  override def errorCode: String = "RUNTIME_NOT_INITIALIZED"
  override def message: String   = s"Runtime not initialized: $reason"
}

/** Error when validation fails (e.g., DAG validation).
  *
  * @param errors
  *   List of validation error messages
  * @param context
  *   Additional debugging context
  */
final case class ValidationError(
    errors: List[String],
    context: Map[String, String] = Map.empty
) extends RuntimeError {
  override def errorCode: String = "VALIDATION_ERROR"
  override def message: String   = errors.mkString("; ")
}
