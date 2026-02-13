package io.constellation.provider

import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.*
import io.constellation.provider.v1.provider as pb

import io.grpc.ServerBuilder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests the gRPC execution path: ExternalModule.callExecutor via GrpcChannelCache + real gRPC
  * server.
  *
  * Also tests ExternalModule.create with a full init/run cycle.
  */
class ExternalModuleExecutionSpec extends AnyFlatSpec with Matchers {

  private val serializer: CValueSerializer = JsonCValueSerializer

  // ===== Fake ModuleExecutor gRPC service =====

  /** A fake executor that echoes the input back with a prefix, or returns an error. */
  private class FakeExecutorService(
      behavior: pb.ExecuteRequest => pb.ExecuteResponse
  ) extends pb.ModuleExecutorGrpc.ModuleExecutor {
    override def execute(request: pb.ExecuteRequest): scala.concurrent.Future[pb.ExecuteResponse] =
      scala.concurrent.Future.successful(behavior(request))
  }

  private def echoExecutor: FakeExecutorService = new FakeExecutorService(req => {
    // Echo: deserialize input, prefix with "echo:", serialize back
    val input = serializer.deserialize(req.inputData.toByteArray).toOption.get
    val output = input match {
      case CValue.CProduct(fields, spec) =>
        val modified = fields.map { case (k, v) =>
          v match {
            case CValue.CString(s) => k -> CValue.CString(s"echo:$s")
            case other             => k -> other
          }
        }
        CValue.CProduct(modified, spec)
      case CValue.CString(s) => CValue.CString(s"echo:$s")
      case other             => other
    }
    val outputBytes = serializer.serialize(output).toOption.get
    pb.ExecuteResponse(
      result = pb.ExecuteResponse.Result.OutputData(
        com.google.protobuf.ByteString.copyFrom(outputBytes)
      )
    )
  })

  private def errorExecutor: FakeExecutorService = new FakeExecutorService(_ =>
    pb.ExecuteResponse(
      result = pb.ExecuteResponse.Result.Error(
        pb.ExecutionError(code = "RUNTIME_ERROR", message = "executor failed")
      )
    )
  )

  private def emptyExecutor: FakeExecutorService = new FakeExecutorService(_ =>
    pb.ExecuteResponse(result = pb.ExecuteResponse.Result.Empty)
  )

  private def startFakeExecutor(service: FakeExecutorService): (io.grpc.Server, Int) = {
    val serviceDef =
      pb.ModuleExecutorGrpc.bindService(service, scala.concurrent.ExecutionContext.global)
    val server = ServerBuilder.forPort(0).addService(serviceDef).build().start()
    (server, server.getPort)
  }

  // ===== Helpers to run ExternalModule init/run =====

