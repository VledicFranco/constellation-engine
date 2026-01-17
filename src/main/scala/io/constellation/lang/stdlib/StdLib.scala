package io.constellation.lang.stdlib

import cats.effect.IO
import io.constellation.api._
import io.constellation.lang.compiler.CompileResult
import io.constellation.lang.runtime.{LangCompiler, LangCompilerBuilder}
import io.constellation.lang.semantic._

import java.util.UUID

/** Standard library of modules for constellation-lang.
  *
  * These modules provide common operations for ML pipeline orchestration.
  */
object StdLib {

  /** Register all standard library functions with a LangCompiler builder */
  def registerAll(builder: LangCompilerBuilder): LangCompilerBuilder = {
    builder
      // Identity and transformation
      .withFunction(Identity.signature)
      .withFunction(Const.intSignature)
      .withFunction(Const.floatSignature)
      .withFunction(Const.stringSignature)
      .withFunction(Const.boolSignature)
      // List operations
      .withFunction(ListOps.lengthSignature)
      .withFunction(ListOps.firstSignature)
      .withFunction(ListOps.lastSignature)
      .withFunction(ListOps.isEmptySignature)
      // Math operations
      .withFunction(MathOps.addSignature)
      .withFunction(MathOps.subtractSignature)
      .withFunction(MathOps.multiplySignature)
      .withFunction(MathOps.divideSignature)
      .withFunction(MathOps.maxSignature)
      .withFunction(MathOps.minSignature)
      // String operations
      .withFunction(StringOps.concatSignature)
      .withFunction(StringOps.upperSignature)
      .withFunction(StringOps.lowerSignature)
      .withFunction(StringOps.lengthSignature)
      // Boolean operations
      .withFunction(BoolOps.andSignature)
      .withFunction(BoolOps.orSignature)
      .withFunction(BoolOps.notSignature)
      // Comparison operations
      .withFunction(CompareOps.eqIntSignature)
      .withFunction(CompareOps.eqStringSignature)
      .withFunction(CompareOps.gtSignature)
      .withFunction(CompareOps.ltSignature)
      .withFunction(CompareOps.gteSignature)
      .withFunction(CompareOps.lteSignature)
      // Debug operations
      .withFunction(DebugOps.logSignature)
  }

  /** Get all standard library modules */
  def allModules: Map[String, Module.Uninitialized] = Map(
    Identity.module.spec.name -> Identity.module,
    Const.intModule.spec.name -> Const.intModule,
    Const.floatModule.spec.name -> Const.floatModule,
    Const.stringModule.spec.name -> Const.stringModule,
    Const.boolModule.spec.name -> Const.boolModule,
    ListOps.lengthModule.spec.name -> ListOps.lengthModule,
    ListOps.firstModule.spec.name -> ListOps.firstModule,
    ListOps.lastModule.spec.name -> ListOps.lastModule,
    ListOps.isEmptyModule.spec.name -> ListOps.isEmptyModule,
    MathOps.addModule.spec.name -> MathOps.addModule,
    MathOps.subtractModule.spec.name -> MathOps.subtractModule,
    MathOps.multiplyModule.spec.name -> MathOps.multiplyModule,
    MathOps.divideModule.spec.name -> MathOps.divideModule,
    MathOps.maxModule.spec.name -> MathOps.maxModule,
    MathOps.minModule.spec.name -> MathOps.minModule,
    StringOps.concatModule.spec.name -> StringOps.concatModule,
    StringOps.upperModule.spec.name -> StringOps.upperModule,
    StringOps.lowerModule.spec.name -> StringOps.lowerModule,
    StringOps.lengthModule.spec.name -> StringOps.lengthModule,
    BoolOps.andModule.spec.name -> BoolOps.andModule,
    BoolOps.orModule.spec.name -> BoolOps.orModule,
    BoolOps.notModule.spec.name -> BoolOps.notModule,
    CompareOps.eqIntModule.spec.name -> CompareOps.eqIntModule,
    CompareOps.eqStringModule.spec.name -> CompareOps.eqStringModule,
    CompareOps.gtModule.spec.name -> CompareOps.gtModule,
    CompareOps.ltModule.spec.name -> CompareOps.ltModule,
    CompareOps.gteModule.spec.name -> CompareOps.gteModule,
    CompareOps.lteModule.spec.name -> CompareOps.lteModule,
    DebugOps.logModule.spec.name -> DebugOps.logModule,
  )

