# RFC-023: TypeScript/JavaScript Module Support

**Status:** Draft (Proof of Concept)
**Priority:** 3 (Extensibility)
**Author:** Agent 4
**Created:** 2026-02-10

---

## Summary

Add optional support for defining Constellation modules in TypeScript/JavaScript, executed via GraalVM's Polyglot API. This enables rapid module development, hot-reloading, and opens Constellation to the JavaScript/TypeScript ecosystem.

**Key Design Principle:** This is an **optional dependency** - users who don't need TypeScript support won't incur any GraalVM overhead.

---

## Motivation

### Current State

Today, all Constellation modules must be:
- Written in Scala
- Compiled to JVM bytecode
- Deployed with application restart

This creates friction for:
1. **Rapid prototyping** - Scala compile cycles are slow for experimentation
2. **Plugin systems** - Users can't upload custom modules without deploying Scala code
3. **Ecosystem access** - Millions of TypeScript developers can't contribute modules
4. **Hot-reloading** - Module changes require full application restart

### Proposed State

With TypeScript module support:
```typescript
// modules/text-processor.ts
export const metadata = {
  name: "TextProcessor",
  description: "Process text with JavaScript",
  version: "1.0.0"
};

export async function execute(input: { text: string }) {
  return {
    processed: input.text.toUpperCase().trim(),
    wordCount: input.text.split(/\s+/).length
  };
}
```

```constellation
# Use TypeScript module seamlessly
result = TextProcessor(text)
out result[processed, wordCount]
```

### Use Cases

1. **Development Velocity** - Prototype modules in TypeScript, promote to Scala when stable
2. **User Plugins** - HTTP API to upload TypeScript modules for custom logic
3. **Community Marketplace** - npm-style module repository
4. **Experimentation** - Data scientists can write modules without Scala knowledge

---

## Architecture: Optional & Pluggable

### Core Principle: Zero Overhead When Unused

The TypeScript support is implemented as a **separate optional module** that users explicitly opt into:

```
modules/
├── core/                    # No TS dependencies
├── runtime/                 # No TS dependencies
├── http-api/               # No TS dependencies
└── ts-runtime/             # NEW: Optional TS module loader
    ├── build.sbt           # Depends on GraalVM
    └── src/main/scala/
        └── io/constellation/tsruntime/
            ├── TSModuleLoader.scala
            ├── TSModule.scala
            └── GraalVMContext.scala
```

### Dependency Graph

```
┌─────────────────────────────────────────┐
│  Constellation Core                     │
│  (No TS dependencies)                   │
└────────────────┬────────────────────────┘
                 │
        ┌────────┴────────┐
        │                 │
        ▼                 ▼
┌───────────────┐  ┌─────────────────────┐
│  http-api     │  │  ts-runtime         │
│  (optional)   │  │  (optional)         │
│               │  │  + GraalVM Polyglot │
└───────────────┘  └─────────────────────┘
```

**Users can:**
- Use Constellation without TypeScript support (no GraalVM needed)
- Add `ts-runtime` dependency to enable TypeScript modules
- Use TypeScript with or without HTTP API

---

## Proposed Solution

### 1. Hot vs Cold TypeScript Modules

Following the hot/cold pipeline pattern, TypeScript modules come in two flavors:

#### **Hot Modules** (Pre-compiled, Startup)

```scala
// At application startup
import io.constellation.tsruntime.TSModuleLoader

val tsLoader = TSModuleLoader.init(
  modulesDir = Path.of("modules/typescript/"),
  mode = TSModuleLoader.Hot
)

for {
  modules <- tsLoader.loadAll()
  _ <- modules.traverse(constellation.setModule)
} yield ()
```

**Characteristics:**
- Loaded from filesystem at startup
- GraalVM AOT-compiled for native performance
- ~1ms execution overhead (same as Scala modules)
- Used for production modules

#### **Cold Modules** (On-Demand, Dynamic)

```scala
// Via HTTP API or programmatically
val source = """
  export async function execute(input) {
    return { result: input.value * 2 };
  }
"""

for {
  module <- tsLoader.loadColdModule("Doubler", source)
  _ <- constellation.setModule(module)
} yield ()
```

**Characteristics:**
- Uploaded via HTTP or loaded dynamically
- JIT-compiled on first execution (~50ms first call)
- ~2-5ms execution overhead after warmup
- Hot-reloadable without restart
- Used for plugins, development, experimentation

### 2. TypeScript Module Format

