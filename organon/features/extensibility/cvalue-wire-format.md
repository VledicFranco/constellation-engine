# CValue JSON Wire Format

> **Path**: `organon/features/extensibility/cvalue-wire-format.md`
> **Parent**: [extensibility/](./README.md)

The CValue JSON wire format defines the canonical JSON serialization for Constellation's type system values (`CValue`) and type descriptors (`CType`). Both the Scala server (Circe codecs) and TypeScript SDK must produce and consume identical JSON — this document is the contract.

## Design Principles

1. **Tagged unions** — Every CValue and CType is a JSON object with a `"tag"` discriminator field
2. **Self-describing** — Compound values carry their type metadata (e.g., `CList` includes `subtype`)
3. **Lossless round-trip** — `decode(encode(value)) === value` for all valid CValues
4. **Cross-language** — The same JSON is produced by Scala (Circe) and TypeScript, and either can deserialize the other's output

## CType Wire Format

CType represents the type of a value without the value itself.

### Primitive Types

```json
{ "tag": "CString" }
{ "tag": "CInt" }
{ "tag": "CFloat" }
{ "tag": "CBoolean" }
```

### Compound Types

**CList** — Homogeneous list with element type:
```json
{ "tag": "CList", "valuesType": { "tag": "CString" } }
```

**CMap** — Key-value map with typed keys and values:
```json
{
  "tag": "CMap",
  "keysType": { "tag": "CString" },
  "valuesType": { "tag": "CInt" }
}
```

**CProduct** — Named record / struct:
```json
{
  "tag": "CProduct",
  "structure": {
    "name": { "tag": "CString" },
    "age": { "tag": "CInt" }
  }
}
```

**CUnion** — Tagged union / variant:
```json
{
  "tag": "CUnion",
  "structure": {
    "text": { "tag": "CString" },
    "number": { "tag": "CInt" }
  }
}
```

**COptional** — Nullable wrapper:
```json
{ "tag": "COptional", "innerType": { "tag": "CString" } }
```

## CValue Wire Format

CValue represents a typed value. Every CValue carries its type metadata inline.

### Primitive Values

```json
{ "tag": "CString", "value": "hello" }
{ "tag": "CInt", "value": 42 }
{ "tag": "CFloat", "value": 3.14 }
{ "tag": "CBoolean", "value": true }
```

**Notes:**
- `CInt` values are JSON numbers (Scala `Long`, TypeScript `number`)
- `CFloat` values are JSON numbers (Scala `Double`, TypeScript `number`)
- No precision guarantees beyond IEEE 754 double (TypeScript limitation)

### CList

