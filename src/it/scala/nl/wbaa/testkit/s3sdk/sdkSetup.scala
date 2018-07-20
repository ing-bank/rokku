package nl.wbaa.testkit.s3sdk

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.typesafe.config.ConfigFactory


trait sdkSetup {
  import sdkSetup._

  def getAmazonS3(awsSignerType: String): AmazonS3 = {
    val cliConf = new ClientConfiguration()
    cliConf.setMaxErrorRetry(1)
    cliConf.setSignerOverride(awsSignerType)

    AmazonS3ClientBuilder
      .standard()
      .withClientConfiguration(cliConf)
      .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("accesskey", "secretkey")))
      .withEndpointConfiguration(new EndpointConfiguration(s"http://127.0.0.1:$proxyPort", "us-west-2"))
      .build()
  }
}

object sdkSetup {
  private val configProxy = ConfigFactory.load().getConfig("proxy.server")
  val proxyPort: Int = configProxy.getInt("port")
}
