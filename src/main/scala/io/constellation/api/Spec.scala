package io.constellation.api

import io.circe.Json

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

case class DagSpec(
  metadata: ComponentMetadata,
  modules: Map[UUID, ModuleNodeSpec],
  data: Map[UUID, DataNodeSpec],
  inEdges: Set[(UUID, UUID)], // data node -> module node
  outEdges: Set[(UUID, UUID)], // module node -> data node
) {

  def name: String = metadata.name

  def description: String = metadata.description

  def tags: List[String] = metadata.tags

  def majorVersion: Int = metadata.majorVersion

  def minorVersion: Int = metadata.minorVersion

  def topLevelDataNodes: Map[UUID, DataNodeSpec] = {
    val topLevelUuids = inEdges.map(_._1).diff(outEdges.map(_._2))
    data.filter { case (dataUuid, _) => topLevelUuids.contains(dataUuid) }
  }
}

object DagSpec {

  def empty(name: String): DagSpec = DagSpec(
    metadata = ComponentMetadata.empty(name),
    modules = Map.empty,
    data = Map.empty,
    inEdges = Set.empty,
    outEdges = Set.empty,
  )
}

final case class DataNodeSpec(name: String, nicknames: Map[UUID, String], cType: CType)

case class ModuleNodeSpec(
  metadata: ComponentMetadata,
  consumes: Map[String, CType] = Map.empty,
  produces: Map[String, CType] = Map.empty,
  config: ModuleConfig = ModuleConfig.default,
  definitionContext: Option[Map[String, Json]] = None,
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
    ModuleConfig(inputsTimeout = FiniteDuration(6, "seconds"), moduleTimeout = FiniteDuration(3, "seconds"))
}

final case class ComponentMetadata(
  name: String,
  description: String,
  tags: List[String],
  majorVersion: Int,
  minorVersion: Int,
)

object ComponentMetadata {

  def empty(name: String): ComponentMetadata =
    ComponentMetadata(name, "", List.empty, 0, 1)
}

sealed trait DagChange

object DagChange {

  final case class AddModule(module: ModuleNodeSpec) extends DagChange
  final case class RemoveModule(moduleId: UUID) extends DagChange
  final case class ConnectData(source: UUID, target: UUID)
}
