package io.constellation.lsp

import scala.collection.mutable

import io.constellation.lang.ast.*
import io.constellation.lang.parser.ConstellationParser
import io.constellation.lsp.SemanticTokenTypes.*

/** Provides semantic tokens for LSP semantic highlighting.
  *
  * This provider parses constellation-lang source code and extracts semantic token information for
  * syntax highlighting that understands the semantic meaning of code elements (e.g., distinguishing
  * functions from variables, parameters from local variables, etc.)
  *
  * @see
  *   https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_semanticTokens
  */
class SemanticTokenProvider {

  /** A raw token with absolute position, before delta-encoding */
  private case class RawToken(
      start: Int,
      length: Int,
      tokenType: TokenType,
      modifiers: Int
  )

  /** Compute semantic tokens for the given source code.
    *
    * @param source
    *   The constellation-lang source code
    * @return
    *   List of integers representing delta-encoded tokens per LSP spec, or empty list on parse
    *   error (graceful degradation)
    */
  def computeTokens(source: String): List[Int] =
    ConstellationParser.parse(source) match {
      case Right(program) =>
        val rawTokens = extractTokens(program)
        encodeTokens(rawTokens, source)
      case Left(_) =>
        // Graceful degradation: return empty on parse error
        // TextMate grammar still provides basic highlighting
        List.empty
    }

  /** Extract raw tokens from the AST */
  private def extractTokens(program: Pipeline): List[RawToken] = {
    val tokens = mutable.ListBuffer[RawToken]()

    // Process all declarations
    program.declarations.foreach { decl =>
      tokens ++= extractDeclarationTokens(decl)
    }

    // Process output declarations
    program.outputs.foreach { output =>
      tokens += RawToken(
        output.span.start,
        output.span.length,
        TokenType.Variable,
        TokenModifier.None
      )
    }

    tokens.toList
  }

  /** Extract tokens from a declaration */
  private def extractDeclarationTokens(decl: Declaration): List[RawToken] = {
    val tokens = mutable.ListBuffer[RawToken]()

    decl match {
      case Declaration.TypeDef(name, definition) =>
        // Type name is a type with declaration+definition modifiers
        tokens += RawToken(
          name.span.start,
          name.span.length,
          TokenType.Type,
          TokenModifier.Declaration | TokenModifier.Definition
        )
        tokens ++= extractTypeExprTokens(definition.value)

      case Declaration.InputDecl(name, typeExpr, annotations) =>
        // Input is a parameter with declaration modifier
        tokens += RawToken(
          name.span.start,
          name.span.length,
          TokenType.Parameter,
          TokenModifier.Declaration
        )
        tokens ++= extractTypeExprTokens(typeExpr.value)
        // Process annotations
        annotations.foreach { case Annotation.Example(expr) =>
          tokens ++= extractExpressionTokens(expr)
        }

      case Declaration.Assignment(target, value) =>
        // Assignment target is a variable with declaration modifier
        tokens += RawToken(
          target.span.start,
          target.span.length,
          TokenType.Variable,
          TokenModifier.Declaration
        )
        tokens ++= extractExpressionTokens(value)

      case Declaration.OutputDecl(name) =>
        // Output is a variable reference
        tokens += RawToken(
          name.span.start,
          name.span.length,
          TokenType.Variable,
          TokenModifier.None
        )

      case Declaration.UseDecl(path, alias) =>
        // Use path is a namespace
        tokens += RawToken(
          path.span.start,
          path.span.length,
          TokenType.Namespace,
          TokenModifier.None
        )
        // Alias is also a namespace with declaration modifier
        alias.foreach { a =>
          tokens += RawToken(
            a.span.start,
            a.span.length,
            TokenType.Namespace,
            TokenModifier.Declaration
          )
        }
    }

    tokens.toList
  }

  /** Extract tokens from a type expression */
  private def extractTypeExprTokens(typeExpr: TypeExpr): List[RawToken] = {
    val tokens = mutable.ListBuffer[RawToken]()

    typeExpr match {
      case TypeExpr.Primitive(_) =>
        // Primitive type names don't have span info in the AST
        // They would need to be tracked separately
        ()

      case TypeExpr.Record(fields) =>
        fields.foreach { case (_, fieldType) =>
          tokens ++= extractTypeExprTokens(fieldType)
        }

      case TypeExpr.Parameterized(_, params) =>
        params.foreach { param =>
          tokens ++= extractTypeExprTokens(param)
        }

      case TypeExpr.TypeRef(_) =>
        // Type refs don't have span info in current AST
        ()

      case TypeExpr.TypeMerge(left, right) =>
        tokens ++= extractTypeExprTokens(left)
        tokens ++= extractTypeExprTokens(right)

      case TypeExpr.Union(members) =>
        members.foreach { member =>
          tokens ++= extractTypeExprTokens(member)
        }
    }

    tokens.toList
  }