#### **Minimal Module**

```typescript
// Simple function export
export function execute(input: any): any {
  return { result: input.value * 2 };
}
```

#### **Full Module with Metadata**

```typescript
// modules/text-processor.ts
export const metadata = {
  name: "TextProcessor",
  description: "Process text with various operations",
  version: "1.0.0",
  inputSchema: {
    text: "string",
    operation: "string"
  },
  outputSchema: {
    result: "string",
    metadata: {
      length: "number",
      wordCount: "number"
    }
  }
};

export async function execute(input: {
  text: string,
  operation: string
}): Promise<{
  result: string,
  metadata: { length: number, wordCount: number }
}> {
  let result = input.text;

  switch (input.operation) {
    case "uppercase":
      result = result.toUpperCase();
      break;
    case "lowercase":
      result = result.toLowerCase();
      break;
    case "trim":
      result = result.trim();
      break;
  }

  return {
    result,
    metadata: {
      length: result.length,
      wordCount: result.split(/\s+/).length
    }
  };
}
```

#### **Module with npm Dependencies** (Future)

```typescript
// modules/markdown-processor.ts
import * as marked from 'marked';  // npm package

export async function execute(input: { markdown: string }) {
  return {
    html: marked.parse(input.markdown)
  };
}
```

### 3. Type Marshalling (CValue ↔ JavaScript)

```scala
// Automatic conversion between Constellation and JavaScript types
object TypeMarshaller {
  def toJS(value: CValue): Value = value match {
    case CValue.CInt(n)      => context.asValue(n)
    case CValue.CString(s)   => context.asValue(s)
    case CValue.CBool(b)     => context.asValue(b)
    case CValue.CFloat(f)    => context.asValue(f)
    case CValue.CList(items) => context.asValue(items.map(toJS))
    case CValue.CRecord(fields) =>
      val obj = context.eval("js", "({})")
      fields.foreach { case (k, v) =>
        obj.putMember(k, toJS(v))
      }
      obj
    case CValue.COption(Some(v)) => toJS(v)
    case CValue.COption(None)    => context.asValue(null)
  }

  def fromJS(value: Value): CValue = {
    if (value.isNumber) CValue.CInt(value.asInt())
    else if (value.isString) CValue.CString(value.asString())
    else if (value.isBoolean) CValue.CBool(value.asBoolean())
    else if (value.hasArrayElements) {
      val items = (0 until value.getArraySize.toInt).map { i =>
        fromJS(value.getArrayElement(i))
      }
      CValue.CList(items.toList)
    }
    else if (value.hasMembers) {
      val fields = value.getMemberKeys.asScala.map { key =>
        key -> fromJS(value.getMember(key))
      }.toMap
      CValue.CRecord(fields)
    }
    else throw new IllegalArgumentException(s"Cannot convert JS value: $value")
  }
}
```

### 4. HTTP API Integration (Optional)

#### **Upload Cold Module**

```bash
POST /modules/typescript
Content-Type: application/json

{
  "name": "CustomProcessor",
  "source": "export function execute(input) { return { result: input.value * 2 }; }",
  "hot": false
}
```

Response:
```json
{
  "status": "registered",
  "name": "CustomProcessor",
  "type": "cold",
  "firstCallLatency": "~50ms",
  "warmedUpLatency": "~2ms"
}
```

#### **Reload Cold Module**

```bash
PUT /modules/typescript/CustomProcessor
Content-Type: application/json

{
  "source": "export function execute(input) { return { result: input.value * 3 }; }"
}
```

Response:
```json
{
  "status": "reloaded",
  "name": "CustomProcessor",
  "previousCalls": 42
}
```

#### **List TypeScript Modules**

```bash
GET /modules/typescript
```

Response:
```json
{
  "modules": [
    {
      "name": "TextProcessor",
      "type": "hot",
      "version": "1.0.0",
      "loaded": "2026-02-10T08:00:00Z",
      "calls": 1523,
      "avgLatency": "1.2ms"
    },
    {
      "name": "CustomProcessor",
      "type": "cold",
      "uploaded": "2026-02-10T09:30:00Z",
      "calls": 42,
      "avgLatency": "2.5ms"
    }
  ]
}
```

---

## Implementation Plan (POC)

### Phase 1: Core Infrastructure (Week 1)

**Goal:** Get "Hello World" TypeScript module working

