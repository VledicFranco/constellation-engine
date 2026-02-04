package io.constellation.http

import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PipelineVersionStoreTest extends AnyFlatSpec with Matchers {

  private def freshStore: PipelineVersionStore =
    PipelineVersionStore.init.unsafeRunSync()

  "PipelineVersionStore" should "create v1 for a new pipeline" in {
    val store = freshStore
    val pv = store.recordVersion("scoring", "hash-aaa", Some("in x: Int\nout x")).unsafeRunSync()

    pv.version shouldBe 1
    pv.structuralHash shouldBe "hash-aaa"
    pv.source shouldBe Some("in x: Int\nout x")
  }

  it should "auto-increment version numbers" in {
    val store = freshStore
    val v1 = store.recordVersion("scoring", "hash-aaa", None).unsafeRunSync()
    val v2 = store.recordVersion("scoring", "hash-bbb", None).unsafeRunSync()
    val v3 = store.recordVersion("scoring", "hash-ccc", None).unsafeRunSync()

    v1.version shouldBe 1
    v2.version shouldBe 2
    v3.version shouldBe 3
  }

  it should "return versions ordered newest first" in {
    val store = freshStore
    store.recordVersion("scoring", "hash-aaa", None).unsafeRunSync()
    store.recordVersion("scoring", "hash-bbb", None).unsafeRunSync()
    store.recordVersion("scoring", "hash-ccc", None).unsafeRunSync()

    val versions = store.listVersions("scoring").unsafeRunSync()
    versions.map(_.version) shouldBe List(3, 2, 1)
    versions.map(_.structuralHash) shouldBe List("hash-ccc", "hash-bbb", "hash-aaa")
  }

  it should "return empty list for unknown pipeline" in {
    val store = freshStore
    store.listVersions("nonexistent").unsafeRunSync() shouldBe empty
  }

  it should "set active version to latest on record" in {
    val store = freshStore
    store.recordVersion("scoring", "hash-aaa", None).unsafeRunSync()
    store.activeVersion("scoring").unsafeRunSync() shouldBe Some(1)

    store.recordVersion("scoring", "hash-bbb", None).unsafeRunSync()
    store.activeVersion("scoring").unsafeRunSync() shouldBe Some(2)
  }

  it should "return None for active version of unknown pipeline" in {
    val store = freshStore
    store.activeVersion("nonexistent").unsafeRunSync() shouldBe None
  }

  it should "setActiveVersion returns true for existing version" in {
    val store = freshStore
    store.recordVersion("scoring", "hash-aaa", None).unsafeRunSync()
    store.recordVersion("scoring", "hash-bbb", None).unsafeRunSync()

    store.setActiveVersion("scoring", 1).unsafeRunSync() shouldBe true
    store.activeVersion("scoring").unsafeRunSync() shouldBe Some(1)
  }

  it should "setActiveVersion returns false for non-existent version" in {
    val store = freshStore
    store.recordVersion("scoring", "hash-aaa", None).unsafeRunSync()

    store.setActiveVersion("scoring", 99).unsafeRunSync() shouldBe false
    store.activeVersion("scoring").unsafeRunSync() shouldBe Some(1) // unchanged
  }

  it should "get a specific version by number" in {
    val store = freshStore
    store.recordVersion("scoring", "hash-aaa", Some("src1")).unsafeRunSync()
    store.recordVersion("scoring", "hash-bbb", Some("src2")).unsafeRunSync()

    val v1 = store.getVersion("scoring", 1).unsafeRunSync()
    v1 should not be None
    v1.get.structuralHash shouldBe "hash-aaa"
    v1.get.source shouldBe Some("src1")

    val v2 = store.getVersion("scoring", 2).unsafeRunSync()
    v2.get.structuralHash shouldBe "hash-bbb"

    store.getVersion("scoring", 3).unsafeRunSync() shouldBe None
  }

  it should "return previous version before the active one" in {
    val store = freshStore
    store.recordVersion("scoring", "hash-aaa", None).unsafeRunSync()
    store.recordVersion("scoring", "hash-bbb", None).unsafeRunSync()
    store.recordVersion("scoring", "hash-ccc", None).unsafeRunSync()

    // Active is v3, previous should be v2
    val prev = store.previousVersion("scoring").unsafeRunSync()
    prev should not be None
    prev.get.version shouldBe 2
    prev.get.structuralHash shouldBe "hash-bbb"
  }

  it should "return None for previousVersion when only v1 exists" in {
    val store = freshStore
    store.recordVersion("scoring", "hash-aaa", None).unsafeRunSync()

    store.previousVersion("scoring").unsafeRunSync() shouldBe None
  }

  it should "return previous version relative to active after setActiveVersion" in {
    val store = freshStore
    store.recordVersion("scoring", "hash-aaa", None).unsafeRunSync()
    store.recordVersion("scoring", "hash-bbb", None).unsafeRunSync()
    store.recordVersion("scoring", "hash-ccc", None).unsafeRunSync()

    // Set active to v2 — previous should be v1
    store.setActiveVersion("scoring", 2).unsafeRunSync()
    val prev = store.previousVersion("scoring").unsafeRunSync()
    prev should not be None
    prev.get.version shouldBe 1
  }

  it should "track multiple pipelines independently" in {
    val store = freshStore
    store.recordVersion("scoring", "hash-aaa", None).unsafeRunSync()
    store.recordVersion("scoring", "hash-bbb", None).unsafeRunSync()
    store.recordVersion("text", "hash-xxx", None).unsafeRunSync()

    store.listVersions("scoring").unsafeRunSync() should have size 2
    store.listVersions("text").unsafeRunSync() should have size 1

    store.activeVersion("scoring").unsafeRunSync() shouldBe Some(2)
    store.activeVersion("text").unsafeRunSync() shouldBe Some(1)
  }

  it should "return None for previousVersion of unknown pipeline" in {
    val store = freshStore
    store.previousVersion("nonexistent").unsafeRunSync() shouldBe None
  }
}
