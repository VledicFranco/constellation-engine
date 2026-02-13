import { describe, it, expect } from "vitest";
import { TypeSchemaConverter } from "../../src/serialization/type-schema-converter.js";
import { CTypes } from "../../src/types/ctype.js";
import type { CType } from "../../src/types/ctype.js";

describe("TypeSchemaConverter", () => {
  // ===== toCType =====

  describe("toCType", () => {
    it("should convert STRING primitive", () => {
      expect(TypeSchemaConverter.toCType({ primitive: { kind: 0 } })).toEqual(CTypes.string());
    });

    it("should convert INT primitive", () => {
      expect(TypeSchemaConverter.toCType({ primitive: { kind: 1 } })).toEqual(CTypes.int());
    });

    it("should convert FLOAT primitive", () => {
      expect(TypeSchemaConverter.toCType({ primitive: { kind: 2 } })).toEqual(CTypes.float());
    });

    it("should convert BOOL primitive", () => {
      expect(TypeSchemaConverter.toCType({ primitive: { kind: 3 } })).toEqual(CTypes.boolean());
    });

    it("should convert string kind names", () => {
      expect(TypeSchemaConverter.toCType({ primitive: { kind: "STRING" } })).toEqual(
        CTypes.string(),
      );
      expect(TypeSchemaConverter.toCType({ primitive: { kind: "INT" } })).toEqual(CTypes.int());
      expect(TypeSchemaConverter.toCType({ primitive: { kind: "FLOAT" } })).toEqual(CTypes.float());
      expect(TypeSchemaConverter.toCType({ primitive: { kind: "BOOL" } })).toEqual(
        CTypes.boolean(),
      );
    });

    it("should convert RecordType to CProduct", () => {
      const schema = {
        record: {
          fields: {
            name: { primitive: { kind: 0 as const } },
            age: { primitive: { kind: 1 as const } },
          },
        },
      };
      expect(TypeSchemaConverter.toCType(schema)).toEqual(
        CTypes.product({ name: CTypes.string(), age: CTypes.int() }),
      );
    });

    it("should convert ListType to CList", () => {
      const schema = { list: { elementType: { primitive: { kind: 0 as const } } } };
      expect(TypeSchemaConverter.toCType(schema)).toEqual(CTypes.list(CTypes.string()));
    });

    it("should convert MapType to CMap", () => {
      const schema = {
        map: {
          keyType: { primitive: { kind: 0 as const } },
          valueType: { primitive: { kind: 1 as const } },
        },
      };
      expect(TypeSchemaConverter.toCType(schema)).toEqual(
        CTypes.map(CTypes.string(), CTypes.int()),
      );
    });

    it("should convert UnionType to CUnion with synthetic names", () => {
      const schema = {
        union: {
          variants: [{ primitive: { kind: 0 as const } }, { primitive: { kind: 1 as const } }],
        },
      };
      const result = TypeSchemaConverter.toCType(schema);
      expect(result).toEqual(CTypes.union({ variant0: CTypes.string(), variant1: CTypes.int() }));
    });

    it("should convert OptionType to COptional", () => {
      const schema = { option: { innerType: { primitive: { kind: 0 as const } } } };
      expect(TypeSchemaConverter.toCType(schema)).toEqual(CTypes.optional(CTypes.string()));
    });

    // Error cases
    it("should throw on empty schema", () => {
      expect(() => TypeSchemaConverter.toCType({})).toThrow("TypeSchema has no type set");
    });

    it("should throw on missing list element type", () => {
      expect(() => TypeSchemaConverter.toCType({ list: {} })).toThrow(
        "ListType missing element_type",
      );
    });

    it("should throw on missing map key type", () => {
      expect(() =>
        TypeSchemaConverter.toCType({
          map: { valueType: { primitive: { kind: 0 } } },
        }),
      ).toThrow("MapType missing key_type");
    });

    it("should throw on missing map value type", () => {
      expect(() =>
        TypeSchemaConverter.toCType({
          map: { keyType: { primitive: { kind: 0 } } },
        }),
      ).toThrow("MapType missing value_type");
    });

    it("should throw on empty union variants", () => {
      expect(() => TypeSchemaConverter.toCType({ union: { variants: [] } })).toThrow(
        "UnionType must have at least one variant",
      );
    });

    it("should throw on missing option inner type", () => {
      expect(() => TypeSchemaConverter.toCType({ option: {} })).toThrow(
        "OptionType missing inner_type",
      );
    });
  });

  // ===== toTypeSchema =====

  describe("toTypeSchema", () => {
    it("should convert CString to STRING primitive", () => {
      expect(TypeSchemaConverter.toTypeSchema(CTypes.string())).toEqual({
        primitive: { kind: 0 },
      });
    });

    it("should convert CInt to INT primitive", () => {
      expect(TypeSchemaConverter.toTypeSchema(CTypes.int())).toEqual({
        primitive: { kind: 1 },
      });
    });

    it("should convert CFloat to FLOAT primitive", () => {
      expect(TypeSchemaConverter.toTypeSchema(CTypes.float())).toEqual({
        primitive: { kind: 2 },
      });
    });

    it("should convert CBoolean to BOOL primitive", () => {
      expect(TypeSchemaConverter.toTypeSchema(CTypes.boolean())).toEqual({
        primitive: { kind: 3 },
      });
    });

    it("should convert CProduct to RecordType", () => {
      const result = TypeSchemaConverter.toTypeSchema(
        CTypes.product({ name: CTypes.string(), age: CTypes.int() }),
      );
      expect(result).toEqual({
        record: {
          fields: {
            name: { primitive: { kind: 0 } },
            age: { primitive: { kind: 1 } },
          },
        },
      });
    });

    it("should convert CList to ListType", () => {
      expect(TypeSchemaConverter.toTypeSchema(CTypes.list(CTypes.string()))).toEqual({
        list: { elementType: { primitive: { kind: 0 } } },
      });
    });

    it("should convert CMap to MapType", () => {
      expect(TypeSchemaConverter.toTypeSchema(CTypes.map(CTypes.string(), CTypes.int()))).toEqual({
        map: {
          keyType: { primitive: { kind: 0 } },
          valueType: { primitive: { kind: 1 } },
        },
      });
    });

    it("should convert CUnion to UnionType", () => {
      const result = TypeSchemaConverter.toTypeSchema(
        CTypes.union({ a: CTypes.string(), b: CTypes.int() }),
      );
      expect(result.union).toBeDefined();
      expect(result.union?.variants).toHaveLength(2);
    });

    it("should convert COptional to OptionType", () => {
      expect(TypeSchemaConverter.toTypeSchema(CTypes.optional(CTypes.string()))).toEqual({
        option: { innerType: { primitive: { kind: 0 } } },
      });
    });
  });

  // ===== Round-trip =====

  describe("round-trip", () => {
    const roundTripCases: Array<{ name: string; ctype: CType }> = [
      { name: "CString", ctype: CTypes.string() },
      { name: "CInt", ctype: CTypes.int() },
      { name: "CFloat", ctype: CTypes.float() },
      { name: "CBoolean", ctype: CTypes.boolean() },
      { name: "CList(CString)", ctype: CTypes.list(CTypes.string()) },
      {
        name: "CMap(CString, CInt)",
        ctype: CTypes.map(CTypes.string(), CTypes.int()),
      },
      {
        name: "CProduct",
        ctype: CTypes.product({ name: CTypes.string(), age: CTypes.int() }),
      },
      { name: "COptional(CString)", ctype: CTypes.optional(CTypes.string()) },
      {
        name: "Nested",
        ctype: CTypes.list(CTypes.product({ x: CTypes.float(), y: CTypes.float() })),
      },
    ];

    for (const { name, ctype } of roundTripCases) {
      it(`should round-trip ${name}`, () => {
        const schema = TypeSchemaConverter.toTypeSchema(ctype);
        const decoded = TypeSchemaConverter.toCType(schema);
        expect(decoded).toEqual(ctype);
      });
    }
  });
});
