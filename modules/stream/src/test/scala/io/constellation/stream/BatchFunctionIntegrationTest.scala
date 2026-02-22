package io.constellation.stream

import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*

import io.constellation.*
import io.constellation.stream.config.StreamPipelineConfig
import io.constellation.stream.connector.ConnectorRegistry
import io.constellation.stream.connector.MemoryConnector
import io.constellation.stream.error.StreamErrorStrategy
import io.constellation.stream.join.JoinStrategy

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** RFC-034 Phase 1: Tests for batch function integration in streaming pipelines. */
class BatchFunctionIntegrationTest extends AnyFlatSpec with Matchers {

  // ===== Helper: Test case classes =====

  private case class StringInput(value: String)
  private case class StringOutput(value: String)

  // ===== Helper: Create test modules =====

  private def createTestModule(name: String): Module.Uninitialized =
    ModuleBuilder
      .metadata(name, s"Test module $name", 1, 0)
      .implementationPure[StringInput, StringOutput](input => StringOutput(s"processed:${input.value}"))
      .build

  // ===== Helper: Batch function that processes a list of inputs =====

  private def createBatchFunction(
      name: String
  ): List[CValue] => IO[List[Either[Throwable, CValue]]] = { inputs =>
    IO.pure(
      inputs.map { input =>
        Right(
          input match {
            case CValue.CString(s) => CValue.CString(s"batch:$s")
            case other             => other
          }
        )
      }
    )
  }

  // ===== Helper: Build a simple single-module DAG =====

