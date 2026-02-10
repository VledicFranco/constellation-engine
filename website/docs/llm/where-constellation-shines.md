---
title: "Where Constellation Shines"
sidebar_position: 2
description: "An honest guide to when Constellation Engine is the right choice vs alternatives"
---

# Where Constellation Shines

**Purpose:** Help you decide if Constellation Engine is the right tool for your problem. We'll be honest about where it excels and where alternatives are better.

## TL;DR - The 30 Second Decision

Use Constellation if:
- You compose multiple backend services and field mapping bugs hurt
- Type safety at compile time matters more than raw throughput
- You want automatic parallelization without manual wiring

Don't use Constellation if:
- You're building simple CRUD APIs
- You need streaming data processing
- You need workflow orchestration with human steps

## Ideal Use Cases

### 1. Backend-for-Frontend (BFF) Layers

**Scenario:** Your frontend needs data from 5 different microservices. Each service has its own schema. You merge, transform, and project fields.

**Why Constellation Shines:**
- **Compile-time field validation** - `customer.userId` fails at compile time if `userId` doesn't exist
- **Automatic parallelization** - Independent service calls run concurrently without manual wiring
- **Record algebra** - `order + customer + shipping` merges cleanly, projection extracts exactly what the frontend needs
- **Hot reload** - Change pipeline logic without redeploying the BFF service

**Example:**
```constellation
in orderId: String

# These run in parallel automatically
order = FetchOrder(orderId)
customer = FetchCustomer(order.customerId)
inventory = CheckInventory(order.items)
shipping = EstimateShipping(order.address)

# Merge and project - compiler validates all fields exist
response = order + customer + inventory + shipping
out response[id, customerName, tier, items, total, shippingCost, estimatedDelivery]
```

**Alternative:** If you only call 1-2 services, plain Scala/Go/Python is simpler. Constellation's value grows with composition complexity.

### 2. API Composition and Aggregation

**Scenario:** Your API gateway combines data from multiple internal services. Different endpoints need different field combinations.

**Why Constellation Shines:**
- **Type-safe field projection** - Each endpoint projects exactly what it needs; compiler catches typos
- **Reusable modules** - `FetchCustomer` used across multiple pipelines
- **Declarative resilience** - Retry/timeout/cache configured in DSL, not scattered in code
- **Versioning** - Multiple pipeline versions can coexist in the same runtime

**Example:**
```constellation
# /api/orders/summary endpoint
in orderId: String
order = FetchOrder(orderId) with retry(3, 1s)
summary = order[id, status, total, createdAt]
out summary

# /api/orders/detail endpoint
in orderId: String
order = FetchOrder(orderId) with retry(3, 1s) with cache(5m)
customer = FetchCustomer(order.customerId) with retry(3, 1s)
detail = order + customer
out detail[id, status, total, customerName, email, items]
```

**Alternative:** If your API gateway just proxies requests (no aggregation), use nginx/Envoy. Constellation is for aggregation, not routing.

### 3. Data Enrichment Pipelines

**Scenario:** You receive events and enrich them with data from multiple sources before storing or forwarding.

**Why Constellation Shines:**
- **Type-safe transformations** - Field accesses validated, no runtime surprises
- **Parallel enrichment** - Independent lookups happen concurrently
- **Error handling** - Use `when` guards and `??` coalescing for missing data
- **Observability** - DAG visualization shows enrichment flow

**Example:**
```constellation
in event: { userId: String, action: String, timestamp: String }

# Parallel enrichment
user = LookupUser(event.userId)
geo = GeolocateIP(event.ipAddress)
session = FetchSession(event.sessionId) when exists(event.sessionId)

# Combine with fallbacks
enriched = event + user + geo + { sessionDuration: session.duration ?? 0 }
out enriched
```

**Alternative:** For high-throughput streaming (millions of events/sec), use Kafka Streams or Flink. Constellation is for orchestration overhead of ~0.15ms per node, not sub-millisecond streaming.

### 4. Multi-Step Validation and Processing

**Scenario:** You validate user input, call external APIs, transform results, and store. Multiple steps with dependencies.

