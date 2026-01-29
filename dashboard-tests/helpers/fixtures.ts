import { request } from '@playwright/test';

/**
 * Known fixture script paths available on the server.
 * These correspond to files in modules/example-app/examples/
 */
export const FIXTURE_SCRIPTS = {
  /** Simple uppercase transformation: in message: String -> Uppercase -> out */
  SIMPLE_TEST: 'simple-test.cst',

  /** Multi-step text analysis: in document: String -> multiple outputs */
  TEXT_ANALYSIS: 'text-analysis.cst',

  /** Numeric data pipeline: multiple typed inputs (List<Int>, Int) */
  DATA_PIPELINE: 'data-pipeline.cst',

  /** Branch expressions demo */
  BRANCH_EXPRESSIONS: 'branch-expressions.cst',
} as const;

/**
 * Dynamically find the first available .cst file via the API.
 * Useful as a fallback when specific fixture paths may vary.
 */
export async function getFirstAvailableScript(apiBaseUrl: string): Promise<string | null> {
  const ctx = await request.newContext({ baseURL: apiBaseUrl });
  try {
    const res = await ctx.get('/api/v1/files');
    if (!res.ok()) return null;
    const data = await res.json();
    const files: any[] = data.files ?? [];
    // Find the first .cst file (could be nested in folders)
    const findCst = (items: any[]): string | null => {
      for (const item of items) {
        if (item.type === 'file' && item.name?.endsWith('.cst')) {
          return item.path ?? item.name;
        }
        if (item.children) {
          const found = findCst(item.children);
          if (found) return found;
        }
      }
      return null;
    };
    return findCst(files);
  } catch {
    return null;
  } finally {
    await ctx.dispose();
  }
}
