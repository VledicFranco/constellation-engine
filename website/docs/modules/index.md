---
title: "Overview"
sidebar_position: 1
---

# Optional Modules

Constellation Engine ships a set of optional, first-party modules that extend the core runtime with integrations for external systems. Each module lives in its own SBT project under `modules/` and can be added as a dependency independently.

## Available Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| [Memcached Cache](./cache-memcached.md) | `constellation-cache-memcached` | Distributed caching via Memcached (spymemcached) |

## When to Use Optional Modules vs. SPI

Constellation provides two extension mechanisms:

- **SPI traits** (see [Integrations](/docs/integrations/cache-backend)) define the interface. You implement them yourself for full control.
- **Optional modules** are ready-made implementations of those SPI traits, published alongside the core library.

Use an optional module when it fits your stack. Implement the SPI directly when you need a backend that isn't covered or want tighter integration with your infrastructure.

## Adding a Module

Add the module artifact to your `build.sbt`:

```scala
libraryDependencies += "io.github.vledicfranco" %% "constellation-cache-memcached" % "<version>"
```

Each module page documents its dependencies, configuration, and usage.

## Module Dependency Graph

All optional modules depend on `constellation-runtime` (the core cache SPI lives there) and never introduce circular dependencies:

```
core
  |
runtime  (CacheBackend, CacheSerde, DistributedCacheBackend)
  |
  +-- cache-memcached  (MemcachedCacheBackend)
  +-- (future modules)
```
