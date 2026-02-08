# Protocol: Add Component Documentation

> How to create documentation for a new component or module.

---

## When to Use

Apply this protocol when:
- Adding a new module to `modules/`
- Creating documentation for an undocumented component
- Splitting a large component into sub-components

---

## Procedure

### Step 1: Create Component Directory

```bash
mkdir -p organon/components/<component-name>
```

### Step 2: Create ETHOS.md

Create the ETHOS file with this structure:

```markdown
# <Component Name> Ethos

> Normative constraints for the <component> layer.

---

## Identity

- **IS:** <What this component is — one line>
- **IS NOT:** <What this component is not — boundaries>

---

## Semantic Mapping

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `MainType` | Description |
| `ConfigType` | Description |

For complete type signatures, see:
- [io.constellation.<pkg>](/organon/generated/io.constellation.<pkg>.md)

---

## Invariants

### 1. <Invariant Name>

<Description of what must always be true>

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/<component>/src/main/scala/.../File.scala#symbolName` |
| Test | `modules/<component>/src/test/scala/.../FileSpec.scala#test name` |

---

## Principles (Prioritized)

1. **<Principle 1>** — <Brief explanation>
2. **<Principle 2>** — <Brief explanation>

---

## Decision Heuristics

- When <situation>, prefer <approach>
- When uncertain about <X>, choose <Y>

---

## Out of Scope

- <What this component does NOT handle> (see [other-component](../other/))

---

## Implements Features

| Feature | Artifacts |
|---------|-----------|
| [Feature Name](../../features/feature/) | `Artifact1`, `Artifact2` |
```

### Step 3: Add Invariants with References

For each behavioral constraint:

1. **Find the implementation:**
   ```bash
   grep -n "def methodName\|class ClassName" modules/<component>/src/main/scala/**/*.scala
   ```

2. **Find the test:**
   ```bash
   grep -n "should\|test\|it \"" modules/<component>/src/test/scala/**/*.scala
   ```

3. **Format the reference:**
   ```markdown
   | Implementation | `path/to/File.scala#symbolName` |
   | Test | `path/to/FileSpec.scala#test description substring` |
   ```

### Step 4: Update Source Mapping (if new package)

If the component introduces a new package, update `GenerateDocs.scala`:

```scala
// modules/doc-generator/src/main/scala/.../GenerateDocs.scala
val sourceMapping: Map[String, String] = Map(
  // ... existing mappings ...
  "io.constellation.<newpkg>" -> "modules/<component>/src/main/scala/io/constellation"
)
```

### Step 5: Regenerate Documentation

```bash
sbt "docGenerator/runMain io.constellation.docgen.GenerateDocs"
```

### Step 6: Verify Ethos References

```bash
sbt "docGenerator/runMain io.constellation.docgen.EthosVerifier"
```

Expected output:
```
All invariants verified.
Coverage: 100.0%
```

If verification fails, fix broken references before committing.

### Step 7: Update Navigation

Add the component to `organon/components/README.md` if it exists, or create one:

```markdown
## Components

| Component | Description |
|-----------|-------------|
| [core](./core/) | Type system and value representation |
| [<new-component>](./<new-component>/) | <Brief description> |
```

---

## Verification Checklist

- [ ] `organon/components/<name>/ETHOS.md` exists
- [ ] Identity section defines IS and IS NOT
- [ ] Semantic mapping covers public API (>80%)
- [ ] All invariants have implementation + test references
- [ ] `EthosVerifier` reports 100% coverage
- [ ] Generated docs include the new package
- [ ] Navigation updated

---

## Common Issues

### "Symbol not found" in EthosVerifier

The symbol name doesn't exist in the referenced file.

**Fix:** Use `grep` to find the exact symbol name:
```bash
grep -n "keyword" modules/<component>/src/main/scala/.../File.scala
```

### Generated docs missing new package

The source mapping doesn't include the new package prefix.

**Fix:** Update `sourceMapping` in `GenerateDocs.scala` and regenerate.

### Invariant has no test

Some invariants are enforced by type system or compiler, not runtime tests.

**Fix:** Reference the type definition or compiler check instead:
```markdown
| Test | `modules/lang-compiler/.../TypeChecker.scala#validateType` |
```

---

## See Also

- [Semantic Mapping Protocol](./semantic-mapping.md) — How to write semantic mappings
- [Regenerate Docs Protocol](./regenerate-docs.md) — How to regenerate documentation
- [Runtime ETHOS](../components/runtime/ETHOS.md) — Example component ETHOS
