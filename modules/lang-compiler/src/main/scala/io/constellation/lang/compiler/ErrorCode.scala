package io.constellation.lang.compiler

import io.constellation.lang.ast.CompileError

/** Error category for grouping related errors */
enum ErrorCategory:
  case Syntax    // Parse/syntax errors (E020-E029)
  case Type      // Type-related errors (E010-E019)
  case Reference // Undefined variable/function/type (E001-E009)
  case Semantic  // Semantic errors like duplicates, cycles (E030-E039)
  case Internal  // Internal compiler errors (E900+)

/** Error code with structured information for improved error messages.
  *
  * Each error code provides:
  *   - A unique identifier (e.g., E001)
  *   - A short title describing the error
  *   - A detailed explanation of why this error occurs
  *   - An optional documentation path for more information
  */
sealed trait ErrorCode {
  def code: String
  def title: String
  def category: ErrorCategory
  def explanation: String
  def docPath: Option[String]

  /** Full documentation URL */
  def docUrl: Option[String] =
    docPath.map(p => s"https://constellation-engine.dev/docs/$p")
}

object ErrorCodes {

  // ========== Reference Errors (E001-E009) ==========

  case object UndefinedVariable extends ErrorCode {
    val code     = "E001"
    val title    = "Undefined variable"
    val category = ErrorCategory.Reference
    val explanation = """The variable you're trying to use has not been declared.
                        |
                        |Variables must be declared before use:
                        |  - As an input: in variableName: Type
                        |  - As an assignment: variableName = SomeModule(...)""".stripMargin.trim
    val docPath = Some("constellation-lang/declarations")
  }

  case object UndefinedFunction extends ErrorCode {
    val code     = "E002"
    val title    = "Undefined function"
    val category = ErrorCategory.Reference
    val explanation = """The function you're trying to call is not registered.
                        |
                        |Make sure the function is:
                        |  - Spelled correctly (function names are case-sensitive)
                        |  - Registered with the compiler via StdLib or custom modules
                        |  - Imported if it's from a namespace: use stdlib.math""".stripMargin.trim
    val docPath = Some("constellation-lang/functions")
  }

  case object UndefinedType extends ErrorCode {
    val code     = "E003"
    val title    = "Undefined type"
    val category = ErrorCategory.Reference
    val explanation = """The type you specified is not defined.
                        |
                        |Built-in types: String, Int, Float, Boolean
                        |Collections: List<T>, Map<K, V>, Optional<T>
                        |Custom types must be declared: type MyType = { field: Type }""".stripMargin.trim
    val docPath = Some("constellation-lang/types")
  }

  case object UndefinedNamespace extends ErrorCode {
    val code     = "E004"
    val title    = "Undefined namespace"
    val category = ErrorCategory.Reference
    val explanation = """The namespace you're trying to use is not registered.
                        |
                        |Available namespaces depend on what modules are loaded.
                        |Common namespaces: stdlib, stdlib.math, stdlib.string""".stripMargin.trim
    val docPath = Some("constellation-lang/namespaces")
  }

  case object AmbiguousFunction extends ErrorCode {
    val code     = "E005"
    val title    = "Ambiguous function reference"
    val category = ErrorCategory.Reference
    val explanation = """Multiple functions match this name.
                        |
                        |Use the fully qualified name to disambiguate:
                        |  - stdlib.math.add instead of just add
                        |  - Or use an alias: use stdlib.math as m, then m.add""".stripMargin.trim
    val docPath = Some("constellation-lang/namespaces")
  }

  case object InvalidProjection extends ErrorCode {
    val code     = "E006"
    val title    = "Invalid projection"
    val category = ErrorCategory.Reference
    val explanation = """The field you're trying to project doesn't exist on this type.
                        |
                        |Projection syntax: record[field1, field2]
                        |This creates a new record with only the specified fields.""".stripMargin.trim
    val docPath = Some("constellation-lang/expressions")
  }

  case object InvalidFieldAccess extends ErrorCode {
    val code     = "E007"
    val title    = "Invalid field access"
    val category = ErrorCategory.Reference
    val explanation = """The field you're trying to access doesn't exist on this type.
                        |
                        |Field access syntax: record.fieldName
                        |The source expression must be a record type with that field.""".stripMargin.trim
    val docPath = Some("constellation-lang/expressions")
  }

