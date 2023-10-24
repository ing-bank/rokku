package com.ing.wbaa.rokku.proxy.provider

import akka.http.scaladsl.model.MediaTypes
import com.ing.wbaa.rokku.proxy.config.AuthorizerSettings
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.rokku.proxy.handler.exception.RokkuListingBucketsException

trait AuthorizationProvider {

  private val logger = new LoggerHandlerWithId

  protected[this] def authorizerSettings: AuthorizerSettings
  protected[this] def isAuthorised(s3Path: String, request: S3Request, user: User)(implicit id: RequestId): Boolean

  def isUserAuthorizedForRequest(request: S3Request, user: User)(implicit id: RequestId): Boolean = {

    request match {
      // object operations, put / delete etc.
      case S3Request(_, Some(s3Path), Some(_), _, _, _, _, _) =>
        isAuthorised(s3Path, request, user)

      // object operation as subfolder, in this case object can be empty
      // we need this to differentiate subfolder create/delete from bucket create/delete
      case S3Request(_, Some(s3Path), None, accessType, _, _, _, _) if s3Path.endsWith("/") && (accessType.isInstanceOf[Delete] || accessType.isInstanceOf[Write]) =>
        isAuthorised(s3Path, request, user)

      // list-objects in the bucket operation
      case S3Request(_, Some(s3Path), None, accessType, _, _, _, _) if accessType.isInstanceOf[Read] || accessType.isInstanceOf[Head] =>
        isAuthorised(s3Path, request, user)

      // multidelete with xml list of objects in post
      case S3Request(_, Some(s3Path), None, accessType, _, _, mediaType, _) if accessType.isInstanceOf[Post] &&
        (mediaType == MediaTypes.`application/xml` || mediaType == MediaTypes.`application/octet-stream`) =>
        logger.debug(s"Passing ranger check for multi object deletion to check method")
        isAuthorised(s3Path, request, user) //we assume user has to have access to bucket

      // create / delete bucket operation
      case S3Request(_, Some(_), None, accessType, _, _, _, _) if (accessType.isInstanceOf[Write] || accessType.isInstanceOf[Delete]) =>
        if (authorizerSettings.createDeleteBucketsEnabled) {
          isAuthorised("/", request, user)
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

}
