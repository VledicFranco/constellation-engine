package io.constellation.stream

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.*
import io.constellation.stream.connector.*
import io.constellation.stream.error.StreamErrorStrategy
import io.constellation.stream.join.JoinStrategy

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StreamCompilerTest extends AnyFlatSpec with Matchers {

  "StreamCompiler" should "compile without error on an empty DagSpec" in {
    val dagSpec = DagSpec(
      metadata = ComponentMetadata("test", "test pipeline", Nil, 1, 0),
      modules = Map.empty,
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = Nil,
      outputBindings = Map.empty
    )

    val result = StreamCompiler
      .wire(dagSpec, ConnectorRegistry.empty, Map.empty)
      .unsafeRunSync()

    result shouldBe a[StreamGraph]
    result.metrics should not be null
  }

  "StreamMetrics" should "start with zero counters" in {
    val snapshot = StreamMetrics.create
      .flatMap(_.snapshot)
      .unsafeRunSync()

    snapshot.totalElements shouldBe 0L
    snapshot.totalErrors shouldBe 0L
    snapshot.totalDlq shouldBe 0L
    snapshot.perModule shouldBe empty
  }

  it should "track element counts" in {
    val snapshot = (for {
      metrics <- StreamMetrics.create
      _       <- metrics.recordElement("ModuleA")
      _       <- metrics.recordElement("ModuleA")
      _       <- metrics.recordElement("ModuleB")
      snap    <- metrics.snapshot
    } yield snap).unsafeRunSync()

    snapshot.totalElements shouldBe 3L
    snapshot.perModule("ModuleA").elementsProcessed shouldBe 2L
    snapshot.perModule("ModuleB").elementsProcessed shouldBe 1L
  }

  it should "track error counts" in {
    val snapshot = (for {
      metrics <- StreamMetrics.create
      _       <- metrics.recordError("ModuleA")
      _       <- metrics.recordDlq("ModuleA")
      snap    <- metrics.snapshot
    } yield snap).unsafeRunSync()

    snapshot.totalErrors shouldBe 1L
    snapshot.totalDlq shouldBe 1L
    snapshot.perModule("ModuleA").errors shouldBe 1L
    snapshot.perModule("ModuleA").dlqCount shouldBe 1L
  }

  "StreamMetrics.noop" should "return zero metrics" in {
    val snapshot = StreamMetrics.noop.snapshot.unsafeRunSync()
    snapshot.totalElements shouldBe 0L
  }

  "ConnectorRegistry" should "register and look up sources and sinks" in {
    val srcQ = cats.effect.std.Queue.bounded[IO, Option[CValue]](10).unsafeRunSync()
    val snkQ = cats.effect.std.Queue.bounded[IO, CValue](10).unsafeRunSync()

    val registry = ConnectorRegistry.builder
      .source("input", MemoryConnector.source("input", srcQ))
      .sink("output", MemoryConnector.sink("output", snkQ))
      .build

    registry.getSource("input") shouldBe defined
    registry.getSink("output") shouldBe defined
    registry.getSource("nonexistent") shouldBe None
    registry.getSink("nonexistent") shouldBe None
    registry.sourceNames shouldBe Set("input")
    registry.sinkNames shouldBe Set("output")
  }

  "MemoryConnector" should "round-trip values through source and sink" in {
    val result = (for {
      srcQ <- cats.effect.std.Queue.bounded[IO, Option[CValue]](10)
      snkQ <- cats.effect.std.Queue.bounded[IO, CValue](10)
      src = MemoryConnector.source("test", srcQ)
      snk = MemoryConnector.sink("test", snkQ)
      // Push values to source and terminate
      _ <- srcQ.offer(Some(CValue.CString("hello")))
      _ <- srcQ.offer(Some(CValue.CInt(42L)))
      _ <- srcQ.offer(None)
      // Pipe source through sink
      _ <- src
        .stream(connector.ValidatedConnectorConfig.empty)
        .through(snk.pipe(connector.ValidatedConnectorConfig.empty))
        .compile
        .drain
      // Collect results
      items <- snkQ.tryTakeN(None)
    } yield items).unsafeRunSync()

    result should have size 2
    result(0) shouldBe CValue.CString("hello")
    result(1) shouldBe CValue.CInt(42L)
  }

  "StreamOptions" should "have sensible defaults" in {
    val opts = StreamOptions()
    opts.defaultParallelism shouldBe 1
    opts.defaultBufferSize shouldBe 256
    opts.metricsEnabled shouldBe true
  }

  "StreamErrorStrategy" should "have all expected variants" in {
    StreamErrorStrategy.Skip shouldBe a[StreamErrorStrategy]
    StreamErrorStrategy.Log shouldBe a[StreamErrorStrategy]
    StreamErrorStrategy.Propagate shouldBe a[StreamErrorStrategy]
    StreamErrorStrategy.Dlq shouldBe a[StreamErrorStrategy]
  }

  "JoinStrategy" should "have all expected variants" in {
    JoinStrategy.CombineLatest shouldBe a[JoinStrategy]
    JoinStrategy.Zip shouldBe a[JoinStrategy]
    JoinStrategy.Buffer(100) shouldBe a[JoinStrategy]
    JoinStrategy.Buffer(100).maxBuffer shouldBe 100
  }

  // ===== Integration Tests =====

  /** Helper to build a simple linear DAG: source -> module -> sink */
  private def linearDag(
      sourceName: String,
      moduleName: String,
      sinkName: String
  ): (DagSpec, java.util.UUID, java.util.UUID, java.util.UUID) = {
    val sourceId = java.util.UUID.randomUUID()
    val moduleId = java.util.UUID.randomUUID()
    val sinkId   = java.util.UUID.randomUUID()

    val dagSpec = DagSpec(
      metadata = ComponentMetadata("linear", "linear pipeline", Nil, 1, 0),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata(moduleName, "transform", Nil, 1, 0),
          consumes = Map("input" -> CType.CString),
          produces = Map("output" -> CType.CString)
        )
      ),
      data = Map(
        sourceId -> DataNodeSpec(
          sourceName,
          Map(sourceId -> sourceName),
          CType.CString,
          None,
          Map.empty
        ),
        sinkId -> DataNodeSpec(sinkName, Map(sinkId -> sinkName), CType.CString, None, Map.empty)
      ),
      inEdges = Set(sourceId -> moduleId),
      outEdges = Set(moduleId -> sinkId),
      declaredOutputs = List(sinkName),
      outputBindings = Map(sinkName -> sinkId)
    )

    (dagSpec, sourceId, moduleId, sinkId)
  }

  "StreamCompiler" should "wire a linear pipeline (source -> module -> sink)" in {
    val (dagSpec, _, moduleId, _) = linearDag("input", "Uppercase", "output")

    val result = (for {
      srcQ <- cats.effect.std.Queue.bounded[IO, Option[CValue]](10)
      snkQ <- cats.effect.std.Queue.bounded[IO, CValue](10)
      registry = ConnectorRegistry.builder
        .source("input", MemoryConnector.source("input", srcQ))
        .sink("output", MemoryConnector.sink("output", snkQ))
        .build
      uppercaseFn = (v: CValue) =>
        v match {
          case CValue.CString(s) => IO.pure(CValue.CString(s.toUpperCase))
          case other             => IO.pure(other)
        }
      graph <- StreamCompiler.wire(dagSpec, registry, Map(moduleId -> uppercaseFn))
      // Push data, terminate, and run
      _     <- srcQ.offer(Some(CValue.CString("hello")))
      _     <- srcQ.offer(Some(CValue.CString("world")))
      _     <- srcQ.offer(None)
      _     <- graph.stream.compile.drain
      items <- snkQ.tryTakeN(None)
      snap  <- graph.metrics.snapshot
    } yield (items, snap)).unsafeRunSync()

    val (items, snap) = result
    items should have size 2
    items(0) shouldBe CValue.CString("HELLO")
    items(1) shouldBe CValue.CString("WORLD")
    snap.totalElements shouldBe 2L
    snap.perModule("Uppercase").elementsProcessed shouldBe 2L
  }

  it should "handle module errors with Log strategy" in {
    val (dagSpec, _, moduleId, _) = linearDag("input", "Faulty", "output")

    val result = (for {
      srcQ <- cats.effect.std.Queue.bounded[IO, Option[CValue]](10)
      snkQ <- cats.effect.std.Queue.bounded[IO, CValue](10)
      registry = ConnectorRegistry.builder
        .source("input", MemoryConnector.source("input", srcQ))
        .sink("output", MemoryConnector.sink("output", snkQ))
        .build
      faultyFn = (_: CValue) => IO.raiseError[CValue](new RuntimeException("boom"))
      graph <- StreamCompiler.wire(
        dagSpec,
        registry,
        Map(moduleId -> faultyFn),
        errorStrategy = StreamErrorStrategy.Log
      )
      _     <- srcQ.offer(Some(CValue.CString("test")))
      _     <- srcQ.offer(None)
      _     <- graph.stream.compile.drain
      items <- snkQ.tryTakeN(None)
      snap  <- graph.metrics.snapshot
    } yield (items, snap)).unsafeRunSync()

    val (items, snap) = result
    items should have size 1
    items(0) shouldBe a[CValue.CString] // error message
    snap.totalErrors shouldBe 1L
    snap.perModule("Faulty").errors shouldBe 1L
  }

  it should "handle module errors with Skip strategy" in {
    val (dagSpec, _, moduleId, _) = linearDag("input", "Skipper", "output")

    val result = (for {
      srcQ <- cats.effect.std.Queue.bounded[IO, Option[CValue]](10)
      snkQ <- cats.effect.std.Queue.bounded[IO, CValue](10)
      registry = ConnectorRegistry.builder
        .source("input", MemoryConnector.source("input", srcQ))
        .sink("output", MemoryConnector.sink("output", snkQ))
        .build
      skipFn = (_: CValue) => IO.raiseError[CValue](new RuntimeException("skip me"))
      graph <- StreamCompiler.wire(
        dagSpec,
        registry,
        Map(moduleId -> skipFn),
        errorStrategy = StreamErrorStrategy.Skip
      )
      _     <- srcQ.offer(Some(CValue.CString("test")))
      _     <- srcQ.offer(None)
      _     <- graph.stream.compile.drain
      items <- snkQ.tryTakeN(None)
    } yield items).unsafeRunSync()

    // Skip genuinely drops failed elements — nothing reaches the sink
    result shouldBe empty
  }

  it should "propagate errors with Propagate strategy" in {
    val (dagSpec, _, moduleId, _) = linearDag("input", "PropFail", "output")

    val result = (for {
      srcQ <- cats.effect.std.Queue.bounded[IO, Option[CValue]](10)
      snkQ <- cats.effect.std.Queue.bounded[IO, CValue](10)
      registry = ConnectorRegistry.builder
        .source("input", MemoryConnector.source("input", srcQ))
        .sink("output", MemoryConnector.sink("output", snkQ))
        .build
      failFn = (_: CValue) => IO.raiseError[CValue](new RuntimeException("fatal"))
      graph <- StreamCompiler.wire(
        dagSpec,
        registry,
        Map(moduleId -> failFn),
        errorStrategy = StreamErrorStrategy.Propagate
      )
      _      <- srcQ.offer(Some(CValue.CString("test")))
      _      <- srcQ.offer(None)
      result <- graph.stream.compile.drain.attempt
    } yield result).unsafeRunSync()

    result.isLeft shouldBe true
    result.left.toOption.get.getMessage shouldBe "fatal"
  }

  it should "trigger shutdown via graph.shutdown" in {
    val dagSpec = DagSpec(
      metadata = ComponentMetadata("empty", "empty", Nil, 1, 0),
      modules = Map.empty,
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = Nil,
      outputBindings = Map.empty
    )

    val result = (for {
      graph <- StreamCompiler.wire(dagSpec, ConnectorRegistry.empty, Map.empty)
      _     <- graph.shutdown
    } yield "ok").unsafeRunSync()

    result shouldBe "ok"
  }

  "StreamTestKit" should "provide ergonomic emit/collect API" in {
    val result = (for {
      kit <- io.constellation.stream.testing.StreamTestKit.create(
        List("source1"),
        List("sink1")
      )
      _     <- kit.emit("source1", CValue.CString("a"))
      _     <- kit.emit("source1", CValue.CInt(1L))
      _     <- kit.complete("source1")
      items <- kit.collectSink("sink1")
    } yield items).unsafeRunSync()

    // Sink has no connection yet, so no items arrive
    result shouldBe empty
  }

  it should "raise error for unknown source" in {
    val result = (for {
      kit    <- io.constellation.stream.testing.StreamTestKit.create(List("src"), List("snk"))
      result <- kit.emit("nonexistent", CValue.CString("x")).attempt
    } yield result).unsafeRunSync()

    result.isLeft shouldBe true
  }

  "StreamRuntime" should "deploy and drain a graph" in {
    val dagSpec = DagSpec(
      metadata = ComponentMetadata("deploy-test", "test", Nil, 1, 0),
      modules = Map.empty,
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = Nil,
      outputBindings = Map.empty
    )

    val result = (for {
      graph <- StreamCompiler.wire(dagSpec, ConnectorRegistry.empty, Map.empty)
      _     <- StreamRuntime.deploy(graph)
    } yield "deployed").unsafeRunSync()

    result shouldBe "deployed"
  }

  "StreamEvent" should "create events with timestamps" in {
    val open = StreamEvent.CircuitOpen("mod", 5)
    open.moduleName shouldBe "mod"
    open.consecutiveErrors shouldBe 5

    val closed = StreamEvent.CircuitClosed("mod")
    closed.moduleName shouldBe "mod"

    val zip = StreamEvent.ZipExhausted("left", "right", "left")
    zip.exhaustedSide shouldBe "left"

    val dlq = StreamEvent.ElementDlq("mod", new RuntimeException("err"))
    dlq.moduleName shouldBe "mod"
  }

  // ===== Fan-in Join Strategy Tests (Issue #229) =====

  /** Helper to build a fan-in DAG: source1 + source2 -> module -> sink */
  private def fanInDag(
      source1Name: String,
      source2Name: String,
      moduleName: String,
      sinkName: String
  ): (DagSpec, java.util.UUID, java.util.UUID, java.util.UUID) = {
    val src1Id = java.util.UUID.randomUUID()
    val src2Id = java.util.UUID.randomUUID()
    val modId  = java.util.UUID.randomUUID()
    val snkId  = java.util.UUID.randomUUID()

    val dagSpec = DagSpec(
      metadata = ComponentMetadata("fan-in", "fan-in pipeline", Nil, 1, 0),
      modules = Map(
        modId -> ModuleNodeSpec(
          metadata = ComponentMetadata(moduleName, "merge", Nil, 1, 0),
          consumes = Map(source1Name -> CType.CString, source2Name -> CType.CString),
          produces = Map(
            "output" -> CType.CProduct(
              Map(source1Name -> CType.CString, source2Name -> CType.CString)
            )
          )
        )
      ),
      data = Map(
        src1Id -> DataNodeSpec(
          source1Name,
          Map(src1Id -> source1Name),
          CType.CString,
          None,
          Map.empty
        ),
        src2Id -> DataNodeSpec(
          source2Name,
          Map(src2Id -> source2Name),
          CType.CString,
          None,
          Map.empty
        ),
        snkId -> DataNodeSpec(
          sinkName,
          Map(snkId -> sinkName),
          CType.CProduct(Map(source1Name -> CType.CString, source2Name -> CType.CString)),
          None,
          Map.empty
        )
      ),
      inEdges = Set(src1Id -> modId, src2Id -> modId),
      outEdges = Set(modId -> snkId),
      declaredOutputs = List(sinkName),
      outputBindings = Map(sinkName -> snkId)
    )
    (dagSpec, src1Id, src2Id, modId)
  }

  "StreamCompiler fan-in" should "zip two input streams into CProduct values" in {
    val (dagSpec, _, _, modId) = fanInDag("left", "right", "Merge", "output")

    val result = (for {
      q1   <- cats.effect.std.Queue.bounded[IO, Option[CValue]](10)
      q2   <- cats.effect.std.Queue.bounded[IO, Option[CValue]](10)
      snkQ <- cats.effect.std.Queue.bounded[IO, CValue](10)
      registry = ConnectorRegistry.builder
        .source("left", MemoryConnector.source("left", q1))
        .source("right", MemoryConnector.source("right", q2))
        .sink("output", MemoryConnector.sink("output", snkQ))
        .build
      identityFn = (v: CValue) => IO.pure(v)
      graph <- StreamCompiler.wire(
        dagSpec,
        registry,
        Map(modId -> identityFn),
        joinStrategy = JoinStrategy.Zip
      )
      _     <- q1.offer(Some(CValue.CString("a")))
      _     <- q1.offer(Some(CValue.CString("b")))
      _     <- q1.offer(Some(CValue.CString("c")))
      _     <- q1.offer(None)
      _     <- q2.offer(Some(CValue.CString("x")))
      _     <- q2.offer(Some(CValue.CString("y")))
      _     <- q2.offer(Some(CValue.CString("z")))
      _     <- q2.offer(None)
      _     <- graph.stream.compile.drain
      items <- snkQ.tryTakeN(None)
    } yield items).unsafeRunSync()

    result should have size 3
    all(result) shouldBe a[CValue.CProduct]
    result(0).asInstanceOf[CValue.CProduct].value("left") shouldBe CValue.CString("a")
    result(0).asInstanceOf[CValue.CProduct].value("right") shouldBe CValue.CString("x")
    result(2).asInstanceOf[CValue.CProduct].value("left") shouldBe CValue.CString("c")
    result(2).asInstanceOf[CValue.CProduct].value("right") shouldBe CValue.CString("z")
  }

  it should "combineLatest two input streams into CProduct values" in {
    val (dagSpec, _, _, modId) = fanInDag("left", "right", "Merge", "output")

    val result = (for {
      q1   <- cats.effect.std.Queue.bounded[IO, Option[CValue]](10)
      q2   <- cats.effect.std.Queue.bounded[IO, Option[CValue]](10)
      snkQ <- cats.effect.std.Queue.bounded[IO, CValue](10)
      registry = ConnectorRegistry.builder
        .source("left", MemoryConnector.source("left", q1))
        .source("right", MemoryConnector.source("right", q2))
        .sink("output", MemoryConnector.sink("output", snkQ))
        .build
      identityFn = (v: CValue) => IO.pure(v)
      graph <- StreamCompiler.wire(
        dagSpec,
        registry,
        Map(modId -> identityFn),
        joinStrategy = JoinStrategy.CombineLatest
      )
      _     <- q1.offer(Some(CValue.CString("a")))
      _     <- q1.offer(None)
      _     <- q2.offer(Some(CValue.CString("x")))
      _     <- q2.offer(None)
      _     <- graph.stream.compile.drain
      items <- snkQ.tryTakeN(None)
    } yield items).unsafeRunSync()

    result should not be empty
    all(result) shouldBe a[CValue.CProduct]
    result.foreach { v =>
      val m = v.asInstanceOf[CValue.CProduct].value
      m should contain key "left"
      m should contain key "right"
    }
  }

  it should "buffer two input streams and zip into CProduct values" in {
    val (dagSpec, _, _, modId) = fanInDag("left", "right", "Merge", "output")

    val result = (for {
      q1   <- cats.effect.std.Queue.bounded[IO, Option[CValue]](10)
      q2   <- cats.effect.std.Queue.bounded[IO, Option[CValue]](10)
      snkQ <- cats.effect.std.Queue.bounded[IO, CValue](10)
      registry = ConnectorRegistry.builder
        .source("left", MemoryConnector.source("left", q1))
        .source("right", MemoryConnector.source("right", q2))
        .sink("output", MemoryConnector.sink("output", snkQ))
        .build
      identityFn = (v: CValue) => IO.pure(v)
      graph <- StreamCompiler.wire(
        dagSpec,
        registry,
        Map(modId -> identityFn),
        joinStrategy = JoinStrategy.Buffer(16)
      )
      _     <- q1.offer(Some(CValue.CString("a")))
      _     <- q1.offer(Some(CValue.CString("b")))
      _     <- q1.offer(None)
      _     <- q2.offer(Some(CValue.CString("x")))
      _     <- q2.offer(Some(CValue.CString("y")))
      _     <- q2.offer(None)
      _     <- graph.stream.compile.drain
      items <- snkQ.tryTakeN(None)
    } yield items).unsafeRunSync()

    result should have size 2
    all(result) shouldBe a[CValue.CProduct]
  }

  // ===== Collect Pseudo-Module Tests (Issue #230) =====

  /** Helper to build a collect DAG: source -> collect -> sink */
  private def collectDag(
      sourceName: String,
      sinkName: String
  ): (DagSpec, java.util.UUID, java.util.UUID, java.util.UUID) = {
    val sourceId  = java.util.UUID.randomUUID()
    val collectId = java.util.UUID.randomUUID()
    val sinkId    = java.util.UUID.randomUUID()

    val dagSpec = DagSpec(
      metadata = ComponentMetadata("collect-test", "collect pipeline", Nil, 1, 0),
      modules = Map(
        collectId -> ModuleNodeSpec(
          metadata = ComponentMetadata("collect", "batch elements", Nil, 1, 0),
          consumes = Map("input" -> CType.CString),
          produces = Map("output" -> CType.CList(CType.CString))
        )
      ),
      data = Map(
        sourceId -> DataNodeSpec(
          sourceName,
          Map(sourceId -> sourceName),
          CType.CString,
          None,
          Map.empty
        ),
        sinkId -> DataNodeSpec(
          sinkName,
          Map(sinkId -> sinkName),
          CType.CList(CType.CString),
          None,
          Map.empty
        )
      ),
      inEdges = Set(sourceId -> collectId),
      outEdges = Set(collectId -> sinkId),
      declaredOutputs = List(sinkName),
      outputBindings = Map(sinkName -> sinkId)
    )

    (dagSpec, sourceId, collectId, sinkId)
  }

  "StreamCompiler collect" should "batch elements by count" in {
    val (dagSpec, _, collectId, _) = collectDag("input", "output")

    val result = (for {
      srcQ <- cats.effect.std.Queue.bounded[IO, Option[CValue]](10)
      snkQ <- cats.effect.std.Queue.bounded[IO, CValue](10)
      registry = ConnectorRegistry.builder
        .source("input", MemoryConnector.source("input", srcQ))
        .sink("output", MemoryConnector.sink("output", snkQ))
        .build
      graph <- StreamCompiler.wire(
        dagSpec,
        registry,
        Map.empty, // collect doesn't use module functions
        moduleOptions = Map(
          collectId -> io.constellation.ModuleCallOptions(
            batchSize = Some(3),
            batchTimeoutMs = Some(5000L)
          )
        )
      )
      _     <- srcQ.offer(Some(CValue.CString("a")))
      _     <- srcQ.offer(Some(CValue.CString("b")))
      _     <- srcQ.offer(Some(CValue.CString("c")))
      _     <- srcQ.offer(None)
      _     <- graph.stream.compile.drain
      items <- snkQ.tryTakeN(None)
      snap  <- graph.metrics.snapshot
    } yield (items, snap)).unsafeRunSync()

    val (items, snap) = result
    items should have size 1
    items(0) shouldBe a[CValue.CList]
    val list = items(0).asInstanceOf[CValue.CList]
    list.value should have size 3
    list.value(0) shouldBe CValue.CString("a")
    list.value(1) shouldBe CValue.CString("b")
    list.value(2) shouldBe CValue.CString("c")
    snap.totalElements shouldBe 1L
    snap.perModule("collect").elementsProcessed shouldBe 1L
  }

  it should "emit partial batch on stream end before count fills" in {
    val (dagSpec, _, collectId, _) = collectDag("input", "output")

    val result = (for {
      srcQ <- cats.effect.std.Queue.bounded[IO, Option[CValue]](10)
      snkQ <- cats.effect.std.Queue.bounded[IO, CValue](10)
      registry = ConnectorRegistry.builder
        .source("input", MemoryConnector.source("input", srcQ))
        .sink("output", MemoryConnector.sink("output", snkQ))
        .build
      graph <- StreamCompiler.wire(
        dagSpec,
        registry,
        Map.empty,
        moduleOptions = Map(
          collectId -> io.constellation.ModuleCallOptions(
            batchSize = Some(10),
            batchTimeoutMs = Some(200L)
          )
        )
      )
      _     <- srcQ.offer(Some(CValue.CString("x")))
      _     <- srcQ.offer(Some(CValue.CString("y")))
      _     <- srcQ.offer(None)
      _     <- graph.stream.compile.drain
      items <- snkQ.tryTakeN(None)
    } yield items).unsafeRunSync()

    result should have size 1
    result(0) shouldBe a[CValue.CList]
    val list = result(0).asInstanceOf[CValue.CList]
    list.value should have size 2
    list.value(0) shouldBe CValue.CString("x")
    list.value(1) shouldBe CValue.CString("y")
  }

  "MemoryConnector.pair" should "create matched source/sink pair" in {
    val result = (for {
      pairResult <- MemoryConnector.pair("test-pair")
      (srcQ, snkQ, src, snk) = pairResult
      _ <- srcQ.offer(Some(CValue.CInt(99L)))
      _ <- srcQ.offer(None)
      _ <- src
        .stream(connector.ValidatedConnectorConfig.empty)
        .through(snk.pipe(connector.ValidatedConnectorConfig.empty))
        .compile
        .drain
      items <- snkQ.tryTakeN(None)
    } yield items).unsafeRunSync()

    result should have size 1
    result(0) shouldBe CValue.CInt(99L)
  }
}
