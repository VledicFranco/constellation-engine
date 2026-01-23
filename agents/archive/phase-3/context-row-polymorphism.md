# Task 3.2: Row Polymorphism

**Phase:** 3 - Type System Enhancements
**Effort:** Very High (3 weeks)
**Impact:** High (Flexible data transformation pipelines)
**Dependencies:** Task 2.4 (Subtyping) ✅, Task 3.1 (Bidirectional Inference)

---

## Objective

Implement row polymorphism to allow functions to accept records with "at least" certain fields, enabling flexible data transformation pipelines where extra fields pass through without explicit projection.

---

## Background

### Current Behavior (No Row Polymorphism)

Functions require exact record types:

```constellation
# Define a function that needs just the 'name' field
# Current: Must define exact expected type
in user: { name: String, age: Int, email: String, active: Boolean }

# GetName expects { name: String } - exact match required
# But user has extra fields!
name = GetName(user)  # ERROR: { name, age, email, active } ≠ { name }

# Workaround: Must project to exact fields
name = GetName(user{name})  # OK but verbose
```

**Current type checker flow:**
```
user: { name: String, age: Int, email: String, active: Boolean }
GetName expects: { name: String }

Check: { name, age, email, active } =:= { name }
Result: NO - extra fields age, email, active not allowed
```

### Desired Behavior (Row Polymorphism)

Functions can specify "at least" requirements:

```constellation
# GetName only cares about 'name' field
# Works with ANY record that has 'name'
in user: { name: String, age: Int, email: String, active: Boolean }

name = GetName(user)  # OK! Extra fields ignored
```

**With row polymorphism:**
```
GetName signature: { name: String | ρ } -> String
                   ^ "a record with 'name' plus any other fields ρ"

user: { name: String, age: Int, email: String, active: Boolean }

Check: { name, age, email, active } <: { name | ρ }
Unify: ρ = { age: Int, email: String, active: Boolean }
Result: OK! ρ absorbs extra fields
```

---

## Technical Design

### Row Variables

Introduce row variables (`ρ`, represented as `RowVar`) to represent "the rest of the fields":

```scala
// SemanticType.scala additions
sealed trait SemanticType

// Existing record type (closed - exact fields)
case class SRecord(fields: Map[String, SemanticType]) extends SemanticType

// NEW: Open record type (at least these fields, plus row variable)
case class SOpenRecord(
  fields: Map[String, SemanticType],
  rowVar: RowVar
) extends SemanticType

// NEW: Row variable representing unknown fields
case class RowVar(id: Int) extends SemanticType {
  def prettyPrint: String = s"ρ$id"
}

// NEW: Row - either empty, concrete fields, or variable
sealed trait Row
case object EmptyRow extends Row
case class ConcreteRow(fields: Map[String, SemanticType]) extends Row
case class VarRow(rowVar: RowVar) extends Row
case class ExtendRow(fields: Map[String, SemanticType], rest: Row) extends Row
```

### Row Polymorphic Function Signatures

Functions can now express "at least" requirements:

```scala
// FunctionSignature.scala
case class FunctionSignature(
  name: String,
  params: List[(String, SemanticType)],
  returns: SemanticType,
  rowVars: List[RowVar] = Nil  // NEW: row variables this signature introduces
)

// Example: GetName has row polymorphic signature
// GetName: forall ρ. { name: String | ρ } -> String
val getNameSig = FunctionSignature(
  name = "GetName",
  params = List(("record", SOpenRecord(Map("name" -> SString), RowVar(0)))),
  returns = SString,
  rowVars = List(RowVar(0))
)
```

### Row Unification

Unification now handles row variables:

