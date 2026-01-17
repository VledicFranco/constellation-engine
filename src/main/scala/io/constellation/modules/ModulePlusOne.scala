package io.constellation.modules

import io.constellation.api._

object ModulePlusOne {

  case class Input(n: Long)

  case class Output(nPlusOne: Long)

  def run(n: Long): Long = n + 1

  val module: Module.Uninitialized =
    ModuleBuilder
      .metadata(name = "PlusOne", description = "Adds one to the input", majorVersion = 0, minorVersion = 1)
      .tags("math", "demo")
      .implementationPure(run)
      .buildSimple
}
