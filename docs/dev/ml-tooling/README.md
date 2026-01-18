# ML Pipeline Tooling for Constellation Engine

## Vision

Constellation Engine aims to be the go-to platform for **ML online inference pipelines**. This requires providing first-class tooling at two levels:

1. **Constellation-lang level** - High-level operations for data scientists
2. **Scala module level** - Low-level control for ML/data engineers

This document outlines the research findings and prioritized work opportunities for building these capabilities.

---

## Two-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        CONSTELLATION-LANG LEVEL                              │
│                      (For Data Scientists)                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  • High-level, declarative syntax                                           │
│  • Common transformations as built-in functions                             │
│  • No infrastructure knowledge required                                      │
│  • Focus on "what" not "how"                                                │
│                                                                              │
│  Example:                                                                    │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  in user_features: {age: Int, income: Float, category: String}        │ │
│  │  out prediction: Float                                                 │ │
│  │                                                                        │ │
│  │  normalized_age = Normalize(user_features.age, mean=35, std=12)       │ │
│  │  encoded_cat = OneHotEncode(user_features.category, ["A","B","C"])    │ │
│  │  features = Concat(normalized_age, user_features.income, encoded_cat) │ │
│  │  prediction = ModelPredict("credit-risk-v2", features)                │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          SCALA MODULE LEVEL                                  │
│                    (For ML/Data Engineers)                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  • Full programmatic control                                                │
│  • Infrastructure integration (Redis, Feast, Triton, etc.)                  │
│  • Custom retry/circuit-breaker logic                                       │
│  • Performance optimization opportunities                                    │
│  • Complex business logic                                                   │
│                                                                              │
│  Example:                                                                    │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  val modelPredict = ModuleBuilder                                     │ │
│  │    .metadata("ModelPredict", "Call model server", 1, 0)               │ │
│  │    .timeout(5.seconds)                                                │ │
│  │    .implementationIO[PredictInput, PredictOutput] { input =>         │ │
│  │      for {                                                            │ │
│  │        cached <- redisClient.get(input.cacheKey)                     │ │
│  │        result <- cached match {                                       │ │
│  │          case Some(v) => IO.pure(v)                                  │ │
│  │          case None => tritonClient.predict(input).flatMap { r =>     │ │
│  │            redisClient.set(input.cacheKey, r, ttl=1.hour).as(r)     │ │
│  │          }                                                            │ │
│  │        }                                                              │ │
│  │      } yield PredictOutput(result)                                   │ │
│  │    }                                                                  │ │
│  │    .build                                                             │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### When to Use Each Level

| Use Case | Level | Rationale |
|----------|-------|-----------|
| Simple feature normalization | constellation-lang | No infrastructure, just math |
| One-hot encoding | constellation-lang | Standard operation |
| Call external model server | Scala module | Needs retry logic, caching |
| Feature store lookup | Scala module | Infrastructure integration |
| Simple string operations | constellation-lang | Data scientist friendly |
| A/B test routing | Scala module | Complex logic, metrics |

---

## Research Findings: Industry State of the Art

Based on research into production ML systems at scale (Uber, Meta, Netflix, Google, etc.), the following capabilities are most critical:

### Sources

