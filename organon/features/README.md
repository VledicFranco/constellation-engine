# Features

> **Path**: `organon/features/`
> **Parent**: [organon/](](../README.md)

Feature-driven documentation organized by what Constellation does, not how it's built.

## Contents

| Feature | Description | Components |
|---------|-------------|------------|
| [type-safety/](./type-safety/) | Compile-time validation of field accesses and type operations | core, compiler |
| [resilience/](./resilience/) | Retry, timeout, fallback, cache, throttle, and error handling | compiler, runtime |
| [parallelization/](./parallelization/) | Automatic concurrent execution of independent modules | runtime |
| [execution/](./execution/) | Hot, cold, and suspended execution modes | runtime, http-api |
| [extensibility/](./extensibility/) | SPI for cache, metrics, listeners; module provider protocol for cross-process modules; module HTTP endpoints for direct invocation | runtime, module-provider-sdk, module-provider, http-api |
| [tooling/](./tooling/) | Dashboard, LSP, VSCode extension | http-api, lang-lsp |
| [lambda-closures/](./lambda-closures/) | Lambda expressions capturing outer-scope variables | core, lang-compiler |

## Structure

Each feature directory contains:

| File | Purpose |
|------|---------|
| `PHILOSOPHY.md` | Why this feature exists, trade-offs, design reasoning |
| `ETHOS.md` | Behavioral constraints for LLMs working on this feature |
| `README.md` | Overview and navigation |
| `*.md` | Detailed documentation with component cross-references |

## Navigation Pattern

Features are the **primary navigation** for understanding Constellation. Start here when asking "what can Constellation do?"

For implementation details, see [components/](../components/) — organized by codebase module.

```
"How does caching work?"     → features/resilience/caching.md
"Where is caching implemented?" → features/resilience/caching.md → Components table → runtime/CacheExecutor.scala
```
