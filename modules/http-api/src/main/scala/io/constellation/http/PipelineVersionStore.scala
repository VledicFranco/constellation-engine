package io.constellation.http

import cats.effect.{IO, Ref}
import cats.implicits.*

import java.time.Instant

/** A version entry for a named pipeline. */
case class PipelineVersion(
    version: Int,
    structuralHash: String,
    createdAt: Instant,
    source: Option[String] = None
)

/** In-memory store that tracks version history for named pipelines.
  *
  * Each call to [[recordVersion]] auto-increments the version number and sets the new version as
  * active. Old versions are retained so callers can list history and roll back.
  */
trait PipelineVersionStore {

  /** Record a new version for a named pipeline.
    *
    * Auto-increments the version number and sets it as active.
    *
    * @param name
    *   Pipeline alias name
    * @param structuralHash
    *   Structural hash of the compiled pipeline image
    * @param source
    *   Optional source code that produced this version
    * @return
    *   The newly created version entry
    */
  def recordVersion(name: String, structuralHash: String, source: Option[String]): IO[PipelineVersion]

  /** List all versions for a named pipeline, newest first. */
  def listVersions(name: String): IO[List[PipelineVersion]]

  /** Get the active version number for a named pipeline. */
  def activeVersion(name: String): IO[Option[Int]]

  /** Set the active version for a named pipeline.
    *
    * @return
    *   `true` if the version exists and was set, `false` if the version doesn't exist
    */
  def setActiveVersion(name: String, version: Int): IO[Boolean]

  /** Get a specific version entry. */
  def getVersion(name: String, version: Int): IO[Option[PipelineVersion]]

  /** Get the version just before the currently active one (for quick rollback). */
  def previousVersion(name: String): IO[Option[PipelineVersion]]
}

object PipelineVersionStore {

  /** Create a new in-memory version store backed by `Ref`. */
  def init: IO[PipelineVersionStore] =
    for {
      versionsRef <- Ref.of[IO, Map[String, List[PipelineVersion]]](Map.empty)
      activeRef   <- Ref.of[IO, Map[String, Int]](Map.empty)
    } yield new PipelineVersionStore {

      def recordVersion(
          name: String,
          structuralHash: String,
          source: Option[String]
      ): IO[PipelineVersion] =
        for {
          now <- IO.realTimeInstant
          pv <- versionsRef.modify { versions =>
            val existing  = versions.getOrElse(name, Nil)
            val nextVer   = existing.headOption.map(_.version + 1).getOrElse(1)
            val newEntry  = PipelineVersion(nextVer, structuralHash, now, source)
            val updated   = newEntry :: existing
            (versions.updated(name, updated), newEntry)
          }
          _ <- activeRef.update(_.updated(name, pv.version))
        } yield pv

      def listVersions(name: String): IO[List[PipelineVersion]] =
        versionsRef.get.map(_.getOrElse(name, Nil))

      def activeVersion(name: String): IO[Option[Int]] =
        activeRef.get.map(_.get(name))

      def setActiveVersion(name: String, version: Int): IO[Boolean] =
        versionsRef.get.flatMap { versions =>
          val exists = versions.getOrElse(name, Nil).exists(_.version == version)
          if exists then activeRef.update(_.updated(name, version)).as(true)
          else IO.pure(false)
        }

      def getVersion(name: String, version: Int): IO[Option[PipelineVersion]] =
        versionsRef.get.map(_.getOrElse(name, Nil).find(_.version == version))

      def previousVersion(name: String): IO[Option[PipelineVersion]] =
        for {
          activeOpt <- activeVersion(name)
          versions  <- listVersions(name)
        } yield activeOpt.flatMap { active =>
          versions.find(_.version == active - 1)
        }
    }
}
