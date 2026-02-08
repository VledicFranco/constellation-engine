package io.constellation.cli

import java.nio.file.Paths

import com.monovore.decline.*

import io.constellation.cli.commands.*

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CommandParsingTest extends AnyFunSuite with Matchers:

  // Helper to wrap Opts in a Command for parsing
  private def wrapCommand[A](opts: Opts[A]): Command[A] =
    Command("test", "test command")(opts)

  // Helper to parse compile command
  private def parseCompile(args: String*): Either[Help, CliCommand] =
    wrapCommand(CompileCommand.command).parse(args.toList)

  // Helper to parse run command
  private def parseRun(args: String*): Either[Help, CliCommand] =
    wrapCommand(RunCommand.command).parse(args.toList)

  // Helper to parse viz command
  private def parseViz(args: String*): Either[Help, CliCommand] =
    wrapCommand(VizCommand.command).parse(args.toList)

  // Helper to parse config command
  private def parseConfig(args: String*): Either[Help, CliCommand] =
    wrapCommand(ConfigCommand.command).parse(args.toList)

  // ============= Compile Command Tests =============

  test("compile: parse file argument"):
    val result = parseCompile("compile", "test.cst")
    result shouldBe a[Right[?, ?]]
    result.toOption.get shouldBe a[CompileCommand]
    result.toOption.get.asInstanceOf[CompileCommand].file.toString shouldBe "test.cst"

  test("compile: parse with watch flag"):
    val result = parseCompile("compile", "--watch", "test.cst")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[CompileCommand]
    cmd.watch shouldBe true

  test("compile: parse with short watch flag"):
    val result = parseCompile("compile", "-w", "test.cst")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[CompileCommand]
    cmd.watch shouldBe true

  test("compile: fail without file argument"):
    val result = parseCompile("compile")
    result shouldBe a[Left[?, ?]]

  // ============= Run Command Tests =============

  test("run: parse file argument"):
    val result = parseRun("run", "test.cst")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[RunCommand]
    cmd.file.toString shouldBe "test.cst"

  test("run: parse with single input"):
    val result = parseRun("run", "--input", "text=hello", "test.cst")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[RunCommand]
    cmd.inputs shouldBe List(("text", "hello"))

  test("run: parse with multiple inputs"):
    val result = parseRun("run", "--input", "a=1", "--input", "b=2", "test.cst")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[RunCommand]
    cmd.inputs should have size 2
    cmd.inputs should contain(("a", "1"))
    cmd.inputs should contain(("b", "2"))

  test("run: parse with short input flag"):
    val result = parseRun("run", "-i", "x=y", "test.cst")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[RunCommand]
    cmd.inputs shouldBe List(("x", "y"))

  test("run: parse with input file"):
    val result = parseRun("run", "--input-file", "inputs.json", "test.cst")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[RunCommand]
    cmd.inputFile.map(_.toString) shouldBe Some("inputs.json")

  test("run: parse with short input file flag"):
    val result = parseRun("run", "-f", "inputs.json", "test.cst")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[RunCommand]
    cmd.inputFile.map(_.toString) shouldBe Some("inputs.json")

  test("run: parse with inputs and input file"):
    val result = parseRun("run", "-i", "a=1", "-f", "inputs.json", "test.cst")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[RunCommand]
    cmd.inputs shouldBe List(("a", "1"))
    cmd.inputFile.map(_.toString) shouldBe Some("inputs.json")

  // ============= Viz Command Tests =============

  test("viz: parse file argument"):
    val result = parseViz("viz", "test.cst")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[VizCommand]
    cmd.file.toString shouldBe "test.cst"
    cmd.format shouldBe VizFormat.Dot // default

  test("viz: parse with dot format"):
    val result = parseViz("viz", "--format", "dot", "test.cst")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[VizCommand]
    cmd.format shouldBe VizFormat.Dot

  test("viz: parse with json format"):
    val result = parseViz("viz", "--format", "json", "test.cst")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[VizCommand]
    cmd.format shouldBe VizFormat.Json

  test("viz: parse with mermaid format"):
    val result = parseViz("viz", "--format", "mermaid", "test.cst")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[VizCommand]
    cmd.format shouldBe VizFormat.Mermaid

  test("viz: parse with short format flag"):
    val result = parseViz("viz", "-F", "json", "test.cst")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[VizCommand]
    cmd.format shouldBe VizFormat.Json

  test("viz: fail with invalid format"):
    val result = parseViz("viz", "--format", "invalid", "test.cst")
    result shouldBe a[Left[?, ?]]

  // ============= Config Command Tests =============

  test("config show: parse"):
    val result = parseConfig("config", "show")
    result shouldBe a[Right[?, ?]]
    result.toOption.get shouldBe a[ConfigCommand.ConfigShow]

  test("config get: parse with key"):
    val result = parseConfig("config", "get", "server.url")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[ConfigCommand.ConfigGet]
    cmd.key shouldBe "server.url"

  test("config set: parse with key and value"):
    val result = parseConfig("config", "set", "server.url", "http://example.com")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[ConfigCommand.ConfigSet]
    cmd.key shouldBe "server.url"
    cmd.value shouldBe "http://example.com"

  test("config set: fail without value"):
    val result = parseConfig("config", "set", "server.url")
    result shouldBe a[Left[?, ?]]
