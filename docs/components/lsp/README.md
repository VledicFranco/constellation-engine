# LSP Component

> **Path**: `docs/components/lsp/`
> **Parent**: [components/](../README.md)
> **Module**: `modules/lang-lsp/`

Language Server Protocol implementation for constellation-lang IDE support.

## Key Files

| File | Purpose |
|------|---------|
| `ConstellationLanguageServer.scala` | Main LSP server implementation |
| `DocumentManager.scala` | Open document state management |
| `DebugSessionManager.scala` | Step-through debugging sessions |
| `Debouncer.scala` | Debounced validation for typing |
| `CompletionTrie.scala` | Efficient prefix-based completion lookup |
| `SemanticTokenProvider.scala` | Semantic syntax highlighting |
| `TypeFormatter.scala` | Type signature formatting for hover |
| `WithClauseCompletions.scala` | Completions for `with` clause options |
| **Protocol** | |
| `protocol/JsonRpc.scala` | JSON-RPC message types |
| `protocol/LspTypes.scala` | LSP type definitions |
| `protocol/LspMessages.scala` | LSP message codecs |
| **Diagnostics** | |
| `diagnostics/OptionsDiagnostics.scala` | Validation for module options |

## Role in the System

The LSP provides IDE integration via WebSocket:

```
                    ┌─────────────┐
                    │    core     │
                    └──────┬──────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  │
   [runtime]         [lang-compiler]          │
        │                  │                  │
        │         ┌────────┴────────┐         │
        │         │                 │         │
        │         ▼                 ▼         │
        │    [lang-stdlib]     [lang-lsp] ◄───┘
        │         │
        └─────────┼─────────────────┐
                  │                 │
                  ▼                 ▼
             [http-api]       [example-app]
```

The LSP:
1. Receives source code changes from the editor
2. Compiles incrementally with debouncing
3. Publishes diagnostics (errors, warnings)
4. Provides completions, hover, semantic tokens

## LSP Features

### Standard LSP Methods

| Method | Handler | Description |
|--------|---------|-------------|
| `initialize` | `handleInitialize` | Server capability negotiation |
| `shutdown` | Direct response | Graceful shutdown |
| `textDocument/completion` | `handleCompletion` | Code completions |
| `textDocument/hover` | `handleHover` | Type information on hover |
| `textDocument/semanticTokens/full` | `handleSemanticTokensFull` | Syntax highlighting |
| `textDocument/didOpen` | `handleDidOpen` | Document opened |
| `textDocument/didChange` | `handleDidChange` | Document edited |
| `textDocument/didSave` | `handleDidSave` | Document saved |
| `textDocument/didClose` | `handleDidClose` | Document closed |

### Constellation Extensions

| Method | Handler | Description |
|--------|---------|-------------|
| `constellation/executePipeline` | `handleExecutePipeline` | Run pipeline with inputs |
| `constellation/getInputSchema` | `handleGetInputSchema` | Get input field definitions |
| `constellation/getDagStructure` | `handleGetDagStructure` | Get DAG topology |
| `constellation/getDagVisualization` | `handleGetDagVisualization` | Get layouted DAG for rendering |
| `constellation/stepStart` | `handleStepStart` | Start step-through execution |
| `constellation/stepNext` | `handleStepNext` | Execute next batch |
| `constellation/stepContinue` | `handleStepContinue` | Run to completion |
| `constellation/stepStop` | `handleStepStop` | Stop debugging session |
| `constellation/getCacheStats` | `handleGetCacheStats` | Compilation cache statistics |

## Architecture

### Document Management

```scala
DocumentManager
├── openDocument(uri, languageId, version, text)
├── updateDocument(uri, version, text)
├── closeDocument(uri)
└── getDocument(uri): Option[DocumentState]

DocumentState
├── uri: String
├── text: String
├── version: Int
├── sourceFile: SourceFile  // For span → line/col conversion
```

### Debounced Validation

To avoid excessive compilation during rapid typing:

```scala
// Configured delay (default 150ms)
val debouncer = Debouncer.create[String](150.milliseconds)

// On document change
debouncer.debounce(uri)(validateDocument(uri))

// On save (immediate)
debouncer.immediate(uri)(validateDocument(uri))
```

### Completion Pipeline

