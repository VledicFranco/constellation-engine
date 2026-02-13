# TypeScript Provider SDK — Ethos

## Identity

The TypeScript SDK is a **polyglot bridge** that enables Node.js/TypeScript services to participate as module providers in the Constellation runtime. It implements the same gRPC-based Module Provider Protocol as the Scala SDK, translating between TypeScript's async/await paradigm and Constellation's type system.

## Semantic Mapping to Scala SDK

| Scala Concept | TypeScript Equivalent |
|---------------|----------------------|
| `sealed trait CType` | `type CType = CStringType \| CIntType \| ...` (discriminated union) |
| `sealed trait CValue` | `type CValue = CStringValue \| CIntValue \| ...` (discriminated union) |
| `IO[A]` | `Promise<A>` |
| `Resource[IO, A]` | Explicit `start()` / `stop()` lifecycle |
| `Ref[IO, A]` | Mutable fields (single-threaded Node.js) |
| `cats.implicits.traverse` | `Promise.all()` / `Array.map()` |
| `FiniteDuration` | `number` (milliseconds, `Ms` suffix convention) |
| `Either[String, A]` | Thrown errors / return types |

## Invariants

1. **Wire format compatibility**: JSON encoding/decoding of CType and CValue MUST produce byte-identical output to the Scala Circe codecs in `CustomJsonCodecs.scala`. This is the most critical invariant — a mismatch breaks cross-language interop.

2. **Transport abstraction**: The SDK MUST support both real gRPC and in-memory fakes through the `ProviderTransport` interface. Production code never depends on gRPC directly.

3. **Lifecycle semantics**: `start()` MUST be called before any provider operations. `stop()` MUST cleanly deregister all modules and release gRPC resources. Both are idempotent.

4. **Proto single source of truth**: The proto file lives in `modules/module-provider-sdk/`. The TypeScript SDK copies it at build time — never duplicates or modifies it.

5. **TypeSchema synthetic names**: Union type conversion from protobuf uses `variant0`, `variant1`, etc. as field names, matching the Scala `TypeSchemaConverter` exactly.

## Design Decisions

- **Promise over IO**: TypeScript lacks an IO monad runtime. Promises are the natural async primitive and avoid forcing fp-ts/Effect on consumers.
- **Explicit lifecycle over Resource**: Without Cats Effect Resource, the SDK uses explicit `start()`/`stop()` methods. This is more familiar to Node.js developers.
- **Mutable state over Ref**: Node.js is single-threaded. Using plain mutable fields is safe and simpler than emulating atomic references.
- **Discriminated unions over classes**: Tagged objects (`{ tag: "CString", value: "..." }`) align with the JSON wire format directly, avoiding serialization mapping layers.
