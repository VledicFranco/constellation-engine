# Ensemble & Aggregation

**Priority:** 12 (Lower)
**Target Level:** Both
**Status:** Not Implemented

---

## Overview

Ensemble methods combine predictions from multiple models for improved accuracy and robustness. Constellation should support common aggregation strategies.

---

## Constellation-Lang Level

### Built-in Functions

```
// Average predictions
EnsembleAverage(predictions: List<Float>) -> Float

// Weighted average
EnsembleWeighted(predictions: List<Float>, weights: List<Float>) -> Float

// Voting (classification)
EnsembleVote(predictions: List<Int>) -> Int

// Max prediction
EnsembleMax(predictions: List<Float>) -> Float
```

### Usage Example

```
in features: List<Float>
out prediction: Float

// Call multiple models
pred1 = ModelPredict("model-v1", features)[0]
pred2 = ModelPredict("model-v2", features)[0]
pred3 = ModelPredict("model-v3", features)[0]

// Ensemble average
prediction = EnsembleAverage([pred1, pred2, pred3])
```

---

## Scala Module Level

```scala
object EnsembleMethods {

  def average(predictions: List[Double]): Double = {
    predictions.sum / predictions.length
  }

  def weightedAverage(predictions: List[Double], weights: List[Double]): Double = {
    predictions.zip(weights).map { case (p, w) => p * w }.sum / weights.sum
  }

  def vote(predictions: List[Int]): Int = {
    predictions.groupBy(identity).maxBy(_._2.length)._1
  }

  def softVote(probabilities: List[List[Double]]): Int = {
    val avgProbs = probabilities.transpose.map(_.sum / probabilities.length)
    avgProbs.zipWithIndex.maxBy(_._1)._2
  }

  def stacking(
    basePredictions: List[Double],
    metaModel: List[Double] => Double
  ): Double = {
    metaModel(basePredictions)
  }
}
```

---

## Implementation Checklist

- [ ] Add `EnsembleAverage` built-in
- [ ] Add `EnsembleWeighted` built-in
- [ ] Add `EnsembleVote` built-in
- [ ] Implement Scala ensemble methods
- [ ] Document with examples

---

## Related Documents

- [Model Inference](./02-model-inference.md) - Call multiple models
- [A/B Testing](./07-ab-testing.md) - Compare ensemble vs single model
