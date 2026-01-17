package io.constellation.api

import io.constellation.api.json.given
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

class CustomJsonCodecsTest extends AnyFlatSpec with Matchers {

  "CustomJsonCodecs" should "encode/decode Module Status generically" in {
    val status: Module.Status = Module.Status.Timed(100.millis)
    val status2: Module.Status = Module.Status.Fired(200.millis, Some(Map("key" -> "value".asJson)))
    val status4: Module.Status = Module.Status.Unfired

    val encoded1 = status.asJson
    val encoded2 = status2.asJson
    val encoded4 = status4.asJson

    val decoded1 = encoded1.as[Module.Status]
    val decoded2 = encoded2.as[Module.Status]
    val decoded4 = encoded4.as[Module.Status]

    decoded1 shouldBe Right(status)
    decoded2 shouldBe Right(status2)
    decoded4 shouldBe Right(status4)
  }

  it should "encode/decode CTypes generically" in {
    val cType1: CType = CType.CString
    val cType2: CType = CType.CFloat
    val cType3: CType = CType.CMap(CType.CInt, CType.CString)
    val cType4: CType = CType.CList(CType.CBoolean)

    val encoded1 = cType1.asJson
    val encoded2 = cType2.asJson
    val encoded3 = cType3.asJson
    val encoded4 = cType4.asJson

    val decoded1 = encoded1.as[CType]
    val decoded2 = encoded2.as[CType]
    val decoded3 = encoded3.as[CType]
    val decoded4 = encoded4.as[CType]

    decoded1 shouldBe Right(cType1)
    decoded2 shouldBe Right(cType2)
    decoded3 shouldBe Right(cType3)
    decoded4 shouldBe Right(cType4)
  }
}
