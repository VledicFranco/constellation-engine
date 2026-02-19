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

        // Candidates + Candidates element-wise merge (CList)
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

        // Seq + Seq element-wise merge
        case (
              lList: List[?] @unchecked,
              rList: List[?] @unchecked,
              CType.CSeq(lElem),
              CType.CSeq(rElem)
            ) =>
          if lList.size != rList.size then {
            throw new IllegalArgumentException(
              s"Cannot merge Seq with different lengths: left has ${lList.size} elements, right has ${rList.size} elements"
            )
          }
          lList.zip(rList).map { case (l, r) => mergeValues(l, r, lElem, rElem) }

        // Candidates + Record broadcast (left is CList)
        case (lList: List[?] @unchecked, rMap: Map[String, ?] @unchecked, CType.CList(_), _) =>
          lList.map {
            case elemMap: Map[String, ?] @unchecked => elemMap ++ rMap
            case other                              => other
          }

        // Seq + Record broadcast (left is CSeq)
        case (lList: List[?] @unchecked, rMap: Map[String, ?] @unchecked, CType.CSeq(_), _) =>
          lList.map {
            case elemMap: Map[String, ?] @unchecked => elemMap ++ rMap
            case other                              => other
          }

        // Record + Candidates broadcast (right is CList)
        case (lMap: Map[String, ?] @unchecked, rList: List[?] @unchecked, _, CType.CList(_)) =>
          rList.map {
            case elemMap: Map[String, ?] @unchecked => lMap ++ elemMap
            case other                              => other
          }

        // Record + Seq broadcast (right is CSeq)
        case (lMap: Map[String, ?] @unchecked, rList: List[?] @unchecked, _, CType.CSeq(_)) =>
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

        case (list: List[?] @unchecked, CType.CSeq(elemType)) =>
          list.map(elem => projectFields(elem, elemType))

        case _ => value
      }
  }

  /** Sentinel value indicating a field access failed because the field doesn't exist in the current
    * union variant. Used for lazy match expression evaluation.
    */
  case object MatchBindingMissing

  /** Field access transform - extracts a single field value from a record. Also handles accessing
    * fields from Candidates (list of records) by mapping over elements. Supports union types by
    * unwrapping the (tag, value) tuple.
    *
    * For match expressions on union types, returns MatchBindingMissing if the field doesn't exist
    * in the current variant (rather than throwing an error).
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
          map.getOrElse(field, MatchBindingMissing)

        case (list: List[?] @unchecked, CType.CList(elemType)) =>
          list.map(elem => accessField(elem, elemType))

        case (list: List[?] @unchecked, CType.CSeq(elemType)) =>
          list.map(elem => accessField(elem, elemType))

        // Handle union types: unwrap (tag, innerValue) and access field from inner value
        case ((tag: String, inner), CType.CUnion(variants)) =>
          variants.get(tag) match {
            case Some(innerType) => accessField(inner, innerType)
            case None            => MatchBindingMissing
          }

        case _ =>
          MatchBindingMissing
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

  // ===== Closure-aware HOF transforms =====
  // These variants receive captured values from the enclosing scope alongside each element.
  // The evaluator takes (element, capturedValues) instead of just element.

  /** Closure filter transform - filters using a predicate that captures outer-scope variables.
    *
    * @param predicateEvaluator
    *   Takes (element, capturedValues) and returns Boolean
    * @param capturedKeys
    *   Names of captured variables to extract from inputs
    */
  final case class ClosureFilterTransform(
      predicateEvaluator: (Any, Map[String, Any]) => Boolean,
      capturedKeys: List[String]
  ) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val source   = inputs("source").asInstanceOf[List[?]]
      val captured = capturedKeys.map(k => k -> inputs(k)).toMap
      source.filter(elem => predicateEvaluator(elem, captured))
    }
  }

  /** Closure map transform - transforms elements using a function that captures outer-scope
    * variables.
    *
    * @param mapEvaluator
    *   Takes (element, capturedValues) and returns transformed value
    * @param capturedKeys
    *   Names of captured variables to extract from inputs
    */
  final case class ClosureMapTransform(
      mapEvaluator: (Any, Map[String, Any]) => Any,
      capturedKeys: List[String]
  ) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val source   = inputs("source").asInstanceOf[List[?]]
      val captured = capturedKeys.map(k => k -> inputs(k)).toMap
      source.map(elem => mapEvaluator(elem, captured))
    }
  }

  /** Closure all transform - checks if all elements satisfy a predicate that captures outer-scope
    * variables.
    */
  final case class ClosureAllTransform(
      predicateEvaluator: (Any, Map[String, Any]) => Boolean,
      capturedKeys: List[String]
  ) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val source   = inputs("source").asInstanceOf[List[?]]
      val captured = capturedKeys.map(k => k -> inputs(k)).toMap
      source.forall(elem => predicateEvaluator(elem, captured))
    }
  }

  /** Closure any transform - checks if any element satisfies a predicate that captures outer-scope
    * variables.
    */
  final case class ClosureAnyTransform(
      predicateEvaluator: (Any, Map[String, Any]) => Boolean,
      capturedKeys: List[String]
  ) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val source   = inputs("source").asInstanceOf[List[?]]
      val captured = capturedKeys.map(k => k -> inputs(k)).toMap
      source.exists(elem => predicateEvaluator(elem, captured))
    }
  }

  /** List literal transform - assembles multiple values into a list. Input names are "elem0",
    * "elem1", etc.
    */
  final case class ListLiteralTransform(numElements: Int) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any =
      (0 until numElements).map(i => inputs(s"elem$i")).toList
  }

  /** Match transform - evaluates patterns in order and returns the matched case's body. Handles
    * union types by unwrapping the (tag, value) tuple before matching.
    *
    * @param patternMatchers
    *   Functions that check if a value matches each pattern
    * @param bodyEvaluators
    *   Functions that compute the body value given the scrutinee
    * @param scrutineeCType
    *   The CType of the scrutinee (for union unwrapping)
    */
  final case class MatchTransform(
      patternMatchers: List[Any => Boolean],
      bodyEvaluators: List[Any => Any],
      scrutineeCType: CType
  ) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val scrutinee = inputs("scrutinee")

      // Unwrap union value for matching
      val (unwrapped, tag) = scrutinee match {
        case (t: String, inner) => (inner, Some(t))
        case other              => (other, None)
      }

      // Find first matching pattern
      val matchingIdx = patternMatchers.indices.find { idx =>
        patternMatchers(idx)(unwrapped)
      }

      matchingIdx match {
        case Some(idx) =>
          // Evaluate the matched body with the scrutinee
          bodyEvaluators(idx)(scrutinee)
        case None =>
          throw new MatchError(s"No pattern matched value: $scrutinee")
      }
    }
  }

  /** Record literal transform - assembles named field values into a record (Map).
    *
    * @param fieldNames
    *   The ordered list of field names in the record
    */
  final case class RecordBuildTransform(fieldNames: List[String]) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any =
      fieldNames.map(name => name -> inputs(name)).toMap
  }

  /** Collect transform - materializes a streaming Seq into a List (single-mode behavior). In
    * streaming mode, this becomes a chunking boundary.
    *
    * @param windowSpec
    *   Optional windowing specification (reserved for Phase 3)
    */
  final case class CollectTransform(windowSpec: Option[Any] = None) extends InlineTransform {
    override def apply(inputs: Map[String, Any]): Any = {
      val source = inputs("source")
      source match {
        case list: List[?] => list
        case other         => List(other)
      }
    }
  }
}
