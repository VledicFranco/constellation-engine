# Documentation Ethos

> Part of the [Documentation Organon](./README.md#organon-guiding-documents). See also: [PHILOSOPHY.md](./PHILOSOPHY.md)
>
> Behavioral constraints for LLMs and humans working on this documentation.

---

## Identity

### What This Documentation IS

- **LLM-optimized.** Structured for token-efficient navigation and context building.
- **Feature-driven.** Organized by what the system does, not how it's built.
- **Hierarchical.** A navigable tree where depth = specificity.
- **Cross-referenced.** Features link to components; no duplication.

### What This Documentation is NOT

- **Not human-first.** For human-readable docs, see `website/docs/`.
- **Not exhaustive.** Covers concepts and patterns, not every API detail.
- **Not a tutorial.** For step-by-step guides, see `website/docs/getting-started/`.
- **Not standalone.** References code as source of truth.

---

## Core Invariants

1. **Code is the source of truth.** When docs conflict with code, fix one or the other. Never leave conflicts.

2. **Feature-driven organization.** Primary navigation is by capability, not by module.

3. **README as router.** Every directory has a README.md with a contents table.

4. **Philosophy/Ethos per domain.** Each feature has PHILOSOPHY.md and ETHOS.md.

5. **Component cross-references.** Feature docs include "Components Involved" tables.

6. **No duplication.** Link, don't copy. One source of truth per fact.

---

## File Constraints

### Naming

| Pattern | Use |
|---------|-----|
| `README.md` | Directory router (required) |
| `PHILOSOPHY.md` | Why this domain exists |
| `ETHOS.md` | Behavioral constraints |
| `lowercase-kebab.md` | Content files |

### Size Limits

| File Type | Max Lines | Action if Exceeded |
|-----------|-----------|-------------------|
| README | 100 | Split into subdirectories |
| Content | 200 | Split into multiple files |
| Philosophy/Ethos | 150 | Tighten, don't ramble |

### Required Sections

**README.md:**
- Path/Parent header
- Brief summary
- Contents table
- Quick reference (optional)

**Content files:**
- Path/Parent header
- Components Involved table
- Related links

---

## Decision Heuristics

### Adding New Documentation

1. **Find the right feature.** Does it belong in an existing feature, or need a new one?
2. **Update the parent README.** Add entry to contents table.
3. **Include component cross-references.** Link to implementation files.
4. **Keep it focused.** One topic per file.

### Modifying Existing Documentation

1. **Verify against code.** Is the claim still true?
2. **Update all references.** Search for links to modified content.
3. **Check both surfaces.** Does `website/docs/` need a parallel update?

### When Uncertain

- **About structure:** Follow existing patterns in sibling directories.
- **About content:** Check the code; it's the source of truth.
- **About audience:** This is for LLMs; keep it factual and navigable.

---

## Consistency with website/docs/

| Aspect | `docs/` (LLM) | `website/docs/` (Human) |
|--------|---------------|-------------------------|
| Same feature? | Must exist in both | Must exist in both |
| Same facts? | Yes, semantically equivalent | Yes, semantically equivalent |
| Same structure? | No, optimized differently | No, optimized differently |
| Same wording? | No, concise here | No, narrative there |

When updating a feature, check if both surfaces need updates.

---

## What Is Out of Scope

Do not add to `docs/`:

- **Tutorials.** Step-by-step learning paths → `website/docs/getting-started/`
- **Marketing.** Value propositions, comparisons → `website/docs/`
- **Changelogs.** Version history → `CHANGELOG.md` at repo root
- **API specs.** OpenAPI, generated docs → `website/docs/api-reference/`

---

## Maintenance

### After Code Changes

1. Check if affected features need doc updates.
2. Verify "Components Involved" tables are still accurate.
3. Update both `docs/` and `website/docs/` if needed.

### Periodic Review

- Are file sizes within limits?
- Are cross-references still valid?
- Do philosophy/ethos docs reflect current practice?
