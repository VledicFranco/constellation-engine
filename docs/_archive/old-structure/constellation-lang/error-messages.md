# Error Messages

constellation-lang provides precise error messages with line and column information.

## Undefined Variable

```
out undefined_var
```
```
Error at 1:5: Undefined variable: undefined_var
```

## Undefined Type

```
in x: NonExistent
```
```
Error at 1:6: Undefined type: NonExistent
```

## Undefined Function

```
result = unknown_func(x)
```
```
Error at 1:10: Undefined function: unknown_func
```

## Type Mismatch

```
# If function expects Int but receives String
result = expects_int(stringValue)
```
```
Error at 1:22: Type mismatch: expected Int, got String
```

## Invalid Projection

```
in data: { id: Int, name: String }
result = data[id, nonexistent]
```
```
Error at 2:10: Invalid projection: field 'nonexistent' not found. Available: id, name
```

## Incompatible Merge

```
in a: Int
in b: String
result = a + b
```
```
Error at 3:10: Cannot merge types: Int + String
```

## Parse Errors

```
in x: Int
out @invalid
```
```
Error at 2:5: Parse error: expected identifier
```
