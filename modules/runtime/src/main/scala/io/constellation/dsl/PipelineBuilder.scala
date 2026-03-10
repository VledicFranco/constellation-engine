package io.constellation.dsl

import java.util.UUID

import scala.compiletime.constValue
import scala.deriving.Mirror

import cats.effect.IO
import cats.implicits.*

import io.constellation.{
  CType,
  CTypeTag,
  ComponentMetadata,
  DagSpec,
  DataNodeSpec,
  Module,
  ModuleBuilder,
  ModuleCallOptions,
  ModuleNodeSpec,
  Runtime
}

/** Extract the field name → [[io.constellation.CType]] map from a single-field or multi-field
  * Product type tag.
  *
  * Returns the `structure` map of the underlying `CType.CProduct`. Used internally by
  * [[PipelineBuilderImpl]] to derive `consumes` / `produces` specs without needing `Mirror`.
  *
  * @throws IllegalStateException
  *   if the resolved CType is not a `CType.CProduct` (i.e., the type is not a Product)
  */
private[dsl] def fieldMapOf[T <: Product](using tag: CTypeTag[T]): Map[String, CType] =
  tag.cType match {
    case CType.CProduct(structure) => structure
    case other =>
      throw new IllegalStateException(
        s"Expected CType.CProduct for Product type, got: $other. " +
          "Ensure the type is a case class with a derived CTypeTag."
      )
  }

/** Require at compile time that `T` has exactly one field.
  *
  * Emits a compile error if `T` has zero or two or more fields. Used by [[PipelineBuilder.step]]
  * and [[PipelineBuilder.adapt]] to enforce the single-field constraint at the type-checking phase
  * rather than at runtime.
  */
private[dsl] inline def requireSingleField[T <: Product](using m: Mirror.ProductOf[T]): Unit =
  inline constValue[Tuple.Size[m.MirroredElemTypes]] match
    case 1 => ()
    case _ =>
      scala.compiletime.error(
        "p.step and p.adapt require a single-field Product type. " +
          "For multi-field inputs, use p.assemble."
      )

/** User-facing DSL scope for building type-safe pipelines.
  *
  * Obtained exclusively via [[Pipeline.define]]; never instantiated directly by user code.
  *
  * ==Compile-time type safety==
  * `step` and `adapt` require both input and output types to be single-field case classes —
  * enforced at compile time via `requireSingleField`. Multi-field fan-in requires `p.assemble`.
  *
  * ==Module registration==
  * Named modules passed to `step` and `assemble` are collected and exposed via
  * [[TypedPipeline.moduleBuilders]], allowing [[TypedPipeline.registerModules]] to register them
  * with a [[io.constellation.Constellation]] instance. Synthetic modules created by `adapt` are
  * never registered — they execute via [[LoadedPipeline.syntheticModules]] directly.
  */
trait PipelineBuilder {

  /** Declare a typed pipeline input and return a handle to it.
    *
    * For single-field Product types (the common case) the data node's CType is the field's type,
    * matching what [[io.constellation.Constellation.run]] expects when the user provides the input
    * value. For non-Product types (e.g., `String`, `Long`) the CType is used as-is.
    *
    * @param name
    *   The input name used when providing values to `Constellation.run`.
    * @tparam I
    *   The Scala type of this input.
    */
  def input[I: CTypeTag](name: String): TypedRef[I]

  /** Wire a module into the pipeline, consuming `ref` as its single input.
    *
    * Both `I` and `O` must each have exactly one field — enforced at compile time. Use `p.assemble`
    * for multi-field inputs.
    *
    * Option methods on the returned [[TypedRef]] (e.g., `.retry(n)`, `.timeout(d)`) are recorded
    * and flushed into `moduleOptions` when the ref is next consumed by `step`, `assemble`, or
    * `output`.
    *
    * The module is collected for [[TypedPipeline.registerModules]] and does not need to be
    * registered manually.
    *
    * @param ref
    *   Typed handle to the data node produced by a prior `input`, `step`, or `adapt`.
    * @param module
    *   The module to wire. Its type parameters must match `I` and `O` exactly.
    */
  inline def step[I <: Product: CTypeTag, O <: Product: CTypeTag](
      ref: TypedRef[I]
  )(
      module: ModuleBuilder[I, O]
  )(using mi: Mirror.ProductOf[I], mo: Mirror.ProductOf[O]): TypedRef[O] = {
    requireSingleField[I]
    requireSingleField[O]
    stepImpl(ref, module.build)
  }

