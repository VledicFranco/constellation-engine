package io.constellation.provider

import io.constellation.CType
import io.constellation.provider.v1.provider as pb

/** Bidirectional converter between protobuf TypeSchema and core CType. */
object TypeSchemaConverter {

  /** Convert a protobuf TypeSchema to a CType. */
  def toCType(schema: pb.TypeSchema): Either[String, CType] =
    schema.`type` match {
      case pb.TypeSchema.Type.Primitive(pt) =>
        pt.kind match {
          case pb.PrimitiveType.Kind.STRING => Right(CType.CString)
          case pb.PrimitiveType.Kind.INT    => Right(CType.CInt)
          case pb.PrimitiveType.Kind.FLOAT  => Right(CType.CFloat)
          case pb.PrimitiveType.Kind.BOOL   => Right(CType.CBoolean)
          case pb.PrimitiveType.Kind.Unrecognized(v) =>
            Left(s"Unrecognized primitive kind: $v")
        }

      case pb.TypeSchema.Type.Record(rt) =>
        val fieldResults = rt.fields.toList.map { case (name, fieldSchema) =>
          toCType(fieldSchema).map(name -> _)
        }
        sequence(fieldResults).map(fields => CType.CProduct(fields.toMap))

      case pb.TypeSchema.Type.List(lt) =>
        lt.elementType match {
          case Some(elemSchema) => toCType(elemSchema).map(CType.CList.apply)
          case None             => Left("ListType missing element_type")
        }

      case pb.TypeSchema.Type.Map(mt) =>
        for {
          keyType <- mt.keyType.toRight("MapType missing key_type").flatMap(toCType)
          valType <- mt.valueType.toRight("MapType missing value_type").flatMap(toCType)
        } yield CType.CMap(keyType, valType)

      case pb.TypeSchema.Type.Union(ut) =>
        if ut.variants.isEmpty then Left("UnionType must have at least one variant")
        else {
          val variantResults = ut.variants.toList.zipWithIndex.map { case (v, i) =>
            toCType(v).map(ct => s"variant$i" -> ct)
          }
          sequence(variantResults).map(variants => CType.CUnion(variants.toMap))
        }

      case pb.TypeSchema.Type.Option(ot) =>
        ot.innerType match {
          case Some(innerSchema) => toCType(innerSchema).map(CType.COptional.apply)
          case None              => Left("OptionType missing inner_type")
        }

      case pb.TypeSchema.Type.Empty =>
        Left("TypeSchema has no type set")
    }

  /** Convert a CType to a protobuf TypeSchema. */
  def toTypeSchema(ctype: CType): pb.TypeSchema = ctype match {
    case CType.CString =>
      pb.TypeSchema(pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.STRING)))
    case CType.CInt =>
      pb.TypeSchema(pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.INT)))
    case CType.CFloat =>
      pb.TypeSchema(pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.FLOAT)))
    case CType.CBoolean =>
      pb.TypeSchema(pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.BOOL)))

    case CType.CProduct(structure) =>
      val fields = structure.view.mapValues(toTypeSchema).toMap
      pb.TypeSchema(pb.TypeSchema.Type.Record(pb.RecordType(fields)))

    case CType.CList(elemType) =>
      pb.TypeSchema(pb.TypeSchema.Type.List(pb.ListType(Some(toTypeSchema(elemType)))))

    case CType.CMap(keyType, valType) =>
      pb.TypeSchema(
        pb.TypeSchema.Type.Map(
          pb.MapType(Some(toTypeSchema(keyType)), Some(toTypeSchema(valType)))
        )
      )

    case CType.CUnion(structure) =>
      val variants = structure.values.map(toTypeSchema).toSeq
      pb.TypeSchema(pb.TypeSchema.Type.Union(pb.UnionType(variants)))

    case CType.COptional(inner) =>
      pb.TypeSchema(pb.TypeSchema.Type.Option(pb.OptionType(Some(toTypeSchema(inner)))))
  }

  private def sequence[A](list: List[Either[String, A]]): Either[String, List[A]] =
    list.foldRight(Right(Nil): Either[String, List[A]]) { (elem, acc) =>
      for {
        a    <- elem
        rest <- acc
      } yield a :: rest
    }
}
