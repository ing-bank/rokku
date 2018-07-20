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

// Experimental: improved update resolution.
updateOptions := updateOptions.value.withCachedResolution(cachedResoluton = true)

val akkaVersion       = "10.1.3"
val akkaStreamVersion = "2.5.13"

resolvers ++= Seq(
    Resolver.bintrayRepo("cakesolutions", "maven"),
    Resolver.jcenterRepo
)

libraryDependencies ++= Seq(
    "com.typesafe.scala-logging"   %% "scala-logging"          % "3.9.0",
    "ch.qos.logback"               %  "logback-classic"        % "1.2.3"           % Runtime,
    "com.typesafe.akka"            %% "akka-http"              % akkaVersion,
    "com.typesafe.akka"            %% "akka-stream"            % akkaStreamVersion,
    "com.typesafe.akka"            %% "akka-http-spray-json"   % akkaVersion,
    "com.typesafe.akka"            %% "akka-http-testkit"      % akkaVersion,
    "com.github.swagger-akka-http" %% "swagger-akka-http"      % "0.14.1",
    "com.amazonaws"                %  "aws-java-sdk-s3"        % "1.11.362",
    "com.lightbend.akka"           %% "akka-stream-alpakka-s3" % "0.20",
    "org.apache.ranger"            % "ranger-plugins-common"   % "1.0.0",
    "org.scalatest"                %% "scalatest"              % "3.0.5"           % "it,test",
    "org.scalamock"                %% "scalamock"              % "4.1.0"           % "it,test",
    "com.whisk"                    %% "docker-testkit-scalatest"     % "0.9.7"     % "it,test",// exclude("javax.ws.rs", "javax.ws.rs-api"),
    "com.whisk"                    %% "docker-testkit-impl-spotify"  % "0.9.7"     % "it,test" //exclude("javax.ws.rs", "javax.ws.rs-api")//,
////    "org.glassfish.jersey.core" % "jersey-server" % "2.27"
//    "org.glassfish.jersey.core" % "jersey-client" % "2.27",
//    "org.glassfish.hk2" % "hk2-api" % "2.1.9"
//    ,"javax.ws.rs" % "javax.ws.rs-api" % "2.0.1"
)

//val workaround = {
//    sys.props += "packaging.type" -> "jar"
//    ()
//}


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
dockerAlias        := docker.DockerAlias(Some("docker.io"), Some("arempter"), "gargoyle-s3proxy", Some(Option(System.getenv("TRAVIS_BRANCH")).getOrElse("latest")))

scalariformPreferences := scalariformPreferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(DoubleIndentConstructorArguments, true)
    .setPreference(DanglingCloseParenthesis, Preserve)

//Coverage settings
coverageMinimum := 70
coverageFailOnMinimum := false
coverageHighlighting := true
