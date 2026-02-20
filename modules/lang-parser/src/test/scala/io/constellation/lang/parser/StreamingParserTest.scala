package io.constellation.lang.parser

import io.constellation.lang.ast.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for RFC-025 Phase 3 parser extensions: streaming with-clause options, WindowSpec,
  * JoinStrategySpec, and @source/@sink annotations.
  */
class StreamingParserTest extends AnyFlatSpec with Matchers {

  // ============================================================================
  // Helper Methods
  // ============================================================================

  private def parseAndGetOptions(source: String): ModuleCallOptions = {
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations
      .collectFirst { case a: Declaration.Assignment => a }
      .getOrElse(fail("Expected an assignment declaration"))

    assignment.value.value match {
      case fc: Expression.FunctionCall => fc.options
      case _                           => fail("Expected a function call expression")
    }
  }

  private def parseProgram(source: String): Pipeline = {
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    result.toOption.get
  }

  // ============================================================================
  // Streaming with-clause options: batch, batch_timeout, window, checkpoint, join
  // ============================================================================

  "Parser" should "parse batch option with integer value" in {
    val source  = """
      in x: Int
      result = Process(x) with batch: 100
      out result
    """
    val options = parseAndGetOptions(source)
    options.batch shouldBe Some(100)
  }

  it should "parse batch_timeout option with duration" in {
    val source  = """
      in x: Int
      result = Process(x) with batch_timeout: 5s
      out result
    """
    val options = parseAndGetOptions(source)
    options.batchTimeout shouldBe Some(Duration(5, DurationUnit.Seconds))
  }

  it should "parse batch and batch_timeout together" in {
    val source  = """
      in x: Int
      result = Process(x) with batch: 50, batch_timeout: 10s
      out result
    """
    val options = parseAndGetOptions(source)
    options.batch shouldBe Some(50)
    options.batchTimeout shouldBe Some(Duration(10, DurationUnit.Seconds))
  }

  it should "parse checkpoint option with duration" in {
    val source  = """
      in x: Int
      result = Process(x) with checkpoint: 30s
      out result
    """
    val options = parseAndGetOptions(source)
    options.checkpoint shouldBe Some(Duration(30, DurationUnit.Seconds))
  }

  it should "parse checkpoint with millisecond duration" in {
    val source  = """
      in x: Int
      result = Process(x) with checkpoint: 500ms
      out result
    """
    val options = parseAndGetOptions(source)
    options.checkpoint shouldBe Some(Duration(500, DurationUnit.Milliseconds))
  }

  // ============================================================================
  // WindowSpec parsing: tumbling, sliding, count
  // ============================================================================

  it should "parse window with tumbling spec" in {
    val source  = """
      in x: Int
      result = Process(x) with window: tumbling(5s)
      out result
    """
    val options = parseAndGetOptions(source)
    options.window shouldBe Some(WindowSpec.Tumbling(Duration(5, DurationUnit.Seconds)))
  }

  it should "parse window with sliding spec" in {
    val source  = """
      in x: Int
      result = Process(x) with window: sliding(10s, 2s)
      out result
    """
    val options = parseAndGetOptions(source)
    options.window shouldBe Some(
      WindowSpec.Sliding(
        Duration(10, DurationUnit.Seconds),
        Duration(2, DurationUnit.Seconds)
      )
    )
  }

  it should "parse window with count spec" in {
    val source  = """
      in x: Int
      result = Process(x) with window: count(100)
      out result
    """
    val options = parseAndGetOptions(source)
    options.window shouldBe Some(WindowSpec.Count(100))
  }

  it should "parse window with tumbling minutes" in {
    val source  = """
      in x: Int
      result = Process(x) with window: tumbling(5min)
      out result
    """
    val options = parseAndGetOptions(source)
    options.window shouldBe Some(WindowSpec.Tumbling(Duration(5, DurationUnit.Minutes)))
  }

  // ============================================================================
  // JoinStrategySpec parsing: combine_latest, zip, buffer
  // ============================================================================

  it should "parse join with combine_latest" in {
    val source  = """
      in x: Int
      result = Process(x) with join: combine_latest
      out result
    """
    val options = parseAndGetOptions(source)
    options.join shouldBe Some(JoinStrategySpec.CombineLatest)
  }

  it should "parse join with zip" in {
    val source  = """
      in x: Int
      result = Process(x) with join: zip
      out result
    """
    val options = parseAndGetOptions(source)
    options.join shouldBe Some(JoinStrategySpec.Zip)
  }

  it should "parse join with buffer and timeout" in {
    val source  = """
      in x: Int
      result = Process(x) with join: buffer(5s)
      out result
    """
    val options = parseAndGetOptions(source)
    options.join shouldBe Some(JoinStrategySpec.Buffer(Duration(5, DurationUnit.Seconds)))
  }

  // ============================================================================
  // Combined streaming and existing options
  // ============================================================================

