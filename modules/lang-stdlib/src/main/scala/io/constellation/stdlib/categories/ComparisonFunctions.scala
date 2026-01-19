package io.constellation.stdlib.categories

import io.constellation.*
import io.constellation.lang.semantic.*

/** Comparison operations for the standard library. */
trait ComparisonFunctions {

  // Input/Output case classes
  case class CompareTwoInts(a: Long, b: Long)
  case class CompareTwoStrings(a: String, b: String)
  case class CompareBoolOut(out: Boolean)

  // Modules
  val eqIntModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.eq-int", "Check if two integers are equal", 1, 0)
    .tags("stdlib", "comparison")
    .implementationPure[CompareTwoInts, CompareBoolOut](in => CompareBoolOut(in.a == in.b))
    .build

  val eqStringModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.eq-string", "Check if two strings are equal", 1, 0)
    .tags("stdlib", "comparison")
    .implementationPure[CompareTwoStrings, CompareBoolOut](in => CompareBoolOut(in.a == in.b))
    .build

  val gtModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.gt", "Check if a > b", 1, 0)
    .tags("stdlib", "comparison")
    .implementationPure[CompareTwoInts, CompareBoolOut](in => CompareBoolOut(in.a > in.b))
    .build

  val ltModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.lt", "Check if a < b", 1, 0)
    .tags("stdlib", "comparison")
    .implementationPure[CompareTwoInts, CompareBoolOut](in => CompareBoolOut(in.a < in.b))
    .build

  val gteModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.gte", "Check if a >= b", 1, 0)
    .tags("stdlib", "comparison")
    .implementationPure[CompareTwoInts, CompareBoolOut](in => CompareBoolOut(in.a >= in.b))
    .build

  val lteModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.lte", "Check if a <= b", 1, 0)
    .tags("stdlib", "comparison")
    .implementationPure[CompareTwoInts, CompareBoolOut](in => CompareBoolOut(in.a <= in.b))
    .build

  // Signatures
  private val twoIntParams    = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt)
  private val twoStringParams = List("a" -> SemanticType.SString, "b" -> SemanticType.SString)

  val eqIntSignature: FunctionSignature = FunctionSignature(
    "eq-int",
    twoIntParams,
    SemanticType.SBoolean,
    "stdlib.eq-int",
    Some("stdlib.compare")
  )
  val eqStringSignature: FunctionSignature = FunctionSignature(
    "eq-string",
    twoStringParams,
    SemanticType.SBoolean,
    "stdlib.eq-string",
    Some("stdlib.compare")
  )
  val gtSignature: FunctionSignature = FunctionSignature(
    "gt",
    twoIntParams,
    SemanticType.SBoolean,
    "stdlib.gt",
    Some("stdlib.compare")
  )
  val ltSignature: FunctionSignature = FunctionSignature(
    "lt",
    twoIntParams,
    SemanticType.SBoolean,
    "stdlib.lt",
    Some("stdlib.compare")
  )
  val gteSignature: FunctionSignature = FunctionSignature(
    "gte",
    twoIntParams,
    SemanticType.SBoolean,
    "stdlib.gte",
    Some("stdlib.compare")
  )
  val lteSignature: FunctionSignature = FunctionSignature(
    "lte",
    twoIntParams,
    SemanticType.SBoolean,
    "stdlib.lte",
    Some("stdlib.compare")
  )

  // Collections
  def comparisonSignatures: List[FunctionSignature] = List(
    eqIntSignature,
    eqStringSignature,
    gtSignature,
    ltSignature,
    gteSignature,
    lteSignature
  )

  def comparisonModules: Map[String, Module.Uninitialized] = Map(
    eqIntModule.spec.name    -> eqIntModule,
    eqStringModule.spec.name -> eqStringModule,
    gtModule.spec.name       -> gtModule,
    ltModule.spec.name       -> ltModule,
    gteModule.spec.name      -> gteModule,
    lteModule.spec.name      -> lteModule
  )
}
