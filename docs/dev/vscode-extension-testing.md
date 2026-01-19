# VSCode Extension Testing Guide

This document describes the test infrastructure for the Constellation VSCode extension, including how to run tests locally, understand the test structure, and add new tests.

## Overview

The VSCode extension uses [@vscode/test-electron](https://github.com/microsoft/vscode-test) to run integration and end-to-end tests in actual VS Code instances. This ensures tests verify real behavior rather than mocked interfaces.

### Test Categories

| Category | Location | Purpose |
|----------|----------|---------|
| Extension Activation | `extension.test.ts` | Verifies extension loads, commands register, language config works |
| LSP Integration | `integration/lsp.test.ts` | Tests WebSocket/LSP communication with server |
| E2E Workflows | `e2e/workflow.test.ts` | Tests complete user workflows end-to-end |

## Running Tests

### Prerequisites

- Node.js 18.x or higher
- npm

### Run All Tests

```bash
cd vscode-extension
npm test
```

This will:
1. Compile TypeScript (`npm run compile`)
2. Download VS Code if needed (cached in `.vscode-test/`)
3. Launch VS Code with the extension loaded
4. Execute all test suites
5. Report results in the terminal

### Run Tests with LSP Server

For full integration testing, start the Constellation server first:

```bash
# Terminal 1: Start the server
cd constellation-engine
make server

# Terminal 2: Run tests
cd vscode-extension
npm test
```

Tests that require the server will skip gracefully if it's unavailable.

## Test Structure

```
vscode-extension/
├── src/
│   └── test/
│       ├── runTest.ts              # Test runner entry point
│       ├── fixtures/               # Test data files
│       │   ├── simple.cst          # Basic script with one input
│       │   ├── multi-step.cst      # Pipeline with multiple steps
│       │   ├── with-errors.cst     # Script with intentional errors
│       │   └── no-inputs.cst       # Script with hardcoded values
│       └── suite/
│           ├── index.ts            # Mocha test suite configuration
│           ├── extension.test.ts   # Extension activation tests
│           ├── integration/
│           │   └── lsp.test.ts     # LSP integration tests
│           └── e2e/
│               └── workflow.test.ts # E2E user workflow tests
```

### Key Files

#### `runTest.ts`

Entry point that configures and launches VS Code for testing:

```typescript
import { runTests } from '@vscode/test-electron';

async function main() {
  await runTests({
    extensionDevelopmentPath,  // Path to extension
    extensionTestsPath,        // Path to test suite
    launchArgs: [
      testWorkspacePath,       // Open fixtures folder
      '--disable-extensions',  // Disable other extensions
    ],
  });
}
```

#### `suite/index.ts`

Mocha configuration for the test suite:

```typescript
const mocha = new Mocha({
  ui: 'tdd',           // Use suite() and test() syntax
  color: true,
  timeout: 60000,      // 60s timeout for extension tests
});
```

## Test Fixtures

Test fixtures are sample `.cst` files used by tests:

### `simple.cst`
Basic script with one input - tests basic extension functionality.
```constellation
in text: String
result = Uppercase(text)
out result
```

### `multi-step.cst`
Multi-step pipeline - tests complex DAG visualization.
```constellation
in text: String
in count: Int
trimmed = Trim(text)
upper = Uppercase(trimmed)
repeated = Repeat(upper, count)
out repeated
```

### `with-errors.cst`
Script with intentional errors - tests error handling and diagnostics.
```constellation
in text: String
result = UnknownModule(text)  # Error: unknown module
other = Uppercase(missing)     # Error: undefined variable
out result
```

### `no-inputs.cst`
Script with no inputs - tests edge case handling.
```constellation
text = "Hello World"
result = Uppercase(text)
out result
```

## Writing Tests

### Test Syntax

Tests use Mocha's TDD interface (`suite` and `test`):

```typescript
import * as assert from 'assert';
import * as vscode from 'vscode';

suite('My Test Suite', () => {
  test('should do something', async () => {
    // Arrange
    const document = await vscode.workspace.openTextDocument(testFilePath);

    // Act
    await vscode.window.showTextDocument(document);

    // Assert
    assert.strictEqual(document.languageId, 'constellation');
  });
});
```

### Common Patterns

#### Opening a Test File
```typescript
const fixturesPath = path.join(__dirname, '..', '..', '..', '..', 'src', 'test', 'fixtures');
const testFilePath = path.join(fixturesPath, 'simple.cst');
const document = await vscode.workspace.openTextDocument(testFilePath);
const editor = await vscode.window.showTextDocument(document);
```

#### Executing a Command
```typescript
try {
  await vscode.commands.executeCommand('constellation.runScript');
  assert.ok(true, 'Command executed');
} catch (error) {
  // Handle expected failures gracefully
}
```

#### Waiting for Async Operations
```typescript
// Wait for extension to activate
await new Promise(resolve => setTimeout(resolve, 2000));

// Wait for editor to update
await new Promise(resolve => setTimeout(resolve, 100));
```

#### Checking Configuration
```typescript
const config = vscode.workspace.getConfiguration('constellation');
const serverUrl = config.get<string>('server.url');
assert.ok(serverUrl?.includes('localhost'));
```

#### Skipping Tests When Server Unavailable
```typescript
suite('LSP Tests', function() {
  let serverAvailable = false;

  suiteSetup(async () => {
    // Check server connectivity
    serverAvailable = await checkServerAvailable();
  });

  test('requires server', function() {
    if (!serverAvailable) {
      this.skip();
      return;
    }
    // Test code here
  });
});
```

### Adding a New Test

1. **Identify the test category:**
   - Extension behavior → `extension.test.ts`
   - LSP communication → `integration/lsp.test.ts`
   - User workflow → `e2e/workflow.test.ts`

2. **Add the test:**
```typescript
test('My new test', async () => {
  // Test implementation
});
```

3. **Add fixtures if needed:**
   Create new `.cst` files in `src/test/fixtures/`

4. **Run tests to verify:**
```bash
npm test
```

## CI Integration

Tests run automatically on GitHub Actions for:
- Push to `master` (when `vscode-extension/**` changes)
- Pull requests to `master`

### CI Matrix

| OS | VS Code Version |
|----|-----------------|
| Ubuntu | stable |
| Windows | stable |
| macOS | stable |

### CI Workflow

The workflow (`.github/workflows/vscode-extension-tests.yml`):

1. **test** - Runs tests on all platforms
   - Linux requires `xvfb-run` for display
   - Windows/macOS run directly

2. **lint** - Checks TypeScript compilation

3. **package** - Creates VSIX artifact (only if tests pass)

## Troubleshooting

### Tests timeout

Increase timeout in `suite/index.ts`:
```typescript
const mocha = new Mocha({
  timeout: 120000, // 2 minutes
});
```

Or for a specific test:
```typescript
test('slow test', async function() {
  this.timeout(120000);
  // ...
});
```

### VS Code download fails

Clear the cache and retry:
```bash
rm -rf .vscode-test/
npm test
```

### Tests fail on CI but pass locally

Common causes:
- **Timing issues**: Add waits for async operations
- **Path differences**: Use `path.join()` for cross-platform paths
- **Display issues on Linux**: Ensure `xvfb-run` is used

### Extension not activating

1. Check the Output panel in VS Code
2. Verify `.cst` file extension is recognized
3. Check for errors in extension host console

## Best Practices

1. **Use descriptive test names** - Test names should describe expected behavior
2. **Test one thing per test** - Keep tests focused and independent
3. **Handle server unavailability** - Tests should skip gracefully without server
4. **Use fixtures** - Don't hardcode test data in tests
5. **Wait appropriately** - VS Code APIs are often async; add reasonable waits
6. **Clean up after tests** - Close editors, restore settings in `suiteTeardown`
7. **Keep tests fast** - Avoid unnecessary waits; use minimal timeouts

## Related Documentation

- [VSCode Extension README](../../vscode-extension/README.md)
- [VSCode Testing API](https://code.visualstudio.com/api/working-with-extensions/testing-extension)
- [@vscode/test-electron](https://github.com/microsoft/vscode-test)
- [Mocha Documentation](https://mochajs.org/)
