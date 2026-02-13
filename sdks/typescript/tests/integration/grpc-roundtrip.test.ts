/**
 * Integration test: real gRPC client <-> server in-process roundtrip.
 *
 * This test requires proto files to be present (run `npm run copy-proto` first)
 * and @grpc/proto-loader to be installed.
 *
 * Tests: register -> execute -> deregister flow over real gRPC.
 */

import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { existsSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const protoPath = resolve(
  __dirname,
  "..",
  "..",
  "proto",
  "constellation",
  "provider",
  "v1",
  "provider.proto",
);

const protoExists = existsSync(protoPath);

// Only run if proto file is available and grpc dependencies are installed
describe.skipIf(!protoExists)("gRPC roundtrip integration", () => {
  let GrpcExecutorServerFactory: any;
  let server: any;

  beforeAll(async () => {
    // Dynamic import so missing deps don't break other tests
    const grpcModule = await import("../../src/transport/grpc-executor-server.js");
    GrpcExecutorServerFactory = grpcModule.GrpcExecutorServerFactory;
  });

  afterAll(async () => {
    if (server) {
      await server.stop();
    }
  });

  it("should start executor server and handle requests", async () => {
    const factory = new GrpcExecutorServerFactory();

    server = await factory.create(
      async (request: any) => {
        // Echo the module name back as output
        const output = new TextEncoder().encode(
          JSON.stringify({ tag: "CString", value: `executed: ${request.moduleName}` }),
        );
        return {
          result: { case: "outputData" as const, value: output },
          metrics: { durationMs: 1, memoryBytes: 0 },
        };
      },
      0, // Let OS pick a free port
    );

    expect(server.port).toBeGreaterThan(0);

    // Import grpc-js to create a test client
    const grpc = await import("@grpc/grpc-js");
    const protoLoader = await import("@grpc/proto-loader");

    const packageDefinition = protoLoader.loadSync(protoPath, {
      keepCase: false,
      longs: Number,
      enums: Number,
      defaults: true,
      oneofs: true,
    });

    const proto = grpc.loadPackageDefinition(packageDefinition);
    const ns = proto.constellation as grpc.GrpcObject;
    const providerNs = ns.provider as grpc.GrpcObject;
    const v1 = providerNs.v1 as grpc.GrpcObject;
    const ModuleExecutor = v1.ModuleExecutor as grpc.ServiceClientConstructor;

    const client = new ModuleExecutor(
      `localhost:${server.port}`,
      grpc.credentials.createInsecure(),
    );

    // Send an execute request
    const response = await new Promise<any>((resolve, reject) => {
      (client as any).execute(
        {
          moduleName: "TestModule",
          inputData: Buffer.from(JSON.stringify({ tag: "CString", value: "test" })),
          executionId: "test-1",
          metadata: {},
        },
        (err: any, resp: any) => {
          if (err) reject(err);
          else resolve(resp);
        },
      );
    });

    // Verify response
    expect(response.outputData).toBeDefined();
    const outputJson = JSON.parse(Buffer.from(response.outputData).toString());
    expect(outputJson).toEqual({ tag: "CString", value: "executed: TestModule" });

    client.close();
  });
});
