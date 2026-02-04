package io.constellation

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.impl.PipelineStoreImpl
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.util.UUID

class PipelineStoreTest extends AnyFlatSpec with Matchers {

  private def mkImage(name: String): PipelineImage = {
    val dag = DagSpec.empty(name)
    PipelineImage(
      structuralHash = ContentHash.computeStructuralHash(dag),
      syntacticHash = "",
      dagSpec = dag,
      moduleOptions = Map.empty,
      compiledAt = Instant.now()
    )
  }

  private def mkStore: PipelineStore = PipelineStoreImpl.init.unsafeRunSync()

  "PipelineStore" should "store and retrieve an image" in {
    val store = mkStore
    val image = mkImage("test")

    val result = (for {
      hash      <- store.store(image)
      retrieved <- store.get(hash)
    } yield (hash, retrieved)).unsafeRunSync()

    result._1 shouldBe image.structuralHash
    result._2 shouldBe Some(image)
  }

  it should "alias and resolve" in {
    val store = mkStore
    val image = mkImage("test")

    val result = (for {
      hash     <- store.store(image)
      _        <- store.alias("myPipeline", hash)
      resolved <- store.resolve("myPipeline")
    } yield resolved).unsafeRunSync()

    result shouldBe Some(image.structuralHash)
  }

  it should "retrieve by name via alias" in {
    val store = mkStore
    val image = mkImage("test")

    val result = (for {
      hash <- store.store(image)
      _    <- store.alias("myPipeline", hash)
      img  <- store.getByName("myPipeline")
    } yield img).unsafeRunSync()

    result shouldBe Some(image)
  }

  it should "return None for unknown name" in {
    val store = mkStore
    store.getByName("nonexistent").unsafeRunSync() shouldBe None
  }

  it should "deduplicate images with the same structural hash" in {
    val store = mkStore
    val image = mkImage("test")

    val result = (for {
      hash1 <- store.store(image)
      hash2 <- store.store(image)
      all   <- store.listImages
    } yield (hash1, hash2, all)).unsafeRunSync()

    result._1 shouldBe result._2
    result._3.size shouldBe 1
  }

  it should "repoint an alias to a different image" in {
    val store = mkStore
    val img1  = mkImage("test1")
    val img2  = mkImage("test2")

    val result = (for {
      hash1 <- store.store(img1)
      hash2 <- store.store(img2)
      _     <- store.alias("latest", hash1)
      r1    <- store.resolve("latest")
      _     <- store.alias("latest", hash2)
      r2    <- store.resolve("latest")
    } yield (r1, r2)).unsafeRunSync()

    result._1 shouldBe Some(img1.structuralHash)
    result._2 shouldBe Some(img2.structuralHash)
  }

  it should "remove an image" in {
    val store = mkStore
    val image = mkImage("test")

    val result = (for {
      hash    <- store.store(image)
      removed <- store.remove(hash)
      gone    <- store.get(hash)
    } yield (removed, gone)).unsafeRunSync()

    result._1 shouldBe true
    result._2 shouldBe None
  }

  it should "return false when removing a non-existent image" in {
    val store = mkStore
    store.remove("nonexistent-hash").unsafeRunSync() shouldBe false
  }

  it should "support syntactic index round-trip" in {
    val store = mkStore
    val image = mkImage("test")

    val result = (for {
      hash   <- store.store(image)
      _      <- store.indexSyntactic("src-hash", "reg-hash", hash)
      lookup <- store.lookupSyntactic("src-hash", "reg-hash")
    } yield lookup).unsafeRunSync()

    result shouldBe Some(image.structuralHash)
  }

  it should "return None for unknown syntactic lookup" in {
    val store = mkStore
    store.lookupSyntactic("unknown", "unknown").unsafeRunSync() shouldBe None
  }

  it should "list aliases" in {
    val store = mkStore
    val img1  = mkImage("test1")
    val img2  = mkImage("test2")

    val result = (for {
      h1  <- store.store(img1)
      h2  <- store.store(img2)
      _   <- store.alias("a", h1)
      _   <- store.alias("b", h2)
      all <- store.listAliases
    } yield all).unsafeRunSync()

    result should contain key "a"
    result should contain key "b"
  }
}
