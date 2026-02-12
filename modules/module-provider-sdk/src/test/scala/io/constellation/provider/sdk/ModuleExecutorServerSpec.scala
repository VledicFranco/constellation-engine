package io.constellation.provider.sdk

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global

import io.constellation.{CType, CValue}
import io.constellation.provider.{CValueSerializer, JsonCValueSerializer}
import io.constellation.provider.v1.provider as pb

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ModuleExecutorServerSpec extends AnyFlatSpec with Matchers {

  private val serializer: CValueSerializer = JsonCValueSerializer

  private def upperHandler(input: CValue): IO[CValue] = IO {
    input match {
      case CValue.CString(s) => CValue.CString(s.toUpperCase)
      case other             => other
    }
  }

  private val modules = List(
    ModuleDefinition("upper", CType.CString, CType.CString, "1.0.0", "Uppercase", upperHandler),
    ModuleDefinition("echo", CType.CInt, CType.CInt, "1.0.0", "Echo", v => IO.pure(v))
  )

  private def createServer(
      mods: List[ModuleDefinition] = modules
  ): (ModuleExecutorServer, Ref[IO, List[ModuleDefinition]]) = {
    val ref = Ref.of[IO, List[ModuleDefinition]](mods).unsafeRunSync()
    (new ModuleExecutorServer(ref, serializer), ref)
  }

  private def mkRequest(moduleName: String, input: CValue): pb.ExecuteRequest = {
    val bytes = serializer.serialize(input).toOption.get
    pb.ExecuteRequest(
      moduleName = moduleName,
      inputData = com.google.protobuf.ByteString.copyFrom(bytes),
      executionId = "exec-1"
    )
  }

  // ===== Correct dispatch =====

  "ModuleExecutorServer" should "dispatch to correct module handler" in {
    val (server, _) = createServer()

    val req  = mkRequest("upper", CValue.CString("hello"))
    val resp = server.handleRequest(req).unsafeRunSync()

    resp.result.isOutputData shouldBe true
    val output = serializer.deserialize(resp.result.outputData.get.toByteArray)
    output shouldBe Right(CValue.CString("HELLO"))
  }

  it should "dispatch echo module correctly" in {
    val (server, _) = createServer()

    val req  = mkRequest("echo", CValue.CInt(42))
    val resp = server.handleRequest(req).unsafeRunSync()

    resp.result.isOutputData shouldBe true
    val output = serializer.deserialize(resp.result.outputData.get.toByteArray)
    output shouldBe Right(CValue.CInt(42))
  }

  // ===== Unknown module =====

  it should "return MODULE_NOT_FOUND for unknown module" in {
    val (server, _) = createServer()

    val req  = mkRequest("nonexistent", CValue.CString("test"))
    val resp = server.handleRequest(req).unsafeRunSync()

    resp.result.isError shouldBe true
    resp.result.error.get.code shouldBe "MODULE_NOT_FOUND"
    resp.result.error.get.message should include("nonexistent")
  }

  // ===== Deserialization error =====

  it should "return TYPE_ERROR for invalid input data" in {
    val (server, _) = createServer()

    val req = pb.ExecuteRequest(
      moduleName = "upper",
      inputData = com.google.protobuf.ByteString.copyFromUtf8("not valid json!!!"),
      executionId = "exec-2"
    )
    val resp = server.handleRequest(req).unsafeRunSync()

    resp.result.isError shouldBe true
    resp.result.error.get.code shouldBe "TYPE_ERROR"
  }

  // ===== Handler exception =====

  it should "return RUNTIME_ERROR when handler throws" in {
    val failingModule = ModuleDefinition(
      "fail",
      CType.CString,
      CType.CString,
      "1.0.0",
      "Fails",
      _ => IO.raiseError(new RuntimeException("handler boom"))
    )
    val (server, _) = createServer(List(failingModule))

    val req  = mkRequest("fail", CValue.CString("test"))
    val resp = server.handleRequest(req).unsafeRunSync()

    resp.result.isError shouldBe true
    resp.result.error.get.code shouldBe "RUNTIME_ERROR"
    resp.result.error.get.message should include("handler boom")
  }

  // ===== Metrics populated =====

  it should "populate execution metrics" in {
    val (server, _) = createServer()

    val req  = mkRequest("upper", CValue.CString("test"))
    val resp = server.handleRequest(req).unsafeRunSync()

    resp.metrics shouldBe defined
    resp.metrics.get.durationMs should be >= 0L
  }

  // ===== Module hot-swap =====

  it should "reflect module changes via Ref" in {
    val (server, ref) = createServer()

    // Initially, "upper" exists
    val resp1 = server.handleRequest(mkRequest("upper", CValue.CString("hi"))).unsafeRunSync()
    resp1.result.isOutputData shouldBe true

    // Remove all modules
    ref.set(List.empty).unsafeRunSync()

    val resp2 = server.handleRequest(mkRequest("upper", CValue.CString("hi"))).unsafeRunSync()
    resp2.result.isError shouldBe true
    resp2.result.error.get.code shouldBe "MODULE_NOT_FOUND"
  }

  // ===== Empty input data =====

  it should "return TYPE_ERROR for empty input data" in {
    val (server, _) = createServer()

    val req = pb.ExecuteRequest(
      moduleName = "upper",
      inputData = com.google.protobuf.ByteString.EMPTY,
      executionId = "exec-3"
    )
    val resp = server.handleRequest(req).unsafeRunSync()

    resp.result.isError shouldBe true
    resp.result.error.get.code shouldBe "TYPE_ERROR"
  }
}
