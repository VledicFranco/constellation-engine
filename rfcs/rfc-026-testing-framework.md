# RFC-026: Testing Framework & Production Replay

**Status:** Draft
**Priority:** P1 (Developer Experience / Quality Infrastructure)
**Author:** Human + Claude
**Created:** 2026-02-10

---

## Summary

Introduce a **three-layer testing framework** for Constellation Engine:

1. **Observation Plugin** — A Scala SPI component that logs module and pipeline input/output values to a pluggable data store (BigQuery, BigTable, local files, etc.), creating a corpus of real production traffic.

2. **Native `.cst` Test Syntax** — First-class `test` blocks and assertion primitives in constellation-lang, runnable via a `constellation test` CLI command, so pipeline authors never need to write Scala to test their pipelines.

3. **Replay Engine** — A test execution mode that pulls sampled production data from the observation store and replays it: real traffic values as pipeline inputs, and real captured outputs to **strategically mock modules** — enabling tests that reproduce production behavior with surgical control over which modules run live and which are stubbed with recorded data.

**Key Insight:** The observation store turns every production execution into a potential test fixture. Combined with strategic mocking, developers can test any module in isolation against real data without standing up the full dependency graph.

---

## Motivation

### Testing Pipelines Today is Painful

Testing a Constellation pipeline currently requires:

1. Writing Scala test code (no native `.cst` testing)
2. Manually constructing `Constellation` instances, registering modules, compiling source
3. Hand-crafting `CValue` inputs for every test case
4. No test doubles — every module in the DAG must be real or manually faked
5. No connection to production reality — test fixtures are invented, not derived from real traffic

This means:
- **Pipeline authors** (who may not write Scala) cannot test their own work
- **Test fixtures rot** — hand-written inputs drift from actual production data
- **Integration testing is all-or-nothing** — you either run the full pipeline or test nothing
- **Regressions from real traffic** are discovered in production, not in tests

### The Observation + Replay Loop

```
┌─────────────┐     ┌───────────────────┐     ┌──────────────────┐
│  Production  │────▶│  Observation Store │────▶│  Test Framework  │
│  Execution   │     │  (BigQuery, etc.) │     │  (Replay Engine) │
└─────────────┘     └───────────────────┘     └──────────────────┘
       │                     │                         │
       │              Captures per-module         Replays samples:
       │              inputs + outputs            - Real inputs for pipeline
       │              at every DAG edge           - Real outputs to mock modules
       │                                          - Run target module live
       │                                                │
       └────────────────────────────────────────────────┘
                    Closed feedback loop
```

**Production traffic becomes the test suite.** When a module is updated, replay recorded inputs to verify the new version produces compatible outputs. When a pipeline is modified, replay real traffic to catch regressions before deployment.

### What This Enables

| Persona | Capability |
|---------|------------|
| **Pipeline author** | Write `.cst` test files with assertions, run `constellation test`, get pass/fail |
| **Module developer** | Replay production inputs against updated module, compare outputs |
| **Platform team** | Continuous regression testing against sampled production traffic |
| **On-call engineer** | Reproduce a production incident by replaying the exact inputs that caused it |

---

## Design Principles

| Principle | Rationale |
|-----------|-----------|
| **Observation is opt-in and zero-overhead when off** | The plugin hooks into `ExecutionListener` SPI. When not configured, no data is captured and no performance cost is incurred. |
| **Store-agnostic** | The observation plugin writes to a `RecordingBackend` trait. BigQuery, BigTable, local JSON files, and custom implementations are all supported. |
| **Strategic mocking over total mocking** | Replay tests don't mock everything — they mock specific modules with recorded data while running the target module live. This tests real behavior in a realistic context. |
| **`.cst` tests are pipelines** | Test files are valid `.cst` files with additional `test` blocks. They compile and execute through the same pipeline as production code. |
| **Replay is a test execution mode, not a separate system** | The replay engine is a configuration of the standard runtime with a `ReplayDataProvider` injected. No separate execution engine. |
| **Sampling controls data volume** | Observation supports configurable sampling rates per pipeline and per module to control storage costs. |

---

## Part 1: Observation Plugin

### Extended ExecutionListener

The current `ExecutionListener` receives module lifecycle events but **not** the actual data flowing through them. The observation plugin requires extending the callbacks:

