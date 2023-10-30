package com.ing.wbaa.rokku.proxy.provider

import akka.http.scaladsl.model.MediaTypes
import com.ing.wbaa.rokku.proxy.config.AccessControlProviderSettings
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.rokku.proxy.handler.exception.RokkuListingBucketsException
import com.ing.wbaa.rokku.proxy.security.{ AccessControl, AccessControlRequest }

import scala.jdk.CollectionConverters._

trait AccessControlProviderClassForName extends AccessControl {

  private val logger = new LoggerHandlerWithId

  lazy val auth: AccessControl = {
    accessControlProviderSettings

    val config: Map[String, String] = Map(
      AccessControl.AUDIT_ENABLED_PARAM -> accessControlProviderSettings.auditEnabled.toString) ++ accessControlProviderSettings.pluginParams
    Class.forName(accessControlProviderSettings.className)
      .getDeclaredConstructor(classOf[java.util.Map[String, String]])
      .newInstance(config.asJava).asInstanceOf[AccessControl]
  }

  def init(): Unit = {
    implicit val id: RequestId = RequestId("-")
    logger.info("AccessControlProviderClassForName uses {}", auth.getClass.getName)
    auth.init()
  }

  def isAccessAllowed(request: AccessControlRequest): Boolean = {
    auth.isAccessAllowed(request)
  }

  protected[this] def accessControlProviderSettings: AccessControlProviderSettings

  def isUserAuthorizedForRequest(request: S3Request, user: User)(implicit id: RequestId): Boolean = {

    request match {
      // object operations, put / delete etc.
      case S3Request(_, Some(s3Path), Some(_), _, _, _, _, _) =>
        isAccessAllowed(prepareAccessControlRequest(s3Path, request, user))

      // object operation as subfolder, in this case object can be empty
      // we need this to differentiate subfolder create/delete from bucket create/delete
      case S3Request(_, Some(s3Path), None, accessType, _, _, _, _) if s3Path.endsWith("/") && (accessType.isInstanceOf[Delete] || accessType.isInstanceOf[Write]) =>
        isAccessAllowed(prepareAccessControlRequest(s3Path, request, user))

      // list-objects in the bucket operation
      case S3Request(_, Some(s3Path), None, accessType, _, _, _, _) if accessType.isInstanceOf[Read] || accessType.isInstanceOf[Head] =>
        isAccessAllowed(prepareAccessControlRequest(s3Path, request, user))

      // multidelete with xml list of objects in post
      case S3Request(_, Some(s3Path), None, accessType, _, _, mediaType, _) if accessType.isInstanceOf[Post] &&
        (mediaType == MediaTypes.`application/xml` || mediaType == MediaTypes.`application/octet-stream`) =>
        logger.debug(s"Passing ranger check for multi object deletion to check method")
        isAccessAllowed(prepareAccessControlRequest(s3Path, request, user)) //we assume user has to have access to bucket

      // create / delete bucket operation
      case S3Request(_, Some(_), None, accessType, _, _, _, _) if (accessType.isInstanceOf[Write] || accessType.isInstanceOf[Delete]) =>
        if (accessControlProviderSettings.createDeleteBucketsEnabled) {
          isAccessAllowed(prepareAccessControlRequest("/", request, user))
        } else {
          logger.info("Creating/Deleting bucket is disabled - request={}", request)
          false
        }

      // list buckets
      case S3Request(_, None, None, accessType, _, _, _, _) if accessType.isInstanceOf[Read] =>
        if (accessControlProviderSettings.listBucketsEnabled) {
          logger.debug("Skipping ranger for listing of buckets with request: {}", request)
          true
        } else {
          logger.debug("listing of buckets is disabled - request:{}", request)
          throw new RokkuListingBucketsException("Listing bucket is disabled")
        }

      case _ =>
        logger.info("Authorization failed. Make sure your request uses supported parameters")
        false
    }
  }

  private def prepareAccessControlRequest(s3Path: String, request: S3Request, user: User): AccessControlRequest = {
    import scala.jdk.CollectionConverters._
    //clientIp is used in audit - we limit the log length because user can put anything in ip headers
    val MAX_CHARACTER_NUMBERS = 100
    new AccessControlRequest(user.userName.value, user.userGroups.map(_.value).asJava, user.userRole.value, s3Path, request.accessType.rangerName, request.accessType.auditAction, request.userIps.toString.take(MAX_CHARACTER_NUMBERS), request.clientIPAddress.toOption.map(_.getHostAddress).orNull, request.headerIPs.allIPs.map(_.toOption.map(_.getHostAddress).orNull).toList.asJava, null)
  }
}
