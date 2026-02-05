# String Functions

> **Path**: `docs/stdlib/string.md`
> **Parent**: [stdlib/](./README.md)

String manipulation and processing functions.

## Function Reference

| Function | Signature | Description |
|----------|-----------|-------------|
| `concat` | `(a: String, b: String) -> String` | Concatenate two strings |
| `string-length` | `(value: String) -> Int` | Get string length |
| `join` | `(list: List<String>, separator: String) -> String` | Join strings with delimiter |
| `split` | `(value: String, substring: String) -> List<String>` | Split by delimiter |
| `contains` | `(value: String, substring: String) -> Boolean` | Check for substring |
| `trim` | `(value: String) -> String` | Remove whitespace |
| `replace` | `(value: String, target: String, replacement: String) -> String` | Replace occurrences |

## Basic Operations

### concat

Concatenate two strings.

```constellation
in firstName: String
in lastName: String
fullName = concat(firstName, lastName)
out fullName
```

### string-length

Get the character count of a string.

```constellation
in text: String
len = string-length(text)
out len
```

Note: Returns character count, not byte count. Unicode is fully supported.

### trim

Remove leading and trailing whitespace.

```constellation
in raw: String
cleaned = trim(raw)
out cleaned
```

## Search Operations

### contains

Check if a string contains a substring.

```constellation
in text: String
in search: String
found = contains(text, search)
out found
```

### replace

Replace all occurrences of a target string.

```constellation
in text: String
in old: String
in new: String
result = replace(text, old, new)
out result
```

## List Operations

### join

Join a list of strings with a separator.

```constellation
in words: List<String>
in separator: String
sentence = join(words, separator)
out sentence
```

Example: `join(["a", "b", "c"], "-")` returns `"a-b-c"`.

### split

Split a string into a list by delimiter.

```constellation
in csv: String
fields = split(csv, ",")
out fields
```

Example: `split("a,b,c", ",")` returns `["a", "b", "c"]`.

## Edge Cases

| Function | Edge Case | Result |
|----------|-----------|--------|
| `concat` | `concat("", "hello")` | `"hello"` |
| `concat` | Unicode strings | Fully supported |
| `string-length` | `string-length("")` | `0` |
| `string-length` | Unicode chars | Character count |
| `join` | Empty list | `""` |
| `join` | Single element | Element only |
| `split` | Delimiter not found | Single-element list |
| `split` | Consecutive delimiters | Includes empty strings |
| `contains` | `contains("hello", "")` | `true` |
| `contains` | `contains("", "x")` | `false` |
| `trim` | `trim("   ")` | `""` |
| `replace` | No match | Original string |
| `replace` | `replace("aaa", "a", "b")` | `"bbb"` |

## Common Patterns

### Building Full Names

```constellation
in firstName: String
in lastName: String

separator = ", "
temp = concat(lastName, separator)
fullName = concat(temp, firstName)
out fullName
```

### Processing CSV Data

```constellation
in csvLine: String

# Split into fields
fields = split(csvLine, ",")

# Get field count
count = list-length(fields)

out count
```

### Cleaning User Input

```constellation
in userInput: String

# Remove whitespace and normalize
cleaned = trim(userInput)
normalized = replace(cleaned, "  ", " ")
out normalized
```

## Performance

| Function | Time Complexity |
|----------|-----------------|
| `concat` | O(n+m) |
| `string-length` | O(1) |
| `join` | O(total length) |
| `split` | O(n) |
| `contains` | O(n*m) worst case |
| `trim` | O(n) |
| `replace` | O(n*m) worst case |

## Error Guarantees

All string operations are safe and never raise exceptions. They handle empty strings and Unicode gracefully.

## Namespace

All string functions are in the `stdlib.string` namespace.