```scala
trait ObservableExecutionListener extends ExecutionListener {

  /** Called after a module completes successfully, with its inputs and outputs. */
  def onModuleObserved(
      executionId: UUID,
      moduleId: UUID,
      moduleName: String,
      inputs: Map[String, CValue],
      output: CValue,
      durationMs: Long
  ): IO[Unit]

  /** Called when pipeline execution starts, with all provided inputs. */
  def onPipelineInputsObserved(
      executionId: UUID,
      dagName: String,
      inputs: Map[String, CValue]
  ): IO[Unit]

  /** Called when pipeline execution completes, with all declared outputs. */
  def onPipelineOutputsObserved(
      executionId: UUID,
      dagName: String,
      outputs: Map[String, CValue]
  ): IO[Unit]
}
```

### Runtime Integration Point

The data capture happens in `Runtime.runWithBackends` at the module execution boundary. Currently (simplified):

```scala
// Current: fires lifecycle event without data
backends.listener.onModuleComplete(executionId, moduleId, moduleName, durationMs)
```

Extended to:

```scala
// New: captures inputs/outputs when ObservableExecutionListener is configured
backends.listener match {
  case obs: ObservableExecutionListener =>
    val inputs = collectModuleInputs(moduleId, dataTable)
    val output = collectModuleOutput(moduleId, dataTable)
    obs.onModuleObserved(executionId, moduleId, moduleName, inputs, output, durationMs)
  case _ =>
    backends.listener.onModuleComplete(executionId, moduleId, moduleName, durationMs)
}
```

The input/output collection reads from the existing `MutableDataTable` (the `Deferred`-based data flow table) which already holds all values at module completion time. No additional storage or buffering is needed — we read what's already there.

### Recording Backend Trait

```scala
trait RecordingBackend {
  def typeName: String

  /** Write a single observation record. */
  def write(record: ObservationRecord): IO[Unit]

  /** Write a batch of records (for efficiency). */
  def writeBatch(records: Vector[ObservationRecord]): IO[Unit]

  /** Query recorded observations. */
  def query(filter: ObservationQuery): IO[Vector[ObservationRecord]]

  /** Sample N random records matching a filter. */
  def sample(filter: ObservationQuery, n: Int): IO[Vector[ObservationRecord]]

  /** Close and flush any pending writes. */
  def close: IO[Unit]
}
```

### Observation Record Schema

```scala
final case class ObservationRecord(
    // Identity
    executionId: UUID,
    dagName: String,
    structuralHash: String,        // Pipeline version (content-addressed)
    timestamp: Long,               // Epoch millis

    // Scope
    scope: ObservationScope,       // Pipeline | Module

    // Module-level (when scope == Module)
    moduleId: Option[UUID],
    moduleName: Option[String],

    // Data
    inputs: Map[String, Json],     // Serialized CValues
    output: Option[Json],          // Serialized CValue (module output or pipeline outputs)

    // Metadata
    durationMs: Long,
    status: ExecutionStatus,       // Completed | Failed
    error: Option[String],
    tags: Map[String, String]      // User-defined tags (environment, region, etc.)
)

sealed trait ObservationScope
object ObservationScope {
  case object Pipeline extends ObservationScope
  case object Module extends ObservationScope
}
```

### Observation Query

```scala
final case class ObservationQuery(
    dagName: Option[String] = None,
    structuralHash: Option[String] = None,
    moduleName: Option[String] = None,
    scope: Option[ObservationScope] = None,
    status: Option[ExecutionStatus] = None,
    startTime: Option[Long] = None,
    endTime: Option[Long] = None,
    tags: Map[String, String] = Map.empty,
    limit: Int = 100
)
```

### Sampling Configuration

```scala
final case class ObservationConfig(
    enabled: Boolean = false,
    backend: RecordingBackend,
    defaultSampleRate: Double = 0.01,          // 1% of executions
    pipelineSampleRates: Map[String, Double] = Map.empty,  // Per-pipeline overrides
    moduleSampleRates: Map[String, Double] = Map.empty,    // Per-module overrides
    maxInputSizeBytes: Int = 1_048_576,        // 1MB cap per input value
    tags: Map[String, String] = Map.empty,     // Global tags (env, region)
    batchSize: Int = 100,                      // Buffer before flushing
    flushInterval: FiniteDuration = 5.seconds
)
```

### Built-In Recording Backends

