ThisBuild / version := "0.6.1"
ThisBuild / scalaVersion := "3.3.1"
ThisBuild / organization := "io.github.vledicfranco"

// Maven Central POM metadata
ThisBuild / homepage := Some(url("https://github.com/VledicFranco/constellation-engine"))
ThisBuild / licenses := List("MIT" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / developers := List(
  Developer("VledicFranco", "Franco Vledicka", "", url("https://github.com/VledicFranco"))
)
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/VledicFranco/constellation-engine"),
    "scm:git@github.com:VledicFranco/constellation-engine.git"
  )
)

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

// Binary compatibility checking (MiMa)
// Baseline will be set to v1.0.0 once released. Pre-1.0, binary compat is not promised.
ThisBuild / mimaPreviousArtifacts := Set.empty
ThisBuild / mimaFailOnProblem := true

// Exclude benchmark, load, and Docker-dependent tests from default test execution
// Run benchmarks:    sbt "runtime/testOnly *Benchmark"
// Run memcached:     sbt "cacheMemcached/testOnly *MemcachedIntegrationTest"
ThisBuild / Test / testOptions += Tests.Filter(name =>
  !name.contains("Benchmark") && !name.contains("SustainedLoad") && !name.contains("MemcachedIntegrationTest")
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
    coverageMinimumStmtTotal := 67,
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
    coverageMinimumStmtTotal := 50,
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
    coverageMinimumStmtTotal := 54,
    coverageMinimumBranchTotal := 57,
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
    coverageMinimumStmtTotal := 13,
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
    coverageMinimumStmtTotal := 53,
    coverageMinimumBranchTotal := 81,
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
    coverageMinimumStmtTotal := 32,
    coverageMinimumBranchTotal := 49,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % "0.23.25",
      "org.http4s" %% "http4s-ember-client" % "0.23.25",
      "org.http4s" %% "http4s-dsl" % "0.23.25",
      "org.http4s" %% "http4s-circe" % "0.23.25",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    ) ++ loggingDeps
  )

// Module Provider - gRPC-based dynamic module registration protocol
lazy val moduleProvider = (project in file("modules/module-provider"))
  .dependsOn(runtime, langCompiler)
  .settings(
    name := "constellation-module-provider",
    publish / skip := true,
    // Coverage thresholds account for ~3750 non-excludable ScalaPB-generated statements.
    // Scala 3 built-in coverage doesn't support package/file exclusions (sbt-scoverage 2.x limitation).
    // Hand-written source: ~1600 stmts (incl. SDK), ~1100 testable (excl. gRPC impls), ~70% covered.
    // Diluted totals: stmt ≈ 12%, branch ≈ 11%. Thresholds set as ratchets on the diluted total.
    coverageExcludedPackages := "io\\.constellation\\.provider\\.v1\\..*",
    coverageMinimumStmtTotal := 13,
    coverageMinimumBranchTotal := 12,
    libraryDependencies ++= Seq(
      "io.grpc"               %  "grpc-netty-shaded"       % scalapb.compiler.Version.grpcJavaVersion,
      "com.thesamet.scalapb"  %% "scalapb-runtime-grpc"    % scalapb.compiler.Version.scalapbVersion,
      "com.thesamet.scalapb"  %% "scalapb-runtime"         % scalapb.compiler.Version.scalapbVersion % "protobuf",
      "org.scalatest"         %% "scalatest"               % "3.2.17" % Test,
    ) ++ loggingDeps,
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
    )
  )

// Cache Memcached - optional Memcached-backed distributed cache backend
lazy val cacheMemcached = (project in file("modules/cache-memcached"))
  .dependsOn(runtime)
  .settings(
    name := "constellation-cache-memcached",
    libraryDependencies ++= Seq(
      "net.spy" % "spymemcached" % "2.12.3",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
      "org.mockito" % "mockito-core" % "5.14.2" % Test,
      "org.testcontainers" % "testcontainers" % "1.20.4" % Test,
    )
  )

// Example Application - demonstrates library usage (not published to Maven Central)
lazy val exampleApp = (project in file("modules/example-app"))
  .dependsOn(runtime, langCompiler, langStdlib, httpApi)
  .settings(
    name := "constellation-example-app",
    publish / skip := true,
    coverageMinimumStmtTotal := 14,
    coverageMinimumBranchTotal := 36,
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

// CLI - command-line interface for Constellation Engine (not published to Maven Central)
lazy val langCli = (project in file("modules/lang-cli"))
  .settings(
    name := "constellation-lang-cli",
    publish / skip := true,
    coverageMinimumStmtTotal := 28,
    coverageMinimumBranchTotal := 31,
    libraryDependencies ++= Seq(
      "com.monovore"      %% "decline-effect"       % "2.4.1",
      "org.http4s"        %% "http4s-ember-client"  % "0.23.25",
      "org.http4s"        %% "http4s-dsl"           % "0.23.25",
      "org.http4s"        %% "http4s-circe"         % "0.23.25",
      "io.circe"          %% "circe-core"           % "0.14.6",
      "io.circe"          %% "circe-generic"        % "0.14.6",
      "io.circe"          %% "circe-parser"         % "0.14.6",
      "com.lihaoyi"       %% "fansi"                % "0.4.0",
      "org.scalatest"     %% "scalatest"            % "3.2.17" % Test,
      "org.scalatestplus" %% "scalacheck-1-17"      % "3.2.17.0" % Test,
    ) ++ loggingDeps,
    // Generate version resource file from build.sbt version
    Compile / resourceGenerators += Def.task {
      val file = (Compile / resourceManaged).value / "cli-version.txt"
      IO.write(file, version.value)
      Seq(file)
    }.taskValue,
    assembly / mainClass := Some("io.constellation.cli.Main"),
    assembly / assemblyJarName := "constellation-cli.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", _*) => MergeStrategy.concat
      case PathList("META-INF", _*)             => MergeStrategy.discard
      case "reference.conf"                     => MergeStrategy.concat
      case "logback.xml"                        => MergeStrategy.first
      case _                                    => MergeStrategy.first
    }
  )

// Doc Generator - extracts Scala type information to markdown (not published)
lazy val docGenerator = (project in file("modules/doc-generator"))
  .dependsOn(core, runtime, langAst, langParser, langCompiler, langStdlib, langLsp, httpApi)
  .settings(
    name := "constellation-doc-generator",
    publish / skip := true,
    libraryDependencies ++= Seq(
      "org.scala-lang" %% "scala3-tasty-inspector" % scalaVersion.value,
      "io.circe" %% "circe-core" % "0.14.6",
      "io.circe" %% "circe-generic" % "0.14.6",
      "io.circe" %% "circe-parser" % "0.14.6",
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    )
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
    cacheMemcached,
    moduleProvider,
    exampleApp,
    langCli,
    docGenerator
  )
  .settings(
    name := "constellation-engine",
    publish / skip := true
  )
