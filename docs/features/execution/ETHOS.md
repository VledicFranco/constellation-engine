# Execution Modes: Ethos

> **Path**: `docs/features/execution/ETHOS.md`
> **Parent**: [execution/](./README.md)

Constraints and invariants for LLMs working on execution-related code.

## Purpose

This document defines the rules that must be followed when modifying execution-related code. These rules exist to maintain system correctness, prevent subtle bugs, and preserve the design intent.

## Mode Selection Invariants

### Rule 1: Mode is Determined by Endpoint

The execution mode is determined solely by the endpoint called:

| Endpoint | Mode | No Exceptions |
|----------|------|---------------|
| `POST /run` | Hot | Always compiles source |
| `POST /compile` | Cold (compile phase) | Never executes |
| `POST /execute` | Cold (execute phase) | Never compiles |
| `POST /executions/{id}/resume` | Suspended | Resumes existing state |

**Violation:** Do not add logic that changes mode based on request content, headers, or configuration. A `/run` request always does hot execution even if the source was previously compiled.

**Rationale:** Mode predictability is a core design principle. Users must be able to reason about system behavior from the endpoint alone.

### Rule 2: Compiled Pipelines are Immutable

Once a `PipelineImage` is created, it must never be modified:

- Structural hash is computed at creation time and never changes
- Module references within the image are fixed
- Input/output schemas are immutable

**Violation:** Do not add methods that mutate a `PipelineImage` after creation. If behavior must change, create a new image with a new hash.

**Rationale:** Immutability enables safe caching, content-addressed storage, and version comparison by hash.

### Rule 3: Structural Hash Determines Identity

Two pipelines are considered identical if and only if their structural hashes match:

- Different source formatting with same semantics produces same hash
- Comments and whitespace changes do not affect the hash
- Any semantic change produces a different hash

**Violation:** Do not add pipeline comparison logic that examines anything other than structural hash. Do not include metadata, timestamps, or syntactic hash in identity checks.

**Rationale:** Content-addressed storage depends on deterministic hashing. Hash equality must imply semantic equality.

## Suspension Rules

### Rule 4: Suspension is Deterministic

Given the same pipeline, inputs, and execution state, the suspension decision must be identical across runs:

- Suspension occurs when a required input is not available in the current scope
- The set of missing inputs is computed deterministically from the pipeline DAG
- Resumption with identical inputs produces identical results

**Violation:** Do not add non-deterministic factors to suspension logic (timing, random values, external state). Do not suspend based on module execution time or resource availability.

**Rationale:** Suspension must be reproducible for debugging and testing. Users must be able to predict when suspension will occur.

### Rule 5: Suspended State is Complete

When execution suspends, all necessary state must be persisted:

- All computed node values
- All provided inputs (including those not yet consumed)
- Pipeline reference (structural hash)
- Execution ID and resumption count

**Violation:** Do not rely on in-memory state surviving between suspension and resumption. Do not assume the same server instance will handle resumption.

**Rationale:** Suspended executions may be resumed on different server instances, after restarts, or after arbitrary delays.

### Rule 6: Resumption Validates Integrity

Before resuming, the system must validate:

- Execution ID exists and is in suspended state
- Pipeline reference still exists and is loadable
- Provided inputs have correct types
- No concurrent resume is in progress

**Violation:** Do not bypass validation checks. Do not resume executions with type-mismatched inputs. Do not allow concurrent resumes of the same execution.

**Rationale:** Validation prevents data corruption and undefined behavior. Concurrent resumes would create race conditions.

### Rule 7: Inputs are Never Overwritten

Once an input is provided (either initially or via resume), it cannot be replaced:

- Providing the same input twice is an error
- Providing a different value for a computed node is an error
- Only missing inputs and unresolved nodes can be provided

**Violation:** Do not add "overwrite" or "force" options for inputs. Do not silently ignore duplicate inputs.

**Rationale:** Overwriting would break execution determinism and make debugging impossible.

## State Management Invariants

