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

  /** Bottom type - subtype of all types. Used for empty collections. */
  case object SNothing extends SemanticType {
    def prettyPrint: String = "Nothing"
  }

  /** Record type with named fields */
  final case class SRecord(fields: Map[String, SemanticType]) extends SemanticType {
    def prettyPrint: String = {
      val fieldStrs = fields.map { case (n, t) => s"$n: ${t.prettyPrint}" }.mkString(", ")
      s"{ $fieldStrs }"
    }
  }

  /** List<T> - collection type with element-wise operations for records.
    *
    * When the element type is a record, List supports:
    *   - Element-wise merge: `list + record` adds fields to each element
    *   - Element-wise projection: `list[field1, field2]` selects fields from each element
    *   - Element-wise field access: `list.field` extracts a field from each element
    *
    * Note: "Candidates" is a legacy alias for List and resolves to SList.
    */
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

  /** Function type: (A, B) => C Used for lambda expressions and higher-order function parameters.
    * Function types exist only at compile-time and have no runtime CType representation.
    */
  final case class SFunction(paramTypes: List[SemanticType], returnType: SemanticType)
      extends SemanticType {
    def prettyPrint: String = {
      val params = paramTypes.map(_.prettyPrint).mkString(", ")
      s"($params) => ${returnType.prettyPrint}"
    }
  }

  /** Union type: A | B | C - value can be any of the member types */
  final case class SUnion(members: Set[SemanticType]) extends SemanticType {
    def prettyPrint: String = members.map(_.prettyPrint).toList.sorted.mkString(" | ")
  }

  /** Row variable - represents unknown additional fields in open records. Used for row
    * polymorphism: { name: String | ρ } means "a record with at least name".
    */
  final case class RowVar(id: Int) extends SemanticType {
    def prettyPrint: String = s"ρ$id"
  }

  /** Open record type - has specific fields plus a row variable for "rest". Enables row
    * polymorphism where functions accept records with "at least" certain fields.
    *
    * Example: SOpenRecord(Map("name" -> SString), RowVar(1)) represents { name: String | ρ1 } This
    * matches any record with at least a "name" field of type String.
    */
  final case class SOpenRecord(
      fields: Map[String, SemanticType],
      rowVar: RowVar
  ) extends SemanticType {
    def prettyPrint: String = {
      val fieldStr = fields.map { case (k, v) => s"$k: ${v.prettyPrint}" }.mkString(", ")
      if fieldStr.isEmpty then s"{ | ${rowVar.prettyPrint} }"
      else s"{ $fieldStr | ${rowVar.prettyPrint} }"
    }
  }

  /** Convert SemanticType to Constellation CType */
  def toCType(st: SemanticType): CType = st match {
    case SString          => CType.CString
    case SInt             => CType.CInt
    case SFloat           => CType.CFloat
    case SBoolean         => CType.CBoolean
    case SNothing         => CType.CString // Bottom type - use String as default for runtime
    case SRecord(fields)  => CType.CProduct(fields.view.mapValues(toCType).toMap)
    case SList(elem)      => CType.CList(toCType(elem))
    case SMap(k, v)       => CType.CMap(toCType(k), toCType(v))
    case SOptional(inner) => CType.COptional(toCType(inner))
    case SFunction(_, _) =>
      throw new IllegalArgumentException(
        "Function types cannot be converted to CType - they exist only at compile time"
      )
    case SUnion(members) =>
      // Use prettyPrint as tag name for each member type
      CType.CUnion(members.map(m => m.prettyPrint -> toCType(m)).toMap)
    case RowVar(_) =>
      throw new IllegalArgumentException(
        "Row variables cannot be converted to CType - they must be resolved during type checking"
      )
    case SOpenRecord(_, _) =>
      throw new IllegalArgumentException(
        "Open record types cannot be converted to CType - they must be closed during type checking"
      )
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
    case CType.CUnion(fields)   => SUnion(fields.values.map(fromCType).toSet)
    case CType.COptional(inner) => SOptional(fromCType(inner))
  }
}

