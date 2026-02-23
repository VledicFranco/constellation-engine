package io.constellation.provider.benchmark

import scala.collection.mutable.ListBuffer

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.*
import io.constellation.provider.sdk.*
import io.constellation.provider.v1.provider as pb
import io.constellation.provider.{CValueSerializer, JsonCValueSerializer}

import io.grpc.{ManagedChannelBuilder, ServerBuilder}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Baseline benchmarks for module provider execution overhead.
  *
  * Measures the current cost of each component in the external module call path:
  *   - CValue serialization/deserialization (JSON-over-bytes)
  *   - gRPC unary Execute roundtrip (localhost, in-process server)
  *   - Full provider call breakdown (serialize + gRPC + deserialize)
  *   - Sequential throughput ceiling (max ops/sec for single-threaded calls)
  *
  * These baselines are recorded BEFORE RFC-034 optimizations. After implementing batch invocation,
  * persistent streaming, and provider-side chaining, re-run to measure improvement.
  *
  * Run with: sbt "moduleProvider/testOnly *ProviderBaselineBenchmark"
  */
class ProviderBaselineBenchmark extends AnyFlatSpec with Matchers {

  private val WarmupIterations             = 5
  private val MeasureIterations            = 20
  private val serializer: CValueSerializer = JsonCValueSerializer

  private val allResults = ListBuffer[BenchmarkResult]()

  // ===== Benchmark Result =====

  private case class BenchmarkResult(
      name: String,
      category: String,
      avgMs: Double,
      minMs: Double,
      maxMs: Double,
      stdDevMs: Double,
      opsPerSec: Double,
      iterations: Int
  ) {
    def show: String = {
      val padded = name.padTo(52, ' ').take(52)
      f"  $padded avg=$avgMs%7.3f ms  [$minMs%.3f–$maxMs%.3f] ±$stdDevMs%.3f  $opsPerSec%8.0f ops/s"
    }
  }

  private def measure(
      name: String,
      category: String,
      warmup: Int = WarmupIterations,
      n: Int = MeasureIterations
  )(op: => Unit): BenchmarkResult = {
    (1 to warmup).foreach(_ => op)
    val ts = (1 to n).map { _ =>
      val t0 = System.nanoTime(); op; (System.nanoTime() - t0) / 1e6
    }
    val avg      = ts.sum / n
    val sorted   = ts.sorted
    val variance = ts.map(t => math.pow(t - avg, 2)).sum / n
    val result = BenchmarkResult(
      name,
      category,
      avg,
      sorted.head,
      sorted.last,
      math.sqrt(variance),
      if avg > 0 then 1000.0 / avg else 0.0,
      n
    )
    allResults += result
    result
  }

  // ===== Test Payloads =====

  private val smallPayload: CValue = CValue.CString("hello world, this is a benchmark test string")

  private val mediumStructure: Map[String, CType] = Map(
    "text"  -> CType.CString,
    "count" -> CType.CInt,
    "score" -> CType.CFloat,
    "tags"  -> CType.CList(CType.CString)
  )

  private val mediumPayload: CValue = CValue.CProduct(
    Map[String, CValue](
      "text"  -> CValue.CString("The quick brown fox jumps over the lazy dog. " * 5),
      "count" -> CValue.CInt(42),
      "score" -> CValue.CFloat(0.95),
      "tags" -> CValue.CList(
        (1 to 10).map(i => CValue.CString(s"tag-$i")).toVector,
        CType.CString
      )
    ),
    mediumStructure
  )

  private val metadataStructure: Map[String, CType] = Map(
    "model"   -> CType.CString,
    "tokens"  -> CType.CInt,
    "version" -> CType.CString
  )

  private val largeStructure: Map[String, CType] = Map(
    "embeddings" -> CType.CList(CType.CFloat),
    "metadata"   -> CType.CProduct(metadataStructure)
  )

