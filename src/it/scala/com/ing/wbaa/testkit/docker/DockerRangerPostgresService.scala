package com.ing.wbaa.testkit.docker

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.whisk.docker.{DockerContainer, DockerKit, DockerReadyChecker}

import scala.concurrent.duration.FiniteDuration

trait DockerRangerPostgresService extends DockerKit {

  import WaitForDocker.waitAtMostDuration

  implicit def testSystem: ActorSystem

  override val StartContainersTimeout: FiniteDuration = waitAtMostDuration
  override val StopContainersTimeout: FiniteDuration = waitAtMostDuration

  private val rangerPostgresInternalPort = 5432

  val rangerPostgresContainer: DockerContainer =
    DockerContainer("nielsdenissen/ranger-postgres:0.0.1", Some("rangerPostgresItTestImage"))
    .withPorts(rangerPostgresInternalPort -> None)
    .withReadyChecker(
      DockerReadyChecker
        .LogLineContains("LOG:  listening on IPv4 address \"0.0.0.0\", port 5432")
        .looped(30, FiniteDuration(10, TimeUnit.SECONDS))
    )

  abstract override def dockerContainers: List[DockerContainer] =
    rangerPostgresContainer :: super.dockerContainers
}
