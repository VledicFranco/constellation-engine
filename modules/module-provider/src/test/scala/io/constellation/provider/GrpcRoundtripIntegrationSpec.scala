package io.constellation.provider

import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.*
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.lang.ast.CompileError
import io.constellation.lang.semantic.{FunctionRegistry, InMemoryFunctionRegistry}
import io.constellation.provider.sdk.*
import io.constellation.provider.v1.provider as pb

import io.grpc.{ManagedChannel, ManagedChannelBuilder, ServerBuilder}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Full SDK-to-Server-to-SDK-Executor roundtrip over real gRPC.
  *
  *   1. Start a ModuleProviderManager (server gRPC on port 0) 2. Start a GrpcExecutorServerFactory
  *      (SDK executor gRPC on port 0) 3. SDK registers a module via GrpcProviderTransport 4. Verify
  *      module appears in server's registry 5. Call the SDK executor directly to verify
  *      serialization roundtrip
  */
class GrpcRoundtripIntegrationSpec extends AnyFlatSpec with Matchers {

  private val serializer: CValueSerializer = JsonCValueSerializer

  private val stringSchema = pb.TypeSchema(
    pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.STRING))
  )

  /** Create a test manager with its own gRPC server on port 0. Returns (manager, server, port). */
  private def createManagerWithGrpc(): (ModuleProviderManager, io.grpc.Server, Int) = {
    val testFunctionRegistry = new InMemoryFunctionRegistry
    val constellation        = ConstellationImpl.init.unsafeRunSync()

    val compiler = new LangCompiler {
      def compile(source: String, dagName: String) = Left(
        List(CompileError.UndefinedFunction("test", None))
      )
      def compileToIR(source: String, dagName: String) = Left(
        List(CompileError.UndefinedFunction("test", None))
      )
      def functionRegistry: FunctionRegistry = testFunctionRegistry
    }

    val config = ProviderManagerConfig(grpcPort = 0, reservedNamespaces = Set("stdlib"))
    val state  = cats.effect.Ref.of[IO, Map[String, ProviderConnection]](Map.empty).unsafeRunSync()
    val cp     = new ControlPlaneManager(state, config, _ => IO.unit)
    val cache  = new GrpcChannelCache
    val manager =
      new ModuleProviderManager(constellation, compiler, config, cp, JsonCValueSerializer, cache)

    // Start gRPC server hosting the ModuleProvider service
    val serviceImpl = new ModuleProviderServiceImplForTest(manager)
    val serviceDef =
      pb.ModuleProviderGrpc.bindService(serviceImpl, scala.concurrent.ExecutionContext.global)
    val server = ServerBuilder.forPort(0).addService(serviceDef).build().start()

    (manager, server, server.getPort)
  }

  /** Start a real SDK executor server that handles execute requests via ModuleExecutorServer. */
  private def startExecutorServer(
      handler: CValue => IO[CValue]
  ): (io.grpc.Server, Int) = {
    val moduleDef = ModuleDefinition(
      "echo",
      CType.CString,
      CType.CString,
      "1.0.0",
      "Echo module",
      handler
    )
    val modulesRef = cats.effect.Ref.of[IO, List[ModuleDefinition]](List(moduleDef)).unsafeRunSync()
    val execServer = new ModuleExecutorServer(modulesRef, serializer)
    val impl       = new TestModuleExecutorImpl(execServer.toHandler)
    val serviceDef =
      pb.ModuleExecutorGrpc.bindService(impl, scala.concurrent.ExecutionContext.global)
    val server = ServerBuilder.forPort(0).addService(serviceDef).build().start()
    (server, server.getPort)
  }

  // ===== Register module and verify in registry =====

  "gRPC roundtrip" should "register module via SDK transport and verify in server registry" in {
    val (manager, providerServer, providerPort) = createManagerWithGrpc()
    val channel = ManagedChannelBuilder.forAddress("localhost", providerPort).usePlaintext().build()
    val transport = new GrpcProviderTransport(channel)

    try {
      val req = pb.RegisterRequest(
        namespace = "roundtrip.test",
        modules = List(
          pb.ModuleDeclaration(
            name = "Echo",
            inputSchema = Some(
              pb.TypeSchema(pb.TypeSchema.Type.Record(pb.RecordType(Map("text" -> stringSchema))))
            ),
            outputSchema = Some(
              pb.TypeSchema(pb.TypeSchema.Type.Record(pb.RecordType(Map("result" -> stringSchema))))
            ),
            version = "1.0.0",
            description = "Test echo module"
          )
        ),
        protocolVersion = 1,
        executorUrl = "localhost:9999"
      )

      val resp = transport.register(req).unsafeRunSync()

      resp.success shouldBe true
      resp.results should have size 1
      resp.results.head.accepted shouldBe true

      // Verify module appears in Constellation's module registry
      val modules = manager.getModules.unsafeRunSync()
      modules.map(_.name) should contain("roundtrip.test.Echo")
    } finally {
      channel.shutdownNow()
      providerServer.shutdownNow()
    }
  }

  // ===== Execute through SDK executor and verify output =====

  it should "execute through SDK executor and verify serialization roundtrip" in {
    val echoHandler: CValue => IO[CValue] = { input =>
      IO.pure(input match {
        case CValue.CString(s) => CValue.CString(s"processed:$s")
        case other             => other
      })
    }

    val (executorServer, executorPort) = startExecutorServer(echoHandler)

    try {
      // Create a gRPC client to the executor (simulating what the server would do via callExecutor)
      val channel =
        ManagedChannelBuilder.forAddress("localhost", executorPort).usePlaintext().build()
      try {
        val stub = pb.ModuleExecutorGrpc.blockingStub(channel)

        val inputBytes = serializer.serialize(CValue.CString("hello")).toOption.get
        val resp = stub.execute(
          pb.ExecuteRequest(
            moduleName = "echo",
            inputData = com.google.protobuf.ByteString.copyFrom(inputBytes),
            executionId = "roundtrip-1"
          )
        )

        resp.result.isOutputData shouldBe true
        val output = serializer.deserialize(resp.result.outputData.get.toByteArray)
        output shouldBe Right(CValue.CString("processed:hello"))
      } finally channel.shutdownNow()
    } finally executorServer.shutdownNow()
  }

  // ===== CProduct input/output roundtrip =====

  it should "handle CProduct input/output serialization roundtrip over gRPC" in {
    val productHandler: CValue => IO[CValue] = { input =>
      // Extract "text" field, uppercase it, return as "result"
      IO.pure(input match {
        case CValue.CProduct(fields, _) =>
          val text = fields.getOrElse("text", CValue.CString("")).asInstanceOf[CValue.CString].value
          CValue.CProduct(
            Map("result" -> CValue.CString(text.toUpperCase)),
            Map("result" -> CType.CString)
          )
        case other => other
      })
    }

    // Start executor with a "transform" module
    val moduleDef = ModuleDefinition(
      "transform",
      CType.CProduct(Map("text" -> CType.CString)),
      CType.CProduct(Map("result" -> CType.CString)),
      "1.0.0",
      "Transform module",
      productHandler
    )
    val modulesRef = cats.effect.Ref.of[IO, List[ModuleDefinition]](List(moduleDef)).unsafeRunSync()
    val execServer = new ModuleExecutorServer(modulesRef, serializer)
    val impl       = new TestModuleExecutorImpl(execServer.toHandler)
    val serviceDef =
      pb.ModuleExecutorGrpc.bindService(impl, scala.concurrent.ExecutionContext.global)
    val server = ServerBuilder.forPort(0).addService(serviceDef).build().start()

    try {
      val channel =
        ManagedChannelBuilder.forAddress("localhost", server.getPort).usePlaintext().build()
      try {
        val stub = pb.ModuleExecutorGrpc.blockingStub(channel)

        val input = CValue.CProduct(
          Map("text" -> CValue.CString("hello world")),
          Map("text" -> CType.CString)
        )
        val inputBytes = serializer.serialize(input).toOption.get
        val resp = stub.execute(
          pb.ExecuteRequest(
            moduleName = "transform",
            inputData = com.google.protobuf.ByteString.copyFrom(inputBytes),
            executionId = "roundtrip-2"
          )
        )

        resp.result.isOutputData shouldBe true
        val output = serializer.deserialize(resp.result.outputData.get.toByteArray).toOption.get
        output match {
          case CValue.CProduct(fields, _) =>
            fields("result") shouldBe CValue.CString("HELLO WORLD")
          case other =>
            fail(s"Expected CProduct, got $other")
        }
      } finally channel.shutdownNow()
    } finally server.shutdownNow()
  }

  // ===== Deregister and verify cleanup =====

  it should "deregister module and verify cleanup" in {
    val (manager, providerServer, providerPort) = createManagerWithGrpc()
    val channel = ManagedChannelBuilder.forAddress("localhost", providerPort).usePlaintext().build()
    val transport = new GrpcProviderTransport(channel)

    try {
      // First register
      val regReq = pb.RegisterRequest(
        namespace = "cleanup.test",
        modules = List(
          pb.ModuleDeclaration(
            name = "TempModule",
            inputSchema = Some(
              pb.TypeSchema(pb.TypeSchema.Type.Record(pb.RecordType(Map("x" -> stringSchema))))
            ),
            outputSchema = Some(
              pb.TypeSchema(pb.TypeSchema.Type.Record(pb.RecordType(Map("y" -> stringSchema))))
            ),
            version = "1.0.0",
            description = "Temp"
          )
        ),
        protocolVersion = 1,
        executorUrl = "localhost:9999"
      )

      val regResp = transport.register(regReq).unsafeRunSync()
      regResp.success shouldBe true
      val connectionId = regResp.connectionId

      // Verify module is registered
      manager.getModules.unsafeRunSync().map(_.name) should contain("cleanup.test.TempModule")

      // Deregister
      val deregReq = pb.DeregisterRequest(
        namespace = "cleanup.test",
        moduleNames = List("TempModule"),
        connectionId = connectionId
      )
      val deregResp = transport.deregister(deregReq).unsafeRunSync()

      deregResp.success shouldBe true
      deregResp.results should have size 1
      deregResp.results.head.removed shouldBe true

      // Verify module is removed
      manager.getModules.unsafeRunSync().map(_.name) should not contain "cleanup.test.TempModule"
    } finally {
      channel.shutdownNow()
      providerServer.shutdownNow()
    }
  }
}