  /** Create a LangCompiler with all standard library functions registered */
  def compiler: LangCompiler = {
    val builder = registerAll(LangCompilerBuilder())
    new StdLibCompiler(builder.build, allModules)
  }

  /** Compiler wrapper that includes stdlib modules in results */
  private class StdLibCompiler(
    underlying: LangCompiler,
    stdModules: Map[String, Module.Uninitialized]
  ) extends LangCompiler {
    def compile(source: String, dagName: String) = {
      underlying.compile(source, dagName).map { result =>
        // Include stdlib modules that are referenced by the DAG
        // Match module specs in the DAG to stdlib modules by name
        val neededStdModules: Map[UUID, Module.Uninitialized] = result.dagSpec.modules.flatMap {
          case (moduleId, spec) =>
            stdModules.find { case (name, _) => spec.name.contains(name) }
              .map { case (_, module) => moduleId -> module }
        }
        result.copy(syntheticModules = result.syntheticModules ++ neededStdModules)
      }
    }
  }
}

// =============================================================================
// Identity Module
// =============================================================================

object Identity {
  case class In(value: String)
  case class Out(value: String)

  val module: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.identity", "Pass-through identity function", 1, 0)
    .tags("stdlib", "utility")
    .implementationPure[In, Out](in => Out(in.value))
    .build

  val signature = FunctionSignature(
    name = "identity",
    params = List("value" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "stdlib.identity"
  )
}

// =============================================================================
// Constant Value Modules
// =============================================================================

object Const {
  case class IntIn(value: Long)
  case class IntOut(out: Long)

  case class FloatIn(value: Double)
  case class FloatOut(out: Double)

  case class StringIn(value: String)
  case class StringOut(out: String)

  case class BoolIn(value: Boolean)
  case class BoolOut(out: Boolean)

  val intModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.const-int", "Return a constant integer", 1, 0)
    .tags("stdlib", "constant")
    .implementationPure[IntIn, IntOut](in => IntOut(in.value))
    .build

  val floatModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.const-float", "Return a constant float", 1, 0)
    .tags("stdlib", "constant")
    .implementationPure[FloatIn, FloatOut](in => FloatOut(in.value))
    .build

  val stringModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.const-string", "Return a constant string", 1, 0)
    .tags("stdlib", "constant")
    .implementationPure[StringIn, StringOut](in => StringOut(in.value))
    .build

  val boolModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.const-bool", "Return a constant boolean", 1, 0)
    .tags("stdlib", "constant")
    .implementationPure[BoolIn, BoolOut](in => BoolOut(in.value))
    .build

  val intSignature = FunctionSignature("const-int", List("value" -> SemanticType.SInt), SemanticType.SInt, "stdlib.const-int")
  val floatSignature = FunctionSignature("const-float", List("value" -> SemanticType.SFloat), SemanticType.SFloat, "stdlib.const-float")
  val stringSignature = FunctionSignature("const-string", List("value" -> SemanticType.SString), SemanticType.SString, "stdlib.const-string")
  val boolSignature = FunctionSignature("const-bool", List("value" -> SemanticType.SBoolean), SemanticType.SBoolean, "stdlib.const-bool")
}

// =============================================================================
// List Operations
// =============================================================================

object ListOps {
  case class ListIntIn(list: List[Long])
  case class IntOut(out: Long)
  case class BoolOut(out: Boolean)

  val lengthModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.list-length", "Get the length of a list", 1, 0)
    .tags("stdlib", "list")
    .implementationPure[ListIntIn, IntOut](in => IntOut(in.list.length.toLong))
    .build

