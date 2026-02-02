package io.constellation

import cats.Eval
import cats.effect.{Deferred, IO, Ref}
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.concurrent.duration.*

class RuntimeTest extends AnyFlatSpec with Matchers {

  private def createEmptyDag: DagSpec = DagSpec.empty("TestDag")

  private def createEmptyState(dag: DagSpec): IO[Runtime.State] = IO.pure(
    Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = dag,
      moduleStatus = Map.empty,
      data = Map.empty
    )
  )

  private def createRuntime(dag: DagSpec): IO[Runtime] =
    for {
      stateRef <- createEmptyState(dag).flatMap(Ref.of[IO, Runtime.State])
    } yield Runtime(table = Map.empty, state = stateRef)

  private def createRuntimeWithTable(
      dag: DagSpec,
      dataIds: List[UUID]
  ): IO[(Runtime, Map[UUID, Deferred[IO, Any]])] =
    for {
      stateRef  <- createEmptyState(dag).flatMap(Ref.of[IO, Runtime.State])
      deferreds <- dataIds.traverse(id => Deferred[IO, Any].map(id -> _))
      table = deferreds.toMap
    } yield (Runtime(table = table, state = stateRef), table)

  // Tests for setModuleStatus
  "setModuleStatus" should "update module status to Unfired" in {
    val dag      = createEmptyDag
    val moduleId = UUID.randomUUID()

    val result = for {
      runtime <- createRuntime(dag)
      _       <- runtime.setModuleStatus(moduleId, Module.Status.Unfired)
      state   <- runtime.state.get
    } yield state

    val state = result.unsafeRunSync()
    state.moduleStatus.get(moduleId) shouldBe defined
    state.moduleStatus(moduleId).value shouldBe Module.Status.Unfired
  }

  it should "update module status to Fired" in {
    val dag      = createEmptyDag
    val moduleId = UUID.randomUUID()
    val latency  = 100.millis

    val result = for {
      runtime <- createRuntime(dag)
      _       <- runtime.setModuleStatus(moduleId, Module.Status.Fired(latency, None))
      state   <- runtime.state.get
    } yield state

    val state = result.unsafeRunSync()
    state.moduleStatus(moduleId).value shouldBe Module.Status.Fired(latency, None)
  }

  it should "update module status to Fired with context" in {
    val dag      = createEmptyDag
    val moduleId = UUID.randomUUID()
    val latency  = 100.millis
    val context  = Map("key" -> io.circe.Json.fromString("value"))

    val result = for {
      runtime <- createRuntime(dag)
      _       <- runtime.setModuleStatus(moduleId, Module.Status.Fired(latency, Some(context)))
      state   <- runtime.state.get
    } yield state

    val state = result.unsafeRunSync()
    state.moduleStatus(moduleId).value match {
      case Module.Status.Fired(lat, ctx) =>
        lat shouldBe latency
        ctx shouldBe Some(context)
      case _ => fail("Expected Fired status")
    }
  }

  it should "update module status to Failed" in {
    val dag      = createEmptyDag
    val moduleId = UUID.randomUUID()
    val error    = new RuntimeException("Test error")

    val result = for {
      runtime <- createRuntime(dag)
      _       <- runtime.setModuleStatus(moduleId, Module.Status.Failed(error))
      state   <- runtime.state.get
    } yield state

    val state = result.unsafeRunSync()
    state.moduleStatus(moduleId).value match {
      case Module.Status.Failed(e) => e.getMessage shouldBe "Test error"
      case _                       => fail("Expected Failed status")
    }
  }

  it should "update module status to Timed" in {
    val dag      = createEmptyDag
    val moduleId = UUID.randomUUID()
    val latency  = 500.millis

    val result = for {
      runtime <- createRuntime(dag)
      _       <- runtime.setModuleStatus(moduleId, Module.Status.Timed(latency))
      state   <- runtime.state.get
    } yield state

    val state = result.unsafeRunSync()
    state.moduleStatus(moduleId).value shouldBe Module.Status.Timed(latency)
  }

  it should "overwrite existing module status" in {
    val dag      = createEmptyDag
    val moduleId = UUID.randomUUID()

    val result = for {
      runtime <- createRuntime(dag)
      _       <- runtime.setModuleStatus(moduleId, Module.Status.Unfired)
      _       <- runtime.setModuleStatus(moduleId, Module.Status.Fired(100.millis, None))
      state   <- runtime.state.get
    } yield state

    val state = result.unsafeRunSync()
    state.moduleStatus(moduleId).value shouldBe Module.Status.Fired(100.millis, None)
  }

  // Tests for setStateData
  "setStateData" should "store CValue in state" in {
    val dag    = createEmptyDag
    val dataId = UUID.randomUUID()
    val value  = CValue.CString("test")

    val result = for {
      runtime <- createRuntime(dag)
      _       <- runtime.setStateData(dataId, value)
      state   <- runtime.state.get
    } yield state

    val state = result.unsafeRunSync()
    state.data.get(dataId) shouldBe defined
    state.data(dataId).value shouldBe value
  }

  it should "store different CValue types" in {
    val dag     = createEmptyDag
    val dataId1 = UUID.randomUUID()
    val dataId2 = UUID.randomUUID()
    val dataId3 = UUID.randomUUID()
    val dataId4 = UUID.randomUUID()

    val result = for {
      runtime <- createRuntime(dag)
      _       <- runtime.setStateData(dataId1, CValue.CString("hello"))
      _       <- runtime.setStateData(dataId2, CValue.CInt(42))
      _       <- runtime.setStateData(dataId3, CValue.CFloat(3.14))
      _       <- runtime.setStateData(dataId4, CValue.CBoolean(true))
      state   <- runtime.state.get
    } yield state

    val state = result.unsafeRunSync()
    state.data(dataId1).value shouldBe CValue.CString("hello")
    state.data(dataId2).value shouldBe CValue.CInt(42)
    state.data(dataId3).value shouldBe CValue.CFloat(3.14)
    state.data(dataId4).value shouldBe CValue.CBoolean(true)
  }

  it should "store complex CValue types" in {
    val dag       = createEmptyDag
    val dataId    = UUID.randomUUID()
    val listValue = CValue.CList(Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)), CType.CInt)

    val result = for {
      runtime <- createRuntime(dag)
      _       <- runtime.setStateData(dataId, listValue)
      state   <- runtime.state.get
    } yield state

    val state = result.unsafeRunSync()
    state.data(dataId).value shouldBe listValue
  }

  it should "overwrite existing data" in {
    val dag    = createEmptyDag
    val dataId = UUID.randomUUID()

    val result = for {
      runtime <- createRuntime(dag)
      _       <- runtime.setStateData(dataId, CValue.CString("first"))
      _       <- runtime.setStateData(dataId, CValue.CString("second"))
      state   <- runtime.state.get
    } yield state

    val state = result.unsafeRunSync()
    state.data(dataId).value shouldBe CValue.CString("second")
  }

  // Tests for table data operations
  "getTableData" should "retrieve data from table" in {
    val dag    = createEmptyDag
    val dataId = UUID.randomUUID()

    val result = for {
      pair <- createRuntimeWithTable(dag, List(dataId))
      (runtime, _) = pair
      _    <- runtime.setTableData(dataId, "test value")
      data <- runtime.getTableData(dataId)
    } yield data

    val data = result.unsafeRunSync()
    data shouldBe "test value"
  }

  it should "fail for missing data ID" in {
    val dag       = createEmptyDag
    val missingId = UUID.randomUUID()

    val result = for {
      runtime <- createRuntime(dag)
      data    <- runtime.getTableData(missingId)
    } yield data

    val attempt = result.attempt.unsafeRunSync()
    attempt.isLeft shouldBe true
    attempt.left.exists(_.getMessage.contains("not found")) shouldBe true
  }

  "setTableData" should "complete deferred in table" in {
    val dag    = createEmptyDag
    val dataId = UUID.randomUUID()

    val result = for {
      pair <- createRuntimeWithTable(dag, List(dataId))
      (runtime, _) = pair
      _    <- runtime.setTableData(dataId, 42L)
      data <- runtime.getTableData(dataId)
    } yield data

    val data = result.unsafeRunSync()
    data shouldBe 42L
  }

  it should "fail for missing data ID" in {
    val dag       = createEmptyDag
    val missingId = UUID.randomUUID()

    val result = for {
      runtime <- createRuntime(dag)
      _       <- runtime.setTableData(missingId, "value")
    } yield ()

    val attempt = result.attempt.unsafeRunSync()
    attempt.isLeft shouldBe true
    attempt.left.exists(_.getMessage.contains("not found")) shouldBe true
  }

  "setTableDataCValue" should "convert CValue and store in table" in {
    val dag    = createEmptyDag
    val dataId = UUID.randomUUID()

    val result = for {
      pair <- createRuntimeWithTable(dag, List(dataId))
      (runtime, _) = pair
      _    <- runtime.setTableDataCValue(dataId, CValue.CString("converted"))
      data <- runtime.getTableData(dataId)
    } yield data

    val data = result.unsafeRunSync()
    data shouldBe "converted"
  }

  it should "convert CInt to Long" in {
    val dag    = createEmptyDag
    val dataId = UUID.randomUUID()

    val result = for {
      pair <- createRuntimeWithTable(dag, List(dataId))
      (runtime, _) = pair
      _    <- runtime.setTableDataCValue(dataId, CValue.CInt(42))
      data <- runtime.getTableData(dataId)
    } yield data

    val data = result.unsafeRunSync()
    data shouldBe 42L
  }

  it should "convert CList to List" in {
    val dag    = createEmptyDag
    val dataId = UUID.randomUUID()

    val result = for {
      pair <- createRuntimeWithTable(dag, List(dataId))
      (runtime, _) = pair
      _ <- runtime.setTableDataCValue(
        dataId,
        CValue.CList(
          Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)),
          CType.CInt
        )
      )
      data <- runtime.getTableData(dataId)
    } yield data

    val data = result.unsafeRunSync()
    data shouldBe List(1L, 2L, 3L)
  }

  it should "convert CSome to Some" in {
    val dag    = createEmptyDag
    val dataId = UUID.randomUUID()

    val result = for {
      pair <- createRuntimeWithTable(dag, List(dataId))
      (runtime, _) = pair
      _    <- runtime.setTableDataCValue(dataId, CValue.CSome(CValue.CInt(42), CType.CInt))
      data <- runtime.getTableData(dataId)
    } yield data

    val data = result.unsafeRunSync()
    data shouldBe Some(42L)
  }

  it should "convert CNone to None" in {
    val dag    = createEmptyDag
    val dataId = UUID.randomUUID()

    val result = for {
      pair <- createRuntimeWithTable(dag, List(dataId))
      (runtime, _) = pair
      _    <- runtime.setTableDataCValue(dataId, CValue.CNone(CType.CInt))
      data <- runtime.getTableData(dataId)
    } yield data

    val data = result.unsafeRunSync()
    data shouldBe None
  }

  it should "succeed silently for data ID not in table" in {
    val dag       = createEmptyDag
    val missingId = UUID.randomUUID()

    val result = for {
      runtime <- createRuntime(dag) // Empty table
      _       <- runtime.setTableDataCValue(missingId, CValue.CString("ignored"))
    } yield ()

    // Should not throw - silently succeeds for passthrough DAGs
    result.unsafeRunSync() shouldBe ()
  }

  "setTableDataRawValue" should "convert RawValue and store in table" in {
    val dag    = createEmptyDag
    val dataId = UUID.randomUUID()

    val result = for {
      pair <- createRuntimeWithTable(dag, List(dataId))
      (runtime, _) = pair
      _    <- runtime.setTableDataRawValue(dataId, RawValue.RString("raw"))
      data <- runtime.getTableData(dataId)
    } yield data

    val data = result.unsafeRunSync()
    data shouldBe "raw"
  }

  it should "convert RInt to Long" in {
    val dag    = createEmptyDag
    val dataId = UUID.randomUUID()

    val result = for {
      pair <- createRuntimeWithTable(dag, List(dataId))
      (runtime, _) = pair
      _    <- runtime.setTableDataRawValue(dataId, RawValue.RInt(100))
      data <- runtime.getTableData(dataId)
    } yield data

    val data = result.unsafeRunSync()
    data shouldBe 100L
  }

  it should "convert RIntList to List" in {
    val dag    = createEmptyDag
    val dataId = UUID.randomUUID()

    val result = for {
      pair <- createRuntimeWithTable(dag, List(dataId))
      (runtime, _) = pair
      _    <- runtime.setTableDataRawValue(dataId, RawValue.RIntList(Array(1L, 2L, 3L)))
      data <- runtime.getTableData(dataId)
    } yield data

    val data = result.unsafeRunSync()
    data shouldBe List(1L, 2L, 3L)
  }

  it should "convert RSome to Some" in {
    val dag    = createEmptyDag
    val dataId = UUID.randomUUID()

    val result = for {
      pair <- createRuntimeWithTable(dag, List(dataId))
      (runtime, _) = pair
      _    <- runtime.setTableDataRawValue(dataId, RawValue.RSome(RawValue.RInt(42)))
      data <- runtime.getTableData(dataId)
    } yield data

    val data = result.unsafeRunSync()
    data shouldBe Some(42L)
  }

  it should "convert RNone to None" in {
    val dag    = createEmptyDag
    val dataId = UUID.randomUUID()

    val result = for {
      pair <- createRuntimeWithTable(dag, List(dataId))
      (runtime, _) = pair
      _    <- runtime.setTableDataRawValue(dataId, RawValue.RNone)
      data <- runtime.getTableData(dataId)
    } yield data

    val data = result.unsafeRunSync()
    data shouldBe None
  }

  it should "succeed silently for data ID not in table" in {
    val dag       = createEmptyDag
    val missingId = UUID.randomUUID()

    val result = for {
      runtime <- createRuntime(dag) // Empty table
      _       <- runtime.setTableDataRawValue(missingId, RawValue.RString("ignored"))
    } yield ()

    // Should not throw - silently succeeds for passthrough DAGs
    result.unsafeRunSync() shouldBe ()
  }

  // Tests for close
  "close" should "set latency in state" in {
    val dag     = createEmptyDag
    val latency = 500.millis

    val result = for {
      runtime    <- createRuntime(dag)
      finalState <- runtime.close(latency)
    } yield finalState

    val state = result.unsafeRunSync()
    state.latency shouldBe Some(latency)
  }

  // Tests for Runtime.State
  "Runtime.State" should "have processUuid" in {
    val state = Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = createEmptyDag,
      moduleStatus = Map.empty,
      data = Map.empty
    )

    state.processUuid should not be null
  }

  it should "store module status as lazy Eval" in {
    val moduleId  = UUID.randomUUID()
    var evaluated = false
    val lazyStatus = Eval.later {
      evaluated = true
      Module.Status.Unfired
    }

    val state = Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = createEmptyDag,
      moduleStatus = Map(moduleId -> lazyStatus),
      data = Map.empty
    )

    evaluated shouldBe false
    state.moduleStatus(moduleId).value
    evaluated shouldBe true
  }

  it should "store data as lazy Eval" in {
    val dataId    = UUID.randomUUID()
    var evaluated = false
    val lazyData = Eval.later {
      evaluated = true
      CValue.CString("test")
    }

    val state = Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = createEmptyDag,
      moduleStatus = Map.empty,
      data = Map(dataId -> lazyData)
    )

    evaluated shouldBe false
    state.data(dataId).value
    evaluated shouldBe true
  }
}
