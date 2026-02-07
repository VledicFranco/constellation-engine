/**
 * Constellation Dashboard - Execution Store
 *
 * Manages execution state for live visualization, including per-node status,
 * values, and timing information streamed via WebSocket.
 */

import { create } from 'zustand';

export type NodeStatus = 'pending' | 'running' | 'completed' | 'failed';

export interface NodeState {
  status: NodeStatus;
  value?: unknown;
  error?: string;
  durationMs?: number;
  startTime?: number;
}

export interface ExecutionState {
  // Current execution ID (if any)
  currentExecutionId: string | null;

  // DAG name for the current execution
  dagName: string | null;

  // Execution start timestamp
  executionStartTime: number | null;

  // Per-node execution state
  nodeStates: Record<string, NodeState>;

  // Node name mapping (nodeId -> nodeName)
  nodeNames: Record<string, string>;

  // Whether execution is in progress
  isRunning: boolean;

  // Overall execution status
  status: 'idle' | 'running' | 'completed' | 'failed';

  // Error message (if execution failed)
  error?: string;

  // Total execution time in ms
  totalDurationMs?: number;

  // Actions
  startExecution: (id: string, dagName?: string) => void;
  updateNodeState: (nodeId: string, state: Partial<NodeState>) => void;
  batchUpdateNodeStates: (updates: Record<string, Partial<NodeState>>) => void;
  completeExecution: (outputs?: Record<string, unknown>, durationMs?: number) => void;
  failExecution: (error: string) => void;
  reset: () => void;
}

export const useExecutionStore = create<ExecutionState>((set, _get) => ({
  // Initial state
  currentExecutionId: null,
  dagName: null,
  executionStartTime: null,
  nodeStates: {},
  nodeNames: {},
  isRunning: false,
  status: 'idle',
  error: undefined,
  totalDurationMs: undefined,

  // Actions
  startExecution: (id, dagName) =>
    set({
      currentExecutionId: id,
      dagName: dagName || null,
      executionStartTime: Date.now(),
      nodeStates: {},
      nodeNames: {},
      isRunning: true,
      status: 'running',
      error: undefined,
      totalDurationMs: undefined,
    }),

  updateNodeState: (nodeId, state) =>
    set((prev) => ({
      nodeStates: {
        ...prev.nodeStates,
        [nodeId]: {
          ...prev.nodeStates[nodeId],
          ...state,
        },
      },
    })),

  batchUpdateNodeStates: (updates) =>
    set((prev) => {
      const newNodeStates = { ...prev.nodeStates };
      for (const [nodeId, state] of Object.entries(updates)) {
        newNodeStates[nodeId] = {
          ...newNodeStates[nodeId],
          ...state,
        };
      }
      return { nodeStates: newNodeStates };
    }),

  completeExecution: (_outputs, durationMs) =>
    set({
      isRunning: false,
      status: 'completed',
      totalDurationMs: durationMs,
    }),

  failExecution: (error) =>
    set({
      isRunning: false,
      status: 'failed',
      error,
    }),

  reset: () =>
    set({
      currentExecutionId: null,
      dagName: null,
      executionStartTime: null,
      nodeStates: {},
      nodeNames: {},
      isRunning: false,
      status: 'idle',
      error: undefined,
      totalDurationMs: undefined,
    }),
}));

// Helper to build ExecutionProfile from store state
export function buildExecutionProfile(state: ExecutionState): import('../components/ProfileView.js').ExecutionProfile | null {
  if (!state.currentExecutionId || !state.executionStartTime) return null;

  const nodes = Object.entries(state.nodeStates)
    .filter(([_, nodeState]) => nodeState.startTime !== undefined)
    .map(([nodeId, nodeState]) => ({
      nodeId,
      nodeName: state.nodeNames[nodeId] || nodeId,
      startTime: (nodeState.startTime || 0) - (state.executionStartTime || 0),
      endTime: (nodeState.startTime || 0) + (nodeState.durationMs || 0) - (state.executionStartTime || 0),
      durationMs: nodeState.durationMs || 0,
      status: (nodeState.status === 'completed' || nodeState.status === 'failed' ? nodeState.status : 'completed') as 'completed' | 'failed',
    }));

  return {
    executionId: state.currentExecutionId,
    dagName: state.dagName || 'Unknown',
    startTime: state.executionStartTime,
    totalDurationMs: state.totalDurationMs || 0,
    nodes,
  };
}
