---
title: "Introduction"
sidebar_position: 1
description: "What is Constellation Engine and why does it exist?"
---

# Introduction

Constellation Engine is a **type-safe pipeline orchestration framework** for Scala 3. It separates *what* your data pipeline does from *how* it's implemented.

:::tip For LLMs and AI Agents
If you're an AI agent helping users with Constellation, use the **[LLM-specialized documentation](/docs/llm/)** instead. It's optimized for AI consumption with task-oriented navigation, complete working examples, and context-window efficient organization.
:::

## The Problem

Backend services that aggregate data from multiple sources accumulate bugs over time. Field name typos, type mismatches, and null pointer exceptions hide in code that compiles fine but fails at runtime.

```constellation
in data: { userId: Int, userName: String }

x = data.userID        # Compile error: field 'userID' not found. Did you mean 'userId'?
y = data.userId + "!"  # Compile error: cannot concatenate Int with String
z = data[email]        # Compile error: field 'email' not found in record
```

Constellation catches these errors **at compile time**, before your pipeline runs.

:::warning Common Pitfall
These errors silently pass Scala's compiler when you use dynamic types like `Map[String, Any]` or `Json`. Constellation's type system extends validation to the pipeline layer.
:::

## Quick Example

This pipeline fetches an order, enriches it with customer data, and returns a response:

```constellation
type Order = { id: String, customerId: String, items: List<Item>, total: Float }
type Customer = { name: String, tier: String }

in order: Order

customer = FetchCustomer(order.customerId)
shipping = EstimateShipping(order.id)

# Merge records - compiler validates all fields exist
enriched = order + customer + shipping

out enriched[id, name, tier, items, total]
```

The `FetchCustomer` and `EstimateShipping` functions are implemented in Scala with full access to the JVM ecosystem:

```scala
case class CustomerInput(customerId: String)
case class CustomerOutput(name: String, tier: String)

val fetchCustomer = ModuleBuilder
  .metadata("FetchCustomer", "Fetch customer data", 1, 0)
  .implementation[CustomerInput, CustomerOutput] { input =>
    IO {
      val response = httpClient.get(s"/customers/${input.customerId}")
      CustomerOutput(response.name, response.tier)
    }
  }
  .build
```

## Developer Experience

Constellation treats tooling as **core infrastructure**, not an afterthought. Every tool is designed to reduce friction and speed up your development cycle.

### CLI — Pipelines from Your Terminal

The Constellation CLI brings compile, run, and deploy operations to your command line. It's designed for **scripting, CI/CD, and fast iteration**.

```bash
# Compile and type-check a pipeline
constellation compile my-pipeline.cst

# Execute with inputs
constellation run my-pipeline.cst --input text="Hello, World!"

# Generate a DAG visualization
constellation viz my-pipeline.cst | dot -Tpng > dag.png

# Deploy to production with canary releases
constellation deploy canary my-pipeline.cst --percent 10
```

**Why CLI matters:**
- **CI/CD integration** — JSON output and deterministic exit codes for automation
- **Scripting** — Pipe-friendly design for shell workflows
- **Server operations** — Health checks, metrics, execution management
- **Deployment** — Push, canary, promote, rollback from the command line

```bash
# Example: validate all pipelines in CI
for f in pipelines/*.cst; do
  constellation compile "$f" --json || exit 1
done
```

:::tip Quick Install
```bash
cs install io.constellation:constellation-cli_3:0.6.1
constellation --version
```
:::

### Dashboard — Write, Test, Visualize

The browser-based dashboard is a **specialized IDE** for pipeline development. It connects to your Constellation server and provides:

- **Live DAG visualization** that updates as you type
- **Integrated execution** with input forms and output display
- **Performance profiling** to identify bottlenecks
- **Execution history** to track pipeline runs

### VSCode Extension — IDE Integration

The VSCode extension provides a rich editing experience via the Language Server Protocol:

- **Autocomplete** for module names, field access, and types
- **Inline errors** displayed as you type
- **Hover documentation** showing types and module signatures
- **One-click execution** with `Ctrl+Shift+R`

:::note LSP for Any Editor
Constellation's LSP server works with any editor that supports the Language Server Protocol—VSCode, Neovim, Emacs, Sublime Text, and more.
:::

## Key Features

| Feature | Description |
|---------|-------------|
| **Type Safety** | Compile-time type checking catches field typos and type mismatches |
| **Declarative DSL** | Hot-reloadable pipeline definitions separate from implementation |
| **Automatic Parallelization** | Independent branches run concurrently on Cats Effect fibers |
| **Resilience** | Retry, timeout, fallback, cache, throttle via declarative `with` clauses |
| **CLI & Dashboard** | Terminal workflows, browser IDE, and DAG visualization |
| **IDE Support** | LSP-powered autocomplete, inline errors, and hover types |
| **Production Ready** | Docker, K8s, auth, CORS, rate limiting, health checks, SPI |

## When Constellation Makes Sense

- **API composition layers** (BFF, API gateways)
- **Data enrichment pipelines** that call multiple services
- **Backends where field mapping bugs** have caused production incidents
- **Teams that value type safety** and want faster iteration cycles
- **CI/CD pipelines** that need automated validation and deployment

## When to Use Something Else

- Simple CRUD applications (your ORM is fine)
- Real-time streaming (use Kafka Streams, Flink)
- Data warehouse ETL (use Spark, dbt)

## Requirements

:::note Prerequisites
Ensure you have the following installed before starting:
:::

- JDK 17+
- SBT 1.10+
- Node.js 18+ (for VSCode extension, optional)

## Next Steps

- Read [Core Concepts](./concepts) to understand pipelines, modules, and the type system
- Follow the [Tutorial](./tutorial) to build your first pipeline
- Install the [CLI Tool](/docs/tooling/cli) for terminal workflows
- Try the [Dashboard](/docs/tooling/dashboard) for visual pipeline development
- Check out the [Examples](./examples/) for common pipeline patterns
