package io.constellation

/**
 * Type-aware accessor for RawValue operations.
 *
 * TypedValueAccessor bridges RawValue (untyped, memory-efficient) and CValue (typed, self-describing)
 * representations. It uses the associated CType to interpret RawValue data.
 *
 * == Usage ==
 * {{{
 * val accessor = TypedValueAccessor(CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt)))
 * val raw = RawValue.RProduct(Array(RawValue.RString("Alice"), RawValue.RInt(30)))
 * val nameValue = accessor.getField(raw, "name") // RString("Alice")
 * val cValue = accessor.toCValue(raw) // CProduct(Map("name" -> CString("Alice"), "age" -> CInt(30)))
 * }}}
 */
final class TypedValueAccessor(val cType: CType) {

  /**
   * Get a field from a product value by name.
   * Requires cType to be CProduct.
   */
  def getField(raw: RawValue, fieldName: String): RawValue = cType match {
    case CType.CProduct(structure) =>
      val fieldIndex = structure.keys.toList.sorted.indexOf(fieldName)
      if (fieldIndex < 0) {
        throw new TypeMismatchException(s"Field '$fieldName' not found in product type")
      }
      raw match {
        case RawValue.RProduct(values) =>
          if (fieldIndex >= values.length) {
            throw new TypeMismatchException(s"Field index $fieldIndex out of bounds (${values.length} fields)")
          }
          values(fieldIndex)
        case other =>
          throw new TypeMismatchException(s"Expected RProduct, got ${other.getClass.getSimpleName}")
      }
    case other =>
      throw new TypeMismatchException(s"Cannot get field from $other")
  }

  /**
   * Get the CType for a field in a product type.
   */
  def getFieldType(fieldName: String): CType = cType match {
    case CType.CProduct(structure) =>
      structure.getOrElse(fieldName,
        throw new TypeMismatchException(s"Field '$fieldName' not found in product type"))
    case other =>
      throw new TypeMismatchException(s"Cannot get field type from $other")
  }

  /**
   * Get an accessor for a field in a product type.
   */
  def fieldAccessor(fieldName: String): TypedValueAccessor =
    new TypedValueAccessor(getFieldType(fieldName))

  /**
   * Get an accessor for list elements.
   */
  def elementAccessor: TypedValueAccessor = cType match {
    case CType.CList(elementType) => new TypedValueAccessor(elementType)
    case other => throw new TypeMismatchException(s"Cannot get element type from $other")
  }

  /**
   * Get an accessor for optional inner type.
   */
  def innerAccessor: TypedValueAccessor = cType match {
    case CType.COptional(innerType) => new TypedValueAccessor(innerType)
    case other => throw new TypeMismatchException(s"Cannot get inner type from $other")
  }

  /**
   * Extract a Long from a RawValue.
   */
  def getInt(raw: RawValue): Long = raw match {
    case RawValue.RInt(v) => v
    case other => throw new TypeMismatchException(s"Expected RInt, got ${other.getClass.getSimpleName}")
  }

  /**
   * Extract a Double from a RawValue.
   */
  def getFloat(raw: RawValue): Double = raw match {
    case RawValue.RFloat(v) => v
    case other => throw new TypeMismatchException(s"Expected RFloat, got ${other.getClass.getSimpleName}")
  }

  /**
   * Extract a String from a RawValue.
   */
  def getString(raw: RawValue): String = raw match {
    case RawValue.RString(v) => v
    case other => throw new TypeMismatchException(s"Expected RString, got ${other.getClass.getSimpleName}")
  }

  /**
   * Extract a Boolean from a RawValue.
   */
  def getBool(raw: RawValue): Boolean = raw match {
    case RawValue.RBool(v) => v
    case other => throw new TypeMismatchException(s"Expected RBool, got ${other.getClass.getSimpleName}")
  }

  /**
   * Convert a RawValue back to CValue when needed (e.g., for JSON output).
   */
  def toCValue(raw: RawValue): CValue = (raw, cType) match {
    // Primitives
    case (RawValue.RString(v), CType.CString) => CValue.CString(v)
    case (RawValue.RInt(v), CType.CInt) => CValue.CInt(v)
    case (RawValue.RFloat(v), CType.CFloat) => CValue.CFloat(v)
    case (RawValue.RBool(v), CType.CBoolean) => CValue.CBoolean(v)

    // Optional
    case (RawValue.RSome(v), CType.COptional(innerType)) =>
      CValue.CSome(new TypedValueAccessor(innerType).toCValue(v), innerType)
    case (RawValue.RNone, CType.COptional(innerType)) =>
      CValue.CNone(innerType)

    // Specialized primitive lists
    case (RawValue.RIntList(values), CType.CList(CType.CInt)) =>
      CValue.CList(values.map(CValue.CInt(_)).toVector, CType.CInt)
    case (RawValue.RFloatList(values), CType.CList(CType.CFloat)) =>
      CValue.CList(values.map(CValue.CFloat(_)).toVector, CType.CFloat)
    case (RawValue.RStringList(values), CType.CList(CType.CString)) =>
      CValue.CList(values.map(CValue.CString(_)).toVector, CType.CString)
    case (RawValue.RBoolList(values), CType.CList(CType.CBoolean)) =>
      CValue.CList(values.map(CValue.CBoolean(_)).toVector, CType.CBoolean)

    // Generic list
    case (RawValue.RList(values), CType.CList(elementType)) =>
      val elementAccessor = new TypedValueAccessor(elementType)
      CValue.CList(values.map(elementAccessor.toCValue).toVector, elementType)

    // Map
    case (RawValue.RMap(entries), CType.CMap(keyType, valueType)) =>
      val keyAccessor = new TypedValueAccessor(keyType)
      val valueAccessor = new TypedValueAccessor(valueType)
      CValue.CMap(
        entries.map { case (k, v) => (keyAccessor.toCValue(k), valueAccessor.toCValue(v)) }.toVector,
        keyType,
        valueType
      )

    // Product
    case (RawValue.RProduct(values), CType.CProduct(structure)) =>
      val sortedFields = structure.toList.sortBy(_._1)
      val convertedFields = sortedFields.zip(values).map { case ((name, fieldType), value) =>
        name -> new TypedValueAccessor(fieldType).toCValue(value)
      }.toMap
      CValue.CProduct(convertedFields, structure)

    // Union
    case (RawValue.RUnion(tag, value), CType.CUnion(structure)) =>
      val variantType = structure.getOrElse(tag,
        throw new TypeMismatchException(s"Unknown union variant '$tag'"))
      CValue.CUnion(new TypedValueAccessor(variantType).toCValue(value), structure, tag)

    case (raw, expected) =>
      throw new TypeMismatchException(s"Cannot convert ${raw.getClass.getSimpleName} to $expected")
  }
}

object TypedValueAccessor {

  def apply(cType: CType): TypedValueAccessor = new TypedValueAccessor(cType)
}

/**
 * Exception thrown when a type mismatch occurs during RawValue operations.
 */
class TypeMismatchException(message: String) extends RuntimeException(message)
