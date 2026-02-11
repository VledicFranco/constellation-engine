package io.constellation.provider

import io.constellation.CType
import io.constellation.provider.v1.{provider => pb}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TypeSchemaConverterSpec extends AnyFlatSpec with Matchers {

  // ===== toCType =====

  "TypeSchemaConverter.toCType" should "convert STRING primitive" in {
    val schema = pb.TypeSchema(pb.TypeSchema.Type.Primitive(
      pb.PrimitiveType(pb.PrimitiveType.Kind.STRING)
    ))
    TypeSchemaConverter.toCType(schema) shouldBe Right(CType.CString)
  }

  it should "convert INT primitive" in {
    val schema = pb.TypeSchema(pb.TypeSchema.Type.Primitive(
      pb.PrimitiveType(pb.PrimitiveType.Kind.INT)
    ))
    TypeSchemaConverter.toCType(schema) shouldBe Right(CType.CInt)
  }

  it should "convert FLOAT primitive" in {
    val schema = pb.TypeSchema(pb.TypeSchema.Type.Primitive(
      pb.PrimitiveType(pb.PrimitiveType.Kind.FLOAT)
    ))
    TypeSchemaConverter.toCType(schema) shouldBe Right(CType.CFloat)
  }

  it should "convert BOOL primitive" in {
    val schema = pb.TypeSchema(pb.TypeSchema.Type.Primitive(
      pb.PrimitiveType(pb.PrimitiveType.Kind.BOOL)
    ))
    TypeSchemaConverter.toCType(schema) shouldBe Right(CType.CBoolean)
  }

  it should "convert RecordType" in {
    val schema = pb.TypeSchema(pb.TypeSchema.Type.Record(pb.RecordType(Map(
      "name" -> pb.TypeSchema(pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.STRING))),
      "age"  -> pb.TypeSchema(pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.INT)))
    ))))
    TypeSchemaConverter.toCType(schema) shouldBe Right(
      CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt))
    )
  }

  it should "convert ListType" in {
    val schema = pb.TypeSchema(pb.TypeSchema.Type.List(pb.ListType(
      Some(pb.TypeSchema(pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.STRING))))
    )))
    TypeSchemaConverter.toCType(schema) shouldBe Right(CType.CList(CType.CString))
  }

  it should "convert MapType" in {
    val schema = pb.TypeSchema(pb.TypeSchema.Type.Map(pb.MapType(
      keyType = Some(pb.TypeSchema(pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.STRING)))),
      valueType = Some(pb.TypeSchema(pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.INT))))
    )))
    TypeSchemaConverter.toCType(schema) shouldBe Right(CType.CMap(CType.CString, CType.CInt))
  }

  it should "convert OptionType" in {
    val schema = pb.TypeSchema(pb.TypeSchema.Type.Option(pb.OptionType(
      Some(pb.TypeSchema(pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.STRING))))
    )))
    TypeSchemaConverter.toCType(schema) shouldBe Right(CType.COptional(CType.CString))
  }

  it should "convert UnionType" in {
    val schema = pb.TypeSchema(pb.TypeSchema.Type.Union(pb.UnionType(Seq(
      pb.TypeSchema(pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.STRING))),
      pb.TypeSchema(pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.INT)))
    ))))
    val result = TypeSchemaConverter.toCType(schema)
    result.isRight shouldBe true
    result.toOption.get match {
      case CType.CUnion(structure) =>
        structure.values.toSet shouldBe Set(CType.CString, CType.CInt)
      case other => fail(s"Expected CUnion, got $other")
    }
  }

  it should "reject empty TypeSchema" in {
    val schema = pb.TypeSchema()
    TypeSchemaConverter.toCType(schema).isLeft shouldBe true
  }

  it should "reject ListType with missing element_type" in {
    val schema = pb.TypeSchema(pb.TypeSchema.Type.List(pb.ListType(None)))
    TypeSchemaConverter.toCType(schema).isLeft shouldBe true
  }

  it should "reject MapType with missing key_type" in {
    val schema = pb.TypeSchema(pb.TypeSchema.Type.Map(pb.MapType(
      keyType = None,
      valueType = Some(pb.TypeSchema(pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.INT))))
    )))
    TypeSchemaConverter.toCType(schema).isLeft shouldBe true
  }

  it should "reject empty UnionType" in {
    val schema = pb.TypeSchema(pb.TypeSchema.Type.Union(pb.UnionType(Seq.empty)))
    TypeSchemaConverter.toCType(schema).isLeft shouldBe true
  }

  // ===== Round-trip =====

  "TypeSchemaConverter" should "round-trip CString" in {
    roundTrip(CType.CString)
  }

  it should "round-trip CInt" in {
    roundTrip(CType.CInt)
  }

  it should "round-trip CFloat" in {
    roundTrip(CType.CFloat)
  }

  it should "round-trip CBoolean" in {
    roundTrip(CType.CBoolean)
  }

  it should "round-trip CProduct" in {
    roundTrip(CType.CProduct(Map("x" -> CType.CString, "y" -> CType.CInt)))
  }

  it should "round-trip CList" in {
    roundTrip(CType.CList(CType.CFloat))
  }

  it should "round-trip CMap" in {
    roundTrip(CType.CMap(CType.CString, CType.CBoolean))
  }

  it should "round-trip COptional" in {
    roundTrip(CType.COptional(CType.CInt))
  }

  it should "round-trip nested types" in {
    roundTrip(CType.CProduct(Map(
      "items" -> CType.CList(CType.CProduct(Map(
        "name"  -> CType.CString,
        "score" -> CType.CFloat
      ))),
      "count" -> CType.CInt
    )))
  }

  private def roundTrip(ctype: CType): Unit = {
    val schema = TypeSchemaConverter.toTypeSchema(ctype)
    val result = TypeSchemaConverter.toCType(schema)
    result shouldBe Right(ctype)
  }
}
