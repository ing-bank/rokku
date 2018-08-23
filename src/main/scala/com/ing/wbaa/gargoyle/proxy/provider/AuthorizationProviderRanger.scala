package com.ing.wbaa.gargoyle.proxy.provider

import com.ing.wbaa.gargoyle.proxy.config.GargoyleRangerSettings
import com.ing.wbaa.gargoyle.proxy.data.{ Read, S3Request, User }
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
    request match {
      case S3Request(_, bucketOpt, bucketObjectOpt, accessType) =>
        bucketOpt match {
          case Some(bucket) =>
            import scala.collection.JavaConverters._

            val resource = new RangerAccessResourceImpl(
              Map[String, AnyRef]("path" -> bucket).asJava
            )
            val bucketObjectExists = if (bucketObjectOpt.getOrElse("").length > 1) true else false
            // TODO: use .setContext for metadata like arn
            val rangerRequest = new RangerAccessRequestImpl(
              resource,
              request.accessType.rangerName,
              user.userName,
              user.userGroups.asJava
            )

            if (bucketObjectExists) {
              // object operations
              logger.debug(s"Checking ranger with request: $rangerRequest")
              Option(rangerPlugin.isAccessAllowed(rangerRequest)).exists(_.getIsAllowed)
            } else if (!bucketObjectExists && accessType == Read) {
              // list-objects operation
              logger.debug(s"Checking ranger with request: $rangerRequest")
              Option(rangerPlugin.isAccessAllowed(rangerRequest)).exists(_.getIsAllowed)
            } else if (rangerSettings.createBucketsEnabled) {
              // create, delete
              logger.debug(s"Skipping ranger with request: $rangerRequest")
              true
            } else {
              logger.info("Authorization failed since no bucket is specified. Currently these commands are blocked.")
              false
            }

          case None =>
            // list buckets
            if (accessType == Read && rangerSettings.listBucketsEnabled) {
              logger.debug(s"Skipping ranger with request: ${request}")
              true
            } else {
              logger.info("Authorization failed since no bucket is specified. Currently these commands are blocked.")
              false
            }
        }
    }
  }
}

object AuthorizationProviderRanger {
  final case class RangerException(private val message: String, private val cause: Throwable = None.orNull)
    extends Exception(message, cause)
}
