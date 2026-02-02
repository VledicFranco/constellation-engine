package io.constellation.lsp.benchmark

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import io.circe.Json
import io.circe.syntax.*
import io.constellation.*
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.SemanticType
import io.constellation.lsp.ConstellationLanguageServer
import io.constellation.lsp.protocol.JsonRpc.*
import io.constellation.lsp.protocol.JsonRpc.RequestId.*
import io.constellation.lsp.protocol.LspMessages.*
import io.constellation.lsp.protocol.LspTypes.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

/** Concurrency benchmarks for LSP operations
  *
  * Tests LSP server behavior under concurrent request load to verify:
  *   - No deadlocks under parallel requests
  *   - Reasonable throughput with concurrent load
  *   - Thread safety of shared state
  *
  * Run with: sbt "langLsp/testOnly *ConcurrencyBenchmark"
  */
class ConcurrencyBenchmark extends AnyFlatSpec with Matchers {

  // Test configuration
  val ParallelRequests   = 10
  val StressTestRequests = 100
  val TimeoutSeconds     = 30

  // Test source
  val testSource: String =
    """in text: String
      |result = Uppercase(text)
      |out result
      |""".stripMargin

  // Test modules
  case class TestInput(text: String)
  case class TestOutput(result: String)

  private def createTestServer(): IO[ConstellationLanguageServer] = {
    val uppercaseModule = ModuleBuilder
      .metadata("Uppercase", "Converts text to uppercase", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.text.toUpperCase))
      .build

