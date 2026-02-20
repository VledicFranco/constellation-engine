package io.constellation.stream

import java.util.UUID

import cats.effect.{Deferred, IO}
import cats.implicits.*

import fs2.Stream

import io.constellation.*
import io.constellation.stream.config.*
import io.constellation.stream.connector.*
import io.constellation.stream.error.StreamErrorStrategy
import io.constellation.stream.join.JoinStrategy

/** Compiles a DagSpec into an fs2 Stream graph for continuous execution.
  *
  * The StreamCompiler is the interpreter functor that transforms a static DAG specification into a
  * running stream graph where:
  *   - Source nodes are bound to `Stream[IO, CValue]` from connectors
  *   - Module nodes become `evalMap` / `parEvalMap` stages
  *   - Inline transforms become `map` stages
  *   - Merge nodes use join strategies (zip, combineLatest)
  *   - Sink nodes consume the stream via `Pipe[IO, CValue, Unit]`
  */
object StreamCompiler {

  /** Wire a DagSpec into a StreamGraph using the provided connectors and options.
    *
    * @param dagSpec
    *   The compiled DAG specification
    * @param registry
    *   Connector registry providing sources and sinks
    * @param modules
    *   Map of module UUID to their execution function
    * @param options
    *   Stream configuration options
    * @param errorStrategy
    *   Per-element error handling strategy
    * @param joinStrategy
    *   Default join strategy for merge points
    * @return
    *   A StreamGraph ready for deployment
    */
  def wire(
      dagSpec: DagSpec,
      registry: ConnectorRegistry,
      modules: Map[UUID, CValue => IO[CValue]],
      options: StreamOptions = StreamOptions(),
      errorStrategy: StreamErrorStrategy = StreamErrorStrategy.Log,
      joinStrategy: JoinStrategy = JoinStrategy.CombineLatest
  ): IO[StreamGraph] =
    for {
      metrics <-
        if options.metricsEnabled then StreamMetrics.create else IO.pure(StreamMetrics.noop)
      shutdown <- Deferred[IO, Either[Throwable, Unit]]
      graph <- buildGraph(
        dagSpec,
        registry,
        modules,
        options,
        errorStrategy,
        joinStrategy,
        metrics,
        shutdown
      )
    } yield graph

  /** Wire a DagSpec into a StreamGraph using a pipeline configuration.
    *
    * Validates the config against the DAG and registry, resolves all connector bindings, then
    * delegates to the core wiring logic.
    */
  def wireWithConfig(
      dagSpec: DagSpec,
      config: StreamPipelineConfig,
      registry: ConnectorRegistry,
      modules: Map[UUID, CValue => IO[CValue]],
      errorStrategy: StreamErrorStrategy = StreamErrorStrategy.Log,
      joinStrategy: JoinStrategy = JoinStrategy.CombineLatest
  ): IO[StreamGraph] = {
    // Identify source and sink names from the DAG
    val moduleOutputDataIds = dagSpec.outEdges.map(_._2)
    val sourceDataIds       = dagSpec.data.keySet -- moduleOutputDataIds
    val sourceNames = sourceDataIds.flatMap { dataId =>
      dagSpec.data.get(dataId).map { dns =>
        dns.nicknames.values.headOption.getOrElse(dns.name)
      }
    }
    val sinkNames = dagSpec.outputBindings.keys.toSet

    PipelineConfigValidator.validate(config, sourceNames, sinkNames, registry) match {
      case Left(errors) =>
        IO.raiseError(
          new IllegalArgumentException(
            s"Pipeline config validation failed: ${errors.map(_.message).mkString("; ")}"
          )
        )
      case Right(validated) =>
        // Build a temporary registry with resolved connectors
        val resolvedBuilder = validated.resolvedSources.foldLeft(ConnectorRegistry.builder) {
          case (b, (srcName, resolvedStream)) =>
            val src = new SourceConnector {
              def name: String                                                 = srcName
              def typeName: String                                             = "resolved"
              def stream(config: ValidatedConnectorConfig): Stream[IO, CValue] = resolvedStream
            }
            b.source(srcName, src)
        }
        val resolvedRegistry = validated.resolvedSinks
          .foldLeft(resolvedBuilder) { case (b, (snkName, resolvedPipe)) =>
            val snk = new SinkConnector {
              def name: String                                                       = snkName
              def typeName: String                                                   = "resolved"
              def pipe(config: ValidatedConnectorConfig): fs2.Pipe[IO, CValue, Unit] = resolvedPipe
            }
            b.sink(snkName, snk)
          }
          .build

        wire(dagSpec, resolvedRegistry, modules, StreamOptions(), errorStrategy, joinStrategy)
    }
  }

