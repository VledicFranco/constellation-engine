package io.constellation.http

import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

import cats.effect.unsafe.implicits.global
import cats.implicits.*

import io.constellation.PipelineImage
import io.constellation.impl.{ConstellationImpl, PipelineStoreImpl}
import io.constellation.lang.LangCompiler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FileSystemPipelineStoreTest extends AnyFlatSpec with Matchers {

  private val simpleSource =
    """in x: Int
      |out x""".stripMargin

  /** Create a temp directory and a filesystem store wrapping a fresh in-memory store. */
  private def freshStore(): (FileSystemPipelineStore, Path) = {
    val tmpDir   = Files.createTempDirectory("fs-pipeline-store-test")
    val delegate = PipelineStoreImpl.init.unsafeRunSync()
    val fsStore  = FileSystemPipelineStore.init(tmpDir, delegate).unsafeRunSync()
    (fsStore, tmpDir)
  }

  /** Compile a simple source to get a PipelineImage. */
  private def compileImage(source: String, name: String = "test"): PipelineImage = {
    val compiler = LangCompiler.empty
    val result   = compiler.compileIO(source, name).unsafeRunSync()
    result.toOption.get.pipeline.image
  }

  /** Clean up a temp directory recursively. */
  private def cleanup(dir: Path): Unit =
    if Files.exists(dir) then {
      Files
        .walk(dir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(p => Files.deleteIfExists(p))
    }

  // --- Basic store and retrieve ---

  "FileSystemPipelineStore" should "store and retrieve a pipeline image" in {
    val (store, tmpDir) = freshStore()
    try {
      val image = compileImage(simpleSource)
      store.store(image).unsafeRunSync()

      val retrieved = store.get(image.structuralHash).unsafeRunSync()
      retrieved shouldBe defined
      retrieved.get.structuralHash shouldBe image.structuralHash
    } finally cleanup(tmpDir)
  }

  it should "persist image to filesystem" in {
    val (store, tmpDir) = freshStore()
    try {
      val image = compileImage(simpleSource)
      store.store(image).unsafeRunSync()

      val imageFile = tmpDir.resolve("images").resolve(s"${image.structuralHash}.json")
      Files.exists(imageFile) shouldBe true
      Files.readString(imageFile).contains(image.structuralHash) shouldBe true
    } finally cleanup(tmpDir)
  }

  it should "persist aliases to filesystem" in {
    val (store, tmpDir) = freshStore()
    try {
      val image = compileImage(simpleSource)
      store.store(image).unsafeRunSync()
      store.alias("scoring", image.structuralHash).unsafeRunSync()

      val aliasesFile = tmpDir.resolve("aliases.json")
      Files.exists(aliasesFile) shouldBe true
      val content = Files.readString(aliasesFile)
      content.contains("scoring") shouldBe true
      content.contains(image.structuralHash) shouldBe true
    } finally cleanup(tmpDir)
  }

  it should "resolve aliases" in {
    val (store, tmpDir) = freshStore()
    try {
      val image = compileImage(simpleSource)
      store.store(image).unsafeRunSync()
      store.alias("scoring", image.structuralHash).unsafeRunSync()

      store.resolve("scoring").unsafeRunSync() shouldBe Some(image.structuralHash)
      store.getByName("scoring").unsafeRunSync().map(_.structuralHash) shouldBe Some(
        image.structuralHash
      )
    } finally cleanup(tmpDir)
  }

  it should "list all stored images" in {
    val (store, tmpDir) = freshStore()
    try {
      val img1 = compileImage(simpleSource, "test1")
      val img2 = compileImage("in x: Int\nin y: Int\nout x", "test2")
      store.store(img1).unsafeRunSync()
      store.store(img2).unsafeRunSync()

      val images = store.listImages.unsafeRunSync()
      images should have size 2
    } finally cleanup(tmpDir)
  }

  it should "remove an image from both memory and filesystem" in {
    val (store, tmpDir) = freshStore()
    try {
      val image = compileImage(simpleSource)
      store.store(image).unsafeRunSync()

      val imageFile = tmpDir.resolve("images").resolve(s"${image.structuralHash}.json")
      Files.exists(imageFile) shouldBe true

      val removed = store.remove(image.structuralHash).unsafeRunSync()
      removed shouldBe true

      store.get(image.structuralHash).unsafeRunSync() shouldBe None
      Files.exists(imageFile) shouldBe false
    } finally cleanup(tmpDir)
  }

  // --- Restart survival ---

  it should "survive a simulated restart (load from persisted data)" in {
    val tmpDir = Files.createTempDirectory("fs-pipeline-restart-test")
    try {
      // Session 1: store images and aliases
      val delegate1 = PipelineStoreImpl.init.unsafeRunSync()
      val store1    = FileSystemPipelineStore.init(tmpDir, delegate1).unsafeRunSync()

      val image = compileImage(simpleSource)
      store1.store(image).unsafeRunSync()
      store1.alias("scoring", image.structuralHash).unsafeRunSync()

      // Session 2: create a new store from the same directory
      val delegate2 = PipelineStoreImpl.init.unsafeRunSync()
      val store2    = FileSystemPipelineStore.init(tmpDir, delegate2).unsafeRunSync()

      // Verify data survived
      store2.get(image.structuralHash).unsafeRunSync() shouldBe defined
      store2.resolve("scoring").unsafeRunSync() shouldBe Some(image.structuralHash)
      store2.getByName("scoring").unsafeRunSync().map(_.structuralHash) shouldBe Some(
        image.structuralHash
      )
      store2.listImages.unsafeRunSync() should have size 1
    } finally cleanup(tmpDir)
  }

  it should "persist and restore multiple images across restart" in {
    val tmpDir = Files.createTempDirectory("fs-pipeline-multi-test")
    try {
      val delegate1 = PipelineStoreImpl.init.unsafeRunSync()
      val store1    = FileSystemPipelineStore.init(tmpDir, delegate1).unsafeRunSync()

      val img1 = compileImage(simpleSource, "test1")
      val img2 = compileImage("in x: Int\nin y: Int\nout x", "test2")
      store1.store(img1).unsafeRunSync()
      store1.store(img2).unsafeRunSync()
      store1.alias("pipeline1", img1.structuralHash).unsafeRunSync()
      store1.alias("pipeline2", img2.structuralHash).unsafeRunSync()

      // Simulate restart
      val delegate2 = PipelineStoreImpl.init.unsafeRunSync()
      val store2    = FileSystemPipelineStore.init(tmpDir, delegate2).unsafeRunSync()

      store2.listImages.unsafeRunSync() should have size 2
      store2.resolve("pipeline1").unsafeRunSync() shouldBe Some(img1.structuralHash)
      store2.resolve("pipeline2").unsafeRunSync() shouldBe Some(img2.structuralHash)
    } finally cleanup(tmpDir)
  }

  // --- Corrupted files ---

  it should "skip corrupted image files without crashing" in {
    val tmpDir = Files.createTempDirectory("fs-pipeline-corrupt-test")
    try {
      // Create a valid image first
      val delegate1 = PipelineStoreImpl.init.unsafeRunSync()
      val store1    = FileSystemPipelineStore.init(tmpDir, delegate1).unsafeRunSync()
      val image     = compileImage(simpleSource)
      store1.store(image).unsafeRunSync()

      // Corrupt one image file
      val corruptFile = tmpDir.resolve("images").resolve("corrupted.json")
      Files.writeString(corruptFile, "not valid json {{{")

      // Should load successfully, skipping the corrupted file
      val delegate2 = PipelineStoreImpl.init.unsafeRunSync()
      val store2    = FileSystemPipelineStore.init(tmpDir, delegate2).unsafeRunSync()

      store2.listImages.unsafeRunSync() should have size 1
      store2.get(image.structuralHash).unsafeRunSync() shouldBe defined
    } finally cleanup(tmpDir)
  }

  it should "skip corrupted aliases.json without crashing" in {
    val tmpDir = Files.createTempDirectory("fs-pipeline-corrupt-alias-test")
    try {
      Files.createDirectories(tmpDir.resolve("images"))
      Files.writeString(tmpDir.resolve("aliases.json"), "not valid json")

      val delegate = PipelineStoreImpl.init.unsafeRunSync()
      val store    = FileSystemPipelineStore.init(tmpDir, delegate).unsafeRunSync()

      store.listAliases.unsafeRunSync() shouldBe empty
    } finally cleanup(tmpDir)
  }

  it should "skip corrupted syntactic-index.json without crashing" in {
    val tmpDir = Files.createTempDirectory("fs-pipeline-corrupt-syn-test")
    try {
      Files.createDirectories(tmpDir.resolve("images"))
      Files.writeString(tmpDir.resolve("syntactic-index.json"), "not valid json")

      val delegate = PipelineStoreImpl.init.unsafeRunSync()
      val store    = FileSystemPipelineStore.init(tmpDir, delegate).unsafeRunSync()

      // Should not crash
      store.listImages.unsafeRunSync() shouldBe empty
    } finally cleanup(tmpDir)
  }

  // --- Syntactic index ---

  it should "persist and restore syntactic index" in {
    val tmpDir = Files.createTempDirectory("fs-pipeline-syntactic-test")
    try {
      val delegate1 = PipelineStoreImpl.init.unsafeRunSync()
      val store1    = FileSystemPipelineStore.init(tmpDir, delegate1).unsafeRunSync()

      store1.indexSyntactic("syn-hash-1", "reg-hash", "struct-hash-1").unsafeRunSync()

      // Verify file exists
      val synFile = tmpDir.resolve("syntactic-index.json")
      Files.exists(synFile) shouldBe true
    } finally cleanup(tmpDir)
  }

  // --- Empty directory ---

  it should "handle initialization with empty directory" in {
    val tmpDir = Files.createTempDirectory("fs-pipeline-empty-test")
    try {
      val delegate = PipelineStoreImpl.init.unsafeRunSync()
      val store    = FileSystemPipelineStore.init(tmpDir, delegate).unsafeRunSync()

      store.listImages.unsafeRunSync() shouldBe empty
      store.listAliases.unsafeRunSync() shouldBe empty
    } finally cleanup(tmpDir)
  }

  // --- Reads are served from memory (delegate) ---

  it should "serve reads from in-memory delegate" in {
    val (store, tmpDir) = freshStore()
    try {
      val image = compileImage(simpleSource)
      store.store(image).unsafeRunSync()

      // Multiple reads should work from memory
      store.get(image.structuralHash).unsafeRunSync() shouldBe defined
      store.get(image.structuralHash).unsafeRunSync() shouldBe defined
      store.get(image.structuralHash).unsafeRunSync() shouldBe defined
    } finally cleanup(tmpDir)
  }

  // --- Concurrent writes ---

  it should "handle concurrent writes without data loss" in {
    val (store, tmpDir) = freshStore()
    try {
      val images = (1 to 10).map { i =>
        compileImage(s"in x$i: Int\nout x$i", s"test$i")
      }.toList

      // Store all images concurrently
      import cats.effect.IO
      val storeAll = images.parTraverse(img => store.store(img))
      storeAll.unsafeRunSync()

      store.listImages.unsafeRunSync().size shouldBe images.size

      // Verify all persisted to disk
      val imageFiles = Files.list(tmpDir.resolve("images")).iterator().asScala.toList
      imageFiles.filter(_.toString.endsWith(".json")).size shouldBe images.size
    } finally cleanup(tmpDir)
  }
}
