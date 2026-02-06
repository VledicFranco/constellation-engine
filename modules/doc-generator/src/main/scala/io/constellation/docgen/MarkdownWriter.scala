package io.constellation.docgen

import java.security.MessageDigest
import java.time.Instant

import io.constellation.docgen.model.*

/** Generates markdown documentation from extracted type information */
object MarkdownWriter:

  /** Generate markdown for a package catalog */
  def generate(catalog: PackageCatalog, sourceHash: String): String =
    val sb = new StringBuilder

    // Header with metadata
    sb.append("<!-- GENERATED: Do not edit manually -->\n")
    sb.append(s"<!-- Source: ${catalog.sourceDir} -->\n")
    sb.append(s"<!-- Hash: $sourceHash -->\n")
    sb.append(s"<!-- Generated: ${Instant.now()} -->\n\n")

    sb.append(s"# ${catalog.pkg}\n\n")

    // Group by type
    val objects = catalog.types.collect { case o: ObjectInfo => o }.sortBy(_.name)
    val classes = catalog.types.collect { case c: ClassInfo => c }.sortBy(_.name)
    val traits  = catalog.types.collect { case t: TraitInfo => t }.sortBy(_.name)
    val enums   = catalog.types.collect { case e: EnumInfo => e }.sortBy(_.name)

    if objects.nonEmpty then
      sb.append("## Objects\n\n")
      objects.foreach(o => writeObject(sb, o))

    if classes.nonEmpty then
      sb.append("## Classes\n\n")
      classes.foreach(c => writeClass(sb, c))

    if traits.nonEmpty then
      sb.append("## Traits\n\n")
      traits.foreach(t => writeTrait(sb, t))

    if enums.nonEmpty then
      sb.append("## Enums\n\n")
      enums.foreach(e => writeEnum(sb, e))

    sb.append("<!-- END GENERATED -->\n")
    sb.toString

  private def writeObject(sb: StringBuilder, obj: ObjectInfo): Unit =
    sb.append(s"### ${obj.name}\n\n")
    obj.scaladoc.foreach(doc => sb.append(s"$doc\n\n"))

    if obj.fields.nonEmpty then
      sb.append("**Fields:**\n\n")
      sb.append("| Field | Type | Description |\n")
      sb.append("|-------|------|-------------|\n")
      obj.fields.foreach { f =>
        val desc = f.scaladoc.getOrElse("")
        sb.append(s"| `${f.name}` | `${f.typeName}` | $desc |\n")
      }
      sb.append("\n")

    if obj.methods.nonEmpty then
      sb.append("**Methods:**\n\n")
      sb.append("| Method | Signature | Description |\n")
      sb.append("|--------|-----------|-------------|\n")
      obj.methods.foreach { m =>
        val sig  = formatSignature(m)
        val desc = m.scaladoc.map(_.takeWhile(_ != '\n')).getOrElse("")
        sb.append(s"| `${m.name}` | `$sig` | $desc |\n")
      }
      sb.append("\n")

  private def writeClass(sb: StringBuilder, cls: ClassInfo): Unit =
    val typeParamStr = if cls.typeParams.isEmpty then "" else s"[${cls.typeParams.mkString(", ")}]"
    val casePrefix   = if cls.isCaseClass then "case class" else "class"
    sb.append(s"### $casePrefix ${cls.name}$typeParamStr\n\n")

    cls.scaladoc.foreach(doc => sb.append(s"$doc\n\n"))

    if cls.parents.nonEmpty then sb.append(s"**Extends:** ${cls.parents.mkString(", ")}\n\n")

    if cls.fields.nonEmpty then
      sb.append("**Fields:**\n\n")
      sb.append("| Field | Type | Description |\n")
      sb.append("|-------|------|-------------|\n")
      cls.fields.foreach { f =>
        val desc = f.scaladoc.getOrElse("")
        sb.append(s"| `${f.name}` | `${f.typeName}` | $desc |\n")
      }
      sb.append("\n")

    if cls.methods.nonEmpty then
      sb.append("**Methods:**\n\n")
      sb.append("| Method | Signature | Description |\n")
      sb.append("|--------|-----------|-------------|\n")
      cls.methods.foreach { m =>
        val sig  = formatSignature(m)
        val desc = m.scaladoc.map(_.takeWhile(_ != '\n')).getOrElse("")
        sb.append(s"| `${m.name}` | `$sig` | $desc |\n")
      }
      sb.append("\n")

  private def writeTrait(sb: StringBuilder, trt: TraitInfo): Unit =
    val typeParamStr = if trt.typeParams.isEmpty then "" else s"[${trt.typeParams.mkString(", ")}]"
    sb.append(s"### trait ${trt.name}$typeParamStr\n\n")

    trt.scaladoc.foreach(doc => sb.append(s"$doc\n\n"))

    if trt.parents.nonEmpty then sb.append(s"**Extends:** ${trt.parents.mkString(", ")}\n\n")

    if trt.methods.nonEmpty then
      sb.append("**Methods:**\n\n")
      sb.append("| Method | Signature | Description |\n")
      sb.append("|--------|-----------|-------------|\n")
      trt.methods.foreach { m =>
        val sig  = formatSignature(m)
        val desc = m.scaladoc.map(_.takeWhile(_ != '\n')).getOrElse("")
        sb.append(s"| `${m.name}` | `$sig` | $desc |\n")
      }
      sb.append("\n")

  private def writeEnum(sb: StringBuilder, enm: EnumInfo): Unit =
    sb.append(s"### enum ${enm.name}\n\n")
    enm.scaladoc.foreach(doc => sb.append(s"$doc\n\n"))

    sb.append("**Cases:**\n\n")
    sb.append("| Case | Parameters |\n")
    sb.append("|------|------------|\n")
    enm.cases.foreach { c =>
      val params =
        if c.params.isEmpty then ""
        else c.params.map(p => s"${p.name}: ${p.typeName}").mkString(", ")
      sb.append(s"| `${c.name}` | $params |\n")
    }
    sb.append("\n")

  private def formatSignature(method: MethodInfo): String =
    val typeParams =
      if method.typeParams.isEmpty then "" else s"[${method.typeParams.mkString(", ")}]"
    val params =
      if method.params.isEmpty then "()"
      else method.params.map(p => s"${p.name}: ${p.typeName}").mkString("(", ", ", ")")
    s"$typeParams$params: ${method.returnType}"

  /** Compute hash of source directory for freshness tracking.
    *
    * Note: Normalizes line endings to LF to ensure consistent hashes across platforms.
    */
  def computeHash(sourceDir: String): String =
    import java.nio.charset.StandardCharsets
    import java.nio.file.{Files, Paths}
    import scala.jdk.CollectionConverters.*

    val path = Paths.get(sourceDir)
    if !Files.exists(path) then return "unknown"

    val md = MessageDigest.getInstance("SHA-256")
    Files
      .walk(path)
      .filter(p => p.toString.endsWith(".scala"))
      .sorted()
      .forEach { file =>
        // Read as text and normalize line endings to LF for consistent cross-platform hashing
        val content    = Files.readString(file, StandardCharsets.UTF_8)
        val normalized = content.replace("\r\n", "\n").replace("\r", "\n")
        md.update(normalized.getBytes(StandardCharsets.UTF_8))
      }

    md.digest().take(6).map("%02x".format(_)).mkString
