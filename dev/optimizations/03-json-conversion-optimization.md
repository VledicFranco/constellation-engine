# Optimization 03: JSON Conversion Optimization

**Priority:** 3 (High Impact)
**Expected Gain:** 10-50ms for large payloads
**Complexity:** Medium-High
**Status:** Not Implemented

---

## Problem Statement

Every request requires bidirectional JSON conversion:

1. **Input:** JSON → CValue (for DAG inputs)
2. **Output:** CValue → JSON (for DAG outputs)

The current implementation uses recursive descent with full materialization, which is inefficient for large payloads common in ML pipelines (embeddings, feature vectors, batch data).

### Current Implementation

```scala
// JsonCValueConverter.scala - Simplified view

def jsonToCValue(json: Json, expectedType: CType): Either[ConversionError, CValue] = {
  (json, expectedType) match {
    case (Json.Str(s), CString) => Right(CStringValue(s))
    case (Json.Num(n), CInt)    => Right(CIntValue(n.toInt))
    case (Json.Arr(arr), CList(elemType)) =>
      arr.traverse(jsonToCValue(_, elemType)).map(CListValue(_, elemType))  // Full traversal
    case (Json.Obj(fields), CProduct(fieldTypes)) =>
      fieldTypes.toList.traverse { case (name, ctype) =>
        fields(name).flatMap(jsonToCValue(_, ctype))  // Recursive descent
      }.map(CProductValue(_))
    // ... more cases
  }
}
```

**Problems:**
- Full traversal of arrays even if only partial data is needed
- Recursive calls for nested structures
- Intermediate `List` allocations during conversion
- No streaming for large payloads

---

## Proposed Solutions

### Solution A: Lazy CValue Conversion

Only convert values when they're actually accessed by modules.

```scala
// New: Lazy CValue wrapper

sealed trait LazyCValue {
  def materialize: CValue
  def cType: CType
}

case class LazyJsonValue(
  json: Json,
  expectedType: CType,
  private var cached: Option[CValue] = None
) extends LazyCValue {

  def materialize: CValue = cached.getOrElse {
    val converted = JsonCValueConverter.convert(json, expectedType)
    cached = Some(converted)
    converted
  }

  def cType: CType = expectedType
}

// For lists: only convert accessed elements
case class LazyListValue(
  jsonArray: Vector[Json],
  elementType: CType,
  private val cache: mutable.Map[Int, CValue] = mutable.Map.empty
) extends LazyCValue {

  def get(index: Int): CValue = cache.getOrElseUpdate(index, {
    JsonCValueConverter.convert(jsonArray(index), elementType)
  })

  def materialize: CValue = CListValue(
    jsonArray.indices.map(get).toList,
    elementType
  )

  def cType: CType = CList(elementType)
}
```

### Solution B: Streaming JSON Parser

Use Jackson Streaming API for large payloads:

```scala
// New file: modules/runtime/.../StreamingJsonConverter.scala

import com.fasterxml.jackson.core.{JsonFactory, JsonParser, JsonToken}

class StreamingJsonConverter {
  private val factory = new JsonFactory()

  def streamToCValue(
    input: InputStream,
    expectedType: CType
  ): IO[CValue] = IO.blocking {
    val parser = factory.createParser(input)
    parseValue(parser, expectedType)
  }

  private def parseValue(parser: JsonParser, expectedType: CType): CValue = {
    val token = parser.nextToken()

    (token, expectedType) match {
      case (JsonToken.VALUE_STRING, CString) =>
        CStringValue(parser.getValueAsString)

      case (JsonToken.VALUE_NUMBER_INT, CInt) =>
        CIntValue(parser.getValueAsInt)

      case (JsonToken.START_ARRAY, CList(elemType)) =>
        parseArray(parser, elemType)

      case (JsonToken.START_OBJECT, CProduct(fields)) =>
        parseObject(parser, fields)

      case _ =>
        throw new ConversionException(s"Unexpected token $token for type $expectedType")
    }
  }

  private def parseArray(parser: JsonParser, elemType: CType): CListValue = {
    val builder = List.newBuilder[CValue]

    while (parser.nextToken() != JsonToken.END_ARRAY) {
      // Don't call nextToken again - parseValue expects current position
      builder += parseValueAtCurrentPosition(parser, elemType)
    }

    CListValue(builder.result(), elemType)
  }

  private def parseObject(parser: JsonParser, fields: Map[String, CType]): CProductValue = {
    val values = mutable.Map[String, CValue]()

    while (parser.nextToken() != JsonToken.END_OBJECT) {
      val fieldName = parser.getCurrentName
      fields.get(fieldName) match {
        case Some(fieldType) =>
          parser.nextToken() // Move to value
          values(fieldName) = parseValueAtCurrentPosition(parser, fieldType)
        case None =>
          parser.nextToken()
          parser.skipChildren() // Skip unknown fields
      }
    }

    CProductValue(values.toMap)
  }
}
```

### Solution C: Binary Protocol (Highest Performance)

For internal/high-throughput use, bypass JSON entirely:

