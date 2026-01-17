package io.constellation.api

import cats.Eval
import io.circe.syntax._
import io.circe.{Decoder, DecodingFailure, Encoder, JsonObject}
import shapeless.labelled.{FieldType, field}
import shapeless.{:+:, :: => :++:, CNil, Coproduct, HList, HNil, Inl, Inr, LabelledGeneric, Lazy, Witness}

import java.util.UUID
import scala.concurrent.duration.{DurationLong, FiniteDuration}

object json extends CustomJsonCodecs

trait CustomJsonCodecs extends LowerPriorityDerivedCodecs {

  implicit def finiteDurationEncoder: Encoder[FiniteDuration] =
    Encoder.instance(_.toMillis.asJson)

  implicit def finiteDurationDecoder: Decoder[FiniteDuration] =
    Decoder.instance(_.as[Long].map(_.millis))

  implicit def exceptionEncoder: Encoder[Throwable] =
    Encoder.instance(e => e.getMessage.asJson)

  implicit def exceptionDecoder: Decoder[Throwable] =
    Decoder.instance(_.as[String].map(new RuntimeException(_)))

  implicit def uuidMapEncoder[A](implicit encoder: Encoder[A]): Encoder[Map[UUID, A]] =
    Encoder[Map[String, A]].contramap[Map[UUID, A]](_.map { case (k, v) => k.toString -> v })

  implicit def uuidMapDecoder[A](implicit decoder: Decoder[A]): Decoder[Map[UUID, A]] =
    Decoder[Map[String, A]].map(_.map { case (k, v) => UUID.fromString(k) -> v })

  implicit def evalEncoder[A](implicit encoder: Encoder[A]): Encoder[Eval[A]] =
    Encoder.instance(_.value.asJson)

  implicit def evalDecoder[A](implicit decoder: Decoder[A]): Decoder[Eval[A]] =
    Decoder.instance(_.as[A].map(Eval.now))

  implicit def higherPriorityListEncoder[A](implicit encoder: Encoder[A]): Encoder[List[A]] =
    Encoder.encodeList

  implicit def higherPriorityListDecoder[A](implicit decoder: Decoder[A]): Decoder[List[A]] =
    Decoder.decodeList

  implicit def higherPriorityOptionEncoder[A](implicit encoder: Encoder[A]): Encoder[Option[A]] =
    Encoder.encodeOption(encoder)

  implicit def higherPriorityOptionDecoder[A](implicit decoder: Decoder[A]): Decoder[Option[A]] =
    Decoder.decodeOption(decoder)

  implicit def higherPriorityTuple2Encoder[A, B](implicit encoderA: Encoder[A], encoderB: Encoder[B]): Encoder[(A, B)] =
    Encoder.encodeTuple2

  implicit def higherPriorityTuple2Decoder[A, B](implicit decoderA: Decoder[A], decoderB: Decoder[B]): Decoder[(A, B)] =
    Decoder.decodeTuple2
}

trait LowerPriorityDerivedCodecs {

  implicit def deriveTaggedEncoder[A](implicit encoder: Lazy[DerivedTaggedEncoder[A]]): Encoder[A] =
    encoder.value.encoder

  implicit def deriveTaggedDecoder[A](implicit decoder: Lazy[DerivedTaggedDecoder[A]]): Decoder[A] =
    decoder.value.decoder
}

trait DerivedTaggedEncoder[A] {
  def encoder: Encoder.AsObject[A]
}

object DerivedTaggedEncoder {

  implicit def higherPriorityListEncoder[A](implicit encoder: Encoder[A]): Encoder[List[A]] = Encoder.encodeList

  implicit def generic[A, H](implicit
    gen: LabelledGeneric.Aux[A, H],
    derivedEncoder: Lazy[DerivedTaggedEncoder[H]],
  ): DerivedTaggedEncoder[A] = new DerivedTaggedEncoder[A] {
    def encoder: Encoder.AsObject[A] = Encoder.AsObject.instance { a =>
      val obj = derivedEncoder.value.encoder.encodeObject(gen.to(a))
      JsonObject(obj.toList: _*)
    }
  }

  implicit def hnil: DerivedTaggedEncoder[HNil] = new DerivedTaggedEncoder[HNil] {
    def encoder: Encoder.AsObject[HNil] = Encoder.AsObject.instance(_ => JsonObject.empty)
  }

