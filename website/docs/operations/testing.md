---
title: "Testing Guide"
sidebar_position: 5
description: "Guide for unit testing, integration testing, mocking, and property-based testing patterns."
---

# Testing Guide

Comprehensive guide for testing Constellation Engine applications, covering unit testing, integration testing, mocking patterns, and property-based testing.

## Testing Stack

Constellation Engine uses the following testing libraries:

| Library | Version | Purpose |
|---------|---------|---------|
| ScalaTest | 3.2.17 | Core testing framework |
| ScalaCheck | 1.17 | Property-based testing |
| Mockito | 5.14.2 | Mocking external dependencies |
| Cats Effect Testing | - | Testing IO-based code |

## Running Tests

Use the `make` commands to run tests consistently across the project:

```bash
# Run all tests
make test

# Run tests for specific modules
make test-core       # Core type system tests
make test-compiler   # Parser and compiler tests
make test-lsp        # Language server tests
make test-dashboard  # Dashboard E2E tests (Playwright)

# Run tests with sbt directly (for filtering)
sbt "runtime/testOnly *ModuleBuilderTest"
sbt "langCompiler/testOnly *LangCompilerTest"
```

## Unit Testing Modules

### Basic Module Test Setup

Every test class should extend `AnyFlatSpec` with `Matchers` and import the cats-effect runtime:

```scala
package io.constellation

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MyModuleTest extends AnyFlatSpec with Matchers {
  // Tests go here
}
```

### Testing ModuleBuilder Modules

When testing modules built with `ModuleBuilder`, verify both the module metadata and execution behavior.

**Testing module metadata:**

```scala
import scala.concurrent.duration._

case class TestInput(x: Long, y: Long)
case class TestOutput(result: Long)

"ModuleBuilder" should "create a module with correct metadata" in {
  val module = ModuleBuilder
    .metadata(
      name = "AddNumbers",
      description = "Adds two numbers",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("math", "arithmetic")
    .moduleTimeout(5.seconds)
    .implementationPure[TestInput, TestOutput](in => TestOutput(in.x + in.y))
    .build

  module.spec.metadata.name shouldBe "AddNumbers"
  module.spec.metadata.description shouldBe "Adds two numbers"
  module.spec.metadata.majorVersion shouldBe 1
  module.spec.metadata.minorVersion shouldBe 0
  module.spec.metadata.tags shouldBe List("math", "arithmetic")
  module.spec.config.moduleTimeout shouldBe 5.seconds
}
```

**Testing type inference:**

```scala
it should "infer input/output types from case classes" in {
  val module = ModuleBuilder
    .metadata("TypedModule", "Tests type inference", 1, 0)
    .implementationPure[TestInput, TestOutput](in => TestOutput(in.x + in.y))
    .build

  // Verify inferred input types
  module.spec.consumes shouldBe Map(
    "x" -> CType.CInt,
    "y" -> CType.CInt
  )

  // Verify inferred output types
  module.spec.produces shouldBe Map(
    "result" -> CType.CInt
  )
}
```

**Testing with complex types:**

```scala
it should "handle nested case classes" in {
  case class NestedInput(values: List[Long], label: String)
  case class NestedOutput(sum: Long, count: Long)

  val module = ModuleBuilder
    .metadata("Aggregator", "Aggregates values", 1, 0)
    .implementationPure[NestedInput, NestedOutput] { in =>
      NestedOutput(in.values.sum, in.values.length.toLong)
    }
    .build

  module.spec.consumes shouldBe Map(
    "values" -> CType.CList(CType.CInt),
    "label"  -> CType.CString
  )
  module.spec.produces shouldBe Map(
    "sum"   -> CType.CInt,
    "count" -> CType.CInt
  )
}
```

### Testing IO-Based Modules

For modules with side effects, test both the effect composition and the actual execution:

