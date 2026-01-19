package io.constellation.examples

import io.constellation.*

object ModulePlusOne {

  case class Input(n: Long)

  case class Output(nPlusOne: Long)

  def run(input: Input): Output = Output(input.n + 1)

  val module: Module.Uninitialized =
    ModuleBuilder
      .metadata(
        name = "PlusOne",
        description = "Adds one to the input",
        majorVersion = 0,
        minorVersion = 1
      )
      .tags("math", "demo")
      .implementationPure(run)
      .build
}
