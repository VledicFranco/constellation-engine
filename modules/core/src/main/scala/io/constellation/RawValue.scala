package io.constellation

/**
 * Unboxed value representation without embedded type metadata.
 *
 * RawValue separates type information from values, storing types once per data node
 * rather than embedded in every value. This significantly reduces memory overhead
 * for large collections, especially in ML workloads.
 *
 * == Memory Savings ==
 *
 * For a list of 10,000 floats:
 * - CListValue[CFloatValue]: ~240KB (6x overhead due to object wrappers)
 * - RFloatList(Array[Double]): ~80KB (unboxed primitives)
 *
 * == Usage ==
 *
 * Type information is stored separately in DataNodeSpec.cType. Use TypedValueAccessor
 * for type-aware operations when needed.
 *
 * @see [[TypedValueAccessor]] for type-aware operations
 * @see [[RawValueConverter]] for CValue ↔ RawValue conversion
 */
sealed trait RawValue {

  /**
   * Debug string representation that includes value info.
   * For type information, use the CType from the corresponding DataNodeSpec.
   */
  def toDebugString: String
}

object RawValue {

  // === Primitive Values ===

  final case class RString(value: String) extends RawValue {
    override def toDebugString: String = s"RString($value)"
  }

  final case class RInt(value: Long) extends RawValue {
    override def toDebugString: String = s"RInt($value)"
  }

  final case class RFloat(value: Double) extends RawValue {
    override def toDebugString: String = s"RFloat($value)"
  }

  final case class RBool(value: Boolean) extends RawValue {
    override def toDebugString: String = s"RBool($value)"
  }

  // === Optional Values ===

  final case class RSome(value: RawValue) extends RawValue {
    override def toDebugString: String = s"RSome(${value.toDebugString})"
  }

  case object RNone extends RawValue {
    override def toDebugString: String = "RNone"
  }

  // === Specialized Primitive Lists (Major Memory Savings) ===

  /**
   * Specialized list of unboxed integers.
   * Memory: ~8 bytes per element (vs ~24 bytes with CIntValue wrapper)
   */
  final case class RIntList(values: Array[Long]) extends RawValue {
    override def toDebugString: String = s"RIntList(${values.length} elements)"

    def length: Int = values.length
    def apply(i: Int): Long = values(i)

    override def equals(other: Any): Boolean = other match {
      case that: RIntList => java.util.Arrays.equals(this.values, that.values)
      case _ => false
    }

    override def hashCode(): Int = java.util.Arrays.hashCode(values)
  }

  /**
   * Specialized list of unboxed doubles.
   * Memory: ~8 bytes per element (vs ~24 bytes with CFloatValue wrapper)
   */
  final case class RFloatList(values: Array[Double]) extends RawValue {
    override def toDebugString: String = s"RFloatList(${values.length} elements)"

    def length: Int = values.length
    def apply(i: Int): Double = values(i)

    override def equals(other: Any): Boolean = other match {
      case that: RFloatList => java.util.Arrays.equals(this.values, that.values)
      case _ => false
    }

    override def hashCode(): Int = java.util.Arrays.hashCode(values)
  }

  /**
   * Specialized list of strings.
   */
  final case class RStringList(values: Array[String]) extends RawValue {
    override def toDebugString: String = s"RStringList(${values.length} elements)"

    def length: Int = values.length
    def apply(i: Int): String = values(i)

    override def equals(other: Any): Boolean = other match {
      case that: RStringList => java.util.Arrays.equals(this.values.asInstanceOf[Array[AnyRef]], that.values.asInstanceOf[Array[AnyRef]])
      case _ => false
    }

    override def hashCode(): Int = java.util.Arrays.hashCode(values.asInstanceOf[Array[AnyRef]])
  }

  /**
   * Specialized list of booleans.
   */
  final case class RBoolList(values: Array[Boolean]) extends RawValue {
    override def toDebugString: String = s"RBoolList(${values.length} elements)"

    def length: Int = values.length
    def apply(i: Int): Boolean = values(i)

    override def equals(other: Any): Boolean = other match {
      case that: RBoolList => java.util.Arrays.equals(this.values, that.values)
      case _ => false
    }

    override def hashCode(): Int = java.util.Arrays.hashCode(values)
  }

  // === Generic Collections ===

  /**
   * Generic list for nested types (records, other lists, etc.)
   */
  final case class RList(values: Array[RawValue]) extends RawValue {
    override def toDebugString: String = s"RList(${values.length} elements)"

    def length: Int = values.length
    def apply(i: Int): RawValue = values(i)

    override def equals(other: Any): Boolean = other match {
      case that: RList => java.util.Arrays.equals(this.values.asInstanceOf[Array[AnyRef]], that.values.asInstanceOf[Array[AnyRef]])
      case _ => false
    }

    override def hashCode(): Int = java.util.Arrays.hashCode(values.asInstanceOf[Array[AnyRef]])
  }

  /**
   * Map from RawValue keys to RawValue values.
   * Uses Array of tuples for better cache locality than HashMap.
   */
  final case class RMap(entries: Array[(RawValue, RawValue)]) extends RawValue {
    override def toDebugString: String = s"RMap(${entries.length} entries)"

    def get(key: RawValue): Option[RawValue] = {
      var i = 0
      while (i < entries.length) {
        if (entries(i)._1 == key) return Some(entries(i)._2)
        i += 1
      }
      None
    }

    override def equals(other: Any): Boolean = other match {
      case that: RMap =>
        entries.length == that.entries.length &&
        entries.zip(that.entries).forall { case ((k1, v1), (k2, v2)) => k1 == k2 && v1 == v2 }
      case _ => false
    }

    override def hashCode(): Int = entries.map { case (k, v) => k.hashCode() ^ v.hashCode() }.sum
  }

  // === Record/Product ===

  /**
   * Record with fields stored by index (field order from CType.CProduct).
   * Field names are not stored - they come from the associated CType.
   */
  final case class RProduct(values: Array[RawValue]) extends RawValue {
    override def toDebugString: String = s"RProduct(${values.length} fields)"

    def apply(i: Int): RawValue = values(i)

    override def equals(other: Any): Boolean = other match {
      case that: RProduct => java.util.Arrays.equals(this.values.asInstanceOf[Array[AnyRef]], that.values.asInstanceOf[Array[AnyRef]])
      case _ => false
    }

    override def hashCode(): Int = java.util.Arrays.hashCode(values.asInstanceOf[Array[AnyRef]])
  }

  // === Union ===

  /**
   * Tagged union value. The tag identifies which variant is active.
   */
  final case class RUnion(tag: String, value: RawValue) extends RawValue {
    override def toDebugString: String = s"RUnion($tag, ${value.toDebugString})"
  }
}