### Rule 8: Execution IDs are Globally Unique

Each execution receives a UUID that is unique across:

- All time (never reused)
- All server instances (coordination not required)
- All pipeline versions

**Violation:** Do not generate execution IDs from sequential counters, timestamps, or other non-UUID sources.

**Rationale:** UUIDs enable distributed operation without coordination. Collision would corrupt suspension state.

### Rule 9: State Transitions are One-Way

Execution status follows a strict progression:

```
pending -> running -> suspended -> running -> completed
                  \                        \-> failed
                   \-> completed
                    \-> failed
```

**Violation:** Do not allow transitions backward (e.g., completed to running). Do not reset suspension state.

**Rationale:** One-way transitions enable reliable state management and audit logging.

### Rule 10: Completed and Failed are Terminal

Once an execution reaches `completed` or `failed`:

- State may be deleted from suspension store
- No further operations are valid
- Execution ID may not be reused

**Violation:** Do not add "retry" operations on failed executions. Create a new execution instead.

**Rationale:** Terminal states simplify garbage collection and prevent state resurrection.

## Concurrency Invariants

### Rule 11: Per-Execution Locking

Resume operations must acquire exclusive access to the execution:

- Only one resume can be in progress at a time per execution
- Lock must be held for the entire resume operation
- Lock must be released on completion, failure, or timeout

**Violation:** Do not allow concurrent resumes. Do not release lock before state is persisted.

**Rationale:** Concurrent resumes would cause lost updates and corrupted state.

### Rule 12: Pipeline Loading is Idempotent

Loading a pipeline by reference must be idempotent:

- Same reference always returns same pipeline (or equivalent error)
- Loading does not modify global state
- Failed load does not leave partial state

**Violation:** Do not cache load failures. Do not modify pipeline store during load.

**Rationale:** Idempotent loading enables retries and simplifies error recovery.

## Error Handling Invariants

### Rule 13: Fail Fast on Invalid State

If internal state is inconsistent, fail immediately:

- Missing expected state is a system error
- Type mismatches in stored data are system errors
- Corrupted suspension state is a system error

**Violation:** Do not attempt to "recover" from inconsistent internal state. Do not silently continue with partial data.

**Rationale:** Masking internal errors leads to data corruption and debugging nightmares.

### Rule 14: User Errors are Distinct from System Errors

Distinguish between:

| Type | Example | HTTP Status |
|------|---------|-------------|
| User error | Missing required input | 400 |
| Client error | Invalid execution ID | 404 |
| System error | State corruption | 500 |

**Violation:** Do not return 500 for user errors. Do not return 400 for system errors.

**Rationale:** Error categorization enables proper client handling and monitoring.

## Testing Requirements

### Rule 15: Execution Tests are Deterministic

All execution-related tests must:

- Use fixed inputs (no random values)
- Mock time-dependent operations
- Run identically in any environment
- Pass on retry without cleanup

**Violation:** Do not write tests that depend on timing, random values, or external services.

### Rule 16: Suspension Tests Cover Serialization Roundtrip

Any test of suspended execution must verify:

- State serializes correctly
- State deserializes to equivalent form
- Resumed execution produces correct result
- Multiple resume cycles work correctly

**Violation:** Do not test suspension with in-memory-only state. Always test serialization.

## Checklist for Execution Changes

Before modifying execution code, verify:

- [ ] Mode selection remains endpoint-based
- [ ] No PipelineImage mutation after creation
- [ ] Structural hash logic unchanged (or intentionally changed with migration)
- [ ] Suspension is deterministic
- [ ] All state persisted on suspend
- [ ] Validation on resume is complete
- [ ] Inputs cannot be overwritten
- [ ] Execution IDs use UUID
- [ ] State transitions are one-way
- [ ] Terminal states are respected
- [ ] Per-execution locking for resume
- [ ] Pipeline loading is idempotent
- [ ] Internal errors fail fast
- [ ] User vs system errors distinguished
- [ ] Tests are deterministic
- [ ] Serialization roundtrip tested
