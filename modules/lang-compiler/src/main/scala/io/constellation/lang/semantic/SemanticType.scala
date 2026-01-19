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

  /** Optional<T> - represents a value that may or may not exist */
  final case class SOptional(inner: SemanticType) extends SemanticType {
    def prettyPrint: String = s"Optional<${inner.prettyPrint}>"
  }

  /** Convert SemanticType to Constellation CType */
  def toCType(st: SemanticType): CType = st match {
    case SString           => CType.CString
    case SInt              => CType.CInt
    case SFloat            => CType.CFloat
    case SBoolean          => CType.CBoolean
    case SRecord(fields)   => CType.CProduct(fields.view.mapValues(toCType).toMap)
    case SCandidates(elem) => CType.CList(toCType(elem))
    case SList(elem)       => CType.CList(toCType(elem))
    case SMap(k, v)        => CType.CMap(toCType(k), toCType(v))
    case SOptional(inner)  => CType.COptional(toCType(inner))
  }

  /** Convert Constellation CType to SemanticType */
  def fromCType(ct: CType): SemanticType = ct match {
    case CType.CString          => SString
    case CType.CInt             => SInt
    case CType.CFloat           => SFloat
    case CType.CBoolean         => SBoolean
    case CType.CList(elem)      => SList(fromCType(elem))
    case CType.CMap(k, v)       => SMap(fromCType(k), fromCType(v))
    case CType.CProduct(fields) => SRecord(fields.view.mapValues(fromCType).toMap)
    case CType.CUnion(fields)   => SRecord(fields.view.mapValues(fromCType).toMap)
    case CType.COptional(inner) => SOptional(fromCType(inner))
  }
}

/** Function signature for registered modules */
final case class FunctionSignature(
    name: String,                         // Language name: "ide-ranker-v2"
    params: List[(String, SemanticType)], // Parameter names and types
    returns: SemanticType,                // Return type
    moduleName: String,                   // Constellation module name
    namespace: Option[String] = None      // Optional namespace: "stdlib.math"
) {

  /** Fully qualified name (namespace.name or just name if no namespace) */
  def qualifiedName: String = namespace.map(_ + ".").getOrElse("") + name

  def prettyPrint: String = {
    val paramStr = params.map { case (n, t) => s"$n: ${t.prettyPrint}" }.mkString(", ")
    s"$qualifiedName($paramStr) -> ${returns.prettyPrint}"
  }
}

import io.constellation.lang.ast.{CompileError, QualifiedName, Span}

/** Tracks imported namespaces and aliases within a scope */
final case class NamespaceScope(
    imports: Map[String, String] = Map.empty, // alias -> full namespace
    wildcardImports: Set[String] = Set.empty  // namespaces imported without alias
) {

  /** Add a wildcard import (use stdlib.math) */
  def addWildcard(namespace: String): NamespaceScope =
    copy(wildcardImports = wildcardImports + namespace)

  /** Add an aliased import (use stdlib.math as m) */
  def addAlias(alias: String, namespace: String): NamespaceScope =
    copy(imports = imports + (alias -> namespace))
}

object NamespaceScope {
  val empty: NamespaceScope = NamespaceScope()
}

/** Registry of available functions for type checking */
trait FunctionRegistry {

  /** Lookup by simple name (for backwards compatibility) */
  def lookup(name: String): Option[FunctionSignature]

  /** Lookup all functions with a given simple name (may return multiple from different namespaces)
    */
  def lookupSimple(name: String): List[FunctionSignature]

  /** Lookup by fully qualified name (e.g., "stdlib.math.add") */
  def lookupQualified(qualifiedName: String): Option[FunctionSignature]

  /** Lookup a function in a given namespace scope, resolving imports and aliases */
  def lookupInScope(
      name: QualifiedName,
      scope: NamespaceScope,
      span: Option[Span]
  ): Either[CompileError, FunctionSignature]

  /** Register a function signature */
  def register(sig: FunctionSignature): Unit

  /** Get all registered function signatures */
  def all: List[FunctionSignature]

  /** Get all unique namespaces */
  def namespaces: Set[String]
}

