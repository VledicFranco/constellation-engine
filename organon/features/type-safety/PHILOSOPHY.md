# Type Safety Philosophy

> Why compile-time type validation is a language feature, not a runtime concern.

---

## The Problem

Pipeline systems often defer type checking to runtime:

```python
# Without compile-time types: runtime failures
def process_user(data):
    name = data["name"]          # KeyError if missing
    age = data["age"]            # KeyError if missing
    result = name.upper()        # AttributeError if not string
    return {"user": name, "years": age}

# Fails at runtime with cryptic stack trace
process_user({"name": "Alice"})  # KeyError: 'age'
```

This pattern produces:

1. **Production failures.** Missing fields crash the pipeline at runtime.
2. **Cryptic errors.** `KeyError: 'age'` doesn't tell you which module expected it.
3. **Defensive code.** Every field access needs try/except or `.get()`.
4. **No IDE support.** Autocomplete can't suggest fields because types are unknown.

---

## The Bet

Type errors are **logic errors**, not data errors. They should be caught before execution, not during.

By validating types at compile time:

```constellation
in user: { name: String, age: Int }
result = user.address.city  # Compile error: 'address' not in { name: String, age: Int }
```

We achieve:

1. **Early failure.** Type errors are reported immediately, with source location.
2. **Clear errors.** "Field 'address' not found in type { name: String, age: Int }".
3. **No defensive code.** Valid field access is guaranteed to succeed.
4. **IDE support.** Autocomplete knows exactly what fields are available.

---

## Design Decisions

### 1. Structural, Not Nominal

Types are compared by structure, not by name:

```constellation
type User = { id: String, name: String }
type Employee = { id: String, name: String }

# These are the SAME type - both are { id: String, name: String }
in person: User
result = FormatEmployee(person)  # Valid: structural match
```

**Why:** Pipeline data often comes from external systems. Requiring exact type names would force boilerplate conversions. Structural typing lets data flow naturally between modules that expect the same shape.

### 2. Width Subtyping for Records

A record with more fields is a subtype of a record with fewer fields:

```constellation
type Full = { id: String, name: String, email: String, role: String }
type Basic = { id: String, name: String }

in user: Full
result = ProcessBasic(user)  # Valid: Full has all fields Basic needs
```

**Why:** Modules often only need a subset of available fields. Width subtyping lets you pass richer records without projection, reducing boilerplate while maintaining type safety.

### 3. Field Access Validated at Compile Time

Every `.field` access is checked against the known type:

```constellation
in user: { name: String, age: Int }

valid = user.name      # OK: 'name' exists
invalid = user.email   # Compile error: 'email' not found
```

**Why:** Runtime field access errors are the most common type-related failures in dynamic systems. Catching them at compile time eliminates an entire class of production bugs.

### 4. Type Algebra for Composition

Records support algebraic operations:

```constellation
# Merge: combine fields from two records
combined = user + profile        # { id, name } + { bio, avatar } = { id, name, bio, avatar }

# Projection: select subset of fields
public = user[id, name, email]   # { id, name, email } from larger record

# Element-wise: operate on list elements
names = users.name               # List<{ name: String }>.name = List<String>
```

**Why:** Data pipelines constantly reshape data. Algebraic operations express these transformations concisely and type-safely, rather than requiring module calls for every field selection.

### 5. Optional Types for Absence

Missing values are explicit in the type system:

```constellation
in maybeUser: Optional<User>
name = maybeUser.name     # Optional<String> - absence propagates
safe = maybeUser ?? defaultUser  # User - coalesce provides fallback
```

**Why:** Null pointer errors are the "billion dollar mistake." Optional types force explicit handling of absence, preventing null-related crashes at runtime.

---

## Trade-Offs

| Decision | Benefit | Cost |
|----------|---------|------|
| Structural typing | No boilerplate conversions | Can't distinguish same-shape different-meaning types |
| Width subtyping | Pass richer data freely | Extra fields are silently ignored |
| Compile-time validation | No field access errors at runtime | Can't handle truly dynamic data |
| Type algebra | Concise data reshaping | Learning curve for operators |
| Optional types | Explicit null handling | More verbose for "happy path" |

### What We Gave Up

- **Dynamic field access.** Can't do `record[fieldName]` where `fieldName` is a variable. The compiler must know field names statically.
- **Nominal types.** Can't distinguish `UserId` from `OrderId` if both are `String`. Use wrapper records if distinction matters.
- **Gradual typing.** No `Any` or escape hatch. All types must be fully known at compile time.
- **Type coercion.** No implicit Int-to-Float or String-to-Int. Use explicit conversion modules.

These limitations are intentional. The type system is strict because strict types catch more bugs.

---

## Structural vs. Nominal: The Full Trade-Off

### Structural Typing (Constellation's Choice)

```constellation
type UserId = String
type OrderId = String

# These are equivalent - both are just String
in userId: UserId
result = ProcessOrder(userId)  # Compiles! Both are String.
```

**Advantage:** No boilerplate. Data flows between modules without conversion.

**Disadvantage:** Can accidentally pass wrong-but-same-type values.

### Nominal Typing (Alternative)

```typescript
// TypeScript with branded types
type UserId = string & { __brand: 'UserId' }
type OrderId = string & { __brand: 'OrderId' }

// These are NOT equivalent - brands differ
const userId: UserId = 'abc' as UserId
processOrder(userId)  // Type error! UserId is not OrderId.
```

**Advantage:** Distinguishes same-shape different-meaning types.

**Disadvantage:** Requires explicit conversions everywhere.

### Constellation's Position

We chose structural typing because:

1. **Pipeline data is external.** Data comes from APIs, databases, files. It doesn't have nominal types attached.
2. **Explicit typing at boundaries.** Modules declare their types; the orchestration layer validates structure.
3. **Pragmatic safety.** We prevent the common bugs (missing fields, wrong types) without the boilerplate of nominal typing.

If you need nominal distinction, use a wrapper record:

```constellation
type UserId = { value: String }
type OrderId = { value: String }

# Now these are different types
in userId: UserId
result = ProcessOrder(userId)  # Compile error: expected OrderId, got UserId
```

---

## Influences

- **Row polymorphism (PureScript, OCaml):** Records with "at least these fields" semantics
- **TypeScript structural typing:** Shapes, not names, determine compatibility
- **Haskell Maybe:** Explicit optional types with propagation
- **SQL projections:** SELECT field1, field2 FROM table

The key insight: data pipelines are about transforming shapes. A type system for pipelines should excel at expressing shape transformations.
