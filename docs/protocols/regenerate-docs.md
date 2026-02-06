# Protocol: Regenerate Documentation

> How to regenerate documentation and fix common issues.

---

## When to Use

Apply this protocol when:
- CI fails with "Documentation is stale"
- Adding new types that should appear in generated docs
- Fixing broken ethos invariant references
- After refactoring that moves/renames symbols

---

## Procedure

### Step 1: Compile All Modules

Generated docs are extracted from compiled TASTy files:

```bash
sbt compile
```

All modules must compile successfully before extraction.

### Step 2: Regenerate Documentation

```bash
sbt "docGenerator/runMain io.constellation.docgen.GenerateDocs"
```

Expected output:
```
Generating Scala documentation catalog...
Classpath entries: 8
Extracted 531 types
  Generated: io.constellation.md (155 types)
  Generated: io.constellation.runtime.md (45 types)
  ...
Done. Output: docs/generated
```

### Step 3: Verify Freshness

```bash
sbt "docGenerator/runMain io.constellation.docgen.FreshnessChecker"
```

Expected output:
```
Documentation Freshness Report
========================================
Total docs: 22
  Fresh: 22
  Stale: 0
  Invalid: 0

All documentation is up to date.
```

### Step 4: Verify Ethos Invariants

```bash
sbt "docGenerator/runMain io.constellation.docgen.EthosVerifier"
```

Expected output:
```
Ethos Verification Report
========================================
Total invariants: 40
Valid: 40
Coverage: 100.0%

All invariants verified.
```

### Step 5: Commit Changes

```bash
git add docs/generated/
git commit -m "docs: regenerate API documentation catalogs"
```

---

## Troubleshooting

### Problem: "Stale: N" in Freshness Report

**Symptom:**
```
Total docs: 22
  Fresh: 20
  Stale: 2
```

**Cause:** Source code changed but generated docs weren't regenerated.

**Fix:** Run Steps 1-2 above, then commit the updated docs.

---

### Problem: "Broken implementation ref" in Ethos Verifier

**Symptom:**
```
Broken implementation ref: 1
  - docs/components/runtime/ETHOS.md
    Invariant: "Cache keys include all inputs"
    Reference: modules/runtime/.../CacheExecutor.scala#computeCacheKey
    Reason: Symbol 'computeCacheKey' not found in file
```

**Cause:** Symbol was renamed or moved.

**Fix:**
1. Find the new symbol name:
   ```bash
   grep -n "def.*cache\|Cache" modules/runtime/src/main/scala/.../CacheExecutor.scala
   ```
2. Update the reference in the ETHOS file
3. Re-run EthosVerifier

---

### Problem: "Missing test ref" in Ethos Verifier

**Symptom:**
```
Missing test ref: 1
  - docs/components/core/ETHOS.md
    Invariant: "Type coercion is explicit"
```

**Cause:** Invariant doesn't have a test reference.

**Fix:**
1. Find a test that validates the invariant:
   ```bash
   grep -rn "coercion\|coerce" modules/core/src/test/scala/
   ```
2. Add the test reference to the invariant table
3. Re-run EthosVerifier

---

### Problem: New types not appearing in generated docs

**Symptom:** Added a new class but it doesn't appear in `docs/generated/*.md`.

**Cause:** Either:
1. Module not compiled
2. Package not in target list
3. Type is private/internal

**Fix:**
1. Ensure module compiles: `sbt compile`
2. Check `GenerateDocs.scala` includes the package:
   ```scala
   val targetPackages: List[String] = List(
     "io.constellation"  // Must be prefix of your package
   )
   ```
3. Verify type is public (not private/protected)

---

### Problem: Hash mismatch between platforms

**Symptom:** Docs are "Fresh" locally but "Stale" in CI.

**Cause:** Line ending or path separator differences.

**Fix:** This was fixed in the doc-generator. If it recurs:
1. Check `MarkdownWriter.computeHash` normalizes line endings
2. Check file sorting uses forward slashes
3. Regenerate on the same platform as CI

---

## CI Integration

The CI pipeline runs these checks automatically:

```yaml
# .github/workflows/ci.yml
- name: Check generated docs freshness
  run: sbt "docGenerator/runMain io.constellation.docgen.FreshnessChecker"

- name: Verify ethos invariant references
  run: sbt "docGenerator/runMain io.constellation.docgen.EthosVerifier"
```

If CI fails on these steps, follow this protocol to fix locally, then push.

---

## Quick Reference

| Task | Command |
|------|---------|
| Compile all | `sbt compile` |
| Regenerate docs | `sbt "docGenerator/runMain io.constellation.docgen.GenerateDocs"` |
| Check freshness | `sbt "docGenerator/runMain io.constellation.docgen.FreshnessChecker"` |
| Verify ethos | `sbt "docGenerator/runMain io.constellation.docgen.EthosVerifier"` |

---

## See Also

- [Add Component Docs Protocol](./add-component-docs.md) — Creating new component documentation
- [Semantic Mapping Protocol](./semantic-mapping.md) — Writing semantic mapping tables
- [RFC-019](../rfcs/rfc-019-generated-documentation.md) — Technical specification
