package io.constellation.http

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import scala.jdk.CollectionConverters.*
import scala.util.Try

import cats.data.EitherT
import cats.effect.IO
import cats.implicits.*

import io.constellation.errors.{ApiError, ErrorHandling}
import io.constellation.http.ApiModels.ErrorResponse
import io.constellation.http.DashboardModels.*
import io.constellation.lang.LangCompiler
import io.constellation.lang.viz.DagVizCompiler
import io.constellation.{CValue, Constellation}

import io.circe.Json
import io.circe.syntax.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.headers.`Content-Type`
import org.http4s.{HttpRoutes, MediaType, Response, StaticFile, Status, Uri}

/** HTTP routes for the Constellation Dashboard.
  *
  * Provides:
  *   - Dashboard HTML/static file serving
  *   - File browser API for .cst files
  *   - Execution API with history storage
  */
class DashboardRoutes(
    constellation: Constellation,
    compiler: LangCompiler,
    storage: ExecutionStorage[IO],
    config: DashboardConfig,
    executionWs: Option[ExecutionWebSocket] = None
) {

  /** Object for query parameter extraction */
  object LimitQueryParam  extends OptionalQueryParamDecoderMatcher[Int]("limit")
  object OffsetQueryParam extends OptionalQueryParamDecoderMatcher[Int]("offset")
  object ScriptQueryParam extends OptionalQueryParamDecoderMatcher[String]("script")

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    // ========================================
    // Dashboard UI Routes
    // ========================================

    // Serve dashboard HTML page
    case GET -> Root / "dashboard" =>
      serveResource("dashboard/index.html", MediaType.text.html)

    // Serve dashboard static files (both /dashboard/static/* and /static/* paths)
    case GET -> Root / "dashboard" / "static" / "css" / file =>
      serveResource(s"dashboard/static/css/$file", MediaType.text.css)

    case GET -> Root / "dashboard" / "static" / "js" / file =>
      serveResource(s"dashboard/static/js/$file", MediaType.application.javascript)

    case GET -> Root / "dashboard" / "static" / "js" / "components" / file =>
      serveResource(s"dashboard/static/js/components/$file", MediaType.application.javascript)

    // Also serve at /static/* for Vite-generated HTML references
    case GET -> Root / "static" / "css" / file =>
      serveResource(s"dashboard/static/css/$file", MediaType.text.css)

    case GET -> Root / "static" / "js" / file =>
      serveResource(s"dashboard/static/js/$file", MediaType.application.javascript)

    case GET -> Root / "static" / "js" / "components" / file =>
      serveResource(s"dashboard/static/js/components/$file", MediaType.application.javascript)

    // ========================================
    // File Browser API
    // ========================================

    // List all .cst files in the configured directory
    case GET -> Root / "api" / "v1" / "files" =>
      for {
        tree     <- IO(buildFileTree(config.getCstDirectory))
        response <- Ok(FilesResponse(config.getCstDirectory.toString, tree))
      } yield response

    // Get content of a specific file
    case GET -> Root / "api" / "v1" / "files" / path =>
      getFileContent(path)

    // Get content using encoded path query param (for paths with slashes)
    case req @ GET -> Root / "api" / "v1" / "file" =>
      req.params.get("path") match {
        case Some(path) => getFileContent(path)
        case None       => BadRequest(DashboardError.invalidRequest("Missing path parameter"))
      }

    // ========================================
    // Compilation Preview API
    // ========================================

    // Compile a script and return DAG visualization (without executing)
    case req @ POST -> Root / "api" / "v1" / "preview" =>
      (for {
        previewReq <- req.as[PreviewRequest]
        result     <- compileForPreview(previewReq.source).value
        response <- result match {
          case Right(dagVizIR) => Ok(PreviewResponse(success = true, dagVizIR = Some(dagVizIR)))
          case Left(errors)    => BadRequest(PreviewResponse(success = false, errors = errors))
        }
      } yield response).handleErrorWith { error =>
        InternalServerError(PreviewResponse(success = false, errors = List(error.getMessage)))
      }

    // ========================================
    // Execution API
    // ========================================

    // Execute a script with storage
    case req @ POST -> Root / "api" / "v1" / "execute" =>
      (for {
        execReq <- req.as[DashboardExecuteRequest]
        result  <- executeScript(execReq).value
        response <- result match {
          case Right(resp) => Ok(resp)
          case Left(error) => errorResponse(error)
        }
      } yield response).handleErrorWith { error =>
        InternalServerError(
          DashboardExecuteResponse(
            success = false,
            executionId = "",
            error = Some(s"Unexpected error: ${error.getMessage}")
          )
        )
      }

    // List executions with optional filtering
    case GET -> Root / "api" / "v1" / "executions" :? LimitQueryParam(limitOpt) +& OffsetQueryParam(
          offsetOpt
        ) +& ScriptQueryParam(scriptOpt) =>
      val limit  = limitOpt.getOrElse(50).min(100)
      val offset = offsetOpt.getOrElse(0)
      for {
        executions <- scriptOpt match {
          case Some(script) => storage.listByScript(script, limit)
          case None         => storage.list(limit, offset)
        }
        stats    <- storage.stats
        response <- Ok(ExecutionListResponse(executions, stats.totalExecutions, limit, offset))
      } yield response

    // Get a specific execution by ID
    case GET -> Root / "api" / "v1" / "executions" / executionId =>
      for {
        execOpt <- storage.get(executionId)
        response <- execOpt match {
          case Some(exec) => Ok(exec.asJson)
          case None       => NotFound(DashboardError.notFound("Execution", executionId))
        }
      } yield response

    // Get DAG visualization for an execution
    case GET -> Root / "api" / "v1" / "executions" / executionId / "dag" =>
      for {
        execOpt <- storage.get(executionId)
        response <- execOpt match {
          case Some(exec) =>
            exec.dagVizIR match {
              case Some(dag) => Ok(dag.asJson)
              case None =>
                NotFound(
                  DashboardError("no_dag", "DAG visualization not available for this execution")
                )
            }
          case None => NotFound(DashboardError.notFound("Execution", executionId))
        }
      } yield response

    // Delete an execution
    case DELETE -> Root / "api" / "v1" / "executions" / executionId =>
      for {
        deleted <- storage.delete(executionId)
        response <-
          if deleted then Ok(Json.obj("deleted" -> Json.fromBoolean(true)))
          else NotFound(DashboardError.notFound("Execution", executionId))
      } yield response

    // ========================================
    // Dashboard Status API
    // ========================================

    // Get dashboard status
    case GET -> Root / "api" / "v1" / "status" =>
      for {
        stats <- storage.stats
        response <- Ok(
          DashboardStatus(
            enabled = config.enableDashboard,
            cstDirectory = config.getCstDirectory.toString,
            sampleRate = config.defaultSampleRate,
            maxExecutions = config.maxStoredExecutions,
            storageStats = stats
          )
        )
      } yield response

    // ========================================
    // Modules API
    // ========================================

    // List all available modules
    case GET -> Root / "api" / "v1" / "modules" =>
      for {
        modules <- constellation.getModules
        moduleInfos = modules.map { spec =>
          ApiModels.ModuleInfo(
            name = spec.name,
            description = spec.description,
            version = s"${spec.majorVersion}.${spec.minorVersion}",
            inputs = spec.consumes.map { case (k, v) => k -> v.toString },
            outputs = spec.produces.map { case (k, v) => k -> v.toString }
          )
        }
        response <- Ok(ApiModels.ModuleListResponse(moduleInfos))
      } yield response
  }

  // ========================================
  // Private Helper Methods
  // ========================================

  /** Serve a resource from the classpath */
  private def serveResource(path: String, mediaType: MediaType): IO[Response[IO]] = {
    val resourcePath = s"/$path"
    StaticFile
      .fromResource[IO](resourcePath)
      .map(_.withContentType(`Content-Type`(mediaType)))
      .getOrElseF(NotFound(DashboardError.notFound("Resource", path)))
  }

  /** Compile source code and return DagVizIR for visualization preview */
  private def compileForPreview(
      source: String
  ): EitherT[IO, List[String], io.constellation.lang.viz.DagVizIR] =
    EitherT.fromEither[IO] {
      for {
        // First compile to IR
        irPipeline <- compiler
          .compileToIR(source, "preview")
          .leftMap(errors => errors.map(_.message))
        // Then generate visualization
        dagVizIR <- Try(DagVizCompiler.compile(irPipeline, Some("preview"))).toEither.leftMap(e =>
          List(e.getMessage)
        )
      } yield dagVizIR
    }

  /** Build a file tree from a directory, only including directories that contain .cst files */
  private def buildFileTree(root: Path): List[FileNode] =
    buildFileTreeRec(root, root)

  /** Directories excluded from the file browser (development/tooling dirs, not user scripts) */
  private val excludedDirs = Set(
    "node_modules",
    "dashboard-tests",
    "vscode-extension",
    ".git",
    "target",
    "out",
    ".bsp",
    ".idea",
    ".vscode",
    "project",
    ".github",
    "agents",
    "scripts",
    "brand"
  )

  /** Maximum directory recursion depth to prevent stack overflow from deeply nested trees. */
  private val MaxRecursionDepth = 20

  /** Maximum file size (10MB) for content serving to prevent memory exhaustion. */
  private val MaxFileSize: Long = 10L * 1024 * 1024

  /** Recursive helper that tracks the base root for relative path computation.
    *
    * Bounded to [[MaxRecursionDepth]] levels to prevent stack overflow from adversarial directory
    * structures. Uses try-finally to ensure `Files.list` streams are closed.
    */
  private def buildFileTreeRec(baseRoot: Path, currentDir: Path, depth: Int = 0): List[FileNode] = {
    if depth > MaxRecursionDepth then return List.empty
    if !Files.exists(currentDir) || !Files.isDirectory(currentDir) then return List.empty

    Try {
      val stream = Files.list(currentDir)
      try
        stream
          .iterator()
          .asScala
          .toList
          .filter(p => Files.isDirectory(p) || p.toString.endsWith(".cst"))
          .filterNot(p => Files.isDirectory(p) && excludedDirs.contains(p.getFileName.toString))
          .flatMap { path =>
            val name         = path.getFileName.toString
            val isDir        = Files.isDirectory(path)
            val relativePath = baseRoot.relativize(path).toString.replace("\\", "/")
            if isDir then
              val children = buildFileTreeRec(baseRoot, path, depth + 1)
              // Only include directories that have .cst files (directly or in subdirectories)
              if children.nonEmpty then
                Some(
                  FileNode(
                    name = name,
                    path = relativePath,
                    fileType = FileType.Directory,
                    children = Some(children)
                  )
                )
              else None
            else
              Some(
                FileNode(
                  name = name,
                  path = relativePath,
                  fileType = FileType.File,
                  size = Some(Files.size(path)),
                  modifiedTime = Some(Files.getLastModifiedTime(path).toMillis)
                )
              )
          }
          .sortBy(node => (node.fileType != FileType.Directory, node.name.toLowerCase))
      finally
        stream.close()
    }.getOrElse(List.empty)
  }

  /** Get file content and parse inputs/outputs.
    *
    * Validates that the resolved path stays within the configured CST directory (path traversal
    * defense) and that the file doesn't exceed [[MaxFileSize]] (memory exhaustion defense).
    */
  private def getFileContent(relativePath: String): IO[Response[IO]] = {
    // Use toAbsolutePath to ensure consistent path comparison on all platforms
    val baseDir = config.getCstDirectory.toAbsolutePath.normalize()
    val fullPath =
      baseDir.resolve(relativePath.replace("/", java.io.File.separator)).toAbsolutePath.normalize()

    // Path traversal defense: ensure resolved path is within the base directory
    if !fullPath.startsWith(baseDir) then
      return BadRequest(DashboardError.invalidRequest("Invalid file path"))

    if !Files.exists(fullPath) then return NotFound(DashboardError.notFound("File", relativePath))

    if !fullPath.toString.endsWith(".cst") then
      return BadRequest(DashboardError.invalidRequest("Only .cst files are supported"))

    // File size defense: prevent memory exhaustion from large files
    if Files.size(fullPath) > MaxFileSize then
      return BadRequest(
        DashboardError.invalidRequest(
          s"File exceeds maximum size of ${MaxFileSize / 1024 / 1024}MB"
        )
      )

    IO {
      val content      = Files.readString(fullPath)
      val lastModified = Files.getLastModifiedTime(fullPath).toMillis
      val fileName     = fullPath.getFileName.toString

      // Parse the script to extract inputs and outputs
      val (inputs, outputs) = parseScriptMetadata(content)

      FileContentResponse(
        path = relativePath,
        name = fileName,
        content = content,
        inputs = inputs,
        outputs = outputs,
        lastModified = Some(lastModified)
      )
    }.flatMap(Ok(_)).handleErrorWith { error =>
      InternalServerError(DashboardError.serverError(s"Failed to read file: ${error.getMessage}"))
    }
  }

  /** Parse a script to extract input and output declarations */
  private def parseScriptMetadata(source: String): (List[InputParam], List[OutputParam]) = {
    // Normalize line endings to \n for consistent parsing
    val normalizedSource = source.replace("\r\n", "\n").replace("\r", "\n")

    // Parse @example annotations followed by input declarations
    // Format: @example("value") or @example(value1, value2) followed by in varName: Type
    // The annotation must be immediately before the input (only whitespace/newlines between)
    // Type pattern: starts with letter/underscore, includes word chars and angle brackets for generics
    // but stops at newline or end of identifier
    val typePattern = """[A-Za-z_][\w]*(?:<[^>]+>)?"""
    val exampleInputPattern =
      s"""(?m)@example\\(([^)]+)\\)\\s*\\n\\s*in\\s+(\\w+)\\s*:\\s*($typePattern)""".r
    val simpleInputPattern = s"""(?m)^\\s*in\\s+(\\w+)\\s*:\\s*($typePattern)""".r
    val outputPattern      = """(?m)^\s*out\s+(\w+)(?:\s*:\s*(\S+))?""".r

    // First, extract inputs with examples
    val inputsWithExamples = exampleInputPattern
      .findAllMatchIn(normalizedSource)
      .map { m =>
        val exampleRaw = m.group(1).trim
        val name       = m.group(2)
        val paramType  = m.group(3).trim

        // Parse the example value - remove quotes if string
        val exampleValue = parseExampleValue(exampleRaw, paramType)

        InputParam(
          name = name,
          paramType = paramType,
          required = true,
          defaultValue = Some(exampleValue)
        )
      }
      .toList

    val inputNamesWithExamples = inputsWithExamples.map(_.name).toSet

    // Then extract inputs without examples (that weren't already captured)
    val inputsWithoutExamples = simpleInputPattern
      .findAllMatchIn(normalizedSource)
      .map { m =>
        InputParam(
          name = m.group(1),
          paramType = m.group(2).trim,
          required = true
        )
      }
      .filterNot(i => inputNamesWithExamples.contains(i.name))
      .toList

    // Filter out any invalid inputs (e.g., where name looks like a type)
    val primitiveTypes = Set(
      "Int",
      "String",
      "Boolean",
      "Float",
      "Double",
      "Long",
      "Any",
      "Unit",
      "List",
      "Map",
      "Set",
      "Option"
    )
    val allInputs = (inputsWithExamples ++ inputsWithoutExamples)
      .filter(i => i.name.nonEmpty && !primitiveTypes.contains(i.name) && i.name.head.isLower)

    val outputs = outputPattern
      .findAllMatchIn(normalizedSource)
      .map { m =>
        OutputParam(
          name = m.group(1),
          paramType = Option(m.group(2)).getOrElse("Any")
        )
      }
      .toList

    (allInputs, outputs)
  }

  /** Parse an example value based on the parameter type */
  private def parseExampleValue(raw: String, paramType: String): Json = {
    val trimmed = raw.trim

    // Handle quoted strings
    if trimmed.startsWith("\"") && trimmed.endsWith("\"") then
      Json.fromString(trimmed.substring(1, trimmed.length - 1))
    // Handle record literals: { field: value, ... }
    else if trimmed.startsWith("{") && trimmed.endsWith("}") then
      parseRecordLiteral(trimmed)
    // Handle list literals: [ value, ... ]
    else if trimmed.startsWith("[") && trimmed.endsWith("]") then
      parseListLiteral(trimmed)
    // Handle numbers
    else if trimmed.matches("-?\\d+\\.\\d+") then Json.fromDoubleOrNull(trimmed.toDouble)
    else if trimmed.matches("-?\\d+") then
      paramType.toLowerCase match {
        case "int" | "integer"  => Json.fromInt(trimmed.toInt)
        case "long"             => Json.fromLong(trimmed.toLong)
        case "double" | "float" => Json.fromDoubleOrNull(trimmed.toDouble)
        case _                  => Json.fromInt(trimmed.toInt)
      }
    // Handle booleans
    else if trimmed == "true" || trimmed == "false" then Json.fromBoolean(trimmed.toBoolean)
    // Default to string
    else Json.fromString(trimmed)
  }

  /** Parse a constellation-lang record literal to JSON.
    * Converts `{ name: "Alice", age: 30 }` to `{"name": "Alice", "age": 30}`
    */
  private def parseRecordLiteral(raw: String): Json = {
    val inner = raw.trim.drop(1).dropRight(1).trim // Remove { }
    if inner.isEmpty then return Json.obj()

    // Split by commas (but not commas inside nested structures)
    val fields = splitTopLevelCommas(inner)
    val pairs = fields.flatMap { field =>
      val colonIdx = field.indexOf(':')
      if colonIdx > 0 then {
        val name = field.substring(0, colonIdx).trim
        val value = field.substring(colonIdx + 1).trim
        Some(name -> parseExampleValue(value, "Any"))
      } else None
    }
    Json.obj(pairs: _*)
  }

  /** Parse a constellation-lang list literal to JSON.
    * Converts `[1, 2, 3]` to `[1, 2, 3]`
    */
  private def parseListLiteral(raw: String): Json = {
    val inner = raw.trim.drop(1).dropRight(1).trim // Remove [ ]
    if inner.isEmpty then return Json.arr()

    val elements = splitTopLevelCommas(inner)
    Json.arr(elements.map(e => parseExampleValue(e.trim, "Any")): _*)
  }

  /** Split a string by commas, but only at the top level (not inside nested { } or [ ]) */
  private def splitTopLevelCommas(s: String): List[String] = {
    val result = scala.collection.mutable.ListBuffer[String]()
    val current = new StringBuilder
    var depth = 0

    for c <- s do {
      c match {
        case '{' | '[' =>
          depth += 1
          current.append(c)
        case '}' | ']' =>
          depth -= 1
          current.append(c)
        case ',' if depth == 0 =>
          result += current.toString.trim
          current.clear()
        case _ =>
          current.append(c)
      }
    }
    if current.nonEmpty then result += current.toString.trim
    result.toList
  }

  /** Execute a script and store the result */
  private def executeScript(
      req: DashboardExecuteRequest
  ): EitherT[IO, ApiError, DashboardExecuteResponse] = {
    val source     = determineSource(req.source)
    val sampleRate = req.sampleRate.getOrElse(config.defaultSampleRate)
    val shouldStore =
      ExecutionStorage.shouldSample(source, req.sampleRate, config.defaultSampleRate)

    for {
      // Read the script file
      content <- EitherT(readScriptFile(req.scriptPath))

      // Compile the script
      compiled <- EitherT(
        compiler
          .compileIO(content, "dashboard_exec")
          .map(_.leftMap { errors =>
            ApiError.CompilationError(errors.map(_.message))
          })
      )

      // Also compile to IR for DAG visualization (best effort)
      irPipeline = compiler.compileToIR(content, "dashboard_exec").toOption

      // Create execution record
      startTime   = System.currentTimeMillis()
      executionId = UUID.randomUUID().toString

      // Optionally compile the DAG visualization
      dagVizIR = irPipeline.flatMap(ir =>
        Try(
          DagVizCompiler.compile(ir, Some(compiled.pipeline.image.dagSpec.metadata.name))
        ).toOption
      )

      // Create initial execution record if sampling
      _ <-
        if shouldStore then
          EitherT.liftF(
            storage.store(
              ExecutionStorage
                .createExecution(
                  dagName = compiled.pipeline.image.dagSpec.metadata.name,
                  scriptPath = Some(req.scriptPath),
                  inputs = req.inputs,
                  source = source,
                  sampleRate = sampleRate
                )
                .copy(executionId = executionId, dagVizIR = dagVizIR)
            )
          )
        else EitherT.pure[IO, ApiError](())

      // Publish execution:start event for live visualization
      _ <- EitherT.liftF(
        executionWs.fold(IO.unit)(ws => ws.publish(
          executionId,
          ExecutionEvent.ExecutionStarted(
            executionId = executionId,
            dagName = compiled.pipeline.image.dagSpec.metadata.name,
            timestamp = System.currentTimeMillis()
          )
        ))
      )

      // Convert inputs
      inputs <- convertInputs(req.inputs, compiled.pipeline.image.dagSpec)

      // Execute using the new API
      sig <- ErrorHandling
        .liftIO(constellation.run(compiled.pipeline, inputs)) { t =>
          ApiError.ExecutionError(s"Execution failed: ${t.getMessage}")
        }
        .leftSemiflatTap { error =>
          // Record failure in execution storage so dashboard doesn't show perpetually running
          val failTime = System.currentTimeMillis()
          val recordFailure = if shouldStore then
            storage
              .update(executionId) { exec =>
                exec.copy(
                  endTime = Some(failTime),
                  status = ExecutionStatus.Failed
                )
              }
              .void
          else IO.unit

          // Publish execution:complete with failed status
          val publishFailure = executionWs.fold(IO.unit)(ws => ws.publish(
            executionId,
            ExecutionEvent.ExecutionCompleted(
              executionId = executionId,
              dagName = compiled.pipeline.image.dagSpec.metadata.name,
              succeeded = false,
              durationMs = failTime - startTime,
              timestamp = System.currentTimeMillis()
            )
          ))

          recordFailure *> publishFailure
        }

      // Extract outputs from DataSignature
      outputs = sig.outputs.map { case (k, v) =>
        k -> io.constellation.JsonCValueConverter.cValueToJson(v)
      }

      endTime    = System.currentTimeMillis()
      durationMs = endTime - startTime

      // Update execution record with results
      _ <-
        if shouldStore then
          EitherT.liftF(storage.update(executionId) { exec =>
            exec.copy(
              endTime = Some(endTime),
              outputs = Some(outputs),
              status = ExecutionStatus.Completed
            )
          })
        else EitherT.pure[IO, ApiError](None)

      // Publish execution:complete event for live visualization
      _ <- EitherT.liftF(
        executionWs.fold(IO.unit)(ws => ws.publish(
          executionId,
          ExecutionEvent.ExecutionCompleted(
            executionId = executionId,
            dagName = compiled.pipeline.image.dagSpec.metadata.name,
            succeeded = true,
            durationMs = durationMs,
            timestamp = System.currentTimeMillis()
          )
        ))
      )

    } yield DashboardExecuteResponse(
      success = true,
      executionId = executionId,
      outputs = outputs,
      dashboardUrl = Some(s"/dashboard#/executions/$executionId"),
      durationMs = Some(durationMs)
    )
  }

  /** Determine execution source from request */
  private def determineSource(source: Option[String]): ExecutionSource =
    source.map(_.toLowerCase) match {
      case Some("vscode") | Some("vscode-extension") => ExecutionSource.VSCodeExtension
      case Some("dashboard")                         => ExecutionSource.Dashboard
      case _                                         => ExecutionSource.API
    }

  /** Read a script file. Validates path stays within the CST directory. */
  private def readScriptFile(scriptPath: String): IO[Either[ApiError, String]] = IO {
    // Use toAbsolutePath to ensure consistent path comparison on all platforms
    val baseDir = config.getCstDirectory.toAbsolutePath.normalize()
    val fullPath =
      baseDir.resolve(scriptPath.replace("/", java.io.File.separator)).toAbsolutePath.normalize()
    if !fullPath.startsWith(baseDir) then Left(ApiError.InputError("Invalid file path"))
    else if !Files.exists(fullPath) then Left(ApiError.NotFoundError("Script", scriptPath))
    else Right(Files.readString(fullPath))
  }

  /** Convert JSON inputs to CValue */
  private def convertInputs(
      inputs: Map[String, Json],
      dagSpec: io.constellation.DagSpec
  ): EitherT[IO, ApiError, Map[String, CValue]] =
    ErrorHandling.liftIO(ExecutionHelper.convertInputs(inputs, dagSpec)) { t =>
      ApiError.InputError(t.getMessage)
    }

  /** Convert API error to HTTP response */
  private def errorResponse(error: ApiError): IO[Response[IO]] = error match {
    case ApiError.NotFoundError(resource, name) =>
      NotFound(DashboardError.notFound(resource, name))
    case ApiError.InputError(msg) =>
      BadRequest(DashboardError.invalidRequest(msg))
    case ApiError.CompilationError(errors) =>
      BadRequest(DashboardError("compilation_error", errors.mkString("; ")))
    case ApiError.ExecutionError(msg) =>
      InternalServerError(DashboardError.serverError(msg))
    case ApiError.OutputError(msg) =>
      InternalServerError(DashboardError.serverError(msg))
  }
}

object DashboardRoutes {

  /** Create DashboardRoutes instance */
  def apply(
      constellation: Constellation,
      compiler: LangCompiler,
      storage: ExecutionStorage[IO],
      config: DashboardConfig,
      executionWs: Option[ExecutionWebSocket] = None
  ): DashboardRoutes =
    new DashboardRoutes(constellation, compiler, storage, config, executionWs)

  /** Create DashboardRoutes with default in-memory storage */
  def withDefaultStorage(
      constellation: Constellation,
      compiler: LangCompiler,
      config: DashboardConfig,
      executionWs: Option[ExecutionWebSocket] = None
  ): IO[DashboardRoutes] = for {
    storage <- ExecutionStorage.inMemory(
      ExecutionStorage.Config(
        maxExecutions = config.maxStoredExecutions
      )
    )
  } yield new DashboardRoutes(constellation, compiler, storage, config, executionWs)
}