  private val largePayload: CValue = CValue.CProduct(
    Map[String, CValue](
      "embeddings" -> CValue.CList(
        (1 to 768).map(i => CValue.CFloat(i * 0.001)).toVector,
        CType.CFloat
      ),
      "metadata" -> CValue.CProduct(
        Map[String, CValue](
          "model"   -> CValue.CString("text-embedding-v2"),
          "tokens"  -> CValue.CInt(512),
          "version" -> CValue.CString("1.0.0")
        ),
        metadataStructure
      )
    ),
    largeStructure
  )

  // ===== gRPC Test Server =====

  private def withExecutorServer[A](
      handler: CValue => IO[CValue]
  )(f: (io.grpc.Server, Int) => A): A = {
    val moduleDef = ModuleDefinition(
      "benchmark",
      CType.CString,
      CType.CString,
      "1.0.0",
      "Benchmark passthrough",
      handler
    )
    val modulesRef = cats.effect.Ref.of[IO, List[ModuleDefinition]](List(moduleDef)).unsafeRunSync()
    val execServer = new ModuleExecutorServer(modulesRef, serializer)
    val impl       = new BenchmarkExecutorImpl(execServer.toHandler)
    val serviceDef =
      pb.ModuleExecutorGrpc.bindService(impl, scala.concurrent.ExecutionContext.global)
    val server = ServerBuilder.forPort(0).addService(serviceDef).build().start()
    try f(server, server.getPort)
    finally server.shutdownNow()
  }

  private def withProductExecutorServer[A](handler: CValue => IO[CValue])(
      f: (io.grpc.Server, Int) => A
  ): A = {
    val moduleDef = ModuleDefinition(
      "benchmark",
      CType.CProduct(Map("text" -> CType.CString)),
      CType.CProduct(Map("result" -> CType.CString)),
      "1.0.0",
      "Benchmark product handler",
      handler
    )
    val modulesRef = cats.effect.Ref.of[IO, List[ModuleDefinition]](List(moduleDef)).unsafeRunSync()
    val execServer = new ModuleExecutorServer(modulesRef, serializer)
    val impl       = new BenchmarkExecutorImpl(execServer.toHandler)
    val serviceDef =
      pb.ModuleExecutorGrpc.bindService(impl, scala.concurrent.ExecutionContext.global)
    val server = ServerBuilder.forPort(0).addService(serviceDef).build().start()
    try f(server, server.getPort)
    finally server.shutdownNow()
  }

  // =====================================================================
  // Suite 1: CValueSerializer Round-Trip
  // =====================================================================

  "Serialization overhead" should "measure small payload round-trip" in {
    val r = measure("serialize+deserialize (small, ~50B)", "serialization") {
      val bytes = serializer.serialize(smallPayload).toOption.get
      val _     = serializer.deserialize(bytes).toOption.get
    }
    println(r.show)
    r.avgMs should be < 5.0
  }

  it should "measure medium payload round-trip" in {
    val r = measure("serialize+deserialize (medium, ~2KB)", "serialization") {
      val bytes = serializer.serialize(mediumPayload).toOption.get
      val _     = serializer.deserialize(bytes).toOption.get
    }
    println(r.show)
    r.avgMs should be < 10.0
  }

  it should "measure large payload round-trip" in {
    val r = measure("serialize+deserialize (large, ~20KB)", "serialization") {
      val bytes = serializer.serialize(largePayload).toOption.get
      val _     = serializer.deserialize(bytes).toOption.get
    }
    println(r.show)
    r.avgMs should be < 50.0
  }

  it should "measure serialize-only for throughput estimation" in {
    val r = measure("serialize only (medium)", "serialization") {
      val _ = serializer.serialize(mediumPayload).toOption.get
    }
    println(r.show)
    r.avgMs should be < 5.0
  }

  it should "measure deserialize-only for throughput estimation" in {
    val bytes = serializer.serialize(mediumPayload).toOption.get
    val r = measure("deserialize only (medium)", "serialization") {
      val _ = serializer.deserialize(bytes).toOption.get
    }
    println(r.show)
    r.avgMs should be < 5.0
  }

  // =====================================================================
  // Suite 2: gRPC Execute Round-Trip (localhost, in-process)
  // =====================================================================

