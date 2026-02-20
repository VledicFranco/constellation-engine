package io.constellation.stream.connector.sse

import scala.concurrent.duration.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.constellation.stream.connector.*

class HttpSseSourceConnectorTest extends AnyFlatSpec with Matchers {

  "HttpSseSourceConnector schema" should "require url" in {
    val config = ConnectorConfig(Map.empty)
    val result = config.validate(HttpSseSourceConnector.schema)

    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.field == "url") shouldBe true
  }

  it should "accept valid config with url only" in {
    val config = ConnectorConfig(Map("url" -> "http://localhost:8080/events"))
    val result = config.validate(HttpSseSourceConnector.schema)

    result.isRight shouldBe true
  }

  it should "apply default for reconnect" in {
    val config = ConnectorConfig(Map("url" -> "http://localhost:8080/events"))
    val result = config.validate(HttpSseSourceConnector.schema)

    result.isRight shouldBe true
    result.toOption.get.getString("reconnect") shouldBe Some("true")
  }

  it should "apply default for reconnect_delay" in {
    val config = ConnectorConfig(Map("url" -> "http://localhost:8080/events"))
    val result = config.validate(HttpSseSourceConnector.schema)

    result.isRight shouldBe true
    result.toOption.get.getDuration("reconnect_delay") shouldBe Some(3.seconds)
  }

  it should "accept custom optional values" in {
    val config = ConnectorConfig(
      Map(
        "url"             -> "http://localhost:8080/events",
        "reconnect"       -> "false",
        "reconnect_delay" -> "10 seconds",
        "last_event_id"   -> "42",
        "headers"         -> "Authorization=Bearer token,X-Custom=value"
      )
    )
    val result = config.validate(HttpSseSourceConnector.schema)

    result.isRight shouldBe true
    val validated = result.toOption.get
    validated.getString("reconnect") shouldBe Some("false")
    validated.getDuration("reconnect_delay") shouldBe Some(10.seconds)
    validated.getString("last_event_id") shouldBe Some("42")
    validated.getString("headers") shouldBe Some("Authorization=Bearer token,X-Custom=value")
  }

  it should "reject invalid reconnect enum value" in {
    val config = ConnectorConfig(
      Map(
        "url"       -> "http://localhost:8080/events",
        "reconnect" -> "maybe"
      )
    )
    val result = config.validate(HttpSseSourceConnector.schema)

    result.isLeft shouldBe true
  }

  "HttpSseSourceConnector" should "have correct typeName" in {
    val connector = new HttpSseSourceConnector("test")
    connector.typeName shouldBe "http-sse"
    connector.name shouldBe "test"
  }

  it should "default to healthy" in {
    import cats.effect.unsafe.implicits.global
    val connector = new HttpSseSourceConnector("test")
    connector.isHealthy.unsafeRunSync() shouldBe true
  }
}
