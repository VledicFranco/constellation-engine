<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/lang-stdlib/src/main/scala/io/constellation -->
<!-- Hash: 6f18812e5662 -->
<!-- Generated: 2026-02-12T10:55:58.105381100Z -->

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
| `stringSignatures` | `(): List` |  |
| `conversionModules` | `(): Map` |  |
| `hofModules` | `(): Map` |  |
| `mathModules` | `(): Map` |  |
| `comparisonSignatures` | `(): List` |  |
| `compiler` | `(): LangCompiler` | /** Create a LangCompiler with all standard library functions registered */ |
| `listModules` | `(): Map` |  |
| `conversionSignatures` | `(): List` |  |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `utilityModules` | `(): Map` |  |
| `registerAll` | `(builder: LangCompilerBuilder): LangCompilerBuilder` | /** Register all standard library functions with a LangCompiler builder */ |
| `booleanModules` | `(): Map` |  |
| `comparisonModules` | `(): Map` |  |
| `booleanSignatures` | `(): List` |  |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `mathSignatures` | `(): List` |  |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `utilitySignatures` | `(): List` |  |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hofSignatures` | `(): List` |  |
| `stringModules` | `(): Map` |  |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `hashCode` | `(): Int` | /** Calculates a hash code value for the object. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `listSignatures` | `(): List` |  |
| `allSignatures` | `(): List` | /** Get all standard library function signatures */ |
| `allModules` | `(): Map` | /** Get all standard library modules */ |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

<!-- END GENERATED -->
