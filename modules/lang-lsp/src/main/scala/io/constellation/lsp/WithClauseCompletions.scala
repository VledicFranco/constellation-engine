package io.constellation.lsp

import io.constellation.lsp.protocol.LspTypes.{CompletionItem, CompletionItemKind}

/** Completion support for the `with` clause in module calls.
  *
  * Provides context-aware completions for:
  *   - `with` keyword after module calls
  *   - Option names (retry, timeout, cache, etc.)
  *   - Option values (backoff strategies, error strategies, priority levels)
  *   - Duration units (ms, s, min, h, d)
  *
  * Uses text-based heuristics for context detection, enabling completions even before the parser
  * fully supports the `with` clause syntax.
  */
object WithClauseCompletions {

  /** Context detected from analyzing text before cursor */
  sealed trait WithClauseContext
  case object AfterModuleCall                        extends WithClauseContext
  case object AfterWith                              extends WithClauseContext
  case class AfterComma(usedOptions: Set[String])    extends WithClauseContext
  case object AfterBackoffColon                      extends WithClauseContext
  case object AfterOnErrorColon                      extends WithClauseContext
  case object AfterPriorityColon                     extends WithClauseContext
  case class AfterCacheBackendColon(partial: String) extends WithClauseContext
  case class AfterNumber(number: String)             extends WithClauseContext
  case object NotInWithClause                        extends WithClauseContext

  /** Definition of a module call option */
  case class OptionDef(
      name: String,
      valueType: String,
      description: String,
      placeholder: String,
      documentation: String
  )

  /** All available module call options */
  val allOptions: List[OptionDef] = List(
    OptionDef(
      "retry",
      "Integer",
      "Maximum retry attempts",
      "3",
      "Number of times to retry the module call on failure. Set to 0 for no retries."
    ),
    OptionDef(
      "timeout",
      "Duration",
      "Maximum execution time",
      "30s",
      "Maximum time to wait for the module to complete. Supports: ms, s, min, h, d."
    ),
    OptionDef(
      "delay",
      "Duration",
      "Delay between retries",
      "1s",
      "Time to wait between retry attempts. Use with `backoff` for exponential delays."
    ),
    OptionDef(
      "backoff",
      "Strategy",
      "Backoff strategy for retries",
      "exponential",
      "How delay increases between retries: fixed (constant), linear (additive), exponential (multiplicative)."
    ),
    OptionDef(
      "fallback",
      "Expression",
      "Default value on failure",
      "defaultValue",
      "Value to use if the module fails after all retries. Can be a literal or variable reference."
    ),
    OptionDef(
      "cache",
      "Duration",
      "Cache TTL",
      "5min",
      "How long to cache the result. Subsequent calls with same inputs return cached value."
    ),
    OptionDef(
      "cache_backend",
      "String",
      "Cache backend name",
      "\"redis\"",
      "Name of the cache backend to use. Configure backends in runtime settings."
    ),
    OptionDef(
      "throttle",
      "Rate",
      "Rate limit",
      "100/1min",
      "Maximum calls per time period. Format: count/duration (e.g., 100/1min)."
    ),
    OptionDef(
      "concurrency",
      "Integer",
      "Max parallel executions",
      "5",
      "Maximum number of concurrent executions when processing lists/batches."
    ),
    OptionDef(
      "on_error",
      "Strategy",
      "Error handling strategy",
      "skip",
      "How to handle errors: propagate (re-throw), skip (return None), log (log and skip), wrap (wrap in error type)."
    ),
    OptionDef(
      "lazy",
      "Boolean",
      "Defer execution",
      "true",
      "If true, execution is deferred until the result is actually needed."
    ),
    OptionDef(
      "priority",
      "Level",
      "Execution priority",
      "normal",
      "Execution priority for scheduling: critical, high, normal, low, background."
    )
  )

  /** Backoff strategy values */
  val backoffStrategies: List[(String, String)] = List(
    ("fixed", "Constant delay between retries"),
    ("linear", "Delay increases linearly (delay * attempt)"),
    ("exponential", "Delay doubles with each retry (delay * 2^attempt)")
  )

  /** Error handling strategy values */
  val onErrorStrategies: List[(String, String)] = List(
    ("propagate", "Re-throw the error to the caller"),
    ("skip", "Return None/empty and continue"),
    ("log", "Log the error and return None/empty"),
    ("wrap", "Wrap the error in a result type")
  )

  /** Priority level values */
  val priorityLevels: List[(String, String)] = List(
    ("critical", "Highest priority - execute immediately"),
    ("high", "High priority - execute before normal tasks"),
    ("normal", "Default priority level"),
    ("low", "Low priority - execute after normal tasks"),
    ("background", "Lowest priority - execute when idle")
  )

  /** Duration unit values */
  val durationUnits: List[(String, String)] = List(
    ("ms", "Milliseconds"),
    ("s", "Seconds"),
    ("min", "Minutes"),
    ("h", "Hours"),
    ("d", "Days")
  )

