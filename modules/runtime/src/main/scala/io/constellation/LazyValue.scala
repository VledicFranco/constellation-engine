package io.constellation

import io.circe.Json
import scala.collection.mutable

/** Lazy CValue that defers JSON conversion until value is accessed.
  * Provides significant performance gains when only a subset of data is used.
  *
  * ==Usage==
  *
  * Lazy values wrap JSON and only convert to CValue when `materialize()` is called:
  * {{{
  * val lazy = LazyJsonValue(json, CType.CString)
  * // No conversion yet
  * val value = lazy.materialize // Conversion happens here
  * }}}
  *
  * For lists, `LazyListValue` converts elements on-demand:
  * {{{
  * val lazyList = LazyListValue(jsonArray, CType.CInt)
  * val first = lazyList.get(0) // Only first element converted
  * }}}
  */
sealed trait LazyCValue {
  /** Convert to CValue, performing deferred conversion if needed */
  def materialize: Either[String, CValue]

  /** Get the expected CType */
  def cType: CType
}

/** Wraps a JSON value and only converts to CValue when materialize() is called.
  * Caches the result after first conversion.
  *
  * @param json The JSON value to wrap
  * @param expectedType The expected CType for conversion
  */
final class LazyJsonValue(
    val json: Json,
    val expectedType: CType
) extends LazyCValue {

  @volatile private var cached: Option[Either[String, CValue]] = None

  def materialize: Either[String, CValue] = {
    cached match {
      case Some(result) => result
      case None =>
        val result = JsonCValueConverter.jsonToCValue(json, expectedType)
        cached = Some(result)
        result
    }
  }

  def cType: CType = expectedType

  /** Check if value has been materialized */
  def isMaterialized: Boolean = cached.isDefined
}

object LazyJsonValue {
  def apply(json: Json, expectedType: CType): LazyJsonValue =
    new LazyJsonValue(json, expectedType)
}

/** Lazy list that only converts accessed elements.
  * Ideal for large arrays where only a subset is needed.
  *
  * @param jsonArray The JSON array as a Vector of Json elements
  * @param elementType The CType of each element
  */
final class LazyListValue(
    val jsonArray: Vector[Json],
    val elementType: CType
) extends LazyCValue {

  private val cache: mutable.Map[Int, Either[String, CValue]] = mutable.Map.empty

  /** Get element at index, converting on first access */
  def get(index: Int): Either[String, CValue] = {
    if (index < 0 || index >= jsonArray.size) {
      Left(s"Index $index out of bounds for list of size ${jsonArray.size}")
    } else {
      cache.getOrElseUpdate(index, {
        JsonCValueConverter.jsonToCValue(jsonArray(index), elementType, s"[$index]")
      })
    }
  }

  def size: Int = jsonArray.size

  /** Materialize all elements - use sparingly for large lists */
  def materialize: Either[String, CValue] = {
    // Convert all elements
    val results = jsonArray.indices.map(get)
    val errors = results.collect { case Left(err) => err }
    if (errors.nonEmpty) {
      Left(errors.mkString("; "))
    } else {
      val values = results.collect { case Right(v) => v }.toVector
      Right(CValue.CList(values, elementType))
    }
  }

  def cType: CType = CType.CList(elementType)

  /** Get number of materialized elements */
  def materializedCount: Int = cache.size

  /** Check if all elements are materialized */
  def isFullyMaterialized: Boolean = cache.size == jsonArray.size
}

object LazyListValue {
  def apply(jsonArray: Vector[Json], elementType: CType): LazyListValue =
    new LazyListValue(jsonArray, elementType)

  /** Create from Json, extracting the array */
  def fromJson(json: Json, elementType: CType): Either[String, LazyListValue] = {
    json.asArray match {
      case Some(arr) => Right(new LazyListValue(arr, elementType))
      case None => Left(s"Expected array, got ${if (json.isNull) "null" else json.name}")
    }
  }
}

/** Lazy product (record) that only converts accessed fields.
  *
  * @param jsonObj The JSON object as a map of field name to Json
  * @param fieldTypes Map of field name to expected CType
  */
final class LazyProductValue(
    val jsonObj: Map[String, Json],
    val fieldTypes: Map[String, CType]
) extends LazyCValue {

  private val cache: mutable.Map[String, Either[String, CValue]] = mutable.Map.empty

  /** Get field by name, converting on first access */
  def getField(name: String): Either[String, Option[CValue]] = {
    fieldTypes.get(name) match {
      case None => Right(None) // Field not in expected structure
      case Some(fieldType) =>
        jsonObj.get(name) match {
          case None => Left(s"Missing required field '$name'")
          case Some(json) =>
            cache.getOrElseUpdate(name, {
              JsonCValueConverter.jsonToCValue(json, fieldType, name)
            }).map(Some(_))
        }
    }
  }

  /** Get field, requiring it to exist */
  def getFieldRequired(name: String): Either[String, CValue] = {
    getField(name).flatMap {
      case Some(v) => Right(v)
      case None => Left(s"Unknown field '$name'")
    }
  }

  /** Materialize all fields */
  def materialize: Either[String, CValue] = {
    val results = fieldTypes.map { case (name, _) =>
      getFieldRequired(name).map(name -> _)
    }
    val errors = results.collect { case Left(err) => err }
    if (errors.nonEmpty) {
      Left(errors.mkString("; "))
    } else {
      val fields = results.collect { case Right(pair) => pair }.toMap
      Right(CValue.CProduct(fields, fieldTypes))
    }
  }

  def cType: CType = CType.CProduct(fieldTypes)

  /** Get number of materialized fields */
  def materializedCount: Int = cache.size

  /** Get field names */
  def fieldNames: Set[String] = fieldTypes.keySet
}

object LazyProductValue {
  def apply(jsonObj: Map[String, Json], fieldTypes: Map[String, CType]): LazyProductValue =
    new LazyProductValue(jsonObj, fieldTypes)

  /** Create from Json, extracting the object */
  def fromJson(json: Json, fieldTypes: Map[String, CType]): Either[String, LazyProductValue] = {
    json.asObject match {
      case Some(obj) => Right(new LazyProductValue(obj.toMap, fieldTypes))
      case None => Left(s"Expected object, got ${if (json.isNull) "null" else json.name}")
    }
  }
}
