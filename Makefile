# Constellation Engine - Development Makefile
# Usage: make <target>

.PHONY: help dev server watch test compile clean extension ext-watch install all coverage coverage-report coverage-html fmt fmt-check lint lint-fix benchmark benchmark-compiler benchmark-viz benchmark-cache benchmark-lsp test-dashboard test-dashboard-smoke test-dashboard-full install-dashboard-tests dashboard dashboard-watch install-dashboard

# Default target
help:
	@echo "Constellation Engine - Development Commands"
	@echo ""
	@echo "Quick Start:"
	@echo "  make dev        - Start full dev environment (server + extension watch)"
	@echo "  make server     - Start the HTTP/LSP server only"
	@echo "  make test       - Run all tests"
	@echo ""
	@echo "Build Commands:"
	@echo "  make compile    - Compile all Scala modules"
	@echo "  make extension  - Compile VSCode extension"
	@echo "  make dashboard  - Compile dashboard TypeScript"
	@echo "  make all        - Compile everything (Scala + TypeScript)"
	@echo "  make clean      - Clean all build artifacts"
	@echo ""
	@echo "Watch Modes:"
	@echo "  make watch      - Watch Scala sources and recompile on changes"
	@echo "  make ext-watch        - Watch VSCode extension TypeScript"
	@echo "  make dashboard-watch  - Watch dashboard TypeScript"
	@echo ""
	@echo "Server Commands:"
	@echo "  make server         - Start with ExampleLib (all functions)"
	@echo "  make server-stdlib  - Start with StdLib only"
	@echo "  make server-rerun   - Start with hot-reload (requires sbt-revolver)"
	@echo ""
	@echo "Testing:"
	@echo "  make test           - Run all tests"
	@echo "  make test-core      - Test core module only"
	@echo "  make test-compiler  - Test compiler module only"
	@echo "  make test-lsp       - Test LSP module only"
	@echo "  make test-fast      - Run tests without recompilation"
	@echo ""
	@echo "Dashboard E2E Tests:"
	@echo "  make test-dashboard       - Run all dashboard E2E tests"
	@echo "  make test-dashboard-smoke - Quick smoke check (~30s)"
	@echo "  make test-dashboard-full  - Full suite with HTML report"
	@echo "  make install-dashboard-tests - Install Playwright + browsers"
	@echo ""
	@echo "Benchmarks:"
	@echo "  make benchmark          - Run all performance benchmarks"
	@echo "  make benchmark-compiler - Compiler pipeline benchmarks"
	@echo "  make benchmark-viz      - Visualization benchmarks"
	@echo "  make benchmark-cache    - Cache effectiveness benchmarks"
	@echo "  make benchmark-lsp      - LSP operations benchmarks"
	@echo ""
	@echo "Code Coverage:"
	@echo "  make coverage       - Run tests with coverage and generate reports"
	@echo "  make coverage-report- Generate coverage report (after running tests)"
	@echo "  make coverage-html  - Open HTML coverage report in browser"
	@echo ""
	@echo "Formatting and Linting:"
	@echo "  make fmt            - Format all Scala code with scalafmt"
	@echo "  make fmt-check      - Check formatting without changing files"
	@echo "  make lint           - Check for lint issues with scalafix"
	@echo "  make lint-fix       - Auto-fix lint issues where possible"
	@echo ""
	@echo "Setup:"
	@echo "  make install    - Install all dependencies"

# =============================================================================
# Quick Start
# =============================================================================

# Full development environment
dev:
	@echo "Starting development environment..."
	@echo "1. Starting server in background..."
	@$(MAKE) server &
	@sleep 5
	@echo "2. Starting extension watch..."
	@$(MAKE) ext-watch

# Start the HTTP/LSP server with all example functions
server:
	@echo "Starting Constellation server on http://localhost:8080..."
	@echo "LSP WebSocket: ws://localhost:8080/lsp"
	@echo "Press Ctrl+C to stop"
	sbt "exampleApp/runMain io.constellation.examples.app.server.ExampleServer"

# Start server with StdLib only (no example modules)
server-stdlib:
	@echo "Starting server with StdLib only..."
	sbt "httpApi/runMain io.constellation.http.examples.DemoServer"

# Start server with hot-reload (requires sbt-revolver plugin)
server-rerun:
	@echo "Starting server with hot-reload..."
	@echo "Server will restart automatically on code changes"
	sbt "~exampleApp/reStart"

# =============================================================================
# Build Commands
# =============================================================================

# Compile all Scala modules
compile:
	@echo "Compiling Scala modules..."
	sbt compile

# Compile VSCode extension
extension:
	@echo "Compiling VSCode extension..."
	cd vscode-extension && npm run compile

# Compile dashboard TypeScript
dashboard:
	@echo "Compiling dashboard TypeScript..."
	cd dashboard && npm run build

# Watch dashboard TypeScript
dashboard-watch:
	@echo "Watching dashboard TypeScript sources for changes..."
	@echo "Press Ctrl+C to stop"
	cd dashboard && npm run watch

# Compile everything
all: compile extension dashboard
	@echo "Build complete!"

# Clean all build artifacts
clean:
	@echo "Cleaning build artifacts..."
	sbt clean
	rm -rf vscode-extension/out
	cd dashboard && npm run clean 2>/dev/null || true
	rm -rf .bloop project/.bloop
	rm -rf target */target
	@echo "Clean complete!"

# =============================================================================
# Watch Modes
# =============================================================================

