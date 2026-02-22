package io.constellation.provider

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.*
import io.constellation.provider.v1.provider as pb

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** RFC-034 Phase 1: Tests for batch function support in ModuleProviderManager. */
class ModuleProviderManagerBatchTest extends AnyFlatSpec with Matchers {

  // ===== Helper: Mock serializer =====

  private val mockSerializer = new CValueSerializer {
    override def serialize(value: CValue): Either[String, Array[Byte]] =
      Right(value.toString.getBytes)

    override def deserialize(bytes: Array[Byte]): Either[String, CValue] =
      Right(CValue.CString(new String(bytes)))
  }

  // ===== Helper: Mock ExecutorPool =====

  private class MockExecutorPool extends ExecutorPool {
    override def next: IO[ExecutorEndpoint] =
      IO.pure(ExecutorEndpoint("conn1", "localhost:9090"))

    override def add(endpoint: ExecutorEndpoint): IO[Unit] = IO.unit

    override def remove(connectionId: String): IO[Boolean] = IO.pure(false)

    override def size: IO[Int] = IO.pure(1)

    override def endpoints: IO[Vector[ExecutorEndpoint]] =
      IO.pure(Vector(ExecutorEndpoint("conn1", "localhost:9090")))
  }

  // ===== Helper: Batch function creator =====

  private def createTestBatchFunction(): List[CValue] => IO[List[Either[Throwable, CValue]]] =
    { inputs =>
      IO.pure(
        inputs.map { input =>
          Right(
            input match {
              case CValue.CString(s) => CValue.CString(s"batched:$s")
              case other             => other
            }
          )
        }
      )
    }

  // ===== Tests =====