/** Test-accessible wrapper for ModuleProviderServiceImpl (which is private to the manager file). */
private class ModuleProviderServiceImplForTest(manager: ModuleProviderManager)
    extends pb.ModuleProviderGrpc.ModuleProvider {

  import cats.effect.unsafe.implicits.global

  override def register(
      request: pb.RegisterRequest
  ): scala.concurrent.Future[pb.RegisterResponse] = {
    val connectionId = UUID.randomUUID().toString
    val promise      = scala.concurrent.Promise[pb.RegisterResponse]()
    manager.handleRegister(request, connectionId).unsafeRunAsync {
      case Right(value) => promise.success(value)
      case Left(error)  => promise.failure(error)
    }
    promise.future
  }

  override def deregister(
      request: pb.DeregisterRequest
  ): scala.concurrent.Future[pb.DeregisterResponse] = {
    val promise = scala.concurrent.Promise[pb.DeregisterResponse]()
    val io =
      if request.connectionId.nonEmpty then manager.handleDeregister(request, request.connectionId)
      else
        manager.controlPlane.getConnectionByNamespace(request.namespace).flatMap {
          case Some(conn) => manager.handleDeregister(request, conn.connectionId)
          case None       => IO.pure(pb.DeregisterResponse(success = false))
        }
    io.unsafeRunAsync {
      case Right(value) => promise.success(value)
      case Left(error)  => promise.failure(error)
    }
    promise.future
  }

  override def controlPlane(
      responseObserver: io.grpc.stub.StreamObserver[pb.ControlMessage]
  ): io.grpc.stub.StreamObserver[pb.ControlMessage] =
    // Minimal control plane for integration tests
    new io.grpc.stub.StreamObserver[pb.ControlMessage] {
      override def onNext(msg: pb.ControlMessage): Unit = ()
      override def onError(t: Throwable): Unit          = ()
      override def onCompleted(): Unit                  = responseObserver.onCompleted()
    }
}

/** Test-only ModuleExecutor gRPC implementation (mirrors private GrpcModuleExecutorImpl from SDK).
  */
private class TestModuleExecutorImpl(handler: pb.ExecuteRequest => IO[pb.ExecuteResponse])
    extends pb.ModuleExecutorGrpc.ModuleExecutor {

  import cats.effect.unsafe.implicits.global

  override def execute(request: pb.ExecuteRequest): scala.concurrent.Future[pb.ExecuteResponse] = {
    val promise = scala.concurrent.Promise[pb.ExecuteResponse]()
    handler(request).unsafeRunAsync {
      case Right(response) => promise.success(response)
      case Left(error) =>
        promise.success(
          pb.ExecuteResponse(
            result = pb.ExecuteResponse.Result.Error(
              pb.ExecutionError(code = "RUNTIME_ERROR", message = error.getMessage)
            )
          )
        )
    }
    promise.future
  }
}
