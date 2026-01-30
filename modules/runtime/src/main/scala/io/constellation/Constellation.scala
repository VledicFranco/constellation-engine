package io.constellation

import cats.effect.IO
import io.constellation.execution.CancellableExecution

/** Core API for the Constellation pipeline orchestration engine.
  *
  * Provides methods for registering modules, managing DAG specifications,
  * and executing pipelines. This is the primary interface that embedders
  * interact with to compile and run constellation-lang pipelines.
  *
  * ==Usage==
  *
  * {{{
  * for {
  *   constellation <- ConstellationImpl.init
  *   _             <- constellation.setModule(myModule)
  *   _             <- constellation.setDag("pipeline", compiledDagSpec)
  *   result        <- constellation.runDag("pipeline", Map("input" -> CValue.CString("hello")))
  * } yield result
  * }}}
  *
  * @see [[io.constellation.impl.ConstellationImpl]] for the default implementation
  * @see [[io.constellation.Module]] for module definitions
  * @see [[io.constellation.DagSpec]] for DAG specifications
  */
trait Constellation {

  /** List all registered module specifications. */
  def getModules: IO[List[ModuleNodeSpec]]

  /** Look up a registered module by name.
    *
    * @param name The module name (case-sensitive, must match `ModuleBuilder.metadata` name)
    * @return `Some(module)` if found, `None` otherwise
    */
  def getModuleByName(name: String): IO[Option[Module.Uninitialized]]

  /** Register a module for use in DAG execution.
    *
    * @param module The uninitialized module to register (name taken from its metadata)
    */
  def setModule(module: Module.Uninitialized): IO[Unit]

  /** Check whether a DAG with the given name exists in the registry.
    *
    * @param name The DAG name to check
    * @return `true` if a DAG with that name is registered
    */
  def dagExists(name: String): IO[Boolean]

  /** Create a new empty DAG with the given name.
    *
    * @param name The DAG name
    * @return `Some(dagSpec)` if created, `None` if a DAG with that name already exists
    */
  def createDag(name: String): IO[Option[DagSpec]]

  /** Register or replace a DAG specification.
    *
    * @param name The DAG name
    * @param spec The DAG specification to store
    */
  def setDag(name: String, spec: DagSpec): IO[Unit]

  /** List all registered DAGs with their metadata. */
  def listDags: IO[Map[String, ComponentMetadata]]

  /** Retrieve a DAG specification by name.
    *
    * @param name The DAG name
    * @return `Some(dagSpec)` if found, `None` otherwise
    */
  def getDag(name: String): IO[Option[DagSpec]]

  /** Execute a named DAG with the given inputs.
    *
    * Resolves modules from the registry and runs the full pipeline.
    *
    * @param name The name of a registered DAG
    * @param inputs Input values keyed by variable name
    * @return The final execution state containing all computed values
    */
  def runDag(name: String, inputs: Map[String, CValue]): IO[Runtime.State]

  /** Run a DAG specification directly without storing it in the registry.
    *
    * @param dagSpec The DAG specification to execute
    * @param inputs Input values keyed by variable name
    * @return The final execution state
    */
  def runDagSpec(dagSpec: DagSpec, inputs: Map[String, CValue]): IO[Runtime.State]

  /** Run a DAG with pre-resolved modules (typically from compilation).
    *
    * @param dagSpec The DAG specification
    * @param inputs Input values keyed by variable name
    * @param modules Module implementations keyed by node UUID
    * @return The final execution state
    */
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
