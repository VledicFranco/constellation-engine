/**
 * TypeScript representation of the Constellation CType system.
 *
 * Uses discriminated unions with a `tag` field, matching the Scala sealed trait hierarchy
 * and JSON wire format exactly.
 */

// ===== CType discriminated union =====

export interface CStringType {
  readonly tag: "CString";
}

export interface CIntType {
  readonly tag: "CInt";
}

export interface CFloatType {
  readonly tag: "CFloat";
}

export interface CBooleanType {
  readonly tag: "CBoolean";
}

export interface CListType {
  readonly tag: "CList";
  readonly valuesType: CType;
}

export interface CMapType {
  readonly tag: "CMap";
  readonly keysType: CType;
  readonly valuesType: CType;
}

export interface CProductType {
  readonly tag: "CProduct";
  readonly structure: Record<string, CType>;
}

export interface CUnionType {
  readonly tag: "CUnion";
  readonly structure: Record<string, CType>;
}

export interface COptionalType {
  readonly tag: "COptional";
  readonly innerType: CType;
}

export type CType =
  | CStringType
  | CIntType
  | CFloatType
  | CBooleanType
  | CListType
  | CMapType
  | CProductType
  | CUnionType
  | COptionalType;

// ===== Factory helpers =====

export const CTypes = {
  string(): CStringType {
    return { tag: "CString" };
  },
  int(): CIntType {
    return { tag: "CInt" };
  },
  float(): CFloatType {
    return { tag: "CFloat" };
  },
  boolean(): CBooleanType {
    return { tag: "CBoolean" };
  },
  list(valuesType: CType): CListType {
    return { tag: "CList", valuesType };
  },
  map(keysType: CType, valuesType: CType): CMapType {
    return { tag: "CMap", keysType, valuesType };
  },
  product(structure: Record<string, CType>): CProductType {
    return { tag: "CProduct", structure };
  },
  union(structure: Record<string, CType>): CUnionType {
    return { tag: "CUnion", structure };
  },
  optional(innerType: CType): COptionalType {
    return { tag: "COptional", innerType };
  },
} as const;
