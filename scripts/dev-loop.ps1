# Playwright Dev Loop - Single iteration automation
# Usage: .\scripts\dev-loop.ps1 [-Port 8080] [-Compile] [-TestFilter "1-simple"]
#
# Runs: kill server -> compile (optional) -> restart server -> screenshot audit
# Screenshots land in: dashboard-tests/screenshots/

param(
    [int]$Port = 8080,
    [switch]$Compile,
    [string]$TestFilter = ""  # e.g. "1-simple-script-dag" to run one test
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Playwright Dev Loop - Iteration" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# Step 1: Kill existing server
Write-Host "`n[1/4] Stopping server..." -ForegroundColor Yellow
$conns = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
if ($conns) {
    $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($pid in $pids) {
        Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 2
}
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3

# Step 2: Compile (if requested or if there are Scala changes)
if ($Compile) {
    Write-Host "[2/4] Compiling..." -ForegroundColor Yellow
    Push-Location $rootDir
    sbt compile
    if ($LASTEXITCODE -ne 0) {
        Write-Host "COMPILE FAILED" -ForegroundColor Red
        Pop-Location
        exit 1
    }
    Pop-Location
} else {
    Write-Host "[2/4] Skipping compile (frontend-only changes)" -ForegroundColor Gray
}

# Step 3: Start server and wait for health
Write-Host "[3/4] Starting server on port $Port..." -ForegroundColor Yellow
$env:CONSTELLATION_PORT = $Port
Start-Job -ScriptBlock {
    $env:CONSTELLATION_PORT = $using:Port
    Set-Location $using:rootDir
    sbt "exampleApp/runMain io.constellation.examples.app.server.ExampleServer"
} | Out-Null

$maxWait = 90
$waited = 0
while ($waited -lt $maxWait) {
    Start-Sleep -Seconds 3
    $waited += 3
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:$Port/health" -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($response.status -eq "ok") {
            Write-Host "  Server ready ($waited`s)" -ForegroundColor Green
            break
        }
    } catch {}
}

if ($waited -ge $maxWait) {
    Write-Host "Server failed to start" -ForegroundColor Red
    exit 1
}

# Step 4: Run screenshot audit
Write-Host "[4/4] Running screenshot audit..." -ForegroundColor Yellow
Push-Location (Join-Path $rootDir "dashboard-tests")

$env:CONSTELLATION_PORT = $Port
if ($TestFilter) {
    npx playwright test screenshot-audit -g $TestFilter --reporter=list
} else {
    npx playwright test screenshot-audit --reporter=list
}
$testResult = $LASTEXITCODE

Pop-Location

if ($testResult -eq 0) {
    Write-Host "`n============================================" -ForegroundColor Green
    Write-Host "  Screenshots ready in dashboard-tests/screenshots/" -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green
} else {
    Write-Host "`n============================================" -ForegroundColor Red
    Write-Host "  Screenshot audit had failures" -ForegroundColor Red
    Write-Host "============================================" -ForegroundColor Red
}

exit $testResult
