#!/usr/bin/env bash
set -e

# Script to install Git hooks for Constellation Engine
# Run this once after cloning the repository

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOOKS_DIR="$REPO_ROOT/.git/hooks"

echo "Installing Git hooks..."

# Pre-commit hook for scalafmt
cat > "$HOOKS_DIR/pre-commit" << 'EOF'
#!/usr/bin/env bash
set -e

echo "Running scalafmt on staged Scala files..."

# Get list of staged Scala files
STAGED_SCALA_FILES=$(git diff --cached --name-only --diff-filter=ACM | grep '\.scala$' || true)

if [ -z "$STAGED_SCALA_FILES" ]; then
  echo "No Scala files staged, skipping scalafmt"
  exit 0
fi

echo "Formatting staged Scala files..."
sbt scalafmtAll > /dev/null 2>&1

# Re-stage formatted files
for file in $STAGED_SCALA_FILES; do
  if [ -f "$file" ]; then
    git add "$file"
  fi
done

echo "✓ Scala files formatted successfully"
exit 0
EOF

chmod +x "$HOOKS_DIR/pre-commit"

echo "✓ Pre-commit hook installed successfully!"
echo ""
echo "The hook will automatically format Scala files before each commit."
echo "To bypass the hook (not recommended), use: git commit --no-verify"
