package io.constellation.impl

import cats.effect.{IO, Ref}
import io.constellation.{DagSpec, Module, ModuleNodeSpec, ModuleRegistry}

import java.util.UUID

class ModuleRegistryImpl(refMap: Ref[IO, Map[String, Module.Uninitialized]]) extends ModuleRegistry {

  override def listModules: IO[List[ModuleNodeSpec]] = for {
    map <- refMap.get
    result = map.values.map(_.spec).toList
  } yield result

  override def register(name: String, node: Module.Uninitialized): IO[Unit] = refMap.update(_ + (name -> node))

  override def initModules(dagSpec: DagSpec): IO[Map[UUID, Module.Uninitialized]] = {
    for {
      store <- refMap.get
      loaded = dagSpec.modules.toList
        .map { case (uuid, spec) => store.get(spec.name).map(uuid -> _) }
        .collect { case Some(f) => f }
        .toMap
    } yield loaded
  }
}

object ModuleRegistryImpl {

  def init: IO[ModuleRegistry] = {
    for {
      ref <- Ref.of[IO, Map[String, Module.Uninitialized]](Map.empty)
    } yield new ModuleRegistryImpl(ref)
  }
}
