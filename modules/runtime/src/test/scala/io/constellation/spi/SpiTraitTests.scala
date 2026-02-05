package io.constellation.spi

import java.util.UUID

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SpiTraitTests extends AnyFlatSpec with Matchers {

  // -- MetricsProvider.noop --

  "MetricsProvider.noop" should "return IO.unit for counter" in {
    MetricsProvider.noop.counter("test.counter").unsafeRunSync() shouldBe ()
  }

  it should "return IO.unit for counter with tags" in {
    MetricsProvider.noop.counter("test.counter", Map("key" -> "value")).unsafeRunSync() shouldBe ()
  }

  it should "return IO.unit for histogram" in {
    MetricsProvider.noop.histogram("test.histogram", 42.0).unsafeRunSync() shouldBe ()
  }

  it should "return IO.unit for histogram with tags" in {
    MetricsProvider.noop
      .histogram("test.histogram", 42.0, Map("key" -> "value"))
      .unsafeRunSync() shouldBe ()
  }

  it should "return IO.unit for gauge" in {
    MetricsProvider.noop.gauge("test.gauge", 1.0).unsafeRunSync() shouldBe ()
  }

  it should "return IO.unit for gauge with tags" in {
    MetricsProvider.noop.gauge("test.gauge", 1.0, Map("key" -> "value")).unsafeRunSync() shouldBe ()
  }

  // -- TracerProvider.noop --

  "TracerProvider.noop" should "pass through body unchanged" in {
    val result = TracerProvider.noop.span("test.span")(IO.pure(42)).unsafeRunSync()
    result shouldBe 42
  }

  it should "pass through body with attributes" in {
    val result =
      TracerProvider.noop.span("test.span", Map("key" -> "value"))(IO.pure("hello")).unsafeRunSync()
    result shouldBe "hello"
  }

  it should "propagate errors from body" in {
    val error = new RuntimeException("test error")
    val result =
      TracerProvider.noop.span("test.span")(IO.raiseError[Int](error)).attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.left.exists(_.getMessage == "test error") shouldBe true
  }

  it should "not alter IO evaluation semantics" in {
    var sideEffect = false
    val io         = IO { sideEffect = true; 99 }
    sideEffect shouldBe false
    val result = TracerProvider.noop.span("test.span")(io).unsafeRunSync()
    sideEffect shouldBe true
    result shouldBe 99
  }

  // -- ExecutionListener.noop --

  "ExecutionListener.noop" should "return IO.unit for all callbacks" in {
    val id       = UUID.randomUUID()
    val moduleId = UUID.randomUUID()

    ExecutionListener.noop.onExecutionStart(id, "testDag").unsafeRunSync() shouldBe ()
    ExecutionListener.noop.onModuleStart(id, moduleId, "TestModule").unsafeRunSync() shouldBe ()
    ExecutionListener.noop
      .onModuleComplete(id, moduleId, "TestModule", 100L)
      .unsafeRunSync() shouldBe ()
    ExecutionListener.noop
      .onModuleFailed(id, moduleId, "TestModule", new RuntimeException("fail"))
      .unsafeRunSync() shouldBe ()
    ExecutionListener.noop
      .onExecutionComplete(id, "testDag", true, 500L)
      .unsafeRunSync() shouldBe ()
  }

  // -- ExecutionListener.composite --

  "ExecutionListener.composite" should "return noop for empty list" in {
    val composite = ExecutionListener.composite()
    val id        = UUID.randomUUID()
    composite.onExecutionStart(id, "test").unsafeRunSync() shouldBe ()
  }

  it should "return single listener for single-element list" in {
    val listener  = ExecutionListener.noop
    val composite = ExecutionListener.composite(listener)
    composite shouldBe listener
  }

  it should "dispatch events to all listeners" in {
    val result = (for {
      calls1 <- Ref.of[IO, List[String]](Nil)
      calls2 <- Ref.of[IO, List[String]](Nil)

      listener1 = new ExecutionListener {
        def onExecutionStart(executionId: UUID, dagName: String): IO[Unit] =
          calls1.update("start" :: _)
        def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit] =
          calls1.update("moduleStart" :: _)
        def onModuleComplete(
            executionId: UUID,
            moduleId: UUID,
            moduleName: String,
            durationMs: Long
        ): IO[Unit] =
          calls1.update("moduleComplete" :: _)
        def onModuleFailed(executionId: UUID, moduleId: UUID, moduleName: String, error: Throwable)
            : IO[Unit] =
          calls1.update("moduleFailed" :: _)
        def onExecutionComplete(
            executionId: UUID,
            dagName: String,
            succeeded: Boolean,
            durationMs: Long
        ): IO[Unit] =
          calls1.update("complete" :: _)
      }

      listener2 = new ExecutionListener {
        def onExecutionStart(executionId: UUID, dagName: String): IO[Unit] =
          calls2.update("start" :: _)
        def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit] =
          calls2.update("moduleStart" :: _)
        def onModuleComplete(
            executionId: UUID,
            moduleId: UUID,
            moduleName: String,
            durationMs: Long
        ): IO[Unit] =
          calls2.update("moduleComplete" :: _)
        def onModuleFailed(executionId: UUID, moduleId: UUID, moduleName: String, error: Throwable)
            : IO[Unit] =
          calls2.update("moduleFailed" :: _)
        def onExecutionComplete(
            executionId: UUID,
            dagName: String,
            succeeded: Boolean,
            durationMs: Long
        ): IO[Unit] =
          calls2.update("complete" :: _)
      }

      composite = ExecutionListener.composite(listener1, listener2)
      id        = UUID.randomUUID()
      moduleId  = UUID.randomUUID()

      _ <- composite.onExecutionStart(id, "testDag")
      _ <- composite.onModuleStart(id, moduleId, "TestModule")
      _ <- composite.onModuleComplete(id, moduleId, "TestModule", 100L)
      _ <- composite.onExecutionComplete(id, "testDag", true, 500L)

      r1 <- calls1.get
      r2 <- calls2.get
    } yield (r1.reverse, r2.reverse)).unsafeRunSync()

    result._1 shouldBe List("start", "moduleStart", "moduleComplete", "complete")
    result._2 shouldBe List("start", "moduleStart", "moduleComplete", "complete")
  }

  it should "swallow errors from individual listeners" in {
    val result = (for {
      calls <- Ref.of[IO, List[String]](Nil)

      failingListener = new ExecutionListener {
        def onExecutionStart(executionId: UUID, dagName: String): IO[Unit] =
          IO.raiseError(new RuntimeException("boom"))
        def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit] =
          IO.raiseError(new RuntimeException("boom"))
        def onModuleComplete(
            executionId: UUID,
            moduleId: UUID,
            moduleName: String,
            durationMs: Long
        ): IO[Unit] =
          IO.raiseError(new RuntimeException("boom"))
        def onModuleFailed(executionId: UUID, moduleId: UUID, moduleName: String, error: Throwable)
            : IO[Unit] =
          IO.raiseError(new RuntimeException("boom"))
        def onExecutionComplete(
            executionId: UUID,
            dagName: String,
            succeeded: Boolean,
            durationMs: Long
        ): IO[Unit] =
          IO.raiseError(new RuntimeException("boom"))
      }

      recordingListener = new ExecutionListener {
        def onExecutionStart(executionId: UUID, dagName: String): IO[Unit] =
          calls.update("start" :: _)
        def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit] =
          calls.update("moduleStart" :: _)
        def onModuleComplete(
            executionId: UUID,
            moduleId: UUID,
            moduleName: String,
            durationMs: Long
        ): IO[Unit] =
          calls.update("moduleComplete" :: _)
        def onModuleFailed(executionId: UUID, moduleId: UUID, moduleName: String, error: Throwable)
            : IO[Unit] =
          calls.update("moduleFailed" :: _)
        def onExecutionComplete(
            executionId: UUID,
            dagName: String,
            succeeded: Boolean,
            durationMs: Long
        ): IO[Unit] =
          calls.update("complete" :: _)
      }

      composite = ExecutionListener.composite(failingListener, recordingListener)
      id        = UUID.randomUUID()

      // Should not throw despite failingListener errors
      _ <- composite.onExecutionStart(id, "testDag")
      _ <- composite.onExecutionComplete(id, "testDag", true, 500L)

      r <- calls.get
    } yield r.reverse).unsafeRunSync()

    result shouldBe List("start", "complete")
  }

  // -- ConstellationBackends --

  "ConstellationBackends.defaults" should "have noop metrics" in {
    ConstellationBackends.defaults.metrics shouldBe MetricsProvider.noop
  }

  it should "have noop tracer" in {
    ConstellationBackends.defaults.tracer shouldBe TracerProvider.noop
  }

  it should "have noop listener" in {
    ConstellationBackends.defaults.listener shouldBe ExecutionListener.noop
  }

  it should "have no cache backend" in {
    ConstellationBackends.defaults.cache shouldBe None
  }

  "ConstellationBackends" should "allow replacing individual backends" in {
    val customMetrics = new MetricsProvider {
      def counter(name: String, tags: Map[String, String]): IO[Unit]                  = IO.unit
      def histogram(name: String, value: Double, tags: Map[String, String]): IO[Unit] = IO.unit
      def gauge(name: String, value: Double, tags: Map[String, String]): IO[Unit]     = IO.unit
    }

    val backends = ConstellationBackends.defaults.copy(metrics = customMetrics)
    backends.metrics shouldBe customMetrics
    backends.tracer shouldBe TracerProvider.noop
    backends.listener shouldBe ExecutionListener.noop
  }
}
