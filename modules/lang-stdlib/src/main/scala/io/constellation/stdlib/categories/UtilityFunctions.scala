package io.constellation.stdlib.categories

import cats.effect.IO

import io.constellation.*
import io.constellation.lang.semantic.*

/** Utility operations for the standard library (identity, debug). */
trait UtilityFunctions {

  // ===== Identity =====
  case class IdentityIn(value: String)
  case class IdentityOut(value: String)

  val identityModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.identity", "Pass-through identity function", 1, 0)
    .tags("stdlib", "utility")
    .implementationPure[IdentityIn, IdentityOut](in => IdentityOut(in.value))
    .build

  val identitySignature: FunctionSignature = FunctionSignature(
    name = "identity",
    params = List("value" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "stdlib.identity",
    namespace = Some("stdlib")
  )

  // ===== Debug =====
  case class LogIn(message: String)
  case class LogOut(out: String)

  val logModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.log", "Log a message and pass through", 1, 0)
    .tags("stdlib", "debug")
    .implementation[LogIn, LogOut] { in =>
      IO(println(s"[constellation-lang] ${in.message}")).as(LogOut(in.message))
    }
    .build

  val logSignature: FunctionSignature = FunctionSignature(
    "log",
    List("message" -> SemanticType.SString),
    SemanticType.SString,
    "stdlib.log",
    Some("stdlib.debug")
  )

  // Collections
  def utilitySignatures: List[FunctionSignature] = List(
    identitySignature,
    logSignature
  )

  def utilityModules: Map[String, Module.Uninitialized] = Map(
    identityModule.spec.name -> identityModule,
    logModule.spec.name      -> logModule
  )
}
