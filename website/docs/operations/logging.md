---
title: "Logging Configuration"
sidebar_position: 10
description: "Configure logging for Constellation Engine in development, production, and library deployments"
---

# Logging Configuration

Constellation Engine uses **Logback** (SLF4J) for structured logging. This guide covers configuration strategies for different deployment scenarios.

---

## Quick Start

### Enable Debug Logging (Development)

```bash
export CONSTELLATION_LOG_LEVEL=DEBUG
make server
```

You'll now see:
- `DEBUG` logs from compilation cache hits/misses
- `INFO` logs for pipeline execution lifecycle
- `WARN` logs for module timeouts
- `ERROR` logs with full stacktraces for failures

### Run with Default Logging (Production)

```bash
make server
```

Default behavior:
- `INFO` level for Constellation packages only
- `WARN` level for everything else (suppresses noise)
- Only important events logged

---

## Environment Variables

### CONSTELLATION_LOG_LEVEL

Controls the log level for all Constellation packages.

| Value | Behavior | Use Case |
|-------|----------|----------|
| `DEBUG` | All debug messages visible | Development, troubleshooting |
| `INFO` | Normal operations (default) | Production, testing |
| `WARN` | Warnings and errors only | Minimal logging |
| `ERROR` | Errors only | Silent operation |

**Example:**
```bash
CONSTELLATION_LOG_LEVEL=DEBUG make server    # Development
CONSTELLATION_LOG_LEVEL=INFO make server     # Production (default)
```

---

## What Gets Logged

### Module Execution (Runtime)

When a module executes, the runtime logs:

| Event | Level | Message | When |
|-------|-------|---------|------|
| **Timeout** | `WARN` | `Module 'X' timed out after 30s` | Module doesn't respond in time |
| **Failure** | `ERROR` | `Module 'X' failed: connection refused` | Module throws exception |

**Example output:**
```
14:32:01.234 [pool-1-thread-2] WARN  io.constellation.Runtime - Module 'FetchUser' timed out after 30s
14:32:02.456 [pool-1-thread-3] ERROR io.constellation.Runtime - Module 'ValidateEmail' failed: Invalid format
```

### Pipeline Execution (ConstellationImpl)

When you run a pipeline, you see:

| Event | Level | Message | Information |
|-------|-------|---------|-------------|
| **Start** | `INFO` | `Executing pipeline 'MyPipeline' [abc123def456]` | Pipeline name + hash prefix |
| **Complete** | `INFO` | `Pipeline 'MyPipeline' [abc123def456] completed: success in 1234ms` | Status + elapsed time |

**Example output:**
```
14:32:00.000 [http-event-loop-1] INFO io.constellation.impl.ConstellationImpl - Executing pipeline 'TextProcessor' [a7f9e2c1d5]
14:32:01.234 [http-event-loop-1] INFO io.constellation.impl.ConstellationImpl - Pipeline 'TextProcessor' [a7f9e2c1d5] completed: success in 1234ms
```

### Compilation (LangCompiler)

When the compiler processes source code:

| Event | Level | Message | Information |
|-------|-------|---------|-------------|
| **Failure** | `ERROR` | `Compilation failed for 'MyPipeline': 2 error(s) — Type mismatch; Unknown module` | Error count + details |

**Example output:**
```
14:32:00.500 [http-event-loop-2] ERROR io.constellation.lang.LangCompiler - Compilation failed for 'test': 2 error(s) — Type mismatch in Add(text, 10); Module 'UndefinedModule' not found
```

### Cache Operations (CachingLangCompiler)

When compilation caching is active and `CONSTELLATION_LOG_LEVEL=DEBUG`:

| Event | Level | Message | Information |
|-------|-------|---------|-------------|
| **Cache Hit** | `DEBUG` | `Compilation cache hit for 'MyPipeline' [src:a7f9e2c1]` | Avoids recompilation |
| **Cache Miss** | `DEBUG` | `Compilation cache miss for 'MyPipeline' — compiling` | Cache miss details |

**Example output (DEBUG only):**
```
14:32:00.001 [http-event-loop-3] DEBUG io.constellation.lang.CachingLangCompiler - Compilation cache hit for 'test' [src:abc12345]
14:32:00.001 [http-event-loop-3] DEBUG io.constellation.lang.CachingLangCompiler - Compilation cache miss for 'new-script' — compiling
```

---

## Configuration Strategies

### Development Setup

**Goal:** See everything for debugging

```bash
export CONSTELLATION_LOG_LEVEL=DEBUG
make server 2>&1 | tee dev.log
```

This gives you:
- Full stacktraces on errors
- Cache hit/miss visibility
- Pipeline timing
- Module-level diagnostics

### Production Setup

