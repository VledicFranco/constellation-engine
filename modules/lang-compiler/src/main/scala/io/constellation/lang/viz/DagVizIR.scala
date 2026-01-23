package io.constellation.lang.viz

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.*

/** Node kinds for DAG visualization */
enum NodeKind:
  case Input         // External data entering the DAG
  case Output        // Declared output of the DAG
  case Operation     // Module call / function invocation
  case Literal       // Literal value (string, int, etc.)
  case Merge         // Record merge operation (+)
  case Project       // Field projection ([field1, field2])
  case FieldAccess   // Single field access (.field)
  case Conditional   // If/else expression
  case Guard         // Guard expression (expr when cond)
  case Branch        // Multi-way branch expression
  case Coalesce      // Null coalescing (??)
  case HigherOrder   // Higher-order operations (map, filter, etc.)
  case ListLiteral   // List literal expression
  case BooleanOp     // AND, OR, NOT operations
  case StringInterp  // String interpolation

object NodeKind:
  given Encoder[NodeKind] = Encoder.encodeString.contramap(_.toString)
  given Decoder[NodeKind] = Decoder.decodeString.emap { s =>
    NodeKind.values.find(_.toString == s).toRight(s"Unknown NodeKind: $s")
  }

/** Edge kinds for DAG visualization */
enum EdgeKind:
  case Data     // Normal data flow
  case Optional // Optional data flow (from guard expressions)
  case Control  // Control flow (conditions)

object EdgeKind:
  given Encoder[EdgeKind] = Encoder.encodeString.contramap(_.toString)
  given Decoder[EdgeKind] = Decoder.decodeString.emap { s =>
    EdgeKind.values.find(_.toString == s).toRight(s"Unknown EdgeKind: $s")
  }

/** Execution status for runtime visualization */
enum ExecutionStatus:
  case Pending   // Not yet executed
  case Running   // Currently executing
  case Completed // Successfully completed
  case Failed    // Execution failed

object ExecutionStatus:
  given Encoder[ExecutionStatus] = Encoder.encodeString.contramap(_.toString)
  given Decoder[ExecutionStatus] = Decoder.decodeString.emap { s =>
    ExecutionStatus.values.find(_.toString == s).toRight(s"Unknown ExecutionStatus: $s")
  }

/** Position in 2D space for layout */
case class Position(x: Double, y: Double)

object Position:
  given Encoder[Position] = deriveEncoder
  given Decoder[Position] = deriveDecoder

/** Execution state attached to nodes after execution */
case class ExecutionState(
    status: ExecutionStatus,
    value: Option[Json] = None,
    durationMs: Option[Long] = None,
    error: Option[String] = None
)

object ExecutionState:
  given Encoder[ExecutionState] = deriveEncoder
  given Decoder[ExecutionState] = deriveDecoder

/** A node in the visualization DAG */
case class VizNode(
    id: String,
    kind: NodeKind,
    label: String,                            // Display name (e.g., "FetchCustomer", "order")
    typeSignature: String,                    // Human-readable type (e.g., "{ id: String, name: String }")
    position: Option[Position] = None,        // Computed by layout engine
    executionState: Option[ExecutionState] = None
)

object VizNode:
  given Encoder[VizNode] = deriveEncoder
  given Decoder[VizNode] = deriveDecoder

/** An edge connecting two nodes */
case class VizEdge(
    id: String,
    source: String, // Source node ID
    target: String, // Target node ID
    label: Option[String] = None, // Parameter name for module inputs
    kind: EdgeKind = EdgeKind.Data
)

object VizEdge:
  given Encoder[VizEdge] = deriveEncoder
  given Decoder[VizEdge] = deriveDecoder

/** A group of nodes (for collapsible sections) */
case class NodeGroup(
    id: String,
    label: String,
    nodeIds: List[String],
    collapsed: Boolean = false
)

object NodeGroup:
  given Encoder[NodeGroup] = deriveEncoder
  given Decoder[NodeGroup] = deriveDecoder

/** Bounding box for the visualization */
case class Bounds(minX: Double, minY: Double, maxX: Double, maxY: Double)

object Bounds:
  given Encoder[Bounds] = deriveEncoder
  given Decoder[Bounds] = deriveDecoder

/** Metadata about the visualization */
case class VizMetadata(
    title: Option[String] = None,
    layoutDirection: String = "TB", // "TB" (top-bottom) or "LR" (left-right)
    bounds: Option[Bounds] = None
)

object VizMetadata:
  given Encoder[VizMetadata] = deriveEncoder
  given Decoder[VizMetadata] = deriveDecoder

/** The complete DAG visualization intermediate representation */
case class DagVizIR(
    nodes: List[VizNode],
    edges: List[VizEdge],
    groups: List[NodeGroup] = List.empty,
    metadata: VizMetadata = VizMetadata()
)

object DagVizIR:
  given Encoder[DagVizIR] = deriveEncoder
  given Decoder[DagVizIR] = deriveDecoder

  /** Create an empty DAG */
  val empty: DagVizIR = DagVizIR(List.empty, List.empty)
