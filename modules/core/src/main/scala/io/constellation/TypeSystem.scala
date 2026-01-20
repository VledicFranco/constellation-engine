package io.constellation

import cats.effect.IO
import cats.implicits.toTraverseOps
import scala.deriving.Mirror
import scala.compiletime.{constValue, erasedValue, summonInline}

/** Constellation Type System
  *
  * This module defines the core type system used throughout Constellation Engine. It provides
  * runtime type representations (CType) and value representations (CValue) that enable type-safe
  * data flow between modules in a DAG pipeline.
  *
  * ==Type Hierarchy==
  *
  * {{{
  * CType (runtime type representation)
  * ├── CString      - String type
  * ├── CInt         - Integer type (Long)
  * ├── CFloat       - Floating point type (Double)
  * ├── CBoolean     - Boolean type
  * ├── CList        - Homogeneous list type
  * ├── CMap         - Key-value map type
  * ├── CProduct     - Record/struct type (named fields)
  * └── CUnion       - Tagged union type
  *
  * CValue (runtime value representation)
  * ├── CString      - String value
  * ├── CInt         - Integer value
  * ├── CFloat       - Float value
  * ├── CBoolean     - Boolean value
  * ├── CList        - List value with element type
  * ├── CMap         - Map value with key/value types
  * ├── CProduct     - Record value with named fields
  * └── CUnion       - Tagged union value
  * }}}
  *
  * ==Type Tags==
  *
  * CTypeTag provides compile-time type mapping from Scala types to CType:
  * {{{
  * given CTypeTag[String]  -> CType.CString
  * given CTypeTag[Long]    -> CType.CInt
  * given CTypeTag[Double]  -> CType.CFloat
  * given CTypeTag[Boolean] -> CType.CBoolean
  * given CTypeTag[List[A]] -> CType.CList(A's type)
  * }}}
  *
  * ==Injectors and Extractors==
  *
  * CValueInjector converts Scala values to CValue:
  * {{{
  * CValueInjector[String].inject("hello") // => CValue.CString("hello")
  * }}}
  *
  * CValueExtractor converts CValue back to Scala types:
  * {{{
  * CValueExtractor[String].extract(CValue.CString("hello")) // => Right("hello")
  * }}}
  *
  * @see
  *   [[io.constellation.Spec]] for DAG specification types
  * @see
  *   [[io.constellation.ModuleBuilder]] for module definition API
  */

/** Runtime type representation for Constellation values.
  *
  * CType is a sealed trait representing all possible types that can flow through a Constellation
  * DAG. Every CValue has a corresponding CType.
  */
sealed trait CType

object CType {
  case object CString                                       extends CType
  case object CInt                                          extends CType
  case object CFloat                                        extends CType
  case object CBoolean                                      extends CType
  final case class CList(valuesType: CType)                 extends CType
  final case class CMap(keysType: CType, valuesType: CType) extends CType
  final case class CProduct(structure: Map[String, CType])  extends CType
  final case class CUnion(structure: Map[String, CType])    extends CType

  /** Optional type - represents a value that may or may not exist */
  final case class COptional(innerType: CType) extends CType
}

sealed trait CValue {
  def ctype: CType
}

object CValue {
  final case class CString(value: String) extends CValue {
    override def ctype: CType = CType.CString
  }
  final case class CInt(value: Long) extends CValue {
    override def ctype: CType = CType.CInt
  }
  final case class CFloat(value: Double) extends CValue {
    override def ctype: CType = CType.CFloat
  }
  final case class CBoolean(value: Boolean) extends CValue {
    override def ctype: CType = CType.CBoolean
  }
  final case class CList(value: Vector[CValue], subtype: CType) extends CValue {
    override def ctype: CType = CType.CList(subtype)
  }
  final case class CMap(value: Vector[(CValue, CValue)], keysType: CType, valuesType: CType)
      extends CValue {
    override def ctype: CType = CType.CMap(keysType, valuesType)
  }
  final case class CProduct(value: Map[String, CValue], structure: Map[String, CType])
      extends CValue {
    override def ctype: CType = CType.CProduct(structure)
  }
  final case class CUnion(value: CValue, structure: Map[String, CType], tag: String)
      extends CValue {
    override def ctype: CType = CType.CUnion(structure)
  }

