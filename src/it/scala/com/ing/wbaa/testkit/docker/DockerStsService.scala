package com.ing.wbaa.testkit.docker

import java.util.concurrent.TimeUnit

import com.whisk.docker.{DockerContainer, DockerKit, DockerReadyChecker}

import scala.concurrent.duration.FiniteDuration

trait DockerStsService extends DockerKit {
  import WaitForDocker.waitAtMostDuration

  override val StartContainersTimeout: FiniteDuration = waitAtMostDuration
  override val StopContainersTimeout: FiniteDuration = waitAtMostDuration

  val stsHost = "0.0.0.0"
  val stsInternalPort = 12345

  lazy val stsContainer: DockerContainer = DockerContainer("kr7ysztof/gargoyle-sts:internal-endpoint-json", None)
    .withEnv(
      s"STS_HOST=$stsHost",
      s"STS_PORT=$stsInternalPort"
    )
    .withPorts(stsInternalPort -> None)
    .withReadyChecker(
        DockerReadyChecker.LogLineContains(s"INFO com.ing.wbaa.gargoyle.sts.StsService - Sts service started listening: /$stsHost:$stsInternalPort").looped(30, FiniteDuration(10, TimeUnit.SECONDS))
    )
    .withCommand("demo")

  abstract override def dockerContainers: List[DockerContainer] =
    stsContainer :: super.dockerContainers
}
