package io.constellation.docgen

import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*
import scala.util.matching.Regex

/** Verifies that ethos invariants have valid implementation and test references */
object EthosVerifier:

  case class Invariant(
      name: String,
      number: Int,
      implementation: Option[Reference],
      test: Option[Reference],
      sourceFile: Path,
      sourceLine: Int
  )

  case class Reference(
      file: String,
      symbol: String
  )

  sealed trait VerificationStatus
  case class Valid(invariant: Invariant)                      extends VerificationStatus
  case class MissingImpl(invariant: Invariant)                extends VerificationStatus
  case class MissingTest(invariant: Invariant)                extends VerificationStatus
  case class BrokenImpl(invariant: Invariant, reason: String) extends VerificationStatus
  case class BrokenTest(invariant: Invariant, reason: String) extends VerificationStatus

  case class Report(
      valid: List[Valid],
      missingImpl: List[MissingImpl],
      missingTest: List[MissingTest],
      brokenImpl: List[BrokenImpl],
      brokenTest: List[BrokenTest]
  ):
    def isHealthy: Boolean =
      missingImpl.isEmpty && missingTest.isEmpty && brokenImpl.isEmpty && brokenTest.isEmpty

    def totalInvariants: Int =
      valid.size + missingImpl.size + missingTest.size + brokenImpl.size + brokenTest.size

    def coverage: Double =
      if totalInvariants == 0 then 100.0
      else (valid.size.toDouble / totalInvariants) * 100.0

  // Matches: ### 1. Invariant name
  private val invariantHeaderPattern: Regex = """^###\s+(\d+)\.\s+(.+)$""".r

  // Matches: | Implementation | `path/to/file.scala#symbol` |
  private val implRefPattern: Regex = """\|\s*Implementation\s*\|\s*`([^`]+)`\s*\|""".r

  // Matches: | Test | `path/to/file.scala#symbol` |
  private val testRefPattern: Regex = """\|\s*Test\s*\|\s*`([^`]+)`\s*\|""".r

  // Parses: path/to/file.scala#symbol
  private val refPattern: Regex = """^(.+)#(.+)$""".r

  def main(args: Array[String]): Unit =
    val docsDir    = Paths.get("docs")
    val sourceRoot = Paths.get(".")

    println("Ethos Verification Report")
    println("=" * 40)
    println()

    val ethosFiles = findEthosFiles(docsDir)
    println(s"Found ${ethosFiles.size} ETHOS.md files")
    println()

    val allInvariants = ethosFiles.flatMap(parseEthosFile)
    println(s"Total invariants: ${allInvariants.size}")

    val report = verify(allInvariants, sourceRoot)

    println()
    println(s"Valid: ${report.valid.size}")
    println(s"Missing implementation ref: ${report.missingImpl.size}")
    println(s"Missing test ref: ${report.missingTest.size}")
    println(s"Broken implementation ref: ${report.brokenImpl.size}")
    println(s"Broken test ref: ${report.brokenTest.size}")
    println()
    println(f"Coverage: ${report.coverage}%.1f%%")
    println()

    if report.missingImpl.nonEmpty then
      println("Missing implementation references:")
      report.missingImpl.foreach { m =>
        println(s"  - ${m.invariant.sourceFile}#${m.invariant.number}: ${m.invariant.name}")
      }
      println()

    if report.missingTest.nonEmpty then
      println("Missing test references:")
      report.missingTest.foreach { m =>
        println(s"  - ${m.invariant.sourceFile}#${m.invariant.number}: ${m.invariant.name}")
      }
      println()

    if report.brokenImpl.nonEmpty then
      println("Broken implementation references:")
      report.brokenImpl.foreach { b =>
        println(s"  - ${b.invariant.sourceFile}#${b.invariant.number}: ${b.reason}")
      }
      println()

    if report.brokenTest.nonEmpty then
      println("Broken test references:")
      report.brokenTest.foreach { b =>
        println(s"  - ${b.invariant.sourceFile}#${b.invariant.number}: ${b.reason}")
      }
      println()

    if report.isHealthy then
      println("All invariants verified.")
      sys.exit(0)
    else
      println("FAILED: Some invariants are not properly verified.")
      sys.exit(1)

  def findEthosFiles(dir: Path): List[Path] =
    if !Files.exists(dir) then return Nil

    Files
      .walk(dir)
      .filter(p => p.getFileName.toString == "ETHOS.md")
      .toList
      .asScala
      .toList

  def parseEthosFile(file: Path): List[Invariant] =
    val content = Files.readString(file, StandardCharsets.UTF_8)
    val lines   = content.linesIterator.toList

    val invariants = scala.collection.mutable.ListBuffer[Invariant]()
    var currentInvariant: Option[(Int, String, Int)] = None // (number, name, startLine)
    var implRef: Option[Reference]                   = None
    var testRef: Option[Reference]                   = None

    lines.zipWithIndex.foreach { case (line, idx) =>
      line match
        case invariantHeaderPattern(num, name) =>
          // Save previous invariant if exists
          currentInvariant.foreach { case (n, nm, sl) =>
            invariants += Invariant(nm, n, implRef, testRef, file, sl)
          }
          // Start new invariant
          currentInvariant = Some((num.toInt, name.trim, idx + 1))
          implRef = None
          testRef = None

        case implRefPattern(ref) =>
          ref match
            case refPattern(f, s) => implRef = Some(Reference(f, s))
            case _                => // Invalid format, ignore
        case testRefPattern(ref) =>
          ref match
            case refPattern(f, s) => testRef = Some(Reference(f, s))
            case _                => // Invalid format, ignore
        case _ => // Other lines, ignore
    }

    // Save last invariant
    currentInvariant.foreach { case (n, nm, sl) =>
      invariants += Invariant(nm, n, implRef, testRef, file, sl)
    }

    invariants.toList

  def verify(invariants: List[Invariant], sourceRoot: Path): Report =
    val results = invariants.map { inv =>
      val implStatus = inv.implementation match
        case None => Left(MissingImpl(inv))
        case Some(ref) =>
          checkReference(ref, sourceRoot) match
            case Right(_)     => Right(())
            case Left(reason) => Left(BrokenImpl(inv, reason))

      val testStatus = inv.test match
        case None => Left(MissingTest(inv))
        case Some(ref) =>
          checkReference(ref, sourceRoot) match
            case Right(_)     => Right(())
            case Left(reason) => Left(BrokenTest(inv, reason))

      (implStatus, testStatus) match
        case (Right(_), Right(_))      => Valid(inv)
        case (Left(m: MissingImpl), _) => m
        case (Left(b: BrokenImpl), _)  => b
        case (_, Left(m: MissingTest)) => m
        case (_, Left(b: BrokenTest))  => b
        case _                         => Valid(inv) // Shouldn't happen
    }

    Report(
      valid = results.collect { case v: Valid => v },
      missingImpl = results.collect { case m: MissingImpl => m },
      missingTest = results.collect { case m: MissingTest => m },
      brokenImpl = results.collect { case b: BrokenImpl => b },
      brokenTest = results.collect { case b: BrokenTest => b }
    )

  private def checkReference(ref: Reference, sourceRoot: Path): Either[String, Unit] =
    val file = sourceRoot.resolve(ref.file)

    if !Files.exists(file) then return Left(s"File not found: ${ref.file}")

    val content = Files.readString(file, StandardCharsets.UTF_8)

    // Check if symbol exists in file (simple substring match)
    // Could be enhanced to use proper parsing
    if !content.contains(ref.symbol) then
      return Left(s"Symbol '${ref.symbol}' not found in ${ref.file}")

    Right(())
