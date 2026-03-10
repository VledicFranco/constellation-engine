package io.constellation.dsl

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.*
import io.constellation.impl.ConstellationImpl

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Phase 1a + 1b acceptance-criteria tests for the Scala DSL. */
class PipelineDslSpec extends AnyFlatSpec with Matchers {

  // ---------------------------------------------------------------------------
  // Shared fixture types — single-field Products required by Phase 1a
  // ---------------------------------------------------------------------------

  case class TextInput(text: String)
  case class TextOutput(result: String)
  case class NumberInput(n: Long)
  case class NumberOutput(doubled: Long)

  // Phase 1b — multi-field fan-in type for p.assemble.
  // Field names deliberately match existing single-field wrappers so that
  // `ctx.field(textInputRef, _.text)` and `ctx.field(textOutputRef, _.result)`
  // are valid selectors and produce the correct nicknames.
  case class MergeInput(text: String, result: String)
  case class MergeOutput(combined: String)

  // Keep as ModuleBuilder[I, O] for use with p.step; call .build only when needed for setModule.
  private val uppercaseBuilder =
    ModuleBuilder
      .metadata("Uppercase", "Converts text to uppercase", 1, 0)
      .implementationPure[TextInput, TextOutput](in => TextOutput(in.text.toUpperCase))

  private val identityBuilder =
    ModuleBuilder
      .metadata("Identity", "Returns text unchanged", 1, 0)
      .implementationPure[TextOutput, TextOutput](in => in)

  private val doubleBuilder =
    ModuleBuilder
      .metadata("Double", "Doubles a number", 1, 0)
      .implementationPure[NumberInput, NumberOutput](in => NumberOutput(in.n * 2))

  private val mergeBuilder =
    ModuleBuilder
      .metadata("Merge", "Merges two strings", 1, 0)
      .implementationPure[MergeInput, MergeOutput](in => MergeOutput(in.text + "|" + in.result))

  // 3-input fan-in fixture — exercises p.assemble with 3 upstream refs.
  // Each upstream type has a field name matching the corresponding field in ThreeWayInput
  // so that ctx.field(aRef, _.a) → nickname "a", ctx.field(bRef, _.b) → nickname "b", etc.
  case class AInput(a: String)
  case class BInput(b: String)
  case class CInput(c: String)
  case class ThreeWayInput(a: String, b: String, c: String)
  case class ThreeWayOutput(combined: String)

  private val threeWayBuilder =
    ModuleBuilder
      .metadata("ThreeWay", "Merges three strings", 1, 0)
      .implementationPure[ThreeWayInput, ThreeWayOutput](in =>
        ThreeWayOutput(in.a + "|" + in.b + "|" + in.c)
      )

  // ---------------------------------------------------------------------------
  // Task 1.1 — TypedRef option accumulation
  // ---------------------------------------------------------------------------

  "TypedRef" should "accumulate retry option" in {
    val pipeline = Pipeline.define("test") { p =>
      val ref = p.input[TextInput]("in")
      val out = p.step(ref)(uppercaseBuilder)
      p.output("out", out.retry(3))
    }
    pipeline.callOptions.values.head.retry shouldBe Some(3)
  }

  it should "accumulate all 11 option methods and map them to the correct fields" in {
    val pipeline = Pipeline.define("AllOptions") { p =>
      val ref = p.input[TextInput]("in")
      val out = p.step(ref)(uppercaseBuilder)
      p.output(
        "out",
        out
          .retry(3)
          .timeout(5.seconds)
          .delay(1.second)
          .backoff("exponential")
          .cache(10.minutes)
          .cacheBackend("redis")
          .throttle(100, 1.second)
          .concurrency(4)
          .onError("skip")
          .lazyEval
          .priority(5)
      )
    }
    val opts = pipeline.callOptions.values.head
    opts.retry shouldBe Some(3)
    opts.timeoutMs shouldBe Some(5_000L)
    opts.delayMs shouldBe Some(1_000L)
    opts.backoff shouldBe Some("exponential")
    opts.cacheMs shouldBe Some(600_000L)
    opts.cacheBackend shouldBe Some("redis")
    opts.throttleCount shouldBe Some(100)
    opts.throttlePerMs shouldBe Some(1_000L)
    opts.concurrency shouldBe Some(4)
    opts.onError shouldBe Some("skip")
    opts.lazyEval shouldBe Some(true)
    opts.priority shouldBe Some(5)
  }

