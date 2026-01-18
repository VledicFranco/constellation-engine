# Constellation Engine - Development Makefile
# Usage: make <target>

.PHONY: help dev server watch test compile clean extension ext-watch install all mcp-install mcp-build mcp-test mcp-start mcp-clean

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
	@echo "  make all        - Compile everything (Scala + TypeScript)"
	@echo "  make clean      - Clean all build artifacts"
	@echo ""
	@echo "Watch Modes:"
	@echo "  make watch      - Watch Scala sources and recompile on changes"
	@echo "  make ext-watch  - Watch TypeScript and recompile on changes"
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
	@echo "Setup:"
	@echo "  make install    - Install all dependencies"
	@echo ""
	@echo "MCP Server:"
	@echo "  make mcp-install - Install MCP server dependencies"
	@echo "  make mcp-build   - Build MCP server"
	@echo "  make mcp-test    - Run MCP server tests"
	@echo "  make mcp-start   - Start MCP server"
	@echo "  make mcp-clean   - Clean MCP server build"

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

# Compile everything
all: compile extension
	@echo "Build complete!"

# Clean all build artifacts
clean:
	@echo "Cleaning build artifacts..."
	sbt clean
	rm -rf vscode-extension/out
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
# Setup
# =============================================================================

# Install all dependencies
install:
	@echo "Installing dependencies..."
	@echo "1. Fetching SBT dependencies..."
	sbt update
	@echo "2. Installing npm packages..."
	cd vscode-extension && npm install
	@echo "Dependencies installed!"

# =============================================================================
# Utilities
# =============================================================================

# Format code (if scalafmt is configured)
fmt:
	sbt scalafmtAll

# Check formatting
fmt-check:
	sbt scalafmtCheckAll

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
# MCP Server
# =============================================================================

# Install MCP server dependencies
mcp-install:
	@echo "Installing MCP server dependencies..."
	cd mcp-server && npm install

# Build MCP server
mcp-build: mcp-install
	@echo "Building MCP server..."
	cd mcp-server && npm run build

# Run MCP server tests
mcp-test: mcp-build
	@echo "Running MCP server tests..."
	cd mcp-server && npm test

# Start MCP server (for testing)
mcp-start: mcp-build
	@echo "Starting MCP server..."
	cd mcp-server && npm start

# Clean MCP server build
mcp-clean:
	@echo "Cleaning MCP server..."
	cd mcp-server && npm run clean 2>/dev/null || true
	rm -rf mcp-server/node_modules mcp-server/dist
