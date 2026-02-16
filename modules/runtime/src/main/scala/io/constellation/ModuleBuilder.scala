package io.constellation

import scala.concurrent.duration.FiniteDuration
import scala.deriving.Mirror

import cats.Eval
import cats.effect.IO

import io.constellation.ModuleBuilder.{SimpleIn, SimpleOut}

import io.circe.Json

/** ModuleBuilder - Fluent API for defining Constellation modules.
  *
  * ModuleBuilder provides a type-safe, declarative way to create processing modules that can be
  * composed into DAG pipelines. Each module has:
  *   - Metadata (name, description, version, tags)
  *   - Input/output type signatures (derived from case classes)
  *   - Implementation (pure function or IO-based)
  *
  * ==Basic Usage==
  *
  * {{{
  * // 1. Define input/output case classes
  * case class TextInput(text: String)
  * case class TextOutput(result: String)
  *
  * // 2. Build the module
  * val uppercase: Module.Uninitialized = ModuleBuilder
  *   .metadata(
  *     name = "Uppercase",
  *     description = "Converts text to uppercase",
  *     majorVersion = 1,
  *     minorVersion = 0
  *   )
  *   .tags("text", "transform")
  *   .implementationPure[TextInput, TextOutput] { input =>
  *     TextOutput(input.text.toUpperCase)
  *   }
  *   .build
  * }}}
  *
  * ==Implementation Types==
  *
  * '''Pure implementations''' - For side-effect-free transformations:
  * {{{
  * .implementationPure[Input, Output] { input => Output(...) }
  * }}}
  *
  * '''IO implementations''' - For effectful operations (HTTP, DB, etc.):
  * {{{
  * .implementation[Input, Output] { input =>
  *   IO {
  *     // perform side effects
  *     Output(...)
  *   }
  * }
  * }}}
  *
  * ==Field Naming Rules==
  *
  * Case class field names map directly to variable names in constellation-lang:
  * {{{
  * case class MyInput(text: String, count: Int)
  *
  * // In constellation-lang:
  * result = MyModule(text, count)  // field names must match exactly
  * }}}
  *
  * ==Module States==
  *
  *   - `Module.Uninitialized` - Module template, not yet registered
  *   - `Module.Initialized` - Module with runtime context, ready for execution
  *
  * @see
  *   [[io.constellation.Module]] for module type definitions
  * @see
  *   [[io.constellation.Constellation]] for module registration API
  */
object ModuleBuilder {

  /** Wrapper for single-field input in `buildSimple` mode. */
  case class SimpleIn[A](in: A)

  /** Wrapper for single-field output in `buildSimple` mode. */
  case class SimpleOut[A](out: A)

  /** Entry point for building a module. Returns a [[ModuleBuilderInit]] for further configuration.
    *
    * @param name
    *   Module name — must exactly match usage in constellation-lang (case-sensitive)
    * @param description
    *   Human-readable description of the module's purpose
    * @param majorVersion
    *   Major semantic version
    * @param minorVersion
    *   Minor semantic version
    * @param tags
    *   Optional classification tags
    */
  def metadata(
      name: String,
      description: String,
      majorVersion: Int,
      minorVersion: Int,
      tags: List[String] = List.empty
  ): ModuleBuilderInit = ModuleBuilderInit(
    ComponentMetadata(
      name = name,
      description = description,
      tags = tags,
      majorVersion = majorVersion,
      minorVersion = minorVersion
    )
  )
}

/** Initial builder state before an implementation function is defined.
  *
  * Call [[implementation]], [[implementationPure]], or [[implementationWithContext]] to transition
  * to a typed [[ModuleBuilder]] with input/output types fixed.
  */
final case class ModuleBuilderInit(
    _metadata: ComponentMetadata,
    _config: ModuleConfig = ModuleConfig.default,
    _context: Option[Map[String, Json]] = None,
    _httpConfig: Option[ModuleHttpConfig] = None
) {

  def name(newName: String): ModuleBuilderInit =
    copy(_metadata = _metadata.copy(name = newName))

  def description(newDescription: String): ModuleBuilderInit =
    copy(_metadata = _metadata.copy(description = newDescription))

  def tags(newTags: String*): ModuleBuilderInit =
    copy(_metadata = _metadata.copy(tags = newTags.toList))

  def version(major: Int, minor: Int): ModuleBuilderInit =
    copy(_metadata = _metadata.copy(majorVersion = major, minorVersion = minor))

  def inputsTimeout(newTimeout: FiniteDuration): ModuleBuilderInit =
    copy(_config = _config.copy(inputsTimeout = newTimeout))

  def moduleTimeout(newTimeout: FiniteDuration): ModuleBuilderInit =
    copy(_config = _config.copy(moduleTimeout = newTimeout))

  def definitionContext(newContext: Map[String, Json]): ModuleBuilderInit =
    copy(_context = Some(newContext))

  /** Mark this module for HTTP endpoint publishing with default configuration. */
  def httpEndpoint(): ModuleBuilderInit =
    copy(_httpConfig = Some(ModuleHttpConfig.default))

  /** Mark this module for HTTP endpoint publishing with custom configuration. */
  def httpEndpoint(config: ModuleHttpConfig): ModuleBuilderInit =
    copy(_httpConfig = Some(config))

  /** Set an effectful (IO-based) implementation function.
    *
    * Use this for operations with side effects such as HTTP calls, database queries, or file I/O.
    *
    * @tparam I
    *   Input case class type
    * @tparam O
    *   Output case class type
    * @param newRun
    *   Function from input to `IO[output]`
    */
  def implementation[I <: Product, O <: Product](newRun: I => IO[O]): ModuleBuilder[I, O] =
    ModuleBuilder(
      _metadata = _metadata,
      _config = _config,
      _context = _context,
      _httpConfig = _httpConfig,
      _run = (input: I) => newRun(input).map(Module.Produces(_, Eval.later(Map.empty)))
    )

  /** Set a pure (side-effect-free) implementation function.
    *
    * Use this for deterministic transformations that don't perform I/O.
    *
    * @tparam I
    *   Input case class type
    * @tparam O
    *   Output case class type
    * @param newRun
    *   Pure function from input to output
    */
  def implementationPure[I <: Product, O <: Product](newRun: I => O): ModuleBuilder[I, O] =
    implementation((input: I) => IO.pure(newRun(input)))

  /** Set an implementation that returns [[Module.Produces]] with execution context metadata.
    *
    * @tparam I
    *   Input case class type
    * @tparam O
    *   Output case class type
    * @param newRun
    *   Function returning `IO[Module.Produces[O]]` with output data and context
    */
  def implementationWithContext[I <: Product, O <: Product](
      newRun: I => IO[Module.Produces[O]]
  ): ModuleBuilder[I, O] =
    ModuleBuilder(
      _metadata = _metadata,
      _config = _config,
      _context = _context,
      _httpConfig = _httpConfig,
      _run = newRun
    )
}

