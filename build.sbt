import com.typesafe.sbt.packager.docker

name := "gargoyle-s3proxy"
version := "0.1"

scalaVersion := "2.12.6"

scalacOptions += "-unchecked"
scalacOptions += "-deprecation"
scalacOptions ++= Seq("-encoding", "utf-8")
scalacOptions += "-target:jvm-1.8"
scalacOptions += "-feature"
scalacOptions += "-Xlint"

// Experimental: improved update resolution.
updateOptions := updateOptions.value.withCachedResolution(cachedResoluton = true)

val akkaVersion       = "10.1.3"
val akkaStreamVersion = "2.5.13"

libraryDependencies += "com.typesafe.scala-logging"   %% "scala-logging"          % "3.9.0"
libraryDependencies += "ch.qos.logback"               %  "logback-classic"        % "1.2.3"           % Runtime

libraryDependencies += "com.typesafe.akka"            %% "akka-http"              % akkaVersion
libraryDependencies += "com.typesafe.akka"            %% "akka-stream"            % akkaStreamVersion
libraryDependencies += "com.typesafe.akka"            %% "akka-http-spray-json"   % akkaVersion
libraryDependencies += "com.typesafe.akka"            %% "akka-http-testkit"      % akkaVersion

libraryDependencies += "com.github.swagger-akka-http" %% "swagger-akka-http"      % "0.14.1"

libraryDependencies += "com.amazonaws"                %  "aws-java-sdk-s3"        % "1.11.362"
libraryDependencies += "com.lightbend.akka"           %% "akka-stream-alpakka-s3" % "0.20"
                                                     
libraryDependencies += "org.scalatest"                %% "scalatest"              % "3.0.5"           % Test
libraryDependencies += "org.scalamock"                %% "scalamock"              % "4.1.0"           % Test

enablePlugins(JavaAppPackaging)

fork := true

// Some default options at runtime: the G1 garbage collector, and headless mode.
javaOptions += "-XX:+UseG1GC"
javaOptions += "-Djava.awt.headless=true"

dockerExposedPorts := Seq(8080) // should match PROXY_PORT
dockerBaseImage    := "openjdk:8u171-jre-alpine3.7"

dockerUsername := Some(name.value)
dockerBuildOptions ++= {
  val alias = docker.DockerAlias(dockerRepository.value, dockerUsername.value, name.value, Some(version.value))
  Seq("-t", alias.versioned)
}