```scala
"IO-based module" should "execute side effects correctly" in {
  import cats.effect.Ref

  case class CountInput(increment: Long)
  case class CountOutput(total: Long)

  // Use a Ref to track side effects
  val test = for {
    counter <- Ref.of[IO, Long](0)
    module = ModuleBuilder
      .metadata("Counter", "Increments a counter", 1, 0)
      .implementation[CountInput, CountOutput] { in =>
        counter.updateAndGet(_ + in.increment).map(CountOutput(_))
      }
      .build
    // Execute the module multiple times
    _       <- module.execute(CountInput(5))
    _       <- module.execute(CountInput(3))
    result  <- module.execute(CountInput(2))
    counted <- counter.get
  } yield (result, counted)

  val (output, finalCount) = test.unsafeRunSync()
  output.total shouldBe 10
  finalCount shouldBe 10
}
```

### Testing Edge Cases

Always test edge cases for your module logic:

```scala
"Uppercase module" should "handle empty string" in {
  val module = createUppercaseModule()
  val result = module.execute(SingleInput("")).unsafeRunSync()
  result.result shouldBe ""
}

it should "handle already uppercase text" in {
  val module = createUppercaseModule()
  val result = module.execute(SingleInput("ALREADY UPPER")).unsafeRunSync()
  result.result shouldBe "ALREADY UPPER"
}

it should "handle mixed case with special characters" in {
  val module = createUppercaseModule()
  val result = module.execute(SingleInput("Hello, World! 123")).unsafeRunSync()
  result.result shouldBe "HELLO, WORLD! 123"
}
```

## Integration Testing Pipelines

Integration tests verify that complete pipelines execute correctly with all modules wired together.

### Setting Up Integration Tests

```scala
import io.constellation._
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.stdlib.StdLib

class PipelineIntegrationTest extends AnyFlatSpec with Matchers {

  /** Create a Constellation instance with all modules registered */
  private def createConstellation: IO[Constellation] =
    for {
      constellation <- ConstellationImpl.init
      allModules = StdLib.allModules.values.toList ++ customModules
      _ <- allModules.traverse(constellation.setModule)
    } yield constellation

  /** Helper to compile and run a pipeline */
  private def runPipeline(
      source: String,
      inputs: Map[String, CValue]
  ): IO[DataSignature] = {
    val compiler = StdLib.compiler
    for {
      constellation <- createConstellation
      compiled = compiler.compile(source, "test-pipeline") match {
        case Right(output) => output.pipeline
        case Left(errors)  => fail(s"Compilation failed: $errors")
      }
      signature <- constellation.run(compiled, inputs)
    } yield signature
  }
}
```

### Testing Pipeline Execution

```scala
"Text processing pipeline" should "chain multiple operations" in {
  val source = """
    in text: String
    trimmed = Trim(text)
    upper = Uppercase(trimmed)
    out upper
  """
  val inputs = Map("text" -> CValue.CString("  hello world  "))

  val result = runPipeline(source, inputs).unsafeRunSync()

  result.status shouldBe PipelineStatus.Completed
  result.outputs.get("upper") shouldBe Some(CValue.CString("HELLO WORLD"))
}

it should "handle conditional branching" in {
  val source = """
    in flag: Boolean
    in valueA: Int
    in valueB: Int
    result = if (flag) valueA else valueB
    out result
  """
  val inputs = Map(
    "flag"   -> CValue.CBoolean(true),
    "valueA" -> CValue.CInt(42),
    "valueB" -> CValue.CInt(0)
  )

  val result = runPipeline(source, inputs).unsafeRunSync()
  result.outputs.get("result") shouldBe Some(CValue.CInt(42))
}
```

### Testing Error Conditions

```scala
"Pipeline execution" should "fail on missing input" in {
  val source = """
    in required: String
    out required
  """
  val inputs = Map.empty[String, CValue]  // Missing required input

  val result = runPipeline(source, inputs).attempt.unsafeRunSync()

  result.isLeft shouldBe true
}

it should "fail on type mismatch" in {
  val source = """
    in text: String
    out text
  """
  val inputs = Map("text" -> CValue.CInt(42))  // Wrong type

  val result = runPipeline(source, inputs).attempt.unsafeRunSync()

  result.isLeft shouldBe true
  result.left.exists(_.getMessage.contains("different type")) shouldBe true
}
```