| Backend | Module | Use Case |
|---------|--------|----------|
| `LocalJsonRecordingBackend` | `core` (built-in) | Development, CI, small-scale testing |
| `BigQueryRecordingBackend` | `constellation-observation-bq` | Production at scale |
| `BigTableRecordingBackend` | `constellation-observation-bt` | Low-latency, high-throughput |

The local JSON backend writes NDJSON files to a configurable directory, one file per pipeline per day:

```
observations/
  user-enrichment/
    2026-02-10.ndjson
    2026-02-11.ndjson
  scoring-pipeline/
    2026-02-10.ndjson
```

### Wiring Example

```scala
val observationConfig = ObservationConfig(
  enabled = true,
  backend = BigQueryRecordingBackend(
    projectId = "my-project",
    dataset = "constellation_observations",
    table = "module_io"
  ),
  defaultSampleRate = 0.05,  // 5% of traffic
  pipelineSampleRates = Map(
    "critical-pipeline" -> 1.0  // 100% for critical pipelines
  ),
  tags = Map("env" -> "production", "region" -> "us-east-1")
)

val constellation = Constellation.builder
  .withObservation(observationConfig)  // Registers the ObservableExecutionListener
  .build
```

---

## Part 2: Native `.cst` Test Syntax

### Test File Convention

Test files use the `.test.cst` extension and live alongside the pipeline they test:

```
pipelines/
  user-enrichment.cst
  user-enrichment.test.cst       # Tests for user-enrichment
  scoring/
    lead-scoring.cst
    lead-scoring.test.cst
```

### Syntax

A `.test.cst` file is a valid constellation-lang file with additional `test` blocks:

```constellation
# Import the pipeline under test
use pipeline "user-enrichment"

# Define test modules/mocks (optional)
mock Uppercase as identity    # Replace Uppercase with passthrough
mock FetchProfile as static { name: "Alice", tier: "premium" }

# ===== Test Blocks =====

test "basic enrichment" {
    # Provide inputs
    in events = { userId: "u1", action: "click", timestamp: 1000 }
    in profiles = { userId: "u1", name: "Alice", tier: "premium" }

    # Assert outputs
    assert scored.userId == "u1"
    assert scored.tier == "premium"
    assert alert.alerted == true
}

test "missing profile falls back gracefully" {
    in events = { userId: "u2", action: "click", timestamp: 2000 }
    # profiles intentionally omitted — tests fallback behavior

    assert scored.userId == "u2"
    assert scored.tier == "unknown"
}

test "premium events trigger alerts" {
    in events = { userId: "u3", action: "purchase", timestamp: 3000 }
    in profiles = { userId: "u3", name: "Bob", tier: "premium" }

    assert alert.alerted == true
    assert scored.action == "purchase"
}
```

### Language Constructs

#### `test` Block

```constellation
test "<name>" {
    <input declarations>
    <assertions>
    <optional snapshot declarations>
}
```

- Each `test` block is an independent execution of the pipeline under test
- Inputs are isolated per test (no shared state)
- All `test` blocks in a file share the same `mock` declarations

#### `assert` Statement

```constellation
# Equality
assert result == expected_value
assert output.field == "value"
assert count > 0

# Type assertions
assert result is String
assert output is { name: String, age: Int }

# Optional assertions
assert optional_field != none
assert optional_field ?? "default" == "default"

# List assertions
assert items.length == 3
assert items contains "hello"

# Approximate numeric comparison
assert score ~= 0.95 within 0.01
```

#### `mock` Declaration

```constellation
# Static mock: always returns the same value
mock ModuleName as static <expression>

# Identity mock: returns its input unchanged
mock ModuleName as identity

# Transform mock: applies an inline transformation
mock ModuleName as (input) => { result: input.text, status: "mocked" }

# Failing mock: simulates module failure
mock ModuleName as failing "Connection timeout"

# Delayed mock: simulates slow module
mock ModuleName as delayed 500ms static { result: "slow" }
```

#### `snapshot` Assertion

```constellation
test "output matches golden file" {
    in text = "Hello World"

    # First run: creates the snapshot file
    # Subsequent runs: compares against it
    snapshot result as "enrichment-basic"
}
```

Snapshot files are stored as JSON alongside tests:

```
pipelines/
  user-enrichment.test.cst
  __snapshots__/
    enrichment-basic.snap.json
```

#### `use pipeline` Import

