# RFC-024: Module Provider Protocol (Revised)

**Status:** Draft (Revision 2 - Organon Compliant)
**Priority:** P3 (Extensibility)
**Author:** Claude (Revision after Organon Analysis)
**Created:** 2026-02-10
**Supersedes:** RFC-024 v1

---

## Summary

Define a **simple HTTP-based Module Provider Protocol** that allows external services to act as module implementations. Constellation orchestrates pipelines by making HTTP calls to these services, enabling polyglot module development without embedding foreign runtimes in the JVM.

**Key Changes from v1:**
- ✅ **Simplified:** HTTP + JSON (not gRPC + protobuf + MessagePack)
- ✅ **Explicit:** Static configuration (not dynamic service discovery)
- ✅ **Type-safe:** Compile-time schema validation
- ✅ **Resilience-aware:** Addresses `with` clause behavior
- ✅ **Failure-explicit:** Clear failure philosophy
- ✅ **Organon-compliant:** Addresses ETHOS concerns

---

## Motivation

### Problem: Language Lock-in

Constellation is Scala-first, which creates friction for teams that:
- Already have Python ML models
- Already have Node.js API integrations
- Prefer Go for high-performance modules
- Have legacy .NET/Ruby services

**Current Solution:** Rewrite everything in Scala
**Proposed Solution:** HTTP bridge to external implementations

### Why Not Embed Languages? (RFC-023 Lessons)

Embedding (GraalVM, Jython, etc.) creates:
- Dependency management nightmares
- Limited API access (no Node.js APIs, no pip packages)
- Distribution bloat (+100MB)
- Single language support
- High maintenance burden

### The HTTP Bridge Approach

```
┌──────────────────────┐         ┌─────────────────┐
│ Constellation (JVM)  │  HTTP   │ Python Service  │
│ - Orchestration      │────────▶│ - ML Models     │
│ - Type checking      │  JSON   │ - pip deps      │
│ - DAG execution      │◀────────│ - Native Python │
└──────────────────────┘         └─────────────────┘
```

**Trade-off:** Accept network latency for language flexibility

---

## ETHOS Compliance Argument

### Issue: Distributed Execution Out of Scope

**ETHOS.md line 149:** "Distributed execution (cross-node DAG execution)" is explicitly out of scope.

**Counter-Argument:** This RFC proposes **delegated execution**, not distributed orchestration:

| Distributed Orchestration (Out of Scope) | Delegated Execution (This RFC) |
|------------------------------------------|--------------------------------|
| DAG split across multiple Constellation instances | DAG runs on single Constellation instance |
| Cross-node coordination | Constellation coordinates all |
| Distributed state | Centralized state |
| Consensus algorithms | Simple HTTP calls |
| Multi-master | Single orchestrator |

**Analogy:** Calling an external API (HTTP request) vs running a distributed database (Cassandra).

**Proposal:** Clarify ETHOS.md to distinguish:
- ❌ **Distributed orchestration:** Multiple Constellation nodes coordinating DAG execution (out of scope)
- ✅ **Delegated execution:** Single Constellation calling external services as module implementations (in scope)

---

## Design

### Principle: Maximum Simplicity

**Choose HTTP + JSON because:**
- Every language has HTTP client/server
- JSON is universally supported
- Debuggable with curl/browser/Postman
- No protobuf compilation needed
- No MessagePack libraries needed
- No gRPC complexity

**Trade HTTP + JSON:**
- Pro: Simplicity, universality, debuggability
- Con: ~5ms extra latency vs gRPC, slightly larger payloads
- **Decision:** Simplicity > 5ms (ETHOS: "Simple Over Powerful")

### Architecture

```
┌──────────────────────────────┐
│ Constellation Engine         │
│                              │
│ ┌─────────────────────────┐ │
│ │ ExternalModule Wrapper  │ │
│ │ - HTTP client           │ │
│ │ - JSON serialization    │ │
│ │ - Retry/timeout logic   │ │
│ └─────────────────────────┘ │
│            │ HTTP POST       │
│            ▼                 │
└────────────┼─────────────────┘
             │
     ┌───────┴────────┐
     │                │
┌────▼─────┐   ┌─────▼────┐
│ Python   │   │ Node.js  │
│ Service  │   │ Service  │
│          │   │          │
│ POST     │   │ POST     │
│ /invoke  │   │ /invoke  │
└──────────┘   └──────────┘
```

---

## Configuration (Static, Explicit)

**application.conf:**