```scala
// Unification.scala
object RowUnification {

  case class Substitution(
    typeSubst: Map[TypeVar, SemanticType],
    rowSubst: Map[RowVar, Row]
  )

  /** Unify two rows, producing substitutions for row variables. */
  def unifyRows(row1: Row, row2: Row, subst: Substitution): Either[UnificationError, Substitution] = {
    (row1, row2) match {
      // Empty rows unify
      case (EmptyRow, EmptyRow) => Right(subst)

      // Row variable unifies with anything
      case (VarRow(v), r) =>
        // Occurs check
        if (occursIn(v, r)) Left(OccursCheckFailed(v, r))
        else Right(subst.withRowVar(v, r))

      case (r, VarRow(v)) =>
        if (occursIn(v, r)) Left(OccursCheckFailed(v, r))
        else Right(subst.withRowVar(v, r))

      // Concrete rows: unify field-by-field
      case (ConcreteRow(f1), ConcreteRow(f2)) =>
        if (f1.keySet != f2.keySet) Left(RowMismatch(f1.keySet, f2.keySet))
        else unifyFields(f1, f2, subst)

      // Extended row: extract common fields, unify rest
      case (ExtendRow(f1, rest1), ExtendRow(f2, rest2)) =>
        val common = f1.keySet.intersect(f2.keySet)
        val only1 = f1.filterKeys(k => !common.contains(k))
        val only2 = f2.filterKeys(k => !common.contains(k))

        for {
          s1 <- unifyFields(f1.view.filterKeys(common.contains).toMap,
                           f2.view.filterKeys(common.contains).toMap, subst)
          s2 <- unifyRows(ExtendRow(only1.toMap, rest1),
                         ExtendRow(only2.toMap, rest2), s1)
        } yield s2

      case _ => Left(IncompatibleRows(row1, row2))
    }
  }

  /** Unify a closed record with an open record. */
  def unifyRecords(
    closed: SRecord,
    open: SOpenRecord,
    subst: Substitution
  ): Either[UnificationError, Substitution] = {
    // Check that closed has all required fields
    val missing = open.fields.keySet.diff(closed.fields.keySet)
    if (missing.nonEmpty) {
      Left(MissingFields(missing))
    } else {
      // Unify matching fields
      val matchingFields = open.fields.map { case (name, expectedType) =>
        (name, closed.fields(name), expectedType)
      }

      for {
        s1 <- matchingFields.foldLeft(Right(subst): Either[UnificationError, Substitution]) {
          case (Right(s), (name, actualType, expectedType)) =>
            unifyTypes(actualType, expectedType, s)
          case (left, _) => left
        }
        // Row variable absorbs extra fields
        extraFields = closed.fields.filterKeys(k => !open.fields.contains(k)).toMap
        s2 = s1.withRowVar(open.rowVar, ConcreteRow(extraFields))
      } yield s2
    }
  }
}
```

### Type Checker Updates

