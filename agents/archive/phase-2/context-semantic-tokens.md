# Task 2.3: Semantic Tokens for LSP

**Phase:** 2 - Core Improvements
**Effort:** Medium (3-4 days)
**Impact:** Medium (Richer syntax highlighting)
**Dependencies:** Task 1.2 (Debounced LSP)
**Blocks:** Task 4.3 (Full Incremental LSP)

---

## Objective

Implement LSP semantic tokens to provide semantic-aware syntax highlighting in VSCode, distinguishing between variables, functions, types, and other semantic elements.

---

## Background

### Current State

VSCode uses TextMate grammar for syntax highlighting (pattern-based):
- All identifiers look the same
- No distinction between function calls and variables
- No highlighting of user-defined types

### With Semantic Tokens

Semantic highlighting uses compiler analysis:
- **Functions** highlighted differently from variables
- **Types** in type positions highlighted as types
- **Keywords** contextually highlighted
- **Parameters** vs **variables** distinguished

---

## Technical Design

### Semantic Token Types

```scala
enum SemanticTokenType(val id: Int) {
  case Namespace extends SemanticTokenType(0)
  case Type extends SemanticTokenType(1)
  case Function extends SemanticTokenType(2)
  case Variable extends SemanticTokenType(3)
  case Parameter extends SemanticTokenType(4)
  case Property extends SemanticTokenType(5)
  case Keyword extends SemanticTokenType(6)
  case String extends SemanticTokenType(7)
  case Number extends SemanticTokenType(8)
  case Operator extends SemanticTokenType(9)
  case Comment extends SemanticTokenType(10)
}

enum SemanticTokenModifier(val bit: Int) {
  case Declaration extends SemanticTokenModifier(0)
  case Definition extends SemanticTokenModifier(1)
  case Readonly extends SemanticTokenModifier(2)
  case DefaultLibrary extends SemanticTokenModifier(3)
}
```

### Token Encoding

LSP requires tokens as delta-encoded integers:

```scala
case class SemanticToken(
  deltaLine: Int,      // Line delta from previous token
  deltaStart: Int,     // Column delta (or absolute if new line)
  length: Int,         // Token length
  tokenType: Int,      // Type index
  tokenModifiers: Int  // Modifier bitmask
)
```

### Provider Implementation

```scala
class SemanticTokenProvider(compiler: LangCompiler) {

  def computeTokens(source: String): List[SemanticToken] = {
    ConstellationParser.parse(source) match {
      case Right(program) =>
        val tokens = extractTokens(program, source)
        encodeTokens(tokens)
      case Left(_) =>
        List.empty  // Return empty on parse error
    }
  }

  private def extractTokens(program: Program, source: String): List[RawToken] = {
    program.declarations.flatMap {
      case TypeDef(name, typeExpr) =>
        List(
          RawToken(name.span, SemanticTokenType.Type, Set(Declaration, Definition)),
          extractTypeTokens(typeExpr)
        ).flatten

      case InputDecl(name, typeExpr, _) =>
        List(
          RawToken(name.span, SemanticTokenType.Parameter, Set(Declaration)),
          extractTypeTokens(typeExpr.value)
        ).flatten

      case Assignment(target, expr) =>
        List(
          RawToken(target.span, SemanticTokenType.Variable, Set(Declaration))
        ) ++ extractExprTokens(expr.value)

      // ... etc
    }
  }
}
```

---

## Deliverables

### Required

- [ ] **`SemanticTokenProvider.scala`** - Token computation from AST
- [ ] **`SemanticTokenTypes.scala`** - Type and modifier definitions
- [ ] **LSP handler** - `textDocument/semanticTokens/full`
- [ ] **VSCode extension update** - Register semantic token provider
- [ ] **Unit tests** - Token extraction correctness

### Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `modules/lang-lsp/src/main/scala/io/constellation/lsp/SemanticTokenProvider.scala` | **New** | Provider |
| `modules/lang-lsp/src/main/scala/io/constellation/lsp/SemanticTokenTypes.scala` | **New** | Types |
| `modules/lang-lsp/src/main/scala/io/constellation/lsp/ConstellationLanguageServer.scala` | Modify | Add handler |
| `vscode-extension/src/extension.ts` | Modify | Register provider |
| `vscode-extension/package.json` | Modify | Declare capabilities |

---

## Implementation Guide

### Step 1: Define Token Types

```scala
// SemanticTokenTypes.scala
package io.constellation.lsp

object SemanticTokenTypes {
  val tokenTypes: List[String] = List(
    "namespace", "type", "function", "variable", "parameter",
    "property", "keyword", "string", "number", "operator", "comment"
  )

  val tokenModifiers: List[String] = List(
    "declaration", "definition", "readonly", "defaultLibrary"
  )

  // For LSP capability response
  val legend = SemanticTokensLegend(tokenTypes, tokenModifiers)
}
```

### Step 2: Implement Provider

