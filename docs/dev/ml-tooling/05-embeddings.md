# Embedding & Vector Operations

**Priority:** 5 (High)
**Target Level:** Both (constellation-lang + Scala modules)
**Status:** Not Implemented

---

## Overview

Embeddings are the foundation of modern ML systems—from semantic search and recommendations to RAG pipelines and similarity matching. Constellation needs first-class support for generating, storing, and searching embeddings.

### Industry Context

> "Meta uses FAISS in production at billions of vectors. The library is 8.5x faster than previous best methods for similarity search."
> — [Vector Database Comparison - LiquidMetal AI](https://liquidmetal.ai/casesAndBlogs/vector-comparison/)

> "Combining symbolic (keyword) and semantic search creates massive savings without hurting relevance. Use BM25/keyword retrieval to fetch top 200–500 candidates cheaply. Apply embedding re-ranking on that subset for semantic accuracy."
> — [Vector Databases 2025](https://lakefs.io/blog/12-vector-databases-2023/)

### Vector Database Options

| Database | Type | Best For | Latency | Scale |
|----------|------|----------|---------|-------|
| **FAISS** | Library | Max performance, self-managed | <5ms | Billions |
| **Pinecone** | Managed | Production, zero ops | 5-20ms | Millions |
| **Weaviate** | Open-source | Hybrid search | 10-50ms | Millions |
| **Qdrant** | Open-source | Filtering + vectors | 10-30ms | Millions |
| **Milvus** | Open-source | Large scale | 10-50ms | Billions |
| **pgvector** | Extension | PostgreSQL users | 20-100ms | Millions |

---

## Constellation-Lang Level

### Built-in Functions

#### Embedding Generation

```
// Generate embedding from text
Embed(model: String, text: String) -> List<Float>

// Batch embedding
EmbedBatch(model: String, texts: List<String>) -> List<List<Float>>

// Embedding with caching
EmbedCached(model: String, text: String, ttl: Int = 3600) -> List<Float>
```

#### Vector Operations

```
// Cosine similarity
CosineSimilarity(vec_a: List<Float>, vec_b: List<Float>) -> Float

// Euclidean distance
EuclideanDistance(vec_a: List<Float>, vec_b: List<Float>) -> Float

// Dot product
DotProduct(vec_a: List<Float>, vec_b: List<Float>) -> Float

// Normalize vector
NormalizeVector(vec: List<Float>) -> List<Float>

// Vector arithmetic
VectorAdd(vec_a: List<Float>, vec_b: List<Float>) -> List<Float>
VectorSubtract(vec_a: List<Float>, vec_b: List<Float>) -> List<Float>
VectorScale(vec: List<Float>, scalar: Float) -> List<Float>
```

#### Vector Search

```
// Search similar vectors in index
VectorSearch(
  index: String,
  query: List<Float>,
  top_k: Int = 10,
  filter: Map<String, Any> = {}
) -> List<{id: String, score: Float, metadata: Map<String, Any>}>

// Upsert vector to index
VectorUpsert(
  index: String,
  id: String,
  vector: List<Float>,
  metadata: Map<String, Any> = {}
) -> Boolean
```

### Usage Examples

#### Semantic Search

```
in query: String
out results: List<{title: String, score: Float}>

// Generate query embedding
query_embedding = Embed("text-embedding-ada-002", query)

// Search similar documents
search_results = VectorSearch("documents", query_embedding, 10)

// Extract titles and scores
results = Map(search_results, r -> {
  title: r.metadata["title"],
  score: r.score
})
```

#### RAG Pipeline

```
in question: String
out answer: String

// 1. Embed the question
question_embedding = Embed("text-embedding-ada-002", question)

// 2. Retrieve relevant context
context_results = VectorSearch("knowledge_base", question_embedding, 5)
context = Join(Map(context_results, r -> r.metadata["content"]), "\n\n")

// 3. Generate answer with context
prompt = Concat("Context:\n", context, "\n\nQuestion: ", question)
answer = LLMGenerate("gpt-4", prompt)
```

#### Recommendation System

```
in user_id: String
in num_recommendations: Int
out recommendations: List<String>

// Get user's embedding (computed from behavior)
user_embedding = FeatureLookup("user_embeddings", user_id)["embedding"]

// Find similar items
similar_items = VectorSearch(
  "product_embeddings",
  user_embedding,
  num_recommendations + 10,  // Over-fetch for filtering
  {in_stock: true}  // Filter by metadata
)

// Filter out already purchased
purchased = FeatureLookup("user_purchases", user_id)["product_ids"]
recommendations = Filter(similar_items, r -> !Contains(purchased, r.id))
recommendations = Take(Map(recommendations, r -> r.id), num_recommendations)
```

---

## Scala Module Level

### Embedding Client Interface

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/vectors/EmbeddingClient.scala

package io.constellation.ml.vectors

import cats.effect.IO

trait EmbeddingClient {
  /**
   * Generate embedding for single text.
   */
  def embed(text: String): IO[Array[Float]]

  /**
   * Generate embeddings for multiple texts (batch).
   */
  def embedBatch(texts: List[String]): IO[List[Array[Float]]]

  /**
   * Get embedding dimension.
   */
  def dimension: Int

  /**
   * Model name.
   */
  def modelName: String
}
```

### OpenAI Embedding Client

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/vectors/OpenAIEmbeddingClient.scala

package io.constellation.ml.vectors

import cats.effect.IO
import org.http4s.client.Client
import io.circe.generic.auto._

class OpenAIEmbeddingClient(
  httpClient: Client[IO],
  apiKey: String,
  model: String = "text-embedding-ada-002"
) extends EmbeddingClient {

  private val apiUrl = "https://api.openai.com/v1/embeddings"

  override def embed(text: String): IO[Array[Float]] = {
    embedBatch(List(text)).map(_.head)
  }

  override def embedBatch(texts: List[String]): IO[List[Array[Float]]] = {
    val request = OpenAIEmbeddingRequest(
      input = texts,
      model = model
    )

    httpClient
      .expect[OpenAIEmbeddingResponse](
        Request[IO](method = Method.POST, uri = Uri.unsafeFromString(apiUrl))
          .withEntity(request.asJson)
          .putHeaders(
            Header.Raw(ci"Authorization", s"Bearer $apiKey"),
            Header.Raw(ci"Content-Type", "application/json")
          )
      )
      .map(_.data.sortBy(_.index).map(_.embedding.toArray))
  }

  override def dimension: Int = model match {
    case "text-embedding-ada-002" => 1536
    case "text-embedding-3-small" => 1536
    case "text-embedding-3-large" => 3072
    case _ => 1536
  }

  override def modelName: String = model
}

case class OpenAIEmbeddingRequest(input: List[String], model: String)
case class OpenAIEmbeddingResponse(data: List[OpenAIEmbeddingData])
case class OpenAIEmbeddingData(index: Int, embedding: List[Float])
```

### Vector Store Interface

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/vectors/VectorStore.scala

package io.constellation.ml.vectors

import cats.effect.IO

trait VectorStore {
  /**
   * Search for similar vectors.
   */
  def search(
    query: Array[Float],
    topK: Int = 10,
    filter: Map[String, Any] = Map.empty
  ): IO[List[SearchResult]]

  /**
   * Upsert a vector with metadata.
   */
  def upsert(
    id: String,
    vector: Array[Float],
    metadata: Map[String, Any] = Map.empty
  ): IO[Unit]

  /**
   * Upsert multiple vectors.
   */
  def upsertBatch(
    vectors: List[(String, Array[Float], Map[String, Any])]
  ): IO[Unit]

  /**
   * Delete vectors by ID.
   */
  def delete(ids: List[String]): IO[Unit]

  /**
   * Get vector by ID.
   */
  def get(id: String): IO[Option[(Array[Float], Map[String, Any])]]
}

case class SearchResult(
  id: String,
  score: Float,
  metadata: Map[String, Any]
)
```

### Pinecone Client

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/vectors/PineconeClient.scala

package io.constellation.ml.vectors

import cats.effect.IO
import org.http4s.client.Client

class PineconeClient(
  httpClient: Client[IO],
  apiKey: String,
  environment: String,
  indexName: String
) extends VectorStore {

  private val baseUrl = s"https://$indexName-$environment.svc.pinecone.io"

  override def search(
    query: Array[Float],
    topK: Int,
    filter: Map[String, Any]
  ): IO[List[SearchResult]] = {

    val request = PineconeQueryRequest(
      vector = query.toList,
      topK = topK,
      includeMetadata = true,
      filter = if (filter.isEmpty) None else Some(filter)
    )

    httpClient
      .expect[PineconeQueryResponse](
        Request[IO](method = Method.POST, uri = Uri.unsafeFromString(s"$baseUrl/query"))
          .withEntity(request.asJson)
          .putHeaders(Header.Raw(ci"Api-Key", apiKey))
      )
      .map(_.matches.map(m => SearchResult(m.id, m.score, m.metadata.getOrElse(Map.empty))))
  }

  override def upsert(
    id: String,
    vector: Array[Float],
    metadata: Map[String, Any]
  ): IO[Unit] = {
    upsertBatch(List((id, vector, metadata)))
  }

  override def upsertBatch(
    vectors: List[(String, Array[Float], Map[String, Any])]
  ): IO[Unit] = {
    val request = PineconeUpsertRequest(
      vectors = vectors.map { case (id, vec, meta) =>
        PineconeVector(id, vec.toList, if (meta.isEmpty) None else Some(meta))
      }
    )

    httpClient
      .expect[PineconeUpsertResponse](
        Request[IO](method = Method.POST, uri = Uri.unsafeFromString(s"$baseUrl/vectors/upsert"))
          .withEntity(request.asJson)
          .putHeaders(Header.Raw(ci"Api-Key", apiKey))
      )
      .void
  }

  // ... other methods
}
```

### FAISS Integration

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/vectors/FAISSStore.scala

package io.constellation.ml.vectors

import cats.effect.IO
import java.nio.file.Path

/**
 * FAISS-based vector store for maximum performance.
 * Requires FAISS native library to be installed.
 */
class FAISSStore(
  indexPath: Path,
  dimension: Int,
  indexType: String = "IVF1024,Flat"  // Or "HNSW32", "Flat", etc.
) extends VectorStore {

  // JNI bindings to FAISS
  @native def createIndex(dimension: Int, indexType: String): Long
  @native def addVectors(indexPtr: Long, vectors: Array[Float], ids: Array[Long]): Unit
  @native def search(indexPtr: Long, query: Array[Float], k: Int): Array[Long]
  @native def saveIndex(indexPtr: Long, path: String): Unit
  @native def loadIndex(path: String): Long

  private var indexPtr: Long = _
  private val idToMetadata = new java.util.concurrent.ConcurrentHashMap[String, Map[String, Any]]()

  def initialize: IO[Unit] = IO {
    indexPtr = if (indexPath.toFile.exists()) {
      loadIndex(indexPath.toString)
    } else {
      createIndex(dimension, indexType)
    }
  }

  override def search(
    query: Array[Float],
    topK: Int,
    filter: Map[String, Any]
  ): IO[List[SearchResult]] = IO {
    val resultIds = search(indexPtr, query, topK * 2)  // Over-fetch for filtering

    resultIds.toList
      .map(id => (id.toString, idToMetadata.getOrDefault(id.toString, Map.empty)))
      .filter { case (_, meta) => matchesFilter(meta, filter) }
      .take(topK)
      .zipWithIndex
      .map { case ((id, meta), idx) =>
        SearchResult(id, 1.0f - (idx * 0.01f), meta)  // Approximate score
      }
  }

  private def matchesFilter(meta: Map[String, Any], filter: Map[String, Any]): Boolean = {
    filter.forall { case (k, v) => meta.get(k).contains(v) }
  }

  // ... other methods
}
```

### Vector Operations Module

```scala
// modules/lang-stdlib/src/main/scala/io/constellation/stdlib/ml/VectorOps.scala

package io.constellation.stdlib.ml

import io.constellation._
import scala.math.{sqrt, pow}

object VectorOps {

  case class CosineSimilarityInput(vecA: List[Double], vecB: List[Double])
  case class CosineSimilarityOutput(similarity: Double)

  val cosineSimilarity = ModuleBuilder
    .metadata("CosineSimilarity", "Compute cosine similarity between vectors", 1, 0)
    .tags("vector", "similarity", "ml")
    .implementationPure[CosineSimilarityInput, CosineSimilarityOutput] { input =>
      val dotProduct = input.vecA.zip(input.vecB).map { case (a, b) => a * b }.sum
      val normA = sqrt(input.vecA.map(x => x * x).sum)
      val normB = sqrt(input.vecB.map(x => x * x).sum)
      val similarity = if (normA == 0 || normB == 0) 0.0 else dotProduct / (normA * normB)
      CosineSimilarityOutput(similarity)
    }
    .build

  case class EuclideanDistanceInput(vecA: List[Double], vecB: List[Double])
  case class EuclideanDistanceOutput(distance: Double)

  val euclideanDistance = ModuleBuilder
    .metadata("EuclideanDistance", "Compute Euclidean distance between vectors", 1, 0)
    .tags("vector", "distance", "ml")
    .implementationPure[EuclideanDistanceInput, EuclideanDistanceOutput] { input =>
      val distance = sqrt(input.vecA.zip(input.vecB).map { case (a, b) => pow(a - b, 2) }.sum)
      EuclideanDistanceOutput(distance)
    }
    .build

  case class NormalizeVectorInput(vec: List[Double])
  case class NormalizeVectorOutput(normalized: List[Double])

  val normalizeVector = ModuleBuilder
    .metadata("NormalizeVector", "Normalize vector to unit length", 1, 0)
    .tags("vector", "transform", "ml")
    .implementationPure[NormalizeVectorInput, NormalizeVectorOutput] { input =>
      val norm = sqrt(input.vec.map(x => x * x).sum)
      val normalized = if (norm == 0) input.vec else input.vec.map(_ / norm)
      NormalizeVectorOutput(normalized)
    }
    .build

  val allModules: List[Module.Uninitialized] = List(
    cosineSimilarity,
    euclideanDistance,
    normalizeVector
  )
}
```

---

## Hybrid Search

Combining keyword and semantic search for better results:

```scala
class HybridSearch(
  vectorStore: VectorStore,
  keywordIndex: KeywordIndex,  // e.g., Elasticsearch
  embeddingClient: EmbeddingClient
) {

  def search(
    query: String,
    topK: Int,
    keywordWeight: Double = 0.3,
    vectorWeight: Double = 0.7
  ): IO[List[SearchResult]] = {
    for {
      // Parallel: keyword search + embedding generation
      (keywordResults, queryEmbedding) <- (
        keywordIndex.search(query, topK * 2),
        embeddingClient.embed(query)
      ).parTupled

      // Vector search on keyword candidates (re-ranking)
      vectorResults <- vectorStore.search(queryEmbedding, topK * 2)

      // Combine and re-rank
      combined = combineResults(keywordResults, vectorResults, keywordWeight, vectorWeight)
    } yield combined.take(topK)
  }

  private def combineResults(
    keyword: List[SearchResult],
    vector: List[SearchResult],
    kwWeight: Double,
    vecWeight: Double
  ): List[SearchResult] = {
    val allIds = (keyword.map(_.id) ++ vector.map(_.id)).distinct

    allIds.map { id =>
      val kwScore = keyword.find(_.id == id).map(_.score).getOrElse(0f)
      val vecScore = vector.find(_.id == id).map(_.score).getOrElse(0f)
      val combinedScore = (kwWeight * kwScore + vecWeight * vecScore).toFloat
      val metadata = keyword.find(_.id == id).orElse(vector.find(_.id == id)).map(_.metadata).getOrElse(Map.empty)
      SearchResult(id, combinedScore, metadata)
    }.sortBy(-_.score)
  }
}
```

---

## Configuration

```hocon
constellation.vectors {
  # Embedding provider
  embedding {
    provider = "openai"  # or "cohere", "local"

    openai {
      api-key = ${OPENAI_API_KEY}
      model = "text-embedding-ada-002"
    }

    cohere {
      api-key = ${COHERE_API_KEY}
      model = "embed-english-v3.0"
    }
  }

  # Vector store
  store {
    provider = "pinecone"  # or "faiss", "qdrant", "weaviate"

    pinecone {
      api-key = ${PINECONE_API_KEY}
      environment = "us-east-1"
      index-name = "constellation"
    }

    faiss {
      index-path = "/data/faiss/index"
      index-type = "IVF1024,Flat"
    }
  }

  # Caching
  cache {
    embeddings {
      enabled = true
      ttl = 24h
    }
  }
}
```

---

## Implementation Checklist

### Constellation-Lang Level

- [ ] Add `Embed` built-in function
- [ ] Add `EmbedBatch` built-in function
- [ ] Add `CosineSimilarity` built-in function
- [ ] Add `EuclideanDistance` built-in function
- [ ] Add `VectorSearch` built-in function
- [ ] Add `VectorUpsert` built-in function
- [ ] Document with examples

### Scala Module Level

- [ ] Implement `EmbeddingClient` trait
- [ ] Implement `OpenAIEmbeddingClient`
- [ ] Implement `VectorStore` trait
- [ ] Implement `PineconeClient`
- [ ] Implement `FAISSStore`
- [ ] Implement `HybridSearch`
- [ ] Add embedding caching
- [ ] Create Constellation module wrappers
- [ ] Write tests and benchmarks

---

## Files to Create

| File | Purpose |
|------|---------|
| `modules/ml-integrations/.../vectors/EmbeddingClient.scala` | Base trait |
| `modules/ml-integrations/.../vectors/OpenAIEmbeddingClient.scala` | OpenAI embeddings |
| `modules/ml-integrations/.../vectors/VectorStore.scala` | Base trait |
| `modules/ml-integrations/.../vectors/PineconeClient.scala` | Pinecone integration |
| `modules/ml-integrations/.../vectors/FAISSStore.scala` | FAISS integration |
| `modules/ml-integrations/.../vectors/HybridSearch.scala` | Hybrid search |
| `modules/lang-stdlib/.../ml/VectorOps.scala` | Constellation modules |

---

## Related Documents

- [Caching Layer](./03-caching-layer.md) - Cache embeddings
- [Model Inference](./02-model-inference.md) - Use embeddings for inference
- [Feature Store](./04-feature-store.md) - Store user embeddings as features
