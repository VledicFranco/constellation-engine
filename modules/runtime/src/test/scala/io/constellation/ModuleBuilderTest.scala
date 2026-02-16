package io.constellation

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

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
      "label"  -> CType.CString
    )
    module.spec.produces shouldBe Map(
      "sum"   -> CType.CInt,
      "count" -> CType.CInt
    )
  }

  // ===== HTTP endpoint configuration =====

  it should "support httpEndpoint on init builder" in {
    val module = ModuleBuilder
      .metadata("Published", "A published module", 1, 0)
      .httpEndpoint()
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x + in.y))
      .build

    module.spec.httpConfig shouldBe Some(ModuleHttpConfig())
    module.spec.httpConfig.get.published shouldBe true
  }

  it should "support httpEndpoint with custom config on init builder" in {
    val config = ModuleHttpConfig(published = false)
    val module = ModuleBuilder
      .metadata("CustomHttp", "Custom HTTP config", 1, 0)
      .httpEndpoint(config)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x))
      .build

    module.spec.httpConfig shouldBe Some(config)
    module.spec.httpConfig.get.published shouldBe false
  }

  it should "have no httpConfig by default" in {
    val module = ModuleBuilder
      .metadata("NoHttp", "No HTTP config", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x))
      .build

    module.spec.httpConfig shouldBe None
  }

  it should "support httpEndpoint after implementation" in {
    val module = ModuleBuilder
      .metadata("PostImpl", "HTTP after impl", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x))
      .httpEndpoint()
      .build

    module.spec.httpConfig shouldBe Some(ModuleHttpConfig())
  }

  // ===== Post-implementation builder methods (ModuleBuilder[I, O]) =====

  "ModuleBuilder[I, O]" should "support name after implementation" in {
    val module = ModuleBuilder
      .metadata("Original", "Description", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x))
      .name("Renamed")
      .build

    module.spec.metadata.name shouldBe "Renamed"
  }

  it should "support description after implementation" in {
    val module = ModuleBuilder
      .metadata("Test", "Original", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x))
      .description("Updated description")
      .build

    module.spec.metadata.description shouldBe "Updated description"
  }

  it should "support tags after implementation" in {
    val module = ModuleBuilder
      .metadata("Test", "Desc", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x))
      .tags("a", "b")
      .build

    module.spec.metadata.tags shouldBe List("a", "b")
  }

  // ===== Functional transformations =====

  case class DoubleOutput(value: Double)
  case class StringInput(text: String)

  it should "support map to transform output type" in {
    val builder = ModuleBuilder
      .metadata("MapTest", "Test map", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x + in.y))
      .map[DoubleOutput](out => DoubleOutput(out.result.toDouble))

    val module = builder.build
    module.spec.metadata.name shouldBe "MapTest"
    module.spec.produces shouldBe Map("value" -> CType.CFloat)
  }

  it should "support contraMap to transform input type" in {
    val builder = ModuleBuilder
      .metadata("ContraMapTest", "Test contraMap", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x + in.y))
      .contraMap[StringInput](s => TestInput(s.text.length.toLong, 0L))

    val module = builder.build
    module.spec.metadata.name shouldBe "ContraMapTest"
    module.spec.consumes shouldBe Map("text" -> CType.CString)
  }

  it should "support biMap to transform both input and output" in {
    val builder = ModuleBuilder
      .metadata("BiMapTest", "Test biMap", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x * in.y))
      .biMap[StringInput, DoubleOutput](
        s => TestInput(s.text.length.toLong, 2L),
        out => DoubleOutput(out.result.toDouble)
      )

    val module = builder.build
    module.spec.metadata.name shouldBe "BiMapTest"
    module.spec.consumes shouldBe Map("text" -> CType.CString)
    module.spec.produces shouldBe Map("value" -> CType.CFloat)
  }
}
