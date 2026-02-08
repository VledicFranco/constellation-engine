<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/lang-lsp/src/main/scala/io/constellation -->
<!-- Hash: 2d69e016924c -->
<!-- Generated: 2026-02-08T06:51:01.621680600Z -->

# io.constellation.lsp.diagnostics

## Objects

### OptionsDiagnostics$

/** Diagnostics and hover support for module call options (`with` clause).
  *
  * Provides:
  *   - Semantic warnings for questionable option combinations
  *   - Errors for invalid option values
  *   - Hover documentation for option names and strategy values
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `diagnose` | `(options: ModuleCallOptions, optionsRange: Range): List[Diagnostic]` | /** Analyze module call options and return any diagnostics. |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `getHover` | `(word: String, textBeforeCursor: String): Option[Hover]` | /** Get hover information for a word at a position in a `with` clause. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

<!-- END GENERATED -->
