package io.constellation

import java.util.UUID

/** A program that is ready to execute.
  *
  * Combines an immutable [[ProgramImage]] with the runtime module instances (synthetic modules like
  * branch modules) needed for execution.
  *
  * @param image
  *   The immutable program snapshot
  * @param syntheticModules
  *   Module implementations keyed by node UUID
  */
final case class LoadedProgram(
    image: ProgramImage,
    syntheticModules: Map[UUID, Module.Uninitialized]
) {

  /** Structural hash of the underlying DAG (convenience delegate). */
  def structuralHash: String = image.structuralHash
}