```hocon
constellation {
  external-modules {
    Sentiment {
      url = "http://ml-service:8080/invoke"
      timeout = 30s
      input-schema {
        text = "String"
      }
      output-schema {
        sentiment = "String"
        confidence = "Float"
      }
    }

    Uppercase {
      url = "http://text-service:8080/invoke"
      timeout = 5s
      input-schema {
        text = "String"
      }
      output-schema {
        result = "String"
      }
    }
  }
}
```

**Why Static Configuration?**
- ETHOS: "Explicit Over Implicit"
- No service discovery complexity
- No dynamic registration
- No heartbeat mechanism
- Configuration as code (versioned, auditable)
- Compile-time validation of schemas

---

## Protocol

### Request Format

```http
POST /invoke HTTP/1.1
Host: ml-service:8080
Content-Type: application/json
X-Execution-Id: uuid-1234
X-Timeout-Ms: 30000

{
  "text": "This product is amazing!"
}
```

### Success Response

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "sentiment": "positive",
  "confidence": 0.95
}
```

### Error Response

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": {
    "code": "TYPE_ERROR",
    "message": "Field 'text' is required",
    "details": {}
  }
}
```

**Error Codes:**
- `TYPE_ERROR` - Input doesn't match schema
- `RUNTIME_ERROR` - Execution failed
- `TIMEOUT` - Module execution exceeded timeout
- `INTERNAL_ERROR` - Unexpected service error

---

## Implementation

### Constellation Side (Scala)

```scala
class ExternalModule(
  name: String,
  config: ExternalModuleConfig,
  httpClient: HttpClient,
  inputType: CType,
  outputType: CType
) extends Module.Uninitialized {

  def spec: ModuleNodeSpec = ModuleNodeSpec(
    name = name,
    description = s"External module at ${config.url}",
    versionMajor = 1,
    versionMinor = 0,
    inputType = inputType,
    outputType = outputType
  )

  def initialize: IO[Module.Initialized] = IO.pure {
    new Module.Initialized {
      def execute(input: CValue): IO[CValue] = {
        for {
          // Validate input matches schema (compile-time guarantee)
          _ <- validateInput(input, inputType)

          // Serialize to JSON
          json <- IO(CValueJson.encode(input))

          // HTTP POST with timeout
          response <- httpClient
            .post(config.url, json)
            .timeout(config.timeout)
            .recoverWith {
              case _: TimeoutException =>
                IO.raiseError(ModuleError.Timeout(name, config.timeout))
              case e: IOException =>
                IO.raiseError(ModuleError.NetworkError(name, e))
            }

          // Deserialize response
          output <- response.status match {
            case Status.Ok =>
              IO(CValueJson.decode(response.body, outputType))
            case Status.BadRequest =>
              IO.raiseError(ModuleError.TypeMismatch(name, response.body))
            case Status.InternalServerError =>
              IO.raiseError(ModuleError.ExecutionFailed(name, response.body))
            case other =>
              IO.raiseError(ModuleError.UnexpectedStatus(name, other))
          }

          // Validate output matches schema
          _ <- validateOutput(output, outputType)

        } yield output
      }
    }
  }

  private def validateInput(value: CValue, expected: CType): IO[Unit] = {
    TypeSystem.typeOf(value) match {
      case Left(err) => IO.raiseError(ModuleError.InvalidInput(name, err))
      case Right(actual) if !TypeSystem.isSubtype(actual, expected) =>
        IO.raiseError(ModuleError.TypeMismatch(name, s"Expected $expected, got $actual"))
      case Right(_) => IO.unit
    }
  }

  private def validateOutput(value: CValue, expected: CType): IO[Unit] = {
    // Same as validateInput but for output
    ???
  }
}
```

### Provider Side (Python)

```python
# ml_service.py
from flask import Flask, request, jsonify
import json

app = Flask(__name__)

@app.route('/invoke', methods=['POST'])
def invoke():
    try:
        # Parse input
        input_data = request.json
        text = input_data.get('text')

        if not text or not isinstance(text, str):
            return jsonify({
                "error": {
                    "code": "TYPE_ERROR",
                    "message": "Field 'text' must be a string"
                }
            }), 400

        # Execute module logic
        from transformers import pipeline
        classifier = pipeline("sentiment-analysis")
        result = classifier(text)[0]

        # Return result
        return jsonify({
            "sentiment": result["label"].lower(),
            "confidence": result["score"]
        }), 200

    except Exception as e:
        return jsonify({
            "error": {
                "code": "RUNTIME_ERROR",
                "message": str(e)
            }
        }), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080)
```