/** Function signature for registered modules */
final case class FunctionSignature(
    name: String,                            // Language name: "ide-ranker-v2"
    params: List[(String, SemanticType)],    // Parameter names and types
    returns: SemanticType,                   // Return type
    moduleName: String,                      // Constellation module name
    namespace: Option[String] = None,        // Optional namespace: "stdlib.math"
    rowVars: List[SemanticType.RowVar] = Nil // Row variables this signature quantifies over
) {

  /** Fully qualified name (namespace.name or just name if no namespace) */
  def qualifiedName: String = namespace.map(_ + ".").getOrElse("") + name

  /** Is this signature row-polymorphic? */
  def isRowPolymorphic: Boolean = rowVars.nonEmpty

  def prettyPrint: String = {
    val paramStr = params.map { case (n, t) => s"$n: ${t.prettyPrint}" }.mkString(", ")
    val rowVarStr =
      if rowVars.isEmpty then "" else s"∀${rowVars.map(_.prettyPrint).mkString(", ")}. "
    s"$rowVarStr$qualifiedName($paramStr) -> ${returns.prettyPrint}"
  }

  /** Create a fresh instantiation of this signature with new row variables. Each call to a
    * row-polymorphic function gets fresh row variables to avoid interference between different call
    * sites.
    */
  def instantiate(freshVarGen: () => SemanticType.RowVar): FunctionSignature =
    if !isRowPolymorphic then this
    else {
      val mapping = rowVars.map(rv => rv -> freshVarGen()).toMap
      copy(
        params = params.map { case (n, t) => (n, substituteRowVars(t, mapping)) },
        returns = substituteRowVars(returns, mapping),
        rowVars = mapping.values.toList
      )
    }

  private def substituteRowVars(
      t: SemanticType,
      mapping: Map[SemanticType.RowVar, SemanticType.RowVar]
  ): SemanticType = {
    import SemanticType.*
    t match {
      case SOpenRecord(fields, rv) =>
        SOpenRecord(
          fields.view.mapValues(substituteRowVars(_, mapping)).toMap,
          mapping.getOrElse(rv, rv)
        )
      case SRecord(fields) =>
        SRecord(fields.view.mapValues(substituteRowVars(_, mapping)).toMap)
      case SList(elem) =>
        SList(substituteRowVars(elem, mapping))
      case SOptional(inner) =>
        SOptional(substituteRowVars(inner, mapping))
      // Note: SCandidates was removed - "Candidates" is now a legacy alias for List
      case SMap(k, v) =>
        SMap(substituteRowVars(k, mapping), substituteRowVars(v, mapping))
      case SFunction(ps, ret) =>
        SFunction(ps.map(substituteRowVars(_, mapping)), substituteRowVars(ret, mapping))
      case SUnion(members) =>
        SUnion(members.map(substituteRowVars(_, mapping)))
      case rv: RowVar =>
        mapping.getOrElse(rv, rv)
      case other => other
    }
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

  /** Deregister a function by its fully qualified name.
    *
    * Removes from qualified name index, simple name index, and namespace tracking. No-op if the
    * function is not registered.
    */
  def deregister(qualifiedName: String): Unit

  /** Get all registered function signatures */
  def all: List[FunctionSignature]

  /** Get all unique namespaces */
  def namespaces: Set[String]
}

/** Thread-safe in-memory implementation of FunctionRegistry.
  *
  * Uses AtomicReference for lock-free concurrent access. Registration and deregistration from
  * provider threads is safe while compiler threads read.
  */
class InMemoryFunctionRegistry extends FunctionRegistry {
  import java.util.concurrent.atomic.AtomicReference

  // Map from simple name -> list of signatures (may have multiple in different namespaces)
  private val bySimpleName = new AtomicReference(Map.empty[String, List[FunctionSignature]])
  // Map from qualified name -> signature
  private val byQualifiedName = new AtomicReference(Map.empty[String, FunctionSignature])
  // All registered namespaces
  private val allNamespaces = new AtomicReference(Set.empty[String])

  def lookup(name: String): Option[FunctionSignature] =
    // First try qualified, then simple (return first match for backwards compat)
    byQualifiedName.get().get(name).orElse(bySimpleName.get().get(name).flatMap(_.headOption))

  def lookupSimple(name: String): List[FunctionSignature] =
    bySimpleName.get().getOrElse(name, List.empty)

  def lookupQualified(qualifiedName: String): Option[FunctionSignature] =
    byQualifiedName.get().get(qualifiedName)

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
            if allNamespaces.get().contains(name.namespace.getOrElse("")) then {
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
    // Atomically update all three indexes. Each uses updateAndGet for thread-safety.
    bySimpleName.updateAndGet { current =>
      current.updatedWith(sig.name) {
        case Some(existing) => Some(existing :+ sig)
        case None           => Some(List(sig))
      }
    }

    byQualifiedName.updateAndGet(_ + (sig.qualifiedName -> sig))

    sig.namespace.foreach { ns =>
      allNamespaces.updateAndGet(_ + ns)
    }
  }

  def deregister(qualifiedName: String): Unit = {
    // Remove from qualified name index, capturing the signature for cleanup
    val removed = byQualifiedName.get().get(qualifiedName)

    removed.foreach { sig =>
      byQualifiedName.updateAndGet(_ - qualifiedName)

      // Remove from simple name index
      bySimpleName.updateAndGet { current =>
        current.updatedWith(sig.name) {
          case Some(existing) =>
            val filtered = existing.filterNot(_.qualifiedName == qualifiedName)
            if filtered.isEmpty then None else Some(filtered)
          case None => None
        }
      }

      // Remove namespace if no other functions use it
      sig.namespace.foreach { ns =>
        val remaining = byQualifiedName.get().values.exists(_.namespace.contains(ns))
        if !remaining then {
          allNamespaces.updateAndGet(_ - ns)
        }
      }
    }
  }

  def all: List[FunctionSignature] = byQualifiedName.get().values.toList

  def namespaces: Set[String] = allNamespaces.get()
}

object FunctionRegistry {
  def empty: FunctionRegistry = new InMemoryFunctionRegistry
}
