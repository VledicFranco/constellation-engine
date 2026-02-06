# Extensibility Ethos

> **Path**: `docs/features/extensibility/ETHOS.md`
> **Parent**: [extensibility/](./README.md)

This document defines the constraints and guidelines for AI agents (LLMs) working on Constellation Engine's Service Provider Interfaces (SPIs). Following these rules ensures interface stability, backward compatibility, and predictable behavior.

## Core Constraints

### Interface Stability

**SPIs are contracts.** Once published, they must remain stable.

| Action | Allowed | Reason |
|--------|---------|--------|
| Add new method with default | Yes | Backward compatible |
| Add new method without default | No | Breaks existing implementations |
| Remove method | No | Breaks existing implementations |
| Change method signature | No | Breaks existing implementations |
| Change return type | No | Breaks existing implementations |
| Add optional parameter with default | Yes | Backward compatible |

**Example: Adding a new capability**

```scala
// CORRECT: New method with default implementation
trait ExecutionListener {
  def onExecutionStart(...): IO[Unit]
  def onModuleStart(...): IO[Unit]

  // Added in v0.4.0 - default ensures existing impls still compile
  def onExecutionSuspended(executionId: UUID, dagName: String): IO[Unit] = IO.unit
}

// WRONG: New method without default
trait ExecutionListener {
  def onExecutionStart(...): IO[Unit]
  def onModuleStart(...): IO[Unit]
  def onExecutionSuspended(...): IO[Unit]  // Breaks all existing implementations!
}
```

### Backward Compatibility

**Never break existing implementations.** Users who upgrade Constellation should not need to modify their custom backends unless they want new functionality.

| Change Type | Compatibility |
|-------------|---------------|
| New trait method with default | Binary compatible, source compatible |
| New case class field with default | Binary compatible, source compatible |
| New sealed trait variant | Binary compatible, source compatible |
| Removed method | Binary incompatible |
| Changed method signature | Binary incompatible |
| New required constructor parameter | Source incompatible |

**Semantic versioning applies:**
- Patch (0.0.X): Bug fixes only, no API changes
- Minor (0.X.0): Backward-compatible additions
- Major (X.0.0): Breaking changes (rare, documented migration path)

### Fire-and-Forget Semantics

**Observability SPIs must never block execution.**

```scala
// CORRECT: Errors swallowed, execution continues
listener.onModuleComplete(execId, moduleId, name, duration)
  .handleErrorWith { error =>
    IO(System.err.println(s"Listener error: $error")) *> IO.unit
  }

// WRONG: Error propagates, could fail pipeline
listener.onModuleComplete(execId, moduleId, name, duration)
```

**Rules:**
- `MetricsProvider` methods: Fire-and-forget
- `ExecutionListener` methods: Fire-and-forget
- `CacheBackend` methods: May propagate errors (cache miss is not a failure)
- `PipelineStore` methods: May propagate errors (persistence failure is critical)

### No Side Effects in Trait Definitions

**SPI traits contain only method signatures.** No vals, no lazy vals, no initialization logic.

```scala
// CORRECT: Pure interface
trait CacheBackend {
  def get[A](key: String): IO[Option[CacheEntry[A]]]
  def set[A](key: String, value: A, ttl: FiniteDuration): IO[Unit]
}

// WRONG: Initialization in trait
trait CacheBackend {
  val logger = LoggerFactory.getLogger(getClass)  // Side effect!

  def get[A](key: String): IO[Option[CacheEntry[A]]]
}
```

### Type Erasure Awareness

**Generic methods suffer type erasure at runtime.** Document this clearly.

```scala
trait CacheBackend {
  /** Get a cached value.
    *
    * WARNING: Type parameter A is erased at runtime. Implementations
    * typically store values as Any and cast on retrieval. Callers must
    * ensure the type matches what was stored.
    */
  def get[A](key: String): IO[Option[CacheEntry[A]]]
}
```

## Dos and Don'ts

### DO

| Action | Reason |
|--------|--------|
| Use `IO[Unit]` for side-effectful operations | Supports async, error handling, cancellation |
| Provide `noop` companion object | Zero-overhead default when extension unused |
| Document thread-safety requirements | Implementations must know concurrency model |
| Use `Map[String, String]` for tags | Universal, serializable, no custom types |
| Provide `composite` for multi-backend SPIs | Common need for multiple listeners/metrics |

### DON'T

| Action | Reason |
|--------|--------|
| Throw exceptions from SPI methods | Use `IO.raiseError` for error handling |
| Use mutable state in trait definitions | Breaks thread safety, unexpected behavior |
| Depend on implementation details | SPIs are contracts, not implementations |
| Add methods without defaults | Breaks existing implementations |
| Use raw `Future` instead of `IO` | Cats Effect provides better semantics |

