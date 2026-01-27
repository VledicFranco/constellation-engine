package io.constellation

import cats.data.Validated.{Invalid, Valid}
import cats.data.{Validated, ValidatedNel}
import cats.effect.{Deferred, IO, Ref}
import cats.implicits.*
import cats.{Eval, Monoid}

import java.util.UUID
import scala.concurrent.TimeoutException

/** Stepped execution engine for debugging DAG pipelines. Allows batch-by-batch execution with state
  * inspection between steps.
  */
object SteppedExecution {

  /** Execution state for a node */
  enum NodeState:
    case Pending
    case Running
    case Completed(value: CValue, durationMs: Long)
    case Failed(error: Throwable)

  /** A batch of nodes that can be executed in parallel */
  case class ExecutionBatch(
      batchIndex: Int,
      moduleIds: List[UUID], // Modules in this batch
      dataIds: List[UUID]    // Data nodes produced by this batch
  )

  /** Session state for stepped execution */
  case class SessionState(
      sessionId: String,
      dagSpec: DagSpec,
      compiledModules: Map[UUID, Module.Uninitialized],
      syntheticModules: Map[UUID, Module.Uninitialized],
      inputs: Map[String, CValue],
      batches: List[ExecutionBatch],
      currentBatchIndex: Int,
      nodeStates: Map[UUID, NodeState],
      runtimeOpt: Option[Runtime],
      runnableModules: Map[UUID, Module.Runnable],
      startTime: Long
  )

  /** Compute execution batches using Kahn's algorithm for topological ordering. Groups modules by
    * dependency level - modules in the same level can run in parallel.
    */
  def computeBatches(dagSpec: DagSpec): List[ExecutionBatch] = {
    val modules  = dagSpec.modules
    val inEdges  = dagSpec.inEdges  // data -> module
    val outEdges = dagSpec.outEdges // module -> data

    // Build dependency graph: module depends on other modules whose outputs it consumes
    val moduleDependencies: Map[UUID, Set[UUID]] = modules.keys.map { moduleId =>
      // Find all data nodes this module consumes
      val consumedData = inEdges.collect { case (dataId, modId) if modId == moduleId => dataId }

      // Find modules that produce those data nodes
      val dependsOn = consumedData.flatMap { dataId =>
        outEdges.collect { case (modId, dId) if dId == dataId => modId }
      }

      moduleId -> dependsOn
    }.toMap

    // Compute in-degree (number of dependencies) for each module
    val inDegree = scala.collection.mutable.Map[UUID, Int]()
    modules.keys.foreach { moduleId =>
      inDegree(moduleId) = moduleDependencies.getOrElse(moduleId, Set.empty).size
    }

    // Build reverse adjacency (modules that depend on this module)
    val dependents = scala.collection.mutable.Map[UUID, Set[UUID]]().withDefaultValue(Set.empty)
    moduleDependencies.foreach { case (moduleId, deps) =>
      deps.foreach { depId =>
        dependents(depId) = dependents(depId) + moduleId
      }
    }

    // Kahn's algorithm to compute levels
    val batches    = scala.collection.mutable.ListBuffer[ExecutionBatch]()
    var remaining  = modules.keys.toSet
    var batchIndex = 0

    // First batch: Input data nodes (level 0)
    val inputDataIds = dagSpec.userInputDataNodes.keys.toList
    batches += ExecutionBatch(
      batchIndex = batchIndex,
      moduleIds = List.empty,
      dataIds = inputDataIds
    )
    batchIndex += 1

    // Subsequent batches: modules grouped by dependency level
    while remaining.nonEmpty do {
      // Find modules with no remaining dependencies (in-degree 0)
      val readyModules = remaining.filter(id => inDegree(id) == 0).toList

      if readyModules.isEmpty && remaining.nonEmpty then {
        throw new RuntimeException("Cycle detected in DAG")
      }

      if readyModules.nonEmpty then {
        // Find data nodes produced by these modules
        val producedData = readyModules.flatMap { moduleId =>
          outEdges.collect { case (modId, dataId) if modId == moduleId => dataId }
        }

        batches += ExecutionBatch(
          batchIndex = batchIndex,
          moduleIds = readyModules,
          dataIds = producedData
        )
        batchIndex += 1

        // Remove processed modules and update in-degrees
        remaining = remaining -- readyModules.toSet
        readyModules.foreach { moduleId =>
          dependents(moduleId).foreach { depId =>
            inDegree(depId) = inDegree(depId) - 1
          }
        }
      }
    }

    batches.toList
  }

  /** Create a new stepped execution session.
    */
  def createSession(
      sessionId: String,
      dagSpec: DagSpec,
      syntheticModules: Map[UUID, Module.Uninitialized],
      registeredModules: Map[UUID, Module.Uninitialized],
      inputs: Map[String, CValue]
  ): IO[SessionState] = IO {
    val batches = computeBatches(dagSpec)

    // Initialize all node states as pending
    val initialNodeStates =
      dagSpec.modules.keys.map(_ -> NodeState.Pending).toMap ++
        dagSpec.data.keys.map(_ -> NodeState.Pending).toMap

    SessionState(
      sessionId = sessionId,
      dagSpec = dagSpec,
      compiledModules = registeredModules,
      syntheticModules = syntheticModules,
      inputs = inputs,
      batches = batches,
      currentBatchIndex = 0,
      nodeStates = initialNodeStates,
      runtimeOpt = None,
      runnableModules = Map.empty,
      startTime = System.currentTimeMillis()
    )
  }

