# A/B Testing & Deployment Strategies

**Priority:** 7 (Medium)
**Target Level:** Scala module (complex logic)
**Status:** Not Implemented

---

## Overview

Safe model deployment requires gradual rollout strategies. A/B testing, canary deployments, and shadow mode allow teams to validate new models before full production exposure.

### Industry Context

> "Shadow deployment is a crucial stage because it's the first time you see how the model will perform in production... Shadow mode is useful when you don't need any user inference feedback."
> — [Shadow vs Canary Deployment - Qwak](https://www.qwak.com/post/shadow-deployment-vs-canary-release-of-machine-learning-models)

> "Many teams run both in sequence. Shadow first to catch obvious regressions, then A/B to validate user experience before full rollout."
> — [A/B Testing ML Models - Qwak](https://www.qwak.com/academy/ab-testing-ml-models)

### Deployment Stages

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     MODEL DEPLOYMENT PIPELINE                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  1. SHADOW MODE (0% user exposure)                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  Request ──► Current Model ──► Response (served to user)            │ │
│  │         └──► New Model ──► Logged (not served)                      │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                              │                                           │
│                              ▼ (if metrics look good)                    │
│  2. CANARY (1-10% user exposure)                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  Request ──► Router ──┬──► Current Model (90%) ──► Response         │ │
│  │                       └──► New Model (10%) ──► Response             │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                              │                                           │
│                              ▼ (if metrics look good)                    │
│  3. A/B TEST (50/50 or custom split)                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  Request ──► Router ──┬──► Model A (50%) ──► Response               │ │
│  │                       └──► Model B (50%) ──► Response               │ │
│  │                       (track conversion, engagement, etc.)           │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                              │                                           │
│                              ▼ (if winning variant identified)           │
│  4. FULL ROLLOUT (100%)                                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  Request ──► Winning Model ──► Response                             │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Constellation-Lang Level

### Built-in Functions

```
// Route to model variant based on experiment
ABRoute(
  entity_id: String,
  experiment: String,
  variants: List<String>
) -> String

// Route with explicit weights
ABRouteWeighted(
  entity_id: String,
  experiment: String,
  variants: List<{name: String, weight: Float}>
) -> String

// Check if entity is in experiment
IsInExperiment(entity_id: String, experiment: String) -> Boolean

// Get experiment variant for entity
GetVariant(entity_id: String, experiment: String) -> String
```

### Usage Examples

#### Simple A/B Test

```
in user_id: String
in features: List<Float>
out prediction: Float

// Route user to model variant
model_variant = ABRoute(user_id, "recommendation-model-v2-test", ["model-v1", "model-v2"])

// Call selected model
prediction = ModelPredict(model_variant, features)[0]
```

#### Weighted Canary Rollout

```
in user_id: String
in features: List<Float>
out prediction: Float

// 95% to current model, 5% to new model
model_variant = ABRouteWeighted(
  user_id,
  "canary-new-fraud-model",
  [
    {name: "fraud-model-v1", weight: 0.95},
    {name: "fraud-model-v2", weight: 0.05}
  ]
)

prediction = ModelPredict(model_variant, features)[0]
```

---

## Scala Module Level

### Experiment Configuration

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/experiments/Experiment.scala

package io.constellation.ml.experiments

import java.time.Instant

case class Experiment(
  id: String,
  name: String,
  description: String,
  variants: List[Variant],
  status: ExperimentStatus,
  startTime: Option[Instant],
  endTime: Option[Instant],
  targetingRules: List[TargetingRule] = List.empty
)

case class Variant(
  id: String,
  name: String,
  weight: Double,  // 0.0 to 1.0
  modelName: Option[String] = None,
  config: Map[String, Any] = Map.empty
)

sealed trait ExperimentStatus
object ExperimentStatus {
  case object Draft extends ExperimentStatus
  case object Running extends ExperimentStatus
  case object Paused extends ExperimentStatus
  case object Completed extends ExperimentStatus
}

sealed trait TargetingRule
object TargetingRule {
  case class UserIdModulo(modulo: Int, remainder: Int) extends TargetingRule
  case class UserAttribute(attribute: String, values: Set[String]) extends TargetingRule
  case class Percentage(percent: Double) extends TargetingRule
}
```

### Experiment Router

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/experiments/ExperimentRouter.scala

package io.constellation.ml.experiments

import cats.effect.IO

trait ExperimentRouter {
  /**
   * Get the variant for an entity in an experiment.
   * Returns None if entity is not in experiment.
   */
  def getVariant(entityId: String, experimentId: String): IO[Option[Variant]]

  /**
   * Check if entity is in experiment.
   */
  def isInExperiment(entityId: String, experimentId: String): IO[Boolean]

  /**
   * Record exposure (for tracking purposes).
   */
  def recordExposure(entityId: String, experimentId: String, variantId: String): IO[Unit]

  /**
   * Record outcome/conversion.
   */
  def recordOutcome(entityId: String, experimentId: String, outcome: String, value: Double): IO[Unit]
}

class HashBasedExperimentRouter(
  experiments: Map[String, Experiment],
  exposureTracker: ExposureTracker
) extends ExperimentRouter {

  override def getVariant(entityId: String, experimentId: String): IO[Option[Variant]] = IO {
    experiments.get(experimentId).filter(_.status == ExperimentStatus.Running).flatMap { experiment =>
      // Check targeting rules
      if (!matchesTargeting(entityId, experiment.targetingRules)) {
        None
      } else {
        // Consistent hash-based assignment
        val hash = consistentHash(entityId, experimentId)
        val normalizedHash = (hash & 0x7FFFFFFF) / Int.MaxValue.toDouble

        // Select variant based on cumulative weights
        var cumulative = 0.0
        experiment.variants.find { variant =>
          cumulative += variant.weight
          normalizedHash < cumulative
        }
      }
    }
  }

  override def isInExperiment(entityId: String, experimentId: String): IO[Boolean] = {
    getVariant(entityId, experimentId).map(_.isDefined)
  }

  override def recordExposure(entityId: String, experimentId: String, variantId: String): IO[Unit] = {
    exposureTracker.record(ExposureEvent(entityId, experimentId, variantId, Instant.now()))
  }

  override def recordOutcome(entityId: String, experimentId: String, outcome: String, value: Double): IO[Unit] = {
    exposureTracker.recordOutcome(OutcomeEvent(entityId, experimentId, outcome, value, Instant.now()))
  }

  private def consistentHash(entityId: String, experimentId: String): Int = {
    import scala.util.hashing.MurmurHash3
    MurmurHash3.stringHash(s"$experimentId:$entityId")
  }

  private def matchesTargeting(entityId: String, rules: List[TargetingRule]): Boolean = {
    if (rules.isEmpty) true
    else rules.forall {
      case TargetingRule.UserIdModulo(modulo, remainder) =>
        Math.abs(entityId.hashCode % modulo) == remainder
      case TargetingRule.Percentage(percent) =>
        val hash = Math.abs(entityId.hashCode % 10000)
        hash < (percent * 100)
      case _ => true
    }
  }
}
```

### Shadow Mode Implementation

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/experiments/ShadowMode.scala

package io.constellation.ml.experiments

import cats.effect.IO
import cats.implicits._

class ShadowModeExecutor(
  productionModel: ModelClient,
  shadowModel: ModelClient,
  comparisonLogger: ComparisonLogger
) {

  case class ShadowResult(
    productionResult: Map[String, Any],
    shadowResult: Option[Map[String, Any]],
    comparison: Option[PredictionComparison]
  )

  def executeWithShadow(
    modelName: String,
    inputs: Map[String, Any],
    shadowModelName: String
  ): IO[ShadowResult] = {
    // Run both models in parallel
    val productionIO = productionModel.predict(modelName, inputs)
    val shadowIO = shadowModel.predict(shadowModelName, inputs).attempt

    (productionIO, shadowIO).parMapN { (prodResult, shadowAttempt) =>
      val shadowResult = shadowAttempt.toOption
      val comparison = shadowResult.map(sr => comparePredictions(prodResult, sr))

      // Log comparison asynchronously
      comparison.foreach { c =>
        comparisonLogger.log(ComparisonRecord(
          timestamp = Instant.now(),
          productionModel = modelName,
          shadowModel = shadowModelName,
          inputs = inputs,
          productionOutput = prodResult,
          shadowOutput = shadowResult,
          comparison = c
        )).start  // Fire and forget
      }

      ShadowResult(prodResult, shadowResult, comparison)
    }
  }

  private def comparePredictions(
    production: Map[String, Any],
    shadow: Map[String, Any]
  ): PredictionComparison = {
    // Compare outputs
    val productionPred = production.get("prediction").map(_.toString.toDouble).getOrElse(0.0)
    val shadowPred = shadow.get("prediction").map(_.toString.toDouble).getOrElse(0.0)

    PredictionComparison(
      absoluteDiff = Math.abs(productionPred - shadowPred),
      relativeDiff = if (productionPred != 0) Math.abs((productionPred - shadowPred) / productionPred) else 0.0,
      sameDecision = (productionPred > 0.5) == (shadowPred > 0.5)  // For binary classification
    )
  }
}

case class PredictionComparison(
  absoluteDiff: Double,
  relativeDiff: Double,
  sameDecision: Boolean
)
```

### Canary Deployment Manager

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/experiments/CanaryManager.scala

package io.constellation.ml.experiments

import cats.effect.IO

case class CanaryConfig(
  experimentId: String,
  productionModel: String,
  canaryModel: String,
  initialCanaryPercent: Double = 0.01,
  maxCanaryPercent: Double = 0.5,
  incrementStep: Double = 0.05,
  rollbackThreshold: RollbackThreshold
)

case class RollbackThreshold(
  maxErrorRate: Double = 0.05,
  maxLatencyP99Ms: Long = 500,
  minSuccessRate: Double = 0.95
)

class CanaryManager(
  experimentRouter: ExperimentRouter,
  metricsCollector: MetricsCollector
) {

  def getCurrentCanaryPercent(experimentId: String): IO[Double] = {
    // Read from experiment config
    ???
  }

  def incrementCanary(experimentId: String): IO[Either[String, Double]] = {
    for {
      metrics <- metricsCollector.getCanaryMetrics(experimentId)
      config <- getConfig(experimentId)

      result <- if (shouldRollback(metrics, config.rollbackThreshold)) {
        rollback(experimentId).as(Left("Rollback triggered due to metric threshold breach"))
      } else if (metrics.canaryPercent >= config.maxCanaryPercent) {
        IO.pure(Left("Already at max canary percentage"))
      } else {
        val newPercent = Math.min(metrics.canaryPercent + config.incrementStep, config.maxCanaryPercent)
        updateCanaryPercent(experimentId, newPercent).as(Right(newPercent))
      }
    } yield result
  }

  def rollback(experimentId: String): IO[Unit] = {
    updateCanaryPercent(experimentId, 0.0) *>
      IO(logger.warn(s"Rolled back canary for experiment $experimentId"))
  }

  private def shouldRollback(metrics: CanaryMetrics, threshold: RollbackThreshold): Boolean = {
    metrics.errorRate > threshold.maxErrorRate ||
      metrics.latencyP99Ms > threshold.maxLatencyP99Ms ||
      metrics.successRate < threshold.minSuccessRate
  }

  private def updateCanaryPercent(experimentId: String, percent: Double): IO[Unit] = ???
  private def getConfig(experimentId: String): IO[CanaryConfig] = ???
}

case class CanaryMetrics(
  canaryPercent: Double,
  errorRate: Double,
  successRate: Double,
  latencyP99Ms: Long,
  sampleSize: Long
)
```

### Constellation Module Wrappers

```scala
// modules/lang-stdlib/src/main/scala/io/constellation/stdlib/experiments/ExperimentOps.scala

package io.constellation.stdlib.experiments

import io.constellation._
import io.constellation.ml.experiments._

class ExperimentOps(experimentRouter: ExperimentRouter) {

  case class ABRouteInput(entityId: String, experiment: String, variants: List[String])
  case class ABRouteOutput(selectedVariant: String)

  val abRoute = ModuleBuilder
    .metadata("ABRoute", "Route entity to experiment variant", 1, 0)
    .tags("experiment", "ab-test", "routing")
    .implementationIO[ABRouteInput, ABRouteOutput] { input =>
      experimentRouter.getVariant(input.entityId, input.experiment).map {
        case Some(variant) => ABRouteOutput(variant.modelName.getOrElse(variant.name))
        case None => ABRouteOutput(input.variants.head)  // Default to first variant
      }
    }
    .build

  case class ABRouteWeightedInput(
    entityId: String,
    experiment: String,
    variants: List[VariantWeight]
  )
  case class VariantWeight(name: String, weight: Double)

  val abRouteWeighted = ModuleBuilder
    .metadata("ABRouteWeighted", "Route entity with explicit weights", 1, 0)
    .tags("experiment", "ab-test", "routing", "weighted")
    .implementationIO[ABRouteWeightedInput, ABRouteOutput] { input =>
      // Use weights to select variant
      val hash = Math.abs(s"${input.experiment}:${input.entityId}".hashCode % 10000) / 10000.0
      var cumulative = 0.0
      val selected = input.variants.find { v =>
        cumulative += v.weight
        hash < cumulative
      }.map(_.name).getOrElse(input.variants.head.name)

      IO.pure(ABRouteOutput(selected))
    }
    .build

  val allModules: List[Module.Uninitialized] = List(
    abRoute, abRouteWeighted
  )
}
```

---

## Configuration

```hocon
constellation.experiments {
  # Experiment storage
  storage {
    type = "redis"  # or "dynamodb", "postgres"

    redis {
      host = "localhost"
      port = 6379
      key-prefix = "experiments:"
    }
  }

  # Exposure tracking
  tracking {
    enabled = true
    buffer-size = 1000
    flush-interval = 5s
    destination = "kafka"  # or "kinesis", "bigquery"

    kafka {
      bootstrap-servers = "localhost:9092"
      topic = "experiment-exposures"
    }
  }

  # Canary defaults
  canary {
    initial-percent = 0.01
    increment-step = 0.05
    max-percent = 0.5
    evaluation-window = 1h

    rollback {
      max-error-rate = 0.05
      max-latency-p99-ms = 500
    }
  }
}
```

---

## Implementation Checklist

### Constellation-Lang Level

- [ ] Add `ABRoute` built-in function
- [ ] Add `ABRouteWeighted` built-in function
- [ ] Add `IsInExperiment` built-in function
- [ ] Document with examples

### Scala Module Level

- [ ] Implement `Experiment` and `Variant` models
- [ ] Implement `ExperimentRouter` trait and `HashBasedExperimentRouter`
- [ ] Implement `ShadowModeExecutor`
- [ ] Implement `CanaryManager`
- [ ] Add exposure tracking
- [ ] Create Constellation module wrappers
- [ ] Write unit and integration tests

---

## Files to Create

| File | Purpose |
|------|---------|
| `modules/ml-integrations/.../experiments/Experiment.scala` | Data models |
| `modules/ml-integrations/.../experiments/ExperimentRouter.scala` | Routing logic |
| `modules/ml-integrations/.../experiments/ShadowMode.scala` | Shadow deployment |
| `modules/ml-integrations/.../experiments/CanaryManager.scala` | Canary management |
| `modules/ml-integrations/.../experiments/ExposureTracker.scala` | Tracking |
| `modules/lang-stdlib/.../experiments/ExperimentOps.scala` | Constellation modules |

---

## Related Documents

- [Model Inference](./02-model-inference.md) - Call model variants
- [Drift Detection](./08-drift-detection.md) - Monitor model performance during rollout
- [Observability](./09-observability.md) - Track experiment metrics
