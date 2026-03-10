package io.constellation.dsl

import java.time.Instant
import java.util.UUID

import cats.effect.IO
import cats.implicits.*

import io.constellation.{
  Constellation,
  DagSpec,
  LoadedPipeline,
  Module,
  ModuleCallOptions,
  PipelineImage
}

/** An immutable, executable pipeline built from a [[Pipeline.define]] block.
  *
  * Holds the sealed [[DagSpec]] and any accumulated [[ModuleCallOptions]] from [[TypedRef]] chains.
  * Call [[load]] to obtain a [[LoadedPipeline]] ready to pass to
  * [[io.constellation.Constellation.run]].
  */
final class TypedPipeline private[dsl] (
    val spec: DagSpec,
    val callOptions: Map[UUID, ModuleCallOptions],
    val syntheticModules: Map[UUID, Module.Uninitialized],
    val moduleBuilders: List[Module.Uninitialized]
) {

  /** Immutable snapshot of this pipeline, computed once at construction. */
  val image: PipelineImage = PipelineImage(
    structuralHash = PipelineImage.computeStructuralHash(spec),
    syntacticHash = "",
    dagSpec = spec,
    moduleOptions = callOptions,
    compiledAt = Instant.now()
  )

  /** Wrap the image into a [[LoadedPipeline]] ready for execution.
    *
    * Pure — no IO. Does NOT call [[PipelineImage.rehydrate]].
    */
  def load: LoadedPipeline = LoadedPipeline(image, syntheticModules)

  /** Register each module builder with `constellation` so the runtime can find it by name.
    *
    * Returns `IO.unit` when `moduleBuilders` is empty (Phase 1a baseline).
    */
  def registerModules(constellation: Constellation): IO[Unit] =
    moduleBuilders.traverse_(constellation.setModule)
}

/** Entry point for the Scala pipeline DSL. */
object Pipeline {

  /** Define a named pipeline.
    *
    * Opens a [[PipelineBuilder]] scope, runs the user-provided block, then seals the accumulated
    * state into a [[TypedPipeline]].
    *
    * @param name
    *   Pipeline name — used as [[io.constellation.ComponentMetadata]] identifier.
    * @param f
    *   Builder block — receives a [[PipelineBuilder]] and wires inputs, steps, and outputs.
    */
  def define(name: String)(f: PipelineBuilder => Unit): TypedPipeline = {
    val builder = new PipelineBuilderImpl()
    f(builder)
    new TypedPipeline(
      spec = builder.buildDagSpec(name),
      callOptions = builder.getCallOptions,
      syntheticModules = builder.getSyntheticModules,
      moduleBuilders = builder.getNamedModules
    )
  }
}