  /** Initialize the runtime for stepping (called before first step).
    */
  def initializeRuntime(session: SessionState): IO[SessionState] = {
    val dag     = session.dagSpec
    // Combine compiled modules with synthetic modules (branch, inline transforms, etc.)
    val allModules = session.compiledModules ++ session.syntheticModules

    for {
      // Validate inputs
      _ <- validateRunIO(dag, session.inputs)

      // Initialize modules and create data table
      modulesAndDataTable <- initModules(dag, allModules)
      (runnableList, dataTable) = modulesAndDataTable
      runnableMap               = runnableList.map(r => r.id -> r).toMap

      // Initialize state ref
      contextRef <- initState(dag)
      rt = Runtime(state = contextRef, table = dataTable)

      // Complete top-level data nodes with input values
      _ <- completeTopLevelDataNodes(dag, session.inputs, rt)

      // Mark input data nodes as completed
      topLevelNodeStates = dag.userInputDataNodes.map { case (dataId, spec) =>
        val nicknames = spec.nicknames.values.toList
        val value = session.inputs
          .collectFirst {
            case (name, cValue) if nicknames.contains(name) => cValue
          }
          .getOrElse(CValue.CString(""))
        dataId -> NodeState.Completed(value, 0L)
      }

      updatedNodeStates = session.nodeStates ++ topLevelNodeStates

    } yield session.copy(
      runtimeOpt = Some(rt),
      runnableModules = runnableMap,
      nodeStates = updatedNodeStates,
      currentBatchIndex = 1 // Skip batch 0 (input data nodes)
    )
  }

  /** Execute the next batch of modules. Returns the updated session state and whether execution is
    * complete.
    */
  def executeNextBatch(session: SessionState): IO[(SessionState, Boolean)] =
    if session.currentBatchIndex >= session.batches.length then {
      // Already complete
      IO.pure((session, true))
    } else {
      val batch = session.batches(session.currentBatchIndex)
      val rt = session.runtimeOpt.getOrElse(
        throw new RuntimeException("Runtime not initialized")
      )

      // Mark modules as running
      val runningStates      = batch.moduleIds.map(_ -> NodeState.Running).toMap
      val sessionWithRunning = session.copy(nodeStates = session.nodeStates ++ runningStates)

      for {
        // Execute all modules in this batch in parallel
        results <- batch.moduleIds.parTraverse { moduleId =>
          executeModule(moduleId, session.runnableModules(moduleId), rt)
        }

        // Update node states with results
        moduleResults = batch.moduleIds.zip(results).toMap
        updatedModuleStates = moduleResults.map {
          case (moduleId, Right((value, duration))) =>
            moduleId -> NodeState.Completed(value, duration)
          case (moduleId, Left(error)) =>
            moduleId -> NodeState.Failed(error)
        }

        // Get completed data node values from runtime state
        stateSnapshot <- rt.state.get
        dataStates = batch.dataIds.flatMap { dataId =>
          stateSnapshot.data.get(dataId).map { evalValue =>
            dataId -> NodeState.Completed(evalValue.value, 0L)
          }
        }.toMap

        newNodeStates = session.nodeStates ++ updatedModuleStates ++ dataStates

        nextBatchIndex = session.currentBatchIndex + 1
        isComplete     = nextBatchIndex >= session.batches.length

        updatedSession = session.copy(
          currentBatchIndex = nextBatchIndex,
          nodeStates = newNodeStates
        )

      } yield (updatedSession, isComplete)
    }

  /** Execute remaining batches to completion.
    */
  def executeToCompletion(session: SessionState): IO[SessionState] = {
    def loop(currentSession: SessionState): IO[SessionState] =
      if currentSession.currentBatchIndex >= currentSession.batches.length then {
        IO.pure(currentSession)
      } else {
        executeNextBatch(currentSession).flatMap { case (updatedSession, _) =>
          loop(updatedSession)
        }
      }
    loop(session)
  }

  /** Get output values from a completed session.
    */
  def getOutputs(session: SessionState): Map[String, CValue] = {
    val outputBindings  = session.dagSpec.outputBindings
    val declaredOutputs = session.dagSpec.declaredOutputs

    declaredOutputs.flatMap { outputName =>
      outputBindings.get(outputName).flatMap { dataNodeUuid =>
        session.nodeStates.get(dataNodeUuid).collect { case NodeState.Completed(value, _) =>
          outputName -> value
        }
      }
    }.toMap
  }

