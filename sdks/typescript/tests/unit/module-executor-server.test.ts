import { describe, it, expect, beforeEach } from "vitest";
import { ModuleExecutorServer } from "../../src/provider/module-executor-server.js";
import { JsonCValueSerializer } from "../../src/serialization/cvalue-serializer.js";
import { CTypes } from "../../src/types/ctype.js";
import { CValues } from "../../src/types/cvalue.js";
import type { ModuleDefinition } from "../../src/types/module-definition.js";
import type { ExecuteRequest } from "../../src/transport/transport.js";

const echoModule: ModuleDefinition = {
  name: "Echo",
  inputType: CTypes.product({ text: CTypes.string() }),
  outputType: CTypes.product({ result: CTypes.string() }),
  version: "1.0.0",
  description: "Echoes input",
  handler: async (input) => input,
};

const errorModule: ModuleDefinition = {
  name: "Faulty",
  inputType: CTypes.string(),
  outputType: CTypes.string(),
  version: "1.0.0",
  description: "Always throws",
  handler: async () => {
    throw new Error("module crashed");
  },
};

function makeRequest(moduleName: string, input: unknown): ExecuteRequest {
  const bytes = JsonCValueSerializer.serialize(input as any);
  return {
    moduleName,
    inputData: bytes,
    executionId: "test-exec-1",
    metadata: {},
  };
}

describe("ModuleExecutorServer", () => {
  let server: ModuleExecutorServer;

  beforeEach(() => {
    server = new ModuleExecutorServer([echoModule, errorModule], JsonCValueSerializer);
  });

  it("should dispatch to correct module and return output", async () => {
    const input = CValues.product({ text: CValues.string("hello") }, { text: CTypes.string() });
    const request = makeRequest("Echo", input);
    const response = await server.handleRequest(request);

    expect(response.result.case).toBe("outputData");
    expect(response.metrics).toBeDefined();
    expect(response.metrics?.durationMs).toBeGreaterThanOrEqual(0);

    if (response.result.case === "outputData") {
      const output = JsonCValueSerializer.deserialize(response.result.value);
      expect(output).toEqual(input);
    }
  });

  it("should return MODULE_NOT_FOUND for unknown module", async () => {
    const request = makeRequest("Unknown", CValues.string("test"));
    const response = await server.handleRequest(request);

    expect(response.result.case).toBe("error");
    if (response.result.case === "error") {
      expect(response.result.value.code).toBe("MODULE_NOT_FOUND");
      expect(response.result.value.message).toContain("Unknown");
    }
  });

  it("should return TYPE_ERROR for malformed input", async () => {
    const request: ExecuteRequest = {
      moduleName: "Echo",
      inputData: new TextEncoder().encode("not valid json"),
      executionId: "test-exec-2",
      metadata: {},
    };
    const response = await server.handleRequest(request);

    expect(response.result.case).toBe("error");
    if (response.result.case === "error") {
      expect(response.result.value.code).toBe("TYPE_ERROR");
      expect(response.result.value.message).toContain("deserialize");
    }
  });

  it("should return RUNTIME_ERROR when handler throws", async () => {
    const request = makeRequest("Faulty", CValues.string("test"));
    const response = await server.handleRequest(request);

    expect(response.result.case).toBe("error");
    if (response.result.case === "error") {
      expect(response.result.value.code).toBe("RUNTIME_ERROR");
      expect(response.result.value.message).toContain("module crashed");
    }
  });

  it("should include metrics in all responses", async () => {
    const request = makeRequest("Echo", CValues.string("test"));
    const response = await server.handleRequest(request);
    expect(response.metrics).toBeDefined();
    expect(response.metrics?.durationMs).toBeGreaterThanOrEqual(0);
  });

  it("should support hot-swap via setModules", async () => {
    const newModule: ModuleDefinition = {
      name: "NewModule",
      inputType: CTypes.string(),
      outputType: CTypes.string(),
      version: "2.0.0",
      description: "New module",
      handler: async () => CValues.string("new"),
    };

    server.setModules([newModule]);

    // Old module should not be found
    const oldRequest = makeRequest("Echo", CValues.string("test"));
    const oldResponse = await server.handleRequest(oldRequest);
    expect(oldResponse.result.case).toBe("error");
    if (oldResponse.result.case === "error") {
      expect(oldResponse.result.value.code).toBe("MODULE_NOT_FOUND");
    }

    // New module should work
    const newRequest = makeRequest("NewModule", CValues.string("test"));
    const newResponse = await server.handleRequest(newRequest);
    expect(newResponse.result.case).toBe("outputData");
  });

  it("toHandler should return a callable function", async () => {
    const handler = server.toHandler();
    const request = makeRequest("Echo", CValues.string("test"));
    const response = await handler(request);
    expect(response.result.case).toBe("outputData");
  });
});