  implicit def hcons[K <: Symbol, H, T <: HList](implicit
    key: Witness.Aux[K],
    headEncoder: Lazy[Encoder[H]],
    tailEncoder: DerivedTaggedEncoder[T]
  ): DerivedTaggedEncoder[FieldType[K, H] :++: T] = new DerivedTaggedEncoder[FieldType[K, H] :++: T] {
    def encoder: Encoder.AsObject[FieldType[K, H] :++: T] = Encoder.AsObject.instance[FieldType[K, H] :++: T] {
      case head :++: tail =>
        val obj = (key.value.name, headEncoder.value(head))
        val fields = obj :: tailEncoder.encoder.encodeObject(tail).toList
        JsonObject(fields: _*)
    }
  }

  implicit def cnil: DerivedTaggedEncoder[CNil] = new DerivedTaggedEncoder[CNil] {
    def encoder: Encoder.AsObject[CNil] = Encoder.AsObject.instance(_ => JsonObject.empty)
  }

  implicit def ccons[K <: Symbol, H, T <: Coproduct](implicit
    key: Witness.Aux[K],
    headEncoder: Lazy[DerivedTaggedEncoder[H]],
    tailEncoder: DerivedTaggedEncoder[T]
  ): DerivedTaggedEncoder[FieldType[K, H] :+: T] = new DerivedTaggedEncoder[FieldType[K, H] :+: T] {
    def encoder: Encoder.AsObject[FieldType[K, H] :+: T] = Encoder.AsObject.instance {
      case Inl(head) =>
        val tag = ("tag", key.value.name.asJson)
        val obj = headEncoder.value.encoder.encodeObject(head).toList
        JsonObject(tag :: obj: _*)
      case Inr(tail) => tailEncoder.encoder.encodeObject(tail)
    }
  }
}

trait DerivedTaggedDecoder[A] {
  def decoder: Decoder[A]
}

object DerivedTaggedDecoder {

  implicit def generic[A, H](implicit
    gen: LabelledGeneric.Aux[A, H],
    derivedDecoder: Lazy[DerivedTaggedDecoder[H]]
  ): DerivedTaggedDecoder[A] = new DerivedTaggedDecoder[A] {
    def decoder: Decoder[A] = derivedDecoder.value.decoder.map(gen.from)
  }

  implicit def hnil: DerivedTaggedDecoder[HNil] = new DerivedTaggedDecoder[HNil] {
    def decoder: Decoder[HNil] = Decoder.const(HNil)
  }

  implicit def hcons[K <: Symbol, H, T <: HList](implicit
    key: Witness.Aux[K],
    headDecoder: Lazy[Decoder[H]],
    tailDecoder: DerivedTaggedDecoder[T]
  ): DerivedTaggedDecoder[FieldType[K, H] :++: T] = new DerivedTaggedDecoder[FieldType[K, H] :++: T] {
    def decoder: Decoder[FieldType[K, H] :++: T] = Decoder.instance { cursor =>
      for {
        head <- cursor.get[H](key.value.name)(headDecoder.value)
        tail <- tailDecoder.decoder(cursor)
      } yield field[K](head) :: tail
    }
  }

  implicit def cnil: DerivedTaggedDecoder[CNil] = new DerivedTaggedDecoder[CNil] {
    def decoder: Decoder[CNil] =
      Decoder.failed(DecodingFailure("Expected a Coproduct, this should be unreachable", Nil))
  }

  implicit def ccons[K <: Symbol, H, T <: Coproduct](implicit
    key: Witness.Aux[K],
    headDecoder: Lazy[DerivedTaggedDecoder[H]],
    tailDecoder: DerivedTaggedDecoder[T]
  ): DerivedTaggedDecoder[FieldType[K, H] :+: T] = new DerivedTaggedDecoder[FieldType[K, H] :+: T] {
    def decoder: Decoder[FieldType[K, H] :+: T] = Decoder.instance { cursor =>
      cursor.get[String]("tag").flatMap {
        case tag if tag == key.value.name =>
          for {
            head <- cursor.as[H](headDecoder.value.decoder)
          } yield Inl(field[K](head))
        case _ =>
          tailDecoder.decoder(cursor).map(t => Inr(t))
      }
    }
  }
}
