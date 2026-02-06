# Documentation Philosophy for LLM-Assisted Development

> A methodology for maintaining consistency between code, LLM-optimized documentation, and human-facing documentation in projects where AI agents are active contributors.

---

## The Three-Layer Model

Software documentation exists at three distinct layers, each serving different consumers with different needs:

```
┌─────────────────────────────────────────────────────────────┐
│                    LAYER 1: CODE                            │
│                                                             │
│   Inline comments, docstrings, type signatures, tests       │
│   Consumer: Compilers, IDEs, developers reading source      │
│   Truth value: AUTHORITATIVE - this is what actually runs   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                 LAYER 2: LLM DOCUMENTATION                  │
│                                                             │
│   Structured knowledge base optimized for AI navigation     │
│   Consumer: LLMs, AI agents, automated tools                │
│   Truth value: DERIVED - should reflect code accurately     │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                LAYER 3: HUMAN DOCUMENTATION                 │
│                                                             │
│   Narratives, tutorials, marketing, conceptual guides       │
│   Consumer: Humans evaluating, learning, or using project   │
│   Truth value: INTERPRETED - conveys value and meaning      │
└─────────────────────────────────────────────────────────────┘
```

### Layer Purposes

| Layer | Primary Purpose | Secondary Purpose |
|-------|-----------------|-------------------|
| Code | Execute correctly | Explain implementation intent |
| LLM Docs | Enable accurate AI assistance | Serve as knowledge index |
| Human Docs | Communicate value proposition | Teach usage patterns |

---

## The Consistency Problem

These layers naturally diverge over time:

```
Day 0:    Code ═══ LLM Docs ═══ Human Docs    (aligned)

Day 30:   Code ─── LLM Docs ─── Human Docs    (minor drift)
              ↑         ↑            ↑
           changed   forgotten   still says
                                 old thing

Day 90:   Code ··· LLM Docs ··· Human Docs    (significant divergence)
              ↑         ↑            ↑
           v2.0    says v1.0    markets
                   behavior     features
                               that changed
```

**Consequences of divergence:**
- LLMs give incorrect advice based on stale docs
- Humans have wrong expectations
- Bug reports for "documented behavior" that code doesn't implement
- Trust erosion in both documentation and AI assistance

---

## Core Principles

### 1. Code is the Source of Truth

Documentation describes what code *does* or *should do*. When they conflict:
- If code is wrong → fix code
- If docs are wrong → fix docs
- Never leave the conflict unresolved

**Anti-pattern:** "The code does X but we document Y because Y is what we intended."

**Correct approach:** Fix the code to do Y, or update docs to describe X, then file an issue for the intended change.

### 2. Each Layer Has One Job

| Layer | Job | Not Its Job |
|-------|-----|-------------|
| Code comments | Explain *why* and *how* of implementation | Teach concepts, sell value |
| LLM docs | Enable efficient, accurate context building | Persuade, entertain, be comprehensive |
| Human docs | Help humans understand and decide | Be exhaustive, replace reading code |

**Anti-pattern:** LLM docs written like marketing copy.

**Anti-pattern:** Human docs that try to document every function.

### 3. Optimize for the Consumer

**LLMs need:**
- Small, focused files (token efficiency)
- Clear navigation structure (decision trees)
- Factual, unambiguous statements
- Explicit cross-references (not "see above")
- Version/freshness signals

**Humans need:**
- Narrative flow (story structure)
- Visual aids (diagrams, screenshots)
- Progressive disclosure (basics → advanced)
- Motivation before mechanics ("why" before "how")
- Real-world examples they can relate to

### 4. Link, Don't Duplicate

When the same fact must appear in multiple places:
- Choose one authoritative location
- Other locations link to it
- Or: generate from single source

**Anti-pattern:** Copy-pasting the same table into 5 files.

**Correct approach:** One file has the table, others say "See [reference](./path)".

### 5. Make Staleness Visible

Every document should signal its freshness:

```markdown
---
last_updated: 2026-02-05
applies_to: v0.4.x
source_of_truth: src/module/Feature.scala
---
```

Stale docs that look current are worse than missing docs.

---

## LLM Documentation Structure

### The Semantic Tree

Organize LLM docs as a navigable tree where:
- **Root** = project identity + navigation map
- **Branches** = major concept areas
- **Leaves** = specific, focused topics

```
docs/
├── README.md              # "What is this?" + top-level navigation
├── STRUCTURE.md           # Meta: how docs are organized (this philosophy)
│
├── concept-area-1/
│   ├── README.md          # Summary + child navigation
│   ├── specific-topic.md  # Focused content (100-200 lines)
│   └── another-topic.md
│
├── concept-area-2/
│   ├── README.md
│   └── sub-area/
│       ├── README.md
│       └── deep-topic.md
```

### README as Router

Every directory has a README.md that serves as a routing node:

