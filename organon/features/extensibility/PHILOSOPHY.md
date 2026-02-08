# Extensibility Philosophy

> **Path**: `organon/features/extensibility/PHILOSOPHY.md`
> **Parent**: [extensibility/](./README.md)

This document explains the design philosophy behind Constellation Engine's extensibility model: why we chose Service Provider Interfaces (SPI) over inheritance, and the principles that guide extension point design.

## Core Principle: Extension Without Modification

The Open-Closed Principle states that software should be open for extension but closed for modification. Constellation Engine takes this principle seriously:

- **Core behavior is immutable** - The runtime, compiler, and execution engine ship as sealed units
- **Extension happens at boundaries** - Integration points are explicitly defined interfaces
- **New capabilities compose** - Extensions combine without modifying each other

This design allows organizations to customize Constellation for their infrastructure without forking the codebase or waiting for upstream changes.

## Why SPI Over Inheritance

Traditional frameworks often use abstract base classes for extensibility:

```scala
// Anti-pattern: Inheritance-based extensibility
abstract class CacheProvider {
  protected def internalGet(key: String): Option[Any]
  protected def internalSet(key: String, value: Any): Unit

  final def get(key: String): Option[Any] = {
    recordMetric()
    internalGet(key)
  }
}
```

This approach has significant drawbacks:

| Problem | Impact |
|---------|--------|
| **Fragile base class** | Changes to parent break children |
| **Diamond inheritance** | Multiple inheritance creates conflicts |
| **Hidden coupling** | Protected members create implicit contracts |
| **Testing difficulty** | Can't mock without inheritance hierarchies |
| **Version lock-in** | Binary compatibility constraints |

Constellation uses trait-based SPIs instead:

```scala
// SPI: Interface-based extensibility
trait CacheBackend {
  def get[A](key: String): IO[Option[CacheEntry[A]]]
  def set[A](key: String, value: A, ttl: FiniteDuration): IO[Unit]
  def delete(key: String): IO[Boolean]
  def clear: IO[Unit]
  def stats: IO[CacheStats]
}
```

### Benefits of SPI

| Benefit | Explanation |
|---------|-------------|
| **Decoupled evolution** | Interface and implementation evolve independently |
| **Composition over inheritance** | Combine behaviors without class hierarchies |
| **Easy testing** | Mock any interface directly |
| **Binary compatibility** | Add methods with defaults without breaking existing implementations |
| **Multiple implementations** | Switch backends without code changes |

## Pluggable Backends: The Registry Pattern

Extensions are registered at application startup, not hardcoded:

```scala
// Startup configuration - no runtime reflection or classpath scanning
ConstellationImpl.builder()
  .withCache(new RedisCacheBackend(redisClient))
  .withMetrics(new PrometheusMetricsProvider(registry))
  .withListener(ExecutionListener.composite(kafkaListener, auditListener))
  .build()
```

This pattern provides:

1. **Explicit wiring** - Dependencies are visible in startup code
2. **Type safety** - Compiler verifies interface compatibility
3. **No magic** - No annotation scanning or reflection
4. **Testability** - Inject mocks directly in tests

### Default Implementations

Every SPI has a zero-overhead default:

```scala
object MetricsProvider {
  val noop: MetricsProvider = new MetricsProvider {
    def counter(name: String, tags: Map[String, String]): IO[Unit] = IO.unit
    def histogram(name: String, value: Double, tags: Map[String, String]): IO[Unit] = IO.unit
    def gauge(name: String, value: Double, tags: Map[String, String]): IO[Unit] = IO.unit
  }
}
```

This ensures:
- **Minimal overhead** - Production systems without metrics pay no cost
- **Gradual adoption** - Add observability incrementally
- **Safe defaults** - Missing configuration doesn't crash the system

## Core Independence

Extensions cannot break core functionality. The execution engine has no compile-time dependency on any extension implementation:

```
┌─────────────────────────────────────────────────────────┐
│                     Core Runtime                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │  Execution  │  │   Compile   │  │  Type System    │  │
│  │   Engine    │  │   Pipeline  │  │                 │  │
│  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘  │
│         │                │                   │           │
│         └────────────────┼───────────────────┘           │
│                          │                               │
│                  ┌───────▼───────┐                       │
│                  │  SPI Traits   │                       │
│                  │  (Interfaces) │                       │
│                  └───────────────┘                       │
└──────────────────────────┬──────────────────────────────┘
                           │
           ┌───────────────┼───────────────┐
           │               │               │
           ▼               ▼               ▼
   ┌───────────────┐ ┌───────────┐ ┌───────────────┐
   │ Redis Cache   │ │ Prometheus│ │ Kafka Events  │
   │ (optional)    │ │ (optional)│ │ (optional)    │
   └───────────────┘ └───────────┘ └───────────────┘
```

