package io.constellation.lang.integration

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.{CValue, CType}
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** End-to-end integration tests for the full pipeline: .cst source → compile → execute → verify outputs.
  *
  * Tests cover passthrough, type variants, boolean logic, conditionals, records, guards, coalesce,
  * multiple outputs, and error cases.
  *
  * Run with: sbt "langCompiler/testOnly *EndToEndPipelineTest"
  */
class EndToEndPipelineTest extends AnyFlatSpec with Matchers {

  private val constellation = ConstellationImpl.init.unsafeRunSync()
  private val compiler      = LangCompiler.empty

  /** Compile and execute a pipeline, returning the outputs map. */
  private def compileAndRun(
      source: String,
      inputs: Map[String, CValue]
  ): IO[Map[String, CValue]] =
    for {
      compiled <- IO.fromEither(
        compiler
          .compile(source, "e2e-test")
          .left
          .map(errors => new RuntimeException(s"Compilation failed: ${errors.map(_.message).mkString(", ")}"))
      )
      dataSig <- constellation.run(compiled.pipeline, inputs)
    } yield dataSig.outputs

  // =========================================================================
  // 1. String passthrough
  // =========================================================================

  "E2E pipeline" should "pass through a String input" in {
    val source  = "in x: String\nout x"
    val outputs = compileAndRun(source, Map("x" -> CValue.CString("hello"))).unsafeRunSync()
    outputs("x") shouldBe CValue.CString("hello")
  }

  // =========================================================================
  // 2. Int passthrough
  // =========================================================================

  it should "pass through an Int input" in {
    val source  = "in x: Int\nout x"
    val outputs = compileAndRun(source, Map("x" -> CValue.CInt(42))).unsafeRunSync()
    outputs("x") shouldBe CValue.CInt(42)
  }

  // =========================================================================
  // 3. Boolean logic chain
  // =========================================================================

  it should "evaluate boolean logic chains (and, or, not)" in {
    val source =
      """in a: Boolean
        |in b: Boolean
        |c = a and b
        |d = a or b
        |e = not a
        |out c
        |out d
        |out e
        |""".stripMargin

    val outputs = compileAndRun(
      source,
      Map("a" -> CValue.CBoolean(true), "b" -> CValue.CBoolean(false))
    ).unsafeRunSync()

    outputs("c") shouldBe CValue.CBoolean(false) // true and false
    outputs("d") shouldBe CValue.CBoolean(true)  // true or false
    outputs("e") shouldBe CValue.CBoolean(false) // not true
  }

  // =========================================================================
  // 4. Conditional expression
  // =========================================================================

  it should "evaluate conditional expressions" in {
    val source =
      """in flag: Boolean
        |in a: Int
        |in b: Int
        |result = if (flag) a else b
        |out result
        |""".stripMargin

    // flag = true, should pick a
    val outputsTrue = compileAndRun(
      source,
      Map(
        "flag" -> CValue.CBoolean(true),
        "a"    -> CValue.CInt(10),
        "b"    -> CValue.CInt(20)
      )
    ).unsafeRunSync()
    outputsTrue("result") shouldBe CValue.CInt(10)

    // flag = false, should pick b
    val outputsFalse = compileAndRun(
      source,
      Map(
        "flag" -> CValue.CBoolean(false),
        "a"    -> CValue.CInt(10),
        "b"    -> CValue.CInt(20)
      )
    ).unsafeRunSync()
    outputsFalse("result") shouldBe CValue.CInt(20)
  }

  // =========================================================================
  // 5. Record passthrough with product type
  // =========================================================================

  it should "pass through a record (product type) input" in {
    val source  = "in user: { name: String, age: Int }\nout user"
    val record  = CValue.CProduct(
      Map("name" -> CValue.CString("Alice"), "age" -> CValue.CInt(30)),
      Map("name" -> CType.CString, "age" -> CType.CInt)
    )
    val outputs = compileAndRun(source, Map("user" -> record)).unsafeRunSync()
    outputs("user") shouldBe record
  }

  // =========================================================================
  // 6. Record merge (a + b)
  // =========================================================================

  it should "merge two records with the + operator" in {
    val source =
      """in a: { x: Int }
        |in b: { y: String }
        |merged = a + b
        |out merged
        |""".stripMargin

    val recA = CValue.CProduct(
      Map("x" -> CValue.CInt(1)),
      Map("x" -> CType.CInt)
    )
    val recB = CValue.CProduct(
      Map("y" -> CValue.CString("hello")),
      Map("y" -> CType.CString)
    )
    val outputs = compileAndRun(source, Map("a" -> recA, "b" -> recB)).unsafeRunSync()

    val merged = outputs("merged").asInstanceOf[CValue.CProduct]
    merged.value("x") shouldBe CValue.CInt(1)
    merged.value("y") shouldBe CValue.CString("hello")
  }

