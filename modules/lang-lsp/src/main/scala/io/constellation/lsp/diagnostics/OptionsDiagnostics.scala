package io.constellation.lsp.diagnostics

import io.constellation.lang.ast.{BackoffStrategy, ErrorStrategy, ModuleCallOptions, PriorityLevel}
import io.constellation.lsp.protocol.LspTypes.{Diagnostic, DiagnosticSeverity, Hover, MarkupContent, Position, Range}

/**
 * Diagnostics and hover support for module call options (`with` clause).
 *
 * Provides:
 * - Semantic warnings for questionable option combinations
 * - Errors for invalid option values
 * - Hover documentation for option names and strategy values
 */
object OptionsDiagnostics {

  /** Maximum recommended retry count before warning */
  val MaxRecommendedRetry = 10

  /** Diagnostic code prefix for options */
  val DiagnosticCodePrefix = "OPTS"

  // ========== Diagnostic Rules ==========

  /**
   * Analyze module call options and return any diagnostics.
   *
   * @param options The parsed ModuleCallOptions
   * @param optionsRange The source range of the entire `with` clause
   * @return List of diagnostics for invalid/questionable options
   */
  def diagnose(options: ModuleCallOptions, optionsRange: Range): List[Diagnostic] = {
    val diagnostics = List.newBuilder[Diagnostic]

    // Warning: delay without retry
    if (options.delay.isDefined && options.retry.isEmpty) {
      diagnostics += Diagnostic(
        range = optionsRange,
        severity = Some(DiagnosticSeverity.Warning),
        code = Some(s"${DiagnosticCodePrefix}001"),
        source = Some("constellation-lang"),
        message = buildMessage(
          "Option `delay` has no effect without `retry`",
          "The `delay` option specifies the wait time between retry attempts. " +
          "Without `retry`, the module is only called once, so the delay is never used.",
          Some("Add `retry: N` to enable retry with delay, or remove `delay` if retries are not needed.")
        )
      )
    }

    // Warning: backoff without delay
    if (options.backoff.isDefined && options.delay.isEmpty) {
      diagnostics += Diagnostic(
        range = optionsRange,
        severity = Some(DiagnosticSeverity.Warning),
        code = Some(s"${DiagnosticCodePrefix}002"),
        source = Some("constellation-lang"),
        message = buildMessage(
          "Option `backoff` has no effect without `delay`",
          "The `backoff` option controls how the delay changes between retries. " +
          "Without a base `delay`, the backoff strategy cannot be applied.",
          Some("Add `delay: <duration>` to set a base delay for the backoff strategy.")
        )
      )
    }

    // Warning: high retry count
    options.retry.foreach { retryCount =>
      if (retryCount > MaxRecommendedRetry) {
        diagnostics += Diagnostic(
          range = optionsRange,
          severity = Some(DiagnosticSeverity.Warning),
          code = Some(s"${DiagnosticCodePrefix}003"),
          source = Some("constellation-lang"),
          message = buildMessage(
            s"High retry count: $retryCount exceeds recommended maximum of $MaxRecommendedRetry",
            "High retry counts can lead to long execution times and may mask underlying issues. " +
            "Consider whether so many retries are truly necessary.",
            Some("Use a lower retry count with exponential backoff, or add a timeout to limit total execution time.")
          )
        )
      }
    }

    // Error: negative retry
    options.retry.foreach { retryCount =>
      if (retryCount < 0) {
        diagnostics += Diagnostic(
          range = optionsRange,
          severity = Some(DiagnosticSeverity.Error),
          code = Some(s"${DiagnosticCodePrefix}004"),
          source = Some("constellation-lang"),
          message = buildMessage(
            s"Invalid retry count: $retryCount (must be non-negative)",
            "The `retry` option specifies the number of retry attempts after the initial call fails. " +
            "It must be zero or positive.",
            Some("Use `retry: 0` for no retries, or `retry: N` for N retry attempts.")
          )
        )
      }
    }

    // Error: zero or negative concurrency
    options.concurrency.foreach { concurrency =>
      if (concurrency <= 0) {
        diagnostics += Diagnostic(
          range = optionsRange,
          severity = Some(DiagnosticSeverity.Error),
          code = Some(s"${DiagnosticCodePrefix}005"),
          source = Some("constellation-lang"),
          message = buildMessage(
            s"Invalid concurrency: $concurrency (must be positive)",
            "The `concurrency` option limits the number of parallel executions. " +
            "It must be at least 1.",
            Some("Use `concurrency: 1` for sequential execution, or `concurrency: N` for N parallel tasks.")
          )
        )
      }
    }

    // Warning: backoff without retry
    if (options.backoff.isDefined && options.retry.isEmpty) {
      diagnostics += Diagnostic(
        range = optionsRange,
        severity = Some(DiagnosticSeverity.Warning),
        code = Some(s"${DiagnosticCodePrefix}006"),
        source = Some("constellation-lang"),
        message = buildMessage(
          "Option `backoff` has no effect without `retry`",
          "The `backoff` strategy only applies when retrying failed calls. " +
          "Without `retry`, there are no retry attempts to apply backoff to.",
          Some("Add `retry: N` to enable retries with backoff.")
        )
      )
    }

    // Warning: cache_backend without cache
    if (options.cacheBackend.isDefined && options.cache.isEmpty) {
      diagnostics += Diagnostic(
        range = optionsRange,
        severity = Some(DiagnosticSeverity.Warning),
        code = Some(s"${DiagnosticCodePrefix}007"),
        source = Some("constellation-lang"),
        message = buildMessage(
          "Option `cache_backend` has no effect without `cache`",
          "The `cache_backend` option specifies which cache store to use. " +
          "Without `cache`, caching is disabled and the backend is ignored.",
          Some("Add `cache: <duration>` to enable caching.")
        )
      )
    }

    diagnostics.result()
  }

