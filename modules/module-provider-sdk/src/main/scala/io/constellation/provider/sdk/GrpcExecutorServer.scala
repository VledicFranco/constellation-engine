package io.constellation.provider.sdk

import cats.effect.{IO, Resource}

import io.constellation.provider.v1.provider as pb

import io.grpc.{Server, ServerBuilder}

/** Production ExecutorServerFactory backed by gRPC ServerBuilder.
  *
  * Creates a gRPC server hosting the ModuleExecutor service.
  */
class GrpcExecutorServerFactory extends ExecutorServerFactory {

  def create(
      handler: pb.ExecuteRequest => IO[pb.ExecuteResponse],
      port: Int
  ): Resource[IO, Int] = createWithBatch(handler, None, port)

  def createWithBatch(
      handler: pb.ExecuteRequest => IO[pb.ExecuteResponse],
      batchHandler: Option[pb.ExecuteBatchRequest => IO[pb.ExecuteBatchResponse]],
      port: Int
  ): Resource[IO, Int] = createWithBatchAndStream(handler, batchHandler, None, port)

  def createWithBatchAndStream(
      handler: pb.ExecuteRequest => IO[pb.ExecuteResponse],
      batchHandler: Option[pb.ExecuteBatchRequest => IO[pb.ExecuteBatchResponse]],
      batchStreamHandler: Option[
        PartialFunction[pb.ExecuteBatchStreamRequest, (pb.ExecuteBatchStreamResponse => Unit) => IO[
          Unit
        ]]
      ],
      port: Int
  ): Resource[IO, Int] = {
    val serviceImpl = new GrpcModuleExecutorImpl(handler, batchHandler, batchStreamHandler)

    Resource.make(
      IO {
        val serviceDef =
          pb.ModuleExecutorGrpc.bindService(serviceImpl, scala.concurrent.ExecutionContext.global)
        val builder = ServerBuilder.forPort(port).addService(serviceDef)
        val server  = builder.build()
        server.start()
        server.getPort // Return actual bound port (may differ if port was 0)
      }
    )(_ => IO.unit) // Server shutdown handled externally
  }
}

/** gRPC service implementation that delegates to handler functions. */
private class GrpcModuleExecutorImpl(
    handler: pb.ExecuteRequest => IO[pb.ExecuteResponse],
    batchHandler: Option[pb.ExecuteBatchRequest => IO[pb.ExecuteBatchResponse]],
    batchStreamHandler: Option[
      PartialFunction[pb.ExecuteBatchStreamRequest, (pb.ExecuteBatchStreamResponse => Unit) => IO[
        Unit
      ]]
    ] = None
) extends pb.ModuleExecutorGrpc.ModuleExecutor {

  import cats.effect.unsafe.implicits.global
  import io.grpc.stub.StreamObserver

  override def execute(request: pb.ExecuteRequest): scala.concurrent.Future[pb.ExecuteResponse] = {
    val promise = scala.concurrent.Promise[pb.ExecuteResponse]()
    handler(request).unsafeRunAsync {
      case Right(response) => promise.success(response)
      case Left(error) =>
        promise.success(
          pb.ExecuteResponse(
            result = pb.ExecuteResponse.Result.Error(
              pb.ExecutionError(
                code = "RUNTIME_ERROR",
                message = error.getMessage
              )
            )
          )
        )
    }
    promise.future
  }

  override def executeBatch(
      request: pb.ExecuteBatchRequest
  ): scala.concurrent.Future[pb.ExecuteBatchResponse] = {
    val promise = scala.concurrent.Promise[pb.ExecuteBatchResponse]()
    val handler = batchHandler.getOrElse((_: pb.ExecuteBatchRequest) =>
      IO.raiseError(new UnsupportedOperationException("ExecuteBatch not supported"))
    )
    handler(request).unsafeRunAsync {
      case Right(response) => promise.success(response)
      case Left(error) =>
        promise.success(
          pb.ExecuteBatchResponse(
            results = Seq(
              pb.ExecuteBatchResult(
                result = pb.ExecuteBatchResult.Result.Error(
                  pb.ExecutionError(
                    code = "RUNTIME_ERROR",
                    message = error.getMessage
                  )
                )
              )
            )
          )
        )
    }
    promise.future
  }

  override def executeBatchStream(
      responseObserver: StreamObserver[pb.ExecuteBatchStreamResponse]
  ): StreamObserver[pb.ExecuteBatchStreamRequest] =
    new StreamObserver[pb.ExecuteBatchStreamRequest] {
      override def onNext(request: pb.ExecuteBatchStreamRequest): Unit =
        batchStreamHandler match {
          case Some(handler) if handler.isDefinedAt(request) =>
            handler(request)(msg => responseObserver.onNext(msg)).unsafeRunAndForget()
          case Some(_) =>
            responseObserver.onNext(
              pb.ExecuteBatchStreamResponse(
                correlationId = request.correlationId,
                error = s"Handler not defined for module: ${request.moduleName}"
              )
            )
          case None =>
            responseObserver.onNext(
              pb.ExecuteBatchStreamResponse(
                correlationId = request.correlationId,
                error = "ExecuteBatchStream not supported by this server"
              )
            )
        }

      override def onError(t: Throwable): Unit =
        responseObserver.onError(t)

      override def onCompleted(): Unit =
        responseObserver.onCompleted()
    }
}
