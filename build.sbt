name := "akka-sse-buffer-check-example"
version := "0.1.0-SNAPSHOT"

scalaVersion := "2.11.6"
scalacOptions ++= Seq("-deprecation", "-feature")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http-experimental" % "1.0-RC3",
  "de.heikoseeberger" %% "akka-sse" % "0.13.0"
)

Revolver.settings