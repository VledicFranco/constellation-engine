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
  case class StringBoolOut(out: Boolean)
  case class ListStringAndSeparator(list: List[String], separator: String)
  case class StringAndSubstring(value: String, substring: String)
  case class ReplaceIn(value: String, target: String, replacement: String)
  case class StringListOut(out: List[String])

  // Modules
  val concatModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.concat", "Concatenate two strings", 1, 0)
    .tags("stdlib", "string")
    .implementationPure[TwoStrings, StringOut](in => StringOut(in.a + in.b))
    .build

  val stringLengthModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.string-length", "Get string length", 1, 0)
    .tags("stdlib", "string")
    .implementationPure[OneString, StringIntOut](in => StringIntOut(in.value.length.toLong))
    .build

  val joinModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.join", "Join strings with delimiter", 1, 0)
    .tags("stdlib", "string")
    .implementationPure[ListStringAndSeparator, StringOut](in =>
      StringOut(in.list.mkString(in.separator))
    )
    .build

  val splitModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.split", "Split string by delimiter", 1, 0)
    .tags("stdlib", "string")
    .implementationPure[StringAndSubstring, StringListOut](in =>
      StringListOut(in.value.split(java.util.regex.Pattern.quote(in.substring), -1).toList)
    )
    .build

  val containsModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.contains", "Check if string contains substring", 1, 0)
    .tags("stdlib", "string")
    .implementationPure[StringAndSubstring, StringBoolOut](in =>
      StringBoolOut(in.value.contains(in.substring))
    )
    .build

  val trimModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.trim", "Trim whitespace from string", 1, 0)
    .tags("stdlib", "string")
    .implementationPure[OneString, StringOut](in => StringOut(in.value.trim))
    .build

  val replaceModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.replace", "Replace occurrences in string", 1, 0)
    .tags("stdlib", "string")
    .implementationPure[ReplaceIn, StringOut](in =>
      StringOut(in.value.replace(in.target, in.replacement))
    )
    .build

  // Signatures
  val concatSignature: FunctionSignature = FunctionSignature(
    "concat",
    List("a" -> SemanticType.SString, "b" -> SemanticType.SString),
    SemanticType.SString,
    "stdlib.concat",
    Some("stdlib.string")
  )
  val stringLengthSignature: FunctionSignature = FunctionSignature(
    "string-length",
    List("value" -> SemanticType.SString),
    SemanticType.SInt,
    "stdlib.string-length",
    Some("stdlib.string")
  )
  val joinSignature: FunctionSignature = FunctionSignature(
    "join",
    List("list" -> SemanticType.SList(SemanticType.SString), "separator" -> SemanticType.SString),
    SemanticType.SString,
    "stdlib.join",
    Some("stdlib.string")
  )
  val splitSignature: FunctionSignature = FunctionSignature(
    "split",
    List("value" -> SemanticType.SString, "substring" -> SemanticType.SString),
    SemanticType.SList(SemanticType.SString),
    "stdlib.split",
    Some("stdlib.string")
  )
  val containsSignature: FunctionSignature = FunctionSignature(
    "contains",
    List("value" -> SemanticType.SString, "substring" -> SemanticType.SString),
    SemanticType.SBoolean,
    "stdlib.contains",
    Some("stdlib.string")
  )
  val trimSignature: FunctionSignature = FunctionSignature(
    "trim",
    List("value" -> SemanticType.SString),
    SemanticType.SString,
    "stdlib.trim",
    Some("stdlib.string")
  )
  val replaceSignature: FunctionSignature = FunctionSignature(
    "replace",
    List(
      "value"       -> SemanticType.SString,
      "target"      -> SemanticType.SString,
      "replacement" -> SemanticType.SString
    ),
    SemanticType.SString,
    "stdlib.replace",
    Some("stdlib.string")
  )

  // Collections
  def stringSignatures: List[FunctionSignature] = List(
    concatSignature,
    stringLengthSignature,
    joinSignature,
    splitSignature,
    containsSignature,
    trimSignature,
    replaceSignature
  )

  def stringModules: Map[String, Module.Uninitialized] = Map(
    concatModule.spec.name       -> concatModule,
    stringLengthModule.spec.name -> stringLengthModule,
    joinModule.spec.name         -> joinModule,
    splitModule.spec.name        -> splitModule,
    containsModule.spec.name     -> containsModule,
    trimModule.spec.name         -> trimModule,
    replaceModule.spec.name      -> replaceModule
  )
}