```scala
// SemanticTokenProvider.scala
package io.constellation.lsp

import io.constellation.lang.ast._

class SemanticTokenProvider {

  def computeTokens(source: String): SemanticTokensResult = {
    ConstellationParser.parse(source) match {
      case Right(program) =>
        val rawTokens = extractTokens(program)
        val encoded = encodeTokens(rawTokens, source)
        SemanticTokensResult(encoded)
      case Left(_) =>
        SemanticTokensResult(List.empty)
    }
  }

  private def extractTokens(program: Program): List[RawToken] = {
    val tokens = mutable.ListBuffer[RawToken]()

    program.declarations.foreach {
      case TypeDef(name, typeExpr) =>
        tokens += RawToken(name.span, TokenType.Type, Modifiers.Declaration | Modifiers.Definition)
        tokens ++= extractTypeExprTokens(typeExpr.value)

      case InputDecl(name, typeExpr, annotations) =>
        tokens += RawToken(name.span, TokenType.Parameter, Modifiers.Declaration)
        tokens ++= extractTypeExprTokens(typeExpr.value)

      case Assignment(target, expr) =>
        tokens += RawToken(target.span, TokenType.Variable, Modifiers.Declaration)
        tokens ++= extractExprTokens(expr.value)

      case OutputDecl(name) =>
        tokens += RawToken(name.span, TokenType.Variable, 0)

      case UseDecl(path, alias) =>
        tokens += RawToken(path.span, TokenType.Namespace, 0)
        alias.foreach(a => tokens += RawToken(a.span, TokenType.Namespace, Modifiers.Declaration))
    }

    tokens.toList
  }

  private def extractExprTokens(expr: Expression): List[RawToken] = expr match {
    case VarRef(name) =>
      // Would need symbol table to distinguish variable vs parameter
      List(RawToken(expr.span, TokenType.Variable, 0))

    case FunctionCall(name, args) =>
      List(RawToken(name.span, TokenType.Function, Modifiers.DefaultLibrary)) ++
        args.flatMap(a => extractExprTokens(a.value))

    case FieldAccess(source, field) =>
      extractExprTokens(source.value) :+ RawToken(field.span, TokenType.Property, 0)

    // ... etc
  }

  private def encodeTokens(tokens: List[RawToken], source: String): List[Int] = {
    val sorted = tokens.sortBy(t => (t.span.start))
    val lineMap = buildLineMap(source)

    var prevLine = 0
    var prevCol = 0
    val encoded = mutable.ListBuffer[Int]()

    sorted.foreach { token =>
      val (line, col) = offsetToLineCol(token.span.start, lineMap)
      val length = token.span.end - token.span.start

      val deltaLine = line - prevLine
      val deltaCol = if (deltaLine == 0) col - prevCol else col

      encoded += deltaLine
      encoded += deltaCol
      encoded += length
      encoded += token.tokenType.id
      encoded += token.modifiers

      prevLine = line
      prevCol = col
    }

    encoded.toList
  }
}
```

### Step 3: Add LSP Handler

```scala
// In ConstellationLanguageServer.scala
def handleSemanticTokensFull(params: SemanticTokensParams): IO[SemanticTokens] = {
  val uri = params.getTextDocument.getUri
  for {
    document <- documentManager.getDocument(uri)
    tokens = semanticTokenProvider.computeTokens(document.text)
  } yield SemanticTokens(tokens.data.asJava)
}
```

### Step 4: Update VSCode Extension

```typescript
// In extension.ts
const serverCapabilities: ServerCapabilities = {
  // ... existing capabilities
  semanticTokensProvider: {
    full: true,
    legend: {
      tokenTypes: ['namespace', 'type', 'function', 'variable', 'parameter',
                   'property', 'keyword', 'string', 'number', 'operator', 'comment'],
      tokenModifiers: ['declaration', 'definition', 'readonly', 'defaultLibrary']
    }
  }
};
```

```json
// In package.json, add contribution
"contributes": {
  "semanticTokenTypes": [
    { "id": "function", "superType": "function", "description": "Constellation function" }
  ],
  "semanticTokenScopes": {
    "function": ["entity.name.function.constellation"]
  }
}
```

---

## Web Resources

### LSP Semantic Tokens
- [LSP Specification - Semantic Tokens](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_semanticTokens)
- [VSCode Semantic Highlighting](https://code.visualstudio.com/api/language-extensions/semantic-highlight-guide)
- [Semantic Token Types](https://code.visualstudio.com/api/language-extensions/semantic-highlight-guide#standard-token-types-and-modifiers)

### Implementation Examples
- [rust-analyzer Semantic Tokens](https://github.com/rust-lang/rust-analyzer/blob/master/crates/ide/src/syntax_highlighting.rs)
- [TypeScript Language Server](https://github.com/typescript-language-features)

---

## Acceptance Criteria

1. **Functional Requirements**
   - [ ] Functions highlighted as functions
   - [ ] Types highlighted as types
   - [ ] Variables vs parameters distinguished
   - [ ] Works with theme colors

2. **Quality Requirements**
   - [ ] No performance regression
   - [ ] Graceful degradation on parse errors
   - [ ] Unit tests for token extraction
