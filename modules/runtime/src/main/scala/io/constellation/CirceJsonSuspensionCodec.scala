package io.constellation

import java.util.UUID

import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*

/** Circe JSON implementation of [[SuspensionCodec]].
  *
  * Serializes [[SuspendedExecution]] to/from JSON bytes using the codecs defined in
  * [[CustomJsonCodecs]].
  */
object CirceJsonSuspensionCodec extends SuspensionCodec with CustomJsonCodecs {

  given suspendedExecutionEncoder: Encoder[SuspendedExecution] = Encoder.instance { se =>
    Json.obj(
      "executionId"     -> se.executionId.toString.asJson,
      "structuralHash"  -> se.structuralHash.asJson,
      "resumptionCount" -> se.resumptionCount.asJson,
      "dagSpec"         -> se.dagSpec.asJson,
      "moduleOptions"   -> se.moduleOptions.asJson,
      "providedInputs"  -> se.providedInputs.asJson,
      "computedValues"  -> se.computedValues.map { case (k, v) => k.toString -> v }.asJson,
      "moduleStatuses"  -> se.moduleStatuses.map { case (k, v) => k.toString -> v }.asJson
    )
  }

  given suspendedExecutionDecoder: Decoder[SuspendedExecution] = Decoder.instance { c =>
    for {
      executionId       <- c.downField("executionId").as[String].map(UUID.fromString)
      structuralHash    <- c.downField("structuralHash").as[String]
      resumptionCount   <- c.downField("resumptionCount").as[Int]
      dagSpec           <- c.downField("dagSpec").as[DagSpec]
      moduleOptions     <- c.downField("moduleOptions").as[Map[UUID, ModuleCallOptions]]
      providedInputs    <- c.downField("providedInputs").as[Map[String, CValue]]
      computedValuesRaw <- c.downField("computedValues").as[Map[String, CValue]]
      moduleStatusesRaw <- c.downField("moduleStatuses").as[Map[String, String]]
    } yield SuspendedExecution(
      executionId = executionId,
      structuralHash = structuralHash,
      resumptionCount = resumptionCount,
      dagSpec = dagSpec,
      moduleOptions = moduleOptions,
      providedInputs = providedInputs,
      computedValues = computedValuesRaw.map { case (k, v) => UUID.fromString(k) -> v },
      moduleStatuses = moduleStatusesRaw.map { case (k, v) => UUID.fromString(k) -> v }
    )
  }

  def encode(suspended: SuspendedExecution): Either[CodecError, Array[Byte]] =
    try {
      val json = suspended.asJson(using suspendedExecutionEncoder)
      Right(json.noSpaces.getBytes("UTF-8"))
    } catch {
      case e: Exception =>
        Left(CodecError(s"Failed to encode SuspendedExecution: ${e.getMessage}", Some(e)))
    }

  def decode(bytes: Array[Byte]): Either[CodecError, SuspendedExecution] = {
    val jsonStr = new String(bytes, "UTF-8")
    parse(jsonStr) match {
      case Left(e) => Left(CodecError(s"Failed to parse JSON: ${e.getMessage}", Some(e)))
      case Right(json) =>
        json.as[SuspendedExecution](using suspendedExecutionDecoder) match {
          case Left(e) =>
            Left(CodecError(s"Failed to decode SuspendedExecution: ${e.getMessage}", Some(e)))
          case Right(se) => Right(se)
        }
    }
  }
}
