package io.constellation.http

import java.nio.file.Path

/** Strategy for creating human-readable aliases from loaded pipeline files. */
enum AliasStrategy {

  /** Use the file name stem as the alias (e.g. `scoring.cst` -> `"scoring"`). */
  case FileName

  /** Use the relative path without extension (e.g. `sub/scoring.cst` -> `"sub/scoring"`). */
  case RelativePath

  /** No alias created; pipelines are only accessible by structural hash. */
  case HashOnly
}

object AliasStrategy {

  /** Parse an alias strategy from a string (case-insensitive, supports hyphenated and camelCase).
    */
  def fromString(s: String): Option[AliasStrategy] = s.toLowerCase match {
    case "filename" | "file-name"         => Some(FileName)
    case "relativepath" | "relative-path" => Some(RelativePath)
    case "hashonly" | "hash-only"         => Some(HashOnly)
    case _                                => None
  }
}

/** Configuration for the startup pipeline loader.
  *
  * @param directory
  *   Directory to scan for `.cst` files
  * @param recursive
  *   Whether to scan subdirectories
  * @param failOnError
  *   If true, any compilation failure aborts server startup
  * @param aliasStrategy
  *   How to derive alias names from file paths
  */
case class PipelineLoaderConfig(
    directory: Path,
    recursive: Boolean = false,
    failOnError: Boolean = false,
    aliasStrategy: AliasStrategy = AliasStrategy.FileName
)

object PipelineLoaderConfig {

  /** Build a config from environment variables, if `CONSTELLATION_PIPELINE_DIR` is set. */
  def fromEnv: Option[PipelineLoaderConfig] =
    sys.env.get("CONSTELLATION_PIPELINE_DIR").map { dir =>
      PipelineLoaderConfig(
        directory = Path.of(dir),
        recursive = sys.env
          .get("CONSTELLATION_PIPELINE_RECURSIVE")
          .exists(v => v.equalsIgnoreCase("true") || v == "1"),
        failOnError = sys.env
          .get("CONSTELLATION_PIPELINE_FAIL_ON_ERROR")
          .exists(v => v.equalsIgnoreCase("true") || v == "1"),
        aliasStrategy = sys.env
          .get("CONSTELLATION_PIPELINE_ALIAS_STRATEGY")
          .flatMap(AliasStrategy.fromString)
          .getOrElse(AliasStrategy.FileName)
      )
    }
}
