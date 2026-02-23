package io.constellation.http

import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*

import io.constellation.*
import io.constellation.stream.config.{SinkBinding, SourceBinding, StreamPipelineConfig}
import io.constellation.stream.connector.{ConnectorRegistry, MemoryConnector}
import io.constellation.stream.error.StreamErrorStrategy
import io.constellation.stream.join.JoinStrategy
import io.constellation.stream.{StreamCompiler, StreamGraph, StreamOptions}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** RFC-034 Phase 1: Tests for batch function extraction and threading in StreamRoutes. */
class StreamRoutesBatchFunctionTest extends AnyFlatSpec with Matchers {

  // ===== Helper: Mock Constellation with batch functions =====

  private class MockConstellationWithBatch(
      batchFns: Map[String, List[CValue] => IO[List[Either[Throwable, CValue]]]]
  ) extends Constellation {
    override def getModules: IO[List[ModuleNodeSpec]]                            = IO.pure(Nil)
    override def getModuleByName(name: String): IO[Option[Module.Uninitialized]] = IO.pure(None)
    override def setModule(module: Module.Uninitialized): IO[Unit]               = IO.unit
    override def removeModule(name: String): IO[Unit]                            = IO.unit
    override def PipelineStore: PipelineStore                                    = ???
    override def run(
        loaded: LoadedPipeline,
        inputs: Map[String, CValue],
        options: ExecutionOptions
    ): IO[DataSignature] = ???
    override def run(
        ref: String,
        inputs: Map[String, CValue],
        options: ExecutionOptions
    ): IO[DataSignature] = ???
    override def resumeFromStore(
        handle: SuspensionHandle,
        additionalInputs: Map[String, CValue],
        resolvedNodes: Map[String, CValue],
        options: ExecutionOptions
    ): IO[DataSignature] = ???

    override def getBatchFunctions(
        moduleNames: List[String]
    ): IO[Map[String, List[CValue] => IO[List[Either[Throwable, CValue]]]]] =
      IO.pure(batchFns.filter { case (k, _) => moduleNames.contains(k) })
  }

  // ===== Helper: Create simple test DAG =====

