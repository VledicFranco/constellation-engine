package io.constellation

import java.security.MessageDigest
import java.util.UUID

/** Utilities for computing deterministic content hashes of DAG specifications.
  *
  * Used by [[ProgramImage]] to derive structural and syntactic hashes that
  * enable deduplication, caching, and change detection.
  */
object ContentHash {

  /** Compute a SHA-256 hex digest of the given bytes. */
  def computeSHA256(bytes: Array[Byte]): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(bytes)
    hash.map("%02x".format(_)).mkString
  }

  /** Produce a deterministic canonical string representation of a [[DagSpec]].
    *
    * The canonical form sorts all maps by key and replaces UUIDs with
    * positional indices so that two structurally equivalent DAGs produce
    * the same string regardless of UUID generation order.
    */
  def canonicalizeDagSpec(dag: DagSpec): String = {
    // Collect all UUIDs in a deterministic, structure-based order.
    // Sort modules by name (not UUID), then data nodes by name (not UUID),
    // so that structurally equivalent DAGs with different UUIDs get the same indices.
    val moduleUuids: List[UUID] =
      dag.modules.toList.sortBy(_._2.name).map(_._1)
    val dataUuids: List[UUID] =
      dag.data.toList.sortBy { case (_, spec) => (spec.name, spec.cType.toString) }.map(_._1)
    val allUuids: List[UUID] = (moduleUuids ++ dataUuids).distinct

    val uuidIndex: Map[UUID, Int] = allUuids.zipWithIndex.toMap

    def idx(uuid: UUID): String = uuidIndex.get(uuid).map(i => s"#$i").getOrElse(uuid.toString)

    val sb = new StringBuilder
    sb.append(s"dag:${dag.metadata.name}\n")

    // Modules sorted by name (structure-based, not UUID-based)
    dag.modules.toList.sortBy(_._2.name).foreach { case (uuid, spec) =>
      sb.append(s"  module[${idx(uuid)}]:${spec.name}\n")
      spec.consumes.toList.sortBy(_._1).foreach { case (name, ctype) =>
        sb.append(s"    consumes:$name=$ctype\n")
      }
      spec.produces.toList.sortBy(_._1).foreach { case (name, ctype) =>
        sb.append(s"    produces:$name=$ctype\n")
      }
    }

    // Data nodes sorted by name (structure-based, not UUID-based)
    dag.data.toList.sortBy { case (_, spec) => (spec.name, spec.cType.toString) }.foreach { case (uuid, spec) =>
      sb.append(s"  data[${idx(uuid)}]:${spec.name}=${spec.cType}\n")
      spec.nicknames.toList.sortBy(_._1.toString).foreach { case (moduleUuid, nick) =>
        sb.append(s"    nickname:${idx(moduleUuid)}=$nick\n")
      }
      spec.inlineTransform.foreach { t =>
        sb.append(s"    transform:${t.getClass.getSimpleName}\n")
      }
      spec.transformInputs.toList.sortBy(_._1).foreach { case (name, inputUuid) =>
        sb.append(s"    transformInput:$name=${idx(inputUuid)}\n")
      }
    }

    // Edges sorted
    dag.inEdges.toList.map { case (from, to) => (idx(from), idx(to)) }.sorted.foreach {
      case (from, to) => sb.append(s"  inEdge:$from->$to\n")
    }
    dag.outEdges.toList.map { case (from, to) => (idx(from), idx(to)) }.sorted.foreach {
      case (from, to) => sb.append(s"  outEdge:$from->$to\n")
    }

    // Declared outputs
    dag.declaredOutputs.sorted.foreach { name =>
      sb.append(s"  output:$name\n")
    }

    // Output bindings sorted
    dag.outputBindings.toList.sortBy(_._1).foreach { case (name, uuid) =>
      sb.append(s"  outputBinding:$name=${idx(uuid)}\n")
    }

    sb.toString()
  }

  /** Compute a structural hash of a [[DagSpec]].
    *
    * Two DAGs that are structurally equivalent (same topology, same module
    * names, same types) will produce the same hash even if their UUIDs differ.
    */
  def computeStructuralHash(dag: DagSpec): String = {
    val canonical = canonicalizeDagSpec(dag)
    computeSHA256(canonical.getBytes("UTF-8"))
  }
}