  /** Convert CValue to a preview string for display.
    */
  def valuePreview(value: CValue, maxLength: Int = 50): String = {
    val str = value match {
      case CValue.CString(v)          => s"\"$v\""
      case CValue.CInt(v)             => v.toString
      case CValue.CFloat(v)           => v.toString
      case CValue.CBoolean(v)         => v.toString
      case CValue.CList(values, _)    => s"[${values.size} items]"
      case CValue.CMap(pairs, _, _)   => s"{${pairs.size} entries}"
      case CValue.CProduct(fields, _) => s"{${fields.keys.mkString(", ")}}"
      case CValue.CUnion(_, _, tag)   => s"$tag(...)"
      case CValue.CSome(inner, _)     => s"Some(${valuePreview(inner, maxLength - 6)})"
      case CValue.CNone(_)            => "None"
    }
    if str.length > maxLength then str.take(maxLength - 3) + "..." else str
  }

  // Private helper methods (adapted from Runtime.scala)

  private def initModules(
      dag: DagSpec,
      modules: Map[UUID, Module.Uninitialized]
  ): IO[(List[Module.Runnable], Runtime.MutableDataTable)] =
    modules.toList
      .traverse { case (moduleId, module) =>
        module.init(moduleId, dag)
      }
      .map(_.foldLeft((List.empty[Module.Runnable], Map.empty[UUID, Deferred[IO, Any]])) {
        case ((accModules, accTable), module) =>
          (module :: accModules, accTable ++ module.data)
      })

  private def initState(dag: DagSpec): IO[Ref[IO, Runtime.State]] = {
    val moduleStatus = dag.modules.map(_._1 -> Eval.later(Module.Status.Unfired))
    Ref.of[IO, Runtime.State](
      Runtime.State(
        processUuid = UUID.randomUUID(),
        dag = dag,
        moduleStatus = moduleStatus,
        data = Map.empty
      )
    )
  }

  private def completeTopLevelDataNodes(
      dag: DagSpec,
      initData: Map[String, CValue],
      rt: Runtime
  ): IO[Unit] =
    dag.userInputDataNodes.toList.traverse { case (uuid, spec) =>
      val nicknames = spec.nicknames.values.toList
      val optCValue = initData.collectFirst {
        case (name, cValue) if nicknames.contains(name) => cValue
      }
      for {
        cValue <- IO.fromOption(optCValue)(
          new RuntimeException(
            "Failed to find data node in init data, this is a bug in the implementation of the engine."
          )
        )
        _ <- rt.setTableDataCValue(uuid, cValue)
        _ <- rt.setStateData(uuid, cValue)
      } yield ()
    }.void

  private def validateRunIO(dag: DagSpec, initData: Map[String, CValue]): IO[Unit] = {
    val topLevelSpecs = dag.userInputDataNodes.values

    def validateInput(name: String, ctype: CType): ValidatedNel[String, Unit] = {
      val isExpectedName = topLevelSpecs.find(_.nicknames.values.toSet.contains(name)) match {
        case Some(spec) => Validated.validNel(spec)
        case None =>
          Validated.invalidNel(s"Input $name was unexpected, input name might be misspelled.")
      }
      val isRightType = isExpectedName.andThen { spec =>
        if spec.cType == ctype then Validated.validNel(())
        else
          Validated.invalidNel(
            s"Input $name had different type, expected '${spec.cType}' but was '$ctype'."
          )
      }
      isRightType
    }

    Monoid.combineAll(initData.toList.map(t => validateInput(t._1, t._2.ctype))) match {
      case Valid(_)          => IO.unit
      case Invalid(messages) => IO.raiseError(new RuntimeException(messages.toList.mkString("\n")))
    }
  }

  private def executeModule(
      moduleId: UUID,
      runnable: Module.Runnable,
      rt: Runtime
  ): IO[Either[Throwable, (CValue, Long)]] = {
    val startTime = System.currentTimeMillis()

    runnable.run(rt).attempt.flatMap {
      case Right(_) =>
        // Get the module's output value from state
        rt.state.get.map { state =>
          val duration = System.currentTimeMillis() - startTime
          state.moduleStatus.get(moduleId) match {
            case Some(evalStatus) =>
              evalStatus.value match {
                case Module.Status.Fired(_, _) =>
                  // Module completed successfully, get first output data value
                  val outputDataIds = rt.table.keys.filter { dataId =>
                    state.dag.outEdges.exists { case (modId, dId) =>
                      modId == moduleId && dId == dataId
                    }
                  }
                  val outputValue = outputDataIds.headOption
                    .flatMap(state.data.get)
                    .map(_.value)
                    .getOrElse(CValue.CString("(no output)"))
                  Right((outputValue, duration))
                case Module.Status.Failed(error) =>
                  Left(error)
                case Module.Status.Timed(latency) =>
                  Left(new TimeoutException(s"Module timed out after ${latency.toMillis}ms"))
                case Module.Status.Unfired =>
                  Left(new RuntimeException("Module did not fire"))
              }
            case None =>
              Left(new RuntimeException("Module status not found"))
          }
        }
      case Left(error) =>
        IO.pure(Left(error))
    }
  }
}
