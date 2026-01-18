# Constellation Engine - Development Startup Script (PowerShell)
# Usage: .\scripts\dev.ps1

param(
    [switch]$ServerOnly,
    [switch]$WatchOnly,
    [switch]$HotReload
)

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  Constellation Engine - Development Environment" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# Check if sbt is available
if (-not (Get-Command sbt -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: sbt is not installed or not in PATH" -ForegroundColor Red
    exit 1
}

# Check if npm is available
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: npm is not installed or not in PATH" -ForegroundColor Red
    exit 1
}

function Start-Server {
    param([switch]$HotReload)

    Write-Host "Starting Constellation Server..." -ForegroundColor Green
    Write-Host "  HTTP API: http://localhost:8080" -ForegroundColor Yellow
    Write-Host "  LSP WebSocket: ws://localhost:8080/lsp" -ForegroundColor Yellow
    Write-Host ""

    if ($HotReload) {
        Write-Host "Hot-reload enabled - server will restart on code changes" -ForegroundColor Magenta
        Start-Job -ScriptBlock { sbt "~exampleApp/reStart" } | Out-Null
    } else {
        Start-Job -ScriptBlock { sbt "exampleApp/runMain io.constellation.examples.app.server.ExampleServer" } | Out-Null
    }
}

function Start-ExtensionWatch {
    Write-Host "Starting TypeScript watch..." -ForegroundColor Green
    $extPath = Join-Path $PSScriptRoot "..\vscode-extension"
    Start-Job -ScriptBlock {
        Set-Location $using:extPath
        npm run watch
    } | Out-Null
}

# Main logic
if ($ServerOnly) {
    Start-Server -HotReload:$HotReload
} elseif ($WatchOnly) {
    Start-ExtensionWatch
} else {
    # Full dev environment
    Write-Host "Starting full development environment..." -ForegroundColor Cyan
    Write-Host ""

    # Start server in background
    Start-Server -HotReload:$HotReload

    # Wait for server to start
    Write-Host "Waiting for server to start..." -ForegroundColor Yellow
    Start-Sleep -Seconds 5

    # Start extension watch
    Start-ExtensionWatch

    Write-Host ""
    Write-Host "================================================" -ForegroundColor Green
    Write-Host "  Development environment is ready!" -ForegroundColor Green
    Write-Host "================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Yellow
    Write-Host "  1. Open VSCode in this directory"
    Write-Host "  2. Press F5 to launch the extension"
    Write-Host "  3. Open a .cst file and start coding!"
    Write-Host ""
    Write-Host "Keyboard shortcuts in VSCode:"
    Write-Host "  Ctrl+Shift+R  - Run script"
    Write-Host "  Ctrl+Shift+D  - Show DAG visualization"
    Write-Host "  Ctrl+Space    - Autocomplete"
    Write-Host ""
}
