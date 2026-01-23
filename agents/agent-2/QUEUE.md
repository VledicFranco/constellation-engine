# Agent 2 Work Queue

**Track:** Language Operators
**Focus:** Operators, Type System Extensions, Orchestration

## Assigned Issues (Max 3)

| Priority | Issue | Title | Status | Branch |
|----------|-------|-------|--------|--------|
| P1 | [#114](https://github.com/VledicFranco/constellation-engine/issues/114) | [Phase 1.3] Implement Completion Trie | In Progress | agent-2/issue-114-completion-trie |

## Backlog

_No additional issues._

## Phase 1 Context

**Read before starting:** `agents/phase-1/context-completion-trie.md`

This task replaces linear module filtering with a prefix trie for O(prefix length) lookups. Low effort (1-2 days).

---

## Implementation Protocol

> **Reference:** See `CLAUDE.md` for complete git workflow, agent startup checklist, self-review protocol, and Phase task workflow. This section is a quick reference.

### Before Writing Any Code

1. **Verify worktree exists** (if not, create from main repo)
   ```bash
   ls ../constellation-agent-2 || (cd ../constellation-engine && git worktree add ../constellation-agent-2 -b agent-2/work master)
   ```

2. **Complete Agent Startup Checklist** (see CLAUDE.md)
   - Verify worktree: `git rev-parse --git-dir` (must contain "worktrees/")
   - Sync with master: `git fetch origin && git checkout master && git pull`
   - Verify build works: `make compile && make test`
   - If build fails on fresh master, STOP and report - don't start on broken master
   - Create branch: `git checkout -b agent-2/issue-<NUMBER>-<short-desc>`

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
   # Edit: agents/agent-2/QUEUE.md → Status: "In Progress"
   git add agents/agent-2/QUEUE.md
   git commit -m "docs(agents): agent-2 starting #<NUMBER>" && git push
   cd ../constellation-agent-2
   git checkout agent-2/issue-<NUMBER>-<short-desc>  # Return to feature branch!
   ```
   - Comment on GitHub issue to claim it (use `mcp__github__add_issue_comment`)

### Pre-Flight Checklist (Ready to Code?)

Before writing any code, verify:
- [ ] In worktree? (`git rev-parse --git-dir` contains "worktrees/")
- [ ] On feature branch? (`git branch --show-current` = `agent-2/issue-...`)
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
    # Edit: agents/agent-2/QUEUE.md → Move to Completed Issues table
    git add agents/agent-2/QUEUE.md
    git commit -m "docs(agents): agent-2 completed #<NUMBER>" && git push
    ```

### If Blocked

- Update QUEUE.md with blocker note in Status column
- Comment on GitHub issue with blocker details (use `mcp__github__add_issue_comment`)
- Notify via issue comment if other agents are affected

---

## Completed Issues

| Issue | Title | PR |
|-------|-------|-----|
| #5 | Add test coverage for example-app modules | Merged |
| #9 | Implement comparison operators | Merged |
| #10 | Implement boolean operators (`and`, `or`, `not`) | Merged |
| #15 | Implement guard expressions (`when`) | Merged |
| #17 | Implement coalesce operator (`??`) | Merged |
| #22 | Implement branch expressions | Merged |
| #23 | Implement lambda expressions | Merged |
| #71 | Add tests for guard, coalesce, branch semantics | Merged |
| #82 | Add tests for StdLib edge cases | Merged |
| #115 | [Phase 1.4] Implement Improved Error Messages | Merged |

## Notes

- **10 issues completed**, starting Phase 1.3 (#114 - Completion Trie)
- Phase 1.4 complete (#115) - structured error codes, suggestions, rich formatting
- Focus: LSP performance improvements with trie-based completions

## Dependencies

```
All complete - no remaining dependencies
```

---
*Updated: 2026-01-22 (Starting #114)*
