package io.constellation.provider

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import cats.effect.{Deferred, IO, Ref, Resource}
import cats.implicits.*

import io.constellation.CValue
import io.constellation.provider.v1.provider as pb

import io.grpc.stub.StreamObserver

/** Manages one persistent bidirectional gRPC stream to a single executor endpoint.
  *
  * Multiplexes multiple concurrent batch requests over the stream using correlation IDs. When a
  * request is sent:
  *   1. Generate a UUID correlation ID 2. Create a Deferred for the result 3. Store correlation ID
  *      -> Deferred in pending map 4. Send request on the stream with correlation ID 5. Await the
  *      Deferred
  *
  * Response callback:
  *   1. Receive response with correlation ID 2. Look up Deferred by correlation ID 3. Complete the
  *      Deferred with the result 4. Remove from pending map
  *
  * If the stream breaks, `broken` flag is set and future calls return an error. The
  * StreamingBatchPool recreates the client when broken.
  */
class StreamingBatchClient(
    requestObserver: StreamObserver[pb.ExecuteBatchStreamRequest],
    pending: Ref[IO, Map[String, Deferred[IO, Either[Throwable, List[Either[Throwable, CValue]]]]]],
    serializer: CValueSerializer,
    broken: Ref[IO, Boolean]
) {

  import cats.effect.unsafe.implicits.global

  /** Send a batch request and await the response (multiplexed on the persistent stream). */
  def send(
      moduleName: String,
      inputs: List[CValue]
  ): IO[List[Either[Throwable, CValue]]] =
    for {
      isBroken <- broken.get
      _ <-
        if isBroken then
          IO.raiseError(new IllegalStateException("Streaming batch stream is broken"))
        else IO.unit

      correlationId = UUID.randomUUID().toString
      deferred <- Deferred[IO, Either[Throwable, List[Either[Throwable, CValue]]]]

      // Serialize all inputs
      serializedInputs <- inputs.traverse { input =>
        IO.fromEither(
          serializer
            .serialize(input)
            .left
            .map(e => new RuntimeException(s"Streaming batch serialization error: $e"))
        )
      }

      // Register the deferred in pending map
      _ <- pending.update { map =>
        map + (correlationId -> deferred)
      }

      // Send request on the stream (non-blocking, onNext doesn't wait for response)
      _ <- IO {
        requestObserver.onNext(
          pb.ExecuteBatchStreamRequest(
            correlationId = correlationId,
            moduleName = moduleName,
            inputs = serializedInputs.map(b => com.google.protobuf.ByteString.copyFrom(b)),
            executionId = UUID.randomUUID().toString
          )
        )
      }

      // Await the response (timeout should be handled at caller level if needed)
      result <- deferred.get

      // Return the result or raise error
      outcomes <- IO.fromEither(result)
    } yield outcomes

  /** Complete a pending request by correlation ID (called by response observer). */
  def completeRequest(
      correlationId: String,
      outcomes: Either[Throwable, List[Either[Throwable, CValue]]]
  ): IO[Unit] =
    for {
      map <- pending.get
      deferred <- map.get(correlationId) match {
        case Some(d) => IO.pure(d)
        case None    => IO.unit.as(null) // Safe: we check before use
      }
      // Complete the deferred if found
      _ <-
        if deferred != null then deferred.complete(outcomes) *> pending.update(_ - correlationId)
        else IO.unit
    } yield ()

  /** Mark the stream as broken so no more requests are accepted. */
  def markBroken: IO[Unit] =
    broken.set(true) *> failAllPending(
      new RuntimeException("Streaming batch stream closed unexpectedly")
    )

  /** Fail all pending requests with an error (called on stream close/error). */
  private def failAllPending(error: Throwable): IO[Unit] =
    for {
      map <- pending.get
      _ <- map.values.toList.traverse { deferred =>
        deferred.complete(Left(error))
      }
      _ <- pending.set(Map.empty)
    } yield ()

  def close: IO[Unit] = IO {
    requestObserver.onCompleted()
  }
}

