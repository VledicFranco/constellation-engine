/**
 * Shared test fixtures for CType, CValue, and cross-language JSON wire format.
 */

import { CTypes } from "../../src/types/ctype.js";
import type { CType } from "../../src/types/ctype.js";
import { CValues } from "../../src/types/cvalue.js";
import type { CValue } from "../../src/types/cvalue.js";

// ===== CType fixtures =====

export const ctypeFixtures: Array<{ name: string; ctype: CType; json: unknown }> = [
  {
    name: "CString",
    ctype: CTypes.string(),
    json: { tag: "CString" },
  },
  {
    name: "CInt",
    ctype: CTypes.int(),
    json: { tag: "CInt" },
  },
  {
    name: "CFloat",
    ctype: CTypes.float(),
    json: { tag: "CFloat" },
  },
  {
    name: "CBoolean",
    ctype: CTypes.boolean(),
    json: { tag: "CBoolean" },
  },
  {
    name: "CList(CString)",
    ctype: CTypes.list(CTypes.string()),
    json: { tag: "CList", valuesType: { tag: "CString" } },
  },
  {
    name: "CMap(CString, CInt)",
    ctype: CTypes.map(CTypes.string(), CTypes.int()),
    json: { tag: "CMap", keysType: { tag: "CString" }, valuesType: { tag: "CInt" } },
  },
  {
    name: "CProduct({name: CString, age: CInt})",
    ctype: CTypes.product({ name: CTypes.string(), age: CTypes.int() }),
    json: {
      tag: "CProduct",
      structure: { name: { tag: "CString" }, age: { tag: "CInt" } },
    },
  },
  {
    name: "CUnion({text: CString, number: CInt})",
    ctype: CTypes.union({ text: CTypes.string(), number: CTypes.int() }),
    json: {
      tag: "CUnion",
      structure: { text: { tag: "CString" }, number: { tag: "CInt" } },
    },
  },
  {
    name: "COptional(CString)",
    ctype: CTypes.optional(CTypes.string()),
    json: { tag: "COptional", innerType: { tag: "CString" } },
  },
  {
    name: "Nested CList(CProduct)",
    ctype: CTypes.list(CTypes.product({ x: CTypes.float(), y: CTypes.float() })),
    json: {
      tag: "CList",
      valuesType: {
        tag: "CProduct",
        structure: { x: { tag: "CFloat" }, y: { tag: "CFloat" } },
      },
    },
  },
];

// ===== CValue fixtures =====

export const cvalueFixtures: Array<{ name: string; cvalue: CValue; json: unknown }> = [
  {
    name: "CString",
    cvalue: CValues.string("hello"),
    json: { tag: "CString", value: "hello" },
  },
  {
    name: "CInt",
    cvalue: CValues.int(42),
    json: { tag: "CInt", value: 42 },
  },
  {
    name: "CFloat",
    cvalue: CValues.float(3.14),
    json: { tag: "CFloat", value: 3.14 },
  },
  {
    name: "CBoolean(true)",
    cvalue: CValues.boolean(true),
    json: { tag: "CBoolean", value: true },
  },
  {
    name: "CBoolean(false)",
    cvalue: CValues.boolean(false),
    json: { tag: "CBoolean", value: false },
  },
  {
    name: "CList([CString])",
    cvalue: CValues.list([CValues.string("a"), CValues.string("b")], CTypes.string()),
    json: {
      tag: "CList",
      value: [
        { tag: "CString", value: "a" },
        { tag: "CString", value: "b" },
      ],
      subtype: { tag: "CString" },
    },
  },
  {
    name: "CMap",
    cvalue: CValues.map(
      [{ key: CValues.string("k1"), value: CValues.int(1) }],
      CTypes.string(),
      CTypes.int(),
    ),
    json: {
      tag: "CMap",
      value: [{ key: { tag: "CString", value: "k1" }, value: { tag: "CInt", value: 1 } }],
      keysType: { tag: "CString" },
      valuesType: { tag: "CInt" },
    },
  },
  {
    name: "CProduct",
    cvalue: CValues.product(
      { name: CValues.string("Alice"), age: CValues.int(30) },
      { name: CTypes.string(), age: CTypes.int() },
    ),
    json: {
      tag: "CProduct",
      value: {
        name: { tag: "CString", value: "Alice" },
        age: { tag: "CInt", value: 30 },
      },
      structure: { name: { tag: "CString" }, age: { tag: "CInt" } },
    },
  },
  {
    name: "CUnion",
    cvalue: CValues.union(
      CValues.string("hello"),
      { text: CTypes.string(), number: CTypes.int() },
      "text",
    ),
    json: {
      tag: "CUnion",
      value: { tag: "CString", value: "hello" },
      structure: { text: { tag: "CString" }, number: { tag: "CInt" } },
      unionTag: "text",
    },
  },
  {
    name: "CSome",
    cvalue: CValues.some(CValues.string("present"), CTypes.string()),
    json: {
      tag: "CSome",
      value: { tag: "CString", value: "present" },
      innerType: { tag: "CString" },
    },
  },
  {
    name: "CNone",
    cvalue: CValues.none(CTypes.string()),
    json: { tag: "CNone", innerType: { tag: "CString" } },
  },
  {
    name: "Empty CList",
    cvalue: CValues.list([], CTypes.int()),
    json: { tag: "CList", value: [], subtype: { tag: "CInt" } },
  },
  {
    name: "Empty CMap",
    cvalue: CValues.map([], CTypes.string(), CTypes.string()),
    json: {
      tag: "CMap",
      value: [],
      keysType: { tag: "CString" },
      valuesType: { tag: "CString" },
    },
  },
];