**Why Constellation Shines:**
- **Clear dependency chain** - DAG makes step order explicit
- **Type safety** - Each step's output is validated against next step's input
- **Composable validation** - Validation modules reused across pipelines
- **Automatic rollback** - Failed steps don't execute dependent nodes

**Example:**
```constellation
in request: { email: String, password: String, profile: Record }

# Step 1: Validation (runs in parallel)
emailValid = ValidateEmail(request.email)
passwordValid = ValidatePassword(request.password)
profileValid = ValidateProfile(request.profile)

# Step 2: Only if all valid (compiler ensures types match)
validation = emailValid + passwordValid + profileValid
user = CreateUser(request) when all([emailValid.valid, passwordValid.valid, profileValid.valid])

# Step 3: Post-creation hooks
welcome = SendWelcomeEmail(user.email) when exists(user)
analytics = TrackSignup(user.id) when exists(user)

out user
```

**Alternative:** For simple validation (1-2 fields), use your web framework's validation library. Constellation is for multi-step, multi-service validation flows.

### 5. Configuration-Driven Processing

**Scenario:** Business logic changes frequently. Non-engineers need to modify processing rules. You want hot-reload without redeploys.

**Why Constellation Shines:**
- **Hot execution** - Upload new `.cst` file, changes live immediately
- **Type-checked configuration** - Bad configs caught at compile time, not runtime
- **Versioning** - Multiple pipeline versions run concurrently during rollout
- **Dashboard visualization** - Non-engineers can see the DAG

**Example Workflow:**
1. Business team edits `.cst` file (or uses a visual editor)
2. Push to HTTP API: `POST /compile` validates types
3. If valid: `POST /pipelines` stores the pipeline
4. Execute with `POST /execute` using stored pipeline
5. Rollback by switching to previous version

**Alternative:** If logic changes are rare (quarterly+), hardcode in your application. Constellation's hot-reload is valuable when changes are frequent (weekly/daily).

## When NOT to Use Constellation

### 1. Simple CRUD Applications

**Scenario:** REST API for a single database. Create/Read/Update/Delete operations.

**Why NOT Constellation:**
- Your ORM already handles type safety
- No service composition to orchestrate
- Adding Constellation is pure overhead

**Better Alternative:**
- Use your web framework directly (http4s, Spring Boot, FastAPI)
- ORM provides sufficient type safety (Slick, Doobie, SQLAlchemy)

**Cost/Benefit:** Constellation adds ~0.15ms overhead per node. For a single DB query, that's a 10-50% latency increase for zero benefit.

### 2. Real-Time Streaming Data

**Scenario:** Process millions of events per second from Kafka. Low-latency aggregations, windowing, stateful processing.

**Why NOT Constellation:**
- Constellation's orchestration overhead (~0.15ms/node) is too high for streaming
- No built-in windowing, watermarks, or stateful operators
- Designed for request/response, not continuous streams

**Better Alternative:**
- **Kafka Streams** - JVM-native, exactly-once semantics
- **Flink** - Industry standard for stateful streaming
- **Spark Structured Streaming** - If you're already in Spark ecosystem

**What About:** Using Constellation to orchestrate stream processors (e.g., configure Flink jobs)? That's reasonable. But don't use Constellation as the stream processor itself.

### 3. Batch ETL and Data Warehousing

**Scenario:** Nightly batch jobs processing terabytes of data. Extract from sources, transform, load into warehouse.

**Why NOT Constellation:**
- No distributed computing primitives (map/reduce, shuffle, partitioning)
- Runs on a single JVM (not a cluster)
- Not optimized for data-parallel workloads

**Better Alternative:**
- **Apache Spark** - Industry standard for batch processing
- **dbt** - SQL-native transformation layer for warehouses
- **Airflow** - Workflow orchestration for batch pipelines

**What About:** Small-scale ETL (gigabytes, not terabytes)? Constellation can work, but you'll hit memory limits before you hit Spark's sweet spot.

### 4. Long-Running Workflows with Human Steps

**Scenario:** Order fulfillment process: submit → approve → ship → deliver. Human approval step can take hours/days. Requires durable state.

