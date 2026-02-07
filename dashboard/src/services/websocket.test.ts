/**
 * Constellation Dashboard - WebSocket Service Tests
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { useExecutionStore } from '../store/execution.store.js';
import type { ExecutionEvent } from './websocket.js';

// Mock WebSocket
class MockWebSocket {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;

  readyState = MockWebSocket.CONNECTING;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onerror: ((error: Event) => void) | null = null;
  onclose: ((event: CloseEvent) => void) | null = null;

  constructor(public url: string) {
    // Simulate async connection
    setTimeout(() => {
      this.readyState = MockWebSocket.OPEN;
      if (this.onopen) this.onopen();
    }, 10);
  }

  send(_data: string): void {
    // Mock send
  }

  close(): void {
    this.readyState = MockWebSocket.CLOSED;
    if (this.onclose) {
      this.onclose({ code: 1000, reason: 'Normal closure' } as CloseEvent);
    }
  }

  // Helper to simulate receiving a message
  simulateMessage(data: string): void {
    if (this.onmessage) {
      this.onmessage({ data });
    }
  }
}

// Store original WebSocket
const originalWebSocket = global.WebSocket;

describe('WebSocket Service', () => {
  beforeEach(() => {
    // Reset the execution store
    useExecutionStore.getState().reset();

    // Mock WebSocket globally
    (global as unknown as { WebSocket: typeof MockWebSocket }).WebSocket =
      MockWebSocket as unknown as typeof WebSocket;
  });

  afterEach(() => {
    // Restore original WebSocket
    (global as unknown as { WebSocket: typeof WebSocket }).WebSocket = originalWebSocket;
    vi.restoreAllMocks();
  });

  describe('ExecutionEvent interface', () => {
    it('should support execution:start event', () => {
      const event: ExecutionEvent = {
        type: 'execution:start',
        executionId: 'exec-123',
        dagName: 'TestDag',
        timestamp: Date.now(),
      };

      expect(event.type).toBe('execution:start');
      expect(event.executionId).toBe('exec-123');
    });

    it('should support module:start event', () => {
      const event: ExecutionEvent = {
        type: 'module:start',
        executionId: 'exec-123',
        moduleId: 'mod-456',
        moduleName: 'Uppercase',
        timestamp: Date.now(),
      };

      expect(event.type).toBe('module:start');
      expect(event.moduleId).toBe('mod-456');
      expect(event.moduleName).toBe('Uppercase');
    });

    it('should support module:complete event', () => {
      const event: ExecutionEvent = {
        type: 'module:complete',
        executionId: 'exec-123',
        moduleId: 'mod-456',
        moduleName: 'Uppercase',
        durationMs: 150,
        timestamp: Date.now(),
      };

      expect(event.type).toBe('module:complete');
      expect(event.durationMs).toBe(150);
    });

    it('should support module:failed event', () => {
      const event: ExecutionEvent = {
        type: 'module:failed',
        executionId: 'exec-123',
        moduleId: 'mod-456',
        moduleName: 'Uppercase',
        error: 'Something went wrong',
        timestamp: Date.now(),
      };

      expect(event.type).toBe('module:failed');
      expect(event.error).toBe('Something went wrong');
    });

    it('should support execution:complete event', () => {
      const event: ExecutionEvent = {
        type: 'execution:complete',
        executionId: 'exec-123',
        dagName: 'TestDag',
        succeeded: true,
        durationMs: 500,
        timestamp: Date.now(),
      };

      expect(event.type).toBe('execution:complete');
      expect(event.succeeded).toBe(true);
      expect(event.durationMs).toBe(500);
    });

    it('should support execution:cancelled event', () => {
      const event: ExecutionEvent = {
        type: 'execution:cancelled',
        executionId: 'exec-123',
        dagName: 'TestDag',
        timestamp: Date.now(),
      };

      expect(event.type).toBe('execution:cancelled');
    });
  });

  describe('Event serialization', () => {
    it('should correctly serialize and deserialize execution:start', () => {
      const event: ExecutionEvent = {
        type: 'execution:start',
        executionId: 'exec-123',
        dagName: 'TestDag',
        timestamp: 1234567890,
      };

      const json = JSON.stringify(event);
      const parsed = JSON.parse(json) as ExecutionEvent;

      expect(parsed.type).toBe('execution:start');
      expect(parsed.executionId).toBe('exec-123');
      expect(parsed.timestamp).toBe(1234567890);
    });

    it('should correctly serialize and deserialize module:complete with value', () => {
      const event: ExecutionEvent = {
        type: 'module:complete',
        executionId: 'exec-123',
        moduleId: 'mod-456',
        moduleName: 'Uppercase',
        value: { result: 'HELLO WORLD' },
        durationMs: 100,
        timestamp: 1234567890,
      };

      const json = JSON.stringify(event);
      const parsed = JSON.parse(json) as ExecutionEvent;

      expect(parsed.value).toEqual({ result: 'HELLO WORLD' });
    });
  });

  describe('Store integration', () => {
    it('should update store status on execution:start', () => {
      const store = useExecutionStore.getState();
      expect(store.status).toBe('idle');

      store.startExecution('exec-123');

      const updatedStore = useExecutionStore.getState();
      expect(updatedStore.status).toBe('running');
      expect(updatedStore.currentExecutionId).toBe('exec-123');
      expect(updatedStore.isRunning).toBe(true);
    });

    it('should update node state on module:start', () => {
      const store = useExecutionStore.getState();
      store.startExecution('exec-123');

      store.updateNodeState('mod-456', {
        status: 'running',
        startTime: Date.now(),
      });

      const updatedStore = useExecutionStore.getState();
      expect(updatedStore.nodeStates['mod-456'].status).toBe('running');
    });

    it('should update node state on module:complete', () => {
      const store = useExecutionStore.getState();
      store.startExecution('exec-123');
      store.updateNodeState('mod-456', { status: 'running' });

      store.updateNodeState('mod-456', {
        status: 'completed',
        value: 'test result',
        durationMs: 150,
      });

      const updatedStore = useExecutionStore.getState();
      expect(updatedStore.nodeStates['mod-456'].status).toBe('completed');
      expect(updatedStore.nodeStates['mod-456'].value).toBe('test result');
      expect(updatedStore.nodeStates['mod-456'].durationMs).toBe(150);
    });

    it('should update node state on module:failed', () => {
      const store = useExecutionStore.getState();
      store.startExecution('exec-123');
      store.updateNodeState('mod-456', { status: 'running' });

      store.updateNodeState('mod-456', {
        status: 'failed',
        error: 'Something went wrong',
        durationMs: 50,
      });

      const updatedStore = useExecutionStore.getState();
      expect(updatedStore.nodeStates['mod-456'].status).toBe('failed');
      expect(updatedStore.nodeStates['mod-456'].error).toBe('Something went wrong');
    });

    it('should complete execution on execution:complete with succeeded=true', () => {
      const store = useExecutionStore.getState();
      store.startExecution('exec-123');

      store.completeExecution(undefined, 500);

      const updatedStore = useExecutionStore.getState();
      expect(updatedStore.status).toBe('completed');
      expect(updatedStore.isRunning).toBe(false);
      expect(updatedStore.totalDurationMs).toBe(500);
    });

    it('should fail execution on execution:complete with succeeded=false', () => {
      const store = useExecutionStore.getState();
      store.startExecution('exec-123');

      store.failExecution('Execution failed');

      const updatedStore = useExecutionStore.getState();
      expect(updatedStore.status).toBe('failed');
      expect(updatedStore.isRunning).toBe(false);
      expect(updatedStore.error).toBe('Execution failed');
    });

    it('should reset store on reset()', () => {
      const store = useExecutionStore.getState();
      store.startExecution('exec-123');
      store.updateNodeState('mod-456', { status: 'completed' });

      store.reset();

      const updatedStore = useExecutionStore.getState();
      expect(updatedStore.status).toBe('idle');
      expect(updatedStore.currentExecutionId).toBeNull();
      expect(updatedStore.nodeStates).toEqual({});
      expect(updatedStore.isRunning).toBe(false);
    });

    it('should support batch node state updates', () => {
      const store = useExecutionStore.getState();
      store.startExecution('exec-123');

      store.batchUpdateNodeStates({
        'mod-1': { status: 'completed', durationMs: 100 },
        'mod-2': { status: 'running' },
        'mod-3': { status: 'pending' },
      });

      const updatedStore = useExecutionStore.getState();
      expect(updatedStore.nodeStates['mod-1'].status).toBe('completed');
      expect(updatedStore.nodeStates['mod-2'].status).toBe('running');
      expect(updatedStore.nodeStates['mod-3'].status).toBe('pending');
    });
  });
});
