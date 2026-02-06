# Human Documentation Ethos

> Part of the Website Organon. See also: [PHILOSOPHY.md](./PHILOSOPHY.md)
>
> Behavioral constraints for maintaining human-facing documentation.

---

## Identity

### What This Documentation IS

- **Human-optimized.** Narrative, visual, progressive disclosure.
- **Task-oriented.** Organized by what users want to accomplish.
- **Welcoming.** Friendly tone, encouraging, accessible to beginners.
- **Docusaurus-powered.** Search, sidebar navigation, versioning.

### What This Documentation is NOT

- **Not LLM-optimized.** For dense, navigable docs, see `docs/`.
- **Not exhaustive internals.** Implementation details live in `docs/components/`.
- **Not API-generated.** Handwritten for clarity and narrative flow.
- **Not the single source.** Code is truth; docs explain it.

---

## Core Invariants

1. **Code is the source of truth.** When docs conflict with code, update one or the other.

2. **Same facts as LLM docs.** `website/docs/` and `docs/` must be semantically equivalent. A fact added to one should exist in the other (formatted appropriately).

3. **Progressive structure.** Getting Started → Cookbook → Reference → Operations. Don't put advanced concepts in Getting Started.

4. **Every page has next steps.** Guide users to logical follow-up content.

5. **Examples are runnable.** Code blocks must work when copy-pasted. Test them.

6. **No broken links.** Docusaurus has `onBrokenLinks: 'throw'`. CI will catch this.

---

## File Constraints

### Naming

| Pattern | Use |
|---------|-----|
| `kebab-case.md` | All content files |
| `index.md` | Category landing pages |
| Frontmatter `title` | Display name (can differ from filename) |

### Required Frontmatter

```yaml
---
title: "Page Title"
sidebar_position: N
description: "One-line description for SEO and link previews"
---
```

### Size Limits

| File Type | Target Lines | Action if Exceeded |
|-----------|--------------|-------------------|
| Tutorial | 200-400 | Split into parts |
| Reference | 100-300 | Split by subtopic |
| Cookbook recipe | 100-200 | Keep focused |

Long pages are fine if well-structured with headers. Very short pages (<50 lines) should probably be merged.

---

## Writing Style

### Tone

| Do | Don't |
|----|-------|
| "You can add caching with..." | "One may add caching..." |
| "This catches the error" | "Errors are caught" |
| "Let's build a pipeline" | "A pipeline will be constructed" |
| "Here's how it works:" | "The following describes..." |

### Structure

| Element | Use |
|---------|-----|
| Headers | Break up content every 3-5 paragraphs |
| Code blocks | Show, don't just tell |
| Tables | Compare options, list features |
| Callouts | Highlight tips, warnings, notes |

### Callout Types (Docusaurus Admonitions)

```markdown
:::tip
Helpful suggestion
:::

:::note
Additional context
:::

:::warning
Important caveat
:::

:::danger
Breaking change or security concern
:::
```

---

## Code Examples

### Must Be Runnable

Every code example should work when copy-pasted. If setup is required, show it.

```markdown
**Bad:**
```constellation
result = ProcessData(input)  # What is 'input'?
```

**Good:**
```constellation
in data: { value: Int }
result = ProcessData(data)
out result
```
```

### Show Input/Output

When demonstrating behavior, show the complete flow:

```markdown
**Input:**
```json
{"text": "hello world"}
```

**Output:**
```json
{"text": "HELLO WORLD"}
```
```

### Language Tags

| Content | Tag |
|---------|-----|
| Constellation DSL | `constellation` |
| Scala code | `scala` |
| JSON data | `json` |
| Shell commands | `bash` |
| YAML config | `yaml` |

---

## Category Rules

### Getting Started

- **Goal:** Get user to "aha" moment quickly
- **Tone:** Exciting, promising
- **Avoid:** Deep technical details, edge cases
- **Required:** Clear next steps at end of each page

### Cookbook

- **Goal:** Solve specific problems
- **Tone:** Practical, direct
- **Format:** Use Case → Pipeline → Explanation → Variations
- **Avoid:** Theory without practical application

### Language Reference

- **Goal:** Complete syntax documentation
- **Tone:** Precise, technical
- **Format:** Syntax → Examples → Edge cases
- **Required:** Cover all valid inputs

### API Reference

- **Goal:** Document programmatic interfaces
- **Tone:** Technical, complete
- **Required:** All endpoints, all parameters, all responses

### Operations

- **Goal:** Enable production deployment
- **Tone:** Serious, operational
- **Required:** Prerequisites, verification steps

---

## Synchronization with LLM Docs

### When Adding Content

1. Write the human-friendly version in `website/docs/`
2. Add corresponding entry in `docs/` (dense, navigable)
3. If new feature: add to feature's ETHOS.md semantic mapping

### When Updating Content

1. Update both surfaces
2. Verify facts match
3. Keep tone appropriate to each surface

### Checking Equivalence

Ask: "Does someone reading only `docs/` learn the same facts as someone reading only `website/docs/`?"

---

## Sidebar Management

### File: `website/sidebars.ts`

- Categories are manually ordered
- New pages must be added to sidebar
- `sidebar_position` in frontmatter controls order within auto-generated sections

### Adding a New Page

1. Create `website/docs/category/page-name.md`
2. Add frontmatter with `sidebar_position`
3. Add to `sidebars.ts` if not using auto-generated sidebar
4. Run `npm run build` to verify no broken links

---

## Local Development

```bash
cd website
npm install
npm run start    # Dev server at localhost:3000
npm run build    # Production build (catches errors)
```

### Before Committing

```bash
npm run build    # Catches broken links, invalid frontmatter
```

---

## Decision Heuristics

### Where Does This Content Belong?

| Content Type | Location |
|--------------|----------|
| "What is X and why?" | Getting Started |
| "How do I do X?" | Cookbook |
| "What's the syntax for X?" | Language Reference |
| "What endpoints/methods exist?" | API Reference |
| "How do I deploy/operate X?" | Operations |

### When Uncertain

- **About structure:** Follow existing patterns in the same category
- **About tone:** Read adjacent pages for calibration
- **About depth:** Start shallow, link to deeper content
- **About duplication:** Link, don't copy

---

## What Is Out of Scope

Do not add to `website/docs/`:

- **LLM-optimized content** → `docs/`
- **Internal implementation notes** → `docs/components/` or `docs/dev/`
- **Auto-generated API docs** → Consider `docs/generated/`
- **Contributor workflow docs** → `CONTRIBUTING.md`, `CLAUDE.md`

---

## Maintenance Checklist

### After Code Changes

- [ ] Update affected documentation
- [ ] Verify code examples still work
- [ ] Check both `website/docs/` and `docs/`

### Periodic Review

- [ ] Test all code examples
- [ ] Click through all "next steps" links
- [ ] Run `npm run build` to catch broken links
- [ ] Compare feature coverage with `docs/features/`
