# RFC-024: Module Provider Protocol

**Status:** Draft (Revision 3 - CURRENT)
**Priority:** P3 (Extensibility)
**Author:** Claude + User
**Created:** 2026-02-10
**Last Updated:** 2026-02-10

> **⚠️ This is version 1 (superseded)**
>
> **Current version:** [rfc-024-module-provider-protocol-v3.md](./rfc-024-module-provider-protocol-v3.md)
>
> **Key changes in v3:**
> - ✅ gRPC (not HTTP) for 2.5x better performance
> - ✅ Compile-time type validation with proof protocol
> - ✅ Static configuration first (defer dynamic registration)
> - ✅ Leverages existing compiler for validation
>
> **See Also:**
> - [rfc-024-analysis.md](./rfc-024-analysis.md) - Organon compliance analysis
> - [rfc-024-refinement-summary.md](./rfc-024-refinement-summary.md) - Revision process
>
> ---

---

## Summary

Define a language-agnostic **Module Provider Protocol** that allows external services (Node.js, Python, Go, Rust, etc.) to register themselves as module providers. Constellation Engine orchestrates pipelines by making RPC calls to these services, enabling true polyglot module development without embedding foreign runtimes in the JVM.

**Key Insight:** Instead of embedding languages in Constellation, let services register and execute in their native runtimes. Constellation becomes the orchestrator, not the executor.

---

## Motivation

### Problem with Language Embedding (RFC-023 Approach)

Embedding JavaScript/TypeScript via GraalVM had fundamental issues:
- **Dependency Hell**: Managing npm packages in JVM
- **Limited API Support**: No Node.js APIs, no native modules
- **Distribution Bloat**: +100MB for GraalVM
- **Single Language**: Only helps TypeScript developers
- **Maintenance Burden**: Keeping up with JS ecosystem

### The Module Provider Solution

Instead of bringing code to Constellation, bring Constellation to the code:

```
OLD (Embedding):
┌────────────────────────────┐
│   Constellation (JVM)      │
│   ├─ Scala modules         │
│   └─ GraalVM (embedded)    │
│       └─ TypeScript code   │  ❌ Dependency hell
└────────────────────────────┘

NEW (Protocol):
┌────────────────────────┐     ┌──────────────┐
│ Constellation (JVM)    │────▶│ Node.js      │
│ - Orchestration        │     │ - npm deps   │
│ - Type checking        │     │ - Modules    │
│ - DAG execution        │     └──────────────┘
└────────────────────────┘     ┌──────────────┐
             │                 │ Python       │
             └────────────────▶│ - pip deps   │
                               │ - Modules    │
                               └──────────────┘
```

---

## Architecture

### Conceptual Model: Nanoservices

Think of each module provider as a **nanoservice** - a small, focused service that provides one or more modules.

**Unlike microservices:**
- Not business-domain-aligned (order service, user service)
- Function-aligned (text processing, ML inference, image generation)
- Can be deployed together or separately
- Constellation doesn't care about topology

**Example:**

```
┌─────────────────────────────────────────────────┐
│  text-processing-service (Node.js)              │
│  Repo: github.com/company/text-processing       │
│                                                  │
│  Modules:                                       │
│  - Uppercase(text: String) -> String            │
│  - Lowercase(text: String) -> String            │
│  - Trim(text: String) -> String                 │
│  - WordCount(text: String) -> Int               │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│  ml-inference-service (Python)                  │
│  Repo: github.com/company/ml-models             │
│                                                  │
│  Modules:                                       │
│  - Sentiment(text: String) -> String            │
│  - Embedding(text: String) -> List<Float>       │
│  - Summarize(text: String, maxLen: Int)         │
└─────────────────────────────────────────────────┘
```

---

## Protocol Design

### 1. Module Registration

Services register modules on startup:

