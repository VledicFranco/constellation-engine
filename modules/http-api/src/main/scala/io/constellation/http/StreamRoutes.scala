package io.constellation.http

import java.util.UUID

import cats.effect.IO
import cats.implicits.*

import io.constellation.http.StreamApiModels.*
import io.constellation.lang.LangCompiler
import io.constellation.stream.StreamCompiler
import io.constellation.stream.StreamOptions
import io.constellation.stream.config.{SinkBinding, SourceBinding, StreamPipelineConfig}
import io.constellation.stream.connector.ConnectorRegistry
import io.constellation.stream.error.StreamErrorStrategy
import io.constellation.stream.join.JoinStrategy
import io.constellation.{
  CType,
  CValue,
  ComponentMetadata,
  Constellation,
  DagSpec,
  DataNodeSpec,
  Module,
  ModuleNodeSpec,
  PipelineImage,
  Runtime
}

import io.circe.Json
import io.circe.syntax.*
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/** HTTP routes for streaming pipeline lifecycle management.
  *
  * Provides:
  *   - `POST /api/v1/streams` — deploy a new streaming pipeline
  *   - `GET /api/v1/streams` — list all managed streams
  *   - `GET /api/v1/streams/:id` — get stream detail with metrics
  *   - `DELETE /api/v1/streams/:id` — stop and remove a stream
  *   - `GET /api/v1/streams/:id/metrics` — detailed metrics for a stream
  *   - `GET /api/v1/connectors` — list available connectors with schemas
  */
