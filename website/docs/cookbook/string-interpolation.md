---
title: "String Interpolation"
sidebar_position: 7
description: "Building dynamic strings with ${expression} syntax"
---

# String Interpolation

Build dynamic strings by embedding expressions directly inside string literals using `${expression}` syntax.

## Use Case

You need to format user-facing messages that include computed values, without manual concatenation.

## The Pipeline

```constellation
# string-interpolation.cst

@example("Alice")
in userName: String

@example(28)
in userAge: Int

@example(1500)
in accountBalance: Int

# Simple variable interpolation
greeting = "Hello, ${userName}!"

# Arithmetic expressions in interpolation
ageNextYear = "Next year you will be ${userAge + 1} years old."

# Multiple interpolations in one string
summary = "User ${userName} is ${userAge} years old."

# Escape sequences: \n for newline, \t for tab, \$ for literal dollar sign
formatted = "Account Summary:\n\tName: ${userName}\n\tBalance: \$${accountBalance}"

# Conditional message using interpolation with branch
balanceStatus = branch {
  accountBalance > 1000 -> "healthy",
  accountBalance > 0 -> "low",
  otherwise -> "overdrawn"
}
statusMessage = "Your account status is: ${balanceStatus}"

# Combining with other operations
upperName = Uppercase(userName)
formalGreeting = "Dear ${upperName}, welcome to our service!"

out greeting
out ageNextYear
out summary
out formatted
out statusMessage
out formalGreeting
```

## Explanation

| Feature | Syntax | Example |
|---|---|---|
| Variable | `${varName}` | `"Hello, ${userName}!"` |
| Arithmetic | `${expr + expr}` | `"Age: ${userAge + 1}"` |
| Newline | `\n` | `"Line1\nLine2"` |
| Tab | `\t` | `"Col1\tCol2"` |
| Literal `$` | `\$` | `"Price: \$${amount}"` |

Interpolation expressions are evaluated at runtime. Any expression valid in an assignment can appear inside `${}`.

:::tip
Keep interpolation expressions simple. For complex logic, compute the value in a separate variable first, then interpolate it.
:::

## Running the Example

### Input
```json
{
  "userName": "Alice",
  "userAge": 28,
  "accountBalance": 1500
}
```

### Expected Output
```json
{
  "greeting": "Hello, Alice!",
  "ageNextYear": "Next year you will be 29 years old.",
  "summary": "User Alice is 28 years old.",
  "formatted": "Account Summary:\n\tName: Alice\n\tBalance: $1500",
  "statusMessage": "Your account status is: healthy",
  "formalGreeting": "Dear ALICE, welcome to our service!"
}
```

## Variations

### Template with multiple fields

```constellation
type User = { name: String, role: String }
in user: User

welcome = "Welcome ${user.name}, you are logged in as ${user.role}."

out welcome
```

:::warning
Don't forget to escape `$` with `\$` when you need a literal dollar sign. `"Price: $100"` will fail; use `"Price: \$100"` instead.
:::

## Best Practices

1. **Use interpolation instead of `concat`** — `"Hello, ${name}!"` is clearer than `concat("Hello, ", concat(name, "!"))`
2. **Escape `$` when needed** — use `\$` for literal dollar signs in financial output
3. **Keep expressions simple** — complex logic should be in a separate variable, then interpolated

## Related Examples

- [Hello World](hello-world.md) — `concat` approach for comparison
- [Branch Expressions](branch-expressions.md) — conditional values used in interpolation
