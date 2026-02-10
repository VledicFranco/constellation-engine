# Module Development Guide

**Complete practical guide for creating Constellation modules.**

This guide provides comprehensive, runnable examples for every module pattern you'll encounter. All examples are production-ready and follow best practices.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [ModuleBuilder API Overview](#modulebuilder-api-overview)
3. [Pure Modules (Side-Effect Free)](#pure-modules-side-effect-free)
4. [IO Modules (Effectful Operations)](#io-modules-effectful-operations)
5. [Async Modules (Concurrent Operations)](#async-modules-concurrent-operations)
6. [Error Handling Patterns](#error-handling-patterns)
7. [Custom Types and Complex Data](#custom-types-and-complex-data)
8. [Input/Output Patterns](#inputoutput-patterns)
9. [Module Registration](#module-registration)
10. [Testing Strategies](#testing-strategies)
11. [Common Patterns](#common-patterns)
12. [Best Practices](#best-practices)
13. [Anti-Patterns to Avoid](#anti-patterns-to-avoid)
14. [Advanced Patterns](#advanced-patterns)

---

## Quick Start

### Minimal Working Module

```scala
import cats.effect.IO
import io.constellation.*

// 1. Define input/output case classes
case class TextInput(text: String)
case class TextOutput(result: String)

// 2. Build the module
val uppercase: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "Uppercase",
    description = "Converts text to uppercase",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("text", "transform")
  .implementationPure[TextInput, TextOutput] { input =>
    TextOutput(input.text.toUpperCase)
  }
  .build

// 3. Register with Constellation
// In your application:
constellation.setModule(uppercase)

// 4. Use in constellation-lang
// in text: String
// result = Uppercase(text)
// out result
```

**Key Rules:**
1. Module name in `metadata()` MUST match usage in `.cst` files (case-sensitive)
2. Case class field names MUST match constellation-lang variable names
3. ALWAYS use case classes for input/output, NEVER tuples
4. Import `cats.implicits._` when using `.traverse` for module registration

---

## ModuleBuilder API Overview

### Builder Stages

```scala
// Stage 1: Initialize with metadata
ModuleBuilderInit
  .metadata(name, description, majorVersion, minorVersion, tags)

// Stage 2: Set implementation (transitions to typed ModuleBuilder)
  .implementationPure[Input, Output](fn)        // Pure function
  .implementation[Input, Output](fn)            // IO-based function
  .implementationWithContext[Input, Output](fn) // With execution context

// Stage 3: Build (finalize)
  .build         // For multi-field case classes
  .buildSimple   // For single-field wrappers
```

### Available Configuration Methods

```scala
ModuleBuilder
  .metadata("MyModule", "Description", 1, 0)
  .tags("category", "subcategory")              // Classification tags
  .inputsTimeout(5.seconds)                     // Timeout for input gathering
  .moduleTimeout(30.seconds)                    // Timeout for module execution
  .definitionContext(Map("key" -> json))        // Custom metadata
  .implementationPure[Input, Output] { input =>
    // implementation
  }
  .build
```

---

## Pure Modules (Side-Effect Free)

Use `implementationPure` for deterministic transformations without I/O.

### Text Transformation

```scala
import io.constellation.*

case class TextInput(text: String)
case class TextOutput(result: String)

val lowercase: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "Lowercase",
    description = "Converts all characters to lowercase",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("text", "transform")
  .implementationPure[TextInput, TextOutput] { input =>
    TextOutput(input.text.toLowerCase)
  }
  .build
```

### Multi-Field Input

```scala
case class ReplaceInput(text: String, find: String, replace: String)
case class ReplaceOutput(result: String)

val replace: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "Replace",
    description = "Replaces all occurrences of a substring",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("text", "transform")
  .implementationPure[ReplaceInput, ReplaceOutput] { input =>
    ReplaceOutput(input.text.replace(input.find, input.replace))
  }
  .build
```

**constellation-lang usage:**
```constellation
in text: String
in find: String
in replace: String
result = Replace(text, find, replace)
out result
```

### Numeric Computation

```scala
case class SumInput(numbers: List[Long])
case class SumOutput(total: Long)

val sumList: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "SumList",
    description = "Calculates the sum of all integers in a list",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("data", "aggregation")
  .implementationPure[SumInput, SumOutput] { input =>
    SumOutput(input.numbers.sum)
  }
  .build
```

### Boolean Logic

```scala
case class ContainsInput(text: String, substring: String)
case class ContainsOutput(contains: Boolean)

val contains: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "Contains",
    description = "Checks if text contains a substring",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("text", "analysis")
  .implementationPure[ContainsInput, ContainsOutput] { input =>
    ContainsOutput(input.text.contains(input.substring))
  }
  .build
```

### List Transformation

```scala
case class FilterInput(numbers: List[Long], threshold: Long)
case class FilterOutput(filtered: List[Long])

val filterGreaterThan: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "FilterGreaterThan",
    description = "Filters a list to keep only numbers greater than threshold",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("data", "filter")
  .implementationPure[FilterInput, FilterOutput] { input =>
    FilterOutput(input.numbers.filter(_ > input.threshold))
  }
  .build
```

### Complex Calculation with Edge Cases

```scala
case class AverageInput(numbers: List[Long])
case class AverageOutput(average: Double)

val average: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "Average",
    description = "Calculates arithmetic mean of numbers",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("data", "statistics")
  .implementationPure[AverageInput, AverageOutput] { input =>
    if (input.numbers.isEmpty) {
      AverageOutput(0.0)
    } else {
      AverageOutput(input.numbers.sum.toDouble / input.numbers.length)
    }
  }
  .build
```

---

## IO Modules (Effectful Operations)

Use `implementation` for operations with side effects (HTTP, DB, file I/O).

### HTTP API Call

```scala
import cats.effect.IO
import io.constellation.*
import org.http4s.client.Client
import org.http4s.Uri

case class ApiCallInput(endpoint: String, apiKey: String)
case class ApiCallOutput(data: String, statusCode: Int)

def createApiCallModule(httpClient: Client[IO]): Module.Uninitialized =
  ModuleBuilder
    .metadata(
      name = "FetchWeather",
      description = "Fetches weather data from external API",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("api", "http", "weather")
    .implementation[ApiCallInput, ApiCallOutput] { input =>
      for {
        uri <- IO.fromEither(Uri.fromString(input.endpoint))
        request = org.http4s.Request[IO](
          method = org.http4s.Method.GET,
          uri = uri
        ).putHeaders(
          org.http4s.headers.Authorization(
            org.http4s.Credentials.Token(
              org.http4s.headers.AuthScheme.Bearer,
              input.apiKey
            )
          )
        )
        response <- httpClient.expect[String](request)
      } yield ApiCallOutput(response, 200)
    }
    .build
```

### Database Query

```scala
import doobie.*
import doobie.implicits.*

case class UserQueryInput(userId: Long)
case class UserQueryOutput(name: String, email: String)

def createUserQueryModule(xa: Transactor[IO]): Module.Uninitialized =
  ModuleBuilder
    .metadata(
      name = "GetUser",
      description = "Fetches user information from database",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("database", "user")
    .implementation[UserQueryInput, UserQueryOutput] { input =>
      sql"""
        SELECT name, email
        FROM users
        WHERE id = ${input.userId}
      """
        .query[(String, String)]
        .unique
        .transact(xa)
        .map { case (name, email) => UserQueryOutput(name, email) }
    }
    .build
```

### File I/O

```scala
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters.*

case class ReadFileInput(path: String)
case class ReadFileOutput(content: String)

val readFile: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "ReadFile",
    description = "Reads entire file content as string",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("io", "file")
  .implementation[ReadFileInput, ReadFileOutput] { input =>
    IO {
      val path = Paths.get(input.path)
      val lines = Files.readAllLines(path).asScala
      ReadFileOutput(lines.mkString("\n"))
    }
  }
  .build
```

### Write to File

```scala
import java.nio.file.{Files, Paths, StandardOpenOption}

case class WriteFileInput(path: String, content: String)
case class WriteFileOutput(bytesWritten: Long)

val writeFile: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "WriteFile",
    description = "Writes content to file (overwrites if exists)",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("io", "file")
  .implementation[WriteFileInput, WriteFileOutput] { input =>
    IO {
      val path = Paths.get(input.path)
      val bytes = input.content.getBytes("UTF-8")
      Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
      WriteFileOutput(bytes.length.toLong)
    }
  }
  .build
```

### Logging with Side Effects

```scala
import org.typelevel.log4cats.Logger

case class LogMessageInput(level: String, message: String)
case class LogMessageOutput(logged: Boolean)

def createLoggerModule(logger: Logger[IO]): Module.Uninitialized =
  ModuleBuilder
    .metadata(
      name = "LogMessage",
      description = "Logs a message at specified level",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("logging", "observability")
    .implementation[LogMessageInput, LogMessageOutput] { input =>
      val logIO = input.level.toLowerCase match {
        case "debug" => logger.debug(input.message)
        case "info"  => logger.info(input.message)
        case "warn"  => logger.warn(input.message)
        case "error" => logger.error(input.message)
        case _       => logger.info(input.message)
      }
      logIO.as(LogMessageOutput(logged = true))
    }
    .build
```

---

## Async Modules (Concurrent Operations)

Modules with concurrent operations, timeouts, and parallelism.

### Slow Operation with Timeout

```scala
import scala.concurrent.duration.*

case class SlowQueryInput(query: String)
case class SlowQueryOutput(result: String)

val slowQuery: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "SlowQuery",
    description = "Simulates a slow database query with 500ms latency",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("resilience", "slow")
  .moduleTimeout(1.second)  // Fail if takes longer than 1 second
  .implementation[SlowQueryInput, SlowQueryOutput] { input =>
    IO.sleep(500.millis) >> IO {
      SlowQueryOutput(s"Result for: ${input.query}")
    }
  }
  .build
```

### Parallel Batch Processing

```scala
import cats.implicits.*

case class BatchProcessInput(items: List[String])
case class BatchProcessOutput(results: List[String])

val batchProcess: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "BatchProcess",
    description = "Processes multiple items in parallel",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("batch", "parallel")
  .implementation[BatchProcessInput, BatchProcessOutput] { input =>
    // Process all items in parallel
    input.items.parTraverse { item =>
      IO.sleep(100.millis) >> IO(item.toUpperCase)
    }.map { results =>
      BatchProcessOutput(results)
    }
  }
  .build
```

### Retry Logic

```scala
import cats.effect.std.Random
import cats.implicits.*

case class RetryableInput(request: String)
case class RetryableOutput(response: String)

val retryableService: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "RetryableService",
    description = "Service with automatic retry on failure",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("resilience", "retry")
  .implementation[RetryableInput, RetryableOutput] { input =>
    def attempt(retriesLeft: Int): IO[RetryableOutput] =
      Random.scalaUtilRandom[IO].flatMap { rand =>
        rand.nextDouble.flatMap { prob =>
          if (prob < 0.7 && retriesLeft > 0) {
            // 70% chance of failure, retry
            IO.sleep(100.millis) >> attempt(retriesLeft - 1)
          } else if (prob < 0.7) {
            // Out of retries
            IO.raiseError(new RuntimeException(s"Failed after retries: ${input.request}"))
          } else {
            // Success
            IO.pure(RetryableOutput(s"Success for: ${input.request}"))
          }
        }
      }

    attempt(retriesLeft = 3)
  }
  .build
```

### Race Between Operations

```scala
import cats.effect.kernel.Outcome
import cats.implicits.*

case class RaceInput(fastUrl: String, slowUrl: String)
case class RaceOutput(winner: String, result: String)

def createRaceModule(httpClient: Client[IO]): Module.Uninitialized =
  ModuleBuilder
    .metadata(
      name = "RaceRequests",
      description = "Makes two requests and returns whichever completes first",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("http", "race", "performance")
    .implementation[RaceInput, RaceOutput] { input =>
      val fastRequest = httpClient.expect[String](input.fastUrl)
      val slowRequest = httpClient.expect[String](input.slowUrl)

      IO.race(fastRequest, slowRequest).map {
        case Left(fastResult) => RaceOutput("fast", fastResult)
        case Right(slowResult) => RaceOutput("slow", slowResult)
      }
    }
    .build
```

---

## Error Handling Patterns

### Graceful Error Handling

```scala
case class SafeDivideInput(numerator: Long, denominator: Long)
case class SafeDivideOutput(result: Double)

val safeDivide: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "SafeDivide",
    description = "Divides two numbers with zero-check",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("math", "safe")
  .implementation[SafeDivideInput, SafeDivideOutput] { input =>
    if (input.denominator == 0) {
      IO.raiseError(new ArithmeticException("Division by zero"))
    } else {
      IO.pure(SafeDivideOutput(input.numerator.toDouble / input.denominator))
    }
  }
  .build
```

### Validation with Errors

```scala
case class EmailInput(email: String)
case class EmailOutput(valid: Boolean, message: String)

val validateEmail: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "ValidateEmail",
    description = "Validates email format",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("validation", "email")
  .implementationPure[EmailInput, EmailOutput] { input =>
    val emailRegex = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".r
    emailRegex.findFirstIn(input.email) match {
      case Some(_) => EmailOutput(valid = true, message = "Valid email")
      case None    => EmailOutput(valid = false, message = "Invalid email format")
    }
  }
  .build
```

### Error Recovery with Fallback

```scala
case class FallbackInput(primary: String, fallback: String)
case class FallbackOutput(result: String, source: String)

def createFallbackModule(httpClient: Client[IO]): Module.Uninitialized =
  ModuleBuilder
    .metadata(
      name = "FetchWithFallback",
      description = "Tries primary URL, falls back to secondary on failure",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("http", "resilience", "fallback")
    .implementation[FallbackInput, FallbackOutput] { input =>
      httpClient.expect[String](input.primary)
        .map(result => FallbackOutput(result, "primary"))
        .handleErrorWith { _ =>
          httpClient.expect[String](input.fallback)
            .map(result => FallbackOutput(result, "fallback"))
        }
    }
    .build
```

### Timeout with Default Value

```scala
import scala.concurrent.duration.*

case class TimeoutInput(url: String, timeoutMs: Long)
case class TimeoutOutput(result: String, timedOut: Boolean)

def createTimeoutModule(httpClient: Client[IO]): Module.Uninitialized =
  ModuleBuilder
    .metadata(
      name = "FetchWithTimeout",
      description = "Fetches URL with timeout, returns default on timeout",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("http", "timeout", "resilience")
    .implementation[TimeoutInput, TimeoutOutput] { input =>
      val request = httpClient.expect[String](input.url)
        .timeout(input.timeoutMs.millis)
        .map(result => TimeoutOutput(result, timedOut = false))
        .handleErrorWith {
          case _: java.util.concurrent.TimeoutException =>
            IO.pure(TimeoutOutput("Request timed out", timedOut = true))
          case other =>
            IO.raiseError(other)
        }
      request
    }
    .build
```

---

## Custom Types and Complex Data

### Working with Lists

```scala
case class JoinInput(items: List[String], separator: String)
case class JoinOutput(result: String)

val joinStrings: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "JoinStrings",
    description = "Joins list of strings with separator",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("string", "list")
  .implementationPure[JoinInput, JoinOutput] { input =>
    JoinOutput(input.items.mkString(input.separator))
  }
  .build
```

### List to List Transformation

```scala
case class MapMultiplyInput(numbers: List[Long], multiplier: Long)
case class MapMultiplyOutput(results: List[Long])

val multiplyEach: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "MultiplyEach",
    description = "Multiplies each number by a constant",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("math", "list")
  .implementationPure[MapMultiplyInput, MapMultiplyOutput] { input =>
    MapMultiplyOutput(input.numbers.map(_ * input.multiplier))
  }
  .build
```

### Complex Nested Structures

```scala
// Note: Complex nested types require custom type tags
// For production use, define custom CTypeTag instances

case class Person(name: String, age: Long, emails: List[String])
case class ProcessPeopleInput(people: List[Person])
case class ProcessPeopleOutput(count: Long, averageAge: Double)

// This pattern works when you stay within Constellation's supported types
// For truly custom types, see Advanced Patterns section

val processPeople: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "ProcessPeople",
    description = "Analyzes a list of people",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("data", "analysis")
  .implementationPure[ProcessPeopleInput, ProcessPeopleOutput] { input =>
    val avgAge = if (input.people.isEmpty) 0.0
                 else input.people.map(_.age).sum.toDouble / input.people.length
    ProcessPeopleOutput(input.people.length.toLong, avgAge)
  }
  .build
```

### Type Conversion

```scala
case class ParseIntInput(text: String)
case class ParseIntOutput(number: Long, success: Boolean)

val parseIntSafe: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "ParseIntSafe",
    description = "Safely parses string to integer",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("conversion", "parse")
  .implementationPure[ParseIntInput, ParseIntOutput] { input =>
    try {
      ParseIntOutput(input.text.toLong, success = true)
    } catch {
      case _: NumberFormatException =>
        ParseIntOutput(0L, success = false)
    }
  }
  .build
```

### Format Conversion

```scala
case class FormatNumberInput(number: Long)
case class FormatNumberOutput(formatted: String)

val formatNumber: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "FormatNumber",
    description = "Formats number with thousand separators",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("format", "display")
  .implementationPure[FormatNumberInput, FormatNumberOutput] { input =>
    val formatter = java.text.NumberFormat.getIntegerInstance
    FormatNumberOutput(formatter.format(input.number))
  }
  .build
```

---

## Input/Output Patterns

### Single Input, Single Output

```scala
case class SingleIn(value: String)
case class SingleOut(result: String)

val singleModule: Module.Uninitialized = ModuleBuilder
  .metadata("Transform", "Transforms single value", 1, 0)
  .implementationPure[SingleIn, SingleOut] { input =>
    SingleOut(input.value.toUpperCase)
  }
  .build
```

### Multiple Inputs, Single Output

```scala
case class MultipleIn(a: String, b: String, c: Long)
case class SingleOut(result: String)

val multipleInputs: Module.Uninitialized = ModuleBuilder
  .metadata("Combine", "Combines multiple inputs", 1, 0)
  .implementationPure[MultipleIn, SingleOut] { input =>
    SingleOut(s"${input.a}-${input.b}-${input.c}")
  }
  .build
```

### Single Input, Multiple Outputs

```scala
case class SingleIn(text: String)
case class MultipleOut(upper: String, lower: String, length: Long)

val multipleOutputs: Module.Uninitialized = ModuleBuilder
  .metadata("Analyze", "Produces multiple outputs", 1, 0)
  .implementationPure[SingleIn, MultipleOut] { input =>
    MultipleOut(
      upper = input.text.toUpperCase,
      lower = input.text.toLowerCase,
      length = input.text.length.toLong
    )
  }
  .build
```

**constellation-lang usage:**
```constellation
in text: String
analysis = Analyze(text)
upperText = analysis.upper
lowerText = analysis.lower
textLen = analysis.length
out upperText
out lowerText
out textLen
```

### Optional Inputs (with defaults)

```scala
case class OptionalIn(required: String, optional: String)
case class OptionalOut(result: String)

val withOptional: Module.Uninitialized = ModuleBuilder
  .metadata("WithDefault", "Handles optional inputs", 1, 0)
  .implementationPure[OptionalIn, OptionalOut] { input =>
    val opt = if (input.optional.isEmpty) "DEFAULT" else input.optional
    OptionalOut(s"${input.required}-$opt")
  }
  .build
```

### List Input, Scalar Output

```scala
case class ListIn(items: List[String])
case class ScalarOut(result: String)

val aggregate: Module.Uninitialized = ModuleBuilder
  .metadata("Aggregate", "Aggregates list to scalar", 1, 0)
  .implementationPure[ListIn, ScalarOut] { input =>
    ScalarOut(input.items.mkString(", "))
  }
  .build
```

### Scalar Input, List Output

```scala
case class ScalarIn(count: Long)
case class ListOut(items: List[Long])

val generate: Module.Uninitialized = ModuleBuilder
  .metadata("GenerateList", "Generates list from scalar", 1, 0)
  .implementationPure[ScalarIn, ListOut] { input =>
    ListOut((1L to input.count).toList)
  }
  .build
```

---

## Module Registration

### Single Module Registration

```scala
import cats.effect.IO
import io.constellation.Constellation

val constellation: Constellation = ??? // from ConstellationImpl.init

// Register one module
constellation.setModule(uppercase).unsafeRunSync()
```

### Multiple Module Registration

```scala
import cats.implicits.* // Required for .traverse

val modules: List[Module.Uninitialized] = List(
  uppercase,
  lowercase,
  trim,
  replace
)

// Register all modules
modules.traverse(constellation.setModule).unsafeRunSync()
```

### Organizing Modules by Category

```scala
object TextModules {
  val uppercase: Module.Uninitialized = ???
  val lowercase: Module.Uninitialized = ???
  val trim: Module.Uninitialized = ???

  val all: List[Module.Uninitialized] = List(
    uppercase,
    lowercase,
    trim
  )
}

object DataModules {
  val sumList: Module.Uninitialized = ???
  val average: Module.Uninitialized = ???

  val all: List[Module.Uninitialized] = List(
    sumList,
    average
  )
}

// Register all modules from both categories
val allModules = TextModules.all ++ DataModules.all
allModules.traverse(constellation.setModule).unsafeRunSync()
```

### Module Registry Pattern

```scala
import io.constellation.lang.semantic.*

object ExampleLib {
  // Define function signatures for compiler
  private val uppercaseSig = FunctionSignature(
    name = "Uppercase",
    params = List("text" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "Uppercase"
  )

  val allSignatures: List[FunctionSignature] = List(
    uppercaseSig
    // ... more signatures
  )

  // Map of modules by name
  def allModules: Map[String, Module.Uninitialized] = Map(
    "Uppercase" -> TextModules.uppercase
    // ... more modules
  )

  // Register with compiler builder
  def registerAll(builder: LangCompilerBuilder): LangCompilerBuilder =
    allSignatures.foldLeft(builder) { (b, sig) =>
      b.withFunction(sig)
    }
}
```

### Runtime Registration with Error Handling

```scala
import cats.effect.IO
import cats.implicits.*

def registerModules(
  constellation: Constellation,
  modules: List[Module.Uninitialized]
): IO[Unit] = {
  modules.traverse { module =>
    constellation.setModule(module)
      .handleErrorWith { error =>
        IO.println(s"Failed to register ${module.spec.name}: ${error.getMessage}")
          .as(())
      }
  }.void
}
```

---

## Testing Strategies

### Basic Module Test

```scala
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UppercaseTest extends AnyFlatSpec with Matchers {

  "Uppercase" should "convert text to uppercase" in {
    // Arrange
    val input = TextInput("hello world")

    // Act - call module implementation directly
    val module = TextModules.uppercase
    val impl = module.implementation
    val result = impl(
      CValue.CProduct(Map("text" -> CValue.CString("hello world")))
    ).unsafeRunSync()

    // Assert
    result match {
      case Module.Produces(output, _) =>
        output should matchPattern {
          case CValue.CProduct(fields) if
            fields.get("result") == Some(CValue.CString("HELLO WORLD")) =>
        }
    }
  }
}
```

### Integration Test with Constellation Runtime

```scala
import cats.effect.IO
import cats.implicits.*
import io.constellation.*
import io.constellation.impl.ConstellationImpl

class TextModulesTest extends AnyFlatSpec with Matchers {

  private def createConstellation: IO[Constellation] =
    for {
      constellation <- ConstellationImpl.init
      _ <- TextModules.all.traverse(constellation.setModule)
    } yield constellation

  private def runModule[T](
    source: String,
    dagName: String,
    inputs: Map[String, CValue],
    outputName: String
  )(extract: CValue => T): T = {
    val compiler = ExampleLib.compiler

    val test = for {
      constellation <- createConstellation
      compiled = compiler.compile(source, dagName).toOption.get
      sig <- constellation.run(compiled.pipeline, inputs)
    } yield sig.outputs.get(outputName)

    val result = test.unsafeRunSync()
    result shouldBe defined
    extract(result.get)
  }

  "Uppercase" should "work end-to-end" in {
    val source = """
      in text: String
      result = Uppercase(text)
      out result
    """

    val inputs = Map("text" -> CValue.CString("hello"))

    val result = runModule[String](source, "test", inputs, "result") {
      case CValue.CString(v) => v
    }

    result shouldBe "HELLO"
  }
}
```

### Property-Based Testing

```scala
import org.scalacheck.Gen
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class UppercasePropertyTest extends AnyPropSpec with ScalaCheckPropertyChecks {

  property("Uppercase should preserve length") {
    forAll { (text: String) =>
      val input = TextInput(text)
      val impl = TextModules.uppercase.implementation
      val result = impl(
        CValue.CProduct(Map("text" -> CValue.CString(text)))
      ).unsafeRunSync()

      result match {
        case Module.Produces(CValue.CProduct(fields), _) =>
          fields.get("result") match {
            case Some(CValue.CString(upper)) =>
              upper.length shouldBe text.length
            case _ => fail("Expected string output")
          }
      }
    }
  }

  property("Uppercase should be idempotent") {
    forAll { (text: String) =>
      val upper1 = text.toUpperCase
      val upper2 = upper1.toUpperCase
      upper1 shouldBe upper2
    }
  }
}
```

### Testing Edge Cases

```scala
class EdgeCaseTest extends AnyFlatSpec with Matchers {

  "Trim" should "handle empty string" in {
    val result = runTest(TrimInput(""))
    result.result shouldBe ""
  }

  it should "handle whitespace-only string" in {
    val result = runTest(TrimInput("   \t\n  "))
    result.result shouldBe ""
  }

  it should "preserve internal whitespace" in {
    val result = runTest(TrimInput("  hello   world  "))
    result.result shouldBe "hello   world"
  }

  "Average" should "return 0 for empty list" in {
    val result = averageTest(AverageInput(List.empty))
    result.average shouldBe 0.0
  }

  it should "handle single element" in {
    val result = averageTest(AverageInput(List(5)))
    result.average shouldBe 5.0
  }

  it should "handle negative numbers" in {
    val result = averageTest(AverageInput(List(-5, -10, -15)))
    result.average shouldBe -10.0
  }

  private def runTest(input: TrimInput): TrimOutput = {
    // implementation
    ???
  }

  private def averageTest(input: AverageInput): AverageOutput = {
    // implementation
    ???
  }
}
```

### Testing Error Handling

```scala
class ErrorHandlingTest extends AnyFlatSpec with Matchers {

  "SafeDivide" should "fail on division by zero" in {
    val input = SafeDivideInput(numerator = 10, denominator = 0)
    val impl = mathModules.safeDivide.implementation

    val result = impl(
      CValue.CProduct(Map(
        "numerator" -> CValue.CInt(10),
        "denominator" -> CValue.CInt(0)
      ))
    ).attempt.unsafeRunSync()

    result shouldBe a [Left[_, _]]
    result.left.map(_.getMessage should include ("Division by zero"))
  }

  "ParseInt" should "return success=false for invalid input" in {
    val result = parseIntTest(ParseIntInput("not a number"))
    result.success shouldBe false
    result.number shouldBe 0L
  }
}
```

---

## Common Patterns

### Text Processing Pipeline

```scala
object TextModules {
  case class TextInput(text: String)
  case class TextOutput(result: String)

  val uppercase = buildTextModule("Uppercase", "Convert to uppercase", _.toUpperCase)
  val lowercase = buildTextModule("Lowercase", "Convert to lowercase", _.toLowerCase)
  val trim = buildTextModule("Trim", "Remove whitespace", _.trim)

  private def buildTextModule(
    name: String,
    description: String,
    transform: String => String
  ): Module.Uninitialized = {
    ModuleBuilder
      .metadata(name, description, 1, 0)
      .tags("text", "transform")
      .implementationPure[TextInput, TextOutput] { input =>
        TextOutput(transform(input.text))
      }
      .build
  }

  val all = List(uppercase, lowercase, trim)
}
```

**Usage in constellation-lang:**
```constellation
in text: String
trimmed = Trim(text)
upper = Uppercase(trimmed)
out upper
```

### Data Aggregation Pipeline

```scala
object AggregationModules {
  case class ListInput(numbers: List[Long])

  val sum = ModuleBuilder
    .metadata("Sum", "Sum of all numbers", 1, 0)
    .implementationPure[ListInput, SingleOutput] { input =>
      SingleOutput(input.numbers.sum)
    }
    .build

  val count = ModuleBuilder
    .metadata("Count", "Count of elements", 1, 0)
    .implementationPure[ListInput, SingleOutput] { input =>
      SingleOutput(input.numbers.length.toLong)
    }
    .build

  val max = ModuleBuilder
    .metadata("Max", "Maximum value", 1, 0)
    .implementationPure[ListInput, SingleOutput] { input =>
      SingleOutput(if (input.numbers.isEmpty) 0L else input.numbers.max)
    }
    .build

  case class SingleOutput(value: Long)

  val all = List(sum, count, max)
}
```

### API Client Pattern

```scala
class ApiClientModules(httpClient: Client[IO], baseUrl: String, apiKey: String) {

  case class GetRequest(endpoint: String)
  case class GetResponse(data: String, statusCode: Int)

  case class PostRequest(endpoint: String, body: String)
  case class PostResponse(data: String, statusCode: Int)

  val get: Module.Uninitialized = ModuleBuilder
    .metadata("ApiGet", "GET request to API", 1, 0)
    .tags("api", "http")
    .implementation[GetRequest, GetResponse] { input =>
      val url = s"$baseUrl/${input.endpoint}"
      for {
        uri <- IO.fromEither(Uri.fromString(url))
        request = org.http4s.Request[IO](
          method = org.http4s.Method.GET,
          uri = uri
        ).putHeaders(
          org.http4s.headers.Authorization(
            org.http4s.Credentials.Token(org.http4s.headers.AuthScheme.Bearer, apiKey)
          )
        )
        response <- httpClient.expect[String](request)
      } yield GetResponse(response, 200)
    }
    .build

  val post: Module.Uninitialized = ModuleBuilder
    .metadata("ApiPost", "POST request to API", 1, 0)
    .tags("api", "http")
    .implementation[PostRequest, PostResponse] { input =>
      val url = s"$baseUrl/${input.endpoint}"
      for {
        uri <- IO.fromEither(Uri.fromString(url))
        request = org.http4s.Request[IO](
          method = org.http4s.Method.POST,
          uri = uri
        ).putHeaders(
          org.http4s.headers.Authorization(
            org.http4s.Credentials.Token(org.http4s.headers.AuthScheme.Bearer, apiKey)
          )
        ).withEntity(input.body)
        response <- httpClient.expect[String](request)
      } yield PostResponse(response, 200)
    }
    .build

  val all = List(get, post)
}
```

### Database CRUD Pattern

```scala
import doobie.*
import doobie.implicits.*

class DatabaseModules(xa: Transactor[IO]) {

  case class CreateUserInput(name: String, email: String)
  case class CreateUserOutput(userId: Long)

  case class GetUserInput(userId: Long)
  case class GetUserOutput(name: String, email: String)

  case class UpdateUserInput(userId: Long, name: String, email: String)
  case class UpdateUserOutput(updated: Boolean)

  case class DeleteUserInput(userId: Long)
  case class DeleteUserOutput(deleted: Boolean)

  val createUser: Module.Uninitialized = ModuleBuilder
    .metadata("CreateUser", "Creates new user", 1, 0)
    .tags("database", "user", "create")
    .implementation[CreateUserInput, CreateUserOutput] { input =>
      sql"""
        INSERT INTO users (name, email)
        VALUES (${input.name}, ${input.email})
      """.update
        .withUniqueGeneratedKeys[Long]("id")
        .transact(xa)
        .map(id => CreateUserOutput(id))
    }
    .build

  val getUser: Module.Uninitialized = ModuleBuilder
    .metadata("GetUser", "Retrieves user by ID", 1, 0)
    .tags("database", "user", "read")
    .implementation[GetUserInput, GetUserOutput] { input =>
      sql"""
        SELECT name, email FROM users WHERE id = ${input.userId}
      """.query[(String, String)]
        .unique
        .transact(xa)
        .map { case (name, email) => GetUserOutput(name, email) }
    }
    .build

  val updateUser: Module.Uninitialized = ModuleBuilder
    .metadata("UpdateUser", "Updates user information", 1, 0)
    .tags("database", "user", "update")
    .implementation[UpdateUserInput, UpdateUserOutput] { input =>
      sql"""
        UPDATE users
        SET name = ${input.name}, email = ${input.email}
        WHERE id = ${input.userId}
      """.update.run
        .transact(xa)
        .map(rows => UpdateUserOutput(rows > 0))
    }
    .build

  val deleteUser: Module.Uninitialized = ModuleBuilder
    .metadata("DeleteUser", "Deletes user by ID", 1, 0)
    .tags("database", "user", "delete")
    .implementation[DeleteUserInput, DeleteUserOutput] { input =>
      sql"""
        DELETE FROM users WHERE id = ${input.userId}
      """.update.run
        .transact(xa)
        .map(rows => DeleteUserOutput(rows > 0))
    }
    .build

  val all = List(createUser, getUser, updateUser, deleteUser)
}
```

### Validation Chain Pattern

```scala
object ValidationModules {
  case class ValidateInput(value: String)
  case class ValidateOutput(valid: Boolean, errors: List[String])

  val validateEmail: Module.Uninitialized = ModuleBuilder
    .metadata("ValidateEmail", "Email format validation", 1, 0)
    .tags("validation")
    .implementationPure[ValidateInput, ValidateOutput] { input =>
      val errors = scala.collection.mutable.ListBuffer[String]()

      if (!input.value.contains("@")) {
        errors += "Missing @ symbol"
      }
      if (!input.value.contains(".")) {
        errors += "Missing domain extension"
      }
      if (input.value.length < 5) {
        errors += "Email too short"
      }

      ValidateOutput(errors.isEmpty, errors.toList)
    }
    .build

  val validatePassword: Module.Uninitialized = ModuleBuilder
    .metadata("ValidatePassword", "Password strength validation", 1, 0)
    .tags("validation", "security")
    .implementationPure[ValidateInput, ValidateOutput] { input =>
      val errors = scala.collection.mutable.ListBuffer[String]()

      if (input.value.length < 8) {
        errors += "Password must be at least 8 characters"
      }
      if (!input.value.exists(_.isUpper)) {
        errors += "Password must contain uppercase letter"
      }
      if (!input.value.exists(_.isDigit)) {
        errors += "Password must contain digit"
      }

      ValidateOutput(errors.isEmpty, errors.toList)
    }
    .build

  val all = List(validateEmail, validatePassword)
}
```

---

## Best Practices

### 1. Naming Conventions

```scala
// GOOD - Clear, descriptive names
val uppercase: Module.Uninitialized = ModuleBuilder
  .metadata("Uppercase", "Converts text to uppercase", 1, 0)

case class TextInput(text: String)
case class TextOutput(result: String)

// BAD - Vague names
val mod1 = ModuleBuilder.metadata("M1", "Does stuff", 1, 0)
case class Input(x: String)
case class Output(y: String)
```

### 2. Field Name Matching

```scala
// CORRECT - Field names match constellation-lang
case class ReplaceInput(text: String, find: String, replace: String)

// In .cst file:
// result = Replace(text, find, replace)  // Works!

// WRONG - Mismatched field names
case class ReplaceInput(str: String, pattern: String, replacement: String)

// In .cst file:
// result = Replace(text, find, replace)  // ERROR! Field names don't match
```

### 3. Comprehensive Descriptions

```scala
// GOOD - Clear, detailed description
val uppercase: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "Uppercase",
    description = "Converts all characters in the input text to uppercase. " +
                  "Useful for normalizing text before comparison or formatting headers.",
    majorVersion = 1,
    minorVersion = 0
  )

// BAD - Vague description
val uppercase = ModuleBuilder
  .metadata("Uppercase", "Makes uppercase", 1, 0)
```

### 4. Appropriate Tags

```scala
// GOOD - Relevant, hierarchical tags
.tags("text", "transform", "case-conversion")

// GOOD - Domain-specific tags
.tags("database", "user", "crud", "read")

// BAD - Too generic or irrelevant
.tags("module", "function")
```

### 5. Error Handling

```scala
// GOOD - Explicit error handling
val safeDivide = ModuleBuilder
  .metadata("SafeDivide", "Divides with zero check", 1, 0)
  .implementation[DivideInput, DivideOutput] { input =>
    if (input.denominator == 0) {
      IO.raiseError(new ArithmeticException("Division by zero"))
    } else {
      IO.pure(DivideOutput(input.numerator.toDouble / input.denominator))
    }
  }
  .build

// BAD - Unchecked errors
val unsafeDivide = ModuleBuilder
  .metadata("Divide", "Divides numbers", 1, 0)
  .implementationPure[DivideInput, DivideOutput] { input =>
    DivideOutput(input.numerator.toDouble / input.denominator)  // Will crash on zero!
  }
  .build
```

### 6. Edge Case Handling

```scala
// GOOD - Handles empty lists
val average = ModuleBuilder
  .metadata("Average", "Calculates mean", 1, 0)
  .implementationPure[ListInput, AverageOutput] { input =>
    if (input.numbers.isEmpty) {
      AverageOutput(0.0)
    } else {
      AverageOutput(input.numbers.sum.toDouble / input.numbers.length)
    }
  }
  .build

// BAD - Crashes on empty list
val badAverage = ModuleBuilder
  .metadata("Average", "Calculates mean", 1, 0)
  .implementationPure[ListInput, AverageOutput] { input =>
    AverageOutput(input.numbers.sum.toDouble / input.numbers.length)  // Division by zero!
  }
  .build
```

### 7. Timeout Configuration

```scala
// GOOD - Appropriate timeouts for slow operations
val slowQuery = ModuleBuilder
  .metadata("QueryDatabase", "Queries large dataset", 1, 0)
  .moduleTimeout(30.seconds)  // Give it time to complete
  .implementation[QueryInput, QueryOutput] { input =>
    // ... slow database query
  }
  .build

// GOOD - Quick timeout for fast operations
val quickCheck = ModuleBuilder
  .metadata("HealthCheck", "Pings service", 1, 0)
  .moduleTimeout(2.seconds)  // Should be fast
  .implementation[HealthInput, HealthOutput] { input =>
    // ... quick health check
  }
  .build
```

### 8. Immutability

```scala
// GOOD - Immutable data structures
case class ProcessInput(items: List[String])
case class ProcessOutput(results: List[String])

val process = ModuleBuilder
  .metadata("Process", "Processes items", 1, 0)
  .implementationPure[ProcessInput, ProcessOutput] { input =>
    val results = input.items.map(_.toUpperCase)  // Creates new list
    ProcessOutput(results)
  }
  .build

// BAD - Mutable state
case class ProcessInput(items: List[String])
case class ProcessOutput(results: List[String])

val mutableBuffer = scala.collection.mutable.ListBuffer[String]()  // BAD!

val badProcess = ModuleBuilder
  .metadata("Process", "Processes items", 1, 0)
  .implementationPure[ProcessInput, ProcessOutput] { input =>
    mutableBuffer.clear()
    input.items.foreach(item => mutableBuffer += item.toUpperCase)
    ProcessOutput(mutableBuffer.toList)
  }
  .build
```

### 9. Resource Management

```scala
// GOOD - Proper resource management with Resource
import cats.effect.Resource

def createDatabaseModule(
  dbConfig: DatabaseConfig
): Resource[IO, Module.Uninitialized] = {
  Resource.make {
    IO(createConnectionPool(dbConfig))
  } { pool =>
    IO(pool.close())
  }.map { pool =>
    ModuleBuilder
      .metadata("QueryDB", "Queries database", 1, 0)
      .implementation[QueryInput, QueryOutput] { input =>
        // Use pool safely
        ???
      }
      .build
  }
}

// BAD - Manual resource management (leak risk)
def badCreateModule(dbConfig: DatabaseConfig): Module.Uninitialized = {
  val pool = createConnectionPool(dbConfig)  // Never closed!
  ModuleBuilder
    .metadata("QueryDB", "Queries database", 1, 0)
    .implementation[QueryInput, QueryOutput] { input =>
      // Use pool
      ???
    }
    .build
}
```

### 10. Semantic Versioning

```scala
// Major version 1, minor version 0 - Initial release
val v1_0 = ModuleBuilder
  .metadata("Process", "Processes data", 1, 0)

// Major version 1, minor version 1 - Backward-compatible addition
val v1_1 = ModuleBuilder
  .metadata("Process", "Processes data with options", 1, 1)
  // Added optional parameter, still works with old code

// Major version 2, minor version 0 - Breaking change
val v2_0 = ModuleBuilder
  .metadata("Process", "Processes data (new format)", 2, 0)
  // Changed input format, requires code updates
```

---

## Anti-Patterns to Avoid

### 1. Don't Use Tuples for Input/Output

```scala
// WRONG - Scala 3 doesn't support single-element tuples
val badModule = ModuleBuilder
  .metadata("Bad", "Don't do this", 1, 0)
  .implementationPure[(String,), (String,)] { input =>
    // COMPILE ERROR!
    ???
  }
  .build

// CORRECT - Use case classes
case class Input(text: String)
case class Output(result: String)

val goodModule = ModuleBuilder
  .metadata("Good", "Do this", 1, 0)
  .implementationPure[Input, Output] { input =>
    Output(input.text.toUpperCase)
  }
  .build
```

### 2. Don't Mismatch Module Names

```scala
// WRONG - Name doesn't match usage
val badName = ModuleBuilder
  .metadata("ToUpperCase", "Converts to uppercase", 1, 0)  // Name: ToUpperCase
  .implementationPure[Input, Output] { ??? }
  .build

// In constellation-lang:
// result = Uppercase(text)  // ERROR! Looking for "Uppercase", not "ToUpperCase"

// CORRECT - Exact match
val goodName = ModuleBuilder
  .metadata("Uppercase", "Converts to uppercase", 1, 0)  // Name: Uppercase
  .implementationPure[Input, Output] { ??? }
  .build

// In constellation-lang:
// result = Uppercase(text)  // Works!
```

### 3. Don't Forget cats.implicits

```scala
import cats.effect.IO
import io.constellation.*
// Missing: import cats.implicits._

val modules = List(module1, module2, module3)

// WRONG - Won't compile without cats.implicits._
modules.traverse(constellation.setModule)  // ERROR: value traverse is not a member

// CORRECT
import cats.implicits._  // Add this!

modules.traverse(constellation.setModule)  // Works!
```

### 4. Don't Use Blocking Operations in Pure Modules

```scala
// WRONG - Blocking I/O in pure implementation
val badModule = ModuleBuilder
  .metadata("ReadFile", "Reads file", 1, 0)
  .implementationPure[FileInput, FileOutput] { input =>
    val content = scala.io.Source.fromFile(input.path).mkString  // BLOCKING!
    FileOutput(content)
  }
  .build

// CORRECT - Use IO for side effects
val goodModule = ModuleBuilder
  .metadata("ReadFile", "Reads file", 1, 0)
  .implementation[FileInput, FileOutput] { input =>
    IO {
      val content = scala.io.Source.fromFile(input.path).mkString
      FileOutput(content)
    }
  }
  .build
```

### 5. Don't Ignore Errors Silently

```scala
// WRONG - Silently returns invalid result
val badParse = ModuleBuilder
  .metadata("ParseInt", "Parses integer", 1, 0)
  .implementationPure[ParseInput, ParseOutput] { input =>
    try {
      ParseOutput(input.text.toLong)
    } catch {
      case _: Exception => ParseOutput(0L)  // Silent failure!
    }
  }
  .build

// CORRECT - Return error information
case class ParseOutput(value: Long, success: Boolean, error: String)

val goodParse = ModuleBuilder
  .metadata("ParseInt", "Parses integer", 1, 0)
  .implementationPure[ParseInput, ParseOutput] { input =>
    try {
      ParseOutput(input.text.toLong, success = true, error = "")
    } catch {
      case e: NumberFormatException =>
        ParseOutput(0L, success = false, error = e.getMessage)
    }
  }
  .build
```

### 6. Don't Use Mutable State Across Invocations

```scala
import scala.collection.mutable

// WRONG - Shared mutable state
val counter = new java.util.concurrent.atomic.AtomicInteger(0)

val badModule = ModuleBuilder
  .metadata("Counter", "Counts invocations", 1, 0)
  .implementationPure[CountInput, CountOutput] { input =>
    val count = counter.incrementAndGet()  // WRONG! Side effect in pure function
    CountOutput(count)
  }
  .build

// CORRECT - Stateless module
val goodModule = ModuleBuilder
  .metadata("Process", "Processes input", 1, 0)
  .implementationPure[ProcessInput, ProcessOutput] { input =>
    // Pure transformation, no shared state
    ProcessOutput(input.value.toUpperCase)
  }
  .build
```

### 7. Don't Create Massive Modules

```scala
// WRONG - One module does too much
val godModule = ModuleBuilder
  .metadata("ProcessEverything", "Does everything", 1, 0)
  .implementation[GodInput, GodOutput] { input =>
    for {
      validated <- validate(input)
      fetched <- fetchFromDb(validated)
      transformed <- transform(fetched)
      enriched <- callExternalApi(transformed)
      formatted <- format(enriched)
      saved <- saveToDb(formatted)
      notified <- sendNotification(saved)
    } yield GodOutput(notified)
  }
  .build

// CORRECT - Separate concerns into modules
val validate = ModuleBuilder.metadata("Validate", "Validates input", 1, 0).???
val fetch = ModuleBuilder.metadata("Fetch", "Fetches from DB", 1, 0).???
val transform = ModuleBuilder.metadata("Transform", "Transforms data", 1, 0).???
val enrich = ModuleBuilder.metadata("Enrich", "Enriches with API", 1, 0).???
val format = ModuleBuilder.metadata("Format", "Formats output", 1, 0).???
val save = ModuleBuilder.metadata("Save", "Saves to DB", 1, 0).???
val notify = ModuleBuilder.metadata("Notify", "Sends notification", 1, 0).???

// In constellation-lang, compose them:
// validated = Validate(input)
// fetched = Fetch(validated)
// transformed = Transform(fetched)
// ... etc
```

### 8. Don't Use println for Logging

```scala
// WRONG - println in production code
val badModule = ModuleBuilder
  .metadata("Process", "Processes data", 1, 0)
  .implementation[Input, Output] { input =>
    IO {
      println(s"Processing: ${input.value}")  // WRONG!
      Output(input.value.toUpperCase)
    }
  }
  .build

// CORRECT - Use proper logging
import org.typelevel.log4cats.Logger

def createModule(logger: Logger[IO]): Module.Uninitialized =
  ModuleBuilder
    .metadata("Process", "Processes data", 1, 0)
    .implementation[Input, Output] { input =>
      for {
        _ <- logger.info(s"Processing: ${input.value}")
        result = Output(input.value.toUpperCase)
      } yield result
    }
    .build
```

---

## Advanced Patterns

### Using ModuleBuilder Transformations

```scala
// Transform output type
val baseModule = ModuleBuilder
  .metadata("Process", "Processes text", 1, 0)
  .implementationPure[Input, Output] { input =>
    Output(input.value.toUpperCase)
  }

// Add length calculation by mapping output
case class EnrichedOutput(result: String, length: Long)

val enrichedModule = baseModule.map { output =>
  EnrichedOutput(output.result, output.result.length.toLong)
}.build
```

### Contramap for Input Transformation

```scala
case class RawInput(data: String)
case class ProcessedInput(cleaned: String)
case class Output(result: String)

val baseModule = ModuleBuilder
  .metadata("Process", "Processes cleaned data", 1, 0)
  .implementationPure[ProcessedInput, Output] { input =>
    Output(input.cleaned.toUpperCase)
  }

// Preprocess input before passing to module
val withPreprocessing = baseModule.contraMap[RawInput] { raw =>
  ProcessedInput(raw.data.trim.toLowerCase)
}.build
```

### BiMap for Both Input and Output

```scala
case class RawInput(value: String)
case class CleanInput(value: String)
case class RawOutput(value: String)
case class FormattedOutput(value: String, timestamp: Long)

val baseModule = ModuleBuilder
  .metadata("Process", "Core processing", 1, 0)
  .implementationPure[CleanInput, RawOutput] { input =>
    RawOutput(input.value.toUpperCase)
  }

val wrappedModule = baseModule.biMap[RawInput, FormattedOutput](
  // Transform input: RawInput => CleanInput
  raw => CleanInput(raw.value.trim),
  // Transform output: RawOutput => FormattedOutput
  raw => FormattedOutput(raw.value, System.currentTimeMillis())
).build
```

### Module with Execution Context

```scala
import cats.Eval

case class ContextInput(value: String)
case class ContextOutput(result: String)

val withContext = ModuleBuilder
  .metadata("ProcessWithContext", "Returns execution metadata", 1, 0)
  .implementationWithContext[ContextInput, ContextOutput] { input =>
    IO {
      val result = ContextOutput(input.value.toUpperCase)
      val context = Map(
        "executionTime" -> io.circe.Json.fromLong(System.currentTimeMillis()),
        "inputLength" -> io.circe.Json.fromInt(input.value.length)
      )
      Module.Produces(result, Eval.later(context))
    }
  }
  .build
```

### Parameterized Module Factory

```scala
class ConfigurableModules(config: AppConfig) {

  def createApiModule(endpoint: String): Module.Uninitialized = {
    case class Input(params: String)
    case class Output(data: String)

    ModuleBuilder
      .metadata(s"Api_${endpoint}", s"Calls $endpoint endpoint", 1, 0)
      .tags("api", endpoint)
      .implementation[Input, Output] { input =>
        // Use config.apiKey, config.baseUrl, etc.
        ???
      }
      .build
  }

  def createDbModule(table: String): Module.Uninitialized = {
    case class Input(id: Long)
    case class Output(data: String)

    ModuleBuilder
      .metadata(s"Query_${table}", s"Queries $table table", 1, 0)
      .tags("database", table)
      .implementation[Input, Output] { input =>
        // Use config.dbConnection, etc.
        ???
      }
      .build
  }

  val allModules: List[Module.Uninitialized] = {
    List(
      createApiModule("users"),
      createApiModule("orders"),
      createDbModule("products"),
      createDbModule("inventory")
    )
  }
}
```

### Dynamic Module Registration

```scala
import io.circe.parser.*

case class ModuleSpec(name: String, operation: String, params: Map[String, String])

def createDynamicModule(spec: ModuleSpec): Module.Uninitialized = {
  case class DynInput(value: String)
  case class DynOutput(result: String)

  spec.operation match {
    case "uppercase" =>
      ModuleBuilder
        .metadata(spec.name, s"Dynamic ${spec.operation}", 1, 0)
        .implementationPure[DynInput, DynOutput] { input =>
          DynOutput(input.value.toUpperCase)
        }
        .build

    case "lowercase" =>
      ModuleBuilder
        .metadata(spec.name, s"Dynamic ${spec.operation}", 1, 0)
        .implementationPure[DynInput, DynOutput] { input =>
          DynOutput(input.value.toLowerCase)
        }
        .build

    case _ =>
      throw new IllegalArgumentException(s"Unknown operation: ${spec.operation}")
  }
}

def registerDynamicModules(
  constellation: Constellation,
  specsJson: String
): IO[Unit] = {
  for {
    json <- IO.fromEither(parse(specsJson))
    specs <- IO.fromEither(json.as[List[ModuleSpec]])
    modules = specs.map(createDynamicModule)
    _ <- modules.traverse(constellation.setModule)
  } yield ()
}
```

### Composite Module Pattern

```scala
// Create a "composite" module that delegates to other modules internally
class CompositeModules(httpClient: Client[IO], db: Transactor[IO]) {

  case class ComplexInput(userId: Long, action: String)
  case class ComplexOutput(result: String, metadata: Map[String, String])

  val complexWorkflow: Module.Uninitialized = ModuleBuilder
    .metadata("ComplexWorkflow", "Multi-step workflow", 1, 0)
    .tags("workflow", "composite")
    .implementation[ComplexInput, ComplexOutput] { input =>
      for {
        // Step 1: Fetch user from DB
        user <- sql"SELECT name FROM users WHERE id = ${input.userId}"
          .query[String]
          .unique
          .transact(db)

        // Step 2: Call external API
        apiResult <- httpClient.expect[String](s"https://api.example.com/${input.action}")

        // Step 3: Combine results
        combined = s"$user: $apiResult"

        metadata = Map(
          "userId" -> input.userId.toString,
          "action" -> input.action,
          "timestamp" -> System.currentTimeMillis().toString
        )
      } yield ComplexOutput(combined, metadata)
    }
    .build
}
```

---

## Summary

This guide covered:

1. **Quick Start**: Minimal working example to get started immediately
2. **ModuleBuilder API**: Complete API surface with all configuration options
3. **Pure Modules**: Side-effect-free transformations for deterministic operations
4. **IO Modules**: Effectful operations including HTTP, database, and file I/O
5. **Async Modules**: Concurrent operations with timeouts, retries, and parallelism
6. **Error Handling**: Graceful error handling, validation, and recovery patterns
7. **Custom Types**: Working with lists, complex structures, and type conversions
8. **Input/Output**: All combinations of single/multiple inputs/outputs
9. **Registration**: Organizing and registering modules with Constellation
10. **Testing**: Unit tests, integration tests, property tests, and edge cases
11. **Common Patterns**: Real-world patterns for text, data, API, database, and validation
12. **Best Practices**: Naming, error handling, resource management, versioning
13. **Anti-Patterns**: Common mistakes to avoid
14. **Advanced Patterns**: Transformations, factories, dynamic registration, composites

**Key Takeaways:**

- ALWAYS use case classes for input/output (never tuples)
- Module names MUST match constellation-lang usage exactly
- Field names MUST match constellation-lang variable names
- Use `implementationPure` for pure functions, `implementation` for I/O
- Import `cats.implicits._` when using `.traverse`
- Handle edge cases explicitly
- Use proper error handling, not silent failures
- Keep modules focused and composable

**Next Steps:**

1. Start with a simple pure module (text transformation)
2. Add I/O modules for your domain (API calls, database queries)
3. Test thoroughly with edge cases
4. Organize modules by category
5. Register with Constellation and expose via compiler
6. Write constellation-lang scripts that compose your modules

For more information:
- See `modules/example-app/src/main/scala/io/constellation/examples/app/modules/` for complete working examples
- See `modules/runtime/src/main/scala/io/constellation/ModuleBuilder.scala` for API documentation
- See `website/docs/llm/foundations/type-system.md` for type system details
- See `website/docs/llm/integration/registering-modules.md` for registration patterns
