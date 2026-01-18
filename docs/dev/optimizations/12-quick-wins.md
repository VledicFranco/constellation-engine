# Optimization 12: Quick Wins

**Priority:** Immediate
**Expected Gain:** Various small improvements
**Complexity:** Low
**Status:** Easy to implement

---

These are small optimizations that can be implemented quickly with minimal risk. Each provides incremental improvement and together they add up.

---

## 1. JIT Warmup for Benchmarking

**Problem:** JVM performance varies significantly before JIT compilation stabilizes.

**Solution:** Warm up the JIT before measuring:

```scala
// Warmup utility
def warmup(iterations: Int = 1000)(block: => Unit): Unit = {
  println(s"Warming up JIT ($iterations iterations)...")
  (1 to iterations).foreach(_ => block)
  System.gc()  // Clean up warmup garbage
  Thread.sleep(100)  // Let GC settle
  println("Warmup complete")
}

// Usage
warmup(1000) {
  compiler.compile(testProgram, modules)
}

// Now benchmark
val result = benchmark {
  compiler.compile(testProgram, modules)
}
```

**Impact:** More accurate benchmarks, identifies true performance baseline.

---

## 2. String Interning for Module Names

**Problem:** Module names are compared frequently, creating string allocation overhead.

**Solution:** Intern common strings:

```scala
// ModuleBuilder.scala
def metadata(name: String, description: String, major: Int, minor: Int): ModuleBuilder[Unit, Unit] = {
  ModuleBuilder(ModuleSpec(
    name = name.intern(),  // Intern the name
    description = description,
    version = Version(major, minor)
  ))
}

// ModuleRegistryImpl.scala
def getModule(name: String): IO[Option[Module.Uninitialized]] = IO.pure {
  modules.get(name.intern())  // Intern for faster lookup
}
```

**Impact:** Faster string comparisons, reduced memory for duplicate names.

---

## 3. Lazy Logger Initialization

**Problem:** Logger creation happens even when logging is disabled.

**Solution:** Use lazy vals and check log level:

```scala
// Before
class Runtime {
  private val logger = LoggerFactory.getLogger(getClass)

  def run(...) = {
    logger.debug(s"Starting execution with ${modules.size} modules")  // Always evaluates
  }
}

// After
class Runtime {
  private lazy val logger = LoggerFactory.getLogger(getClass)

  def run(...) = {
    if (logger.isDebugEnabled) {
      logger.debug(s"Starting execution with ${modules.size} modules")
    }
  }
}

// Or use macro-based logging
import com.typesafe.scalalogging.LazyLogging

class Runtime extends LazyLogging {
  def run(...) = {
    logger.debug(s"Starting execution with ${modules.size} modules")  // Macro checks level
  }
}
```

**Impact:** Eliminates string interpolation overhead when logging is disabled.

---

## 4. Pre-size Collections

**Problem:** Collections grow dynamically, causing reallocations.

**Solution:** Pre-size when capacity is known:

```scala
// Before
def initDataTable(dagSpec: DagSpec): MutableDataTable = {
  val map = mutable.Map.empty[UUID, Deferred[IO, Any]]  // Default size
  dagSpec.dataNodes.foreach(node => map.put(node.id, ...))
  map
}

// After
def initDataTable(dagSpec: DagSpec): MutableDataTable = {
  val expectedSize = dagSpec.dataNodes.size
  val map = new java.util.HashMap[UUID, Deferred[IO, Any]](
    (expectedSize * 1.3).toInt  // Account for load factor
  )
  dagSpec.dataNodes.foreach(node => map.put(node.id, ...))
  map.asScala
}
```

**Impact:** Eliminates resize operations, better cache locality.

---

## 5. Avoid Repeated Pattern Compilation

**Problem:** Regex patterns compiled on every use.

**Solution:** Compile once, reuse:

```scala
// Before
def stripPrefix(name: String): String = {
  name.split("\\.").lastOption.getOrElse(name)  // split creates Pattern
}

// After
object NameUtils {
  private val DotPattern = "\\.".r

  def stripPrefix(name: String): String = {
    DotPattern.split(name).lastOption.getOrElse(name)
  }
}
```

**Impact:** Eliminates regex compilation overhead.

---

## 6. Use Value Classes for IDs

**Problem:** UUID boxing creates allocation overhead.

**Solution:** Use value classes where possible:

```scala
// Value class wraps without allocation (in most cases)
case class DataNodeId(value: UUID) extends AnyVal
case class ModuleNodeId(value: UUID) extends AnyVal

// Type safety without runtime overhead
def getDataNode(id: DataNodeId): Option[DataNodeSpec] = ...
def getModuleNode(id: ModuleNodeId): Option[ModuleNodeSpec] = ...
```

