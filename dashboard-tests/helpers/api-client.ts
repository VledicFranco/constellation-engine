import { APIRequestContext } from '@playwright/test';

/**
 * REST API client for direct server interaction during tests.
 * Used for test setup/teardown and backend assertions.
 */
export class ApiClient {
  constructor(private request: APIRequestContext) {}

  /** Get the list of available .cst files */
  async getFiles(): Promise<any> {
    const res = await this.request.get('/api/v1/files');
    return res.json();
  }

  /** Get the content and metadata for a specific file */
  async getFileContent(path: string): Promise<any> {
    const res = await this.request.get(`/api/v1/file?path=${encodeURIComponent(path)}`);
    return res.json();
  }

  /** Preview/compile a script source for DAG visualization */
  async previewScript(source: string): Promise<any> {
    const res = await this.request.post('/api/v1/preview', {
      data: { source }
    });
    return res.json();
  }

  /** Execute a script with given inputs */
  async executeScript(scriptPath: string, inputs: Record<string, unknown>): Promise<any> {
    const res = await this.request.post('/api/v1/execute', {
      data: { scriptPath, inputs, source: 'dashboard' }
    });
    return res.json();
  }

  /** Get execution history */
  async getExecutions(limit = 50): Promise<any> {
    const res = await this.request.get(`/api/v1/executions?limit=${limit}`);
    return res.json();
  }

  /** Get a specific execution by ID */
  async getExecution(id: string): Promise<any> {
    const res = await this.request.get(`/api/v1/executions/${id}`);
    return res.json();
  }

  /** Delete an execution by ID */
  async deleteExecution(id: string): Promise<any> {
    const res = await this.request.delete(`/api/v1/executions/${id}`);
    return res.json();
  }

  /** Get dashboard status */
  async getStatus(): Promise<any> {
    const res = await this.request.get('/api/v1/status');
    return res.json();
  }

  /** Check server health */
  async checkHealth(): Promise<boolean> {
    try {
      const res = await this.request.get('/health');
      return res.ok();
    } catch {
      return false;
    }
  }
}
