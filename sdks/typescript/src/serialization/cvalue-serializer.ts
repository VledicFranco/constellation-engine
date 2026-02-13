/**
 * CValue serialization to/from bytes.
 *
 * v1 uses JSON-over-bytes, wire-compatible with the Scala Circe codecs in
 * `io.constellation.json.CustomJsonCodecs`. The JSON format uses tagged unions
 * with a "tag" discriminator field.
 */

import type { CType } from "../types/ctype.js";
import type { CValue } from "../types/cvalue.js";

// ===== Serializer interface =====

export interface CValueSerializer {
  serialize(value: CValue): Uint8Array;
  deserialize(bytes: Uint8Array): CValue;
}

// ===== JSON wire format encoder/decoder =====

/** Encode a CType to its JSON wire representation (matching Scala Circe codecs). */
export function encodeCType(ctype: CType): unknown {
  switch (ctype.tag) {
    case "CString":
      return { tag: "CString" };
    case "CInt":
      return { tag: "CInt" };
    case "CFloat":
      return { tag: "CFloat" };
    case "CBoolean":
      return { tag: "CBoolean" };
    case "CList":
      return { tag: "CList", valuesType: encodeCType(ctype.valuesType) };
    case "CMap":
      return {
        tag: "CMap",
        keysType: encodeCType(ctype.keysType),
        valuesType: encodeCType(ctype.valuesType),
      };
    case "CProduct": {
      const structure: Record<string, unknown> = {};
      for (const [k, v] of Object.entries(ctype.structure)) {
        structure[k] = encodeCType(v);
      }
      return { tag: "CProduct", structure };
    }
    case "CUnion": {
      const structure: Record<string, unknown> = {};
      for (const [k, v] of Object.entries(ctype.structure)) {
        structure[k] = encodeCType(v);
      }
      return { tag: "CUnion", structure };
    }
    case "COptional":
      return { tag: "COptional", innerType: encodeCType(ctype.innerType) };
  }
}

/** Decode a CType from its JSON wire representation. */
export function decodeCType(json: unknown): CType {
  if (typeof json !== "object" || json === null || !("tag" in json)) {
    throw new Error(`Invalid CType JSON: expected object with "tag" field`);
  }
  const obj = json as Record<string, unknown>;
  const tag = obj.tag as string;

  switch (tag) {
    case "CString":
      return { tag: "CString" };
    case "CInt":
      return { tag: "CInt" };
    case "CFloat":
      return { tag: "CFloat" };
    case "CBoolean":
      return { tag: "CBoolean" };
    case "CList":
      if (obj.valuesType == null) throw new Error(`CList missing "valuesType" field`);
      return { tag: "CList", valuesType: decodeCType(obj.valuesType) };
    case "CMap":
      if (obj.keysType == null) throw new Error(`CMap missing "keysType" field`);
      if (obj.valuesType == null) throw new Error(`CMap missing "valuesType" field`);
      return {
        tag: "CMap",
        keysType: decodeCType(obj.keysType),
        valuesType: decodeCType(obj.valuesType),
      };
    case "CProduct": {
      if (obj.structure == null || typeof obj.structure !== "object") {
        throw new Error(`CProduct missing "structure" field`);
      }
      const structure: Record<string, CType> = {};
      const raw = obj.structure as Record<string, unknown>;
      for (const [k, v] of Object.entries(raw)) {
        structure[k] = decodeCType(v);
      }
      return { tag: "CProduct", structure };
    }
    case "CUnion": {
      if (obj.structure == null || typeof obj.structure !== "object") {
        throw new Error(`CUnion missing "structure" field`);
      }
      const structure: Record<string, CType> = {};
      const raw = obj.structure as Record<string, unknown>;
      for (const [k, v] of Object.entries(raw)) {
        structure[k] = decodeCType(v);
      }
      return { tag: "CUnion", structure };
    }
    case "COptional":
      if (obj.innerType == null) throw new Error(`COptional missing "innerType" field`);
      return { tag: "COptional", innerType: decodeCType(obj.innerType) };
    default:
      throw new Error(`Unknown CType tag: ${tag}`);
  }
}

/** Encode a CValue to its JSON wire representation (matching Scala Circe codecs). */
export function encodeCValue(cvalue: CValue): unknown {
  switch (cvalue.tag) {
    case "CString":
      return { tag: "CString", value: cvalue.value };
    case "CInt":
      return { tag: "CInt", value: cvalue.value };
    case "CFloat":
      return { tag: "CFloat", value: cvalue.value };
    case "CBoolean":
      return { tag: "CBoolean", value: cvalue.value };
    case "CList":
      return {
        tag: "CList",
        value: cvalue.value.map(encodeCValue),
        subtype: encodeCType(cvalue.subtype),
      };
    case "CMap":
      return {
        tag: "CMap",
        value: cvalue.value.map((pair) => ({
          key: encodeCValue(pair.key),
          value: encodeCValue(pair.value),
        })),
        keysType: encodeCType(cvalue.keysType),
        valuesType: encodeCType(cvalue.valuesType),
      };
    case "CProduct": {
      const value: Record<string, unknown> = {};
      for (const [k, v] of Object.entries(cvalue.value)) {
        value[k] = encodeCValue(v);
      }
      const structure: Record<string, unknown> = {};
      for (const [k, v] of Object.entries(cvalue.structure)) {
        structure[k] = encodeCType(v);
      }
      return { tag: "CProduct", value, structure };
    }
    case "CUnion": {
      const structure: Record<string, unknown> = {};
      for (const [k, v] of Object.entries(cvalue.structure)) {
        structure[k] = encodeCType(v);
      }
      return {
        tag: "CUnion",
        value: encodeCValue(cvalue.value),
        structure,
        unionTag: cvalue.unionTag,
      };
    }
    case "CSome":
      return {
        tag: "CSome",
        value: encodeCValue(cvalue.value),
        innerType: encodeCType(cvalue.innerType),
      };
    case "CNone":
      return {
        tag: "CNone",
        innerType: encodeCType(cvalue.innerType),
      };
  }
}

