package io.constellation

import cats.Eval
import cats.effect.IO
import io.constellation.ModuleBuilder.{SimpleIn, SimpleOut}
import io.circe.Json

import scala.concurrent.duration.FiniteDuration
import scala.deriving.Mirror

object ModuleBuilder {

  case class SimpleIn[A](in: A)

  case class SimpleOut[A](out: A)

  def metadata(
    name: String,
    description: String,
    majorVersion: Int,
    minorVersion: Int,
    tags: List[String] = List.empty,
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

/** Initial builder state before implementation is defined */
final case class ModuleBuilderInit(
  _metadata: ComponentMetadata,
  _config: ModuleConfig = ModuleConfig.default,
  _context: Option[Map[String, Json]] = None,
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

  def implementation[I <: Product, O <: Product](newRun: I => IO[O]): ModuleBuilder[I, O] =
    ModuleBuilder(
      _metadata = _metadata,
      _config = _config,
      _context = _context,
      _run = (input: I) => newRun(input).map(Module.Produces(_, Eval.later(Map.empty)))
    )

  def implementationPure[I <: Product, O <: Product](newRun: I => O): ModuleBuilder[I, O] =
    implementation((input: I) => IO.pure(newRun(input)))

  def implementationWithContext[I <: Product, O <: Product](newRun: I => IO[Module.Produces[O]]): ModuleBuilder[I, O] =
    ModuleBuilder(
      _metadata = _metadata,
      _config = _config,
      _context = _context,
      _run = newRun
    )
}

/** Typed builder with implementation defined */
final case class ModuleBuilder[I <: Product, O <: Product](
  _metadata: ComponentMetadata,
  _config: ModuleConfig = ModuleConfig.default,
  _context: Option[Map[String, Json]] = None,
  _run: I => IO[Module.Produces[O]],
) {

  def map[O2 <: Product](f: O => O2): ModuleBuilder[I, O2] =
    copy(_run = (input: I) => _run(input).map(o => o.copy(data = f(o.data))))

  def contraMap[I2 <: Product](f: I2 => I): ModuleBuilder[I2, O] =
    copy(_run = (input: I2) => _run(f(input)))

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

  inline def build(using mi: Mirror.ProductOf[I], mo: Mirror.ProductOf[O]): Module.Uninitialized = {
    val spec = ModuleNodeSpec(metadata = _metadata, config = _config, definitionContext = _context)
    Module.uninitialized[I, O](spec, _run)
  }

  inline def buildSimple(using
    inTag: CTypeTag[I],
    outTag: CTypeTag[O],
    outInjector: CValueInjector[O],
    miIn: Mirror.ProductOf[SimpleIn[I]],
    moOut: Mirror.ProductOf[SimpleOut[O]],
  ): Module.Uninitialized = {
    biMap[SimpleIn[I], SimpleOut[O]](_.in, SimpleOut(_)).build
  }
}
