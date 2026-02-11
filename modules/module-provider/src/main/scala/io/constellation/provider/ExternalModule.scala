package io.constellation.provider

import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

import scala.concurrent.duration.FiniteDuration

import cats.effect.{Deferred, IO}
import cats.implicits.*

import io.constellation.*
import io.constellation.provider.v1.{provider => pb}

import io.grpc.{ManagedChannel, ManagedChannelBuilder}

/** Thread-safe cache of gRPC channels keyed by executor URL.
  *
  * Channels are created lazily on first use and reused for subsequent calls. When a provider
  * deregisters, its channel should be shut down via `shutdownChannel`. On system shutdown,
  * `shutdownAll` closes all remaining channels.
  */
class GrpcChannelCache {
  private val channels = new ConcurrentHashMap[String, ManagedChannel]()

  /** Get or create a channel for the given executor URL. */
  def getChannel(executorUrl: String): ManagedChannel =
    channels.computeIfAbsent(executorUrl, url => {
      val (host, port) = ExternalModule.parseHostPort(url) match {
        case Right(hp) => hp
        case Left(_)   => (url, 9090) // fallback; should not happen after registration validation
      }
      ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
    })

  /** Shut down and remove the channel for a given executor URL. */
  def shutdownChannel(executorUrl: String): Unit =
    Option(channels.remove(executorUrl)).foreach { ch =>
      ch.shutdown()
      try ch.awaitTermination(5, TimeUnit.SECONDS)
      catch { case _: InterruptedException => () }
    }

  /** Shut down all channels. Called during system shutdown. */
  def shutdownAll(): Unit = {
    import scala.jdk.CollectionConverters.*
    channels.asScala.values.foreach { ch =>
      ch.shutdown()
      try ch.awaitTermination(5, TimeUnit.SECONDS)
      catch { case _: InterruptedException => () }
    }
    channels.clear()
  }
}

/** Creates Module.Uninitialized instances backed by gRPC execution on an external provider. */
object ExternalModule {

  /** Create an uninitialized module that executes via gRPC callback. */
  def create(
      name: String,
      namespace: String,
      executorUrl: String,
      inputType: CType,
      outputType: CType,
      description: String,
      serializer: CValueSerializer,
      channelCache: GrpcChannelCache
  ): Module.Uninitialized = {
    val qualifiedName = s"$namespace.$name"

    val consumesSpec: Map[String, CType] = inputType match {
      case CType.CProduct(structure) => structure
      case other                     => Map("input" -> other)
    }
    val producesSpec: Map[String, CType] = outputType match {
      case CType.CProduct(structure) => structure
      case other                     => Map("output" -> other)
    }

    val spec = ModuleNodeSpec(
      metadata = ComponentMetadata(
        name = qualifiedName,
        description = description,
        tags = List("external", "provider"),
        majorVersion = 1,
        minorVersion = 0
      ),
      consumes = consumesSpec,
      produces = producesSpec
    )

    Module.Uninitialized(
      spec = spec,
      init = (moduleId: UUID, dagSpec: DagSpec) =>
        for {
          consumesNs <- Module.Namespace.consumes(moduleId, dagSpec)
          producesNs <- Module.Namespace.produces(moduleId, dagSpec)
          // Create deferreds for each input field
          consumeEntries <- consumesSpec.keys.toList.traverse { fieldName =>
            for {
              id       <- consumesNs.nameId(fieldName)
              deferred <- Deferred[IO, Any]
            } yield (id, deferred, fieldName)
          }
          // Create deferreds for each output field
          produceEntries <- producesSpec.keys.toList.traverse { fieldName =>
            for {
              id       <- producesNs.nameId(fieldName)
              deferred <- Deferred[IO, Any]
            } yield (id, deferred, fieldName)
          }

          dataTable = (consumeEntries.map(t => t._1 -> t._2) ++
            produceEntries.map(t => t._1 -> t._2)).toMap

        } yield Module.Runnable(
          id = moduleId,
          data = dataTable,
          run = (runtime: Runtime) =>
            (for {
              _ <- runtime.setModuleStatus(moduleId, Module.Status.Unfired)

              // Read all inputs from the data table as raw Any, convert to CValue
              inputFields <- consumeEntries.traverse { case (dataId, _, fieldName) =>
                val fieldType = consumesSpec(fieldName)
                runtime.getTableData(dataId).map { anyVal =>
                  fieldName -> Runtime.anyToCValue(anyVal, fieldType)
                }
              }

              // Build input CValue
              inputCValue = CValue.CProduct(inputFields.toMap, consumesSpec)

              // Serialize
              inputBytes <- IO.fromEither(
                serializer.serialize(inputCValue).left.map(e =>
                  new RuntimeException(s"Serialization error: $e")
                )
              )

              // gRPC call (uses cached channel)
              startTime <- IO.monotonic
              response  <- callExecutor(channelCache, executorUrl, name, inputBytes, moduleId.toString)
              endTime   <- IO.monotonic
              durationNs = (endTime - startTime).toNanos

              // Handle response
              _ <- response.result match {
                case pb.ExecuteResponse.Result.OutputData(outputBytes) =>
                  for {
                    outputCValue <- IO.fromEither(
                      serializer.deserialize(outputBytes.toByteArray).left.map(e =>
                        new RuntimeException(s"Deserialization error: $e")
                      )
                    )
                    // Write outputs to data table
                    _ <- outputCValue match {
                      case CValue.CProduct(fields, _) =>
                        produceEntries.traverse_ { case (dataId, _, fieldName) =>
                          fields.get(fieldName) match {
                            case Some(value) =>
                              runtime.setTableDataCValue(dataId, value) >>
                                runtime.setStateData(dataId, value)
                            case None =>
                              IO.raiseError(new RuntimeException(
                                s"External module '$name' output missing field '$fieldName'"
                              ))
                          }
                        }
                      case singleValue if producesSpec.size == 1 =>
                        val (dataId, _, _) = produceEntries.head
                        runtime.setTableDataCValue(dataId, singleValue) >>
                          runtime.setStateData(dataId, singleValue)
                      case other =>
                        IO.raiseError(new RuntimeException(
                          s"External module '$name' returned unexpected output type: ${other.ctype}"
                        ))
                    }
                  } yield ()

                case pb.ExecuteResponse.Result.Error(err) =>
                  IO.raiseError(new RuntimeException(
                    s"External module '$name' failed: [${err.code}] ${err.message}"
                  ))

                case pb.ExecuteResponse.Result.Empty =>
                  IO.raiseError(new RuntimeException(
                    s"External module '$name' returned empty response"
                  ))
              }

              _ <- runtime.setModuleStatus(
                moduleId,
                Module.Status.Fired(FiniteDuration(durationNs, TimeUnit.NANOSECONDS), None)
              )
            } yield ()).handleErrorWith { e =>
              runtime.setModuleStatus(moduleId, Module.Status.Failed(e))
            }
        )
    )
  }

