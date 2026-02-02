package io.constellation.cache

import io.circe.{Decoder, Encoder, parser}
import io.circe.syntax.*
import io.constellation.CValue
import io.constellation.json.given

import java.io.{
  ByteArrayInputStream,
  ByteArrayOutputStream,
  ObjectInputStream,
  ObjectOutputStream
}

/** Type class for serializing and deserializing cache values.
  *
  * Distributed cache backends (Memcached, Redis) need to convert values to/from bytes for network
  * transport. This type class provides pluggable serialization strategies.
  *
  * ==Built-in instances==
  *
  *   - `CacheSerde.cvalueSerde` - JSON-based serde for `CValue` using existing Circe codecs
  *   - `CacheSerde.mapCValueSerde` - JSON-based serde for `Map[String, CValue]`
  *   - `CacheSerde.javaSerde` - Java serialization fallback for any `Serializable` value
  *
  * ==Custom implementations==
  *
  * {{{
  * given mySerde: CacheSerde[MyType] = new CacheSerde[MyType] {
  *   def serialize(value: MyType): Array[Byte] = ...
  *   def deserialize(bytes: Array[Byte]): MyType = ...
  * }
  * }}}
  */
trait CacheSerde[A] {

  /** Serialize a value to bytes. */
  def serialize(value: A): Array[Byte]

  /** Deserialize bytes back to a value.
    *
    * @throws CacheSerdeException
    *   if deserialization fails
    */
  def deserialize(bytes: Array[Byte]): A
}

/** Exception thrown when cache serialization or deserialization fails. */
class CacheSerdeException(message: String, cause: Throwable = null)
    extends RuntimeException(message, cause)

object CacheSerde {

  /** JSON-based serde for CValue using existing Circe codecs.
    *
    * This is the preferred serde for constellation values as it produces human-readable JSON and
    * uses the battle-tested Circe codecs already defined in `CustomJsonCodecs`.
    */
  given cvalueSerde: CacheSerde[CValue] = new CacheSerde[CValue] {
    override def serialize(value: CValue): Array[Byte] =
      value.asJson.noSpaces.getBytes("UTF-8")

    override def deserialize(bytes: Array[Byte]): CValue = {
      val jsonStr = new String(bytes, "UTF-8")
      parser
        .decode[CValue](jsonStr)
        .fold(
          err => throw new CacheSerdeException(s"Failed to deserialize CValue: ${err.getMessage}", err),
          identity
        )
    }
  }

  /** JSON-based serde for Map[String, CValue]. */
  given mapCValueSerde: CacheSerde[Map[String, CValue]] = new CacheSerde[Map[String, CValue]] {
    override def serialize(value: Map[String, CValue]): Array[Byte] =
      value.asJson.noSpaces.getBytes("UTF-8")

    override def deserialize(bytes: Array[Byte]): Map[String, CValue] = {
      val jsonStr = new String(bytes, "UTF-8")
      parser
        .decode[Map[String, CValue]](jsonStr)
        .fold(
          err =>
            throw new CacheSerdeException(
              s"Failed to deserialize Map[String, CValue]: ${err.getMessage}",
              err
            ),
          identity
        )
    }
  }

  /** Java serialization fallback for any Serializable value.
    *
    * Uses standard Java ObjectOutputStream/ObjectInputStream. This works for any value that
    * implements `java.io.Serializable`, but produces opaque binary output and is slower than
    * JSON-based serdes.
    *
    * Use this as a fallback when no type-specific serde is available.
    */
  def javaSerde[A <: java.io.Serializable]: CacheSerde[A] = new CacheSerde[A] {
    override def serialize(value: A): Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      try {
        val oos = new ObjectOutputStream(baos)
        try {
          oos.writeObject(value)
          oos.flush()
          baos.toByteArray
        } finally oos.close()
      } catch {
        case e: Exception =>
          throw new CacheSerdeException(s"Java serialization failed: ${e.getMessage}", e)
      } finally baos.close()
    }

    override def deserialize(bytes: Array[Byte]): A = {
      val bais = new ByteArrayInputStream(bytes)
      try {
        val ois = new ObjectInputStream(bais)
        try ois.readObject().asInstanceOf[A]
        finally ois.close()
      } catch {
        case e: Exception =>
          throw new CacheSerdeException(s"Java deserialization failed: ${e.getMessage}", e)
      } finally bais.close()
    }
  }

  /** Serde for Any values.
    *
    * Attempts JSON serialization for CValue instances, falls back to Java serialization for other
    * types. This is used by `DistributedCacheBackend` where the generic type is erased to `Any`.
    */
  val anySerde: CacheSerde[Any] = new CacheSerde[Any] {
    private val javaFallback = javaSerde[java.io.Serializable]

    override def serialize(value: Any): Array[Byte] = value match {
      case cv: CValue => cvalueSerde.serialize(cv)
      case s: java.io.Serializable =>
        // Prefix with 0x01 to distinguish from JSON (which starts with '{' or '[')
        val bytes = javaFallback.serialize(s)
        Array[Byte](0x01) ++ bytes
      case other =>
        throw new CacheSerdeException(
          s"Cannot serialize value of type ${other.getClass.getName}: not Serializable"
        )
    }

    override def deserialize(bytes: Array[Byte]): Any = {
      if bytes.isEmpty then {
        throw new CacheSerdeException("Cannot deserialize empty byte array")
      }
      if bytes(0) == 0x01 then {
        // Java-serialized value (prefixed with 0x01)
        javaFallback.deserialize(bytes.drop(1))
      } else {
        // Try JSON (CValue) first
        try cvalueSerde.deserialize(bytes)
        catch {
          case _: CacheSerdeException =>
            // Fallback to Java deserialization
            javaFallback.deserialize(bytes)
        }
      }
    }
  }
}
