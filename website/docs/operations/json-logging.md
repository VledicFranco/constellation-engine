---
title: "Structured JSON Logging"
sidebar_position: 4
description: "Configure structured JSON logging for production deployments"
---

# Structured JSON Logging

Constellation Engine uses SLF4J with Logback for logging. The default configuration outputs human-readable plain text, suitable for development. For production deployments, you can switch to structured JSON output for integration with log aggregation systems (ELK, Datadog, Splunk, CloudWatch).

## Default Configuration

The default `logback.xml` in `modules/http-api/src/main/resources/` outputs plain text:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <logger name="io.constellation" level="${CONSTELLATION_LOG_LEVEL:-INFO}" />
  <logger name="org.http4s" level="INFO" />
  <logger name="org.http4s.ember" level="WARN" />

  <root level="WARN">
    <appender-ref ref="CONSOLE" />
  </root>
</configuration>
```

Sample output:

```
14:32:01.123 [io-compute-1] INFO  i.c.e.ConstellationRuntime - Pipeline executed in 42ms
```

## Adding the JSON Encoder Dependency

Add the [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder) to your `build.sbt`:

```scala
libraryDependencies += "net.logstash.logback" % "logstash-logback-encoder" % "7.4"
```

This encoder produces JSON output compatible with the Elastic Common Schema (ECS).

## JSON Logback Configuration

Replace the default `logback.xml` with a JSON-producing configuration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <includeMdcKeyName>executionId</includeMdcKeyName>
      <includeMdcKeyName>pipelineName</includeMdcKeyName>
      <timeZone>UTC</timeZone>
    </encoder>
  </appender>

  <logger name="io.constellation" level="${CONSTELLATION_LOG_LEVEL:-INFO}" />
  <logger name="org.http4s" level="INFO" />
  <logger name="org.http4s.ember" level="WARN" />

  <root level="WARN">
    <appender-ref ref="JSON_CONSOLE" />
  </root>
</configuration>
```

Sample output:

```json
{
  "@timestamp": "2026-02-04T14:32:01.123Z",
  "@version": "1",
  "message": "Pipeline executed in 42ms",
  "logger_name": "io.constellation.execution.ConstellationRuntime",
  "thread_name": "io-compute-1",
  "level": "INFO",
  "executionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "pipelineName": "lead-scoring"
}
```

## Environment-Switched Configuration

Use a single `logback.xml` that switches between plain text (development) and JSON (production) based on an environment variable:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <!-- Plain text appender for development -->
  <appender name="TEXT_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- JSON appender for production -->
  <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <includeMdcKeyName>executionId</includeMdcKeyName>
      <includeMdcKeyName>pipelineName</includeMdcKeyName>
      <timeZone>UTC</timeZone>
    </encoder>
  </appender>

  <logger name="io.constellation" level="${CONSTELLATION_LOG_LEVEL:-INFO}" />
  <logger name="org.http4s" level="INFO" />
  <logger name="org.http4s.ember" level="WARN" />

  <!-- Switch based on CONSTELLATION_LOG_FORMAT: "json" for JSON, anything else for text -->
  <if condition='property("CONSTELLATION_LOG_FORMAT").equalsIgnoreCase("json")'>
    <then>
      <root level="WARN">
        <appender-ref ref="JSON_CONSOLE" />
      </root>
    </then>
    <else>
      <root level="WARN">
        <appender-ref ref="TEXT_CONSOLE" />
      </root>
    </else>
  </if>
</configuration>
```

:::note
The conditional configuration requires the `janino` dependency:
```scala
libraryDependencies += "org.codehaus.janino" % "janino" % "3.1.12"
```
:::

## MDC Fields

Constellation populates these MDC (Mapped Diagnostic Context) fields during pipeline execution:

| Field | Type | Description |
|---|---|---|
| `executionId` | UUID | Unique identifier for each pipeline execution |
| `pipelineName` | String | Name of the pipeline being executed |

These fields appear automatically in JSON log output when using the `LogstashEncoder`. In plain text mode, you can include them in the pattern:

```xml
<pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [exec=%X{executionId}] - %msg%n</pattern>
```

## Log Level Configuration

| Variable | Default | Description |
|---|---|---|
| `CONSTELLATION_LOG_LEVEL` | `INFO` | Log level for `io.constellation` packages |

Valid levels: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`

| Level | Use case |
|---|---|
| `TRACE` | Full pipeline compilation details, DAG traversal steps |
| `DEBUG` | Module execution timing, cache hit/miss, retry attempts |
| `INFO` | Pipeline execution summaries, server lifecycle events |
| `WARN` | Deprecation warnings, fallback activations, slow executions |
| `ERROR` | Pipeline failures, unhandled exceptions |

Set the level at startup:

```bash
CONSTELLATION_LOG_LEVEL=DEBUG make server
```

## Docker / Kubernetes Integration

JSON logging to stdout is the standard pattern for containerized deployments. Container runtimes (Docker, containerd) capture stdout and make it available to log collectors.

### Docker

```dockerfile
ENV CONSTELLATION_LOG_FORMAT=json
ENV CONSTELLATION_LOG_LEVEL=INFO
```

```bash
# View logs
docker logs <container-id>

# Stream logs with jq
docker logs -f <container-id> | jq .
```

### Kubernetes

No sidecar is needed when logging JSON to stdout. Most log collectors (Fluentd, Fluent Bit, Vector) parse JSON lines automatically.

```yaml
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
        - name: constellation
          env:
            - name: CONSTELLATION_LOG_FORMAT
              value: "json"
            - name: CONSTELLATION_LOG_LEVEL
              value: "INFO"
```

## Correlation IDs

Each pipeline execution gets a unique `executionId` (UUID). This ID appears in:

- **Log output** — via MDC (see above)
- **API responses** — in the `X-Execution-Id` response header
- **Execution history** — in the dashboard and `/executions` API

To trace a request end-to-end:

```bash
# Find all logs for a specific execution
docker logs <container> | jq 'select(.executionId == "a1b2c3d4-...")'

# Match with the API response header
curl -v http://localhost:8080/run/my-pipeline \
  -d '{"text": "hello"}' 2>&1 | grep X-Execution-Id
```
