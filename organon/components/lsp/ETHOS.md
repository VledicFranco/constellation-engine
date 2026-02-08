# LSP Ethos

> Normative constraints for the Language Server Protocol implementation.

---

## Identity

- **IS:** Protocol adapter providing IDE integration for constellation-lang via LSP standard
- **IS NOT:** A compiler, runtime, HTTP server, or syntax definition

---

## Semantic Mapping

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `ConstellationLanguageServer` | Main LSP server handling requests and notifications |
| `DocumentManager` | Thread-safe registry of open documents with text and version |
| `DocumentState` | Snapshot of a single document (URI, text, version, sourceFile) |
| `Debouncer` | Rate limiter that coalesces rapid edits before validation |
| `DebugSessionManager` | Registry of step-through debugging sessions with state isolation |
| `SessionState` | Execution state for a debug session (batches, node states, progress) |
| `CompletionTrie` | O(k) prefix tree for efficient completion lookups |
| `SemanticTokenProvider` | Delta-encoded token extractor for semantic highlighting |
| `TypeFormatter` | Pretty-printer for type signatures in hover content |
| `WithClauseCompletions` | Context-aware completions for `with` clause options |
| `OptionsDiagnostics` | Semantic warnings for module call options |
| `JsonRpc` | JSON-RPC 2.0 message types for LSP communication |
| `LspTypes` | Core LSP protocol type definitions (Position, Range, etc.) |
| `LspMessages` | Request/Response/Notification codecs |

For complete type signatures, see:
- [io.constellation.lsp](/organon/generated/io.constellation.lsp.md)
- [io.constellation.lsp.protocol](/organon/generated/io.constellation.lsp.protocol.md)
- [io.constellation.lsp.diagnostics](/organon/generated/io.constellation.lsp.diagnostics.md)

---

## Invariants

### 1. Requests return responses with matching ID

Every LSP request receives exactly one response with the same request ID. Unknown methods return `MethodNotFound` error.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-lsp/src/main/scala/io/constellation/lsp/ConstellationLanguageServer.scala#handleRequest` |
| Test | `modules/lang-lsp/src/test/scala/io/constellation/lsp/ConstellationLanguageServerTest.scala#return MethodNotFound for unknown request` |

### 2. Debouncing coalesces rapid edits

Multiple rapid `didChange` notifications for the same document result in only one validation after the debounce delay. Subsequent calls cancel pending actions.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-lsp/src/main/scala/io/constellation/lsp/Debouncer.scala#debounce` |
| Test | `modules/lang-lsp/src/test/scala/io/constellation/lsp/DebouncerTest.scala#only execute once for rapid calls` |

### 3. Document state is thread-safe

`DocumentManager` uses `Ref[IO, Map]` for atomic updates. Concurrent opens, updates, and closes do not corrupt state.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-lsp/src/main/scala/io/constellation/lsp/DocumentManager.scala#Ref[IO, Map[String, DocumentState]]` |
| Test | `modules/lang-lsp/src/test/scala/io/constellation/lsp/DocumentManagerTest.scala#handle concurrent updates` |

### 4. Completion trie provides O(k) prefix lookup

`CompletionTrie.findByPrefix` runs in O(k) time where k is prefix length, not O(n) where n is item count. Lookups are case-insensitive.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-lsp/src/main/scala/io/constellation/lsp/CompletionTrie.scala#findByPrefix` |
| Test | `modules/lang-lsp/src/test/scala/io/constellation/lsp/CompletionTrieTest.scala#be case-insensitive` |

### 5. Debug sessions are isolated

Each `SessionState` maintains independent execution state. Stepping one session does not affect others.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-lsp/src/main/scala/io/constellation/lsp/DebugSessionManager.scala#sessionsRef` |
| Test | `modules/lang-lsp/src/test/scala/io/constellation/lsp/DebugSessionManagerTest.scala#ensure sessions don't interfere` |

### 6. Semantic tokens are delta-encoded

`SemanticTokenProvider.computeTokens` returns exactly 5 integers per token (deltaLine, deltaStart, length, tokenType, modifiers) as required by LSP.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-lsp/src/main/scala/io/constellation/lsp/SemanticTokenProvider.scala#computeTokens` |
| Test | `modules/lang-lsp/src/test/scala/io/constellation/lsp/SemanticTokenProviderTest.scala#produce exactly 5 integers per token` |

### 7. Invalid params return error, not exception

Malformed or missing request parameters return `InvalidParams` error response. The server does not throw.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-lsp/src/main/scala/io/constellation/lsp/ConstellationLanguageServer.scala#InvalidParams` |
| Test | `modules/lang-lsp/src/test/scala/io/constellation/lsp/ConstellationLanguageServerTest.scala#handle malformed completion params gracefully` |

---

## Principles (Prioritized)

1. **Protocol compliance first** - Strict adherence to LSP specification; non-standard extensions use `constellation/` prefix
2. **Responsiveness over completeness** - Return partial results fast (debounce, skip semantic tokens for large files)
3. **Graceful degradation** - Parse errors produce empty tokens, not crashes; missing documents return empty completions
4. **Stateless handlers** - Each request is handled independently; all state lives in `DocumentManager` or `DebugSessionManager`

---

## Decision Heuristics

- When adding a new LSP feature, implement the standard method name first; only add `constellation/` extension if standard is insufficient
- When uncertain about response timing, prefer debounced validation to immediate; adjust delay based on benchmarks
- When handling malformed input, log and return error response; never throw from request handlers
- When optimizing completion, prefer trie over linear filtering; rebuild trie only when module registry changes
- When skipping expensive operations (semantic tokens for large files), document the threshold and rationale

---

## Out of Scope

- Parsing and compilation (see [compiler/](../compiler/))
- Runtime execution engine (see [runtime/](../runtime/))
- HTTP routing and WebSocket transport (see [http-api/](../http-api/))
- Core type system definitions (see [core/](../core/))
- VSCode extension packaging (see [tooling/vscode/](../../tooling/vscode/))

---

## Implements Features

| Feature | Artifacts |
|---------|-----------|
| [Tooling](../../features/tooling/) | ConstellationLanguageServer, DocumentManager, CompletionTrie, SemanticTokenProvider, DebugSessionManager |
