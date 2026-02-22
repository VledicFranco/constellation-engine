package io.constellation.stream

import java.util.UUID
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

import cats.effect.{Deferred, IO}
import cats.implicits.*

import io.constellation.*
import io.constellation.stream.config.*
import io.constellation.stream.connector.*
import io.constellation.stream.error.StreamErrorStrategy
import io.constellation.stream.join.JoinStrategy

import fs2.Stream
import fs2.concurrent.SignallingRef

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
      joinStrategy: JoinStrategy = JoinStrategy.CombineLatest,
      moduleOptions: Map[UUID, ModuleCallOptions] = Map.empty,
      batchModules: Map[UUID, List[CValue] => IO[List[Either[Throwable, CValue]]]] = Map.empty
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
        shutdown,
        moduleOptions,
        batchModules
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
      options: StreamOptions = StreamOptions(),
      errorStrategy: StreamErrorStrategy = StreamErrorStrategy.Log,
      joinStrategy: JoinStrategy = JoinStrategy.CombineLatest,
      moduleOptions: Map[UUID, ModuleCallOptions] = Map.empty,
      batchModules: Map[UUID, List[CValue] => IO[List[Either[Throwable, CValue]]]] = Map.empty
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

        wire(
          dagSpec,
          resolvedRegistry,
          modules,
          options,
          errorStrategy,
          joinStrategy,
          moduleOptions,
          batchModules
        )
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
      shutdownSignal: Deferred[IO, Either[Throwable, Unit]],
      moduleOptions: Map[UUID, ModuleCallOptions] = Map.empty,
      batchModules: Map[UUID, List[CValue] => IO[List[Either[Throwable, CValue]]]] = Map.empty
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

      // Build named input streams for fan-in join (field name from DataNodeSpec.name)
      val namedInputStreams: List[(String, Stream[IO, CValue])] = inputDataIds.toList.flatMap {
        id =>
          for {
            stream   <- streams.get(id)
            dataSpec <- dagSpec.data.get(id)
          } yield dataSpec.name -> stream
      }

      val inputStream: Stream[IO, CValue] = joinInputStreams(namedInputStreams, joinStrategy)

      // Detect collect pseudo-module
      val isCollect = moduleName == "collect" || moduleName == "stdlib.builtin.collect"

      val newStreams: Map[UUID, Stream[IO, CValue]] =
        if isCollect then {
          val opts      = moduleOptions.get(moduleId)
          val batchSize = opts.flatMap(_.batchSize).getOrElse(100)
          val batchMs   = opts.flatMap(_.batchTimeoutMs).getOrElse(1000L)
          val timeout   = FiniteDuration(batchMs, TimeUnit.MILLISECONDS)
          val elemType = inputDataIds.headOption
            .flatMap(id => dagSpec.data.get(id))
            .map(_.cType)
            .getOrElse(CType.CString)

          val collected = inputStream
            .groupWithin(batchSize, timeout)
            .map(chunk => CValue.CList(chunk.toVector, elemType))
            .evalTap(_ => metrics.recordElement(moduleName))

          outputDataIds.map(_ -> collected).toMap
        } else {
          // Standard module processing with batch support (RFC-034 Phase 1)
          val opts      = moduleOptions.get(moduleId)
          val batchSize = opts.flatMap(_.batchSize).getOrElse(1)
          val batchTimeout = opts
            .flatMap(_.batchTimeoutMs)
            .map(ms => FiniteDuration(ms, TimeUnit.MILLISECONDS))
            .getOrElse(FiniteDuration(1, TimeUnit.SECONDS))
          val batchFn = batchModules.get(moduleId)

          outputDataIds.flatMap { outDataId =>
            moduleFn.map { fn =>
              val processed = (batchFn, batchSize > 1) match {
                case (Some(bf), true) =>
                  // Batch path: group elements, call batch function, flatten results
                  inputStream
                    .groupWithin(batchSize, batchTimeout)
                    .evalMap { chunk =>
                      bf(chunk.toList).flatMap { results =>
                        results.traverse {
                          case Right(v) =>
                            metrics.recordElement(moduleName).as(Some(v))
                          case Left(err) =>
                            errorStrategy match {
                              case StreamErrorStrategy.Skip =>
                                metrics.recordError(moduleName).as(None)
                              case StreamErrorStrategy.Log =>
                                metrics.recordError(moduleName).as(
                                  Some(CValue.CString(s"error: ${safeMessage(err)}"))
                                )
                              case StreamErrorStrategy.Dlq =>
                                metrics.recordDlq(moduleName).as(
                                  Some(CValue.CString(s"dlq: ${safeMessage(err)}"))
                                )
                              case StreamErrorStrategy.Propagate =>
                                IO.raiseError(err)
                            }
                        }
                      }
                    }
                    .flatMap(results => Stream.emits(results.flatten))

                case _ =>
                  // Per-element path (unchanged)
                  errorStrategy match {
                    case StreamErrorStrategy.Skip =>
                      inputStream.evalMapFilter { input =>
                        (fn(input).map(Some(_)) <* metrics.recordElement(moduleName))
                          .handleError(_ => None)
                      }
                    case StreamErrorStrategy.Log =>
                      inputStream.evalMap { input =>
                        val wrapped = fn(input).handleErrorWith { err =>
                          metrics.recordError(moduleName) *>
                            IO.pure(CValue.CString(s"error: ${safeMessage(err)}"))
                        }
                        wrapped <* metrics.recordElement(moduleName)
                      }
                    case StreamErrorStrategy.Propagate =>
                      inputStream.evalMap { input =>
                        fn(input) <* metrics.recordElement(moduleName)
                      }
                    case StreamErrorStrategy.Dlq =>
                      inputStream.evalMap { input =>
                        val wrapped = fn(input).handleErrorWith { err =>
                          metrics.recordDlq(moduleName) *>
                            IO.pure(CValue.CString(s"dlq: ${safeMessage(err)}"))
                        }
                        wrapped <* metrics.recordElement(moduleName)
                      }
                  }
              }

              outDataId -> processed
            }
          }.toMap
        }

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

  private def safeMessage(e: Throwable): String =
    Option(e.getMessage).getOrElse(e.getClass.getSimpleName)

  /** Build a CProduct from a value map, deriving the type structure from each value's ctype. */
  private def makeCProduct(values: Map[String, CValue]): CValue.CProduct =
    CValue.CProduct(values, values.map { case (k, v) => k -> v.ctype })

  /** Dispatches to the correct join implementation based on strategy. Single-input passes through
    * as-is (no CProduct wrapping) — preserves existing single-input behavior exactly.
    */
  private def joinInputStreams(
      namedStreams: List[(String, Stream[IO, CValue])],
      strategy: JoinStrategy
  ): Stream[IO, CValue] =
    namedStreams match {
      case Nil                => Stream.empty
      case (_, single) :: Nil => single
      case multiple =>
        val names   = multiple.map(_._1)
        val streams = multiple.map(_._2)
        strategy match {
          case JoinStrategy.Zip            => zipAll(names, streams)
          case JoinStrategy.CombineLatest  => combineLatestAll(multiple)
          case JoinStrategy.Buffer(maxBuf) => zipAll(names, streams.map(_.buffer(maxBuf)))
        }
    }

  /** Zip N streams synchronously into CProduct emissions. Terminates when the shortest stream
    * terminates (standard zip semantics).
    */
  private def zipAll(
      names: List[String],
      streams: List[Stream[IO, CValue]]
  ): Stream[IO, CValue] =
    streams match {
      case Nil      => Stream.empty
      case s :: Nil => s.map(v => makeCProduct(Map(names.head -> v)))
      case first :: rest =>
        val zipped = rest.foldLeft(first.map(v => List(v))) { (acc, s) =>
          acc.zip(s).map { case (vs, v) => vs :+ v }
        }
        zipped.map(values => makeCProduct(names.zip(values).toMap))
    }

  /** CombineLatest: emit a CProduct snapshot whenever any input emits, once all inputs have
    * produced at least one value. Uses SignallingRef per input to hold latest values; merge
    * interleaves concurrent input streams without deadlock.
    */
  private def combineLatestAll(
      namedStreams: List[(String, Stream[IO, CValue])]
  ): Stream[IO, CValue] = {
    val n = namedStreams.size
    Stream
      .eval(namedStreams.traverse { case (name, _) =>
        SignallingRef.of[IO, Option[CValue]](None).map(name -> _)
      })
      .flatMap { refs =>
        val refMap = refs.toMap
        namedStreams
          .map { case (name, stream) =>
            stream.evalMapFilter { v =>
              for {
                _       <- refMap(name).set(Some(v))
                entries <- refs.traverse { case (n2, ref) => ref.get.map(n2 -> _) }
                resolved = entries.collect { case (n2, Some(v2)) => n2 -> v2 }
              } yield Option.when(resolved.size == n)(makeCProduct(resolved.toMap))
            }
          }
          .reduce(_ merge _)
      }
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
