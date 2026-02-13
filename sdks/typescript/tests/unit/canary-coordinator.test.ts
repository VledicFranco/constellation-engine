import { describe, it, expect } from "vitest";
import { CanaryCoordinator } from "../../src/provider/canary-coordinator.js";
import { InstanceConnection } from "../../src/provider/instance-connection.js";
import { FakeProviderTransport } from "../helpers/fake-provider-transport.js";
import { CTypes } from "../../src/types/ctype.js";
import { CValues } from "../../src/types/cvalue.js";
import { DEFAULT_SDK_CONFIG } from "../../src/types/config.js";
import type { CanaryConfig } from "../../src/types/config.js";
import type { ModuleDefinition } from "../../src/types/module-definition.js";

const oldModule: ModuleDefinition = {
  name: "OldModule",
  inputType: CTypes.string(),
  outputType: CTypes.string(),
  version: "1.0.0",
  description: "Old",
  handler: async () => CValues.string("old"),
};

const newModule: ModuleDefinition = {
  name: "NewModule",
  inputType: CTypes.string(),
  outputType: CTypes.string(),
  version: "2.0.0",
  description: "New",
  handler: async () => CValues.string("new"),
};

// Use a very short observation window for tests
const testCanaryConfig: CanaryConfig = {
  observationWindowMs: 1,
  healthThreshold: 0.95,
  maxLatencyMs: 5000,
  rollbackOnFailure: true,
};

async function createConnection(
  address: string,
  connectFirst = true,
): Promise<{ conn: InstanceConnection; transport: FakeProviderTransport }> {
  const transport = new FakeProviderTransport();
  const conn = new InstanceConnection(
    address,
    "test.ns",
    transport,
    DEFAULT_SDK_CONFIG,
    [oldModule],
    "localhost:9091",
  );
  if (connectFirst) {
    await conn.connect();
  }
  return { conn, transport };
}

describe("CanaryCoordinator", () => {
  it("should return Promoted for empty connections", async () => {
    const coordinator = new CanaryCoordinator(new Map(), testCanaryConfig);
    const result = await coordinator.rollout([oldModule], [newModule]);
    expect(result.status).toBe("Promoted");
  });

  it("should promote when all instances are healthy", async () => {
    const { conn: conn1 } = await createConnection("host1:9090");
    const { conn: conn2 } = await createConnection("host2:9090");

    const connections = new Map([
      ["host1", conn1],
      ["host2", conn2],
    ]);

    const coordinator = new CanaryCoordinator(connections, testCanaryConfig);
    const result = await coordinator.rollout([oldModule], [newModule]);
    expect(result.status).toBe("Promoted");
  });

  it("should rollback when a disconnected instance is encountered", async () => {
    const { conn: conn1 } = await createConnection("host1:9090");
    const { conn: conn2 } = await createConnection("host2:9090", false); // not connected

    const connections = new Map([
      ["host1", conn1],
      ["host2", conn2],
    ]);

    const coordinator = new CanaryCoordinator(connections, testCanaryConfig);
    const result = await coordinator.rollout([oldModule], [newModule]);
    expect(result.status).toBe("RolledBack");
    if (result.status === "RolledBack") {
      expect(result.reason).toContain("host2");
    }
  });

  it("should rollback previously upgraded instances on failure", async () => {
    const { conn: conn1 } = await createConnection("host1:9090");
    const { conn: conn2 } = await createConnection("host2:9090", false); // disconnected = unhealthy

    const connections = new Map([
      ["host1", conn1],
      ["host2", conn2],
    ]);

    const coordinator = new CanaryCoordinator(connections, testCanaryConfig);
    const result = await coordinator.rollout([oldModule], [newModule]);

    expect(result.status).toBe("RolledBack");
    // host1 should have been rolled back to old modules
    expect(conn1.modules[0].name).toBe("OldModule");
    // host2 should also have been rolled back
    expect(conn2.modules[0].name).toBe("OldModule");
  });
});
