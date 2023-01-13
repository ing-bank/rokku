package com.ing.wbaa.rokku.proxy.handler.namespace

import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.model.{AmazonS3Exception, ListObjectsV2Result}
import com.ing.wbaa.rokku.proxy.config.NamespaceSettings
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.rokku.proxy.metrics.MetricsFactory.{incrementBucketNamespacesInCache, incrementBucketNamespacesNotFound, incrementBucketNamespacesSearch}
import com.ing.wbaa.rokku.proxy.util.S3Utils

import scala.collection.immutable.ListMap
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

case class NamespaceName(name: String)

case class BucketName(name: String)

trait NamespacesHandler {
  private val logger = new LoggerHandlerWithId
  private val bucketCredentials: mutable.Map[BucketName, BasicAWSCredentials] = scala.collection.mutable.Map[BucketName, BasicAWSCredentials]()

  protected[this] val namespaceSettings: NamespaceSettings

  private def namespaceCredentials: ListMap[NamespaceName, BasicAWSCredentials] = namespaceSettings.namespaceCredentialsMap

  private def findNamespace(bucketName: BucketName)(implicit id: RequestId): Option[(NamespaceName, BasicAWSCredentials)] = {
    namespaceCredentials.find {
      case (namespaceName, _) =>
        isBucketInNamespace(bucketName, namespaceName)
    }
  }

  protected[this] def listBucket(bucketName: String, credentials: BasicAWSCredentials, maxKeys: Int = 5): ListObjectsV2Result

  protected[this] def bucketNamespaceCredentials(request: HttpRequest)(implicit id: RequestId): Option[BasicAWSCredentials] = {
    val bucketName = BucketName(S3Utils.getBucketName(S3Utils.getPathNameFromUrlOrHost(request)))
    logger.debug("looking namespace for bucket {}", bucketName.name)
    bucketCredentials.get(bucketName) match {
      case Some(credentials) =>
        logger.debug("credentials exist for bucket {}", bucketName.name)
        incrementBucketNamespacesInCache()
        Some(credentials)
      case None =>
        logger.info("credentials for bucket {} do not exist - looking for the bucket in namespaces", bucketName.name)
        incrementBucketNamespacesSearch()
        val namespaceCredentials = findNamespace(bucketName)
        if (namespaceCredentials.isDefined) {
          updateBucketCredentials(bucketName, namespaceCredentials.get._2)
          logger.info("added credentials from namespace {} for bucket {}", namespaceCredentials.get._1, bucketName.name)
        } else {
          logger.warn("no namespace for bucket {} in namespaces {}", bucketName.name, namespaceCredentials)
          incrementBucketNamespacesNotFound()
        }
        namespaceCredentials.map(_._2)
    }
  }

  protected[this] def updateBucketCredentials(bucketName: BucketName, credentials: BasicAWSCredentials): Unit = {
    bucketCredentials.update(bucketName, credentials)
  }

  protected[this] def isBucketInNamespace(bucketName: BucketName, namespaceName: NamespaceName)(implicit id: RequestId): Boolean = {
    namespaceCredentials.get(namespaceName) match {
      case Some(credentials) =>
        logger.debug("checking namespace {} for bucket {} with key {}", namespaceName, bucketName.name, credentials.getAWSAccessKeyId)
        Try {
          listBucket(bucketName.name, credentials, 1)
        } match {
          case Failure(ex) if ex.isInstanceOf[AmazonS3Exception] =>
            if (ex.asInstanceOf[AmazonS3Exception].getStatusCode != 404) {
              logger.error("namespace {} returned exception {} for credentials {} but should only status code 404", namespaceName, ex, credentials.getAWSAccessKeyId)
            }
            false
          case Failure(ex) =>
            logger.error("namespace {} returned exception {} for credentials {}", namespaceName, ex, credentials.getAWSAccessKeyId)
            false
          case Success(_) =>
            logger.info("bucket {} found in namespace {}", bucketName.name, namespaceName.name)
            true
        }
      case None =>
        logger.error("missing namespace credentials!!!")
        false
    }
  }
}
