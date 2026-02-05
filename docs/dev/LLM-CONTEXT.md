# Constellation Engine - LLM Context

> **Ultra-condensed reference for LLM context windows. See FEATURE-OVERVIEW.md for details.**

## What It Is

Type-safe pipeline orchestration for Scala 3. Write pipelines in `.cst` DSL, implement modules in Scala, execute with automatic parallelization.

## Core Concepts

- **Pipeline**: DAG of modules that transforms inputs → outputs
- **Module**: Scala function exposed to DSL (e.g., `GetUser`, `EnrichProfile`)
- **Hot execution**: `POST /run` - compile + execute in one call
- **Cold execution**: `POST /compile` then `POST /execute` - pre-compiled, fast

## DSL Syntax

```constellation
# Declarations
in userId: String                    # input
type Profile = { name: String }      # type alias
out result                           # output

# Module calls
user = GetUser(userId)               # call module
user = GetUser(userId) with retry: 3, timeout: 5s, cache: 15min

# Type operations
record.field                         # field access
list.field                           # element-wise (extracts field from each)
a + b                                # merge records
record[f1, f2]                       # projection (select fields)
expr when condition                  # guard → Optional
optional ?? default                  # coalesce
```

## Types

| Type | Example |
|------|---------|
| Primitives | `String`, `Int`, `Float`, `Boolean` |
| List | `List<Order>` |
| Record | `{ id: String, amount: Float }` |
| Optional | `Optional<User>` |
| Union | `Success \| Error` |

## Module Options

| Option | Example | Purpose |
|--------|---------|---------|
| retry | `with retry: 3` | Retry on failure |
| timeout | `with timeout: 5s` | Max time |
| fallback | `with fallback: {}` | Default on failure |
| cache | `with cache: 15min` | Cache results |
| delay | `with delay: 500ms` | Wait between retries |
| backoff | `with backoff: exponential` | Delay growth |
| throttle | `with throttle: 100/1min` | Rate limit |
| concurrency | `with concurrency: 5` | Max parallel |
| on_error | `with on_error: skip` | Error strategy |
| lazy | `with lazy: true` | Defer execution |
| priority | `with priority: high` | Scheduling hint |

## HTTP API

| Endpoint | Purpose |
|----------|---------|
| `POST /run` | Compile + execute (hot) |
| `POST /compile` | Compile + store (cold) |
| `POST /execute` | Execute by ref (cold) |
| `GET /modules` | List available modules |
| `GET /pipelines` | List stored pipelines |
| `GET /health` | Health check |

## Stdlib Categories

- **Math**: add, subtract, multiply, divide, max, min, abs
- **String**: concat, join, split, contains, trim, replace
- **List**: length, first, last, sum, filter, map
- **Boolean**: and, or, not
- **Compare**: gt, lt, gte, lte, eq-int, eq-string

## Example Patterns

**Basic:**
```constellation
in name: String
result = Uppercase(name)
out result
```

**With resilience:**
```constellation
in id: String
data = FetchData(id) with retry: 3, timeout: 10s, fallback: { status: "unknown" }
out data
```

**Parallel fan-out:**
```constellation
in productId: String
inventory = GetInventory(productId)
pricing = GetPricing(productId)       # runs in parallel with above
combined = inventory + pricing        # merge after both complete
out combined
```

**Conditional:**
```constellation
in user: User
premium = GetPremiumData(user) when user.tier == "premium"
result = premium ?? { features: [] }
out result
```

## Key Files

| Purpose | Path |
|---------|------|
| Module creation | `ModuleBuilder` in runtime |
| Pipeline execution | `POST /run` or `POST /execute` |
| Type system | `CType`, `CValue` in core |
| Server setup | `ConstellationServer.builder()` |

## What Constellation Is NOT

- Not a streaming framework (use Kafka/Flink)
- Not an ETL tool (use Spark/dbt)
- Not an ORM (use Slick/Doobie)
- Not for simple single-service calls
