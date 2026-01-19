package io.constellation.stdlib.categories

import io.constellation.*
import io.constellation.lang.semantic.*

/** List operations for the standard library. */
trait ListFunctions {

  // Input/Output case classes
  case class ListIntIn(list: List[Long])
  case class ListIntOut(out: Long)
  case class ListBoolOut(out: Boolean)

  // Modules
  val listLengthModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.list-length", "Get the length of a list", 1, 0)
    .tags("stdlib", "list")
    .implementationPure[ListIntIn, ListIntOut](in => ListIntOut(in.list.length.toLong))
    .build

  val listFirstModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.list-first", "Get the first element of a list", 1, 0)
    .tags("stdlib", "list")
    .implementationPure[ListIntIn, ListIntOut](in => ListIntOut(in.list.headOption.getOrElse(0L)))
    .build

  val listLastModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.list-last", "Get the last element of a list", 1, 0)
    .tags("stdlib", "list")
    .implementationPure[ListIntIn, ListIntOut](in => ListIntOut(in.list.lastOption.getOrElse(0L)))
    .build

  val listIsEmptyModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.list-is-empty", "Check if a list is empty", 1, 0)
    .tags("stdlib", "list")
    .implementationPure[ListIntIn, ListBoolOut](in => ListBoolOut(in.list.isEmpty))
    .build

  // Signatures
  val listLengthSignature: FunctionSignature = FunctionSignature(
    "list-length",
    List("list" -> SemanticType.SList(SemanticType.SInt)),
    SemanticType.SInt,
    "stdlib.list-length",
    Some("stdlib.list")
  )
  val listFirstSignature: FunctionSignature = FunctionSignature(
    "list-first",
    List("list" -> SemanticType.SList(SemanticType.SInt)),
    SemanticType.SInt,
    "stdlib.list-first",
    Some("stdlib.list")
  )
  val listLastSignature: FunctionSignature = FunctionSignature(
    "list-last",
    List("list" -> SemanticType.SList(SemanticType.SInt)),
    SemanticType.SInt,
    "stdlib.list-last",
    Some("stdlib.list")
  )
  val listIsEmptySignature: FunctionSignature = FunctionSignature(
    "list-is-empty",
    List("list" -> SemanticType.SList(SemanticType.SInt)),
    SemanticType.SBoolean,
    "stdlib.list-is-empty",
    Some("stdlib.list")
  )

  // Collections
  def listSignatures: List[FunctionSignature] = List(
    listLengthSignature,
    listFirstSignature,
    listLastSignature,
    listIsEmptySignature
  )

  def listModules: Map[String, Module.Uninitialized] = Map(
    listLengthModule.spec.name  -> listLengthModule,
    listFirstModule.spec.name   -> listFirstModule,
    listLastModule.spec.name    -> listLastModule,
    listIsEmptyModule.spec.name -> listIsEmptyModule
  )
}
