# Data Validation & Schema Enforcement

**Priority:** 6 (Medium)
**Target Level:** Both (constellation-lang + Scala modules)
**Status:** Not Implemented

---

## Overview

Data validation is critical for production ML pipelines. Invalid or unexpected input data can cause silent prediction errors, model failures, or worse—wrong predictions served to users.

### Industry Context

> "Automated validation at each ingest step catches schema violations and drift before they corrupt training sets."
> — [MLOps Guide - Galileo](https://galileo.ai/blog/mlops-operationalizing-machine-learning)

> "Issues in the data processing pipelines feeding your production model can lead to errors or incorrect predictions. These issues can look like unexpected changes in the source data schema."
> — [ML Model Monitoring - Datadog](https://www.datadoghq.com/blog/ml-model-monitoring-in-production-best-practices/)

### Validation Types

| Type | Purpose | When |
|------|---------|------|
| **Schema validation** | Correct types, required fields | Input boundary |
| **Range validation** | Values within expected bounds | Pre-transform |
| **Statistical validation** | Distribution matches expected | Runtime |
| **Business rules** | Domain-specific constraints | Pre-inference |

---

## Constellation-Lang Level

### Built-in Functions

#### Basic Validation

```
// Validate value is not null
NotNull(value: Any) -> Any  // Raises error if null

// Validate value is in range
InRange(value: Float, min: Float, max: Float) -> Float

// Validate value is one of allowed values
OneOf(value: String, allowed: List<String>) -> String

// Validate list length
ValidateLength(list: List<Any>, min: Int, max: Int) -> List<Any>

// Validate with custom predicate
Validate(value: Any, predicate: (Any) -> Boolean, error_msg: String) -> Any
```

#### Schema Validation

```
// Validate against schema
ValidateSchema(data: Map<String, Any>, schema: Schema) -> Map<String, Any>

// Define schema inline
Schema({
  field_name: {type: "string", required: true},
  age: {type: "int", min: 0, max: 150},
  email: {type: "string", pattern: "^[\\w.-]+@[\\w.-]+\\.\\w+$"}
})
```

#### Coercion and Defaults

```
// Coerce to type with fallback
CoerceInt(value: Any, default: Int) -> Int
CoerceFloat(value: Any, default: Float) -> Float
CoerceString(value: Any, default: String) -> String

// Fill missing with default
Default(value: Any, default: Any) -> Any
```

### Usage Examples

#### Input Validation Pipeline

```
in raw_input: {user_id: String, amount: Float, category: String}
out validated: {user_id: String, amount: Float, category: String}

// Validate user_id is not empty
validated_user_id = Validate(
  raw_input.user_id,
  id -> Length(id) > 0,
  "user_id cannot be empty"
)

// Validate amount is positive and reasonable
validated_amount = InRange(raw_input.amount, 0.01, 1000000.0)

// Validate category is known
validated_category = OneOf(
  raw_input.category,
  ["electronics", "clothing", "food", "other"]
)

validated = {
  user_id: validated_user_id,
  amount: validated_amount,
  category: validated_category
}
```

#### Schema-Based Validation

```
in request: Map<String, Any>
out validated_request: {user_id: String, features: List<Float>}

// Define expected schema
request_schema = Schema({
  user_id: {type: "string", required: true, min_length: 1},
  features: {type: "list", element_type: "float", min_length: 10, max_length: 100}
})

// Validate and extract
validated = ValidateSchema(request, request_schema)
validated_request = {
  user_id: validated["user_id"],
  features: validated["features"]
}
```

#### Graceful Degradation

```
in input: {value: Any}
out result: Float

// Coerce with fallback, don't fail
safe_value = CoerceFloat(input.value, 0.0)

// Clip to valid range
clamped_value = Clip(safe_value, -100.0, 100.0)

result = clamped_value
```

---

## Scala Module Level

### Validation Framework

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/validation/Validator.scala

package io.constellation.ml.validation

import cats.data.ValidatedNel
import cats.syntax.validated._

sealed trait ValidationError {
  def message: String
  def field: Option[String]
}

case class NullValueError(field: Option[String]) extends ValidationError {
  def message = s"${field.getOrElse("Value")} cannot be null"
}

case class RangeError(field: Option[String], value: Double, min: Double, max: Double) extends ValidationError {
  def message = s"${field.getOrElse("Value")} $value is out of range [$min, $max]"
}

case class TypeMismatchError(field: Option[String], expected: String, actual: String) extends ValidationError {
  def message = s"${field.getOrElse("Value")} expected $expected but got $actual"
}

case class PatternMismatchError(field: Option[String], pattern: String) extends ValidationError {
  def message = s"${field.getOrElse("Value")} does not match pattern $pattern"
}

case class AllowedValuesError(field: Option[String], value: Any, allowed: Set[Any]) extends ValidationError {
  def message = s"${field.getOrElse("Value")} '$value' is not in allowed values: ${allowed.mkString(", ")}"
}

type ValidationResult[A] = ValidatedNel[ValidationError, A]

object Validator {

  def notNull[A](value: A, field: String = ""): ValidationResult[A] = {
    if (value == null) NullValueError(Some(field).filter(_.nonEmpty)).invalidNel
    else value.validNel
  }

  def inRange(value: Double, min: Double, max: Double, field: String = ""): ValidationResult[Double] = {
    if (value >= min && value <= max) value.validNel
    else RangeError(Some(field).filter(_.nonEmpty), value, min, max).invalidNel
  }

  def oneOf[A](value: A, allowed: Set[A], field: String = ""): ValidationResult[A] = {
    if (allowed.contains(value)) value.validNel
    else AllowedValuesError(Some(field).filter(_.nonEmpty), value, allowed).invalidNel
  }

  def matchesPattern(value: String, pattern: String, field: String = ""): ValidationResult[String] = {
    if (value.matches(pattern)) value.validNel
    else PatternMismatchError(Some(field).filter(_.nonEmpty), pattern).invalidNel
  }

  def listLength[A](list: List[A], min: Int, max: Int, field: String = ""): ValidationResult[List[A]] = {
    if (list.length >= min && list.length <= max) list.validNel
    else RangeError(Some(field).filter(_.nonEmpty), list.length, min, max).invalidNel
  }
}
```

### Schema Definition

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/validation/Schema.scala

package io.constellation.ml.validation

import cats.data.ValidatedNel
import cats.implicits._

sealed trait FieldSchema {
  def fieldName: String
  def required: Boolean
  def validate(value: Any): ValidationResult[Any]
}

case class StringFieldSchema(
  fieldName: String,
  required: Boolean = true,
  minLength: Option[Int] = None,
  maxLength: Option[Int] = None,
  pattern: Option[String] = None,
  allowedValues: Option[Set[String]] = None
) extends FieldSchema {

  def validate(value: Any): ValidationResult[Any] = {
    value match {
      case null if required => NullValueError(Some(fieldName)).invalidNel
      case null => value.validNel
      case s: String =>
        val checks = List(
          minLength.map(min => Validator.inRange(s.length, min, Int.MaxValue, s"$fieldName.length")),
          maxLength.map(max => Validator.inRange(s.length, 0, max, s"$fieldName.length")),
          pattern.map(p => Validator.matchesPattern(s, p, fieldName)),
          allowedValues.map(av => Validator.oneOf(s, av, fieldName))
        ).flatten

        checks.sequence.map(_ => s)

      case other =>
        TypeMismatchError(Some(fieldName), "String", other.getClass.getSimpleName).invalidNel
    }
  }
}

case class NumberFieldSchema(
  fieldName: String,
  required: Boolean = true,
  min: Option[Double] = None,
  max: Option[Double] = None,
  isInteger: Boolean = false
) extends FieldSchema {

  def validate(value: Any): ValidationResult[Any] = {
    value match {
      case null if required => NullValueError(Some(fieldName)).invalidNel
      case null => value.validNel
      case n: Number =>
        val d = n.doubleValue()
        val rangeCheck = (min, max) match {
          case (Some(lo), Some(hi)) => Validator.inRange(d, lo, hi, fieldName)
          case (Some(lo), None) => Validator.inRange(d, lo, Double.MaxValue, fieldName)
          case (None, Some(hi)) => Validator.inRange(d, Double.MinValue, hi, fieldName)
          case (None, None) => d.validNel
        }
        rangeCheck.map(_ => n)

      case other =>
        TypeMismatchError(Some(fieldName), "Number", other.getClass.getSimpleName).invalidNel
    }
  }
}

case class ListFieldSchema(
  fieldName: String,
  required: Boolean = true,
  minLength: Option[Int] = None,
  maxLength: Option[Int] = None,
  elementSchema: Option[FieldSchema] = None
) extends FieldSchema {

  def validate(value: Any): ValidationResult[Any] = {
    value match {
      case null if required => NullValueError(Some(fieldName)).invalidNel
      case null => value.validNel
      case list: List[_] =>
        val lengthCheck = (minLength, maxLength) match {
          case (Some(min), Some(max)) => Validator.listLength(list, min, max, fieldName)
          case (Some(min), None) => Validator.listLength(list, min, Int.MaxValue, fieldName)
          case (None, Some(max)) => Validator.listLength(list, 0, max, fieldName)
          case (None, None) => list.validNel
        }

        val elementChecks = elementSchema match {
          case Some(schema) =>
            list.zipWithIndex.traverse { case (elem, idx) =>
              schema.copy(fieldName = s"$fieldName[$idx]").validate(elem)
            }
          case None => list.validNel
        }

        (lengthCheck, elementChecks).mapN((_, elems) => elems)

      case other =>
        TypeMismatchError(Some(fieldName), "List", other.getClass.getSimpleName).invalidNel
    }
  }
}

case class RecordSchema(
  fields: Map[String, FieldSchema]
) {
  def validate(data: Map[String, Any]): ValidationResult[Map[String, Any]] = {
    fields.toList.traverse { case (name, schema) =>
      val value = data.get(name).orNull
      schema.validate(value).map(v => name -> v)
    }.map(_.toMap)
  }
}
```

### Constellation Module Wrappers

```scala
// modules/lang-stdlib/src/main/scala/io/constellation/stdlib/validation/ValidationOps.scala

package io.constellation.stdlib.validation

import io.constellation._
import io.constellation.ml.validation._

object ValidationOps {

  case class InRangeInput(value: Double, min: Double, max: Double)
  case class InRangeOutput(value: Double)

  val inRange = ModuleBuilder
    .metadata("InRange", "Validate value is within range", 1, 0)
    .tags("validation", "range")
    .implementationPure[InRangeInput, InRangeOutput] { input =>
      Validator.inRange(input.value, input.min, input.max) match {
        case cats.data.Validated.Valid(v) => InRangeOutput(v)
        case cats.data.Validated.Invalid(errors) =>
          throw new ValidationException(errors.toList.map(_.message).mkString("; "))
      }
    }
    .build

  case class OneOfInput(value: String, allowed: List[String])
  case class OneOfOutput(value: String)

  val oneOf = ModuleBuilder
    .metadata("OneOf", "Validate value is one of allowed values", 1, 0)
    .tags("validation", "categorical")
    .implementationPure[OneOfInput, OneOfOutput] { input =>
      Validator.oneOf(input.value, input.allowed.toSet) match {
        case cats.data.Validated.Valid(v) => OneOfOutput(v)
        case cats.data.Validated.Invalid(errors) =>
          throw new ValidationException(errors.toList.map(_.message).mkString("; "))
      }
    }
    .build

  case class NotNullInput(value: Any)
  case class NotNullOutput(value: Any)

  val notNull = ModuleBuilder
    .metadata("NotNull", "Validate value is not null", 1, 0)
    .tags("validation", "null")
    .implementationPure[NotNullInput, NotNullOutput] { input =>
      if (input.value == null) {
        throw new ValidationException("Value cannot be null")
      }
      NotNullOutput(input.value)
    }
    .build

  val allModules: List[Module.Uninitialized] = List(
    inRange, oneOf, notNull
  )
}

class ValidationException(message: String) extends RuntimeException(message)
```

---

## Validation Modes

### Fail-Fast vs Accumulating

```scala
// Fail-fast: stop at first error
def validateFailFast(data: Map[String, Any], schema: RecordSchema): Either[ValidationError, Map[String, Any]] = {
  schema.fields.foldLeft[Either[ValidationError, Map[String, Any]]](Right(Map.empty)) {
    case (Left(err), _) => Left(err)
    case (Right(acc), (name, fieldSchema)) =>
      fieldSchema.validate(data.get(name).orNull).toEither match {
        case Left(errs) => Left(errs.head)
        case Right(v) => Right(acc + (name -> v))
      }
  }
}

// Accumulating: collect all errors
def validateAccumulating(data: Map[String, Any], schema: RecordSchema): ValidationResult[Map[String, Any]] = {
  schema.validate(data)  // Already accumulating via ValidatedNel
}
```

### Strict vs Lenient

```scala
// Strict: unknown fields are errors
def validateStrict(data: Map[String, Any], schema: RecordSchema): ValidationResult[Map[String, Any]] = {
  val unknownFields = data.keys.toSet -- schema.fields.keys.toSet
  if (unknownFields.nonEmpty) {
    ValidationError.UnknownFields(unknownFields).invalidNel
  } else {
    schema.validate(data)
  }
}

// Lenient: ignore unknown fields
def validateLenient(data: Map[String, Any], schema: RecordSchema): ValidationResult[Map[String, Any]] = {
  schema.validate(data)  // Just validates known fields
}
```

---

## Configuration

```hocon
constellation.validation {
  # Default validation mode
  mode = "accumulating"  # or "fail-fast"

  # Handle unknown fields
  unknown-fields = "ignore"  # or "error", "warn"

  # Type coercion
  coercion {
    enabled = true
    string-to-number = true
    number-to-string = true
  }

  # Schema registry (optional)
  schema-registry {
    enabled = false
    url = "http://schema-registry:8081"
  }
}
```

---

## Implementation Checklist

### Constellation-Lang Level

- [ ] Add `NotNull` built-in function
- [ ] Add `InRange` built-in function
- [ ] Add `OneOf` built-in function
- [ ] Add `Validate` built-in function
- [ ] Add `ValidateSchema` built-in function
- [ ] Add coercion functions (CoerceInt, CoerceFloat, etc.)
- [ ] Document with examples

### Scala Module Level

- [ ] Implement `Validator` object with basic validators
- [ ] Implement `FieldSchema` sealed trait and variants
- [ ] Implement `RecordSchema` for composite validation
- [ ] Add validation modes (fail-fast, accumulating)
- [ ] Create Constellation module wrappers
- [ ] Write unit tests

---

## Files to Create

| File | Purpose |
|------|---------|
| `modules/ml-integrations/.../validation/Validator.scala` | Core validators |
| `modules/ml-integrations/.../validation/Schema.scala` | Schema definitions |
| `modules/ml-integrations/.../validation/ValidationError.scala` | Error types |
| `modules/lang-stdlib/.../validation/ValidationOps.scala` | Constellation modules |

---

## Related Documents

- [Feature Transformations](./01-feature-transformations.md) - Validate before transforming
- [Drift Detection](./08-drift-detection.md) - Monitor validation failures as drift signal
- [Observability](./09-observability.md) - Track validation metrics
