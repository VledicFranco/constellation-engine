name := "constellation-http-api"

val http4sVersion = "0.23.25"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.typelevel" %% "cats-effect" % "3.5.2",
  "io.circe" %% "circe-generic" % "0.14.6",
  "ch.qos.logback" % "logback-classic" % "1.4.11",
  "org.scalatest" %% "scalatest" % "3.2.17" % Test,
)
