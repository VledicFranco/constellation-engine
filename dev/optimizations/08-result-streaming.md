# Optimization 08: Result Streaming

**Priority:** 8 (Lower Priority - Situational)
**Expected Gain:** Improved Time-To-First-Byte (TTFB)
**Complexity:** High
**Status:** Not Implemented

---

## Problem Statement

Currently, DAG execution collects all results before returning:

```scala
// Runtime.scala (simplified)

def run(dagSpec: DagSpec, inputs: ...): IO[Map[String, CValue]] = {
  for {
    _ <- executeAllModules()      // Wait for ALL modules
    results <- collectAllOutputs() // Collect ALL outputs
  } yield results                  // Return everything at once
}
```

For DAGs with multiple independent outputs, the client must wait for the slowest output even if faster outputs are ready.

### Example Scenario

```
DAG with 3 outputs:
- output_a: ready in 10ms (fast)
- output_b: ready in 50ms (medium)
- output_c: ready in 500ms (slow, calls external API)

Current behavior:
  Client receives response at: 500ms (waits for slowest)

With streaming:
  Client receives output_a at: 10ms
  Client receives output_b at: 50ms
  Client receives output_c at: 500ms
```

---

## Proposed Solution

Stream results as they become available using Server-Sent Events (SSE) or WebSocket frames.

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    DAG Execution                         │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  [Module A] ──► [output_a] ───┐                         │
│                               ├──► Stream Emitter ──►   │
│  [Module B] ──► [output_b] ───┤         │               │
│                               │         ▼               │
│  [Module C] ──► [output_c] ───┘    SSE/WebSocket        │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### Implementation

#### Step 1: Streaming Runtime Interface

```scala
// New: StreamingRuntime.scala

import fs2.Stream
import cats.effect.IO

trait StreamingRuntime {

  case class OutputEvent(
    name: String,
    value: CValue,
    timestamp: Long,
    isFinal: Boolean  // True if this is the last output
  )

  /**
   * Execute DAG and stream results as they complete.
   */
  def runStreaming(
    dagSpec: DagSpec,
    inputs: Map[String, CValue]
  ): Stream[IO, OutputEvent]
}

class StreamingRuntimeImpl(runtime: Runtime) extends StreamingRuntime {

  def runStreaming(
    dagSpec: DagSpec,
    inputs: Map[String, CValue]
  ): Stream[IO, OutputEvent] = {

    // Identify output data nodes
    val outputNodes = dagSpec.dataNodes.filter(_.name.startsWith("out_"))
    val outputCount = outputNodes.size

    Stream.eval(runtime.initExecution(dagSpec, inputs)).flatMap { execution =>
      // Create a stream that emits as each output completes
      Stream.emits(outputNodes.toList)
        .parEvalMapUnordered(outputCount) { node =>
          // Wait for this specific output
          execution.awaitDataNode(node.id).map { value =>
            OutputEvent(
              name = node.name.stripPrefix("out_"),
              value = value,
              timestamp = System.currentTimeMillis(),
              isFinal = false
            )
          }
        }
        .onFinalize(execution.cleanup)
        .zipWithIndex
        .map { case (event, index) =>
          event.copy(isFinal = index == outputCount - 1)
        }
    }
  }
}
```

#### Step 2: SSE Endpoint

```scala
// ConstellationRoutes.scala additions

import org.http4s.ServerSentEvent
import fs2.Stream

def streamingRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {

  case req @ POST -> Root / "execute" / "stream" =>
    for {
      request <- req.as[ExecuteRequest]
      dagSpec <- compiler.compile(request.source)

      // Create SSE stream
      sseStream = streamingRuntime
        .runStreaming(dagSpec, request.inputs)
        .map { event =>
          ServerSentEvent(
            data = Some(event.asJson.noSpaces),
            eventType = Some(if (event.isFinal) "complete" else "output"),
            id = Some(event.name)
          )
        }

      response <- Ok(sseStream)
        .map(_.withContentType(`Content-Type`(MediaType.text.`event-stream`)))
    } yield response
}
```

#### Step 3: WebSocket Alternative

```scala
// For bidirectional streaming (e.g., progress updates)

def websocketRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {

  case GET -> Root / "execute" / "ws" =>
    val handler: Pipe[IO, WebSocketFrame, WebSocketFrame] = { inputStream =>
      inputStream
        .collect { case WebSocketFrame.Text(json, _) => json }
        .evalMap(json => IO.fromEither(decode[ExecuteRequest](json)))
        .flatMap { request =>
          Stream.eval(compiler.compile(request.source)).flatMap { dagSpec =>
            streamingRuntime
              .runStreaming(dagSpec, request.inputs)
              .map(event => WebSocketFrame.Text(event.asJson.noSpaces))
          }
        }
    }

    WebSocketBuilder[IO].build(handler)
}
```

---

## Client Integration

