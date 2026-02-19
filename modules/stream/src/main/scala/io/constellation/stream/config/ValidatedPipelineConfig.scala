package io.constellation.stream.config

import cats.effect.IO

import fs2.{Pipe, Stream}

import io.constellation.CValue

/** A fully validated pipeline config with resolved connector streams and pipes.
  *
  * Only created by successful validation via `PipelineConfigValidator.validate`.
  */
final case class ValidatedPipelineConfig(
    resolvedSources: Map[String, Stream[IO, CValue]],
    resolvedSinks: Map[String, Pipe[IO, CValue, Unit]],
    resolvedDlq: Option[Pipe[IO, CValue, Unit]]
)
