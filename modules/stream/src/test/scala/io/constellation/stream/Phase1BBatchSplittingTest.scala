package io.constellation.stream

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.CValue

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** RFC-034 Phase 1B: Tests for batch splitting and max_batch_size handling. */
class Phase1BBatchSplittingTest extends AnyFlatSpec with Matchers {

  "BatchSplitter.splitBatch" should "return single batch when size is unlimited" in {
    val inputs  = (1 to 100).map(i => CValue.CString(s"item_$i")).toList
    val batches = BatchSplitter.splitBatch(inputs, maxBatchSize = 0)

    batches should have length 1
    batches.head should have length 100
  }

  it should "return single batch when inputs fit within limit" in {
    val inputs  = (1 to 10).map(i => CValue.CString(s"item_$i")).toList
    val batches = BatchSplitter.splitBatch(inputs, maxBatchSize = 50)

    batches should have length 1
    batches.head should have length 10
  }

  it should "split batch into multiple chunks when exceeding limit" in {
    val inputs  = (1 to 100).map(i => CValue.CString(s"item_$i")).toList
    val batches = BatchSplitter.splitBatch(inputs, maxBatchSize = 30)

    batches should have length 4 // 30 + 30 + 30 + 10
    batches(0) should have length 30
    batches(1) should have length 30
    batches(2) should have length 30
    batches(3) should have length 10
  }

  it should "handle exact boundary conditions" in {
    val inputs  = (1 to 100).map(i => CValue.CString(s"item_$i")).toList
    val batches = BatchSplitter.splitBatch(inputs, maxBatchSize = 25)

    batches should have length 4
    batches.foreach(b => b should have length 25)
  }

  it should "preserve element order after splitting" in {
    val inputs  = (1 to 50).map(i => CValue.CString(s"num_$i")).toList
    val batches = BatchSplitter.splitBatch(inputs, maxBatchSize = 15)

    val flattened = batches.flatten
    flattened should equal(inputs)
  }

  "BatchSplitter.processWithSplitting" should "process and combine results correctly" in {
    val inputs = List(
      CValue.CString("a"),
      CValue.CString("b"),
      CValue.CString("c"),
      CValue.CString("d"),
      CValue.CString("e")
    )

    val batchFn: List[CValue] => IO[List[Either[Throwable, CValue]]] = { inputs =>
      IO.pure(
        inputs.map { input =>
          Right(
            input match {
              case CValue.CString(s) => CValue.CString(s"processed:$s")
              case other             => other
            }
          )
        }
      )
    }

    val result =
      BatchSplitter.processWithSplitting(inputs, batchFn, maxBatchSize = 2).unsafeRunSync()

    result should have length 5
    result.map(_.toOption.get.asInstanceOf[CValue.CString].value) should contain theSameElementsAs
      List("processed:a", "processed:b", "processed:c", "processed:d", "processed:e")
  }

  it should "preserve order when splitting across multiple chunks" in {
    val inputs = (1 to 50).map(i => CValue.CString(f"item_$i%02d")).toList

    val batchFn: List[CValue] => IO[List[Either[Throwable, CValue]]] = { inputs =>
      IO.pure(inputs.map(Right(_)))
    }

    val result =
      BatchSplitter.processWithSplitting(inputs, batchFn, maxBatchSize = 15).unsafeRunSync()

    val resultStrings = result.map(_.toOption.get.asInstanceOf[CValue.CString].value)
    resultStrings should equal(inputs.map(_.asInstanceOf[CValue.CString].value))
  }

  it should "handle error results from chunked processing" in {
    val inputs = (1 to 10).map(i => CValue.CString(s"item_$i")).toList

    val batchFn: List[CValue] => IO[List[Either[Throwable, CValue]]] = { inputs =>
      IO.pure(
        inputs.zipWithIndex.map { case (input, idx) =>
          if idx == 1 then Left(new RuntimeException("Error at idx 1"))
          else Right(input)
        }
      )
    }

    val result =
      BatchSplitter.processWithSplitting(inputs, batchFn, maxBatchSize = 3).unsafeRunSync()

    result should have length 10
    // Chunks: [3, 3, 3, 1]. Error at idx==1 in each chunk with >1 element = 3 errors
    result.count(_.isLeft) should be(3)
  }

  "StreamOptions" should "support max_batch_size configuration" in {
    val options1 = StreamOptions(maxBatchSize = 0)
    options1.maxBatchSize shouldBe 0 // Unlimited

    val options2 = StreamOptions(maxBatchSize = 50)
    options2.maxBatchSize shouldBe 50

    val options3 = StreamOptions(maxBatchSize = 100)
    options3.maxBatchSize shouldBe 100
  }

  it should "support adaptive batching flag" in {
    val options1 = StreamOptions(adaptiveBatching = false)
    options1.adaptiveBatching shouldBe false

    val options2 = StreamOptions(adaptiveBatching = true)
    options2.adaptiveBatching shouldBe true
  }

  it should "support batch routing flag" in {
    val options1 = StreamOptions(batchRouting = false)
    options1.batchRouting shouldBe false

    val options2 = StreamOptions(batchRouting = true)
    options2.batchRouting shouldBe true
  }

  it should "combine all Phase 1B options" in {
    val options = StreamOptions(
      maxBatchSize = 100,
      adaptiveBatching = true,
      batchRouting = true,
      metricsEnabled = true
    )

    options.maxBatchSize shouldBe 100
    options.adaptiveBatching shouldBe true
    options.batchRouting shouldBe true
    options.metricsEnabled shouldBe true
  }

  "Batch splitting edge cases" should "handle empty batch" in {
    val inputs  = List.empty[CValue]
    val batches = BatchSplitter.splitBatch(inputs, maxBatchSize = 50)

    batches should have length 1
    batches.head should be(empty)
  }

  it should "handle single element" in {
    val inputs  = List(CValue.CString("single"))
    val batches = BatchSplitter.splitBatch(inputs, maxBatchSize = 50)

    batches should have length 1
    batches.head should have length 1
  }

  it should "handle single element with max size 1" in {
    val inputs  = List(CValue.CString("single"))
    val batches = BatchSplitter.splitBatch(inputs, maxBatchSize = 1)

    batches should have length 1
    batches.head should have length 1
  }

  it should "handle max batch size of 1" in {
    val inputs  = (1 to 5).map(i => CValue.CString(s"$i")).toList
    val batches = BatchSplitter.splitBatch(inputs, maxBatchSize = 1)

    batches should have length 5
    batches.foreach(b => b should have length 1)
  }
}
