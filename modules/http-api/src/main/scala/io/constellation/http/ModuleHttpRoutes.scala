package io.constellation.http

import java.util.UUID

import cats.effect.IO
import cats.implicits.*

import io.constellation.http.ApiModels.*
import io.constellation.{
  CType,
  CValue,
  ComponentMetadata,
  Constellation,
  DagSpec,
  DataNodeSpec,
  JsonCValueConverter,
  ModuleNodeSpec,
  Runtime
}

import io.circe.Json
import io.circe.syntax.*
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/** HTTP routes for direct module invocation and discovery.
  *
  * Provides:
  *   - `GET /modules/published` — list all modules marked with `.httpEndpoint()`
  *   - `POST /modules/:name/invoke` — invoke a published module with JSON inputs
  *
  * Invocation builds a minimal synthetic single-node DAG and delegates to `Runtime.run()`, reusing
  * all existing execution infrastructure (timeouts, deferred tables, error handling).
  */
class ModuleHttpRoutes(constellation: Constellation) {

  private val logger: Logger[IO] = Slf4jLogger.getLoggerFromClass[IO](classOf[ModuleHttpRoutes])

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    // ===== Discovery =====

    case GET -> Root / "modules" / "published" =>
      for {
        specs <- constellation.publishedModules
        infos = specs.map { spec =>
          PublishedModuleInfo(
            name = spec.name,
            description = spec.description,
            version = s"${spec.majorVersion}.${spec.minorVersion}",
            tags = spec.tags,
            endpoint = s"/modules/${spec.name}/invoke",
            inputs = spec.consumes.map { case (k, v) => k -> v.toString },
            outputs = spec.produces.map { case (k, v) => k -> v.toString }
          )
        }
        resp <- Ok(PublishedModulesResponse(infos))
      } yield resp

    // ===== Invocation =====

    case req @ POST -> Root / "modules" / name / "invoke" =>
      (for {
        moduleOpt <- constellation.getModuleByName(name)
        result <- moduleOpt match {
          case None =>
            NotFound(ErrorResponse("NotFound", s"Module '$name' not found"))

          case Some(module) if !module.spec.httpConfig.exists(_.published) =>
            NotFound(
              ErrorResponse("NotFound", s"Module '$name' is not published as an HTTP endpoint")
            )

          case Some(module) =>
            for {
              body       <- req.as[Json]
              jsonInputs <- IO.fromEither(parseJsonInputs(body))
              cvalues    <- convertInputs(jsonInputs, module.spec)
              state      <- runSyntheticDag(module.spec, module, cvalues)
              outputs    <- extractOutputs(state)
              resp       <- Ok(ModuleInvokeResponse(success = true, outputs = outputs, module = Some(name)))
            } yield resp
        }
      } yield result).handleErrorWith { err =>
        val message = Option(err.getMessage).getOrElse(err.getClass.getSimpleName)
        err match {
          case _: IllegalArgumentException =>
            BadRequest(
              ModuleInvokeResponse(success = false, error = Some(message), module = Some(name))
            )
          case _ =>
            logger.error(err)(s"Module invoke failed: $name") *>
              InternalServerError(
                ModuleInvokeResponse(success = false, error = Some(message), module = Some(name))
              )
        }
      }
  }

  /** Parse the request body as a flat JSON object of field → value. */
  private def parseJsonInputs(body: Json): Either[IllegalArgumentException, Map[String, Json]] =
    body.asObject match {
      case Some(obj) => Right(obj.toMap)
      case None =>
        Left(new IllegalArgumentException("Request body must be a JSON object"))
    }

  /** Convert JSON inputs to CValues using the module's consumes schema. */
  private def convertInputs(
      jsonInputs: Map[String, Json],
      spec: ModuleNodeSpec
  ): IO[Map[String, CValue]] =
    spec.consumes.toList
      .traverse { case (fieldName, ctype) =>
        jsonInputs.get(fieldName) match {
          case Some(json) =>
            JsonCValueConverter.jsonToCValue(json, ctype, fieldName) match {
              case Right(cvalue) => IO.pure(fieldName -> cvalue)
              case Left(error) =>
                IO.raiseError(new IllegalArgumentException(s"Input '$fieldName': $error"))
            }
          case None =>
            IO.raiseError(
              new IllegalArgumentException(s"Missing required input: '$fieldName'")
            )
        }
      }
      .map(_.toMap)

  /** Build a synthetic single-node DAG and execute it via `Runtime.run()`. */
  private def runSyntheticDag(
      spec: ModuleNodeSpec,
      module: io.constellation.Module.Uninitialized,
      inputs: Map[String, CValue]
  ): IO[Runtime.State] = {
    val moduleId = UUID.randomUUID()

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

    // Build data node specs
    val dataSpecs: Map[UUID, DataNodeSpec] =
      inputNodes.map { case (uuid, (name, ctype)) =>
        uuid -> DataNodeSpec(
          name = name,
          nicknames = Map(moduleId -> name),
          cType = ctype
        )
      } ++
        outputNodes.map { case (uuid, (name, ctype)) =>
          uuid -> DataNodeSpec(
            name = name,
            nicknames = Map(moduleId -> name),
            cType = ctype
          )
        }

    // Build edges
    val inEdges: Set[(UUID, UUID)]  = inputNodes.keySet.map(dataId => (dataId, moduleId))
    val outEdges: Set[(UUID, UUID)] = outputNodes.keySet.map(dataId => (moduleId, dataId))

    // Build output bindings
    val outputBindings: Map[String, UUID] =
      outputNodes.map { case (uuid, (name, _)) => name -> uuid }

    val dagSpec = DagSpec(
      metadata = ComponentMetadata.empty(s"__invoke_${spec.name}"),
      modules = Map(moduleId -> spec),
      data = dataSpecs,
      inEdges = inEdges,
      outEdges = outEdges,
      declaredOutputs = outputNodes.values.map(_._1).toList,
      outputBindings = outputBindings
    )

    Runtime.run(dagSpec, inputs, Map(moduleId -> module))
  }

  /** Extract output CValues from Runtime.State and convert to JSON. */
  private def extractOutputs(state: Runtime.State): IO[Map[String, Json]] =
    ExecutionHelper.extractOutputs(state)
}
