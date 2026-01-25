package io.constellation.http

import cats.data.EitherT
import cats.effect.IO
import cats.implicits.*
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import io.constellation.{CValue, Constellation, Runtime}
import io.constellation.http.ApiModels.*
import io.constellation.errors.{ApiError, ErrorHandling}
import io.constellation.lang.{CachingLangCompiler, LangCompiler}
import io.constellation.lang.semantic.FunctionRegistry
import io.circe.syntax.*
import io.circe.Json

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/** HTTP routes for the Constellation Engine API */
class ConstellationRoutes(
    constellation: Constellation,
    compiler: LangCompiler,
    functionRegistry: FunctionRegistry
) {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    // Compile constellation-lang source code
    case req @ POST -> Root / "compile" =>
      for {
        compileReq <- req.as[CompileRequest]
        result = compiler.compile(compileReq.source, compileReq.dagName)
        response <- result match {
          case Right(compiled) =>
            constellation.setDag(compileReq.dagName, compiled.dagSpec).flatMap { _ =>
              Ok(
                CompileResponse(
                  success = true,
                  dagName = Some(compileReq.dagName)
                )
              )
            }
          case Left(errors) =>
            BadRequest(
              CompileResponse(
                success = false,
                errors = errors.map(_.message)
              )
            )
        }
      } yield response

    // Execute a DAG with inputs
    case req @ POST -> Root / "execute" =>
      (for {
        execReq <- req.as[ExecuteRequest]
        result  <- executeStoredDag(execReq).value
        response <- result match {
          case Right(outputs) =>
            Ok(ExecuteResponse(success = true, outputs = outputs, error = None))
          case Left(ApiError.NotFoundError(_, name)) =>
            NotFound(ErrorResponse(error = "DagNotFound", message = s"DAG '$name' not found"))
          case Left(ApiError.InputError(msg)) =>
            BadRequest(ExecuteResponse(success = false, error = Some(s"Input error: $msg")))
          case Left(error) =>
            InternalServerError(ExecuteResponse(success = false, error = Some(error.message)))
        }
      } yield response).handleErrorWith { error =>
        InternalServerError(
          ExecuteResponse(success = false, error = Some(s"Unexpected error: ${error.getMessage}"))
        )
      }

    // Compile and run a script in one step (without storing the DAG)
    case req @ POST -> Root / "run" =>
      (for {
        runReq <- req.as[RunRequest]
        result <- compileAndRunDag(runReq).value
        response <- result match {
          case Right(outputs) =>
            Ok(RunResponse(success = true, outputs = outputs))
          case Left(ApiError.CompilationError(errors)) =>
            BadRequest(RunResponse(success = false, compilationErrors = errors))
          case Left(ApiError.InputError(msg)) =>
            BadRequest(RunResponse(success = false, error = Some(s"Input error: $msg")))
          case Left(error) =>
            InternalServerError(RunResponse(success = false, error = Some(error.message)))
        }
      } yield response).handleErrorWith { error =>
        InternalServerError(
          RunResponse(success = false, error = Some(s"Unexpected error: ${error.getMessage}"))
        )
      }

    // List all available DAGs
    case GET -> Root / "dags" =>
      for {
        dags     <- constellation.listDags
        response <- Ok(DagListResponse(dags))
      } yield response

    // Get a specific DAG by name
    case GET -> Root / "dags" / dagName =>
      for {
        dagOpt <- constellation.getDag(dagName)
        response <- dagOpt match {
          case Some(dagSpec) =>
            Ok(
              DagResponse(
                name = dagName,
                metadata = dagSpec.metadata
              )
            )
          case None =>
            NotFound(
              ErrorResponse(
                error = "DagNotFound",
                message = s"DAG '$dagName' not found"
              )
            )
        }
      } yield response

    // List all available modules
    case GET -> Root / "modules" =>
      for {
        modules <- constellation.getModules
        moduleInfos = modules.map { spec =>
          ModuleInfo(
            name = spec.name,
            description = spec.description,
            version = s"${spec.majorVersion}.${spec.minorVersion}",
            inputs = spec.consumes.map { case (k, v) => k -> v.toString },
            outputs = spec.produces.map { case (k, v) => k -> v.toString }
          )
        }
        response <- Ok(ModuleListResponse(moduleInfos))
      } yield response

    // List all available namespaces
    case GET -> Root / "namespaces" =>
      val namespaceList = functionRegistry.namespaces.toList.sorted
      Ok(NamespaceListResponse(namespaceList))

    // List functions in a specific namespace
    case GET -> Root / "namespaces" / namespace =>
      val functions = functionRegistry.all
        .filter(sig =>
          sig.namespace.exists(ns => ns == namespace || ns.startsWith(namespace + "."))
        )
        .map { sig =>
          FunctionInfo(
            name = sig.name,
            qualifiedName = sig.qualifiedName,
            params = sig.params.map { case (name, typ) => s"$name: ${typ.prettyPrint}" },
            returns = sig.returns.prettyPrint
          )
        }
      if functions.nonEmpty then {
        Ok(NamespaceFunctionsResponse(namespace, functions))
      } else {
        NotFound(
          ErrorResponse(
            error = "NamespaceNotFound",
            message = s"Namespace '$namespace' not found or has no functions"
          )
        )
      }

    // Health check endpoint
    case GET -> Root / "health" =>
      Ok(Json.obj("status" -> Json.fromString("ok")))

    // Metrics endpoint for performance monitoring
    case GET -> Root / "metrics" =>
      val uptimeSeconds = java.time.Duration.between(ConstellationRoutes.startTime, Instant.now()).getSeconds
      val requestCount = ConstellationRoutes.requestCount.incrementAndGet()

      val cacheStats = compiler match {
        case c: CachingLangCompiler =>
          val s = c.cacheStats
          Some(Json.obj(
            "hits" -> Json.fromLong(s.hits),
            "misses" -> Json.fromLong(s.misses),
            "hitRate" -> Json.fromDoubleOrNull(s.hitRate),
            "evictions" -> Json.fromLong(s.evictions),
            "entries" -> Json.fromInt(s.entries)
          ))
        case _ => None
      }

      Ok(Json.obj(
        "timestamp" -> Json.fromString(Instant.now().toString),
        "cache" -> cacheStats.getOrElse(Json.Null),
        "server" -> Json.obj(
          "uptime_seconds" -> Json.fromLong(uptimeSeconds),
          "requests_total" -> Json.fromLong(requestCount)
        )
      ))
  }

  // ========== Private Helper Methods ==========

  /** Execute a stored DAG using EitherT for clean error handling */
  private def executeStoredDag(req: ExecuteRequest): EitherT[IO, ApiError, Map[String, Json]] =
    for {
      dagSpec <- EitherT(
        constellation.getDag(req.dagName).map {
          case Some(spec) => Right(spec)
          case None       => Left(ApiError.NotFoundError("DAG", req.dagName))
        }
      )
      inputs  <- convertInputs(req.inputs, dagSpec)
      state   <- executeDag(req.dagName, inputs)
      outputs <- extractOutputs(state)
    } yield outputs

  /** Compile and run a DAG in one step using EitherT */
  private def compileAndRunDag(req: RunRequest): EitherT[IO, ApiError, Map[String, Json]] =
    for {
      compiled <- EitherT.fromEither[IO](
        compiler.compile(req.source, "ephemeral").leftMap { errors =>
          ApiError.CompilationError(errors.map(_.message))
        }
      )
      inputs  <- convertInputs(req.inputs, compiled.dagSpec)
      state   <- executeDagWithModules(compiled.dagSpec, inputs, compiled.syntheticModules)
      outputs <- extractOutputs(state)
    } yield outputs

  /** Convert JSON inputs to CValue, wrapping errors */
  private def convertInputs(
      inputs: Map[String, Json],
      dagSpec: io.constellation.DagSpec
  ): EitherT[IO, ApiError, Map[String, CValue]] =
    ErrorHandling.liftIO(ExecutionHelper.convertInputs(inputs, dagSpec)) { t =>
      ApiError.InputError(t.getMessage)
    }

  /** Execute a stored DAG by name */
  private def executeDag(
      dagName: String,
      inputs: Map[String, CValue]
  ): EitherT[IO, ApiError, Runtime.State] =
    ErrorHandling.liftIO(constellation.runDag(dagName, inputs)) { t =>
      ApiError.ExecutionError(s"Execution failed: ${t.getMessage}")
    }

  /** Execute a DAG with pre-resolved modules */
  private def executeDagWithModules(
      dagSpec: io.constellation.DagSpec,
      inputs: Map[String, CValue],
      modules: Map[java.util.UUID, io.constellation.Module.Uninitialized]
  ): EitherT[IO, ApiError, Runtime.State] =
    ErrorHandling.liftIO(constellation.runDagWithModules(dagSpec, inputs, modules)) { t =>
      ApiError.ExecutionError(s"Execution failed: ${t.getMessage}")
    }

  /** Extract outputs from execution state */
  private def extractOutputs(state: Runtime.State): EitherT[IO, ApiError, Map[String, Json]] =
    ErrorHandling.liftIO(ExecutionHelper.extractOutputs(state)) { t =>
      ApiError.OutputError(t.getMessage)
    }
}

object ConstellationRoutes {
  /** Server start time for uptime calculation */
  private val startTime: Instant = Instant.now()

  /** Total request count across all endpoints */
  private val requestCount: AtomicLong = new AtomicLong(0)

  def apply(
      constellation: Constellation,
      compiler: LangCompiler,
      functionRegistry: FunctionRegistry
  ): ConstellationRoutes =
    new ConstellationRoutes(constellation, compiler, functionRegistry)
}