```protobuf
// registration.proto
service ModuleRegistry {
  rpc Register(RegistrationRequest) returns (RegistrationResponse);
  rpc Heartbeat(HeartbeatRequest) returns (HeartbeatResponse);
  rpc Unregister(UnregisterRequest) returns (UnregisterResponse);
}

message RegistrationRequest {
  string service_id = 1;           // "text-processing-service"
  string service_url = 2;          // "http://text-processing:8080"
  repeated ModuleSpec modules = 3;
}

message ModuleSpec {
  string name = 1;                 // "Uppercase"
  string description = 2;
  TypeSchema input_schema = 3;     // CType representation
  TypeSchema output_schema = 4;
  int32 version_major = 5;
  int32 version_minor = 6;
}

message TypeSchema {
  oneof type {
    PrimitiveType primitive = 1;   // String, Int, Float, Bool
    RecordType record = 2;          // { field: Type, ... }
    ListType list = 3;              // List<T>
    UnionType union = 4;            // A | B
    OptionType option = 5;          // Option<T>
  }
}
```

**Example (Node.js SDK):**

```typescript
import { ConstellationProvider } from '@constellation/provider-sdk';

const provider = new ConstellationProvider({
  serviceId: 'text-processing-service',
  serviceUrl: 'http://localhost:8080',
  registryUrl: 'http://constellation:9000'
});

// Register modules
provider.registerModule({
  name: 'Uppercase',
  description: 'Convert text to uppercase',
  inputSchema: { text: 'String' },
  outputSchema: { result: 'String' },
  handler: async (input) => {
    return { result: input.text.toUpperCase() };
  }
});

provider.registerModule({
  name: 'WordCount',
  description: 'Count words in text',
  inputSchema: { text: 'String' },
  outputSchema: { count: 'Int' },
  handler: async (input) => {
    return { count: input.text.split(/\s+/).length };
  }
});

// Start server and register with Constellation
await provider.start();
```

### 2. Module Execution

Constellation calls modules via RPC:

```protobuf
// execution.proto
service ModuleExecutor {
  rpc Execute(ExecuteRequest) returns (ExecuteResponse);
}

message ExecuteRequest {
  string module_name = 1;           // "Uppercase"
  bytes input_data = 2;             // MessagePack-encoded input
  string execution_id = 3;          // For tracing
  map<string, string> context = 4;  // Optional metadata
}

message ExecuteResponse {
  oneof result {
    bytes output_data = 1;          // MessagePack-encoded output
    ExecutionError error = 2;
  }
  ExecutionMetrics metrics = 3;
}

message ExecutionError {
  string code = 1;                  // "TYPE_ERROR", "RUNTIME_ERROR"
  string message = 2;
  string stack_trace = 3;
}

message ExecutionMetrics {
  int64 duration_ms = 1;
  int64 memory_bytes = 2;
}
```

**Binary Serialization:** MessagePack for efficiency

### 3. Service Discovery

**Option A: Central Registry (POC)**

```scala
class ModuleProviderRegistry {
  private val providers = ConcurrentHashMap[String, ServiceInfo]()

  def register(req: RegistrationRequest): IO[Unit] = IO {
    req.modules.foreach { spec =>
      val module = ExternalModule(
        name = spec.name,
        serviceUrl = req.service_url,
        inputSchema = spec.input_schema,
        outputSchema = spec.output_schema
      )
      providers.put(spec.name, ServiceInfo(req.service_id, module))
    }
  }

  def getModule(name: String): IO[Option[ExternalModule]] = IO {
    Option(providers.get(name)).map(_.module)
  }
}
```

**Option B: Service Mesh (Production)**

Use existing service mesh (Istio, Linkerd, Consul) for discovery:

```yaml
# Kubernetes Service
apiVersion: v1
kind: Service
metadata:
  name: text-processing-service
  annotations:
    constellation.io/provides-modules: "Uppercase,Lowercase,Trim,WordCount"
    constellation.io/module-version: "1.0"
spec:
  selector:
    app: text-processing
  ports:
    - port: 8080
```

Constellation queries Kubernetes API for services with `constellation.io/provides-modules` annotation.

---

## Implementation Example

### Constellation Side (Scala)

