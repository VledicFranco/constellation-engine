package io.constellation.invariants

import io.github.vledicfranco.organon.testing.adapters.OrganonFlatSpec
import io.github.vledicfranco.organon.testing.*

import java.nio.file.Path

class NamingConventionSpec extends OrganonFlatSpec:

  private val projectRoot = Path.of(".").toAbsolutePath.normalize

  // ---------------------------------------------------------------------------
  // INV-NAMING-1: Core source files are PascalCase
  // ---------------------------------------------------------------------------

  testInvariant("INV-NAMING-1", "Core source files are PascalCase"):
    Assertions.assertNamingConvention(NamingConventionOptions(
      mode = NamingMode.Filenames,
      files = Seq("modules/core/src/main/scala/**/*.scala"),
      convention = Convention.PascalCase,
      exceptions = Set("package"),
      cwd = Some(projectRoot)
    ))

  // ---------------------------------------------------------------------------
  // INV-NAMING-2: Runtime source files are PascalCase
  // ---------------------------------------------------------------------------

  testInvariant("INV-NAMING-2", "Runtime source files are PascalCase"):
    Assertions.assertNamingConvention(NamingConventionOptions(
      mode = NamingMode.Filenames,
      files = Seq("modules/runtime/src/main/scala/**/*.scala"),
      convention = Convention.PascalCase,
      exceptions = Set("package"),
      cwd = Some(projectRoot)
    ))
