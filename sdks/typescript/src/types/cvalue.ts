/**
 * TypeScript representation of the Constellation CValue system.
 *
 * Uses discriminated unions with a `tag` field, matching the Scala sealed trait hierarchy
 * and JSON wire format exactly.
 */

import type { CType } from "./ctype.js";

// ===== CValue discriminated union =====

export interface CStringValue {
  readonly tag: "CString";
  readonly value: string;
}

export interface CIntValue {
  readonly tag: "CInt";
  readonly value: number;
}

export interface CFloatValue {
  readonly tag: "CFloat";
  readonly value: number;
}

export interface CBooleanValue {
  readonly tag: "CBoolean";
  readonly value: boolean;
}

export interface CListValue {
  readonly tag: "CList";
  readonly value: CValue[];
  readonly subtype: CType;
}

export interface CMapValue {
  readonly tag: "CMap";
  readonly value: Array<{ key: CValue; value: CValue }>;
  readonly keysType: CType;
  readonly valuesType: CType;
}

export interface CProductValue {
  readonly tag: "CProduct";
  readonly value: Record<string, CValue>;
  readonly structure: Record<string, CType>;
}

export interface CUnionValue {
  readonly tag: "CUnion";
  readonly value: CValue;
  readonly structure: Record<string, CType>;
  readonly unionTag: string;
}

export interface CSomeValue {
  readonly tag: "CSome";
  readonly value: CValue;
  readonly innerType: CType;
}

export interface CNoneValue {
  readonly tag: "CNone";
  readonly innerType: CType;
}

export type CValue =
  | CStringValue
  | CIntValue
  | CFloatValue
  | CBooleanValue
  | CListValue
  | CMapValue
  | CProductValue
  | CUnionValue
  | CSomeValue
  | CNoneValue;

// ===== Factory helpers =====

export const CValues = {
  string(value: string): CStringValue {
    return { tag: "CString", value };
  },
  int(value: number): CIntValue {
    return { tag: "CInt", value };
  },
  float(value: number): CFloatValue {
    return { tag: "CFloat", value };
  },
  boolean(value: boolean): CBooleanValue {
    return { tag: "CBoolean", value };
  },
  list(value: CValue[], subtype: CType): CListValue {
    return { tag: "CList", value, subtype };
  },
  map(value: Array<{ key: CValue; value: CValue }>, keysType: CType, valuesType: CType): CMapValue {
    return { tag: "CMap", value, keysType, valuesType };
  },
  product(value: Record<string, CValue>, structure: Record<string, CType>): CProductValue {
    return { tag: "CProduct", value, structure };
  },
  union(value: CValue, structure: Record<string, CType>, unionTag: string): CUnionValue {
    return { tag: "CUnion", value, structure, unionTag };
  },
  some(value: CValue, innerType: CType): CSomeValue {
    return { tag: "CSome", value, innerType };
  },
  none(innerType: CType): CNoneValue {
    return { tag: "CNone", innerType };
  },
} as const;
