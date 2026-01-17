package io.constellation.examples.app.modules

import io.constellation._
import cats.effect.IO

/** Custom data processing modules for the example application
  *
  * Demonstrates numeric and list processing capabilities.
  */
object DataModules {

  // ========== List Operations ==========

  /** Sum a list of numbers */
  case class SumInput(numbers: List[Long])
  case class SumOutput(total: Long)

  val sumList: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "SumList",
      description = "Calculates the sum of all integers in a list. Returns 0 for an empty list.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("data", "aggregation")
    .implementationPure[SumInput, SumOutput] { input =>
      SumOutput(input.numbers.sum)
    }
    .build

  /** Calculate average of a list */
  case class AverageInput(numbers: List[Long])
  case class AverageOutput(average: Double)

  val average: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "Average",
      description = "Calculates the arithmetic mean of a list of numbers. Returns 0.0 for an empty list.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("data", "statistics")
    .implementationPure[AverageInput, AverageOutput] { input =>
      if (input.numbers.isEmpty) {
        AverageOutput(0.0)
      } else {
        AverageOutput(input.numbers.sum.toDouble / input.numbers.length)
      }
    }
    .build

  /** Find maximum value in a list */
  case class MaxInput(numbers: List[Long])
  case class MaxOutput(max: Long)

  val max: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "Max",
      description = "Finds the maximum (largest) value in a list of integers. Returns 0 for an empty list.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("data", "statistics")
    .implementationPure[MaxInput, MaxOutput] { input =>
      MaxOutput(if (input.numbers.isEmpty) 0L else input.numbers.max)
    }
    .build

  /** Find minimum value in a list */
  case class MinInput(numbers: List[Long])
  case class MinOutput(min: Long)

  val min: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "Min",
      description = "Finds the minimum (smallest) value in a list of integers. Returns 0 for an empty list.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("data", "statistics")
    .implementationPure[MinInput, MinOutput] { input =>
      MinOutput(if (input.numbers.isEmpty) 0L else input.numbers.min)
    }
    .build

  // ========== List Transformations ==========

  /** Filter list by threshold */
  case class FilterInput(numbers: List[Long], threshold: Long)
  case class FilterOutput(filtered: List[Long])

  val filterGreaterThan: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "FilterGreaterThan",
      description = "Filters a list to keep only numbers greater than the specified threshold value. Returns only values strictly greater than the threshold.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("data", "filter")
    .implementationPure[FilterInput, FilterOutput] { input =>
      FilterOutput(input.numbers.filter(_ > input.threshold))
    }
    .build

  /** Map: multiply each element */
  case class MultiplyEachInput(numbers: List[Long], multiplier: Long)
  case class MultiplyEachOutput(result: List[Long])

  val multiplyEach: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "MultiplyEach",
      description = "Multiplies each number in a list by a constant multiplier. Returns a new list with the transformed values.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("data", "transform")
    .implementationPure[MultiplyEachInput, MultiplyEachOutput] { input =>
      MultiplyEachOutput(input.numbers.map(_ * input.multiplier))
    }
    .build

  // ========== Data Generators ==========

  /** Generate a range of numbers */
  case class RangeInput(start: Long, end: Long)
  case class RangeOutput(numbers: List[Long])

  val range: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "Range",
      description = "Generates a list of consecutive integers from start to end (inclusive). Useful for iteration and sequence generation.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("data", "generator")
    .implementationPure[RangeInput, RangeOutput] { input =>
      RangeOutput((input.start to input.end).toList)
    }
    .build

  // ========== Format Converters ==========

  /** Format number with commas */
  case class FormatNumberInput(number: Long)
  case class FormatNumberOutput(formatted: String)

  val formatNumber: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "FormatNumber",
      description = "Formats a number with thousand separators (commas) for improved readability. Example: 1000000 becomes 1,000,000.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("data", "format")
    .implementationPure[FormatNumberInput, FormatNumberOutput] { input =>
      val formatter = java.text.NumberFormat.getIntegerInstance
      FormatNumberOutput(formatter.format(input.number))
    }
    .build

  // ========== All Modules ==========

  /** All data processing modules */
  val all: List[Module.Uninitialized] = List(
    sumList,
    average,
    max,
    min,
    filterGreaterThan,
    multiplyEach,
    range,
    formatNumber
  )
}
