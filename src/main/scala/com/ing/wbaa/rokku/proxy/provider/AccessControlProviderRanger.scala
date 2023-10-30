package com.ing.wbaa.rokku.proxy.provider

import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.security.{ AccessControl, AccessControlRequest }
import org.apache.ranger.plugin.audit.RangerDefaultAuditHandler
import org.apache.ranger.plugin.policyengine.{ RangerAccessRequestImpl, RangerAccessResourceImpl }
import org.apache.ranger.plugin.service.RangerBasePlugin
import org.slf4j.LoggerFactory

import java.net.URLDecoder
import scala.util.{ Failure, Success, Try }
/**
 * Interface for security provider implementations.
 */
class AccessControlProviderRanger(config: java.util.Map[String, String]) extends AccessControl {

  private val APP_ID_PARAM = "appId"
  private val SERVICE_TYPE_PARAM = "serviceType"
  private val ROLE_PREFIX_PARAM = "rolePrefix"
  private val USER_DOMAIN_POSTFIX_PARAM = "userDomainPostfix"

  private val logger = LoggerFactory.getLogger(getClass.getName)

  import AccessControlProviderRanger.RangerException

  private[this] lazy val rangerPlugin = {
    try {
      val p = new RangerBasePlugin(config.get(SERVICE_TYPE_PARAM), config.get(APP_ID_PARAM))
      p.init()
      if ("true".equals(config.get(AccessControl.AUDIT_ENABLED_PARAM))) p.setResultProcessor(new RangerDefaultAuditHandler())
      p
    } catch {
      case ex: java.lang.NullPointerException =>
        throw RangerException(s"Ranger serviceType or appId not found (serviceType=${config.get(SERVICE_TYPE_PARAM)}, " +
          s"appId=${config.get(APP_ID_PARAM)})", ex)
      case ex: Throwable =>
        throw RangerException("Unknown exception from Ranger plugin caught", ex)
    }
  }

  def init(): Unit = {
    rangerPluginForceInit
  }

  /**
   * Force initialization of the Ranger plugin.
   * This ensures we get connection errors on startup instead of when the first call is made.
   */
  protected[this] def rangerPluginForceInit: RangerBasePlugin = rangerPlugin

  /**
   * Check authorization with Ranger. Operations like list-buckets or create, delete bucket must be
   * enabled in configuration. They are disabled by default
   */

  import scala.jdk.CollectionConverters._
  def isAccessAllowed(request: AccessControlRequest): Boolean = {
    val rangerResource = new RangerAccessResourceImpl(
      Map[String, AnyRef]("path" -> URLDecoder.decode(request.path, "UTF-8")).asJava
    )

    val rangerRequest = request.userRole match {
      case roleValue if roleValue.isInstanceOf[String] && roleValue.nonEmpty =>
        prepareAccessRequest(rangerResource, request.accessType, null, Set(UserGroup(s"${config.get(ROLE_PREFIX_PARAM)}${roleValue}")).map(_.value.toLowerCase))
      case _ =>
        prepareAccessRequest(
          rangerResource, request.accessType, request.user + config.get(USER_DOMAIN_POSTFIX_PARAM), request.userGroups.asScala.map(_.toLowerCase).toSet)
    }

    rangerRequest.setAction(request.action)
    rangerRequest.setClientIPAddress(request.clientIpAddress)
    //RemoteIp and Forwarded are used in policies (check - AbstractIpCidrMatcher)
    rangerRequest.setRemoteIPAddress(request.remoteIpAddress)
    rangerRequest.setForwardedAddresses(request.forwardedIpAddresses)
    logger.debug(s"Checking ranger with request: $rangerRequest")
    Try {
      rangerPlugin.isAccessAllowed(rangerRequest).getIsAllowed
    } match {
      case Success(authorization) => authorization
      case Failure(err) =>
        logger.warn("Exception during authorization of the request", err)
        false
    }
  }

  private def prepareAccessRequest(rangerResource: RangerAccessResourceImpl, accessType: String, user: String, groups: Set[String]) = new RangerAccessRequestImpl(
    rangerResource,
    accessType,
    user,
    groups.asJava,
    Set.empty.asJava
  )
}

object AccessControlProviderRanger {
  final case class RangerException(private val message: String, private val cause: Throwable = None.orNull)
    extends Exception(message, cause)
}