```
User Types → getCompletions()
                    │
    ┌───────────────┼───────────────┐
    │               │               │
    ▼               ▼               ▼
WithClause?    Namespace?      Standard?
    │               │               │
    ▼               ▼               ▼
WithClause    Qualified      Trie Lookup
Completions   Functions      (keywords +
                             modules)
```

1. **WithClause context**: Option names (`retry`, `timeout`, etc.)
2. **Namespace context**: Functions in `stdlib.math.`
3. **Standard context**: Keywords + registered modules via `CompletionTrie`

### Completion Trie

Efficient O(k) prefix lookup (k = prefix length):

```scala
val trie = CompletionTrie(items)
val matches = trie.findByPrefix("add")  // addModule, add, ...
```

Tries are:
- Pre-built for keywords (static)
- Rebuilt when modules change (cached with `lastModuleNames`)

### Semantic Tokens

Provides syntax highlighting data:

```scala
SemanticTokenProvider.computeTokens(source: String): List[Int]
```

Returns delta-encoded token data:
- Delta line, delta start char, length, token type, modifiers

Token types include:
- Keywords (`in`, `out`, `type`, `with`)
- Types (PascalCase identifiers)
- Functions (module calls)
- Variables
- Strings, numbers, comments

### DAG Visualization

```scala
// 1. Compile to IR
compiler.compileToIR(source, dagName)

// 2. Convert IR to visualization IR
DagVizCompiler.compile(irPipeline)

// 3. Apply layout algorithm
SugiyamaLayout.layout(vizIR, layoutConfig)

// 4. Return positioned nodes/edges
DagVisualization(nodes, edges, groups, metadata)
```

Supports execution state overlay when `executionId` is provided.

## Step-Through Debugging

### Session Lifecycle

```
stepStart(uri, inputs)
    │
    ▼
┌──────────────────────┐
│  DebugSessionManager │
│  ├── sessionId       │
│  ├── batches[]       │
│  ├── nodeStates{}    │
│  └── currentBatch    │
└──────────┬───────────┘
           │
    stepNext(sessionId) ←──┐
           │               │
           ▼               │
    Execute next batch ────┘
           │
    stepContinue(sessionId)
           │
           ▼
    Run remaining batches
           │
    stepStop(sessionId)
```

### Session State

```scala
case class SessionState(
  sessionId: String,
  dagSpec: DagSpec,
  batches: List[ExecutionBatch],
  currentBatchIndex: Int,
  nodeStates: Map[UUID, NodeState],
  startTime: Long
)

sealed trait NodeState
case object Pending extends NodeState
case class Completed(value: Any, durationMs: Long) extends NodeState
```

## Error Handling

### Enhanced Diagnostics

Errors include:
- Error code (e.g., `E0001`)
- Explanation
- Fix suggestions
- Code context snippet

```scala
// Error code mapping
val errorCode = CompilerErrorCodes.fromCompileError(error)

// Suggestions
val suggestions = Suggestions.forError(error, context)

// Build enhanced message
s"[${errorCode.code}] ${error.message}\n\n${errorCode.explanation}\n\n${suggestions.mkString("\n")}"
```

### Warning Codes

| Code | Warning Type |
|------|--------------|
| `OPTS001` | Option dependency issue |
| `OPTS003` | High retry count |

## Performance

### Timing Thresholds

| Operation | Log Threshold | Target |
|-----------|---------------|--------|
| Request | >50ms | Log slow requests |
| Notification | >10ms | Log slow notifications |
| Autocomplete | <50ms | User-perceivable |

### Large File Handling

```scala
// Skip semantic tokens for files > 150 lines
val MaxLinesForSemanticTokens = 150
if lineCount > MaxLinesForSemanticTokens then
  SemanticTokens(data = List.empty)
```

## Dependencies

- **Depends on:** `core` (CType, CValue), `runtime` (Module, Constellation), `lang-compiler` (LangCompiler)
- **Depended on by:** `http-api` (LspWebSocketHandler)

## Features Using This Component

| Feature | LSP Role |
|---------|----------|
| [VSCode Extension](../../tooling/vscode/) | WebSocket client |
| [DAG visualization](../../features/visualization/) | getDagVisualization |
| [Step debugging](../../features/debugging/) | Step execution session |
| [Error messages](../../reference/errors.md) | Enhanced diagnostics |
