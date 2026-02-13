<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/lang-stdlib/src/main/scala/io/constellation -->
<!-- Hash: 6f18812e5662 -->
<!-- Generated: 2026-02-13T07:57:33.902913300Z -->

# io.constellation.stdlib

## Objects

### StdLib$

/** Standard library of modules for constellation-lang.
  *
  * These modules provide common operations for pipeline orchestration. The implementation is split
  * across category files in the `categories/` directory for maintainability.
  *
  * Categories:
  *   - MathFunctions: add, subtract, multiply, divide, max, min, abs, modulo, round, negate
  *   - StringFunctions: concat, string-length, join, split, contains, trim, replace
  *   - ListFunctions: list-length, list-first, list-last, list-is-empty, list-sum, list-concat,
  *     list-contains, list-reverse
  *   - BooleanFunctions: and, or, not
  *   - ComparisonFunctions: eq-int, eq-string, gt, lt, gte, lte
  *   - UtilityFunctions: identity, log
  *   - HigherOrderFunctions: filter, map, all, any (lambda-based)
  *   - TypeConversionFunctions: to-string, to-int, to-float
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `comparisonModules` | `(): Map` |  |
| `stringSignatures` | `(): List` |  |
| `utilitySignatures` | `(): List` |  |
| `allModules` | `(): Map` | /** Get all standard library modules */ |
| `conversionSignatures` | `(): List` |  |
| `listSignatures` | `(): List` |  |
| `compiler` | `(): LangCompiler` | /** Create a LangCompiler with all standard library functions registered */ |
| `comparisonSignatures` | `(): List` |  |
| `hofSignatures` | `(): List` |  |
| `stringModules` | `(): Map` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `allSignatures` | `(): List` | /** Get all standard library function signatures */ |
| `booleanModules` | `(): Map` |  |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `booleanSignatures` | `(): List` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `conversionModules` | `(): Map` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `utilityModules` | `(): Map` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `listModules` | `(): Map` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `registerAll` | `(builder: LangCompilerBuilder): LangCompilerBuilder` | /** Register all standard library functions with a LangCompiler builder */ |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `hofModules` | `(): Map` |  |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `mathModules` | `(): Map` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `mathSignatures` | `(): List` |  |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |

<!-- END GENERATED -->
