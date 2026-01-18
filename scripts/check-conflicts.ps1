# Constellation Engine - Pre-Work Conflict Check Script
# Usage: .\scripts\check-conflicts.ps1 -Files "path/to/file1.scala","path/to/file2.scala"
#        .\scripts\check-conflicts.ps1 -Pattern "modules/lang-compiler/**/*.scala"
#
# Checks if any open PRs touch the files you're about to modify.
# Run this before starting work to avoid conflicts with other agents.

param(
    [string[]]$Files,
    [string]$Pattern,
    [switch]$Verbose
)

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  Constellation Engine - Conflict Check" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# Check if gh CLI is available
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: GitHub CLI (gh) is not installed or not in PATH" -ForegroundColor Red
    Write-Host "Install it from: https://cli.github.com/" -ForegroundColor Yellow
    exit 1
}

# Check if authenticated
$authStatus = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Not authenticated with GitHub CLI" -ForegroundColor Red
    Write-Host "Run: gh auth login" -ForegroundColor Yellow
    exit 1
}

# If pattern provided, expand it to file list
if ($Pattern) {
    $Files = Get-ChildItem -Path $Pattern -Recurse -File | ForEach-Object {
        $_.FullName.Replace((Get-Location).Path + "\", "").Replace("\", "/")
    }
    if ($Verbose) {
        Write-Host "Expanded pattern to $($Files.Count) files" -ForegroundColor Gray
    }
}

if (-not $Files -or $Files.Count -eq 0) {
    Write-Host "Usage: .\scripts\check-conflicts.ps1 -Files 'file1.scala','file2.scala'" -ForegroundColor Yellow
    Write-Host "       .\scripts\check-conflicts.ps1 -Pattern 'modules/**/*.scala'" -ForegroundColor Yellow
    exit 1
}

Write-Host "Checking $($Files.Count) file(s) for conflicts with open PRs..." -ForegroundColor Yellow
Write-Host ""

# Get list of open PRs
$openPRs = gh pr list --state open --json number,headRefName,title,author 2>&1 | ConvertFrom-Json

if (-not $openPRs -or $openPRs.Count -eq 0) {
    Write-Host "No open PRs found. Safe to proceed!" -ForegroundColor Green
    exit 0
}

Write-Host "Found $($openPRs.Count) open PR(s). Checking for file overlaps..." -ForegroundColor Yellow
Write-Host ""

$conflictsFound = $false
$conflictDetails = @()

foreach ($pr in $openPRs) {
    # Get files changed in this PR
    $prFiles = gh pr diff $pr.number --name-only 2>&1
    if ($LASTEXITCODE -ne 0) {
        if ($Verbose) {
            Write-Host "  Warning: Could not get diff for PR #$($pr.number)" -ForegroundColor Gray
        }
        continue
    }

    $prFileList = $prFiles -split "`n" | Where-Object { $_ -ne "" }

    # Check for overlaps
    foreach ($file in $Files) {
        # Normalize path separators
        $normalizedFile = $file.Replace("\", "/")

        foreach ($prFile in $prFileList) {
            $normalizedPrFile = $prFile.Replace("\", "/")

            if ($normalizedFile -eq $normalizedPrFile) {
                $conflictsFound = $true
                $conflictDetails += @{
                    PR = $pr.number
                    Title = $pr.title
                    Branch = $pr.headRefName
                    Author = $pr.author.login
                    File = $file
                }
            }
        }
    }
}

if ($conflictsFound) {
    Write-Host "================================================" -ForegroundColor Red
    Write-Host "  CONFLICTS DETECTED!" -ForegroundColor Red
    Write-Host "================================================" -ForegroundColor Red
    Write-Host ""

    # Group by PR
    $groupedConflicts = $conflictDetails | Group-Object -Property PR

    foreach ($group in $groupedConflicts) {
        $prInfo = $group.Group[0]
        Write-Host "PR #$($prInfo.PR): $($prInfo.Title)" -ForegroundColor Red
        Write-Host "  Branch: $($prInfo.Branch)" -ForegroundColor Yellow
        Write-Host "  Author: $($prInfo.Author)" -ForegroundColor Yellow
        Write-Host "  Conflicting files:" -ForegroundColor Yellow
        foreach ($conflict in $group.Group) {
            Write-Host "    - $($conflict.File)" -ForegroundColor White
        }
        Write-Host ""
    }

    Write-Host "RECOMMENDATION:" -ForegroundColor Yellow
    Write-Host "  1. Coordinate with the PR author before proceeding" -ForegroundColor White
    Write-Host "  2. Wait for the PR to be merged, then rebase" -ForegroundColor White
    Write-Host "  3. Or work on different files if possible" -ForegroundColor White
    Write-Host ""

    exit 1
} else {
    Write-Host "================================================" -ForegroundColor Green
    Write-Host "  No conflicts found. Safe to proceed!" -ForegroundColor Green
    Write-Host "================================================" -ForegroundColor Green
    Write-Host ""

    if ($Verbose) {
        Write-Host "Checked against PRs:" -ForegroundColor Gray
        foreach ($pr in $openPRs) {
            Write-Host "  #$($pr.number): $($pr.title)" -ForegroundColor Gray
        }
    }

    exit 0
}
