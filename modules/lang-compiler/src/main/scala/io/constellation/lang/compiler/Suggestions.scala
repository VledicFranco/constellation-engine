package io.constellation.lang.compiler

import io.constellation.lang.ast.CompileError

/** Generates contextual suggestions for compiler errors.
  *
  * Provides "Did you mean?" suggestions using Levenshtein distance and context-aware suggestions
  * based on error type.
  */
object Suggestions {

  /** Find strings similar to the target using Levenshtein distance.
    *
    * @param target
    *   The string to match
    * @param candidates
    *   Available strings to suggest
    * @param maxDistance
    *   Maximum edit distance (default 2)
    * @param maxSuggestions
    *   Maximum suggestions to return (default 3)
    * @return
    *   List of similar strings, sorted by similarity (best first)
    */
  def findSimilar(
      target: String,
      candidates: List[String],
      maxDistance: Int = 2,
      maxSuggestions: Int = 3
  ): List[String] =
    candidates
      .filterNot(_.equalsIgnoreCase(target)) // Exclude exact matches (case-insensitive)
      .map(c => (c, levenshteinDistance(target.toLowerCase, c.toLowerCase)))
      .filter(_._2 <= maxDistance)
      .sortBy(_._2)
      .take(maxSuggestions)
      .map(_._1)

  /** Calculate Levenshtein edit distance between two strings.
    *
    * The Levenshtein distance is the minimum number of single-character edits (insertions,
    * deletions, or substitutions) required to change one string into the other.
    *
    * @param s1
    *   First string
    * @param s2
    *   Second string
    * @return
    *   Edit distance (0 means strings are identical)
    */
  def levenshteinDistance(s1: String, s2: String): Int = {
    val m = s1.length
    val n = s2.length

    // Handle edge cases
    if m == 0 then return n
    if n == 0 then return m

    // Use two-row optimization to reduce memory
    var prevRow = (0 to n).toArray
    var currRow = new Array[Int](n + 1)

    for i <- 1 to m do {
      currRow(0) = i
      for j <- 1 to n do {
        val cost = if s1(i - 1) == s2(j - 1) then 0 else 1
        currRow(j) = Math.min(
          Math.min(prevRow(j) + 1, currRow(j - 1) + 1),
          prevRow(j - 1) + cost
        )
      }
      // Swap rows
      val temp = prevRow
      prevRow = currRow
      currRow = temp
    }

    prevRow(n)
  }

  /** Generate contextual suggestions for a compile error.
    *
    * @param error
    *   The compile error
    * @param context
    *   Compilation context with available symbols
    * @return
    *   List of suggestion strings
    */
  def forError(error: CompileError, context: SuggestionContext): List[String] =
    error match {
      case CompileError.UndefinedVariable(name, _) =>
        val similar = findSimilar(name, context.definedVariables)
        similar.map(s => s"Did you mean '$s'?") ++
          (if similar.isEmpty then List(s"Declare the variable: in $name: String") else Nil)

      case CompileError.UndefinedFunction(name, _) =>
        val similar           = findSimilar(name, context.availableFunctions)
        val didYouMean        = similar.map(s => s"Did you mean '$s'?")
        val importSuggestions = suggestImports(name, context)
        didYouMean ++ importSuggestions

      case CompileError.UndefinedType(name, _) =>
        val builtInTypes = List("String", "Int", "Float", "Boolean", "List", "Map", "Optional")
        val allTypes     = builtInTypes ++ context.definedTypes
        val similar      = findSimilar(name, allTypes)
        similar.map(s => s"Did you mean '$s'?")

      case CompileError.InvalidProjection(field, availableFields, _) =>
        val similar = findSimilar(field, availableFields)
        similar.map(s => s"Did you mean '$s'?") ++
          (if availableFields.nonEmpty then
             List(s"Available fields: ${availableFields.mkString(", ")}")
           else Nil)

      case CompileError.InvalidFieldAccess(field, availableFields, _) =>
        val similar = findSimilar(field, availableFields)
        similar.map(s => s"Did you mean '.$s'?") ++
          (if availableFields.nonEmpty then
             List(s"Available fields: ${availableFields.mkString(", ")}")
           else Nil)

      case CompileError.TypeMismatch(expected, actual, _) =>
        suggestTypeConversion(expected, actual)

      case CompileError.UndefinedNamespace(namespace, _) =>
        val similar = findSimilar(namespace, context.availableNamespaces)
        similar.map(s => s"Did you mean '$s'?") ++
          (if context.availableNamespaces.nonEmpty then
             List(s"Available namespaces: ${context.availableNamespaces.mkString(", ")}")
           else Nil)

      case CompileError.AmbiguousFunction(name, candidates, _) =>
        candidates.take(3).map(c => s"Use '$c' for the specific function")

      case _ => Nil
    }

  /** Suggest namespace imports for an undefined function */
  private def suggestImports(name: String, context: SuggestionContext): List[String] =
    context.functionsByNamespace
      .filter { case (_, functions) =>
        functions.exists(_.equalsIgnoreCase(name))
      }
      .keys
      .take(2)
      .map(ns => s"Try adding: use $ns")
      .toList

  /** Suggest type conversions for type mismatches */
  private def suggestTypeConversion(expected: String, actual: String): List[String] =
    (expected, actual) match {
      case ("String", "Int" | "Float" | "Boolean") =>
        List(s"Use ToString($actual) to convert to String")
      case ("Int", "Float") =>
        List("Use ToInt(value) to convert Float to Int (truncates)")
      case ("Float", "Int") =>
        List("Int values are automatically promoted to Float")
      case ("Boolean", _) =>
        List(s"Use a comparison expression to get a Boolean (e.g., x > 0)")
      case (exp, act) if exp.startsWith("Optional<") && !act.startsWith("Optional<") =>
        List(s"Wrap the value: Some($act)")
      case (exp, act) if !exp.startsWith("Optional<") && act.startsWith("Optional<") =>
        List("Use the coalesce operator: optional ?? defaultValue")
      case _ => Nil
    }
}

/** Context for generating suggestions.
  *
  * Contains information about available symbols in the current compilation.
  */
case class SuggestionContext(
    definedVariables: List[String] = Nil,
    definedTypes: List[String] = Nil,
    availableFunctions: List[String] = Nil,
    availableNamespaces: List[String] = Nil,
    functionsByNamespace: Map[String, List[String]] = Map.empty
)

object SuggestionContext {
  val empty: SuggestionContext = SuggestionContext()
}
