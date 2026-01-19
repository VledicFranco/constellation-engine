/**
 * Build & Test Tools for Constellation Engine MCP Server
 */

import * as fs from 'fs';
import * as path from 'path';
import { z } from 'zod';
import { spawnAsync, withTiming } from '../utils/process.js';
import { getChangedFiles } from '../utils/git.js';
import {
  getCacheDir,
  MODULE_SBT_NAMES,
  getAffectedModules,
} from '../utils/paths.js';
import {
  parseScalaTestOutput,
  parseScalacOutput,
  countWarnings,
} from '../utils/parsers.js';
import type {
  ModuleName,
  TestResult,
  CachedTestResult,
  BuildResult,
  AffectedTestsResult,
} from '../types/index.js';

// Tool schemas
export const RunTestsSchema = z.object({
  module: z
    .enum(['all', 'core', 'runtime', 'parser', 'compiler', 'lsp', 'http', 'stdlib'])
    .default('all'),
  fastMode: z.boolean().default(false),
});

export const GetTestStatusSchema = z.object({
  module: z
    .enum(['all', 'core', 'runtime', 'parser', 'compiler', 'lsp', 'http', 'stdlib'])
    .default('all'),
});

export const GetBuildStatusSchema = z.object({
  module: z
    .enum(['all', 'core', 'runtime', 'parser', 'compiler', 'lsp', 'http', 'stdlib'])
    .default('all'),
});

export const RunAffectedTestsSchema = z.object({
  baseBranch: z.string().default('master'),
});

// Cache file path
function getTestCachePath(): string {
  return path.join(getCacheDir(), 'test-results.json');
}

interface CacheEntry {
  module: ModuleName;
  timestamp: string;
  result: TestResult;
}

function readCache(): Record<string, CacheEntry> {
  const cachePath = getTestCachePath();
  try {
    if (fs.existsSync(cachePath)) {
      return JSON.parse(fs.readFileSync(cachePath, 'utf-8'));
    }
  } catch {
    // Cache corrupted, return empty
  }
  return {};
}

function writeCache(entry: CacheEntry): void {
  const cache = readCache();
  cache[entry.module] = entry;
  const cachePath = getTestCachePath();
  fs.writeFileSync(cachePath, JSON.stringify(cache, null, 2), 'utf-8');
}

/**
 * Build the test command for a specific module
 */
function getTestCommand(module: ModuleName, fastMode: boolean): string {
  const isWindows = process.platform === 'win32';

  if (module === 'all') {
    // Use make test for all modules
    return isWindows ? '.\\scripts\\dev.ps1 -TestOnly' : 'make test';
  }

  const sbtProject = MODULE_SBT_NAMES[module];
  if (!sbtProject) {
    throw new Error(`Unknown module: ${module}`);
  }

  // Use sbt directly for specific modules
  const testCmd = fastMode ? 'testQuick' : 'test';
  return isWindows
    ? `sbt "${sbtProject}/${testCmd}"`
    : `sbt "${sbtProject}/${testCmd}"`;
}

/**
 * Build the compile command for a specific module
 */
function getCompileCommand(module: ModuleName): string {
  const isWindows = process.platform === 'win32';

  if (module === 'all') {
    return isWindows ? '.\\scripts\\dev.ps1 -CompileOnly' : 'make compile';
  }

  const sbtProject = MODULE_SBT_NAMES[module];
  if (!sbtProject) {
    throw new Error(`Unknown module: ${module}`);
  }

  return isWindows
    ? `sbt "${sbtProject}/compile"`
    : `sbt "${sbtProject}/compile"`;
}

/**
 * Run tests for all modules or a specific one
 */
export async function runTests(
  module: ModuleName = 'all',
  fastMode: boolean = false
): Promise<TestResult> {
  const command = getTestCommand(module, fastMode);

  const { result: execResult, duration } = await withTiming(() =>
    spawnAsync(command, [], {
      timeout: 600000, // 10 minutes for tests
    })
  );

  const { summary, failedTests } = parseScalaTestOutput(
    execResult.stdout + execResult.stderr
  );

  const testResult: TestResult = {
    success: execResult.exitCode === 0,
    exitCode: execResult.exitCode,
    stdout: execResult.stdout,
    stderr: execResult.stderr,
    duration,
    summary,
    failedTests: failedTests.length > 0 ? failedTests : undefined,
  };

  // Cache the result
  writeCache({
    module,
    timestamp: new Date().toISOString(),
    result: testResult,
  });

  return testResult;
}

