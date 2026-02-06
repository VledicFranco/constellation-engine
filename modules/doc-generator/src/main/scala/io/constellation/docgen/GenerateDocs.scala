package io.constellation.docgen

import scala.annotation.experimental

import io.constellation.docgen.model.*

import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

/** Main entry point for generating documentation from compiled Scala code */
@experimental
object GenerateDocs:

  /** Target packages to extract documentation from */
  val targetPackages: List[String] = List(
    "io.constellation"
  )

  /** Source directories for hash computation (relative to project root) */
  val sourceMapping: Map[String, String] = Map(
    "io.constellation"          -> "modules/core/src/main/scala/io/constellation",
    "io.constellation.runtime"  -> "modules/runtime/src/main/scala/io/constellation",
    "io.constellation.ast"      -> "modules/lang-ast/src/main/scala/io/constellation",
    "io.constellation.parser"   -> "modules/lang-parser/src/main/scala/io/constellation",
    "io.constellation.compiler" -> "modules/lang-compiler/src/main/scala/io/constellation",
    "io.constellation.stdlib"   -> "modules/lang-stdlib/src/main/scala/io/constellation",
    "io.constellation.lsp"      -> "modules/lang-lsp/src/main/scala/io/constellation",
    "io.constellation.http"     -> "modules/http-api/src/main/scala/io/constellation"
  )

  /** Output directory for generated docs */
  val outputDir: Path = Paths.get("docs/generated")

  /** Module target directories to scan for TASTy files */
  val moduleTargets: List[String] = List(
    "modules/core/target/scala-3.3.1/classes",
    "modules/runtime/target/scala-3.3.1/classes",
    "modules/lang-ast/target/scala-3.3.1/classes",
    "modules/lang-parser/target/scala-3.3.1/classes",
    "modules/lang-compiler/target/scala-3.3.1/classes",
    "modules/lang-stdlib/target/scala-3.3.1/classes",
    "modules/lang-lsp/target/scala-3.3.1/classes",
    "modules/http-api/target/scala-3.3.1/classes"
  )

  def main(args: Array[String]): Unit =
    println("Generating Scala documentation catalog...")

    // Use module target directories as classpath
    val classpath = moduleTargets.filter(p => Files.exists(Paths.get(p)))

    println(s"Classpath entries: ${classpath.size}")

    // Extract types from TASTy files
    val types = TastyExtractor.extract(classpath, targetPackages)
    println(s"Extracted ${types.size} types")

    // Group by package
    val byPackage = types.groupBy(_.pkg)
    println(s"Packages: ${byPackage.keys.toList.sorted.mkString(", ")}")

    // Ensure output directory exists
    Files.createDirectories(outputDir)

    // Generate markdown for each package
    byPackage.foreach { case (pkg, pkgTypes) =>
      val sourceDir = findSourceDir(pkg)
      val hash      = MarkdownWriter.computeHash(sourceDir)
      val catalog   = PackageCatalog(pkg, sourceDir, pkgTypes.toList)
      val markdown  = MarkdownWriter.generate(catalog, hash)

      val fileName   = s"$pkg.md"
      val outputPath = outputDir.resolve(fileName)
      Files.writeString(
        outputPath,
        markdown,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
      )
      println(s"  Generated: $fileName (${pkgTypes.size} types)")
    }

    // Generate index file
    generateIndex(byPackage.keys.toList.sorted)

    println(s"Done. Output: $outputDir")

  private def findSourceDir(pkg: String): String =
    // Find the most specific matching source directory
    sourceMapping.toList
      .filter { case (prefix, _) => pkg.startsWith(prefix) }
      .sortBy { case (prefix, _) => -prefix.length }
      .headOption
      .map(_._2)
      .getOrElse(s"modules/*/src/main/scala/${pkg.replace('.', '/')}")

  private def generateIndex(packages: List[String]): Unit =
    val sb = new StringBuilder
    sb.append("<!-- GENERATED: Do not edit manually -->\n\n")
    sb.append("# Generated Scala Catalog\n\n")
    sb.append(
      "This directory contains auto-generated documentation extracted from Scala source code.\n\n"
    )
    sb.append("## Packages\n\n")
    sb.append("| Package | Description |\n")
    sb.append("|---------|-------------|\n")
    packages.foreach { pkg =>
      sb.append(s"| [$pkg](./$pkg.md) | |\n")
    }
    sb.append("\n## Usage\n\n")
    sb.append("These files describe **what exists** in the codebase. For **what it means** and\n")
    sb.append("**how to use it**, see the component ETHOS files with their semantic mappings.\n\n")
    sb.append("## Regenerating\n\n")
    sb.append("```bash\n")
    sb.append("make generate-docs\n")
    sb.append("```\n")

    val outputPath = outputDir.resolve("README.md")
    Files.writeString(
      outputPath,
      sb.toString,
      StandardCharsets.UTF_8,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING
    )
    println("  Generated: README.md (index)")
