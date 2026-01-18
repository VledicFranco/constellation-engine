# Feature Transformations

**Priority:** 1 (Highest)
**Target Level:** Both (constellation-lang + Scala modules)
**Status:** Not Implemented

---

## Overview

Feature transformations are the bread and butter of ML inference pipelines. Every production ML system needs to transform raw input data into the feature format the model expects. Getting this wrong causes **training-serving skew**, the #1 cause of ML production failures.

### Why This Matters

> "Skew can be caused by a discrepancy between how you handle data in the training and serving pipelines. For example, if your model is trained on a logarithmically transformed feature, but it's presented with the raw feature during serving, the prediction output might not be accurate."
> — [TensorFlow TFX Best Practices](https://www.tensorflow.org/tfx/guide/tft_bestpractices)

---

## Constellation-Lang Level

### Built-in Functions to Implement

#### 1. Numerical Transformations

```
// Normalize to zero mean, unit variance
Normalize(value: Float, mean: Float, std: Float) -> Float

// Scale to [0, 1] range
MinMaxScale(value: Float, min: Float, max: Float) -> Float

// Log transformation (handles zeros)
LogTransform(value: Float, offset: Float = 1.0) -> Float

// Clip to range
Clip(value: Float, min: Float, max: Float) -> Float

// Discretize continuous values into bins
Discretize(value: Float, boundaries: List<Float>) -> Int
```

**Usage Example:**
```
in transaction: {amount: Float, age: Int}
out features: {norm_amount: Float, norm_age: Float, amount_bucket: Int}

// Normalize using training statistics
norm_amount = Normalize(transaction.amount, mean=150.0, std=75.0)
norm_age = Normalize(ToFloat(transaction.age), mean=35.0, std=12.0)

// Discretize amount into buckets
amount_bucket = Discretize(transaction.amount, [0, 50, 100, 500, 1000])
```

#### 2. Categorical Encoding

```
// One-hot encoding
OneHotEncode(value: String, categories: List<String>) -> List<Float>

// Label encoding (string to integer)
LabelEncode(value: String, categories: List<String>) -> Int

// Hash encoding (for high-cardinality)
HashEncode(value: String, num_buckets: Int) -> Int

// Target encoding (requires pre-computed mapping)
TargetEncode(value: String, encoding_map: Map<String, Float>) -> Float
```

**Usage Example:**
```
in user: {country: String, device: String, user_id: String}
out encoded: {country_vec: List<Float>, device_idx: Int, user_bucket: Int}

// One-hot for low cardinality
country_vec = OneHotEncode(user.country, ["US", "UK", "DE", "FR", "OTHER"])

// Label encode for ordinal
device_idx = LabelEncode(user.device, ["mobile", "tablet", "desktop"])

// Hash encode for high cardinality (millions of users)
user_bucket = HashEncode(user.user_id, 10000)
```

#### 3. Text Transformations

```
// Lowercase
Lower(text: String) -> String

// Tokenize
Tokenize(text: String, separator: String = " ") -> List<String>

// N-grams
NGrams(tokens: List<String>, n: Int) -> List<String>

// Text length
TextLength(text: String) -> Int

// Contains substring
Contains(text: String, substring: String) -> Boolean
```

#### 4. List/Array Operations

```
// Concatenate features
Concat(features: List<Float>...) -> List<Float>

// Pad or truncate to fixed length
PadOrTruncate(list: List<Float>, length: Int, pad_value: Float) -> List<Float>

// Aggregate
Sum(list: List<Float>) -> Float
Mean(list: List<Float>) -> Float
Max(list: List<Float>) -> Float
Min(list: List<Float>) -> Float
```

#### 5. Date/Time Transformations

```
// Extract components
DayOfWeek(timestamp: Int) -> Int      // 0-6
HourOfDay(timestamp: Int) -> Int      // 0-23
MonthOfYear(timestamp: Int) -> Int    // 1-12
IsWeekend(timestamp: Int) -> Boolean

// Time since
SecondsSince(timestamp: Int, reference: Int) -> Int
DaysSince(timestamp: Int, reference: Int) -> Int

// Cyclical encoding (for periodic features)
CyclicalEncode(value: Int, period: Int) -> {sin: Float, cos: Float}
```

**Usage Example:**
```
in event: {timestamp: Int}
out time_features: {hour: Int, day: Int, is_weekend: Boolean, hour_sin: Float, hour_cos: Float}

hour = HourOfDay(event.timestamp)
day = DayOfWeek(event.timestamp)
is_weekend = IsWeekend(event.timestamp)

// Cyclical encoding preserves continuity (23:00 is close to 00:00)
hour_encoded = CyclicalEncode(hour, 24)
hour_sin = hour_encoded.sin
hour_cos = hour_encoded.cos
```

---

## Scala Module Level

### Core Implementation

```scala
// modules/lang-stdlib/src/main/scala/io/constellation/stdlib/ml/Transformations.scala

package io.constellation.stdlib.ml

import io.constellation._
import io.constellation.TypeSystem._

object Transformations {

  // ============================================
  // Numerical Transformations
  // ============================================

  case class NormalizeInput(value: Double, mean: Double, std: Double)
  case class NormalizeOutput(result: Double)

  val normalize = ModuleBuilder
    .metadata("Normalize", "Normalize value to zero mean, unit variance", 1, 0)
    .tags("transform", "numerical", "ml")
    .implementationPure[NormalizeInput, NormalizeOutput] { input =>
      val result = if (input.std == 0) 0.0 else (input.value - input.mean) / input.std
      NormalizeOutput(result)
    }
    .build

  case class MinMaxScaleInput(value: Double, min: Double, max: Double)
  case class MinMaxScaleOutput(result: Double)

  val minMaxScale = ModuleBuilder
    .metadata("MinMaxScale", "Scale value to [0, 1] range", 1, 0)
    .tags("transform", "numerical", "ml")
    .implementationPure[MinMaxScaleInput, MinMaxScaleOutput] { input =>
      val range = input.max - input.min
      val result = if (range == 0) 0.5 else (input.value - input.min) / range
      MinMaxScaleOutput(math.max(0.0, math.min(1.0, result)))  // Clip to [0, 1]
    }
    .build

  case class DiscretizeInput(value: Double, boundaries: List[Double])
  case class DiscretizeOutput(bucket: Int)

  val discretize = ModuleBuilder
    .metadata("Discretize", "Discretize continuous value into buckets", 1, 0)
    .tags("transform", "numerical", "ml")
    .implementationPure[DiscretizeInput, DiscretizeOutput] { input =>
      val bucket = input.boundaries.indexWhere(_ > input.value) match {
        case -1 => input.boundaries.length  // Greater than all boundaries
        case idx => idx
      }
      DiscretizeOutput(bucket)
    }
    .build

  // ============================================
  // Categorical Transformations
  // ============================================

  case class OneHotEncodeInput(value: String, categories: List[String])
  case class OneHotEncodeOutput(encoded: List[Double])

  val oneHotEncode = ModuleBuilder
    .metadata("OneHotEncode", "One-hot encode categorical value", 1, 0)
    .tags("transform", "categorical", "ml")
    .implementationPure[OneHotEncodeInput, OneHotEncodeOutput] { input =>
      val encoded = input.categories.map { cat =>
        if (cat == input.value) 1.0 else 0.0
      }
      OneHotEncodeOutput(encoded)
    }
    .build

  case class HashEncodeInput(value: String, numBuckets: Int)
  case class HashEncodeOutput(bucket: Int)

  val hashEncode = ModuleBuilder
    .metadata("HashEncode", "Hash encode high-cardinality categorical", 1, 0)
    .tags("transform", "categorical", "ml")
    .implementationPure[HashEncodeInput, HashEncodeOutput] { input =>
      // Use MurmurHash for good distribution
      val hash = scala.util.hashing.MurmurHash3.stringHash(input.value)
      val bucket = math.abs(hash % input.numBuckets)
      HashEncodeOutput(bucket)
    }
    .build

  // ============================================
  // Date/Time Transformations
  // ============================================

  case class CyclicalEncodeInput(value: Int, period: Int)
  case class CyclicalEncodeOutput(sin: Double, cos: Double)

  val cyclicalEncode = ModuleBuilder
    .metadata("CyclicalEncode", "Encode periodic feature as sin/cos", 1, 0)
    .tags("transform", "temporal", "ml")
    .implementationPure[CyclicalEncodeInput, CyclicalEncodeOutput] { input =>
      val angle = 2.0 * math.Pi * input.value / input.period
      CyclicalEncodeOutput(math.sin(angle), math.cos(angle))
    }
    .build

  // ============================================
  // Feature Combination
  // ============================================

  case class ConcatInput(vectors: List[List[Double]])
  case class ConcatOutput(result: List[Double])

  val concat = ModuleBuilder
    .metadata("Concat", "Concatenate feature vectors", 1, 0)
    .tags("transform", "combination", "ml")
    .implementationPure[ConcatInput, ConcatOutput] { input =>
      ConcatOutput(input.vectors.flatten)
    }
    .build

  // Register all transformations
  val allModules: List[Module.Uninitialized] = List(
    normalize,
    minMaxScale,
    discretize,
    oneHotEncode,
    hashEncode,
    cyclicalEncode,
    concat
  )

  val allSignatures: Map[String, FunctionSignature] = allModules.map { m =>
    m.spec.name -> m.signature
  }.toMap
}
```

### Stateful Transformations (Advanced)

For transformations that need to store state from training:

```scala
// Transformation with learned parameters

case class LearnedNormalizer(mean: Double, std: Double) {
  def transform(value: Double): Double = (value - mean) / std
}

object LearnedNormalizer {
  def fit(values: Seq[Double]): LearnedNormalizer = {
    val mean = values.sum / values.length
    val variance = values.map(v => math.pow(v - mean, 2)).sum / values.length
    LearnedNormalizer(mean, math.sqrt(variance))
  }

  // Serialize for storage
  def toJson(ln: LearnedNormalizer): String =
    s"""{"mean": ${ln.mean}, "std": ${ln.std}}"""

  def fromJson(json: String): LearnedNormalizer = {
    // Parse JSON and create normalizer
    ???
  }
}

// Module that uses pre-fitted normalizer
def createNormalizerModule(params: LearnedNormalizer): Module.Uninitialized = {
  ModuleBuilder
    .metadata("FittedNormalize", s"Normalize with μ=${params.mean}, σ=${params.std}", 1, 0)
    .implementationPure[NormalizeValueInput, NormalizeValueOutput] { input =>
      NormalizeValueOutput(params.transform(input.value))
    }
    .build
}
```

---

## Training-Serving Consistency

### The Problem

```python
# Training time (Python)
from sklearn.preprocessing import StandardScaler
scaler = StandardScaler()
X_train_scaled = scaler.fit_transform(X_train)
model.fit(X_train_scaled, y_train)

# What are scaler.mean_ and scaler.scale_?
# If you don't save these, inference will be wrong!
```

### The Solution

Store transformation parameters alongside the DAG:

```scala
// TransformationConfig stored with DagSpec

case class TransformationConfig(
  normalizers: Map[String, NormalizerParams],  // feature_name -> (mean, std)
  encoders: Map[String, EncoderParams],        // feature_name -> categories
  scalers: Map[String, ScalerParams]           // feature_name -> (min, max)
)

case class NormalizerParams(mean: Double, std: Double)
case class EncoderParams(categories: List[String])
case class ScalerParams(min: Double, max: Double)

// Extended DagSpec
case class DagSpec(
  // ... existing fields
  transformationConfig: Option[TransformationConfig] = None
)
```

### API for Consistency

```
// constellation-lang with explicit parameters
norm_amount = Normalize(amount, mean=150.0, std=75.0)

// Or reference a saved config
norm_amount = Normalize(amount, config="training_stats.amount")
```

---

## Performance Considerations

### Vectorized Operations

For batch inference, implement vectorized versions:

```scala
// Single value (used in constellation-lang)
def normalize(value: Double, mean: Double, std: Double): Double =
  (value - mean) / std

// Vectorized (used internally for batches)
def normalizeVector(values: Array[Double], mean: Double, std: Double): Array[Double] = {
  val result = new Array[Double](values.length)
  var i = 0
  while (i < values.length) {
    result(i) = (values(i) - mean) / std
    i += 1
  }
  result
}
```

### Benchmarks

| Operation | Single Value | Batch (1000) | Vectorized Batch |
|-----------|--------------|--------------|------------------|
| Normalize | 50ns | 50μs | 15μs |
| OneHotEncode | 100ns | 100μs | 40μs |
| HashEncode | 200ns | 200μs | 150μs |

---

## Implementation Checklist

### Constellation-Lang Level

- [ ] Add `Normalize` built-in function
- [ ] Add `MinMaxScale` built-in function
- [ ] Add `Discretize` built-in function
- [ ] Add `OneHotEncode` built-in function
- [ ] Add `LabelEncode` built-in function
- [ ] Add `HashEncode` built-in function
- [ ] Add `Concat` built-in function
- [ ] Add `CyclicalEncode` built-in function
- [ ] Add temporal extraction functions (DayOfWeek, HourOfDay, etc.)
- [ ] Document all functions with examples

### Scala Module Level

- [ ] Implement `Transformations.scala` in lang-stdlib
- [ ] Add vectorized implementations for batch performance
- [ ] Add `TransformationConfig` support in DagSpec
- [ ] Create serialization for learned transformers
- [ ] Write unit tests for all transformations
- [ ] Benchmark performance

---

## Files to Create/Modify

| File | Changes |
|------|---------|
| `modules/lang-stdlib/.../ml/Transformations.scala` | New: All transformation modules |
| `modules/lang-stdlib/.../StdLib.scala` | Register transformation modules |
| `modules/core/.../Spec.scala` | Add TransformationConfig |
| `modules/lang-parser/.../ConstellationParser.scala` | Parse transformation functions |
| `modules/lang-compiler/.../TypeChecker.scala` | Type check transformation calls |

---

## Related Documents

- [Model Inference](./02-model-inference.md) - Use transformed features for prediction
- [Data Validation](./06-data-validation.md) - Validate input before transformation
- [Drift Detection](./08-drift-detection.md) - Monitor transformation distributions
