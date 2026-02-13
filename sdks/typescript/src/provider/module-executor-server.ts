/**
 * Dispatches incoming ExecuteRequests to the correct ModuleDefinition handler.
 *
 * Mirrors the Scala ModuleExecutorServer class.
 */

import type { ModuleDefinition } from "../types/module-definition.js";
import type { CValueSerializer } from "../serialization/cvalue-serializer.js";
import type { ExecuteRequest, ExecuteResponse } from "../transport/transport.js";

export class ModuleExecutorServer {
  private modules: ModuleDefinition[];
  private readonly serializer: CValueSerializer;

  constructor(modules: ModuleDefinition[], serializer: CValueSerializer) {
    this.modules = [...modules];
    this.serializer = serializer;
  }

  /** Update the module list (for canary rollout). */
  setModules(modules: ModuleDefinition[]): void {
    this.modules = [...modules];
  }

  /** Handle an ExecuteRequest by dispatching to the appropriate module. */
  async handleRequest(request: ExecuteRequest): Promise<ExecuteResponse> {
    const startTime = performance.now();
    try {
      const response = await this.dispatch(request);
      const durationMs = Math.round(performance.now() - startTime);
      return {
        ...response,
        metrics: { durationMs, memoryBytes: 0 },
      };
    } catch (error) {
      const durationMs = Math.round(performance.now() - startTime);
      const err = error instanceof Error ? error : new Error(String(error));
      return {
        result: {
          case: "error",
          value: {
            code: "RUNTIME_ERROR",
            message: err.message,
            stackTrace: err.stack?.split("\n").slice(0, 10).join("\n") ?? "",
          },
        },
        metrics: { durationMs, memoryBytes: 0 },
      };
    }
  }

  /** Convert this server into a request handler function for use with ExecutorServerFactory. */
  toHandler(): (request: ExecuteRequest) => Promise<ExecuteResponse> {
    return (request) => this.handleRequest(request);
  }

  private async dispatch(request: ExecuteRequest): Promise<ExecuteResponse> {
    const moduleDef = this.modules.find((m) => m.name === request.moduleName);
    if (!moduleDef) {
      return {
        result: {
          case: "error",
          value: {
            code: "MODULE_NOT_FOUND",
            message: `Module not found: ${request.moduleName}`,
            stackTrace: "",
          },
        },
      };
    }

    let inputCValue;
    try {
      inputCValue = this.serializer.deserialize(request.inputData);
    } catch (error) {
      const msg = error instanceof Error ? error.message : String(error);
      return {
        result: {
          case: "error",
          value: {
            code: "TYPE_ERROR",
            message: `Failed to deserialize input: ${msg}`,
            stackTrace: "",
          },
        },
      };
    }

    const outputCValue = await moduleDef.handler(inputCValue);

    let outputBytes;
    try {
      outputBytes = this.serializer.serialize(outputCValue);
    } catch (error) {
      const msg = error instanceof Error ? error.message : String(error);
      throw new Error(`Failed to serialize output: ${msg}`);
    }

    return {
      result: { case: "outputData", value: outputBytes },
    };
  }
}
