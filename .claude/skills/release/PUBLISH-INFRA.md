# Publish Infrastructure

How Constellation Engine artifacts get from Git tag to Maven Central.

## Pipeline

```
git push tag v0.7.0
  → GitHub Release (created by release.ps1)
    → .github/workflows/publish.yml (triggered on release:published)
      → sbt ci-release
        → Sign with PGP
        → Publish to Sonatype (Maven Central staging)
        → Auto-release to Maven Central
```

## GitHub Actions Workflow

**File:** `.github/workflows/publish.yml`

**Trigger:** `release` event, type `published`

**Steps:**
1. Checkout with full history (`fetch-depth: 0`)
2. Setup Java 17 (Temurin) with sbt caching
3. Setup sbt
4. Run `sbt ci-release`

**Secrets required:**
| Secret | Purpose |
|--------|---------|
| `PGP_PASSPHRASE` | GPG key passphrase for artifact signing |
| `PGP_SECRET` | Base64-encoded GPG secret key |
| `SONATYPE_USERNAME` | Maven Central (Sonatype) username |
| `SONATYPE_PASSWORD` | Maven Central (Sonatype) password |

## sbt-ci-release Plugin

**Version:** 1.11.2 (configured in `project/plugins.sbt`)

The `ci-release` task:
1. Reads version from Git tag (strips `v` prefix)
2. Sets `publishTo` to Sonatype staging
3. Signs artifacts with PGP
4. Publishes all non-skipped modules
5. Auto-releases the staging repository

## Modules Published

Controlled by `publish / skip` in `build.sbt`:

**Published:**
- constellation-core
- constellation-runtime
- constellation-lang-ast
- constellation-lang-parser
- constellation-lang-compiler
- constellation-lang-stdlib
- constellation-lang-lsp
- constellation-http-api
- constellation-module-provider-sdk
- constellation-cache-memcached

**Skipped:**
- constellation-module-provider (server — not a library)
- constellation-example-app (demo only)
- constellation-lang-cli (standalone binary)
- constellation-doc-generator (internal tooling)
- constellation-engine (root aggregate)

## Maven Coordinates

```xml
<groupId>io.github.vledicfranco</groupId>
<artifactId>constellation-core_3</artifactId>
<version>0.7.0</version>
```

sbt:
```scala
libraryDependencies += "io.github.vledicfranco" %% "constellation-core" % "0.7.0"
```

## Troubleshooting

| Issue | Check |
|-------|-------|
| Workflow not triggered | Verify release was created (not draft). Check `gh release list`. |
| PGP signing failure | Verify `PGP_SECRET` is base64-encoded. Re-export: `gpg --export-secret-keys <id> \| base64` |
| Sonatype rejection | Check staging repo at https://s01.oss.sonatype.org/. Common: missing javadoc, bad POM. |
| Version mismatch | `ci-release` reads version from Git tag, not build.sbt. Ensure tag matches. |
| Partial publish | Some modules may publish while others fail. Check Sonatype staging for partial uploads. |

## Monitoring a Release

```bash
# Check workflow status
gh run list --workflow=publish.yml --limit=5

# Watch a specific run
gh run watch <run-id>

# View workflow logs
gh run view <run-id> --log
```
