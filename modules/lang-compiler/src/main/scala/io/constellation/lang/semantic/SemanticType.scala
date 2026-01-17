package io.constellation.lang.semantic

import io.constellation.CType

/** Internal type representation for the type checker */
sealed trait SemanticType {
  def prettyPrint: String
}

object SemanticType {
  case object SString extends SemanticType {
    def prettyPrint: String = "String"
  }

  case object SInt extends SemanticType {
    def prettyPrint: String = "Int"
  }

  case object SFloat extends SemanticType {
    def prettyPrint: String = "Float"
  }

  case object SBoolean extends SemanticType {
    def prettyPrint: String = "Boolean"
  }

  /** Record type with named fields */
  final case class SRecord(fields: Map[String, SemanticType]) extends SemanticType {
    def prettyPrint: String = {
      val fieldStrs = fields.map { case (n, t) => s"$n: ${t.prettyPrint}" }.mkString(", ")
      s"{ $fieldStrs }"
    }
  }

  /** Candidates<T> - a collection type for ML batching */
  final case class SCandidates(element: SemanticType) extends SemanticType {
    def prettyPrint: String = s"Candidates<${element.prettyPrint}>"
  }

  /** List<T> */
  final case class SList(element: SemanticType) extends SemanticType {
    def prettyPrint: String = s"List<${element.prettyPrint}>"
  }

  /** Map<K, V> */
  final case class SMap(key: SemanticType, value: SemanticType) extends SemanticType {
    def prettyPrint: String = s"Map<${key.prettyPrint}, ${value.prettyPrint}>"
  }

  /** Convert SemanticType to Constellation CType */
  def toCType(st: SemanticType): CType = st match {
    case SString => CType.CString
    case SInt => CType.CInt
    case SFloat => CType.CFloat
    case SBoolean => CType.CBoolean
    case SRecord(fields) => CType.CProduct(fields.view.mapValues(toCType).toMap)
    case SCandidates(elem) => CType.CList(toCType(elem))
    case SList(elem) => CType.CList(toCType(elem))
    case SMap(k, v) => CType.CMap(toCType(k), toCType(v))
  }

  /** Convert Constellation CType to SemanticType */
  def fromCType(ct: CType): SemanticType = ct match {
    case CType.CString => SString
    case CType.CInt => SInt
    case CType.CFloat => SFloat
    case CType.CBoolean => SBoolean
    case CType.CList(elem) => SList(fromCType(elem))
    case CType.CMap(k, v) => SMap(fromCType(k), fromCType(v))
    case CType.CProduct(fields) => SRecord(fields.view.mapValues(fromCType).toMap)
    case CType.CUnion(fields) => SRecord(fields.view.mapValues(fromCType).toMap)
  }
}

/** Function signature for registered modules */
final case class FunctionSignature(
  name: String,                              // Language name: "ide-ranker-v2"
  params: List[(String, SemanticType)],      // Parameter names and types
  returns: SemanticType,                     // Return type
  moduleName: String                         // Constellation module name
) {
  def prettyPrint: String = {
    val paramStr = params.map { case (n, t) => s"$n: ${t.prettyPrint}" }.mkString(", ")
    s"$name($paramStr) -> ${returns.prettyPrint}"
  }
}

/** Registry of available functions for type checking */
trait FunctionRegistry {
  def lookup(name: String): Option[FunctionSignature]
  def register(sig: FunctionSignature): Unit
  def all: List[FunctionSignature]
}

/** In-memory implementation of FunctionRegistry */
class InMemoryFunctionRegistry extends FunctionRegistry {
  private var functions: Map[String, FunctionSignature] = Map.empty

  def lookup(name: String): Option[FunctionSignature] = functions.get(name)

  def register(sig: FunctionSignature): Unit = {
    functions = functions + (sig.name -> sig)
  }

  def all: List[FunctionSignature] = functions.values.toList
}

object FunctionRegistry {
  def empty: FunctionRegistry = new InMemoryFunctionRegistry
}
