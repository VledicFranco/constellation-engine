# Declarations

## Type Definitions

```
type TypeName = TypeExpression
```

Examples:

```
type User = { id: Int, name: String }
type UserList = List<User>
type Merged = TypeA + TypeB
```

## Input Declarations

```
in variableName: TypeExpression
```

Examples:

```
in userId: Int
in query: String
in items: Candidates<{ id: String, score: Float }>
```

## Assignments

```
variableName = expression
```

Examples:

```
result = process(input)
merged = a + b
projected = data[field1, field2]
```

## Output Declaration

Every program must have exactly one output:

```
out expression
```

Examples:

```
out result
out items[id, score] + computed[rank]
```
