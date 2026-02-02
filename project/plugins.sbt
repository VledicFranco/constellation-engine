// Constellation Engine SBT Plugins

// Hot-reload for development - automatically restart server on code changes
// Usage: sbt "~exampleApp/reStart"
addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")

// Code formatting
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

// Linting and refactoring rules
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.11.1")

// Code coverage reporting
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.9")

// Fat JAR packaging for deployment
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.5")

// Automated Maven Central publishing (GPG signing + Sonatype release)
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.2")
