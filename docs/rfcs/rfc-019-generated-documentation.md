# RFC-019: Generated Documentation and Organon Verification

> Automated Scala catalog generation and verification against organon constraints.

---

## Status

- **Status:** Implemented
- **Created:** 2026-02-06
- **Implemented:** 2026-02-06
- **Author:** Claude + Human

---

## Summary

This RFC proposes:
1. **Scala catalog generation** — Compiler plugin extracts type/method signatures to markdown
2. **Organon verification** — CI verifies ethos invariants have implementation and test references
3. **Freshness tracking** — Hash-based staleness detection for generated docs

The generator is **Scala-generic**. Constellation-specific semantics live in the organon via semantic mapping tables.

---

## Layer Model

```
┌─────────────────────────────────────────────────────────────┐
│                    ORGANON (Manual)                         │
│   Philosophy, Ethos, Semantic Mappings                      │
│   Defines: What it MEANS, why, how to use                   │
└─────────────────────────────────────────────────────────────┘
                              ↑ interprets
┌─────────────────────────────────────────────────────────────┐
│               GENERATED CATALOG (Automated)                 │
│   Per-package Scala type/method signatures                  │
│   Defines: What EXISTS in code                              │
└─────────────────────────────────────────────────────────────┘
                              ↑ extracts
┌─────────────────────────────────────────────────────────────┐
│                    CODE (Source of Truth)                   │
│   Classes, traits, objects, methods                         │
│   Defines: What IS                                          │
└─────────────────────────────────────────────────────────────┘
```

**Separation of concerns:**

| Layer | Contains | Authored by |
|-------|----------|-------------|
| Organon | Why, what it means, constraints | Human/LLM |
| Generated | What exists (signatures, types) | Compiler |
| Code | Implementation | Human/LLM |

---

## Part 1: Scala Catalog Generation

### 1.1 Extraction Mechanism

**Compiler plugin + sbt task** — Extracts type info during compilation, writes docs via sbt.

The extraction happens in two stages:

1. **Compiler plugin** collects type information during compilation → writes JSON to `target/`
2. **sbt task** reads JSON → generates markdown to `docs/generated/`

```scala
// modules/doc-generator/src/main/scala/DocCompilerPlugin.scala
class DocCompilerPlugin extends PluginPhase:
  override def phaseName = "doc-extractor"

  override def runOn(units: List[CompilationUnit])(using Context): List[CompilationUnit] =
    val catalog = units.flatMap(extractTypes)
    val json = catalog.asJson
    Files.write(Paths.get("target/doc-catalog.json"), json.getBytes)
    units

  private def extractTypes(unit: CompilationUnit)(using Context): List[TypeInfo] =
    // Walk AST, extract classes, objects, methods
    ???
```

```scala
// modules/doc-generator/src/main/scala/GenerateDocs.scala
object GenerateDocs:
  def main(args: Array[String]): Unit =
    val catalog = readJson(Paths.get("target/doc-catalog.json"))
    val markdown = MarkdownWriter.generate(catalog)
    markdown.foreach { case (pkg, content) =>
      Files.write(Paths.get(s"docs/generated/$pkg.md"), content.getBytes)
    }
```

**Why not pure macros:** Scala 3 macros generate code, not files. A compiler plugin can observe the full AST and write artifacts.

### 1.2 What Gets Extracted

| Scala Construct | Extracted Info |
|-----------------|----------------|
| `class` / `trait` | Name, type parameters, parent types |
| `object` | Name, methods, fields |
| `case class` | Fields with types |
| `def` | Name, parameters, return type, scaladoc |
| `enum` | Cases with parameters |

### 1.3 Output Format

Per-package markdown files:

