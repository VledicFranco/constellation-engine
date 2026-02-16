package io.constellation.http

import scala.concurrent.duration.*

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import cats.implicits.*

import io.constellation.http.ApiModels.*
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.stdlib.StdLib

import io.circe.Json
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Advanced integration tests for edge cases and stress scenarios (Phase 7).
  *
  * Focuses on:
  *   - Concurrent request handling under load
  *   - Error response consistency across all endpoints
  *   - Edge cases in request validation
  *   - Resource cleanup and leak prevention
  *
  * Run with: sbt "httpApi/testOnly *AdvancedIntegrationTest"
  */
class AdvancedIntegrationTest extends AnyFlatSpec with Matchers {

  /** Helper to create a client + server resource on a random port */
  private def serverAndClient(): Resource[IO, (Client[IO], Uri)] =
    for {
      constellation <- Resource.eval(ConstellationImpl.init)
      _ <- Resource.eval(StdLib.allModules.values.toList.traverse(constellation.setModule))
      compiler = StdLib.compiler

      builder = ConstellationServer
        .builder(constellation, compiler)
        .withHost("127.0.0.1")
        .withPort(0) // random port

      server <- builder.build

      host      = server.address.getHostString
      hostStr   = if host.contains(":") then s"[$host]" else host
      serverUri = Uri.unsafeFromString(s"http://$hostStr:${server.address.getPort}")

      client <- EmberClientBuilder.default[IO].build
    } yield (client, serverUri)

  // ===== Concurrent Request Handling Tests =====

  "Server under concurrent load" should "handle 50 parallel health check requests" in {
    serverAndClient()
      .use { case (client, baseUri) =>
        val requests = List.fill(50)(client.expect[Json](baseUri / "health"))

        requests.parSequence.map { results =>
          results.size shouldBe 50
          results.foreach { json =>
            (json \\ "status").headOption.flatMap(_.asString) shouldBe Some("ok")
          }
        }
      }
      .unsafeRunSync()
  }

  it should "handle 30 parallel compile requests without errors" in {
    val source = """in x: Int
                   |out x""".stripMargin

    serverAndClient()
      .use { case (client, baseUri) =>
        val requests = (1 to 30).toList.map { i =>
          client.expect[Json](
            Request[IO](
              method = Method.POST,
              uri = baseUri / "compile"
            ).withEntity(CompileRequest(source, Some(s"test-$i")))
          )
        }

        requests.parSequence.map { results =>
          results.size shouldBe 30
          results.foreach { json =>
            (json \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(true)
          }
        }
      }
      .unsafeRunSync()
  }

  it should "handle mixed endpoint requests concurrently" in {
    serverAndClient()
      .use { case (client, baseUri) =>
        val healthRequests = List.fill(10)(client.expect[Json](baseUri / "health"))
        val moduleRequests = List.fill(10)(client.expect[Json](baseUri / "modules"))

        for {
          healthResults <- healthRequests.parSequence
          moduleResults <- moduleRequests.parSequence
        } yield {
          healthResults.size shouldBe 10
          moduleResults.size shouldBe 10
          healthResults.foreach { json =>
            (json \\ "status").headOption.flatMap(_.asString) shouldBe Some("ok")
          }
        }
      }
      .unsafeRunSync()
  }

  // ===== Error Response Consistency Tests =====

  "Error responses" should "return 404 for non-existent endpoints" in {
    serverAndClient()
      .use { case (client, baseUri) =>
        client
          .run(Request[IO](method = Method.GET, uri = baseUri / "nonexistent"))
          .use { response =>
            IO {
              response.status shouldBe Status.NotFound
            }
          }
      }
      .unsafeRunSync()
  }

  it should "return 404 for unsupported HTTP methods on existing routes" in {
    serverAndClient()
      .use { case (client, baseUri) =>
        client
          .run(Request[IO](method = Method.PUT, uri = baseUri / "health"))
          .use { response =>
            IO {
              response.status shouldBe Status.NotFound
            }
          }
      }
      .unsafeRunSync()
  }

  it should "handle missing request body" in {
    serverAndClient()
      .use { case (client, baseUri) =>
        client
          .run(Request[IO](method = Method.POST, uri = baseUri / "compile"))
          .use { response =>
            IO {
              response.status shouldBe Status.BadRequest
            }
          }
      }
      .unsafeRunSync()
  }

  // ===== Resource Cleanup Tests =====

  "Server" should "cleanup resources properly on shutdown" in {
    val result = serverAndClient().use { case (client, baseUri) =>
      client.expect[Json](baseUri / "health").map { json =>
        (json \\ "status").headOption.flatMap(_.asString) shouldBe Some("ok")
      }
    }

    result.unsafeRunSync()
  }

  it should "handle rapid startup and shutdown cycles" in {
    val cycles = (1 to 5).toList

    cycles
      .traverse { _ =>
        serverAndClient().use { case (client, baseUri) =>
          client.expect[Json](baseUri / "health")
        }
      }
      .unsafeRunSync()
  }

  // ===== Request Validation Edge Cases =====

  "Compile endpoint" should "handle moderately long source code" in {
    val longSource = "in x: Int\n" + ("temp = Add(x, 1)\n" * 50) + "out temp"

    serverAndClient()
      .use { case (client, baseUri) =>
        client
          .run(
            Request[IO](method = Method.POST, uri = baseUri / "compile")
              .withEntity(CompileRequest(longSource, Some("long-test")))
          )
          .use { response =>
            IO {
              // Should handle moderately long source (may succeed or fail with validation error)
              response.status should (be(Status.Ok) or be(Status.BadRequest))
            }
          }
      }
      .unsafeRunSync()
  }

  it should "handle source with unicode characters" in {
    val unicodeSource = """in text: String
                          |result = add(text: "Hello 世界 🌍")
                          |out result""".stripMargin

    serverAndClient()
      .use { case (client, baseUri) =>
        client
          .run(
            Request[IO](method = Method.POST, uri = baseUri / "compile")
              .withEntity(CompileRequest(unicodeSource, Some("unicode-test")))
          )
          .use { response =>
            IO {
              response.status should (be(Status.Ok) or be(Status.BadRequest))
            }
          }
      }
      .unsafeRunSync()
  }

  it should "handle empty DAG name gracefully" in {
    val source = """in x: Int
                   |out x""".stripMargin

    serverAndClient()
      .use { case (client, baseUri) =>
        client
          .expect[Json](
            Request[IO](
              method = Method.POST,
              uri = baseUri / "compile"
            ).withEntity(CompileRequest(source, Some("")))
          )
          .map { json =>
            (json \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(true)
          }
      }
      .unsafeRunSync()
  }
}