**Why NOT Constellation:**
- Pipelines are short-lived (seconds to minutes, not days)
- No durable state between steps
- No human task queue or approval UI

**Better Alternative:**
- **Temporal** - Durable workflows, retry across process restarts
- **Camunda** - BPMN workflows, human task management
- **AWS Step Functions** - Managed workflow orchestration

**What About:** Short-lived workflows (all automated, < 1 minute)? Constellation is fine. It's the durability and human-in-the-loop that's missing.

### 5. High-Frequency Trading or Ultra-Low Latency

**Scenario:** Microsecond latency requirements. Every nanosecond counts.

**Why NOT Constellation:**
- Constellation's orchestration overhead is ~0.15ms per node (150 microseconds)
- Built on Cats Effect (garbage-collected JVM)
- Not optimized for cache locality or NUMA awareness

**Better Alternative:**
- **C++/Rust** with manual memory management
- **Specialized trading platforms** (FIX engines, FPGA acceleration)

**What About:** Standard web API latency (10-100ms SLAs)? Constellation is fine. 0.15ms overhead is noise compared to network/DB latency.

## Decision Matrix: Constellation vs Alternatives

| Requirement | Constellation | Airflow | Temporal | Spark | Plain Scala/Go |
|-------------|---------------|---------|----------|-------|----------------|
| **Service composition (< 10 calls)** | Excellent | Overkill | Overkill | No | Good |
| **Type-safe field access** | Excellent | No | No | Schema evolution only | Depends on libraries |
| **Automatic parallelization** | Excellent | Manual task deps | Manual activities | Excellent | Manual |
| **Hot reload (no redeploy)** | Excellent | Config only | No | No | No |
| **Retry/timeout built-in** | Excellent | Yes | Excellent | No | Manual |
| **Sub-second workflows** | Excellent | Poor | Good | No | Excellent |
| **Multi-hour workflows** | No | Excellent | Excellent | Yes | No |
| **Distributed execution** | No | Yes | Yes | Excellent | No |
| **Human-in-the-loop** | No | Yes | Excellent | No | Custom |
| **Streaming data** | No | No | No | Excellent | No |
| **Learning curve** | Medium | Medium | High | Medium | Low |
| **Deployment complexity** | Low | High | High | High | Low |
| **Operational overhead** | Low | High | Medium | High | Low |

### How to Read This Matrix

- **Excellent** = This is a core strength, purpose-built for this use case
- **Good** = Works well, not the primary design goal
- **Poor** = Technically possible but painful
- **No** = Not supported or fundamentally mismatched
- **Manual** = You must implement this yourself

## Architecture Patterns That Work Well

### Pattern 1: Constellation + Plain Microservices

**Architecture:**
```
Frontend
   ↓
Constellation BFF (orchestration + type safety)
   ↓
Plain microservices (business logic + data)
```

**Why It Works:**
- Microservices focus on domain logic (single responsibility)
- Constellation focuses on composition (field mapping, merging)
- Type safety at the composition boundary catches integration bugs
- Services can be in any language; Constellation is the orchestration layer

**Anti-Pattern:** Putting complex business logic in Constellation modules. Keep modules thin (HTTP call, DB query, simple transform). Put heavy logic in services.

### Pattern 2: Constellation as API Gateway Layer

**Architecture:**
```
External clients
   ↓
API Gateway (routing, auth, rate limiting) - nginx/Envoy
   ↓
Constellation (aggregation + field projection)
   ↓
Internal services
```

**Why It Works:**
- Gateway handles infrastructure concerns (TLS, auth, rate limiting)
- Constellation handles application concerns (aggregation, transformation)
- Clear separation of responsibilities
- Hot-reload for business logic, stable gateway for infrastructure

**Anti-Pattern:** Using Constellation as your only gateway. It has basic auth/CORS/rate limiting, but nginx/Envoy are more battle-tested for edge concerns.

### Pattern 3: Constellation + Event-Driven Backend

**Architecture:**
```
Event source (Kafka, SQS, etc.)
   ↓
Constellation (enrichment + transformation)
   ↓
Event sink (Database, S3, downstream topic)
```

