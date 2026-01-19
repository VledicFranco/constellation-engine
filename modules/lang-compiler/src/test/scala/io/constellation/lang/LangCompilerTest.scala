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
      d.name.contains("merge") && d.inlineTransform.exists(_.isInstanceOf[InlineTransform.MergeTransform])
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
      d.name.contains("project") && d.inlineTransform.exists(_.isInstanceOf[InlineTransform.ProjectTransform])
    ) shouldBe true
  }

  it should "compile a program with registered functions" in {
    val registry = FunctionRegistry.empty
    registry.register(FunctionSignature(
      name = "double",
      params = List("n" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "double-module"
    ))

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
      d.name.contains("conditional") && d.inlineTransform.contains(InlineTransform.ConditionalTransform)
    ) shouldBe true
  }

  it should "compile the example program from design doc" in {
    val registry = FunctionRegistry.empty

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
    registry.register(FunctionSignature(
      name = "process",
      params = List("input" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "process-module"
    ))

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
      .withFunction(FunctionSignature(
        name = "transform",
        params = List("data" -> SemanticType.SString),
        returns = SemanticType.SString,
        moduleName = "transform-module"
      ))
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
    registry.register(FunctionSignature(
      name = "step1",
      params = List("x" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "step1"
    ))
    registry.register(FunctionSignature(
      name = "step2",
      params = List("x" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "step2"
    ))

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
    dataNodes.exists(d => d.name.contains("project") && d.inlineTransform.exists(_.isInstanceOf[InlineTransform.ProjectTransform])) shouldBe true
    dataNodes.exists(d => d.name.contains("merge") && d.inlineTransform.exists(_.isInstanceOf[InlineTransform.MergeTransform])) shouldBe true
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
    val aBinding = compiled.dagSpec.outputBindings("a")
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
    registry.register(FunctionSignature(
      name = "add",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "stdlib.add",
      namespace = Some("stdlib.math")
    ))

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
    registry.register(FunctionSignature(
      name = "add",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "stdlib.add",
      namespace = Some("stdlib.math")
    ))

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
    registry.register(FunctionSignature(
      name = "add",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "stdlib.add",
      namespace = Some("stdlib.math")
    ))

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
    registry.register(FunctionSignature(
      name = "add",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "stdlib.add",
      namespace = Some("stdlib.math")
    ))

    val modules = Map("stdlib.add" -> addModule)
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
    registry.register(FunctionSignature(
      name = "add",
      params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
      returns = SemanticType.SInt,
      moduleName = "stdlib.add",
      namespace = Some("stdlib.math")
    ))

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
      .withFunction(FunctionSignature(
        name = "add",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "stdlib.add",
        namespace = Some("stdlib.math")
      ))
      .withFunction(FunctionSignature(
        name = "upper",
        params = List("value" -> SemanticType.SString),
        returns = SemanticType.SString,
        moduleName = "stdlib.upper",
        namespace = Some("stdlib.string")
      ))
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
      d.name.contains("merge") && d.inlineTransform.exists(_.isInstanceOf[InlineTransform.MergeTransform])
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
    compiled.dagSpec.data.values.exists(_.inlineTransform.exists(_.isInstanceOf[InlineTransform.MergeTransform])) shouldBe true
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
      d.name.contains("merge") && d.inlineTransform.exists(_.isInstanceOf[InlineTransform.MergeTransform])
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
      d.name.contains("merge") && d.inlineTransform.exists(_.isInstanceOf[InlineTransform.MergeTransform])
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
      d.name.contains("merge") && d.inlineTransform.exists(_.isInstanceOf[InlineTransform.MergeTransform])
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

    val compiled = result.toOption.get
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
      d.name.contains("merge") && d.inlineTransform.exists(_.isInstanceOf[InlineTransform.MergeTransform])
    )
    mergeDataNodes should have size 2
  }
}
