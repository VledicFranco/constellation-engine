/**
 * Constellation Dashboard - API Service
 *
 * Typed API client for all backend endpoints.
 * Uses fetch with proper error handling and type safety.
 */

// Re-use types from existing type definitions
// These will be imported once we convert types.d.ts to a proper module

const BASE_URL = '/api/v1';

export class ApiError extends Error {
  constructor(
    public response: Response,
    public body?: unknown,
    message?: string
  ) {
    super(message || `API Error: ${response.status} ${response.statusText}`);
    this.name = 'ApiError';
  }
}

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    ...options,
  });

  if (!response.ok) {
    const body = await response.json().catch(() => undefined);
    throw new ApiError(response, body);
  }

  return response.json();
}

// ============================================================================
// Files API
// ============================================================================

export interface FileNode {
  name: string;
  path: string;
  fileType: 'file' | 'directory';
  size?: number;
  modifiedTime?: number;
  children?: FileNode[];
}

export interface FilesResponse {
  root: string;
  files: FileNode[];
}

export interface FileContentResponse {
  path: string;
  name: string;
  content: string;
  inputs: InputParam[];
  outputs: OutputParam[];
  lastModified?: number;
}

export interface InputParam {
  name: string;
  paramType: string;
  required: boolean;
  defaultValue?: unknown;
}

export interface OutputParam {
  name: string;
  paramType: string;
}

// ============================================================================
// Preview/Compile API
// ============================================================================

export interface DagVizIR {
  nodes: VizNode[];
  edges: VizEdge[];
  groups: NodeGroup[];
  metadata: VizMetadata;
}

export interface VizNode {
  id: string;
  kind: string;
  label: string;
  typeSignature: string;
  position?: { x: number; y: number };
  executionState?: {
    status: string;
    value?: unknown;
    durationMs?: number;
    error?: string;
  };
}

export interface VizEdge {
  id: string;
  source: string;
  target: string;
  label?: string;
  kind: string;
}

export interface NodeGroup {
  id: string;
  label: string;
  nodeIds: string[];
  collapsed: boolean;
}

export interface VizMetadata {
  title?: string;
  layoutDirection: string;
  bounds?: {
    minX: number;
    minY: number;
    maxX: number;
    maxY: number;
  };
}

export interface PreviewResponse {
  success: boolean;
  dagVizIR?: DagVizIR;
  errors: string[];
}

// ============================================================================
// Execute API
// ============================================================================

export interface ExecuteRequest {
  scriptPath?: string;
  ref?: string;
  inputs: Record<string, unknown>;
  source?: string;
  sampleRate?: number;
}

export interface ExecuteResponse {
  success: boolean;
  executionId: string;
  outputs: Record<string, unknown>;
  error?: string;
  dashboardUrl?: string;
  durationMs?: number;
}

export interface ExecutionSummary {
  executionId: string;
  dagName: string;
  scriptPath?: string;
  startTime: number;
  endTime?: number;
  status: string;
  source: string;
  nodeCount: number;
  outputPreview?: string;
}

export interface StoredExecution {
  executionId: string;
  dagName: string;
  scriptPath?: string;
  startTime: number;
  endTime?: number;
  inputs: Record<string, unknown>;
  outputs?: Record<string, unknown>;
  status: string;
  nodeResults: Record<string, unknown>;
  dagVizIR?: DagVizIR;
  sampleRate: number;
  source: string;
  error?: string;
}

export interface ExecutionListResponse {
  executions: ExecutionSummary[];
  total: number;
  limit: number;
  offset: number;
}

// ============================================================================
// Modules API
// ============================================================================

export interface ModuleInfo {
  name: string;
  description: string;
  version: string;
  category?: string;
  inputs: Record<string, string>;
  outputs: Record<string, string>;
  examples?: string[];
}

export interface ModulesResponse {
  modules: ModuleInfo[];
}

// ============================================================================
// API Client
// ============================================================================

export const api = {
  // Files
  files: {
    list: () => fetchJson<FilesResponse>(`${BASE_URL}/files`),

    get: (path: string) =>
      fetchJson<FileContentResponse>(
        `${BASE_URL}/file?path=${encodeURIComponent(path)}`
      ),
  },

  // Preview/Compile
  preview: {
    compile: (source: string) =>
      fetchJson<PreviewResponse>(`${BASE_URL}/preview`, {
        method: 'POST',
        body: JSON.stringify({ source }),
      }),
  },

  // Execute
  execute: {
    run: (scriptPath: string, inputs: Record<string, unknown>) =>
      fetchJson<ExecuteResponse>(`${BASE_URL}/execute`, {
        method: 'POST',
        body: JSON.stringify({ scriptPath, inputs, source: 'dashboard' }),
      }),

    runByRef: (ref: string, inputs: Record<string, unknown>) =>
      fetchJson<ExecuteResponse>('/execute', {
        method: 'POST',
        body: JSON.stringify({ ref, inputs }),
      }),

    get: (id: string) =>
      fetchJson<StoredExecution>(`${BASE_URL}/executions/${id}`),

    getDag: (id: string) =>
      fetchJson<DagVizIR>(`${BASE_URL}/executions/${id}/dag`),

    list: (limit = 50, scriptPath?: string) => {
      const params = new URLSearchParams({ limit: String(limit) });
      if (scriptPath) params.set('script', scriptPath);
      return fetchJson<ExecutionListResponse>(
        `${BASE_URL}/executions?${params}`
      );
    },
  },

  // Modules
  modules: {
    list: () => fetchJson<ModulesResponse>(`${BASE_URL}/modules`),
  },

  // Health
  health: {
    check: () => fetchJson<{ status: string }>('/health'),
    live: () => fetchJson<{ status: string }>('/health/live'),
    ready: () => fetchJson<{ status: string }>('/health/ready'),
  },
};

export default api;
