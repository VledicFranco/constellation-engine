package io.constellation.http

import java.time.Instant

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.CValue
import io.constellation.stream.{StreamGraph, StreamMetrics, StreamMetricsSnapshot}

import fs2.Stream
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StreamLifecycleManagerTest extends AnyFlatSpec with Matchers {

  /** Create a minimal StreamGraph that completes immediately */
  private def immediateGraph: IO[StreamGraph] =
    for {
      metrics  <- StreamMetrics.create
      shutdown <- cats.effect.std.Queue.bounded[IO, Unit](1)
    } yield StreamGraph(
      stream = Stream.empty,
      metrics = metrics,
      shutdown = shutdown.offer(()).void
    )

  /** Create a StreamGraph that runs until shutdown is signalled */
  private def longRunningGraph: IO[StreamGraph] =
    for {
      metrics  <- StreamMetrics.create
      shutdown <- cats.effect.std.Queue.bounded[IO, Unit](1)
    } yield StreamGraph(
      stream = Stream.eval(shutdown.take) >> Stream.empty,
      metrics = metrics,
      shutdown = shutdown.offer(()).void
    )

  /** Create a StreamGraph that fails after starting */
  private def failingGraph: IO[StreamGraph] =
    for {
      metrics  <- StreamMetrics.create
      shutdown <- cats.effect.std.Queue.bounded[IO, Unit](1)
    } yield StreamGraph(
      stream = Stream.raiseError[IO](new RuntimeException("test failure")),
      metrics = metrics,
      shutdown = shutdown.offer(()).void
    )

  // ===== Create =====

  "StreamLifecycleManager" should "be created with empty state" in {
    val mgr     = StreamLifecycleManager.create.unsafeRunSync()
    val streams = mgr.list.unsafeRunSync()
    streams shouldBe empty
  }

  // ===== Deploy =====

  it should "deploy a stream graph" in {
    val result = (for {
      mgr   <- StreamLifecycleManager.create
      graph <- immediateGraph
      res   <- mgr.deploy("s1", "test-stream", graph)
    } yield res).unsafeRunSync()

    result shouldBe a[Right[_, _]]
    val managed = result.toOption.get
    managed.id shouldBe "s1"
    managed.name shouldBe "test-stream"
    managed.status shouldBe StreamStatus.Running
  }

  it should "reject duplicate stream IDs" in {
    val result = (for {
      mgr    <- StreamLifecycleManager.create
      graph  <- immediateGraph
      _      <- mgr.deploy("s1", "test-stream", graph)
      graph2 <- immediateGraph
      res    <- mgr.deploy("s1", "duplicate", graph2)
    } yield res).unsafeRunSync()

    result shouldBe a[Left[_, _]]
    result.left.toOption.get should include("already exists")
  }

  // ===== Get =====

  it should "get a deployed stream by ID" in {
    val result = (for {
      mgr   <- StreamLifecycleManager.create
      graph <- immediateGraph
      _     <- mgr.deploy("s1", "test-stream", graph)
      got   <- mgr.get("s1")
    } yield got).unsafeRunSync()

    result shouldBe defined
    result.get.name shouldBe "test-stream"
  }

  it should "return None for unknown stream ID" in {
    val result = (for {
      mgr <- StreamLifecycleManager.create
      got <- mgr.get("unknown")
    } yield got).unsafeRunSync()

    result shouldBe None
  }

  // ===== List =====

  it should "list all deployed streams" in {
    val result = (for {
      mgr    <- StreamLifecycleManager.create
      graph1 <- immediateGraph
      graph2 <- immediateGraph
      _      <- mgr.deploy("s1", "stream-1", graph1)
      _      <- mgr.deploy("s2", "stream-2", graph2)
      list   <- mgr.list
    } yield list).unsafeRunSync()

    result should have size 2
    result.map(_.name).toSet shouldBe Set("stream-1", "stream-2")
  }

  // ===== Stop =====

  it should "stop a running stream and remove it from state" in {
    val result = (for {
      mgr   <- StreamLifecycleManager.create
      graph <- longRunningGraph
      _     <- mgr.deploy("s1", "test-stream", graph)
      res   <- mgr.stop("s1")
      got   <- mgr.get("s1")
      list  <- mgr.list
    } yield (res, got, list)).unsafeRunSync()

    val (stopResult, streamOpt, streams) = result
    stopResult shouldBe Right(())
    streamOpt shouldBe None
    streams shouldBe empty
  }

  it should "return error when stopping unknown stream" in {
    val result = (for {
      mgr <- StreamLifecycleManager.create
      res <- mgr.stop("unknown")
    } yield res).unsafeRunSync()

    result shouldBe a[Left[_, _]]
    result.left.toOption.get should include("not found")
  }

  it should "return error on double-stop (stream already removed)" in {
    val result = (for {
      mgr   <- StreamLifecycleManager.create
      graph <- longRunningGraph
      _     <- mgr.deploy("s1", "test-stream", graph)
      res1  <- mgr.stop("s1")
      res2  <- mgr.stop("s1")
    } yield (res1, res2)).unsafeRunSync()

    val (first, second) = result
    first shouldBe Right(())
    second shouldBe a[Left[_, _]]
    second.left.toOption.get should include("not found")
  }

  it should "stop a failed stream cleanly" in {
    val result = (for {
      mgr   <- StreamLifecycleManager.create
      graph <- failingGraph
      _     <- mgr.deploy("s1", "test-stream", graph)
      // Allow the fiber to fail
      _   <- IO.sleep(scala.concurrent.duration.FiniteDuration(100, "ms"))
      res <- mgr.stop("s1")
      got <- mgr.get("s1")
    } yield (res, got)).unsafeRunSync()

    val (stopResult, streamOpt) = result
    stopResult shouldBe Right(())
    streamOpt shouldBe None
  }

  // ===== Metrics =====

  it should "return metrics for a deployed stream" in {
    val result = (for {
      mgr   <- StreamLifecycleManager.create
      graph <- immediateGraph
      _     <- mgr.deploy("s1", "test-stream", graph)
      snap  <- mgr.metrics("s1")
    } yield snap).unsafeRunSync()

    result shouldBe defined
    result.get.totalElements shouldBe 0L
    result.get.totalErrors shouldBe 0L
  }

  it should "return None for metrics of unknown stream" in {
    val result = (for {
      mgr  <- StreamLifecycleManager.create
      snap <- mgr.metrics("unknown")
    } yield snap).unsafeRunSync()

    result shouldBe None
  }

  // ===== Event Publisher =====

  it should "publish events when event publisher is set" in {
    val result = (for {
      mgr    <- StreamLifecycleManager.create
      events <- cats.effect.Ref.of[IO, List[StreamEvent]](Nil)
      _      <- mgr.setEventPublisher(e => events.update(_ :+ e))
      graph  <- immediateGraph
      _      <- mgr.deploy("s1", "test-stream", graph)
      evts   <- events.get
    } yield evts).unsafeRunSync()

    result should have size 1
    result.head shouldBe a[StreamEvent.StreamDeployed]
    result.head.streamId shouldBe "s1"
  }
}
