# Benchmarks

> **Path**: `docs/dev/benchmarks/`
> **Parent**: [dev/](../README.md)

Performance benchmarks, profiling guides, and historical results.

## Contents

| File | Description |
|------|-------------|
| [benchmark-framework.md](./benchmark-framework.md) | How to write and run benchmarks |
| [performance-benchmarks.md](./performance-benchmarks.md) | Benchmark documentation and targets |
| [benchmark-results.md](./benchmark-results.md) | Historical benchmark results |
| [PERFORMANCE-SUMMARY.md](./PERFORMANCE-SUMMARY.md) | Performance overview and key metrics |

## Quick Reference

### Running Benchmarks

```bash
sbt "langCompiler/testOnly *Benchmark"    # Compiler benchmarks
sbt "langCompiler/testOnly *RegressionTests"  # Regression suite
sbt "langLsp/testOnly *Benchmark"         # LSP benchmarks
```

### Key Performance Targets

| Operation | Target |
|-----------|--------|
| Parse (small) | <5ms |
| Full pipeline (medium) | <100ms |
| Cache hit | <5ms |
| Autocomplete | <50ms |
