package com.ing.wbaa.testkit.docker

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import com.ing.wbaa.gargoyle.proxy.config.GargoyleStorageS3Settings
import com.whisk.docker.{DockerContainer, DockerKit, DockerReadyChecker}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait DockerCephS3Service extends DockerKit {
  import WaitForDocker.waitAtMostDuration

  implicit def testSystem: ActorSystem

  override val StartContainersTimeout: FiniteDuration = waitAtMostDuration
  override val StopContainersTimeout: FiniteDuration = waitAtMostDuration

  private val cephInternalPort = 8010

  private val cephContainer: DockerContainer = DockerContainer("ceph/daemon:v3.0.5-stable-3.0-luminous-centos-7", None)
    .withEnv(
      "CEPH_DEMO_UID=ceph-admin",
      "CEPH_DEMO_ACCESS_KEY=accesskey",
      "CEPH_DEMO_SECRET_KEY=secretkey",
      "CEPH_DEMO_BUCKET=demobucket",
      "RGW_NAME=localhost",
      s"RGW_CIVETWEB_PORT=$cephInternalPort",
      "NETWORK_AUTO_DETECT=4",
      "RESTAPI_LOG_LEVEL=debug"
    )
    .withPorts(cephInternalPort -> None)
    .withReadyChecker(
        DockerReadyChecker.LogLineContains("* Running on http://[::]:5000/").looped(30, FiniteDuration(10, TimeUnit.SECONDS))
    )
    .withCommand("demo")

  // Settings to connect to S3 storage, we have to wait for the docker container to retrieve the exposed port
  lazy val gargoyleStorageS3SettingsFuture: Future[GargoyleStorageS3Settings] =
    cephContainer.getPorts()(docker = dockerExecutor, ec = dockerExecutionContext)
      .map { portMapping =>
        new GargoyleStorageS3Settings(testSystem.settings.config) {
          override val storageS3Authority: Uri.Authority =
            Uri.Authority(Uri.Host("127.0.0.1"), portMapping(cephInternalPort))
        }
      }

  abstract override def dockerContainers: List[DockerContainer] =
    cephContainer :: super.dockerContainers
}
