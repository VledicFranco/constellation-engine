package io.constellation.stream.connector.websocket

import scala.concurrent.duration.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.constellation.stream.connector.*

class WebSocketConnectorTest extends AnyFlatSpec with Matchers {

  // ===== Source Schema Validation =====

  "WebSocketSourceConnector schema" should "require uri" in {
    val config = ConnectorConfig(Map.empty)
    val result = config.validate(WebSocketSourceConnector.schema)

    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.field == "uri") shouldBe true
  }

  it should "accept valid config with uri only" in {
    val config = ConnectorConfig(Map("uri" -> "ws://localhost:8080/stream"))
    val result = config.validate(WebSocketSourceConnector.schema)

    result.isRight shouldBe true
  }

  it should "apply default for reconnect" in {
    val config = ConnectorConfig(Map("uri" -> "ws://localhost:8080"))
    val result = config.validate(WebSocketSourceConnector.schema)

    result.isRight shouldBe true
    result.toOption.get.getString("reconnect") shouldBe Some("true")
  }

  it should "apply default for reconnect_delay" in {
    val config = ConnectorConfig(Map("uri" -> "ws://localhost:8080"))
    val result = config.validate(WebSocketSourceConnector.schema)

    result.isRight shouldBe true
    result.toOption.get.getDuration("reconnect_delay") shouldBe Some(1.second)
  }

  it should "apply default for max_reconnects" in {
    val config = ConnectorConfig(Map("uri" -> "ws://localhost:8080"))
    val result = config.validate(WebSocketSourceConnector.schema)

    result.isRight shouldBe true
    result.toOption.get.getInt("max_reconnects") shouldBe Some(10)
  }

  it should "reject invalid reconnect enum value" in {
    val config = ConnectorConfig(
      Map(
        "uri"       -> "ws://localhost:8080",
        "reconnect" -> "maybe"
      )
    )
    val result = config.validate(WebSocketSourceConnector.schema)

    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.field == "reconnect") shouldBe true
  }

  it should "reject out-of-range max_reconnects" in {
    val config = ConnectorConfig(
      Map(
        "uri"            -> "ws://localhost:8080",
        "max_reconnects" -> "5000"
      )
    )
    val result = config.validate(WebSocketSourceConnector.schema)

    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.field == "max_reconnects") shouldBe true
  }

  it should "accept custom optional values" in {
    val config = ConnectorConfig(
      Map(
        "uri"             -> "ws://localhost:8080",
        "reconnect"       -> "false",
        "reconnect_delay" -> "5 seconds",
        "max_reconnects"  -> "3"
      )
    )
    val result = config.validate(WebSocketSourceConnector.schema)

    result.isRight shouldBe true
    val validated = result.toOption.get
    validated.getString("reconnect") shouldBe Some("false")
    validated.getDuration("reconnect_delay") shouldBe Some(5.seconds)
    validated.getInt("max_reconnects") shouldBe Some(3)
  }

  // ===== Sink Schema Validation =====

  "WebSocketSinkConnector schema" should "require uri" in {
    val config = ConnectorConfig(Map.empty)
    val result = config.validate(WebSocketSinkConnector.schema)

    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.field == "uri") shouldBe true
  }

  it should "accept valid config with uri" in {
    val config = ConnectorConfig(Map("uri" -> "ws://localhost:8080/sink"))
    val result = config.validate(WebSocketSinkConnector.schema)

    result.isRight shouldBe true
  }

  // ===== Connector Properties =====

  "WebSocketSourceConnector" should "have correct typeName" in {
    val connector = new WebSocketSourceConnector("test")
    connector.typeName shouldBe "websocket"
    connector.name shouldBe "test"
  }

  "WebSocketSinkConnector" should "have correct typeName" in {
    val connector = new WebSocketSinkConnector("test")
    connector.typeName shouldBe "websocket"
    connector.name shouldBe "test"
  }

  // ===== Health Check Defaults =====

  "WebSocketSourceConnector" should "default to healthy" in {
    import cats.effect.unsafe.implicits.global
    val connector = new WebSocketSourceConnector("test")
    connector.isHealthy.unsafeRunSync() shouldBe true
  }

  "WebSocketSinkConnector" should "default to healthy" in {
    import cats.effect.unsafe.implicits.global
    val connector = new WebSocketSinkConnector("test")
    connector.isHealthy.unsafeRunSync() shouldBe true
  }
}
