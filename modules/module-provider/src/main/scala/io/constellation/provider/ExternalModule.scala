package io.constellation.provider

import java.util.UUID
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

import cats.effect.{Deferred, IO}
import cats.implicits.*

import io.constellation.*
import io.constellation.provider.v1.{provider => pb}

import io.grpc.ManagedChannelBuilder

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
      serializer: CValueSerializer
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

              // gRPC call
              startTime = System.nanoTime()
              response <- callExecutor(executorUrl, name, inputBytes, moduleId.toString)
              durationNs = System.nanoTime() - startTime

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
                              runtime.setTableDataCValue(dataId, value)
                            case None =>
                              IO.raiseError(new RuntimeException(
                                s"External module '$name' output missing field '$fieldName'"
                              ))
                          }
                        }
                      case singleValue if producesSpec.size == 1 =>
                        val (dataId, _, _) = produceEntries.head
                        runtime.setTableDataCValue(dataId, singleValue)
                      case other =>
                        IO.raiseError(new RuntimeException(
                          s"External module '$name' returned unexpected output type: ${other.ctype}"
                        ))
                    }
                    // Also set state data for each output
                    _ <- produceEntries.traverse_ { case (dataId, _, _) =>
                      outputCValue match {
                        case CValue.CProduct(fields, _) =>
                          fields.get(produceEntries.find(_._1 == dataId).get._3).traverse_ { v =>
                            runtime.setStateData(dataId, v)
                          }
                        case singleValue =>
                          runtime.setStateData(dataId, singleValue)
                      }
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
      executorUrl: String,
      moduleName: String,
      inputBytes: Array[Byte],
      executionId: String
  ): IO[pb.ExecuteResponse] = IO {
    val (host, port) = parseHostPort(executorUrl)
    val channel = ManagedChannelBuilder
      .forAddress(host, port)
      .usePlaintext()
      .build()

    try {
      val stub = pb.ModuleExecutorGrpc.blockingStub(channel)
      stub.execute(
        pb.ExecuteRequest(
          moduleName = moduleName,
          inputData = com.google.protobuf.ByteString.copyFrom(inputBytes),
          executionId = executionId
        )
      )
    } finally {
      channel.shutdown()
    }
  }

  private def parseHostPort(url: String): (String, Int) = {
    val parts = url.split(':')
    if parts.length == 2 then (parts(0), parts(1).toInt)
    else (url, 9090)
  }
}
