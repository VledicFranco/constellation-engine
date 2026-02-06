package io.constellation.docgen

import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*
import scala.util.matching.Regex

/** Checks if generated documentation is stale compared to source code */
object FreshnessChecker:

  case class DocMetadata(
      source: String,
      hash: String,
      generated: String
  )

  sealed trait FreshnessStatus
  case class Fresh(doc: Path, metadata: DocMetadata)                      extends FreshnessStatus
  case class Stale(doc: Path, metadata: DocMetadata, currentHash: String) extends FreshnessStatus
  case class Invalid(doc: Path, reason: String)                           extends FreshnessStatus

  case class Report(
      fresh: List[Fresh],
      stale: List[Stale],
      invalid: List[Invalid]
  ):
    def isHealthy: Boolean = stale.isEmpty && invalid.isEmpty
    def totalDocs: Int     = fresh.size + stale.size + invalid.size

  private val sourcePattern: Regex    = """<!-- Source: (.+) -->""".r
  private val hashPattern: Regex      = """<!-- Hash: ([a-f0-9]+) -->""".r
  private val generatedPattern: Regex = """<!-- Generated: (.+) -->""".r

  def main(args: Array[String]): Unit =
    val generatedDir = Paths.get("docs/generated")
    val report       = check(generatedDir)

    println("Documentation Freshness Report")
    println("=" * 40)
    println()
    println(s"Total docs: ${report.totalDocs}")
    println(s"  Fresh: ${report.fresh.size}")
    println(s"  Stale: ${report.stale.size}")
    println(s"  Invalid: ${report.invalid.size}")
    println()

    if report.stale.nonEmpty then
      println("Stale documents (need regeneration):")
      report.stale.foreach { s =>
        println(s"  - ${s.doc.getFileName}")
        println(s"    Source: ${s.metadata.source}")
        println(s"    Doc hash: ${s.metadata.hash}")
        println(s"    Current hash: ${s.currentHash}")
      }
      println()

    if report.invalid.nonEmpty then
      println("Invalid documents (missing metadata):")
      report.invalid.foreach { i =>
        println(s"  - ${i.doc.getFileName}: ${i.reason}")
      }
      println()

    if report.isHealthy then
      println("All documentation is up to date.")
      sys.exit(0)
    else
      println("FAILED: Documentation is stale or invalid.")
      println("Run 'make generate-docs' to regenerate.")
      sys.exit(1)

  def check(generatedDir: Path): Report =
    if !Files.exists(generatedDir) then
      return Report(Nil, Nil, List(Invalid(generatedDir, "Directory does not exist")))

    val docs = Files
      .list(generatedDir)
      .filter(p => p.toString.endsWith(".md") && p.getFileName.toString != "README.md")
      .toList
      .asScala
      .toList

    val results = docs.map(checkDoc)

    Report(
      fresh = results.collect { case f: Fresh => f },
      stale = results.collect { case s: Stale => s },
      invalid = results.collect { case i: Invalid => i }
    )

  private def checkDoc(doc: Path): FreshnessStatus =
    val content = Files.readString(doc, StandardCharsets.UTF_8)
    val lines   = content.linesIterator.take(10).toList

    val sourceOpt    = lines.collectFirst { case sourcePattern(s) => s }
    val hashOpt      = lines.collectFirst { case hashPattern(h) => h }
    val generatedOpt = lines.collectFirst { case generatedPattern(g) => g }

    (sourceOpt, hashOpt, generatedOpt) match
      case (Some(source), Some(hash), Some(generated)) =>
        val metadata    = DocMetadata(source, hash, generated)
        val currentHash = MarkdownWriter.computeHash(source)

        if currentHash == hash then Fresh(doc, metadata)
        else if currentHash == "unknown" then
          Fresh(doc, metadata) // Source dir not found, assume fresh
        else Stale(doc, metadata, currentHash)

      case (None, _, _) =>
        Invalid(doc, "Missing Source metadata")

      case (_, None, _) =>
        Invalid(doc, "Missing Hash metadata")

      case (_, _, None) =>
        Invalid(doc, "Missing Generated metadata")