/**
 * Get cached results from most recent test run (no re-execution)
 */
export async function getTestStatus(
  module: ModuleName = 'all'
): Promise<CachedTestResult> {
  const cache = readCache();
  const entry = cache[module];

  if (!entry) {
    return { available: false };
  }

  return {
    available: true,
    lastRunTimestamp: entry.timestamp,
    summary: entry.result.summary,
    failedTests: entry.result.failedTests,
  };
}

/**
 * Check if project compiles
 */
export async function getBuildStatus(
  module: ModuleName = 'all'
): Promise<BuildResult> {
  const command = getCompileCommand(module);

  const { result: execResult, duration } = await withTiming(() =>
    spawnAsync(command, [], {
      timeout: 300000, // 5 minutes for compilation
    })
  );

  const errors = parseScalacOutput(execResult.stdout + execResult.stderr);
  const warnings = countWarnings(execResult.stdout + execResult.stderr);

  return {
    success: execResult.exitCode === 0,
    exitCode: execResult.exitCode,
    errors: errors.filter((e) => e.severity === 'error'),
    warnings,
    duration,
  };
}

/**
 * Run tests only for modules affected by changed files
 */
export async function runAffectedTests(
  baseBranch: string = 'master'
): Promise<AffectedTestsResult> {
  // Get changed files
  const changedFiles = await getChangedFiles(baseBranch);

  if (changedFiles.length === 0) {
    return {
      affectedModules: [],
      success: true,
      results: [],
    };
  }

  // Determine affected modules
  const affectedModules = getAffectedModules(changedFiles);

  if (affectedModules.length === 0) {
    return {
      affectedModules: [],
      success: true,
      results: [],
    };
  }

  // Run tests for each affected module
  const results: Array<{ module: ModuleName; result: TestResult }> = [];
  let allSuccess = true;

  for (const module of affectedModules) {
    const result = await runTests(module, false);
    results.push({ module, result });

    if (!result.success) {
      allSuccess = false;
    }
  }

  return {
    affectedModules,
    success: allSuccess,
    results,
  };
}

// Tool definitions for MCP
export const buildTools = [
  {
    name: 'constellation_run_tests',
    description:
      'Run tests for all modules or a specific module. Returns test results with pass/fail counts and failure details.',
    inputSchema: {
      type: 'object' as const,
      properties: {
        module: {
          type: 'string',
          enum: ['all', 'core', 'runtime', 'parser', 'compiler', 'lsp', 'http', 'stdlib'],
          default: 'all',
          description: 'Module to test. Defaults to all.',
        },
        fastMode: {
          type: 'boolean',
          default: false,
          description: 'Skip recompilation (sbt testQuick)',
        },
      },
      required: [] as string[],
    },
    handler: async (args: { module?: ModuleName; fastMode?: boolean }) =>
      runTests(args.module ?? 'all', args.fastMode ?? false),
  },
  {
    name: 'constellation_get_test_status',
    description:
      'Get cached results from most recent test run without re-executing tests.',
    inputSchema: {
      type: 'object' as const,
      properties: {
        module: {
          type: 'string',
          enum: ['all', 'core', 'runtime', 'parser', 'compiler', 'lsp', 'http', 'stdlib'],
          default: 'all',
          description: 'Module to check. Defaults to all.',
        },
      },
      required: [] as string[],
    },
    handler: async (args: { module?: ModuleName }) =>
      getTestStatus(args.module ?? 'all'),
  },
  {
    name: 'constellation_get_build_status',
    description:
      'Check if project compiles. Returns compilation errors and warnings.',
    inputSchema: {
      type: 'object' as const,
      properties: {
        module: {
          type: 'string',
          enum: ['all', 'core', 'runtime', 'parser', 'compiler', 'lsp', 'http', 'stdlib'],
          default: 'all',
          description: 'Module to compile. Defaults to all.',
        },
      },
      required: [] as string[],
    },
    handler: async (args: { module?: ModuleName }) =>
      getBuildStatus(args.module ?? 'all'),
  },
  {
    name: 'constellation_run_affected_tests',
    description:
      'Run tests only for modules affected by changed files compared to base branch. Uses module dependency graph to include downstream dependents.',
    inputSchema: {
      type: 'object' as const,
      properties: {
        baseBranch: {
          type: 'string',
          default: 'master',
          description: 'Branch to compare against. Defaults to master.',
        },
      },
      required: [] as string[],
    },
    handler: async (args: { baseBranch?: string }) =>
      runAffectedTests(args.baseBranch ?? 'master'),
  },
];
