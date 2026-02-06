---
title: "Introduction"
sidebar_position: 1
description: "What is Constellation Engine and why does it exist?"
---

# Introduction

Constellation Engine is a **type-safe pipeline orchestration framework** for Scala 3. It separates *what* your data pipeline does from *how* it's implemented.

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

## How It Works

You define pipeline logic in a declarative DSL called **constellation-lang** (`.cst` files), and implement the underlying functions as **Scala modules**. The compiler validates every field access and type, then the runtime executes your pipeline with automatic parallelization.

```
constellation-lang (.cst files)         Scala Modules
─────────────────────────────           ─────────────
Declarative pipeline logic              Function implementations
Type-checked at compile time            Full language power
Hot-reloadable                          IO, HTTP calls, databases
```

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

## When Constellation Makes Sense

- **API composition layers** (BFF, API gateways)
- **Data enrichment pipelines** that call multiple services
- **Backends where field mapping bugs** have caused production incidents
- **Teams that value type safety** and want faster iteration cycles

## When to Use Something Else

- Simple CRUD applications (your ORM is fine)
- Real-time streaming (use Kafka Streams, Flink)
- Data warehouse ETL (use Spark, dbt)

## Key Features

| Feature | Description |
|---------|-------------|
| **Type Safety** | Compile-time type checking catches field typos and type mismatches |
| **Declarative DSL** | Hot-reloadable pipeline definitions separate from implementation |
| **Automatic Parallelization** | Independent branches run concurrently on Cats Effect fibers |
| **Resilience** | Retry, timeout, fallback, cache, throttle via declarative `with` clauses |
| **IDE Support** | VSCode extension with autocomplete, inline errors, hover types |
| **Production Ready** | Docker, K8s, auth, CORS, rate limiting, health checks, SPI |

:::tip VSCode Extension
Install the Constellation Engine VSCode extension for the best development experience. It provides syntax highlighting, real-time error checking, autocomplete, hover types, and one-click pipeline execution.
:::

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
- Read the [Language Reference](/docs/language/) to learn constellation-lang syntax
- Check out the [Examples](./examples/) for common pipeline patterns
