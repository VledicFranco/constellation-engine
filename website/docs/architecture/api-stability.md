---
title: "API Stability"
sidebar_position: 3
description: "Versioning policy, stability tiers, deprecation guarantees"
---

# API Stability

This document describes Constellation Engine's versioning policy, API stability tiers, and deprecation guarantees. It is intended for library consumers who need to understand what they can depend on across releases.

## Semantic Versioning

Constellation Engine follows [Semantic Versioning 2.0.0](https://semver.org/):

| Version Component | When Bumped | Example |
|-------------------|-------------|---------|
| **Major** (X.0.0) | Breaking changes to public API | Removing a method, changing a type signature |
| **Minor** (0.X.0) | New features, backward-compatible changes | Adding a new module option, new builder method |
| **Patch** (0.0.X) | Bug fixes, internal improvements | Fixing a type-checking bug, performance improvement |

**Pre-1.0 notice:** Until v1.0.0, minor version bumps may include breaking changes. Pin your dependency to a specific minor version (e.g., `0.4.x`) for stability.

## Stability Tiers

Every API surface in Constellation falls into one of three tiers:

| Tier | Guarantee | Annotation | Examples |
|------|-----------|------------|----------|
| **Public API** | Breaking changes only in major versions (post-1.0). Minimum 1 minor version deprecation window. | None (default for public packages) | `ModuleBuilder`, `Constellation`, `ConstellationServer.builder`, `CValue`, `CType` |
| **SPI (Service Provider Interface)** | Same guarantees as Public API. Implementors should expect new methods with default implementations in minor versions. | Documented in SPI docs | `MetricsProvider`, `TracerProvider`, `ExecutionListener`, `CacheBackend`, `ExecutionStorage` |
| **Internal** | No stability guarantees. May change in any release without notice. | Package names containing `.internal.` or documented as internal | Compiler IR nodes, parser combinators, AST transformation passes, DAG optimizer internals |

## What Constitutes Public API

The following are considered public API and covered by stability guarantees:

### Core Types (`constellation-core`)

- `CValue` and all subtypes (`CString`, `CInt`, `CFloat`, `CBool`, `CList`, `CRecord`, `CNull`)
- `CType` and all subtypes
- Type coercion and conversion methods

### Module System (`constellation-runtime`)

- `ModuleBuilder` — all public methods for defining modules
- `Module` and `Module.Initialized` / `Module.Uninitialized` types
- `Constellation` — the main orchestration container
- `FunctionSignature` — module type declarations
- `Runtime` — DAG execution API
- Cache and execution listener SPIs

### HTTP Server (`constellation-http-api`)

- `ConstellationServer.builder` and all builder methods
- Configuration case classes: `AuthConfig`, `CorsConfig`, `RateLimitConfig`, `HealthCheckConfig`
- `ApiRole` enum values
- HTTP endpoint paths and response shapes documented in the API reference

### Compiler (`constellation-lang-compiler`)

- `DagCompiler.compile` — the public compilation entry point
- `CompilationResult` and `CompilationError` types
- `TypeChecker` public interface

### Standard Library (`constellation-lang-stdlib`)

- `StdLib` — module registration and signature lists
- All standard library module names and their input/output contracts

## What Is Internal

The following are **not** covered by stability guarantees and may change without notice:

- **Compiler internals** — IR nodes (`IRNode`, `IRGenerator`), optimization passes, semantic analysis internals
- **Parser internals** — individual parser combinators, intermediate parse results
- **AST nodes** — `ConstellationAST` node types (consumed only by the compiler)
- **Runtime internals** — scheduler implementation details, cache key derivation, internal execution state
- **HTTP internals** — middleware implementation details, internal route construction

If you find yourself depending on internal APIs, open an issue requesting that the API be promoted to Public or SPI tier.

## Deprecation Policy

When a public API element must change:

1. **Deprecation** — The element is annotated with `@deprecated("message", "version")` in the next minor release. The deprecation message includes migration guidance.
2. **Migration window** — The deprecated element continues to work for at least one minor version cycle.
3. **Removal** — The element is removed in the next major version.

**Example timeline (post-1.0):**

| Version | Action |
|---------|--------|
| 1.2.0 | `oldMethod` deprecated with `@deprecated("Use newMethod instead", "1.2.0")` |
| 1.3.0 | `oldMethod` still works, compiler warns |
| 2.0.0 | `oldMethod` removed |

**Pre-1.0:** Deprecated APIs may be removed in the next minor version rather than waiting for a major version.

## Compatibility Scope

### Scala Version

Constellation targets **Scala 3.3.x LTS**. Cross-building for other Scala 3 versions may be added in the future but is not guaranteed.

### JDK Version

Minimum supported JDK: **17**. The library is tested on JDK 17 and should work on later LTS releases (21+). JDK version support follows the same stability policy — dropping a JDK version is a breaking change.

### Dependency Policy

Constellation's transitive dependencies (cats-core, cats-effect, circe, http4s) follow their own versioning policies. Constellation will:

- **Stay on binary-compatible versions** of its dependencies within a major version
- **Document dependency version bumps** in the changelog
- **Not expose dependency types in public API signatures** where avoidable, to minimize transitive compatibility concerns

### Binary Compatibility

Binary compatibility is checked using [MiMa (Migration Manager)](https://github.com/lightbend/mima). Starting with v1.0.0, every published module will be checked against the previous release for binary compatibility. Breaking binary changes require a major version bump.

**Published modules covered by MiMa:**

- `constellation-core`
- `constellation-runtime`
- `constellation-lang-ast`
- `constellation-lang-parser`
- `constellation-lang-compiler`
- `constellation-lang-stdlib`
- `constellation-lang-lsp`
- `constellation-http-api`
- `constellation-cache-memcached`

## constellation-lang Syntax Stability

The constellation-lang scripting language follows the same versioning policy as the Scala API:

- **Post-1.0:** Grammar changes that reject previously valid scripts are breaking changes (major version only)
- **New syntax** (expressions, operators, module options) can be added in minor versions
- **Error message text** is not part of the stability guarantee and may change in any release
- **Compiler diagnostics** (error codes, warning categories) may be added in minor versions

## Recommendations

### For Library Consumers

1. **Pin minor versions pre-1.0** — use `"io.github.vledicfranco" %% "constellation-core" % "0.4.x"` style ranges
2. **Avoid internal packages** — if a type is in a `.internal.` package or documented as internal, don't depend on it
3. **Watch deprecation warnings** — compile with `-deprecation` (enabled by default) and migrate promptly
4. **Test on upgrade** — run your test suite when bumping Constellation versions

### For SPI Implementors

1. **Implement all trait methods** — new methods will have default implementations, but providing real implementations is recommended
2. **Test against the latest patch release** — SPI contracts are tested as part of Constellation's own test suite
3. **Report compatibility issues** — if an SPI change breaks your implementation, open an issue