1. **Create `ts-runtime` module**
   ```scala
   // build.sbt
   lazy val tsRuntime = project
     .in(file("modules/ts-runtime"))
     .settings(
       name := "constellation-ts-runtime",
       libraryDependencies ++= Seq(
         "org.graalvm.polyglot" % "polyglot" % "23.1.0",
         "org.graalvm.polyglot" % "js" % "23.1.0"
       )
     )
     .dependsOn(runtime)
   ```

2. **Implement `TSModuleLoader`**
   ```scala
   class TSModuleLoader(
     context: Context,
     constellation: Constellation
   ) {
     def loadHotModule(path: Path): IO[Module.Uninitialized]
     def loadColdModule(name: String, source: String): IO[Module.Uninitialized]
   }
   ```

3. **Implement type marshalling**
   ```scala
   object TypeMarshaller {
     def toJS(value: CValue): Value
     def fromJS(value: Value): CValue
   }
   ```

4. **Write tests**
   ```scala
   test("load simple TypeScript module") {
     val source = """
       export function execute(input) {
         return { result: input.value * 2 };
       }
     """

     for {
       module <- loader.loadColdModule("Doubler", source)
       _ <- constellation.setModule(module)
       result <- constellation.run(...)
     } yield {
       result shouldBe CValue.CRecord(Map("result" -> CValue.CInt(4)))
     }
   }
   ```

**Success Criteria:**
- ✅ Can load TypeScript module from string
- ✅ Can execute module and get result
- ✅ Type marshalling works for primitives and records

### Phase 2: Hot vs Cold (Week 2)

**Goal:** Implement hot/cold distinction with performance benchmarks

1. **Hot module loader**
   ```scala
   def loadHotModules(dir: Path): IO[List[Module.Uninitialized]] = {
     for {
       files <- listTSFiles(dir)
       modules <- files.parTraverse { file =>
         for {
           source <- IO(Files.readString(file))
           // AOT compile for performance
           compiled <- compileAOT(source)
           module <- createHotModule(file, compiled)
         } yield module
       }
     } yield modules
   }
   ```

2. **Cold module loader with hot-reload**
   ```scala
   def reloadColdModule(name: String, source: String): IO[Unit] = {
     for {
       _ <- removeModule(name)
       module <- loadColdModule(name, source)
       _ <- constellation.setModule(module)
     } yield ()
   }
   ```

3. **Benchmarks**
   ```scala
   benchmark("hot module execution") {
     // Target: <1ms per call
   }

   benchmark("cold module first call") {
     // Target: <50ms
   }

   benchmark("cold module warmed up") {
     // Target: <5ms
   }
   ```

**Success Criteria:**
- ✅ Hot modules execute in <1ms
- ✅ Cold modules warm up within 5 calls
- ✅ Can reload cold modules without restart

### Phase 3: HTTP API Integration (Week 3)

**Goal:** Enable dynamic module upload via HTTP

1. **Add routes to `ConstellationRoutes`**
   ```scala
   case req @ POST -> Root / "modules" / "typescript" =>
     // Upload cold module

   case req @ PUT -> Root / "modules" / "typescript" / name =>
     // Reload cold module

   case GET -> Root / "modules" / "typescript" =>
     // List TypeScript modules
   ```

2. **Module metadata tracking**
   ```scala
   case class TSModuleMetadata(
     name: String,
     moduleType: TSModuleType,
     loadedAt: Instant,
     callCount: Long,
     avgLatency: FiniteDuration
   )
   ```

3. **Security: Sandboxing**
   ```scala
   val context = Context.newBuilder("js")
     .allowAllAccess(false)           // No filesystem/network by default
     .option("js.timer", "false")     // No setTimeout/setInterval
     .option("js.console", "true")    // Allow console.log for debugging
     .resourceLimits(
       maxHeapMemory = 100 * 1024 * 1024,  // 100MB heap limit
       maxCpuTime = Duration.ofSeconds(5)   // 5s CPU time limit
     )
     .build()
   ```

**Success Criteria:**
- ✅ Can upload TypeScript module via HTTP
- ✅ Can reload module without restart
- ✅ Modules are sandboxed (no filesystem access)
- ✅ Memory/CPU limits enforced

### Phase 4: Documentation & Examples (Week 4)

1. **Create example TypeScript modules**
   - Text processing
   - JSON transformation
   - HTTP client wrapper
   - Data validation

2. **Update documentation**
   - Add guide: "Writing TypeScript Modules"
   - Add guide: "Hot vs Cold Modules"
   - Update HTTP API docs

