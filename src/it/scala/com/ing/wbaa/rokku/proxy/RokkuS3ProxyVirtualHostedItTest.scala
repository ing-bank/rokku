package com.ing.wbaa.rokku.proxy

import akka.http.scaladsl.model.Uri.Authority
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.{AWSCredentials, AWSStaticCredentialsProvider, BasicSessionCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}

class RokkuS3ProxyVirtualHostedItTest extends RokkuS3ProxyItTest {

  override def getAmazonS3(authority: Authority,
                           credentials: AWSCredentials = new BasicSessionCredentials("accesskey", "secretkey", "token")
                          ): AmazonS3 = {
    val cliConf = new ClientConfiguration()
    cliConf.setMaxErrorRetry(1)

    AmazonS3ClientBuilder
      .standard()
      .disableChunkedEncoding()
      .withClientConfiguration(cliConf)
      .withCredentials(new AWSStaticCredentialsProvider(credentials))
      .withPathStyleAccessEnabled(false)
      .withEndpointConfiguration(new EndpointConfiguration(s"http://s3.localhost:${authority.port}", awsRegion))
      .build()
  }
}