  it should "return a new TypedRef for each option call (immutable accumulation)" in {
    // Options live on the OUTPUT ref and are flushed when that ref is next consumed.
    val pipeline = Pipeline.define("Immutable") { p =>
      val ref = p.input[TextInput]("in")
      val out = p.step(ref)(uppercaseBuilder)
      val o1  = out.retry(3)
      val o2  = o1.timeout(5.seconds)
      // o1 carries only retry; o2 carries retry + timeout — both are independent refs
      p.output("out", o2)
    }
    // o2 was consumed by p.output → its options are flushed into callOptions
    val opts = pipeline.callOptions.values.head
    opts.retry shouldBe Some(3)
    opts.timeoutMs shouldBe Some(5_000L)
  }

  // ---------------------------------------------------------------------------
  // Task 1.2 — DagSpec structure
  // ---------------------------------------------------------------------------

  "Pipeline.define (single step)" should "register exactly one module and two data nodes" in {
    val pipeline = Pipeline.define("SingleStep") { p =>
      val ref = p.input[TextInput]("in")
      val out = p.step(ref)(uppercaseBuilder)
      p.output("out", out)
    }
    val spec = pipeline.spec
    spec.modules should have size 1
    spec.data should have size 2
  }

  it should "wire one inEdge and one outEdge" in {
    val pipeline = Pipeline.define("Edges") { p =>
      val ref = p.input[TextInput]("in")
      val out = p.step(ref)(uppercaseBuilder)
      p.output("out", out)
    }
    val spec = pipeline.spec
    spec.inEdges should have size 1
    spec.outEdges should have size 1
  }

  it should "register the declared output name and binding" in {
    val pipeline = Pipeline.define("Output") { p =>
      val ref = p.input[TextInput]("in")
      val out = p.step(ref)(uppercaseBuilder)
      p.output("my-output", out)
    }
    val spec = pipeline.spec
    spec.declaredOutputs should contain("my-output")
    spec.outputBindings.keys should contain("my-output")
  }

  it should "expose the field type (not CProduct) as input data node cType" in {
    val pipeline = Pipeline.define("InputCType") { p =>
      val ref = p.input[TextInput]("in")
      val out = p.step(ref)(uppercaseBuilder)
      p.output("out", out)
    }
    val inputNode = pipeline.spec.data.values.find(_.name == "in").get
    // TextInput is a 1-field Product; the DSL must unwrap to the field type CString
    inputNode.cType shouldBe CType.CString
  }

  it should "set the consuming-module nickname on the input data node" in {
    val pipeline = Pipeline.define("InputNickname") { p =>
      val ref = p.input[TextInput]("in")
      val out = p.step(ref)(uppercaseBuilder)
      p.output("out", out)
    }
    val spec       = pipeline.spec
    val moduleUUID = spec.modules.keys.head
    val inputNode  = spec.data.values.find(_.name == "in").get
    // The module consumes the field named "text"
    inputNode.nicknames should contain(moduleUUID -> "text")
  }

  it should "set the producing-module nickname on the output data node" in {
    val pipeline = Pipeline.define("OutputNickname") { p =>
      val ref = p.input[TextInput]("in")
      val out = p.step(ref)(uppercaseBuilder)
      p.output("out", out)
    }
    val spec       = pipeline.spec
    val moduleUUID = spec.modules.keys.head
    val outputNode = spec.data.values.find(_.name == "Uppercase-out").get
    // The module produces the field named "result"
    outputNode.nicknames should contain(moduleUUID -> "result")
  }

  it should "connect inEdges from the correct data node to the module" in {
    val pipeline = Pipeline.define("InEdge") { p =>
      val ref = p.input[TextInput]("in")
      val out = p.step(ref)(uppercaseBuilder)
      p.output("out", out)
    }
    val spec       = pipeline.spec
    val moduleUUID = spec.modules.keys.head
    val inputUUID  = spec.data.find(_._2.name == "in").get._1
    spec.inEdges should contain((inputUUID, moduleUUID))
  }

  it should "connect outEdges from the module to the correct data node" in {
    val pipeline = Pipeline.define("OutEdge") { p =>
      val ref = p.input[TextInput]("in")
      val out = p.step(ref)(uppercaseBuilder)
      p.output("out", out)
    }
    val spec       = pipeline.spec
    val moduleUUID = spec.modules.keys.head
    val outUUID    = spec.data.find(_._2.name == "Uppercase-out").get._1
    spec.outEdges should contain((moduleUUID, outUUID))
  }

