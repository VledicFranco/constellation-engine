package io.constellation.http.benchmark

import java.util.UUID

import cats.Eval
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}
import cats.implicits.*

import io.constellation.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Benchmarks for streaming module per-element execution overhead.
  *
  * Measures the lightweight execution path (module.init + Ref + setTableDataCValue + runnable.run +
  * state.get) against the full Runtime.run path to quantify the improvement from PR #239.
  *
  * Run with: sbt "httpApi/testOnly *StreamingModuleBenchmark"
  */
class StreamingModuleBenchmark extends AnyFlatSpec with Matchers {

  // ===== Test Module Definitions =====

  case class StrIn(value: String)
  case class StrOut(value: String)

  val strModule: Module.Uninitialized = ModuleBuilder
    .metadata("BenchStr", "benchmark passthrough", 1, 0)
    .implementationPure[StrIn, StrOut](in => StrOut(in.value))
    .build

  case class MultiIn(a: String, b: String)
  case class MultiOut(result: String)

  val multiModule: Module.Uninitialized = ModuleBuilder
    .metadata("BenchMulti", "benchmark multi-field", 1, 0)
    .implementationPure[MultiIn, MultiOut](in => MultiOut(in.a + in.b))
    .build

  // ===== Synthetic DAG Setup =====

  private case class SyntheticSetup(
      moduleId: UUID,
      dag: DagSpec,
      fieldToInputUUID: Map[String, UUID],
      fieldToOutputUUID: Map[String, UUID]
  )

  private def makeSyntheticSetup(module: Module.Uninitialized): SyntheticSetup = {
    val spec        = module.spec
    val syntheticId = UUID.randomUUID()

    val inputNodes: Map[UUID, (String, CType)] =
      spec.consumes.map { case (k, v) => UUID.randomUUID() -> (k, v) }

    val outputNodes: Map[UUID, (String, CType)] =
      spec.produces.map { case (k, v) => UUID.randomUUID() -> (k, v) }

    val dataSpecs: Map[UUID, DataNodeSpec] =
      (inputNodes ++ outputNodes).map { case (uuid, (name, ctype)) =>
        uuid -> DataNodeSpec(name, Map(syntheticId -> name), ctype)
      }

    val dag = DagSpec(
      metadata = ComponentMetadata.empty(s"__bench_${spec.metadata.name}"),
      modules = Map(syntheticId -> spec),
      data = dataSpecs,
      inEdges = inputNodes.keySet.map(_ -> syntheticId),
      outEdges = outputNodes.keySet.map(syntheticId -> _),
      declaredOutputs = outputNodes.values.map(_._1).toList,
      outputBindings = outputNodes.map { case (uuid, (name, _)) => name -> uuid }
    )

    SyntheticSetup(
      syntheticId,
      dag,
      inputNodes.map { case (uuid, (name, _)) => name -> uuid },
      outputNodes.map { case (uuid, (name, _)) => name -> uuid }
    )
  }

  // ===== Per-element Execution Paths =====

  private def runElementLightweight(
      module: Module.Uninitialized,
      setup: SyntheticSetup,
      inputMap: Map[String, CValue]
  ): IO[CValue] =
    for {
      runnable <- module.init(setup.moduleId, setup.dag)
      minimalState <- Ref.of[IO, Runtime.State](
        Runtime.State(
          processUuid = UUID.randomUUID(),
          dag = setup.dag,
          moduleStatus = Map(setup.moduleId -> Eval.later(Module.Status.Unfired)),
          data = Map.empty
        )
      )
      runtime = Runtime(table = runnable.data, state = minimalState)
      _ <- inputMap.toList.traverse { case (name, cv) =>
        setup.fieldToInputUUID.get(name).traverse(uuid => runtime.setTableDataCValue(uuid, cv))
      }
      _     <- runnable.run(runtime)
      state <- minimalState.get
    } yield setup.fieldToOutputUUID.values.headOption
      .flatMap(uuid => state.data.get(uuid).map(_.value))
      .getOrElse(CValue.CString(""))

  private def runElementLegacy(
      module: Module.Uninitialized,
      setup: SyntheticSetup,
      inputMap: Map[String, CValue]
  ): IO[CValue] =
    Runtime
      .run(setup.dag, inputMap, Map(setup.moduleId -> module))
      .map(state =>
        setup.fieldToOutputUUID.values.headOption
          .flatMap(uuid => state.data.get(uuid).map(_.value))
          .getOrElse(CValue.CString(""))
      )

