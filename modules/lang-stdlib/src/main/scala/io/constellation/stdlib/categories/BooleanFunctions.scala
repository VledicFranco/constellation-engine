package io.constellation.stdlib.categories

import io.constellation.*
import io.constellation.lang.semantic.*

/** Boolean operations for the standard library. */
trait BooleanFunctions {

  // Input/Output case classes
  case class TwoBools(a: Boolean, b: Boolean)
  case class OneBool(value: Boolean)
  case class BoolOut(out: Boolean)

  // Modules
  val andModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.and", "Logical AND", 1, 0)
    .tags("stdlib", "boolean")
    .implementationPure[TwoBools, BoolOut](in => BoolOut(in.a && in.b))
    .build

  val orModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.or", "Logical OR", 1, 0)
    .tags("stdlib", "boolean")
    .implementationPure[TwoBools, BoolOut](in => BoolOut(in.a || in.b))
    .build

  val notModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.not", "Logical NOT", 1, 0)
    .tags("stdlib", "boolean")
    .implementationPure[OneBool, BoolOut](in => BoolOut(!in.value))
    .build

  // Signatures
  val andSignature: FunctionSignature = FunctionSignature(
    "and",
    List("a" -> SemanticType.SBoolean, "b" -> SemanticType.SBoolean),
    SemanticType.SBoolean,
    "stdlib.and",
    Some("stdlib.bool")
  )
  val orSignature: FunctionSignature = FunctionSignature(
    "or",
    List("a" -> SemanticType.SBoolean, "b" -> SemanticType.SBoolean),
    SemanticType.SBoolean,
    "stdlib.or",
    Some("stdlib.bool")
  )
  val notSignature: FunctionSignature = FunctionSignature(
    "not",
    List("value" -> SemanticType.SBoolean),
    SemanticType.SBoolean,
    "stdlib.not",
    Some("stdlib.bool")
  )

  // Collections
  def booleanSignatures: List[FunctionSignature] = List(
    andSignature,
    orSignature,
    notSignature
  )

  def booleanModules: Map[String, Module.Uninitialized] = Map(
    andModule.spec.name -> andModule,
    orModule.spec.name  -> orModule,
    notModule.spec.name -> notModule
  )
}
