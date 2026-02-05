package io.constellation.cache

import java.security.MessageDigest
import java.util.Base64

import io.constellation.CValue

/** Generates deterministic cache keys from module names and inputs.
  *
  * Cache keys are computed as:
  * {{{
  * key = hash(moduleName + canonicalSerialize(inputs))
  * }}}
  *
  * The canonical serialization ensures that equivalent inputs produce the same key, regardless of
  * map ordering, etc.
  */
object CacheKeyGenerator {

  /** Generate a cache key for a module call.
    *
    * @param moduleName
    *   The name of the module
    * @param inputs
    *   The input values to the module
    * @param version
    *   Optional version to invalidate cache on module changes
    * @return
    *   A deterministic cache key string
    */
  def generateKey(
      moduleName: String,
      inputs: Map[String, CValue],
      version: Option[String] = None
  ): String = {
    val canonical = canonicalString(moduleName, inputs, version)
    hashString(canonical)
  }

  /** Generate a short key (truncated hash) for display purposes. Not suitable for production cache
    * keys due to collision risk.
    */
  def generateShortKey(
      moduleName: String,
      inputs: Map[String, CValue],
      length: Int = 8
  ): String =
    generateKey(moduleName, inputs).take(length)

  /** Create the canonical string representation for hashing. Ensures deterministic ordering of map
    * entries.
    */
  private def canonicalString(
      moduleName: String,
      inputs: Map[String, CValue],
      version: Option[String]
  ): String = {
    val sb = new StringBuilder

    // Module name
    sb.append("m:")
    sb.append(moduleName)
    sb.append(";")

    // Version if present
    version.foreach { v =>
      sb.append("v:")
      sb.append(v)
      sb.append(";")
    }

    // Inputs in sorted order
    sb.append("i:")
    inputs.toSeq.sortBy(_._1).foreach { case (name, value) =>
      sb.append(name)
      sb.append("=")
      sb.append(serializeCValue(value))
      sb.append(",")
    }

    sb.toString()
  }

  /** Serialize a CValue to a canonical string representation. */
  private def serializeCValue(value: CValue): String = value match {
    case CValue.CString(s)  => s"S:${escapeString(s)}"
    case CValue.CInt(i)     => s"I:$i"
    case CValue.CFloat(f)   => s"F:$f"
    case CValue.CBoolean(b) => s"B:$b"
    case CValue.CList(items, _) =>
      val serialized = items.map(serializeCValue).mkString("[", ",", "]")
      s"L:$serialized"
    case CValue.CMap(pairs, _, _) =>
      val sorted = pairs.sortBy(p => serializeCValue(p._1))
      val serialized = sorted
        .map { case (k, v) =>
          s"${serializeCValue(k)}:${serializeCValue(v)}"
        }
        .mkString("{", ",", "}")
      s"M:$serialized"
    case CValue.CProduct(fields, _) =>
      val sorted = fields.toSeq.sortBy(_._1)
      val serialized = sorted
        .map { case (name, v) =>
          s"$name:${serializeCValue(v)}"
        }
        .mkString("(", ",", ")")
      s"P:$serialized"
    case CValue.CUnion(inner, _, tag) =>
      s"U:$tag:${serializeCValue(inner)}"
    case CValue.CSome(inner, _) =>
      s"O+:${serializeCValue(inner)}"
    case CValue.CNone(_) =>
      "O-"
  }

  /** Escape special characters in strings for canonical representation. */
  private def escapeString(s: String): String =
    s.replace("\\", "\\\\")
      .replace(":", "\\:")
      .replace(",", "\\,")
      .replace("[", "\\[")
      .replace("]", "\\]")
      .replace("{", "\\{")
      .replace("}", "\\}")
      .replace("(", "\\(")
      .replace(")", "\\)")

  /** Hash a string using SHA-256 and encode as Base64 URL-safe. */
  private def hashString(input: String): String = {
    val md   = MessageDigest.getInstance("SHA-256")
    val hash = md.digest(input.getBytes("UTF-8"))
    Base64.getUrlEncoder.withoutPadding().encodeToString(hash)
  }

  /** Compute a hash for raw bytes (e.g., for binary inputs). */
  def hashBytes(bytes: Array[Byte]): String = {
    val md   = MessageDigest.getInstance("SHA-256")
    val hash = md.digest(bytes)
    Base64.getUrlEncoder.withoutPadding().encodeToString(hash)
  }
}
