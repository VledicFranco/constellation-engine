/**
 * PerformanceTracker - Client-side performance instrumentation for the VS Code extension.
 *
 * Provides timing and statistics for LSP operations, panel rendering, and user interactions.
 * Logs slow operations (>500ms) to console as warnings.
 */

export interface OperationTimer {
  /** Ends the timer and returns elapsed time in milliseconds */
  end(): number;
}

export interface PerformanceStats {
  avg: number;
  p95: number;
  max: number;
  count: number;
}

export class PerformanceTracker {
  private static instance: PerformanceTracker;
  private metrics: Map<string, number[]> = new Map();
  private readonly maxSamples = 100;
  private readonly slowThresholdMs = 500;

  private constructor() {
    // Private constructor for singleton pattern
  }

  static getInstance(): PerformanceTracker {
    if (!PerformanceTracker.instance) {
      PerformanceTracker.instance = new PerformanceTracker();
    }
    return PerformanceTracker.instance;
  }

  /**
   * Starts timing an operation. Call end() on the returned timer to record the duration.
   * @param name The operation name (use Operations constants for consistency)
   * @returns An OperationTimer that records the duration when end() is called
   */
  startOperation(name: string): OperationTimer {
    const start = performance.now();
    return {
      end: () => {
        const elapsed = performance.now() - start;
        this.recordLatency(name, elapsed);
        return elapsed;
      }
    };
  }

  /**
   * Records a latency measurement for an operation.
   * Logs a warning if the operation exceeds the slow threshold.
   * @param operation The operation name
   * @param ms The duration in milliseconds
   */
  recordLatency(operation: string, ms: number): void {
    if (!this.metrics.has(operation)) {
      this.metrics.set(operation, []);
    }
    const arr = this.metrics.get(operation)!;
    arr.push(ms);

    // Keep only the most recent samples
    if (arr.length > this.maxSamples) {
      arr.shift();
    }

    // Log slow operations
    if (ms > this.slowThresholdMs) {
      console.warn(`[PERF] Slow operation: ${operation} took ${ms.toFixed(1)}ms`);
    }
  }

  /**
   * Gets statistics for a specific operation.
   * @param operation The operation name
   * @returns Statistics including avg, p95, max, and count, or null if no data
   */
  getStats(operation: string): PerformanceStats | null {
    const samples = this.metrics.get(operation);
    if (!samples || samples.length === 0) {
      return null;
    }
    const sorted = [...samples].sort((a, b) => a - b);
    return {
      avg: samples.reduce((a, b) => a + b) / samples.length,
      p95: sorted[Math.floor(sorted.length * 0.95)],
      max: sorted[sorted.length - 1],
      count: samples.length
    };
  }

  /**
   * Gets statistics for all tracked operations.
   * @returns A record mapping operation names to their statistics
   */
  getAllStats(): Record<string, PerformanceStats> {
    const result: Record<string, PerformanceStats> = {};
    for (const [op] of this.metrics) {
      const stats = this.getStats(op);
      if (stats) {
        result[op] = stats;
      }
    }
    return result;
  }

  /**
   * Clears all recorded metrics.
   */
  reset(): void {
    this.metrics.clear();
  }

  /**
   * Logs all current statistics to the console.
   */
  logStats(): void {
    const stats = this.getAllStats();
    if (Object.keys(stats).length === 0) {
      console.log('[PERF] No performance data collected');
      return;
    }
    console.log('[PERF] Performance Statistics:');
    for (const [op, s] of Object.entries(stats)) {
      console.log(`  ${op}: avg=${s.avg.toFixed(1)}ms, p95=${s.p95.toFixed(1)}ms, max=${s.max.toFixed(1)}ms, count=${s.count}`);
    }
  }
}

/**
 * Predefined operation names for consistency across the extension.
 * Use these constants when calling startOperation() or recordLatency().
 */
export const Operations = {
  // LSP Operations
  LSP_REQUEST: 'lsp.request',
  LSP_COMPLETION: 'lsp.completion',
  LSP_HOVER: 'lsp.hover',
  LSP_DIAGNOSTICS: 'lsp.diagnostics',
  LSP_GET_DAG: 'lsp.getDag',
  LSP_EXECUTE: 'lsp.execute',
  LSP_STEP_START: 'lsp.stepStart',
  LSP_STEP_NEXT: 'lsp.stepNext',

  // DAG Visualization
  DAG_REFRESH: 'dag.refresh',
  DAG_RENDER: 'dag.render',

  // Script Execution
  EXECUTION_FULL: 'execution.full',
  EXECUTION_STEP: 'execution.step',

  // Panel Operations
  PANEL_CREATE: 'panel.create',

  // WebSocket
  WS_ROUNDTRIP: 'ws.roundtrip'
} as const;
