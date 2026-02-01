package io.constellation

import cats.Eval

import java.time.{Duration, Instant}
import java.util.UUID
import scala.concurrent.duration.FiniteDuration

/** Builds [[SignatureMetadata]] from execution state, controlled by [[ExecutionOptions]] flags.
  *
  * Shared by both [[io.constellation.impl.ConstellationImpl]] and [[SuspendableExecution]]
  * to avoid duplicating non-trivial metadata computation logic.
  */
object MetadataBuilder {

  /** Build metadata from a completed or suspended execution.
    *
    * @param state            The runtime execution state
    * @param dagSpec          The DAG specification that was executed
    * @param options          Flags controlling which optional metadata to include
    * @param startedAt        When execution started
    * @param completedAt      When execution finished
    * @param inputNodeNames   Names of nodes that were provided as user inputs
    * @param resolvedNodeNames Names of nodes that were manually resolved during resume
    * @return Populated SignatureMetadata
    */
  def build(
      state: Runtime.State,
      dagSpec: DagSpec,
      options: ExecutionOptions,
      startedAt: Instant,
      completedAt: Instant,
      inputNodeNames: Set[String],
      resolvedNodeNames: Set[String] = Set.empty
  ): SignatureMetadata = {
    val totalDuration = Duration.between(startedAt, completedAt)

    SignatureMetadata(
      startedAt = Some(startedAt),
      completedAt = Some(completedAt),
      totalDuration = Some(totalDuration),
      nodeTimings = if (options.includeTimings) Some(buildNodeTimings(state, dagSpec)) else None,
      provenance = if (options.includeProvenance) Some(buildProvenance(state, dagSpec)) else None,
      blockedGraph = if (options.includeBlockedGraph) Some(buildBlockedGraph(state, dagSpec)) else None,
      resolutionSources =
        if (options.includeResolutionSources)
          Some(buildResolutionSources(state, dagSpec, inputNodeNames, resolvedNodeNames))
        else None
    )
  }

  /** Extract per-module execution timings from Fired statuses.
    *
    * Maps module name -> execution duration for every module that completed successfully.
    */
  private def buildNodeTimings(
      state: Runtime.State,
      dagSpec: DagSpec
  ): Map[String, Duration] = {
    state.moduleStatus.flatMap { case (uuid, evalStatus) =>
      evalStatus.value match {
        case Module.Status.Fired(latency, _) =>
          val moduleName = dagSpec.modules.get(uuid).map(_.name).getOrElse(uuid.toString)
          Some(moduleName -> Duration.ofNanos(latency.toNanos))
        case _ => None
      }
    }
  }

  /** Build provenance map: data node name -> source description.
    *
    * Sources are:
    *   - `"<input>"` for user-provided input nodes
    *   - `"<inline-transform>"` for nodes computed by inline transforms
    *   - module name for nodes produced by module execution
    */
  private def buildProvenance(
      state: Runtime.State,
      dagSpec: DagSpec
  ): Map[String, String] = {
    // Pre-compute lookup: data node UUID -> producing module UUID
    val dataToProducingModule: Map[UUID, UUID] = dagSpec.outEdges.map { case (moduleUuid, dataUuid) =>
      dataUuid -> moduleUuid
    }.toMap

    // Pre-compute input data node UUIDs (top-level nodes without inline transforms)
    val inputDataUuids: Set[UUID] = dagSpec.userInputDataNodes.keySet

    // Pre-compute inline transform data node UUIDs
    val inlineTransformUuids: Set[UUID] = dagSpec.data.collect {
      case (uuid, spec) if spec.inlineTransform.isDefined => uuid
    }.toSet

    // Build provenance for every data node that has a computed value
    state.data.flatMap { case (uuid, _) =>
      dagSpec.data.get(uuid).map { spec =>
        val source =
          if (inputDataUuids.contains(uuid)) "<input>"
          else if (inlineTransformUuids.contains(uuid)) "<inline-transform>"
          else dataToProducingModule.get(uuid) match {
            case Some(moduleUuid) =>
              dagSpec.modules.get(moduleUuid).map(_.name).getOrElse(moduleUuid.toString)
            case None => "<unknown>"
          }
        spec.name -> source
      }
    }
  }

