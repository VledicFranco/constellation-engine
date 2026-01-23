# Task 3.1: Bidirectional Type Inference

**Phase:** 3 - Type System Enhancements
**Effort:** High (2 weeks)
**Impact:** High (Better ergonomics, fewer type annotations)
**Dependencies:** Task 2.4 (Subtyping) ✅
**Blocks:** Task 3.2 (Row Polymorphism)

---

## Objective

Implement bidirectional type inference that propagates expected types downward into expressions, enabling implicit lambda parameter types and better error messages.

---

## Background

### Current Behavior (Unidirectional)

Types flow only bottom-up from leaves to root:

```constellation
# Current: Lambda parameters MUST have explicit types
items = Filter(users, (u: User) => u.active)  # OK
items = Filter(users, u => u.active)           # ERROR: Cannot infer type of 'u'

# Current: Empty list needs context but doesn't get it
defaults: List<Int> = []  # ERROR: Cannot determine element type

# Current: Ambiguous function calls
result = Process([])  # ERROR: Multiple overloads, cannot determine which
```

**How it works now:**
```
Expression Tree (bottom-up only)
       ↑
    result
       ↑
   Process([])
       ↑
      []      → Type: List<Nothing>
              → Process expects List<T>, but T unknown
              → Error!
```

### Desired Behavior (Bidirectional)

Types flow both up (inference) and down (checking):

```constellation
# Lambda parameters inferred from context
items = Filter(users, u => u.active)  # OK: u inferred as User

# Empty list typed from context
defaults: List<Int> = []  # OK: [] checked against List<Int>

# Function overload resolved from context
result: String = Process([])  # OK: Process<String> selected
```

**How it should work:**
```
Expression Tree (bidirectional)
       ↓ Expected: String (from annotation)
    result
       ↓ Expected: String
   Process([])    → Select Process that returns String
       ↓ Expected: List<T> where Process<T> returns String
      []          → Check [] against List<T> → OK!
       ↑
    Type: List<T>
```

---

## Technical Design

### Bidirectional Algorithm

Two modes of operation:

1. **Inference mode (⇑)**: Synthesize type from expression structure (bottom-up)
2. **Checking mode (⇓)**: Verify expression against expected type (top-down)

```
Γ ⊢ e ⇒ A    (inference: e synthesizes type A)
Γ ⊢ e ⇐ A    (checking: e checks against type A)

Key rules:

                Γ ⊢ e ⇒ A    A <: B
[Sub]          ─────────────────────
                   Γ ⊢ e ⇐ B

[Var]          ────────────────────  (x:A) ∈ Γ
                   Γ ⊢ x ⇒ A

[Lam-Check]    Γ, x:A ⊢ e ⇐ B
               ─────────────────────
               Γ ⊢ (λx. e) ⇐ A → B

[Lam-Infer]    Γ, x:A ⊢ e ⇒ B        (A annotated)
               ─────────────────────
               Γ ⊢ (λx:A. e) ⇒ A → B

[App]          Γ ⊢ f ⇒ A → B    Γ ⊢ arg ⇐ A
               ─────────────────────────────
               Γ ⊢ f(arg) ⇒ B
```

### Type Checker Refactoring

