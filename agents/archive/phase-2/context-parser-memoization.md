# Task 2.2: Parser Memoization

**Phase:** 2 - Core Improvements
**Effort:** Medium (1 week)
**Impact:** High (2-5x parse time reduction on complex inputs)
**Dependencies:** None
**Blocks:** None

---

## Objective

Implement memoization for the cats-parse based parser to avoid redundant backtracking and achieve near-linear parse time for all inputs.

---

## Background

### Current Problem

The parser uses extensive backtracking without memoization:

```scala
// ConstellationParser.scala
lazy val expression: P[Expression] = P.defer(lambdaExpr.backtrack | exprCoalesce)
```

For input `result = someFunction(a, b)`:
1. Try `lambdaExpr` → fails after checking for `(`
2. Backtrack, try `exprCoalesce`
3. Eventually parse as function call

Each backtrack re-parses the same input positions, leading to O(n²) worst-case.

### Impact

| Input Complexity | Current | With Memoization |
|------------------|---------|------------------|
| Simple (10 lines) | 5ms | 5ms |
| Medium (100 lines) | 50ms | 15ms |
| Complex (1000 lines) | 500ms+ | 50ms |

---

## Technical Design

### Approach 1: Packrat Parsing (Recommended)

Wrap the parser with position-keyed memoization:

```scala
class MemoizedParser[A](parser: P[A]) {
  private val cache = mutable.Map[(Int, String), Either[Error, (A, Int)]]()

  def parse(input: String, offset: Int): Either[Error, (A, Int)] = {
    val key = (offset, parser.toString) // Use parser identity
    cache.getOrElseUpdate(key, parser.parse(input.substring(offset)))
  }
}
```

### Approach 2: Selective Memoization

Only memoize at high-backtrack positions:

```scala
// Memoize only these parsers (identified via profiling):
val expression = memoize(P.defer(lambdaExpr.backtrack | exprCoalesce))
val typeExpr = memoize(P.defer(unionType.backtrack | ...))
val primary = memoize(functionCall.backtrack | varRef | ...)
```

### Approach 3: Parser Restructuring

Reduce backtracking via lookahead:

```scala
// Instead of backtracking:
val primary = functionCall.backtrack | varRef

// Use lookahead to decide:
val primary = (identifier ~ P.peek(P.char('('))).flatMap {
  case (name, Some(_)) => functionCall(name)
  case (name, None) => varRef(name)
}
```

---

## Deliverables

### Required

- [ ] **Profiling analysis** - Identify hotspots in current parser
- [ ] **Memoization strategy** - Choose and implement approach
- [ ] **Integration** - Update ConstellationParser
- [ ] **Benchmark suite** - Measure improvement
- [ ] **Unit tests** - Verify correctness preserved

### Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `modules/lang-parser/src/main/scala/io/constellation/lang/parser/MemoizationSupport.scala` | **New** | Memoization utilities |
| `modules/lang-parser/src/main/scala/io/constellation/lang/parser/ConstellationParser.scala` | Modify | Apply memoization |
| `modules/lang-parser/src/test/scala/io/constellation/lang/parser/ParserBenchmark.scala` | **New** | Benchmarks |

---

## Implementation Guide

### Step 1: Profile Current Parser

```scala
// Add timing to key parsers
def timedParser[A](name: String, p: P[A]): P[A] = {
  P.caret.with1.flatMap { start =>
    val startTime = System.nanoTime()
    p.map { result =>
      val elapsed = System.nanoTime() - startTime
      println(s"$name at ${start.offset}: ${elapsed}ns")
      result
    }
  }
}
```

### Step 2: Implement Memoization

