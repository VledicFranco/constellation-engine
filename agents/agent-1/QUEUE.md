# Agent 1 Work Queue

**Track:** Compiler/Language
**Focus:** Parser, Type System, Core Language

## Assigned Issues

| Priority | Issue | Title | Status | Branch |
|----------|-------|-------|--------|--------|
| - | - | _Queue empty - awaiting new assignments_ | - | - |

## Backlog

_No additional issues._

---

## Implementation Protocol

> **Reference:** See `CLAUDE.md` for complete git workflow, agent startup checklist, self-review protocol, and Phase task workflow. This section is a quick reference.

### Before Writing Any Code

1. **Verify worktree exists** (if not, create from main repo)
   ```bash
   ls ../constellation-agent-1 || (cd ../constellation-engine && git worktree add ../constellation-agent-1 -b agent-1/work master)
   ```

2. **Complete Agent Startup Checklist** (see CLAUDE.md)
   - Verify worktree: `git rev-parse --git-dir` (must contain "worktrees/")
   - Sync with master: `git fetch origin && git checkout master && git pull`
   - Verify build works: `make compile && make test`
   - If build fails on fresh master, STOP and report - don't start on broken master
   - Create branch: `git checkout -b agent-1/issue-<NUMBER>-<short-desc>`

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
   # Edit: agents/agent-1/QUEUE.md → Status: "In Progress"
   git add agents/agent-1/QUEUE.md
   git commit -m "docs(agents): agent-1 starting #<NUMBER>" && git push
   cd ../constellation-agent-1
   git checkout agent-1/issue-<NUMBER>-<short-desc>  # Return to feature branch!
   ```
   - Comment on GitHub issue to claim it (use `mcp__github__add_issue_comment`)

### Pre-Flight Checklist (Ready to Code?)

Before writing any code, verify:
- [ ] In worktree? (`git rev-parse --git-dir` contains "worktrees/")
- [ ] On feature branch? (`git branch --show-current` = `agent-1/issue-...`)
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
    # Edit: agents/agent-1/QUEUE.md → Move to Completed Issues table
    git add agents/agent-1/QUEUE.md
    git commit -m "docs(agents): agent-1 completed #<NUMBER>" && git push
    ```

### If Blocked

- Update QUEUE.md with blocker note in Status column
- Comment on GitHub issue with blocker details (use `mcp__github__add_issue_comment`)
- Notify via issue comment if other agents are affected

---

## Completed Issues

| Issue | Title | PR |
|-------|-------|-----|
| #117 | [Phase 2.2] Parser Memoization | Merged |
| #116 | [Phase 2.1] IR Optimization Passes | Merged |
| #112 | [Phase 1.1] Implement Compilation Caching | Merged |
| #103 | Type Checker: Validate example value types | Merged |
| #102 | Parser: Add @example annotation parsing | Merged |
| #101 | AST: Add Annotation and ExampleAnnotation types | Merged |
| #97 | Complete documentation stub files | Merged |
| #96 | Wrap synchronous throws in IO context in DagCompiler | Merged |
| #87 | Improve lang-compiler branch coverage | Merged |
| #69 | Add tests for union type compilation and runtime | Merged |
| #70 | Add tests for lambda expression compilation | Merged |
| #72 | Add tests for string interpolation | Merged |
| #4 | Add test coverage for lang-ast module | Merged |
| #8 | Implement field access operator (`.`) | Merged |
| #11 | Implement arithmetic operators | Merged |
| #51 | Record + Record type merge | Merged |
| #54 | Record projection with `{}` syntax | Merged |
| #61 | Fix parser infinite loop | Merged |
| #52 | Candidates + Candidates element-wise merge | Merged |
| #53 | Candidates + Record broadcast merge | Merged |
| #55 | Type merge error handling | Merged |
| #46 | Design: Add InlineTransform support to DataNodeSpec | Merged |
| #47 | Design: Introduce RawValue type | Merged |
| #48 | Design: Module initialization pooling | Merged |
| #27 | Implement string interpolation | Merged |
| #24 | Implement union types | Merged |

## Notes

- **26 issues completed** - Phase 2 complete!
- Implemented IR Optimization Passes (#116): DCE, Constant Folding, CSE
- Implemented Parser Memoization (#117): P.oneOf optimization, cache infrastructure
- Awaiting new phase assignments

## Dependencies

```
#112 ✅ ──▶ #116 ✅ (IR Optimization) - COMPLETE
#117 ✅ (Parser Memoization) - COMPLETE
```

---
*Updated: 2026-01-22 (Phase 2 complete)*
