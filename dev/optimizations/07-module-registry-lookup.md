# Optimization 07: Module Registry Fast Lookup

**Priority:** 7 (Medium Impact)
**Expected Gain:** <1ms per lookup, cleaner code
**Complexity:** Low
**Status:** Not Implemented

---

## Problem Statement

Module lookup performs string operations on every access:

```scala
// ModuleRegistryImpl.scala:22-25

def getModule(name: String): IO[Option[Module.Uninitialized]] = IO.pure {
  modules.get(name).orElse {
    // Fallback: strip dag prefix
    val strippedName = name.split("\\.").lastOption.getOrElse(name)
    modules.get(strippedName)
  }
}
```

For each module lookup:
1. Primary lookup in map
2. If miss: string split operation
3. Extract last element
4. Secondary lookup

### Impact

| DAG Size | Lookups | String Ops (worst case) |
|----------|---------|-------------------------|
| 10 modules | 10 | 10 splits |
| 50 modules | 50 | 50 splits |
| 100 modules | 100 | 100 splits |

While individual operations are fast (~1μs), they add up and create unnecessary garbage.

---

## Proposed Solution

Pre-compute all name variants at registration time, not lookup time.

### Implementation

#### Step 1: Multi-Key Registry

```scala
// ModuleRegistryImpl.scala - Optimized

class ModuleRegistryImpl extends ModuleRegistry {

  // Primary registry: canonical name → module
  private val modules: mutable.Map[String, Module.Uninitialized] =
    mutable.Map.empty

  // Fast lookup index: all possible names → canonical name
  private val nameIndex: mutable.Map[String, String] =
    mutable.Map.empty

  override def register(module: Module.Uninitialized): IO[Unit] = IO.delay {
    val canonicalName = module.spec.name

    // Store module
    modules.put(canonicalName, module)

    // Index all name variants
    indexNameVariants(canonicalName)
  }

  private def indexNameVariants(name: String): Unit = {
    // Full name
    nameIndex.put(name, name)

    // Without dag prefix: "mydag.Uppercase" → "Uppercase"
    if (name.contains(".")) {
      val shortName = name.split("\\.").last
      // Only add if no conflict
      if (!nameIndex.contains(shortName)) {
        nameIndex.put(shortName, name)
      }
    }

    // Lowercase variant (optional, for case-insensitive lookup)
    // nameIndex.put(name.toLowerCase, name)
  }

  override def getModule(name: String): IO[Option[Module.Uninitialized]] = IO.pure {
    // Single lookup via index
    nameIndex.get(name).flatMap(modules.get)
  }
}
```

#### Step 2: Batch Registration

For registering multiple modules efficiently:

```scala
def registerAll(moduleList: List[Module.Uninitialized]): IO[Unit] = IO.delay {
  moduleList.foreach { module =>
    val canonicalName = module.spec.name
    modules.put(canonicalName, module)
    indexNameVariants(canonicalName)
  }
}
```

#### Step 3: Conflict Detection

Handle name collisions gracefully:

```scala
private def indexNameVariants(name: String): Unit = {
  // Full name always indexed
  nameIndex.put(name, name)

  if (name.contains(".")) {
    val shortName = name.split("\\.").last

    nameIndex.get(shortName) match {
      case None =>
        // No conflict, add short name
        nameIndex.put(shortName, name)

      case Some(existing) if existing == name =>
        // Same module, no action needed
        ()

      case Some(existing) =>
        // Conflict! Log warning and skip short name indexing
        logger.warn(
          s"Module name conflict: '$shortName' could refer to '$name' or '$existing'. " +
          s"Use fully qualified names to avoid ambiguity."
        )
    }
  }
}
```

---

## Lookup Comparison

### Before (String Operations on Every Lookup)

```scala
def getModule("mydag.Uppercase"):
  1. modules.get("mydag.Uppercase")  → Miss (module registered as "Uppercase")
  2. "mydag.Uppercase".split("\\.")  → Array("mydag", "Uppercase")
  3. .lastOption                      → Some("Uppercase")
  4. modules.get("Uppercase")         → Hit

  Total: 2 map lookups + 1 string split + array allocation
```

### After (Pre-computed Index)

