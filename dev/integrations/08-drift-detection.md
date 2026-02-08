# Drift Detection & Monitoring

**Priority:** 8 (Medium)
**Target Level:** Both (constellation-lang + Scala modules)
**Status:** Not Implemented

---

## Overview

ML models degrade over time as the world changes. Drift detection identifies when input data or model behavior has shifted from training conditions, signaling the need for retraining or investigation.

### Industry Context

> "Data drift refers to changes in the distribution of the features an ML model receives in production, potentially causing a decline in model performance."
> — [Data Drift - Evidently AI](https://www.evidentlyai.com/ml-in-production/data-drift)

> "When drift exceeds a predefined limit, trigger workflows that retrain the model on recent data, run evaluation suites, and stage the candidate for canary rollout."
> — [Advanced ML Model Monitoring](https://enhancedmlops.com/advanced-ml-model-monitoring-drift-detection-explainability-and-automated-retraining/)

### Types of Drift

| Type | Definition | Detection Method |
|------|------------|------------------|
| **Data Drift** | Input feature distributions change | Statistical tests |
| **Concept Drift** | Relationship between X and Y changes | Performance monitoring |
| **Prediction Drift** | Model output distribution changes | Output monitoring |
| **Label Drift** | Target variable distribution changes | Ground truth monitoring |

---

## Constellation-Lang Level

### Built-in Functions

```
// Check if value is within expected distribution
IsAnomaly(
  value: Float,
  baseline_mean: Float,
  baseline_std: Float,
  threshold: Float = 3.0  // z-score threshold
) -> Boolean

// Check distribution drift for a batch
CheckDrift(
  current_values: List<Float>,
  baseline_mean: Float,
  baseline_std: Float,
  method: String = "ks"  // "ks", "psi", "js"
) -> {drifted: Boolean, score: Float}

// Log feature value for drift monitoring
LogFeature(feature_name: String, value: Float) -> Float

// Log prediction for monitoring
LogPrediction(model: String, prediction: Float, features: List<Float>) -> Float
```

### Usage Examples

#### Real-time Anomaly Detection

```
in feature_value: Float
in baseline: {mean: Float, std: Float}
out is_anomaly: Boolean
out processed_value: Float

// Check if this feature value is anomalous
is_anomaly = IsAnomaly(feature_value, baseline.mean, baseline.std, 3.0)

// Log for monitoring (passes through value)
processed_value = LogFeature("transaction_amount", feature_value)
```

#### Prediction Logging

```
in features: List<Float>
out prediction: Float

// Get prediction
raw_prediction = ModelPredict("fraud-model", features)[0]

// Log for drift monitoring (passes through value)
prediction = LogPrediction("fraud-model", raw_prediction, features)
```

---

## Scala Module Level

### Drift Detection Framework

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/monitoring/DriftDetector.scala

package io.constellation.ml.monitoring

import cats.effect.IO

trait DriftDetector {
  /**
   * Check for drift in a feature.
   */
  def checkFeatureDrift(
    featureName: String,
    currentValues: Array[Double],
    baseline: DistributionBaseline
  ): IO[DriftResult]

  /**
   * Check for drift in predictions.
   */
  def checkPredictionDrift(
    modelName: String,
    currentPredictions: Array[Double],
    baseline: DistributionBaseline
  ): IO[DriftResult]

  /**
   * Check for multivariate drift.
   */
  def checkMultivariateDrift(
    currentData: Array[Array[Double]],
    baseline: MultivariateBaseline
  ): IO[DriftResult]
}

case class DistributionBaseline(
  mean: Double,
  std: Double,
  min: Double,
  max: Double,
  percentiles: Map[Int, Double],  // e.g., 25 -> p25, 50 -> p50, 75 -> p75
  histogram: Array[Double]  // Binned counts
)

case class MultivariateBaseline(
  featureBaselines: Map[String, DistributionBaseline],
  correlationMatrix: Array[Array[Double]]
)

case class DriftResult(
  drifted: Boolean,
  score: Double,
  method: String,
  threshold: Double,
  details: Map[String, Any] = Map.empty
)
```

### Statistical Tests Implementation

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/monitoring/StatisticalTests.scala

package io.constellation.ml.monitoring

object StatisticalTests {

  /**
   * Kolmogorov-Smirnov test for distribution comparison.
   */
  def kolmogorovSmirnovTest(
    sample1: Array[Double],
    sample2: Array[Double]
  ): (Double, Double) = {  // (statistic, p-value)
    val n1 = sample1.length
    val n2 = sample2.length

    val sorted1 = sample1.sorted
    val sorted2 = sample2.sorted

    // Compute empirical CDFs and find max difference
    var maxDiff = 0.0
    var i1 = 0
    var i2 = 0

    while (i1 < n1 || i2 < n2) {
      val v1 = if (i1 < n1) sorted1(i1) else Double.MaxValue
      val v2 = if (i2 < n2) sorted2(i2) else Double.MaxValue

      if (v1 <= v2) {
        i1 += 1
      }
      if (v2 <= v1) {
        i2 += 1
      }

      val cdf1 = i1.toDouble / n1
      val cdf2 = i2.toDouble / n2
      maxDiff = math.max(maxDiff, math.abs(cdf1 - cdf2))
    }

    // Approximate p-value
    val en = math.sqrt((n1.toDouble * n2) / (n1 + n2))
    val pValue = 2 * math.exp(-2 * en * en * maxDiff * maxDiff)

    (maxDiff, pValue)
  }

  /**
   * Population Stability Index (PSI) for drift detection.
   * PSI < 0.1: No significant change
   * 0.1 <= PSI < 0.2: Moderate change
   * PSI >= 0.2: Significant change
   */
  def populationStabilityIndex(
    expected: Array[Double],  // Baseline histogram proportions
    actual: Array[Double]      // Current histogram proportions
  ): Double = {
    require(expected.length == actual.length, "Histograms must have same number of bins")

    val epsilon = 1e-10  // Avoid log(0)

    expected.zip(actual).map { case (e, a) =>
      val eAdj = math.max(e, epsilon)
      val aAdj = math.max(a, epsilon)
      (aAdj - eAdj) * math.log(aAdj / eAdj)
    }.sum
  }

  /**
   * Jensen-Shannon divergence.
   * Bounded between 0 and 1 (using log base 2).
   */
  def jensenShannonDivergence(
    p: Array[Double],
    q: Array[Double]
  ): Double = {
    require(p.length == q.length, "Distributions must have same length")

    val m = p.zip(q).map { case (pi, qi) => (pi + qi) / 2 }

    def klDivergence(p: Array[Double], q: Array[Double]): Double = {
      p.zip(q).map { case (pi, qi) =>
        if (pi == 0) 0.0 else pi * math.log(pi / qi) / math.log(2)
      }.sum
    }

    (klDivergence(p, m) + klDivergence(q, m)) / 2
  }

  /**
   * Z-score based anomaly detection.
   */
  def zScoreAnomaly(
    value: Double,
    mean: Double,
    std: Double,
    threshold: Double = 3.0
  ): Boolean = {
    if (std == 0) value != mean
    else math.abs((value - mean) / std) > threshold
  }
}
```

### Drift Detector Implementation

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/monitoring/DefaultDriftDetector.scala

package io.constellation.ml.monitoring

import cats.effect.IO

class DefaultDriftDetector(
  ksThreshold: Double = 0.05,    // p-value threshold for KS test
  psiThreshold: Double = 0.2,    // PSI threshold
  jsThreshold: Double = 0.1      // JS divergence threshold
) extends DriftDetector {

  override def checkFeatureDrift(
    featureName: String,
    currentValues: Array[Double],
    baseline: DistributionBaseline
  ): IO[DriftResult] = IO {

    // Method 1: KS test against reconstructed baseline
    val baselineSample = reconstructSample(baseline, currentValues.length)
    val (ksStatistic, pValue) = StatisticalTests.kolmogorovSmirnovTest(currentValues, baselineSample)

    // Method 2: PSI using histograms
    val currentHistogram = computeHistogram(currentValues, baseline.histogram.length)
    val psi = StatisticalTests.populationStabilityIndex(baseline.histogram, currentHistogram)

    // Combine signals
    val drifted = pValue < ksThreshold || psi > psiThreshold

    DriftResult(
      drifted = drifted,
      score = psi,  // Use PSI as primary score
      method = "ks+psi",
      threshold = psiThreshold,
      details = Map(
        "ks_statistic" -> ksStatistic,
        "ks_pvalue" -> pValue,
        "psi" -> psi,
        "current_mean" -> currentValues.sum / currentValues.length,
        "baseline_mean" -> baseline.mean
      )
    )
  }

  override def checkPredictionDrift(
    modelName: String,
    currentPredictions: Array[Double],
    baseline: DistributionBaseline
  ): IO[DriftResult] = {
    // Same logic as feature drift
    checkFeatureDrift(s"$modelName:predictions", currentPredictions, baseline)
  }

  private def reconstructSample(baseline: DistributionBaseline, n: Int): Array[Double] = {
    // Reconstruct approximate sample from baseline statistics
    val random = new scala.util.Random(42)
    Array.fill(n)(baseline.mean + random.nextGaussian() * baseline.std)
  }

  private def computeHistogram(values: Array[Double], numBins: Int): Array[Double] = {
    val min = values.min
    val max = values.max
    val binWidth = (max - min) / numBins

    val counts = Array.fill(numBins)(0.0)
    values.foreach { v =>
      val bin = math.min(((v - min) / binWidth).toInt, numBins - 1)
      counts(bin) += 1
    }

    // Normalize to proportions
    val total = counts.sum
    counts.map(_ / total)
  }
}
```

### Real-time Drift Monitor

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/monitoring/RealtimeDriftMonitor.scala

package io.constellation.ml.monitoring

import cats.effect.{IO, Ref}
import scala.collection.mutable

class RealtimeDriftMonitor(
  driftDetector: DriftDetector,
  windowSize: Int = 1000,
  checkInterval: Int = 100,  // Check every N samples
  alerter: DriftAlerter
) {

  private val featureWindows: mutable.Map[String, SlidingWindow] = mutable.Map.empty
  private val baselines: mutable.Map[String, DistributionBaseline] = mutable.Map.empty

  def recordFeature(featureName: String, value: Double): IO[Unit] = IO {
    val window = featureWindows.getOrElseUpdate(featureName, new SlidingWindow(windowSize))
    window.add(value)

    // Check drift periodically
    if (window.count % checkInterval == 0) {
      checkAndAlert(featureName, window)
    }
  }

  def recordPrediction(modelName: String, prediction: Double): IO[Unit] = {
    recordFeature(s"$modelName:prediction", prediction)
  }

  def setBaseline(featureName: String, baseline: DistributionBaseline): IO[Unit] = IO {
    baselines.put(featureName, baseline)
  }

  private def checkAndAlert(featureName: String, window: SlidingWindow): Unit = {
    baselines.get(featureName).foreach { baseline =>
      val currentValues = window.getValues
      driftDetector.checkFeatureDrift(featureName, currentValues, baseline).flatMap { result =>
        if (result.drifted) {
          alerter.alert(DriftAlert(
            featureName = featureName,
            driftScore = result.score,
            threshold = result.threshold,
            timestamp = java.time.Instant.now(),
            details = result.details
          ))
        } else IO.unit
      }.unsafeRunSync()
    }
  }
}

class SlidingWindow(size: Int) {
  private val buffer = new Array[Double](size)
  private var index = 0
  var count: Long = 0

  def add(value: Double): Unit = {
    buffer(index) = value
    index = (index + 1) % size
    count += 1
  }

  def getValues: Array[Double] = {
    if (count < size) buffer.take(count.toInt)
    else buffer.clone()
  }
}

case class DriftAlert(
  featureName: String,
  driftScore: Double,
  threshold: Double,
  timestamp: java.time.Instant,
  details: Map[String, Any]
)

trait DriftAlerter {
  def alert(alert: DriftAlert): IO[Unit]
}
```

### Constellation Module Wrappers

```scala
// modules/lang-stdlib/src/main/scala/io/constellation/stdlib/monitoring/DriftOps.scala

package io.constellation.stdlib.monitoring

import io.constellation._
import io.constellation.ml.monitoring._

class DriftOps(driftMonitor: RealtimeDriftMonitor) {

  case class IsAnomalyInput(value: Double, baselineMean: Double, baselineStd: Double, threshold: Double)
  case class IsAnomalyOutput(isAnomaly: Boolean)

  val isAnomaly = ModuleBuilder
    .metadata("IsAnomaly", "Check if value is anomalous", 1, 0)
    .tags("monitoring", "anomaly", "drift")
    .implementationPure[IsAnomalyInput, IsAnomalyOutput] { input =>
      val anomaly = StatisticalTests.zScoreAnomaly(
        input.value, input.baselineMean, input.baselineStd, input.threshold
      )
      IsAnomalyOutput(anomaly)
    }
    .build

  case class LogFeatureInput(featureName: String, value: Double)
  case class LogFeatureOutput(value: Double)

  val logFeature = ModuleBuilder
    .metadata("LogFeature", "Log feature value for drift monitoring", 1, 0)
    .tags("monitoring", "logging", "drift")
    .implementationIO[LogFeatureInput, LogFeatureOutput] { input =>
      driftMonitor.recordFeature(input.featureName, input.value)
        .as(LogFeatureOutput(input.value))
    }
    .build

  case class LogPredictionInput(model: String, prediction: Double, features: List[Double])
  case class LogPredictionOutput(prediction: Double)

  val logPrediction = ModuleBuilder
    .metadata("LogPrediction", "Log prediction for drift monitoring", 1, 0)
    .tags("monitoring", "logging", "drift", "prediction")
    .implementationIO[LogPredictionInput, LogPredictionOutput] { input =>
      driftMonitor.recordPrediction(input.model, input.prediction)
        .as(LogPredictionOutput(input.prediction))
    }
    .build

  val allModules: List[Module.Uninitialized] = List(
    isAnomaly, logFeature, logPrediction
  )
}
```

---

## Configuration

```hocon
constellation.drift-detection {
  # Detection thresholds
  thresholds {
    ks-test-pvalue = 0.05
    psi = 0.2
    js-divergence = 0.1
    z-score = 3.0
  }

  # Monitoring windows
  windows {
    size = 1000
    check-interval = 100
  }

  # Alerting
  alerts {
    enabled = true
    destinations = ["slack", "pagerduty"]

    slack {
      webhook-url = ${SLACK_WEBHOOK_URL}
      channel = "#ml-alerts"
    }
  }

  # Baseline management
  baselines {
    storage = "redis"
    auto-update = false
    update-interval = 24h
  }
}
```

---

## Implementation Checklist

### Constellation-Lang Level

- [ ] Add `IsAnomaly` built-in function
- [ ] Add `CheckDrift` built-in function
- [ ] Add `LogFeature` built-in function
- [ ] Add `LogPrediction` built-in function
- [ ] Document with examples

### Scala Module Level

- [ ] Implement `DriftDetector` trait
- [ ] Implement statistical tests (KS, PSI, JS)
- [ ] Implement `DefaultDriftDetector`
- [ ] Implement `RealtimeDriftMonitor`
- [ ] Add alerting integration
- [ ] Create Constellation module wrappers
- [ ] Write tests with known drift scenarios

---

## Files to Create

| File | Purpose |
|------|---------|
| `modules/ml-integrations/.../monitoring/DriftDetector.scala` | Base trait |
| `modules/ml-integrations/.../monitoring/StatisticalTests.scala` | Test implementations |
| `modules/ml-integrations/.../monitoring/RealtimeDriftMonitor.scala` | Real-time monitoring |
| `modules/ml-integrations/.../monitoring/DriftAlerter.scala` | Alert handling |
| `modules/lang-stdlib/.../monitoring/DriftOps.scala` | Constellation modules |

---

## Related Documents

- [Data Validation](./06-data-validation.md) - Validate before drift check
- [A/B Testing](./07-ab-testing.md) - Monitor drift during rollout
- [Observability](./09-observability.md) - Track drift metrics
