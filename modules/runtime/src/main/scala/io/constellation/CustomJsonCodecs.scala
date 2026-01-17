package io.constellation

import cats.Eval
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, HCursor, Json}

import java.util.UUID
import scala.concurrent.duration.{DurationLong, FiniteDuration}

object json extends CustomJsonCodecs

trait CustomJsonCodecs {

  given finiteDurationEncoder: Encoder[FiniteDuration] =
    Encoder.instance(_.toMillis.asJson)

  given finiteDurationDecoder: Decoder[FiniteDuration] =
    Decoder.instance(_.as[Long].map(_.millis))

  given exceptionEncoder: Encoder[Throwable] =
    Encoder.instance(e => e.getMessage.asJson)

  given exceptionDecoder: Decoder[Throwable] =
    Decoder.instance(_.as[String].map(new RuntimeException(_)))

  given uuidMapEncoder[A](using encoder: Encoder[A]): Encoder[Map[UUID, A]] =
    Encoder[Map[String, A]].contramap[Map[UUID, A]](_.map { case (k, v) => k.toString -> v })

  given uuidMapDecoder[A](using decoder: Decoder[A]): Decoder[Map[UUID, A]] =
    Decoder[Map[String, A]].map(_.map { case (k, v) => UUID.fromString(k) -> v })

  given evalEncoder[A](using encoder: Encoder[A]): Encoder[Eval[A]] =
    Encoder.instance(_.value.asJson)

  given evalDecoder[A](using decoder: Decoder[A]): Decoder[Eval[A]] =
    Decoder.instance(_.as[A].map(Eval.now))

  // CType codecs with tagged union format
  given ctypeEncoder: Encoder[CType] = Encoder.instance {
    case CType.CString => Json.obj("tag" -> "CString".asJson)
    case CType.CInt => Json.obj("tag" -> "CInt".asJson)
    case CType.CFloat => Json.obj("tag" -> "CFloat".asJson)
    case CType.CBoolean => Json.obj("tag" -> "CBoolean".asJson)
    case CType.CList(valuesType) =>
      Json.obj("tag" -> "CList".asJson, "valuesType" -> valuesType.asJson)
    case CType.CMap(keysType, valuesType) =>
      Json.obj("tag" -> "CMap".asJson, "keysType" -> keysType.asJson, "valuesType" -> valuesType.asJson)
    case CType.CProduct(structure) =>
      Json.obj("tag" -> "CProduct".asJson, "structure" -> structure.asJson)
    case CType.CUnion(structure) =>
      Json.obj("tag" -> "CUnion".asJson, "structure" -> structure.asJson)
  }

  given ctypeDecoder: Decoder[CType] = Decoder.instance { c =>
    c.downField("tag").as[String].flatMap {
      case "CString" => Right(CType.CString)
      case "CInt" => Right(CType.CInt)
      case "CFloat" => Right(CType.CFloat)
      case "CBoolean" => Right(CType.CBoolean)
      case "CList" => c.downField("valuesType").as[CType].map(CType.CList.apply)
      case "CMap" => for {
        keysType <- c.downField("keysType").as[CType]
        valuesType <- c.downField("valuesType").as[CType]
      } yield CType.CMap(keysType, valuesType)
      case "CProduct" => c.downField("structure").as[Map[String, CType]].map(CType.CProduct.apply)
      case "CUnion" => c.downField("structure").as[Map[String, CType]].map(CType.CUnion.apply)
      case other => Left(io.circe.DecodingFailure(s"Unknown CType tag: $other", c.history))
    }
  }

  // Module.Status codecs with tagged union format
  given moduleStatusEncoder: Encoder[Module.Status] = Encoder.instance {
    case Module.Status.Unfired =>
      Json.obj("tag" -> "Unfired".asJson)
    case Module.Status.Fired(latency, context) =>
      Json.obj(
        "tag" -> "Fired".asJson,
        "latency" -> latency.asJson,
        "context" -> context.asJson
      )
    case Module.Status.Timed(latency) =>
      Json.obj("tag" -> "Timed".asJson, "latency" -> latency.asJson)
    case Module.Status.Failed(error) =>
      Json.obj("tag" -> "Failed".asJson, "error" -> error.asJson)
  }

  given moduleStatusDecoder: Decoder[Module.Status] = Decoder.instance { c =>
    c.downField("tag").as[String].flatMap {
      case "Unfired" => Right(Module.Status.Unfired)
      case "Fired" => for {
        latency <- c.downField("latency").as[FiniteDuration]
        context <- c.downField("context").as[Option[Map[String, Json]]]
      } yield Module.Status.Fired(latency, context)
      case "Timed" =>
        c.downField("latency").as[FiniteDuration].map(Module.Status.Timed.apply)
      case "Failed" =>
        c.downField("error").as[Throwable].map(Module.Status.Failed.apply)
      case other => Left(io.circe.DecodingFailure(s"Unknown Module.Status tag: $other", c.history))
    }
  }
}
