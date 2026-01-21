package io.constellation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import io.circe.parser._
import java.util.UUID

class ConstellationErrorTest extends AnyFlatSpec with Matchers {

  // ========== TypeMismatchError Tests ==========

  "TypeMismatchError" should "include expected and actual in message" in {
    val error = TypeMismatchError("CString", "CInt")

    error.message shouldBe "Expected CString, but got CInt"
    error.errorCode shouldBe "TYPE_MISMATCH"
    error.category shouldBe "type"
  }

  it should "include context in getMessage" in {
    val error = TypeMismatchError(
      expected = "CString",
      actual = "CInt",
      context = Map("field" -> "name", "module" -> "Uppercase")
    )

    error.getMessage should include("[TYPE_MISMATCH]")
    error.getMessage should include("Expected CString")
    error.getMessage should include("field=name")
    error.getMessage should include("module=Uppercase")
  }

  it should "serialize to JSON correctly" in {
    val error = TypeMismatchError(
      expected = "CString",
      actual = "CInt",
      context = Map("field" -> "test")
    )

    val json = error.toJson
    json.hcursor.get[String]("error").toOption shouldBe Some("TYPE_MISMATCH")
    json.hcursor.get[String]("category").toOption shouldBe Some("type")
    json.hcursor.get[String]("message").toOption shouldBe Some("Expected CString, but got CInt")
    json.hcursor.downField("context").get[String]("field").toOption shouldBe Some("test")
  }

  it should "create from CType values using factory method" in {
    val error = TypeMismatchError.fromTypes(CType.CString, CType.CInt)

    error.expected should include("CString")
    error.actual should include("CInt")
  }

  it should "be throwable as an exception" in {
    val error = TypeMismatchError("String", "Int")

    val caught = intercept[TypeMismatchError] {
      throw error
    }

    caught.getMessage should include("Expected String")
  }

  // ========== TypeConversionError Tests ==========

  "TypeConversionError" should "include conversion details in message" in {
    val error = TypeConversionError("JSON", "CValue", "Invalid format")

    error.message shouldBe "Cannot convert from JSON to CValue: Invalid format"
    error.errorCode shouldBe "TYPE_CONVERSION"
    error.category shouldBe "type"
  }

  it should "serialize to JSON" in {
    val error = TypeConversionError("RawValue", "CValue", "Type mismatch")

    val json = error.toJson
    json.hcursor.get[String]("error").toOption shouldBe Some("TYPE_CONVERSION")
    json.hcursor.get[String]("message").toOption.get should include("Cannot convert from RawValue to CValue")
  }

  // ========== NodeNotFoundError Tests ==========

  "NodeNotFoundError" should "include node ID and type in message" in {
    val nodeId = UUID.randomUUID()
    val error = NodeNotFoundError(nodeId, "Input")

    error.message shouldBe s"Input node $nodeId not found"
    error.errorCode shouldBe "NODE_NOT_FOUND"
    error.category shouldBe "compiler"
  }

  it should "have factory methods for common node types" in {
    val nodeId = UUID.randomUUID()

    NodeNotFoundError.input(nodeId).message should include("Input node")
    NodeNotFoundError.source(nodeId).message should include("Source node")
    NodeNotFoundError.condition(nodeId).message should include("Condition node")
    NodeNotFoundError.expression(nodeId).message should include("Expression node")
  }

  it should "serialize to JSON" in {
    val nodeId = UUID.randomUUID()
    val error = NodeNotFoundError(nodeId, "Source", Map("dag" -> "test-dag"))

    val json = error.toJson
    json.hcursor.get[String]("error").toOption shouldBe Some("NODE_NOT_FOUND")
    json.hcursor.get[String]("category").toOption shouldBe Some("compiler")
    json.hcursor.downField("context").get[String]("dag").toOption shouldBe Some("test-dag")
  }

  // ========== UndefinedVariableError Tests ==========

  "UndefinedVariableError" should "include variable name in message" in {
    val error = UndefinedVariableError("myVar")

    error.message shouldBe "Undefined variable: myVar"
    error.errorCode shouldBe "UNDEFINED_VARIABLE"
    error.category shouldBe "compiler"
  }

  it should "serialize to JSON" in {
    val error = UndefinedVariableError("x", Map("scope" -> "lambda"))

    val json = error.toJson
    json.hcursor.get[String]("error").toOption shouldBe Some("UNDEFINED_VARIABLE")
    json.hcursor.downField("context").get[String]("scope").toOption shouldBe Some("lambda")
  }

  // ========== CycleDetectedError Tests ==========

  "CycleDetectedError" should "show generic message without node IDs" in {
    val error = CycleDetectedError()

    error.message shouldBe "Cycle detected in DAG"
    error.errorCode shouldBe "CYCLE_DETECTED"
    error.category shouldBe "compiler"
  }

  it should "include node IDs when provided" in {
    val nodeIds = List(UUID.randomUUID(), UUID.randomUUID())
    val error = CycleDetectedError(nodeIds)

    error.message should include("involving nodes:")
    nodeIds.foreach { id =>
      error.message should include(id.toString)
    }
  }