### Provider Side (Node.js)

```javascript
// text_service.js
const express = require('express');
const app = express();
app.use(express.json());

app.post('/invoke', (req, res) => {
  try {
    const { text } = req.body;

    if (!text || typeof text !== 'string') {
      return res.status(400).json({
        error: {
          code: 'TYPE_ERROR',
          message: "Field 'text' must be a string"
        }
      });
    }

    // Execute module logic
    const result = text.toUpperCase();

    // Return result
    res.json({ result });

  } catch (err) {
    res.status(500).json({
      error: {
        code: 'RUNTIME_ERROR',
        message: err.message
      }
    });
  }
});

app.listen(8080, () => {
  console.log('Text service listening on port 8080');
});
```

---

## Resilience Integration

### How `with` Clauses Work

**1. `with retry: 3`**

```scala
result = Sentiment(input) with retry: 3
```

**Behavior:**
- Constellation retries HTTP request up to 3 times on network errors
- Does NOT retry on 4xx errors (type errors, invalid input)
- DOES retry on 5xx errors (service errors) and timeouts
- **Idempotency:** External services must be idempotent or use request IDs

**Implementation:**
```scala
def executeWithRetry(maxAttempts: Int): IO[CValue] =
  execute(input)
    .retryWithBackoff(
      maxAttempts,
      shouldRetry = {
        case ModuleError.NetworkError(_, _) => true
        case ModuleError.Timeout(_, _) => true
        case ModuleError.ExecutionFailed(_, _) => true
        case _ => false
      }
    )
```

**2. `with timeout: 30s`**

```scala
result = Sentiment(input) with timeout: 30s
```

**Behavior:**
- HTTP request timeout set to 30s
- If service doesn't respond in 30s, module fails
- Timeout error surfaces to user clearly

**3. `with cache: 5min`**

```scala
result = Sentiment(input) with cache: 5min
```

**Behavior:**
- Cache HTTP response based on input hash
- Cache hit: Skip HTTP call entirely
- Cache miss: Make HTTP call, cache result
- **Same as Scala modules** - caching is transparent

**4. `with fallback: default`**

```scala
result = Sentiment(input) with fallback: { sentiment: "neutral", confidence: 0.0 }
```

**Behavior:**
- On any error (network, timeout, 5xx), return fallback value
- **Same as Scala modules** - fallback is last resort

---

## Failure Philosophy

### Clear Error Messages

**ETHOS: "Fail with clear messages. Error messages should identify the problem and suggest solutions."**

**Examples:**

```
❌ Module 'Sentiment' failed

✅ External module 'Sentiment' failed: Network timeout after 30s
   → Check if ml-service is running: kubectl get pods | grep ml-service
   → Check network connectivity: curl http://ml-service:8080/health
   → Consider increasing timeout: with timeout: 60s
```

```
❌ Type error

✅ External module 'Sentiment' received invalid response:
   Expected: { sentiment: String, confidence: Float }
   Received: { sentiment: String, score: Float }
   → Field 'confidence' is missing. Did the service API change?
   → Check ml-service logs for errors
```

### Failure Visibility

**ETHOS: "Fail visibly. Never swallow errors silently."**

**All external module errors surface clearly:**
- Network failures → `ModuleError.NetworkError`
- Timeouts → `ModuleError.Timeout`
- Type mismatches → `ModuleError.TypeMismatch`
- Service errors → `ModuleError.ExecutionFailed`

**No silent failures.** Users always know what went wrong.

---

## Type Safety Preservation

### Compile-Time Schema Validation

**Problem:** External services can return wrong types at runtime.

**Solution:** Validate schemas at Constellation startup.

```scala
object ExternalModuleLoader {
  def loadFromConfig(config: Config): IO[List[Module.Uninitialized]] = {
    config.getConfig("constellation.external-modules").entrySet.traverse { entry =>
      val name = entry.getKey
      val moduleConfig = entry.getValue.asInstanceOf[ConfigObject]

      for {
        // Parse schemas from config
        inputType <- IO(parseTypeSchema(moduleConfig.get("input-schema")))
        outputType <- IO(parseTypeSchema(moduleConfig.get("output-schema")))

        // Validate schemas are well-formed
        _ <- validateTypeSchema(inputType)
        _ <- validateTypeSchema(outputType)

        // Create external module
        module = new ExternalModule(name, moduleConfig, httpClient, inputType, outputType)

        // Optional: Health check external service
        _ <- healthCheck(moduleConfig.getString("url"))

      } yield module
    }
  }
}
```