```markdown
# Concept Area

Brief summary of what this area covers.

## Contents

| Path | Description |
|------|-------------|
| [topic-a.md](./topic-a.md) | One-line description |
| [topic-b.md](./topic-b.md) | One-line description |
| [sub-area/](./sub-area/) | One-line description of sub-area |

## Quick Reference

Key facts an LLM might need without descending further.
```

This enables LLMs to navigate efficiently:
1. Read root README → understand project, pick direction
2. Read area README → understand area, pick topic or go deeper
3. Read leaf file → get specific information needed

### Feature Map at Root

The root README should include a **feature map table** — a structured overview of all major capabilities with metadata that helps LLMs understand and navigate the system:

```markdown
## Core Features

| Feature | Description | Components | Level | Docs |
|---------|-------------|------------|-------|------|
| Feature A | One-line description | module1, module2 | Basic | [docs/area/](./area/) |
| Feature B | One-line description | module3 | Intermediate | [docs/other.md](./other.md) |
| Feature C | One-line description | module1, module4 | Advanced | [docs/deep/](./deep/) |
```

**Column purposes:**

| Column | Purpose |
|--------|---------|
| Feature | Scannable name for quick identification |
| Description | One sentence explaining what it does |
| Components | Which code modules implement this (navigation hint to source) |
| Level | Complexity indicator (Basic/Intermediate/Advanced) |
| Docs | Direct link to detailed documentation |

**Why this matters for LLMs:**

1. **Capability discovery** — LLM can scan what the system does without reading everything
2. **Component mapping** — Links features to code areas for investigation
3. **Complexity signal** — Helps LLM gauge how much context is needed
4. **Direct navigation** — Jumps to relevant docs without tree traversal