  "gRPC call overhead" should "measure single Execute for simple string" in {
    val handler: CValue => IO[CValue] = input => IO.pure(input)

    withExecutorServer(handler) { (_, port) =>
      val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()
      try {
        val stub       = pb.ModuleExecutorGrpc.blockingStub(channel)
        val inputBytes = serializer.serialize(smallPayload).toOption.get

        val r = measure("gRPC Execute roundtrip (small string)", "grpc") {
          val _ = stub.execute(
            pb.ExecuteRequest(
              moduleName = "benchmark",
              inputData = com.google.protobuf.ByteString.copyFrom(inputBytes),
              executionId = "bench-1"
            )
          )
        }
        println(r.show)
        r.avgMs should be < 20.0
      } finally channel.shutdownNow()
    }
  }

  it should "measure single Execute for CProduct type" in {
    val handler: CValue => IO[CValue] = { input =>
      IO.pure(input match {
        case CValue.CProduct(fields, _) =>
          val text = fields.get("text").collect { case CValue.CString(v) => v }.getOrElse("")
          CValue.CProduct(
            Map("result" -> CValue.CString(text.toUpperCase)),
            Map("result" -> CType.CString)
          )
        case other => other
      })
    }

    withProductExecutorServer(handler) { (_, port) =>
      val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()
      try {
        val stub = pb.ModuleExecutorGrpc.blockingStub(channel)
        val input = CValue.CProduct(
          Map("text" -> CValue.CString("benchmark input")),
          Map("text" -> CType.CString)
        )
        val inputBytes = serializer.serialize(input).toOption.get

        val r = measure("gRPC Execute roundtrip (CProduct)", "grpc") {
          val _ = stub.execute(
            pb.ExecuteRequest(
              moduleName = "benchmark",
              inputData = com.google.protobuf.ByteString.copyFrom(inputBytes),
              executionId = "bench-2"
            )
          )
        }
        println(r.show)
        r.avgMs should be < 20.0
      } finally channel.shutdownNow()
    }
  }

  it should "measure sequential throughput ceiling" in {
    val handler: CValue => IO[CValue] = input => IO.pure(input)

    withExecutorServer(handler) { (_, port) =>
      val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()
      try {
        val stub       = pb.ModuleExecutorGrpc.blockingStub(channel)
        val inputBytes = serializer.serialize(smallPayload).toOption.get
        val request = pb.ExecuteRequest(
          moduleName = "benchmark",
          inputData = com.google.protobuf.ByteString.copyFrom(inputBytes),
          executionId = "bench-throughput"
        )

        // Warmup
        (1 to 50).foreach(_ => stub.execute(request))

        // Measure 500 sequential calls
        val n  = 500
        val t0 = System.nanoTime()
        (1 to n).foreach(_ => stub.execute(request))
        val elapsed   = (System.nanoTime() - t0) / 1e6
        val avgMs     = elapsed / n
        val opsPerSec = n * 1000.0 / elapsed

        val result = BenchmarkResult(
          s"sequential throughput ($n calls)",
          "grpc",
          avgMs,
          avgMs,
          avgMs,
          0.0,
          opsPerSec,
          n
        )
        allResults += result
        println(result.show)
        println(f"    Total: $elapsed%.0f ms for $n calls")
        opsPerSec should be > 100.0 // sanity: at least 100 ops/sec
      } finally channel.shutdownNow()
    }
  }

  // =====================================================================
  // Suite 3: Full Provider Call Breakdown
  // =====================================================================

