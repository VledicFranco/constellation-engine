/**
 * Path resolution and module mapping utilities
 */

import * as path from 'path';
import * as fs from 'fs';
import type { ModuleName } from '../types/index.js';

/**
 * Module dependency graph for affected test detection
 * If module A depends on module B, changes to B should trigger tests for A
 */
export const MODULE_DEPENDENCIES: Record<ModuleName, ModuleName[]> = {
  all: [],
  core: [],
  runtime: ['core'],
  parser: ['core'],
  compiler: ['core', 'parser'],
  stdlib: ['core', 'runtime', 'compiler'],
  lsp: ['core', 'compiler'],
  http: ['core', 'runtime', 'compiler', 'stdlib', 'lsp'],
};

/**
 * Map file paths to their corresponding module
 */
export const MODULE_PATH_PATTERNS: Record<ModuleName, RegExp[]> = {
  all: [],
  core: [/modules[\/\\]core[\/\\]/],
  runtime: [/modules[\/\\]runtime[\/\\]/],
  parser: [/modules[\/\\]lang-parser[\/\\]/, /modules[\/\\]lang-ast[\/\\]/],
  compiler: [/modules[\/\\]lang-compiler[\/\\]/],
  stdlib: [/modules[\/\\]lang-stdlib[\/\\]/],
  lsp: [/modules[\/\\]lang-lsp[\/\\]/],
  http: [/modules[\/\\]http-api[\/\\]/, /modules[\/\\]example-app[\/\\]/],
};

/**
 * SBT project names for each module
 */
export const MODULE_SBT_NAMES: Record<ModuleName, string> = {
  all: '',
  core: 'core',
  runtime: 'runtime',
  parser: 'lang-parser',
  compiler: 'lang-compiler',
  stdlib: 'lang-stdlib',
  lsp: 'lang-lsp',
  http: 'http-api',
};

/**
 * Detect which module a file belongs to
 */
export function getModuleForFile(filePath: string): ModuleName | null {
  const normalizedPath = path.normalize(filePath);

  for (const [module, patterns] of Object.entries(MODULE_PATH_PATTERNS)) {
    if (module === 'all') continue;
    for (const pattern of patterns) {
      if (pattern.test(normalizedPath)) {
        return module as ModuleName;
      }
    }
  }

  return null;
}

/**
 * Get all modules that depend on the given module (downstream dependents)
 */
export function getDependentModules(module: ModuleName): ModuleName[] {
  if (module === 'all') return [];

  const dependents: ModuleName[] = [];

  for (const [mod, deps] of Object.entries(MODULE_DEPENDENCIES)) {
    if (mod === 'all') continue;
    if (deps.includes(module)) {
      dependents.push(mod as ModuleName);
      // Recursively get dependents of dependents
      dependents.push(...getDependentModules(mod as ModuleName));
    }
  }

  // Remove duplicates
  return [...new Set(dependents)];
}

/**
 * Get all affected modules given a list of changed files
 */
export function getAffectedModules(changedFiles: string[]): ModuleName[] {
  const directlyAffected = new Set<ModuleName>();

  for (const file of changedFiles) {
    const module = getModuleForFile(file);
    if (module && module !== 'all') {
      directlyAffected.add(module);
    }
  }

  // Add downstream dependents
  const allAffected = new Set<ModuleName>(directlyAffected);
  for (const module of directlyAffected) {
    for (const dependent of getDependentModules(module)) {
      allAffected.add(dependent);
    }
  }

  return [...allAffected];
}

/**
 * Find the main repository root from current directory
 * Works from both main repo and worktrees
 */
export async function findMainRepoRoot(startDir?: string): Promise<string | null> {
  const dir = startDir ?? process.cwd();

  // Try to find .git directory
  let current = dir;
  while (current !== path.dirname(current)) {
    const gitPath = path.join(current, '.git');

    try {
      const stat = fs.statSync(gitPath);
      if (stat.isDirectory()) {
        // Regular git repo - this is the root
        return current;
      } else if (stat.isFile()) {
        // Worktree - read the file to find main repo
        const content = fs.readFileSync(gitPath, 'utf-8');
        const match = content.match(/gitdir:\s*(.+)/);
        if (match) {
          // The gitdir points to .git/worktrees/<name>
          // Navigate up to find the main repo
          const gitDir = path.resolve(current, match[1].trim());
          const mainGitDir = path.dirname(path.dirname(gitDir));
          return path.dirname(mainGitDir);
        }
      }
    } catch {
      // .git doesn't exist at this level, continue up
    }

    current = path.dirname(current);
  }

  return null;
}

/**
 * Get the path to the agents directory in the main repo
 */
export async function getAgentsDir(): Promise<string | null> {
  const mainRepo = await findMainRepoRoot();
  if (!mainRepo) return null;

  const agentsDir = path.join(mainRepo, 'agents');
  try {
    const stat = fs.statSync(agentsDir);
    if (stat.isDirectory()) {
      return agentsDir;
    }
  } catch {
    // agents directory doesn't exist
  }

  return null;
}

/**
 * Get the cache directory for MCP server data
 */
export function getCacheDir(): string {
  const cacheDir = path.join(process.cwd(), '.mcp-cache');
  if (!fs.existsSync(cacheDir)) {
    fs.mkdirSync(cacheDir, { recursive: true });
  }
  return cacheDir;
}

/**
 * Normalize path separators for cross-platform compatibility
 */
export function normalizePath(filePath: string): string {
  return path.normalize(filePath).replace(/\\/g, '/');
}
