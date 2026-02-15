package io.constellation.invariants

import java.nio.file.Path

import io.github.vledicfranco.organon.testing.*
import io.github.vledicfranco.organon.testing.adapters.OrganonFlatSpec

class StructuralInvariantsSpec extends OrganonFlatSpec:

  private val projectRoot = Path.of(".").toAbsolutePath.normalize

  // ---------------------------------------------------------------------------
  // INV-ORGANON-3: Every component directory has README.md
  // ---------------------------------------------------------------------------

  testInvariant("INV-ORGANON-3", "Every organon component has README.md"):
    Assertions.assertFileExists(
      FileExistsOptions(
        files = Seq(
          "organon/components/cli/README.md",
          "organon/components/compiler/README.md",
          "organon/components/core/README.md",
          "organon/components/http-api/README.md",
          "organon/components/lsp/README.md",
          "organon/components/module-provider/README.md",
          "organon/components/runtime/README.md",
          "organon/components/stdlib/README.md",
          "organon/components/typescript-sdk/README.md"
        ),
        cwd = Some(projectRoot)
      )
    )

  // ---------------------------------------------------------------------------
  // INV-ORGANON-4: Every component has ETHOS.md
  // ---------------------------------------------------------------------------

  testInvariant("INV-ORGANON-4", "Every organon component has ETHOS.md"):
    Assertions.assertFileExists(
      FileExistsOptions(
        files = Seq(
          "organon/components/cli/ETHOS.md",
          "organon/components/compiler/ETHOS.md",
          "organon/components/core/ETHOS.md",
          "organon/components/http-api/ETHOS.md",
          "organon/components/lsp/ETHOS.md",
          "organon/components/module-provider/ETHOS.md",
          "organon/components/runtime/ETHOS.md",
          "organon/components/stdlib/ETHOS.md",
          "organon/components/typescript-sdk/ETHOS.md"
        ),
        cwd = Some(projectRoot)
      )
    )

  // ---------------------------------------------------------------------------
  // INV-ORGANON-4-FEAT: Every feature has ETHOS.md + PHILOSOPHY.md
  // ---------------------------------------------------------------------------

  testInvariant("INV-ORGANON-4-FEAT", "Every organon feature has ETHOS.md and PHILOSOPHY.md"):
    Assertions.assertFileExists(
      FileExistsOptions(
        files = Seq(
          "organon/features/execution/ETHOS.md",
          "organon/features/execution/PHILOSOPHY.md",
          "organon/features/extensibility/ETHOS.md",
          "organon/features/extensibility/PHILOSOPHY.md",
          "organon/features/parallelization/ETHOS.md",
          "organon/features/parallelization/PHILOSOPHY.md",
          "organon/features/resilience/ETHOS.md",
          "organon/features/resilience/PHILOSOPHY.md",
          "organon/features/tooling/ETHOS.md",
          "organon/features/tooling/PHILOSOPHY.md",
          "organon/features/type-safety/ETHOS.md",
          "organon/features/type-safety/PHILOSOPHY.md"
        ),
        cwd = Some(projectRoot)
      )
    )

  // ---------------------------------------------------------------------------
  // INV-STRUCT-1: Every module has a test directory
  // ---------------------------------------------------------------------------

  testInvariant("INV-STRUCT-1", "Every module has a test directory"):
    Assertions.assertFileExists(
      FileExistsOptions(
        files = Seq(
          "modules/core/src/test",
          "modules/runtime/src/test",
          "modules/lang-ast/src/test",
          "modules/lang-parser/src/test",
          "modules/lang-compiler/src/test",
          "modules/lang-stdlib/src/test",
          "modules/lang-lsp/src/test",
          "modules/http-api/src/test"
        ),
        cwd = Some(projectRoot)
      )
    )
