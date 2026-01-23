# Task 1.2: Debounced LSP Analysis

**Phase:** 1 - Quick Wins
**Effort:** Low (1-2 days)
**Impact:** Medium (Better UX, reduced CPU usage)
**Dependencies:** None
**Blocks:** Task 2.3 (Semantic Tokens)

---

## Objective

Implement debouncing for LSP document change events to avoid triggering redundant compilations during rapid typing.

---

## Background

### Current Behavior

Every document change triggers a full validation:

```scala
// ConstellationLanguageServer.scala - line 1149
def handleDidChange(params: DidChangeTextDocumentParams): IO[Unit] = {
  // ... update document state ...
  validateDocument(uri)  // Called on EVERY change
}

private def validateDocument(uri: String): IO[Unit] = {
  // Full compilation happens here
  compiler.compile(document.text, "validation")
  // ...
}
```

**Problem:** During rapid typing, each keystroke triggers a full compilation. For a 100ms compilation, typing "hello" triggers 5 compilations taking 500ms total.

### Impact

1. **CPU waste:** 10-20x more compilations than needed
2. **UI lag:** Diagnostics flicker as they update constantly
3. **Battery drain:** Important for laptop users

---

## Technical Design

### Debouncer Pattern

A debouncer delays execution until a "quiet period" with no new events:

```
User types: h...e...l...l...o
Events:     │   │   │   │   │
            ▼   ▼   ▼   ▼   ▼
            [cancel previous, restart timer]
                            │
                            │ 200ms delay
                            ▼
                        [validate]
```

### Implementation

```scala
class Debouncer[K] private (
  pending: Ref[IO, Map[K, Fiber[IO, Throwable, Unit]]],
  delay: FiniteDuration
) {
  def debounce(key: K)(action: IO[Unit]): IO[Unit] = {
    pending.modify { map =>
      // Cancel existing pending action for this key
      val cancelOld = map.get(key).traverse_(_.cancel)

      // Start new delayed action
      val newFiber = (IO.sleep(delay) *> action).start

      // Return updated map and the operations to run
      (map, cancelOld *> newFiber.flatMap(f => pending.update(_ + (key -> f))))
    }.flatten
  }
}

object Debouncer {
  def create[K](delay: FiniteDuration): IO[Debouncer[K]] = {
    Ref.of[IO, Map[K, Fiber[IO, Throwable, Unit]]](Map.empty)
      .map(new Debouncer(_, delay))
  }
}
```

### Integration

```scala
// In ConstellationLanguageServer
class ConstellationLanguageServer(
  // ... existing params ...
  debouncer: Debouncer[String]  // Key = document URI
) {

  def handleDidChange(params: DidChangeTextDocumentParams): IO[Unit] = {
    val uri = params.getTextDocument.getUri
    // ... update document state ...

    // Debounced validation
    debouncer.debounce(uri)(validateDocument(uri))
  }
}
```

---

## Deliverables

### Required

- [ ] **`Debouncer.scala`** - Generic debouncer implementation:
  - Configurable delay (default 200ms)
  - Key-based debouncing (different documents debounce independently)
  - Proper fiber cancellation
  - Thread-safe

- [ ] **Integration with LSP**:
  - Add debouncer to `ConstellationLanguageServer`
  - Wire up in `handleDidChange`
  - Configurable delay via settings

- [ ] **Unit Tests**:
  - Rapid calls only trigger one action
  - Different keys debounce independently
  - Cancellation works correctly
  - Action executes after delay

### Optional Enhancements

- [ ] Immediate validation on document save (bypass debounce)
- [ ] Adaptive delay based on compilation time
- [ ] Metrics for debounce effectiveness

---

## Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `modules/lang-lsp/src/main/scala/io/constellation/lsp/Debouncer.scala` | **New** | Debouncer implementation |
| `modules/lang-lsp/src/main/scala/io/constellation/lsp/ConstellationLanguageServer.scala` | Modify | Add debouncing |
| `modules/lang-lsp/src/test/scala/io/constellation/lsp/DebouncerTest.scala` | **New** | Tests |

---

## Implementation Guide

> **Overview:** 3 steps | ~3 new files | Estimated 1-2 days

### Step 1: Create Debouncer

