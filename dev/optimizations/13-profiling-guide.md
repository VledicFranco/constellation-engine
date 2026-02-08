# Profiling Guide

This guide explains how to measure and identify performance bottlenecks in the Constellation Engine.

---

## Principle: Measure First, Optimize Second

> "Premature optimization is the root of all evil" - Donald Knuth

Before implementing any optimization:
1. Establish a baseline measurement
2. Identify actual bottlenecks (not assumed ones)
3. Implement optimization
4. Measure again to verify improvement
5. Ensure no regressions in other areas

---

## Quick Profiling Setup

### 1. Built-in Timing

Add timing instrumentation to key code paths:

```scala
// TimingUtils.scala

import scala.collection.mutable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicLong, LongAdder}

object TimingUtils {

  case class TimingStats(
    count: Long,
    totalNanos: Long,
    minNanos: Long,
    maxNanos: Long
  ) {
    def avgMs: Double = if (count == 0) 0.0 else totalNanos / count / 1_000_000.0
    def minMs: Double = minNanos / 1_000_000.0
    def maxMs: Double = maxNanos / 1_000_000.0

    override def toString: String =
      f"count=$count, avg=${avgMs}%.2fms, min=${minMs}%.2fms, max=${maxMs}%.2fms"
  }

  private val timings = new ConcurrentHashMap[String, TimingAccumulator]()

  class TimingAccumulator {
    val count = new LongAdder()
    val total = new LongAdder()
    val min = new AtomicLong(Long.MaxValue)
    val max = new AtomicLong(Long.MinValue)

    def record(nanos: Long): Unit = {
      count.increment()
      total.add(nanos)
      min.updateAndGet(current => math.min(current, nanos))
      max.updateAndGet(current => math.max(current, nanos))
    }

    def stats: TimingStats = TimingStats(
      count.sum(), total.sum(), min.get(), max.get()
    )
  }

  def timed[A](label: String)(block: => A): A = {
    val start = System.nanoTime()
    try {
      block
    } finally {
      val elapsed = System.nanoTime() - start
      timings.computeIfAbsent(label, _ => new TimingAccumulator()).record(elapsed)
    }
  }

  def timedIO[A](label: String)(io: IO[A]): IO[A] = {
    IO.monotonic.flatMap { start =>
      io.flatMap { result =>
        IO.monotonic.map { end =>
          val elapsed = (end - start).toNanos
          timings.computeIfAbsent(label, _ => new TimingAccumulator()).record(elapsed)
          result
        }
      }
    }
  }

  def report(): String = {
    val sb = new StringBuilder("=== Timing Report ===\n")
    timings.forEach { (label, acc) =>
      sb.append(s"$label: ${acc.stats}\n")
    }
    sb.toString()
  }

  def reset(): Unit = timings.clear()
}
```

### 2. Instrument Key Paths

```scala
// LangCompiler.scala
def compile(source: String, modules: ...): Either[CompileError, DagSpec] = {
  for {
    ast     <- TimingUtils.timed("compile.parse") { parser.parse(source) }
    typed   <- TimingUtils.timed("compile.typeCheck") { typeChecker.check(ast, modules) }
    ir      <- TimingUtils.timed("compile.irGen") { irGenerator.generate(typed) }
    dagSpec <- TimingUtils.timed("compile.dagCompile") { dagCompiler.compile(ir, modules) }
  } yield dagSpec
}

// Runtime.scala
def run(dagSpec: DagSpec, inputs: ...): IO[Result] = {
  for {
    modules  <- TimingUtils.timedIO("runtime.initModules") { initModules(dagSpec) }
    table    <- TimingUtils.timedIO("runtime.initDataTable") { initDataTable(dagSpec) }
    _        <- TimingUtils.timedIO("runtime.execute") { modules.parTraverse(_.run) }
    results  <- TimingUtils.timedIO("runtime.collect") { collectResults() }
  } yield results
}
```

### 3. Report Endpoint

```scala
// ConstellationRoutes.scala
case GET -> Root / "debug" / "timing" =>
  Ok(TimingUtils.report())

case POST -> Root / "debug" / "timing" / "reset" =>
  TimingUtils.reset()
  Ok("Timing stats reset")
```

---

## JVM Profiling Tools

### 1. async-profiler (Recommended)

Low-overhead CPU and allocation profiling:

