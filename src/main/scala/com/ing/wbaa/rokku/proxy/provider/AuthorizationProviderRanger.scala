package com.ing.wbaa.rokku.proxy.provider

import java.net.URLDecoder

import akka.http.scaladsl.model.MediaTypes
import com.ing.wbaa.rokku.proxy.config.RangerSettings
import com.ing.wbaa.rokku.proxy.data.{ Delete, Head, Post, Read, RequestId, S3Request, User, UserAssumeRole, UserGroup, Write }
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import org.apache.ranger.plugin.audit.RangerDefaultAuditHandler
import org.apache.ranger.plugin.policyengine.{ RangerAccessRequestImpl, RangerAccessResourceImpl }
import org.apache.ranger.plugin.service.RangerBasePlugin

import scala.util.{ Failure, Success, Try }

/**
 * Interface for security provider implementations.
 */
trait AuthorizationProviderRanger {

  private val logger = new LoggerHandlerWithId

  import AuthorizationProviderRanger.RangerException

  protected[this] def rangerSettings: RangerSettings

  private[this] lazy val rangerPlugin = {
    try {
      val p = new RangerBasePlugin(rangerSettings.serviceType, rangerSettings.appId)
      p.init()
      if (rangerSettings.auditEnabled) p.setResultProcessor(new RangerDefaultAuditHandler())
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
  protected[this] def rangerPluginForceInit: RangerBasePlugin = rangerPlugin

  /**
   *  Check authorization with Ranger. Operations like list-buckets or create, delete bucket must be
   *  enabled in configuration. They are disabled by default
   */
  def isUserAuthorizedForRequest(request: S3Request, user: User)(implicit id: RequestId): Boolean = {
    import scala.collection.JavaConverters._

    def prepareAccessRequest(rangerResource: RangerAccessResourceImpl, user: String, groups: Set[String]) = new RangerAccessRequestImpl(
      rangerResource,
      request.accessType.rangerName,
      user,
      groups.asJava
    )

    def isAuthorisedByRanger(s3path: String): Boolean = {
      val rangerResource = new RangerAccessResourceImpl(
        Map[String, AnyRef]("path" -> URLDecoder.decode(s3path, "UTF-8")).asJava
      )

      val rangerRequest = user.userRole match {
        case UserAssumeRole(roleValue) if !roleValue.isEmpty =>
          prepareAccessRequest(rangerResource, null, Set(UserGroup(s"${rangerSettings.rolePrefix}${roleValue}")).map(_.value.toLowerCase))
        case _ =>
          prepareAccessRequest(
            rangerResource, user.userName.value + rangerSettings.userDomainPostfix, user.userGroups.map(_.value.toLowerCase))
      }

      rangerRequest.setAction(request.accessType.auditAction)
      val MAX_CHARACTER_NUMBERS = 100
      //clientIp is used in audit - we limit the log length because user can put anything in ip headers
      rangerRequest.setClientIPAddress(request.userIps.toString.take(MAX_CHARACTER_NUMBERS))
      //RemoteIp and Forwarded are used in policies (check - AbstractIpCidrMatcher)
      rangerRequest.setRemoteIPAddress(request.clientIPAddress.toOption.map(_.getHostAddress).orNull)
      rangerRequest.setForwardedAddresses(request.headerIPs.allIPs.map(_.toOption.map(_.getHostAddress).orNull).asJava)

      logger.debug(s"Checking ranger with request: $rangerRequest")
      Try { rangerPlugin.isAccessAllowed(rangerRequest).getIsAllowed } match {
        case Success(authorization) => authorization
        case Failure(err) =>
          logger.warn(s"Exception during authorization of the request: $err")
          false
      }
    }

    request match {
      // object operations, put / delete etc.
      case S3Request(_, Some(s3path), Some(_), _, _, _, _) =>
        isAuthorisedByRanger(s3path)

      // object operation as subfolder, in this case object can be empty
      // we need this to differentiate subfolder create/delete from bucket create/delete
      case S3Request(_, Some(s3path), None, accessType, _, _, _) if s3path.endsWith("/") && (accessType.isInstanceOf[Delete] || accessType.isInstanceOf[Write]) =>
        isAuthorisedByRanger(s3path)

      // list-objects in the bucket operation
      case S3Request(_, Some(s3path), None, accessType, _, _, _) if accessType.isInstanceOf[Read] || accessType.isInstanceOf[Head] =>
        isAuthorisedByRanger(s3path)

      // multidelete with xml list of objects in post
      case S3Request(_, Some(s3path), None, accessType, _, _, mediaType) if accessType.isInstanceOf[Post] &&
        (mediaType == MediaTypes.`application/xml` || mediaType == MediaTypes.`application/octet-stream`) =>
        logger.debug(s"Passing ranger check for multi object deletion to check method")
        true

      // create / delete bucket operation
      case S3Request(_, Some(bucket), None, accessType, _, _, _) if (accessType.isInstanceOf[Write] || accessType.isInstanceOf[Delete]) =>
        isAuthorisedByRanger("/")

      // list buckets
      case S3Request(_, None, None, accessType, _, _, _) if accessType.isInstanceOf[Read] && rangerSettings.listBucketsEnabled =>
        logger.debug(s"Skipping ranger for listing of buckets with request: $request")
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
