package com.ing.wbaa.airlock.proxy.provider

import akka.http.scaladsl.model.RemoteAddress
import com.ing.wbaa.airlock.proxy.config.RangerSettings
import com.ing.wbaa.airlock.proxy.data._
import com.typesafe.scalalogging.LazyLogging
import org.apache.ranger.plugin.policyengine.{ RangerAccessRequestImpl, RangerAccessResourceImpl }
import org.apache.ranger.plugin.service.RangerBasePlugin

import scala.util.{ Failure, Success, Try }

/**
 * Interface for security provider implementations.
 */
trait AuthorizationProviderRanger extends LazyLogging {

  import AuthorizationProviderRanger.RangerException

  protected[this] def rangerSettings: RangerSettings

  private[this] lazy val rangerPlugin = {
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
  protected[this] def rangerPluginForceInit: RangerBasePlugin = rangerPlugin

  /**
   *  Check authorization with Ranger. Operations like list-buckets or create, delete bucket must be
   *  enabled in configuration. They are disabled by default
   */
  def isUserAuthorizedForRequest(request: S3Request, user: User, clientIPAddress: RemoteAddress, headerIPs: HeaderIPs): Boolean = {

    def isAuthorisedByRanger(s3path: String): Boolean = {
      import scala.collection.JavaConverters._

      val rangerResource = new RangerAccessResourceImpl(
        Map[String, AnyRef]("path" -> s3path).asJava
      )

      val rangerRequest = new RangerAccessRequestImpl(
        rangerResource,
        request.accessType.rangerName,
        user.userName.value + rangerSettings.userDomainPostfix,
        user.userGroups.map(_.value.toLowerCase).asJava
      )
      // We're using the original client's IP address for verification in Ranger. Ranger seems to use the
      // RemoteIPAddress variable for this. For the header IPs we use the ForwardedAddresses: this is not
      // completely true, but it works fairly enough.
      rangerRequest.setRemoteIPAddress(clientIPAddress.toOption.map(_.getHostAddress).orNull)
      rangerRequest.setForwardedAddresses(headerIPs.allIPs.map(_.toOption.map(_.getHostAddress).orNull).asJava)

      logger.debug(s"Checking ranger with request: $rangerRequest")
      Try { rangerPlugin.isAccessAllowed(rangerRequest).getIsAllowed } match {
        case Success(authorization) => authorization
        case Failure(err) =>
          logger.warn(s"Exception during authorization of the request: ${err}")
          false
      }
    }

    request match {
      // object operations, put / delete etc.
      case S3Request(_, Some(s3path), Some(_), _) =>
        isAuthorisedByRanger(s3path)

      // object operation as subfolder, in this case object can be empty
      // we need this to differentiate subfolder create/delete from bucket create/delete
      case S3Request(_, Some(s3path), None, accessType) if s3path.endsWith("/") && (accessType == Delete || accessType == Write) =>
        isAuthorisedByRanger(s3path)

      // list-objects in the bucket operation
      case S3Request(_, Some(s3path), None, accessType) if accessType == Read || accessType == Head =>
        isAuthorisedByRanger(s3path)

      // create / delete bucket operation
      case S3Request(_, Some(_), None, accessType) if (accessType == Write || accessType == Delete) && rangerSettings.createBucketsEnabled =>
        logger.debug(s"Skipping ranger for creation/deletion of bucket with request: $request")
        true

      // list buckets
      case S3Request(_, None, None, accessType) if accessType == Read && rangerSettings.listBucketsEnabled =>
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