```scala
// Debouncer.scala
package io.constellation.lsp

import cats.effect.{IO, Ref, Fiber}
import cats.syntax.all._
import scala.concurrent.duration._

/**
 * A debouncer that delays action execution until a quiet period.
 * Multiple rapid calls with the same key will cancel previous pending
 * actions and restart the timer.
 *
 * @tparam K The type of key used to identify debounce contexts
 */
class Debouncer[K] private (
  pending: Ref[IO, Map[K, Fiber[IO, Throwable, Unit]]],
  delay: FiniteDuration
) {

  /**
   * Schedule an action to run after the delay, canceling any
   * previously scheduled action for the same key.
   */
  def debounce(key: K)(action: IO[Unit]): IO[Unit] = {
    for {
      // Cancel existing pending action
      oldFiber <- pending.modify { map =>
        (map - key, map.get(key))
      }
      _ <- oldFiber.traverse_(_.cancel)

      // Start new delayed action
      fiber <- (IO.sleep(delay) *> action *> cleanup(key)).start
      _ <- pending.update(_ + (key -> fiber))
    } yield ()
  }

  /**
   * Execute an action immediately without debouncing, canceling
   * any pending debounced action for the same key.
   */
  def immediate(key: K)(action: IO[Unit]): IO[Unit] = {
    for {
      oldFiber <- pending.modify { map =>
        (map - key, map.get(key))
      }
      _ <- oldFiber.traverse_(_.cancel)
      _ <- action
    } yield ()
  }

  /**
   * Cancel any pending action for the given key.
   */
  def cancel(key: K): IO[Unit] = {
    pending.modify { map =>
      (map - key, map.get(key))
    }.flatMap(_.traverse_(_.cancel))
  }

  /**
   * Cancel all pending actions.
   */
  def cancelAll: IO[Unit] = {
    pending.modify(map => (Map.empty, map.values.toList))
      .flatMap(_.traverse_(_.cancel))
  }

  private def cleanup(key: K): IO[Unit] = {
    pending.update(_ - key)
  }
}

object Debouncer {
  val DefaultDelay: FiniteDuration = 200.millis

  def create[K](delay: FiniteDuration = DefaultDelay): IO[Debouncer[K]] = {
    Ref.of[IO, Map[K, Fiber[IO, Throwable, Unit]]](Map.empty)
      .map(new Debouncer(_, delay))
  }
}
```

### Step 2: Integrate with LSP Server

```scala
// In ConstellationLanguageServer.scala

object ConstellationLanguageServer {
  // Add to create method
  def create(
    constellation: Constellation,
    compiler: LangCompiler,
    debounceDelay: FiniteDuration = Debouncer.DefaultDelay
  ): IO[ConstellationLanguageServer] = {
    for {
      documentManager <- DocumentManager.create
      debouncer <- Debouncer.create[String](debounceDelay)
      debugSessionManager <- DebugSessionManager.create
    } yield new ConstellationLanguageServer(
      constellation,
      compiler,
      documentManager,
      debouncer,
      debugSessionManager
    )
  }
}

class ConstellationLanguageServer(
  constellation: Constellation,
  compiler: LangCompiler,
  documentManager: DocumentManager,
  debouncer: Debouncer[String],  // NEW
  debugSessionManager: DebugSessionManager
) {

  // Modify handleDidChange
  def handleDidChange(params: DidChangeTextDocumentParams): IO[Unit] = {
    val uri = params.getTextDocument.getUri
    val version = params.getTextDocument.getVersion
    val changes = params.getContentChanges.asScala.toList

    for {
      _ <- documentManager.applyChanges(uri, version, changes)
      // Use debounced validation
      _ <- debouncer.debounce(uri)(validateDocument(uri))
    } yield ()
  }

  // Add immediate validation for save
  def handleDidSave(params: DidSaveTextDocumentParams): IO[Unit] = {
    val uri = params.getTextDocument.getUri
    // Immediate validation on save (bypass debounce)
    debouncer.immediate(uri)(validateDocument(uri))
  }

  // Modify handleDidClose
  def handleDidClose(params: DidCloseTextDocumentParams): IO[Unit] = {
    val uri = params.getTextDocument.getUri
    for {
      _ <- debouncer.cancel(uri)  // Cancel pending validation
      _ <- documentManager.closeDocument(uri)
    } yield ()
  }
}
```

### Step 3: Configuration

Allow configuring the debounce delay:

```scala
// In LspConfig or as initialization option
case class LspConfig(
  debounceDelay: FiniteDuration = 200.millis,
  // ... other config
)

// Apply during initialization
def handleInitialize(params: InitializeParams): IO[InitializeResult] = {
  // Parse custom initialization options if provided
  val config = parseConfig(params.getInitializationOptions)
  // Store or apply config...
}
```

---

## Testing Strategy

### Unit Tests