```bash
# Download async-profiler
wget https://github.com/jvm-profiling-tools/async-profiler/releases/download/v2.9/async-profiler-2.9-linux-x64.tar.gz
tar xzf async-profiler-2.9-linux-x64.tar.gz

# Profile running JVM (find PID first)
jps  # Find constellation process PID

# CPU profiling (30 seconds)
./profiler.sh -d 30 -f cpu-profile.html <PID>

# Allocation profiling
./profiler.sh -d 30 -e alloc -f alloc-profile.html <PID>

# Wall-clock profiling (includes I/O wait)
./profiler.sh -d 30 -e wall -f wall-profile.html <PID>
```

### 2. JFR (Java Flight Recorder)

Built into JDK, zero configuration:

```bash
# Start with JFR enabled
java -XX:+FlightRecorder \
     -XX:StartFlightRecording=duration=60s,filename=recording.jfr \
     -jar constellation-server.jar

# Or attach to running process
jcmd <PID> JFR.start duration=60s filename=recording.jfr

# Analyze with JDK Mission Control
jmc recording.jfr
```

### 3. VisualVM

GUI-based profiling:

```bash
# Install VisualVM
# https://visualvm.github.io/

# Start application with JMX enabled
java -Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=9010 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     -jar constellation-server.jar

# Connect VisualVM to localhost:9010
```

---

## Memory Profiling

### 1. Heap Dump Analysis

```bash
# Trigger heap dump
jmap -dump:format=b,file=heap.hprof <PID>

# Or on OutOfMemoryError (add to JVM args)
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heap.hprof

# Analyze with Eclipse MAT or VisualVM
```

### 2. GC Logging

```bash
# JDK 11+ GC logging
java -Xlog:gc*:file=gc.log:time,level,tags \
     -jar constellation-server.jar

# Analyze GC behavior
# Look for:
# - Frequent young GC (allocation pressure)
# - Long GC pauses (large heaps, full GC)
# - Memory growth (potential leak)
```

### 3. Allocation Tracking

```scala
// Track allocations in hot paths
def trackAllocations[A](label: String)(block: => A): A = {
  val runtime = Runtime.getRuntime
  runtime.gc()  // Clean slate
  val before = runtime.totalMemory() - runtime.freeMemory()

  val result = block

  runtime.gc()
  val after = runtime.totalMemory() - runtime.freeMemory()
  val allocated = after - before

  println(s"$label allocated: ${allocated / 1024}KB")
  result
}
```

---

## Benchmarking Best Practices

### 1. Use JMH for Microbenchmarks

```scala
// build.sbt
libraryDependencies += "org.openjdk.jmh" % "jmh-core" % "1.36"
libraryDependencies += "org.openjdk.jmh" % "jmh-generator-annprocess" % "1.36"

// Benchmark class
import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
class CompilationBenchmark {

  var source: String = _
  var compiler: LangCompiler = _

  @Setup
  def setup(): Unit = {
    source = "in x: Int\nout y: Int\ny = Uppercase(x)"
    compiler = new LangCompiler(...)
  }

  @Benchmark
  def benchmarkCompile(): DagSpec = {
    compiler.compile(source, modules).toOption.get
  }
}
```

### 2. Realistic Load Testing

```scala
// LoadTest.scala
import cats.effect.IO
import cats.implicits._

def loadTest(
  concurrency: Int,
  requestsPerWorker: Int,
  request: IO[Unit]
): IO[LoadTestResult] = {

  val workers = (1 to concurrency).toList.map { workerId =>
    (1 to requestsPerWorker).toList.traverse { _ =>
      IO.monotonic.flatMap { start =>
        request.attempt.flatMap { result =>
          IO.monotonic.map { end =>
            RequestResult(workerId, (end - start).toMillis, result.isRight)
          }
        }
      }
    }
  }

  workers.parSequence.map { results =>
    val flat = results.flatten
    LoadTestResult(
      totalRequests = flat.size,
      successfulRequests = flat.count(_.success),
      avgLatencyMs = flat.map(_.latencyMs).sum.toDouble / flat.size,
      p99LatencyMs = percentile(flat.map(_.latencyMs), 99),
      requestsPerSecond = flat.size * 1000.0 / flat.map(_.latencyMs).max
    )
  }
}

case class RequestResult(workerId: Int, latencyMs: Long, success: Boolean)
case class LoadTestResult(
  totalRequests: Int,
  successfulRequests: Int,
  avgLatencyMs: Double,
  p99LatencyMs: Double,
  requestsPerSecond: Double
)
```

