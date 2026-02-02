package io.constellation.lsp

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.constellation.lsp.protocol.LspTypes.{CompletionItem, CompletionItemKind}
import io.constellation.lsp.WithClauseCompletions.*

class WithClauseCompletionsTest extends AnyFlatSpec with Matchers {

  // ========== Context Detection Tests ==========

  "analyzeContext" should "detect AfterModuleCall when cursor is after module call" in {
    analyzeContext("result = MyModule(input) ", "") shouldBe AfterModuleCall
    analyzeContext("  data = Process(x, y) ", "") shouldBe AfterModuleCall
    analyzeContext("Transform(a)", "") shouldBe AfterModuleCall
  }

  it should "detect AfterWith when cursor is after 'with '" in {
    analyzeContext("result = MyModule(input) with ", "") shouldBe AfterWith
    analyzeContext("Process(x) with ", "") shouldBe AfterWith
  }

  it should "detect AfterComma with used options" in {
    val ctx = analyzeContext("result = MyModule(input) with retry: 3, ", "")
    ctx shouldBe a[AfterComma]
    ctx.asInstanceOf[AfterComma].usedOptions should contain("retry")
  }

  it should "detect AfterComma with multiple used options" in {
    val ctx = analyzeContext("result = MyModule(input) with retry: 3, timeout: 30s, ", "")
    ctx shouldBe a[AfterComma]
    val usedOptions = ctx.asInstanceOf[AfterComma].usedOptions
    usedOptions should contain("retry")
    usedOptions should contain("timeout")
  }

  it should "detect AfterBackoffColon" in {
    analyzeContext("result = MyModule(input) with backoff: ", "") shouldBe AfterBackoffColon
    analyzeContext("with retry: 3, backoff: ", "") shouldBe AfterBackoffColon
  }

  it should "detect AfterOnErrorColon" in {
    analyzeContext("result = MyModule(input) with on_error: ", "") shouldBe AfterOnErrorColon
    analyzeContext("with retry: 3, on_error: ", "") shouldBe AfterOnErrorColon
  }

  it should "detect AfterPriorityColon" in {
    analyzeContext("result = MyModule(input) with priority: ", "") shouldBe AfterPriorityColon
  }

  it should "detect AfterCacheBackendColon" in {
    val ctx = analyzeContext("with cache_backend: ", "")
    ctx shouldBe a[AfterCacheBackendColon]

    val ctx2 = analyzeContext("with cache_backend: \"red", "")
    ctx2 shouldBe a[AfterCacheBackendColon]
    ctx2.asInstanceOf[AfterCacheBackendColon].partial shouldBe "red"
  }

  it should "detect AfterNumber for duration options" in {
    val ctx = analyzeContext("with timeout: 30 ", "")
    ctx shouldBe a[AfterNumber]
    ctx.asInstanceOf[AfterNumber].number shouldBe "30"

    val ctx2 = analyzeContext("with delay: 100 ", "")
    ctx2 shouldBe a[AfterNumber]
    ctx2.asInstanceOf[AfterNumber].number shouldBe "100"

    val ctx3 = analyzeContext("with cache: 5 ", "")
    ctx3 shouldBe a[AfterNumber]
    ctx3.asInstanceOf[AfterNumber].number shouldBe "5"
  }

  it should "detect NotInWithClause for unrelated contexts" in {
    analyzeContext("in text: String", "") shouldBe NotInWithClause
    analyzeContext("result = ", "") shouldBe NotInWithClause
    analyzeContext("out result", "") shouldBe NotInWithClause
  }

  // ========== Completion Generation Tests ==========

  "getCompletions" should "return 'with' keyword for AfterModuleCall" in {
    val completions = getCompletions(AfterModuleCall)
    completions should have length 1
    completions.head.label shouldBe "with"
    completions.head.kind shouldBe Some(CompletionItemKind.Keyword)
  }