    val lowercaseModule = ModuleBuilder
      .metadata("Lowercase", "Converts text to lowercase", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.text.toLowerCase))
      .build

    for {
      constellation <- ConstellationImpl.init
      _             <- constellation.setModule(uppercaseModule)
      _             <- constellation.setModule(lowercaseModule)

      compiler = LangCompiler.builder
        .withModule(
          "Uppercase",
          uppercaseModule,
          List("text" -> SemanticType.SString),
          SemanticType.SString
        )
        .withModule(
          "Lowercase",
          lowercaseModule,
          List("text" -> SemanticType.SString),
          SemanticType.SString
        )
        .withCaching()
        .build

      server <- ConstellationLanguageServer.create(
        constellation,
        compiler,
        _ => IO.unit
      )
    } yield server
  }

  private def openDocument(
      server: ConstellationLanguageServer,
      uri: String,
      source: String
  ): IO[Unit] =
    server.handleNotification(
      Notification(
        method = "textDocument/didOpen",
        params = Some(
          DidOpenTextDocumentParams(
            TextDocumentItem(uri, "constellation", 1, source)
          ).asJson
        )
      )
    )

  private def completionRequest(
      server: ConstellationLanguageServer,
      uri: String,
      line: Int,
      char: Int,
      id: String
  ): IO[Response] =
    server.handleRequest(
      Request(
        id = StringId(id),
        method = "textDocument/completion",
        params = Some(
          CompletionParams(
            textDocument = TextDocumentIdentifier(uri),
            position = Position(line, char)
          ).asJson
        )
      )
    )

  private def hoverRequest(
      server: ConstellationLanguageServer,
      uri: String,
      line: Int,
      char: Int,
      id: String
  ): IO[Response] =
    server.handleRequest(
      Request(
        id = StringId(id),
        method = "textDocument/hover",
        params = Some(
          HoverParams(
            textDocument = TextDocumentIdentifier(uri),
            position = Position(line, char)
          ).asJson
        )
      )
    )

  private def dagRequest(
      server: ConstellationLanguageServer,
      uri: String,
      id: String
  ): IO[Response] =
    server.handleRequest(
      Request(
        id = StringId(id),
        method = "constellation/getDagVisualization",
        params = Some(Json.obj("uri" -> Json.fromString(uri)))
      )
    )

  // -----------------------------------------------------------------
  // Parallel Completion Requests
  // -----------------------------------------------------------------

  "Parallel completion requests" should "complete 10 requests concurrently without errors" in {
    val result = (for {
      server <- createTestServer()
      uri = "file:///test/concurrent1.cst"
      _ <- openDocument(server, uri, testSource)

      // Create 10 parallel completion requests
      requests = (0 until ParallelRequests).map { i =>
        completionRequest(server, uri, 1, 5, s"completion-$i")
      }.toList

      // Execute all in parallel
      startTime = System.nanoTime()
      responses <- requests.parSequence
      endTime   = System.nanoTime()
      elapsedMs = (endTime - startTime) / 1e6
    } yield (responses, elapsedMs)).unsafeRunSync()

    val (responses, elapsedMs) = result

    // All requests should succeed
    responses.size shouldBe ParallelRequests
    responses.foreach { response =>
      response.error shouldBe None
      response.result shouldBe defined
    }

    // Print timing
    println(
      f"[CONCURRENCY] 10 parallel completions: $elapsedMs%.2fms total, ${elapsedMs / ParallelRequests}%.2fms avg"
    )

    // Target: <1000ms total for 10 requests (allows for CI variability)
    elapsedMs should be < 1000.0
  }

  // -----------------------------------------------------------------
  // Mixed Request Types
  // -----------------------------------------------------------------

  "Mixed concurrent requests" should "handle completion, hover, and DAG requests in parallel" in {
    val result = (for {
      server <- createTestServer()
      uri = "file:///test/concurrent2.cst"
      _ <- openDocument(server, uri, testSource)

      // Mix of request types
      requests = List(
        completionRequest(server, uri, 1, 5, "mix-completion-1"),
        completionRequest(server, uri, 1, 8, "mix-completion-2"),
        hoverRequest(server, uri, 0, 3, "mix-hover-1"),
        hoverRequest(server, uri, 1, 10, "mix-hover-2"),
        dagRequest(server, uri, "mix-dag-1")
      )

      startTime = System.nanoTime()
      responses <- requests.parSequence
      endTime   = System.nanoTime()
      elapsedMs = (endTime - startTime) / 1e6
    } yield (responses, elapsedMs)).unsafeRunSync()

    val (responses, elapsedMs) = result

    // All requests should succeed
    responses.size shouldBe 5
    responses.foreach { response =>
      response.error shouldBe None
    }

    println(f"[CONCURRENCY] Mixed 5 parallel requests: $elapsedMs%.2fms total")

    // Should complete without timeout (DAG visualization alone takes ~250ms;
    // allow 1s for CI load and concurrent test interference)
    elapsedMs should be < 1500.0
  }

  // -----------------------------------------------------------------
  // Stress Test - Many Rapid Requests
  // -----------------------------------------------------------------

  "Stress test" should "handle 100 rapid requests without deadlock" in {
    val result = (for {
      server <- createTestServer()
      uri = "file:///test/stress.cst"
      _ <- openDocument(server, uri, testSource)

      // 100 rapid completion requests
      requests = (0 until StressTestRequests).map { i =>
        completionRequest(server, uri, 1, 5, s"stress-$i")
      }.toList

      startTime = System.nanoTime()
      // Use parSequence with a timeout to detect deadlocks
      responses <- IO
        .race(
          requests.parSequence,
          IO.sleep(TimeoutSeconds.seconds) *> IO.raiseError[List[Response]](
            new RuntimeException("Timeout - possible deadlock detected")
          )
        )
        .flatMap {
          case Left(results) => IO.pure(results)
          case Right(_)      => IO.raiseError(new RuntimeException("Timeout"))
        }
      endTime   = System.nanoTime()
      elapsedMs = (endTime - startTime) / 1e6
    } yield (responses, elapsedMs)).unsafeRunSync()

    val (responses, elapsedMs) = result

    // All 100 requests should complete
    responses.size shouldBe StressTestRequests
    val errorCount = responses.count(_.error.isDefined)

    println(
      f"[CONCURRENCY] 100 stress requests: $elapsedMs%.2fms total, ${elapsedMs / StressTestRequests}%.2fms avg, $errorCount errors"
    )

    // No more than 5% errors allowed (some may fail due to racing)
    errorCount should be <= 5
  }

  // -----------------------------------------------------------------
  // Multiple Documents Concurrently
  // -----------------------------------------------------------------

  "Multiple document handling" should "process requests for different documents in parallel" in {
    val result = (for {
      server <- createTestServer()

      // Open 5 different documents
      docs = (0 until 5).map(i => s"file:///test/doc$i.cst")
      _ <- docs.toList.traverse(uri => openDocument(server, uri, testSource))

      // Request completion for each document in parallel
      requests = docs.zipWithIndex.map { case (uri, i) =>
        completionRequest(server, uri, 1, 5, s"multidoc-$i")
      }.toList

      startTime = System.nanoTime()
      responses <- requests.parSequence
      endTime   = System.nanoTime()
      elapsedMs = (endTime - startTime) / 1e6
    } yield (responses, elapsedMs)).unsafeRunSync()

    val (responses, elapsedMs) = result

    responses.size shouldBe 5
    responses.foreach { response =>
      response.error shouldBe None
      response.result shouldBe defined
    }

    println(f"[CONCURRENCY] 5 different documents parallel: $elapsedMs%.2fms total")

    // Threshold allows for CI variability
    elapsedMs should be < 1000.0
  }

  // -----------------------------------------------------------------
  // Concurrent Cache Access
  // -----------------------------------------------------------------

  "Concurrent cache access" should "handle parallel requests hitting the same cached compilation" in {
    val result = (for {
      server <- createTestServer()
      uri = "file:///test/cached.cst"
      _ <- openDocument(server, uri, testSource)

      // First request warms up cache
      _ <- completionRequest(server, uri, 1, 5, "warmup")

      // Now 20 parallel requests should all hit cache
      requests = (0 until 20).map { i =>
        completionRequest(server, uri, 1, 5, s"cached-$i")
      }.toList

      startTime = System.nanoTime()
      responses <- requests.parSequence
      endTime   = System.nanoTime()
      elapsedMs = (endTime - startTime) / 1e6
    } yield (responses, elapsedMs)).unsafeRunSync()

    val (responses, elapsedMs) = result

    responses.size shouldBe 20
    responses.foreach { response =>
      response.error shouldBe None
    }

    println(
      f"[CONCURRENCY] 20 parallel cached requests: $elapsedMs%.2fms total, ${elapsedMs / 20}%.2fms avg"
    )

    // With caching, should be fast (allows for CI variability)
    elapsedMs should be < 500.0
  }
}
