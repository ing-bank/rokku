package nl.wbaa.gargoyle.proxy

import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.scalatest.DockerTestKit
import nl.wbaa.testkit.docker.DockerCephS3Service
import nl.wbaa.testkit.s3sdk.sdkSetup
import org.scalatest._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

class S3ProxyItTest extends WordSpec with DiagrammedAssertions
  with DockerTestKit
  with DockerKitSpotify
  with DockerCephS3Service
  with BeforeAndAfterAll
  with sdkSetup {
  private val logger = LoggerFactory.getLogger(this.getClass)

  override def beforeAll(): Unit = {
    logger.info(s"Starting Ceph docker images and S3 Proxy")
    new S3Proxy().start()
    super.beforeAll()
  }

  "S3 Proxy" should {
    // TODO: Expand with different signer types
    val awsSignerTypes = List(
      "S3SignerType"//,
//      SignerFactory.NO_OP_SIGNER,
//      SignerFactory.QUERY_STRING_SIGNER,
//      SignerFactory.VERSION_FOUR_SIGNER,
//      SignerFactory.VERSION_FOUR_UNSIGNED_PAYLOAD_SIGNER,
//      SignerFactory.VERSION_THREE_SIGNER
    )

    awsSignerTypes.foreach{ awsSignerType =>
      s"proxy with $awsSignerType" that {
        val sdk = getAmazonS3(awsSignerType)
        "list the current buckets" in {
          assert(sdk.listBuckets().asScala.toList.map(_.getName) == List("demobucket"))
        }
      }
    }
  }
}
