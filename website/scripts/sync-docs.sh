#!/usr/bin/env bash
# sync-docs.sh - Copies documentation from source locations into website/docs/
# with Docusaurus frontmatter injection.
#
# Usage: ./scripts/sync-docs.sh
# Run from the website/ directory or pass REPO_ROOT.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEBSITE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$WEBSITE_DIR/.." && pwd)"
DEST="$WEBSITE_DIR/docs"

# Clean existing synced docs (preserve new content files)
echo "Syncing documentation into $DEST ..."

# Helper: copy a file, injecting frontmatter if it doesn't already have it
sync_file() {
  local src="$1"
  local dst="$2"
  local title="$3"
  local sidebar_position="$4"
  local description="${5:-}"

  mkdir -p "$(dirname "$dst")"

  # Check if source already has frontmatter
  if head -1 "$src" | grep -q '^---'; then
    cp "$src" "$dst"
  else
    {
      echo "---"
      echo "title: \"$title\""
      echo "sidebar_position: $sidebar_position"
      if [ -n "$description" ]; then
        echo "description: \"$description\""
      fi
      echo "---"
      echo ""
      cat "$src"
    } > "$dst"
  fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# Getting Started
# ═══════════════════════════════════════════════════════════════════════════════
mkdir -p "$DEST/getting-started/examples"

sync_file "$REPO_ROOT/docs/getting-started.md" \
  "$DEST/getting-started/tutorial.md" "Tutorial" 2 \
  "Step-by-step guide to building your first Constellation pipeline"

sync_file "$REPO_ROOT/docs/embedding-guide.md" \
  "$DEST/getting-started/embedding-guide.md" "Embedding Guide" 3 \
  "Embed Constellation Engine into your JVM application"

sync_file "$REPO_ROOT/docs/examples/README.md" \
  "$DEST/getting-started/examples/index.md" "Examples" 1 \
  "Example pipelines demonstrating Constellation features"

sync_file "$REPO_ROOT/docs/examples/text-cleaning.md" \
  "$DEST/getting-started/examples/text-cleaning.md" "Text Cleaning" 2
sync_file "$REPO_ROOT/docs/examples/content-analysis.md" \
  "$DEST/getting-started/examples/content-analysis.md" "Content Analysis" 3
sync_file "$REPO_ROOT/docs/examples/data-statistics.md" \
  "$DEST/getting-started/examples/data-statistics.md" "Data Statistics" 4
sync_file "$REPO_ROOT/docs/examples/list-processing.md" \
  "$DEST/getting-started/examples/list-processing.md" "List Processing" 5
sync_file "$REPO_ROOT/docs/examples/batch-enrichment.md" \
  "$DEST/getting-started/examples/batch-enrichment.md" "Batch Enrichment" 6
sync_file "$REPO_ROOT/docs/examples/scoring-pipeline.md" \
  "$DEST/getting-started/examples/scoring-pipeline.md" "Scoring Pipeline" 7

# ═══════════════════════════════════════════════════════════════════════════════
# Language Reference
# ═══════════════════════════════════════════════════════════════════════════════
mkdir -p "$DEST/language/options"

sync_file "$REPO_ROOT/docs/constellation-lang/README.md" \
  "$DEST/language/index.md" "Language Overview" 1 \
  "constellation-lang reference documentation"

sync_file "$REPO_ROOT/docs/constellation-lang/program-structure.md" \
  "$DEST/language/program-structure.md" "Program Structure" 2
sync_file "$REPO_ROOT/docs/constellation-lang/types.md" \
  "$DEST/language/types.md" "Types" 3
sync_file "$REPO_ROOT/docs/constellation-lang/declarations.md" \
  "$DEST/language/declarations.md" "Declarations" 4
sync_file "$REPO_ROOT/docs/constellation-lang/expressions.md" \
  "$DEST/language/expressions.md" "Expressions" 5
sync_file "$REPO_ROOT/docs/constellation-lang/type-algebra.md" \
  "$DEST/language/type-algebra.md" "Type Algebra" 6
sync_file "$REPO_ROOT/docs/constellation-lang/orchestration-algebra.md" \
  "$DEST/language/orchestration-algebra.md" "Orchestration Algebra" 7
sync_file "$REPO_ROOT/docs/constellation-lang/comments.md" \
  "$DEST/language/comments.md" "Comments" 8
sync_file "$REPO_ROOT/docs/constellation-lang/module-options.md" \
  "$DEST/language/module-options.md" "Module Options" 9
sync_file "$REPO_ROOT/docs/constellation-lang/error-messages.md" \
  "$DEST/language/error-messages.md" "Error Messages" 10
sync_file "$REPO_ROOT/docs/constellation-lang/examples.md" \
  "$DEST/language/examples.md" "Examples" 11

sync_file "$REPO_ROOT/docs/constellation-lang/examples/resilient-pipelines.md" \
  "$DEST/language/resilient-pipelines.md" "Resilient Pipelines" 12

# Module options
sync_file "$REPO_ROOT/docs/constellation-lang/options/retry.md" \
  "$DEST/language/options/retry.md" "retry" 1
sync_file "$REPO_ROOT/docs/constellation-lang/options/timeout.md" \
  "$DEST/language/options/timeout.md" "timeout" 2
sync_file "$REPO_ROOT/docs/constellation-lang/options/fallback.md" \
  "$DEST/language/options/fallback.md" "fallback" 3
sync_file "$REPO_ROOT/docs/constellation-lang/options/cache.md" \
  "$DEST/language/options/cache.md" "cache" 4
sync_file "$REPO_ROOT/docs/constellation-lang/options/cache-backend.md" \
  "$DEST/language/options/cache-backend.md" "cache-backend" 5
sync_file "$REPO_ROOT/docs/constellation-lang/options/delay.md" \
  "$DEST/language/options/delay.md" "delay" 6
sync_file "$REPO_ROOT/docs/constellation-lang/options/backoff.md" \
  "$DEST/language/options/backoff.md" "backoff" 7
sync_file "$REPO_ROOT/docs/constellation-lang/options/throttle.md" \
  "$DEST/language/options/throttle.md" "throttle" 8
sync_file "$REPO_ROOT/docs/constellation-lang/options/concurrency.md" \
  "$DEST/language/options/concurrency.md" "concurrency" 9
sync_file "$REPO_ROOT/docs/constellation-lang/options/on-error.md" \
  "$DEST/language/options/on-error.md" "on-error" 10
sync_file "$REPO_ROOT/docs/constellation-lang/options/lazy.md" \
  "$DEST/language/options/lazy.md" "lazy" 11
sync_file "$REPO_ROOT/docs/constellation-lang/options/priority.md" \
  "$DEST/language/options/priority.md" "priority" 12

# ═══════════════════════════════════════════════════════════════════════════════
# API Reference
# ═══════════════════════════════════════════════════════════════════════════════
mkdir -p "$DEST/api-reference"

sync_file "$REPO_ROOT/docs/api-guide.md" \
  "$DEST/api-reference/programmatic-api.md" "Programmatic API" 2 \
  "Scala API for creating modules and building pipelines"

sync_file "$REPO_ROOT/docs/stdlib.md" \
  "$DEST/api-reference/stdlib.md" "Standard Library" 3 \
  "Built-in functions available in every pipeline"

sync_file "$REPO_ROOT/docs/error-reference.md" \
  "$DEST/api-reference/error-reference.md" "Error Reference" 4 \
  "Structured error codes and resolution guides"

sync_file "$REPO_ROOT/docs/api/lsp-websocket.md" \
  "$DEST/api-reference/lsp-websocket.md" "LSP WebSocket" 5 \
  "WebSocket protocol for IDE integration"

# ═══════════════════════════════════════════════════════════════════════════════
# Architecture
# ═══════════════════════════════════════════════════════════════════════════════
mkdir -p "$DEST/architecture"

sync_file "$REPO_ROOT/docs/architecture.md" \
  "$DEST/architecture/technical-architecture.md" "Technical Architecture" 1 \
  "System design, module graph, compilation pipeline"

sync_file "$REPO_ROOT/docs/security.md" \
  "$DEST/architecture/security-model.md" "Security Model" 2 \
  "Trust boundaries, sandboxing, HTTP hardening"

# ═══════════════════════════════════════════════════════════════════════════════
# Operations
# ═══════════════════════════════════════════════════════════════════════════════
mkdir -p "$DEST/operations"

sync_file "$REPO_ROOT/docs/ops/configuration.md" \
  "$DEST/operations/configuration.md" "Configuration" 1

sync_file "$REPO_ROOT/docs/ops/deployment.md" \
  "$DEST/operations/deployment.md" "Deployment" 2

sync_file "$REPO_ROOT/docs/ops/runbook.md" \
  "$DEST/operations/runbook.md" "Runbook" 3

sync_file "$REPO_ROOT/docs/performance-tuning.md" \
  "$DEST/operations/performance-tuning.md" "Performance Tuning" 4 \
  "Scheduler, circuit breakers, caching, and memory tuning"

# ═══════════════════════════════════════════════════════════════════════════════
# Integrations (SPI)
# ═══════════════════════════════════════════════════════════════════════════════
mkdir -p "$DEST/integrations"

sync_file "$REPO_ROOT/docs/integrations/spi/metrics-provider.md" \
  "$DEST/integrations/metrics-provider.md" "Metrics Provider" 1

sync_file "$REPO_ROOT/docs/integrations/spi/tracer-provider.md" \
  "$DEST/integrations/tracer-provider.md" "Tracer Provider" 2

sync_file "$REPO_ROOT/docs/integrations/spi/execution-listener.md" \
  "$DEST/integrations/execution-listener.md" "Execution Listener" 3

sync_file "$REPO_ROOT/docs/integrations/spi/cache-backend.md" \
  "$DEST/integrations/cache-backend.md" "Cache Backend" 4

sync_file "$REPO_ROOT/docs/integrations/spi/execution-storage.md" \
  "$DEST/integrations/execution-storage.md" "Execution Storage" 5

# ═══════════════════════════════════════════════════════════════════════════════
# Tooling
# ═══════════════════════════════════════════════════════════════════════════════
mkdir -p "$DEST/tooling"

sync_file "$REPO_ROOT/docs/tooling.md" \
  "$DEST/tooling/dashboard.md" "Dashboard" 1 \
  "Web dashboard, DAG visualization, and execution history"

sync_file "$REPO_ROOT/docs/LSP_INTEGRATION.md" \
  "$DEST/tooling/lsp-integration.md" "LSP Integration" 2 \
  "Language Server Protocol setup for IDEs"

sync_file "$REPO_ROOT/docs/troubleshooting.md" \
  "$DEST/tooling/troubleshooting.md" "Troubleshooting" 3 \
  "Common issues and solutions"

# ═══════════════════════════════════════════════════════════════════════════════
# Resources
# ═══════════════════════════════════════════════════════════════════════════════
mkdir -p "$DEST/resources"

sync_file "$REPO_ROOT/docs/ML_ORCHESTRATION_CHALLENGES.md" \
  "$DEST/resources/ml-orchestration-challenges.md" "ML Orchestration Challenges" 1 \
  "Why ML pipeline orchestration is hard and how Constellation addresses it"

sync_file "$REPO_ROOT/docs/migration/v0.3.0.md" \
  "$DEST/resources/migration-v030.md" "Migration: v0.3.0" 2 \
  "Upgrading from v0.2.x to v0.3.0"

sync_file "$REPO_ROOT/CONTRIBUTING.md" \
  "$DEST/resources/contributing.md" "Contributing" 3 \
  "How to contribute to Constellation Engine"

sync_file "$REPO_ROOT/CHANGELOG.md" \
  "$DEST/resources/changelog.md" "Changelog" 4 \
  "Version history and release notes"

# ═══════════════════════════════════════════════════════════════════════════════
# Fix cross-references: rewrite links from source doc structure to website structure
# ═══════════════════════════════════════════════════════════════════════════════
echo "Fixing cross-references..."

fix_links() {
  local file="$1"

  # Use sed to fix common link patterns
  # The source docs use relative links like "constellation-lang/README.md" etc.
  # We need to rewrite to the website's structure.

  sed -i \
    -e 's|(constellation-lang/README\.md)|(../language/index.md)|g' \
    -e 's|(constellation-lang/declarations\.md)|(../language/declarations.md)|g' \
    -e 's|(constellation-lang/declarations\.md#input-annotations)|(../language/declarations.md#input-annotations)|g' \
    -e 's|(constellation-lang/error-messages\.md)|(../language/error-messages.md)|g' \
    -e 's|(constellation-lang/types\.md)|(../language/types.md)|g' \
    -e 's|(constellation-lang/expressions\.md)|(../language/expressions.md)|g' \
    -e 's|(constellation-lang/module-options\.md)|(../language/module-options.md)|g' \
    -e 's|(stdlib\.md)|(../api-reference/stdlib.md)|g' \
    -e 's|(security\.md)|(../architecture/security-model.md)|g' \
    -e 's|(architecture\.md)|(../architecture/technical-architecture.md)|g' \
    -e 's|(api-guide\.md)|(../api-reference/programmatic-api.md)|g' \
    -e 's|(error-reference\.md)|(../api-reference/error-reference.md)|g' \
    -e 's|(performance-tuning\.md)|(../operations/performance-tuning.md)|g' \
    -e 's|(LSP_INTEGRATION\.md)|(../tooling/lsp-integration.md)|g' \
    -e 's|(getting-started\.md)|(../getting-started/tutorial.md)|g' \
    -e 's|(examples/README\.md)|(../getting-started/examples/index.md)|g' \
    -e 's|(migration/v0\.3\.0\.md)|(../resources/migration-v030.md)|g' \
    -e 's|(integrations/spi/)|(../integrations/metrics-provider.md)|g' \
    -e 's|(integrations/spi/cache-backend\.md)|(../integrations/cache-backend.md)|g' \
    -e 's|(integrations/spi/metrics-provider\.md)|(../integrations/metrics-provider.md)|g' \
    -e 's|(dev/playwright-dev-loop\.md)|(#)|g' \
    -e 's|(dev/rfcs/rfc-012-dashboard-e2e-tests\.md)|(#)|g' \
    -e 's|(../../dev/global-scheduler\.md)|(#)|g' \
    "$file"
}

# Fix links for specific categories that have cross-references
fix_links_relative() {
  local file="$1"

  # For language/ files: fix relative paths within same directory
  sed -i \
    -e 's|(./examples/resilient-pipelines\.md)|(./resilient-pipelines.md)|g' \
    -e 's|(./options/)|(./options/retry.md)|g' \
    -e 's|(../module-options\.md)|(./module-options.md)|g' \
    -e 's|(../options/)|(./options/retry.md)|g' \
    "$file"
}

# Fix links for resource files (migration) that use ../ paths
fix_migration_links() {
  local file="$1"

  sed -i \
    -e 's|(../embedding-guide\.md)|(../getting-started/embedding-guide.md)|g' \
    -e 's|(../security\.md)|(../architecture/security-model.md)|g' \
    -e 's|(../performance-tuning\.md)|(../operations/performance-tuning.md)|g' \
    -e 's|(../error-reference\.md)|(../api-reference/error-reference.md)|g' \
    -e 's|(../integrations/spi/)|(../integrations/metrics-provider.md)|g' \
    -e 's|(../stdlib\.md)|(../api-reference/stdlib.md)|g' \
    -e 's|(../constellation-lang/README\.md)|(../language/index.md)|g' \
    -e 's|(../getting-started\.md)|(./tutorial.md)|g' \
    "$file"
}

# Apply link fixes across all synced docs
find "$DEST" -name '*.md' -exec bash -c 'for f; do
  sed -i \
    -e "s|(constellation-lang/README\.md)|(../language/index.md)|g" \
    -e "s|(constellation-lang/declarations\.md#input-annotations)|(../language/declarations.md#input-annotations)|g" \
    -e "s|(constellation-lang/declarations\.md)|(../language/declarations.md)|g" \
    -e "s|(constellation-lang/error-messages\.md)|(../language/error-messages.md)|g" \
    -e "s|(security\.md)|(../architecture/security-model.md)|g" \
    -e "s|(LSP_INTEGRATION\.md)|(../tooling/lsp-integration.md)|g" \
    "$f"
done' _ {} +

# Fix specific files with known broken paths
for f in "$DEST"/getting-started/*.md; do
  sed -i \
    -e 's|(stdlib\.md)|(../api-reference/stdlib.md)|g' \
    -e 's|(architecture\.md)|(../architecture/technical-architecture.md)|g' \
    -e 's|(api-guide\.md)|(../api-reference/programmatic-api.md)|g' \
    -e 's|(error-reference\.md)|(../api-reference/error-reference.md)|g' \
    -e 's|(performance-tuning\.md)|(../operations/performance-tuning.md)|g' \
    -e 's|(getting-started\.md)|(./tutorial.md)|g' \
    -e 's|(examples/README\.md)|(./examples/index.md)|g' \
    -e 's|(migration/v0\.3\.0\.md)|(../resources/migration-v030.md)|g' \
    -e 's|(integrations/spi/)|(../integrations/metrics-provider.md)|g' \
    "$f"
done

for f in "$DEST"/getting-started/examples/*.md; do
  sed -i \
    -e 's|(../stdlib\.md)|(../../api-reference/stdlib.md)|g' \
    -e 's|(../constellation-lang/README\.md)|(../../language/index.md)|g' \
    -e 's|(../getting-started\.md)|(../tutorial.md)|g' \
    "$f"
done

for f in "$DEST"/language/*.md; do
  sed -i \
    -e 's|(./examples/resilient-pipelines\.md)|(./resilient-pipelines.md)|g' \
    -e 's|(./options/)|(./options/retry.md)|g' \
    -e 's|(../module-options\.md)|(./module-options.md)|g' \
    -e 's|(../options/)|(./options/retry.md)|g' \
    "$f"
done

for f in "$DEST"/language/options/*.md; do
  sed -i \
    -e 's|(../../dev/global-scheduler\.md)|(#)|g' \
    "$f"
done

for f in "$DEST"/tooling/*.md; do
  sed -i \
    -e 's|(getting-started\.md)|(../getting-started/tutorial.md)|g' \
    -e 's|(api-guide\.md)|(../api-reference/programmatic-api.md)|g' \
    -e 's|(architecture\.md)|(../architecture/technical-architecture.md)|g' \
    -e 's|(dev/playwright-dev-loop\.md)|(#)|g' \
    -e 's|(dev/rfcs/rfc-012-dashboard-e2e-tests\.md)|(#)|g' \
    "$f"
done

for f in "$DEST"/operations/*.md; do
  sed -i \
    -e 's|(integrations/spi/cache-backend\.md)|(../integrations/cache-backend.md)|g' \
    -e 's|(integrations/spi/metrics-provider\.md)|(../integrations/metrics-provider.md)|g' \
    "$f"
done

for f in "$DEST"/resources/migration-v030.md; do
  sed -i \
    -e 's|(../embedding-guide\.md)|(../getting-started/embedding-guide.md)|g' \
    -e 's|(../security\.md)|(../architecture/security-model.md)|g' \
    -e 's|(../performance-tuning\.md)|(../operations/performance-tuning.md)|g' \
    -e 's|(../error-reference\.md)|(../api-reference/error-reference.md)|g' \
    -e 's|(../integrations/spi/)|(../integrations/metrics-provider.md)|g' \
    "$f"
done

for f in "$DEST"/architecture/*.md; do
  sed -i \
    -e 's|(integrations/spi/)|(../integrations/metrics-provider.md)|g' \
    "$f"
done

echo ""
echo "Sync complete! Files written to $DEST"
find "$DEST" -name '*.md' | wc -l | xargs -I{} echo "  Total: {} markdown files"
