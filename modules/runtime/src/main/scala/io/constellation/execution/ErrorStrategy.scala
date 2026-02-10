package io.constellation.execution

import cats.effect.IO

import io.constellation.{CType, CValue}

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/** Error handling strategies for module execution.
  *
  * These strategies determine what happens when a module fails:
  *
  * | Strategy  | Behavior                              |
  * |:----------|:--------------------------------------|
  * | Propagate | Re-throw the error (default)          |
  * | Skip      | Return zero value for type, continue  |
  * | Log       | Log error and return zero value       |
  * | Wrap      | Wrap result in Either[ModuleError, A] |
  */
sealed trait ErrorStrategy

object ErrorStrategy {

  /** Re-throw the error (default behavior). */
  case object Propagate extends ErrorStrategy

  /** Return zero/default value for the type, continue execution. */
  case object Skip extends ErrorStrategy

  /** Log the error and return zero value. */
  case object Log extends ErrorStrategy

  /** Wrap result in Either for downstream handling. */
  case object Wrap extends ErrorStrategy

  /** Parse error strategy from string. */
  def fromString(s: String): Option[ErrorStrategy] = s.toLowerCase match {
    case "propagate" => Some(Propagate)
    case "skip"      => Some(Skip)
    case "log"       => Some(Log)
    case "wrap"      => Some(Wrap)
    case _           => None
  }
}

/** Error wrapper for Wrap strategy. */
final case class ModuleError(
    moduleName: String,
    error: Throwable,
    timestamp: Long = System.currentTimeMillis()
) {
  def message: String   = error.getMessage
  def errorType: String = error.getClass.getSimpleName

  override def toString: String =
    s"ModuleError($moduleName: $errorType: $message)"
}

/** Executor for error handling strategies. */
object ErrorStrategyExecutor {
  private val logger: Logger[IO] =
    Slf4jLogger.getLoggerFromName[IO]("io.constellation.execution.ErrorStrategyExecutor")

  /** Execute an operation with the specified error strategy.
    *
    * @param operation
    *   The IO operation to execute
    * @param strategy
    *   The error handling strategy
    * @param outputType
    *   The expected output type (for zero value)
    * @param moduleName
    *   The module name (for logging/wrapping)
    * @return
    *   The result according to the strategy
    */
  def execute[A](
      operation: IO[A],
      strategy: ErrorStrategy,
      outputType: CType,
      moduleName: String
  ): IO[Any] =
    strategy match {
      case ErrorStrategy.Propagate =>
        operation

      case ErrorStrategy.Skip =>
        operation.handleError { _ =>
          zeroValue(outputType).asInstanceOf[A]
        }

      case ErrorStrategy.Log =>
        operation.handleErrorWith { error =>
          logger.warn(error)(
            s"[$moduleName] failed: ${error.getMessage}. Skipping with zero value."
          ) >>
            IO.pure(zeroValue(outputType).asInstanceOf[A])
        }

      case ErrorStrategy.Wrap =>
        operation.attempt.map {
          case Right(value) => Right(value)
          case Left(error)  => Left(ModuleError(moduleName, error))
        }
    }

  /** Execute and return typed result for non-Wrap strategies.
    *
    * Use this when you know the strategy is not Wrap.
    */
  def executeTyped[A](
      operation: IO[A],
      strategy: ErrorStrategy,
      outputType: CType,
      moduleName: String
  ): IO[A] = {
    require(strategy != ErrorStrategy.Wrap, "Use execute() for Wrap strategy")
    execute(operation, strategy, outputType, moduleName).asInstanceOf[IO[A]]
  }

  /** Get the zero/default value for a CType.
    *
    * Zero values are type-appropriate defaults:
    *   - String: ""
    *   - Int: 0
    *   - Float: 0.0
    *   - Boolean: false
    *   - List: empty list
    *   - Optional: None
    *   - Product: product with zero values
    *   - Other: None
    */
  def zeroValue(ctype: CType): CValue = ctype match {
    case CType.CString =>
      CValue.CString("")

    case CType.CInt =>
      CValue.CInt(0)

    case CType.CFloat =>
      CValue.CFloat(0.0)

    case CType.CBoolean =>
      CValue.CBoolean(false)

    case CType.CList(elemType) =>
      CValue.CList(Vector.empty, elemType)

    case CType.CMap(keyType, valueType) =>
      CValue.CMap(Vector.empty, keyType, valueType)

    case CType.CProduct(structure) =>
      val zeroFields = structure.map { case (name, fieldType) =>
        name -> zeroValue(fieldType)
      }
      CValue.CProduct(zeroFields, structure)

    case CType.CUnion(variants) =>
      // Use first variant with zero value
      val (firstTag, firstType) = variants.head
      CValue.CUnion(zeroValue(firstType), variants, firstTag)

    case CType.COptional(_) =>
      CValue.CNone(ctype)
  }

  /** Check if a type has a meaningful zero value.
    *
    * Some types (like functions or resources) don't have sensible defaults.
    */
  def hasZeroValue(ctype: CType): Boolean = ctype match {
    case CType.CString | CType.CInt | CType.CFloat | CType.CBoolean => true
    case CType.CList(_) | CType.CMap(_, _) | CType.COptional(_)     => true
    case CType.CProduct(structure) => structure.values.forall(hasZeroValue)
    case CType.CUnion(variants)    => variants.values.exists(hasZeroValue)
  }
}
