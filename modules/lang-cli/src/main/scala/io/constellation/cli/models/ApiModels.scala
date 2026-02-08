package io.constellation.cli.models

import io.circe.Decoder

/** Shared API response models for the CLI.
  *
  * These models are used across multiple commands to avoid duplication.
  */
object ApiModels:

  /** Module information returned by pipeline endpoints.
    */
  case class ModuleInfo(
      name: String,
      description: String,
      version: String,
      inputs: Map[String, String],
      outputs: Map[String, String]
  )

  object ModuleInfo:
    given Decoder[ModuleInfo] = Decoder.instance { c =>
      for
        name        <- c.downField("name").as[String]
        description <- c.downField("description").as[String]
        version     <- c.downField("version").as[String]
        inputs      <- c.downField("inputs").as[Map[String, String]]
        outputs     <- c.downField("outputs").as[Map[String, String]]
      yield ModuleInfo(name, description, version, inputs, outputs)
    }

  /** Pipeline detail response with full module information.
    */
  case class PipelineDetailResponse(
      structuralHash: String,
      syntacticHash: String,
      aliases: List[String],
      compiledAt: String,
      modules: List[ModuleInfo],
      declaredOutputs: List[String],
      inputSchema: Map[String, String],
      outputSchema: Map[String, String]
  )

  object PipelineDetailResponse:
    given Decoder[PipelineDetailResponse] = Decoder.instance { c =>
      for
        structuralHash <- c.downField("structuralHash").as[String]
        syntacticHash  <- c.downField("syntacticHash").as[Option[String]].map(_.getOrElse(""))
        aliases        <- c.downField("aliases").as[Option[List[String]]].map(_.getOrElse(Nil))
        compiledAt     <- c.downField("compiledAt").as[Option[String]].map(_.getOrElse(""))
        modules        <- c.downField("modules").as[List[ModuleInfo]]
        declaredOutputs <- c
          .downField("declaredOutputs")
          .as[Option[List[String]]]
          .map(_.getOrElse(Nil))
        inputSchema  <- c.downField("inputSchema").as[Map[String, String]]
        outputSchema <- c.downField("outputSchema").as[Map[String, String]]
      yield PipelineDetailResponse(
        structuralHash,
        syntacticHash,
        aliases,
        compiledAt,
        modules,
        declaredOutputs,
        inputSchema,
        outputSchema
      )
    }

  /** Pipeline summary for list operations.
    */
  case class PipelineSummary(
      structuralHash: String,
      syntacticHash: String,
      aliases: List[String],
      compiledAt: String,
      moduleCount: Int,
      declaredOutputs: List[String]
  )

  object PipelineSummary:
    given Decoder[PipelineSummary] = Decoder.instance { c =>
      for
        structuralHash <- c.downField("structuralHash").as[String]
        syntacticHash  <- c.downField("syntacticHash").as[String]
        aliases        <- c.downField("aliases").as[Option[List[String]]].map(_.getOrElse(Nil))
        compiledAt     <- c.downField("compiledAt").as[String]
        moduleCount    <- c.downField("moduleCount").as[Int]
        declaredOutputs <- c
          .downField("declaredOutputs")
          .as[Option[List[String]]]
          .map(_.getOrElse(Nil))
      yield PipelineSummary(
        structuralHash,
        syntacticHash,
        aliases,
        compiledAt,
        moduleCount,
        declaredOutputs
      )
    }

  /** Execution summary for listing suspended executions.
    */
  case class ExecutionSummary(
      executionId: String,
      structuralHash: String,
      resumptionCount: Int,
      missingInputs: Map[String, String],
      createdAt: String
  )

  object ExecutionSummary:
    given Decoder[ExecutionSummary] = Decoder.instance { c =>
      for
        executionId     <- c.downField("executionId").as[String]
        structuralHash  <- c.downField("structuralHash").as[String]
        resumptionCount <- c.downField("resumptionCount").as[Int]
        missingInputs   <- c.downField("missingInputs").as[Map[String, String]]
        createdAt       <- c.downField("createdAt").as[String]
      yield ExecutionSummary(executionId, structuralHash, resumptionCount, missingInputs, createdAt)
    }