  // ========== Type Errors (E010-E019) ==========

  case object TypeMismatch extends ErrorCode {
    val code     = "E010"
    val title    = "Type mismatch"
    val category = ErrorCategory.Type
    val explanation = """The actual type does not match the expected type.
                        |
                        |This often happens when:
                        |  - Passing wrong argument type to a function
                        |  - Assigning incompatible value to a variable
                        |  - Returning wrong type from a conditional""".stripMargin.trim
    val docPath = Some("constellation-lang/type-system")
  }

  case object IncompatibleOperator extends ErrorCode {
    val code     = "E011"
    val title    = "Incompatible types for operator"
    val category = ErrorCategory.Type
    val explanation = """The operator cannot be applied to these types.
                        |
                        |Operators and supported types:
                        |  - Arithmetic (+, -, *, /): Int, Float
                        |  - Comparison (==, !=, <, >): Int, Float, String, Boolean
                        |  - Boolean (and, or, not): Boolean
                        |  - Merge (+): Records, Candidates""".stripMargin.trim
    val docPath = Some("constellation-lang/operators")
  }

  case object IncompatibleMerge extends ErrorCode {
    val code     = "E012"
    val title    = "Incompatible types for merge"
    val category = ErrorCategory.Type
    val explanation = """Cannot merge these types with the + operator.
                        |
                        |The merge operator requires compatible types:
                        |  - Two records (fields are merged)
                        |  - Two Candidates (element-wise merge)
                        |  - Candidates + Record (broadcast)
                        |  - Record + Candidates (broadcast)""".stripMargin.trim
    val docPath = Some("constellation-lang/operators")
  }

  case object UnsupportedComparison extends ErrorCode {
    val code     = "E013"
    val title    = "Unsupported comparison"
    val category = ErrorCategory.Type
    val explanation = """This comparison operator is not supported for these types.
                        |
                        |Comparison operators (==, !=, <, >, <=, >=) work with:
                        |  - Int and Int
                        |  - Float and Float
                        |  - String and String (lexicographic)
                        |  - Boolean and Boolean (== and != only)""".stripMargin.trim
    val docPath = Some("constellation-lang/operators")
  }

  case object UnsupportedArithmetic extends ErrorCode {
    val code     = "E014"
    val title    = "Unsupported arithmetic"
    val category = ErrorCategory.Type
    val explanation = """Arithmetic operators are not supported for these types.
                        |
                        |Arithmetic operators (+, -, *, /) work with:
                        |  - Int and Int -> Int
                        |  - Float and Float -> Float
                        |  - Int and Float (or vice versa) -> Float""".stripMargin.trim
    val docPath = Some("constellation-lang/operators")
  }

  case object GeneralTypeError extends ErrorCode {
    val code     = "E015"
    val title    = "Type error"
    val category = ErrorCategory.Type
    val explanation = """A type-related error occurred during compilation.
                        |
                        |Check that all expressions have compatible types.""".stripMargin.trim
    val docPath = Some("constellation-lang/type-system")
  }

  case object InvalidOptionValue extends ErrorCode {
    val code     = "E016"
    val title    = "Invalid option value"
    val category = ErrorCategory.Type
    val explanation = """The value provided for a module call option is invalid.
                        |
                        |Option value constraints:
                        |  - retry: must be >= 0
                        |  - timeout, delay, cache: must be > 0
                        |  - concurrency: must be > 0
                        |  - throttle count: must be > 0""".stripMargin.trim
    val docPath = Some("constellation-lang/module-options")
  }

  case object FallbackTypeMismatch extends ErrorCode {
    val code     = "E017"
    val title    = "Fallback type mismatch"
    val category = ErrorCategory.Type
    val explanation = """The fallback expression type doesn't match the module return type.
                        |
                        |The fallback option provides a default value when the module fails.
                        |Its type must be compatible with what the module returns.
                        |
                        |Example:
                        |  result = GetName(id) with fallback: "Unknown"
                        |  If GetName returns String, "Unknown" is valid.
                        |  If GetName returns Int, "Unknown" would be invalid.""".stripMargin.trim
    val docPath = Some("constellation-lang/module-options")
  }

