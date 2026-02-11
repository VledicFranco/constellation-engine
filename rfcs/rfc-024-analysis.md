# RFC-024 Organon Analysis

**RFC:** Module Provider Protocol
**Status:** Draft (Under Review)
**Reviewer:** Claude (Organon Methodology Application)
**Date:** 2026-02-10

---

## Organon Compliance Check

### Core Invariants (ETHOS.md)

| Invariant | Compliance | Notes |
|-----------|------------|-------|
| Code is source of truth | ✅ Pass | No conflicts |
| Types are structural | ✅ Pass | Preserved |
| Modules are pure interfaces | ⚠️ **Concern** | External modules introduce network effects (latency, failures) |
| DAGs are acyclic | ✅ Pass | Preserved |
| Resilience is declarative | ❌ **FAIL** | RFC doesn't address how `with retry`, `timeout`, `cache` work with RPC |
| Parallelism is automatic | ⚠️ **Concern** | RFC doesn't address scheduling implications for distributed modules |

### Design Principles (Priority Order)

| Principle | Compliance | Notes |
|-----------|------------|-------|
| 1. Type Safety Over Convenience | ❌ **FAIL** | RPC boundary weakens compile-time guarantees. Schema mismatches discovered at runtime. |
| 2. Explicit Over Implicit | ⚠️ **Concern** | Service discovery and registration adds implicit behavior |
| 3. Composition Over Extension | ✅ Pass | Extends via modules, not syntax |
| 4. Declarative Over Imperative | ✅ Pass | Maintains declarative module interface |
| 5. Simple Over Powerful | ❌ **FAIL** | gRPC + protobuf + MessagePack + registry + heartbeats + SDKs = high complexity |

### Identity Check (ETHOS.md)

| Statement | RFC Alignment |
|-----------|---------------|
| "Constellation IS a pipeline orchestration framework" | ✅ Aligns - still orchestrates |
| "Constellation is NOT distributed execution" (line 149) | ❌ **CONTRADICTS** - RFC proposes distributed execution |

---

## Critical Issues

### 1. **Contradicts Core Philosophy** 🔴

**ETHOS.md line 149:** "Distributed execution (cross-node DAG execution)" is explicitly **out of scope**.

**RFC-024:** Proposes exactly this - modules running on separate services, orchestrated across network boundaries.

**Resolution Required:** Either:
- A) Argue for changing ETHOS.md to allow distributed execution (requires strong justification)
- B) Find a different approach that doesn't violate this constraint
- C) Reject RFC as incompatible with product vision

### 2. **Type Safety Degradation** 🔴

**PHILOSOPHY.md:** "Validate field accesses at compile time (not runtime)"

**RFC Problem:**
```scala
// Compile time: ✅ Type checks pass
result = ExternalModule(input)

// Runtime: ❌ Failures possible:
// - Network timeout
// - Service not registered
// - Schema mismatch (service returns wrong type)
// - Serialization error
```

**ETHOS.md:** "Fail at compile time. Catch errors before execution whenever possible."

**RFC violates this** - External module type errors are runtime errors.

### 3. **Complexity vs Simplicity** 🔴

**ETHOS.md:** "Simple Over Powerful - Prefer a simple solution that covers 90% of cases"

**RFC Proposes:**
- gRPC (Protocol Buffers)
- MessagePack serialization
- Service registry (central + service mesh)
- Heartbeat mechanism
- SDK for Node.js, Python, Go, Rust, etc.
- Service discovery
- Operational overhead (monitoring, debugging, deployment)

**Question:** Does this cover a critical 90% use case, or is it solving an edge case with high complexity?

### 4. **Resilience Not Addressed** 🔴

**Missing sections:**
- How does `with retry: 3` work for external modules?
  - Does it retry the RPC call?
  - What about idempotency?
- How does `with timeout: 30s` work?
  - RPC timeout + module execution timeout?
- How does `with cache: 5min` work?
  - Cache RPC results?
  - Cache on which side?
- How does `with fallback: default` work?
  - What if service is down?

**ETHOS.md:** "Resilience is declarative. Retry, timeout, fallback, cache are language constructs."

RFC must explicitly address these.

### 5. **Module Purity Violated** ⚠️

**PHILOSOPHY.md:** "Modules are Scala functions wrapped in metadata."

**RFC-024:** Modules are RPC calls to external services.

**Implications:**
- Network latency (not pure computation)
- Network failures (new failure mode)
- Observability challenges (cross-service tracing)
- Debugging complexity (distributed systems)

**Question:** Is this still a "pure interface" if network effects are exposed?

---

## Missing Critical Sections

### 1. Failure Modes & Philosophy

**Required:**
- What happens when service is down?
- What happens on network timeout?
- What happens on schema mismatch?
- What happens on serialization error?
- How do we surface these errors to users?

**ETHOS.md:** "Fail visibly. Never swallow errors silently."

### 2. Parallelization & Scheduling

**Questions:**
- How does the scheduler handle external modules?
- Can external modules be parallelized?
- What if external service can't handle parallel requests?
- Does `with concurrency: 5` limit RPC calls?

### 3. Transaction Semantics

**Questions:**
- What if pipeline fails halfway through?
- Do external modules support rollback?
- What about compensating actions?

