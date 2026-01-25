package io.constellation.benchmark

import cats.effect.unsafe.implicits.global
import io.constellation._
import io.constellation.impl.ModuleRegistryImpl
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

/** Benchmarks for ModuleRegistry lookup performance.
  *
  * Measures:
  * - Exact name lookup
  * - Short name lookup (without prefix)
  * - Prefixed query lookup (query has prefix, module doesn't)
  * - Batch registration performance
  *
  * Run with: sbt "runtime/testOnly *ModuleRegistryBenchmark"
  */
class ModuleRegistryBenchmark extends AnyFlatSpec with Matchers {

  val WarmupIterations = 100
  val MeasureIterations = 1000

  // -------------------------------------------------------------------------
  // Timing Utilities
  // -------------------------------------------------------------------------

  case class TimingResult(
      name: String,
      avgNs: Double,
      minNs: Double,
      maxNs: Double,
      stdDevNs: Double,
      opsPerSec: Double
  ) {
    def toConsoleString: String =
      f"$name%-40s: $avgNs%8.0fns (±$stdDevNs%.0f) [$minNs%.0f - $maxNs%.0f] ${opsPerSec / 1000}%.1fK ops/s"
  }

  def measureWithWarmup[A](name: String, warmup: Int, iterations: Int)(op: => A): TimingResult = {
    // Warmup
    var i = 0
    while (i < warmup) {
      op
      i += 1
    }

    // Measure
    val timings = mutable.ArrayBuffer[Double]()
    i = 0
    while (i < iterations) {
      val start = System.nanoTime()
      op
      val end = System.nanoTime()
      timings += (end - start).toDouble
      i += 1
    }

    val sorted = timings.sorted
    val avg = timings.sum / timings.length
    val min = sorted.head
    val max = sorted.last
    val variance = timings.map(t => math.pow(t - avg, 2)).sum / timings.length
    val stdDev = math.sqrt(variance)
    val opsPerSec = if (avg > 0) 1e9 / avg else 0

    TimingResult(name, avg, min, max, stdDev, opsPerSec)
  }

  // -------------------------------------------------------------------------
  // Test Fixtures
  // -------------------------------------------------------------------------

  case class TestInput(x: Long)
  case class TestOutput(result: Long)

