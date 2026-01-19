package io.constellation.lang.semantic

import io.constellation.CType
import io.constellation.lang.ast.*
import io.constellation.lang.parser.ConstellationParser
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TypeCheckerTest extends AnyFlatSpec with Matchers {

  private def parse(source: String): Program =
    ConstellationParser.parse(source).getOrElse(fail("Parse failed"))

  private def check(
      source: String,
      registry: FunctionRegistry = FunctionRegistry.empty
  ): Either[List[CompileError], TypedProgram] =
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
    getOutputType(result.toOption.get) shouldBe SemanticType.SMap(
      SemanticType.SString,
      SemanticType.SInt
    )
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

    val outputType  = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
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

    val outputType  = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields should have size 2
  }

  it should "type check projection with curly brace syntax" in {
    val source = """
      in data: { id: Int, name: String, email: String }
      result = data{id, name}
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SRecord]
    outputType.fields should have size 2
    outputType.fields.keys should contain allOf ("id", "name")
    outputType.fields.keys should not contain "email"
  }

  it should "type check single field projection with curly braces" in {
    val source = """
      in data: { id: Int, name: String }
      result = data{id}
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SRecord]
    outputType.fields should have size 1
    outputType.fields("id") shouldBe SemanticType.SInt
  }

  it should "type check curly brace projection on Candidates" in {
    val source = """
      type Item = { id: Int, name: String, extra: String }
      in items: Candidates<Item>
      result = items{id, name}
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType  = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields should have size 2
    elementType.fields.keys should contain allOf ("id", "name")
  }

  it should "report error for unknown field in curly brace projection" in {
    val source = """
      in data: { id: Int, name: String }
      result = data{id, unknown}
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
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
    registry.register(
      FunctionSignature(
        name = "double",
        params = List("n" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "double-module"
      )
    )

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
    registry.register(
      FunctionSignature(
        name = "add",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "add-module"
      )
    )

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
    val communicationType = SemanticType.SRecord(
      Map(
        "communicationId" -> SemanticType.SString,
        "contentBlocks"   -> SemanticType.SList(SemanticType.SString),
        "channel"         -> SemanticType.SString
      )
    )

    val embeddingsType = SemanticType.SCandidates(
      SemanticType.SRecord(
        Map(
          "embedding" -> SemanticType.SList(SemanticType.SFloat)
        )
      )
    )

    val scoresType = SemanticType.SCandidates(
      SemanticType.SRecord(
        Map(
          "score" -> SemanticType.SFloat
        )
      )
    )

    registry.register(
      FunctionSignature(
        name = "ide-ranker-v2-candidate-embed",
        params = List("input" -> SemanticType.SCandidates(communicationType)),
        returns = embeddingsType,
        moduleName = "ide-ranker-v2-candidate-embed"
      )
    )

    registry.register(
      FunctionSignature(
        name = "ide-ranker-v2-precomputed-embeddings",
        params = List(
          "data"   -> SemanticType.SCandidates(SemanticType.SRecord(Map.empty)), // Will be merged
          "userId" -> SemanticType.SInt
        ),
        returns = scoresType,
        moduleName = "ide-ranker-v2-precomputed-embeddings"
      )
    )

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
    registry.register(
      FunctionSignature(
        name = "expects-int",
        params = List("n" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "expects-int"
      )
    )

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
    registry.register(
      FunctionSignature(
        name = "two-args",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "two-args"
      )
    )

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
    result.left.toOption.get
      .exists(_.isInstanceOf[CompileError.UnsupportedArithmetic]) shouldBe true
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
    registry.register(
      FunctionSignature(
        name = "add",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "stdlib.add",
        namespace = Some("stdlib.math")
      )
    )

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
    registry.register(
      FunctionSignature(
        name = "add",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "stdlib.add",
        namespace = Some("stdlib.math")
      )
    )

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
    registry.register(
      FunctionSignature(
        name = "add",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "stdlib.add",
        namespace = Some("stdlib.math")
      )
    )

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
    registry.register(
      FunctionSignature(
        name = "add",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "stdlib.add",
        namespace = Some("stdlib.math")
      )
    )
    registry.register(
      FunctionSignature(
        name = "upper",
        params = List("value" -> SemanticType.SString),
        returns = SemanticType.SString,
        moduleName = "stdlib.upper",
        namespace = Some("stdlib.string")
      )
    )

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
    registry.register(
      FunctionSignature(
        name = "add",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "stdlib.add",
        namespace = Some("stdlib.math")
      )
    )

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
    registry.register(
      FunctionSignature(
        name = "process",
        params = List("x" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "ns1.process",
        namespace = Some("namespace1")
      )
    )
    registry.register(
      FunctionSignature(
        name = "process",
        params = List("x" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "ns2.process",
        namespace = Some("namespace2")
      )
    )

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
    registry.register(
      FunctionSignature(
        name = "unique",
        params = List("x" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "myns.unique",
        namespace = Some("myns")
      )
    )

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
    registry.register(
      FunctionSignature(
        name = "add",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "stdlib.add",
        namespace = Some("stdlib.math")
      )
    )

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

    val outputType  = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
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

    val outputType  = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
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
    registry.register(
      FunctionSignature(
        name = "eq-int",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.eq-int",
        namespace = Some("stdlib.compare")
      )
    )
    registry.register(
      FunctionSignature(
        name = "lt",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.lt",
        namespace = Some("stdlib.compare")
      )
    )
    registry.register(
      FunctionSignature(
        name = "gt",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.gt",
        namespace = Some("stdlib.compare")
      )
    )
    registry.register(
      FunctionSignature(
        name = "lte",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.lte",
        namespace = Some("stdlib.compare")
      )
    )
    registry.register(
      FunctionSignature(
        name = "gte",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.gte",
        namespace = Some("stdlib.compare")
      )
    )
    // String comparison
    registry.register(
      FunctionSignature(
        name = "eq-string",
        params = List("a" -> SemanticType.SString, "b" -> SemanticType.SString),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.eq-string",
        namespace = Some("stdlib.compare")
      )
    )
    // Boolean not for NotEq
    registry.register(
      FunctionSignature(
        name = "not",
        params = List("value" -> SemanticType.SBoolean),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.not",
        namespace = Some("stdlib.bool")
      )
    )
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
    result.left.toOption.get
      .exists(_.isInstanceOf[CompileError.UnsupportedComparison]) shouldBe true
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

  // Candidates + Candidates edge cases

  it should "merge Candidates with empty inner record with Candidates with non-empty record" in {
    val source = """
      in a: Candidates<{}>
      in b: Candidates<{ x: Int }>
      result = a + b
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType  = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields should have size 1
    elementType.fields("x") shouldBe SemanticType.SInt
  }

  it should "merge Candidates with non-empty inner record with Candidates with empty record" in {
    val source = """
      in a: Candidates<{ x: Int }>
      in b: Candidates<{}>
      result = a + b
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType  = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields should have size 1
    elementType.fields("x") shouldBe SemanticType.SInt
  }

  it should "merge two Candidates with empty inner records" in {
    val source = """
      in a: Candidates<{}>
      in b: Candidates<{}>
      result = a + b
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType  = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields shouldBe empty
  }

  it should "merge Candidates with overlapping fields (right wins)" in {
    val source = """
      in a: Candidates<{ id: Int, value: Int }>
      in b: Candidates<{ value: String, extra: Boolean }>
      result = a + b
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType  = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields should have size 3
    elementType.fields("id") shouldBe SemanticType.SInt
    elementType.fields("value") shouldBe SemanticType.SString // Right wins
    elementType.fields("extra") shouldBe SemanticType.SBoolean
  }

  it should "chain multiple Candidates merges" in {
    val source = """
      in a: Candidates<{ x: Int }>
      in b: Candidates<{ y: String }>
      in c: Candidates<{ z: Boolean }>
      result = a + b + c
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType  = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields should have size 3
    elementType.fields("x") shouldBe SemanticType.SInt
    elementType.fields("y") shouldBe SemanticType.SString
    elementType.fields("z") shouldBe SemanticType.SBoolean
  }

  // Candidates + Record broadcast merge edge cases

  it should "broadcast empty record to Candidates elements" in {
    val source = """
      in items: Candidates<{ id: Int, name: String }>
      in empty: {}
      result = items + empty
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType  = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields should have size 2
    elementType.fields("id") shouldBe SemanticType.SInt
    elementType.fields("name") shouldBe SemanticType.SString
  }

  it should "broadcast record to Candidates with empty inner type" in {
    val source = """
      in items: Candidates<{}>
      in context: { userId: Int }
      result = items + context
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType  = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields should have size 1
    elementType.fields("userId") shouldBe SemanticType.SInt
  }

  it should "handle overlapping fields in Candidates + Record (right wins)" in {
    val source = """
      in items: Candidates<{ id: Int, value: Int }>
      in context: { value: String, extra: Boolean }
      result = items + context
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType  = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields should have size 3
    elementType.fields("id") shouldBe SemanticType.SInt
    elementType.fields("value") shouldBe SemanticType.SString // Right (Record) wins
    elementType.fields("extra") shouldBe SemanticType.SBoolean
  }

  it should "handle overlapping fields in Record + Candidates (right wins)" in {
    val source = """
      in context: { value: Int, extra: Boolean }
      in items: Candidates<{ id: Int, value: String }>
      result = context + items
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType  = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields should have size 3
    elementType.fields("id") shouldBe SemanticType.SInt
    elementType.fields("value") shouldBe SemanticType.SString // Right (Candidates elem) wins
    elementType.fields("extra") shouldBe SemanticType.SBoolean
  }

  it should "chain Candidates + Record + Candidates merges" in {
    val source = """
      in a: Candidates<{ x: Int }>
      in b: { y: String }
      in c: Candidates<{ z: Boolean }>
      result = a + b + c
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType  = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields should have size 3
    elementType.fields("x") shouldBe SemanticType.SInt
    elementType.fields("y") shouldBe SemanticType.SString
    elementType.fields("z") shouldBe SemanticType.SBoolean
  }

  it should "chain Record + Candidates + Record merges" in {
    val source = """
      in a: { x: Int }
      in b: Candidates<{ y: String }>
      in c: { z: Boolean }
      result = a + b + c
      out result
    """
    val result = check(source)
    result.isRight shouldBe true

    val outputType  = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    val elementType = outputType.element.asInstanceOf[SemanticType.SRecord]
    elementType.fields should have size 3
    elementType.fields("x") shouldBe SemanticType.SInt
    elementType.fields("y") shouldBe SemanticType.SString
    elementType.fields("z") shouldBe SemanticType.SBoolean
  }

  // Guard expression tests

  it should "type check guard expression returning Optional<T>" in {
    val source = """
      in value: Int
      in isActive: Boolean
      result = value when isActive
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SInt)
  }

  it should "type check guard expression with String value" in {
    val source = """
      in data: String
      in flag: Boolean
      result = data when flag
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SString)
  }

  it should "type check guard expression with record value" in {
    val source = """
      in person: { name: String, age: Int }
      in isAdult: Boolean
      result = person when isAdult
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SOptional]
    outputType.inner shouldBe a[SemanticType.SRecord]
    val recordType = outputType.inner.asInstanceOf[SemanticType.SRecord]
    recordType.fields("name") shouldBe SemanticType.SString
    recordType.fields("age") shouldBe SemanticType.SInt
  }

  it should "type check guard expression with comparison condition" in {
    val source = """
      in score: Int
      in data: String
      result = data when score > 90
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SString)
  }

  it should "type check guard expression with boolean operators in condition" in {
    val source = """
      in value: Int
      in a: Boolean
      in b: Boolean
      result = value when a and b
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SInt)
  }

  it should "type check guard expression with complex boolean condition" in {
    val source = """
      in value: String
      in x: Int
      in flag: Boolean
      result = value when x > 10 and flag
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SString)
  }

  it should "type check guard expression with function call as expression" in {
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        name = "process",
        params = List("x" -> SemanticType.SInt),
        returns = SemanticType.SFloat,
        moduleName = "process-module"
      )
    )

    val source = """
      in x: Int
      in isEnabled: Boolean
      result = process(x) when isEnabled
      out result
    """
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SFloat)
  }

  it should "type check guard expression with Candidates value" in {
    val source = """
      type Item = { id: Int }
      in items: Candidates<Item>
      in shouldProcess: Boolean
      result = items when shouldProcess
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SOptional]
    outputType.inner shouldBe a[SemanticType.SCandidates]
  }

  it should "report error for guard expression with non-boolean condition" in {
    val source = """
      in value: Int
      in condition: Int
      result = value when condition
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "report error for guard expression with String condition" in {
    val source = """
      in value: Int
      in condition: String
      result = value when condition
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "type check guard expression with literal value" in {
    val source = """
      in flag: Boolean
      result = 42 when flag
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SInt)
  }

  it should "type check guard expression with boolean literal condition" in {
    val source = """
      in value: String
      result = value when true
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SString)
  }

  it should "type check guard expression with merge expression" in {
    val source = """
      in a: { x: Int }
      in b: { y: String }
      in flag: Boolean
      result = a + b when flag
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SOptional]
    outputType.inner shouldBe a[SemanticType.SRecord]
    val recordType = outputType.inner.asInstanceOf[SemanticType.SRecord]
    recordType.fields should have size 2
  }

  it should "type check Optional type in input declaration" in {
    val source = """
      in maybeValue: Optional<Int>
      out maybeValue
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SInt)
  }

  // Coalesce operator tests

  it should "type check coalesce operator unwrapping Optional to non-optional fallback" in {
    val source = """
      in maybeValue: Optional<Int>
      in fallback: Int
      result = maybeValue ?? fallback
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    // Optional<Int> ?? Int returns Int (unwrapped)
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check coalesce with two Optional values returning Optional" in {
    val source = """
      in primary: Optional<Int>
      in secondary: Optional<Int>
      result = primary ?? secondary
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    // Optional<Int> ?? Optional<Int> returns Optional<Int>
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SInt)
  }

  it should "type check coalesce with guard expression" in {
    val source = """
      in value: Int
      in condition: Boolean
      in fallback: Int
      result = value when condition ?? fallback
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    // (value when condition) returns Optional<Int>, ?? fallback returns Int
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check coalesce with String types" in {
    val source = """
      in maybeName: Optional<String>
      result = maybeName ?? "default"
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SString
  }

  it should "type check coalesce with record types" in {
    val source = """
      in maybeRecord: Optional<{ name: String, age: Int }>
      in defaultRecord: { name: String, age: Int }
      result = maybeRecord ?? defaultRecord
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SRecord]
    outputType.fields("name") shouldBe SemanticType.SString
    outputType.fields("age") shouldBe SemanticType.SInt
  }

  it should "type check chained coalesce (right associative) returning final type" in {
    val source = """
      in first: Optional<Int>
      in second: Optional<Int>
      in last: Int
      result = first ?? second ?? last
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    // first ?? (second ?? last)
    // second ?? last returns Int
    // first ?? Int returns Int
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check chained coalesce with all optionals" in {
    val source = """
      in first: Optional<Int>
      in second: Optional<Int>
      in third: Optional<Int>
      result = first ?? second ?? third
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    // All optionals, result is Optional<Int>
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SInt)
  }

  it should "type check coalesce with guard returning Optional" in {
    val source = """
      in value: Int
      in condition: Boolean
      in fallback: Optional<Int>
      result = value when condition ?? fallback
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    // (value when condition) returns Optional<Int>
    // Optional<Int> ?? Optional<Int> returns Optional<Int>
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SInt)
  }

  it should "report error for coalesce with non-optional left operand" in {
    val source = """
      in notOptional: Int
      in fallback: Int
      result = notOptional ?? fallback
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeError]) shouldBe true
  }

  it should "report error for coalesce with mismatched types" in {
    val source = """
      in maybeInt: Optional<Int>
      in fallbackString: String
      result = maybeInt ?? fallbackString
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "type check coalesce with Candidates type" in {
    val source = """
      type Item = { id: Int }
      in maybeItems: Optional<Candidates<Item>>
      in defaultItems: Candidates<Item>
      result = maybeItems ?? defaultItems
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SCandidates]
    outputType.element shouldBe a[SemanticType.SRecord]
  }

  it should "type check coalesce in conditional expression" in {
    val source = """
      in flag: Boolean
      in maybeA: Optional<Int>
      in maybeB: Optional<Int>
      in fallback: Int
      result = if (flag) maybeA ?? fallback else maybeB ?? fallback
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check coalesce with nested Optional (unwraps one layer)" in {
    val source = """
      in nested: Optional<Optional<Int>>
      in fallback: Optional<Int>
      result = nested ?? fallback
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    // Optional<Optional<Int>> ?? Optional<Int> returns Optional<Int>
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SInt)
  }

  // Comprehensive error tests for '+' operator on incompatible types

  it should "report UnsupportedArithmetic error for Int + String" in {
    val source = """
      in a: Int
      in b: String
      result = a + b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    val errors     = result.left.toOption.get
    val arithError = errors.collectFirst { case e: CompileError.UnsupportedArithmetic => e }.get
    arithError.message should include("+")
    arithError.message should include("Int")
    arithError.message should include("String")
  }

  it should "report UnsupportedArithmetic error for Boolean + Boolean" in {
    val source = """
      in a: Boolean
      in b: Boolean
      result = a + b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    val errors = result.left.toOption.get
    errors.exists(_.isInstanceOf[CompileError.UnsupportedArithmetic]) shouldBe true
  }

  it should "report UnsupportedArithmetic error for Float + String" in {
    val source = """
      in a: Float
      in b: String
      result = a + b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    val errors = result.left.toOption.get
    errors.exists(_.isInstanceOf[CompileError.UnsupportedArithmetic]) shouldBe true
  }

  it should "report UnsupportedArithmetic error for Candidates + Int" in {
    val source = """
      in a: Candidates<{ id: Int }>
      in b: Int
      result = a + b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    val errors = result.left.toOption.get
    errors.exists(_.isInstanceOf[CompileError.UnsupportedArithmetic]) shouldBe true
  }

  it should "report UnsupportedArithmetic error for Int + Candidates" in {
    val source = """
      in a: Int
      in b: Candidates<{ id: Int }>
      result = a + b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    val errors = result.left.toOption.get
    errors.exists(_.isInstanceOf[CompileError.UnsupportedArithmetic]) shouldBe true
  }

  it should "report UnsupportedArithmetic error for List + List (non-Candidates)" in {
    val source = """
      in a: List<Int>
      in b: List<String>
      result = a + b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    val errors = result.left.toOption.get
    errors.exists(_.isInstanceOf[CompileError.UnsupportedArithmetic]) shouldBe true
  }

  it should "report UnsupportedArithmetic error for Map + Record" in {
    val source = """
      in a: Map<String, Int>
      in b: { x: String }
      result = a + b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    val errors = result.left.toOption.get
    errors.exists(_.isInstanceOf[CompileError.UnsupportedArithmetic]) shouldBe true
  }

  it should "report UnsupportedArithmetic error for Candidates with non-record inner type + Record" in {
    val source = """
      in a: Candidates<Int>
      in b: { x: String }
      result = a + b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    val errors = result.left.toOption.get
    errors.exists(_.isInstanceOf[CompileError.UnsupportedArithmetic]) shouldBe true
  }

  it should "include source location in arithmetic error" in {
    val source = """
      in a: Int
      in b: String
      result = a + b
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    val errors     = result.left.toOption.get
    val arithError = errors.collectFirst { case e: CompileError.UnsupportedArithmetic => e }.get
    arithError.span.isDefined shouldBe true
  }

  it should "report IncompatibleMerge when merging record with non-record in type definition" in {
    val source = """
      type Invalid = { x: Int } + Int
      in a: Invalid
      out a
    """
    val result = check(source)
    result.isLeft shouldBe true
    val errors = result.left.toOption.get
    errors.exists(_.isInstanceOf[CompileError.IncompatibleMerge]) shouldBe true
  }

  // Lambda expression tests

  private def hofRegistry: FunctionRegistry = {
    val registry = FunctionRegistry.empty
    // filter: (List<Int>, (Int) => Boolean) => List<Int>
    registry.register(FunctionSignature(
      name = "filter",
      params = List(
        "items" -> SemanticType.SList(SemanticType.SInt),
        "predicate" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
      ),
      returns = SemanticType.SList(SemanticType.SInt),
      moduleName = "stdlib.hof.filter-int",
      namespace = Some("stdlib.collection")
    ))
    // map: (List<Int>, (Int) => Int) => List<Int>
    registry.register(FunctionSignature(
      name = "map",
      params = List(
        "items" -> SemanticType.SList(SemanticType.SInt),
        "transform" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SInt)
      ),
      returns = SemanticType.SList(SemanticType.SInt),
      moduleName = "stdlib.hof.map-int-int",
      namespace = Some("stdlib.collection")
    ))
    // all: (List<Int>, (Int) => Boolean) => Boolean
    registry.register(FunctionSignature(
      name = "all",
      params = List(
        "items" -> SemanticType.SList(SemanticType.SInt),
        "predicate" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
      ),
      returns = SemanticType.SBoolean,
      moduleName = "stdlib.hof.all-int",
      namespace = Some("stdlib.collection")
    ))
    // any: (List<Int>, (Int) => Boolean) => Boolean
    registry.register(FunctionSignature(
      name = "any",
      params = List(
        "items" -> SemanticType.SList(SemanticType.SInt),
        "predicate" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
      ),
      returns = SemanticType.SBoolean,
      moduleName = "stdlib.hof.any-int",
      namespace = Some("stdlib.collection")
    ))
    // Comparison functions for use in lambda bodies
    registry.register(FunctionSignature(
      name = "gt",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SBoolean,
      moduleName = "stdlib.gt",
      namespace = Some("stdlib.compare")
    ))
    registry
  }

  it should "type check lambda expression in filter function" in {
    val source = """
      in items: List<Int>
      result = filter(items, (x) => x > 0)
      out result
    """
    val result = check(source, hofRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SList(SemanticType.SInt)
  }

  it should "infer lambda parameter type from function context" in {
    val source = """
      in items: List<Int>
      result = filter(items, (x) => x > 0)
      out result
    """
    val result = check(source, hofRegistry)
    result.isRight shouldBe true
    // If inference works, the lambda type-checks correctly
    getOutputType(result.toOption.get) shouldBe SemanticType.SList(SemanticType.SInt)
  }

  it should "type check lambda with explicit type annotation" in {
    val source = """
      in items: List<Int>
      result = filter(items, (x: Int) => x > 0)
      out result
    """
    val result = check(source, hofRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SList(SemanticType.SInt)
  }

  it should "type check map function with lambda" in {
    val source = """
      in items: List<Int>
      result = map(items, (x) => x * 2)
      out result
    """
    // Need arithmetic for x * 2 (desugars to multiply(x, 2))
    val registry = hofRegistry
    registry.register(FunctionSignature(
      name = "multiply",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "stdlib.multiply",
      namespace = Some("stdlib.math")
    ))
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SList(SemanticType.SInt)
  }

  it should "type check all function with lambda predicate" in {
    val source = """
      in items: List<Int>
      result = all(items, (x) => x > 0)
      out result
    """
    val result = check(source, hofRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check any function with lambda predicate" in {
    val source = """
      in items: List<Int>
      result = any(items, (x) => x > 0)
      out result
    """
    val result = check(source, hofRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "report error when lambda body type does not match expected return type" in {
    val source = """
      in items: List<Int>
      result = filter(items, (x) => x)
      out result
    """
    // filter expects (Int) => Boolean, but lambda returns Int
    val result = check(source, hofRegistry)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "report error when lambda has wrong parameter types" in {
    val source = """
      in items: List<Int>
      result = filter(items, (x: String) => true)
      out result
    """
    // filter expects (Int) => Boolean, but lambda has String parameter
    val result = check(source, hofRegistry)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "type check SFunction type" in {
    // Verify that SFunction.prettyPrint works correctly
    val funcType = SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
    funcType.prettyPrint shouldBe "(Int) => Boolean"
  }

  it should "type check SFunction with multiple parameters" in {
    val funcType = SemanticType.SFunction(List(SemanticType.SInt, SemanticType.SString), SemanticType.SBoolean)
    funcType.prettyPrint shouldBe "(Int, String) => Boolean"
  }

  it should "type check lambda with boolean operators in body" in {
    val registry = hofRegistry
    val source = """
      in items: List<Int>
      result = filter(items, (x) => x > 0 and x < 100)
      out result
    """
    registry.register(FunctionSignature(
      name = "lt",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SBoolean,
      moduleName = "stdlib.lt",
      namespace = Some("stdlib.compare")
    ))
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SList(SemanticType.SInt)
  }

  // Additional Guard Expression Tests

  it should "type check guard in nested expression (conditional with guard)" in {
    val source = """
      in flag: Boolean
      in value: Int
      in other: Int
      in condition: Boolean
      result = if (flag) value when condition else other when condition
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SInt)
  }

  it should "type check guard with field access expression" in {
    val source = """
      in record: { value: Int, active: Boolean }
      result = record.value when record.active
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SInt)
  }

  it should "type check guard in assignment chain" in {
    val source = """
      in x: Int
      in cond1: Boolean
      in cond2: Boolean
      step1 = x when cond1
      step2 = step1 ?? 0
      final = step2 when cond2
      out final
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SInt)
  }

  it should "type check guard with List type" in {
    val source = """
      in items: List<Int>
      in hasItems: Boolean
      result = items when hasItems
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(
      SemanticType.SList(SemanticType.SInt)
    )
  }

  it should "type check guard with Optional value (nested Optional)" in {
    val source = """
      in maybeValue: Optional<Int>
      in condition: Boolean
      result = maybeValue when condition
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(
      SemanticType.SOptional(SemanticType.SInt)
    )
  }

  // Additional Coalesce Operator Tests

  it should "type check coalesce with multiple guards chained" in {
    val source = """
      in a: Int
      in b: Int
      in c: Int
      in cond1: Boolean
      in cond2: Boolean
      result = a when cond1 ?? b when cond2 ?? c
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check coalesce in field access context" in {
    val source = """
      in maybeRecord: Optional<{ name: String, age: Int }>
      in defaultRecord: { name: String, age: Int }
      result = maybeRecord ?? defaultRecord
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SRecord]
    outputType.fields("name") shouldBe SemanticType.SString
    outputType.fields("age") shouldBe SemanticType.SInt
  }

  it should "type check coalesce with List fallback" in {
    val source = """
      in maybeItems: Optional<List<Int>>
      in defaultItems: List<Int>
      result = maybeItems ?? defaultItems
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SList(SemanticType.SInt)
  }

  it should "report error for coalesce with mismatched inner types" in {
    val source = """
      in maybeInt: Optional<Int>
      in maybeString: Optional<String>
      result = maybeInt ?? maybeString
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  // Branch Expression Tests

  it should "type check branch with guard arms" in {
    val source = """
      in score: Int
      in data: String
      in high: Boolean
      in low: Boolean
      result = branch {
        high -> data when score > 90,
        low -> data when score < 10,
        otherwise -> data when true
      }
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SString)
  }

  it should "type check branch with coalesce in arms" in {
    val source = """
      in flag: Boolean
      in primary: Optional<Int>
      in secondary: Optional<Int>
      in fallback: Int
      result = branch {
        flag -> primary ?? fallback,
        otherwise -> secondary ?? fallback
      }
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check branch where arms return Optional types" in {
    val source = """
      in flag: Boolean
      in value: Int
      in cond: Boolean
      result = branch {
        flag -> value when cond,
        otherwise -> value when not cond
      }
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SInt)
  }

  it should "type check branch with complex condition using comparisons" in {
    val source = """
      in x: Int
      in y: Int
      in a: String
      in b: String
      in c: String
      result = branch {
        x > y and x > 0 -> a,
        x < y or y < 0 -> b,
        otherwise -> c
      }
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SString
  }

  it should "type check branch with not operator in condition" in {
    val source = """
      in flag: Boolean
      in a: Int
      in b: Int
      result = branch {
        not flag -> a,
        otherwise -> b
      }
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "report error for branch with inconsistent Optional/non-Optional arms" in {
    val source = """
      in flag: Boolean
      in value: Int
      in cond: Boolean
      result = branch {
        flag -> value when cond,
        otherwise -> value
      }
      out result
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  // Integration Tests: Combined Patterns

  it should "type check guard + coalesce + conditional pattern" in {
    val source = """
      in data: Int
      in flag: Boolean
      in condition: Boolean
      in fallback: Int
      guarded = data when condition
      result = if (flag) guarded ?? fallback else fallback
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check branch with guard conditions and coalesce fallbacks" in {
    val source = """
      in x: Int
      in y: Int
      in cond1: Boolean
      in cond2: Boolean
      in default: Int
      result = branch {
        cond1 -> x when cond2 ?? default,
        otherwise -> y when cond2 ?? default
      }
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check nested guards with coalesce unwrapping" in {
    // Testing simpler chain: multiple Optional values with final non-optional fallback
    val source = """
      in value: Int
      in cond1: Boolean
      in cond2: Boolean
      in fallback: Int
      step1 = value when cond1
      step2 = value when cond2
      result = step1 ?? step2 ?? fallback
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    // step1 is Optional<Int>
    // step2 is Optional<Int>
    // step1 ?? step2 is Optional<Int>
    // ... ?? fallback is Int
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check complex orchestration pipeline" in {
    val source = """
      in primaryData: Optional<Int>
      in secondaryData: Optional<Int>
      in isEnabled: Boolean
      in threshold: Int

      selected = primaryData ?? secondaryData ?? 0
      validated = selected when selected > threshold
      result = validated ?? 0
      out result
    """
    val result = check(source, comparisonRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check guard with merge expression result" in {
    val source = """
      in a: { x: Int }
      in b: { y: String }
      in cond: Boolean
      merged = a + b
      result = merged when cond
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SOptional]
    outputType.inner shouldBe a[SemanticType.SRecord]
    val recordType = outputType.inner.asInstanceOf[SemanticType.SRecord]
    recordType.fields should have size 2
  }

  it should "type check coalesce with projection" in {
    val source = """
      in maybeRecord: Optional<{ name: String, age: Int, extra: String }>
      in defaultRecord: { name: String, age: Int, extra: String }
      selected = maybeRecord ?? defaultRecord
      result = selected[name, age]
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    val outputType = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SRecord]
    outputType.fields should have size 2
    outputType.fields.keys should contain allOf ("name", "age")
  }

  it should "type check branch in conditional then branch" in {
    val source = """
      in outer: Boolean
      in inner: Boolean
      in a: Int
      in b: Int
      in c: Int
      result = if (outer) branch { inner -> a, otherwise -> b } else c
      out result
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SInt
  }

  it should "type check guard in branch with function call" in {
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        name = "process",
        params = List("x" -> SemanticType.SInt),
        returns = SemanticType.SString,
        moduleName = "process-module"
      )
    )

    val source = """
      in value: Int
      in flag: Boolean
      in isValid: Boolean
      result = branch {
        flag -> process(value) when isValid,
        otherwise -> "default" when true
      }
      out result
    """
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SOptional(SemanticType.SString)
  }

  // Union type tests

  it should "type check simple union type declaration" in {
    val source = """
      in x: String | Int
      out x
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe a[SemanticType.SUnion]

    val union = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SUnion]
    union.members should have size 2
    union.members should contain allOf (SemanticType.SString, SemanticType.SInt)
  }

  it should "type check multi-member union type" in {
    val source = """
      in x: String | Int | Boolean
      out x
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe a[SemanticType.SUnion]

    val union = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SUnion]
    union.members should have size 3
    union.members should contain allOf (SemanticType.SString, SemanticType.SInt, SemanticType.SBoolean)
  }

  it should "type check union type in type definition" in {
    val source = """
      type Result = String | Int
      in x: Result
      out x
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe a[SemanticType.SUnion]

    val union = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SUnion]
    union.members should have size 2
  }

  it should "type check union type with records" in {
    val source = """
      type Success = { value: Int }
      type Error = { message: String }
      type Result = Success | Error
      in x: Result
      out x
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe a[SemanticType.SUnion]

    val union = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SUnion]
    union.members should have size 2
    union.members.forall(_.isInstanceOf[SemanticType.SRecord]) shouldBe true
  }

  it should "flatten nested unions into a single union" in {
    val source = """
      type A = String | Int
      type B = Boolean | Float
      type Combined = A | B
      in x: Combined
      out x
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe a[SemanticType.SUnion]

    val union = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SUnion]
    // Should be flattened to 4 members, not nested unions
    union.members should have size 4
    union.members should contain allOf (SemanticType.SString, SemanticType.SInt, SemanticType.SBoolean, SemanticType.SFloat)
  }

  it should "simplify single-member union to the member type" in {
    val source = """
      type Single = String | String
      in x: Single
      out x
    """
    val result = check(source)
    result.isRight shouldBe true
    // A union with only one unique member should simplify to that member
    getOutputType(result.toOption.get) shouldBe SemanticType.SString
  }

  it should "type check union type as function return type" in {
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        name = "maybe-process",
        params = List("x" -> SemanticType.SInt),
        returns = SemanticType.SUnion(Set(SemanticType.SInt, SemanticType.SString)),
        moduleName = "maybe-process"
      )
    )

    val source = """
      in x: Int
      result = maybe-process(x)
      out result
    """
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe a[SemanticType.SUnion]
  }

  it should "type check union type as function parameter" in {
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        name = "handle-either",
        params = List("x" -> SemanticType.SUnion(Set(SemanticType.SInt, SemanticType.SString))),
        returns = SemanticType.SBoolean,
        moduleName = "handle-either"
      )
    )

    val source = """
      in x: String | Int
      result = handle-either(x)
      out result
    """
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check union type with Optional member" in {
    val source = """
      in x: Optional<Int> | String
      out x
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe a[SemanticType.SUnion]

    val union = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SUnion]
    union.members should have size 2
    union.members should contain(SemanticType.SOptional(SemanticType.SInt))
    union.members should contain(SemanticType.SString)
  }

  it should "type check union type with List member" in {
    val source = """
      in x: List<Int> | String
      out x
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe a[SemanticType.SUnion]

    val union = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SUnion]
    union.members should have size 2
    union.members should contain(SemanticType.SList(SemanticType.SInt))
    union.members should contain(SemanticType.SString)
  }

  it should "type check union type with Candidates member" in {
    val source = """
      type Item = { id: Int }
      in x: Candidates<Item> | String
      out x
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe a[SemanticType.SUnion]

    val union = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SUnion]
    union.members should have size 2
    union.members should contain(SemanticType.SString)
    union.members.exists(_.isInstanceOf[SemanticType.SCandidates]) shouldBe true
  }

  it should "preserve union type through assignment" in {
    val source = """
      in x: String | Int
      y = x
      out y
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe a[SemanticType.SUnion]
  }

  it should "type check union with merge type expression" in {
    val source = """
      type A = { x: Int }
      type B = { y: String }
      type Extended = A + B | { z: Boolean }
      in x: Extended
      out x
    """
    val result = check(source)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe a[SemanticType.SUnion]

    val union = getOutputType(result.toOption.get).asInstanceOf[SemanticType.SUnion]
    union.members should have size 2
    // One member should be the merged A + B record
    union.members.exists {
      case SemanticType.SRecord(fields) =>
        fields.contains("x") && fields.contains("y")
      case _ => false
    } shouldBe true
  }

  it should "report error when union member type is undefined" in {
    val source = """
      in x: String | UndefinedType
      out x
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.UndefinedType]) shouldBe true
  }

  it should "format union type with prettyPrint" in {
    val union = SemanticType.SUnion(Set(SemanticType.SString, SemanticType.SInt))
    // Members are sorted alphabetically in prettyPrint
    union.prettyPrint should (equal("Int | String") or equal("String | Int"))
  }

  it should "convert union SemanticType to CType correctly" in {
    val union = SemanticType.SUnion(Set(SemanticType.SString, SemanticType.SInt))
    val cType = SemanticType.toCType(union)

    cType shouldBe a[CType.CUnion]
    val cUnion = cType.asInstanceOf[CType.CUnion]
    cUnion.structure.keys should contain allOf ("String", "Int")
    cUnion.structure("String") shouldBe CType.CString
    cUnion.structure("Int") shouldBe CType.CInt
  }

  it should "convert CType.CUnion to SemanticType correctly" in {
    val cUnion = CType.CUnion(Map("String" -> CType.CString, "Int" -> CType.CInt))
    val semType = SemanticType.fromCType(cUnion)

    semType shouldBe a[SemanticType.SUnion]
    val union = semType.asInstanceOf[SemanticType.SUnion]
    union.members should contain allOf (SemanticType.SString, SemanticType.SInt)
  }

  // Additional Lambda Expression Tests

  it should "type check lambda with conditional expression in body" in {
    val registry = hofRegistry
    registry.register(FunctionSignature(
      name = "lt",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SBoolean,
      moduleName = "stdlib.lt",
      namespace = Some("stdlib.compare")
    ))

    val source = """
      in items: List<Int>
      result = filter(items, (x) => if (x > 0) x < 100 else false)
      out result
    """
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SList(SemanticType.SInt)
  }

  it should "type check lambda with field access in body" in {
    val registry = FunctionRegistry.empty
    // filter over records
    registry.register(FunctionSignature(
      name = "filterRecords",
      params = List(
        "items" -> SemanticType.SList(SemanticType.SRecord(Map("value" -> SemanticType.SInt, "active" -> SemanticType.SBoolean))),
        "predicate" -> SemanticType.SFunction(
          List(SemanticType.SRecord(Map("value" -> SemanticType.SInt, "active" -> SemanticType.SBoolean))),
          SemanticType.SBoolean
        )
      ),
      returns = SemanticType.SList(SemanticType.SRecord(Map("value" -> SemanticType.SInt, "active" -> SemanticType.SBoolean))),
      moduleName = "filter-records",
      namespace = Some("stdlib.collection")
    ))

    val source = """
      in items: List<{ value: Int, active: Boolean }>
      result = filterRecords(items, (x) => x.active)
      out result
    """
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe a[SemanticType.SList]
  }

  it should "type check lambda with not operator in body" in {
    val source = """
      in items: List<Int>
      result = filter(items, (x) => not (x > 0))
      out result
    """
    val result = check(source, hofRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SList(SemanticType.SInt)
  }

  it should "type check lambda with or operator in body" in {
    val registry = hofRegistry
    registry.register(FunctionSignature(
      name = "lt",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SBoolean,
      moduleName = "stdlib.lt",
      namespace = Some("stdlib.compare")
    ))

    val source = """
      in items: List<Int>
      result = filter(items, (x) => x < 0 or x > 100)
      out result
    """
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SList(SemanticType.SInt)
  }

  it should "type check map lambda with arithmetic expression" in {
    val registry = hofRegistry
    registry.register(FunctionSignature(
      name = "add",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "stdlib.add",
      namespace = Some("stdlib.math")
    ))

    val source = """
      in items: List<Int>
      result = map(items, (x) => x + 10)
      out result
    """
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SList(SemanticType.SInt)
  }

  it should "report error when lambda parameter shadows outer variable" in {
    // Lambda parameters should be scoped to lambda body - this tests that outer 'x' is not confused with lambda 'x'
    val source = """
      in items: List<Int>
      in x: Int
      result = filter(items, (x) => x > 0)
      out result
    """
    val result = check(source, hofRegistry)
    // Should succeed - lambda parameter x shadows outer x within lambda body
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SList(SemanticType.SInt)
  }

  it should "type check lambda with complex boolean expression" in {
    val registry = hofRegistry
    registry.register(FunctionSignature(
      name = "lt",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SBoolean,
      moduleName = "stdlib.lt",
      namespace = Some("stdlib.compare")
    ))
    registry.register(FunctionSignature(
      name = "gte",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SBoolean,
      moduleName = "stdlib.gte",
      namespace = Some("stdlib.compare")
    ))

    val source = """
      in items: List<Int>
      result = filter(items, (x) => (x > 0 and x < 100) or (x >= 1000))
      out result
    """
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SList(SemanticType.SInt)
  }

  it should "report error when lambda body references undefined variable" in {
    val source = """
      in items: List<Int>
      result = filter(items, (x) => y > 0)
      out result
    """
    val result = check(source, hofRegistry)
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.UndefinedVariable]) shouldBe true
  }

  it should "type check SFunction pretty print with no parameters" in {
    val funcType = SemanticType.SFunction(List(), SemanticType.SBoolean)
    funcType.prettyPrint shouldBe "() => Boolean"
  }

  it should "type check SFunction equality" in {
    val funcType1 = SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
    val funcType2 = SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
    val funcType3 = SemanticType.SFunction(List(SemanticType.SString), SemanticType.SBoolean)

    funcType1 shouldBe funcType2
    funcType1 should not be funcType3
  }

  it should "type check lambda with literal comparison" in {
    val source = """
      in items: List<Int>
      result = filter(items, (x) => x > 42)
      out result
    """
    val result = check(source, hofRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SList(SemanticType.SInt)
  }

  // Note: This test verifies behavior when a lambda is passed where a non-function type is expected.
  // Current behavior: Lambda parameter 'y' is not defined in the outer scope, so it fails with UndefinedVariable
  it should "report error when lambda passed to non-HOF function" in {
    val registry = FunctionRegistry.empty
    registry.register(FunctionSignature(
      name = "process",
      params = List("x" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "process"
    ))
    // Also need add function for y + 1
    registry.register(FunctionSignature(
      name = "add",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "stdlib.add",
      namespace = Some("stdlib.math")
    ))

    val source = """
      in x: Int
      result = process((y) => y + 1)
      out result
    """
    val result = check(source, registry)
    // Lambda passed to non-HOF function - this should fail with some error
    // Either: UndefinedVariable (y not in scope), TypeMismatch (function vs int), or other
    result.isLeft shouldBe true
  }

  it should "report error when lambda arity does not match expected function type" in {
    // filter expects (Int) => Boolean but we provide (x, y) => ...
    // Note: Multi-param lambdas may not be fully supported, so this tests error handling
    val registry = FunctionRegistry.empty
    registry.register(FunctionSignature(
      name = "binaryFilter",
      params = List(
        "items" -> SemanticType.SList(SemanticType.SInt),
        "predicate" -> SemanticType.SFunction(List(SemanticType.SInt, SemanticType.SInt), SemanticType.SBoolean)
      ),
      returns = SemanticType.SList(SemanticType.SInt),
      moduleName = "binary-filter"
    ))

    val source = """
      in items: List<Int>
      result = filter(items, (x, y) => x > y)
      out result
    """
    val result = check(source, hofRegistry)
    result.isLeft shouldBe true
    // Error type may vary - just verify it fails
  }

  it should "type check chained HOF calls" in {
    val registry = hofRegistry
    registry.register(FunctionSignature(
      name = "multiply",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "stdlib.multiply",
      namespace = Some("stdlib.math")
    ))

    val source = """
      in items: List<Int>
      filtered = filter(items, (x) => x > 0)
      result = map(filtered, (x) => x * 2)
      out result
    """
    val result = check(source, registry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SList(SemanticType.SInt)
  }

  it should "type check all function with literal true predicate" in {
    val source = """
      in items: List<Int>
      result = all(items, (x) => true)
      out result
    """
    val result = check(source, hofRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check any function with literal false predicate" in {
    val source = """
      in items: List<Int>
      result = any(items, (x) => false)
      out result
    """
    val result = check(source, hofRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check combining filter result with all" in {
    val source = """
      in items: List<Int>
      positives = filter(items, (x) => x > 0)
      result = all(positives, (x) => x > 0)
      out result
    """
    val result = check(source, hofRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }

  it should "type check combining filter result with any" in {
    val source = """
      in items: List<Int>
      positives = filter(items, (x) => x > 0)
      result = any(positives, (x) => x > 100)
      out result
    """
    val result = check(source, hofRegistry)
    result.isRight shouldBe true
    getOutputType(result.toOption.get) shouldBe SemanticType.SBoolean
  }
}
