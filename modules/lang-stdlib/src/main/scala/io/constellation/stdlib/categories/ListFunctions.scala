package io.constellation.stdlib.categories

import cats.effect.IO

import io.constellation.*
import io.constellation.lang.semantic.*

/** List operations for the standard library. */
trait ListFunctions {

  // Input/Output case classes
  case class ListIntIn(list: List[Long])
  case class ListIntOut(out: Long)
  case class ListBoolOut(out: Boolean)
  case class TwoListInts(a: List[Long], b: List[Long])
  case class ListIntAndValue(list: List[Long], value: Long)
  case class ListIntListOut(out: List[Long])

  // Modules
  val listLengthModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.list-length", "Get the length of a list", 1, 0)
    .tags("stdlib", "list")
    .implementationPure[ListIntIn, ListIntOut](in => ListIntOut(in.list.length.toLong))
    .build

  val listFirstModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.list-first", "Get the first element of a list", 1, 0)
    .tags("stdlib", "list")
    .implementation[ListIntIn, ListIntOut] { in =>
      in.list.headOption match {
        case Some(v) => IO.pure(ListIntOut(v))
        case None => IO.raiseError(new NoSuchElementException("stdlib.list-first: list is empty"))
      }
    }
    .build

  val listLastModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.list-last", "Get the last element of a list", 1, 0)
    .tags("stdlib", "list")
    .implementation[ListIntIn, ListIntOut] { in =>
      in.list.lastOption match {
        case Some(v) => IO.pure(ListIntOut(v))
        case None    => IO.raiseError(new NoSuchElementException("stdlib.list-last: list is empty"))
      }
    }
    .build

  val listIsEmptyModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.list-is-empty", "Check if a list is empty", 1, 0)
    .tags("stdlib", "list")
    .implementationPure[ListIntIn, ListBoolOut](in => ListBoolOut(in.list.isEmpty))
    .build

  val listSumModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.list-sum", "Sum all elements in a list", 1, 0)
    .tags("stdlib", "list")
    .implementationPure[ListIntIn, ListIntOut](in => ListIntOut(in.list.sum))
    .build

  val listConcatModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.list-concat", "Concatenate two lists", 1, 0)
    .tags("stdlib", "list")
    .implementationPure[TwoListInts, ListIntListOut](in => ListIntListOut(in.a ++ in.b))
    .build

  val listContainsModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.list-contains", "Check if element exists in list", 1, 0)
    .tags("stdlib", "list")
    .implementationPure[ListIntAndValue, ListBoolOut](in => ListBoolOut(in.list.contains(in.value)))
    .build

  val listReverseModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.list-reverse", "Reverse list order", 1, 0)
    .tags("stdlib", "list")
    .implementationPure[ListIntIn, ListIntListOut](in => ListIntListOut(in.list.reverse))
    .build

  // Signatures
  val listLengthSignature: FunctionSignature = FunctionSignature(
    "list-length",
    List("list" -> SemanticType.SSeq(SemanticType.SInt)),
    SemanticType.SInt,
    "stdlib.list-length",
    Some("stdlib.list")
  )
  val listFirstSignature: FunctionSignature = FunctionSignature(
    "list-first",
    List("list" -> SemanticType.SSeq(SemanticType.SInt)),
    SemanticType.SInt,
    "stdlib.list-first",
    Some("stdlib.list")
  )
  val listLastSignature: FunctionSignature = FunctionSignature(
    "list-last",
    List("list" -> SemanticType.SSeq(SemanticType.SInt)),
    SemanticType.SInt,
    "stdlib.list-last",
    Some("stdlib.list")
  )
  val listIsEmptySignature: FunctionSignature = FunctionSignature(
    "list-is-empty",
    List("list" -> SemanticType.SSeq(SemanticType.SInt)),
    SemanticType.SBoolean,
    "stdlib.list-is-empty",
    Some("stdlib.list")
  )
  val listSumSignature: FunctionSignature = FunctionSignature(
    "list-sum",
    List("list" -> SemanticType.SSeq(SemanticType.SInt)),
    SemanticType.SInt,
    "stdlib.list-sum",
    Some("stdlib.list")
  )
  val listConcatSignature: FunctionSignature = FunctionSignature(
    "list-concat",
    List(
      "a" -> SemanticType.SSeq(SemanticType.SInt),
      "b" -> SemanticType.SSeq(SemanticType.SInt)
    ),
    SemanticType.SSeq(SemanticType.SInt),
    "stdlib.list-concat",
    Some("stdlib.list")
  )
  val listContainsSignature: FunctionSignature = FunctionSignature(
    "list-contains",
    List("list" -> SemanticType.SSeq(SemanticType.SInt), "value" -> SemanticType.SInt),
    SemanticType.SBoolean,
    "stdlib.list-contains",
    Some("stdlib.list")
  )
  val listReverseSignature: FunctionSignature = FunctionSignature(
    "list-reverse",
    List("list" -> SemanticType.SSeq(SemanticType.SInt)),
    SemanticType.SSeq(SemanticType.SInt),
    "stdlib.list-reverse",
    Some("stdlib.list")
  )

  // Collections
  def listSignatures: List[FunctionSignature] = List(
    listLengthSignature,
    listFirstSignature,
    listLastSignature,
    listIsEmptySignature,
    listSumSignature,
    listConcatSignature,
    listContainsSignature,
    listReverseSignature
  )

  def listModules: Map[String, Module.Uninitialized] = Map(
    listLengthModule.spec.name   -> listLengthModule,
    listFirstModule.spec.name    -> listFirstModule,
    listLastModule.spec.name     -> listLastModule,
    listIsEmptyModule.spec.name  -> listIsEmptyModule,
    listSumModule.spec.name      -> listSumModule,
    listConcatModule.spec.name   -> listConcatModule,
    listContainsModule.spec.name -> listContainsModule,
    listReverseModule.spec.name  -> listReverseModule
  )
}
