# Changelog

All notable changes to Constellation Engine will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Removed (Breaking)

#### Deprecated API Removal (v1 RC)
- **`DagRegistry`**: Deleted `DagRegistry` trait and `DagRegistryImpl` class. Use `ProgramStore` for storing and retrieving compiled programs.
- **`Constellation` legacy methods**: Removed `dagExists`, `createDag`, `setDag`, `listDags`, `getDag`, `runDag`, `runDagSpec`, `runDagWithModules`, `runDagWithModulesAndPriorities`, and `runDagCancellable`. Use `constellation.run(LoadedProgram, inputs)` or `constellation.run(ref, inputs, options)` instead.
- **`CompileResult`**: Replaced internal `CompileResult` with package-private `DagCompileOutput`. External callers use `CompilationOutput` (unchanged).
- **`CompilationOutput` deprecated accessors**: Removed `.dagSpec`, `.syntheticModules`, `.moduleOptions` convenience accessors. Use `.program.image.dagSpec`, `.program.syntheticModules`, `.program.image.moduleOptions` instead.
- **`TypeChecker.checkLegacy`**: Removed unused private method.
- **`/dags` HTTP endpoints**: Removed `GET /dags` and `GET /dags/{dagName}`. Use `GET /programs` and `GET /programs/{ref}` instead.
- **`DagListResponse` / `DagResponse` API models**: Removed from HTTP API models.

### Added

#### Documentation (RFC-013 Phase 6)
- **Embedding Guide** (`docs/embedding-guide.md`): End-to-end guide for embedding Constellation in a JVM application — dependencies, minimal setup, complete runnable example, custom modules, production configuration (scheduler, backends, lifecycle, circuit breakers), and optional HTTP server.
- **Security Model** (`docs/security.md`): Trust model documentation covering constellation-lang sandboxing, module permissions, HTTP hardening (auth, CORS, rate limiting), input validation, error information disclosure, dependency audit, and production recommendations.
- **Error Reference** (`docs/error-reference.md`): Structured catalog of all error types — type errors (TYPE_MISMATCH, TYPE_CONVERSION), compiler errors (NODE_NOT_FOUND, UNDEFINED_VARIABLE, CYCLE_DETECTED, UNSUPPORTED_OPERATION), runtime errors (MODULE_NOT_FOUND, MODULE_EXECUTION, INPUT_VALIDATION, DATA_NOT_FOUND, RUNTIME_NOT_INITIALIZED, VALIDATION_ERROR), execution lifecycle errors (CircuitOpenException, QueueFullException, ShutdownRejectedException), and HTTP error responses (401, 403, 429, 503).
- **Performance Tuning Guide** (`docs/performance-tuning.md`): Production tuning guide covering scheduler configuration (bounded vs unbounded, maxConcurrency sizing, starvation timeout), timeout strategy, circuit breaker tuning, cache configuration, object pool tuning, JVM settings (G1GC/ZGC, heap sizing), monitoring instrumentation points, and diagnostic checklist.
- **SPI Integration Guides** (`docs/integrations/spi/`): Five integration guides with trait API documentation and example implementations:
  - MetricsProvider — Prometheus/Micrometer and Datadog StatsD examples
  - TracerProvider — OpenTelemetry/otel4s and Jaeger examples
  - ExecutionListener — Kafka event publishing and Doobie database audit examples
  - CacheBackend — Redis/redis4cats and Caffeine examples
  - ExecutionStorage — PostgreSQL/Doobie and SQLite examples
- **Dashboard & Tooling Guide** (`docs/tooling.md`): Documentation for the web dashboard (file browser, script editor, DAG visualization, execution history), VSCode extension (features, shortcuts, configuration), Playwright dev loop, and E2E testing.
- **Migration Guide** (`docs/migration/v0.3.0.md`): v0.2.x to v0.3.0 migration guide covering non-breaking changes, API additions (ConstellationBackends, ConstellationBuilder, CancellableExecution, CircuitBreaker, ConstellationLifecycle, GlobalScheduler, ServerBuilder methods), opt-in steps, new environment variables, and deployment artifacts.
- **OpenAPI Spec Update** (`docs/api/openapi.yaml`): Added `/health/live`, `/health/ready`, `/health/detail` endpoints; `bearerAuth` security scheme; `HealthLiveResponse`, `HealthReadyResponse`, `HealthDetailResponse` schemas; 401/403 error responses on applicable endpoints.
- **Architecture Update** (`docs/architecture.md`): Added Backend SPI Layer section with ConstellationBackends diagram, Execution Lifecycle section (cancellation, lifecycle state machine, circuit breaker, bounded scheduler), and HTTP Hardening section (middleware stack order).
- **LLM Guide Update** (`llm.md`): Added `spi/` and `execution/` directory listings to Module Structure, `http-api` middleware file listings, ConstellationBackends to Key Concepts as section 5.
- **Core Features Update** (`docs/dev/core-features.md`): Added Lifecycle Management section (graceful shutdown, cancellable execution, circuit breakers, bounded scheduler) and SPI Hook Points section.
- **Docs README Update** (`docs/README.md`): Reorganized documentation table with new sections for Getting Started, Architecture & Security, Tooling, SPI Integration Guides, and Migration.