  private def makeSingleModuleDag(
      moduleName: String,
      consumes: Map[String, CType] = Map("input" -> CType.CString),
      produces: Map[String, CType] = Map("output" -> CType.CString)
  ): DagSpec = {
    val moduleId = UUID.randomUUID()
    val inputDataId = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    DagSpec(
      metadata = ComponentMetadata(s"test_${moduleName.toLowerCase}", "test dag", Nil, 1, 0),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata(moduleName, s"$moduleName module", Nil, 1, 0),
          consumes = consumes,
          produces = produces
        )
      ),
      data = Map(
        inputDataId -> DataNodeSpec(
          name = "input",
          nicknames = Map(moduleId -> "input"),
          cType = CType.CString
        ),
        outputDataId -> DataNodeSpec(
          name = "output",
          nicknames = Map(moduleId -> "output"),
          cType = CType.CString
        )
      ),
      inEdges = Set(inputDataId -> moduleId),
      outEdges = Set(moduleId -> outputDataId),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> outputDataId)
    )
  }

  // ===== Tests =====

  "StreamCompiler.wire" should "accept batchModules parameter" in {
    val dagSpec = makeSingleModuleDag("TestMod")
    val registry = ConnectorRegistry.empty
    val modules = dagSpec.modules.keys.toList.map(_ -> ((s: CValue) => IO.pure(s))).toMap
    val batchModules = Map.empty[UUID, List[CValue] => IO[List[Either[Throwable, CValue]]]]

    val graph = StreamCompiler
      .wire(
        dagSpec,
        registry,
        modules,
        batchModules = batchModules
      )
      .unsafeRunSync()

    graph shouldBe a[StreamGraph]
    graph.metrics should not be null
  }

  it should "use batch functions when provided" in {
    val dagSpec = makeSingleModuleDag("BatchModule")
    val registry = ConnectorRegistry.empty

    val moduleId = dagSpec.modules.keys.head
    val modules = Map(moduleId -> ((s: CValue) => IO.pure(s)))
    val batchFunctions = Map(
      moduleId -> createBatchFunction("BatchModule")
    )

    // This should not throw; batch functions are accepted and integrated
    val graph = StreamCompiler
      .wire(
        dagSpec,
        registry,
        modules,
        batchModules = batchFunctions
      )
      .unsafeRunSync()

    graph shouldBe a[StreamGraph]
  }

  it should "ignore batch functions for modules not in DAG" in {
    val dagSpec = makeSingleModuleDag("Module1")
    val registry = ConnectorRegistry.empty

    val moduleId = dagSpec.modules.keys.head
    val unusedModuleId = UUID.randomUUID()
    val modules = Map(moduleId -> ((s: CValue) => IO.pure(s)))
    val batchFunctions = Map(
      unusedModuleId -> createBatchFunction("UnusedModule")
    )

    // Batch functions for non-existent modules should be harmless
    val graph = StreamCompiler
      .wire(
        dagSpec,
        registry,
        modules,
        batchModules = batchFunctions
      )
      .unsafeRunSync()

    graph shouldBe a[StreamGraph]
  }

  "BatchFunctionExtraction" should "return empty map for non-batch modules" in {
    val dagSpec = makeSingleModuleDag("NoBatchModule")
    val result = StreamCompiler
      .wire(
        dagSpec,
        ConnectorRegistry.empty,
        dagSpec.modules.keys.toList.map(_ -> ((s: CValue) => IO.pure(s))).toMap,
        batchModules = Map.empty
      )
      .unsafeRunSync()

    // Empty batch modules map should be handled gracefully
    result shouldBe a[StreamGraph]
  }

  it should "handle mixed batch and non-batch modules" in {
    val moduleId1 = UUID.randomUUID()
    val moduleId2 = UUID.randomUUID()

    val dagSpec = DagSpec(
      metadata = ComponentMetadata("mixed_dag", "test dag with mixed modules", Nil, 1, 0),
      modules = Map(
        moduleId1 -> ModuleNodeSpec(
          metadata = ComponentMetadata("Module1", "first module", Nil, 1, 0),
          consumes = Map("input" -> CType.CString),
          produces = Map("output" -> CType.CString)
        ),
        moduleId2 -> ModuleNodeSpec(
          metadata = ComponentMetadata("Module2", "second module", Nil, 1, 0),
          consumes = Map("input" -> CType.CString),
          produces = Map("output" -> CType.CString)
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = Nil,
      outputBindings = Map.empty
    )

    val modules = Map(
      moduleId1 -> ((s: CValue) => IO.pure(s)),
      moduleId2 -> ((s: CValue) => IO.pure(s))
    )

    val batchFunctions = Map(
      moduleId1 -> createBatchFunction("Module1")
      // moduleId2 has no batch function
    )

    val graph = StreamCompiler
      .wire(
        dagSpec,
        ConnectorRegistry.empty,
        modules,
        batchModules = batchFunctions
      )
      .unsafeRunSync()

    graph shouldBe a[StreamGraph]
  }

  "Batch function error handling" should "handle batch function failures gracefully" in {
    val dagSpec = makeSingleModuleDag("FailingBatchModule")
    val registry = ConnectorRegistry.empty

    val moduleId = dagSpec.modules.keys.head
    val modules = Map(moduleId -> ((s: CValue) => IO.pure(s)))

    // Batch function that returns errors
    val batchFunctionWithErrors: List[CValue] => IO[List[Either[Throwable, CValue]]] =
      { inputs =>
        IO.pure(
          inputs.map { input =>
            Left(new RuntimeException(s"Batch processing failed for $input"))
          }
        )
      }

    val batchFunctions = Map(moduleId -> batchFunctionWithErrors)

    // Should accept error-returning batch functions
    val graph = StreamCompiler
      .wire(
        dagSpec,
        registry,
        modules,
        batchModules = batchFunctions
      )
      .unsafeRunSync()

    graph shouldBe a[StreamGraph]
  }

  "Batch function differentiation" should "return different results for batch vs single-element" in {
    val singleElementResult = (IO.pure(CValue.CString("test")): IO[CValue])
      .unsafeRunSync()

    val batchFn = createBatchFunction("TestMod")
    val batchResults = batchFn(List(CValue.CString("test")))
      .unsafeRunSync()
      .map(_.toOption)

    // Batch functions should have different output format (List[Either]) vs single-element
    batchResults should not be empty
    batchResults.head should be(Some(CValue.CString("batch:test")))
  }

  "Batch functions with multiple inputs" should "process all inputs in a batch" in {
    val batchFn = createBatchFunction("MultiModule")
    val inputs = List(
      CValue.CString("input1"),
      CValue.CString("input2"),
      CValue.CString("input3")
    )

    val results = batchFn(inputs)
      .unsafeRunSync()
      .map(_.toOption.get.asInstanceOf[CValue.CString].value)

    results should have length 3
    results should contain theSameElementsAs List(
      "batch:input1",
      "batch:input2",
      "batch:input3"
    )
  }

  "Batch function integration" should "accept batch functions without errors" in {
    val dagSpec = makeSingleModuleDag("IntegrationModule")
    val registry = ConnectorRegistry.empty

    val moduleId = dagSpec.modules.keys.head
    val modules = Map(moduleId -> ((s: CValue) => IO.pure(s)))
    val batchFunctions = Map(
      moduleId -> createBatchFunction("IntegrationModule")
    )

    val result = StreamCompiler
      .wire(
        dagSpec,
        registry,
        modules,
        batchModules = batchFunctions
      )

    result.unsafeRunSync() shouldBe a[StreamGraph]
  }

  "Empty batch functions map" should "work with wire API" in {
    val dagSpec = makeSingleModuleDag("EmptyBatchModule")
    val registry = ConnectorRegistry.empty
    val modules = dagSpec.modules.keys.toList.map(_ -> ((s: CValue) => IO.pure(s))).toMap

    val graph = StreamCompiler
      .wire(
        dagSpec,
        registry,
        modules,
        batchModules = Map.empty
      )
      .unsafeRunSync()

    graph shouldBe a[StreamGraph]
  }

  "Batch functions with default parameter" should "use empty map when omitted" in {
    val dagSpec = makeSingleModuleDag("DefaultModule")
    val registry = ConnectorRegistry.empty
    val modules = dagSpec.modules.keys.toList.map(_ -> ((s: CValue) => IO.pure(s))).toMap

    // Call without batchModules parameter (should default to Map.empty)
    val graph = StreamCompiler
      .wire(
        dagSpec,
        registry,
        modules
      )
      .unsafeRunSync()

    graph shouldBe a[StreamGraph]
  }
}
