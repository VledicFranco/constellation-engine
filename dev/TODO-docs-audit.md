# Documentation Audit TODO

> Generated: 2026-02-05
> Status: Pending fixes

---

## Critical Issues (Priority 1)

### 1. Incorrect stdlib behavior in docs/

**File:** `docs/stdlib.md`

**Problem:** Documentation says divide/modulo return 0 on division by zero, but they actually raise `ArithmeticException`.

**Current (wrong):**
```markdown
| `divide` | `(a: Int, b: Int) -> Int` | Integer division (returns 0 if b is 0) |
| `modulo` | `(a: Int, b: Int) -> Int` | Remainder (returns 0 if b is 0) |
```

**Should be:**
```markdown
| `divide` | `(a: Int, b: Int) -> Int` | Integer division (raises ArithmeticException if b is 0) |
| `modulo` | `(a: Int, b: Int) -> Int` | Remainder (raises ArithmeticException if b is 0) |
```

---

### 2. Outdated version numbers

**Files to update from `0.3.1` to `0.4.0`:**
- `docs/_archive/LEGACY-README.md`
- `website/docs/getting-started/embedding-guide.md`

---

### 3. Missing environment variable

**File:** `docs/tooling/dashboard.md`

**Add to configuration table:**
```markdown
| `CONSTELLATION_DASHBOARD_ENABLED` | `true` | Enable/disable dashboard endpoints |
```

---

## Content Gaps (Priority 2)

### Types documentation

**docs/ version:** `docs/language/types/` - 6 files, basic coverage

**website/ version:** `website/docs/language/types.md` - 641 lines with:
- Subtyping rules (width, depth)
- Type inference (bidirectional)
- Type compatibility matrix
- Common type errors and fixes

**Action:** Consider backporting expanded content to docs/language/types/

---

### Stdlib documentation

**docs/ version:** `docs/stdlib/` - 6 files, basic coverage

**website/ version:** `website/docs/api-reference/stdlib.md` - 840 lines with:
- Edge cases for each function
- Error behavior tables
- Performance complexity notes
- Truth tables for boolean ops
- Safe patterns

**Action:** Consider backporting edge cases and error tables to docs/stdlib/

---

## Redundancy (Priority 3)

These files are nearly identical in both locations - consider deduplication:

| docs/ | website/docs/ |
|-------|---------------|
| (archived) embedding-guide.md | getting-started/embedding-guide.md |
| (archived) security.md | architecture/security-model.md |
| integrations/spi/* | integrations/* |
| (archived) error-reference.md | api-reference/error-reference.md |

**Recommendation:** The new docs/ structure is LLM-optimized; website/docs/ is human-optimized. Keep both but ensure content consistency.

---

## Website-Only Content (No action needed)

These exist only in website/docs/ and are appropriate there:
- `cookbook/*` (24 recipe files)
- `resources/roadmap.md`
- `resources/contributing.md`
- `modules/cache-memcached.md`

---

## Cross-Reference Improvements

> **Note:** docs/STRUCTURE.md was removed during the feature-driven restructure.
> The relationship to website/docs/ is now documented in docs/README.md.

---

## Completion Checklist

- [ ] Fix stdlib divide/modulo error
- [ ] Update version numbers to 0.4.0
- [ ] Add CONSTELLATION_DASHBOARD_ENABLED to docs/tooling/dashboard.md
- [ ] Review types content for backporting
- [ ] Review stdlib content for backporting
- [x] Add cross-reference note to docs/README.md
