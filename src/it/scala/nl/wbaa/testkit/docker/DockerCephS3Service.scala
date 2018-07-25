package nl.wbaa.testkit.docker

import java.util.concurrent.TimeUnit

import com.whisk.docker.{DockerContainer, DockerKit, DockerReadyChecker}

import scala.concurrent.duration.FiniteDuration

trait DockerCephS3Service extends DockerKit {
  import WaitForDocker.waitAtMostDuration

  override val StartContainersTimeout: FiniteDuration = waitAtMostDuration
  override val StopContainersTimeout: FiniteDuration = waitAtMostDuration

  val cephInternalPort = 8010

  lazy val cephContainer: DockerContainer = DockerContainer("ceph/daemon:latest", None)
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
        DockerReadyChecker.LogLineContains("Running on http://0.0.0.0:5000/").looped(30, FiniteDuration(10, TimeUnit.SECONDS))
    )
    .withCommand("demo")

  abstract override def dockerContainers: List[DockerContainer] =
    cephContainer :: super.dockerContainers
}