  /** Analyze the text before cursor to determine completion context.
    *
    * @param textBeforeCursor
    *   The text on the current line, up to the cursor position
    * @param fullLineText
    *   The complete line text (for multi-line context if needed)
    * @return
    *   The detected WithClauseContext
    */
  def analyzeContext(textBeforeCursor: String, fullLineText: String): WithClauseContext = {
    // Use original text for patterns that check trailing characters
    // Use trimmed for patterns that don't depend on trailing whitespace
    val trimmed = textBeforeCursor.trim

    // Check for specific option value contexts first (most specific patterns)
    // These end with colon followed by optional whitespace - use original text
    if textBeforeCursor.matches(""".*\bbackoff:\s*$""") then {
      return AfterBackoffColon
    }
    if textBeforeCursor.matches(""".*\bon_error:\s*$""") then {
      return AfterOnErrorColon
    }
    if textBeforeCursor.matches(""".*\bpriority:\s*$""") then {
      return AfterPriorityColon
    }
    if textBeforeCursor.matches(""".*\bcache_backend:\s*"?(\w*)$""") then {
      val partial = """cache_backend:\s*"?(\w*)$""".r
        .findFirstMatchIn(textBeforeCursor)
        .map(_.group(1))
        .getOrElse("")
      return AfterCacheBackendColon(partial)
    }

    // Check for number context (for duration units)
    // Pattern: after timeout:, delay:, cache:, or similar followed by a number and space
    val numberPattern = """.*\b(timeout|delay|cache):\s*(\d+)\s*$""".r
    numberPattern.findFirstMatchIn(textBeforeCursor) match {
      case Some(m) => return AfterNumber(m.group(2))
      case None    => // continue
    }

    // Check for "with" followed by space (suggest option names)
    // Use original text to preserve trailing space
    if textBeforeCursor.matches(""".*\bwith\s+$""") then {
      return AfterWith
    }

    // Check for comma after an option value (suggest remaining options)
    // Pattern: `with option: value, ` or `with option: value,`
    if textBeforeCursor.matches(""".*\bwith\s+.+,\s*$""") then {
      val usedOptions = extractUsedOptions(textBeforeCursor)
      return AfterComma(usedOptions)
    }

    // Check for module call pattern: `Identifier(...)` possibly followed by whitespace
    // This should suggest "with" keyword
    if trimmed.matches(""".*[A-Z]\w*\s*\([^)]*\)\s*$""") then {
      return AfterModuleCall
    }

    NotInWithClause
  }

  /** Extract option names that have already been used in the with clause.
    */
  private def extractUsedOptions(text: String): Set[String] = {
    val optionPattern = """\b(\w+)\s*:""".r
    val withIdx       = text.lastIndexOf("with")
    if withIdx < 0 then return Set.empty

    val withClauseText = text.substring(withIdx)
    optionPattern.findAllMatchIn(withClauseText).map(_.group(1)).toSet
  }

  /** Get completion items for the given context.
    */
  def getCompletions(context: WithClauseContext): List[CompletionItem] = context match {
    case AfterModuleCall =>
      List(
        CompletionItem(
          label = "with",
          kind = Some(CompletionItemKind.Keyword),
          detail = Some("Add module call options"),
          documentation = Some(
            "Configure retry, timeout, cache, and other execution options for this module call."
          ),
          insertText = Some("with "),
          filterText = Some("with"),
          sortText = Some("0_with") // Sort first
        )
      )

    case AfterWith =>
      buildOptionCompletions(Set.empty)

    case AfterComma(usedOptions) =>
      buildOptionCompletions(usedOptions)

    case AfterBackoffColon =>
      backoffStrategies.map { case (name, desc) =>
        CompletionItem(
          label = name,
          kind = Some(CompletionItemKind.Field),
          detail = Some("Backoff strategy"),
          documentation = Some(desc),
          insertText = Some(name),
          filterText = Some(name),
          sortText = Some(name)
        )
      }

    case AfterOnErrorColon =>
      onErrorStrategies.map { case (name, desc) =>
        CompletionItem(
          label = name,
          kind = Some(CompletionItemKind.Field),
          detail = Some("Error handling strategy"),
          documentation = Some(desc),
          insertText = Some(name),
          filterText = Some(name),
          sortText = Some(name)
        )
      }

    case AfterPriorityColon =>
      priorityLevels.map { case (name, desc) =>
        CompletionItem(
          label = name,
          kind = Some(CompletionItemKind.Field),
          detail = Some("Priority level"),
          documentation = Some(desc),
          insertText = Some(name),
          filterText = Some(name),
          sortText = Some(name)
        )
      }

    case AfterCacheBackendColon(_) =>
      // Suggest common cache backends
      List("memory", "redis", "memcached").map { backend =>
        CompletionItem(
          label = s"\"$backend\"",
          kind = Some(CompletionItemKind.Text),
          detail = Some("Cache backend"),
          documentation = Some(s"Use $backend as cache backend"),
          insertText = Some(s"\"$backend\""),
          filterText = Some(backend),
          sortText = Some(backend)
        )
      }

    case AfterNumber(number) =>
      durationUnits.map { case (unit, desc) =>
        CompletionItem(
          label = unit,
          kind = Some(CompletionItemKind.Text),
          detail = Some(desc),
          documentation = Some(s"$number $desc"),
          insertText = Some(unit),
          filterText = Some(unit),
          sortText = Some(unit)
        )
      }

    case NotInWithClause =>
      List.empty
  }

  /** Build completion items for option names, excluding already-used options.
    */
  private def buildOptionCompletions(usedOptions: Set[String]): List[CompletionItem] =
    allOptions
      .filterNot(opt => usedOptions.contains(opt.name))
      .map { opt =>
        CompletionItem(
          label = opt.name,
          kind = Some(CompletionItemKind.Property),
          detail = Some(s"${opt.valueType} - ${opt.description}"),
          documentation = Some(opt.documentation),
          insertText = Some(s"${opt.name}: ${opt.placeholder}"),
          filterText = Some(opt.name),
          sortText = Some(opt.name)
        )
      }
}