**Impact:** Type safety with reduced allocation in hot paths.

---

## 7. Fast Path for Common Cases

**Problem:** Generic code handles all cases equally.

**Solution:** Add fast paths for common cases:

```scala
// JsonCValueConverter.scala
def jsonToCValue(json: Json, expectedType: CType): Either[Error, CValue] = {
  // Fast path for primitives (most common)
  (json, expectedType) match {
    case (Json.Str(s), CString) => Right(CStringValue(s))
    case (Json.Num(n), CInt) if n.toInt.isDefined =>
      Right(CIntValue(n.toInt.get))
    case (Json.True, CBoolean) => Right(CBooleanValue(true))
    case (Json.False, CBoolean) => Right(CBooleanValue(false))

    // Slower path for complex types
    case _ => jsonToCValueComplex(json, expectedType)
  }
}
```

**Impact:** Faster handling of common primitive conversions.

---

## 8. Batch IO Operations

**Problem:** Multiple small IOs create overhead.

**Solution:** Batch related operations:

```scala
// Before: N separate IOs
def initModules(specs: List[ModuleSpec]): IO[List[Module]] = {
  specs.traverse(spec => registry.getModule(spec.name))  // N lookups
}

// After: Batch lookup
def initModules(specs: List[ModuleSpec]): IO[List[Module]] = {
  val names = specs.map(_.name)
  registry.getModules(names)  // Single batch lookup
}

// In ModuleRegistry
def getModules(names: List[String]): IO[List[Option[Module]]] = IO.pure {
  names.map(modules.get)  // Single synchronized access
}
```

**Impact:** Reduced IO overhead for bulk operations.

---

## 9. Avoid Unnecessary Copies

**Problem:** Defensive copies create overhead.

**Solution:** Use views and avoid copies when safe:

```scala
// Before: creates new list
def getOutputNodes(dagSpec: DagSpec): List[DataNodeSpec] = {
  dagSpec.dataNodes.filter(_.name.startsWith("out_")).toList
}

// After: lazy view
def getOutputNodes(dagSpec: DagSpec): Iterable[DataNodeSpec] = {
  dagSpec.dataNodes.view.filter(_.name.startsWith("out_"))
}

// Or if you need List, use to(List) only at the end
def getOutputNodesList(dagSpec: DagSpec): List[DataNodeSpec] = {
  dagSpec.dataNodes.iterator.filter(_.name.startsWith("out_")).toList
}
```

**Impact:** Reduced intermediate collection allocation.

---

## 10. Profile-Guided Optimization Targets

Add timing instrumentation to identify actual bottlenecks:

```scala
// Simple timing utility
def timed[A](label: String)(block: => A): A = {
  val start = System.nanoTime()
  val result = block
  val elapsed = (System.nanoTime() - start) / 1_000_000.0
  println(f"$label: $elapsed%.2fms")
  result
}

// Usage
def compile(source: String): DagSpec = {
  timed("parse") { parser.parse(source) }
  timed("typeCheck") { typeChecker.check(ast) }
  timed("irGen") { irGenerator.generate(typed) }
  timed("dagCompile") { dagCompiler.compile(ir) }
}
```

**Impact:** Identifies where to focus optimization effort.

---

## Implementation Priority

| Quick Win | Effort | Impact | Implement When |
|-----------|--------|--------|----------------|
| JIT Warmup | 5 min | Benchmarking | Before any benchmarking |
| Pre-size Collections | 10 min | Medium | During any collection work |
| Fast Path Primitives | 15 min | High | JSON conversion updates |
| String Interning | 10 min | Low-Medium | Module registry work |
| Lazy Logging | 10 min | Low | Logging updates |
| Batch IO | 20 min | Medium | Module initialization |
| Avoid Copies | 15 min | Medium | Hot path optimization |
| Profile Instrumentation | 15 min | Diagnostic | Before major optimization |

---

## Validation

After implementing quick wins, verify improvements:

```scala
// Before/after comparison
val baseline = benchmark(iterations = 1000) {
  oldImplementation()
}

val optimized = benchmark(iterations = 1000) {
  newImplementation()
}

println(s"Improvement: ${(1 - optimized / baseline) * 100}%")
```

---

## Related Documents

- [Profiling Guide](./13-profiling-guide.md) - How to measure performance
- [Compilation Caching](./01-compilation-caching.md) - Major optimization for repeat compilations
