# Documentation Ethos

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

## Invariants

1. **Code is the source of truth.** When docs conflict with code, fix one or the other.
2. **Feature-driven organization.** Primary navigation is by capability, not by module.
3. **README as router.** Every directory has a README.md with a contents table.
4. **Philosophy/Ethos per domain.** Each feature has PHILOSOPHY.md and ETHOS.md.
5. **Component cross-references.** Feature docs include "Components Involved" tables.
6. **No duplication.** Link, don't copy. One source of truth per fact.
7. **Generated catalogs are read-only.** Never edit `docs/generated/*.md` manually.
8. **Semantic mapping bridges layers.** Component ETHOS files map generated types to domain meaning.

---

## Principles (Prioritized)

1. **Accuracy over coverage** — Correct information beats comprehensive information.
2. **Navigability over prose** — Tables and links beat paragraphs.
3. **Concision over explanation** — State facts; put explanations in PHILOSOPHY.md.

---

## File Constraints

| File Type | Max Lines | Naming |
|-----------|-----------|--------|
| README | 100 | `README.md` (required per directory) |
| Content | 200 | `lowercase-kebab.md` |
| Philosophy/Ethos | 150 | `PHILOSOPHY.md`, `ETHOS.md` |

---

## Decision Heuristics

| Situation | Action |
|-----------|--------|
| Adding new docs | Find the right feature, update parent README, add component cross-refs |
| Modifying docs | Verify against code, update all references, check both surfaces |
| Uncertain about structure | Follow existing patterns in sibling directories |
| Uncertain about content | Check the code; it's the source of truth |

---

## Three-Layer Documentation Model

| Layer | Location | Purpose |
|-------|----------|---------|
| Code | `modules/*/src/` | Source of truth |
| Generated | `docs/generated/` | What exists (signatures) |
| Organon | `docs/components/*/ETHOS.md` | What it means (semantics) |

See [Semantic Mapping Protocol](./protocols/semantic-mapping.md) for writing semantic mappings.

Run `make verify-ethos` to check invariant references. Run `make check-docs` for freshness.

---

## Dual Surface Consistency

| Aspect | `docs/` (LLM) | `website/docs/` (Human) |
|--------|---------------|-------------------------|
| Same facts | Yes | Yes |
| Same structure | No (navigable) | No (narrative) |

When updating features, check if both surfaces need updates.

---

## Out of Scope

Do not add to `docs/`:
- Tutorials → `website/docs/getting-started/`
- Marketing → `website/docs/`
- Changelogs → `CHANGELOG.md`
- API specs → `website/docs/api-reference/`
