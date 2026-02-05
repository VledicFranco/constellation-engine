import * as path from 'path';
import Mocha from 'mocha';
import { glob } from 'glob';

// Tests to skip in CI for faster feedback (these are heavy e2e tests)
const CI_SKIP_PATTERNS = [
  'performance.test.js',
  'memory-management.test.js',
];

export async function run(): Promise<void> {
  // Create the mocha test
  const mocha = new Mocha({
    ui: 'tdd',
    color: true,
    timeout: 60000, // 60s timeout for extension tests
  });

  const testsRoot = path.resolve(__dirname, '.');
  const isCI = process.env.CI === 'true';

  // Find all test files
  let files = await glob('**/*.test.js', { cwd: testsRoot });

  // Skip heavy tests in CI for faster feedback
  if (isCI) {
    const skipCount = files.length;
    files = files.filter(f => !CI_SKIP_PATTERNS.some(pattern => f.endsWith(pattern)));
    const skipped = skipCount - files.length;
    if (skipped > 0) {
      console.log(`[CI] Skipping ${skipped} heavy test file(s) for faster feedback`);
    }
  }

  // Add files to the test suite
  for (const f of files) {
    mocha.addFile(path.resolve(testsRoot, f));
  }

  // Run the mocha test
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