  private def createTestDag(moduleName: String): DagSpec = {
    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    DagSpec(
      metadata = ComponentMetadata("test", "test dag", Nil, 1, 0),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata(moduleName, "test module", Nil, 1, 0),
          consumes = Map("input" -> CType.CString),
          produces = Map("output" -> CType.CString)
        )
      ),
      data = Map(
        inputId  -> DataNodeSpec("input", Map(moduleId -> "input"), CType.CString),
        outputId -> DataNodeSpec("output", Map(moduleId -> "output"), CType.CString)
      ),
      inEdges = Set(inputId -> moduleId),
      outEdges = Set(moduleId -> outputId),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> outputId)
    )
  }

  private def createBatchFn(name: String): List[CValue] => IO[List[Either[Throwable, CValue]]] = {
    inputs =>
      IO.pure(
        inputs.map { input =>
          Right(
            input match {
              case CValue.CString(s) => CValue.CString(s"$name:$s")
              case other             => other
            }
          )
        }
      )
  }

  // ===== Tests =====

  "getBatchFunctions" should "retrieve batch functions by module names" in {
    val batchFns = Map(
      "namespace.Module1" -> createBatchFn("Module1"),
      "namespace.Module2" -> createBatchFn("Module2")
    )

    val constellation = new MockConstellationWithBatch(batchFns)

    val result = constellation
      .getBatchFunctions(List("namespace.Module1"))
      .unsafeRunSync()

    result should have size 1
    result.keys.toList should contain("namespace.Module1")
  }

  it should "return empty map when no matching modules" in {
    val batchFns      = Map("namespace.Module1" -> createBatchFn("Module1"))
    val constellation = new MockConstellationWithBatch(batchFns)

    val result = constellation
      .getBatchFunctions(List("namespace.NonExistent"))
      .unsafeRunSync()

    result should be(empty)
  }

  it should "handle multiple requested modules" in {
    val batchFns = Map(
      "namespace.Module1" -> createBatchFn("Module1"),
      "namespace.Module2" -> createBatchFn("Module2"),
      "namespace.Module3" -> createBatchFn("Module3")
    )

    val constellation = new MockConstellationWithBatch(batchFns)

    val result = constellation
      .getBatchFunctions(List("namespace.Module1", "namespace.Module3"))
      .unsafeRunSync()

    result should have size 2
    result.keys.toList should contain theSameElementsAs List(
      "namespace.Module1",
      "namespace.Module3"
    )
  }

  it should "default to empty map in base Constellation trait" in {
    val constellation = new Constellation {
      override def getModules: IO[List[ModuleNodeSpec]]                            = IO.pure(Nil)
      override def getModuleByName(name: String): IO[Option[Module.Uninitialized]] = IO.pure(None)
      override def setModule(module: Module.Uninitialized): IO[Unit]               = IO.unit
      override def PipelineStore: PipelineStore                                    = ???
      override def run(
          loaded: LoadedPipeline,
          inputs: Map[String, CValue],
          options: ExecutionOptions
      ): IO[DataSignature] = ???
      override def run(
          ref: String,
          inputs: Map[String, CValue],
          options: ExecutionOptions
      ): IO[DataSignature] = ???
      override def resumeFromStore(
          handle: SuspensionHandle,
          additionalInputs: Map[String, CValue],
          resolvedNodes: Map[String, CValue],
          options: ExecutionOptions
      ): IO[DataSignature] = ???
    }

    val result = constellation
      .getBatchFunctions(List("any.Module"))
      .unsafeRunSync()

    result should be(empty)
  }

  "Batch function mapping" should "map batch functions correctly" in {
    val batchFns = Map(
      "MyModule" -> createBatchFn("MyModule")
    )

    val constellation = new MockConstellationWithBatch(batchFns)

    val batchFnMap = constellation
      .getBatchFunctions(List("MyModule"))
      .unsafeRunSync()

    val testFn    = batchFnMap("MyModule")
    val testInput = List(CValue.CString("test"))
    val results   = testFn(testInput).unsafeRunSync()

    results should have length 1
    results.head.map(_.asInstanceOf[CValue.CString].value) should be(Right("MyModule:test"))
  }

  "Batch function filtering" should "filter to only requested modules" in {
    val batchFns = Map(
      "mod.A" -> createBatchFn("A"),
      "mod.B" -> createBatchFn("B"),
      "mod.C" -> createBatchFn("C")
    )

    val constellation = new MockConstellationWithBatch(batchFns)

    val result = constellation
      .getBatchFunctions(List("mod.B"))
      .unsafeRunSync()

    result.keys.toList should equal(List("mod.B"))
  }

  "Batch function integration" should "work end-to-end with StreamCompiler" in {
    val dagSpec  = createTestDag("TestModule")
    val registry = ConnectorRegistry.empty
    val moduleId = dagSpec.modules.keys.head

    // Create constellation with batch function
    val batchFns = Map(
      "TestModule" -> createBatchFn("TestModule")
    )
    val constellation = new MockConstellationWithBatch(batchFns)

    // Simulate StreamRoutes flow
    val batchFnMap = constellation
      .getBatchFunctions(List("TestModule"))
      .unsafeRunSync()

    // Map module names to UUIDs
    val batchModulesByUuid = Map(moduleId -> batchFnMap("TestModule"))

    // Pass to StreamCompiler
    val graph = StreamCompiler
      .wire(
        dagSpec,
        registry,
        Map(moduleId -> ((s: CValue) => IO.pure(s))),
        batchModules = batchModulesByUuid
      )
      .unsafeRunSync()

    graph shouldBe a[StreamGraph]
  }

  "Batch function chaining" should "chain multiple batch functions" in {
    val batchFns = Map(
      "Module1" -> createBatchFn("Batch1"),
      "Module2" -> createBatchFn("Batch2")
    )

    val constellation = new MockConstellationWithBatch(batchFns)

    val moduleNames = List("Module1", "Module2")
    val result = constellation
      .getBatchFunctions(moduleNames)
      .unsafeRunSync()

    result should have size 2

    // Test that each function can be called independently
    val fn1Results = result("Module1")(List(CValue.CString("x"))).unsafeRunSync()
    val fn2Results = result("Module2")(List(CValue.CString("y"))).unsafeRunSync()

    fn1Results.head.map(_.asInstanceOf[CValue.CString].value) should be(Right("Batch1:x"))
    fn2Results.head.map(_.asInstanceOf[CValue.CString].value) should be(Right("Batch2:y"))
  }

  "Batch functions with error handling" should "preserve error information" in {
    val errorBatchFn: List[CValue] => IO[List[Either[Throwable, CValue]]] = { inputs =>
      IO.pure(
        inputs.map { _ =>
          Left(new RuntimeException("Batch processing error"))
        }
      )
    }

    val batchFns      = Map("ErrorModule" -> errorBatchFn)
    val constellation = new MockConstellationWithBatch(batchFns)

    val result = constellation
      .getBatchFunctions(List("ErrorModule"))
      .unsafeRunSync()

    val testResults = result("ErrorModule")(List(CValue.CString("test")))
      .unsafeRunSync()

    testResults.head should be(a[Left[_, _]])
    testResults.head.asInstanceOf[Left[Throwable, _]].value.getMessage should include(
      "Batch processing error"
    )
  }

  "Batch function extraction" should "handle empty module list" in {
    val batchFns      = Map("Module1" -> createBatchFn("Module1"))
    val constellation = new MockConstellationWithBatch(batchFns)

    val result = constellation
      .getBatchFunctions(List.empty)
      .unsafeRunSync()

    result should be(empty)
  }

  it should "handle large batch function maps" in {
    val batchFns = (1 to 100).map { i =>
      s"Module$i" -> createBatchFn(s"Module$i")
    }.toMap

    val constellation = new MockConstellationWithBatch(batchFns)

    val requestedModules = (1 to 50).map(i => s"Module$i").toList

    val result = constellation
      .getBatchFunctions(requestedModules)
      .unsafeRunSync()

    result should have size 50
  }

  "Batch function performance" should "extract functions quickly" in {
    val batchFns = (1 to 1000).map { i =>
      s"Module$i" -> createBatchFn(s"Module$i")
    }.toMap

    val constellation = new MockConstellationWithBatch(batchFns)

    val startTime = System.nanoTime()
    val result = constellation
      .getBatchFunctions((1 to 100).map(i => s"Module$i").toList)
      .unsafeRunSync()
    val endTime = System.nanoTime()

    val elapsedMs = (endTime - startTime) / 1e6
    result should have size 100
    elapsedMs should be < 100.0 // Should complete in under 100ms
  }
}
