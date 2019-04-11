package com.ing.wbaa.testkit.awssdk

import java.io.File

import akka.http.scaladsl.model.Uri.Authority
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.{AWSCredentials, AWSStaticCredentialsProvider, BasicSessionCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.amazonaws.services.s3.transfer.model.UploadResult
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._


trait S3SdkHelpers {
  private val awsRegion = ConfigFactory.load().getString("airlock.storage.s3.region")

  def getAmazonS3(authority: Authority,
                  credentials: AWSCredentials = new BasicSessionCredentials("accesskey", "secretkey", "token")
                 ): AmazonS3 = {
    val cliConf = new ClientConfiguration()
    cliConf.setMaxErrorRetry(1)

    AmazonS3ClientBuilder
      .standard()
      .withClientConfiguration(cliConf)
      .withCredentials(new AWSStaticCredentialsProvider(credentials))
      .withEndpointConfiguration(new EndpointConfiguration(s"http://${authority.host.address}:${authority.port}", awsRegion))
      .build()
  }

  def getKeysInBucket(sdk: AmazonS3, bucket: String): List[String] =
    sdk
      .listObjectsV2(bucket)
      .getObjectSummaries
      .asScala.toList
      .map(_.getKey)

  def doMultiPartUpload(sdk: AmazonS3, bucket: String, file: String, key: String): UploadResult = {
    val upload = TransferManagerBuilder
      .standard()
      .withS3Client(sdk)
      .build()
      .upload(bucket, key, new File(file))

    upload.waitForUploadResult()
  }
}
