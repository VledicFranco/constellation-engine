research/online-pipelines/
README.md
Why “online pipelines” matter (industry reality)

Most production ML stacks split into two time domains:

Offline (training / batch): feature generation, labels, training, evaluation.

Online (inference path): request-time transforms, feature/embedding retrieval, candidate generation, ranking, business rules, fallbacks, monitoring.

Online inference paths are where teams feel the most pain because they must be:

Low-latency + high-throughput

Correct + consistent with training (avoid training/serving skew)

Observable + safe to roll out (canary/A-B/shadow)

Composable (multi-model graphs, pre/post-processing, ensembles)

This shows up clearly in what “feature platforms” and “serving platforms” emphasize:

Feature stores exist largely to make features consistent and available for both training and low-latency serving, and to reduce leakage/skew.

Serving platforms increasingly support inference graphs (chaining routers/transformers/predictors), autoscaling, and rollout strategies like canary / A-B testing.

state-of-the-art.md
1) Online features + request-time transforms (Feature Store / Feature Platform)

What’s common in industry

A dual-store model: offline store (historical) + online store (low latency) with a feature server for serving features to inference services.

Online store implementations often use Redis (or DynamoDB/Cassandra/etc.) to hit tight latency budgets.

Increasing emphasis on request-time / real-time feature computation, not only pre-materialized features. (Commercial platforms highlight this heavily.)

Key “needed most” capabilities

Point-in-time correctness + leakage avoidance for training sets (so offline pipelines and online serving agree).

Push-based materialization to online store (precompute & push) for latency reduction.

A clean path for non-Python serving via a feature server (relevant to your Scala-first runtime story).

Implication for Constellation
Constellation’s DSL already models the inference DAG: the missing “out-of-the-box” value is:

request-time transforms + joins

online feature/embedding fetch

caching + versioning + observability primitives

2) Model serving & inference graphs

What’s common in industry

Kubernetes-native “model inference platforms” provide standardized serving APIs, autoscaling, and increasingly first-class graph / pipeline composition:

KServe: highlights autoscaling, standardized inference protocol, and InferenceGraph for chaining services.

Seldon Core: explicitly promotes inference graphs composed of predictors/transformers/routers/combiners plus metrics/provenance.

Performance-critical serving patterns

Dynamic batching (combine requests to raise throughput without killing latency).

NVIDIA Triton supports dynamic batching controls like preferred batch size and max queue delay.

BentoML offers “adaptive batching” that adjusts batching windows dynamically.

Programmable serving DAGs at the serving layer:

Ray Serve positions itself as programmable, scalable online inference serving (including LLM serving features).

Implication for Constellation
If you want “inference paths out of the box,” you need:

graph composition primitives (already strong)

production concerns: batching, concurrency, timeouts, retries, fallbacks, rollout hooks, telemetry

3) The convergence: online ML + RAG/LLMOps

Feature platforms and serving stacks are expanding to cover embeddings/prompts:

Feast docs now explicitly include GenAI/RAG topics and even vector DB / on-demand views as surfaced areas.

Tecton describes building and managing features, embeddings, and prompts in one platform narrative.

Implication
Your Candidates<T> batching + record algebra is a strong base for:

retrieval + rerank + post-processing pipelines

hybrid “business logic + model logic” graphs

what-industry-needs-most.md
The recurring “pain points” (what teams pay for)

These show up across feature stores + serving platforms:

Training/Serving consistency

Avoid leakage, keep point-in-time correct training data; keep feature definitions reusable across offline + online.

Latency budgets + throughput

Online stores + caching + batching are non-negotiable.

Dynamic/adaptive batching is a standard lever in high-perf serving.

Composable inference graphs

Routers/transformers/combiners, multi-stage inference, ensembles, and fallbacks.

Safe deployment + rollout

Canary/A-B testing and traffic routing are “table stakes” in modern serving platforms.

Observability + provenance

Metrics, tracing, and “what model/version/features produced this output?” are repeatedly called out.

