# Changelog

All notable changes to Constellation Engine will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

#### Compiler Improvements
- **Compilation Caching**: New `CompilationCache` and `CachingLangCompiler` classes that cache compilation results to avoid redundant parsing, type checking, and IR generation. Features include:
  - Thread-safe storage using cats-effect `Ref[IO, Map]`
  - LRU eviction policy with configurable max entries
  - TTL-based expiration with configurable max age
  - Cache statistics tracking (hits, misses, evictions, hit rate)
  - Builder integration via `LangCompilerBuilder.withCaching()`

#### LSP Improvements
- **Debounced Document Validation**: LSP server now debounces document change events to avoid excessive compilations during rapid typing. Reduces CPU usage by 10-20x during active editing.
  - Configurable debounce delay (default: 200ms)
  - Document save triggers immediate validation (bypasses debounce)
  - Document close cancels any pending validation
  - Independent debouncing per document URI

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
