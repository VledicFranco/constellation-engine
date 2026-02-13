/**
 * In-memory ExecutorServerFactory for testing.
 *
 * Mirrors the Scala FakeExecutorServerFactory.
 */

import type {
  ExecutorServerFactory,
  ExecutorServer,
  ExecuteRequest,
  ExecuteResponse,
} from "../../src/transport/transport.js";

export class FakeExecutorServerFactory implements ExecutorServerFactory {
  handler: ((request: ExecuteRequest) => Promise<ExecuteResponse>) | null = null;
  started = false;
  stopped = false;

  async create(
    handler: (request: ExecuteRequest) => Promise<ExecuteResponse>,
    port: number,
  ): Promise<ExecutorServer> {
    this.handler = handler;
    this.started = true;
    return {
      port,
      stop: async () => {
        this.stopped = true;
      },
    };
  }

  /** Execute a request through the registered handler (for test assertions). */
  async execute(request: ExecuteRequest): Promise<ExecuteResponse> {
    if (!this.handler) {
      throw new Error("No handler registered");
    }
    return this.handler(request);
  }
}
