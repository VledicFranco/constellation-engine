package io.constellation.docgen.model

import io.circe.*
import io.circe.generic.semiauto.*

/** Represents extracted type information from Scala code */
sealed trait TypeInfo:
  def name: String
  def pkg: String
  def scaladoc: Option[String]

  def fullName: String = s"$pkg.$name"

object TypeInfo:
  given Encoder[TypeInfo] = Encoder.instance {
    case c: ClassInfo  => ClassInfo.encoder(c)
    case o: ObjectInfo => ObjectInfo.encoder(o)
    case t: TraitInfo  => TraitInfo.encoder(t)
    case e: EnumInfo   => EnumInfo.encoder(e)
  }

  given Decoder[TypeInfo] = Decoder.instance { cursor =>
    cursor.get[String]("kind").flatMap {
      case "class"  => ClassInfo.decoder(cursor)
      case "object" => ObjectInfo.decoder(cursor)
      case "trait"  => TraitInfo.decoder(cursor)
      case "enum"   => EnumInfo.decoder(cursor)
      case other    => Left(DecodingFailure(s"Unknown kind: $other", cursor.history))
    }
  }

/** Information about a class or case class */
case class ClassInfo(
    name: String,
    pkg: String,
    scaladoc: Option[String],
    typeParams: List[String],
    parents: List[String],
    fields: List[FieldInfo],
    methods: List[MethodInfo],
    isCaseClass: Boolean
) extends TypeInfo

object ClassInfo:
  given encoder: Encoder[ClassInfo] = deriveEncoder[ClassInfo].mapJson(_.deepMerge(Json.obj("kind" -> Json.fromString("class"))))
  given decoder: Decoder[ClassInfo] = deriveDecoder[ClassInfo]

/** Information about an object (singleton) */
case class ObjectInfo(
    name: String,
    pkg: String,
    scaladoc: Option[String],
    methods: List[MethodInfo],
    fields: List[FieldInfo]
) extends TypeInfo

object ObjectInfo:
  given encoder: Encoder[ObjectInfo] = deriveEncoder[ObjectInfo].mapJson(_.deepMerge(Json.obj("kind" -> Json.fromString("object"))))
  given decoder: Decoder[ObjectInfo] = deriveDecoder[ObjectInfo]

/** Information about a trait */
case class TraitInfo(
    name: String,
    pkg: String,
    scaladoc: Option[String],
    typeParams: List[String],
    parents: List[String],
    methods: List[MethodInfo]
) extends TypeInfo

object TraitInfo:
  given encoder: Encoder[TraitInfo] = deriveEncoder[TraitInfo].mapJson(_.deepMerge(Json.obj("kind" -> Json.fromString("trait"))))
  given decoder: Decoder[TraitInfo] = deriveDecoder[TraitInfo]

/** Information about an enum */
case class EnumInfo(
    name: String,
    pkg: String,
    scaladoc: Option[String],
    cases: List[EnumCaseInfo]
) extends TypeInfo

object EnumInfo:
  given encoder: Encoder[EnumInfo] = deriveEncoder[EnumInfo].mapJson(_.deepMerge(Json.obj("kind" -> Json.fromString("enum"))))
  given decoder: Decoder[EnumInfo] = deriveDecoder[EnumInfo]

/** Information about a field */
case class FieldInfo(
    name: String,
    typeName: String,
    scaladoc: Option[String]
)

object FieldInfo:
  given Encoder[FieldInfo] = deriveEncoder
  given Decoder[FieldInfo] = deriveDecoder

/** Information about a method */
case class MethodInfo(
    name: String,
    typeParams: List[String],
    params: List[ParamInfo],
    returnType: String,
    scaladoc: Option[String]
)

object MethodInfo:
  given Encoder[MethodInfo] = deriveEncoder
  given Decoder[MethodInfo] = deriveDecoder

/** Information about a method parameter */
case class ParamInfo(
    name: String,
    typeName: String
)

object ParamInfo:
  given Encoder[ParamInfo] = deriveEncoder
  given Decoder[ParamInfo] = deriveDecoder

/** Information about an enum case */
case class EnumCaseInfo(
    name: String,
    params: List[ParamInfo]
)

object EnumCaseInfo:
  given Encoder[EnumCaseInfo] = deriveEncoder
  given Decoder[EnumCaseInfo] = deriveDecoder

/** A catalog of types organized by package */
case class PackageCatalog(
    pkg: String,
    sourceDir: String,
    types: List[TypeInfo]
)

object PackageCatalog:
  given Encoder[PackageCatalog] = deriveEncoder
  given Decoder[PackageCatalog] = deriveDecoder

/** Full catalog across all packages */
case class Catalog(
    packages: List[PackageCatalog],
    generatedAt: String,
    sourceHash: String
)

object Catalog:
  given Encoder[Catalog] = deriveEncoder
  given Decoder[Catalog] = deriveDecoder