**Why It Works:**
- Events are batched (100-1000 per request) to amortize overhead
- Constellation enriches each event with parallel lookups
- Type-safe field access prevents schema drift bugs
- DAG visualization helps debug enrichment logic

**Anti-Pattern:** Processing events one-at-a-time. Batch them first. Constellation's ~0.15ms overhead is fine for batches, expensive for single events.

### Pattern 4: Constellation for A/B Testing Variants

**Architecture:**
```
Client request
   ↓
Router (decides variant)
   ↓
Constellation (stores multiple pipeline versions)
   ↓
Execute variant-specific pipeline
```

**Why It Works:**
- Multiple pipeline versions coexist in same runtime
- Hot-reload to deploy new variants without downtime
- Type checking ensures variants don't break contracts
- DAG diff visualization shows variant differences

**Anti-Pattern:** Using Constellation as the A/B testing framework. Use a proper feature flag system (LaunchDarkly, Unleash) to decide which pipeline to execute.

## Real-World Scenarios with Recommendations

### Scenario 1: E-Commerce Product Detail Page

**Requirements:**
- Fetch product info from catalog service
- Get real-time inventory from warehouse service
- Fetch reviews from review service
- Get personalized recommendations from ML service
- Merge all data for frontend

**Recommendation:** Use Constellation

**Why:**
- 4 independent service calls (automatic parallelization)
- Field merging and projection (`product + inventory + reviews + recommendations`)
- Type safety catches schema changes (e.g., catalog adds/removes fields)
- Hot-reload to adjust fields without redeploy

**Implementation:**
```constellation
in productId: String
in userId: Optional<String>

# Parallel fetches
product = FetchProduct(productId) with cache(5m)
inventory = CheckInventory(productId)
reviews = FetchReviews(productId) with cache(10m)
recommendations = GetRecommendations(userId, productId) when exists(userId)

# Merge and project
detail = product + inventory + reviews + { recommendations: recommendations ?? [] }
out detail[id, name, price, inStock, averageRating, reviewCount, recommendations]
```

### Scenario 2: Payment Processing Workflow

**Requirements:**
- Validate card details
- Check fraud score
- Authorize payment with payment gateway
- Store transaction record
- Send receipt email

**Recommendation:** Use Temporal (or Constellation + external state store)