/** Decode a CValue from its JSON wire representation. */
export function decodeCValue(json: unknown): CValue {
  if (typeof json !== "object" || json === null || !("tag" in json)) {
    throw new Error(`Invalid CValue JSON: expected object with "tag" field`);
  }
  const obj = json as Record<string, unknown>;
  const tag = obj.tag as string;

  switch (tag) {
    case "CString":
      if (typeof obj.value !== "string") throw new Error(`CString "value" must be a string`);
      return { tag: "CString", value: obj.value };
    case "CInt":
      if (typeof obj.value !== "number") throw new Error(`CInt "value" must be a number`);
      return { tag: "CInt", value: obj.value };
    case "CFloat":
      if (typeof obj.value !== "number") throw new Error(`CFloat "value" must be a number`);
      return { tag: "CFloat", value: obj.value };
    case "CBoolean":
      if (typeof obj.value !== "boolean") throw new Error(`CBoolean "value" must be a boolean`);
      return { tag: "CBoolean", value: obj.value };
    case "CList":
      if (!Array.isArray(obj.value)) throw new Error(`CList "value" must be an array`);
      if (obj.subtype == null) throw new Error(`CList missing "subtype" field`);
      return {
        tag: "CList",
        value: (obj.value as unknown[]).map(decodeCValue),
        subtype: decodeCType(obj.subtype),
      };
    case "CMap": {
      if (!Array.isArray(obj.value)) throw new Error(`CMap "value" must be an array`);
      if (obj.keysType == null) throw new Error(`CMap missing "keysType" field`);
      if (obj.valuesType == null) throw new Error(`CMap missing "valuesType" field`);
      const pairs = (obj.value as Array<Record<string, unknown>>).map((pair) => ({
        key: decodeCValue(pair.key),
        value: decodeCValue(pair.value),
      }));
      return {
        tag: "CMap",
        value: pairs,
        keysType: decodeCType(obj.keysType),
        valuesType: decodeCType(obj.valuesType),
      };
    }
    case "CProduct": {
      if (obj.value == null || typeof obj.value !== "object") {
        throw new Error(`CProduct missing "value" field`);
      }
      if (obj.structure == null || typeof obj.structure !== "object") {
        throw new Error(`CProduct missing "structure" field`);
      }
      const value: Record<string, CValue> = {};
      const rawValue = obj.value as Record<string, unknown>;
      for (const [k, v] of Object.entries(rawValue)) {
        value[k] = decodeCValue(v);
      }
      const structure: Record<string, CType> = {};
      const rawStructure = obj.structure as Record<string, unknown>;
      for (const [k, v] of Object.entries(rawStructure)) {
        structure[k] = decodeCType(v);
      }
      return { tag: "CProduct", value, structure };
    }
    case "CUnion": {
      if (obj.structure == null || typeof obj.structure !== "object") {
        throw new Error(`CUnion missing "structure" field`);
      }
      if (typeof obj.unionTag !== "string") throw new Error(`CUnion missing "unionTag" field`);
      const structure: Record<string, CType> = {};
      const rawStructure = obj.structure as Record<string, unknown>;
      for (const [k, v] of Object.entries(rawStructure)) {
        structure[k] = decodeCType(v);
      }
      return {
        tag: "CUnion",
        value: decodeCValue(obj.value),
        structure,
        unionTag: obj.unionTag,
      };
    }
    case "CSome":
      if (obj.innerType == null) throw new Error(`CSome missing "innerType" field`);
      return {
        tag: "CSome",
        value: decodeCValue(obj.value),
        innerType: decodeCType(obj.innerType),
      };
    case "CNone":
      if (obj.innerType == null) throw new Error(`CNone missing "innerType" field`);
      return {
        tag: "CNone",
        innerType: decodeCType(obj.innerType),
      };
    default:
      throw new Error(`Unknown CValue tag: ${tag}`);
  }
}

// ===== JsonCValueSerializer =====

const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder();

/** JSON-over-bytes CValueSerializer, wire-compatible with Scala's JsonCValueSerializer. */
export const JsonCValueSerializer: CValueSerializer = {
  serialize(value: CValue): Uint8Array {
    const json = JSON.stringify(encodeCValue(value));
    return textEncoder.encode(json);
  },

  deserialize(bytes: Uint8Array): CValue {
    const json = textDecoder.decode(bytes);
    const parsed = JSON.parse(json) as unknown;
    return decodeCValue(parsed);
  },
};
