package io.constellation

/** Error during suspension encoding/decoding. */
final case class CodecError(message: String, cause: Option[Throwable] = None)
    extends RuntimeException(message, cause.orNull)

/** Codec for serializing/deserializing [[SuspendedExecution]] snapshots.
  *
  * Implementations should handle the full [[SuspendedExecution]] graph including
  * CValue data, DagSpec topology, and module options.
  */
trait SuspensionCodec {

  /** Encode a SuspendedExecution to bytes. */
  def encode(suspended: SuspendedExecution): Either[CodecError, Array[Byte]]

  /** Decode a SuspendedExecution from bytes. */
  def decode(bytes: Array[Byte]): Either[CodecError, SuspendedExecution]
}
