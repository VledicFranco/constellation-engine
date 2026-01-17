ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.12"
ThisBuild / organization := "io.constellation"

lazy val root = (project in file("."))
  .settings(
    name := "constellation-engine",
    libraryDependencies ++= Seq(
      // Cats
      "org.typelevel" %% "cats-core" % "2.10.0",
      "org.typelevel" %% "cats-effect" % "3.5.2",

      // Shapeless for generic programming
      "com.chuusai" %% "shapeless" % "2.3.10",

      // Circe for JSON
      "io.circe" %% "circe-core" % "0.14.6",
      "io.circe" %% "circe-generic" % "0.14.6",
      "io.circe" %% "circe-parser" % "0.14.6",

      // Testing
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    ),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlint:unused",
    ),
  )
