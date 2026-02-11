# Testing Documentation

This directory contains strategic testing documentation and guidelines for Constellation Engine.

## Documents

### [`strategic-testing-guide.md`](./strategic-testing-guide.md)
**Main Reference** - Comprehensive guide based on our Phase 5-7 testing campaign experience.

**Key Topics:**
- Quality over quantity philosophy (5 tests → +20% coverage)
- What worked: P0 gap closure, strategic analysis, functional validation
- What didn't work: API assumptions, late recognition of diminishing returns
- Current coverage landscape (all modules meet thresholds)
- Future strategy: Bug-driven testing
- ROI calculation framework
- Testing workflow and checklist

**When to Use:**
- Planning a new testing phase
- Evaluating whether to add tests
- Understanding testing ROI
- Checking current coverage status

---

## Quick Reference

### Run Coverage Report
```bash
sbt clean coverage test coverageReport
open modules/<module>/target/scala-3.3.1/scoverage-report/index.html
```

### Check Existing Tests
```bash
grep -r "FeatureName" modules/<module>/src/test/
```

### Run Tests
```bash
sbt "<module>/testOnly *TestClass"
```

---

## Current Coverage Status (2026-02-10)

| Module | Coverage | Status |
|--------|----------|--------|
| core | 84.88% stmt, 100% branch | ✅ Excellent |
| runtime | 67.65% stmt, 81.47% branch | ✅ Very Good |
| lang-ast | 83.90% stmt, 100% branch | ✅ Excellent |
| lang-parser | 50.12% stmt, 92.86% branch | ✅ Very Good |
| lang-compiler | 54.25% stmt, 59% branch | ✅ Good |
| lang-stdlib | 14.21% stmt, 100% branch | ✅ Metadata focus |
| lang-lsp | 53.06% stmt, 81.43% branch | ✅ Very Good |
| lang-cli | 28.32% stmt, 31.76% branch | ✅ Good |
| http-api | 32.44% stmt, 49.01% branch | ✅ Good |
| example-app | 14.04% stmt, 36.36% branch | ✅ Demo code |

**All modules meet or exceed minimum thresholds.**

---

## When to Add Tests

✅ **Add tests for:**
- Production bugs (regression prevention)
- New features (as they're developed)
- User-facing features with <50% coverage
- Critical paths with 0% coverage

⚠️ **Don't add tests for:**
- Just to increase coverage percentage
- Areas with 500+ existing tests
- Internal implementation details
- Low-impact demo/example code

---

## ROI Benchmarks (From Our Campaign)

- **Exceptional:** >3% coverage per test (Phase 6.1: 4% per test)
- **Good:** 0.3-3% per test (Phase 6.2: 0.38% per test)
- **Medium:** 0.1-0.3% per test (Phase 5: 0.33% per test)
- **Low:** <0.1% per test (Phase 7: 0.04% per test)

**Decision Threshold:** If ROI <0.1% per test, evaluate whether functional value justifies continuing.

---

## Test Quality Checklist

- [ ] Tests pass consistently (not flaky)
- [ ] Coverage increased OR functional value clear
- [ ] Tests are focused (one concern per test)
- [ ] Not duplicating existing tests
- [ ] Fast execution (<100ms per test ideally)
- [ ] Error cases covered (not just happy path)
- [ ] Build.sbt thresholds updated (if needed)

---

## Related Documentation

- **Performance Testing:** `dev/benchmarks/` - Performance benchmarks and regression tests
- **Build Config:** `build.sbt` - Coverage thresholds (search for `coverageMinimum`)
- **CI/CD:** `.github/workflows/` - Automated testing and coverage enforcement

---

**Last Updated:** 2026-02-10
**Campaign:** Phases 5-7 Complete
**Total Tests Added:** ~55 tests, +21.43% lang-lsp branch coverage, 2 P0 gaps closed
