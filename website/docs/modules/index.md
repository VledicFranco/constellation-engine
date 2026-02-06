---
title: "Overview"
sidebar_position: 1
description: "Overview of official and community modules that extend Constellation Engine with caching, metrics, and more."
---

# Module Ecosystem

Constellation Engine provides a modular architecture that allows you to extend the core runtime with additional capabilities. This page provides an overview of the module ecosystem, including official modules, community contributions, and guidance for developing your own modules.

## What Are Modules?

In Constellation Engine, "modules" refer to two related concepts:

1. **Pipeline Modules** - Functions you register with the runtime that can be called from constellation-lang pipelines (e.g., `Uppercase`, `FetchUser`, `ComputeScore`)

2. **Optional Library Modules** - SBT library dependencies that extend the core runtime with integrations for external systems (e.g., cache backends, monitoring, authentication)

This page focuses on **Optional Library Modules**. For information on creating pipeline modules, see [Creating Modules](/docs/api-reference/programmatic-api#creating-modules).

---

## Official Modules

Constellation Engine ships a set of optional, first-party modules that extend the core runtime with production-ready integrations. Each module lives in its own SBT project under `modules/` and can be added as a dependency independently.

### Available Modules

| Module | Artifact | Description | Status |
|--------|----------|-------------|--------|
| [Memcached Cache](./cache-memcached.md) | `constellation-cache-memcached` | Distributed caching via Memcached (spymemcached) | Stable |

### Planned Modules

The following modules are planned for future releases:

| Module | Description | Target Release |
|--------|-------------|----------------|
| `cache-redis` | Distributed caching via Redis (Lettuce client) | v1.1 |
| `cache-caffeine` | High-performance local caching (Caffeine) | v1.1 |
| `metrics-prometheus` | Prometheus metrics exporter | v1.2 |
| `metrics-datadog` | Datadog metrics integration | v1.2 |
| `tracing-opentelemetry` | OpenTelemetry distributed tracing | v1.2 |

:::info Feature Requests
Have a module you'd like to see? [Open an issue](https://github.com/VledicFranco/constellation-engine/issues/new) with the `enhancement` label.
:::

---

## When to Use Optional Modules vs. SPI

Constellation provides two extension mechanisms for integrating with external systems:

### SPI Traits (Service Provider Interface)

The core runtime defines SPI traits that establish contracts for pluggable functionality:

- `CacheBackend` - Caching storage and retrieval
- `CacheSerde` - Serialization/deserialization for cache values
- `DistributedCacheBackend` - Extended interface for distributed caches

**Use SPI traits when:**
- You need a backend not covered by official modules
- You want tighter integration with your existing infrastructure
- You have specific performance or behavioral requirements
- You're integrating with proprietary systems

**Example: Custom cache backend**
```scala
import io.constellation.cache.{CacheBackend, CacheStats}
import cats.effect.IO
import scala.concurrent.duration.FiniteDuration

class MyCustomCacheBackend extends CacheBackend[IO] {
  def get[A](key: String): IO[Option[A]] = ???
  def set[A](key: String, value: A, ttl: Option[FiniteDuration]): IO[Unit] = ???
  def delete(key: String): IO[Unit] = ???
  def stats: IO[CacheStats] = ???
}
```

See [CacheBackend SPI Guide](/docs/integrations/cache-backend) for complete documentation.

### Optional Modules

Optional modules are ready-made implementations of SPI traits, published as separate libraries:

**Use optional modules when:**
- Your stack uses a supported backend (Memcached, etc.)
- You want production-ready code without implementing SPI yourself
- You prefer minimal configuration over custom behavior
- You value community-tested implementations

**Example: Using Memcached module**
```scala
import io.constellation.cache.memcached.{MemcachedCacheBackend, MemcachedConfig}

// One line to get a production-ready cache backend
MemcachedCacheBackend.resource(MemcachedConfig.single()).use { backend =>
  // Use backend with ConstellationBuilder
}
```

---

## Adding a Module to Your Project

### Step 1: Add the Dependency

Add the module artifact to your `build.sbt`:

```scala
libraryDependencies += "io.github.vledicfranco" %% "constellation-cache-memcached" % "0.4.0"
```

### Step 2: Import and Configure

Each module provides factory methods for creating configured instances:

```scala
import io.constellation.cache.memcached.{MemcachedCacheBackend, MemcachedConfig}

// Configure the backend
val config = MemcachedConfig(
  addresses = List("cache1.example.com:11211", "cache2.example.com:11211"),
  operationTimeout = 2500.millis,
  keyPrefix = "myapp"
)

// Create a resource-managed instance
MemcachedCacheBackend.resource(config).use { backend =>
  // backend is ready to use
}
```

### Step 3: Integrate with Constellation

Use the module with `ConstellationBuilder`:

```scala
import io.constellation.impl.ConstellationImpl

MemcachedCacheBackend.resource(config).use { cacheBackend =>
  for {
    constellation <- ConstellationImpl.builder()
      .withCache(cacheBackend)
      .build()
    // Pipeline execution now uses Memcached for caching
  } yield ()
}
```

---

## Module Dependency Graph

All optional modules depend on `constellation-runtime` (where the SPI traits live) and never introduce circular dependencies:

```
constellation-core
       │
       ▼
constellation-runtime
       │
       ├── CacheBackend (SPI trait)
       ├── CacheSerde (SPI trait)
       └── DistributedCacheBackend (SPI trait)
       │
       ▼
┌──────────────────────────────────────────────┐
│           Optional Modules                    │
├──────────────────────────────────────────────┤
│  cache-memcached    MemcachedCacheBackend    │
│  cache-redis        (planned)                │
│  cache-caffeine     (planned)                │
│  metrics-prometheus (planned)                │
│  tracing-otel       (planned)                │
└──────────────────────────────────────────────┘
```

---

## Finding Community Modules

Community members may publish their own Constellation Engine modules. Here's how to find them:

### GitHub Search

Search GitHub for repositories tagged with `constellation-engine`:
- [GitHub: constellation-engine topic](https://github.com/topics/constellation-engine)

### Maven Central

Search Maven Central for packages with `constellation` in the group or artifact ID:
- [Maven Central: constellation](https://search.maven.org/search?q=constellation)

### Community Resources

- **GitHub Discussions**: Ask about community modules in [GitHub Discussions](https://github.com/VledicFranco/constellation-engine/discussions)
- **Discord**: Join the community Discord (link in repository README)

:::caution Third-Party Modules
Community modules are not officially supported. Review the source code and check maintenance status before using in production.
:::

---

## Module Development Quick Start

Want to create your own module? Here's a quick guide to get started.

### Step 1: Create a New SBT Project

```scala
// build.sbt
lazy val myModule = project
  .in(file("my-module"))
  .settings(
    name := "constellation-my-module",
    libraryDependencies ++= Seq(
      "io.github.vledicfranco" %% "constellation-runtime" % "0.4.0",
      // Your module's dependencies
    )
  )
```

### Step 2: Implement the SPI Trait

Choose which SPI trait your module implements:

```scala
package io.constellation.mymodule

import io.constellation.cache.{CacheBackend, CacheStats}
import cats.effect.{IO, Resource}
import scala.concurrent.duration.FiniteDuration

final case class MyModuleConfig(
  host: String = "localhost",
  port: Int = 1234,
  timeout: FiniteDuration = 5.seconds
)

object MyModuleConfig {
  def default(): MyModuleConfig = MyModuleConfig()
}

class MyCacheBackend(config: MyModuleConfig) extends CacheBackend[IO] {

  override def get[A](key: String): IO[Option[A]] = {
    // Implement retrieval logic
    ???
  }

  override def set[A](key: String, value: A, ttl: Option[FiniteDuration]): IO[Unit] = {
    // Implement storage logic
    ???
  }

  override def delete(key: String): IO[Unit] = {
    // Implement deletion logic
    ???
  }

  override def stats: IO[CacheStats] = {
    // Implement statistics retrieval
    IO.pure(CacheStats(hits = 0, misses = 0, evictions = 0, size = 0, maxSize = None))
  }
}

object MyCacheBackend {
  /** Resource-managed factory for proper lifecycle management */
  def resource(config: MyModuleConfig): Resource[IO, MyCacheBackend] = {
    Resource.make(
      IO(new MyCacheBackend(config))  // Acquire: create backend
    )(backend =>
      IO.unit  // Release: cleanup (close connections, etc.)
    )
  }
}
```

### Step 3: Add Configuration Support

Support environment variables and programmatic configuration:

```scala
object MyModuleConfig {
  def default(): MyModuleConfig = MyModuleConfig()

  def fromEnv(): MyModuleConfig = MyModuleConfig(
    host = sys.env.getOrElse("MY_MODULE_HOST", "localhost"),
    port = sys.env.getOrElse("MY_MODULE_PORT", "1234").toInt,
    timeout = sys.env.getOrElse("MY_MODULE_TIMEOUT_MS", "5000").toInt.millis
  )
}
```

### Step 4: Write Tests

```scala
import org.scalatest.funsuite.AnyFunSuite
import cats.effect.unsafe.implicits.global

class MyCacheBackendSpec extends AnyFunSuite {

  test("get returns None for missing keys") {
    MyCacheBackend.resource(MyModuleConfig.default()).use { backend =>
      for {
        result <- backend.get[String]("nonexistent")
      } yield assert(result.isEmpty)
    }.unsafeRunSync()
  }

  test("set and get round-trip") {
    MyCacheBackend.resource(MyModuleConfig.default()).use { backend =>
      for {
        _      <- backend.set("key", "value", None)
        result <- backend.get[String]("key")
      } yield assert(result.contains("value"))
    }.unsafeRunSync()
  }
}
```

### Step 5: Document Your Module

Create a README with:
- Installation instructions
- Configuration reference
- Usage examples
- Known limitations
- Contributing guidelines

### Step 6: Publish

Publish to Maven Central or your organization's artifact repository:

```scala
// build.sbt publishing configuration
ThisBuild / organization := "com.example"
ThisBuild / publishTo := sonatypePublishToBundle.value
```

---

## Best Practices for Module Development

### Resource Management

Always use `Resource` for lifecycle management:

```scala
// Good: Resource handles cleanup automatically
def resource(config: Config): Resource[IO, MyBackend] =
  Resource.make(acquire)(release)

// Avoid: Manual lifecycle management is error-prone
def create(config: Config): IO[MyBackend] = ???
def close(backend: MyBackend): IO[Unit] = ???
```

### Error Handling

Wrap external errors in domain-specific exceptions:

```scala
case class MyModuleException(message: String, cause: Throwable)
    extends RuntimeException(message, cause)

def get[A](key: String): IO[Option[A]] = {
  externalClient.fetch(key)
    .map(Some(_))
    .recover { case e: ExternalException =>
      throw MyModuleException(s"Failed to get key: $key", e)
    }
}
```

### Configuration Validation

Validate configuration at construction time:

```scala
final case class MyModuleConfig(
  host: String,
  port: Int,
  timeout: FiniteDuration
) {
  require(host.nonEmpty, "host must not be empty")
  require(port > 0 && port < 65536, "port must be valid")
  require(timeout > Duration.Zero, "timeout must be positive")
}
```

### Statistics and Observability

Implement meaningful statistics:

```scala
override def stats: IO[CacheStats] = {
  for {
    hits <- hitsCounter.get
    misses <- missesCounter.get
    size <- sizeGauge.get
  } yield CacheStats(
    hits = hits,
    misses = misses,
    evictions = 0,  // If not trackable, document why
    size = size,
    maxSize = Some(config.maxSize)
  )
}
```

---

## See Also

- [Memcached Cache Module](./cache-memcached.md) - Complete example of an official module
- [CacheBackend SPI Guide](/docs/integrations/cache-backend) - Implementing cache backends
- [Creating Pipeline Modules](/docs/api-reference/programmatic-api#creating-modules) - Registering functions for use in pipelines
- [Contributing Guide](https://github.com/VledicFranco/constellation-engine/blob/master/CONTRIBUTING.md) - Contributing to Constellation Engine