```scala
// TypeChecker.scala updates
class RowPolymorphicTypeChecker(registry: FunctionRegistry) {

  private var nextRowVar = 0

  def freshRowVar(): RowVar = {
    nextRowVar += 1
    RowVar(nextRowVar)
  }

  /** Check function call with row polymorphism */
  def checkFunctionCall(
    name: String,
    args: List[TypedExpression],
    span: Span,
    subst: Substitution
  ): Either[TypeError, (TypedExpression, Substitution)] = {

    registry.lookup(name) match {
      case Some(sig) if sig.rowVars.nonEmpty =>
        // Instantiate fresh row variables for this call
        val freshVars = sig.rowVars.map(_ => freshRowVar())
        val varMapping = sig.rowVars.zip(freshVars).toMap
        val instantiatedParams = sig.params.map { case (pname, ptype) =>
          (pname, substituteRowVars(ptype, varMapping))
        }
        val instantiatedReturn = substituteRowVars(sig.returns, varMapping)

        // Check arguments against instantiated param types
        checkArgs(args, instantiatedParams, span, subst).map { case (typedArgs, s) =>
          (TypedFunctionCall(name, typedArgs, applySubst(instantiatedReturn, s), span), s)
        }

      case Some(sig) =>
        // Non-row-polymorphic function - use standard checking
        checkArgsExact(args, sig.params, span, subst)

      case None =>
        Left(UnknownFunction(name, span))
    }
  }

  /** Check argument against expected type, allowing row polymorphism */
  def checkArg(
    arg: TypedExpression,
    expected: SemanticType,
    argIndex: Int,
    funcName: String,
    subst: Substitution
  ): Either[TypeError, Substitution] = {
    (arg.semanticType, expected) match {
      // Closed record passed to open record parameter
      case (SRecord(actualFields), SOpenRecord(expectedFields, rowVar)) =>
        RowUnification.unifyRecords(SRecord(actualFields), SOpenRecord(expectedFields, rowVar), subst)
          .left.map(err => RowUnificationError(err, argIndex, funcName, arg.span))

      // Open record passed to open record (both have row vars)
      case (SOpenRecord(af, av), SOpenRecord(ef, ev)) =>
        // More complex unification needed
        unifyOpenRecords(SOpenRecord(af, av), SOpenRecord(ef, ev), subst)
          .left.map(err => RowUnificationError(err, argIndex, funcName, arg.span))

      // Standard subtyping check
      case (actual, exp) =>
        if (Subtyping.isSubtype(actual, exp)) Right(subst)
        else Left(TypeMismatch(exp, actual, arg.span))
    }
  }

  /** Substitute row variables in a type */
  def substituteRowVars(typ: SemanticType, mapping: Map[RowVar, RowVar]): SemanticType = {
    typ match {
      case SOpenRecord(fields, rowVar) =>
        val newRowVar = mapping.getOrElse(rowVar, rowVar)
        SOpenRecord(fields.view.mapValues(substituteRowVars(_, mapping)).toMap, newRowVar)
      case SRecord(fields) =>
        SRecord(fields.view.mapValues(substituteRowVars(_, mapping)).toMap)
      case SList(elem) =>
        SList(substituteRowVars(elem, mapping))
      case SFunction(params, ret) =>
        SFunction(params.map(substituteRowVars(_, mapping)), substituteRowVars(ret, mapping))
      case other => other
    }
  }
}
```

### Row Polymorphism for Data Transformation

The key use case - records flowing through pipelines:

```scala
// StdLib additions for row-polymorphic functions

// Map preserves extra fields in records
// Map: forall ρ α β. List<{ ...α | ρ }> × (α -> β) -> List<{ ...β | ρ }>
// When mapping over records, extra fields pass through!

// SelectField extracts one field, ignores rest
// SelectField: forall ρ. { name: T | ρ } -> T

// AddField adds a field to records, preserving existing
// AddField: forall ρ. { | ρ } × String × T -> { newField: T | ρ }
```

---

## Deliverables

### Required

- [ ] **`RowVar` and `SOpenRecord`** - New types in SemanticType
- [ ] **`RowUnification.scala`** - Row unification algorithm
- [ ] **Updated `FunctionSignature`** - Support for row-polymorphic signatures
- [ ] **Updated `TypeChecker`** - Instantiate and check row variables
- [ ] **Updated `Subtyping`** - Handle open record subtyping
- [ ] **StdLib updates** - Make relevant functions row-polymorphic
- [ ] **Comprehensive tests** - All row polymorphism scenarios
- [ ] **Documentation** - Updated type system docs

### Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/SemanticType.scala` | Modify | Add RowVar, SOpenRecord, Row types |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/RowUnification.scala` | **New** | Row unification algorithm |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/FunctionSignature.scala` | Modify | Add rowVars field |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/TypeChecker.scala` | Modify | Handle row polymorphism |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/Subtyping.scala` | Modify | Open record subtyping |
| `modules/lang-stdlib/src/main/scala/io/constellation/stdlib/StdLib.scala` | Modify | Row-polymorphic signatures |
| `modules/lang-compiler/src/test/scala/io/constellation/lang/semantic/RowPolymorphismTest.scala` | **New** | Tests |

---

## Implementation Guide

### Step 1: Add Row Types to SemanticType

```scala
// SemanticType.scala
sealed trait SemanticType {
  def prettyPrint: String
}

object SemanticType {
  // ... existing types ...

  /** Row variable - represents unknown additional fields */
  case class RowVar(id: Int) extends SemanticType {
    def prettyPrint: String = s"ρ$id"
  }

  /** Open record - has specific fields plus row variable for "rest" */
  case class SOpenRecord(
    fields: Map[String, SemanticType],
    rowVar: RowVar
  ) extends SemanticType {
    def prettyPrint: String = {
      val fieldStr = fields.map { case (k, v) => s"$k: ${v.prettyPrint}" }.mkString(", ")
      s"{ $fieldStr | ${rowVar.prettyPrint} }"
    }
  }
}
```

