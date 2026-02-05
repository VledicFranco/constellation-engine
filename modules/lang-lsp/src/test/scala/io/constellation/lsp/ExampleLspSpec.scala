package io.constellation.lsp

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.*
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.{FunctionSignature, SemanticType}
import io.constellation.lsp.protocol.JsonRpc.RequestId.*
import io.constellation.lsp.protocol.JsonRpc.*
import io.constellation.lsp.protocol.LspMessages.*
import io.constellation.lsp.protocol.LspTypes.*

import io.circe.Json
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** LSP integration tests for @example annotation extraction in GetInputSchema.
  *
  * Tests cover:
  *   - Primitive type examples (String, Int, Float, Boolean)
  *   - Inputs without @example annotations
  *   - Mixed inputs with and without annotations
  *   - Multiple inputs with different example types
  *   - Complex expressions (return None since unsupported)
  *
  * Note: The LSP server only converts literal expressions to JSON. Variable references, function
  * calls, and other complex expressions return None. Record and list literals are not supported by
  * the parser in expressions.
  *
  * See Issue #110: https://github.com/VledicFranco/constellation-engine/issues/110
  */
class ExampleLspSpec extends AnyFlatSpec with Matchers {

  case class TestInput(text: String)
  case class TestOutput(result: String)

  private def createUppercaseModule(): Module.Uninitialized =
    ModuleBuilder
      .metadata("Uppercase", "Converts text to uppercase", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.text.toUpperCase))
      .build

  private def createTestServer(): IO[ConstellationLanguageServer] = {
    val uppercaseModule = createUppercaseModule()

    for {
      constellation <- ConstellationImpl.init
      _             <- constellation.setModule(uppercaseModule)

      compiler = LangCompiler.builder
        .withModule(
          "Uppercase",
          uppercaseModule,
          List("text" -> SemanticType.SString),
          SemanticType.SString
        )
        .build

      server <- ConstellationLanguageServer.create(
        constellation,
        compiler,
        _ => IO.unit
      )
    } yield server
  }

  private def openDocumentAndGetSchema(
      server: ConstellationLanguageServer,
      source: String
  ): IO[Json] =
    for {
      _ <- server.handleNotification(
        Notification(
          method = "textDocument/didOpen",
          params = Some(
            DidOpenTextDocumentParams(
              TextDocumentItem("file:///test.cst", "constellation", 1, source)
            ).asJson
          )
        )
      )
      response <- server.handleRequest(
        Request(
          id = StringId("1"),
          method = "constellation/getInputSchema",
          params = Some(GetInputSchemaParams("file:///test.cst").asJson)
        )
      )
    } yield response.result.getOrElse(Json.Null)

  /** Helper to extract the example JSON from the first input in the schema result */
  private def extractFirstExample(resultJson: Json): Option[Json] =
    for {
      inputs       <- (resultJson \\ "inputs").headOption.flatMap(_.asArray)
      firstInput   <- inputs.headOption
      exampleField <- firstInput.asObject.flatMap(_("example"))
      if !exampleField.isNull
    } yield exampleField

  /** Helper to extract the example JSON from input at given index */
  private def extractExampleAtIndex(resultJson: Json, index: Int): Option[Json] =
    for {
      inputs       <- (resultJson \\ "inputs").headOption.flatMap(_.asArray)
      input        <- inputs.lift(index)
      exampleField <- input.asObject.flatMap(_("example"))
      if !exampleField.isNull
    } yield exampleField

  // ==========================================================================
  // String Literal Examples
  // ==========================================================================

  "LSP getInputSchema @example" should "return example for String literal" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example("hello world")
          in text: String
          out text
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    (schemaJson \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(true)

    val example = extractFirstExample(schemaJson)
    example shouldBe Some(Json.fromString("hello world"))
  }

  it should "return example for empty String literal" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example("")
          in text: String
          out text
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(Json.fromString(""))
  }

  it should "return example for String with special characters" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example("hello\nworld")
          in text: String
          out text
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(Json.fromString("hello\nworld"))
  }

  // ==========================================================================
  // Integer Literal Examples
  // ==========================================================================

  it should "return example for Int literal" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example(42)
          in count: Int
          out count
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(Json.fromInt(42))
  }

  it should "return example for zero Int literal" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example(0)
          in count: Int
          out count
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(Json.fromInt(0))
  }

  it should "return example for negative Int literal" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example(-100)
          in offset: Int
          out offset
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(Json.fromInt(-100))
  }

  it should "return example for large Int literal" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example(2147483647)
          in bigNum: Int
          out bigNum
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(Json.fromLong(2147483647L))
  }

  // ==========================================================================
  // Float Literal Examples
  // ==========================================================================

  it should "return example for Float literal" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example(3.14)
          in ratio: Float
          out ratio
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(Json.fromDoubleOrNull(3.14))
  }

  it should "return example for zero Float literal" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example(0.0)
          in value: Float
          out value
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(Json.fromDoubleOrNull(0.0))
  }

  it should "return example for Float with many decimal places" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example(3.141592653)
          in precise: Float
          out precise
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(Json.fromDoubleOrNull(3.141592653))
  }

  // ==========================================================================
  // Boolean Literal Examples
  // ==========================================================================

  it should "return example for Boolean true literal" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example(true)
          in enabled: Boolean
          out enabled
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(Json.fromBoolean(true))
  }

  it should "return example for Boolean false literal" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example(false)
          in disabled: Boolean
          out disabled
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(Json.fromBoolean(false))
  }

  // ==========================================================================
  // No Example Annotation
  // ==========================================================================

  it should "return None for input without @example annotation" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          in text: String
          out text
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    (schemaJson \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(true)

    val example = extractFirstExample(schemaJson)
    example shouldBe None
  }

  it should "return None for all inputs without @example annotations" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          in name: String
          in count: Int
          in enabled: Boolean
          out name
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    extractExampleAtIndex(schemaJson, 0) shouldBe None
    extractExampleAtIndex(schemaJson, 1) shouldBe None
    extractExampleAtIndex(schemaJson, 2) shouldBe None
  }

  // ==========================================================================
  // Mixed Inputs (Some With Examples, Some Without)
  // ==========================================================================

  it should "handle mixed inputs with and without @example annotations" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example("hello")
          in text: String
          in count: Int
          @example(true)
          in enabled: Boolean
          out text
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    (schemaJson \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(true)

    // First input has example
    extractExampleAtIndex(schemaJson, 0) shouldBe Some(Json.fromString("hello"))
    // Second input has no example
    extractExampleAtIndex(schemaJson, 1) shouldBe None
    // Third input has example
    extractExampleAtIndex(schemaJson, 2) shouldBe Some(Json.fromBoolean(true))
  }

  // ==========================================================================
  // Multiple Inputs with Different Example Types
  // ==========================================================================

  it should "return correct examples for multiple inputs with different types" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example("Alice")
          in name: String
          @example(30)
          in age: Int
          @example(1.75)
          in height: Float
          @example(true)
          in active: Boolean
          out name
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    (schemaJson \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(true)

    extractExampleAtIndex(schemaJson, 0) shouldBe Some(Json.fromString("Alice"))
    extractExampleAtIndex(schemaJson, 1) shouldBe Some(Json.fromInt(30))
    extractExampleAtIndex(schemaJson, 2) shouldBe Some(Json.fromDoubleOrNull(1.75))
    extractExampleAtIndex(schemaJson, 3) shouldBe Some(Json.fromBoolean(true))
  }

  // ==========================================================================
  // Complex Expressions (Return None)
  // ==========================================================================

  it should "return None for variable reference example" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          defaultValue = "hello"
          @example(defaultValue)
          in text: String
          out text
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    // Variable references are not converted to JSON
    val example = extractFirstExample(schemaJson)
    example shouldBe None
  }

  it should "return None for arithmetic expression example" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example(1 + 2)
          in count: Int
          out count
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    // Arithmetic expressions are not converted to JSON
    val example = extractFirstExample(schemaJson)
    example shouldBe None
  }

  it should "return None for conditional expression example" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example(if (true) 1 else 2)
          in count: Int
          out count
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    // Conditional expressions are not converted to JSON
    val example = extractFirstExample(schemaJson)
    example shouldBe None
  }

  // ==========================================================================
  // Edge Cases
  // ==========================================================================

  it should "handle input with @example but parse error elsewhere" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example("hello")
          in text: String
          result = InvalidSyntax(((
          out text
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    // Should return error when there's a parse error
    (schemaJson \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(false)
  }

  it should "handle empty document" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(server, "")
    } yield schema

    val schemaJson = result.unsafeRunSync()
    // Empty document might succeed or fail, but shouldn't throw
    (schemaJson \\ "success").headOption.flatMap(_.asBoolean) shouldBe defined
  }

  it should "handle document with only output declaration" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          out result
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    // No inputs means empty inputs list
    val inputs = (schemaJson \\ "inputs").headOption.flatMap(_.asArray)
    inputs.map(_.isEmpty) shouldBe Some(true)
  }

  // ==========================================================================
  // Response Structure Verification
  // ==========================================================================

  it should "include all InputField properties in response" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example("test value")
          in myInput: String
          out myInput
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    (schemaJson \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(true)

    // Get the first input field
    val firstInput = for {
      inputs <- (schemaJson \\ "inputs").headOption.flatMap(_.asArray)
      input  <- inputs.headOption
      obj    <- input.asObject
    } yield obj

    firstInput shouldBe defined
    val inputObj = firstInput.get

    // Verify all expected fields are present
    inputObj("name").flatMap(_.asString) shouldBe Some("myInput")
    inputObj("type") shouldBe defined // TypeDescriptor
    inputObj("line") shouldBe defined // Line number
    inputObj("example").flatMap(_.asString) shouldBe Some("test value")
  }

  // ==========================================================================
  // List Literal Examples
  // ==========================================================================

  it should "return example for empty list literal" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example([])
          in items: List<Int>
          out items
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(Json.arr())
  }

  it should "return example for list of integers" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example([1, 2, 3])
          in numbers: List<Int>
          out numbers
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(Json.arr(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3)))
  }

  it should "return example for list of strings" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example(["Alice", "Bob", "Charlie"])
          in names: List<String>
          out names
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(
      Json.arr(
        Json.fromString("Alice"),
        Json.fromString("Bob"),
        Json.fromString("Charlie")
      )
    )
  }

  it should "return example for list of booleans" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example([true, false, true])
          in flags: List<Boolean>
          out flags
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(
      Json.arr(
        Json.fromBoolean(true),
        Json.fromBoolean(false),
        Json.fromBoolean(true)
      )
    )
  }

  it should "return example for list of floats" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example([1.5, 2.5, 3.5])
          in values: List<Float>
          out values
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(
      Json.arr(
        Json.fromDoubleOrNull(1.5),
        Json.fromDoubleOrNull(2.5),
        Json.fromDoubleOrNull(3.5)
      )
    )
  }

  it should "return example for single element list" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example([42])
          in data: List<Int>
          out data
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(Json.arr(Json.fromInt(42)))
  }

  it should "return example for list with negative integers" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example([-1, -2, -3])
          in offsets: List<Int>
          out offsets
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    val example    = extractFirstExample(schemaJson)
    example shouldBe Some(Json.arr(Json.fromInt(-1), Json.fromInt(-2), Json.fromInt(-3)))
  }

  it should "handle multiple inputs with list and non-list examples" in {
    val result = for {
      server <- createTestServer()
      schema <- openDocumentAndGetSchema(
        server,
        """
          @example("hello")
          in text: String
          @example([1, 2, 3])
          in numbers: List<Int>
          @example(42)
          in count: Int
          out text
        """
      )
    } yield schema

    val schemaJson = result.unsafeRunSync()
    (schemaJson \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(true)

    extractExampleAtIndex(schemaJson, 0) shouldBe Some(Json.fromString("hello"))
    extractExampleAtIndex(schemaJson, 1) shouldBe Some(
      Json.arr(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3))
    )
    extractExampleAtIndex(schemaJson, 2) shouldBe Some(Json.fromInt(42))
  }
}
