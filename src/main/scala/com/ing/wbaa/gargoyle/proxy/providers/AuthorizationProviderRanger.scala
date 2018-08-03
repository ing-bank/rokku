package com.ing.wbaa.gargoyle.proxy.providers

import com.ing.wbaa.gargoyle.proxy.config.GargoyleRangerSettings
import com.ing.wbaa.gargoyle.proxy.data.{S3Request, User}
import com.typesafe.scalalogging.LazyLogging
import org.apache.ranger.plugin.policyengine.{RangerAccessRequestImpl, RangerAccessResourceImpl}
import org.apache.ranger.plugin.service.RangerBasePlugin

import scala.collection.JavaConverters._

/**
 * Interface for security provider implementations.
 */
trait AuthorizationProviderRanger extends AuthorizationProviderBase with LazyLogging {

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

  override def isAuthorized(request: S3Request, user: User): Boolean = {
    val resource = new RangerAccessResourceImpl(
      Map[String, AnyRef]("path" -> request.bucket).asJava
    )

    // TODO: use .setContext for metadata like arn
    val rangerRequest = new RangerAccessRequestImpl(
      resource,
      request.accessType.toString,
      user.userId,
      user.groups.asJava
    )

    logger.debug(s"Checking ranger with request: $rangerRequest")
    Option(rangerPlugin.isAccessAllowed(rangerRequest)).exists(_.getIsAllowed)
  }
}

object AuthorizationProviderRanger {
  final case class RangerException(private val message: String, private val cause: Throwable = None.orNull)
    extends Exception(message, cause)
}
