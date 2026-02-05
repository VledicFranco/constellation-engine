# When to Use Constellation

> **Path**: `docs/overview/when-to-use.md`
> **Parent**: [overview/](./README.md)

## Good Fit

| Use Case | Why Constellation Works |
|----------|------------------------|
| **API composition (BFF)** | Orchestrate multiple backend calls, merge results, type-safe |
| **Data enrichment** | Fan-out to services, combine with type algebra |
| **ML inference pipelines** | Batch processing with Candidates, parallel model calls |
| **Multi-service aggregation** | Automatic parallelization, retry/timeout per service |
| **Type-critical workflows** | Compile-time field validation catches errors early |

## Poor Fit

| Use Case | Better Alternative |
|----------|-------------------|
| Simple CRUD operations | ORM (Slick, Doobie) |
| Stream processing | Kafka Streams, Flink, Akka Streams |
| Large-scale ETL | Spark, dbt |
| Single-service calls | Direct HTTP client |
| Prototype/throwaway scripts | Direct Scala/Python |

## Decision Criteria

**Use Constellation when:**
- You have 3+ services to coordinate
- Field access errors have caused production incidents
- You need observability into multi-step workflows
- Non-developers need to compose pipelines
- You want hot-reload without restarts

**Don't use Constellation when:**
- Simple request-response patterns suffice
- You need streaming/unbounded data
- Latency is sub-millisecond critical (compile overhead)
- Team is unfamiliar with typed functional patterns

## Comparison

| Aspect | Constellation | Manual Scala | Workflow Engines (Temporal) |
|--------|--------------|--------------|----------------------------|
| Type safety | Compile-time DSL validation | Scala compiler only | Runtime validation |
| Learning curve | Moderate (new DSL) | Low | High (state machines) |
| Hot reload | Yes | No (redeploy) | Yes |
| Long-running | Suspend/resume | Manual | Native |
| Observability | Built-in DAG tracing | Manual | Built-in |
