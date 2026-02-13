# @constellation-engine/provider-sdk

TypeScript SDK for the [Constellation Engine](https://github.com/VledicFranco/constellation-engine) Module Provider Protocol.

Build external module providers in Node.js/TypeScript that register with Constellation Engine over gRPC — no JVM required.

## Installation

```bash
npm install @constellation-engine/provider-sdk
```

## Quick Start

```typescript
import {
  ConstellationProvider,
  CTypes,
  CValues,
  GrpcProviderTransport,
  GrpcExecutorServerFactory,
} from "@constellation-engine/provider-sdk";

const provider = await ConstellationProvider.create({
  namespace: "my-service",
  instances: ["localhost:9090"],
  transportFactory: (addr) => {
    const [host, port] = addr.split(":");
    return new GrpcProviderTransport(host, Number(port));
  },
  executorServerFactory: new GrpcExecutorServerFactory(),
});

provider.register({
  name: "Greet",
  inputType: CTypes.product({ name: CTypes.string() }),
  outputType: CTypes.product({ greeting: CTypes.string() }),
  version: "1.0.0",
  description: "Returns a greeting",
  handler: async (input) => {
    const name = (input as any).value.name.value;
    return CValues.product(
      { greeting: CValues.string(`Hello, ${name}!`) },
      { greeting: CTypes.string() },
    );
  },
});

await provider.start();
console.log("Provider running — press Ctrl+C to stop");

process.on("SIGINT", async () => {
  await provider.stop();
  process.exit(0);
});
```

## Requirements

- Node.js 18+
- A running Constellation Engine instance with the Module Provider Protocol enabled

## Features

- Full CType/CValue type system with JSON wire-format compatibility
- gRPC transport (register, deregister, control plane, execution)
- Canary rollout support
- Static and DNS-based instance discovery
- Promise-based API (no Cats Effect dependency)

## API Reference

See the [Module Provider documentation](https://github.com/VledicFranco/constellation-engine/blob/master/website/docs/integrations/module-provider.md).

## License

MIT
