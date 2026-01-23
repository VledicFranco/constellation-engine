package io.constellation.lang.semantic

import io.constellation.lang.semantic.SemanticType._

/** Row unification for row polymorphism.
  *
  * Row unification allows closed records to be passed to functions expecting
  * open records. The extra fields in the closed record are captured by the
  * row variable.
  *
  * Example:
  * {{{
  * // Function signature: GetName: { name: String | ρ } -> String
  * // Actual argument: { name: String, age: Int, email: String }
  *
  * // Unification:
  * // - Required fields match: name: String ✓
  * // - Row variable ρ captures: { age: Int, email: String }
  * }}}
  */
object RowUnification {

  /** Unification errors */
  sealed trait UnificationError {
    def message: String
  }

  case class MissingFields(fields: Set[String]) extends UnificationError {
    def message: String = s"Missing required field(s): ${fields.mkString(", ")}"
  }

  case class FieldTypeMismatch(field: String, expected: SemanticType, actual: SemanticType) extends UnificationError {
    def message: String = s"Field '$field' has type ${actual.prettyPrint} but expected ${expected.prettyPrint}"
  }

  case class IncompatibleTypes(expected: SemanticType, actual: SemanticType) extends UnificationError {
    def message: String = s"Expected ${expected.prettyPrint} but got ${actual.prettyPrint}"
  }

  /** Substitution mapping row variables to their resolved field sets.
    *
    * When a closed record unifies with an open record, the row variable
    * is bound to the "extra" fields from the closed record.
    */
  case class Substitution(rowSubst: Map[RowVar, Map[String, SemanticType]] = Map.empty) {

    /** Get the fields bound to a row variable */
    def apply(rowVar: RowVar): Option[Map[String, SemanticType]] = rowSubst.get(rowVar)

    /** Add a row variable binding */
    def withRowVar(rv: RowVar, fields: Map[String, SemanticType]): Substitution =
      copy(rowSubst = rowSubst + (rv -> fields))

    /** Merge two substitutions (second wins on conflicts) */
    def merge(other: Substitution): Substitution =
      copy(rowSubst = rowSubst ++ other.rowSubst)

    /** Check if substitution is empty */
    def isEmpty: Boolean = rowSubst.isEmpty

    /** Check if all row variables in the given list are bound */
    def isBound(rowVars: List[RowVar]): Boolean =
      rowVars.forall(rowSubst.contains)
  }

  object Substitution {
    val empty: Substitution = Substitution()
  }

  /** Unify a closed record with an open record.
    *
    * Checks that the closed record has all fields required by the open record,
    * and that those field types are subtypes of the expected types.
    * The row variable captures any extra fields from the closed record.
    *
    * @param closed The concrete closed record type (the actual argument)
    * @param open The open record type with row variable (the expected parameter)
    * @return Either an error or a substitution binding the row variable to extra fields
    */
  def unifyClosedWithOpen(
    closed: SRecord,
    open: SOpenRecord
  ): Either[UnificationError, Substitution] = {
    // Check that closed record has all required fields
    val missingFields = open.fields.keySet.diff(closed.fields.keySet)
    if (missingFields.nonEmpty) {
      Left(MissingFields(missingFields))
    } else {
      // Check that field types are compatible (using subtyping)
      val fieldMismatch = open.fields.collectFirst {
        case (fieldName, expectedType) if !Subtyping.isSubtype(closed.fields(fieldName), expectedType) =>
          FieldTypeMismatch(fieldName, expectedType, closed.fields(fieldName))
      }

      fieldMismatch match {
        case Some(error) => Left(error)
        case None =>
          // Row variable captures extra fields
          val extraFields = closed.fields.view.filterKeys(k => !open.fields.contains(k)).toMap
          Right(Substitution.empty.withRowVar(open.rowVar, extraFields))
      }
    }
  }

  /** Unify two open records.
    *
    * This is more complex than closed-to-open unification because both
    * records have row variables that need to be related.
    *
    * @param actual The actual open record type
    * @param expected The expected open record type
    * @param subst Existing substitutions to extend
    * @return Either an error or an updated substitution
    */
  def unifyOpenWithOpen(
    actual: SOpenRecord,
    expected: SOpenRecord,
    subst: Substitution
  ): Either[UnificationError, Substitution] = {
    // Check that actual has all fields required by expected
    val missingFields = expected.fields.keySet.diff(actual.fields.keySet)
    if (missingFields.nonEmpty) {
      Left(MissingFields(missingFields))
    } else {
      // Check field type compatibility
      val fieldMismatch = expected.fields.collectFirst {
        case (fieldName, expectedType) if !Subtyping.isSubtype(actual.fields(fieldName), expectedType) =>
          FieldTypeMismatch(fieldName, expectedType, actual.fields(fieldName))
      }

      fieldMismatch match {
        case Some(error) => Left(error)
        case None =>
          // The actual record's extra fields plus its row variable should unify
          // with the expected record's row variable
          val actualExtraFields = actual.fields.view.filterKeys(k => !expected.fields.contains(k)).toMap

          // For now, we bind the expected row variable to the actual extra fields
          // (This is a simplification - full row unification would relate the row variables)
          Right(subst.withRowVar(expected.rowVar, actualExtraFields))
      }
    }
  }

  /** Apply substitution to a type, replacing open records with closed records
    * when their row variables are fully resolved.
    */
  def applySubstitution(typ: SemanticType, subst: Substitution): SemanticType = {
    typ match {
      case SOpenRecord(fields, rowVar) =>
        subst(rowVar) match {
          case Some(extraFields) =>
            // Close the record by combining fields with resolved row variable
            val allFields = fields.view.mapValues(applySubstitution(_, subst)).toMap ++ extraFields
            SRecord(allFields)
          case None =>
            // Row variable not yet bound - keep as open record
            SOpenRecord(fields.view.mapValues(applySubstitution(_, subst)).toMap, rowVar)
        }
      case SRecord(fields) =>
        SRecord(fields.view.mapValues(applySubstitution(_, subst)).toMap)
      case SList(elem) =>
        SList(applySubstitution(elem, subst))
      case SOptional(inner) =>
        SOptional(applySubstitution(inner, subst))
      case SCandidates(elem) =>
        SCandidates(applySubstitution(elem, subst))
      case SMap(k, v) =>
        SMap(applySubstitution(k, subst), applySubstitution(v, subst))
      case SFunction(params, ret) =>
        SFunction(params.map(applySubstitution(_, subst)), applySubstitution(ret, subst))
      case SUnion(members) =>
        SUnion(members.map(applySubstitution(_, subst)))
      case other => other
    }
  }
}
