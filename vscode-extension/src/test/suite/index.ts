/**
 * Mocha Test Suite Configuration
 *
 * This module configures and runs the Mocha test suite inside VS Code.
 * It is loaded by the test runner (runTest.ts) after VS Code starts.
 *
 * Configuration:
 *   - ui: 'tdd' - Uses suite() and test() syntax (not describe/it)
 *   - timeout: 60000ms - Extension tests need longer timeouts for VS Code startup
 *   - color: true - Colorized output for better readability
 *
 * Test Discovery:
 *   All files matching **\/*.test.js in this directory are included.
 *   This allows organizing tests into subdirectories (integration/, e2e/).
 *
 * @see https://mochajs.org/#tdd
 */

import * as path from 'path';
import Mocha from 'mocha';
import { glob } from 'glob';

/**
 * Runs the test suite inside VS Code.
 *
 * This function is called by @vscode/test-electron after VS Code launches.
 * It discovers all test files, configures Mocha, and executes the tests.
 *
 * @returns Promise that resolves when all tests pass, rejects on failure
 */
export async function run(): Promise<void> {
  // Configure Mocha with TDD interface (suite/test instead of describe/it)
  // The 60s timeout accommodates VS Code startup and extension activation
  const mocha = new Mocha({
    ui: 'tdd',
    color: true,
    timeout: 60000,
  });

  const testsRoot = path.resolve(__dirname, '.');

  // Discover all test files recursively
  // Pattern matches: extension.test.js, integration/lsp.test.js, e2e/workflow.test.js
  const files = await glob('**/*.test.js', { cwd: testsRoot });

  // Register each test file with Mocha
  for (const f of files) {
    mocha.addFile(path.resolve(testsRoot, f));
  }

  // Execute tests and return result
  return new Promise((resolve, reject) => {
    mocha.run(failures => {
      if (failures > 0) {
        reject(new Error(`${failures} tests failed.`));
      } else {
        resolve();
      }
    });
  });
}
