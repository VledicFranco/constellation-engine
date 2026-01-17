package io.constellation.lsp

import cats.effect.IO
import cats.implicits._
import io.circe._
import io.circe.syntax._
import io.constellation.Constellation
import io.constellation.lang.LangCompiler
import io.constellation.lang.ast.{Declaration, TypeExpr, Span, SourceFile}
import io.constellation.lang.parser.ConstellationParser
import io.constellation.lsp.protocol.JsonRpc._
import io.constellation.lsp.protocol.LspTypes._
import io.constellation.lsp.protocol.LspMessages._

/** Language server for constellation-lang with LSP support */
class ConstellationLanguageServer(
  constellation: Constellation,
  compiler: LangCompiler,
  documentManager: DocumentManager,
  publishDiagnostics: PublishDiagnosticsParams => IO[Unit]
) {

  /** Handle LSP request */
  def handleRequest(request: Request): IO[Response] = {
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

      case method =>
        IO.pure(Response(
          id = request.id,
          error = Some(ResponseError(
            code = ErrorCodes.MethodNotFound,
            message = s"Method not found: $method"
          ))
        ))
    }
  }

  /** Handle LSP notification (no response) */
  def handleNotification(notification: Notification): IO[Unit] = {
    notification.method match {
      case "initialized" =>
        IO.unit // Client finished initialization

      case "exit" =>
        IO.unit // Client is exiting, cleanup if needed

      case "textDocument/didOpen" =>
        handleDidOpen(notification)

      case "textDocument/didChange" =>
        handleDidChange(notification)

      case "textDocument/didClose" =>
        handleDidClose(notification)

      case _ =>
        IO.unit // Ignore unknown notifications
    }
  }

  // ========== Request Handlers ==========

  private def handleInitialize(request: Request): IO[Response] = {
    val result = InitializeResult(
      capabilities = ServerCapabilities(
        textDocumentSync = Some(1), // Full text sync
        completionProvider = Some(CompletionOptions(
          triggerCharacters = Some(List("(", ",", " "))
        )),
        hoverProvider = Some(true),
        executeCommandProvider = None // Command handled client-side
      )
    )

    IO.pure(Response(
      id = request.id,
      result = Some(result.asJson)
    ))
  }

  private def handleCompletion(request: Request): IO[Response] = {
    request.params match {
      case None =>
        IO.pure(Response(
          id = request.id,
          error = Some(ResponseError(ErrorCodes.InvalidParams, "Missing params"))
        ))
      case Some(json) =>
        json.as[CompletionParams] match {
          case Left(decodeError) =>
            IO.pure(Response(
              id = request.id,
              error = Some(ResponseError(ErrorCodes.InvalidParams, s"Invalid params: ${decodeError.message}"))
            ))
          case Right(params) =>
            documentManager.getDocument(params.textDocument.uri).flatMap {
              case Some(document) =>
                getCompletions(document, params.position).map { completions =>
                  Response(id = request.id, result = Some(completions.asJson))
                }
              case None =>
                IO.pure(Response(
                  id = request.id,
                  result = Some(CompletionList(isIncomplete = false, items = List.empty).asJson)
                ))
            }
        }
    }
  }

  private def handleHover(request: Request): IO[Response] = {
    request.params match {
      case None =>
        for {
          _ <- IO.println(s"[HOVER] Missing params in request")
        } yield Response(
          id = request.id,
          error = Some(ResponseError(ErrorCodes.InvalidParams, "Missing params"))
        )
      case Some(json) =>
        json.as[HoverParams] match {
          case Left(decodeError) =>
            for {
              _ <- IO.println(s"[HOVER] Params decode error: ${decodeError.message}")
            } yield Response(
              id = request.id,
              error = Some(ResponseError(ErrorCodes.InvalidParams, s"Invalid params: ${decodeError.message}"))
            )
          case Right(params) =>
            for {
              _ <- IO.println(s"[HOVER] Request received for uri: ${params.textDocument.uri}, position: ${params.position}")
              maybeDoc <- documentManager.getDocument(params.textDocument.uri)
              response <- maybeDoc match {
                case Some(document) =>
                  for {
                    hover <- getHover(document, params.position)
                    response = Response(id = request.id, result = hover.map(_.asJson))
                    _ <- IO.println(s"[HOVER] Response result: ${hover.isDefined}")
                    _ <- IO.println(s"[HOVER] Response JSON: ${response.asJson.noSpaces}")
                  } yield response
                case None =>
                  for {
                    _ <- IO.println(s"[HOVER] Document not found: ${params.textDocument.uri}")
                  } yield Response(id = request.id, result = None)
              }
            } yield response
        }
    }
  }

  private def handleExecutePipeline(request: Request): IO[Response] = {
    request.params match {
      case None =>
        IO.pure(Response(
          id = request.id,
          error = Some(ResponseError(ErrorCodes.InvalidParams, "Missing params"))
        ))
      case Some(json) =>
        json.as[ExecutePipelineParams] match {
          case Left(decodeError) =>
            IO.pure(Response(
              id = request.id,
              error = Some(ResponseError(ErrorCodes.InvalidParams, s"Invalid params: ${decodeError.message}"))
            ))
          case Right(params) =>
            documentManager.getDocument(params.uri).flatMap {
              case Some(document) =>
                executePipeline(document, params.inputs).map { execResult =>
                  Response(id = request.id, result = Some(execResult.asJson))
                }
              case None =>
                IO.pure(Response(
                  id = request.id,
                  result = Some(ExecutePipelineResult(
                    success = false,
                    outputs = None,
                    error = Some("Document not found")
                  ).asJson)
                ))
            }
        }
    }
  }

  private def handleGetInputSchema(request: Request): IO[Response] = {
    request.params match {
      case None =>
        IO.pure(Response(
          id = request.id,
          error = Some(ResponseError(ErrorCodes.InvalidParams, "Missing params"))
        ))
      case Some(json) =>
        json.as[GetInputSchemaParams] match {
          case Left(decodeError) =>
            IO.pure(Response(
              id = request.id,
              error = Some(ResponseError(ErrorCodes.InvalidParams, s"Invalid params: ${decodeError.message}"))
            ))
          case Right(params) =>
            documentManager.getDocument(params.uri).flatMap {
              case Some(document) =>
                IO.pure(getInputSchema(document)).map { schemaResult =>
                  Response(id = request.id, result = Some(schemaResult.asJson))
                }
              case None =>
                IO.pure(Response(
                  id = request.id,
                  result = Some(GetInputSchemaResult(
                    success = false,
                    inputs = None,
                    error = Some("Document not found")
                  ).asJson)
                ))
            }
        }
    }
  }

  private def getInputSchema(document: DocumentState): GetInputSchemaResult = {
    ConstellationParser.parse(document.text) match {
      case Right(program) =>
        val sourceFile = document.sourceFile
        val inputFields = program.declarations.collect {
          case Declaration.InputDecl(name, typeExpr) =>
            val (startLC, _) = sourceFile.spanToLineCol(name.span)
            InputField(
              name = name.value,
              `type` = typeExprToDescriptor(typeExpr.value),
              line = startLC.line
            )
        }
        GetInputSchemaResult(
          success = true,
          inputs = Some(inputFields),
          error = None
        )
      case Left(error) =>
        GetInputSchemaResult(
          success = false,
          inputs = None,
          error = Some(error.message)
        )
    }
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
          TypeDescriptor.RecordType(List(
            RecordField("_merged_left", leftDesc),
            RecordField("_merged_right", rightDesc)
          ))
      }
  }

  // ========== Notification Handlers ==========

  private def handleDidOpen(notification: Notification): IO[Unit] = {
    for {
      params <- IO.fromEither(
        notification.params
          .toRight(new Exception("Missing params"))
          .flatMap(_.as[DidOpenTextDocumentParams].left.map(e =>
            new Exception(s"Invalid params: ${e.message}")
          ))
      )
      _ <- documentManager.openDocument(
        uri = params.textDocument.uri,
        languageId = params.textDocument.languageId,
        version = params.textDocument.version,
        text = params.textDocument.text
      )
      _ <- validateDocument(params.textDocument.uri)
    } yield ()
  }.handleErrorWith(_ => IO.unit)

  private def handleDidChange(notification: Notification): IO[Unit] = {
    for {
      params <- IO.fromEither(
        notification.params
          .toRight(new Exception("Missing params"))
          .flatMap(_.as[DidChangeTextDocumentParams].left.map(e =>
            new Exception(s"Invalid params: ${e.message}")
          ))
      )
      _ <- params.contentChanges.headOption match {
        case Some(change) =>
          documentManager.updateDocument(
            uri = params.textDocument.uri,
            version = params.textDocument.version,
            text = change.text
          )
        case None => IO.unit
      }
      _ <- validateDocument(params.textDocument.uri)
    } yield ()
  }.handleErrorWith(_ => IO.unit)

  private def handleDidClose(notification: Notification): IO[Unit] = {
    for {
      params <- IO.fromEither(
        notification.params
          .toRight(new Exception("Missing params"))
          .flatMap(_.as[DidCloseTextDocumentParams].left.map(e =>
            new Exception(s"Invalid params: ${e.message}")
          ))
      )
      _ <- documentManager.closeDocument(params.textDocument.uri)
      // Clear diagnostics
      _ <- publishDiagnostics(PublishDiagnosticsParams(params.textDocument.uri, List.empty))
    } yield ()
  }.handleErrorWith(_ => IO.unit)

  // ========== Language Features ==========

  private def getCompletions(document: DocumentState, position: Position): IO[CompletionList] = {
    val wordAtCursor = document.getWordAtPosition(position).getOrElse("")

    constellation.getModules.map { modules =>
      val moduleCompletions = modules.map { module =>
        // Format signature for detail field
        val signature = TypeFormatter.formatSignature(
          module.name,
          module.consumes,
          module.produces
        )

        // Enhanced documentation with type info
        val enhancedDoc = if (module.consumes.nonEmpty || module.produces.nonEmpty) {
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

      val keywordCompletions = List(
        CompletionItem("in", Some(CompletionItemKind.Keyword), Some("Input declaration"), None, None, None, None),
        CompletionItem("out", Some(CompletionItemKind.Keyword), Some("Output declaration"), None, None, None, None)
      )

      val allItems = keywordCompletions ++ moduleCompletions.filter(_.label.toLowerCase.contains(wordAtCursor.toLowerCase))

      CompletionList(
        isIncomplete = false,
        items = allItems.toList
      )
    }
  }

  private def getHover(document: DocumentState, position: Position): IO[Option[Hover]] = {
    val wordAtCursor = document.getWordAtPosition(position).getOrElse("")

    for {
      _ <- IO.println(s"[HOVER DEBUG] Word at cursor: '$wordAtCursor'")
      modules <- constellation.getModules
      _ <- IO.println(s"[HOVER DEBUG] Total modules: ${modules.size}")
      _ <- IO.println(s"[HOVER DEBUG] Looking for module: '$wordAtCursor'")

      matchingModule = modules.find(_.name == wordAtCursor)
      _ <- IO.println(s"[HOVER DEBUG] Found module: ${matchingModule.isDefined}")

      result <- matchingModule match {
        case Some(module) =>
          for {
            _ <- IO.println(s"[HOVER DEBUG] Module: ${module.name}, consumes: ${module.consumes.keys.mkString(", ")}, produces: ${module.produces.keys.mkString(", ")}")
            signature = TypeFormatter.formatSignature(
              module.name,
              module.consumes,
              module.produces
            )
            _ <- IO.println(s"[HOVER DEBUG] Signature: $signature")
            hover <- (for {
              h <- IO.pure(constructHoverContent(module, signature))
              _ <- IO.println(s"[HOVER DEBUG] Hover constructed successfully")
            } yield Some(h)).handleErrorWith { error =>
              for {
                _ <- IO.println(s"[HOVER DEBUG] Error constructing hover: ${error.getMessage}")
              } yield None
            }
          } yield hover

        case None =>
          for {
            _ <- IO.println(s"[HOVER DEBUG] No module found for '$wordAtCursor'")
          } yield None
      }
    } yield result
  }

  private def constructHoverContent(module: io.constellation.ModuleNodeSpec, signature: String): Hover = {
    // Format parameters section
    val paramsSection = if (module.consumes.nonEmpty) {
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
    val tagsString = if (module.tags.nonEmpty) module.tags.mkString(", ") else "none"

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

  private def validateDocument(uri: String): IO[Unit] = {
    documentManager.getDocument(uri).flatMap {
      case Some(document) =>
        compiler.compile(document.text, "validation") match {
          case Right(_) =>
            // No errors
            publishDiagnostics(PublishDiagnosticsParams(uri, List.empty))

          case Left(errors) =>
            val diagnostics = errors.flatMap { error =>
              error.span.map { span =>
                // Convert span to line/col using SourceFile
                val (startLC, endLC) = document.sourceFile.spanToLineCol(span)

                Diagnostic(
                  range = Range(
                    start = Position(startLC.line - 1, startLC.col - 1),  // LSP uses 0-based
                    end = Position(endLC.line - 1, endLC.col - 1)
                  ),
                  severity = Some(DiagnosticSeverity.Error),
                  code = None,
                  source = Some("constellation-lang"),
                  message = error.message
                )
              }
            }
            publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics))
        }

      case None =>
        IO.unit
    }
  }

  private def executePipeline(document: DocumentState, inputs: Map[String, Json]): IO[ExecutePipelineResult] = {
    import io.constellation.CValue

    // Generate unique DAG name from URI
    val dagName = s"lsp-${document.uri.hashCode.abs}"

    compiler.compile(document.text, dagName) match {
      case Right(compiled) =>
        val startTime = System.currentTimeMillis()
        // Convert JSON inputs to CValue
        val cvalueInputs: Map[String, CValue] = inputs.flatMap { case (name, json) =>
          jsonToCValue(json).map(name -> _)
        }

        for {
          _ <- constellation.setDag(dagName, compiled.dagSpec)
          result <- constellation.runDag(dagName, cvalueInputs).attempt
          endTime = System.currentTimeMillis()
          execResult = result match {
            case Right(state) =>
              // Convert output CValues back to JSON
              // state.data is Map[UUID, Eval[CValue]] - we need to get by UUID and call .value
              // For now, just return basic output confirmation
              val outputJson: Map[String, Json] = state.data.flatMap { case (uuid, evalCvalue) =>
                // Try to find the nickname for this UUID from the DAG spec
                state.dag.data.get(uuid).flatMap { spec =>
                  spec.nicknames.values.headOption.map { nickname =>
                    nickname -> cvalueToJson(evalCvalue.value)
                  }
                }
              }
              ExecutePipelineResult(
                success = true,
                outputs = Some(outputJson),
                error = None,
                executionTimeMs = Some(endTime - startTime)
              )
            case Left(error) =>
              ExecutePipelineResult(
                success = false,
                outputs = None,
                error = Some(error.getMessage),
                executionTimeMs = Some(endTime - startTime)
              )
          }
        } yield execResult

      case Left(errors) =>
        IO.pure(ExecutePipelineResult(
          success = false,
          outputs = None,
          error = Some(errors.map(_.message).mkString("; "))
        ))
    }
  }

  private def jsonToCValue(json: Json): Option[io.constellation.CValue] = {
    import io.constellation.CValue._
    json.fold(
      jsonNull = None,
      jsonBoolean = b => Some(CBoolean(b)),
      jsonNumber = n => n.toLong.map(l => CInt(l)).orElse(Some(CFloat(n.toDouble))),
      jsonString = s => Some(CString(s)),
      jsonArray = arr => {
        val values = arr.flatMap(jsonToCValue).toVector
        if (values.nonEmpty) {
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
    import io.constellation.CValue._
    cvalue match {
      case CString(v) => Json.fromString(v)
      case CInt(v) => Json.fromLong(v)
      case CFloat(v) => Json.fromDoubleOrNull(v)
      case CBoolean(v) => Json.fromBoolean(v)
      case CList(values, _) => Json.fromValues(values.map(cvalueToJson))
      case CProduct(fields, _) => Json.fromFields(fields.map { case (k, v) => k -> cvalueToJson(v) })
      case CMap(pairs, _, _) => Json.fromFields(pairs.map { case (k, v) =>
        k.asInstanceOf[CString].value -> cvalueToJson(v)
      })
      case CUnion(value, _, tag) => Json.obj("tag" -> Json.fromString(tag), "value" -> cvalueToJson(value))
    }
  }
}

object ConstellationLanguageServer {
  def create(
    constellation: Constellation,
    compiler: LangCompiler,
    publishDiagnostics: PublishDiagnosticsParams => IO[Unit]
  ): IO[ConstellationLanguageServer] =
    DocumentManager.create.map { docManager =>
      new ConstellationLanguageServer(constellation, compiler, docManager, publishDiagnostics)
    }
}