### Testing with Custom Modules

```scala
"Custom module pipeline" should "integrate with standard modules" in {
  case class ScoreInput(text: String)
  case class ScoreOutput(score: Long)

  val scoringModule = ModuleBuilder
    .metadata("SentimentScore", "Calculates sentiment score", 1, 0)
    .implementationPure[ScoreInput, ScoreOutput] { in =>
      // Simple heuristic: positive words add points
      val positiveWords = Set("good", "great", "excellent", "happy")
      val words = in.text.toLowerCase.split("\\s+")
      val score = words.count(positiveWords.contains).toLong
      ScoreOutput(score)
    }
    .build

  val test = for {
    constellation <- ConstellationImpl.init
    _ <- constellation.setModule(scoringModule)
    // ... rest of test
  } yield ()

  test.unsafeRunSync()
}
```

## Testing with Mocks

Use Mockito to mock external dependencies in unit tests.

### Setting Up Mocks

```scala
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach

class CacheBackendTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  private var mockClient: ExternalClient = _

  override def beforeEach(): Unit = {
    mockClient = mock(classOf[ExternalClient])
  }

  "CacheBackend" should "call client with correct arguments" in {
    when(mockClient.get("key")).thenReturn("value")

    val backend = new CacheBackend(mockClient)
    val result = backend.fetch("key")

    result shouldBe "value"
    verify(mockClient).get("key")
  }
}
```

### Mocking IO Operations

When mocking Cats Effect IO operations, wrap mock calls appropriately:

```scala
import cats.effect.IO

class AsyncServiceTest extends AnyFlatSpec with Matchers {

  "Service" should "handle successful responses" in {
    val mockHttp = mock(classOf[HttpClient])
    when(mockHttp.get(anyString())).thenReturn("response")

    val service = new MyService(mockHttp)
    val result = service.fetchData("endpoint").unsafeRunSync()

    result shouldBe "response"
    verify(mockHttp).get("endpoint")
  }

  it should "handle failures gracefully" in {
    val mockHttp = mock(classOf[HttpClient])
    when(mockHttp.get(anyString())).thenThrow(new RuntimeException("timeout"))

    val service = new MyService(mockHttp)
    val result = service.fetchData("endpoint").attempt.unsafeRunSync()

    result.isLeft shouldBe true
  }
}
```

### Verifying Method Calls

```scala
it should "call methods in correct order" in {
  val mockProcessor = mock(classOf[Processor])

  // Set up mock behavior
  when(mockProcessor.validate(any())).thenReturn(true)
  when(mockProcessor.process(any())).thenReturn("result")

  val service = new ProcessingService(mockProcessor)
  service.execute("input")

  // Verify order of calls
  val inOrder = org.mockito.Mockito.inOrder(mockProcessor)
  inOrder.verify(mockProcessor).validate("input")
  inOrder.verify(mockProcessor).process("input")
}
```

## Property-Based Testing

Use ScalaCheck to test properties that should hold for all inputs.

### Creating Generators

Constellation provides built-in generators in `ConstellationGenerators`:

```scala
import io.constellation.property.ConstellationGenerators._
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class TypeSystemPropertyTest extends AnyFlatSpec
    with Matchers
    with ScalaCheckPropertyChecks {

  "CValue" should "always have ctype matching construction type" in {
    forAll(genTypedValue) { case (expectedType, value) =>
      value.ctype shouldBe expectedType
    }
  }

  "CType generator" should "produce all primitive types" in {
    val primitiveTypes = Set[CType](
      CType.CString, CType.CInt, CType.CFloat, CType.CBoolean
    )
    val generated = (1 to 200).flatMap(_ => genPrimitiveCType.sample).toSet

    primitiveTypes.foreach { pt =>
      generated should contain(pt)
    }
  }
}
```

