import { describe, it, expect } from "vitest";
import {
  JsonCValueSerializer,
  encodeCType,
  decodeCType,
  encodeCValue,
  decodeCValue,
} from "../../src/serialization/cvalue-serializer.js";
import { CTypes } from "../../src/types/ctype.js";
import { CValues } from "../../src/types/cvalue.js";
import { ctypeFixtures, cvalueFixtures } from "../helpers/test-fixtures.js";

// ===== CType encode/decode =====

describe("CType encoding", () => {
  for (const fixture of ctypeFixtures) {
    it(`should encode ${fixture.name}`, () => {
      const encoded = encodeCType(fixture.ctype);
      expect(encoded).toEqual(fixture.json);
    });
  }
});

describe("CType decoding", () => {
  for (const fixture of ctypeFixtures) {
    it(`should decode ${fixture.name}`, () => {
      const decoded = decodeCType(fixture.json);
      expect(decoded).toEqual(fixture.ctype);
    });
  }
});

describe("CType round-trip", () => {
  for (const fixture of ctypeFixtures) {
    it(`should round-trip ${fixture.name}`, () => {
      const encoded = encodeCType(fixture.ctype);
      const decoded = decodeCType(encoded);
      expect(decoded).toEqual(fixture.ctype);
    });
  }
});

describe("CType decoding errors", () => {
  it("should throw on missing tag", () => {
    expect(() => decodeCType({ foo: "bar" })).toThrow('expected object with "tag" field');
  });

  it("should throw on null input", () => {
    expect(() => decodeCType(null)).toThrow('expected object with "tag" field');
  });

  it("should throw on unknown tag", () => {
    expect(() => decodeCType({ tag: "CUnknown" })).toThrow("Unknown CType tag: CUnknown");
  });

  it("should throw on CList missing valuesType", () => {
    expect(() => decodeCType({ tag: "CList" })).toThrow('CList missing "valuesType"');
  });

  it("should throw on CMap missing keysType", () => {
    expect(() => decodeCType({ tag: "CMap", valuesType: { tag: "CString" } })).toThrow(
      'CMap missing "keysType"',
    );
  });

  it("should throw on CMap missing valuesType", () => {
    expect(() => decodeCType({ tag: "CMap", keysType: { tag: "CString" } })).toThrow(
      'CMap missing "valuesType"',
    );
  });

  it("should throw on CProduct missing structure", () => {
    expect(() => decodeCType({ tag: "CProduct" })).toThrow('CProduct missing "structure"');
  });

  it("should throw on CUnion missing structure", () => {
    expect(() => decodeCType({ tag: "CUnion" })).toThrow('CUnion missing "structure"');
  });

  it("should throw on COptional missing innerType", () => {
    expect(() => decodeCType({ tag: "COptional" })).toThrow('COptional missing "innerType"');
  });
});

// ===== CValue encode/decode =====

describe("CValue encoding", () => {
  for (const fixture of cvalueFixtures) {
    it(`should encode ${fixture.name}`, () => {
      const encoded = encodeCValue(fixture.cvalue);
      expect(encoded).toEqual(fixture.json);
    });
  }
});

describe("CValue decoding", () => {
  for (const fixture of cvalueFixtures) {
    it(`should decode ${fixture.name}`, () => {
      const decoded = decodeCValue(fixture.json);
      expect(decoded).toEqual(fixture.cvalue);
    });
  }
});

describe("CValue round-trip", () => {
  for (const fixture of cvalueFixtures) {
    it(`should round-trip ${fixture.name}`, () => {
      const encoded = encodeCValue(fixture.cvalue);
      const decoded = decodeCValue(encoded);
      expect(decoded).toEqual(fixture.cvalue);
    });
  }
});

