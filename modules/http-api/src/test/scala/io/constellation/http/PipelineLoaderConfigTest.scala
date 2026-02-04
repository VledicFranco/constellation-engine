package io.constellation.http

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PipelineLoaderConfigTest extends AnyFlatSpec with Matchers {

  "AliasStrategy.fromString" should "parse filename variants" in {
    AliasStrategy.fromString("filename") shouldBe Some(AliasStrategy.FileName)
    AliasStrategy.fromString("file-name") shouldBe Some(AliasStrategy.FileName)
    AliasStrategy.fromString("FileName") shouldBe Some(AliasStrategy.FileName)
    AliasStrategy.fromString("FILE-NAME") shouldBe Some(AliasStrategy.FileName)
  }

  it should "parse relative-path variants" in {
    AliasStrategy.fromString("relativepath") shouldBe Some(AliasStrategy.RelativePath)
    AliasStrategy.fromString("relative-path") shouldBe Some(AliasStrategy.RelativePath)
    AliasStrategy.fromString("RelativePath") shouldBe Some(AliasStrategy.RelativePath)
  }

  it should "parse hash-only variants" in {
    AliasStrategy.fromString("hashonly") shouldBe Some(AliasStrategy.HashOnly)
    AliasStrategy.fromString("hash-only") shouldBe Some(AliasStrategy.HashOnly)
    AliasStrategy.fromString("HashOnly") shouldBe Some(AliasStrategy.HashOnly)
  }

  it should "return None for unknown strategies" in {
    AliasStrategy.fromString("unknown") shouldBe None
    AliasStrategy.fromString("") shouldBe None
    AliasStrategy.fromString("file_name") shouldBe None
  }

  "PipelineLoaderConfig" should "have sensible defaults" in {
    val config = PipelineLoaderConfig(directory = java.nio.file.Path.of("/tmp/pipelines"))
    config.recursive shouldBe false
    config.failOnError shouldBe false
    config.aliasStrategy shouldBe AliasStrategy.FileName
  }
}
