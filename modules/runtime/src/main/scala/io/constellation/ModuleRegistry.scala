package io.constellation

import cats.effect.IO

import java.util.UUID

trait ModuleRegistry {

  def listModules: IO[List[ModuleNodeSpec]]

  def register(name: String, node: Module.Uninitialized): IO[Unit]

  def initModules(spec: DagSpec): IO[Map[UUID, Module.Uninitialized]]
}
