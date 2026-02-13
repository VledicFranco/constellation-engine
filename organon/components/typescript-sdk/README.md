# TypeScript Provider SDK — Organon Component

## Overview

The TypeScript Provider SDK (`@constellation-engine/provider-sdk`) enables building external module providers in Node.js/TypeScript that communicate with Constellation Engine over gRPC.

## Key Files

| File | Purpose |
|------|---------|
| `sdks/typescript/src/types/ctype.ts` | CType discriminated union + `CTypes` factory |
| `sdks/typescript/src/types/cvalue.ts` | CValue discriminated union + `CValues` factory |
| `sdks/typescript/src/serialization/cvalue-serializer.ts` | JSON wire format codec (matches Scala Circe) |
| `sdks/typescript/src/serialization/type-schema-converter.ts` | CType ↔ protobuf TypeSchema conversion |
| `sdks/typescript/src/transport/transport.ts` | Transport interfaces |
| `sdks/typescript/src/transport/grpc-provider-transport.ts` | gRPC client transport |
| `sdks/typescript/src/transport/grpc-executor-server.ts` | gRPC executor server |
| `sdks/typescript/src/provider/constellation-provider.ts` | Main SDK entry point |
| `sdks/typescript/src/provider/instance-connection.ts` | Single connection lifecycle |
| `sdks/typescript/src/provider/module-executor-server.ts` | Request dispatch to handlers |
| `sdks/typescript/src/provider/canary-coordinator.ts` | Sequential canary rollout |
| `sdks/typescript/src/discovery/` | Instance discovery strategies |

## Navigation

- **Scala equivalent**: `modules/module-provider-sdk/` (1:1 API mapping)
- **Proto definition**: `modules/module-provider-sdk/src/main/protobuf/constellation/provider/v1/provider.proto`
- **Wire format source of truth**: `modules/runtime/src/main/scala/io/constellation/CustomJsonCodecs.scala`
- **Ethos**: See `ETHOS.md` in this directory
