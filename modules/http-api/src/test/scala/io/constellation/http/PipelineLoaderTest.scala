package io.constellation.http

import java.nio.file.{Files, Path}

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*

import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.stdlib.StdLib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PipelineLoaderTest extends AnyFlatSpec with Matchers {

  // Simple passthrough CST source (requires no registered modules)
  private val validSource1 =
    """in x: Int
      |out x""".stripMargin

  private val validSource2 =
    """in text: String
      |out text""".stripMargin

  private val invalidSource =
    """in x: Int
      |out undefined_variable""".stripMargin

  /** Helper to create a temp directory with `.cst` files. */
  private def withTempDir(files: Map[String, String])(test: Path => Unit): Unit = {
    val tmpDir = Files.createTempDirectory("pipeline-loader-test")
    try {
      files.foreach { case (name, content) =>
        val filePath = tmpDir.resolve(name)
        Files.createDirectories(filePath.getParent)
        Files.writeString(filePath, content)
      }
      test(tmpDir)
    } finally
      // Cleanup
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete(_))
  }

  /** Create fresh constellation + compiler for each test to avoid state leakage. */
  private def freshInstances: (ConstellationImpl, LangCompiler) =
    (ConstellationImpl.init.unsafeRunSync(), LangCompiler.empty)

  // --- Load valid files ---

  "PipelineLoader" should "load 2 valid .cst files" in withTempDir(
    Map("scoring.cst" -> validSource1, "text.cst" -> validSource2)
  ) { dir =>
    val (constellation, compiler) = freshInstances
    val config                    = PipelineLoaderConfig(directory = dir)
    val result = PipelineLoader.load(config, constellation, compiler).unsafeRunSync()

    result.loaded shouldBe 2
    result.failed shouldBe 0
    result.skipped shouldBe 0
    result.errors shouldBe empty

    // Verify pipelines are accessible by alias
    constellation.PipelineStore.getByName("scoring").unsafeRunSync() should not be None
    constellation.PipelineStore.getByName("text").unsafeRunSync() should not be None
  }

  // --- Mixed valid + invalid, failOnError = false ---

  it should "load valid files and report failures when failOnError is false" in withTempDir(
    Map("good.cst" -> validSource1, "bad.cst" -> invalidSource)
  ) { dir =>
    val (constellation, compiler) = freshInstances
    val config                    = PipelineLoaderConfig(directory = dir, failOnError = false)
    val result = PipelineLoader.load(config, constellation, compiler).unsafeRunSync()

    result.loaded shouldBe 1
    result.failed shouldBe 1
    result.skipped shouldBe 0
    result.errors should have size 1

    constellation.PipelineStore.getByName("good").unsafeRunSync() should not be None
    constellation.PipelineStore.getByName("bad").unsafeRunSync() shouldBe None
  }

  // --- Mixed valid + invalid, failOnError = true ---

  it should "raise error when failOnError is true and a file fails" in withTempDir(
    Map("good.cst" -> validSource1, "bad.cst" -> invalidSource)
  ) { dir =>
    val (constellation, compiler) = freshInstances
    val config                    = PipelineLoaderConfig(directory = dir, failOnError = true)

    val error = intercept[RuntimeException] {
      PipelineLoader.load(config, constellation, compiler).unsafeRunSync()
    }
    error.getMessage should include("failed to compile")
  }

  // --- Empty directory ---

  it should "return empty result for empty directory" in withTempDir(Map.empty) { dir =>
    val (constellation, compiler) = freshInstances
    val config                    = PipelineLoaderConfig(directory = dir)
    val result = PipelineLoader.load(config, constellation, compiler).unsafeRunSync()

    result.loaded shouldBe 0
    result.failed shouldBe 0
    result.skipped shouldBe 0
    result.errors shouldBe empty
  }

  // --- Recursive scanning ---

  it should "find files in subdirectories when recursive is true" in withTempDir(
    Map("top.cst" -> validSource1, "sub/nested.cst" -> validSource2)
  ) { dir =>
    val (constellation, compiler) = freshInstances
    val config                    = PipelineLoaderConfig(directory = dir, recursive = true)
    val result = PipelineLoader.load(config, constellation, compiler).unsafeRunSync()

    result.loaded shouldBe 2
    result.failed shouldBe 0

    constellation.PipelineStore.getByName("top").unsafeRunSync() should not be None
    constellation.PipelineStore.getByName("nested").unsafeRunSync() should not be None
  }

  it should "not find files in subdirectories when recursive is false" in withTempDir(
    Map("top.cst" -> validSource1, "sub/nested.cst" -> validSource2)
  ) { dir =>
    val (constellation, compiler) = freshInstances
    val config                    = PipelineLoaderConfig(directory = dir, recursive = false)
    val result = PipelineLoader.load(config, constellation, compiler).unsafeRunSync()

    result.loaded shouldBe 1
    result.failed shouldBe 0
  }

  // --- Dedup via syntactic hash ---

  it should "skip files that are already in the store" in withTempDir(
    Map("scoring.cst" -> validSource1)
  ) { dir =>
    val (constellation, compiler) = freshInstances
    val config                    = PipelineLoaderConfig(directory = dir)

    // Load once
    val result1 = PipelineLoader.load(config, constellation, compiler).unsafeRunSync()
    result1.loaded shouldBe 1
    result1.skipped shouldBe 0

    // Load again — should be skipped via syntactic index
    val result2 = PipelineLoader.load(config, constellation, compiler).unsafeRunSync()
    result2.loaded shouldBe 0
    result2.skipped shouldBe 1
  }

  // --- AliasStrategy.FileName ---

  it should "create alias from file name stem with FileName strategy" in withTempDir(
    Map("scoring.cst" -> validSource1)
  ) { dir =>
    val (constellation, compiler) = freshInstances
    val config = PipelineLoaderConfig(directory = dir, aliasStrategy = AliasStrategy.FileName)
    PipelineLoader.load(config, constellation, compiler).unsafeRunSync()

    constellation.PipelineStore.getByName("scoring").unsafeRunSync() should not be None
  }

  // --- AliasStrategy.RelativePath ---

  it should "create alias from relative path with RelativePath strategy" in withTempDir(
    Map("sub/scoring.cst" -> validSource1)
  ) { dir =>
    val (constellation, compiler) = freshInstances
    val config = PipelineLoaderConfig(
      directory = dir,
      recursive = true,
      aliasStrategy = AliasStrategy.RelativePath
    )
    PipelineLoader.load(config, constellation, compiler).unsafeRunSync()

    constellation.PipelineStore.getByName("sub/scoring").unsafeRunSync() should not be None
  }

  // --- AliasStrategy.HashOnly ---

  it should "not create aliases with HashOnly strategy" in withTempDir(
    Map("scoring.cst" -> validSource1)
  ) { dir =>
    val (constellation, compiler) = freshInstances
    val config = PipelineLoaderConfig(directory = dir, aliasStrategy = AliasStrategy.HashOnly)
    val result = PipelineLoader.load(config, constellation, compiler).unsafeRunSync()

    result.loaded shouldBe 1
    constellation.PipelineStore.getByName("scoring").unsafeRunSync() shouldBe None

    // But the image should exist in the store (accessible by hash)
    val images = constellation.PipelineStore.listImages.unsafeRunSync()
    images should have size 1
  }

  // --- FileName collision ---

  it should "skip duplicate file names with FileName strategy and recursive" in withTempDir(
    Map("a/test.cst" -> validSource1, "b/test.cst" -> validSource2)
  ) { dir =>
    val (constellation, compiler) = freshInstances
    val config = PipelineLoaderConfig(
      directory = dir,
      recursive = true,
      aliasStrategy = AliasStrategy.FileName
    )
    val result = PipelineLoader.load(config, constellation, compiler).unsafeRunSync()

    // First one (a/test.cst) should load, second (b/test.cst) should be skipped as collision
    result.loaded shouldBe 1
    result.failed shouldBe 1 // collision counts as failure
    result.errors should have size 1
    result.errors.head should include("Alias collision")

    // The alias should point to the first file's pipeline
    constellation.PipelineStore.getByName("test").unsafeRunSync() should not be None
  }

  // --- No collision with RelativePath strategy ---

  it should "not have collisions with RelativePath strategy for same-name files" in withTempDir(
    Map("a/test.cst" -> validSource1, "b/test.cst" -> validSource2)
  ) { dir =>
    val (constellation, compiler) = freshInstances
    val config = PipelineLoaderConfig(
      directory = dir,
      recursive = true,
      aliasStrategy = AliasStrategy.RelativePath
    )
    val result = PipelineLoader.load(config, constellation, compiler).unsafeRunSync()

    result.loaded shouldBe 2
    result.failed shouldBe 0

    constellation.PipelineStore.getByName("a/test").unsafeRunSync() should not be None
    constellation.PipelineStore.getByName("b/test").unsafeRunSync() should not be None
  }

  // --- Non-existent directory ---

  it should "raise error for non-existent directory" in {
    val (constellation, compiler) = freshInstances
    val config = PipelineLoaderConfig(directory = Path.of("/nonexistent/pipeline/dir"))

    val error = intercept[IllegalArgumentException] {
      PipelineLoader.load(config, constellation, compiler).unsafeRunSync()
    }
    error.getMessage should include("does not exist")
  }

  // --- Non-directory path ---

  it should "raise error when path is a file, not a directory" in {
    val tmpFile = Files.createTempFile("not-a-dir", ".txt")
    try {
      val (constellation, compiler) = freshInstances
      val config                    = PipelineLoaderConfig(directory = tmpFile)

      val error = intercept[IllegalArgumentException] {
        PipelineLoader.load(config, constellation, compiler).unsafeRunSync()
      }
      error.getMessage should include("not a directory")
    } finally Files.delete(tmpFile)
  }

  // --- Ignores non-cst files ---

  it should "ignore non-.cst files" in withTempDir(
    Map("readme.md" -> "# Readme", "scoring.cst" -> validSource1, "data.json" -> "{}")
  ) { dir =>
    val (constellation, compiler) = freshInstances
    val config                    = PipelineLoaderConfig(directory = dir)
    val result = PipelineLoader.load(config, constellation, compiler).unsafeRunSync()

    result.loaded shouldBe 1
    result.failed shouldBe 0
  }

  // --- Closure crash resilience (RFC-030) ---

  // Source that triggers IllegalStateException in IRGenerator.generateLambdaIR:
  // the lambda captures `threshold` from outer scope, which is not resolvable
  // in the lambda's isolated GenContext.
  private val closureSource =
    """use stdlib.collection
      |use stdlib.compare
      |
      |in numbers: List<Int>
      |in threshold: Int
      |
      |above = filter(numbers, (x) => gt(x, threshold))
      |out above""".stripMargin

  private def freshInstancesWithStdLib: (ConstellationImpl, LangCompiler) = {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    StdLib.allModules.values.toList
      .traverse(constellation.setModule)
      .unsafeRunSync()
    (constellation, StdLib.compiler)
  }

  it should "gracefully handle closure crash instead of crashing (failOnError=false)" in
    withTempDir(
      Map("closure.cst" -> closureSource, "good.cst" -> validSource1)
    ) { dir =>
      val (constellation, compiler) = freshInstancesWithStdLib
      val config = PipelineLoaderConfig(directory = dir, failOnError = false)
      val result = PipelineLoader.load(config, constellation, compiler).unsafeRunSync()

      // The closure file should fail gracefully, the passthrough file should load
      result.loaded shouldBe 1
      result.failed shouldBe 1
      result.errors should have size 1
      result.errors.head should include("IllegalStateException")
    }

  it should "gracefully handle closure crash instead of crashing (failOnError=true)" in
    withTempDir(
      Map("closure.cst" -> closureSource)
    ) { dir =>
      val (constellation, compiler) = freshInstancesWithStdLib
      val config = PipelineLoaderConfig(directory = dir, failOnError = true)

      // Should raise RuntimeException with compilation failure info, not crash with
      // raw IllegalStateException
      val error = intercept[RuntimeException] {
        PipelineLoader.load(config, constellation, compiler).unsafeRunSync()
      }
      error.getMessage should include("failed to compile")
      error.getMessage should include("IllegalStateException")
    }

  it should "handle compiler throwing unexpected exception as failure" in withTempDir(
    Map("crash.cst" -> validSource1)
  ) { dir =>
    // Create a compiler that throws on compileIO
    val throwingCompiler = new LangCompiler {
      def compile(
          source: String,
          dagName: String
      ) = throw new IllegalStateException("simulated crash")

      def compileToIR(
          source: String,
          dagName: String
      ) = throw new IllegalStateException("simulated crash")

      def functionRegistry = LangCompiler.empty.functionRegistry
    }
    val (constellation, _) = freshInstances
    val config = PipelineLoaderConfig(directory = dir, failOnError = false)
    val result = PipelineLoader.load(config, constellation, throwingCompiler).unsafeRunSync()

    result.loaded shouldBe 0
    result.failed shouldBe 1
    result.errors should have size 1
    result.errors.head should include("IllegalStateException")
    result.errors.head should include("simulated crash")
  }

  // --- Non-UTF-8 file handling (issue #221) ---

  it should "report descriptive error for non-UTF-8 file" in {
    val tmpDir = Files.createTempDirectory("pipeline-loader-utf8-test")
    try {
      // Write a file with invalid UTF-8 bytes (0xFE 0xFF are never valid in UTF-8)
      val badFile = tmpDir.resolve("bad-encoding.cst")
      Files.write(badFile, Array[Byte](0x69, 0x6e, 0x20, 0xfe.toByte, 0xff.toByte, 0x3a, 0x20))

      val (constellation, compiler) = freshInstances
      val config = PipelineLoaderConfig(directory = tmpDir, failOnError = false)
      val result = PipelineLoader.load(config, constellation, compiler).unsafeRunSync()

      result.failed shouldBe 1
      result.errors should have size 1
      result.errors.head should include("invalid UTF-8")
    } finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete(_))
  }
}
