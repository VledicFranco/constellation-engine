---
title: "Program Structure"
sidebar_position: 2
---

# Program Structure

A constellation-lang program consists of:

1. **Type definitions** (optional) - Define custom record types
2. **Input declarations** - Declare external inputs to the pipeline
3. **Assignments** - Compute intermediate values
4. **Output declaration** - Declare the pipeline output (exactly one required)

```
# Type definitions
type MyType = { field1: String, field2: Int }

# Input declarations
in inputName: TypeExpr

# Assignments
variable = expression

# Output (required, exactly one)
out expression
```