**Goal:** Minimal logging, fast performance

```bash
export CONSTELLATION_LOG_LEVEL=WARN
make server
```

Or, start the server and redirect logs to a file:

```bash
make server 2>> constellation.log &
```

This logs only:
- Critical errors (module failures, compilation errors)
- Warnings (timeouts)
- No debug spam

### Testing Setup

**Goal:** Normal logging during test runs

```bash
export CONSTELLATION_LOG_LEVEL=INFO
make test
```

---

## For Library Users

If you're using Constellation Engine as a library in your Scala project:

### 1. Add Logback Dependency

```scala
// build.sbt
libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "2.0.7",
  "ch.qos.logback" % "logback-classic" % "1.4.11"
)
```

### 2. Create Your Own logback.xml

Place this in `src/main/resources/logback.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- Constellation packages - adjust as needed -->
  <logger name="io.constellation" level="${CONSTELLATION_LOG_LEVEL:-INFO}" />

  <!-- Suppress http4s noise -->
  <logger name="org.http4s.ember" level="WARN" />

  <!-- Root logger -->
  <root level="WARN">
    <appender-ref ref="CONSOLE" />
  </root>
</configuration>
```

### 3. Use in Your Code

```scala
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

val constellation = ConstellationImpl.init

val myPipeline = """
  in text: String
  result = Uppercase(text)
  out result
"""

for {
  logger <- Slf4jLogger.create[IO]
  compiled <- constellation.langCompiler.compileIO(myPipeline, "MyPipeline")
  result <- compiled match {
    case Right(output) =>
      logger.info("Compilation succeeded") *> IO.pure(output)
    case Left(errors) =>
      logger.error(s"Compilation failed: ${errors.size} errors") *>
      IO.raiseError(new Exception(s"Failed: ${errors.map(_.message).mkString("; ")}"))
  }
} yield result
```

### 4. Control Logging via Environment

Library users can control logging at runtime:

```bash
# For debugging
CONSTELLATION_LOG_LEVEL=DEBUG java -jar myapp.jar

# For production
CONSTELLATION_LOG_LEVEL=WARN java -jar myapp.jar
```

---

## Advanced Configuration

### Production Logging with File Rotation

For long-running servers, use rolling file appenders:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <!-- Console for immediate visibility -->
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- File with rolling (rotate daily or at 100MB) -->
  <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/constellation.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
      <!-- Rotate daily or when file reaches 100MB -->
      <fileNamePattern>logs/constellation.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
      <maxFileSize>100MB</maxFileSize>
      <maxHistory>30</maxHistory> <!-- Keep 30 days -->
    </rollingPolicy>
    <encoder>
      <pattern>%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- Constellation at INFO in production -->
  <logger name="io.constellation" level="INFO" />
  <logger name="org.http4s.ember" level="WARN" />

  <!-- Root to FILE, console for emergencies -->
  <root level="WARN">
    <appender-ref ref="FILE" />
    <appender-ref ref="CONSOLE" />
  </root>
</configuration>
```

### JSON Structured Logging (Optional)

For log aggregation (ELK, Datadog, Splunk):

```xml
<appender name="LOGSTASH" class="ch.qos.logback.core.rolling.RollingFileAppender">
  <file>logs/constellation.json</file>
  <encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <customFields>{"environment":"production","service":"constellation"}</customFields>
  </encoder>
  <!-- rolling policy same as above -->
</appender>
```

---

## Troubleshooting

### No logs appearing?

1. **Check environment variable:**
   ```bash
   echo $CONSTELLATION_LOG_LEVEL
   ```

2. **Verify logback.xml exists and is on classpath:**
   ```bash
   jar tf constellation-engine.jar | grep logback.xml
   ```

3. **Check if logs are being suppressed:**
   - Root logger level might be too high (set to `WARN` or `ERROR`)
   - Try `CONSTELLATION_LOG_LEVEL=DEBUG` explicitly

### Logs too verbose?

1. **Reduce to WARN:**
   ```bash
   export CONSTELLATION_LOG_LEVEL=WARN
   ```

2. **Suppress specific loggers in logback.xml:**
   ```xml
   <logger name="org.http4s" level="ERROR" />
   <logger name="org.typelevel" level="WARN" />
   ```

### Performance impact?

- DEBUG logging adds ~5-10% overhead
- INFO/WARN logging is negligible (<1%)
- Use INFO in production, DEBUG only for troubleshooting

---

## Related Topics

- [Debugging Guide](../cookbook/debugging.md) — Visual debugging with the dashboard
- [Error Handling](../cookbook/error-handling.md) — Handling failures in pipelines
- [Performance Debugging](../cookbook/debugging.md#8-performance-debugging) — Analyzing slow executions