  "Pipeline.define (two sequential steps)" should "register two modules, three data nodes, two edges each" in {
    val pipeline = Pipeline.define("TwoSteps") { p =>
      val ref1 = p.input[TextInput]("in")
      val ref2 = p.step(ref1)(uppercaseBuilder) // TextInput → TextOutput
      val ref3 = p.step(ref2)(identityBuilder)  // TextOutput → TextOutput
      p.output("out", ref3)
    }
    val spec = pipeline.spec
    spec.modules should have size 2
    spec.data should have size 3 // input + middle + final output
    spec.inEdges should have size 2
    spec.outEdges should have size 2
    spec.declaredOutputs should contain("out")
  }

  // ---------------------------------------------------------------------------
  // moduleOptions key == module node UUID (not output data node UUID)
  // ---------------------------------------------------------------------------

  "moduleOptions in PipelineImage" should "be keyed by the module node UUID" in {
    val pipeline = Pipeline.define("OptionsKey") { p =>
      val ref = p.input[TextInput]("in")
      val out = p.step(ref)(uppercaseBuilder)
      p.output("out", out.retry(5))
    }
    val moduleUUID = pipeline.spec.modules.keys.head

    // callOptions key must be the module node UUID
    pipeline.callOptions.keys should contain(moduleUUID)
    pipeline.image.moduleOptions.keys should contain(moduleUUID)
    pipeline.callOptions(moduleUUID).retry shouldBe Some(5)
  }

  // ---------------------------------------------------------------------------
  // Single-field constraint — enforced at compile time via requireSingleField.
  // Passing a multi-field type to p.step or p.adapt is a compile error, not a
  // runtime exception. No runtime test is needed; the constraint is verified by
  // the Scala 3 type checker on every build.
  // ---------------------------------------------------------------------------

  // ---------------------------------------------------------------------------
  // registerModules — named module collection and registration
  // ---------------------------------------------------------------------------

  "pipeline.moduleBuilders" should "contain one entry per p.step call" in {
    val pipeline = Pipeline.define("RegSingle") { p =>
      val ref = p.input[TextInput]("in")
      val out = p.step(ref)(uppercaseBuilder)
      p.output("out", out)
    }
    pipeline.moduleBuilders should have size 1
  }

  it should "contain one entry per p.step in a multi-step pipeline" in {
    val pipeline = Pipeline.define("RegMulti") { p =>
      val ref1 = p.input[TextInput]("in")
      val ref2 = p.step(ref1)(uppercaseBuilder)
      val ref3 = p.step(ref2)(identityBuilder)
      p.output("out", ref3)
    }
    pipeline.moduleBuilders should have size 2
  }

  it should "be empty for a pure-adapt pipeline (no named modules)" in {
    val pipeline = Pipeline.define("RegAdaptOnly") { p =>
      val ref     = p.input[TextInput]("in")
      val adapted = p.adapt(ref)(ti => TextOutput(ti.text))
      p.output("out", adapted)
    }
    pipeline.moduleBuilders shouldBe empty
  }

  "pipeline.registerModules" should "register named modules so ConstellationImpl can execute the pipeline" in {
    val pipeline = Pipeline.define("RegisterE2E") { p =>
      val ref = p.input[TextInput]("text-in")
      val out = p.step(ref)(uppercaseBuilder)
      p.output("text-out", out)
    }

    val constellation = ConstellationImpl.init.unsafeRunSync()
    pipeline.registerModules(constellation).unsafeRunSync()

    val sig = constellation
      .run(pipeline.load, Map("text-in" -> CValue.CString("hello")))
      .unsafeRunSync()

    sig.outputs.get("text-out") shouldBe Some(CValue.CString("HELLO"))
  }

  it should "not register synthetic adapt modules (they execute via syntheticModules)" in {
    val pipeline = Pipeline.define("RegisterAdaptOnly") { p =>
      val ref     = p.input[TextInput]("in")
      val adapted = p.adapt(ref)(ti => TextOutput(ti.text.toUpperCase))
      p.output("out", adapted)
    }

    // registerModules has nothing to register — adapt modules are synthetic
    val constellation = ConstellationImpl.init.unsafeRunSync()
    pipeline.registerModules(constellation).unsafeRunSync()

    val sig = constellation
      .run(pipeline.load, Map("in" -> CValue.CString("hello")))
      .unsafeRunSync()

    sig.outputs.get("out") shouldBe Some(CValue.CString("HELLO"))
  }

