# Caching Layer

**Priority:** 3 (High)
**Target Level:** Both (constellation-lang + Scala modules)
**Status:** Not Implemented

---

## Overview

Caching is critical for ML inference pipelines. Model inference is often the most expensive operation, and many requests have overlapping inputs that can benefit from cached predictions.

### Industry Context

> "In e-commerce, using Redis meant returning recommendations in microseconds for repeat requests, versus having to recompute them with the full model serve pipeline."
> — [ML Model Serving with FastAPI and Redis](https://www.analyticsvidhya.com/blog/2025/06/ml-model-serving/)

> "A 70% reduction in compute time for repeated prompts means 70% fewer GPU-hours billed."
> — [KV Cache-Aware Routing - Red Hat](https://developers.redhat.com/articles/2025/10/07/master-kv-cache-aware-routing-llm-d-efficient-ai-inference)

### Cache Types for ML

| Cache Type | Use Case | Latency | Hit Rate |
|------------|----------|---------|----------|
| Prediction cache | Exact input → output | <5ms | 10-50% |
| Feature cache | User/entity features | <5ms | 60-90% |
| Embedding cache | Text/image embeddings | <10ms | 30-70% |
| Semantic cache | Similar inputs (LLM) | <20ms | 20-40% |

---

## Constellation-Lang Level

### Built-in Functions

```
// Basic cache operations
CacheGet(key: String) -> Any | Null
CacheSet(key: String, value: Any, ttl: Int = 300) -> Any
CacheGetOrCompute(key: String, compute: () -> Any, ttl: Int = 300) -> Any

// Typed cache operations
CacheGetFloat(key: String, default: Float) -> Float
CacheGetList(key: String, default: List<Float>) -> List<Float>

// Cache key generation
CacheKey(prefix: String, values: Any...) -> String

// Cache invalidation
CacheDelete(key: String) -> Boolean
CacheClear(pattern: String) -> Int
```

### Usage Examples

#### Cached Model Prediction

```
in user_id: String
in features: List<Float>
out prediction: Float

// Generate cache key from user and features
cache_key = CacheKey("pred", user_id, Hash(features))

// Try cache first, compute if miss
cached_result = CacheGet(cache_key)

prediction = If(cached_result != Null,
  cached_result,
  Block(
    result = ModelPredict("recommender", features),
    CacheSet(cache_key, result[0], 3600),  // Cache for 1 hour
    result[0]
  )
)
```

#### Simplified with CacheGetOrCompute

```
in user_id: String
in features: List<Float>
out prediction: Float

cache_key = CacheKey("pred", user_id, Hash(features))

prediction = CacheGetOrCompute(
  cache_key,
  () -> ModelPredict("recommender", features)[0],
  3600  // TTL in seconds
)
```

#### Feature Caching

```
in user_id: String
out features: List<Float>

// Cache user features (computed daily, cached for hours)
cache_key = CacheKey("user_features", user_id)

features = CacheGetOrCompute(
  cache_key,
  () -> FeatureLookup("user_feature_store", user_id),
  7200  // 2 hours
)
```

---

## Scala Module Level

### Cache Client Interface

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/cache/CacheClient.scala

package io.constellation.ml.cache

import cats.effect.IO
import scala.concurrent.duration.FiniteDuration

trait CacheClient {
  def get[A](key: String)(implicit decoder: CacheDecoder[A]): IO[Option[A]]

  def set[A](key: String, value: A, ttl: Option[FiniteDuration] = None)(
    implicit encoder: CacheEncoder[A]
  ): IO[Unit]

  def getOrCompute[A](key: String, compute: IO[A], ttl: Option[FiniteDuration] = None)(
    implicit encoder: CacheEncoder[A], decoder: CacheDecoder[A]
  ): IO[A]

  def delete(key: String): IO[Boolean]

  def deletePattern(pattern: String): IO[Long]

  def exists(key: String): IO[Boolean]
}

// Type class for cache serialization
trait CacheEncoder[A] {
  def encode(value: A): Array[Byte]
}

trait CacheDecoder[A] {
  def decode(bytes: Array[Byte]): Either[Throwable, A]
}
```

### Redis Cache Implementation

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/cache/RedisCacheClient.scala

package io.constellation.ml.cache

import cats.effect.IO
import dev.profunktor.redis4cats.RedisCommands
import scala.concurrent.duration.FiniteDuration

class RedisCacheClient(
  redis: RedisCommands[IO, String, Array[Byte]],
  keyPrefix: String = "constellation:"
) extends CacheClient {

  private def prefixedKey(key: String): String = s"$keyPrefix$key"

  override def get[A](key: String)(implicit decoder: CacheDecoder[A]): IO[Option[A]] = {
    redis.get(prefixedKey(key)).map {
      case Some(bytes) => decoder.decode(bytes).toOption
      case None => None
    }
  }

  override def set[A](key: String, value: A, ttl: Option[FiniteDuration])(
    implicit encoder: CacheEncoder[A]
  ): IO[Unit] = {
    val bytes = encoder.encode(value)
    ttl match {
      case Some(duration) => redis.setEx(prefixedKey(key), bytes, duration)
      case None => redis.set(prefixedKey(key), bytes)
    }
  }

  override def getOrCompute[A](key: String, compute: IO[A], ttl: Option[FiniteDuration])(
    implicit encoder: CacheEncoder[A], decoder: CacheDecoder[A]
  ): IO[A] = {
    get[A](key).flatMap {
      case Some(cached) =>
        IO.pure(cached)
      case None =>
        compute.flatTap(value => set(key, value, ttl))
    }
  }

  override def delete(key: String): IO[Boolean] = {
    redis.del(prefixedKey(key)).map(_ > 0)
  }

  override def deletePattern(pattern: String): IO[Long] = {
    redis.keys(prefixedKey(pattern)).flatMap { keys =>
      if (keys.nonEmpty) redis.del(keys.toSeq: _*)
      else IO.pure(0L)
    }
  }

  override def exists(key: String): IO[Boolean] = {
    redis.exists(prefixedKey(key))
  }
}
```

### In-Memory Cache (for testing/development)

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/cache/InMemoryCacheClient.scala

package io.constellation.ml.cache

import cats.effect.{IO, Ref}
import scala.concurrent.duration.FiniteDuration

class InMemoryCacheClient(
  store: Ref[IO, Map[String, CacheEntry]]
) extends CacheClient {

  case class CacheEntry(
    value: Array[Byte],
    expiresAt: Option[Long]
  ) {
    def isExpired: Boolean = expiresAt.exists(_ < System.currentTimeMillis())
  }

  override def get[A](key: String)(implicit decoder: CacheDecoder[A]): IO[Option[A]] = {
    store.get.map { map =>
      map.get(key).filterNot(_.isExpired).flatMap { entry =>
        decoder.decode(entry.value).toOption
      }
    }
  }

  override def set[A](key: String, value: A, ttl: Option[FiniteDuration])(
    implicit encoder: CacheEncoder[A]
  ): IO[Unit] = {
    val entry = CacheEntry(
      value = encoder.encode(value),
      expiresAt = ttl.map(d => System.currentTimeMillis() + d.toMillis)
    )
    store.update(_ + (key -> entry))
  }

  override def getOrCompute[A](key: String, compute: IO[A], ttl: Option[FiniteDuration])(
    implicit encoder: CacheEncoder[A], decoder: CacheDecoder[A]
  ): IO[A] = {
    get[A](key).flatMap {
      case Some(cached) => IO.pure(cached)
      case None => compute.flatTap(value => set(key, value, ttl))
    }
  }

  // ... other methods
}

object InMemoryCacheClient {
  def create: IO[InMemoryCacheClient] = {
    Ref.of[IO, Map[String, CacheEntry]](Map.empty).map(new InMemoryCacheClient(_))
  }
}
```

### Constellation Module Wrappers

```scala
// modules/lang-stdlib/src/main/scala/io/constellation/stdlib/cache/CacheOps.scala

package io.constellation.stdlib.cache

import io.constellation._
import io.constellation.ml.cache.CacheClient
import cats.effect.IO

class CacheOps(cacheClient: CacheClient) {

  case class CacheGetInput(key: String)
  case class CacheGetOutput(value: Option[String])  // JSON-serialized

  val cacheGet = ModuleBuilder
    .metadata("CacheGet", "Get value from cache", 1, 0)
    .tags("cache", "read")
    .implementationIO[CacheGetInput, CacheGetOutput] { input =>
      cacheClient.get[String](input.key).map(CacheGetOutput)
    }
    .build

  case class CacheSetInput(key: String, value: String, ttlSeconds: Int)
  case class CacheSetOutput(success: Boolean)

  val cacheSet = ModuleBuilder
    .metadata("CacheSet", "Set value in cache", 1, 0)
    .tags("cache", "write")
    .implementationIO[CacheSetInput, CacheSetOutput] { input =>
      val ttl = if (input.ttlSeconds > 0) Some(input.ttlSeconds.seconds) else None
      cacheClient.set(input.key, input.value, ttl).as(CacheSetOutput(true))
    }
    .build

  case class CacheKeyInput(prefix: String, parts: List[String])
  case class CacheKeyOutput(key: String)

  val cacheKey = ModuleBuilder
    .metadata("CacheKey", "Generate cache key from parts", 1, 0)
    .tags("cache", "utility")
    .implementationPure[CacheKeyInput, CacheKeyOutput] { input =>
      val key = (input.prefix :: input.parts).mkString(":")
      CacheKeyOutput(key)
    }
    .build

  val allModules: List[Module.Uninitialized] = List(
    cacheGet, cacheSet, cacheKey
  )
}
```

---

## Semantic Caching (for LLMs)

### Concept

> "If someone asks 'how can I get money back' and another asks 'how can I get refund,' a normal exact match cache sees them as completely different. Semantic Caching looks at the meaning—it uses embeddings to measure how similar two questions are."
> — [Semantic Caching with Redis](https://shilpathota.medium.com/semantic-caching-of-ai-agents-using-redis-database-b114edfa5e68)

### Implementation

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/cache/SemanticCache.scala

package io.constellation.ml.cache

import cats.effect.IO

class SemanticCache(
  embeddingClient: EmbeddingClient,
  vectorStore: VectorStore,
  cacheClient: CacheClient,
  similarityThreshold: Double = 0.95
) {

  case class CachedResponse(
    query: String,
    embedding: Array[Float],
    response: String
  )

  def getOrCompute(
    query: String,
    compute: String => IO[String],
    ttl: Option[FiniteDuration] = None
  ): IO[String] = {
    for {
      // Generate embedding for query
      queryEmbedding <- embeddingClient.embed(query)

      // Search for similar cached queries
      similar <- vectorStore.search(queryEmbedding, topK = 1)

      result <- similar.headOption match {
        case Some(hit) if hit.score >= similarityThreshold =>
          // Cache hit - return cached response
          cacheClient.get[String](hit.id).flatMap {
            case Some(cached) => IO.pure(cached)
            case None => compute(query).flatTap(r => cacheResponse(query, queryEmbedding, r, ttl))
          }

        case _ =>
          // Cache miss - compute and cache
          compute(query).flatTap(r => cacheResponse(query, queryEmbedding, r, ttl))
      }
    } yield result
  }

  private def cacheResponse(
    query: String,
    embedding: Array[Float],
    response: String,
    ttl: Option[FiniteDuration]
  ): IO[Unit] = {
    val key = generateKey(query)
    for {
      _ <- vectorStore.upsert(key, embedding)
      _ <- cacheClient.set(key, response, ttl)
    } yield ()
  }

  private def generateKey(query: String): String = {
    val hash = java.security.MessageDigest.getInstance("SHA-256")
      .digest(query.getBytes("UTF-8"))
      .take(16)
      .map("%02x".format(_))
      .mkString
    s"semantic:$hash"
  }
}
```

---

## Cache Strategies

### 1. Cache-Aside (Lazy Loading)

```scala
def cacheAside[A](key: String, compute: IO[A], ttl: FiniteDuration): IO[A] = {
  cache.get[A](key).flatMap {
    case Some(value) => IO.pure(value)
    case None => compute.flatTap(v => cache.set(key, v, Some(ttl)))
  }
}
```

### 2. Write-Through

```scala
def writeThrough[A](key: String, value: A, ttl: FiniteDuration): IO[A] = {
  for {
    _ <- cache.set(key, value, Some(ttl))
    _ <- persistToDatabase(key, value)
  } yield value
}
```

### 3. Write-Behind (Async)

```scala
def writeBehind[A](key: String, value: A, ttl: FiniteDuration): IO[A] = {
  for {
    _ <- cache.set(key, value, Some(ttl))
    // Persist asynchronously
    _ <- persistToDatabase(key, value).start
  } yield value
}
```

### 4. Refresh-Ahead

```scala
def refreshAhead[A](
  key: String,
  compute: IO[A],
  ttl: FiniteDuration,
  refreshThreshold: Double = 0.8  // Refresh when 80% of TTL elapsed
): IO[A] = {
  cache.getWithTtl[A](key).flatMap {
    case Some((value, remainingTtl)) =>
      val shouldRefresh = remainingTtl.toMillis < (ttl.toMillis * (1 - refreshThreshold))
      if (shouldRefresh) {
        // Return cached value, refresh in background
        compute.flatTap(v => cache.set(key, v, Some(ttl))).start.as(value)
      } else {
        IO.pure(value)
      }
    case None =>
      compute.flatTap(v => cache.set(key, v, Some(ttl)))
  }
}
```

---

## Cache Key Design

### Best Practices

```scala
object CacheKeyBuilder {
  // Include all inputs that affect the output
  def predictionKey(modelName: String, modelVersion: String, features: Array[Double]): String = {
    val featureHash = hashFeatures(features)
    s"pred:$modelName:$modelVersion:$featureHash"
  }

  // Include user context for personalized caches
  def userPredictionKey(userId: String, modelName: String, features: Array[Double]): String = {
    val featureHash = hashFeatures(features)
    s"upred:$userId:$modelName:$featureHash"
  }

  // Time-bucketed keys for freshness requirements
  def timeBucketedKey(prefix: String, bucketMinutes: Int): String = {
    val bucket = System.currentTimeMillis() / (bucketMinutes * 60 * 1000)
    s"$prefix:$bucket"
  }

  private def hashFeatures(features: Array[Double]): String = {
    val bytes = features.flatMap { d =>
      java.nio.ByteBuffer.allocate(8).putDouble(d).array()
    }
    java.security.MessageDigest.getInstance("MD5")
      .digest(bytes)
      .take(8)
      .map("%02x".format(_))
      .mkString
  }
}
```

---

## Performance Metrics

### Cache Effectiveness

```scala
class CacheMetrics {
  private val hits = new LongAdder()
  private val misses = new LongAdder()
  private val latencies = new ConcurrentHistogram()

  def recordHit(latencyMs: Long): Unit = {
    hits.increment()
    latencies.record(latencyMs)
  }

  def recordMiss(latencyMs: Long): Unit = {
    misses.increment()
    latencies.record(latencyMs)
  }

  def hitRate: Double = {
    val h = hits.sum()
    val m = misses.sum()
    if (h + m == 0) 0.0 else h.toDouble / (h + m)
  }

  def report: CacheReport = CacheReport(
    hits = hits.sum(),
    misses = misses.sum(),
    hitRate = hitRate,
    avgLatencyMs = latencies.mean(),
    p99LatencyMs = latencies.percentile(99)
  )
}
```

---

## Configuration

```hocon
constellation.cache {
  # Backend selection
  backend = "redis"  # or "in-memory"

  # Redis configuration
  redis {
    host = "localhost"
    port = 6379
    password = ${?REDIS_PASSWORD}
    database = 0
    key-prefix = "constellation:"

    # Connection pool
    pool {
      max-total = 50
      max-idle = 10
      min-idle = 5
    }
  }

  # Default TTLs by cache type
  ttl {
    prediction = 1h
    features = 6h
    embeddings = 24h
    semantic = 1h
  }

  # Semantic cache
  semantic {
    enabled = true
    similarity-threshold = 0.95
    embedding-model = "text-embedding-ada-002"
  }
}
```

---

## Implementation Checklist

### Constellation-Lang Level

- [ ] Add `CacheGet` built-in function
- [ ] Add `CacheSet` built-in function
- [ ] Add `CacheGetOrCompute` built-in function
- [ ] Add `CacheKey` built-in function
- [ ] Add `CacheDelete` built-in function
- [ ] Document with examples

### Scala Module Level

- [ ] Implement `CacheClient` trait
- [ ] Implement `RedisCacheClient`
- [ ] Implement `InMemoryCacheClient`
- [ ] Add cache encoders/decoders for common types
- [ ] Implement `SemanticCache` for LLM caching
- [ ] Add cache metrics collection
- [ ] Create Constellation module wrappers
- [ ] Write unit and integration tests

---

## Files to Create

| File | Purpose |
|------|---------|
| `modules/ml-integrations/.../cache/CacheClient.scala` | Base trait |
| `modules/ml-integrations/.../cache/RedisCacheClient.scala` | Redis implementation |
| `modules/ml-integrations/.../cache/InMemoryCacheClient.scala` | In-memory implementation |
| `modules/ml-integrations/.../cache/SemanticCache.scala` | Semantic caching |
| `modules/ml-integrations/.../cache/CacheMetrics.scala` | Metrics |
| `modules/lang-stdlib/.../cache/CacheOps.scala` | Constellation modules |

---

## Related Documents

- [Model Inference](./02-model-inference.md) - Cache prediction results
- [Feature Store](./04-feature-store.md) - Feature caching integration
- [Embeddings](./05-embeddings.md) - Embedding caching