  // =========================================================================
  // 7. Field projection (data[field1, field2])
  // =========================================================================

  it should "project fields from a record" in {
    val source =
      """in data: { name: String, age: Int, email: String }
        |projected = data[name, age]
        |out projected
        |""".stripMargin

    val record = CValue.CProduct(
      Map(
        "name"  -> CValue.CString("Bob"),
        "age"   -> CValue.CInt(25),
        "email" -> CValue.CString("bob@example.com")
      ),
      Map(
        "name"  -> CType.CString,
        "age"   -> CType.CInt,
        "email" -> CType.CString
      )
    )
    val outputs = compileAndRun(source, Map("data" -> record)).unsafeRunSync()

    val projected = outputs("projected").asInstanceOf[CValue.CProduct]
    projected.value should contain key "name"
    projected.value should contain key "age"
    projected.value should not contain key("email")
  }

  // =========================================================================
  // 8. List passthrough
  // =========================================================================

  it should "pass through a List input" in {
    val source = "in items: List<Int>\nout items"
    val list   = CValue.CList(
      Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)),
      CType.CInt
    )
    val outputs = compileAndRun(source, Map("items" -> list)).unsafeRunSync()
    outputs("items") shouldBe list
  }

  // =========================================================================
  // 9. Guard + coalesce (x when flag, ?? fallback)
  // =========================================================================

  it should "evaluate guard and coalesce expressions" in {
    val source =
      """in flag: Boolean
        |in value: Int
        |in fallback: Int
        |guarded = value when flag
        |result = guarded ?? fallback
        |out result
        |""".stripMargin

    // flag = true → value passes through
    val outputsTrue = compileAndRun(
      source,
      Map(
        "flag"     -> CValue.CBoolean(true),
        "value"    -> CValue.CInt(42),
        "fallback" -> CValue.CInt(0)
      )
    ).unsafeRunSync()
    outputsTrue("result") shouldBe CValue.CInt(42)

    // flag = false → fallback
    val outputsFalse = compileAndRun(
      source,
      Map(
        "flag"     -> CValue.CBoolean(false),
        "value"    -> CValue.CInt(42),
        "fallback" -> CValue.CInt(0)
      )
    ).unsafeRunSync()
    outputsFalse("result") shouldBe CValue.CInt(0)
  }

  // =========================================================================
  // 10. Multiple outputs
  // =========================================================================

  it should "handle multiple outputs" in {
    val source =
      """in x: Int
        |in y: String
        |in flag: Boolean
        |out x
        |out y
        |out flag
        |""".stripMargin

    val outputs = compileAndRun(
      source,
      Map(
        "x"    -> CValue.CInt(1),
        "y"    -> CValue.CString("hi"),
        "flag" -> CValue.CBoolean(true)
      )
    ).unsafeRunSync()

    outputs("x") shouldBe CValue.CInt(1)
    outputs("y") shouldBe CValue.CString("hi")
    outputs("flag") shouldBe CValue.CBoolean(true)
  }

  // =========================================================================
  // 11. Compilation error case (undefined variable)
  // =========================================================================

  it should "report compilation errors for undefined variables" in {
    val source = "in x: Int\nout undefined_var"
    val result = compiler.compile(source, "error-test")

    result.isLeft shouldBe true
    val errors = result.left.toOption.get
    errors should not be empty
    errors.exists(_.message.toLowerCase.contains("undefined")) shouldBe true
  }

  // =========================================================================
  // 12. Type mismatch error case
  // =========================================================================

  it should "report type errors for type mismatches" in {
    // Using an Int where Boolean is expected in a conditional
    val source =
      """in x: Int
        |result = if (x) x else x
        |out result
        |""".stripMargin

    val result = compiler.compile(source, "type-error-test")

    result.isLeft shouldBe true
    val errors = result.left.toOption.get
    errors should not be empty
  }

  // =========================================================================
  // Additional: Float passthrough
  // =========================================================================

  it should "pass through a Float input" in {
    val source  = "in x: Float\nout x"
    val outputs = compileAndRun(source, Map("x" -> CValue.CFloat(3.14))).unsafeRunSync()
    outputs("x") shouldBe CValue.CFloat(3.14)
  }

  // =========================================================================
  // Additional: Boolean passthrough
  // =========================================================================

  it should "pass through a Boolean input" in {
    val source  = "in x: Boolean\nout x"
    val outputs = compileAndRun(source, Map("x" -> CValue.CBoolean(true))).unsafeRunSync()
    outputs("x") shouldBe CValue.CBoolean(true)
  }
}
