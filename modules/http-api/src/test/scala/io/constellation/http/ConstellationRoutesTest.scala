package io.constellation.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s._
import org.http4s.implicits._
import org.http4s.circe.CirceEntityCodec._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.http.ApiModels._
import io.circe.Json

class ConstellationRoutesTest extends AnyFlatSpec with Matchers {

  // Create test constellation and compiler instances
  val constellation = ConstellationImpl.init.unsafeRunSync()
  val compiler = LangCompiler.empty
  val routes = ConstellationRoutes(constellation, compiler).routes

  "ConstellationRoutes" should "respond to health check" in {
    val request = Request[IO](Method.GET, uri"/health")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[Json].unsafeRunSync()
    body.hcursor.downField("status").as[String] shouldBe Right("ok")
  }

  it should "compile valid constellation-lang programs" in {
    val compileRequest = CompileRequest(
      source = """
        in x: Int
        in y: Int
        out x
      """,
      dagName = "test-dag"
    )

    val request = Request[IO](Method.POST, uri"/compile")
      .withEntity(compileRequest)

    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[CompileResponse].unsafeRunSync()
    body.success shouldBe true
    body.dagName shouldBe Some("test-dag")
    body.errors shouldBe List.empty
  }

  it should "return errors for invalid constellation-lang programs" in {
    val compileRequest = CompileRequest(
      source = """
        in x: Int
        out undefined_variable
      """,
      dagName = "invalid-dag"
    )

    val request = Request[IO](Method.POST, uri"/compile")
      .withEntity(compileRequest)

    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.BadRequest
    val body = response.as[CompileResponse].unsafeRunSync()
    body.success shouldBe false
    body.errors should not be empty
  }

  it should "list available DAGs" in {
    // First compile a DAG
    val compileRequest = CompileRequest(
      source = "in x: Int\nout x",
      dagName = "list-test-dag"
    )

    val compileReq = Request[IO](Method.POST, uri"/compile")
      .withEntity(compileRequest)
    routes.orNotFound.run(compileReq).unsafeRunSync()

    // Then list DAGs
    val listRequest = Request[IO](Method.GET, uri"/dags")
    val response = routes.orNotFound.run(listRequest).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[DagListResponse].unsafeRunSync()
    body.dags.keySet should contain("list-test-dag")
  }

  it should "get a specific DAG by name" in {
    // First compile a DAG
    val compileRequest = CompileRequest(
      source = "in x: Int\nout x",
      dagName = "get-test-dag"
    )

    val compileReq = Request[IO](Method.POST, uri"/compile")
      .withEntity(compileRequest)
    routes.orNotFound.run(compileReq).unsafeRunSync()

    // Then get the DAG
    val getRequest = Request[IO](Method.GET, uri"/dags/get-test-dag")
    val response = routes.orNotFound.run(getRequest).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[DagResponse].unsafeRunSync()
    body.name shouldBe "get-test-dag"
  }

  it should "return 404 for non-existent DAG" in {
    val request = Request[IO](Method.GET, uri"/dags/non-existent")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
    val body = response.as[ErrorResponse].unsafeRunSync()
    body.error shouldBe "DagNotFound"
  }

  it should "list available modules" in {
    val request = Request[IO](Method.GET, uri"/modules")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[ModuleListResponse].unsafeRunSync()
    body.modules shouldBe a[List[_]]
  }
}
