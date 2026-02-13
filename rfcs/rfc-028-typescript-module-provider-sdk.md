# RFC-028: TypeScript Module Provider SDK

## Status

Accepted

## Summary

A TypeScript SDK (`@constellation-engine/provider-sdk`) that implements the Module Provider Protocol (RFC-024), enabling Node.js/TypeScript services to register as external module providers with Constellation Engine over gRPC — no JVM dependency required.

## Motivation

The Scala Provider SDK (shipped in v0.7.0) enables external module providers but requires a JVM runtime. Many teams building microservices use Node.js, Python, or Go. A TypeScript SDK enables the largest non-JVM ecosystem to integrate with Constellation, supporting:

1. **Node.js microservices** as pipeline modules (ML inference, API adapters, etc.)
2. **Polyglot architectures** where different pipeline modules run on different runtimes
3. **Serverless/edge** deployments where JVM startup time is prohibitive
4. **Frontend teams** who want to contribute modules using familiar tooling

## Design Decisions

### Promise over IO

The Scala SDK uses Cats Effect IO for all async operations. TypeScript lacks a comparable effect system runtime. We chose native Promises because:

- Zero-dependency async model familiar to all Node.js developers
- No opinion on fp-ts, Effect-TS, or other libraries
- Simpler error handling (try/catch vs IO.handleErrorWith)
- Compatible with async/await syntax

### Explicit lifecycle over Resource

Scala's `Resource[IO, A]` provides automatic cleanup via bracket semantics. TypeScript doesn't have this abstraction built in. Instead:

- `ConstellationProvider.start()` acquires resources
- `ConstellationProvider.stop()` releases resources
- Both are idempotent
- Users are responsible for calling `stop()` on shutdown (typically via `process.on("SIGINT")`)

### Discriminated unions over classes

CType and CValue are modeled as TypeScript discriminated unions (tagged objects with a `tag` field) rather than class hierarchies because:

- The JSON wire format already uses `{"tag": "CString", "value": "..."}` — direct mapping
- No serialization layer needed (the type IS the wire format)
- Exhaustive switch/case checking via TypeScript's type narrowing
- Immutable by convention (readonly fields)

### Mutable fields over atomic references

The Scala SDK uses `Ref[IO, A]` (atomic references) for thread-safe state. Node.js is single-threaded, so:

- Plain mutable fields are safe and simpler
- No concurrent state issues
- No need for lock-free data structures

### Proto sharing

The proto file is the contract between all SDKs. Rather than copying or duplicating:

- Single source of truth: `modules/module-provider-sdk/src/main/protobuf/`
- TypeScript SDK copies at build time via `scripts/copy-proto.mjs`
- CI verifies the proto hasn't drifted

## API Surface

```typescript
// Main entry point
const provider = await ConstellationProvider.create({
  namespace: "my-service",
  instances: ["localhost:9090"],
  transportFactory: (addr) => new GrpcProviderTransport(host, port),
  executorServerFactory: new GrpcExecutorServerFactory(),
});

provider.register({
  name: "Analyze",
  inputType: CTypes.product({ text: CTypes.string() }),
  outputType: CTypes.product({ score: CTypes.float() }),
  version: "1.0.0",
  description: "Sentiment analysis",
  handler: async (input) => { /* ... */ },
});

await provider.start();
// ... running ...
await provider.stop();
```

## Package Structure

```
sdks/typescript/
  src/
    types/          # CType, CValue, ModuleDefinition, Config, ConnectionState, CanaryResult
    serialization/  # CValueSerializer (JSON), TypeSchemaConverter (proto)
    transport/      # Interfaces + gRPC implementations
    provider/       # ConstellationProvider, InstanceConnection, ModuleExecutorServer, CanaryCoordinator
    discovery/      # DiscoveryStrategy, StaticDiscovery, DnsDiscovery
    index.ts        # Barrel export
  tests/
    unit/           # All unit tests
    integration/    # gRPC roundtrip test
    helpers/        # FakeProviderTransport, FakeExecutorServerFactory, fixtures
```

## Versioning Strategy

The TypeScript SDK version tracks the Constellation Engine version exactly. When the release script bumps the version, it updates `sdks/typescript/package.json` alongside `build.sbt` and `vscode-extension/package.json`.

## CI/CD

- **Tests**: `.github/workflows/typescript-sdk-tests.yml` — Node.js 18/20/22 matrix, runs on `sdks/typescript/**` or proto changes
- **Publish**: `.github/workflows/npm-publish.yml` — Triggered on GitHub release, publishes to npm with provenance

## Risks

| Risk | Mitigation |
|------|-----------|
| JSON wire format mismatch | Cross-language test fixtures captured from Scala Circe output |
| Proto codegen incompatibility | Integration test: roundtrip proto bytes between TS and Scala |
| npm org unavailable | Check `@constellation-engine` availability; fallback: scoped under `@vledicfranco` |

## References

- [RFC-024: Module Provider Protocol](./rfc-024-module-provider-protocol-v4.md)
- [Scala Provider SDK](../modules/module-provider-sdk/)
- [Proto definition](../modules/module-provider-sdk/src/main/protobuf/constellation/provider/v1/provider.proto)
