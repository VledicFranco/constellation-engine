# Protocol: Semantic Mapping

> How to write semantic mapping tables in component ETHOS files.

---

## When to Use

Apply this protocol when:
- Creating a new component ETHOS file
- Adding new Scala artifacts to an existing component
- Reviewing semantic mapping completeness

---

## Procedure

### Step 1: Identify Public API Surface

List Scala artifacts that are part of the component's public API:

```bash
# Find public types in a module
grep -r "^class \|^object \|^trait \|^case class \|^enum " \
  modules/<component>/src/main/scala/io/constellation/
```

**Include:**
- Entry point objects (e.g., `ModuleBuilder`, `ConstellationServer`)
- Core domain types (e.g., `CType`, `CValue`, `Module`)
- Configuration types (e.g., `RetryConfig`, `CacheConfig`)
- SPI traits (e.g., `CacheBackend`, `MetricsProvider`)

**Exclude:**
- Internal implementation classes (e.g., `*Impl`, `*Internal`)
- Private helpers
- Test utilities

### Step 2: Write Domain Meanings

For each artifact, write a one-line domain meaning:

| Pattern | Example |
|---------|---------|
| "X that does Y" | `RetryExecutor` — Executor that retries failed module calls |
| "Represents X" | `CValue` — Runtime representation of typed constellation data |
| "Factory for X" | `ModuleBuilder` — Factory for creating pipeline modules |
| "Configuration for X" | `CacheConfig` — Configuration for module result caching |

**Guidelines:**
- Use domain language, not implementation details
- Start with the artifact's role, not its mechanics
- Keep to one line (< 80 chars)

### Step 3: Format the Table

```markdown
## Semantic Mapping

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `EntryPoint` | Description of what it represents |
| `CoreType` | Description of domain concept |
| `ConfigType` | Description of configuration purpose |

For complete type signatures, see:
- [io.constellation.component](/docs/generated/io.constellation.component.md)
```

### Step 4: Link to Generated Catalog

Always include a reference to the generated documentation:

```markdown
For complete type signatures, see:
- [io.constellation.runtime](/docs/generated/io.constellation.runtime.md)
```

This separates semantic meaning (manual) from technical signatures (generated).

### Step 5: Verify Coverage

Check that key public types are mapped:

```bash
# Count public types in module
grep -c "^class \|^object \|^trait " modules/<component>/src/main/scala/**/*.scala

# Count entries in semantic mapping
grep -c "^\| \`" docs/components/<component>/ETHOS.md
```

Aim for >80% coverage of public API types.

---

## Quality Checklist

- [ ] All entry point objects are mapped
- [ ] Domain types use business language, not technical jargon
- [ ] Each description is one line
- [ ] Generated catalog is linked
- [ ] No internal/private types included

---

## Examples

### Good Semantic Mapping

```markdown
| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `ModuleBuilder` | Factory for creating pipeline modules with metadata and implementation |
| `Module[I, O]` | Executable unit in the pipeline DAG with typed input/output |
| `FunctionSignature` | Module metadata exposed to the compiler for type checking |
```

### Poor Semantic Mapping

```markdown
| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `ModuleBuilder` | A class that builds modules |  # Too vague
| `Module[I, O]` | Generic module with type parameters I and O |  # Technical, not domain
| `FunctionSignature` | Contains name, inputType, outputType fields |  # Implementation details
```

---

## See Also

- [Component ETHOS Template](../components/runtime/ETHOS.md) — Example of complete ETHOS file
- [Generated Catalogs](../generated/) — Auto-extracted type signatures
