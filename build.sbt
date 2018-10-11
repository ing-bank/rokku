import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.ExecCmd
import scalariform.formatter.preferences._

name := "airlock"
version := "0.1"

scalaVersion := "2.12.6"

scalacOptions += "-unchecked"
scalacOptions += "-deprecation"
scalacOptions ++= Seq("-encoding", "utf-8")
scalacOptions += "-target:jvm-1.8"
scalacOptions += "-feature"
scalacOptions += "-Xlint"
scalacOptions += "-Xfatal-warnings"

// Experimental: improved update resolution.
updateOptions := updateOptions.value.withCachedResolution(cachedResoluton = true)

val akkaVersion       = "10.1.3"
val akkaStreamVersion = "2.5.14"

libraryDependencies ++= Seq(
    "com.typesafe.scala-logging"   %% "scala-logging"          % "3.9.0",
    "ch.qos.logback"               %  "logback-classic"        % "1.2.3"           % Runtime,
    "com.typesafe.akka"            %% "akka-http"              % akkaVersion,
    "com.typesafe.akka"            %% "akka-stream"            % akkaStreamVersion,
    "com.typesafe.akka"            %% "akka-http-spray-json"   % akkaVersion,
    "com.typesafe.akka"            %% "akka-http-testkit"      % akkaVersion,
    "com.amazonaws"                %  "aws-java-sdk-s3"        % "1.11.372",
    "com.lightbend.akka"           %% "akka-stream-alpakka-s3" % "0.20",
    "org.apache.ranger"            %  "ranger-plugins-common"  % "1.1.0",
    "io.github.twonote"            % "radosgw-admin4j"         % "1.0.2",
    "org.scalatest"                %% "scalatest"              % "3.0.5"           % "it,test",
    "com.amazonaws"                % "aws-java-sdk-sts"        % "1.11.372"        % IntegrationTest
)

// Fix logging dependencies:
//  - Our logging implementation is Logback, via the Slf4j API.
//  - Therefore we suppress the Log4j implentation and re-route its API calls over Slf4j.
libraryDependencies += "org.slf4j" % "log4j-over-slf4j" % "1.7.25" % Runtime
excludeDependencies += "org.slf4j" % "slf4j-log4j12"
excludeDependencies += "log4j" % "log4j"

configs(IntegrationTest)
Defaults.itSettings

parallelExecution in Test:= true
parallelExecution in IntegrationTest := true

enablePlugins(JavaAppPackaging)

fork := true

// Some default options at runtime: the G1 garbage collector, and headless mode.
javaOptions += "-XX:+UseG1GC"
javaOptions += "-Djava.awt.headless=true"

dockerExposedPorts := Seq(8080) // should match PROXY_PORT
dockerCommands     += ExecCmd("ENV", "PROXY_HOST", "0.0.0.0")
dockerBaseImage    := "openjdk:8u171-jre-slim-stretch"
dockerAlias        := docker.DockerAlias(Some("docker.io"),
                                         Some("nielsdenissen"),
                                         "airlock",
                                         Option(System.getenv("DOCKER_TAG")))

scalariformPreferences := scalariformPreferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(DanglingCloseParenthesis, Preserve)
    .setPreference(DoubleIndentConstructorArguments, true)
    .setPreference(DoubleIndentMethodDeclaration, true)
    .setPreference(NewlineAtEndOfFile, true)
    .setPreference(SingleCasePatternOnNewline, false)

// hack for ranger conf dir - should contain files like ranger-s3-security.xml etc.
scriptClasspath in bashScriptDefines ~= (cp => cp :+ ":/etc/airlock")

//Coverage settings
Compile / coverageMinimum := 70
Compile / coverageFailOnMinimum := false
Compile / coverageHighlighting := true
Compile / coverageEnabled := true
