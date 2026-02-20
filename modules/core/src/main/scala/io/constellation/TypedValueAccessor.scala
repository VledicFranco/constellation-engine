package io.constellation

/** Type-aware accessor for RawValue operations.
  *
  * TypedValueAccessor bridges RawValue (untyped, memory-efficient) and CValue (typed,
  * self-describing) representations. It uses the associated CType to interpret RawValue data.
  *
  * ==Usage==
  * {{{
  * val accessor = TypedValueAccessor(CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt)))
  * val raw = RawValue.RProduct(Array(RawValue.RString("Alice"), RawValue.RInt(30)))
  * val nameValue = accessor.getField(raw, "name") // RString("Alice")
  * val cValue = accessor.toCValue(raw) // CProduct(Map("name" -> CString("Alice"), "age" -> CInt(30)))
  * }}}
  */
final class TypedValueAccessor(val cType: CType) {

  /** Get a field from a product value by name. Requires cType to be CProduct.
    */
  def getField(raw: RawValue, fieldName: String): RawValue = cType match {
    case CType.CProduct(structure) =>
      val fieldIndex = structure.keys.toList.sorted.indexOf(fieldName)
      if fieldIndex < 0 then {
        throw TypeMismatchError(
          expected = s"field '$fieldName' in product",
          actual = s"product without field '$fieldName'",
          context = Map("field" -> fieldName, "availableFields" -> structure.keys.mkString(", "))
        )
      }
      raw match {
        case RawValue.RProduct(values) =>
          if fieldIndex >= values.length then {
            throw TypeMismatchError(
              expected = s"product with at least ${fieldIndex + 1} fields",
              actual = s"product with ${values.length} fields",
              context =
                Map("fieldIndex" -> fieldIndex.toString, "fieldCount" -> values.length.toString)
            )
          }
          values(fieldIndex)
        case other =>
          throw TypeMismatchError(
            expected = "RProduct",
            actual = other.getClass.getSimpleName
          )
      }
    case other =>
      throw TypeMismatchError(
        expected = "CProduct type",
        actual = other.toString,
        context = Map("operation" -> "getField")
      )
  }

  /** Get the CType for a field in a product type.
    */
  def getFieldType(fieldName: String): CType = cType match {
    case CType.CProduct(structure) =>
      structure.getOrElse(
        fieldName,
        throw TypeMismatchError(
          expected = s"field '$fieldName' in product",
          actual = s"product without field '$fieldName'",
          context = Map("field" -> fieldName, "availableFields" -> structure.keys.mkString(", "))
        )
      )
    case other =>
      throw TypeMismatchError(
        expected = "CProduct type",
        actual = other.toString,
        context = Map("operation" -> "getFieldType")
      )
  }

  /** Get an accessor for a field in a product type.
    */
  def fieldAccessor(fieldName: String): TypedValueAccessor =
    new TypedValueAccessor(getFieldType(fieldName))

  /** Get an accessor for list elements.
    */
  def elementAccessor: TypedValueAccessor = cType match {
    case CType.CList(elementType) => new TypedValueAccessor(elementType)
    case CType.CSeq(elementType)  => new TypedValueAccessor(elementType)
    case other =>
      throw TypeMismatchError(
        expected = "CList or CSeq type",
        actual = other.toString,
        context = Map("operation" -> "elementAccessor")
      )
  }

  /** Get an accessor for optional inner type.
    */
  def innerAccessor: TypedValueAccessor = cType match {
    case CType.COptional(innerType) => new TypedValueAccessor(innerType)
    case other =>
      throw TypeMismatchError(
        expected = "COptional type",
        actual = other.toString,
        context = Map("operation" -> "innerAccessor")
      )
  }

  /** Extract a Long from a RawValue.
    */
  def getInt(raw: RawValue): Long = raw match {
    case RawValue.RInt(v) => v
    case other =>
      throw TypeMismatchError(
        expected = "RInt",
        actual = other.getClass.getSimpleName
      )
  }