### Step 2: Create RowUnification Module

Start with basic unification:

```scala
// RowUnification.scala
package io.constellation.lang.semantic

object RowUnification {

  sealed trait UnificationError
  case class MissingFields(fields: Set[String]) extends UnificationError
  case class FieldTypeMismatch(field: String, expected: SemanticType, actual: SemanticType) extends UnificationError
  case class OccursCheckFailed(rowVar: RowVar, row: Row) extends UnificationError

  case class Substitution(rowSubst: Map[RowVar, Map[String, SemanticType]] = Map.empty) {
    def apply(rowVar: RowVar): Option[Map[String, SemanticType]] = rowSubst.get(rowVar)

    def withRowVar(rv: RowVar, fields: Map[String, SemanticType]): Substitution =
      copy(rowSubst = rowSubst + (rv -> fields))

    def isEmpty: Boolean = rowSubst.isEmpty
  }

  object Substitution {
    val empty: Substitution = Substitution()
  }

  /** Check if a closed record satisfies an open record type.
    * Returns substitution mapping row variable to extra fields.
    */
  def unifyClosedWithOpen(
    closed: SRecord,
    open: SOpenRecord
  ): Either[UnificationError, Substitution] = {
    // Check required fields exist and are compatible
    val missingFields = open.fields.keySet.diff(closed.fields.keySet)
    if (missingFields.nonEmpty) {
      return Left(MissingFields(missingFields))
    }

    // Check field types
    for ((fieldName, expectedType) <- open.fields) {
      val actualType = closed.fields(fieldName)
      if (!Subtyping.isSubtype(actualType, expectedType)) {
        return Left(FieldTypeMismatch(fieldName, expectedType, actualType))
      }
    }

    // Row variable captures extra fields
    val extraFields = closed.fields.view.filterKeys(k => !open.fields.contains(k)).toMap
    Right(Substitution.empty.withRowVar(open.rowVar, extraFields))
  }
}
```

### Step 3: Update FunctionSignature

```scala
// FunctionSignature.scala
case class FunctionSignature(
  name: String,
  params: List[(String, SemanticType)],
  returns: SemanticType,
  description: String = "",
  rowVars: List[RowVar] = Nil  // Row variables this signature quantifies over
) {

  /** Is this signature row-polymorphic? */
  def isRowPolymorphic: Boolean = rowVars.nonEmpty

  /** Create a fresh instantiation of this signature with new row variables */
  def instantiate(freshVarGen: () => RowVar): FunctionSignature = {
    if (!isRowPolymorphic) this
    else {
      val mapping = rowVars.map(rv => rv -> freshVarGen()).toMap
      copy(
        params = params.map { case (n, t) => (n, substituteRowVars(t, mapping)) },
        returns = substituteRowVars(returns, mapping),
        rowVars = mapping.values.toList
      )
    }
  }

  private def substituteRowVars(t: SemanticType, mapping: Map[RowVar, RowVar]): SemanticType = {
    t match {
      case SOpenRecord(fields, rv) =>
        SOpenRecord(fields.view.mapValues(substituteRowVars(_, mapping)).toMap, mapping.getOrElse(rv, rv))
      case SRecord(fields) =>
        SRecord(fields.view.mapValues(substituteRowVars(_, mapping)).toMap)
      case SList(elem) =>
        SList(substituteRowVars(elem, mapping))
      case SOptional(inner) =>
        SOptional(substituteRowVars(inner, mapping))
      case SFunction(ps, ret) =>
        SFunction(ps.map(substituteRowVars(_, mapping)), substituteRowVars(ret, mapping))
      case other => other
    }
  }
}
```

### Step 4: Update TypeChecker

