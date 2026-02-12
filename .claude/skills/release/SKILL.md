---
name: release
description: >
  Maven publishing workflow using the existing release.ps1/release.sh infrastructure.
  Handles pre-flight checks, QA gate, version bumping, CHANGELOG update, Git tag, GitHub Release,
  and Maven Central publish trigger. Use when the user invokes /release.
  Supports: patch, minor, major, and dry-run mode.
disable-model-invocation: true
allowed-tools: Bash, Read, AskUserQuestion
argument-hint: "[dry] [patch|minor|major]"
---

# Release Workflow

Guides a Constellation Engine release through pre-flight, QA, and publish stages. Delegates the actual release to `scripts/release.ps1` (Windows) or `scripts/release.sh` (Unix).

---

## Argument Parsing

Parse the invocation arguments:

| Invocation | Type | Dry Run |
|------------|------|---------|
| `/release minor` | minor | no |
| `/release patch` | patch | no |
| `/release major` | major | no |
| `/release dry minor` | minor | yes |
| `/release dry patch` | patch | yes |
| `/release dry major` | major | yes |

If no release type is provided, ask the user which type they want (patch/minor/major).

---

## Workflow Stages

### Stage 1 — Pre-Flight Checks (automatic)

Run these checks and report results. Fail early if any check fails.

```bash
# 1. Verify on master branch
git branch --show-current
# Must be "master"

# 2. Clean working directory
git status --porcelain
# Must be empty (or only untracked files in agents/)

# 3. Synced with origin
git fetch origin master
git rev-list HEAD..origin/master --count
# Must be "0"

# 4. gh CLI authenticated
gh auth status
# Must succeed
```

Report:
```
Pre-flight:
  Branch: master
  Working dir: clean
  Remote sync: up to date
  GitHub CLI: authenticated
```

### Stage 2 — CHANGELOG Review (interactive)

Read `CHANGELOG.md` and extract the `[Unreleased]` section. Present it to the user.

- If `[Unreleased]` section is empty or missing, warn the user and ask whether to proceed
- If it has content, show the content and confirm it looks correct
- Use `AskUserQuestion` to get confirmation before proceeding

### Stage 3 — QA Gate (automatic)

Run phases 1-6 of the `/qa` pipeline (compile + format + lint + docs + ethos + tests):

```bash
sbt compile
sbt scalafmtCheckAll
sbt -J-Xmx4g "scalafixAll --check"
sbt "docGenerator/runMain io.constellation.docgen.FreshnessChecker"
sbt "docGenerator/runMain io.constellation.docgen.EthosVerifier"
sbt -J-Xmx4g test
```

Note: `-J-Xmx4g` is required for scalafix and test — parallel compilation OOMs with default heap.

Sequential execution — fail on first error. If any phase fails, abort the release and report which phase failed with its fix suggestion.

### Stage 4 — Version Confirmation (interactive)

Extract the current version from `build.sbt`:
```bash
grep 'ThisBuild / version' build.sbt
```

Calculate the new version based on the release type and present to the user:

```
Current version: 0.6.1(-SNAPSHOT)
Release type: minor
New version: 0.7.0

Files that will be updated:
  - build.sbt
  - vscode-extension/package.json
  - CHANGELOG.md ([Unreleased] → [0.7.0] - 2026-02-12)
```

Use `AskUserQuestion` to confirm before proceeding.

### Stage 5 — Dry Run (automatic)

Run the release script in dry-run mode:

**Windows:**
```powershell
.\scripts\release.ps1 -Type <type> -DryRun
```

**Unix:**
```bash
./scripts/release.sh <type> --dry-run
```

Show the dry-run output to the user. If `/release dry <type>` was invoked, stop here — do not proceed to Stage 6.

### Stage 6 — Execute Release (user confirmation required)

This is a destructive action. Before executing, clearly state what will happen:

```
This will:
  1. Update version in build.sbt, package.json, CHANGELOG.md
  2. Run sbt test
  3. Commit: "chore(release): v0.7.0"
  4. Tag: v0.7.0
  5. Push commit and tag to origin/master
  6. Create GitHub Release (triggers Maven Central publish)

Proceed?
```

Use `AskUserQuestion` to get explicit confirmation. On approval:

**Windows:**
```powershell
.\scripts\release.ps1 -Type <type>
```

**Unix:**
```bash
./scripts/release.sh <type>
```

### Stage 7 — Post-Release Verification (automatic)

After the release script completes:

```bash
# Verify tag exists
git tag -l "v<new-version>"

# Verify GitHub release was created
gh release view "v<new-version>"

# Check if publish workflow was triggered
gh run list --workflow=publish.yml --limit=1
```

Report:
```
Post-release:
  Git tag: v0.7.0 (created)
  GitHub Release: https://github.com/VledicFranco/constellation-engine/releases/tag/v0.7.0
  Publish workflow: triggered (run #1234)
```

---

## Published Modules

These modules are published to Maven Central when the publish workflow runs:

| Module | Maven Artifact |
|--------|---------------|
| core | constellation-core |
| runtime | constellation-runtime |
| lang-ast | constellation-lang-ast |
| lang-parser | constellation-lang-parser |
| lang-compiler | constellation-lang-compiler |
| lang-stdlib | constellation-lang-stdlib |
| lang-lsp | constellation-lang-lsp |
| http-api | constellation-http-api |
| module-provider-sdk | constellation-module-provider-sdk |
| cache-memcached | constellation-cache-memcached |

**NOT published** (publish/skip = true): module-provider (server), example-app, lang-cli, doc-generator, root.

Maven group: `io.github.vledicfranco`

For publish infrastructure details, see `PUBLISH-INFRA.md`.

---

## Error Recovery

| Stage | Recovery |
|-------|----------|
| Pre-flight: wrong branch | `git checkout master` |
| Pre-flight: dirty workdir | `git stash` or commit changes |
| Pre-flight: behind remote | `git pull origin master` |
| QA gate failure | Fix the issue, re-run `/qa quick` or `/qa tests` |
| Release script failure | Script auto-reverts file changes on test failure |
| Post-release: no workflow | Check GitHub Actions tab manually |
