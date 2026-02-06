# RFC-019: Generated Documentation and Organon Verification

> Automated documentation generation from code and verification against organon constraints.

---

## Status

- **Status:** Draft
- **Created:** 2026-02-06
- **Author:** Claude + Human

---

## Summary

This RFC proposes a system for:
1. Generating reference documentation from Scala code using metaprogramming
2. Verifying that code satisfies organon constraints via CI
3. Tracking documentation freshness to prevent drift

The goal is to make code the single source of truth for reference documentation while ensuring the organon remains the authority on behavioral constraints.

---

## Motivation

### Current Problems

| Problem | Impact |
|---------|--------|
| Manual doc updates after code changes | Docs drift out of sync |
| No verification that code matches ethos | Constraints exist on paper only |
| Duplicate information (code + docs) | Maintenance burden, divergence |
| No freshness tracking | Stale docs erode trust |

### Goals

1. **Generate** reference docs (module signatures, types, options) from code
2. **Verify** code satisfies organon invariants
3. **Track** freshness so stale docs are visible
4. **Preserve** manual content (philosophy, ethos, explanations)

---

## Design

### Layer Model

```
┌─────────────────────────────────────────────────────────────┐
│                    ORGANON (Manual)                         │
│   Philosophy, Ethos, Protocols                              │
│   Governs what SHOULD exist                                 │
└─────────────────────────────────────────────────────────────┘
                              ↑ verifies
┌─────────────────────────────────────────────────────────────┐
│               GENERATED DOCS (Automated)                    │
│   Module signatures, type catalogs, option tables           │
│   Describes what DOES exist                                 │
└─────────────────────────────────────────────────────────────┘
                              ↑ extracts
┌─────────────────────────────────────────────────────────────┐
│                    CODE (Source of Truth)                   │
│   ModuleBuilder, CType, validation logic                    │
│   Defines what EXISTS                                       │
└─────────────────────────────────────────────────────────────┘
```

---

## Part 1: Documentation Generation

### 1.1 What Gets Generated

| Source | Generated Target | Content |
|--------|------------------|---------|
| `ModuleBuilder.metadata()` | `docs/components/*/modules.md` | Module name, description, input/output types |
| `CType` subclasses | `docs/components/core/types.md` | Type hierarchy, field definitions |
| `StdLib.allSignatures` | `docs/stdlib/*.md` | Function signatures, descriptions |
| Resilience option validation | `docs/features/resilience/options.md` | Option names, types, defaults, constraints |
| Exception hierarchy | `docs/components/*/errors.md` | Error types, messages, causes |

### 1.2 Extraction Mechanism

**Option A: Compile-time macros (Scala 3)**

```scala
import scala.quoted.*

object DocExtractor:
  inline def extractModule[I, O](builder: ModuleBuilder[I, O]): ModuleDoc =
    ${ extractModuleImpl[I, O]('builder) }

  private def extractModuleImpl[I: Type, O: Type](
    builder: Expr[ModuleBuilder[I, O]]
  )(using Quotes): Expr[ModuleDoc] =
    import quotes.reflect.*

    // Extract type info using Mirror
    val inputFields = TypeRepr.of[I].typeSymbol.caseFields
    val outputFields = TypeRepr.of[O].typeSymbol.caseFields

    // Generate ModuleDoc at compile time
    '{ ModuleDoc(
      inputType = ${ Expr(Type.show[I]) },
      inputFields = ${ Expr(inputFields.map(_.name)) },
      outputType = ${ Expr(Type.show[O]) },
      outputFields = ${ Expr(outputFields.map(_.name)) }
    )}
```

**Option B: Runtime reflection + build plugin**

```scala
// sbt plugin that runs after compile
object DocGeneratorPlugin extends AutoPlugin {
  override def projectSettings = Seq(
    generateDocs := {
      val classloader = (Compile / fullClasspath).value
      val modules = discoverModules(classloader)
      val docs = modules.map(extractDocumentation)
      writeMarkdown(docs, baseDirectory.value / "docs")
    }
  )
}
```

**Option C: Annotation processing**