```scala
// In TypeChecker.scala
class TypeChecker(registry: FunctionRegistry) {

  private var rowVarCounter = 0

  private def freshRowVar(): RowVar = {
    rowVarCounter += 1
    RowVar(rowVarCounter)
  }

  def checkFunctionCall(
    name: String,
    args: List[Expression],
    span: Span,
    env: TypeEnvironment
  ): Either[TypeError, TypedExpression] = {
    registry.lookup(name) match {
      case Some(sig) =>
        // Instantiate signature with fresh row variables
        val instantiated = sig.instantiate(() => freshRowVar())

        // Check argument count
        if (args.length != instantiated.params.length) {
          return Left(ArityMismatch(name, instantiated.params.length, args.length, span))
        }

        // Check each argument, collecting row substitutions
        var subst = Substitution.empty
        val typedArgs = args.zip(instantiated.params).zipWithIndex.map {
          case ((arg, (paramName, paramType)), idx) =>
            checkArgument(arg, paramType, idx, name, env, subst) match {
              case Right((typed, newSubst)) =>
                subst = newSubst
                Right(typed)
              case Left(err) => Left(err)
            }
        }

        typedArgs.sequence.map { tas =>
          // Apply substitution to return type
          val resultType = applySubstitution(instantiated.returns, subst)
          TypedFunctionCall(name, tas, resultType, span)
        }

      case None => Left(UnknownFunction(name, span))
    }
  }

  def checkArgument(
    arg: Expression,
    expected: SemanticType,
    argIndex: Int,
    funcName: String,
    env: TypeEnvironment,
    subst: Substitution
  ): Either[TypeError, (TypedExpression, Substitution)] = {
    inferExpr(arg, env).flatMap { typedArg =>
      (typedArg.semanticType, expected) match {
        // Closed record passed to open record parameter
        case (actual: SRecord, expected: SOpenRecord) =>
          RowUnification.unifyClosedWithOpen(actual, expected) match {
            case Right(newSubst) =>
              Right((typedArg, subst.merge(newSubst)))
            case Left(err) =>
              Left(toTypeError(err, argIndex, funcName, arg.span))
          }

        // Standard subtyping
        case (actual, exp) =>
          if (Subtyping.isSubtype(actual, exp)) Right((typedArg, subst))
          else Left(TypeMismatch(exp, actual, arg.span))
      }
    }
  }

  /** Apply row substitution to a type */
  def applySubstitution(typ: SemanticType, subst: Substitution): SemanticType = {
    typ match {
      case SOpenRecord(fields, rowVar) =>
        subst(rowVar) match {
          case Some(extraFields) =>
            // Close the record with concrete fields
            SRecord(fields ++ extraFields)
          case None =>
            // Row variable not yet bound
            typ
        }
      case SList(elem) => SList(applySubstitution(elem, subst))
      case SRecord(fields) => SRecord(fields.view.mapValues(applySubstitution(_, subst)).toMap)
      case other => other
    }
  }
}
```

### Step 5: Update Subtyping

```scala
// Subtyping.scala additions
def isSubtype(sub: SemanticType, sup: SemanticType): Boolean = {
  if (sub == sup) return true

  (sub, sup) match {
    // ... existing cases ...

    // Closed record is subtype of open record if it has all required fields
    case (SRecord(subFields), SOpenRecord(supFields, _)) =>
      supFields.forall { case (name, supType) =>
        subFields.get(name).exists(subType => isSubtype(subType, supType))
      }

    // Open record is subtype of open record (more complex)
    case (SOpenRecord(subFields, subRv), SOpenRecord(supFields, supRv)) =>
      // All required fields in sup must be in sub
      supFields.forall { case (name, supType) =>
        subFields.get(name).exists(subType => isSubtype(subType, supType))
      }
      // Note: row variables require unification, not just subtyping

    case _ => false
  }
}
```

### Step 6: Update StdLib

Make relevant functions row-polymorphic:

