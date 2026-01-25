# Performance Test Fixtures

These fixtures are used for E2E performance tests and match the Scala `TestFixtures` in `modules/lang-compiler/src/test/scala/io/constellation/lang/benchmark/TestFixtures.scala`.

## Fixture Sizes

| File | Lines | Purpose | Parse Target | Pipeline Target |
|------|-------|---------|--------------|-----------------|
| `perf-small.cst` | ~15 | Quick sanity checks | <5ms | <50ms |
| `perf-medium.cst` | ~80 | Realistic small file | <50ms | <150ms |
| `perf-large.cst` | ~200 | Complex pipeline | <200ms | <400ms |
| `perf-stress-100.cst` | ~110 | Scalability test | N/A | Stress |

## Usage in TypeScript Tests

```typescript
import * as path from 'path';
import * as vscode from 'vscode';

const fixturesPath = path.join(__dirname, '../../fixtures');

// Load a fixture file
const smallFixture = vscode.Uri.file(path.join(fixturesPath, 'perf-small.cst'));
const mediumFixture = vscode.Uri.file(path.join(fixturesPath, 'perf-medium.cst'));
const largeFixture = vscode.Uri.file(path.join(fixturesPath, 'perf-large.cst'));
const stressFixture = vscode.Uri.file(path.join(fixturesPath, 'perf-stress-100.cst'));

// Example: Open and measure
const start = performance.now();
const doc = await vscode.workspace.openTextDocument(smallFixture);
await vscode.window.showTextDocument(doc);
const elapsed = performance.now() - start;
console.log(`File open took ${elapsed.toFixed(2)}ms`);
```

## Fixture Content Overview

### perf-small.cst
Basic operations: type definition, inputs, field access, conditional, guard, coalesce.
```
type Data = { value: Int, flag: Boolean }
in data: Data
result = if (flag) value else fallback
```

### perf-medium.cst
Multiple record types, compound boolean logic, guard expressions, coalesce chains.
```
type Config = { enabled: Boolean, active: Boolean, ... }
isFullyEnabled = enabled and active
effectiveScore = premiumScore ?? activeScore ?? defaultScore
```

### perf-large.cst
Complex access control system with 4 input types, 50+ derived values, nested conditionals.
```
# System config, feature flags, user profile, access rules
canAccessExperimental = canAccessAdvanced and experimentalFeatures and canAccessBeta
```

### perf-stress-100.cst
100 chained boolean operations for scalability testing.
```
b0 = baseFlag
b1 = b0 or baseFlag
b2 = not b1
...
b100 = b99 and baseFlag
```

## Updating Fixtures

When updating fixtures, ensure they remain in sync with the Scala TestFixtures. Both sets should test equivalent complexity levels.

### Synchronization Checklist

- [ ] Line counts approximately match
- [ ] Same language features used (types, guards, coalesce, conditionals)
- [ ] Complexity level is equivalent
- [ ] Performance targets align

## Existing Functional Fixtures

The following fixtures are for functional testing (not performance):
- `simple.cst` - Basic syntax test
- `multi-step.cst` - Multi-step pipeline
- `no-inputs.cst` - No input declarations
- `with-errors.cst` - Syntax errors for error handling tests
- `with-examples.cst` - Example data
- `mixed-examples.cst` - Mixed example formats
