package io.constellation.lsp

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import io.circe.*
import io.circe.syntax.*
import io.constellation.{CValue, Constellation, ExecutionTracker, ExecutionTrace, NodeExecutionResult, NodeStatus, Module, SteppedExecution}
import io.constellation.lang.{ast, CachingLangCompiler, CacheStats, LangCompiler}
import io.constellation.lang.viz.{DagVizCompiler, SugiyamaLayout, LayoutConfig}
import io.constellation.lang.ast.{Annotation, CompileError, CompileWarning, Declaration, Expression, SourceFile, Span, TypeExpr}
import io.constellation.lang.compiler.{ErrorCode, ErrorCodes => CompilerErrorCodes, ErrorFormatter, SuggestionContext, Suggestions}
import io.constellation.lang.semantic.FunctionRegistry
import io.constellation.lang.parser.ConstellationParser
import io.constellation.lsp.protocol.JsonRpc.*
import io.constellation.lsp.protocol.LspTypes.*
import io.constellation.lsp.protocol.LspMessages.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration._

/** Language server for constellation-lang with LSP support */
class ConstellationLanguageServer(
    constellation: Constellation,
    compiler: LangCompiler,
    documentManager: DocumentManager,
    debugSessionManager: DebugSessionManager,
    debouncer: Debouncer[String],
    publishDiagnostics: PublishDiagnosticsParams => IO[Unit],
    executionTracker: Option[ExecutionTracker[IO]] = None
) {
  private val logger: Logger[IO] =
    Slf4jLogger.getLoggerFromClass[IO](classOf[ConstellationLanguageServer])

  // Cached completion tries for efficient prefix-based lookups
  private var moduleCompletionTrie: CompletionTrie = CompletionTrie.empty
  private var lastModuleNames: Set[String] = Set.empty
  private val keywordCompletionTrie: CompletionTrie = buildKeywordTrie()

  // Semantic token provider for syntax highlighting
  private val semanticTokenProvider: SemanticTokenProvider = SemanticTokenProvider()

  /** Handle LSP request */
  def handleRequest(request: Request): IO[Response] = {
    val startTime = System.currentTimeMillis()
    handleRequestInternal(request).flatTap { _ =>
      val elapsed = System.currentTimeMillis() - startTime
      if (elapsed > 50) { // Only log slow requests (>50ms)
        logger.info(s"[TIMING] Request ${request.method} took ${elapsed}ms")
      } else {
        IO.unit
      }
    }
  }

  private def handleRequestInternal(request: Request): IO[Response] =
    request.method match {
      case "initialize" =>
        handleInitialize(request)

      case "shutdown" =>
        // LSP shutdown request - just return success
        IO.pure(Response(id = request.id, result = Some(Json.Null)))

      case "textDocument/completion" =>
        handleCompletion(request)

      case "textDocument/hover" =>
        handleHover(request)

      case "constellation/executePipeline" =>
        handleExecutePipeline(request)

      case "constellation/getInputSchema" =>
        handleGetInputSchema(request)

      case "constellation/getDagStructure" =>
        handleGetDagStructure(request)

      case "constellation/getDagVisualization" =>
        handleGetDagVisualization(request)

      case "constellation/stepStart" =>
        handleStepStart(request)

      case "constellation/stepNext" =>
        handleStepNext(request)

      case "constellation/stepContinue" =>
        handleStepContinue(request)

      case "constellation/stepStop" =>
        handleStepStop(request)

      case "constellation/getCacheStats" =>
        handleGetCacheStats(request)

      case "textDocument/semanticTokens/full" =>
        handleSemanticTokensFull(request)

      case method =>
        IO.pure(
          Response(
            id = request.id,
            error = Some(
              ResponseError(
                code = ErrorCodes.MethodNotFound,
                message = s"Method not found: $method"
              )
            )
          )
        )
    }

  /** Handle LSP notification (no response) */
  def handleNotification(notification: Notification): IO[Unit] = {
    val startTime = System.currentTimeMillis()
    handleNotificationInternal(notification).flatTap { _ =>
      val elapsed = System.currentTimeMillis() - startTime
      if (elapsed > 10) { // Only log notifications taking >10ms
        logger.info(s"[TIMING] Notification ${notification.method} took ${elapsed}ms")
      } else {
        IO.unit
      }
    }
  }

  private def handleNotificationInternal(notification: Notification): IO[Unit] =
    notification.method match {
      case "initialized" =>
        IO.unit // Client finished initialization

      case "exit" =>
        IO.unit // Client is exiting, cleanup if needed

      case "textDocument/didOpen" =>
        handleDidOpen(notification)

      case "textDocument/didChange" =>
        handleDidChange(notification)

      case "textDocument/didSave" =>
        handleDidSave(notification)

      case "textDocument/didClose" =>
        handleDidClose(notification)

      case _ =>
        IO.unit // Ignore unknown notifications
    }

  // ========== Request Handlers ==========

  private def handleInitialize(request: Request): IO[Response] = {
    val result = InitializeResult(
      capabilities = ServerCapabilities(
        textDocumentSync = Some(1), // Full text sync
        completionProvider = Some(
          CompletionOptions(
            triggerCharacters = Some(List("(", ",", " ", ".", ":"))
          )
        ),
        hoverProvider = Some(true),
        executeCommandProvider = None, // Command handled client-side
        semanticTokensProvider = Some(
          SemanticTokensOptions(
            legend = SemanticTokensLegend(
              tokenTypes = SemanticTokenTypes.tokenTypes,
              tokenModifiers = SemanticTokenTypes.tokenModifiers
            ),
            full = Some(true),
            range = None
          )
        )
      )
    )

    IO.pure(
      Response(
        id = request.id,
        result = Some(result.asJson)
      )
    )
  }

  private def handleCompletion(request: Request): IO[Response] =
    request.params match {
      case None =>
        IO.pure(
          Response(
            id = request.id,
            error = Some(ResponseError(ErrorCodes.InvalidParams, "Missing params"))
          )
        )
      case Some(json) =>
        json.as[CompletionParams] match {
          case Left(decodeError) =>
            IO.pure(
              Response(
                id = request.id,
                error = Some(
                  ResponseError(ErrorCodes.InvalidParams, s"Invalid params: ${decodeError.message}")
                )
              )
            )
          case Right(params) =>
            documentManager.getDocument(params.textDocument.uri).flatMap {
              case Some(document) =>
                getCompletions(document, params.position).map { completions =>
                  Response(id = request.id, result = Some(completions.asJson))
                }
              case None =>
                IO.pure(
                  Response(
                    id = request.id,
                    result = Some(CompletionList(isIncomplete = false, items = List.empty).asJson)
                  )
                )
            }
        }
    }

  private def handleSemanticTokensFull(request: Request): IO[Response] =
    request.params match {
      case None =>
        IO.pure(
          Response(
            id = request.id,
            error = Some(ResponseError(ErrorCodes.InvalidParams, "Missing params"))
          )
        )
      case Some(json) =>
        json.as[SemanticTokensParams] match {
          case Left(decodeError) =>
            IO.pure(
              Response(
                id = request.id,
                error = Some(
                  ResponseError(ErrorCodes.InvalidParams, s"Invalid params: ${decodeError.message}")
                )
              )
            )
          case Right(params) =>
            documentManager.getDocument(params.textDocument.uri).map {
              case Some(document) =>
                // Skip semantic tokens for large files to prevent OOM in VS Code extension
                // Large files generate thousands of tokens which can overwhelm the client
                val MaxLinesForSemanticTokens = 150
                val lineCount = document.text.count(_ == '\n') + 1
                if (lineCount > MaxLinesForSemanticTokens) {
                  // Return empty tokens for large files
                  Response(id = request.id, result = Some(SemanticTokens(data = List.empty).asJson))
                } else {
                  val tokens = semanticTokenProvider.computeTokens(document.text)
                  Response(id = request.id, result = Some(SemanticTokens(data = tokens).asJson))
                }
              case None =>
                // Return empty tokens for unknown documents
                Response(id = request.id, result = Some(SemanticTokens(data = List.empty).asJson))
            }
        }
    }

  private def handleHover(request: Request): IO[Response] =
    request.params match {
      case None =>
        for {
          _ <- logger.debug("Missing params in hover request")
        } yield Response(
          id = request.id,
          error = Some(ResponseError(ErrorCodes.InvalidParams, "Missing params"))
        )
      case Some(json) =>
        json.as[HoverParams] match {
          case Left(decodeError) =>
            for {
              _ <- logger.debug(s"Hover params decode error: ${decodeError.message}")
            } yield Response(
              id = request.id,
              error = Some(
                ResponseError(ErrorCodes.InvalidParams, s"Invalid params: ${decodeError.message}")
              )
            )
          case Right(params) =>
            for {
              _ <- logger.debug(
                s"Hover request for uri: ${params.textDocument.uri}, position: ${params.position}"
              )
              maybeDoc <- documentManager.getDocument(params.textDocument.uri)
              response <- maybeDoc match {
                case Some(document) =>
                  for {
                    hover <- getHover(document, params.position)
                    response = Response(id = request.id, result = hover.map(_.asJson))
                    _ <- logger.debug(s"Hover response result: ${hover.isDefined}")
                  } yield response
                case None =>
                  for {
                    _ <- logger.debug(s"Hover document not found: ${params.textDocument.uri}")
                  } yield Response(id = request.id, result = None)
              }
            } yield response
        }
    }

  private def handleExecutePipeline(request: Request): IO[Response] =
    request.params match {
      case None =>
        IO.pure(
          Response(
            id = request.id,
            error = Some(ResponseError(ErrorCodes.InvalidParams, "Missing params"))
          )
        )
      case Some(json) =>
        json.as[ExecutePipelineParams] match {
          case Left(decodeError) =>
            IO.pure(
              Response(
                id = request.id,
                error = Some(
                  ResponseError(ErrorCodes.InvalidParams, s"Invalid params: ${decodeError.message}")
                )
              )
            )
          case Right(params) =>
            documentManager.getDocument(params.uri).flatMap {
              case Some(document) =>
                executePipeline(document, params.inputs).map { execResult =>
                  Response(id = request.id, result = Some(execResult.asJson))
                }
              case None =>
                IO.pure(
                  Response(
                    id = request.id,
                    result = Some(
                      ExecutePipelineResult(
                        success = false,
                        outputs = None,
                        error = Some("Document not found")
                      ).asJson
                    )
                  )
                )
            }
        }
    }

  private def handleGetInputSchema(request: Request): IO[Response] =
    request.params match {
      case None =>
        IO.pure(
          Response(
            id = request.id,
            error = Some(ResponseError(ErrorCodes.InvalidParams, "Missing params"))
          )
        )
      case Some(json) =>
        json.as[GetInputSchemaParams] match {
          case Left(decodeError) =>
            IO.pure(
              Response(
                id = request.id,
                error = Some(
                  ResponseError(ErrorCodes.InvalidParams, s"Invalid params: ${decodeError.message}")
                )
              )
            )
          case Right(params) =>
            documentManager.getDocument(params.uri).flatMap {
              case Some(document) =>
                IO.pure(getInputSchema(document)).map { schemaResult =>
                  Response(id = request.id, result = Some(schemaResult.asJson))
                }
              case None =>
                IO.pure(
                  Response(
                    id = request.id,
                    result = Some(
                      GetInputSchemaResult(
                        success = false,
                        inputs = None,
                        error = Some("Document not found")
                      ).asJson
                    )
                  )
                )
            }
        }
    }

  private def getInputSchema(document: DocumentState): GetInputSchemaResult =
    ConstellationParser.parse(document.text) match {
      case Right(program) =>
        val sourceFile = document.sourceFile
        val inputFields = program.declarations.collect {
          case Declaration.InputDecl(name, typeExpr, annotations) =>
            val (startLC, _) = sourceFile.spanToLineCol(name.span)

            // Extract example from annotations
            val example = annotations.collectFirst { case Annotation.Example(exprLoc) =>
              evaluateExampleToJson(exprLoc.value)
            }.flatten

            InputField(
              name = name.value,
              `type` = typeExprToDescriptor(typeExpr.value),
              line = startLC.line,
              example = example
            )
        }
        GetInputSchemaResult(
          success = true,
          inputs = Some(inputFields),
          error = None,
          errors = None
        )
      case Left(error) =>
        val sourceFile = document.sourceFile
        val errorInfo  = compileErrorToErrorInfo(error, sourceFile)
        GetInputSchemaResult(
          success = false,
          inputs = None,
          error = Some(error.message),
          errors = Some(List(errorInfo))
        )
    }

  /** Convert an example expression to JSON for LSP transport.
    * Supports literal values only (strings, numbers, booleans, lists of literals).
    * Complex expressions (function calls, references) return None.
    */
  private def evaluateExampleToJson(expr: Expression): Option[Json] =
    try {
      expr match {
        case Expression.StringLit(value) => Some(Json.fromString(value))
        case Expression.IntLit(value)    => Some(Json.fromLong(value))
        case Expression.FloatLit(value)  => Some(Json.fromDoubleOrNull(value))
        case Expression.BoolLit(value)   => Some(Json.fromBoolean(value))
        case Expression.ListLit(elements) =>
          // Recursively convert each element; if any fails, the whole list fails
          val convertedElements = elements.map(loc => evaluateExampleToJson(loc.value))
          if convertedElements.forall(_.isDefined) then
            Some(Json.arr(convertedElements.flatten*))
          else
            None
        case _ => None // Complex expressions not supported
      }
    } catch {
      case _: Exception => None // Fail gracefully
    }

  private def handleGetDagStructure(request: Request): IO[Response] =
    request.params match {
      case None =>
        IO.pure(
          Response(
            id = request.id,
            error = Some(ResponseError(ErrorCodes.InvalidParams, "Missing params"))
          )
        )
      case Some(json) =>
        json.as[GetDagStructureParams] match {
          case Left(decodeError) =>
            IO.pure(
              Response(
                id = request.id,
                error = Some(
                  ResponseError(ErrorCodes.InvalidParams, s"Invalid params: ${decodeError.message}")
                )
              )
            )
          case Right(params) =>
            documentManager.getDocument(params.uri).flatMap {
              case Some(document) =>
                IO.pure(getDagStructure(document)).map { dagResult =>
                  Response(id = request.id, result = Some(dagResult.asJson))
                }
              case None =>
                IO.pure(
                  Response(
                    id = request.id,
                    result = Some(
                      GetDagStructureResult(
                        success = false,
                        dag = None,
                        error = Some("Document not found")
                      ).asJson
                    )
                  )
                )
            }
        }
    }

  private def getDagStructure(document: DocumentState): GetDagStructureResult = {
    val dagName = s"lsp-dag-${document.uri.hashCode.abs}"

    compiler.compile(document.text, dagName) match {
      case Right(compiled) =>
        val dagSpec = compiled.program.image.dagSpec

        val modules = dagSpec.modules.map { case (uuid, spec) =>
          uuid.toString -> ModuleNode(
            name = spec.name,
            consumes = spec.consumes.map { case (k, v) => k -> cTypeToString(v) },
            produces = spec.produces.map { case (k, v) => k -> cTypeToString(v) }
          )
        }

        val data = dagSpec.data.map { case (uuid, spec) =>
          uuid.toString -> DataNode(
            name = spec.name,
            cType = cTypeToString(spec.cType)
          )
        }

        val inEdges = dagSpec.inEdges.toList.map { case (from, to) =>
          (from.toString, to.toString)
        }

        val outEdges = dagSpec.outEdges.toList.map { case (from, to) =>
          (from.toString, to.toString)
        }

        GetDagStructureResult(
          success = true,
          dag = Some(
            DagStructure(
              modules = modules,
              data = data,
              inEdges = inEdges,
              outEdges = outEdges,
              declaredOutputs = dagSpec.declaredOutputs
            )
          ),
          error = None
        )

      case Left(errors) =>
        GetDagStructureResult(
          success = false,
          dag = None,
          error = Some(errors.map(_.message).mkString("; "))
        )
    }
  }

  private def handleGetDagVisualization(request: Request): IO[Response] =
    request.params match {
      case None =>
        IO.pure(
          Response(
            id = request.id,
            error = Some(ResponseError(code = ErrorCodes.InvalidParams, message = "Missing params"))
          )
        )
      case Some(paramsJson) =>
        paramsJson.as[GetDagVisualizationParams] match {
          case Left(error) =>
            IO.pure(
              Response(
                id = request.id,
                error = Some(ResponseError(code = ErrorCodes.InvalidParams, message = error.getMessage))
              )
            )
          case Right(params) =>
            documentManager.getDocument(params.uri).flatMap {
              case Some(document) =>
                getDagVisualization(document, params).map { result =>
                  Response(id = request.id, result = Some(result.asJson))
                }
              case None =>
                IO.pure(
                  Response(
                    id = request.id,
                    result = Some(
                      GetDagVisualizationResult(
                        success = false,
                        dag = None,
                        error = Some("Document not found")
                      ).asJson
                    )
                  )
                )
            }
        }
    }

  private def getDagVisualization(
      document: DocumentState,
      params: GetDagVisualizationParams
  ): IO[GetDagVisualizationResult] = {
    val totalStart = System.currentTimeMillis()
    val dagName = s"lsp-dag-${document.uri.hashCode.abs}"

    val irStart = System.currentTimeMillis()
    compiler.compileToIR(document.text, dagName) match {
      case Right(irProgram) =>
        val irTime = System.currentTimeMillis() - irStart

        // Compile IR to visualization IR
        val vizStart = System.currentTimeMillis()
        val vizIR = DagVizCompiler.compile(irProgram, title = Some(dagName))
        val vizTime = System.currentTimeMillis() - vizStart

        // Apply layout
        val layoutStart = System.currentTimeMillis()
        val direction = params.direction.getOrElse("TB")
        val layoutConfig = LayoutConfig(direction = direction)
        val layoutedVizIR = SugiyamaLayout.layout(vizIR, layoutConfig)
        val layoutTime = System.currentTimeMillis() - layoutStart

        logger.info(s"[TIMING] getDagVisualization: IR=${irTime}ms, vizCompile=${vizTime}ms, layout=${layoutTime}ms").unsafeRunSync()

        // Get execution trace if executionId provided and tracker available
        val executionTraceIO: IO[Option[ExecutionTrace]] =
          (params.executionId, executionTracker) match {
            case (Some(execId), Some(tracker)) => tracker.getTrace(execId)
            case _                              => IO.pure(None)
          }

        executionTraceIO.map { traceOpt =>
          // Convert to LSP message types, enriching with execution state if available
          val nodes = layoutedVizIR.nodes.map { n =>
            // Look up execution state from trace by node ID
            val execState: Option[DagVizExecutionState] = traceOpt.flatMap { trace =>
              trace.nodeResults.get(n.id).map { result =>
                DagVizExecutionState(
                  status = result.status.toString,
                  value = result.value,
                  durationMs = result.durationMs,
                  error = result.error
                )
              }
            }.orElse {
              // Fall back to any execution state already in the node
              n.executionState.map(es =>
                DagVizExecutionState(
                  status = es.status.toString,
                  value = es.value,
                  durationMs = es.durationMs,
                  error = es.error
                )
              )
            }

            DagVizNode(
              id = n.id,
              kind = n.kind.toString,
              label = n.label,
              typeSignature = n.typeSignature,
              position = n.position.map(p => DagVizPosition(p.x, p.y)),
              executionState = execState
            )
          }

          val edges = layoutedVizIR.edges.map { e =>
            DagVizEdge(
              id = e.id,
              source = e.source,
              target = e.target,
              label = e.label,
              kind = e.kind.toString
            )
          }

          val groups = layoutedVizIR.groups.map { g =>
            DagVizGroup(
              id = g.id,
              label = g.label,
              nodeIds = g.nodeIds,
              collapsed = g.collapsed
            )
          }

          val metadata = DagVizMetadata(
            title = layoutedVizIR.metadata.title,
            layoutDirection = layoutedVizIR.metadata.layoutDirection,
            bounds = layoutedVizIR.metadata.bounds.map(b =>
              DagVizBounds(b.minX, b.minY, b.maxX, b.maxY)
            )
          )

          GetDagVisualizationResult(
            success = true,
            dag = Some(DagVisualization(nodes, edges, groups, metadata)),
            error = None
          )
        }

      case Left(errors) =>
        IO.pure(GetDagVisualizationResult(
          success = false,
          dag = None,
          error = Some(errors.map(_.message).mkString("; "))
        ))
    }
  }

  private def cTypeToString(cType: io.constellation.CType): String = cType match {
    case io.constellation.CType.CString           => "String"
    case io.constellation.CType.CInt              => "Int"
    case io.constellation.CType.CFloat            => "Float"
    case io.constellation.CType.CBoolean          => "Boolean"
    case io.constellation.CType.CList(valuesType) => s"List<${cTypeToString(valuesType)}>"
    case io.constellation.CType.CMap(keysType, valuesType) =>
      s"Map<${cTypeToString(keysType)}, ${cTypeToString(valuesType)}>"
    case io.constellation.CType.CProduct(structure) =>
      s"{ ${structure.map { case (k, v) => s"$k: ${cTypeToString(v)}" }.mkString(", ")} }"
    case io.constellation.CType.CUnion(structure) =>
      structure.keys.mkString(" | ")
    case io.constellation.CType.COptional(innerType) =>
      s"Optional<${cTypeToString(innerType)}>"
  }

  private def typeExprToDescriptor(typeExpr: TypeExpr): TypeDescriptor = typeExpr match {
    case TypeExpr.Primitive(name) =>
      TypeDescriptor.PrimitiveType(name)

    case TypeExpr.Record(fields) =>
      TypeDescriptor.RecordType(
        fields.map { case (name, fieldType) =>
          RecordField(name, typeExprToDescriptor(fieldType))
        }
      )

    case TypeExpr.Parameterized(name, params) =>
      // Special case for List<T> and Map<K,V>
      name match {
        case "List" if params.size == 1 =>
          TypeDescriptor.ListType(typeExprToDescriptor(params.head))
        case "Map" if params.size == 2 =>
          TypeDescriptor.MapType(
            typeExprToDescriptor(params.head),
            typeExprToDescriptor(params(1))
          )
        case _ =>
          TypeDescriptor.ParameterizedType(name, params.map(typeExprToDescriptor))
      }

    case TypeExpr.TypeRef(name) =>
      TypeDescriptor.RefType(name)

    case TypeExpr.TypeMerge(left, right) =>
      // For merged types, represent as a record with fields from both sides
      // This is a simplification - actual merge semantics depend on the type system
      (typeExprToDescriptor(left), typeExprToDescriptor(right)) match {
        case (TypeDescriptor.RecordType(leftFields), TypeDescriptor.RecordType(rightFields)) =>
          TypeDescriptor.RecordType(leftFields ++ rightFields)
        case (leftDesc, rightDesc) =>
          // Fallback: create a pseudo-record with _left and _right
          TypeDescriptor.RecordType(
            List(
              RecordField("_merged_left", leftDesc),
              RecordField("_merged_right", rightDesc)
            )
          )
      }

    case TypeExpr.Union(members) =>
      TypeDescriptor.UnionType(members.map(typeExprToDescriptor))
  }

  // ========== Step-through Execution Handlers ==========

  private def handleStepStart(request: Request): IO[Response] =
    request.params match {
      case None =>
        IO.pure(
          Response(
            id = request.id,
            error = Some(ResponseError(ErrorCodes.InvalidParams, "Missing params"))
          )
        )
      case Some(json) =>
        json.as[StepStartParams] match {
          case Left(decodeError) =>
            IO.pure(
              Response(
                id = request.id,
                error = Some(
                  ResponseError(ErrorCodes.InvalidParams, s"Invalid params: ${decodeError.message}")
                )
              )
            )
          case Right(params) =>
            documentManager.getDocument(params.uri).flatMap {
              case Some(document) =>
                startStepExecution(document, params.inputs).map { result =>
                  Response(id = request.id, result = Some(result.asJson))
                }
              case None =>
                IO.pure(
                  Response(
                    id = request.id,
                    result = Some(
                      StepStartResult(
                        success = false,
                        sessionId = None,
                        totalBatches = None,
                        initialState = None,
                        error = Some("Document not found")
                      ).asJson
                    )
                  )
                )
            }
        }
    }

  private def handleStepNext(request: Request): IO[Response] =
    request.params match {
      case None =>
        IO.pure(
          Response(
            id = request.id,
            error = Some(ResponseError(ErrorCodes.InvalidParams, "Missing params"))
          )
        )
      case Some(json) =>
        json.as[StepNextParams] match {
          case Left(decodeError) =>
            IO.pure(
              Response(
                id = request.id,
                error = Some(
                  ResponseError(ErrorCodes.InvalidParams, s"Invalid params: ${decodeError.message}")
                )
              )
            )
          case Right(params) =>
            stepNext(params.sessionId).map { result =>
              Response(id = request.id, result = Some(result.asJson))
            }
        }
    }

  private def handleStepContinue(request: Request): IO[Response] =
    request.params match {
      case None =>
        IO.pure(
          Response(
            id = request.id,
            error = Some(ResponseError(ErrorCodes.InvalidParams, "Missing params"))
          )
        )
      case Some(json) =>
        json.as[StepContinueParams] match {
          case Left(decodeError) =>
            IO.pure(
              Response(
                id = request.id,
                error = Some(
                  ResponseError(ErrorCodes.InvalidParams, s"Invalid params: ${decodeError.message}")
                )
              )
            )
          case Right(params) =>
            stepContinue(params.sessionId).map { result =>
              Response(id = request.id, result = Some(result.asJson))
            }
        }
    }

  private def handleStepStop(request: Request): IO[Response] =
    request.params match {
      case None =>
        IO.pure(
          Response(
            id = request.id,
            error = Some(ResponseError(ErrorCodes.InvalidParams, "Missing params"))
          )
        )
      case Some(json) =>
        json.as[StepStopParams] match {
          case Left(decodeError) =>
            IO.pure(
              Response(
                id = request.id,
                error = Some(
                  ResponseError(ErrorCodes.InvalidParams, s"Invalid params: ${decodeError.message}")
                )
              )
            )
          case Right(params) =>
            debugSessionManager.stopSession(params.sessionId).map { success =>
              Response(id = request.id, result = Some(StepStopResult(success).asJson))
            }
        }
    }

  /** Handle constellation/getCacheStats request
    *
    * Returns cache statistics if using CachingLangCompiler, otherwise returns
    * cachingEnabled: false.
    */
  private def handleGetCacheStats(request: Request): IO[Response] = {
    val result = compiler match {
      case cachingCompiler: CachingLangCompiler =>
        val stats = cachingCompiler.cacheStats
        Json.obj(
          "success" -> Json.fromBoolean(true),
          "cachingEnabled" -> Json.fromBoolean(true),
          "stats" -> Json.obj(
            "hits" -> Json.fromLong(stats.hits),
            "misses" -> Json.fromLong(stats.misses),
            "hitRate" -> Json.fromDoubleOrNull(stats.hitRate),
            "evictions" -> Json.fromLong(stats.evictions),
            "entries" -> Json.fromInt(stats.entries)
          )
        )
      case _ =>
        Json.obj(
          "success" -> Json.fromBoolean(true),
          "cachingEnabled" -> Json.fromBoolean(false),
          "stats" -> Json.Null
        )
    }
    IO.pure(Response(id = request.id, result = Some(result)))
  }

  private def startStepExecution(
      document: DocumentState,
      inputs: Map[String, Json]
  ): IO[StepStartResult] = {
    val dagName = s"lsp-step-${document.uri.hashCode.abs}"

    compiler.compile(document.text, dagName) match {
      case Right(compiled) =>
        val cvalueInputs: Map[String, CValue] = inputs.flatMap { case (name, json) =>
          jsonToCValue(json).map(name -> _)
        }

        // Build module map from compiled DAG
        // First, merge synthetic modules with registered modules looked up by name
        val moduleMapIO: IO[Map[java.util.UUID, Module.Uninitialized]] =
          compiled.program.image.dagSpec.modules.toList
            .traverse { case (uuid, spec) =>
              // First check if this is a synthetic module (keyed by uuid)
              compiled.program.syntheticModules.get(uuid) match {
                case Some(mod) => IO.pure(uuid -> mod)
                case None      =>
                  // Otherwise look up by name from constellation's registered modules
                  constellation.getModuleByName(spec.name).flatMap {
                    case Some(mod) => IO.pure(uuid -> mod)
                    case None =>
                      IO.raiseError(new RuntimeException(s"Module ${spec.name} not found"))
                  }
              }
            }
            .map(_.toMap)

        (for {
          moduleMap <- moduleMapIO
          session <- debugSessionManager.createSession(
            compiled.program.image.dagSpec,
            compiled.program.syntheticModules,
            moduleMap,
            cvalueInputs
          )
        } yield StepStartResult(
          success = true,
          sessionId = Some(session.sessionId),
          totalBatches = Some(session.batches.length),
          initialState = Some(buildStepState(session)),
          error = None
        )).handleError { error =>
          StepStartResult(
            success = false,
            sessionId = None,
            totalBatches = None,
            initialState = None,
            error = Some(error.getMessage)
          )
        }

      case Left(errors) =>
        IO.pure(
          StepStartResult(
            success = false,
            sessionId = None,
            totalBatches = None,
            initialState = None,
            error = Some(errors.map(_.message).mkString("; "))
          )
        )
    }
  }

  private def stepNext(sessionId: String): IO[StepNextResult] =
    debugSessionManager
      .stepNext(sessionId)
      .map {
        case Some((session, isComplete)) =>
          StepNextResult(
            success = true,
            state = Some(buildStepState(session)),
            isComplete = isComplete,
            error = None
          )
        case None =>
          StepNextResult(
            success = false,
            state = None,
            isComplete = false,
            error = Some("Session not found")
          )
      }
      .handleError { error =>
        StepNextResult(
          success = false,
          state = None,
          isComplete = false,
          error = Some(error.getMessage)
        )
      }

  private def stepContinue(sessionId: String): IO[StepContinueResult] =
    debugSessionManager
      .stepContinue(sessionId)
      .map {
        case Some(session) =>
          val outputs       = SteppedExecution.getOutputs(session)
          val outputsJson   = outputs.map { case (name, value) => name -> cvalueToJson(value) }
          val executionTime = System.currentTimeMillis() - session.startTime

          StepContinueResult(
            success = true,
            state = Some(buildStepState(session)),
            outputs = Some(outputsJson),
            executionTimeMs = Some(executionTime),
            error = None
          )
        case None =>
          StepContinueResult(
            success = false,
            state = None,
            outputs = None,
            executionTimeMs = None,
            error = Some("Session not found")
          )
      }
      .handleError { error =>
        StepContinueResult(
          success = false,
          state = None,
          outputs = None,
          executionTimeMs = None,
          error = Some(error.getMessage)
        )
      }

  private def buildStepState(session: SteppedExecution.SessionState): StepState = {
    val dagSpec = session.dagSpec
    val currentBatch = if session.currentBatchIndex < session.batches.length then {
      session.batches(session.currentBatchIndex)
    } else {
      session.batches.lastOption.getOrElse(
        SteppedExecution.ExecutionBatch(0, List.empty, List.empty)
      )
    }

    val completedNodes = session.nodeStates.collect {
      case (nodeId, SteppedExecution.NodeState.Completed(value, durationMs)) =>
        val (nodeName, nodeType) = dagSpec.modules.get(nodeId) match {
          case Some(spec) => (spec.name, "module")
          case None =>
            dagSpec.data.get(nodeId) match {
              case Some(spec) => (spec.name, "data")
              case None       => (nodeId.toString.take(8), "unknown")
            }
        }
        CompletedNode(
          nodeId = nodeId.toString,
          nodeName = nodeName,
          nodeType = nodeType,
          valuePreview = SteppedExecution.valuePreview(value),
          durationMs = if durationMs > 0 then Some(durationMs) else None
        )
    }.toList

    val pendingNodeIds = session.nodeStates.collect {
      case (nodeId, SteppedExecution.NodeState.Pending) => nodeId.toString
    }.toList

    StepState(
      currentBatch = session.currentBatchIndex,
      totalBatches = session.batches.length,
      batchNodes = (currentBatch.moduleIds ++ currentBatch.dataIds).map(_.toString),
      completedNodes = completedNodes,
      pendingNodes = pendingNodeIds
    )
  }

  // ========== Notification Handlers ==========

  private def handleDidOpen(notification: Notification): IO[Unit] = {
    for {
      params <- IO.fromEither(
        notification.params
          .toRight(new Exception("Missing params"))
          .flatMap(
            _.as[DidOpenTextDocumentParams].left.map(e =>
              new Exception(s"Invalid params: ${e.message}")
            )
          )
      )
      _ <- documentManager.openDocument(
        uri = params.textDocument.uri,
        languageId = params.textDocument.languageId,
        version = params.textDocument.version,
        text = params.textDocument.text
      )
      // Debounce validation on open to avoid lag when rapidly switching files
      _ <- debouncer.debounce(params.textDocument.uri)(validateDocument(params.textDocument.uri))
    } yield ()
  }.handleErrorWith(e => logger.warn(s"Error in didOpen: ${e.getMessage}"))

  private def handleDidChange(notification: Notification): IO[Unit] = {
    for {
      params <- IO.fromEither(
        notification.params
          .toRight(new Exception("Missing params"))
          .flatMap(
            _.as[DidChangeTextDocumentParams].left.map(e =>
              new Exception(s"Invalid params: ${e.message}")
            )
          )
      )
      uri = params.textDocument.uri
      _ <- params.contentChanges.headOption match {
        case Some(change) =>
          documentManager.updateDocument(
            uri = uri,
            version = params.textDocument.version,
            text = change.text
          )
        case None => IO.unit
      }
      // Use debounced validation to avoid excessive compilation during rapid typing
      _ <- debouncer.debounce(uri)(validateDocument(uri))
    } yield ()
  }.handleErrorWith(e => logger.warn(s"Error in didChange: ${e.getMessage}"))

  private def handleDidSave(notification: Notification): IO[Unit] = {
    for {
      params <- IO.fromEither(
        notification.params
          .toRight(new Exception("Missing params"))
          .flatMap(
            _.as[DidSaveTextDocumentParams].left.map(e =>
              new Exception(s"Invalid params: ${e.message}")
            )
          )
      )
      uri = params.textDocument.uri
      // Immediate validation on save - bypass debounce for responsive feedback
      _ <- debouncer.immediate(uri)(validateDocument(uri))
    } yield ()
  }.handleErrorWith(e => logger.warn(s"Error in didSave: ${e.getMessage}"))

  private def handleDidClose(notification: Notification): IO[Unit] = {
    for {
      params <- IO.fromEither(
        notification.params
          .toRight(new Exception("Missing params"))
          .flatMap(
            _.as[DidCloseTextDocumentParams].left.map(e =>
              new Exception(s"Invalid params: ${e.message}")
            )
          )
      )
      uri = params.textDocument.uri
      // Cancel any pending validation for this document
      _ <- debouncer.cancel(uri)
      _ <- documentManager.closeDocument(uri)
      // Clear diagnostics
      _ <- publishDiagnostics(PublishDiagnosticsParams(uri, List.empty))
    } yield ()
  }.handleErrorWith(e => logger.warn(s"Error in didClose: ${e.getMessage}"))

  // ========== Language Features ==========

  private def getCompletions(document: DocumentState, position: Position): IO[CompletionList] = {
    val wordAtCursor     = document.getWordAtPosition(position).getOrElse("")
    val lineText         = document.getLine(position.line).getOrElse("")
    val textBeforeCursor = lineText.take(position.character)

    // Check for with clause context first (higher priority than general completions)
    val withClauseContext = WithClauseCompletions.analyzeContext(textBeforeCursor, lineText)
    withClauseContext match {
      case WithClauseCompletions.NotInWithClause =>
        // Fall through to standard completion logic
        ()
      case ctx =>
        // Return with clause completions
        val items = WithClauseCompletions.getCompletions(ctx)
        return IO.pure(CompletionList(isIncomplete = false, items = items))
    }

    constellation.getModules.map { modules =>
      // Update the module completion trie if modules have changed
      updateModuleCompletionTrie(modules)

      // Check context: are we after "use " or after a namespace prefix like "stdlib."?
      val isAfterUse = textBeforeCursor.trim.startsWith("use ")
      val namespacePrefix = {
        // Check if we're typing after a dot (e.g., "stdlib." or "stdlib.math.")
        val dotIdx = textBeforeCursor.lastIndexOf('.')
        if dotIdx >= 0 then {
          val beforeDot = textBeforeCursor.take(dotIdx).trim.split("\\s+").last
          Some(beforeDot)
        } else None
      }

      val registry = compiler.functionRegistry

      // Namespace completions (for "use stdlib" context)
      val namespaceCompletions = if isAfterUse then {
        registry.namespaces.toList.map { ns =>
          CompletionItem(
            label = ns,
            kind = Some(CompletionItemKind.Module),
            detail = Some(s"Namespace: $ns"),
            documentation = Some(s"Import functions from $ns"),
            insertText = Some(ns),
            filterText = Some(ns),
            sortText = Some(s"0_$ns") // Sort namespaces first
          )
        }
      } else List.empty

      // Qualified function completions (for "stdlib.math." context)
      val qualifiedCompletions = namespacePrefix match {
        case Some(prefix) =>
          registry.all
            .filter { sig =>
              sig.namespace.exists(ns => ns == prefix || ns.startsWith(prefix + "."))
            }
            .map { sig =>
              val paramStr =
                sig.params.map { case (n, t) => s"$n: ${t.prettyPrint}" }.mkString(", ")
              CompletionItem(
                label = sig.name,
                kind = Some(CompletionItemKind.Function),
                detail = Some(s"${sig.qualifiedName}($paramStr) -> ${sig.returns.prettyPrint}"),
                documentation = Some(s"Function in namespace ${sig.namespace.getOrElse("global")}"),
                insertText = Some(s"${sig.name}()"),
                filterText = Some(sig.name),
                sortText = Some(sig.name)
              )
            }
        case None => List.empty
      }

      // Combine completions based on context using efficient trie lookups
      val allItems = if isAfterUse then {
        namespaceCompletions
      } else if namespacePrefix.isDefined then {
        qualifiedCompletions.toList
      } else {
        // Use trie-based prefix lookup for O(k) instead of O(n) filtering
        // where k = prefix length and n = number of items
        val keywordMatches = keywordCompletionTrie.findByPrefix(wordAtCursor)
        val moduleMatches = moduleCompletionTrie.findByPrefix(wordAtCursor)
        keywordMatches ++ moduleMatches
      }

      CompletionList(
        isIncomplete = false,
        items = allItems.toList
      )
    }
  }

  /**
   * Build a trie containing all keyword completions.
   * This is called once at initialization since keywords are static.
   */
  private def buildKeywordTrie(): CompletionTrie = {
    val keywords = List(
      CompletionItem("in", Some(CompletionItemKind.Keyword), Some("Input declaration"), None, None, None, None),
      CompletionItem("out", Some(CompletionItemKind.Keyword), Some("Output declaration"), None, None, None, None),
      CompletionItem("use", Some(CompletionItemKind.Keyword), Some("Import namespace"), None, Some("use "), None, None),
      CompletionItem("as", Some(CompletionItemKind.Keyword), Some("Alias for import"), None, None, None, None),
      CompletionItem("type", Some(CompletionItemKind.Keyword), Some("Type definition"), None, None, None, None),
      CompletionItem("if", Some(CompletionItemKind.Keyword), Some("Conditional expression"), None, None, None, None),
      CompletionItem("else", Some(CompletionItemKind.Keyword), Some("Else branch"), None, None, None, None),
      CompletionItem("true", Some(CompletionItemKind.Keyword), Some("Boolean true"), None, None, None, None),
      CompletionItem("false", Some(CompletionItemKind.Keyword), Some("Boolean false"), None, None, None, None)
    )
    CompletionTrie(keywords)
  }

  /**
   * Update the module completion trie if modules have changed.
   * Uses a simple cache invalidation based on module names.
   */
  private def updateModuleCompletionTrie(modules: List[io.constellation.ModuleNodeSpec]): Unit = {
    val currentNames = modules.map(_.name).toSet
    if (currentNames != lastModuleNames) {
      val moduleItems = modules.map { module =>
        val signature = TypeFormatter.formatSignature(module.name, module.consumes, module.produces)
        val enhancedDoc = if module.consumes.nonEmpty || module.produces.nonEmpty then {
          val paramsDoc = TypeFormatter.formatParameters(module.consumes)
          val returnsDoc = TypeFormatter.formatReturns(module.produces)
          s"""${module.metadata.description}
             |
             |**Parameters:**
             |$paramsDoc
             |
             |**Returns:** $returnsDoc
             |""".stripMargin
        } else {
          module.metadata.description
        }
        CompletionItem(
          label = module.name,
          kind = Some(CompletionItemKind.Function),
          detail = Some(signature),
          documentation = Some(enhancedDoc),
          insertText = Some(s"${module.name}()"),
          filterText = Some(module.name),
          sortText = Some(module.name)
        )
      }
      moduleCompletionTrie = CompletionTrie(moduleItems)
      lastModuleNames = currentNames
    }
  }

  private def getHover(document: DocumentState, position: Position): IO[Option[Hover]] = {
    val wordAtCursor = document.getWordAtPosition(position).getOrElse("")
    val lineText = document.getLine(position.line).getOrElse("")
    val textBeforeCursor = lineText.take(position.character)

    // Check for option/strategy hover first (higher priority in with clause context)
    diagnostics.OptionsDiagnostics.getHover(wordAtCursor, textBeforeCursor) match {
      case Some(hover) =>
        return IO.pure(Some(hover))
      case None =>
        // Fall through to module hover
    }

    for {
      _       <- logger.trace(s"Hover word at cursor: '$wordAtCursor'")
      modules <- constellation.getModules
      _       <- logger.trace(s"Hover total modules: ${modules.size}, looking for: '$wordAtCursor'")

      matchingModule = modules.find(_.name == wordAtCursor)
      _ <- logger.trace(s"Hover found module: ${matchingModule.isDefined}")

      result <- matchingModule match {
        case Some(module) =>
          for {
            _ <- logger.trace(s"Hover module: ${module.name}, consumes: ${module.consumes.keys
                .mkString(", ")}, produces: ${module.produces.keys.mkString(", ")}")
            signature = TypeFormatter.formatSignature(
              module.name,
              module.consumes,
              module.produces
            )
            hover <- (for {
              h <- IO.pure(constructHoverContent(module, signature))
              _ <- logger.trace("Hover constructed successfully")
            } yield Some(h)).handleErrorWith { error =>
              logger.warn(s"Error constructing hover: ${error.getMessage}").as(None)
            }
          } yield hover

        case None =>
          logger.trace(s"No module found for '$wordAtCursor'").as(None)
      }
    } yield result
  }

  private def constructHoverContent(
      module: io.constellation.ModuleNodeSpec,
      signature: String
  ): Hover = {
    // Format parameters section
    val paramsSection = if module.consumes.nonEmpty then {
      s"""
         |### Parameters
         |${TypeFormatter.formatParameters(module.consumes)}
         |""".stripMargin
    } else {
      ""
    }

    // Format returns section
    val returnsSection = s"""
                            |### Returns
                            |${TypeFormatter.formatReturns(module.produces)}
                            |""".stripMargin

    // Use metadata.description for consistency with completion
    // Handle tags safely in case they're empty
    val tagsString = if module.tags.nonEmpty then module.tags.mkString(", ") else "none"

    val markdown = s"""### `$signature`
                      |
                      |**Version**: ${module.majorVersion}.${module.minorVersion}
                      |
                      |${module.metadata.description}
                      |$paramsSection$returnsSection
                      |**Tags**: $tagsString
                      |""".stripMargin

    Hover(
      contents = MarkupContent(kind = "markdown", value = markdown)
    )
  }

  private def validateDocument(uri: String): IO[Unit] =
    documentManager.getDocument(uri).flatMap {
      case Some(document) =>
        val startTime = System.currentTimeMillis()
        val result = compiler.compile(document.text, "validation")
        val compileTime = System.currentTimeMillis() - startTime
        logger.info(s"[TIMING] validateDocument compile took ${compileTime}ms for ${uri.split('/').lastOption.getOrElse(uri)}").unsafeRunSync()
        result match {
          case Right(compileResult) =>
            // Convert warnings to diagnostics
            val warningDiagnostics = compileResult.warnings.flatMap { warning =>
              warning.span.map { span =>
                val (startLC, endLC) = document.sourceFile.spanToLineCol(span)
                Diagnostic(
                  range = Range(
                    start = Position(startLC.line - 1, startLC.col - 1),
                    end = Position(endLC.line - 1, endLC.col - 1)
                  ),
                  severity = Some(DiagnosticSeverity.Warning),
                  code = Some(warningCodeFor(warning)),
                  source = Some("constellation-lang"),
                  message = warning.message
                )
              }
            }
            publishDiagnostics(PublishDiagnosticsParams(uri, warningDiagnostics))

          case Left(errors) =>
            // Build suggestion context from compiler state
            val context = buildSuggestionContext()

            val diagnostics = errors.flatMap { error =>
              error.span.map { span =>
                // Convert span to line/col using SourceFile
                val (startLC, endLC) = document.sourceFile.spanToLineCol(span)

                // Get error code and suggestions from the new error system
                val errorCode   = CompilerErrorCodes.fromCompileError(error)
                val suggestions = Suggestions.forError(error, context)

                // Build enhanced message with explanation and suggestions
                val enhancedMessage = buildEnhancedMessage(error, errorCode, suggestions)

                Diagnostic(
                  range = Range(
                    start = Position(startLC.line - 1, startLC.col - 1), // LSP uses 0-based
                    end = Position(endLC.line - 1, endLC.col - 1)
                  ),
                  severity = Some(DiagnosticSeverity.Error),
                  code = Some(errorCode.code),
                  source = Some("constellation-lang"),
                  message = enhancedMessage
                )
              }
            }
            publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics))
        }

      case None =>
        IO.unit
    }

  /** Generate a warning code for a CompileWarning */
  private def warningCodeFor(warning: ast.CompileWarning): String = warning match {
    case _: ast.CompileWarning.OptionDependency => "OPTS001"
    case _: ast.CompileWarning.HighRetryCount   => "OPTS003"
  }

  /** Build suggestion context from compiler's function registry */
  private def buildSuggestionContext(): SuggestionContext = {
    val registry = compiler.functionRegistry
    SuggestionContext(
      definedVariables = Nil, // Would need to track per-document
      definedTypes = Nil,     // Would need to track per-document
      availableFunctions = registry.all.map(_.name),
      availableNamespaces = registry.namespaces.toList,
      functionsByNamespace = registry.all
        .groupBy(_.namespace.getOrElse("global"))
        .view
        .mapValues(_.map(_.name))
        .toMap
    )
  }

  /** Build enhanced error message with explanation and suggestions */
  private def buildEnhancedMessage(
      error: CompileError,
      errorCode: ErrorCode,
      suggestions: List[String]
  ): String = {
    val parts = List(
      error.message,
      "",
      errorCode.explanation
    ) ++ (
      if (suggestions.nonEmpty)
        List("", suggestions.mkString("\n"))
      else Nil
    )
    parts.mkString("\n")
  }

  private def executePipeline(
      document: DocumentState,
      inputs: Map[String, Json]
  ): IO[ExecutePipelineResult] = {
    import io.constellation.CValue

    // Generate unique DAG name from URI
    val dagName    = s"lsp-${document.uri.hashCode.abs}"
    val sourceFile = document.sourceFile

    compiler.compile(document.text, dagName) match {
      case Right(compiled) =>
        val startTime = System.currentTimeMillis()
        // Convert JSON inputs to CValue
        val cvalueInputs: Map[String, CValue] = inputs.flatMap { case (name, json) =>
          jsonToCValue(json).map(name -> _)
        }

        for {
          // Use the new API: constellation.run with LoadedProgram
          result <- constellation
            .run(compiled.program, cvalueInputs)
            .attempt
          endTime = System.currentTimeMillis()
          execResult = result match {
            case Right(sig) =>
              val outputJson: Map[String, Json] = sig.outputs.map { case (k, v) =>
                k -> cvalueToJson(v)
              }

              ExecutePipelineResult(
                success = true,
                outputs = Some(outputJson),
                error = None,
                errors = None,
                executionTimeMs = Some(endTime - startTime)
              )
            case Left(error) =>
              val errorInfo = runtimeErrorToErrorInfo(error)
              ExecutePipelineResult(
                success = false,
                outputs = None,
                error = Some(error.getMessage),
                errors = Some(List(errorInfo)),
                executionTimeMs = Some(endTime - startTime)
              )
          }
        } yield execResult

      case Left(errors) =>
        val errorInfos = errors.map(e => compileErrorToErrorInfo(e, sourceFile))
        IO.pure(
          ExecutePipelineResult(
            success = false,
            outputs = None,
            error = Some(errors.map(_.message).mkString("; ")),
            errors = Some(errorInfos)
          )
        )
    }
  }

  private def jsonToCValue(json: Json): Option[io.constellation.CValue] = {
    import io.constellation.CValue.*
    json.fold(
      jsonNull = None,
      jsonBoolean = b => Some(CBoolean(b)),
      jsonNumber = n => n.toLong.map(l => CInt(l)).orElse(Some(CFloat(n.toDouble))),
      jsonString = s => Some(CString(s)),
      jsonArray = arr => {
        val values = arr.flatMap(jsonToCValue).toVector
        if values.nonEmpty then {
          Some(CList(values, values.head.ctype))
        } else {
          Some(CList(Vector.empty, io.constellation.CType.CString)) // Default to string list
        }
      },
      jsonObject = obj => {
        val fields = obj.toMap.flatMap { case (k, v) =>
          jsonToCValue(v).map(k -> _)
        }
        val structure = fields.map { case (k, v) => k -> v.ctype }
        Some(CProduct(fields, structure))
      }
    )
  }

  private def cvalueToJson(cvalue: io.constellation.CValue): Json = {
    import io.constellation.CValue.*
    cvalue match {
      case CString(v)       => Json.fromString(v)
      case CInt(v)          => Json.fromLong(v)
      case CFloat(v)        => Json.fromDoubleOrNull(v)
      case CBoolean(v)      => Json.fromBoolean(v)
      case CList(values, _) => Json.fromValues(values.map(cvalueToJson))
      case CProduct(fields, _) =>
        Json.fromFields(fields.map { case (k, v) => k -> cvalueToJson(v) })
      case CMap(pairs, _, _) =>
        Json.fromFields(pairs.map { case (k, v) =>
          k.asInstanceOf[CString].value -> cvalueToJson(v)
        })
      case CUnion(value, _, tag) =>
        Json.obj("tag" -> Json.fromString(tag), "value" -> cvalueToJson(value))
      case CSome(value, _) => cvalueToJson(value)
      case CNone(_)        => Json.Null
    }
  }

  /** Convert CompileError to structured ErrorInfo for the extension */
  private def compileErrorToErrorInfo(error: CompileError, sourceFile: SourceFile): ErrorInfo = {
    // Use the new error code system
    val errorCode       = CompilerErrorCodes.fromCompileError(error)
    val context         = buildSuggestionContext()
    val suggestions     = Suggestions.forError(error, context)

    // Map compiler ErrorCategory to LSP ErrorCategory
    import io.constellation.lang.compiler.{ErrorCategory => CompilerCategory}
    val category = errorCode.category match {
      case CompilerCategory.Syntax    => ErrorCategory.Syntax
      case CompilerCategory.Type      => ErrorCategory.Type
      case CompilerCategory.Reference => ErrorCategory.Reference
      case CompilerCategory.Semantic  => ErrorCategory.Type // Map semantic to type for LSP
      case CompilerCategory.Internal  => ErrorCategory.Internal
    }

    val (line, column, endLine, endColumn, codeContext) = error.span match {
      case Some(span) =>
        val (startLC, endLC) = sourceFile.spanToLineCol(span)
        val codeCtx          = buildCodeContext(sourceFile, startLC.line, startLC.col, span.length)
        (Some(startLC.line), Some(startLC.col), Some(endLC.line), Some(endLC.col), Some(codeCtx))
      case None =>
        (None, None, None, None, None)
    }

    // Combine suggestions into a single string for the ErrorInfo
    val suggestion = suggestions.headOption

    ErrorInfo(
      category = category,
      message = s"[${errorCode.code}] ${error.message}",
      line = line,
      column = column,
      endLine = endLine,
      endColumn = endColumn,
      codeContext = codeContext,
      suggestion = suggestion
    )
  }

  /** Build a code context snippet showing the error location */
  private def buildCodeContext(
      sourceFile: SourceFile,
      errorLine: Int,
      errorCol: Int,
      spanLength: Int
  ): String = {
    val sb    = new StringBuilder()
    val lines = sourceFile.content.split("\n", -1) // -1 to preserve trailing empty strings

    // Show 1 line before if available
    if errorLine > 1 && errorLine - 2 < lines.length then {
      sb.append(f"  ${errorLine - 1}%3d │ ${lines(errorLine - 2)}\n")
    }

    // Show the error line
    if errorLine - 1 < lines.length then {
      val errorLineContent = lines(errorLine - 1)
      sb.append(f"  $errorLine%3d │ $errorLineContent\n")

      // Add the caret line pointing to the error
      val caretPadding = " " * (errorCol - 1)
      val carets       = "^" * (spanLength max 1)
      sb.append(f"      │ $caretPadding$carets\n")
    }

    // Show 1 line after if available
    if errorLine < lines.length then {
      sb.append(f"  ${errorLine + 1}%3d │ ${lines(errorLine)}")
    }

    sb.toString()
  }

  /** Convert a runtime exception to ErrorInfo */
  private def runtimeErrorToErrorInfo(error: Throwable): ErrorInfo =
    ErrorInfo(
      category = ErrorCategory.Runtime,
      message = error.getMessage,
      line = None,
      column = None,
      endLine = None,
      endColumn = None,
      codeContext = None,
      suggestion = None
    )
}

