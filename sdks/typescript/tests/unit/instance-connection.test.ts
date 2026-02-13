import { describe, it, expect, beforeEach } from "vitest";
import { InstanceConnection } from "../../src/provider/instance-connection.js";
import { ConnectionState } from "../../src/types/connection.js";
import { FakeProviderTransport } from "../helpers/fake-provider-transport.js";
import { CTypes } from "../../src/types/ctype.js";
import { CValues } from "../../src/types/cvalue.js";
import { DEFAULT_SDK_CONFIG } from "../../src/types/config.js";
import type { ModuleDefinition } from "../../src/types/module-definition.js";

const testModule: ModuleDefinition = {
  name: "TestModule",
  inputType: CTypes.product({ text: CTypes.string() }),
  outputType: CTypes.product({ result: CTypes.string() }),
  version: "1.0.0",
  description: "A test module",
  handler: async (_input) => CValues.string("ok"),
};

describe("InstanceConnection", () => {
  let transport: FakeProviderTransport;
  let conn: InstanceConnection;

  beforeEach(() => {
    transport = new FakeProviderTransport();
    conn = new InstanceConnection(
      "localhost:9090",
      "test.ns",
      transport,
      DEFAULT_SDK_CONFIG,
      [testModule],
      "localhost:9091",
    );
  });

  it("should start in Disconnected state", () => {
    expect(conn.currentState).toBe(ConnectionState.Disconnected);
    expect(conn.isHealthy).toBe(false);
  });

  it("should transition to Active on successful connect", async () => {
    await conn.connect();
    expect(conn.currentState).toBe(ConnectionState.Active);
    expect(conn.isHealthy).toBe(true);
    expect(conn.connectionId).toBe("fake-conn-id");
  });

  it("should send register request with correct fields", async () => {
    await conn.connect();
    expect(transport.registerCalls).toHaveLength(1);
    const req = transport.registerCalls[0];
    expect(req.namespace).toBe("test.ns");
    expect(req.protocolVersion).toBe(1);
    expect(req.modules).toHaveLength(1);
    expect(req.modules[0].name).toBe("TestModule");
  });

  it("should use provider executor URL, not instance address", async () => {
    await conn.connect();
    const req = transport.registerCalls[0];
    expect(req.executorUrl).toBe("localhost:9091");
    expect(req.executorUrl).not.toContain("9090");
  });

  it("should be idempotent when already Active", async () => {
    await conn.connect();
    await conn.connect(); // No-op
    expect(transport.registerCalls).toHaveLength(1);
  });

  it("should transition to Disconnected on failed registration", async () => {
    transport.setRegisterResponse({
      success: false,
      results: [{ moduleName: "TestModule", accepted: false, rejectionReason: "conflict" }],
      protocolVersion: 1,
      connectionId: "",
    });

    await expect(conn.connect()).rejects.toThrow("Registration failed");
    expect(conn.currentState).toBe(ConnectionState.Disconnected);
  });

  it("should transition to Disconnected on transport error", async () => {
    const errorTransport: FakeProviderTransport = new FakeProviderTransport();
    errorTransport.register = async () => {
      throw new Error("network error");
    };
    const errorConn = new InstanceConnection(
      "localhost:9090",
      "test.ns",
      errorTransport,
      DEFAULT_SDK_CONFIG,
      [testModule],
      "localhost:9091",
    );

    await expect(errorConn.connect()).rejects.toThrow("network error");
    expect(errorConn.currentState).toBe(ConnectionState.Disconnected);
  });

  it("should disconnect after connect", async () => {
    await conn.connect();
    await conn.disconnect();
    expect(conn.currentState).toBe(ConnectionState.Disconnected);
    expect(conn.connectionId).toBeUndefined();
    expect(transport.deregisterCalls).toHaveLength(1);
    expect(transport.deregisterCalls[0].connectionId).toBe("fake-conn-id");
  });

  it("should be no-op when disconnecting from Disconnected", async () => {
    await conn.disconnect();
    expect(transport.deregisterCalls).toHaveLength(0);
  });

  it("should handle deregister errors gracefully", async () => {
    await conn.connect();
    transport.deregister = async () => {
      throw new Error("deregister failed");
    };
    await conn.disconnect(); // Should not throw
    expect(conn.currentState).toBe(ConnectionState.Disconnected);
  });

  it("should replace modules", async () => {
    const newModule: ModuleDefinition = {
      ...testModule,
      name: "NewModule",
    };
    conn.replaceModules([newModule]);
    expect(conn.modules).toHaveLength(1);
    expect(conn.modules[0].name).toBe("NewModule");
  });

  it("should include groupId in register request when configured", async () => {
    const groupTransport = new FakeProviderTransport();
    const configWithGroup = {
      ...DEFAULT_SDK_CONFIG,
      groupId: "my-group-1",
    };
    const groupConn = new InstanceConnection(
      "localhost:9090",
      "test.ns",
      groupTransport,
      configWithGroup,
      [testModule],
      "localhost:9091",
    );

    await groupConn.connect();
    expect(groupTransport.registerCalls).toHaveLength(1);
    expect(groupTransport.registerCalls[0].groupId).toBe("my-group-1");
  });
});
