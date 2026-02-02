package io.constellation

import io.circe.Json

/** Adaptive JSON converter that chooses the best strategy based on payload characteristics.
  *
  * ==Strategies==
  *
  *   - **Eager** (current): Best for small payloads (<10KB). Uses recursive descent with full
  *     materialization. Lowest overhead for small data.
  *
  *   - **Lazy**: Best for medium payloads (10KB-100KB) or when partial access is expected. Wraps
  *     JSON and defers conversion until values are accessed.
  *
  *   - **Streaming**: Best for large payloads (>100KB). Uses Jackson streaming parser for
  *     memory-efficient parsing without loading entire structure.
  *
  * ==Usage==
  *
  * {{{
  * val converter = AdaptiveJsonConverter()
  * val result = converter.convert(json, expectedType)
  *
  * // With explicit size hint (avoids re-estimation)
  * val result = converter.convert(json, expectedType, sizeHint = Some(50000))
  *
  * // With custom thresholds
  * val customConverter = AdaptiveJsonConverter(
  *   lazyThreshold = 5000,
  *   streamingThreshold = 50000
  * )
  * }}}
  *
  * ==Performance Characteristics==
  *
  * | Payload Size | Strategy  | Overhead | Memory    |
  * |:-------------|:----------|:---------|:----------|
  * | < 10KB       | Eager     | Minimal  | Full      |
  * | 10-100KB     | Lazy      | Low      | On-demand |
  * | > 100KB      | Streaming | Medium   | Constant  |
  */
class AdaptiveJsonConverter(
    val lazyThreshold: Int = 10_000,      // 10KB - use lazy above this
    val streamingThreshold: Int = 100_000 // 100KB - use streaming above this
) {

  private val streamingConverter = StreamingJsonConverter()

  /** The strategy selected for the last conversion (for diagnostics) */
  @volatile private var lastStrategy: ConversionStrategy = ConversionStrategy.Eager

  /** Convert JSON to CValue using the optimal strategy.
    *
    * @param json
    *   The JSON to convert
    * @param expectedType
    *   The expected CType
    * @param sizeHint
    *   Optional size hint in bytes (avoids re-estimation)
    * @return
    *   Either error message or converted CValue
    */
  def convert(
      json: Json,
      expectedType: CType,
      sizeHint: Option[Int] = None
  ): Either[String, CValue] = {
    val size = sizeHint.getOrElse(estimateSize(json))

    if size > streamingThreshold then {
      lastStrategy = ConversionStrategy.Streaming
      convertStreaming(json, expectedType)
    } else if size > lazyThreshold then {
      lastStrategy = ConversionStrategy.Lazy
      convertLazy(json, expectedType)
    } else {
      lastStrategy = ConversionStrategy.Eager
      JsonCValueConverter.jsonToCValue(json, expectedType)
    }
  }

  /** Convert using streaming strategy. Serializes JSON to bytes, then parses with Jackson
    * streaming.
    */
  private def convertStreaming(json: Json, expectedType: CType): Either[String, CValue] = {
    val bytes = json.noSpaces.getBytes("UTF-8")
    streamingConverter.streamToCValue(bytes, expectedType)
  }

  /** Convert using lazy strategy. Creates lazy wrappers that defer conversion.
    */
  private def convertLazy(json: Json, expectedType: CType): Either[String, CValue] =
    // For lazy conversion, we create a LazyJsonValue and immediately materialize
    // This gives us the benefit of lazy conversion for nested structures
    // In the future, we could return LazyCValue directly for truly deferred conversion
    LazyJsonValue(json, expectedType).materialize

  /** Estimate JSON size in bytes without full serialization. Uses structural heuristics for speed.
    */
  private def estimateSize(json: Json): Int =
    estimateSizeRecursive(json, 0)

  /** Recursive size estimation with depth limit to prevent stack overflow */
  private def estimateSizeRecursive(json: Json, depth: Int): Int =
    if depth > 50 then {
      // Assume large for deeply nested structures
      streamingThreshold + 1
    } else {
      json.fold(
        jsonNull = 4,
        jsonBoolean = _ => 5,
        jsonNumber = n => {
          // Numbers vary in size; use conservative estimate
          val s = n.toString
          if s.contains(".") then s.length + 1 else s.length
        },
        jsonString = s => s.length + 2, // +2 for quotes
        jsonArray = arr =>
          if arr.isEmpty then 2
          else {
            val elemEstimate = estimateSizeRecursive(arr.head, depth + 1)
            2 + arr.size * (elemEstimate + 1) // +1 for comma
          },
        jsonObject = obj =>
          if obj.isEmpty then 2
          else {
            // Estimate based on first few fields
            val sample = obj.toList.take(3)
            val avgFieldSize = if sample.nonEmpty then {
              sample.map { case (k, v) =>
                k.length + 4 + estimateSizeRecursive(v, depth + 1) // key + ": " + value
              }.sum / sample.size
            } else 10
            2 + obj.size * (avgFieldSize + 1)
          }
      )
    }

  /** Get the strategy used for the last conversion (for testing/diagnostics) */
  def getLastStrategy: ConversionStrategy = lastStrategy
}

/** Conversion strategy used by AdaptiveJsonConverter */
sealed trait ConversionStrategy
object ConversionStrategy {
  case object Eager     extends ConversionStrategy
  case object Lazy      extends ConversionStrategy
  case object Streaming extends ConversionStrategy
}

object AdaptiveJsonConverter {
  private lazy val defaultInstance = new AdaptiveJsonConverter()

  def apply(): AdaptiveJsonConverter = defaultInstance

  /** Create with custom thresholds */
  def apply(lazyThreshold: Int, streamingThreshold: Int): AdaptiveJsonConverter =
    new AdaptiveJsonConverter(lazyThreshold, streamingThreshold)

  /** Default thresholds */
  val DefaultLazyThreshold: Int      = 10_000
  val DefaultStreamingThreshold: Int = 100_000

  /** Convert using default instance */
  def convert(json: Json, expectedType: CType): Either[String, CValue] =
    defaultInstance.convert(json, expectedType)

  /** Convert with explicit size hint */
  def convert(json: Json, expectedType: CType, sizeHint: Int): Either[String, CValue] =
    defaultInstance.convert(json, expectedType, Some(sizeHint))
}