```scala
def getModule("mydag.Uppercase"):
  1. nameIndex.get("mydag.Uppercase") → Some("Uppercase")
  2. modules.get("Uppercase")          → Hit

  Total: 2 map lookups, no string operations
```

---

## Memory Trade-off

| Approach | Memory per Module | Lookup Speed |
|----------|-------------------|--------------|
| Current | ~200 bytes | O(1) + string ops |
| Indexed | ~400 bytes | O(1) pure |

The extra ~200 bytes per module for indexing is negligible (100 modules = 20KB).

---

## Advanced: Trie-Based Lookup

For very large registries (1000+ modules), use a trie:

```scala
import scala.collection.mutable

class TrieRegistry {
  private case class TrieNode(
    children: mutable.Map[Char, TrieNode] = mutable.Map.empty,
    var module: Option[Module.Uninitialized] = None
  )

  private val root = TrieNode()

  def register(name: String, module: Module.Uninitialized): Unit = {
    var node = root
    for (char <- name) {
      node = node.children.getOrElseUpdate(char, TrieNode())
    }
    node.module = Some(module)
  }

  def lookup(name: String): Option[Module.Uninitialized] = {
    var node = root
    for (char <- name) {
      node.children.get(char) match {
        case Some(child) => node = child
        case None => return None
      }
    }
    node.module
  }

  // Prefix search for autocomplete
  def findByPrefix(prefix: String): List[Module.Uninitialized] = {
    var node = root
    for (char <- prefix) {
      node.children.get(char) match {
        case Some(child) => node = child
        case None => return Nil
      }
    }
    collectAll(node)
  }

  private def collectAll(node: TrieNode): List[Module.Uninitialized] = {
    node.module.toList ++ node.children.values.flatMap(collectAll)
  }
}
```

**Benefits:**
- O(k) lookup where k = name length (not registry size)
- Efficient prefix search for LSP autocomplete
- Natural support for hierarchical names (dag.module.submodule)

---

## LSP Integration

The optimized registry benefits LSP autocomplete:

```scala
// ConstellationLanguageServer.scala

def getCompletions(prefix: String): List[CompletionItem] = {
  // With trie: efficient prefix search
  registry.findByPrefix(prefix).map { module =>
    CompletionItem(
      label = module.spec.name,
      kind = CompletionItemKind.Function,
      detail = module.spec.description
    )
  }
}
```

---

## Benchmarks

### Test Scenario

```scala
// Registry with 100 modules
val registry = new ModuleRegistryImpl()
(1 to 100).foreach(i => registry.register(createModule(s"dag$i.Module$i")))

// 10,000 lookups
val current = benchmark {
  (1 to 10000).foreach(i => currentRegistry.getModule(s"dag${i % 100}.Module${i % 100}"))
}

val optimized = benchmark {
  (1 to 10000).foreach(i => optimizedRegistry.getModule(s"dag${i % 100}.Module${i % 100}"))
}
```

### Expected Results

| Metric | Current | Optimized | Improvement |
|--------|---------|-----------|-------------|
| Lookup time | ~500ns | ~100ns | 5x faster |
| Allocations | 1 array/lookup | 0 | 100% reduction |
| GC impact | Minor | None | Cleaner |

---

## Implementation Checklist

- [ ] Add `nameIndex` map to `ModuleRegistryImpl`
- [ ] Update `register` to index name variants
- [ ] Update `getModule` to use index-based lookup
- [ ] Add conflict detection and logging
- [ ] Optional: Implement trie-based registry for large deployments
- [ ] Update LSP to use prefix search
- [ ] Add unit tests for name resolution

---

## Files to Modify

| File | Changes |
|------|---------|
| `modules/runtime/.../impl/ModuleRegistryImpl.scala` | Add name indexing |
| `modules/runtime/.../ModuleRegistry.scala` | Optional: add `findByPrefix` |
| `modules/lang-lsp/.../ConstellationLanguageServer.scala` | Use prefix search |

---

## Related Optimizations

- [Compilation Caching](./01-compilation-caching.md) - Cached DAGs reduce lookups
- [Module Initialization Pooling](./02-module-initialization-pooling.md) - Cached templates reduce repeated lookups
