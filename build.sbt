import com.typesafe.sbt.packager.docker

name := "gargoyle-s3proxy"

version := "0.1"

scalaVersion := "2.12.6"

val akkaVersion       = "10.1.3"
val akkaStreamVersion = "2.5.12"
val loggingVersion    = "3.9.0"
val logbackVersion    = "1.2.3"
val scalatestVersion  = "3.0.5"
val scalaMockVersion  = "4.1.0"

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-encoding", "utf-8",
  "-target:jvm-1.8",
  "-feature"
)

lazy val akka = Seq(
  "com.typesafe.akka" %% "akka-http"   % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaStreamVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion
)
lazy val scalaTests = Seq(
  "org.scalatest" %% "scalatest" % scalatestVersion % Test,
  "org.scalamock" %% "scalamock" % scalaMockVersion % Test
)

lazy val logging = Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % loggingVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion
)

libraryDependencies ++= akka ++ logging

enablePlugins(JavaAppPackaging)

dockerExposedPorts := Seq(8080)
dockerBaseImage    := "openjdk:jre"

dockerRepository :=  Some("registry.somewhere.com")
dockerUsername := Some(name.value)
dockerBuildOptions ++= {
  val alias = docker.DockerAlias(dockerRepository.value, dockerUsername.value, name.value, Some(version.value))
  Seq("-t", alias.versioned)
}