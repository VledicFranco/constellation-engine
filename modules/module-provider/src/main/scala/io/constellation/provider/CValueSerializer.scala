package io.constellation.provider

import io.constellation.CValue
import io.constellation.json.{cvalueEncoder, cvalueDecoder}

import io.circe.parser.decode
import io.circe.syntax.*

/** Serialization of CValue to/from bytes.
  *
  * v1 uses JSON-over-bytes via existing Circe codecs. Future versions may swap to MessagePack for
  * ~2.5x performance improvement. The trait boundary allows this swap without changing callers.
  */
trait CValueSerializer {
  def serialize(value: CValue): Either[String, Array[Byte]]
  def deserialize(bytes: Array[Byte]): Either[String, CValue]
}

/** JSON-over-bytes implementation using existing Circe codecs. */
object JsonCValueSerializer extends CValueSerializer {

  def serialize(value: CValue): Either[String, Array[Byte]] =
    Right(value.asJson.noSpaces.getBytes(java.nio.charset.StandardCharsets.UTF_8))

  def deserialize(bytes: Array[Byte]): Either[String, CValue] = {
    val json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
    decode[CValue](json).left.map(_.getMessage)
  }
}