```scala
// TypeChecker.scala - bidirectional version
sealed trait Mode
case object Infer extends Mode
case class Check(expected: SemanticType) extends Mode

class BidirectionalTypeChecker(registry: FunctionRegistry) {

  /** Main entry point */
  def checkProgram(program: Program): Either[TypeError, TypedProgram] = {
    var env = TypeEnvironment.empty

    val typedStatements = program.statements.map { stmt =>
      checkStatement(stmt, env) match {
        case Right((typed, newEnv)) =>
          env = newEnv
          Right(typed)
        case Left(err) => Left(err)
      }
    }

    typedStatements.sequence.map(TypedProgram(_))
  }

  /** Check a statement */
  def checkStatement(
    stmt: Statement,
    env: TypeEnvironment
  ): Either[TypeError, (TypedStatement, TypeEnvironment)] = {
    stmt match {
      case InputDecl(name, typeExpr, span) =>
        val semType = resolveType(typeExpr)
        val newEnv = env.withVariable(name, semType)
        Right((TypedInputDecl(name, semType, span), newEnv))

      case Assignment(name, expr, span) =>
        // Check if there's a type annotation
        env.getExpectedType(name) match {
          case Some(expected) =>
            // Checking mode: push expected type down
            checkExpr(expr, env, Check(expected)).map { typed =>
              val newEnv = env.withVariable(name, typed.semanticType)
              (TypedAssignment(name, typed, span), newEnv)
            }
          case None =>
            // Inference mode: synthesize type
            inferExpr(expr, env).map { typed =>
              val newEnv = env.withVariable(name, typed.semanticType)
              (TypedAssignment(name, typed, span), newEnv)
            }
        }

      case OutputDecl(name, span) =>
        env.getVariable(name) match {
          case Some(semType) =>
            Right((TypedOutputDecl(name, semType, span), env))
          case None =>
            Left(UndefinedVariable(name, span))
        }
    }
  }

  /** Infer type of expression (synthesis mode ⇑) */
  def inferExpr(expr: Expression, env: TypeEnvironment): Either[TypeError, TypedExpression] = {
    expr match {
      case Identifier(name, span) =>
        env.getVariable(name) match {
          case Some(semType) => Right(TypedIdentifier(name, semType, span))
          case None => Left(UndefinedVariable(name, span))
        }

      case IntLiteral(value, span) =>
        Right(TypedIntLiteral(value, span))

      case StringLiteral(value, span) =>
        Right(TypedStringLiteral(value, span))

      case FunctionCall(name, args, span) =>
        inferFunctionCall(name, args, span, env)

      case Lambda(params, body, span) =>
        // All params must have annotations in inference mode
        val paramTypes = params.map {
          case (name, Some(typeExpr)) => Right((name, resolveType(typeExpr)))
          case (name, None) => Left(CannotInferLambdaParam(name, span))
        }

        paramTypes.sequence.flatMap { typedParams =>
          val lambdaEnv = typedParams.foldLeft(env) { case (e, (n, t)) =>
            e.withVariable(n, t)
          }
          inferExpr(body, lambdaEnv).map { typedBody =>
            TypedLambda(typedParams, typedBody, span)
          }
        }

      case ListLiteral(elements, span) if elements.isEmpty =>
        // Cannot infer type of empty list
        Left(CannotInferEmptyList(span))

      case ListLiteral(elements, span) =>
        elements.traverse(inferExpr(_, env)).map { typedElements =>
          val elementType = Subtyping.commonType(typedElements.map(_.semanticType))
          TypedListLiteral(typedElements, elementType, span)
        }

      case FieldAccess(target, field, span) =>
        inferExpr(target, env).flatMap { typedTarget =>
          typedTarget.semanticType match {
            case SRecord(fields) =>
              fields.get(field) match {
                case Some(fieldType) =>
                  Right(TypedFieldAccess(typedTarget, field, fieldType, span))
                case None =>
                  Left(FieldNotFound(field, typedTarget.semanticType, span))
              }
            case other =>
              Left(NotARecord(other, span))
          }
        }

      // ... other cases
    }
  }

  /** Check expression against expected type (checking mode ⇓) */
  def checkExpr(
    expr: Expression,
    env: TypeEnvironment,
    mode: Mode
  ): Either[TypeError, TypedExpression] = {
    mode match {
      case Infer => inferExpr(expr, env)
      case Check(expected) => checkAgainst(expr, expected, env)
    }
  }

  /** Check expression against specific expected type */
  def checkAgainst(
    expr: Expression,
    expected: SemanticType,
    env: TypeEnvironment
  ): Either[TypeError, TypedExpression] = {
    (expr, expected) match {

      // Lambda without annotations: infer params from expected function type
      case (Lambda(params, body, span), SFunction(expectedParams, expectedReturn)) =>
        if (params.length != expectedParams.length) {
          Left(ArityMismatch("lambda", expectedParams.length, params.length, span))
        } else {
          // Pair lambda params with expected types
          val typedParams = params.zip(expectedParams).map {
            case ((name, Some(typeExpr)), (_, expectedType)) =>
              val annotatedType = resolveType(typeExpr)
              if (!Subtyping.isSubtype(annotatedType, expectedType)) {
                Left(TypeMismatch(expectedType, annotatedType, span))
              } else {
                Right((name, annotatedType))
              }
            case ((name, None), (_, expectedType)) =>
              // Infer parameter type from context!
              Right((name, expectedType))
          }

          typedParams.sequence.flatMap { params =>
            val lambdaEnv = params.foldLeft(env) { case (e, (n, t)) =>
              e.withVariable(n, t)
            }
            // Check body against expected return type
            checkAgainst(body, expectedReturn, lambdaEnv).map { typedBody =>
              TypedLambda(params, typedBody, span)
            }
          }
        }

      // Empty list: use expected element type
      case (ListLiteral(Nil, span), SList(expectedElem)) =>
        Right(TypedListLiteral(Nil, expectedElem, span))

      // Non-empty list: check each element against expected element type
      case (ListLiteral(elements, span), SList(expectedElem)) =>
        elements.traverse(checkAgainst(_, expectedElem, env)).map { typedElements =>
          TypedListLiteral(typedElements, expectedElem, span)
        }

      // Record literal: check each field
      case (RecordLiteral(fields, span), SRecord(expectedFields)) =>
        val typedFields = fields.map { case (name, expr) =>
          expectedFields.get(name) match {
            case Some(expectedType) =>
              checkAgainst(expr, expectedType, env).map(name -> _)
            case None =>
              // Extra field - allow (width subtyping)
              inferExpr(expr, env).map(name -> _)
          }
        }

        typedFields.sequence.map { tf =>
          TypedRecordLiteral(tf.toMap, span)
        }

      // Default: infer then check subtyping
      case _ =>
        inferExpr(expr, env).flatMap { typed =>
          if (Subtyping.isSubtype(typed.semanticType, expected)) {
            Right(typed)
          } else {
            Left(TypeMismatch(expected, typed.semanticType, expr.span))
          }
        }
    }
  }

  /** Infer function call - may use expected type for overload resolution */
  def inferFunctionCall(
    name: String,
    args: List[Expression],
    span: Span,
    env: TypeEnvironment
  ): Either[TypeError, TypedExpression] = {
    registry.lookup(name) match {
      case Some(sig) =>
        // Check argument count
        if (args.length != sig.params.length) {
          return Left(ArityMismatch(name, sig.params.length, args.length, span))
        }

        // Check each argument against expected param type (checking mode!)
        val typedArgs = args.zip(sig.params).map { case (arg, (_, expectedType)) =>
          checkAgainst(arg, expectedType, env)
        }

        typedArgs.sequence.map { tas =>
          TypedFunctionCall(name, tas, sig.returns, span)
        }

      case None =>
        Left(UnknownFunction(name, span))
    }
  }
}
```

