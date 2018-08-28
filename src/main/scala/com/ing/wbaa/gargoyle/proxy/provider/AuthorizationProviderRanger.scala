package com.ing.wbaa.gargoyle.proxy.provider

import com.ing.wbaa.gargoyle.proxy.config.GargoyleRangerSettings
import com.ing.wbaa.gargoyle.proxy.data._
import com.typesafe.scalalogging.LazyLogging
import org.apache.ranger.plugin.policyengine.{ RangerAccessRequestImpl, RangerAccessResourceImpl }
import org.apache.ranger.plugin.service.RangerBasePlugin

/**
 * Interface for security provider implementations.
 */
trait AuthorizationProviderRanger extends LazyLogging {

  import AuthorizationProviderRanger.RangerException

  def rangerSettings: GargoyleRangerSettings

  private lazy val rangerPlugin = {
    try {
      val p = new RangerBasePlugin(rangerSettings.serviceType, rangerSettings.appId)
      p.init()
      p
    } catch {
      case ex: java.lang.NullPointerException =>
        throw RangerException(s"Ranger serviceType or appId not found (serviceType=${rangerSettings.serviceType}, " +
          s"appId=${rangerSettings.appId})", ex)
      case ex: Throwable =>
        throw RangerException("Unknown exception from Ranger plugin caught", ex)
    }
  }

  /**
   * Force initialization of the Ranger plugin.
   * This ensures we get connection errors on startup instead of when the first call is made.
   */
  def rangerPluginForceInit: RangerBasePlugin = rangerPlugin

  /**
   *  Check authorization with Ranger. Operations like list-buckets or create, delete bucket must be
   *  enabled in configuration. They are disabled by default
   */
  def isUserAuthorizedForRequest(request: S3Request, user: User): Boolean = {

    def rangerRequest(bucket: String): RangerAccessRequestImpl = {
      import scala.collection.JavaConverters._

      val resource = new RangerAccessResourceImpl(
        Map[String, AnyRef]("path" -> bucket).asJava
      )

      // TODO: use .setContext for metadata like arn
      new RangerAccessRequestImpl(
        resource,
        request.accessType.rangerName,
        user.userName,
        user.userGroups.asJava
      )
    }

    request match {
      // object operations, put / delete etc.
      case S3Request(_, Some(bucket), Some(bucketObject), _) =>
        logger.debug(s"Checking ranger with request: ${rangerRequest(bucket)}")
        Option(rangerPlugin.isAccessAllowed(rangerRequest(bucket))).exists(_.getIsAllowed)

      // list-objects in the bucket operation
      case S3Request(_, Some(bucket), None, accessType) if accessType == Read =>
        logger.debug(s"Checking ranger with request: ${rangerRequest(bucket)}")
        Option(rangerPlugin.isAccessAllowed(rangerRequest(bucket))).exists(_.getIsAllowed)

      // create / delete bucket opetation
      case S3Request(_, Some(bucket), None, accessType) if (accessType == Write || accessType == Delete) && rangerSettings.createBucketsEnabled =>
        logger.debug(s"Skipping ranger with request: ${request}")
        true

      // list buckets
      case S3Request(_, None, None, accessType) if accessType == Read && rangerSettings.listBucketsEnabled =>
        logger.debug(s"Skipping ranger with request: ${request}")
        true

      case _ =>
        logger.info("Authorization failed. Make sure your request uses supported parameters")
        false
    }
  }
}

object AuthorizationProviderRanger {
  final case class RangerException(private val message: String, private val cause: Throwable = None.orNull)
    extends Exception(message, cause)
}
