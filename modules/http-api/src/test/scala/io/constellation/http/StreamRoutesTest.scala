package io.constellation.http

import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.http.StreamApiModels.*
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.stream.connector.{ConnectorRegistry, MemoryConnector}

import io.circe.Json
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.implicits.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StreamRoutesTest extends AnyFlatSpec with Matchers {

  private def createRoutes(
      registry: ConnectorRegistry = ConnectorRegistry.empty
  ): (StreamRoutes, StreamLifecycleManager) = {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val compiler      = LangCompiler.empty
    val manager       = StreamLifecycleManager.create.unsafeRunSync()
    val routes        = new StreamRoutes(manager, registry, constellation, compiler)
    (routes, manager)
  }

  // ===== List Streams =====

  "GET /api/v1/streams" should "return empty list when no streams deployed" in {
    val (sr, _) = createRoutes()

    val request  = Request[IO](Method.GET, uri"/api/v1/streams")
    val response = sr.routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[StreamListResponse].unsafeRunSync()
    body.streams shouldBe empty
  }

  // ===== Get Stream =====

  "GET /api/v1/streams/:id" should "return 404 for unknown stream" in {
    val (sr, _) = createRoutes()

    val request  = Request[IO](Method.GET, uri"/api/v1/streams/unknown")
    val response = sr.routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
  }

  it should "return stream info after deploy" in {
    val (sr, mgr) = createRoutes()

    // Deploy a stream directly via manager
    val graph = (for {
      metrics  <- io.constellation.stream.StreamMetrics.create
      shutdown <- cats.effect.std.Queue.bounded[IO, Unit](1)
    } yield io.constellation.stream.StreamGraph(
      stream = fs2.Stream.eval(shutdown.take) >> fs2.Stream.empty,
      metrics = metrics,
      shutdown = shutdown.offer(()).void
    )).unsafeRunSync()

    mgr.deploy("s1", "test-stream", graph).unsafeRunSync()

    val request  = Request[IO](Method.GET, uri"/api/v1/streams/s1")
    val response = sr.routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[StreamInfoResponse].unsafeRunSync()
    body.id shouldBe "s1"
    body.name shouldBe "test-stream"
    body.status shouldBe "running"
  }

  // ===== Stop Stream =====

  "DELETE /api/v1/streams/:id" should "return 404 for unknown stream" in {
    val (sr, _) = createRoutes()

    val request  = Request[IO](Method.DELETE, uri"/api/v1/streams/unknown")
    val response = sr.routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
  }

  it should "stop a running stream" in {
    val (sr, mgr) = createRoutes()

    // Deploy
    val graph = (for {
      metrics  <- io.constellation.stream.StreamMetrics.create
      shutdown <- cats.effect.std.Queue.bounded[IO, Unit](1)
    } yield io.constellation.stream.StreamGraph(
      stream = fs2.Stream.eval(shutdown.take) >> fs2.Stream.empty,
      metrics = metrics,
      shutdown = shutdown.offer(()).void
    )).unsafeRunSync()

    mgr.deploy("s1", "test-stream", graph).unsafeRunSync()

    val request  = Request[IO](Method.DELETE, uri"/api/v1/streams/s1")
    val response = sr.routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[Json].unsafeRunSync()
    body.hcursor.downField("status").as[String] shouldBe Right("stopped")
  }

  // ===== Metrics =====

  "GET /api/v1/streams/:id/metrics" should "return 404 for unknown stream" in {
    val (sr, _) = createRoutes()

    val request  = Request[IO](Method.GET, uri"/api/v1/streams/unknown/metrics")
    val response = sr.routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
  }

  it should "return metrics for a deployed stream" in {
    val (sr, mgr) = createRoutes()

    val graph = (for {
      metrics  <- io.constellation.stream.StreamMetrics.create
      shutdown <- cats.effect.std.Queue.bounded[IO, Unit](1)
    } yield io.constellation.stream.StreamGraph(
      stream = fs2.Stream.eval(shutdown.take) >> fs2.Stream.empty,
      metrics = metrics,
      shutdown = shutdown.offer(()).void
    )).unsafeRunSync()

    mgr.deploy("s1", "test-stream", graph).unsafeRunSync()

    val request  = Request[IO](Method.GET, uri"/api/v1/streams/s1/metrics")
    val response = sr.routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[StreamMetricsSummary].unsafeRunSync()
    body.totalElements shouldBe 0L
    body.totalErrors shouldBe 0L
  }

  // ===== Connectors =====

  "GET /api/v1/connectors" should "return empty list when no connectors registered" in {
    val (sr, _) = createRoutes()

    val request  = Request[IO](Method.GET, uri"/api/v1/connectors")
    val response = sr.routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[ConnectorListResponse].unsafeRunSync()
    body.connectors shouldBe empty
  }

  it should "list registered connectors" in {
    val (srcQ, snkQ, src, snk) =
      io.constellation.stream.connector.MemoryConnector.pair("test").unsafeRunSync()

    val registry = ConnectorRegistry.builder
      .source("test-src", src)
      .sink("test-snk", snk)
      .build

    val (sr, _) = createRoutes(registry)

    val request  = Request[IO](Method.GET, uri"/api/v1/connectors")
    val response = sr.routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[ConnectorListResponse].unsafeRunSync()
    body.connectors should have size 2
    body.connectors.map(_.kind).toSet shouldBe Set("source", "sink")
  }

  // ===== Deploy Stream (error cases) =====

  "POST /api/v1/streams" should "return error for unknown pipeline ref" in {
    val (sr, _) = createRoutes()

    val deployReq = StreamDeployRequest(
      name = "test-stream",
      pipelineRef = "nonexistent"
    )

    val request = Request[IO](Method.POST, uri"/api/v1/streams")
      .withEntity(deployReq)
    val response = sr.routes.orNotFound.run(request).unsafeRunSync()

    // Should fail because pipeline doesn't exist
    response.status shouldBe Status.BadRequest
  }
}
