package io.constellation.lang.semantic

import io.constellation.lang.ast.*
import io.constellation.lang.parser.ConstellationParser
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TypeCheckerTest extends AnyFlatSpec with Matchers {

  private def parse(source: String): Program =
    ConstellationParser.parse(source).getOrElse(fail("Parse failed"))

  private def check(source: String, registry: FunctionRegistry = FunctionRegistry.empty): Either[List[CompileError], TypedProgram] =
    TypeChecker.check(parse(source), registry)

  /** Helper to get the output type (assumes single output) */
  private def getOutputType(program: TypedProgram): SemanticType =
    program.outputs.head._2

  "TypeChecker" should "type check simple primitive inputs" in {
    val source = """
      in x: Int
      in y: String
      out x
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check record types" in {
    val source = """
      type Person = { name: String, age: Int }
      in p: Person
      out p
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe a[SemanticType.SRecord]

    val record = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SRecord]
    record.fields should have size 2
    record.fields("name") shouldBe SemanticType.SString
    record.fields("age") shouldBe SemanticType.SInt
  }

  it should "type check parameterized types" in {
    val source = """
      type Item = { id: String }
      in items: Candidates<Item>
      out items
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe a[SemanticType.SCandidates]
  }

  it should "type check List types" in {
    val source = """
      in data: List<Int>
      out data
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SList(SemanticType.SInt)
  }

  it should "type check Map types" in {
    val source = """
      in data: Map<String, Int>
      out data
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SMap(SemanticType.SString, SemanticType.SInt)
  }

  it should "type check assignments" in {
    val source = """
      in x: Int
      y = x
      out y
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check record merge expressions" in {
    val source = """
      in a: { x: Int }
      in b: { y: String }
      result = a + b
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SRecord]
    outputType.fields should have size 2
    outputType.fields("x") shouldBe SemanticType.SInt
    outputType.fields("y") shouldBe SemanticType.SString
  }

  it should "type check candidates merge expressions" in {
    val source = """
      type A = { x: Int }
      type B = { y: String }
      in a: Candidates<A>
      in b: Candidates<B>
      result = a + b
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields("x") shouldBe SemanticType.SInt
    elementType.fields("y") shouldBe SemanticType.SString
  }

  it should "type check projection expressions" in {
    val source = """
      in data: { id: Int, name: String, email: String }
      result = data[id, name]
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SRecord]
    outputType.fields should have size 2
    outputType.fields.keys should contain allOf ("id", "name")
    outputType.fields.keys should not contain "email"
  }

  it should "type check projection on Candidates" in {
    val source = """
      type Item = { id: Int, name: String, extra: String }
      in items: Candidates<Item>
      result = items[id, name]
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields should have size 2
  }

  it should "type check conditional expressions" in {
    val source = """
      in flag: Boolean
      in a: Int
      in b: Int
      result = if (flag) a else b
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check string literals" in {
    val source = """
      x = "hello"
      out x
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SString
  }

  it should "type check integer literals" in {
    val source = """
      x = 42
      out x
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check float literals" in {
    val source = """
      x = 3.14
      out x
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SFloat
  }

  it should "type check boolean literals" in {
    val source = """
      x = true
      out x
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check function calls with registry" in {
    val registry = FunctionRegistry.empty
    registry.register(FunctionSignature(
      name = "double",
      params = List("n" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "double-module"
    ))

    val source = """
      in x: Int
      result = double(x)
      out result
    """
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check multi-argument function calls" in {
    val registry = FunctionRegistry.empty
    registry.register(FunctionSignature(
      name = "add",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "add-module"
    ))

    val source = """
      in x: Int
      in y: Int
      result = add(x, y)
      out result
    """
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check the example program from design doc" in {
    val registry = FunctionRegistry.empty

    // Register the functions used in the example
    val communicationType = SemanticType.SRecord(Map(
      "communicationId" -> SemanticType.SString,
      "contentBlocks" -> SemanticType.SList(SemanticType.SString),
      "channel" -> SemanticType.SString
    ))

    val embeddingsType = SemanticType.SCandidates(SemanticType.SRecord(Map(
      "embedding" -> SemanticType.SList(SemanticType.SFloat)
    )))

    val scoresType = SemanticType.SCandidates(SemanticType.SRecord(Map(
      "score" -> SemanticType.SFloat
    )))

    registry.register(FunctionSignature(
      name = "ide-ranker-v2-candidate-embed",
      params = List("input" -> SemanticType.SCandidates(communicationType)),
      returns = embeddingsType,
      moduleName = "ide-ranker-v2-candidate-embed"
    ))

    registry.register(FunctionSignature(
      name = "ide-ranker-v2-precomputed-embeddings",
      params = List(
        "data" -> SemanticType.SCandidates(SemanticType.SRecord(Map.empty)), // Will be merged
        "userId" -> SemanticType.SInt
      ),
      returns = scoresType,
      moduleName = "ide-ranker-v2-precomputed-embeddings"
    ))

    val source = """
      type Communication = {
        communicationId: String,
        contentBlocks: List<String>,
        channel: String
      }

      in communications: Candidates<Communication>
      in mappedUserId: Int

      embeddings = ide-ranker-v2-candidate-embed(communications)
      out embeddings
    """

    val result = check(source, registry)
    result.isRight shouldBe true
  }

  // Error cases

  it should "report undefined variable error" in {
    val source = """
      out undefined_var
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.UndefinedVariable]) shouldBe true
  }

  it should "report undefined type error" in {
    val source = """
      in x: NonExistentType
      out x
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.UndefinedType]) shouldBe true
  }

  it should "report undefined function error" in {
    val source = """
      in x: Int
      result = unknown_function(x)
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.UndefinedFunction]) shouldBe true
  }

  it should "report type mismatch error for function arguments" in {
    val registry = FunctionRegistry.empty
    registry.register(FunctionSignature(
      name = "expects-int",
      params = List("n" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "expects-int"
    ))

    val source = """
      in x: String
      result = expects-int(x)
      out result
    """
    val result = check(source, registry)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "report wrong argument count error" in {
    val registry = FunctionRegistry.empty
    registry.register(FunctionSignature(
      name = "two-args",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "two-args"
    ))

    val source = """
      in x: Int
      result = two-args(x)
      out result
    """
    val result = check(source, registry)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeError]) shouldBe true
  }

  it should "report invalid projection error" in {
    val source = """
      in data: { id: Int, name: String }
      result = data[id, nonexistent]
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.InvalidProjection]) shouldBe true
  }

  it should "report projection on non-record error" in {
    val source = """
      in x: Int
      result = x[field]
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeError]) shouldBe true
  }

  it should "report incompatible merge error" in {
    val source = """
      in a: Int
      in b: String
      result = a + b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.IncompatibleMerge]) shouldBe true
  }

  it should "report conditional with non-boolean condition error" in {
    val source = """
      in flag: Int
      in a: Int
      in b: Int
      result = if (flag) a else b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "report conditional with mismatched branch types error" in {
    val source = """
      in flag: Boolean
      in a: Int
      in b: String
      result = if (flag) a else b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "include position information in errors" in {
    val source = """
      out undefined_var
    """
    val result = check(source)
    result.isLeft shouldBe true
    val error = result.left.toOption.get.head
    error.span.isDefined shouldBe true
  }

  // Type merge semantics

  it should "right-hand side wins on field conflicts in merge" in {
    val source = """
      in a: { x: Int, y: Int }
      in b: { y: String, z: String }
      result = a + b
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SRecord]
    outputType.fields should have size 3
    outputType.fields("x") shouldBe SemanticType.SInt
    outputType.fields("y") shouldBe SemanticType.SString // Right wins
    outputType.fields("z") shouldBe SemanticType.SString
  }

  it should "merge Candidates with record" in {
    val source = """
      type A = { x: Int }
      in a: Candidates<A>
      in b: { y: String }
      result = a + b
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields("x") shouldBe SemanticType.SInt
    elementType.fields("y") shouldBe SemanticType.SString
  }

  it should "merge record with Candidates" in {
    val source = """
      type A = { y: String }
      in a: { x: Int }
      in b: Candidates<A>
      result = a + b
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields should have size 2
  }

  // Type reference

  it should "resolve type references" in {
    val source = """
      type Base = { id: Int }
      type Extended = Base + { name: String }
      in data: Extended
      out data
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SRecord]
    outputType.fields should have size 2
    outputType.fields("id") shouldBe SemanticType.SInt
    outputType.fields("name") shouldBe SemanticType.SString
  }
}