```scala
// MemoizationSupport.scala
package io.constellation.lang.parser

import cats.parse.{Parser => P, Parser0}
import scala.collection.mutable

trait MemoizationSupport {
  // Thread-local cache to avoid contention
  private val cacheHolder = new ThreadLocal[mutable.Map[Any, Any]] {
    override def initialValue() = mutable.Map.empty
  }

  protected def memoize[A](parser: P[A]): P[A] = {
    new P[A] {
      def parse(input: String): Either[P.Error, (String, A)] = {
        val cache = cacheHolder.get()
        val key = (this, input)

        cache.get(key) match {
          case Some(result) => result.asInstanceOf[Either[P.Error, (String, A)]]
          case None =>
            val result = parser.parse(input)
            cache(key) = result
            result
        }
      }
    }
  }

  /** Clear cache - call before each top-level parse */
  protected def clearMemoCache(): Unit = {
    cacheHolder.get().clear()
  }
}
```

### Step 3: Apply to Parser

```scala
// In ConstellationParser.scala
object ConstellationParser extends MemoizationSupport {

  // Apply memoization to high-backtrack parsers
  lazy val expression: P[Expression] = memoize(
    P.defer(lambdaExpr.backtrack | exprCoalesce)
  )

  lazy val typeExpr: P[TypeExpr] = memoize(
    P.defer(unionType)
  )

  def parse(source: String): Either[CompileError.ParseError, Program] = {
    clearMemoCache()  // Fresh cache for each parse
    program.parseAll(source).left.map(...)
  }
}
```

---

## Testing Strategy

```scala
class ParserMemoizationTest extends AnyFlatSpec with Matchers {

  "Memoized parser" should "produce same results as unmemoized" in {
    val testCases = loadTestCases("parser-tests/")

    testCases.foreach { source =>
      val memoized = MemoizedParser.parse(source)
      val unmemoized = UnmemoizedParser.parse(source)
      memoized shouldBe unmemoized
    }
  }

  it should "be faster on complex inputs" in {
    val complex = generateComplexInput(lines = 500)

    val memoTime = benchmark(100)(MemoizedParser.parse(complex))
    val unmemoTime = benchmark(100)(UnmemoizedParser.parse(complex))

    memoTime should be < (unmemoTime * 0.5)  // At least 2x faster
  }
}
```

---

## Web Resources

### Packrat Parsing
- [Packrat Parsing Paper](https://pdos.csail.mit.edu/~baford/packrat/icfp02/) - Original paper by Ford
- [Packrat Parsing: Simple, Powerful, Lazy, Linear Time](https://bford.info/pub/lang/packrat-icfp02.pdf)
- [PEG Parsing](https://en.wikipedia.org/wiki/Parsing_expression_grammar)

### cats-parse
- [cats-parse Documentation](https://typelevel.org/cats-parse/)
- [cats-parse Source](https://github.com/typelevel/cats-parse)
- [Parser Combinator Performance](https://www.lihaoyi.com/post/EasyParsingwithParserCombinators.html) - FastParse comparison

### Memoization Techniques
- [Memoization in Scala](https://www.scala-lang.org/api/2.13.x/scala/collection/mutable/WeakHashMap.html)
- [Thread-Local Storage](https://docs.oracle.com/javase/8/docs/api/java/lang/ThreadLocal.html)

---

## Acceptance Criteria

1. **Functional Requirements**
   - [ ] All existing parser tests pass
   - [ ] Memoization cache is cleared between parses
   - [ ] Thread-safe for concurrent parsing

2. **Performance Requirements**
   - [ ] 2x+ improvement on 500+ line programs
   - [ ] No regression on simple programs
   - [ ] Memory overhead < 10MB for typical programs

3. **Quality Requirements**
   - [ ] Benchmark suite with reproducible results
   - [ ] No test regressions

---

## Notes for Implementer

1. **Profile first** - Identify which parsers actually backtrack most.

2. **Cache key matters** - Use parser identity + position, not just position.

3. **Memory management** - Clear cache between parses to avoid leaks.

4. **Thread safety** - Use ThreadLocal or ensure external synchronization.

5. **Consider hybrid approach** - Selective memoization + restructuring may be better than full Packrat.