  // ---------------------------------------------------------------------------
  // Task 1.3 — pipeline.load → LoadedPipeline → ConstellationImpl.run
  // ---------------------------------------------------------------------------

  "pipeline.load" should "return a LoadedPipeline with the correct structuralHash" in {
    val pipeline = Pipeline.define("HashCheck") { p =>
      val ref = p.input[TextInput]("in")
      val out = p.step(ref)(uppercaseBuilder)
      p.output("out", out)
    }
    val loaded = pipeline.load
    loaded.structuralHash shouldBe PipelineImage.computeStructuralHash(pipeline.spec)
    loaded.image.dagSpec shouldBe pipeline.spec
  }

  it should "produce a LoadedPipeline that ConstellationImpl can execute end-to-end" in {
    val pipeline = Pipeline.define("EndToEnd") { p =>
      val ref = p.input[TextInput]("text-in")
      val out = p.step(ref)(uppercaseBuilder)
      p.output("text-out", out)
    }

    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(uppercaseBuilder.build).unsafeRunSync()

    val loaded = pipeline.load
    val sig = constellation
      .run(loaded, Map("text-in" -> CValue.CString("hello")))
      .unsafeRunSync()

    sig.outputs.get("text-out") shouldBe Some(CValue.CString("HELLO"))
  }

  it should "pass a two-step pipeline through ConstellationImpl end-to-end" in {
    val pipeline = Pipeline.define("TwoStepE2E") { p =>
      val ref1 = p.input[TextInput]("in")
      val ref2 = p.step(ref1)(uppercaseBuilder)
      val ref3 = p.step(ref2)(identityBuilder)
      p.output("out", ref3)
    }

    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(uppercaseBuilder.build).unsafeRunSync()
    constellation.setModule(identityBuilder.build).unsafeRunSync()

    val sig = constellation
      .run(pipeline.load, Map("in" -> CValue.CString("world")))
      .unsafeRunSync()

    sig.outputs.get("out") shouldBe Some(CValue.CString("WORLD"))
  }

  // ---------------------------------------------------------------------------
  // Phase 1b — p.assemble (fan-in from multiple upstream nodes)
  // ---------------------------------------------------------------------------

  "p.assemble" should "produce correct DagSpec for a two-input fan-in module" in {
    val pipeline = Pipeline.define("Assemble") { p =>
      // TextInput.text and TextOutput.result match MergeInput field names
      val textRef   = p.input[TextInput]("text-in")
      val resultRef = p.input[TextOutput]("result-in")
      val out = p.assemble(mergeBuilder) { ctx =>
        MergeInput(
          text = ctx.field(textRef, _.text),
          result = ctx.field(resultRef, _.result)
        )
      }
      p.output("out", out)
    }
    val spec = pipeline.spec
    spec.modules should have size 1
    spec.data should have size 3     // text-in + result-in + Merge-out
    spec.inEdges should have size 2  // text-in→Merge, result-in→Merge
    spec.outEdges should have size 1 // Merge→Merge-out
    spec.declaredOutputs should contain("out")
  }

  it should "wire field nicknames for both upstream nodes" in {
    val pipeline = Pipeline.define("AssembleNicknames") { p =>
      val textRef   = p.input[TextInput]("text-in")
      val resultRef = p.input[TextOutput]("result-in")
      val out = p.assemble(mergeBuilder) { ctx =>
        MergeInput(
          text = ctx.field(textRef, _.text),
          result = ctx.field(resultRef, _.result)
        )
      }
      p.output("out", out)
    }
    val spec       = pipeline.spec
    val moduleUUID = spec.modules.keys.head
    val textNode   = spec.data.values.find(_.name == "text-in").get
    val resultNode = spec.data.values.find(_.name == "result-in").get
    textNode.nicknames should contain(moduleUUID -> "text")
    resultNode.nicknames should contain(moduleUUID -> "result")
  }

  it should "execute end-to-end through ConstellationImpl" in {
    val pipeline = Pipeline.define("AssembleE2E") { p =>
      val textRef   = p.input[TextInput]("t")
      val resultRef = p.input[TextOutput]("r")
      val out = p.assemble(mergeBuilder) { ctx =>
        MergeInput(
          text = ctx.field(textRef, _.text),
          result = ctx.field(resultRef, _.result)
        )
      }
      p.output("out", out)
    }

    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(mergeBuilder.build).unsafeRunSync()

    val sig = constellation
      .run(
        pipeline.load,
        Map("t" -> CValue.CString("hello"), "r" -> CValue.CString("world"))
      )
      .unsafeRunSync()

    sig.outputs.get("out") shouldBe Some(CValue.CString("hello|world"))
  }

