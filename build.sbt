import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.ExecCmd
import scalariform.formatter.preferences._

val rokkuVersion = scala.sys.env.getOrElse("ROKKU_VERSION", "SNAPSHOT")

name := "rokku"
version := rokkuVersion
scalaVersion := "2.12.8"

scalacOptions += "-unchecked"
scalacOptions += "-deprecation"
scalacOptions ++= Seq("-encoding", "utf-8")
scalacOptions += "-target:jvm-1.8"
scalacOptions += "-feature"
scalacOptions += "-Xlint"
scalacOptions += "-Xfatal-warnings"

// Experimental: improved update resolution.
updateOptions := updateOptions.value.withCachedResolution(cachedResoluton = true)

val akkaHttpVersion = "10.1.8"
val akkaVersion = "2.5.23"
val logbackJson = "0.1.5"
val metricVersion = "3.2.2" // align with C* driver core, can be updated with new C* persistence from akka

libraryDependencies ++= Seq(
    "com.typesafe.scala-logging"   %% "scala-logging"          % "3.9.0",
    "ch.qos.logback"               %  "logback-classic"        % "1.2.3",
    "ch.qos.logback.contrib"       %  "logback-json-classic"   % logbackJson,
    "ch.qos.logback.contrib"       %  "logback-jackson"        % logbackJson,
    "com.fasterxml.jackson.core"   %  "jackson-databind"       % "2.9.9",
    "com.typesafe.akka"            %% "akka-slf4j"             % akkaVersion,
    "com.typesafe.akka"            %% "akka-http"              % akkaHttpVersion,
    "com.typesafe.akka"            %% "akka-stream"            % akkaVersion,
    "com.typesafe.akka"            %% "akka-http-spray-json"   % akkaHttpVersion,
    "com.typesafe.akka"            %% "akka-http-xml"          % akkaHttpVersion,
    "com.amazonaws"                %  "aws-java-sdk-s3"        % "1.11.505",
    "org.apache.kafka"             %  "kafka-clients"           % "2.0.0",
    "net.manub"                    %% "scalatest-embedded-kafka" % "2.0.0" % IntegrationTest,
    "org.apache.ranger"            %  "ranger-plugins-common"  % "1.1.0" exclude("org.apache.kafka", "kafka_2.11") exclude("org.apache.htrace","htrace-core"),
    "io.github.twonote"            %  "radosgw-admin4j"        % "1.0.2",
    "com.lightbend.akka"           %% "akka-stream-alpakka-xml"% "1.0-M2",
    "io.dropwizard.metrics"        % "metrics-core"            % metricVersion,
//    "io.dropwizard.metrics"        % "metrics-jmx"             % metricVersion, // bring back after persistence update
    "com.auth0"                    % "java-jwt"                % "3.8.0",
    "com.typesafe.akka"            %% "akka-testkit"           % akkaVersion       % Test,
    "com.typesafe.akka"            %% "akka-http-testkit"      % akkaHttpVersion   % Test,
    "org.scalatest"                %% "scalatest"              % "3.0.5"           % "it,test",
    "com.amazonaws"                %  "aws-java-sdk-sts"       % "1.11.505"        % IntegrationTest
) ++ persistenceDependencies

val persistenceDependencies = Seq (
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.98",
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % "0.98" % Test
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
javaOptions += "-Dlogback.configurationFile=/etc/rokku/logback.xml"

dockerExposedPorts := Seq(8080) // should match PROXY_PORT
dockerCommands     += ExecCmd("ENV", "PROXY_HOST", "0.0.0.0")
dockerBaseImage    := "openjdk:8u171-jre-slim-stretch"
dockerAlias        := docker.DockerAlias(Some("docker.io"), Some("wbaa"), "rokku", Some(rokkuVersion))

scalariformPreferences := scalariformPreferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(DanglingCloseParenthesis, Preserve)
    .setPreference(DoubleIndentConstructorArguments, true)
    .setPreference(DoubleIndentMethodDeclaration, true)
    .setPreference(NewlineAtEndOfFile, true)
    .setPreference(SingleCasePatternOnNewline, false)

// hack for ranger conf dir - should contain files like ranger-s3-security.xml etc.
scriptClasspath in bashScriptDefines ~= (cp => cp :+ ":/etc/rokku")

//Coverage settings
Compile / coverageMinimum := 70
Compile / coverageFailOnMinimum := false
Compile / coverageHighlighting := true
Compile / coverageEnabled := true