```json
{
  "tag": "CList",
  "value": [
    { "tag": "CString", "value": "a" },
    { "tag": "CString", "value": "b" }
  ],
  "subtype": { "tag": "CString" }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `value` | `CValue[]` | List elements, each a full CValue JSON |
| `subtype` | `CType` | Element type descriptor |

### CMap

```json
{
  "tag": "CMap",
  "value": [
    {
      "key": { "tag": "CString", "value": "name" },
      "value": { "tag": "CInt", "value": 1 }
    }
  ],
  "keysType": { "tag": "CString" },
  "valuesType": { "tag": "CInt" }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `value` | `{ key: CValue, value: CValue }[]` | Array of key-value pair objects |
| `keysType` | `CType` | Key type descriptor |
| `valuesType` | `CType` | Value type descriptor |

**Important:** Map entries are an **array of objects** with `key` and `value` fields, not a JSON object. This preserves non-string keys (e.g., `CInt` keys).

### CProduct

```json
{
  "tag": "CProduct",
  "value": {
    "name": { "tag": "CString", "value": "Alice" },
    "age": { "tag": "CInt", "value": 30 }
  },
  "structure": {
    "name": { "tag": "CString" },
    "age": { "tag": "CInt" }
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `value` | `Record<string, CValue>` | Field name → field value |
| `structure` | `Record<string, CType>` | Field name → field type |

### CUnion

```json
{
  "tag": "CUnion",
  "value": { "tag": "CString", "value": "hello" },
  "structure": {
    "text": { "tag": "CString" },
    "number": { "tag": "CInt" }
  },
  "unionTag": "text"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `value` | `CValue` | The active variant's value |
| `structure` | `Record<string, CType>` | All possible variants |
| `unionTag` | `string` | Which variant is active |

### COptional (CSome / CNone)

**Present value:**
```json
{
  "tag": "CSome",
  "value": { "tag": "CString", "value": "hello" },
  "innerType": { "tag": "CString" }
}
```

**Absent value:**
```json
{
  "tag": "CNone",
  "innerType": { "tag": "CString" }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `value` | `CValue` | The wrapped value (CSome only) |
| `innerType` | `CType` | The type of the optional value |

**Note:** Optional values use `CSome`/`CNone` tags, not `COptional`. `COptional` is a type-level concept only.

## CType ↔ TypeSchema Protobuf Mapping

The Module Provider Protocol uses protobuf `TypeSchema` messages for type declarations during registration. The `TypeSchemaConverter` translates between `CType` and `TypeSchema`.

| CType | TypeSchema.type | Protobuf Message |
|-------|----------------|------------------|
| `CString` | `primitive` | `PrimitiveType(STRING)` |
| `CInt` | `primitive` | `PrimitiveType(INT)` |
| `CFloat` | `primitive` | `PrimitiveType(FLOAT)` |
| `CBoolean` | `primitive` | `PrimitiveType(BOOL)` |
| `CProduct` | `record` | `RecordType(fields: Map[String, TypeSchema])` |
| `CList` | `list` | `ListType(elementType: TypeSchema)` |
| `CMap` | `map` | `MapType(keyType, valueType: TypeSchema)` |
| `CUnion` | `union` | `UnionType(variants: Seq[TypeSchema])` |
| `COptional` | `option` | `OptionType(innerType: TypeSchema)` |

**Union variant naming:** When converting `UnionType` from protobuf to `CType`, variants are synthetically named `variant0`, `variant1`, etc. (ordered by position in the protobuf sequence). Both the Scala and TypeScript converters follow this convention.

## Cross-Language Compatibility Invariants

1. **Tag strings are case-sensitive** — `"CString"` not `"cstring"`
2. **All fields are required** — Missing fields are errors, not defaults
3. **Map values are arrays** — Never JSON objects (preserves non-string keys)
4. **CInt maps to JSON number** — No string encoding for integers
5. **Empty containers are valid** — `CList` with `value: []`, `CProduct` with `value: {}`
6. **Type metadata is always present** — `CList.subtype`, `CMap.keysType`/`valuesType`, etc.
7. **Encoding is deterministic per SDK** — But field ordering may differ between Scala and TypeScript (JSON object key order is not guaranteed)

## Serialization Transport

For the Module Provider Protocol, CValues are serialized as **JSON-over-bytes**:

1. Encode `CValue` to JSON string using the wire format above
2. Convert JSON string to UTF-8 bytes
3. Send bytes in the `ExecuteRequest.input` / `ExecuteResponse.output` protobuf fields

Both Scala (`JsonCValueSerializer`) and TypeScript (`JsonCValueSerializer`) follow this pattern.

**Future:** The `CValueSerializer` trait allows swapping to a more efficient format (e.g., MessagePack) without changing callers.

## Implementation References

| Component | File | Language |
|-----------|------|----------|
| CValue/CType Circe codecs | `modules/runtime/.../CustomJsonCodecs.scala` | Scala |
| CValue byte serializer | `modules/module-provider-sdk/.../CValueSerializer.scala` | Scala |
| CType ↔ TypeSchema converter | `modules/module-provider-sdk/.../TypeSchemaConverter.scala` | Scala |
| CValue JSON encoder/decoder | `sdks/typescript/src/serialization/cvalue-serializer.ts` | TypeScript |
| CType ↔ TypeSchema converter | `sdks/typescript/src/serialization/type-schema-converter.ts` | TypeScript |

## Related

- [module-provider.md](./module-provider.md) — Protocol overview
- [control-plane.md](./control-plane.md) — Connection lifecycle
- [Component: module-provider](../../components/module-provider/) — Server implementation
- [Component: typescript-sdk](../../components/typescript-sdk/) — TypeScript SDK
