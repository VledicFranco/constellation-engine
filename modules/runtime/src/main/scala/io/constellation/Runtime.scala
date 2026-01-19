package io.constellation

import cats.data.Validated.{Invalid, Valid}
import cats.data.{Validated, ValidatedNel}
import cats.effect.{Deferred, IO, Ref}
import cats.implicits.{catsSyntaxParallelTraverse1, catsSyntaxTuple2Parallel, toTraverseOps}
import cats.{Eval, Monoid}
import io.circe.Json

import java.util.UUID
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration
import scala.deriving.Mirror
import scala.compiletime.{constValueTuple, erasedValue, summonInline}

final case class Runtime(table: Runtime.MutableDataTable, state: Runtime.MutableState) {

  def close(latency: FiniteDuration): IO[Runtime.State] =
    state.updateAndGet(s => s.copy(latency = Some(latency)))

  def setModuleStatus(moduleId: UUID, status: => Module.Status): IO[Unit] =
    state.update(s => s.copy(moduleStatus = s.moduleStatus.updated(moduleId, Eval.later(status))))

  def setStateData(dataId: UUID, data: => CValue): IO[Unit] =
    state.update(s => s.copy(data = s.data.updated(dataId, Eval.later(data))))

  def getTableData(dataId: UUID): IO[Any] =
    table.get(dataId) match {
      case Some(deferred) => deferred.get
      case None           => IO.raiseError(new RuntimeException(s"Data with ID $dataId not found in table"))
    }

  def setTableData(dataId: UUID, data: Any): IO[Unit] =
    table.get(dataId) match {
      case Some(deferred) => deferred.complete(data).void
      case None           => IO.raiseError(new RuntimeException(s"Deferred for data ID $dataId not found in table"))
    }

  def setTableDataCValue(dataId: UUID, data: CValue): IO[Unit] =
    table.get(dataId) match {
      case Some(_) =>
        for {
          anyData <- cValueToAny(data)
          _ <- setTableData(dataId, anyData)
        } yield ()
      case None =>
        // No table entry for this data node (e.g., passthrough DAG with no modules)
        // This is fine - only modules need the table entries
        IO.unit
    }

  /**
   * Set table data using a RawValue directly (memory-efficient path).
   * Converts RawValue to the appropriate Scala type for internal use.
   */
  def setTableDataRawValue(dataId: UUID, data: RawValue): IO[Unit] =
    table.get(dataId) match {
      case Some(_) =>
        val anyData = rawValueToAny(data)
        setTableData(dataId, anyData)
      case None =>
        IO.unit
    }

  private def rawValueToAny(raw: RawValue): Any = raw match {
    case RawValue.RInt(value) => value
    case RawValue.RString(value) => value
    case RawValue.RBool(value) => value
    case RawValue.RFloat(value) => value
    case RawValue.RIntList(values) => values.toList
    case RawValue.RFloatList(values) => values.toList
    case RawValue.RStringList(values) => values.toList
    case RawValue.RBoolList(values) => values.toList
    case RawValue.RList(values) => values.map(rawValueToAny).toList
    case RawValue.RMap(entries) =>
      entries.map { case (k, v) => (rawValueToAny(k), rawValueToAny(v)) }.toMap
    case RawValue.RProduct(values) =>
      values.map(rawValueToAny).toList
    case RawValue.RUnion(tag, value) =>
      (tag, rawValueToAny(value))
    case RawValue.RSome(value) =>
      Some(rawValueToAny(value))
    case RawValue.RNone =>
      None
  }

  private def cValueToAny(cValue: CValue): IO[Any] = cValue match {
    case CValue.CInt(value)       => IO.pure(value)
    case CValue.CString(value)    => IO.pure(value)
    case CValue.CBoolean(value)   => IO.pure(value)
    case CValue.CFloat(value)     => IO.pure(value)
    case CValue.CList(values, _)  => values.toList.traverse(cValueToAny)
    case CValue.CMap(pairs, _, _) =>
      pairs.toList.traverse { case (k, v) =>
        for {
          kAny <- cValueToAny(k)
          vAny <- cValueToAny(v)
        } yield (kAny, vAny)
      }
    case CValue.CProduct(fields, _) =>
      fields.toList.traverse { case (name, value) =>
        cValueToAny(value).map(name -> _)
      }.map(_.toMap)
    case CValue.CUnion(value, _, tag) =>
      cValueToAny(value).map(v => (tag, v))
    case CValue.CSome(value, _) =>
      cValueToAny(value).map(Some(_))
    case CValue.CNone(_) =>
      IO.pure(None)
  }
}

