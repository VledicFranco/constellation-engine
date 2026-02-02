package io.constellation.lang.semantic

/** Type checking mode for bidirectional type inference.
  *
  * Bidirectional type checking operates in two modes:
  *   - Inference (⇑): Synthesize a type from the expression structure (bottom-up)
  *   - Checking (⇓): Verify expression against an expected type (top-down)
  *
  * The key insight is that expected type information flows down into expressions, enabling type
  * inference for constructs that would otherwise need annotations.
  *
  * @see
  *   "Bidirectional Typing" by Dunfield & Krishnaswami (2021)
  * @see
  *   "Complete and Easy Bidirectional Typechecking" by Dunfield & Pfenning (2013)
  */
sealed trait Mode {

  /** Human-readable description of the mode for error messages */
  def describe: String
}

object Mode {

  /** Inference mode (⇑): Synthesize type from expression structure.
    *
    * Used when no expected type is available. The type checker must derive the type purely from the
    * expression's structure.
    *
    * Examples where inference mode is used:
    *   - `x = 42` (no type annotation, infer Int from literal)
    *   - `result = Process(data)` (infer from function return type)
    */
  case object Infer extends Mode {
    def describe: String = "inference mode"
  }

  /** Checking mode (⇓): Verify expression against expected type.
    *
    * Used when an expected type is available from context. The type checker can use this
    * information to:
    *   - Infer lambda parameter types
    *   - Type empty collections
    *   - Produce better error messages
    *
    * Examples where checking mode is used:
    *   - `x: Int = ...` (check expression against Int)
    *   - `Filter(users, u => u.active)` (check lambda against expected function type)
    *   - `defaults: List<Int> = []` (check empty list against List<Int>)
    */
  final case class Check(expected: SemanticType) extends Mode {
    def describe: String = s"checking mode (expected: ${expected.prettyPrint})"
  }

  /** Context information for error messages in bidirectional checking */
  sealed trait TypeContext {
    def describe: String
  }

  object TypeContext {

    /** At the top level, no specific context */
    case object TopLevel extends TypeContext {
      def describe: String = "at top level"
    }

    /** In a function argument position */
    final case class FunctionArgument(
        funcName: String,
        argIndex: Int,
        paramName: String
    ) extends TypeContext {
      def describe: String = s"in argument ${argIndex + 1} ('$paramName') of $funcName"
    }

    /** In a lambda body */
    final case class LambdaBody(expectedReturn: SemanticType) extends TypeContext {
      def describe: String = s"in lambda body (expected return: ${expectedReturn.prettyPrint})"
    }

    /** In a list element */
    final case class ListElement(index: Int, expectedElement: SemanticType) extends TypeContext {
      def describe: String = s"in list element $index (expected: ${expectedElement.prettyPrint})"
    }

    /** In a record field */
    final case class RecordField(fieldName: String, expectedType: SemanticType)
        extends TypeContext {
      def describe: String = s"in record field '$fieldName' (expected: ${expectedType.prettyPrint})"
    }

    /** In a conditional branch */
    final case class ConditionalBranch(branchType: String) extends TypeContext {
      def describe: String = s"in $branchType branch of conditional"
    }
  }
}
