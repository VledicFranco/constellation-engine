# Development Workflows

> **Path**: `docs/dev/workflows/`
> **Parent**: [dev/](../README.md)

Development workflows and tooling guides for contributors.

## Contents

| File | Description |
|------|-------------|
| [playwright-dev-loop.md](./playwright-dev-loop.md) | Screenshot-driven UI development protocol |

## Overview

This directory contains development workflow documentation — repeatable processes for common development tasks:

- **UI Development** — Screenshot-driven iteration with Playwright
- **Testing Workflows** — How to run and debug tests
- **Debugging Guides** — Troubleshooting common issues

## Quick Reference

### Playwright Dev Loop

For visual CSS/HTML/JS changes, use the screenshot-driven dev loop:

```powershell
.\scripts\dev-loop.ps1              # Frontend-only: skip compile
.\scripts\dev-loop.ps1 -Compile     # Backend changes: compile first
```

Screenshots are captured to `dashboard-tests/screenshots/` for visual analysis.

See [playwright-dev-loop.md](./playwright-dev-loop.md) for the full protocol.