### Available Generators

| Generator | Description |
|-----------|-------------|
| `genPrimitiveCType` | Generates String, Int, Float, Boolean |
| `genCType(maxDepth)` | Generates any CType up to depth |
| `genCValueForType(ctype)` | Generates CValue matching a CType |
| `genTypedValue` | Generates matching (CType, CValue) pairs |
| `genFieldName` | Generates valid field/variable names |
| `genValidProgram` | Generates valid constellation-lang programs |

### Writing Custom Generators

```scala
import org.scalacheck.Gen

val genPositiveInt: Gen[Long] = Gen.choose(1L, 1000000L)

val genNonEmptyString: Gen[String] = Gen.alphaNumStr.suchThat(_.nonEmpty)

val genRecord: Gen[CType.CProduct] = for {
  numFields <- Gen.choose(1, 5)
  fields <- Gen.listOfN(numFields, for {
    name <- genFieldName
    typ  <- genPrimitiveCType
  } yield (name, typ))
} yield CType.CProduct(fields.toMap)
```

### Property Examples

```scala
"List operations" should "preserve length after map" in {
  forAll(Gen.listOf(Gen.alphaNumStr)) { list =>
    val mapped = list.map(_.toUpperCase)
    mapped.length shouldBe list.length
  }
}

"CProduct" should "have consistent field names" in {
  val productType = CType.CProduct(Map("x" -> CType.CInt, "y" -> CType.CString))

  forAll(genCValueForType(productType)) { value =>
    val product = value.asInstanceOf[CValue.CProduct]
    product.value.keys shouldBe Set("x", "y")
    product.structure shouldBe productType.structure
  }
}
```

## Test Fixtures

### Using Standard Fixtures

The `TestFixtures` object provides pre-built constellation programs for testing:

```scala
import io.constellation.lang.benchmark.TestFixtures

class CompilerBenchmarkTest extends AnyFlatSpec with Matchers {

  "Compiler" should "parse small programs quickly" in {
    val source = TestFixtures.smallProgram
    val compiler = LangCompiler.empty

    val startTime = System.nanoTime()
    val result = compiler.compile(source, "benchmark")
    val elapsed = (System.nanoTime() - startTime) / 1_000_000

    result.isRight shouldBe true
    elapsed should be < 5L  // Under 5ms
  }

  it should "handle medium programs" in {
    val source = TestFixtures.mediumProgram
    // ... test
  }

  it should "handle large programs" in {
    val source = TestFixtures.largeProgram
    // ... test
  }
}
```

### Creating Custom Fixtures

```scala
object MyTestFixtures {

  /** Simple text processing fixture */
  val textPipeline: String = """
    in text: String
    cleaned = Trim(text)
    upper = Uppercase(cleaned)
    out upper
  """.stripMargin

  /** Fixture with conditional logic */
  val conditionalPipeline: String = """
    in flag: Boolean
    in primary: Int
    in fallback: Int
    result = if (flag) primary else fallback
    out result
  """.stripMargin

  /** Generate parametric fixture */
  def generateChainedPipeline(chainLength: Int): String = {
    val sb = new StringBuilder
    sb.append("in text: String\n")
    sb.append("step0 = Trim(text)\n")
    for (i <- 1 until chainLength) {
      sb.append(s"step$i = Uppercase(step${i-1})\n")
    }
    sb.append(s"out step${chainLength - 1}\n")
    sb.toString
  }
}
```

### Example Data

```scala
object TestData {

  val sampleStrings: List[String] = List(
    "",
    " ",
    "hello",
    "HELLO WORLD",
    "  mixed Case  ",
    "special!@#$%^&*()"
  )

  val sampleInts: List[Long] = List(
    0L, 1L, -1L, Long.MaxValue, Long.MinValue, 42L
  )

  val sampleInputs: Map[String, CValue] = Map(
    "text"    -> CValue.CString("hello world"),
    "count"   -> CValue.CInt(10),
    "enabled" -> CValue.CBoolean(true),
    "rate"    -> CValue.CFloat(0.5)
  )
}
```