  it should "serialize to JSON" in {
    val error = CycleDetectedError(Nil, Map("phase" -> "compilation"))

    val json = error.toJson
    json.hcursor.get[String]("error").toOption shouldBe Some("CYCLE_DETECTED")
  }

  // ========== UnsupportedOperationError Tests ==========

  "UnsupportedOperationError" should "include operation and reason" in {
    val error = UnsupportedOperationError("lambda body", "recursive calls not supported")

    error.message shouldBe "Unsupported operation 'lambda body': recursive calls not supported"
    error.errorCode shouldBe "UNSUPPORTED_OPERATION"
    error.category shouldBe "compiler"
  }

  // ========== ModuleNotFoundError Tests ==========

  "ModuleNotFoundError" should "include module name" in {
    val error = ModuleNotFoundError("Uppercase")

    error.message shouldBe "Module 'Uppercase' not found in namespace"
    error.errorCode shouldBe "MODULE_NOT_FOUND"
    error.category shouldBe "runtime"
  }

  it should "serialize to JSON" in {
    val error = ModuleNotFoundError("MyModule")

    val json = error.toJson
    json.hcursor.get[String]("error").toOption shouldBe Some("MODULE_NOT_FOUND")
    json.hcursor.get[String]("category").toOption shouldBe Some("runtime")
  }

  // ========== ModuleExecutionError Tests ==========

  "ModuleExecutionError" should "include module name and cause" in {
    val cause = new RuntimeException("Connection failed")
    val error = ModuleExecutionError("HttpFetch", Some(cause))

    error.message should include("Module 'HttpFetch' execution failed")
    error.message should include("Connection failed")
    error.errorCode shouldBe "MODULE_EXECUTION"
    error.getCause shouldBe cause
  }

  it should "work without a cause" in {
    val error = ModuleExecutionError("TestModule", None)

    error.message shouldBe "Module 'TestModule' execution failed"
    error.getCause shouldBe null
  }

  it should "have convenient apply methods" in {
    val cause = new RuntimeException("error")

    val error1 = ModuleExecutionError("Mod", cause)
    error1.cause shouldBe Some(cause)

    val error2 = ModuleExecutionError("Mod", cause, Map("key" -> "value"))
    error2.context shouldBe Map("key" -> "value")
  }

  // ========== InputValidationError Tests ==========

  "InputValidationError" should "include input name and reason" in {
    val error = InputValidationError("email", "must be a valid email address")

    error.message shouldBe "Input 'email' validation failed: must be a valid email address"
    error.errorCode shouldBe "INPUT_VALIDATION"
    error.category shouldBe "runtime"
  }

  // ========== DataNotFoundError Tests ==========

  "DataNotFoundError" should "include data ID and type" in {
    val dataId = UUID.randomUUID()
    val error = DataNotFoundError(dataId, "Data")

    error.message shouldBe s"Data with ID $dataId not found"
    error.errorCode shouldBe "DATA_NOT_FOUND"
    error.category shouldBe "runtime"
  }

  it should "have factory methods for common data types" in {
    val dataId = UUID.randomUUID()

    DataNotFoundError.data(dataId).message should include("Data with ID")
    DataNotFoundError.deferred(dataId).message should include("Deferred with ID")
  }

  // ========== RuntimeNotInitializedError Tests ==========

  "RuntimeNotInitializedError" should "include reason" in {
    val error = RuntimeNotInitializedError("module registry empty")

    error.message shouldBe "Runtime not initialized: module registry empty"
    error.errorCode shouldBe "RUNTIME_NOT_INITIALIZED"
    error.category shouldBe "runtime"
  }

  // ========== ValidationError Tests ==========

  "ValidationError" should "join multiple error messages" in {
    val error = ValidationError(List("Field X is required", "Field Y must be positive"))

    error.message shouldBe "Field X is required; Field Y must be positive"
    error.errorCode shouldBe "VALIDATION_ERROR"
    error.category shouldBe "runtime"
  }

  it should "handle single error" in {
    val error = ValidationError(List("Single error"))

    error.message shouldBe "Single error"
  }

  it should "handle empty error list" in {
    val error = ValidationError(Nil)

    error.message shouldBe ""
  }

  // ========== Pattern Matching Tests ==========

  "ConstellationError" should "support pattern matching on categories" in {
    val errors: List[ConstellationError] = List(
      TypeMismatchError("A", "B"),
      NodeNotFoundError(UUID.randomUUID(), "Input"),
      ModuleNotFoundError("Test")
    )

    val categories = errors.map {
      case _: TypeError     => "type"
      case _: CompilerError => "compiler"
      case _: RuntimeError  => "runtime"
    }

    categories shouldBe List("type", "compiler", "runtime")
  }

  // ========== JSON Encoder Tests ==========

  "ConstellationError encoder" should "work with circe encoder" in {
    import ConstellationError.given

    val error: ConstellationError = TypeMismatchError("A", "B")
    val json = error.asJson

    json.hcursor.get[String]("error").toOption shouldBe Some("TYPE_MISMATCH")
  }
}