  it should "return all option names for AfterWith" in {
    val completions = getCompletions(AfterWith)
    completions.map(_.label) should contain allOf (
      "retry", "timeout", "delay", "backoff", "fallback",
      "cache", "cache_backend", "throttle", "concurrency",
      "on_error", "lazy", "priority"
    )
  }

  it should "exclude used options for AfterComma" in {
    val completions = getCompletions(AfterComma(Set("retry", "timeout")))
    completions.map(_.label) should not contain "retry"
    completions.map(_.label) should not contain "timeout"
    completions.map(_.label) should contain("delay")
    completions.map(_.label) should contain("backoff")
  }

  it should "return backoff strategies for AfterBackoffColon" in {
    val completions = getCompletions(AfterBackoffColon)
    completions.map(_.label) should contain allOf ("fixed", "linear", "exponential")
    completions should have length 3
  }

  it should "return error strategies for AfterOnErrorColon" in {
    val completions = getCompletions(AfterOnErrorColon)
    completions.map(_.label) should contain allOf ("propagate", "skip", "log", "wrap")
    completions should have length 4
  }

  it should "return priority levels for AfterPriorityColon" in {
    val completions = getCompletions(AfterPriorityColon)
    completions.map(
      _.label
    ) should contain allOf ("critical", "high", "normal", "low", "background")
    completions should have length 5
  }

  it should "return cache backends for AfterCacheBackendColon" in {
    val completions = getCompletions(AfterCacheBackendColon(""))
    completions.map(_.label) should contain allOf ("\"memory\"", "\"redis\"", "\"memcached\"")
    completions should have length 3
  }

  it should "return duration units for AfterNumber" in {
    val completions = getCompletions(AfterNumber("30"))
    completions.map(_.label) should contain allOf ("ms", "s", "min", "h", "d")
    completions should have length 5
  }

  it should "return empty list for NotInWithClause" in {
    val completions = getCompletions(NotInWithClause)
    completions shouldBe empty
  }

  // ========== Option Definition Tests ==========

  "allOptions" should "contain all 12 expected options" in {
    allOptions should have length 12
    allOptions.map(_.name) should contain allOf (
      "retry", "timeout", "delay", "backoff", "fallback",
      "cache", "cache_backend", "throttle", "concurrency",
      "on_error", "lazy", "priority"
    )
  }

  it should "have valid placeholders for each option" in {
    allOptions.foreach { opt =>
      opt.placeholder should not be empty
    }
  }

  it should "have documentation for each option" in {
    allOptions.foreach { opt =>
      opt.documentation should not be empty
    }
  }

  // ========== Integration-style Tests ==========

  "WithClauseCompletions" should "support typical completion workflow" in {
    // Step 1: After module call - suggest 'with'
    val ctx1 = analyzeContext("result = Process(data) ", "")
    ctx1 shouldBe AfterModuleCall
    getCompletions(ctx1).map(_.label) should contain("with")

    // Step 2: After 'with' - suggest options
    val ctx2 = analyzeContext("result = Process(data) with ", "")
    ctx2 shouldBe AfterWith
    getCompletions(ctx2).map(_.label) should contain("retry")

    // Step 3: After first option - suggest remaining options
    val ctx3 = analyzeContext("result = Process(data) with retry: 3, ", "")
    ctx3 shouldBe a[AfterComma]
    val completions3 = getCompletions(ctx3)
    completions3.map(_.label) should not contain "retry"
    completions3.map(_.label) should contain("timeout")

    // Step 4: After 'backoff:' - suggest strategies
    val ctx4 = analyzeContext("result = Process(data) with retry: 3, backoff: ", "")
    ctx4 shouldBe AfterBackoffColon
    getCompletions(ctx4).map(_.label) should contain("exponential")
  }

  it should "handle duration unit completion" in {
    val ctx = analyzeContext("result = Process(data) with timeout: 30 ", "")
    ctx shouldBe a[AfterNumber]
    val completions = getCompletions(ctx)
    completions.map(_.label) should contain("s")
    completions.map(_.label) should contain("ms")
    completions.map(_.label) should contain("min")
  }
}
