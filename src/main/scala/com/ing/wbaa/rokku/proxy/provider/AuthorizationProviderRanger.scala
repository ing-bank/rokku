package com.ing.wbaa.rokku.proxy.provider

import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import org.apache.ranger.plugin.audit.RangerDefaultAuditHandler
import org.apache.ranger.plugin.policyengine.{ RangerAccessRequestImpl, RangerAccessResourceImpl }
import org.apache.ranger.plugin.service.RangerBasePlugin

import java.net.URLDecoder
import scala.util.{ Failure, Success, Try }
/**
 * Interface for security provider implementations.
 */
trait AuthorizationProviderRanger extends AuthorizationProvider {

  private val logger = new LoggerHandlerWithId

  import AuthorizationProviderRanger.RangerException

  private[this] lazy val rangerPlugin = {
    try {
      val p = new RangerBasePlugin(authorizerSettings.serviceType, authorizerSettings.appId)
      p.init()
      if (authorizerSettings.auditEnabled) p.setResultProcessor(new RangerDefaultAuditHandler())
      p
    } catch {
      case ex: java.lang.NullPointerException =>
        throw RangerException(s"Ranger serviceType or appId not found (serviceType=${authorizerSettings.serviceType}, " +
          s"appId=${authorizerSettings.appId})", ex)
      case ex: Throwable =>
        throw RangerException("Unknown exception from Ranger plugin caught", ex)
    }
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

  def isAuthorised(s3Path: String, request: S3Request, user: User)(implicit id: RequestId): Boolean = {
    val rangerResource = new RangerAccessResourceImpl(
      Map[String, AnyRef]("path" -> URLDecoder.decode(s3Path, "UTF-8")).asJava
    )

    val rangerRequest = user.userRole match {
      case UserAssumeRole(roleValue) if roleValue.nonEmpty =>
        prepareAccessRequest(rangerResource, request.accessType.rangerName, null, Set(UserGroup(s"${authorizerSettings.rolePrefix}${roleValue}")).map(_.value.toLowerCase))
      case _ =>
        prepareAccessRequest(
          rangerResource, request.accessType.rangerName, user.userName.value + authorizerSettings.userDomainPostfix, user.userGroups.map(_.value.toLowerCase))
    }

    rangerRequest.setAction(request.accessType.auditAction)
    val MAX_CHARACTER_NUMBERS = 100
    //clientIp is used in audit - we limit the log length because user can put anything in ip headers
    rangerRequest.setClientIPAddress(request.userIps.toString.take(MAX_CHARACTER_NUMBERS))
    //RemoteIp and Forwarded are used in policies (check - AbstractIpCidrMatcher)
    rangerRequest.setRemoteIPAddress(request.clientIPAddress.toOption.map(_.getHostAddress).orNull)
    rangerRequest.setForwardedAddresses(request.headerIPs.allIPs.map(_.toOption.map(_.getHostAddress).orNull).asJava)

    logger.debug(s"Checking ranger with request: $rangerRequest")
    Try {
      rangerPlugin.isAccessAllowed(rangerRequest).getIsAllowed
    } match {
      case Success(authorization) => authorization
      case Failure(err) =>
        logger.warn("Exception during authorization of the request:{}", err)
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

object AuthorizationProviderRanger {
  final case class RangerException(private val message: String, private val cause: Throwable = None.orNull)
    extends Exception(message, cause)
}