```constellation
# Import a pipeline to test
use pipeline "user-enrichment"          # By name
use pipeline "./user-enrichment.cst"    # By relative path
```

### CLI Command

```bash
# Run all tests
constellation test

# Run tests in a specific file
constellation test pipelines/user-enrichment.test.cst

# Run a specific test by name
constellation test --filter "basic enrichment"

# Run with verbose output
constellation test --verbose

# Update snapshots
constellation test --update-snapshots

# Output JUnit XML (CI integration)
constellation test --reporter junit --output test-results.xml

# Output JSON report
constellation test --reporter json --output test-results.json
```

### Test Execution Model

```
┌─────────────────────┐
│ .test.cst file       │
│ ┌─────────────────┐ │
│ │ use pipeline    │ │──▶ Load pipeline under test
│ │ mock X as ...   │ │──▶ Register mock modules
│ │ test "..." {    │ │──▶ For each test block:
│ │   in x = ...    │ │    1. Compile pipeline (cached)
│ │   assert y == z │ │    2. Override modules with mocks
│ │ }               │ │    3. Execute with provided inputs
│ └─────────────────┘ │    4. Evaluate assertions against outputs
└─────────────────────┘    5. Report pass/fail
```

The test runner:
1. Parses the `.test.cst` file (extended parser)
2. Compiles the referenced pipeline normally
3. Replaces specified modules with mock implementations
4. For each `test` block: injects inputs, runs pipeline, evaluates assertions
5. Aggregates results and reports

---

## Part 3: Scala Test DSL (`PipelineTestKit`)

For Scala developers embedding Constellation, a fluent test DSL reduces boilerplate:

### PipelineTestKit API

```scala
import io.constellation.testing._

class UserEnrichmentTest extends AnyFlatSpec with PipelineTestKit {

  // One-liner pipeline compilation + module registration
  val pipeline = compilePipeline("pipelines/user-enrichment.cst")
    .withModules(ExampleLib.allModules)
    .build

  "user enrichment" should "enrich events with profiles" in {
    pipeline.test
      .input("events", record("userId" -> "u1", "action" -> "click"))
      .input("profiles", record("userId" -> "u1", "name" -> "Alice", "tier" -> "premium"))
      .mock("FetchExternal", static(record("score" -> 0.95)))
      .run()
      .assertOutput("scored", _.field("userId") shouldBe cstring("u1"))
      .assertOutput("scored", _.field("tier") shouldBe cstring("premium"))
  }

  it should "handle missing profiles" in {
    pipeline.test
      .input("events", record("userId" -> "u2", "action" -> "click"))
      // profiles intentionally omitted
      .run()
      .assertOutput("scored", _.field("tier") shouldBe cstring("unknown"))
  }
}
```

### Builder Helpers

```scala
// CValue construction helpers
import io.constellation.testing.CValueBuilders._

record("name" -> "Alice", "age" -> 42)         // CValue.CMap(...)
cstring("hello")                                 // CValue.CString("hello")
cint(42)                                         // CValue.CInt(42)
clist(cstring("a"), cstring("b"))               // CValue.CList(Vector(...))
cbool(true)                                      // CValue.CBool(true)

// Module mock helpers
import io.constellation.testing.MockModules._

static(record("result" -> "mocked"))            // Always returns same value
identity[MyInput]                                // Passes input through
failing("timeout")                               // Always fails
recording[MyInput, MyOutput](realModule)        // Wraps real module, records I/O
conditional { input =>                           // Input-dependent mock
  if (input.field("userId") == cstring("u1"))
    record("tier" -> "premium")
  else
    record("tier" -> "free")
}
```

### Assertion Helpers

```scala
// Output assertions
result.assertOutput("scored") { output =>
  output should haveField("userId", cstring("u1"))
  output should haveField("tier", cstring("premium"))
  output shouldNot haveField("internal_id")
}

// Status assertions
result.assertCompleted()
result.assertFailed()
result.assertSuspended(missingInputs = Set("approval"))

// Snapshot testing
result.assertOutputMatchesSnapshot("scored", "enrichment-basic")

// Structural assertions
result.assertModuleWasCalled("Uppercase")
result.assertModuleWasNotCalled("ExpensiveML")  // Verify lazy/guard behavior
result.assertModuleDuration("SlowModule", _ < 100.millis)
```

### Parametric Testing

