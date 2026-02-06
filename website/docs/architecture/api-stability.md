---
title: "API Stability"
sidebar_position: 3
description: "Versioning policy, stability tiers, deprecation guarantees"
---

# API Stability

This document describes Constellation Engine's versioning policy, API stability tiers, and deprecation guarantees. It answers the key question: **What can I depend on, and what might change?**

:::tip Quick Answer
**Your code will keep working** across minor version upgrades (1.x to 1.y). Breaking changes only happen in major versions (1.x to 2.0), with deprecation warnings and migration guides.
:::

## Stability Summary

Before diving into details, here is a quick reference of what you can depend on:

| Component | Stability | What This Means |
|-----------|-----------|-----------------|
| HTTP API endpoints | **Stable** | Endpoint paths and response shapes will not break in minor versions |
| constellation-lang syntax | **Stable** | Your `.cst` files keep working across upgrades |
| Module signatures | **Stable** | Existing modules stay compatible; new modules may be added |
| Scala embedding API | **Stable** | `Constellation`, `ModuleBuilder`, `CValue`, `CType` interfaces |
| Internal APIs | **Unstable** | May change without notice in any release |
| Generated code | **Unstable** | DAG format, cache keys, IR nodes may change |

### What "Stable" Means

When we say an API is stable:

- **No breaking changes** in minor versions (1.x to 1.y)
- **Deprecation warnings** for at least one minor version before removal
- **Migration guides** provided for major version upgrades
- **Binary compatibility** checked with MiMa (post-1.0)

### What Might Change Without Notice

These are explicitly **not** part of the stable API:

- Internal Scala packages (anything in `.internal.` packages)
- DAG serialization format and IR nodes
- Cache key derivation algorithm
- Compiler optimization passes
- Parser combinator internals
- Error message text (content may improve)
- Metric names (may be added or renamed)

:::warning Pre-1.0 Notice
Until v1.0.0, minor version bumps may include breaking changes. Pin your dependency to a specific minor version (e.g., `0.4.x`) for stability during the pre-1.0 phase.
:::

## Versioning Policy