object ConstellationLanguageServer {
  /** Default debounce delay for document validation */
  val DefaultDebounceDelay: FiniteDuration = Debouncer.DefaultDelay

  /**
   * Create a new ConstellationLanguageServer with default debounce settings.
   */
  def create(
      constellation: Constellation,
      compiler: LangCompiler,
      publishDiagnostics: PublishDiagnosticsParams => IO[Unit]
  ): IO[ConstellationLanguageServer] =
    create(constellation, compiler, publishDiagnostics, DefaultDebounceDelay, None)

  /**
   * Create a new ConstellationLanguageServer with custom debounce delay.
   *
   * @param debounceDelay How long to wait after the last document change before validating.
   *                      Shorter delays give faster feedback but use more CPU.
   *                      Recommended range: 100ms - 300ms.
   */
  def create(
      constellation: Constellation,
      compiler: LangCompiler,
      publishDiagnostics: PublishDiagnosticsParams => IO[Unit],
      debounceDelay: FiniteDuration
  ): IO[ConstellationLanguageServer] =
    create(constellation, compiler, publishDiagnostics, debounceDelay, None)

  /**
   * Create a new ConstellationLanguageServer with execution tracking support.
   *
   * @param debounceDelay How long to wait after the last document change before validating.
   * @param executionTracker Optional execution tracker for enriching visualization with execution state.
   */
  def create(
      constellation: Constellation,
      compiler: LangCompiler,
      publishDiagnostics: PublishDiagnosticsParams => IO[Unit],
      debounceDelay: FiniteDuration,
      executionTracker: Option[ExecutionTracker[IO]]
  ): IO[ConstellationLanguageServer] =
    for {
      docManager   <- DocumentManager.create
      debugManager <- DebugSessionManager.create
      debouncer    <- Debouncer.create[String](debounceDelay)
    } yield new ConstellationLanguageServer(
      constellation,
      compiler,
      docManager,
      debugManager,
      debouncer,
      publishDiagnostics,
      executionTracker
    )
}
