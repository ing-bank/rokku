import com.typesafe.sbt.packager.docker

name := "gargoyle-s3proxy"

version := "0.1"

scalaVersion := "2.12.6"

val akkaVersion       = "10.1.3"
val akkaStreamVersion = "2.5.12"


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
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  "org.scalamock" %% "scalamock" % "4.1.0" % Test
)
lazy val logging = Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)
lazy val swagger = Seq(
  "com.github.swagger-akka-http" %% "swagger-akka-http" % "0.14.0"
)
lazy val aws3sdk = Seq(
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.360"
)
lazy val alpakkaS3 = Seq(
  "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "0.20"
)


libraryDependencies ++= akka ++ scalaTests ++ logging ++ aws3sdk ++ alpakkaS3

enablePlugins(JavaAppPackaging)

dockerExposedPorts := Seq(8080) // should match PROXY_PORT
dockerBaseImage    := "openjdk:8u171-jre-alpine3.7"

dockerRepository :=  Some("registry.somewhere.com")
dockerUsername := Some(name.value)
dockerBuildOptions ++= {
  val alias = docker.DockerAlias(dockerRepository.value, dockerUsername.value, name.value, Some(version.value))
  Seq("-t", alias.versioned)
}