```markdown
<!-- GENERATED -->
<!-- Source: modules/runtime/src/main/scala/io/constellation/runtime/ -->
<!-- Hash: a3f2b1c4d5e6 -->
<!-- Generated: 2026-02-06T14:30:00Z -->

# io.constellation.runtime

## Objects

### ModuleBuilder

Builder for creating pipeline modules.

| Method | Signature | Description |
|--------|-----------|-------------|
| `metadata` | `(name: String, desc: String, major: Int, minor: Int): MetadataStep` | Set module metadata |
| `implementationPure` | `[I, O](f: I => O): BuildStep[I, O]` | Pure implementation |
| `implementation` | `[I, O](f: I => IO[O]): BuildStep[I, O]` | Effectful implementation |

## Case Classes

### Module[I, O]

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` | Module identifier |
| `signature` | `FunctionSignature` | Type metadata |
| `execute` | `I => IO[O]` | Execution function |

<!-- END GENERATED -->
```

### 1.4 File Structure

```
docs/
├── generated/                          # Auto-generated (committed)
│   ├── io.constellation.runtime.md
│   ├── io.constellation.TypeSystem.md
│   ├── io.constellation.compiler.md
│   └── ...
├── components/                         # Manual
│   └── runtime/
│       ├── ETHOS.md                    # Semantic mapping + invariants
│       └── README.md
└── features/                           # Manual
    └── resilience/
        └── ETHOS.md
```

### 1.5 Semantic Mapping (in Component ETHOS)

The organon interprets the raw catalog:

```markdown
# Runtime Ethos

## Semantic Mapping

| Scala Artifact | Constellation Meaning |
|----------------|----------------------|
| `ModuleBuilder` | Factory for creating pipeline modules |
| `Module[I, O]` | Executable unit in the pipeline DAG |
| `CValue` | Runtime representation of typed data |
| `FunctionSignature` | Module metadata exposed to compiler |

For raw signatures, see [generated catalog](/docs/generated/io.constellation.runtime.md).
```

---

## Part 2: Organon Verification

### 2.1 Invariant Format

Ethos invariants include implementation and test references:

```markdown
## Invariants

### 1. Cache keys include all inputs

All parameters affecting module output must be included in the cache key.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/runtime/src/main/scala/io/constellation/cache/CacheExecutor.scala#computeCacheKey` |
| Test | `modules/runtime/src/test/scala/io/constellation/cache/CacheExecutorSpec.scala#cache keys include all inputs` |

### 2. TTL required, max 24 hours

Cache TTL must be explicitly specified and cannot exceed 24 hours.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/runtime/src/main/scala/io/constellation/cache/CacheOption.scala#validate` |
| Test | `modules/runtime/src/test/scala/io/constellation/cache/CacheOptionSpec.scala#rejects TTL over 24 hours` |
```

**Reference format:** `path/to/file.scala#symbolName`

- Uses symbol names, not line numbers (line numbers break easily)
- Symbol can be function name, test name, or class name

### 2.2 Verification Tool

```scala
// modules/doc-generator/src/main/scala/EthosVerifier.scala
object EthosVerifier:
  case class Invariant(
    name: String,
    implementation: Reference,
    test: Reference
  )

  case class Reference(file: Path, symbol: String)

  def verify(ethosFile: Path, sourceRoot: Path): Report =
    val invariants = parseInvariants(ethosFile)

    invariants.map { inv =>
      val implExists = fileContainsSymbol(sourceRoot, inv.implementation)
      val testExists = fileContainsSymbol(sourceRoot, inv.test)
      InvariantStatus(inv, implExists, testExists)
    }

  private def fileContainsSymbol(root: Path, ref: Reference): Boolean =
    val file = root.resolve(ref.file)
    file.exists && Source.fromFile(file).mkString.contains(ref.symbol)
```

### 2.3 Verification Checks

| Check | Description |
|-------|-------------|
| Implementation exists | File exists and contains symbol |
| Test exists | Test file exists and contains test name |
| All invariants covered | Every invariant has both references |
| No orphan tests | Tests claiming invariant coverage are referenced |

### 2.4 CI Output

