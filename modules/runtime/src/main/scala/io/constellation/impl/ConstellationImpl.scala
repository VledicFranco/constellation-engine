package io.constellation.impl

import cats.effect.IO
import io.constellation.*

final class ConstellationImpl(moduleRegistry: ModuleRegistry, dagRegistry: DagRegistry)
    extends Constellation {

  def getModules: IO[List[ModuleNodeSpec]] =
    moduleRegistry.listModules

  def getModuleByName(name: String): IO[Option[Module.Uninitialized]] =
    moduleRegistry.get(name)

  def setModule(factory: Module.Uninitialized): IO[Unit] =
    moduleRegistry.register(factory.spec.name, factory)

  def dagExists(name: String): IO[Boolean] =
    dagRegistry.exists(name)

  def createDag(name: String): IO[Option[DagSpec]] =
    for {
      exists <- dagRegistry.exists(name)
      result <-
        if exists then {
          IO.pure(None)
        } else {
          val dagSpec = DagSpec.empty(name)
          dagRegistry.register(name, dagSpec).as(Some(dagSpec))
        }
    } yield result

  def setDag(name: String, spec: DagSpec): IO[Unit] =
    dagRegistry.register(name, spec)

  def listDags: IO[Map[String, ComponentMetadata]] =
    dagRegistry.list

  def getDag(name: String): IO[Option[DagSpec]] =
    dagRegistry.retrieve(name, None)

  def runDag(name: String, inputs: Map[String, CValue]): IO[Runtime.State] =
    for {
      dag <- dagRegistry.retrieve(name, None)
      result <- dag match {
        case Some(dagSpec) =>
          for {
            modules <- moduleRegistry.initModules(dagSpec)
            context <- Runtime.run(dagSpec, inputs, modules)
          } yield context
        case None => IO.raiseError(new Exception(s"DAG $name not found"))
      }
    } yield result

  def runDagSpec(dagSpec: DagSpec, inputs: Map[String, CValue]): IO[Runtime.State] =
    for {
      modules <- moduleRegistry.initModules(dagSpec)
      context <- Runtime.run(dagSpec, inputs, modules)
    } yield context

  def runDagWithModules(
      dagSpec: DagSpec,
      inputs: Map[String, CValue],
      modules: Map[java.util.UUID, Module.Uninitialized]
  ): IO[Runtime.State] =
    Runtime.run(dagSpec, inputs, modules)
}

object ConstellationImpl {

  def init: IO[ConstellationImpl] =
    for {
      moduleRegistry <- ModuleRegistryImpl.init
      dagRegistry    <- DagRegistryImpl.init
    } yield new ConstellationImpl(moduleRegistry = moduleRegistry, dagRegistry = dagRegistry)
}
