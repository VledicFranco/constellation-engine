<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/lang-stdlib/src/main/scala/io/constellation -->
<!-- Hash: 1ae78b57c966 -->
<!-- Generated: 2026-02-06T18:40:29.333629700Z -->

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
| `mathModules` | `(): Map` |  |
| `compiler` | `(): LangCompiler` | /** Create a LangCompiler with all standard library functions registered */ |
| `stringModules` | `(): Map` |  |
| `utilityModules` | `(): Map` |  |
| `registerAll` | `(builder: LangCompilerBuilder): LangCompilerBuilder` | /** Register all standard library functions with a LangCompiler builder */ |
| `stringSignatures` | `(): List` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `allModules` | `(): Map` | /** Get all standard library modules */ |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `booleanModules` | `(): Map` |  |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `conversionModules` | `(): Map` |  |
| `listModules` | `(): Map` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `listSignatures` | `(): List` |  |
| `conversionSignatures` | `(): List` |  |
| `utilitySignatures` | `(): List` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `mathSignatures` | `(): List` |  |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `hofSignatures` | `(): List` |  |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `hofModules` | `(): Map` |  |
| `allSignatures` | `(): List` | /** Get all standard library function signatures */ |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `comparisonModules` | `(): Map` |  |
| `comparisonSignatures` | `(): List` |  |
| `booleanSignatures` | `(): List` |  |

<!-- END GENERATED -->
