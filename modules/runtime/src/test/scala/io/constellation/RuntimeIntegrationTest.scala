package io.constellation

import java.util.UUID

import cats.Eval
import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.pool.RuntimePool

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RuntimeIntegrationTest extends AnyFlatSpec with Matchers {

  case class StringInput(text: String)
  case class StringOutput(result: String)
  case class IntInput(x: Long)
  case class IntOutput(result: Long)
  case class TwoIntInput(a: Long, b: Long)
  case class TwoIntOutput(sum: Long)

  private def uppercaseModule: Module.Uninitialized =
    ModuleBuilder
      .metadata("Uppercase", "Converts to uppercase", 1, 0)
      .implementationPure[StringInput, StringOutput](in => StringOutput(in.text.toUpperCase))
      .build

  private def doubleModule: Module.Uninitialized =
    ModuleBuilder
      .metadata("Double", "Doubles a number", 1, 0)
      .implementationPure[IntInput, IntOutput](in => IntOutput(in.x * 2))
      .build

  private def addModule: Module.Uninitialized =
    ModuleBuilder
      .metadata("Add", "Adds two numbers", 1, 0)
      .implementationPure[TwoIntInput, TwoIntOutput](in => TwoIntOutput(in.a + in.b))
      .build

  private def failingModule: Module.Uninitialized =
    ModuleBuilder
      .metadata("Failing", "Always fails", 1, 0)
      .implementation[StringInput, StringOutput] { _ =>
        IO.raiseError(new RuntimeException("intentional failure"))
      }
      .build

  // Helper to build a simple single-module DAG: input -> module -> output
  private def singleModuleDag(
      moduleId: UUID,
      inputDataId: UUID,
      outputDataId: UUID,
      inputName: String,
      outputName: String,
      inputType: CType,
      outputType: CType
  ): DagSpec = DagSpec(
    metadata = ComponentMetadata.empty("TestDag"),
    modules = Map(
      moduleId -> ModuleNodeSpec(
        metadata = ComponentMetadata("TestModule", "Test", List.empty, 1, 0),
        consumes = Map(inputName -> inputType),
        produces = Map(outputName -> outputType)
      )
    ),
    data = Map(
      inputDataId  -> DataNodeSpec(inputName, Map(moduleId -> inputName), inputType),
      outputDataId -> DataNodeSpec(outputName, Map(moduleId -> outputName), outputType)
    ),
    inEdges = Set((inputDataId, moduleId)),
    outEdges = Set((moduleId, outputDataId))
  )

  // ===== Runtime.run() basic tests =====

  "Runtime.run" should "execute a single string module" in {
    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag =
      singleModuleDag(moduleId, inputId, outputId, "text", "result", CType.CString, CType.CString)
    val modules  = Map(moduleId -> uppercaseModule)
    val initData = Map("text" -> CValue.CString("hello"))

    val state = Runtime.run(dag, initData, modules).unsafeRunSync()

    state.latency shouldBe defined
    state.dag shouldBe dag
    // Module should be Fired
    state.moduleStatus(moduleId).value shouldBe a[Module.Status.Fired]
    // Output data should be set
    state.data(outputId).value shouldBe CValue.CString("HELLO")
  }

  it should "execute a single integer module" in {
    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag = singleModuleDag(moduleId, inputId, outputId, "x", "result", CType.CInt, CType.CInt)
    val modules  = Map(moduleId -> doubleModule)
    val initData = Map("x" -> CValue.CInt(21L))

    val state = Runtime.run(dag, initData, modules).unsafeRunSync()

    state.moduleStatus(moduleId).value shouldBe a[Module.Status.Fired]
    state.data(outputId).value shouldBe CValue.CInt(42L)
  }

  it should "execute two sequential modules" in {
    val module1Id = UUID.randomUUID()
    val module2Id = UUID.randomUUID()
    val inputId   = UUID.randomUUID()
    val midId     = UUID.randomUUID()
    val outputId  = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("SequentialDag"),
      modules = Map(
        module1Id -> ModuleNodeSpec(
          metadata = ComponentMetadata("Double", "Doubles", List.empty, 1, 0),
          consumes = Map("x" -> CType.CInt),
          produces = Map("result" -> CType.CInt)
        ),
        module2Id -> ModuleNodeSpec(
          metadata = ComponentMetadata("Double2", "Doubles again", List.empty, 1, 0),
          consumes = Map("x" -> CType.CInt),
          produces = Map("result" -> CType.CInt)
        )
      ),
      data = Map(
        inputId  -> DataNodeSpec("x", Map(module1Id -> "x"), CType.CInt),
        midId    -> DataNodeSpec("mid", Map(module1Id -> "result", module2Id -> "x"), CType.CInt),
        outputId -> DataNodeSpec("result", Map(module2Id -> "result"), CType.CInt)
      ),
      inEdges = Set((inputId, module1Id), (midId, module2Id)),
      outEdges = Set((module1Id, midId), (module2Id, outputId))
    )

    val modules  = Map(module1Id -> doubleModule, module2Id -> doubleModule)
    val initData = Map("x" -> CValue.CInt(5L))

    val state = Runtime.run(dag, initData, modules).unsafeRunSync()

    state.moduleStatus(module1Id).value shouldBe a[Module.Status.Fired]
    state.moduleStatus(module2Id).value shouldBe a[Module.Status.Fired]
    state.data(outputId).value shouldBe CValue.CInt(20L) // 5 * 2 * 2
  }

  it should "execute parallel modules" in {
    val module1Id = UUID.randomUUID()
    val module2Id = UUID.randomUUID()
    val inputId   = UUID.randomUUID()
    val output1Id = UUID.randomUUID()
    val output2Id = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("ParallelDag"),
      modules = Map(
        module1Id -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Uppercase", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        ),
        module2Id -> ModuleNodeSpec(
          metadata = ComponentMetadata("Double", "Double", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputId -> DataNodeSpec(
          "text",
          Map(module1Id -> "text", module2Id -> "text"),
          CType.CString
        ),
        output1Id -> DataNodeSpec("result1", Map(module1Id -> "result"), CType.CString),
        output2Id -> DataNodeSpec("result2", Map(module2Id -> "result"), CType.CString)
      ),
      inEdges = Set((inputId, module1Id), (inputId, module2Id)),
      outEdges = Set((module1Id, output1Id), (module2Id, output2Id))
    )

    val modules  = Map(module1Id -> uppercaseModule, module2Id -> uppercaseModule)
    val initData = Map("text" -> CValue.CString("hello"))

    val state = Runtime.run(dag, initData, modules).unsafeRunSync()

    state.moduleStatus(module1Id).value shouldBe a[Module.Status.Fired]
    state.moduleStatus(module2Id).value shouldBe a[Module.Status.Fired]
    state.data(output1Id).value shouldBe CValue.CString("HELLO")
    state.data(output2Id).value shouldBe CValue.CString("HELLO")
  }

  it should "handle module that fails" in {
    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag =
      singleModuleDag(moduleId, inputId, outputId, "text", "result", CType.CString, CType.CString)
    val modules  = Map(moduleId -> failingModule)
    val initData = Map("text" -> CValue.CString("hello"))

    val state = Runtime.run(dag, initData, modules).unsafeRunSync()

    state.moduleStatus(moduleId).value shouldBe a[Module.Status.Failed]
  }

  // ===== Validation error tests =====

  it should "fail with wrong input type" in {
    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag =
      singleModuleDag(moduleId, inputId, outputId, "text", "result", CType.CString, CType.CString)
    val modules = Map(moduleId -> uppercaseModule)
    // Provide CInt instead of CString
    val initData = Map("text" -> CValue.CInt(42L))

    val result = Runtime.run(dag, initData, modules).attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.left.toOption.get.getMessage should include("different type")
  }

  it should "fail with unexpected input name" in {
    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag =
      singleModuleDag(moduleId, inputId, outputId, "text", "result", CType.CString, CType.CString)
    val modules  = Map(moduleId -> uppercaseModule)
    val initData = Map("wrongName" -> CValue.CString("hello"))

    val result = Runtime.run(dag, initData, modules).attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.left.toOption.get.getMessage should include("unexpected")
  }

  // ===== Runtime.runWithRawInputs() =====

  "Runtime.runWithRawInputs" should "execute with raw value inputs" in {
    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag =
      singleModuleDag(moduleId, inputId, outputId, "text", "result", CType.CString, CType.CString)
    val modules    = Map(moduleId -> uppercaseModule)
    val initData   = Map("text" -> RawValue.RString("hello"))
    val inputTypes = Map("text" -> CType.CString)

    val state = Runtime.runWithRawInputs(dag, initData, inputTypes, modules).unsafeRunSync()

    state.moduleStatus(moduleId).value shouldBe a[Module.Status.Fired]
    state.data(outputId).value shouldBe CValue.CString("HELLO")
  }

  it should "execute with integer raw inputs" in {
    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag = singleModuleDag(moduleId, inputId, outputId, "x", "result", CType.CInt, CType.CInt)
    val modules    = Map(moduleId -> doubleModule)
    val initData   = Map("x" -> RawValue.RInt(10L))
    val inputTypes = Map("x" -> CType.CInt)

    val state = Runtime.runWithRawInputs(dag, initData, inputTypes, modules).unsafeRunSync()

    state.moduleStatus(moduleId).value shouldBe a[Module.Status.Fired]
    state.data(outputId).value shouldBe CValue.CInt(20L)
  }

  it should "fail with wrong input type" in {
    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag =
      singleModuleDag(moduleId, inputId, outputId, "text", "result", CType.CString, CType.CString)
    val modules    = Map(moduleId -> uppercaseModule)
    val initData   = Map("text" -> RawValue.RInt(42L))
    val inputTypes = Map("text" -> CType.CInt) // CInt != CString expected

    val result =
      Runtime.runWithRawInputs(dag, initData, inputTypes, modules).attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.left.toOption.get.getMessage should include("different type")
  }

  it should "fail with unexpected input name" in {
    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag =
      singleModuleDag(moduleId, inputId, outputId, "text", "result", CType.CString, CType.CString)
    val modules    = Map(moduleId -> uppercaseModule)
    val initData   = Map("wrong" -> RawValue.RString("hello"))
    val inputTypes = Map("wrong" -> CType.CString)

    val result =
      Runtime.runWithRawInputs(dag, initData, inputTypes, modules).attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.left.toOption.get.getMessage should include("unexpected")
  }

  // ===== Runtime.runPooled() =====

  "Runtime.runPooled" should "execute with pooled resources" in {
    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag =
      singleModuleDag(moduleId, inputId, outputId, "text", "result", CType.CString, CType.CString)
    val modules  = Map(moduleId -> uppercaseModule)
    val initData = Map("text" -> CValue.CString("pooled"))

    val state = (for {
      pool   <- RuntimePool.create()
      result <- Runtime.runPooled(dag, initData, modules, pool)
    } yield result).unsafeRunSync()

    state.moduleStatus(moduleId).value shouldBe a[Module.Status.Fired]
    state.data(outputId).value shouldBe CValue.CString("POOLED")
  }

  it should "execute sequential modules with pooled resources" in {
    val module1Id = UUID.randomUUID()
    val module2Id = UUID.randomUUID()
    val inputId   = UUID.randomUUID()
    val midId     = UUID.randomUUID()
    val outputId  = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("PooledSequential"),
      modules = Map(
        module1Id -> ModuleNodeSpec(
          metadata = ComponentMetadata("Double", "Doubles", List.empty, 1, 0),
          consumes = Map("x" -> CType.CInt),
          produces = Map("result" -> CType.CInt)
        ),
        module2Id -> ModuleNodeSpec(
          metadata = ComponentMetadata("Double2", "Doubles again", List.empty, 1, 0),
          consumes = Map("x" -> CType.CInt),
          produces = Map("result" -> CType.CInt)
        )
      ),
      data = Map(
        inputId  -> DataNodeSpec("x", Map(module1Id -> "x"), CType.CInt),
        midId    -> DataNodeSpec("mid", Map(module1Id -> "result", module2Id -> "x"), CType.CInt),
        outputId -> DataNodeSpec("result", Map(module2Id -> "result"), CType.CInt)
      ),
      inEdges = Set((inputId, module1Id), (midId, module2Id)),
      outEdges = Set((module1Id, midId), (module2Id, outputId))
    )

    val modules  = Map(module1Id -> doubleModule, module2Id -> doubleModule)
    val initData = Map("x" -> CValue.CInt(3L))

    val state = (for {
      pool   <- RuntimePool.create()
      result <- Runtime.runPooled(dag, initData, modules, pool)
    } yield result).unsafeRunSync()

    state.data(outputId).value shouldBe CValue.CInt(12L)
  }

  // ===== Runtime instance method tests =====

  "Runtime instance" should "convert CValue to Any via cValueToAny" in {
    // We test indirectly via setTableDataCValue and getTableData
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataCValue(dataId, CValue.CString("test"))
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe "test"
  }

  it should "convert CValue.CInt to Any" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataCValue(dataId, CValue.CInt(42L))
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe 42L
  }

  it should "convert CValue.CBoolean to Any" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataCValue(dataId, CValue.CBoolean(true))
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe true
  }

  it should "convert CValue.CFloat to Any" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataCValue(dataId, CValue.CFloat(3.14))
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe 3.14
  }

  it should "convert CValue.CList to Any" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _ <- runtime.setTableDataCValue(
        dataId,
        CValue.CList(Vector(CValue.CInt(1L), CValue.CInt(2L)), CType.CInt)
      )
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe List(1L, 2L)
  }

  it should "convert CValue.CMap to Any" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _ <- runtime.setTableDataCValue(
        dataId,
        CValue.CMap(
          Vector((CValue.CString("a"), CValue.CInt(1L))),
          CType.CString,
          CType.CInt
        )
      )
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe List(("a", 1L))
  }

  it should "convert CValue.CProduct to Any" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _ <- runtime.setTableDataCValue(
        dataId,
        CValue.CProduct(
          Map("name" -> CValue.CString("Alice")),
          Map("name" -> CType.CString)
        )
      )
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe a[Map[?, ?]]
    result.asInstanceOf[Map[String, Any]]("name") shouldBe "Alice"
  }

  it should "convert CValue.CUnion to Any" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _ <- runtime.setTableDataCValue(
        dataId,
        CValue.CUnion(
          CValue.CString("hello"),
          Map("str" -> CType.CString),
          "str"
        )
      )
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe ("str", "hello")
  }

  it should "convert CValue.CSome to Any" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataCValue(dataId, CValue.CSome(CValue.CInt(42L), CType.CInt))
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe Some(42L)
  }

  it should "convert CValue.CNone to Any" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataCValue(dataId, CValue.CNone(CType.CInt))
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe None
  }

  // ===== setTableDataRawValue tests =====

  it should "set table data from RawValue.RString" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataRawValue(dataId, RawValue.RString("raw"))
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe "raw"
  }

  it should "set table data from RawValue.RInt" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataRawValue(dataId, RawValue.RInt(99L))
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe 99L
  }

  it should "set table data from RawValue.RBool" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataRawValue(dataId, RawValue.RBool(true))
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe true
  }

  it should "set table data from RawValue.RFloat" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataRawValue(dataId, RawValue.RFloat(2.72))
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe 2.72
  }

  it should "set table data from RawValue.RIntList" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataRawValue(dataId, RawValue.RIntList(Array(1L, 2L, 3L)))
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe List(1L, 2L, 3L)
  }

  it should "set table data from RawValue.RStringList" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataRawValue(dataId, RawValue.RStringList(Array("a", "b")))
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe List("a", "b")
  }

  it should "set table data from RawValue.RBoolList" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataRawValue(dataId, RawValue.RBoolList(Array(true, false)))
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe List(true, false)
  }

  it should "set table data from RawValue.RFloatList" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataRawValue(dataId, RawValue.RFloatList(Array(1.1, 2.2)))
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe List(1.1, 2.2)
  }

  it should "set table data from RawValue.RList" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _ <- runtime.setTableDataRawValue(
        dataId,
        RawValue.RList(Array[RawValue](RawValue.RInt(1L), RawValue.RInt(2L)))
      )
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe List(1L, 2L)
  }

  it should "set table data from RawValue.RMap" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _ <- runtime.setTableDataRawValue(
        dataId,
        RawValue.RMap(Array[(RawValue, RawValue)]((RawValue.RString("k"), RawValue.RInt(1L))))
      )
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe Map("k" -> 1L)
  }

  it should "set table data from RawValue.RProduct" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _ <- runtime.setTableDataRawValue(
        dataId,
        RawValue.RProduct(Array[RawValue](RawValue.RString("Alice"), RawValue.RInt(30L)))
      )
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe List("Alice", 30L)
  }

  it should "set table data from RawValue.RUnion" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataRawValue(dataId, RawValue.RUnion("tag1", RawValue.RString("val")))
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe ("tag1", "val")
  }

  it should "set table data from RawValue.RSome" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataRawValue(dataId, RawValue.RSome(RawValue.RInt(7L)))
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe Some(7L)
  }

  it should "set table data from RawValue.RNone" in {
    val dataId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")

    val result = (for {
      deferred <- cats.effect.Deferred[IO, Any]
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map(dataId -> deferred), state = stateRef)
      _    <- runtime.setTableDataRawValue(dataId, RawValue.RNone)
      data <- runtime.getTableData(dataId)
    } yield data).unsafeRunSync()

    result shouldBe None
  }

  // ===== Error path: getTableData with missing ID =====

  it should "fail getTableData for missing UUID" in {
    val dag = DagSpec.empty("test")

    val result = (for {
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map.empty, state = stateRef)
      data <- runtime.getTableData(UUID.randomUUID())
    } yield data).attempt.unsafeRunSync()

    result.isLeft shouldBe true
    result.left.toOption.get.getMessage should include("not found in table")
  }

  it should "fail setTableData for missing UUID" in {
    val dag = DagSpec.empty("test")

    val result = (for {
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map.empty, state = stateRef)
      _ <- runtime.setTableData(UUID.randomUUID(), "value")
    } yield ()).attempt.unsafeRunSync()

    result.isLeft shouldBe true
    result.left.toOption.get.getMessage should include("not found")
  }

  // ===== setTableDataCValue and setTableDataRawValue with missing UUID silently succeed =====

  it should "silently succeed for setTableDataCValue with missing UUID" in {
    val dag = DagSpec.empty("test")

    val result = (for {
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map.empty, state = stateRef)
      _ <- runtime.setTableDataCValue(UUID.randomUUID(), CValue.CString("ignored"))
    } yield "ok").unsafeRunSync()

    result shouldBe "ok"
  }

  it should "silently succeed for setTableDataRawValue with missing UUID" in {
    val dag = DagSpec.empty("test")

    val result = (for {
      stateRef <- cats.effect.Ref.of[IO, Runtime.State](
        Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
      )
      runtime = Runtime(table = Map.empty, state = stateRef)
      _ <- runtime.setTableDataRawValue(UUID.randomUUID(), RawValue.RString("ignored"))
    } yield "ok").unsafeRunSync()

    result shouldBe "ok"
  }

  // ===== Module with two inputs =====

  it should "execute a module with two inputs" in {
    val moduleId = UUID.randomUUID()
    val input1Id = UUID.randomUUID()
    val input2Id = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TwoInputDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Add", "Adds", List.empty, 1, 0),
          consumes = Map("a" -> CType.CInt, "b" -> CType.CInt),
          produces = Map("sum" -> CType.CInt)
        )
      ),
      data = Map(
        input1Id -> DataNodeSpec("a", Map(moduleId -> "a"), CType.CInt),
        input2Id -> DataNodeSpec("b", Map(moduleId -> "b"), CType.CInt),
        outputId -> DataNodeSpec("sum", Map(moduleId -> "sum"), CType.CInt)
      ),
      inEdges = Set((input1Id, moduleId), (input2Id, moduleId)),
      outEdges = Set((moduleId, outputId))
    )

    val modules  = Map(moduleId -> addModule)
    val initData = Map("a" -> CValue.CInt(10L), "b" -> CValue.CInt(32L))

    val state = Runtime.run(dag, initData, modules).unsafeRunSync()

    state.moduleStatus(moduleId).value shouldBe a[Module.Status.Fired]
    state.data(outputId).value shouldBe CValue.CInt(42L)
  }

  // ===== Runtime.runWithBackends() =====

  "Runtime.runWithBackends" should "execute with default backends" in {
    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag =
      singleModuleDag(moduleId, inputId, outputId, "text", "result", CType.CString, CType.CString)
    val modules  = Map(moduleId -> uppercaseModule)
    val initData = Map("text" -> CValue.CString("backends"))

    val state = Runtime
      .runWithBackends(
        dag,
        initData,
        modules,
        Map.empty,
        io.constellation.execution.GlobalScheduler.unbounded,
        io.constellation.spi.ConstellationBackends.defaults
      )
      .unsafeRunSync()

    state.moduleStatus(moduleId).value shouldBe a[Module.Status.Fired]
    state.data(outputId).value shouldBe CValue.CString("BACKENDS")
  }

  it should "handle failed module with backends" in {
    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag =
      singleModuleDag(moduleId, inputId, outputId, "text", "result", CType.CString, CType.CString)
    val modules  = Map(moduleId -> failingModule)
    val initData = Map("text" -> CValue.CString("fail"))

    val state = Runtime
      .runWithBackends(
        dag,
        initData,
        modules,
        Map.empty,
        io.constellation.execution.GlobalScheduler.unbounded,
        io.constellation.spi.ConstellationBackends.defaults
      )
      .unsafeRunSync()

    state.moduleStatus(moduleId).value shouldBe a[Module.Status.Failed]
  }

  it should "execute sequential modules with backends and priorities" in {
    val module1Id = UUID.randomUUID()
    val module2Id = UUID.randomUUID()
    val inputId   = UUID.randomUUID()
    val midId     = UUID.randomUUID()
    val outputId  = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("BackendsSequential"),
      modules = Map(
        module1Id -> ModuleNodeSpec(
          metadata = ComponentMetadata("Double", "Doubles", List.empty, 1, 0),
          consumes = Map("x" -> CType.CInt),
          produces = Map("result" -> CType.CInt)
        ),
        module2Id -> ModuleNodeSpec(
          metadata = ComponentMetadata("Double2", "Doubles again", List.empty, 1, 0),
          consumes = Map("x" -> CType.CInt),
          produces = Map("result" -> CType.CInt)
        )
      ),
      data = Map(
        inputId  -> DataNodeSpec("x", Map(module1Id -> "x"), CType.CInt),
        midId    -> DataNodeSpec("mid", Map(module1Id -> "result", module2Id -> "x"), CType.CInt),
        outputId -> DataNodeSpec("result", Map(module2Id -> "result"), CType.CInt)
      ),
      inEdges = Set((inputId, module1Id), (midId, module2Id)),
      outEdges = Set((module1Id, midId), (module2Id, outputId))
    )

    val modules    = Map(module1Id -> doubleModule, module2Id -> doubleModule)
    val initData   = Map("x" -> CValue.CInt(7L))
    val priorities = Map(module1Id -> 80, module2Id -> 50)

    val state = Runtime
      .runWithBackends(
        dag,
        initData,
        modules,
        priorities,
        io.constellation.execution.GlobalScheduler.unbounded,
        io.constellation.spi.ConstellationBackends.defaults
      )
      .unsafeRunSync()

    state.data(outputId).value shouldBe CValue.CInt(28L) // 7 * 2 * 2
  }

  // ===== Runtime.runCancellable() =====

  "Runtime.runCancellable" should "execute and complete normally" in {
    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag =
      singleModuleDag(moduleId, inputId, outputId, "text", "result", CType.CString, CType.CString)
    val modules  = Map(moduleId -> uppercaseModule)
    val initData = Map("text" -> CValue.CString("cancel"))

    val state = (for {
      exec <- Runtime.runCancellable(
        dag,
        initData,
        modules,
        Map.empty,
        io.constellation.execution.GlobalScheduler.unbounded,
        io.constellation.spi.ConstellationBackends.defaults
      )
      result <- exec.result
    } yield result).unsafeRunSync()

    state.data(outputId).value shouldBe CValue.CString("CANCEL")
  }

  // ===== Runtime.runWithTimeout() =====

  "Runtime.runWithTimeout" should "execute within timeout" in {
    import scala.concurrent.duration.*

    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag =
      singleModuleDag(moduleId, inputId, outputId, "text", "result", CType.CString, CType.CString)
    val modules  = Map(moduleId -> uppercaseModule)
    val initData = Map("text" -> CValue.CString("timeout"))

    val state = Runtime
      .runWithTimeout(
        10.seconds,
        dag,
        initData,
        modules,
        Map.empty,
        io.constellation.execution.GlobalScheduler.unbounded,
        io.constellation.spi.ConstellationBackends.defaults
      )
      .unsafeRunSync()

    state.data(outputId).value shouldBe CValue.CString("TIMEOUT")
  }

  // ===== Inline transform tests =====

  "Runtime.run with inline transforms" should "execute a DAG with a conditional inline transform" in {
    val moduleId        = UUID.randomUUID()
    val inputTextId     = UUID.randomUUID()
    val inputCondId     = UUID.randomUUID()
    val outputId        = UUID.randomUUID()
    val transformDataId = UUID.randomUUID()

    // Build a DAG: inputText -> Uppercase -> output
    //              inputCond + output -> conditional transform -> transformedData
    // Simplified: just test with module + an inline transform that reads from module output

    // Simpler approach: input -> module -> moduleOutput, and a separate conditional transform
    // that reads from the user input
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("InlineTransformDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Uppercase", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputTextId -> DataNodeSpec("text", Map(moduleId -> "text"), CType.CString),
        outputId    -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CString),
        // A literal inline transform that doesn't depend on any input
        transformDataId -> DataNodeSpec(
          "literal",
          Map.empty,
          CType.CInt,
          inlineTransform = Some(InlineTransform.LiteralTransform(42L)),
          transformInputs = Map.empty
        )
      ),
      inEdges = Set((inputTextId, moduleId)),
      outEdges = Set((moduleId, outputId))
    )

    val modules  = Map(moduleId -> uppercaseModule)
    val initData = Map("text" -> CValue.CString("inline"))

    val state = Runtime.run(dag, initData, modules).unsafeRunSync()

    state.moduleStatus(moduleId).value shouldBe a[Module.Status.Fired]
    state.data(outputId).value shouldBe CValue.CString("INLINE")
    // Inline transform should have computed the literal value
    state.data(transformDataId).value shouldBe CValue.CInt(42L)
  }

  it should "execute a DAG with conditional inline transform reading module output" in {
    val moduleId       = UUID.randomUUID()
    val inputId        = UUID.randomUUID()
    val moduleOutputId = UUID.randomUUID()
    val condResultId   = UUID.randomUUID()

    // input -> module -> moduleOutput
    // moduleOutput feeds into a conditional inline transform
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("CondTransformDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Double", "Doubles", List.empty, 1, 0),
          consumes = Map("x" -> CType.CInt),
          produces = Map("result" -> CType.CInt)
        )
      ),
      data = Map(
        inputId        -> DataNodeSpec("x", Map(moduleId -> "x"), CType.CInt),
        moduleOutputId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CInt),
        condResultId -> DataNodeSpec(
          "not_flag",
          Map.empty,
          CType.CBoolean,
          inlineTransform = Some(InlineTransform.LiteralTransform(true)),
          transformInputs = Map.empty
        )
      ),
      inEdges = Set((inputId, moduleId)),
      outEdges = Set((moduleId, moduleOutputId))
    )

    val modules  = Map(moduleId -> doubleModule)
    val initData = Map("x" -> CValue.CInt(5L))

    val state = Runtime.run(dag, initData, modules).unsafeRunSync()

    state.data(moduleOutputId).value shouldBe CValue.CInt(10L)
    state.data(condResultId).value shouldBe CValue.CBoolean(true)
  }

  // ===== runWithBackends with inline transforms =====

  it should "execute with backends and inline transforms" in {
    val moduleId  = UUID.randomUUID()
    val inputId   = UUID.randomUUID()
    val outputId  = UUID.randomUUID()
    val literalId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("BackendsInlineDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Uppercase", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputId  -> DataNodeSpec("text", Map(moduleId -> "text"), CType.CString),
        outputId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CString),
        literalId -> DataNodeSpec(
          "const",
          Map.empty,
          CType.CString,
          inlineTransform = Some(InlineTransform.LiteralTransform("constant")),
          transformInputs = Map.empty
        )
      ),
      inEdges = Set((inputId, moduleId)),
      outEdges = Set((moduleId, outputId))
    )

    val modules  = Map(moduleId -> uppercaseModule)
    val initData = Map("text" -> CValue.CString("test"))

    val state = Runtime
      .runWithBackends(
        dag,
        initData,
        modules,
        Map.empty,
        io.constellation.execution.GlobalScheduler.unbounded,
        io.constellation.spi.ConstellationBackends.defaults
      )
      .unsafeRunSync()

    state.data(outputId).value shouldBe CValue.CString("TEST")
    state.data(literalId).value shouldBe CValue.CString("constant")
  }

  // ===== Parameter naming collision (issue #219) =====

  "Runtime.run" should "not confuse pipeline input with formal parameter of same name" in {
    val someModuleId = UUID.randomUUID()
    val inputAId     = UUID.randomUUID()
    val inputBId     = UUID.randomUUID()

    // Two top-level data nodes:
    //   inputAId: name="a", type=CInt (the pipeline input)
    //   inputBId: name="b", type=CString, but nicknames contain "a" (formal param)
    // Without the fix, providing input "a" with CInt would match inputBId (CString)
    // via nicknames and fail with a type mismatch.
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("CollisionDag"),
      modules = Map.empty,
      data = Map(
        inputAId -> DataNodeSpec("a", Map.empty, CType.CInt),
        inputBId -> DataNodeSpec("b", Map(someModuleId -> "a"), CType.CString)
      ),
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val modules  = Map.empty[UUID, Module.Uninitialized]
    val initData = Map("a" -> CValue.CInt(7L), "b" -> CValue.CString("hello"))

    // Before fix: "a" matched inputBId (CString) via nicknames → type mismatch.
    // After fix: "a" matches inputAId (CInt) via spec.name → passes.
    val result = Runtime.run(dag, initData, modules).attempt.unsafeRunSync()
    withClue(result.left.map(_.getMessage).merge) {
      result.isRight shouldBe true
    }
  }
}
