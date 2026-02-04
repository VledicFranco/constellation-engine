package io.constellation

import java.util.UUID

/** A program that is ready to execute.
  *
  * Combines an immutable [[PipelineImage]] with the runtime module instances (synthetic modules like
  * branch modules) needed for execution.
  *
  * @param image
  *   The immutable program snapshot
  * @param syntheticModules
  *   Module implementations keyed by node UUID
  */
final case class LoadedPipeline(
    image: PipelineImage,
    syntheticModules: Map[UUID, Module.Uninitialized]
) {

  /** Structural hash of the underlying DAG (convenience delegate). */
  def structuralHash: String = image.structuralHash
}
