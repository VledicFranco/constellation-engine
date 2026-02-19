package io.constellation.stream.delivery

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OffsetCommitterTest extends AnyFlatSpec with Matchers {

  "OffsetCommitter.noop" should "return None for currentOffset" in {
    OffsetCommitter.noop.currentOffset.unsafeRunSync() shouldBe None
  }

  it should "accept commits without error" in {
    OffsetCommitter.noop.commit(Offset("123")).unsafeRunSync()
    // noop still returns None after commit
    OffsetCommitter.noop.currentOffset.unsafeRunSync() shouldBe None
  }

  "OffsetCommitter.inMemory" should "track committed offsets" in {
    val result = (for {
      committer <- OffsetCommitter.inMemory
      initial   <- committer.currentOffset
      _         <- committer.commit(Offset("offset-1"))
      after1    <- committer.currentOffset
      _         <- committer.commit(Offset("offset-2"))
      after2    <- committer.currentOffset
    } yield (initial, after1, after2)).unsafeRunSync()

    result._1 shouldBe None
    result._2 shouldBe Some(Offset("offset-1"))
    result._3 shouldBe Some(Offset("offset-2"))
  }

  it should "overwrite previous offset on each commit" in {
    val result = (for {
      committer <- OffsetCommitter.inMemory
      _         <- committer.commit(Offset("a"))
      _         <- committer.commit(Offset("b"))
      _         <- committer.commit(Offset("c"))
      current   <- committer.currentOffset
    } yield current).unsafeRunSync()

    result shouldBe Some(Offset("c"))
  }

  "DeliveryGuarantee" should "have all expected variants" in {
    DeliveryGuarantee.AtMostOnce shouldBe a[DeliveryGuarantee]
    DeliveryGuarantee.AtLeastOnce shouldBe a[DeliveryGuarantee]
  }

  "Offset" should "wrap a string value" in {
    val offset = Offset("partition-0:42")
    offset.value shouldBe "partition-0:42"
  }

  "SourceConnector defaults" should "use AtMostOnce and noop committer" in {
    import io.constellation.CValue
    import io.constellation.stream.connector.*
    import fs2.Stream

    val src = new SourceConnector {
      def name: String     = "test"
      def typeName: String = "test"
      def stream(config: ValidatedConnectorConfig): Stream[IO, CValue] = Stream.empty
    }

    src.deliveryGuarantee shouldBe DeliveryGuarantee.AtMostOnce
    src.offsetCommitter.currentOffset.unsafeRunSync() shouldBe None
  }
}