### Enhanced Lambda Inference

```scala
// Special handling for higher-order functions
def checkHigherOrderCall(
  funcName: String,
  collectionArg: Expression,
  lambdaArg: Lambda,
  span: Span,
  env: TypeEnvironment
): Either[TypeError, TypedExpression] = {

  // First infer collection type
  inferExpr(collectionArg, env).flatMap { typedCollection =>
    val elementType = typedCollection.semanticType match {
      case SList(elem) => elem
      case SCandidates(elem) => elem
      case other => return Left(ExpectedCollection(other, span))
    }

    // Determine expected lambda type based on function
    val expectedLambdaType = funcName match {
      case "Filter" => SFunction(List(("x", elementType)), SBoolean)
      case "Map" =>
        // Map's lambda return type is inferred from body
        // We pass element type as param type
        SFunction(List(("x", elementType)), SAny)  // SAny as placeholder
      case "All" | "Any" => SFunction(List(("x", elementType)), SBoolean)
      case "SortBy" => SFunction(List(("x", elementType)), SAny)
    }

    // Check lambda against expected type
    checkAgainst(lambdaArg, expectedLambdaType, env).map { typedLambda =>
      val returnType = funcName match {
        case "Filter" => typedCollection.semanticType
        case "Map" =>
          val lambdaReturn = typedLambda.asInstanceOf[TypedLambda].body.semanticType
          SList(lambdaReturn)
        case "All" | "Any" => SBoolean
        case "SortBy" => typedCollection.semanticType
      }
      TypedFunctionCall(funcName, List(typedCollection, typedLambda), returnType, span)
    }
  }
}
```

### Better Error Messages

Bidirectional typing enables better errors:

```scala
// With expected type context, errors are more helpful
case class TypeMismatchWithContext(
  expected: SemanticType,
  actual: SemanticType,
  context: String,  // "in argument 2 of Filter"
  span: Span
) extends TypeError {
  def message = s"Type mismatch: expected $expected but got $actual $context"
}

case class CannotInferLambdaParam(
  paramName: String,
  hint: Option[String],  // "try adding type annotation: ($paramName: User)"
  span: Span
) extends TypeError

case class AmbiguousOverload(
  funcName: String,
  candidates: List[FunctionSignature],
  hint: String,  // "add type annotation to disambiguate"
  span: Span
) extends TypeError
```

