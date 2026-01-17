package io.constellation.api

import cats.data.Validated.{Invalid, Valid}
import cats.data.{Validated, ValidatedNel}
import cats.effect.{Deferred, IO, Ref}
import cats.implicits.{catsSyntaxParallelTraverse1, toTraverseOps}
import cats.{Eval, Monoid}
import io.circe.Json
import shapeless._
import shapeless.labelled.{FieldType, field}
import shapeless.syntax.typeable._

import java.util.UUID
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration

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
    for {
      anyData <- cValueToAny(data)
      _ <- setTableData(dataId, anyData)
    } yield ()

  private def cValueToAny(cValue: CValue): IO[Any] = cValue match {
    case CValue.CInt(value)      => IO.pure(value)
    case CValue.CString(value)   => IO.pure(value)
    case CValue.CBoolean(value)  => IO.pure(value)
    case CValue.CList(values, _) => IO.pure(values.map(cValueToAny))
    // case CValue.CMap(map, _, _) => IO.pure(map)
    case _ => IO.raiseError(new RuntimeException("Unsupported CValue type for conversion to Witness"))
  }
}

object Runtime {

  def run(dag: DagSpec, initData: Map[String, CValue], modules: Map[UUID, Module.Uninitialized]): IO[Runtime.State] = {
    for {
      _ <- validateRunIO(dag, initData)
      modulesAndDataTable <- initModules(dag, modules)
      (runnable, dataTable) = modulesAndDataTable
      contextRef <- initState(dag)
      runtime = Runtime(state = contextRef, table = dataTable)
      _ <- completeTopLevelDataNodes(dag, initData, runtime)
      latency <- runnable
        .parTraverse(_.run(runtime))
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

  def uninitialized[I, O, HI <: HList, HO <: HList](partialSpec: ModuleNodeSpec, run0: I => IO[Module.Produces[O]])(
    implicit
    inputToHList: LabelledGeneric.Aux[I, HI],
    outputToHList: shapeless.LabelledGeneric.Aux[O, HO],
    specBuilderConsumes: Lazy[DataNodeSpecBuilder[HI]],
    specBuilderProduces: Lazy[DataNodeSpecBuilder[HO]],
    registerConsumes: Lazy[RegisterData[HI]],
    registerProduces: Lazy[RegisterData[HO]],
    awaitOnInputs: Lazy[AwaitOnInputs[HI]],
    provideOnOutputs: Lazy[ProvideOnOutputs[HO]],
  ): Module.Uninitialized = Module.Uninitialized(
    spec = partialSpec.copy(consumes = specBuilderConsumes.value.build, produces = specBuilderProduces.value.build),
    init = (moduleId, dagSpec) => {
      for {
        consumesNamespace <- Module.Namespace.consumes(moduleId, dagSpec)
        producesNamespace <- Module.Namespace.produces(moduleId, dagSpec)
        consumesTable <- registerConsumes.value.registerData(consumesNamespace)
        producesTable <- registerProduces.value.registerData(producesNamespace)
        runnableModule = Module.Runnable(
          id = moduleId,
          data = consumesTable ++ producesTable,
          run = runtime => {
            (for {
              _ <- runtime.setModuleStatus(moduleId, Module.Status.Unfired)
              consumes <- awaitOnInputs.value.awaitOnInputs(consumesNamespace, runtime)
              consumesGen = inputToHList.from(consumes)
              producesAndLatency <- run0(consumesGen).timed.timeout(partialSpec.config.moduleTimeout)
              (latency, produces) = producesAndLatency
              producesContext = () => {
                val context = produces.implementationContext.value
                if (context.isEmpty) None
                else Some(context)
              }
              producesGen = outputToHList.to(produces.data)
              _ <- provideOnOutputs.value.provideOutputs(producesNamespace, runtime, producesGen)
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

trait DataNodeSpecBuilder[A] {

  def build: Map[String, CType]
}

object DataNodeSpecBuilder {

  implicit def hnil: DataNodeSpecBuilder[HNil] = new DataNodeSpecBuilder[HNil] {
    def build: Map[String, CType] = Map.empty[String, CType]
  }

  implicit def hcons[K <: Symbol, V, T <: HList](implicit
    key: Witness.Aux[K],
    headTag: CTypeTag[V],
    tail: DataNodeSpecBuilder[T],
  ): DataNodeSpecBuilder[FieldType[K, V] :: T] = new DataNodeSpecBuilder[FieldType[K, V] :: T] {

    def build: Map[String, CType] = {
      val headName = key.value.name
      val headType = headTag.cType
      tail.build.updated(headName, headType)
    }
  }
}

trait AwaitOnInputs[I] {

  def awaitOnInputs(namespace: Module.Namespace, runtime: Runtime): IO[I]
}

object AwaitOnInputs {

  implicit def hnil: AwaitOnInputs[HNil] =
    (_: Module.Namespace, _: Runtime) => IO.pure(HNil)

  implicit def hcons[K <: Symbol, V, T <: HList](implicit
    tail: AwaitOnInputs[T],
    key: Witness.Aux[K],
    typeable: Typeable[V]
  ): AwaitOnInputs[FieldType[K, V] :: T] = new AwaitOnInputs[FieldType[K, V] :: T] {

    def awaitOnInputs(namespace: Module.Namespace, runtime: Runtime): IO[FieldType[K, V] :: T] = {
      for {
        tailValues <- tail.awaitOnInputs(namespace, runtime)
        dataId <- namespace.nameId(key.value.name)
        valueAny <- runtime.getTableData(dataId)
        value <- IO.fromOption(valueAny.cast[V])(
          new RuntimeException(s"Failed to cast value for data ${key.value.name} to type ${typeable.describe}")
        )
      } yield field[K](value) :: tailValues
    }
  }
}

trait ProvideOnOutputs[O <: HList] {

  def provideOutputs(namespace: Module.Namespace, runtime: Runtime, outputs: O): IO[Unit]
}

object ProvideOnOutputs {

  implicit def hnil: ProvideOnOutputs[HNil] =
    (_: Module.Namespace, _: Runtime, _: HNil) => IO.unit

  implicit def hcons[K <: Symbol, V, T <: HList](implicit
    tail: ProvideOnOutputs[T],
    key: Witness.Aux[K],
    headInjector: CValueInjector[V]
  ): ProvideOnOutputs[FieldType[K, V] :: T] =
    (namespace: Module.Namespace, runtime: Runtime, outputs: FieldType[K, V] :: T) =>
      for {
        dataId <- namespace.nameId(key.value.name)
        _ <- runtime.setTableData(dataId, outputs.head)
        _ <- runtime.setStateData(dataId, headInjector.inject(outputs.head))
        _ <- tail.provideOutputs(namespace, runtime, outputs.tail)
      } yield ()
}

trait RegisterData[T <: HList] {

  def registerData(namespace: Module.Namespace): IO[Runtime.MutableDataTable]
}

object RegisterData {

  implicit def hnil: RegisterData[HNil] =
    (_: Module.Namespace) => IO.pure(Map.empty)

  implicit def hcons[K <: Symbol, V, T <: HList](implicit
    key: Witness.Aux[K],
    tail: RegisterData[T],
  ): RegisterData[FieldType[K, V] :: T] =
    (namespace: Module.Namespace) =>
      for {
        dataId <- namespace.nameId(key.value.name)
        deferred <- Deferred[IO, Any]
        tailTable <- tail.registerData(namespace)
        newTable = tailTable.updated(dataId, deferred)
      } yield newTable
}