  private def buildGraph(
      dagSpec: DagSpec,
      registry: ConnectorRegistry,
      modules: Map[UUID, CValue => IO[CValue]],
      options: StreamOptions,
      errorStrategy: StreamErrorStrategy,
      joinStrategy: JoinStrategy,
      metrics: StreamMetrics,
      shutdownSignal: Deferred[IO, Either[Throwable, Unit]]
  ): IO[StreamGraph] = {
    // Identify source nodes (data nodes with no incoming edges from module outputs)
    val moduleOutputDataIds = dagSpec.outEdges.map(_._2)
    val sourceDataIds       = dagSpec.data.keySet -- moduleOutputDataIds

    // Identify sink nodes (declared outputs)
    val sinkDataIds = dagSpec.outputBindings.values.toSet

    // Build topologically-sorted node processing order
    val sortedModuleIds = topologicalSort(dagSpec)

    // Build streams for each source
    val sourceStreams: Map[UUID, Stream[IO, CValue]] = sourceDataIds.flatMap { dataId =>
      dagSpec.data.get(dataId).map { dataNodeSpec =>
        val sourceName = dataNodeSpec.nicknames.values.headOption.getOrElse(dataNodeSpec.name)
        val source     = registry.getSource(sourceName)
        dataId -> source.map(_.stream(ValidatedConnectorConfig.empty)).getOrElse(Stream.empty)
      }
    }.toMap

    // Wire each module node as an evalMap stage
    val moduleStreams = sortedModuleIds.foldLeft(sourceStreams) { (streams, moduleId) =>
      val moduleSpec = dagSpec.modules(moduleId)
      val moduleName = moduleSpec.metadata.name

      // Find input data nodes for this module
      val inputEdges   = dagSpec.inEdges.filter(_._2 == moduleId)
      val inputDataIds = inputEdges.map(_._1)

      // Find output data nodes for this module
      val outputEdges   = dagSpec.outEdges.filter(_._1 == moduleId)
      val outputDataIds = outputEdges.map(_._2)

      // Get module function
      val moduleFn = modules.get(moduleId)

      // Fan-in guard: warn when multiple inputs are present (fan-in is deferred to a future RFC)
      if inputDataIds.size > 1 then
        System.err.println(
          s"[WARN] StreamCompiler: Module '$moduleName' ($moduleId) has ${inputDataIds.size} inputs; only first used (fan-in is deferred)"
        )

      // For each output data node, create a stream that:
      // 1. Takes input from upstream data nodes
      // 2. Applies the module function
      // 3. Applies error handling
      val newStreams = outputDataIds.flatMap { outDataId =>
        moduleFn.map { fn =>
          val inputStream = inputDataIds.headOption
            .flatMap(id => streams.get(id))
            .getOrElse(Stream.empty)

          val processed = inputStream.evalMap { input =>
            val wrapped = errorStrategy match {
              case StreamErrorStrategy.Skip =>
                fn(input).handleError(_ => CValue.CString(""))
              case StreamErrorStrategy.Log =>
                fn(input).handleErrorWith { err =>
                  metrics.recordError(moduleName) *>
                    IO.pure(CValue.CString(s"error: ${err.getMessage}"))
                }
              case StreamErrorStrategy.Propagate =>
                fn(input)
              case StreamErrorStrategy.Dlq =>
                fn(input).handleErrorWith { err =>
                  metrics.recordDlq(moduleName) *>
                    IO.pure(CValue.CString(s"dlq: ${err.getMessage}"))
                }
            }
            wrapped <* metrics.recordElement(moduleName)
          }

          outDataId -> processed
        }
      }.toMap

      streams ++ newStreams
    }

    // Connect sinks
    val sinkStreams = sinkDataIds.flatMap { dataId =>
      dagSpec.data.get(dataId).flatMap { dataNodeSpec =>
        val sinkName = dagSpec.outputBindings
          .find(_._2 == dataId)
          .map(_._1)
          .getOrElse(dataNodeSpec.name)
        val sink = registry.getSink(sinkName)
        moduleStreams.get(dataId).map { stream =>
          sink match {
            case Some(s) => stream.through(s.pipe(ValidatedConnectorConfig.empty)).drain
            case None    => stream.drain
          }
        }
      }
    }

    // Compose all sink streams into a single graph
    val composedStream: Stream[IO, Unit] =
      if sinkStreams.isEmpty then Stream.empty
      else
        sinkStreams.toList match {
          case single :: Nil => single
          case multiple      => multiple.reduce(_ merge _)
        }

    IO.pure(
      StreamGraph(
        stream = composedStream.interruptWhen(shutdownSignal),
        metrics = metrics,
        shutdown = shutdownSignal.complete(Right(())).void
      )
    )
  }

  /** Topological sort of module nodes in the DAG. */
  private def topologicalSort(dagSpec: DagSpec): List[UUID] = {
    // Build adjacency: module A -> module B if A's output is B's input
    val moduleOrder = for {
      (outModId, outDataId) <- dagSpec.outEdges.toList
      (inDataId, inModId)   <- dagSpec.inEdges.toList
      if outDataId == inDataId
    } yield outModId -> inModId

    val allModuleIds = dagSpec.modules.keys.toSet
    val adj          = moduleOrder.groupMap(_._1)(_._2).withDefaultValue(Nil)

    var visited = Set.empty[UUID]
    var result  = List.empty[UUID]

    def dfs(id: UUID): Unit =
      if !visited.contains(id) then {
        visited += id
        adj(id).foreach(dfs)
        result = id :: result
      }

    allModuleIds.foreach(dfs)
    result
  }
}
