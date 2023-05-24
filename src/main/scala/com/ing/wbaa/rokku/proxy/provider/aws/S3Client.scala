package com.ing.wbaa.rokku.proxy.provider.aws

import com.amazonaws.auth.{ AWSStaticCredentialsProvider, BasicAWSCredentials }
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.{ ListObjectsV2Request, ListObjectsV2Result }
import com.amazonaws.services.s3.{ AmazonS3, AmazonS3ClientBuilder }
import com.ing.wbaa.rokku.proxy.config.StorageS3Settings
import com.ing.wbaa.rokku.proxy.metrics.MetricsFactory

import scala.concurrent.ExecutionContext

trait S3Client {
  protected[this] implicit def executionContext: ExecutionContext

  protected[this] def storageS3Settings: StorageS3Settings

  protected[this] lazy val storageNPACredentials: BasicAWSCredentials = {
    new BasicAWSCredentials(
      storageS3Settings.storageS3AdminAccesskey,
      storageS3Settings.storageS3AdminSecretkey)
  }

  protected[this] lazy val endpointConfiguration: AwsClientBuilder.EndpointConfiguration = {
    new AwsClientBuilder.EndpointConfiguration(
      s"${storageS3Settings.storageS3Schema}://${storageS3Settings.storageS3Authority.host.address()}:${storageS3Settings.storageS3Authority.port}",
      Regions.US_EAST_1.getName)
  }

  protected[this] lazy val s3Client: AmazonS3 = {
    AmazonS3ClientBuilder.standard()
      .withPathStyleAccessEnabled(true)
      .withCredentials(new AWSStaticCredentialsProvider(storageNPACredentials))
      .withEndpointConfiguration(endpointConfiguration)
      .build()
  }

  protected[this] def s3Client(credentials: BasicAWSCredentials): AmazonS3 = {
    AmazonS3ClientBuilder.standard()
      .withPathStyleAccessEnabled(true)
      .withCredentials(new AWSStaticCredentialsProvider(credentials))
      .withEndpointConfiguration(endpointConfiguration)
      .build()
  }

  protected def listBucketRequest(bucketName: String, maxKeys: Int): ListObjectsV2Request = {
    new ListObjectsV2Request().withBucketName(bucketName).withMaxKeys(maxKeys)
  }

  protected[this] def listBucket(bucketName: String, credentials: BasicAWSCredentials, maxKeys: Int): ListObjectsV2Result = {
    s3Client(credentials).listObjectsV2(listBucketRequest(bucketName, maxKeys))
  }

  protected[this] def listBucket(bucketName: String, maxKeys: Int = 5): String = {
    s3Client.listObjectsV2(listBucketRequest(bucketName, maxKeys)).getBucketName
  }

  def listBucket: String = {
    val start = System.nanoTime()
    val result = listBucket(storageS3Settings.bucketName)
    val took = System.nanoTime() - start
    MetricsFactory.markRequestStorageTime(took)
    result
  }

  def getBucketLocation(bucketName: String, credentials: BasicAWSCredentials): String = {
    s3Client(credentials).getBucketLocation(bucketName)
  }

}
