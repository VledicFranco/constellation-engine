# Language Reference

> **Path**: `docs/language/`
> **Parent**: [docs/](../README.md)

The constellation-lang DSL for defining pipelines.

## Contents

| Path | Description |
|------|-------------|
| [types/](./types/) | Type system: primitives, records, lists, optionals, unions |
| [expressions/](./expressions/) | Operators, field access, control flow |
| [options/](./options/) | Module orchestration: retry, cache, timeout, etc. |

## Syntax Overview

### Declarations

```constellation
# Input declaration
in variableName: Type
in userId: String @example("user-123")

# Type alias
type Profile = { name: String, email: String }

# Output declaration
out result
out value1, value2, value3
```

### Module Calls

```constellation
# Basic call
result = ModuleName(param1, param2)

# Named parameters
user = GetUser(id: userId, includeHistory: true)

# With options
data = FetchData(id) with retry: 3, timeout: 5s
```

### Comments

```constellation
# This is a comment
result = Process(input)  # Inline comment
```

## Quick Reference

| Construct | Syntax | Example |
|-----------|--------|---------|
| Input | `in name: Type` | `in userId: String` |
| Output | `out expr` | `out result` |
| Type alias | `type Name = Type` | `type User = { id: String }` |
| Module call | `result = Module(args)` | `user = GetUser(id)` |
| With options | `with option: value` | `with retry: 3` |
| Field access | `record.field` | `user.name` |
| Element-wise | `list.field` | `users.name` |
| Merge | `a + b` | `user + profile` |
| Projection | `record[fields]` | `user[id, name]` |
| Guard | `expr when cond` | `data when valid` |
| Coalesce | `opt ?? default` | `name ?? "unknown"` |
