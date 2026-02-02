package io.constellation.lsp

/** Semantic token types for LSP semantic highlighting.
  *
  * These types are used to classify tokens in the source code for semantic-aware syntax
  * highlighting. The indices must match the order declared in the legend sent during
  * initialization.
  *
  * @see
  *   https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_semanticTokens
  */
object SemanticTokenTypes {

  /** Standard LSP semantic token types. The index corresponds to the position in the legend's
    * tokenTypes array.
    */
  enum TokenType(val index: Int) {
    case Namespace extends TokenType(0)
    case Type      extends TokenType(1)
    case Function  extends TokenType(2)
    case Variable  extends TokenType(3)
    case Parameter extends TokenType(4)
    case Property  extends TokenType(5)
    case Keyword   extends TokenType(6)
    case String    extends TokenType(7)
    case Number    extends TokenType(8)
    case Operator  extends TokenType(9)
    case Comment   extends TokenType(10)
  }

  /** Standard LSP semantic token modifiers. These are represented as a bitmask - each modifier is a
    * power of 2.
    */
  object TokenModifier {
    val Declaration: Int    = 1 << 0 // 1
    val Definition: Int     = 1 << 1 // 2
    val Readonly: Int       = 1 << 2 // 4
    val DefaultLibrary: Int = 1 << 3 // 8

    val None: Int = 0
  }

  /** Token types as string list for LSP legend. Order must match TokenType enum indices.
    */
  val tokenTypes: List[String] = List(
    "namespace",
    "type",
    "function",
    "variable",
    "parameter",
    "property",
    "keyword",
    "string",
    "number",
    "operator",
    "comment"
  )

  /** Token modifiers as string list for LSP legend. Order must match bit positions in
    * TokenModifier.
    */
  val tokenModifiers: List[String] = List(
    "declaration",
    "definition",
    "readonly",
    "defaultLibrary"
  )
}