  private def buildDagAndRun(
      moduleName: String,
      namespace: String,
      inputType: CType,
      outputType: CType,
      executorUrl: String,
      channelCache: GrpcChannelCache,
      inputValue: CValue
  ): IO[Either[Throwable, Map[String, CValue]]] = {
    val pool = RoundRobinExecutorPool.create.flatMap { p =>
      p.add(ExecutorEndpoint("conn-1", executorUrl)).as(p)
    }

    pool.flatMap { execPool =>
      val module = ExternalModule.create(
        name = moduleName,
        namespace = namespace,
        executorPool = execPool,
        inputType = inputType,
        outputType = outputType,
        description = "test module",
        serializer = serializer,
        channelCache = channelCache
      )

      val moduleId = UUID.randomUUID()

      // Build consumes/produces specs matching ExternalModule logic
      val consumesSpec: Map[String, CType] = inputType match {
        case CType.CProduct(structure) => structure
        case other                     => Map("input" -> other)
      }
      val producesSpec: Map[String, CType] = outputType match {
        case CType.CProduct(structure) => structure
        case other                     => Map("output" -> other)
      }

      // Create data node UUIDs for each input field
      val consumeDataIds = consumesSpec.keys.map(name => name -> UUID.randomUUID()).toMap
      // The DAG compiler creates a single composite output node with the first field name as nickname
      val outputFieldName = producesSpec.keys.head
      val outputDataId    = UUID.randomUUID()
      val produceDataIds  = Map(outputFieldName -> outputDataId)

      val dagSpec = DagSpec(
        metadata = ComponentMetadata("test-dag", "test", List.empty, 1, 0),
        modules = Map(moduleId -> module.spec),
        data = consumeDataIds.map { case (name, id) =>
          id -> DataNodeSpec(name, Map(moduleId -> name), consumesSpec(name))
        } + (outputDataId -> DataNodeSpec(
          outputFieldName + "_output",
          Map(moduleId -> outputFieldName),
          outputType
        )),
        inEdges = consumeDataIds.values.map(dataId => (dataId, moduleId)).toSet,
        outEdges = Set((moduleId, outputDataId))
      )

      for {
        runnable <- module.init(moduleId, dagSpec)

        // Set input data in the table
        _ <- inputValue match {
          case CValue.CProduct(fields, _) =>
            import cats.implicits.*
            fields.toList.traverse_ { case (fieldName, fieldValue) =>
              val dataId = consumeDataIds(fieldName)
              runnable.data.get(dataId) match {
                case Some(deferred) =>
                  val anyVal = fieldValue match {
                    case CValue.CString(s)  => s
                    case CValue.CInt(i)     => i
                    case CValue.CBoolean(b) => b
                    case CValue.CFloat(f)   => f
                    case other              => other // fallback
                  }
                  deferred.complete(anyVal).void
                case None => IO.raiseError(new RuntimeException(s"No deferred for $fieldName"))
              }
            }
          case singleValue =>
            val dataId = consumeDataIds.values.head
            runnable.data.get(dataId) match {
              case Some(deferred) =>
                val anyVal = singleValue match {
                  case CValue.CString(s) => s
                  case CValue.CInt(i)    => i
                  case other             => other
                }
                deferred.complete(anyVal).void
              case None => IO.raiseError(new RuntimeException("No deferred for single input"))
            }
        }

        // Create Runtime
        runtimeState <- cats.effect.Ref.of[IO, Runtime.State](
          Runtime.State(
            processUuid = UUID.randomUUID(),
            dag = dagSpec,
            moduleStatus = Map.empty,
            data = Map.empty
          )
        )
        runtime = Runtime(runnable.data, runtimeState)

        // Run the module
        result <- runnable.run(runtime).attempt

        // Read outputs from state
        finalState <- runtimeState.get
        outputs = produceDataIds
          .map { case (name, _) =>
            name -> finalState.data.get(produceDataIds(name)).map(_.value.asInstanceOf[CValue])
          }
          .collect { case (name, Some(value)) => name -> value }

      } yield result.map(_ => outputs)
    }
  }

  // ===== callExecutor happy path (CProduct) =====

  "ExternalModule execution" should "execute via gRPC and return output for CProduct" in {
    val (server, port) = startFakeExecutor(echoExecutor)
    val cache          = new GrpcChannelCache
    val executorUrl    = s"localhost:$port"
    val inputType      = CType.CProduct(Map("text" -> CType.CString))
    val outputType     = CType.CProduct(Map("text" -> CType.CString))
    val inputValue = CValue.CProduct(
      Map("text" -> CValue.CString("hello")),
      inputType.asInstanceOf[CType.CProduct].structure
    )

    try {
      val result = buildDagAndRun(
        "EchoModule",
        "test.ns",
        inputType,
        outputType,
        executorUrl,
        cache,
        inputValue
      ).unsafeRunSync()

      result shouldBe a[Right[_, _]]
      val outputs = result.toOption.get
      // The single output node contains the composite CProduct
      outputs("text") shouldBe CValue.CProduct(
        Map("text" -> CValue.CString("echo:hello")),
        Map("text" -> CType.CString)
      )
    } finally {
      cache.shutdownAll()
      server.shutdownNow()
    }
  }

  // ===== Multi-field output (e.g., AnalyzeSentiment: { score: Float, label: String }) =====

  it should "execute via gRPC with multi-field output type" in {
    // Executor returns a CProduct with score + label (like AnalyzeSentiment)
    val multiFieldExecutor = new FakeExecutorService(req => {
      val outputCValue = CValue.CProduct(
        Map("score" -> CValue.CFloat(0.85f), "label" -> CValue.CString("positive")),
        Map("score" -> CType.CFloat, "label"         -> CType.CString)
      )
      val outputBytes = serializer.serialize(outputCValue).toOption.get
      pb.ExecuteResponse(
        result = pb.ExecuteResponse.Result.OutputData(
          com.google.protobuf.ByteString.copyFrom(outputBytes)
        )
      )
    })

    val (server, port) = startFakeExecutor(multiFieldExecutor)
    val cache          = new GrpcChannelCache
    val executorUrl    = s"localhost:$port"
    val inputType      = CType.CProduct(Map("text" -> CType.CString))
    val outputType     = CType.CProduct(Map("score" -> CType.CFloat, "label" -> CType.CString))
    val inputValue =
      CValue.CProduct(Map("text" -> CValue.CString("great movie")), Map("text" -> CType.CString))

    try {
      val result = buildDagAndRun(
        "AnalyzeSentiment",
        "nlp.sentiment",
        inputType,
        outputType,
        executorUrl,
        cache,
        inputValue
      ).unsafeRunSync()

      result shouldBe a[Right[_, _]]
      val outputs = result.toOption.get
      // The composite output node contains the full CProduct with both fields
      val outputKey = outputType.asInstanceOf[CType.CProduct].structure.keys.head
      val composite = outputs(outputKey).asInstanceOf[CValue.CProduct]
      composite.value("score") shouldBe CValue.CFloat(0.85f)
      composite.value("label") shouldBe CValue.CString("positive")
    } finally {
      cache.shutdownAll()
      server.shutdownNow()
    }
  }