```scala
// Using Protocol Buffers or MessagePack

// Define schema
message CValueProto {
  oneof value {
    string string_value = 1;
    int64 int_value = 2;
    double float_value = 3;
    bool bool_value = 4;
    ListValue list_value = 5;
    MapValue map_value = 6;
    ProductValue product_value = 7;
  }
}

// Scala integration
class ProtobufCValueConverter {
  def fromProto(proto: CValueProto, expectedType: CType): CValue = {
    proto.value match {
      case CValueProto.Value.StringValue(s) => CStringValue(s)
      case CValueProto.Value.IntValue(i)    => CIntValue(i.toInt)
      case CValueProto.Value.ListValue(lv)  =>
        CListValue(lv.elements.map(fromProto(_, elementType)), elementType)
      // ... etc
    }
  }

  def toProto(cvalue: CValue): CValueProto = {
    cvalue match {
      case CStringValue(s) => CValueProto(CValueProto.Value.StringValue(s))
      case CIntValue(i)    => CValueProto(CValueProto.Value.IntValue(i))
      // ... etc
    }
  }
}
```

---

## Hybrid Approach (Recommended)

Combine solutions based on payload size:

```scala
class AdaptiveJsonConverter(
  streamingThreshold: Int = 100_000,  // 100KB
  lazyThreshold: Int = 10_000         // 10KB
) {

  def convert(json: Json, expectedType: CType): CValue = {
    val size = estimateSize(json)

    if (size > streamingThreshold) {
      // Large payload: use streaming
      streamingConverter.convert(json.noSpaces.getBytes, expectedType)
    } else if (size > lazyThreshold) {
      // Medium payload: use lazy conversion
      LazyJsonValue(json, expectedType)
    } else {
      // Small payload: eager conversion is fine
      eagerConverter.convert(json, expectedType)
    }
  }

  private def estimateSize(json: Json): Int = {
    // Quick heuristic based on structure
    json.fold(
      jsonNull = 4,
      jsonBool = _ => 5,
      jsonNumber = _ => 8,
      jsonString = _.length,
      jsonArray = arr => arr.size * 20,  // Estimate
      jsonObject = obj => obj.size * 50   // Estimate
    )
  }
}
```

---

## Output Optimization

### Current Output Flow

```scala
// All results collected, then converted
val results: Map[String, CValue] = dagExecution.run()
val jsonOutput: Json = results.map { case (k, v) => k -> cvalueToJson(v) }
```

### Streaming Output

```scala
// Stream results as they become available
def runStreaming(dagSpec: DagSpec, inputs: ...): fs2.Stream[IO, (String, Json)] = {
  fs2.Stream.evals(outputNodes.map { node =>
    runtime.awaitDataNode(node.id).map { cvalue =>
      node.name -> cvalueToJson(cvalue)
    }
  })
}
```

---

## Benchmarks

### Test Data

```scala
// Small: 1KB JSON (typical API request)
val small = Json.obj("text" -> Json.fromString("hello" * 200))

// Medium: 100KB JSON (feature vector)
val medium = Json.obj("features" -> Json.arr(List.fill(10000)(Json.fromDouble(0.5)): _*))

// Large: 10MB JSON (batch embeddings)
val large = Json.obj("embeddings" -> Json.arr(
  List.fill(1000)(Json.arr(List.fill(1536)(Json.fromDouble(0.1)): _*)): _*
))
```

### Expected Results

| Payload | Current | Streaming | Lazy | Binary |
|---------|---------|-----------|------|--------|
| 1KB | 0.5ms | 0.6ms | 0.3ms | 0.1ms |
| 100KB | 15ms | 8ms | 2ms* | 1ms |
| 10MB | 500ms | 150ms | 10ms* | 20ms |

*Lazy timing is for initial parse only; full materialization takes longer

---

## Memory Impact

### Current (Eager)

```
Input JSON (10MB) → CValue tree (15MB) → Scala values (10MB)
Peak memory: 35MB per request
```

### With Streaming

```
Input JSON (streamed) → CValue tree (15MB) → Scala values (10MB)
Peak memory: 25MB per request
```

### With Lazy

```
Input JSON (10MB, retained) → Lazy refs (1KB) → Materialized on access
Peak memory: 10MB + accessed values only
```

---

## Implementation Checklist

- [ ] Implement `LazyJsonValue` and `LazyListValue`
- [ ] Implement `StreamingJsonConverter` with Jackson
- [ ] Create `AdaptiveJsonConverter` with size thresholds
- [ ] Add configuration for thresholds
- [ ] Optional: Add Protocol Buffers support for binary format
- [ ] Update `ConstellationRoutes` to use adaptive converter
- [ ] Benchmark with realistic ML payloads
- [ ] Add memory profiling tests

---

## Files to Modify

| File | Changes |
|------|---------|
| `modules/runtime/.../JsonCValueConverter.scala` | Add lazy/streaming modes |
| New: `modules/runtime/.../StreamingJsonConverter.scala` | Jackson streaming impl |
| New: `modules/runtime/.../LazyValue.scala` | Lazy value wrappers |
| `modules/http-api/.../ConstellationRoutes.scala` | Use adaptive converter |
| `build.sbt` | Add Jackson dependency if not present |

---

## Dependencies

```scala
// build.sbt additions (if needed)
libraryDependencies ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-core" % "2.15.2",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.15.2"
)

// Optional: For binary protocol
libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime" % "0.11.13"
```

---

## Related Optimizations

- [CValue Type Metadata Stripping](./06-cvalue-type-metadata.md) - Reduces CValue memory overhead
- [Result Streaming](./08-result-streaming.md) - Complements streaming JSON output
