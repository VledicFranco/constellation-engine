# Human Documentation Philosophy

> Part of the Website Organon. See also: [ETHOS.md](./ETHOS.md)
>
> Why the human-facing documentation is structured the way it is.

---

## The Problem

Documentation serves multiple audiences with conflicting needs:

| Audience | Needs | Optimization |
|----------|-------|--------------|
| Evaluators | Quick understanding, value proposition | Scannable, visual, persuasive |
| Learners | Progressive disclosure, guided paths | Tutorials, step-by-step |
| Users | Task completion, reference lookup | Searchable, complete |
| Contributors | Accuracy, maintainability | Single source of truth |

A single documentation structure optimized for one audience degrades the experience for others.

---

## The Bet

We maintain **two documentation surfaces**:

| Surface | Location | Audience | Optimization |
|---------|----------|----------|--------------|
| Human docs | `website/docs/` | Humans | Narrative, visual, progressive |
| LLM docs | `docs/` | LLMs, agents | Navigable tree, dense, factual |

Content is **semantically equivalent** but **formatted differently**. The same feature exists in both places, structured for its audience.

---

## Design Decisions

### 1. Progressive Disclosure

Information is layered from simple to complex:

```
Getting Started (why, quick win)
    ↓
Concepts (mental model)
    ↓
Tutorial (guided practice)
    ↓
Cookbook (patterns)
    ↓
Language Reference (complete syntax)
    ↓
API Reference (technical details)
```

Users can stop at any level when they have enough context to proceed.

### 2. Task-Oriented Organization

Categories reflect **what users want to do**, not how the system is built:

| Category | User Intent |
|----------|-------------|
| Getting Started | "I want to understand what this is" |
| Cookbook | "I want to solve a specific problem" |
| Language Reference | "I need syntax details" |
| Operations | "I need to run this in production" |
| API Reference | "I need programmatic access details" |

### 3. Narrative Flow

Human docs use complete sentences and explanations:

```markdown
# Good (Human docs)
Constellation catches type errors at compile time. When you access
a field that doesn't exist, the compiler tells you immediately—before
your pipeline runs in production.

# Dense (LLM docs - different optimization)
Type errors caught at compile time. Invalid field access → compile error.
```

### 4. Visual Learning

Code examples show input and output:

```markdown
**Input:**
```json
{"userId": 123, "userName": "Alice"}
```

**Pipeline:**
```constellation
in data: { userId: Int, userName: String }
greeting = FormatGreeting(data.userName)
out greeting
```

**Output:**
```json
{"message": "Hello, Alice!"}
```

### 5. Friendly Tone

Second person, present tense, encouraging:

- "You can add resilience with..." (not "One may add...")
- "This catches the error before production" (not "Errors are caught...")
- "Let's build a pipeline" (not "A pipeline shall be constructed")

### 6. Scannable Structure

Every page follows a predictable pattern:

1. **Title** — What this page covers
2. **Quick summary** — One paragraph, the key point
3. **Content** — Explanations with examples
4. **Next steps** — Where to go from here

Headers, tables, and code blocks break up walls of text.

---

## Category Purposes

| Category | Purpose | Tone |
|----------|---------|------|
| Getting Started | Convince & orient | Exciting, promising |
| Cookbook | Solve problems | Practical, copy-paste ready |
| Language Reference | Document syntax | Precise, complete |
| API Reference | Document interfaces | Technical, exhaustive |
| Operations | Enable production | Serious, operational |
| Resources | Support journey | Helpful, administrative |

---

## Trade-Offs

| Decision | Benefit | Cost |
|----------|---------|------|
| Two surfaces | Optimized per audience | Maintenance burden |
| Progressive disclosure | Low barrier to entry | May frustrate experts |
| Narrative style | Accessible to beginners | More words per fact |
| Task-oriented | Matches user intent | Harder to find internals |
| Docusaurus | Rich features, search | Build step, dependencies |

---

## Relationship to Other Docs

| Location | Purpose |
|----------|---------|
| `website/docs/` (this) | Human-optimized documentation |
| `docs/` | LLM-optimized documentation |
| `docs/generated/` | Auto-extracted type signatures |
| Repo root `PHILOSOPHY.md` | Product philosophy |
| Repo root `ETHOS.md` | Codebase constraints |

---

## Key Insight

Human docs and LLM docs serve the same information need (understanding Constellation) but through different cognitive interfaces. Humans scan, skim, and seek narrative; LLMs traverse, parse, and seek structure. Serving both well requires intentionally different presentations of the same truth.