**Why Temporal is Better:**
- Payment processing requires durability (can't lose state if process crashes)
- Potential for long-running operations (fraud review can take minutes/hours)
- Requires saga pattern (compensating transactions on failure)

**Why Constellation is Risky:**
- No durable state between steps
- Designed for < 1 minute workflows
- If JVM crashes mid-payment, state is lost

**When Constellation Works:**
- If all steps are idempotent and synchronous
- If entire flow completes in < 30 seconds
- If you handle durability externally (e.g., event sourcing)

### Scenario 3: Daily Report Generation

**Requirements:**
- Query database for yesterday's orders (millions of rows)
- Aggregate by category, region, customer segment
- Generate PDF report
- Upload to S3
- Email to stakeholders

**Recommendation:** Use Airflow + Spark (or dbt)

**Why:**
- Batch processing of large datasets (Constellation is single-JVM)
- Scheduled execution (Airflow's strength)
- Complex aggregations (Spark's strength)
- Constellation has no built-in scheduling or distributed compute

**When Constellation Works:**
- If dataset is small (thousands of rows, not millions)
- If aggregation is simple (no complex joins or window functions)
- If you already have a scheduler (cron, Kubernetes CronJob) and just need orchestration

### Scenario 4: Multi-Cloud Resource Provisioning

**Requirements:**
- Provision VM in AWS
- Configure networking in GCP
- Set up monitoring in Datadog
- Each step can take 1-5 minutes
- Need retries and rollback on failure

**Recommendation:** Use Terraform + Temporal

**Why:**
- Infrastructure-as-code is Terraform's domain
- Long-running operations (Temporal handles durability)
- Rollback requires infrastructure state (Terraform state)

**Why NOT Constellation:**
- Not designed for infrastructure provisioning
- No state management for infrastructure
- Modules would just shell out to Terraform/AWS CLI (why add a layer?)

### Scenario 5: Chatbot Intent Processing

**Requirements:**
- Parse user message
- Classify intent with ML model
- Fetch context from user profile
- Call appropriate backend API based on intent
- Format response for chat UI

**Recommendation:** Use Constellation

**Why:**
- Sub-second latency requirement (human conversation)
- Multiple service calls (NLP service, profile service, backend APIs)
- Type-safe field mapping (response format matters)
- Hot-reload to adjust logic (chat flows change frequently)

**Implementation:**
```constellation
in message: String
in userId: String

# Step 1: NLP
intent = ClassifyIntent(message) with timeout(500ms)
entities = ExtractEntities(message)

# Step 2: Context
user = FetchUserProfile(userId)

# Step 3: Dispatch by intent
response = branch {
  eq(intent.name, "check_balance") -> CheckBalance(user.accountId),
  eq(intent.name, "transfer_money") -> InitiateTransfer(user.accountId, entities.amount, entities.recipient),
  eq(intent.name, "transaction_history") -> GetTransactions(user.accountId),
  otherwise -> { error: "Unknown intent" }
}

out response
```

## Trade-Offs and Considerations

### Latency vs Type Safety

**Trade-Off:** Constellation adds ~0.15ms orchestration overhead per node for compile-time type safety.

**When It's Worth It:**
- API composition (network latency >> 0.15ms)
- Multi-service aggregation (parallelization saves more than overhead costs)
- Backends with history of field mapping bugs

**When It's Not:**
- Ultra-low latency requirements (< 1ms p99)
- Single-service calls (overhead is pure cost)
- High-frequency trading

### Flexibility vs Simplicity

**Trade-Off:** Constellation DSL is less flexible than general-purpose code but simpler to reason about.

**When It's Worth It:**
- Pipeline logic changes frequently (hot-reload wins)
- Non-engineers need to understand flows (DAG visualization)
- Type safety > arbitrary computation

**When It's Not:**
- Complex imperative logic (state machines, loops)
- One-off custom workflows (DSL is overhead)
- Need full language power (reflection, metaprogramming)

### Hot Reload vs Performance

**Trade-Off:** Hot execution compiles on every request. Cold execution pre-compiles for speed.

**When Hot is Worth It:**
- Development (fast iteration > performance)
- Infrequent pipelines (compilation cost amortized)
- Pipeline-as-configuration use cases

**When Cold is Better:**
- Production high-throughput APIs
- Same pipeline executed thousands of times/second
- Latency-sensitive applications

**Recommendation:** Use hot in dev, cold in production. Constellation supports both.

### Learning Curve vs Long-Term Maintainability

**Trade-Off:** Team must learn constellation-lang DSL vs using familiar languages.

**When It's Worth It:**
- Team already knows functional programming (smaller leap)
- Field mapping bugs have caused production incidents
- Pipeline logic is complex enough to justify abstraction

**When It's Not:**
- Small team (< 3 engineers) with high turnover
- Simple integration needs (1-2 service calls)
- Tight deadline, no time to learn new tools

**Mitigation:**
- DSL is small (learn in 1-2 hours)
- Modules are plain Scala (familiar to JVM developers)
- VSCode extension provides autocomplete/validation

## Frequently Asked Questions

### "We already use Airflow. Why add Constellation?"

**Different domains:**
- **Airflow** = Batch job orchestration, scheduling, retry across days
- **Constellation** = Sub-second API composition, type-safe field mapping

**Use both:**
- Airflow schedules batch jobs
- Constellation orchestrates service calls within those jobs (or separately for APIs)

**Example:** Airflow triggers nightly report. Within that job, Constellation orchestrates fetching data from 5 services, merging fields, and formatting output.

### "Can Constellation replace our service mesh (Istio, Linkerd)?"

**No, different layers:**
- **Service mesh** = Infrastructure (routing, load balancing, mTLS, observability)
- **Constellation** = Application (aggregation, transformation, type safety)

**Use both:**
- Service mesh handles service-to-service communication
- Constellation handles application-level composition

**Example:** Constellation calls `FetchCustomer(id)`. Service mesh handles: routing to healthy pod, circuit breaking, mutual TLS, distributed tracing. Constellation handles: retry policy, field projection, type validation.

### "Is Constellation a replacement for GraphQL?"

**Different approaches to similar problems:**

| Dimension | Constellation | GraphQL |
|-----------|---------------|---------|
| **Client control** | Server-side projections | Client-side queries |
| **Type safety** | Compile-time (server) | Runtime (client queries validated) |
| **Flexibility** | Pre-defined pipelines | Ad-hoc queries |
| **Performance** | Optimized per pipeline | N+1 query problem (without DataLoader) |
| **Caching** | Pipeline-level | Field-level resolvers |

**Use Constellation instead of GraphQL when:**
- You control both frontend and backend (no ad-hoc queries needed)
- You want compile-time validation of all possible queries
- You prefer pre-defined endpoints over schema exploration

**Use GraphQL instead when:**
- External clients need ad-hoc queries
- Mobile apps benefit from reducing over-fetching
- You want a single schema for all clients

**Use Both:**
- GraphQL as your public API
- Constellation as your BFF layer behind GraphQL
- GraphQL resolvers call Constellation pipelines

### "Can I use Constellation with Python/Node.js/Go services?"

**Yes!** Modules are just HTTP/gRPC calls. Your services can be in any language.

**Example:**
```scala
// Scala module that calls Python service
case class ClassifyInput(text: String)
case class ClassifyOutput(label: String, confidence: Double)

val classifyIntent = ModuleBuilder
  .metadata("ClassifyIntent", "Call Python NLP service", 1, 0)
  .implementation[ClassifyInput, ClassifyOutput] { input =>
    IO {
      val response = httpClient.post("http://nlp-service:8000/classify", input)
      ClassifyOutput(response.label, response.confidence)
    }
  }
  .build
```

**Pattern:** Constellation is the orchestration layer. Services are the implementation layer. Services don't know they're being called by Constellation.

### "What's the operational overhead?"

**Minimal for single-JVM deployments:**
- One JVM process (same as any Scala/Java app)
- No external dependencies (Postgres, Redis, etc.) required
- Health checks and metrics built-in
- Deploys like any JVM application (Docker, Kubernetes, fat JAR)

**Add these for production:**
- Reverse proxy (nginx/Envoy) for TLS termination
- Prometheus for metrics scraping
- Optional: Redis for pipeline caching (performance optimization)

**Compared to alternatives:**
- **Airflow:** Needs Postgres, Redis, workers, scheduler, webserver (5+ processes)
- **Temporal:** Needs database, multiple services (frontend, history, matching)
- **Spark:** Needs cluster manager (YARN, K8s) + distributed state

**Constellation is closer to "deploy a web service" than "operate a distributed system."**

## Migration Strategies

### From Plain Scala/Java Services

**Incremental approach:**
1. **Start with new features** - Don't rewrite existing code
2. **Wrap one service call** - Create a module for your most-called service
3. **Add a second service** - Use Constellation to compose two calls
4. **Measure impact** - Did type safety catch bugs? Did parallelization help latency?
5. **Expand gradually** - Add more modules over time

**Example timeline:**
- Week 1: Define modules for 2 existing services
- Week 2: Build one Constellation pipeline for a new feature
- Week 3: Add resilience (retry, timeout) to the pipeline
- Week 4: Add 2 more pipelines for related features
- Month 2: Evaluate - is it worth continuing?

### From Airflow

**Don't replace Airflow entirely.** Use Constellation for sub-tasks.

**Pattern:**
```python
# Airflow DAG
@dag(schedule="@daily")
def daily_report():
    # Use Airflow for scheduling and long-running tasks
    extract = extract_data_from_warehouse()  # Airflow task

    # Use Constellation for orchestration within a task
    transform = run_constellation_pipeline(
        pipeline="transform_report_data",
        inputs={"data": extract}
    )

    load = upload_to_s3(transform)  # Airflow task
```

**Benefit:** Airflow handles scheduling and durability. Constellation handles type-safe composition.

### From Microservices with Manual Composition

**Pattern:** Extract composition logic into Constellation, keep business logic in services.

**Before:**
```scala
// In your API service (40 lines of composition logic)
def getOrderDetail(orderId: String): IO[OrderDetail] = for {
  order    <- orderService.get(orderId).timeout(5.seconds).retry(3)
  customer <- customerService.get(order.customerId).timeout(5.seconds).retry(3)
  shipping <- shippingService.estimate(order.address).timeout(3.seconds)
                .handleErrorWith(_ => IO.pure(ShippingEstimate.default))
  items    <- order.items.traverse(itemId => itemService.get(itemId).timeout(3.seconds))
} yield OrderDetail(
  id = order.id,
  customerName = customer.name,
  items = items,
  total = order.total,
  shippingCost = shipping.cost
)
```

**After:**
```constellation
# order-detail.cst (10 lines)
in orderId: String

order = FetchOrder(orderId) with retry(3, 1s) with timeout(5s)
customer = FetchCustomer(order.customerId) with retry(3, 1s) with timeout(5s)
shipping = EstimateShipping(order.address) with timeout(3s) with fallback({ cost: 0 })
items = FetchItems(order.items) with timeout(3s)

detail = order + customer + { shippingCost: shipping.cost, items: items }
out detail[id, customerName, items, total, shippingCost]
```

**Benefits:**
- Composition logic is declarative (easier to reason about)
- Resilience is visible (retry/timeout in DSL, not scattered in code)
- Type-safe field access (compiler catches `customerName` typos)
- Hot-reload (change pipeline without redeploying service)

## Summary: The Constellation Sweet Spot

Use Constellation when:

1. **You compose 3+ services** - Automatic parallelization and type safety pay off
2. **Field mapping bugs hurt** - Compile-time validation of field access is valuable
3. **Logic changes frequently** - Hot-reload without redeploy saves time
4. **Resilience matters** - Declarative retry/timeout/fallback is cleaner than manual
5. **Sub-minute workflows** - Designed for request/response, not long-running processes

Don't use Constellation when:

1. **Simple CRUD** - Your ORM already handles type safety
2. **Streaming data** - Use Kafka Streams, Flink
3. **Batch ETL** - Use Spark, dbt
4. **Long-running workflows** - Use Temporal, Camunda
5. **Ultra-low latency** - Every microsecond counts

**The Sweet Spot:**
- 3-10 service calls per request
- 10-100ms total latency budget
- Services in multiple languages/teams
- Frequent pipeline logic changes
- Production impact from field mapping bugs

If that describes your problem, Constellation is worth evaluating. If not, use the alternatives listed above.

## Next Steps

### If Constellation Seems Like a Good Fit

1. **Read [Getting Started](./getting-started.md)** - Quick overview
2. **Try a proof-of-concept** - Build one pipeline for a real use case
3. **Measure impact** - Did type safety catch bugs? Did parallelization help?
4. **Expand incrementally** - Don't rewrite everything at once

### If You're Unsure

1. **Check the [Cookbook](../cookbook/index.md)** - See if examples match your problems
2. **Ask specific questions** - Open a GitHub Discussion with your use case
3. **Compare alternatives** - Use the decision matrix above to evaluate options

### If Constellation Isn't the Right Fit

That's okay! Use the right tool for your problem:

- **Airflow** - Scheduled batch workflows
- **Temporal** - Durable long-running workflows
- **Spark** - Big data batch processing
- **Kafka Streams/Flink** - Real-time streaming
- **GraphQL** - Ad-hoc client queries
- **Plain Scala/Go/Python** - Simple service composition

**Remember:** Not every problem needs a framework. Sometimes plain code is best.

---

**Related Documentation:**
- [Technical Architecture](../architecture/technical-architecture.md) - How Constellation works internally
- [Security Model](../architecture/security-model.md) - Trust boundaries and hardening
 - Detailed performance data
- [PHILOSOPHY.md](https://github.com/VledicFranco/constellation-engine/blob/master/PHILOSOPHY.md) - Design rationale
