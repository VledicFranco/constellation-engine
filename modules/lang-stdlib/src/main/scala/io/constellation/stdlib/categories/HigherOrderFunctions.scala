package io.constellation.stdlib.categories

import io.constellation.lang.semantic.*

/** Higher-order functions for list processing in the standard library.
  *
  * These functions accept lambda expressions and are processed specially by the compiler. The
  * runtime execution is handled via InlineTransform (FilterTransform, MapTransform, etc.) rather
  * than traditional Module implementations.
  *
  * Usage in constellation-lang:
  * {{{
  * use stdlib.collection
  *
  * in numbers: List<Int>
  *
  * positives = filter(numbers, (x) => x > 0)
  * doubled = map(numbers, (x) => x * 2)
  * allPositive = all(numbers, (x) => x > 0)
  * anyNegative = any(numbers, (x) => x < 0)
  * }}}
  */
trait HigherOrderFunctions {

  // Note: HOF functions don't have Module implementations because they use
  // InlineTransform at runtime. The DagCompiler creates FilterTransform,
  // MapTransform, AllTransform, or AnyTransform nodes which handle execution.

  // Signatures for higher-order functions

  /** filter: (List<Int>, (Int) => Boolean) => List<Int> Keep elements that satisfy the predicate.
    */
  val filterIntSignature: FunctionSignature = FunctionSignature(
    name = "filter",
    params = List(
      "items"     -> SemanticType.SList(SemanticType.SInt),
      "predicate" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
    ),
    returns = SemanticType.SList(SemanticType.SInt),
    moduleName = "stdlib.hof.filter-int",
    namespace = Some("stdlib.collection")
  )

  /** map: (List<Int>, (Int) => Int) => List<Int> Transform each element using the transform
    * function.
    */
  val mapIntIntSignature: FunctionSignature = FunctionSignature(
    name = "map",
    params = List(
      "items"     -> SemanticType.SList(SemanticType.SInt),
      "transform" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SInt)
    ),
    returns = SemanticType.SList(SemanticType.SInt),
    moduleName = "stdlib.hof.map-int-int",
    namespace = Some("stdlib.collection")
  )

  /** all: (List<Int>, (Int) => Boolean) => Boolean Returns true if all elements satisfy the
    * predicate.
    */
  val allIntSignature: FunctionSignature = FunctionSignature(
    name = "all",
    params = List(
      "items"     -> SemanticType.SList(SemanticType.SInt),
      "predicate" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
    ),
    returns = SemanticType.SBoolean,
    moduleName = "stdlib.hof.all-int",
    namespace = Some("stdlib.collection")
  )

  /** any: (List<Int>, (Int) => Boolean) => Boolean Returns true if any element satisfies the
    * predicate.
    */
  val anyIntSignature: FunctionSignature = FunctionSignature(
    name = "any",
    params = List(
      "items"     -> SemanticType.SList(SemanticType.SInt),
      "predicate" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
    ),
    returns = SemanticType.SBoolean,
    moduleName = "stdlib.hof.any-int",
    namespace = Some("stdlib.collection")
  )

  // Collection of signatures

  def hofSignatures: List[FunctionSignature] = List(
    filterIntSignature,
    mapIntIntSignature,
    allIntSignature,
    anyIntSignature
  )

  // No modules needed - InlineTransform handles execution
  def hofModules: Map[String, Nothing] = Map.empty
}
