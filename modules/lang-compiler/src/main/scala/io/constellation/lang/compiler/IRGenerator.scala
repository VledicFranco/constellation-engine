package io.constellation.lang.compiler

import io.constellation.lang.semantic.*

import java.util.UUID

/** Generates IR from a typed AST */
object IRGenerator {

  /** Context for IR generation */
  private case class GenContext(
    nodes: Map[UUID, IRNode],
    bindings: Map[String, UUID]  // Variable name -> node ID
  ) {
    def addNode(node: IRNode): GenContext =
      copy(nodes = nodes + (node.id -> node))

    def bind(name: String, nodeId: UUID): GenContext =
      copy(bindings = bindings + (name -> nodeId))

    def lookup(name: String): Option[UUID] =
      bindings.get(name)
  }

  private object GenContext {
    def empty: GenContext = GenContext(Map.empty, Map.empty)
  }

  /** Generate IR from a typed program */
  def generate(program: TypedProgram): IRProgram = {
    val (finalCtx, inputIds) = generateDeclarations(program.declarations, GenContext.empty)
    val (resultCtx, outputId) = generateExpression(program.output, finalCtx)

    IRProgram(
      nodes = resultCtx.nodes,
      inputs = inputIds,
      output = outputId,
      outputType = program.outputType
    )
  }

  private def generateDeclarations(
    decls: List[TypedDeclaration],
    ctx: GenContext
  ): (GenContext, List[UUID]) = {
    var currentCtx = ctx
    var inputIds = List.empty[UUID]

    decls.foreach {
      case TypedDeclaration.TypeDef(_, _) =>
        // Type definitions don't generate IR nodes
        ()

      case TypedDeclaration.InputDecl(name, semanticType) =>
        val id = UUID.randomUUID()
        val node = IRNode.Input(id, name, semanticType)
        currentCtx = currentCtx.addNode(node).bind(name, id)
        inputIds = inputIds :+ id

      case TypedDeclaration.Assignment(name, value) =>
        val (newCtx, valueId) = generateExpression(value, currentCtx)
        currentCtx = newCtx.bind(name, valueId)
    }

    (currentCtx, inputIds)
  }

  private def generateExpression(
    expr: TypedExpression,
    ctx: GenContext
  ): (GenContext, UUID) = expr match {

    case TypedExpression.VarRef(name, _) =>
      // Variable reference just looks up the existing node ID
      ctx.lookup(name) match {
        case Some(id) => (ctx, id)
        case None =>
          // This shouldn't happen after type checking, but handle gracefully
          throw new IllegalStateException(s"Undefined variable in IR generation: $name")
      }

    case TypedExpression.FunctionCall(name, signature, args) =>
      // Generate IR for each argument
      val (argsCtx, argIds) = args.foldLeft((ctx, List.empty[(String, UUID)])) {
        case ((currentCtx, ids), arg) =>
          val (newCtx, argId) = generateExpression(arg, currentCtx)
          val paramName = signature.params(ids.size)._1
          (newCtx, ids :+ (paramName -> argId))
      }

      val id = UUID.randomUUID()
      val node = IRNode.ModuleCall(
        id = id,
        moduleName = signature.moduleName,
        languageName = name,
        inputs = argIds.toMap,
        outputType = signature.returns
      )
      (argsCtx.addNode(node), id)

    case TypedExpression.Merge(left, right, semanticType) =>
      val (leftCtx, leftId) = generateExpression(left, ctx)
      val (rightCtx, rightId) = generateExpression(right, leftCtx)

      val id = UUID.randomUUID()
      val node = IRNode.MergeNode(id, leftId, rightId, semanticType)
      (rightCtx.addNode(node), id)

    case TypedExpression.Projection(source, fields, semanticType) =>
      val (sourceCtx, sourceId) = generateExpression(source, ctx)

      val id = UUID.randomUUID()
      val node = IRNode.ProjectNode(id, sourceId, fields, semanticType)
      (sourceCtx.addNode(node), id)

    case TypedExpression.Conditional(cond, thenBr, elseBr, semanticType) =>
      val (condCtx, condId) = generateExpression(cond, ctx)
      val (thenCtx, thenId) = generateExpression(thenBr, condCtx)
      val (elseCtx, elseId) = generateExpression(elseBr, thenCtx)

      val id = UUID.randomUUID()
      val node = IRNode.ConditionalNode(id, condId, thenId, elseId, semanticType)
      (elseCtx.addNode(node), id)

    case TypedExpression.Literal(value, semanticType) =>
      val id = UUID.randomUUID()
      val node = IRNode.LiteralNode(id, value, semanticType)
      (ctx.addNode(node), id)
  }
}
