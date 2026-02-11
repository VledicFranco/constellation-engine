package io.constellation

import cats.effect.IO

/** Core API for the Constellation pipeline orchestration engine.
  *
  * Provides methods for registering modules and executing pipelines. This is the primary interface
  * that embedders interact with to compile and run constellation-lang pipelines.
  *
  * {{{
  * for {
  *   constellation <- ConstellationImpl.init
  *   _             <- constellation.setModule(myModule)
  *   // Use LoadedPipeline + run
  *   result        <- constellation.run(LoadedPipeline, inputs)
  *   // Or use PipelineStore ref + run
  *   _             <- constellation.PipelineStore.store(image)
  *   _             <- constellation.PipelineStore.alias("pipeline", image.structuralHash)
  *   result        <- constellation.run("pipeline", inputs)
  * } yield result
  * }}}
  *
  * @see
  *   [[io.constellation.impl.ConstellationImpl]] for the default implementation
  * @see
  *   [[io.constellation.Module]] for module definitions
  * @see
  *   [[io.constellation.DagSpec]] for DAG specifications
  */
trait Constellation {

  /** List all registered module specifications. */
  def getModules: IO[List[ModuleNodeSpec]]

  /** Look up a registered module by name.
    *
    * @param name
    *   The module name (case-sensitive, must match `ModuleBuilder.metadata` name)
    * @return
    *   `Some(module)` if found, `None` otherwise
    */
  def getModuleByName(name: String): IO[Option[Module.Uninitialized]]

  /** Register a module for use in DAG execution.
    *
    * @param module
    *   The uninitialized module to register (name taken from its metadata)
    */
  def setModule(module: Module.Uninitialized): IO[Unit]

  /** Remove a module by name.
    *
    * Default no-op for backwards compatibility. Implementations that support dynamic
    * registration/deregistration should override this.
    */
  def removeModule(name: String): IO[Unit] = IO.unit

  // ---------------------------------------------------------------------------
  // New API (Phase 1)
  // ---------------------------------------------------------------------------

  /** Access the pipeline store for managing compiled pipeline images. */
  def PipelineStore: PipelineStore

  /** Execute a loaded pipeline with the given inputs.
    *
    * @param loaded
    *   A LoadedPipeline (from compilation or rehydration)
    * @param inputs
    *   Input values keyed by variable name
    * @param options
    *   Execution options controlling metadata collection
    * @return
    *   A DataSignature describing the execution outcome
    */
  def run(
      loaded: LoadedPipeline,
      inputs: Map[String, CValue],
      options: ExecutionOptions = ExecutionOptions()
  ): IO[DataSignature]

  /** Execute a pipeline by reference (alias name or "sha256:<hash>").
    *
    * Resolves the reference via the PipelineStore, rehydrates the LoadedPipeline, and delegates to
    * `run(loaded, inputs, options)`.
    *
    * @param ref
    *   A pipeline alias name or "sha256:<hash>" structural hash
    * @param inputs
    *   Input values keyed by variable name
    * @param options
    *   Execution options controlling metadata collection
    * @return
    *   A DataSignature describing the execution outcome
    */
  def run(
      ref: String,
      inputs: Map[String, CValue],
      options: ExecutionOptions
  ): IO[DataSignature]

  // ---------------------------------------------------------------------------
  // Suspension Store API (Phase 4)
  // ---------------------------------------------------------------------------

  /** Access the optional suspension store.
    *
    * Returns `None` if no SuspensionStore was configured via the builder.
    */
  def suspensionStore: Option[SuspensionStore] = None

  /** Resume a suspended execution from the SuspensionStore.
    *
    * Loads the suspended execution by handle, merges additional inputs and resolved nodes, and
    * re-executes the pipeline.
    *
    * @param handle
    *   Handle returned by `SuspensionStore.save`
    * @param additionalInputs
    *   New input values to provide
    * @param resolvedNodes
    *   Manually-resolved data node values
    * @param options
    *   Execution options controlling metadata collection
    * @return
    *   A DataSignature describing the resumed execution outcome
    * @throws IllegalStateException
    *   if no SuspensionStore is configured
    * @throws NoSuchElementException
    *   if the handle is not found
    */
  def resumeFromStore(
      handle: SuspensionHandle,
      additionalInputs: Map[String, CValue] = Map.empty,
      resolvedNodes: Map[String, CValue] = Map.empty,
      options: ExecutionOptions = ExecutionOptions()
  ): IO[DataSignature]

}
