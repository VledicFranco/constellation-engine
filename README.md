# Constellation Engine

[![CI](https://github.com/VledicFranco/constellation-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/VledicFranco/constellation-engine/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/VledicFranco/constellation-engine/graph/badge.svg)](https://codecov.io/gh/VledicFranco/constellation-engine)

**Stop writing glue code. Start describing pipelines.**

Constellation Engine is a type-safe orchestration language that lets you define data transformation pipelines declaratively. Describe *what* you want to compute, not *how* to wire it together.

---

## Why Constellation?

### The Problem

Building backend pipelines today means drowning in boilerplate:

```python
# Traditional approach: 50 lines of glue code
def aggregate_order(order, customer, inventory):
    enriched = {}
    enriched['orderId'] = order['id']
    enriched['items'] = order['items']
    enriched['customerName'] = customer['name']
    enriched['customerTier'] = customer['tier']

    available_items = []
    for item in order['items']:
        stock = inventory.get(item['productId'], {})
        if stock.get('quantity', 0) >= item['quantity']:
            available_items.append({
                'productId': item['productId'],
                'price': stock['price'],
                'quantity': item['quantity']
            })
    enriched['availableItems'] = available_items
    return enriched
```

Type errors hide until runtime. Field mismatches cause silent bugs. Refactoring is terrifying.

### The Solution

```
# Constellation: Declarative, fully type-checked
in order: { id: String, items: List<{ productId: String, quantity: Int }> }
in customer: { name: String, tier: String }

enriched = order + customer
output = enriched[id, items, name, tier]

out output
```

Every field access is validated at compile time. Type mismatches are caught before execution. Your IDE shows you exactly what's available.

---

## See It In Action

### API Response Aggregation

Combine data from multiple services into a unified response:

```
type Order = { id: String, total: Float, status: String }
type Customer = { customerId: Int, name: String, tier: String }

in orders: List<Order>
in customer: Customer

# Merge adds customer context to EACH order
enriched = orders + customer

# Project selects fields from EACH order
output = enriched[id, total, status, customerId, name]

out output
```

**Input:**
```json
{
  "orders": [
    {"id": "ord-1", "total": 99.99, "status": "shipped"},
    {"id": "ord-2", "total": 149.50, "status": "pending"}
  ],
  "customer": {"customerId": 123, "name": "Alice", "tier": "premium"}
}
```

**Output:**
```json
{
  "output": [
    {"id": "ord-1", "total": 99.99, "status": "shipped", "customerId": 123, "name": "Alice"},
    {"id": "ord-2", "total": 149.50, "status": "pending", "customerId": 123, "name": "Alice"}
  ]
}
```

### Conditional Workflows

Build configurable business logic with full type safety:

```
type Transaction = { id: String, amount: Int, category: String }
type Rules = { threshold: Int, taxRate: Float }

in transaction: Transaction
in rules: Rules

# Conditional logic with type-safe field access
isLarge = gte(transaction.amount, rules.threshold)
taxAmount = branch isLarge then multiply(transaction.amount, rules.taxRate) else 0

result = transaction + { tax: taxAmount, flagged: isLarge }

out result
```

### Higher-Order Transformations

Filter and transform collections with lambdas:

```
in users: List<{ name: String, age: Int, active: Boolean }>

# Filter to active users over 18
adults = Filter(users, u => and(u.active, gte(u.age, 18)))

# Extract just names
names = Map(adults, u => u.name)

# Check if all are active
allActive = All(adults, u => u.active)

out names
out allActive
```

---

## Key Features

### Type Algebra

Merge records with `+` (right side wins on conflicts):

```
in a: { x: Int, y: Int }
in b: { y: String, z: String }

merged = a + b  # Type: { x: Int, y: String, z: String }
```

### Projections

Select exactly the fields you need:

```
in user: { id: Int, name: String, email: String, passwordHash: String }

public = user[id, name, email]  # Type: { id: Int, name: String, email: String }
```

### Element-wise List Operations

Operations on `List<Record>` apply to each element automatically:

```
in items: List<{ id: String, price: Float }>
in context: { currency: String }

# Every item gets currency added
enriched = items + context  # List<{ id: String, price: Float, currency: String }>

# Extract a single field from each item
prices = items.price  # List<Float>
```

### Full IDE Support

- Real-time type checking as you type
- Autocomplete for fields and functions
- Hover for type information
- Go-to-definition for custom types
- Inline error messages with suggestions

---

## Quick Start

### 1. Install

```bash
git clone https://github.com/VledicFranco/constellation-engine.git
cd constellation-engine
make compile
```

### 2. Start the Server

```bash
make server
```

### 3. Run a Pipeline

**Via HTTP:**
```bash
curl -X POST http://localhost:8080/run \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in x: Int\nresult = add(x, 10)\nout result",
    "inputs": {"x": 5}
  }'
```

**Via VSCode:**
1. Install the Constellation extension
2. Create a `.cst` file
3. Press `Ctrl+Shift+R` to run

---

## Integrate With Your Code

```scala
import io.constellation.lang.runtime.LangCompiler
import io.constellation.lang.semantic._

// Define your service modules
val compiler = LangCompiler.builder
  .withFunction(FunctionSignature(
    name = "fetch-user",
    params = List("userId" -> SemanticType.SInt),
    returns = userType,
    moduleName = "fetch-user"
  ))
  .withModule(fetchUserModule)  // Your actual implementation
  .build

// Compile and run
compiler.compile(source, "my-pipeline") match {
  case Right(result) =>
    val dagSpec = result.dagSpec
    // Execute with Runtime.run(dagSpec, inputs, modules)
  case Left(errors) =>
    errors.foreach(e => println(e.format))
}
```

---

## Architecture

```
Source Code (.cst)
       |
       v
  +---------+     +-------------+     +-------------+     +-------------+
  | Parser  | --> | TypeChecker | --> | IRGenerator | --> | DagCompiler |
  +---------+     +-------------+     +-------------+     +-------------+
       |               |                    |                    |
       v               v                    v                    v
      AST          TypedAST            IRProgram             DagSpec
                                                          + Synthetic
                                                            Modules
```

| Stage | What It Does |
|-------|--------------|
| **Parser** | Parses source into AST with position tracking |
| **TypeChecker** | Validates types, catches field/type mismatches |
| **IRGenerator** | Transforms to intermediate representation |
| **DagCompiler** | Generates executable DAG + synthetic modules |

---

## Project Structure

```
modules/
├── core/           # Type system (CType, CValue)
├── runtime/        # DAG execution, ModuleBuilder API
├── lang-ast/       # AST definitions with source positions
├── lang-parser/    # Parser (cats-parse)
├── lang-compiler/  # Type checker, IR, DAG compiler
├── lang-stdlib/    # Standard library (Map, Filter, etc.)
├── lang-lsp/       # Language Server Protocol
├── http-api/       # HTTP server + WebSocket LSP
└── example-app/    # Example application
```

---

## Development

```bash
# Compile everything
make compile

# Run all tests
make test

# Start dev environment (server + hot reload)
make dev

# Run specific test suites
make test-core
make test-compiler
make test-lsp
```

---

## Requirements

- **JDK 17+**
- **SBT 1.9+**
- **Node.js 18+** (for VSCode extension)

---

## Documentation

| Resource | Description |
|----------|-------------|
| [Getting Started](docs/getting-started.md) | Complete tutorial from zero to custom modules |
| [Language Reference](docs/constellation-lang/README.md) | Full syntax and semantics |
| [Standard Library](docs/stdlib.md) | Built-in functions reference |
| [Pipeline Examples](docs/examples/README.md) | Real-world examples with explanations |
| [Architecture Guide](docs/architecture.md) | Deep dive into internals |
| [API Guide](docs/api-guide.md) | Programmatic usage |
| [Contributing](CONTRIBUTING.md) | Development setup and workflow |

---

## Use Cases

Constellation excels at:

- **API Composition** - Aggregate responses from multiple microservices
- **Data Transformation** - ETL pipelines with compile-time validation
- **Workflow Orchestration** - Conditional business logic with type safety
- **Backend-for-Frontend** - Build tailored API responses
- **Event Processing** - Transform and route events through pipelines

---

## Why "Constellation"?

Pipelines are graphs of connected nodes - like stars forming constellations. Each node (module) is a point of light; together they form something meaningful.

---

## License

[MIT](LICENSE)

---

**Ready to stop writing glue code?** [Get started in 10 minutes](docs/getting-started.md)
