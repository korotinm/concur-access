import sbt.Keys._

scalaVersion in ThisBuild := "2.12.8"


lazy val commonDependencies = Seq(
  "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "org.scalatest" %% "scalatest" % "3.0.1" % Test
)

lazy val commonSettings = Seq(
  organization := "noname",
  version := "0.1-SNAPSHOT",
  testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-W", "30", "30"),
  javaOptions in (Test, run) += "-Xmx128G -Xms128G",
  scalacOptions ++= List(
    "-encoding",
    "UTF-8",
    "-target:jvm-1.8",
    "-unchecked",
    "-Ywarn-unused",
    "-deprecation",
    "-Ypartial-unification"
  ),
  javacOptions ++= Seq("-Xfatal-warnings", "-source", "1.8", "-target", "1.8"),
  resolvers += "Typesafe" at "http://repo.typesafe.com/typesafe/releases/",
  libraryDependencies ++= commonDependencies
)

lazy val concuraccess = (project in file("concuraccess"))
  .settings(commonSettings)
  .settings(
    parallelExecution in Test := false
  )

val root = (project in file("."))
  .settings(commonSettings)
  .aggregate(concuraccess)
