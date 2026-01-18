# Constellation Engine - TODO List

This document tracks potential improvements, refactors, missing tests, and feature ideas for the Constellation Engine project.

## Table of Contents

1. [Code Cleanups](#code-cleanups)
2. [Refactoring Opportunities](#refactoring-opportunities)
3. [Missing Tests](#missing-tests)
4. [constellation-lang Missing Features](#constellation-lang-missing-features)
5. [Feature Ideas](#feature-ideas)
6. [Documentation Improvements](#documentation-improvements)
7. [Technical Debt](#technical-debt)

---

## Code Cleanups

### High Priority

- [ ] **Remove redundant import in ConstellationLanguageServer.scala**
  - Line 14 has both wildcard import and explicit imports from LspMessages
  - File: `modules/lang-lsp/src/main/scala/io/constellation/lsp/ConstellationLanguageServer.scala`

- [ ] **Clean up "Empty message" error logging in WebSocket handler**
  - Server logs `[WS] Error processing message: Empty message` frequently
  - Should either fix the root cause or reduce log verbosity
  - File: `modules/http-api/src/main/scala/io/constellation/http/LspWebSocketHandler.scala`

- [ ] **Remove unused .bloop and project/.bloop directories from git**
  - These are generated build artifacts that shouldn't be tracked
  - Add to .gitignore if not already present

- [ ] **Clean up `nul` file in root directory**
  - Appears to be an accidental Windows artifact

### Medium Priority

- [ ] **Consolidate duplicate type definitions**
  - `SemanticType` in lang-compiler and `CType` in core have overlap
  - Consider unifying or documenting the relationship more clearly

- [ ] **Standardize error message formatting**
  - Some errors include position info, others don't
  - Create consistent error formatting utilities

- [ ] **Remove debug print statements**
  - Check for any remaining `println` or debug logging that should be removed
  - Replace with proper logging framework if needed

### Low Priority

- [ ] **Organize imports consistently across files**
  - Some files use wildcard imports, others explicit
  - Consider adopting a consistent style

- [ ] **Add explicit return types to public methods**
  - Some public methods rely on type inference
  - Explicit types improve API clarity

---

## Refactoring Opportunities

### Architecture

- [ ] **Extract common webview patterns in VSCode extension**
  - `ScriptRunnerPanel.ts` and `DagVisualizerPanel.ts` share similar structure
  - Consider a base class or shared utilities for webview panels

- [ ] **Modularize StdLib functions**
  - Current `StdLib.scala` is large (400+ lines)
  - Split into separate files by category (math, string, list, etc.)

- [ ] **Unify function registration between StdLib and ExampleLib**
  - Both follow similar patterns
  - Create a common trait or base class for function libraries

- [ ] **Extract DAG layout algorithm from DagVisualizerPanel**
  - Current topological sort is embedded in JS
  - Could be moved to a separate utility module

### Code Quality

- [ ] **Replace magic strings with constants**
  - LSP method names (`"textDocument/completion"`, etc.)
  - HTTP endpoint paths
  - Error message keys

- [ ] **Add structured logging**
  - Replace `println` with proper logging (e.g., log4cats)
  - Add log levels (DEBUG, INFO, WARN, ERROR)

- [ ] **Improve error handling in LSP server**
  - Some errors are swallowed or logged without proper propagation
  - Add comprehensive error recovery

- [ ] **Type-safe message passing in VSCode webviews**
  - Current message protocol uses untyped objects
  - Define TypeScript interfaces for all message types

---

## Missing Tests

### Critical (No Test Coverage)

- [ ] **modules/lang-ast** - No tests
  - Add AST construction tests
  - Add Span position tests
  - Add equality and hash code tests

- [ ] **modules/example-app** - No tests
  - Add tests for DataModules (SumList, Average, etc.)
  - Add tests for TextModules (Uppercase, WordCount, etc.)
  - Add integration tests for ExampleLib compiler

- [ ] **DagVisualizerPanel** - No tests
  - Add tests for getDagStructure LSP endpoint
  - Add webview rendering tests (if possible)

- [ ] **ScriptRunnerPanel** - No tests
  - Add tests for input schema generation
  - Add tests for execution result handling

### Important (Limited Coverage)

- [ ] **TypeChecker edge cases**
  - Test type algebra (merge semantics)
  - Test error recovery behavior
  - Test complex nested types

- [ ] **Parser error messages**
  - Test that parse errors include useful position info
  - Test recovery from common syntax mistakes

- [ ] **Runtime execution**
  - Add tests for module timeout handling
  - Add tests for concurrent DAG execution
  - Add tests for error propagation through DAG

- [ ] **HTTP API**
  - Add tests for WebSocket LSP endpoint
  - Add tests for concurrent request handling
  - Add tests for error responses

### Nice to Have

- [ ] **Performance tests**
  - Benchmark compilation of large programs
  - Benchmark DAG execution with many nodes
  - Memory usage profiling

- [ ] **Property-based tests**
  - Use ScalaCheck for parser round-trip tests
  - Use ScalaCheck for type system invariants

---

## constellation-lang Missing Features

Language features documented or proposed but not yet implemented in the compiler.

### Operators (Core)

- [ ] **Field access operator (`.`)**
  - Syntax: `record.fieldName`
  - Currently only projections (`record[field1, field2]`) are supported
  - Needed for: accessing individual fields, nested record access
  - Files: `ConstellationParser.scala`, `AST.scala`, `TypeChecker.scala`

- [ ] **Comparison operators**
  - Syntax: `==`, `!=`, `<`, `>`, `<=`, `>=`
  - Return type: `Boolean`
  - Needed for: conditional guards, filtering, predicates
  - Files: `ConstellationParser.scala`, `AST.scala`, `TypeChecker.scala`, `IRGenerator.scala`

- [ ] **Boolean operators**
  - Syntax: `and`, `or`, `not`
  - Short-circuit evaluation semantics
  - Needed for: compound conditions, orchestration guards
  - Files: `ConstellationParser.scala`, `AST.scala`, `TypeChecker.scala`

- [ ] **Arithmetic operators**
  - Syntax: `+`, `-`, `*`, `/` for numeric types
  - Currently `+` only means merge for records
  - Needed for: numeric computations, scoring adjustments
  - Files: `ConstellationParser.scala` (disambiguate from merge), `TypeChecker.scala`

### Orchestration (Proposed in orchestration-algebra.md)

- [ ] **Guard expressions (`when`)**
  - Syntax: `result = expression when condition`
  - Returns `Optional<T>` when condition may be false
  - Needed for: conditional execution, feature flags, early termination
  - Files: `ConstellationParser.scala`, `AST.scala`, `TypeChecker.scala`, `IRGenerator.scala`

- [ ] **Optional<T> type**
  - Wrapper type for values that may be absent
  - Semantics: `Some(v)` or `None`
  - Needed for: guard expressions, safe field access, coalesce
  - Files: `SemanticType.scala`, `TypeChecker.scala`, `CType` in core

- [ ] **Coalesce operator (`??`)**
  - Syntax: `optional_expr ?? fallback_expr`
  - Returns first non-None value
  - Needed for: fallback chains, default values
  - Files: `ConstellationParser.scala`, `AST.scala`, `TypeChecker.scala`

- [ ] **Optional chaining (`?.`)**
  - Syntax: `optional_value?.field`
  - Safe field access that propagates None
  - Needed for: safe nested access on optional values
  - Files: `ConstellationParser.scala`, `AST.scala`, `TypeChecker.scala`

- [ ] **Branch expression**
  - Syntax: `branch { cond1 -> expr1, cond2 -> expr2, otherwise -> default }`
  - Multi-way conditional with exhaustiveness checking
  - Needed for: model selection, tiered processing
  - Files: `ConstellationParser.scala`, `AST.scala`, `TypeChecker.scala`

- [ ] **Aggregate predicates (`all`, `any`)**
  - Syntax: `all(collection, predicate)`, `any(collection, predicate)`
  - Boolean aggregation over collections
  - Needed for: validation gates, conditional processing
  - Files: Built-in functions or special forms in parser

### Type System Enhancements

- [ ] **Union types**
  - Syntax: `Type1 | Type2`
  - Needed for: variant returns, error handling
  - Files: `AST.scala` (TypeExpr), `SemanticType.scala`, `TypeChecker.scala`

- [ ] **Nullable types shorthand**
  - Syntax: `Type?` as shorthand for `Optional<Type>`
  - Needed for: ergonomic optional declarations
  - Files: `ConstellationParser.scala`, `AST.scala`

- [ ] **Type aliases with parameters**
  - Syntax: `type Result<T> = { value: T, error: String }`
  - Needed for: generic type definitions
  - Files: `ConstellationParser.scala`, `AST.scala`, `TypeChecker.scala`

### Expression Enhancements

- [ ] **String interpolation**
  - Syntax: `"Hello, ${name}!"`
  - Needed for: dynamic string construction
  - Files: `ConstellationParser.scala`, `AST.scala`

- [ ] **List literals**
  - Syntax: `[1, 2, 3]` or `["a", "b", "c"]`
  - Needed for: inline list construction
  - Files: `ConstellationParser.scala`, `AST.scala`, `TypeChecker.scala`

- [ ] **Record literals**
  - Syntax: `{ field1: value1, field2: value2 }`
  - Needed for: inline record construction
  - Files: `ConstellationParser.scala`, `AST.scala`, `TypeChecker.scala`

- [ ] **Let bindings in expressions**
  - Syntax: `let x = expr1 in expr2`
  - Needed for: local bindings within expressions
  - Files: `ConstellationParser.scala`, `AST.scala`, `TypeChecker.scala`

### Runtime Features

- [ ] **Lambda expressions**
  - Syntax: `(x) => x.field` or `(a, b) => a + b`
  - Needed for: `all`/`any` predicates, map/filter operations
  - Files: All compiler stages, runtime support

- [ ] **Pipeline operator**
  - Syntax: `input |> transform1 |> transform2`
  - Needed for: cleaner chaining of transformations
  - Files: `ConstellationParser.scala`, `AST.scala` (desugars to nested calls)

---

## Feature Ideas

### Short Term (Easy Wins)

- [ ] **DAG Visualizer: Layout direction toggle**
  - Allow switching between top-to-bottom and left-to-right layouts
  - Add button in visualizer header

- [ ] **DAG Visualizer: Export as PNG/SVG**
  - Add export button to save visualization
  - Useful for documentation

- [ ] **Script Runner: Save/Load input presets**
  - Allow saving input configurations
  - Quickly switch between test scenarios

- [ ] **Script Runner: Input history**
  - Remember recent input values
  - Auto-fill from history

- [ ] **Better autocomplete**
  - Include parameter names in autocomplete
  - Show expected types inline

### Medium Term (Moderate Effort)

- [ ] **DAG Visualizer: Execution highlighting**
  - Show which nodes have executed
  - Animate data flow during execution

- [ ] **DAG Visualizer: Node details panel**
  - Click node to see full input/output types
  - Show module documentation

- [ ] **Script Runner: Step-through execution**
  - Execute DAG one node at a time
  - Inspect intermediate values

- [ ] **Go to Definition for modules**
  - LSP support for navigating to module source
  - Works across Scala/constellation-lang boundary

- [ ] **Inline type hints**
  - Show inferred types inline in editor
  - Similar to TypeScript/Rust inlay hints

- [ ] **Watch mode for script execution**
  - Auto-re-run when source file changes
  - Configurable input values

### Long Term (Significant Effort)

- [ ] **Debugging support**
  - Breakpoints in constellation-lang
  - Step through execution
  - Variable inspection

- [ ] **Module marketplace/registry**
  - Share modules between projects
  - Version management

- [ ] **Visual DAG editor**
  - Drag-and-drop pipeline construction
  - Bi-directional sync with text

- [ ] **Distributed execution**
  - Execute DAG across multiple machines
  - Spark/Flink integration

- [ ] **Type inference for inputs**
  - Infer input types from usage
  - Optional explicit type annotations

- [ ] **Pattern matching in constellation-lang**
  - Match on union types
  - Destructuring for products

- [ ] **Module versioning and compatibility**
  - Semantic versioning enforcement
  - Compatibility checking between versions

---

## Documentation Improvements

### Code Documentation

- [ ] **Add ScalaDoc to all public APIs**
  - Spec.scala - document DagSpec, ModuleNodeSpec, DataNodeSpec
  - Runtime.scala - document execution model
  - IR.scala - document intermediate representation

- [ ] **Add examples to ScalaDoc**
  - Include usage examples in documentation
  - Link to related classes/methods

- [ ] **Document constellation-lang grammar formally**
  - EBNF or similar notation
  - Include in docs/ directory

### User Documentation

- [ ] **Getting Started tutorial**
  - Step-by-step guide for new users
  - Cover common use cases

- [ ] **Module development guide**
  - Best practices for creating modules
  - Testing strategies

- [ ] **Deployment guide**
  - Production deployment considerations
  - Configuration options

- [ ] **Troubleshooting guide**
  - Common errors and solutions
  - FAQ section

### API Documentation

- [ ] **REST API OpenAPI spec**
  - Generate OpenAPI/Swagger documentation
  - Include request/response examples

- [ ] **LSP protocol documentation**
  - Document custom LSP methods
  - Include message format examples

---

## Technical Debt

### Build System

- [ ] **Update to latest Scala 3.x**
  - Currently on 3.3.1
  - Evaluate newer versions for improvements

- [ ] **Add CI/CD pipeline**
  - GitHub Actions for automated testing
  - Automated release process

- [ ] **Add code coverage reporting**
  - Integrate with ScoveragePlugin
  - Set coverage thresholds

- [ ] **Add linting**
  - scalafmt for formatting
  - scalafix for refactoring rules

### Dependencies

- [ ] **Audit and update dependencies**
  - Check for security vulnerabilities
  - Update to latest stable versions

- [ ] **Reduce bundle size for VSCode extension**
  - Audit included dependencies
  - Consider code splitting

### Performance

- [ ] **Profile compilation performance**
  - Identify bottlenecks
  - Consider caching strategies

- [ ] **Optimize DAG execution**
  - Parallel execution of independent nodes
  - Memory-efficient value passing

- [ ] **WebSocket connection pooling**
  - Handle multiple concurrent LSP connections
  - Resource management

---

## Priority Matrix

| Priority | Category | Items |
|----------|----------|-------|
| **P0** | Tests | ExampleLib tests, AST tests |
| **P0** | Cleanup | Empty message logging, redundant imports |
| **P1** | Language | Field access (`.`), comparison operators, boolean operators |
| **P1** | Feature | DAG export, layout toggle |
| **P1** | Refactor | Extract webview base class |
| **P2** | Language | Guard expressions (`when`), Optional<T> type, coalesce (`??`) |
| **P2** | Feature | Execution highlighting, step-through |
| **P2** | Docs | Getting started tutorial |
| **P3** | Language | Branch expressions, lambdas, union types |
| **P3** | Feature | Visual DAG editor, debugging |
| **P3** | Tech Debt | CI/CD, code coverage |

---

## Contributing

When working on items from this list:

1. Create a branch: `feature/todo-item-name` or `fix/todo-item-name`
2. Reference this file in commit messages
3. Update this file when items are completed (mark with [x])
4. Add any new items discovered during development

---

**Last Updated:** 2026-01-18