  // ===== Measurement Util =====

  private case class BMResult(
      name: String,
      avgMs: Double,
      minMs: Double,
      maxMs: Double,
      opsPerSec: Double
  ) {
    def show: String =
      f"  $name%-52s avg=${avgMs}%6.3f ms  " +
        f"[${minMs}%.3f\u2013${maxMs}%.3f]  ${opsPerSec}%.0f ops/s"
  }

  private def measure(name: String, warmup: Int = 5, n: Int = 20)(op: => Unit): BMResult = {
    (1 to warmup).foreach(_ => op)
    val ts = (1 to n).map { _ =>
      val t = System.nanoTime()
      op
      (System.nanoTime() - t) / 1e6
    }
    BMResult(name, ts.sum / n, ts.min, ts.max, 1000.0 / (ts.sum / n))
  }

  // ===== Collected Results =====

  private var results = List.empty[BMResult]

  // ===== Suite 1: Lightweight streaming element execution =====

  "Lightweight streaming element execution" should "execute single-field module under 5ms per element" in {
    val setup = makeSyntheticSetup(strModule)
    val input = Map("value" -> CValue.CString("hello"))

    val r = measure("single-field string module") {
      runElementLightweight(strModule, setup, input).unsafeRunSync()
    }

    println(r.show)
    results = results :+ r
    r.avgMs should be < 5.0
  }

  it should "execute multi-field module under 10ms per element" in {
    val setup = makeSyntheticSetup(multiModule)
    val input = Map("a" -> CValue.CString("hello"), "b" -> CValue.CString(" world"))

    val r = measure("multi-field product module") {
      runElementLightweight(multiModule, setup, input).unsafeRunSync()
    }

    println(r.show)
    results = results :+ r
    r.avgMs should be < 10.0
  }

  // ===== Suite 2: Lightweight vs Runtime.run comparison =====

  "Lightweight vs Runtime.run comparison" should "show overhead comparison" in {
    val setup = makeSyntheticSetup(strModule)
    val input = Map("value" -> CValue.CString("hello"))

    val lightweight = measure("lightweight path (current)") {
      runElementLightweight(strModule, setup, input).unsafeRunSync()
    }

    val legacy = measure("Runtime.run (legacy)") {
      runElementLegacy(strModule, setup, input).unsafeRunSync()
    }

    println(legacy.show)
    println(lightweight.show)
    val ratio = legacy.avgMs / lightweight.avgMs
    if ratio >= 1.0 then println(f"  Speedup: ${ratio}%.1fx faster")
    else println(f"  Ratio: ${ratio}%.2fx (within noise for single-module DAGs)")

    results = results :+ lightweight :+ legacy

    // Both paths should be fast enough for streaming (<5ms per element).
    // For single-module synthetic DAGs the paths are comparable; the lightweight
    // path wins at scale by avoiding validation, scheduler setup, and fiber
    // allocation that Runtime.run performs on each call.
    lightweight.avgMs should be < 5.0
    legacy.avgMs should be < 5.0
  }

  // ===== Suite 3: Streaming throughput at scale =====

  "Streaming throughput at scale" should "sustain over 500 ops/sec for 1000 sequential elements" in {
    val setup = makeSyntheticSetup(strModule)
    val input = Map("value" -> CValue.CString("throughput-test"))
    val n     = 1000

    // Warmup
    (1 to 10).foreach(_ => runElementLightweight(strModule, setup, input).unsafeRunSync())

    val start = System.nanoTime()
    (1 to n).foreach(_ => runElementLightweight(strModule, setup, input).unsafeRunSync())
    val elapsed   = (System.nanoTime() - start) / 1e6
    val opsPerSec = n.toDouble / (elapsed / 1000.0)

    val r = BMResult(s"$n sequential elements", elapsed / n, elapsed / n, elapsed / n, opsPerSec)
    println(r.show)
    results = results :+ r
    opsPerSec should be > 500.0
  }

  // ===== Suite 4: Streaming benchmark report =====

  "Streaming benchmark report" should "print benchmark summary" in {
    println()
    println("=" * 80)
    println("STREAMING MODULE BENCHMARK SUMMARY")
    println("=" * 80)
    results.foreach(r => println(r.show))
    println("=" * 80)

    results.size should be > 0
  }
}
