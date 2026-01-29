# Restart Constellation server for dev loop iterations
# Usage: .\scripts\restart-server.ps1 [-Port 8080] [-Compile]

param(
    [int]$Port = 8080,
    [switch]$Compile
)

Write-Host "[dev-loop] Stopping server on port $Port..." -ForegroundColor Yellow

# Kill Java processes holding the port
$conns = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
if ($conns) {
    $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($pid in $pids) {
        Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
        Write-Host "[dev-loop] Killed PID $pid" -ForegroundColor Gray
    }
    Start-Sleep -Seconds 2
} else {
    Write-Host "[dev-loop] No process on port $Port" -ForegroundColor Gray
}

# Kill any lingering sbt/java processes
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3

# Compile if requested
if ($Compile) {
    Write-Host "[dev-loop] Compiling..." -ForegroundColor Cyan
    sbt compile
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[dev-loop] Compilation failed!" -ForegroundColor Red
        exit 1
    }
}

# Start server in background
Write-Host "[dev-loop] Starting server on port $Port..." -ForegroundColor Green
$env:CONSTELLATION_PORT = $Port
Start-Job -ScriptBlock {
    $env:CONSTELLATION_PORT = $using:Port
    Set-Location $using:PWD
    sbt "exampleApp/runMain io.constellation.examples.app.server.ExampleServer"
} | Out-Null

# Wait for health check
Write-Host "[dev-loop] Waiting for health check..." -ForegroundColor Yellow
$maxWait = 90
$waited = 0
while ($waited -lt $maxWait) {
    Start-Sleep -Seconds 3
    $waited += 3
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:$Port/health" -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($response.status -eq "ok") {
            Write-Host "[dev-loop] Server ready on port $Port ($waited`s)" -ForegroundColor Green
            exit 0
        }
    } catch {}
    Write-Host "  ... waiting ($waited`s)" -ForegroundColor Gray
}

Write-Host "[dev-loop] Server failed to start within $maxWait seconds" -ForegroundColor Red
exit 1
