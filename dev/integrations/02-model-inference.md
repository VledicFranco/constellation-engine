# Model Inference Integration

**Priority:** 2 (High)
**Target Level:** Both (constellation-lang + Scala modules)
**Status:** Not Implemented

---

## Overview

Model inference is the core of any ML pipeline. Constellation needs to support calling various model serving backends while providing a consistent, easy-to-use interface.

### Industry Context

> "Trained ML models rarely succeed in production without careful attention to serving—latency, cost, and reliability hinge on deployment choices including endpoints, autoscaling, batching, hardware allocation, versioning, load balancing, and monitoring."
> — [AI Model Serving Frameworks 2025](https://www.devopsschool.com/blog/top-10-ai-model-serving-frameworks-tools-in-2025-features-pros-cons-comparison/)

### Common Model Serving Backends

| Backend | Use Case | Latency | Complexity |
|---------|----------|---------|------------|
| NVIDIA Triton | High-throughput GPU inference | 5-50ms | High |
| TensorFlow Serving | TensorFlow models | 10-100ms | Medium |
| TorchServe | PyTorch models | 10-100ms | Medium |
| Seldon Core | Kubernetes-native, any framework | 20-200ms | High |
| BentoML | Multi-framework, easy deployment | 10-100ms | Low |
| Custom HTTP/gRPC | Any model, full control | Varies | Low |

---

## Constellation-Lang Level

### Built-in Functions

```
// Basic model prediction
ModelPredict(model: String, features: List<Float>) -> List<Float>

// Prediction with named inputs/outputs
ModelPredictNamed(
  model: String,
  inputs: Map<String, Any>,
  output_name: String
) -> Any

// Prediction with fallback
ModelPredictWithFallback(
  model: String,
  features: List<Float>,
  fallback: List<Float>
) -> List<Float>

// Classification (returns class + probability)
Classify(model: String, features: List<Float>) -> {class: Int, probability: Float}

// Batch prediction (for micro-batching)
BatchPredict(model: String, batch: List<List<Float>>) -> List<List<Float>>
```

### Usage Examples

#### Simple Prediction

```
in features: List<Float>
out prediction: Float

result = ModelPredict("fraud-detector-v2", features)
prediction = result[0]
```

#### Classification with Confidence

```
in user_features: List<Float>
out risk_level: String
out confidence: Float

classification = Classify("risk-classifier", user_features)

risk_level = Match(classification.class, {
  0: "low",
  1: "medium",
  2: "high"
})

confidence = classification.probability
```

#### Multi-Model Pipeline

```
in text: String
out sentiment: String
out entities: List<String>

// Call embedding model
embeddings = ModelPredict("text-embedder", Tokenize(text))

// Call sentiment model
sentiment_scores = ModelPredict("sentiment-classifier", embeddings)
sentiment = ArgMax(sentiment_scores) == 0 ? "negative" : "positive"

// Call NER model
entity_predictions = ModelPredict("ner-model", embeddings)
entities = ExtractEntities(entity_predictions)
```

#### Prediction with Fallback

```
in features: List<Float>
out prediction: Float

// Use fallback if model fails or times out
result = ModelPredictWithFallback(
  "recommendation-model",
  features,
  [0.5]  // Default prediction
)

prediction = result[0]
```

---

## Scala Module Level

### Generic Model Client Interface

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/inference/ModelClient.scala

package io.constellation.ml.inference

import cats.effect.IO

trait ModelClient {
  def predict(
    modelName: String,
    inputs: Map[String, Any],
    timeout: FiniteDuration = 5.seconds
  ): IO[Map[String, Any]]

  def predictBatch(
    modelName: String,
    batch: List[Map[String, Any]],
    timeout: FiniteDuration = 10.seconds
  ): IO[List[Map[String, Any]]]

  def healthCheck: IO[Boolean]
}
```

### NVIDIA Triton Client

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/inference/TritonClient.scala

package io.constellation.ml.inference

import cats.effect.IO
import org.http4s.client.Client
import org.http4s.circe._
import io.circe.syntax._

class TritonClient(
  httpClient: Client[IO],
  baseUrl: String,
  defaultTimeout: FiniteDuration = 5.seconds
) extends ModelClient {

  // Triton HTTP inference endpoint
  private val inferenceUrl = s"$baseUrl/v2/models"

  override def predict(
    modelName: String,
    inputs: Map[String, Any],
    timeout: FiniteDuration
  ): IO[Map[String, Any]] = {

    val request = TritonInferRequest(
      inputs = inputs.map { case (name, value) =>
        TritonInput(
          name = name,
          shape = inferShape(value),
          datatype = inferDatatype(value),
          data = toTritonData(value)
        )
      }.toList
    )

    httpClient
      .expect[TritonInferResponse](
        Request[IO](
          method = Method.POST,
          uri = Uri.unsafeFromString(s"$inferenceUrl/$modelName/infer")
        ).withEntity(request.asJson)
      )
      .timeout(timeout)
      .map(parseTritonResponse)
  }

  override def predictBatch(
    modelName: String,
    batch: List[Map[String, Any]],
    timeout: FiniteDuration
  ): IO[List[Map[String, Any]]] = {
    // Triton supports batching natively via the batch dimension
    val batchedInputs = batchInputs(batch)
    predict(modelName, batchedInputs, timeout).map(unbatchOutputs(_, batch.size))
  }

  private def inferShape(value: Any): List[Int] = value match {
    case arr: Array[Float] => List(arr.length)
    case arr: Array[Double] => List(arr.length)
    case list: List[_] => List(list.length)
    case _ => List(1)
  }

  private def inferDatatype(value: Any): String = value match {
    case _: Float | _: Array[Float] => "FP32"
    case _: Double | _: Array[Double] => "FP64"
    case _: Int | _: Array[Int] => "INT32"
    case _: Long | _: Array[Long] => "INT64"
    case _: String => "BYTES"
    case _ => "FP32"
  }

  override def healthCheck: IO[Boolean] = {
    httpClient
      .expect[String](s"$baseUrl/v2/health/ready")
      .map(_ => true)
      .handleError(_ => false)
  }
}

// Triton protocol models
case class TritonInput(
  name: String,
  shape: List[Int],
  datatype: String,
  data: List[Any]
)

case class TritonInferRequest(inputs: List[TritonInput])
case class TritonInferResponse(outputs: List[TritonOutput])
case class TritonOutput(name: String, shape: List[Int], datatype: String, data: List[Any])
```

### TensorFlow Serving Client

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/inference/TFServingClient.scala

package io.constellation.ml.inference

import cats.effect.IO
import org.http4s.client.Client

class TFServingClient(
  httpClient: Client[IO],
  baseUrl: String,
  defaultTimeout: FiniteDuration = 5.seconds
) extends ModelClient {

  override def predict(
    modelName: String,
    inputs: Map[String, Any],
    timeout: FiniteDuration
  ): IO[Map[String, Any]] = {

    val request = TFServingRequest(
      signature_name = "serving_default",
      instances = List(inputs)
    )

    httpClient
      .expect[TFServingResponse](
        Request[IO](
          method = Method.POST,
          uri = Uri.unsafeFromString(s"$baseUrl/v1/models/$modelName:predict")
        ).withEntity(request.asJson)
      )
      .timeout(timeout)
      .map(_.predictions.head)
  }

  // ...
}

case class TFServingRequest(signature_name: String, instances: List[Map[String, Any]])
case class TFServingResponse(predictions: List[Map[String, Any]])
```

### Constellation Module Wrapper

```scala
// Create Constellation modules from model clients

object ModelInferenceModules {

  def createModelPredictModule(
    client: ModelClient,
    modelName: String,
    inputName: String = "input",
    outputName: String = "output"
  ): Module.Uninitialized = {

    case class PredictInput(features: List[Double])
    case class PredictOutput(predictions: List[Double])

    ModuleBuilder
      .metadata(s"Predict_$modelName", s"Call model $modelName", 1, 0)
      .tags("inference", "ml", modelName)
      .timeout(5.seconds)
      .implementationIO[PredictInput, PredictOutput] { input =>
        client
          .predict(modelName, Map(inputName -> input.features.toArray))
          .map { response =>
            val predictions = response(outputName) match {
              case arr: Array[Double] => arr.toList
              case arr: Array[Float] => arr.map(_.toDouble).toList
              case list: List[_] => list.map(_.toString.toDouble)
              case other => List(other.toString.toDouble)
            }
            PredictOutput(predictions)
          }
      }
      .build
  }

  def createClassifyModule(
    client: ModelClient,
    modelName: String
  ): Module.Uninitialized = {

    case class ClassifyInput(features: List[Double])
    case class ClassifyOutput(classIdx: Int, probability: Double)

    ModuleBuilder
      .metadata(s"Classify_$modelName", s"Classify using $modelName", 1, 0)
      .tags("inference", "classification", "ml")
      .timeout(5.seconds)
      .implementationIO[ClassifyInput, ClassifyOutput] { input =>
        client
          .predict(modelName, Map("input" -> input.features.toArray))
          .map { response =>
            val probabilities = response("probabilities").asInstanceOf[Array[Double]]
            val classIdx = probabilities.indices.maxBy(probabilities)
            ClassifyOutput(classIdx, probabilities(classIdx))
          }
      }
      .build
  }
}
```

---

## Resilience Patterns

### Timeout Handling

```scala
def predictWithTimeout(
  client: ModelClient,
  modelName: String,
  features: List[Double],
  timeout: FiniteDuration,
  fallback: List[Double]
): IO[List[Double]] = {
  client
    .predict(modelName, Map("input" -> features))
    .timeout(timeout)
    .map(r => r("output").asInstanceOf[List[Double]])
    .handleError(_ => fallback)
}
```

### Circuit Breaker

```scala
import io.github.resilience4j.circuitbreaker.CircuitBreaker

class ResilientModelClient(
  underlying: ModelClient,
  circuitBreaker: CircuitBreaker
) extends ModelClient {

  override def predict(
    modelName: String,
    inputs: Map[String, Any],
    timeout: FiniteDuration
  ): IO[Map[String, Any]] = {
    IO.fromCallable { () =>
      circuitBreaker.executeCallable { () =>
        underlying.predict(modelName, inputs, timeout).unsafeRunSync()
      }
    }
  }
}
```

### Retry with Backoff

```scala
import cats.effect.IO
import retry._

def predictWithRetry(
  client: ModelClient,
  modelName: String,
  features: Map[String, Any]
): IO[Map[String, Any]] = {

  val policy = RetryPolicies.limitRetries[IO](3) |+|
    RetryPolicies.exponentialBackoff[IO](100.millis)

  retryingOnAllErrors(
    policy = policy,
    onError = (err, details) => IO(logger.warn(s"Retry ${details.retriesSoFar}: $err"))
  ) {
    client.predict(modelName, features)
  }
}
```

---

## Model Versioning

### Version Selection

```scala
case class ModelVersion(
  name: String,
  version: String,
  isDefault: Boolean,
  trafficWeight: Double  // For gradual rollout
)

class VersionedModelClient(
  underlying: ModelClient,
  versions: List[ModelVersion]
) extends ModelClient {

  override def predict(
    modelName: String,
    inputs: Map[String, Any],
    timeout: FiniteDuration
  ): IO[Map[String, Any]] = {
    val selectedVersion = selectVersion(modelName)
    underlying.predict(s"$modelName:$selectedVersion", inputs, timeout)
  }

  private def selectVersion(modelName: String): String = {
    val modelVersions = versions.filter(_.name == modelName)

    // Weighted random selection for gradual rollout
    val random = scala.util.Random.nextDouble()
    var cumulative = 0.0
    modelVersions.find { v =>
      cumulative += v.trafficWeight
      random < cumulative
    }.map(_.version).getOrElse("latest")
  }
}
```

### Constellation-Lang Support

```
// Use specific version
prediction = ModelPredict("fraud-detector:v2.1", features)

// Use latest (default)
prediction = ModelPredict("fraud-detector", features)
```

---

## Performance Optimization

### Connection Pooling

```scala
import org.http4s.ember.client.EmberClientBuilder

val clientResource = EmberClientBuilder
  .default[IO]
  .withMaxTotal(100)           // Max connections
  .withMaxPerKey(_ => 20)      // Max per host
  .withIdleTimeInPool(30.seconds)
  .build
```

### Micro-Batching

```scala
class BatchingModelClient(
  underlying: ModelClient,
  maxBatchSize: Int = 32,
  maxWaitTime: FiniteDuration = 10.millis
) {
  private val queue = new LinkedBlockingQueue[PendingRequest]()

  def predict(modelName: String, inputs: Map[String, Any]): IO[Map[String, Any]] = {
    IO.async_ { cb =>
      queue.add(PendingRequest(modelName, inputs, cb))
      maybeFlush()
    }
  }

  private def maybeFlush(): Unit = {
    if (queue.size >= maxBatchSize) {
      flush()
    } else {
      // Schedule flush after maxWaitTime
      scheduler.schedule(() => flush(), maxWaitTime)
    }
  }

  private def flush(): Unit = {
    val batch = new java.util.ArrayList[PendingRequest]()
    queue.drainTo(batch, maxBatchSize)

    if (!batch.isEmpty) {
      val modelName = batch.get(0).modelName
      val inputs = batch.asScala.map(_.inputs).toList

      underlying.predictBatch(modelName, inputs).attempt.unsafeRunSync() match {
        case Right(results) =>
          batch.asScala.zip(results).foreach { case (req, result) =>
            req.callback(Right(result))
          }
        case Left(error) =>
          batch.asScala.foreach(_.callback(Left(error)))
      }
    }
  }
}
```

---

## Configuration

```hocon
constellation.ml.inference {
  # Default model client
  default-backend = "triton"

  # Triton configuration
  triton {
    url = "http://triton-server:8000"
    timeout = 5s
    max-connections = 50
  }

  # TensorFlow Serving configuration
  tensorflow {
    url = "http://tf-serving:8501"
    timeout = 10s
  }

  # Resilience
  circuit-breaker {
    failure-rate-threshold = 50
    wait-duration-in-open-state = 30s
    sliding-window-size = 10
  }

  # Batching
  micro-batching {
    enabled = true
    max-batch-size = 32
    max-wait-time = 10ms
  }
}
```

---

## Implementation Checklist

### Constellation-Lang Level

- [ ] Add `ModelPredict` built-in function
- [ ] Add `ModelPredictWithFallback` built-in function
- [ ] Add `Classify` built-in function
- [ ] Add `BatchPredict` built-in function
- [ ] Support model versioning syntax (`model:version`)
- [ ] Document all functions with examples

### Scala Module Level

- [ ] Implement `ModelClient` trait
- [ ] Implement `TritonClient`
- [ ] Implement `TFServingClient`
- [ ] Implement `GenericHttpModelClient`
- [ ] Add circuit breaker wrapper
- [ ] Add retry with backoff
- [ ] Add micro-batching client
- [ ] Add connection pooling configuration
- [ ] Write integration tests
- [ ] Write performance benchmarks

---

## Files to Create

| File | Purpose |
|------|---------|
| `modules/ml-integrations/.../inference/ModelClient.scala` | Base trait |
| `modules/ml-integrations/.../inference/TritonClient.scala` | NVIDIA Triton |
| `modules/ml-integrations/.../inference/TFServingClient.scala` | TensorFlow Serving |
| `modules/ml-integrations/.../inference/ResilientClient.scala` | Circuit breaker |
| `modules/ml-integrations/.../inference/BatchingClient.scala` | Micro-batching |
| `modules/lang-stdlib/.../ml/Inference.scala` | Constellation modules |

---

## Related Documents

- [Caching Layer](./03-caching-layer.md) - Cache prediction results
- [A/B Testing](./07-ab-testing.md) - Route between model versions
- [Observability](./09-observability.md) - Monitor inference latency