3. **Create sample application**
   ```
   example-app-ts/
   ├── modules/
   │   └── text-processor.ts
   └── pipelines/
       └── text-pipeline.cst
   ```

**Success Criteria:**
- ✅ 5+ working example modules
- ✅ Complete documentation
- ✅ Sample app demonstrates usage

---

## Making It Optional: Packaging Strategy

### Build Variants

#### **Standard Distribution** (No TypeScript)

```bash
# build.sbt
lazy val constellationStandard = project
  .aggregate(core, runtime, httpApi)
  .settings(
    name := "constellation-engine"
  )

# Usage
libraryDependencies += "io.github.vledicfranco" %% "constellation-engine" % "0.7.0"
```

**Size:** ~15MB JAR

#### **TypeScript Distribution** (With GraalVM)

```bash
# build.sbt
lazy val constellationWithTS = project
  .aggregate(core, runtime, httpApi, tsRuntime)
  .settings(
    name := "constellation-engine-with-typescript"
  )

# Usage
libraryDependencies += "io.github.vledicfranco" %% "constellation-engine-with-typescript" % "0.7.0"
```

**Size:** ~115MB JAR (includes GraalVM Polyglot)

#### **À la Carte** (Explicit Dependencies)

```scala
// User controls exactly what they include
libraryDependencies ++= Seq(
  "io.github.vledicfranco" %% "constellation-core" % "0.7.0",
  "io.github.vledicfranco" %% "constellation-runtime" % "0.7.0",
  "io.github.vledicfranco" %% "constellation-http-api" % "0.7.0",
  "io.github.vledicfranco" %% "constellation-ts-runtime" % "0.7.0"  // Optional!
)
```

### Docker Images

```dockerfile
# Standard image (no TypeScript)
FROM eclipse-temurin:17-jre
COPY constellation-engine.jar /app/
ENTRYPOINT ["java", "-jar", "/app/constellation-engine.jar"]

# TypeScript-enabled image
FROM ghcr.io/graalvm/graalvm-ce:latest
COPY constellation-engine-with-typescript.jar /app/
ENTRYPOINT ["java", "-jar", "/app/constellation-engine-with-typescript.jar"]
```

### Feature Detection

```scala
// Runtime detection of TypeScript support
object Features {
  def hasTypeScriptSupport: Boolean =
    Try(Class.forName("io.constellation.tsruntime.TSModuleLoader")).isSuccess
}

// Graceful degradation
if (Features.hasTypeScriptSupport) {
  logger.info("TypeScript module support enabled")
  tsLoader.loadHotModules(Path.of("modules/ts/"))
} else {
  logger.info("TypeScript module support not available (ts-runtime not in classpath)")
}
```

---

## Trade-offs & Alternatives

### Trade-offs

| Aspect | Pro | Con |
|--------|-----|-----|
| **Distribution Size** | Users without TS pay no cost | TS distribution is 100MB larger |
| **Type Safety** | Enables rapid prototyping | No compile-time type checking |
| **Performance** | Hot modules = native speed | Cold modules have ~2-5ms overhead |
| **Ecosystem** | Access to npm ecosystem | Need to vet security of TS modules |
| **Complexity** | Plugin system for users | More moving parts to maintain |

### Alternative 1: Lua Instead of TypeScript

**Pros:**
- Smaller runtime (~500KB vs ~100MB)
- Simpler embedding
- Good performance

**Cons:**
- Much smaller ecosystem than JavaScript/TypeScript
- Less familiar to developers
- No npm packages

**Verdict:** TypeScript has better ecosystem and developer familiarity.

### Alternative 2: WebAssembly Modules

**Pros:**
- Near-native performance
- Language-agnostic (compile from any language)
- Security sandboxing built-in

**Cons:**
- More complex integration
- Harder to debug
- Less accessible for rapid development

**Verdict:** WASM could be future enhancement, but TS is better for POC.

### Alternative 3: Scala.js Bridge

**Pros:**
- Full type safety
- No GraalVM dependency

**Cons:**
- Complex build pipeline
- Poor npm ecosystem support
- Scala.js has limitations

**Verdict:** Too complex for POC, harder for users to adopt.

---

## Security Considerations

### Sandboxing

