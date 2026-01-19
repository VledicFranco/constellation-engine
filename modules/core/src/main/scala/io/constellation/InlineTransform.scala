package io.constellation

/**
 * Inline transforms are lightweight operations that execute directly on data nodes
 * without the overhead of a full module (no UUID generation, no deferred allocation,
 * no module scheduling).
 *
 * These are used for simple synthetic operations like record merging, field projection,
 * field access, and conditionals.
 *
 * @see docs/dev/optimizations/04-inline-synthetic-modules.md
 */
sealed trait InlineTransform {
  /**
   * Apply the transform to the input values.
   *
   * @param inputs Map of input name to value. The keys depend on the transform type.
   * @return The transformed value
   */
  def apply(inputs: Map[String, Any]): Any
}

object InlineTransform {

  /**
   * Merge transform - combines two record values into one.
   * Handles Record + Record merge, Candidates + Candidates element-wise merge,
   * and Candidates + Record broadcast merge.
   *
   * @param leftType The CType of the left input (for determining merge strategy)
   * @param rightType The CType of the right input (for determining merge strategy)
   */
  final case class MergeTransform(
    leftType: CType,
    rightType: CType
  ) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val left = inputs("left")
      val right = inputs("right")
      mergeValues(left, right, leftType, rightType)
    }

    private def mergeValues(left: Any, right: Any, leftCType: CType, rightCType: CType): Any = {
      (left, right, leftCType, rightCType) match {
        // Record + Record merge
        case (lMap: Map[String, ?] @unchecked, rMap: Map[String, ?] @unchecked, _, _) =>
          lMap ++ rMap

        // Candidates + Candidates element-wise merge
        case (lList: List[?] @unchecked, rList: List[?] @unchecked, CType.CList(lElem), CType.CList(rElem)) =>
          if (lList.size != rList.size) {
            throw new IllegalArgumentException(
              s"Cannot merge Candidates with different lengths: left has ${lList.size} elements, right has ${rList.size} elements"
            )
          }
          lList.zip(rList).map { case (l, r) => mergeValues(l, r, lElem, rElem) }

        // Candidates + Record broadcast (left is list)
        case (lList: List[?] @unchecked, rMap: Map[String, ?] @unchecked, CType.CList(_), _) =>
          lList.map {
            case elemMap: Map[String, ?] @unchecked => elemMap ++ rMap
            case other => other
          }

        // Record + Candidates broadcast (right is list)
        case (lMap: Map[String, ?] @unchecked, rList: List[?] @unchecked, _, CType.CList(_)) =>
          rList.map {
            case elemMap: Map[String, ?] @unchecked => lMap ++ elemMap
            case other => other
          }

        // Fallback: right wins
        case _ => right
      }
    }
  }

  /**
   * Project transform - extracts specified fields from a record.
   * Also handles projecting from Candidates (list of records) by mapping over elements.
   *
   * @param fields The field names to extract
   * @param sourceType The CType of the source (for determining if it's a list)
   */
  final case class ProjectTransform(
    fields: List[String],
    sourceType: CType
  ) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val source = inputs("source")
      projectFields(source, sourceType)
    }

    private def projectFields(value: Any, cType: CType): Any = {
      (value, cType) match {
        case (map: Map[String, ?] @unchecked, CType.CProduct(_)) =>
          fields.flatMap(f => map.get(f).map(f -> _)).toMap

        case (list: List[?] @unchecked, CType.CList(elemType)) =>
          list.map(elem => projectFields(elem, elemType))

        case _ => value
      }
    }
  }

  /**
   * Field access transform - extracts a single field value from a record.
   * Also handles accessing fields from Candidates (list of records) by mapping over elements.
   *
   * @param field The field name to extract
   * @param sourceType The CType of the source (for determining if it's a list)
   */
  final case class FieldAccessTransform(
    field: String,
    sourceType: CType
  ) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val source = inputs("source")
      accessField(source, sourceType)
    }

    private def accessField(value: Any, cType: CType): Any = {
      (value, cType) match {
        case (map: Map[String, ?] @unchecked, CType.CProduct(_)) =>
          map.getOrElse(field, throw new IllegalStateException(s"Field '$field' not found"))

        case (list: List[?] @unchecked, CType.CList(elemType)) =>
          list.map(elem => accessField(elem, elemType))

        case _ =>
          throw new IllegalStateException(s"Cannot access field '$field' on non-record type")
      }
    }
  }

  /**
   * Conditional transform - selects between two values based on a boolean condition.
   */
  case object ConditionalTransform extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val condition = inputs("cond").asInstanceOf[Boolean]
      if (condition) inputs("thenBr") else inputs("elseBr")
    }
  }

  /**
   * Guard transform - returns Optional based on condition.
   * Returns Some(expr) if condition is true, None otherwise.
   */
  case object GuardTransform extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val condition = inputs("cond").asInstanceOf[Boolean]
      if (condition) Some(inputs("expr")) else None
    }
  }

  /**
   * Coalesce transform - unwraps Optional with fallback.
   * Returns the inner value if Some, otherwise returns the fallback.
   */
  case object CoalesceTransform extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      inputs("left") match {
        case Some(v) => v
        case None => inputs("right")
      }
    }
  }

  /**
   * Boolean AND transform with short-circuit semantics.
   */
  case object AndTransform extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val left = inputs("left").asInstanceOf[Boolean]
      if (!left) false else inputs("right").asInstanceOf[Boolean]
    }
  }

  /**
   * Boolean OR transform with short-circuit semantics.
   */
  case object OrTransform extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val left = inputs("left").asInstanceOf[Boolean]
      if (left) true else inputs("right").asInstanceOf[Boolean]
    }
  }

  /**
   * Boolean NOT transform.
   */
  case object NotTransform extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      !inputs("operand").asInstanceOf[Boolean]
    }
  }

  /**
   * Literal transform - produces a constant value.
   * Used for literal values that need to be fed into data nodes.
   *
   * @param value The constant value to produce
   */
  final case class LiteralTransform(value: Any) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = value
  }
}
