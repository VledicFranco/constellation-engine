# Agent 3 Work Queue

**Track:** Integration
**Focus:** LSP, HTTP API, VSCode Extension

## Assigned Issues

| Priority | Issue | Title | Status | Branch |
|----------|-------|-------|--------|--------|
| - | - | - | - | - |

## Backlog

_No additional issues._

---

## Implementation Protocol

> **Reference:** See `CLAUDE.md` for complete git workflow, agent startup checklist, self-review protocol, and Phase task workflow. This section is a quick reference.

### Before Writing Any Code

1. **Verify worktree exists** (if not, create from main repo)
   ```bash
   ls ../constellation-agent-3 || (cd ../constellation-engine && git worktree add ../constellation-agent-3 -b agent-3/work master)
   ```

2. **Complete Agent Startup Checklist** (see CLAUDE.md)
   - Verify worktree: `git rev-parse --git-dir` (must contain "worktrees/")
   - Sync with master: `git fetch origin && git checkout master && git pull`
   - Verify build works: `make compile && make test`
   - If build fails on fresh master, STOP and report - don't start on broken master
   - Create branch: `git checkout -b agent-3/issue-<NUMBER>-<short-desc>`

3. **Read the context file THOROUGHLY**
   - Context file is the **canonical** implementation guide
   - Read ALL sections, especially "Notes for Implementer"
   - If context file references `docs/dev/optimizations/`, use for background only
   - When in doubt, follow the context file over other docs

4. **Explore existing code BEFORE writing any code**
   - Read all **existing** files in "Files to Modify" (those marked "Modify", skip "New")
   - Understand current patterns and style
   - Note any conflicts with context file design (ask if unsure)

5. **Update QUEUE.md and claim issue on GitHub**
   ```bash
   cd ../constellation-engine && git pull origin master
   # Edit: agents/agent-3/QUEUE.md → Status: "In Progress"
   git add agents/agent-3/QUEUE.md
   git commit -m "docs(agents): agent-3 starting #<NUMBER>" && git push
   cd ../constellation-agent-3
   git checkout agent-3/issue-<NUMBER>-<short-desc>  # Return to feature branch!
   ```
   - Comment on GitHub issue to claim it (use `mcp__github__add_issue_comment`)

### Pre-Flight Checklist (Ready to Code?)

Before writing any code, verify:
- [ ] In worktree? (`git rev-parse --git-dir` contains "worktrees/")
- [ ] On feature branch? (`git branch --show-current` = `agent-3/issue-...`)
- [ ] Build passes? (`make compile && make test`)
- [ ] Context file read thoroughly?
- [ ] Existing files in "Files to Modify" explored?
- [ ] QUEUE.md updated and pushed?
- [ ] GitHub issue claimed with comment?

### While Implementing

6. **Follow the Implementation Guide** in context file
   - Work through Steps 1, 2, 3... in order
   - Check off Deliverables as you complete them

7. **Write tests (MANDATORY)**
   - Write unit tests for all new code (>80% coverage target)
   - Update existing tests if modifying existing code
   - Write integration tests for component interactions
   - Run `make test` before each commit

8. **Update documentation (MANDATORY)**
   - Add ScalaDoc to public APIs
   - Update `CHANGELOG.md` under `[Unreleased]`
   - Update relevant docs in `docs/` if behavior changes

9. **Make incremental commits**
   - Commit after each logical unit of work
   - Run `make test` before each commit

### Before Merging

10. **Verify against Acceptance Criteria**
    - Every checkbox in context file's Acceptance Criteria must pass
    - Run full test suite: `make test`

11. **Self-review** (see CLAUDE.md for full checklist)
    - `git diff origin/master...HEAD` to review all changes
    - Check code quality, consistency, test coverage

12. **Merge to master** (see CLAUDE.md Git Workflow)
    - Rebase on latest master, resolve conflicts, re-run tests
    - Merge and push

13. **Update QUEUE.md in main repo**
    ```bash
    cd ../constellation-engine && git pull origin master
    # Edit: agents/agent-3/QUEUE.md → Move to Completed Issues table
    git add agents/agent-3/QUEUE.md
    git commit -m "docs(agents): agent-3 completed #<NUMBER>" && git push
    ```

### If Blocked

- Update QUEUE.md with blocker note in Status column
- Comment on GitHub issue with blocker details (use `mcp__github__add_issue_comment`)
- Notify via issue comment if other agents are affected

---

## Completed Issues

| Issue | Title | PR |
|-------|-------|-----|
| #118 | [Phase 2.3] LSP Semantic Tokens | Merged |
| #113 | [Phase 1.2] Implement Debounced LSP Analysis | Merged |
| #106 | VSCode Extension: Update ScriptRunnerPanel to use examples | Merged |
| #105 | LSP Server: Extract and expose example values | Merged |
| #104 | LSP Protocol: Add example field to InputField | Merged |
| #95 | Create OpenAPI/Swagger specification for HTTP API | Merged |
| #90 | Add logging to silent error handlers in LSP server | Merged |
| #85 | Improve lang-lsp test coverage to 60% | Merged |
| #83 | Fix pattern match exhaustivity warning in LSP | Merged |
| #81 | Add unit tests for DAG layout computation | Merged |
| #80 | Add e2e tests for Script Runner execution | Merged |
| #79 | Add e2e tests for DAG interactivity | Merged |
| #78 | Add e2e tests for execution state visualization | Merged |
| #77 | Add e2e tests for step-through debugging | Merged |
| #43 | Add integration/e2e tests for VSCode extension | Merged |
| #34 | Remove redundant empty message check | Merged |
| #18 | DAG Visualizer: Add execution highlighting | Merged |
| #14 | Refactor: Extract common webview patterns | Merged |
| #13 | DAG Visualizer: Export as PNG/SVG | Merged |
| #12 | DAG Visualizer: Add layout direction toggle | Merged |
| #7 | Remove redundant imports in LSP server | Merged |
| #6 | Fix "Empty message" error logging | Merged |
| #2 | Display errors in VSCode extension results | Merged |
| #1 | Improve DAG Visualizer UX | Merged |

## Notes

- **24 issues completed** - Phase 2 complete!
- Agent 3 queue empty - awaiting Phase 4 assignments
- Task 4.2 (Full Incremental LSP) dependency #120 now COMPLETE
- Ready to be assigned Task 4.2 once issue is created

## Dependencies

```
Phase 2 ✅ COMPLETE
#118 ✅ ──▶ Task 4.2 (Full Incremental LSP) - UNBLOCKED
#120 ✅ (Agent 1 COMPLETE) ──▶ Task 4.2 (Agent 3 ready)
```

---
*Updated: 2026-01-23 (#120 dependency complete)*
