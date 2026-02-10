---
title: "Type Algebra Patterns"
sidebar_position: 1
description: "Comprehensive guide to record operations, type composition, and type-driven design in constellation-lang"
---

# Type Algebra Patterns

Type algebra is the foundation of data transformation in Constellation. This guide covers record operations, type composition strategies, and patterns for building maintainable pipelines.

## Table of Contents

1. [Core Concepts](#core-concepts)
2. [Record Construction and Deconstruction](#record-construction-and-deconstruction)
3. [Record Merge Patterns](#record-merge-patterns)
4. [Field Access and Projection](#field-access-and-projection)
5. [Optional Type Handling](#optional-type-handling)
6. [Union Type Patterns](#union-type-patterns)
7. [List Operations and Transformations](#list-operations-and-transformations)
8. [Type-Driven Design Strategies](#type-driven-design-strategies)
9. [Type Composition Patterns](#type-composition-patterns)
10. [Inline Transforms vs Modules](#inline-transforms-vs-modules)
11. [Common Pitfalls and Solutions](#common-pitfalls-and-solutions)
12. [Performance Considerations](#performance-considerations)

---

## Core Concepts

### Type Algebra Operators

Constellation provides three primary type algebra operators:

| Operator | Syntax | Purpose | Result Type |
|----------|--------|---------|-------------|
| **Merge** | `A + B` | Combine records | Record with all fields from A and B |
| **Projection** | `record{field1, field2}` | Select fields | Record with only specified fields |
| **Field Access** | `record.field` | Extract single field | Type of the field |

### Type System Guarantees

All type algebra operations are validated at compile time:

- **Field existence**: Compiler verifies fields exist before access
- **Type compatibility**: Merge operations check type compatibility
- **Exhaustiveness**: Union type matches must cover all variants
- **Structural subtyping**: Records with extra fields can substitute for records with fewer fields

### When to Use Type Algebra

Type algebra shines when:

1. **Combining data sources**: Merging API responses, database results, and configuration
2. **Reshaping outputs**: Selecting specific fields for public APIs
3. **Enriching batch data**: Adding context to collections
4. **Building flexible interfaces**: Using structural subtyping for loose coupling

---

## Record Construction and Deconstruction

### Basic Record Construction

Records are constructed from module outputs or inline using record syntax:

```constellation
# Inline record construction
type Config = { timeout: Int, retries: Int }

in timeout: Int
in retries: Int

# Records are typically built by modules, not inline literals
# But you can use field access and module outputs to build records
config = { timeout: timeout, retries: retries }

out config
```

**Important**: Constellation does not support literal record construction syntax (e.g., `{ field: value }`). Records are built by:
1. Module outputs
2. Input declarations
3. Type algebra operations (merge, projection)

### Deconstruction via Field Access

Extract fields from records using dot notation:

```constellation
type User = { id: Int, name: String, email: String, role: String }

in user: User

# Deconstruct by accessing individual fields
userId = user.id
userName = user.name
userEmail = user.email
userRole = user.role

# Use fields independently
uppercaseName = Uppercase(userName)
isAdmin = eq(userRole, "admin")

out userId
out uppercaseName
out isAdmin
```

**Pattern**: Extract only the fields you need, then operate on them. This makes data flow explicit.

### Deconstruction via Projection

Select multiple fields at once using projection:

```constellation
type FullProfile = {
  id: Int,
  name: String,
  email: String,
  phone: String,
  address: String,
  city: String,
  state: String,
  zipCode: String,
  country: String
}

in profile: FullProfile

# Project to public API shape
publicProfile = profile{id, name, email}

# Project to contact info
contactInfo = profile{email, phone}

# Project to address
address = profile{address, city, state, zipCode, country}

out publicProfile
out contactInfo
out address
```

**Pattern**: Use projection when you need to pass multiple fields to downstream operations or outputs.

### Nested Record Access

Access nested record fields using chained dot notation:

```constellation
type Address = { street: String, city: String, zipCode: String }
type User = { name: String, address: Address }

in user: User

# Access nested fields
city = user.address.city
street = user.address.street

# Process nested data
uppercaseCity = Uppercase(city)

out uppercaseCity
```

**Pattern**: Extract nested data early in your pipeline, then operate on flat values.

---

## Record Merge Patterns

### Basic Merge (Right-Biased)

The `+` operator merges two records. When field names conflict, the right operand wins:

```constellation
type BaseUser = { id: Int, name: String, email: String }
type UpdatedInfo = { email: String, phone: String }

in base: BaseUser
in update: UpdatedInfo

# Merge: update.email overwrites base.email
merged = base + update
# Result type: { id: Int, name: String, email: String, phone: String }

out merged
```

**Critical**: Right-hand side wins conflicts. `A + B` means "start with A, override with B".

### Sequential Merging

Chain merges to combine multiple sources:

```constellation
type User = { id: Int, name: String }
type Profile = { bio: String, avatar: String }
type Preferences = { theme: String, notifications: Boolean }

in user: User
in profile: Profile
in prefs: Preferences

# Left-to-right evaluation: (user + profile) + prefs
combined = user + profile + prefs
# Result: { id, name, bio, avatar, theme, notifications }

out combined
```

**Pattern**: Order merges by priority. Rightmost source has highest priority for conflicting fields.

### Merge-Then-Project Pattern

Combine data sources, then select specific fields:

```constellation
type Order = { orderId: String, customerId: Int, total: Float }
type Customer = { customerId: Int, name: String, email: String }
type Shipping = { orderId: String, trackingNumber: String, estimatedDelivery: String }

in order: Order
in customer: Customer
in shipping: Shipping

# Merge all data
full = order + customer + shipping

# Project to invoice shape
invoice = full{orderId, name, email, total, estimatedDelivery}

# Project to tracking info
tracking = full{orderId, trackingNumber, estimatedDelivery}

out invoice
out tracking
```

**Pattern**: Merge everything first, then create multiple projections for different use cases.

### Enrichment Pattern

Add context fields to existing records:

```constellation
type Event = { eventType: String, userId: Int, timestamp: Int }
type RequestContext = { requestId: String, ipAddress: String }

in event: Event
in context: RequestContext

# Enrich event with request context
enrichedEvent = event + context
# Result: { eventType, userId, timestamp, requestId, ipAddress }

out enrichedEvent
```

**Pattern**: Use for logging, auditing, and tracing. Merge context into domain events.

### Conditional Merge with Optionals

Merge optional records using guards and coalesce:

```constellation
type BaseConfig = { timeout: Int, retries: Int }
type Override = { timeout: Int, maxConnections: Int }

in base: BaseConfig
in maybeOverride: Optional<Override>

# Unwrap optional with empty record fallback
# Note: You'd typically use a module that returns an empty record
override = maybeOverride ?? base

# Merge with base
final = base + override

out final
```

**Pattern**: Use optional overrides for configuration merging. Provide sensible defaults.

---

## Field Access and Projection

### Field Access for Single Values

Use dot notation when you need a single field:

```constellation
type Product = { id: String, name: String, price: Float, stock: Int }

in product: Product

# Extract single fields
productName = product.name
productPrice = product.price

# Use in computations
displayName = Uppercase(productName)
isExpensive = gt(productPrice, 100.0)

out displayName
out isExpensive
```

**When to use**: Single field extraction for computations, conditionals, or module inputs.

### Projection for Multiple Fields

Use `{}` syntax when you need multiple fields:

```constellation
type FullProduct = {
  id: String,
  name: String,
  description: String,
  price: Float,
  cost: Float,
  stock: Int,
  supplier: String,
  internalNotes: String
}

in product: FullProduct

# Public API projection
publicInfo = product{id, name, description, price, stock}

# Internal reporting projection
internalInfo = product{id, name, cost, supplier, internalNotes}

# Analytics projection
analyticsInfo = product{id, price, cost, stock}

out publicInfo
out internalInfo
out analyticsInfo
```

**When to use**: Selecting multiple fields for outputs, module inputs, or intermediate shapes.

### Projection Preserves Type Safety

The compiler verifies all projected fields exist:

```constellation
type User = { id: Int, name: String, email: String }

in user: User

# Compiler error: field 'phone' does not exist
# contactInfo = user{id, name, phone}

# Correct: only existing fields
contactInfo = user{id, name, email}

out contactInfo
```

**Benefit**: Typos and refactoring errors are caught at compile time.

### Projection with Nested Records

Project nested structures to flatten data:

```constellation
type Address = { street: String, city: String, state: String, zipCode: String }
type Company = { name: String, address: Address, phone: String }

in company: Company

# Access nested then project
address = company.address
location = address{city, state}

# Or extract individual nested fields
city = company.address.city
state = company.address.state

out location
out city
out state
```

**Pattern**: Extract nested records first, then project or access fields.

### Combining Access and Projection

Mix field access and projection for complex reshaping:

```constellation
type Order = {
  id: String,
  customer: { name: String, email: String, tier: String },
  items: Candidates<{ productId: String, quantity: Int, price: Float }>,
  total: Float
}

in order: Order

# Extract nested customer
customer = order.customer

# Project customer fields
customerInfo = customer{name, email}

# Extract scalar fields
orderId = order.id
orderTotal = order.total

# Access items (stays as Candidates)
items = order.items

out customerInfo
out orderId
out orderTotal
out items
```

**Pattern**: Break complex records into manageable pieces using both access and projection.

---

## Optional Type Handling

### Optional Inputs with Coalesce

Handle optional inputs with default values:

```constellation
@example(30)
in defaultTimeout: Int

@example(3)
in defaultRetries: Int

# Optional configuration overrides
in maybeTimeout: Optional<Int>
in maybeRetries: Optional<Int>

# Unwrap with defaults immediately
timeout = maybeTimeout ?? defaultTimeout
retries = maybeRetries ?? defaultRetries

# Use unwrapped values in computations
totalWaitTime = timeout * retries

out timeout
out retries
out totalWaitTime
```

**Pattern**: Unwrap optionals immediately at the top of your pipeline. Don't pass `Optional<T>` through multiple steps.

### Guards Create Optionals

Use `when` to create conditional values:

```constellation
@example(85)
in score: Int

@example(50)
in passingScore: Int

# Guard produces Optional<String>
passMessage = "Passed!" when score >= passingScore

# Guard produces Optional<Int>
bonus = 10 when score > 90

# Unwrap with fallback
resultMessage = passMessage ?? "Did not pass"
actualBonus = bonus ?? 0

out resultMessage
out actualBonus
```

**Pattern**: Guards are for conditional existence. Coalesce immediately if you need a concrete value.

### Chained Coalesce for Priority Fallbacks

Try multiple options in priority order:

```constellation
@example(75)
in score: Int

# Multiple tiers
goldTier = 100 when score >= 90
silverTier = 50 when score >= 70
bronzeTier = 25 when score >= 50

# First non-None wins
reward = goldTier ?? silverTier ?? bronzeTier ?? 0

out reward
```

**Pattern**: Express tiered logic as a chain of guards + coalesce. Reads like a priority list.

### Optional Record Fields

Handle optional fields within records:

```constellation
type UserProfile = {
  name: String,
  email: String,
  phone: Optional<String>,
  bio: Optional<String>
}

in profile: UserProfile

# Extract required fields
name = profile.name
email = profile.email

# Extract optional fields
phone = profile.phone
bio = profile.bio

# Provide defaults for optionals
displayPhone = phone ?? "N/A"
displayBio = bio ?? "No bio provided"

out name
out email
out displayPhone
out displayBio
```

**Pattern**: Extract optional fields, then coalesce for display or processing.

### Conditionally Merging Optionals

Merge only when optional data is present:

```constellation
type BaseRecord = { id: Int, name: String }
type Enhancement = { score: Float, rank: Int }

in base: BaseRecord
in maybeEnhancement: Optional<Enhancement>

# Guard the merge
enhancedWhenPresent = (base + enhancement) when maybeEnhancement is Some(enhancement)

# Or use explicit None check
hasEnhancement = maybeEnhancement is Some(_)
result = if (hasEnhancement) base + maybeEnhancement else base

out result
```

**Note**: Pattern matching syntax shown here is illustrative. Current Constellation supports coalesce but not full pattern matching on optionals. Use modules to conditionally merge.

### Best Practices for Optionals

1. **Unwrap early**: Don't pass `Optional<T>` through your pipeline
2. **Provide sensible defaults**: Use `??` with meaningful fallback values
3. **Use guards for conditional existence**: Not for error handling
4. **Chain for priority**: `a ?? b ?? c ?? default` is readable and efficient

---

## Union Type Patterns

### Defining Union Types

Model variant data with union types:

```constellation
# Success or failure result
type Result = { value: Int, status: String } | { error: String, code: Int }

# Flexible identifiers
type Identifier = String | Int

# API response variants
type ApiResponse =
  { data: Candidates<{ id: String, name: String }>, count: Int } |
  { error: String, retryAfter: Int }

# Processing states
type TaskState =
  { pending: Boolean } |
  { inProgress: Int } |
  { completed: { result: String, duration: Int } } |
  { failed: { reason: String, retryCount: Int } }
```

**Pattern**: Name union types for reuse. Use descriptive field names to distinguish variants.

### Union Inputs

Accept variant data from external sources:

```constellation
type Result = { success: Boolean, value: Int } | { success: Boolean, error: String }

in result: Result

# Pass through union-typed values
output = result

out output
```

**Pattern**: Union inputs model real-world APIs where response shape varies.

### Pattern Matching on Unions

Discriminate between union variants with `match`:

```constellation
type ApiResult = { data: String, count: Int } | { error: String }

in result: ApiResult

# Match extracts fields based on shape
message = match result {
  { data, count } -> "Success: ${count} items",
  { error } -> "Error: ${error}"
}

out message
```

**Critical**: All variants must be covered. The compiler enforces exhaustiveness.

### Exhaustiveness Checking

Compiler verifies all variants are handled:

```constellation
type Status = { active: Boolean } | { paused: Int } | { stopped: String }

in status: Status

# Compiler error if any variant is missing
result = match status {
  { active } -> "Active",
  { paused } -> "Paused"
  # Error: Missing pattern for { stopped: String }
}
```

**Solution**: Add remaining patterns or use wildcard `_`:

```constellation
result = match status {
  { active } -> "Active",
  _ -> "Not active"
}
```

### Field Binding in Patterns

Pattern variables become available in the match arm:

```constellation
type User = { id: Int, name: String, role: String } | { error: String, code: Int }

in user: User

greeting = match user {
  { id, name, role } -> "Welcome ${name} (ID: ${id}, Role: ${role})",
  { error, code } -> "Error ${code}: ${error}"
}

out greeting
```

**Pattern**: Bind all fields you need in the match arm. Unused fields can be omitted or named `_`.

### Nested Union Matching

Handle unions within records:

```constellation
type Inner = { x: Int } | { y: String }
type Outer = { inner: Inner, metadata: String }

in data: Outer

# Access outer field
metadata = data.metadata

# Extract inner union
inner = data.inner

# Match on inner
innerResult = match inner {
  { x } -> x,
  { y } -> 0  # Default for string variant
}

out metadata
out innerResult
```

**Pattern**: Extract nested unions first, then match separately for clarity.

### Union with Candidates

Model batch operations where each item can succeed or fail:

```constellation
type ItemResult = { id: String, value: Int } | { id: String, error: String }

in results: Candidates<ItemResult>

# Each candidate can be either variant
# Pattern match in a Map module to handle each item
out results
```

**Pattern**: Use unions inside `Candidates<T>` for batch operations with heterogeneous results.

### Union Design Best Practices

1. **Use discriminating fields**: Make variants structurally distinct
2. **Name your unions**: `type Result = Success | Error` is clearer than inline unions
3. **Keep variants simple**: Deeply nested unions are hard to match
4. **Document variants**: Comment what each variant represents
5. **Consider Optional vs Union**: `Optional<T>` is clearer than `T | None` for nullable values

---

## List Operations and Transformations

### Candidates Type

`Candidates<T>` represents a collection (list) of items of type `T`:

```constellation
type Item = { id: String, value: Int }

in items: Candidates<Item>

# Pass to modules that process collections
out items
```

**Key point**: Candidates is the primary collection type in Constellation.

### Broadcasting Records to Candidates

Merge a record into every element of a Candidates:

```constellation
type Item = { id: String, name: String }
type Context = { requestId: String, timestamp: Int }

in items: Candidates<Item>
in context: Context

# Broadcast context to every item
enriched = items + context
# Result type: Candidates<{ id: String, name: String, requestId: String, timestamp: Int }>

out enriched
```

**Pattern**: Use for adding request context, user info, or configuration to batch data.

### Merging Two Candidates

Element-wise merge when both sides are Candidates:

```constellation
type Base = { id: String, name: String }
type Extra = { id: String, score: Float }

in bases: Candidates<Base>
in extras: Candidates<Extra>

# Element-wise merge (must have same length)
merged = bases + extras
# Result: Candidates<{ id: String, name: String, score: Float }>

out merged
```

**Critical**: Element-wise merge requires both Candidates to have the same length. Runtime error otherwise.

### Candidates + Candidates Right-Bias

When merging two Candidates with overlapping fields, right side wins:

```constellation
type A = { id: String, value: Int }
type B = { id: String, value: Int, extra: String }

in listA: Candidates<A>
in listB: Candidates<B>

# listB.value overwrites listA.value for each pair
result = listA + listB
# Result: Candidates<{ id: String, value: Int, extra: String }>

out result
```

**Pattern**: Use when you have a base dataset and want to apply updates element-wise.

### Filtering Candidates with Guards

Create optional values in a map, then filter:

```constellation
type Item = { id: String, score: Int }

in items: Candidates<Item>

# Map with guard - not directly supported, use module
# This is illustrative of the pattern:

# Module: FilterHighScores
# Inputs: items: Candidates<Item>, threshold: Int
# Returns: Candidates<Item> (filtered)

@example(70)
in threshold: Int

filtered = FilterHighScores(items, threshold)

out filtered
```

**Pattern**: Use filtering modules. Guards work on single values, not collections.

### Transforming Candidates Fields

Access fields from Candidates items requires mapping:

```constellation
type User = { id: Int, name: String, email: String }

in users: Candidates<User>

# Extract a single field from each item requires a module
# Module: ExtractField
# Inputs: records: Candidates<{...}>, fieldName: String
# Returns: Candidates<extracted type>

names = ExtractNames(users)  # Returns Candidates<String>

out names
```

**Pattern**: Field extraction from Candidates requires a mapping module.

### Projecting Candidates Items

Project each item in a Candidates:

```constellation
type FullUser = { id: Int, name: String, email: String, phone: String, address: String }

in users: Candidates<FullUser>

# Projection applies to the inner record type
publicUsers = users{id, name, email}
# Type: Candidates<{ id: Int, name: String, email: String }>

out publicUsers
```

**Pattern**: Use projection on Candidates to select fields from each item. Syntax works on the collection directly.

### Flattening Nested Candidates

Handle Candidates of Candidates:

```constellation
type Group = { groupId: String, members: Candidates<{ userId: Int, name: String }> }

in groups: Candidates<Group>

# Access nested Candidates
# Each group has a members field that is itself a Candidates

# Flattening requires a module
# Module: FlattenGroups
# Inputs: groups: Candidates<Group>
# Returns: Candidates<{ userId: Int, name: String }>

allMembers = FlattenGroups(groups)

out allMembers
```

**Pattern**: Use flattening modules for nested collections.

### Aggregating Candidates

Reduce a Candidates to a single value:

```constellation
type Sale = { amount: Float, productId: String }

in sales: Candidates<Sale>

# Aggregation requires modules
# Module: SumAmounts
# Inputs: sales: Candidates<Sale>
# Returns: Float

totalSales = SumAmounts(sales)

# Module: CountItems
# Inputs: items: Candidates<T>
# Returns: Int

itemCount = CountItems(sales)

out totalSales
out itemCount
```

**Pattern**: Use aggregation modules for sum, count, average, min, max operations.

### Sorting Candidates

Sort collections by field or computed value:

```constellation
type Product = { id: String, name: String, price: Float, stock: Int }

in products: Candidates<Product>

# Sorting requires a module
# Module: SortByPrice
# Inputs: products: Candidates<Product>
# Returns: Candidates<Product> (sorted)

sortedByPrice = SortByPrice(products)

out sortedByPrice
```

**Pattern**: Create type-specific sorting modules or parameterized sorting modules.

### Batching and Windowing

Group Candidates into batches:

```constellation
type Event = { timestamp: Int, userId: Int, eventType: String }

in events: Candidates<Event>

@example(100)
in batchSize: Int

# Batching requires a module
# Module: BatchEvents
# Inputs: events: Candidates<Event>, batchSize: Int
# Returns: Candidates<Candidates<Event>>

batches = BatchEvents(events, batchSize)

out batches
```

**Pattern**: Use batching modules for chunking large datasets or implementing windowing.

---

## Type-Driven Design Strategies

### Start with Types

Define your data structures before writing logic:

```constellation
# 1. Define domain types
type User = { id: Int, email: String, role: String }
type Order = { orderId: String, userId: Int, total: Float, items: Candidates<Item> }
type Item = { productId: String, quantity: Int, price: Float }

# 2. Define inputs
in user: User
in orders: Candidates<Order>

# 3. Define intermediate types
type OrderSummary = { orderId: String, total: Float, itemCount: Int }

# 4. Now write the pipeline logic
# ...
```

**Benefit**: Types document your data model and catch errors early.

### Use Type Aliases for Clarity

Name complex types for reuse:

```constellation
type UserId = Int
type Email = String
type Price = Float

type Product = { id: String, name: String, price: Price }
type Customer = { userId: UserId, email: Email, name: String }
```

**Pattern**: Type aliases make domain concepts explicit and pipelines more readable.

### Structural Subtyping for Flexibility

Records with extra fields satisfy requirements for records with fewer fields:

```constellation
type MinimalUser = { id: Int, name: String }

# Module that accepts MinimalUser
# def ProcessUser(user: MinimalUser): ...

type FullUser = { id: Int, name: String, email: String, phone: String }

in fullUser: FullUser

# FullUser is structurally compatible with MinimalUser
result = ProcessUser(fullUser)  # OK: fullUser has id and name

out result
```

**Benefit**: Modules can specify minimal requirements without coupling to full record shapes.

### Design Modules for Type Composition

Modules should accept and return records that compose well:

```constellation
# Good: Returns a record that can be merged
# Module: EnrichUser
# Inputs: userId: Int
# Returns: { bio: String, joinDate: String }

# Usage:
type User = { id: Int, name: String, email: String }
in user: User

enrichment = EnrichUser(user.id)
fullProfile = user + enrichment
# Result: { id, name, email, bio, joinDate }
```

**Pattern**: Design module outputs as mergeable records for easy composition.

### Validate Types at Boundaries

Use input types to enforce contracts:

```constellation
# API inputs should be strongly typed
type CreateUserRequest = {
  email: String,
  name: String,
  password: String
}

type CreateUserResponse = {
  userId: Int,
  email: String,
  createdAt: String
}

in request: CreateUserRequest

# Pipeline logic...
response = CreateUser(request)

out response: CreateUserResponse
```

**Pattern**: Declare explicit input and output types for public APIs. Use projection to enforce output shape.

### Progressive Refinement

Start with broad types, narrow as you go:

```constellation
type RawEvent = {
  eventType: String,
  userId: Optional<Int>,
  sessionId: Optional<String>,
  timestamp: Int,
  payload: String
}

in event: RawEvent

# Refine types through validation
hasUserId = event.userId is Some(_)
userId = event.userId ?? 0

# Build refined type
type ValidatedEvent = { eventType: String, userId: Int, timestamp: Int }

validated = ValidatedEvent(event.eventType, userId, event.timestamp)

# Continue with validated
out validated
```

**Pattern**: Accept loose types at boundaries, refine internally for type safety.

### Compose Types Like Legos

Build complex types from simple pieces:

```constellation
type Coordinates = { lat: Float, lon: Float }
type Timestamp = { createdAt: Int, updatedAt: Int }
type Identifiable = { id: String }

# Compose base types
type Location = Identifiable + Coordinates + Timestamp + { name: String }
# Result: { id: String, lat: Float, lon: Float, createdAt: Int, updatedAt: Int, name: String }
```

**Pattern**: Define reusable type fragments, compose them for domain types.

---

## Type Composition Patterns

### Layered Enrichment

Build up rich records incrementally:

```constellation
type BaseEntity = { id: String, type: String }

in entity: BaseEntity

# Layer 1: Add metadata
metadata = GetMetadata(entity.id)
withMetadata = entity + metadata
# Result: { id, type, createdAt, updatedAt, author }

# Layer 2: Add computed properties
computed = ComputeProperties(entity.id)
withComputed = withMetadata + computed
# Result: { id, type, ..., score, rank, category }

# Layer 3: Add related data
related = FetchRelated(entity.id)
full = withComputed + related
# Result: { id, type, ..., relatedIds, parentId }

out full
```

**Pattern**: Build rich entities by merging layers. Each layer adds a group of related fields.

### Fan-Out / Fan-In

Fetch from multiple sources, merge results:

```constellation
type EntityId = String

in entityId: EntityId

# Fan out: parallel fetches
basicInfo = GetBasicInfo(entityId)
stats = GetStatistics(entityId)
relationships = GetRelationships(entityId)

# Fan in: merge results
complete = basicInfo + stats + relationships

out complete
```

**Pattern**: Parallel data fetching with merge. Enables efficient batching and caching.

### Projection Pipelines

Chain projections to progressively refine data:

```constellation
type FullRecord = {
  id: String,
  name: String,
  email: String,
  phone: String,
  address: String,
  city: String,
  state: String,
  zipCode: String,
  internalNotes: String,
  accountStatus: String,
  lastLogin: Int
}

in record: FullRecord

# Stage 1: Remove internal fields
externalView = record{id, name, email, phone, address, city, state, zipCode, accountStatus, lastLogin}

# Stage 2: Public API surface
publicView = externalView{id, name, email}

# Stage 3: Contact card
contactView = externalView{name, email, phone}

out publicView
out contactView
```

**Pattern**: Start with full data, create multiple projections for different consumers.

### Conditional Composition

Merge different data based on conditions:

```constellation
type BaseData = { id: Int, name: String }
type PremiumData = { features: Candidates<String>, support: String }
type StandardData = { limitedFeatures: Candidates<String> }

in base: BaseData
in isPremium: Boolean

# Fetch conditional data
premiumData = GetPremiumData(base.id) when isPremium
standardData = GetStandardData(base.id) when not isPremium

# Conditional merge
withPremium = base + premiumData when isPremium
withStandard = base + standardData when not isPremium

result = withPremium ?? withStandard ?? base

out result
```

**Pattern**: Use guards and coalesce for conditional merges. Provide sensible fallbacks.

### Merge with Override Semantics

Explicitly override fields using right-biased merge:

```constellation
type DefaultConfig = { timeout: Int, retries: Int, caching: Boolean }
type UserOverrides = { timeout: Int }

in defaults: DefaultConfig
in overrides: UserOverrides

# Overrides win
final = defaults + overrides
# Result: { timeout: <from overrides>, retries: <from defaults>, caching: <from defaults> }

out final
```

**Pattern**: Use merge for configuration override systems. Document right-bias behavior.

### Symmetric Merge Pattern

When both sides are equally important:

```constellation
type LeftSource = { a: Int, b: String }
type RightSource = { c: Float, d: Boolean }

in left: LeftSource
in right: RightSource

# No conflicts, symmetric merge
combined = left + right
# Result: { a, b, c, d }

out combined
```

**Pattern**: Design records without overlapping fields for symmetric composition.

### Record Transformation Chains

Chain projections and merges:

```constellation
type Input = { a: Int, b: Int, c: Int, d: Int, e: Int }

in input: Input

# Project subset
step1 = input{a, b, c}

# Transform
transformed = Transform(step1)

# Merge new data
enriched = transformed + { extra: "value" }

# Project final shape
output = enriched{a, extra}

out output
```

**Pattern**: Chain type algebra operations to progressively transform data shapes.

---

## Inline Transforms vs Modules

### When to Use Inline Transforms

Use type algebra (merge, projection, field access) when:

1. **Reshaping data**: Selecting or combining fields
2. **No complex logic**: Pure structural transformations
3. **Clear intent**: Operation is self-documenting

```constellation
type FullUser = { id: Int, name: String, email: String, phone: String, internal: String }

in user: FullUser

# Inline: Clear and simple
publicUser = user{id, name, email}

out publicUser
```

**Benefit**: No module indirection, clear data flow, minimal overhead.

### When to Use Modules

Use modules when:

1. **Business logic**: Any computation beyond field selection
2. **External effects**: API calls, database queries, file I/O
3. **Complex transforms**: Multi-step computations
4. **Reusability**: Logic used in multiple pipelines
5. **Testing**: Need to test transformation in isolation

```constellation
type RawScore = { correct: Int, total: Int, timeSpent: Int }
type ScoringResult = { score: Float, grade: String, percentile: Int }

in raw: RawScore

# Module: Encapsulates scoring algorithm
result = CalculateScore(raw)

out result
```

**Benefit**: Encapsulation, testability, reusability.

### Hybrid Approach

Combine both for clarity:

```constellation
type FullResponse = {
  id: String,
  data: { value: Int, processed: Boolean },
  metadata: { timestamp: Int, requestId: String },
  internal: String
}

in response: FullResponse

# Inline: Extract nested data
data = response.data
metadata = response.metadata

# Module: Business logic
processedValue = ProcessData(data.value)

# Inline: Reshape output
output = {
  id: response.id,
  processedValue: processedValue,
  timestamp: metadata.timestamp
}

out output
```

**Pattern**: Use inline transforms for structure, modules for logic.

### Performance Considerations

Inline transforms are zero-cost abstractions:

```constellation
# These compile to direct field access, no overhead
userId = user.id
publicInfo = user{name, email}
merged = base + extension
```

Module calls have overhead:

```constellation
# Module invocation, serialization, execution
result = ExpensiveComputation(data)
```

**Guideline**: Prefer inline for pure structural operations. Use modules for everything else.

### Code Organization

Inline transforms keep pipelines readable:

```constellation
# Good: Clear data flow
input = request{userId, itemId}
user = GetUser(input.userId)
item = GetItem(input.itemId)
context = user + item
result = ProcessRequest(context)
out result
```

Too much inlining reduces clarity:

```constellation
# Bad: Everything inline, hard to follow
out ProcessRequest(GetUser(request{userId, itemId}.userId) + GetItem(request{userId, itemId}.itemId))
```

**Guideline**: Break complex expressions into named intermediate steps.

---

## Common Pitfalls and Solutions

### Pitfall: Unintended Field Overwriting

```constellation
type A = { id: Int, value: Int }
type B = { id: Int, value: Int, extra: String }

in a: A
in b: B

# Pitfall: b.value overwrites a.value
merged = a + b
# Result: { id: <from b>, value: <from b>, extra: <from b> }
```

**Solution**: Be explicit about merge order and document conflicts:

```constellation
# Explicit: b overrides a
merged = a + b

# Or: a overrides b
merged = b + a

# Or: project to avoid conflicts
safeMerge = a{id} + b{value, extra}
```

### Pitfall: Forgotten Field in Projection

```constellation
type User = { id: Int, name: String, email: String }

in user: User

# Pitfall: Forgot to include 'email'
output = user{id, name}
# email is lost
```

**Solution**: Use projection intentionally. Comment what you're excluding:

```constellation
# Explicitly selecting public fields only
# Excluding: email (internal use)
publicUser = user{id, name}
```

### Pitfall: Assuming Element-Wise Merge Length

```constellation
type A = { id: Int }
type B = { value: String }

in listA: Candidates<A>  # Length: 5
in listB: Candidates<B>  # Length: 3

# Pitfall: Runtime error if lengths don't match
merged = listA + listB
```

**Solution**: Validate lengths or use modules that handle length mismatches:

```constellation
# Use a module that validates or pads
merged = SafeMerge(listA, listB)
```

### Pitfall: Deeply Nested Field Access

```constellation
# Pitfall: Hard to read and fragile
value = order.customer.address.billing.street.name
```

**Solution**: Extract intermediate records:

```constellation
customer = order.customer
address = customer.address
billing = address.billing
street = billing.street
streetName = street.name
```

### Pitfall: Overusing Optional

```constellation
# Pitfall: Everything is optional
in maybeUserId: Optional<Int>
in maybeName: Optional<String>
in maybeEmail: Optional<String>
in maybePhone: Optional<String>
```

**Solution**: Only make truly optional fields optional. Required inputs catch missing data early:

```constellation
# Better: Required inputs enforce complete data
in userId: Int
in name: String
in email: String
in phone: Optional<String>  # Genuinely optional
```

### Pitfall: Forgetting Right-Bias

```constellation
type Config = { timeout: Int }
type Defaults = { timeout: Int }

in config: Config
in defaults: Defaults

# Pitfall: Intended defaults to provide fallback, but config is overwritten
result = config + defaults
# Result: defaults.timeout used (probably not intended)
```

**Solution**: Order matters. Right side wins:

```constellation
# Correct: defaults first, config overrides
result = defaults + config
```

### Pitfall: Union Without Exhaustive Match

```constellation
type Result = { success: String } | { error: String } | { pending: Boolean }

in result: Result

# Pitfall: Forgot 'pending' case
message = match result {
  { success } -> "OK",
  { error } -> "Failed"
  # Compiler error: Missing pattern for { pending }
}
```

**Solution**: Compiler enforces exhaustiveness. Add missing patterns or wildcard:

```constellation
message = match result {
  { success } -> "OK",
  { error } -> "Failed",
  { pending } -> "Processing"
}
```

### Pitfall: Projection on Non-Record

```constellation
in value: Int

# Pitfall: Can't project fields from Int
result = value{field}  # Type error
```

**Solution**: Projection only works on records:

```constellation
type Record = { value: Int, field: String }
in record: Record
result = record{value}  # OK
```

---

## Performance Considerations

### Compile-Time Optimizations

Type algebra operations are optimized away at compile time:

```constellation
# These are zero-cost
field = record.field              # Direct field access
subset = record{a, b, c}          # Compile-time projection
merged = record1 + record2        # Structural merge
```

**Benefit**: No runtime overhead for type algebra operations.

### Module Call Overhead

Modules have invocation overhead:

```constellation
# Runtime cost
result = ExpensiveModule(input)
```

**Guideline**: Use modules for logic, inline transforms for structure.

### Merge Strategy

Right-biased merge is efficient:

```constellation
# Efficient: Single pass
result = a + b + c + d
```

Deeply nested merges have the same cost:

```constellation
# Also efficient: Still single pass
result = ((a + b) + c) + d
```

### Projection Cost

Projection is free at compile time:

```constellation
# No runtime cost
subset = largeRecord{field1, field2}
```

**Benefit**: Use projection liberally for clarity without performance cost.

### Candidates Operations

Broadcasting is efficient:

```constellation
type Item = { id: String }
type Context = { requestId: String }

in items: Candidates<Item>
in context: Context

# Efficient: Context is not copied, only referenced
enriched = items + context
```

Element-wise merge requires iteration:

```constellation
type A = { id: String }
type B = { value: Int }

in listA: Candidates<A>
in listB: Candidates<B>

# Requires iteration: O(n)
merged = listA + listB
```

**Guideline**: Broadcasting is cheaper than element-wise merge.

### Field Access Patterns

Repeated field access is optimized:

```constellation
# Multiple accesses to same field: optimized
name1 = user.name
name2 = user.name
# Compiler can optimize to single access
```

Nested access is efficient:

```constellation
# Nested access: Direct path, no temporary allocations
city = user.address.city
```

### When to Optimize

Optimize when:

1. **Hot paths**: Operations in tight loops or high-frequency endpoints
2. **Large data**: Processing large Candidates or deeply nested records
3. **Profiling shows bottleneck**: Measure first, optimize second

Don't optimize:

1. **Premature optimization**: Clarity first, optimize if needed
2. **Type algebra operations**: Already optimized by compiler
3. **One-time setup**: Pipeline initialization is not a hot path

---

## Summary

### Key Takeaways

1. **Merge (`+`)**: Combines records, right side wins conflicts
2. **Projection (`{}`)**: Selects specific fields from records
3. **Field Access (`.`)**: Extracts single fields
4. **Optionals**: Use `??` to unwrap with defaults
5. **Unions**: Use `match` for exhaustive pattern matching
6. **Candidates**: Broadcast records or element-wise merge
7. **Inline vs Modules**: Inline for structure, modules for logic
8. **Type-Driven Design**: Start with types, let compiler enforce correctness

### Design Principles

1. **Composition over Complexity**: Build complex types from simple pieces
2. **Explicit over Implicit**: Make data flow clear with named steps
3. **Types as Documentation**: Use types to communicate intent
4. **Fail Fast**: Leverage compile-time checking
5. **Progressive Refinement**: Start broad, narrow as you go

### Common Patterns Summary

| Pattern | Syntax | Use Case |
|---------|--------|----------|
| **Field Extraction** | `value = record.field` | Get single value |
| **Multi-Field Selection** | `subset = record{a, b, c}` | Select multiple fields |
| **Data Enrichment** | `enriched = base + extra` | Add fields |
| **Override Config** | `final = defaults + overrides` | Configuration merge |
| **Broadcast Context** | `enriched = items + context` | Add context to collection |
| **Optional with Default** | `value = optional ?? default` | Handle missing values |
| **Tiered Fallback** | `result = a ?? b ?? c ?? default` | Priority-based defaults |
| **Union Discrimination** | `match union { ... }` | Handle variants |
| **Fan-Out / Fan-In** | `result = a + b + c` | Combine parallel fetches |
| **Progressive Projection** | `public = full{...}` | Narrow types |

### Next Steps

1. **Practice**: Write pipelines using type algebra
2. **Experiment**: Try different composition patterns
3. **Review**: Read your pipelines - are types making intent clear?
4. **Refactor**: Simplify complex logic with better type composition
5. **Learn More**: See [Type System Reference](../foundations/type-system.md)

---

## Additional Resources

- [Language Type Algebra Reference](../../language/type-algebra.md)
- [Type System Foundations](../foundations/type-system.md)
- [Cookbook: Record Types](../../cookbook/record-types.md)
- [Cookbook: Optional Types](../../cookbook/optional-types.md)
- [Cookbook: Union Types](../../cookbook/union-types.md)
- [Cookbook: Guard and Coalesce](../../cookbook/guard-and-coalesce.md)