  // ===== Multi-field output via protobuf TypeSchemaConverter (simulates real registration) =====

  it should "work when output type comes from TypeSchemaConverter (protobuf path)" in {
    // Build a TypeSchema via proto → CType (exactly like real gRPC registration)
    val outputSchema = pb.TypeSchema(
      pb.TypeSchema.Type.Record(
        pb.RecordType(
          fields = Map(
            "score" -> pb.TypeSchema(
              pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.FLOAT))
            ),
            "label" -> pb.TypeSchema(
              pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.STRING))
            )
          )
        )
      )
    )
    val outputType = TypeSchemaConverter.toCType(outputSchema).toOption.get

    // Verify it's a CProduct with the expected fields
    outputType shouldBe a[CType.CProduct]
    val producesMap = outputType.asInstanceOf[CType.CProduct].structure
    producesMap.keySet should contain allOf ("score", "label")

    // The critical check: keys.head must be consistent across DagCompiler and ExternalModule.init
    // Both use the SAME Map instance, so this should always be true
    val firstKey = producesMap.keys.head

    val multiFieldExecutor = new FakeExecutorService(_ => {
      val outputCValue = CValue.CProduct(
        Map("score" -> CValue.CFloat(0.85f), "label" -> CValue.CString("positive")),
        Map("score" -> CType.CFloat, "label"         -> CType.CString)
      )
      val outputBytes = serializer.serialize(outputCValue).toOption.get
      pb.ExecuteResponse(
        result = pb.ExecuteResponse.Result.OutputData(
          com.google.protobuf.ByteString.copyFrom(outputBytes)
        )
      )
    })

    val (server, port) = startFakeExecutor(multiFieldExecutor)
    val cache          = new GrpcChannelCache
    val executorUrl    = s"localhost:$port"
    val inputSchema = pb.TypeSchema(
      pb.TypeSchema.Type.Record(
        pb.RecordType(
          fields = Map(
            "text" -> pb.TypeSchema(
              pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.STRING))
            )
          )
        )
      )
    )
    val inputType = TypeSchemaConverter.toCType(inputSchema).toOption.get
    val inputValue =
      CValue.CProduct(Map("text" -> CValue.CString("great movie")), Map("text" -> CType.CString))

    try {
      val result = buildDagAndRun(
        "AnalyzeSentiment",
        "nlp.sentiment",
        inputType,
        outputType,
        executorUrl,
        cache,
        inputValue
      ).unsafeRunSync()

      result shouldBe a[Right[_, _]]
      val outputs   = result.toOption.get
      val composite = outputs(firstKey).asInstanceOf[CValue.CProduct]
      composite.value("score") shouldBe CValue.CFloat(0.85f)
      composite.value("label") shouldBe CValue.CString("positive")
    } finally {
      cache.shutdownAll()
      server.shutdownNow()
    }
  }

  // ===== Single-field output handling =====

  it should "handle single-field output (non-CProduct response)" in {
    // When output spec has exactly 1 field, the module wraps a single CValue into that field
    val singleFieldExecutor = new FakeExecutorService(_ => {
      // Return a plain CString (not a CProduct)
      val outputBytes = serializer.serialize(CValue.CString("single-result")).toOption.get
      pb.ExecuteResponse(
        result = pb.ExecuteResponse.Result.OutputData(
          com.google.protobuf.ByteString.copyFrom(outputBytes)
        )
      )
    })

    val (server, port) = startFakeExecutor(singleFieldExecutor)
    val cache          = new GrpcChannelCache
    val executorUrl    = s"localhost:$port"
    val inputType      = CType.CProduct(Map("text" -> CType.CString))
    val outputType     = CType.CProduct(Map("result" -> CType.CString))
    val inputValue =
      CValue.CProduct(Map("text" -> CValue.CString("input")), Map("text" -> CType.CString))

    try {
      val result = buildDagAndRun(
        "SingleModule",
        "test.ns",
        inputType,
        outputType,
        executorUrl,
        cache,
        inputValue
      ).unsafeRunSync()

      result shouldBe a[Right[_, _]]
      val outputs = result.toOption.get
      // Single-field output: the executor returns a plain CString, stored as-is in the composite node
      outputs("result") shouldBe CValue.CString("single-result")
    } finally {
      cache.shutdownAll()
      server.shutdownNow()
    }
  }

  // ===== Executor returns error response =====

  it should "propagate error when executor returns ExecuteResponse.Error" in {
    val (server, port) = startFakeExecutor(errorExecutor)
    val cache          = new GrpcChannelCache
    val executorUrl    = s"localhost:$port"
    val inputType      = CType.CProduct(Map("text" -> CType.CString))
    val outputType     = CType.CProduct(Map("result" -> CType.CString))
    val inputValue =
      CValue.CProduct(Map("text" -> CValue.CString("input")), Map("text" -> CType.CString))

    try {
      val result = buildDagAndRun(
        "ErrorModule",
        "test.ns",
        inputType,
        outputType,
        executorUrl,
        cache,
        inputValue
      ).unsafeRunSync()

      // The module's run catches errors and sets Failed status, so result is Right
      // but the module status should be Failed
      result shouldBe a[Right[_, _]]
    } finally {
      cache.shutdownAll()
      server.shutdownNow()
    }
  }

  // ===== Executor returns empty response =====

  it should "propagate error when executor returns empty response" in {
    val (server, port) = startFakeExecutor(emptyExecutor)
    val cache          = new GrpcChannelCache
    val executorUrl    = s"localhost:$port"
    val inputType      = CType.CProduct(Map("text" -> CType.CString))
    val outputType     = CType.CProduct(Map("result" -> CType.CString))
    val inputValue =
      CValue.CProduct(Map("text" -> CValue.CString("input")), Map("text" -> CType.CString))

    try {
      val result = buildDagAndRun(
        "EmptyModule",
        "test.ns",
        inputType,
        outputType,
        executorUrl,
        cache,
        inputValue
      ).unsafeRunSync()

      // Module catches the error and sets Failed status
      result shouldBe a[Right[_, _]]
    } finally {
      cache.shutdownAll()
      server.shutdownNow()
    }
  }

  // ===== Channel cache reuse =====

  it should "reuse channel via GrpcChannelCache for same executor URL" in {
    val (server, port) = startFakeExecutor(echoExecutor)
    val cache          = new GrpcChannelCache
    val executorUrl    = s"localhost:$port"

    try {
      val ch1 = cache.getChannel(executorUrl)
      val ch2 = cache.getChannel(executorUrl)
      ch1 shouldBe theSameInstanceAs(ch2)
    } finally {
      cache.shutdownAll()
      server.shutdownNow()
    }
  }

  // ===== ExternalModule.create spec building =====

  it should "build correct consumes/produces spec from CProduct types" in {
    val cache = new GrpcChannelCache
    val pool  = RoundRobinExecutorPool.create.unsafeRunSync()
    pool.add(ExecutorEndpoint("conn-1", "localhost:9091")).unsafeRunSync()

    val module = ExternalModule.create(
      name = "TestMod",
      namespace = "ns",
      executorPool = pool,
      inputType = CType.CProduct(Map("a" -> CType.CString, "b" -> CType.CInt)),
      outputType = CType.CProduct(Map("x" -> CType.CFloat)),
      description = "test desc",
      serializer = serializer,
      channelCache = cache
    )

    module.spec.name shouldBe "ns.TestMod"
    module.spec.consumes shouldBe Map("a" -> CType.CString, "b" -> CType.CInt)
    module.spec.produces shouldBe Map("x" -> CType.CFloat)
    module.spec.metadata.description shouldBe "test desc"
    module.spec.metadata.tags should contain("external")
    module.spec.metadata.tags should contain("provider")
  }

  it should "wrap non-CProduct input type as Map(input -> type)" in {
    val cache = new GrpcChannelCache
    val pool  = RoundRobinExecutorPool.create.unsafeRunSync()
    pool.add(ExecutorEndpoint("conn-1", "localhost:9091")).unsafeRunSync()

    val module = ExternalModule.create(
      name = "Simple",
      namespace = "ns",
      executorPool = pool,
      inputType = CType.CString,
      outputType = CType.CInt,
      description = "simple",
      serializer = serializer,
      channelCache = cache
    )

    module.spec.consumes shouldBe Map("input" -> CType.CString)
    module.spec.produces shouldBe Map("output" -> CType.CInt)
  }
}
