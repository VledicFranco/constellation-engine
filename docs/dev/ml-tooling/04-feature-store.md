# Feature Store Integration

**Priority:** 4 (High)
**Target Level:** Scala module (infrastructure integration)
**Status:** Not Implemented

---

## Overview

Feature stores are critical infrastructure for production ML systems. They provide consistent feature retrieval for both training and inference, solving the training-serving skew problem at the infrastructure level.

### Industry Context

> "Feast is the leading open-source feature store in 2025 and continues to be the go-to choice for ML teams that prioritize modularity, transparency, and control."
> — [Feature Stores 2025 - GoCodeo](https://www.gocodeo.com/post/top-5-feature-stores-in-2025-tecton-feast-and-beyond)

> "A big difference between Feast and Tecton is that Tecton supports transformations, so feature pipelines can be managed end-to-end within Tecton."
> — [Feature Stores Comparison](https://www.gocodeo.com/post/top-5-feature-stores-in-2025-tecton-feast-and-beyond)

### Feature Store Components

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         FEATURE STORE                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │  Feature     │  │   Offline    │  │   Online     │  │  Feature     │ │
│  │  Registry    │  │   Store      │  │   Store      │  │  Serving     │ │
│  │  ──────────  │  │  ──────────  │  │  ──────────  │  │  ──────────  │ │
│  │  Metadata    │  │  BigQuery    │  │  Redis       │  │  REST/gRPC   │ │
│  │  Schemas     │  │  Snowflake   │  │  DynamoDB    │  │  Low-latency │ │
│  │  Lineage     │  │  Parquet     │  │  Bigtable    │  │  Batch/RT    │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘ │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Feature Store Options

| Store | Type | Best For | Latency |
|-------|------|----------|---------|
| **Feast** | Open-source | Flexibility, self-hosted | 5-20ms |
| **Tecton** | Managed | Enterprise, streaming | 5-10ms |
| **Databricks FS** | Integrated | Spark-heavy orgs | 10-50ms |
| **Vertex AI FS** | Managed | GCP users | 5-15ms |
| **Amazon SageMaker FS** | Managed | AWS users | 5-20ms |

---

## Constellation-Lang Level

### Built-in Functions

```
// Basic feature lookup
FeatureLookup(store: String, entity_id: String) -> Map<String, Any>

// Multi-entity lookup
FeatureLookupBatch(store: String, entity_ids: List<String>) -> List<Map<String, Any>>

// Specific feature retrieval
GetFeature(store: String, entity_id: String, feature_name: String) -> Any

// Feature freshness check
FeatureTimestamp(store: String, entity_id: String) -> Int
```

### Usage Examples

#### Basic Feature Lookup

```
in user_id: String
out features: {purchase_count: Int, avg_order_value: Float, last_login_days: Int}

// Lookup user features from feature store
user_features = FeatureLookup("user_features", user_id)

features = {
  purchase_count: user_features["purchase_count_30d"],
  avg_order_value: user_features["avg_order_value"],
  last_login_days: user_features["days_since_last_login"]
}
```

#### Combining Multiple Feature Views

```
in user_id: String
in product_id: String
out features: List<Float>

// Lookup from different feature views
user_features = FeatureLookup("user_features", user_id)
product_features = FeatureLookup("product_features", product_id)
interaction_features = FeatureLookup("user_product_interactions",
  CacheKey("", user_id, product_id))

// Combine all features
features = Concat(
  [user_features["embedding"]],
  [product_features["embedding"]],
  [interaction_features["click_rate"], interaction_features["view_count"]]
)
```

---

## Scala Module Level

### Feature Store Client Interface

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/features/FeatureStoreClient.scala

package io.constellation.ml.features

import cats.effect.IO
import scala.concurrent.duration.FiniteDuration

trait FeatureStoreClient {
  /**
   * Get features for a single entity.
   *
   * @param featureView Name of the feature view
   * @param entityId Entity identifier
   * @param features Optional list of specific features (all if empty)
   * @return Map of feature name to value
   */
  def getOnlineFeatures(
    featureView: String,
    entityId: String,
    features: List[String] = List.empty
  ): IO[Map[String, Any]]

  /**
   * Get features for multiple entities (batch).
   */
  def getOnlineFeaturesBatch(
    featureView: String,
    entityIds: List[String],
    features: List[String] = List.empty
  ): IO[List[Map[String, Any]]]

  /**
   * Get feature metadata.
   */
  def getFeatureMetadata(featureView: String): IO[FeatureViewMetadata]

  /**
   * Health check.
   */
  def healthCheck: IO[Boolean]
}

case class FeatureViewMetadata(
  name: String,
  features: List[FeatureMetadata],
  entities: List[String],
  ttl: Option[FiniteDuration]
)

case class FeatureMetadata(
  name: String,
  dtype: String,
  description: Option[String]
)
```

### Feast Client Implementation

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/features/FeastClient.scala

package io.constellation.ml.features

import cats.effect.IO
import org.http4s.client.Client
import org.http4s.circe._
import io.circe.syntax._
import io.circe.generic.auto._

class FeastClient(
  httpClient: Client[IO],
  feastServerUrl: String,
  timeout: FiniteDuration = 5.seconds
) extends FeatureStoreClient {

  override def getOnlineFeatures(
    featureView: String,
    entityId: String,
    features: List[String]
  ): IO[Map[String, Any]] = {
    getOnlineFeaturesBatch(featureView, List(entityId), features)
      .map(_.headOption.getOrElse(Map.empty))
  }

  override def getOnlineFeaturesBatch(
    featureView: String,
    entityIds: List[String],
    features: List[String]
  ): IO[List[Map[String, Any]]] = {

    val request = FeastOnlineFeaturesRequest(
      feature_service = featureView,
      entities = entityIds.map(id => Map("entity_id" -> id)),
      features = if (features.isEmpty) None else Some(features)
    )

    httpClient
      .expect[FeastOnlineFeaturesResponse](
        Request[IO](
          method = Method.POST,
          uri = Uri.unsafeFromString(s"$feastServerUrl/get-online-features")
        ).withEntity(request.asJson)
      )
      .timeout(timeout)
      .map(parseResponse)
  }

  private def parseResponse(response: FeastOnlineFeaturesResponse): List[Map[String, Any]] = {
    val featureNames = response.metadata.feature_names
    response.results.map { row =>
      featureNames.zip(row.values).toMap
    }
  }

  override def getFeatureMetadata(featureView: String): IO[FeatureViewMetadata] = {
    httpClient
      .expect[FeastFeatureViewMetadata](
        Uri.unsafeFromString(s"$feastServerUrl/feature-views/$featureView")
      )
      .map { feast =>
        FeatureViewMetadata(
          name = feast.name,
          features = feast.features.map(f => FeatureMetadata(f.name, f.dtype, f.description)),
          entities = feast.entities,
          ttl = feast.ttl.map(_.seconds)
        )
      }
  }

  override def healthCheck: IO[Boolean] = {
    httpClient
      .expect[String](s"$feastServerUrl/health")
      .map(_ => true)
      .handleError(_ => false)
  }
}

// Feast API models
case class FeastOnlineFeaturesRequest(
  feature_service: String,
  entities: List[Map[String, String]],
  features: Option[List[String]]
)

case class FeastOnlineFeaturesResponse(
  metadata: FeastResponseMetadata,
  results: List[FeastFeatureRow]
)

case class FeastResponseMetadata(feature_names: List[String])
case class FeastFeatureRow(values: List[Any], statuses: List[String], timestamps: List[Long])
```

### Tecton Client Implementation

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/features/TectonClient.scala

package io.constellation.ml.features

import cats.effect.IO
import org.http4s.client.Client

class TectonClient(
  httpClient: Client[IO],
  tectonUrl: String,
  apiKey: String,
  timeout: FiniteDuration = 5.seconds
) extends FeatureStoreClient {

  override def getOnlineFeatures(
    featureView: String,
    entityId: String,
    features: List[String]
  ): IO[Map[String, Any]] = {

    val request = TectonGetFeaturesRequest(
      params = TectonParams(
        feature_service_name = featureView,
        join_key_map = Map("entity_id" -> entityId),
        request_context_map = Map.empty
      )
    )

    httpClient
      .expect[TectonGetFeaturesResponse](
        Request[IO](
          method = Method.POST,
          uri = Uri.unsafeFromString(s"$tectonUrl/api/v1/feature-service/get-features")
        )
        .withEntity(request.asJson)
        .putHeaders(Header.Raw(ci"Authorization", s"Tecton-key $apiKey"))
      )
      .timeout(timeout)
      .map { response =>
        response.result.features.map { f =>
          f.name -> f.value
        }.toMap
      }
  }

  // ... batch implementation
}
```

### Feature Store with Caching Layer

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/features/CachedFeatureStoreClient.scala

package io.constellation.ml.features

import cats.effect.IO
import io.constellation.ml.cache.CacheClient

class CachedFeatureStoreClient(
  underlying: FeatureStoreClient,
  cache: CacheClient,
  defaultTtl: FiniteDuration = 5.minutes
) extends FeatureStoreClient {

  override def getOnlineFeatures(
    featureView: String,
    entityId: String,
    features: List[String]
  ): IO[Map[String, Any]] = {

    val cacheKey = s"features:$featureView:$entityId"

    cache.getOrCompute[Map[String, Any]](
      cacheKey,
      underlying.getOnlineFeatures(featureView, entityId, features),
      Some(defaultTtl)
    )
  }

  override def getOnlineFeaturesBatch(
    featureView: String,
    entityIds: List[String],
    features: List[String]
  ): IO[List[Map[String, Any]]] = {

    // Check cache for each entity
    entityIds.traverse { entityId =>
      val cacheKey = s"features:$featureView:$entityId"
      cache.get[Map[String, Any]](cacheKey).map(entityId -> _)
    }.flatMap { cacheResults =>
      val cached = cacheResults.collect { case (id, Some(features)) => id -> features }.toMap
      val missing = cacheResults.collect { case (id, None) => id }

      if (missing.isEmpty) {
        // All cached
        IO.pure(entityIds.map(cached))
      } else {
        // Fetch missing from store
        underlying.getOnlineFeaturesBatch(featureView, missing, features).flatMap { fetched =>
          // Cache fetched results
          missing.zip(fetched).traverse { case (id, features) =>
            cache.set(s"features:$featureView:$id", features, Some(defaultTtl))
          }.map { _ =>
            val combined = cached ++ missing.zip(fetched).toMap
            entityIds.map(combined)
          }
        }
      }
    }
  }

  // delegate other methods
  override def getFeatureMetadata(featureView: String): IO[FeatureViewMetadata] =
    underlying.getFeatureMetadata(featureView)

  override def healthCheck: IO[Boolean] =
    underlying.healthCheck
}
```

### Constellation Module Wrapper

```scala
// modules/lang-stdlib/src/main/scala/io/constellation/stdlib/features/FeatureOps.scala

package io.constellation.stdlib.features

import io.constellation._
import io.constellation.ml.features.FeatureStoreClient

class FeatureOps(featureStore: FeatureStoreClient) {

  case class FeatureLookupInput(store: String, entityId: String)
  case class FeatureLookupOutput(features: Map[String, Any])

  val featureLookup = ModuleBuilder
    .metadata("FeatureLookup", "Lookup features from feature store", 1, 0)
    .tags("features", "ml", "lookup")
    .timeout(5.seconds)
    .implementationIO[FeatureLookupInput, FeatureLookupOutput] { input =>
      featureStore
        .getOnlineFeatures(input.store, input.entityId)
        .map(FeatureLookupOutput)
    }
    .build

  case class FeatureLookupBatchInput(store: String, entityIds: List[String])
  case class FeatureLookupBatchOutput(features: List[Map[String, Any]])

  val featureLookupBatch = ModuleBuilder
    .metadata("FeatureLookupBatch", "Batch lookup features from feature store", 1, 0)
    .tags("features", "ml", "lookup", "batch")
    .timeout(10.seconds)
    .implementationIO[FeatureLookupBatchInput, FeatureLookupBatchOutput] { input =>
      featureStore
        .getOnlineFeaturesBatch(input.store, input.entityIds)
        .map(FeatureLookupBatchOutput)
    }
    .build

  val allModules: List[Module.Uninitialized] = List(
    featureLookup,
    featureLookupBatch
  )
}
```

---

## Point-in-Time Correctness

### The Problem

Feature stores must prevent **data leakage** by ensuring features are computed using only data available at prediction time:

```
Timeline:
  t=0: User makes purchase A
  t=1: [PREDICTION TIME] What is user's purchase probability?
  t=2: User makes purchase B

At t=1, feature "purchase_count" should be 1, not 2!
```

### Solution: Feature Freshness Tracking

```scala
case class FeatureValue(
  value: Any,
  timestamp: Long,  // When this feature value was computed
  ttl: FiniteDuration
) {
  def isStale: Boolean = {
    System.currentTimeMillis() - timestamp > ttl.toMillis
  }
}

// In feature lookup
def getOnlineFeaturesWithTimestamp(
  featureView: String,
  entityId: String
): IO[(Map[String, Any], Map[String, Long])] = {
  // Returns features and their computation timestamps
  ???
}
```

---

## Configuration

```hocon
constellation.feature-store {
  # Backend selection
  backend = "feast"  # or "tecton", "databricks", etc.

  # Feast configuration
  feast {
    server-url = "http://feast-server:6566"
    timeout = 5s
  }

  # Tecton configuration
  tecton {
    url = "https://app.tecton.ai"
    api-key = ${TECTON_API_KEY}
    timeout = 5s
  }

  # Caching layer
  cache {
    enabled = true
    ttl = 5m
    backend = "redis"
  }

  # Health checks
  health-check {
    enabled = true
    interval = 30s
  }
}
```

---

## Implementation Checklist

### Constellation-Lang Level

- [ ] Add `FeatureLookup` built-in function
- [ ] Add `FeatureLookupBatch` built-in function
- [ ] Add `GetFeature` built-in function
- [ ] Document with examples

### Scala Module Level

- [ ] Implement `FeatureStoreClient` trait
- [ ] Implement `FeastClient`
- [ ] Implement `TectonClient`
- [ ] Implement `CachedFeatureStoreClient`
- [ ] Add connection pooling
- [ ] Add health check monitoring
- [ ] Create Constellation module wrappers
- [ ] Write integration tests

---

## Files to Create

| File | Purpose |
|------|---------|
| `modules/ml-integrations/.../features/FeatureStoreClient.scala` | Base trait |
| `modules/ml-integrations/.../features/FeastClient.scala` | Feast implementation |
| `modules/ml-integrations/.../features/TectonClient.scala` | Tecton implementation |
| `modules/ml-integrations/.../features/CachedFeatureStoreClient.scala` | Caching wrapper |
| `modules/lang-stdlib/.../features/FeatureOps.scala` | Constellation modules |

---

## Related Documents

- [Caching Layer](./03-caching-layer.md) - Feature caching strategies
- [Feature Transformations](./01-feature-transformations.md) - Transform retrieved features
- [Data Validation](./06-data-validation.md) - Validate feature values