### JavaScript SSE Client

```javascript
const eventSource = new EventSource('/execute/stream', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    source: 'in text: String\nout upper: String\nupper = Uppercase(text)',
    inputs: { text: 'hello' }
  })
});

eventSource.addEventListener('output', (event) => {
  const output = JSON.parse(event.data);
  console.log(`Received ${output.name}:`, output.value);
  // Update UI incrementally
});

eventSource.addEventListener('complete', (event) => {
  const output = JSON.parse(event.data);
  console.log('Final output:', output.name);
  eventSource.close();
});

eventSource.onerror = (error) => {
  console.error('Stream error:', error);
  eventSource.close();
};
```

### Python Client

```python
import sseclient
import requests

def execute_streaming(source: str, inputs: dict):
    response = requests.post(
        'http://localhost:8080/execute/stream',
        json={'source': source, 'inputs': inputs},
        stream=True
    )

    client = sseclient.SSEClient(response)
    for event in client.events():
        output = json.loads(event.data)
        yield output['name'], output['value']

        if event.event == 'complete':
            break

# Usage
for name, value in execute_streaming(source, inputs):
    print(f"Got {name}: {value}")
```

---

## Progress Events

Extend streaming to include progress updates:

```scala
sealed trait StreamEvent
case class OutputReady(name: String, value: CValue) extends StreamEvent
case class ModuleStarted(name: String) extends StreamEvent
case class ModuleCompleted(name: String, latencyMs: Long) extends StreamEvent
case class ExecutionProgress(completed: Int, total: Int) extends StreamEvent

def runStreamingWithProgress(
  dagSpec: DagSpec,
  inputs: Map[String, CValue]
): Stream[IO, StreamEvent] = {
  // Interleave output events with progress events
  val outputStream = runStreaming(dagSpec, inputs).map(OutputReady.apply)
  val progressStream = runtime.progressUpdates.map {
    case (completed, total) => ExecutionProgress(completed, total)
  }

  outputStream.merge(progressStream)
}
```

---

## Backpressure Handling

For slow clients, implement backpressure:

```scala
def runStreamingWithBackpressure(
  dagSpec: DagSpec,
  inputs: Map[String, CValue],
  maxBuffered: Int = 10
): Stream[IO, OutputEvent] = {

  Stream.eval(Queue.bounded[IO, OutputEvent](maxBuffered)).flatMap { queue =>
    // Producer: execution emits to queue
    val producer = runStreaming(dagSpec, inputs)
      .evalMap(event => queue.offer(event))
      .compile
      .drain

    // Consumer: pull from queue (backpressure applied here)
    val consumer = Stream.fromQueueUnterminated(queue)

    // Run both concurrently
    consumer.concurrently(Stream.eval(producer))
  }
}
```

---

## Benchmarks

### Test Scenario

```
DAG with mixed latency outputs:
- fast_output: 5ms
- medium_output: 50ms
- slow_output: 500ms (simulated external API)
```

### Expected Results

| Metric | Batch Response | Streaming |
|--------|----------------|-----------|
| TTFB (first output) | 500ms | 5ms |
| All outputs received | 500ms | 500ms |
| Perceived responsiveness | Poor | Excellent |

---

## When to Use Streaming

### Good Use Cases

- DAGs with independent outputs of varying latency
- Long-running pipelines where progress feedback is valuable
- Real-time dashboards displaying partial results
- ML inference with multiple model outputs

### When Batch is Better

- Simple DAGs with single output
- Very fast executions (<10ms total)
- Clients that need all outputs atomically
- High-throughput batch processing (SSE overhead per request)

---

## Implementation Checklist

- [ ] Create `StreamingRuntime` trait and implementation
- [ ] Add SSE endpoint to `ConstellationRoutes`
- [ ] Optional: Add WebSocket endpoint for bidirectional streaming
- [ ] Implement progress event emission in `Runtime`
- [ ] Add backpressure handling
- [ ] Create JavaScript client library
- [ ] Update VSCode extension to use streaming
- [ ] Document streaming API

---

## Files to Modify

| File | Changes |
|------|---------|
| New: `modules/runtime/.../StreamingRuntime.scala` | Streaming execution |
| `modules/http-api/.../ConstellationRoutes.scala` | SSE/WebSocket endpoints |
| `modules/http-api/.../ApiModels.scala` | Stream event types |
| `modules/runtime/.../Runtime.scala` | Progress event hooks |

---

## Dependencies

```scala
// build.sbt additions (if not present)
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-server" % http4sVersion,  // SSE support included
  "co.fs2" %% "fs2-core" % fs2Version               // Stream primitives
)
```

---

## Related Optimizations

- [JSON Conversion Optimization](./03-json-conversion-optimization.md) - Streaming JSON complements result streaming
- [Compilation Caching](./01-compilation-caching.md) - Fast compilation enables faster TTFB
