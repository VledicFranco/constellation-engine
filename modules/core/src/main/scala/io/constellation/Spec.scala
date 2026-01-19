package io.constellation

import io.circe.Json

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

case class DagSpec(
    metadata: ComponentMetadata,
    modules: Map[UUID, ModuleNodeSpec],
    data: Map[UUID, DataNodeSpec],
    inEdges: Set[(UUID, UUID)],                   // data node -> module node
    outEdges: Set[(UUID, UUID)],                  // module node -> data node
    declaredOutputs: List[String] = List.empty,   // Explicitly declared output variable names
    outputBindings: Map[String, UUID] = Map.empty // Output name -> data node UUID
) {

  def name: String = metadata.name

  def description: String = metadata.description

  def tags: List[String] = metadata.tags

  def majorVersion: Int = metadata.majorVersion

  def minorVersion: Int = metadata.minorVersion

  /** Data nodes that are inputs to the DAG (not produced by any module) */
  def topLevelDataNodes: Map[UUID, DataNodeSpec] = {
    val producedByModules = outEdges.map(_._2)
    data.filter { case (dataUuid, _) => !producedByModules.contains(dataUuid) }
  }

  /** Data nodes that are outputs of the DAG (not consumed by any module) */
  def bottomLevelDataNodes: Map[UUID, DataNodeSpec] = {
    val consumedByModules = inEdges.map(_._1)
    data.filter { case (dataUuid, _) => !consumedByModules.contains(dataUuid) }
  }
}

object DagSpec {

  def empty(name: String): DagSpec = DagSpec(
    metadata = ComponentMetadata.empty(name),
    modules = Map.empty,
    data = Map.empty,
    inEdges = Set.empty,
    outEdges = Set.empty,
    declaredOutputs = List.empty
  )
}

/** Specification for a data node in a DAG.
  *
  * @param name
  *   The name of the data node
  * @param nicknames
  *   Map from module UUID to the parameter name used by that module
  * @param cType
  *   The type of data this node holds
  * @param inlineTransform
  *   Optional inline transform to compute this node's value from inputs. When present, this data
  *   node computes its value by applying the transform to the values of its input data nodes
  *   (specified in transformInputs). This eliminates the need for a synthetic module node.
  * @param transformInputs
  *   Map from input name (as expected by the transform) to source data node UUID. Only used when
  *   inlineTransform is defined. For example, a MergeTransform expects inputs named "left" and
  *   "right".
  */
final case class DataNodeSpec(
    name: String,
    nicknames: Map[UUID, String],
    cType: CType,
    inlineTransform: Option[InlineTransform] = None,
    transformInputs: Map[String, UUID] = Map.empty
)

case class ModuleNodeSpec(
    metadata: ComponentMetadata,
    consumes: Map[String, CType] = Map.empty,
    produces: Map[String, CType] = Map.empty,
    config: ModuleConfig = ModuleConfig.default,
    definitionContext: Option[Map[String, Json]] = None
) {

  def name: String = metadata.name

  def description: String = metadata.description

  def tags: List[String] = metadata.tags

  def majorVersion: Int = metadata.majorVersion

  def minorVersion: Int = metadata.minorVersion

  def inputsTimeout: FiniteDuration = config.inputsTimeout

  def moduleTimeout: FiniteDuration = config.moduleTimeout
}

object ModuleNodeSpec {

  def empty: ModuleNodeSpec = ModuleNodeSpec(
    metadata = ComponentMetadata.empty("EmptyModule"),
    consumes = Map.empty,
    produces = Map.empty,
    config = ModuleConfig.default,
    definitionContext = None
  )
}

final case class ModuleConfig(inputsTimeout: FiniteDuration, moduleTimeout: FiniteDuration)

object ModuleConfig {

  def default: ModuleConfig =
    ModuleConfig(
      inputsTimeout = FiniteDuration(6, "seconds"),
      moduleTimeout = FiniteDuration(3, "seconds")
    )
}

final case class ComponentMetadata(
    name: String,
    description: String,
    tags: List[String],
    majorVersion: Int,
    minorVersion: Int
)

object ComponentMetadata {

  def empty(name: String): ComponentMetadata =
    ComponentMetadata(name, "", List.empty, 0, 1)
}

sealed trait DagChange

object DagChange {

  final case class AddModule(module: ModuleNodeSpec) extends DagChange
  final case class RemoveModule(moduleId: UUID)      extends DagChange
  final case class ConnectData(source: UUID, target: UUID)
}