/** In-memory implementation of FunctionRegistry */
class InMemoryFunctionRegistry extends FunctionRegistry {
  // Map from simple name -> list of signatures (may have multiple in different namespaces)
  private var bySimpleName: Map[String, List[FunctionSignature]] = Map.empty
  // Map from qualified name -> signature
  private var byQualifiedName: Map[String, FunctionSignature] = Map.empty
  // All registered namespaces
  private var allNamespaces: Set[String] = Set.empty

  def lookup(name: String): Option[FunctionSignature] =
    // First try qualified, then simple (return first match for backwards compat)
    byQualifiedName.get(name).orElse(bySimpleName.get(name).flatMap(_.headOption))

  def lookupSimple(name: String): List[FunctionSignature] =
    bySimpleName.getOrElse(name, List.empty)

  def lookupQualified(qualifiedName: String): Option[FunctionSignature] =
    byQualifiedName.get(qualifiedName)

  def lookupInScope(
      name: QualifiedName,
      scope: NamespaceScope,
      span: Option[Span]
  ): Either[CompileError, FunctionSignature] =
    if name.isSimple then {
      // Simple name: check imports, then look for unambiguous match
      val simpleName = name.localName

      // Check if the simple name itself is an alias for a namespace
      scope.imports.get(simpleName) match {
        case Some(_) =>
          // The simple name is a namespace alias, so this is an incomplete reference
          Left(CompileError.UndefinedFunction(simpleName, span))

        case None =>
          // Collect all candidates from wildcard imports
          val fromWildcards = scope.wildcardImports.flatMap { ns =>
            lookupQualified(s"$ns.$simpleName")
          }.toList

          // Also check for direct match (no namespace)
          val direct = lookupSimple(simpleName).filter(_.namespace.isEmpty)

          // For backwards compatibility: if no imports are defined, also include functions with namespaces
          val backwardsCompat = if scope.imports.isEmpty && scope.wildcardImports.isEmpty then {
            // Include ALL functions with this simple name for backwards compatibility
            lookupSimple(simpleName).filter(_.namespace.isDefined)
          } else {
            List.empty
          }

          val allCandidates = (direct ++ fromWildcards ++ backwardsCompat).distinct

          allCandidates match {
            case Nil =>
              // No matches - maybe it exists but not imported
              val existing = lookupSimple(simpleName)
              if existing.nonEmpty then {
                Left(
                  CompileError.UndefinedFunction(
                    s"$simpleName (did you mean to import ${existing.map(_.qualifiedName).mkString(" or ")}?)",
                    span
                  )
                )
              } else {
                Left(CompileError.UndefinedFunction(simpleName, span))
              }
            case List(single) => Right(single)
            case multiple =>
              Left(CompileError.AmbiguousFunction(simpleName, multiple.map(_.qualifiedName), span))
          }
      }
    } else {
      // Qualified name: first part might be an alias
      val firstPart = name.parts.head
      val restParts = name.parts.tail

      scope.imports.get(firstPart) match {
        case Some(aliasedNamespace) =>
          // First part is an alias, expand it
          val fullName = (aliasedNamespace :: restParts).mkString(".")
          lookupQualified(fullName).toRight(
            CompileError.UndefinedFunction(name.fullName, span)
          )

        case None =>
          // Try as a fully qualified name
          lookupQualified(name.fullName).toRight(
            // Check if namespace exists but function doesn't
            if allNamespaces.contains(name.namespace.getOrElse("")) then {
              CompileError.UndefinedFunction(name.fullName, span)
            } else {
              name.namespace match {
                case Some(ns) => CompileError.UndefinedNamespace(ns, span)
                case None     => CompileError.UndefinedFunction(name.fullName, span)
              }
            }
          )
      }
    }

  def register(sig: FunctionSignature): Unit = {
    // Register by simple name
    bySimpleName = bySimpleName.updatedWith(sig.name) {
      case Some(existing) => Some(existing :+ sig)
      case None           => Some(List(sig))
    }

    // Register by qualified name
    byQualifiedName = byQualifiedName + (sig.qualifiedName -> sig)

    // Track namespace
    sig.namespace.foreach { ns =>
      allNamespaces = allNamespaces + ns
    }
  }

  def all: List[FunctionSignature] = byQualifiedName.values.toList

  def namespaces: Set[String] = allNamespaces
}

object FunctionRegistry {
  def empty: FunctionRegistry = new InMemoryFunctionRegistry
}