  // ========== Syntax Errors (E020-E029) ==========

  case object ParseError extends ErrorCode {
    val code     = "E020"
    val title    = "Syntax error"
    val category = ErrorCategory.Syntax
    val explanation = """The parser encountered invalid syntax.
                        |
                        |Check for:
                        |  - Missing or extra parentheses
                        |  - Missing commas between arguments
                        |  - Typos in keywords
                        |  - Unclosed strings or brackets""".stripMargin.trim
    val docPath = Some("constellation-lang/syntax")
  }

  case object UnexpectedToken extends ErrorCode {
    val code     = "E021"
    val title    = "Unexpected token"
    val category = ErrorCategory.Syntax
    val explanation = """The parser found a token it didn't expect at this position.
                        |
                        |This usually indicates a syntax error nearby.""".stripMargin.trim
    val docPath = Some("constellation-lang/syntax")
  }

  // ========== Semantic Errors (E030-E039) ==========

  case object DuplicateDefinition extends ErrorCode {
    val code     = "E030"
    val title    = "Duplicate definition"
    val category = ErrorCategory.Semantic
    val explanation = """This name is already defined in the current scope.
                        |
                        |Each variable, type, and input must have a unique name.""".stripMargin.trim
    val docPath = Some("constellation-lang/declarations")
  }

  case object CircularDependency extends ErrorCode {
    val code     = "E031"
    val title    = "Circular dependency"
    val category = ErrorCategory.Semantic
    val explanation = """A circular dependency was detected in the DAG.
                        |
                        |Variables cannot depend on themselves, directly or indirectly.""".stripMargin.trim
    val docPath = Some("constellation-lang/dag")
  }

  // ========== Internal Errors (E900+) ==========

  case object InternalError extends ErrorCode {
    val code     = "E900"
    val title    = "Internal compiler error"
    val category = ErrorCategory.Internal
    val explanation = """An unexpected error occurred in the compiler.
                        |
                        |This is a bug in the compiler. Please report it at:
                        |https://github.com/VledicFranco/constellation-engine/issues""".stripMargin.trim
    val docPath = None
  }

  /** All defined error codes for lookup and iteration */
  val all: List[ErrorCode] = List(
    UndefinedVariable,
    UndefinedFunction,
    UndefinedType,
    UndefinedNamespace,
    AmbiguousFunction,
    InvalidProjection,
    InvalidFieldAccess,
    TypeMismatch,
    IncompatibleOperator,
    IncompatibleMerge,
    UnsupportedComparison,
    UnsupportedArithmetic,
    GeneralTypeError,
    InvalidOptionValue,
    FallbackTypeMismatch,
    ParseError,
    UnexpectedToken,
    DuplicateDefinition,
    CircularDependency,
    InternalError
  )

  /** Map a CompileError to its corresponding ErrorCode */
  def fromCompileError(error: CompileError): ErrorCode = error match {
    case _: CompileError.UndefinedVariable     => UndefinedVariable
    case _: CompileError.UndefinedFunction     => UndefinedFunction
    case _: CompileError.UndefinedType         => UndefinedType
    case _: CompileError.UndefinedNamespace    => UndefinedNamespace
    case _: CompileError.AmbiguousFunction     => AmbiguousFunction
    case _: CompileError.InvalidProjection     => InvalidProjection
    case _: CompileError.InvalidFieldAccess    => InvalidFieldAccess
    case _: CompileError.TypeMismatch          => TypeMismatch
    case _: CompileError.IncompatibleMerge     => IncompatibleMerge
    case _: CompileError.UnsupportedComparison => UnsupportedComparison
    case _: CompileError.UnsupportedArithmetic => UnsupportedArithmetic
    case _: CompileError.InvalidOptionValue    => InvalidOptionValue
    case _: CompileError.FallbackTypeMismatch  => FallbackTypeMismatch
    case _: CompileError.TypeError             => GeneralTypeError
    case _: CompileError.ParseError            => ParseError
    case _: CompileError.InternalError         => InternalError
    case _: CompileError.NonExhaustiveMatch    => GeneralTypeError
    case _: CompileError.PatternTypeMismatch   => TypeMismatch
    case _: CompileError.InvalidPattern        => GeneralTypeError
  }
}