```scala
// Table-driven tests
val testCases = Table(
  ("input",              "expectedTier"),
  ("premium-user",       "premium"),
  ("free-user",          "free"),
  ("trial-user",         "trial")
)

forAll(testCases) { (userId, expectedTier) =>
  pipeline.test
    .input("userId", cstring(userId))
    .run()
    .assertOutput("result", _.field("tier") shouldBe cstring(expectedTier))
}
```

---

## Part 4: Replay Engine

The replay engine connects the observation store to the test framework, enabling production traffic replay with strategic mocking.

### Core Concept: Strategic Mocking

In a DAG with modules A → B → C → D, to test module C with real data:

```
Production observation store has:
  A: inputs → outputs (recorded)
  B: inputs → outputs (recorded)
  C: inputs → outputs (recorded)  ← module under test
  D: inputs → outputs (recorded)

Replay test:
  A: MOCKED with recorded outputs    ← upstream mocked
  B: MOCKED with recorded outputs    ← upstream mocked
  C: RUNS LIVE with recorded inputs  ← target module runs for real
  D: MOCKED or LIVE                  ← downstream configurable
```

The replay engine:
1. Queries the observation store for sample executions
2. For modules marked as mocked: injects their recorded outputs
3. For the target module: provides recorded inputs, runs it live
4. Compares live outputs against recorded outputs (regression detection)

### Replay Configuration

```scala
val replayConfig = ReplayConfig(
  source = observationBackend,

  // Which pipeline executions to replay
  query = ObservationQuery(
    dagName = Some("user-enrichment"),
    startTime = Some(oneDayAgo),
    status = Some(ExecutionStatus.Completed),
    tags = Map("env" -> "production")
  ),

  // How many samples to replay
  sampleSize = 100,

  // Strategic mocking: which modules to mock vs. run live
  strategy = ReplayStrategy.testModule(
    target = "ScoreEvent",           // Run this module live
    mockUpstream = true,             // Mock everything upstream with recorded outputs
    mockDownstream = true            // Mock everything downstream
  ),

  // Comparison mode
  comparison = ComparisonMode.Exact  // or Structural, Approximate(tolerance)
)
```

### Replay Strategies

```scala
sealed trait ReplayStrategy

object ReplayStrategy {

  /** Test a single module: mock everything else with recorded data. */
  case class TestModule(
      target: String,
      mockUpstream: Boolean = true,
      mockDownstream: Boolean = true
  ) extends ReplayStrategy

  /** Test the full pipeline: use recorded pipeline inputs, compare outputs. */
  case class FullPipeline(
      compareOutputs: Boolean = true
  ) extends ReplayStrategy

  /** Test a subgraph: run modules A through C live, mock the rest. */
  case class TestSubgraph(
      liveModules: Set[String],
      mockRest: Boolean = true
  ) extends ReplayStrategy

  /** Shadow mode: run live pipeline alongside recorded, compare results. */
  case class Shadow(
      tolerances: Map[String, ComparisonMode] = Map.empty
  ) extends ReplayStrategy
}
```

### Comparison Modes

```scala
sealed trait ComparisonMode

object ComparisonMode {
  /** Exact equality of CValues. */
  case object Exact extends ComparisonMode

  /** Structural match: same fields and types, values may differ. */
  case object Structural extends ComparisonMode

  /** Numeric values within tolerance, strings exact. */
  case class Approximate(tolerance: Double = 0.01) extends ComparisonMode

  /** Custom comparison function. */
  case class Custom(compare: (CValue, CValue) => Boolean) extends ComparisonMode
}
```

### Replay Execution Flow

```
┌──────────────────┐
│ Observation Store │
│ (BigQuery, etc.) │
└────────┬─────────┘
         │ query + sample
         ▼
┌──────────────────┐     ┌─────────────────────────────────┐
│  Replay Engine   │────▶│  For each sampled execution:     │
│                  │     │                                   │
│  - Fetch records │     │  1. Load pipeline under test      │
│  - Build mocks   │     │  2. For mocked modules:           │
│  - Run pipeline  │     │     Create static mock from       │
│  - Compare       │     │     recorded outputs              │
│                  │     │  3. For live modules:              │
│                  │     │     Use real implementation        │
│                  │     │  4. Inject recorded pipeline       │
│                  │     │     inputs                         │
│                  │     │  5. Execute pipeline               │
│                  │     │  6. Compare live vs recorded       │
│                  │     │     outputs                        │
│                  │     │  7. Report: pass/fail/diff         │
└──────────────────┘     └─────────────────────────────────┘
```

