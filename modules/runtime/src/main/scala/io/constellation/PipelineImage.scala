package io.constellation

import java.time.Instant
import java.util.UUID

/** Immutable, serializable snapshot of a compiled pipeline.
  *
  * A PipelineImage captures everything needed to reconstruct a runnable program: the DAG topology,
  * module call options, and content hashes for deduplication.
  *
  * @param structuralHash
  *   SHA-256 of the canonicalized DagSpec (UUID-independent)
  * @param syntacticHash
  *   SHA-256 of the source text (empty if source unavailable)
  * @param dagSpec
  *   The compiled DAG specification
  * @param moduleOptions
  *   Per-module runtime options (retry, timeout, etc.)
  * @param compiledAt
  *   Timestamp when the image was created
  * @param sourceHash
  *   Optional hash of the original source code
  */
final case class PipelineImage(
    structuralHash: String,
    syntacticHash: String,
    dagSpec: DagSpec,
    moduleOptions: Map[UUID, ModuleCallOptions],
    compiledAt: Instant,
    sourceHash: Option[String] = None
)

object PipelineImage {

  /** Compute the structural hash of a DagSpec. */
  def computeStructuralHash(dagSpec: DagSpec): String =
    ContentHash.computeStructuralHash(dagSpec)

  /** Rehydrate a PipelineImage into a LoadedPipeline by reconstructing synthetic modules.
    *
    * Only branch modules can be reconstructed from the DagSpec alone. HOF transforms (filter, map,
    * etc.) contain closures that cannot be reconstructed; those programs require the original
    * LoadedPipeline or recompilation.
    */
  def rehydrate(image: PipelineImage): LoadedPipeline = {
    val syntheticModules = SyntheticModuleFactory.fromDagSpec(image.dagSpec)
    LoadedPipeline(image, syntheticModules)
  }
}
