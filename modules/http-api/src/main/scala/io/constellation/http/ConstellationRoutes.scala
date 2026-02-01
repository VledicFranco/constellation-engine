package io.constellation.http

import cats.data.EitherT
import cats.effect.IO
import cats.implicits.*
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import io.constellation.{CValue, Constellation, JsonCValueConverter, ProgramImage, Runtime}
import io.constellation.execution.{ConstellationLifecycle, GlobalScheduler, QueueFullException}
import io.constellation.http.ApiModels.*
import io.constellation.errors.{ApiError, ErrorHandling}
import io.constellation.lang.{CachingLangCompiler, LangCompiler}
import io.constellation.lang.semantic.FunctionRegistry
import io.circe.syntax.*
import io.circe.Json

import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import org.http4s.{DecodeFailure, DecodeResult, EntityDecoder, MalformedMessageBodyFailure, MediaType}
import org.http4s.headers.`Content-Type`
import org.typelevel.ci.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/** HTTP routes for the Constellation Engine API */
class ConstellationRoutes(
    constellation: Constellation,
    compiler: LangCompiler,
    functionRegistry: FunctionRegistry,
    scheduler: Option[GlobalScheduler] = None,
    lifecycle: Option[ConstellationLifecycle] = None
) {

  private val logger: Logger[IO] = Slf4jLogger.getLoggerFromClass[IO](classOf[ConstellationRoutes])

  // Maximum request body size (10MB)
  private val maxBodySize: Long = 10 * 1024 * 1024

  /** Extract or generate a request ID for tracing. */
  private def requestId(req: org.http4s.Request[IO]): String =
    req.headers.get(ci"X-Request-ID").map(_.head.value)
      .getOrElse(UUID.randomUUID().toString)

  /** Null-safe error message extraction. */
  private def safeMessage(t: Throwable): String =
    Option(t.getMessage).getOrElse(t.getClass.getSimpleName)

  /** Validate a program reference (name or hash).
    *
    * Refs can be:
    * - Program name (any non-empty string, max 256 chars)
    * - SHA-256 structural hash (exactly 64 hex characters)
    *
    * If a ref is exactly 64 chars, it MUST be valid hex (treated as hash).
    */
  private def validateRef(ref: String): Either[String, String] = {
    if (ref.isBlank) {
      Left("Program reference cannot be blank")
    } else if (ref.length == 64) {
      // Must be a SHA-256 hash - validate it's valid hex
      if (ref.matches("[a-fA-F0-9]{64}")) Right(ref)
      else Left(s"Invalid hash format: '$ref' (expected 64 hex characters)")
    } else if (ref.length > 256) {
      // Prevent excessively long names
      Left(s"Program reference too long: ${ref.length} characters (max 256)")
    } else {
      // Treat as program name
      Right(ref)
    }
  }

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    // Compile constellation-lang source code
    // Now stores ProgramImage in ProgramStore and returns structuralHash/syntacticHash
    case req @ POST -> Root / "compile" =>
      for {
        compileReq <- req.as[CompileRequest]
        effectiveName = compileReq.effectiveName
        result <- effectiveName match {
          case Some(n) => compiler.compileIO(compileReq.source, n)
          case None    => compiler.compileIO(compileReq.source, "unnamed")
        }
        response <- result match {
          case Right(compiled) =>
            val image = compiled.program.image
            for {
              // Store the image in ProgramStore (content-addressed)
              _ <- constellation.programStore.store(image)
              // Create alias if name was provided
              _ <- effectiveName.traverse_(n => constellation.programStore.alias(n, image.structuralHash))
              // Also store in legacy DagRegistry for backward compatibility
              _ <- effectiveName.traverse_(n => constellation.setDag(n, compiled.program.image.dagSpec))
              resp <- Ok(
                CompileResponse(
                  success = true,
                  structuralHash = Some(image.structuralHash),
                  syntacticHash = Some(image.syntacticHash),
                  dagName = effectiveName,
                  name = effectiveName
                )
              )
            } yield resp
          case Left(errors) =>
            BadRequest(
              CompileResponse(
                success = false,
                errors = errors.map(_.message)
              )
            )
        }
      } yield response

    // Execute a program by reference (name, structural hash, or legacy dagName)
    case req @ POST -> Root / "execute" =>
      val reqId = requestId(req)
      (for {
        execReq <- req.as[ExecuteRequest]
        effectiveRef = execReq.effectiveRef
        result <- effectiveRef match {
          case None =>
            BadRequest(ExecuteResponse(success = false, error = Some("Missing 'ref' or 'dagName' field")))
          case Some(ref) =>
            validateRef(ref) match {
              case Left(validationError) =>
                BadRequest(ExecuteResponse(success = false, error = Some(s"Invalid ref: $validationError")))
              case Right(validatedRef) =>
                executeByRef(validatedRef, execReq.inputs).value.flatMap {
                  case Right(outputs) =>
                    Ok(ExecuteResponse(success = true, outputs = outputs, error = None))
                  case Left(ApiError.NotFoundError(_, name)) =>
                    NotFound(ErrorResponse(error = "NotFound", message = s"Program '$name' not found", requestId = Some(reqId)))
                  case Left(ApiError.InputError(msg)) =>
                    BadRequest(ExecuteResponse(success = false, error = Some(s"Input error: $msg")))
                  case Left(apiErr) =>
                    logger.error(s"[$reqId] Execute error: ${apiErr.message}") *>
                    InternalServerError(ExecuteResponse(success = false, error = Some(apiErr.message)))
                }
            }
        }
      } yield result).handleErrorWith {
        case _: QueueFullException =>
          TooManyRequests(ErrorResponse(error = "QueueFull", message = "Server is overloaded, try again later", requestId = Some(reqId)))
        case _: ConstellationLifecycle.ShutdownRejectedException =>
          ServiceUnavailable(ErrorResponse(error = "ShuttingDown", message = "Server is shutting down", requestId = Some(reqId)))
        case error =>
          logger.error(error)(s"[$reqId] Unexpected error in /execute") *>
          InternalServerError(
            ExecuteResponse(success = false, error = Some(s"Unexpected error: ${safeMessage(error)}"))
          )
      }

    // Compile and run a script in one step
    // Now stores the image and returns structuralHash
    case req @ POST -> Root / "run" =>
      val reqId = requestId(req)
      (for {
        runReq <- req.as[RunRequest]
        result <- compileStoreAndRun(runReq).value
        response <- result match {
          case Right((outputs, structuralHash)) =>
            Ok(RunResponse(success = true, outputs = outputs, structuralHash = Some(structuralHash)))
          case Left(ApiError.CompilationError(errors)) =>
            BadRequest(RunResponse(success = false, compilationErrors = errors))
          case Left(ApiError.InputError(msg)) =>
            BadRequest(RunResponse(success = false, error = Some(s"Input error: $msg")))
          case Left(apiErr) =>
            logger.error(s"[$reqId] Run error: ${apiErr.message}") *>
            InternalServerError(RunResponse(success = false, error = Some(apiErr.message)))
        }
      } yield response).handleErrorWith {
        case _: QueueFullException =>
          TooManyRequests(ErrorResponse(error = "QueueFull", message = "Server is overloaded, try again later", requestId = Some(reqId)))
        case _: ConstellationLifecycle.ShutdownRejectedException =>
          ServiceUnavailable(ErrorResponse(error = "ShuttingDown", message = "Server is shutting down", requestId = Some(reqId)))
        case error =>
          logger.error(error)(s"[$reqId] Unexpected error in /run") *>
          InternalServerError(
            RunResponse(success = false, error = Some(s"Unexpected error: ${safeMessage(error)}"))
          )
      }

    // ---------------------------------------------------------------------------
    // Program management endpoints (Phase 5)
    // ---------------------------------------------------------------------------

    // List all stored programs
    case GET -> Root / "programs" =>
      for {
        images  <- constellation.programStore.listImages
        aliases <- constellation.programStore.listAliases
        // Build reverse map: structuralHash -> List[alias]
        aliasMap = aliases.toList.groupMap(_._2)(_._1)
        summaries = images.map { img =>
          ProgramSummary(
            structuralHash = img.structuralHash,
            syntacticHash = img.syntacticHash,
            aliases = aliasMap.getOrElse(img.structuralHash, Nil),
            compiledAt = img.compiledAt.toString,
            moduleCount = img.dagSpec.modules.size,
            declaredOutputs = img.dagSpec.declaredOutputs
          )
        }
        response <- Ok(ProgramListResponse(summaries))
      } yield response

    // Get program metadata by reference (name or structural hash)
    case GET -> Root / "programs" / ref =>
      for {
        imageOpt <- resolveImage(ref)
        aliases  <- constellation.programStore.listAliases
        response <- imageOpt match {
          case Some(img) =>
            val imageAliases = aliases.toList.collect {
              case (name, hash) if hash == img.structuralHash => name
            }
            val modules = img.dagSpec.modules.values.map { spec =>
              ModuleInfo(
                name = spec.name,
                description = spec.description,
                version = s"${spec.majorVersion}.${spec.minorVersion}",
                inputs = spec.consumes.map { case (k, v) => k -> v.toString },
                outputs = spec.produces.map { case (k, v) => k -> v.toString }
              )
            }.toList
            val inputSchema = img.dagSpec.userInputDataNodes.values.map { ds =>
              ds.name -> ds.cType.toString
            }.toMap
            val outputSchema = img.dagSpec.declaredOutputs.flatMap { outName =>
              img.dagSpec.outputBindings.get(outName).flatMap { uuid =>
                img.dagSpec.data.get(uuid).map(ds => outName -> ds.cType.toString)
              }
            }.toMap
            Ok(ProgramDetailResponse(
              structuralHash = img.structuralHash,
              syntacticHash = img.syntacticHash,
              aliases = imageAliases,
              compiledAt = img.compiledAt.toString,
              modules = modules,
              declaredOutputs = img.dagSpec.declaredOutputs,
              inputSchema = inputSchema,
              outputSchema = outputSchema
            ))
          case None =>
            NotFound(ErrorResponse(error = "NotFound", message = s"Program '$ref' not found"))
        }
      } yield response

    // Delete a program by reference
    case DELETE -> Root / "programs" / ref =>
      for {
        imageOpt <- resolveImage(ref)
        response <- imageOpt match {
          case None =>
            NotFound(ErrorResponse(error = "NotFound", message = s"Program '$ref' not found"))
          case Some(img) =>
            for {
              aliases <- constellation.programStore.listAliases
              pointingAliases = aliases.toList.collect {
                case (name, hash) if hash == img.structuralHash => name
              }
              resp <- if (pointingAliases.nonEmpty) then {
                Conflict(ErrorResponse(
                  error = "AliasConflict",
                  message = s"Cannot delete program: aliases [${pointingAliases.mkString(", ")}] point to it"
                ))
              } else {
                constellation.programStore.remove(img.structuralHash).flatMap { removed =>
                  if (removed) Ok(Json.obj("deleted" -> Json.fromBoolean(true)))
                  else NotFound(ErrorResponse(error = "NotFound", message = s"Program '$ref' not found"))
                }
              }
            } yield resp
        }
      } yield response

    // Repoint an alias to a different structural hash
    case req @ PUT -> Root / "programs" / name / "alias" =>
      for {
        aliasReq <- req.as[AliasRequest]
        imageOpt <- constellation.programStore.get(aliasReq.structuralHash)
        response <- imageOpt match {
          case None =>
            NotFound(ErrorResponse(
              error = "NotFound",
              message = s"Program with hash '${aliasReq.structuralHash}' not found"
            ))
          case Some(_) =>
            constellation.programStore.alias(name, aliasReq.structuralHash).flatMap { _ =>
              Ok(Json.obj(
                "name" -> Json.fromString(name),
                "structuralHash" -> Json.fromString(aliasReq.structuralHash)
              ))
            }
        }
      } yield response

    // ---------------------------------------------------------------------------
    // Legacy DAG endpoints (kept for backward compatibility)
    // ---------------------------------------------------------------------------

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
      lifecycle match {
        case Some(lc) =>
          lc.state.flatMap {
            case io.constellation.execution.LifecycleState.Running =>
              Ok(Json.obj("status" -> Json.fromString("ok")))
            case io.constellation.execution.LifecycleState.Draining =>
              ServiceUnavailable(Json.obj("status" -> Json.fromString("draining")))
            case io.constellation.execution.LifecycleState.Stopped =>
              ServiceUnavailable(Json.obj("status" -> Json.fromString("stopped")))
          }
        case None =>
          Ok(Json.obj("status" -> Json.fromString("ok")))
      }

    // Metrics endpoint for performance monitoring
    case GET -> Root / "metrics" =>
      for {
        uptimeSeconds <- IO.pure(java.time.Duration.between(ConstellationRoutes.startTime, Instant.now()).getSeconds)
        requestCount <- IO.pure(ConstellationRoutes.requestCount.incrementAndGet())

        cacheStats = compiler match {
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

        // Get scheduler stats if available
        schedulerStats <- scheduler match {
          case Some(s) =>
            s.stats.map { stats =>
              Some(Json.obj(
                "enabled" -> Json.fromBoolean(true),
                "activeCount" -> Json.fromInt(stats.activeCount),
                "queuedCount" -> Json.fromInt(stats.queuedCount),
                "totalSubmitted" -> Json.fromLong(stats.totalSubmitted),
                "totalCompleted" -> Json.fromLong(stats.totalCompleted),
                "highPriorityCompleted" -> Json.fromLong(stats.highPriorityCompleted),
                "lowPriorityCompleted" -> Json.fromLong(stats.lowPriorityCompleted),
                "starvationPromotions" -> Json.fromLong(stats.starvationPromotions)
              ))
            }
          case None =>
            IO.pure(Some(Json.obj("enabled" -> Json.fromBoolean(false))))
        }

        response <- Ok(Json.obj(
          "timestamp" -> Json.fromString(Instant.now().toString),
          "cache" -> cacheStats.getOrElse(Json.Null),
          "scheduler" -> schedulerStats.getOrElse(Json.Null),
          "server" -> Json.obj(
            "uptime_seconds" -> Json.fromLong(uptimeSeconds),
            "requests_total" -> Json.fromLong(requestCount)
          )
        ))
      } yield response
  }

  // ========== Private Helper Methods ==========

  /** Resolve a program image from a reference (name or "sha256:<hash>"). */
  private def resolveImage(ref: String): IO[Option[ProgramImage]] =
    if (ref.startsWith("sha256:"))
      constellation.programStore.get(ref.stripPrefix("sha256:"))
    else
      constellation.programStore.getByName(ref)

  /** Execute a program by reference using the new API.
    *
    * First tries ProgramStore (content-addressed). Falls back to legacy DagRegistry.
    */
  private def executeByRef(
      ref: String,
      jsonInputs: Map[String, Json]
  ): EitherT[IO, ApiError, Map[String, Json]] =
    EitherT(
      resolveImage(ref).flatMap {
        case Some(image) =>
          // New path: use ProgramStore + constellation.run
          val dagSpec = image.dagSpec
          convertInputs(jsonInputs, dagSpec).value.flatMap {
            case Left(err) => IO.pure(Left(err))
            case Right(inputs) =>
              val loaded = io.constellation.ProgramImage.rehydrate(image)
              constellation.run(loaded, inputs).attempt.map {
                case Right(sig) =>
                  val outputs = sig.outputs.map { case (k, v) =>
                    k -> JsonCValueConverter.cValueToJson(v)
                  }
                  Right(outputs)
                case Left(err) =>
                  Left(ApiError.ExecutionError(s"Execution failed: ${err.getMessage}"))
              }
          }
        case None =>
          // Fall back to legacy DagRegistry
          executeStoredDag(ref, jsonInputs).value
      }
    )

  /** Legacy: execute a stored DAG by name using EitherT for clean error handling */
  private def executeStoredDag(dagName: String, jsonInputs: Map[String, Json]): EitherT[IO, ApiError, Map[String, Json]] =
    for {
      dagSpec <- EitherT(
        constellation.getDag(dagName).map {
          case Some(spec) => Right(spec)
          case None       => Left(ApiError.NotFoundError("Program", dagName))
        }
      )
      inputs  <- convertInputs(jsonInputs, dagSpec)
      state   <- executeDag(dagName, inputs)
      outputs <- extractOutputs(state)
    } yield outputs

  /** Compile, store, and run a script in one step. Returns (outputs, structuralHash). */
  private def compileStoreAndRun(req: RunRequest): EitherT[IO, ApiError, (Map[String, Json], String)] =
    for {
      compiled <- EitherT(
        compiler.compileIO(req.source, "ephemeral").map(_.leftMap { errors =>
          ApiError.CompilationError(errors.map(_.message))
        })
      )
      image = compiled.program.image
      // Store image in ProgramStore for dedup and future reference
      _ <- EitherT.liftF(constellation.programStore.store(image))
      inputs  <- convertInputs(req.inputs, compiled.program.image.dagSpec)
      // Use new API: constellation.run with LoadedProgram
      sig <- ErrorHandling.liftIO(constellation.run(compiled.program, inputs)) { t =>
        ApiError.ExecutionError(s"Execution failed: ${t.getMessage}")
      }
    } yield {
      val outputs = sig.outputs.map { case (k, v) =>
        k -> JsonCValueConverter.cValueToJson(v)
      }
      (outputs, image.structuralHash)
    }

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
    new ConstellationRoutes(constellation, compiler, functionRegistry, None, None)

  def apply(
      constellation: Constellation,
      compiler: LangCompiler,
      functionRegistry: FunctionRegistry,
      scheduler: GlobalScheduler
  ): ConstellationRoutes =
    new ConstellationRoutes(constellation, compiler, functionRegistry, Some(scheduler), None)

  def apply(
      constellation: Constellation,
      compiler: LangCompiler,
      functionRegistry: FunctionRegistry,
      scheduler: GlobalScheduler,
      lifecycle: ConstellationLifecycle
  ): ConstellationRoutes =
    new ConstellationRoutes(constellation, compiler, functionRegistry, Some(scheduler), Some(lifecycle))
}
