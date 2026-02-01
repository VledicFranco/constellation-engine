package io.constellation

import cats.effect.IO

@deprecated("Use ProgramStore for program image management", "0.3.0")
trait DagRegistry {

  def list: IO[Map[String, ComponentMetadata]]

  def exists(name: String): IO[Boolean]

  def register(name: String, spec: DagSpec): IO[Unit]

  def retrieve(name: String, version: Option[String]): IO[Option[DagSpec]]
}
