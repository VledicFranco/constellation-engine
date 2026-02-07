/**
 * Constellation Dashboard - WebSocket Service
 *
 * Manages WebSocket connections for live execution visualization.
 * Subscribes to execution events and updates the execution store.
 */

import { useExecutionStore } from '../store/execution.store.js';
import type { NodeStatus } from '../store/execution.store.js';

export interface ExecutionEvent {
  type:
    | 'execution:start'
    | 'module:start'
    | 'module:complete'
    | 'module:failed'
    | 'execution:complete'
    | 'execution:cancelled';
  executionId: string;
  moduleId?: string;
  moduleName?: string;
  dagName?: string;
  timestamp: number;
  value?: unknown;
  error?: string;
  durationMs?: number;
  succeeded?: boolean;
}

export class ExecutionWebSocket {
  private ws: WebSocket | null = null;
  private executionId: string;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 3;
  private reconnectDelay = 1000;

  constructor(executionId: string) {
    this.executionId = executionId;
  }

  /**
   * Connect to the execution events WebSocket
   */
  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const url = `${protocol}//${window.location.host}/api/v1/executions/events?executionId=${encodeURIComponent(this.executionId)}`;

      this.ws = new WebSocket(url);

      this.ws.onopen = () => {
        console.log(`[WS] Connected to execution ${this.executionId}`);
        this.reconnectAttempts = 0;
        resolve();
      };

      this.ws.onmessage = (event) => {
        this.handleMessage(event.data);
      };

      this.ws.onerror = (error) => {
        console.error('[WS] Error:', error);
        reject(error);
      };

      this.ws.onclose = (event) => {
        console.log(`[WS] Closed: ${event.code} ${event.reason}`);
        this.handleClose();
      };
    });
  }

  /**
   * Handle incoming WebSocket messages
   */
  private handleMessage(data: string): void {
    try {
      const event: ExecutionEvent = JSON.parse(data);
      const store = useExecutionStore.getState();

      switch (event.type) {
        case 'execution:start':
          store.startExecution(event.executionId, event.dagName);
          break;

        case 'module:start':
          if (event.moduleId) {
            store.updateNodeState(event.moduleId, {
              status: 'running' as NodeStatus,
              startTime: event.timestamp,
            });
            // Store node name mapping
            if (event.moduleName) {
              const state = useExecutionStore.getState();
              useExecutionStore.setState({
                nodeNames: { ...state.nodeNames, [event.moduleId]: event.moduleName }
              });
            }
          }
          break;

        case 'module:complete':
          if (event.moduleId) {
            store.updateNodeState(event.moduleId, {
              status: 'completed' as NodeStatus,
              value: event.value,
              durationMs: event.durationMs,
            });
          }
          break;

        case 'module:failed':
          if (event.moduleId) {
            store.updateNodeState(event.moduleId, {
              status: 'failed' as NodeStatus,
              error: event.error,
              durationMs: event.durationMs,
            });
          }
          break;

        case 'execution:complete':
          if (event.succeeded) {
            store.completeExecution(undefined, event.durationMs);
          } else {
            store.failExecution(event.error || 'Execution failed');
          }
          break;

        case 'execution:cancelled':
          store.failExecution('Execution cancelled');
          break;
      }
    } catch (error) {
      console.error('[WS] Failed to parse message:', error);
    }
  }

  /**
   * Handle WebSocket close - attempt reconnection if appropriate
   */
  private handleClose(): void {
    const store = useExecutionStore.getState();

    // Don't reconnect if execution is complete or failed
    if (store.status === 'completed' || store.status === 'failed') {
      return;
    }

    // Attempt reconnection
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      console.log(
        `[WS] Attempting reconnection ${this.reconnectAttempts}/${this.maxReconnectAttempts}`
      );

      setTimeout(() => {
        this.connect().catch((error) => {
          console.error('[WS] Reconnection failed:', error);
        });
      }, this.reconnectDelay * this.reconnectAttempts);
    } else {
      console.warn('[WS] Max reconnection attempts reached');
    }
  }

  /**
   * Close the WebSocket connection
   */
  disconnect(): void {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  /**
   * Check if connected
   */
  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }
}

/**
 * Subscribe to execution events for live visualization
 */
export function subscribeToExecution(executionId: string): () => void {
  const ws = new ExecutionWebSocket(executionId);

  ws.connect().catch((error) => {
    console.error('Failed to connect to execution WebSocket:', error);
  });

  // Return cleanup function
  return () => {
    ws.disconnect();
  };
}

export default ExecutionWebSocket;
