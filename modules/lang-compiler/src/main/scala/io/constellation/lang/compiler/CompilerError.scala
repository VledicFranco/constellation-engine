package io.constellation.lang.compiler

import java.util.UUID

/** Errors that can occur during DAG compilation.
  *
  * These are compile-time errors (not runtime), representing invalid IR structures
  * or unsupported operations discovered during compilation.
  */
sealed trait CompilerError {
  def message: String
}

object CompilerError {

  /** A node reference in the IR could not be resolved */
  final case class NodeNotFound(nodeId: UUID, context: String) extends CompilerError {
    def message: String = s"Node $nodeId not found in $context"
  }

  /** A lambda parameter was referenced but not bound */
  final case class LambdaParameterNotBound(paramName: String) extends CompilerError {
    def message: String = s"Lambda parameter '$paramName' not bound"
  }

  /** An operation is not yet implemented */
  final case class UnsupportedOperation(operation: String) extends CompilerError {
    def message: String = s"$operation is not yet implemented"
  }

  /** Attempted field access on a non-record value */
  final case class InvalidFieldAccess(field: String, actualType: String) extends CompilerError {
    def message: String = s"Cannot access field '$field' on $actualType"
  }

  /** An IR node type is not supported in the given context */
  final case class UnsupportedNodeType(nodeType: String, context: String) extends CompilerError {
    def message: String = s"Unsupported node type '$nodeType' in $context"
  }

  /** A built-in function is not supported in lambda bodies */
  final case class UnsupportedFunction(moduleName: String, funcName: String) extends CompilerError {
    def message: String = s"Unsupported function in lambda body: $moduleName (funcName=$funcName)"
  }

  /** Convert a CompilerError to an exception for backwards compatibility at API boundaries */
  def toException(error: CompilerError): IllegalStateException =
    new IllegalStateException(error.message)
}