```
Organon Verification Report
===========================

Invariants: 14
  With implementation: 14 ✓
  With test: 12 ✗

Missing test references:
  - docs/features/resilience/ETHOS.md#3: "Fallback must be type-compatible"
  - docs/features/resilience/ETHOS.md#5: "Throttle respects burst"

Broken references:
  - docs/features/type-safety/ETHOS.md#2: CTypeValidator.scala#validate (file moved)

FAILED: 2 missing tests, 1 broken reference
```

---

## Part 3: Freshness Tracking

### 3.1 Source Hashing

Generated docs include metadata:

```markdown
<!-- GENERATED -->
<!-- Source: modules/runtime/src/main/scala/io/constellation/runtime/ -->
<!-- Hash: a3f2b1c4d5e6 -->
<!-- Generated: 2026-02-06T14:30:00Z -->
```

### 3.2 Freshness Check

```scala
object FreshnessChecker:
  def check(generatedDir: Path, sourceRoot: Path): Report =
    val docs = findGeneratedDocs(generatedDir)

    docs.map { doc =>
      val meta = parseMetadata(doc)
      val currentHash = computeHash(sourceRoot.resolve(meta.source))

      if currentHash == meta.hash then Fresh(doc)
      else Stale(doc, meta.hash, currentHash)
    }
```

### 3.3 CI Integration

```yaml
- name: Check doc freshness
  run: |
    sbt compile
    sbt docFreshness
    # Fails if any generated docs are stale
```

### 3.4 Commit Policy

Generated docs are **committed** (not gitignored):
- LLMs can read without building
- Version history preserved
- Works offline

---

## Implementation Phases

### Phase 1: Catalog Extraction

**Goal:** Compiler plugin + sbt task generates per-package markdown.

**Deliverables:**
- `modules/doc-generator` module
- `DocCompilerPlugin` compiler plugin
- `GenerateDocs` main class
- `docs/generated/*.md` output

**Commands:**
```bash
make generate-docs    # sbt docGenerator/run
```

**Files:**
```
modules/doc-generator/
├── src/main/scala/
│   ├── DocCompilerPlugin.scala
│   ├── GenerateDocs.scala
│   ├── MarkdownWriter.scala
│   └── model/
│       └── TypeInfo.scala
```

### Phase 2: Freshness Tracking

**Goal:** CI detects stale generated docs.

**Deliverables:**
- `FreshnessChecker` tool
- Hash metadata in generated docs

**Commands:**
```bash
make check-docs       # sbt "docGenerator/runMain FreshnessChecker"
```

### Phase 3: Invariant Verification

**Goal:** CI verifies ethos references exist.

**Deliverables:**
- `EthosVerifier` tool
- Invariant format protocol
- CI job with coverage reporting

**Commands:**
```bash
make verify-ethos     # sbt "docGenerator/runMain EthosVerifier"
```

### Phase 4: Semantic Mapping Protocol

**Goal:** Standard format for ethos → code mapping.

**Deliverables:**
- Protocol added to meta-organon (`ethos/protocols/semantic-mapping.md`)
- Example semantic mappings in component ETHOS files
- Documentation

---

## Dependencies

```
Phase 1 (extraction)
    │
    ├──→ Phase 2 (freshness)
    │
    └──→ Phase 3 (verification)
              │
              └──→ Phase 4 (protocol)
```

Phases 2 and 3 can run in parallel after Phase 1.

---

## Trade-offs

| Decision | Benefit | Cost |
|----------|---------|------|
| Compiler plugin | Full AST access, no runtime cost | Complex implementation |
| No annotations | No drift risk | Less explicit metadata |
| Scala-generic | Reusable, domain knowledge in organon | Requires semantic mapping |
| Committed generated docs | LLM-readable, versioned | PR noise |
| Symbol-based references | Stable across refactors | May miss renames |

---

## References

- [Meta-Organon](../../../ethos/) - Methodology for organon creation
- [Scala 3 Macros](https://docs.scala-lang.org/scala3/guides/macros/) - Metaprogramming docs
