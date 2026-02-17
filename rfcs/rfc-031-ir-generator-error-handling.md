# RFC-031: IRGenerator Error Handling Refactor

- **Status:** Implemented
- **Created:** 2026-02-16
- **Author:** Claude (discovered during RFC-030 review)

## Summary

Refactor `IRGenerator` to return compilation errors via `Either` instead of
throwing `IllegalStateException`, making the compiler pipeline robust against
unexpected IR generation failures without requiring defensive `try/catch` or
`.handleErrorWith` at every call site.

## Motivation

`IRGenerator` currently throws `IllegalStateException` for unrecoverable
conditions (undefined variables, lambdas in invalid contexts, unknown
higher-order functions). These exceptions bypass the compiler's structured
error path (`Either[List[CompileError], ...]`) and surface as failed IO
effects that callers must catch individually.

This already caused a P0 server crash (PR #223): `PipelineLoader` called
`compileIO` which wraps `compile` in `IO(...)`. When `IRGenerator` threw,
the exception became a failed IO that bypassed the `Left(errors)` handler
and crashed the server. The fix was adding `.handleErrorWith` at the call
site — but this is defensive, not structural.

### Problems with Throwing

1. **Every caller must defend independently.** Any new code calling
   `compile` / `compileIO` must remember to catch exceptions. Forgetting
   causes silent crash vectors.
2. **Error messages are poor.** `IllegalStateException` loses source span
   information. Structured `CompileError` includes file location, severity,
   and context for LSP diagnostics.
3. **Inconsistent error model.** Parse errors and type errors flow through
   `Either[List[CompileError], ...]`. IR generation errors throw. Callers
   see two different failure modes from the same `compile()` call.

## Proposed Solution

### Change `IRGenerator.generate` Return Type

```scala
// Before
def generate(program: TypedPipeline): IRPipeline

// After
def generate(program: TypedPipeline): Either[List[CompileError], IRPipeline]
```

### Replace `throw` with `Left`

There are currently 3 throw sites in `IRGenerator`:

| Location | Current Exception | Proposed CompileError |
|----------|-------------------|----------------------|
| `VarRef` case (line ~90) | `IllegalStateException("Undefined variable in IR generation: $name")` | `CompileError.undefinedVariable(name, span)` |
| `Lambda` case (line ~249) | `IllegalStateException("Lambda expression at $span cannot be used in this context...")` | `CompileError.invalidLambdaContext(span)` |
| `getHigherOrderOp` (line ~307) | `IllegalArgumentException("Unknown higher-order function: $moduleName")` | `CompileError.unknownHigherOrderFunction(moduleName, span)` |

### Thread `Either` Through Internal Methods

`generateExpression` and `generateDeclarations` currently return
`(GenContext, UUID)`. After the refactor:

```scala
// Before
private def generateExpression(expr: TypedExpression, ctx: GenContext): (GenContext, UUID)

// After
private def generateExpression(
    expr: TypedExpression, ctx: GenContext
): Either[List[CompileError], (GenContext, UUID)]
```

This threads errors up through the call chain without exceptions.

### Update `LangCompilerImpl.compile`

The `compile` method chains parse → type-check → IR generation. Currently
IR generation is the only step that can throw. After the refactor, it returns
`Either` like the other steps:

```scala
// Simplified flow
for {
  parsed     <- parser.parse(source)           // Either
  typed      <- typeChecker.check(parsed)      // Either
  ir         <- IRGenerator.generate(typed)    // Either (NEW)
  pipeline   <- DagCompiler.compile(ir, ...)   // Either
} yield pipeline
```

## Key Files

- `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/IRGenerator.scala`
  — all 3 throw sites, `generate`, `generateExpression`, `generateDeclarations`
- `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/CompilerError.scala`
  — add new error variants for IR generation failures
- `modules/lang-compiler/src/main/scala/io/constellation/lang/LangCompilerImpl.scala`
  — update `compile` to handle `Either` from IR generation
- `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/DagCompiler.scala`
  — may need adjustment if it calls `IRGenerator.generate` directly

## Impact

### PipelineLoader `.handleErrorWith` — Keep or Remove?

The `.handleErrorWith` added in PR #223 becomes redundant for IR generation
errors since they would flow through `Left`. However, **keep it** — it
provides defense-in-depth against future unexpected exceptions from any part
of the compilation pipeline (parser bugs, serialization errors, etc.).

### LSP Diagnostics

Structured `CompileError` from IR generation means the LSP server can show
proper diagnostics for closure errors (red squiggly on the captured variable)
instead of silent failures.

### Test Impact

Existing tests that use `intercept[IllegalStateException]` (e.g.,
`CompilerErrorTest`) would need updating to check for `Left(errors)` instead.

## Alternatives Considered

1. **Wrap throws in `Try` at the `compile` boundary** — simpler but still
   loses span information and keeps the inconsistent error model
2. **Use `MonadError` / `EitherT`** — more principled but adds complexity
   to the internal API for limited benefit in synchronous code
3. **Keep throwing, add more `.handleErrorWith`** — current approach,
   works but fragile

## Priority

P2 — This is a code quality improvement that reduces crash risk and improves
error reporting. Not blocking any features, but should be done before adding
more IR generation features (like closures in RFC-030).
