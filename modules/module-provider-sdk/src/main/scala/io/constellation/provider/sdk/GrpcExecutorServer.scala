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
  ): Resource[IO, Int] = {
    val serviceImpl = new GrpcModuleExecutorImpl(handler)

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

/** gRPC service implementation that delegates to a handler function. */
private class GrpcModuleExecutorImpl(handler: pb.ExecuteRequest => IO[pb.ExecuteResponse])
    extends pb.ModuleExecutorGrpc.ModuleExecutor {

  import cats.effect.unsafe.implicits.global

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
}