#### Deployment Examples (RFC-013 Phase 4)
- **Fat JAR**: sbt-assembly integration for `exampleApp` module. Build with `make assembly`.
- **Dockerfile**: Multi-stage build (JDK 17 builder, JRE 17 runtime). Non-root user, built-in HEALTHCHECK on `/health/live`.
- **Docker Compose**: Dev stack with configurable env vars and health checks. Monitoring section scaffolded for future `/metrics` endpoint.
- **Kubernetes Manifests**: Namespace, Deployment (liveness/readiness probes, resource limits, security context), Service (ClusterIP), and ConfigMap in `deploy/k8s/`.
- **Makefile Targets**: `make assembly`, `make docker-build`, `make docker-run`.

#### HTTP API Hardening (RFC-013 Phase 3)
- **API Authentication**: Static API key authentication with role-based access control (Admin, Execute, ReadOnly). Opt-in via `.withAuth(AuthConfig(...))`. Supports `Authorization: Bearer <key>` header. Public paths (`/health`, `/metrics`) bypass auth.
- **CORS Middleware**: Configurable cross-origin request support via `.withCors(CorsConfig(...))`. Delegates to http4s built-in CORS. Supports wildcard and specific origin lists.
- **HTTP Rate Limiting**: Per-IP token bucket rate limiting via `.withRateLimit(RateLimitConfig(...))`. Returns `429 Too Many Requests` with `Retry-After` header. Health and metrics endpoints exempt.
- **Deep Health Checks**: New endpoints — `/health/live` (liveness probe), `/health/ready` (readiness with custom checks), `/health/detail` (opt-in full diagnostics). Existing `/health` endpoint unchanged.
- All hardening features disabled by default — zero overhead and identical behavior for existing users.

#### Documentation
- **README Rewrite**: Complete rewrite with sales pitch, compelling examples, and clear value proposition
- **LICENSE File**: Added MIT license file to repository root
- Standardized Java version requirement to JDK 17+ across all docs
- Updated all documentation to use `make` commands instead of raw `sbt`

