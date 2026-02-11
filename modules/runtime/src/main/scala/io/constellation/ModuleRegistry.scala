package io.constellation

import java.util.UUID

import cats.effect.IO

trait ModuleRegistry {

  def listModules: IO[List[ModuleNodeSpec]]

  def register(name: String, node: Module.Uninitialized): IO[Unit]

  def get(name: String): IO[Option[Module.Uninitialized]]

  /** Remove a module by its canonical name.
    *
    * Removes from both the module map and the name index. No-op if the module is not registered.
    */
  def deregister(name: String): IO[Unit]

  def initModules(spec: DagSpec): IO[Map[UUID, Module.Uninitialized]]
}
