<!-- GENERATED: Do not edit manually -->
<!-- Source: modules/http-api/src/main/scala/io/constellation -->
<!-- Hash: 57f4504e5d93 -->
<!-- Generated: 2026-02-10T06:41:44.760743900Z -->

# io.constellation.http.examples

## Objects

### DemoServer$

/** Demo HTTP server with standard library functions pre-loaded
  *
  * This example demonstrates how to start a Constellation HTTP API server with the standard library
  * functions available for use in constellation-lang pipelines.
  *
  * Once started, you can:
  *   - Compile constellation-lang pipelines: POST /compile
  *   - Execute compiled DAGs: POST /execute
  *   - List available DAGs: GET /dags
  *   - List available modules: GET /modules
  *   - Check server health: GET /health
  *
  * Example curl commands:
  * {{{
  * # Health check
  * curl http://localhost:8080/health
  *
  * # Compile a pipeline
  * curl -X POST http://localhost:8080/compile \
  *   -H "Content-Type: application/json" \
  *   -d '{
  *     "source": "in a: Int\nin b: Int\nresult = add(a, b)\nout result",
  *     "dagName": "addition-dag"
  *   }'
  *
  * # List available modules
  * curl http://localhost:8080/modules
  *
  * # List available DAGs
  * curl http://localhost:8080/dags
  * }}}
  */

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| `run` | `(): IO` |  |
| `run` | `(args: List[String]): IO[ExitCode]` |  |
| `==` | `(x$0: Any): Boolean` | /** Test two objects for equality. |
| `equals` | `(x$0: Any): Boolean` | /** Compares the receiver object (`this`) with the argument object (`that`) for equivalence. |
| `wait` | `(x$0: Long, x$1: Int): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-int-]] |
| `wait` | `(x$0: Long): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait-long-]]. |
| `wait` | `(): Unit` | /** See [[https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html#wait--]]. |
| `toString` | `(): String` | /** Returns a string representation of the object. |
| `getClass` | `[X0](): Class[Any]` | /** Returns the runtime class representation of the object. |
| `!=` | `(x$0: Any): Boolean` | /** Test two objects for inequality. |
| `notifyAll` | `(): Unit` | /** Wakes up all threads that are waiting on the receiver object's monitor. |
| `notify` | `(): Unit` | /** Wakes up a single thread that is waiting on the receiver object's monitor. |
| `eq` | `(x$0: Object): Boolean` | /** Tests whether the argument (`that`) is a reference to the receiver object (`this`). |
| `main` | `(args: Array[String]): Unit` |  |
| `hashCode` | `(): Int` | /** Calculate a hash code value for the object. |
| `asInstanceOf` | `[X0](): Any` | /** Cast the receiver object to be of type `T0`. |
| `isInstanceOf` | `[X0](): Boolean` | /** Test whether the dynamic type of the receiver object is `T0`. |
| `synchronized` | `[X0](x$0: X0): Any` | /** Executes the code in `body` with an exclusive lock on `this`. |
| `ne` | `(x$0: Object): Boolean` | /** Equivalent to `!(this eq that)`. |
| `##` | `(): Int` | /** Equivalent to `x.hashCode` except for boxed numeric types and `null`. |

<!-- END GENERATED -->
