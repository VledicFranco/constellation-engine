<#
.SYNOPSIS
    Release automation script for Constellation Engine

.DESCRIPTION
    Creates semantic versioned releases (patch, minor, major) with:
    - Version bumping in build.sbt and package.json
    - CHANGELOG.md updates
    - Git commit, tag, and push
    - GitHub release creation

.PARAMETER Type
    Release type: patch, minor, or major

.PARAMETER DryRun
    Preview changes without executing them

.EXAMPLE
    .\scripts\release.ps1 -Type patch
    .\scripts\release.ps1 -Type minor -DryRun
#>

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("patch", "minor", "major")]
    [string]$Type,

    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

# Colors for output
function Write-Step { param($msg) Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Info { param($msg) Write-Host "    $msg" -ForegroundColor Gray }
function Write-Success { param($msg) Write-Host "    $msg" -ForegroundColor Green }
function Write-Warning { param($msg) Write-Host "    $msg" -ForegroundColor Yellow }
function Write-Error { param($msg) Write-Host "    $msg" -ForegroundColor Red }

# Get repository root
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $ScriptDir
if (-not $RepoRoot -or -not (Test-Path "$RepoRoot\.git")) {
    $RepoRoot = (Get-Location).Path
}
Set-Location $RepoRoot

Write-Host "`nConstellation Engine Release Script" -ForegroundColor Magenta
Write-Host "===================================" -ForegroundColor Magenta
if ($DryRun) { Write-Warning "DRY RUN MODE - No changes will be made" }

# Check prerequisites
Write-Step "Checking prerequisites..."

# Check for gh CLI
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Error "GitHub CLI (gh) is not installed. Install from: https://cli.github.com/"
    exit 1
}
Write-Success "GitHub CLI found"

# Check gh auth
$ghAuth = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "Not authenticated with GitHub CLI. Run: gh auth login"
    exit 1
}
Write-Success "GitHub CLI authenticated"

# Check for clean working directory (modified/staged files only, allow untracked)
$gitStatus = git status --porcelain -- ':!agents/' | Where-Object { $_ -notmatch '^\?\?' }
if ($gitStatus) {
    Write-Error "Working directory not clean. Commit or stash changes first."
    Write-Info "Changed files:"
    $gitStatus | ForEach-Object { Write-Info "  $_" }
    exit 1
}
Write-Success "Working directory clean"

# Check we're on master
$currentBranch = git branch --show-current
if ($currentBranch -ne "master") {
    Write-Error "Must be on master branch (currently on: $currentBranch)"
    exit 1
}
Write-Success "On master branch"

# Ensure up to date with remote
Write-Step "Syncing with remote..."
git fetch origin
$behind = git rev-list HEAD..origin/master --count
if ([int]$behind -gt 0) {
    Write-Error "Local branch is behind origin/master by $behind commits. Run: git pull"
    exit 1
}
Write-Success "Up to date with origin/master"

# Parse current version from build.sbt
Write-Step "Reading current version..."
$buildSbt = Get-Content "$RepoRoot\build.sbt" -Raw
if ($buildSbt -match 'ThisBuild / version := "(\d+)\.(\d+)\.(\d+)(-SNAPSHOT)?"') {
    $major = [int]$Matches[1]
    $minor = [int]$Matches[2]
    $patch = [int]$Matches[3]
    $isSnapshot = $Matches[4] -eq "-SNAPSHOT"
    $currentVersion = "$major.$minor.$patch"
    if ($isSnapshot) { $currentVersion += "-SNAPSHOT" }
} else {
    Write-Error "Could not parse version from build.sbt"
    exit 1
}
Write-Info "Current version: $currentVersion"

# Calculate new version
switch ($Type) {
    "major" { $major++; $minor = 0; $patch = 0 }
    "minor" { $minor++; $patch = 0 }
    "patch" { $patch++ }
}
$newVersion = "$major.$minor.$patch"
Write-Success "New version: $newVersion"

