package io.constellation.stdlib.categories

import io.constellation.*
import io.constellation.lang.semantic.*

/** Type conversion operations for the standard library. */
trait TypeConversionFunctions {

  // Input/Output case classes
  case class ConvertIntIn(value: Long)
  case class ConvertFloatIn(value: Double)
  case class ConvertStringOut(out: String)
  case class ConvertIntOut(out: Long)
  case class ConvertFloatOut(out: Double)

  // Modules
  val toStringModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.to-string", "Convert integer to string", 1, 0)
    .tags("stdlib", "convert")
    .implementationPure[ConvertIntIn, ConvertStringOut](in => ConvertStringOut(in.value.toString))
    .build

  val toIntModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.to-int", "Truncate float to integer", 1, 0)
    .tags("stdlib", "convert")
    .implementationPure[ConvertFloatIn, ConvertIntOut](in => ConvertIntOut(in.value.toLong))
    .build

  val toFloatModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.to-float", "Convert integer to float", 1, 0)
    .tags("stdlib", "convert")
    .implementationPure[ConvertIntIn, ConvertFloatOut](in => ConvertFloatOut(in.value.toDouble))
    .build

  // Signatures
  val toStringSignature: FunctionSignature = FunctionSignature(
    "to-string",
    List("value" -> SemanticType.SInt),
    SemanticType.SString,
    "stdlib.to-string",
    Some("stdlib.convert")
  )
  val toIntSignature: FunctionSignature = FunctionSignature(
    "to-int",
    List("value" -> SemanticType.SFloat),
    SemanticType.SInt,
    "stdlib.to-int",
    Some("stdlib.convert")
  )
  val toFloatSignature: FunctionSignature = FunctionSignature(
    "to-float",
    List("value" -> SemanticType.SInt),
    SemanticType.SFloat,
    "stdlib.to-float",
    Some("stdlib.convert")
  )

  // Collections
  def conversionSignatures: List[FunctionSignature] = List(
    toStringSignature,
    toIntSignature,
    toFloatSignature
  )

  def conversionModules: Map[String, Module.Uninitialized] = Map(
    toStringModule.spec.name -> toStringModule,
    toIntModule.spec.name    -> toIntModule,
    toFloatModule.spec.name  -> toFloatModule
  )
}