object Runtime {

  def run(dag: DagSpec, initData: Map[String, CValue], modules: Map[UUID, Module.Uninitialized]): IO[Runtime.State] = {
    for {
      _ <- validateRunIO(dag, initData)
      modulesAndDataTable <- initModules(dag, modules)
      (runnable, moduleDataTable) = modulesAndDataTable

      // Create deferreds for data nodes with inline transforms
      inlineTransformTable <- initInlineTransformTable(dag)
      dataTable = moduleDataTable ++ inlineTransformTable

      contextRef <- initState(dag)
      runtime = Runtime(state = contextRef, table = dataTable)
      _ <- completeTopLevelDataNodes(dag, initData, runtime)

      // Start inline transform fibers (they run in parallel with modules)
      transformFibers <- startInlineTransformFibers(dag, runtime)

      latency <- (
        runnable.parTraverse(_.run(runtime)),
        transformFibers.parTraverse(_.join)
      ).parMapN((_, _) => ())
        .timed
        .map(_._1)

      finalState <- runtime.close(latency)
    } yield finalState
  }

  /**
   * Run DAG with RawValue inputs (memory-efficient path).
   *
   * This method accepts RawValue inputs directly, avoiding the memory overhead
   * of CValue wrappers for large data structures. Internally uses the same
   * execution model but with more efficient data representation.
   *
   * @param dag The DAG specification to execute
   * @param initData Map of input names to RawValue (memory-efficient representation)
   * @param inputTypes Map of input names to their CType (needed for validation and state reporting)
   * @param modules The modules to execute
   * @return The execution state with CValue results (for backwards compatibility)
   */
  def runWithRawInputs(
    dag: DagSpec,
    initData: Map[String, RawValue],
    inputTypes: Map[String, CType],
    modules: Map[UUID, Module.Uninitialized]
  ): IO[Runtime.State] = {
    for {
      _ <- validateRawInputsIO(dag, initData, inputTypes)
      modulesAndDataTable <- initModules(dag, modules)
      (runnable, moduleDataTable) = modulesAndDataTable

      // Create deferreds for data nodes with inline transforms
      inlineTransformTable <- initInlineTransformTable(dag)
      dataTable = moduleDataTable ++ inlineTransformTable

      contextRef <- initState(dag)
      runtime = Runtime(state = contextRef, table = dataTable)
      _ <- completeTopLevelDataNodesRaw(dag, initData, inputTypes, runtime)

      // Start inline transform fibers (they run in parallel with modules)
      transformFibers <- startInlineTransformFibers(dag, runtime)

      latency <- (
        runnable.parTraverse(_.run(runtime)),
        transformFibers.parTraverse(_.join)
      ).parMapN((_, _) => ())
        .timed
        .map(_._1)

      finalState <- runtime.close(latency)
    } yield finalState
  }

  type MutableDataTable = Map[UUID, Deferred[IO, Any]]

  type MutableState = Ref[IO, State]

  final case class State(
    processUuid: UUID,
    dag: DagSpec,
    moduleStatus: Map[UUID, Eval[Module.Status]],
    data: Map[UUID, Eval[CValue]],
    latency: Option[FiniteDuration] = None,
  )

  private def validateRun(dag: DagSpec, initData: Map[String, CValue]): ValidatedNel[String, Unit] = {
    val topLevelSpecs = dag.topLevelDataNodes.values

    def validateInput(name: String, ctype: CType): ValidatedNel[String, Unit] = {
      val isExpectedName = topLevelSpecs.find(_.nicknames.values.toSet.contains(name)) match {
        case Some(spec) => Validated.validNel(spec)
        case None =>
          Validated.invalidNel(s"Input $name was unexpected, input name might be misspelled.")
      }
      val isRightType = isExpectedName.andThen { spec =>
        if (spec.cType == ctype) Validated.validNel(())
        else
          Validated.invalidNel(s"Input $name had different type, expected '${spec.cType}' but was '$ctype'.")
      }
      isRightType
    }

    Monoid.combineAll(initData.toList.map(t => validateInput(t._1, t._2.ctype)))
  }

  private def validateRunIO(dag: DagSpec, initData: Map[String, CValue]): IO[Unit] =
    validateRun(dag, initData) match {
      case Valid(_)          => IO.unit
      case Invalid(messages) => IO.raiseError(new RuntimeException(messages.toList.mkString("\n")))
    }

