# Protocol: Semantic Mapping

> How to write semantic mapping tables in component ETHOS files.

---

## Goal

Create a semantic mapping that bridges Scala type signatures to domain meaning.

---

## Preconditions

- [ ] Generated catalog exists (`docs/generated/*.md`)
- [ ] Component ETHOS.md exists
- [ ] Understanding of what the Scala types represent

---

## Steps

### 1. Identify Public API Surface

**Include:** Entry points, core domain types, configuration types, SPI traits.

**Exclude:** `*Impl` classes, private helpers, test utilities.

```bash
grep -r "^class \|^object \|^trait \|^case class " \
  modules/<component>/src/main/scala/io/constellation/
```

### 2. Write Domain Meanings

One line per artifact using domain language:

| Pattern | Example |
|---------|---------|
| "X that does Y" | `RetryExecutor` — Executor that retries failed calls |
| "Represents X" | `CValue` — Runtime representation of typed data |
| "Factory for X" | `ModuleBuilder` — Factory for creating modules |

### 3. Format and Link

```markdown
## Semantic Mapping

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `EntryPoint` | What it represents in domain terms |

For signatures, see [generated catalog](/docs/generated/io.constellation.X.md).
```

### 4. Verify Coverage

```bash
# Count entries vs public types
grep -c "^\| \`" docs/components/<component>/ETHOS.md
```

Aim for >80% coverage of public API types.

---

## Verification

- [ ] All entry point objects mapped
- [ ] Descriptions use domain language (not technical jargon)
- [ ] Each description is one line
- [ ] Generated catalog linked
- [ ] No internal/private types included

---

## See Also

- [Runtime ETHOS](../components/runtime/ETHOS.md) — Complete example
- [Generated Catalogs](../generated/) — Auto-extracted signatures
