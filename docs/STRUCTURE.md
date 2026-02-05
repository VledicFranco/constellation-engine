# Documentation Structure & Ethos

> This file explains how the `docs/` directory is organized and why. Read this if you're contributing to documentation or want to understand the navigation pattern.

---

## Purpose

This documentation is **optimized for LLM consumption**. The goal is efficient knowledge retrieval - an LLM should find relevant context with minimal token usage through depth-first navigation.

For human-oriented documentation with rich formatting, tutorials, and visual guides, see `website/docs/`.

---

## Core Principles

### 1. Tree as Semantic Map

The directory structure is a **semantic hierarchy**:
- **Closer to root** = more abstract, high-level concepts
- **Deeper in tree** = more specific, detailed implementation

```
docs/
├── README.md          ← "What is Constellation?" (30 seconds)
├── overview/          ← Core concepts (5 minutes)
├── language/          ← DSL details
│   └── options/       ← Module options
│       └── caching.md ← Specific: cache + cache_backend
```

An LLM navigates by reading parent nodes to decide which child to explore, loading only what's needed.

### 2. README as Router

Every directory has a `README.md` that serves as a **routing node**:

```markdown
# Category Name

One-paragraph summary of what this category covers.

## Contents

| Path | Description |
|------|-------------|
| [child-a/](./child-a/) | What child-a covers |
| [child-b.md](./child-b.md) | What child-b covers |

## Quick Reference (optional)

Key facts an LLM might need without descending further.
```

This lets LLMs make navigation decisions without loading all children.

### 3. Progressive Disclosure

| Level | Content | Target Length |
|-------|---------|---------------|
| Root README | Project identity + top-level navigation | 50-80 lines |
| Category README | Summary + children table + quick reference | 80-150 lines |
| Leaf file | Complete detail for one focused topic | 100-200 lines |

If a leaf file exceeds 200 lines, consider splitting into a subdirectory.

### 4. Small, Focused Files

Each file answers **one question**:
- `caching.md` → "How do cache and cache_backend options work?"
- `execution-modes.md` → "What are hot, cold, and suspended pipelines?"

Avoid files that answer multiple unrelated questions.

### 5. Explicit Navigation

Every file (except root) includes a navigation header:

```markdown
# Topic Name

> **Path**: `docs/language/options/caching.md`
> **Parent**: [options/](./README.md) | **See also**: [resilience.md](./resilience.md)

Content starts here...
```

This helps LLMs understand location and find related content.

### 6. Cross-References Over Duplication

When content relates to another file, link rather than duplicate:

```markdown
The `cache_backend` option requires a custom backend implementation.
See [extensibility/cache-backend.md](../../extensibility/cache-backend.md).
```

Duplication causes staleness; references stay current.

---

## Navigation Pattern for LLMs

```
Task: "Understand how to implement retry with exponential backoff"

1. Read docs/README.md
   → See "language/" covers DSL syntax and options

2. Read docs/language/README.md
   → See "options/" covers module orchestration options

3. Read docs/language/options/README.md
   → See "resilience.md" covers retry, timeout, fallback, delay, backoff

4. Read docs/language/options/resilience.md
   → Found: complete information about retry + backoff interaction

Total: 4 files read, focused context acquired
```

Compare to loading a single 2000-line reference doc - the tree approach is more token-efficient when the LLM doesn't need everything.

---

## File Naming Conventions

| Pattern | Use |
|---------|-----|
| `README.md` | Directory router (required for every directory) |
| `lowercase-kebab.md` | Leaf content files |
| `UPPERCASE.md` | Meta-files (this file, CONTRIBUTING, etc.) |

---

## Content Guidelines

### Do

- Start with a one-sentence summary
- Use tables for structured information
- Include code examples for syntax
- Link to related files
- Keep files focused and scannable

### Don't

- Write long prose paragraphs (use lists/tables)
- Duplicate content across files
- Include implementation details in concept docs
- Add meta-commentary ("In this section we will...")
- Use complex formatting (LLMs parse markdown literally)

### Code Examples

Keep examples minimal and focused:

```constellation
# Good: shows one concept
result = GetUser(id) with retry: 3, backoff: exponential

# Bad: shows many unrelated concepts in one block
in userId: String
in options: { retries: Int, useCache: Boolean }
user = GetUser(userId) with retry: options.retries
enriched = EnrichProfile(user) with cache: 15min when options.useCache
validated = ValidateUser(enriched) with timeout: 5s, fallback: { valid: false }
out validated
```

---

## Directory Overview

```
docs/
├── README.md              # Root: What is Constellation? + navigation
├── STRUCTURE.md           # This file: documentation ethos
│
├── overview/              # High-level concepts
│   ├── README.md
│   ├── when-to-use.md     # Use cases, anti-patterns
│   ├── architecture.md    # System design, module graph
│   └── glossary.md        # Term definitions
│
├── language/              # Constellation DSL
│   ├── README.md
│   ├── types/             # Type system
│   ├── expressions/       # Operators, control flow
│   └── options/           # Module orchestration
│
├── runtime/               # Execution engine
│   ├── README.md
│   ├── execution-modes.md # Hot, cold, suspended
│   ├── scheduling.md      # Priority scheduler
│   └── module-builder.md  # Scala API
│
├── http-api/              # REST API
│   ├── README.md
│   ├── execution.md       # /run, /compile, /execute
│   ├── pipelines.md       # Pipeline management
│   └── security.md        # Auth, CORS, rate limiting
│
├── extensibility/         # SPI interfaces
│   ├── README.md
│   └── *.md               # Individual SPIs
│
├── tooling/               # Developer tools
│   ├── README.md
│   └── *.md               # Dashboard, VSCode, LSP
│
├── stdlib/                # Standard library
│   ├── README.md
│   └── *.md               # Function categories
│
└── dev/                   # Development docs (RFCs, benchmarks)
    └── ...                # Existing structure preserved
```

---

## Relationship to website/docs/

| Aspect | `docs/` (this directory) | `website/docs/` |
|--------|--------------------------|-----------------|
| Audience | LLMs, agents, tools | Humans |
| Optimization | Token efficiency, navigation | Readability, visual appeal |
| Structure | Deep tree, small files | Flat-ish, comprehensive pages |
| Examples | Minimal, focused | Rich, with context |
| Formatting | Plain markdown, tables | MDX, components, diagrams |

Content should be **semantically equivalent** but **formatted differently**. When updating features, update both.

---

## Contributing

When adding documentation:

1. **Find the right location** - Navigate the tree to find where it belongs
2. **Update parent README** - Add entry to the contents table
3. **Keep it focused** - One topic per file
4. **Add navigation header** - Path, parent, related links
5. **Cross-reference** - Link to related files rather than duplicating
6. **Check length** - If >200 lines, consider splitting

When restructuring:

1. **Update all parent READMEs** - Navigation tables must stay current
2. **Fix cross-references** - Search for links to moved files
3. **Preserve dev/** - RFCs and development docs have their own structure
