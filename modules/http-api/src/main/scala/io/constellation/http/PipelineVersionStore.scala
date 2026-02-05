package io.constellation.http

import java.time.Instant

import cats.effect.{IO, Ref}
import cats.implicits.*

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
  def recordVersion(
      name: String,
      structuralHash: String,
      source: Option[String]
  ): IO[PipelineVersion]

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

  /** Create a new in-memory version store backed by `Ref` with no version limit. */
  def init: IO[PipelineVersionStore] = initWithLimit(None)

  /** Create a new in-memory version store backed by `Ref`.
    *
    * @param maxVersionsPerPipeline
    *   Optional maximum number of versions to retain per pipeline. When exceeded, the oldest
    *   non-active versions are pruned. None means unlimited.
    */
  def initWithLimit(maxVersionsPerPipeline: Option[Int]): IO[PipelineVersionStore] =
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
            val existing = versions.getOrElse(name, Nil)
            val nextVer  = existing.headOption.map(_.version + 1).getOrElse(1)
            val newEntry = PipelineVersion(nextVer, structuralHash, now, source)
            val updated  = newEntry :: existing
            (versions.updated(name, updated), newEntry)
          }
          _ <- activeRef.update(_.updated(name, pv.version))
          _ <- pruneVersions(name)
        } yield pv

      /** Prune oldest non-active versions if the count exceeds maxVersionsPerPipeline. */
      private def pruneVersions(name: String): IO[Unit] = maxVersionsPerPipeline match {
        case None => IO.unit
        case Some(max) =>
          activeRef.get.flatMap { activeMap =>
            val activeVer = activeMap.get(name)
            versionsRef.update { versions =>
              versions.get(name) match {
                case None                                         => versions
                case Some(versionList) if versionList.size <= max => versions
                case Some(versionList)                            =>
                  // Keep the newest `max` versions, but always retain the active version
                  val (keep, candidates) = versionList.splitAt(max)
                  val mustKeep           = candidates.filter(v => activeVer.contains(v.version))
                  versions.updated(name, keep ++ mustKeep)
              }
            }
          }
      }

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
