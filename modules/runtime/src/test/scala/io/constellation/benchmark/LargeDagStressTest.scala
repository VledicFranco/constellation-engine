package io.constellation.benchmark

import java.util.UUID

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*

import io.constellation.*
import io.constellation.execution.GlobalScheduler
import io.constellation.spi.ConstellationBackends

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Large DAG stress tests (RFC-013 Phase 5.1)
  *
  * Validates that the engine handles large DAGs within time and memory bounds, produces
  * deterministic results, and handles high-concurrency scheduling.
  *
  * Run with: sbt "runtime/testOnly *LargeDagStressTest"
  */
class LargeDagStressTest extends AnyFlatSpec with Matchers {

  // -------------------------------------------------------------------------
  // Test Modules
  // -------------------------------------------------------------------------

  private case class TextInput(text: String)
  private case class TextOutput(result: String)

  private def createUppercaseModule(): Module.Uninitialized =
    ModuleBuilder
      .metadata("Uppercase", "Converts text to uppercase", 1, 0)
      .implementationPure[TextInput, TextOutput](in => TextOutput(in.text.toUpperCase))
      .build

  private def createLowercaseModule(): Module.Uninitialized =
    ModuleBuilder
      .metadata("Lowercase", "Converts text to lowercase", 1, 0)
      .implementationPure[TextInput, TextOutput](in => TextOutput(in.text.toLowerCase))
      .build

  // -------------------------------------------------------------------------
  // DAG Generators
  // -------------------------------------------------------------------------

  /** Create a sequential chain DAG with N modules: input -> M1 -> M2 -> ... -> Mn -> output */
  private def createChainDag(nodeCount: Int): (DagSpec, Map[UUID, Module.Uninitialized]) = {
    val moduleIds = (1 to nodeCount).map(_ => UUID.randomUUID()).toList
    val dataIds   = (1 to (nodeCount + 1)).map(_ => UUID.randomUUID()).toList

    val moduleSpecs = moduleIds.zipWithIndex.map { case (id, idx) =>
      val name = if idx % 2 == 0 then "Uppercase" else "Lowercase"
      id -> ModuleNodeSpec(
        metadata = ComponentMetadata(name, s"M$idx", List.empty, 1, 0),
        consumes = Map("text" -> CType.CString),
        produces = Map("result" -> CType.CString)
      )
    }.toMap

    val dataSpecs = dataIds.zipWithIndex.map { case (id, idx) =>
      val portMap = if idx == 0 then {
        Map(id -> "input", moduleIds.head -> "text")
      } else if idx < moduleIds.length then {
        Map(moduleIds(idx - 1) -> "result", moduleIds(idx) -> "text")
      } else {
        Map(moduleIds.last -> "result")
      }
      id -> DataNodeSpec(s"data$idx", portMap, CType.CString)
    }.toMap

    val inEdges = dataIds.init.zipWithIndex.map { case (dataId, idx) =>
      (dataId, moduleIds(idx))
    }.toSet

    val outEdges = moduleIds.zipWithIndex.map { case (moduleId, idx) =>
      (moduleId, dataIds(idx + 1))
    }.toSet

    val dag = DagSpec(
      metadata = ComponentMetadata.empty(s"ChainDag$nodeCount"),
      modules = moduleSpecs,
      data = dataSpecs,
      inEdges = inEdges,
      outEdges = outEdges,
      declaredOutputs = List(s"data$nodeCount"),
      outputBindings = Map(s"data$nodeCount" -> dataIds.last)
    )

    val modules = moduleIds.zipWithIndex.map { case (id, idx) =>
      if idx   % 2 == 0 then id -> createUppercaseModule()
      else id -> createLowercaseModule()
    }.toMap

    (dag, modules)
  }

