/**
 * Production ProviderTransport backed by @grpc/grpc-js.
 *
 * Wraps ts-proto generated stubs to communicate with a Constellation server.
 * Mirrors the Scala GrpcProviderTransport class.
 */

import * as grpc from "@grpc/grpc-js";
import * as protoLoader from "@grpc/proto-loader";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import type {
  ProviderTransport,
  RegisterRequest,
  RegisterResponse,
  DeregisterRequest,
  DeregisterResponse,
  ControlPlaneHandler,
  ControlPlaneStream,
  Heartbeat,
  DrainAck,
} from "./transport.js";

// ===== Proto loading =====

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

// ===== GrpcProviderTransport =====

export class GrpcProviderTransport implements ProviderTransport {
  private readonly client: grpc.Client;

  constructor(host: string, port: number) {
    const proto = loadProtoDescriptor();
    // Navigate to constellation.provider.v1.ModuleProvider
    const ns = proto.constellation as grpc.GrpcObject;
    const providerNs = ns.provider as grpc.GrpcObject;
    const v1 = providerNs.v1 as grpc.GrpcObject;
    const ModuleProvider = v1.ModuleProvider as grpc.ServiceClientConstructor;

    this.client = new ModuleProvider(`${host}:${port}`, grpc.credentials.createInsecure());
  }

  async register(request: RegisterRequest): Promise<RegisterResponse> {
    return new Promise((resolve, reject) => {
      (this.client as any).register(request, (err: grpc.ServiceError | null, response: any) => {
        if (err) reject(err);
        else resolve(response as RegisterResponse);
      });
    });
  }

  async deregister(request: DeregisterRequest): Promise<DeregisterResponse> {
    return new Promise((resolve, reject) => {
      (this.client as any).deregister(request, (err: grpc.ServiceError | null, response: any) => {
        if (err) reject(err);
        else resolve(response as DeregisterResponse);
      });
    });
  }

  async openControlPlane(handler: ControlPlaneHandler): Promise<ControlPlaneStream> {
    const call = (this.client as any).controlPlane() as grpc.ClientDuplexStream<any, any>;

    call.on("data", (msg: any) => {
      // @grpc/proto-loader with oneofs:true sets msg.payload to the field name
      // and the value at msg[fieldName]. Check both camelCase and snake_case.
      if (msg.heartbeatAck || msg.heartbeat_ack) {
        handler.onHeartbeatAck(msg.heartbeatAck ?? msg.heartbeat_ack).catch(() => {});
      } else if (msg.activeModulesReport || msg.active_modules_report) {
        handler
          .onActiveModulesReport(msg.activeModulesReport ?? msg.active_modules_report)
          .catch(() => {});
      } else if (msg.drainRequest || msg.drain_request) {
        handler.onDrainRequest(msg.drainRequest ?? msg.drain_request).catch(() => {});
      }
    });

    call.on("error", (err: Error) => {
      handler.onStreamError(err).catch(() => {});
    });

    call.on("end", () => {
      handler.onStreamCompleted().catch(() => {});
    });

    return new GrpcControlPlaneStream(call);
  }

  /** Close the underlying gRPC channel. */
  close(): void {
    this.client.close();
  }
}

class GrpcControlPlaneStream implements ControlPlaneStream {
  constructor(private readonly call: grpc.ClientDuplexStream<any, any>) {}

  async sendHeartbeat(hb: Heartbeat, connectionId?: string): Promise<void> {
    this.call.write({
      connectionId: connectionId ?? "",
      protocolVersion: 1,
      heartbeat: hb,
    });
  }

  async sendDrainAck(ack: DrainAck): Promise<void> {
    this.call.write({ drainAck: ack });
  }

  async close(): Promise<void> {
    try {
      this.call.end();
    } catch {
      // Best-effort
    }
  }
}