## Best Practices

### Test Organization

1. **One assertion per test** (when practical) for clearer failure messages
2. **Group related tests** using `describe` blocks or nested test classes
3. **Use descriptive names** that explain the scenario being tested
4. **Follow AAA pattern**: Arrange, Act, Assert

```scala
"Uppercase module" should "convert lowercase to uppercase" in {
  // Arrange
  val input = SingleInput("hello")
  val module = createUppercaseModule()

  // Act
  val result = module.execute(input).unsafeRunSync()

  // Assert
  result.result shouldBe "HELLO"
}
```

### Testing IO Code

1. **Use `unsafeRunSync()` only in tests** - production code should compose IO
2. **Test both success and failure paths** using `.attempt`
3. **Use `Ref` for tracking state changes** in concurrent tests

```scala
"Concurrent execution" should "not lose updates" in {
  import cats.effect.{Ref, IO}
  import cats.implicits._

  val test = for {
    counter <- Ref.of[IO, Int](0)
    _       <- (1 to 100).toList.parTraverse(_ => counter.update(_ + 1))
    final   <- counter.get
  } yield final

  val result = test.unsafeRunSync()
  result shouldBe 100
}
```

### Test Independence

Ensure tests do not depend on each other:

```scala
class IndependentTests extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  private var constellation: Constellation = _

  override def beforeEach(): Unit = {
    // Fresh instance for each test
    constellation = ConstellationImpl.init.unsafeRunSync()
  }

  "Test A" should "work independently" in {
    // Uses fresh constellation instance
  }

  "Test B" should "also work independently" in {
    // Also uses fresh constellation instance
  }
}
```

### Performance Testing

For performance-critical code, use the benchmark harness:

```scala
import io.constellation.lang.benchmark.BenchmarkHarness

"Parser performance" should "meet targets" in {
  val harness = new BenchmarkHarness()
  val result = harness.measureWithWarmup(
    warmupIterations = 10,
    measureIterations = 50
  ) {
    compiler.compile(TestFixtures.smallProgram, "perf-test")
  }

  result.median should be < 5.millis
  result.p99 should be < 10.millis
}
```

## Dashboard E2E Tests

For testing the web dashboard, see the dedicated E2E testing setup:

```bash
# Install Playwright
make install-dashboard-tests

# Run smoke tests
make test-dashboard-smoke

# Run full E2E suite
make test-dashboard

# Run with visible browser
cd dashboard-tests && npx playwright test --headed
```

Dashboard tests use Page Objects for maintainability. See `dashboard-tests/pages/` for examples.

## Troubleshooting

### Common Test Failures

| Error | Cause | Solution |
|-------|-------|----------|
| `TimeoutException` | IO not completing | Check for deadlocks, increase timeout |
| `NoSuchElementException` | Missing module | Ensure module is registered |
| `Type mismatch` | Wrong CValue type | Verify input types match spec |
| `Compilation failed` | Invalid source | Check syntax, undefined variables |

### Debugging Tips

1. **Add logging** with `IO.println` for debugging IO flows
2. **Use `.unsafeRunTimed(5.seconds)`** to catch hanging tests
3. **Run single test** with `sbt "testOnly *MyTest -- -z \"test name\""`
4. **Enable stack traces** with `sbt "testOnly *MyTest" -- -oF`

```scala
// Debug helper for IO
def debugIO[A](label: String)(io: IO[A]): IO[A] =
  IO.println(s"Starting: $label") *>
    io.flatTap(a => IO.println(s"Completed: $label = $a"))
```

## Next Steps

- [Deployment](./deployment.md) — Running tests in CI/CD pipelines
- [Performance Tuning](./performance-tuning.md) — Performance benchmarks and targets
- [Configuration](./configuration.md) — Test environment configuration
- [Runbook](./runbook.md) — Debugging production issues