**At startup, Constellation validates:**
1. ✅ Schema syntax is correct
2. ✅ External service is reachable (health check)
3. ✅ Types are well-formed (no invalid CTypes)

**At runtime, Constellation validates:**
1. ✅ Input matches schema before HTTP call
2. ✅ Output matches schema after HTTP call
3. ✅ Failures surface clearly (not silent)

**Result:** Type errors caught as early as possible (startup), not deep in production pipelines.

---

## Parallelization

### Automatic Parallelization Preserved

**Question:** Can external modules be parallelized?

**Answer:** Yes, same as Scala modules.

```scala
// Independent external modules execute in parallel
result1 = Sentiment(text1)  // HTTP call 1
result2 = Sentiment(text2)  // HTTP call 2 (parallel)
result3 = Uppercase(text3)  // HTTP call 3 (parallel)
```

**Scheduler sees:**
- 3 independent nodes
- 0 dependencies between them
- ∴ Execute in parallel (3 concurrent HTTP requests)

### `with concurrency: N`

```scala
result = Sentiment(input) with concurrency: 5
```

**Behavior:**
- Limits parallel execution to 5 concurrent HTTP calls
- Queues additional requests
- **Same as Scala modules** - concurrency limiting works identically

---

## Development Experience

### Local Development

**docker-compose.yml:**

```yaml
version: '3.8'
services:
  constellation:
    image: constellation-engine:latest
    ports:
      - "8080:8080"
    environment:
      - EXTERNAL_MODULES_URL_SENTIMENT=http://ml-service:8080/invoke
      - EXTERNAL_MODULES_URL_UPPERCASE=http://text-service:8080/invoke

  ml-service:
    build: ./ml-service
    ports:
      - "8081:8080"

  text-service:
    build: ./text-service
    ports:
      - "8082:8080"
```

**Developer runs:**
```bash
docker-compose up
# All services start together
# Constellation connects to external modules automatically
```

### Testing

**Unit Tests (Mock HTTP):**

```scala
test("External module handles network timeout") {
  val mockClient = new HttpClient {
    def post(url: String, body: Json): IO[HttpResponse] =
      IO.sleep(35.seconds) >> IO.pure(HttpResponse(Status.Ok, "{}"))
  }

  val module = new ExternalModule("Sentiment", config, mockClient, inputType, outputType)

  module.initialize.flatMap(_.execute(input)).timeout(30.seconds).attempt.map {
    case Left(_: TimeoutException) => succeed
    case Right(_) => fail("Should have timed out")
  }
}
```

**Integration Tests (Real HTTP):**

```scala
test("Sentiment analysis returns valid result") {
  // Start real Python service in background
  val service = startPythonService("ml_service.py")

  try {
    val module = new ExternalModule("Sentiment", config, httpClient, inputType, outputType)

    module.initialize.flatMap { m =>
      m.execute(CValue.CRecord(Map("text" -> CValue.CString("Great product!"))))
    }.map { result =>
      result shouldBe CValue.CRecord(Map(
        "sentiment" -> CValue.CString("positive"),
        "confidence" -> CValue.CFloat(0.95)
      ))
    }
  } finally {
    service.stop()
  }
}
```

---

## Deployment

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: constellation
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: constellation
        image: constellation-engine:latest
        env:
        - name: EXTERNAL_MODULES_URL_SENTIMENT
          value: "http://ml-service:8080/invoke"
        - name: EXTERNAL_MODULES_URL_UPPERCASE
          value: "http://text-service:8080/invoke"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ml-service
spec:
  replicas: 5  # ML inference needs more capacity
  template:
    spec:
      containers:
      - name: ml-service
        image: ml-service:latest
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: text-service
spec:
  replicas: 2  # Text processing is lightweight
```

**Independent scaling:** Each service scales based on its own needs.

---

## Benefits

### 1. **Simplicity** (vs v1)

**v1:** gRPC + protobuf + MessagePack + registry + heartbeats + SDKs
**v2:** HTTP + JSON + static config

**Lines of code:**
- v1: ~2000 lines (protocol definitions, registry, SDKs)
- v2: ~500 lines (HTTP client wrapper, JSON serialization)

### 2. **Debuggability**

```bash
# Debug external module with curl
curl -X POST http://ml-service:8080/invoke \
  -H "Content-Type: application/json" \
  -d '{"text": "This is great!"}'

