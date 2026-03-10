package io.constellation.dsl

import java.util.UUID

import scala.quoted.*

/** Mutable dependency collector used inside a [[PipelineBuilder.assemble]] block.
  *
  * Each [[field]] call records one input-field binding (upstream data node UUID → field name of the
  * assembled input type) and returns a placeholder value of the field's type.
  *
  * The collected bindings are consumed by [[PipelineBuilderImpl.assemble]] after the user-supplied
  * lambda returns, to wire `inEdges` and `DataNodeSpec.nicknames` for the fan-in module.
  *
  * ==Usage==
  * {{{
  * p.assemble(mergeModule) { ctx =>
  *   MergeInput(
  *     left  = ctx.field(nodeA, _.left),   // records nodeA.uuid → "left"
  *     right = ctx.field(nodeB, _.right)   // records nodeB.uuid → "right"
  *   )
  * }
  * }}}
  */
final class AssemblyCtx private[dsl] () {

  private var deps: List[(TypedRefImpl[?], String)] = List.empty

  /** Record a dependency: upstream data node `ref` feeds field `fieldName` of the assembled type.
    *
    * Called by the macro expansion of [[field]] at runtime. Stores the full `TypedRefImpl` so that
    * accumulated call options can be flushed by [[PipelineBuilderImpl.assemble]].
    */
  def recordDep(ref: TypedRef[?], fieldName: String): Unit =
    deps = deps :+ (ref.asInstanceOf[TypedRefImpl[?]] -> fieldName)

  /** Extract the field name from `selector` at compile time, record the dependency at runtime, and
    * return a placeholder value of the field's Scala type.
    *
    * `selector` must be a simple field selector of the form `_.fieldName`. Arbitrary expressions
    * inside the selector produce a compile error.
    *
    * The placeholder return value is always discarded — only the dependency recording side-effect
    * matters.
    */
  inline def field[A, B](ref: TypedRef[A], selector: A => B): B =
    ${ AssemblyCtx.fieldImpl('ref, 'selector, 'this) }

  /** The collected (dataNodeUUID, fieldName) pairs in recording order. */
  private[dsl] def getDeps: List[(UUID, String)] = deps.map { case (ref, name) => ref.uuid -> name }

  /** The collected (TypedRefImpl, fieldName) pairs — used by [[PipelineBuilderImpl]] to flush
    * accumulated call options from each upstream ref.
    */
  private[dsl] def getDepsWithRefs: List[(TypedRefImpl[?], String)] = deps
}

object AssemblyCtx {

  /** Scala 3 macro: extracts the field name from `selector` at compile time, generates code to
    * record the dependency in `ctx` at runtime, and returns a null placeholder of type `B`.
    */
  def fieldImpl[A: Type, B: Type](
      ref: Expr[TypedRef[A]],
      selector: Expr[A => B],
      ctx: Expr[AssemblyCtx]
  )(using Quotes): Expr[B] = {
    import quotes.reflect.*

    val fieldName: String = extractFieldName(selector.asTerm)

    '{
      $ctx.recordDep($ref, ${ Expr(fieldName) })
      null.asInstanceOf[B]
    }
  }

  /** Recursively walk a term to find the selected field name. */
  private def extractFieldName(using Quotes)(term: quotes.reflect.Term): String = {
    import quotes.reflect.*
    term match {
      case Select(_, name) => name
      case Block(List(defDef: DefDef), _) =>
        defDef.rhs match {
          case Some(body) => extractFieldName(body)
          case None =>
            report.errorAndAbort("ctx.field requires a simple field selector: _.fieldName")
        }
      case Inlined(_, _, inner) => extractFieldName(inner)
      case Lambda(_, body)      => extractFieldName(body)
      case Typed(inner, _)      => extractFieldName(inner)
      case Ident(_)             =>
        // Scala 3 may create a proxy variable (selector$proxyN) for lambda arguments
        // inside non-inline enclosing functions. Follow the proxy to its definition.
        term.symbol.tree match {
          case DefDef(_, _, _, Some(body)) => extractFieldName(body)
          case ValDef(_, _, Some(body))    => extractFieldName(body)
          case _ =>
            report.errorAndAbort(
              s"ctx.field requires a simple field selector like _.fieldName, got: ${term.show}"
            )
        }
      case _ =>
        report.errorAndAbort(
          s"ctx.field requires a simple field selector like _.fieldName, got: ${term.show}"
        )
    }
  }
}