---

## Deliverables

### Required

- [ ] **`BidirectionalTypeChecker.scala`** - New type checker implementation
- [ ] **`Mode.scala`** - Check/Infer mode types
- [ ] **Lambda inference** - Params inferred from context
- [ ] **Empty list handling** - Type from expected context
- [ ] **Higher-order function support** - Filter, Map, etc.
- [ ] **Enhanced error messages** - With context
- [ ] **Migration** - Replace old TypeChecker
- [ ] **Comprehensive tests** - All inference scenarios

### Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/BidirectionalTypeChecker.scala` | **New** | Main implementation |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/TypeChecker.scala` | Modify | Delegate to bidirectional |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/TypeError.scala` | Modify | Enhanced errors |
| `modules/lang-compiler/src/test/scala/io/constellation/lang/semantic/BidirectionalTypeCheckerTest.scala` | **New** | Tests |

---

## Implementation Guide

### Step 1: Add Mode Types

```scala
// Mode.scala
package io.constellation.lang.semantic

sealed trait TypeMode
case object Infer extends TypeMode
case class Check(expected: SemanticType) extends TypeMode
```

### Step 2: Create BidirectionalTypeChecker

Start with the core structure from Technical Design. Implement:
1. `inferExpr` for synthesis mode
2. `checkAgainst` for checking mode
3. Subsumption rule (infer then check subtyping)

### Step 3: Handle Lambdas

The key improvement - lambdas without annotations:

```scala
// When checking lambda against function type
case (Lambda(params, body, span), SFunction(expectedParams, expectedReturn)) =>
  // Params get their types from expected function type!
  val inferredParams = params.zip(expectedParams).map {
    case ((name, None), (_, expectedType)) => (name, expectedType)  // Inferred!
    case ((name, Some(ann)), (_, expectedType)) => (name, resolveType(ann))
  }
  // ...
```

### Step 4: Handle Higher-Order Functions

Special treatment for Filter, Map, etc:

```scala
def checkFunctionCall(name: String, args: List[Expression], expected: Option[SemanticType], env: TypeEnvironment) = {
  name match {
    case "Filter" | "Map" | "All" | "Any" | "SortBy" =>
      args match {
        case List(collection, lambda: Lambda) =>
          checkHigherOrderCall(name, collection, lambda, env)
        case _ =>
          // Fall back to standard checking
          inferFunctionCall(name, args, env)
      }
    case _ =>
      inferFunctionCall(name, args, env)
  }
}
```

### Step 5: Improve Error Messages

Add context to errors:

```scala
def checkArgument(arg: Expression, expected: SemanticType, argIndex: Int, funcName: String, env: TypeEnvironment) = {
  checkAgainst(arg, expected, env).left.map {
    case TypeMismatch(exp, act, span) =>
      TypeMismatchWithContext(exp, act, s"in argument ${argIndex + 1} of $funcName", span)
    case other => other
  }
}
```

### Step 6: Migrate Existing Code

Update `TypeChecker.scala` to use the new implementation:

```scala
// TypeChecker.scala
object TypeChecker {
  def check(program: Program, registry: FunctionRegistry): Either[TypeError, TypedProgram] = {
    new BidirectionalTypeChecker(registry).checkProgram(program)
  }
}
```

---

## Testing Strategy