  /** Some value - present Optional value */
  final case class CSome(value: CValue, innerType: CType) extends CValue {
    override def ctype: CType = CType.COptional(innerType)
  }

  /** None value - absent Optional value */
  final case class CNone(innerType: CType) extends CValue {
    override def ctype: CType = CType.COptional(innerType)
  }
}

trait CTypeTag[A] {

  def cType: CType
}

object CTypeTag {

  private def of[A](cType0: CType): CTypeTag[A] = new CTypeTag[A] {
    override def cType: CType = cType0
  }

  given stringTag: CTypeTag[String] =
    of[String](CType.CString)

  given longTag: CTypeTag[Long] =
    of[Long](CType.CInt)

  given doubleTag: CTypeTag[Double] =
    of[Double](CType.CFloat)

  given booleanTag: CTypeTag[Boolean] =
    of[Boolean](CType.CBoolean)

  given vectorTag[A](using injector: CTypeTag[A]): CTypeTag[Vector[A]] =
    of[Vector[A]](CType.CList(injector.cType))

  given listTag[A](using injector: CTypeTag[A]): CTypeTag[List[A]] =
    of[List[A]](CType.CList(injector.cType))

  given mapTag[A, B](using
      keyInjector: CTypeTag[A],
      valueInjector: CTypeTag[B]
  ): CTypeTag[Map[A, B]] =
    of[Map[A, B]](CType.CMap(keyInjector.cType, valueInjector.cType))

  given optionTag[A](using innerTag: CTypeTag[A]): CTypeTag[Option[A]] =
    of[Option[A]](CType.COptional(innerTag.cType))

  /** Derive CTypeTag for a case class using Scala 3 Mirrors.
    *
    * This given has lower priority than primitive type tags, ensuring that primitive types like
    * String, Long, Double, Boolean are resolved correctly without ambiguity.
    *
    * @tparam T
    *   A case class type (Product)
    * @return
    *   CTypeTag[T] with CType.CProduct containing the field structure
    */
  inline given productTag[T <: Product](using m: Mirror.ProductOf[T]): CTypeTag[T] = {
    val fieldNames = getLabels[m.MirroredElemLabels]
    val fieldTypes = getTypes[m.MirroredElemTypes]
    val structure  = fieldNames.zip(fieldTypes).toMap
    of[T](CType.CProduct(structure))
  }

  private inline def getLabels[T <: Tuple]: List[String] =
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (h *: t)   => constValue[h].asInstanceOf[String] :: getLabels[t]
    }

  private inline def getTypes[T <: Tuple]: List[CType] =
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (h *: t)   => summonInline[CTypeTag[h]].cType :: getTypes[t]
    }
}

/** Convenience function to derive CType from a Scala type.
  *
  * This function uses the CTypeTag type class to convert Scala types to their corresponding CType
  * representation. It works with primitive types, collections, and case classes (via Scala 3
  * Mirrors).
  *
  * ==Examples==
  * {{{
  * // Primitive types
  * deriveType[String]  // => CType.CString
  * deriveType[Long]    // => CType.CInt
  *
  * // Collections
  * deriveType[List[String]]        // => CType.CList(CType.CString)
  * deriveType[Map[String, Long]]   // => CType.CMap(CType.CString, CType.CInt)
  *
  * // Case classes
  * case class Person(name: String, age: Long)
  * deriveType[Person]  // => CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt))
  *
  * // Nested case classes
  * case class Team(leader: Person, size: Long)
  * deriveType[Team]    // => CType.CProduct(Map("leader" -> CType.CProduct(...), "size" -> CType.CInt))
  * }}}
  *
  * @tparam T
  *   The Scala type to derive CType from
  * @param tag
  *   Implicit CTypeTag instance (resolved at compile time)
  * @return
  *   The CType representation of type T
  */
inline def deriveType[T](using tag: CTypeTag[T]): CType = tag.cType

trait CValueExtractor[A] {

  def extract(data: CValue): IO[A]

  def map[B](f: A => B): CValueExtractor[B] =
    (data: CValue) => extract(data).map(f)
}

object CValueExtractor {