  private def createTestModule(name: String): Module.Uninitialized =
    ModuleBuilder
      .metadata(name, s"Test module $name", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x * 2))
      .build

  private def createRegistryWithModules(count: Int): ModuleRegistryImpl = {
    val modules = (1 to count).map { i =>
      val name = s"dag$i.Module$i"
      name -> createTestModule(name)
    }.toList

    ModuleRegistryImpl.withModules(modules).unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // Benchmarks
  // -------------------------------------------------------------------------

  "Exact name lookup" should "be fast" in {
    println("\n" + "=" * 70)
    println("MODULE REGISTRY LOOKUP BENCHMARK")
    println("=" * 70)

    val registry = createRegistryWithModules(100)

    val result = measureWithWarmup("exact_lookup_100modules", WarmupIterations, MeasureIterations) {
      registry.get("dag50.Module50").unsafeRunSync()
    }
    println(result.toConsoleString)

    // IO overhead adds ~30-50µs, actual map lookup is O(1)
    result.avgNs should be < 100000.0 // 100µs including IO overhead
  }

  "Short name lookup" should "be fast via index" in {
    val modules = List(
      "Uppercase" -> createTestModule("Uppercase"),
      "Lowercase" -> createTestModule("Lowercase"),
      "Trim" -> createTestModule("Trim")
    )
    val registry = ModuleRegistryImpl.withModules(modules).unsafeRunSync()

    val result = measureWithWarmup("short_name_lookup", WarmupIterations, MeasureIterations) {
      registry.get("Uppercase").unsafeRunSync()
    }
    println(result.toConsoleString)

    // IO overhead adds ~30-50µs
    result.avgNs should be < 100000.0
  }

  "Prefixed query lookup" should "find module by stripping prefix" in {
    val modules = List("Uppercase" -> createTestModule("Uppercase"))
    val registry = ModuleRegistryImpl.withModules(modules).unsafeRunSync()

    val result = measureWithWarmup("prefixed_query_lookup", WarmupIterations, MeasureIterations) {
      registry.get("test.Uppercase").unsafeRunSync()
    }
    println(result.toConsoleString)

    // Slightly slower due to fallback, but still fast (IO overhead dominates)
    result.avgNs should be < 100000.0 // 100µs including IO overhead
  }

  "Large registry lookup" should "scale well" in {
    println("\n" + "=" * 70)
    println("SCALING TEST (10, 100, 500, 1000 modules)")
    println("=" * 70)

    val sizes = List(10, 100, 500, 1000)
    val results = sizes.map { size =>
      val registry = createRegistryWithModules(size)
      val targetName = s"dag${size / 2}.Module${size / 2}"

      measureWithWarmup(s"lookup_${size}_modules", WarmupIterations / 2, MeasureIterations / 2) {
        registry.get(targetName).unsafeRunSync()
      }
    }

    results.foreach(r => println(r.toConsoleString))

    // All sizes should have similar lookup time (O(1))
    val maxRatio = results.last.avgNs / results.head.avgNs
    println(f"\nScaling ratio (1000/10 modules): $maxRatio%.2fx")
    maxRatio should be < 3.0 // Should not scale linearly
  }

  "Batch registration" should "be efficient" in {
    println("\n" + "=" * 70)
    println("BATCH REGISTRATION BENCHMARK")
    println("=" * 70)

    val modules100 = (1 to 100).map(i => s"Module$i" -> createTestModule(s"Module$i")).toList
    val modules500 = (1 to 500).map(i => s"Module$i" -> createTestModule(s"Module$i")).toList

    val result100 = measureWithWarmup("batch_register_100", 5, 20) {
      ModuleRegistryImpl.withModules(modules100).unsafeRunSync()
    }
    println(result100.toConsoleString)

    val result500 = measureWithWarmup("batch_register_500", 5, 20) {
      ModuleRegistryImpl.withModules(modules500).unsafeRunSync()
    }
    println(result500.toConsoleString)

    // Should be fast enough for startup
    result100.avgNs should be < 10_000_000.0 // 10ms
    result500.avgNs should be < 50_000_000.0 // 50ms
  }

  "Index helpers" should "work correctly" in {
    println("\n" + "=" * 70)
    println("INDEX HELPER METHODS")
    println("=" * 70)

    val modules = List(
      "Uppercase" -> createTestModule("Uppercase"),
      "dag1.Transform" -> createTestModule("dag1.Transform"),
      "dag2.Transform" -> createTestModule("dag2.Transform") // Conflict on "Transform"
    )
    val registry = ModuleRegistryImpl.withModules(modules).unsafeRunSync()

    val size = registry.size.unsafeRunSync()
    val indexSize = registry.indexSize.unsafeRunSync()

    println(s"Registered modules: $size")
    println(s"Indexed names:      $indexSize")
    println(s"Index expansion:    ${indexSize.toDouble / size}x")

    size shouldBe 3
    // Index should have: Uppercase, dag1.Transform, dag2.Transform, Transform (first wins)
    indexSize shouldBe 4

    // Verify conflict resolution - "Transform" should resolve to first registered
    val transform = registry.get("Transform").unsafeRunSync()
    transform shouldBe defined
    transform.get.spec.name shouldBe "dag1.Transform"

    // Full names still work
    val dag2 = registry.get("dag2.Transform").unsafeRunSync()
    dag2 shouldBe defined
    dag2.get.spec.name shouldBe "dag2.Transform"
  }

  "Benchmark summary" should "print final report" in {
    println("\n" + "=" * 70)
    println("MODULE REGISTRY BENCHMARK SUMMARY")
    println("=" * 70)
    println("""
      |Performance Characteristics:
      | - Exact lookup:    O(1) - single map lookup via index
      | - Short name:      O(1) - pre-indexed at registration
      | - Prefixed query:  O(1) - fallback strip + lookup
      |
      |Optimizations Applied:
      | - Pre-computed name index (no string ops at lookup time)
      | - Short name indexing (first registration wins on conflict)
      | - Batch registration via registerAll
      |
      |Expected Gains:
      | - <1µs per lookup (vs string splitting overhead)
      | - Reduced GC pressure (no allocations per lookup)
      | - Consistent O(1) regardless of registry size
      """.stripMargin)

    succeed
  }
}
