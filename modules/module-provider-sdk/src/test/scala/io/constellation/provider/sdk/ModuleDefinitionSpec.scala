package io.constellation.provider.sdk

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.CType
import io.constellation.provider.TypeSchemaConverter
import io.constellation.provider.v1.provider as pb

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ModuleDefinitionSpec extends AnyFlatSpec with Matchers {

  private val stringType  = CType.CString
  private val intType     = CType.CInt
  private val productType = CType.CProduct(Map("text" -> CType.CString, "count" -> CType.CInt))

  private val echoHandler: CValue => IO[CValue] = v => IO.pure(v)

  private type CValue = io.constellation.CValue

  // ===== Construction =====

  "ModuleDefinition" should "store all fields" in {
    val md = ModuleDefinition(
      name = "analyze",
      inputType = productType,
      outputType = stringType,
      version = "1.0.0",
      description = "Analyzes text",
      handler = echoHandler
    )

    md.name shouldBe "analyze"
    md.inputType shouldBe productType
    md.outputType shouldBe stringType
    md.version shouldBe "1.0.0"
    md.description shouldBe "Analyzes text"
  }

  // ===== qualifiedName =====

  it should "produce a qualified name with namespace prefix" in {
    val md = ModuleDefinition("analyze", stringType, stringType, "1.0.0", "desc", echoHandler)

    md.qualifiedName("ml.sentiment") shouldBe "ml.sentiment.analyze"
  }

  it should "produce a qualified name with single-segment namespace" in {
    val md = ModuleDefinition("run", intType, intType, "2.0.0", "desc", echoHandler)

    md.qualifiedName("tools") shouldBe "tools.run"
  }

  // ===== toDeclaration =====

  it should "convert to ModuleDeclaration with correct schemas" in {
    val md = ModuleDefinition(
      name = "transform",
      inputType = productType,
      outputType = CType.CList(CType.CString),
      version = "1.2.3",
      description = "Transforms input",
      handler = echoHandler
    )

    val decl = md.toDeclaration
    decl.name shouldBe "transform"
    decl.version shouldBe "1.2.3"
    decl.description shouldBe "Transforms input"

    // Round-trip: declaration schema -> CType should match original
    val inputRoundTrip = TypeSchemaConverter.toCType(decl.inputSchema.get)
    inputRoundTrip shouldBe Right(productType)

    val outputRoundTrip = TypeSchemaConverter.toCType(decl.outputSchema.get)
    outputRoundTrip shouldBe Right(CType.CList(CType.CString))
  }

  it should "convert primitive types in toDeclaration" in {
    val md = ModuleDefinition("echo", stringType, intType, "0.1.0", "Echo", echoHandler)

    val decl     = md.toDeclaration
    val inputRt  = TypeSchemaConverter.toCType(decl.inputSchema.get)
    val outputRt = TypeSchemaConverter.toCType(decl.outputSchema.get)

    inputRt shouldBe Right(CType.CString)
    outputRt shouldBe Right(CType.CInt)
  }
}
