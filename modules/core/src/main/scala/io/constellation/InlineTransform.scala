package io.constellation

/** Inline transforms are lightweight operations that execute directly on data nodes without the
  * overhead of a full module (no UUID generation, no deferred allocation, no module scheduling).
  *
  * These are used for simple synthetic operations like record merging, field projection, field
  * access, and conditionals.
  *
  * ==Type Safety Note==
  *
  * The `asInstanceOf` casts in this file are **safe by construction**:
  *
  *   - All inline transforms receive inputs from the compiled DAG
  *   - The DagCompiler type-checks all expressions before generating transforms
  *   - Boolean operations (And, Or, Not, Conditional, Guard) only receive Boolean inputs because
  *     the type checker verifies operand types during compilation
  *   - List operations (Filter, Map, All, Any) only receive List inputs because the type checker
  *     verifies the source expression has a List type
  *
  * Runtime type validation can be enabled by setting `CONSTELLATION_DEBUG=true` for development and
  * debugging purposes. See [[io.constellation.DebugMode]].
  *
  * @see
  *   docs/dev/optimizations/04-inline-synthetic-modules.md
  * @see
  *   [[io.constellation.DebugMode]] for optional runtime type validation
  */
sealed trait InlineTransform {

  /** Apply the transform to the input values.
    *
    * @param inputs
    *   Map of input name to value. The keys depend on the transform type.
    * @return
    *   The transformed value
    */
  def apply(inputs: Map[String, Any]): Any
}

object InlineTransform {

