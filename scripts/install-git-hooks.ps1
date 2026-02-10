# Script to install Git hooks for Constellation Engine
# Run this once after cloning the repository

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$HooksDir = Join-Path $RepoRoot ".git\hooks"

Write-Host "Installing Git hooks..." -ForegroundColor Cyan

# Pre-commit hook for scalafmt and scalafix
$PreCommitHook = @'
#!/usr/bin/env bash
set -e

echo "Running code quality checks on staged Scala files..."

# Get list of staged Scala files
STAGED_SCALA_FILES=$(git diff --cached --name-only --diff-filter=ACM | grep '\.scala$' || true)

if [ -z "$STAGED_SCALA_FILES" ]; then
  echo "No Scala files staged, skipping checks"
  exit 0
fi

echo "1/2 Running scalafmt..."
sbt scalafmtAll > /dev/null 2>&1
echo "✓ scalafmt completed"

echo "2/2 Running scalafix..."
sbt scalafixAll > /dev/null 2>&1
echo "✓ scalafix completed"

# Re-stage formatted files
for file in $STAGED_SCALA_FILES; do
  if [ -f "$file" ]; then
    git add "$file"
  fi
done

echo "✓ All code quality checks passed!"
exit 0
'@

$PreCommitPath = Join-Path $HooksDir "pre-commit"
Set-Content -Path $PreCommitPath -Value $PreCommitHook -NoNewline

Write-Host '✓ Pre-commit hook installed successfully!' -ForegroundColor Green
Write-Host ''
Write-Host 'The hook will automatically run scalafmt and scalafix before each commit.'
Write-Host 'To bypass the hook, use: git commit --no-verify'
