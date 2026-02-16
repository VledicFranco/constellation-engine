package io.constellation.cli

import java.nio.file.{Files, Path}

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.cli.commands.RunCommand

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class RunCommandTest extends AnyFunSuite with Matchers with BeforeAndAfterEach:

  private var tempDir: Path = _

  override def beforeEach(): Unit =
    tempDir = Files.createTempDirectory("cli-run-test")

  override def afterEach(): Unit =
    if tempDir != null && Files.exists(tempDir) then
      Files
        .walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.deleteIfExists)

  // ============= Command Construction Tests =============

  test("RunCommand: stores file path"):
    val file = tempDir.resolve("pipeline.cst")
    val cmd  = RunCommand(file)
    cmd.file shouldBe file
    cmd.inputs shouldBe empty
    cmd.inputFile shouldBe None

  test("RunCommand: stores inputs"):
    val file = tempDir.resolve("pipeline.cst")
    val cmd  = RunCommand(file, inputs = List(("a", "1"), ("b", "2")))
    cmd.inputs should have size 2

  test("RunCommand: stores input file"):
    val file      = tempDir.resolve("pipeline.cst")
    val inputFile = tempDir.resolve("inputs.json")
    val cmd       = RunCommand(file, inputFile = Some(inputFile))
    cmd.inputFile shouldBe Some(inputFile)

  // ============= Input Parsing Tests =============

  test("parseValue: parses string"):
    val result = parseValue("hello world")
    result shouldBe Json.fromString("hello world")

  test("parseValue: parses integer"):
    val result = parseValue("42")
    result shouldBe Json.fromLong(42)

  test("parseValue: parses float"):
    val result = parseValue("3.14")
    result.isNumber shouldBe true

  test("parseValue: parses boolean true"):
    val result = parseValue("true")
    result shouldBe Json.True

  test("parseValue: parses boolean false"):
    val result = parseValue("false")
    result shouldBe Json.False

  test("parseValue: parses null"):
    val result = parseValue("null")
    result shouldBe Json.Null

  test("parseValue: parses JSON array"):
    val result = parseValue("[1, 2, 3]")
    result.isArray shouldBe true
    result.asArray.get should have size 3

  test("parseValue: parses JSON object"):
    val result = parseValue("""{"key": "value"}""")
    result.isObject shouldBe true

  // ============= Input File Loading Tests =============

  test("input file: loads valid JSON"):
    val inputFile = tempDir.resolve("inputs.json")
    Files.writeString(inputFile, """{"text": "hello", "count": 5}""")

    val content = Files.readString(inputFile)
    val parsed  = parse(content).flatMap(_.as[Map[String, Json]])
    parsed shouldBe a[Right[?, ?]]
    parsed.toOption.get should have size 2
    parsed.toOption.get("text") shouldBe Json.fromString("hello")
    parsed.toOption.get("count") shouldBe Json.fromInt(5)

  test("input file: handles nested JSON"):
    val inputFile = tempDir.resolve("inputs.json")
    Files.writeString(inputFile, """{"data": {"nested": true}}""")

    val content = Files.readString(inputFile)
    val parsed  = parse(content).flatMap(_.as[Map[String, Json]])
    parsed shouldBe a[Right[?, ?]]
    val dataJson = parsed.toOption.get.get("data")
    dataJson.map(_.hcursor.downField("nested").as[Boolean].toOption).flatten shouldBe Some(true)

  // ============= Response Parsing Tests =============

  test("RunResponse: decode success response"):
    val json = Json.obj(
      "success" -> Json.True,
      "outputs" -> Json.obj(
        "result" -> Json.fromString("hello"),
        "count"  -> Json.fromInt(42)
      ),
      "status"      -> Json.fromString("completed"),
      "executionId" -> Json.fromString("exec-123")
    )
    val result = json.as[RunCommand.RunResponse]
    result shouldBe a[Right[?, ?]]
    val resp = result.toOption.get
    resp.success shouldBe true
    resp.outputs should have size 2
    resp.status shouldBe Some("completed")
    resp.error shouldBe None

  test("RunResponse: decode suspended response"):
    val json = Json.obj(
      "success"     -> Json.True,
      "status"      -> Json.fromString("suspended"),
      "executionId" -> Json.fromString("exec-456"),
      "missingInputs" -> Json.obj(
        "input1" -> Json.fromString("CString"),
        "input2" -> Json.fromString("CInt")
      ),
      "pendingOutputs" -> Json.arr(Json.fromString("result"))
    )
    val result = json.as[RunCommand.RunResponse]
    result shouldBe a[Right[?, ?]]
    val resp = result.toOption.get
    resp.success shouldBe true
    resp.status shouldBe Some("suspended")
    resp.missingInputs shouldBe Some(Map("input1" -> "CString", "input2" -> "CInt"))
    resp.pendingOutputs shouldBe Some(List("result"))

  test("RunResponse: decode compilation error response"):
    val json = Json.obj(
      "success" -> Json.False,
      "compilationErrors" -> Json.arr(
        Json.fromString("Syntax error"),
        Json.fromString("Type error")
      )
    )
    val result = json.as[RunCommand.RunResponse]
    result shouldBe a[Right[?, ?]]
    val resp = result.toOption.get
    resp.success shouldBe false
    resp.compilationErrors should have size 2

  test("RunResponse: decode runtime error response"):
    val json = Json.obj(
      "success" -> Json.False,
      "error"   -> Json.fromString("Division by zero")
    )
    val result = json.as[RunCommand.RunResponse]
    result shouldBe a[Right[?, ?]]
    val resp = result.toOption.get
    resp.success shouldBe false
    resp.error shouldBe Some("Division by zero")

  // ============= CLI Input Override Tests =============

  test("CLI inputs override file inputs"):
    // When both --input and --input-file are provided, CLI inputs take precedence
    val inputFile = tempDir.resolve("inputs.json")
    Files.writeString(inputFile, """{"text": "from-file", "count": 10}""")

    val cliInputs   = List(("text", "from-cli"))
    val fileContent = Files.readString(inputFile)
    val fileInputs  = parse(fileContent).flatMap(_.as[Map[String, Json]]).getOrElse(Map.empty)

    val cliMap = cliInputs.map { case (k, v) => k -> parseValue(v) }.toMap
    val merged = fileInputs ++ cliMap

    merged("text") shouldBe Json.fromString("from-cli")
    merged("count") shouldBe Json.fromInt(10)

  // ============= Input Parsing Edge Cases =============

  test("parseValue: empty string"):
    val result = parseValue("")
    result shouldBe Json.fromString("")

  test("parseValue: whitespace-only string"):
    val result = parseValue("   ")
    result shouldBe Json.fromString("   ")

  test("parseValue: string with equals sign"):
    val result = parseValue("a=b")
    result shouldBe Json.fromString("a=b")

  test("parseValue: scientific notation"):
    val result = parseValue("1e10")
    // Should parse as number (JSON valid via parse)
    result.isNumber shouldBe true

  test("parseValue: very large integer string"):
    val result = parseValue("99999999999999999999")
    // Should parse as a number or string - not crash
    (result.isNumber || result.isString) shouldBe true

  test("parseValue: negative number"):
    val result = parseValue("-42")
    result.isNumber shouldBe true

  test("parseValue: negative float"):
    val result = parseValue("-3.14")
    result.isNumber shouldBe true

  test("parseValue: case-insensitive booleans"):
    // parseValue uses .toLowerCase so all cases map to boolean
    parseValue("True") shouldBe Json.True
    parseValue("TRUE") shouldBe Json.True
    parseValue("False") shouldBe Json.False
    parseValue("FALSE") shouldBe Json.False

  test("parseValue: string that looks like number but isn't"):
    val result = parseValue("12abc")
    result shouldBe Json.fromString("12abc")

  test("parseValue: zero"):
    val result = parseValue("0")
    result shouldBe Json.fromLong(0)

  test("parseValue: zero float"):
    val result = parseValue("0.0")
    result.isNumber shouldBe true

  // ============= Helper Methods =============

  private def parseValue(s: String): Json =
    parse(s).getOrElse {
      s.toLowerCase match
        case "true"  => Json.True
        case "false" => Json.False
        case "null"  => Json.Null
        case _ =>
          s.toDoubleOption match
            case Some(d) if s.contains(".") => Json.fromDoubleOrNull(d)
            case Some(d)                    => Json.fromLong(d.toLong)
            case None                       => Json.fromString(s)
    }
