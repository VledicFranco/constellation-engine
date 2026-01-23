# Task 2.4: Subtyping Implementation

**Phase:** 2 - Core Improvements
**Effort:** Medium (1 week)
**Impact:** Medium (More flexible type system)
**Dependencies:** None
**Blocks:** Task 3.1 (Incremental Compilation), Task 3.4 (Bidirectional Inference), Task 4.1 (Row Polymorphism), Task 4.2 (Effect System)

---

## Objective

Implement a subtyping lattice for the type system, enabling `SNothing` as a true bottom type, width subtyping for records, and proper union type handling.

---

## Background

### Current Behavior

Types are compared for exact equality:

```scala
// TypeChecker.scala - current
if (actualType != expectedType) {
  TypeMismatch(expected, actual, span)
}
```

**Problems:**
- `SNothing` (from empty lists `[]`) can't be assigned to `List<String>`
- Records with extra fields aren't accepted where fewer fields expected
- Union members aren't handled flexibly

### Desired Behavior

```constellation
# Empty list should be assignable to any list type
items: List<String> = []  # Works: SNothing <: SString

# Record with extra fields should work
user: { name: String } = { name: "Alice", age: 30 }  # Works: width subtyping

# Union member should satisfy union type check
result: Int | String = 42  # Works: SInt <: SInt | SString
```

---

## Technical Design

### Subtyping Rules

```
S <: S                           (Reflexivity)
S <: T ∧ T <: U ⟹ S <: U        (Transitivity)
SNothing <: T                    (Bottom)
T <: SAny                        (Top - if added)

SList(S) <: SList(T) ⟸ S <: T   (Covariance)
SOptional(S) <: SOptional(T) ⟸ S <: T

SRecord(F₁) <: SRecord(F₂) ⟸ ∀f∈F₂. f∈F₁ ∧ F₁(f) <: F₂(f)  (Width + Depth)

S <: T₁ | T₂ ⟸ S <: T₁ ∨ S <: T₂   (Union upper bound)
T₁ | T₂ <: S ⟸ T₁ <: S ∧ T₂ <: S   (Union lower bound)
```

### Implementation

```scala
// Subtyping.scala
object Subtyping {

  def isSubtype(sub: SemanticType, sup: SemanticType): Boolean = {
    if (sub == sup) return true  // Reflexivity

    (sub, sup) match {
      // Bottom type
      case (SNothing, _) => true

      // Collections (covariant)
      case (SList(subElem), SList(supElem)) =>
        isSubtype(subElem, supElem)

      case (SCandidates(subElem), SCandidates(supElem)) =>
        isSubtype(subElem, supElem)

      case (SOptional(subInner), SOptional(supInner)) =>
        isSubtype(subInner, supInner)

      case (SMap(subK, subV), SMap(supK, supV)) =>
        subK == supK && isSubtype(subV, supV)  // Keys invariant, values covariant

      // Records (width + depth subtyping)
      case (SRecord(subFields), SRecord(supFields)) =>
        supFields.forall { case (name, supType) =>
          subFields.get(name).exists(subType => isSubtype(subType, supType))
        }

      // Union types
      case (_, SUnion(supMembers)) =>
        // sub is subtype if it's subtype of any member
        supMembers.exists(m => isSubtype(sub, m))

      case (SUnion(subMembers), _) =>
        // Union is subtype if ALL members are subtypes
        subMembers.forall(m => isSubtype(m, sup))

      // Functions (contravariant in params, covariant in return)
      case (SFunction(subParams, subRet), SFunction(supParams, supRet)) =>
        subParams.length == supParams.length &&
        subParams.zip(supParams).forall { case ((_, subT), (_, supT)) =>
          isSubtype(supT, subT)  // Contravariant!
        } &&
        isSubtype(subRet, supRet)

      case _ => false
    }
  }

  /** Find least upper bound of two types */
  def lub(a: SemanticType, b: SemanticType): SemanticType = {
    if (isSubtype(a, b)) b
    else if (isSubtype(b, a)) a
    else SUnion(Set(a, b))  // Create union
  }

  /** Find greatest lower bound of two types */
  def glb(a: SemanticType, b: SemanticType): SemanticType = {
    if (isSubtype(a, b)) a
    else if (isSubtype(b, a)) b
    else SNothing  // No common subtype
  }
}
```

---

## Deliverables

### Required

- [ ] **`Subtyping.scala`** - Core subtyping implementation
- [ ] **Integration with TypeChecker** - Replace equality checks
- [ ] **`SNothing` handling** - Proper bottom type behavior
- [ ] **Record subtyping** - Width + depth rules
- [ ] **Union subtyping** - Upper and lower bound rules
- [ ] **Comprehensive tests** - All subtyping rules covered

### Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/Subtyping.scala` | **New** | Core implementation |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/TypeChecker.scala` | Modify | Use subtyping |
| `modules/lang-compiler/src/test/scala/io/constellation/lang/semantic/SubtypingTest.scala` | **New** | Tests |

---

## Implementation Guide

### Step 1: Create Subtyping Object

