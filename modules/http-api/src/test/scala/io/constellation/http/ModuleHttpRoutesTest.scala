package io.constellation.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*

import io.constellation.http.ApiModels.*
import io.constellation.impl.ConstellationImpl
import io.constellation.{ModuleBuilder, ModuleHttpConfig}

import io.circe.Json
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.implicits.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ModuleHttpRoutesTest extends AnyFlatSpec with Matchers {

  // ===== Test module definitions =====

  case class TextInput(text: String)
  case class TextOutput(result: String)

  case class MathInput(x: Long, y: Long)
  case class MathOutput(sum: Long)

  private val uppercaseModule = ModuleBuilder
    .metadata("Uppercase", "Converts text to uppercase", 1, 0)
    .httpEndpoint()
    .implementationPure[TextInput, TextOutput](in => TextOutput(in.text.toUpperCase))
    .build

  private val addModule = ModuleBuilder
    .metadata("Add", "Adds two numbers", 1, 0)
    .tags("math")
    .httpEndpoint()
    .implementationPure[MathInput, MathOutput](in => MathOutput(in.x + in.y))
    .build

  private val unpublishedModule = ModuleBuilder
    .metadata("Secret", "Not published", 1, 0)
    .implementationPure[TextInput, TextOutput](in => TextOutput(in.text))
    .build

  private val disabledEndpointModule = ModuleBuilder
    .metadata("Disabled", "Endpoint disabled", 1, 0)
    .httpEndpoint(ModuleHttpConfig(published = false))
    .implementationPure[TextInput, TextOutput](in => TextOutput(in.text))
    .build

  // ===== Test setup helper =====

  private def setup(
      modules: List[io.constellation.Module.Uninitialized] = List.empty
  ): HttpRoutes[IO] = {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    modules.foreach(m => constellation.setModule(m).unsafeRunSync())
    new ModuleHttpRoutes(constellation).routes
  }

  // ===== GET /modules/published =====

  "GET /modules/published" should "return empty list when no published modules" in {
    val routes   = setup()
    val request  = Request[IO](Method.GET, uri"/modules/published")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[PublishedModulesResponse].unsafeRunSync()
    body.modules shouldBe empty
  }

  it should "return only published modules (not unpublished)" in {
    val routes   = setup(List(uppercaseModule, unpublishedModule))
    val request  = Request[IO](Method.GET, uri"/modules/published")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[PublishedModulesResponse].unsafeRunSync()
    body.modules should have length 1
    body.modules.head.name shouldBe "Uppercase"
    body.modules.head.endpoint shouldBe "/modules/Uppercase/invoke"
  }

  it should "exclude modules with published=false" in {
    val routes   = setup(List(uppercaseModule, disabledEndpointModule))
    val request  = Request[IO](Method.GET, uri"/modules/published")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[PublishedModulesResponse].unsafeRunSync()
    body.modules should have length 1
    body.modules.head.name shouldBe "Uppercase"
  }

  it should "return multiple published modules" in {
    val routes   = setup(List(uppercaseModule, addModule))
    val request  = Request[IO](Method.GET, uri"/modules/published")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[PublishedModulesResponse].unsafeRunSync()
    body.modules should have length 2
    body.modules.map(_.name).toSet shouldBe Set("Uppercase", "Add")
  }

  it should "include input and output schema in module info" in {
    val routes   = setup(List(addModule))
    val request  = Request[IO](Method.GET, uri"/modules/published")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    val body = response.as[PublishedModulesResponse].unsafeRunSync()
    val info = body.modules.head
    info.inputs should contain key "x"
    info.inputs should contain key "y"
    info.outputs should contain key "sum"
    info.tags shouldBe List("math")
    info.version shouldBe "1.0"
  }

  // ===== POST /modules/:name/invoke =====

  "POST /modules/:name/invoke" should "invoke a published module with correct inputs" in {
    val routes = setup(List(uppercaseModule))
    val request = Request[IO](Method.POST, uri"/modules/Uppercase/invoke")
      .withEntity(Json.obj("text" -> Json.fromString("hello")))
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[ModuleInvokeResponse].unsafeRunSync()
    body.success shouldBe true
    body.module shouldBe Some("Uppercase")
    body.outputs("result") shouldBe Json.fromString("HELLO")
  }

  it should "invoke a module with multiple inputs" in {
    val routes = setup(List(addModule))
    val request = Request[IO](Method.POST, uri"/modules/Add/invoke")
      .withEntity(Json.obj("x" -> Json.fromInt(3), "y" -> Json.fromInt(7)))
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[ModuleInvokeResponse].unsafeRunSync()
    body.success shouldBe true
    body.outputs("sum") shouldBe Json.fromInt(10)
  }

  it should "return 404 for unknown module" in {
    val routes = setup(List(uppercaseModule))
    val request = Request[IO](Method.POST, uri"/modules/Unknown/invoke")
      .withEntity(Json.obj("text" -> Json.fromString("test")))
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
  }

  it should "return 404 for non-published module" in {
    val routes = setup(List(unpublishedModule))
    val request = Request[IO](Method.POST, uri"/modules/Secret/invoke")
      .withEntity(Json.obj("text" -> Json.fromString("test")))
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
  }

  it should "return 404 for module with published=false" in {
    val routes = setup(List(disabledEndpointModule))
    val request = Request[IO](Method.POST, uri"/modules/Disabled/invoke")
      .withEntity(Json.obj("text" -> Json.fromString("test")))
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
  }

  it should "return 400 for wrong input type" in {
    val routes = setup(List(addModule))
    val request = Request[IO](Method.POST, uri"/modules/Add/invoke")
      .withEntity(Json.obj("x" -> Json.fromString("not-a-number"), "y" -> Json.fromInt(5)))
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.BadRequest
    val body = response.as[ModuleInvokeResponse].unsafeRunSync()
    body.success shouldBe false
    body.error shouldBe defined
  }

  it should "return 400 for missing required field" in {
    val routes = setup(List(addModule))
    val request = Request[IO](Method.POST, uri"/modules/Add/invoke")
      .withEntity(Json.obj("x" -> Json.fromInt(5)))
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.BadRequest
    val body = response.as[ModuleInvokeResponse].unsafeRunSync()
    body.success shouldBe false
    body.error.get should include("y")
  }

  it should "return 400 for non-object body" in {
    val routes = setup(List(uppercaseModule))
    val request = Request[IO](Method.POST, uri"/modules/Uppercase/invoke")
      .withEntity(Json.fromString("not an object"))
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.BadRequest
    val body = response.as[ModuleInvokeResponse].unsafeRunSync()
    body.success shouldBe false
    body.error.get should include("JSON object")
  }
}
