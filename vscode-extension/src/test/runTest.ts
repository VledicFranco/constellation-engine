/**
 * VSCode Extension Test Runner
 *
 * This is the entry point for running the extension's integration tests.
 * It uses @vscode/test-electron to:
 *   1. Download a VS Code instance (cached in .vscode-test/)
 *   2. Launch VS Code with our extension loaded
 *   3. Run the Mocha test suite inside VS Code
 *   4. Report results and exit with appropriate code
 *
 * Usage:
 *   npm test                    # Run all tests
 *   npm run compile && npm test # Compile first, then test
 *
 * The test runner opens the fixtures folder as a workspace, which contains
 * sample .cst files used by the tests.
 *
 * @see https://code.visualstudio.com/api/working-with-extensions/testing-extension
 * @see docs/dev/vscode-extension-testing.md for full documentation
 */

import * as path from 'path';
import { runTests } from '@vscode/test-electron';

async function main() {
  try {
    // The folder containing the Extension Manifest package.json
    // This tells VS Code where to find our extension to load it
    const extensionDevelopmentPath = path.resolve(__dirname, '../../');

    // The path to the compiled test suite entry point (suite/index.js)
    // VS Code will require() this module to run tests
    const extensionTestsPath = path.resolve(__dirname, './suite/index');

    // Path to the test workspace containing fixture files (.cst scripts)
    // Opening this as a workspace makes fixtures available to tests
    const testWorkspacePath = path.resolve(__dirname, '../../src/test/fixtures');

    // Download VS Code (if needed), launch it, and run tests
    // The first run downloads VS Code; subsequent runs use the cached version
    await runTests({
      extensionDevelopmentPath,
      extensionTestsPath,
      launchArgs: [
        testWorkspacePath,
        '--disable-extensions', // Disable other extensions to avoid interference
      ],
    });
  } catch (err) {
    console.error('Failed to run tests:', err);
    process.exit(1);
  }
}

main();
