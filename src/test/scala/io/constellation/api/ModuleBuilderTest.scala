package io.constellation.api

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

class ModuleBuilderTest extends AnyFlatSpec with Matchers {

  case class TestInput(x: Long, y: Long)
  case class TestOutput(result: Long)

  "ModuleBuilder" should "create a module with metadata" in {
    val module = ModuleBuilder
      .metadata(
        name = "TestModule",
        description = "A test module",
        majorVersion = 1,
        minorVersion = 2
      )
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x + in.y))
      .build

    module.spec.metadata.name shouldBe "TestModule"
    module.spec.metadata.description shouldBe "A test module"
    module.spec.metadata.majorVersion shouldBe 1
    module.spec.metadata.minorVersion shouldBe 2
  }

  it should "support tags" in {
    val module = ModuleBuilder
      .metadata("Tagged", "Has tags", 1, 0)
      .tags("tag1", "tag2", "tag3")
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x))
      .build

    module.spec.metadata.tags shouldBe List("tag1", "tag2", "tag3")
  }

  it should "support custom timeouts" in {
    val module = ModuleBuilder
      .metadata("Timeouts", "Custom timeouts", 1, 0)
      .inputsTimeout(5.seconds)
      .moduleTimeout(10.seconds)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x))
      .build

    module.spec.config.inputsTimeout shouldBe 5.seconds
    module.spec.config.moduleTimeout shouldBe 10.seconds
  }

  it should "support definition context" in {
    val ctx = Map("key" -> Json.fromString("value"))
    val module = ModuleBuilder
      .metadata("WithContext", "Has context", 1, 0)
      .definitionContext(ctx)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x))
      .build

    module.spec.definitionContext shouldBe Some(ctx)
  }

  it should "infer consumes spec from input case class" in {
    val module = ModuleBuilder
      .metadata("InferConsumes", "Infers input types", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x + in.y))
      .build

    module.spec.consumes shouldBe Map(
      "x" -> CType.CInt,
      "y" -> CType.CInt
    )
  }

  it should "infer produces spec from output case class" in {
    val module = ModuleBuilder
      .metadata("InferProduces", "Infers output types", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x))
      .build

    module.spec.produces shouldBe Map(
      "result" -> CType.CInt
    )
  }

  it should "support IO-based implementation" in {
    var sideEffect = 0L

    val module = ModuleBuilder
      .metadata("IOImpl", "IO implementation", 1, 0)
      .implementation[TestInput, TestOutput] { in =>
        IO {
          sideEffect = in.x * in.y
          TestOutput(sideEffect)
        }
      }
      .build

    module.spec.metadata.name shouldBe "IOImpl"
  }

  it should "allow chaining configuration methods" in {
    val module = ModuleBuilder
      .metadata("Chained", "Chained config", 1, 0)
      .name("RenamedModule")
      .description("Updated description")
      .version(2, 5)
      .tags("updated")
      .inputsTimeout(3.seconds)
      .moduleTimeout(6.seconds)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x))
      .build

    module.spec.metadata.name shouldBe "RenamedModule"
    module.spec.metadata.description shouldBe "Updated description"
    module.spec.metadata.majorVersion shouldBe 2
    module.spec.metadata.minorVersion shouldBe 5
    module.spec.metadata.tags shouldBe List("updated")
    module.spec.config.inputsTimeout shouldBe 3.seconds
    module.spec.config.moduleTimeout shouldBe 6.seconds
  }

  "ModuleBuilderInit" should "allow configuration before implementation is set" in {
    val init = ModuleBuilder
      .metadata("PreConfig", "Pre-implementation config", 1, 0)
      .tags("pre")
      .inputsTimeout(2.seconds)

    // Can set implementation and build later
    val module = init
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x))
      .build

    module.spec.metadata.tags shouldBe List("pre")
    module.spec.config.inputsTimeout shouldBe 2.seconds
  }

  "Complex types" should "work with nested case classes" in {
    case class NestedInput(values: List[Long], label: String)
    case class NestedOutput(sum: Long, count: Long)

    val module = ModuleBuilder
      .metadata("Nested", "Nested types", 1, 0)
      .implementationPure[NestedInput, NestedOutput] { in =>
        NestedOutput(in.values.sum, in.values.length.toLong)
      }
      .build

    module.spec.consumes shouldBe Map(
      "values" -> CType.CList(CType.CInt),
      "label" -> CType.CString
    )
    module.spec.produces shouldBe Map(
      "sum" -> CType.CInt,
      "count" -> CType.CInt
    )
  }
}
