package io.constellation.http

import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.duration.*

import cats.data.EitherT
import cats.effect.{IO, Ref}
import cats.implicits.*

import io.constellation.errors.{ApiError, ErrorHandling}
import io.constellation.execution.{ConstellationLifecycle, GlobalScheduler, QueueFullException}
import io.constellation.http.ApiModels.*
import io.constellation.lang.semantic.FunctionRegistry
import io.constellation.lang.{CachingLangCompiler, LangCompiler}
import io.constellation.{
  CValue,
  Constellation,
  DagSpec,
  DataSignature,
  InputAlreadyProvidedError,
  InputTypeMismatchError,
  JsonCValueConverter,
  NodeAlreadyResolvedError,
  NodeTypeMismatchError,
  PipelineChangedError,
  PipelineImage,
  PipelineStatus,
  ResumeInProgressError,
  SuspensionFilter,
  SuspensionHandle,
  SuspensionSummary,
  UnknownNodeError
}

import io.circe.Json
import io.circe.syntax.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.headers.{`Content-Length`, `Content-Type`}
import org.http4s.{
  DecodeFailure,
  DecodeResult,
  EntityDecoder,
  HttpRoutes,
  MalformedMessageBodyFailure,
  MediaType,
  Response,
  Status
}
import org.typelevel.ci.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/** HTTP routes for the Constellation Engine API */
class ConstellationRoutes(
    constellation: Constellation,
    compiler: LangCompiler,
    functionRegistry: FunctionRegistry,
    scheduler: Option[GlobalScheduler] = None,
    lifecycle: Option[ConstellationLifecycle] = None,
    versionStore: Option[PipelineVersionStore] = None,
    filePathMap: Option[Ref[IO, Map[String, Path]]] = None,
    canaryRouter: Option[CanaryRouter] = None
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
                  // Record version if versioning is enabled and a name was provided
                  _ <- (versionStore, effectiveName).tupled.traverse_ { case (vs, n) =>
                    vs.recordVersion(n, image.structuralHash, Some(compileReq.source))
                  }
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

    // Execute a pipeline by reference (name, structural hash, or legacy dagName)
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
                              missingInputs = if missing.nonEmpty then Some(missing) else None,
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
                      missingInputs = if missing.nonEmpty then Some(missing) else None,
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
    // Pipeline versioning endpoints (Phase 4a)
    // ---------------------------------------------------------------------------

    // Hot-reload a named pipeline
    case req @ POST -> Root / "pipelines" / name / "reload" =>
      versionStore match {
        case None =>
          BadRequest(
            ErrorResponse(error = "VersioningNotEnabled", message = "Versioning not enabled")
          )
        case Some(vs) =>
          val reqId = requestId(req)
          handleReload(name, req, vs, reqId)
      }

    // List version history for a named pipeline
    case GET -> Root / "pipelines" / name / "versions" =>
      versionStore match {
        case None =>
          BadRequest(
            ErrorResponse(error = "VersioningNotEnabled", message = "Versioning not enabled")
          )
        case Some(vs) =>
          for {
            versions  <- vs.listVersions(name)
            activeOpt <- vs.activeVersion(name)
            response <- versions match {
              case Nil =>
                NotFound(
                  ErrorResponse(error = "NotFound", message = s"Pipeline '$name' not found")
                )
              case _ =>
                val active = activeOpt.getOrElse(0)
                val infos = versions.map { pv =>
                  PipelineVersionInfo(
                    version = pv.version,
                    structuralHash = pv.structuralHash,
                    createdAt = pv.createdAt.toString,
                    active = pv.version == active
                  )
                }
                Ok(PipelineVersionsResponse(name = name, versions = infos, activeVersion = active))
            }
          } yield response
      }

    // Rollback to previous version
    case POST -> Root / "pipelines" / name / "rollback" =>
      versionStore match {
        case None =>
          BadRequest(
            ErrorResponse(error = "VersioningNotEnabled", message = "Versioning not enabled")
          )
        case Some(vs) =>
          handleRollback(name, None, vs)
      }

    // Rollback to specific version
    case POST -> Root / "pipelines" / name / "rollback" / vStr =>
      versionStore match {
        case None =>
          BadRequest(
            ErrorResponse(error = "VersioningNotEnabled", message = "Versioning not enabled")
          )
        case Some(vs) =>
          vStr.toIntOption match {
            case None =>
              BadRequest(
                ErrorResponse(
                  error = "InvalidVersion",
                  message = s"Version must be an integer, got '$vStr'"
                )
              )
            case Some(v) =>
              handleRollback(name, Some(v), vs)
          }
      }

    // ---------------------------------------------------------------------------
    // Canary release endpoints (Phase 4b)
    // ---------------------------------------------------------------------------

    // Get canary deployment status
    case GET -> Root / "pipelines" / name / "canary" =>
      canaryRouter match {
        case None =>
          BadRequest(
            ErrorResponse(error = "CanaryNotEnabled", message = "Canary routing not enabled")
          )
        case Some(cr) =>
          cr.getState(name).flatMap {
            case None =>
              NotFound(
                ErrorResponse(
                  error = "NotFound",
                  message = s"No canary deployment active for pipeline '$name'"
                )
              )
            case Some(state) =>
              Ok(toCanaryStateResponse(state))
          }
      }

    // Manually promote canary to next step
    case POST -> Root / "pipelines" / name / "canary" / "promote" =>
      canaryRouter match {
        case None =>
          BadRequest(
            ErrorResponse(error = "CanaryNotEnabled", message = "Canary routing not enabled")
          )
        case Some(cr) =>
          cr.promote(name).flatMap {
            case None =>
              NotFound(
                ErrorResponse(
                  error = "NotFound",
                  message = s"No canary deployment active for pipeline '$name'"
                )
              )
            case Some(state) =>
              // If promotion completed the canary, update the alias to the new version
              (if state.status == CanaryStatus.Complete then
                 constellation.PipelineStore.alias(name, state.newVersion.structuralHash)
               else IO.unit) *>
                Ok(toCanaryStateResponse(state))
          }
      }

    // Manually rollback canary
    case POST -> Root / "pipelines" / name / "canary" / "rollback" =>
      canaryRouter match {
        case None =>
          BadRequest(
            ErrorResponse(error = "CanaryNotEnabled", message = "Canary routing not enabled")
          )
        case Some(cr) =>
          cr.rollback(name).flatMap {
            case None =>
              NotFound(
                ErrorResponse(
                  error = "NotFound",
                  message = s"No canary deployment active for pipeline '$name'"
                )
              )
            case Some(state) =>
              Ok(toCanaryStateResponse(state))
          }
      }

    // Abort canary deployment
    case DELETE -> Root / "pipelines" / name / "canary" =>
      canaryRouter match {
        case None =>
          BadRequest(
            ErrorResponse(error = "CanaryNotEnabled", message = "Canary routing not enabled")
          )
        case Some(cr) =>
          cr.abort(name).flatMap {
            case None =>
              NotFound(
                ErrorResponse(
                  error = "NotFound",
                  message = s"No canary deployment active for pipeline '$name'"
                )
              )
            case Some(state) =>
              Ok(toCanaryStateResponse(state))
          }
      }

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
                missingInputs = s.missingInputs.map { case (name, ctype) =>
                  name -> ctype.toString
                },
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
                    val jsonInputs   = resumeReq.additionalInputs.getOrElse(Map.empty)
                    val jsonResolved = resumeReq.resolvedNodes.getOrElse(Map.empty)
                    (for {
                      cvalueInputs   <- convertAdditionalInputs(jsonInputs, dagSpec)
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
                            missingInputs = if missing.nonEmpty then Some(missing) else None,
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
                            message =
                              s"A resume operation is already in progress for execution '$id'",
                            requestId = Some(reqId)
                          )
                        )
                      case Left(e: InputTypeMismatchError) =>
                        BadRequest(
                          ExecuteResponse(
                            success = false,
                            error = Some(s"Input error: ${e.getMessage}")
                          )
                        )
                      case Left(e: InputAlreadyProvidedError) =>
                        BadRequest(
                          ExecuteResponse(
                            success = false,
                            error = Some(s"Input error: ${e.getMessage}")
                          )
                        )
                      case Left(e: UnknownNodeError) =>
                        BadRequest(
                          ExecuteResponse(
                            success = false,
                            error = Some(s"Input error: ${e.getMessage}")
                          )
                        )
                      case Left(e: NodeTypeMismatchError) =>
                        BadRequest(
                          ExecuteResponse(
                            success = false,
                            error = Some(s"Input error: ${e.getMessage}")
                          )
                        )
                      case Left(e: NodeAlreadyResolvedError) =>
                        BadRequest(
                          ExecuteResponse(
                            success = false,
                            error = Some(s"Input error: ${e.getMessage}")
                          )
                        )
                      case Left(e: PipelineChangedError) =>
                        BadRequest(
                          ExecuteResponse(
                            success = false,
                            error = Some(s"Pipeline error: ${e.getMessage}")
                          )
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
                        logger
                          .error(error)(s"[$reqId] Unexpected error in /executions/$id/resume") *>
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
    // Supports content negotiation: Accept: text/plain → Prometheus format, else JSON
    case req @ GET -> Root / "metrics" =>
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

        metricsJson = Json.obj(
          "timestamp" -> Json.fromString(Instant.now().toString),
          "cache"     -> cacheStats.getOrElse(Json.Null),
          "scheduler" -> schedulerStats.getOrElse(Json.Null),
          "server" -> Json.obj(
            "uptime_seconds" -> Json.fromLong(uptimeSeconds),
            "requests_total" -> Json.fromLong(requestCount)
          )
        )

        // Content negotiation: text/plain → Prometheus format, else JSON
        wantsPrometheus = req.headers
          .get[org.http4s.headers.Accept]
          .exists(_.values.toList.exists(_.mediaRange.satisfiedBy(MediaType.text.plain)))

        response <-
          if wantsPrometheus then
            Ok(PrometheusFormatter.format(metricsJson))
              .map(_.withContentType(`Content-Type`(MediaType.text.plain)))
          else Ok(metricsJson)
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
    *
    * When a canary deployment is active for the pipeline name, traffic is routed to either the old
    * or new version based on the configured weight. Execution latency and success/failure are
    * recorded in the canary metrics.
    */
  private def executeByRef(
      ref: String,
      jsonInputs: Map[String, Json]
  ): EitherT[IO, ApiError, (DataSignature, DagSpec)] =
    EitherT(
      // Check if canary routing applies (only for name refs, not hash refs)
      canaryRouter
        .filter(_ => !ref.startsWith("sha256:") && ref.length != 64)
        .traverse(_.selectVersion(ref))
        .map(_.flatten)
        .flatMap {
          case Some(selectedHash) =>
            // Canary active — execute the selected version by hash and record metrics
            resolveImage(s"sha256:$selectedHash").flatMap {
              case Some(image) =>
                val startTime = System.nanoTime()
                executeImage(image, jsonInputs).flatMap { result =>
                  val latencyMs = (System.nanoTime() - startTime) / 1e6
                  val success = result.isRight && result.toOption.exists { case (sig, _) =>
                    sig.status match {
                      case _: PipelineStatus.Failed => false
                      case _                        => true
                    }
                  }
                  canaryRouter
                    .traverse(_.recordResult(ref, selectedHash, success, latencyMs))
                    .flatMap { stateOpt =>
                      // If auto-promotion completed, update the alias to the new version
                      stateOpt.flatten match {
                        case Some(state) if state.status == CanaryStatus.Complete =>
                          constellation.PipelineStore
                            .alias(ref, state.newVersion.structuralHash)
                            .as(result)
                        case _ => IO.pure(result)
                      }
                    }
                }
              case None =>
                IO.pure(Left(ApiError.NotFoundError("Pipeline", ref)))
            }
          case None =>
            // No canary — normal resolution
            resolveImage(ref).flatMap {
              case Some(image) => executeImage(image, jsonInputs)
              case None        => IO.pure(Left(ApiError.NotFoundError("Pipeline", ref)))
            }
        }
    )

  /** Execute a resolved pipeline image with the given JSON inputs. */
  private def executeImage(
      image: PipelineImage,
      jsonInputs: Map[String, Json]
  ): IO[Either[ApiError, (DataSignature, DagSpec)]] = {
    val dagSpec = image.dagSpec
    convertInputsLenient(jsonInputs, dagSpec).value.flatMap {
      case Left(err) => IO.pure(Left(err))
      case Right(inputs) =>
        val allInputNames = dagSpec.userInputDataNodes.values.map(_.name).toSet
        val missingNames  = allInputNames -- inputs.keySet
        if missingNames.nonEmpty then {
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
  }

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
      image   = compiled.pipeline.image
      dagSpec = image.dagSpec
      // Store image in PipelineStore for dedup and future reference
      _      <- EitherT.liftF(constellation.PipelineStore.store(image))
      inputs <- convertInputsLenient(req.inputs, dagSpec)
      sig <- {
        val allInputNames = dagSpec.userInputDataNodes.values.map(_.name).toSet
        val missingNames  = allInputNames -- inputs.keySet
        if missingNames.nonEmpty then {
          // Short-circuit: build a Suspended DataSignature without runtime execution
          EitherT.rightT[IO, ApiError](
            buildSuspendedSignature(dagSpec, image.structuralHash, inputs)
          )
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
    case PipelineStatus.Completed => "completed"
    case PipelineStatus.Suspended => "suspended"
    case PipelineStatus.Failed(_) => "failed"
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

    jsonInputs.toList
      .traverse { case (name, json) =>
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
      }
      .map(_.toMap)
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

    jsonNodes.toList
      .traverse { case (name, json) =>
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
      }
      .map(_.toMap)
  }

  /** Handle a pipeline reload request.
    *
    * Resolves the source (from request body or file path map), compiles, and records a new version
    * if the structural hash changed.
    */
  private def handleReload(
      name: String,
      req: org.http4s.Request[IO],
      vs: PipelineVersionStore,
      reqId: String
  ): IO[org.http4s.Response[IO]] = {
    // Custom decoder: accept empty body as empty ReloadRequest
    val parseBody: IO[ReloadRequest] =
      req.headers.get[`Content-Length`] match {
        case Some(cl) if cl.length == 0 => IO.pure(ReloadRequest(None))
        case None                       =>
          // No Content-Length header — check Content-Type to decide
          req.headers.get[`Content-Type`] match {
            case Some(ct) if ct.mediaType == MediaType.application.json =>
              req.as[ReloadRequest]
            case _ =>
              IO.pure(ReloadRequest(None))
          }
        case _ => req.as[ReloadRequest]
      }

    parseBody
      .flatMap { reloadReq =>
        // Determine source: from body, or re-read from file
        val resolveSource: IO[Either[String, String]] = reloadReq.source match {
          case Some(src) => IO.pure(Right(src))
          case None =>
            filePathMap match {
              case None =>
                IO.pure(Left("No source provided and no file path known for this pipeline"))
              case Some(ref) =>
                ref.get.flatMap { pathMap =>
                  pathMap.get(name) match {
                    case None =>
                      IO.pure(Left("No source provided and no file path known for this pipeline"))
                    case Some(path) =>
                      IO(Files.readString(path)).map(Right(_)).handleErrorWith { e =>
                        IO.pure(Left(s"Failed to read file ${path}: ${safeMessage(e)}"))
                      }
                  }
                }
            }
        }

        resolveSource.flatMap {
          case Left(errMsg) =>
            BadRequest(ErrorResponse(error = "NoSource", message = errMsg))

          case Right(source) =>
            compiler
              .compileIO(source, name)
              .timeoutTo(
                compilationTimeout,
                IO.raiseError(
                  new java.util.concurrent.TimeoutException(
                    s"Compilation timed out after $compilationTimeout"
                  )
                )
              )
              .flatMap {
                case Left(errors) =>
                  BadRequest(
                    ErrorResponse(
                      error = "CompilationError",
                      message = errors.map(_.message).mkString("; ")
                    )
                  )

                case Right(compiled) =>
                  val image   = compiled.pipeline.image
                  val newHash = image.structuralHash

                  for {
                    // Get the current alias hash
                    currentHashOpt <- constellation.PipelineStore.resolve(name)
                    changed = !currentHashOpt.contains(newHash)
                    result <-
                      if !changed then {
                        // Hash unchanged — no new version needed
                        vs.activeVersion(name).flatMap { activeOpt =>
                          Ok(
                            ReloadResponse(
                              success = true,
                              previousHash = currentHashOpt,
                              newHash = newHash,
                              name = name,
                              changed = false,
                              version = activeOpt.getOrElse(1)
                            )
                          )
                        }
                      } else {
                        for {
                          // Store new image
                          _ <- constellation.PipelineStore.store(image)
                          // Update alias (unless canary — alias stays on old version during canary)
                          _ <-
                            if reloadReq.canary.isEmpty then
                              constellation.PipelineStore.alias(name, newHash)
                            else IO.unit
                          // Record new version
                          pv <- vs.recordVersion(name, newHash, Some(source))
                          // Start canary deployment if requested
                          canaryStateOpt <- (canaryRouter, reloadReq.canary) match {
                            case (Some(cr), Some(ccReq)) =>
                              val canaryConfig = toCanaryConfig(ccReq)
                              // Look up old version from the version store
                              vs.getVersion(name, pv.version - 1).flatMap {
                                case Some(oldPv) =>
                                  cr.startCanary(name, oldPv, pv, canaryConfig).map(_.map(Some(_)))
                                case None =>
                                  // No previous version — cannot canary
                                  IO.pure(Left("No previous version exists for canary deployment"))
                              }
                            case _ => IO.pure(Right(None))
                          }
                          resp <- canaryStateOpt match {
                            case Left(errMsg) if reloadReq.canary.isDefined =>
                              Conflict(
                                ErrorResponse(
                                  error = "CanaryConflict",
                                  message = errMsg
                                )
                              )
                            case _ =>
                              val canaryResp = canaryStateOpt.toOption.flatten
                                .map(toCanaryStateResponse)
                              Ok(
                                ReloadResponse(
                                  success = true,
                                  previousHash = currentHashOpt,
                                  newHash = newHash,
                                  name = name,
                                  changed = true,
                                  version = pv.version,
                                  canary = canaryResp
                                )
                              )
                          }
                        } yield resp
                      }
                  } yield result
              }
        }
      }
      .handleErrorWith { error =>
        logger.error(error)(s"[$reqId] Unexpected error in /pipelines/$name/reload") *>
          InternalServerError(
            ErrorResponse(
              error = "InternalError",
              message = s"Unexpected error: ${safeMessage(error)}",
              requestId = Some(reqId)
            )
          )
      }
  }

  /** Handle a pipeline rollback request (to previous version or specific version). */
  private def handleRollback(
      name: String,
      targetVersion: Option[Int],
      vs: PipelineVersionStore
  ): IO[org.http4s.Response[IO]] =
    for {
      activeOpt <- vs.activeVersion(name)
      response <- activeOpt match {
        case None =>
          NotFound(ErrorResponse(error = "NotFound", message = s"Pipeline '$name' not found"))
        case Some(currentActive) =>
          val targetIO: IO[Option[PipelineVersion]] = targetVersion match {
            case Some(v) => vs.getVersion(name, v)
            case None    => vs.previousVersion(name)
          }
          targetIO.flatMap {
            case None =>
              val detail = targetVersion match {
                case Some(v) => s"Version $v not found for pipeline '$name'"
                case None    => s"No previous version exists for pipeline '$name'"
              }
              NotFound(ErrorResponse(error = "NotFound", message = detail))

            case Some(pv) =>
              // Verify the structural hash still exists in PipelineStore
              constellation.PipelineStore.get(pv.structuralHash).flatMap {
                case None =>
                  NotFound(
                    ErrorResponse(
                      error = "NotFound",
                      message =
                        s"Pipeline image for version ${pv.version} (hash=${pv.structuralHash.take(12)}...) no longer exists"
                    )
                  )
                case Some(_) =>
                  for {
                    // Set the target as active version
                    _ <- vs.setActiveVersion(name, pv.version)
                    // Update alias to point to the target hash
                    _ <- constellation.PipelineStore.alias(name, pv.structuralHash)
                    resp <- Ok(
                      RollbackResponse(
                        success = true,
                        name = name,
                        previousVersion = currentActive,
                        activeVersion = pv.version,
                        structuralHash = pv.structuralHash
                      )
                    )
                  } yield resp
              }
          }
      }
    } yield response

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

  /** Convert a CanaryConfigRequest to a CanaryConfig, applying defaults for omitted fields. */
  private def toCanaryConfig(req: CanaryConfigRequest): CanaryConfig = {
    val defaults = CanaryConfig()
    CanaryConfig(
      initialWeight = req.initialWeight.getOrElse(defaults.initialWeight),
      promotionSteps = req.promotionSteps.getOrElse(defaults.promotionSteps),
      observationWindow =
        req.observationWindow.flatMap(parseDuration).getOrElse(defaults.observationWindow),
      errorThreshold = req.errorThreshold.getOrElse(defaults.errorThreshold),
      latencyThresholdMs = req.latencyThresholdMs.orElse(defaults.latencyThresholdMs),
      minRequests = req.minRequests.getOrElse(defaults.minRequests),
      autoPromote = req.autoPromote.getOrElse(defaults.autoPromote)
    )
  }

  /** Parse a duration string like "5m", "30s", "1h" to a FiniteDuration. */
  private def parseDuration(s: String): Option[FiniteDuration] = {
    val pattern = """(\d+)(s|ms|m|min|h)""".r
    s.toLowerCase match {
      case pattern(num, "s")   => num.toIntOption.map(_.seconds)
      case pattern(num, "ms")  => num.toIntOption.map(_.milliseconds)
      case pattern(num, "m")   => num.toIntOption.map(_.minutes)
      case pattern(num, "min") => num.toIntOption.map(_.minutes)
      case pattern(num, "h")   => num.toIntOption.map(_.hours)
      case _                   => s.toIntOption.map(_.seconds)
    }
  }

  /** Convert a CanaryState to its API response model. */
  private def toCanaryStateResponse(state: CanaryState): CanaryStateResponse = {
    def toMetricsResponse(vm: VersionMetrics): VersionMetricsResponse =
      VersionMetricsResponse(
        requests = vm.requests,
        successes = vm.successes,
        failures = vm.failures,
        avgLatencyMs = vm.avgLatencyMs,
        p99LatencyMs = vm.p99LatencyMs
      )

    CanaryStateResponse(
      pipelineName = state.pipelineName,
      oldVersion = CanaryVersionInfo(
        version = state.oldVersion.version,
        structuralHash = state.oldVersion.structuralHash
      ),
      newVersion = CanaryVersionInfo(
        version = state.newVersion.version,
        structuralHash = state.newVersion.structuralHash
      ),
      currentWeight = state.currentWeight,
      currentStep = state.currentStep,
      status = state.status match {
        case CanaryStatus.Observing  => "observing"
        case CanaryStatus.Promoting  => "promoting"
        case CanaryStatus.RolledBack => "rolled_back"
        case CanaryStatus.Complete   => "complete"
      },
      startedAt = state.startedAt.toString,
      metrics = CanaryMetricsResponse(
        oldVersion = toMetricsResponse(state.metrics.oldVersion),
        newVersion = toMetricsResponse(state.metrics.newVersion)
      )
    )
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
    val allInputNames = dagSpec.userInputDataNodes.values.map(_.name).toSet
    val missingInputs = (allInputNames -- inputs.keySet).toList.sorted
    val pendingOutputs =
      dagSpec.declaredOutputs // All outputs are pending when suspended pre-execution
    val execId = UUID.randomUUID()
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
