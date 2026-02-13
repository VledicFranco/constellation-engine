/**
 * In-memory ProviderTransport for testing.
 *
 * Records all calls and returns configurable responses.
 * Mirrors the Scala FakeProviderTransport.
 */

import type {
  ProviderTransport,
  RegisterRequest,
  RegisterResponse,
  DeregisterRequest,
  DeregisterResponse,
  ControlPlaneHandler,
  ControlPlaneStream,
  ControlMessage,
  Heartbeat,
  DrainAck,
} from "../../src/transport/transport.js";

export class FakeProviderTransport implements ProviderTransport {
  registerCalls: RegisterRequest[] = [];
  deregisterCalls: DeregisterRequest[] = [];
  controlPlaneOpened = false;
  controlPlaneClosed = false;

  private registerResp: RegisterResponse = {
    success: true,
    results: [],
    protocolVersion: 1,
    connectionId: "fake-conn-id",
  };

  private deregisterResp: DeregisterResponse = {
    success: true,
    results: [],
  };

  private controlMessageQueue: ControlMessage[] = [];

  async register(request: RegisterRequest): Promise<RegisterResponse> {
    this.registerCalls.push(request);
    return this.registerResp;
  }

  async deregister(request: DeregisterRequest): Promise<DeregisterResponse> {
    this.deregisterCalls.push(request);
    return this.deregisterResp;
  }

  async openControlPlane(handler: ControlPlaneHandler): Promise<ControlPlaneStream> {
    this.controlPlaneOpened = true;
    const stream = new FakeControlPlaneStream(handler, this.controlMessageQueue);
    return stream;
  }

  setRegisterResponse(resp: RegisterResponse): void {
    this.registerResp = resp;
  }

  setDeregisterResponse(resp: DeregisterResponse): void {
    this.deregisterResp = resp;
  }

  enqueueControlMessage(msg: ControlMessage): void {
    this.controlMessageQueue.push(msg);
  }
}

export class FakeControlPlaneStream implements ControlPlaneStream {
  sentHeartbeats: Heartbeat[] = [];
  sentDrainAcks: DrainAck[] = [];
  closed = false;

  constructor(
    private handler: ControlPlaneHandler,
    private controlMessages: ControlMessage[],
  ) {}

  async sendHeartbeat(hb: Heartbeat, connectionId?: string): Promise<void> {
    this.sentHeartbeats.push(hb);
  }

  async sendDrainAck(ack: DrainAck): Promise<void> {
    this.sentDrainAcks.push(ack);
  }

  async close(): Promise<void> {
    this.closed = true;
  }

  /** Process one pending server message by dispatching to the handler. Returns true if a message was processed. */
  async processOneMessage(): Promise<boolean> {
    const msg = this.controlMessages.shift();
    if (!msg) return false;
    await this.dispatchMessage(msg);
    return true;
  }

  private async dispatchMessage(msg: ControlMessage): Promise<void> {
    switch (msg.payload.case) {
      case "heartbeatAck":
        await this.handler.onHeartbeatAck(msg.payload.value);
        break;
      case "activeModulesReport":
        await this.handler.onActiveModulesReport(msg.payload.value);
        break;
      case "drainRequest":
        await this.handler.onDrainRequest(msg.payload.value);
        break;
    }
  }
}
