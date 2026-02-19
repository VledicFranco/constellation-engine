package io.constellation.stream.config

import cats.effect.IO

import fs2.{Pipe, Stream}

import io.constellation.CValue
import io.constellation.stream.connector.*

/** Validates a StreamPipelineConfig against a DagSpec and ConnectorRegistry.
  *
  * Checks that all DAG source/sink nodes have bindings, that binding connector types exist
  * in the registry, and that binding properties validate against connector schemas.
  */
object PipelineConfigValidator {

  /** Validate pipeline config and resolve all connector bindings.
    *
    * @param config
    *   The pipeline configuration with source/sink bindings
    * @param sourceNames
    *   Source node names from the DAG that need bindings
    * @param sinkNames
    *   Sink node names from the DAG that need bindings
    * @param registry
    *   The connector registry to resolve connector types against
    * @return
    *   Right(ValidatedPipelineConfig) with resolved streams/pipes, or Left(errors)
    */
  def validate(
      config: StreamPipelineConfig,
      sourceNames: Set[String],
      sinkNames: Set[String],
      registry: ConnectorRegistry
  ): Either[List[ConfigValidationError], ValidatedPipelineConfig] = {
    val unboundSources = sourceNames.filterNot(config.sourceBindings.contains).toList
      .map(ConfigValidationError.UnboundSource.apply)

    val unboundSinks = sinkNames.filterNot(config.sinkBindings.contains).toList
      .map(ConfigValidationError.UnboundSink.apply)

    val (sourceErrors, resolvedSources) = resolveSourceBindings(config.sourceBindings, registry)
    val (sinkErrors, resolvedSinks)     = resolveSinkBindings(config.sinkBindings, registry)
    val (dlqErrors, resolvedDlq)        = resolveDlq(config.dlq, registry)

    val allErrors = unboundSources ++ unboundSinks ++ sourceErrors ++ sinkErrors ++ dlqErrors

    if (allErrors.nonEmpty) Left(allErrors)
    else Right(ValidatedPipelineConfig(resolvedSources, resolvedSinks, resolvedDlq))
  }

  private def resolveSourceBindings(
      bindings: Map[String, SourceBinding],
      registry: ConnectorRegistry
  ): (List[ConfigValidationError], Map[String, Stream[IO, CValue]]) = {
    val errors  = List.newBuilder[ConfigValidationError]
    val sources = Map.newBuilder[String, Stream[IO, CValue]]

    bindings.foreach { case (bindingName, binding) =>
      registry.getSource(binding.connectorType) match {
        case None =>
          // Try to find by typeName across all registered sources
          registry.allSources.values.find(_.typeName == binding.connectorType) match {
            case None =>
              errors += ConfigValidationError.UnknownConnectorType(bindingName, binding.connectorType)
            case Some(connector) =>
              resolveSourceConnector(bindingName, binding, connector) match {
                case Left(errs)    => errors ++= errs
                case Right(stream) => sources += (bindingName -> stream)
              }
          }
        case Some(connector) =>
          resolveSourceConnector(bindingName, binding, connector) match {
            case Left(errs)    => errors ++= errs
            case Right(stream) => sources += (bindingName -> stream)
          }
      }
    }

    (errors.result(), sources.result())
  }

  private def resolveSourceConnector(
      bindingName: String,
      binding: SourceBinding,
      connector: SourceConnector
  ): Either[List[ConfigValidationError], Stream[IO, CValue]] = {
    val connConfig = ConnectorConfig(binding.properties)
    connConfig.validate(connector.configSchema) match {
      case Left(configErrors) =>
        Left(List(ConfigValidationError.ConnectorConfigErrors(bindingName, configErrors)))
      case Right(validated) =>
        Right(connector.stream(validated))
    }
  }

  private def resolveSinkBindings(
      bindings: Map[String, SinkBinding],
      registry: ConnectorRegistry
  ): (List[ConfigValidationError], Map[String, Pipe[IO, CValue, Unit]]) = {
    val errors = List.newBuilder[ConfigValidationError]
    val sinks  = Map.newBuilder[String, Pipe[IO, CValue, Unit]]

    bindings.foreach { case (bindingName, binding) =>
      registry.getSink(binding.connectorType) match {
        case None =>
          registry.allSinks.values.find(_.typeName == binding.connectorType) match {
            case None =>
              errors += ConfigValidationError.UnknownConnectorType(bindingName, binding.connectorType)
            case Some(connector) =>
              resolveSinkConnector(bindingName, binding, connector) match {
                case Left(errs)  => errors ++= errs
                case Right(pipe) => sinks += (bindingName -> pipe)
              }
          }
        case Some(connector) =>
          resolveSinkConnector(bindingName, binding, connector) match {
            case Left(errs)  => errors ++= errs
            case Right(pipe) => sinks += (bindingName -> pipe)
          }
      }
    }

    (errors.result(), sinks.result())
  }

  private def resolveSinkConnector(
      bindingName: String,
      binding: SinkBinding,
      connector: SinkConnector
  ): Either[List[ConfigValidationError], Pipe[IO, CValue, Unit]] = {
    val connConfig = ConnectorConfig(binding.properties)
    connConfig.validate(connector.configSchema) match {
      case Left(configErrors) =>
        Left(List(ConfigValidationError.ConnectorConfigErrors(bindingName, configErrors)))
      case Right(validated) =>
        Right(connector.pipe(validated))
    }
  }

  private def resolveDlq(
      dlq: Option[SinkBinding],
      registry: ConnectorRegistry
  ): (List[ConfigValidationError], Option[Pipe[IO, CValue, Unit]]) =
    dlq match {
      case None => (Nil, None)
      case Some(binding) =>
        registry.getSink(binding.connectorType) match {
          case None =>
            registry.allSinks.values.find(_.typeName == binding.connectorType) match {
              case None =>
                (List(ConfigValidationError.UnknownConnectorType("dlq", binding.connectorType)), None)
              case Some(connector) =>
                resolveSinkConnector("dlq", binding, connector) match {
                  case Left(errs)  => (errs, None)
                  case Right(pipe) => (Nil, Some(pipe))
                }
            }
          case Some(connector) =>
            resolveSinkConnector("dlq", binding, connector) match {
              case Left(errs)  => (errs, None)
              case Right(pipe) => (Nil, Some(pipe))
            }
        }
    }
}
