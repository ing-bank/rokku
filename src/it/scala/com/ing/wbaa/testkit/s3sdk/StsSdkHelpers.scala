package com.ing.wbaa.testkit.s3sdk

import akka.http.scaladsl.model.Uri
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.Regions
import com.amazonaws.services.securitytoken.{AWSSecurityTokenService, AWSSecurityTokenServiceClientBuilder}


trait StsSdkHelpers {
  def getAmazonSTSSdk(uri: Uri): AWSSecurityTokenService = {
    AWSSecurityTokenServiceClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("accesskey", "secretkey")))
      .withEndpointConfiguration(new EndpointConfiguration(
        s"${uri.scheme}://${uri.authority.host.address}:${uri.authority.port}", Regions.DEFAULT_REGION.getName)
      )
      .build()
  }
}
