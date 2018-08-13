package com.ing.wbaa.gargoyle.proxy.provider

import com.ing.wbaa.gargoyle.proxy.config.GargoyleRangerSettings
import com.ing.wbaa.gargoyle.proxy.data.{ S3Request, User }
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
   * Check authorization with Ranger. Currently we deny any requests not to a specific bucket (e.g. listBuckets)
   */
  def isUserAuthorizedForRequest(request: S3Request, user: User): Boolean = {
    request.bucket match {
      case Some(bucket) =>
        import scala.collection.JavaConverters._

        val resource = new RangerAccessResourceImpl(
          Map[String, AnyRef]("path" -> bucket).asJava
        )

        // TODO: use .setContext for metadata like arn
        val rangerRequest = new RangerAccessRequestImpl(
          resource,
          request.accessType.rangerName,
          user.userId,
          user.groups.asJava
        )

        logger.debug(s"Checking ranger with request: $rangerRequest")
        Option(rangerPlugin.isAccessAllowed(rangerRequest)).exists(_.getIsAllowed)
      case None =>
        logger.info("Authorization failed since no bucket is specified. Currently these commands are blocked.")
        false
    }
  }
}

object AuthorizationProviderRanger {
  final case class RangerException(private val message: String, private val cause: Throwable = None.orNull)
    extends Exception(message, cause)
}
