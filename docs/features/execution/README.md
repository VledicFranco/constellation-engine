# Execution Modes

> **Path**: `docs/features/execution/`
> **Parent**: [features/](../README.md)

Hot, cold, and suspended execution modes.

## Ethos Summary

- **Hot** execution is the default for development (compile + run in one request)
- **Cold** execution is for production (pre-compiled, execute by reference)
- **Suspended** execution handles missing inputs gracefully
- Mode choice is **explicit**, not inferred

## Contents

| File | Description |
|------|-------------|
| PHILOSOPHY.md | Why multiple execution modes exist |
| ETHOS.md | Constraints for LLMs working on execution |
| hot-execution.md | Compile and run in one request |
| cold-execution.md | Pre-compiled, execute by reference |
| suspension.md | Pause on missing inputs, resume later |

## Quick Reference

| Mode | Endpoint | Use Case |
|------|----------|----------|
| Hot | `POST /run` | Development, ad-hoc queries |
| Cold | `POST /compile` → `POST /execute` | Production, high throughput |
| Suspended | `POST /run` → `POST /resume` | Multi-step workflows |

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `http-api` | Endpoints for each mode | `ConstellationRoutes.scala` |
| `runtime` | Execution and suspension | `Runtime.scala`, `SuspendableExecution.scala` |
| `lang-compiler` | Compilation pipeline | `LangCompiler.scala` |
