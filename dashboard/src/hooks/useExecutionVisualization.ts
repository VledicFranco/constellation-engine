/**
 * Constellation Dashboard - Execution Visualization Hook
 *
 * Bridges the Zustand execution store to the DAG visualizer
 * for live execution visualization.
 */

import { useEffect } from 'react';
import { useExecutionStore, NodeState } from '../store/execution.store.js';
import { subscribeToExecution } from '../services/websocket.js';

/**
 * Hook to connect execution store to DAG visualizer
 */
export function useExecutionVisualization(
  dagVisualizer: DagVisualizerInterface | null,
  executionId: string | null
): void {
  // Subscribe to execution events via WebSocket
  useEffect(() => {
    if (!executionId) return;

    const unsubscribe = subscribeToExecution(executionId);
    return () => {
      unsubscribe();
    };
  }, [executionId]);

  // Sync node states from store to DAG visualizer
  useEffect(() => {
    if (!dagVisualizer) return;

    // Subscribe to store changes
    const unsubscribe = useExecutionStore.subscribe((state) => {
      // Update each node state in the DAG visualizer
      for (const [nodeId, nodeState] of Object.entries(state.nodeStates)) {
        dagVisualizer.updateExecutionState(nodeId, {
          status: capitalizeStatus(nodeState.status),
          value: nodeState.value,
          durationMs: nodeState.durationMs,
          error: nodeState.error,
        });
      }
    });

    return () => {
      unsubscribe();
    };
  }, [dagVisualizer]);
}

/**
 * Capitalize status for DAG visualizer (Pending, Running, Completed, Failed)
 */
function capitalizeStatus(status: NodeState['status']): string {
  return status.charAt(0).toUpperCase() + status.slice(1);
}

/**
 * Interface for DAG visualizer (minimal subset needed)
 */
interface DagVisualizerInterface {
  updateExecutionState(nodeId: string, state: {
    status: string;
    value?: unknown;
    durationMs?: number;
    error?: string;
  }): void;
  resetExecutionStates(): void;
}

/**
 * Start execution and connect visualization
 *
 * Call this when starting script execution to set up the live visualization pipeline.
 */
export function startExecutionWithVisualization(
  executionId: string,
  dagVisualizer: DagVisualizerInterface | null
): () => void {
  // Initialize execution in store
  const store = useExecutionStore.getState();
  store.startExecution(executionId);

  // Reset DAG visualizer states
  if (dagVisualizer) {
    dagVisualizer.resetExecutionStates();
  }

  // Subscribe to WebSocket events
  const unsubscribe = subscribeToExecution(executionId);

  // Set up store-to-visualizer sync
  let lastNodeStates: Record<string, NodeState> = {};
  const storeUnsubscribe = useExecutionStore.subscribe((state) => {
    if (!dagVisualizer) return;

    // Only update changed nodes
    for (const [nodeId, nodeState] of Object.entries(state.nodeStates)) {
      const lastState = lastNodeStates[nodeId];
      if (!lastState || lastState.status !== nodeState.status || lastState.value !== nodeState.value) {
        dagVisualizer.updateExecutionState(nodeId, {
          status: capitalizeStatus(nodeState.status),
          value: nodeState.value,
          durationMs: nodeState.durationMs,
          error: nodeState.error,
        });
      }
    }
    lastNodeStates = { ...state.nodeStates };
  });

  // Return cleanup function
  return () => {
    unsubscribe();
    storeUnsubscribe();
  };
}

export default useExecutionVisualization;
