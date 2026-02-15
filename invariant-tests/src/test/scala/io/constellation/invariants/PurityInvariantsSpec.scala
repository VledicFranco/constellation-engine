package io.constellation.invariants

import io.github.vledicfranco.organon.testing.adapters.OrganonFlatSpec
import io.github.vledicfranco.organon.testing.*

import java.nio.file.Path

class PurityInvariantsSpec extends OrganonFlatSpec:

  private val projectRoot = Path.of(".").toAbsolutePath.normalize

  // ---------------------------------------------------------------------------
  // INV-RUNTIME-1a: Stdlib modules don't import mutable collections
  // ---------------------------------------------------------------------------

  testInvariant("INV-RUNTIME-1a", "Stdlib modules don't import mutable collections"):
    Assertions.assertNoSideEffects(
      NoSideEffectsOptions(
        files = Seq("modules/lang-stdlib/src/main/scala/**/*.scala"),
        forbiddenModules = Seq(
          "scala.collection.mutable",
          "java.util.ArrayList",
          "java.util.HashMap"
        ),
        cwd = Some(projectRoot)
      )
    )

  // ---------------------------------------------------------------------------
  // INV-ROOT-3a: Core type system doesn't use unsafe runtime
  // ---------------------------------------------------------------------------

  testInvariant("INV-ROOT-3a", "Core type system doesn't use unsafe runtime"):
    Assertions.assertNoSideEffects(
      NoSideEffectsOptions(
        files = Seq("modules/core/src/main/scala/**/*.scala"),
        forbiddenModules = Seq("cats.effect.unsafe"),
        cwd = Some(projectRoot)
      )
    )

  // ---------------------------------------------------------------------------
  // INV-ROOT-3b: AST module has no side-effect imports
  // ---------------------------------------------------------------------------

  testInvariant("INV-ROOT-3b", "AST module has no side-effect imports"):
    Assertions.assertNoSideEffects(
      NoSideEffectsOptions(
        files = Seq("modules/lang-ast/src/main/scala/**/*.scala"),
        forbiddenModules =
          Seq("java.io", "scala.io.Source", "java.nio.file.Files", "cats.effect.IO"),
        cwd = Some(projectRoot)
      )
    )
