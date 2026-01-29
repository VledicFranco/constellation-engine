import { request } from '@playwright/test';

/**
 * Utility for verifying and interacting with the Constellation server.
 * When running locally, expects the server is already running.
 * In CI, playwright.config.ts starts the server via webServer option.
 */
export class ServerManager {
  constructor(private baseUrl: string) {}

  /** Check that the server is healthy */
  async waitForHealthy(timeoutMs = 30_000): Promise<void> {
    const ctx = await request.newContext({ baseURL: this.baseUrl });
    const deadline = Date.now() + timeoutMs;

    while (Date.now() < deadline) {
      try {
        const res = await ctx.get('/health');
        if (res.ok()) {
          await ctx.dispose();
          return;
        }
      } catch { /* retry */ }
      await new Promise(r => setTimeout(r, 500));
    }
    await ctx.dispose();
    throw new Error(`Server not healthy after ${timeoutMs}ms`);
  }

  /** Get the list of available .cst files */
  async getFiles(): Promise<unknown> {
    const ctx = await request.newContext({ baseURL: this.baseUrl });
    const res = await ctx.get('/api/v1/files');
    const data = await res.json();
    await ctx.dispose();
    return data;
  }

  /** Execute a script directly via API (for test setup) */
  async execute(scriptPath: string, inputs: Record<string, unknown>): Promise<unknown> {
    const ctx = await request.newContext({ baseURL: this.baseUrl });
    const res = await ctx.post('/api/v1/execute', {
      data: { scriptPath, inputs, source: 'test' }
    });
    const data = await res.json();
    await ctx.dispose();
    return data;
  }

  /** Get execution history (for assertions) */
  async getExecutions(limit = 10): Promise<unknown> {
    const ctx = await request.newContext({ baseURL: this.baseUrl });
    const res = await ctx.get(`/api/v1/executions?limit=${limit}`);
    const data = await res.json();
    await ctx.dispose();
    return data;
  }
}
