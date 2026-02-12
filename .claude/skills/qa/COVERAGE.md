# Coverage Thresholds Reference

Per-module coverage thresholds configured in `build.sbt`. Phase 7 (Coverage) validates against these.

## Per-Module Thresholds

| Module | sbt Name | Stmt Min | Branch Min |
|--------|----------|----------|------------|
| core | `core` | 80% | 70% |
| runtime | `runtime` | 67% | 65% |
| lang-ast | `langAst` | 70% | 60% |
| lang-parser | `langParser` | 50% | 70% |
| lang-compiler | `langCompiler` | 54% | 57% |
| lang-stdlib | `langStdlib` | 13% | 60% |
| lang-lsp | `langLsp` | 53% | 81% |
| http-api | `httpApi` | 32% | 49% |
| module-provider-sdk | `moduleProviderSdk` | 79% | 85% |
| module-provider | `moduleProvider` | 66% | 70% |
| lang-cli | `langCli` | 28% | 31% |
| example-app | `exampleApp` | 14% | 36% |
| cache-memcached | `cacheMemcached` | (global) | (global) |
| doc-generator | `docGenerator` | (global) | (global) |

## Global Defaults

Modules without explicit thresholds use the global defaults from `build.sbt`:

```scala
ThisBuild / coverageMinimumStmtTotal := 60
ThisBuild / coverageMinimumBranchTotal := 50
```

## Coverage Exclusions

- **module-provider-sdk**: Excludes ScalaPB-generated code (`io.constellation.provider.v1.*`) and `src_managed/` files
- All other modules: no exclusions

## Enforcement

`coverageFailOnMinimum` is currently `false` globally. When set to `true`, `sbt coverageReport` fails if any module is below its threshold.

To check coverage manually:
```bash
sbt coverage test coverageReport coverageAggregate
```

Per-module reports are at:
```
modules/<module>/target/scala-3.3.4/scoverage-report/scoverage.xml
```

Aggregate report:
```
target/scala-3.3.4/scoverage-report/scoverage.xml
```