  val firstModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.list-first", "Get the first element of a list", 1, 0)
    .tags("stdlib", "list")
    .implementationPure[ListIntIn, IntOut](in => IntOut(in.list.headOption.getOrElse(0L)))
    .build

  val lastModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.list-last", "Get the last element of a list", 1, 0)
    .tags("stdlib", "list")
    .implementationPure[ListIntIn, IntOut](in => IntOut(in.list.lastOption.getOrElse(0L)))
    .build

  val isEmptyModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.list-is-empty", "Check if a list is empty", 1, 0)
    .tags("stdlib", "list")
    .implementationPure[ListIntIn, BoolOut](in => BoolOut(in.list.isEmpty))
    .build

  val lengthSignature = FunctionSignature("list-length", List("list" -> SemanticType.SList(SemanticType.SInt)), SemanticType.SInt, "stdlib.list-length")
  val firstSignature = FunctionSignature("list-first", List("list" -> SemanticType.SList(SemanticType.SInt)), SemanticType.SInt, "stdlib.list-first")
  val lastSignature = FunctionSignature("list-last", List("list" -> SemanticType.SList(SemanticType.SInt)), SemanticType.SInt, "stdlib.list-last")
  val isEmptySignature = FunctionSignature("list-is-empty", List("list" -> SemanticType.SList(SemanticType.SInt)), SemanticType.SBoolean, "stdlib.list-is-empty")
}

// =============================================================================
// Math Operations
// =============================================================================

object MathOps {
  case class TwoInts(a: Long, b: Long)
  case class TwoFloats(a: Double, b: Double)
  case class IntOut(out: Long)
  case class FloatOut(out: Double)

  val addModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.add", "Add two integers", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[TwoInts, IntOut](in => IntOut(in.a + in.b))
    .build

  val subtractModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.subtract", "Subtract two integers", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[TwoInts, IntOut](in => IntOut(in.a - in.b))
    .build

  val multiplyModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.multiply", "Multiply two integers", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[TwoInts, IntOut](in => IntOut(in.a * in.b))
    .build

  val divideModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.divide", "Divide two integers", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[TwoInts, IntOut](in => IntOut(if (in.b != 0) in.a / in.b else 0))
    .build

  val maxModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.max", "Maximum of two integers", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[TwoInts, IntOut](in => IntOut(Math.max(in.a, in.b)))
    .build

  val minModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.min", "Minimum of two integers", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[TwoInts, IntOut](in => IntOut(Math.min(in.a, in.b)))
    .build

  private val twoIntParams = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt)

  val addSignature = FunctionSignature("add", twoIntParams, SemanticType.SInt, "stdlib.add")
  val subtractSignature = FunctionSignature("subtract", twoIntParams, SemanticType.SInt, "stdlib.subtract")
  val multiplySignature = FunctionSignature("multiply", twoIntParams, SemanticType.SInt, "stdlib.multiply")
  val divideSignature = FunctionSignature("divide", twoIntParams, SemanticType.SInt, "stdlib.divide")
  val maxSignature = FunctionSignature("max", twoIntParams, SemanticType.SInt, "stdlib.max")
  val minSignature = FunctionSignature("min", twoIntParams, SemanticType.SInt, "stdlib.min")
}

// =============================================================================
// String Operations
// =============================================================================

object StringOps {
  case class TwoStrings(a: String, b: String)
  case class OneString(value: String)
  case class StringOut(out: String)
  case class IntOut(out: Long)

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

  val lengthModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.string-length", "Get string length", 1, 0)
    .tags("stdlib", "string")
    .implementationPure[OneString, IntOut](in => IntOut(in.value.length.toLong))
    .build

  val concatSignature = FunctionSignature("concat", List("a" -> SemanticType.SString, "b" -> SemanticType.SString), SemanticType.SString, "stdlib.concat")
  val upperSignature = FunctionSignature("upper", List("value" -> SemanticType.SString), SemanticType.SString, "stdlib.upper")
  val lowerSignature = FunctionSignature("lower", List("value" -> SemanticType.SString), SemanticType.SString, "stdlib.lower")
  val lengthSignature = FunctionSignature("string-length", List("value" -> SemanticType.SString), SemanticType.SInt, "stdlib.string-length")
}

