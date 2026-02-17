# RFC-026: Pipeline Test Kit

**Status:** Draft
**Priority:** P2 (Developer Experience)
**Author:** Human + Claude
**Created:** 2026-02-10
**Revised:** 2026-02-17

---

## Summary

A lightweight Scala testing library (`io.constellation.testing`) that reduces boilerplate when writing pipeline tests. Three components:

1. **`CValueBuilders`** — Concise construction helpers for `CValue` instances
2. **`MockModules`** — Factory methods for creating mock `Module.Uninitialized` instances
3. **`PipelineTestKit`** — A ScalaTest mixin that wires compilation, module registration, execution, and assertions into a fluent builder

No parser changes, no new CLI commands, no new language syntax. Just a test utility library that makes the existing Scala testing story less verbose.

---

## Motivation

Testing a Constellation pipeline today works — but is verbose:

```scala
// Current: ~20 lines of boilerplate for a simple pipeline test
val registry = FunctionRegistry.empty
registry.register(FunctionSignature("Uppercase", List("text" -> SemanticType.SString), SemanticType.SString, "uppercase"))

val mockModule = ModuleBuilder
  .metadata("uppercase", "mock", 1, 0)
  .implementationPure[UppercaseInput, UppercaseOutput] { in => UppercaseOutput(in.text.toUpperCase) }
  .build

val compiler = LangCompiler(registry, Map("uppercase" -> mockModule))
val result = compiler.compile(source, "test-dag")
val output = result.toOption.get

val constellation = Constellation.create().unsafeRunSync()
constellation.setModule(mockModule).unsafeRunSync()
val loaded = constellation.loadPipeline(output.pipeline).unsafeRunSync()
val execResult = constellation.run(loaded.image, Map("text" -> CValue.CString("hello"))).unsafeRunSync()
// ... extract and assert on CValue outputs
```

The three pain points:
1. **CValue construction** — `CValue.CProduct(Map("name" -> CValue.CString("Alice"), "age" -> CValue.CInt(42)))` is unreadable
2. **Mock modules** — Every mock needs a case class, `ModuleBuilder` call, and `FunctionSignature` registration
3. **Pipeline wiring** — Compiler setup, module registration, constellation creation, pipeline loading, execution, output extraction — all repeated per test

---

## Design

### `CValueBuilders`

```scala
package io.constellation.testing

import io.constellation.TypeSystem._

object CValueBuilders {
  def cstring(s: String): CValue = CValue.CString(s)
  def cint(n: Long): CValue = CValue.CInt(n)
  def cfloat(d: Double): CValue = CValue.CFloat(d)
  def cbool(b: Boolean): CValue = CValue.CBoolean(b)

  def clist(items: CValue*): CValue =
    CValue.CList(items.toVector)

  def record(fields: (String, CValue)*): CValue =
    CValue.CProduct(fields.toMap)

  /** Auto-convert Scala primitives to CValue */
  implicit def stringToCValue(s: String): CValue = CValue.CString(s)
  implicit def intToCValue(n: Int): CValue = CValue.CInt(n.toLong)
  implicit def longToCValue(n: Long): CValue = CValue.CInt(n)
  implicit def doubleToCValue(d: Double): CValue = CValue.CFloat(d)
  implicit def boolToCValue(b: Boolean): CValue = CValue.CBoolean(b)
}
```

**Before:**
```scala
CValue.CProduct(Map("name" -> CValue.CString("Alice"), "age" -> CValue.CInt(42)))
```

**After:**
```scala
record("name" -> cstring("Alice"), "age" -> cint(42))
// or with implicits:
record("name" -> "Alice", "age" -> 42)
```

### `MockModules`

```scala
package io.constellation.testing

object MockModules {

  /** Create a mock module that always returns the same CValue output. */
  def staticModule(
      name: String,
      consumes: Map[String, CType],
      produces: Map[String, CType],
      output: CValue
  ): Module.Uninitialized

  /** Create a mock module that passes its first input field through unchanged. */
  def identityModule(
      name: String,
      fieldName: String,
      fieldType: CType
  ): Module.Uninitialized

  /** Create a mock module that always fails with the given error. */
  def failingModule(
      name: String,
      consumes: Map[String, CType],
      produces: Map[String, CType],
      error: String
  ): Module.Uninitialized
}
```