  /**
   * Build a formatted diagnostic message with explanation and suggestion.
   */
  private def buildMessage(
      summary: String,
      explanation: String,
      suggestion: Option[String]
  ): String = {
    val parts = List(summary, "", explanation) ++
      suggestion.map(s => List("", s"Suggestion: $s")).getOrElse(Nil)
    parts.mkString("\n")
  }

  // ========== Hover Support ==========

  /** Option documentation for hover */
  case class OptionDoc(
      name: String,
      signature: String,
      description: String,
      example: String,
      relatedOptions: List[String]
  )

  /** All option documentation */
  val optionDocs: Map[String, OptionDoc] = Map(
    "retry" -> OptionDoc(
      name = "retry",
      signature = "retry: Int",
      description = "Number of times to retry the module call after an initial failure. " +
        "Set to 0 for no retries (fail immediately on first error).",
      example = "result = FetchData(url) with retry: 3",
      relatedOptions = List("delay", "backoff", "fallback", "on_error")
    ),
    "timeout" -> OptionDoc(
      name = "timeout",
      signature = "timeout: Duration",
      description = "Maximum time to wait for the module to complete. " +
        "If exceeded, the call is cancelled and treated as a failure.",
      example = "result = SlowService(input) with timeout: 30s",
      relatedOptions = List("retry", "fallback")
    ),
    "delay" -> OptionDoc(
      name = "delay",
      signature = "delay: Duration",
      description = "Time to wait between retry attempts. " +
        "Combined with `backoff`, the actual delay may increase with each attempt.",
      example = "result = UnreliableAPI(data) with retry: 3, delay: 1s",
      relatedOptions = List("retry", "backoff")
    ),
    "backoff" -> OptionDoc(
      name = "backoff",
      signature = "backoff: fixed | linear | exponential",
      description = "Strategy for increasing delay between retry attempts.\n" +
        "- `fixed`: Same delay every time\n" +
        "- `linear`: Delay increases by the base delay each attempt (delay * attempt)\n" +
        "- `exponential`: Delay doubles each attempt (delay * 2^attempt)",
      example = "result = FlakeyService(req) with retry: 5, delay: 100ms, backoff: exponential",
      relatedOptions = List("retry", "delay")
    ),
    "fallback" -> OptionDoc(
      name = "fallback",
      signature = "fallback: Expression",
      description = "Default value to use if the module fails after all retries. " +
        "The fallback expression must have a type compatible with the module's return type.",
      example = "result = FetchConfig(key) with retry: 2, fallback: defaultConfig",
      relatedOptions = List("retry", "on_error")
    ),
    "cache" -> OptionDoc(
      name = "cache",
      signature = "cache: Duration",
      description = "Time to cache the result. Subsequent calls with the same inputs " +
        "within the TTL will return the cached value without re-executing the module.",
      example = "result = ExpensiveComputation(params) with cache: 5min",
      relatedOptions = List("cache_backend")
    ),
    "cache_backend" -> OptionDoc(
      name = "cache_backend",
      signature = "cache_backend: String",
      description = "Name of the cache backend to use. Available backends are configured " +
        "in the runtime settings. Common options: \"memory\", \"redis\", \"memcached\".",
      example = "result = FrequentLookup(id) with cache: 1h, cache_backend: \"redis\"",
      relatedOptions = List("cache")
    ),
    "throttle" -> OptionDoc(
      name = "throttle",
      signature = "throttle: Rate (count/duration)",
      description = "Rate limit for this module call. Prevents exceeding external API limits " +
        "or overloading downstream services.",
      example = "result = ExternalAPI(query) with throttle: 100/1min",
      relatedOptions = List("concurrency")
    ),
    "concurrency" -> OptionDoc(
      name = "concurrency",
      signature = "concurrency: Int",
      description = "Maximum number of parallel executions when processing collections. " +
        "Limits resource usage and prevents overwhelming external services.",
      example = "results = ProcessBatch(items) with concurrency: 5",
      relatedOptions = List("throttle")
    ),
    "on_error" -> OptionDoc(
      name = "on_error",
      signature = "on_error: propagate | skip | log | wrap",
      description = "How to handle errors after retries are exhausted.\n" +
        "- `propagate`: Re-throw the error to the caller (default)\n" +
        "- `skip`: Return None/empty and continue execution\n" +
        "- `log`: Log the error and return None/empty\n" +
        "- `wrap`: Wrap the error in an ErrorResult type",
      example = "result = OptionalData(id) with on_error: skip",
      relatedOptions = List("retry", "fallback")
    ),
    "lazy" -> OptionDoc(
      name = "lazy",
      signature = "lazy: Boolean",
      description = "If true, execution is deferred until the result is actually needed. " +
        "Useful for conditional execution or avoiding unnecessary computations.",
      example = "deferred = ExpensiveOp(data) with lazy: true",
      relatedOptions = List("priority")
    ),
    "priority" -> OptionDoc(
      name = "priority",
      signature = "priority: critical | high | normal | low | background",
      description = "Execution priority for scheduling.\n" +
        "- `critical`: Highest priority, execute immediately\n" +
        "- `high`: Execute before normal tasks\n" +
        "- `normal`: Default priority\n" +
        "- `low`: Execute after normal tasks\n" +
        "- `background`: Lowest priority, execute when idle",
      example = "audit = LogEvent(event) with priority: background",
      relatedOptions = List("lazy")
    )
  )