# Check response
{"sentiment": "positive", "confidence": 0.95}
```

**No special tools needed** - just curl, browser, Postman.

### 3. **Language Flexibility**

Python, Node.js, Go, Rust, Ruby, .NET, PHP - anything with HTTP + JSON.

**Adoption cost:** ~50 lines of code per service (HTTP server + JSON parsing).

### 4. **Independent Development**

```
Team A (Scala)   → Constellation core
Team B (Python)  → ML models
Team C (Node.js) → API integrations
Team D (Go)      → High-performance modules

Each team:
- Uses their preferred language
- Manages their own dependencies
- Deploys independently
- Tests independently
```

### 5. **Gradual Migration**

```scala
// Mix Scala and external modules seamlessly
val pipeline = for {
  cleaned <- Trim(input.text)           // Scala (in-process)
  sentiment <- Sentiment(cleaned)       // Python (HTTP)
  formatted <- FormatResult(sentiment)  // Scala (in-process)
} yield formatted
```

---

## Trade-offs

| Aspect | Benefit | Cost |
|--------|---------|------|
| **Latency** | Acceptable for most cases | +1-5ms per HTTP call |
| **Complexity** | Simple (HTTP + JSON) | More services to deploy |
| **Type Safety** | Validated at startup + runtime | Not compile-time for external schemas |
| **Debugging** | Easy (curl, logs) | Distributed tracing helps |
| **Flexibility** | Any language | Operational overhead |

**Decision:** Trade 5ms for language flexibility. Most pipelines have 100ms+ total latency, so 5ms is negligible.

---

## Migration Path

### Phase 1: Proof of Concept (1 week)

- Implement ExternalModule wrapper
- Create simple Python service (Sentiment)
- End-to-end test: Constellation → Python → Response
- Measure latency overhead

**Success Criteria:** <5ms HTTP overhead

### Phase 2: Hardening (1 week)

- Add retry logic
- Add timeout handling
- Add error surfacing
- Add schema validation
- Integration tests

**Success Criteria:** All resilience options work

### Phase 3: Documentation (1 week)

- Provider guide (Python, Node.js, Go examples)
- Deployment guide (Docker, Kubernetes)
- Testing guide
- Migration guide (Scala → External)

**Success Criteria:** External team can create module without help

### Phase 4: Production Pilots (2 weeks)

- Migrate 2-3 real modules to external services
- Monitor performance
- Collect feedback
- Iterate

**Success Criteria:** Production-ready, teams satisfied

---

## Alternatives Considered

### Alternative 1: GraalVM Embedding (RFC-023)

**Rejected:** Dependency hell, limited APIs, single language

### Alternative 2: WebAssembly (WASM)

**Pro:** Near-native performance, sandboxed, portable
**Con:** Immature ecosystem, limited language support (no Python ML libs)
**Decision:** Revisit in 2027 when WASM matures

### Alternative 3: Suspension + Webhooks (RFC-014 Extension)

**Pro:** Leverages existing suspension infrastructure
**Con:** More complex for simple sync calls (overkill)
**Decision:** Keep for async/long-running modules

### Alternative 4: Do Nothing (Scala Only)

**Pro:** Simplest (no external modules)
**Con:** Language lock-in, friction for non-Scala teams
**Decision:** Rejected - polyglot support requested by users

---

## Open Questions

1. **Request IDs:** Should we enforce idempotency tokens?
2. **Observability:** Integrate with OpenTelemetry for tracing?
3. **Security:** Mutual TLS between Constellation and services?
4. **Versioning:** How to handle breaking changes in external modules?
5. **Marketplace:** Should we create a registry of public external modules?

---

## Success Criteria

- ✅ Python service provides ML module
- ✅ Node.js service provides API integration module
- ✅ Mixed pipeline (Scala + Python + Node.js)
- ✅ <5ms HTTP overhead
- ✅ All resilience options work (`retry`, `timeout`, `cache`, `fallback`)
- ✅ Type safety preserved (validate at startup + runtime)
- ✅ Errors surface clearly
- ✅ Developer experience: <50 lines of code to create external module

---

## Conclusion

This revision addresses Organon concerns by:

1. ✅ **Simplifying:** HTTP + JSON (not gRPC + protobuf)
2. ✅ **Explicit:** Static config (not service discovery)
3. ✅ **Type-safe:** Validation at startup + runtime
4. ✅ **Resilience-aware:** All `with` clauses defined
5. ✅ **Failure-explicit:** Clear error messages
6. ✅ **Philosophy-aligned:** Argues for "delegated execution" vs "distributed orchestration"

**Trade-off:** Accept 5ms network latency for language flexibility and simplicity.

**Recommended:** Approve for POC implementation.
