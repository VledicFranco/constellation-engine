package io.constellation.impl

import cats.effect.{IO, Ref}

import io.constellation.{PipelineImage, PipelineStore}

/** In-memory implementation of [[PipelineStore]].
  *
  * Uses three concurrent Ref maps: one for images, one for aliases, and one for the syntactic
  * index.
  */
class PipelineStoreImpl private (
    images: Ref[IO, Map[String, PipelineImage]],
    aliases: Ref[IO, Map[String, String]],
    syntacticIndex: Ref[IO, Map[(String, String), String]]
) extends PipelineStore {

  def store(image: PipelineImage): IO[String] = {
    val hash = image.structuralHash
    images.update(_.updated(hash, image)).as(hash)
  }

  def alias(name: String, structuralHash: String): IO[Unit] =
    aliases.update(_.updated(name, structuralHash))

  def resolve(name: String): IO[Option[String]] =
    aliases.get.map(_.get(name))

  def listAliases: IO[Map[String, String]] =
    aliases.get

  def get(structuralHash: String): IO[Option[PipelineImage]] =
    images.get.map(_.get(structuralHash))

  def getByName(name: String): IO[Option[PipelineImage]] =
    for {
      optHash <- resolve(name)
      optImage <- optHash match {
        case Some(hash) => get(hash)
        case None       => IO.pure(None)
      }
    } yield optImage

  def indexSyntactic(
      syntacticHash: String,
      registryHash: String,
      structuralHash: String
  ): IO[Unit] =
    syntacticIndex.update(_.updated((syntacticHash, registryHash), structuralHash))

  def lookupSyntactic(syntacticHash: String, registryHash: String): IO[Option[String]] =
    syntacticIndex.get.map(_.get((syntacticHash, registryHash)))

  def listImages: IO[List[PipelineImage]] =
    images.get.map(_.values.toList)

  def remove(structuralHash: String): IO[Boolean] =
    images.modify { map =>
      if map.contains(structuralHash) then (map - structuralHash, true)
      else (map, false)
    }
}

object PipelineStoreImpl {

  /** Create a new empty in-memory PipelineStore. */
  def init: IO[PipelineStore] =
    for {
      images         <- Ref.of[IO, Map[String, PipelineImage]](Map.empty)
      aliases        <- Ref.of[IO, Map[String, String]](Map.empty)
      syntacticIndex <- Ref.of[IO, Map[(String, String), String]](Map.empty)
    } yield new PipelineStoreImpl(images, aliases, syntacticIndex)
}