```scala
@GenerateDoc(
  feature = "resilience",
  category = "options"
)
case class CacheOption(
  @DocField("Time-to-live for cached results")
  ttl: Duration,

  @DocField("Cache backend identifier", default = "memory")
  backend: Option[String]
)
```

**Recommendation:** Option A (compile-time macros) for type-safe extraction, with Option C annotations for additional metadata.

### 1.3 Output Format

Generated markdown includes metadata header:

```markdown
<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/runtime/src/main/scala/io/constellation/modules/TextModules.scala -->
<!-- Hash: a3f2b1c4d5e6f7 -->
<!-- Generated: 2026-02-06T14:30:00Z -->

# Text Modules

| Module | Description | Input | Output |
|--------|-------------|-------|--------|
| Uppercase | Converts text to uppercase | `TextInput` | `TextOutput` |
| Lowercase | Converts text to lowercase | `TextInput` | `TextOutput` |
| Trim | Removes leading/trailing whitespace | `TextInput` | `TextOutput` |

<!-- END GENERATED -->

## Usage Notes

(Manual content below the generated section is preserved)
```

### 1.4 Build Integration

```
sbt compile
    ↓
sbt generateDocs
    ↓
docs/generated/*.md updated
    ↓
CI checks: git diff --exit-code docs/generated/
    ↓
Fail if generated docs weren't committed
```

---

## Part 2: Organon Verification

### 2.1 Ethos Assertions in Code

Annotate code with organon references:

```scala
object CacheExecutor {
  /**
   * @ethos-invariant Cache keys include all inputs
   * @ethos-source docs/features/resilience/ETHOS.md
   */
  def computeCacheKey(moduleName: String, inputs: CValue*): String = {
    val inputHash = inputs.map(_.hashCode).mkString("-")
    s"$moduleName:$inputHash"
  }

  /**
   * @ethos-invariant TTL required, max 24 hours
   * @ethos-source docs/features/resilience/ETHOS.md
   */
  def validateTtl(ttl: Duration): Either[String, Duration] = {
    if (ttl > 24.hours) Left("TTL exceeds maximum of 24 hours")
    else if (ttl <= Duration.Zero) Left("TTL must be positive")
    else Right(ttl)
  }
}
```

### 2.2 Verification Tool

```scala
object EthosVerifier {
  case class Assertion(
    invariant: String,
    source: String,
    location: SourceLocation
  )

  case class Invariant(
    text: String,
    source: String
  )

  def verify(codebase: Path, docsRoot: Path): VerificationResult = {
    // 1. Scan code for @ethos-invariant annotations
    val assertions = scanAssertions(codebase)

    // 2. Parse ETHOS.md files for invariants
    val invariants = parseEthosFiles(docsRoot)

    // 3. Match assertions to invariants
    val matched = assertions.map { assertion =>
      invariants.find(_.text.contains(assertion.invariant)) match {
        case Some(inv) => Matched(assertion, inv)
        case None => Unmatched(assertion)
      }
    }

    // 4. Find invariants without assertions (coverage)
    val uncovered = invariants.filterNot { inv =>
      assertions.exists(a => inv.text.contains(a.invariant))
    }

    VerificationResult(matched, uncovered)
  }
}
```

### 2.3 CI Integration

```yaml
# .github/workflows/organon-verify.yml
name: Organon Verification

on: [push, pull_request]

jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Verify ethos assertions
        run: sbt "ethosVerify"

      - name: Check coverage
        run: |
          COVERAGE=$(sbt -batch "ethosVerify --coverage" | tail -1)
          if [ "$COVERAGE" -lt 80 ]; then
            echo "Ethos coverage below 80%: $COVERAGE%"
            exit 1
          fi
```

### 2.4 Verification Report

```
Organon Verification Report
===========================

Matched Assertions: 12
Unmatched Assertions: 1
  - "Rate limit per client IP" in RateLimitMiddleware.scala:45
    No matching invariant found in docs/features/resilience/ETHOS.md

Invariant Coverage: 85% (12/14)
Uncovered Invariants:
  - "Cache miss executes normally" (docs/features/resilience/ETHOS.md)
  - "Fallback must be type-compatible" (docs/features/resilience/ETHOS.md)

FAILED: Unmatched assertions found
```

