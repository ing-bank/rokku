package com.ing.wbaa.rokku.proxy.security

import akka.http.scaladsl.model.MediaTypes
import com.ing.wbaa.rokku.proxy.config.AccessControlSettings
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.rokku.proxy.handler.exception.RokkuListingBucketsException

trait AccessControl {

  private val logger = new LoggerHandlerWithId
  protected[this] def authorizerSettings: AccessControlSettings

  def init(): Unit
  def isAccessAllowed(request: AccessControlRequest)(implicit id: RequestId): Boolean

  def isUserAuthorizedForRequest(request: S3Request, user: User)(implicit id: RequestId): Boolean = {

    request match {
      // object operations, put / delete etc.
      case S3Request(_, Some(s3Path), Some(_), _, _, _, _, _) =>
        isAccessAllowed(prepareAuthorizeRequest(s3Path, request, user))

      // object operation as subfolder, in this case object can be empty
      // we need this to differentiate subfolder create/delete from bucket create/delete
      case S3Request(_, Some(s3Path), None, accessType, _, _, _, _) if s3Path.endsWith("/") && (accessType.isInstanceOf[Delete] || accessType.isInstanceOf[Write]) =>
        isAccessAllowed(prepareAuthorizeRequest(s3Path, request, user))

      // list-objects in the bucket operation
      case S3Request(_, Some(s3Path), None, accessType, _, _, _, _) if accessType.isInstanceOf[Read] || accessType.isInstanceOf[Head] =>
        isAccessAllowed(prepareAuthorizeRequest(s3Path, request, user))

      // multidelete with xml list of objects in post
      case S3Request(_, Some(s3Path), None, accessType, _, _, mediaType, _) if accessType.isInstanceOf[Post] &&
        (mediaType == MediaTypes.`application/xml` || mediaType == MediaTypes.`application/octet-stream`) =>
        logger.debug(s"Passing ranger check for multi object deletion to check method")
        isAccessAllowed(prepareAuthorizeRequest(s3Path, request, user)) //we assume user has to have access to bucket

      // create / delete bucket operation
      case S3Request(_, Some(_), None, accessType, _, _, _, _) if (accessType.isInstanceOf[Write] || accessType.isInstanceOf[Delete]) =>
        if (authorizerSettings.createDeleteBucketsEnabled) {
          isAccessAllowed(prepareAuthorizeRequest("/", request, user))
        } else {
          logger.info("Creating/Deleting bucket is disabled - request={}", request)
          false
        }

      // list buckets
      case S3Request(_, None, None, accessType, _, _, _, _) if accessType.isInstanceOf[Read] =>
        if (authorizerSettings.listBucketsEnabled) {
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

  private def prepareAuthorizeRequest(s3Path: String, request: S3Request, user: User): AccessControlRequest = {
    //clientIp is used in audit - we limit the log length because user can put anything in ip headers
    val MAX_CHARACTER_NUMBERS = 100
    AccessControlRequest(user.userName.value, user.userGroups.map(_.value), user.userRole.value, s3Path, request.accessType.rangerName, request.accessType.auditAction, request.userIps.toString.take(MAX_CHARACTER_NUMBERS), request.clientIPAddress.toOption.map(_.getHostAddress).orNull, request.headerIPs.allIPs.map(_.toOption.map(_.getHostAddress).orNull).toSet)
  }
}