// =============================================================================
// Boolean Operations
// =============================================================================

object BoolOps {
  case class TwoBools(a: Boolean, b: Boolean)
  case class OneBool(value: Boolean)
  case class BoolOut(out: Boolean)

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

  val andSignature = FunctionSignature("and", List("a" -> SemanticType.SBoolean, "b" -> SemanticType.SBoolean), SemanticType.SBoolean, "stdlib.and")
  val orSignature = FunctionSignature("or", List("a" -> SemanticType.SBoolean, "b" -> SemanticType.SBoolean), SemanticType.SBoolean, "stdlib.or")
  val notSignature = FunctionSignature("not", List("value" -> SemanticType.SBoolean), SemanticType.SBoolean, "stdlib.not")
}

// =============================================================================
// Comparison Operations
// =============================================================================

object CompareOps {
  case class TwoInts(a: Long, b: Long)
  case class TwoStrings(a: String, b: String)
  case class BoolOut(out: Boolean)

  val eqIntModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.eq-int", "Check if two integers are equal", 1, 0)
    .tags("stdlib", "comparison")
    .implementationPure[TwoInts, BoolOut](in => BoolOut(in.a == in.b))
    .build

  val eqStringModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.eq-string", "Check if two strings are equal", 1, 0)
    .tags("stdlib", "comparison")
    .implementationPure[TwoStrings, BoolOut](in => BoolOut(in.a == in.b))
    .build

  val gtModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.gt", "Check if a > b", 1, 0)
    .tags("stdlib", "comparison")
    .implementationPure[TwoInts, BoolOut](in => BoolOut(in.a > in.b))
    .build

  val ltModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.lt", "Check if a < b", 1, 0)
    .tags("stdlib", "comparison")
    .implementationPure[TwoInts, BoolOut](in => BoolOut(in.a < in.b))
    .build

  val gteModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.gte", "Check if a >= b", 1, 0)
    .tags("stdlib", "comparison")
    .implementationPure[TwoInts, BoolOut](in => BoolOut(in.a >= in.b))
    .build

  val lteModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.lte", "Check if a <= b", 1, 0)
    .tags("stdlib", "comparison")
    .implementationPure[TwoInts, BoolOut](in => BoolOut(in.a <= in.b))
    .build

  private val twoIntParams = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt)
  private val twoStringParams = List("a" -> SemanticType.SString, "b" -> SemanticType.SString)

  val eqIntSignature = FunctionSignature("eq-int", twoIntParams, SemanticType.SBoolean, "stdlib.eq-int")
  val eqStringSignature = FunctionSignature("eq-string", twoStringParams, SemanticType.SBoolean, "stdlib.eq-string")
  val gtSignature = FunctionSignature("gt", twoIntParams, SemanticType.SBoolean, "stdlib.gt")
  val ltSignature = FunctionSignature("lt", twoIntParams, SemanticType.SBoolean, "stdlib.lt")
  val gteSignature = FunctionSignature("gte", twoIntParams, SemanticType.SBoolean, "stdlib.gte")
  val lteSignature = FunctionSignature("lte", twoIntParams, SemanticType.SBoolean, "stdlib.lte")
}

// =============================================================================
// Debug Operations
// =============================================================================

object DebugOps {
  case class LogIn(message: String)
  case class LogOut(out: String)

  val logModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.log", "Log a message and pass through", 1, 0)
    .tags("stdlib", "debug")
    .implementation[LogIn, LogOut] { in =>
      IO(println(s"[constellation-lang] ${in.message}")).as(LogOut(in.message))
    }
    .build

  val logSignature = FunctionSignature("log", List("message" -> SemanticType.SString), SemanticType.SString, "stdlib.log")
}
