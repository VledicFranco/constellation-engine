#!/usr/bin/env bash
#
# Release automation script for Constellation Engine
#
# Creates semantic versioned releases (patch, minor, major) with:
# - Version bumping in build.sbt and package.json
# - CHANGELOG.md updates
# - Git commit, tag, and push
# - GitHub release creation
#
# Usage:
#   ./scripts/release.sh patch|minor|major [--dry-run]
#
# Examples:
#   ./scripts/release.sh patch
#   ./scripts/release.sh minor --dry-run

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
GRAY='\033[0;37m'
NC='\033[0m' # No Color

step() { echo -e "\n${CYAN}==> $1${NC}"; }
info() { echo -e "    ${GRAY}$1${NC}"; }
success() { echo -e "    ${GREEN}$1${NC}"; }
warn() { echo -e "    ${YELLOW}$1${NC}"; }
error() { echo -e "    ${RED}$1${NC}"; }

# Parse arguments
TYPE=""
DRY_RUN=false

for arg in "$@"; do
    case $arg in
        patch|minor|major)
            TYPE=$arg
            ;;
        --dry-run)
            DRY_RUN=true
            ;;
        -h|--help)
            echo "Usage: $0 patch|minor|major [--dry-run]"
            echo ""
            echo "Arguments:"
            echo "  patch      Bump patch version (0.1.0 -> 0.1.1)"
            echo "  minor      Bump minor version (0.1.0 -> 0.2.0)"
            echo "  major      Bump major version (0.1.0 -> 1.0.0)"
            echo "  --dry-run  Preview changes without executing them"
            exit 0
            ;;
        *)
            error "Unknown argument: $arg"
            echo "Usage: $0 patch|minor|major [--dry-run]"
            exit 1
            ;;
    esac
done

if [ -z "$TYPE" ]; then
    error "Release type required: patch, minor, or major"
    echo "Usage: $0 patch|minor|major [--dry-run]"
    exit 1
fi

# Get repository root
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

echo -e "\n${MAGENTA}Constellation Engine Release Script${NC}"
echo -e "${MAGENTA}===================================${NC}"
if [ "$DRY_RUN" = true ]; then
    warn "DRY RUN MODE - No changes will be made"
fi

# Check prerequisites
step "Checking prerequisites..."

# Check for gh CLI
if ! command -v gh &> /dev/null; then
    error "GitHub CLI (gh) is not installed. Install from: https://cli.github.com/"
    exit 1
fi
success "GitHub CLI found"

# Check gh auth
if ! gh auth status &> /dev/null; then
    error "Not authenticated with GitHub CLI. Run: gh auth login"
    exit 1
fi
success "GitHub CLI authenticated"

# Check for clean working directory (excluding agents/)
if [ -n "$(git status --porcelain -- ':!agents/')" ]; then
    error "Working directory not clean. Commit or stash changes first."
    info "Changed files:"
    git status --porcelain -- ':!agents/' | while read line; do
        info "  $line"
    done
    exit 1
fi
success "Working directory clean"

# Check we're on master
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "master" ]; then
    error "Must be on master branch (currently on: $CURRENT_BRANCH)"
    exit 1
fi
success "On master branch"

# Ensure up to date with remote
step "Syncing with remote..."
git fetch origin
BEHIND=$(git rev-list HEAD..origin/master --count)
if [ "$BEHIND" -gt 0 ]; then
    error "Local branch is behind origin/master by $BEHIND commits. Run: git pull"
    exit 1
fi
success "Up to date with origin/master"

# Parse current version from build.sbt
step "Reading current version..."
CURRENT_VERSION=$(grep 'ThisBuild / version :=' build.sbt | sed 's/.*"\([^"]*\)".*/\1/')
if [ -z "$CURRENT_VERSION" ]; then
    error "Could not parse version from build.sbt"
    exit 1
fi

# Extract version components
MAJOR=$(echo "$CURRENT_VERSION" | sed 's/-.*//' | cut -d. -f1)
MINOR=$(echo "$CURRENT_VERSION" | sed 's/-.*//' | cut -d. -f2)
PATCH=$(echo "$CURRENT_VERSION" | sed 's/-.*//' | cut -d. -f3)

info "Current version: $CURRENT_VERSION"

