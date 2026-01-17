package io.constellation

import cats.effect.IO
import cats.implicits.toTraverseOps

sealed trait CType

object CType {
  case object CString extends CType
  case object CInt extends CType
  case object CFloat extends CType
  case object CBoolean extends CType
  final case class CList(valuesType: CType) extends CType
  final case class CMap(keysType: CType, valuesType: CType) extends CType
  final case class CProduct(structure: Map[String, CType]) extends CType
  final case class CUnion(structure: Map[String, CType]) extends CType
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
  final case class CMap(value: Vector[(CValue, CValue)], keysType: CType, valuesType: CType) extends CValue {
    override def ctype: CType = CType.CMap(keysType, valuesType)
  }
  final case class CProduct(value: Map[String, CValue], structure: Map[String, CType]) extends CValue {
    override def ctype: CType = CType.CProduct(structure)
  }
  final case class CUnion(value: CValue, structure: Map[String, CType], tag: String) extends CValue {
    override def ctype: CType = CType.CUnion(structure)
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

  given mapTag[A, B](using keyInjector: CTypeTag[A], valueInjector: CTypeTag[B]): CTypeTag[Map[A, B]] =
    of[Map[A, B]](CType.CMap(keyInjector.cType, valueInjector.cType))
}

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
            key <- keyExtractor.extract(k)
            value <- valueExtractor.extract(v)
          } yield key -> value
        }
        .map(_.toMap)
    case other =>
      IO.raiseError(new RuntimeException(s"Expected CValue.CMap, but got $other"))
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

  given listInjector[A](using injector: CValueInjector[A], typeTag: CTypeTag[A]): CValueInjector[List[A]] =
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
}
