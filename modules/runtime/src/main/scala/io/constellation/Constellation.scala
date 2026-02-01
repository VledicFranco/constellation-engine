package io.constellation

import cats.effect.IO
import io.constellation.execution.CancellableExecution

/** Core API for the Constellation pipeline orchestration engine.
  *
  * Provides methods for registering modules, managing DAG specifications,
  * and executing pipelines. This is the primary interface that embedders
  * interact with to compile and run constellation-lang pipelines.
  *
  * ==New API (0.3.0+)==
  *
  * {{{
  * for {
  *   constellation <- ConstellationImpl.init
  *   _             <- constellation.setModule(myModule)
  *   // Use LoadedProgram + run
  *   result        <- constellation.run(loadedProgram, inputs)
  *   // Or use ProgramStore ref + run
  *   _             <- constellation.programStore.store(image)
  *   _             <- constellation.programStore.alias("pipeline", image.structuralHash)
  *   result        <- constellation.run("pipeline", inputs)
  * } yield result
  * }}}
  *
  * ==Legacy API (deprecated)==
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

  // ---------------------------------------------------------------------------
  // New API (Phase 1)
  // ---------------------------------------------------------------------------

  /** Access the program store for managing compiled program images. */
  def programStore: ProgramStore

  /** Execute a loaded program with the given inputs.
    *
    * @param loaded  A LoadedProgram (from compilation or rehydration)
    * @param inputs  Input values keyed by variable name
    * @param options Execution options controlling metadata collection
    * @return A DataSignature describing the execution outcome
    */
  def run(
      loaded: LoadedProgram,
      inputs: Map[String, CValue],
      options: ExecutionOptions = ExecutionOptions()
  ): IO[DataSignature]

  /** Execute a program by reference (alias name or "sha256:<hash>").
    *
    * Resolves the reference via the ProgramStore, rehydrates the LoadedProgram,
    * and delegates to `run(loaded, inputs, options)`.
    *
    * @param ref     A program alias name or "sha256:<hash>" structural hash
    * @param inputs  Input values keyed by variable name
    * @param options Execution options controlling metadata collection
    * @return A DataSignature describing the execution outcome
    */
  def run(
      ref: String,
      inputs: Map[String, CValue],
      options: ExecutionOptions
  ): IO[DataSignature]

  // ---------------------------------------------------------------------------
  // Legacy API (deprecated, kept for backward compatibility)
  // ---------------------------------------------------------------------------

  /** Check whether a DAG with the given name exists in the registry. */
  @deprecated("Use ProgramStore and Constellation.run", "0.3.0")
  def dagExists(name: String): IO[Boolean]

  /** Create a new empty DAG with the given name. */
  @deprecated("Use ProgramStore and Constellation.run", "0.3.0")
  def createDag(name: String): IO[Option[DagSpec]]

  /** Register or replace a DAG specification. */
  @deprecated("Use ProgramStore and Constellation.run", "0.3.0")
  def setDag(name: String, spec: DagSpec): IO[Unit]

  /** List all registered DAGs with their metadata. */
  @deprecated("Use ProgramStore.listImages", "0.3.0")
  def listDags: IO[Map[String, ComponentMetadata]]

  /** Retrieve a DAG specification by name. */
  @deprecated("Use ProgramStore.getByName", "0.3.0")
  def getDag(name: String): IO[Option[DagSpec]]

  /** Execute a named DAG with the given inputs. */
  @deprecated("Use Constellation.run(ref, inputs, options)", "0.3.0")
  def runDag(name: String, inputs: Map[String, CValue]): IO[Runtime.State]

  /** Run a DAG specification directly without storing it in the registry. */
  @deprecated("Use Constellation.run(LoadedProgram, inputs)", "0.3.0")
  def runDagSpec(dagSpec: DagSpec, inputs: Map[String, CValue]): IO[Runtime.State]

  /** Run a DAG with pre-resolved modules. */
  @deprecated("Use Constellation.run(LoadedProgram, inputs)", "0.3.0")
  def runDagWithModules(
      dagSpec: DagSpec,
      inputs: Map[String, CValue],
      modules: Map[java.util.UUID, Module.Uninitialized]
  ): IO[Runtime.State]

  /** Run a DAG with pre-resolved modules and priority scheduling. */
  @deprecated("Use Constellation.run(LoadedProgram, inputs)", "0.3.0")
  def runDagWithModulesAndPriorities(
      dagSpec: DagSpec,
      inputs: Map[String, CValue],
      modules: Map[java.util.UUID, Module.Uninitialized],
      modulePriorities: Map[java.util.UUID, Int]
  ): IO[Runtime.State]

  /** Run a named DAG with cancellation support. */
  def runDagCancellable(name: String, inputs: Map[String, CValue]): IO[CancellableExecution] =
    runDag(name, inputs).flatMap { state =>
      CancellableExecution.completed(java.util.UUID.randomUUID(), state)
    }
}
