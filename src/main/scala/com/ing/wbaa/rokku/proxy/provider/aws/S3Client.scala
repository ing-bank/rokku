package com.ing.wbaa.rokku.proxy.provider.aws

import com.amazonaws.auth.{ AWSStaticCredentialsProvider, BasicAWSCredentials }
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.{ AccessControlList, BucketPolicy }
import com.amazonaws.services.s3.{ AmazonS3, AmazonS3ClientBuilder }
import com.ing.wbaa.rokku.proxy.config.StorageS3Settings

import scala.concurrent.{ ExecutionContext, Future }

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

  def getBucketAcl(bucketName: String): Future[AccessControlList] = Future {
    s3Client.getBucketAcl(bucketName)
  }

  def getBucketPolicy(bucketName: String): Future[BucketPolicy] = Future {
    s3Client.getBucketPolicy(bucketName)
  }

  def listBucket: String = {
    s3Client.listObjects(storageS3Settings.bucketName).getBucketName
  }
}