describe("CValue decoding errors", () => {
  it("should throw on missing tag", () => {
    expect(() => decodeCValue({ value: "bar" })).toThrow('expected object with "tag" field');
  });

  it("should throw on null input", () => {
    expect(() => decodeCValue(null)).toThrow('expected object with "tag" field');
  });

  it("should throw on unknown tag", () => {
    expect(() => decodeCValue({ tag: "CUnknown" })).toThrow("Unknown CValue tag: CUnknown");
  });

  it("should throw on CString with non-string value", () => {
    expect(() => decodeCValue({ tag: "CString", value: 42 })).toThrow(
      'CString "value" must be a string',
    );
  });

  it("should throw on CInt with non-number value", () => {
    expect(() => decodeCValue({ tag: "CInt", value: "not a number" })).toThrow(
      'CInt "value" must be a number',
    );
  });

  it("should throw on CFloat with non-number value", () => {
    expect(() => decodeCValue({ tag: "CFloat", value: true })).toThrow(
      'CFloat "value" must be a number',
    );
  });

  it("should throw on CBoolean with non-boolean value", () => {
    expect(() => decodeCValue({ tag: "CBoolean", value: "true" })).toThrow(
      'CBoolean "value" must be a boolean',
    );
  });

  it("should throw on CList with non-array value", () => {
    expect(() =>
      decodeCValue({ tag: "CList", value: "not array", subtype: { tag: "CString" } }),
    ).toThrow('CList "value" must be an array');
  });

  it("should throw on CList missing subtype", () => {
    expect(() => decodeCValue({ tag: "CList", value: [] })).toThrow('CList missing "subtype"');
  });

  it("should throw on CMap with non-array value", () => {
    expect(() =>
      decodeCValue({
        tag: "CMap",
        value: "bad",
        keysType: { tag: "CString" },
        valuesType: { tag: "CInt" },
      }),
    ).toThrow('CMap "value" must be an array');
  });

  it("should throw on CProduct missing value", () => {
    expect(() => decodeCValue({ tag: "CProduct", structure: { a: { tag: "CString" } } })).toThrow(
      'CProduct missing "value"',
    );
  });

  it("should throw on CProduct missing structure", () => {
    expect(() =>
      decodeCValue({
        tag: "CProduct",
        value: { a: { tag: "CString", value: "x" } },
      }),
    ).toThrow('CProduct missing "structure"');
  });

  it("should throw on CUnion missing structure", () => {
    expect(() =>
      decodeCValue({
        tag: "CUnion",
        value: { tag: "CString", value: "hi" },
        unionTag: "a",
      }),
    ).toThrow('CUnion missing "structure"');
  });

  it("should throw on CUnion missing unionTag", () => {
    expect(() =>
      decodeCValue({
        tag: "CUnion",
        value: { tag: "CString", value: "hi" },
        structure: { a: { tag: "CString" } },
      }),
    ).toThrow('CUnion missing "unionTag"');
  });

  it("should throw on CSome missing innerType", () => {
    expect(() => decodeCValue({ tag: "CSome", value: { tag: "CInt", value: 1 } })).toThrow(
      'CSome missing "innerType"',
    );
  });

  it("should throw on CNone missing innerType", () => {
    expect(() => decodeCValue({ tag: "CNone" })).toThrow('CNone missing "innerType"');
  });
});

// ===== JsonCValueSerializer =====