  "Provider call breakdown" should "decompose overhead into serialize + gRPC + deserialize" in {
    val handler: CValue => IO[CValue] = { input =>
      IO.pure(input match {
        case CValue.CProduct(fields, _) =>
          val text = fields.get("text").collect { case CValue.CString(v) => v }.getOrElse("")
          CValue.CProduct(
            Map("result" -> CValue.CString(text.toUpperCase)),
            Map("result" -> CType.CString)
          )
        case other => other
      })
    }

    withProductExecutorServer(handler) { (_, port) =>
      val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()
      try {
        val stub = pb.ModuleExecutorGrpc.blockingStub(channel)
        val input = CValue.CProduct(
          Map("text" -> CValue.CString("The quick brown fox jumps over the lazy dog")),
          Map("text" -> CType.CString)
        )

        // Warmup
        (1 to WarmupIterations).foreach { _ =>
          val bytes = serializer.serialize(input).toOption.get
          val resp = stub.execute(
            pb.ExecuteRequest(
              moduleName = "benchmark",
              inputData = com.google.protobuf.ByteString.copyFrom(bytes),
              executionId = "bench-breakdown"
            )
          )
          serializer.deserialize(resp.result.outputData.get.toByteArray)
        }

        // Measure with breakdown
        val serializeTimes   = ListBuffer[Double]()
        val grpcTimes        = ListBuffer[Double]()
        val deserializeTimes = ListBuffer[Double]()
        val totalTimes       = ListBuffer[Double]()

        (1 to MeasureIterations).foreach { _ =>
          val tTotal = System.nanoTime()

          // Phase 1: Serialize
          val tSer  = System.nanoTime()
          val bytes = serializer.serialize(input).toOption.get
          serializeTimes += (System.nanoTime() - tSer) / 1e6

          // Phase 2: gRPC call
          val tGrpc = System.nanoTime()
          val resp = stub.execute(
            pb.ExecuteRequest(
              moduleName = "benchmark",
              inputData = com.google.protobuf.ByteString.copyFrom(bytes),
              executionId = "bench-breakdown"
            )
          )
          grpcTimes += (System.nanoTime() - tGrpc) / 1e6

          // Phase 3: Deserialize
          val tDeser = System.nanoTime()
          val _      = serializer.deserialize(resp.result.outputData.get.toByteArray).toOption.get
          deserializeTimes += (System.nanoTime() - tDeser) / 1e6

          totalTimes += (System.nanoTime() - tTotal) / 1e6
        }

        def avg(buf: ListBuffer[Double]): Double = buf.sum / buf.size

        val serAvg   = avg(serializeTimes)
        val grpcAvg  = avg(grpcTimes)
        val deserAvg = avg(deserializeTimes)
        val totalAvg = avg(totalTimes)

        println("\n  Provider Call Breakdown (CProduct, localhost):")
        println(f"    Serialize:   $serAvg%7.3f ms  (${serAvg / totalAvg * 100}%4.1f%%)")
        println(f"    gRPC call:   $grpcAvg%7.3f ms  (${grpcAvg / totalAvg * 100}%4.1f%%)")
        println(f"    Deserialize: $deserAvg%7.3f ms  (${deserAvg / totalAvg * 100}%4.1f%%)")
        println(f"    Total:       $totalAvg%7.3f ms")
        println(f"    Throughput:  ${1000.0 / totalAvg}%7.0f ops/sec")

        allResults += BenchmarkResult(
          "breakdown: serialize",
          "breakdown",
          serAvg,
          serializeTimes.min,
          serializeTimes.max,
          0.0,
          0.0,
          MeasureIterations
        )
        allResults += BenchmarkResult(
          "breakdown: gRPC call",
          "breakdown",
          grpcAvg,
          grpcTimes.min,
          grpcTimes.max,
          0.0,
          0.0,
          MeasureIterations
        )
        allResults += BenchmarkResult(
          "breakdown: deserialize",
          "breakdown",
          deserAvg,
          deserializeTimes.min,
          deserializeTimes.max,
          0.0,
          0.0,
          MeasureIterations
        )
        allResults += BenchmarkResult(
          "breakdown: total",
          "breakdown",
          totalAvg,
          totalTimes.min,
          totalTimes.max,
          0.0,
          1000.0 / totalAvg,
          MeasureIterations
        )

        totalAvg should be < 20.0
      } finally channel.shutdownNow()
    }
  }

  // =====================================================================
  // Suite 4: N-Module Pipeline Simulation
  // =====================================================================

