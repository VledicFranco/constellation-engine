# List Functions

> **Path**: `docs/stdlib/list.md`
> **Parent**: [stdlib/](./README.md)

Functions for working with lists.

## Function Reference

| Function | Signature | Description |
|----------|-----------|-------------|
| `list-length` | `(list: List<Int>) -> Int` | Get list length |
| `list-first` | `(list: List<Int>) -> Int` | Get first element |
| `list-last` | `(list: List<Int>) -> Int` | Get last element |
| `list-is-empty` | `(list: List<Int>) -> Boolean` | Check if empty |
| `list-sum` | `(list: List<Int>) -> Int` | Sum all elements |
| `list-concat` | `(a: List<Int>, b: List<Int>) -> List<Int>` | Concatenate lists |
| `list-contains` | `(list: List<Int>, value: Int) -> Boolean` | Check membership |
| `list-reverse` | `(list: List<Int>) -> List<Int>` | Reverse order |

## Inspection Functions

### list-length

Get the number of elements in a list.

```constellation
in numbers: List<Int>
count = list-length(numbers)
out count
```

### list-is-empty

Check if a list has no elements.

```constellation
in numbers: List<Int>
empty = list-is-empty(numbers)
out empty
```

### list-contains

Check if a value exists in the list.

```constellation
in numbers: List<Int>
in target: Int
found = list-contains(numbers, target)
out found
```

## Access Functions

### list-first

Get the first element of a list.

```constellation
in numbers: List<Int>
first = list-first(numbers)
out first
```

**Error**: Raises `NoSuchElementException` if list is empty.

### list-last

Get the last element of a list.

```constellation
in numbers: List<Int>
last = list-last(numbers)
out last
```

**Error**: Raises `NoSuchElementException` if list is empty.

## Transformation Functions

### list-concat

Concatenate two lists.

```constellation
in listA: List<Int>
in listB: List<Int>
combined = list-concat(listA, listB)
out combined
```

### list-reverse

Reverse the order of elements.

```constellation
in numbers: List<Int>
reversed = list-reverse(numbers)
out reversed
```

## Aggregation Functions

### list-sum

Sum all elements in a list.

```constellation
in numbers: List<Int>
total = list-sum(numbers)
out total
```

Returns 0 for an empty list.

## Edge Cases

| Function | Edge Case | Result |
|----------|-----------|--------|
| `list-length` | Empty list | `0` |
| `list-is-empty` | Empty list | `true` |
| `list-is-empty` | Non-empty | `false` |
| `list-sum` | Empty list | `0` |
| `list-sum` | Negatives | Summed correctly |
| `list-contains` | Empty list | `false` |
| `list-reverse` | Empty list | `[]` |
| `list-reverse` | Single element | Same list |
| `list-concat` | Both empty | `[]` |
| `list-concat` | One empty | Other list |

## Safe Access Pattern

Always check for empty lists before using `list-first` or `list-last`:

```constellation
in numbers: List<Int>

# Safe first element access
isEmpty = list-is-empty(numbers)
safeFirst = if (isEmpty) 0 else list-first(numbers)
out safeFirst
```

## Common Patterns

### Data Aggregation

```constellation
in values: List<Int>

count = list-length(values)
total = list-sum(values)

out total
```

### Combining Lists

```constellation
in batch1: List<Int>
in batch2: List<Int>

all = list-concat(batch1, batch2)
totalCount = list-length(all)
out totalCount
```

### Checking Membership

```constellation
in allowed: List<Int>
in value: Int

isAllowed = list-contains(allowed, value)
out isAllowed
```

## Performance

| Function | Time | Space |
|----------|------|-------|
| `list-length` | O(1) | O(1) |
| `list-first` | O(1) | O(1) |
| `list-last` | O(1) | O(1) |
| `list-is-empty` | O(1) | O(1) |
| `list-sum` | O(n) | O(1) |
| `list-concat` | O(n+m) | O(n+m) |
| `list-contains` | O(n) | O(1) |
| `list-reverse` | O(n) | O(n) |

## Error Summary

| Function | Error Condition | Exception |
|----------|-----------------|-----------|
| `list-first` | Empty list | `NoSuchElementException` |
| `list-last` | Empty list | `NoSuchElementException` |

All other list functions are safe and never raise exceptions.

## Namespace

All list functions are in the `stdlib.list` namespace.
