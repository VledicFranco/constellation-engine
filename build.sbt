ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.1"
ThisBuild / organization := "io.constellation"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
)

// Logging dependencies (shared across modules)
val log4catsVersion = "2.6.0"
val logbackVersion = "1.4.11"

lazy val loggingDeps = Seq(
  "org.typelevel" %% "log4cats-core" % log4catsVersion,
  "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion % Runtime
)

// Core module - foundational types, no dependencies
lazy val core = project
  .in(file("modules/core"))
  .settings(
    name := "constellation-core",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.10.0",
      "org.typelevel" %% "cats-effect" % "3.5.2",
      "io.circe" %% "circe-core" % "0.14.6",
      "io.circe" %% "circe-generic" % "0.14.6",
      "io.circe" %% "circe-parser" % "0.14.6",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    )
  )

// Runtime module - execution engine
lazy val runtime = project
  .in(file("modules/runtime"))
  .dependsOn(core)
  .settings(
    name := "constellation-runtime",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.10.0",
      "org.typelevel" %% "cats-effect" % "3.5.2",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    )
  )

// Language AST - syntax tree definitions
lazy val langAst = (project in file("modules/lang-ast"))
  .dependsOn(core)
  .settings(
    name := "constellation-lang-ast",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    )
  )

// Language Parser - text to AST
lazy val langParser = (project in file("modules/lang-parser"))
  .dependsOn(langAst)
  .settings(
    name := "constellation-lang-parser",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-parse" % "1.0.0",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    )
  )

// Language Compiler - AST to executable DAG
lazy val langCompiler = (project in file("modules/lang-compiler"))
  .dependsOn(langAst, langParser, runtime)
  .settings(
    name := "constellation-lang-compiler",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.10.0",
      "org.typelevel" %% "cats-effect" % "3.5.2",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    )
  )

// Standard Library - built-in modules and examples
lazy val langStdlib = (project in file("modules/lang-stdlib"))
  .dependsOn(runtime, langCompiler)
  .settings(
    name := "constellation-lang-stdlib",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    )
  )

// Language Server Protocol - LSP server for constellation-lang
lazy val langLsp = (project in file("modules/lang-lsp"))
  .dependsOn(runtime, langCompiler)
  .settings(
    name := "constellation-lang-lsp",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.10.0",
      "org.typelevel" %% "cats-effect" % "3.5.2",
      "io.circe" %% "circe-core" % "0.14.6",
      "io.circe" %% "circe-generic" % "0.14.6",
      "io.circe" %% "circe-parser" % "0.14.6",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    ) ++ loggingDeps
  )

// HTTP API - REST API server for compiler and runtime with WebSocket LSP support
lazy val httpApi = (project in file("modules/http-api"))
  .dependsOn(runtime, langCompiler, langStdlib, langLsp)
  .settings(
    name := "constellation-http-api",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % "0.23.25",
      "org.http4s" %% "http4s-ember-client" % "0.23.25",
      "org.http4s" %% "http4s-dsl" % "0.23.25",
      "org.http4s" %% "http4s-circe" % "0.23.25",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    ) ++ loggingDeps
  )

// Example Application - demonstrates library usage
lazy val exampleApp = (project in file("modules/example-app"))
  .dependsOn(runtime, langCompiler, langStdlib, httpApi)
  .settings(
    name := "constellation-example-app",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    ) ++ loggingDeps
  )

// Root project aggregates all modules
lazy val root = (project in file("."))
  .aggregate(
    core,
    runtime,
    langAst,
    langParser,
    langCompiler,
    langStdlib,
    langLsp,
    httpApi,
    exampleApp
  )
  .settings(
    name := "constellation-engine",
    publish / skip := true
  )
