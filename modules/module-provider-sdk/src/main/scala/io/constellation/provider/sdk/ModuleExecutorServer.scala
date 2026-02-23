package io.constellation.provider.sdk

import cats.effect.{IO, Ref}
import cats.implicits.*

import io.constellation.CValue
import io.constellation.provider.CValueSerializer
import io.constellation.provider.v1.provider as pb

/** Dispatches incoming ExecuteRequest to the correct ModuleDefinition handler.
  *
  * Module list is stored in a Ref to support hot-swap (canary rollout).
  */
class ModuleExecutorServer(
    modules: Ref[IO, List[ModuleDefinition]],
    serializer: CValueSerializer
) {

  /** Handle an ExecuteRequest by dispatching to the appropriate module. */
  def handleRequest(request: pb.ExecuteRequest): IO[pb.ExecuteResponse] =
    for {
      startTime <- IO.monotonic
      response <- dispatch(request).handleError {
        case e: ModuleNotFoundException => e.errorResponse
        case e: TypeErrorException      => e.errorResponse
        case error =>
          pb.ExecuteResponse(
            result = pb.ExecuteResponse.Result.Error(
              pb.ExecutionError(
                code = "RUNTIME_ERROR",
                message = error.getMessage,
                stackTrace = error.getStackTrace.take(10).mkString("\n")
              )
            )
          )
      }
      endTime <- IO.monotonic
      durationMs = (endTime - startTime).toMillis
      withMetrics = response.copy(
        metrics = Some(pb.ExecutionMetrics(durationMs = durationMs))
      )
    } yield withMetrics

  private def dispatch(request: pb.ExecuteRequest): IO[pb.ExecuteResponse] =
    for {
      currentModules <- modules.get
      moduleDef <- currentModules.find(_.name == request.moduleName) match {
        case Some(md) => IO.pure(md)
        case None     => IO.raiseError(new ModuleNotFoundException(request.moduleName))
      }
      inputCValue <- IO.fromEither(
        serializer
          .deserialize(request.inputData.toByteArray)
          .left
          .map(e => new TypeErrorException(s"Failed to deserialize input: $e"))
      )
      outputCValue <- moduleDef.handler(inputCValue)
      outputBytes <- IO.fromEither(
        serializer
          .serialize(outputCValue)
          .left
          .map(e => new RuntimeException(s"Failed to serialize output: $e"))
      )
    } yield pb.ExecuteResponse(
      result = pb.ExecuteResponse.Result.OutputData(
        com.google.protobuf.ByteString.copyFrom(outputBytes)
      )
    )

  /** Handle an ExecuteBatchRequest by dispatching each input to the module (RFC-034 Phase 1). */
  def handleBatchRequest(request: pb.ExecuteBatchRequest): IO[pb.ExecuteBatchResponse] =
    for {
      startTime      <- IO.monotonic
      currentModules <- modules.get
      moduleDef <- currentModules.find(_.name == request.moduleName) match {
        case Some(md) => IO.pure(md)
        case None     => IO.raiseError(new ModuleNotFoundException(request.moduleName))
      }
      inputs <- request.inputs.toList.traverse { inputBytes =>
        IO.fromEither(
          serializer
            .deserialize(inputBytes.toByteArray)
            .left
            .map(e => new TypeErrorException(s"Failed to deserialize batch input: $e"))
        )
      }
      // Use batch handler if available, otherwise loop single handler
      results <- moduleDef.batchHandler match {
        case Some(batchFn) => batchFn(inputs)
        case None =>
          inputs.traverse { input =>
            moduleDef
              .handler(input)
              .map(Right(_))
              .handleError(Left(_))
          }
      }
      batchResults <- results.traverse {
        case Right(output) =>
          IO.fromEither(
            serializer
              .serialize(output)
              .left
              .map(e => new RuntimeException(s"Failed to serialize batch output: $e"))
          ).map(bytes =>
            pb.ExecuteBatchResult(
              result = pb.ExecuteBatchResult.Result.OutputData(
                com.google.protobuf.ByteString.copyFrom(bytes)
              )
            )
          )
        case Left(error) =>
          IO.pure(
            pb.ExecuteBatchResult(
              result = pb.ExecuteBatchResult.Result.Error(
                pb.ExecutionError(
                  code = "RUNTIME_ERROR",
                  message = Option(error.getMessage).getOrElse("Unknown error")
                )
              )
            )
          )
      }
      endTime <- IO.monotonic
      durationMs = (endTime - startTime).toMillis
    } yield pb.ExecuteBatchResponse(
      results = batchResults,
      aggregateMetrics = Some(pb.ExecutionMetrics(durationMs = durationMs))
    )

  /** Convert this server into a request handler function for use with ExecutorServerFactory. */
  def toHandler: pb.ExecuteRequest => IO[pb.ExecuteResponse] = handleRequest

  /** Convert this server into a batch request handler function. */
  def toBatchHandler: pb.ExecuteBatchRequest => IO[pb.ExecuteBatchResponse] = handleBatchRequest

  /** Handle a bidirectional ExecuteBatchStream by dispatching each request (RFC-034 Phase 2B).
    *
    * Each request has a correlation_id that must be echoed in the response. Errors at the batch
    * level (module not found) go in the error field. Element-level errors go in
    * results[].result.error.
    */
  def handleBatchStreamRequest(
      request: pb.ExecuteBatchStreamRequest,
      sendResponse: pb.ExecuteBatchStreamResponse => Unit
  ): IO[Unit] =
    (for {
      startTime      <- IO.monotonic
      currentModules <- modules.get
      moduleDef <- currentModules.find(_.name == request.moduleName) match {
        case Some(md) => IO.pure(md)
        case None =>
          IO.raiseError(new ModuleNotFoundException(request.moduleName))
      }
      inputs <- request.inputs.toList.traverse { inputBytes =>
        IO.fromEither(
          serializer
            .deserialize(inputBytes.toByteArray)
            .left
            .map(e => new TypeErrorException(s"Failed to deserialize batch stream input: $e"))
        )
      }
      // Use batch handler if available, otherwise loop single handler
      results <- moduleDef.batchHandler match {
        case Some(batchFn) => batchFn(inputs)
        case None =>
          inputs.traverse { input =>
            moduleDef
              .handler(input)
              .map(Right(_))
              .handleError(Left(_))
          }
      }
      batchResults <- results.traverse {
        case Right(output) =>
          IO.fromEither(
            serializer
              .serialize(output)
              .left
              .map(e => new RuntimeException(s"Failed to serialize batch stream output: $e"))
          ).map(bytes =>
            pb.ExecuteBatchResult(
              result = pb.ExecuteBatchResult.Result.OutputData(
                com.google.protobuf.ByteString.copyFrom(bytes)
              )
            )
          )
        case Left(error) =>
          IO.pure(
            pb.ExecuteBatchResult(
              result = pb.ExecuteBatchResult.Result.Error(
                pb.ExecutionError(
                  code = "RUNTIME_ERROR",
                  message = Option(error.getMessage).getOrElse("Unknown error")
                )
              )
            )
          )
      }
      endTime <- IO.monotonic
      durationMs = (endTime - startTime).toMillis
      _ <- IO {
        sendResponse(
          pb.ExecuteBatchStreamResponse(
            correlationId = request.correlationId,
            results = batchResults,
            aggregateMetrics = Some(pb.ExecutionMetrics(durationMs = durationMs))
          )
        )
      }
    } yield ()).handleError {
      case e: ModuleNotFoundException =>
        IO {
          sendResponse(
            pb.ExecuteBatchStreamResponse(
              correlationId = request.correlationId,
              error = e.getMessage
            )
          )
        }
      case e: TypeErrorException =>
        IO {
          sendResponse(
            pb.ExecuteBatchStreamResponse(
              correlationId = request.correlationId,
              error = e.getMessage
            )
          )
        }
      case error =>
        IO {
          sendResponse(
            pb.ExecuteBatchStreamResponse(
              correlationId = request.correlationId,
              error = s"Stream batch error: ${error.getMessage}"
            )
          )
        }
    }

  /** Convert this server into a batch stream handler function. */
  def toBatchStreamHandler
      : PartialFunction[pb.ExecuteBatchStreamRequest, (pb.ExecuteBatchStreamResponse => Unit) => IO[
        Unit
      ]] = { case request =>
    sendResponse => handleBatchStreamRequest(request, sendResponse)
  }
}

private[sdk] class ModuleNotFoundException(moduleName: String)
    extends RuntimeException(s"Module not found: $moduleName") {
  def errorResponse: pb.ExecuteResponse = pb.ExecuteResponse(
    result = pb.ExecuteResponse.Result.Error(
      pb.ExecutionError(
        code = "MODULE_NOT_FOUND",
        message = s"Module not found: $moduleName"
      )
    )
  )
}

private[sdk] class TypeErrorException(msg: String) extends RuntimeException(msg) {
  def errorResponse: pb.ExecuteResponse = pb.ExecuteResponse(
    result = pb.ExecuteResponse.Result.Error(
      pb.ExecutionError(
        code = "TYPE_ERROR",
        message = msg
      )
    )
  )
}