  /** Build blocked graph: missing input name -> list of transitively blocked data node names.
    *
    * Performs a BFS forward walk from missing input data nodes through
    * inEdges (data->module) and outEdges (module->data) to find all
    * transitively blocked nodes.
    */
  private def buildBlockedGraph(
      state: Runtime.State,
      dagSpec: DagSpec
  ): Map[String, List[String]] = {
    val uuidToName: Map[UUID, String] = dagSpec.data.map { case (uuid, spec) => uuid -> spec.name }
    val computedUuids: Set[UUID] = state.data.keySet

    // Pre-compute adjacency: data UUID -> set of module UUIDs that consume it
    val dataToModules: Map[UUID, Set[UUID]] = dagSpec.inEdges
      .groupMap(_._1)(_._2)
      .map { case (k, v) => k -> v }

    // Pre-compute adjacency: module UUID -> set of data UUIDs it produces
    val moduleToData: Map[UUID, Set[UUID]] = dagSpec.outEdges
      .groupMap(_._1)(_._2)
      .map { case (k, v) => k -> v }

    // Find missing input data nodes (top-level, no value computed)
    val missingInputUuids: Set[UUID] = dagSpec.userInputDataNodes.keySet -- computedUuids

    missingInputUuids.flatMap { missingUuid =>
      uuidToName.get(missingUuid).map { missingName =>
        val blocked = bfsForward(missingUuid, computedUuids, dataToModules, moduleToData, dagSpec)
        val blockedNames = blocked.flatMap(uuidToName.get).toList.sorted
        missingName -> blockedNames
      }
    }.toMap
  }

  /** BFS forward from a missing data node to find all transitively blocked data nodes. */
  private def bfsForward(
      startDataUuid: UUID,
      computedUuids: Set[UUID],
      dataToModules: Map[UUID, Set[UUID]],
      moduleToData: Map[UUID, Set[UUID]],
      dagSpec: DagSpec
  ): Set[UUID] = {
    var visited = Set.empty[UUID]    // visited data node UUIDs
    var queue = List(startDataUuid)  // BFS frontier of data node UUIDs

    while (queue.nonEmpty) {
      val current = queue.head
      queue = queue.tail

      if (!visited.contains(current)) {
        visited += current

        // Find modules that consume this data node
        val downstreamModules = dataToModules.getOrElse(current, Set.empty)

        // Find data nodes produced by those modules
        val producedData = downstreamModules.flatMap { moduleUuid =>
          moduleToData.getOrElse(moduleUuid, Set.empty)
        }

        // Add unvisited, uncomputed data nodes to the queue
        val newFrontier = producedData.filterNot(uuid => visited.contains(uuid) || computedUuids.contains(uuid))
        queue = queue ++ newFrontier
      }
    }

    // Remove the starting node itself from the result (it's the missing input, not a blocked node)
    visited - startDataUuid
  }

  /** Classify how each computed data node's value was obtained.
    *
    * @param inputNodeNames    Names of user-provided inputs
    * @param resolvedNodeNames Names of manually-resolved nodes (from resume)
    */
  private def buildResolutionSources(
      state: Runtime.State,
      dagSpec: DagSpec,
      inputNodeNames: Set[String],
      resolvedNodeNames: Set[String]
  ): Map[String, ResolutionSource] = {
    val uuidToName: Map[UUID, String] = dagSpec.data.map { case (uuid, spec) => uuid -> spec.name }

    state.data.flatMap { case (uuid, _) =>
      uuidToName.get(uuid).map { name =>
        val source =
          if (resolvedNodeNames.contains(name)) ResolutionSource.FromManualResolution
          else if (inputNodeNames.contains(name)) ResolutionSource.FromInput
          else ResolutionSource.FromModuleExecution
        name -> source
      }
    }
  }
}
