package io.constellation.examples.app.modules

import io.constellation._
import cats.effect.IO

/** Custom text processing modules for the example application
  *
  * This demonstrates how users can create their own domain-specific
  * modules using the ModuleBuilder API.
  */
object TextModules {

  // ========== Text Transformers ==========

  case class TextInput(text: String)
  case class TextOutput(result: String)

  /** Convert text to uppercase */
  val uppercase: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "Uppercase",
      description = "Converts text to uppercase",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("text", "transform")
    .implementationPure[TextInput, TextOutput] { input =>
      TextOutput(input.text.toUpperCase)
    }
    .build

  /** Convert text to lowercase */
  val lowercase: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "Lowercase",
      description = "Converts text to lowercase",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("text", "transform")
    .implementationPure[TextInput, TextOutput] { input =>
      TextOutput(input.text.toLowerCase)
    }
    .build

  /** Trim whitespace from text */
  val trim: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "Trim",
      description = "Removes leading and trailing whitespace",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("text", "transform")
    .implementationPure[TextInput, TextOutput] { input =>
      TextOutput(input.text.trim)
    }
    .build

  /** Replace text */
  case class ReplaceInput(text: String, find: String, replace: String)
  case class ReplaceOutput(result: String)

  val replace: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "Replace",
      description = "Replaces all occurrences of a string",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("text", "transform")
    .implementationPure[ReplaceInput, ReplaceOutput] { input =>
      ReplaceOutput(input.text.replace(input.find, input.replace))
    }
    .build

  // ========== Text Analyzers ==========

  /** Count words in text */
  case class WordCountInput(text: String)
  case class WordCountOutput(count: Long)

  val wordCount: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "WordCount",
      description = "Counts the number of words in text",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("text", "analysis")
    .implementationPure[WordCountInput, WordCountOutput] { input =>
      val words = input.text.split("\\s+").filter(_.nonEmpty)
      WordCountOutput(words.length.toLong)
    }
    .build

  /** Calculate text length */
  case class LengthInput(text: String)
  case class LengthOutput(length: Long)

  val textLength: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "TextLength",
      description = "Returns the length of the text",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("text", "analysis")
    .implementationPure[LengthInput, LengthOutput] { input =>
      LengthOutput(input.text.length.toLong)
    }
    .build

  /** Check if text contains a substring */
  case class ContainsInput(text: String, substring: String)
  case class ContainsOutput(contains: Boolean)

  val contains: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "Contains",
      description = "Checks if text contains a substring",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("text", "analysis")
    .implementationPure[ContainsInput, ContainsOutput] { input =>
      ContainsOutput(input.text.contains(input.substring))
    }
    .build

  // ========== Text Splitters ==========

  /** Split text into lines */
  case class SplitLinesInput(text: String)
  case class SplitLinesOutput(lines: List[String])

  val splitLines: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "SplitLines",
      description = "Splits text into lines",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("text", "split")
    .implementationPure[SplitLinesInput, SplitLinesOutput] { input =>
      SplitLinesOutput(input.text.split("\n").toList)
    }
    .build

  /** Split text by delimiter */
  case class SplitInput(text: String, delimiter: String)
  case class SplitOutput(parts: List[String])

  val split: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "Split",
      description = "Splits text by a delimiter",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("text", "split")
    .implementationPure[SplitInput, SplitOutput] { input =>
      SplitOutput(input.text.split(input.delimiter).toList)
    }
    .build

  // ========== All Modules ==========

  /** All text processing modules */
  val all: List[Module.Uninitialized] = List(
    uppercase,
    lowercase,
    trim,
    replace,
    wordCount,
    textLength,
    contains,
    splitLines,
    split
  )
}