# Watch Scala sources and recompile
watch:
	@echo "Watching Scala sources for changes..."
	@echo "Press Ctrl+C to stop"
	sbt "~compile"

# Watch TypeScript and recompile
ext-watch:
	@echo "Watching TypeScript sources for changes..."
	@echo "Press Ctrl+C to stop"
	cd vscode-extension && npm run watch

# =============================================================================
# Testing
# =============================================================================

# Run all tests
test:
	@echo "Running all tests..."
	sbt test

# Run tests without recompilation
test-fast:
	@echo "Running tests (fast mode)..."
	sbt "testOnly -- -l slow"

# Module-specific tests
test-core:
	sbt "core/test"

test-runtime:
	sbt "runtime/test"

test-parser:
	sbt "langParser/test"

test-compiler:
	sbt "langCompiler/test"

test-lsp:
	sbt "langLsp/test"

test-http:
	sbt "httpApi/test"

test-stdlib:
	sbt "langStdlib/test"

# =============================================================================
# Dashboard E2E Tests
# =============================================================================

# Run all dashboard E2E tests
test-dashboard:
	@echo "Running dashboard E2E tests..."
	cd dashboard-tests && npx playwright test

# Run smoke tests only (quick verification)
test-dashboard-smoke:
	@echo "Running dashboard smoke tests..."
	cd dashboard-tests && npx playwright test smoke.spec.ts

# Run full suite with HTML report
test-dashboard-full:
	@echo "Running full dashboard E2E suite with HTML report..."
	cd dashboard-tests && npx playwright test --reporter=html

# Install dashboard test dependencies
install-dashboard-tests:
	@echo "Installing dashboard test dependencies..."
	cd dashboard-tests && npm ci && npx playwright install --with-deps chromium

# =============================================================================
# Performance Benchmarks
# =============================================================================

# Run all benchmarks
benchmark:
	@echo "Running all performance benchmarks..."
	@echo "Results will be written to target/benchmark-*.json"
	sbt "langCompiler/testOnly *Benchmark" "langLsp/testOnly *LspOperationsBenchmark"

# Run compiler pipeline benchmarks only
benchmark-compiler:
	@echo "Running compiler pipeline benchmarks..."
	sbt "langCompiler/testOnly *CompilerPipelineBenchmark"

# Run visualization benchmarks only
benchmark-viz:
	@echo "Running visualization benchmarks..."
	sbt "langCompiler/testOnly *VisualizationBenchmark"

# Run cache effectiveness benchmarks
benchmark-cache:
	@echo "Running cache benchmarks..."
	sbt "langCompiler/testOnly *CacheBenchmark"

# Run LSP operations benchmarks
benchmark-lsp:
	@echo "Running LSP operations benchmarks..."
	sbt "langLsp/testOnly *LspOperationsBenchmark"

# =============================================================================
# Code Coverage
# =============================================================================

# Run tests with coverage and generate reports
coverage:
	@echo "Running tests with coverage..."
	sbt clean coverage test coverageReport coverageAggregate
	@echo ""
	@echo "Coverage reports generated:"
	@echo "  - HTML: target/scala-3.3.1/scoverage-report/index.html"
	@echo "  - XML:  target/scala-3.3.1/scoverage-report/scoverage.xml"
	@echo "  - Aggregate: target/scala-3.3.1/scoverage-report/index.html"

# Generate coverage report (run after 'sbt coverage test')
coverage-report:
	@echo "Generating coverage reports..."
	sbt coverageReport coverageAggregate

# Open HTML coverage report in browser
coverage-html:
	@echo "Opening coverage report..."
	@if [ -f target/scala-3.3.1/scoverage-report/index.html ]; then \
		xdg-open target/scala-3.3.1/scoverage-report/index.html 2>/dev/null || \
		open target/scala-3.3.1/scoverage-report/index.html 2>/dev/null || \
		start target/scala-3.3.1/scoverage-report/index.html 2>/dev/null || \
		echo "Please open target/scala-3.3.1/scoverage-report/index.html in your browser"; \
	else \
		echo "Coverage report not found. Run 'make coverage' first."; \
	fi

# =============================================================================
# Setup
# =============================================================================

# Install dashboard TypeScript dependencies
install-dashboard:
	@echo "Installing dashboard dependencies..."
	cd dashboard && npm install

# Install all dependencies
install:
	@echo "Installing dependencies..."
	@echo "1. Fetching SBT dependencies..."
	sbt update
	@echo "2. Installing VSCode extension npm packages..."
	cd vscode-extension && npm install
	@echo "3. Installing dashboard npm packages..."
	cd dashboard && npm install
	@echo "Dependencies installed!"

# =============================================================================
# Formatting and Linting
# =============================================================================

# Format all Scala code
fmt:
	@echo "Formatting Scala code..."
	sbt scalafmtAll

# Check formatting without changes
fmt-check:
	@echo "Checking code formatting..."
	sbt scalafmtCheckAll

# Check for lint issues
lint:
	@echo "Checking for lint issues..."
	sbt "scalafixAll --check"

# Auto-fix lint issues
lint-fix:
	@echo "Fixing lint issues..."
	sbt scalafixAll

# =============================================================================
# Utilities
# =============================================================================

# Generate documentation
docs:
	sbt doc

# Package VSCode extension
package-ext:
	cd vscode-extension && npm run package

# Show project info
info:
	@echo "Scala version: 3.3.1"
	@echo "SBT version: 1.9.7"
	@sbt "show version"

# =============================================================================