  // ---------------------------------------------------------------------------
  // Phase 1b — p.adapt (synthetic inline type-adaptation module)
  // ---------------------------------------------------------------------------

  "p.adapt" should "register a synthetic module in the TypedPipeline" in {
    val pipeline = Pipeline.define("AdaptSynthetic") { p =>
      val ref     = p.input[TextInput]("in")
      val adapted = p.adapt(ref)(ti => TextOutput(ti.text))
      p.output("out", adapted)
    }
    // The adapt module appears in both the DagSpec and the syntheticModules map
    pipeline.spec.modules should have size 1
    pipeline.syntheticModules should have size 1
    // The UUID keys must match
    pipeline.spec.modules.keys.toSet shouldBe pipeline.syntheticModules.keys.toSet
  }

  it should "produce correct DagSpec structure (input node + adapt-out node)" in {
    val pipeline = Pipeline.define("AdaptDagSpec") { p =>
      val ref     = p.input[TextInput]("in")
      val adapted = p.adapt(ref)(ti => TextOutput(ti.text))
      p.output("out", adapted)
    }
    val spec = pipeline.spec
    spec.modules should have size 1
    spec.data should have size 2 // "in" + adapt-out
    spec.inEdges should have size 1
    spec.outEdges should have size 1
  }

  it should "execute a pure adaptation end-to-end through ConstellationImpl" in {
    // adapt wraps a pure function — no setModule needed (synthetic module bypasses registry)
    val pipeline = Pipeline.define("AdaptE2E") { p =>
      val ref     = p.input[TextInput]("in")
      val adapted = p.adapt(ref)(ti => TextOutput(ti.text.toUpperCase))
      p.output("out", adapted)
    }

    val constellation = ConstellationImpl.init.unsafeRunSync()

    val sig = constellation
      .run(pipeline.load, Map("in" -> CValue.CString("hello")))
      .unsafeRunSync()

    sig.outputs.get("out") shouldBe Some(CValue.CString("HELLO"))
  }

  it should "chain adapt + step and execute correctly" in {
    // input → adapt (TextInput→TextInput identity re-wrap) → step (uppercase) → output
    val pipeline = Pipeline.define("AdaptThenStep") { p =>
      val ref = p.input[TextInput]("in")
      // adapt passes the value through unchanged (just proves the chain works)
      val adapted = p.adapt(ref)(ti => TextInput(ti.text))
      val out     = p.step(adapted)(uppercaseBuilder)
      p.output("out", out)
    }

    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(uppercaseBuilder.build).unsafeRunSync()

    val sig = constellation
      .run(pipeline.load, Map("in" -> CValue.CString("hello")))
      .unsafeRunSync()

    sig.outputs.get("out") shouldBe Some(CValue.CString("HELLO"))
  }

  it should "chain two adapts in sequence and execute correctly" in {
    // input → adapt (TextInput→TextOutput, append "!") → adapt (TextOutput→TextInput, uppercase)
    val pipeline = Pipeline.define("TwoAdaptsChained") { p =>
      val ref      = p.input[TextInput]("in")
      val adapted1 = p.adapt(ref)(ti => TextOutput(ti.text + "!"))
      val adapted2 = p.adapt(adapted1)(to => TextInput(to.result.toUpperCase))
      p.output("out", adapted2)
    }

    // Two synthetic modules, no named modules — no setModule needed
    pipeline.syntheticModules should have size 2
    pipeline.moduleBuilders shouldBe empty

    val constellation = ConstellationImpl.init.unsafeRunSync()
    val sig = constellation
      .run(pipeline.load, Map("in" -> CValue.CString("hello")))
      .unsafeRunSync()

    sig.outputs.get("out") shouldBe Some(CValue.CString("HELLO!"))
  }

  // ---------------------------------------------------------------------------
  // Advanced topology — 3+ sequential steps
  // ---------------------------------------------------------------------------

