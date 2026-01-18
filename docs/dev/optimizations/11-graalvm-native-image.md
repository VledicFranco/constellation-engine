# Optimization 11: GraalVM Native Image

**Priority:** 11 (Lower Priority - Situational)
**Expected Gain:** 90%+ cold-start reduction, lower memory footprint
**Complexity:** High
**Status:** Not Implemented

---

## Problem Statement

JVM-based applications have significant cold-start overhead:

| Metric | JVM | Impact |
|--------|-----|--------|
| Startup time | 2-5 seconds | Slow serverless cold starts |
| Memory footprint | 200-500MB | Higher cloud costs |
| JIT warmup | 10-30 seconds | Initial requests are slow |

For serverless/FaaS deployments where instances scale frequently, this overhead is prohibitive.

---

## Proposed Solution

Use GraalVM Native Image to compile Constellation Engine to a native executable.

### Benefits

| Metric | JVM | Native Image | Improvement |
|--------|-----|--------------|-------------|
| Startup time | 3s | 50ms | 98% faster |
| Memory (idle) | 300MB | 50MB | 83% less |
| Memory (working) | 500MB | 150MB | 70% less |
| First request latency | 500ms* | 10ms | 98% faster |

*After JIT warmup, JVM may be faster for long-running workloads.

---

## Implementation

### Step 1: Add Native Image Plugin

```scala
// project/plugins.sbt

addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.4")
```

```scala
// build.sbt

lazy val httpApi = project
  .in(file("modules/http-api"))
  .enablePlugins(NativeImagePlugin)
  .settings(
    nativeImageOptions ++= Seq(
      "--no-fallback",
      "--enable-http",
      "--enable-https",
      "-H:+ReportExceptionStackTraces",
      "--initialize-at-build-time",
      "-H:ReflectionConfigurationFiles=reflect-config.json",
      "-H:ResourceConfigurationFiles=resource-config.json"
    )
  )
```

### Step 2: Reflection Configuration

GraalVM Native Image requires explicit reflection configuration:

```json
// modules/http-api/src/main/resources/reflect-config.json

[
  {
    "name": "io.constellation.TypeSystem$CStringValue",
    "allDeclaredConstructors": true,
    "allPublicMethods": true
  },
  {
    "name": "io.constellation.TypeSystem$CIntValue",
    "allDeclaredConstructors": true,
    "allPublicMethods": true
  },
  {
    "name": "io.constellation.TypeSystem$CListValue",
    "allDeclaredConstructors": true,
    "allPublicMethods": true
  },
  // ... all CValue subtypes

  {
    "name": "io.constellation.http.ApiModels$ExecuteRequest",
    "allDeclaredConstructors": true,
    "allPublicMethods": true
  },
  // ... all API model classes
]
```

### Step 3: Resource Configuration

Include embedded resources:

```json
// modules/http-api/src/main/resources/resource-config.json

{
  "resources": {
    "includes": [
      {"pattern": "reference.conf"},
      {"pattern": "application.conf"},
      {"pattern": "logback.xml"}
    ]
  }
}
```

### Step 4: Cats-Effect Compatibility

Configure cats-effect for Native Image:

```scala
// Main.scala

import cats.effect.{IO, IOApp}
import cats.effect.unsafe.IORuntime

object Main extends IOApp.Simple {

  // Use a runtime compatible with Native Image
  override protected def runtime: IORuntime = {
    // Native Image doesn't support work-stealing pool well
    // Use fixed thread pool instead
    IORuntime.builder()
      .setCompute(
        ExecutionContext.fromExecutor(
          Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors())
        ),
        () => ()
      )
      .build()
  }

  def run: IO[Unit] = ConstellationServer.serve
}
```

### Step 5: Handle Scala 3 Specifics

Scala 3's metaprogramming features need special handling:

```scala
// For inline/macro code used at runtime
// May need to move to compile-time generation

// Before (problematic for Native Image):
inline def awaitOnInputs[I](using m: Mirror.ProductOf[I]): IO[I] = ...

// After (Native Image friendly):
// Generate code at compile time, use at runtime
def awaitOnInputs[I](schema: InputSchema[I]): IO[I] = ...
```

---

## Build Process

### Development Build

```bash
# Regular JVM build for development
sbt httpApi/compile
sbt httpApi/run
```

### Native Image Build

```bash
# Build native executable
sbt httpApi/nativeImage

# Output location
# modules/http-api/target/native-image/constellation-server

# Run native executable
./modules/http-api/target/native-image/constellation-server
```

### Docker Multi-Stage Build

```dockerfile
# Dockerfile.native

# Stage 1: Build native image
FROM ghcr.io/graalvm/native-image:22.3.0 AS builder

WORKDIR /app
COPY . .

RUN sbt httpApi/nativeImage

# Stage 2: Minimal runtime image
FROM gcr.io/distroless/base-debian11

COPY --from=builder /app/modules/http-api/target/native-image/constellation-server /app/constellation-server

EXPOSE 8080
ENTRYPOINT ["/app/constellation-server"]
```