```scala
// Subtyping.scala
package io.constellation.lang.semantic

object Subtyping {

  /** Check if `sub` is a subtype of `sup` */
  def isSubtype(sub: SemanticType, sup: SemanticType): Boolean = {
    // Implementation as shown above
  }

  /** Check type compatibility for assignment/passing */
  def isAssignable(value: SemanticType, target: SemanticType): Boolean = {
    isSubtype(value, target)
  }

  /** Compute common supertype for conditional branches */
  def commonType(types: List[SemanticType]): SemanticType = {
    types.reduceLeft(lub)
  }
}
```

### Step 2: Integrate with TypeChecker

```scala
// In TypeChecker.scala
// Replace:
if (actualType != expectedType) {
  TypeMismatch(expected, actual, span)
}

// With:
if (!Subtyping.isSubtype(actualType, expectedType)) {
  TypeMismatch(expected, actual, span)
}

// For conditionals:
// Replace:
if (thenType != elseType) {
  TypeMismatch(...)
}

// With:
val resultType = Subtyping.commonType(List(thenType, elseType))
```

### Step 3: Handle Empty Lists

```scala
// In type checking for list literals
case ListLit(elements) if elements.isEmpty =>
  // Empty list has type List<Nothing>
  SList(SNothing)

case ListLit(elements) =>
  val elementTypes = elements.map(checkExpr)
  val commonElem = Subtyping.commonType(elementTypes)
  SList(commonElem)
```

---

## Testing Strategy

```scala
class SubtypingTest extends AnyFlatSpec with Matchers {

  "Subtyping" should "handle reflexivity" in {
    Subtyping.isSubtype(SInt, SInt) shouldBe true
    Subtyping.isSubtype(SString, SString) shouldBe true
  }

  it should "handle SNothing as bottom type" in {
    Subtyping.isSubtype(SNothing, SInt) shouldBe true
    Subtyping.isSubtype(SNothing, SString) shouldBe true
    Subtyping.isSubtype(SNothing, SList(SInt)) shouldBe true
    Subtyping.isSubtype(SInt, SNothing) shouldBe false
  }

  it should "handle covariant lists" in {
    Subtyping.isSubtype(SList(SNothing), SList(SInt)) shouldBe true
    Subtyping.isSubtype(SList(SInt), SList(SNothing)) shouldBe false
  }

  it should "handle record width subtyping" in {
    val narrow = SRecord(Map("name" -> SString))
    val wide = SRecord(Map("name" -> SString, "age" -> SInt))

    Subtyping.isSubtype(wide, narrow) shouldBe true  // Has all required fields
    Subtyping.isSubtype(narrow, wide) shouldBe false // Missing 'age'
  }

  it should "handle record depth subtyping" in {
    val sub = SRecord(Map("value" -> SNothing))
    val sup = SRecord(Map("value" -> SInt))

    Subtyping.isSubtype(sub, sup) shouldBe true
  }

  it should "handle union upper bound" in {
    val union = SUnion(Set(SInt, SString))

    Subtyping.isSubtype(SInt, union) shouldBe true
    Subtyping.isSubtype(SString, union) shouldBe true
    Subtyping.isSubtype(SBoolean, union) shouldBe false
  }

  it should "handle union lower bound" in {
    val union = SUnion(Set(SInt, SString))

    // Union is subtype of T only if ALL members are subtypes
    Subtyping.isSubtype(union, SInt) shouldBe false
    Subtyping.isSubtype(SUnion(Set(SInt)), SInt) shouldBe true
  }

  it should "compute LUB correctly" in {
    Subtyping.lub(SInt, SInt) shouldBe SInt
    Subtyping.lub(SNothing, SInt) shouldBe SInt
    Subtyping.lub(SInt, SString) shouldBe SUnion(Set(SInt, SString))
  }
}
```

---

## Web Resources

### Subtyping Theory
- [Wikipedia: Subtyping](https://en.wikipedia.org/wiki/Subtyping)
- [TAPL Chapter 15](https://www.cis.upenn.edu/~bcpierce/tapl/) - Types and Programming Languages
- [Variance (CS)](https://en.wikipedia.org/wiki/Covariance_and_contravariance_(computer_science))

### Type System Design
- [TypeScript Subtyping](https://www.typescriptlang.org/docs/handbook/type-compatibility.html)
- [Scala Variance](https://docs.scala-lang.org/tour/variances.html)
- [Flow Type Compatibility](https://flow.org/en/docs/lang/subtypes/)

### Union Types
- [TypeScript Union Types](https://www.typescriptlang.org/docs/handbook/2/everyday-types.html#union-types)
- [Ceylon Union Types](https://ceylon-lang.org/documentation/1.3/tour/types/)

---

## Acceptance Criteria

1. **Functional Requirements**
   - [ ] `SNothing <: T` for all T
   - [ ] `List<Nothing> <: List<T>` (empty list assignable)
   - [ ] Record width subtyping works
   - [ ] Union subtyping works in both directions
   - [ ] Conditional branches find common type

2. **Quality Requirements**
   - [ ] No test regressions
   - [ ] All subtyping rules have test coverage
   - [ ] Edge cases handled (nested types, recursive types)

---

## Notes for Implementer

1. **Start with basic rules** - Reflexivity, bottom type, then add complexity.

2. **Be careful with variance** - Functions are contravariant in parameters!

3. **Test transitivity** - If A <: B and B <: C, ensure A <: C works.

4. **Handle cycles** - If recursive types are possible, prevent infinite loops.

5. **Update error messages** - Type mismatch errors should explain subtyping failure.
