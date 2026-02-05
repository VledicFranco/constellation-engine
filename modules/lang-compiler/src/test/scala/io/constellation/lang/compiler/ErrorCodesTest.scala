package io.constellation.lang.compiler

import io.constellation.lang.ast.CompileError

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ErrorCodesTest extends AnyFlatSpec with Matchers {

  // ========== Error Code Mapping Tests ==========

  "ErrorCodes.fromCompileError" should "map UndefinedVariable to E001" in {
    val error     = CompileError.UndefinedVariable("x", None)
    val errorCode = ErrorCodes.fromCompileError(error)

    errorCode.code shouldBe "E001"
    errorCode.title shouldBe "Undefined variable"
    errorCode.category shouldBe ErrorCategory.Reference
  }

  it should "map UndefinedFunction to E002" in {
    val error     = CompileError.UndefinedFunction("Foo", None)
    val errorCode = ErrorCodes.fromCompileError(error)

    errorCode.code shouldBe "E002"
    errorCode.title shouldBe "Undefined function"
    errorCode.category shouldBe ErrorCategory.Reference
  }

  it should "map UndefinedType to E003" in {
    val error     = CompileError.UndefinedType("Bar", None)
    val errorCode = ErrorCodes.fromCompileError(error)

    errorCode.code shouldBe "E003"
    errorCode.title shouldBe "Undefined type"
    errorCode.category shouldBe ErrorCategory.Reference
  }

  it should "map UndefinedNamespace to E004" in {
    val error     = CompileError.UndefinedNamespace("baz", None)
    val errorCode = ErrorCodes.fromCompileError(error)

    errorCode.code shouldBe "E004"
    errorCode.title shouldBe "Undefined namespace"
    errorCode.category shouldBe ErrorCategory.Reference
  }

  it should "map AmbiguousFunction to E005" in {
    val error = CompileError.AmbiguousFunction("add", List("stdlib.math.add", "custom.add"), None)
    val errorCode = ErrorCodes.fromCompileError(error)

    errorCode.code shouldBe "E005"
    errorCode.title shouldBe "Ambiguous function reference"
    errorCode.category shouldBe ErrorCategory.Reference
  }

  it should "map InvalidProjection to E006" in {
    val error     = CompileError.InvalidProjection("field", List("a", "b"), None)
    val errorCode = ErrorCodes.fromCompileError(error)

    errorCode.code shouldBe "E006"
    errorCode.title shouldBe "Invalid projection"
    errorCode.category shouldBe ErrorCategory.Reference
  }

  it should "map InvalidFieldAccess to E007" in {
    val error     = CompileError.InvalidFieldAccess("field", List("a", "b"), None)
    val errorCode = ErrorCodes.fromCompileError(error)

    errorCode.code shouldBe "E007"
    errorCode.title shouldBe "Invalid field access"
    errorCode.category shouldBe ErrorCategory.Reference
  }

  it should "map TypeMismatch to E010" in {
    val error     = CompileError.TypeMismatch("String", "Int", None)
    val errorCode = ErrorCodes.fromCompileError(error)

    errorCode.code shouldBe "E010"
    errorCode.title shouldBe "Type mismatch"
    errorCode.category shouldBe ErrorCategory.Type
  }

  it should "map IncompatibleMerge to E012" in {
    val error     = CompileError.IncompatibleMerge("String", "Int", None)
    val errorCode = ErrorCodes.fromCompileError(error)

    errorCode.code shouldBe "E012"
    errorCode.title shouldBe "Incompatible types for merge"
    errorCode.category shouldBe ErrorCategory.Type
  }

  it should "map UnsupportedComparison to E013" in {
    val error     = CompileError.UnsupportedComparison("==", "String", "Int", None)
    val errorCode = ErrorCodes.fromCompileError(error)

    errorCode.code shouldBe "E013"
    errorCode.title shouldBe "Unsupported comparison"
    errorCode.category shouldBe ErrorCategory.Type
  }

  it should "map UnsupportedArithmetic to E014" in {
    val error     = CompileError.UnsupportedArithmetic("+", "String", "Int", None)
    val errorCode = ErrorCodes.fromCompileError(error)

    errorCode.code shouldBe "E014"
    errorCode.title shouldBe "Unsupported arithmetic"
    errorCode.category shouldBe ErrorCategory.Type
  }

  it should "map TypeError to E015" in {
    val error     = CompileError.TypeError("Some type error", None)
    val errorCode = ErrorCodes.fromCompileError(error)

    errorCode.code shouldBe "E015"
    errorCode.title shouldBe "Type error"
    errorCode.category shouldBe ErrorCategory.Type
  }

  it should "map ParseError to E020" in {
    val error     = CompileError.ParseError("Unexpected token", None)
    val errorCode = ErrorCodes.fromCompileError(error)

    errorCode.code shouldBe "E020"
    errorCode.title shouldBe "Syntax error"
    errorCode.category shouldBe ErrorCategory.Syntax
  }

  it should "map InternalError to E900" in {
    val error     = CompileError.InternalError("Something went wrong", None)
    val errorCode = ErrorCodes.fromCompileError(error)

    errorCode.code shouldBe "E900"
    errorCode.title shouldBe "Internal compiler error"
    errorCode.category shouldBe ErrorCategory.Internal
  }

  // ========== ErrorCode Properties Tests ==========

  "ErrorCode" should "have non-empty explanations" in {
    ErrorCodes.all.foreach { errorCode =>
      errorCode.explanation should not be empty
      withClue(s"Error code ${errorCode.code} has empty explanation") {
        errorCode.explanation.length should be > 10
      }
    }
  }

  it should "have unique codes" in {
    val codes = ErrorCodes.all.map(_.code)
    codes.distinct.length shouldBe codes.length
  }

  it should "have documentation paths for most errors" in {
    // Internal errors don't need docs
    val nonInternalErrors = ErrorCodes.all.filter(_.category != ErrorCategory.Internal)

    nonInternalErrors.foreach { errorCode =>
      withClue(s"Error code ${errorCode.code} should have docPath") {
        errorCode.docPath shouldBe defined
      }
    }
  }

  // ========== ErrorCode.docUrl Tests ==========

  "ErrorCode.docUrl" should "return full URL when docPath is defined" in {
    ErrorCodes.UndefinedVariable.docUrl shouldBe defined
    ErrorCodes.UndefinedVariable.docUrl.get should startWith(
      "https://constellation-engine.dev/docs/"
    )
  }

  it should "return None when docPath is None" in {
    ErrorCodes.InternalError.docUrl shouldBe empty
  }

  // ========== ErrorCategory Tests ==========

  "ErrorCategory" should "have expected values" in {
    ErrorCategory.values should contain(ErrorCategory.Syntax)
    ErrorCategory.values should contain(ErrorCategory.Type)
    ErrorCategory.values should contain(ErrorCategory.Reference)
    ErrorCategory.values should contain(ErrorCategory.Semantic)
    ErrorCategory.values should contain(ErrorCategory.Internal)
  }

  // ========== ErrorCodes.all Tests ==========

  "ErrorCodes.all" should "contain all error codes" in {
    // Verify key error codes are in the list
    ErrorCodes.all should contain(ErrorCodes.UndefinedVariable)
    ErrorCodes.all should contain(ErrorCodes.UndefinedFunction)
    ErrorCodes.all should contain(ErrorCodes.TypeMismatch)
    ErrorCodes.all should contain(ErrorCodes.ParseError)
    ErrorCodes.all should contain(ErrorCodes.InternalError)
  }

  it should "have at least 10 error codes" in {
    ErrorCodes.all.length should be >= 10
  }
}