  protected def stepImpl[I <: Product: CTypeTag, O <: Product: CTypeTag](
      ref: TypedRef[I],
      built: Module.Uninitialized
  ): TypedRef[O]

  /** Wire a multi-input module into the pipeline using an [[AssemblyCtx]] binding block.
    *
    * Allows fan-in from multiple upstream nodes into a single module with a multi-field input type.
    * The lambda `f` is invoked exactly once during pipeline construction to record field bindings;
    * its return value (a placeholder `I` with null fields) is immediately discarded.
    *
    * `O` must have exactly one field — enforced at compile time.
    *
    * The module is collected for [[TypedPipeline.registerModules]] and does not need to be
    * registered manually.
    *
    * @param module
    *   The module to wire.
    * @param f
    *   Assembly block: receives an [[AssemblyCtx]] and must construct an `I` value using
    *   `ctx.field(ref, _.fieldName)` for every field.
    */
  inline def assemble[I <: Product: CTypeTag, O <: Product: CTypeTag](
      module: ModuleBuilder[I, O]
  )(
      f: AssemblyCtx => I
  )(using mi: Mirror.ProductOf[I], mo: Mirror.ProductOf[O]): TypedRef[O] = {
    requireSingleField[O]
    assembleImpl(module.build, f)
  }

  protected def assembleImpl[I <: Product: CTypeTag, O <: Product: CTypeTag](
      built: Module.Uninitialized,
      f: AssemblyCtx => I
  ): TypedRef[O]

  /** Insert an inline type-adaptation step between two type-incompatible nodes.
    *
    * Creates an anonymous synthetic module that wraps `f` and stores it in
    * `TypedPipeline.syntheticModules`. The synthetic module never appears in the Constellation
    * module registry — `registerModules` does not register it.
    *
    * Both `A` and `B` must be single-field Product types — enforced at compile time.
    *
    * @param ref
    *   The upstream handle to adapt from.
    * @param f
    *   Pure transformation `A → B`.
    */
  inline def adapt[A <: Product: CTypeTag, B <: Product: CTypeTag](
      ref: TypedRef[A]
  )(
      f: A => B
  )(using ma: Mirror.ProductOf[A], mb: Mirror.ProductOf[B]): TypedRef[B] = {
    requireSingleField[A]
    requireSingleField[B]
    adaptImpl(ref, f, ma, mb)
  }

  protected def adaptImpl[A <: Product: CTypeTag, B <: Product: CTypeTag](
      ref: TypedRef[A],
      f: A => B,
      ma: Mirror.ProductOf[A],
      mb: Mirror.ProductOf[B]
  ): TypedRef[B]

  /** Declare a named output for the pipeline, bound to the data node referenced by `ref`.
    *
    * Any options accumulated on `ref` are flushed into `moduleOptions` at this call.
    */
  def output(name: String, ref: TypedRef[?]): Unit
}

/** Mutable accumulator that backs [[PipelineBuilder]] during a [[Pipeline.define]] block.
  *
  * Sealed to `private[dsl]` — the public contract is [[PipelineBuilder]].
  */