describe("JsonCValueSerializer", () => {
  it("should serialize and deserialize CString", () => {
    const value = CValues.string("hello");
    const bytes = JsonCValueSerializer.serialize(value);
    const decoded = JsonCValueSerializer.deserialize(bytes);
    expect(decoded).toEqual(value);
  });

  it("should serialize and deserialize CInt", () => {
    const value = CValues.int(42);
    const bytes = JsonCValueSerializer.serialize(value);
    const decoded = JsonCValueSerializer.deserialize(bytes);
    expect(decoded).toEqual(value);
  });

  it("should serialize and deserialize CFloat", () => {
    const value = CValues.float(3.14);
    const bytes = JsonCValueSerializer.serialize(value);
    const decoded = JsonCValueSerializer.deserialize(bytes);
    expect(decoded).toEqual(value);
  });

  it("should serialize and deserialize CProduct", () => {
    const value = CValues.product(
      { name: CValues.string("Alice"), age: CValues.int(30) },
      { name: CTypes.string(), age: CTypes.int() },
    );
    const bytes = JsonCValueSerializer.serialize(value);
    const decoded = JsonCValueSerializer.deserialize(bytes);
    expect(decoded).toEqual(value);
  });

  it("should serialize and deserialize CMap", () => {
    const value = CValues.map(
      [
        { key: CValues.string("x"), value: CValues.int(1) },
        { key: CValues.string("y"), value: CValues.int(2) },
      ],
      CTypes.string(),
      CTypes.int(),
    );
    const bytes = JsonCValueSerializer.serialize(value);
    const decoded = JsonCValueSerializer.deserialize(bytes);
    expect(decoded).toEqual(value);
  });

  it("should serialize and deserialize CUnion", () => {
    const value = CValues.union(
      CValues.string("hello"),
      { text: CTypes.string(), number: CTypes.int() },
      "text",
    );
    const bytes = JsonCValueSerializer.serialize(value);
    const decoded = JsonCValueSerializer.deserialize(bytes);
    expect(decoded).toEqual(value);
  });

  it("should serialize and deserialize CSome", () => {
    const value = CValues.some(CValues.int(42), CTypes.int());
    const bytes = JsonCValueSerializer.serialize(value);
    const decoded = JsonCValueSerializer.deserialize(bytes);
    expect(decoded).toEqual(value);
  });

  it("should serialize and deserialize CNone", () => {
    const value = CValues.none(CTypes.string());
    const bytes = JsonCValueSerializer.serialize(value);
    const decoded = JsonCValueSerializer.deserialize(bytes);
    expect(decoded).toEqual(value);
  });

  it("should produce UTF-8 JSON bytes", () => {
    const value = CValues.string("hello");
    const bytes = JsonCValueSerializer.serialize(value);
    const json = new TextDecoder().decode(bytes);
    const parsed = JSON.parse(json);
    expect(parsed).toEqual({ tag: "CString", value: "hello" });
  });

  // Cross-language wire format fixtures (captured from Scala Circe output)
  describe("cross-language wire format", () => {
    it("should match Scala CString JSON", () => {
      const scalaJson = '{"tag":"CString","value":"hello"}';
      const decoded = JsonCValueSerializer.deserialize(new TextEncoder().encode(scalaJson));
      expect(decoded).toEqual(CValues.string("hello"));
    });

    it("should match Scala CInt JSON", () => {
      const scalaJson = '{"tag":"CInt","value":42}';
      const decoded = JsonCValueSerializer.deserialize(new TextEncoder().encode(scalaJson));
      expect(decoded).toEqual(CValues.int(42));
    });

    it("should match Scala CFloat JSON", () => {
      const scalaJson = '{"tag":"CFloat","value":3.14}';
      const decoded = JsonCValueSerializer.deserialize(new TextEncoder().encode(scalaJson));
      expect(decoded).toEqual(CValues.float(3.14));
    });

    it("should match Scala CBoolean JSON", () => {
      const scalaJson = '{"tag":"CBoolean","value":true}';
      const decoded = JsonCValueSerializer.deserialize(new TextEncoder().encode(scalaJson));
      expect(decoded).toEqual(CValues.boolean(true));
    });

    it("should match Scala CProduct JSON", () => {
      const scalaJson =
        '{"tag":"CProduct","value":{"name":{"tag":"CString","value":"Alice"}},"structure":{"name":{"tag":"CString"}}}';
      const decoded = JsonCValueSerializer.deserialize(new TextEncoder().encode(scalaJson));
      expect(decoded).toEqual(
        CValues.product({ name: CValues.string("Alice") }, { name: CTypes.string() }),
      );
    });

    it("should match Scala CMap JSON", () => {
      const scalaJson =
        '{"tag":"CMap","value":[{"key":{"tag":"CString","value":"k"},"value":{"tag":"CInt","value":1}}],"keysType":{"tag":"CString"},"valuesType":{"tag":"CInt"}}';
      const decoded = JsonCValueSerializer.deserialize(new TextEncoder().encode(scalaJson));
      expect(decoded).toEqual(
        CValues.map(
          [{ key: CValues.string("k"), value: CValues.int(1) }],
          CTypes.string(),
          CTypes.int(),
        ),
      );
    });

    it("should match Scala CUnion JSON", () => {
      const scalaJson =
        '{"tag":"CUnion","value":{"tag":"CString","value":"hi"},"structure":{"a":{"tag":"CString"},"b":{"tag":"CInt"}},"unionTag":"a"}';
      const decoded = JsonCValueSerializer.deserialize(new TextEncoder().encode(scalaJson));
      expect(decoded).toEqual(
        CValues.union(CValues.string("hi"), { a: CTypes.string(), b: CTypes.int() }, "a"),
      );
    });

    it("should match Scala CSome JSON", () => {
      const scalaJson =
        '{"tag":"CSome","value":{"tag":"CInt","value":42},"innerType":{"tag":"CInt"}}';
      const decoded = JsonCValueSerializer.deserialize(new TextEncoder().encode(scalaJson));
      expect(decoded).toEqual(CValues.some(CValues.int(42), CTypes.int()));
    });

    it("should match Scala CNone JSON", () => {
      const scalaJson = '{"tag":"CNone","innerType":{"tag":"CString"}}';
      const decoded = JsonCValueSerializer.deserialize(new TextEncoder().encode(scalaJson));
      expect(decoded).toEqual(CValues.none(CTypes.string()));
    });
  });
});
