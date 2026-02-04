package io.constellation.http

import cats.effect.IO
import cats.implicits.*
import io.constellation.{Constellation, ContentHash}
import io.constellation.lang.LangCompiler
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Loads `.cst` pipeline files from a directory at server startup.
  *
  * Compiles each file, stores the resulting [[io.constellation.PipelineImage]] in
  * [[io.constellation.PipelineStore]], and optionally creates aliases based on the configured
  * [[AliasStrategy]].
  */
object PipelineLoader {

  private val logger: Logger[IO] =
    Slf4jLogger.getLoggerFromName[IO]("io.constellation.http.PipelineLoader")

  /** Result of a pipeline loading operation. */
  case class LoadResult(loaded: Int, failed: Int, skipped: Int, errors: List[String])

  /** Load all `.cst` files from the configured directory.
    *
    * @param config
    *   Loader configuration (directory, recursion, error handling, alias strategy)
    * @param constellation
    *   The constellation engine instance (provides PipelineStore)
    * @param compiler
    *   The compiler to use for compiling `.cst` sources
    * @return
    *   A [[LoadResult]] summarizing what happened
    */
  def load(
      config: PipelineLoaderConfig,
      constellation: Constellation,
      compiler: LangCompiler
  ): IO[LoadResult] = {
    val dir = config.directory

    // Validate directory
    if (!Files.exists(dir))
      return IO.raiseError(
        new IllegalArgumentException(s"Pipeline directory does not exist: $dir")
      )
    if (!Files.isDirectory(dir))
      return IO.raiseError(
        new IllegalArgumentException(s"Pipeline path is not a directory: $dir")
      )

    // Compute registry hash (same approach as CachingLangCompiler)
    val registryHash = ContentHash.computeSHA256(
      compiler.functionRegistry.all.map(_.toString).sorted.mkString(",").getBytes("UTF-8")
    )

    for {
      files <- IO(scanForCstFiles(dir, config.recursive))
      _     <- logger.info(s"PipelineLoader: found ${files.size} .cst file(s) in $dir")
      result <- processFiles(files, dir, config, constellation, compiler, registryHash)
      _ <- logger.info(
        s"PipelineLoader: loaded=${result.loaded}, failed=${result.failed}, skipped=${result.skipped}"
      )
      _ <-
        if config.failOnError && result.errors.nonEmpty then
          IO.raiseError(
            new RuntimeException(
              s"PipelineLoader: ${result.errors.size} file(s) failed to compile:\n  - ${result.errors.mkString("\n  - ")}"
            )
          )
        else IO.unit
    } yield result
  }

  /** Scan for `.cst` files, sorted by path for deterministic ordering. */
  private def scanForCstFiles(dir: Path, recursive: Boolean): List[Path] = {
    val stream =
      if recursive then Files.walk(dir)
      else Files.list(dir)
    try
      stream
        .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".cst"))
        .sorted()
        .collect(java.util.stream.Collectors.toList[Path])
        .asScala
        .toList
    finally stream.close()
  }

  /** Process all discovered files, tracking aliases for collision detection. */
  private def processFiles(
      files: List[Path],
      baseDir: Path,
      config: PipelineLoaderConfig,
      constellation: Constellation,
      compiler: LangCompiler,
      registryHash: String
  ): IO[LoadResult] = {
    // Track seen alias names for FileName collision detection
    case class Acc(loaded: Int, failed: Int, skipped: Int, errors: List[String], seenAliases: Set[String])

    files
      .foldLeftM(Acc(0, 0, 0, Nil, Set.empty)) { (acc, file) =>
        val aliasName = deriveAlias(file, baseDir, config.aliasStrategy)

        // Check for FileName collision
        aliasName match {
          case Some(name) if acc.seenAliases.contains(name) =>
            val msg = s"Alias collision: '$name' already loaded, skipping $file"
            logger.error(msg).as(
              acc.copy(failed = acc.failed + 1, errors = acc.errors :+ msg)
            )

          case _ =>
            processFile(file, aliasName, constellation, compiler, registryHash).map {
              case FileResult.Loaded =>
                acc.copy(
                  loaded = acc.loaded + 1,
                  seenAliases = aliasName.fold(acc.seenAliases)(acc.seenAliases + _)
                )
              case FileResult.Skipped =>
                acc.copy(
                  skipped = acc.skipped + 1,
                  seenAliases = aliasName.fold(acc.seenAliases)(acc.seenAliases + _)
                )
              case FileResult.Failed(msg) =>
                acc.copy(
                  failed = acc.failed + 1,
                  errors = acc.errors :+ msg,
                  seenAliases = aliasName.fold(acc.seenAliases)(acc.seenAliases + _)
                )
            }
        }
      }
      .map(acc => LoadResult(acc.loaded, acc.failed, acc.skipped, acc.errors))
  }

  private enum FileResult {
    case Loaded
    case Skipped
    case Failed(message: String)
  }

  /** Process a single `.cst` file: read, dedup-check, compile, store, alias. */
  private def processFile(
      file: Path,
      aliasName: Option[String],
      constellation: Constellation,
      compiler: LangCompiler,
      registryHash: String
  ): IO[FileResult] = {
    val dagName = aliasName.getOrElse(file.getFileName.toString.stripSuffix(".cst"))

    for {
      source     <- IO(Files.readString(file))
      sourceHash <- IO(ContentHash.computeSHA256(source.getBytes("UTF-8")))

      // Check syntactic cache for dedup
      existing <- constellation.PipelineStore.lookupSyntactic(sourceHash, registryHash)

      result <- existing match {
        case Some(_) =>
          logger.info(s"PipelineLoader: skipping $file (already in store)").as(FileResult.Skipped)
        case None =>
          compileAndStore(file, source, dagName, aliasName, constellation, compiler, sourceHash, registryHash)
      }
    } yield result
  }

  /** Compile source, store image, create alias, index syntactic hash. */
  private def compileAndStore(
      file: Path,
      source: String,
      dagName: String,
      aliasName: Option[String],
      constellation: Constellation,
      compiler: LangCompiler,
      sourceHash: String,
      registryHash: String
  ): IO[FileResult] =
    compiler.compileIO(source, dagName).flatMap {
      case Right(compiled) =>
        val image = compiled.pipeline.image
        for {
          _ <- constellation.PipelineStore.store(image)
          _ <- constellation.PipelineStore.indexSyntactic(sourceHash, registryHash, image.structuralHash)
          _ <- aliasName.traverse_(name => constellation.PipelineStore.alias(name, image.structuralHash))
          _ <- logger.info(
            s"PipelineLoader: loaded $file -> hash=${image.structuralHash.take(12)}..." +
              aliasName.fold("")(n => s", alias='$n'")
          )
        } yield FileResult.Loaded

      case Left(errors) =>
        val msg = s"$file: ${errors.map(_.message).mkString("; ")}"
        logger.warn(s"PipelineLoader: failed to compile $msg").as(FileResult.Failed(msg))
    }

  /** Derive the alias name from a file path based on the alias strategy. */
  private def deriveAlias(
      file: Path,
      baseDir: Path,
      strategy: AliasStrategy
  ): Option[String] = strategy match {
    case AliasStrategy.HashOnly =>
      None
    case AliasStrategy.FileName =>
      Some(file.getFileName.toString.stripSuffix(".cst"))
    case AliasStrategy.RelativePath =>
      val rel = baseDir.relativize(file).toString.replace('\\', '/')
      Some(rel.stripSuffix(".cst"))
  }
}