# Calculate new version
case $TYPE in
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        ;;
    patch)
        PATCH=$((PATCH + 1))
        ;;
esac

NEW_VERSION="$MAJOR.$MINOR.$PATCH"
success "New version: $NEW_VERSION"

# Update build.sbt
step "Updating build.sbt..."
if [ "$DRY_RUN" = false ]; then
    sed -i.bak "s/ThisBuild \/ version := \"[^\"]*\"/ThisBuild \/ version := \"$NEW_VERSION\"/" build.sbt
    rm -f build.sbt.bak
fi
success "build.sbt updated"

# Update vscode-extension/package.json
step "Updating vscode-extension/package.json..."
if [ "$DRY_RUN" = false ]; then
    sed -i.bak "s/\"version\": \"[^\"]*\"/\"version\": \"$NEW_VERSION\"/" vscode-extension/package.json
    rm -f vscode-extension/package.json.bak
fi
success "package.json updated"

# Update sdks/typescript/package.json
step "Updating sdks/typescript/package.json..."
if [ "$DRY_RUN" = false ]; then
    sed -i.bak "s/\"version\": \"[^\"]*\"/\"version\": \"$NEW_VERSION\"/" sdks/typescript/package.json
    rm -f sdks/typescript/package.json.bak
fi
success "sdks/typescript/package.json updated"

# Update CHANGELOG.md
step "Updating CHANGELOG.md..."
TODAY=$(date +%Y-%m-%d)
if [ "$DRY_RUN" = false ]; then
    sed -i.bak "s/\[Unreleased\]/[$NEW_VERSION] - $TODAY/" CHANGELOG.md
    rm -f CHANGELOG.md.bak
fi
success "CHANGELOG.md updated with release date"

# Run tests
step "Running tests..."
if [ "$DRY_RUN" = false ]; then
    if ! sbt test; then
        error "Tests failed! Aborting release."
        # Revert changes
        git checkout -- build.sbt vscode-extension/package.json sdks/typescript/package.json CHANGELOG.md
        exit 1
    fi
    success "All tests passed"
else
    info "Skipping tests (dry run)"
fi

# Git operations
step "Creating git commit..."
if [ "$DRY_RUN" = false ]; then
    git add build.sbt vscode-extension/package.json sdks/typescript/package.json CHANGELOG.md
    git commit -m "chore(release): v$NEW_VERSION"
fi
success "Commit created"

step "Creating git tag..."
if [ "$DRY_RUN" = false ]; then
    git tag -a "v$NEW_VERSION" -m "Release v$NEW_VERSION"
fi
success "Tag v$NEW_VERSION created"

step "Pushing to origin..."
if [ "$DRY_RUN" = false ]; then
    git push origin master
    git push origin "v$NEW_VERSION"
fi
success "Pushed to origin"

# Create GitHub release
step "Creating GitHub release..."
RELEASE_NOTES="## What's Changed

See [CHANGELOG.md](https://github.com/VledicFranco/constellation-engine/blob/v$NEW_VERSION/CHANGELOG.md) for details.

### Installation

**SBT:**
\`\`\`scala
libraryDependencies += \"io.constellation\" %% \"constellation-core\" % \"$NEW_VERSION\"
\`\`\`

**VSCode Extension:**
Install from the [VS Code Marketplace](https://marketplace.visualstudio.com/items?itemName=constellation.constellation-lang) or download the \`.vsix\` from the release assets."

if [ "$DRY_RUN" = false ]; then
    gh release create "v$NEW_VERSION" \
        --title "v$NEW_VERSION" \
        --notes "$RELEASE_NOTES" \
        --latest
fi
success "GitHub release created"

# Summary
echo ""
echo -e "${GREEN}Release v$NEW_VERSION complete!${NC}"
echo -e "${GREEN}================================${NC}"
info "- build.sbt: $NEW_VERSION"
info "- vscode-extension/package.json: $NEW_VERSION"
info "- sdks/typescript/package.json: $NEW_VERSION"
info "- Tag: v$NEW_VERSION"
info "- GitHub Release: https://github.com/VledicFranco/constellation-engine/releases/tag/v$NEW_VERSION"

if [ "$DRY_RUN" = true ]; then
    echo ""
    warn "This was a dry run. Run without --dry-run to execute."
fi
