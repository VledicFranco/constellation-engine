# RFC-034 Phase 2: Persistent Streaming & Adaptive Integration
## Persistent bidirectional gRPC streams with runtime metrics

**Status:** 🚀 In Design (Ready for Implementation)
**Target:** 10-20% additional throughput gain over Phase 1B
**Key Focus:** Runtime metrics collection, adaptive batch sizing, StreamCompiler integration

---

## Phase 2 Objectives

### Primary Goals

1. **StreamCompiler Integration**
   - Integrate Phase 1B `BatchSplitter` into `StreamCompiler.wire()`
   - Apply `BatchRoutingDecision` for batch function selection
   - Respect `StreamOptions.maxBatchSize`, `adaptiveBatching`, `batchRouting`

2. **Runtime Metrics Collection**
   - Measure batch execution time in `StreamRoutes`
   - Record batch sizes and throughput
   - Feed metrics back to `AdaptiveBatchConfig` for next batch

3. **Persistent Streaming (Optional for Phase 2)**
   - Evaluate bidirectional gRPC streaming benefits
   - Measure connection reuse savings (10-20% projected)
   - Design streaming protocol if beneficial

4. **Performance Validation**
   - Benchmark Phase 1B integrated performance
   - Validate 10-20% additional gain from persistent connections
   - Document baseline → Phase 2 improvements

---

## Implementation Roadmap

### Task 1: StreamCompiler.wire() Integration
**Scope:** Integrate Phase 1B batch splitting into compilation pipeline

**Changes Required:**
- Modify `StreamCompiler.wire()` to check `StreamOptions.maxBatchSize`
- Apply `BatchSplitter.splitBatch()` to batch function inputs
- Use `BatchRoutingDecision.decideBatchRouting()` for routing logic
- Thread `BatchHistory` through execution for metrics

**Files:**
- `modules/stream/src/main/scala/io/constellation/stream/StreamCompiler.scala`

**Tests:**
- Batch splitting in compiled DAGs
- Routing decisions at compile time
- Integration with existing batch functions

**Deliverables:**
- StreamCompiler respects Phase 1B options
- All existing tests still passing
- New integration tests validating constraint compliance

---

### Task 2: Runtime Metrics Collection
**Scope:** Measure throughput and collect history for adaptive optimization

**Changes Required:**
- Track batch execution time in `StreamRoutes` execution handler
- Record to `BatchHistory` after each batch
- Calculate `AdaptiveBatchConfig` recommendation
- Apply sizing to subsequent batches

**Files:**
- `modules/stream/src/main/scala/io/constellation/stream/StreamRoutes.scala`
- `modules/stream/src/main/scala/io/constellation/stream/BatchSplitter.scala` (extend)

**Metrics to Track:**
```scala
case class StreamMetrics(
  batchesProcessed: Long,
  elementsProcessed: Long,
  totalTimeMs: Double,
  observedThroughputPerSec: Double,
  avgBatchSize: Double,
  adaptiveSizingAdjustments: Int
)
```

**Tests:**
- Metrics recorded per batch
- Throughput calculated correctly
- History trim after 100 entries
- Adaptive sizing adjustments applied

**Deliverables:**
- Runtime metrics exported (logs, monitoring hooks)
- History tracked and persisted per stream
- Adaptive sizing feedback loop operational

---

### Task 3: Persistent Streaming (Phase 2 Optional)
**Scope:** Evaluate and potentially implement bidirectional gRPC streams

**Rationale:**
- Eliminate per-call connection setup (~1-2% savings estimated)
- Reduce HTTP/2 framing overhead
- Keep connection warm for bursty traffic

**Potential Benefit:**
- Baseline: 747 ops/sec
- Phase 1B: 5,000+ elem/sec (batching)
- Phase 2: 5,500-6,000 elem/sec (+10-20% from persistent connections)

**Implementation Notes:**
- Design bidirectional stream protocol
- Handle client streaming, server streaming, bidirectional
- Implement connection pooling/reuse
- Benchmark improvement before/after

**Files to Create/Modify:**
- `modules/stream/src/main/scala/io/constellation/stream/StreamConnection.scala` (new)
- `modules/stream/src/main/scala/io/constellation/stream/StreamRoutes.scala` (modify)
- Provider SDK: update gRPC service definition