  "Pipeline simulation" should "measure overhead scaling with module count" in {
    val handler: CValue => IO[CValue] = input => IO.pure(input)

    withExecutorServer(handler) { (_, port) =>
      val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()
      try {
        val stub       = pb.ModuleExecutorGrpc.blockingStub(channel)
        val inputBytes = serializer.serialize(smallPayload).toOption.get
        val request = pb.ExecuteRequest(
          moduleName = "benchmark",
          inputData = com.google.protobuf.ByteString.copyFrom(inputBytes),
          executionId = "bench-pipeline"
        )

        // Warmup
        (1 to 20).foreach(_ => stub.execute(request))

        // Simulate N-module pipelines (1, 3, 5 modules per event)
        List(1, 3, 5).foreach { moduleCount =>
          val iterations = 100
          val t0         = System.nanoTime()
          (1 to iterations).foreach { _ =>
            // Each event passes through moduleCount sequential calls
            (1 to moduleCount).foreach(_ => stub.execute(request))
          }
          val elapsed      = (System.nanoTime() - t0) / 1e6
          val avgPerEvent  = elapsed / iterations
          val eventsPerSec = iterations * 1000.0 / elapsed
          val callsPerSec  = eventsPerSec * moduleCount

          val result = BenchmarkResult(
            s"pipeline sim: $moduleCount modules × $iterations events",
            "pipeline",
            avgPerEvent,
            avgPerEvent,
            avgPerEvent,
            0.0,
            eventsPerSec,
            iterations
          )
          allResults += result
          println(
            f"  ${moduleCount}%d modules: $avgPerEvent%7.3f ms/event  " +
              f"$eventsPerSec%7.0f events/sec  ($callsPerSec%.0f calls/sec)"
          )
        }

        succeed
      } finally channel.shutdownNow()
    }
  }

  // =====================================================================
  // Suite 5: Baseline Report
  // =====================================================================

  "Provider baseline report" should "print comprehensive summary" in {
    println("\n" + "=" * 80)
    println("PROVIDER BASELINE BENCHMARK REPORT")
    println("=" * 80)
    println("  Pre-RFC-034 baselines. Re-run after each optimization phase.")
    println()

    val categories = List("serialization", "grpc", "breakdown", "pipeline")
    categories.foreach { cat =>
      val results = allResults.filter(_.category == cat)
      if results.nonEmpty then {
        println(s"  --- $cat ---")
        results.foreach(r => println(r.show))
        println()
      }
    }

    println("  --- RFC-034 Phase Targets ---")
    println("  Phase 0+1 (batch):     ~100x call reduction → ~60-130x throughput gain")
    println("  Phase 2 (streaming):   ~10-20% additional throughput improvement")
    println("  Phase 3 (chaining):    ~2-5x for same-provider linear chains")
    println("=" * 80)

    allResults.size should be > 0
  }
}

/** Test-only ModuleExecutor gRPC implementation for benchmarks. */
private class BenchmarkExecutorImpl(handler: pb.ExecuteRequest => IO[pb.ExecuteResponse])
    extends pb.ModuleExecutorGrpc.ModuleExecutor {

  import cats.effect.unsafe.implicits.global

  override def execute(request: pb.ExecuteRequest): scala.concurrent.Future[pb.ExecuteResponse] = {
    val promise = scala.concurrent.Promise[pb.ExecuteResponse]()
    handler(request).unsafeRunAsync {
      case Right(response) => promise.success(response)
      case Left(error) =>
        promise.success(
          pb.ExecuteResponse(
            result = pb.ExecuteResponse.Result.Error(
              pb.ExecutionError(code = "BENCHMARK_ERROR", message = error.getMessage)
            )
          )
        )
    }
    promise.future
  }

  override def executeBatch(
      request: pb.ExecuteBatchRequest
  ): scala.concurrent.Future[pb.ExecuteBatchResponse] =
    // Stub: batch not used in these tests
    scala.concurrent.Future.successful(pb.ExecuteBatchResponse())

  override def executeBatchStream(
      responseObserver: io.grpc.stub.StreamObserver[pb.ExecuteBatchStreamResponse]
  ): io.grpc.stub.StreamObserver[pb.ExecuteBatchStreamRequest] =
    // Stub: streaming batch not used in these tests
    new io.grpc.stub.StreamObserver[pb.ExecuteBatchStreamRequest] {
      override def onNext(request: pb.ExecuteBatchStreamRequest): Unit = ()
      override def onError(t: Throwable): Unit                         = ()
      override def onCompleted(): Unit                                 = ()
    }
}