  /** Merge transform - combines two record values into one. Handles Record + Record merge,
    * Candidates + Candidates element-wise merge, and Candidates + Record broadcast merge.
    *
    * @param leftType
    *   The CType of the left input (for determining merge strategy)
    * @param rightType
    *   The CType of the right input (for determining merge strategy)
    */
  final case class MergeTransform(
      leftType: CType,
      rightType: CType
  ) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val left  = inputs("left")
      val right = inputs("right")
      mergeValues(left, right, leftType, rightType)
    }

    private def mergeValues(left: Any, right: Any, leftCType: CType, rightCType: CType): Any =
      (left, right, leftCType, rightCType) match {
        // Record + Record merge
        case (lMap: Map[String, ?] @unchecked, rMap: Map[String, ?] @unchecked, _, _) =>
          lMap ++ rMap

        // Candidates + Candidates element-wise merge
        case (
              lList: List[?] @unchecked,
              rList: List[?] @unchecked,
              CType.CList(lElem),
              CType.CList(rElem)
            ) =>
          if lList.size != rList.size then {
            throw new IllegalArgumentException(
              s"Cannot merge Candidates with different lengths: left has ${lList.size} elements, right has ${rList.size} elements"
            )
          }
          lList.zip(rList).map { case (l, r) => mergeValues(l, r, lElem, rElem) }

        // Candidates + Record broadcast (left is list)
        case (lList: List[?] @unchecked, rMap: Map[String, ?] @unchecked, CType.CList(_), _) =>
          lList.map {
            case elemMap: Map[String, ?] @unchecked => elemMap ++ rMap
            case other                              => other
          }

        // Record + Candidates broadcast (right is list)
        case (lMap: Map[String, ?] @unchecked, rList: List[?] @unchecked, _, CType.CList(_)) =>
          rList.map {
            case elemMap: Map[String, ?] @unchecked => lMap ++ elemMap
            case other                              => other
          }

        // Fallback: right wins
        case _ => right
      }
  }

  /** Project transform - extracts specified fields from a record. Also handles projecting from
    * Candidates (list of records) by mapping over elements.
    *
    * @param fields
    *   The field names to extract
    * @param sourceType
    *   The CType of the source (for determining if it's a list)
    */
  final case class ProjectTransform(
      fields: List[String],
      sourceType: CType
  ) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val source = inputs("source")
      projectFields(source, sourceType)
    }

    private def projectFields(value: Any, cType: CType): Any =
      (value, cType) match {
        case (map: Map[String, ?] @unchecked, CType.CProduct(_)) =>
          fields.flatMap(f => map.get(f).map(f -> _)).toMap

        case (list: List[?] @unchecked, CType.CList(elemType)) =>
          list.map(elem => projectFields(elem, elemType))

        case _ => value
      }
  }

  /** Field access transform - extracts a single field value from a record. Also handles accessing
    * fields from Candidates (list of records) by mapping over elements.
    *
    * @param field
    *   The field name to extract
    * @param sourceType
    *   The CType of the source (for determining if it's a list)
    */
  final case class FieldAccessTransform(
      field: String,
      sourceType: CType
  ) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val source = inputs("source")
      accessField(source, sourceType)
    }

    private def accessField(value: Any, cType: CType): Any =
      (value, cType) match {
        case (map: Map[String, ?] @unchecked, CType.CProduct(_)) =>
          map.getOrElse(field, throw new IllegalStateException(s"Field '$field' not found"))

        case (list: List[?] @unchecked, CType.CList(elemType)) =>
          list.map(elem => accessField(elem, elemType))

        case _ =>
          throw new IllegalStateException(s"Cannot access field '$field' on non-record type")
      }
  }

  /** Conditional transform - selects between two values based on a boolean condition.
    */
  case object ConditionalTransform extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val condition = inputs("cond").asInstanceOf[Boolean]
      if condition then inputs("thenBr") else inputs("elseBr")
    }
  }

  /** Guard transform - returns Optional based on condition. Returns Some(expr) if condition is
    * true, None otherwise.
    */
  case object GuardTransform extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val condition = inputs("cond").asInstanceOf[Boolean]
      if condition then Some(inputs("expr")) else None
    }
  }

  /** Coalesce transform - unwraps Optional with fallback. Returns the inner value if Some,
    * otherwise returns the fallback.
    */
  case object CoalesceTransform extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any =
      inputs("left") match {
        case Some(v) => v
        case None    => inputs("right")
      }
  }

  /** Boolean AND transform with short-circuit semantics.
    */
  case object AndTransform extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val left = inputs("left").asInstanceOf[Boolean]
      if !left then false else inputs("right").asInstanceOf[Boolean]
    }
  }

  /** Boolean OR transform with short-circuit semantics.
    */
  case object OrTransform extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val left = inputs("left").asInstanceOf[Boolean]
      if left then true else inputs("right").asInstanceOf[Boolean]
    }
  }

  /** Boolean NOT transform.
    */
  case object NotTransform extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any =
      !inputs("operand").asInstanceOf[Boolean]
  }

  /** Literal transform - produces a constant value. Used for literal values that need to be fed
    * into data nodes.
    *
    * @param value
    *   The constant value to produce
    */
  final case class LiteralTransform(value: Any) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = value
  }

  /** String interpolation transform - combines static parts with evaluated expressions. Converts
    * expression values to strings and interleaves them with the static parts.
    *
    * @param parts
    *   The static string parts (parts.length == numExpressions + 1)
    */
  final case class StringInterpolationTransform(parts: List[String]) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val numExprs = parts.length - 1
      val sb       = new StringBuilder

      for i <- 0 until numExprs do {
        sb.append(parts(i))
        val exprValue = inputs(s"expr$i")
        sb.append(stringify(exprValue))
      }
      sb.append(parts.last)

      sb.toString()
    }

    private def stringify(value: Any): String = value match {
      case s: String     => s
      case n: Number     => n.toString
      case b: Boolean    => b.toString
      case None          => ""
      case Some(v)       => stringify(v)
      case list: List[?] => list.map(stringify).mkString("[", ", ", "]")
      case map: Map[?, ?] @unchecked =>
        map.map { case (k, v) => s"$k: ${stringify(v)}" }.mkString("{", ", ", "}")
      case other => other.toString
    }
  }

  /** Filter transform - filters a list based on a predicate. The predicate function is evaluated
    * for each element.
    *
    * @param predicateEvaluator
    *   A function that takes an element and returns a Boolean
    */
  final case class FilterTransform(
      predicateEvaluator: Any => Boolean
  ) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val source = inputs("source").asInstanceOf[List[?]]
      source.filter(predicateEvaluator)
    }
  }

  /** Map transform - transforms each element of a list. The transform function is evaluated for
    * each element.
    *
    * @param transformEvaluator
    *   A function that takes an element and returns the transformed value
    */
  final case class MapTransform(
      transformEvaluator: Any => Any
  ) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val source = inputs("source").asInstanceOf[List[?]]
      source.map(transformEvaluator)
    }
  }

  /** All transform - checks if all elements satisfy a predicate.
    *
    * @param predicateEvaluator
    *   A function that takes an element and returns a Boolean
    */
  final case class AllTransform(
      predicateEvaluator: Any => Boolean
  ) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val source = inputs("source").asInstanceOf[List[?]]
      source.forall(predicateEvaluator)
    }
  }

  /** Any transform - checks if any element satisfies a predicate.
    *
    * @param predicateEvaluator
    *   A function that takes an element and returns a Boolean
    */
  final case class AnyTransform(
      predicateEvaluator: Any => Boolean
  ) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val source = inputs("source").asInstanceOf[List[?]]
      source.exists(predicateEvaluator)
    }
  }

  /** List literal transform - assembles multiple values into a list. Input names are "elem0",
    * "elem1", etc.
    */
  final case class ListLiteralTransform(numElements: Int) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any =
      (0 until numElements).map(i => inputs(s"elem$i")).toList
  }
}