### Scala API

```scala
import io.constellation.testing.replay._

class ScoreModuleReplayTest extends AnyFlatSpec with ReplayTestKit {

  val backend = LocalJsonRecordingBackend("observations/")

  "ScoreEvent module" should "produce compatible outputs on recent traffic" in {
    replay(
      pipeline = "user-enrichment",
      backend = backend,
      strategy = ReplayStrategy.TestModule(target = "ScoreEvent"),
      sampleSize = 50,
      comparison = ComparisonMode.Approximate(0.01)
    ).assertAllPassed()
  }

  it should "handle the edge cases from last week's incident" in {
    replay(
      pipeline = "user-enrichment",
      backend = backend,
      query = ObservationQuery(
        startTime = Some(incidentStart),
        endTime = Some(incidentEnd),
        tags = Map("env" -> "production")
      ),
      strategy = ReplayStrategy.FullPipeline(),
      comparison = ComparisonMode.Exact
    ).assertAllPassed()
  }
}
```

### `.cst` Replay Syntax

Replay is also accessible from `.test.cst` files:

```constellation
use pipeline "user-enrichment"

# Replay tests pull data from the observation store
replay "ScoreEvent regression" {
    from observations
    where dagName == "user-enrichment"
    where timestamp > 2026-02-01
    sample 50

    # Strategic mocking
    target ScoreEvent          # Run this module live
    mock upstream              # Mock everything feeding into ScoreEvent
    mock downstream            # Mock everything downstream

    # Comparison
    compare exact              # or: approximate 0.01, structural
}

replay "full pipeline regression on recent traffic" {
    from observations
    where dagName == "user-enrichment"
    where tags.env == "production"
    sample 100

    # No target = full pipeline replay
    compare approximate 0.01
}

replay "reproduce incident" {
    from observations
    where executionId == "abc-123-def"

    target ScoreEvent
    mock upstream
    compare exact
}
```

---

## Part 5: Observation Data Lifecycle

### Data Flow

```
Production Runtime
       │
       │ ExecutionListener.onModuleObserved()
       ▼
┌──────────────┐
│ Observation   │──── sampling filter (1-100%)
│ Buffer        │──── size cap (1MB per value)
│ (in-memory)   │──── batching (100 records / 5s)
└──────┬───────┘
       │ writeBatch()
       ▼
┌──────────────┐
│ Recording     │     BigQuery / BigTable / Local JSON / Custom
│ Backend       │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ Observation   │     Queryable by: pipeline, module, time range,
│ Store         │     execution ID, tags, status
└──────┬───────┘
       │ query() / sample()
       ▼
┌──────────────┐
│ Replay Engine │     Strategic mocking + comparison
│ / Test Runner │
└──────────────┘
```

### Retention and Cleanup

Observation data can grow large. The `RecordingBackend` contract does not manage retention — this is delegated to the underlying store:

- **BigQuery:** Partition by date, set table expiration (e.g., 30 days)
- **BigTable:** Column family TTL (e.g., 7 days for module I/O, 90 days for pipeline I/O)
- **Local JSON:** Configurable file rotation and max directory size

### Privacy and Redaction

Production data may contain sensitive values. The observation plugin supports a redaction hook:

```scala
val config = ObservationConfig(
  // ...
  redactor = Some(ObservationRedactor { record =>
    record.copy(
      inputs = record.inputs.map { case (k, v) =>
        if (sensitiveFields.contains(k)) (k, Json.fromString("[REDACTED]"))
        else (k, v)
      }
    )
  })
)
```

Redaction runs **before** the record leaves the process — sensitive data never reaches the recording backend.

---

## Implementation Phases

### Phase 1: Observation Plugin

**Scope:** Extended listener + local JSON backend + sampling

- [ ] Extend `ExecutionListener` with `ObservableExecutionListener` trait
- [ ] Capture module inputs/outputs in `Runtime.runWithBackends`
- [ ] `ObservationRecord` data model and JSON serialization
- [ ] `RecordingBackend` trait
- [ ] `LocalJsonRecordingBackend` implementation
- [ ] `ObservationConfig` with sampling rates
- [ ] In-memory buffering with batch flush
- [ ] Redaction hook
- [ ] Integration with `Constellation.builder.withObservation()`
- [ ] Unit tests with in-memory backend

