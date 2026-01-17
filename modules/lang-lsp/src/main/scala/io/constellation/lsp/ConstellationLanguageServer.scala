package io.constellation.lsp

import cats.effect.IO
import cats.implicits._
import io.circe._
import io.circe.syntax._
import io.constellation.Constellation
import io.constellation.lang.LangCompiler
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

      case "textDocument/completion" =>
        handleCompletion(request)

      case "textDocument/hover" =>
        handleHover(request)

      case "constellation/executePipeline" =>
        handleExecutePipeline(request)

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
        executeCommandProvider = Some(ExecuteCommandOptions(
          commands = List("constellation.executePipeline")
        ))
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
        IO.pure(Response(
          id = request.id,
          error = Some(ResponseError(ErrorCodes.InvalidParams, "Missing params"))
        ))
      case Some(json) =>
        json.as[HoverParams] match {
          case Left(decodeError) =>
            IO.pure(Response(
              id = request.id,
              error = Some(ResponseError(ErrorCodes.InvalidParams, s"Invalid params: ${decodeError.message}"))
            ))
          case Right(params) =>
            documentManager.getDocument(params.textDocument.uri).flatMap {
              case Some(document) =>
                getHover(document, params.position).map { hover =>
                  Response(id = request.id, result = hover.map(_.asJson))
                }
              case None =>
                IO.pure(Response(id = request.id, result = None))
            }
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
        CompletionItem(
          label = module.name,
          kind = Some(CompletionItemKind.Function),
          detail = Some(s"v${module.majorVersion}.${module.minorVersion}"),
          documentation = Some(module.metadata.description),
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

    constellation.getModules.map { modules =>
      modules.find(_.name == wordAtCursor).map { module =>
        val markdown = s"""**${module.name}** (v${module.majorVersion}.${module.minorVersion})
                          |
                          |${module.description}
                          |
                          |**Tags:** ${module.tags.mkString(", ")}
                          |""".stripMargin

        Hover(
          contents = MarkupContent(kind = "markdown", value = markdown)
        )
      }
    }
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
              error.position.map { pos =>
                Diagnostic(
                  range = Range(
                    start = Position(pos.line - 1, pos.column - 1),
                    end = Position(pos.line - 1, pos.column + 10)
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
    // Generate unique DAG name from URI
    val dagName = s"lsp-${document.uri.hashCode.abs}"

    compiler.compile(document.text, dagName) match {
      case Right(compiled) =>
        for {
          _ <- constellation.setDag(dagName, compiled.dagSpec)
          // Convert JSON inputs to CValue (simplified - would need proper conversion)
          // For now, assume inputs are already in correct format
          result <- constellation.runDag(dagName, Map.empty).attempt
          execResult = result match {
            case Right(state) =>
              ExecutePipelineResult(
                success = true,
                outputs = Some(Map.empty), // Would convert CValue to JSON
                error = None
              )
            case Left(error) =>
              ExecutePipelineResult(
                success = false,
                outputs = None,
                error = Some(error.getMessage)
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
