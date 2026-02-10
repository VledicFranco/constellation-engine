# Script to fix all broken documentation links

$ErrorActionPreference = "Stop"

Write-Host "Fixing broken documentation links..." -ForegroundColor Cyan

# Fix 1: cli-reference.md - api-reference.md → ../api-reference/http-api-overview.md
$file = "website\docs\llm\cli-reference.md"
(Get-Content $file -Raw) -replace '\[HTTP API Reference\]\(\.\/api-reference\.md\)', '[HTTP API Reference](../api-reference/http-api-overview.md)' | Set-Content $file -NoNewline
Write-Host "Fixed $file"

# Fix 2: execution-modes.md - ../operations/performance-tuning.md → remove (doesn't exist)
$file = "website\docs\llm\foundations\execution-modes.md"
(Get-Content $file -Raw) -replace '- \[Performance Tuning\]\(\.\.\/operations\/performance-tuning\.md\)', '' | Set-Content $file -NoNewline
Write-Host "Fixed $file"

# Fix 3: pipeline-lifecycle.md - ../patterns/performance-tuning.md → remove (doesn't exist)
$file = "website\docs\llm\foundations\pipeline-lifecycle.md"
(Get-Content $file -Raw) -replace '- \[Performance Optimization\]\(\.\.\/patterns\/performance-tuning\.md\)', '' | Set-Content $file -NoNewline
Write-Host "Fixed $file"

# Fix 4: index.md - ../api-reference/index.md → ../api-reference/http-api-overview.md
$file = "website\docs\llm\index.md"
(Get-Content $file -Raw) -replace '\[API Reference\]\(\.\.\/api-reference\/index\.md\)', '[API Reference](../api-reference/http-api-overview.md)' | Set-Content $file -NoNewline
Write-Host "Fixed $file"

# Fix 5: embedded-api.md - ../../../getting-started/embedding-guide.md → ../../getting-started/embedding-guide.md
$file = "website\docs\llm\integration\embedded-api.md"
(Get-Content $file -Raw) -replace '\.\.\/\.\.\/\.\.\/getting-started\/embedding-guide\.md', '../../getting-started/embedding-guide.md' | Set-Content $file -NoNewline
Write-Host "Fixed $file"

# Fix 6: module-registration.md - ../../foundations/module-builder.md → ../patterns/module-development.md
$file = "website\docs\llm\integration\module-registration.md"
(Get-Content $file -Raw) -replace '\.\.\/\.\.\/foundations\/module-builder\.md', '../patterns/module-development.md' | Set-Content $file -NoNewline
Write-Host "Fixed $file"

# Fix 7: error-handling.md - ./testing.md → remove (doesn't exist)
$file = "website\docs\llm\patterns\error-handling.md"
(Get-Content $file -Raw) -replace '- \[Testing Patterns\]\(\.\/testing\.md\)', '' | Set-Content $file -NoNewline
Write-Host "Fixed $file"

# Fix 8: resilience.md - ../../../cookbook/retry-and-fallback.md → ../../cookbook/retry-and-fallback.md
$file = "website\docs\llm\patterns\resilience.md"
(Get-Content $file -Raw) -replace '\.\.\/\.\.\/\.\.\/cookbook\/retry-and-fallback\.md', '../../cookbook/retry-and-fallback.md' | Set-Content $file -NoNewline
Write-Host "Fixed $file"

# Fix 9: project-structure.md - ../getting-started/development-setup.md → remove (doesn't exist)
$file = "website\docs\llm\project-structure.md"
(Get-Content $file -Raw) -replace '- \[Development Setup\]\(\.\.\/getting-started\/development-setup\.md\)', '' | Set-Content $file -NoNewline
Write-Host "Fixed $file"

# Fix 10: error-codes.md - ../patterns/testing.md → remove (doesn't exist)
$file = "website\docs\llm\reference\error-codes.md"
(Get-Content $file -Raw) -replace '- \[Testing Error Scenarios\]\(\.\.\/patterns\/testing\.md\)', '' | Set-Content $file -NoNewline
Write-Host "Fixed $file"

# Fix 11: http-api.md - ./lsp-websocket.md → ../api-reference/lsp-websocket.md
$file = "website\docs\llm\reference\http-api.md"
(Get-Content $file -Raw) -replace '\[LSP WebSocket\]\(\.\/lsp-websocket\.md\)', '[LSP WebSocket](../../api-reference/lsp-websocket.md)' | Set-Content $file -NoNewline
Write-Host "Fixed $file"

# Fix 12: where-constellation-shines.md - ../../dev/benchmarks/performance-benchmarks.md → remove (doesn't exist)
$file = "website\docs\llm\where-constellation-shines.md"
(Get-Content $file -Raw) -replace '- \[Performance Benchmarks\]\(\.\.\/\.\.\/dev\/benchmarks\/performance-benchmarks\.md\)', '' | Set-Content $file -NoNewline
Write-Host "Fixed $file"

Write-Host ''
Write-Host 'All broken links fixed!' -ForegroundColor Green
