package io.constellation.stdlib.categories

import cats.effect.IO
import io.constellation.*
import io.constellation.lang.semantic.*

/** Utility operations for the standard library (identity, constants, debug). */
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

  // ===== Constants =====
  case class ConstIntIn(value: Long)
  case class ConstIntOut(out: Long)

  case class ConstFloatIn(value: Double)
  case class ConstFloatOut(out: Double)

  case class ConstStringIn(value: String)
  case class ConstStringOut(out: String)

  case class ConstBoolIn(value: Boolean)
  case class ConstBoolOut(out: Boolean)

  val constIntModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.const-int", "Return a constant integer", 1, 0)
    .tags("stdlib", "constant")
    .implementationPure[ConstIntIn, ConstIntOut](in => ConstIntOut(in.value))
    .build

  val constFloatModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.const-float", "Return a constant float", 1, 0)
    .tags("stdlib", "constant")
    .implementationPure[ConstFloatIn, ConstFloatOut](in => ConstFloatOut(in.value))
    .build

  val constStringModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.const-string", "Return a constant string", 1, 0)
    .tags("stdlib", "constant")
    .implementationPure[ConstStringIn, ConstStringOut](in => ConstStringOut(in.value))
    .build

  val constBoolModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.const-bool", "Return a constant boolean", 1, 0)
    .tags("stdlib", "constant")
    .implementationPure[ConstBoolIn, ConstBoolOut](in => ConstBoolOut(in.value))
    .build

  val constIntSignature: FunctionSignature = FunctionSignature(
    "const-int",
    List("value" -> SemanticType.SInt),
    SemanticType.SInt,
    "stdlib.const-int",
    Some("stdlib")
  )
  val constFloatSignature: FunctionSignature = FunctionSignature(
    "const-float",
    List("value" -> SemanticType.SFloat),
    SemanticType.SFloat,
    "stdlib.const-float",
    Some("stdlib")
  )
  val constStringSignature: FunctionSignature = FunctionSignature(
    "const-string",
    List("value" -> SemanticType.SString),
    SemanticType.SString,
    "stdlib.const-string",
    Some("stdlib")
  )
  val constBoolSignature: FunctionSignature = FunctionSignature(
    "const-bool",
    List("value" -> SemanticType.SBoolean),
    SemanticType.SBoolean,
    "stdlib.const-bool",
    Some("stdlib")
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
    constIntSignature,
    constFloatSignature,
    constStringSignature,
    constBoolSignature,
    logSignature
  )

  def utilityModules: Map[String, Module.Uninitialized] = Map(
    identityModule.spec.name    -> identityModule,
    constIntModule.spec.name    -> constIntModule,
    constFloatModule.spec.name  -> constFloatModule,
    constStringModule.spec.name -> constStringModule,
    constBoolModule.spec.name   -> constBoolModule,
    logModule.spec.name         -> logModule
  )
}
