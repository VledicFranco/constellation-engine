package io.constellation

import cats.effect.IO
import cats.implicits.*
import io.constellation.execution.GlobalScheduler
import io.constellation.spi.ConstellationBackends

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// Error types for resume validation
final case class InputTypeMismatchError(name: String, expected: CType, actual: CType)
    extends RuntimeException(s"Input '$name' type mismatch: expected $expected, got $actual")

final case class InputAlreadyProvidedError(name: String)
    extends RuntimeException(s"Input '$name' was already provided with a different value")

final case class UnknownNodeError(name: String)
    extends RuntimeException(s"Unknown node name: '$name'")

final case class NodeTypeMismatchError(name: String, expected: CType, actual: CType)
    extends RuntimeException(s"Node '$name' type mismatch: expected $expected, got $actual")

final case class NodeAlreadyResolvedError(name: String)
    extends RuntimeException(s"Node '$name' has already been computed")

final case class ProgramChangedError(expected: String, actual: String)
    extends RuntimeException(s"Program structural hash changed: expected $expected, got $actual")

final case class ProgramNotFoundError(ref: String)
    extends RuntimeException(s"Program not found: $ref")

final case class ResumeInProgressError(executionId: UUID)
    extends RuntimeException(s"Resume operation already in progress for execution $executionId")

/** Provides the ability to resume a suspended pipeline execution.
  *
  * Given a [[SuspendedExecution]] snapshot, additional inputs, and optionally
  * manually-resolved node values, this re-executes the pipeline from where
  * it left off.
  *
  * Thread-safety: Concurrent resume calls for the same executionId are
  * prevented by an in-flight operation tracker. Only one resume can proceed
  * at a time per suspended execution.
  */
object SuspendableExecution {

  // Track in-flight resume operations to prevent concurrent modifications
  // Key: executionId, Value: timestamp when resume started
  private val inFlightResumes = new ConcurrentHashMap[UUID, Long]()

  /** Resume a suspended execution with additional inputs and/or resolved nodes.
    *
    * @param suspended       The suspended execution snapshot to resume from
    * @param additionalInputs New input values to add (keyed by variable name)
    * @param resolvedNodes   Manually-resolved data node values (keyed by variable name)
    * @param modules         Module implementations to use for execution
    * @param options         Execution options
    * @param scheduler       Task scheduler
    * @param backends        SPI backends
    * @return A new DataSignature reflecting the resumed execution outcome
    */
  def resume(
      suspended: SuspendedExecution,
      additionalInputs: Map[String, CValue] = Map.empty,
      resolvedNodes: Map[String, CValue] = Map.empty,
      modules: Map[UUID, Module.Uninitialized] = Map.empty,
      options: ExecutionOptions = ExecutionOptions(),
      scheduler: GlobalScheduler = GlobalScheduler.unbounded,
      backends: ConstellationBackends = ConstellationBackends.defaults
  ): IO[DataSignature] = {
    val executionId = suspended.executionId
    val dagSpec = suspended.dagSpec
    val startedAt = Instant.now()

    // Atomic check-and-set: claim this execution for resume or fail
    val claimResult = Option(inFlightResumes.putIfAbsent(executionId, System.currentTimeMillis()))

    claimResult match {
      case Some(_) =>
        // Another resume is already in progress for this execution
        IO.raiseError(ResumeInProgressError(executionId))

      case None =>
        // Successfully claimed - proceed with resume, ensuring cleanup on completion/failure
        doResume(suspended, additionalInputs, resolvedNodes, modules, options, scheduler, backends, startedAt)
          .guarantee(IO.delay(inFlightResumes.remove(executionId)))
    }
  }

  /** Internal resume implementation - called after successfully claiming the execution. */
  private def doResume(
      suspended: SuspendedExecution,
      additionalInputs: Map[String, CValue],
      resolvedNodes: Map[String, CValue],
      modules: Map[UUID, Module.Uninitialized],
      options: ExecutionOptions,
      scheduler: GlobalScheduler,
      backends: ConstellationBackends,
      startedAt: Instant
  ): IO[DataSignature] = {
    val dagSpec = suspended.dagSpec

    // Validate additional inputs
    val inputValidation = validateAdditionalInputs(dagSpec, suspended.providedInputs, additionalInputs)
    // Validate resolved nodes
    val nodeValidation = validateResolvedNodes(dagSpec, suspended.computedValues, resolvedNodes)

    inputValidation *> nodeValidation *> {
      // Merge inputs
      val mergedInputs = suspended.providedInputs ++ additionalInputs

      // Merge computed values: existing + resolved nodes (mapped from name to UUID)
      val nameToUuid: Map[String, UUID] = dagSpec.data.collect {
        case (uuid, spec) => spec.name -> uuid
      }
      val resolvedByUuid: Map[UUID, CValue] = resolvedNodes.flatMap { case (name, value) =>
        nameToUuid.get(name).map(_ -> value)
      }
      val mergedComputed = suspended.computedValues ++ resolvedByUuid

      // Reconstruct synthetic modules from DagSpec
      val syntheticModules = SyntheticModuleFactory.fromDagSpec(dagSpec)
      val allModules = syntheticModules ++ modules

      // Execute the pipeline with pre-populated values
      for {
        state <- Runtime.runWithBackends(dagSpec, mergedInputs, allModules, Map.empty, scheduler, backends)
      } yield {
        val newResumptionCount = suspended.resumptionCount + 1
        buildDataSignature(state, dagSpec, suspended.structuralHash,
          mergedInputs, options, startedAt, newResumptionCount, suspended.moduleOptions,
          resolvedNodeNames = resolvedNodes.keySet)
      }
    }
  }