**Tests:**
- Stream connection lifecycle
- Data integrity across streams
- Connection reuse benefits
- Fallback to non-streaming

**Deliverables:**
- Stream protocol designed
- Connection pooling implemented
- Performance validated (10-20% improvement)
- Fallback for non-streaming clients

---

## Success Criteria

### Testing
- [ ] All Phase 1B tests still passing (75/75)
- [ ] Full suite still passing (1892/1892)
- [ ] Phase 2 integration tests added (20+ new tests)
- [ ] Performance benchmarks validating improvements
- [ ] Zero regressions in any module

### Performance
- [ ] StreamCompiler integration with zero overhead
- [ ] Runtime metrics collection <1% overhead
- [ ] Adaptive sizing active and responsive
- [ ] Persistent streaming +10-20% throughput (if implemented)
- [ ] Baseline → Phase 2: 747 ops/sec → 5,500+ elem/sec

### Documentation
- [ ] Algorithm specifications for new components
- [ ] Performance reports comparing Phase 0 → Phase 1B → Phase 2
- [ ] Integration guide for Phase 2 features
- [ ] Known limitations and trade-offs documented

---

## Phase 2 Structure

```
modules/stream/src/main/scala/io/constellation/stream/
├── StreamCompiler.scala          (modify: integrate Phase 1B)
├── StreamRoutes.scala            (modify: add metrics, adaptive sizing)
├── StreamOptions.scala           (no change, Phase 1B ready)
├── BatchSplitter.scala           (extend: metrics integration)
└── StreamMetrics.scala           (new: metrics data structures)

modules/stream/src/test/scala/io/constellation/stream/
├── Phase2StreamCompilerIntegrationTest.scala   (new)
├── Phase2MetricsCollectionTest.scala           (new)
├── Phase2AdaptiveSizingTest.scala              (new)
└── Phase2PersistentStreamingTest.scala         (new, if Phase 2.5)

docs/
└── RFC-034-Phase2-StreamingIntegration.md      (this file)

constellation-engine/tmp/
└── RFC-034-Phase2-Results.md                   (results after implementation)
```

---

## Known Risks & Mitigations

### Risk 1: StreamCompiler changes break existing behavior
**Mitigation:** Keep all Phase 1B options opt-in (default: disabled)

### Risk 2: Metrics collection adds latency
**Mitigation:** Use non-blocking metrics (atomic counters, local state)

### Risk 3: Persistent streaming incompatible with existing modules
**Mitigation:** Implement as optional transport layer with graceful fallback

### Risk 4: Adaptive sizing oscillates under variable load
**Mitigation:** Exponential moving average for stability, configurable window size

---

## Timeline & Effort

| Task | Complexity | Estimated Hours | Priority |
|------|-----------|-----------------|----------|
| Task 1: StreamCompiler Integration | Medium | 4-6 hours | P0 |
| Task 2: Runtime Metrics | Medium | 4-6 hours | P0 |
| Task 3: Persistent Streaming | High | 6-8 hours | P2 |
| Testing & Validation | Medium | 4-6 hours | P0 |
| Documentation & Reports | Low | 2-3 hours | P1 |
| **Total** | | **20-29 hours** | |

**Phase 2A (MVP):** Tasks 1-2 + Testing (12-18 hours)
**Phase 2B (Full):** Tasks 1-3 + Testing + Docs (20-29 hours)

---

## Git History & Branches

**Phase 1B Completion:**
```
master: 1e34248 test: RFC-034 Phase 1B — Add performance comparison tests
```

**Phase 2 Development:**
```
feat/034-phase2-streaming: [new commits here]
```

---

## Resources & References

- **Phase 1B Report:** `constellation-engine/tmp/RFC-034-Phase1B-PerformanceReport.md`
- **Baseline Metrics:** `constellation-engine/tmp/RFC-034-Phase0-Baseline.md`
- **BatchSplitter:** `modules/stream/src/main/scala/io/constellation/stream/BatchSplitter.scala`
- **StreamOptions:** `modules/stream/src/main/scala/io/constellation/stream/StreamOptions.scala`

---

**Created:** February 22, 2026
**Status:** 🚀 Ready for Implementation
**Next Steps:** Create Task #1 PR for StreamCompiler integration