The runtime calls SPI methods through the interface. If no implementation is registered, the no-op default handles the call. The core never fails due to missing extensions.

## Design Principles for New Extension Points

When adding new SPIs to Constellation, follow these principles:

### 1. Minimal Surface Area

Interfaces should be small and focused:

```scala
// Good: Focused interface
trait MetricsProvider {
  def counter(name: String, tags: Map[String, String]): IO[Unit]
  def histogram(name: String, value: Double, tags: Map[String, String]): IO[Unit]
  def gauge(name: String, value: Double, tags: Map[String, String]): IO[Unit]
}

// Bad: Kitchen-sink interface
trait MetricsProvider {
  def counter(...): IO[Unit]
  def histogram(...): IO[Unit]
  def gauge(...): IO[Unit]
  def timer(...): IO[Unit]
  def meter(...): IO[Unit]
  def healthCheck(...): IO[Boolean]
  def registerCallback(...): IO[Unit]
  // ... 20 more methods
}
```

### 2. Effectful Returns

All SPI methods return `IO[_]` to support:
- Async backends (Redis, Kafka, HTTP)
- Error handling
- Resource management
- Cancellation

```scala
// Always IO
def get[A](key: String): IO[Option[CacheEntry[A]]]

// Never raw values
def get[A](key: String): Option[CacheEntry[A]]  // Wrong
```

### 3. Fire-and-Forget Semantics

Observability SPIs (metrics, listeners) are invoked fire-and-forget:

```scala
// Execution continues regardless of listener outcome
listener.onModuleComplete(execId, moduleId, name, duration)
  .handleErrorWith(_ => IO.unit)  // Errors are swallowed
```

This ensures:
- Extensions cannot block execution
- Failures in one listener don't affect others
- Performance overhead is predictable

### 4. Composite Implementations

SPIs that support multiple simultaneous backends provide a `composite` factory:

```scala
object ExecutionListener {
  def composite(listeners: ExecutionListener*): ExecutionListener
}

// Usage: Multiple listeners receive every event
val listener = ExecutionListener.composite(
  kafkaListener,
  auditListener,
  metricsListener
)
```

### 5. Default Methods for Evolution

New methods are added with defaults to maintain backward compatibility:

```scala
trait ExecutionListener {
  // Original methods (no default)
  def onExecutionStart(...): IO[Unit]
  def onModuleStart(...): IO[Unit]

  // Added in v0.3.0 - has default for backward compatibility
  def onExecutionCancelled(executionId: UUID, dagName: String): IO[Unit] = IO.unit
}
```

## Comparison With Alternative Approaches

### Dependency Injection Frameworks

Frameworks like Guice or Spring provide DI but add complexity:

| Aspect | DI Framework | Constellation SPI |
|--------|--------------|-------------------|
| Wiring | Annotations/XML | Explicit builder |
| Discovery | Classpath scanning | Direct registration |
| Debugging | Stack traces through proxies | Direct calls |
| Startup time | Slower (reflection) | Fast (direct) |
| Type safety | Runtime | Compile-time |

### Plugin Systems

Hot-loadable plugins (like OSGI) provide runtime flexibility but at a cost:

| Aspect | Plugin System | Constellation SPI |
|--------|---------------|-------------------|
| Hot reload | Yes | No (restart required) |
| Isolation | Class loader per plugin | Shared runtime |
| Complexity | High | Low |
| Performance | Overhead from isolation | Direct calls |

Constellation prioritizes simplicity and performance over hot-reloading. Most production systems restart for configuration changes anyway.

## Summary

Constellation's extensibility model is built on these foundations:

1. **SPIs are traits** - Pure interfaces with no implementation
2. **Registration is explicit** - Builder pattern at startup
3. **Defaults are no-ops** - Missing extensions don't crash
4. **Core is independent** - Extensions can't break execution
5. **Evolution uses defaults** - New methods don't break implementors

This approach delivers flexibility without complexity, allowing organizations to integrate Constellation with their infrastructure while maintaining upgrade compatibility.

## See Also

- [ETHOS.md](./ETHOS.md) - Constraints for AI agents working on SPIs
- [cache-backend.md](./cache-backend.md) - Cache SPI reference
- [metrics-provider.md](./metrics-provider.md) - Metrics SPI reference
- [execution-listener.md](./execution-listener.md) - Execution events SPI reference
- [execution-storage.md](./execution-storage.md) - Persistence SPI reference
