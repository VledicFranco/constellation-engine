# sync-docs.ps1 - Copies documentation from source locations into website/docs/
# with Docusaurus frontmatter injection.
#
# Usage: .\scripts\sync-docs.ps1
# Run from the website\ directory.

param(
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $PSCommandPath
$WebsiteDir = Split-Path -Parent $ScriptDir
$RepoRoot = Split-Path -Parent $WebsiteDir
$Dest = Join-Path $WebsiteDir "docs"

Write-Host "Syncing documentation into $Dest ..." -ForegroundColor Cyan

function Sync-File {
    param(
        [string]$Src,
        [string]$Dst,
        [string]$Title,
        [int]$SidebarPosition,
        [string]$Description = ""
    )

    $dir = Split-Path -Parent $Dst
    if (-not (Test-Path $dir)) {
        if ($DryRun) {
            Write-Host "  [DRY-RUN] mkdir $dir" -ForegroundColor Yellow
        } else {
            New-Item -ItemType Directory -Force -Path $dir | Out-Null
        }
    }

    if (-not (Test-Path $Src)) {
        Write-Warning "Source not found: $Src"
        return
    }

    $content = Get-Content -Path $Src -Raw -ErrorAction SilentlyContinue
    if (-not $content) {
        Write-Warning "Empty file: $Src"
        return
    }

    # Check if source already has frontmatter
    if ($content.StartsWith("---")) {
        $output = $content
    } else {
        $frontmatter = "---`ntitle: `"$Title`"`nsidebar_position: $SidebarPosition"
        if ($Description) {
            $frontmatter += "`ndescription: `"$Description`""
        }
        $frontmatter += "`n---`n`n"
        $output = $frontmatter + $content
    }

    if ($DryRun) {
        Write-Host "  [DRY-RUN] $Src -> $Dst" -ForegroundColor Yellow
    } else {
        Set-Content -Path $Dst -Value $output -NoNewline -Encoding UTF8
        Write-Host "  $($Src | Split-Path -Leaf) -> $Dst" -ForegroundColor Gray
    }
}

# ═══════════════════════════════════════════════════════════════════════════════
# Getting Started
# ═══════════════════════════════════════════════════════════════════════════════

Sync-File "$RepoRoot\docs\getting-started.md" `
    "$Dest\getting-started\tutorial.md" "Tutorial" 2 `
    "Step-by-step guide to building your first Constellation pipeline"

Sync-File "$RepoRoot\docs\embedding-guide.md" `
    "$Dest\getting-started\embedding-guide.md" "Embedding Guide" 3 `
    "Embed Constellation Engine into your JVM application"

Sync-File "$RepoRoot\docs\examples\README.md" `
    "$Dest\getting-started\examples\index.md" "Examples" 1 `
    "Example pipelines demonstrating Constellation features"

Sync-File "$RepoRoot\docs\examples\text-cleaning.md" `
    "$Dest\getting-started\examples\text-cleaning.md" "Text Cleaning" 2
Sync-File "$RepoRoot\docs\examples\content-analysis.md" `
    "$Dest\getting-started\examples\content-analysis.md" "Content Analysis" 3
Sync-File "$RepoRoot\docs\examples\data-statistics.md" `
    "$Dest\getting-started\examples\data-statistics.md" "Data Statistics" 4
Sync-File "$RepoRoot\docs\examples\list-processing.md" `
    "$Dest\getting-started\examples\list-processing.md" "List Processing" 5
Sync-File "$RepoRoot\docs\examples\batch-enrichment.md" `
    "$Dest\getting-started\examples\batch-enrichment.md" "Batch Enrichment" 6
Sync-File "$RepoRoot\docs\examples\scoring-pipeline.md" `
    "$Dest\getting-started\examples\scoring-pipeline.md" "Scoring Pipeline" 7

# ═══════════════════════════════════════════════════════════════════════════════
# Language Reference
# ═══════════════════════════════════════════════════════════════════════════════

Sync-File "$RepoRoot\docs\constellation-lang\README.md" `
    "$Dest\language\index.md" "Language Overview" 1 `
    "constellation-lang reference documentation"

Sync-File "$RepoRoot\docs\constellation-lang\program-structure.md" `
    "$Dest\language\program-structure.md" "Program Structure" 2
Sync-File "$RepoRoot\docs\constellation-lang\types.md" `
    "$Dest\language\types.md" "Types" 3
Sync-File "$RepoRoot\docs\constellation-lang\declarations.md" `
    "$Dest\language\declarations.md" "Declarations" 4
Sync-File "$RepoRoot\docs\constellation-lang\expressions.md" `
    "$Dest\language\expressions.md" "Expressions" 5
Sync-File "$RepoRoot\docs\constellation-lang\type-algebra.md" `
    "$Dest\language\type-algebra.md" "Type Algebra" 6
Sync-File "$RepoRoot\docs\constellation-lang\orchestration-algebra.md" `
    "$Dest\language\orchestration-algebra.md" "Orchestration Algebra" 7
Sync-File "$RepoRoot\docs\constellation-lang\comments.md" `
    "$Dest\language\comments.md" "Comments" 8
Sync-File "$RepoRoot\docs\constellation-lang\module-options.md" `
    "$Dest\language\module-options.md" "Module Options" 9
Sync-File "$RepoRoot\docs\constellation-lang\error-messages.md" `
    "$Dest\language\error-messages.md" "Error Messages" 10
Sync-File "$RepoRoot\docs\constellation-lang\examples.md" `
    "$Dest\language\examples.md" "Examples" 11

Sync-File "$RepoRoot\docs\constellation-lang\examples\resilient-pipelines.md" `
    "$Dest\language\resilient-pipelines.md" "Resilient Pipelines" 12

# Module options
Sync-File "$RepoRoot\docs\constellation-lang\options\retry.md"       "$Dest\language\options\retry.md"       "retry"       1
Sync-File "$RepoRoot\docs\constellation-lang\options\timeout.md"     "$Dest\language\options\timeout.md"     "timeout"     2
Sync-File "$RepoRoot\docs\constellation-lang\options\fallback.md"    "$Dest\language\options\fallback.md"    "fallback"    3
Sync-File "$RepoRoot\docs\constellation-lang\options\cache.md"       "$Dest\language\options\cache.md"       "cache"       4
Sync-File "$RepoRoot\docs\constellation-lang\options\cache-backend.md" "$Dest\language\options\cache-backend.md" "cache-backend" 5
Sync-File "$RepoRoot\docs\constellation-lang\options\delay.md"       "$Dest\language\options\delay.md"       "delay"       6
Sync-File "$RepoRoot\docs\constellation-lang\options\backoff.md"     "$Dest\language\options\backoff.md"     "backoff"     7
Sync-File "$RepoRoot\docs\constellation-lang\options\throttle.md"    "$Dest\language\options\throttle.md"    "throttle"    8
Sync-File "$RepoRoot\docs\constellation-lang\options\concurrency.md" "$Dest\language\options\concurrency.md" "concurrency" 9
Sync-File "$RepoRoot\docs\constellation-lang\options\on-error.md"    "$Dest\language\options\on-error.md"    "on-error"    10
Sync-File "$RepoRoot\docs\constellation-lang\options\lazy.md"        "$Dest\language\options\lazy.md"        "lazy"        11
Sync-File "$RepoRoot\docs\constellation-lang\options\priority.md"    "$Dest\language\options\priority.md"    "priority"    12

# ═══════════════════════════════════════════════════════════════════════════════
# API Reference
# ═══════════════════════════════════════════════════════════════════════════════

Sync-File "$RepoRoot\docs\api-guide.md" `
    "$Dest\api-reference\programmatic-api.md" "Programmatic API" 2 `
    "Scala API for creating modules and building pipelines"

Sync-File "$RepoRoot\docs\stdlib.md" `
    "$Dest\api-reference\stdlib.md" "Standard Library" 3 `
    "Built-in functions available in every pipeline"

Sync-File "$RepoRoot\docs\error-reference.md" `
    "$Dest\api-reference\error-reference.md" "Error Reference" 4 `
    "Structured error codes and resolution guides"

Sync-File "$RepoRoot\docs\api\lsp-websocket.md" `
    "$Dest\api-reference\lsp-websocket.md" "LSP WebSocket" 5 `
    "WebSocket protocol for IDE integration"

# ═══════════════════════════════════════════════════════════════════════════════
# Architecture
# ═══════════════════════════════════════════════════════════════════════════════

Sync-File "$RepoRoot\docs\architecture.md" `
    "$Dest\architecture\technical-architecture.md" "Technical Architecture" 1 `
    "System design, module graph, compilation pipeline"

Sync-File "$RepoRoot\docs\security.md" `
    "$Dest\architecture\security-model.md" "Security Model" 2 `
    "Trust boundaries, sandboxing, HTTP hardening"

# ═══════════════════════════════════════════════════════════════════════════════
# Operations
# ═══════════════════════════════════════════════════════════════════════════════

Sync-File "$RepoRoot\docs\ops\configuration.md"     "$Dest\operations\configuration.md"     "Configuration"      1
Sync-File "$RepoRoot\docs\ops\deployment.md"         "$Dest\operations\deployment.md"         "Deployment"         2
Sync-File "$RepoRoot\docs\ops\runbook.md"            "$Dest\operations\runbook.md"            "Runbook"            3
Sync-File "$RepoRoot\docs\performance-tuning.md" `
    "$Dest\operations\performance-tuning.md" "Performance Tuning" 4 `
    "Scheduler, circuit breakers, caching, and memory tuning"

# ═══════════════════════════════════════════════════════════════════════════════
# Integrations (SPI)
# ═══════════════════════════════════════════════════════════════════════════════

Sync-File "$RepoRoot\docs\integrations\spi\metrics-provider.md"   "$Dest\integrations\metrics-provider.md"   "Metrics Provider"   1
Sync-File "$RepoRoot\docs\integrations\spi\tracer-provider.md"    "$Dest\integrations\tracer-provider.md"    "Tracer Provider"    2
Sync-File "$RepoRoot\docs\integrations\spi\execution-listener.md" "$Dest\integrations\execution-listener.md" "Execution Listener" 3
Sync-File "$RepoRoot\docs\integrations\spi\cache-backend.md"      "$Dest\integrations\cache-backend.md"      "Cache Backend"      4
Sync-File "$RepoRoot\docs\integrations\spi\execution-storage.md"  "$Dest\integrations\execution-storage.md"  "Execution Storage"  5

# ═══════════════════════════════════════════════════════════════════════════════
# Tooling
# ═══════════════════════════════════════════════════════════════════════════════

Sync-File "$RepoRoot\docs\tooling.md" `
    "$Dest\tooling\dashboard.md" "Dashboard" 1 `
    "Web dashboard, DAG visualization, and execution history"

Sync-File "$RepoRoot\docs\LSP_INTEGRATION.md" `
    "$Dest\tooling\lsp-integration.md" "LSP Integration" 2 `
    "Language Server Protocol setup for IDEs"

Sync-File "$RepoRoot\docs\troubleshooting.md" `
    "$Dest\tooling\troubleshooting.md" "Troubleshooting" 3 `
    "Common issues and solutions"

# ═══════════════════════════════════════════════════════════════════════════════
# Resources
# ═══════════════════════════════════════════════════════════════════════════════

Sync-File "$RepoRoot\docs\ML_ORCHESTRATION_CHALLENGES.md" `
    "$Dest\resources\ml-orchestration-challenges.md" "ML Orchestration Challenges" 1 `
    "Why ML pipeline orchestration is hard and how Constellation addresses it"

Sync-File "$RepoRoot\docs\migration\v0.3.0.md" `
    "$Dest\resources\migration-v030.md" "Migration: v0.3.0" 2 `
    "Upgrading from v0.2.x to v0.3.0"

Sync-File "$RepoRoot\CONTRIBUTING.md" `
    "$Dest\resources\contributing.md" "Contributing" 3 `
    "How to contribute to Constellation Engine"

Sync-File "$RepoRoot\CHANGELOG.md" `
    "$Dest\resources\changelog.md" "Changelog" 4 `
    "Version history and release notes"

# ═══════════════════════════════════════════════════════════════════════════════

$count = (Get-ChildItem -Path $Dest -Filter "*.md" -Recurse).Count
Write-Host ""
Write-Host "Sync complete! $count markdown files written to $Dest" -ForegroundColor Green