  private def validateRawInputs(
    dag: DagSpec,
    initData: Map[String, RawValue],
    inputTypes: Map[String, CType]
  ): ValidatedNel[String, Unit] = {
    val topLevelSpecs = dag.topLevelDataNodes.values

    def validateInput(name: String, ctype: CType): ValidatedNel[String, Unit] = {
      val isExpectedName = topLevelSpecs.find(_.nicknames.values.toSet.contains(name)) match {
        case Some(spec) => Validated.validNel(spec)
        case None =>
          Validated.invalidNel(s"Input $name was unexpected, input name might be misspelled.")
      }
      val isRightType = isExpectedName.andThen { spec =>
        if (spec.cType == ctype) Validated.validNel(())
        else
          Validated.invalidNel(s"Input $name had different type, expected '${spec.cType}' but was '$ctype'.")
      }
      isRightType
    }

    Monoid.combineAll(inputTypes.toList.map { case (name, ctype) => validateInput(name, ctype) })
  }

  private def validateRawInputsIO(
    dag: DagSpec,
    initData: Map[String, RawValue],
    inputTypes: Map[String, CType]
  ): IO[Unit] =
    validateRawInputs(dag, initData, inputTypes) match {
      case Valid(_)          => IO.unit
      case Invalid(messages) => IO.raiseError(new RuntimeException(messages.toList.mkString("\n")))
    }

  private def initModules(
    dag: DagSpec,
    modules: Map[UUID, Module.Uninitialized]
  ): IO[(List[Module.Runnable], MutableDataTable)] = {
    modules.toList
      .traverse { case (moduleId, module) =>
        module.init(moduleId, dag)
      }
      .map(_.foldLeft((List.empty[Module.Runnable], Map.empty[UUID, Deferred[IO, Any]])) {
        case ((accModules, accTable), module) =>
          (module :: accModules, accTable ++ module.data)
      })
  }

  private def initState(dag: DagSpec): IO[Ref[IO, State]] = {
    val moduleStatus = dag.modules.map(_._1 -> Eval.later(Module.Status.Unfired))
    Ref.of[IO, State](State(processUuid = UUID.randomUUID(), dag = dag, moduleStatus = moduleStatus, data = Map.empty))
  }

  private def completeTopLevelDataNodes(dag: DagSpec, initData: Map[String, CValue], runtime: Runtime): IO[Unit] =
    dag.topLevelDataNodes.toList.traverse { case (uuid, spec) =>
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
        _ <- runtime.setTableDataCValue(uuid, cValue)
        _ <- runtime.setStateData(uuid, cValue)
      } yield ()
    }.void

  /**
   * Complete top-level data nodes using RawValue inputs (memory-efficient path).
   */
  private def completeTopLevelDataNodesRaw(
    dag: DagSpec,
    initData: Map[String, RawValue],
    inputTypes: Map[String, CType],
    runtime: Runtime
  ): IO[Unit] =
    dag.topLevelDataNodes.toList.traverse { case (uuid, spec) =>
      val nicknames = spec.nicknames.values.toList
      val optRawValue = initData.collectFirst {
        case (name, rawValue) if nicknames.contains(name) => (name, rawValue)
      }
      for {
        nameAndRaw <- IO.fromOption(optRawValue)(
          new RuntimeException(
            "Failed to find data node in init data, this is a bug in the implementation of the engine."
          )
        )
        (name, rawValue) = nameAndRaw
        cType = inputTypes.getOrElse(name, spec.cType)
        // Set table data using RawValue directly (memory-efficient)
        _ <- runtime.setTableDataRawValue(uuid, rawValue)
        // Convert to CValue only for state reporting (backwards compatibility)
        cValue = RawValueConverter.toCValue(rawValue, cType)
        _ <- runtime.setStateData(uuid, cValue)
      } yield ()
    }.void

  /**
   * Create deferreds for all data nodes that have inline transforms.
   * These data nodes compute their values from other data nodes rather than from modules.
   */
  private def initInlineTransformTable(dag: DagSpec): IO[MutableDataTable] = {
    dag.data.toList
      .filter(_._2.inlineTransform.isDefined)
      .traverse { case (dataId, _) =>
        Deferred[IO, Any].map(dataId -> _)
      }
      .map(_.toMap)
  }

  /**
   * Start fibers to compute inline transform values.
   * Each fiber waits for its input values, applies the transform, and completes the output deferred.
   * Fibers run in parallel and naturally respect data dependencies through deferred waiting.
   */
  private def startInlineTransformFibers(dag: DagSpec, runtime: Runtime): IO[List[cats.effect.Fiber[IO, Throwable, Unit]]] = {
    dag.data.toList
      .filter(_._2.inlineTransform.isDefined)
      .traverse { case (dataId, spec) =>
        computeInlineTransform(dataId, spec, runtime).start
      }
  }

  /**
   * Compute a single inline transform value.
   * Waits for all input values, applies the transform, and sets the output.
   */
  private def computeInlineTransform(dataId: UUID, spec: DataNodeSpec, runtime: Runtime): IO[Unit] = {
    spec.inlineTransform match {
      case Some(transform) =>
        for {
          // Wait for all input values
          inputValues <- spec.transformInputs.toList.traverse { case (inputName, inputDataId) =>
            runtime.getTableData(inputDataId).map(inputName -> _)
          }
          inputMap = inputValues.toMap

          // Apply the transform
          result = transform.apply(inputMap)

          // Complete the output deferred
          _ <- runtime.setTableData(dataId, result)
        } yield ()

      case None =>
        // Should not happen - we filter for inlineTransform.isDefined above
        IO.unit
    }
  }
}

