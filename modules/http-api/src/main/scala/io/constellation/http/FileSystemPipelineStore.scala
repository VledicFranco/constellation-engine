package io.constellation.http

import cats.effect.IO
import cats.implicits.*
import io.circe.{Json, parser}
import io.circe.syntax.*
import io.constellation.{PipelineImage, PipelineStore}
import io.constellation.json.given
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.nio.file.{Files, Path, StandardOpenOption}
import scala.jdk.CollectionConverters.*

/** Filesystem-backed [[PipelineStore]] that wraps an existing in-memory store as a cache layer.
  *
  * Reads are served from the in-memory delegate (fast). Writes go to both the in-memory delegate
  * and the filesystem (durable). On initialization, persisted data is loaded from the filesystem
  * into the delegate.
  *
  * Filesystem layout:
  * {{{
  * <directory>/
  *   images/
  *     <structuralHash>.json    # PipelineImage serialized as JSON
  *   aliases.json               # { "name": "structuralHash", ... }
  *   syntactic-index.json       # { "syntacticHash": "structuralHash", ... }
  * }}}
  */
class FileSystemPipelineStore private[http] (
    private[http] val storeDirectory: Path,
    private[http] val delegate: PipelineStore
) extends PipelineStore {

  private val logger: Logger[IO] = Slf4jLogger.getLoggerFromClass[IO](classOf[FileSystemPipelineStore])

  private[http] val imagesDir     = storeDirectory.resolve("images")
  private[http] val aliasesFile   = storeDirectory.resolve("aliases.json")
  private[http] val syntacticFile = storeDirectory.resolve("syntactic-index.json")

  def store(image: PipelineImage): IO[String] =
    for {
      hash <- delegate.store(image)
      _    <- persistImage(image)
    } yield hash

  def alias(name: String, structuralHash: String): IO[Unit] =
    for {
      _ <- delegate.alias(name, structuralHash)
      _ <- persistAliases()
    } yield ()

  def resolve(name: String): IO[Option[String]] =
    delegate.resolve(name)

  def listAliases: IO[Map[String, String]] =
    delegate.listAliases

  def get(structuralHash: String): IO[Option[PipelineImage]] =
    delegate.get(structuralHash)

  def getByName(name: String): IO[Option[PipelineImage]] =
    delegate.getByName(name)

  def indexSyntactic(syntacticHash: String, registryHash: String, structuralHash: String): IO[Unit] =
    for {
      _ <- delegate.indexSyntactic(syntacticHash, registryHash, structuralHash)
      _ <- persistSyntacticIndex()
    } yield ()

  def lookupSyntactic(syntacticHash: String, registryHash: String): IO[Option[String]] =
    delegate.lookupSyntactic(syntacticHash, registryHash)

  def listImages: IO[List[PipelineImage]] =
    delegate.listImages

  def remove(structuralHash: String): IO[Boolean] =
    for {
      removed <- delegate.remove(structuralHash)
      _ <- if removed then removeImageFile(structuralHash) else IO.unit
    } yield removed

  // ========== Persistence Methods ==========

  private def persistImage(image: PipelineImage): IO[Unit] =
    IO {
      Files.createDirectories(imagesDir)
      val file = imagesDir.resolve(s"${image.structuralHash}.json")
      val json = image.asJson.spaces2
      Files.writeString(file, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
      ()
    }.handleErrorWith { e =>
      logger.warn(s"Failed to persist image ${image.structuralHash}: ${e.getMessage}")
    }

  private def persistAliases(): IO[Unit] =
    delegate.listAliases.flatMap { aliases =>
      IO {
        val json = Json.fromFields(aliases.map { case (k, v) => k -> Json.fromString(v) }).spaces2
        Files.writeString(aliasesFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        ()
      }.handleErrorWith { e =>
        logger.warn(s"Failed to persist aliases: ${e.getMessage}")
      }
    }

  private def persistSyntacticIndex(): IO[Unit] =
    delegate.listImages.flatMap { images =>
      val entries = images.flatMap { img =>
        if img.syntacticHash.nonEmpty then
          Some(img.syntacticHash -> Json.fromString(img.structuralHash))
        else None
      }
      IO {
        val json = Json.fromFields(entries).spaces2
        Files.writeString(syntacticFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        ()
      }.handleErrorWith { e =>
        logger.warn(s"Failed to persist syntactic index: ${e.getMessage}")
      }
    }

  private def removeImageFile(structuralHash: String): IO[Unit] =
    IO {
      val file = imagesDir.resolve(s"$structuralHash.json")
      Files.deleteIfExists(file)
      ()
    }.handleErrorWith { e =>
      logger.warn(s"Failed to remove image file $structuralHash: ${e.getMessage}")
    }
}

object FileSystemPipelineStore {

  private val logger: Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("io.constellation.http.FileSystemPipelineStore")

  /** Create a filesystem-backed pipeline store wrapping an existing in-memory store.
    *
    * Loads all persisted images, aliases, and syntactic index from the directory into the delegate
    * on initialization. Creates the directory structure if it doesn't exist.
    *
    * @param directory
    *   Root directory for persistent storage
    * @param delegate
    *   In-memory PipelineStore to use as cache layer (typically constellation.PipelineStore)
    * @return
    *   A new FileSystemPipelineStore with persisted data loaded
    */
  def init(directory: Path, delegate: PipelineStore): IO[FileSystemPipelineStore] =
    for {
      _ <- IO(Files.createDirectories(directory.resolve("images")))
      store = new FileSystemPipelineStore(directory, delegate)
      _ <- loadPersistedData(store)
    } yield store

  /** Load all persisted data from filesystem into the delegate store. */
  private def loadPersistedData(store: FileSystemPipelineStore): IO[Unit] =
    for {
      imageCount     <- loadImages(store)
      aliasCount     <- loadAliases(store)
      syntacticCount <- loadSyntacticIndex(store)
      _ <- logger.info(
        s"FileSystemPipelineStore: loaded $imageCount images, $aliasCount aliases, $syntacticCount syntactic entries from ${store.storeDirectory}"
      )
    } yield ()

  /** Load persisted pipeline images from the images/ directory. */
  private def loadImages(store: FileSystemPipelineStore): IO[Int] = IO {
    val dir = store.imagesDir
    if Files.exists(dir) then {
      Files.list(dir).iterator().asScala
        .filter(p => p.toString.endsWith(".json"))
        .toList
    } else Nil
  }.flatMap { files =>
    files.traverse { file =>
      IO(Files.readString(file)).flatMap { content =>
        parser.parse(content).flatMap(_.as[PipelineImage]) match {
          case Right(image) =>
            store.delegate.store(image).as(1)
          case Left(err) =>
            logger.warn(s"Skipping corrupted image file ${file.getFileName}: ${err.getMessage}").as(0)
        }
      }.handleErrorWith { e =>
        logger.warn(s"Failed to read image file ${file.getFileName}: ${e.getMessage}").as(0)
      }
    }.map(_.sum)
  }

  /** Load persisted aliases from aliases.json. */
  private def loadAliases(store: FileSystemPipelineStore): IO[Int] = {
    val file = store.aliasesFile
    IO(Files.exists(file)).flatMap {
      case false => IO.pure(0)
      case true =>
        IO(Files.readString(file)).flatMap { content =>
          parser.parse(content).flatMap(_.as[Map[String, String]]) match {
            case Right(aliases) =>
              val aliasList: List[(String, String)] = aliases.toList
              aliasList.traverse_ { case (name, hash) =>
                store.delegate.alias(name, hash)
              }.as(aliases.size)
            case Left(err) =>
              logger.warn(s"Skipping corrupted aliases.json: ${err.getMessage}").as(0)
          }
        }.handleErrorWith { e =>
          logger.warn(s"Failed to read aliases.json: ${e.getMessage}").as(0)
        }
    }
  }

  /** Load persisted syntactic index from syntactic-index.json. */
  private def loadSyntacticIndex(store: FileSystemPipelineStore): IO[Int] = {
    val file = store.syntacticFile
    IO(Files.exists(file)).flatMap {
      case false => IO.pure(0)
      case true =>
        IO(Files.readString(file)).flatMap { content =>
          parser.parse(content).flatMap(_.as[Map[String, String]]) match {
            case Right(entries) =>
              val entryList: List[(String, String)] = entries.toList
              entryList.traverse_ { case (syntacticHash, structuralHash) =>
                store.delegate.indexSyntactic(syntacticHash, "", structuralHash)
              }.as(entries.size)
            case Left(err) =>
              logger.warn(s"Skipping corrupted syntactic-index.json: ${err.getMessage}").as(0)
          }
        }.handleErrorWith { e =>
          logger.warn(s"Failed to read syntactic-index.json: ${e.getMessage}").as(0)
        }
    }
  }
}
