package io.constellation.provider

import java.util.concurrent.ConcurrentHashMap

import cats.effect.{IO, Ref, Resource}
import cats.implicits.*

import io.constellation.CValue
import io.constellation.provider.v1.provider as pb

/** Pool of persistent bidirectional gRPC streams, one per executor endpoint URL.
  *
  * When a request is made to an executor URL:
  *   1. Check if a client for that URL exists and is not broken 2. If missing or broken: create a
  *      new one 3. Send the request on the persistent stream
  *
  * Clients are created with StreamingBatchClient.resource() but stored in a concurrent map for
  * reuse. When a client breaks, it's recreated on next use.
  */
class StreamingBatchPool private (
    private[provider] val clients: Ref[IO, Map[String, StreamingBatchClient]],
    private val channelCache: GrpcChannelCache,
    private val serializer: CValueSerializer
) {

  /** Get or create a client for the given executor URL. Recreates if broken. */
  private def getOrCreate(executorUrl: String): IO[StreamingBatchClient] =
    for {
      map <- clients.get
      existing = map.get(executorUrl)

      client <- existing match {
        case Some(c) =>
          // Use existing client; if broken, the send call will fail and pool can recreate on next use
          IO.pure(c)
        case None =>
          // No client exists, create one
          createNewClient(executorUrl)
      }
    } yield client

  private def createNewClient(executorUrl: String): IO[StreamingBatchClient] =
    for {
      channel <- IO(channelCache.getChannel(executorUrl))
      asyncStub = pb.ModuleExecutorGrpc.stub(channel)
      // Use allocated to extract the client value without automatic cleanup
      // (we manage cleanup via the pool's Resource release)
      allocResult <- StreamingBatchClient.resource(asyncStub, serializer).allocated
      client = allocResult._1
      _ <- clients.update(_ + (executorUrl -> client))
    } yield client

  /** Execute a batch via the persistent stream for the given URL. */
  def executeBatch(
      executorUrl: String,
      moduleName: String,
      inputs: List[CValue]
  ): IO[List[Either[Throwable, CValue]]] =
    for {
      client  <- getOrCreate(executorUrl)
      results <- client.send(moduleName, inputs)
    } yield results
}

object StreamingBatchPool {

  /** Create a StreamingBatchPool as a Resource that manages client cleanup. */
  def resource(
      channelCache: GrpcChannelCache,
      serializer: CValueSerializer
  ): Resource[IO, StreamingBatchPool] =
    Resource.make(
      for {
        clients <- Ref[IO].of(Map.empty[String, StreamingBatchClient])
      } yield new StreamingBatchPool(clients, channelCache, serializer)
    )(pool =>
      for {
        map <- pool.clients.get
        _ <- map.values.toList.traverse { client =>
          client.close
        }
      } yield ()
    )
}