```scala
// External module wrapper
class ExternalModule(
  name: String,
  serviceUrl: String,
  inputSchema: CType,
  outputSchema: CType,
  httpClient: HttpClient
) extends Module.Uninitialized {

  def spec: ModuleNodeSpec = ModuleNodeSpec(
    name = name,
    description = s"External module from $serviceUrl",
    versionMajor = 1,
    versionMinor = 0,
    inputType = inputSchema,
    outputType = outputSchema
  )

  def initialize: IO[Module.Initialized] = IO.pure {
    new Module.Initialized {
      def execute(input: CValue): IO[CValue] = {
        for {
          // Serialize input to MessagePack
          inputBytes <- IO(MessagePack.encode(input))

          // Call external service
          response <- httpClient.post(
            url = s"$serviceUrl/execute",
            body = ExecuteRequest(
              module_name = name,
              input_data = inputBytes
            )
          )

          // Deserialize output from MessagePack
          output <- response.result match {
            case ExecuteResponse.OutputData(bytes) =>
              IO(MessagePack.decode(bytes, outputSchema))
            case ExecuteResponse.Error(err) =>
              IO.raiseError(new RuntimeException(s"Module error: ${err.message}"))
          }
        } yield output
      }
    }
  }
}

// Use in pipelines
val textProcessor = ExternalModule(
  name = "Uppercase",
  serviceUrl = "http://text-processing:8080",
  inputSchema = CType.CRecord(Map("text" -> CType.CString)),
  outputSchema = CType.CRecord(Map("result" -> CType.CString)),
  httpClient = httpClient
)

constellation.setModule(textProcessor)
```

### Provider Side (Node.js SDK)

```typescript
// @constellation/provider-sdk
import express from 'express';
import msgpack from 'msgpack-lite';

export class ConstellationProvider {
  private modules = new Map<string, ModuleHandler>();
  private app = express();

  constructor(private config: ProviderConfig) {
    this.setupRoutes();
  }

  registerModule(spec: ModuleSpec) {
    this.modules.set(spec.name, {
      spec,
      handler: spec.handler
    });
  }

  private setupRoutes() {
    // Execution endpoint
    this.app.post('/execute', async (req, res) => {
      const request = ExecuteRequest.decode(req.body);
      const module = this.modules.get(request.module_name);

      if (!module) {
        return res.status(404).json({
          error: { code: 'MODULE_NOT_FOUND', message: `Module ${request.module_name} not found` }
        });
      }

      try {
        const startTime = Date.now();

        // Decode input
        const input = msgpack.decode(request.input_data);

        // Execute module
        const output = await module.handler(input);

        // Encode output
        const outputBytes = msgpack.encode(output);

        res.json({
          output_data: outputBytes,
          metrics: {
            duration_ms: Date.now() - startTime
          }
        });
      } catch (err) {
        res.json({
          error: {
            code: 'RUNTIME_ERROR',
            message: err.message,
            stack_trace: err.stack
          }
        });
      }
    });

    // Health check
    this.app.get('/health', (req, res) => {
      res.json({ status: 'healthy' });
    });
  }

  async start() {
    // Start HTTP server
    this.app.listen(8080);

    // Register with Constellation
    await this.registerWithConstellation();

    // Start heartbeat
    this.startHeartbeat();
  }

  private async registerWithConstellation() {
    const modules = Array.from(this.modules.values()).map(m => ({
      name: m.spec.name,
      description: m.spec.description,
      input_schema: m.spec.inputSchema,
      output_schema: m.spec.outputSchema
    }));

    await fetch(`${this.config.registryUrl}/register`, {
      method: 'POST',
      body: JSON.stringify({
        service_id: this.config.serviceId,
        service_url: this.config.serviceUrl,
        modules
      })
    });
  }
}
```

### Provider Side (Python SDK)

```python
# constellation-provider-sdk
from constellation_sdk import ConstellationProvider
import msgpack

provider = ConstellationProvider(
    service_id="ml-inference",
    service_url="http://localhost:8080",
    registry_url="http://constellation:9000"
)

@provider.module(
    name="Sentiment",
    input_schema={"text": "String"},
    output_schema={"sentiment": "String", "confidence": "Float"}
)
async def sentiment_analysis(input_data):
    from transformers import pipeline

    classifier = pipeline("sentiment-analysis")
    result = classifier(input_data["text"])[0]

    return {
        "sentiment": result["label"],
        "confidence": result["score"]
    }

if __name__ == "__main__":
    provider.start()
```

