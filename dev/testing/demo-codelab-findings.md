# Demo Codelab Walkthrough Findings

**Date:** 2026-02-13
**Scope:** Walked through all 8 codelabs in `constellation-demo` project, verifying APIs, syntax, Docker config, and DX.

## Bugs Found

### 1. Scala SDK: `executorUrl` uses instanceAddress instead of executorHost (#214)

**Severity:** P1 — Scala providers cannot work in Docker/K8s networking without a workaround.

**Location:** `modules/module-provider-sdk/.../InstanceConnection.scala:61`

```scala
executorUrl = s"$instanceAddress:${config.executorPort}"
```

`instanceAddress` is the constellation server address (e.g., `constellation-server:9090`), producing `constellation-server:9090:50052`. The server-side `parseHostPort` uses `lastIndexOf(':')`, yielding host=`constellation-server:9090` port=50052. `ManagedChannelBuilder.forAddress` fails because `constellation-server:9090` is not a valid hostname.

**TS SDK handles this correctly** with separate `executorHost` and `executorPort` fields in `SdkConfig`.

**Workaround:** Pass the executor hostname as the "instance" address, and hardcode the server connection in the transport factory:

```scala
instances = List(executorHost),
transportFactory = { (_: String) =>
  val channel = ManagedChannelBuilder.forAddress(serverHost, serverPort).build()
  new GrpcProviderTransport(channel)
}
```

**Fix:** Add `executorHost: String` to `SdkConfig` and use it in `InstanceConnection`.

### 2. `CValue.CProduct` structure param type confusion (#215)

**Severity:** P2 — Compile error, but confusing error message.

**Location:** `modules/core/.../TypeSystem.scala:118`

```scala
final case class CProduct(value: Map[String, CValue], structure: Map[String, CType])
```

Users naturally pass `CType.CProduct(Map(...))` as the second parameter since both names are `CProduct`. But the signature expects `Map[String, CType]`, not `CType`. This was found 4 times in the demo's Scala provider modules.

**Fix options:**
- Add factory overload accepting `CType.CProduct`
- Add clear ScalaDoc warning
- Consider renaming the parameter

## DX Gaps Found

### 3. No setup guide in demo README

The Quick Start assumed users would know to run `setup.sh` first and understand what it does. Without it, `docker compose up --build` fails with a cryptic `COPY *.tgz` error.

**Fixed in demo:** Added Prerequisites section, explained `setup.sh`, added warning about the failure.

### 4. No healthchecks on server/provider Docker services

Only memcached had a healthcheck. Providers used `depends_on: - constellation-server` which only waits for container start, not readiness. This causes providers to crash-loop while the server is still booting.

**Fixed in demo:** Added healthcheck to `constellation-server`, changed provider `depends_on` to `condition: service_healthy`.

### 5. `.env` file unused

`docker-compose.yml` hardcoded all environment variables instead of referencing the `.env` file. The `.env` file existed but was dead code.

**Status:** Low priority, noted but not fixed.

## What Passed Verification

- **Server DemoServer.scala:** All 24 API calls verified correct against engine source
- **TS Provider:** All imports, types, factory methods, and handler signatures correct
- **Pipeline syntax:** All 13 `.cst` files use valid constellation-lang syntax
- **StdLib modules:** All module names verified against `StdLib.scala` and `ExampleLib.scala`
- **Parser features:** `with {}` options, guards, coalesce, branch, if/else all verified against parser source
- **Docker networking:** Service names, ports, and env vars consistent across all services

## Parity Gap: Scala SDK vs TS SDK

| Feature | TS SDK | Scala SDK |
|---------|--------|-----------|
| `executorHost` config | Yes | **Missing** (#214) |
| `executorPort` config | Yes | Yes |
| Sync `register()` | Yes (void) | Yes (IO[Unit]) |
| `start()`/`stop()` | async/async | Resource |
| Transport constructor | `(host, port)` | `(ManagedChannel)` |

The TS SDK has a slightly better DX for Docker deployments due to the `executorHost` field.