```scala
// StdLib.scala
object StdLib {

  private var rowVarId = 0
  private def freshRowVar(): RowVar = {
    rowVarId += 1
    RowVar(rowVarId)
  }

  // GetName: forall ρ. { name: String | ρ } -> String
  val getName: FunctionSignature = {
    val rv = freshRowVar()
    FunctionSignature(
      name = "GetName",
      params = List(("record", SOpenRecord(Map("name" -> SString), rv))),
      returns = SString,
      description = "Extract name field from any record with a name",
      rowVars = List(rv)
    )
  }

  // GetAge: forall ρ. { age: Int | ρ } -> Int
  val getAge: FunctionSignature = {
    val rv = freshRowVar()
    FunctionSignature(
      name = "GetAge",
      params = List(("record", SOpenRecord(Map("age" -> SInt), rv))),
      returns = SInt,
      description = "Extract age field from any record with an age",
      rowVars = List(rv)
    )
  }

  // SelectField: forall ρ T. String × { field: T | ρ } -> T
  // (This is more complex - needs type-level field access)
}
```

---

## Testing Strategy

```scala
class RowPolymorphismTest extends AnyFlatSpec with Matchers {

  "Row polymorphism" should "allow extra fields to pass through" in {
    val source = """
      |in user: { name: String, age: Int, email: String }
      |name = GetName(user)
      |out name
    """.stripMargin

    val result = compile(source)
    result.isRight shouldBe true
  }

  it should "reject records missing required fields" in {
    val source = """
      |in partial: { age: Int }
      |name = GetName(partial)
      |out name
    """.stripMargin

    val result = compile(source)
    result.isLeft shouldBe true
    result.left.get.message should include("missing")
    result.left.get.message should include("name")
  }

  it should "work with nested records" in {
    val source = """
      |in person: { name: String, address: { city: String, zip: String } }
      |name = GetName(person)
      |out name
    """.stripMargin

    val result = compile(source)
    result.isRight shouldBe true
  }

  it should "work in higher-order functions" in {
    val source = """
      |in users: List<{ name: String, age: Int, active: Boolean }>
      |names = Map(users, u => GetName(u))
      |out names
    """.stripMargin

    val result = compile(source)
    result.isRight shouldBe true

    val namesType = findVariable(result.toOption.get, "names")
    namesType shouldBe SList(SString)
  }

  it should "work with Filter on records with extra fields" in {
    val source = """
      |in users: List<{ name: String, age: Int, active: Boolean, score: Int }>
      |active = Filter(users, u => u.active)
      |out active
    """.stripMargin

    val result = compile(source)
    result.isRight shouldBe true

    // Result should preserve all fields
    val activeType = findVariable(result.toOption.get, "active")
    activeType match {
      case SList(SRecord(fields)) =>
        fields.keySet should contain allOf ("name", "age", "active", "score")
      case _ => fail("Expected List<Record>")
    }
  }

  it should "chain row-polymorphic functions" in {
    val source = """
      |in data: { name: String, age: Int, score: Float }
      |name = GetName(data)
      |age = GetAge(data)
      |out name
      |out age
    """.stripMargin

    val result = compile(source)
    result.isRight shouldBe true
  }

  it should "work with record literals" in {
    val source = """
      |record = { name: "Alice", age: 30, role: "Engineer" }
      |name = GetName(record)
      |out name
    """.stripMargin

    val result = compile(source)
    result.isRight shouldBe true
  }

  it should "correctly infer result types through pipelines" in {
    val source = """
      |in users: List<{ name: String, age: Int, dept: String }>
      |adults = Filter(users, u => u.age >= 18)
      |names = Map(adults, u => GetName(u))
      |out names
    """.stripMargin

    val result = compile(source)
    result.isRight shouldBe true

    // adults should preserve all fields
    val adultsType = findVariable(result.toOption.get, "adults")
    adultsType match {
      case SList(SRecord(fields)) =>
        fields.keySet should contain allOf ("name", "age", "dept")
      case _ => fail("Expected List<Record>")
    }

    // names should be List<String>
    val namesType = findVariable(result.toOption.get, "names")
    namesType shouldBe SList(SString)
  }
}

class RowSubtypingTest extends AnyFlatSpec with Matchers {

  "Row subtyping" should "allow closed record as subtype of open record" in {
    val closed = SRecord(Map("name" -> SString, "age" -> SInt))
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))

    Subtyping.isSubtype(closed, open) shouldBe true
  }

  it should "reject closed record missing required field" in {
    val closed = SRecord(Map("age" -> SInt))
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))

    Subtyping.isSubtype(closed, open) shouldBe false
  }

  it should "check field type compatibility" in {
    val closed = SRecord(Map("name" -> SInt))  // Wrong type!
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))

    Subtyping.isSubtype(closed, open) shouldBe false
  }
}

class RowUnificationTest extends AnyFlatSpec with Matchers {

  "Row unification" should "capture extra fields in row variable" in {
    val closed = SRecord(Map("name" -> SString, "age" -> SInt, "active" -> SBoolean))
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))

    val result = RowUnification.unifyClosedWithOpen(closed, open)
    result.isRight shouldBe true

    val subst = result.toOption.get
    subst(RowVar(1)) shouldBe Some(Map("age" -> SInt, "active" -> SBoolean))
  }

  it should "produce empty row for exact match" in {
    val closed = SRecord(Map("name" -> SString))
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))

    val result = RowUnification.unifyClosedWithOpen(closed, open)
    result.isRight shouldBe true

    val subst = result.toOption.get
    subst(RowVar(1)) shouldBe Some(Map.empty)
  }
}
```

