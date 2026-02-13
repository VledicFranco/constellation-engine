// ===== Types =====
export type {
  CType,
  CStringType,
  CIntType,
  CFloatType,
  CBooleanType,
  CListType,
  CMapType,
  CProductType,
  CUnionType,
  COptionalType,
} from "./types/ctype.js";
export { CTypes } from "./types/ctype.js";

export type {
  CValue,
  CStringValue,
  CIntValue,
  CFloatValue,
  CBooleanValue,
  CListValue,
  CMapValue,
  CProductValue,
  CUnionValue,
  CSomeValue,
  CNoneValue,
} from "./types/cvalue.js";
export { CValues } from "./types/cvalue.js";

export type { ModuleDefinition } from "./types/module-definition.js";
export { toDeclaration, qualifiedName } from "./types/module-definition.js";
export type { SdkConfig, CanaryConfig } from "./types/config.js";
export { DEFAULT_SDK_CONFIG, DEFAULT_CANARY_CONFIG } from "./types/config.js";
export { ConnectionState } from "./types/connection.js";
export type { CanaryResult } from "./types/canary.js";

// ===== Serialization =====
export type { CValueSerializer } from "./serialization/cvalue-serializer.js";
export {
  JsonCValueSerializer,
  encodeCType,
  decodeCType,
  encodeCValue,
  decodeCValue,
} from "./serialization/cvalue-serializer.js";
export { TypeSchemaConverter } from "./serialization/type-schema-converter.js";

// ===== Transport =====
export type {
  ProviderTransport,
  ControlPlaneHandler,
  ControlPlaneStream,
  ExecutorServerFactory,
  ExecutorServer,
  RegisterRequest,
  RegisterResponse,
  DeregisterRequest,
  DeregisterResponse,
  ExecuteRequest,
  ExecuteResponse,
  ExecutionError,
  ExecutionMetrics,
  ControlMessage,
  Heartbeat,
  HeartbeatAck,
  ActiveModulesReport,
  DrainRequest,
  DrainAck,
} from "./transport/transport.js";
export { GrpcProviderTransport } from "./transport/grpc-provider-transport.js";
export { GrpcExecutorServerFactory } from "./transport/grpc-executor-server.js";

// ===== Discovery =====
export type { DiscoveryStrategy } from "./discovery/discovery-strategy.js";
export { StaticDiscovery } from "./discovery/static-discovery.js";
export { DnsDiscovery } from "./discovery/dns-discovery.js";

// ===== Provider =====
export { ConstellationProvider } from "./provider/constellation-provider.js";
export type { ProviderOptions } from "./provider/constellation-provider.js";
export { ModuleExecutorServer } from "./provider/module-executor-server.js";