  "Pipeline.define (three sequential steps)" should "produce correct DagSpec structure" in {
    val pipeline = Pipeline.define("ThreeSteps") { p =>
      val ref1 = p.input[TextInput]("in")
      val ref2 = p.step(ref1)(uppercaseBuilder) // TextInput  → TextOutput
      val ref3 = p.step(ref2)(identityBuilder)  // TextOutput → TextOutput
      val ref4 = p.step(ref3)(identityBuilder)  // TextOutput → TextOutput
      p.output("out", ref4)
    }
    val spec = pipeline.spec
    spec.modules should have size 3
    spec.data should have size 4 // in + 3 output nodes
    spec.inEdges should have size 3
    spec.outEdges should have size 3
    pipeline.moduleBuilders should have size 3
  }

  it should "execute correctly end-to-end" in {
    val pipeline = Pipeline.define("ThreeStepsE2E") { p =>
      val ref1 = p.input[TextInput]("in")
      val ref2 = p.step(ref1)(uppercaseBuilder)
      val ref3 = p.step(ref2)(identityBuilder)
      val ref4 = p.step(ref3)(identityBuilder)
      p.output("out", ref4)
    }

    val constellation = ConstellationImpl.init.unsafeRunSync()
    pipeline.registerModules(constellation).unsafeRunSync()

    val sig = constellation
      .run(pipeline.load, Map("in" -> CValue.CString("hello")))
      .unsafeRunSync()

    sig.outputs.get("out") shouldBe Some(CValue.CString("HELLO"))
  }

  // ---------------------------------------------------------------------------
  // Advanced topology — 3-input fan-in via p.assemble
  // ---------------------------------------------------------------------------

  "p.assemble (3-input fan-in)" should "produce correct DagSpec structure" in {
    val pipeline = Pipeline.define("ThreeWayAssemble") { p =>
      val aRef = p.input[AInput]("a-in")
      val bRef = p.input[BInput]("b-in")
      val cRef = p.input[CInput]("c-in")
      val out = p.assemble(threeWayBuilder) { ctx =>
        ThreeWayInput(
          a = ctx.field(aRef, _.a),
          b = ctx.field(bRef, _.b),
          c = ctx.field(cRef, _.c)
        )
      }
      p.output("out", out)
    }
    val spec = pipeline.spec
    spec.modules should have size 1
    spec.data should have size 4    // a-in + b-in + c-in + ThreeWay-out
    spec.inEdges should have size 3 // a→ThreeWay, b→ThreeWay, c→ThreeWay
    spec.outEdges should have size 1
    spec.declaredOutputs should contain("out")
  }

  it should "wire nicknames for all three upstream nodes" in {
    val pipeline = Pipeline.define("ThreeWayNicknames") { p =>
      val aRef = p.input[AInput]("a-in")
      val bRef = p.input[BInput]("b-in")
      val cRef = p.input[CInput]("c-in")
      p.assemble(threeWayBuilder) { ctx =>
        ThreeWayInput(
          a = ctx.field(aRef, _.a),
          b = ctx.field(bRef, _.b),
          c = ctx.field(cRef, _.c)
        )
      }
    }
    val spec       = pipeline.spec
    val moduleUUID = spec.modules.keys.head
    val aNode      = spec.data.values.find(_.name == "a-in").get
    val bNode      = spec.data.values.find(_.name == "b-in").get
    val cNode      = spec.data.values.find(_.name == "c-in").get
    aNode.nicknames should contain(moduleUUID -> "a")
    bNode.nicknames should contain(moduleUUID -> "b")
    cNode.nicknames should contain(moduleUUID -> "c")
  }

  it should "execute correctly end-to-end" in {
    val pipeline = Pipeline.define("ThreeWayE2E") { p =>
      val aRef = p.input[AInput]("a")
      val bRef = p.input[BInput]("b")
      val cRef = p.input[CInput]("c")
      val out = p.assemble(threeWayBuilder) { ctx =>
        ThreeWayInput(
          a = ctx.field(aRef, _.a),
          b = ctx.field(bRef, _.b),
          c = ctx.field(cRef, _.c)
        )
      }
      p.output("out", out)
    }

    val constellation = ConstellationImpl.init.unsafeRunSync()
    pipeline.registerModules(constellation).unsafeRunSync()

    val sig = constellation
      .run(
        pipeline.load,
        Map(
          "a" -> CValue.CString("foo"),
          "b" -> CValue.CString("bar"),
          "c" -> CValue.CString("baz")
        )
      )
      .unsafeRunSync()

    sig.outputs.get("out") shouldBe Some(CValue.CString("foo|bar|baz"))
  }
}