  /** Validate additional inputs against the DAG spec and existing inputs. */
  private def validateAdditionalInputs(
      dagSpec: DagSpec,
      existingInputs: Map[String, CValue],
      additionalInputs: Map[String, CValue]
  ): IO[Unit] = {
    val expectedInputs = dagSpec.userInputDataNodes.values.flatMap { spec =>
      spec.nicknames.values.map(name => name -> spec.cType)
    }.toMap

    val errors = additionalInputs.toList.flatMap { case (name, value) =>
      // Check if the input name is expected
      expectedInputs.get(name) match {
        case None => List(UnknownNodeError(name))
        case Some(expectedType) =>
          val typeErrors =
            if (value.ctype != expectedType)
              List(InputTypeMismatchError(name, expectedType, value.ctype))
            else Nil

          // Check for duplicate with different value
          val dupErrors = existingInputs.get(name) match {
            case Some(existing) if existing != value => List(InputAlreadyProvidedError(name))
            case _                                    => Nil
          }

          typeErrors ++ dupErrors
      }
    }

    if (errors.isEmpty) IO.unit
    else IO.raiseError(errors.head)
  }

  /** Validate manually-resolved nodes. */
  private def validateResolvedNodes(
      dagSpec: DagSpec,
      computedValues: Map[UUID, CValue],
      resolvedNodes: Map[String, CValue]
  ): IO[Unit] = {
    val nameToSpec: Map[String, (UUID, DataNodeSpec)] = dagSpec.data.map { case (uuid, spec) =>
      spec.name -> (uuid, spec)
    }

    val errors = resolvedNodes.toList.flatMap { case (name, value) =>
      nameToSpec.get(name) match {
        case None => List(UnknownNodeError(name))
        case Some((uuid, spec)) =>
          val typeErrors =
            if (value.ctype != spec.cType)
              List(NodeTypeMismatchError(name, spec.cType, value.ctype))
            else Nil

          val alreadyComputed =
            if (computedValues.contains(uuid))
              List(NodeAlreadyResolvedError(name))
            else Nil

          typeErrors ++ alreadyComputed
      }
    }

    if (errors.isEmpty) IO.unit
    else IO.raiseError(errors.head)
  }

  /** Build a DataSignature from a Runtime.State after resume. */
  private def buildDataSignature(
      state: Runtime.State,
      dagSpec: DagSpec,
      structuralHash: String,
      inputs: Map[String, CValue],
      options: ExecutionOptions,
      startedAt: Instant,
      resumptionCount: Int,
      moduleOptions: Map[UUID, ModuleCallOptions],
      resolvedNodeNames: Set[String] = Set.empty
  ): DataSignature = {
    val uuidToName: Map[UUID, String] = dagSpec.data.map { case (uuid, spec) => uuid -> spec.name }

    val computedNodes: Map[String, CValue] = state.data.flatMap { case (uuid, evalCValue) =>
      uuidToName.get(uuid).map(name => name -> evalCValue.value)
    }

    val outputs: Map[String, CValue] = dagSpec.declaredOutputs.flatMap { name =>
      computedNodes.get(name).map(name -> _)
    }.toMap

    val expectedInputNames = dagSpec.userInputDataNodes.values.flatMap(_.nicknames.values).toSet
    val missingInputs = (expectedInputNames -- inputs.keySet).toList.sorted
    val pendingOutputs = dagSpec.declaredOutputs.filterNot(outputs.contains)

    val failedModules = state.moduleStatus.toList.flatMap { case (uuid, evalStatus) =>
      evalStatus.value match {
        case Module.Status.Failed(error) =>
          val moduleName = dagSpec.modules.get(uuid).map(_.name).getOrElse(uuid.toString)
          Some(ExecutionError(moduleName, moduleName, error.getMessage, Some(error)))
        case _ => None
      }
    }

    val status: PipelineStatus =
      if (failedModules.nonEmpty) PipelineStatus.Failed(failedModules)
      else if (pendingOutputs.isEmpty && missingInputs.isEmpty) PipelineStatus.Completed
      else PipelineStatus.Suspended

    val completedAt = Instant.now()
    val metadata = MetadataBuilder.build(
      state, dagSpec, options, startedAt, completedAt,
      inputNodeNames = inputs.keySet,
      resolvedNodeNames = resolvedNodeNames
    )

    val suspendedState: Option[SuspendedExecution] =
      if (status == PipelineStatus.Completed) None
      else Some(SuspendedExecution(
        executionId = state.processUuid,
        structuralHash = structuralHash,
        resumptionCount = resumptionCount,
        dagSpec = dagSpec,
        moduleOptions = moduleOptions,
        providedInputs = inputs,
        computedValues = state.data.map { case (uuid, evalCValue) => uuid -> evalCValue.value },
        moduleStatuses = state.moduleStatus.map { case (uuid, evalStatus) =>
          uuid -> evalStatus.value.toString
        }
      ))

    DataSignature(
      executionId = state.processUuid,
      structuralHash = structuralHash,
      resumptionCount = resumptionCount,
      status = status,
      inputs = inputs,
      computedNodes = computedNodes,
      outputs = outputs,
      missingInputs = missingInputs,
      pendingOutputs = pendingOutputs,
      suspendedState = suspendedState,
      metadata = metadata
    )
  }
}