```scala
val context = Context.newBuilder("js")
  // Disable dangerous features
  .allowAllAccess(false)
  .allowIO(IOAccess.NONE)            // No filesystem access
  .allowEnvironmentAccess(EnvironmentAccess.NONE)
  .allowCreateThread(false)
  .allowNativeAccess(false)

  // Resource limits
  .resourceLimits(
    ResourceLimits.newBuilder()
      .maxHeapMemory(100 * 1024 * 1024)    // 100MB heap
      .maxCpuTime(Duration.ofSeconds(5))    // 5s CPU time
      .statementLimit(1_000_000)            // 1M statements
  )

  // Allowed features
  .option("js.timer", "false")       // No setTimeout
  .option("js.console", "true")      // Allow console.log
  .option("js.load", "false")        // No dynamic require()
  .build()
```

### Code Review for Hot Modules

Hot modules (loaded at startup) should be:
- ✅ Stored in version control
- ✅ Code-reviewed like Scala modules
- ✅ Scanned for security issues
- ✅ Not user-uploaded

Cold modules (dynamic) should be:
- ⚠️ Treated as untrusted code
- ⚠️ Sandboxed with resource limits
- ⚠️ Rate-limited per user/IP
- ⚠️ Logged for audit trail

### Denial of Service Protection

```scala
// Rate limiting for module uploads
case req @ POST -> Root / "modules" / "typescript" =>
  for {
    clientIP <- getClientIP(req)
    allowed <- rateLimiter.tryAcquire(clientIP, cost = 1)
    response <- if (allowed) {
      // Process upload
    } else {
      TooManyRequests("Rate limit exceeded: max 10 module uploads per minute")
    }
  } yield response
```

---

## Success Criteria (POC)

### Must Have (Minimum Viable POC)