private[dsl] final class PipelineBuilderImpl extends PipelineBuilder {

  private var modules: Map[UUID, ModuleNodeSpec]        = Map.empty
  private var data: Map[UUID, DataNodeSpec]             = Map.empty
  private var inEdges: Set[(UUID, UUID)]                = Set.empty // data → module
  private var outEdges: Set[(UUID, UUID)]               = Set.empty // module → data
  private var declaredOutputs: List[String]             = List.empty
  private var outputBindings: Map[String, UUID]         = Map.empty
  private var callOptions: Map[UUID, ModuleCallOptions] = Map.empty
  private var dataToModule: Map[UUID, UUID] = Map.empty // output data UUID → producing module UUID
  private var syntheticMods: Map[UUID, Module.Uninitialized] = Map.empty
  private var namedModules: List[Module.Uninitialized]       = List.empty
  private var adaptCounter: Int                              = 0

  // TypedRef is sealed; TypedRefImpl is the only subtype — cast is safe.
  private def asImpl[A](ref: TypedRef[A]): TypedRefImpl[A] =
    ref.asInstanceOf[TypedRefImpl[A]]

  /** If `ref` carries accumulated options, record them under its producing module's UUID. */
  private def flushOptions(ref: TypedRefImpl[?]): Unit =
    if !ref.options.isEmpty then
      dataToModule.get(ref.uuid).foreach { moduleUUID =>
        callOptions = callOptions + (moduleUUID -> ref.options)
      }

  /** Register an output data node and its outEdge, returning the data node UUID. */
  private def registerOutputNode(
      moduleUUID: UUID,
      fieldNameO: String,
      fieldTypeO: CType,
      nodeName: String
  ): UUID = {
    val outputDataUUID = UUID.randomUUID()
    data = data + (outputDataUUID -> DataNodeSpec(
      name = nodeName,
      nicknames = Map(moduleUUID -> fieldNameO),
      cType = fieldTypeO,
      inlineTransform = None,
      transformInputs = Map.empty
    ))
    outEdges = outEdges + ((moduleUUID, outputDataUUID))
    dataToModule = dataToModule + (outputDataUUID -> moduleUUID)
    outputDataUUID
  }

  def input[I: CTypeTag](name: String): TypedRef[I] = {
    val uuid = UUID.randomUUID()
    val ct   = summon[CTypeTag[I]].cType
    // For single-field Products, expose the field type so that validateRunIO accepts
    // a plain CValue (e.g. CValue.CString) rather than a CProduct wrapper.
    val nodeCType: CType = ct match {
      case CType.CProduct(fields) if fields.size == 1 => fields.values.head
      case other                                      => other
    }
    data = data + (uuid -> DataNodeSpec(
      name = name,
      nicknames = Map.empty,
      cType = nodeCType,
      inlineTransform = None,
      transformInputs = Map.empty
    ))
    TypedRef[I](uuid)
  }

  protected def stepImpl[I <: Product: CTypeTag, O <: Product: CTypeTag](
      ref: TypedRef[I],
      built: Module.Uninitialized
  ): TypedRef[O] = {
    val refImpl = asImpl(ref)
    flushOptions(refImpl)

    // Use built.spec.consumes / .produces — derived from Mirror, equivalent to fieldMapOf[I/O].
    val consumes   = built.spec.consumes
    val produces   = built.spec.produces
    val fieldNameI = consumes.keys.head
    val moduleUUID = UUID.randomUUID()

    // Add the consuming module's field name as a nickname on the input data node.
    data.get(refImpl.uuid).foreach { inputSpec =>
      data = data + (refImpl.uuid -> inputSpec.copy(
        nicknames = inputSpec.nicknames + (moduleUUID -> fieldNameI)
      ))
    }

    modules = modules + (moduleUUID -> built.spec)
    inEdges = inEdges + ((refImpl.uuid, moduleUUID))
    namedModules = namedModules :+ built

    val fieldNameO = produces.keys.head
    val fieldTypeO = produces.values.head
    val outUUID =
      registerOutputNode(moduleUUID, fieldNameO, fieldTypeO, s"${built.spec.metadata.name}-out")

    TypedRef[O](outUUID)
  }

  protected def assembleImpl[I <: Product: CTypeTag, O <: Product: CTypeTag](
      built: Module.Uninitialized,
      f: AssemblyCtx => I
  ): TypedRef[O] = {
    val produces   = built.spec.produces
    val moduleUUID = UUID.randomUUID()

    // Run the assembly block to collect field bindings.
    val ctx = new AssemblyCtx()
    f(ctx) // return value is discarded — only side effects on ctx matter
    val deps: List[(UUID, String)] = ctx.getDeps

    // Flush accumulated call options from each upstream ref passed to ctx.field.
    ctx.getDepsWithRefs.foreach { case (ref, _) => flushOptions(ref) }

    // Wire each consumed data node: add inEdge + nickname.
    for (dataNodeUUID, fieldName) <- deps do
      inEdges = inEdges + ((dataNodeUUID, moduleUUID))
      data.get(dataNodeUUID).foreach { spec =>
        data = data + (dataNodeUUID -> spec.copy(
          nicknames = spec.nicknames + (moduleUUID -> fieldName)
        ))
      }

    modules = modules + (moduleUUID -> built.spec)
    namedModules = namedModules :+ built

    val fieldNameO = produces.keys.head
    val fieldTypeO = produces.values.head
    val outUUID =
      registerOutputNode(moduleUUID, fieldNameO, fieldTypeO, s"${built.spec.metadata.name}-out")

    TypedRef[O](outUUID)
  }

  protected def adaptImpl[A <: Product: CTypeTag, B <: Product: CTypeTag](
      ref: TypedRef[A],
      f: A => B,
      ma: Mirror.ProductOf[A],
      mb: Mirror.ProductOf[B]
  ): TypedRef[B] = {
    val refImpl     = asImpl(ref)
    val consumesMap = fieldMapOf[A]
    val producesMap = fieldMapOf[B]

    val fieldNameA = consumesMap.keys.head
    val fieldNameB = producesMap.keys.head
    val fieldTypeB = producesMap.values.head

    val adaptName = s"__adapt_$adaptCounter"
    adaptCounter += 1
    val moduleUUID = UUID.randomUUID()

    // Add the adapt module's field name as a nickname on the input data node.
    data.get(refImpl.uuid).foreach { inputSpec =>
      data = data + (refImpl.uuid -> inputSpec.copy(
        nicknames = inputSpec.nicknames + (moduleUUID -> fieldNameA)
      ))
    }

    val adaptSpec = ModuleNodeSpec(
      metadata = ComponentMetadata.empty(adaptName),
      consumes = consumesMap,
      produces = producesMap
    )

    // Capture mirrors and function as closures for the init lambda.
    val capturedMa: Mirror.ProductOf[A] = ma
    val capturedMb: Mirror.ProductOf[B] = mb
    val capturedF: A => B               = f
    val capturedFT: CType               = fieldTypeB

    val syntheticModule = Module.Uninitialized(
      spec = adaptSpec,
      init = (adaptModuleId, dagSpec) =>
        for {
          consumesNs  <- Module.Namespace.consumes(adaptModuleId, dagSpec)
          producesNs  <- Module.Namespace.produces(adaptModuleId, dagSpec)
          inputId     <- consumesNs.nameId(fieldNameA)
          outputId    <- producesNs.nameId(fieldNameB)
          inDeferred  <- cats.effect.Deferred[IO, Any]
          outDeferred <- cats.effect.Deferred[IO, Any]
        } yield Module.Runnable(
          id = adaptModuleId,
          data = Map(inputId -> inDeferred, outputId -> outDeferred),
          run = runtime =>
            for {
              rawA <- runtime.getTableData(inputId)
              boxedA = capturedMa.fromProduct(Tuple1(rawA)).asInstanceOf[A]
              boxedB = capturedF(boxedA)
              rawB   = boxedB.productElement(0)
              _ <- runtime.setTableData(outputId, rawB)
              cValue = Runtime.anyToCValue(rawB, capturedFT)
              _ <- runtime.setStateData(outputId, cValue)
            } yield ()
        )
    )

    syntheticMods = syntheticMods + (moduleUUID -> syntheticModule)
    modules = modules + (moduleUUID             -> adaptSpec)
    inEdges = inEdges + ((refImpl.uuid, moduleUUID))

    val outUUID = registerOutputNode(moduleUUID, fieldNameB, fieldTypeB, s"$adaptName-out")

    TypedRef[B](outUUID)
  }

  def output(name: String, ref: TypedRef[?]): Unit = {
    val refImpl = asImpl(ref)
    flushOptions(refImpl)
    declaredOutputs = declaredOutputs :+ name
    outputBindings = outputBindings + (name -> refImpl.uuid)
  }

  /** Seal the accumulated state into a [[DagSpec]].
    *
    * Called by [[Pipeline.define]] after the user's builder block completes.
    */
  private[dsl] def buildDagSpec(name: String): DagSpec =
    DagSpec(
      metadata = ComponentMetadata.empty(name),
      modules = modules,
      data = data,
      inEdges = inEdges,
      outEdges = outEdges,
      declaredOutputs = declaredOutputs,
      outputBindings = outputBindings
    )

  /** Call options collected from [[TypedRef]] chains, keyed by module node UUID. */
  private[dsl] def getCallOptions: Map[UUID, ModuleCallOptions] = callOptions

  /** Synthetic modules created by `p.adapt` calls, keyed by their module node UUID. */
  private[dsl] def getSyntheticModules: Map[UUID, Module.Uninitialized] = syntheticMods

  /** Named modules collected by `p.step` and `p.assemble`, in declaration order.
    *
    * Used by [[TypedPipeline.registerModules]] to register all non-synthetic modules with a
    * [[io.constellation.Constellation]] instance. Synthetic adapt modules are excluded.
    */
  private[dsl] def getNamedModules: List[Module.Uninitialized] = namedModules
}
