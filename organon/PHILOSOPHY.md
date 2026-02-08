# Documentation Philosophy

> Part of the [Documentation Organon](./README.md#organon-guiding-documents). See also: [ETHOS.md](./ETHOS.md)
>
> Why this documentation is structured the way it is.

---

## The Problem

Documentation serves multiple audiences with conflicting needs:

| Audience | Needs | Optimization |
|----------|-------|--------------|
| LLMs/Agents | Token efficiency, navigable structure, factual density | Small files, depth-first trees |
| Humans evaluating | Value proposition, quick understanding | Narrative flow, visuals |
| Humans learning | Progressive disclosure, examples | Tutorials, guides |
| Contributors | Accuracy, maintainability | Single source of truth |

A single documentation structure cannot serve all audiences well. Optimizing for one degrades the experience for others.

---

## The Bet

We maintain **two documentation surfaces**:

| Surface | Location | Audience | Optimization |
|---------|----------|----------|--------------|
| LLM docs | `docs/` | LLMs, agents, tools | Navigable tree, small files, factual |
| Human docs | `website/docs/` | Humans | Narrative, visual, progressive |

Content is **semantically equivalent** but **formatted differently**. The same feature is documented in both places, but structured for its audience.

---

## Three-Layer Documentation Model

Documentation exists in three layers, each with a distinct purpose:

```
┌─────────────────────────────────────────────────────────────┐
│                    ORGANON (Manual)                         │
│  Philosophy + Ethos + Semantic Mapping                      │
│  "What it means, why it exists, how to use it"              │
├─────────────────────────────────────────────────────────────┤
│                GENERATED CATALOG (Auto)                     │
│  docs/generated/*.md — TASTy-extracted signatures           │
│  "What exists in the codebase"                              │
├─────────────────────────────────────────────────────────────┤
│                    CODE (Source)                            │
│  modules/*/src/main/scala/**/*.scala                        │
│  "The truth"                                                │
└─────────────────────────────────────────────────────────────┘
```

| Layer | Content | Maintained By | Changes When |
|-------|---------|---------------|--------------|
| Code | Implementation | Developers | Features change |
| Generated | Type signatures, method lists | `make generate-docs` | Code changes |
| Organon | Domain meaning, invariants | Developers | Understanding changes |

**Key insight:** Generated catalogs answer "what exists?" Organon answers "what does it mean?" The semantic mapping in component ETHOS files bridges the two.

---

## Design Decisions

### 1. Feature-Driven Organization

Documentation is organized by **what the system does** (features), not **how it's built** (components).

```
# YES: Feature-driven (what can I do?)
organon/features/resilience/caching.md

# Secondary: Component reference (where is it implemented?)
organon/components/runtime/README.md
```

Users ask "how do I add caching?" not "what does the runtime module do?" Features are the primary navigation; components are cross-referenced.

### 2. Philosophy/Ethos/Protocol Pattern

Each domain has three artifact types:

| Artifact | Purpose | Example |
|----------|---------|---------|
| Philosophy | Explains *why* (for understanding) | "Why is resilience declarative?" |
| Ethos | Prescribes *what* (for behavioral consistency) | "Resilience options must validate at compile time" |
| Protocol | Specifies *how* (for execution) | "Steps to add a new resilience option" |

This pattern repeats at every level: root docs, each feature, each component.

For documentation-specific protocols, see [protocols/](./protocols/).

### 3. README as Router

Every directory has a README.md that serves as a navigation node:

```markdown
# Category Name

Brief summary.

## Contents

| Path | Description |
|------|-------------|
| [child-a/](./child-a/) | What child-a covers |
| [child-b.md](./child-b.md) | What child-b covers |
```

LLMs navigate by reading READMEs to decide which child to explore, loading only what's needed.

### 4. Component Cross-References

Feature docs include "Components Involved" tables linking to implementation:

```markdown
## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| runtime | Cache execution | CacheExecutor.scala |
| compiler | Option validation | OptionValidator.scala |
```

This bridges "what it does" to "where it's implemented" without duplicating content.

### 5. Small, Focused Files

| File Type | Target Lines | Rationale |
|-----------|--------------|-----------|
| README (router) | 50-100 | Quick navigation decisions |
| Content file | 100-200 | Complete topic, token-efficient |
| Philosophy/Ethos | 100-150 | Focused principles |

If a file exceeds 200 lines, split it.

### 6. Semantic Mapping in Component ETHOS

Component ETHOS files include a **Semantic Mapping** section that connects generated catalog types to domain meaning:

```markdown
## Semantic Mapping

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `ModuleBuilder` | Fluent API for creating typed modules |
| `CancellableExecution` | Handle for in-flight execution |

For complete signatures, see [generated catalog](/organon/generated/io.constellation.md).
```

This bridges the gap between "what exists" (generated) and "what it means" (organon).

### 7. Invariants with References

Component ETHOS files document invariants with links to implementation and tests:

```markdown
### 1. Modules are pure functions

| Aspect | Reference |
|--------|-----------|
| Implementation | `path/to/Module.scala#methodName` |
| Test | `path/to/ModuleTest.scala#test name` |
```

The ethos verifier (`make verify-ethos`) checks that these references are valid.

---

## Trade-Offs

| Decision | Benefit | Cost |
|----------|---------|------|
| Two surfaces | Optimized for each audience | Maintenance burden |
| Feature-driven | Matches user mental model | Harder to find implementation details |
| Philosophy/ethos pattern | Behavioral consistency | More files to maintain |
| Small files | Token-efficient | More navigation hops |

---

## Relationship to Other Docs

| Location | Purpose |
|----------|---------|
| `docs/` (this) | LLM-optimized documentation |
| `website/docs/` | Human-optimized documentation |
| `website/PHILOSOPHY.md` | Why human docs are structured that way |
| `website/ETHOS.md` | Constraints for human docs |
| `../ethos/` | Portable methodology (not Constellation-specific) |
| Repo root `PHILOSOPHY.md` | Constellation product philosophy |
| Repo root `ETHOS.md` | Constellation product ethos |
