package io.constellation.property

import org.scalacheck.{Arbitrary, Gen}
import io.constellation.{CType, CValue}

/** ScalaCheck generators for Constellation core types (RFC-013 Phase 5.2)
  *
  * Provides generators for CType, CValue, and constellation-lang source fragments.
  */
object ConstellationGenerators {

  // -------------------------------------------------------------------------
  // CType Generators
  // -------------------------------------------------------------------------

  /** Generate a primitive CType */
  val genPrimitiveCType: Gen[CType] = Gen.oneOf(
    CType.CString,
    CType.CInt,
    CType.CFloat,
    CType.CBoolean
  )

  /** Generate a CType up to a given depth (to avoid infinite recursion) */
  def genCType(maxDepth: Int = 3): Gen[CType] = {
    if (maxDepth <= 0) genPrimitiveCType
    else Gen.frequency(
      (4, genPrimitiveCType),
      (1, genCType(maxDepth - 1).map(CType.CList.apply)),
      (1, for {
        kt <- genPrimitiveCType
        vt <- genCType(maxDepth - 1)
      } yield CType.CMap(kt, vt)),
      (1, for {
        size <- Gen.choose(1, 4)
        fields <- Gen.listOfN(size, for {
          name <- genFieldName
          t <- genCType(maxDepth - 1)
        } yield (name, t))
      } yield CType.CProduct(fields.toMap)),
      (1, genCType(maxDepth - 1).map(CType.COptional.apply))
    )
  }

  /** Generate a valid field/variable name */
  val genFieldName: Gen[String] = for {
    head <- Gen.alphaLowerChar
    tail <- Gen.listOf(Gen.frequency(
      (3, Gen.alphaNumChar),
      (1, Gen.const('_'))
    )).map(_.mkString)
    name <- Gen.const(s"$head$tail")
    if name.length <= 20 && name.length >= 1
  } yield name

  // -------------------------------------------------------------------------
  // CValue Generators
  // -------------------------------------------------------------------------

  /** Generate a CValue for a given CType */
  def genCValueForType(ctype: CType, maxDepth: Int = 2): Gen[CValue] = ctype match {
    case CType.CString =>
      Gen.alphaNumStr.map(s => CValue.CString(s.take(100)))

    case CType.CInt =>
      Gen.choose(-1000000L, 1000000L).map(CValue.CInt.apply)

    case CType.CFloat =>
      Gen.choose(-1e6, 1e6).map(CValue.CFloat.apply)

    case CType.CBoolean =>
      Gen.oneOf(true, false).map(CValue.CBoolean.apply)

    case CType.CList(elemType) =>
      if (maxDepth <= 0) Gen.const(CValue.CList(Vector.empty, elemType))
      else for {
        size <- Gen.choose(0, 5)
        elems <- Gen.listOfN(size, genCValueForType(elemType, maxDepth - 1))
      } yield CValue.CList(elems.toVector, elemType)

    case CType.CMap(kt, vt) =>
      if (maxDepth <= 0) Gen.const(CValue.CMap(Vector.empty, kt, vt))
      else for {
        size <- Gen.choose(0, 3)
        pairs <- Gen.listOfN(size, for {
          k <- genCValueForType(kt, maxDepth - 1)
          v <- genCValueForType(vt, maxDepth - 1)
        } yield (k, v))
      } yield CValue.CMap(pairs.toVector, kt, vt)

    case CType.CProduct(structure) =>
      if (maxDepth <= 0 || structure.isEmpty)
        Gen.const(CValue.CProduct(
          structure.map { case (k, t) => k -> defaultCValue(t) },
          structure
        ))
      else for {
        values <- Gen.sequence[List[CValue], CValue](
          structure.values.toList.map(t => genCValueForType(t, maxDepth - 1))
        )
      } yield CValue.CProduct(
        structure.keys.zip(values).toMap,
        structure
      )

    case CType.CUnion(structure) =>
      for {
        (tag, tagType) <- Gen.oneOf(structure.toList)
        value <- genCValueForType(tagType, maxDepth - 1)
      } yield CValue.CUnion(value, structure, tag)

    case CType.COptional(innerType) =>
      Gen.oneOf(
        Gen.const(CValue.CNone(innerType)),
        genCValueForType(innerType, maxDepth - 1).map(v => CValue.CSome(v, innerType))
      )
  }

  /** Default value for any CType (used as fallback) */
  private def defaultCValue(ctype: CType): CValue = ctype match {
    case CType.CString => CValue.CString("")
    case CType.CInt => CValue.CInt(0)
    case CType.CFloat => CValue.CFloat(0.0)
    case CType.CBoolean => CValue.CBoolean(false)
    case CType.CList(et) => CValue.CList(Vector.empty, et)
    case CType.CMap(kt, vt) => CValue.CMap(Vector.empty, kt, vt)
    case CType.CProduct(s) => CValue.CProduct(s.map { case (k, t) => k -> defaultCValue(t) }, s)
    case CType.CUnion(s) =>
      val (tag, t) = s.head
      CValue.CUnion(defaultCValue(t), s, tag)
    case CType.COptional(t) => CValue.CNone(t)
  }

  /** Generate a random CType and matching CValue pair */
  val genTypedValue: Gen[(CType, CValue)] = for {
    ctype <- genCType(2)
    value <- genCValueForType(ctype)
  } yield (ctype, value)

  // -------------------------------------------------------------------------
  // Constellation-lang source generators
  // -------------------------------------------------------------------------

  /** Generate a valid variable name (not a reserved word) */
  val genVarName: Gen[String] = for {
    head <- Gen.alphaLowerChar
    tail <- Gen.listOfN(Gen.choose(2, 8).sample.getOrElse(4), Gen.alphaLowerChar).map(_.mkString)
  } yield s"$head$tail"

  /** Generate a type name for constellation-lang */
  val genTypeName: Gen[String] = Gen.oneOf("String", "Int", "Float", "Boolean")

  /** Generate a simple valid constellation-lang program with boolean operations */
  val genSimpleBooleanProgram: Gen[String] = for {
    numVars <- Gen.choose(2, 10)
    varNames <- Gen.listOfN(numVars, genVarName).map(_.distinct.take(numVars))
    if varNames.size >= 2
  } yield {
    val sb = new StringBuilder
    sb.append("in flag: Boolean\n")
    sb.append("in fallback: Int\n")
    sb.append("in value: Int\n\n")

    // Generate boolean variable chain
    sb.append(s"${varNames.head} = flag\n")
    varNames.tail.zipWithIndex.foreach { case (name, idx) =>
      val prev = varNames(idx)
      val op = idx % 4 match {
        case 0 => s"$prev and flag"
        case 1 => s"$prev or flag"
        case 2 => s"not $prev"
        case 3 => s"($prev and flag) or not flag"
      }
      sb.append(s"$name = $op\n")
    }

    val last = varNames.last
    sb.append(s"\nguarded = value when $last\n")
    sb.append(s"result = guarded ?? fallback\n")
    sb.append(s"\nout result\n")
    sb.append(s"out $last\n")
    sb.toString
  }

  // -------------------------------------------------------------------------
  // Arbitrary instances
  // -------------------------------------------------------------------------

  implicit val arbCType: Arbitrary[CType] = Arbitrary(genCType())
  implicit val arbPrimitiveCType: Arbitrary[CType] = Arbitrary(genPrimitiveCType)
}