**Anti-patterns:**
- Feature names that are marketing-speak instead of technical terms
- Missing component mapping (LLM can't find relevant code)
- No complexity indicator (LLM doesn't know if it needs more context)
- Links to directories instead of specific files when a specific file exists

### Leaf File Format

```markdown
# Topic Name

> **Path**: `docs/area/topic.md`
> **Parent**: [area/](./README.md)
> **Related**: [other-topic.md](./other-topic.md)

Brief description (1-2 sentences).

## Section 1

Content with tables, code examples, factual statements.

## Section 2

More content. Cross-reference other files explicitly:
"For X, see [other-file.md](../other-area/other-file.md)."
```

### File Size Guidelines

| File Type | Target Lines | Rationale |
|-----------|--------------|-----------|
| Root README | 50-80 | Quick orientation |
| Area README | 80-150 | Navigation + summary |
| Leaf file | 100-200 | Focused, complete topic |

If a leaf exceeds 200 lines, consider splitting into sub-area.

---

## Human Documentation Structure

### Narrative Flow

Human docs should follow a story structure:

1. **Hook** — Why should I care? (problem statement, value prop)
2. **Context** — What do I need to know first? (prerequisites, concepts)
3. **Journey** — How do I do the thing? (tutorial, guide)
4. **Reference** — Where do I look things up? (API docs, tables)
5. **Troubleshooting** — What if it doesn't work? (FAQ, common errors)

### Progressive Disclosure

Organize by complexity level:

```
Getting Started/        # Level 1: "Hello World"
├── introduction.md
├── quick-start.md
└── first-project.md

Guides/                 # Level 2: Common tasks
├── basic-usage.md
├── configuration.md
└── common-patterns.md

Advanced/               # Level 3: Power users
├── architecture.md
├── extending.md
└── performance.md

Reference/              # Level 4: Look-up
├── api.md
├── configuration-options.md
└── error-codes.md
```

### Value-First Writing

Every page should answer "why should I care?" before "how does it work?":

```markdown
# Feature X

<!-- BAD: Jumps straight to mechanics -->
Feature X is configured by setting the `x_enabled` flag to true and
providing a `XConfig` object with the following fields...

<!-- GOOD: Establishes value first -->
Feature X reduces API latency by 40% by caching repeated calls.
Use it when your pipeline makes multiple calls to the same service
with identical parameters.

## When to Use

- High-latency external APIs
- Repeated lookups with same parameters
- Cost-sensitive API calls (pay-per-request)

## How It Works

Feature X caches results based on...
```

---

## Consistency Mechanisms

### 1. Generation from Code

Identify sections that can be auto-generated:

| Generated From | Generates |
|----------------|-----------|
| Type signatures | API reference tables |
| Function docstrings | Function documentation |
| Test cases | Example snippets |
| Error definitions | Error reference |
| Config schemas | Configuration docs |

**Implementation pattern:**

```
source-file.scala          docs/generated/api.md
        │                           ▲
        └──► generation-script ─────┘
```

Mark generated sections clearly:

```markdown
<!-- GENERATED FROM src/Api.scala - DO NOT EDIT -->
| Function | Signature | Description |
|----------|-----------|-------------|
...
<!-- END GENERATED -->
```

### 2. Doc Tests

Examples in documentation should be executable:

```markdown
```scala
// This example is validated by CI
val result = MyApi.process("input")
assert(result == "expected output")
```                                        ```
```

CI runs these examples; if code changes break them, build fails.

### 3. Freshness Tracking

Every doc file includes metadata:

```yaml
---
last_updated: 2026-02-05
source_version: 0.4.0
validates_against: src/Feature.scala
---
```

Tooling can flag docs where `source_version` < current version.

### 4. Cross-Reference Validation

Build process validates that:
- All internal links resolve
- Referenced code locations exist
- Version numbers are consistent

### 5. Ownership Model

Each doc file has an owner (person or team):

```yaml
---
owner: platform-team
reviewers: [alice, bob]
---
```

When related code changes, owners are notified to review docs.

---

## Anti-Patterns to Avoid

### In Code Comments

| Anti-Pattern | Problem | Instead |
|--------------|---------|---------|
| Commenting *what* | Redundant with code | Comment *why* |
| Stale TODOs | Never get done | Use issue tracker |
| Commented-out code | Confuses readers | Delete it (git remembers) |
| Novel in a docstring | Too long to read | Link to detailed docs |

### In LLM Docs

| Anti-Pattern | Problem | Instead |
|--------------|---------|---------|
| Marketing language | Wastes tokens, low signal | Factual statements |
| Huge files | Exceeds context, hard to navigate | Split into tree |
| Duplicate content | Diverges over time | Single source + links |
| Vague cross-refs | "See above", "elsewhere" | Explicit file paths |
| No navigation structure | LLM can't find things | README routers |

### In Human Docs

| Anti-Pattern | Problem | Instead |
|--------------|---------|---------|
| Starting with "how" | Reader doesn't know why to care | Start with "why" |
| Wall of text | Humans skim | Use headers, lists, tables |
| Assuming knowledge | Alienates beginners | Progressive disclosure |
| Only happy path | Users hit errors | Include troubleshooting |
| Outdated screenshots | Erodes trust | Automate or version clearly |

---

## Implementation Checklist

When setting up documentation for a new project:

### Layer 1: Code
- [ ] Establish docstring conventions (format, required fields)
- [ ] Configure doc extraction tooling
- [ ] Set up example/doc test infrastructure
- [ ] Define "code location" format for references

### Layer 2: LLM Docs
- [ ] Create `docs/` directory structure
- [ ] Write `docs/README.md` (root navigation)
- [ ] Write `docs/STRUCTURE.md` (or link to this philosophy)
- [ ] Create README.md for each major area
- [ ] Establish leaf file template
- [ ] Set up link validation in CI

### Layer 3: Human Docs
- [ ] Choose documentation platform (Docusaurus, MkDocs, etc.)
- [ ] Create getting-started flow
- [ ] Establish narrative structure
- [ ] Set up screenshot/asset pipeline
- [ ] Create contribution guide

### Consistency
- [ ] Identify generatable sections
- [ ] Set up generation scripts
- [ ] Configure freshness tracking
- [ ] Define ownership for each doc area
- [ ] Add doc review to PR checklist

---

## Measuring Success

### LLM Docs Quality

| Metric | How to Measure | Target |
|--------|----------------|--------|
| Navigation efficiency | Avg files read to answer question | < 4 |
| Accuracy | Spot-check LLM answers against code | > 95% correct |
| Freshness | % of docs updated within last release | > 80% |
| Coverage | Topics with dedicated doc vs total topics | > 90% |

### Human Docs Quality

| Metric | How to Measure | Target |
|--------|----------------|--------|
| Time to first success | User testing: time to complete tutorial | < 15 min |
| Support tickets | Questions answered by docs vs tickets | > 80% by docs |
| Bounce rate | Analytics on doc pages | < 40% |
| Search success | % of searches with clicks | > 70% |

### Consistency

| Metric | How to Measure | Target |
|--------|----------------|--------|
| Doc test pass rate | CI results | 100% |
| Link validity | Link checker results | 100% |
| Freshness delta | Docs version vs code version | 0 releases behind |
| Divergence incidents | Bug reports for doc/code mismatch | < 1/month |

---

## Summary

1. **Code is truth** — Documentation describes code, not the other way around
2. **Three layers, three purposes** — Don't mix them
3. **LLM docs: navigate efficiently** — Tree structure, small files, factual
4. **Human docs: convey value** — Narrative, progressive, visual
5. **Consistency requires systems** — Generation, testing, tracking, ownership
6. **Make staleness visible** — Version markers, freshness dates
7. **Link, don't duplicate** — Single source of truth, explicit references

---

*This document itself follows the principles it describes: it's structured for an LLM to extract methodology, while being readable enough for humans to understand and adopt.*