```scala
class DebouncerTest extends CatsEffectSuite {
  import scala.concurrent.duration._

  test("debounce should delay execution") {
    for {
      debouncer <- Debouncer.create[String](100.millis)
      counter <- Ref.of[IO, Int](0)

      _ <- debouncer.debounce("key")(counter.update(_ + 1))
      beforeDelay <- counter.get
      _ <- IO.sleep(150.millis)
      afterDelay <- counter.get
    } yield {
      assertEquals(beforeDelay, 0)  // Not executed yet
      assertEquals(afterDelay, 1)   // Executed after delay
    }
  }

  test("rapid calls should only execute once") {
    for {
      debouncer <- Debouncer.create[String](100.millis)
      counter <- Ref.of[IO, Int](0)

      // Rapid calls
      _ <- debouncer.debounce("key")(counter.update(_ + 1))
      _ <- IO.sleep(50.millis)
      _ <- debouncer.debounce("key")(counter.update(_ + 1))
      _ <- IO.sleep(50.millis)
      _ <- debouncer.debounce("key")(counter.update(_ + 1))

      // Wait for final execution
      _ <- IO.sleep(150.millis)
      count <- counter.get
    } yield assertEquals(count, 1)  // Only one execution
  }

  test("different keys debounce independently") {
    for {
      debouncer <- Debouncer.create[String](100.millis)
      counter <- Ref.of[IO, Int](0)

      _ <- debouncer.debounce("key1")(counter.update(_ + 1))
      _ <- debouncer.debounce("key2")(counter.update(_ + 1))

      _ <- IO.sleep(150.millis)
      count <- counter.get
    } yield assertEquals(count, 2)  // Both executed
  }

  test("immediate should bypass debounce") {
    for {
      debouncer <- Debouncer.create[String](100.millis)
      counter <- Ref.of[IO, Int](0)

      _ <- debouncer.debounce("key")(counter.update(_ + 1))
      _ <- IO.sleep(50.millis)
      _ <- debouncer.immediate("key")(counter.update(_ + 10))

      beforeDelay <- counter.get
      _ <- IO.sleep(100.millis)
      afterDelay <- counter.get
    } yield {
      assertEquals(beforeDelay, 10)  // Immediate executed
      assertEquals(afterDelay, 10)   // Debounced was cancelled
    }
  }

  test("cancel should prevent execution") {
    for {
      debouncer <- Debouncer.create[String](100.millis)
      counter <- Ref.of[IO, Int](0)

      _ <- debouncer.debounce("key")(counter.update(_ + 1))
      _ <- IO.sleep(50.millis)
      _ <- debouncer.cancel("key")
      _ <- IO.sleep(100.millis)

      count <- counter.get
    } yield assertEquals(count, 0)  // Never executed
  }
}
```

### Integration Tests

```scala
class LspDebouncingTest extends CatsEffectSuite {

  test("rapid document changes should only validate once") {
    for {
      server <- createTestServer(debounceDelay = 100.millis)
      validationCount <- Ref.of[IO, Int](0)
      // ... setup to count validations

      // Simulate rapid typing
      _ <- (1 to 10).toList.traverse { i =>
        server.handleDidChange(makeChangeParams(s"content $i"))
          *> IO.sleep(20.millis)
      }

      // Wait for debounce
      _ <- IO.sleep(150.millis)
      count <- validationCount.get
    } yield assertEquals(count, 1)  // Only one validation
  }

  test("save should trigger immediate validation") {
    for {
      server <- createTestServer(debounceDelay = 200.millis)
      // ...
    } yield ()
  }
}
```

---

## Web Resources

### Debouncing Concepts
- [Debounce vs Throttle](https://css-tricks.com/debouncing-throttling-explained-examples/) - Visual explanation
- [Lodash debounce](https://lodash.com/docs/4.17.15#debounce) - Reference implementation
- [RxJS debounceTime](https://rxjs.dev/api/operators/debounceTime) - Reactive approach

### Cats Effect Patterns
- [Cats Effect Fibers](https://typelevel.org/cats-effect/docs/concepts#fibers) - Understanding fiber cancellation
- [Cats Effect Ref](https://typelevel.org/cats-effect/docs/std/ref) - Concurrent state management
- [Cats Effect Temporal](https://typelevel.org/cats-effect/docs/typeclasses/temporal) - Time-based operations

### LSP Implementations
- [VSCode Language Server Guide](https://code.visualstudio.com/api/language-extensions/language-server-extension-guide)
- [Eclipse LSP4J](https://github.com/eclipse-lsp4j/lsp4j) - Java LSP implementation (debouncing examples)
- [rust-analyzer Debouncing](https://github.com/rust-lang/rust-analyzer/blob/master/crates/rust-analyzer/src/global_state.rs) - Rust implementation

---

## Acceptance Criteria

1. **Functional Requirements**
   - [ ] Rapid changes only trigger one validation
   - [ ] Different documents debounce independently
   - [ ] Document save triggers immediate validation
   - [ ] Document close cancels pending validation
   - [ ] Delay is configurable

2. **Performance Requirements**
   - [ ] No additional latency for single changes
   - [ ] 10+ rapid changes complete in < 500ms (previously would take 1000+ms)
   - [ ] Memory overhead < 1KB per document

3. **Quality Requirements**
   - [ ] Unit test coverage > 80%
   - [ ] No test regressions
   - [ ] Thread-safe implementation

---

## Notes for Implementer

1. **Use Cats Effect `Fiber`** - The implementation should use fiber cancellation rather than thread interruption for clean shutdown.

2. **Test timing carefully** - Timing tests can be flaky. Use generous tolerances (±50ms) in assertions.

3. **Consider edge cases:**
   - What if validation fails?
   - What if document is closed while validation pending?
   - What about very long-running validations?

4. **Don't forget cleanup** - Remove fiber references from the map after action completes to prevent memory leaks.

5. **Delay selection:**
   - Too short (< 100ms): Still too many compilations
   - Too long (> 500ms): User perceives lag
   - Sweet spot: 150-300ms
