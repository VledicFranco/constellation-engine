package io.constellation.examples.app.dsl

import scala.concurrent.duration.*

import cats.effect.unsafe.implicits.global

import io.constellation.*
import io.constellation.dsl.*
import io.constellation.impl.ConstellationImpl

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for the Scala DSL example in [[DslExamples]].
  *
  * Verifies that the DSL-built pipeline:
  *   - Produces the correct `DagSpec` structure (right number of nodes, edges, outputs)
  *   - Executes end-to-end correctly via `ConstellationImpl`
  *   - Wires call options (`retry`, `timeout`, `cache`) into `PipelineImage.moduleOptions`
  *   - Registers only the named modules via `registerModules` (not synthetic adapt module)
  */
class DslExamplesTest extends AnyFlatSpec with Matchers {

  import DslExamples.*

  // ---------------------------------------------------------------------------
  // DagSpec structure
  // ---------------------------------------------------------------------------

  "textPipeline.spec" should "have the correct number of modules" in {
    // adapt (synthetic) + trim + uppercase + summarize = 4 modules in DagSpec
    textPipeline.spec.modules should have size 4
  }

  it should "have the correct number of data nodes" in {
    // raw + adapt-out + trim-out + uppercase-out + summarize-out = 5 data nodes
    textPipeline.spec.data should have size 5
  }

  it should "declare exactly one output named 'summary'" in {
    textPipeline.spec.declaredOutputs should contain("summary")
    textPipeline.spec.declaredOutputs should have size 1
  }

  it should "have correct edge counts" in {
    // inEdges:  raw→adapt, adapt-out→trim, trim-out→uppercase, trim-out→summarize, uppercase-out→summarize = 5
    // outEdges: adapt→adapt-out, trim→trim-out, uppercase→uppercase-out, summarize→summarize-out = 4
    textPipeline.spec.inEdges should have size 5
    textPipeline.spec.outEdges should have size 4
  }

  // ---------------------------------------------------------------------------
  // Synthetic modules (p.adapt creates one synthetic module)
  // ---------------------------------------------------------------------------

  "textPipeline.syntheticModules" should "contain exactly the adapt synthetic module" in {
    textPipeline.syntheticModules should have size 1
  }

  it should "have a UUID that also appears in the DagSpec modules map" in {
    val syntheticUuids = textPipeline.syntheticModules.keys.toSet
    val dagModuleUuids = textPipeline.spec.modules.keys.toSet
    syntheticUuids.subsetOf(dagModuleUuids) shouldBe true
  }

  // ---------------------------------------------------------------------------
  // Call options
  // ---------------------------------------------------------------------------

  "textPipeline call options" should "include retry=2 and timeoutMs on the trim module" in {
    val trimModuleUuid = textPipeline.spec.modules
      .find(_._2.name == "Trim")
      .map(_._1)
      .getOrElse(fail("Trim module not found in DagSpec"))

    val opts =
      textPipeline.callOptions.getOrElse(trimModuleUuid, fail("No options for Trim module"))
    opts.retry shouldBe Some(2)
    opts.timeoutMs shouldBe Some(5_000L)
  }

  it should "include cacheMs on the uppercase module" in {
    val upperModuleUuid = textPipeline.spec.modules
      .find(_._2.name == "Uppercase")
      .map(_._1)
      .getOrElse(fail("Uppercase module not found in DagSpec"))

    val opts =
      textPipeline.callOptions.getOrElse(upperModuleUuid, fail("No options for Uppercase module"))
    opts.cacheMs shouldBe Some(600_000L)
  }

  // ---------------------------------------------------------------------------
  // End-to-end execution
  // ---------------------------------------------------------------------------

  "DslExamples.run" should "trim, uppercase, and assemble the result correctly" in {
    val result = DslExamples.run("  hello world  ").unsafeRunSync()
    result shouldBe "'hello world' → 'HELLO WORLD'"
  }

  it should "handle empty string input" in {
    val result = DslExamples.run("   ").unsafeRunSync()
    result shouldBe "'' → ''"
  }

  it should "handle already-trimmed input" in {
    val result = DslExamples.run("scala").unsafeRunSync()
    result shouldBe "'scala' → 'SCALA'"
  }

  // ---------------------------------------------------------------------------
  // registerModules — named module collection
  // ---------------------------------------------------------------------------

  "textPipeline.moduleBuilders" should "contain exactly the 3 named modules (not the adapt synthetic)" in {
    // trim + uppercase + summarize = 3; the adapt module is synthetic and excluded
    textPipeline.moduleBuilders should have size 3
  }

  "textPipeline.registerModules" should "produce working execution as an alternative to manual setModule calls" in {
    val result = (for {
      c      <- ConstellationImpl.init
      _      <- textPipeline.registerModules(c)
      output <- c.run(textPipeline.load, Map("raw" -> CValue.CString("  hello  ")))
    } yield output.outputs.get("summary")).unsafeRunSync()

    result shouldBe Some(CValue.CString("'hello' → 'HELLO'"))
  }

  // ---------------------------------------------------------------------------
  // Structural hash note (documented divergence)
  // ---------------------------------------------------------------------------

  "textPipeline.image.structuralHash" should "be non-empty and deterministic" in {
    val h1 = textPipeline.image.structuralHash
    val h2 = Pipeline
      .define("textProcessing") { p =>
        val rawRef       = p.input[RawInput]("raw")
        val trimInputRef = p.adapt(rawRef)(r => TrimInput(r.text))
        val trimmedRef   = p.step(trimInputRef)(trimBuilder).retry(2).timeout(5.seconds)
        val upperRef     = p.step(trimmedRef)(uppercaseBuilder).cache(10.minutes)
        val summaryRef = p.assemble(summarizeBuilder) { ctx =>
          SummaryInput(
            text = ctx.field(trimmedRef, _.text),
            result = ctx.field(upperRef, _.result)
          )
        }
        p.output("summary", summaryRef)
      }
      .image
      .structuralHash

    h1 should not be empty
    h1 shouldBe h2 // deterministic: same topology produces same hash
  }
}