**Deliverable:** Developers can enable observation in any Constellation instance and capture module I/O to local files.

### Phase 2: Native `.cst` Test Syntax

**Scope:** Parser extensions + test runner + CLI command

- [ ] Parser: `test` block syntax
- [ ] Parser: `assert` statement with comparison operators
- [ ] Parser: `mock` declarations (static, identity, transform, failing, delayed)
- [ ] Parser: `use pipeline` import
- [ ] Parser: `snapshot` assertion
- [ ] AST: `TestBlock`, `Assertion`, `MockDeclaration` nodes
- [ ] Test runner: compile pipeline, inject mocks, execute, evaluate assertions
- [ ] Snapshot file management (create, compare, update)
- [ ] CLI: `constellation test` command
- [ ] CLI: `--filter`, `--verbose`, `--update-snapshots`, `--reporter` flags
- [ ] JUnit XML and JSON reporters for CI

**Deliverable:** Pipeline authors can write and run `.test.cst` files without Scala.

### Phase 3: Scala Test DSL (`PipelineTestKit`)

**Scope:** Fluent Scala API for embedded testing

- [ ] `PipelineTestKit` trait (mixin for ScalaTest)
- [ ] `compilePipeline()` builder with module registration
- [ ] `CValueBuilders` — `record()`, `cstring()`, `cint()`, `clist()`, etc.
- [ ] `MockModules` — `static()`, `identity()`, `failing()`, `recording()`, `conditional()`
- [ ] Assertion helpers — `haveField`, `assertOutput`, `assertCompleted`, `assertModuleWasCalled`
- [ ] Snapshot testing integration
- [ ] Parametric testing with ScalaTest `Table`

**Deliverable:** Scala developers can test pipelines with minimal boilerplate.

### Phase 4: Replay Engine

**Scope:** Observation store queries + strategic mocking + comparison

- [ ] `ReplayConfig` and `ReplayStrategy` data models
- [ ] `ReplayEngine` — queries observation store, builds mock map, executes pipeline
- [ ] Strategic mocking: `TestModule`, `FullPipeline`, `TestSubgraph`, `Shadow`
- [ ] `ComparisonMode` — `Exact`, `Structural`, `Approximate`, `Custom`
- [ ] Diff reporting (what changed between recorded and live output)
- [ ] `ReplayTestKit` trait for Scala tests
- [ ] `.test.cst` `replay` block syntax
- [ ] Parser: `replay` block with `from`, `where`, `sample`, `target`, `mock`, `compare`
- [ ] CLI: `constellation test --replay` mode
- [ ] Replay report generation (HTML, JSON)

**Deliverable:** Tests can replay production traffic with surgical control over which modules run live.

### Phase 5: External Backends + Advanced Features

**Scope:** BigQuery/BigTable backends, property testing, coverage

- [ ] `constellation-observation-bq` module (BigQuery backend)
- [ ] `constellation-observation-bt` module (BigTable backend)
- [ ] Property-based test generation from observation data (schema-aware fuzzing)
- [ ] `.cst` pipeline coverage reporting (which nodes were exercised)
- [ ] CI integration: GitHub Actions workflow for replay regression tests
- [ ] Dashboard integration: test results panel, replay controls

---

## Example: Full Lifecycle

### 1. Enable Observation in Production

```scala
val constellation = Constellation.builder
  .withModules(allModules)
  .withObservation(ObservationConfig(
    enabled = true,
    backend = BigQueryRecordingBackend(project, dataset, table),
    defaultSampleRate = 0.05,
    tags = Map("env" -> "production")
  ))
  .build
```

### 2. Write `.cst` Tests with Static Fixtures

```constellation
# scoring.test.cst
use pipeline "scoring"

mock FetchProfile as static { name: "Test", tier: "premium" }

test "premium users get high score" {
    in userId = "u1"
    assert score > 0.8
}
```

### 3. Run Tests Locally

```bash
$ constellation test scoring.test.cst
scoring.test.cst
  [PASS] premium users get high score (12ms)

1 passed, 0 failed
```

### 4. Add Replay Tests Against Production Traffic

```constellation
# scoring.test.cst (extended)
use pipeline "scoring"

# ... static tests above ...

replay "ScoreEvent regression on last 24h traffic" {
    from observations
    where dagName == "scoring"
    where timestamp > now - 24h
    sample 200

    target ScoreEvent
    mock upstream
    compare approximate 0.05
}
```