  /** Strategy value documentation */
  val strategyDocs: Map[String, (String, String)] = Map(
    // Backoff strategies
    "fixed" -> ("Backoff Strategy", "Constant delay between retries. Each retry waits the same base delay time."),
    "linear" -> ("Backoff Strategy", "Delay increases linearly: delay * attempt. First retry waits `delay`, second waits `2*delay`, etc."),
    "exponential" -> ("Backoff Strategy", "Delay doubles each retry: delay * 2^attempt. Recommended for network calls to avoid thundering herd."),

    // Error strategies
    "propagate" -> ("Error Strategy", "Re-throw the error to the caller. The pipeline will fail if this module fails."),
    "skip" -> ("Error Strategy", "Return None/empty and continue execution. Useful for optional data."),
    "log" -> ("Error Strategy", "Log the error and return None/empty. Combines visibility with continued execution."),
    "wrap" -> ("Error Strategy", "Wrap the error in an ErrorResult type. Allows the caller to inspect the error."),

    // Priority levels
    "critical" -> ("Priority Level", "Highest priority. Execute immediately with minimal queuing."),
    "high" -> ("Priority Level", "Above normal priority. Execute before normal tasks."),
    "normal" -> ("Priority Level", "Default priority level for most tasks."),
    "low" -> ("Priority Level", "Below normal priority. Yield to higher-priority tasks."),
    "background" -> ("Priority Level", "Lowest priority. Execute when no other tasks are waiting.")
  )

  /**
   * Get hover information for a word at a position in a `with` clause.
   *
   * @param word The word under the cursor
   * @param textBeforeCursor Text on the line before the cursor position
   * @return Hover information if the word is an option name or strategy value
   */
  def getHover(word: String, textBeforeCursor: String): Option[Hover] = {
    // Check if we're in a with clause context
    val isInWithClause = textBeforeCursor.contains("with ")

    if (!isInWithClause) {
      return None
    }

    // Check for option name
    optionDocs.get(word).map { doc =>
      val markdown = formatOptionHover(doc)
      Hover(contents = MarkupContent(kind = "markdown", value = markdown))
    }.orElse {
      // Check for strategy value
      strategyDocs.get(word).map { case (category, description) =>
        val markdown = formatStrategyHover(word, category, description)
        Hover(contents = MarkupContent(kind = "markdown", value = markdown))
      }
    }
  }

  /**
   * Format hover content for an option name.
   */
  private def formatOptionHover(doc: OptionDoc): String = {
    val related = if (doc.relatedOptions.nonEmpty) {
      s"\n\n**Related options:** ${doc.relatedOptions.map(o => s"`$o`").mkString(", ")}"
    } else ""

    s"""### `${doc.signature}`
       |
       |${doc.description}
       |
       |**Example:**
       |```constellation
       |${doc.example}
       |```$related
       |""".stripMargin
  }

  /**
   * Format hover content for a strategy value.
   */
  private def formatStrategyHover(value: String, category: String, description: String): String = {
    s"""### `$value` ($category)
       |
       |$description
       |""".stripMargin
  }
}
