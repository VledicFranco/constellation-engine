package io.constellation.impl

import cats.effect.{IO, Ref}
import io.constellation.{ComponentMetadata, DagRegistry, DagSpec}

class DagRegistryImpl(dagsRef: Ref[IO, Map[String, DagSpec]]) extends DagRegistry {

  override def list: IO[Map[String, ComponentMetadata]] =
    for {
      map <- dagsRef.get
    } yield map.values.toList.map(dag => dag.metadata.name -> dag.metadata).toMap

  override def exists(name: String): IO[Boolean] =
    dagsRef.get.map(_.contains(name))

  override def register(name: String, spec: DagSpec): IO[Unit] =
    dagsRef.update(_.updated(name, spec))

  /** Retrieve dag with version, or latest published version. */
  override def retrieve(name: String, version: Option[String]): IO[Option[DagSpec]] =
    dagsRef.get.map(_.get(name))
}

object DagRegistryImpl {

  def init: IO[DagRegistry] = {
    for {
      dagsRef <- Ref.of[IO, Map[String, DagSpec]](Map.empty)
    } yield new DagRegistryImpl(dagsRef)
  }
}
