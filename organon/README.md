# Organon — Navigation Guide

> Version 1.0 — Organon Structure Documentation

This directory contains the **organon system** for Constellation Engine — structured constraint documentation optimized for LLM context loading and human onboarding.

---

## Hierarchy

```
/ETHOS.md                    ← Product-level constraints
/PHILOSOPHY.md               ← Product-level design rationale
/organon/
  ├── ETHOS.md               ← Meta (about the organon system)
  ├── PHILOSOPHY.md          ← Meta design rationale
  ├── components/            ← Component-level organons
  ├── features/              ← Feature-level organons
  ├── protocols/             ← Operational procedures
  └── generated/             ← Auto-generated Scala catalogs
/rfcs/                       ← Proposals and change requests
/dev/                        ← Internal development documentation
```

---

## Scope Resolution

| Scope | Location | Purpose |
|-------|----------|---------|
| Product | `/ETHOS.md`, `/PHILOSOPHY.md` | Universal constraints |
| Meta | `organon/ETHOS.md` | About the organon system itself |
| Component | `organon/components/<name>/` | Implementation unit constraints |
| Feature | `organon/features/<name>/` | Capability constraints |
| Protocol | `organon/protocols/<name>.md` | Operational procedures |

Child scopes inherit parent constraints. Product-level overrides all.

---

## LLM Loading Order

```
Session Start:
  ├─ Load /ETHOS.md (product invariants)
  └─ Load /PHILOSOPHY.md (design rationale)

Before Working on Component:
  └─ Load organon/components/<component>/ETHOS.md

Before Working on Feature:
  └─ Load organon/features/<feature>/ETHOS.md

When Following Procedure:
  └─ Load organon/protocols/<protocol>.md
```

---

## Current Organons

### Components
- [core](./components/core/) — Type system, CValue, CType
- [runtime](./components/runtime/) — Module execution, DAG runtime
- [compiler](./components/compiler/) — Parser, type checker, IR generator
- [lsp](./components/lsp/) — Language server protocol
- [http-api](./components/http-api/) — REST API, WebSocket, dashboard
- [stdlib](./components/stdlib/) — Standard library modules
- [module-provider](./components/module-provider/) — gRPC-based dynamic module registration

### Features
- [execution](./features/execution/) — Hot/cold execution, suspension
- [resilience](./features/resilience/) — Retry, fallback, caching
- [parallelization](./features/parallelization/) — Layer execution, scheduling
- [type-safety](./features/type-safety/) — Type algebra, optionals
- [extensibility](./features/extensibility/) — SPIs, module provider protocol
- [tooling](./features/tooling/) — LSP, dashboard, VSCode extension

### Protocols
- [semantic-mapping](./protocols/semantic-mapping.md) — Bridging generated catalogs to domain meaning

---

## Related Documents

- **`/CLAUDE.md`** — Operational development guide
- **`/rfcs/`** — Feature proposals and change requests
- **`/dev/`** — Internal development documentation
- **`website/docs/`** — Human-readable documentation

---

## Document Metadata

- **Scope:** Meta-level — defines organon structure
- **Authority:** Navigation guide, does not define constraints
- **Versioning:** Increment on structure changes
