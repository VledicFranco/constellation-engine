package io.constellation.modules

import cats.effect.IO
import io.constellation.api._

import scala.concurrent.duration.DurationInt

object ModuleAlwaysFails {

  case class Input(n: Long)

  case class Output(n: Long)

  def module: Module.Uninitialized =
    ModuleBuilder
      .metadata(
        name = "AlwaysFails",
        description = "Always fails with random latency",
        majorVersion = 0,
        minorVersion = 1
      )
      .tags("demo")
      .implementation(run)
      .build

  def run(in: Input): IO[Output] =
    for {
      randomDelay <- IO(scala.util.Random.nextInt(10))
      _ <- IO.sleep(randomDelay.millis)
      _ <- IO.whenA(1 == 1)(IO.raiseError(new RuntimeException("This module always fails with random latency")))
    } yield Output(in.n)
}