We follow [Semantic Versioning 2.0.0](https://semver.org/):

| Version | When Bumped | What to Expect |
|---------|-------------|----------------|
| **MAJOR** (2.0.0) | Breaking changes to public API | Migration required; guides provided |
| **MINOR** (1.1.0) | New features, backwards compatible | Safe to upgrade; test your code |
| **PATCH** (1.0.1) | Bug fixes only | Safe to upgrade immediately |

### Version Examples

| Change Type | Example | Version Bump |
|-------------|---------|--------------|
| Removing a method | `oldMethod` deleted | Major |
| Changing a type signature | `def foo(x: Int)` to `def foo(x: String)` | Major |
| Adding a new module | New `JsonParse` module in StdLib | Minor |
| Adding a builder method | New `.withTimeout()` option | Minor |
| Fixing a type-checking bug | Incorrect error message | Patch |
| Performance improvement | Faster DAG execution | Patch |

## Stability Tiers

Every API surface in Constellation falls into one of three tiers:

### Tier Overview

| Tier | Can I Depend On It? | Breaking Changes | Examples |
|------|---------------------|------------------|----------|
| **Public API** | Yes | Major versions only (with deprecation) | `ModuleBuilder`, `Constellation`, `CValue` |
| **SPI** | Yes (if implementing) | Major versions only; new methods in minor | `CacheBackend`, `ExecutionListener` |
| **Internal** | No | Any release | IR nodes, parser combinators |

### Public API (Stable)

These are safe to depend on. Breaking changes only in major versions, with at least one minor version deprecation window.

**How to identify:** Default for public packages. No special annotation.

### SPI (Service Provider Interface)

Same guarantees as Public API. If you implement an SPI trait, expect:
- New methods may be added in minor versions (with default implementations)
- Existing method signatures will not change in minor versions

**How to identify:** Documented in SPI documentation.

### Internal

No stability guarantees. May change in any release without notice.

**How to identify:** Package names containing `.internal.` or explicitly documented as internal.

:::note
If you find yourself depending on internal APIs, [open an issue](https://github.com/VledicFranco/constellation-engine/issues) requesting promotion to Public or SPI tier.
:::

## What You Can Depend On

The following are considered **public API** and covered by stability guarantees:

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

## What Might Change

The following are **not** covered by stability guarantees and may change without notice:

| Category | Examples | Why Unstable |
|----------|----------|--------------|
| Compiler internals | `IRNode`, `IRGenerator`, optimization passes | Implementation details that improve over time |
| Parser internals | Parser combinators, intermediate parse results | Grammar implementation may be refactored |
| AST nodes | `ConstellationAST` node types | Consumed only by the compiler |
| Runtime internals | Scheduler details, cache key derivation | Optimization opportunities |
| HTTP internals | Middleware implementation, internal routes | May restructure for performance |
| Generated output | DAG serialization format, error message text | Improves with each release |

:::tip
If you find yourself depending on internal APIs, [open an issue](https://github.com/VledicFranco/constellation-engine/issues) requesting promotion to Public or SPI tier. We are happy to stabilize APIs that users genuinely need.
:::

## Deprecation Policy

We never remove public APIs without warning. When a public API element must change:

### The Deprecation Process

```
1. DEPRECATION     →  2. MIGRATION WINDOW  →  3. REMOVAL
   (Minor release)       (At least 1 minor)      (Major release)
```

1. **Deprecation** — The element is annotated with `@deprecated("message", "version")`. The message includes migration guidance.
2. **Migration window** — The deprecated element continues to work for at least one minor version.
3. **Removal** — The element is removed in the next major version.

### Example Timeline (Post-1.0)

| Version | What Happens | Your Action |
|---------|--------------|-------------|
| 1.2.0 | `oldMethod` deprecated with `@deprecated("Use newMethod instead", "1.2.0")` | Update your code (optional) |
| 1.3.0 | `oldMethod` still works, compiler warns | Update your code (recommended) |
| 2.0.0 | `oldMethod` removed | Must have updated by now |

### Pre-1.0 Deprecation

During the pre-1.0 phase, deprecated APIs may be removed in the next minor version rather than waiting for a major version. We recommend:

- Compile with `-deprecation` (enabled by default)
- Address deprecation warnings before upgrading
- Check the [CHANGELOG](https://github.com/VledicFranco/constellation-engine/blob/master/CHANGELOG.md) before upgrading

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

Your `.cst` scripts are protected by the same stability guarantees as the Scala API:

| What | Stability | Notes |
|------|-----------|-------|
| Existing syntax | **Stable** | Scripts that work today keep working |
| New syntax | Added in minor versions | New expressions, operators, module options |
| Error messages | **Unstable** | Text may improve in any release |
| Error codes | Added in minor versions | New codes may appear |

**Post-1.0:** Grammar changes that reject previously valid scripts are breaking changes (major version only).

## Upgrade Checklist

Use this checklist when upgrading Constellation versions:

### For Minor Version Upgrades (1.x to 1.y)

- [ ] Read the [CHANGELOG](https://github.com/VledicFranco/constellation-engine/blob/master/CHANGELOG.md) for new features and deprecations
- [ ] Run your test suite
- [ ] Check for deprecation warnings and plan migration
- [ ] Update code to use new features if desired

### For Major Version Upgrades (1.x to 2.0)

- [ ] Read the migration guide in the release notes
- [ ] Address all deprecation warnings from the previous version
- [ ] Run your test suite
- [ ] Review breaking changes that affect your code
- [ ] Update SPI implementations if applicable

## Recommendations

### For Library Consumers

| Recommendation | Why |
|----------------|-----|
| Pin minor versions pre-1.0 | Use `"0.4.+"` style ranges for stability |
| Avoid `.internal.` packages | No stability guarantees |
| Compile with `-deprecation` | Catch deprecation warnings early |
| Run tests on upgrade | Verify your code still works |

### For SPI Implementors

| Recommendation | Why |
|----------------|-----|
| Implement all trait methods | Default implementations may not be optimal |
| Test against latest patch | SPI contracts are tested in CI |
| Report compatibility issues | We want to hear about problems |

## Related

- [Technical Architecture](./technical-architecture.md) — How Constellation Engine processes pipelines
- [Security Model](./security-model.md) — Trust boundaries and HTTP hardening
- [Programmatic API](../api-reference/programmatic-api.md) — Stable Scala embedding API
- [HTTP API Overview](../api-reference/http-api-overview.md) — Stable REST endpoints