### 4. Observability

**Questions:**
- How do we trace requests across services?
- How do we aggregate metrics?
- How do we correlate logs?

### 5. Deployment Model

**Questions:**
- How do developers test locally?
- How does CI/CD work?
- What about staging vs production?
- Version compatibility?

---

## Alternative Approaches (Simpler)

### Alternative 1: HTTP JSON (No gRPC)

**Simplification:**
```scala
// Instead of: gRPC + protobuf + MessagePack + registry
// Use: HTTP POST + JSON

POST http://service:8080/modules/Uppercase/invoke
Content-Type: application/json

{"text": "hello"}

Response:
{"result": "HELLO"}
```

**Benefits:**
- Simpler (just HTTP + JSON)
- Easier to debug (curl, browser, postman)
- Language-agnostic (every language has HTTP + JSON)
- No protobuf compilation

**Trade-offs:**
- Slightly larger payloads (JSON vs MessagePack)
- Slightly slower (HTTP vs gRPC)
- But: Simplicity > 5ms latency

### Alternative 2: Static Registration (No Service Discovery)

**Simplification:**
```scala
// Instead of: Dynamic registry + heartbeats + service mesh
// Use: Static configuration

// application.conf
external-modules {
  Uppercase {
    url = "http://text-processing:8080/modules/Uppercase/invoke"
    input-type = "{ text: String }"
    output-type = "{ result: String }"
  }
  Sentiment {
    url = "http://ml-inference:8080/modules/Sentiment/invoke"
    input-type = "{ text: String }"
    output-type = "{ sentiment: String, confidence: Float }"
  }
}
```

**Benefits:**
- No registry service to maintain
- No heartbeat mechanism
- Configuration is explicit (not discovered)
- Easier to reason about

**Trade-offs:**
- Manual configuration (not auto-discovery)
- But: ETHOS says "Explicit Over Implicit"

### Alternative 3: Webhook Pattern (Push, Not Pull)

**Different Model:**
```scala
// Instead of: Constellation calls external services (pull)
// Use: External services POST results to Constellation (push)

// 1. Constellation starts execution, suspends waiting for external input
result = ExternalModule(input) // Suspends

// 2. External service POSTs result
POST http://constellation:8080/executions/{id}/provide-result
{"nodeId": "ExternalModule", "result": {"data": "..."}}

// 3. Constellation resumes execution
```

**Benefits:**
- Fits existing suspension model (RFC-014)
- No RPC from Constellation
- External service can be async/long-running

**Trade-offs:**
- More complex for simple sync calls
- But: Already have suspension infrastructure

---

## Recommendations

### Option A: **Simplify RFC** (Preserves Intent)

**Changes Required:**
1. Replace gRPC + protobuf with HTTP + JSON
2. Replace dynamic registry with static configuration
3. Add section on resilience (`with` clauses)
4. Add section on failure modes
5. Add section on parallelization
6. Argue why distributed execution should be allowed (change ETHOS.md)

**Outcome:** Simpler RFC that fits philosophy better

### Option B: **Use Webhook + Suspension Model** (Aligns with Existing)

**Changes Required:**
1. Reframe as extension of RFC-014 (Suspendable Execution)
2. External modules trigger suspension
3. External services POST results back
4. Leverage existing suspension infrastructure

**Outcome:** Aligns with existing patterns, less new infrastructure

### Option C: **Reject RFC** (Incompatible)

**Reasoning:**
- Distributed execution is explicitly out of scope
- Type safety degradation unacceptable
- Complexity doesn't justify benefits

**Outcome:** Focus on in-process polyglot (GraalVM) or WebAssembly instead

---

## Questions for Author/Stakeholder

1. **Philosophy:** Why should distributed execution be allowed? What changes to ETHOS.md?

2. **Use Case:** What % of users need polyglot modules? Is this solving 10% or 90% of use cases?

3. **Type Safety:** How do we preserve compile-time guarantees across RPC boundaries?

4. **Complexity:** Why gRPC + protobuf + MessagePack? Why not HTTP + JSON?

5. **Resilience:** How do `with retry`, `with timeout`, `with cache`, `with fallback` work?

6. **Failure Modes:** What happens when network fails? Service crashes? Schema mismatch?

7. **Alternative:** Why not extend RFC-014 (suspension) instead of new protocol?

8. **Trade-off:** Is 5ms RPC latency + operational complexity worth language flexibility?

---

## Next Steps

1. **Author Response:** Address questions above
2. **ETHOS Debate:** Argue for allowing distributed execution or find alternative
3. **Simplification:** Remove unnecessary complexity (gRPC, MessagePack, registry)
4. **Integration:** Show how resilience, parallelization, failure handling work
5. **Comparison:** Side-by-side with alternatives (suspension model, WebAssembly)

---

## Verdict: **NOT ACCEPTABLE** (Current Form)

**Reasons:**
1. ❌ Contradicts ETHOS.md (distributed execution out of scope)
2. ❌ Violates "Type Safety Over Convenience"
3. ❌ Violates "Simple Over Powerful"
4. ❌ Missing critical sections (resilience, failure modes)
5. ❌ Doesn't address module purity concerns

**Path Forward:** Major revision required with Organon-compliant justification.