  "getBatchFunctions" should "return empty map when no batch functions registered" in {
    // Create a mock constellation to test the interface
    val constellation = new Constellation {
      override def getModules: IO[List[ModuleNodeSpec]] = IO.pure(Nil)
      override def getModuleByName(name: String): IO[Option[Module.Uninitialized]] =
        IO.pure(None)
      override def setModule(module: Module.Uninitialized): IO[Unit] = IO.unit
      override def PipelineStore: PipelineStore = ???
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
      .getBatchFunctions(List("namespace.module"))
      .unsafeRunSync()

    result should be(empty)
  }

  "Batch function support flags" should "handle ModuleCapabilities correctly" in {
    // Test that capabilities are parsed correctly
    val capWithBatch = pb.ModuleCapabilities(supportsBatch = true, maxBatchSize = 100)
    val capWithoutBatch = pb.ModuleCapabilities(supportsBatch = false, maxBatchSize = 0)

    capWithBatch.supportsBatch shouldBe true
    capWithBatch.maxBatchSize shouldBe 100

    capWithoutBatch.supportsBatch shouldBe false
    capWithoutBatch.maxBatchSize shouldBe 0
  }

  "Module capabilities" should "support batch declaration" in {
    val capabilities = pb.ModuleCapabilities(
      supportsBatch = true,
      maxBatchSize = 1000
    )

    capabilities.supportsBatch shouldBe true
    capabilities.maxBatchSize should be > 0
  }

  it should "disable batch by default" in {
    val defaultCapabilities = pb.ModuleCapabilities()

    defaultCapabilities.supportsBatch shouldBe false
    defaultCapabilities.maxBatchSize shouldBe 0
  }

  "ModuleCapabilities in ModuleDeclaration" should "be optional" in {
    val declWithCapabilities = pb.ModuleDeclaration(
      name = "TestModule",
      capabilities = Some(pb.ModuleCapabilities(supportsBatch = true))
    )

    val declWithoutCapabilities = pb.ModuleDeclaration(name = "TestModule")

    declWithCapabilities.capabilities should be(defined)
    declWithCapabilities.capabilities.get.supportsBatch shouldBe true

    declWithoutCapabilities.capabilities should be(empty)
  }

  "Batch function integration" should "accept batch-enabled module declarations" in {
    val batchCapabilities = pb.ModuleCapabilities(
      supportsBatch = true,
      maxBatchSize = 500
    )

    val declaration = pb.ModuleDeclaration(
      name = "BatchModule",
      capabilities = Some(batchCapabilities)
    )

    declaration.name shouldBe "BatchModule"
    declaration.capabilities.get.supportsBatch shouldBe true
  }

  "ExternalModule batch support" should "create batch function when supported" in {
    val batchFn = ExternalModule.createBatchFunction(
      name = "TestModule",
      executorPool = new MockExecutorPool,
      serializer = mockSerializer,
      channelCache = new GrpcChannelCache,
      supportsBatch = true
    )

    // Verify the batch function is created (Some)
    batchFn should be(defined)

    // Note: Actually calling the batch function would require a running gRPC server,
    // so we just verify that the function object exists and is callable
    batchFn.get should be(a[Function1[_, _]])
  }

  it should "return None when batch not supported" in {
    val batchFn = ExternalModule.createBatchFunction(
      name = "TestModule",
      executorPool = new MockExecutorPool,
      serializer = mockSerializer,
      channelCache = new GrpcChannelCache,
      supportsBatch = false
    )

    batchFn should be(empty)
  }

  "Batch function processing" should "handle multiple inputs" in {
    val batchFn = createTestBatchFunction()
    val inputs = List(
      CValue.CString("a"),
      CValue.CString("b"),
      CValue.CString("c")
    )

    val results = batchFn(inputs).unsafeRunSync()

    results should have length 3
    results.map(_.toOption.get.asInstanceOf[CValue.CString].value) should contain theSameElementsAs
      List("batched:a", "batched:b", "batched:c")
  }

  it should "handle empty batch" in {
    val batchFn = createTestBatchFunction()
    val results = batchFn(List.empty).unsafeRunSync()

    results should be(empty)
  }

  it should "preserve order of inputs" in {
    val batchFn = createTestBatchFunction()
    val inputs = (1 to 100).map(i => CValue.CString(s"item$i")).toList

    val results = batchFn(inputs).unsafeRunSync()

    val resultStrings = results.map(_.toOption.get.asInstanceOf[CValue.CString].value)
    resultStrings should have length 100

    for (i <- 0 until 100) {
      resultStrings(i) shouldBe s"batched:item${i + 1}"
    }
  }

  "Batch function error handling" should "propagate errors correctly" in {
    val errorBatchFn: List[CValue] => IO[List[Either[Throwable, CValue]]] = { inputs =>
      IO.pure(
        inputs.zipWithIndex.map { case (_, idx) =>
          if (idx == 1) Left(new RuntimeException("Error at index 1"))
          else Right(CValue.CString("ok"))
        }
      )
    }

    val inputs = List(
      CValue.CString("a"),
      CValue.CString("b"),
      CValue.CString("c")
    )

    val results = errorBatchFn(inputs).unsafeRunSync()

    results should have length 3
    results(0) should be(a[Right[_, _]])
    results(1) should be(a[Left[_, _]])
    results(2) should be(a[Right[_, _]])
  }

  "Batch function qualification" should "use qualified names" in {
    val qualifiedName = "my_namespace.MyBatchModule"
    val parts = qualifiedName.split("\\.")

    parts should have length 2
    parts(0) shouldBe "my_namespace"
    parts(1) shouldBe "MyBatchModule"
  }

  it should "support nested namespaces" in {
    val qualifiedName = "company.team.service.BatchModule"
    val namespace = qualifiedName.split("\\.").dropRight(1).mkString(".")
    val moduleName = qualifiedName.split("\\.").last

    namespace shouldBe "company.team.service"
    moduleName shouldBe "BatchModule"
  }

  "Max batch size handling" should "declare batch size limits" in {
    val smallBatch = pb.ModuleCapabilities(supportsBatch = true, maxBatchSize = 10)
    val largeBatch = pb.ModuleCapabilities(supportsBatch = true, maxBatchSize = 10000)
    val unlimited = pb.ModuleCapabilities(supportsBatch = true, maxBatchSize = 0)

    smallBatch.maxBatchSize shouldBe 10
    largeBatch.maxBatchSize shouldBe 10000
    unlimited.maxBatchSize shouldBe 0
  }

  "Batch function with max size" should "respect batch size constraints" in {
    val maxSize = 50
    val capabilities = pb.ModuleCapabilities(supportsBatch = true, maxBatchSize = maxSize)

    // In Phase 1, StreamCompiler will need to split batches exceeding maxSize
    // This test documents the API contract
    capabilities.supportsBatch shouldBe true
    capabilities.maxBatchSize shouldBe maxSize
  }

  "Batch provider registration" should "extract capabilities from declaration" in {
    val capabilities = pb.ModuleCapabilities(supportsBatch = true, maxBatchSize = 256)
    val declaration = pb.ModuleDeclaration(
      name = "BatchProcessor",
      capabilities = Some(capabilities)
    )

    val extracted = declaration.capabilities.map(_.supportsBatch).getOrElse(false)
    extracted shouldBe true
  }
}
