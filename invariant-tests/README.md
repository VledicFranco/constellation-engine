# Invariant Tests

Structural invariant verification for Constellation Engine using [organon-testing](https://github.com/VledicFranco/organon).

## Running

```bash
make test-invariants
```

## Test Files

| File | Invariants | Purpose |
|------|-----------|---------|
| `CoreInvariantsSpec` | INV-ROOT-3, INV-CORE-1, INV-CORE-6 | Core module purity and exports |
| `StructuralInvariantsSpec` | INV-ORGANON-3, INV-ORGANON-4, INV-ORGANON-4-FEAT, INV-STRUCT-1 | Organon governance structure |
| `NamingConventionSpec` | INV-NAMING-1/2 | File naming conventions |
| `PurityInvariantsSpec` | INV-RUNTIME-1a, INV-ROOT-3a/3b | Module purity constraints |

## Adding New Invariant Tests

1. Create a new spec extending `OrganonFlatSpec`
2. Use `testInvariant("INV-ID", "description")` to link tests to invariant IDs
3. Use assertions from `io.github.vledicfranco.organon.testing.Assertions`
4. Set `cwd = Some(projectRoot)` for path resolution from project root
