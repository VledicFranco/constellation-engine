/**
 * Production ExecutorServerFactory backed by @grpc/grpc-js.
 *
 * Creates a gRPC server hosting the ModuleExecutor service.
 * Mirrors the Scala GrpcExecutorServerFactory class.
 */

import * as grpc from "@grpc/grpc-js";
import * as protoLoader from "@grpc/proto-loader";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import type {
  ExecutorServerFactory,
  ExecutorServer,
  ExecuteRequest,
  ExecuteResponse,
} from "./transport.js";

function loadProtoDescriptor(): grpc.GrpcObject {
  const protoPath = resolve(
    dirname(fileURLToPath(import.meta.url)),
    "..",
    "..",
    "proto",
    "constellation",
    "provider",
    "v1",
    "provider.proto",
  );

  const packageDefinition = protoLoader.loadSync(protoPath, {
    keepCase: false,
    longs: Number,
    enums: Number,
    defaults: true,
    oneofs: true,
  });

  return grpc.loadPackageDefinition(packageDefinition);
}

export class GrpcExecutorServerFactory implements ExecutorServerFactory {
  async create(
    handler: (request: ExecuteRequest) => Promise<ExecuteResponse>,
    port: number,
  ): Promise<ExecutorServer> {
    const proto = loadProtoDescriptor();
    const ns = proto.constellation as grpc.GrpcObject;
    const providerNs = ns.provider as grpc.GrpcObject;
    const v1 = providerNs.v1 as grpc.GrpcObject;
    const ModuleExecutor = v1.ModuleExecutor as grpc.ServiceClientConstructor;

    const server = new grpc.Server();

    server.addService(ModuleExecutor.service, {
      execute: async (call: grpc.ServerUnaryCall<any, any>, callback: grpc.sendUnaryData<any>) => {
        try {
          const request = call.request as ExecuteRequest;
          // Convert inputData from Buffer to Uint8Array if needed
          if (request.inputData && Buffer.isBuffer(request.inputData)) {
            request.inputData = new Uint8Array(request.inputData);
          }
          const response = await handler(request);

          // Convert the response to proto-compatible format
          const protoResponse: Record<string, unknown> = {};
          if (response.result.case === "outputData") {
            protoResponse.outputData = response.result.value;
          } else if (response.result.case === "error") {
            protoResponse.error = response.result.value;
          }
          if (response.metrics) {
            protoResponse.metrics = response.metrics;
          }

          callback(null, protoResponse);
        } catch (error) {
          const err = error instanceof Error ? error : new Error(String(error));
          callback(null, {
            error: {
              code: "RUNTIME_ERROR",
              message: err.message,
              stackTrace: "",
            },
            metrics: { durationMs: 0, memoryBytes: 0 },
          });
        }
      },
    });

    const boundPort = await new Promise<number>((resolvePort, reject) => {
      server.bindAsync(
        `0.0.0.0:${port}`,
        grpc.ServerCredentials.createInsecure(),
        (err, actualPort) => {
          if (err) reject(err);
          else resolvePort(actualPort);
        },
      );
    });

    return {
      port: boundPort,
      stop: () =>
        new Promise<void>((resolveStop) => {
          server.tryShutdown(() => resolveStop());
        }),
    };
  }
}
