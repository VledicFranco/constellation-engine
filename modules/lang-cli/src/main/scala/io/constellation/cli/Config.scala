package io.constellation.cli

import java.nio.file.{Files, Path, Paths}

import cats.effect.IO
import cats.implicits.*

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import io.circe.parser.decode
import io.circe.syntax.*

import org.http4s.Uri

/** Output format for CLI results. */
enum OutputFormat:
  case Human, Json

object OutputFormat:
  given Encoder[OutputFormat] = Encoder.encodeString.contramap(_.toString.toLowerCase)
  given Decoder[OutputFormat] = Decoder.decodeString.emap {
    case "human" => Right(Human)
    case "json"  => Right(Json)
    case other   => Left(s"Invalid output format: $other")
  }

/** Server configuration. */
case class ServerConfig(
    url: String = "http://localhost:8080",
    token: Option[String] = None
)

object ServerConfig:
  given Encoder[ServerConfig] = deriveEncoder
  given Decoder[ServerConfig] = Decoder.instance { c =>
    for
      url   <- c.downField("url").as[Option[String]].map(_.getOrElse("http://localhost:8080"))
      token <- c.downField("token").as[Option[String]]
    yield ServerConfig(url, token)
  }

/** Default settings for CLI behavior. */
case class DefaultsConfig(
    output: OutputFormat = OutputFormat.Human,
    vizFormat: String = "dot"
)

object DefaultsConfig:
  given Encoder[DefaultsConfig] = Encoder.instance { d =>
    io.circe.Json.obj(
      "output"     -> io.circe.Json.fromString(d.output.toString.toLowerCase),
      "viz_format" -> io.circe.Json.fromString(d.vizFormat)
    )
  }
  given Decoder[DefaultsConfig] = Decoder.instance { c =>
    for
      output    <- c.downField("output").as[Option[OutputFormat]].map(_.getOrElse(OutputFormat.Human))
      vizFormat <- c.downField("viz_format").as[Option[String]].map(_.getOrElse("dot"))
    yield DefaultsConfig(output, vizFormat)
  }

/** CLI configuration loaded from file, environment, and command-line flags. */
case class CliConfig(
    server: ServerConfig = ServerConfig(),
    defaults: DefaultsConfig = DefaultsConfig()
):
  /** Get the server URL as an http4s Uri. */
  def serverUri: Either[String, Uri] =
    Uri.fromString(server.url).leftMap(_.message)

  /** Get the effective output format, with optional CLI override. */
  def effectiveOutput(jsonFlag: Boolean): OutputFormat =
    if jsonFlag then OutputFormat.Json else defaults.output

object CliConfig:
  given Encoder[CliConfig] = deriveEncoder
  given Decoder[CliConfig] = Decoder.instance { c =>
    for
      server   <- c.downField("server").as[Option[ServerConfig]].map(_.getOrElse(ServerConfig()))
      defaults <- c.downField("defaults").as[Option[DefaultsConfig]].map(_.getOrElse(DefaultsConfig()))
    yield CliConfig(server, defaults)
  }

  /** Default configuration directory. */
  def configDir: Path =
    Paths.get(System.getProperty("user.home"), ".constellation")

  /** Default configuration file path. */
  def configPath: Path =
    configDir.resolve("config.json")

  /** Load configuration with precedence: CLI flags > env vars > config file > defaults. */
  def load(
      serverUrl: Option[String] = None,
      token: Option[String] = None,
      jsonOutput: Boolean = false
  ): IO[CliConfig] =
    for
      // 1. Load from config file (if exists)
      fileConfig <- loadFromFile

      // 2. Override with environment variables
      envUrl   = sys.env.get("CONSTELLATION_SERVER_URL")
      envToken = sys.env.get("CONSTELLATION_TOKEN")
      envConfig = fileConfig.copy(
        server = fileConfig.server.copy(
          url = envUrl.getOrElse(fileConfig.server.url),
          token = envToken.orElse(fileConfig.server.token)
        )
      )

      // 3. Override with CLI flags
      finalConfig = envConfig.copy(
        server = envConfig.server.copy(
          url = serverUrl.getOrElse(envConfig.server.url),
          token = token.orElse(envConfig.server.token)
        ),
        defaults = envConfig.defaults.copy(
          output = if jsonOutput then OutputFormat.Json else envConfig.defaults.output
        )
      )
    yield finalConfig

  /** Load configuration from file, returning defaults if file doesn't exist. */
  def loadFromFile: IO[CliConfig] =
    IO.blocking {
      val path = configPath
      if Files.exists(path) then
        val content = Files.readString(path)
        decode[CliConfig](content).getOrElse(CliConfig())
      else CliConfig()
    }.handleError(_ => CliConfig())

  /** Save configuration to file. */
  def save(config: CliConfig): IO[Unit] =
    IO.blocking {
      val dir = configDir
      if !Files.exists(dir) then Files.createDirectories(dir)
      Files.writeString(configPath, config.asJson.spaces2)
    }

  /** Get a specific config value by dot-separated path. */
  def getValue(path: String): IO[Option[String]] =
    loadFromFile.map { config =>
      val json = config.asJson
      resolvePath(json, path.split("\\.").toList)
    }

  /** Set a specific config value by dot-separated path. */
  def setValue(path: String, value: String): IO[Unit] =
    for
      config <- loadFromFile
      json    = config.asJson
      updated = updatePath(json, path.split("\\.").toList, value)
      newConfig <- IO.fromEither(
        updated.as[CliConfig].leftMap(e => new RuntimeException(s"Invalid config: ${e.message}"))
      )
      _ <- save(newConfig)
    yield ()

  /** Resolve a value from JSON by path. */
  private def resolvePath(json: io.circe.Json, path: List[String]): Option[String] =
    path match
      case Nil         => json.asString.orElse(json.asNumber.map(_.toString)).orElse(Some(json.noSpaces))
      case head :: tail =>
        json.asObject.flatMap(_.apply(head)).flatMap(resolvePath(_, tail))

  /** Update a value in JSON by path. */
  private def updatePath(json: io.circe.Json, path: List[String], value: String): io.circe.Json =
    path match
      case Nil => io.circe.Json.fromString(value)
      case head :: tail =>
        val obj = json.asObject.getOrElse(io.circe.JsonObject.empty)
        val current = obj(head).getOrElse(io.circe.Json.Null)
        io.circe.Json.fromJsonObject(obj.add(head, updatePath(current, tail, value)))
