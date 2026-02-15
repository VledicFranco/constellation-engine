package io.constellation.invariants

import io.github.vledicfranco.organon.testing.adapters.OrganonFlatSpec
import io.github.vledicfranco.organon.testing.*

import java.nio.file.Path

class CoreInvariantsSpec extends OrganonFlatSpec:

  private val projectRoot = Path.of(".").toAbsolutePath.normalize

  // ---------------------------------------------------------------------------
  // INV-ROOT-3: Core type system has no side-effect imports
  // ---------------------------------------------------------------------------

  testInvariant("INV-ROOT-3", "Core type system has no side-effect imports"):
    Assertions.assertNoSideEffects(NoSideEffectsOptions(
      files = Seq("modules/core/src/main/scala/**/*.scala"),
      forbiddenModules = Seq("java.io", "scala.io.Source", "java.nio.file.Files"),
      cwd = Some(projectRoot)
    ))

  // ---------------------------------------------------------------------------
  // INV-CORE-1: TypeSystem exports CValue, CType, CTypeTag
  // ---------------------------------------------------------------------------

  testInvariant("INV-CORE-1", "TypeSystem exports CValue, CType, CTypeTag"):
    Assertions.assertExportsPresent(ExportsPresentOptions(
      file = "modules/core/src/main/scala/io/constellation/TypeSystem.scala",
      expectedExports = Seq("CValue", "CType", "CTypeTag"),
      cwd = Some(projectRoot)
    ))

  // ---------------------------------------------------------------------------
  // INV-CORE-6: Error hierarchy exports ConstellationError, TypeError, etc.
  // ---------------------------------------------------------------------------

  testInvariant("INV-CORE-6", "Error hierarchy exports ConstellationError, TypeError, CompilerError, RuntimeError"):
    Assertions.assertExportsPresent(ExportsPresentOptions(
      file = "modules/core/src/main/scala/io/constellation/ConstellationError.scala",
      expectedExports = Seq("ConstellationError", "TypeError", "CompilerError", "RuntimeError"),
      cwd = Some(projectRoot)
    ))
