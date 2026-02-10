package io.constellation.execution

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.{CType, CValue}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ErrorStrategyExecutorCoverageTest extends AnyFlatSpec with Matchers {

  // -------------------------------------------------------------------------
  // ErrorStrategy.fromString
  // -------------------------------------------------------------------------

  "ErrorStrategy.fromString" should "parse 'propagate' to Propagate" in {
    ErrorStrategy.fromString("propagate") shouldBe Some(ErrorStrategy.Propagate)
  }

  it should "parse 'skip' to Skip" in {
    ErrorStrategy.fromString("skip") shouldBe Some(ErrorStrategy.Skip)
  }

  it should "parse 'log' to Log" in {
    ErrorStrategy.fromString("log") shouldBe Some(ErrorStrategy.Log)
  }

  it should "parse 'wrap' to Wrap" in {
    ErrorStrategy.fromString("wrap") shouldBe Some(ErrorStrategy.Wrap)
  }

  it should "parse case-insensitively ('PROPAGATE')" in {
    ErrorStrategy.fromString("PROPAGATE") shouldBe Some(ErrorStrategy.Propagate)
  }

  it should "parse mixed case ('Skip')" in {
    ErrorStrategy.fromString("Skip") shouldBe Some(ErrorStrategy.Skip)
  }

  it should "return None for unknown strategy" in {
    ErrorStrategy.fromString("unknown") shouldBe None
  }

  it should "return None for empty string" in {
    ErrorStrategy.fromString("") shouldBe None
  }

  // -------------------------------------------------------------------------
  // ModuleError
  // -------------------------------------------------------------------------

  "ModuleError" should "return error message via .message" in {
    val err = ModuleError("TestModule", new RuntimeException("something broke"))
    err.message shouldBe "something broke"
  }

  it should "return error class simple name via .errorType" in {
    val err = ModuleError("TestModule", new IllegalArgumentException("bad arg"))
    err.errorType shouldBe "IllegalArgumentException"
  }

  it should "include moduleName, errorType and message in toString" in {
    val err = ModuleError("MyModule", new RuntimeException("oops"))
    val str = err.toString
    str should include("MyModule")
    str should include("RuntimeException")
    str should include("oops")
  }

  it should "format toString as ModuleError(name: type: message)" in {
    val err = ModuleError("Uppercase", new RuntimeException("boom"))
    err.toString shouldBe "ModuleError(Uppercase: RuntimeException: boom)"
  }

  it should "handle null message in error" in {
    val err = ModuleError("TestModule", new RuntimeException(null: String))
    err.message shouldBe null
    err.errorType shouldBe "RuntimeException"
  }

  it should "store timestamp" in {
    val before = System.currentTimeMillis()
    val err    = ModuleError("TestModule", new RuntimeException("fail"))
    val after  = System.currentTimeMillis()

    err.timestamp should be >= before
    err.timestamp should be <= after
  }

  it should "accept custom timestamp" in {
    val err = ModuleError("TestModule", new RuntimeException("fail"), timestamp = 12345L)
    err.timestamp shouldBe 12345L
  }

  // -------------------------------------------------------------------------
  // zeroValue for each CType
  // -------------------------------------------------------------------------

  "ErrorStrategyExecutor.zeroValue" should "return CString('') for CString" in {
    ErrorStrategyExecutor.zeroValue(CType.CString) shouldBe CValue.CString("")
  }

  it should "return CInt(0) for CInt" in {
    ErrorStrategyExecutor.zeroValue(CType.CInt) shouldBe CValue.CInt(0)
  }

  it should "return CFloat(0.0) for CFloat" in {
    ErrorStrategyExecutor.zeroValue(CType.CFloat) shouldBe CValue.CFloat(0.0)
  }

  it should "return CBoolean(false) for CBoolean" in {
    ErrorStrategyExecutor.zeroValue(CType.CBoolean) shouldBe CValue.CBoolean(false)
  }

  it should "return empty CList for CList(CInt)" in {
    val result = ErrorStrategyExecutor.zeroValue(CType.CList(CType.CInt))
    result shouldBe CValue.CList(Vector.empty, CType.CInt)
  }

  it should "return empty CList for CList(CString)" in {
    val result = ErrorStrategyExecutor.zeroValue(CType.CList(CType.CString))
    result shouldBe CValue.CList(Vector.empty, CType.CString)
  }

  it should "return empty CMap for CMap(CString, CInt)" in {
    val result = ErrorStrategyExecutor.zeroValue(CType.CMap(CType.CString, CType.CInt))
    result shouldBe CValue.CMap(Vector.empty, CType.CString, CType.CInt)
  }

  it should "return CProduct with zero fields for CProduct" in {
    val structure = Map("a" -> CType.CString, "b" -> CType.CInt)
    val result    = ErrorStrategyExecutor.zeroValue(CType.CProduct(structure))
    result shouldBe CValue.CProduct(
      Map("a" -> CValue.CString(""), "b" -> CValue.CInt(0)),
      structure
    )
  }

  it should "return CProduct with recursive zero values for nested CProduct" in {
    val innerStructure = Map("x" -> CType.CFloat)
    val outerStructure = Map("inner" -> CType.CProduct(innerStructure), "flag" -> CType.CBoolean)
    val result         = ErrorStrategyExecutor.zeroValue(CType.CProduct(outerStructure))

    val expected = CValue.CProduct(
      Map(
        "inner" -> CValue.CProduct(Map("x" -> CValue.CFloat(0.0)), innerStructure),
        "flag"  -> CValue.CBoolean(false)
      ),
      outerStructure
    )
    result shouldBe expected
  }

  it should "return CUnion with first variant zero value for CUnion" in {
    val variants = Map("Left" -> CType.CString, "Right" -> CType.CInt)
    val result   = ErrorStrategyExecutor.zeroValue(CType.CUnion(variants))
    val union    = result.asInstanceOf[CValue.CUnion]
    union.structure shouldBe variants
    val (expectedTag, expectedType) = variants.head
    union.tag shouldBe expectedTag
    union.value shouldBe ErrorStrategyExecutor.zeroValue(expectedType)
  }

  it should "return CNone for COptional(CString)" in {
    val optType = CType.COptional(CType.CString)
    val result  = ErrorStrategyExecutor.zeroValue(optType)
    result shouldBe CValue.CNone(optType)
  }

  it should "return CNone for COptional(CInt)" in {
    val optType = CType.COptional(CType.CInt)
    val result  = ErrorStrategyExecutor.zeroValue(optType)
    result shouldBe CValue.CNone(optType)
  }

  it should "return CNone for nested COptional" in {
    val nestedOpt = CType.COptional(CType.COptional(CType.CString))
    val result    = ErrorStrategyExecutor.zeroValue(nestedOpt)
    result shouldBe CValue.CNone(nestedOpt)
  }

  // -------------------------------------------------------------------------
  // hasZeroValue for each type
  // -------------------------------------------------------------------------

  "ErrorStrategyExecutor.hasZeroValue" should "return true for CString" in {
    ErrorStrategyExecutor.hasZeroValue(CType.CString) shouldBe true
  }

  it should "return true for CInt" in {
    ErrorStrategyExecutor.hasZeroValue(CType.CInt) shouldBe true
  }

  it should "return true for CFloat" in {
    ErrorStrategyExecutor.hasZeroValue(CType.CFloat) shouldBe true
  }

  it should "return true for CBoolean" in {
    ErrorStrategyExecutor.hasZeroValue(CType.CBoolean) shouldBe true
  }

  it should "return true for CList" in {
    ErrorStrategyExecutor.hasZeroValue(CType.CList(CType.CInt)) shouldBe true
  }

  it should "return true for CMap" in {
    ErrorStrategyExecutor.hasZeroValue(CType.CMap(CType.CString, CType.CInt)) shouldBe true
  }

  it should "return true for COptional" in {
    ErrorStrategyExecutor.hasZeroValue(CType.COptional(CType.CString)) shouldBe true
  }

  it should "return true for CProduct when all fields have zero values" in {
    val structure = Map("name" -> CType.CString, "age" -> CType.CInt)
    ErrorStrategyExecutor.hasZeroValue(CType.CProduct(structure)) shouldBe true
  }

  it should "return true for CUnion when any variant has zero value" in {
    val variants = Map("Text" -> CType.CString, "Number" -> CType.CInt)
    ErrorStrategyExecutor.hasZeroValue(CType.CUnion(variants)) shouldBe true
  }

  it should "return false for CUnion when no variant has zero value" in {
    val variants = Map.empty[String, CType]
    ErrorStrategyExecutor.hasZeroValue(CType.CUnion(variants)) shouldBe false
  }

  // -------------------------------------------------------------------------
  // execute with Propagate strategy
  // -------------------------------------------------------------------------

  "ErrorStrategyExecutor.execute (Propagate)" should "return result on success" in {
    val result = ErrorStrategyExecutor
      .execute(IO.pure(42), ErrorStrategy.Propagate, CType.CInt, "TestModule")
      .unsafeRunSync()
    result shouldBe 42
  }

  it should "re-throw error on failure" in {
    val error = new RuntimeException("boom")
    val caught = intercept[RuntimeException] {
      ErrorStrategyExecutor
        .execute(IO.raiseError(error), ErrorStrategy.Propagate, CType.CInt, "TestModule")
        .unsafeRunSync()
    }
    caught shouldBe error
  }

  it should "preserve the original exception type" in {
    val error = new IllegalStateException("bad state")
    val caught = intercept[IllegalStateException] {
      ErrorStrategyExecutor
        .execute(IO.raiseError(error), ErrorStrategy.Propagate, CType.CString, "Mod")
        .unsafeRunSync()
    }
    caught.getMessage shouldBe "bad state"
  }

  // -------------------------------------------------------------------------
  // execute with Skip strategy
  // -------------------------------------------------------------------------

  "ErrorStrategyExecutor.execute (Skip)" should "return result on success" in {
    val result = ErrorStrategyExecutor
      .execute(IO.pure(42), ErrorStrategy.Skip, CType.CInt, "TestModule")
      .unsafeRunSync()
    result shouldBe 42
  }

  it should "return zero value CString for CString on failure" in {
    val result = ErrorStrategyExecutor
      .execute(
        IO.raiseError[CValue](new RuntimeException("fail")),
        ErrorStrategy.Skip,
        CType.CString,
        "TestModule"
      )
      .unsafeRunSync()
    result shouldBe CValue.CString("")
  }

  it should "return zero value CInt for CInt on failure" in {
    val result = ErrorStrategyExecutor
      .execute(
        IO.raiseError[CValue](new RuntimeException("fail")),
        ErrorStrategy.Skip,
        CType.CInt,
        "TestModule"
      )
      .unsafeRunSync()
    result shouldBe CValue.CInt(0)
  }

  it should "return zero value CFloat for CFloat on failure" in {
    val result = ErrorStrategyExecutor
      .execute(
        IO.raiseError[CValue](new RuntimeException("fail")),
        ErrorStrategy.Skip,
        CType.CFloat,
        "TestModule"
      )
      .unsafeRunSync()
    result shouldBe CValue.CFloat(0.0)
  }

  // -------------------------------------------------------------------------
  // execute with Log strategy
  // -------------------------------------------------------------------------

  "ErrorStrategyExecutor.execute (Log)" should "return result on success" in {
    val result = ErrorStrategyExecutor
      .execute(IO.pure(42), ErrorStrategy.Log, CType.CInt, "TestModule")
      .unsafeRunSync()
    result shouldBe 42
  }

  it should "return zero value on failure (and log)" in {
    val result = ErrorStrategyExecutor
      .execute(
        IO.raiseError[CValue](new RuntimeException("logged error")),
        ErrorStrategy.Log,
        CType.CInt,
        "TestModule"
      )
      .unsafeRunSync()
    result shouldBe CValue.CInt(0)
  }

  it should "return zero value CBoolean on failure with Log strategy" in {
    val result = ErrorStrategyExecutor
      .execute(
        IO.raiseError[CValue](new RuntimeException("bool fail")),
        ErrorStrategy.Log,
        CType.CBoolean,
        "LogModule"
      )
      .unsafeRunSync()
    result shouldBe CValue.CBoolean(false)
  }

  // -------------------------------------------------------------------------
  // execute with Wrap strategy
  // -------------------------------------------------------------------------

  "ErrorStrategyExecutor.execute (Wrap)" should "return Right(result) on success" in {
    val result = ErrorStrategyExecutor
      .execute(IO.pure(42), ErrorStrategy.Wrap, CType.CInt, "TestModule")
      .unsafeRunSync()
    result shouldBe Right(42)
  }

  it should "return Left(ModuleError) on failure" in {
    val error = new RuntimeException("wrapped error")
    val result = ErrorStrategyExecutor
      .execute(IO.raiseError(error), ErrorStrategy.Wrap, CType.CInt, "TestModule")
      .unsafeRunSync()
    result match {
      case Left(me: ModuleError) =>
        me.moduleName shouldBe "TestModule"
        me.error shouldBe error
        me.message shouldBe "wrapped error"
        me.errorType shouldBe "RuntimeException"
      case other =>
        fail(s"Expected Left(ModuleError) but got $other")
    }
  }

  it should "preserve module name in wrapped ModuleError" in {
    val error = new RuntimeException("test")
    val result = ErrorStrategyExecutor
      .execute(IO.raiseError(error), ErrorStrategy.Wrap, CType.CString, "MyCustomModule")
      .unsafeRunSync()
    result match {
      case Left(me: ModuleError) =>
        me.moduleName shouldBe "MyCustomModule"
      case other =>
        fail(s"Expected Left(ModuleError) but got $other")
    }
  }

  // -------------------------------------------------------------------------
  // executeTyped
  // -------------------------------------------------------------------------

  "ErrorStrategyExecutor.executeTyped" should "work for Propagate strategy on success" in {
    val result = ErrorStrategyExecutor
      .executeTyped(IO.pure(42), ErrorStrategy.Propagate, CType.CInt, "TestModule")
      .unsafeRunSync()
    result shouldBe 42
  }

  it should "work for Skip strategy on success" in {
    val result = ErrorStrategyExecutor
      .executeTyped(IO.pure("hello"), ErrorStrategy.Skip, CType.CString, "TestModule")
      .unsafeRunSync()
    result shouldBe "hello"
  }

  it should "work for Log strategy on success" in {
    val result = ErrorStrategyExecutor
      .executeTyped(IO.pure(3.14), ErrorStrategy.Log, CType.CFloat, "TestModule")
      .unsafeRunSync()
    result shouldBe 3.14
  }

  it should "throw IllegalArgumentException for Wrap strategy" in {
    intercept[IllegalArgumentException] {
      ErrorStrategyExecutor
        .executeTyped(IO.pure(42), ErrorStrategy.Wrap, CType.CInt, "TestModule")
    }
  }

  it should "re-throw error with Propagate strategy" in {
    val error = new RuntimeException("typed boom")
    val caught = intercept[RuntimeException] {
      ErrorStrategyExecutor
        .executeTyped(IO.raiseError[Int](error), ErrorStrategy.Propagate, CType.CInt, "Mod")
        .unsafeRunSync()
    }
    caught shouldBe error
  }
}