object StreamingBatchClient {

  /** Create a StreamingBatchClient as a Resource that manages lifecycle and response callback.
    *
    * Opens the persistent bidirectional stream via asyncStub.executeBatchStream(), returns a new
    * StreamingBatchClient that sends requests on the stream. On close, completes the stream.
    */
  def resource(
      asyncStub: pb.ModuleExecutorGrpc.ModuleExecutorStub,
      serializer: CValueSerializer
  ): Resource[IO, StreamingBatchClient] =
    Resource.make(
      for {
        pending <- Ref[IO].of(
          Map.empty[String, Deferred[IO, Either[Throwable, List[Either[Throwable, CValue]]]]]
        )
        broken <- Ref[IO].of(false)

        // Build response observer that completes pending requests
        responseObserver = new StreamObserver[pb.ExecuteBatchStreamResponse] {
          import cats.effect.unsafe.implicits.global

          override def onNext(response: pb.ExecuteBatchStreamResponse): Unit = {
            val io: IO[Unit] = for {
              map <- pending.get
              deferred <- map.get(response.correlationId) match {
                case Some(d) => IO.pure(d)
                case None    => IO.unit.as(null)
              }
              // Process response
              _ <-
                if deferred != null then
                  // Deserialize results and complete deferred
                  for {
                    outcomes <-
                      if response.error.nonEmpty then
                        IO.pure(Left(new RuntimeException(response.error)))
                      else
                        response.results.toList
                          .traverse { batchResult =>
                            batchResult.result match {
                              case pb.ExecuteBatchResult.Result.OutputData(outputBytes) =>
                                IO.fromEither(
                                  serializer
                                    .deserialize(outputBytes.toByteArray)
                                    .left
                                    .map(e =>
                                      new RuntimeException(
                                        s"Stream batch deserialization error: $e"
                                      )
                                    )
                                ).map(Right(_))
                              case pb.ExecuteBatchResult.Result.Error(err) =>
                                IO.pure(
                                  Left(
                                    new RuntimeException(
                                      s"Stream batch element error: [${err.code}] ${err.message}"
                                    )
                                  )
                                )
                              case pb.ExecuteBatchResult.Result.Empty =>
                                IO.pure(Left(new RuntimeException("Stream batch empty result")))
                            }
                          }
                          .map(Right(_))
                    _ <- deferred.complete(outcomes)
                    _ <- pending.update(_ - response.correlationId)
                  } yield ()
                else IO.unit
            } yield ()
            io.unsafeRunAndForget()
          }

          override def onError(t: Throwable): Unit = {
            val io: IO[Unit] = for {
              map <- pending.get
              _ <- map.values.toList.traverse { deferred =>
                deferred.complete(Left(t))
              }
              _ <- pending.set(Map.empty)
              _ <- broken.set(true)
            } yield ()
            io.unsafeRunAndForget()
          }

          override def onCompleted(): Unit = {
            val io: IO[Unit] = for {
              map <- pending.get
              _ <- map.values.toList.traverse { deferred =>
                deferred.complete(
                  Left(new RuntimeException("Streaming batch stream completed unexpectedly"))
                )
              }
              _ <- pending.set(Map.empty)
              _ <- broken.set(true)
            } yield ()
            io.unsafeRunAndForget()
          }
        }

        // Open the stream
        requestObserver = asyncStub.executeBatchStream(responseObserver)
        client          = new StreamingBatchClient(requestObserver, pending, serializer, broken)
      } yield client
    )(client => client.close)

  /** Convenience overload that doesn't return the resource, just creates and manages it. */
  def open(
      asyncStub: pb.ModuleExecutorGrpc.ModuleExecutorStub,
      serializer: CValueSerializer
  ): IO[StreamingBatchClient] =
    resource(asyncStub, serializer).allocated.map(_._1)
}
