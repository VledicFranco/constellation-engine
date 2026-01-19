package io.constellation

import cats.effect.IO

import java.util.UUID

trait ModuleRegistry {

  def listModules: IO[List[ModuleNodeSpec]]

  def register(name: String, node: Module.Uninitialized): IO[Unit]

  def get(name: String): IO[Option[Module.Uninitialized]]

  def initModules(spec: DagSpec): IO[Map[UUID, Module.Uninitialized]]
}
