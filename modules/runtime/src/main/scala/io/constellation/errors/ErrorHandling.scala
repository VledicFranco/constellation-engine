package io.constellation.errors

import cats.data.EitherT
import cats.effect.IO
import cats.implicits.*

/** Standardized error types for HTTP API responses */
sealed trait ApiError {
  def message: String
}

object ApiError {

  /** Error during input conversion (JSON → CValue) */
  case class InputError(message: String) extends ApiError

  /** Error during DAG execution */
  case class ExecutionError(message: String) extends ApiError

  /** Error during output extraction (CValue → JSON) */
  case class OutputError(message: String) extends ApiError

  /** DAG or resource not found */
  case class NotFoundError(resource: String, name: String) extends ApiError {
    def message: String = s"$resource '$name' not found"
  }

  /** Compilation error */
  case class CompilationError(errors: List[String]) extends ApiError {
    def message: String = errors.mkString("; ")
  }

  /** Wrap an exception as an API error */
  def fromThrowable(prefix: String)(t: Throwable): ApiError =
    ExecutionError(s"$prefix: ${t.getMessage}")
}

/** Error handling utilities for consistent patterns across modules */
object ErrorHandling {

  /** Convert an IO operation to EitherT with error mapping */
  def liftIO[E, A](io: IO[A])(mapError: Throwable => E): EitherT[IO, E, A] =
    EitherT(io.attempt.map(_.leftMap(mapError)))

  /** Convert an Either value into EitherT */
  def fromEither[E, A](either: Either[E, A]): EitherT[IO, E, A] =
    EitherT.fromEither[IO](either)

  /** Convert an Option into EitherT with a custom error for None */
  def fromOption[E, A](opt: Option[A], ifNone: => E): EitherT[IO, E, A] =
    EitherT.fromOption[IO](opt, ifNone)

  /** Lift a pure value into EitherT */
  def pure[E, A](a: A): EitherT[IO, E, A] =
    EitherT.pure[IO, E](a)

  /** Handle errors in notification handlers by logging instead of silently swallowing.
    *
    * Use this for LSP notification handlers (didOpen, didChange, didClose) where we can't return an
    * error response but shouldn't completely ignore failures.
    *
    * @param operation
    *   Name of the operation for logging context
    * @param logger
    *   Function to log warning messages
    * @param fa
    *   The IO operation to execute
    * @return
    *   IO[Unit] that completes successfully even if the operation fails
    */
  def handleNotification(operation: String, logger: String => IO[Unit])(fa: IO[Unit]): IO[Unit] =
    fa.handleErrorWith { error =>
      logger(s"Error in $operation: ${error.getMessage}")
    }

  /** Execute an IO operation and convert to Option, logging errors.
    *
    * Use this for request handlers where we want to return None on failure rather than propagating
    * exceptions, but still want visibility into failures.
    *
    * @param operation
    *   Name of the operation for logging context
    * @param logger
    *   Function to log warning messages
    * @param fa
    *   The IO operation to execute
    * @return
    *   IO[Option[A]] - Some(result) on success, None on failure
    */
  def handleRequestWithLogging[A](
      operation: String,
      logger: String => IO[Unit]
  )(fa: IO[A]): IO[Option[A]] =
    fa.attempt.flatMap {
      case Right(a) => IO.pure(Some(a))
      case Left(e)  => logger(s"Error in $operation: ${e.getMessage}") *> IO.pure(None)
    }

  /** Execute an IO and map failure to a result type.
    *
    * Use this when you need to return a structured error result rather than throwing or returning
    * Option.
    *
    * @param fa
    *   The IO operation to execute
    * @param onSuccess
    *   Function to build success result
    * @param onError
    *   Function to build error result from exception
    * @return
    *   IO[R] - success or error result
    */
  def executeWithResult[A, R](fa: IO[A])(onSuccess: A => R, onError: Throwable => R): IO[R] =
    fa.attempt.map {
      case Right(a) => onSuccess(a)
      case Left(e)  => onError(e)
    }
}