## Implementation Guidelines for AI Agents

When implementing or modifying SPIs:

### Check Existing Implementations First

Before modifying an SPI trait, search for all implementations:

```bash
# Find all implementations of CacheBackend
grep -r "extends CacheBackend" modules/
grep -r "with CacheBackend" modules/
```

Every implementation must still compile after your changes.

### Add Tests for New Capabilities

New SPI methods need tests in:
- Unit tests for the no-op implementation
- Integration tests with at least one real implementation
- Contract tests that verify all implementations behave consistently

### Update Documentation

When modifying SPIs:
1. Update the trait's ScalaDoc
2. Update `docs/features/extensibility/<spi-name>.md`
3. Add migration notes if behavior changes

### Preserve Binary Compatibility

Use MiMa (Migration Manager) to verify binary compatibility:

```bash
sbt mimaReportBinaryIssues
```

If MiMa reports issues, reconsider your changes.

### Consider Performance Impact

SPIs are on the hot path. Profile new methods:

```scala
// Avoid in SPI implementations:
- Reflection
- Synchronous blocking
- Unbounded memory allocation
- Expensive string operations in tight loops
```

## Migration Patterns

### Adding a Required Capability

If you must add a method that implementations should override (not use the default):

1. Add the method with a deprecation warning default:

```scala
trait ExecutionListener {
  /** @deprecated("Implement this method for proper suspension tracking", "0.5.0") */
  def onExecutionSuspended(executionId: UUID, dagName: String): IO[Unit] = {
    IO(System.err.println(
      "WARNING: onExecutionSuspended not implemented. " +
      "Override this method to track suspensions."
    ))
  }
}
```

2. Document in CHANGELOG that implementations should override
3. In next major version, make the method abstract (breaking change)

### Deprecating a Method

```scala
trait ExecutionListener {
  /** @deprecated("Use onModuleComplete with ModuleResult instead", "0.5.0") */
  def onModuleComplete(
    executionId: UUID,
    moduleId: UUID,
    moduleName: String,
    durationMs: Long
  ): IO[Unit]

  // New method - implementations should migrate to this
  def onModuleComplete(
    executionId: UUID,
    moduleId: UUID,
    result: ModuleResult
  ): IO[Unit] = {
    // Default delegates to old method for backward compatibility
    onModuleComplete(executionId, moduleId, result.moduleName, result.durationMs)
  }
}
```

### Splitting a Large SPI

If an SPI grows too large, split it using composition:

```scala
// Before: Monolithic
trait ObservabilityProvider {
  def counter(...): IO[Unit]
  def histogram(...): IO[Unit]
  def onExecutionStart(...): IO[Unit]
  def onModuleComplete(...): IO[Unit]
}

// After: Composed
trait MetricsProvider {
  def counter(...): IO[Unit]
  def histogram(...): IO[Unit]
}

trait ExecutionListener {
  def onExecutionStart(...): IO[Unit]
  def onModuleComplete(...): IO[Unit]
}

// Builder accepts both
ConstellationImpl.builder()
  .withMetrics(metrics)
  .withListener(listener)
```

## Testing Checklist for SPI Changes

Before submitting changes to SPIs:

- [ ] All existing implementations still compile
- [ ] No-op implementation updated (if method added)
- [ ] Composite implementation updated (if applicable)
- [ ] Unit tests pass for all implementations
- [ ] Integration tests verify end-to-end behavior
- [ ] ScalaDoc updated for new/changed methods
- [ ] `docs/features/extensibility/<spi>.md` updated
- [ ] CHANGELOG updated with migration notes
- [ ] MiMa reports no binary compatibility issues
- [ ] Performance benchmarks show no regression

## Summary

Working on Constellation SPIs requires discipline:

1. **Stability over features** - Don't break existing implementations
2. **Defaults over requirements** - New methods have sensible defaults
3. **Documentation over discovery** - Make behavior explicit
4. **Tests over assumptions** - Verify compatibility systematically

These constraints exist to build trust. When organizations integrate Constellation with their infrastructure, they need confidence that upgrades won't break their custom backends.

## See Also

- [PHILOSOPHY.md](./PHILOSOPHY.md) - Why SPIs are designed this way
- [cache-backend.md](./cache-backend.md) - Cache SPI reference
- [metrics-provider.md](./metrics-provider.md) - Metrics SPI reference
- [execution-listener.md](./execution-listener.md) - Execution events SPI reference
- [execution-storage.md](./execution-storage.md) - Persistence SPI reference
