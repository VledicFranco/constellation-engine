/**
 * Bidirectional converter between protobuf TypeSchema and CType.
 *
 * Mirrors the Scala `io.constellation.provider.TypeSchemaConverter` exactly,
 * including synthetic field names for union variants ("variant0", "variant1", etc.).
 *
 * Since proto codegen may not be available in all environments (e.g., tests),
 * this module works with plain objects matching the proto message shapes.
 */

import type { CType } from "../types/ctype.js";

// ===== Proto message shapes (plain objects, no codegen dependency) =====

export interface TypeSchema {
  primitive?: PrimitiveType;
  record?: RecordType;
  list?: ListType;
  map?: MapType;
  union?: UnionType;
  option?: OptionType;
}

export interface PrimitiveType {
  kind: PrimitiveKind;
}

export type PrimitiveKind = 0 | 1 | 2 | 3 | "STRING" | "INT" | "FLOAT" | "BOOL";

export interface RecordType {
  fields: Record<string, TypeSchema>;
}

export interface ListType {
  elementType?: TypeSchema;
}

export interface MapType {
  keyType?: TypeSchema;
  valueType?: TypeSchema;
}

export interface UnionType {
  variants: TypeSchema[];
}

export interface OptionType {
  innerType?: TypeSchema;
}

// ===== Converter =====

export const TypeSchemaConverter = {
  /** Convert a protobuf TypeSchema to a CType. */
  toCType(schema: TypeSchema): CType {
    if (schema.primitive != null) {
      const kind = normalizePrimitiveKind(schema.primitive.kind);
      switch (kind) {
        case 0:
          return { tag: "CString" };
        case 1:
          return { tag: "CInt" };
        case 2:
          return { tag: "CFloat" };
        case 3:
          return { tag: "CBoolean" };
        default:
          throw new Error(`Unrecognized primitive kind: ${kind}`);
      }
    }

    if (schema.record != null) {
      const structure: Record<string, CType> = {};
      for (const [name, fieldSchema] of Object.entries(schema.record.fields)) {
        structure[name] = TypeSchemaConverter.toCType(fieldSchema);
      }
      return { tag: "CProduct", structure };
    }

    if (schema.list != null) {
      if (!schema.list.elementType) {
        throw new Error("ListType missing element_type");
      }
      return { tag: "CList", valuesType: TypeSchemaConverter.toCType(schema.list.elementType) };
    }

    if (schema.map != null) {
      if (!schema.map.keyType) {
        throw new Error("MapType missing key_type");
      }
      if (!schema.map.valueType) {
        throw new Error("MapType missing value_type");
      }
      return {
        tag: "CMap",
        keysType: TypeSchemaConverter.toCType(schema.map.keyType),
        valuesType: TypeSchemaConverter.toCType(schema.map.valueType),
      };
    }

    if (schema.union != null) {
      if (schema.union.variants.length === 0) {
        throw new Error("UnionType must have at least one variant");
      }
      const structure: Record<string, CType> = {};
      schema.union.variants.forEach((v, i) => {
        structure[`variant${i}`] = TypeSchemaConverter.toCType(v);
      });
      return { tag: "CUnion", structure };
    }

    if (schema.option != null) {
      if (!schema.option.innerType) {
        throw new Error("OptionType missing inner_type");
      }
      return {
        tag: "COptional",
        innerType: TypeSchemaConverter.toCType(schema.option.innerType),
      };
    }

    throw new Error("TypeSchema has no type set");
  },

  /** Convert a CType to a protobuf TypeSchema. */
  toTypeSchema(ctype: CType): TypeSchema {
    switch (ctype.tag) {
      case "CString":
        return { primitive: { kind: 0 } };
      case "CInt":
        return { primitive: { kind: 1 } };
      case "CFloat":
        return { primitive: { kind: 2 } };
      case "CBoolean":
        return { primitive: { kind: 3 } };
      case "CProduct": {
        const fields: Record<string, TypeSchema> = {};
        for (const [name, fieldType] of Object.entries(ctype.structure)) {
          fields[name] = TypeSchemaConverter.toTypeSchema(fieldType);
        }
        return { record: { fields } };
      }
      case "CList":
        return { list: { elementType: TypeSchemaConverter.toTypeSchema(ctype.valuesType) } };
      case "CMap":
        return {
          map: {
            keyType: TypeSchemaConverter.toTypeSchema(ctype.keysType),
            valueType: TypeSchemaConverter.toTypeSchema(ctype.valuesType),
          },
        };
      case "CUnion": {
        const variants = Object.values(ctype.structure).map((v) =>
          TypeSchemaConverter.toTypeSchema(v),
        );
        return { union: { variants } };
      }
      case "COptional":
        return { option: { innerType: TypeSchemaConverter.toTypeSchema(ctype.innerType) } };
    }
  },
} as const;

// ===== Helpers =====

function normalizePrimitiveKind(kind: PrimitiveKind): number {
  if (typeof kind === "number") return kind;
  switch (kind) {
    case "STRING":
      return 0;
    case "INT":
      return 1;
    case "FLOAT":
      return 2;
    case "BOOL":
      return 3;
    default:
      throw new Error(`Unknown primitive kind string: ${kind}`);
  }
}