  /** Extract tokens from an expression */
  private def extractExpressionTokens(located: Located[Expression]): List[RawToken] = {
    val tokens = mutable.ListBuffer[RawToken]()
    val expr   = located.value
    val span   = located.span

    expr match {
      case Expression.VarRef(_) =>
        // Variable reference
        tokens += RawToken(span.start, span.length, TokenType.Variable, TokenModifier.None)

      case Expression.FunctionCall(name, args, _) =>
        // Function name - use the span of the Located expression minus args
        // Since QualifiedName doesn't have span, we need to estimate
        val funcNameLen = name.fullName.length
        tokens += RawToken(
          span.start,
          funcNameLen,
          TokenType.Function,
          TokenModifier.DefaultLibrary
        )
        args.foreach { arg =>
          tokens ++= extractExpressionTokens(arg)
        }

      case Expression.Merge(left, right) =>
        tokens ++= extractExpressionTokens(left)
        tokens ++= extractExpressionTokens(right)

      case Expression.Projection(source, _) =>
        tokens ++= extractExpressionTokens(source)
      // Field names in projection don't have individual spans

      case Expression.FieldAccess(source, field) =>
        tokens ++= extractExpressionTokens(source)
        tokens += RawToken(
          field.span.start,
          field.span.length,
          TokenType.Property,
          TokenModifier.None
        )

      case Expression.Conditional(condition, thenBranch, elseBranch) =>
        tokens ++= extractExpressionTokens(condition)
        tokens ++= extractExpressionTokens(thenBranch)
        tokens ++= extractExpressionTokens(elseBranch)

      case Expression.StringLit(_) =>
        tokens += RawToken(span.start, span.length, TokenType.String, TokenModifier.None)

      case Expression.StringInterpolation(_, expressions) =>
        // The full interpolation is a string
        tokens += RawToken(span.start, span.length, TokenType.String, TokenModifier.None)
        // But also extract tokens from interpolated expressions
        expressions.foreach { expr =>
          tokens ++= extractExpressionTokens(expr)
        }

      case Expression.IntLit(_) =>
        tokens += RawToken(span.start, span.length, TokenType.Number, TokenModifier.None)

      case Expression.FloatLit(_) =>
        tokens += RawToken(span.start, span.length, TokenType.Number, TokenModifier.None)

      case Expression.BoolLit(_) =>
        // Booleans are keywords
        tokens += RawToken(span.start, span.length, TokenType.Keyword, TokenModifier.None)

      case Expression.ListLit(elements) =>
        elements.foreach { elem =>
          tokens ++= extractExpressionTokens(elem)
        }

      case Expression.Compare(left, _, right) =>
        tokens ++= extractExpressionTokens(left)
        tokens ++= extractExpressionTokens(right)

      case Expression.Arithmetic(left, _, right) =>
        tokens ++= extractExpressionTokens(left)
        tokens ++= extractExpressionTokens(right)

      case Expression.BoolBinary(left, _, right) =>
        tokens ++= extractExpressionTokens(left)
        tokens ++= extractExpressionTokens(right)

      case Expression.Not(operand) =>
        tokens ++= extractExpressionTokens(operand)

      case Expression.Guard(expr, condition) =>
        tokens ++= extractExpressionTokens(expr)
        tokens ++= extractExpressionTokens(condition)

      case Expression.Coalesce(left, right) =>
        tokens ++= extractExpressionTokens(left)
        tokens ++= extractExpressionTokens(right)

      case Expression.Branch(cases, otherwise) =>
        cases.foreach { case (cond, result) =>
          tokens ++= extractExpressionTokens(cond)
          tokens ++= extractExpressionTokens(result)
        }
        tokens ++= extractExpressionTokens(otherwise)

      case Expression.Lambda(params, body) =>
        // Lambda parameters
        params.foreach { param =>
          tokens += RawToken(
            param.name.span.start,
            param.name.span.length,
            TokenType.Parameter,
            TokenModifier.Declaration
          )
          // Type annotation if present
          param.typeAnnotation.foreach { typeExpr =>
            tokens ++= extractTypeExprTokens(typeExpr.value)
          }
        }
        tokens ++= extractExpressionTokens(body)
    }

    tokens.toList
  }

  /** Encode raw tokens as delta-encoded integers per LSP spec.
    *
    * Each token is encoded as 5 integers:
    *   - deltaLine: line delta from previous token
    *   - deltaStart: character delta (or absolute if new line)
    *   - length: token length
    *   - tokenType: token type index
    *   - tokenModifiers: modifier bitmask
    */
  private def encodeTokens(rawTokens: List[RawToken], source: String): List[Int] = {
    if rawTokens.isEmpty then return List.empty

    // Build line map for offset -> line/col conversion
    val lineMap = LineMap.fromSource(source)

    // Sort tokens by position
    val sorted = rawTokens.sortBy(_.start)

    var prevLine = 0
    var prevCol  = 0
    val encoded  = mutable.ListBuffer[Int]()

    sorted.foreach { token =>
      val lineCol = lineMap.offsetToLineCol(token.start)
      // LineCol is 1-based, convert to 0-based for LSP
      val line = lineCol.line - 1
      val col  = lineCol.col - 1

      val deltaLine = line - prevLine
      val deltaCol  = if deltaLine == 0 then col - prevCol else col

      encoded += deltaLine
      encoded += deltaCol
      encoded += token.length
      encoded += token.tokenType.index
      encoded += token.modifiers

      prevLine = line
      prevCol = col
    }

    encoded.toList
  }
}

object SemanticTokenProvider {

  /** Create a new SemanticTokenProvider instance */
  def apply(): SemanticTokenProvider = new SemanticTokenProvider()
}