```bash
# Build minimal container
docker build -f Dockerfile.native -t constellation-server:native .

# Image size comparison
# JVM image: ~500MB
# Native image: ~80MB
```

---

## Runtime Considerations

### Peak Performance

Native Image uses AOT compilation without JIT optimization:

| Workload | JVM (warmed) | Native Image | Winner |
|----------|--------------|--------------|--------|
| Cold start | Slow | Fast | Native |
| First 100 requests | Medium | Fast | Native |
| Sustained load | Fastest | Fast | JVM |

**Recommendation:** Use Native Image for:
- Serverless/FaaS deployments
- CLI tools
- Microservices with frequent scaling

Use JVM for:
- Long-running servers with stable load
- Maximum throughput requirements

### Memory Management

```scala
// Configure Native Image heap
nativeImageOptions ++= Seq(
  "-Xmx256m",  // Max heap
  "-Xms64m",   // Initial heap
  "-H:+PrintGCSummary"  // Debug GC behavior
)
```

---

## Testing Native Image

### Automated Testing

```scala
// NativeImageTest.scala

class NativeImageSpec extends AnyFunSuite {

  test("native image starts successfully") {
    val process = new ProcessBuilder(
      "./target/native-image/constellation-server"
    ).start()

    try {
      // Wait for startup
      Thread.sleep(500)

      // Health check
      val response = requests.get("http://localhost:8080/health")
      assert(response.statusCode == 200)
    } finally {
      process.destroy()
    }
  }

  test("native image handles requests correctly") {
    // Start server, send test requests, verify responses
    ???
  }
}
```

### Native Image Agent

Use the agent to automatically discover reflection/resource requirements:

```bash
# Run with agent to generate config
java -agentlib:native-image-agent=config-output-dir=native-image-config \
  -jar constellation-server.jar

# Exercise all code paths
curl http://localhost:8080/health
curl -X POST http://localhost:8080/execute -d '{"source": "...", "inputs": {}}'

# Generated configs in native-image-config/
# - reflect-config.json
# - resource-config.json
# - proxy-config.json
# - serialization-config.json
```

---

## Known Issues and Workarounds

### Issue 1: Circe JSON Derivation

Circe's automatic derivation uses reflection heavily.

**Workaround:** Use semi-automatic derivation with explicit codecs:

```scala
// Before (problematic):
import io.circe.generic.auto._

// After (Native Image friendly):
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

case class ExecuteRequest(source: String, inputs: Map[String, Json])

object ExecuteRequest {
  implicit val decoder: Decoder[ExecuteRequest] = deriveDecoder
  implicit val encoder: Encoder[ExecuteRequest] = deriveEncoder
}
```

### Issue 2: Http4s Client

Http4s uses Java's ServiceLoader which needs configuration.

**Workaround:** Add to `native-image.properties`:

```properties
Args = --initialize-at-run-time=io.netty
```

### Issue 3: Logging

Logback initialization can fail.

**Workaround:** Use simpler logging or configure properly:

```xml
<!-- logback.xml for Native Image -->
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>
  <root level="INFO">
    <appender-ref ref="STDOUT"/>
  </root>
</configuration>
```

---

## CI/CD Integration

### GitHub Actions

```yaml
# .github/workflows/native-image.yml

name: Native Image Build

on:
  push:
    branches: [main]
  release:
    types: [published]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - uses: graalvm/setup-graalvm@v1
        with:
          version: '22.3.0'
          java-version: '17'
          components: 'native-image'

      - name: Build Native Image
        run: sbt httpApi/nativeImage

      - name: Test Native Image
        run: |
          ./modules/http-api/target/native-image/constellation-server &
          sleep 2
          curl --fail http://localhost:8080/health

      - name: Upload Artifact
        uses: actions/upload-artifact@v3
        with:
          name: constellation-server-native
          path: modules/http-api/target/native-image/constellation-server
```

---

## Implementation Checklist

- [ ] Add sbt-native-image plugin
- [ ] Create reflection configuration for all CValue types
- [ ] Create reflection configuration for API models
- [ ] Create resource configuration
- [ ] Update cats-effect runtime for Native Image compatibility
- [ ] Convert Circe derivation to semi-automatic
- [ ] Create Dockerfile for native image
- [ ] Add CI/CD pipeline for native builds
- [ ] Benchmark cold start and throughput
- [ ] Document deployment procedures

---

## Files to Modify/Create

| File | Changes |
|------|---------|
| `project/plugins.sbt` | Add native-image plugin |
| `build.sbt` | Native image configuration |
| `modules/http-api/src/main/resources/reflect-config.json` | Reflection config |
| `modules/http-api/src/main/resources/resource-config.json` | Resource config |
| `Dockerfile.native` | Multi-stage build |
| `.github/workflows/native-image.yml` | CI/CD |

---

## Related Optimizations

- [Compilation Caching](./01-compilation-caching.md) - Pre-populate cache at build time
- [Module Initialization Pooling](./02-module-initialization-pooling.md) - Pre-warm pools at startup
