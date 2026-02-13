import { describe, it, expect, beforeEach } from "vitest";
import { ConstellationProvider } from "../../src/provider/constellation-provider.js";
import { ConnectionState } from "../../src/types/connection.js";
import { FakeProviderTransport } from "../helpers/fake-provider-transport.js";
import { FakeExecutorServerFactory } from "../helpers/fake-executor-server.js";
import { CTypes } from "../../src/types/ctype.js";
import { CValues } from "../../src/types/cvalue.js";
import type { ModuleDefinition } from "../../src/types/module-definition.js";

const testModule: ModuleDefinition = {
  name: "Greet",
  inputType: CTypes.product({ name: CTypes.string() }),
  outputType: CTypes.product({ greeting: CTypes.string() }),
  version: "1.0.0",
  description: "Greets",
  handler: async (_input) => {
    return CValues.product({ greeting: CValues.string("Hello!") }, { greeting: CTypes.string() });
  },
};

describe("ConstellationProvider", () => {
  let transports: Map<string, FakeProviderTransport>;
  let executorFactory: FakeExecutorServerFactory;

  beforeEach(() => {
    transports = new Map();
  });

  function createTransport(address: string) {
    const transport = new FakeProviderTransport();
    transports.set(address, transport);
    return transport;
  }

  async function createProvider(instances: string[] = ["host1:9090"]) {
    executorFactory = new FakeExecutorServerFactory();
    const provider = await ConstellationProvider.create({
      namespace: "test.ns",
      instances,
      transportFactory: createTransport,
      executorServerFactory: executorFactory,
      config: {
        canary: {
          observationWindowMs: 1,
          healthThreshold: 0.95,
          maxLatencyMs: 5000,
          rollbackOnFailure: true,
        },
      },
    });
    return provider;
  }

  it("should create without starting", async () => {
    const provider = await createProvider();
    expect(provider.namespace).toBe("test.ns");
    expect(provider.registeredModules).toHaveLength(0);
  });

  it("should register modules before start", async () => {
    const provider = await createProvider();
    provider.register(testModule);
    expect(provider.registeredModules).toHaveLength(1);
    expect(provider.registeredModules[0].name).toBe("Greet");
  });

  it("should throw when registering after start", async () => {
    const provider = await createProvider();
    provider.register(testModule);
    await provider.start();

    expect(() => provider.register(testModule)).toThrow("Cannot register modules after");

    await provider.stop();
  });

  it("should start and connect to all instances", async () => {
    const provider = await createProvider(["host1:9090", "host2:9090"]);
    provider.register(testModule);
    await provider.start();

    const status = await provider.status();
    expect(status).toHaveLength(2);
    expect(status.every((s) => s.state === ConnectionState.Active)).toBe(true);

    // Verify executor server was started
    expect(executorFactory.started).toBe(true);

    // Verify register was called for each instance
    expect(transports.get("host1:9090")?.registerCalls).toHaveLength(1);
    expect(transports.get("host2:9090")?.registerCalls).toHaveLength(1);

    await provider.stop();
  });

  it("should be idempotent on start", async () => {
    const provider = await createProvider();
    provider.register(testModule);
    await provider.start();
    await provider.start(); // no-op
    expect(transports.get("host1:9090")?.registerCalls).toHaveLength(1);
    await provider.stop();
  });

  it("should stop and disconnect all instances", async () => {
    const provider = await createProvider(["host1:9090"]);
    provider.register(testModule);
    await provider.start();
    await provider.stop();

    expect(transports.get("host1:9090")?.deregisterCalls).toHaveLength(1);
    expect(executorFactory.stopped).toBe(true);
  });

  it("should be idempotent on stop", async () => {
    const provider = await createProvider();
    provider.register(testModule);
    await provider.start();
    await provider.stop();
    await provider.stop(); // no-op, should not throw
  });

  it("should perform canary rollout and promote", async () => {
    const provider = await createProvider(["host1:9090", "host2:9090"]);
    provider.register(testModule);
    await provider.start();

    const newModule: ModuleDefinition = {
      ...testModule,
      name: "GreetV2",
      version: "2.0.0",
    };

    const result = await provider.canaryRollout([newModule]);
    expect(result.status).toBe("Promoted");
    expect(provider.registeredModules[0].name).toBe("GreetV2");

    await provider.stop();
  });

  it("should return empty status when not started", async () => {
    const provider = await createProvider();
    const status = await provider.status();
    expect(status).toHaveLength(0);
  });

  it("should clean up executor server and connections on partial start failure", async () => {
    executorFactory = new FakeExecutorServerFactory();
    const callCount = { n: 0 };
    const provider = await ConstellationProvider.create({
      namespace: "test.ns",
      instances: ["host1:9090", "host2:9090"],
      transportFactory: (addr) => {
        const transport = new FakeProviderTransport();
        transports.set(addr, transport);
        callCount.n++;
        // Second transport fails on register
        if (callCount.n === 2) {
          transport.setRegisterResponse({
            success: false,
            results: [{ moduleName: "Greet", accepted: false, rejectionReason: "conflict" }],
            protocolVersion: 1,
            connectionId: "",
          });
        }
        return transport;
      },
      executorServerFactory: executorFactory,
      config: {
        canary: {
          observationWindowMs: 1,
          healthThreshold: 0.95,
          maxLatencyMs: 5000,
          rollbackOnFailure: true,
        },
      },
    });
    provider.register(testModule);

    await expect(provider.start()).rejects.toThrow("Registration failed");
    // Executor server should be cleaned up
    expect(executorFactory.stopped).toBe(true);
  });

  it("should handle disconnect errors gracefully during stop", async () => {
    const provider = await createProvider(["host1:9090"]);
    provider.register(testModule);
    await provider.start();

    // Make deregister throw
    const transport = transports.get("host1:9090")!;
    transport.deregister = async () => {
      throw new Error("deregister boom");
    };

    // stop() should not throw even if disconnect fails
    await provider.stop();
    expect(executorFactory.stopped).toBe(true);
  });

  it("should handle transport close errors gracefully during stop", async () => {
    executorFactory = new FakeExecutorServerFactory();
    const provider = await ConstellationProvider.create({
      namespace: "test.ns",
      instances: ["host1:9090"],
      transportFactory: (addr) => {
        const transport = new FakeProviderTransport();
        transports.set(addr, transport);
        // Add a close method that throws
        (transport as any).close = () => {
          throw new Error("close failed");
        };
        return transport;
      },
      executorServerFactory: executorFactory,
      config: {
        canary: {
          observationWindowMs: 1,
          healthThreshold: 0.95,
          maxLatencyMs: 5000,
          rollbackOnFailure: true,
        },
      },
    });
    provider.register(testModule);
    await provider.start();

    // stop() should not throw even if transport close throws
    await provider.stop();
    expect(executorFactory.stopped).toBe(true);
  });
});
