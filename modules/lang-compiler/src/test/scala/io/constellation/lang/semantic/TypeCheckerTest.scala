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

  it should "report unsupported arithmetic error for incompatible types" in {
    val source = """
      in a: Int
      in b: String
      result = a + b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    // + with non-numeric/non-record types produces UnsupportedArithmetic
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.UnsupportedArithmetic]) shouldBe true
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

  // Namespace / Qualified Name tests

  it should "type check fully qualified function calls" in {
    val registry = FunctionRegistry.empty
    registry.register(FunctionSignature(
      name = "add",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "stdlib.add",
      namespace = Some("stdlib.math")
    ))

    val source = """
      in a: Int
      in b: Int
      result = stdlib.math.add(a, b)
      out result
    """
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check use declaration with wildcard import" in {
    val registry = FunctionRegistry.empty
    registry.register(FunctionSignature(
      name = "add",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "stdlib.add",
      namespace = Some("stdlib.math")
    ))

    val source = """
      use stdlib.math
      in a: Int
      in b: Int
      result = add(a, b)
      out result
    """
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check use declaration with alias" in {
    val registry = FunctionRegistry.empty
    registry.register(FunctionSignature(
      name = "add",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "stdlib.add",
      namespace = Some("stdlib.math")
    ))

    val source = """
      use stdlib.math as m
      in a: Int
      in b: Int
      result = m.add(a, b)
      out result
    """
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check multiple use declarations" in {
    val registry = FunctionRegistry.empty
    registry.register(FunctionSignature(
      name = "add",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "stdlib.add",
      namespace = Some("stdlib.math")
    ))
    registry.register(FunctionSignature(
      name = "upper",
      params = List("value" -> SemanticType.SString),
      returns = SemanticType.SString,
      moduleName = "stdlib.upper",
      namespace = Some("stdlib.string")
    ))

    val source = """
      use stdlib.math
      use stdlib.string as str
      in a: Int
      in b: Int
      in greeting: String
      sum = add(a, b)
      upper_greeting = str.upper(greeting)
      out sum
    """
    val result = check(source, registry)
    result.isRight shouldBe true
  }

  it should "report undefined namespace error for unknown namespace" in {
    val registry = FunctionRegistry.empty
    registry.register(FunctionSignature(
      name = "add",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "stdlib.add",
      namespace = Some("stdlib.math")
    ))

    val source = """
      in a: Int
      result = nonexistent.namespace.add(a, a)
      out result
    """
    val result = check(source, registry)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.UndefinedNamespace]) shouldBe true
  }

  it should "report ambiguous function error when multiple namespaces have same function" in {
    val registry = FunctionRegistry.empty
    registry.register(FunctionSignature(
      name = "process",
      params = List("x" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "ns1.process",
      namespace = Some("namespace1")
    ))
    registry.register(FunctionSignature(
      name = "process",
      params = List("x" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "ns2.process",
      namespace = Some("namespace2")
    ))

    val source = """
      use namespace1
      use namespace2
      in x: Int
      result = process(x)
      out result
    """
    val result = check(source, registry)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.AmbiguousFunction]) shouldBe true
  }

  it should "resolve unambiguous function from single wildcard import" in {
    val registry = FunctionRegistry.empty
    registry.register(FunctionSignature(
      name = "unique",
      params = List("x" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "myns.unique",
      namespace = Some("myns")
    ))

    val source = """
      use myns
      in x: Int
      result = unique(x)
      out result
    """
    val result = check(source, registry)
    result.isRight shouldBe true
  }

  it should "prefer imported function over requiring full qualification" in {
    val registry = FunctionRegistry.empty
    registry.register(FunctionSignature(
      name = "add",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "stdlib.add",
      namespace = Some("stdlib.math")
    ))

    // Without import, simple name shouldn't work if namespaces are defined
    val source = """
      in a: Int
      result = add(a, a)
      out result
    """
    // This should work for backwards compatibility when no imports are defined
    val result = check(source, registry)
    result.isRight shouldBe true
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

  it should "merge empty record with non-empty record" in {
    val source = """
      in a: {}
      in b: { x: Int, y: String }
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

  it should "merge non-empty record with empty record" in {
    val source = """
      in a: { x: Int, y: String }
      in b: {}
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

  it should "merge two empty records" in {
    val source = """
      in a: {}
      in b: {}
      result = a + b
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SRecord]
    outputType.fields shouldBe empty
  }

  it should "merge nested records (shallow merge)" in {
    val source = """
      in a: { outer: { inner: Int } }
      in b: { other: String }
      result = a + b
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SRecord]
    outputType.fields should have size 2
    outputType.fields("outer") shouldBe a[SemanticType.SRecord]
    outputType.fields("other") shouldBe SemanticType.SString
  }

  it should "right-hand side wins when nested record field conflicts" in {
    val source = """
      in a: { data: { x: Int } }
      in b: { data: { y: String } }
      result = a + b
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SRecord]
    outputType.fields should have size 1
    // Right-hand side wins completely - no deep merge
    val dataType = outputType.fields("data").asInstanceOf[SemanticType.SRecord]
    dataType.fields should have size 1
    dataType.fields("y") shouldBe SemanticType.SString
  }

  it should "chain multiple record merges" in {
    val source = """
      in a: { x: Int }
      in b: { y: String }
      in c: { z: Boolean }
      result = a + b + c
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SRecord]
    outputType.fields should have size 3
    outputType.fields("x") shouldBe SemanticType.SInt
    outputType.fields("y") shouldBe SemanticType.SString
    outputType.fields("z") shouldBe SemanticType.SBoolean
  }

  it should "report error for merging Int with record" in {
    val source = """
      in a: Int
      in b: { x: String }
      result = a + b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
  }

  it should "report error for merging record with Int" in {
    val source = """
      in a: { x: String }
      in b: Int
      result = a + b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
  }

  it should "report error for merging String with String using +" in {
    val source = """
      in a: String
      in b: String
      result = a + b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
  }

  it should "report error for merging List with record" in {
    val source = """
      in a: List<Int>
      in b: { x: String }
      result = a + b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
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

  // Field access tests

  it should "type check simple field access" in {
    val source = """
      in user: { name: String, age: Int }
      result = user.name
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SString
  }

  it should "type check chained field access" in {
    val source = """
      in person: { address: { city: String, zip: String } }
      result = person.address.city
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SString
  }

  it should "type check field access returning record type" in {
    val source = """
      in person: { name: String, address: { city: String } }
      result = person.address
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SRecord]
    outputType.fields should have size 1
    outputType.fields("city") shouldBe SemanticType.SString
  }

  it should "type check field access on Candidates (returns Candidates of field type)" in {
    val source = """
      type User = { name: String, score: Float }
      in users: Candidates<User>
      result = users.score
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SCandidates(SemanticType.SFloat)
  }

  it should "report invalid field access error for non-existent field" in {
    val source = """
      in data: { id: Int, name: String }
      result = data.nonexistent
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.InvalidFieldAccess]) shouldBe true
  }

  it should "report error for field access on non-record type" in {
    val source = """
      in x: Int
      result = x.field
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeError]) shouldBe true
  }

  it should "report invalid field access error for Candidates with non-existent field" in {
    val source = """
      type User = { name: String }
      in users: Candidates<User>
      result = users.nonexistent
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.InvalidFieldAccess]) shouldBe true
  }

  it should "type check field access combined with other operations" in {
    val source = """
      in a: { x: Int }
      in b: { y: Int }
      merged = a + b
      result = merged.x
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  // Comparison operator tests

  private def comparisonRegistry: FunctionRegistry = {
    val registry = FunctionRegistry.empty
    // Int comparison functions
    registry.register(FunctionSignature(
      name = "eq-int",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SBoolean,
      moduleName = "stdlib.eq-int",
      namespace = Some("stdlib.compare")
    ))
    registry.register(FunctionSignature(
      name = "lt",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SBoolean,
      moduleName = "stdlib.lt",
      namespace = Some("stdlib.compare")
    ))
    registry.register(FunctionSignature(
      name = "gt",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SBoolean,
      moduleName = "stdlib.gt",
      namespace = Some("stdlib.compare")
    ))
    registry.register(FunctionSignature(
      name = "lte",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SBoolean,
      moduleName = "stdlib.lte",
      namespace = Some("stdlib.compare")
    ))
    registry.register(FunctionSignature(
      name = "gte",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SBoolean,
      moduleName = "stdlib.gte",
      namespace = Some("stdlib.compare")
    ))
    // String comparison
    registry.register(FunctionSignature(
      name = "eq-string",
      params = List("a" -> SemanticType.SString, "b" -> SemanticType.SString),
      returns = SemanticType.SBoolean,
      moduleName = "stdlib.eq-string",
      namespace = Some("stdlib.compare")
    ))
    // Boolean not for NotEq
    registry.register(FunctionSignature(
      name = "not",
      params = List("value" -> SemanticType.SBoolean),
      returns = SemanticType.SBoolean,
      moduleName = "stdlib.not",
      namespace = Some("stdlib.bool")
    ))
    registry
  }

  it should "type check equality comparison for Int (==)" in {
    val source = """
      in a: Int
      in b: Int
      result = a == b
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check less than comparison (<)" in {
    val source = """
      in a: Int
      in b: Int
      result = a < b
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check greater than comparison (>)" in {
    val source = """
      in a: Int
      in b: Int
      result = a > b
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check less than or equal comparison (<=)" in {
    val source = """
      in a: Int
      in b: Int
      result = a <= b
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check greater than or equal comparison (>=)" in {
    val source = """
      in a: Int
      in b: Int
      result = a >= b
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check inequality comparison for Int (!=)" in {
    val source = """
      in a: Int
      in b: Int
      result = a != b
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check equality comparison for String (==)" in {
    val source = """
      in a: String
      in b: String
      result = a == b
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check inequality comparison for String (!=)" in {
    val source = """
      in a: String
      in b: String
      result = a != b
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "report error for comparison with mismatched types" in {
    val source = """
      in a: Int
      in b: String
      result = a == b
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "report error for unsupported comparison (< on String)" in {
    val source = """
      in a: String
      in b: String
      result = a < b
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.UnsupportedComparison]) shouldBe true
  }

  it should "type check comparison with literal" in {
    val source = """
      in x: Int
      result = x == 42
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check comparison in conditional" in {
    val source = """
      in x: Int
      in a: Int
      in b: Int
      result = if (x > 0) a else b
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  // Boolean operator tests

  it should "type check 'and' operator" in {
    val source = """
      in a: Boolean
      in b: Boolean
      result = a and b
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check 'or' operator" in {
    val source = """
      in a: Boolean
      in b: Boolean
      result = a or b
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check 'not' operator" in {
    val source = """
      in a: Boolean
      result = not a
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check chained boolean operators" in {
    val source = """
      in a: Boolean
      in b: Boolean
      in c: Boolean
      result = a and b or c
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check double negation" in {
    val source = """
      in a: Boolean
      result = not not a
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check boolean operators with comparison operators" in {
    val source = """
      in x: Int
      in y: Int
      in z: Int
      result = x < y and y < z
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check complex boolean expression with parentheses" in {
    val source = """
      in a: Boolean
      in b: Boolean
      in c: Boolean
      result = (a or b) and c
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check not with comparison" in {
    val source = """
      in x: Int
      in y: Int
      result = not x == y
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "report error for 'and' with non-boolean left operand" in {
    val source = """
      in a: Int
      in b: Boolean
      result = a and b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "report error for 'and' with non-boolean right operand" in {
    val source = """
      in a: Boolean
      in b: String
      result = a and b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "report error for 'or' with non-boolean operands" in {
    val source = """
      in a: Int
      in b: Int
      result = a or b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "report error for 'not' with non-boolean operand" in {
    val source = """
      in a: String
      result = not a
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "type check boolean operators with boolean literals" in {
    val source = """
      in flag: Boolean
      result = flag and true or false
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check boolean expression in conditional" in {
    val source = """
      in a: Boolean
      in b: Boolean
      in x: Int
      in y: Int
      result = if (a and b) x else y
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }
}
