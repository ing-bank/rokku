import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.ExecCmd
import scalariform.formatter.preferences._

name := "gargoyle-s3proxy"
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

// Excludes (javax.ws.rs and com.sun.jersey) to fix conflicts for the method: javax.ws.rs.core.MultivaluedMap.addAll
libraryDependencies ++= Seq(
    "com.typesafe.scala-logging"   %% "scala-logging"          % "3.9.0",
    "ch.qos.logback"               %  "logback-classic"        % "1.2.3"           % Runtime,
    "com.typesafe.akka"            %% "akka-http"              % akkaVersion,
    "com.typesafe.akka"            %% "akka-stream"            % akkaStreamVersion,
    "com.typesafe.akka"            %% "akka-http-spray-json"   % akkaVersion,
    "com.typesafe.akka"            %% "akka-http-testkit"      % akkaVersion,
    "com.amazonaws"                %  "aws-java-sdk-s3"        % "1.11.372",
    "com.lightbend.akka"           %% "akka-stream-alpakka-s3" % "0.20",
    "org.apache.ranger"            %  "ranger-plugins-common"  % "1.1.0", // exclude("javax.ws.rs", "jsr311-api") exclude("com.sun.jersey", "jersey-core") exclude("com.sun.jersey", "jersey-json") exclude("com.sun.jersey", "jersey-server") exclude("com.sun.jersey", "jersey-bundle"),
    "org.scalatest"                %% "scalatest"              % "3.0.5"           % "it,test",
    "com.whisk"                    %% "docker-testkit-scalatest"     % "0.9.7"     % IntegrationTest,
    "com.whisk"                    %% "docker-testkit-impl-spotify"  % "0.9.7"     % IntegrationTest
)

configs(IntegrationTest)
Defaults.itSettings

parallelExecution in IntegrationTest := false

enablePlugins(JavaAppPackaging)

fork := true

// Some default options at runtime: the G1 garbage collector, and headless mode.
javaOptions += "-XX:+UseG1GC"
javaOptions += "-Djava.awt.headless=true"

dockerExposedPorts := Seq(8080) // should match PROXY_PORT
dockerCommands     += ExecCmd("ENV", "PROXY_HOST", "0.0.0.0")
dockerBaseImage    := "openjdk:8u171-jre-slim-stretch"
dockerAlias        := docker.DockerAlias(Some("docker.io"),
                                         Some("arempter"),
                                         "gargoyle-s3proxy",
                                         Some(Option(System.getenv("TRAVIS_BRANCH")).getOrElse("latest")))

scalariformPreferences := scalariformPreferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(DanglingCloseParenthesis, Preserve)
    .setPreference(DoubleIndentConstructorArguments, true)
    .setPreference(DoubleIndentMethodDeclaration, true)
    .setPreference(NewlineAtEndOfFile, true)
    .setPreference(SingleCasePatternOnNewline, false)

//Coverage settings
coverageMinimum := 70
coverageFailOnMinimum := false
coverageHighlighting := true
coverageEnabled := true
