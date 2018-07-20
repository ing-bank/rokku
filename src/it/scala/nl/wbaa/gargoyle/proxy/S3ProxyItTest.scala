package nl.wbaa.gargoyle.proxy

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials, SignerFactory}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.typesafe.config.ConfigFactory
import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.scalatest.DockerTestKit
import nl.wbaa.testkit.docker.DockerCephS3Service
import org.scalatest._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

class S3ProxyItTest extends WordSpec with DiagrammedAssertions
  with DockerTestKit
  with DockerKitSpotify
  with DockerCephS3Service
  with BeforeAndAfterAll {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private val configProxy = ConfigFactory.load().getConfig("proxy.server")
  val proxyPort: Int = configProxy.getInt("port")

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
        val cliConf = new ClientConfiguration()
        cliConf.setMaxErrorRetry(1)
        cliConf.setSignerOverride(awsSignerType)

        val testCli = AmazonS3ClientBuilder
          .standard()
          .withClientConfiguration(cliConf)
          .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("accesskey", "secretkey")))
          .withEndpointConfiguration(new EndpointConfiguration(s"http://127.0.0.1:$proxyPort", "us-west-2"))
          .build()

        "list the current buckets" in {
          assert(testCli.listBuckets().asScala.toList.map(_.getName) == List("demobucket"))
        }
      }
    }
  }
}
