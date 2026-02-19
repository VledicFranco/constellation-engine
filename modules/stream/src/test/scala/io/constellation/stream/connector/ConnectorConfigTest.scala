package io.constellation.stream.connector

import scala.concurrent.duration.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConnectorConfigTest extends AnyFlatSpec with Matchers {

  // ===== Required Field Validation =====

  "ConnectorConfig" should "fail when a required field is missing" in {
    val schema = ConnectorSchema(
      required = Map("uri" -> PropertyType.StringProp())
    )
    val config = ConnectorConfig(Map.empty)
    val result = config.validate(schema)

    result.isLeft shouldBe true
    result.left.toOption.get should have size 1
    result.left.toOption.get.head shouldBe a[ConnectorConfigError.MissingRequired]
    result.left.toOption.get.head.field shouldBe "uri"
  }

  it should "pass when all required fields are present" in {
    val schema = ConnectorSchema(
      required = Map("uri" -> PropertyType.StringProp())
    )
    val config = ConnectorConfig(Map("uri" -> "ws://localhost:8080"))
    val result = config.validate(schema)

    result.isRight shouldBe true
    result.toOption.get.getString("uri") shouldBe Some("ws://localhost:8080")
  }

  // ===== Int Validation =====

  it should "fail when an int field has non-numeric value" in {
    val schema = ConnectorSchema(
      required = Map("port" -> PropertyType.IntProp(min = 1, max = 65535))
    )
    val config = ConnectorConfig(Map("port" -> "not-a-number"))
    val result = config.validate(schema)

    result.isLeft shouldBe true
    result.left.toOption.get.head shouldBe a[ConnectorConfigError.InvalidType]
  }

  it should "fail when an int field is out of range" in {
    val schema = ConnectorSchema(
      required = Map("port" -> PropertyType.IntProp(min = 1, max = 65535))
    )
    val config = ConnectorConfig(Map("port" -> "70000"))
    val result = config.validate(schema)

    result.isLeft shouldBe true
    val err = result.left.toOption.get.head.asInstanceOf[ConnectorConfigError.OutOfRange]
    err.field shouldBe "port"
    err.value shouldBe 70000
    err.min shouldBe 1
    err.max shouldBe 65535
  }

  it should "pass when an int field is within range" in {
    val schema = ConnectorSchema(
      required = Map("port" -> PropertyType.IntProp(min = 1, max = 65535))
    )
    val config = ConnectorConfig(Map("port" -> "8080"))
    val result = config.validate(schema)

    result.isRight shouldBe true
    result.toOption.get.getInt("port") shouldBe Some(8080)
  }

  // ===== Enum Validation =====

  it should "fail when an enum field has invalid value" in {
    val schema = ConnectorSchema(
      required = Map("mode" -> PropertyType.EnumProp(Set("batch", "stream")))
    )
    val config = ConnectorConfig(Map("mode" -> "invalid"))
    val result = config.validate(schema)

    result.isLeft shouldBe true
    val err = result.left.toOption.get.head.asInstanceOf[ConnectorConfigError.InvalidEnum]
    err.value shouldBe "invalid"
    err.allowed shouldBe Set("batch", "stream")
  }

  it should "pass when an enum field has valid value" in {
    val schema = ConnectorSchema(
      required = Map("mode" -> PropertyType.EnumProp(Set("batch", "stream")))
    )
    val config = ConnectorConfig(Map("mode" -> "stream"))

    config.validate(schema).isRight shouldBe true
  }

  // ===== Duration Validation =====

  it should "parse valid duration strings" in {
    val schema = ConnectorSchema(
      required = Map("timeout" -> PropertyType.DurationProp())
    )
    val config = ConnectorConfig(Map("timeout" -> "5 seconds"))
    val result = config.validate(schema)

    result.isRight shouldBe true
    result.toOption.get.getDuration("timeout") shouldBe Some(5.seconds)
  }

  it should "fail on invalid duration strings" in {
    val schema = ConnectorSchema(
      required = Map("timeout" -> PropertyType.DurationProp())
    )
    val config = ConnectorConfig(Map("timeout" -> "not-a-duration"))
    val result = config.validate(schema)

    result.isLeft shouldBe true
    result.left.toOption.get.head shouldBe a[ConnectorConfigError.InvalidType]
  }

  // ===== Defaults =====

  it should "apply defaults for absent optional fields" in {
    val schema = ConnectorSchema(
      optional = Map(
        "retries" -> PropertyType.IntProp(default = Some(3)),
        "mode"    -> PropertyType.EnumProp(Set("fast", "slow"), default = Some("fast"))
      )
    )
    val config = ConnectorConfig(Map.empty)
    val result = config.validate(schema)

    result.isRight shouldBe true
    val validated = result.toOption.get
    validated.getInt("retries") shouldBe Some(3)
    validated.getString("mode") shouldBe Some("fast")
  }

  it should "not override provided values with defaults" in {
    val schema = ConnectorSchema(
      optional = Map("retries" -> PropertyType.IntProp(default = Some(3)))
    )
    val config = ConnectorConfig(Map("retries" -> "10"))
    val result = config.validate(schema)

    result.isRight shouldBe true
    result.toOption.get.getInt("retries") shouldBe Some(10)
  }

  // ===== Error Accumulation =====

  it should "accumulate multiple errors" in {
    val schema = ConnectorSchema(
      required = Map(
        "uri"  -> PropertyType.StringProp(),
        "port" -> PropertyType.IntProp(min = 1, max = 65535)
      )
    )
    val config = ConnectorConfig(Map.empty) // both missing
    val result = config.validate(schema)

    result.isLeft shouldBe true
    result.left.toOption.get should have size 2
    result.left.toOption.get.map(_.field).toSet shouldBe Set("uri", "port")
  }

  // ===== Empty Schema =====

  it should "pass validation with empty schema" in {
    val config = ConnectorConfig(Map("extra" -> "ignored"))
    val result = config.validate(ConnectorSchema.empty)

    result.isRight shouldBe true
  }

  // ===== ValidatedConnectorConfig =====

  "ValidatedConnectorConfig" should "support getStringOrDefault" in {
    val validated = ValidatedConnectorConfig.empty
    validated.getStringOrDefault("missing", "fallback") shouldBe "fallback"
  }

  it should "expose toMap" in {
    val schema = ConnectorSchema(
      required = Map("key" -> PropertyType.StringProp())
    )
    val config   = ConnectorConfig(Map("key" -> "value"))
    val validated = config.validate(schema).toOption.get

    validated.toMap shouldBe Map("key" -> "value")
  }

  // ===== ConnectorConfigError messages =====

  "ConnectorConfigError" should "produce readable messages" in {
    ConnectorConfigError.MissingRequired("uri").message should include("uri")
    ConnectorConfigError.InvalidType("port", "Int", "abc").message should include("Int")
    ConnectorConfigError.OutOfRange("port", 99999, 1, 65535).message should include("99999")
    ConnectorConfigError.InvalidEnum("mode", "bad", Set("a", "b")).message should include("bad")
  }
}
