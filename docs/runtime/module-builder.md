# Module Builder

> **Path**: `docs/runtime/module-builder.md`
> **Parent**: [runtime/](./README.md)

Fluent Scala API for creating Constellation modules.

## Overview

`ModuleBuilder` provides a type-safe, declarative way to create processing modules with:

- **Metadata**: name, description, version, tags
- **Type signatures**: derived from input/output case classes
- **Implementation**: pure function or IO-based

## Basic Usage

```scala
import io.constellation.ModuleBuilder
import cats.effect.IO

// 1. Define input/output case classes
case class TextInput(text: String)
case class TextOutput(result: String)

// 2. Build the module
val uppercase = ModuleBuilder
  .metadata("Uppercase", "Converts text to uppercase", 1, 0)
  .tags("text", "transform")
  .implementationPure[TextInput, TextOutput] { input =>
    TextOutput(input.text.toUpperCase)
  }
  .build
```

## Builder Flow

```
ModuleBuilder.metadata(...)
         │
         ▼
   ModuleBuilderInit        <- Configure metadata, timeouts
         │ .implementation[I, O](...)
         ▼
   ModuleBuilder[I, O]      <- Typed with input/output
         │ .build
         ▼
   Module.Uninitialized     <- Ready for registration
```

## Implementation Types

### Pure Implementation

For side-effect-free transformations:

```scala
.implementationPure[Input, Output] { input =>
  Output(input.data.toUpperCase)
}
```

### IO Implementation

For effectful operations (HTTP, database, file I/O):

```scala
.implementation[Input, Output] { input =>
  IO {
    val response = httpClient.get(input.url)
    Output(response.body)
  }
}
```

### With Context

Return additional metadata alongside the result:

```scala
.implementationWithContext[Input, Output] { input =>
  IO {
    Module.Produces(
      data = Output(result),
      implementationContext = Eval.later(Map(
        "timing" -> Json.fromLong(elapsed)
      ))
    )
  }
}
```

## Configuration Options

| Method | Purpose |
|--------|---------|
| `.metadata(name, desc, major, minor)` | Set module identity |
| `.tags("tag1", "tag2")` | Add classification tags |
| `.inputsTimeout(duration)` | Time to wait for inputs |
| `.moduleTimeout(duration)` | Time for module execution |
| `.definitionContext(map)` | Attach static metadata |

```scala
import scala.concurrent.duration._

val module = ModuleBuilder
  .metadata("SlowModule", "Long-running operation", 1, 0)
  .inputsTimeout(10.seconds)
  .moduleTimeout(60.seconds)
  .implementation[Input, Output](longRunningProcess)
  .build
```

## Field Naming Rules

Case class field names map directly to variable names in constellation-lang:

```scala
case class MyInput(text: String, count: Int)

// In constellation-lang:
result = MyModule(text, count)  // Field names must match exactly
```

**Critical**: Field names are case-sensitive; order matters for positional arguments.

## Type Mapping

| Scala Type | CType |
|------------|-------|
| `String` | `CType.CString` |
| `Long` | `CType.CInt` |
| `Double` | `CType.CFloat` |
| `Boolean` | `CType.CBoolean` |
| `List[A]` | `CType.CList(A)` |
| `Option[A]` | `CType.COptional(A)` |
| Case class | `CType.CProduct(fields)` |

See [type-system.md](./type-system.md) for details.

## Functional Transformations

```scala
// Transform output
builder.map[NewOutput](o => NewOutput(o.result.length))

// Transform input
builder.contraMap[NewInput](i => Input(i.rawText))

// Transform both
builder.biMap[NewIn, NewOut](inFn, outFn)
```

## Module States

| State | Description |
|-------|-------------|
| `Module.Uninitialized` | Template from builder, not yet registered |
| `Module.Initialized` | Has runtime context, ready for execution |

### Registration

```scala
for {
  constellation <- ConstellationImpl.init
  _ <- constellation.setModule(myModule)
} yield ()
```

## Complete Example

```scala
import cats.effect.{IO, IOApp}
import io.constellation._
import io.constellation.impl.ConstellationImpl

object TextPipeline extends IOApp.Simple {
  case class AnalyzeInput(text: String)
  case class AnalyzeOutput(wordCount: Long, charCount: Long)

  val analyzeModule = ModuleBuilder
    .metadata("Analyze", "Text analysis", 1, 0)
    .implementationPure[AnalyzeInput, AnalyzeOutput] { input =>
      AnalyzeOutput(
        wordCount = input.text.split("\\s+").length,
        charCount = input.text.length
      )
    }
    .build

  def run: IO[Unit] = for {
    constellation <- ConstellationImpl.init
    _ <- constellation.setModule(analyzeModule)
  } yield ()
}
```

## Related Files

| File | Purpose |
|------|---------|
| `modules/runtime/.../ModuleBuilder.scala` | Implementation |
| [type-system.md](./type-system.md) | CType and CValue details |
| `modules/example-app/.../modules/` | Example modules |
