package io.constellation.provider.sdk

import cats.effect.{IO, Ref}

import io.constellation.CValue
import io.constellation.provider.CValueSerializer
import io.constellation.provider.v1.{provider => pb}

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
      response  <- dispatch(request).handleError {
        case e: ModuleNotFoundException => e.errorResponse
        case e: TypeErrorException      => e.errorResponse
        case error =>
          pb.ExecuteResponse(
            result = pb.ExecuteResponse.Result.Error(pb.ExecutionError(
              code = "RUNTIME_ERROR",
              message = error.getMessage,
              stackTrace = error.getStackTrace.take(10).mkString("\n")
            ))
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
        serializer.deserialize(request.inputData.toByteArray).left.map(e =>
          new TypeErrorException(s"Failed to deserialize input: $e")
        )
      )
      outputCValue <- moduleDef.handler(inputCValue)
      outputBytes <- IO.fromEither(
        serializer.serialize(outputCValue).left.map(e =>
          new RuntimeException(s"Failed to serialize output: $e")
        )
      )
    } yield pb.ExecuteResponse(
      result = pb.ExecuteResponse.Result.OutputData(
        com.google.protobuf.ByteString.copyFrom(outputBytes)
      )
    )

  /** Convert this server into a request handler function for use with ExecutorServerFactory. */
  def toHandler: pb.ExecuteRequest => IO[pb.ExecuteResponse] = handleRequest
}

private[sdk] class ModuleNotFoundException(moduleName: String)
    extends RuntimeException(s"Module not found: $moduleName") {
  def errorResponse: pb.ExecuteResponse = pb.ExecuteResponse(
    result = pb.ExecuteResponse.Result.Error(pb.ExecutionError(
      code = "MODULE_NOT_FOUND",
      message = s"Module not found: $moduleName"
    ))
  )
}

private[sdk] class TypeErrorException(msg: String) extends RuntimeException(msg) {
  def errorResponse: pb.ExecuteResponse = pb.ExecuteResponse(
    result = pb.ExecuteResponse.Result.Error(pb.ExecutionError(
      code = "TYPE_ERROR",
      message = msg
    ))
  )
}