  private def callExecutor(
      channelCache: GrpcChannelCache,
      executorUrl: String,
      moduleName: String,
      inputBytes: Array[Byte],
      executionId: String
  ): IO[pb.ExecuteResponse] = IO {
    val channel = channelCache.getChannel(executorUrl)
    val stub = pb.ModuleExecutorGrpc.blockingStub(channel)
    stub.execute(
      pb.ExecuteRequest(
        moduleName = moduleName,
        inputData = com.google.protobuf.ByteString.copyFrom(inputBytes),
        executionId = executionId
      )
    )
  }

  /** Parse a host:port string into its components. Visible for testing and validation. */
  private[provider] def parseHostPort(url: String): Either[String, (String, Int)] = {
    val trimmed = url.trim
    if trimmed.isEmpty then Left("Empty executor URL")
    else if trimmed.startsWith("[") then {
      // IPv6 address: [::1]:9090
      val closeBracket = trimmed.indexOf(']')
      if closeBracket < 0 then Left(s"Invalid IPv6 address in URL: $trimmed")
      else {
        val host = trimmed.substring(0, closeBracket + 1)
        val rest = trimmed.substring(closeBracket + 1)
        if rest.isEmpty then Right((host, 9090))
        else if rest.startsWith(":") then
          rest.substring(1).toIntOption match {
            case Some(port) if port > 0 && port <= 65535 => Right((host, port))
            case Some(_) => Left(s"Port out of range in URL: $trimmed")
            case None    => Left(s"Invalid port in URL: $trimmed")
          }
        else Left(s"Invalid URL format: $trimmed")
      }
    }
    else if trimmed.contains("://") then Left(s"executor_url must be host:port format, not a URL with scheme: $trimmed")
    else {
      val lastColon = trimmed.lastIndexOf(':')
      if lastColon < 0 then Right((trimmed, 9090))
      else {
        val host = trimmed.substring(0, lastColon)
        val portStr = trimmed.substring(lastColon + 1)
        if host.isEmpty then Left(s"Empty host in URL: $trimmed")
        else portStr.toIntOption match {
          case Some(port) if port > 0 && port <= 65535 => Right((host, port))
          case Some(_) => Left(s"Port out of range in URL: $trimmed")
          case None    => Left(s"Invalid port '$portStr' in URL: $trimmed")
        }
      }
    }
  }
}
