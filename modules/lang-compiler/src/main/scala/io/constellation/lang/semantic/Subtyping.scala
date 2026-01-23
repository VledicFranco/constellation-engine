package io.constellation.lang.semantic

/** Subtyping lattice for the Constellation type system.
  *
  * Implements structural subtyping with:
  * - SNothing as bottom type (subtype of all types)
  * - Covariant collections (List, Candidates, Optional)
  * - Width + depth subtyping for records
  * - Union type handling (upper and lower bounds)
  * - Contravariant function parameters
  */
object Subtyping {

  /** Check if `sub` is a subtype of `sup`.
    *
    * Subtyping rules:
    * {{{
    * S <: S                           (Reflexivity)
    * S <: T ∧ T <: U ⟹ S <: U        (Transitivity - implicit via rules)
    * SNothing <: T                    (Bottom)
    *
    * SList(S) <: SList(T) ⟸ S <: T   (Covariance)
    * SOptional(S) <: SOptional(T) ⟸ S <: T
    * SCandidates(S) <: SCandidates(T) ⟸ S <: T
    * SMap(K, S) <: SMap(K, T) ⟸ S <: T (values covariant, keys invariant)
    *
    * SRecord(F₁) <: SRecord(F₂) ⟸ ∀f∈F₂. f∈F₁ ∧ F₁(f) <: F₂(f)  (Width + Depth)
    *
    * S <: T₁ | T₂ ⟸ S <: T₁ ∨ S <: T₂   (Union upper bound)
    * T₁ | T₂ <: S ⟸ T₁ <: S ∧ T₂ <: S   (Union lower bound)
    *
    * SFunction(P₁, R₁) <: SFunction(P₂, R₂) ⟸
    *   |P₁| = |P₂| ∧ (∀i. P₂ᵢ <: P₁ᵢ) ∧ R₁ <: R₂  (Contravariant params, covariant return)
    * }}}
    */
  def isSubtype(sub: SemanticType, sup: SemanticType): Boolean = {
    // Reflexivity: every type is a subtype of itself
    if (sub == sup) return true

    (sub, sup) match {
      // Bottom type is subtype of everything
      case (SemanticType.SNothing, _) => true

      // Collections are covariant in their element type
      case (SemanticType.SList(subElem), SemanticType.SList(supElem)) =>
        isSubtype(subElem, supElem)

      case (SemanticType.SCandidates(subElem), SemanticType.SCandidates(supElem)) =>
        isSubtype(subElem, supElem)

      case (SemanticType.SOptional(subInner), SemanticType.SOptional(supInner)) =>
        isSubtype(subInner, supInner)

      // Maps: keys invariant, values covariant
      case (SemanticType.SMap(subK, subV), SemanticType.SMap(supK, supV)) =>
        subK == supK && isSubtype(subV, supV)

      // Records: width + depth subtyping
      // sub has all fields required by sup, and each field is a subtype
      case (SemanticType.SRecord(subFields), SemanticType.SRecord(supFields)) =>
        supFields.forall { case (name, supType) =>
          subFields.get(name).exists(subType => isSubtype(subType, supType))
        }

      // Union as supertype: sub is subtype if it's subtype of any member
      case (_, SemanticType.SUnion(supMembers)) =>
        supMembers.exists(m => isSubtype(sub, m))

      // Union as subtype: all members must be subtypes of sup
      case (SemanticType.SUnion(subMembers), _) =>
        subMembers.forall(m => isSubtype(m, sup))

      // Functions: contravariant in parameters, covariant in return type
      case (SemanticType.SFunction(subParams, subRet), SemanticType.SFunction(supParams, supRet)) =>
        subParams.length == supParams.length &&
        subParams.zip(supParams).forall { case (subT, supT) =>
          isSubtype(supT, subT) // Contravariant!
        } &&
        isSubtype(subRet, supRet)

      // No subtyping relationship
      case _ => false
    }
  }

  /** Check if a value of type `actual` can be assigned to a variable of type `expected`.
    *
    * This is equivalent to checking if `actual` is a subtype of `expected`.
    */
  def isAssignable(actual: SemanticType, expected: SemanticType): Boolean =
    isSubtype(actual, expected)

  /** Compute the least upper bound (LUB) of two types.
    *
    * The LUB is the most specific type that is a supertype of both.
    * Used for finding common type in conditional branches.
    *
    * @return The LUB, or a union type if no simpler common supertype exists
    */
  def lub(a: SemanticType, b: SemanticType): SemanticType = {
    if (isSubtype(a, b)) b
    else if (isSubtype(b, a)) a
    else {
      // No direct subtype relationship - create a union
      // Flatten any existing unions
      val aMembers = a match {
        case SemanticType.SUnion(members) => members
        case other                        => Set(other)
      }
      val bMembers = b match {
        case SemanticType.SUnion(members) => members
        case other                        => Set(other)
      }
      val combined = aMembers ++ bMembers
      if (combined.size == 1) combined.head
      else SemanticType.SUnion(combined)
    }
  }

  /** Compute the greatest lower bound (GLB) of two types.
    *
    * The GLB is the most general type that is a subtype of both.
    *
    * @return The GLB, or SNothing if no common subtype exists
    */
  def glb(a: SemanticType, b: SemanticType): SemanticType = {
    if (isSubtype(a, b)) a
    else if (isSubtype(b, a)) b
    else SemanticType.SNothing // No common subtype
  }

  /** Compute the common type for a list of types.
    *
    * This is the LUB of all types in the list.
    * Used for inferring element types in list literals and branch result types.
    *
    * @param types Non-empty list of types
    * @return The common supertype
    */
  def commonType(types: List[SemanticType]): SemanticType = {
    require(types.nonEmpty, "commonType requires at least one type")
    types.reduceLeft(lub)
  }

  /** Explain why a subtype relationship does not hold.
    *
    * Returns a human-readable explanation for type error messages.
    */
  def explainFailure(sub: SemanticType, sup: SemanticType): Option[String] = {
    if (isSubtype(sub, sup)) None
    else {
      (sub, sup) match {
        case (SemanticType.SRecord(subFields), SemanticType.SRecord(supFields)) =>
          // Find missing fields
          val missing = supFields.keySet.diff(subFields.keySet)
          if (missing.nonEmpty) {
            Some(s"Record is missing required field(s): ${missing.mkString(", ")}")
          } else {
            // Find incompatible fields
            val incompatible = supFields.collect {
              case (name, supType) if subFields.get(name).exists(subType => !isSubtype(subType, supType)) =>
                val subType = subFields(name)
                s"'$name' has type ${subType.prettyPrint} but expected ${supType.prettyPrint}"
            }
            if (incompatible.nonEmpty) {
              Some(s"Field type mismatch: ${incompatible.mkString("; ")}")
            } else {
              None
            }
          }

        case (SemanticType.SList(subElem), SemanticType.SList(supElem)) =>
          explainFailure(subElem, supElem).map(reason => s"List element type mismatch: $reason")

        case (SemanticType.SOptional(subInner), SemanticType.SOptional(supInner)) =>
          explainFailure(subInner, supInner).map(reason => s"Optional inner type mismatch: $reason")

        case (_, SemanticType.SUnion(members)) =>
          Some(s"${sub.prettyPrint} is not a member of union type ${sup.prettyPrint}")

        case (SemanticType.SFunction(subParams, _), SemanticType.SFunction(supParams, _))
            if subParams.length != supParams.length =>
          Some(s"Function has ${subParams.length} parameters but expected ${supParams.length}")

        case _ =>
          Some(s"${sub.prettyPrint} is not a subtype of ${sup.prettyPrint}")
      }
    }
  }
}
