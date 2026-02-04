package io.constellation.http

import cats.data.EitherT
import cats.effect.IO
import cats.implicits.*
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import io.constellation.{CValue, Constellation, DataSignature, DagSpec, JsonCValueConverter, PipelineImage, PipelineStatus, SuspensionFilter, SuspensionHandle, SuspensionSummary, InputTypeMismatchError, InputAlreadyProvidedError, ResumeInProgressError, UnknownNodeError, NodeTypeMismatchError, NodeAlreadyResolvedError, PipelineChangedError}
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
import org.http4s.{
  DecodeFailure,
  DecodeResult,
  EntityDecoder,
  MalformedMessageBodyFailure,
  MediaType,
  Response,
  Status
}
import org.http4s.headers.{`Content-Length`, `Content-Type`}
import org.typelevel.ci.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration.*

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
    req.headers
      .get(ci"X-Request-ID")
      .map(_.head.value)
      .getOrElse(UUID.randomUUID().toString)

  /** Null-safe error message extraction. */
  private def safeMessage(t: Throwable): String =
    Option(t.getMessage).getOrElse(t.getClass.getSimpleName)

  /** Compilation timeout (30 seconds). */
  private val compilationTimeout: FiniteDuration = 30.seconds

  /** Check if request body exceeds maximum allowed size. Returns Some(413 response) if too large,
    * None if OK.
    */
  private def checkBodySize(req: org.http4s.Request[IO]): Option[IO[org.http4s.Response[IO]]] =
    req.headers.get[`Content-Length`].flatMap { cl =>
      if cl.length > maxBodySize then {
        Some(
          IO.pure(
            Response[IO](Status.PayloadTooLarge)
              .withEntity(
                ErrorResponse(
                  error = "PayloadTooLarge",
                  message = s"Request body too large: ${cl.length} bytes (max ${maxBodySize})"
                )
              )
          )
        )
      } else None
    }

  /** Validate a pipeline reference (name or hash).
    *
    * Refs can be:
    *   - Pipeline name (any non-empty string, max 256 chars)
    *   - SHA-256 structural hash (exactly 64 hex characters)
    *
    * If a ref is exactly 64 chars, it MUST be valid hex (treated as hash).
    */
  private def validateRef(ref: String): Either[String, String] =
    if ref.isBlank then {
      Left("Pipeline reference cannot be blank")
    } else if ref.length == 64 then {
      // Must be a SHA-256 hash - validate it's valid hex
      if ref.matches("[a-fA-F0-9]{64}") then Right(ref)
      else Left(s"Invalid hash format: '$ref' (expected 64 hex characters)")
    } else if ref.length > 256 then {
      // Prevent excessively long names
      Left(s"Pipeline reference too long: ${ref.length} characters (max 256)")
    } else {
      // Treat as pipeline name
      Right(ref)
    }

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    // Compile constellation-lang source code
    // Now stores PipelineImage in PipelineStore and returns structuralHash/syntacticHash
    case req @ POST -> Root / "compile" =>
      checkBodySize(req) match {
        case Some(tooLarge) => tooLarge
        case None =>
          for {
            compileReq <- req.as[CompileRequest]
            effectiveName = compileReq.effectiveName
            result <- (effectiveName match {
              case Some(n) => compiler.compileIO(compileReq.source, n)
              case None    => compiler.compileIO(compileReq.source, "unnamed")
            }).timeoutTo(
              compilationTimeout,
              IO.raiseError(
                new java.util.concurrent.TimeoutException(
                  s"Compilation timed out after $compilationTimeout"
                )
              )
            )
            response <- result match {
              case Right(compiled) =>
                val image = compiled.pipeline.image
                for {
                  // Store the image in PipelineStore (content-addressed)
                  _ <- constellation.PipelineStore.store(image)
                  // Create alias if name was provided
                  _ <- effectiveName.traverse_(n =>
                    constellation.PipelineStore.alias(n, image.structuralHash)
                  )
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
      }

    // Execute a program by reference (name, structural hash, or legacy dagName)
    case req @ POST -> Root / "execute" =>
      checkBodySize(req) match {
        case Some(tooLarge) => tooLarge
        case None =>
          val reqId = requestId(req)
          (for {
            execReq <- req.as[ExecuteRequest]
            effectiveRef = execReq.effectiveRef
            result <- effectiveRef match {
              case None =>
                BadRequest(
                  ExecuteResponse(success = false, error = Some("Missing 'ref' or 'dagName' field"))
                )
              case Some(ref) =>
                validateRef(ref) match {
                  case Left(validationError) =>
                    BadRequest(
                      ExecuteResponse(
                        success = false,
                        error = Some(s"Invalid ref: $validationError")
                      )
                    )
                  case Right(validatedRef) =>
                    executeByRef(validatedRef, execReq.inputs).value.flatMap {
                      case Right((sig, dagSpec)) =>
                        val outputs = outputsToJson(sig)
                        val missing = ExecutionHelper.buildMissingInputsMap(
                          sig.inputs.keySet,
                          dagSpec
                        )
                        val isSuccess = sig.status match {
                          case PipelineStatus.Failed(_) => false
                          case _                        => true
                        }
                        autoSaveSuspension(sig) *>
                        Ok(
                          ExecuteResponse(
                            success = isSuccess,
                            outputs = outputs,
                            error = sig.status match {
                              case PipelineStatus.Failed(errors) =>
                                Some(errors.map(_.message).mkString("; "))
                              case _ => None
                            },
                            status = Some(statusString(sig.status)),
                            executionId = Some(sig.executionId.toString),
                            missingInputs =
                              if missing.nonEmpty then Some(missing) else None,
                            pendingOutputs =
                              if sig.pendingOutputs.nonEmpty then Some(sig.pendingOutputs)
                              else None,
                            resumptionCount = Some(sig.resumptionCount)
                          )
                        )
                      case Left(ApiError.NotFoundError(_, name)) =>
                        NotFound(
                          ErrorResponse(
                            error = "NotFound",
                            message = s"Pipeline '$name' not found",
                            requestId = Some(reqId)
                          )
                        )
                      case Left(ApiError.InputError(msg)) =>
                        BadRequest(
                          ExecuteResponse(success = false, error = Some(s"Input error: $msg"))
                        )
                      case Left(apiErr) =>
                        logger.error(s"[$reqId] Execute error: ${apiErr.message}") *>
                          InternalServerError(
                            ExecuteResponse(success = false, error = Some(apiErr.message))
                          )
                    }
                }
            }
          } yield result).handleErrorWith {
            case _: QueueFullException =>
              TooManyRequests(
                ErrorResponse(
                  error = "QueueFull",
                  message = "Server is overloaded, try again later",
                  requestId = Some(reqId)
                )
              )
            case _: ConstellationLifecycle.ShutdownRejectedException =>
              ServiceUnavailable(
                ErrorResponse(
                  error = "ShuttingDown",
                  message = "Server is shutting down",
                  requestId = Some(reqId)
                )
              )
            case error =>
              logger.error(error)(s"[$reqId] Unexpected error in /execute") *>
                InternalServerError(
                  ExecuteResponse(
                    success = false,
                    error = Some(s"Unexpected error: ${safeMessage(error)}")
                  )
                )
          }
      }

    // Compile and run a script in one step
    // Now stores the image and returns structuralHash
    case req @ POST -> Root / "run" =>
      checkBodySize(req) match {
        case Some(tooLarge) => tooLarge
        case None =>
          val reqId = requestId(req)
          (for {
            runReq <- req.as[RunRequest]
            result <- compileStoreAndRun(runReq).value
            response <- result match {
              case Right((sig, dagSpec, structuralHash)) =>
                val outputs = outputsToJson(sig)
                val missing = ExecutionHelper.buildMissingInputsMap(
                  sig.inputs.keySet,
                  dagSpec
                )
                val isSuccess = sig.status match {
                  case PipelineStatus.Failed(_) => false
                  case _                        => true
                }
                autoSaveSuspension(sig) *>
                Ok(
                  RunResponse(
                    success = isSuccess,
                    outputs = outputs,
                    structuralHash = Some(structuralHash),
                    error = sig.status match {
                      case PipelineStatus.Failed(errors) =>
                        Some(errors.map(_.message).mkString("; "))
                      case _ => None
                    },
                    status = Some(statusString(sig.status)),
                    executionId = Some(sig.executionId.toString),
                    missingInputs =
                      if missing.nonEmpty then Some(missing) else None,
                    pendingOutputs =
                      if sig.pendingOutputs.nonEmpty then Some(sig.pendingOutputs)
                      else None,
                    resumptionCount = Some(sig.resumptionCount)
                  )
                )
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
              TooManyRequests(
                ErrorResponse(
                  error = "QueueFull",
                  message = "Server is overloaded, try again later",
                  requestId = Some(reqId)
                )
              )
            case _: ConstellationLifecycle.ShutdownRejectedException =>
              ServiceUnavailable(
                ErrorResponse(
                  error = "ShuttingDown",
                  message = "Server is shutting down",
                  requestId = Some(reqId)
                )
              )
            case error =>
              logger.error(error)(s"[$reqId] Unexpected error in /run") *>
                InternalServerError(
                  RunResponse(
                    success = false,
                    error = Some(s"Unexpected error: ${safeMessage(error)}")
                  )
                )
          }
      }

    // ---------------------------------------------------------------------------
    // Pipeline management endpoints (Phase 5)
    // ---------------------------------------------------------------------------

    // List all stored pipelines
    case GET -> Root / "pipelines" =>
      for {
        images  <- constellation.PipelineStore.listImages
        aliases <- constellation.PipelineStore.listAliases
        // Build reverse map: structuralHash -> List[alias]
        aliasMap = aliases.toList.groupMap(_._2)(_._1)
        summaries = images.map { img =>
          PipelineSummary(
            structuralHash = img.structuralHash,
            syntacticHash = img.syntacticHash,
            aliases = aliasMap.getOrElse(img.structuralHash, Nil),
            compiledAt = img.compiledAt.toString,
            moduleCount = img.dagSpec.modules.size,
            declaredOutputs = img.dagSpec.declaredOutputs
          )
        }
        response <- Ok(PipelineListResponse(summaries))
      } yield response

    // Get pipeline metadata by reference (name or structural hash)
    case GET -> Root / "pipelines" / ref =>
      for {
        imageOpt <- resolveImage(ref)
        aliases  <- constellation.PipelineStore.listAliases
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
            Ok(
              PipelineDetailResponse(
                structuralHash = img.structuralHash,
                syntacticHash = img.syntacticHash,
                aliases = imageAliases,
                compiledAt = img.compiledAt.toString,
                modules = modules,
                declaredOutputs = img.dagSpec.declaredOutputs,
                inputSchema = inputSchema,
                outputSchema = outputSchema
              )
            )
          case None =>
            NotFound(ErrorResponse(error = "NotFound", message = s"Pipeline '$ref' not found"))
        }
      } yield response

    // Delete a pipeline by reference
    case DELETE -> Root / "pipelines" / ref =>
      for {
        imageOpt <- resolveImage(ref)
        response <- imageOpt match {
          case None =>
            NotFound(ErrorResponse(error = "NotFound", message = s"Pipeline '$ref' not found"))
          case Some(img) =>
            for {
              aliases <- constellation.PipelineStore.listAliases
              pointingAliases = aliases.toList.collect {
                case (name, hash) if hash == img.structuralHash => name
              }
              resp <-
                if pointingAliases.nonEmpty then {
                  Conflict(
                    ErrorResponse(
                      error = "AliasConflict",
                      message =
                        s"Cannot delete pipeline: aliases [${pointingAliases.mkString(", ")}] point to it"
                    )
                  )
                } else {
                  constellation.PipelineStore.remove(img.structuralHash).flatMap { removed =>
                    if removed then Ok(Json.obj("deleted" -> Json.fromBoolean(true)))
                    else
                      NotFound(
                        ErrorResponse(error = "NotFound", message = s"Pipeline '$ref' not found")
                      )
                  }
                }
            } yield resp
        }
      } yield response

    // Repoint an alias to a different structural hash
    case req @ PUT -> Root / "pipelines" / name / "alias" =>
      for {
        aliasReq <- req.as[AliasRequest]
        imageOpt <- constellation.PipelineStore.get(aliasReq.structuralHash)
        response <- imageOpt match {
          case None =>
            NotFound(
              ErrorResponse(
                error = "NotFound",
                message = s"Pipeline with hash '${aliasReq.structuralHash}' not found"
              )
            )
          case Some(_) =>
            constellation.PipelineStore.alias(name, aliasReq.structuralHash).flatMap { _ =>
              Ok(
                Json.obj(
                  "name"           -> Json.fromString(name),
                  "structuralHash" -> Json.fromString(aliasReq.structuralHash)
                )
              )
            }
        }
      } yield response

    // ---------------------------------------------------------------------------
    // Suspension management endpoints (Phase 2)
    // ---------------------------------------------------------------------------

    // List suspended executions
    case GET -> Root / "executions" =>
      constellation.suspensionStore match {
        case None =>
          Ok(ExecutionListResponse(List.empty))
        case Some(store) =>
          for {
            summaries <- store.list()
            execSummaries = summaries.map { s =>
              ExecutionSummary(
                executionId = s.executionId.toString,
                structuralHash = s.structuralHash,
                resumptionCount = s.resumptionCount,
                missingInputs = s.missingInputs.map { case (name, ctype) => name -> ctype.toString },
                createdAt = s.createdAt.toString
              )
            }
            response <- Ok(ExecutionListResponse(execSummaries))
          } yield response
      }

    // Get suspended execution detail
    case GET -> Root / "executions" / id =>
      constellation.suspensionStore match {
        case None =>
          NotFound(
            ErrorResponse(
              error = "NotFound",
              message = s"Execution '$id' not found (no suspension store configured)"
            )
          )
        case Some(store) =>
          findHandleByExecutionId(store, id).flatMap {
            case None =>
              NotFound(
                ErrorResponse(error = "NotFound", message = s"Execution '$id' not found")
              )
            case Some(handle) =>
              // Re-list with the specific filter to get the summary
              store.list(SuspensionFilter(executionId = Some(UUID.fromString(id)))).flatMap {
                case summary :: _ =>
                  Ok(
                    ExecutionSummary(
                      executionId = summary.executionId.toString,
                      structuralHash = summary.structuralHash,
                      resumptionCount = summary.resumptionCount,
                      missingInputs = summary.missingInputs.map { case (name, ctype) =>
                        name -> ctype.toString
                      },
                      createdAt = summary.createdAt.toString
                    )
                  )
                case Nil =>
                  NotFound(
                    ErrorResponse(error = "NotFound", message = s"Execution '$id' not found")
                  )
              }
          }
      }

    // Resume a suspended execution
    case req @ POST -> Root / "executions" / id / "resume" =>
      constellation.suspensionStore match {
        case None =>
          NotFound(
            ErrorResponse(
              error = "NotFound",
              message = s"Execution '$id' not found (no suspension store configured)"
            )
          )
        case Some(store) =>
          val reqId = requestId(req)
          (for {
            resumeReq <- req.as[ResumeRequest]
            handleOpt <- findHandleByExecutionId(store, id)
            response <- handleOpt match {
              case None =>
                NotFound(
                  ErrorResponse(error = "NotFound", message = s"Execution '$id' not found")
                )
              case Some(handle) =>
                // Load the suspended state to get the DagSpec for input conversion
                store.load(handle).flatMap {
                  case None =>
                    NotFound(
                      ErrorResponse(error = "NotFound", message = s"Execution '$id' not found")
                    )
                  case Some(suspended) =>
                    val dagSpec = suspended.dagSpec
                    // Convert JSON inputs to CValue using the DagSpec
                    val jsonInputs    = resumeReq.additionalInputs.getOrElse(Map.empty)
                    val jsonResolved  = resumeReq.resolvedNodes.getOrElse(Map.empty)
                    (for {
                      cvalueInputs <- convertAdditionalInputs(jsonInputs, dagSpec)
                      cvalueResolved <- convertResolvedNodes(jsonResolved, dagSpec)
                      sig <- constellation.resumeFromStore(
                        handle,
                        cvalueInputs,
                        cvalueResolved
                      )
                      // Auto-delete on completion, auto-save if still suspended
                      _ <- sig.status match {
                        case PipelineStatus.Completed =>
                          store.delete(handle).void
                        case _ =>
                          // Delete old entry, save new one
                          store.delete(handle) *> autoSaveSuspension(sig)
                      }
                    } yield sig).attempt.flatMap {
                      case Right(sig) =>
                        val outputs = outputsToJson(sig)
                        val missing = ExecutionHelper.buildMissingInputsMap(
                          sig.inputs.keySet,
                          dagSpec
                        )
                        val isSuccess = sig.status match {
                          case PipelineStatus.Failed(_) => false
                          case _                        => true
                        }
                        Ok(
                          ExecuteResponse(
                            success = isSuccess,
                            outputs = outputs,
                            error = sig.status match {
                              case PipelineStatus.Failed(errors) =>
                                Some(errors.map(_.message).mkString("; "))
                              case _ => None
                            },
                            status = Some(statusString(sig.status)),
                            executionId = Some(sig.executionId.toString),
                            missingInputs =
                              if missing.nonEmpty then Some(missing) else None,
                            pendingOutputs =
                              if sig.pendingOutputs.nonEmpty then Some(sig.pendingOutputs)
                              else None,
                            resumptionCount = Some(sig.resumptionCount)
                          )
                        )
                      case Left(_: ResumeInProgressError) =>
                        Conflict(
                          ErrorResponse(
                            error = "ResumeInProgress",
                            message = s"A resume operation is already in progress for execution '$id'",
                            requestId = Some(reqId)
                          )
                        )
                      case Left(e: InputTypeMismatchError) =>
                        BadRequest(
                          ExecuteResponse(success = false, error = Some(s"Input error: ${e.getMessage}"))
                        )
                      case Left(e: InputAlreadyProvidedError) =>
                        BadRequest(
                          ExecuteResponse(success = false, error = Some(s"Input error: ${e.getMessage}"))
                        )
                      case Left(e: UnknownNodeError) =>
                        BadRequest(
                          ExecuteResponse(success = false, error = Some(s"Input error: ${e.getMessage}"))
                        )
                      case Left(e: NodeTypeMismatchError) =>
                        BadRequest(
                          ExecuteResponse(success = false, error = Some(s"Input error: ${e.getMessage}"))
                        )
                      case Left(e: NodeAlreadyResolvedError) =>
                        BadRequest(
                          ExecuteResponse(success = false, error = Some(s"Input error: ${e.getMessage}"))
                        )
                      case Left(e: PipelineChangedError) =>
                        BadRequest(
                          ExecuteResponse(success = false, error = Some(s"Pipeline error: ${e.getMessage}"))
                        )
                      case Left(e: NoSuchElementException) =>
                        NotFound(
                          ErrorResponse(
                            error = "NotFound",
                            message = e.getMessage,
                            requestId = Some(reqId)
                          )
                        )
                      case Left(error) =>
                        logger.error(error)(s"[$reqId] Unexpected error in /executions/$id/resume") *>
                          InternalServerError(
                            ExecuteResponse(
                              success = false,
                              error = Some(s"Unexpected error: ${safeMessage(error)}")
                            )
                          )
                    }
                }
            }
          } yield response).handleErrorWith { error =>
            val rid = reqId
            logger.error(error)(s"[$rid] Unexpected error in /executions/$id/resume") *>
              InternalServerError(
                ExecuteResponse(
                  success = false,
                  error = Some(s"Unexpected error: ${safeMessage(error)}")
                )
              )
          }
      }

    // Delete a suspended execution
    case DELETE -> Root / "executions" / id =>
      constellation.suspensionStore match {
        case None =>
          NotFound(
            ErrorResponse(
              error = "NotFound",
              message = s"Execution '$id' not found (no suspension store configured)"
            )
          )
        case Some(store) =>
          findHandleByExecutionId(store, id).flatMap {
            case None =>
              NotFound(
                ErrorResponse(error = "NotFound", message = s"Execution '$id' not found")
              )
            case Some(handle) =>
              store.delete(handle).flatMap { deleted =>
                if deleted then Ok(Json.obj("deleted" -> Json.fromBoolean(true)))
                else
                  NotFound(
                    ErrorResponse(error = "NotFound", message = s"Execution '$id' not found")
                  )
              }
          }
      }

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
        uptimeSeconds <- IO.pure(
          java.time.Duration.between(ConstellationRoutes.startTime, Instant.now()).getSeconds
        )
        requestCount <- IO.pure(ConstellationRoutes.requestCount.incrementAndGet())

        cacheStats = compiler match {
          case c: CachingLangCompiler =>
            val s = c.cacheStats
            Some(
              Json.obj(
                "hits"      -> Json.fromLong(s.hits),
                "misses"    -> Json.fromLong(s.misses),
                "hitRate"   -> Json.fromDoubleOrNull(s.hitRate),
                "evictions" -> Json.fromLong(s.evictions),
                "entries"   -> Json.fromInt(s.entries)
              )
            )
          case _ => None
        }

        // Get scheduler stats if available
        schedulerStats <- scheduler match {
          case Some(s) =>
            s.stats.map { stats =>
              Some(
                Json.obj(
                  "enabled"               -> Json.fromBoolean(true),
                  "activeCount"           -> Json.fromInt(stats.activeCount),
                  "queuedCount"           -> Json.fromInt(stats.queuedCount),
                  "totalSubmitted"        -> Json.fromLong(stats.totalSubmitted),
                  "totalCompleted"        -> Json.fromLong(stats.totalCompleted),
                  "highPriorityCompleted" -> Json.fromLong(stats.highPriorityCompleted),
                  "lowPriorityCompleted"  -> Json.fromLong(stats.lowPriorityCompleted),
                  "starvationPromotions"  -> Json.fromLong(stats.starvationPromotions)
                )
              )
            }
          case None =>
            IO.pure(Some(Json.obj("enabled" -> Json.fromBoolean(false))))
        }

        response <- Ok(
          Json.obj(
            "timestamp" -> Json.fromString(Instant.now().toString),
            "cache"     -> cacheStats.getOrElse(Json.Null),
            "scheduler" -> schedulerStats.getOrElse(Json.Null),
            "server" -> Json.obj(
              "uptime_seconds" -> Json.fromLong(uptimeSeconds),
              "requests_total" -> Json.fromLong(requestCount)
            )
          )
        )
      } yield response
  }

  // ========== Private Helper Methods ==========

  /** Resolve a pipeline image from a reference (name or "sha256:<hash>"). */
  private def resolveImage(ref: String): IO[Option[PipelineImage]] =
    if ref.startsWith("sha256:") then constellation.PipelineStore.get(ref.stripPrefix("sha256:"))
    else constellation.PipelineStore.getByName(ref)

  /** Execute a pipeline by reference using the PipelineStore.
    *
    * Returns the full DataSignature and DagSpec so route handlers can populate suspension fields.
    * If inputs are incomplete, returns a Suspended DataSignature without calling the runtime.
    */
  private def executeByRef(
      ref: String,
      jsonInputs: Map[String, Json]
  ): EitherT[IO, ApiError, (DataSignature, DagSpec)] =
    EitherT(
      resolveImage(ref).flatMap {
        case Some(image) =>
          val dagSpec = image.dagSpec
          convertInputsLenient(jsonInputs, dagSpec).value.flatMap {
            case Left(err) => IO.pure(Left(err))
            case Right(inputs) =>
              val allInputNames = dagSpec.userInputDataNodes.values.map(_.name).toSet
              val missingNames  = allInputNames -- inputs.keySet
              if missingNames.nonEmpty then {
                // Short-circuit: build a Suspended DataSignature without runtime execution
                val sig = buildSuspendedSignature(dagSpec, image.structuralHash, inputs)
                IO.pure(Right((sig, dagSpec)))
              } else {
                val loaded = io.constellation.PipelineImage.rehydrate(image)
                constellation.run(loaded, inputs).attempt.map {
                  case Right(sig) =>
                    Right((sig, dagSpec))
                  case Left(err) =>
                    Left(ApiError.ExecutionError(s"Execution failed: ${err.getMessage}"))
                }
              }
          }
        case None =>
          IO.pure(Left(ApiError.NotFoundError("Pipeline", ref)))
      }
    )

  /** Compile, store, and run a script in one step.
    *
    * Returns the full DataSignature, DagSpec, and structuralHash so route handlers can populate
    * suspension fields. If inputs are incomplete, returns a Suspended DataSignature without calling
    * the runtime.
    */
  private def compileStoreAndRun(
      req: RunRequest
  ): EitherT[IO, ApiError, (DataSignature, DagSpec, String)] =
    for {
      compiled <- EitherT(
        compiler
          .compileIO(req.source, "ephemeral")
          .map(_.leftMap { errors =>
            ApiError.CompilationError(errors.map(_.message))
          })
      )
      image = compiled.pipeline.image
      dagSpec = image.dagSpec
      // Store image in PipelineStore for dedup and future reference
      _      <- EitherT.liftF(constellation.PipelineStore.store(image))
      inputs <- convertInputsLenient(req.inputs, dagSpec)
      sig <- {
        val allInputNames = dagSpec.userInputDataNodes.values.map(_.name).toSet
        val missingNames  = allInputNames -- inputs.keySet
        if missingNames.nonEmpty then {
          // Short-circuit: build a Suspended DataSignature without runtime execution
          EitherT.rightT[IO, ApiError](buildSuspendedSignature(dagSpec, image.structuralHash, inputs))
        } else {
          // All inputs present — run the pipeline
          ErrorHandling.liftIO(constellation.run(compiled.pipeline, inputs)) { t =>
            ApiError.ExecutionError(s"Execution failed: ${t.getMessage}")
          }
        }
      }
    } yield (sig, dagSpec, image.structuralHash)

  /** Convert JSON inputs to CValue, wrapping errors */
  private def convertInputs(
      inputs: Map[String, Json],
      dagSpec: io.constellation.DagSpec
  ): EitherT[IO, ApiError, Map[String, CValue]] =
    ErrorHandling.liftIO(ExecutionHelper.convertInputs(inputs, dagSpec)) { t =>
      ApiError.InputError(t.getMessage)
    }

  /** Convert JSON inputs to CValue leniently (skip missing), wrapping errors */
  private def convertInputsLenient(
      inputs: Map[String, Json],
      dagSpec: io.constellation.DagSpec
  ): EitherT[IO, ApiError, Map[String, CValue]] =
    ErrorHandling.liftIO(ExecutionHelper.convertInputsLenient(inputs, dagSpec)) { t =>
      ApiError.InputError(t.getMessage)
    }

  /** Convert a PipelineStatus to its wire string representation. */
  private def statusString(status: PipelineStatus): String = status match {
    case PipelineStatus.Completed  => "completed"
    case PipelineStatus.Suspended  => "suspended"
    case PipelineStatus.Failed(_)  => "failed"
  }

  /** Extract outputs from a DataSignature and convert CValues to JSON. */
  private def outputsToJson(sig: DataSignature): Map[String, Json] =
    sig.outputs.map { case (k, v) => k -> JsonCValueConverter.cValueToJson(v) }

  /** Convert JSON additional inputs to CValue for resume.
    *
    * Looks up each input name in the DagSpec's user input data nodes to determine the expected
    * type, then converts the JSON value accordingly. Unknown names raise UnknownNodeError so the
    * resume validation layer produces proper error messages.
    */
  private def convertAdditionalInputs(
      jsonInputs: Map[String, Json],
      dagSpec: DagSpec
  ): IO[Map[String, CValue]] = {
    val inputNameToType: Map[String, io.constellation.CType] =
      dagSpec.userInputDataNodes.values.flatMap { spec =>
        spec.nicknames.values.map(name => name -> spec.cType)
      }.toMap

    jsonInputs.toList.traverse { case (name, json) =>
      inputNameToType.get(name) match {
        case Some(ctype) =>
          JsonCValueConverter.jsonToCValue(json, ctype, name) match {
            case Right(cValue) => IO.pure(name -> cValue)
            case Left(error) =>
              IO.raiseError(new RuntimeException(s"Input '$name': $error"))
          }
        case None =>
          IO.raiseError(UnknownNodeError(name))
      }
    }.map(_.toMap)
  }

  /** Convert JSON resolved nodes to CValue for resume.
    *
    * Looks up each node name in the DagSpec's data nodes to determine the expected type and
    * converts the JSON value accordingly.
    */
  private def convertResolvedNodes(
      jsonNodes: Map[String, Json],
      dagSpec: DagSpec
  ): IO[Map[String, CValue]] = {
    val nameToType: Map[String, io.constellation.CType] =
      dagSpec.data.map { case (_, spec) => spec.name -> spec.cType }

    jsonNodes.toList.traverse { case (name, json) =>
      nameToType.get(name) match {
        case Some(ctype) =>
          JsonCValueConverter.jsonToCValue(json, ctype, name) match {
            case Right(cValue) => IO.pure(name -> cValue)
            case Left(error) =>
              IO.raiseError(new RuntimeException(s"Node '$name': $error"))
          }
        case None =>
          IO.raiseError(UnknownNodeError(name))
      }
    }.map(_.toMap)
  }

  /** Auto-save a suspended execution to the SuspensionStore if configured.
    *
    * If the constellation has a SuspensionStore and the DataSignature contains a suspendedState,
    * saves it to the store. No-op if no store is configured or execution is not suspended.
    */
  private def autoSaveSuspension(sig: DataSignature): IO[Unit] =
    (constellation.suspensionStore, sig.suspendedState) match {
      case (Some(store), Some(suspended)) =>
        store.save(suspended).void
      case _ => IO.unit
    }

  /** Find a SuspensionHandle by executionId using the SuspensionStore.
    *
    * @return
    *   Some(handle) if found, None if not found
    */
  private def findHandleByExecutionId(
      store: io.constellation.SuspensionStore,
      executionId: String
  ): IO[Option[SuspensionHandle]] =
    IO.fromTry(scala.util.Try(UUID.fromString(executionId))).attempt.flatMap {
      case Left(_) => IO.pure(None)
      case Right(uuid) =>
        store.list(SuspensionFilter(executionId = Some(uuid))).map(_.headOption.map(_.handle))
    }

  /** Build a Suspended DataSignature without executing the runtime.
    *
    * Used when inputs are incomplete — we know the pipeline will be suspended so we can
    * short-circuit and return the suspension information directly.
    */
  private def buildSuspendedSignature(
      dagSpec: io.constellation.DagSpec,
      structuralHash: String,
      inputs: Map[String, CValue]
  ): DataSignature = {
    val allInputNames  = dagSpec.userInputDataNodes.values.map(_.name).toSet
    val missingInputs  = (allInputNames -- inputs.keySet).toList.sorted
    val pendingOutputs = dagSpec.declaredOutputs // All outputs are pending when suspended pre-execution
    val execId         = UUID.randomUUID()
    val suspendedState = io.constellation.SuspendedExecution(
      executionId = execId,
      structuralHash = structuralHash,
      resumptionCount = 0,
      dagSpec = dagSpec,
      moduleOptions = Map.empty,
      providedInputs = inputs,
      computedValues = Map.empty,
      moduleStatuses = Map.empty
    )
    DataSignature(
      executionId = execId,
      structuralHash = structuralHash,
      resumptionCount = 0,
      status = PipelineStatus.Suspended,
      inputs = inputs,
      computedNodes = Map.empty,
      outputs = Map.empty,
      missingInputs = missingInputs,
      pendingOutputs = pendingOutputs,
      suspendedState = Some(suspendedState)
    )
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
    new ConstellationRoutes(
      constellation,
      compiler,
      functionRegistry,
      Some(scheduler),
      Some(lifecycle)
    )
}