  /** Create a wide DAG with W parallel branches of depth D. Each branch: input -> M1 -> M2 -> ...
    * -> Md -> output_i
    */
  private def createWideDag(width: Int, depth: Int): (DagSpec, Map[UUID, Module.Uninitialized]) = {
    val inputDataId    = UUID.randomUUID()
    var allModuleSpecs = Map.empty[UUID, ModuleNodeSpec]
    var allDataSpecs   = Map.empty[UUID, DataNodeSpec]
    var allInEdges     = Set.empty[(UUID, UUID)]
    var allOutEdges    = Set.empty[(UUID, UUID)]
    var allModules     = Map.empty[UUID, Module.Uninitialized]
    var outputBindings = Map.empty[String, UUID]

    val firstModuleIds = scala.collection.mutable.ListBuffer[UUID]()

    for branch <- 0 until width do {
      val branchModuleIds = (1 to depth).map(_ => UUID.randomUUID()).toList
      val branchDataIds   = (1 to depth).map(_ => UUID.randomUUID()).toList
      val outputDataId    = UUID.randomUUID()

      firstModuleIds += branchModuleIds.head

      branchModuleIds.zipWithIndex.foreach { case (id, idx) =>
        val name = if (branch + idx) % 2 == 0 then "Uppercase" else "Lowercase"
        allModuleSpecs += id -> ModuleNodeSpec(
          metadata = ComponentMetadata(name, s"M_${branch}_$idx", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
        allModules += id -> (if (branch + idx) % 2 == 0 then createUppercaseModule()
                             else createLowercaseModule())
      }

      branchDataIds.zipWithIndex.foreach { case (id, idx) =>
        if idx < depth - 1 then {
          allDataSpecs += id -> DataNodeSpec(
            s"mid_${branch}_$idx",
            Map(branchModuleIds(idx) -> "result", branchModuleIds(idx + 1) -> "text"),
            CType.CString
          )
          allInEdges += ((id, branchModuleIds(idx + 1)))
          allOutEdges += ((branchModuleIds(idx), id))
        }
      }

      allDataSpecs += outputDataId -> DataNodeSpec(
        s"output_$branch",
        Map(branchModuleIds.last -> "result"),
        CType.CString
      )
      allOutEdges += ((branchModuleIds.last, outputDataId))
      outputBindings += (s"output_$branch" -> outputDataId)

      allInEdges += ((inputDataId, branchModuleIds.head))
    }

    val inputNicknames: Map[UUID, String] =
      Map(inputDataId -> "input") ++ firstModuleIds.map(id => id -> "text").toMap
    allDataSpecs += inputDataId -> DataNodeSpec("input", inputNicknames, CType.CString)

    val dag = DagSpec(
      metadata = ComponentMetadata.empty(s"WideDag_${width}x$depth"),
      modules = allModuleSpecs,
      data = allDataSpecs,
      inEdges = allInEdges,
      outEdges = allOutEdges,
      declaredOutputs = (0 until width).map(i => s"output_$i").toList,
      outputBindings = outputBindings
    )

    (dag, allModules)
  }

  // -------------------------------------------------------------------------
  // Helper: Run a DAG and return the result
  // -------------------------------------------------------------------------

  private def runDag(
      dag: DagSpec,
      modules: Map[UUID, Module.Uninitialized],
      inputs: Map[String, CValue]
  ): Runtime.State =
    Runtime.run(dag, inputs, modules).unsafeRunSync()

  // -------------------------------------------------------------------------
  // 5.1: 100-node DAG
  // -------------------------------------------------------------------------

  "Runtime" should "execute a 100-node chain DAG within 5 seconds" in {
    val (dag, modules) = createChainDag(100)
    val inputs         = Map("input" -> CValue.CString("stress test"))

    val start   = System.nanoTime()
    val state   = runDag(dag, modules, inputs)
    val elapsed = (System.nanoTime() - start) / 1e6

    println(f"100-node chain DAG: $elapsed%.1f ms")
    elapsed should be < 5000.0
    state.latency.isDefined shouldBe true
  }

  // -------------------------------------------------------------------------
  // 5.1: 500-node DAG
  // -------------------------------------------------------------------------

  it should "execute a 500-node chain DAG within 30 seconds" in {
    val (dag, modules) = createChainDag(500)
    val inputs         = Map("input" -> CValue.CString("stress test"))

    val start   = System.nanoTime()
    val state   = runDag(dag, modules, inputs)
    val elapsed = (System.nanoTime() - start) / 1e6

    println(f"500-node chain DAG: $elapsed%.1f ms")
    elapsed should be < 30000.0
    state.latency.isDefined shouldBe true
  }

  // -------------------------------------------------------------------------
  // 5.1: Wide DAG (parallelism stress)
  // -------------------------------------------------------------------------

  it should "execute a wide DAG (50 branches x 10 depth = 500 modules) within 30 seconds" in {
    val (dag, modules) = createWideDag(width = 50, depth = 10)
    val inputs         = Map("input" -> CValue.CString("parallel stress"))

    val start   = System.nanoTime()
    val state   = runDag(dag, modules, inputs)
    val elapsed = (System.nanoTime() - start) / 1e6

    println(f"Wide DAG 50x10: $elapsed%.1f ms")
    elapsed should be < 30000.0
    state.latency.isDefined shouldBe true
  }

  // -------------------------------------------------------------------------
  // 5.1: Memory stays bounded
  // -------------------------------------------------------------------------

  it should "keep memory bounded during large DAG execution" in {
    System.gc()
    Thread.sleep(100)
    val jrt        = java.lang.Runtime.getRuntime
    val heapBefore = jrt.totalMemory() - jrt.freeMemory()

    // Execute a 200-node DAG 10 times
    (1 to 10).foreach { _ =>
      val (dag, modules) = createChainDag(200)
      val inputs         = Map("input" -> CValue.CString("memory test"))
      runDag(dag, modules, inputs)
    }

    System.gc()
    Thread.sleep(100)
    val heapAfter = jrt.totalMemory() - jrt.freeMemory()

    val heapGrowthMB = (heapAfter - heapBefore).toDouble / (1024 * 1024)
    println(f"Heap growth after 10x 200-node DAGs: $heapGrowthMB%.1f MB")

    // Allow generous margin but verify no unbounded growth
    heapGrowthMB should be < 200.0
  }

  // -------------------------------------------------------------------------
  // 5.1: Results are deterministic
  // -------------------------------------------------------------------------

  it should "produce deterministic results for the same DAG" in {
    val (dag, modules) = createChainDag(50)
    val inputs         = Map("input" -> CValue.CString("determinism test"))

    val results = (1 to 5).map { _ =>
      val state = runDag(dag, modules, inputs)
      state.data.map { case (k, v) => (k, v.value) }
    }

    // All runs should produce identical data maps
    results.sliding(2).foreach { pair =>
      pair.head shouldBe pair.last
    }
  }

  // -------------------------------------------------------------------------
  // 5.1: Scheduler handles 1000 concurrent submissions
  // -------------------------------------------------------------------------

  "Scheduler" should "handle 1000 concurrent submissions" in {
    GlobalScheduler
      .bounded(maxConcurrency = 16)
      .use { scheduler =>
        for {
          results <- (1 to 1000).toList.map { i =>
            scheduler.submit(50, IO.pure(i * 2))
          }.parSequence
        } yield {
          results.size shouldBe 1000
          results.toSet shouldBe (1 to 1000).map(_ * 2).toSet
        }
      }
      .unsafeRunSync()
  }

  it should "handle 1000 concurrent submissions with bounded scheduler and queue" in {
    GlobalScheduler
      .bounded(maxConcurrency = 8, maxQueueSize = 0)
      .use { scheduler =>
        for {
          results <- (1 to 1000).toList.map { i =>
            scheduler.submit(i % 100, IO.pure(i))
          }.parSequence
        } yield {
          results.size shouldBe 1000
          results.toSet shouldBe (1 to 1000).toSet
        }
      }
      .unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // 5.1: Compiler handles large programs
  // -------------------------------------------------------------------------

  "Compiler via DAG generation" should "handle 1000-node chain DAG construction in under 1 second" in {
    val start          = System.nanoTime()
    val (dag, modules) = createChainDag(1000)
    val elapsed        = (System.nanoTime() - start) / 1e6

    println(f"1000-node DAG construction: $elapsed%.1f ms")

    dag.modules.size shouldBe 1000
    dag.data.size shouldBe 1001
    modules.size shouldBe 1000
    elapsed should be < 1000.0
  }
}
