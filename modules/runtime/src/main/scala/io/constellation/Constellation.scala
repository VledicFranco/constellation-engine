package io.constellation

import cats.effect.IO
import io.constellation.execution.CancellableExecution

trait Constellation {

  def getModules: IO[List[ModuleNodeSpec]]

  def getModuleByName(name: String): IO[Option[Module.Uninitialized]]

  def setModule(module: Module.Uninitialized): IO[Unit]

  def dagExists(name: String): IO[Boolean]

  def createDag(name: String): IO[Option[DagSpec]]

  def setDag(name: String, spec: DagSpec): IO[Unit]

  def listDags: IO[Map[String, ComponentMetadata]]

  def getDag(name: String): IO[Option[DagSpec]]

  def runDag(name: String, inputs: Map[String, CValue]): IO[Runtime.State]

  /** Run a DAG directly without storing it */
  def runDagSpec(dagSpec: DagSpec, inputs: Map[String, CValue]): IO[Runtime.State]

  /** Run a DAG with pre-resolved modules (from compilation) */
  def runDagWithModules(
      dagSpec: DagSpec,
      inputs: Map[String, CValue],
      modules: Map[java.util.UUID, Module.Uninitialized]
  ): IO[Runtime.State]

  /** Run a DAG with pre-resolved modules and priority scheduling.
    *
    * @param dagSpec The DAG specification
    * @param inputs Input data
    * @param modules Module implementations keyed by UUID
    * @param modulePriorities Priority values per module UUID (0-100, higher = more important)
    * @return Execution state
    */
  def runDagWithModulesAndPriorities(
      dagSpec: DagSpec,
      inputs: Map[String, CValue],
      modules: Map[java.util.UUID, Module.Uninitialized],
      modulePriorities: Map[java.util.UUID, Int]
  ): IO[Runtime.State]

  /** Run a named DAG with cancellation support.
    *
    * Returns a `CancellableExecution` handle immediately. The caller can
    * await results via `handle.result` or cancel via `handle.cancel`.
    *
    * Default implementation delegates to `runDag` wrapped in a completed handle.
    */
  def runDagCancellable(name: String, inputs: Map[String, CValue]): IO[CancellableExecution] =
    runDag(name, inputs).flatMap { state =>
      CancellableExecution.completed(java.util.UUID.randomUUID(), state)
    }
}