# Update build.sbt
Write-Step "Updating build.sbt..."
$newBuildSbt = $buildSbt -replace 'ThisBuild / version := "[^"]+"', "ThisBuild / version := `"$newVersion`""
if (-not $DryRun) {
    $newBuildSbt | Set-Content "$RepoRoot\build.sbt" -NoNewline
}
Write-Success "build.sbt updated"

# Update vscode-extension/package.json
Write-Step "Updating vscode-extension/package.json..."
$packageJson = Get-Content "$RepoRoot\vscode-extension\package.json" -Raw
$newPackageJson = $packageJson -replace '"version": "[^"]+"', "`"version`": `"$newVersion`""
if (-not $DryRun) {
    $newPackageJson | Set-Content "$RepoRoot\vscode-extension\package.json" -NoNewline
}
Write-Success "package.json updated"

# Update sdks/typescript/package.json
Write-Step "Updating sdks/typescript/package.json..."
$tsSdkPkgJson = Get-Content "$RepoRoot\sdks\typescript\package.json" -Raw
$newTsSdkPkgJson = $tsSdkPkgJson -replace '"version": "[^"]+"', "`"version`": `"$newVersion`""
if (-not $DryRun) {
    $newTsSdkPkgJson | Set-Content "$RepoRoot\sdks\typescript\package.json" -NoNewline
}
Write-Success "sdks/typescript/package.json updated"

# Update CHANGELOG.md
Write-Step "Updating CHANGELOG.md..."
$changelog = Get-Content "$RepoRoot\CHANGELOG.md" -Raw
$today = Get-Date -Format "yyyy-MM-dd"
$newChangelog = $changelog -replace '\[Unreleased\]', "[$newVersion] - $today"
if (-not $DryRun) {
    $newChangelog | Set-Content "$RepoRoot\CHANGELOG.md" -NoNewline
}
Write-Success "CHANGELOG.md updated with release date"

# Run tests
Write-Step "Running tests..."
if (-not $DryRun) {
    $testResult = sbt test 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Tests failed! Aborting release."
        # Revert changes
        git checkout -- build.sbt vscode-extension/package.json sdks/typescript/package.json CHANGELOG.md
        exit 1
    }
    Write-Success "All tests passed"
} else {
    Write-Info "Skipping tests (dry run)"
}

# Git operations
Write-Step "Creating git commit..."
if (-not $DryRun) {
    git add build.sbt vscode-extension/package.json sdks/typescript/package.json CHANGELOG.md
    git commit -m "chore(release): v$newVersion"
}
Write-Success "Commit created"

Write-Step "Creating git tag..."
if (-not $DryRun) {
    git tag -a "v$newVersion" -m "Release v$newVersion"
}
Write-Success "Tag v$newVersion created"

Write-Step "Pushing to origin..."
if (-not $DryRun) {
    git push origin master
    git push origin "v$newVersion"
}
Write-Success "Pushed to origin"

# Create GitHub release
Write-Step "Creating GitHub release..."
$releaseNotes = @"
## What's Changed

See [CHANGELOG.md](https://github.com/VledicFranco/constellation-engine/blob/v$newVersion/CHANGELOG.md) for details.

### Installation

**SBT:**
``````scala
libraryDependencies += "io.constellation" %% "constellation-core" % "$newVersion"
``````

**VSCode Extension:**
Install from the [VS Code Marketplace](https://marketplace.visualstudio.com/items?itemName=constellation.constellation-lang) or download the `.vsix` from the release assets.
"@

if (-not $DryRun) {
    gh release create "v$newVersion" `
        --title "v$newVersion" `
        --notes $releaseNotes `
        --latest
}
Write-Success "GitHub release created"

# Summary
Write-Host "`n" -NoNewline
Write-Host "Release v$newVersion complete!" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Green
Write-Info "- build.sbt: $newVersion"
Write-Info "- vscode-extension/package.json: $newVersion"
Write-Info "- sdks/typescript/package.json: $newVersion"
Write-Info "- Tag: v$newVersion"
Write-Info "- GitHub Release: https://github.com/VledicFranco/constellation-engine/releases/tag/v$newVersion"

if ($DryRun) {
    Write-Host "`n"
    Write-Warning "This was a dry run. Run without -DryRun to execute."
}