tooling-plan.md
Two tool levels (as you described)
A) constellation-lang tools (Data Scientist-facing)

Goal: common operations and transforms inside inference path.

Recommend a “standard library” of safe, declarative primitives:

Feature/embedding retrieval

get_features(entityKeys, featureSet, at=timestamp?)

get_embeddings(ids, store=..., version=...)

Request-time transforms

normalize, bucketize, clip, impute, one_hot, hash, text_clean

“On-demand feature view” style transforms (request + precomputed features). This mirrors how feature platforms frame real-time features.

Candidates-oriented ops (you already have element-wise merge/project)

filter, topK, groupBy, dedupe, windowAgg (bounded)

Graph control (but DS-safe)

fallback(primary, secondary)

branch(condition, then, else)

timeout(ms), retry(n) (bounded, safe defaults)

Lightweight post-processing

score calibration (sigmoid_calibrate, isotonic?)

constraints (max_per_group, diversify)

Principle: DS tools should be hard to misuse (bounded complexity, explicit cost/latency hints, default-safe).

B) Scala module tools (MLE/DE-facing)

Goal: lower-level control to “move data and manipulate complexities.”

Recommend modules aligned to real serving stacks:

Online store connectors

Redis online store patterns are common in feature serving.

Batching / scheduling

Provide a first-class batching abstraction similar to what Triton/BentoML emphasize:

dynamic batching knobs (max queue delay, preferred batch sizes)

adaptive batching strategies

RPC + protocol

gRPC/HTTP, streaming responses (useful for LLM-ish paths; Ray Serve calls this out)

Traffic shaping + rollouts

weighted routing, canary, A/B hooks (mirrors KServe/Seldon positioning)

Observability

OpenTelemetry tracing, Prometheus metrics, structured logs

Execution runtime

concurrency limits, backpressure, circuit breakers, caches (per-node + distributed)

Principle: Scala modules expose the “sharp edges” needed for production guarantees.

prioritized-opportunities.md
P0 — Highest ROI (core “online pipeline” value)

Online feature/embedding fetch + caching

A built-in abstraction for “fetch features fast” (online store + cache) is central to industry feature serving.

Request-time transforms as first-class nodes

Match the “real-time features computed at inference time” direction.

Batching controls wired to Candidates<T>

Make Candidates<T> not just a type, but a performance contract:

dynamic batching knobs (queue delay, batch sizes)

adaptive batching option

Inference graph ergonomics

Provide idiomatic constructs for “router/transformer/predictor/combiner” graph patterns the market already uses.

P1 — Production hardening (what gets you adopted)

Rollouts + traffic routing hooks (canary/A-B/shadow)

These are explicitly promoted by serving platforms.

Observability & provenance built-in

“Every node emits metrics + trace spans + lineage tags.”

Ray Serve and Seldon emphasize monitoring/metrics patterns.

Contracts & schema evolution

Versioned record schemas; runtime validation when integrating with external stores.

P2 — Differentiators (Constellation-native “wow”)

Compile-time cost/latency annotations

Let nodes declare expected latency classes (e.g., cache-hit vs cache-miss), and have the compiler produce warnings (“this path likely violates p99 30ms”).

Automatic “training/serving skew” checks

If you also model offline feature generation, you can statically detect mismatched transforms.

GenAI/RAG path primitives

Retrieval + rerank pipelines are now mainstream; feature platforms are explicitly evolving there.

notes-on-constellation-fit.md
Why Constellation’s model is a good match

Serving platforms are converging on graphs (KServe InferenceGraph, Seldon inference graphs).

Performance is increasingly won through batching and scheduling (Triton dynamic batching, BentoML adaptive batching).

Feature platforms exist to provide consistent features across offline+online with low-latency serving.

Constellation can unify these into a single coherent developer experience:

DS writes clean inference DAGs in constellation-lang

MLE/DE plugs in serious runtime behaviors via Scala modules