object Module {

  final case class Produces[A](data: A, implementationContext: Eval[Map[String, Json]])

  sealed trait Status

  object Status {
    case object Unfired extends Status
    final case class Fired(latency: FiniteDuration, context: Option[Map[String, Json]] = None) extends Status
    final case class Timed(latency: FiniteDuration) extends Status
    final case class Failed(error: Throwable) extends Status
  }

  final case class Uninitialized(spec: ModuleNodeSpec, init: (UUID, DagSpec) => IO[Runnable])

  final case class Runnable(id: UUID, data: Runtime.MutableDataTable, run: Runtime => IO[Unit])

  final case class Namespace(nameToUUID: Map[String, UUID]) extends AnyVal {

    def nameId(name: String): IO[UUID] =
      IO.fromOption(nameToUUID.get(name))(new RuntimeException(s"Module name $name not found in namespace."))
  }

  object Namespace {

    def consumes(moduleId: UUID, dag: DagSpec): IO[Namespace] = {
      val consumesUuids = dag.inEdges.collect { case (data, module) if moduleId == module => data }
      fromDataIds(moduleId, consumesUuids, dag)
    }

    def produces(moduleId: UUID, dag: DagSpec): IO[Namespace] = {
      val producesUuids = dag.outEdges.collect { case (module, data) if moduleId == module => data }
      fromDataIds(moduleId, producesUuids, dag)
    }

    private def fromDataIds(moduleId: UUID, dataIds: Set[UUID], dag: DagSpec): IO[Namespace] =
      dataIds.toList
        .traverse { dataId =>
          for {
            spec <- IO.fromOption(dag.data.get(dataId))(
              new RuntimeException(
                "Failed to find data node in dag, this is a bug in the implementation of the engine."
              )
            )
            nickname <- IO.fromOption(spec.nicknames.get(moduleId))(
              new RuntimeException(
                "Failed to find nickname for data node in dag, this is a bug in the implementation of the engine."
              )
            )
          } yield nickname -> dataId
        }
        .map(_.toMap)
        .map(Namespace(_))
  }

  // TODO: Phase 4 - Implement with Scala 3 Mirrors
  // This will replace the shapeless-based implementation
  inline def uninitialized[I <: Product, O <: Product](
    partialSpec: ModuleNodeSpec,
    run0: I => IO[Module.Produces[O]]
  )(using mi: Mirror.ProductOf[I], mo: Mirror.ProductOf[O]): Module.Uninitialized = {
    // Build consumes/produces specs from case class field names and types
    val consumesSpec = buildDataNodeSpec[I]
    val producesSpec = buildDataNodeSpec[O]

    Module.Uninitialized(
      spec = partialSpec.copy(consumes = consumesSpec, produces = producesSpec),
      init = (moduleId, dagSpec) => {
        for {
          consumesNamespace <- Module.Namespace.consumes(moduleId, dagSpec)
          producesNamespace <- Module.Namespace.produces(moduleId, dagSpec)
          consumesTable <- registerData[I](consumesNamespace)
          producesTable <- registerData[O](producesNamespace)
          runnableModule = Module.Runnable(
            id = moduleId,
            data = consumesTable ++ producesTable,
            run = runtime => {
              (for {
                _ <- runtime.setModuleStatus(moduleId, Module.Status.Unfired)
                consumes <- awaitOnInputs[I](consumesNamespace, runtime)
                producesAndLatency <- run0(consumes).timed.timeout(partialSpec.config.moduleTimeout)
                (latency, produces) = producesAndLatency
                producesContext = () => {
                  val context = produces.implementationContext.value
                  if (context.isEmpty) None
                  else Some(context)
                }
                _ <- provideOnOutputs[O](producesNamespace, runtime, produces.data)
                _ <- runtime.setModuleStatus(moduleId, Module.Status.Fired(latency, producesContext()))
              } yield ())
                .timeout(partialSpec.config.inputsTimeout)
                .handleErrorWith {
                  case _: TimeoutException =>
                    runtime.setModuleStatus(moduleId, Module.Status.Timed(partialSpec.config.inputsTimeout))
                  case e =>
                    runtime.setModuleStatus(moduleId, Module.Status.Failed(e))
                }
                .void
            }
          )
        } yield runnableModule
      }
    )
  }

