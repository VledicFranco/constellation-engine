package io.constellation

import cats.effect.IO

trait Constellation {

  def getModules: IO[List[ModuleNodeSpec]]

  def setModule(module: Module.Uninitialized): IO[Unit]

  def dagExists(name: String): IO[Boolean]

  def createDag(name: String): IO[Option[DagSpec]]

  def setDag(name: String, spec: DagSpec): IO[Unit]

  def listDags: IO[Map[String, ComponentMetadata]]

  def getDag(name: String): IO[Option[DagSpec]]

  def runDag(name: String, inputs: Map[String, CValue]): IO[Runtime.State]

  /** Run a DAG directly without storing it */
  def runDagSpec(dagSpec: DagSpec, inputs: Map[String, CValue]): IO[Runtime.State]
}
