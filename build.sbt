ThisBuild / version := "0.2.0"
ThisBuild / scalaVersion := "3.3.1"
ThisBuild / organization := "io.constellation"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
)

// Scalafix semantic analysis (required for scalafix rules)
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

// Code coverage settings
ThisBuild / coverageEnabled := false // Enable via `sbt coverage` command
ThisBuild / coverageHighlighting := true
ThisBuild / coverageFailOnMinimum := false // Set to true in CI to enforce thresholds
ThisBuild / coverageMinimumStmtTotal := 60
ThisBuild / coverageMinimumBranchTotal := 50

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
    coverageMinimumStmtTotal := 80,
    coverageMinimumBranchTotal := 70,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.10.0",
      "org.typelevel" %% "cats-effect" % "3.5.2",
      "io.circe" %% "circe-core" % "0.14.6",
      "io.circe" %% "circe-generic" % "0.14.6",
      "io.circe" %% "circe-parser" % "0.14.6",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
      "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0" % Test,
    )
  )

// Runtime module - execution engine
lazy val runtime = project
  .in(file("modules/runtime"))
  .dependsOn(core)
  .settings(
    name := "constellation-runtime",
    coverageMinimumStmtTotal := 75,
    coverageMinimumBranchTotal := 65,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.10.0",
      "org.typelevel" %% "cats-effect" % "3.5.2",
      "com.fasterxml.jackson.core" % "jackson-core" % "2.15.3",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
      "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0" % Test,
    ) ++ loggingDeps
  )

// Language AST - syntax tree definitions
lazy val langAst = (project in file("modules/lang-ast"))
  .dependsOn(core)
  .settings(
    name := "constellation-lang-ast",
    coverageMinimumStmtTotal := 70,
    coverageMinimumBranchTotal := 60,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    )
  )

// Language Parser - text to AST
lazy val langParser = (project in file("modules/lang-parser"))
  .dependsOn(langAst)
  .settings(
    name := "constellation-lang-parser",
    coverageMinimumStmtTotal := 80,
    coverageMinimumBranchTotal := 70,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-parse" % "1.0.0",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
      "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0" % Test,
    )
  )

// Language Compiler - AST to executable DAG
lazy val langCompiler = (project in file("modules/lang-compiler"))
  .dependsOn(langAst, langParser, runtime)
  .settings(
    name := "constellation-lang-compiler",
    coverageMinimumStmtTotal := 75,
    coverageMinimumBranchTotal := 65,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.10.0",
      "org.typelevel" %% "cats-effect" % "3.5.2",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
      "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0" % Test,
    ) ++ loggingDeps
  )

// Standard Library - built-in modules and examples
lazy val langStdlib = (project in file("modules/lang-stdlib"))
  .dependsOn(runtime, langCompiler)
  .settings(
    name := "constellation-lang-stdlib",
    coverageMinimumStmtTotal := 70,
    coverageMinimumBranchTotal := 60,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    )
  )

// Language Server Protocol - LSP server for constellation-lang
lazy val langLsp = (project in file("modules/lang-lsp"))
  .dependsOn(runtime, langCompiler)
  .settings(
    name := "constellation-lang-lsp",
    coverageMinimumStmtTotal := 60,
    coverageMinimumBranchTotal := 50,
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
    coverageMinimumStmtTotal := 60,
    coverageMinimumBranchTotal := 50,
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
    coverageMinimumStmtTotal := 70,
    coverageMinimumBranchTotal := 60,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    ) ++ loggingDeps,
    // Fat JAR packaging via sbt-assembly
    assembly / mainClass := Some("io.constellation.examples.app.server.ExampleServer"),
    assembly / assemblyJarName := s"constellation-${version.value}.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", _*) => MergeStrategy.concat
      case PathList("META-INF", _*)             => MergeStrategy.discard
      case "reference.conf"                     => MergeStrategy.concat
      case "logback.xml"                        => MergeStrategy.first
      case _                                    => MergeStrategy.first
    }
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