```scala
class BidirectionalTypeCheckerTest extends AnyFlatSpec with Matchers {

  val checker = new BidirectionalTypeChecker(TestRegistry)

  "Bidirectional inference" should "infer lambda parameter types from context" in {
    val source = """
      |in users: List<{ name: String, active: Boolean }>
      |active = Filter(users, u => u.active)
      |out active
    """.stripMargin

    val result = compile(source)
    result.isRight shouldBe true

    // Verify lambda param was inferred
    val lambda = findLambda(result.toOption.get)
    lambda.params.head._2 shouldBe SRecord(Map(
      "name" -> SString,
      "active" -> SBoolean
    ))
  }

  it should "type empty lists from context" in {
    val source = """
      |defaults: List<Int> = []
      |out defaults
    """.stripMargin

    val result = compile(source)
    result.isRight shouldBe true

    val listType = findVariable(result.toOption.get, "defaults")
    listType shouldBe SList(SInt)
  }

  it should "still require annotations when context unavailable" in {
    val source = """
      |transform = (x => x)
      |out transform
    """.stripMargin

    val result = compile(source)
    result.isLeft shouldBe true
    result.left.get shouldBe a[CannotInferLambdaParam]
  }

  it should "check lambda body against expected return type" in {
    val source = """
      |in nums: List<Int>
      |strs = Map(nums, n => n)
      |out strs
    """.stripMargin

    // Map infers return type from lambda body
    val result = compile(source)
    result.isRight shouldBe true

    val strsType = findVariable(result.toOption.get, "strs")
    strsType shouldBe SList(SInt)
  }

  it should "produce better error messages with context" in {
    val source = """
      |in users: List<{ name: String }>
      |filtered = Filter(users, u => u.age)
      |out filtered
    """.stripMargin

    val result = compile(source)
    result.isLeft shouldBe true

    val error = result.left.get
    error.message should include("Filter")
    error.message should include("Boolean")
  }

  it should "handle nested lambdas" in {
    val source = """
      |in matrix: List<List<Int>>
      |flattened = FlatMap(matrix, row => Map(row, x => x * 2))
      |out flattened
    """.stripMargin

    val result = compile(source)
    result.isRight shouldBe true
  }

  it should "work with record literals" in {
    val source = """
      |user: { name: String, age: Int } = { name: "Alice", age: 30 }
      |out user
    """.stripMargin

    val result = compile(source)
    result.isRight shouldBe true
  }

  it should "allow extra fields in record literals (width subtyping)" in {
    val source = """
      |user: { name: String } = { name: "Alice", age: 30, active: true }
      |out user
    """.stripMargin

    val result = compile(source)
    result.isRight shouldBe true
  }
}

class ErrorMessageTest extends AnyFlatSpec with Matchers {

  "Error messages" should "include function context" in {
    val error = TypeMismatchWithContext(
      SBoolean, SInt,
      "in argument 2 of Filter",
      Span.empty
    )
    error.message should include("argument 2")
    error.message should include("Filter")
  }

  it should "suggest fixes for lambda param inference" in {
    val error = CannotInferLambdaParam(
      "x",
      Some("try: (x: Int) => ..."),
      Span.empty
    )
    error.message should include("x: Int")
  }
}
```

---

## Web Resources

### Bidirectional Type Theory
- [Bidirectional Typing](https://arxiv.org/abs/1908.05839) - Dunfield & Krishnaswami
- [Complete and Easy Bidirectional Typechecking](https://arxiv.org/abs/1306.6032) - Dunfield & Pfenning
- [Local Type Inference](https://www.cis.upenn.edu/~bcpierce/papers/lti-toplas.pdf) - Pierce & Turner

### Practical Implementations
- [TypeScript Type Inference](https://www.typescriptlang.org/docs/handbook/type-inference.html)
- [Scala Type Inference](https://docs.scala-lang.org/tour/type-inference.html)
- [Rust Type Inference](https://doc.rust-lang.org/book/ch03-02-data-types.html)

### Lambda Calculus
- [Simply Typed Lambda Calculus](https://en.wikipedia.org/wiki/Simply_typed_lambda_calculus)
- [TAPL](https://www.cis.upenn.edu/~bcpierce/tapl/) - Types and Programming Languages

---

## Acceptance Criteria

1. **Functional Requirements**
   - [ ] Lambda params inferred in Filter, Map, All, Any, SortBy
   - [ ] Empty list typed from expected context
   - [ ] Record literal fields checked against expected record
   - [ ] Explicit annotations still work and take precedence
   - [ ] Subsumption rule connects inference and checking

2. **Error Message Requirements**
   - [ ] Errors include function/argument context
   - [ ] Suggestions for adding type annotations
   - [ ] Clear explanation of expected vs actual types

3. **Quality Requirements**
   - [ ] No test regressions (existing code still compiles)
   - [ ] Performance within 10% of current type checker
   - [ ] All bidirectional rules have test coverage

---

## Notes for Implementer

1. **Subsumption is key** - The rule "if we can infer A and A <: B, then we can check against B" bridges the two modes.

2. **Preserve backward compatibility** - Code with explicit annotations must still work. Bidirectional just makes annotations optional where context exists.

3. **Order matters for rules** - Try specific checking rules before falling back to inference + subsumption.

4. **Higher-order functions are special** - They need custom handling to propagate element types to lambda params.

5. **Error recovery** - When inference fails, provide helpful hints about what annotation to add.

6. **Don't over-infer** - Some cases genuinely need annotations. That's OK. The goal is ergonomics, not eliminating all annotations.

7. **Test incrementally** - Start with lambda inference for Filter, verify it works, then add Map, etc.