  // Helper to get field names from a Product type at compile time
  inline def getFieldNames[T <: Product](using m: Mirror.ProductOf[T]): List[String] = {
    val labels = constValueTuple[m.MirroredElemLabels]
    tupleToList(labels)
  }

  private inline def tupleToList[T <: Tuple](t: T): List[String] = {
    inline t match {
      case _: EmptyTuple => Nil
      case t: (h *: tail) => t.head.asInstanceOf[String] :: tupleToList(t.tail)
    }
  }

  // Build Map[String, CType] from case class structure
  inline def buildDataNodeSpec[T <: Product](using m: Mirror.ProductOf[T]): Map[String, CType] = {
    val names = getFieldNames[T]
    val types = getFieldTypes[T]
    names.zip(types).toMap
  }

  inline def getFieldTypes[T <: Product](using m: Mirror.ProductOf[T]): List[CType] = {
    getTypesFromTuple[m.MirroredElemTypes]
  }

  private inline def getTypesFromTuple[T <: Tuple]: List[CType] = {
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (h *: tail) =>
        summonInline[CTypeTag[h]].cType :: getTypesFromTuple[tail]
    }
  }

  // Register deferred data slots for each field
  inline def registerData[T <: Product](namespace: Namespace)(using m: Mirror.ProductOf[T]): IO[Runtime.MutableDataTable] = {
    val names = getFieldNames[T]
    names.traverse { name =>
      for {
        dataId <- namespace.nameId(name)
        deferred <- Deferred[IO, Any]
      } yield dataId -> deferred
    }.map(_.toMap)
  }

  // Await on inputs and construct case class
  inline def awaitOnInputs[T <: Product](namespace: Namespace, runtime: Runtime)(using m: Mirror.ProductOf[T]): IO[T] = {
    val names = getFieldNames[T]
    for {
      values <- names.traverse { name =>
        for {
          dataId <- namespace.nameId(name)
          value <- runtime.getTableData(dataId)
        } yield value
      }
      tuple = Tuple.fromArray(values.toArray)
    } yield m.fromTuple(tuple.asInstanceOf[m.MirroredElemTypes])
  }

  // Provide outputs to runtime
  inline def provideOnOutputs[T <: Product](namespace: Namespace, runtime: Runtime, outputs: T)(using m: Mirror.ProductOf[T]): IO[Unit] = {
    val names = getFieldNames[T]
    val values = outputs.productIterator.toList
    val injectors = getInjectors[T]

    names.zip(values).zip(injectors).traverse { case ((name, value), injector) =>
      for {
        dataId <- namespace.nameId(name)
        _ <- runtime.setTableData(dataId, value)
        _ <- runtime.setStateData(dataId, injector.asInstanceOf[CValueInjector[Any]].inject(value))
      } yield ()
    }.void
  }

  inline def getInjectors[T <: Product](using m: Mirror.ProductOf[T]): List[CValueInjector[?]] = {
    getInjectorsFromTuple[m.MirroredElemTypes]
  }

  private inline def getInjectorsFromTuple[T <: Tuple]: List[CValueInjector[?]] = {
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (h *: tail) =>
        summonInline[CValueInjector[h]] :: getInjectorsFromTuple[tail]
    }
  }
}

// Type class for building data node specs - used by inline methods
trait DataNodeSpecBuilder[A] {
  def build: Map[String, CType]
}

// Type class for awaiting on inputs
trait AwaitOnInputs[I] {
  def awaitOnInputs(namespace: Module.Namespace, runtime: Runtime): IO[I]
}

// Type class for providing outputs
trait ProvideOnOutputs[O] {
  def provideOutputs(namespace: Module.Namespace, runtime: Runtime, outputs: O): IO[Unit]
}

// Type class for registering data
trait RegisterData[T] {
  def registerData(namespace: Module.Namespace): IO[Runtime.MutableDataTable]
}