/** Typed builder state with input/output types and implementation function defined.
  *
  * Supports functional transformations (`map`, `contraMap`, `biMap`) and finalization via `build`
  * (multi-field case classes) or `buildSimple` (single-field wrappers).
  *
  * @tparam I
  *   Input case class type
  * @tparam O
  *   Output case class type
  */
final case class ModuleBuilder[I <: Product, O <: Product](
    _metadata: ComponentMetadata,
    _config: ModuleConfig = ModuleConfig.default,
    _context: Option[Map[String, Json]] = None,
    _httpConfig: Option[ModuleHttpConfig] = None,
    _run: I => IO[Module.Produces[O]]
) {

  /** Transform the output type, keeping the input type unchanged. */
  def map[O2 <: Product](f: O => O2): ModuleBuilder[I, O2] =
    copy(_run = (input: I) => _run(input).map(o => o.copy(data = f(o.data))))

  /** Transform the input type, keeping the output type unchanged. */
  def contraMap[I2 <: Product](f: I2 => I): ModuleBuilder[I2, O] =
    copy(_run = (input: I2) => _run(f(input)))

  /** Transform both input and output types simultaneously. */
  def biMap[I2 <: Product, O2 <: Product](f: I2 => I, g: O => O2): ModuleBuilder[I2, O2] =
    copy(_run = (input: I2) => _run(f(input)).map(o => o.copy(data = g(o.data))))

  def name(newName: String): ModuleBuilder[I, O] =
    copy(_metadata = _metadata.copy(name = newName))

  def description(newDescription: String): ModuleBuilder[I, O] =
    copy(_metadata = _metadata.copy(description = newDescription))

  def tags(newTags: String*): ModuleBuilder[I, O] =
    copy(_metadata = _metadata.copy(tags = newTags.toList))

  def version(major: Int, minor: Int): ModuleBuilder[I, O] =
    copy(_metadata = _metadata.copy(majorVersion = major, minorVersion = minor))

  def inputsTimeout(newTimeout: FiniteDuration): ModuleBuilder[I, O] =
    copy(_config = _config.copy(inputsTimeout = newTimeout))

  def moduleTimeout(newTimeout: FiniteDuration): ModuleBuilder[I, O] =
    copy(_config = _config.copy(moduleTimeout = newTimeout))

  def definitionContext(newContext: Map[String, Json]): ModuleBuilder[I, O] =
    copy(_context = Some(newContext))

  /** Mark this module for HTTP endpoint publishing with default configuration. */
  def httpEndpoint(): ModuleBuilder[I, O] =
    copy(_httpConfig = Some(ModuleHttpConfig.default))

  /** Mark this module for HTTP endpoint publishing with custom configuration. */
  def httpEndpoint(config: ModuleHttpConfig): ModuleBuilder[I, O] =
    copy(_httpConfig = Some(config))

  /** Finalize the builder and produce an uninitialized module.
    *
    * Input/output type signatures are derived at compile time from the case class mirrors.
    *
    * @return
    *   An uninitialized module ready for registration via [[Constellation.setModule]]
    */
  inline def build(using mi: Mirror.ProductOf[I], mo: Mirror.ProductOf[O]): Module.Uninitialized = {
    val spec = ModuleNodeSpec(
      metadata = _metadata,
      config = _config,
      definitionContext = _context,
      httpConfig = _httpConfig
    )
    Module.uninitialized[I, O](spec, _run)
  }

  /** Build a module using `SimpleIn`/`SimpleOut` wrappers for single-value I/O.
    *
    * Useful when the module takes and returns a single value rather than a multi-field case class.
    */
  inline def buildSimple(using
      inTag: CTypeTag[I],
      outTag: CTypeTag[O],
      outInjector: CValueInjector[O],
      miIn: Mirror.ProductOf[SimpleIn[I]],
      moOut: Mirror.ProductOf[SimpleOut[O]]
  ): Module.Uninitialized =
    biMap[SimpleIn[I], SimpleOut[O]](_.in, SimpleOut(_)).build
}
