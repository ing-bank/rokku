package nl.wbaa.testkit.docker

import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import com.whisk.docker.{DockerContainer, DockerKit, DockerReadyChecker}
import nl.wbaa.testkit.AwaitAtMostTrait

import scala.concurrent.duration.FiniteDuration

trait DockerCephS3Service extends DockerKit with DockerPortPicker with AwaitAtMostTrait {
  override val StartContainersTimeout: FiniteDuration = waitAtMostDuration
  override val StopContainersTimeout: FiniteDuration = waitAtMostDuration

  private val configS3 = ConfigFactory.load().getConfig("s3.server")
  val exposedPort: Int = configS3.getInt("port") // For now this port is static, if we need multiple versions, use randomAvailablePort()

  private val port = 8010

  lazy val cephContainer: DockerContainer = DockerContainer("ceph/daemon:latest", Some("cephittest"))
    .withEnv(
      "CEPH_DEMO_UID=ceph-admin",
      "CEPH_DEMO_ACCESS_KEY=accesskey",
      "CEPH_DEMO_SECRET_KEY=secretkey",
      "CEPH_DEMO_BUCKET=demobucket",
      "RGW_NAME=localhost",
      s"RGW_CIVETWEB_PORT=$port",
      "NETWORK_AUTO_DETECT=4",
      "RESTAPI_LOG_LEVEL=debug"
    )
    .withPorts(port -> Some(exposedPort))
    .withReadyChecker(
        DockerReadyChecker.LogLineContains("Running on http://0.0.0.0:5000/").looped(30, FiniteDuration(10, TimeUnit.SECONDS))
    )
    .withCommand("demo")

  abstract override def dockerContainers: List[DockerContainer] =
    cephContainer :: super.dockerContainers
}
