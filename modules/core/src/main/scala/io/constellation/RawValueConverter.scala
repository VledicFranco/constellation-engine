package io.constellation

/** Converts between CValue and RawValue representations.
  *
  * This converter is used at system boundaries:
  *   - JSON input → RawValue (using fromCValue after JSON parsing)
  *   - Internal execution uses RawValue
  *   - RawValue → JSON output (using TypedValueAccessor.toCValue)
  *
  * ==Memory Optimization==
  *
  * The converter automatically uses specialized array types for primitive lists:
  *   - List[Long] → RIntList
  *   - List[Double] → RFloatList
  *   - List[String] → RStringList
  *   - List[Boolean] → RBoolList
  *
  * This provides ~6x memory reduction for large numeric arrays.
  */
object RawValueConverter {

  /** Convert a CValue to its memory-efficient RawValue representation. Type information is stripped
    * and must be stored separately (in DataNodeSpec.cType).
    */
  def fromCValue(cValue: CValue): RawValue = cValue match {
    // Primitives
    case CValue.CString(v)  => RawValue.RString(v)
    case CValue.CInt(v)     => RawValue.RInt(v)
    case CValue.CFloat(v)   => RawValue.RFloat(v)
    case CValue.CBoolean(v) => RawValue.RBool(v)

    // Optional
    case CValue.CSome(v, _) => RawValue.RSome(fromCValue(v))
    case CValue.CNone(_)    => RawValue.RNone

    // Lists - use specialized arrays when possible
    case CValue.CList(values, CType.CInt) =>
      val arr = new Array[Long](values.length)
      var i   = 0
      while i < values.length do {
        values(i) match {
          case CValue.CInt(v) => arr(i) = v
          case other => throw new TypeMismatchException(s"Expected CInt in list, got $other")
        }
        i += 1
      }
      RawValue.RIntList(arr)

    case CValue.CList(values, CType.CFloat) =>
      val arr = new Array[Double](values.length)
      var i   = 0
      while i < values.length do {
        values(i) match {
          case CValue.CFloat(v) => arr(i) = v
          case other => throw new TypeMismatchException(s"Expected CFloat in list, got $other")
        }
        i += 1
      }
      RawValue.RFloatList(arr)

    case CValue.CList(values, CType.CString) =>
      val arr = new Array[String](values.length)
      var i   = 0
      while i < values.length do {
        values(i) match {
          case CValue.CString(v) => arr(i) = v
          case other => throw new TypeMismatchException(s"Expected CString in list, got $other")
        }
        i += 1
      }
      RawValue.RStringList(arr)

    case CValue.CList(values, CType.CBoolean) =>
      val arr = new Array[Boolean](values.length)
      var i   = 0
      while i < values.length do {
        values(i) match {
          case CValue.CBoolean(v) => arr(i) = v
          case other => throw new TypeMismatchException(s"Expected CBoolean in list, got $other")
        }
        i += 1
      }
      RawValue.RBoolList(arr)

    // Generic list for nested types
    case CValue.CList(values, _) =>
      RawValue.RList(values.map(fromCValue).toArray)

    // Map
    case CValue.CMap(entries, _, _) =>
      RawValue.RMap(entries.map { case (k, v) => (fromCValue(k), fromCValue(v)) }.toArray)

    // Product - field order matches sorted field names
    case CValue.CProduct(fields, structure) =>
      val sortedFieldNames = structure.keys.toList.sorted
      val values = sortedFieldNames
        .map { name =>
          fields.getOrElse(
            name,
            throw new TypeMismatchException(s"Missing field '$name' in product")
          )
        }
        .map(fromCValue)
        .toArray
      RawValue.RProduct(values)

    // Union
    case CValue.CUnion(value, _, tag) =>
      RawValue.RUnion(tag, fromCValue(value))
  }

  /** Convert a RawValue back to CValue using type information. Delegates to TypedValueAccessor.
    */
  def toCValue(raw: RawValue, cType: CType): CValue =
    TypedValueAccessor(cType).toCValue(raw)
}