class StreamRoutes(
    manager: StreamLifecycleManager,
    registry: ConnectorRegistry,
    constellation: Constellation,
    compiler: LangCompiler
) {

  private val logger: Logger[IO] = Slf4jLogger.getLoggerFromClass[IO](classOf[StreamRoutes])

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    // ===== Deploy =====

    case req @ POST -> Root / "api" / "v1" / "streams" =>
      (for {
        body   <- req.as[StreamDeployRequest]
        result <- deployStream(body)
        resp <- result match {
          case Right(info) => Created(info)
          case Left(error) => BadRequest(Json.obj("error" -> Json.fromString(error)))
        }
      } yield resp).handleErrorWith { err =>
        val message = Option(err.getMessage).getOrElse(err.getClass.getSimpleName)
        logger.error(err)(s"Stream deploy failed") *>
          InternalServerError(Json.obj("error" -> Json.fromString(message)))
      }

    // ===== List =====

    case GET -> Root / "api" / "v1" / "streams" =>
      for {
        streams <- manager.list
        infos = streams.map(m => toStreamInfo(m))
        resp <- Ok(StreamListResponse(infos))
      } yield resp

    // ===== Get =====

    case GET -> Root / "api" / "v1" / "streams" / id =>
      for {
        streamOpt <- manager.get(id)
        resp <- streamOpt match {
          case Some(managed) =>
            for {
              metricsOpt <- manager.metrics(id)
              info = toStreamInfo(managed, metricsOpt.map(toMetricsSummary))
              r <- Ok(info)
            } yield r
          case None =>
            NotFound(Json.obj("error" -> Json.fromString(s"Stream '$id' not found")))
        }
      } yield resp

    // ===== Stop =====

    case DELETE -> Root / "api" / "v1" / "streams" / id =>
      for {
        result <- manager.stop(id)
        resp <- result match {
          case Right(_) =>
            Ok(Json.obj("status" -> Json.fromString("stopped"), "id" -> Json.fromString(id)))
          case Left(error) => NotFound(Json.obj("error" -> Json.fromString(error)))
        }
      } yield resp

    // ===== Metrics =====

    case GET -> Root / "api" / "v1" / "streams" / id / "metrics" =>
      for {
        metricsOpt <- manager.metrics(id)
        resp <- metricsOpt match {
          case Some(snap) => Ok(toMetricsSummary(snap))
          case None => NotFound(Json.obj("error" -> Json.fromString(s"Stream '$id' not found")))
        }
      } yield resp

    // ===== Connectors =====

    case GET -> Root / "api" / "v1" / "connectors" =>
      val sourceInfos = registry.allSources.map { case (name, connector) =>
        val schema = registry.getSourceSchema(name).map { s =>
          ConnectorSchemaResponse(
            required = s.required.map { case (k, v) => k -> v.toString },
            optional = s.optional.map { case (k, v) => k -> v.toString }
          )
        }
        ConnectorInfoResponse(
          name = name,
          typeName = connector.typeName,
          kind = "source",
          schema = schema
        )
      }.toList

      val sinkInfos = registry.allSinks.map { case (name, connector) =>
        val schema = registry.getSinkSchema(name).map { s =>
          ConnectorSchemaResponse(
            required = s.required.map { case (k, v) => k -> v.toString },
            optional = s.optional.map { case (k, v) => k -> v.toString }
          )
        }
        ConnectorInfoResponse(
          name = name,
          typeName = connector.typeName,
          kind = "sink",
          schema = schema
        )
      }.toList

      Ok(ConnectorListResponse(sourceInfos ++ sinkInfos))
  }

  /** Deploy a streaming pipeline from a request. */
  private def deployStream(
      req: StreamDeployRequest
  ): IO[Either[String, StreamInfoResponse]] =
    for {
      // Resolve pipeline image
      imageOpt <- constellation.PipelineStore.get(req.pipelineRef)
      result <- imageOpt match {
        case None =>
          // Try resolving as alias
          constellation.PipelineStore.resolve(req.pipelineRef).flatMap {
            case Some(hash) =>
              constellation.PipelineStore.get(hash).flatMap {
                case Some(image) => doDeploy(req, image)
                case None        => IO.pure(Left(s"Pipeline '${req.pipelineRef}' not found"))
              }
            case None =>
              IO.pure(Left(s"Pipeline '${req.pipelineRef}' not found"))
          }
        case Some(image) => doDeploy(req, image)
      }
    } yield result

  private def doDeploy(
      req: StreamDeployRequest,
      image: PipelineImage
  ): IO[Either[String, StreamInfoResponse]] = {
    val dagSpec  = image.dagSpec
    val streamId = UUID.randomUUID().toString

    for {
      // Extract module functions from constellation
      moduleFns <- extractModuleFunctions(dagSpec)

      // Build StreamPipelineConfig from request bindings
      config = StreamPipelineConfig(
        sourceBindings = req.sourceBindings.map { case (name, binding) =>
          name -> SourceBinding(binding.connectorType, binding.properties)
        },
        sinkBindings = req.sinkBindings.map { case (name, binding) =>
          name -> SinkBinding(binding.connectorType, binding.properties)
        }
      )

      // Parse options
      errorStrategy = req.options
        .flatMap(_.errorStrategy)
        .map(parseErrorStrategy)
        .getOrElse(StreamErrorStrategy.Log)
      joinStrategy = req.options
        .flatMap(_.joinStrategy)
        .map(parseJoinStrategy)
        .getOrElse(JoinStrategy.CombineLatest)

      // Wire the stream graph
      graph <- StreamCompiler.wireWithConfig(
        dagSpec,
        config,
        registry,
        moduleFns,
        errorStrategy,
        joinStrategy
      )

      // Deploy via lifecycle manager
      result <- manager.deploy(streamId, req.name, graph)
    } yield result.map(managed => toStreamInfo(managed))
  }

  /** Extract module functions from the constellation instance for streaming.
    *
    * For each module in the DAG, looks up the Module.Uninitialized from the constellation, builds a
    * synthetic single-node DAG, and wraps it as `CValue => IO[CValue]` using `Runtime.run`. This
    * mirrors the pattern in `ModuleHttpRoutes.runSyntheticDag`.
    */
  private def extractModuleFunctions(
      dagSpec: DagSpec
  ): IO[Map[UUID, CValue => IO[CValue]]] =
    dagSpec.modules.toList
      .traverse { case (moduleId, spec) =>
        constellation.getModuleByName(spec.name).flatMap {
          case None =>
            logger.warn(
              s"Module '${spec.name}' not found, streaming will passthrough for module $moduleId"
            ) *>
              IO.pure(Some(moduleId -> (((input: CValue) => IO.pure(input)): CValue => IO[CValue])))
          case Some(module) =>
            buildStreamingModuleFn(spec, module).map { fn =>
              Some(moduleId -> fn)
            }
        }
      }
      .map(_.flatten.toMap)

  /** Build a `CValue => IO[CValue]` wrapper for a module by constructing a synthetic single-node
    * DAG and delegating to `Runtime.run` on each invocation.
    *
    * Each streaming element gets a fresh Runtime context — the module is initialized and executed
    * per element, matching the behavior of single-mode execution.
    */
  private def buildStreamingModuleFn(
      spec: ModuleNodeSpec,
      module: Module.Uninitialized
  ): IO[CValue => IO[CValue]] = {
    val syntheticModuleId = UUID.randomUUID()

    // Create input data nodes — one per consumes field
    val inputNodes: Map[UUID, (String, CType)] =
      spec.consumes.map { case (fieldName, ctype) =>
        UUID.randomUUID() -> (fieldName, ctype)
      }

    // Create output data nodes — one per produces field
    val outputNodes: Map[UUID, (String, CType)] =
      spec.produces.map { case (fieldName, ctype) =>
        UUID.randomUUID() -> (fieldName, ctype)
      }

    // Build data node specs with nicknames mapping the synthetic module to field names
    val dataSpecs: Map[UUID, DataNodeSpec] =
      inputNodes.map { case (uuid, (name, ctype)) =>
        uuid -> DataNodeSpec(
          name = name,
          nicknames = Map(syntheticModuleId -> name),
          cType = ctype
        )
      } ++
        outputNodes.map { case (uuid, (name, ctype)) =>
          uuid -> DataNodeSpec(
            name = name,
            nicknames = Map(syntheticModuleId -> name),
            cType = ctype
          )
        }

    val inEdges: Set[(UUID, UUID)]  = inputNodes.keySet.map(dataId => (dataId, syntheticModuleId))
    val outEdges: Set[(UUID, UUID)] = outputNodes.keySet.map(dataId => (syntheticModuleId, dataId))
    val outputBindings: Map[String, UUID] =
      outputNodes.map { case (uuid, (name, _)) => name -> uuid }

    val syntheticDag = DagSpec(
      metadata = ComponentMetadata.empty(s"__stream_${spec.name}"),
      modules = Map(syntheticModuleId -> spec),
      data = dataSpecs,
      inEdges = inEdges,
      outEdges = outEdges,
      declaredOutputs = outputNodes.values.map(_._1).toList,
      outputBindings = outputBindings
    )

    IO.pure { (input: CValue) =>
      // Map single CValue to the module's input fields
      val inputMap: Map[String, CValue] = spec.consumes.keys.toList match {
        case singleField :: Nil => Map(singleField -> input)
        case _ =>
          input match {
            case CValue.CProduct(fields, _) => fields
            case _ => spec.consumes.map { case (fieldName, _) => fieldName -> input }
          }
      }

      Runtime.run(syntheticDag, inputMap, Map(syntheticModuleId -> module)).map { state =>
        // Extract output CValue from runtime state
        spec.produces.keys.toList match {
          case singleField :: Nil =>
            outputNodes
              .collectFirst {
                case (uuid, (name, _)) if name == singleField =>
                  state.data.get(uuid).map(_.value).getOrElse(input)
              }
              .getOrElse(input)
          case _ =>
            val fields = outputNodes.flatMap { case (uuid, (name, _)) =>
              state.data.get(uuid).map(ev => name -> ev.value)
            }
            CValue.CProduct(fields, spec.produces)
        }
      }
    }
  }

  private def toStreamInfo(
      managed: ManagedStream,
      metrics: Option[StreamMetricsSummary] = None
  ): StreamInfoResponse =
    StreamInfoResponse(
      id = managed.id,
      name = managed.name,
      status = managed.status match {
        case StreamStatus.Running   => "running"
        case StreamStatus.Failed(e) => s"failed: $e"
        case StreamStatus.Stopped   => "stopped"
      },
      startedAt = managed.startedAt.toString,
      metrics = metrics
    )

  private def toMetricsSummary(
      snap: io.constellation.stream.StreamMetricsSnapshot
  ): StreamMetricsSummary =
    StreamMetricsSummary(
      totalElements = snap.totalElements,
      totalErrors = snap.totalErrors,
      totalDlq = snap.totalDlq,
      perModule = snap.perModule.map { case (name, m) =>
        name -> ModuleMetrics(m.elementsProcessed, m.errors, m.dlqCount)
      }
    )

  private def parseErrorStrategy(s: String): StreamErrorStrategy = s.toLowerCase match {
    case "skip"      => StreamErrorStrategy.Skip
    case "log"       => StreamErrorStrategy.Log
    case "propagate" => StreamErrorStrategy.Propagate
    case "dlq"       => StreamErrorStrategy.Dlq
    case _           => StreamErrorStrategy.Log
  }

  private def parseJoinStrategy(s: String): JoinStrategy = s.toLowerCase match {
    case "zip"            => JoinStrategy.Zip
    case "combinelatest"  => JoinStrategy.CombineLatest
    case "combine_latest" => JoinStrategy.CombineLatest
    case _                => JoinStrategy.CombineLatest
  }
}
