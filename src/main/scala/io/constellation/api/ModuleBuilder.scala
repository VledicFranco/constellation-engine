package io.constellation.api

import cats.Eval
import cats.effect.IO
import io.constellation.api.ModuleBuilder.{SimpleIn, SimpleOut}
import io.circe.Json
import shapeless.{HList, LabelledGeneric, Lazy}

import scala.concurrent.duration.FiniteDuration

object ModuleBuilder {

  case class SimpleIn[A](in: A)

  case class SimpleOut[A](out: A)

  def metadata(
    name: String,
    description: String,
    majorVersion: Int,
    minorVersion: Int,
    tags: List[String] = List.empty,
  ): ModuleBuilder[Any, Any] = new ModuleBuilder(
    ComponentMetadata(
      name = name,
      description = description,
      tags = tags,
      majorVersion = majorVersion,
      minorVersion = minorVersion
    )
  )
}

final case class ModuleBuilder[I, O](
  _metadata: ComponentMetadata,
  _config: ModuleConfig = ModuleConfig.default,
  _context: Option[Map[String, Json]] = None,
  _run: I => IO[Module.Produces[O]] = (_: I) =>
    IO.raiseError(new RuntimeException("Module implementation is not defined")),
) {

  def map[O2](f: O => O2): ModuleBuilder[I, O2] =
    copy(_run = (input: I) => _run(input).map(o => o.copy(data = f(o.data))))

  def contraMap[I2](f: I2 => I): ModuleBuilder[I2, O] =
    copy(_run = (input: I2) => _run(f(input)))

  def biMap[I2, O2](f: I2 => I, g: O => O2): ModuleBuilder[I2, O2] =
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

  def implementation[I2, O2](newRun: I2 => IO[O2]): ModuleBuilder[I2, O2] =
    copy(_run = (input: I2) => newRun(input).map(Module.Produces(_, Eval.later(Map.empty))))

  def implementationPure[I2, O2](newRun: I2 => O2): ModuleBuilder[I2, O2] =
    implementation((input: I2) => IO.pure(newRun(input)))

  def implementationWithContext[I2, O2](newRun: I2 => IO[Module.Produces[O2]]): ModuleBuilder[I2, O2] =
    copy(_run = newRun)

  def build[HI <: HList, HO <: HList](implicit
    inputToHList: LabelledGeneric.Aux[I, HI],
    outputToHList: shapeless.LabelledGeneric.Aux[O, HO],
    specBuilderConsumes: Lazy[DataNodeSpecBuilder[HI]],
    specBuilderProduces: Lazy[DataNodeSpecBuilder[HO]],
    registerConsumes: Lazy[RegisterData[HI]],
    registerProduces: Lazy[RegisterData[HO]],
    awaitOnInputs: Lazy[AwaitOnInputs[HI]],
    provideOnOutputs: Lazy[ProvideOnOutputs[HO]]
  ): Module.Uninitialized = {
    val spec = ModuleNodeSpec(metadata = _metadata, config = _config, definitionContext = _context)
    Module.uninitialized(spec, _run)
  }

  def buildSimple(implicit
    inTag: CTypeTag[I],
    outTag: CTypeTag[O],
    outInjector: CValueInjector[O],
  ): Module.Uninitialized = {
    biMap[SimpleIn[I], SimpleOut[O]](_.in, SimpleOut(_)).build
  }
}