#### Type System
- **Bidirectional Type Inference**: Implemented bidirectional type checking for improved type inference (#120)
  - `Mode.scala`: Inference (⇑) and Checking (⇓) modes with rich TypeContext for error messages
  - `BidirectionalTypeChecker.scala`: Full bidirectional type checking implementation
  - Lambda parameter inference: `filter(users, (u) => u.active)` now infers `u` as record type from context
  - Empty list typing: `[]` infers as `List<Nothing>`, compatible with any `List<T>` via subtyping
  - Subsumption rule: Automatically applies when inferred type is subtype of expected type
  - Enhanced error messages: Reports "in argument 2 ('predicate') of filter" instead of generic errors
  - Full backward compatibility: Existing explicit annotations continue to work

- **Subtyping System**: Implemented structural subtyping for the Constellation type system (#119)
  - `Subtyping.scala`: Core subtyping implementation with `isSubtype`, `lub` (least upper bound), `glb` (greatest lower bound)
  - `SNothing` as bottom type: Empty collections and conditionals now work seamlessly with typed collections
  - Covariant collections: `List<Nothing>` assignable to `List<T>`, `Candidates<Nothing>` to `Candidates<T>`
  - Record width + depth subtyping: Records with extra fields are subtypes of records expecting fewer fields
  - Union type handling: Conditional branches with different types produce union types via LUB
  - Function contravariance: Correct handling of function parameter and return type subtyping
  - `explainFailure`: Human-readable explanations for type error messages

- **Row Polymorphism**: Implemented row polymorphism for flexible record handling (#121)
  - `RowVar(id)`: Row variable type representing unknown additional fields in open records
  - `SOpenRecord(fields, rowVar)`: Open record type with specific fields plus a row variable for "rest"
  - `RowUnification.scala`: Row unification algorithm for matching closed records against open records
  - `Substitution`: Mapping from row variables to their resolved field sets with merge support
  - Updated `FunctionSignature` with `rowVars` field and `instantiate()` for fresh variable generation per call site
  - Updated `Subtyping`: Closed records with extra fields are subtypes of open records requiring fewer fields
  - Updated `BidirectionalTypeChecker`: Row-polymorphic function call handling with automatic row unification
  - `RecordFunctions.scala`: Row-polymorphic stdlib functions (GetName, GetAge, GetId, GetValue)
  - Example: `GetName: ∀ρ. { name: String | ρ } -> String` accepts any record with at least a `name` field

#### Runtime
- **Execution Tracker**: New system for capturing per-node execution data during DAG execution (#124)
  - `ExecutionTracker.scala`: Thread-safe tracker using Ref for concurrent access
  - `NodeStatus` enum: Pending, Running, Completed, Failed states
  - `NodeExecutionResult`: Captures status, value (JSON), duration, and error per node
  - `ExecutionTrace`: Complete trace with execution ID, DAG name, timestamps, and node results
  - LRU eviction: Configurable max traces to prevent unbounded memory growth
  - Value truncation: Large JSON values (>10KB default) are automatically truncated
  - `fromRuntimeState`: Helper to convert existing `Runtime.State` to execution trace
  - Enables DAG visualization to show execution state and runtime values

#### Compiler Improvements
- **IR Optimization Passes**: New optimization framework that reduces DAG size and improves runtime performance (#116)
  - `OptimizationPass.scala`: Base trait for implementing optimization passes
  - `IROptimizer.scala`: Orchestrator with iterative optimization until fixpoint
  - `DeadCodeElimination.scala`: Removes IR nodes not reachable from outputs
  - `ConstantFolding.scala`: Evaluates constant expressions at compile time (arithmetic, string concat, boolean ops)
  - `CommonSubexpressionElimination.scala`: Deduplicates identical computations
  - `OptimizationConfig.scala`: Configuration for enabling/disabling individual passes
  - Builder integration via `LangCompilerBuilder.withOptimization()`
  - Optimization is disabled by default for backward compatibility

- **Compilation Caching**: New `CompilationCache` and `CachingLangCompiler` classes that cache compilation results to avoid redundant parsing, type checking, and IR generation. Features include:
  - Thread-safe storage using cats-effect `Ref[IO, Map]`
  - LRU eviction policy with configurable max entries
  - TTL-based expiration with configurable max age
  - Cache statistics tracking (hits, misses, evictions, hit rate)
  - Builder integration via `LangCompilerBuilder.withCaching()`

- **Improved Error Messages**: Enhanced compiler error messages with structured error codes, explanations, "did you mean" suggestions, and documentation links (#115)

#### Parser Optimizations
- **Parser Performance Improvements**: Optimized Constellation parser for reduced backtracking and better efficiency (#117)
  - `MemoizationSupport.scala`: Thread-safe memoization infrastructure with cache hit/miss tracking for benchmarking
  - Replaced chained `|` alternatives with `P.oneOf` for O(1) alternative selection
  - `parseWithStats()` method for performance benchmarking with cache statistics
  - `ParserOptimizations` utilities for optimized choice combinators
  - `ErrorCode.scala`: Error code catalog with E001-E900 codes covering reference, type, syntax, semantic, and internal errors
  - `Suggestions.scala`: Levenshtein distance-based "Did you mean?" suggestions for typos
  - `ErrorFormatter.scala`: Rich error formatting with code snippets, caret markers, and multiple output formats (plain text, markdown, one-line)
  - LSP diagnostics now include error codes and contextual suggestions

#### LSP Improvements
- **Semantic Token Highlighting**: LSP server now provides semantic tokens for rich syntax highlighting in VSCode (#118)
  - Functions, types, variables, parameters, and properties highlighted distinctly
  - Modifier support (declaration, definition, readonly, defaultLibrary)
  - Graceful degradation on parse errors (falls back to TextMate grammar)
  - Delta-encoded token format per LSP specification
  - VSCode extension registers semantic token scopes for theme integration

- **Debounced Document Validation**: LSP server now debounces document change events to avoid excessive compilations during rapid typing. Reduces CPU usage by 10-20x during active editing.
  - Configurable debounce delay (default: 200ms)
  - Document save triggers immediate validation (bypasses debounce)
  - Document close cancels any pending validation
  - Independent debouncing per document URI

- **Completion Trie**: Replaced linear filtering with prefix trie for O(k) completion lookups instead of O(n) (#114)
  - `CompletionTrie.scala`: Trie data structure for efficient prefix-based lookups
  - Case-insensitive matching for better discoverability
  - Cached keyword completions (built once at initialization)
  - Module completions automatically updated when registry changes

## [0.2.0] - 2026-01-22

### Added

#### Language Features
- **Union Types**: Support for variant returns with `A | B` syntax
- **Lambda Expressions**: Higher-order functions with `|x| -> expr` syntax
- **String Interpolation**: Template strings with embedded expressions `"Hello ${name}"`
- **Branch Expressions**: Multi-way conditionals for complex decision logic
- **Guard Expressions**: Conditional filtering with `when` keyword
- **Coalesce Operator**: Null handling with `??` operator
- **Record Projection**: Curly brace syntax for field extraction `record{field1, field2}`
- **Candidates Merge**: Support for Candidates + Record broadcast merge and Candidates + Candidates element-wise merge

#### Core Infrastructure
- **RawValue Type**: Memory-efficient data representation for runtime values
- **Inline Transforms**: Lightweight operations for synthetic modules without UUID overhead
- **Object Pooling**: Infrastructure for reduced memory allocations
- **Custom Exception Hierarchy**: Structured domain errors (`TypeError`, `CompilerError`, `RuntimeError`) with JSON serialization
- **DebugMode Utility**: Optional runtime type validation via `CONSTELLATION_DEBUG=true` environment variable
- **Scala 3 Mirrors**: Automatic type derivation for cleaner module definitions
- **Structured Logging**: Log4cats integration with configurable log levels

#### Developer Experience
- **Step-Through Execution**: Full debugging workflow in LSP and VSCode extension
- **DAG Visualizer Enhancements**:
  - Distinct node shapes by type
  - Data type color coding
  - Pretty-printed value previews
  - Simplified labels with tooltips
  - Edge hover highlighting
  - Zoom controls and fit-to-view
  - Search and node details panel
  - PNG/SVG export functionality
  - Execution state highlighting
- **Code Coverage**: Scoverage integration for test coverage reporting
- **Linting**: Scalafmt and Scalafix for code quality

#### Documentation
- **Getting Started Tutorial**: Comprehensive onboarding guide for new users
- **OpenAPI Specification**: Complete HTTP API documentation
- **Language Documentation**: Updated docs for all implemented features

### Changed
- Modularized StdLib into separate category files for better organization
- Standardized error handling patterns across modules
- Wrapped synchronous throws in Either-based error handling in lang-compiler
- Changed agent workflow protocol from PRs to direct merge for faster iteration

### Fixed
- Support for inline transforms in string interpolation and higher-order functions
- Parser infinite loop issue resolved
- Missing `UnsupportedArithmetic` case in LSP pattern match
- Boolean operators as keywords in StdLib
- Improved `IncompatibleMerge` error message to be more actionable
- Added logging to silent error handlers in LSP

### Tests
- Comprehensive test suites for:
  - TypedValueAccessor and InlineTransform (core)
  - JsonCValueConverter (runtime)
  - String interpolation compilation
  - Lambda expression compilation
  - Union type compilation and runtime
  - Guard, coalesce, and branch semantics
  - LSP server components (33% to 51% coverage)
  - HTTP API module
  - StdLib edge cases
  - VSCode extension (unit, integration, and e2e tests)
  - DAG layout computation and rendering
  - Step-through debugging workflow

## [0.1.0] - Initial Release

### Added
- Core type system with `CType` and `CValue`
- Module system with `ModuleBuilder` API
- Runtime execution engine with dependency resolution
- constellation-lang DSL parser
- DAG compiler with type checking and semantic analysis
- Standard library with common operations
- HTTP API server with WebSocket support
- LSP server for IDE integration
- VSCode extension with:
  - Syntax highlighting
  - Autocomplete
  - Diagnostics
  - Script runner
  - DAG visualizer

[0.2.0] - 2026-01-22: https://github.com/VledicFranco/constellation-engine/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/VledicFranco/constellation-engine/releases/tag/v0.1.0