### 3. Comparison Testing

```scala
def compareImplementations[A](
  name1: String, impl1: => A,
  name2: String, impl2: => A,
  iterations: Int = 10000
): Unit = {

  // Warmup both
  (1 to 1000).foreach { _ => impl1; impl2 }

  // Benchmark
  val times1 = (1 to iterations).map { _ =>
    val start = System.nanoTime()
    impl1
    System.nanoTime() - start
  }

  val times2 = (1 to iterations).map { _ =>
    val start = System.nanoTime()
    impl2
    System.nanoTime() - start
  }

  val avg1 = times1.sum / iterations / 1000.0
  val avg2 = times2.sum / iterations / 1000.0
  val improvement = (1 - avg2 / avg1) * 100

  println(s"$name1: ${avg1}µs avg")
  println(s"$name2: ${avg2}µs avg")
  println(f"Improvement: $improvement%.1f%%")
}
```

---

## Identifying Bottlenecks

### Decision Tree

```
Is the system slow?
├── Yes
│   ├── Is CPU usage high?
│   │   ├── Yes → Profile CPU (async-profiler -e cpu)
│   │   │   └── Look for hot methods
│   │   └── No → Profile wall-clock (async-profiler -e wall)
│   │       └── Look for I/O, locks, or waiting
│   │
│   ├── Is memory usage high?
│   │   ├── Yes → Profile allocations (async-profiler -e alloc)
│   │   │   └── Look for allocation-heavy methods
│   │   └── No → Check GC logs
│   │       └── Look for long GC pauses
│   │
│   └── Is latency variable?
│       ├── Yes → Look for GC pauses or lock contention
│       └── No → Profile specific slow operation
│
└── No → Establish baseline for future comparison
```

### Common Patterns

| Symptom | Likely Cause | Investigation |
|---------|--------------|---------------|
| High CPU, uniform | Computation-heavy code | CPU profile |
| High CPU, spiky | GC | GC logs |
| Low CPU, high latency | I/O or locks | Wall-clock profile |
| Growing memory | Leak or cache growth | Heap dump over time |
| Variable latency | GC or contention | GC logs + thread analysis |

---

## Profiling Checklist

Before optimization:

- [ ] Define what "fast enough" means (target latency/throughput)
- [ ] Create reproducible test scenario
- [ ] Establish baseline measurements
- [ ] Profile CPU usage
- [ ] Profile memory allocation
- [ ] Check GC behavior
- [ ] Identify top 3 bottlenecks

After optimization:

- [ ] Measure same scenario
- [ ] Verify improvement meets target
- [ ] Check for regressions in other areas
- [ ] Update baseline for future reference

---

## Continuous Performance Monitoring

### 1. Metrics Export

```scala
// Expose metrics via Prometheus
libraryDependencies += "io.prometheus" % "simpleclient" % "0.16.0"
libraryDependencies += "io.prometheus" % "simpleclient_hotspot" % "0.16.0"

// Track key metrics
val compilationDuration = Histogram.build()
  .name("constellation_compilation_seconds")
  .help("Time to compile a program")
  .register()

val executionDuration = Histogram.build()
  .name("constellation_execution_seconds")
  .help("Time to execute a DAG")
  .register()

// Use in code
def compile(source: String): DagSpec = {
  val timer = compilationDuration.startTimer()
  try {
    doCompile(source)
  } finally {
    timer.observeDuration()
  }
}
```

### 2. Alerting

Set up alerts for performance degradation:

```yaml
# Prometheus alert rules
groups:
  - name: constellation-performance
    rules:
      - alert: SlowCompilation
        expr: histogram_quantile(0.99, constellation_compilation_seconds) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Compilation p99 latency exceeds 500ms"

      - alert: HighErrorRate
        expr: rate(constellation_errors_total[5m]) > 0.01
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Error rate exceeds 1%"
```

---

## Related Documents

- [Quick Wins](./12-quick-wins.md) - Easy improvements after profiling
- [Compilation Caching](./01-compilation-caching.md) - Common first optimization target
- [Module Initialization Pooling](./02-module-initialization-pooling.md) - Common second optimization target