---

## Benefits

### 1. **True Polyglot Support**

Not just TypeScript - **any language**:

```
Node.js  → text processing, API integrations, modern JS libs
Python   → ML models, data science, numpy/pandas
Go       → high-performance, concurrency, system integration
Rust     → cryptography, image processing, unsafe operations
.NET     → legacy enterprise systems, SAP integration
Ruby     → legacy Rails apps, scripting
```

### 2. **Dependency Management Solved**

Each service manages its own dependencies:

```dockerfile
# text-processing-service/Dockerfile
FROM node:18
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm install  # All npm dependencies managed here
COPY . .
CMD ["node", "server.js"]
```

No more GraalVM, no JVM dependency hell!

### 3. **Independent Scalability**

```yaml
# Kubernetes scaling
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ml-inference-service
spec:
  replicas: 10  # ML inference needs more instances
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: text-processing-service
spec:
  replicas: 2   # Text processing is lightweight
```

Each module provider scales based on its own needs.

### 4. **Natural Isolation & Security**

- Network boundaries (firewall rules, VPCs)
- Resource limits (CPU/memory per service)
- Separate credentials/secrets
- Blast radius containment

### 5. **Team Autonomy**

```
Team A (Node.js) → text-processing-service
Team B (Python) → ml-inference-service
Team C (Go) → image-processing-service

Each team:
- Uses their preferred language
- Manages their own dependencies
- Deploys independently
- Monitors their own services
```

### 6. **Gradual Migration**

```scala
// Mix Scala and external modules seamlessly
val pipeline = for {
  // Scala module (in-process)
  cleaned <- Trim(input.text)

  // External Node.js module (RPC)
  enriched <- EnrichText(cleaned)

  // External Python module (RPC)
  sentiment <- Sentiment(enriched)

  // Scala module (in-process)
  result <- FormatResult(sentiment)
} yield result
```

Migrate incrementally, no big-bang rewrite!

---

## Trade-offs

| Aspect | Pro | Con |
|--------|-----|-----|
| **Latency** | Acceptable for most use cases | +1-5ms per RPC call |
| **Complexity** | Service boundaries | More operational overhead |
| **Debugging** | Isolated services | Distributed tracing needed |
| **Development** | Language freedom | Multiple repos/builds |
| **Deployment** | Independent scaling | More services to manage |

---

## POC Implementation Plan

### Week 1: Protocol Definition
- Define protobuf/gRPC contracts
- Implement MessagePack serialization
- Basic registry service

### Week 2: Constellation Integration
- ExternalModule wrapper
- Registry client
- End-to-end RPC call

### Week 3: SDKs
- Node.js provider SDK
- Python provider SDK
- Example services

### Week 4: Documentation & Examples
- Protocol docs
- SDK guides
- Sample microservices

---

## Success Criteria

- ✅ Node.js service registers modules with Constellation
- ✅ Constellation pipeline calls Node.js module via RPC
- ✅ Python service provides ML inference module
- ✅ Mixed pipeline (Scala + Node.js + Python modules)
- ✅ <5ms RPC overhead
- ✅ Type safety preserved across RPC boundary

---

## Future Enhancements

- **Streaming**: Support streaming inputs/outputs
- **Batching**: Batch multiple module calls for efficiency
- **Caching**: Cache RPC results based on inputs
- **Async**: Non-blocking module execution
- **Versioning**: Multiple versions of same module
- **Governance**: Module approval workflow
- **Marketplace**: Public module registry

---

## References

- [gRPC](https://grpc.io/)
- [MessagePack](https://msgpack.org/)
- [Service Mesh Patterns](https://www.servicemeshbook.com/)
- [AWS Lambda Function URLs](https://docs.aws.amazon.com/lambda/latest/dg/lambda-urls.html)
- [OpenAI Function Calling](https://platform.openai.com/docs/guides/function-calling)

---

## Conclusion

The Module Provider Protocol transforms Constellation from a monolithic Scala framework into a **polyglot orchestration platform**. Services register modules in their native languages, manage their own dependencies, and scale independently. Constellation focuses on what it does best: type-safe pipeline orchestration.

This is the right architecture for modern distributed systems.
