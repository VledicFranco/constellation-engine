package io.constellation.stdlib.categories

import io.constellation.*
import io.constellation.lang.semantic.*

/** Math operations for the standard library. */
trait MathFunctions {

  // Input/Output case classes
  case class TwoInts(a: Long, b: Long)
  case class OneInt(value: Long)
  case class OneFloat(value: Double)
  case class MathIntOut(out: Long)

  // Modules
  val addModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.add", "Add two integers", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[TwoInts, MathIntOut](in => MathIntOut(in.a + in.b))
    .build

  val subtractModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.subtract", "Subtract two integers", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[TwoInts, MathIntOut](in => MathIntOut(in.a - in.b))
    .build

  val multiplyModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.multiply", "Multiply two integers", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[TwoInts, MathIntOut](in => MathIntOut(in.a * in.b))
    .build

  val divideModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.divide", "Divide two integers", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[TwoInts, MathIntOut](in => MathIntOut(if in.b != 0 then in.a / in.b else 0))
    .build

  val maxModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.max", "Maximum of two integers", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[TwoInts, MathIntOut](in => MathIntOut(Math.max(in.a, in.b)))
    .build

  val minModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.min", "Minimum of two integers", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[TwoInts, MathIntOut](in => MathIntOut(Math.min(in.a, in.b)))
    .build

  val absModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.abs", "Absolute value of an integer", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[OneInt, MathIntOut](in => MathIntOut(Math.abs(in.value)))
    .build

  val moduloModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.modulo", "Remainder after division", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[TwoInts, MathIntOut](in => MathIntOut(if in.b != 0 then in.a % in.b else 0))
    .build

  val roundModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.round", "Round float to nearest integer", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[OneFloat, MathIntOut](in => MathIntOut(Math.round(in.value)))
    .build

  val negateModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.negate", "Negate a number", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[OneInt, MathIntOut](in => MathIntOut(-in.value))
    .build

  // Signatures
  private val twoIntParams = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt)

  val addSignature: FunctionSignature =
    FunctionSignature("add", twoIntParams, SemanticType.SInt, "stdlib.add", Some("stdlib.math"))
  val subtractSignature: FunctionSignature = FunctionSignature(
    "subtract",
    twoIntParams,
    SemanticType.SInt,
    "stdlib.subtract",
    Some("stdlib.math")
  )
  val multiplySignature: FunctionSignature = FunctionSignature(
    "multiply",
    twoIntParams,
    SemanticType.SInt,
    "stdlib.multiply",
    Some("stdlib.math")
  )
  val divideSignature: FunctionSignature = FunctionSignature(
    "divide",
    twoIntParams,
    SemanticType.SInt,
    "stdlib.divide",
    Some("stdlib.math")
  )
  val maxSignature: FunctionSignature =
    FunctionSignature("max", twoIntParams, SemanticType.SInt, "stdlib.max", Some("stdlib.math"))
  val minSignature: FunctionSignature =
    FunctionSignature("min", twoIntParams, SemanticType.SInt, "stdlib.min", Some("stdlib.math"))
  val absSignature: FunctionSignature =
    FunctionSignature("abs", List("value" -> SemanticType.SInt), SemanticType.SInt, "stdlib.abs", Some("stdlib.math"))
  val moduloSignature: FunctionSignature =
    FunctionSignature("modulo", twoIntParams, SemanticType.SInt, "stdlib.modulo", Some("stdlib.math"))
  val roundSignature: FunctionSignature = FunctionSignature(
    "round",
    List("value" -> SemanticType.SFloat),
    SemanticType.SInt,
    "stdlib.round",
    Some("stdlib.math")
  )
  val negateSignature: FunctionSignature = FunctionSignature(
    "negate",
    List("value" -> SemanticType.SInt),
    SemanticType.SInt,
    "stdlib.negate",
    Some("stdlib.math")
  )

  // Collections
  def mathSignatures: List[FunctionSignature] = List(
    addSignature,
    subtractSignature,
    multiplySignature,
    divideSignature,
    maxSignature,
    minSignature,
    absSignature,
    moduloSignature,
    roundSignature,
    negateSignature
  )

  def mathModules: Map[String, Module.Uninitialized] = Map(
    addModule.spec.name      -> addModule,
    subtractModule.spec.name -> subtractModule,
    multiplyModule.spec.name -> multiplyModule,
    divideModule.spec.name   -> divideModule,
    maxModule.spec.name      -> maxModule,
    minModule.spec.name      -> minModule,
    absModule.spec.name      -> absModule,
    moduloModule.spec.name   -> moduloModule,
    roundModule.spec.name    -> roundModule,
    negateModule.spec.name   -> negateModule
  )
}