---

## Part 3: Freshness Tracking

### 3.1 Source Hashing

Each generated doc tracks its source:

```markdown
<!-- Source: modules/runtime/src/.../CacheExecutor.scala -->
<!-- Hash: a3f2b1c4d5e6f7 -->
```

### 3.2 Freshness Check

```scala
object FreshnessChecker {
  def check(docsRoot: Path, sourceRoot: Path): FreshnessReport = {
    val generatedDocs = findGeneratedDocs(docsRoot)

    generatedDocs.map { doc =>
      val metadata = parseMetadata(doc)
      val sourceFile = sourceRoot.resolve(metadata.source)
      val currentHash = computeHash(sourceFile)

      if (currentHash == metadata.hash) Fresh(doc)
      else Stale(doc, metadata.hash, currentHash)
    }
  }
}
```

### 3.3 CI Integration

```yaml
- name: Check doc freshness
  run: |
    sbt "docFreshness"
    # Fails if any generated docs are stale
```

### 3.4 Developer Workflow

```
1. Developer modifies source file
2. Pre-commit hook runs: sbt generateDocs
3. Git stages updated docs
4. CI verifies docs match source
```

---

## File Structure

```
modules/
├── doc-generator/                    # New module
│   └── src/main/scala/
│       ├── DocExtractor.scala        # Compile-time extraction
│       ├── EthosVerifier.scala       # Assertion verification
│       ├── FreshnessChecker.scala    # Staleness detection
│       └── MarkdownWriter.scala      # Output formatting

docs/
├── generated/                        # Auto-generated (gitignored patterns)
│   ├── modules.md
│   ├── types.md
│   └── stdlib.md
├── features/
│   └── resilience/
│       ├── ETHOS.md                  # Manual (invariants verified)
│       ├── options.md                # Mixed (generated table + manual notes)
│       └── PHILOSOPHY.md             # Manual
```

---

## Annotations Reference

| Annotation | Location | Purpose |
|------------|----------|---------|
| `@GenerateDoc` | Case class | Include in generated docs |
| `@DocField` | Field | Field description and default |
| `@ethos-invariant` | Scaladoc | Link code to ethos constraint |
| `@ethos-source` | Scaladoc | Path to governing ethos file |
| `@no-generate` | Any | Exclude from generation |

---

## Migration Path

### Phase 1: Foundation
1. Create `doc-generator` module
2. Implement `DocExtractor` for ModuleBuilder
3. Generate `docs/generated/modules.md`
4. Add to CI

### Phase 2: Verification
1. Add `@ethos-invariant` annotations to critical code
2. Implement `EthosVerifier`
3. Add coverage target (start at 50%, increase to 80%)
4. Add to CI

### Phase 3: Expansion
1. Extend extraction to CType, StdLib, resilience options
2. Add freshness tracking
3. Add pre-commit hook
4. Document workflow in CONTRIBUTING.md

### Phase 4: Refinement
1. Add IDE support (show ethos coverage in gutter)
2. Add dashboard widget for organon health
3. Generate coverage badge for README

---

## Trade-offs

| Decision | Benefit | Cost |
|----------|---------|------|
| Compile-time macros | Type-safe, fast | Complex implementation |
| Annotation-based | Explicit, flexible | More boilerplate |
| Separate generated dir | Clear separation | Navigation overhead |
| 80% coverage target | Practical goal | Some invariants unchecked |

---

## Open Questions

1. **Granularity:** Should we generate docs per-module or per-file?
2. **Mixed content:** How to preserve manual notes in generated files?
3. **Versioning:** Should generated docs be committed or gitignored?
4. **IDE integration:** How to surface ethos coverage in editor?

---

## References

- [Meta-Organon](../../ethos/) - The methodology this implements
- [RFC-017](./rfc-017-v1-readiness.md) - V1 readiness (documentation phase)
- [Scala 3 Macros](https://docs.scala-lang.org/scala3/guides/macros/) - Metaprogramming reference