---

## Web Resources

### Row Polymorphism Theory
- [Row Types Paper](http://homepages.inf.ed.ac.uk/wadler/papers/row-poly/row-poly.pdf) - Wand & Rémy
- [Extensible Records with Scoped Labels](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/scopedlabels.pdf)
- [Row Polymorphism Tutorial](https://www.cl.cam.ac.uk/~jdy22/papers/a-polymorphic-record-calculus-and-its-compilation.pdf)

### Practical Implementations
- [OCaml Object Types](https://ocaml.org/docs/objects) - Row polymorphism in practice
- [TypeScript Structural Typing](https://www.typescriptlang.org/docs/handbook/type-compatibility.html)
- [PureScript Row Types](https://pursuit.purescript.org/packages/purescript-record/3.0.0)
- [Elm Extensible Records](https://elm-lang.org/docs/records)

### Type System Foundations
- [TAPL](https://www.cis.upenn.edu/~bcpierce/tapl/) - Types and Programming Languages
- [Practical Type Inference](https://www.cambridge.org/core/journals/journal-of-functional-programming/article/practical-type-inference-for-arbitraryrank-types/E8C0D0E2A2A858A8B9B507C8ED2B5CA6)

---

## Acceptance Criteria

1. **Functional Requirements**
   - [ ] Records with extra fields pass to functions requiring fewer fields
   - [ ] Missing required fields produce clear error messages
   - [ ] Row polymorphism works with higher-order functions (Map, Filter, etc.)
   - [ ] Chaining row-polymorphic functions works correctly
   - [ ] Result types correctly computed through data pipelines

2. **Integration Requirements**
   - [ ] Works with bidirectional type inference (Task 3.1)
   - [ ] Works with existing subtyping (Task 2.4)
   - [ ] Existing closed record types still work unchanged

3. **Quality Requirements**
   - [ ] No test regressions
   - [ ] Performance within 15% of current type checker
   - [ ] Comprehensive test coverage for row unification

---

## Notes for Implementer

1. **Start simple** - Begin with closed-to-open record checking. This covers the main use case (passing records with extra fields to functions).

2. **Defer complex cases** - Open-to-open record unification is more complex. Implement after basic case works.

3. **Existing subtyping helps** - The current `Subtyping.scala` already handles width subtyping for closed records. Row polymorphism extends this to function signatures.

4. **Test incrementally** - Get GetName working first, then GetAge, then Map/Filter, then chained operations.

5. **Error messages matter** - When a field is missing, tell the user which field and what type was expected.

6. **Preserve existing behavior** - Functions without row variables should work exactly as before. Row polymorphism is opt-in per signature.

7. **Consider interaction with 3.1** - Bidirectional inference will want to push expected types down. When the expected type is an open record, the implementation should instantiate fresh row variables.

8. **Don't over-engineer** - Constellation scripts are short. Complex row-polymorphism features (row concatenation, row restriction) can be added later if needed.
