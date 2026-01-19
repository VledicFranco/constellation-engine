package io.constellation.lang

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.*
import io.constellation.lang.ast.CompileError
import io.constellation.lang.compiler.CompileResult
import io.constellation.lang.semantic.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LangCompilerTest extends AnyFlatSpec with Matchers {

  "LangCompiler" should "compile a simple program with inputs" in {
    val compiler = LangCompiler.empty

    val source = """
      in x: Int
      in y: String
      out x
    """

    val result = compiler.compile(source, "simple-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.name shouldBe "simple-dag"
    compiled.dagSpec.data should not be empty
  }

  it should "compile a program with record types" in {
    val compiler = LangCompiler.empty

    val source = """
      type Person = { name: String, age: Int }
      in person: Person
      out person
    """

    val result = compiler.compile(source, "record-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.data.values.exists(_.cType match {
      case CType.CProduct(fields) =>
        fields.contains("name") && fields.contains("age")
      case _ => false
    }) shouldBe true
  }

  it should "compile a program with merge expressions" in {
    val compiler = LangCompiler.empty

    val source = """
      in a: { x: Int }
      in b: { y: String }
      result = a + b
      out result
    """

    val result = compiler.compile(source, "merge-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have a data node with inline merge transform
    compiled.dagSpec.data.values.exists(d =>
      d.name.contains("merge") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.MergeTransform]
      )
    ) shouldBe true
  }

  it should "compile a program with projection expressions" in {
    val compiler = LangCompiler.empty

    val source = """
      in data: { id: Int, name: String, extra: String }
      result = data[id, name]
      out result
    """

    val result = compiler.compile(source, "project-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have a data node with inline project transform
    compiled.dagSpec.data.values.exists(d =>
      d.name.contains("project") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.ProjectTransform]
      )
    ) shouldBe true
  }

  it should "compile a program with registered functions" in {
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        name = "double",
        params = List("n" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "double-module"
      )
    )

    val compiler = LangCompiler(registry, Map.empty)

    val source = """
      in x: Int
      result = double(x)
      out result
    """

    val result = compiler.compile(source, "func-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.modules should not be empty
  }

  it should "compile a program with conditional expressions" in {
    val compiler = LangCompiler.empty

    val source = """
      in flag: Boolean
      in a: Int
      in b: Int
      result = if (flag) a else b
      out result
    """

    val result = compiler.compile(source, "cond-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have a data node with inline conditional transform
    compiled.dagSpec.data.values.exists(d =>
      d.name.contains("conditional") && d.inlineTransform.contains(
        InlineTransform.ConditionalTransform
      )
    ) shouldBe true
  }

  it should "compile the example program from design doc" in {
    val registry = FunctionRegistry.empty

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

    val compiler = LangCompiler(registry, Map.empty)

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

    val result = compiler.compile(source, "ide-ranker-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.modules should not be empty
  }

  it should "report errors for undefined variables" in {
    val compiler = LangCompiler.empty

    val source = """
      out undefined_var
    """

    val result = compiler.compile(source, "error-dag")
    result.isLeft shouldBe true
  }

  it should "report errors for undefined functions" in {
    val compiler = LangCompiler.empty

    val source = """
      in x: Int
      result = unknown_function(x)
      out result
    """

    val result = compiler.compile(source, "error-dag")
    result.isLeft shouldBe true
  }

  it should "report parse errors" in {
    val compiler = LangCompiler.empty

    val source = """
      in x: Int
      out @invalid
    """

    val result = compiler.compile(source, "error-dag")
    result.isLeft shouldBe true
  }

  it should "generate correct DAG edges for simple pipeline" in {
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        name = "process",
        params = List("input" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "process-module"
      )
    )

    val compiler = LangCompiler(registry, Map.empty)

    val source = """
      in x: Int
      result = process(x)
      out result
    """

    val result = compiler.compile(source, "pipeline-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have edges connecting input -> module -> output
    compiled.dagSpec.inEdges should not be empty
    compiled.dagSpec.outEdges should not be empty
  }

  it should "use builder pattern correctly" in {
    val compiler = LangCompiler.builder
      .withFunction(
        FunctionSignature(
          name = "transform",
          params = List("data" -> SemanticType.SString),
          returns = SemanticType.SString,
          moduleName = "transform-module"
        )
      )
      .build

    val source = """
      in data: String
      result = transform(data)
      out result
    """

    val result = compiler.compile(source, "builder-dag")
    result.isRight shouldBe true
  }

  // IR generation tests

  it should "generate IR with correct topological order" in {
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        name = "step1",
        params = List("x" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "step1"
      )
    )
    registry.register(
      FunctionSignature(
        name = "step2",
        params = List("x" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "step2"
      )
    )

    val compiler = LangCompiler(registry, Map.empty)

    val source = """
      in x: Int
      a = step1(x)
      b = step2(a)
      out b
    """

    val result = compiler.compile(source, "sequential-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have 2 module nodes in the DAG
    compiled.dagSpec.modules should have size 2
  }

  it should "compile complex expressions with merge and projection" in {
    val compiler = LangCompiler.empty

    val source = """
      in a: { x: Int, y: Int, extra: String }
      in b: { z: String }
      result = a[x, y] + b
      out result
    """

    val result = compiler.compile(source, "complex-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have data nodes with both project and merge inline transforms
    val dataNodes = compiled.dagSpec.data.values.toList
    dataNodes.exists(d =>
      d.name.contains("project") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.ProjectTransform]
      )
    ) shouldBe true
    dataNodes.exists(d =>
      d.name.contains("merge") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.MergeTransform]
      )
    ) shouldBe true
  }

  it should "handle Candidates projection correctly" in {
    val compiler = LangCompiler.empty

    val source = """
      type Item = { id: Int, name: String, description: String }
      in items: Candidates<Item>
      result = items[id, name]
      out result
    """

    val result = compiler.compile(source, "candidates-project-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Output should be Candidates with projected fields
    val outputDataNodes = compiled.dagSpec.data.values.filter(_.name.contains("project"))
    outputDataNodes should not be empty
  }

  it should "handle multiple inputs correctly" in {
    val compiler = LangCompiler.empty

    val source = """
      in userId: Int
      in sessionId: String
      in items: List<Int>
      out userId
    """

    val result = compiler.compile(source, "multi-input-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have 3 input data nodes
    compiled.dagSpec.data should have size 3
    val names = compiled.dagSpec.data.values.map(_.name).toSet
    names should contain allOf ("userId", "sessionId", "items")
  }

  it should "compile type references correctly" in {
    val compiler = LangCompiler.empty

    val source = """
      type Base = { id: Int }
      type Extended = Base + { name: String }
      in data: Extended
      out data
    """

    val result = compiler.compile(source, "type-ref-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // The input data node should have the merged type
    val inputNode = compiled.dagSpec.data.values.find(_.name == "data")
    inputNode.isDefined shouldBe true
    inputNode.get.cType match {
      case CType.CProduct(fields) =>
        fields.keySet should contain allOf ("id", "name")
      case _ => fail("Expected CProduct type")
    }
  }

  it should "handle literals in assignments" in {
    val compiler = LangCompiler.empty

    val source = """
      x = 42
      out x
    """

    val result = compiler.compile(source, "literal-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have a data node for the literal
    compiled.dagSpec.data should not be empty
  }

  // Multiple outputs tests

  it should "track multiple declared outputs" in {
    val compiler = LangCompiler.empty

    val source = """
      in x: Int
      in y: Int
      z = x
      out x
      out z
    """

    val result = compiler.compile(source, "multi-out-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.declaredOutputs should contain allOf ("x", "z")
    compiled.dagSpec.declaredOutputs should have size 2
    // y is NOT declared as output
    compiled.dagSpec.declaredOutputs should not contain "y"
  }

  it should "create output bindings for all declared outputs" in {
    val compiler = LangCompiler.empty

    val source = """
      in x: Int
      in y: Int
      z = x
      out x
      out z
    """

    val result = compiler.compile(source, "bindings-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // outputBindings should contain mappings for x and z
    compiled.dagSpec.outputBindings.keys should contain allOf ("x", "z")
    compiled.dagSpec.outputBindings should have size 2
    // y is NOT in output bindings
    compiled.dagSpec.outputBindings.keys should not contain "y"
  }

  it should "map aliased outputs to correct data nodes" in {
    val compiler = LangCompiler.empty

    val source = """
      in x: Int
      z = x
      out z
    """

    val result = compiler.compile(source, "alias-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // z should be bound to the same data node as x
    val zBinding = compiled.dagSpec.outputBindings.get("z")
    zBinding.isDefined shouldBe true

    // The data node should exist
    compiled.dagSpec.data.get(zBinding.get).isDefined shouldBe true
  }

  it should "handle multiple outputs from different expressions" in {
    val compiler = LangCompiler.empty

    val source = """
      in a: { x: Int }
      in b: { y: Int }
      merged = a + b
      out a
      out merged
    """

    val result = compiler.compile(source, "multi-expr-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.declaredOutputs should contain allOf ("a", "merged")
    compiled.dagSpec.outputBindings should have size 2

    // a and merged should point to different data nodes
    val aBinding      = compiled.dagSpec.outputBindings("a")
    val mergedBinding = compiled.dagSpec.outputBindings("merged")
    aBinding should not equal mergedBinding
  }

  it should "report error for undefined output variable" in {
    val compiler = LangCompiler.empty

    val source = """
      in x: Int
      out undefined_var
    """

    val result = compiler.compile(source, "error-dag")
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.UndefinedVariable]) shouldBe true
  }

  // Namespace / Qualified Name tests

  it should "compile programs with fully qualified function names" in {
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

    val compiler = LangCompiler(registry, Map.empty)

    val source = """
      in a: Int
      in b: Int
      result = stdlib.math.add(a, b)
      out result
    """

    val result = compiler.compile(source, "fqn-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.modules should not be empty
  }

  it should "compile programs with use declarations" in {
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

    val compiler = LangCompiler(registry, Map.empty)

    val source = """
      use stdlib.math
      in a: Int
      in b: Int
      result = add(a, b)
      out result
    """

    val result = compiler.compile(source, "use-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.modules should not be empty
  }

  it should "compile programs with aliased imports" in {
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

    val compiler = LangCompiler(registry, Map.empty)

    val source = """
      use stdlib.math as m
      in a: Int
      in b: Int
      result = m.add(a, b)
      out result
    """

    val result = compiler.compile(source, "alias-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.modules should not be empty
  }

  it should "compile programs with multiple namespaces" in {
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

    val compiler = LangCompiler(registry, Map.empty)

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

    val result = compiler.compile(source, "multi-ns-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have 2 modules: add and upper
    compiled.dagSpec.modules should have size 2
  }

  it should "include synthetic modules for namespace functions" in {
    // Create a real module for testing
    case class TwoInts(a: Long, b: Long)
    case class IntOut(out: Long)

    val addModule: Module.Uninitialized = ModuleBuilder
      .metadata("stdlib.add", "Add two integers", 1, 0)
      .implementationPure[TwoInts, IntOut](in => IntOut(in.a + in.b))
      .build

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

    val modules  = Map("stdlib.add" -> addModule)
    val compiler = LangCompiler(registry, modules)

    val source = """
      use stdlib.math
      in a: Int
      in b: Int
      result = add(a, b)
      out result
    """

    val result = compiler.compile(source, "synth-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // syntheticModules should contain the resolved module
    compiled.syntheticModules should not be empty
  }

  it should "report error for undefined namespace" in {
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

    val compiler = LangCompiler(registry, Map.empty)

    val source = """
      in a: Int
      result = nonexistent.namespace.add(a, a)
      out result
    """

    val result = compiler.compile(source, "error-dag")
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.UndefinedNamespace]) shouldBe true
  }

  it should "report error for ambiguous function call" in {
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

    val compiler = LangCompiler(registry, Map.empty)

    val source = """
      use namespace1
      use namespace2
      in x: Int
      result = process(x)
      out result
    """

    val result = compiler.compile(source, "error-dag")
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.AmbiguousFunction]) shouldBe true
  }

  it should "expose function registry for namespace introspection" in {
    val compiler = LangCompiler.builder
      .withFunction(
        FunctionSignature(
          name = "add",
          params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
          returns = SemanticType.SInt,
          moduleName = "stdlib.add",
          namespace = Some("stdlib.math")
        )
      )
      .withFunction(
        FunctionSignature(
          name = "upper",
          params = List("value" -> SemanticType.SString),
          returns = SemanticType.SString,
          moduleName = "stdlib.upper",
          namespace = Some("stdlib.string")
        )
      )
      .build

    val registry = compiler.functionRegistry

    // Should have both namespaces registered
    registry.namespaces should contain allOf ("stdlib.math", "stdlib.string")

    // Should be able to lookup by qualified name
    registry.lookupQualified("stdlib.math.add").isDefined shouldBe true
    registry.lookupQualified("stdlib.string.upper").isDefined shouldBe true
  }

  // Candidates + Candidates merge compilation tests

  it should "compile Candidates + Candidates merge expression" in {
    val compiler = LangCompiler.empty

    val source = """
      type A = { id: Int }
      type B = { name: String }
      in items: Candidates<A>
      in metadata: Candidates<B>
      result = items + metadata
      out result
    """

    val result = compiler.compile(source, "candidates-merge-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have a data node with inline merge transform
    compiled.dagSpec.data.values.exists(d =>
      d.name.contains("merge") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.MergeTransform]
      )
    ) shouldBe true
  }

  it should "compile Candidates + Candidates with empty inner records" in {
    val compiler = LangCompiler.empty

    val source = """
      in a: Candidates<{}>
      in b: Candidates<{ x: Int }>
      result = a + b
      out result
    """

    val result = compiler.compile(source, "empty-candidates-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have a data node with inline merge transform
    compiled.dagSpec.data.values.exists(
      _.inlineTransform.exists(_.isInstanceOf[InlineTransform.MergeTransform])
    ) shouldBe true
  }

  it should "compile chained Candidates merges" in {
    val compiler = LangCompiler.empty

    val source = """
      type A = { x: Int }
      type B = { y: String }
      type C = { z: Boolean }
      in a: Candidates<A>
      in b: Candidates<B>
      in c: Candidates<C>
      result = a + b + c
      out result
    """

    val result = compiler.compile(source, "chained-candidates-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have 2 data nodes with inline merge transforms for a + b + c
    val mergeDataNodes = compiled.dagSpec.data.values.filter(d =>
      d.name.contains("merge") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.MergeTransform]
      )
    )
    mergeDataNodes should have size 2
  }

  it should "generate correct output type for Candidates merge" in {
    val compiler = LangCompiler.empty

    val source = """
      in items: Candidates<{ id: Int, name: String }>
      in scores: Candidates<{ score: Float }>
      result = items + scores
      out result
    """

    val result = compiler.compile(source, "typed-candidates-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // The output data node should have the merged Candidates type (CList of CProduct)
    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    resultBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(resultBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType match {
      case CType.CList(CType.CProduct(fields)) =>
        fields.keys should contain allOf ("id", "name", "score")
      case other => fail(s"Expected CList(CProduct(...)), got $other")
    }
  }

  // Candidates + Record broadcast merge compilation tests

  it should "compile Candidates + Record broadcast merge" in {
    val compiler = LangCompiler.empty

    val source = """
      type Item = { id: Int, name: String }
      in items: Candidates<Item>
      in context: { userId: Int }
      result = items + context
      out result
    """

    val result = compiler.compile(source, "candidates-record-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have a data node with inline merge transform
    compiled.dagSpec.data.values.exists(d =>
      d.name.contains("merge") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.MergeTransform]
      )
    ) shouldBe true
  }

  it should "compile Record + Candidates broadcast merge" in {
    val compiler = LangCompiler.empty

    val source = """
      type Item = { id: Int, name: String }
      in context: { userId: Int }
      in items: Candidates<Item>
      result = context + items
      out result
    """

    val result = compiler.compile(source, "record-candidates-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have a data node with inline merge transform
    compiled.dagSpec.data.values.exists(d =>
      d.name.contains("merge") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.MergeTransform]
      )
    ) shouldBe true
  }

  it should "generate correct output type for Candidates + Record merge" in {
    val compiler = LangCompiler.empty

    val source = """
      in items: Candidates<{ id: Int, name: String }>
      in context: { userId: Int, sessionId: String }
      result = items + context
      out result
    """

    val result = compiler.compile(source, "typed-broadcast-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    resultBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(resultBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType match {
      case CType.CList(CType.CProduct(fields)) =>
        fields.keys should contain allOf ("id", "name", "userId", "sessionId")
      case other => fail(s"Expected CList(CProduct(...)), got $other")
    }
  }

  it should "compile chained Candidates + Record + Candidates merge" in {
    val compiler = LangCompiler.empty

    val source = """
      in a: Candidates<{ x: Int }>
      in b: { y: String }
      in c: Candidates<{ z: Boolean }>
      result = a + b + c
      out result
    """

    val result = compiler.compile(source, "chained-broadcast-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have 2 data nodes with inline merge transforms for a + b + c
    val mergeDataNodes = compiled.dagSpec.data.values.filter(d =>
      d.name.contains("merge") && d.inlineTransform.exists(
        _.isInstanceOf[InlineTransform.MergeTransform]
      )
    )
    mergeDataNodes should have size 2
  }

  // Branch expression tests

  it should "compile a simple branch expression" in {
    val registry = FunctionRegistry.empty
    // Register comparison functions needed by the compiler
    registry.register(
      FunctionSignature(
        name = "gt",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SBoolean,
        moduleName = "gt"
      )
    )
    registry.register(
      FunctionSignature(
        name = "lt",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SBoolean,
        moduleName = "lt"
      )
    )

    val compiler = LangCompiler(registry, Map.empty)

    val source = """
      in score: Int
      in high: Int
      in low: Int
      in mediumResult: Int
      result = branch {
        score > high -> high,
        score < low -> low,
        otherwise -> mediumResult
      }
      out result
    """

    val result = compiler.compile(source, "branch-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have a synthetic branch module
    compiled.syntheticModules should not be empty
    compiled.dagSpec.modules.values.exists(_.name.contains("branch")) shouldBe true
  }

  it should "compile a branch expression with single case" in {
    val compiler = LangCompiler.empty

    // Simplified source with minimal whitespace
    val source =
      "in flag: Boolean\nin a: Int\nin b: Int\nresult = branch { flag -> a, otherwise -> b }\nout result"

    val result = compiler.compile(source, "branch-single-dag")
    result.left.foreach(errors => println(s"DEBUG ERRORS: $errors"))
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.modules.values.exists(_.name.contains("branch")) shouldBe true
  }

  it should "compile a branch expression with only otherwise" in {
    val compiler = LangCompiler.empty

    val source = """
      in x: Int
      result = branch {
        otherwise -> x
      }
      out result
    """

    val result = compiler.compile(source, "branch-only-otherwise-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.modules.values.exists(_.name.contains("branch")) shouldBe true
  }

  it should "compile a branch expression with string results" in {
    val compiler = LangCompiler.empty

    val source = """
      in cond1: Boolean
      in cond2: Boolean
      result = branch {
        cond1 -> "first",
        cond2 -> "second",
        otherwise -> "default"
      }
      out result
    """

    val result = compiler.compile(source, "branch-string-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    resultBinding.isDefined shouldBe true
  }

  it should "report error when branch condition is not Boolean" in {
    val compiler = LangCompiler.empty

    val source = """
      in x: Int
      in a: Int
      in b: Int
      result = branch {
        x -> a,
        otherwise -> b
      }
      out result
    """

    val result = compiler.compile(source, "error-dag")
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "report error when branch expressions have different types" in {
    val compiler = LangCompiler.empty

    val source = """
      in flag: Boolean
      result = branch {
        flag -> 42,
        otherwise -> "hello"
      }
      out result
    """

    val result = compiler.compile(source, "error-dag")
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "report error when otherwise type doesn't match branch types" in {
    val compiler = LangCompiler.empty

    val source = """
      in flag: Boolean
      result = branch {
        flag -> 42,
        otherwise -> 3.14
      }
      out result
    """

    val result = compiler.compile(source, "error-dag")
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "compile branch with record results" in {
    val compiler = LangCompiler.empty

    val source = """
      in flag: Boolean
      in a: { x: Int }
      in b: { x: Int }
      result = branch {
        flag -> a,
        otherwise -> b
      }
      out result
    """

    val result = compiler.compile(source, "branch-record-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    resultBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(resultBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType match {
      case CType.CProduct(fields) =>
        fields.keys should contain("x")
      case other => fail(s"Expected CProduct, got $other")
    }
  }

  it should "compile nested branch expressions" in {
    val compiler = LangCompiler.empty

    val source = """
      in outer: Boolean
      in inner: Boolean
      in a: Int
      in b: Int
      in c: Int
      result = branch {
        outer -> branch {
          inner -> a,
          otherwise -> b
        },
        otherwise -> c
      }
      out result
    """

    val result = compiler.compile(source, "nested-branch-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have 2 branch modules
    val branchModules = compiled.dagSpec.modules.values.filter(_.name.contains("branch"))
    branchModules should have size 2
  }

  it should "compile branch with boolean operators in conditions" in {
    val compiler = LangCompiler.empty

    val source = """
      in a: Boolean
      in b: Boolean
      in x: Int
      in y: Int
      in z: Int
      result = branch {
        a and b -> x,
        a or b -> y,
        otherwise -> z
      }
      out result
    """

    val result = compiler.compile(source, "branch-bool-ops-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.modules.values.exists(_.name.contains("branch")) shouldBe true
    // AND and OR operations use inline transforms on data nodes (not separate modules)
    compiled.dagSpec.data.values.exists(_.name.contains("and")) shouldBe true
    compiled.dagSpec.data.values.exists(_.name.contains("or")) shouldBe true
  }

  // Lambda expression and higher-order function tests

  private def hofCompiler: LangCompiler = {
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
    registry.register(FunctionSignature(
      name = "lt",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SBoolean,
      moduleName = "stdlib.lt",
      namespace = Some("stdlib.compare")
    ))
    // Arithmetic functions for map
    registry.register(FunctionSignature(
      name = "multiply",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "stdlib.multiply",
      namespace = Some("stdlib.math")
    ))
    LangCompiler(registry, Map.empty)
  }

  it should "compile filter with lambda expression" in {
    val compiler = hofCompiler

    val source = """
      in items: List<Int>
      result = filter(items, (x) => x > 0)
      out result
    """

    val result = compiler.compile(source, "filter-lambda-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have a higher-order node in the DAG
    compiled.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(_.isInstanceOf[InlineTransform.FilterTransform])
    ) shouldBe true
  }

  it should "compile map with lambda expression" in {
    val compiler = hofCompiler

    val source = """
      in items: List<Int>
      result = map(items, (x) => x * 2)
      out result
    """

    val result = compiler.compile(source, "map-lambda-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have a higher-order node with MapTransform
    compiled.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(_.isInstanceOf[InlineTransform.MapTransform])
    ) shouldBe true
  }

  it should "compile all with lambda expression" in {
    val compiler = hofCompiler

    val source = """
      in items: List<Int>
      result = all(items, (x) => x > 0)
      out result
    """

    val result = compiler.compile(source, "all-lambda-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have a higher-order node with AllTransform
    compiled.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(_.isInstanceOf[InlineTransform.AllTransform])
    ) shouldBe true
  }

  it should "compile any with lambda expression" in {
    val compiler = hofCompiler

    val source = """
      in items: List<Int>
      result = any(items, (x) => x > 0)
      out result
    """

    val result = compiler.compile(source, "any-lambda-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have a higher-order node with AnyTransform
    compiled.dagSpec.data.values.exists(d =>
      d.name.contains("hof") && d.inlineTransform.exists(_.isInstanceOf[InlineTransform.AnyTransform])
    ) shouldBe true
  }

  it should "compile lambda with explicit type annotation" in {
    val compiler = hofCompiler

    val source = """
      in items: List<Int>
      result = filter(items, (x: Int) => x > 0)
      out result
    """

    val result = compiler.compile(source, "typed-lambda-dag")
    result.isRight shouldBe true
  }

  it should "compile lambda with boolean operators in body" in {
    val compiler = hofCompiler

    val source = """
      in items: List<Int>
      result = filter(items, (x) => x > 0 and x < 100)
      out result
    """

    val result = compiler.compile(source, "bool-lambda-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.data.values.exists(d =>
      d.inlineTransform.exists(_.isInstanceOf[InlineTransform.FilterTransform])
    ) shouldBe true
  }

  it should "generate correct output type for filter" in {
    val compiler = hofCompiler

    val source = """
      in items: List<Int>
      result = filter(items, (x) => x > 0)
      out result
    """

    val result = compiler.compile(source, "filter-output-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    resultBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(resultBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.CList(CType.CInt)
  }

  it should "generate correct output type for all" in {
    val compiler = hofCompiler

    val source = """
      in items: List<Int>
      result = all(items, (x) => x > 0)
      out result
    """

    val result = compiler.compile(source, "all-output-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    resultBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(resultBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.CBoolean
  }

  it should "report error when lambda body type mismatches expected return type" in {
    val compiler = hofCompiler

    val source = """
      in items: List<Int>
      result = filter(items, (x) => x)
      out result
    """
    // filter expects (Int) => Boolean, but lambda returns Int

    val result = compiler.compile(source, "error-dag")
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  it should "report error when lambda parameter type mismatches" in {
    val compiler = hofCompiler

    val source = """
      in items: List<Int>
      result = filter(items, (x: String) => true)
      out result
    """
    // filter expects (Int) => Boolean, but lambda has String parameter

    val result = compiler.compile(source, "error-dag")
    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[CompileError.TypeMismatch]) shouldBe true
  }

  // Guard expression DAG compilation tests

  it should "compile guard expression with correct DAG structure" in {
    val compiler = LangCompiler.empty

    val source = """
      in value: Int
      in condition: Boolean
      result = value when condition
      out result
    """

    val result = compiler.compile(source, "guard-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Guard should create a data node with GuardTransform
    compiled.dagSpec.data.values.exists(d =>
      d.name.contains("guard") && d.inlineTransform.contains(InlineTransform.GuardTransform)
    ) shouldBe true
  }

  it should "compile guard with correct Optional output type" in {
    val compiler = LangCompiler.empty

    val source = """
      in value: Int
      in condition: Boolean
      result = value when condition
      out result
    """

    val result = compiler.compile(source, "guard-type-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    resultBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(resultBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.COptional(CType.CInt)
  }

  it should "compile guard with record type" in {
    val compiler = LangCompiler.empty

    val source = """
      in person: { name: String, age: Int }
      in isActive: Boolean
      result = person when isActive
      out result
    """

    val result = compiler.compile(source, "guard-record-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    val outputNode    = compiled.dagSpec.data.get(resultBinding.get)
    outputNode.get.cType match {
      case CType.COptional(CType.CProduct(fields)) =>
        fields.keys should contain allOf ("name", "age")
      case other => fail(s"Expected COptional(CProduct(...)), got $other")
    }
  }

  it should "compile chained guards correctly" in {
    val compiler = LangCompiler.empty

    val source = """
      in value: Int
      in cond1: Boolean
      in cond2: Boolean
      step1 = value when cond1
      step2 = step1 when cond2
      out step2
    """

    val result = compiler.compile(source, "chained-guard-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have two guard transforms
    val guardNodes = compiled.dagSpec.data.values.filter(d =>
      d.inlineTransform.contains(InlineTransform.GuardTransform)
    )
    guardNodes should have size 2
  }

  // Coalesce operator DAG compilation tests

  it should "compile coalesce expression with correct DAG structure" in {
    val compiler = LangCompiler.empty

    val source = """
      in maybeValue: Optional<Int>
      in fallback: Int
      result = maybeValue ?? fallback
      out result
    """

    val result = compiler.compile(source, "coalesce-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Coalesce should create a data node with CoalesceTransform
    compiled.dagSpec.data.values.exists(d =>
      d.name.contains("coalesce") && d.inlineTransform.contains(InlineTransform.CoalesceTransform)
    ) shouldBe true
  }

  it should "compile coalesce with correct unwrapped output type" in {
    val compiler = LangCompiler.empty

    val source = """
      in maybeValue: Optional<Int>
      in fallback: Int
      result = maybeValue ?? fallback
      out result
    """

    val result = compiler.compile(source, "coalesce-type-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    val outputNode    = compiled.dagSpec.data.get(resultBinding.get)
    // Should be Int (unwrapped), not Optional<Int>
    outputNode.get.cType shouldBe CType.CInt
  }

  it should "compile coalesce between two Optionals returning Optional" in {
    val compiler = LangCompiler.empty

    val source = """
      in primary: Optional<Int>
      in secondary: Optional<Int>
      result = primary ?? secondary
      out result
    """

    val result = compiler.compile(source, "coalesce-optional-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    val outputNode    = compiled.dagSpec.data.get(resultBinding.get)
    // Optional ?? Optional returns Optional
    outputNode.get.cType shouldBe CType.COptional(CType.CInt)
  }

  it should "compile chained coalesce correctly" in {
    val compiler = LangCompiler.empty

    val source = """
      in first: Optional<Int>
      in second: Optional<Int>
      in last: Int
      result = first ?? second ?? last
      out result
    """

    val result = compiler.compile(source, "chained-coalesce-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have two coalesce transforms
    val coalesceNodes = compiled.dagSpec.data.values.filter(d =>
      d.inlineTransform.contains(InlineTransform.CoalesceTransform)
    )
    coalesceNodes should have size 2

    // Final result should be Int
    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    val outputNode    = compiled.dagSpec.data.get(resultBinding.get)
    outputNode.get.cType shouldBe CType.CInt
  }

  // Guard + Coalesce combined DAG tests

  it should "compile guard with coalesce combination" in {
    val compiler = LangCompiler.empty

    val source = """
      in value: Int
      in condition: Boolean
      in fallback: Int
      result = value when condition ?? fallback
      out result
    """

    val result = compiler.compile(source, "guard-coalesce-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have both guard and coalesce transforms
    compiled.dagSpec.data.values.exists(
      _.inlineTransform.contains(InlineTransform.GuardTransform)
    ) shouldBe true
    compiled.dagSpec.data.values.exists(
      _.inlineTransform.contains(InlineTransform.CoalesceTransform)
    ) shouldBe true

    // Final result should be Int (unwrapped by coalesce)
    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    val outputNode    = compiled.dagSpec.data.get(resultBinding.get)
    outputNode.get.cType shouldBe CType.CInt
  }

  it should "compile multiple guards with single coalesce fallback" in {
    val compiler = LangCompiler.empty

    val source = """
      in a: Int
      in b: Int
      in cond1: Boolean
      in cond2: Boolean
      in fallback: Int
      result = a when cond1 ?? b when cond2 ?? fallback
      out result
    """

    val result = compiler.compile(source, "multi-guard-coalesce-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have two guards and two coalesce operations
    val guardNodes = compiled.dagSpec.data.values.filter(
      _.inlineTransform.contains(InlineTransform.GuardTransform)
    )
    guardNodes should have size 2

    val coalesceNodes = compiled.dagSpec.data.values.filter(
      _.inlineTransform.contains(InlineTransform.CoalesceTransform)
    )
    coalesceNodes should have size 2
  }

  // Branch with guard/coalesce DAG tests

  it should "compile branch with guard in arms" in {
    val compiler = LangCompiler.empty

    val source = """
      in flag: Boolean
      in value: Int
      in condition: Boolean
      result = branch {
        flag -> value when condition,
        otherwise -> value when not condition
      }
      out result
    """

    val result = compiler.compile(source, "branch-guard-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have a branch module
    compiled.dagSpec.modules.values.exists(_.name.contains("branch")) shouldBe true
    // Should have guard transforms
    compiled.dagSpec.data.values.exists(
      _.inlineTransform.contains(InlineTransform.GuardTransform)
    ) shouldBe true
  }

  it should "compile branch with coalesce in arms" in {
    val compiler = LangCompiler.empty

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

    val result = compiler.compile(source, "branch-coalesce-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have a branch module
    compiled.dagSpec.modules.values.exists(_.name.contains("branch")) shouldBe true
    // Should have coalesce transforms
    compiled.dagSpec.data.values.exists(
      _.inlineTransform.contains(InlineTransform.CoalesceTransform)
    ) shouldBe true
  }

  it should "compile branch with guard + coalesce in arms" in {
    val compiler = LangCompiler.empty

    val source = """
      in selector: Boolean
      in value: Int
      in cond: Boolean
      in fallback: Int
      result = branch {
        selector -> value when cond ?? fallback,
        otherwise -> fallback
      }
      out result
    """

    val result = compiler.compile(source, "branch-guard-coalesce-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have branch, guard, and coalesce
    compiled.dagSpec.modules.values.exists(_.name.contains("branch")) shouldBe true
    compiled.dagSpec.data.values.exists(
      _.inlineTransform.contains(InlineTransform.GuardTransform)
    ) shouldBe true
    compiled.dagSpec.data.values.exists(
      _.inlineTransform.contains(InlineTransform.CoalesceTransform)
    ) shouldBe true
  }

  // Complex orchestration DAG tests

  it should "compile complex orchestration pipeline DAG" in {
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        name = "gt",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SBoolean,
        moduleName = "gt"
      )
    )

    val compiler = LangCompiler(registry, Map.empty)

    val source = """
      in primaryData: Optional<Int>
      in secondaryData: Optional<Int>
      in threshold: Int

      selected = primaryData ?? secondaryData ?? 0
      validated = selected when selected > threshold
      result = validated ?? 0
      out result
    """

    val result = compiler.compile(source, "orchestration-pipeline-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get

    // Verify DAG has coalesce nodes for primary ?? secondary ?? 0
    val coalesceNodes = compiled.dagSpec.data.values.filter(
      _.inlineTransform.contains(InlineTransform.CoalesceTransform)
    ).toList
    coalesceNodes.size should be >= 2

    // Verify DAG has guard node for `selected when selected > threshold`
    compiled.dagSpec.data.values.exists(
      _.inlineTransform.contains(InlineTransform.GuardTransform)
    ) shouldBe true

    // Verify comparison module for `selected > threshold`
    compiled.dagSpec.modules should not be empty

    // Final output should be Int
    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    val outputNode    = compiled.dagSpec.data.get(resultBinding.get)
    outputNode.get.cType shouldBe CType.CInt
  }

  it should "compile nested branch with guards and coalesce" in {
    val compiler = LangCompiler.empty

    val source = """
      in outer: Boolean
      in inner: Boolean
      in value: Int
      in cond: Boolean
      in fallback: Int
      result = branch {
        outer -> branch {
          inner -> value when cond ?? fallback,
          otherwise -> fallback
        },
        otherwise -> value when not cond ?? fallback
      }
      out result
    """

    val result = compiler.compile(source, "nested-branch-guard-coalesce-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get

    // Should have 2 branch modules (nested)
    val branchModules = compiled.dagSpec.modules.values.filter(_.name.contains("branch"))
    branchModules should have size 2

    // Should have guard and coalesce transforms
    compiled.dagSpec.data.values.exists(
      _.inlineTransform.contains(InlineTransform.GuardTransform)
    ) shouldBe true
    compiled.dagSpec.data.values.exists(
      _.inlineTransform.contains(InlineTransform.CoalesceTransform)
    ) shouldBe true
  }

  it should "compile guard with Candidates type" in {
    val compiler = LangCompiler.empty

    val source = """
      type Item = { id: Int, name: String }
      in items: Candidates<Item>
      in hasItems: Boolean
      result = items when hasItems
      out result
    """

    val result = compiler.compile(source, "guard-candidates-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    val outputNode    = compiled.dagSpec.data.get(resultBinding.get)
    outputNode.get.cType match {
      case CType.COptional(CType.CList(CType.CProduct(fields))) =>
        fields.keys should contain allOf ("id", "name")
      case other => fail(s"Expected COptional(CList(CProduct(...))), got $other")
    }
  }

  it should "compile coalesce with Candidates fallback" in {
    val compiler = LangCompiler.empty

    val source = """
      type Item = { id: Int }
      in maybeItems: Optional<Candidates<Item>>
      in defaultItems: Candidates<Item>
      result = maybeItems ?? defaultItems
      out result
    """

    val result = compiler.compile(source, "coalesce-candidates-dag")
    result.isRight shouldBe true

    val compiled      = result.toOption.get
    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    val outputNode    = compiled.dagSpec.data.get(resultBinding.get)
    outputNode.get.cType match {
      case CType.CList(CType.CProduct(fields)) =>
        fields.keys should contain("id")
      case other => fail(s"Expected CList(CProduct(...)), got $other")
    }
  }

  it should "compile guard with merge expression" in {
    val compiler = LangCompiler.empty

    val source = """
      in a: { x: Int }
      in b: { y: String }
      in cond: Boolean
      merged = a + b
      result = merged when cond
      out result
    """

    val result = compiler.compile(source, "guard-merge-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have merge and guard
    compiled.dagSpec.data.values.exists(
      _.inlineTransform.exists(_.isInstanceOf[InlineTransform.MergeTransform])
    ) shouldBe true
    compiled.dagSpec.data.values.exists(
      _.inlineTransform.contains(InlineTransform.GuardTransform)
    ) shouldBe true
  }

  // Union type compilation tests

  it should "compile program with simple union type input" in {
    val compiler = LangCompiler.empty

    val source = """
      in x: String | Int
      out x
    """

    val result = compiler.compile(source, "union-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.data should have size 1

    val inputNode = compiled.dagSpec.data.values.head
    inputNode.cType match {
      case CType.CUnion(structure) =>
        structure.keys should contain allOf ("String", "Int")
      case other => fail(s"Expected CUnion, got $other")
    }
  }

  it should "compile program with multi-member union type" in {
    val compiler = LangCompiler.empty

    val source = """
      in x: String | Int | Boolean
      out x
    """

    val result = compiler.compile(source, "multi-union-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val inputNode = compiled.dagSpec.data.values.head
    inputNode.cType match {
      case CType.CUnion(structure) =>
        structure should have size 3
        structure.keys should contain allOf ("String", "Int", "Boolean")
      case other => fail(s"Expected CUnion, got $other")
    }
  }

  it should "compile program with union type definition" in {
    val compiler = LangCompiler.empty

    val source = """
      type Result = String | Int
      in x: Result
      out x
    """

    val result = compiler.compile(source, "typedef-union-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val inputNode = compiled.dagSpec.data.values.head
    inputNode.cType shouldBe a[CType.CUnion]
  }

  it should "compile program with union of record types" in {
    val compiler = LangCompiler.empty

    val source = """
      type Success = { value: Int }
      type Error = { message: String }
      type Result = Success | Error
      in x: Result
      out x
    """

    val result = compiler.compile(source, "record-union-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val inputNode = compiled.dagSpec.data.values.head
    inputNode.cType match {
      case CType.CUnion(structure) =>
        structure should have size 2
        structure.values.forall(_.isInstanceOf[CType.CProduct]) shouldBe true
      case other => fail(s"Expected CUnion, got $other")
    }
  }

  it should "compile union type with function that returns union" in {
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        name = "parse-result",
        params = List("input" -> SemanticType.SString),
        returns = SemanticType.SUnion(Set(SemanticType.SInt, SemanticType.SString)),
        moduleName = "parse-result"
      )
    )

    val compiler = LangCompiler(registry, Map.empty)

    val source = """
      in input: String
      result = parse-result(input)
      out result
    """

    val result = compiler.compile(source, "func-union-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.modules should not be empty

    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    resultBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(resultBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe a[CType.CUnion]
  }

  it should "compile union type with function that accepts union parameter" in {
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        name = "handle-either",
        params = List("x" -> SemanticType.SUnion(Set(SemanticType.SInt, SemanticType.SString))),
        returns = SemanticType.SBoolean,
        moduleName = "handle-either"
      )
    )

    val compiler = LangCompiler(registry, Map.empty)

    val source = """
      in x: Int | String
      result = handle-either(x)
      out result
    """

    val result = compiler.compile(source, "param-union-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.modules should not be empty

    val resultBinding = compiled.dagSpec.outputBindings.get("result")
    resultBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(resultBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType shouldBe CType.CBoolean
  }

  it should "compile union type preserving correct structure through assignment" in {
    val compiler = LangCompiler.empty

    val source = """
      in x: String | Int
      y = x
      out y
    """

    val result = compiler.compile(source, "assign-union-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val resultBinding = compiled.dagSpec.outputBindings.get("y")
    resultBinding.isDefined shouldBe true

    val outputNode = compiled.dagSpec.data.get(resultBinding.get)
    outputNode.isDefined shouldBe true
    outputNode.get.cType match {
      case CType.CUnion(structure) =>
        structure.keys should contain allOf ("String", "Int")
      case other => fail(s"Expected CUnion, got $other")
    }
  }

  it should "compile union with Optional member" in {
    val compiler = LangCompiler.empty

    val source = """
      in x: Optional<Int> | String
      out x
    """

    val result = compiler.compile(source, "optional-union-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val inputNode = compiled.dagSpec.data.values.head
    inputNode.cType match {
      case CType.CUnion(structure) =>
        structure should have size 2
        structure.keys should contain("String")
        // Should have an Optional<Int> member
        structure.values.exists {
          case CType.COptional(CType.CInt) => true
          case _ => false
        } shouldBe true
      case other => fail(s"Expected CUnion, got $other")
    }
  }

  it should "compile union with List member" in {
    val compiler = LangCompiler.empty

    val source = """
      in x: List<Int> | String
      out x
    """

    val result = compiler.compile(source, "list-union-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val inputNode = compiled.dagSpec.data.values.head
    inputNode.cType match {
      case CType.CUnion(structure) =>
        structure should have size 2
        structure.keys should contain("String")
        // Should have a List<Int> member
        structure.values.exists {
          case CType.CList(CType.CInt) => true
          case _ => false
        } shouldBe true
      case other => fail(s"Expected CUnion, got $other")
    }
  }

  it should "compile union with Candidates member" in {
    val compiler = LangCompiler.empty

    val source = """
      type Item = { id: Int }
      in x: Candidates<Item> | String
      out x
    """

    val result = compiler.compile(source, "candidates-union-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val inputNode = compiled.dagSpec.data.values.head
    inputNode.cType match {
      case CType.CUnion(structure) =>
        structure should have size 2
        structure.keys should contain("String")
        // Should have a Candidates<Item> member (represented as CList)
        structure.values.exists {
          case CType.CList(CType.CProduct(_)) => true
          case _ => false
        } shouldBe true
      case other => fail(s"Expected CUnion, got $other")
    }
  }

  it should "flatten nested union types in compilation" in {
    val compiler = LangCompiler.empty

    val source = """
      type A = String | Int
      type B = Boolean | Float
      type Combined = A | B
      in x: Combined
      out x
    """

    val result = compiler.compile(source, "nested-union-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val inputNode = compiled.dagSpec.data.values.head
    inputNode.cType match {
      case CType.CUnion(structure) =>
        // Should be flattened to 4 members
        structure should have size 4
        structure.keys should contain allOf ("String", "Int", "Boolean", "Float")
      case other => fail(s"Expected CUnion with 4 members, got $other")
    }
  }

  it should "generate correct CUnion tag names for record types" in {
    val compiler = LangCompiler.empty

    val source = """
      type Success = { value: Int }
      type Failure = { error: String }
      type Result = Success | Failure
      in x: Result
      out x
    """

    val result = compiler.compile(source, "tagged-union-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val inputNode = compiled.dagSpec.data.values.head
    inputNode.cType match {
      case CType.CUnion(structure) =>
        structure should have size 2
        // Record types should have generated tag names
        structure.values.forall(_.isInstanceOf[CType.CProduct]) shouldBe true
      case other => fail(s"Expected CUnion, got $other")
    }
  }
}