### 5. Run Replay in CI

```bash
$ constellation test scoring.test.cst --reporter junit --output results.xml
scoring.test.cst
  [PASS] premium users get high score (12ms)
  [PASS] ScoreEvent regression on last 24h traffic (200/200 samples passed) (8.4s)

2 passed, 0 failed
```

### 6. Investigate a Failure

```bash
$ constellation test --filter "ScoreEvent regression" --verbose
scoring.test.cst
  [FAIL] ScoreEvent regression on last 24h traffic (198/200 passed, 2 failed)

  Sample execution-id: abc-123
    Input:  { userId: "u999", action: "refund", amount: -50.0 }
    Expected output: { score: 0.2, risk: "low" }
    Actual output:   { score: 0.8, risk: "high" }
    Diff: score: 0.2 -> 0.8, risk: "low" -> "high"

  Sample execution-id: def-456
    Input:  { userId: "u888", action: "refund", amount: -200.0 }
    ...
```

---

## Open Questions

### 1. Observation granularity for inline transforms

Inline transforms (merges, projections, field access) are not module calls — they execute inside the runtime without going through the `ExecutionListener`. Should the observation plugin capture intermediate inline transform values, or only module boundaries?

**Leaning:** Module boundaries only. Inline transforms are deterministic functions of their inputs and can be recomputed. Capturing them would significantly increase data volume with little testing value.

### 2. Replay with schema evolution

When a pipeline changes (new fields, removed modules, renamed inputs), recorded observations may not match the current schema. Should the replay engine handle migration automatically, or fail-fast on schema mismatch?

**Leaning:** Fail-fast with clear error messages. Schema migration is a pipeline author concern. The replay engine should report which fields/modules are missing and suggest re-recording.

### 3. Determinism of mock injection

When mocking upstream modules, the mock outputs must be consistent with what the target module received in production. If the DAG topology changes between recording and replay, the mock injection may produce different data flow. How strictly should topology be validated?

**Leaning:** Validate that the target module's direct input edges match the recorded schema. Upstream topology changes are fine as long as the target module receives compatible inputs.

### 4. Test isolation for stateful modules

If modules have internal state (caches, counters), replay tests may produce different results than production. Should the replay engine reset module state between samples?

**Leaning:** Yes — each replay sample should execute with a fresh module instance (matching the batch execution model where modules are initialized per execution).

### 5. Privacy-aware replay

Even with redaction in the observation plugin, replaying production data in CI/local environments may violate data governance policies. Should the framework support synthetic data generation from observation schemas (preserving statistical properties without real values)?

### 6. Interaction with RFC-025 Streaming Pipelines

For streaming pipelines, observation would capture per-element module I/O (potentially very high volume). Should streaming observation use different sampling strategies (time-based windows, reservoir sampling)?

---

## Rejected Alternatives

### Record at the HTTP API layer instead of the runtime layer

Capturing request/response at the HTTP API level would miss per-module granularity — only pipeline-level inputs/outputs would be recorded. The whole value proposition of strategic mocking requires module-level observation.

### Use a separate sidecar process for observation

A sidecar approach (intercepting module calls via proxy) would add latency and complexity. The `ExecutionListener` SPI provides zero-overhead hooks that are already in the execution path.

### Make observation part of the language (e.g., `observe` keyword)

Observation is an operational concern, not a pipeline authoring concern. Pipeline authors should not need to modify their `.cst` files to enable/disable observation. The SPI approach keeps it entirely in deployment configuration.

### Build a custom test runner instead of extending the CLI

A separate test binary would fragment the toolchain. Extending `constellation test` keeps testing as a first-class CLI operation alongside `constellation compile` and `constellation run`.

---

## References

- **RFC-014:** Suspendable Execution — `DataSignature` captures execution state
- **RFC-015:** Pipeline Lifecycle — hot/cold loading, canary releases
- **RFC-021:** Constellation CLI — `constellation test` extends the CLI
- **RFC-025:** Streaming Pipelines — streaming observation interaction
- **Existing SPI:** `ExecutionListener`, `MetricsProvider`, `ExecutionStorage`
- **Existing infra:** `ExecutionTracker` (in-memory), `ExecutionWebSocket` (real-time events)