- [MLOps Landscape 2025 - Neptune.ai](https://neptune.ai/blog/mlops-tools-platforms-landscape)
- [FTI Pipeline Architecture - Hopsworks](https://www.hopsworks.ai/post/mlops-to-ml-systems-with-fti-pipelines)
- [Feature Stores 2025 - GoCodeo](https://www.gocodeo.com/post/top-5-feature-stores-in-2025-tecton-feast-and-beyond)
- [Inference Platforms 2025 - BentoML](https://www.bentoml.com/blog/how-to-vet-inference-platforms)
- [Vector Databases Comparison - LiquidMetal AI](https://liquidmetal.ai/casesAndBlogs/vector-comparison/)
- [ML Model Serving with Redis - Analytics Vidhya](https://www.analyticsvidhya.com/blog/2025/06/ml-model-serving/)
- [Data Drift Detection - Evidently AI](https://www.evidentlyai.com/ml-in-production/data-drift)
- [Shadow vs Canary Deployment - Qwak](https://www.qwak.com/post/shadow-deployment-vs-canary-release-of-machine-learning-models)

---

## Tooling Index (Prioritized by Impact)

### High Priority - Core ML Pipeline Needs

| Priority | Capability | Document | Target Level |
|----------|------------|----------|--------------|
| **1** | [Feature Transformations](./01-feature-transformations.md) | Encoding, normalization, scaling | Both |
| **2** | [Model Inference](./02-model-inference.md) | Call model servers (Triton, TF Serving) | Both |
| **3** | [Caching Layer](./03-caching-layer.md) | Prediction & feature caching | Both |
| **4** | [Feature Store Integration](./04-feature-store.md) | Feast, Tecton, online/offline | Scala module |
| **5** | [Embedding Operations](./05-embeddings.md) | Vector search, similarity | Both |

### Medium Priority - Production Hardening

| Priority | Capability | Document | Target Level |
|----------|------------|----------|--------------|
| **6** | [Data Validation](./06-data-validation.md) | Schema enforcement, type checking | Both |
| **7** | [A/B Testing & Deployment](./07-ab-testing.md) | Shadow, canary, traffic splitting | Scala module |
| **8** | [Drift Detection](./08-drift-detection.md) | Data & concept drift monitoring | Both |
| **9** | [Observability](./09-observability.md) | Metrics, logging, tracing | Scala module |

### Lower Priority - Advanced Capabilities

| Priority | Capability | Document | Target Level |
|----------|------------|----------|--------------|
| **10** | [External API Integration](./10-external-apis.md) | REST calls, retries, circuit breakers | Scala module |
| **11** | [Batch Processing](./11-batch-processing.md) | Micro-batching, throughput optimization | Scala module |
| **12** | [Ensemble & Aggregation](./12-ensemble.md) | Multi-model combination | Both |

---

## Implementation Strategy

### Phase 1: Foundation (Highest Impact)

```
Week 1-2: Feature Transformations (constellation-lang)
  └── Normalize, OneHotEncode, StandardScale, etc.

Week 3-4: Model Inference (both levels)
  └── ModelPredict built-in + TritonClient module

Week 5-6: Caching Layer (both levels)
  └── Cache built-in + Redis module
```

### Phase 2: Production Readiness

```
Week 7-8: Feature Store Integration
  └── FeastClient module for online feature lookup

Week 9-10: Data Validation
  └── Validate built-in + schema enforcement

Week 11-12: Observability
  └── Metrics export, tracing integration
```

### Phase 3: Advanced Features

```
Week 13-14: Embedding Operations
  └── Vector similarity, FAISS integration

Week 15-16: A/B Testing
  └── Traffic splitting, shadow mode support

Week 17-18: Drift Detection
  └── Distribution monitoring, alerts
```

---

## Design Principles

### 1. Training-Serving Consistency

The #1 cause of ML production issues is training-serving skew. Constellation must ensure:

```
┌─────────────────────────────────────────────────────────────────┐
│                    TRAINING TIME                                 │
│  raw_data ──► normalize(mean=μ, std=σ) ──► model.fit()         │
└─────────────────────────────────────────────────────────────────┘
                              │
                    SAME μ, σ values
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    INFERENCE TIME                                │
│  raw_input ──► normalize(mean=μ, std=σ) ──► model.predict()    │
└─────────────────────────────────────────────────────────────────┘
```

**Solution:** Store transformation parameters alongside DAG definitions.

### 2. Latency Budget Awareness

Every operation in an inference path has a latency budget:

| Operation | Typical Budget | Notes |
|-----------|----------------|-------|
| Feature lookup | 1-5ms | From feature store |
| Transformation | <1ms | CPU-bound |
| Model inference | 10-100ms | GPU-bound |
| Total pipeline | 50-200ms | End-to-end |

**Solution:** Profile-guided optimization, caching at every layer.

### 3. Graceful Degradation

Production systems must handle failures gracefully:

```scala
// Example: fallback to cached prediction if model fails
val prediction = modelPredict(features)
  .timeoutTo(100.millis, cachedPrediction(userId))
  .handleErrorWith(_ => defaultPrediction)
```

**Solution:** Built-in fallback support, circuit breakers.

---

## Common Patterns

### Pattern 1: Feature Engineering Pipeline

```
in raw_data: {user_id: String, amount: Float, category: String}
out features: List<Float>

// Normalize numerical features
norm_amount = Normalize(raw_data.amount, mean=1000, std=500)

// Encode categorical
cat_encoded = OneHotEncode(raw_data.category, ["food", "travel", "retail"])

// Lookup historical features
user_features = FeatureLookup("user_features", raw_data.user_id)

// Combine all features
features = Concat(norm_amount, cat_encoded, user_features)
```

### Pattern 2: Cached Model Inference

```
in features: List<Float>
in cache_key: String
out prediction: Float

// Check cache first
cached = CacheGet(cache_key)

// Call model if cache miss
model_result = ModelPredict("fraud-detector", features)

// Update cache and return
prediction = CacheSet(cache_key, model_result, ttl=300)
```

### Pattern 3: A/B Test with Fallback

```
in user_id: String
in features: List<Float>
out prediction: Float

// Route based on experiment
model_name = ABRoute(user_id, "experiment-123", ["model-v1", "model-v2"])

// Call selected model with fallback
prediction = ModelPredictWithFallback(model_name, features, default=0.5)
```

---

## File Organization

```
modules/
├── lang-stdlib/
│   └── src/main/scala/io/constellation/stdlib/
│       ├── StdLib.scala              # Core standard library
│       ├── ml/
│       │   ├── Transformations.scala # Normalize, Encode, Scale
│       │   ├── Validation.scala      # Schema validation
│       │   └── Embeddings.scala      # Vector operations
│       └── cache/
│           └── CacheOps.scala        # Cache get/set
│
├── ml-integrations/                  # NEW MODULE
│   └── src/main/scala/io/constellation/ml/
│       ├── inference/
│       │   ├── TritonClient.scala    # NVIDIA Triton
│       │   ├── TFServingClient.scala # TensorFlow Serving
│       │   └── GenericModelClient.scala
│       ├── features/
│       │   ├── FeastClient.scala     # Feast feature store
│       │   └── FeatureCacheClient.scala
│       ├── vectors/
│       │   ├── FAISSClient.scala     # Vector similarity
│       │   └── PineconeClient.scala
│       ├── cache/
│       │   ├── RedisCacheClient.scala
│       │   └── MemcachedClient.scala
│       └── monitoring/
│           ├── MetricsExporter.scala
│           └── DriftDetector.scala
```

---

## Success Metrics

### Developer Experience

| Metric | Target | Measurement |
|--------|--------|-------------|
| Time to first inference | <15 min | New user onboarding |
| Lines of code for standard pipeline | <20 | vs. raw Python/Spark |
| Documentation completeness | 100% | All modules documented |

### Performance

| Metric | Target | Measurement |
|--------|--------|-------------|
| Transformation overhead | <1ms | Feature transforms |
| Cache hit latency | <5ms | Redis lookup |
| End-to-end p99 | <100ms | Full pipeline |

### Reliability

| Metric | Target | Measurement |
|--------|--------|-------------|
| Uptime | 99.9% | Production deployments |
| Fallback success rate | >99% | When primary fails |
| Training-serving skew | 0 | Transformation consistency |

---

## Next Steps

1. Review individual capability documents
2. Prioritize based on your specific use cases
3. Start with [Feature Transformations](./01-feature-transformations.md) and [Model Inference](./02-model-inference.md)
4. Provide feedback on API design before implementation
