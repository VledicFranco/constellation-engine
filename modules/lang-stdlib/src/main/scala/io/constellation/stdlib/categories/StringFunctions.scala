package io.constellation.stdlib.categories

import io.constellation.*
import io.constellation.lang.semantic.*

/** String operations for the standard library. */
trait StringFunctions {

  // Input/Output case classes
  case class TwoStrings(a: String, b: String)
  case class OneString(value: String)
  case class StringOut(out: String)
  case class StringIntOut(out: Long)

  // Modules
  val concatModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.concat", "Concatenate two strings", 1, 0)
    .tags("stdlib", "string")
    .implementationPure[TwoStrings, StringOut](in => StringOut(in.a + in.b))
    .build

  val upperModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.upper", "Convert string to uppercase", 1, 0)
    .tags("stdlib", "string")
    .implementationPure[OneString, StringOut](in => StringOut(in.value.toUpperCase))
    .build

  val lowerModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.lower", "Convert string to lowercase", 1, 0)
    .tags("stdlib", "string")
    .implementationPure[OneString, StringOut](in => StringOut(in.value.toLowerCase))
    .build

  val stringLengthModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.string-length", "Get string length", 1, 0)
    .tags("stdlib", "string")
    .implementationPure[OneString, StringIntOut](in => StringIntOut(in.value.length.toLong))
    .build

  // Signatures
  val concatSignature: FunctionSignature = FunctionSignature(
    "concat",
    List("a" -> SemanticType.SString, "b" -> SemanticType.SString),
    SemanticType.SString,
    "stdlib.concat",
    Some("stdlib.string")
  )
  val upperSignature: FunctionSignature = FunctionSignature(
    "upper",
    List("value" -> SemanticType.SString),
    SemanticType.SString,
    "stdlib.upper",
    Some("stdlib.string")
  )
  val lowerSignature: FunctionSignature = FunctionSignature(
    "lower",
    List("value" -> SemanticType.SString),
    SemanticType.SString,
    "stdlib.lower",
    Some("stdlib.string")
  )
  val stringLengthSignature: FunctionSignature = FunctionSignature(
    "string-length",
    List("value" -> SemanticType.SString),
    SemanticType.SInt,
    "stdlib.string-length",
    Some("stdlib.string")
  )

  // Collections
  def stringSignatures: List[FunctionSignature] = List(
    concatSignature,
    upperSignature,
    lowerSignature,
    stringLengthSignature
  )

  def stringModules: Map[String, Module.Uninitialized] = Map(
    concatModule.spec.name       -> concatModule,
    upperModule.spec.name        -> upperModule,
    lowerModule.spec.name        -> lowerModule,
    stringLengthModule.spec.name -> stringLengthModule
  )
}
