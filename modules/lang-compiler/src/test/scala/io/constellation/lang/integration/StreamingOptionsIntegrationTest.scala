package io.constellation.lang.integration

import io.constellation.lang.ast.*
import io.constellation.lang.compiler.IRModuleCallOptions
import io.constellation.lang.parser.ConstellationParser
import io.constellation.lang.semantic.{
  FunctionRegistry,
  FunctionSignature,
  SemanticType,
  TypeChecker
}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Integration tests for RFC-025 Phase 3 streaming options:
  *   - IR translation (WindowSpec/JoinStrategySpec -> serialized strings)
  *   - Type checker validation (range checks, mutual exclusivity, dependency warnings)
  */
class StreamingOptionsIntegrationTest extends AnyFlatSpec with Matchers {

  // ============================================================================
  // IRModuleCallOptions streaming field translation
  // ============================================================================

  "IRModuleCallOptions" should "convert streaming fields to core ModuleCallOptions" in {
    val irOpts = IRModuleCallOptions(
      batchSize = Some(100),
      batchTimeoutMs = Some(5000L),
      window = Some("tumbling:5000"),
      checkpointMs = Some(30000L),
      joinStrategy = Some("combine-latest")
    )

    val coreOpts = irOpts.toModuleCallOptions
    coreOpts.batchSize shouldBe Some(100)
    coreOpts.batchTimeoutMs shouldBe Some(5000L)
    coreOpts.window shouldBe Some("tumbling:5000")
    coreOpts.checkpointMs shouldBe Some(30000L)
    coreOpts.joinStrategy shouldBe Some("combine-latest")
  }

  it should "have isEmpty false when streaming fields are set" in {
    val irOpts = IRModuleCallOptions(batchSize = Some(50))
    irOpts.isEmpty shouldBe false
  }

  it should "have isEmpty true when all fields are empty" in {
    IRModuleCallOptions.empty.isEmpty shouldBe true
  }

  it should "convert all window spec formats correctly" in {
    // Tumbling
    val tumblingOpts = IRModuleCallOptions(window = Some("tumbling:5000"))
    tumblingOpts.toModuleCallOptions.window shouldBe Some("tumbling:5000")

    // Sliding
    val slidingOpts = IRModuleCallOptions(window = Some("sliding:10000:2000"))
    slidingOpts.toModuleCallOptions.window shouldBe Some("sliding:10000:2000")

    // Count
    val countOpts = IRModuleCallOptions(window = Some("count:100"))
    countOpts.toModuleCallOptions.window shouldBe Some("count:100")
  }

  it should "convert all join strategy formats correctly" in {
    val combineOpts = IRModuleCallOptions(joinStrategy = Some("combine-latest"))
    combineOpts.toModuleCallOptions.joinStrategy shouldBe Some("combine-latest")

    val zipOpts = IRModuleCallOptions(joinStrategy = Some("zip"))
    zipOpts.toModuleCallOptions.joinStrategy shouldBe Some("zip")

    val bufferOpts = IRModuleCallOptions(joinStrategy = Some("buffer:5000"))
    bufferOpts.toModuleCallOptions.joinStrategy shouldBe Some("buffer:5000")
  }

  // ============================================================================
  // Parser -> IR round-trip for streaming options
  // ============================================================================

  "Parser-to-IR round-trip" should "parse and preserve batch option" in {
    val source  = """
      in x: Int
      result = Process(x) with batch: 100
      out result
    """
    val options = parseAndGetOptions(source)
    options.batch shouldBe Some(100)
  }

  it should "parse and preserve window tumbling spec" in {
    val source  = """
      in x: Int
      result = Process(x) with window: tumbling(5s)
      out result
    """
    val options = parseAndGetOptions(source)
    options.window shouldBe Some(WindowSpec.Tumbling(Duration(5, DurationUnit.Seconds)))
  }

  it should "parse and preserve window sliding spec" in {
    val source  = """
      in x: Int
      result = Process(x) with window: sliding(10s, 2s)
      out result
    """
    val options = parseAndGetOptions(source)
    options.window shouldBe Some(
      WindowSpec.Sliding(Duration(10, DurationUnit.Seconds), Duration(2, DurationUnit.Seconds))
    )
  }

  it should "parse and preserve window count spec" in {
    val source  = """
      in x: Int
      result = Process(x) with window: count(50)
      out result
    """
    val options = parseAndGetOptions(source)
    options.window shouldBe Some(WindowSpec.Count(50))
  }

  it should "parse and preserve join combine_latest" in {
    val source  = """
      in x: Int
      result = Process(x) with join: combine_latest
      out result
    """
    val options = parseAndGetOptions(source)
    options.join shouldBe Some(JoinStrategySpec.CombineLatest)
  }

  it should "parse and preserve join buffer with timeout" in {
    val source  = """
      in x: Int
      result = Process(x) with join: buffer(3s)
      out result
    """
    val options = parseAndGetOptions(source)
    options.join shouldBe Some(JoinStrategySpec.Buffer(Duration(3, DurationUnit.Seconds)))
  }

  // ============================================================================
  // Type checker validation for streaming options
  // ============================================================================

  "Type checker" should "reject batch with zero value" in {
    val source = """
      in x: Int
      result = Process(x) with batch: 0
      out result
    """
    val result = compileWithStdSignatures(source)
    result.isLeft shouldBe true
    result.left.get.exists(_.message.contains("batch")) shouldBe true
  }

  it should "accept valid batch value" in {
    val source = """
      in x: Int
      result = Process(x) with batch: 50
      out result
    """
    // Should parse without error (type checking may fail for unknown module, but batch is valid)
    val options = parseAndGetOptions(source)
    options.batch shouldBe Some(50)
  }

  // ============================================================================
  // AST ModuleCallOptions isEmpty
  // ============================================================================

  "ModuleCallOptions" should "be non-empty with batch set" in {
    val opts = ModuleCallOptions(batch = Some(100))
    opts.isEmpty shouldBe false
  }

  it should "be non-empty with window set" in {
    val opts =
      ModuleCallOptions(window = Some(WindowSpec.Tumbling(Duration(5, DurationUnit.Seconds))))
    opts.isEmpty shouldBe false
  }

  it should "be non-empty with join set" in {
    val opts = ModuleCallOptions(join = Some(JoinStrategySpec.CombineLatest))
    opts.isEmpty shouldBe false
  }

  it should "be non-empty with checkpoint set" in {
    val opts = ModuleCallOptions(checkpoint = Some(Duration(30, DurationUnit.Seconds)))
    opts.isEmpty shouldBe false
  }

  it should "be empty with no fields set" in {
    ModuleCallOptions.empty.isEmpty shouldBe true
  }

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

  private def compileWithStdSignatures(
      source: String
  ): Either[List[CompileError], Unit] = {
    val parseResult = ConstellationParser.parse(source)
    parseResult match {
      case Left(err)      => Left(List(err))
      case Right(program) =>
        // Create a minimal function registry with "Process"
        val processSignature = FunctionSignature(
          name = "Process",
          params = List("x" -> SemanticType.SInt),
          returns = SemanticType.SInt,
          moduleName = "Process"
        )
        val registry = FunctionRegistry.empty
        registry.register(processSignature)

        TypeChecker.check(program, registry) match {
          case Right(_)   => Right(())
          case Left(errs) => Left(errs)
        }
    }
  }
}