  it should "parse streaming options combined with existing options" in {
    val source  = """
      in x: Int
      result = Process(x) with retry: 3, timeout: 30s, batch: 100, window: tumbling(5s), join: combine_latest
      out result
    """
    val options = parseAndGetOptions(source)
    options.retry shouldBe Some(3)
    options.timeout shouldBe Some(Duration(30, DurationUnit.Seconds))
    options.batch shouldBe Some(100)
    options.window shouldBe Some(WindowSpec.Tumbling(Duration(5, DurationUnit.Seconds)))
    options.join shouldBe Some(JoinStrategySpec.CombineLatest)
  }

  it should "parse all streaming options together" in {
    val source  = """
      in x: Int
      result = Process(x) with batch: 50, batch_timeout: 5s, checkpoint: 30s, join: zip
      out result
    """
    val options = parseAndGetOptions(source)
    options.batch shouldBe Some(50)
    options.batchTimeout shouldBe Some(Duration(5, DurationUnit.Seconds))
    options.checkpoint shouldBe Some(Duration(30, DurationUnit.Seconds))
    options.join shouldBe Some(JoinStrategySpec.Zip)
  }

  // ============================================================================
  // @source annotation parsing
  // ============================================================================

  it should "parse @source annotation on input with connector type only" in {
    val source    = """
      @source("websocket")
      in events: String
      out events
    """
    val program   = parseProgram(source)
    val inputDecl = program.declarations.collectFirst { case i: Declaration.InputDecl => i }.get
    inputDecl.annotations should have size 1
    inputDecl.annotations.head shouldBe a[Annotation.Source]
    val srcAnnot = inputDecl.annotations.head.asInstanceOf[Annotation.Source]
    srcAnnot.connector shouldBe "websocket"
    srcAnnot.properties shouldBe empty
  }

  it should "parse @source annotation with properties" in {
    val source    = """
      @source("websocket", uri: "ws://localhost:8080")
      in events: String
      out events
    """
    val program   = parseProgram(source)
    val inputDecl = program.declarations.collectFirst { case i: Declaration.InputDecl => i }.get
    inputDecl.annotations should have size 1
    val srcAnnot = inputDecl.annotations.head.asInstanceOf[Annotation.Source]
    srcAnnot.connector shouldBe "websocket"
    srcAnnot.properties should have size 1
    srcAnnot.properties("uri").value shouldBe Expression.StringLit("ws://localhost:8080")
  }

  it should "parse @source annotation with multiple properties" in {
    val source    = """
      @source("http-sse", url: "https://api.example.com/events", reconnect: true)
      in events: String
      out events
    """
    val program   = parseProgram(source)
    val inputDecl = program.declarations.collectFirst { case i: Declaration.InputDecl => i }.get
    val srcAnnot  = inputDecl.annotations.head.asInstanceOf[Annotation.Source]
    srcAnnot.connector shouldBe "http-sse"
    srcAnnot.properties should have size 2
    srcAnnot.properties("url").value shouldBe Expression.StringLit("https://api.example.com/events")
    srcAnnot.properties("reconnect").value shouldBe Expression.BoolLit(true)
  }

  // ============================================================================
  // @sink annotation parsing
  // ============================================================================

  it should "parse @sink annotation on output with connector type only" in {
    val source     = """
      in x: String
      @sink("websocket")
      out x
    """
    val program    = parseProgram(source)
    val outputDecl = program.declarations.collectFirst { case o: Declaration.OutputDecl => o }.get
    outputDecl.annotations should have size 1
    val sinkAnnot = outputDecl.annotations.head.asInstanceOf[Annotation.Sink]
    sinkAnnot.connector shouldBe "websocket"
    sinkAnnot.properties shouldBe empty
  }

  it should "parse @sink annotation with properties" in {
    val source     = """
      in x: String
      @sink("websocket", uri: "ws://localhost:9090")
      out x
    """
    val program    = parseProgram(source)
    val outputDecl = program.declarations.collectFirst { case o: Declaration.OutputDecl => o }.get
    val sinkAnnot  = outputDecl.annotations.head.asInstanceOf[Annotation.Sink]
    sinkAnnot.connector shouldBe "websocket"
    sinkAnnot.properties should have size 1
    sinkAnnot.properties("uri").value shouldBe Expression.StringLit("ws://localhost:9090")
  }

  // ============================================================================
  // Combined annotations
  // ============================================================================

  it should "parse @source with @example on the same input" in {
    val source    = """
      @source("websocket", uri: "ws://localhost:8080")
      @example("hello")
      in text: String
      out text
    """
    val program   = parseProgram(source)
    val inputDecl = program.declarations.collectFirst { case i: Declaration.InputDecl => i }.get
    inputDecl.annotations should have size 2
    inputDecl.annotations(0) shouldBe a[Annotation.Source]
    inputDecl.annotations(1) shouldBe a[Annotation.Example]
  }

  it should "parse output without @sink annotation" in {
    val source     = """
      in x: String
      out x
    """
    val program    = parseProgram(source)
    val outputDecl = program.declarations.collectFirst { case o: Declaration.OutputDecl => o }.get
    outputDecl.annotations shouldBe empty
  }
}