**Before:**
```scala
case class MockInput(text: String)
case class MockOutput(result: String)

val mock = ModuleBuilder
  .metadata("my-module", "mock", 1, 0)
  .implementationPure[MockInput, MockOutput] { _ => MockOutput("mocked") }
  .build
```

**After:**
```scala
val mock = MockModules.staticModule(
  name = "my-module",
  consumes = Map("text" -> CType.CString),
  produces = Map("result" -> CType.CString),
  output = record("result" -> "mocked")
)
```

No case class needed — mocks operate on CValue directly.

### `PipelineTestKit`

A ScalaTest mixin with a fluent builder:

```scala
package io.constellation.testing

trait PipelineTestKit { self: AnyFlatSpec with Matchers =>

  /** Compile a pipeline from source string. */
  def testPipeline(source: String): PipelineTestBuilder

  /** Compile a pipeline from a .cst file path. */
  def testPipelineFromFile(path: String): PipelineTestBuilder
}

class PipelineTestBuilder {
  /** Register a function signature for type checking. */
  def withSignature(sig: FunctionSignature): PipelineTestBuilder

  /** Register a real module. */
  def withModule(languageName: String, module: Module.Uninitialized, ...): PipelineTestBuilder

  /** Register a mock module (replaces any real module with same name). */
  def withMock(languageName: String, mock: Module.Uninitialized, ...): PipelineTestBuilder

  /** Provide a pipeline input. */
  def input(name: String, value: CValue): PipelineTestBuilder

  /** Execute and return result for assertions. */
  def run(): PipelineTestResult
}

class PipelineTestResult {
  /** Assert pipeline completed successfully. */
  def assertCompleted(): PipelineTestResult

  /** Assert pipeline failed. */
  def assertFailed(): PipelineTestResult

  /** Get a specific output value. */
  def output(name: String): CValue

  /** Assert on a specific output. */
  def assertOutput(name: String)(f: CValue => Unit): PipelineTestResult
}
```

**Full test example:**

```scala
class ScoringPipelineTest extends AnyFlatSpec with Matchers with PipelineTestKit {
  import CValueBuilders._

  "scoring pipeline" should "score premium users highly" in {
    testPipeline("""
        in user: {name: String, tier: String}
        score = ScoreUser(user)
        out score
      """)
      .withMock("ScoreUser", staticModule(
        "score-user", Map("user" -> userType), Map("score" -> CType.CFloat),
        record("score" -> 0.95)
      ), params, returns)
      .input("user", record("name" -> "Alice", "tier" -> "premium"))
      .run()
      .assertCompleted()
      .assertOutput("score") { v =>
        v shouldBe a[CValue.CProduct]
      }
  }
}
```

---

## Scope

### In scope

- `CValueBuilders` object with factory methods and implicit conversions
- `MockModules` object with `staticModule`, `identityModule`, `failingModule`
- `PipelineTestKit` trait with `testPipeline` / `testPipelineFromFile` builders
- `PipelineTestResult` with output extraction and basic assertions
- Unit tests for all three components
- Lives in a new `testing` module (test-scoped dependency, not shipped in production JARs)

### Out of scope (future work)

- Native `.cst` test syntax (parser extensions, `test`/`assert`/`mock` blocks)
- Observation plugin (production traffic capture)
- Replay engine (production traffic replay with strategic mocking)
- External recording backends (BigQuery, BigTable)
- Snapshot testing
- `constellation test` CLI command

These features may be revisited post-v1.0 when user personas and production usage patterns are clearer.

---

## Implementation

Single phase, estimated small:

- [ ] Create `testing` sbt module (depends on `runtime` + `lang-compiler`, test-scope only)
- [ ] `CValueBuilders` — factory methods + implicit conversions
- [ ] `MockModules` — `staticModule`, `identityModule`, `failingModule`
- [ ] `PipelineTestKit` — ScalaTest mixin with fluent builder
- [ ] `PipelineTestResult` — output extraction + assertions
- [ ] Tests for all components
- [ ] Example usage in `example-app` tests (dogfood the kit)

---

## References

- **Existing testing patterns:** `modules/lang-compiler/src/test/scala/io/constellation/lang/LangCompilerTest.scala`
- **Module builder API:** `modules/runtime/src/main/scala/io/constellation/ModuleBuilder.scala`
- **CValue/CType definitions:** `modules/core/src/main/scala/io/constellation/TypeSystem.scala`