  /** Extract a Double from a RawValue.
    */
  def getFloat(raw: RawValue): Double = raw match {
    case RawValue.RFloat(v) => v
    case other =>
      throw TypeMismatchError(
        expected = "RFloat",
        actual = other.getClass.getSimpleName
      )
  }

  /** Extract a String from a RawValue.
    */
  def getString(raw: RawValue): String = raw match {
    case RawValue.RString(v) => v
    case other =>
      throw TypeMismatchError(
        expected = "RString",
        actual = other.getClass.getSimpleName
      )
  }

  /** Extract a Boolean from a RawValue.
    */
  def getBool(raw: RawValue): Boolean = raw match {
    case RawValue.RBool(v) => v
    case other =>
      throw TypeMismatchError(
        expected = "RBool",
        actual = other.getClass.getSimpleName
      )
  }

  /** Convert a RawValue back to CValue when needed (e.g., for JSON output).
    */
  def toCValue(raw: RawValue): CValue = (raw, cType) match {
    // Primitives
    case (RawValue.RString(v), CType.CString) => CValue.CString(v)
    case (RawValue.RInt(v), CType.CInt)       => CValue.CInt(v)
    case (RawValue.RFloat(v), CType.CFloat)   => CValue.CFloat(v)
    case (RawValue.RBool(v), CType.CBoolean)  => CValue.CBoolean(v)

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

    // Specialized primitive seqs
    case (RawValue.RIntList(values), CType.CSeq(CType.CInt)) =>
      CValue.CSeq(values.map(CValue.CInt(_)).toVector, CType.CInt)
    case (RawValue.RFloatList(values), CType.CSeq(CType.CFloat)) =>
      CValue.CSeq(values.map(CValue.CFloat(_)).toVector, CType.CFloat)
    case (RawValue.RStringList(values), CType.CSeq(CType.CString)) =>
      CValue.CSeq(values.map(CValue.CString(_)).toVector, CType.CString)
    case (RawValue.RBoolList(values), CType.CSeq(CType.CBoolean)) =>
      CValue.CSeq(values.map(CValue.CBoolean(_)).toVector, CType.CBoolean)

    // Generic seq
    case (RawValue.RList(values), CType.CSeq(elementType)) =>
      val elementAccessor = new TypedValueAccessor(elementType)
      CValue.CSeq(values.map(elementAccessor.toCValue).toVector, elementType)

    // Map
    case (RawValue.RMap(entries), CType.CMap(keyType, valueType)) =>
      val keyAccessor   = new TypedValueAccessor(keyType)
      val valueAccessor = new TypedValueAccessor(valueType)
      CValue.CMap(
        entries.map { case (k, v) =>
          (keyAccessor.toCValue(k), valueAccessor.toCValue(v))
        }.toVector,
        keyType,
        valueType
      )

    // Product
    case (RawValue.RProduct(values), CType.CProduct(structure)) =>
      val sortedFields = structure.toList.sortBy(_._1)
      val convertedFields = sortedFields
        .zip(values)
        .map { case ((name, fieldType), value) =>
          name -> new TypedValueAccessor(fieldType).toCValue(value)
        }
        .toMap
      CValue.CProduct(convertedFields, structure)

    // Union
    case (RawValue.RUnion(tag, value), CType.CUnion(structure)) =>
      val variantType = structure.getOrElse(
        tag,
        throw TypeMismatchError(
          expected = s"known union variant",
          actual = s"unknown variant '$tag'",
          context = Map("tag" -> tag, "knownVariants" -> structure.keys.mkString(", "))
        )
      )
      CValue.CUnion(new TypedValueAccessor(variantType).toCValue(value), structure, tag)

    case (raw, expected) =>
      throw TypeMismatchError(
        expected = expected.toString,
        actual = raw.getClass.getSimpleName
      )
  }
}

object TypedValueAccessor {

  def apply(cType: CType): TypedValueAccessor = new TypedValueAccessor(cType)
}
