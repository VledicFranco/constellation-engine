package io.constellation

import cats.Eval
import cats.syntax.all.*
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
    case CType.CString  => Json.obj("tag" -> "CString".asJson)
    case CType.CInt     => Json.obj("tag" -> "CInt".asJson)
    case CType.CFloat   => Json.obj("tag" -> "CFloat".asJson)
    case CType.CBoolean => Json.obj("tag" -> "CBoolean".asJson)
    case CType.CList(valuesType) =>
      Json.obj("tag" -> "CList".asJson, "valuesType" -> valuesType.asJson)
    case CType.CMap(keysType, valuesType) =>
      Json.obj(
        "tag"        -> "CMap".asJson,
        "keysType"   -> keysType.asJson,
        "valuesType" -> valuesType.asJson
      )
    case CType.CProduct(structure) =>
      Json.obj("tag" -> "CProduct".asJson, "structure" -> structure.asJson)
    case CType.CUnion(structure) =>
      Json.obj("tag" -> "CUnion".asJson, "structure" -> structure.asJson)
    case CType.COptional(innerType) =>
      Json.obj("tag" -> "COptional".asJson, "innerType" -> innerType.asJson)
  }

  given ctypeDecoder: Decoder[CType] = Decoder.instance { c =>
    c.downField("tag").as[String].flatMap {
      case "CString"  => Right(CType.CString)
      case "CInt"     => Right(CType.CInt)
      case "CFloat"   => Right(CType.CFloat)
      case "CBoolean" => Right(CType.CBoolean)
      case "CList"    => c.downField("valuesType").as[CType].map(CType.CList.apply)
      case "CMap" =>
        for {
          keysType   <- c.downField("keysType").as[CType]
          valuesType <- c.downField("valuesType").as[CType]
        } yield CType.CMap(keysType, valuesType)
      case "CProduct"  => c.downField("structure").as[Map[String, CType]].map(CType.CProduct.apply)
      case "CUnion"    => c.downField("structure").as[Map[String, CType]].map(CType.CUnion.apply)
      case "COptional" => c.downField("innerType").as[CType].map(CType.COptional.apply)
      case other       => Left(io.circe.DecodingFailure(s"Unknown CType tag: $other", c.history))
    }
  }

  // Module.Status codecs with tagged union format
  given moduleStatusEncoder: Encoder[Module.Status] = Encoder.instance {
    case Module.Status.Unfired =>
      Json.obj("tag" -> "Unfired".asJson)
    case Module.Status.Fired(latency, context) =>
      Json.obj(
        "tag"     -> "Fired".asJson,
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
      case "Fired" =>
        for {
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

  // CValue codecs with tagged union format
  given cvalueEncoder: Encoder[CValue] = Encoder.instance {
    case CValue.CString(value)   => Json.obj("tag" -> "CString".asJson, "value" -> value.asJson)
    case CValue.CInt(value)      => Json.obj("tag" -> "CInt".asJson, "value" -> value.asJson)
    case CValue.CFloat(value)    => Json.obj("tag" -> "CFloat".asJson, "value" -> value.asJson)
    case CValue.CBoolean(value)  => Json.obj("tag" -> "CBoolean".asJson, "value" -> value.asJson)
    case CValue.CList(value, subtype) =>
      Json.obj("tag" -> "CList".asJson, "value" -> value.asJson, "subtype" -> subtype.asJson)
    case CValue.CMap(value, keysType, valuesType) =>
      val pairs = value.map { case (k, v) => Json.obj("key" -> k.asJson, "value" -> v.asJson) }
      Json.obj(
        "tag" -> "CMap".asJson,
        "value" -> pairs.asJson,
        "keysType" -> keysType.asJson,
        "valuesType" -> valuesType.asJson
      )
    case CValue.CProduct(value, structure) =>
      Json.obj("tag" -> "CProduct".asJson, "value" -> value.asJson, "structure" -> structure.asJson)
    case CValue.CUnion(value, structure, tag) =>
      Json.obj(
        "tag" -> "CUnion".asJson,
        "value" -> value.asJson,
        "structure" -> structure.asJson,
        "unionTag" -> tag.asJson
      )
    case CValue.CSome(value, innerType) =>
      Json.obj("tag" -> "CSome".asJson, "value" -> value.asJson, "innerType" -> innerType.asJson)
    case CValue.CNone(innerType) =>
      Json.obj("tag" -> "CNone".asJson, "innerType" -> innerType.asJson)
  }

  given cvalueDecoder: Decoder[CValue] = Decoder.instance { c =>
    c.downField("tag").as[String].flatMap {
      case "CString"  => c.downField("value").as[String].map(CValue.CString.apply)
      case "CInt"     => c.downField("value").as[Long].map(CValue.CInt.apply)
      case "CFloat"   => c.downField("value").as[Double].map(CValue.CFloat.apply)
      case "CBoolean" => c.downField("value").as[Boolean].map(CValue.CBoolean.apply)
      case "CList" =>
        for {
          value   <- c.downField("value").as[Vector[CValue]]
          subtype <- c.downField("subtype").as[CType]
        } yield CValue.CList(value, subtype)
      case "CMap" =>
        for {
          pairs      <- c.downField("value").as[Vector[Json]]
          keysType   <- c.downField("keysType").as[CType]
          valuesType <- c.downField("valuesType").as[CType]
          decoded <- pairs.traverse { pairJson =>
            val cursor = pairJson.hcursor
            for {
              k <- cursor.downField("key").as[CValue]
              v <- cursor.downField("value").as[CValue]
            } yield (k, v)
          }
        } yield CValue.CMap(decoded, keysType, valuesType)
      case "CProduct" =>
        for {
          value     <- c.downField("value").as[Map[String, CValue]]
          structure <- c.downField("structure").as[Map[String, CType]]
        } yield CValue.CProduct(value, structure)
      case "CUnion" =>
        for {
          value     <- c.downField("value").as[CValue]
          structure <- c.downField("structure").as[Map[String, CType]]
          tag       <- c.downField("unionTag").as[String]
        } yield CValue.CUnion(value, structure, tag)
      case "CSome" =>
        for {
          value     <- c.downField("value").as[CValue]
          innerType <- c.downField("innerType").as[CType]
        } yield CValue.CSome(value, innerType)
      case "CNone" =>
        c.downField("innerType").as[CType].map(CValue.CNone.apply)
      case other => Left(io.circe.DecodingFailure(s"Unknown CValue tag: $other", c.history))
    }
  }

  // ModuleCallOptions codec
  given moduleCallOptionsEncoder: Encoder[ModuleCallOptions] = Encoder.instance { opts =>
    Json.obj(
      "retry" -> opts.retry.asJson,
      "timeoutMs" -> opts.timeoutMs.asJson,
      "delayMs" -> opts.delayMs.asJson,
      "backoff" -> opts.backoff.asJson,
      "cacheMs" -> opts.cacheMs.asJson,
      "cacheBackend" -> opts.cacheBackend.asJson,
      "throttleCount" -> opts.throttleCount.asJson,
      "throttlePerMs" -> opts.throttlePerMs.asJson,
      "concurrency" -> opts.concurrency.asJson,
      "onError" -> opts.onError.asJson,
      "lazyEval" -> opts.lazyEval.asJson,
      "priority" -> opts.priority.asJson
    )
  }

  given moduleCallOptionsDecoder: Decoder[ModuleCallOptions] = Decoder.instance { c =>
    for {
      retry         <- c.downField("retry").as[Option[Int]]
      timeoutMs     <- c.downField("timeoutMs").as[Option[Long]]
      delayMs       <- c.downField("delayMs").as[Option[Long]]
      backoff       <- c.downField("backoff").as[Option[String]]
      cacheMs       <- c.downField("cacheMs").as[Option[Long]]
      cacheBackend  <- c.downField("cacheBackend").as[Option[String]]
      throttleCount <- c.downField("throttleCount").as[Option[Int]]
      throttlePerMs <- c.downField("throttlePerMs").as[Option[Long]]
      concurrency   <- c.downField("concurrency").as[Option[Int]]
      onError       <- c.downField("onError").as[Option[String]]
      lazyEval      <- c.downField("lazyEval").as[Option[Boolean]]
      priority      <- c.downField("priority").as[Option[Int]]
    } yield ModuleCallOptions(retry, timeoutMs, delayMs, backoff, cacheMs, cacheBackend,
      throttleCount, throttlePerMs, concurrency, onError, lazyEval, priority)
  }

  // ComponentMetadata codec
  given componentMetadataEncoder: Encoder[ComponentMetadata] = Encoder.instance { m =>
    Json.obj(
      "name" -> m.name.asJson,
      "description" -> m.description.asJson,
      "tags" -> m.tags.asJson,
      "majorVersion" -> m.majorVersion.asJson,
      "minorVersion" -> m.minorVersion.asJson
    )
  }

  given componentMetadataDecoder: Decoder[ComponentMetadata] = Decoder.instance { c =>
    for {
      name         <- c.downField("name").as[String]
      description  <- c.downField("description").as[String]
      tags         <- c.downField("tags").as[List[String]]
      majorVersion <- c.downField("majorVersion").as[Int]
      minorVersion <- c.downField("minorVersion").as[Int]
    } yield ComponentMetadata(name, description, tags, majorVersion, minorVersion)
  }

  // ModuleConfig codec
  given moduleConfigEncoder: Encoder[ModuleConfig] = Encoder.instance { c =>
    Json.obj(
      "inputsTimeout" -> c.inputsTimeout.asJson,
      "moduleTimeout" -> c.moduleTimeout.asJson
    )
  }

  given moduleConfigDecoder: Decoder[ModuleConfig] = Decoder.instance { c =>
    for {
      inputsTimeout <- c.downField("inputsTimeout").as[FiniteDuration]
      moduleTimeout <- c.downField("moduleTimeout").as[FiniteDuration]
    } yield ModuleConfig(inputsTimeout, moduleTimeout)
  }

  // DataNodeSpec codec (without InlineTransform - closures are not serializable)
  given dataNodeSpecEncoder: Encoder[DataNodeSpec] = Encoder.instance { d =>
    val transformTag = d.inlineTransform.map(_.getClass.getSimpleName.stripSuffix("$"))
    Json.obj(
      "name" -> d.name.asJson,
      "nicknames" -> d.nicknames.map { case (k, v) => k.toString -> v }.asJson,
      "cType" -> d.cType.asJson,
      "inlineTransformType" -> transformTag.asJson,
      "transformInputs" -> d.transformInputs.map { case (k, v) => k -> v.toString }.asJson
    )
  }

  given dataNodeSpecDecoder: Decoder[DataNodeSpec] = Decoder.instance { c =>
    for {
      name      <- c.downField("name").as[String]
      nicknames <- c.downField("nicknames").as[Map[String, String]]
      cType     <- c.downField("cType").as[CType]
      transformInputsStr <- c.downField("transformInputs").as[Map[String, String]]
    } yield DataNodeSpec(
      name = name,
      nicknames = nicknames.map { case (k, v) => UUID.fromString(k) -> v },
      cType = cType,
      inlineTransform = None, // Closures cannot be deserialized
      transformInputs = transformInputsStr.map { case (k, v) => k -> UUID.fromString(v) }
    )
  }

  // ModuleNodeSpec codec
  given moduleNodeSpecEncoder: Encoder[ModuleNodeSpec] = Encoder.instance { m =>
    Json.obj(
      "metadata" -> m.metadata.asJson,
      "consumes" -> m.consumes.asJson,
      "produces" -> m.produces.asJson,
      "config" -> m.config.asJson,
      "definitionContext" -> m.definitionContext.asJson
    )
  }

  given moduleNodeSpecDecoder: Decoder[ModuleNodeSpec] = Decoder.instance { c =>
    for {
      metadata          <- c.downField("metadata").as[ComponentMetadata]
      consumes          <- c.downField("consumes").as[Map[String, CType]]
      produces          <- c.downField("produces").as[Map[String, CType]]
      config            <- c.downField("config").as[ModuleConfig]
      definitionContext  <- c.downField("definitionContext").as[Option[Map[String, Json]]]
    } yield ModuleNodeSpec(metadata, consumes, produces, config, definitionContext)
  }

  // DagSpec codec
  given dagSpecEncoder: Encoder[DagSpec] = Encoder.instance { d =>
    Json.obj(
      "metadata" -> d.metadata.asJson,
      "modules" -> d.modules.asJson,
      "data" -> d.data.asJson,
      "inEdges" -> d.inEdges.toList.map { case (a, b) => Json.arr(a.toString.asJson, b.toString.asJson) }.asJson,
      "outEdges" -> d.outEdges.toList.map { case (a, b) => Json.arr(a.toString.asJson, b.toString.asJson) }.asJson,
      "declaredOutputs" -> d.declaredOutputs.asJson,
      "outputBindings" -> d.outputBindings.map { case (k, v) => k -> v.toString }.asJson
    )
  }

  given dagSpecDecoder: Decoder[DagSpec] = Decoder.instance { c =>
    for {
      metadata <- c.downField("metadata").as[ComponentMetadata]
      modules  <- c.downField("modules").as[Map[UUID, ModuleNodeSpec]]
      data     <- c.downField("data").as[Map[UUID, DataNodeSpec]]
      inEdgesRaw  <- c.downField("inEdges").as[List[List[String]]]
      outEdgesRaw <- c.downField("outEdges").as[List[List[String]]]
      declaredOutputs <- c.downField("declaredOutputs").as[List[String]]
      outputBindingsRaw <- c.downField("outputBindings").as[Map[String, String]]
    } yield {
      val inEdges = inEdgesRaw.collect {
        case List(a, b) => (UUID.fromString(a), UUID.fromString(b))
      }.toSet
      val outEdges = outEdgesRaw.collect {
        case List(a, b) => (UUID.fromString(a), UUID.fromString(b))
      }.toSet
      val outputBindings = outputBindingsRaw.map { case (k, v) => k -> UUID.fromString(v) }
      DagSpec(metadata, modules, data, inEdges, outEdges, declaredOutputs, outputBindings)
    }
  }
}
