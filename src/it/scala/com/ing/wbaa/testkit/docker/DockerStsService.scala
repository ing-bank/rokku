package com.ing.wbaa.testkit.docker

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import com.ing.wbaa.gargoyle.proxy.config.GargoyleStsSettings
import com.whisk.docker.{DockerContainer, DockerKit, DockerReadyChecker}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait DockerStsService extends DockerKit {
  import WaitForDocker.waitAtMostDuration

  implicit def testSystem: ActorSystem

  override val StartContainersTimeout: FiniteDuration = waitAtMostDuration
  override val StopContainersTimeout: FiniteDuration = waitAtMostDuration

  private val stsHost = "0.0.0.0"
  private val stsInternalPort = 12345

  private val stsContainer: DockerContainer = DockerContainer("kr7ysztof/gargoyle-sts:0.0.2", None)
    .withEnv(
      s"STS_HOST=$stsHost",
      s"STS_PORT=$stsInternalPort"
    )
    .withPorts(stsInternalPort -> None)
    .withReadyChecker(
        DockerReadyChecker.LogLineContains(
          s"INFO com.ing.wbaa.gargoyle.sts.Server$$$$anon$$1 - Sts service started listening: /$stsHost:$stsInternalPort"
        ).looped(30, FiniteDuration(10, TimeUnit.SECONDS))
    )

  // Settings to connect to S3 storage, we have to wait for the docker container to retrieve the exposed port
  lazy val gargoyleStsSettingsFuture: Future[GargoyleStsSettings] =
    stsContainer.getPorts()(docker = dockerExecutor, ec = dockerExecutionContext)
      .map { portMapping =>
        new GargoyleStsSettings(testSystem.settings.config) {
          override val stsBaseUri: Uri = Uri(
            scheme = "http",
            authority = Uri.Authority(Uri.Host("127.0.0.1"), portMapping(stsInternalPort))
          )
        }
      }

  abstract override def dockerContainers: List[DockerContainer] =
    stsContainer :: super.dockerContainers
}
