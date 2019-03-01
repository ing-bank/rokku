package com.ing.wbaa.airlock.proxy.provider.aws

import com.amazonaws.auth.{ AWSStaticCredentialsProvider, BasicAWSCredentials }
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.{ AmazonS3, AmazonS3ClientBuilder }
import com.ing.wbaa.airlock.proxy.config.StorageS3Settings

import scala.concurrent.Future
import scala.io.Source

trait S3Client {
  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] val defaultBucketPolicy: String = Source.fromResource("default-bucket-policy.json").getLines().mkString

  protected[this] def storageS3Settings: StorageS3Settings

  protected[this] lazy val s3Client: AmazonS3 = {
    val credentials = new BasicAWSCredentials(
      storageS3Settings.storageS3AdminAccesskey,
      storageS3Settings.storageS3AdminSecretkey)

    val endpointConfiguration = new AwsClientBuilder.EndpointConfiguration(
      s"http://${storageS3Settings.storageS3Authority.host.address()}:${storageS3Settings.storageS3Authority.port}",
      Regions.US_EAST_1.getName)

    AmazonS3ClientBuilder.standard()
      .withPathStyleAccessEnabled(true)
      .withCredentials(new AWSStaticCredentialsProvider(credentials))
      .withEndpointConfiguration(endpointConfiguration)
      .build()
  }

  /**
   * Sets the default bucket policy
   * @param bucketName The name of the bucket to set the policy
   * @return A future which completes when the policy is set
   */
  protected[this] def setDefaultBucketPolicy(bucketName: String): Future[Unit] = Future {
    s3Client.setBucketPolicy(bucketName, defaultBucketPolicy)
  }

}