- ✅ Load TypeScript module from string
- ✅ Execute TypeScript module and return result
- ✅ Type marshalling for primitives, records, lists
- ✅ Hot vs cold module distinction
- ✅ HTTP API to upload cold modules
- ✅ Basic sandboxing (no filesystem access)
- ✅ Documentation with examples
- ✅ Optional dependency (doesn't bloat standard distribution)

### Should Have (Enhanced POC)

- ✅ Hot-reload cold modules without restart
- ✅ Performance benchmarks (hot: <1ms, cold: <5ms warmed up)
- ✅ Memory/CPU resource limits
- ✅ Module metadata tracking (calls, latency)
- ✅ List TypeScript modules endpoint
- ✅ 5+ working example modules

### Could Have (Future Enhancements)

- ⏸️ npm package support (install dependencies)
- ⏸️ TypeScript → Constellation type generation
- ⏸️ Source maps for debugging
- ⏸️ TypeScript compilation (currently compile to JS first)
- ⏸️ Module marketplace/registry
- ⏸️ Async/Promise support
- ⏸️ Streaming support

### Won't Have (Explicitly Out of Scope for POC)

- ❌ Production-grade security audit
- ❌ WebAssembly support
- ❌ Multiple language runtimes (Python, Ruby, etc.)
- ❌ Distributed module execution
- ❌ Module versioning system

---

## Timeline & Effort Estimate

### POC Timeline: 4 Weeks

| Week | Focus | Deliverables |
|------|-------|--------------|
| **Week 1** | Core Infrastructure | TSModuleLoader, type marshalling, basic tests |
| **Week 2** | Hot/Cold Implementation | Performance optimization, benchmarks |
| **Week 3** | HTTP API Integration | Upload/reload endpoints, sandboxing |
| **Week 4** | Documentation & Polish | Examples, docs, sample app |

### Effort: 1 Developer, Full-Time

- **Week 1-2:** Core engineering (40 hours)
- **Week 3:** API integration (20 hours)
- **Week 4:** Documentation (20 hours)
- **Total:** ~80 hours for POC

### Post-POC: Production Readiness

If POC is successful, production readiness requires:
- Security audit (1-2 weeks)
- Performance optimization (1 week)
- Integration tests (1 week)
- User feedback iteration (2-4 weeks)

**Total to production:** ~6-8 weeks after POC

---

## Open Questions

1. **Should we support npm dependencies in POC?**
   - Pro: Enables rich ecosystem
   - Con: Adds complexity, security concerns
   - **Recommendation:** Not in POC, add in v2

2. **Should TypeScript modules be able to call other Constellation modules?**
   - Pro: Composition is powerful
   - Con: Complex to implement
   - **Recommendation:** Not in POC, interesting future feature

3. **How do we handle async/await in TypeScript?**
   - TypeScript uses Promises, Constellation uses IO
   - Need Promise ↔ IO bridge
   - **Recommendation:** Block on Promise.then() in POC, optimize later

4. **Should we compile TypeScript to JavaScript on the server, or require pre-compilation?**
   - Server-side compilation: Easier for users, more overhead
   - Pre-compilation: Faster, but extra build step
   - **Recommendation:** Require pre-compilation for POC (users run `tsc` first)

5. **What's the upgrade path from cold → hot modules?**
   - When should a cold module "graduate" to hot?
   - **Recommendation:** Manual promotion - user copies to hot modules directory

---

## Next Steps

1. **Review this RFC** - Get feedback from stakeholders
2. **Prototype Week 1** - Build core TSModuleLoader to validate feasibility
3. **Performance benchmarks** - Verify hot modules achieve <1ms target
4. **Security review** - Validate GraalVM sandboxing is sufficient
5. **Go/No-Go decision** - After Week 1, decide if POC is viable

---

## References

- [GraalVM Polyglot API](https://www.graalvm.org/latest/reference-manual/embed-languages/)
- [GraalVM JavaScript Runtime](https://www.graalvm.org/javascript/)
- [Constellation Module System](../website/docs/llm/patterns/module-development.md)
- [Hot vs Cold Pipelines](../website/docs/llm/foundations/execution-modes.md)

---

## Appendix: Example TypeScript Modules

### Example 1: Simple Text Transform

```typescript
// modules/uppercase.ts
export function execute(input: { text: string }) {
  return { result: input.text.toUpperCase() };
}
```

### Example 2: JSON Transformation

```typescript
// modules/json-transform.ts
export async function execute(input: {
  data: object,
  transformations: string[]
}) {
  let result = { ...input.data };

  for (const transform of input.transformations) {
    if (transform === "flatten") {
      result = flattenObject(result);
    } else if (transform === "camelCase") {
      result = toCamelCase(result);
    }
  }

  return { transformed: result };
}

function flattenObject(obj: any, prefix = ''): any {
  return Object.keys(obj).reduce((acc, key) => {
    const pre = prefix.length ? prefix + '.' : '';
    if (typeof obj[key] === 'object' && obj[key] !== null) {
      Object.assign(acc, flattenObject(obj[key], pre + key));
    } else {
      acc[pre + key] = obj[key];
    }
    return acc;
  }, {});
}

function toCamelCase(obj: any): any {
  // ... implementation
}
```

### Example 3: HTTP Client Wrapper

```typescript
// modules/http-client.ts
export const metadata = {
  name: "HttpClient",
  description: "Make HTTP requests",
  version: "1.0.0"
};

export async function execute(input: {
  url: string,
  method: string,
  headers?: Record<string, string>,
  body?: any
}) {
  const response = await fetch(input.url, {
    method: input.method,
    headers: input.headers,
    body: input.body ? JSON.stringify(input.body) : undefined
  });

  const data = await response.json();

  return {
    status: response.status,
    headers: Object.fromEntries(response.headers.entries()),
    body: data
  };
}
```

### Example 4: Data Validation

```typescript
// modules/validator.ts
export async function execute(input: {
  data: any,
  schema: {
    type: string,
    required?: string[],
    properties?: Record<string, any>
  }
}) {
  const errors: string[] = [];

  if (input.schema.type === "object") {
    if (typeof input.data !== "object") {
      errors.push(`Expected object, got ${typeof input.data}`);
    }

    if (input.schema.required) {
      for (const field of input.schema.required) {
        if (!(field in input.data)) {
          errors.push(`Missing required field: ${field}`);
        }
      }
    }

    if (input.schema.properties) {
      for (const [field, schema] of Object.entries(input.schema.properties)) {
        if (field in input.data) {
          // Recursive validation
          const fieldResult = await execute({
            data: input.data[field],
            schema: schema as any
          });
          errors.push(...fieldResult.errors);
        }
      }
    }
  }

  return {
    valid: errors.length === 0,
    errors
  };
}
```

---

## Conclusion

TypeScript module support enables Constellation to tap into the massive JavaScript/TypeScript ecosystem while maintaining its core strengths of type safety and performance. By making it **optional and pluggable**, we avoid forcing GraalVM overhead on users who don't need it.

The hot/cold module pattern provides the best of both worlds:
- **Hot modules** for production (native performance)
- **Cold modules** for development and plugins (flexibility)

This POC will validate the technical feasibility and user demand before committing to production-grade implementation.