  given stringExtractor: CValueExtractor[String] = {
    case CValue.CString(value) => IO.pure(value)
    case other =>
      IO.raiseError(new RuntimeException(s"Expected CValue.CString, but got $other"))
  }

  given longExtractor: CValueExtractor[Long] = {
    case CValue.CInt(value) => IO.pure(value)
    case other =>
      IO.raiseError(new RuntimeException(s"Expected CValue.CInt, but got $other"))
  }

  given doubleExtractor: CValueExtractor[Double] = {
    case CValue.CFloat(value) => IO.pure(value)
    case other =>
      IO.raiseError(new RuntimeException(s"Expected CValue.CFloat, but got $other"))
  }

  given booleanExtractor: CValueExtractor[Boolean] = {
    case CValue.CBoolean(value) => IO.pure(value)
    case other =>
      IO.raiseError(new RuntimeException(s"Expected CValue.CBoolean, but got $other"))
  }

  given vectorExtractor[A](using extractor: CValueExtractor[A]): CValueExtractor[Vector[A]] = {
    case CValue.CList(value, _) => value.traverse(extractor.extract)
    case other =>
      IO.raiseError(new RuntimeException(s"Expected CValue.CList, but got $other"))
  }

  given listExtractor[A](using extractor: CValueExtractor[A]): CValueExtractor[List[A]] =
    vectorExtractor[A].map(_.toList)

  given mapExtractor[A, B](using
      keyExtractor: CValueExtractor[A],
      valueExtractor: CValueExtractor[B]
  ): CValueExtractor[Map[A, B]] = {
    case CValue.CMap(value, _, _) =>
      value
        .traverse { case (k, v) =>
          for {
            key   <- keyExtractor.extract(k)
            value <- valueExtractor.extract(v)
          } yield key -> value
        }
        .map(_.toMap)
    case other =>
      IO.raiseError(new RuntimeException(s"Expected CValue.CMap, but got $other"))
  }

  given optionExtractor[A](using extractor: CValueExtractor[A]): CValueExtractor[Option[A]] = {
    case CValue.CSome(value, _) => extractor.extract(value).map(Some(_))
    case CValue.CNone(_)        => IO.pure(None)
    case other =>
      IO.raiseError(new RuntimeException(s"Expected CValue.CSome or CValue.CNone, but got $other"))
  }
}

trait CValueInjector[A] {

  def inject(value: A): CValue

  def contramap[B](f: B => A): CValueInjector[B] =
    (value: B) => inject(f(value))
}

object CValueInjector {

  given stringInjector: CValueInjector[String] = (value: String) => CValue.CString(value)

  given longInjector: CValueInjector[Long] = (value: Long) => CValue.CInt(value)

  given doubleInjector: CValueInjector[Double] = (value: Double) => CValue.CFloat(value)

  given booleanInjector: CValueInjector[Boolean] = (value: Boolean) => CValue.CBoolean(value)

  given vectorInjector[A](using
      injector: CValueInjector[A],
      typeTag: CTypeTag[A]
  ): CValueInjector[Vector[A]] =
    (value: Vector[A]) => CValue.CList(value.map(injector.inject), typeTag.cType)

  given listInjector[A](using
      injector: CValueInjector[A],
      typeTag: CTypeTag[A]
  ): CValueInjector[List[A]] =
    vectorInjector[A].contramap(_.toVector)

  given mapInjector[A, B](using
      keyInjector: CValueInjector[A],
      valueInjector: CValueInjector[B],
      keyTypeTag: CTypeTag[A],
      valueTypeTag: CTypeTag[B]
  ): CValueInjector[Map[A, B]] =
    (value: Map[A, B]) =>
      CValue.CMap(
        value.toVector.map { case (k, v) => keyInjector.inject(k) -> valueInjector.inject(v) },
        keyTypeTag.cType,
        valueTypeTag.cType
      )

  given optionInjector[A](using
      injector: CValueInjector[A],
      typeTag: CTypeTag[A]
  ): CValueInjector[Option[A]] =
    (value: Option[A]) =>
      value match {
        case Some(v) => CValue.CSome(injector.inject(v), typeTag.cType)
        case None    => CValue.CNone(typeTag.cType)
      }
}
