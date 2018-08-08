package com.ing.wbaa.testkit.docker

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.proxy.config.GargoyleRangerSettings
import com.whisk.docker.{ContainerLink, DockerContainer, DockerKit, DockerReadyChecker}

import scala.concurrent.duration.FiniteDuration

trait DockerRangerAdminService extends DockerKit with DockerRangerPostgresService {

  import WaitForDocker.waitAtMostDuration

  implicit def testSystem: ActorSystem

  override val StartContainersTimeout: FiniteDuration = waitAtMostDuration
  override val StopContainersTimeout: FiniteDuration = waitAtMostDuration

  private val rangerInternalPort = 6080

  private val rangerContainer: DockerContainer = DockerContainer("nielsdenissen/ranger-admin:0.0.1", None)
    .withPorts(rangerInternalPort -> None)
    .withReadyChecker(
      DockerReadyChecker
        .LogLineContains(s"Policy created")
        .looped(30, FiniteDuration(10, TimeUnit.SECONDS))
    )
    .withLinks(
      ContainerLink(rangerPostgresContainer, "postgres-server")
    )

  // Settings to connect to S3 storage, we have to wait for the docker container to retrieve the exposed port
  lazy val gargoyleRangerSettings: GargoyleRangerSettings =
    new GargoyleRangerSettings(testSystem.settings.config) {
      override val serviceType: String = "s3"
      override val appId: String = "testservice"
    }

  abstract override def dockerContainers: List[DockerContainer] =
    rangerContainer :: super.dockerContainers
}
